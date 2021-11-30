package neo.idlib.geometry

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib.idLib
import neo.idlib.containers.CFloat
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector.idVec3
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object TraceModel {
    const val MAX_TRACEMODEL_EDGES = 32
    const val MAX_TRACEMODEL_POLYEDGES = 16
    const val MAX_TRACEMODEL_POLYS = 16

    // these are bit cache limits
    const val MAX_TRACEMODEL_VERTS = 32

    /*
     ===============================================================================

     A trace model is an arbitrary polygonal model which is used by the
     collision detection system to find collisions, contacts or the contents
     of a volume. For collision detection speed reasons the number of vertices
     and edges are limited. The trace model can have any shape. However convex
     models are usually preferred.

     ===============================================================================
     */
    // trace model type
    enum class traceModel_t {
        TRM_INVALID,  // invalid trm
        TRM_BOX,  // box
        TRM_OCTAHEDRON,  // octahedron
        TRM_DODECAHEDRON,  // dodecahedron
        TRM_CYLINDER,  // cylinder approximation
        TRM_CONE,  // cone approximation
        TRM_BONE,  // two tetrahedrons attached to each other
        TRM_POLYGON,  // arbitrary convex polygon
        TRM_POLYGONVOLUME,  // volume for arbitrary convex polygon
        TRM_CUSTOM // loaded from map model or ASE/LWO
    }

    class traceModelVert_t : idVec3()
    class traceModelEdge_t {
        val normal: idVec3 = idVec3()
        var v: IntArray = IntArray(2)
        fun oSet(t: traceModelEdge_t) {
            v.get(0) = t.v.get(0)
            v.get(1) = t.v.get(1)
            normal.set(t.normal)
        }
    }

    class traceModelPoly_t {
        val bounds: idBounds = idBounds()
        var dist = 0f
        val edges: IntArray = IntArray(TraceModel.MAX_TRACEMODEL_POLYEDGES)
        val normal: idVec3 = idVec3()
        var numEdges = 0
        private fun oSet(t: traceModelPoly_t) {
            normal.set(t.normal)
            dist = t.dist
            bounds.set(t.bounds)
            numEdges = t.numEdges
            System.arraycopy(t.edges, 0, edges, 0, TraceModel.MAX_TRACEMODEL_POLYEDGES)
        }

    }

    class idTraceModel() {
        var bounds // bounds of model
                : idBounds
        var edges = Stream.generate { traceModelEdge_t() }.limit((TraceModel.MAX_TRACEMODEL_EDGES + 1).toLong())
            .toArray<traceModelEdge_t?> { _Dummy_.__Array__() }
        var isConvex // true when model is convex
                = false
        var numEdges: Int
        var numPolys: Int
        var numVerts: Int
        val offset // offset to center of model
                : idVec3
        var polys = Stream.generate { traceModelPoly_t() }.limit(TraceModel.MAX_TRACEMODEL_POLYS.toLong())
            .toArray<traceModelPoly_t?> { _Dummy_.__Array__() }

        //
        var type: traceModel_t?
        var verts = Stream.generate { traceModelVert_t() }.limit(TraceModel.MAX_TRACEMODEL_VERTS.toLong())
            .toArray<traceModelVert_t?> { _Dummy_.__Array__() }

        // axial bounding box
        constructor(boxBounds: idBounds?) : this() {
            InitBox()
            SetupBox(boxBounds)
        }

        // cylinder approximation
        constructor(cylBounds: idBounds?, numSides: Int) : this() {
            SetupCylinder(cylBounds, numSides)
        }

        // bone
        constructor(length: Float, width: Float) : this() {
            InitBone()
            SetupBone(length, width)
        }

        // axial box
        fun SetupBox(boxBounds: idBounds) {
            var i: Int
            if (type != traceModel_t.TRM_BOX) {
                InitBox()
            }
            // offset to center
            offset.set(boxBounds.get(0).oPlus(boxBounds.get(1)).oMultiply(0.5f))
            // set box vertices
            i = 0
            while (i < 8) {
                verts[i].set(0, boxBounds.get(i xor (i shr 1) and 1).oGet(0))
                verts[i].set(1, boxBounds.get(i shr 1 and 1).oGet(1))
                verts[i].set(2, boxBounds.get(i shr 2 and 1).oGet(2))
                i++
            }
            // set polygon plane distances
            polys[0].dist = -boxBounds.get(0).oGet(2)
            polys[1].dist = boxBounds.get(1).oGet(2)
            polys[2].dist = -boxBounds.get(0).oGet(1)
            polys[3].dist = boxBounds.get(1).oGet(0)
            polys[4].dist = boxBounds.get(1).oGet(1)
            polys[5].dist = -boxBounds.get(0).oGet(0)
            // set polygon bounds
            i = 0
            while (i < 6) {
                polys[i].bounds.set(boxBounds)
                i++
            }
            polys[0].bounds.set(1, 2, boxBounds.get(0).oGet(2))
            polys[1].bounds.set(0, 2, boxBounds.get(1).oGet(2))
            polys[2].bounds.set(1, 1, boxBounds.get(0).oGet(1))
            polys[3].bounds.set(0, 0, boxBounds.get(1).oGet(0))
            polys[4].bounds.set(0, 1, boxBounds.get(1).oGet(1))
            polys[5].bounds.set(1, 0, boxBounds.get(0).oGet(0))
            bounds.set(boxBounds)
        }

        /*
         ============
         idTraceModel::SetupBox

         The origin is placed at the center of the cube.
         ============
         */
        fun SetupBox(size: Float) {
            val boxBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            boxBounds.get(0).Set(-halfSize, -halfSize, -halfSize)
            boxBounds.get(1).Set(halfSize, halfSize, halfSize)
            SetupBox(boxBounds)
        }

        // octahedron
        fun SetupOctahedron(octBounds: idBounds?) {
            var i: Int
            var e0: Int
            var e1: Int
            var v0: Int
            var v1: Int
            var v2: Int
            val v = idVec3()
            if (type != traceModel_t.TRM_OCTAHEDRON) {
                InitOctahedron()
            }
            offset.set(octBounds.get(0).oPlus(octBounds.get(1)).oMultiply(0.5f))
            v.set(0, octBounds.get(1).oGet(0) - offset.get(0))
            v.set(1, octBounds.get(1).oGet(1) - offset.get(1))
            v.set(2, octBounds.get(1).oGet(2) - offset.get(2))

            // set vertices
            verts[0].set(offset.x + v.get(0), offset.y, offset.z)
            verts[1].set(offset.x - v.get(0), offset.y, offset.z)
            verts[2].set(offset.x, offset.y + v.get(1), offset.z)
            verts[3].set(offset.x, offset.y - v.get(1), offset.z)
            verts[4].set(offset.x, offset.y, offset.z + v.get(2))
            verts[5].set(offset.x, offset.y, offset.z - v.get(2))

            // set polygons
            i = 0
            while (i < numPolys) {
                e0 = polys[i].edges.get(0)
                e1 = polys[i].edges.get(1)
                v0 = edges[Math.abs(e0)].v.get(Math_h.INTSIGNBITSET(e0))
                v1 = edges[Math.abs(e0)].v.get(Math_h.INTSIGNBITNOTSET(e0))
                v2 = edges[Math.abs(e1)].v.get(Math_h.INTSIGNBITNOTSET(e1))
                // polygon plane
                polys[i].normal.set(verts[v1].minus(verts[v0]).Cross(verts[v2].minus(verts[v0])))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal.times(verts[v0])
                // polygon bounds
                polys[i].bounds.set(0, polys[i].bounds.set(0, verts[v0]))
                polys[i].bounds.AddPoint(verts[v1])
                polys[i].bounds.AddPoint(verts[v2])
                i++
            }

            // trm bounds
            bounds = octBounds
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupOctahedron

         The origin is placed at the center of the octahedron.
         ============
         */
        fun SetupOctahedron(size: Float) {
            val octBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            octBounds.get(0).Set(-halfSize, -halfSize, -halfSize)
            octBounds.get(1).Set(halfSize, halfSize, halfSize)
            SetupOctahedron(octBounds)
        }

        // dodecahedron
        fun SetupDodecahedron(dodBounds: idBounds?) {
            var i: Int
            var e0: Int
            var e1: Int
            var e2: Int
            var e3: Int
            var v0: Int
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            var s: Float
            val d: Float
            val a = idVec3()
            val b = idVec3()
            val c = idVec3()
            if (type != traceModel_t.TRM_DODECAHEDRON) {
                InitDodecahedron()
            }
            a.z = 0.5773502691896257f
            a.y = a.z
            a.x = a.y // 1.0f / ( 3.0f ) ^ 0.5f;
            b.z = 0.3568220897730899f
            b.y = b.z
            b.x = b.y // ( ( 3.0f - ( 5.0f ) ^ 0.5f ) / 6.0f ) ^ 0.5f;
            c.z = 0.9341723589627156f
            c.y = c.z
            c.x = c.y // ( ( 3.0f + ( 5.0f ) ^ 0.5f ) / 6.0f ) ^ 0.5f;
            d = 0.5f / c.get(0)
            s = (dodBounds.get(1).oGet(0) - dodBounds.get(0).oGet(0)) * d
            a.x *= s
            b.x *= s
            c.x *= s
            s = (dodBounds.get(1).oGet(1) - dodBounds.get(0).oGet(1)) * d
            a.y *= s
            b.y *= s
            c.y *= s
            s = (dodBounds.get(1).oGet(2) - dodBounds.get(0).oGet(2)) * d
            a.z *= s
            b.z *= s
            c.z *= s
            offset.set(dodBounds.get(0).oPlus(dodBounds.get(1)).oMultiply(0.5f))

            // set vertices
            verts[0].set(offset.x + a.get(0), offset.y + a.get(1), offset.z + a.get(2))
            verts[1].set(offset.x + a.get(0), offset.y + a.get(1), offset.z - a.get(2))
            verts[2].set(offset.x + a.get(0), offset.y - a.get(1), offset.z + a.get(2))
            verts[3].set(offset.x + a.get(0), offset.y - a.get(1), offset.z - a.get(2))
            verts[4].set(offset.x - a.get(0), offset.y + a.get(1), offset.z + a.get(2))
            verts[5].set(offset.x - a.get(0), offset.y + a.get(1), offset.z - a.get(2))
            verts[6].set(offset.x - a.get(0), offset.y - a.get(1), offset.z + a.get(2))
            verts[7].set(offset.x - a.get(0), offset.y - a.get(1), offset.z - a.get(2))
            verts[8].set(offset.x + b.get(0), offset.y + c.get(1), offset.z /*        */)
            verts[9].set(offset.x - b.get(0), offset.y + c.get(1), offset.z /*        */)
            verts[10].set(offset.x + b.get(0), offset.y - c.get(1), offset.z /*        */)
            verts[11].set(offset.x - b.get(0), offset.y - c.get(1), offset.z /*        */)
            verts[12].set(offset.x + c.get(0), offset.y /*        */, offset.z + b.get(2))
            verts[13].set(offset.x + c.get(0), offset.y /*        */, offset.z - b.get(2))
            verts[14].set(offset.x - c.get(0), offset.y /*        */, offset.z + b.get(2))
            verts[15].set(offset.x - c.get(0), offset.y /*        */, offset.z - b.get(2))
            verts[16].set(offset.x /*        */, offset.y + b.get(1), offset.z + c.get(2))
            verts[17].set(offset.x /*        */, offset.y - b.get(1), offset.z + c.get(2))
            verts[18].set(offset.x /*        */, offset.y + b.get(1), offset.z - c.get(2))
            verts[19].set(offset.x /*        */, offset.y - b.get(1), offset.z - c.get(2))

            // set polygons
            i = 0
            while (i < numPolys) {
                e0 = polys[i].edges.get(0)
                e1 = polys[i].edges.get(1)
                e2 = polys[i].edges.get(2)
                e3 = polys[i].edges.get(3)
                v0 = edges[Math.abs(e0)].v.get(Math_h.INTSIGNBITSET(e0))
                v1 = edges[Math.abs(e0)].v.get(Math_h.INTSIGNBITNOTSET(e0))
                v2 = edges[Math.abs(e1)].v.get(Math_h.INTSIGNBITNOTSET(e1))
                v3 = edges[Math.abs(e2)].v.get(Math_h.INTSIGNBITNOTSET(e2))
                v4 = edges[Math.abs(e3)].v.get(Math_h.INTSIGNBITNOTSET(e3))
                // polygon plane
                polys[i].normal.set(verts[v1].minus(verts[v0]).Cross(verts[v2].minus(verts[v0])))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal.times(verts[v0])
                // polygon bounds
                polys[i].bounds.set(0, polys[i].bounds.set(1, verts[v0]))
                polys[i].bounds.AddPoint(verts[v1])
                polys[i].bounds.AddPoint(verts[v2])
                polys[i].bounds.AddPoint(verts[v3])
                polys[i].bounds.AddPoint(verts[v4])
                i++
            }

            // trm bounds
            bounds = dodBounds
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupDodecahedron

         The origin is placed at the center of the octahedron.
         ============
         */
        fun SetupDodecahedron(size: Float) {
            val dodBounds = idBounds()
            val halfSize: Float
            halfSize = size * 0.5f
            dodBounds.get(0).Set(-halfSize, -halfSize, -halfSize)
            dodBounds.get(1).Set(halfSize, halfSize, halfSize)
            SetupDodecahedron(dodBounds)
        }

        // cylinder approximation
        fun SetupCylinder(cylBounds: idBounds?, numSides: Int) {
            var i: Int
            var n: Int
            var ii: Int
            var n2: Int
            var angle: Float
            val halfSize = idVec3()
            n = numSides
            if (n < 3) {
                n = 3
            }
            if (n * 2 > TraceModel.MAX_TRACEMODEL_VERTS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many vertices\n")
                n = TraceModel.MAX_TRACEMODEL_VERTS / 2
            }
            if (n * 3 > TraceModel.MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many sides\n")
                n = TraceModel.MAX_TRACEMODEL_EDGES / 3
            }
            if (n + 2 > TraceModel.MAX_TRACEMODEL_POLYS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCylinder: too many polygons\n")
                n = TraceModel.MAX_TRACEMODEL_POLYS - 2
            }
            type = traceModel_t.TRM_CYLINDER
            numVerts = n * 2
            numEdges = n * 3
            numPolys = n + 2
            offset.set(cylBounds.get(0).oPlus(cylBounds.get(1)).oMultiply(0.5f))
            halfSize.set(cylBounds.get(1).minus(offset))
            i = 0
            while (i < n) {

                // verts
                angle = idMath.TWO_PI * i / n
                verts[i].x = (Math.cos(angle.toDouble()) * halfSize.x + offset.x).toFloat()
                verts[i].y = (Math.sin(angle.toDouble()) * halfSize.y + offset.y).toFloat()
                verts[i].z = -halfSize.z + offset.z
                verts[n + i].x = verts[i].x
                verts[n + i].y = verts[i].y
                verts[n + i].z = halfSize.z + offset.z
                // edges
                ii = i + 1
                n2 = n shl 1
                edges[ii].v.get(0) = i
                edges[ii].v.get(1) = ii % n
                edges[n + ii].v.get(0) = edges[ii].v.get(0) + n
                edges[n + ii].v.get(1) = edges[ii].v.get(1) + n
                edges[n2 + ii].v.get(0) = i
                edges[n2 + ii].v.get(1) = n + i
                // vertical polygon edges
                polys[i].numEdges = 4
                polys[i].edges.get(0) = ii
                polys[i].edges.get(1) = n2 + ii % n + 1
                polys[i].edges.get(2) = -(n + ii)
                polys[i].edges.get(3) = -(n2 + ii)
                // bottom and top polygon edges
                polys[n].edges.get(i) = -(n - i)
                polys[n + 1].edges.get(i) = n + ii
                i++
            }
            // bottom and top polygon numEdges
            polys[n].numEdges = n
            polys[n + 1].numEdges = n
            // polygons
            i = 0
            while (i < n) {

                // vertical polygon plane
                polys[i].normal.set(verts[(i + 1) % n].minus(verts[i]).Cross(verts[n + i].minus(verts[i])))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal.times(verts[i])
                // vertical polygon bounds
                polys[i].bounds.Clear()
                polys[i].bounds.AddPoint(verts[i])
                polys[i].bounds.AddPoint(verts[(i + 1) % n])
                polys[i].bounds.set(0, 2, -halfSize.z + offset.z)
                polys[i].bounds.set(1, 2, halfSize.z + offset.z)
                i++
            }
            // bottom and top polygon plane
            polys[n].normal.set(0.0f, 0.0f, -1.0f)
            polys[n].dist = -cylBounds.get(0).oGet(2)
            polys[n + 1].normal.set(0.0f, 0.0f, 1.0f)
            polys[n + 1].dist = cylBounds.get(1).oGet(2)
            // trm bounds
            bounds = cylBounds
            // bottom and top polygon bounds
            polys[n].bounds.set(bounds)
            polys[n].bounds.set(1, 2, bounds.get(0).oGet(2))
            polys[n + 1].bounds.set(bounds)
            polys[n + 1].bounds.set(0, 2, bounds.get(1).oGet(2))
            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupCylinder

         The origin is placed at the center of the cylinder.
         ============
         */
        fun SetupCylinder(height: Float, width: Float, numSides: Int) {
            val cylBounds = idBounds()
            val halfHeight: Float
            val halfWidth: Float
            halfHeight = height * 0.5f
            halfWidth = width * 0.5f
            cylBounds.get(0).Set(-halfWidth, -halfWidth, -halfHeight)
            cylBounds.get(1).Set(halfWidth, halfWidth, halfHeight)
            SetupCylinder(cylBounds, numSides)
        }

        // cone approximation
        fun SetupCone(coneBounds: idBounds?, numSides: Int) {
            var i: Int
            var n: Int
            var ii: Int
            var angle: Float
            val halfSize = idVec3()
            n = numSides
            if (n < 2) {
                n = 3
            }
            if (n + 1 > TraceModel.MAX_TRACEMODEL_VERTS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many vertices\n")
                n = TraceModel.MAX_TRACEMODEL_VERTS - 1
            }
            if (n * 2 > TraceModel.MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many edges\n")
                n = TraceModel.MAX_TRACEMODEL_EDGES / 2
            }
            if (n + 1 > TraceModel.MAX_TRACEMODEL_POLYS) {
                idLib.common.Printf("WARNING: idTraceModel::SetupCone: too many polygons\n")
                n = TraceModel.MAX_TRACEMODEL_POLYS - 1
            }
            type = traceModel_t.TRM_CONE
            numVerts = n + 1
            numEdges = n * 2
            numPolys = n + 1
            offset.set(coneBounds.get(0).oPlus(coneBounds.get(1)).oMultiply(0.5f))
            halfSize.set(coneBounds.get(1).minus(offset))
            verts[n].set(0.0f, 0.0f, halfSize.z + offset.z)
            i = 0
            while (i < n) {

                // verts
                angle = idMath.TWO_PI * i / n
                verts[i].x = (Math.cos(angle.toDouble()) * halfSize.x + offset.x).toFloat()
                verts[i].y = (Math.sin(angle.toDouble()) * halfSize.y + offset.y).toFloat()
                verts[i].z = -halfSize.z + offset.z
                // edges
                ii = i + 1
                edges[ii].v.get(0) = i
                edges[ii].v.get(1) = ii % n
                edges[n + ii].v.get(0) = i
                edges[n + ii].v.get(1) = n
                // vertical polygon edges
                polys[i].numEdges = 3
                polys[i].edges.get(0) = ii
                polys[i].edges.get(1) = n + ii % n + 1
                polys[i].edges.get(2) = -(n + ii)
                // bottom polygon edges
                polys[n].edges.get(i) = -(n - i)
                i++
            }
            // bottom polygon numEdges
            polys[n].numEdges = n

            // polygons
            i = 0
            while (i < n) {

                // polygon plane
                polys[i].normal.set(verts[(i + 1) % n].minus(verts[i]).Cross(verts[n].minus(verts[i])))
                polys[i].normal.Normalize()
                polys[i].dist = polys[i].normal.times(verts[i])
                // polygon bounds
                polys[i].bounds.Clear()
                polys[i].bounds.AddPoint(verts[i])
                polys[i].bounds.AddPoint(verts[(i + 1) % n])
                polys[i].bounds.AddPoint(verts[n])
                i++
            }
            // bottom polygon plane
            polys[n].normal.set(0.0f, 0.0f, -1.0f)
            polys[n].dist = -coneBounds.get(0).oGet(2)
            // trm bounds
            bounds = coneBounds
            // bottom polygon bounds
            polys[n].bounds.set(bounds)
            polys[n].bounds.set(1, 2, bounds.get(0).oGet(2))
            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::SetupCone

         The origin is placed at the apex of the cone.
         ============
         */
        fun SetupCone(height: Float, width: Float, numSides: Int) {
            val coneBounds = idBounds()
            val halfWidth: Float
            halfWidth = width * 0.5f
            coneBounds.get(0).Set(-halfWidth, -halfWidth, -height)
            coneBounds.get(1).Set(halfWidth, halfWidth, 0.0f)
            SetupCone(coneBounds, numSides)
        }

        /*
         ============
         idTraceModel::SetupBone

         The origin is placed at the center of the bone.
         ============
         */
        fun SetupBone(length: Float, width: Float) { // two tetrahedrons attached to each other
            var i: Int
            var j: Int
            var edgeNum: Int
            val halfLength = (length * 0.5).toFloat()
            if (type != traceModel_t.TRM_BONE) {
                InitBone()
            }
            // offset to center
            offset.set(0.0f, 0.0f, 0.0f)
            // set vertices
            verts[0].set(0.0f, 0.0f, -halfLength)
            verts[1].set(0.0f, width * -0.5f, 0.0f)
            verts[2].set(width * 0.5f, width * 0.25f, 0.0f)
            verts[3].set(width * -0.5f, width * 0.25f, 0.0f)
            verts[4].set(0.0f, 0.0f, halfLength)
            // set bounds
            bounds.get(0).Set(width * -0.5f, width * -0.5f, -halfLength)
            bounds.get(1).Set(width * 0.5f, width * 0.25f, halfLength)
            // poly plane normals
            polys[0].normal.set(verts[2].minus(verts[0]).Cross(verts[1].minus(verts[0])))
            polys[0].normal.Normalize()
            polys[2].normal.set(-polys[0].normal.get(0), polys[0].normal.get(1), polys[0].normal.get(2))
            polys[3].normal.set(polys[0].normal.get(0), polys[0].normal.get(1), -polys[0].normal.get(2))
            polys[5].normal.set(-polys[0].normal.get(0), polys[0].normal.get(1), -polys[0].normal.get(2))
            polys[1].normal.set(verts[3].minus(verts[0]).Cross(verts[2].minus(verts[0])))
            polys[1].normal.Normalize()
            polys[4].normal.set(polys[1].normal.get(0), polys[1].normal.get(1), -polys[1].normal.get(2))
            // poly plane distances
            i = 0
            while (i < 6) {
                polys[i].dist = polys[i].normal.times(verts[edges[Math.abs(polys[i].edges.get(0))].v.get(0)])
                polys[i].bounds.Clear()
                j = 0
                while (j < 3) {
                    edgeNum = polys[i].edges.get(j)
                    polys[i].bounds.AddPoint(verts[edges[Math.abs(edgeNum)].v.get(if (edgeNum < 0) 1 else 0)])
                    j++
                }
                i++
            }
            GenerateEdgeNormals()
        }

        // arbitrary convex polygon
        fun SetupPolygon(v: Array<idVec3?>?, count: Int) {
            var i: Int
            var j: Int
            val mid = idVec3()
            type = traceModel_t.TRM_POLYGON
            numVerts = count
            // times three because we need to be able to turn the polygon into a volume
            if (numVerts * 3 > TraceModel.MAX_TRACEMODEL_EDGES) {
                idLib.common.Printf("WARNING: idTraceModel::SetupPolygon: too many vertices\n")
                numVerts = TraceModel.MAX_TRACEMODEL_EDGES / 3
            }
            numEdges = numVerts
            numPolys = 2
            // set polygon planes
            polys[0].numEdges = numEdges
            polys[0].normal.set(v.get(1).minus(v.get(0)).Cross(v.get(2).minus(v.get(0))))
            polys[0].normal.Normalize()
            polys[0].dist = polys[0].normal.times(v.get(0))
            polys[1].numEdges = numEdges
            polys[1].normal.set(polys[0].normal.oNegative())
            polys[1].dist = -polys[0].dist
            // setup verts, edges and polygons
            polys[0].bounds.Clear()
            mid.set(Vector.getVec3_origin())
            i = 0
            j = 1
            while (i < numVerts) {
                if (j >= numVerts) {
                    j = 0
                }
                verts[i].set(v.get(i))
                edges[i + 1].v.get(0) = i
                edges[i + 1].v.get(1) = j
                edges[i + 1].normal.set(polys[0].normal.Cross(v.get(i).minus(v.get(j))))
                edges[i + 1].normal.Normalize()
                polys[0].edges.get(i) = i + 1
                polys[1].edges.get(i) = -(numVerts - i)
                polys[0].bounds.AddPoint(verts[i])
                mid.plusAssign(v.get(i))
                i++
                j++
            }
            polys[1].bounds.set(polys[0].bounds)
            // offset to center
            offset.set(mid.times(1.0f / numVerts))
            // total bounds
            bounds = polys[0].bounds
            // considered non convex because the model has no volume
            isConvex = false
        }

        fun SetupPolygon(w: idWinding?) {
            var i: Int
            val verts: Array<idVec3?> = idVec3.Companion.generateArray(
                Math.max(
                    3,
                    w.GetNumPoints()
                )
            ) //TODO: this is a temp hack, for some reason the math is fucked
            i = 0
            while (i < w.GetNumPoints()) {
                verts[i].set(w.get(i).ToVec3())
                i++
            }
            SetupPolygon(verts, w.GetNumPoints())
        }

        // generate edge normals
        fun GenerateEdgeNormals(): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            var numSharpEdges: Int
            var dot: Float
            val dir = idVec3()
            var poly: traceModelPoly_t?
            var edge: traceModelEdge_t?
            i = 0
            while (i <= numEdges) {
                edges[i].normal.Zero()
                i++
            }
            numSharpEdges = 0
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                j = 0
                while (j < poly.numEdges) {
                    edgeNum = poly.edges.get(j)
                    edge = edges[Math.abs(edgeNum)]
                    if (edge.normal.get(0) == 0.0f && edge.normal.get(1) == 0.0f && edge.normal.get(2) == 0.0f) {
                        edge.normal.set(poly.normal)
                    } else {
                        dot = edge.normal.times(poly.normal)
                        // if the two planes make a very sharp edge
                        if (dot < SHARP_EDGE_DOT) {
                            // max length normal pointing outside both polygons
                            dir.set(verts[edge.v.get(if (edgeNum > 0) 1 else 0)].minus(verts[edge.v.get(if (edgeNum < 0) 1 else 0)]))
                            edge.normal.set(edge.normal.Cross(dir).oPlus(poly.normal.Cross(dir.oNegative())))
                            edge.normal.timesAssign(0.5f / (0.5f + 0.5f * SHARP_EDGE_DOT) / edge.normal.Length())
                            numSharpEdges++
                        } else {
                            edge.normal.set(edge.normal.oPlus(poly.normal).oMultiply(0.5f / (0.5f + 0.5f * dot)))
                        }
                    }
                    j++
                }
                i++
            }
            return numSharpEdges
        }

        // translate the trm
        fun Translate(translation: idVec3?) {
            var i: Int
            i = 0
            while (i < numVerts) {
                verts[i].plusAssign(translation)
                i++
            }
            i = 0
            while (i < numPolys) {
                polys[i].dist += polys[i].normal.times(translation)
                polys[i].bounds.timesAssign(translation)
                i++
            }
            offset.plusAssign(translation)
            bounds.timesAssign(translation)
        }

        // rotate the trm
        fun Rotate(rotation: idMat3?) {
            var i: Int
            var j: Int
            var edgeNum: Int
            i = 0
            while (i < numVerts) {
                verts[i].set(rotation.times(verts[i]))
                i++
            }
            bounds.Clear()
            i = 0
            while (i < numPolys) {
                polys[i].normal.set(rotation.times(polys[i].normal))
                polys[i].bounds.Clear()
                edgeNum = 0
                j = 0
                while (j < polys[i].numEdges) {
                    edgeNum = polys[i].edges.get(j)
                    polys[i].bounds.AddPoint(verts[edges[Math.abs(edgeNum)].v.get(Math_h.INTSIGNBITSET(edgeNum))])
                    j++
                }
                polys[i].dist =
                    polys[i].normal.times(verts[edges[Math.abs(edgeNum)].v.get(Math_h.INTSIGNBITSET(edgeNum))])
                bounds.timesAssign(polys[i].bounds)
                i++
            }
            GenerateEdgeNormals()
        }

        // shrink the model m units on all sides
        fun Shrink(m: Float) {
            var i: Int
            var j: Int
            var edgeNum: Int
            var edge: traceModelEdge_t?
            val dir = idVec3()
            if (type == traceModel_t.TRM_POLYGON) {
                i = 0
                while (i < numEdges) {
                    edgeNum = polys[0].edges.get(i)
                    edge = edges[Math.abs(edgeNum)]
                    dir.set(
                        verts[edge.v.get(Math_h.INTSIGNBITSET(edgeNum))].minus(
                            verts[edge.v.get(
                                Math_h.INTSIGNBITNOTSET(
                                    edgeNum
                                )
                            )]
                        )
                    )
                    if (dir.Normalize() < 2.0f * m) {
                        i++
                        continue
                    }
                    dir.timesAssign(m)
                    verts[edge.v.get(0)].minusAssign(dir)
                    verts[edge.v.get(1)].plusAssign(dir)
                    i++
                }
                return
            }
            i = 0
            while (i < numPolys) {
                polys[i].dist -= m
                j = 0
                while (j < polys[i].numEdges) {
                    edgeNum = polys[i].edges.get(j)
                    edge = edges[Math.abs(edgeNum)]
                    verts[edge.v.get(Math_h.INTSIGNBITSET(edgeNum))].minusAssign(polys[i].normal.times(m))
                    j++
                }
                i++
            }
        }

        // compare
        fun Compare(trm: idTraceModel?): Boolean {
            var i: Int
            if (type != trm.type || numVerts != trm.numVerts || numEdges != trm.numEdges || numPolys != trm.numPolys) {
                return false
            }
            if (bounds !== trm.bounds || offset !== trm.offset) {
                return false
            }
            when (type) {
                traceModel_t.TRM_INVALID, traceModel_t.TRM_BOX, traceModel_t.TRM_OCTAHEDRON, traceModel_t.TRM_DODECAHEDRON, traceModel_t.TRM_CYLINDER, traceModel_t.TRM_CONE -> {}
                traceModel_t.TRM_BONE, traceModel_t.TRM_POLYGON, traceModel_t.TRM_POLYGONVOLUME, traceModel_t.TRM_CUSTOM -> {
                    i = 0
                    while (i < trm.numVerts) {
                        if (verts[i] == trm.verts[i]) {
                            return false
                        }
                        i++
                    }
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = 3
            hash = 19 * hash + if (type != null) type.hashCode() else 0
            hash = 19 * hash + numVerts
            hash = 19 * hash + Arrays.deepHashCode(verts)
            hash = 19 * hash + numEdges
            hash = 19 * hash + numPolys
            hash = 19 * hash + Objects.hashCode(offset)
            hash = 19 * hash + Objects.hashCode(bounds)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idTraceModel?
            if (type != other.type) {
                return false
            }
            if (numVerts != other.numVerts) {
                return false
            }
            if (!Arrays.deepEquals(verts, other.verts)) {
                return false
            }
            if (numEdges != other.numEdges) {
                return false
            }
            if (numPolys != other.numPolys) {
                return false
            }
            return if (offset != other.offset) {
                false
            } else bounds == other.bounds
        }

        //public	bool				operator==(	const idTraceModel &trm ) const;
        //public	bool				operator!=(	const idTraceModel &trm ) const;
        //
        // get the area of one of the polygons
        fun GetPolygonArea(polyNum: Int): Float {
            var i: Int
            val base = idVec3()
            val v1 = idVec3()
            val v2 = idVec3()
            val cross = idVec3()
            var total: Float
            val poly: traceModelPoly_t?
            if (polyNum < 0 || polyNum >= numPolys) {
                return 0.0f
            }
            poly = polys[polyNum]
            total = 0.0f
            base.set(verts[edges[Math.abs(poly.edges.get(0))].v.get(Math_h.INTSIGNBITSET(poly.edges.get(0)))])
            i = 0
            while (i < poly.numEdges) {
                v1.set(
                    verts[edges[Math.abs(poly.edges.get(i))].v.get(Math_h.INTSIGNBITSET(poly.edges.get(i)))].minus(
                        base
                    )
                )
                v2.set(
                    verts[edges[Math.abs(poly.edges.get(i))].v.get(Math_h.INTSIGNBITNOTSET(poly.edges.get(i)))].minus(
                        base
                    )
                )
                cross.set(v1.Cross(v2))
                total += cross.Length()
                i++
            }
            return total * 0.5f
        }

        // get the silhouette edges
        fun GetProjectionSilhouetteEdges(projectionOrigin: idVec3?, silEdges: IntArray?): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            val edgeIsSilEdge = IntArray(TraceModel.MAX_TRACEMODEL_EDGES + 1)
            var poly: traceModelPoly_t?
            val dir = idVec3()

//	memset( edgeIsSilEdge, 0, sizeof( edgeIsSilEdge ) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                edgeNum = poly.edges.get(0)
                dir.set(verts[edges[Math.abs(edgeNum)].v.get(Math_h.INTSIGNBITSET(edgeNum))].minus(projectionOrigin))
                if (dir.times(poly.normal) < 0.0f) {
                    j = 0
                    while (j < poly.numEdges) {
                        edgeNum = poly.edges.get(j)
                        edgeIsSilEdge[Math.abs(edgeNum)] = edgeIsSilEdge[Math.abs(edgeNum)] xor 1
                        j++
                    }
                }
                i++
            }
            return GetOrderedSilhouetteEdges(edgeIsSilEdge, silEdges)
        }

        fun GetParallelProjectionSilhouetteEdges(projectionDir: idVec3?, silEdges: IntArray?): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            val edgeIsSilEdge = IntArray(TraceModel.MAX_TRACEMODEL_EDGES + 1)
            var poly: traceModelPoly_t?

//	memset( edgeIsSilEdge, 0, sizeof( edgeIsSilEdge ) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                if (projectionDir.times(poly.normal) < 0.0f) {
                    j = 0
                    while (j < poly.numEdges) {
                        edgeNum = poly.edges.get(j)
                        edgeIsSilEdge[Math.abs(edgeNum)] = edgeIsSilEdge[Math.abs(edgeNum)] xor 1
                        j++
                    }
                }
                i++
            }
            return GetOrderedSilhouetteEdges(edgeIsSilEdge, silEdges)
        }

        // calculate mass properties assuming an uniform density
        fun GetMassProperties(density: Float, mass: CFloat?, centerOfMass: idVec3?, inertiaTensor: idMat3?) {
            val integrals = volumeIntegrals_t()
            DBG_GetMassProperties++

            // if polygon trace model
            if (type == traceModel_t.TRM_POLYGON) {
                val trm = idTraceModel()
                VolumeFromPolygon(trm, 1.0f)
                trm.GetMassProperties(density, mass, centerOfMass, inertiaTensor)
                return
            }
            VolumeIntegrals(integrals)

            // if no volume
            if (integrals.T0 == 0.0f) {
                mass.setVal(1.0f)
                centerOfMass.Zero()
                inertiaTensor.Identity()
                return
            }

            // mass of model
            mass.setVal(density * integrals.T0)
            // center of mass
            centerOfMass.set(integrals.T1.div(integrals.T0))
            // compute inertia tensor
            inertiaTensor.set(0, 0, density * (integrals.T2.get(1) + integrals.T2.get(2)))
            inertiaTensor.set(1, 1, density * (integrals.T2.get(2) + integrals.T2.get(0)))
            inertiaTensor.set(2, 2, density * (integrals.T2.get(0) + integrals.T2.get(1)))
            inertiaTensor.set(0, 1, -density * integrals.TP.get(0))
            inertiaTensor.set(1, 0, -density * integrals.TP.get(0))
            inertiaTensor.set(1, 2, -density * integrals.TP.get(1))
            inertiaTensor.set(2, 1, -density * integrals.TP.get(1))
            inertiaTensor.set(2, 0, -density * integrals.TP.get(2))
            inertiaTensor.set(0, 2, -density * integrals.TP.get(2))
            // translate inertia tensor to center of mass
            inertiaTensor.minusAssign(
                0,
                0,
                mass.getVal() * (centerOfMass.get(1) * centerOfMass.get(1) + centerOfMass.get(2) * centerOfMass.get(
                    2
                ))
            )
            inertiaTensor.minusAssign(
                1,
                1,
                mass.getVal() * (centerOfMass.get(2) * centerOfMass.get(2) + centerOfMass.get(0) * centerOfMass.get(
                    0
                ))
            )
            inertiaTensor.minusAssign(
                2,
                2,
                mass.getVal() * (centerOfMass.get(0) * centerOfMass.get(0) + centerOfMass.get(1) * centerOfMass.get(
                    1
                ))
            )
            inertiaTensor.plusAssign(0, 1, mass.getVal() * centerOfMass.get(0) * centerOfMass.get(1))
            inertiaTensor.plusAssign(1, 0, mass.getVal() * centerOfMass.get(0) * centerOfMass.get(1))
            inertiaTensor.plusAssign(1, 2, mass.getVal() * centerOfMass.get(1) * centerOfMass.get(2))
            inertiaTensor.plusAssign(2, 1, mass.getVal() * centerOfMass.get(1) * centerOfMass.get(2))
            inertiaTensor.plusAssign(2, 0, mass.getVal() * centerOfMass.get(2) * centerOfMass.get(0))
            inertiaTensor.plusAssign(0, 2, mass.getVal() * centerOfMass.get(2) * centerOfMass.get(0))
        }

        //
        /*
         ============
         idTraceModel::InitBox

         Initialize size independent box.
         ============
         */
        private fun InitBox() {
            var i: Int
            type = traceModel_t.TRM_BOX
            numVerts = 8
            numEdges = 12
            numPolys = 6

            // set box edges
            i = 0
            while (i < 4) {
                edges[i + 1].v.get(0) = i
                edges[i + 1].v.get(1) = i + 1 and 3
                edges[i + 5].v.get(0) = 4 + i
                edges[i + 5].v.get(1) = 4 + (i + 1 and 3)
                edges[i + 9].v.get(0) = i
                edges[i + 9].v.get(1) = 4 + i
                i++
            }

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 4
            polys[0].edges.get(0) = -4
            polys[0].edges.get(1) = -3
            polys[0].edges.get(2) = -2
            polys[0].edges.get(3) = -1
            polys[0].normal.set(0.0f, 0.0f, -1.0f)
            polys[1].numEdges = 4
            polys[1].edges.get(0) = 5
            polys[1].edges.get(1) = 6
            polys[1].edges.get(2) = 7
            polys[1].edges.get(3) = 8
            polys[1].normal.set(0.0f, 0.0f, 1.0f)
            polys[2].numEdges = 4
            polys[2].edges.get(0) = 1
            polys[2].edges.get(1) = 10
            polys[2].edges.get(2) = -5
            polys[2].edges.get(3) = -9
            polys[2].normal.set(0.0f, -1.0f, 0.0f)
            polys[3].numEdges = 4
            polys[3].edges.get(0) = 2
            polys[3].edges.get(1) = 11
            polys[3].edges.get(2) = -6
            polys[3].edges.get(3) = -10
            polys[3].normal.set(1.0f, 0.0f, 0.0f)
            polys[4].numEdges = 4
            polys[4].edges.get(0) = 3
            polys[4].edges.get(1) = 12
            polys[4].edges.get(2) = -7
            polys[4].edges.get(3) = -11
            polys[4].normal.set(0.0f, 1.0f, 0.0f)
            polys[5].numEdges = 4
            polys[5].edges.get(0) = 4
            polys[5].edges.get(1) = 9
            polys[5].edges.get(2) = -8
            polys[5].edges.get(3) = -12
            polys[5].normal.set(-1.0f, 0.0f, 0.0f)

            // convex model
            isConvex = true
            GenerateEdgeNormals()
        }

        /*
         ============
         idTraceModel::InitOctahedron

         Initialize size independent octahedron.
         ============
         */
        private fun InitOctahedron() {
            type = traceModel_t.TRM_OCTAHEDRON
            numVerts = 6
            numEdges = 12
            numPolys = 8

            // set edges
            edges[1].v.get(0) = 4
            edges[1].v.get(1) = 0
            edges[2].v.get(0) = 0
            edges[2].v.get(1) = 2
            edges[3].v.get(0) = 2
            edges[3].v.get(1) = 4
            edges[4].v.get(0) = 2
            edges[4].v.get(1) = 1
            edges[5].v.get(0) = 1
            edges[5].v.get(1) = 4
            edges[6].v.get(0) = 1
            edges[6].v.get(1) = 3
            edges[7].v.get(0) = 3
            edges[7].v.get(1) = 4
            edges[8].v.get(0) = 3
            edges[8].v.get(1) = 0
            edges[9].v.get(0) = 5
            edges[9].v.get(1) = 2
            edges[10].v.get(0) = 0
            edges[10].v.get(1) = 5
            edges[11].v.get(0) = 5
            edges[11].v.get(1) = 1
            edges[12].v.get(0) = 5
            edges[12].v.get(1) = 3

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 3
            polys[0].edges.get(0) = 1
            polys[0].edges.get(1) = 2
            polys[0].edges.get(2) = 3
            polys[1].numEdges = 3
            polys[1].edges.get(0) = -3
            polys[1].edges.get(1) = 4
            polys[1].edges.get(2) = 5
            polys[2].numEdges = 3
            polys[2].edges.get(0) = -5
            polys[2].edges.get(1) = 6
            polys[2].edges.get(2) = 7
            polys[3].numEdges = 3
            polys[3].edges.get(0) = -7
            polys[3].edges.get(1) = 8
            polys[3].edges.get(2) = -1
            polys[4].numEdges = 3
            polys[4].edges.get(0) = 9
            polys[4].edges.get(1) = -2
            polys[4].edges.get(2) = 10
            polys[5].numEdges = 3
            polys[5].edges.get(0) = 11
            polys[5].edges.get(1) = -4
            polys[5].edges.get(2) = -9
            polys[6].numEdges = 3
            polys[6].edges.get(0) = 12
            polys[6].edges.get(1) = -6
            polys[6].edges.get(2) = -11
            polys[7].numEdges = 3
            polys[7].edges.get(0) = -10
            polys[7].edges.get(1) = -8
            polys[7].edges.get(2) = -12

            // convex model
            isConvex = true
        }

        /*
         ============
         idTraceModel::InitDodecahedron

         Initialize size independent dodecahedron.
         ============
         */
        private fun InitDodecahedron() {
            type = traceModel_t.TRM_DODECAHEDRON
            numVerts = 20
            numEdges = 30
            numPolys = 12

            // set edges
            edges[1].v.get(0) = 0
            edges[1].v.get(1) = 8
            edges[2].v.get(0) = 8
            edges[2].v.get(1) = 9
            edges[3].v.get(0) = 9
            edges[3].v.get(1) = 4
            edges[4].v.get(0) = 4
            edges[4].v.get(1) = 16
            edges[5].v.get(0) = 16
            edges[5].v.get(1) = 0
            edges[6].v.get(0) = 16
            edges[6].v.get(1) = 17
            edges[7].v.get(0) = 17
            edges[7].v.get(1) = 2
            edges[8].v.get(0) = 2
            edges[8].v.get(1) = 12
            edges[9].v.get(0) = 12
            edges[9].v.get(1) = 0
            edges[10].v.get(0) = 2
            edges[10].v.get(1) = 10
            edges[11].v.get(0) = 10
            edges[11].v.get(1) = 3
            edges[12].v.get(0) = 3
            edges[12].v.get(1) = 13
            edges[13].v.get(0) = 13
            edges[13].v.get(1) = 12
            edges[14].v.get(0) = 9
            edges[14].v.get(1) = 5
            edges[15].v.get(0) = 5
            edges[15].v.get(1) = 15
            edges[16].v.get(0) = 15
            edges[16].v.get(1) = 14
            edges[17].v.get(0) = 14
            edges[17].v.get(1) = 4
            edges[18].v.get(0) = 3
            edges[18].v.get(1) = 19
            edges[19].v.get(0) = 19
            edges[19].v.get(1) = 18
            edges[20].v.get(0) = 18
            edges[20].v.get(1) = 1
            edges[21].v.get(0) = 1
            edges[21].v.get(1) = 13
            edges[22].v.get(0) = 7
            edges[22].v.get(1) = 11
            edges[23].v.get(0) = 11
            edges[23].v.get(1) = 6
            edges[24].v.get(0) = 6
            edges[24].v.get(1) = 14
            edges[25].v.get(0) = 15
            edges[25].v.get(1) = 7
            edges[26].v.get(0) = 1
            edges[26].v.get(1) = 8
            edges[27].v.get(0) = 18
            edges[27].v.get(1) = 5
            edges[28].v.get(0) = 6
            edges[28].v.get(1) = 17
            edges[29].v.get(0) = 11
            edges[29].v.get(1) = 10
            edges[30].v.get(0) = 19
            edges[30].v.get(1) = 7

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 5
            polys[0].edges.get(0) = 1
            polys[0].edges.get(1) = 2
            polys[0].edges.get(2) = 3
            polys[0].edges.get(3) = 4
            polys[0].edges.get(4) = 5
            polys[1].numEdges = 5
            polys[1].edges.get(0) = -5
            polys[1].edges.get(1) = 6
            polys[1].edges.get(2) = 7
            polys[1].edges.get(3) = 8
            polys[1].edges.get(4) = 9
            polys[2].numEdges = 5
            polys[2].edges.get(0) = -8
            polys[2].edges.get(1) = 10
            polys[2].edges.get(2) = 11
            polys[2].edges.get(3) = 12
            polys[2].edges.get(4) = 13
            polys[3].numEdges = 5
            polys[3].edges.get(0) = 14
            polys[3].edges.get(1) = 15
            polys[3].edges.get(2) = 16
            polys[3].edges.get(3) = 17
            polys[3].edges.get(4) = -3
            polys[4].numEdges = 5
            polys[4].edges.get(0) = 18
            polys[4].edges.get(1) = 19
            polys[4].edges.get(2) = 20
            polys[4].edges.get(3) = 21
            polys[4].edges.get(4) = -12
            polys[5].numEdges = 5
            polys[5].edges.get(0) = 22
            polys[5].edges.get(1) = 23
            polys[5].edges.get(2) = 24
            polys[5].edges.get(3) = -16
            polys[5].edges.get(4) = 25
            polys[6].numEdges = 5
            polys[6].edges.get(0) = -9
            polys[6].edges.get(1) = -13
            polys[6].edges.get(2) = -21
            polys[6].edges.get(3) = 26
            polys[6].edges.get(4) = -1
            polys[7].numEdges = 5
            polys[7].edges.get(0) = -26
            polys[7].edges.get(1) = -20
            polys[7].edges.get(2) = 27
            polys[7].edges.get(3) = -14
            polys[7].edges.get(4) = -2
            polys[8].numEdges = 5
            polys[8].edges.get(0) = -4
            polys[8].edges.get(1) = -17
            polys[8].edges.get(2) = -24
            polys[8].edges.get(3) = 28
            polys[8].edges.get(4) = -6
            polys[9].numEdges = 5
            polys[9].edges.get(0) = -23
            polys[9].edges.get(1) = 29
            polys[9].edges.get(2) = -10
            polys[9].edges.get(3) = -7
            polys[9].edges.get(4) = -28
            polys[10].numEdges = 5
            polys[10].edges.get(0) = -25
            polys[10].edges.get(1) = -15
            polys[10].edges.get(2) = -27
            polys[10].edges.get(3) = -19
            polys[10].edges.get(4) = 30
            polys[11].numEdges = 5
            polys[11].edges.get(0) = -30
            polys[11].edges.get(1) = -18
            polys[11].edges.get(2) = -11
            polys[11].edges.get(3) = -29
            polys[11].edges.get(4) = -22

            // convex model
            isConvex = true
        }

        /*
         ============
         idTraceModel::InitBone

         Initialize size independent bone.
         ============
         */
        private fun InitBone() {
            var i: Int
            type = traceModel_t.TRM_BONE
            numVerts = 5
            numEdges = 9
            numPolys = 6

            // set bone edges
            i = 0
            while (i < 3) {
                edges[i + 1].v.get(0) = 0
                edges[i + 1].v.get(1) = i + 1
                edges[i + 4].v.get(0) = 1 + i
                edges[i + 4].v.get(1) = 1 + (i + 1) % 3
                edges[i + 7].v.get(0) = i + 1
                edges[i + 7].v.get(1) = 4
                i++
            }

            // all edges of a polygon go counter clockwise
            polys[0].numEdges = 3
            polys[0].edges.get(0) = 2
            polys[0].edges.get(1) = -4
            polys[0].edges.get(2) = -1
            polys[1].numEdges = 3
            polys[1].edges.get(0) = 3
            polys[1].edges.get(1) = -5
            polys[1].edges.get(2) = -2
            polys[2].numEdges = 3
            polys[2].edges.get(0) = 1
            polys[2].edges.get(1) = -6
            polys[2].edges.get(2) = -3
            polys[3].numEdges = 3
            polys[3].edges.get(0) = 4
            polys[3].edges.get(1) = 8
            polys[3].edges.get(2) = -7
            polys[4].numEdges = 3
            polys[4].edges.get(0) = 5
            polys[4].edges.get(1) = 9
            polys[4].edges.get(2) = -8
            polys[5].numEdges = 3
            polys[5].edges.get(0) = 6
            polys[5].edges.get(1) = 7
            polys[5].edges.get(2) = -9

            // convex model
            isConvex = true
        }

        private fun oSet(trm: idTraceModel?) {
            type = trm.type
            numVerts = trm.numVerts
            for (i in 0 until numVerts) {
                verts[i].set(trm.verts[i])
            }
            numEdges = trm.numEdges
            for (i in 0 until numEdges) {
                edges[i].oSet(trm.edges[i])
            }
            numPolys = trm.numPolys
            for (i in 0 until numPolys) {
                polys[i].oSet(trm.polys[i])
            }
            offset.set(trm.offset)
            bounds.set(trm.bounds)
            isConvex = trm.isConvex
        }

        private fun ProjectionIntegrals(polyNum: Int, a: Int, b: Int, integrals: projectionIntegrals_t?) {
            val poly: traceModelPoly_t?
            var i: Int
            var edgeNum: Int
            val v1 = idVec3()
            val v2 = idVec3()
            var a0: Float
            var a1: Float
            var da: Float
            var b0: Float
            var b1: Float
            var db: Float
            var a0_2: Float
            var a0_3: Float
            var a0_4: Float
            var b0_2: Float
            var b0_3: Float
            var b0_4: Float
            var a1_2: Float
            var a1_3: Float
            var b1_2: Float
            var b1_3: Float
            var C1: Float
            var Ca: Float
            var Caa: Float
            var Caaa: Float
            var Cb: Float
            var Cbb: Float
            var Cbbb: Float
            var Cab: Float
            var Kab: Float
            var Caab: Float
            var Kaab: Float
            var Cabb: Float
            var Kabb: Float

//	memset(&integrals, 0, sizeof(projectionIntegrals_t));
            poly = polys[polyNum]
            i = 0
            while (i < poly.numEdges) {
                edgeNum = poly.edges.get(i)
                v1.set(verts[edges[Math.abs(edgeNum)].v.get(if (edgeNum < 0) 1 else 0)])
                v2.set(verts[edges[Math.abs(edgeNum)].v.get(if (edgeNum > 0) 1 else 0)])
                a0 = v1.get(a)
                b0 = v1.get(b)
                a1 = v2.get(a)
                b1 = v2.get(b)
                da = a1 - a0
                db = b1 - b0
                a0_2 = a0 * a0
                a0_3 = a0_2 * a0
                a0_4 = a0_3 * a0
                b0_2 = b0 * b0
                b0_3 = b0_2 * b0
                b0_4 = b0_3 * b0
                a1_2 = a1 * a1
                a1_3 = a1_2 * a1
                b1_2 = b1 * b1
                b1_3 = b1_2 * b1
                C1 = a1 + a0
                Ca = a1 * C1 + a0_2
                Caa = a1 * Ca + a0_3
                Caaa = a1 * Caa + a0_4
                Cb = b1 * (b1 + b0) + b0_2
                Cbb = b1 * Cb + b0_3
                Cbbb = b1 * Cbb + b0_4
                Cab = 3 * a1_2 + 2 * a1 * a0 + a0_2
                Kab = a1_2 + 2 * a1 * a0 + 3 * a0_2
                Caab = a0 * Cab + 4 * a1_3
                Kaab = a1 * Kab + 4 * a0_3
                Cabb = 4 * b1_3 + 3 * b1_2 * b0 + 2 * b1 * b0_2 + b0_3
                Kabb = b1_3 + 2 * b1_2 * b0 + 3 * b1 * b0_2 + 4 * b0_3
                integrals.P1 += db * C1
                integrals.Pa += db * Ca
                integrals.Paa += db * Caa
                integrals.Paaa += db * Caaa
                integrals.Pb += da * Cb
                integrals.Pbb += da * Cbb
                integrals.Pbbb += da * Cbbb
                integrals.Pab += db * (b1 * Cab + b0 * Kab)
                integrals.Paab += db * (b1 * Caab + b0 * Kaab)
                integrals.Pabb += da * (a1 * Cabb + a0 * Kabb)
                i++
            }
            integrals.P1 *= 1.0f / 2.0f
            integrals.Pa *= 1.0f / 6.0f
            integrals.Paa *= 1.0f / 12.0f
            integrals.Paaa *= 1.0f / 20.0f
            integrals.Pb *= 1.0f / -6.0f
            integrals.Pbb *= 1.0f / -12.0f
            integrals.Pbbb *= 1.0f / -20.0f
            integrals.Pab *= 1.0f / 24.0f
            integrals.Paab *= 1.0f / 60.0f
            integrals.Pabb *= 1.0f / -60.0f
        }

        private fun PolygonIntegrals(polyNum: Int, a: Int, b: Int, c: Int, integrals: polygonIntegrals_t?) {
            val pi = projectionIntegrals_t()
            val n = idVec3()
            val w: Float
            val k1: Float
            val k2: Float
            val k3: Float
            val k4: Float
            ProjectionIntegrals(polyNum, a, b, pi)
            n.set(polys[polyNum].normal)
            w = -polys[polyNum].dist
            k1 = 1 / n.get(c)
            k2 = k1 * k1
            k3 = k2 * k1
            k4 = k3 * k1
            integrals.Fa = k1 * pi.Pa
            integrals.Fb = k1 * pi.Pb
            integrals.Fc = -k2 * (n.get(a) * pi.Pa + n.get(b) * pi.Pb + w * pi.P1)
            integrals.Faa = k1 * pi.Paa
            integrals.Fbb = k1 * pi.Pbb
            integrals.Fcc =
                k3 * (Math_h.Square(n.get(a)) * pi.Paa + 2 * n.get(a) * n.get(b) * pi.Pab + Math_h.Square(n.get(b)) * pi.Pbb + w * (2 * (n.get(
                    a
                ) * pi.Pa + n.get(b) * pi.Pb) + w * pi.P1))
            integrals.Faaa = k1 * pi.Paaa
            integrals.Fbbb = k1 * pi.Pbbb
            integrals.Fccc =
                -k4 * (Math_h.Cube(n.get(a)) * pi.Paaa + 3 * Math_h.Square(n.get(a)) * n.get(b) * pi.Paab + 3 * n.get(
                    a
                ) * Math_h.Square(n.get(b)) * pi.Pabb + Math_h.Cube(n.get(b)) * pi.Pbbb + 3 * w * (Math_h.Square(
                    n.get(
                        a
                    )
                ) * pi.Paa + 2 * n.get(a) * n.get(b) * pi.Pab + Math_h.Square(n.get(b)) * pi.Pbb) + w * w * (3 * (n.get(
                    a
                ) * pi.Pa + n.get(b) * pi.Pb) + w * pi.P1))
            integrals.Faab = k1 * pi.Paab
            integrals.Fbbc = -k2 * (n.get(a) * pi.Pabb + n.get(b) * pi.Pbbb + w * pi.Pbb)
            integrals.Fcca =
                k3 * (Math_h.Square(n.get(a)) * pi.Paaa + 2 * n.get(a) * n.get(b) * pi.Paab + Math_h.Square(n.get(b)) * pi.Pabb + w * (2 * (n.get(
                    a
                ) * pi.Paa + n.get(b) * pi.Pab) + w * pi.Pa))
        }

        private fun VolumeIntegrals(integrals: volumeIntegrals_t?) {
            var poly: traceModelPoly_t?
            val pi = polygonIntegrals_t()
            var i: Int
            var a: Int
            var b: Int
            var c: Int
            var nx: Float
            var ny: Float
            var nz: Float
            var T0 = 0f
            val T1 = FloatArray(3)
            val T2 = FloatArray(3)
            val TP = FloatArray(3)

//	memset( &integrals, 0, sizeof(volumeIntegrals_t) );
            i = 0
            while (i < numPolys) {
                poly = polys[i]
                nx = Math.abs(poly.normal.get(0))
                ny = Math.abs(poly.normal.get(1))
                nz = Math.abs(poly.normal.get(2))
                c = if (nx > ny && nx > nz) {
                    0
                } else {
                    if (ny > nz) 1 else 2
                }
                a = (c + 1) % 3
                b = (a + 1) % 3
                PolygonIntegrals(i, a, b, c, pi)
                T0 += poly.normal.get(0) * if (a == 0) pi.Fa else if (b == 0) pi.Fb else pi.Fc
                T1[a] += poly.normal.get(a) * pi.Faa
                T1[b] += poly.normal.get(b) * pi.Fbb
                T1[c] += poly.normal.get(c) * pi.Fcc
                T2[a] += poly.normal.get(a) * pi.Faaa
                T2[b] += poly.normal.get(b) * pi.Fbbb
                T2[c] += poly.normal.get(c) * pi.Fccc
                TP[a] += poly.normal.get(a) * pi.Faab
                TP[b] += poly.normal.get(b) * pi.Fbbc
                TP[c] += poly.normal.get(c) * pi.Fcca
                i++
            }
            integrals.T0 = T0
            integrals.T1.set(0, T1[0] * 0.5f)
            integrals.T1.set(1, T1[1] * 0.5f)
            integrals.T1.set(2, T1[2] * 0.5f)
            integrals.T2.set(0, T2[0] * (1.0f / 3.0f))
            integrals.T2.set(1, T2[1] * (1.0f / 3.0f))
            integrals.T2.set(2, T2[2] * (1.0f / 3.0f))
            integrals.TP.set(0, TP[0] * 0.5f)
            integrals.TP.set(1, TP[1] * 0.5f)
            integrals.TP.set(2, TP[2] * 0.5f)
        }

        private fun VolumeFromPolygon(trm: idTraceModel?, thickness: Float) {
            var i: Int
            trm.oSet(this)
            trm.type = traceModel_t.TRM_POLYGONVOLUME
            trm.numVerts = numVerts * 2
            trm.numEdges = numEdges * 3
            trm.numPolys = numEdges + 2
            i = 0
            while (i < numEdges) {
                trm.verts[numVerts + i].set(verts[i].minus(polys[0].normal.times(thickness)))
                trm.edges[numEdges + i + 1].v.get(0) = numVerts + i
                trm.edges[numEdges + i + 1].v.get(1) = numVerts + (i + 1) % numVerts
                trm.edges[numEdges * 2 + i + 1].v.get(0) = i
                trm.edges[numEdges * 2 + i + 1].v.get(1) = numVerts + i
                trm.polys[1].edges.get(i) = -(numEdges + i + 1)
                trm.polys[2 + i].numEdges = 4
                trm.polys[2 + i].edges.get(0) = -(i + 1)
                trm.polys[2 + i].edges.get(1) = numEdges * 2 + i + 1
                trm.polys[2 + i].edges.get(2) = numEdges + i + 1
                trm.polys[2 + i].edges.get(3) = -(numEdges * 2 + (i + 1) % numEdges + 1)
                trm.polys[2 + i].normal.set(verts[(i + 1) % numVerts].minus(verts[i]).Cross(polys[0].normal))
                trm.polys[2 + i].normal.Normalize()
                trm.polys[2 + i].dist = trm.polys[2 + i].normal.times(verts[i])
                i++
            }
            trm.polys[1].dist = trm.polys[1].normal.times(trm.verts[numEdges])
            trm.GenerateEdgeNormals()
        }

        private fun GetOrderedSilhouetteEdges(edgeIsSilEdge: IntArray?, silEdges: IntArray?): Int {
            var i: Int
            var j: Int
            var edgeNum: Int
            val numSilEdges: Int
            var nextSilVert: Int
            val unsortedSilEdges = IntArray(TraceModel.MAX_TRACEMODEL_EDGES)
            numSilEdges = 0
            i = 1
            while (i <= numEdges) {
                if (edgeIsSilEdge.get(i) != 0) {
                    unsortedSilEdges[numSilEdges++] = i
                }
                i++
            }
            silEdges.get(0) = unsortedSilEdges[0]
            unsortedSilEdges[0] = -1
            nextSilVert = edges[silEdges.get(0)].v.get(0)
            i = 1
            while (i < numSilEdges) {
                j = 1
                while (j < numSilEdges) {
                    edgeNum = unsortedSilEdges[j]
                    if (edgeNum >= 0) {
                        if (edges[edgeNum].v.get(0) == nextSilVert) {
                            nextSilVert = edges[edgeNum].v.get(1)
                            silEdges.get(i) = edgeNum
                            break
                        }
                        if (edges[edgeNum].v.get(1) == nextSilVert) {
                            nextSilVert = edges[edgeNum].v.get(0)
                            silEdges.get(i) = -edgeNum
                            break
                        }
                    }
                    j++
                }
                if (j >= numSilEdges) {
                    silEdges.get(i) = 1 // shouldn't happen
                }
                unsortedSilEdges[j] = -1
                i++
            }
            return numSilEdges
        }

        internal inner class projectionIntegrals_t {
            var P1 = 0f
            var Pa = 0f
            var Pb = 0f
            var Paa = 0f
            var Pab = 0f
            var Pbb = 0f
            var Paaa = 0f
            var Paab = 0f
            var Pabb = 0f
            var Pbbb = 0f
        }

        internal inner class polygonIntegrals_t {
            var Fa = 0f
            var Fb = 0f
            var Fc = 0f
            var Faa = 0f
            var Fbb = 0f
            var Fcc = 0f
            var Faaa = 0f
            var Fbbb = 0f
            var Fccc = 0f
            var Faab = 0f
            var Fbbc = 0f
            var Fcca = 0f
        }

        internal inner class volumeIntegrals_t {
            var T0 = 0f
            val T1: idVec3?
            val T2: idVec3?
            val TP: idVec3?

            init {
                T1 = idVec3()
                T2 = idVec3()
                TP = idVec3()
            }
        }

        companion object {
            const val SHARP_EDGE_DOT = -0.7f
            private var DBG_GetMassProperties = 0
        }

        init {
            type = traceModel_t.TRM_INVALID
            numPolys = 0
            numEdges = numPolys
            numVerts = numEdges
            offset = idVec3()
            bounds = idBounds()
        }
    }
}