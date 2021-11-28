package neo.idlib.math

import neo.idlib.Text.Str.idStr
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Quat {
    /**
     * ===============================================================================
     *
     *
     * Quaternion
     *
     *
     * ===============================================================================
     */
    class idQuat {
        //        public:
        var w = 0f
        var x //TODO:prime candidate to turn into an array.
                = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        constructor(quat: idQuat?) {
            x = quat.x
            y = quat.y
            z = quat.z
            w = quat.w
        }

        //	float &			operator[]( int index );
        fun Set(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        fun oGet(index: Int): Float {
            return when (index) {
                1 -> y
                2 -> z
                3 -> w
                else -> x
            }
        }

        fun oSet(index: Int, value: Float) {
            when (index) {
                1 -> y = value
                2 -> z = value
                3 -> w = value
                else -> x = value
            }
        }

        fun oNegative(): idQuat? {
            return idQuat(-x, -y, -z, -w)
        }

        fun oSet(a: idQuat?): idQuat? {
            x = a.x
            y = a.y
            z = a.z
            w = a.w
            return this
        }

        fun oPlus(a: idQuat?): idQuat? {
            return idQuat(x + a.x, y + a.y, z + a.z, w + a.w)
        }

        fun oPluSet(a: idQuat?): idQuat? {
            x += a.x
            y += a.y
            z += a.z
            w += a.w
            return this
        }

        fun oMinus(a: idQuat?): idQuat? {
            return idQuat(x - a.x, y - a.y, z - a.z, w - a.w)
        }

        fun oMinSet(a: idQuat?): idQuat? {
            x -= a.x
            y -= a.y
            z -= a.z
            w -= a.w
            return this
        }

        fun oMultiply(a: idQuat?): idQuat? {
            return idQuat(
                w * a.x + x * a.w + y * a.z - z * a.y,
                w * a.y + y * a.w + z * a.x - x * a.z,
                w * a.z + z * a.w + x * a.y - y * a.x,
                w * a.w - x * a.x - y * a.y - z * a.z
            )
        }

        fun oMultiply(a: idVec3?): idVec3? {
//#if 0
            // it's faster to do the conversion to a 3x3 matrix and multiply the vector by this 3x3 matrix
//            return (ToMat3() * a);
//#else
            // result = this->Inverse() * idQuat( a.x, a.y, a.z, 0.0f ) * (*this)
            val xxzz = x * x - z * z
            val wwyy = w * w - y * y
            val xw2 = x * w * 2.0f
            val xy2 = x * y * 2.0f
            val xz2 = x * z * 2.0f
            val yw2 = y * w * 2.0f
            val yz2 = y * z * 2.0f
            val zw2 = z * w * 2.0f
            return idVec3(
                (xxzz + wwyy) * a.x + (xy2 + zw2) * a.y + (xz2 - yw2) * a.z,
                (xy2 - zw2) * a.x + (y * y + w * w - x * x - z * z) * a.y + (yz2 + xw2) * a.z,
                (xz2 + yw2) * a.x + (yz2 - xw2) * a.y + (wwyy - xxzz) * a.z
            )
            //#endif
        }

        fun oMultiply(a: Float): idQuat? {
            return idQuat(x * a, y * a, z * a, w * a)
        }

        //
        fun oMulSet(a: idQuat?): idQuat? {
            this.oSet(this.oMultiply(a))
            return this
        }

        fun oMulSet(a: Float): idQuat? {
            x *= a
            y *= a
            z *= a
            w *= a
            return this
        }

        //
        fun Compare(a: idQuat?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z && w == a.w
        }

        fun Compare(a: idQuat?, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(x - a.x) > epsilon) {
                return false
            }
            if (Math.abs(y - a.y) > epsilon) {
                return false
            }
            return if (Math.abs(z - a.z) > epsilon) {
                false
            } else Math.abs(w - a.w) <= epsilon
        }

        //public 	bool			operator==(	const idQuat &a ) const;					// exact compare, no epsilon
        //public 	bool			operator!=(	const idQuat &a ) const;					// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 5
            hash = 31 * hash + java.lang.Float.floatToIntBits(x)
            hash = 31 * hash + java.lang.Float.floatToIntBits(y)
            hash = 31 * hash + java.lang.Float.floatToIntBits(z)
            hash = 31 * hash + java.lang.Float.floatToIntBits(w)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idQuat?
            if (java.lang.Float.floatToIntBits(x) != java.lang.Float.floatToIntBits(other.x)) {
                return false
            }
            if (java.lang.Float.floatToIntBits(y) != java.lang.Float.floatToIntBits(other.y)) {
                return false
            }
            return if (java.lang.Float.floatToIntBits(z) != java.lang.Float.floatToIntBits(other.z)) {
                false
            } else java.lang.Float.floatToIntBits(w) == java.lang.Float.floatToIntBits(other.w)
        }

        //
        fun Inverse(): idQuat? {
            return idQuat(-x, -y, -z, w)
        }

        fun Length(): Float {
            val len: Float
            len = x * x + y * y + z * z + w * w
            return idMath.Sqrt(len)
        }

        fun Normalize(): idQuat? {
            val len: Float
            val ilength: Float
            len = Length()
            if (len != 0f) {
                ilength = 1 / len
                x *= ilength
                y *= ilength
                z *= ilength
                w *= ilength
            }
            return this
        }

        //
        fun CalcW(): Float {
            // take the absolute value because floating point rounding may cause the dot of x,y,z to be larger than 1
            return Math.sqrt(Math.abs(1.0f - (x * x + y * y + z * z)).toDouble()).toFloat()
        }

        fun GetDimension(): Int {
            return 4
        }

        //
        fun ToAngles(): idAngles? {
            return ToMat3().ToAngles()
        }

        fun ToRotation(): idRotation? {
            val vec = idVec3()
            var angle: Float
            vec.x = x
            vec.y = y
            vec.z = z
            angle = idMath.ACos(w)
            if (angle == 0.0f) {
                vec.Set(0.0f, 0.0f, 1.0f)
            } else {
                //vec *= (1.0f / sin( angle ));
                vec.Normalize()
                vec.FixDegenerateNormal()
                angle *= 2.0f * idMath.M_RAD2DEG
            }
            return idRotation(Vector.getVec3_origin(), vec, angle)
        }

        fun ToMat3(): idMat3? {
            val mat = idMat3()
            val wx: Float
            val wy: Float
            val wz: Float
            val xx: Float
            val yy: Float
            val yz: Float
            val xy: Float
            val xz: Float
            val zz: Float
            val x2: Float
            val y2: Float
            val z2: Float
            x2 = x + x
            y2 = y + y
            z2 = z + z
            xx = x * x2
            xy = x * y2
            xz = x * z2
            yy = y * y2
            yz = y * z2
            zz = z * z2
            wx = w * x2
            wy = w * y2
            wz = w * z2
            mat.oSet(0, 0, 1.0f - (yy + zz))
            mat.oSet(0, 1, xy - wz)
            mat.oSet(0, 2, xz + wy)
            mat.oSet(1, 0, xy + wz)
            mat.oSet(1, 1, 1.0f - (xx + zz))
            mat.oSet(1, 2, yz - wx)
            mat.oSet(2, 0, xz - wy)
            mat.oSet(2, 1, yz + wx)
            mat.oSet(2, 2, 1.0f - (xx + yy))
            return mat
        }

        fun ToMat4(): idMat4? {
            return ToMat3().ToMat4()
        }

        fun ToCQuat(): idCQuat? {
            return if (w < 0.0f) {
                idCQuat(-x, -y, -z)
            } else idCQuat(x, y, z)
        }

        fun ToAngularVelocity(): idVec3? {
            val vec = idVec3()
            vec.x = x
            vec.y = y
            vec.z = z
            vec.Normalize()
            return vec.oMultiply(idMath.ACos(w))
        }

        //public 	const float *	ToFloatPtr( void ) const;
        @Deprecated("")
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z, w) //TODO:array!?
        }

        fun ToString(precision: Int): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }
        //
        /**
         * ===================== idQuat::Slerp
         *
         *
         * Spherical linear interpolation between two quaternions.
         * =====================
         */
        fun Slerp(from: idQuat?, to: idQuat?, t: Float): idQuat? {
            var temp: idQuat? = idQuat()
            val omega: Float
            var cosom: Float
            val sinom: Float
            var scale0: Float
            val scale1: Float
            if (t <= 0.0f) {
                this.oSet(from)
                return this
            }
            if (t >= 1.0f) {
                this.oSet(to)
                return this
            }
            if (from === to) {
                this.oSet(to)
                return this
            }
            cosom = from.x * to.x + from.y * to.y + from.z * to.z + from.w * to.w
            if (cosom < 0.0f) {
                this.oSet(to.oNegative())
                cosom = -cosom
            } else {
                temp = to
            }
            if (1.0f - cosom > 1e-6f) {
//#if 0
//		omega = acos( cosom );
//		sinom = 1.0f / sin( omega );
//		scale0 = sin( ( 1.0f - t ) * omega ) * sinom;
//		scale1 = sin( t * omega ) * sinom;
//#else
                scale0 = 1.0f - cosom * cosom
                sinom = idMath.InvSqrt(scale0)
                omega = idMath.ATan16(scale0 * sinom, cosom)
                scale0 = idMath.Sin16((1.0f - t) * omega) * sinom
                scale1 = idMath.Sin16(t * omega) * sinom
                //#endif
            } else {
                scale0 = 1.0f - t
                scale1 = t
            }
            this.oSet(from.oMultiply(scale0).oPlus(temp.oMultiply(scale1)))
            return this
        }

        companion object {
            fun oMultiply(a: Float, b: idQuat?): idQuat? {
                return b.oMultiply(a)
            }

            //
            //	float			operator[]( int index ) const;
            fun oMultiply(a: idVec3?, b: idQuat?): idVec3? {
                return b.oMultiply(a)
            }
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Compressed quaternion
     *
     *
     * ===============================================================================
     */
    class idCQuat {
        //        public:
        var x = 0f
        var y = 0f
        var z = 0f

        //
        constructor()
        constructor(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        //
        fun Set(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        //
        //	float			operator[]( int index ) const;
        fun oGet(index: Int): Float {
            return when (index) {
                1 -> y
                2 -> z
                else -> x
            }
        }

        //	float &			operator[]( int index );
        fun oSet(index: Int, value: Float) {
            when (index) {
                1 -> y = value
                2 -> z = value
                else -> x = value
            }
        }

        //
        fun Compare(a: idCQuat?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z
        }

        fun Compare(a: idCQuat?, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(x - a.x) > epsilon) {
                return false
            }
            return if (Math.abs(y - a.y) > epsilon) {
                false
            } else Math.abs(z - a.z) <= epsilon
        }

        //	bool			operator==(	const idCQuat &a ) const;					// exact compare, no epsilon
        //	bool			operator!=(	const idCQuat &a ) const;					// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 37 * hash + java.lang.Float.floatToIntBits(x)
            hash = 37 * hash + java.lang.Float.floatToIntBits(y)
            hash = 37 * hash + java.lang.Float.floatToIntBits(z)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idCQuat?
            if (java.lang.Float.floatToIntBits(x) != java.lang.Float.floatToIntBits(other.x)) {
                return false
            }
            return if (java.lang.Float.floatToIntBits(y) != java.lang.Float.floatToIntBits(other.y)) {
                false
            } else java.lang.Float.floatToIntBits(z) == java.lang.Float.floatToIntBits(other.z)
        }

        //
        fun GetDimension(): Int {
            return 3
        }

        //
        fun ToAngles(): idAngles? {
            return ToQuat().ToAngles()
        }

        fun ToRotation(): idRotation? {
            return ToQuat().ToRotation()
        }

        fun ToMat3(): idMat3? {
            return ToQuat().ToMat3()
        }

        fun ToMat4(): idMat4? {
            return ToQuat().ToMat4()
        }

        fun ToQuat(): idQuat? {
            // take the absolute value because floating point rounding may cause the dot of x,y,z to be larger than 1
            return idQuat(x, y, z, Math.sqrt(Math.abs(1.0f - (x * x + y * y + z * z)).toDouble()).toFloat())
        }

        //	const float *	ToFloatPtr( void ) const;
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z) //TODO:back redf
        }

        fun ToString(precision: Int): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }
    }
}