package neo.idlib.math

import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import java.util.*

/**
 * Plücker
 */
class Pluecker {
    /*
     ===============================================================================

     Pluecker coordinate

     ===============================================================================
     */
    class idPluecker {
        private val p: FloatArray? = FloatArray(6)

        constructor()
        constructor(a: FloatArray?) {
            System.arraycopy(a, 0, p, 0, 6) //memcpy( p, a, 6 * sizeof( float ) );
        }

        constructor(start: idVec3?, end: idVec3?) {
            FromLine(start, end)
        }

        constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        //public	float			operator[]( final int index ) final;
        fun oGet(index: Int): Float {
            return p.get(index)
        }

        fun oNegative(): idPluecker? { // flips the direction
            return idPluecker(-p.get(0), -p.get(1), -p.get(2), -p.get(3), -p.get(4), -p.get(5))
        }

        fun oMultiply(a: Float): idPluecker? {
            return idPluecker(p.get(0) * a, p.get(1) * a, p.get(2) * a, p.get(3) * a, p.get(4) * a, p.get(5) * a)
        }

        fun oDivide(a: Float): idPluecker? {
            val inva: Float
            assert(a != 0.0f)
            inva = 1.0f / a
            return idPluecker(
                p.get(0) * inva,
                p.get(1) * inva,
                p.get(2) * inva,
                p.get(3) * inva,
                p.get(4) * inva,
                p.get(5) * inva
            )
        }

        fun oMultiply(a: idPluecker?): Float { // permuted inner product
            return p.get(0) * a.p.get(4) + p.get(1) * a.p.get(5) + p.get(2) * a.p.get(3) + p.get(4) * a.p.get(0) + p.get(
                5
            ) * a.p.get(1) + p.get(3) * a.p.get(2)
        }

        fun oMinus(a: idPluecker?): idPluecker? {
            return idPluecker(
                p.get(0) - a.oGet(0),
                p.get(1) - a.oGet(1),
                p.get(2) - a.oGet(2),
                p.get(3) - a.oGet(3),
                p.get(4) - a.oGet(4),
                p.get(5) - a.oGet(5)
            )
        }

        fun oPlus(a: idPluecker?): idPluecker? {
            return idPluecker(
                p.get(0) + a.oGet(0),
                p.get(1) + a.oGet(1),
                p.get(2) + a.oGet(2),
                p.get(3) + a.oGet(3),
                p.get(4) + a.oGet(4),
                p.get(5) + a.oGet(5)
            )
        }

        fun oMulSet(a: Float): idPluecker? {
            p.get(0) *= a
            p.get(1) *= a
            p.get(2) *= a
            p.get(3) *= a
            p.get(4) *= a
            p.get(5) *= a
            return this
        }

        fun oDivSet(a: Float): idPluecker? {
            val inva: Float
            assert(a != 0.0f)
            inva = 1.0f / a
            p.get(0) *= inva
            p.get(1) *= inva
            p.get(2) *= inva
            p.get(3) *= inva
            p.get(4) *= inva
            p.get(5) *= inva
            return this
        }

        fun oPluSet(a: idPluecker?): idPluecker? {
            p.get(0) += a.oGet(0)
            p.get(1) += a.oGet(1)
            p.get(2) += a.oGet(2)
            p.get(3) += a.oGet(3)
            p.get(4) += a.oGet(4)
            p.get(5) += a.oGet(5)
            return this
        }

        fun oMinSet(a: idPluecker?): idPluecker? {
            p.get(0) -= a.oGet(0)
            p.get(1) -= a.oGet(1)
            p.get(2) -= a.oGet(2)
            p.get(3) -= a.oGet(3)
            p.get(4) -= a.oGet(4)
            p.get(5) -= a.oGet(5)
            return this
        }

        fun Compare(a: idPluecker?): Boolean { // exact compare, no epsilon
            return (p.get(0) == a.p.get(0) && p.get(1) == a.p.get(1) && p.get(2) == a.p.get(2)
                    && p.get(3) == a.p.get(3) && p.get(4) == a.p.get(4) && p.get(5) == a.p.get(5))
        }

        fun Compare(a: idPluecker?, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(p.get(0) - a.p.get(0)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(1) - a.p.get(1)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(2) - a.p.get(2)) > epsilon) {
                return false
            }
            if (Math.abs(p.get(3) - a.p.get(3)) > epsilon) {
                return false
            }
            return if (Math.abs(p.get(4) - a.p.get(4)) > epsilon) {
                false
            } else Math.abs(p.get(5) - a.p.get(5)) <= epsilon
        }

        //public	boolean			operator==(	final idPluecker &a ) final;					// exact compare, no epsilon
        //public	boolean			operator!=(	final idPluecker &a ) final;					// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 5
            hash = 83 * hash + Arrays.hashCode(p)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idPluecker?
            return Arrays.equals(p, other.p)
        }

        fun Set(a: idPluecker?) {
            p.get(0) = a.p.get(0)
            p.get(1) = a.p.get(1)
            p.get(2) = a.p.get(2)
            p.get(3) = a.p.get(3)
            p.get(4) = a.p.get(4)
            p.get(5) = a.p.get(5)
        }

        fun Set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        fun Zero() {
            p.get(5) = 0.0f
            p.get(4) = p.get(5)
            p.get(3) = p.get(4)
            p.get(2) = p.get(3)
            p.get(1) = p.get(2)
            p.get(0) = p.get(1)
        }

        fun FromLine(start: idVec3?, end: idVec3?) { // pluecker from line{
            p.get(0) = start.oGet(0) * end.oGet(1) - end.oGet(0) * start.oGet(1)
            p.get(1) = start.oGet(0) * end.oGet(2) - end.oGet(0) * start.oGet(2)
            p.get(2) = start.oGet(0) - end.oGet(0)
            p.get(3) = start.oGet(1) * end.oGet(2) - end.oGet(1) * start.oGet(2)
            p.get(4) = start.oGet(2) - end.oGet(2)
            p.get(5) = end.oGet(1) - start.oGet(1)
        }

        fun FromRay(start: idVec3?, dir: idVec3?) { // pluecker from ray
            p.get(0) = start.oGet(0) * dir.oGet(1) - dir.oGet(0) * start.oGet(1)
            p.get(1) = start.oGet(0) * dir.oGet(2) - dir.oGet(0) * start.oGet(2)
            p.get(2) = -dir.oGet(0)
            p.get(3) = start.oGet(1) * dir.oGet(2) - dir.oGet(1) * start.oGet(2)
            p.get(4) = -dir.oGet(2)
            p.get(5) = dir.oGet(1)
        }

        /*
         ================
         idPluecker::FromPlanes

         pluecker coordinate for the intersection of two planes
         ================
         */
        fun FromPlanes(p1: idPlane?, p2: idPlane?): Boolean { // pluecker from intersection of planes
            p.get(0) = -(p1.oGet(2) * -p2.oGet(3) - p2.oGet(2) * -p1.oGet(3))
            p.get(1) = -(p2.oGet(1) * -p1.oGet(3) - p1.oGet(1) * -p2.oGet(3))
            p.get(2) = p1.oGet(1) * p2.oGet(2) - p2.oGet(1) * p1.oGet(2)
            p.get(3) = -(p1.oGet(0) * -p2.oGet(3) - p2.oGet(0) * -p1.oGet(3))
            p.get(4) = p1.oGet(0) * p2.oGet(1) - p2.oGet(0) * p1.oGet(1)
            p.get(5) = p1.oGet(0) * p2.oGet(2) - p2.oGet(0) * p1.oGet(2)
            return p.get(2) != 0.0f || p.get(5) != 0.0f || p.get(4) != 0.0f
        }

        // pluecker to line
        fun ToLine(start: idVec3?, end: idVec3?): Boolean {
            val dir1 = idVec3()
            val dir2 = idVec3()
            val d: Float
            dir1.oSet(0, p.get(3))
            dir1.oSet(1, -p.get(1))
            dir1.oSet(2, p.get(0))
            dir2.oSet(0, -p.get(2))
            dir2.oSet(1, p.get(5))
            dir2.oSet(2, -p.get(4))
            d = dir2.oMultiply(dir2)
            if (d == 0.0f) {
                return false // pluecker coordinate does not represent a line
            }
            start.oSet(dir2.Cross(dir1).oMultiply(1.0f / d))
            end.oSet(start.oPlus(dir2))
            return true
        }

        // pluecker to ray
        fun ToRay(start: idVec3?, dir: idVec3?): Boolean {
            val dir1 = idVec3()
            val d: Float
            dir1.oSet(0, p.get(3))
            dir1.oSet(1, -p.get(1))
            dir1.oSet(2, p.get(0))
            dir.oSet(0, -p.get(2))
            dir.oSet(1, p.get(5))
            dir.oSet(2, -p.get(4))
            d = dir.oMultiply(dir)
            if (d == 0.0f) {
                return false // pluecker coordinate does not represent a line
            }
            start.oSet(dir.Cross(dir1).oMultiply(1.0f / d))
            return true
        }

        fun ToDir(dir: idVec3?) { // pluecker to direction{
            dir.oSet(0, -p.get(2))
            dir.oSet(1, p.get(5))
            dir.oSet(2, -p.get(4))
        }

        fun PermutedInnerProduct(a: idPluecker?): Float { // pluecker permuted inner product
            return p.get(0) * a.p.get(4) + p.get(1) * a.p.get(5) + p.get(2) * a.p.get(3) + p.get(4) * a.p.get(0) + p.get(
                5
            ) * a.p.get(1) + p.get(3) * a.p.get(2)
        }

        /*
         ================
         idPluecker::Distance3DSqr

         calculates square of shortest distance between the two
         3D lines represented by their pluecker coordinates
         ================
         */
        fun Distance3DSqr(a: idPluecker?): Float { // pluecker line distance{
            val d: Float
            val s: Float
            val dir = idVec3()
            dir.oSet(0, -a.p.get(5) * p.get(4) - a.p.get(4) * -p.get(5))
            dir.oSet(1, a.p.get(4) * p.get(2) - a.p.get(2) * p.get(4))
            dir.oSet(2, a.p.get(2) * -p.get(5) - -a.p.get(5) * p.get(2))
            if (dir.oGet(0) == 0.0f && dir.oGet(1) == 0.0f && dir.oGet(2) == 0.0f) {
                return -1.0f // FIXME: implement for parallel lines
            }
            d =
                a.p.get(4) * (p.get(2) * dir.oGet(1) - -p.get(5) * dir.oGet(0)) + a.p.get(5) * (p.get(2) * dir.oGet(2) - p.get(
                    4
                ) * dir.oGet(0)) + a.p.get(2) * (-p.get(5) * dir.oGet(2) - p.get(4) * dir.oGet(1))
            s = PermutedInnerProduct(a) / d
            return dir.oMultiply(dir) * (s * s)
        }

        fun Length(): Float { // pluecker length
            return idMath.Sqrt(p.get(5) * p.get(5) + p.get(4) * p.get(4) + p.get(2) * p.get(2))
        }

        fun LengthSqr(): Float { // pluecker squared length
            return p.get(5) * p.get(5) + p.get(4) * p.get(4) + p.get(2) * p.get(2)
        }

        fun Normalize(): idPluecker? { // pluecker normalize
            var d: Float
            d = LengthSqr()
            if (d == 0.0f) {
                return this // pluecker coordinate does not represent a line
            }
            d = idMath.InvSqrt(d)
            return idPluecker(p.get(0) * d, p.get(1) * d, p.get(2) * d, p.get(3) * d, p.get(4) * d, p.get(5) * d)
        }

        fun NormalizeSelf(): Float { // pluecker normalize 
            val l: Float
            val d: Float
            l = LengthSqr()
            if (l == 0.0f) {
                return l // pluecker coordinate does not represent a line
            }
            d = idMath.InvSqrt(l)
            p.get(0) *= d
            p.get(1) *= d
            p.get(2) *= d
            p.get(3) *= d
            p.get(4) *= d
            p.get(5) *= d
            return d * l
        }

        fun GetDimension(): Int {
            return 6
        } //

        //public	final float *	ToFloatPtr( void ) final;
        //public	float *			ToFloatPtr( void );
        //public	final char *	ToString( int precision = 2 ) final;
        companion object {
            fun generateArray(length: Int): Array<idPluecker> {
                val arr = arrayOf<idPluecker>()
                for (i in 0..length) {
                    arr[i] = idPluecker()
                }
                return arr
            }
        }
    }
}