package neo.idlib.BV

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Lib
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import java.util.stream.Stream

/**
 *
 */
object Frustum {
    /*
     bit 0 = min x
     bit 1 = max x
     bit 2 = min y
     bit 3 = max y
     bit 4 = min z
     bit 5 = max z
     */
    private val boxVertPlanes: IntArray? = intArrayOf(
        1 shl 0 or (1 shl 2) or (1 shl 4),
        1 shl 1 or (1 shl 2) or (1 shl 4),
        1 shl 1 or (1 shl 3) or (1 shl 4),
        1 shl 0 or (1 shl 3) or (1 shl 4),
        1 shl 0 or (1 shl 2) or (1 shl 5),
        1 shl 1 or (1 shl 2) or (1 shl 5),
        1 shl 1 or (1 shl 3) or (1 shl 5),
        1 shl 0 or (1 shl 3) or (1 shl 5)
    )

    /*
     ============
     BoxToPoints
     ============
     */
    private fun BoxToPoints(center: idVec3?, extents: idVec3?, axis: idMat3?, points: Array<idVec3?>?) {
        val ax = idMat3()
        val temp: Array<idVec3?> = idVec3.Companion.generateArray(4)
        ax.oSet(0, axis.oGet(0).oMultiply(extents.oGet(0)))
        ax.oSet(1, axis.oGet(1).oMultiply(extents.oGet(1)))
        ax.oSet(2, axis.oGet(2).oMultiply(extents.oGet(2)))
        temp[0].oSet(center.oMinus(ax.oGet(0)))
        temp[1].oSet(center.oPlus(ax.oGet(0)))
        temp[2].oSet(ax.oGet(1).oMinus(ax.oGet(2)))
        temp[3].oSet(ax.oGet(1).oPlus(ax.oGet(2)))
        points.get(0).oSet(temp[0].oMinus(temp[3]))
        points.get(1).oSet(temp[1].oMinus(temp[3]))
        points.get(2).oSet(temp[1].oPlus(temp[2]))
        points.get(3).oSet(temp[0].oPlus(temp[2]))
        points.get(4).oSet(temp[0].oMinus(temp[2]))
        points.get(5).oSet(temp[1].oMinus(temp[2]))
        points.get(6).oSet(temp[1].oPlus(temp[3]))
        points.get(7).oSet(temp[0].oPlus(temp[3]))
    }

    /*
     ===============================================================================

     Orthogonal Frustum

     ===============================================================================
     */
    class idFrustum {
        private val origin // frustum origin
                : idVec3?
        private var axis // frustum orientation
                : idMat3?
        private var dFar // distance of far plane, dFar > dNear
                : Float
        private var dLeft // half the width at the far plane
                = 0f
        private var dNear // distance of near plane, dNear >= 0.0f
                : Float
        private var dUp // half the height at the far plane
                = 0f

        //
        //
        private var invFar // 1.0f / dFar
                = 0f

        constructor() {
            origin = idVec3()
            axis = idMat3()
            dFar = 0.0f
            dNear = dFar
        }

        constructor(f: idFrustum?) {
            origin = idVec3(f.origin)
            axis = idMat3(f.axis)
            dNear = f.dNear
            dFar = f.dFar
            dLeft = f.dLeft
            dUp = f.dUp
            invFar = f.invFar
        }

        fun SetOrigin(origin: idVec3?) {
            this.origin.oSet(origin)
        }

        fun SetAxis(axis: idMat3?) {
            this.axis = idMat3(axis)
        }

        fun SetSize(dNear: Float, dFar: Float, dLeft: Float, dUp: Float) {
            assert(dNear >= 0.0f && dFar > dNear && dLeft > 0.0f && dUp > 0.0f)
            this.dNear = dNear
            this.dFar = dFar
            this.dLeft = dLeft
            this.dUp = dUp
            invFar = 1.0f / dFar
        }

        fun SetPyramid(dNear: Float, dFar: Float) {
            assert(dNear >= 0.0f && dFar > dNear)
            this.dNear = dNear
            this.dFar = dFar
            dLeft = dFar
            dUp = dFar
            invFar = 1.0f / dFar
        }

        fun MoveNearDistance(dNear: Float) {
            assert(dNear >= 0.0f)
            this.dNear = dNear
        }

        fun MoveFarDistance(dFar: Float) {
            assert(dFar > dNear)
            val scale = dFar / this.dFar
            this.dFar = dFar
            dLeft *= scale
            dUp *= scale
            invFar = 1.0f / dFar
        }

        // returns frustum origin
        fun GetOrigin(): idVec3? {
            return origin
        }

        // returns frustum orientation
        fun GetAxis(): idMat3? {
            return axis
        }

        fun GetCenter(): idVec3? {                        // returns center of frustum
            return origin.oPlus(axis.oGet(0).oMultiply((dFar - dNear) * 0.5f))
        }

        fun IsValid(): Boolean {                            // returns true if the frustum is valid
            return dFar > dNear
        }

        fun GetNearDistance(): Float {                    // returns distance to near plane
            return dNear
        }

        fun GetFarDistance(): Float {                    // returns distance to far plane
            return dFar
        }

        //
        fun GetLeft(): Float {                            // returns left vector length
            return dLeft
        }

        fun GetUp(): Float {                            // returns up vector length
            return dUp
        }

        fun Expand(d: Float): idFrustum? {                    // returns frustum expanded in all directions with the given value
            val f = idFrustum(this)
            f.origin.oMinSet(f.axis.oGet(0).oMultiply(d))
            f.dFar += 2.0f * d
            f.dLeft = f.dFar * dLeft * invFar
            f.dUp = f.dFar * dUp * invFar
            f.invFar = 1.0f / dFar
            return f
        }

        fun ExpandSelf(d: Float): idFrustum? {                    // expands frustum in all directions with the given value
            origin.oMinSet(axis.oGet(0).oMultiply(d))
            dFar += 2.0f * d
            dLeft = dFar * dLeft * invFar
            dUp = dFar * dUp * invFar
            invFar = 1.0f / dFar
            return this
        }

        fun Translate(translation: idVec3?): idFrustum? {    // returns translated frustum
            val f = idFrustum(this)
            f.origin.oPluSet(translation)
            return f
        }

        fun TranslateSelf(translation: idVec3?): idFrustum? {        // translates frustum
            origin.oPluSet(translation)
            return this
        }

        //
        fun Rotate(rotation: idMat3?): idFrustum? {            // returns rotated frustum
            val f = idFrustum(this)
            f.axis.oMulSet(rotation)
            return f
        }

        fun RotateSelf(rotation: idMat3?): idFrustum? {            // rotates frustum
            axis.oMulSet(rotation)
            return this
        }

        fun PlaneDistance(plane: idPlane?): Float {
            val min = CFloat()
            val max = CFloat()
            AxisProjection(plane.Normal(), min, max)
            if (min.getVal() + plane.oGet(3) > 0.0f) {
                return min.getVal() + plane.oGet(0)
            }
            return if (max.getVal() + plane.oGet(3) < 0.0f) {
                max.getVal() + plane.oGet(3)
            } else 0.0f
        }

        //
        @JvmOverloads
        fun PlaneSide(plane: idPlane?, epsilon: Float = Plane.ON_EPSILON): Int {
            val min = CFloat()
            val max = CFloat()
            AxisProjection(plane.Normal(), min, max)
            if (min.getVal() + plane.oGet(3) > epsilon) {
                return Plane.PLANESIDE_FRONT
            }
            return if (max.getVal() + plane.oGet(3) < epsilon) {
                Plane.PLANESIDE_BACK
            } else Plane.PLANESIDE_CROSS
        }

        // fast culling but might not cull everything outside the frustum
        fun CullPoint(point: idVec3?): Boolean {
            val p = idVec3()
            val scale: Float

            // transform point to frustum space
            p.oSet(point.oMinus(origin).oMultiply(axis.Transpose()))
            // test whether or not the point is within the frustum
            if (p.x < dNear || p.x > dFar) {
                return true
            }
            scale = p.x * invFar
            return if (Math.abs(p.y) > dLeft * scale) {
                true
            } else Math.abs(p.z) > dUp * scale
        }

        /*
         ============
         idFrustum::CullBounds

         Tests if any of the planes of the frustum can be used as a separating plane.

         24 muls best case
         37 muls worst case
         ============
         */
        fun CullBounds(bounds: idBounds?): Boolean {
            val localOrigin = idVec3()
            val center = idVec3()
            val extents = idVec3()
            val localAxis: idMat3?
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))

            // transform the bounds into the space of this frustum
            localOrigin.oSet(center.oMinus(origin).oMultiply(axis.Transpose()))
            localAxis = axis.Transpose()
            return CullLocalBox(localOrigin, extents, localAxis)
        }

        /*
         ============
         idFrustum::CullBox

         Tests if any of the planes of the frustum can be used as a separating plane.

         39 muls best case
         61 muls worst case
         ============
         */
        fun CullBox(box: idBox?): Boolean {
            val localOrigin = idVec3()
            val localAxis: idMat3?

            // transform the box into the space of this frustum
            localOrigin.oSet(box.GetCenter().oMinus(origin).oMultiply(axis.Transpose()))
            localAxis = box.GetAxis().oMultiply(axis.Transpose())
            return CullLocalBox(localOrigin, box.GetExtents(), localAxis)
        }

        /*
         ============
         idFrustum::CullSphere

         Tests if any of the planes of the frustum can be used as a separating plane.

         9 muls best case
         21 muls worst case
         ============
         */
        fun CullSphere(sphere: idSphere?): Boolean {
            var d: Float
            val r: Float
            val rs: Float
            val sFar: Float
            val center = idVec3()
            center.oSet(sphere.GetOrigin().oMinus(origin).oMultiply(axis.Transpose()))
            r = sphere.GetRadius()

            // test near plane
            if (dNear - center.x > r) {
                return true
            }

            // test far plane
            if (center.x - dFar > r) {
                return true
            }
            rs = r * r
            sFar = dFar * dFar

            // test left/right planes
            d = dFar * Math.abs(center.y) - dLeft * center.x
            if (d * d > rs * (sFar + dLeft * dLeft)) {
                return true
            }

            // test up/down planes
            d = dFar * Math.abs(center.z) - dUp * center.x
            return d * d > rs * (sFar + dUp * dUp)
        }

        //
        /*
         ============
         idFrustum::CullFrustum

         Tests if any of the planes of this frustum can be used as a separating plane.

         58 muls best case
         88 muls worst case
         ============
         */
        fun CullFrustum(frustum: idFrustum?): Boolean {
            val localFrustum: idFrustum
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)

            // transform the given frustum into the space of this frustum
            localFrustum = idFrustum(frustum)
            localFrustum.origin.oSet(frustum.origin.oMinus(origin).oMultiply(axis.Transpose()))
            localFrustum.axis = frustum.axis.oMultiply(axis.Transpose())
            localFrustum.ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            return CullLocalFrustum(localFrustum, indexPoints, cornerVecs)
        }

        fun CullWinding(winding: idWinding?): Boolean {
            var i: Int
            val pointCull: IntArray
            val localPoints: Array<idVec3?> = idVec3.Companion.generateArray(winding.GetNumPoints())
            val transpose: idMat3?
            pointCull = IntArray(winding.GetNumPoints())
            transpose = axis.Transpose()
            i = 0
            while (i < winding.GetNumPoints()) {
                localPoints[i].oSet(winding.oGet(i).ToVec3().oMinus(origin).oMultiply(transpose))
                i++
            }
            return CullLocalWinding(localPoints, winding.GetNumPoints(), pointCull)
        }

        // exact intersection tests
        fun ContainsPoint(point: idVec3?): Boolean {
            return !CullPoint(point)
        }

        fun IntersectsBounds(bounds: idBounds?): Boolean {
            val localOrigin = idVec3()
            val center = idVec3()
            val extents = idVec3()
            val localAxis: idMat3?
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))
            localOrigin.oSet(center.oMinus(origin).oMultiply(axis.Transpose()))
            localAxis = axis.Transpose()
            if (CullLocalBox(localOrigin, extents, localAxis)) {
                return false
            }
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            if (BoundsCullLocalFrustum(bounds, this, indexPoints, cornerVecs)) {
                return false
            }
            List.idSwap(indexPoints, indexPoints, 2, 3)
            List.idSwap(indexPoints, indexPoints, 6, 7)
            if (LocalFrustumIntersectsBounds(indexPoints, bounds)) {
                return true
            }
            Frustum.BoxToPoints(localOrigin, extents, localAxis, indexPoints)
            return LocalFrustumIntersectsFrustum(indexPoints, true)
        }

        fun IntersectsBox(box: idBox?): Boolean {
            val localOrigin = idVec3()
            val localAxis: idMat3?
            localOrigin.oSet(box.GetCenter().oMinus(origin).oMultiply(axis.Transpose()))
            localAxis = box.GetAxis().oMultiply(axis.Transpose())
            if (CullLocalBox(localOrigin, box.GetExtents(), localAxis)) {
                return false
            }
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val localFrustum = idFrustum()
            localFrustum.oSet(this)
            localFrustum.origin.oSet(origin.oMinus(box.GetCenter()).oMultiply(box.GetAxis().Transpose()))
            localFrustum.axis = axis.oMultiply(box.GetAxis().Transpose())
            localFrustum.ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            if (BoundsCullLocalFrustum(
                    idBounds(box.GetExtents().oNegative(), box.GetExtents()),
                    localFrustum,
                    indexPoints,
                    cornerVecs
                )
            ) {
                return false
            }
            List.idSwap(indexPoints, indexPoints, 2, 3)
            List.idSwap(indexPoints, indexPoints, 6, 7)
            if (LocalFrustumIntersectsBounds(indexPoints, idBounds(box.GetExtents().oNegative(), box.GetExtents()))) {
                return true
            }
            Frustum.BoxToPoints(localOrigin, box.GetExtents(), localAxis, indexPoints)
            return LocalFrustumIntersectsFrustum(indexPoints, true)
        }

        private fun VORONOI_INDEX(x: Int, y: Int, z: Int): Int {
            return x + y * 3 + z * 9
        }

        fun IntersectsSphere(sphere: idSphere?): Boolean {
            val index: Int
            var x: Int
            var y: Int
            var z: Int
            var scale: Float
            val r: Float
            val d: Float
            val p = idVec3()
            val dir = idVec3()
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            if (CullSphere(sphere)) {
                return false
            }
            z = 0
            y = z
            x = y
            dir.Zero()
            p.oSet(sphere.GetOrigin().oMinus(origin).oMultiply(axis.Transpose()))
            if (p.x <= dNear) {
                scale = dNear * invFar
                dir.y = Math.abs(p.y) - dLeft * scale
                dir.z = Math.abs(p.z) - dUp * scale
            } else if (p.x >= dFar) {
                dir.y = Math.abs(p.y) - dLeft
                dir.z = Math.abs(p.z) - dUp
            } else {
                scale = p.x * invFar
                dir.y = Math.abs(p.y) - dLeft * scale
                dir.z = Math.abs(p.z) - dUp * scale
            }
            if (dir.y > 0.0f) {
                y = 1 + Math_h.FLOATSIGNBITNOTSET(p.y)
            }
            if (dir.z > 0.0f) {
                z = 1 + Math_h.FLOATSIGNBITNOTSET(p.z)
            }
            if (p.x < dNear) {
                scale = dLeft * dNear * invFar
                if (p.x < dNear + (scale - p.y) * scale * invFar) {
                    scale = dUp * dNear * invFar
                    if (p.x < dNear + (scale - p.z) * scale * invFar) {
                        x = 1
                    }
                }
            } else {
                if (p.x > dFar) {
                    x = 2
                } else if (p.x > dFar + (dLeft - p.y) * dLeft * invFar) {
                    x = 2
                } else if (p.x > dFar + (dUp - p.z) * dUp * invFar) {
                    x = 2
                }
            }
            r = sphere.GetRadius()
            index = VORONOI_INDEX(x, y, z)
            when (index) {
                VORONOI_INDEX_0_0_0 -> return true
                VORONOI_INDEX_1_0_0 -> return dNear - p.x < r
                VORONOI_INDEX_2_0_0 -> return p.x - dFar < r
                VORONOI_INDEX_0_1_0 -> {
                    d = dFar * p.y - dLeft * p.x
                    return d * d < r * r * (dFar * dFar + dLeft * dLeft)
                }
                VORONOI_INDEX_0_2_0 -> {
                    d = -dFar * p.z - dLeft * p.x
                    return d * d < r * r * (dFar * dFar + dLeft * dLeft)
                }
                VORONOI_INDEX_0_0_1 -> {
                    d = dFar * p.z - dUp * p.x
                    return d * d < r * r * (dFar * dFar + dUp * dUp)
                }
                VORONOI_INDEX_0_0_2 -> {
                    d = -dFar * p.z - dUp * p.x
                    return d * d < r * r * (dFar * dFar + dUp * dUp)
                }
                else -> {
                    ToIndexPoints(points)
                    when (index) {
                        VORONOI_INDEX_1_1_1 -> return sphere.ContainsPoint(points[0])
                        VORONOI_INDEX_2_1_1 -> return sphere.ContainsPoint(points[4])
                        VORONOI_INDEX_1_2_1 -> return sphere.ContainsPoint(points[1])
                        VORONOI_INDEX_2_2_1 -> return sphere.ContainsPoint(points[5])
                        VORONOI_INDEX_1_1_2 -> return sphere.ContainsPoint(points[2])
                        VORONOI_INDEX_2_1_2 -> return sphere.ContainsPoint(points[6])
                        VORONOI_INDEX_1_2_2 -> return sphere.ContainsPoint(points[3])
                        VORONOI_INDEX_2_2_2 -> return sphere.ContainsPoint(points[7])
                        VORONOI_INDEX_1_1_0 -> return sphere.LineIntersection(points[0], points[2])
                        VORONOI_INDEX_2_1_0 -> return sphere.LineIntersection(points[4], points[6])
                        VORONOI_INDEX_1_2_0 -> return sphere.LineIntersection(points[1], points[3])
                        VORONOI_INDEX_2_2_0 -> return sphere.LineIntersection(points[5], points[7])
                        VORONOI_INDEX_1_0_1 -> return sphere.LineIntersection(points[0], points[1])
                        VORONOI_INDEX_2_0_1 -> return sphere.LineIntersection(points[4], points[5])
                        VORONOI_INDEX_0_1_1 -> return sphere.LineIntersection(points[0], points[4])
                        VORONOI_INDEX_0_2_1 -> return sphere.LineIntersection(points[1], points[5])
                        VORONOI_INDEX_1_0_2 -> return sphere.LineIntersection(points[2], points[3])
                        VORONOI_INDEX_2_0_2 -> return sphere.LineIntersection(points[6], points[7])
                        VORONOI_INDEX_0_1_2 -> return sphere.LineIntersection(points[2], points[6])
                        VORONOI_INDEX_0_2_2 -> return sphere.LineIntersection(points[3], points[7])
                    }
                }
            }
            return false
        }

        fun IntersectsFrustum(frustum: idFrustum?): Boolean {
            val indexPoints2: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs2: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val localFrustum2: idFrustum
            localFrustum2 = idFrustum(frustum)
            localFrustum2.origin.oSet(frustum.origin.oMinus(origin).oMultiply(axis.Transpose()))
            localFrustum2.axis = frustum.axis.oMultiply(axis.Transpose())
            localFrustum2.ToIndexPointsAndCornerVecs(indexPoints2, cornerVecs2)
            if (CullLocalFrustum(localFrustum2, indexPoints2, cornerVecs2)) {
                return false
            }
            val indexPoints1: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs1: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val localFrustum1 = idFrustum(this)
            localFrustum1.origin.oSet(origin.oMinus(frustum.origin).oMultiply(frustum.axis.Transpose()))
            localFrustum1.axis = axis.oMultiply(frustum.axis.Transpose())
            localFrustum1.ToIndexPointsAndCornerVecs(indexPoints1, cornerVecs1)
            if (frustum.CullLocalFrustum(localFrustum1, indexPoints1, cornerVecs1)) {
                return false
            }
            List.idSwap(indexPoints2, indexPoints2, 2, 3)
            List.idSwap(indexPoints2, indexPoints2, 6, 7)
            if (LocalFrustumIntersectsFrustum(indexPoints2, localFrustum2.dNear > 0.0f)) {
                return true
            }
            List.idSwap(indexPoints1, indexPoints1, 2, 3)
            List.idSwap(indexPoints1, indexPoints1, 6, 7)
            return frustum.LocalFrustumIntersectsFrustum(indexPoints1, localFrustum1.dNear > 0.0f)
        }

        fun IntersectsWinding(winding: idWinding?): Boolean {
            var i: Int
            var j: Int
            val pointCull: IntArray
            val min = CFloat()
            val max = CFloat()
            val localPoints: Array<idVec3?> = idVec3.Companion.generateArray(winding.GetNumPoints())
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val transpose: idMat3?
            val plane = idPlane()
            pointCull = IntArray(winding.GetNumPoints())
            transpose = axis.Transpose()
            i = 0
            while (i < winding.GetNumPoints()) {
                localPoints[i].oSet(winding.oGet(i).ToVec3().oMinus(origin).oMultiply(transpose))
                i++
            }

            // if the winding is culled
            if (CullLocalWinding(localPoints, winding.GetNumPoints(), pointCull)) {
                return false
            }
            winding.GetPlane(plane)
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, plane.Normal(), min, max)

            // if the frustum does not cross the winding plane
            if (min.getVal() + plane.oGet(3) > 0.0f || max.getVal() + plane.oGet(3) < 0.0f) {
                return false
            }

            // test if any of the winding edges goes through the frustum
            i = 0
            while (i < winding.GetNumPoints()) {
                j = (i + 1) % winding.GetNumPoints()
                if (0 == pointCull[i] and pointCull[j]) {
                    if (LocalLineIntersection(localPoints[i], localPoints[j])) {
                        return true
                    }
                }
                i++
            }
            List.idSwap(indexPoints, indexPoints, 2, 3)
            List.idSwap(indexPoints, indexPoints, 6, 7)

            // test if any edges of the frustum intersect the winding
            i = 0
            while (i < 4) {
                if (winding.LineIntersection(plane, indexPoints[i], indexPoints[4 + i])) {
                    return true
                }
                i++
            }
            if (dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    if (winding.LineIntersection(plane, indexPoints[i], indexPoints[i + 1 and 3])) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (winding.LineIntersection(plane, indexPoints[4 + i], indexPoints[4 + (i + 1 and 3)])) {
                    return true
                }
                i++
            }
            return false
        }

        /*
         ============
         idFrustum::LineIntersection

         Returns true if the line intersects the box between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3?, end: idVec3?): Boolean {
            return LocalLineIntersection(
                start.oMinus(origin).oMultiply(axis.Transpose()),
                end.oMinus(origin).oMultiply(axis.Transpose())
            )
        }

        /*
         ============
         idFrustum::RayIntersection

         Returns true if the ray intersects the bounds.
         The ray can intersect the bounds in both directions from the start point.
         If start is inside the frustum then scale1 < 0 and scale2 > 0.
         ============
         */
        fun RayIntersection(start: idVec3?, dir: idVec3?, scale1: CFloat?, scale2: CFloat?): Boolean {
            return if (LocalRayIntersection(
                    start.oMinus(origin).oMultiply(axis.Transpose()),
                    dir.oMultiply(axis.Transpose()),
                    scale1,
                    scale2
                )
            ) {
                true
            } else scale1.getVal() <= scale2.getVal()
        }

        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the bounds.
         ============
         */
        // returns true if the projection origin is far enough away from the bounding volume to create a valid frustum
        fun FromProjection(bounds: idBounds?, projectionOrigin: idVec3?, dFar: Float): Boolean {
            return FromProjection(
                idBox(bounds, Vector.getVec3_origin(), idMat3.Companion.getMat3_identity()),
                projectionOrigin,
                dFar
            )
        }

        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the box.
         ============
         */
        fun FromProjection(box: idBox?, projectionOrigin: idVec3?, dFar: Float): Boolean {
            var i: Int
            var bestAxis: Int
            var value: Float
            var bestValue: Float
            val dir = idVec3()
            assert(dFar > 0.0f)
            invFar = 0.0f
            this.dFar = invFar
            dNear = this.dFar
            dir.oSet(box.GetCenter().oMinus(projectionOrigin))
            if (dir.Normalize() == 0.0f) {
                return false
            }
            bestAxis = 0
            bestValue = Math.abs(box.GetAxis().oGet(0).oMultiply(dir))
            i = 1
            while (i < 3) {
                value = Math.abs(box.GetAxis().oGet(i).oMultiply(dir))
                if (value * box.GetExtents().oGet(bestAxis) * box.GetExtents()
                        .oGet(bestAxis) < bestValue * box.GetExtents().oGet(i) * box.GetExtents().oGet(i)
                ) {
                    bestValue = value
                    bestAxis = i
                }
                i++
            }

//#if 1
            var j: Int
            var minX: Int
            var minY: Int
            var maxY: Int
            var minZ: Int
            var maxZ: Int
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            maxZ = 0
            minZ = maxZ
            maxY = minZ
            minY = maxY
            minX = minY
            j = 0
            while (j < 2) {
                axis.oSet(0, dir)
                axis.oSet(
                    1,
                    box.GetAxis().oGet(bestAxis)
                        .oMinus(axis.oGet(0).oMultiply(box.GetAxis().oGet(bestAxis).oMultiply(axis.oGet(0))))
                )
                axis.oGet(1).Normalize()
                axis.oGet(2).Cross(axis.oGet(0), axis.oGet(1))
                Frustum.BoxToPoints(
                    box.GetCenter().oMinus(projectionOrigin).oMultiply(axis.Transpose()),
                    box.GetExtents(),
                    box.GetAxis().oMultiply(axis.Transpose()),
                    points
                )
                if (points[0].x <= 1.0f) {
                    return false
                }
                maxZ = 0
                minZ = maxZ
                maxY = minZ
                minY = maxY
                minX = minY
                i = 1
                while (i < 8) {
                    if (points[i].x <= 1.0f) {
                        return false
                    }
                    if (points[i].x < points[minX].x) {
                        minX = i
                    }
                    if (points[minY].x * points[i].y < points[i].x * points[minY].y) {
                        minY = i
                    } else if (points[maxY].x * points[i].y > points[i].x * points[maxY].y) {
                        maxY = i
                    }
                    if (points[minZ].x * points[i].z < points[i].x * points[minZ].z) {
                        minZ = i
                    } else if (points[maxZ].x * points[i].z > points[i].x * points[maxZ].z) {
                        maxZ = i
                    }
                    i++
                }
                if (j == 0) {
                    dir.oPluSet(
                        axis.oGet(1).oMultiply(
                            idMath.Tan16(
                                0.5f * (idMath.ATan16(points[minY].y, points[minY].x) + idMath.ATan16(
                                    points[maxY].y,
                                    points[maxY].x
                                ))
                            )
                        )
                    )
                    dir.oPluSet(
                        axis.oGet(2).oMultiply(
                            idMath.Tan16(
                                0.5f * (idMath.ATan16(points[minZ].z, points[minZ].x) + idMath.ATan16(
                                    points[maxZ].z,
                                    points[maxZ].x
                                ))
                            )
                        )
                    )
                    dir.Normalize()
                }
                j++
            }
            origin.oSet(projectionOrigin)
            dNear = points[minX].x
            this.dFar = dFar
            dLeft = Lib.Companion.Max(
                Math.abs(points[minY].y / points[minY].x),
                Math.abs(points[maxY].y / points[maxY].x)
            ) * dFar
            dUp = Lib.Companion.Max(
                Math.abs(points[minZ].z / points[minZ].x),
                Math.abs(points[maxZ].z / points[maxZ].x)
            ) * dFar
            invFar = 1.0f / dFar
            return true
        }

        //
        /*
         ============
         idFrustum::FromProjection

         Creates a frustum which contains the projection of the sphere.
         ============
         */
        fun FromProjection(sphere: idSphere?, projectionOrigin: idVec3?, dFar: Float): Boolean {
            val dir = idVec3()
            val d: Float
            val r: Float
            val s: Float
            val x: Float
            val y: Float
            assert(dFar > 0.0f)
            dir.oSet(sphere.GetOrigin().oMinus(projectionOrigin))
            d = dir.Normalize()
            r = sphere.GetRadius()
            if (d <= r + 1.0f) {
                invFar = 0.0f
                this.dFar = invFar
                dNear = this.dFar
                return false
            }
            origin.oSet(projectionOrigin)
            axis = dir.ToMat3()
            s = idMath.Sqrt(d * d - r * r)
            x = r / d * s
            y = idMath.Sqrt(s * s - x * x)
            dNear = d - r
            this.dFar = dFar
            dLeft = x / y * dFar
            dUp = dLeft
            invFar = 1.0f / dFar
            return true
        }

        /*
         ============
         idFrustum::ConstrainToBounds

         Returns false if no part of the bounds extends beyond the near plane.
         ============
         */
        // moves the far plane so it extends just beyond the bounding volume
        fun ConstrainToBounds(bounds: idBounds?): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            bounds.AxisProjection(axis.oGet(0), min, max)
            newdFar = max.getVal() - origin.oMultiply(axis.oGet(0))
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ConstrainToBox

         Returns false if no part of the box extends beyond the near plane.
         ============
         */
        fun ConstrainToBox(box: idBox?): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            box.AxisProjection(axis.oGet(0), min, max)
            newdFar = max.getVal() - origin.oMultiply(axis.oGet(0))
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ConstrainToSphere

         Returns false if no part of the sphere extends beyond the near plane.
         ============
         */
        fun ConstrainToSphere(sphere: idSphere?): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            sphere.AxisProjection(axis.oGet(0), min, max)
            newdFar = max.getVal() - origin.oMultiply(axis.oGet(0))
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        //
        /*
         ============
         idFrustum::ConstrainToFrustum

         Returns false if no part of the frustum extends beyond the near plane.
         ============
         */
        fun ConstrainToFrustum(frustum: idFrustum?): Boolean {
            val min = CFloat()
            val max = CFloat()
            val newdFar: Float
            frustum.AxisProjection(axis.oGet(0), min, max)
            newdFar = max.getVal() - origin.oMultiply(axis.oGet(0))
            if (newdFar <= dNear) {
                MoveFarDistance(dNear + 1.0f)
                return false
            }
            MoveFarDistance(newdFar)
            return true
        }

        /*
         ============
         idFrustum::ToPlanes

         planes point outwards
         ============
         */
        fun ToPlanes(planes: Array<idPlane?>?) {            // planes point outwards
            var i: Int
            val scaled: Array<idVec3?> = idVec3.Companion.generateArray(2)
            val points: Array<idVec3?> = idVec3.Companion.generateArray(4)
            planes.get(0).oNorSet(axis.oGet(0).oNegative())
            planes.get(0).SetDist(-dNear)
            planes.get(1).oNorSet(axis.oGet(0))
            planes.get(1).SetDist(dFar)
            scaled[0].oSet(axis.oGet(1).oMultiply(dLeft))
            scaled[1].oSet(axis.oGet(2).oMultiply(dUp))
            points[0].oSet(scaled[0].oPlus(scaled[1]))
            points[1].oSet(scaled[0].oPlus(scaled[1]).oNegative())
            points[2].oSet(scaled[0].oMinus(scaled[1]).oNegative())
            points[3].oSet(scaled[0].oMinus(scaled[1]))
            i = 0
            while (i < 4) {
                planes.get(i + 2).oNorSet(points[i].Cross(points[i + 1 and 3].oMinus(points[i])))
                planes.get(i + 2).Normalize()
                planes.get(i + 2).FitThroughPoint(points[i])
                i++
            }
        }

        //
        fun ToPoints(points: Array<idVec3?>?) {                // 8 corners of the frustum
            val scaled = idMat3()
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dNear)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft * dNear * invFar))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp * dNear * invFar))
            points.get(0).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            points.get(1).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            points.get(2).oSet(points.get(1).oMinus(scaled.oGet(2)))
            points.get(3).oSet(points.get(0).oMinus(scaled.oGet(2)))
            points.get(0).oPluSet(scaled.oGet(2))
            points.get(1).oPluSet(scaled.oGet(2))
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dFar)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp))
            points.get(4).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            points.get(5).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            points.get(6).oSet(points.get(5).oMinus(scaled.oGet(2)))
            points.get(7).oSet(points.get(4).oMinus(scaled.oGet(2)))
            points.get(4).oPluSet(scaled.oGet(2))
            points.get(5).oPluSet(scaled.oGet(2))
        }

        /*
         ============
         idFrustum::AxisProjection

         40 muls
         ============
         */
        // calculates the projection of this frustum onto the given axis
        fun AxisProjection(dir: idVec3?, min: CFloat?, max: CFloat?) {
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, dir, min, max)
        }

        //
        /*
         ============
         idFrustum::AxisProjection

         76 muls
         ============
         */
        fun AxisProjection(ax: idMat3?, bounds: idBounds?) {
            val indexPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val b00 = CFloat(bounds.oGet(0).oGet(0))
            val b01 = CFloat(bounds.oGet(0).oGet(1))
            val b02 = CFloat(bounds.oGet(0).oGet(2))
            val b10 = CFloat(bounds.oGet(1).oGet(0))
            val b11 = CFloat(bounds.oGet(1).oGet(1))
            val b12 = CFloat(bounds.oGet(1).oGet(2))
            ToIndexPointsAndCornerVecs(indexPoints, cornerVecs)
            AxisProjection(indexPoints, cornerVecs, ax.oGet(0), b00, b11)
            AxisProjection(indexPoints, cornerVecs, ax.oGet(1), b01, b11)
            AxisProjection(indexPoints, cornerVecs, ax.oGet(2), b02, b12)
            bounds.oSet(0, 0, b00.getVal())
            bounds.oSet(0, 1, b01.getVal())
            bounds.oSet(0, 2, b02.getVal())
            bounds.oSet(1, 0, b10.getVal())
            bounds.oSet(1, 1, b11.getVal())
            bounds.oSet(1, 2, b12.getVal())
        }

        // calculates the bounds for the projection in this frustum
        fun ProjectionBounds(bounds: idBounds?, projectionBounds: idBounds?): Boolean {
            return ProjectionBounds(
                idBox(bounds, Vector.getVec3_origin(), idMat3.Companion.getMat3_identity()),
                projectionBounds
            )
        }

        fun ProjectionBounds(box: idBox?, projectionBounds: idBounds?): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val pointCull = Stream.generate { CInt() }.limit(8).toArray<CInt?> { _Dummy_.__Array__() }
            val scale1 = CFloat()
            val scale2 = CFloat()
            var localFrustum: idFrustum
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val localOrigin = idVec3()
            val localAxis: idMat3?
            val localScaled: idMat3?
            val bounds = idBounds(box.GetExtents().oNegative(), box.GetExtents())

            // if the frustum origin is inside the bounds
            if (bounds.ContainsPoint(origin.oMinus(box.GetCenter()).oMultiply(box.GetAxis().Transpose()))) {
                // bounds that cover the whole frustum
                val boxMin = CFloat()
                val boxMax = CFloat()
                val base: Float
                base = origin.oMultiply(axis.oGet(0))
                box.AxisProjection(axis.oGet(0), boxMin, boxMax)
                projectionBounds.oSet(0, 0, boxMin.getVal() - base)
                projectionBounds.oSet(1, 0, boxMax.getVal() - base)
                projectionBounds.oSet(0, 1, -1.0f)
                projectionBounds.oSet(0, 2, -1.0f)
                projectionBounds.oSet(1, 1, 1.0f)
                projectionBounds.oSet(1, 2, 1.0f)
                return true
            }
            projectionBounds.Clear()

            // transform the bounds into the space of this frustum
            localOrigin.oSet(box.GetCenter().oMinus(origin).oMultiply(axis.Transpose()))
            localAxis = box.GetAxis().oMultiply(axis.Transpose())
            Frustum.BoxToPoints(localOrigin, box.GetExtents(), localAxis, points)

            // test outer four edges of the bounds
            culled = -1
            outside = 0
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                AddLocalLineToProjectionBoundsSetCull(
                    points[p1],
                    points[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1].getVal() and pointCull[p2].getVal())
                outside = outside or (pointCull[p1].getVal() or pointCull[p2].getVal())
                i++
            }

            // if the bounds are completely outside this frustum
            if (culled != 0) {
                return false
            }

            // if the bounds are completely inside this frustum
            if (0 == outside) {
                return true
            }

            // test the remaining edges of the bounds
            i = 0
            while (i < 4) {
                p1 = i
                p2 = i + 1 and 3
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1].getVal(),
                    pointCull[p2].getVal(),
                    projectionBounds
                )
                i++
            }
            i = 0
            while (i < 4) {
                p1 = 4 + i
                p2 = 4 + (i + 1 and 3)
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1].getVal(),
                    pointCull[p2].getVal(),
                    projectionBounds
                )
                i++
            }

            // if the bounds extend beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                localOrigin.oSet(origin.oMinus(box.GetCenter()).oMultiply(box.GetAxis().Transpose()))
                localScaled = axis.oMultiply(box.GetAxis().Transpose())
                localScaled.oGet(0).oMulSet(dFar)
                localScaled.oGet(1).oMulSet(dLeft)
                localScaled.oGet(2).oMulSet(dUp)

                // test the outer edges of this frustum for intersection with the bounds
                if (outside and 2 == 2 && outside and 8 == 8) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled.oGet(0).oMinus(localScaled.oGet(1).oMinus(localScaled.oGet(2))),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, -1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled.oGet(0).oMinus(localScaled.oGet(1).oPlus(localScaled.oGet(2))),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, -1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled.oGet(0).oPlus(localScaled.oGet(1).oMinus(localScaled.oGet(2))),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, 1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    BoundsRayIntersection(
                        bounds,
                        localOrigin,
                        localScaled.oGet(0).oPlus(localScaled.oGet(1).oPlus(localScaled.oGet(2))),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, 1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        fun ProjectionBounds(sphere: idSphere?, projectionBounds: idBounds?): Boolean {
            var d: Float
            val r: Float
            val rs: Float
            val sFar: Float
            val center = idVec3()
            projectionBounds.Clear()
            center.oSet(sphere.GetOrigin().oMinus(origin).oMultiply(axis.Transpose()))
            r = sphere.GetRadius()
            rs = r * r
            sFar = dFar * dFar

            // test left/right planes
            d = dFar * Math.abs(center.y) - dLeft * center.x
            if (d * d > rs * (sFar + dLeft * dLeft)) {
                return false
            }

            // test up/down planes
            d = dFar * Math.abs(center.z) - dUp * center.x
            if (d * d > rs * (sFar + dUp * dUp)) {
                return false
            }

            // bounds that cover the whole frustum
            projectionBounds.oGet(0).x = 0.0f
            projectionBounds.oGet(1).x = dFar
            projectionBounds.oGet(0).z = -1.0f
            projectionBounds.oGet(0).y = projectionBounds.oGet(0).z
            projectionBounds.oGet(1).z = 1.0f
            projectionBounds.oGet(1).y = projectionBounds.oGet(1).z
            return true
        }

        fun ProjectionBounds(frustum: idFrustum?, projectionBounds: idBounds?): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val pointCull = Stream.generate { CInt() }.limit(8).toArray<CInt?> { _Dummy_.__Array__() }
            val scale1 = CFloat()
            val scale2 = CFloat()
            val localFrustum: idFrustum
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val localOrigin = idVec3()
            val localScaled: idMat3?

            // if the frustum origin is inside the other frustum
            if (frustum.ContainsPoint(origin)) {
                // bounds that cover the whole frustum
                val frustumMin = CFloat()
                val frustumMax = CFloat()
                val base: Float
                base = origin.oMultiply(axis.oGet(0))
                frustum.AxisProjection(axis.oGet(0), frustumMin, frustumMax)
                projectionBounds.oGet(0).x = frustumMin.getVal() - base
                projectionBounds.oGet(1).x = frustumMax.getVal() - base
                projectionBounds.oGet(0).z = -1.0f
                projectionBounds.oGet(0).y = projectionBounds.oGet(0).z
                projectionBounds.oGet(1).z = 1.0f
                projectionBounds.oGet(1).y = projectionBounds.oGet(1).z
                return true
            }
            projectionBounds.Clear()

            // transform the given frustum into the space of this frustum
            localFrustum = idFrustum(frustum)
            localFrustum.origin.oSet(frustum.origin.oMinus(origin).oMultiply(axis.Transpose()))
            localFrustum.axis = frustum.axis.oMultiply(axis.Transpose())
            localFrustum.ToPoints(points)

            // test outer four edges of the other frustum
            culled = -1
            outside = 0
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                AddLocalLineToProjectionBoundsSetCull(
                    points[p1],
                    points[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1].getVal() and pointCull[p2].getVal())
                outside = outside or (pointCull[p1].getVal() or pointCull[p2].getVal())
                i++
            }

            // if the other frustum is completely outside this frustum
            if (culled != 0) {
                return false
            }

            // if the other frustum is completely inside this frustum
            if (0 == outside) {
                return true
            }

            // test the remaining edges of the other frustum
            if (localFrustum.dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = i + 1 and 3
                    AddLocalLineToProjectionBoundsUseCull(
                        points[p1],
                        points[p2],
                        pointCull[p1].getVal(),
                        pointCull[p2].getVal(),
                        projectionBounds
                    )
                    i++
                }
            }
            i = 0
            while (i < 4) {
                p1 = 4 + i
                p2 = 4 + (i + 1 and 3)
                AddLocalLineToProjectionBoundsUseCull(
                    points[p1],
                    points[p2],
                    pointCull[p1].getVal(),
                    pointCull[p2].getVal(),
                    projectionBounds
                )
                i++
            }

            // if the other frustum extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                localOrigin.oSet(origin.oMinus(frustum.origin).oMultiply(frustum.axis.Transpose()))
                localScaled = axis.oMultiply(frustum.axis.Transpose())
                localScaled.oGet(0).oMulSet(dFar)
                localScaled.oGet(1).oMulSet(dLeft)
                localScaled.oGet(2).oMulSet(dUp)

                // test the outer edges of this frustum for intersection with the other frustum
                if (outside and 2 == 2 && outside and 8 == 8) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled.oGet(0).oMinus(localScaled.oGet(1)).oMinus(localScaled.oGet(2)),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, -1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled.oGet(0).oMinus(localScaled.oGet(1)).oPlus(localScaled.oGet(2)),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, -1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled.oGet(0).oPlus(localScaled.oGet(1)).oMinus(localScaled.oGet(2)),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, 1.0f, -1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    frustum.LocalRayIntersection(
                        localOrigin,
                        localScaled.oGet(0).oPlus(localScaled.oGet(1)).oPlus(localScaled.oGet(2)),
                        scale1,
                        scale2
                    )
                    if (scale1.getVal() <= scale2.getVal() && scale1.getVal() >= 0.0f) {
                        projectionBounds.AddPoint(idVec3(scale1.getVal() * dFar, 1.0f, 1.0f))
                        projectionBounds.AddPoint(idVec3(scale2.getVal() * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        fun ProjectionBounds(winding: idWinding?, projectionBounds: idBounds?): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            var culled: Int
            var outside: Int
            val scale = CFloat()
            val localPoints: Array<idVec3?> = idVec3.Companion.generateArray(winding.GetNumPoints())
            val transpose: idMat3?
            val scaled = idMat3()
            val plane = idPlane()
            projectionBounds.Clear()

            // transform the winding points into the space of this frustum
            transpose = axis.Transpose()
            i = 0
            while (i < winding.GetNumPoints()) {
                localPoints[i].oSet(winding.oGet(0).ToVec3().oMinus(origin).oMultiply(transpose))
                i++
            }

            // test the winding edges
            culled = -1
            outside = 0
            val pointCull =
                Stream.generate { CInt() }.limit(winding.GetNumPoints().toLong()).toArray<CInt?> { _Dummy_.__Array__() }
            i = 0
            while (i < winding.GetNumPoints()) {
                p1 = i
                p2 = (i + 1) % winding.GetNumPoints()
                AddLocalLineToProjectionBoundsSetCull(
                    localPoints[p1],
                    localPoints[p2],
                    pointCull[p1],
                    pointCull[p2],
                    projectionBounds
                )
                culled = culled and (pointCull[p1].getVal() and pointCull[p2].getVal())
                outside = outside or (pointCull[p1].getVal() or pointCull[p2].getVal())
                i += 2
            }

            // if completely culled
            if (culled != 0) {
                return false
            }

            // if completely inside
            if (0 == outside) {
                return true
            }

            // test remaining winding edges
            i = 1
            while (i < winding.GetNumPoints()) {
                p1 = i
                p2 = (i + 1) % winding.GetNumPoints()
                AddLocalLineToProjectionBoundsUseCull(
                    localPoints[p1],
                    localPoints[p2],
                    pointCull[p1].getVal(),
                    pointCull[p2].getVal(),
                    projectionBounds
                )
                i += 2
            }

            // if the winding extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {
                winding.GetPlane(plane)
                scaled.oSet(0, axis.oGet(0).oMultiply(dFar))
                scaled.oSet(1, axis.oGet(1).oMultiply(dLeft))
                scaled.oSet(2, axis.oGet(2).oMultiply(dUp))

                // test the outer edges of this frustum for intersection with the winding
                if (outside and 2 == 2 && outside and 8 == 8) {
                    if (winding.RayIntersection(
                            plane,
                            origin,
                            scaled.oGet(0).oMinus(scaled.oGet(1)).oMinus(scaled.oGet(2)),
                            scale
                        )
                    ) {
                        projectionBounds.AddPoint(idVec3(scale.getVal() * dFar, -1.0f, -1.0f))
                    }
                }
                if (outside and 2 == 2 && outside and 4 == 4) {
                    if (winding.RayIntersection(
                            plane,
                            origin,
                            scaled.oGet(0).oMinus(scaled.oGet(1)).oPlus(scaled.oGet(2)),
                            scale
                        )
                    ) {
                        projectionBounds.AddPoint(idVec3(scale.getVal() * dFar, -1.0f, 1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 8 == 8) {
                    if (winding.RayIntersection(
                            plane,
                            origin,
                            scaled.oGet(0).oPlus(scaled.oGet(1)).oMinus(scaled.oGet(2)),
                            scale
                        )
                    ) {
                        projectionBounds.AddPoint(idVec3(scale.getVal() * dFar, 1.0f, -1.0f))
                    }
                }
                if (outside and 1 == 1 && outside and 2 == 2) {
                    if (winding.RayIntersection(
                            plane,
                            origin,
                            scaled.oGet(0).oPlus(scaled.oGet(1)).oPlus(scaled.oGet(2)),
                            scale
                        )
                    ) {
                        projectionBounds.AddPoint(idVec3(scale.getVal() * dFar, 1.0f, 1.0f))
                    }
                }
            }
            return true
        }

        // calculates the bounds for the projection in this frustum of the given frustum clipped to the given box
        fun ClippedProjectionBounds(frustum: idFrustum?, clipBox: idBox?, projectionBounds: idBounds?): Boolean {
            var i: Int
            var p1: Int
            var p2: Int
            val usedClipPlanes: Int
            val nearCull: Int
            val farCull: Int
            var outside: Int
            val clipPointCull = Stream.generate { CInt() }.limit(8).toArray<CInt?> { _Dummy_.__Array__() }
            val clipPlanes = Stream.generate { CInt() }.limit(4).toArray<CInt?> { _Dummy_.__Array__() }
            val pointCull = Stream.generate { CInt() }.limit(2).toArray<CInt?> { _Dummy_.__Array__() }
            val boxPointCull = Stream.generate { CInt() }.limit(8).toArray<CInt?> { _Dummy_.__Array__() }
            val startClip = CInt()
            val endClip = CInt()
            val leftScale: Float
            val upScale: Float
            val s1 = CFloat()
            val s2 = CFloat()
            val t1 = CFloat()
            val t2 = CFloat()
            val clipFractions = Stream.generate { CFloat() }.limit(4).toArray<CFloat?> { _Dummy_.__Array__() }
            val localFrustum: idFrustum
            val localOrigin1 = idVec3()
            val localOrigin2 = idVec3()
            val start = idVec3()
            val end = idVec3()
            val clipPoints: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val localPoints1: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val localPoints2: Array<idVec3?> = idVec3.Companion.generateArray(8)
            var localAxis1: idMat3?
            var localAxis2: idMat3?
            var transpose: idMat3
            val clipBounds = idBounds()

            // if the frustum origin is inside the other frustum
            if (frustum.ContainsPoint(origin)) {
                // bounds that cover the whole frustum
                val clipBoxMin = CFloat()
                val clipBoxMax = CFloat()
                val frustumMin = CFloat()
                val frustumMax = CFloat()
                val base = CFloat()
                base.setVal(origin.oMultiply(axis.oGet(0)))
                clipBox.AxisProjection(axis.oGet(0), clipBoxMin, clipBoxMax)
                frustum.AxisProjection(axis.oGet(0), frustumMin, frustumMax)
                projectionBounds.oGet(0).x = Lib.Companion.Max(clipBoxMin.getVal(), frustumMin.getVal()) - base.getVal()
                projectionBounds.oGet(1).x = Lib.Companion.Min(clipBoxMax.getVal(), frustumMax.getVal()) - base.getVal()
                projectionBounds.oGet(0).z = -1.0f
                projectionBounds.oGet(0).y = projectionBounds.oGet(0).z
                projectionBounds.oGet(1).z = 1.0f
                projectionBounds.oGet(1).y = projectionBounds.oGet(1).z
                return true
            }
            projectionBounds.Clear()

            // clip the outer edges of the given frustum to the clip bounds
            frustum.ClipFrustumToBox(clipBox, clipFractions, clipPlanes)
            usedClipPlanes =
                clipPlanes[0].getVal() or clipPlanes[1].getVal() or clipPlanes[2].getVal() or clipPlanes[3].getVal()

            // transform the clipped frustum to the space of this frustum
            transpose = idMat3(axis)
            transpose.TransposeSelf()
            localFrustum = idFrustum(frustum)
            localFrustum.origin.oSet(frustum.origin.oMinus(origin).oMultiply(transpose))
            localFrustum.axis = frustum.axis.oMultiply(transpose)
            localFrustum.ToClippedPoints(clipFractions, clipPoints)

            // test outer four edges of the clipped frustum
            i = 0
            while (i < 4) {
                p1 = i
                p2 = 4 + i
                val clipPointCull_p1 = CInt()
                val clipPointCull_p2 = CInt()
                AddLocalLineToProjectionBoundsSetCull(
                    clipPoints[p1],
                    clipPoints[p2],
                    clipPointCull_p1,
                    clipPointCull_p2,
                    projectionBounds
                )
                clipPointCull[p1].setVal(clipPointCull_p1.getVal())
                clipPointCull[p2].setVal(clipPointCull_p2.getVal())
                i++
            }

            // get cull bits for the clipped frustum
            outside =
                (clipPointCull[0].getVal() or clipPointCull[1].getVal() or clipPointCull[2].getVal() or clipPointCull[3].getVal()
                        or clipPointCull[4].getVal() or clipPointCull[5].getVal() or clipPointCull[6].getVal() or clipPointCull[7].getVal())
            nearCull =
                clipPointCull[0].getVal() and clipPointCull[1].getVal() and clipPointCull[2].getVal() and clipPointCull[3].getVal()
            farCull =
                clipPointCull[4].getVal() and clipPointCull[5].getVal() and clipPointCull[6].getVal() and clipPointCull[7].getVal()

            // if the clipped frustum is not completely inside this frustum
            if (outside != 0) {

                // test the remaining edges of the clipped frustum
                if (0 == nearCull && localFrustum.dNear > 0.0f) {
                    i = 0
                    while (i < 4) {
                        p1 = i
                        p2 = i + 1 and 3
                        AddLocalLineToProjectionBoundsUseCull(
                            clipPoints[p1],
                            clipPoints[p2],
                            clipPointCull[p1].getVal(),
                            clipPointCull[p2].getVal(),
                            projectionBounds
                        )
                        i++
                    }
                }
                if (0 == farCull) {
                    i = 0
                    while (i < 4) {
                        p1 = 4 + i
                        p2 = 4 + (i + 1 and 3)
                        AddLocalLineToProjectionBoundsUseCull(
                            clipPoints[p1],
                            clipPoints[p2],
                            clipPointCull[p1].getVal(),
                            clipPointCull[p2].getVal(),
                            projectionBounds
                        )
                        i++
                    }
                }
            }

            // if the clipped frustum far end points are inside this frustum
            if (!(farCull != 0 && 0 == nearCull and farCull)
                &&  // if the clipped frustum is not clipped to a single plane of the clip bounds
                (clipPlanes[0] !== clipPlanes[1] || clipPlanes[1] !== clipPlanes[2] || clipPlanes[2] !== clipPlanes[3])
            ) {

                // transform the clip box into the space of the other frustum
                transpose = idMat3(frustum.axis)
                transpose.TransposeSelf()
                localOrigin1.oSet(clipBox.GetCenter().oMinus(frustum.origin).oMultiply(transpose))
                localAxis1 = clipBox.GetAxis().oMultiply(transpose)
                Frustum.BoxToPoints(localOrigin1, clipBox.GetExtents(), localAxis1, localPoints1)

                // cull the box corners with the other frustum
                leftScale = frustum.dLeft * frustum.invFar
                upScale = frustum.dUp * frustum.invFar
                i = 0
                while (i < 8) {
                    val p = localPoints1[i]
                    if (0 == Frustum.boxVertPlanes[i] and usedClipPlanes || p.x <= 0.0f) {
                        boxPointCull[i].setVal(1 or 2 or 4 or 8)
                    } else {
                        boxPointCull[i].setVal(0)
                        if (Math.abs(p.y) > p.x * leftScale) {
                            boxPointCull[i].setVal(boxPointCull[i].getVal() or 1 shl Math_h.FLOATSIGNBITSET(p.y))
                        }
                        if (Math.abs(p.z) > p.x * upScale) {
                            boxPointCull[i].setVal(boxPointCull[i].getVal() or 4 shl Math_h.FLOATSIGNBITSET(p.z))
                        }
                    }
                    i++
                }

                // transform the clip box into the space of this frustum
                transpose = idMat3(axis)
                transpose.TransposeSelf()
                localOrigin2.oSet(clipBox.GetCenter().oMinus(origin).oMultiply(transpose))
                localAxis2 = clipBox.GetAxis().oMultiply(transpose)
                Frustum.BoxToPoints(localOrigin2, clipBox.GetExtents(), localAxis2, localPoints2)

                // clip the edges of the clip bounds to the other frustum and add the clipped edges to the projection bounds
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = 4 + i
                    if (0 == boxPointCull[p1].getVal() and boxPointCull[p2].getVal()) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0].getVal(),
                                startClip.getVal(),
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1].getVal(),
                                endClip.getVal(),
                                projectionBounds
                            )
                            outside = outside or (pointCull[0].getVal() or pointCull[1].getVal())
                        }
                    }
                    i++
                }
                i = 0
                while (i < 4) {
                    p1 = i
                    p2 = i + 1 and 3
                    if (0 == boxPointCull[p1].getVal() and boxPointCull[p2].getVal()) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0].getVal(),
                                startClip.getVal(),
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1].getVal(),
                                endClip.getVal(),
                                projectionBounds
                            )
                            outside = outside or (pointCull[0].getVal() or pointCull[1].getVal())
                        }
                    }
                    i++
                }
                i = 0
                while (i < 4) {
                    p1 = 4 + i
                    p2 = 4 + (i + 1 and 3)
                    if (0 == boxPointCull[p1].getVal() and boxPointCull[p2].getVal()) {
                        if (frustum.ClipLine(localPoints1, localPoints2, p1, p2, start, end, startClip, endClip)) {
                            AddLocalLineToProjectionBoundsSetCull(start, end, pointCull[1], projectionBounds)
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                start,
                                pointCull[0].getVal(),
                                startClip.getVal(),
                                projectionBounds
                            )
                            AddLocalCapsToProjectionBounds(
                                clipPoints,
                                4,
                                clipPointCull,
                                4,
                                end,
                                pointCull[1].getVal(),
                                endClip.getVal(),
                                projectionBounds
                            )
                            outside = outside or (pointCull[0].getVal() or pointCull[1].getVal())
                        }
                    }
                    i++
                }
            }

            // if the clipped frustum extends beyond two or more boundaries of this frustum
            if (outside != 1 && outside != 2 && outside != 4 && outside != 8) {

                // transform this frustum into the space of the other frustum
                transpose = idMat3(frustum.axis)
                transpose.TransposeSelf()
                localOrigin1.oSet(origin.oMinus(frustum.origin).oMultiply(transpose))
                localAxis1 = axis.oMultiply(transpose)
                localAxis1.oGet(0).oMulSet(dFar)
                localAxis1.oGet(1).oMulSet(dLeft)
                localAxis1.oGet(2).oMulSet(dUp)

                // transform this frustum into the space of the clip bounds
                transpose = idMat3(clipBox.GetAxis())
                transpose.TransposeSelf()
                localOrigin2.oSet(origin.oMinus(clipBox.GetCenter()).oMultiply(transpose))
                localAxis2 = axis.oMultiply(transpose)
                localAxis2.oGet(0).oMulSet(dFar)
                localAxis2.oGet(1).oMulSet(dLeft)
                localAxis2.oGet(2).oMulSet(dUp)
                clipBounds.oSet(0, clipBox.GetExtents().oNegative())
                clipBounds.oSet(1, clipBox.GetExtents())

                // test the outer edges of this frustum for intersection with both the other frustum and the clip bounds
                if (outside and 2 != 0 && outside and 8 != 0) {
                    frustum.LocalRayIntersection(
                        localOrigin1,
                        localAxis1.oGet(0).oMinus(localAxis1.oGet(1).oMinus(localAxis1.oGet(2))),
                        s1,
                        s2
                    )
                    if (s1.getVal() <= s2.getVal() && s1.getVal() >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2.oGet(0).oMinus(localAxis2.oGet(1).oMinus(localAxis2.oGet(2))),
                            t1,
                            t2
                        )
                        if (t1.getVal() <= t2.getVal() && t2.getVal() > s1.getVal() && t1.getVal() < s2.getVal()) {
                            projectionBounds.AddPoint(idVec3(s1.getVal() * dFar, -1.0f, -1.0f))
                            projectionBounds.AddPoint(idVec3(s2.getVal() * dFar, -1.0f, -1.0f))
                        }
                    }
                }
                if (outside and 2 != 0 && outside and 4 != 0) {
                    frustum.LocalRayIntersection(
                        localOrigin1,
                        localAxis1.oGet(0).oMinus(localAxis1.oGet(1).oPlus(localAxis1.oGet(2))),
                        s1,
                        s2
                    )
                    if (s1.getVal() <= s2.getVal() && s1.getVal() >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2.oGet(0).oMinus(localAxis2.oGet(1).oPlus(localAxis2.oGet(2))),
                            t1,
                            t2
                        )
                        if (t1.getVal() <= t2.getVal() && t2.getVal() > s1.getVal() && t1.getVal() < s2.getVal()) {
                            projectionBounds.AddPoint(idVec3(s1.getVal() * dFar, -1.0f, 1.0f))
                            projectionBounds.AddPoint(idVec3(s2.getVal() * dFar, -1.0f, 1.0f))
                        }
                    }
                }
                if (outside and 1 != 0 && outside and 8 != 0) {
                    frustum.LocalRayIntersection(
                        localOrigin1,
                        localAxis1.oGet(0).oPlus(localAxis1.oGet(1).oMinus(localAxis1.oGet(2))),
                        s1,
                        s2
                    )
                    if (s1.getVal() <= s2.getVal() && s1.getVal() >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2.oGet(0).oPlus(localAxis2.oGet(1).oMinus(localAxis2.oGet(2))),
                            t1,
                            t2
                        )
                        if (t1.getVal() <= t2.getVal() && t2.getVal() > s1.getVal() && t1.getVal() < s2.getVal()) {
                            projectionBounds.AddPoint(idVec3(s1.getVal() * dFar, 1.0f, -1.0f))
                            projectionBounds.AddPoint(idVec3(s2.getVal() * dFar, 1.0f, -1.0f))
                        }
                    }
                }
                if (outside and 1 != 0 && outside and 2 != 0) {
                    frustum.LocalRayIntersection(
                        localOrigin1,
                        localAxis1.oGet(0).oPlus(localAxis1.oGet(1).oPlus(localAxis1.oGet(2))),
                        s1,
                        s2
                    )
                    if (s1.getVal() <= s2.getVal() && s1.getVal() >= 0.0f) {
                        BoundsRayIntersection(
                            clipBounds,
                            localOrigin2,
                            localAxis2.oGet(0).oPlus(localAxis2.oGet(1).oPlus(localAxis2.oGet(2))),
                            t1,
                            t2
                        )
                        if (t1.getVal() <= t2.getVal() && t2.getVal() > s1.getVal() && t1.getVal() < s2.getVal()) {
                            projectionBounds.AddPoint(idVec3(s1.getVal() * dFar, 1.0f, 1.0f))
                            projectionBounds.AddPoint(idVec3(s2.getVal() * dFar, 1.0f, 1.0f))
                        }
                    }
                }
            }
            return true
        }

        /*
         ============
         idFrustum::CullLocalBox

         Tests if any of the planes of the frustum can be used as a separating plane.

         3 muls best case
         25 muls worst case
         ============
         */
        private fun CullLocalBox(localOrigin: idVec3?, extents: idVec3?, localAxis: idMat3?): Boolean {
            var d1: Float
            var d2: Float
            val testOrigin = idVec3()
            val testAxis: idMat3

            // near plane
            d1 = dNear - localOrigin.x
            d2 = (Math.abs(extents.oGet(0) * localAxis.oGet(0).oGet(0))
                    + Math.abs(extents.oGet(1) * localAxis.oGet(1).oGet(0))
                    + Math.abs(extents.oGet(2) * localAxis.oGet(2).oGet(0)))
            if (d1 - d2 > 0.0f) {
                return true
            }

            // far plane
            d1 = localOrigin.x - dFar
            if (d1 - d2 > 0.0f) {
                return true
            }
            testOrigin.oSet(localOrigin)
            testAxis = idMat3(localAxis)
            if (testOrigin.y < 0.0f) {
                testOrigin.y = -testOrigin.y
                testAxis.oGet(0).oSet(1, -testAxis.oGet(0).oGet(1))
                testAxis.oGet(1).oSet(1, -testAxis.oGet(1).oGet(1))
                testAxis.oGet(0).oSet(1, -testAxis.oGet(2).oGet(1))
            }

            // test left/right planes
            d1 = dFar * testOrigin.y - dLeft * testOrigin.x
            d2 = (Math.abs(extents.oGet(0) * (dFar * testAxis.oGet(0).oGet(1) - dLeft * testAxis.oGet(0).oGet(0)))
                    + Math.abs(extents.oGet(1) * (dFar * testAxis.oGet(1).oGet(1) - dLeft * testAxis.oGet(1).oGet(0)))
                    + Math.abs(extents.oGet(2) * (dFar * testAxis.oGet(2).oGet(1) - dLeft * testAxis.oGet(2).oGet(0))))
            if (d1 - d2 > 0.0f) {
                return true
            }
            if (testOrigin.z < 0.0f) {
                testOrigin.z = -testOrigin.z
                testAxis.oGet(0).oSet(2, -testAxis.oGet(0).oGet(2))
                testAxis.oGet(1).oSet(2, -testAxis.oGet(1).oGet(2))
                testAxis.oGet(2).oSet(2, -testAxis.oGet(2).oGet(2))
            }

            // test up/down planes
            d1 = dFar * testOrigin.z - dUp * testOrigin.x
            d2 = (Math.abs(extents.oGet(0) * (dFar * testAxis.oGet(0).oGet(2) - dUp * testAxis.oGet(0).oGet(0)))
                    + Math.abs(extents.oGet(1) * (dFar * testAxis.oGet(1).oGet(2) - dUp * testAxis.oGet(1).oGet(0)))
                    + Math.abs(extents.oGet(2) * (dFar * testAxis.oGet(2).oGet(2) - dUp * testAxis.oGet(2).oGet(0))))
            return d1 - d2 > 0.0f
        }

        /*
         ============
         idFrustum::CullLocalFrustum

         Tests if any of the planes of this frustum can be used as a separating plane.

         0 muls best case
         30 muls worst case
         ============
         */
        private fun CullLocalFrustum(
            localFrustum: idFrustum?,
            indexPoints: Array<idVec3?>?,
            cornerVecs: Array<idVec3?>?
        ): Boolean {
            var index: Int
            var dx: Float
            var dy: Float
            var dz: Float
            val leftScale: Float
            val upScale: Float

            // test near plane
            dy = -localFrustum.axis.oGet(1).x
            dz = -localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).x < dNear) {
                return true
            }

            // test far plane
            dy = localFrustum.axis.oGet(1).x
            dz = localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).x > dFar) {
                return true
            }
            leftScale = dLeft * invFar

            // test left plane
            dy = dFar * localFrustum.axis.oGet(1).y - dLeft * localFrustum.axis.oGet(1).x
            dz = dFar * localFrustum.axis.oGet(2).y - dLeft * localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = dFar * cornerVecs.get(index).y - dLeft * cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).y > indexPoints.get(index).x * leftScale) {
                return true
            }

            // test right plane
            dy = -dFar * localFrustum.axis.oGet(1).y - dLeft * localFrustum.axis.oGet(1).x
            dz = -dFar * localFrustum.axis.oGet(2).y - dLeft * localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -dFar * cornerVecs.get(index).y - dLeft * cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).y < -indexPoints.get(index).x * leftScale) {
                return true
            }
            upScale = dUp * invFar

            // test up plane
            dy = dFar * localFrustum.axis.oGet(1).z - dUp * localFrustum.axis.oGet(1).x
            dz = dFar * localFrustum.axis.oGet(2).z - dUp * localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = dFar * cornerVecs.get(index).z - dUp * cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).z > indexPoints.get(index).x * upScale) {
                return true
            }

            // test down plane
            dy = -dFar * localFrustum.axis.oGet(1).z - dUp * localFrustum.axis.oGet(1).x
            dz = -dFar * localFrustum.axis.oGet(2).z - dUp * localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -dFar * cornerVecs.get(index).z - dUp * cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            return indexPoints.get(index).z < -indexPoints.get(index).x * upScale
        }

        private fun CullLocalWinding(points: Array<idVec3?>?, numPoints: Int, pointCull: IntArray?): Boolean {
            var i: Int
            var pCull: Int
            var culled: Int
            val leftScale: Float
            val upScale: Float
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            culled = -1
            i = 0
            while (i < numPoints) {
                val p = points.get(i)
                pCull = 0
                if (p.x < dNear) {
                    pCull = 1
                } else if (p.x > dFar) {
                    pCull = 2
                }
                if (Math.abs(p.y) > p.x * leftScale) {
                    pCull = pCull or (4 shl Math_h.FLOATSIGNBITSET(p.y))
                }
                if (Math.abs(p.z) > p.x * upScale) {
                    pCull = pCull or (16 shl Math_h.FLOATSIGNBITSET(p.z))
                }
                culled = culled and pCull
                pointCull.get(i) = pCull
                i++
            }
            return culled != 0
        }

        /*
         ============
         idFrustum::BoundsCullLocalFrustum

         Tests if any of the bounding box planes can be used as a separating plane.
         ============
         */
        private fun BoundsCullLocalFrustum(
            bounds: idBounds?,
            localFrustum: idFrustum?,
            indexPoints: Array<idVec3?>?,
            cornerVecs: Array<idVec3?>?
        ): Boolean {
            var index: Int
            var dx: Float
            var dy: Float
            var dz: Float
            dy = -localFrustum.axis.oGet(1).x
            dz = -localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).x < bounds.oGet(0).x) {
                return true
            }
            dy = localFrustum.axis.oGet(1).x
            dz = localFrustum.axis.oGet(2).x
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = cornerVecs.get(index).x
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).x > bounds.oGet(1).x) {
                return true
            }
            dy = -localFrustum.axis.oGet(1).y
            dz = -localFrustum.axis.oGet(2).y
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -cornerVecs.get(index).y
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).y < bounds.oGet(0).y) {
                return true
            }
            dy = localFrustum.axis.oGet(1).y
            dz = localFrustum.axis.oGet(2).y
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = cornerVecs.get(index).y
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).y > bounds.oGet(1).y) {
                return true
            }
            dy = -localFrustum.axis.oGet(1).z
            dz = -localFrustum.axis.oGet(2).z
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = -cornerVecs.get(index).z
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            if (indexPoints.get(index).z < bounds.oGet(0).z) {
                return true
            }
            dy = localFrustum.axis.oGet(1).z
            dz = localFrustum.axis.oGet(2).z
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = cornerVecs.get(index).z
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            return indexPoints.get(index).z > bounds.oGet(1).z
        }

        /*
         ============
         idFrustum::LocalLineIntersection

         7 divs
         30 muls
         ============
         */
        private fun LocalLineIntersection(start: idVec3?, end: idVec3?): Boolean {
            val dir = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var startInside = 1
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.oSet(end.oMinus(start))

            // test near plane
            if (dNear > 0.0f) {
                d1 = dNear - start.x
                startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
                if (Math_h.FLOATNOTZERO(d1)) {
                    d2 = dNear - end.x
                    if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                        f = d1 / (d1 - d2)
                        if (Math.abs(start.y + f * dir.y) <= dNear * leftScale) {
                            if (Math.abs(start.z + f * dir.z) <= dNear * upScale) {
                                return true
                            }
                        }
                    }
                }
            }

            // test far plane
            d1 = start.x - dFar
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            if (Math_h.FLOATNOTZERO(d1)) {
                d2 = end.x - dFar
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    if (Math.abs(start.y + f * dir.y) <= dFar * leftScale) {
                        if (Math.abs(start.z + f * dir.z) <= dFar * upScale) {
                            return true
                        }
                    }
                }
            }
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            if (Math_h.FLOATNOTZERO(d1)) {
                d2 = fend - lend
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x >= dNear && x <= dFar) {
                        if (Math.abs(start.z + f * dir.z) <= x * upScale) {
                            return true
                        }
                    }
                }
            }

            // test right plane
            d1 = -fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            if (Math_h.FLOATNOTZERO(d1)) {
                d2 = -fend - lend
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x >= dNear && x <= dFar) {
                        if (Math.abs(start.z + f * dir.z) <= x * upScale) {
                            return true
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            if (Math_h.FLOATNOTZERO(d1)) {
                d2 = fend - lend
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x >= dNear && x <= dFar) {
                        if (Math.abs(start.y + f * dir.y) <= x * leftScale) {
                            return true
                        }
                    }
                }
            }

            // test down plane
            d1 = -fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            if (Math_h.FLOATNOTZERO(d1)) {
                d2 = -fend - lend
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = start.x + f * dir.x
                    if (x >= dNear && x <= dFar) {
                        if (Math.abs(start.y + f * dir.y) <= x * leftScale) {
                            return true
                        }
                    }
                }
            }
            return startInside != 0
        }

        /*
         ============
         idFrustum::LocalRayIntersection

         Returns true if the ray starts inside the frustum.
         If there was an intersection scale1 <= scale2
         ============
         */
        private fun LocalRayIntersection(start: idVec3?, dir: idVec3?, scale1: CFloat?, scale2: CFloat?): Boolean {
            val end = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var startInside = 1
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            end.oSet(start.oPlus(dir))
            scale1.setVal(idMath.INFINITY)
            scale2.setVal(-idMath.INFINITY)

            // test near plane
            if (dNear > 0.0f) {
                d1 = dNear - start.x
                startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
                d2 = dNear - end.x
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    if (Math.abs(start.y + f * dir.y) <= dNear * leftScale) {
                        if (Math.abs(start.z + f * dir.z) <= dNear * upScale) {
                            if (f < scale1.getVal()) {
                                scale1.setVal(f)
                            }
                            if (f > scale2.getVal()) {
                                scale2.setVal(f)
                            }
                        }
                    }
                }
            }

            // test far plane
            d1 = start.x - dFar
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            d2 = end.x - dFar
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                if (Math.abs(start.y + f * dir.y) <= dFar * leftScale) {
                    if (Math.abs(start.z + f * dir.z) <= dFar * upScale) {
                        if (f < scale1.getVal()) {
                            scale1.setVal(f)
                        }
                        if (f > scale2.getVal()) {
                            scale2.setVal(f)
                        }
                    }
                }
            }
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            d2 = fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (Math.abs(start.z + f * dir.z) <= x * upScale) {
                        if (f < scale1.getVal()) {
                            scale1.setVal(f)
                        }
                        if (f > scale2.getVal()) {
                            scale2.setVal(f)
                        }
                    }
                }
            }

            // test right plane
            d1 = -fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            d2 = -fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (Math.abs(start.z + f * dir.z) <= x * upScale) {
                        if (f < scale1.getVal()) {
                            scale1.setVal(f)
                        }
                        if (f > scale2.getVal()) {
                            scale2.setVal(f)
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            d2 = fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (Math.abs(start.y + f * dir.y) <= x * leftScale) {
                        if (f < scale1.getVal()) {
                            scale1.setVal(f)
                        }
                        if (f > scale2.getVal()) {
                            scale2.setVal(f)
                        }
                    }
                }
            }

            // test down plane
            d1 = -fstart - lstart
            startInside = startInside and Math_h.FLOATSIGNBITSET(d1)
            d2 = -fend - lend
            if (d1 != d2) {
                f = d1 / (d1 - d2)
                x = start.x + f * dir.x
                if (x >= dNear && x <= dFar) {
                    if (Math.abs(start.y + f * dir.y) <= x * leftScale) {
                        if (f < scale1.getVal()) {
                            scale1.setVal(f)
                        }
                        if (f > scale2.getVal()) {
                            scale2.setVal(f)
                        }
                    }
                }
            }
            return startInside != 0
        }

        private fun LocalFrustumIntersectsFrustum(points: Array<idVec3?>?, testFirstSide: Boolean): Boolean {
            var i: Int

            // test if any edges of the other frustum intersect this frustum
            i = 0
            while (i < 4) {
                if (LocalLineIntersection(points.get(i), points.get(4 + i))) {
                    return true
                }
                i++
            }
            if (testFirstSide) {
                i = 0
                while (i < 4) {
                    if (LocalLineIntersection(points.get(i), points.get(i + 1 and 3))) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (LocalLineIntersection(points.get(4 + i), points.get(4 + (i + 1 and 3)))) {
                    return true
                }
                i++
            }
            return false
        }

        private fun LocalFrustumIntersectsBounds(points: Array<idVec3?>?, bounds: idBounds?): Boolean {
            var i: Int

            // test if any edges of the other frustum intersect this frustum
            i = 0
            while (i < 4) {
                if (bounds.LineIntersection(points.get(i), points.get(4 + i))) {
                    return true
                }
                i++
            }
            if (dNear > 0.0f) {
                i = 0
                while (i < 4) {
                    if (bounds.LineIntersection(points.get(i), points.get(i + 1 and 3))) {
                        return true
                    }
                    i++
                }
            }
            i = 0
            while (i < 4) {
                if (bounds.LineIntersection(points.get(4 + i), points.get(4 + (i + 1 and 3)))) {
                    return true
                }
                i++
            }
            return false
        }

        private fun ToClippedPoints(fractions: Array<CFloat?>?, points: Array<idVec3?>?) {
            val scaled = idMat3()
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dNear)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft * dNear * invFar))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp * dNear * invFar))
            points.get(0).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            points.get(1).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            points.get(2).oSet(points.get(1).oMinus(scaled.oGet(2)))
            points.get(3).oSet(points.get(0).oMinus(scaled.oGet(2)))
            points.get(0).oPluSet(scaled.oGet(2))
            points.get(1).oPluSet(scaled.oGet(2))
            scaled.oSet(0, axis.oGet(0).oMultiply(dFar))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft))
            scaled.oSet(2, axis.oGet(2).oMulSet(dUp))
            points.get(4).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            points.get(5).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            points.get(6).oSet(points.get(5).oMinus(scaled.oGet(2)))
            points.get(7).oSet(points.get(4).oMinus(scaled.oGet(2)))
            points.get(4).oPluSet(scaled.oGet(2))
            points.get(5).oPluSet(scaled.oGet(2))
            points.get(4).oSet(origin.oPlus(points.get(4).oMultiply(fractions.get(0).getVal())))
            points.get(5).oSet(origin.oPlus(points.get(5).oMultiply(fractions.get(1).getVal())))
            points.get(6).oSet(origin.oPlus(points.get(6).oMultiply(fractions.get(2).getVal())))
            points.get(7).oSet(origin.oPlus(points.get(7).oMultiply(fractions.get(3).getVal())))
        }

        private fun ToIndexPoints(indexPoints: Array<idVec3?>?) {
            val scaled = idMat3()
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dNear)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft * dNear * invFar))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp * dNear * invFar))
            indexPoints.get(0).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            indexPoints.get(2).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            indexPoints.get(1).oSet(indexPoints.get(0).oPlus(scaled.oGet(2)))
            indexPoints.get(3).oSet(indexPoints.get(2).oPlus(scaled.oGet(2)))
            indexPoints.get(0).oMinSet(scaled.oGet(2))
            indexPoints.get(2).oMinSet(scaled.oGet(2))
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dFar)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft))
            scaled.oSet(2, axis.oGet(2).oMulSet(dUp))
            indexPoints.get(4).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            indexPoints.get(6).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            indexPoints.get(5).oSet(indexPoints.get(4).oPlus(scaled.oGet(2)))
            indexPoints.get(7).oSet(indexPoints.get(6).oPlus(scaled.oGet(2)))
            indexPoints.get(4).oMinSet(scaled.oGet(2))
            indexPoints.get(6).oMinSet(scaled.oGet(2))
        }

        /*
         ============
         idFrustum::ToIndexPointsAndCornerVecs

         22 muls
         ============
         */
        private fun ToIndexPointsAndCornerVecs(indexPoints: Array<idVec3?>?, cornerVecs: Array<idVec3?>?) {
            val scaled = idMat3()
            scaled.oSet(0, origin.oPlus(axis.oGet(0).oMultiply(dNear)))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft * dNear * invFar))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp * dNear * invFar))
            indexPoints.get(0).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            indexPoints.get(2).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            indexPoints.get(1).oSet(indexPoints.get(0).oPlus(scaled.oGet(2)))
            indexPoints.get(3).oSet(indexPoints.get(2).oPlus(scaled.oGet(2)))
            indexPoints.get(0).oMinSet(scaled.oGet(2))
            indexPoints.get(2).oMinSet(scaled.oGet(2))
            scaled.oSet(0, axis.oGet(0).oMultiply(dFar))
            scaled.oSet(1, axis.oGet(1).oMultiply(dLeft))
            scaled.oSet(2, axis.oGet(2).oMultiply(dUp))
            cornerVecs.get(0).oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            cornerVecs.get(2).oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            cornerVecs.get(1).oSet(cornerVecs.get(0).oPlus(scaled.oGet(2)))
            cornerVecs.get(3).oSet(cornerVecs.get(2).oPlus(scaled.oGet(2)))
            cornerVecs.get(0).oMinSet(scaled.oGet(2))
            cornerVecs.get(2).oMinSet(scaled.oGet(2))
            indexPoints.get(4).oSet(cornerVecs.get(0).oPlus(origin))
            indexPoints.get(5).oSet(cornerVecs.get(1).oPlus(origin))
            indexPoints.get(6).oSet(cornerVecs.get(2).oPlus(origin))
            indexPoints.get(7).oSet(cornerVecs.get(3).oPlus(origin))
        }

        /*
         ============
         idFrustum::AxisProjection

         18 muls
         ============
         */
        private fun AxisProjection(
            indexPoints: Array<idVec3?>?,
            cornerVecs: Array<idVec3?>?,
            dir: idVec3?,
            min: CFloat?,
            max: CFloat?
        ) {
            var dx: Float
            val dy: Float
            val dz: Float
            var index: Int
            dy = dir.x * axis.oGet(1).x + dir.y * axis.oGet(1).y + dir.z * axis.oGet(1).z
            dz = dir.x * axis.oGet(2).x + dir.y * axis.oGet(2).y + dir.z * axis.oGet(2).z
            index = Math_h.FLOATSIGNBITSET(dy) shl 1 or Math_h.FLOATSIGNBITSET(dz)
            dx = dir.x * cornerVecs.get(index).x + dir.y * cornerVecs.get(index).y + dir.z * cornerVecs.get(index).z
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            min.setVal(indexPoints.get(index).oMultiply(dir))
            index = index.inv() and 3
            dx = -dir.x * cornerVecs.get(index).x - dir.y * cornerVecs.get(index).y - dir.z * cornerVecs.get(index).z
            index = index or (Math_h.FLOATSIGNBITSET(dx) shl 2)
            max.setVal(indexPoints.get(index).oMultiply(dir))
        }

        private fun AddLocalLineToProjectionBoundsSetCull(
            start: idVec3?,
            end: idVec3?,
            cull: CInt?,
            bounds: idBounds?
        ) {
            val cull2 = CInt()
            AddLocalLineToProjectionBoundsSetCull(start, end, cull, cull2, bounds)
            cull.setVal(cull2.getVal())
        }

        private fun AddLocalLineToProjectionBoundsSetCull(
            start: idVec3?,
            end: idVec3?,
            startCull: CInt?,
            endCull: CInt?,
            bounds: idBounds?
        ) {
            val dir = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            val leftScale: Float
            val upScale: Float
            var cull1: Int
            var cull2: Int

//#ifdef FRUSTUM_DEBUG
//	static idCVar r_showInteractionScissors( "r_showInteractionScissors", "0", CVAR_RENDERER | CVAR_INTEGER, "", 0, 2, idCmdSystem::ArgCompletion_Integer<0,2> );
//	if ( r_showInteractionScissors.GetInteger() > 1 ) {
//		session->rw->DebugLine( colorGreen, origin + start * axis, origin + end * axis );
//	}
//#endif
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.oSet(end.oMinus(start))
            fstart = dFar * start.y
            fend = dFar * end.y
            lstart = dLeft * start.x
            lend = dLeft * end.x

            // test left plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            cull1 = Math_h.FLOATSIGNBITSET(d1)
            cull2 = Math_h.FLOATSIGNBITSET(d2)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.z = start.z + f * dir.z
                        if (Math.abs(p.z) <= p.x * upScale) {
                            p.y = 1.0f
                            p.z = p.z * dFar / (p.x * dUp)
                            bounds.AddPoint(p)
                        }
                    }
                }
            }

            // test right plane
            d1 = fstart + lstart
            d2 = fend + lend
            cull1 = cull1 or (Math_h.FLOATSIGNBITSET(d1) shl 1)
            cull2 = cull2 or (Math_h.FLOATSIGNBITSET(d2) shl 1)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.z = start.z + f * dir.z
                        if (Math.abs(p.z) <= p.x * upScale) {
                            p.y = -1.0f
                            p.z = p.z * dFar / (p.x * dUp)
                            bounds.AddPoint(p)
                        }
                    }
                }
            }
            fstart = dFar * start.z
            fend = dFar * end.z
            lstart = dUp * start.x
            lend = dUp * end.x

            // test up plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            cull1 = cull1 or (Math_h.FLOATSIGNBITSET(d1) shl 2)
            cull2 = cull2 or (Math_h.FLOATSIGNBITSET(d2) shl 2)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.y = start.y + f * dir.y
                        if (Math.abs(p.y) <= p.x * leftScale) {
                            p.y = p.y * dFar / (p.x * dLeft)
                            p.z = 1.0f
                            bounds.AddPoint(p)
                        }
                    }
                }
            }

            // test down plane
            d1 = fstart + lstart
            d2 = fend + lend
            cull1 = cull1 or (Math_h.FLOATSIGNBITSET(d1) shl 3)
            cull2 = cull2 or (Math_h.FLOATSIGNBITSET(d2) shl 3)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (p.x > 0.0f) {
                        p.y = start.y + f * dir.y
                        if (Math.abs(p.y) <= p.x * leftScale) {
                            p.y = p.y * dFar / (p.x * dLeft)
                            p.z = -1.0f
                            bounds.AddPoint(p)
                        }
                    }
                }
            }
            if (cull1 == 0 && start.x > 0.0f) {
                // add start point to projection bounds
                p.x = start.x
                p.y = start.y * dFar / (start.x * dLeft)
                p.z = start.z * dFar / (start.x * dUp)
                bounds.AddPoint(p)
            }
            if (cull2 == 0 && end.x > 0.0f) {
                // add end point to projection bounds
                p.x = end.x
                p.y = end.y * dFar / (end.x * dLeft)
                p.z = end.z * dFar / (end.x * dUp)
                bounds.AddPoint(p)
            }
            if (start.x < bounds.oGet(0).x) {
                bounds.oGet(0).x = if (start.x < 0.0f) 0.0f else start.x
            }
            if (end.x < bounds.oGet(0).x) {
                bounds.oGet(0).x = if (end.x < 0.0f) 0.0f else end.x
            }
            startCull.setVal(cull1)
            endCull.setVal(cull2)
        }

        private fun AddLocalLineToProjectionBoundsUseCull(
            start: idVec3?,
            end: idVec3?,
            startCull: Int,
            endCull: Int,
            bounds: idBounds?
        ) {
            val dir = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            val leftScale: Float
            val upScale: Float
            val clip: Int
            clip = startCull xor endCull
            if (0 == clip) {
                return
            }

//#ifdef FRUSTUM_DEBUG
//	static idCVar r_showInteractionScissors( "r_showInteractionScissors", "0", CVAR_RENDERER | CVAR_INTEGER, "", 0, 2, idCmdSystem.ArgCompletion_Integer<0,2> );
//	if ( r_showInteractionScissors.GetInteger() > 1 ) {
//		session->rw->DebugLine( colorGreen, origin + start * axis, origin + end * axis );
//	}
//#endif
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            dir.oSet(end.oMinus(start))
            if (clip and (1 or 2) != 0) {
                fstart = dFar * start.y
                fend = dFar * end.y
                lstart = dLeft * start.x
                lend = dLeft * end.x
                if (clip and 1 != 0) {
                    // test left plane
                    d1 = -fstart + lstart
                    d2 = -fend + lend
                    if (Math_h.FLOATNOTZERO(d1)) {
                        if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.z = start.z + f * dir.z
                                if (Math.abs(p.z) <= p.x * upScale) {
                                    p.y = 1.0f
                                    p.z = p.z * dFar / (p.x * dUp)
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
                if (clip and 2 != 0) {
                    // test right plane
                    d1 = fstart + lstart
                    d2 = fend + lend
                    if (Math_h.FLOATNOTZERO(d1)) {
                        if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.z = start.z + f * dir.z
                                if (Math.abs(p.z) <= p.x * upScale) {
                                    p.y = -1.0f
                                    p.z = p.z * dFar / (p.x * dUp)
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
            }
            if (clip and (4 or 8) != 0) {
                fstart = dFar * start.z
                fend = dFar * end.z
                lstart = dUp * start.x
                lend = dUp * end.x
                if (clip and 4 != 0) {
                    // test up plane
                    d1 = -fstart + lstart
                    d2 = -fend + lend
                    if (Math_h.FLOATNOTZERO(d1)) {
                        if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.y = start.y + f * dir.y
                                if (Math.abs(p.y) <= p.x * leftScale) {
                                    p.y = p.y * dFar / (p.x * dLeft)
                                    p.z = 1.0f
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
                if (clip and 8 != 0) {
                    // test down plane
                    d1 = fstart + lstart
                    d2 = fend + lend
                    if (Math_h.FLOATNOTZERO(d1)) {
                        if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                            f = d1 / (d1 - d2)
                            p.x = start.x + f * dir.x
                            if (p.x > 0.0f) {
                                p.y = start.y + f * dir.y
                                if (Math.abs(p.y) <= p.x * leftScale) {
                                    p.y = p.y * dFar / (p.x * dLeft)
                                    p.z = -1.0f
                                    bounds.AddPoint(p)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun AddLocalCapsToProjectionBounds(
            endPoints: Array<idVec3?>?,
            endPointsOffset: Int,
            endPointCull: Array<CInt?>?,
            endPointCullOffset: Int,
            point: idVec3?,
            pointCull: Int,
            pointClip: Int,
            projectionBounds: idBounds?
        ): Boolean {
            val p: IntArray?
            if (pointClip < 0) {
                return false
            }
            p = capPointIndex.get(pointClip)
            AddLocalLineToProjectionBoundsUseCull(
                endPoints.get(endPointsOffset + p.get(0)),
                point,
                endPointCull.get(endPointCullOffset + p.get(0)).getVal(),
                pointCull,
                projectionBounds
            )
            AddLocalLineToProjectionBoundsUseCull(
                endPoints.get(endPointsOffset + p.get(1)),
                point,
                endPointCull.get(endPointCullOffset + p.get(1)).getVal(),
                pointCull,
                projectionBounds
            )
            return true
        }

        private fun AddLocalCapsToProjectionBounds(
            endPoints: Array<idVec3?>?,
            endPointCull: Array<CInt?>?,
            point: idVec3?,
            pointCull: Int,
            pointClip: Int,
            projectionBounds: idBounds?
        ): Boolean {
            return AddLocalCapsToProjectionBounds(
                endPoints,
                0,
                endPointCull,
                0,
                point,
                pointCull,
                pointClip,
                projectionBounds
            )
        }

        /*
         ============
         idFrustum::BoundsRayIntersection

         Returns true if the ray starts inside the bounds.
         If there was an intersection scale1 <= scale2
         ============
         */
        private fun BoundsRayIntersection(
            bounds: idBounds?,
            start: idVec3?,
            dir: idVec3?,
            scale1: CFloat?,
            scale2: CFloat?
        ): Boolean {
            val end = idVec3()
            val p = idVec3()
            var d1: Float
            var d2: Float
            var f: Float
            var i: Int
            var startInside = 1
            scale1.setVal(idMath.INFINITY)
            scale2.setVal(-idMath.INFINITY)
            end.oSet(start.oPlus(dir))
            i = 0
            while (i < 2) {
                d1 = start.x - bounds.oGet(i).x
                startInside = startInside and (Math_h.FLOATSIGNBITSET(d1) xor i)
                d2 = end.x - bounds.oGet(i).x
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.y = start.y + f * dir.y
                    if (bounds.oGet(0).y <= p.y && p.y <= bounds.oGet(1).y) {
                        p.z = start.z + f * dir.z
                        if (bounds.oGet(0).z <= p.z && p.z <= bounds.oGet(1).z) {
                            if (f < scale1.getVal()) {
                                scale1.setVal(f)
                            }
                            if (f > scale2.getVal()) {
                                scale2.setVal(f)
                            }
                        }
                    }
                }
                d1 = start.y - bounds.oGet(i).y
                startInside = startInside and (Math_h.FLOATSIGNBITSET(d1) xor i)
                d2 = end.y - bounds.oGet(i).y
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (bounds.oGet(0).x <= p.x && p.x <= bounds.oGet(1).x) {
                        p.z = start.z + f * dir.z
                        if (bounds.oGet(0).z <= p.z && p.z <= bounds.oGet(1).z) {
                            if (f < scale1.getVal()) {
                                scale1.setVal(f)
                            }
                            if (f > scale2.getVal()) {
                                scale2.setVal(f)
                            }
                        }
                    }
                }
                d1 = start.z - bounds.oGet(i).z
                startInside = startInside and (Math_h.FLOATSIGNBITSET(d1) xor i)
                d2 = end.z - bounds.oGet(i).z
                if (d1 != d2) {
                    f = d1 / (d1 - d2)
                    p.x = start.x + f * dir.x
                    if (bounds.oGet(0).x <= p.x && p.x <= bounds.oGet(1).x) {
                        p.y = start.y + f * dir.y
                        if (bounds.oGet(0).y <= p.y && p.y <= bounds.oGet(1).y) {
                            if (f < scale1.getVal()) {
                                scale1.setVal(f)
                            }
                            if (f > scale2.getVal()) {
                                scale2.setVal(f)
                            }
                        }
                    }
                }
                i++
            }
            return startInside != 0
        }

        /*
         ============
         idFrustum::ClipFrustumToBox

         Clips the frustum far extents to the box.
         ============
         */
        private fun ClipFrustumToBox(box: idBox?, clipFractions: Array<CFloat?>?, clipPlanes: Array<CInt?>?) {
            var i: Int
            var index: Int
            var f: Float
            val minf: Float
            val scaled = idMat3()
            val localAxis: idMat3?
            val transpose: idMat3?
            val localOrigin = idVec3()
            val cornerVecs: Array<idVec3?> = idVec3.Companion.generateArray(4)
            val bounds = idBounds()
            transpose = box.GetAxis()
            transpose.TransposeSelf()
            localOrigin.oSet(origin.oMinus(box.GetCenter()).oMultiply(transpose))
            localAxis = axis.oMultiply(transpose)
            scaled.oSet(0, localAxis.oGet(0).oMultiply(dFar))
            scaled.oSet(1, localAxis.oGet(1).oMultiply(dLeft))
            scaled.oSet(2, localAxis.oGet(2).oMultiply(dUp))
            cornerVecs[0].oSet(scaled.oGet(0).oPlus(scaled.oGet(1)))
            cornerVecs[1].oSet(scaled.oGet(0).oMinus(scaled.oGet(1)))
            cornerVecs[2].oSet(cornerVecs[1].oMinus(scaled.oGet(2)))
            cornerVecs[3].oSet(cornerVecs[0].oMinus(scaled.oGet(2)))
            cornerVecs[0].oPluSet(scaled.oGet(2))
            cornerVecs[1].oPluSet(scaled.oGet(2))
            bounds.oSet(0, box.GetExtents().oNegative())
            bounds.oSet(1, box.GetExtents())
            minf = (dNear + 1.0f) * invFar
            i = 0
            while (i < 4) {
                index = Math_h.FLOATSIGNBITNOTSET(cornerVecs[i].x)
                f = (bounds.oGet(index).x - localOrigin.x) / cornerVecs[i].x
                clipFractions.get(i).setVal(f)
                clipPlanes.get(i).setVal(1 shl index)
                index = Math_h.FLOATSIGNBITNOTSET(cornerVecs[i].y)
                f = (bounds.oGet(index).y - localOrigin.y) / cornerVecs[i].y
                if (f < clipFractions.get(i).getVal()) {
                    clipFractions.get(i).setVal(f)
                    clipPlanes.get(i).setVal(4 shl index)
                }
                index = Math_h.FLOATSIGNBITNOTSET(cornerVecs[i].z)
                f = (bounds.oGet(index).z - localOrigin.z) / cornerVecs[i].z
                if (f < clipFractions.get(i).getVal()) {
                    clipFractions.get(i).setVal(f)
                    clipPlanes.get(i).setVal(16 shl index)
                }

                // make sure the frustum is not clipped between the frustum origin and the near plane
                if (clipFractions.get(i).getVal() < minf) {
                    clipFractions.get(i).setVal(minf)
                }
                i++
            }
        }

        /*
         ============
         idFrustum::ClipLine

         Returns true if part of the line is inside the frustum.
         Does not clip to the near and far plane.
         ============
         */
        private fun ClipLine(
            localPoints: Array<idVec3?>?,
            points: Array<idVec3?>?,
            startIndex: Int,
            endIndex: Int,
            start: idVec3?,
            end: idVec3?,
            startClip: CInt?,
            endClip: CInt?
        ): Boolean {
            var d1: Float
            var d2: Float
            var fstart: Float
            var fend: Float
            var lstart: Float
            var lend: Float
            var f: Float
            var x: Float
            val leftScale: Float
            val upScale: Float
            var scale1: Float
            var scale2: Float
            var startCull: Int
            var endCull: Int
            val localStart = idVec3()
            val localEnd = idVec3()
            val localDir = idVec3()
            leftScale = dLeft * invFar
            upScale = dUp * invFar
            localStart.oSet(localPoints.get(startIndex))
            localEnd.oSet(localPoints.get(endIndex))
            localDir.oSet(localEnd.oMinus(localStart))
            startClip.setVal(endClip.getVal() - 1)
            scale1 = idMath.INFINITY
            scale2 = -idMath.INFINITY
            fstart = dFar * localStart.y
            fend = dFar * localEnd.y
            lstart = dLeft * localStart.x
            lend = dLeft * localEnd.x

            // test left plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            startCull = Math_h.FLOATSIGNBITSET(d1)
            endCull = Math_h.FLOATSIGNBITSET(d2)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (Math.abs(localStart.z + f * localDir.z) <= x * upScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip.setVal(0)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip.setVal(0)
                            }
                        }
                    }
                }
            }

            // test right plane
            d1 = fstart + lstart
            d2 = fend + lend
            startCull = startCull or (Math_h.FLOATSIGNBITSET(d1) shl 1)
            endCull = endCull or (Math_h.FLOATSIGNBITSET(d2) shl 1)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (Math.abs(localStart.z + f * localDir.z) <= x * upScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip.setVal(1)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip.setVal(1)
                            }
                        }
                    }
                }
            }
            fstart = dFar * localStart.z
            fend = dFar * localEnd.z
            lstart = dUp * localStart.x
            lend = dUp * localEnd.x

            // test up plane
            d1 = -fstart + lstart
            d2 = -fend + lend
            startCull = startCull or (Math_h.FLOATSIGNBITSET(d1) shl 2)
            endCull = endCull or (Math_h.FLOATSIGNBITSET(d2) shl 2)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (Math.abs(localStart.y + f * localDir.y) <= x * leftScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip.setVal(2)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip.setVal(2)
                            }
                        }
                    }
                }
            }

            // test down plane
            d1 = fstart + lstart
            d2 = fend + lend
            startCull = startCull or (Math_h.FLOATSIGNBITSET(d1) shl 3)
            endCull = endCull or (Math_h.FLOATSIGNBITSET(d2) shl 3)
            if (Math_h.FLOATNOTZERO(d1)) {
                if (Math_h.FLOATSIGNBITSET(d1) xor Math_h.FLOATSIGNBITSET(d2) != 0) {
                    f = d1 / (d1 - d2)
                    x = localStart.x + f * localDir.x
                    if (x >= 0.0f) {
                        if (Math.abs(localStart.y + f * localDir.y) <= x * leftScale) {
                            if (f < scale1) {
                                scale1 = f
                                startClip.setVal(3)
                            }
                            if (f > scale2) {
                                scale2 = f
                                endClip.setVal(3)
                            }
                        }
                    }
                }
            }

            // if completely inside
            if (0 == startCull or endCull) {
                start.oSet(points.get(startIndex))
                end.oSet(points.get(endIndex))
                return true
            } else if (scale1 <= scale2) {
                if (0 == startCull) {
                    start.oSet(points.get(startIndex))
                    startClip.setVal(-1)
                } else {
                    start.oSet(
                        points.get(startIndex).oPlus(points.get(endIndex).oMinus(points.get(startIndex)))
                            .oMultiply(scale1)
                    )
                }
                if (0 == endCull) {
                    end.oSet(points.get(endIndex))
                    endClip.setVal(-1)
                } else {
                    end.oSet(
                        points.get(startIndex).oPlus(points.get(endIndex).oMinus(points.get(startIndex)))
                            .oMultiply(scale2)
                    )
                }
                return true
            }
            return false
        }

        private fun oSet(f: idFrustum?) {
            origin.oSet(f.origin)
            axis = idMat3(f.axis)
            dNear = f.dNear
            dFar = f.dFar
            dLeft = f.dLeft
            dUp = f.dUp
            invFar = f.invFar
        }

        companion object {
            val capPointIndex: Array<IntArray?>? =
                arrayOf(intArrayOf(0, 3), intArrayOf(1, 2), intArrayOf(0, 1), intArrayOf(2, 3))
            private const val VORONOI_INDEX_0_0_0 = 0 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_0_0 = 1 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_0_0 = 2 + 0 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_1_0 = 0 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_2_0 = 0 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_0_0_1 = 0 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_0_2 = 0 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_1_1 = 1 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_1_1 = 2 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_2_1 = 1 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_2_1 = 2 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_1_2 = 1 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_1_2 = 2 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_2_2 = 1 + 2 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_2_2 = 2 + 2 * 3 + 2 * 9
            private const val VORONOI_INDEX_1_1_0 = 1 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_1_0 = 2 + 1 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_2_0 = 1 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_2_2_0 = 2 + 2 * 3 + 0 * 9
            private const val VORONOI_INDEX_1_0_1 = 1 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_2_0_1 = 2 + 0 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_1_1 = 0 + 1 * 3 + 1 * 9
            private const val VORONOI_INDEX_0_2_1 = 0 + 2 * 3 + 1 * 9
            private const val VORONOI_INDEX_1_0_2 = 1 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_2_0_2 = 2 + 0 * 3 + 2 * 9
            private const val VORONOI_INDEX_0_1_2 = 0 + 1 * 3 + 2 * 9
            private const val VORONOI_INDEX_0_2_2 = 0 + 2 * 3 + 2 * 9
        }
    }
}