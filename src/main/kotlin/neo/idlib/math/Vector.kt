package neo.idlib.math

import neo.TempDump.SERiAL
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Rotation.idRotation
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.*

/**
 *
 */
object Vector {
    private val vec2_origin: idVec2 = idVec2(0.0f, 0.0f)


    val vec3_origin: idVec3 = idVec3(0.0f, 0.0f, 0.0f)
    private val vec3_zero: idVec3 = getVec3Origin()
    private val vec4_origin: idVec4 = idVec4(0.0f, 0.0f, 0.0f, 0.0f)
    private val vec4_zero: idVec4 = vec4_origin
    private val vec5_origin: idVec5 = idVec5(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    private val vec6_infinity: idVec6 =
        idVec6(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY, idMath.INFINITY)
    private val vec6_origin: idVec6 = idVec6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    private val vec6_zero: idVec6 = vec6_origin

    @Deprecated("")
    fun RAD2DEG(a: Double): Float {
        return a.toFloat() * idMath.M_RAD2DEG
    }

    fun RAD2DEG(a: Float): Float {
        return a * idMath.M_RAD2DEG
    }

    fun getVec2_origin(): idVec2 {
        return idVec2(vec2_origin)
    }

    fun getVec3Origin(): idVec3 {
        return idVec3(0.0f, 0.0f, 0.0f)
    }


    fun getVec3_zero(): idVec3 {
        return idVec3(vec3_zero)
    }

    fun getVec4_origin(): idVec4 {
        return idVec4(vec4_origin)
    }

    fun getVec4_zero(): idVec4 {
        return idVec4(vec4_zero)
    }

    fun getVec5_origin(): idVec5 {
        return idVec5(vec5_origin)
    }

    fun getVec6_origin(): idVec6 {
        return idVec6(vec6_origin.p)
    }

    fun getVec6_zero(): idVec6 {
        return idVec6(vec6_zero.p)
    }

    fun getVec6_infinity(): idVec6 {
        return idVec6(vec6_infinity.p)
    }

    /*
     ===============================================================================

     Old 3D vector macros, should no longer be used.

     ===============================================================================
     */

    fun DotProduct(a: DoubleArray, b: DoubleArray): Double {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }


    fun DotProduct(a: FloatArray, b: FloatArray): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }


    fun DotProduct(a: idVec3, b: idVec3): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }


    fun DotProduct(a: idVec3, b: idVec4): Float {
        return DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idVec3, b: idVec5): Float {
        return DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idPlane, b: idPlane): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }


    fun VectorSubtract(a: DoubleArray, b: DoubleArray, c: DoubleArray): DoubleArray {
        c[0] = a[0] - b[0]
        c[1] = a[1] - b[1]
        c[2] = a[2] - b[2]
        return c
    }


    fun VectorSubtract(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
        c[0] = a[0] - b[0]
        c[1] = a[1] - b[1]
        c[2] = a[2] - b[2]
        return c
    }


    fun VectorSubtract(a: idVec3, b: idVec3, c: FloatArray): FloatArray {
        c[0] = a[0] - b[0]
        c[1] = a[1] - b[1]
        c[2] = a[2] - b[2]
        return c
    }

    fun VectorSubtract(a: idVec3, b: idVec3, c: idVec3): idVec3 {
        c[0] = a[0] - b[0]
        c[1] = a[1] - b[1]
        c[2] = a[2] - b[2]
        return c
    }

    fun VectorAdd(a: DoubleArray, b: DoubleArray, c: Array<Double>) {
        c[0] = a[0] + b[0]
        c[1] = a[1] + b[1]
        c[2] = a[2] + b[2]
    }

    fun VectorScale(v: DoubleArray, s: Double, o: Array<Double>) {
        o[0] = v[0] * s
        o[1] = v[1] * s
        o[2] = v[2] * s
    }


    fun VectorMA(v: DoubleArray, s: Double, b: DoubleArray, o: Array<Double>) {
        o[0] = v[0] + b[0] * s
        o[1] = v[1] + b[1] * s
        o[2] = v[2] + b[2] * s
    }


    fun VectorMA(v: idVec3, s: Float, b: idVec3, o: idVec3) {
        o[0] = v[0] + b[0] * s
        o[1] = v[1] + b[1] * s
        o[2] = v[2] + b[2] * s
    }

    fun VectorCopy(a: DoubleArray, b: Array<Double>) {
        b[0] = a[0]
        b[1] = a[1]
        b[2] = a[2]
    }

    fun VectorCopy(a: idVec3, b: idVec3) {
        b.set(a)
    }

    fun VectorCopy(a: idVec3, b: idVec5) {
        b.set(a)
    }

    fun VectorCopy(a: idVec5, b: idVec3) {
        b.set(a.ToVec3())
    }

    interface idVec<T : idVec<T>> {
        //reflection was too slow.
        //never thought I would say this, but thank God for type erasure.
        operator fun get(index: Int): Float
        fun set(a: T): T
        operator fun set(index: Int, value: Float): Float
        operator fun plus(a: T): T
        operator fun minus(a: T): T
        operator fun div(a: Int): T // used in idlib/math/Plane
        operator fun times(a: T): Float
        operator fun times(a: Float): T
        operator fun times(a: Int): T // used in idlib/math/Curve
        operator fun div(a: Float): T
        fun plusAssign(a: T): T  // too bad kotlin's augmented assigns are Unit-only :(
        fun GetDimension(): Int
        fun Zero()
    }

    //===============================================================
    //
    //	idVec2 - 2D vector
    //
    //===============================================================
    class idVec2 : idVec<idVec2>, SERiAL {

        var x = 0f


        var y = 0f


        constructor(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        constructor(v: idVec2) {
            x = v.x
            y = v.y
        }

        constructor()

        fun set(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        override fun Zero() {
            y = 0.0f
            x = y
        }

        //public	float			operator[]( int index ) const;
        override fun set(index: Int, value: Float): Float {
            return if (index == 1) {
                value.also { y = it }
            } else {
                value.also { x = it }
            }
        }

        //public	float &			operator[]( int index );
        fun plusAssign(index: Int, value: Float): Float {
            return if (index == 1) {
                value.let { y += it; y }
            } else {
                value.let { x += it; x }
            }
        }

        //public	idVec2			operator-() const;
        override fun get(index: Int): Float {
            return if (index == 1) {
                y
            } else x
        }

        //public	float			operator*( const idVec2 &a ) const;
        override fun times(a: idVec2): Float {
            return x * a.x + y * a.y
        }

        //public	idVec2			operator/( const float a ) const;
        //public	idVec2			operator*( const float a ) const;
        override fun times(a: Float): idVec2 {
            return idVec2(x * a, y * a)
        }

        override fun times(a: Int): idVec2 {
            return idVec2(x * a, y * a)
        }

        override fun div(a: Float): idVec2 {
            val inva = 1.0f / a
            return idVec2(x * inva, y * inva)
        }

        //public	idVec2			operator+( const idVec2 &a ) const;
        override fun plus(a: idVec2): idVec2 {
            return idVec2(x + a.x, y + a.y)
        }

        //public	idVec2			operator-( const idVec2 &a ) const;
        override fun minus(a: idVec2): idVec2 {
            return idVec2(x - a.x, y - a.y)
        }

        override fun div(a: Int): idVec2 {
            return idVec2(x - a, y - a)
        }

        //public	idVec2 &		operator+=( const idVec2 &a );
        override fun plusAssign(a: idVec2): idVec2 {
            x += a.x
            y += a.y
            return this
        }

        //public	idVec2 &		operator/=( const idVec2 &a );
        //public	idVec2 &		operator/=( const float a );
        //public	idVec2 &		operator-=( const idVec2 &a );
        fun minusAssign(a: idVec2): idVec2 {
            x -= a.x
            y -= a.y
            return this
        }

        //public	idVec2 &		operator*=( const float a );
        fun timesAssign(a: Float): idVec2 {
            x *= a
            y *= a
            return this
        }

        //public	friend idVec2	operator*( const float a, const idVec2 b );
        override fun set(a: idVec2): idVec2 {
            x = a.x
            y = a.y
            return this
        }

        fun Compare(a: idVec2): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y
        }

        //public	bool			operator==(	const idVec2 &a ) const;						// exact compare, no epsilon
        //public	bool			operator!=(	const idVec2 &a ) const;						// exact compare, no epsilon
        fun Compare(a: idVec2, epsilon: Float): Boolean { // compare with epsilon
            return if (abs(x - a.x) > epsilon) {
                false
            } else abs(y - a.y) <= epsilon
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y)
        }

        fun LengthFast(): Float {
            val sqrLength: Float = x * x + y * y
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun LengthSqr(): Float {
            return x * x + y * y
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float = x * x + y * y
            val invLength: Float = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val lengthSqr: Float = x * x + y * y
            val invLength: Float = idMath.RSqrt(lengthSqr)
            x *= invLength
            y *= invLength
            return invLength * lengthSqr
        }

        fun Truncate(length: Float): idVec2 { // cap length
            val length2: Float
            val ilength: Float
            if (length == 0f) {
                Zero()
            } else {
                length2 = LengthSqr()
                if (length2 > length * length) {
                    ilength = length * idMath.InvSqrt(length2)
                    x *= ilength
                    y *= ilength
                }
            }
            return this
        }

        fun Clamp(min: idVec2, max: idVec2) {
            if (x < min.x) {
                x = min.x
            } else if (x > max.x) {
                x = max.x
            }
            if (y < min.y) {
                y = min.y
            } else if (y > max.y) {
                y = max.y
            }
        }

        fun Snap() { // snap to closest integer value
//            x = floor(x + 0.5f);
            x = floor((x + 0.5f).toDouble()).toFloat()
            y = floor((y + 0.5f).toDouble()).toFloat()
        }

        fun SnapInt() { // snap towards integer (floor)
            x = x.toInt().toFloat()
            y = y.toInt().toFloat()
        }

        override fun GetDimension(): Int {
            return 2
        }

        //public	float *			ToFloatPtr( void );
        fun ToFloatPtr(): FloatArray {
            return floatArrayOf(x, y)
        }


        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y"
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec2, v2: idVec2, l: Float) {
            if (l <= 0.0f) {
                this.set(v1) //( * this) = v1;
            } else if (l >= 1.0f) {
                this.set(v2) //( * this) = v2;
            } else {
                this.set(v1 + (v2 - v1) * l) //( * this) = v1 + l * (v2 - v1);
            }
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            val SIZE = 2 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE


            fun generateArray(length: Int): Array<idVec2> {
                return Array(length) { idVec2() }
            }
        }
    }

    //===============================================================
    //
    //	idVec3 - 3D vector
    //
    //===============================================================
    open class idVec3 : idVec<idVec3>, SERiAL {

        var x = 0f


        var y = 0f


        var z = 0f

        constructor()
        constructor(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        constructor(x: Int, y: Int, z: Int) {
            this.x = x.toFloat()
            this.y = y.toFloat()
            this.z = z.toFloat()
        }

        constructor(v: idVec3) {
            x = v.x
            y = v.y
            z = v.z
        }


        constructor(xyz: FloatArray, offset: Int = 0) {
            x = xyz[offset + 0]
            y = xyz[offset + 1]
            z = xyz[offset + 2]
        }

        fun set(x: Float, y: Float, z: Float) {
            this.x = x
            this.y = y
            this.z = z
        }

        override fun Zero() {
            z = 0.0f
            y = z
            x = y
        }

        //public	float			operator[]( final  int index ) final ;
        //public	float &			operator[]( final  int index );
        //public	idVec3			operator-() final ;
        operator fun unaryMinus(): idVec3 {
            return idVec3(-x, -y, -z)
        }

        //public	idVec3 &		operator=( final  idVec3 &a );		// required because of a msvc 6 & 7 bug
        override fun set(a: idVec3): idVec3 {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        fun set(a: idVec2): idVec3 {
            x = a.x
            y = a.y
            return this
        }

        //public	float			operator*( final  idVec3 &a ) final ;
        override fun times(a: idVec3): Float { // I have no idea why this should return "float" instead of idVec3
            return a.x * x + a.y * y + a.z * z
        }

        override fun times(a: Int): idVec3 {
            return idVec3(x * a, y * a, z * a)
        }

        fun timesVec(a: idVec3): idVec3 {
            return idVec3(x * a.x, y * a.y, z * a.z)
        }

        //public	idVec3			operator*( final  float a ) final ;
        override fun times(a: Float): idVec3 {
            return idVec3(x * a, y * a, z * a)
        }

        operator fun times(a: idMat3): idVec3 {
            return idVec3(
                a.getRow(0)[0] * x + a.getRow(1)[0] * y + a.getRow(2)[0] * z,
                a.getRow(0)[1] * x + a.getRow(1)[1] * y + a.getRow(2)[1] * z,
                a.getRow(0)[2] * x + a.getRow(1)[2] * y + a.getRow(2)[2] * z
            )
        }

        operator fun times(a: idRotation): idVec3 {
            return a * this
        }

        operator fun times(a: idMat4): idVec3 {
            return a * this
        }

        //public	idVec3			operator/( final  float a ) final ;
        override fun div(a: Float): idVec3 {
            val inva = 1.0f / a
            return idVec3(x * inva, y * inva, z * inva)
        }

        //public	idVec3			operator+( final  idVec3 &a ) final ;F
        override fun plus(a: idVec3): idVec3 {
            return idVec3(x + a.x, y + a.y, z + a.z)
        }

        //public	idVec3			operator-( final  idVec3 &a ) final ;
        override fun minus(a: idVec3): idVec3 {
            return idVec3(x - a.x, y - a.y, z - a.z)
        }

        override fun div(a: Int): idVec3 {
            return idVec3(x - a, y - a, z - a)
        }

        //public	idVec3 &		operator+=( final  idVec3 &a );
        override fun plusAssign(a: idVec3): idVec3 {
            x += a.x
            y += a.y
            z += a.z
            return this
        }

        //public	idVec3 &		operator-=( final  idVec3 &a );
        fun minusAssign(a: idVec3): idVec3 {
            x -= a.x
            y -= a.y
            z -= a.z
            return this
        }

        //public	idVec3 &		operator/=( final  idVec3 &a );
        fun divAssign(a: Float): idVec3 {
            x /= a
            y /= a
            z /= a
            return this
        }

        //public	idVec3 &		operator*=( final  float a );
        fun timesAssign(a: Float): idVec3 {
            x *= a
            y *= a
            z *= a
            return this
        }

        fun timesAssign(mat: idMat3): idVec3 {
            this.set(idMat3.timesAssign(this, mat))
            return this
        }

        //public	boolean			operator==(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        fun timesAssign(rotation: idRotation): idVec3 {
            this.set(rotation * this)
            return this
        }

        fun Compare(a: idVec3): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z
        }

        fun Compare(a: idVec3, epsilon: Float): Boolean { // compare with epsilon
            if (abs(x - a.x) > epsilon) {
                return false
            }
            return if (abs(y - a.y) > epsilon) {
                false
            } else abs(z - a.z) <= epsilon
        }

        //private idVec3  multiply(float a){
        //    return new idVec3( this.x * a, this.y * a, this.z * a );
        //}
        operator fun plus(a: Float): idVec3 {
            x += a
            y += a
            z += a
            return this
        }

        fun div(a: idVec3, b: Float): idVec3 {
            val invB = 1.0f / b
            return idVec3(a.x * b, a.y * b, a.z * b)
        }


        fun FixDegenerateNormal(): Boolean { // fix degenerate axial cases
            if (x == 0.0f) {
                if (y == 0.0f) {
                    if (z > 0.0f) {
                        if (z != 1.0f) {
                            z = 1.0f
                            return true
                        }
                    } else {
                        if (z != -1.0f) {
                            z = -1.0f
                            return true
                        }
                    }
                    return false
                } else if (z == 0.0f) {
                    if (y > 0.0f) {
                        if (y != 1.0f) {
                            y = 1.0f
                            return true
                        }
                    } else {
                        if (y != -1.0f) {
                            y = -1.0f
                            return true
                        }
                    }
                    return false
                }
            } else if (y == 0.0f) {
                if (z == 0.0f) {
                    if (x > 0.0f) {
                        if (x != 1.0f) {
                            x = 1.0f
                            return true
                        }
                    } else {
                        if (x != -1.0f) {
                            x = -1.0f
                            return true
                        }
                    }
                    return false
                }
            }
            if (abs(x) == 1.0f) {
                if (y != 0.0f || z != 0.0f) {
                    z = 0.0f
                    y = z
                    return true
                }
                return false
            } else if (abs(y) == 1.0f) {
                if (x != 0.0f || z != 0.0f) {
                    z = 0.0f
                    x = z
                    return true
                }
                return false
            } else if (abs(z) == 1.0f) {
                if (x != 0.0f || y != 0.0f) {
                    y = 0.0f
                    x = y
                    return true
                }
                return false
            }
            return false
        }

        fun FixDenormals(): Boolean { // change tiny numbers to zero
            var denormal = false
            if (abs(x) < 1e-30f) {
                x = 0.0f
                denormal = true
            }
            if (abs(y) < 1e-30f) {
                y = 0.0f
                denormal = true
            }
            if (abs(z) < 1e-30f) {
                z = 0.0f
                denormal = true
            }
            return denormal
        }

        fun Cross(a: idVec3): idVec3 {
            return idVec3(y * a.z - z * a.y, z * a.x - x * a.z, x * a.y - y * a.x)
        }

        fun Cross(a: idVec3, b: idVec3): idVec3 {
            x = a.y * b.z - a.z * b.y
            y = a.z * b.x - a.x * b.z
            z = a.x * b.y - a.y * b.x
            return this
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y + z * z)
        }

        fun LengthSqr(): Float {
            return x * x + y * y + z * z
        }

        fun LengthFast(): Float {
            val sqrLength: Float = x * x + y * y + z * z
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float = x * x + y * y + z * z
            val invLength: Float = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float = x * x + y * y + z * z
            val invLength: Float = idMath.RSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            return invLength * sqrLength
        }

        fun Truncate(length: Float): idVec3 { // cap length
            val length2: Float
            val ilength: Float
            if (length != 0.0f) {
                Zero()
            } else {
                length2 = LengthSqr()
                if (length2 > length * length) {
                    ilength = length * idMath.InvSqrt(length2)
                    x *= ilength
                    y *= ilength
                    z *= ilength
                }
            }
            return this
        }

        fun Clamp(min: idVec3, max: idVec3) {
            if (x < min.x) {
                x = min.x
            } else if (x > max.x) {
                x = max.x
            }
            if (y < min.y) {
                y = min.y
            } else if (y > max.y) {
                y = max.y
            }
            if (z < min.z) {
                z = min.z
            } else if (z > max.z) {
                z = max.z
            }
        }

        fun Snap() { // snap to closest integer value
            x = floor((x + 0.5f).toDouble()).toFloat()
            y = floor((y + 0.5f).toDouble()).toFloat()
            z = floor((z + 0.5f).toDouble()).toFloat()
        }

        fun SnapInt() { // snap towards integer (floor)
            x = x.toInt().toFloat()
            y = y.toInt().toFloat()
            z = z.toInt().toFloat()
        }

        override fun GetDimension(): Int {
            return 3
        }

        fun ToYaw(): Float {
            var yaw: Float
            if (y == 0.0f && x == 0.0f) {
                yaw = 0.0f
            } else {
                yaw = RAD2DEG(atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
            }
            return yaw
        }

        fun ToPitch(): Float {
            val forward: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                forward = idMath.Sqrt(x * x + y * y)
                pitch = RAD2DEG(atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return pitch
        }

        fun ToAngles(): idAngles {
            val forward: Float
            var yaw: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                yaw = 0.0f
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                yaw = RAD2DEG(atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = RAD2DEG(atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return idAngles(-pitch, yaw, 0.0f)
        }

        //public	idVec2 &		ToVec2( void );
        fun ToPolar(): idPolar3 {
            val forward: Float
            var yaw: Float
            var pitch: Float
            if (x == 0.0f && y == 0.0f) {
                yaw = 0.0f
                pitch = if (z > 0.0f) {
                    90.0f
                } else {
                    270.0f
                }
            } else {
                yaw = RAD2DEG(atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = RAD2DEG(atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return idPolar3(idMath.Sqrt(x * x + y * y + z * z), yaw, -pitch)
        }

        //public	float *			ToFloatPtr( void );
        // vector should be normalized
        fun ToMat3(): idMat3 {
            val mat = idMat3()
            var d: Float
            mat.setRow(0, x, y, z)
            d = x * x + y * y
            if (d == 0f) {
//		mat[1][0] = 1.0f;
//		mat[1][1] = 0.0f;
//		mat[1][2] = 0.0f;
                mat.setRow(1, 1.0f, 0.0f, 0.0f) //TODO:test, and rename, column??
            } else {
                d = idMath.InvSqrt(d)
                //		mat[1][0] = -y * d;
//		mat[1][1] = x * d;
//		mat[1][2] = 0.0f;
                mat.setRow(1, -y * d, x * d, 0.0f)
            }
            //        mat[2] = Cross( mat[1] );
            mat.setRow(2, Cross(mat.getRow(1)))
            return mat
        }

        fun ToVec2(): idVec2 {
//	return *reinterpret_cast<const idVec2 *>(this);
            return idVec2(x, y)
        }

        fun ToFloatPtr(): FloatArray {
            return floatArrayOf(x, y, z)
        }


        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y $z"
        }

        // vector should be normalized
        fun NormalVectors(left: idVec3, down: idVec3) {
            var d: Float
            d = x * x + y * y
            if (d == 0f) {
                left.x = 1f
                left.y = 0f
                left.z = 0f
            } else {
                d = idMath.InvSqrt(d)
                left.x = -y * d
                left.y = x * d
                left.z = 0f
            }
            down.set(left.Cross(this))
        }

        fun OrthogonalBasis(left: idVec3, up: idVec3) {
            val l: Float
            val s: Float
            if (abs(z) > 0.7f) {
                l = y * y + z * z
                s = idMath.InvSqrt(l)
                up.x = 0f
                up.y = z * s
                up.z = -y * s
                left.x = l * s
                left.y = -x * up.z
                left.z = x * up.y
            } else {
                l = x * x + y * y
                s = idMath.InvSqrt(l)
                left.x = -y * s
                left.y = x * s
                left.z = 0f
                up.x = -z * left.y
                up.y = z * left.x
                up.z = l * s
            }
        }

        /*
         =============
         ProjectSelfOntoSphere

         Projects the z component onto a sphere.
         =============
         */

        fun ProjectOntoPlane(normal: idVec3, overBounce: Float = 1.0f) {
            // x * a.x + y * a.y + z * a.z;
            var backoff: Float = this * normal //	backoff = this.x * normal.x;//TODO:normal.x???
            if (overBounce.toDouble() != 1.0) {
                if (backoff < 0) {
                    backoff *= overBounce
                } else {
                    backoff /= overBounce
                }
            }
            this.minusAssign(normal * backoff) //	*this -= backoff * normal;
        }


        fun ProjectAlongPlane(normal: idVec3, epsilon: Float, overBounce: Float = 1.0f): Boolean {
            val cross = idVec3()
            cross.set(this.Cross(normal).Cross(this))
            // normalize so a fixed epsilon can be used
            cross.Normalize()
            val len: Float = normal * cross
            if (abs(len) < epsilon) {
                return false
            }
            cross.timesAssign(overBounce * (normal * this) / len) //	cross *= overBounce * ( normal * (*this) ) / len;
            this.minusAssign(cross) //(*this) -= cross;
            return true
        }

        fun ProjectSelfOntoSphere(radius: Float) {
            val rsqr = radius * radius
            val len = Length()
            z = if (len < rsqr * 0.5f) {
                sqrt((rsqr - len).toDouble()).toFloat()
            } else {
                (rsqr / (2.0f * sqrt(len.toDouble()))).toFloat()
            }
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec3, v2: idVec3, l: Float) {
            if (l <= 0.0f) {
                this.set(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.set(v2) //(*this) = v2;
            } else {
                this.set(v1 + (v2 - v1) * l) //(*this) = v1 + l * ( v2 - v1 );
            }
        }

        fun SLerp(v1: idVec3, v2: idVec3, t: Float) {
            val omega: Float
            val sinom: Float
            val scale0: Float
            val scale1: Float
            if (t <= 0.0f) {
//		(*this) = v1;
                set(v1)
                return
            } else if (t >= 1.0f) {
//		(*this) = v2;
                set(v2)
                return
            }
            val cosom: Float = v1 * v2
            if (1.0f - cosom > LERP_DELTA) {
                omega = acos(cosom.toDouble()).toFloat()
                sinom = sin(omega.toDouble()).toFloat()
                scale0 = (sin(((1.0f - t) * omega).toDouble()) / sinom).toFloat()
                scale1 = (sin((t * omega).toDouble()) / sinom).toFloat()
            } else {
                scale0 = 1.0f - t
                scale1 = t
            }

//	(*this) = ( v1 * scale0 + v2 * scale1 );
            set((v1 * scale0 + v2 * scale1))
        }

        override fun get(i: Int): Float { //TODO:rename you lazy ass
            if (i == 1) {
                return y
            } else if (i == 2) {
                return z
            }
            return x
        }

        override fun set(i: Int, value: Float): Float {
            if (i == 1) {
                y = value
            } else if (i == 2) {
                z = value
            } else {
                x = value
            }
            return value
        }

        fun plusAssign(i: Int, value: Float) {
            if (i == 1) {
                y += value
            } else if (i == 2) {
                z += value
            } else {
                x += value
            }
        }

        fun minusAssign(i: Int, value: Float) {
            if (i == 1) {
                y -= value
            } else if (i == 2) {
                z -= value
            } else {
                x -= value
            }
        }

        fun timesAssign(i: Int, value: Float) {
            if (i == 1) {
                y *= value
            } else if (i == 2) {
                z *= value
            } else {
                x *= value
            }
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            x = buffer.float
            y = buffer.float
            z = buffer.float
        }

        override fun Write(): ByteBuffer {
            val buffer = ByteBuffer.allocate(BYTES)
            buffer.putFloat(x).putFloat(y).putFloat(z).flip()
            return buffer
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is idVec3) return false
            val idVec3 = o
            if (idVec3.x.compareTo(x) != 0) return false
            return if (idVec3.y.compareTo(y) != 0) false else idVec3.z.compareTo(z) == 0
        }

        override fun hashCode(): Int {
            var result = if (x != +0.0f) java.lang.Float.floatToIntBits(x) else 0
            result = 31 * result + if (y != +0.0f) java.lang.Float.floatToIntBits(y) else 0
            result = 31 * result + if (z != +0.0f) java.lang.Float.floatToIntBits(z) else 0
            return result
        }

        fun ToVec2_oPluSet(v: idVec2) {
            x += v.x
            y += v.y
        }

        fun ToVec2_oMinSet(v: idVec2) {
            x -= v.x
            y -= v.y
        }

        fun ToVec2_oMulSet(a: Float) {
            x *= a
            y *= a
        }

        fun ToVec2_Normalize() {
            val v = ToVec2()
            v.Normalize()
            this.set(v)
        }

        fun ToVec2_NormalizeFast() {
            val v = ToVec2()
            v.NormalizeFast()
            this.set(v)
        }

        companion object {
            @Transient

            val SIZE = 3 * java.lang.Float.SIZE

            @Transient

            val BYTES = SIZE / java.lang.Byte.SIZE

            /*
         =============
         SLerp

         Spherical linear interpolation from v1 to v2.
         Vectors are expected to be normalized.
         =============
         */
            private const val LERP_DELTA = 1e-6

            //public	friend idVec3	operator*( final  float a, final  idVec3 b );
            fun times(a: Float, b: idVec3): idVec3 {
                return idVec3(b.x * a, b.y * a, b.z * a)
            }


            fun generateArray(length: Int): Array<idVec3> {
                return Array(length) { idVec3() }
            }


            fun generateArray(firstDimensionSize: Int, secondDimensionSize: Int): Array<Array<idVec3>> {
                return Array(firstDimensionSize) { Array(secondDimensionSize) { idVec3() } }
            }

            fun copyVec(arr: Array<idVec3>): Array<idVec3> {
                val out = generateArray(arr.size)
                for (i in out.indices) {
                    out[i].set(arr[i])
                }
                return out
            }

            fun toByteBuffer(vecs: Array<idVec3>): ByteBuffer {
                val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
                for (vec in vecs) {
                    data.put(vec.Write().rewind())
                }
                return data.flip()
            }
        }
    }

    //===============================================================
    //
    //	idVec4 - 4D vector
    //
    //===============================================================
    class idVec4 : idVec<idVec4>, SERiAL {
        private val DBG_count = DBG_counter++


        var w = 0f


        var x = 0f


        var y = 0f


        var z = 0f

        constructor()
        constructor(v: idVec4) {
            x = v.x
            y = v.y
            z = v.z
            w = v.w
        }

        constructor(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        constructor(x: Int, y: Int, z: Int, w: Int) {
            this.x = x.toFloat()
            this.y = y.toFloat()
            this.z = z.toFloat()
            this.w = w.toFloat()
        }

        fun set(x: Float, y: Float, z: Float, w: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.w = w
        }

        override fun Zero() {
            w = 0.0f
            z = w
            y = z
            x = y
        }

        //public	float			operator[]( final  int index ) final ;
        //public	float &			operator[]( final  int index );
        //public	idVec4			operator-() final ;
        override fun times(a: idVec4): Float {
            return x * a.x + y * a.y + z * a.z + w * a.w
        }

        //public	idVec4			operator/( final  float a ) final ;
        override fun times(a: Float): idVec4 {
            return idVec4(x * a, y * a, z * a, w * a)
        }

        override fun times(a: Int): idVec4 {
            return idVec4(x * a, y * a, z * a, w * a)
        }

        override fun plus(a: idVec4): idVec4 {
            return idVec4(x + a.x, y + a.y, z + a.z, w + a.w)
        }

        override fun minus(a: idVec4): idVec4 {
            return idVec4(x - a.x, y - a.y, z - a.z, w - a.w)
        }

        override fun div(a: Int): idVec4 {
            return idVec4(x - a, y - a, z - a, w - a)
        }

        operator fun unaryMinus(): idVec4 {
            return idVec4(-x, -y, -z, -w)
        }

        //public	idVec4 &		operator+=( final  idVec4 &a );
        //public	idVec4			operator-( final  idVec4 &a ) final ;
        fun minusAssign(i: Int, value: Float) {
            when (i) {
                1 -> y -= value
                2 -> z -= value
                3 -> w -= value
                else -> x -= value
            }
        }

        fun timesAssign(i: Int, value: Float) {
            when (i) {
                1 -> y *= value
                2 -> z *= value
                3 -> w *= value
                else -> x *= value
            }
        }

        override fun plusAssign(a: idVec4): idVec4 {
            x += a.x
            y += a.y
            z += a.z
            w += a.w
            return this
        }

        //public	bool			operator==(	final  idVec4 &a ) final ;						// exact compare, no epsilon
        //public	bool			operator!=(	final  idVec4 &a ) final ;						// exact compare, no epsilon
        //public	idVec4 &		operator-=( final  idVec4 &a );
        //public	idVec4 &		operator/=( final  idVec4 &a );
        //public	idVec4 &		operator/=( final  float a );
        //public	idVec4 &		operator*=( final  float a );
        //
        //public	friend idVec4	operator*( final  float a, final  idVec4 b );
        fun Compare(a: idVec4): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z && w == a.w
        }

        fun Compare(a: idVec4, epsilon: Float): Boolean { // compare with epsilon
            if (abs(x - a.x) > epsilon) {
                return false
            }
            if (abs(y - a.y) > epsilon) {
                return false
            }
            return if (abs(z - a.z) > epsilon) {
                false
            } else abs(w - a.w) <= epsilon
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y + z * z + w * w)
        }

        fun LengthSqr(): Float {
            return x * x + y * y + z * z + w * w
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float = x * x + y * y + z * z + w * w
            val invLength: Float = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            w *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float = x * x + y * y + z * z + w * w
            val invLength: Float = idMath.RSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            w *= invLength
            return invLength * sqrLength
        }

        //public	idVec2 &		ToVec2( void );
        override fun GetDimension(): Int {
            return 4
        }

        //public	idVec3 &		ToVec3( void );
        @Deprecated("")
        fun ToVec2(): idVec2 {
//	return *reinterpret_cast<const idVec2 *>(this);
            return idVec2(x, y)
        }

        //public	float *			ToFloatPtr( void );
        @Deprecated("")
        fun ToVec3(): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(this);
            return idVec3(x, y, z)
        }

        fun ToFloatPtr(): FloatArray {
            return floatArrayOf(x, y, z, w) //TODO:put shit in array si we can referef it
        }


        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "$x $y $z $w"
        }

        /*
         =============
         Lerp

         Linearly inperpolates one vector to another.
         =============
         */
        fun Lerp(v1: idVec4, v2: idVec4, l: Float) {
            if (l <= 0.0f) {
//		(*this) = v1;
                x = v1.x
                y = v1.y
                z = v1.z
                w = v1.w
            } else if (l >= 1.0f) {
//		(*this) = v2;
                x = v2.x
                y = v2.y
                z = v2.z
                w = v2.w
            } else {
//		(*this) = v1 + l * ( v2 - v1 );
                w = v1.w + l * (v2.w - v1.w)
                x = v1.x + l * (v2.x - v1.x)
                y = v1.y + l * (v2.y - v1.y)
                z = v1.z + l * (v2.z - v1.z)
            }
        }

        override fun set(a: idVec4): idVec4 {
            x = a.x
            y = a.y
            z = a.z
            w = a.w
            return this
        }

        fun set(a: idVec3): idVec4 {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        override fun get(i: Int): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> y
                2 -> z
                3 -> w
                else -> x
            }
        }

        override fun set(i: Int, value: Float): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { w = it }
                else -> value.also { x = it }
            }
        }

        fun plusAssign(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.let { y += it; y }
                2 -> value.let { z += it; z }
                3 -> value.let { w += it; w }
                else -> value.let { x += it; x }
            }
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer) {
            x = buffer.float
            y = buffer.float
            z = buffer.float
            w = buffer.float
        }

        override fun Write(): ByteBuffer {
            val buffer = AllocBuffer()
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w).flip()
            return buffer
        }

        override fun div(a: Float): idVec4 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient

            val SIZE = 4 * java.lang.Float.SIZE

            @Transient

            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0


            fun generateArray(length: Int): Array<idVec4> {
                return Array(length) { idVec4() }
            }

            fun toByteBuffer(vecs: Array<idVec4>): ByteBuffer {
                val data = BufferUtils.createByteBuffer(BYTES * vecs.size)
                for (vec in vecs) {
                    data.put(vec.Write().rewind())
                }
                return data.flip()
            }
        }
    }

    //===============================================================
    //
    //	idVec5 - 5D vector
    //
    //===============================================================
    class idVec5 : idVec<idVec5>, SERiAL {

        var s = 0f


        var t = 0f


        var x = 0f


        var y = 0f


        var z = 0f

        constructor()
        constructor(xyz: idVec3, st: idVec2) {
            x = xyz.x
            y = xyz.y
            z = xyz.z
            //	s = st[0];
            s = st.x
            //	t = st[1];
            t = st.y
        }

        constructor(x: Float, y: Float, z: Float, s: Float, t: Float) {
            this.x = x
            this.y = y
            this.z = z
            this.s = s
            this.t = t
        }

        constructor(a: idVec3) {
            x = a.x
            y = a.y
            z = a.z
        }

        //copy constructor
        constructor(a: idVec5) {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
        }

        //public	float			operator[]( int index ) final ;
        override fun get(i: Int): Float { //TODO:rename you lazy sod
            return when (i) {
                1 -> y
                2 -> z
                3 -> s
                4 -> t
                else -> x
            }
        }

        override fun set(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { s = it }
                4 -> value.also { t = it }
                else -> value.also { x = it }
            }
        }

        override fun set(a: idVec5): idVec5 {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
            return this
        }

        //public	float &			operator[]( int index );
        //public	idVec5 &		operator=( final  idVec3 &a );
        fun set(a: idVec3): idVec5 {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        override fun GetDimension(): Int {
            return 5
        }

        fun ToVec3(): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(this);
            return idVec3(x, y, z)
        }

        //public	float *			ToFloatPtr( void );
        //public	idVec3 &		ToVec3( void );
        fun ToFloatPtr(): FloatArray {
            return floatArrayOf(x, y, z) //TODO:array!?
        }


        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        fun Lerp(v1: idVec5, v2: idVec5, l: Float) {
            if (l <= 0.0f) {
                this.set(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.set(v2) //(*this) = v2;
            } else {
                x = v1.x + l * (v2.x - v1.x)
                y = v1.y + l * (v2.y - v1.y)
                z = v1.z + l * (v2.z - v1.z)
                s = v1.s + l * (v2.s - v1.s)
                t = v1.t + l * (v2.t - v1.t)
            }
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun plus(a: idVec5): idVec5 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun minus(a: idVec5): idVec5 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun div(a: Int): idVec5 {
            TODO("Not yet implemented")
        }

        override fun times(a: idVec5): Float {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun div(a: Float): idVec5 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun times(a: Int): idVec5 {
            TODO("Not yet implemented")
        }

        fun ToVec3_oMulSet(axis: idMat3) {
            this.set(ToVec3().timesAssign(axis))
        }

        fun ToVec3_oPluSet(origin: idVec3) {
            this.set(ToVec3().plusAssign(origin))
        }


        companion object {
            @Transient
            val SIZE = 5 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            fun generateArray(length: Int): Array<idVec5> {
                return Array(length) { idVec5() }
            }
        }

        override fun times(a: Float): idVec5 {
            TODO("Not yet implemented")
        }

        override fun plusAssign(a: idVec5): idVec5 {
            TODO("Not yet implemented")
        }

        override fun Zero() {
            TODO("Not yet implemented")
        }
    }

    //===============================================================
    //
    //	idVec6 - 6D vector
    //
    //===============================================================
    class idVec6 : idVec<idVec6>, SERiAL {
        private val DBG_count = DBG_counter++

        //
        //
        var p: FloatArray = FloatArray(6)

        constructor() {
            DBG_idVec6++
            val a = 0
        }

        constructor(a: FloatArray) {
//	memcpy( p, a, 6 * sizeof( float ) );
            System.arraycopy(a, 0, p, 0, 6)
        }

        constructor(v: idVec6) {
            System.arraycopy(v.p, 0, p, 0, 6)
        }

        constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p[0] = a1
            p[1] = a2
            p[2] = a3
            p[3] = a4
            p[4] = a5
            p[5] = a6
        }

        fun set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p[0] = a1
            p[1] = a2
            p[2] = a3
            p[3] = a4
            p[4] = a5
            p[5] = a6
        }

        override fun Zero() {
            p[5] = 0.0f
            p[4] = p[5]
            p[3] = p[4]
            p[2] = p[3]
            p[1] = p[2]
            p[0] = p[1]
        }

        //public 	float			operator[]( final  int index ) final ;
        //public 	float &			operator[]( final  int index );
        operator fun unaryMinus(): idVec6 {
            return idVec6(-p[0], -p[1], -p[2], -p[3], -p[4], -p[5])
        }

        override fun times(a: Float): idVec6 {
            return idVec6(p[0] * a, p[1] * a, p[2] * a, p[3] * a, p[4] * a, p[5] * a)
        }

        //public 	idVec6			operator/( final  float a ) final ;
        override fun times(a: idVec6): Float {
            return p[0] * a.p[0] + p[1] * a.p[1] + p[2] * a.p[2] + p[3] * a.p[3] + p[4] * a.p[4] + p[5] * a.p[5]
        }

        override fun times(a: Int): idVec6 {
            return idVec6(p[0] * a, p[1] * a, p[2] * a, p[3] * a, p[4] * a, p[5] * a)
        }

        //public 	idVec6			operator-( final  idVec6 &a ) final ;
        override fun plus(a: idVec6): idVec6 {
            return idVec6(
                p[0] + a.p[0],
                p[1] + a.p[1],
                p[2] + a.p[2],
                p[3] + a.p[3],
                p[4] + a.p[4],
                p[5] + a.p[5]
            )
        }

        //public 	idVec6 &		operator*=( final  float a );
        //public 	idVec6 &		operator/=( final  float a );
        override fun plusAssign(a: idVec6): idVec6 {
            p[0] += a.p[0]
            p[1] += a.p[1]
            p[2] += a.p[2]
            p[3] += a.p[3]
            p[4] += a.p[4]
            p[5] += a.p[5]
            return this
        }

        //public 	idVec6 &		operator-=( final  idVec6 &a );
        //
        //public 	friend idVec6	operator*( final  float a, final  idVec6 b );
        fun Compare(a: idVec6): Boolean { // exact compare, no epsilon
            return (p[0] == a.p[0] && p[1] == a.p[1] && p[2] == a.p[2]
                    && p[3] == a.p[3] && p[4] == a.p[4] && p[5] == a.p[5])
        }

        fun Compare(a: idVec6, epsilon: Float): Boolean { // compare with epsilon
            if (abs(p[0] - a.p[0]) > epsilon) {
                return false
            }
            if (abs(p[1] - a.p[1]) > epsilon) {
                return false
            }
            if (abs(p[2] - a.p[2]) > epsilon) {
                return false
            }
            if (abs(p[3] - a.p[3]) > epsilon) {
                return false
            }
            return if (abs(p[4] - a.p[4]) > epsilon) {
                false
            } else abs(p[5] - a.p[5]) <= epsilon
        }

        //public 	bool			operator==(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        //public 	bool			operator!=(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        fun Length(): Float {
            return idMath.Sqrt(
                p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
            )
        }

        fun LengthSqr(): Float {
            return p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float = p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
            val invLength: Float = idMath.InvSqrt(sqrLength)
            p[0] *= invLength
            p[1] *= invLength
            p[2] *= invLength
            p[3] *= invLength
            p[4] *= invLength
            p[5] *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float = p[0] * p[0] + p[1] * p[1] + p[2] * p[2] + p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
            val invLength: Float = idMath.RSqrt(sqrLength)
            p[0] *= invLength
            p[1] *= invLength
            p[2] *= invLength
            p[3] *= invLength
            p[4] *= invLength
            p[5] *= invLength
            return invLength * sqrLength
        }

        override fun GetDimension(): Int {
            return 6
        }

        fun SubVec3(index: Int): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(p + index * 3);
            var index = index
            return idVec3(p[3.let { index *= it; index }], p[index + 1], p[index + 2])
        }

        //public 	idVec3 &		SubVec3( int index );
        fun ToFloatPtr(): FloatArray {
            return p
        }

        //public 	float *			ToFloatPtr( void );

        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun set(a: idVec6): idVec6 {
            p[0] = a.p[0]
            p[1] = a.p[1]
            p[2] = a.p[2]
            p[3] = a.p[3]
            p[4] = a.p[4]
            p[5] = a.p[5]
            return this
        }

        override fun get(index: Int): Float {
            return p[index]
        }

        override fun set(index: Int, value: Float): Float {
            return value.also { p[index] = it }
        }

        //
        //        public void setP(final int index, final float value) {
        //            p[index] = value;
        //        }
        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun minus(a: idVec6): idVec6 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun div(a: Int): idVec6 {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun div(a: Float): idVec6 {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun SubVec3_oSet(i: Int, v: idVec3) {
            System.arraycopy(v.ToFloatPtr(), 0, p, i * 3, 3)
        }

        fun SubVec3_oPluSet(i: Int, v: idVec3): idVec3 {
            val off = i * 3
            p[off + 0] += v.x
            p[off + 1] += v.y
            p[off + 2] += v.z
            return idVec3(p, off)
        }

        fun SubVec3_oMinSet(i: Int, v: idVec3): idVec3 {
            return SubVec3_oPluSet(i, v.unaryMinus())
        }

        fun SubVec3_oMulSet(i: Int, v: Float) {
            val off = i * 3
            p[off + 0] *= v
            p[off + 1] *= v
            p[off + 2] *= v
        }

        fun SubVec3_Normalize(i: Int): Float {
            val v = SubVec3(i)
            val normalize = v.Normalize()
            SubVec3_oSet(i, v)
            return normalize
        }

        override fun hashCode(): Int {
            return Arrays.hashCode(p)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val idVec6 = o as idVec6
            return Arrays.equals(p, idVec6.p)
        }

        override fun toString(): String {
            return "idVec6{" +
                    "p=" + Arrays.toString(p) +
                    '}'
        }

        companion object {
            @Transient
            val SIZE = 6 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0
            private var DBG_idVec6 = 0
        }
    }

    //===============================================================
    //
    //	idVecX - arbitrary sized vector
    //
    //  The vector lives on 16 byte aligned and 16 byte padded memory.
    //
    //	NOTE: due to the temporary memory pool idVecX cannot be used by multiple threads
    //
    //===============================================================
    public class idVecX {
        private var size // size of the vector
                : Int = 1
        var p // memory the vector is stored
                : FloatArray = FloatArray(1)
        var VECX_SIMD = false
        private var alloced // if -1 p points to data set with SetData
                : Int = 1

        constructor()

        constructor(length: Int) {
            alloced = 0
            size = alloced
            SetSize(length)
        }

        constructor(length: Int, data: FloatArray) {
            alloced = 0
            size = alloced
            SetData(length, data)
        }

        @Deprecated("")
        fun VECX_CLEAREND() { //TODO:is this function need for Java?
            var s = size
            ////            while (s < ((s + 3) & ~3)) {
            while (s < p.size) {
                p[s++] = 0.0f
            }
        }

        //public					~idVecX( void );
        //public	float			operator[]( const int index ) const;
        fun get(index: Int): Float {
            return p[index]
        }

        operator fun set(index: Int, value: Float): Float {
            return value.also { p[index] = it }
        }

        //public	float &			operator[]( const int index );
        //public	idVecX			operator-() const;
        operator fun unaryMinus(): idVecX {
            val m = idVecX()
            m.SetTempSize(size)
            var i: Int = 0
            while (i < size) {
                m.p[i] = -p[i]
                i++
            }
            return m
        }

        //public	idVecX &		operator=( const idVecX &a );
        fun set(a: idVecX): idVecX {
            SetSize(a.size)
            System.arraycopy(a.p, 0, p, 0, p.size)
            tempIndex = 0
            return this
        }

        operator fun times(a: Float): idVecX {
            val m = idVecX()
            m.SetTempSize(size)
            if (VECX_SIMD) {
                Simd.SIMDProcessor.Mul16(m.p, p, a, size)
            } else {
                var i: Int = 0
                while (i < size) {
                    m.p[i] = p[i] * a
                    i++
                }
            }
            return m
        }

        //public	idVecX			operator/( const float a ) const;
        //public	float			operator*( const idVecX &a ) const;
        operator fun times(a: idVecX): Float {
            var sum = 0.0f
            assert(size == a.size)
            var i: Int = 0
            while (i < size) {
                sum += p[i] * a.p[i]
                i++
            }
            return sum
        }

        //public	idVecX			operator-( const idVecX &a ) const;
        operator fun minus(a: idVecX): idVecX {
            val m = idVecX()
            assert(size == a.size)
            m.SetTempSize(size)
            for (i in 0..size) {
                m.p[i] = p[i] - a.p[i]
            }
            return m
        }

        //public	idVecX			operator+( const idVecX &a ) const;
        operator fun plus(a: idVecX): idVecX {
            val m = idVecX()
            assert(size == a.size)
            m.SetTempSize(size)
            //#ifdef VECX_SIMD
//	SIMDProcessor->Add16( m.p, p, a.p, size );
//#else
            var i: Int = 0
            while (i < size) {
                m.p[i] = p[i] + a.p[i]
                i++
            }
            //#endif
            return m
        }

        //public	idVecX &		operator*=( const float a );
        fun timesAssign(a: Float): idVecX {
//#ifdef VECX_SIMD
//	SIMDProcessor->MulAssign16( p, a, size );
//#else
            var i: Int = 0
            while (i < size) {
                p[i] *= a
                i++
            }
            //#endif
            return this
        }

        //public	idVecX &		operator/=( const float a );
        //public	idVecX &		operator+=( const idVecX &a );
        //public	idVecX &		operator-=( const idVecX &a );
        //public	friend idVecX	operator*( const float a, const idVecX b );
        fun Compare(a: idVecX): Boolean { // exact compare, no epsilon
            assert(size == a.size)
            var i: Int = 0
            while (i < size) {
                if (p[i] != a.p[i]) {
                    return false
                }
                i++
            }
            return true
        }

        fun Compare(a: idVecX, epsilon: Float): Boolean { // compare with epsilon
            assert(size == a.size)
            var i: Int = 0
            while (i < size) {
                if (abs(p[i] - a.p[i]) > epsilon) {
                    return false
                }
                i++
            }
            return true
        }

        //public	bool			operator==(	const idVecX &a ) const;						// exact compare, no epsilon
        //public	bool			operator!=(	const idVecX &a ) const;						// exact compare, no epsilon
        fun SetSize(newSize: Int) {
            val alloc = newSize + 3 and 3.inv()
            if (alloc > alloced && alloced != -1) {
                p = FloatArray(alloc)
                alloced = alloc
            }
            size = newSize
            VECX_CLEAREND()
        }


        fun ChangeSize(newSize: Int, makeZero: Boolean = false) {
            val alloc = newSize + 3 and 3.inv()
            if (alloc > alloced && alloced != -1) {
                val oldVec = p
                //		p = (float *) Mem_Alloc16( alloc * sizeof( float ) );
                p = FloatArray(alloc)
                alloced = alloc
                System.arraycopy(oldVec, 0, p, 0, size) //TODO:ifelse
                if (makeZero) {
                    // zero any new elements
                    for (i in size until newSize) {
                        p[i] = 0.0f
                    }
                }
            }
            size = newSize
            VECX_CLEAREND()
        }

        fun GetSize(): Int {
            return size
        }

        fun SetData(length: Int, data: FloatArray) {
            if ((p[0] < tempPtr[0] || p[0] >= tempPtr[0] + VECX_MAX_TEMP) && alloced != -1) {
//		Mem_Free16( p );
            }
            //	assert( ( ( (int) data ) & 15 ) == 0 ); // data must be 16 byte aligned
            p = data
            size = length
            alloced = -1
            VECX_CLEAREND()
        }

        fun Zero() {
//#ifdef VECX_SIMD
//	SIMDProcessor.Zero16( p, size );
//#else
//	memset( p, 0, size * sizeof( float ) );
//#endif
            Arrays.fill(p, 0, size, 0f)
        }

        fun Zero(length: Int) {
            SetSize(length)
            //#ifdef VECX_SIMD
//	SIMDProcessor.Zero16( p, length );
//#else
//	memset( p, 0, size * sizeof( float ) );
//#endif
            Arrays.fill(p, 0, size, 0f)
        }


        fun Random(seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            val rnd = idRandom(seed)
            val c: Float = u - l
            var i: Int = 0
            while (i < size) {
                p[i] = l + rnd.RandomFloat() * c
                i++
            }
        }


        fun Random(length: Int, seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            val rnd = idRandom(seed)
            SetSize(length)
            val c: Float = u - l
            var i: Int = 0
            while (i < size) {
                if (idMatX.DISABLE_RANDOM_TEST) { //for testing.
                    p[i] = i.toFloat()
                } else {
                    p[i] = l + rnd.RandomFloat() * c
                }
                i++
            }
        }

        fun Negate() {
//#ifdef VECX_SIMD
//	SIMDProcessor.Negate16( p, size );
//#else
            var oGet: Int = 0
            while (oGet < size) {
                p[oGet] = -p[oGet]
                oGet++
            }
            //#endif
        }

        fun Clamp(min: Float, max: Float) {
            var i: Int = 0
            while (i < size) {
                if (p[i] < min) {
                    p[i] = min
                } else if (p[i] > max) {
                    p[i] = max
                }
                i++
            }
        }

        fun SwapElements(e1: Int, e2: Int): idVecX {
            val tmp: Float = p[e1]
            p[e1] = p[e2]
            p[e2] = tmp
            return this
        }

        fun Length(): Float {
            var sum = 0.0f
            var i: Int = 0
            while (i < size) {
                sum += p[i] * p[i]
                i++
            }
            return idMath.Sqrt(sum)
        }

        fun LengthSqr(): Float {
            var sum = 0.0f
            var i: Int = 0
            while (i < size) {
                sum += p[i] * p[i]
                i++
            }
            return sum
        }

        fun Normalize(): idVecX {
            val m = idVecX()
            val invSqrt: Float
            var sum = 0.0f
            m.SetTempSize(size)
            var i: Int = 0
            while (i < size) {
                sum += p[i] * p[i]
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                m.p[i] = p[i] * invSqrt
                i++
            }
            return m
        }

        fun NormalizeSelf(): Float {
            val invSqrt: Float
            var sum = 0.0f
            var i: Int = 0
            while (i < size) {
                sum += p[i] * p[i]
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                p[i] *= invSqrt
                i++
            }
            return invSqrt * sum
        }

        fun GetDimension(): Int {
            return size
        }

        @Deprecated("readonly")
        fun SubVec3(index: Int): idVec3 {
            var index = index
            assert(index >= 0 && index * 3 + 3 <= size)
            //	return *reinterpret_cast<idVec3 *>(p + index * 3);
            return idVec3(p[3.let { index *= it; index }], p[index + 1], p[index + 2])
        }
        //public	idVec3 &		SubVec3( int index );

        @Deprecated("readonly")
        fun SubVec6(index: Int): idVec6 {
            var index = index
            assert(index >= 0 && index * 6 + 6 <= size)
            //	return *reinterpret_cast<idVec6 *>(p + index * 6);
            return idVec6(
                p[6.let { index *= it; index }],
                p[index + 1],
                p[index + 2],
                p[index + 3],
                p[index + 4],
                p[index + 5]
            )
        }

        //public	idVec6 &		SubVec6( int index );
        fun ToFloatPtr(): FloatArray {
            return p
        }

        //public	float *			ToFloatPtr( void );

        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        fun SetTempSize(newSize: Int) {
            size = newSize
            alloced = newSize + 3 and 3.inv()
            assert(alloced < VECX_MAX_TEMP)
            if (tempIndex + alloced > VECX_MAX_TEMP) {
                tempIndex = 0
            }
            //            p = idVecX.tempPtr + idVecX.tempIndex;
//            for (int a = 0; a < idVecX.tempIndex; a++) {//TODO:trippple check
//                p[a] = idVecX.tempPtr[a + idVecX.tempIndex];
//            }
            p = FloatArray(alloced)
            tempIndex += alloced
            VECX_CLEAREND()
        }

        fun SubVec3_Normalize(i: Int) {
            val vec3 = idVec3(p, i * 3)
            vec3.Normalize()
            SubVec3_oSet(i, vec3)
        }

        fun SubVec3_oSet(i: Int, v: idVec3) {
            p[i * 3 + 0] = v[0]
            p[i * 3 + 1] = v[1]
            p[i * 3 + 2] = v[2]
        }

        fun SubVec6_oSet(i: Int, v: idVec6) {
            p[i * 6 + 0] = v[0]
            p[i * 6 + 1] = v[1]
            p[i * 6 + 2] = v[2]
            p[i * 6 + 3] = v[3]
            p[i * 6 + 4] = v[4]
            p[i * 6 + 5] = v[5]
        }

        fun SubVec6_oPluSet(i: Int, v: idVec6) {
            p[i * 6 + 0] += v[0]
            p[i * 6 + 1] += v[1]
            p[i * 6 + 2] += v[2]
            p[i * 6 + 3] += v[3]
            p[i * 6 + 4] += v[4]
            p[i * 6 + 5] += v[5]
        }

        companion object {
            // friend class idMatX;
            const val VECX_MAX_TEMP = 1024
            private val temp: FloatArray = FloatArray(VECX_MAX_TEMP + 4) // used to store intermediate results
            private val tempPtr = temp // pointer to 16 byte aligned temporary memory
            private var tempIndex // index into memory pool, wraps around
                    = 0

            fun VECX_QUAD(x: Int): Int {
                return x + 3 and 3.inv()
            }

            @Deprecated("")
            fun VECX_ALLOCA(n: Int): FloatArray {
//    ( (float *) _alloca16( VECX_QUAD( n ) ) )
//            float[] temp = new float[VECX_QUAD(n)];
//            Arrays.fill(temp, -107374176);
//
//            return temp;
                return FloatArray(VECX_QUAD(n))
            }
        }
    }

    //===============================================================
    //
    //	idPolar3
    //
    //===============================================================
    class idPolar3 {
        var radius = 0f
        var theta = 0f
        var phi = 0f

        constructor()
        constructor(radius: Float, theta: Float, phi: Float) {
            assert(radius > 0)
            this.radius = radius
            this.theta = theta
            this.phi = phi
        }

        fun set(radius: Float, theta: Float, phi: Float) {
            assert(radius > 0)
            this.radius = radius
            this.theta = theta
            this.phi = phi
        }

        //public	float			operator[]( const int index ) const;
        //public	float &			operator[]( const int index );
        //public	idPolar3		operator-() const;
        //public	idPolar3 &		operator=( const idPolar3 &a );
        fun ToVec3(): idVec3 {
            val sp = CFloat()
            val cp = CFloat()
            val st = CFloat()
            val ct = CFloat()
            //            sp = cp = st = ct = 0.0f;
            idMath.SinCos(phi, sp, cp)
            idMath.SinCos(theta, st, ct)
            return idVec3(cp._val * radius * ct._val, cp._val * radius * st._val, radius * sp._val)
        }
    }
}