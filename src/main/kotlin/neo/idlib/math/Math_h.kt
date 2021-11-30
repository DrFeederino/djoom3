package neo.idlib.math

import neo.idlib.containers.CFloat
import kotlin.math.*

/**
 * ===============================================================================
 *
 *
 * Math
 *
 *
 * ===============================================================================
 */
object Math_h {
    private const val IEEE_DBLE_EXPONENT_BIAS = 0
    private const val IEEE_DBLE_EXPONENT_BITS = 15
    private const val IEEE_DBLE_MANTISSA_BITS = 63
    private const val IEEE_DBLE_SIGN_BIT = 79
    private const val IEEE_DBL_EXPONENT_BIAS = 1023
    private const val IEEE_DBL_EXPONENT_BITS = 11
    private const val IEEE_DBL_MANTISSA_BITS = 52
    private const val IEEE_DBL_SIGN_BIT = 63
    private const val IEEE_FLT_EXPONENT_BIAS = 127
    private const val IEEE_FLT_EXPONENT_BITS = 8
    private const val IEEE_FLT_MANTISSA_BITS = 23
    private const val IEEE_FLT_SIGN_BIT = 31
    fun DEG2RAD(a: Float): Float {
        return a * idMath.M_DEG2RAD
    }

    fun RAD2DEG(a: Float): Float {
        return a * idMath.M_RAD2DEG
    }

    fun SEC2MS(t: Float): Float {
        return idMath.FtoiFast(t * idMath.M_SEC2MS).toFloat()
    }

    fun MS2SEC(t: Float): Float {
        return t * idMath.M_MS2SEC //TODO:doest anybody need the double returns?
    }

    fun ANGLE2SHORT(x: Float): Float {
        return (idMath.FtoiFast(x * 65536.0f / 360.0f) and 65535).toFloat()
    }

    fun SHORT2ANGLE(x: Float): Float {
        return x * (360.0f / 65536.0f)
    }

    fun ANGLE2BYTE(x: Float): Float {
        return (idMath.FtoiFast(x * 256.0f / 360.0f) and 255).toFloat()
    }

    fun BYTE2ANGLE(x: Float): Float {
        return x * (360.0f / 256.0f)
    }

    fun FLOATSIGNBITSET(f: Float): Int {
        return if (
        /**
         * (const unsigned long *)&
         */
            java.lang.Float.floatToIntBits(f) and -0x80000000 == 0) 0 else 1
    }

    fun FLOATSIGNBITNOTSET(f: Float): Int {
        return  /*(~(*(const unsigned long *)&(f))) >> 31;}*/if (java.lang.Float.floatToIntBits(f)
                .inv() and -0x80000000 == 0
        ) 0 else 1
    }

    fun FLOATNOTZERO(f: Float): Boolean /*{return (*(const unsigned long *)&(f)) & ~(1<<31) ;}*/ {
        return f != 0.0f
    }

    fun INTSIGNBITSET(i: Int): Int {
        return i ushr 31
    }

    fun INTSIGNBITNOTSET(i: Int): Int {
        return i.inv() ushr 31
    }

    fun FLOAT_IS_NAN(x: Float): Boolean /*(((*(const unsigned long *)&x) & 0x7f800000) == 0x7f800000)*/ {
        return x.isNaN()
    }

    fun FLOAT_IS_INF(x: Float): Boolean {
        return java.lang.Float.floatToIntBits(x) and 0x7fffffff == 0x7f800000
    }

    // Not used
//    fun FLOAT_IS_IND(x: Float): Boolean {
//        return x == -0x400000f
//    }

    fun FLOAT_IS_DENORMAL(x: Float): Boolean {
        return java.lang.Float.floatToIntBits(x) and 0x7f800000 == 0x00000000 && java.lang.Float.floatToIntBits(x) and 0x007fffff != 0x00000000
    }

    fun MaxIndex(x: Float, y: Float): Int {
        return if (x > y) 0 else 1
    }

    fun MinIndex(x: Float, y: Float): Int {
        return if (x < y) 0 else 1
    }

    fun Max3(x: Float, y: Float, z: Float): Float {
        return if (x > y) if (x > z) x else z else if (y > z) y else z
    }

    fun Min3(x: Float, y: Float, z: Float): Float {
        return if (x < y) if (x < z) x else z else if (y < z) y else z
    }

    fun Max3Index(x: Float, y: Float, z: Float): Int {
        return if (x > y) if (x > z) 0 else 2 else if (y > z) 1 else 2
    }

    fun Min3Index(x: Float, y: Float, z: Float): Int {
        return if (x < y) if (x < z) 0 else 2 else if (y < z) 1 else 2
    }

    fun Sign(f: Float): Int {
        return if (f > 0) 1 else if (f < 0) -1 else 0
    }

    fun Square(x: Float): Float { //FUCKME: promoting float to double!
        return x * x
    }

    fun Cube(x: Float): Float {
        return x * x * x
    }

    object idMath {
        const val EXP_BIAS = 127
        const val EXP_POS = 23
        const val FLT_EPSILON = 1.192092896e-07f // smallest positive number such that 1.0+FLT_EPSILON != 1.0
        const val INFINITY = 1e30f // huge number which should be larger than any valid number used

        //	enum {
        const val LOOKUP_BITS = 8
        const val LOOKUP_POS = EXP_POS - LOOKUP_BITS
        const val M_MS2SEC = 0.001f // milliseconds to seconds multiplier
        const val PI = 3.14159265358979323846f // pi

        //TODO:radians?
        const val M_DEG2RAD = PI / 180.0f // degrees to radians multiplier
        const val M_RAD2DEG = 180.0f / PI // radians to degrees multiplier
        const val SEED_POS = EXP_POS - 8
        const val SQRT_1OVER2 = 0.70710678118654752440f // sqrt( 1 / 2 )
        const val SQRT_TABLE_SIZE = 2 shl LOOKUP_BITS
        const val LOOKUP_MASK = SQRT_TABLE_SIZE - 1
        private val iSqrt: IntArray = IntArray(SQRT_TABLE_SIZE)
        const val TWO_PI = 2.0f * PI // pi * 2
        const val E = 2.71828182845904523536f // e
        const val HALF_PI = 0.5f * PI // pi / 2
        const val M_SEC2MS = 1000.0f // seconds to milliseconds multiplier
        const val ONEFOURTH_PI = 0.25f * PI // pi / 4
        const val SQRT_1OVER3 = 0.57735026918962576450f // sqrt( 1 / 3 )
        const val SQRT_THREE = 1.73205080756887729352f // sqrt( 3 )
        const val SQRT_TWO = 1.41421356237309504880f // sqrt( 2 )
        private var initialized = false
        fun Init() {
            var fi: _flint
            var fo: _flint
            for (i in 0 until SQRT_TABLE_SIZE) {
                fi = _flint(EXP_BIAS - 1 shl EXP_POS or (i shl LOOKUP_POS))
                fo = _flint((1.0 / sqrt(fi.f.toDouble())).toFloat())
                iSqrt[i] = (fo.i + (1 shl SEED_POS - 2) shr SEED_POS and 0xFF) shl SEED_POS
            }
            iSqrt[SQRT_TABLE_SIZE / 2] = 0xFF shl SEED_POS
            initialized = true
        }

        fun RSqrt(x: Float): Float { // reciprocal square root, returns huge number when x == 0.0
            var i: Long
            val y: Float
            var r: Float
            y = x * 0.5f
            //	i = *reinterpret_cast<long *>( &x );
            i = java.lang.Float.floatToIntBits(x).toLong()
            i = 0x5f3759df - (i shr 1)
            r = java.lang.Float.intBitsToFloat(i.toInt())
            r = r * (1.5f - r * r * y)
            return r
        }

        // inverse square root with 32 bits precision, returns huge number when x == 0.0
        fun InvSqrt(x: Float): Float {

//	long  a = ((union _flint*)(&x))->i;
            val seed = _flint(x)
            val a = seed.getInt()
            assert(initialized)
            val y = (x * 0.5f).toDouble()
            seed.setInt(
                3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS
                        or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK]
            )
            var r = seed.f.toDouble()
            r = r * (1.5f - r * r * y)
            r = r * (1.5f - r * r * y)
            return r.toFloat()
        }

        fun InvSqrt16(x: Float): Float { // inverse square root with 16 bits precision, returns huge number when x == 0.0
            val seed = _flint(x)
            val a = seed.getInt()
            assert(initialized)
            val y = (x * 0.5f).toDouble()
            seed.setInt(3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK])
            var r = seed.f.toDouble()
            r = r * (1.5f - r * r * y)
            return r.toFloat()
        }

        fun InvSqrt64(x: Float): Double { // inverse square root with 64 bits precision, returns huge number when x == 0.0
            val seed = _flint(x)
            val a = seed.getInt()
            assert(initialized)
            val y = (x * 0.5f).toDouble()
            seed.setInt(3 * EXP_BIAS - 1 - (a shr EXP_POS and 0xFF) shr 1 shl EXP_POS or iSqrt[a shr EXP_POS - LOOKUP_BITS and LOOKUP_MASK])
            var r = seed.f.toDouble()
            r = r * (1.5f - r * r * y)
            r = r * (1.5f - r * r * y)
            r = r * (1.5f - r * r * y)
            return r
        }

        fun Sqrt(x: Float): Float { // square root with 32 bits precision
            return x * InvSqrt(x)
        }

        fun Sqrt16(x: Float): Float { // square root with 16 bits precision
            return x * InvSqrt16(x)
        }

        fun Sqrt64(x: Float): Double { // square root with 64 bits precision
            return x * InvSqrt64(x)
        }

        fun Sin(a: Float): Float {
            return sin(a.toDouble()).toFloat()
        } // sine with 32 bits precision

        fun Sin16(a: Float): Float { // sine with 16 bits precision, maximum absolute error is 2.3082e-09
            var a = a
            val s: Float
            if (a < 0.0f || a >= TWO_PI) {
//		a -= floorf( a / TWO_PI ) * TWO_PI;
                a -= (floor((a / TWO_PI).toDouble()) * TWO_PI).toFloat()
            }
            //#if 1
            if (a < PI) {
                if (a > HALF_PI) {
                    a = PI - a
                }
            } else {
                a = if (a > PI + HALF_PI) {
                    a - TWO_PI
                } else {
                    PI - a
                }
            }
            //#else
//	a = PI - a;
//	if ( fabs( a ) >= HALF_PI ) {
//		a = ( ( a < 0.0f ) ? -PI : PI ) - a;
//	}
//#endif
            s = a * a
            return a * (((((-2.39e-08f * s + 2.7526e-06f) * s - 1.98409e-04f) * s + 8.3333315e-03f) * s - 1.666666664e-01f) * s + 1.0f)
        }

        fun Sin64(a: Float): Double {
            return sin(a.toDouble())
        } // sine with 64 bits precision

        fun Cos(a: Float): Float {
            return cos(a.toDouble()).toFloat()
        } // cosine with 32 bits precision

        fun Cos16(a: Float): Float { // cosine with 16 bits precision, maximum absolute error is 2.3082e-09
            var a = a
            val s: Float
            val d: Float
            if (a < 0.0f || a >= TWO_PI) {
//		a -= floorf( a / TWO_PI ) * TWO_PI;
                a -= (floor((a / TWO_PI).toDouble()) * TWO_PI).toFloat()
            }
            //#if 1
            if (a < PI) {
                if (a > HALF_PI) {
                    a = PI - a
                    d = -1.0f
                } else {
                    d = 1.0f
                }
            } else {
                if (a > PI + HALF_PI) {
                    a = a - TWO_PI
                    d = 1.0f
                } else {
                    a = PI - a
                    d = -1.0f
                }
            }
            //#else
//	a = PI - a;
//	if ( fabs( a ) >= HALF_PI ) {
//		a = ( ( a < 0.0f ) ? -PI : PI ) - a;
//		d = 1.0f;
//	} else {
//		d = -1.0f;
//	}
//#endif
            s = a * a
            return d * (((((-2.605e-07f * s + 2.47609e-05f) * s - 1.3888397e-03f) * s + 4.16666418e-02f) * s - 4.999999963e-01f) * s + 1.0f)
        }

        fun Cos64(a: Float): Double {
            return cos(a.toDouble())
        } // cosine with 64 bits precision

        fun SinCos(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 32 bits precision
//#ifdef _WIN32//i wish.
//	_asm {
//		fld		a
//		fsincos
//		mov		ecx, c
//		mov		edx, s
//		fstp	dword ptr [ecx]
//		fstp	dword ptr [edx]
//	}
//#else
            s._val = (sin(a.toDouble()).toFloat())
            c._val = (cos(a.toDouble()).toFloat())
            //#endif
        }

        fun SinCos16(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 16 bits precision
            var a = a
            val t: Float
            val d: Float
            if (a < 0.0f || a >= TWO_PI) {
//		a -= floorf( a / idMath::TWO_PI ) * idMath::TWO_PI;
                a -= (floor((a / TWO_PI).toDouble()) * TWO_PI).toFloat()
            }
            //#if 1
            if (a < PI) {
                if (a > HALF_PI) {
                    a = PI - a
                    d = -1.0f
                } else {
                    d = 1.0f
                }
            } else {
                if (a > PI + HALF_PI) {
                    a = a - TWO_PI
                    d = 1.0f
                } else {
                    a = PI - a
                    d = -1.0f
                }
            }
            //#else
//	a = PI - a;
//	if ( fabs( a ) >= HALF_PI ) {
//		a = ( ( a < 0.0f ) ? -PI : PI ) - a;
//		d = 1.0f;
//	} else {
//		d = -1.0f;
//	}
//#endif
            t = a * a
            s._val =
                (a * (((((-2.39e-08f * t + 2.7526e-06f) * t - 1.98409e-04f) * t + 8.3333315e-03f) * t - 1.666666664e-01f) * t + 1.0f))
            c._val =
                (d * (((((-2.605e-07f * t + 2.47609e-05f) * t - 1.3888397e-03f) * t + 4.16666418e-02f) * t - 4.999999963e-01f) * t + 1.0f))
        }

        fun SinCos64(a: Float, s: CFloat, c: CFloat) { // sine and cosine with 64 bits precision
//#ifdef _WIN32
//	_asm {
//		fld		a
//		fsincos
//		mov		ecx, c
//		mov		edx, s
//		fstp	qword ptr [ecx]
//		fstp	qword ptr [edx]
//	}
//#else
            s._val = (sin(a.toDouble()).toFloat())
            c._val = (cos(a.toDouble()).toFloat())
            //#endif
        }

        fun Tan(a: Float): Float { // tangent with 32 bits precision
            return tan(a.toDouble()).toFloat()
        }

        fun Tan16(a: Float): Float { // tangent with 16 bits precision, maximum absolute error is 1.8897e-08
            var a = a
            var s: Float
            val reciprocal: Boolean
            if (a < 0.0f || a >= PI) {
//		a -= floorf( a / PI ) * PI;
                a -= (floor((a / PI).toDouble()) * PI).toFloat()
            }
            //#if 1
            if (a < HALF_PI) {
                if (a > ONEFOURTH_PI) {
                    a = HALF_PI - a
                    reciprocal = true
                } else {
                    reciprocal = false
                }
            } else {
                if (a > HALF_PI + ONEFOURTH_PI) {
                    a = a - PI
                    reciprocal = false
                } else {
                    a = HALF_PI - a
                    reciprocal = true
                }
            }
            //#else
//	a = HALF_PI - a;
//	if ( fabs( a ) >= ONEFOURTH_PI ) {
//		a = ( ( a < 0.0f ) ? -HALF_PI : HALF_PI ) - a;
//		reciprocal = false;
//	} else {
//		reciprocal = true;
//	}
//#endif
            s = a * a
            s =
                a * ((((((9.5168091e-03f * s + 2.900525e-03f) * s + 2.45650893e-02f) * s + 5.33740603e-02f) * s + 1.333923995e-01f) * s + 3.333314036e-01f) * s + 1.0f)
            return if (reciprocal) {
                1.0f / s
            } else {
                s
            }
        }

        fun Tan64(a: Float): Double { // tangent with 64 bits precision
            return tan(a.toDouble())
        }

        fun ASin(a: Float): Float { // arc sine with 32 bits precision, input is clamped to [-1, 1] to avoid a silent NaN
            if (a <= -1.0f) {
                return -HALF_PI
            }
            return if (a >= 1.0f) {
                HALF_PI
            } else asin(a.toDouble()).toFloat()
        }

        fun ASin16(a: Float): Float { // arc sine with 16 bits precision, maximum absolute error is 6.7626e-05
            var a = a
            return if (1 == Math_h.FLOATSIGNBITSET(a)) {
                if (a <= -1.0f) {
                    return -HALF_PI
                }
                a = abs(a)
                ((((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a).toDouble()) - HALF_PI).toFloat()
            } else {
                if (a >= 1.0f) {
                    HALF_PI
                } else (HALF_PI - (((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a).toDouble())).toFloat()
            }
        }

        fun ASin64(a: Float): Float { // arc sine with 64 bits precision
            if (a <= -1.0f) {
                return -HALF_PI
            }
            return if (a >= 1.0f) {
                HALF_PI
            } else sin(a.toDouble()).toFloat()
        }

        fun ACos(a: Float): Float { // arc cosine with 32 bits precision, input is clamped to [-1, 1] to avoid a silent NaN
            if (a <= -1.0f) {
                return PI
            }
            return if (a >= 1.0f) {
                0.0f
            } else acos(a.toDouble()).toFloat()
        }

        fun ACos16(a: Float): Float { // arc cosine with 16 bits precision, maximum absolute error is 6.7626e-05
            var a = a
            return if (1 == Math_h.FLOATSIGNBITSET(a)) {
                if (a <= -1.0f) {
                    return PI
                }
                a = abs(a)
                (PI - (((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a).toDouble())).toFloat()
            } else {
                if (a >= 1.0f) {
                    0.0f
                } else ((((-0.0187293f * a + 0.0742610f) * a - 0.2121144f) * a + 1.5707288f) * sqrt((1.0f - a).toDouble())).toFloat()
            }
        }

        fun ACos64(a: Float): Float { // arc cosine with 64 bits precision
            if (a <= -1.0f) {
                return PI
            }
            return if (a >= 1.0f) {
                0.0f
            } else acos(a.toDouble()).toFloat()
        }

        fun ATan(a: Float): Float { // arc tangent with 32 bits precision
            return atan(a.toDouble()).toFloat()
        }

        fun ATan16(a: Float): Float { // arc tangent with 16 bits precision, maximum absolute error is 1.3593e-08
            var a = a
            var s: Float
            return if (abs(a) > 1.0f) {
                a = 1.0f / a
                s = a * a
                s = -((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                        * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
                if (1 == Math_h.FLOATSIGNBITSET(a)) {
                    s - HALF_PI
                } else {
                    s + HALF_PI
                }
            } else {
                s = a * a
                ((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                        * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
            }
        }

        fun ATan64(a: Float): Double { // arc tangent with 64 bits precision
            return atan(a.toDouble())
        }

        fun ATan(y: Float, x: Float): Float { // arc tangent with 32 bits precision
            return atan2(y.toDouble(), x.toDouble()).toFloat()
        }

        fun ATan16(
            y: Float,
            x: Float
        ): Float { // arc tangent with 16 bits precision, maximum absolute error is 1.3593e-08
            val a: Float
            var s: Float
            return if (abs(y) > abs(x)) {
                a = x / y
                s = a * a
                s = -((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                        * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
                if (1 == Math_h.FLOATSIGNBITSET(a)) {
                    s - HALF_PI
                } else {
                    s + HALF_PI
                }
            } else {
                a = y / x
                s = a * a
                ((((((((0.0028662257f * s - 0.0161657367f) * s + 0.0429096138f) * s - 0.0752896400f)
                        * s + 0.1065626393f) * s - 0.1420889944f) * s + 0.1999355085f) * s - 0.3333314528f) * s + 1.0f) * a
            }
        }

        fun ATan64(y: Float, x: Float): Double { // arc tangent with 64 bits precision
            return atan2(y.toDouble(), x.toDouble())
        }

        fun Pow(x: Float, y: Float): Float { // x raised to the power y with 32 bits precision
            return x.toDouble().pow(y.toDouble()).toFloat()
        }

        fun Pow16(x: Float, y: Float): Float { // x raised to the power y with 16 bits precision
            return Exp16(y * Log16(x))
        }

        fun Pow64(x: Float, y: Float): Double { // x raised to the power y with 64 bits precision
            return x.toDouble().pow(y.toDouble())
        }

        fun Exp(f: Float): Float { // e raised to the power f with 32 bits precision
            return exp(f.toDouble()).toFloat()
        }

        fun Exp16(f: Float): Float { // e raised to the power f with 16 bits precision
            var i: Int
            val s: Int
            val e: Int
            val m: Int
            val exponent: Int
            var x: Float
            val x2: Float
            var y: Float
            val p: Float
            val q: Float
            x = f * 1.44269504088896340f // multiply with ( 1 / log( 2 ) )
            //#if 1
//	i = *reinterpret_cast<int *>(&x);
            i = java.lang.Float.floatToIntBits(x)
            s = i shr Math_h.IEEE_FLT_SIGN_BIT
            e =
                (i shr Math_h.IEEE_FLT_MANTISSA_BITS and (1 shl Math_h.IEEE_FLT_EXPONENT_BITS) - 1) - Math_h.IEEE_FLT_EXPONENT_BIAS
            m = i and (1 shl Math_h.IEEE_FLT_MANTISSA_BITS) - 1 or (1 shl Math_h.IEEE_FLT_MANTISSA_BITS)
            i = m shr Math_h.IEEE_FLT_MANTISSA_BITS - e and (e shr 31).inv() xor s
            //#else
//	i = (int) x;
//	if ( x < 0.0f ) {
//		i--;
//	}
//#endif
            exponent = i + Math_h.IEEE_FLT_EXPONENT_BIAS shl Math_h.IEEE_FLT_MANTISSA_BITS
            //	y = *reinterpret_cast<float *>(&exponent);
            y = java.lang.Float.intBitsToFloat(exponent)
            x -= i.toFloat()
            if (x >= 0.5f) {
                x -= 0.5f
                y *= 1.4142135623730950488f // multiply with sqrt( 2 )
            }
            x2 = x * x
            p = x * (7.2152891511493f + x2 * 0.0576900723731f)
            q = 20.8189237930062f + x2
            x = y * (q + p) / (q - p)
            return x
        }

        fun Exp64(f: Float): Double { // e raised to the power f with 64 bits precision
            return exp(f.toDouble())
        }

        fun Log(f: Float): Float { // natural logarithm with 32 bits precision
            return ln(f.toDouble()).toFloat()
        }

        fun Log16(f: Float): Float { // natural logarithm with 16 bits precision
            var i: Int
            val exponent: Int
            var y: Float
            val y2: Float

//	i = *reinterpret_cast<int *>(&f);
            i = java.lang.Float.floatToIntBits(f)
            exponent =
                (i shr Math_h.IEEE_FLT_MANTISSA_BITS and (1 shl Math_h.IEEE_FLT_EXPONENT_BITS) - 1) - Math_h.IEEE_FLT_EXPONENT_BIAS
            i -= exponent + 1 shl Math_h.IEEE_FLT_MANTISSA_BITS // get value in the range [.5, 1>
            //	y = *reinterpret_cast<float *>(&i);
            y = java.lang.Float.intBitsToFloat(i)
            y *= 1.4142135623730950488f // multiply with sqrt( 2 )
            y = (y - 1.0f) / (y + 1.0f)
            y2 = y * y
            y =
                y * (2.000000000046727f + y2 * (0.666666635059382f + y2 * (0.4000059794795f + y2 * (0.28525381498f + y2 * 0.2376245609f))))
            y += 0.693147180559945f * (exponent.toFloat() + 0.5f)
            return y
        }

        fun Log64(f: Float): Double { // natural logarithm with 64 bits precision
            return ln(f.toDouble())
        }

        fun IPow(x: Int, y: Int): Int { // integral x raised to the power y
            var y = y
            var r: Int
            r = x
            while (y > 1) {
                r *= x
                y--
            }
            return r
        }

        fun ILog2(f: Float): Int { // integral base-2 logarithm of the floating point value
//	return ( ( (*reinterpret_cast<int *>(&f)) >> IEEE_FLT_MANTISSA_BITS ) & ( ( 1 << IEEE_FLT_EXPONENT_BITS ) - 1 ) ) - IEEE_FLT_EXPONENT_BIAS;
            return (java.lang.Float.floatToIntBits(f) shr Math_h.IEEE_FLT_MANTISSA_BITS and (1 shl Math_h.IEEE_FLT_EXPONENT_BITS) - 1) - Math_h.IEEE_FLT_EXPONENT_BIAS
        }

        fun ILog2(i: Int): Int { // integral base-2 logarithm of the integer value
            return ILog2(i.toFloat())
        }

        fun BitsForFloat(f: Float): Int { // minumum number of bits required to represent ceil( f )
            return ILog2(f) + 1
        }

        fun BitsForInteger(i: Int): Int { // minumum number of bits required to represent i
            return ILog2(i.toFloat()) + 1
        }

        fun MaskForFloatSign(f: Float): Int { // returns 0x00000000 if x >= 0.0f and returns 0xFFFFFFFF if x <= -0.0f
//	return ( (*reinterpret_cast<int *>(&f)) >> 31 );
            return java.lang.Float.floatToIntBits(f) shr 31
        }

        fun MaskForIntegerSign(i: Int): Int { // returns 0x00000000 if x >= 0 and returns 0xFFFFFFFF if x < 0
            return i shr 31
        }

        fun FloorPowerOfTwo(x: Int): Int { // round x down to the nearest power of 2
            return CeilPowerOfTwo(x) shr 1
        }

        fun CeilPowerOfTwo(x: Int): Int { // round x up to the nearest power of 2
            var x = x
            x--
            x = x or (x shr 1)
            x = x or (x shr 2)
            x = x or (x shr 4)
            x = x or (x shr 8)
            x = x or (x shr 16)
            x++
            return x
        }

        fun IsPowerOfTwo(x: Int): Boolean { // returns true if x is a power of 2
            return x and x - 1 == 0 && x > 0
        }

        fun BitCount(x: Int): Int { // returns the number of 1 bits in x
            var x = x
            x -= x shr 1 and 0x55555555
            x = (x shr 2 and 0x33333333) + (x and 0x33333333)
            x = (x shr 4) + x and 0x0f0f0f0f
            x += x shr 8
            return x + (x shr 16) and 0x0000003f
        }

        fun BitReverse(x: Int): Int { // returns the bit reverse of x
            var x = x
            x = x shr 1 and 0x55555555 or (x and 0x55555555 shl 1)
            x = x shr 2 and 0x33333333 or (x and 0x33333333 shl 2)
            x = x shr 4 and 0x0f0f0f0f or (x and 0x0f0f0f0f shl 4)
            x = x shr 8 and 0x00ff00ff or (x and 0x00ff00ff shl 8)
            return x shr 16 or (x shl 16)
        }

        fun Abs(x: Int): Int { // returns the absolute value of the integer value (for reference only)
            val y = x shr 31
            return (x xor y) - y
        }

        fun Floor(f: Float): Float { // returns the largest integer that is less than or equal to the given value
            return floor(f.toDouble()).toFloat()
        }

        fun Ceil(f: Float): Float { // returns the smallest integer that is greater than or equal to the given value
            return ceil(f.toDouble()).toFloat()
        }

        fun Rint(f: Float): Float { // returns the nearest integer
            return floor((f + 0.5f).toDouble()).toFloat()
        }

        fun Ftoi(f: Float): Int { // float to int conversion
            return f.toInt()
        }

        // fast float to int conversion but uses current FPU round mode (default round nearest)
        fun FtoiFast(f: Float): Int {
//#ifdef _WIN32
//	int i;
//	__asm fld		f
//	__asm fistp		i		// use default rouding mode (round nearest)
//	return i;
//#elif 0						// round chop (C/C++ standard)
//            int i, s, e, m, shift;
//            i = Float.floatToIntBits(f);//*reinterpret_cast<int *>(&f);
//            s = i >> IEEE_FLT_SIGN_BIT;
//            e = ((i >> IEEE_FLT_MANTISSA_BITS) & ((1 << IEEE_FLT_EXPONENT_BITS) - 1)) - IEEE_FLT_EXPONENT_BIAS;
//            m = (i & ((1 << IEEE_FLT_MANTISSA_BITS) - 1)) | (1 << IEEE_FLT_MANTISSA_BITS);
//            shift = e - IEEE_FLT_MANTISSA_BITS;
//            return ((((m >> -shift) | (m << shift)) & ~(e >> 31)) ^ s) - s;
            return f.roundToInt() //TODO:fix the C++ function.
            //#elif defined( __i386__ )
//#elif 0
//	int i = 0;
//	__asm__ __volatile__ (
//						  "fld %1\n" \
//						  "fistp %0\n" \
//						  : "=m" (i) \
//						  : "m" (f) );
//	return i;
//#else
//	return (int) f;
//#endif
        }

        fun Ftol(f: Float): Long { // float to long conversion
            return f.toLong()
        }

        fun FtolFast(f: Float): Int { // fast float to long conversion but uses current FPU round mode (default round nearest)
//#ifdef _WIN32
//	// FIXME: this overflows on 31bits still .. same as FtoiFast
//	unsigned long i;
//	__asm fld		f
//	__asm fistp		i		// use default rouding mode (round nearest)
//	return i;
//#elif 0						// round chop (C/C++ standard)
            val i: Int
            val s: Int
            val e: Int
            val m: Int
            val shift: Int
            //	i = *reinterpret_cast<int *>(&f);
            i = java.lang.Float.floatToIntBits(f)
            s = i shr Math_h.IEEE_FLT_SIGN_BIT
            e =
                (i shr Math_h.IEEE_FLT_MANTISSA_BITS and (1 shl Math_h.IEEE_FLT_EXPONENT_BITS) - 1) - Math_h.IEEE_FLT_EXPONENT_BIAS
            m = i and (1 shl Math_h.IEEE_FLT_MANTISSA_BITS) - 1 or (1 shl Math_h.IEEE_FLT_MANTISSA_BITS)
            shift = e - Math_h.IEEE_FLT_MANTISSA_BITS
            return (m shr -shift or (m shl shift) and (e shr 31).inv() xor s) - s
            //#elif defined( __i386__ )
//#elif 0
//	// for some reason, on gcc I need to make sure i == 0 before performing a fistp
//	int i = 0;
//	__asm__ __volatile__ (
//						  "fld %1\n" \
//						  "fistp %0\n" \
//						  : "=m" (i) \
//						  : "m" (f) );
//	return i;
//#else
//	return (unsigned long) f;
//#endif
        }

        fun ClampChar(i: Int): Char {
            if (i < -128) { //goddamn unsigned char!!
                return Char(-128)
            }
            return if (i > 127) {
                127.toChar()
            } else i.toChar()
        }

        fun ClampShort(i: Int): Short { //TODO:signed
            if (i < -32768) {
                return -32768
            }
            return if (i > 32767) {
                32767
            } else i.toShort()
        }

        fun ClampInt(min: Int, max: Int, value: Int): Int {
            if (value < min) {
                return min
            }
            return if (value > max) {
                max
            } else value
        }

        fun ClampFloat(min: Float, max: Float, value: Float): Float {
            if (value < min) {
                return min
            }
            return if (value > max) {
                max
            } else value
        }

        fun AngleNormalize360(angle: Float): Float {
            var angle = angle
            if (angle >= 360.0f || angle < 0.0f) {
                angle -= (floor((angle / 360.0f).toDouble()) * 360.0f).toFloat()
            }
            return angle
        }

        fun AngleNormalize180(angle: Float): Float {
            var angle = angle
            angle = AngleNormalize360(angle)
            if (angle > 180.0f) {
                angle -= 360.0f
            }
            return angle
        }

        fun AngleDelta(angle1: Float, angle2: Float): Float {
            return AngleNormalize180(angle1 - angle2)
        }

        fun FloatToBits(f: Float, exponentBits: Int, mantissaBits: Int): Int {
            var exponentBits = exponentBits
            val i: Int
            val sign: Int
            val exponent: Int
            val mantissa: Int
            var value: Int
            assert(exponentBits >= 2 && exponentBits <= 8)
            assert(mantissaBits >= 2 && mantissaBits <= 23)
            val maxBits = (1 shl exponentBits - 1) - 1 shl mantissaBits or (1 shl mantissaBits) - 1
            val minBits = (1 shl exponentBits) - 2 shl mantissaBits or 1
            val max = BitsToFloat(maxBits, exponentBits, mantissaBits)
            val min = BitsToFloat(minBits, exponentBits, mantissaBits)
            if (f >= 0.0f) {
                if (f >= max) {
                    return maxBits
                } else if (f <= min) {
                    return minBits
                }
            } else {
                if (f <= -max) {
                    return maxBits or (1 shl exponentBits + mantissaBits)
                } else if (f >= -min) {
                    return minBits or (1 shl exponentBits + mantissaBits)
                }
            }
            exponentBits--
            //	i = *reinterpret_cast<int *>(&f);
            i = java.lang.Float.floatToIntBits(f)
            sign = i shr Math_h.IEEE_FLT_SIGN_BIT and 1
            exponent =
                (i shr Math_h.IEEE_FLT_MANTISSA_BITS and (1 shl Math_h.IEEE_FLT_EXPONENT_BITS) - 1) - Math_h.IEEE_FLT_EXPONENT_BIAS
            mantissa = i and (1 shl Math_h.IEEE_FLT_MANTISSA_BITS) - 1
            value = sign shl 1 + exponentBits + mantissaBits
            value =
                value or (Math_h.INTSIGNBITSET(exponent) shl exponentBits or (abs(exponent) and (1 shl exponentBits) - 1) shl mantissaBits)
            value = value or (mantissa shr Math_h.IEEE_FLT_MANTISSA_BITS - mantissaBits)
            return value
        }

        //	};
        fun BitsToFloat(i: Int, exponentBits: Int, mantissaBits: Int): Float {
            var exponentBits = exponentBits
            val exponentSign = intArrayOf(1, -1)
            val sign: Int
            val exponent: Int
            val mantissa: Int
            val value: Int
            assert(exponentBits >= 2 && exponentBits <= 8)
            assert(mantissaBits >= 2 && mantissaBits <= 23)
            exponentBits--
            sign = i shr 1 + exponentBits + mantissaBits
            exponent =
                (i shr mantissaBits and (1 shl exponentBits) - 1) * exponentSign[i shr exponentBits + mantissaBits and 1]
            mantissa = i and (1 shl mantissaBits) - 1 shl Math_h.IEEE_FLT_MANTISSA_BITS - mantissaBits
            value =
                sign shl Math_h.IEEE_FLT_SIGN_BIT or (exponent + Math_h.IEEE_FLT_EXPONENT_BIAS shl Math_h.IEEE_FLT_MANTISSA_BITS) or mantissa
            //	return *reinterpret_cast<float *>(&value);
            return java.lang.Float.intBitsToFloat(value)
        }

        fun FloatHash(array: FloatArray, numFloats: Int): Int {
            var i: Int
            var hash = 0
            //	const int *ptr;

//	ptr = reinterpret_cast<const int *>( array );
            i = 0
            while (i < numFloats) {

//		hash ^= ptr[i];
                hash = hash xor java.lang.Float.floatToIntBits(array[i])
                i++
            }
            return hash
        }

        internal class _flint {
            var f = 0f
            var i = 0

            constructor(i: Int) {
                setInt(i)
            }

            constructor(f: Float) {
                setFloat(f)
            }

            fun getInt(): Int {
                return i
            }

            fun setInt(i: Int) {
                this.i = i
                f = java.lang.Float.intBitsToFloat(i)
            }

            fun getFloat(): Float {
                return f
            }

            fun setFloat(f: Float) {
                this.f = f
                i = java.lang.Float.floatToIntBits(f)
            }
        }
    }
}