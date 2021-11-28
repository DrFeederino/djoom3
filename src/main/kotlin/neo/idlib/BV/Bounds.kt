package neo.idlib.BV

import neo.TempDump.SERiAL
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Lib
import neo.idlib.containers.CFloat
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Simd
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import kotlin.math.abs

object Bounds {
    var bounds_zero: idBounds? = null

    /*
     ================
     BoundsForPointRotation

     only for rotations < 180 degrees
     ================
     */
    fun BoundsForPointRotation(start: idVec3, rotation: idRotation): idBounds {
        var i: Int
        val radiusSqr: Float
        val v1 = idVec3()
        val v2 = idVec3()
        val origin = idVec3()
        val axis = idVec3()
        val end = idVec3()
        val bounds = idBounds()
        end.oSet(rotation.oMultiply(start))
        axis.oSet(rotation.GetVec())
        origin.oSet(rotation.GetOrigin().oPlus(axis.oMultiply(axis.oMultiply(start.oMinus(rotation.GetOrigin())))))
        radiusSqr = start.oMinus(origin).LengthSqr()
        v1.oSet(start.oMinus(origin).Cross(axis))
        v2.oSet(end.oMinus(origin).Cross(axis))
        i = 0
        while (i < 3) {

            // if the derivative changes sign along this axis during the rotation from start to end
            if (v1.oGet(i) > 0.0f && v2.oGet(i) < 0.0f || v1.oGet(i) < 0.0f && v2.oGet(i) > 0.0f) {
                if (0.5f * (start.oGet(i) + end.oGet(i)) - origin.oGet(i) > 0.0f) {
                    bounds.oSet(0, i, Lib.Min(start.oGet(i), end.oGet(i)))
                    bounds.oSet(1, i, origin.oGet(i) + idMath.Sqrt(radiusSqr * (1.0f - axis.oGet(i) * axis.oGet(i))))
                } else {
                    bounds.oSet(0, i, origin.oGet(i) - idMath.Sqrt(radiusSqr * (1.0f - axis.oGet(i) * axis.oGet(i))))
                    bounds.oSet(1, i, Lib.Max(start.oGet(i), end.oGet(i)))
                }
            } else if (start.oGet(i) > end.oGet(i)) {
                bounds.oSet(0, i, end.oGet(i))
                bounds.oSet(1, i, start.oGet(i))
            } else {
                bounds.oSet(0, i, start.oGet(i))
                bounds.oSet(1, i, end.oGet(i))
            }
            i++
        }
        return bounds
    }

    /*
     ===============================================================================

     Axis Aligned Bounding Box

     ===============================================================================
     */
    class idBounds : SERiAL {
        private val b: Array<idVec3> = idVec3.generateArray(2)

        constructor()
        constructor(mins: idVec3, maxs: idVec3) {
            b[0].oSet(mins)
            b[1].oSet(maxs)
        }

        constructor(bounds: idBounds) {
            this.oSet(bounds)
        }

        constructor(point: idVec3) {
            b[0].oSet(point)
            b[1].oSet(point)
        }

        operator fun set(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float) {
            b[0].oSet(idVec3(v0, v1, v2))
            b[1].oSet(idVec3(v3, v4, v5))
        }

        //
        //public	final idVec3 	operator[]( final int index ) ;
        fun oSet(bounds: idBounds) {
            b[0].oSet(bounds.b[0])
            b[1].oSet(bounds.b[1])
        }

        fun oGet(index: Int): idVec3 {
            return b[index]
        }

        fun oGet(index1: Int, index2: Int): Float {
            return b[index1].oGet(index2)
        }

        fun oSet(index: Int, t: idVec3): idVec3 {
            return b[index].oSet(t)
        }

        fun oSet(x: Int, y: Int, value: Float): Float {
            return b[x].oSet(y, value)
        }

        // returns translated bounds
        fun oPlus(t: idVec3): idBounds {
            return idBounds(b[0].oPlus(t), b[1].oPlus(t))
        }

        // translate the bounds
        fun oPluSet(t: idVec3): idBounds {
            b[0].oPluSet(t)
            b[1].oPluSet(t)
            return this
        }

        fun oPluSet(index: Int, t: idVec3): idVec3 {
            return b[index].oPluSet(t)
        }

        // returns rotated bounds
        fun oMultiply(r: idMat3): idBounds {
            val bounds = idBounds()
            bounds.FromTransformedBounds(this, Vector.getVec3_origin(), r)
            return bounds
        }

        // rotate the bounds
        fun oMulSet(r: idMat3): idBounds {
            FromTransformedBounds(this, Vector.getVec3_origin(), r)
            return this
        }

        fun oPlus(a: idBounds): idBounds {
            val newBounds: idBounds
            newBounds = idBounds(this)
            newBounds.AddBounds(a)
            return newBounds
        }

        fun oPluSet(a: idBounds): idBounds {
            AddBounds(a)
            return this
        }

        fun oMinus(a: idBounds): idBounds {
            assert(
                b[1].oGet(0) - b[0].oGet(0) > a.b[1].oGet(0) - a.b[0].oGet(0) && b[1]
                    .oGet(1) - b[0].oGet(1) > a.b[1].oGet(1) - a.b[0].oGet(1) && b[1].oGet(2) - b[0]
                    .oGet(2) > a.b[1].oGet(2) - a.b[0].oGet(2)
            )
            return idBounds(
                idVec3(
                    b[0].oGet(0) + a.b[1].oGet(0),
                    b[0].oGet(1) + a.b[1].oGet(1),
                    b[0].oGet(2) + a.b[1].oGet(2)
                ),
                idVec3(
                    b[1].oGet(0) + a.b[0].oGet(0),
                    b[1].oGet(1) + a.b[0].oGet(1),
                    b[1].oGet(2) + a.b[0].oGet(2)
                )
            )
        }

        fun oMinSet(a: idBounds): idBounds {
            assert(
                b[1].oGet(0) - b[0].oGet(0) > a.b[1].oGet(0) - a.b[0].oGet(0) && b[1]
                    .oGet(1) - b[0].oGet(1) > a.b[1].oGet(1) - a.b[0].oGet(1) && b[1].oGet(2) - b[0]
                    .oGet(2) > a.b[1].oGet(2) - a.b[0].oGet(2)
            )
            b[0].oPluSet(a.b[1])
            b[1].oPluSet(a.b[0])
            return this
        }

        fun oMinSet(t: idVec3): idBounds {
            b[0].oMinSet(t)
            b[1].oMinSet(t)
            return this
        }

        fun oMinSet(index: Int, t: idVec3): idVec3 {
            return b[index].oMinSet(t)
        }

        fun Compare(a: idBounds): Boolean {                            // exact compare, no epsilon
            return b[0].Compare(a.b[0]) && b[1].Compare(a.b[1])
        }

        //public	boolean			operator==(	final idBounds a ) ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final idBounds a ) ;						// exact compare, no epsilon
        fun Compare(a: idBounds, epsilon: Float): Boolean {    // compare with epsilon
            return b[0].Compare(a.b[0], epsilon) && b[1].Compare(a.b[1], epsilon)
        }

        override fun hashCode(): Int {
            var hash = 5
            hash = 11 * hash + b.contentDeepHashCode()
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idBounds
            return b.contentDeepEquals(other.b)
        }

        // inside out bounds
        fun Clear() {
            b[0].oSet(idVec3(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY))
            b[1]
                .oSet(idVec3(-idMath.INFINITY, -idMath.INFINITY, -idMath.INFINITY)) //TODO:set faster than new objects?
        }

        // single point at origin
        fun Zero() {
            b[1].z = 0f
            b[1].y = b[1].z
            b[1].x = b[1].y
            b[0].z = b[1].x
            b[0].y = b[0].z
            b[0].x = b[0].y
        }

        // returns center of bounds
        fun GetCenter(): idVec3 {
            return idVec3(
                (b[1].oGet(0) + b[0].oGet(0)) * 0.5f,
                (b[1].oGet(1) + b[0].oGet(1)) * 0.5f,
                (b[1].oGet(2) + b[0].oGet(2)) * 0.5f
            )
        }

        // returns the radius relative to the bounds origin
        fun GetRadius(): Float {
            var i: Int
            var total: Float
            var b0: Float
            var b1: Float
            total = 0.0f
            i = 0
            while (i < 3) {
                b0 = abs(b[0].oGet(i))
                b1 = abs(b[1].oGet(i))
                total += if (b0 > b1) {
                    b0 * b0
                } else {
                    b1 * b1
                }
                i++
            }
            return idMath.Sqrt(total)
        }

        // returns the radius relative to the given center
        fun GetRadius(center: idVec3): Float {
            var i: Int
            var total: Float
            var b0: Float
            var b1: Float
            total = 0.0f
            i = 0
            while (i < 3) {
                b0 = abs(center.oGet(i) - b[0].oGet(i))
                b1 = abs(b[1].oGet(i) - center.oGet(i))
                total += if (b0 > b1) {
                    b0 * b0
                } else {
                    b1 * b1
                }
                i++
            }
            return idMath.Sqrt(total)
        }

        // returns the volume of the bounds
        fun GetVolume(): Float {
            return if (b[0].oGet(0) >= b[1].oGet(0) || b[0].oGet(1) >= b[1].oGet(1) || b[0]
                    .oGet(2) >= b[1].oGet(2)
            ) {
                0.0f
            } else (b[1].oGet(0) - b[0].oGet(0)) * (b[1].oGet(1) - b[0].oGet(1)) * (b[1]
                .oGet(2) - b[0].oGet(2))
        }

        // returns true if bounds are inside out
        fun IsCleared(): Boolean {
            return b[0].oGet(0) > b[1].oGet(0)
        }

        // add the point, returns true if the bounds expanded
        fun AddPoint(v: idVec3): Boolean {
            var expanded = false
            if (v.oGet(0) < b[0].oGet(0)) {
                b[0].oSet(0, v.oGet(0))
                expanded = true
            }
            if (v.oGet(0) > b[1].oGet(0)) {
                b[1].oSet(0, v.oGet(0))
                expanded = true
            }
            if (v.oGet(1) < b[0].oGet(1)) {
                b[0].oSet(1, v.oGet(1))
                expanded = true
            }
            if (v.oGet(1) > b[1].oGet(1)) {
                b[1].oSet(1, v.oGet(1))
                expanded = true
            }
            if (v.oGet(2) < b[0].oGet(2)) {
                b[0].oSet(2, v.oGet(2))
                expanded = true
            }
            if (v.oGet(2) > b[1].oGet(2)) {
                b[1].oSet(2, v.oGet(2))
                expanded = true
            }
            return expanded
        }

        // add the bounds, returns true if the bounds expanded
        fun AddBounds(a: idBounds): Boolean {
            var expanded = false
            if (a.b[0].oGet(0) < b[0].oGet(0)) {
                b[0].oSet(0, a.b[0].oGet(0))
                expanded = true
            }
            if (a.b[0].oGet(1) < b[0].oGet(1)) {
                b[0].oSet(1, a.b[0].oGet(1))
                expanded = true
            }
            if (a.b[0].oGet(2) < b[0].oGet(2)) {
                b[0].oSet(2, a.b[0].oGet(2))
                expanded = true
            }
            if (a.b[1].oGet(0) > b[1].oGet(0)) {
                b[1].oSet(0, a.b[1].oGet(0))
                expanded = true
            }
            if (a.b[1].oGet(1) > b[1].oGet(1)) {
                b[1].oSet(1, a.b[1].oGet(1))
                expanded = true
            }
            if (a.b[1].oGet(2) > b[1].oGet(2)) {
                b[1].oSet(2, a.b[1].oGet(2))
                expanded = true
            }
            return expanded
        }

        // return intersection of this bounds with the given bounds
        fun Intersect(a: idBounds): idBounds {
            val n = idBounds()
            n.b[0].oSet(0, if (a.b[0].oGet(0) > b[0].oGet(0)) a.b[0].oGet(0) else b[0].oGet(0))
            n.b[0].oSet(1, if (a.b[0].oGet(1) > b[0].oGet(1)) a.b[0].oGet(1) else b[0].oGet(1))
            n.b[0].oSet(2, if (a.b[0].oGet(2) > b[0].oGet(2)) a.b[0].oGet(2) else b[0].oGet(2))
            n.b[1].oSet(0, if (a.b[1].oGet(0) < b[1].oGet(0)) a.b[1].oGet(0) else b[1].oGet(0))
            n.b[1].oSet(1, if (a.b[1].oGet(1) < b[1].oGet(1)) a.b[1].oGet(1) else b[1].oGet(1))
            n.b[1].oSet(2, if (a.b[1].oGet(2) < b[1].oGet(2)) a.b[1].oGet(2) else b[1].oGet(2))
            return n
        }

        // intersect this bounds with the given bounds
        fun IntersectSelf(a: idBounds): idBounds {
            if (a.b[0].oGet(0) > b[0].oGet(0)) {
                b[0].oSet(0, a.b[0].oGet(0))
            }
            if (a.b[0].oGet(1) > b[0].oGet(1)) {
                b[0].oSet(1, a.b[0].oGet(1))
            }
            if (a.b[0].oGet(2) > b[0].oGet(2)) {
                b[0].oSet(2, a.b[0].oGet(2))
            }
            if (a.b[1].oGet(0) < b[1].oGet(0)) {
                b[1].oSet(0, a.b[1].oGet(0))
            }
            if (a.b[1].oGet(1) < b[1].oGet(1)) {
                b[1].oSet(1, a.b[1].oGet(1))
            }
            if (a.b[1].oGet(2) < b[1].oGet(2)) {
                b[1].oSet(2, a.b[1].oGet(2))
            }
            return this
        }

        /**
         * @return bounds expanded in all directions with the given value
         */
        fun Expand(d: Float): idBounds {
            return idBounds(
                idVec3(b[0].oGet(0) - d, b[0].oGet(1) - d, b[0].oGet(2) - d),
                idVec3(b[1].oGet(0) + d, b[1].oGet(1) + d, b[1].oGet(2) + d)
            )
        }

        /**
         * expand bounds in all directions with the given value
         */
        fun ExpandSelf(d: Float): idBounds {
            b[0].oMinSet(idVec3(d, d, d))
            b[1].x += d
            b[1].y += d
            b[1].z += d
            return this
        }

        fun Translate(translation: idVec3): idBounds { // return translated bounds
            return idBounds(b[0].oPlus(translation), b[1].oPlus(translation))
        }

        // translate this bounds
        fun TranslateSelf(translation: idVec3): idBounds {
            b[0].oPluSet(translation)
            b[1].oPluSet(translation)
            return this
        }

        // return rotated bounds
        fun Rotate(rotation: idMat3): idBounds {
            val bounds = idBounds()
            bounds.FromTransformedBounds(this, Vector.getVec3_origin(), rotation)
            return bounds
        }

        // rotate this bounds
        fun RotateSelf(rotation: idMat3): idBounds {
            FromTransformedBounds(this, Vector.getVec3_origin(), rotation)
            return this
        }

        fun PlaneDistance(plane: idPlane): Float {
            val center = idVec3()
            val d1: Float
            val d2: Float
            center.oSet(b[0].oPlus(b[1]).oMultiply(0.5f))
            d1 = plane.Distance(center)
            d2 = (abs((b[1].oGet(0) - center.oGet(0)) * plane.Normal().oGet(0))
                    + abs((b[1].oGet(1) - center.oGet(1)) * plane.Normal().oGet(1))
                    + abs((b[1].oGet(2) - center.oGet(2)) * plane.Normal().oGet(2)))
            if (d1 - d2 > 0.0f) {
                return d1 - d2
            }
            return if (d1 + d2 < 0.0f) {
                d1 + d2
            } else 0.0f
        }

        @JvmOverloads
        fun PlaneSide(plane: idPlane, epsilon: Float = Plane.ON_EPSILON): Int {
            val center = idVec3()
            val d1: Float
            val d2: Float
            center.oSet(b[0].oPlus(b[1]).oMultiply(0.5f))
            d1 = plane.Distance(center)
            d2 = (abs((b[1].oGet(0) - center.oGet(0)) * plane.Normal().oGet(0))
                    + abs((b[1].oGet(1) - center.oGet(1)) * plane.Normal().oGet(1))
                    + abs((b[1].oGet(2) - center.oGet(2)) * plane.Normal().oGet(2)))
            if (d1 - d2 > epsilon) {
                return Plane.PLANESIDE_FRONT
            }
            return if (d1 + d2 < -epsilon) {
                Plane.PLANESIDE_BACK
            } else Plane.PLANESIDE_CROSS
        }

        // includes touching
        fun ContainsPoint(p: idVec3): Boolean {
            return (p.oGet(0) >= b[0].oGet(0) && p.oGet(1) >= b[0].oGet(1) && p.oGet(2) >= b[0].oGet(2)
                    && p.oGet(0) <= b[1].oGet(0) && p.oGet(1) <= b[1].oGet(1) && p.oGet(2) <= b[1].oGet(2))
        }

        // includes touching
        fun IntersectsBounds(a: idBounds): Boolean {
            return (a.b[1].oGet(0) >= b[0].oGet(0) && a.b[1].oGet(1) >= b[0].oGet(1) && a.b[1]
                .oGet(2) >= b[0].oGet(2)
                    && a.b[0].oGet(0) <= b[1].oGet(0) && a.b[0].oGet(1) <= b[1].oGet(1) && a.b[0]
                .oGet(2) <= b[1].oGet(2))
        }

        /*
         ============
         idBounds::LineIntersection

         Returns true if the line intersects the bounds between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3, end: idVec3): Boolean {
            val ld = FloatArray(3)
            val center = idVec3(b[0].oPlus(b[1]).oMultiply(0.5f))
            val extents = idVec3(b[1].oMinus(center))
            val lineDir = idVec3(end.oMinus(start).oMultiply(0.5f))
            val lineCenter = idVec3(start.oPlus(lineDir))
            val dir = idVec3(lineCenter.oMinus(center))
            ld[0] = abs(lineDir.oGet(0))
            if (abs(dir.oGet(0)) > extents.oGet(0) + ld[0]) {
                return false
            }
            ld[1] = abs(lineDir.oGet(1))
            if (abs(dir.oGet(1)) > extents.oGet(1) + ld[1]) {
                return false
            }
            ld[2] = abs(lineDir.oGet(2))
            if (abs(dir.oGet(2)) > extents.oGet(2) + ld[2]) {
                return false
            }
            val cross = idVec3(lineDir.Cross(dir))
            if (abs(cross.oGet(0)) > extents.oGet(1) * ld[2] + extents.oGet(2) * ld[1]) {
                return false
            }
            return if (abs(cross.oGet(1)) > extents.oGet(0) * ld[2] + extents.oGet(2) * ld[0]) {
                false
            } else abs(cross.oGet(2)) <= extents.oGet(0) * ld[1] + extents.oGet(1) * ld[0]
        }

        /*
         ============
         idBounds::RayIntersection

         Returns true if the ray intersects the bounds.
         The ray can intersect the bounds in both directions from the start point.
         If start is inside the bounds it is considered an intersection with scale = 0
         ============
         */
        fun RayIntersection(
            start: idVec3,
            dir: idVec3,
            scale: CFloat
        ): Boolean { // intersection point is start + dir * scale
            var i: Int
            var ax0: Int
            val ax1: Int
            val ax2: Int
            var side: Int
            var inside: Int
            var f: Float
            val hit = idVec3()
            ax0 = -1
            inside = 0
            i = 0
            while (i < 3) {
                side = if (start.oGet(i) < b[0].oGet(i)) {
                    0
                } else if (start.oGet(i) > b[1].oGet(i)) {
                    1
                } else {
                    inside++
                    i++
                    continue
                }
                if (dir.oGet(i) == 0.0f) {
                    i++
                    continue
                }
                f = start.oGet(i) - b[side].oGet(i)
                if (ax0 < 0 || abs(f) > abs(scale._val * dir.oGet(i))) {
                    scale._val = -(f / dir.oGet(i))
                    ax0 = i
                }
                i++
            }
            if (ax0 < 0) {
                scale._val = 0.0f //TODO:should scale have a backreference?
                // return true if the start point is inside the bounds
                return inside == 3
            }
            ax1 = (ax0 + 1) % 3
            ax2 = (ax0 + 2) % 3
            hit.oSet(ax1, start.oGet(ax1) + scale._val * dir.oGet(ax1))
            hit.oSet(ax2, start.oGet(ax2) + scale._val * dir.oGet(ax2))
            return hit.oGet(ax1) >= b[0].oGet(ax1) && hit.oGet(ax1) <= b[1].oGet(ax1) && hit.oGet(ax2) >= b[0].oGet(ax2) && hit.oGet(
                ax2
            ) <= b[1].oGet(ax2)
        }

        // most tight bounds for the given transformed bounds
        fun FromTransformedBounds(bounds: idBounds, origin: idVec3, axis: idMat3) {
            var i: Int
            val center = idVec3()
            val extents = idVec3()
            val rotatedExtents = idVec3()
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))
            i = 0
            while (i < 3) {
                rotatedExtents.oSet(
                    i, abs(extents.oGet(0) * axis.oGet(0).oGet(i))
                            + abs(extents.oGet(1) * axis.oGet(1).oGet(i))
                            + abs(extents.oGet(2) * axis.oGet(2).oGet(i))
                )
                i++
            }
            center.oSet(origin.oPlus(axis.oMultiply(center)))
            b[0].oSet(center.oMinus(rotatedExtents))
            b[1].oSet(center.oPlus(rotatedExtents))
        }

        /*
         ============
         idBounds::FromPoints

         Most tight bounds for a point set.
         ============
         */
        fun FromPoints(points: Array<idVec3>, numPoints: Int) { // most tight bounds for a point set
            Simd.SIMDProcessor.MinMax(b[0], b[1], points, numPoints)
        }

        /*
         ============
         idBounds::FromPointTranslation

         Most tight bounds for the translational movement of the given point.
         ============
         */
        fun FromPointTranslation(point: idVec3, translation: idVec3) { // most tight bounds for a translation
            var i: Int
            i = 0
            while (i < 3) {
                if (translation.oGet(i) < 0.0f) {
                    b[0].oSet(i, point.oGet(i) + translation.oGet(i))
                    b[1].oSet(i, point.oGet(i))
                } else {
                    b[0].oSet(i, point.oGet(i))
                    b[1].oSet(i, point.oGet(i) + translation.oGet(i))
                }
                i++
            }
        }

        /*
         ============
         idBounds::FromBoundsTranslation

         Most tight bounds for the translational movement of the given bounds.
         ============
         */
        fun FromBoundsTranslation(bounds: idBounds, origin: idVec3, axis: idMat3, translation: idVec3) {
            var i: Int
            if (axis.IsRotated()) {
                FromTransformedBounds(bounds, origin, axis)
            } else {
                b[0].oSet(bounds.oGet(0).oPlus(origin))
                b[1].oSet(bounds.oGet(1).oPlus(origin))
            }
            i = 0
            while (i < 3) {
                if (translation.oGet(i) < 0.0f) {
                    b[0].oPluSet(i, translation.oGet(i))
                } else {
                    b[1].oPluSet(i, translation.oGet(i))
                }
                i++
            }
        }

        /*
         ============
         idBounds::FromPointRotation

         Most tight bounds for the rotational movement of the given point.
         ============
         */
        fun FromPointRotation(point: idVec3, rotation: idRotation) { // most tight bounds for a rotation
            val radius: Float
            if (abs(rotation.GetAngle()) < 180.0f) {
                BoundsForPointRotation(point, rotation)
            } else {
                radius = point.oMinus(rotation.GetOrigin()).Length()

                // FIXME: these bounds are usually way larger
                b[0].Set(-radius, -radius, -radius)
                b[1].Set(radius, radius, radius)
            }
        }

        /*
         ============
         idBounds::FromBoundsRotation

         Most tight bounds for the rotational movement of the given bounds.
         ============
         */
        fun FromBoundsRotation(bounds: idBounds, origin: idVec3, axis: idMat3, rotation: idRotation) {
            var i: Int
            val radius: Float
            val point = idVec3()
            if (abs(rotation.GetAngle()) < 180.0f) {
                val rotationPointBounds: Array<idVec3> = idVec3.copyVec(
                    BoundsForPointRotation(
                        axis.oMultiply(bounds.oGet(0)).oPlus(origin),
                        rotation
                    ).b
                )
                b[0].oSet(rotationPointBounds[0])
                b[1].oSet(rotationPointBounds[1])
                i = 1
                while (i < 8) {
                    point.oSet(0, bounds.oGet(i xor (i shr 1) and 1).oGet(0))
                    point.oSet(1, bounds.oGet(i shr 1 and 1).oGet(1))
                    point.oSet(2, bounds.oGet(i shr 2 and 1).oGet(2))
                    this.oPluSet(BoundsForPointRotation(axis.oMultiply(point).oPlus(origin), rotation))
                    i++
                }
            } else {
                point.oSet(bounds.oGet(1).oMinus(bounds.oGet(0)).oMultiply(0.5f))
                radius = bounds.oGet(1).oMinus(point).Length() + point.oMinus(rotation.GetOrigin()).Length()

                // FIXME: these bounds are usually way larger
                b[0].Set(-radius, -radius, -radius)
                b[1].Set(radius, radius, radius)
            }
        }

        fun ToPoints(points: Array<idVec3>) {
            for (i in 0..7) {
                points[i].oSet(0, b[i xor (i shr 1) and 1].oGet(0))
                points[i].oSet(1, b[i shr 1 and 1].oGet(1))
                points[i].oSet(2, b[i shr 2 and 1].oGet(2))
            }
        }

        fun ToSphere(): idSphere {
            val sphere = idSphere()
            sphere.SetOrigin(b[0].oPlus(b[1]).oMultiply(0.5f))
            sphere.SetRadius(b[1].oMinus(sphere.GetOrigin()).Length())
            return sphere
        }

        fun AxisProjection(dir: idVec3, min: CFloat, max: CFloat) {
            val d1: Float
            val d2: Float
            val center = idVec3()
            val extents = idVec3()
            center.oSet(b[0].oPlus(b[1]).oMultiply(0.5f))
            extents.oSet(b[1].oMinus(center))
            d1 = dir.oMultiply(center)
            d2 = (abs(extents.oGet(0) * dir.oGet(0))
                    + abs(extents.oGet(1) * dir.oGet(1))
                    + abs(extents.oGet(2) * dir.oGet(2)))
            min._val = d1 - d2
            max._val = d1 + d2
        }

        fun AxisProjection(origin: idVec3, axis: idMat3, dir: idVec3, min: CFloat, max: CFloat) {
            val d1: Float
            val d2: Float
            val center = idVec3()
            val extents = idVec3()
            center.oSet(b[0].oPlus(b[1]).oMultiply(0.5f))
            extents.oSet(b[1].oMinus(center))
            center.oSet(origin.oPlus(axis.oMultiply(center)))
            d1 = dir.oMultiply(center)
            d2 = (abs(extents.oGet(0) * dir.oMultiply(axis.oGet(0)))
                    + abs(extents.oGet(1) * dir.oMultiply(axis.oGet(1)))
                    + abs(extents.oGet(2) * dir.oMultiply(axis.oGet(2))))
            min._val = d1 - d2
            max._val = d1 + d2
        }

        override fun toString(): String {
            return b.contentToString()
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            b[0].Read(buffer)
            b[1].Read(buffer)
        }

        override fun Write(): ByteBuffer {
            val buffer = AllocBuffer()
            buffer.put(b[0].Write()).put(b[1].Write()).flip()
            return buffer
        }

        companion object {
            val BYTES: Int = idVec3.BYTES * 2

            //
            //
            fun ClearBounds(): idBounds {
                val idBounds = idBounds()
                idBounds.Clear()
                return idBounds
            }
        }
    }
}