package neo.Renderer

import neo.Renderer.Model.srfTriangles_s
import neo.framework.Common
import neo.idlib.Lib.idException
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane

/**
 *
 */
object tr_polytope {
    const val MAX_POLYTOPE_PLANES = 6

    /*
     =====================
     R_PolytopeSurface

     Generate vertexes and indexes for a polytope, and optionally returns the polygon windings.
     The positive sides of the planes will be visible.
     =====================
     */
    @Throws(idException::class)
    fun R_PolytopeSurface(numPlanes: Int, planes: Array<idPlane?>?, windings: Array<idWinding?>?): srfTriangles_s? {
        var i: Int
        var j: Int
        val tri: srfTriangles_s
        val planeWindings = arrayOfNulls<idFixedWinding?>(tr_polytope.MAX_POLYTOPE_PLANES)
        var numVerts: Int
        var numIndexes: Int
        if (numPlanes > tr_polytope.MAX_POLYTOPE_PLANES) {
            Common.common.Error("R_PolytopeSurface: more than %d planes", tr_polytope.MAX_POLYTOPE_PLANES)
        }
        numVerts = 0
        numIndexes = 0
        i = 0
        while (i < numPlanes) {
            val plane = planes.get(i)
            planeWindings[i] = idFixedWinding()
            val w = planeWindings[i]
            w.BaseForPlane(plane)
            j = 0
            while (j < numPlanes) {
                val plane2 = planes.get(j)
                if (j == i) {
                    j++
                    continue
                }
                if (!w.ClipInPlace(plane2.unaryMinus(), Plane.ON_EPSILON)) {
                    break
                }
                j++
            }
            if (w.GetNumPoints() <= 2) {
                i++
                continue
            }
            numVerts += w.GetNumPoints()
            numIndexes += (w.GetNumPoints() - 2) * 3
            i++
        }

        // allocate the surface
        tri = srfTriangles_s() //R_AllocStaticTriSurf();
        tr_trisurf.R_AllocStaticTriSurfVerts(tri, numVerts)
        tr_trisurf.R_AllocStaticTriSurfIndexes(tri, numIndexes)

        // copy the data from the windings
        i = 0
        while (i < numPlanes) {
            val w = planeWindings[i]
            if (0 == w.GetNumPoints()) {
                i++
                continue
            }
            j = 0
            while (j < w.GetNumPoints()) {
                tri.verts[tri.numVerts + j].Clear()
                tri.verts[tri.numVerts + j].xyz.set(w.oGet(j).ToVec3())
                j++
            }
            j = 1
            while (j < w.GetNumPoints() - 1) {
                tri.indexes[tri.numIndexes + 0] = tri.numVerts
                tri.indexes[tri.numIndexes + 1] = tri.numVerts + j
                tri.indexes[tri.numIndexes + 2] = tri.numVerts + j + 1
                tri.numIndexes += 3
                j++
            }
            tri.numVerts += w.GetNumPoints()

            // optionally save the winding
            if (windings != null) {
                windings[i] = idWinding(w)
            }
            i++
        }
        tr_trisurf.R_BoundTriSurf(tri)
        return tri
    }
}