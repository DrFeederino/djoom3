package neo.idlib.geometry

import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Pluecker.idPluecker
import neo.idlib.math.Vector.idVec3
import java.util.*

/**
 *
 */
object Surface {
    //    @Deprecated
    private fun UpdateVertexIndex(
        vertexIndexNum: IntArray,
        vertexRemap: IntArray,
        vertexCopyIndex: IntArray,
        vertNum: Int
    ): Int {
        val s = Math_h.INTSIGNBITSET(vertexRemap[vertNum])
        vertexIndexNum[0] = vertexRemap[vertNum]
        vertexRemap[vertNum] = vertexIndexNum[s]
        vertexIndexNum[1] += s
        vertexCopyIndex[vertexRemap[vertNum]] = vertNum
        return vertexRemap[vertNum]
    }

    //    private static int UpdateVertexIndex(int vertexIndexNum[], int[] vertexRemap, final int rIndex, int[] vertexCopyIndex, final int cIndex, int vertNum) {
    //        int s = INTSIGNBITSET(vertexRemap[rIndex + vertNum]);
    //        vertexIndexNum[0] = vertexRemap[rIndex + vertNum];
    //        vertexRemap[rIndex + vertNum] = vertexIndexNum[s];
    //        vertexIndexNum[1] += s;
    //        vertexCopyIndex[cIndex + vertexRemap[rIndex + vertNum]] = vertNum;
    //        return vertexRemap[rIndex + vertNum];
    //    }
    /*
     ===============================================================================

     Surface base class.

     A surface is tesselated to a triangle mesh with each edge shared by
     at most two triangles.

     ===============================================================================
     */
    class surfaceEdge_t {
        var tris: IntArray = IntArray(2) // edge triangles
        var verts: IntArray = IntArray(2) // edge vertices always with ( verts[0] < verts[1] )

        companion object {
            private fun generateArray(length: Int): Array<surfaceEdge_t> {
                return Array(length) { surfaceEdge_t() }
            }
        }
    }

    open class idSurface {
        protected val edgeIndexes: idList<Int> =
            idList() // 3 references to edges for each triangle, may be negative for reversed edge
        protected val edges: idList<surfaceEdge_t> = idList() // edges
        protected val indexes: idList<Int> = idList() // 3 references to vertices for each triangle
        protected val verts: idList<idDrawVert> = idList() // vertices

        //
        //
        constructor()
        constructor(surf: idSurface) {
            verts.set(surf.verts)
            indexes.set(surf.indexes)
            edges.set(surf.edges)
            edgeIndexes.set(surf.edgeIndexes)
        }

        constructor(verts: Array<idDrawVert>?, numVerts: Int, indexes: IntArray?, numIndexes: Int) {
            assert(verts != null && indexes != null && numVerts > 0 && numIndexes > 0)
            this.verts.SetNum(numVerts)
            //	memcpy( this.verts.Ptr(), verts, numVerts * sizeof( verts[0] ) );
            //System.arraycopy(verts, 0, this.verts.getList(), 0, numVerts);
            for (i in 0 until numVerts) {
                this.verts[i] = verts!![i]
            }
            this.indexes.SetNum(numIndexes)
            //	memcpy( this.indexes.Ptr(), indexes, numIndexes * sizeof( indexes[0] ) );
            System.arraycopy(indexes, 0, this.indexes.getList(), 0, numIndexes)
            GenerateEdgeIndexes()
        }

        //public							~idSurface( void );
        //
        //public	const idDrawVert &		operator[]( const int index ) const;
        operator fun get(index: Int): idDrawVert {
            return verts[index]
        }

        fun plusAssign(surf: idSurface): idSurface {
            var i: Int
            val m: Int
            val n: Int
            n = verts.Num()
            m = indexes.Num()
            verts.Append(surf.verts) // merge verts where possible ?
            indexes.Append(surf.indexes)
            i = m
            while (i < indexes.Num()) {
                indexes[i] += n
                i++
            }
            GenerateEdgeIndexes()
            return this
        }

        fun GetNumIndexes(): Int {
            return indexes.Num()
        }

        fun GetIndexes(): Array<Int> {
            return indexes.getList()!!
        }

        //public	int						GetNumVertices( void ) const { return verts.Num(); }
        //public	const idDrawVert *		GetVertices( void ) const { return verts.Ptr(); }
        //public	const int *				GetEdgeIndexes( void ) const { return edgeIndexes.Ptr(); }
        //public	const surfaceEdge_t *	GetEdges( void ) const { return edges.Ptr(); }
        //
        open fun Clear() {
            verts.Clear()
            indexes.Clear()
            edges.Clear()
            edgeIndexes.Clear()
        }

        fun SwapTriangles(surf: idSurface) {
            verts.Swap(surf.verts)
            indexes.Swap(surf.indexes)
            edges.Swap(surf.edges)
            edgeIndexes.Swap(surf.edgeIndexes)
        }

        fun TranslateSelf(translation: idVec3) {
            for (i in 0 until verts.Num()) {
                verts[i].xyz.plusAssign(translation)
            }
        }

        fun RotateSelf(rotation: idMat3) {
            for (i in 0 until verts.Num()) {
                verts[i].xyz.timesAssign(rotation)
                verts[i].normal.timesAssign(rotation)
                verts[i].tangents[0].timesAssign(rotation)
                verts[i].tangents[1].timesAssign(rotation)
            }
        }

        //
        // splits the surface into a front and back surface, the surface itself stays unchanged
        // frontOnPlaneEdges and backOnPlaneEdges optionally store the indexes to the edges that lay on the split plane
        // returns a SIDE_?
        @JvmOverloads
        fun Split(
            plane: idPlane,
            epsilon: Float,
            front: Array<Array<idSurface?>?>,
            back: Array<Array<idSurface?>?>,
            frontOnPlaneEdges: IntArray? = null,
            backOnPlaneEdges: IntArray? = null
        ): Int {
            val dists: FloatArray
            var f: Float
            val sides: IntArray
            val counts = IntArray(3)
            val edgeSplitVertex: IntArray
            var numEdgeSplitVertexes: Int
            val vertexRemap = arrayOfNulls<IntArray?>(2)
            val vertexIndexNum = Array<IntArray?>(2) { IntArray(2) }
            val vertexCopyIndex = arrayOfNulls<IntArray?>(2)
            val indexPtr = arrayOfNulls<Array<Int?>?>(2)
            val indexNum = IntArray(2)
            var index: Array<Int?>?
            val onPlaneEdges = arrayOfNulls<IntArray?>(2)
            val numOnPlaneEdges = IntArray(2)
            var maxOnPlaneEdges: Int
            var i: Int
            val surface = arrayOfNulls<idSurface?>(2)
            val v = idDrawVert()
            dists = FloatArray(verts.Num())
            sides = IntArray(verts.Num())
            counts[2] = 0
            counts[1] = counts[2]
            counts[0] = counts[1]

            // determine side for each vertex
            i = 0
            while (i < verts.Num()) {
                f = plane.Distance(verts[i].xyz)
                dists[i] = f
                if (f > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (f < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts.get(sides[i])++
                i++
            }
            back.get(0) = null
            front.get(0) = back.get(0)

            // if coplanar, put on the front side if the normals match
            if (0 == counts[Plane.SIDE_FRONT] && 0 == counts[Plane.SIDE_BACK]) {
                f = verts[indexes[1]].xyz.minus(verts[indexes[0]].xyz).Cross(
                    verts[indexes[0]].xyz.minus(verts[indexes[2]].xyz)
                ).oMultiply(plane.Normal())
                return if (Math_h.FLOATSIGNBITSET(f) != 0) {
                    back.get(0).get(0) = idSurface(this) //TODO:check deref
                    Plane.SIDE_BACK
                } else {
                    front.get(0).get(0) = idSurface(this)
                    Plane.SIDE_FRONT
                }
            }
            // if nothing at the front of the clipping plane
            if (0 == counts[Plane.SIDE_FRONT]) {
                back.get(0).get(0) = idSurface(this)
                return Plane.SIDE_BACK
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[Plane.SIDE_BACK]) {
                front.get(0).get(0) = idSurface(this)
                return Plane.SIDE_FRONT
            }

            // allocate front and back surface
            surface[0] = idSurface()
            front.get(0).get(0) = surface[0]
            surface[1] = idSurface()
            back.get(0).get(0) = surface[1]
            edgeSplitVertex = IntArray(edges.Num())
            numEdgeSplitVertexes = 0
            maxOnPlaneEdges = 4 * counts[Plane.SIDE_ON]
            counts[Plane.SIDE_ON] = 0
            counts[Plane.SIDE_BACK] = counts[Plane.SIDE_ON]
            counts[Plane.SIDE_FRONT] = counts[Plane.SIDE_BACK]

            // split edges
            i = 0
            while (i < edges.Num()) {
                val v0 = edges[i].verts[0]
                val v1 = edges[i].verts[1]
                val sidesOr: Int = sides[v0] or sides[v1]

                // if both vertexes are on the same side or one is on the clipping plane
                if (sides[v0] xor sides[v1] == 0 || sidesOr and Plane.SIDE_ON == 0) {
                    edgeSplitVertex[i] = -1
                    counts[sidesOr and Plane.SIDE_BACK]++
                    counts[Plane.SIDE_ON] += sidesOr and Plane.SIDE_ON shr 1
                } else {
                    f = dists[v0] / (dists[v0] - dists[v1])
                    v.LerpAll(verts[v0], verts[v1], f)
                    edgeSplitVertex[i] = numEdgeSplitVertexes++
                    surface[0].verts.Append(v)
                    surface[1].verts.Append(v)
                }
                i++
            }

            // each edge is shared by at most two triangles, as such there can never be more indexes than twice the number of edges
            surface[0].indexes.Resize((counts[Plane.SIDE_FRONT] + counts[Plane.SIDE_ON]) * 2 + numEdgeSplitVertexes * 4)
            surface[1].indexes.Resize((counts[Plane.SIDE_BACK] + counts[Plane.SIDE_ON]) * 2 + numEdgeSplitVertexes * 4)

            // allocate indexes to construct the triangle indexes for the front and back surface
            vertexRemap[0] = IntArray(verts.Num())
            //	memset( vertexRemap[0], -1, verts.Num() * sizeof( int ) );
            Arrays.fill(vertexRemap[0], -1, 0, verts.Num())
            vertexRemap[1] = IntArray(verts.Num())
            //	memset( vertexRemap[1], -1, verts.Num() * sizeof( int ) );
            Arrays.fill(vertexRemap[0], -1, 0, verts.Num())
            vertexCopyIndex[0] = IntArray(numEdgeSplitVertexes + verts.Num())
            vertexCopyIndex[1] = IntArray(numEdgeSplitVertexes + verts.Num())
            vertexIndexNum[1].get(0) = 0
            vertexIndexNum[0].get(0) = vertexIndexNum[1].get(0)
            vertexIndexNum[1].get(1) = numEdgeSplitVertexes
            vertexIndexNum[0].get(1) = vertexIndexNum[1].get(1)
            indexPtr[0] = surface[0].indexes.getList()
            indexPtr[1] = surface[1].indexes.getList()
            indexNum[0] = surface[0].indexes.Num()
            indexNum[1] = surface[1].indexes.Num()
            maxOnPlaneEdges += 4 * numEdgeSplitVertexes
            // allocate one more in case no triangles are actually split which may happen for a disconnected surface
            onPlaneEdges[0] = IntArray(maxOnPlaneEdges + 1)
            onPlaneEdges[1] = IntArray(maxOnPlaneEdges + 1)
            numOnPlaneEdges[1] = 0
            numOnPlaneEdges[0] = numOnPlaneEdges[1]

            // split surface triangles
            i = 0
            while (i < edgeIndexes.Num()) {
                var e0: Int
                var e1: Int
                var e2: Int
                var v0: Int
                var v1: Int
                var v2: Int
                var s: Int
                var n: Int
                e0 = Math.abs(edgeIndexes[i + 0])
                e1 = Math.abs(edgeIndexes[i + 1])
                e2 = Math.abs(edgeIndexes[i + 2])
                v0 = indexes[i + 0]
                v1 = indexes[i + 1]
                v2 = indexes[i + 2]
                when (Math_h.INTSIGNBITSET(edgeSplitVertex[e0]) or (Math_h.INTSIGNBITSET(edgeSplitVertex[e1]) shl 1) or (Math_h.INTSIGNBITSET(
                    edgeSplitVertex[e2]
                ) shl 2) xor 7) {
                    0 -> {
                        // no edges split
                        if (sides[v0] and sides[v1] and sides[v2] and Plane.SIDE_ON != 0) {
                            // coplanar
                            f = verts[v1].xyz.minus(verts[v0].xyz)
                                .Cross(verts[v0].xyz.minus(verts[v2].xyz)).oMultiply(plane.Normal())
                            s = Math_h.FLOATSIGNBITSET(f)
                        } else {
                            s = sides[v0] or sides[v1] or sides[v2] and Plane.SIDE_BACK
                        }
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]) = n
                        numOnPlaneEdges[s] += sides[v0] and sides[v1] shr 1
                        onPlaneEdges[s].get(numOnPlaneEdges[s]) = n + 1
                        numOnPlaneEdges[s] += sides[v1] and sides[v2] shr 1
                        onPlaneEdges[s].get(numOnPlaneEdges[s]) = n + 2
                        numOnPlaneEdges[s] += sides[v2] and sides[v0] shr 1
                        index = indexPtr[s]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        indexNum[s] = n
                    }
                    1 -> {
                        // first edge split
                        s = sides[v0] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        indexNum[s] = n
                    }
                    2 -> {
                        // second edge split
                        s = sides[v1] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        indexNum[s] = n
                    }
                    3 -> {
                        // first and second edge split
                        s = sides[v1] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        indexNum[s] = n
                    }
                    4 -> {
                        // third edge split
                        s = sides[v2] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        indexNum[s] = n
                    }
                    5 -> {
                        // first and third edge split
                        s = sides[v0] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) = edgeSplitVertex[e0]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        index.get(n++) = edgeSplitVertex[e2]
                        indexNum[s] = n
                    }
                    6 -> {
                        // second and third edge split
                        s = sides[v2] and Plane.SIDE_BACK
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v2)
                        indexNum[s] = n
                        s = s xor 1
                        n = indexNum[s]
                        onPlaneEdges[s].get(numOnPlaneEdges[s]++) = n
                        index = indexPtr[s]
                        index.get(n++) = edgeSplitVertex[e1]
                        index.get(n++) = edgeSplitVertex[e2]
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v0)
                        index.get(n++) =
                            Surface.UpdateVertexIndex(vertexIndexNum[s], vertexRemap[s], vertexCopyIndex[s], v1)
                        index.get(n++) = edgeSplitVertex[e2]
                        indexNum[s] = n
                    }
                }
                i += 3
            }
            surface[0].indexes.SetNum(indexNum[0], false)
            surface[1].indexes.SetNum(indexNum[1], false)

            // copy vertexes
            surface[0].verts.SetNum(vertexIndexNum[0].get(1), false)
            //            index = vertexCopyIndex[0];
            i = numEdgeSplitVertexes
            while (i < surface[0].verts.Num()) {
                surface[0].verts[i] = verts[vertexCopyIndex[0].get(i)]
                i++
            }
            surface[1].verts.SetNum(vertexIndexNum[1].get(1), false)
            //            index = vertexCopyIndex[1];
            i = numEdgeSplitVertexes
            while (i < surface[1].verts.Num()) {
                surface[1].verts[i] = verts[vertexCopyIndex[1].get(i)]
                i++
            }

            // generate edge indexes
            surface[0].GenerateEdgeIndexes()
            surface[1].GenerateEdgeIndexes()
            if (null != frontOnPlaneEdges) {
//		memcpy( frontOnPlaneEdges, onPlaneEdges[0], numOnPlaneEdges[0] * sizeof( int ) );
                System.arraycopy(onPlaneEdges[0], 0, frontOnPlaneEdges, 0, numOnPlaneEdges[0])
                frontOnPlaneEdges[numOnPlaneEdges[0]] = -1
            }
            if (null != backOnPlaneEdges) {
//		memcpy( backOnPlaneEdges, onPlaneEdges[1], numOnPlaneEdges[1] * sizeof( int ) );
                System.arraycopy(onPlaneEdges[1], 0, backOnPlaneEdges, 0, numOnPlaneEdges[1])
                backOnPlaneEdges[numOnPlaneEdges[1]] = -1
            }
            return Plane.SIDE_CROSS
        }

        // cuts off the part at the back side of the plane, returns true if some part was at the front
        // if there is nothing at the front the number of points is set to zero
        @JvmOverloads
        fun ClipInPlace(plane: idPlane?, epsilon: Float = Plane.ON_EPSILON, keepOn: Boolean = false): Boolean {
            val dists: FloatArray
            var f: Float
            val sides: ByteArray
            val counts = IntArray(3)
            var i: Int
            val edgeSplitVertex: IntArray
            val vertexRemap: IntArray
            val vertexIndexNum = IntArray(2)
            val vertexCopyIndex: IntArray
            val indexPtr: Array<Int?>
            val indexNum: Int
            var numEdgeSplitVertexes: Int
            val v = idDrawVert()
            val newVerts = idList<idDrawVert?>()
            val newIndexes = idList<Int?>()
            dists = FloatArray(verts.Num())
            sides = ByteArray(verts.Num())
            counts[2] = 0
            counts[1] = counts[2]
            counts[0] = counts[1]

            // determine side for each vertex
            i = 0
            while (i < verts.Num()) {
                f = plane.Distance(verts[i].xyz)
                dists[i] = f
                if (f > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (f < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts.get(sides[i])++
                i++
            }

            // if coplanar, put on the front side if the normals match
            if (0 == counts[Plane.SIDE_FRONT] && 0 == counts[Plane.SIDE_BACK]) {
                f = verts[indexes[1]].xyz.minus(verts[indexes[0]].xyz)
                    .Cross(verts[indexes[0]].xyz.minus(verts[indexes[2]].xyz))
                    .oMultiply(plane.Normal())
                return if (Math_h.FLOATSIGNBITSET(f) != 0) {
                    Clear()
                    false
                } else {
                    true
                }
            }
            // if nothing at the front of the clipping plane
            if (0 == counts[Plane.SIDE_FRONT]) {
                Clear()
                return false
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[Plane.SIDE_BACK]) {
                return true
            }
            edgeSplitVertex = IntArray(edges.Num())
            numEdgeSplitVertexes = 0
            counts[Plane.SIDE_BACK] = 0
            counts[Plane.SIDE_FRONT] = counts[Plane.SIDE_BACK]

            // split edges
            i = 0
            while (i < edges.Num()) {
                val v0 = edges[i].verts[0]
                val v1 = edges[i].verts[1]

                // if both vertexes are on the same side or one is on the clipping plane
                if (sides[v0] xor sides[v1] == 0 || sides[v0] or sides[v1] and Plane.SIDE_ON != 0) {
                    edgeSplitVertex[i] = -1
                    counts[sides[v0] or sides[v1] and Plane.SIDE_BACK]++
                } else {
                    f = dists[v0] / (dists[v0] - dists[v1])
                    v.LerpAll(verts[v0], verts[v1], f)
                    edgeSplitVertex[i] = numEdgeSplitVertexes++
                    newVerts.Append(v)
                }
                i++
            }

            // each edge is shared by at most two triangles, as such there can never be
            // more indexes than twice the number of edges
            newIndexes.Resize((counts[Plane.SIDE_FRONT] shl 1) + (numEdgeSplitVertexes shl 2))

            // allocate indexes to construct the triangle indexes for the front and back surface
            vertexRemap = IntArray(verts.Num())
            Arrays.fill(vertexRemap, -1, 0, verts.Num())
            vertexCopyIndex = IntArray(numEdgeSplitVertexes + verts.Num())
            vertexIndexNum[0] = 0
            vertexIndexNum[1] = numEdgeSplitVertexes
            indexPtr = newIndexes.list
            indexNum = newIndexes.Num()

            // split surface triangles
            i = 0
            while (i < edgeIndexes.Num()) {
                var e0: Int
                var e1: Int
                var e2: Int
                var v0: Int
                var v1: Int
                var v2: Int
                e0 = Math.abs(edgeIndexes[i + 0])
                e1 = Math.abs(edgeIndexes[i + 1])
                e2 = Math.abs(edgeIndexes[i + 2])
                v0 = indexes[i + 0]
                v1 = indexes[i + 1]
                v2 = indexes[i + 2]
                when (Math_h.INTSIGNBITSET(edgeSplitVertex[e0]) or (Math_h.INTSIGNBITSET(edgeSplitVertex[e1]) shl 1) or (Math_h.INTSIGNBITSET(
                    edgeSplitVertex[e2]
                ) shl 2) xor 7) {
                    0 -> {
                        // no edges split
                        if (sides[v0] or sides[v1] or sides[v2] and Plane.SIDE_BACK != 0) {
                            break
                        }
                        if (sides[v0] and sides[v1] and sides[v2] and Plane.SIDE_ON != 0) {
                            // coplanar
                            if (!keepOn) {
                                break
                            }
                            f = verts[v1].xyz.minus(verts[v0].xyz)
                                .Cross(verts[v0].xyz.minus(verts[v2].xyz)).oMultiply(plane.Normal())
                            if (Math_h.FLOATSIGNBITSET(f) != 0) {
                                break
                            }
                        }
                        indexPtr[indexNum++] =
                            Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                        indexPtr[indexNum++] =
                            Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                        indexPtr[indexNum++] =
                            Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                    }
                    1 -> {
                        // first edge split
                        if (sides[v0] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                        } else {
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                        }
                    }
                    2 -> {
                        // second edge split
                        if (sides[v1] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                        } else {
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                        }
                    }
                    3 -> {
                        // first and second edge split
                        if (sides[v1] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                        } else {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                        }
                    }
                    4 -> {
                        // third edge split
                        if (sides[v2] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                        } else {
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                        }
                    }
                    5 -> {
                        // first and third edge split
                        if (sides[v0] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                        } else {
                            indexPtr[indexNum++] = edgeSplitVertex[e0]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                        }
                    }
                    6 -> {
                        // second and third edge split
                        if (sides[v2] and Plane.SIDE_BACK == 0) {
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v2)
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                        } else {
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] = edgeSplitVertex[e1]
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v0)
                            indexPtr[indexNum++] =
                                Surface.UpdateVertexIndex(vertexIndexNum, vertexRemap, vertexCopyIndex, v1)
                            indexPtr[indexNum++] = edgeSplitVertex[e2]
                        }
                    }
                }
                i += 3
            }
            newIndexes.SetNum(indexNum, false)

            // copy vertexes
            newVerts.SetNum(vertexIndexNum[1], false)
            i = numEdgeSplitVertexes
            while (i < newVerts.Num()) {
                newVerts[i] = verts[vertexCopyIndex[i]]
                i++
            }

            // copy back to this surface
            indexes.set(newIndexes)
            verts.set(newVerts)
            GenerateEdgeIndexes()
            return true
        }

        //
        // returns true if each triangle can be reached from any other triangle by a traversal
        fun IsConnected(): Boolean {
            var i: Int
            var j: Int
            var numIslands: Int
            val numTris: Int
            var queueStart: Int
            var queueEnd: Int
            val queue: IntArray
            val islandNum: IntArray
            var curTri: Int
            var nextTri: Int
            var edgeNum: Int
            var index: Int
            numIslands = 0
            numTris = indexes.Num() / 3
            islandNum = IntArray(numTris)
            Arrays.fill(islandNum, -1, 0, numTris)
            queue = IntArray(numTris)
            i = 0
            while (i < numTris) {
                if (islandNum[i] != -1) {
                    i++
                    continue
                }
                queueStart = 0
                queueEnd = 1
                queue[0] = i
                islandNum[i] = numIslands
                curTri = queue[queueStart]
                while (queueStart < queueEnd) {
                    index = curTri * 3
                    j = 0
                    while (j < 3) {
                        edgeNum = edgeIndexes[index + j]
                        nextTri = edges[Math.abs(edgeNum)].tris[Math_h.INTSIGNBITNOTSET(edgeNum)]
                        if (nextTri == -1) {
                            j++
                            continue
                        }
                        nextTri /= 3
                        if (islandNum[nextTri] != -1) {
                            j++
                            continue
                        }
                        queue[queueEnd++] = nextTri
                        islandNum[nextTri] = numIslands
                        j++
                    }
                    curTri = queue[++queueStart]
                }
                numIslands++
                i++
            }
            return numIslands == 1
        }

        // returns true if the surface is closed
        fun IsClosed(): Boolean {
            for (i in 0 until edges.Num()) {
                if (edges[i].tris[0] < 0 || edges[i].tris[1] < 0) {
                    return false
                }
            }
            return true
        }

        // returns true if the surface is a convex hull
        @JvmOverloads
        fun IsPolytope(epsilon: Float = 0.1f): Boolean {
            var i: Int
            var j: Int
            val plane = idPlane()
            if (!IsClosed()) {
                return false
            }
            i = 0
            while (i < indexes.Num()) {
                plane.FromPoints(
                    verts[indexes[i + 0]].xyz,
                    verts[indexes[i + 1]].xyz,
                    verts[indexes[i + 2]].xyz
                )
                j = 0
                while (j < verts.Num()) {
                    if (plane.Side(verts[j].xyz, epsilon) == Plane.SIDE_FRONT) {
                        return false
                    }
                    j++
                }
                i += 3
            }
            return true
        }

        //
        fun PlaneDistance(plane: idPlane?): Float {
            var i: Int
            var d: Float
            var min: Float
            var max: Float
            min = idMath.INFINITY
            max = -min
            i = 0
            while (i < verts.Num()) {
                d = plane.Distance(verts[i].xyz)
                if (d < min) {
                    min = d
                    if (Math_h.FLOATSIGNBITSET(min) and Math_h.FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                if (d > max) {
                    max = d
                    if (Math_h.FLOATSIGNBITSET(min) and Math_h.FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                i++
            }
            if (Math_h.FLOATSIGNBITNOTSET(min) != 0) {
                return min
            }
            return if (Math_h.FLOATSIGNBITSET(max) != 0) {
                max
            } else 0.0f
        }

        @JvmOverloads
        fun PlaneSide(plane: idPlane?, epsilon: Float = Plane.ON_EPSILON): Int {
            var front: Boolean
            var back: Boolean
            var i: Int
            var d: Float
            front = false
            back = false
            i = 0
            while (i < verts.Num()) {
                d = plane.Distance(verts[i].xyz)
                if (d < -epsilon) {
                    if (front) {
                        return Plane.SIDE_CROSS
                    }
                    back = true
                    i++
                    continue
                } else if (d > epsilon) {
                    if (back) {
                        return Plane.SIDE_CROSS
                    }
                    front = true
                    i++
                    continue
                }
                i++
            }
            if (back) {
                return Plane.SIDE_BACK
            }
            return if (front) {
                Plane.SIDE_FRONT
            } else Plane.SIDE_ON
        }

        // returns true if the line intersects one of the surface triangles
        //
        @JvmOverloads
        fun LineIntersection(start: idVec3, end: idVec3, backFaceCull: Boolean = false): Boolean {
            val scale = CFloat()
            RayIntersection(start, end.minus(start), scale, false)
            return scale.getVal() >= 0.0f && scale.getVal() <= 1.0f
        }

        // intersection point is start + dir * scale
        @JvmOverloads
        fun RayIntersection(start: idVec3, dir: idVec3, scale: CFloat?, backFaceCull: Boolean = false): Boolean {
            var i: Int
            var i0: Int
            var i1: Int
            var i2: Int
            var s0: Int
            var s1: Int
            var s2: Int
            var d: Float
            val s = CFloat()
            val sidedness: ByteArray
            val rayPl = idPluecker()
            val pl = idPluecker()
            val plane = idPlane()
            sidedness = ByteArray(edges.Num())
            scale.setVal(idMath.INFINITY)
            rayPl.FromRay(start, dir)

            // ray sidedness for edges
            i = 0
            while (i < edges.Num()) {
                pl.FromLine(verts[edges[i].verts[1]].xyz, verts[edges[i].verts[0]].xyz)
                d = pl.PermutedInnerProduct(rayPl)
                sidedness[i] = Math_h.FLOATSIGNBITSET(d).toByte()
                i++
            }

            // test triangles
            i = 0
            while (i < edgeIndexes.Num()) {
                i0 = edgeIndexes[i + 0]
                i1 = edgeIndexes[i + 1]
                i2 = edgeIndexes[i + 2]
                s0 = sidedness[Math.abs(i0)] xor Math_h.INTSIGNBITSET(i0)
                s1 = sidedness[Math.abs(i1)] xor Math_h.INTSIGNBITSET(i1)
                s2 = sidedness[Math.abs(i2)] xor Math_h.INTSIGNBITSET(i2)
                if (s0 and s1 and s2 != 0) {
                    plane.FromPoints(
                        verts[indexes[i + 0]].xyz,
                        verts[indexes[i + 1]].xyz,
                        verts[indexes[i + 2]].xyz
                    )
                    plane.RayIntersection(start, dir, s)
                    if (Math.abs(s.getVal()) < Math.abs(scale.getVal())) {
                        scale.setVal(s.getVal())
                    }
                } else if (!backFaceCull && s0 or s1 or s2 == 0) {
                    plane.FromPoints(
                        verts[indexes[i + 0]].xyz,
                        verts[indexes[i + 1]].xyz,
                        verts[indexes[i + 2]].xyz
                    )
                    plane.RayIntersection(start, dir, s)
                    if (Math.abs(s.getVal()) < Math.abs(scale.getVal())) {
                        scale.setVal(s.getVal())
                    }
                }
                i += 3
            }
            return Math.abs(scale.getVal()) < idMath.INFINITY
        }

        /*
         =================
         idSurface::GenerateEdgeIndexes

         Assumes each edge is shared by at most two triangles.
         =================
         */
        protected fun GenerateEdgeIndexes() {
            var i: Int
            var j: Int
            var i0: Int
            var i1: Int
            var i2: Int
            var s: Int
            var v0: Int
            var v1: Int
            var edgeNum: Int
            val vertexEdges: IntArray
            val edgeChain: IntArray
            var index: Array<Int?>?
            val e = surfaceEdge_t.generateArray(3)
            vertexEdges = IntArray(verts.Num())
            Arrays.fill(vertexEdges, 0, verts.Num(), -1)
            edgeChain = IntArray(indexes.Num())
            edgeIndexes.SetNum(indexes.Num(), true)
            edges.Clear()

            // the first edge is a dummy
            e[0].tris[1] = 0
            e[0].tris[0] = e[0].tris[1]
            e[0].verts[1] = e[0].tris[0]
            e[0].verts[0] = e[0].verts[1]
            edges.Append(e[0])
            i = 0
            while (i < indexes.Num()) {
                index = indexes.getList() //index = indexes.Ptr() + i;
                // vertex numbers
                i0 = index[i + 0]
                i1 = index[i + 1]
                i2 = index[i + 2]
                // setup edges each with smallest vertex number first
                s = Math_h.INTSIGNBITSET(i1 - i0)
                e[0].verts[0] = index[i + s]
                e[0].verts[1] = index[i + s xor 1]
                s = Math_h.INTSIGNBITSET(i2 - i1) + 1
                e[1].verts[0] = index[i + s]
                e[1].verts[1] = index[i + s xor 3]
                s = Math_h.INTSIGNBITSET(i2 - i0) shl 1
                e[2].verts[0] = index[i + s]
                e[2].verts[1] = index[i + s xor 2]
                // get edges
                j = 0
                while (j < 3) {
                    v0 = e[j].verts[0]
                    v1 = e[j].verts[1]
                    edgeNum = vertexEdges[v0]
                    while (edgeNum >= 0) {
                        if (edges[edgeNum].verts[1] == v1) {
                            break
                        }
                        edgeNum = edgeChain[edgeNum]
                    }
                    // if the edge does not yet exist
                    if (edgeNum < 0) {
                        e[j].tris[1] = -1
                        e[j].tris[0] = e[j].tris[1]
                        edgeNum = edges.Append(e[j])
                        edgeChain[edgeNum] = vertexEdges[v0]
                        vertexEdges[v0] = edgeNum
                    }
                    // update edge index and edge tri references
                    if (index[i + j] == v0) {
                        assert(
                            edges[edgeNum].tris[0] == -1 // edge may not be shared by more than two triangles
                        )
                        edges[edgeNum].tris[0] = i
                        edgeIndexes[i + j] = edgeNum
                    } else {
                        assert(
                            edges[edgeNum].tris[1] == -1 // edge may not be shared by more than two triangles
                        )
                        edges[edgeNum].tris[1] = i
                        edgeIndexes[i + j] = -edgeNum
                    }
                    j++
                }
                i += 3
            }
        }

        protected fun FindEdge(v1: Int, v2: Int): Int {
            var i: Int
            val firstVert: Int
            val secondVert: Int
            if (v1 < v2) {
                firstVert = v1
                secondVert = v2
            } else {
                firstVert = v2
                secondVert = v1
            }
            i = 1
            while (i < edges.Num()) {
                if (edges[i].verts[0] == firstVert) {
                    if (edges[i].verts[1] == secondVert) {
                        break
                    }
                }
                i++
            }
            return if (i < edges.Num()) {
                if (v1 < v2) i else -i
            } else 0
        }
    }
}