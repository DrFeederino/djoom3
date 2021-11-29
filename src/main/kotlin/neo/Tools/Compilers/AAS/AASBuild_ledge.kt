package neo.Tools.Compilers.AAS

import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPNode
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object AASBuild_ledge {
    const val LEDGE_EPSILON = 0.1f

    //===============================================================
    //
    //	idLedge
    //
    //===============================================================
    internal class idLedge {
        val end: idVec3? = idVec3()
        var node: idBrushBSPNode? = null
        var numExpandedPlanes = 0
        var numPlanes = 0
        var numSplitPlanes = 0
        val planes: Array<idPlane?>? = idPlane.Companion.generateArray(8)
        val start: idVec3? = idVec3()

        //
        //
        constructor()
        constructor(v1: idVec3?, v2: idVec3?, gravityDir: idVec3?, n: idBrushBSPNode?) {
            start.oSet(v1)
            end.oSet(v2)
            node = n
            numPlanes = 4
            planes.get(0).SetNormal(v1.oMinus(v2).Cross(gravityDir))
            planes.get(0).Normalize()
            planes.get(0).FitThroughPoint(v1)
            planes.get(1).SetNormal(v1.oMinus(v2).Cross(planes.get(0).Normal()))
            planes.get(1).Normalize()
            planes.get(1).FitThroughPoint(v1)
            planes.get(2).SetNormal(v1.oMinus(v2))
            planes.get(2).Normalize()
            planes.get(2).FitThroughPoint(v1)
            planes.get(3).SetNormal(v2.oMinus(v1))
            planes.get(3).Normalize()
            planes.get(3).FitThroughPoint(v2)
        }

        fun AddPoint(v: idVec3?) {
            if (planes.get(2).Distance(v) > 0.0f) {
                start.oSet(v)
                planes.get(2).FitThroughPoint(start)
            }
            if (planes.get(3).Distance(v) > 0.0f) {
                end.oSet(v)
                planes.get(3).FitThroughPoint(end)
            }
        }

        /*
         ============
         idLedge::CreateBevels

         NOTE: this assumes the gravity is vertical
         ============
         */
        fun CreateBevels(gravityDir: idVec3?) {
            val i: Int
            var j: Int
            val bounds = idBounds()
            val size = idVec3()
            val normal = idVec3()
            bounds.Clear()
            bounds.AddPoint(start)
            bounds.AddPoint(end)
            size.oSet(bounds.oGet(1).oMinus(bounds.oGet(0)))

            // plane through ledge
            planes.get(0).SetNormal(start.oMinus(end).Cross(gravityDir))
            planes.get(0).Normalize()
            planes.get(0).FitThroughPoint(start)
            // axial bevels at start and end point
            i = if (size.oGet(1) > size.oGet(0)) 1 else 0
            normal.oSet(Vector.getVec3_origin())
            normal.oSet(i, 1.0f)
            j = if (end.oGet(i) > start.oGet(i)) 1 else 0
            planes.get(1 + j).SetNormal(normal)
            planes.get(1 +  /*!j*/(1 xor j)).SetNormal(normal.oNegative())
            planes.get(1).FitThroughPoint(start)
            planes.get(2).FitThroughPoint(end)
            numExpandedPlanes = 3
            // if additional bevels are required
            if (Math.abs(size.oGet( /*!i*/1 xor i)) > 0.01f) {
                normal.oSet(Vector.getVec3_origin())
                normal.oSet( /*!i]*/1 xor i, 1.0f)
                j = if (end.oGet( /*!i]*/1 xor i) > start.oGet( /*!i]*/1 xor i)) 1 else 0
                planes.get(3 + j).SetNormal(normal)
                planes.get(3 +  /*!j]*/1 xor j).SetNormal(normal.oNegative())
                planes.get(3).FitThroughPoint(start)
                planes.get(4).FitThroughPoint(end)
                numExpandedPlanes = 5
            }
            // opposite of first
            planes.get(numExpandedPlanes + 0) = planes.get(0).oNegative()
            // number of planes used for splitting
            numSplitPlanes = numExpandedPlanes + 1
            // top plane
            planes.get(numSplitPlanes + 0).SetNormal(start.oMinus(end).Cross(planes.get(0).Normal()))
            planes.get(numSplitPlanes + 0).Normalize()
            planes.get(numSplitPlanes + 0).FitThroughPoint(start)
            // bottom plane
            planes.get(numSplitPlanes + 1) = planes.get(numSplitPlanes + 0).oNegative()
            // total number of planes
            numPlanes = numSplitPlanes + 2
        }

        fun Expand(bounds: idBounds?, maxStepHeight: Float) {
            var i: Int
            var j: Int
            val v = idVec3()
            i = 0
            while (i < numExpandedPlanes) {
                j = 0
                while (j < 3) {
                    if (planes.get(i).Normal().oGet(j) > 0.0f) {
                        v.oSet(j, bounds.oGet(0, j))
                    } else {
                        v.oSet(j, bounds.oGet(1, j))
                    }
                    j++
                }
                planes.get(i).SetDist(planes.get(i).Dist() + v.times(planes.get(i).Normal().oNegative()))
                i++
            }
            planes.get(numSplitPlanes + 0).SetDist(planes.get(numSplitPlanes + 0).Dist() + maxStepHeight)
            planes.get(numSplitPlanes + 1).SetDist(planes.get(numSplitPlanes + 1).Dist() + 1.0f)
        }

        fun ChopWinding(winding: idWinding?): idWinding? {
            var i: Int
            var w: idWinding?
            w = winding.Copy()
            i = 0
            while (i < numPlanes && w != null) {
                w = w.Clip(planes.get(i).oNegative(), Plane.ON_EPSILON, true)
                i++
            }
            return w
        }

        fun PointBetweenBounds(v: idVec3?): Boolean {
            return planes.get(2).Distance(v) < AASBuild_ledge.LEDGE_EPSILON && planes.get(3)
                .Distance(v) < AASBuild_ledge.LEDGE_EPSILON
        }
    }
}