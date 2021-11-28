package neo.idlib.BV

import neo.idlib.containers.CFloat
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import java.util.*

/**
 *
 */
class Sphere {
    /*
     ===============================================================================

     Sphere

     ===============================================================================
     */
    class idSphere {
        private val origin: idVec3?
        private var radius = 0f

        //
        //
        constructor() {
            origin = idVec3()
        }

        constructor(point: idVec3?) {
            origin = idVec3(point)
            radius = 0.0f
        }

        constructor(point: idVec3?, r: Float) {
            origin = idVec3(point)
            radius = r
        }

        //
        fun oGet(index: Int): Float {
            return origin.oGet(index)
        }

        fun oSet(index: Int, value: Float): Float {
            return origin.oSet(index, value)
        }

        fun oPlus(t: idVec3?): idSphere? {                // returns tranlated sphere
            return idSphere(origin.oPlus(t), radius)
        }

        fun oPluSet(t: idVec3?): idSphere? {                    // translate the sphere
            origin.oPluSet(t)
            return this
        }

        //public	idSphere		operator+( final idSphere &s );
        //public	idSphere &		operator+=( final idSphere &s );
        //
        fun Compare(a: idSphere?): Boolean {                            // exact compare, no epsilon
            return origin.Compare(a.origin) && radius == a.radius
        }

        fun Compare(a: idSphere?, epsilon: Float): Boolean {    // compare with epsilon
            return origin.Compare(a.origin, epsilon) && Math.abs(radius - a.radius) <= epsilon
        }

        //public	boolean			operator==(	final idSphere &a );						// exact compare, no epsilon
        //public	boolean			operator!=(	final idSphere &a );						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 97 * hash + Objects.hashCode(origin)
            hash = 97 * hash + java.lang.Float.floatToIntBits(radius)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idSphere?
            return if (origin != other.origin) {
                false
            } else java.lang.Float.floatToIntBits(radius) == java.lang.Float.floatToIntBits(other.radius)
        }

        fun Clear() {                                    // inside out sphere
            origin.Zero()
            radius = -1.0f
        }

        fun Zero() {                                    // single point at origin
            origin.Zero()
            radius = 0.0f
        }

        fun SetOrigin(o: idVec3?) {                    // set origin of sphere
            origin.oSet(o)
        }

        fun SetRadius(r: Float) {                        // set square radius
            radius = r
        }

        fun GetOrigin(): idVec3? {                        // returns origin of sphere
            return origin
        }

        fun GetRadius(): Float {                        // returns sphere radius
            return radius
        }

        fun IsCleared(): Boolean {                    // returns true if sphere is inside out
            return radius < 0.0f
        }

        fun AddPoint(p: idVec3?): Boolean {                    // add the point, returns true if the sphere expanded
            return if (radius < 0.0f) {
                origin.oSet(p)
                radius = 0.0f
                true
            } else {
                var r = p.oMinus(origin).LengthSqr()
                if (r > radius * radius) {
                    r = idMath.Sqrt(r)
                    origin.oPluSet(p.oMinus(origin).oMultiply(0.5f).oMultiply(1.0f - radius / r))
                    radius += 0.5f * (r - radius)
                    return true
                }
                false
            }
        }

        fun AddSphere(s: idSphere?): Boolean {                    // add the sphere, returns true if the sphere expanded
            return if (radius < 0.0f) {
                origin.oSet(s.origin)
                radius = s.radius
                true
            } else {
                var r = s.origin.oMinus(origin).LengthSqr()
                if (r > (radius + s.radius) * (radius + s.radius)) {
                    r = idMath.Sqrt(r)
                    origin.oPluSet(s.origin.oPlus(origin).oMultiply(0.5f).oMultiply(1.0f - radius / (r + s.radius)))
                    radius += 0.5f * (r + s.radius - radius)
                    return true
                }
                false
            }
        }

        fun Expand(d: Float): idSphere? {                    // return bounds expanded in all directions with the given value
            return idSphere(origin, radius + d)
        }

        fun ExpandSelf(d: Float): idSphere? {                    // expand bounds in all directions with the given value
            radius += d
            return this
        }

        fun Translate(translation: idVec3?): idSphere? {
            return idSphere(origin.oPlus(translation), radius)
        }

        fun TranslateSelf(translation: idVec3?): idSphere? {
            origin.oPluSet(translation)
            return this
        }

        fun PlaneDistance(plane: idPlane?): Float {
            val d: Float
            d = plane.Distance(origin)
            if (d > radius) {
                return d - radius
            }
            return if (d < -radius) {
                d + radius
            } else 0.0f
        }

        @JvmOverloads
        fun PlaneSide(plane: idPlane?, epsilon: Float = Plane.ON_EPSILON): Int {
            val d: Float
            d = plane.Distance(origin)
            if (d > radius + epsilon) {
                return Plane.PLANESIDE_FRONT
            }
            return if (d < -radius - epsilon) {
                Plane.PLANESIDE_BACK
            } else Plane.PLANESIDE_CROSS
        }

        fun ContainsPoint(p: idVec3?): Boolean {            // includes touching
            return p.oMinus(origin).LengthSqr() <= radius * radius
        }

        fun IntersectsSphere(s: idSphere?): Boolean {    // includes touching
            val r = s.radius + radius
            return s.origin.oMinus(origin).LengthSqr() <= r * r
        }

        /*
         ============
         idSphere::LineIntersection

         Returns true if the line intersects the sphere between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3?, end: idVec3?): Boolean {
            val r = idVec3()
            val s = idVec3()
            val e = idVec3()
            val a: Float
            s.oSet(start.oMinus(origin))
            e.oSet(end.oMinus(origin))
            r.oSet(e.oMinus(s))
            a = s.oNegative().oMultiply(r)
            return if (a <= 0) {
                s.oMultiply(s) < radius * radius
            } else if (a >= r.oMultiply(r)) {
                e.oMultiply(e) < radius * radius
            } else {
                r.oSet(s.oPlus(r.oMultiply(a / r.oMultiply(r))))
                r.oMultiply(r) < radius * radius
            }
        }

        /*
         ============
         idSphere::RayIntersection

         Returns true if the ray intersects the sphere.
         The ray can intersect the sphere in both directions from the start point.
         If start is inside the sphere then scale1 < 0 and scale2 > 0.
         ============
         */
        // intersection points are (start + dir * scale1) and (start + dir * scale2)
        fun RayIntersection(start: idVec3?, dir: idVec3?, scale1: CFloat?, scale2: CFloat?): Boolean {
            var a: Float
            val b: Float
            val c: Float
            val d: Float
            val sqrtd: Float
            val p = idVec3()
            p.oSet(start.oMinus(origin))
            a = dir.oMultiply(dir)
            b = dir.oMultiply(p)
            c = p.oMultiply(p) - radius * radius
            d = b * b - c * a
            if (d < 0.0f) {
                return false
            }
            sqrtd = idMath.Sqrt(d)
            a = 1.0f / a
            scale1.setVal((-b + sqrtd) * a)
            scale2.setVal((-b - sqrtd) * a)
            return true
        }

        /*
         ============
         idSphere::FromPoints

         Tight sphere for a point set.
         ============
         */
        // Tight sphere for a point set.
        fun FromPoints(points: Array<idVec3?>?, numPoints: Int) {
            var i: Int
            var radiusSqr: Float
            var dist: Float
            val mins = idVec3()
            val maxs = idVec3()
            Simd.SIMDProcessor.MinMax(mins, maxs, points, numPoints)
            origin.oSet(mins.oPlus(maxs).oMultiply(0.5f))
            radiusSqr = 0.0f
            i = 0
            while (i < numPoints) {
                dist = points.get(i).oMinus(origin).LengthSqr()
                if (dist > radiusSqr) {
                    radiusSqr = dist
                }
                i++
            }
            radius = idMath.Sqrt(radiusSqr)
        }

        // Most tight sphere for a translation.
        fun FromPointTranslation(point: idVec3?, translation: idVec3?) {
            origin.oSet(point.oPlus(translation.oMultiply(0.5f)))
            radius = idMath.Sqrt(0.5f * translation.LengthSqr())
        }

        fun FromSphereTranslation(sphere: idSphere?, start: idVec3?, translation: idVec3?) {
            origin.oSet(start.oPlus(sphere.origin).oPlus(translation.oMultiply(0.5f)))
            radius = idMath.Sqrt(0.5f * translation.LengthSqr()) + sphere.radius
        }

        // Most tight sphere for a rotation.
        fun FromPointRotation(point: idVec3?, rotation: idRotation?) {
            val end = idVec3(rotation.oMultiply(point))
            origin.oSet(point.oPlus(end).oMultiply(0.5f))
            radius = idMath.Sqrt(0.5f * end.oMinus(point).LengthSqr())
        }

        fun FromSphereRotation(sphere: idSphere?, start: idVec3?, rotation: idRotation?) {
            val end = idVec3(rotation.oMultiply(sphere.origin))
            origin.oSet(start.oPlus(sphere.origin.oPlus(end)).oMultiply(0.5f))
            radius = idMath.Sqrt(0.5f * end.oMinus(sphere.origin).LengthSqr()) + sphere.radius
        }

        fun AxisProjection(dir: idVec3?, min: CFloat?, max: CFloat?) {
            val d: Float
            d = dir.oMultiply(origin)
            min.setVal(d - radius)
            max.setVal(d + radius)
        }
    }
}