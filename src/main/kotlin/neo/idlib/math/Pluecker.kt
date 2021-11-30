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
        private val p: FloatArray = FloatArray(6)

        constructor()
        constructor(a: FloatArray) {
            System.arraycopy(a, 0, p, 0, 6) //memcpy( p, a, 6 * sizeof( float ) );
        }

        constructor(start: idVec3, end: idVec3) {
            FromLine(start, end)
        }

        constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p[0] = a1
            p[1] = a2
            p[2] = a3
            p[3] = a4
            p[4] = a5
            p[5] = a6
        }

        //public	float			operator[]( final int index ) final;
        fun get(index: Int): Float {
            return p[index]
        }

        operator fun unaryMinus(): idPluecker { // flips the direction
            return idPluecker(-p[0], -p[1], -p[2], -p[3], -p[4], -p[5])
        }

        operator fun times(a: Float): idPluecker {
            return idPluecker(p[0] * a, p[1] * a, p[2] * a, p[3] * a, p[4] * a, p[5] * a)
        }

        operator fun div(a: Float): idPluecker {
            val inva: Float
            assert(a != 0.0f)
            inva = 1.0f / a
            return idPluecker(
                p[0] * inva,
                p[1] * inva,
                p[2] * inva,
                p[3] * inva,
                p[4] * inva,
                p[5] * inva
            )
        }

        operator fun times(a: idPluecker): Float { // permuted inner product
            return p[0] * a.p[4] + p[1] * a.p[5] + p[2] * a.p[3] + p[4] * a.p[0] + p[5] * a.p[1] + p[3] * a.p[2]
        }

        operator fun minus(a: idPluecker): idPluecker {
            return idPluecker(
                p[0] - a.get(0),
                p[1] - a.get(1),
                p[2] - a.get(2),
                p[3] - a.get(3),
                p[4] - a.get(4),
                p[5] - a.get(5)
            )
        }

        operator fun plus(a: idPluecker): idPluecker {
            return idPluecker(
                p[0] + a.get(0),
                p[1] + a.get(1),
                p[2] + a.get(2),
                p[3] + a.get(3),
                p[4] + a.get(4),
                p[5] + a.get(5)
            )
        }

        fun timesAssign(a: Float): idPluecker {
            p[0] *= a
            p[1] *= a
            p[2] *= a
            p[3] *= a
            p[4] *= a
            p[5] *= a
            return this
        }

        fun divAssign(a: Float): idPluecker {
            val inva: Float
            assert(a != 0.0f)
            inva = 1.0f / a
            p[0] *= inva
            p[1] *= inva
            p[2] *= inva
            p[3] *= inva
            p[4] *= inva
            p[5] *= inva
            return this
        }

        fun plusAssign(a: idPluecker): idPluecker {
            p[0] += a.get(0)
            p[1] += a.get(1)
            p[2] += a.get(2)
            p[3] += a.get(3)
            p[4] += a.get(4)
            p[5] += a.get(5)
            return this
        }

        fun minusAssign(a: idPluecker): idPluecker {
            p[0] -= a.get(0)
            p[1] -= a.get(1)
            p[2] -= a.get(2)
            p[3] -= a.get(3)
            p[4] -= a.get(4)
            p[5] -= a.get(5)
            return this
        }

        fun Compare(a: idPluecker): Boolean { // exact compare, no epsilon
            return (p[0] == a.p[0] && p[1] == a.p[1] && p[2] == a.p[2]
                    && p[3] == a.p[3] && p[4] == a.p[4] && p[5] == a.p[5])
        }

        fun Compare(a: idPluecker, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(p[0] - a.p[0]) > epsilon) {
                return false
            }
            if (Math.abs(p[1] - a.p[1]) > epsilon) {
                return false
            }
            if (Math.abs(p[2] - a.p[2]) > epsilon) {
                return false
            }
            if (Math.abs(p[3] - a.p[3]) > epsilon) {
                return false
            }
            return if (Math.abs(p[4] - a.p[4]) > epsilon) {
                false
            } else Math.abs(p[5] - a.p[5]) <= epsilon
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
            val other = obj as idPluecker
            return Arrays.equals(p, other.p)
        }

        fun Set(a: idPluecker) {
            p[0] = a.p[0]
            p[1] = a.p[1]
            p[2] = a.p[2]
            p[3] = a.p[3]
            p[4] = a.p[4]
            p[5] = a.p[5]
        }

        fun Set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p[0] = a1
            p[1] = a2
            p[2] = a3
            p[3] = a4
            p[4] = a5
            p[5] = a6
        }

        fun Zero() {
            p[5] = 0.0f
            p[4] = p[5]
            p[3] = p[4]
            p[2] = p[3]
            p[1] = p[2]
            p[0] = p[1]
        }

        fun FromLine(start: idVec3, end: idVec3) { // pluecker from line{
            p[0] = start[0] * end[1] - end[0] * start[1]
            p[1] = start[0] * end[2] - end[0] * start[2]
            p[2] = start[0] - end[0]
            p[3] = start[1] * end[2] - end[1] * start[2]
            p[4] = start[2] - end[2]
            p[5] = end[1] - start[1]
        }

        fun FromRay(start: idVec3, dir: idVec3) { // pluecker from ray
            p[0] = start[0] * dir[1] - dir[0] * start[1]
            p[1] = start[0] * dir[2] - dir[0] * start[2]
            p[2] = -dir[0]
            p[3] = start[1] * dir[2] - dir[1] * start[2]
            p[4] = -dir[2]
            p[5] = dir[1]
        }

        /*
         ================
         idPluecker::FromPlanes

         pluecker coordinate for the intersection of two planes
         ================
         */
        fun FromPlanes(p1: idPlane, p2: idPlane): Boolean { // pluecker from intersection of planes
            p[0] = -(p1.get(2) * -p2.get(3) - p2.get(2) * -p1.get(3))
            p[1] = -(p2.get(1) * -p1.get(3) - p1.get(1) * -p2.get(3))
            p[2] = p1.get(1) * p2.get(2) - p2.get(1) * p1.get(2)
            p[3] = -(p1.get(0) * -p2.get(3) - p2.get(0) * -p1.get(3))
            p[4] = p1.get(0) * p2.get(1) - p2.get(0) * p1.get(1)
            p[5] = p1.get(0) * p2.get(2) - p2.get(0) * p1.get(2)
            return p[2] != 0.0f || p[5] != 0.0f || p[4] != 0.0f
        }

        // pluecker to line
        fun ToLine(start: idVec3, end: idVec3): Boolean {
            val dir1 = idVec3()
            val dir2 = idVec3()
            val d: Float
            dir1[0] = p[3]
            dir1[1] = -p[1]
            dir1[2] = p[0]

            dir2[0] = -p[2]
            dir2[1] = p[5]
            dir2[2] = -p[4]

            d = dir2 * dir2
            if (d == 0.0f) {
                return false // pluecker coordinate does not represent a line
            }

            start.set(dir2.Cross(dir1) * (1.0f / d))
            end.set(start + dir2)
            return true
        }

        // pluecker to ray
        fun ToRay(start: idVec3, dir: idVec3): Boolean {
            val dir1 = idVec3()
            val d: Float
            dir1[0] = p[3]
            dir1[1] = -p[1]
            dir1[2] = p[0]

            dir[0] = -p[2]
            dir[1] = p[5]
            dir[2] = -p[4]

            d = dir * dir
            if (d == 0.0f) {
                return false // pluecker coordinate does not represent a line
            }

            start.set(dir.Cross(dir1) * (1.0f / d))
            return true
        }

        fun ToDir(dir: idVec3) { // pluecker to direction{
            dir[0] = -p[2]
            dir[1] = p[5]
            dir[2] = -p[4]
        }

        fun PermutedInnerProduct(a: idPluecker): Float { // pluecker permuted inner product
            return p[0] * a.p[4] + p[1] * a.p[5] + p[2] * a.p[3] + p[4] * a.p[0] + p[5] * a.p[1] + p[3] * a.p[2]
        }

        /*
         ================
         idPluecker::Distance3DSqr

         calculates square of shortest distance between the two
         3D lines represented by their pluecker coordinates
         ================
         */
        fun Distance3DSqr(a: idPluecker): Float { // pluecker line distance{
            val d: Float
            val s: Float
            val dir = idVec3()
            dir[0] = -a.p[5] * p[4] - a.p[4] * -p[5]
            dir[1] = a.p[4] * p[2] - a.p[2] * p[4]
            dir[2] = a.p[2] * -p[5] - -a.p[5] * p[2]
            if (dir[0] == 0.0f && dir[1] == 0.0f && dir[2] == 0.0f) {
                return -1.0f    // FIXME: implement for parallel lines
            }
            d = a.p[4] * (p[2] * dir[1] - -p[5] * dir[0]) +
                    a.p[5] * (p[2] * dir[2] - p[4] * dir[0]) +
                    a.p[2] * (-p[5] * dir[2] - p[4] * dir[1])
            s = PermutedInnerProduct(a) / d
            return (dir * dir) * (s * s)
        }

        fun Length(): Float { // pluecker length
            return idMath.Sqrt(p[5] * p[5] + p[4] * p[4] + p[2] * p[2])
        }

        fun LengthSqr(): Float { // pluecker squared length
            return p[5] * p[5] + p[4] * p[4] + p[2] * p[2]
        }

        fun Normalize(): idPluecker { // pluecker normalize
            var d: Float
            d = LengthSqr()
            if (d == 0.0f) {
                return this // pluecker coordinate does not represent a line
            }
            d = idMath.InvSqrt(d)
            return idPluecker(p[0] * d, p[1] * d, p[2] * d, p[3] * d, p[4] * d, p[5] * d)
        }

        fun NormalizeSelf(): Float { // pluecker normalize 
            val l: Float
            val d: Float
            l = LengthSqr()
            if (l == 0.0f) {
                return l // pluecker coordinate does not represent a line
            }
            d = idMath.InvSqrt(l)
            p[0] *= d
            p[1] *= d
            p[2] *= d
            p[3] *= d
            p[4] *= d
            p[5] *= d
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