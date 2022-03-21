package neo.CM

import neo.CM.AbstractCollisionModel_local.cm_edge_s
import neo.CM.AbstractCollisionModel_local.cm_vertex_s
import neo.idlib.math.Math_h
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Pluecker.idPluecker

object CollisionModel_contents {
    /*
     ===============================================================================

     Contents test

     ===============================================================================
     */
    /*
     ================
     CM_SetTrmEdgeSidedness
     ================
     */
    fun CM_SetTrmEdgeSidedness(edge: cm_edge_s, bpl: idPluecker, epl: idPluecker, bitNum: Int) {
        if (0 == edge.sideSet and (1 shl bitNum)) {
            val fl: Float
            fl = bpl.PermutedInnerProduct(epl)
            edge.side = edge.side and (1 shl bitNum).inv() or (Math_h.FLOATSIGNBITSET(fl) shl bitNum)
            edge.sideSet = edge.sideSet or (1 shl bitNum)
        }
    }

    /*
     ================
     CM_SetTrmPolygonSidedness
     ================
     */
    fun CM_SetTrmPolygonSidedness(v: cm_vertex_s, plane: idPlane, bitNum: Int) {
        if (0 == v.sideSet and (1 shl bitNum)) {
            val fl: Float
            fl = plane.Distance(v.p)
            /* cannot use float sign bit because it is undetermined when fl == 0.0f */if (fl < 0.0f) {
                v.side = v.side or (1 shl bitNum)
            } else {
                v.side = v.side and (1 shl bitNum).inv()
            }
            v.sideSet = v.sideSet or (1 shl bitNum)
        }
    }
}