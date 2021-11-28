package neo.idlib.math

import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
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
import java.nio.*
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object Vector {
    private val vec2_origin: idVec2 = idVec2(0.0f, 0.0f)
    private val vec3_origin: idVec3 = idVec3(0.0f, 0.0f, 0.0f)
    private val vec3_zero: idVec3 = vec3_origin
    private val vec4_origin: idVec4 = idVec4(0.0f, 0.0f, 0.0f, 0.0f)
    private val vec4_zero: idVec4? = vec4_origin
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

    fun getVec3_origin(): idVec3 {
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
        return a.oGet(0) * b.oGet(0) + a.oGet(1) * b.oGet(1) + a.oGet(2) * b.oGet(2)
    }

    fun DotProduct(a: idVec3, b: idVec4): Float {
        return DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idVec3, b: idVec5): Float {
        return DotProduct(a, b.ToVec3())
    }

    fun DotProduct(a: idPlane, b: idPlane): Float {
        return a.oGet(0) * b.oGet(0) + a.oGet(1) * b.oGet(1) + a.oGet(2) * b.oGet(2)
    }

    fun VectorSubtract(a: DoubleArray?, b: DoubleArray?, c: DoubleArray?): DoubleArray? {
        c.get(0) = a.get(0) - b.get(0)
        c.get(1) = a.get(1) - b.get(1)
        c.get(2) = a.get(2) - b.get(2)
        return c
    }

    fun VectorSubtract(a: FloatArray?, b: FloatArray?, c: FloatArray?): FloatArray? {
        c.get(0) = a.get(0) - b.get(0)
        c.get(1) = a.get(1) - b.get(1)
        c.get(2) = a.get(2) - b.get(2)
        return c
    }

    fun VectorSubtract(a: idVec3, b: idVec3, c: FloatArray?): FloatArray? {
        c.get(0) = a.oGet(0) - b.oGet(0)
        c.get(1) = a.oGet(1) - b.oGet(1)
        c.get(2) = a.oGet(2) - b.oGet(2)
        return c
    }

    fun VectorSubtract(a: idVec3, b: idVec3, c: idVec3): idVec3 {
        c.oSet(0, a.oGet(0) - b.oGet(0))
        c.oSet(1, a.oGet(1) - b.oGet(1))
        c.oSet(2, a.oGet(2) - b.oGet(2))
        return c
    }

    fun VectorAdd(a: DoubleArray?, b: DoubleArray?, c: Array<Double?>?) {
        c.get(0) = a.get(0) + b.get(0)
        c.get(1) = a.get(1) + b.get(1)
        c.get(2) = a.get(2) + b.get(2)
    }

    fun VectorScale(v: DoubleArray?, s: Double, o: Array<Double?>?) {
        o.get(0) = v.get(0) * s
        o.get(1) = v.get(1) * s
        o.get(2) = v.get(2) * s
    }

    fun VectorMA(v: DoubleArray?, s: Double, b: DoubleArray?, o: Array<Double?>?) {
        o.get(0) = v.get(0) + b.get(0) * s
        o.get(1) = v.get(1) + b.get(1) * s
        o.get(2) = v.get(2) + b.get(2) * s
    }

    fun VectorMA(v: idVec3, s: Float, b: idVec3, o: idVec3) {
        o.oSet(0, v.oGet(0) + b.oGet(0) * s)
        o.oSet(1, v.oGet(1) + b.oGet(1) * s)
        o.oSet(2, v.oGet(2) + b.oGet(2) * s)
    }

    fun VectorCopy(a: DoubleArray?, b: Array<Double?>?) {
        b.get(0) = a.get(0)
        b.get(1) = a.get(1)
        b.get(2) = a.get(2)
    }

    fun VectorCopy(a: idVec3, b: idVec3) {
        b.oSet(a)
    }

    fun VectorCopy(a: idVec3, b: idVec5?) {
        b.oSet(a)
    }

    fun VectorCopy(a: idVec5?, b: idVec3) {
        b.oSet(a.ToVec3())
    }

    interface idVec<type : idVec<*>> {
        //reflection was too slow.
        //never thought I would say this, but thank God for type erasure.
        fun oGet(index: Int): Float {
            throw TODO_Exception()
        }

        fun oSet(a: type): type {
            throw TODO_Exception()
        }

        fun oSet(index: Int, value: Float): Float {
            throw TODO_Exception()
        }

        fun oPlus(a: type?): type? {
            throw TODO_Exception()
        }

        fun oMinus(a: type?): type? {
            throw TODO_Exception()
        }

        fun oMultiply(a: type?): Float {
            throw TODO_Exception()
        }

        fun oMultiply(a: Float): type? {
            throw TODO_Exception()
        }

        fun oDivide(a: Float): type? {
            throw TODO_Exception()
        }

        fun oPluSet(a: type?): type? {
            throw TODO_Exception()
        }

        fun GetDimension(): Int {
            throw TODO_Exception()
        }

        fun Zero() {
            throw TODO_Exception()
        }
    }

    //===============================================================
    //
    //	idVec2 - 2D vector
    //
    //===============================================================
    class idVec2 : idVec<idVec2?>, SERiAL {
        var x = 0f
        var y = 0f

        constructor()
        constructor(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        constructor(v: idVec2?) {
            x = v.x
            y = v.y
        }

        fun Set(x: Float, y: Float) {
            this.x = x
            this.y = y
        }

        override fun Zero() {
            y = 0.0f
            x = y
        }

        //public	float			operator[]( int index ) const;
        override fun oSet(index: Int, value: Float): Float {
            return if (index == 1) {
                value.also { y = it }
            } else {
                value.also { x = it }
            }
        }

        //public	float &			operator[]( int index );
        fun oPluSet(index: Int, value: Float): Float {
            return if (index == 1) {
                value.let { y += it; y }
            } else {
                value.let { x += it; x }
            }
        }

        //public	idVec2			operator-() const;
        override fun oGet(index: Int): Float { //TODO:rename you lazy sod
            return if (index == 1) {
                y
            } else x
        }

        //public	float			operator*( const idVec2 &a ) const;
        override fun oMultiply(a: idVec2?): Float {
            return x * a.x + y * a.y
        }

        //public	idVec2			operator/( const float a ) const;
        //public	idVec2			operator*( const float a ) const;
        override fun oMultiply(a: Float): idVec2? {
            return idVec2(x * a, y * a)
        }

        override fun oDivide(a: Float): idVec2? {
            val inva = 1.0f / a
            return idVec2(x * inva, y * inva)
        }

        //public	idVec2			operator+( const idVec2 &a ) const;
        override fun oPlus(a: idVec2?): idVec2? {
            return idVec2(x + a.x, y + a.y)
        }

        //public	idVec2			operator-( const idVec2 &a ) const;
        override fun oMinus(a: idVec2?): idVec2? {
            return idVec2(x - a.x, y - a.y)
        }

        //public	idVec2 &		operator+=( const idVec2 &a );
        override fun oPluSet(a: idVec2?): idVec2? {
            x += a.x
            y += a.y
            return this
        }

        //public	idVec2 &		operator/=( const idVec2 &a );
        //public	idVec2 &		operator/=( const float a );
        //public	idVec2 &		operator*=( const float a );
        //public	idVec2 &		operator-=( const idVec2 &a );
        fun oMinSet(a: idVec2?): idVec2? {
            x -= a.x
            y -= a.y
            return this
        }

        fun oMulSet(a: Float): idVec2? {
            x *= a
            y *= a
            return this
        }

        //public	friend idVec2	operator*( const float a, const idVec2 b );
        override fun oSet(a: idVec2?): idVec2? {
            x = a.x
            y = a.y
            return this
        }

        fun Compare(a: idVec2?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y
        }

        //public	bool			operator==(	const idVec2 &a ) const;						// exact compare, no epsilon
        //public	bool			operator!=(	const idVec2 &a ) const;						// exact compare, no epsilon
        fun Compare(a: idVec2?, epsilon: Float): Boolean { // compare with epsilon
            return if (Math.abs(x - a.x) > epsilon) {
                false
            } else Math.abs(y - a.y) <= epsilon
        }

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y)
        }

        fun LengthFast(): Float {
            val sqrLength: Float
            sqrLength = x * x + y * y
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun LengthSqr(): Float {
            return x * x + y * y
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val lengthSqr: Float
            val invLength: Float
            lengthSqr = x * x + y * y
            invLength = idMath.RSqrt(lengthSqr)
            x *= invLength
            y *= invLength
            return invLength * lengthSqr
        }

        fun Truncate(length: Float): idVec2? { // cap length
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

        fun Clamp(min: idVec2?, max: idVec2?) {
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
            x = Math.floor((x + 0.5f).toDouble()).toFloat()
            y = Math.floor((y + 0.5f).toDouble()).toFloat()
        }

        fun SnapInt() { // snap towards integer (floor)
            x = x.toInt().toFloat()
            y = y.toInt().toFloat()
        }

        override fun GetDimension(): Int {
            return 2
        }

        //public	float *			ToFloatPtr( void );
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y)
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
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
        fun Lerp(v1: idVec2?, v2: idVec2?, l: Float) {
            if (l <= 0.0f) {
                this.oSet(v1) //( * this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //( * this) = v2;
            } else {
                this.oSet(v2.oMinus(v1).oMultiply(l).oPlus(v1)) //( * this) = v1 + l * (v2 - v1);
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            val SIZE = 2 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            fun generateArray(length: Int): Array<idVec2?>? {
                return Stream.generate { idVec2() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
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

        constructor(v: idVec3) {
            x = v.x
            y = v.y
            z = v.z
        }

        @JvmOverloads
        constructor(xyz: FloatArray?, offset: Int = 0) {
            x = xyz.get(offset + 0)
            y = xyz.get(offset + 1)
            z = xyz.get(offset + 2)
        }

        fun Set(x: Float, y: Float, z: Float) {
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
        fun oNegative(): idVec3 {
            return idVec3(-x, -y, -z)
        }

        //public	idVec3 &		operator=( final  idVec3 &a );		// required because of a msvc 6 & 7 bug
        override fun oSet(a: idVec3): idVec3 {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        fun oSet(a: idVec2?): idVec3 {
            x = a.x
            y = a.y
            return this
        }

        //public	float			operator*( final  idVec3 &a ) final ;
        override fun oMultiply(a: idVec3): Float {
            return a.x * x + a.y * y + a.z * z
        }

        //public	idVec3			operator*( final  float a ) final ;
        override fun oMultiply(a: Float): idVec3 {
            return idVec3(x * a, y * a, z * a)
        }

        fun oMultiply(a: idMat3?): idVec3 {
            return idVec3(
                a.getRow(0).oGet(0) * x + a.getRow(1).oGet(0) * y + a.getRow(2).oGet(0) * z,
                a.getRow(0).oGet(1) * x + a.getRow(1).oGet(1) * y + a.getRow(2).oGet(1) * z,
                a.getRow(0).oGet(2) * x + a.getRow(1).oGet(2) * y + a.getRow(2).oGet(2) * z
            )
        }

        fun oMultiply(a: idRotation?): idVec3 {
            return a.oMultiply(this)
        }

        fun oMultiply(a: idMat4?): idVec3 {
            return a.oMultiply(this)
        }

        //public	idVec3			operator/( final  float a ) final ;
        override fun oDivide(a: Float): idVec3 {
            val inva = 1.0f / a
            return idVec3(x * inva, y * inva, z * inva)
        }

        //public	idVec3			operator+( final  idVec3 &a ) final ;F
        override fun oPlus(a: idVec3): idVec3 {
            return idVec3(x + a.x, y + a.y, z + a.z)
        }

        //public	idVec3			operator-( final  idVec3 &a ) final ;
        override fun oMinus(a: idVec3): idVec3 {
            return idVec3(x - a.x, y - a.y, z - a.z)
        }

        //public	idVec3 &		operator+=( final  idVec3 &a );
        override fun oPluSet(a: idVec3): idVec3 {
            x += a.x
            y += a.y
            z += a.z
            return this
        }

        //public	idVec3 &		operator-=( final  idVec3 &a );
        fun oMinSet(a: idVec3): idVec3 {
            x -= a.x
            y -= a.y
            z -= a.z
            return this
        }

        //public	idVec3 &		operator/=( final  idVec3 &a );
        fun oDivSet(a: Float): idVec3 {
            x /= a
            y /= a
            z /= a
            return this
        }

        //public	idVec3 &		operator*=( final  float a );
        fun oMulSet(a: Float): idVec3 {
            x *= a
            y *= a
            z *= a
            return this
        }

        fun oMulSet(mat: idMat3?): idVec3 {
            this.oSet(idMat3.Companion.oMulSet(this, mat))
            return this
        }

        //public	boolean			operator==(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final  idVec3 &a ) final ;						// exact compare, no epsilon
        fun oMulSet(rotation: idRotation?): idVec3 {
            this.oSet(rotation.oMultiply(this))
            return this
        }

        fun Compare(a: idVec3): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z
        }

        fun Compare(a: idVec3, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(x - a.x) > epsilon) {
                return false
            }
            return if (Math.abs(y - a.y) > epsilon) {
                false
            } else Math.abs(z - a.z) <= epsilon
        }

        //private idVec3  multiply(float a){
        //    return new idVec3( this.x * a, this.y * a, this.z * a );
        //}
        fun oPlus(a: Float): idVec3 {
            x += a
            y += a
            z += a
            return this
        }

        private fun oDivide(a: idVec3, b: Float): idVec3 {
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
            if (Math.abs(x) == 1.0f) {
                if (y != 0.0f || z != 0.0f) {
                    z = 0.0f
                    y = z
                    return true
                }
                return false
            } else if (Math.abs(y) == 1.0f) {
                if (x != 0.0f || z != 0.0f) {
                    z = 0.0f
                    x = z
                    return true
                }
                return false
            } else if (Math.abs(z) == 1.0f) {
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
            if (Math.abs(x) < 1e-30f) {
                x = 0.0f
                denormal = true
            }
            if (Math.abs(y) < 1e-30f) {
                y = 0.0f
                denormal = true
            }
            if (Math.abs(z) < 1e-30f) {
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
            val sqrLength: Float
            sqrLength = x * x + y * y + z * z
            return sqrLength * idMath.RSqrt(sqrLength)
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z
            invLength = idMath.RSqrt(sqrLength)
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
            x = Math.floor((x + 0.5f).toDouble()).toFloat()
            y = Math.floor((y + 0.5f).toDouble()).toFloat()
            z = Math.floor((z + 0.5f).toDouble()).toFloat()
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
                yaw = RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
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
                pitch = RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return pitch
        }

        fun ToAngles(): idAngles? {
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
                yaw = RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
                if (pitch < 0.0f) {
                    pitch += 360.0f
                }
            }
            return idAngles(-pitch, yaw, 0.0f)
        }

        //public	idVec2 &		ToVec2( void );
        fun ToPolar(): idPolar3? {
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
                yaw = RAD2DEG(Math.atan2(y.toDouble(), x.toDouble()))
                if (yaw < 0.0f) {
                    yaw += 360.0f
                }
                forward = idMath.Sqrt(x * x + y * y)
                pitch = RAD2DEG(Math.atan2(z.toDouble(), forward.toDouble()))
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

        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z)
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
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
            down.oSet(left.Cross(this))
        }

        fun OrthogonalBasis(left: idVec3, up: idVec3) {
            val l: Float
            val s: Float
            if (Math.abs(z) > 0.7f) {
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
        @JvmOverloads
        fun ProjectOntoPlane(normal: idVec3, overBounce: Float = 1.0f) {
            var backoff: Float
            // x * a.x + y * a.y + z * a.z;
            backoff = this.oMultiply(normal) //	backoff = this.x * normal.x;//TODO:normal.x???
            if (overBounce.toDouble() != 1.0) {
                if (backoff < 0) {
                    backoff *= overBounce
                } else {
                    backoff /= overBounce
                }
            }
            this.oMinSet(oMultiply(backoff, normal)) //	*this -= backoff * normal;
        }

        @JvmOverloads
        fun ProjectAlongPlane(normal: idVec3, epsilon: Float, overBounce: Float = 1.0f): Boolean {
            val cross = idVec3()
            val len: Float
            cross.oSet(this.Cross(normal).Cross(this))
            // normalize so a fixed epsilon can be used
            cross.Normalize()
            len = normal.oMultiply(cross)
            if (Math.abs(len) < epsilon) {
                return false
            }
            cross.oMulSet(overBounce * normal.oMultiply(this) / len) //	cross *= overBounce * ( normal * (*this) ) / len;
            this.oMinSet(cross) //(*this) -= cross;
            return true
        }

        fun ProjectSelfOntoSphere(radius: Float) {
            val rsqr = radius * radius
            val len = Length()
            z = if (len < rsqr * 0.5f) {
                Math.sqrt((rsqr - len).toDouble()).toFloat()
            } else {
                (rsqr / (2.0f * Math.sqrt(len.toDouble()))).toFloat()
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
                this.oSet(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //(*this) = v2;
            } else {
                this.oSet(v2.oMinus(v1).oMultiply(l).oPlus(v1)) //(*this) = v1 + l * ( v2 - v1 );
            }
        }

        fun SLerp(v1: idVec3, v2: idVec3, t: Float) {
            val omega: Float
            val cosom: Float
            val sinom: Float
            val scale0: Float
            val scale1: Float
            if (t <= 0.0f) {
//		(*this) = v1;
                oSet(v1)
                return
            } else if (t >= 1.0f) {
//		(*this) = v2;
                oSet(v2)
                return
            }
            cosom = v1.oMultiply(v2)
            if (1.0f - cosom > LERP_DELTA) {
                omega = Math.acos(cosom.toDouble()).toFloat()
                sinom = Math.sin(omega.toDouble()).toFloat()
                scale0 = (Math.sin(((1.0f - t) * omega).toDouble()) / sinom).toFloat()
                scale1 = (Math.sin((t * omega).toDouble()) / sinom).toFloat()
            } else {
                scale0 = 1.0f - t
                scale1 = t
            }

//	(*this) = ( v1 * scale0 + v2 * scale1 );
            oSet(v1.oMultiply(scale0).oPlus(v2.oMultiply(scale1)))
        }

        override fun oGet(i: Int): Float { //TODO:rename you lazy ass
            if (i == 1) {
                return y
            } else if (i == 2) {
                return z
            }
            return x
        }

        override fun oSet(i: Int, value: Float): Float {
            if (i == 1) {
                y = value
            } else if (i == 2) {
                z = value
            } else {
                x = value
            }
            return value
        }

        fun oPluSet(i: Int, value: Float) {
            if (i == 1) {
                y += value
            } else if (i == 2) {
                z += value
            } else {
                x += value
            }
        }

        fun oMinSet(i: Int, value: Float) {
            if (i == 1) {
                y -= value
            } else if (i == 2) {
                z -= value
            } else {
                x -= value
            }
        }

        fun oMulSet(i: Int, value: Float) {
            if (i == 1) {
                y *= value
            } else if (i == 2) {
                z *= value
            } else {
                x *= value
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer?) {
            x = buffer.getFloat()
            y = buffer.getFloat()
            z = buffer.getFloat()
        }

        override fun Write(): ByteBuffer? {
            val buffer = ByteBuffer.allocate(BYTES)
            buffer.putFloat(x).putFloat(y).putFloat(z).flip()
            return buffer
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is idVec3) return false
            val idVec3 = o as idVec3
            if (java.lang.Float.compare(idVec3.x, x) != 0) return false
            return if (java.lang.Float.compare(idVec3.y, y) != 0) false else java.lang.Float.compare(idVec3.z, z) == 0
        }

        override fun hashCode(): Int {
            var result = if (x != +0.0f) java.lang.Float.floatToIntBits(x) else 0
            result = 31 * result + if (y != +0.0f) java.lang.Float.floatToIntBits(y) else 0
            result = 31 * result + if (z != +0.0f) java.lang.Float.floatToIntBits(z) else 0
            return result
        }

        fun ToVec2_oPluSet(v: idVec2?) {
            x += v.x
            y += v.y
        }

        fun ToVec2_oMinSet(v: idVec2?) {
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
            this.oSet(v)
        }

        fun ToVec2_NormalizeFast() {
            val v = ToVec2()
            v.NormalizeFast()
            this.oSet(v)
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
            fun oMultiply(a: Float, b: idVec3): idVec3 {
                return idVec3(b.x * a, b.y * a, b.z * a)
            }

            fun generateArray(length: Int): Array<idVec3> {
                val arr = arrayOf<idVec3>()
                for (i in 0..length) {
                    arr[i] = idVec3()
                }
                return arr
            }

            fun generateArray(firstDimensionSize: Int, secondDimensionSize: Int): Array<Array<idVec3>> {
                val out = Array<Array<idVec3>>(firstDimensionSize) { arrayOf<idVec3>(secondDimensionSize) }
                for (i in 0 until firstDimensionSize) {
                    out[i] = generateArray(secondDimensionSize)
                }
                return out
            }

            fun copyVec(arr: Array<idVec3>): Array<idVec3> {
                val out = generateArray(arr.size)
                for (i in out.indices) {
                    out[i].oSet(arr[i])
                }
                return out
            }

            fun toByteBuffer(vecs: Array<idVec3>?): ByteBuffer? {
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
    class idVec4 : idVec<idVec4?>, SERiAL {
        private val DBG_count = DBG_counter++
        var w = 0f
        var x = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(v: idVec4?) {
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

        fun Set(x: Float, y: Float, z: Float, w: Float) {
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
        override fun oMultiply(a: idVec4?): Float {
            return x * a.x + y * a.y + z * a.z + w * a.w
        }

        //public	idVec4			operator/( final  float a ) final ;
        override fun oMultiply(a: Float): idVec4? {
            return idVec4(x * a, y * a, z * a, w * a)
        }

        fun oMultiply(a: Float?): idVec4? { //for our reflection method
            return oMultiply(a.toFloat())
        }

        override fun oPlus(a: idVec4?): idVec4? {
            return idVec4(x + a.x, y + a.y, z + a.z, w + a.w)
        }

        override fun oMinus(a: idVec4?): idVec4? {
            return idVec4(x - a.x, y - a.y, z - a.z, w - a.w)
        }

        fun oNegative(): idVec4? {
            return idVec4(-x, -y, -z, -w)
        }

        //public	idVec4 &		operator+=( final  idVec4 &a );
        //public	idVec4			operator-( final  idVec4 &a ) final ;
        fun oMinSet(i: Int, value: Float) { //TODO:rename you lazy ass
            when (i) {
                1 -> y -= value
                2 -> z -= value
                3 -> w -= value
                else -> x -= value
            }
        }

        fun oMulSet(i: Int, value: Float) { //TODO:rename you lazy ass
            when (i) {
                1 -> y *= value
                2 -> z *= value
                3 -> w *= value
                else -> x *= value
            }
        }

        override fun oPluSet(a: idVec4?): idVec4? {
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
        fun Compare(a: idVec4?): Boolean { // exact compare, no epsilon
            return x == a.x && y == a.y && z == a.z && w == a.w
        }

        fun Compare(a: idVec4?, epsilon: Float): Boolean { // compare with epsilon
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

        fun Length(): Float {
            return idMath.Sqrt(x * x + y * y + z * z + w * w)
        }

        fun LengthSqr(): Float {
            return x * x + y * y + z * z + w * w
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z + w * w
            invLength = idMath.InvSqrt(sqrLength)
            x *= invLength
            y *= invLength
            z *= invLength
            w *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength = x * x + y * y + z * z + w * w
            invLength = idMath.RSqrt(sqrLength)
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
        fun ToVec2(): idVec2? {
//	return *reinterpret_cast<const idVec2 *>(this);
            return idVec2(x, y)
        }

        //public	float *			ToFloatPtr( void );
        @Deprecated("")
        fun ToVec3(): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(this);
            return idVec3(x, y, z)
        }

        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z, w) //TODO:put shit in array si we can referef it
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
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
        fun Lerp(v1: idVec4?, v2: idVec4?, l: Float) {
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

        override fun oSet(a: idVec4?): idVec4? {
            x = a.x
            y = a.y
            z = a.z
            w = a.w
            return this
        }

        fun oSet(a: idVec3): idVec4? {
            x = a.x
            y = a.y
            z = a.z
            return this
        }

        override fun oGet(i: Int): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> y
                2 -> z
                3 -> w
                else -> x
            }
        }

        override fun oSet(i: Int, value: Float): Float { //TODO:rename you lazy ass
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { w = it }
                else -> value.also { x = it }
            }
        }

        fun oPluSet(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.let { y += it; y }
                2 -> value.let { z += it; z }
                3 -> value.let { w += it; w }
                else -> value.let { x += it; x }
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            return ByteBuffer.allocate(BYTES)
        }

        override fun Read(buffer: ByteBuffer?) {
            x = buffer.getFloat()
            y = buffer.getFloat()
            z = buffer.getFloat()
            w = buffer.getFloat()
        }

        override fun Write(): ByteBuffer? {
            val buffer = AllocBuffer()
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w).flip()
            return buffer
        }

        override fun oDivide(a: Float): idVec4? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            @Transient
            val SIZE = 4 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            private var DBG_counter = 0
            fun generateArray(length: Int): Array<idVec4?>? {
                return Stream.generate { idVec4() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }

            fun toByteBuffer(vecs: Array<idVec4?>?): ByteBuffer? {
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
    class idVec5 : idVec<idVec5?>, SERiAL {
        var s = 0f
        var t = 0f
        var x = 0f
        var y = 0f
        var z = 0f

        constructor()
        constructor(xyz: idVec3, st: idVec2?) {
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
        constructor(a: idVec5?) {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
        }

        //public	float			operator[]( int index ) final ;
        override fun oGet(i: Int): Float { //TODO:rename you lazy sod
            return when (i) {
                1 -> y
                2 -> z
                3 -> s
                4 -> t
                else -> x
            }
        }

        override fun oSet(i: Int, value: Float): Float {
            return when (i) {
                1 -> value.also { y = it }
                2 -> value.also { z = it }
                3 -> value.also { s = it }
                4 -> value.also { t = it }
                else -> value.also { x = it }
            }
        }

        override fun oSet(a: idVec5?): idVec5? {
            x = a.x
            y = a.y
            z = a.z
            s = a.s
            t = a.t
            return this
        }

        //public	float &			operator[]( int index );
        //public	idVec5 &		operator=( final  idVec3 &a );
        fun oSet(a: idVec3): idVec5? {
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
        fun ToFloatPtr(): FloatArray? {
            return floatArrayOf(x, y, z) //TODO:array!?
        }

        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        fun Lerp(v1: idVec5?, v2: idVec5?, l: Float) {
            if (l <= 0.0f) {
                this.oSet(v1) //(*this) = v1;
            } else if (l >= 1.0f) {
                this.oSet(v2) //(*this) = v2;
            } else {
                x = v1.x + l * (v2.x - v1.x)
                y = v1.y + l * (v2.y - v1.y)
                z = v1.z + l * (v2.z - v1.z)
                s = v1.s + l * (v2.s - v1.s)
                t = v1.t + l * (v2.t - v1.t)
            }
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oPlus(a: idVec5?): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMinus(a: idVec5?): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMultiply(a: idVec5?): Float {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oDivide(a: Float): idVec5? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun ToVec3_oMulSet(axis: idMat3?) {
            this.oSet(ToVec3().oMulSet(axis))
        }

        fun ToVec3_oPluSet(origin: idVec3) {
            this.oSet(ToVec3().oPluSet(origin))
        }

        companion object {
            @Transient
            val SIZE = 5 * java.lang.Float.SIZE

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
            fun generateArray(length: Int): Array<idVec5?>? {
                return Stream.generate { idVec5() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }
    }

    //===============================================================
    //
    //	idVec6 - 6D vector
    //
    //===============================================================
    class idVec6 : idVec<idVec6?>, SERiAL {
        private val DBG_count = DBG_counter++

        //
        //
        var p: FloatArray? = FloatArray(6)

        constructor() {
            DBG_idVec6++
            val a = 0
        }

        constructor(a: FloatArray?) {
//	memcpy( p, a, 6 * sizeof( float ) );
            System.arraycopy(a, 0, p, 0, 6)
        }

        constructor(v: idVec6?) {
            System.arraycopy(v.p, 0, p, 0, 6)
        }

        constructor(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        fun Set(a1: Float, a2: Float, a3: Float, a4: Float, a5: Float, a6: Float) {
            p.get(0) = a1
            p.get(1) = a2
            p.get(2) = a3
            p.get(3) = a4
            p.get(4) = a5
            p.get(5) = a6
        }

        override fun Zero() {
            p.get(5) = 0.0f
            p.get(4) = p.get(5)
            p.get(3) = p.get(4)
            p.get(2) = p.get(3)
            p.get(1) = p.get(2)
            p.get(0) = p.get(1)
        }

        //public 	float			operator[]( final  int index ) final ;
        //public 	float &			operator[]( final  int index );
        fun oNegative(): idVec6? {
            return idVec6(-p.get(0), -p.get(1), -p.get(2), -p.get(3), -p.get(4), -p.get(5))
        }

        override fun oMultiply(a: Float): idVec6? {
            return idVec6(p.get(0) * a, p.get(1) * a, p.get(2) * a, p.get(3) * a, p.get(4) * a, p.get(5) * a)
        }

        //public 	idVec6			operator/( final  float a ) final ;
        override fun oMultiply(a: idVec6?): Float {
            return p.get(0) * a.p.get(0) + p.get(1) * a.p.get(1) + p.get(2) * a.p.get(2) + p.get(3) * a.p.get(3) + p.get(
                4
            ) * a.p.get(4) + p.get(5) * a.p.get(5)
        }

        //public 	idVec6			operator-( final  idVec6 &a ) final ;
        override fun oPlus(a: idVec6?): idVec6? {
            return idVec6(
                p.get(0) + a.p.get(0),
                p.get(1) + a.p.get(1),
                p.get(2) + a.p.get(2),
                p.get(3) + a.p.get(3),
                p.get(4) + a.p.get(4),
                p.get(5) + a.p.get(5)
            )
        }

        //public 	idVec6 &		operator*=( final  float a );
        //public 	idVec6 &		operator/=( final  float a );
        override fun oPluSet(a: idVec6?): idVec6? {
            p.get(0) += a.p.get(0)
            p.get(1) += a.p.get(1)
            p.get(2) += a.p.get(2)
            p.get(3) += a.p.get(3)
            p.get(4) += a.p.get(4)
            p.get(5) += a.p.get(5)
            return this
        }

        //public 	idVec6 &		operator-=( final  idVec6 &a );
        //
        //public 	friend idVec6	operator*( final  float a, final  idVec6 b );
        fun Compare(a: idVec6?): Boolean { // exact compare, no epsilon
            return (p.get(0) == a.p.get(0) && p.get(1) == a.p.get(1) && p.get(2) == a.p.get(2)
                    && p.get(3) == a.p.get(3) && p.get(4) == a.p.get(4) && p.get(5) == a.p.get(5))
        }

        fun Compare(a: idVec6?, epsilon: Float): Boolean { // compare with epsilon
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

        //public 	bool			operator==(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        //public 	bool			operator!=(	final  idVec6 &a ) final ;						// exact compare, no epsilon
        fun Length(): Float {
            return idMath.Sqrt(
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(
                    4
                ) * p.get(4) + p.get(5) * p.get(5)
            )
        }

        fun LengthSqr(): Float {
            return p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                4
            ) + p.get(5) * p.get(5)
        }

        fun Normalize(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength =
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                    4
                ) + p.get(5) * p.get(5)
            invLength = idMath.InvSqrt(sqrLength)
            p.get(0) *= invLength
            p.get(1) *= invLength
            p.get(2) *= invLength
            p.get(3) *= invLength
            p.get(4) *= invLength
            p.get(5) *= invLength
            return invLength * sqrLength
        }

        fun NormalizeFast(): Float { // returns length
            val sqrLength: Float
            val invLength: Float
            sqrLength =
                p.get(0) * p.get(0) + p.get(1) * p.get(1) + p.get(2) * p.get(2) + p.get(3) * p.get(3) + p.get(4) * p.get(
                    4
                ) + p.get(5) * p.get(5)
            invLength = idMath.RSqrt(sqrLength)
            p.get(0) *= invLength
            p.get(1) *= invLength
            p.get(2) *= invLength
            p.get(3) *= invLength
            p.get(4) *= invLength
            p.get(5) *= invLength
            return invLength * sqrLength
        }

        override fun GetDimension(): Int {
            return 6
        }

        fun SubVec3(index: Int): idVec3 {
//	return *reinterpret_cast<const idVec3 *>(p + index * 3);
            var index = index
            return idVec3(p.get(3.let { index *= it; index }), p.get(index + 1), p.get(index + 2))
        }

        //public 	idVec3 &		SubVec3( int index );
        fun ToFloatPtr(): FloatArray? {
            return p
        }

        //public 	float *			ToFloatPtr( void );
        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun oSet(a: idVec6?): idVec6? {
            p.get(0) = a.p.get(0)
            p.get(1) = a.p.get(1)
            p.get(2) = a.p.get(2)
            p.get(3) = a.p.get(3)
            p.get(4) = a.p.get(4)
            p.get(5) = a.p.get(5)
            return this
        }

        override fun oGet(index: Int): Float {
            return p.get(index)
        }

        override fun oSet(index: Int, value: Float): Float {
            return value.also { p.get(index) = it }
        }

        //
        //        public void setP(final int index, final float value) {
        //            p[index] = value;
        //        }
        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oMinus(a: idVec6?): idVec6? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oDivide(a: Float): idVec6? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun SubVec3_oSet(i: Int, v: idVec3) {
            System.arraycopy(v.ToFloatPtr(), 0, p, i * 3, 3)
        }

        fun SubVec3_oPluSet(i: Int, v: idVec3): idVec3 {
            val off = i * 3
            p.get(off + 0) += v.x
            p.get(off + 1) += v.y
            p.get(off + 2) += v.z
            return idVec3(p, off)
        }

        fun SubVec3_oMinSet(i: Int, v: idVec3): idVec3 {
            return SubVec3_oPluSet(i, v.oNegative())
        }

        fun SubVec3_oMulSet(i: Int, v: Float) {
            val off = i * 3
            p.get(off + 0) *= v
            p.get(off + 1) *= v
            p.get(off + 2) *= v
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
            val idVec6 = o as idVec6?
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
    class idVecX {
        var p // memory the vector is stored
                : FloatArray?
        var VECX_SIMD = false
        private var alloced // if -1 p points to data set with SetData
                : Int

        //
        //
        private var size // size of the vector
                : Int

        constructor() {
            alloced = 0
            size = alloced
            p = null
        }

        constructor(length: Int) {
            alloced = 0
            size = alloced
            p = null
            SetSize(length)
        }

        constructor(length: Int, data: FloatArray?) {
            alloced = 0
            size = alloced
            p = null
            SetData(length, data)
        }

        @Deprecated("")
        fun VECX_CLEAREND() { //TODO:is this function need for Java?
            var s = size
            ////            while (s < ((s + 3) & ~3)) {
            while (s < p.size) {
                p.get(s++) = 0.0f
            }
        }

        //public					~idVecX( void );
        //public	float			operator[]( const int index ) const;
        fun oGet(index: Int): Float {
            return p.get(index)
        }

        fun oSet(index: Int, value: Float): Float {
            return value.also { p.get(index) = it }
        }

        //public	float &			operator[]( const int index );
        //public	idVecX			operator-() const;
        fun oNegative(): idVecX? {
            var i: Int
            val m = idVecX()
            m.SetTempSize(size)
            i = 0
            while (i < size) {
                m.p.get(i) = -p.get(i)
                i++
            }
            return m
        }

        //public	idVecX &		operator=( const idVecX &a );
        fun oSet(a: idVecX?): idVecX? {
            SetSize(a.size)
            System.arraycopy(a.p, 0, p, 0, p.size)
            tempIndex = 0
            return this
        }

        fun oMultiply(a: Float): idVecX? {
            val m = idVecX()
            m.SetTempSize(size)
            if (VECX_SIMD) {
                Simd.SIMDProcessor.Mul16(m.p, p, a, size)
            } else {
                var i: Int
                i = 0
                while (i < size) {
                    m.p.get(i) = p.get(i) * a
                    i++
                }
            }
            return m
        }

        //public	idVecX			operator/( const float a ) const;
        //public	float			operator*( const idVecX &a ) const;
        fun oMultiply(a: idVecX?): Float {
            var i: Int
            var sum = 0.0f
            assert(size == a.size)
            i = 0
            while (i < size) {
                sum += p.get(i) * a.p.get(i)
                i++
            }
            return sum
        }

        //public	idVecX			operator-( const idVecX &a ) const;
        //public	idVecX			operator+( const idVecX &a ) const;
        fun oPlus(a: idVecX?): idVecX? {
            val m = idVecX()
            assert(size == a.size)
            m.SetTempSize(size)
            //#ifdef VECX_SIMD
//	SIMDProcessor->Add16( m.p, p, a.p, size );
//#else
            var i: Int
            i = 0
            while (i < size) {
                m.p.get(i) = p.get(i) + a.p.get(i)
                i++
            }
            //#endif
            return m
        }

        //public	idVecX &		operator*=( const float a );
        fun oMulSet(a: Float): idVecX? {
//#ifdef VECX_SIMD
//	SIMDProcessor->MulAssign16( p, a, size );
//#else
            var i: Int
            i = 0
            while (i < size) {
                p.get(i) *= a
                i++
            }
            //#endif
            return this
        }

        //public	idVecX &		operator/=( const float a );
        //public	idVecX &		operator+=( const idVecX &a );
        //public	idVecX &		operator-=( const idVecX &a );
        //public	friend idVecX	operator*( const float a, const idVecX b );
        fun Compare(a: idVecX?): Boolean { // exact compare, no epsilon
            var i: Int
            assert(size == a.size)
            i = 0
            while (i < size) {
                if (p.get(i) != a.p.get(i)) {
                    return false
                }
                i++
            }
            return true
        }

        fun Compare(a: idVecX?, epsilon: Float): Boolean { // compare with epsilon
            var i: Int
            assert(size == a.size)
            i = 0
            while (i < size) {
                if (Math.abs(p.get(i) - a.p.get(i)) > epsilon) {
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
                if (p != null) {
                    p = null
                }
                p = FloatArray(alloc)
                alloced = alloc
            }
            size = newSize
            VECX_CLEAREND()
        }

        @JvmOverloads
        fun ChangeSize(newSize: Int, makeZero: Boolean = false) {
            val alloc = newSize + 3 and 3.inv()
            if (alloc > alloced && alloced != -1) {
                val oldVec = p
                //		p = (float *) Mem_Alloc16( alloc * sizeof( float ) );
                p = FloatArray(alloc)
                alloced = alloc
                if (oldVec != null) {
                    System.arraycopy(oldVec, 0, p, 0, size)
                    //			Mem_Free16( oldVec );//garbage collect me!
                } //TODO:ifelse
                if (makeZero) {
                    // zero any new elements
                    for (i in size until newSize) {
                        p.get(i) = 0.0f
                    }
                }
            }
            size = newSize
            VECX_CLEAREND()
        }

        fun GetSize(): Int {
            return size
        }

        fun SetData(length: Int, data: FloatArray?) {
            if (p != null && (p.get(0) < tempPtr.get(0) || p.get(0) >= tempPtr.get(0) + VECX_MAX_TEMP) && alloced != -1) {
//		Mem_Free16( p );
                p = null
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

        @JvmOverloads
        fun Random(seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            var i: Int
            val c: Float
            val rnd = idRandom(seed)
            c = u - l
            i = 0
            while (i < size) {
                p.get(i) = l + rnd.RandomFloat() * c
                i++
            }
        }

        @JvmOverloads
        fun Random(length: Int, seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
            var i: Int
            val c: Float
            val rnd = idRandom(seed)
            SetSize(length)
            c = u - l
            i = 0
            while (i < size) {
                if (idMatX.Companion.DISABLE_RANDOM_TEST) { //for testing.
                    p.get(i) = i
                } else {
                    p.get(i) = l + rnd.RandomFloat() * c
                }
                i++
            }
        }

        fun Negate() {
//#ifdef VECX_SIMD
//	SIMDProcessor.Negate16( p, size );
//#else
            var oGet: Int
            oGet = 0
            while (oGet < size) {
                p.get(oGet) = -p.get(oGet)
                oGet++
            }
            //#endif
        }

        fun Clamp(min: Float, max: Float) {
            var i: Int
            i = 0
            while (i < size) {
                if (p.get(i) < min) {
                    p.get(i) = min
                } else if (p.get(i) > max) {
                    p.get(i) = max
                }
                i++
            }
        }

        fun SwapElements(e1: Int, e2: Int): idVecX? {
            val tmp: Float
            tmp = p.get(e1)
            p.get(e1) = p.get(e2)
            p.get(e2) = tmp
            return this
        }

        fun Length(): Float {
            var i: Int
            var sum = 0.0f
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            return idMath.Sqrt(sum)
        }

        fun LengthSqr(): Float {
            var i: Int
            var sum = 0.0f
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            return sum
        }

        fun Normalize(): idVecX? {
            var i: Int
            val m = idVecX()
            val invSqrt: Float
            var sum = 0.0f
            m.SetTempSize(size)
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                m.p.get(i) = p.get(i) * invSqrt
                i++
            }
            return m
        }

        fun NormalizeSelf(): Float {
            val invSqrt: Float
            var sum = 0.0f
            var i: Int
            i = 0
            while (i < size) {
                sum += p.get(i) * p.get(i)
                i++
            }
            invSqrt = idMath.InvSqrt(sum)
            i = 0
            while (i < size) {
                p.get(i) *= invSqrt
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
            return idVec3(p.get(3.let { index *= it; index }), p.get(index + 1), p.get(index + 2))
        }
        //public	idVec3 &		SubVec3( int index );

        @Deprecated("readonly")
        fun SubVec6(index: Int): idVec6? {
            var index = index
            assert(index >= 0 && index * 6 + 6 <= size)
            //	return *reinterpret_cast<idVec6 *>(p + index * 6);
            return idVec6(
                p.get(6.let { index *= it; index }),
                p.get(index + 1),
                p.get(index + 2),
                p.get(index + 3),
                p.get(index + 4),
                p.get(index + 5)
            )
        }

        //public	idVec6 &		SubVec6( int index );
        fun ToFloatPtr(): FloatArray? {
            return p
        }

        //public	float *			ToFloatPtr( void );
        @JvmOverloads
        fun ToString(precision: Int = 2): String? {
            return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
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
            p.get(i * 3 + 0) = v.oGet(0)
            p.get(i * 3 + 1) = v.oGet(1)
            p.get(i * 3 + 2) = v.oGet(2)
        }

        fun SubVec6_oSet(i: Int, v: idVec6?) {
            p.get(i * 6 + 0) = v.oGet(0)
            p.get(i * 6 + 1) = v.oGet(1)
            p.get(i * 6 + 2) = v.oGet(2)
            p.get(i * 6 + 3) = v.oGet(3)
            p.get(i * 6 + 4) = v.oGet(4)
            p.get(i * 6 + 5) = v.oGet(5)
        }

        fun SubVec6_oPluSet(i: Int, v: idVec6?) {
            p.get(i * 6 + 0) += v.oGet(0)
            p.get(i * 6 + 1) += v.oGet(1)
            p.get(i * 6 + 2) += v.oGet(2)
            p.get(i * 6 + 3) += v.oGet(3)
            p.get(i * 6 + 4) += v.oGet(4)
            p.get(i * 6 + 5) += v.oGet(5)
        }

        companion object {
            // friend class idMatX;
            const val VECX_MAX_TEMP = 1024
            private val temp: FloatArray? = FloatArray(VECX_MAX_TEMP + 4) // used to store intermediate results
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
    internal class idPolar3 {
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

        fun Set(radius: Float, theta: Float, phi: Float) {
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
            return idVec3(cp.getVal() * radius * ct.getVal(), cp.getVal() * radius * st.getVal(), radius * sp.getVal())
        }
    }
}