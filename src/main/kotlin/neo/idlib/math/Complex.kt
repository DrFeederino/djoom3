package neo.idlib.math

import neo.idlib.math.Math_h.idMath

/**
 *
 */
class Complex {
    /*
     ===============================================================================

     Complex number

     ===============================================================================
     */
    internal class idComplex {
        var i // imaginary part
                = 0f
        var r // real part
                = 0f

        constructor()
        constructor(r: Float, i: Float) {
            this.r = r
            this.i = i
        }

        //public		float &				operator[]( int index );
        //
        //public		idComplex			operator-() final;
        fun Set(r: Float, i: Float) {
            this.r = r
            this.i = i
        }

        //public		idComplex &			operator=( final idComplex &a );
        fun Zero() {
            i = 0.0f
            r = i
        }

        //
        //public		idComplex			operator*( final idComplex &a ) final;
        //public	float				operator[]( int index ) final;
        fun oGet(index: Int): Float {
            assert(index >= 0 && index < 2)
            return if (0 == index) {
                r
            } else {
                i
            }
        }

        //public		idComplex			operator/( final idComplex &a ) final;
        fun oSet(index: Int, value: Float) {
            assert(index >= 0 && index < 2)
            if (0 == index) {
                r = value
            } else {
                i = value
            }
        }

        //public		idComplex			operator+( final idComplex &a ) final;
        fun oNegative(): idComplex? {
            return idComplex(-r, -i)
        }

        //public		idComplex			operator-( final idComplex &a ) final;
        fun oSet(a: idComplex?): idComplex? {
            r = a.r
            i = a.i
            return this
        }

        //
        //public		idComplex &			operator*=( final idComplex &a );
        fun oMultiply(a: idComplex?): idComplex? {
            return idComplex(r * a.r - i * a.i, i * a.r + r * a.i)
        }

        //public		idComplex &			operator/=( final idComplex &a );
        fun oDivide(a: idComplex?): idComplex? {
            val s: Float
            val t: Float
            return if (Math.abs(a.r) >= Math.abs(a.i)) {
                s = a.i / a.r
                t = 1.0f / (a.r + s * a.i)
                idComplex((r + s * i) * t, (i - s * r) * t)
            } else {
                s = a.r / a.i
                t = 1.0f / (s * a.r + a.i)
                idComplex((r * s + i) * t, (i * s - r) * t)
            }
        }

        //public		idComplex &			operator+=( final idComplex &a );
        fun oPlus(a: idComplex?): idComplex? {
            return idComplex(r + a.r, i + a.i)
        }

        //public		idComplex &			operator-=( final idComplex &a );
        fun oMinus(a: idComplex?): idComplex? {
            return idComplex(r - a.r, i - a.i)
        }

        //
        //public		idComplex			operator*( final float a ) final;
        fun oMulSet(a: idComplex?): idComplex? {
            Set(r * a.r - i * a.i, i * a.r + r * a.i)
            return this
        }

        //public		idComplex			operator/( final float a ) final;
        fun oDivSet(a: idComplex?): idComplex? {
            val s: Float
            val t: Float
            if (Math.abs(a.r) >= Math.abs(a.i)) {
                s = a.i / a.r
                t = 1.0f / (a.r + s * a.i)
                Set((r + s * i) * t, (i - s * r) * t)
            } else {
                s = a.r / a.i
                t = 1.0f / (s * a.r + a.i)
                Set((r * s + i) * t, (i * s - r) * t)
            }
            return this
        }

        //public		idComplex			operator+( final float a ) final;
        fun oPluSet(a: idComplex?): idComplex? {
            r += a.r
            i += a.i
            return this
        }

        //public		idComplex			operator-( final float a ) final;
        fun oMinSet(a: idComplex?): idComplex? {
            r -= a.r
            i -= a.i
            return this
        }

        //
        //public		idComplex &			operator*=( final float a );
        fun oMultiply(a: Float): idComplex? {
            return idComplex(r * a, i * a)
        }

        //public		idComplex &			operator/=( final float a );
        fun oDivide(a: Float): idComplex? {
            val s = 1.0f / a
            return idComplex(r * s, i * s)
        }

        //public		idComplex &			operator+=( final float a );
        fun oPlus(a: Float): idComplex? {
            return idComplex(r + a, i)
        }

        //public		idComplex &			operator-=( final float a );
        fun oMinus(a: Float): idComplex? {
            return idComplex(r - a, i)
        }

        //
        //public		friend idComplex	operator*( final float a, final idComplex &b );
        fun oMulSet(a: Float): idComplex? {
            r *= a
            i *= a
            return this
        }

        //public		friend idComplex	operator/( final float a, final idComplex &b );
        fun oDivSet(a: Float): idComplex? {
            val s = 1.0f / a
            r *= s
            i *= s
            return this
        }

        //public		friend idComplex	operator+( final float a, final idComplex &b );
        fun oPluSet(a: Float): idComplex? {
            r += a
            return this
        }

        //public		friend idComplex	operator-( final float a, final idComplex &b );
        fun oMinSet(a: Float): idComplex? {
            r -= a
            return this
        }

        //
        fun Compare(a: idComplex?): Boolean { // exact compare, no epsilon
            return r == a.r && i == a.i
        }

        fun Compare(a: idComplex?, epsilon: Float): Boolean { // compare with epsilon
            return if (Math.abs(r - a.r) > epsilon) {
                false
            } else Math.abs(i - a.i) <= epsilon
        }

        //public		boolean				operator==(	final idComplex &a ) final;						// exact compare, no epsilon
        //public		boolean				operator!=(	final idComplex &a ) final;						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 29 * hash + java.lang.Float.floatToIntBits(r)
            hash = 29 * hash + java.lang.Float.floatToIntBits(i)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idComplex?
            return if (java.lang.Float.floatToIntBits(r) != java.lang.Float.floatToIntBits(other.r)) {
                false
            } else java.lang.Float.floatToIntBits(i) == java.lang.Float.floatToIntBits(other.i)
        }

        fun Reciprocal(): idComplex? {
            val s: Float
            val t: Float
            return if (Math.abs(r) >= Math.abs(i)) {
                s = i / r
                t = 1.0f / (r + s * i)
                idComplex(t, -s * t)
            } else {
                s = r / i
                t = 1.0f / (s * r + i)
                idComplex(s * t, -t)
            }
        }

        fun Sqrt(): idComplex? {
            val x: Float
            val y: Float
            var w: Float
            if (r == 0.0f && i == 0.0f) {
                return idComplex(0.0f, 0.0f)
            }
            x = Math.abs(r)
            y = Math.abs(i)
            if (x >= y) {
                w = y / x
                w = idMath.Sqrt(x) * idMath.Sqrt(0.5f * (1.0f + idMath.Sqrt(1.0f + w * w)))
            } else {
                w = x / y
                w = idMath.Sqrt(y) * idMath.Sqrt(0.5f * (w + idMath.Sqrt(1.0f + w * w)))
            }
            if (w == 0.0f) {
                return idComplex(0.0f, 0.0f)
            }
            return if (r >= 0.0f) {
                idComplex(w, 0.5f * i / w)
            } else {
                idComplex(0.5f * y / w, if (i >= 0.0f) w else -w)
            }
        }

        fun Abs(): Float {
            val x: Float
            val y: Float
            val t: Float
            x = Math.abs(r)
            y = Math.abs(i)
            return if (x == 0.0f) {
                y
            } else if (y == 0.0f) {
                x
            } else if (x > y) {
                t = y / x
                x * idMath.Sqrt(1.0f + t * t)
            } else {
                t = x / y
                y * idMath.Sqrt(1.0f + t * t)
            }
        }

        fun GetDimension(): Int {
            return 2
        } //

        //public		final float *		ToFloatPtr( void ) final;
        //public		float *				ToFloatPtr( void );
        //public		final char *		ToString( int precision = 2 ) final;
        companion object {
            fun oMultiply(a: Float, b: idComplex?): idComplex? {
                return idComplex(a * b.r, a * b.i)
            }

            fun oDivide(a: Float, b: idComplex?): idComplex? {
                val s: Float
                val t: Float
                return if (Math.abs(b.r) >= Math.abs(b.i)) {
                    s = b.i / b.r
                    t = a / (b.r + s * b.i)
                    idComplex(t, -s * t)
                } else {
                    s = b.r / b.i
                    t = a / (s * b.r + b.i)
                    idComplex(s * t, -t)
                }
            }

            fun oPlus(a: Float, b: idComplex?): idComplex? {
                return idComplex(a + b.r, b.i)
            }

            fun oMinus(a: Float, b: idComplex?): idComplex? {
                return idComplex(a - b.r, -b.i)
            }
        }
    }
}