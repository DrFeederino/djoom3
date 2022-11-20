package neo.idlib.math

import neo.idlib.math.Math_h.idMath
import kotlin.math.abs

/**
 *
 */
class Complex {
    /*
     ===============================================================================

     Complex number

     ===============================================================================
     */
    class idComplex {
        var i // imaginary part
                = 0f
        var r // real part
                = 0f

        constructor()
        constructor(r: Float, i: Float) {
            this.r = r
            this.i = i
        }

        //public		float &				operator[]( int index )
        //
        //public		idComplex			operator-() final
        fun set(r: Float, i: Float) {
            this.r = r
            this.i = i
        }

        //public		idComplex &			operator=( final idComplex &a )
        fun Zero() {
            i = 0.0f
            r = i
        }

        //
        //public		idComplex			operator*( final idComplex &a ) final
        //public	float				operator[]( int index ) final
        operator fun get(index: Int): Float {
            assert(index in 0..1)
            return if (0 == index) {
                r
            } else {
                i
            }
        }

        //public		idComplex			operator/( final idComplex &a ) final
        operator fun set(index: Int, value: Float) {
            assert(index in 0..1)
            if (0 == index) {
                r = value
            } else {
                i = value
            }
        }

        //public		idComplex			operator+( final idComplex &a ) final
        operator fun unaryMinus(): idComplex {
            return idComplex(-r, -i)
        }

        //public		idComplex			operator-( final idComplex &a ) final
        fun set(a: idComplex): idComplex {
            r = a.r
            i = a.i
            return this
        }

        //
        //public		idComplex &			operator*=( final idComplex &a )
        operator fun times(a: idComplex): idComplex {
            return idComplex(r * a.r - i * a.i, i * a.r + r * a.i)
        }

        operator fun times(a: Int): idComplex {
            return idComplex(r * a, i * a)
        }

        //public		idComplex &			operator/=( final idComplex &a )
        operator fun div(a: idComplex): idComplex {
            val s: Float
            val t: Float
            return if (abs(a.r) >= abs(a.i)) {
                s = a.i / a.r
                t = 1.0f / (a.r + s * a.i)
                idComplex((r + s * i) * t, (i - s * r) * t)
            } else {
                s = a.r / a.i
                t = 1.0f / (s * a.r + a.i)
                idComplex((r * s + i) * t, (i * s - r) * t)
            }
        }

        fun div(a: Float, b: idComplex): idComplex {
            val s: Float
            val t: Float
            if (abs(b.r) >= abs(b.i)) {
                s = b.i / b.r
                t = a / (b.r + s * b.i)
                return idComplex(t, -s * t)
            } else {
                s = b.r / b.i
                t = a / (s * b.r + b.i)
                return idComplex(s * t, -t)
            }
        }

        operator fun div(a: Float): idComplex {
            var s = 1.0f / a
            return idComplex(r * s, i * s)
        }

        //public		idComplex &			operator+=( final idComplex &a )
        operator fun plus(a: idComplex): idComplex {
            return idComplex(r + a.r, i + a.i)
        }

        //public		idComplex &			operator-=( final idComplex &a )
        operator fun minus(a: idComplex): idComplex {
            return idComplex(r - a.r, i - a.i)
        }

        //
        //public		idComplex			operator*( final float a ) final
        fun timesAssign(a: idComplex): idComplex {
            set(r * a.r - i * a.i, i * a.r + r * a.i)
            return this
        }

        //public		idComplex			operator/( final float a ) final
        fun divAssign(a: idComplex): idComplex {
            val s: Float
            val t: Float
            if (abs(a.r) >= abs(a.i)) {
                s = a.i / a.r
                t = 1.0f / (a.r + s * a.i)
                set((r + s * i) * t, (i - s * r) * t)
            } else {
                s = a.r / a.i
                t = 1.0f / (s * a.r + a.i)
                set((r * s + i) * t, (i * s - r) * t)
            }
            return this
        }

        //public		idComplex			operator+( final float a ) final
        fun plusAssign(a: idComplex): idComplex {
            r += a.r
            i += a.i
            return this
        }

        //public		idComplex			operator-( final float a ) final
        fun minusAssign(a: idComplex): idComplex {
            r -= a.r
            i -= a.i
            return this
        }

        //
        //public		idComplex &			operator*=( final float a )
        operator fun times(a: Float): idComplex {
            return idComplex(r * a, i * a)
        }

        //public		idComplex &			operator+=( final float a )
        fun plus(a: Float): idComplex {
            return idComplex(r + a, i)
        }

        //public		idComplex &			operator-=( final float a )
        fun minus(a: Float): idComplex {
            return idComplex(r - a, i)
        }

        //
        //public		friend idComplex	operator*( final float a, final idComplex &b )
        fun timesAssign(a: Float): idComplex {
            return idComplex(r * a, i * a)
        }

        //public		friend idComplex	operator/( final float a, final idComplex &b )
        fun divAssign(a: Float): idComplex {
            val s = 1.0f / a
            r *= s
            i *= s
            return this
        }

        //public		friend idComplex	operator+( final float a, final idComplex &b )
        fun plusAssign(a: Float): idComplex {
            r += a
            return this
        }

        //public		friend idComplex	operator-( final float a, final idComplex &b )
        fun minusAssign(a: Float): idComplex {
            r -= a
            return this
        }

        //
        fun Compare(a: idComplex): Boolean { // exact compare, no epsilon
            return r == a.r && i == a.i
        }

        fun Compare(a: idComplex, epsilon: Float): Boolean { // compare with epsilon
            return if (abs(r - a.r) > epsilon) {
                false
            } else abs(i - a.i) <= epsilon
        }

        //public		boolean				operator==(	final idComplex &a ) final						// exact compare, no epsilon
        //public		boolean				operator!=(	final idComplex &a ) final						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 29 * hash + java.lang.Float.floatToIntBits(r)
            hash = 29 * hash + java.lang.Float.floatToIntBits(i)
            return hash
        }

        override fun equals(other: Any?): Boolean {
            if (other == null) {
                return false
            }
            if (javaClass != other.javaClass) {
                return false
            }
            val idComplex = other as idComplex
            return if (java.lang.Float.floatToIntBits(r) != java.lang.Float.floatToIntBits(idComplex.r)) {
                false
            } else java.lang.Float.floatToIntBits(i) == java.lang.Float.floatToIntBits(idComplex.i)
        }

        fun Reciprocal(): idComplex {
            val s: Float
            val t: Float
            return if (abs(r) >= abs(i)) {
                s = i / r
                t = 1.0f / (r + s * i)
                idComplex(t, -s * t)
            } else {
                s = r / i
                t = 1.0f / (s * r + i)
                idComplex(s * t, -t)
            }
        }

        fun Sqrt(): idComplex {
            var w: Float
            if (r == 0.0f && i == 0.0f) {
                return idComplex(0.0f, 0.0f)
            }
            val x: Float = abs(r)
            val y: Float = abs(i)
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
            val t: Float
            val x: Float = abs(r)
            val y: Float = abs(i)
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

        //public		final float *		ToFloatPtr( void ) final
        //public		float *				ToFloatPtr( void )
        //public		final char *		ToString( int precision = 2 ) final
        companion object {
            fun times(a: Float, b: idComplex): idComplex {
                return idComplex(a * b.r, a * b.i)
            }

            fun div(a: Float, b: idComplex): idComplex {
                val s: Float
                val t: Float
                return if (abs(b.r) >= abs(b.i)) {
                    s = b.i / b.r
                    t = a / (b.r + s * b.i)
                    idComplex(t, -s * t)
                } else {
                    s = b.r / b.i
                    t = a / (s * b.r + b.i)
                    idComplex(s * t, -t)
                }
            }

            fun plus(a: Float, b: idComplex): idComplex {
                return idComplex(a + b.r, b.i)
            }

            fun minus(a: Float, b: idComplex): idComplex {
                return idComplex(a - b.r, -b.i)
            }
        }
    }
}