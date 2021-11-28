package neo.Tools.Compilers.DMap

import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.framework.Common
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.geometry.DrawVert.idDrawVert

/**
 *
 */
object optimize_gcc {
    //
    const val MAX_OPT_VERTEXES = 0x10000
    val optVerts: Array<optVertex_s?>? = arrayOfNulls<optVertex_s?>(optimize_gcc.MAX_OPT_VERTEXES)
    var numOptVerts = 0

    /*
     crazy gcc 3.3.5 optimization bug
     happens even at -O1
     if you remove the 'return NULL;' after Error(), it only happens at -O3 / release
     see dmap.gcc.zip test map and .proc outputs
     */
    var optBounds: idBounds? = null

    /*
     ================
     FindOptVertex
     ================
     */
    fun FindOptVertex(v: idDrawVert?, opt: optimizeGroup_s?): optVertex_s? {
        var i: Int
        val x: Float
        val y: Float
        val vert: optVertex_s?

        // deal with everything strictly as 2D
        x = v.xyz.oMultiply(opt.axis[0])
        y = v.xyz.oMultiply(opt.axis[1])

        // should we match based on the t-junction fixing hash verts?
        i = 0
        while (i < optimize_gcc.numOptVerts) {
            if (optimize_gcc.optVerts[i].pv.oGet(0) == x && optimize_gcc.optVerts[i].pv.oGet(1) == y) {
                return optimize_gcc.optVerts[i]
            }
            i++
        }
        if (optimize_gcc.numOptVerts >= optimize_gcc.MAX_OPT_VERTEXES) {
            Common.common.Error("MAX_OPT_VERTEXES")
            return null
        }
        optimize_gcc.numOptVerts++
        optimize_gcc.optVerts[i] = optVertex_s()
        vert = optimize_gcc.optVerts[i]
        //	memset( vert, 0, sizeof( *vert ) );
        vert.v = v
        vert.pv.oSet(0, x)
        vert.pv.oSet(1, y)
        vert.pv.oSet(2, 0f)
        optimize_gcc.optBounds.AddPoint(vert.pv)
        return vert
    }
}