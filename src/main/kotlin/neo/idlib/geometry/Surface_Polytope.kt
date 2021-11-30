package neo.idlib.geometry

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Math_h
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object Surface_Polytope {
    const val POLYTOPE_VERTEX_EPSILON = 0.1f

    /*
     ===============================================================================

     Polytope surface.

     NOTE: vertexes are not duplicated for texture coordinates.

     ===============================================================================
     */
    internal inner class idSurface_Polytope : idSurface() {
        fun FromPlanes(planes: Array<idPlane?>?, numPlanes: Int) {
            var i: Int
            var j: Int
            var k: Int
            val windingVerts: IntArray
            val w = idFixedWinding()
            val newVert = idDrawVert()
            windingVerts = IntArray(Winding.MAX_POINTS_ON_WINDING)
            //	memset( &newVert, 0, sizeof( newVert ) );
            i = 0
            while (i < numPlanes) {
                w.BaseForPlane(planes.get(i))
                j = 0
                while (j < numPlanes) {
                    if (j == i) {
                        j++
                        continue
                    }
                    if (!w.ClipInPlace(planes.get(j).unaryMinus(), Plane.ON_EPSILON, true)) {
                        break
                    }
                    j++
                }
                if (0 == w.GetNumPoints()) {
                    i++
                    continue
                }
                j = 0
                while (j < w.GetNumPoints()) {
                    k = 0
                    while (k < verts.Num()) {
                        if (verts.get(k).xyz.Compare(w.oGet(j).ToVec3(), Surface_Polytope.POLYTOPE_VERTEX_EPSILON)) {
                            break
                        }
                        j++
                    }
                    if (k >= verts.Num()) {
                        newVert.xyz.set(w.oGet(j).ToVec3())
                        k = verts.Append(newVert)
                    }
                    windingVerts[j] = k
                    j++
                }
                j = 2
                while (j < w.GetNumPoints()) {
                    indexes.Append(windingVerts[0])
                    indexes.Append(windingVerts[j - 1])
                    indexes.Append(windingVerts[j])
                    j++
                }
                i++
            }
            GenerateEdgeIndexes()
        }

        fun SetupTetrahedron(bounds: idBounds?) {
            val center = idVec3()
            val scale = idVec3()
            val c1: Float
            val c2: Float
            val c3: Float
            c1 = 0.4714045207f
            c2 = 0.8164965809f
            c3 = -0.3333333333f
            center.set(bounds.GetCenter())
            scale.set(bounds.get(1).minus(center))
            verts.SetNum(4)
            verts.get(0).xyz.set(center.oPlus(idVec3(0.0f, 0.0f, scale.z)))
            verts.get(1).xyz.set(center.oPlus(idVec3(2.0f * c1 * scale.x, 0.0f, c3 * scale.z)))
            verts.get(2).xyz.set(center.oPlus(idVec3(-c1 * scale.x, c2 * scale.y, c3 * scale.z)))
            verts.get(3).xyz.set(center.oPlus(idVec3(-c1 * scale.x, -c2 * scale.y, c3 * scale.z)))
            indexes.SetNum(4 * 3)
            indexes.set(0 * 3 + 0, 0)
            indexes.set(0 * 3 + 1, 1)
            indexes.set(0 * 3 + 2, 2)
            indexes.set(1 * 3 + 0, 0)
            indexes.set(1 * 3 + 1, 2)
            indexes.set(1 * 3 + 2, 3)
            indexes.set(2 * 3 + 0, 0)
            indexes.set(2 * 3 + 1, 3)
            indexes.set(2 * 3 + 2, 1)
            indexes.set(3 * 3 + 0, 1)
            indexes.set(3 * 3 + 1, 3)
            indexes.set(3 * 3 + 2, 2)
            GenerateEdgeIndexes()
        }

        fun SetupHexahedron(bounds: idBounds?) {
            val center = idVec3()
            val scale = idVec3()
            center.set(bounds.GetCenter())
            scale.set(bounds.get(1).minus(center))
            verts.SetNum(8)
            verts.get(0).xyz.set(center.oPlus(idVec3(-scale.x, -scale.y, -scale.z)))
            verts.get(1).xyz.set(center.oPlus(idVec3(scale.x, -scale.y, -scale.z)))
            verts.get(2).xyz.set(center.oPlus(idVec3(scale.x, scale.y, -scale.z)))
            verts.get(3).xyz.set(center.oPlus(idVec3(-scale.x, scale.y, -scale.z)))
            verts.get(4).xyz.set(center.oPlus(idVec3(-scale.x, -scale.y, scale.z)))
            verts.get(5).xyz.set(center.oPlus(idVec3(scale.x, -scale.y, scale.z)))
            verts.get(6).xyz.set(center.oPlus(idVec3(scale.x, scale.y, scale.z)))
            verts.get(7).xyz.set(center.oPlus(idVec3(-scale.x, scale.y, scale.z)))
            indexes.SetNum(12 * 3)
            indexes.set(0 * 3 + 0, 0)
            indexes.set(0 * 3 + 1, 3)
            indexes.set(0 * 3 + 2, 2)
            indexes.set(1 * 3 + 0, 0)
            indexes.set(1 * 3 + 1, 2)
            indexes.set(1 * 3 + 2, 1)
            indexes.set(2 * 3 + 0, 0)
            indexes.set(2 * 3 + 1, 1)
            indexes.set(2 * 3 + 2, 5)
            indexes.set(3 * 3 + 0, 0)
            indexes.set(3 * 3 + 1, 5)
            indexes.set(3 * 3 + 2, 4)
            indexes.set(4 * 3 + 0, 0)
            indexes.set(4 * 3 + 1, 4)
            indexes.set(4 * 3 + 2, 7)
            indexes.set(5 * 3 + 0, 0)
            indexes.set(5 * 3 + 1, 7)
            indexes.set(5 * 3 + 2, 3)
            indexes.set(6 * 3 + 0, 6)
            indexes.set(6 * 3 + 1, 5)
            indexes.set(6 * 3 + 2, 1)
            indexes.set(7 * 3 + 0, 6)
            indexes.set(7 * 3 + 1, 1)
            indexes.set(7 * 3 + 2, 2)
            indexes.set(8 * 3 + 0, 6)
            indexes.set(8 * 3 + 1, 2)
            indexes.set(8 * 3 + 2, 3)
            indexes.set(9 * 3 + 0, 6)
            indexes.set(9 * 3 + 1, 3)
            indexes.set(9 * 3 + 2, 7)
            indexes.set(10 * 3 + 0, 6)
            indexes.set(10 * 3 + 1, 7)
            indexes.set(10 * 3 + 2, 4)
            indexes.set(11 * 3 + 0, 6)
            indexes.set(11 * 3 + 1, 4)
            indexes.set(11 * 3 + 2, 5)
            GenerateEdgeIndexes()
        }

        fun SetupOctahedron(bounds: idBounds?) {
            val center = idVec3()
            val scale = idVec3()
            center.set(bounds.GetCenter())
            scale.set(bounds.get(1).minus(center))
            verts.SetNum(6)
            verts.get(0).xyz.set(center.oPlus(idVec3(scale.x, 0.0f, 0.0f)))
            verts.get(1).xyz.set(center.oPlus(idVec3(-scale.x, 0.0f, 0.0f)))
            verts.get(2).xyz.set(center.oPlus(idVec3(0.0f, scale.y, 0.0f)))
            verts.get(3).xyz.set(center.oPlus(idVec3(0.0f, -scale.y, 0.0f)))
            verts.get(4).xyz.set(center.oPlus(idVec3(0.0f, 0.0f, scale.z)))
            verts.get(5).xyz.set(center.oPlus(idVec3(0.0f, 0.0f, -scale.z)))
            indexes.SetNum(8 * 3)
            indexes.set(0 * 3 + 0, 4)
            indexes.set(0 * 3 + 1, 0)
            indexes.set(0 * 3 + 2, 2)
            indexes.set(1 * 3 + 0, 4)
            indexes.set(1 * 3 + 1, 2)
            indexes.set(1 * 3 + 2, 1)
            indexes.set(2 * 3 + 0, 4)
            indexes.set(2 * 3 + 1, 1)
            indexes.set(2 * 3 + 2, 3)
            indexes.set(3 * 3 + 0, 4)
            indexes.set(3 * 3 + 1, 3)
            indexes.set(3 * 3 + 2, 0)
            indexes.set(4 * 3 + 0, 5)
            indexes.set(4 * 3 + 1, 2)
            indexes.set(4 * 3 + 2, 0)
            indexes.set(5 * 3 + 0, 5)
            indexes.set(5 * 3 + 1, 1)
            indexes.set(5 * 3 + 2, 0)
            indexes.set(6 * 3 + 0, 5)
            indexes.set(6 * 3 + 1, 3)
            indexes.set(6 * 3 + 2, 1)
            indexes.set(7 * 3 + 0, 5)
            indexes.set(7 * 3 + 1, 0)
            indexes.set(7 * 3 + 2, 3)
            GenerateEdgeIndexes()
        }

        //public	void				SetupDodecahedron( const idBounds &bounds );
        //public	void				SetupIcosahedron( const idBounds &bounds );
        //public	void				SetupCylinder( const idBounds &bounds, const int numSides );
        //public	void				SetupCone( const idBounds &bounds, const int numSides );
        //
        fun SplitPolytope(
            plane: idPlane?,
            epsilon: Float,
            front: Array<idSurface_Polytope?>?,
            back: Array<idSurface_Polytope?>?
        ): Int {
            val side: Int
            var i: Int
            var j: Int
            var s: Int
            var v0: Int
            var v1: Int
            var v2: Int
            var edgeNum: Int
            val surface = Array<Array<Array<idSurface?>?>?>(2) { Array(1) { arrayOfNulls<idSurface?>(1) } }
            val polytopeSurfaces = arrayOfNulls<idSurface_Polytope?>(2)
            var surf: idSurface_Polytope?
            val onPlaneEdges = arrayOfNulls<IntArray?>(2)
            onPlaneEdges[0] = IntArray(indexes.Num() / 3)
            onPlaneEdges[1] = IntArray(indexes.Num() / 3)
            side = Split(plane, epsilon, surface[0], surface[1], onPlaneEdges[0], onPlaneEdges[1])
            polytopeSurfaces[0] = idSurface_Polytope()
            front.get(0) = polytopeSurfaces[0]
            polytopeSurfaces[1] = idSurface_Polytope()
            back.get(0) = polytopeSurfaces[1]
            s = 0
            while (s < 2) {
                if (surface[s].get(1).get(1) != null) {
                    polytopeSurfaces[s] = idSurface_Polytope()
                    polytopeSurfaces[s].SwapTriangles(surface[s].get(1).get(1))
                    //                    delete surface[s];
                    surface[s].get(1).get(1) = null
                }
                s++
            }
            front.get(0) = polytopeSurfaces[0]
            back.get(0) = polytopeSurfaces[1]
            if (side != Plane.SIDE_CROSS) {
                return side
            }

            // add triangles to close off the front and back polytope
            s = 0
            while (s < 2) {
                surf = polytopeSurfaces[s]
                edgeNum = surf.edgeIndexes.get(onPlaneEdges[s].get(0))
                v0 = surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITSET(edgeNum)]
                v1 = surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITNOTSET(edgeNum)]
                i = 1
                while (onPlaneEdges[s].get(i) >= 0) {
                    j = i + 1
                    while (onPlaneEdges[s].get(j) >= 0) {
                        edgeNum = surf.edgeIndexes.get(onPlaneEdges[s].get(j))
                        if (v1 == surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITSET(edgeNum)]) {
                            v1 = surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITNOTSET(edgeNum)]
                            List.idSwap(onPlaneEdges, s, i, onPlaneEdges, s, j)
                            break
                        }
                        j++
                    }
                    i++
                }
                i = 2
                while (onPlaneEdges[s].get(i) >= 0) {
                    edgeNum = surf.edgeIndexes.get(onPlaneEdges[s].get(i))
                    v1 = surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITNOTSET(edgeNum)]
                    v2 = surf.edges.get(Math.abs(edgeNum)).verts[Math_h.INTSIGNBITSET(edgeNum)]
                    surf.indexes.Append(v0)
                    surf.indexes.Append(v1)
                    surf.indexes.Append(v2)
                    i++
                }
                surf.GenerateEdgeIndexes()
                s++
            }
            return side
        }
    }
}