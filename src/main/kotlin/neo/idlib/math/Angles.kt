package neo.idlib.math

import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.math.Math_h.DEG2RAD
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Quat.idQuat
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer

/**
 *
 */
object Angles {
    /*
     ===============================================================================

     Euler angles

     ===============================================================================
     */
    //
    // angle indexes
    const val PITCH = 0 // up / down
    const val ROLL = 2 // fall over
    const val YAW = 1 // left / right
    private val ang_zero: idAngles = idAngles(0.0f, 0.0f, 0.0f) //TODO:make sure new instances are created everytime.
    fun getAng_zero(): idAngles {
        return idAngles(ang_zero)
    }

    class idAngles : SERiAL {
        var pitch = 0f
        var roll = 0f
        var yaw = 0f

        constructor()
        constructor(pitch: Float, yaw: Float, roll: Float) {
            this.pitch = pitch
            this.yaw = yaw
            this.roll = roll
        }

        constructor(v: idVec3) {
            pitch = v.x
            yaw = v.y
            roll = v.z
        }

        constructor(a: idAngles) {
            pitch = a.pitch
            yaw = a.yaw
            roll = a.roll
        }

        fun set(pitch: Float, yaw: Float, roll: Float) {
            this.pitch = pitch
            this.yaw = yaw
            this.roll = roll
        }
        //
        //public	float			operator[]( int index ) final ;
        /**
         * @return @deprecated for post constructor use. seeing as how the
         * constructor sets everything to zero anyways.
         */
        @Deprecated("")
        fun Zero(): idAngles {
            roll = 0.0f
            yaw = roll
            pitch = yaw
            return this
        }

        operator fun get(index: Int): Float {
            assert(index in 0..2)
            return when (index) {
                1 -> yaw
                2 -> roll
                else -> pitch
            }
        }

        //public	float &			operator[]( int index );
        operator fun set(index: Int, value: Float) {
            when (index) {
                1 -> yaw = value
                2 -> roll = value
                else -> pitch = value
            }
        }

        fun plusAssign(index: Int, value: Float): Float {
            return when (index) {
                1 -> value.let { yaw += it; yaw }
                2 -> value.let { roll += it; roll }
                else -> value.let { pitch += it; pitch }
            }
        }

        fun minusAssign(index: Int, value: Float): Float {
            return when (index) {
                1 -> value.let { yaw -= it; yaw }
                2 -> value.let { roll -= it; roll }
                else -> value.let { pitch -= it; pitch }
            }
        }

        operator fun unaryMinus(): idAngles { // negate angles, in general not the inverse rotation
            return idAngles(-pitch, -yaw, -roll)
        }

        //public	idAngles &		operator=( final  idAngles &a );
        fun set(a: idAngles): idAngles {
            pitch = a.pitch
            yaw = a.yaw
            roll = a.roll
            return this
        }

        operator fun plus(a: idAngles): idAngles {
            return idAngles(pitch + a.pitch, yaw + a.yaw, roll + a.roll)
        }

        operator fun plus(a: idVec3): idAngles {
            return idAngles(pitch + a.x, yaw + a.y, roll + a.z)
        }

        fun plusAssign(a: idAngles): idAngles {
            pitch += a.pitch
            yaw += a.yaw
            roll += a.roll
            return this
        }

        operator fun minus(a: idAngles): idAngles {
            return idAngles(pitch - a.pitch, yaw - a.yaw, roll - a.roll)
        }

        fun minusAssign(a: idAngles): idAngles {
            pitch -= a.pitch
            yaw -= a.yaw
            roll -= a.roll
            return this
        }

        operator fun times(a: Float): idAngles {
            return idAngles(pitch * a, yaw * a, roll * a)
        }

        fun timesAssign(a: Float): idAngles {
            pitch *= a
            yaw *= a
            roll *= a
            return this
        }

        operator fun div(a: Float): idAngles {
            val inva = 1.0f / a
            return idAngles(pitch * inva, yaw * inva, roll * inva)
        }

        //
        fun divAssign(a: Float): idAngles {
            val inva = 1.0f / a
            pitch *= inva
            yaw *= inva
            roll *= inva
            return this
        }

        //
        fun Compare(a: idAngles): Boolean { // exact compare, no epsilon
            return a.pitch == pitch && a.yaw == yaw && a.roll == roll
        }

        fun Compare(a: idAngles, epsilon: Float): Boolean { // compare with epsilon
            if (Math.abs(pitch - a.pitch) > epsilon) {
                return false
            }
            return if (Math.abs(yaw - a.yaw) > epsilon) {
                false
            } else Math.abs(roll - a.roll) <= epsilon
        }

        //public	boolean 			operator==(	final  idAngles &a ) final ;						// exact compare, no epsilon
        //public	boolean 			operator!=(	final  idAngles &a ) final ;						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 73 * hash + java.lang.Float.floatToIntBits(pitch)
            hash = 73 * hash + java.lang.Float.floatToIntBits(yaw)
            hash = 73 * hash + java.lang.Float.floatToIntBits(roll)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idAngles
            if (java.lang.Float.floatToIntBits(pitch) != java.lang.Float.floatToIntBits(other.pitch)) {
                return false
            }
            return if (java.lang.Float.floatToIntBits(yaw) != java.lang.Float.floatToIntBits(other.yaw)) {
                false
            } else java.lang.Float.floatToIntBits(roll) == java.lang.Float.floatToIntBits(other.roll)
        }
        //
        /**
         * ================= idAngles::Normalize360
         *
         *
         * returns angles normalized to the range [0 <= angle < 360]
         * =================
         */
        fun Normalize360(): idAngles { // normalizes 'this'
            var i: Int
            i = 0
            while (i < 3) {
                if (get(i) >= 360.0f || get(i) < 0.0f) {
                    this.plusAssign(i, -Math.floor((get(i) / 360.0f).toDouble()).toFloat() * 360.0f)
                    if (get(i) >= 360.0f) {
                        this.plusAssign(i, -360.0f)
                    }
                    if (get(i) < 0.0f) {
                        this.plusAssign(i, 360.0f)
                    }
                }
                i++
            }
            return this
        }

        /**
         * ================= idAngles::Normalize180
         *
         *
         * returns angles normalized to the range [-180 < angle <= 180]
         * =================
         */
        fun Normalize180(): idAngles { // normalizes 'this'
            Normalize360()
            if (pitch > 180.0f) {
                pitch -= 360.0f
            }
            if (yaw > 180.0f) {
                yaw -= 360.0f
            }
            if (roll > 180.0f) {
                roll -= 360.0f
            }
            return this
        }

        //
        fun Clamp(min: idAngles, max: idAngles) {
            if (pitch < min.pitch) {
                pitch = min.pitch
            } else if (pitch > max.pitch) {
                pitch = max.pitch
            }
            if (yaw < min.yaw) {
                yaw = min.yaw
            } else if (yaw > max.yaw) {
                yaw = max.yaw
            }
            if (roll < min.roll) {
                roll = min.roll
            } else if (roll > max.roll) {
                roll = max.roll
            }
        }

        //
        fun GetDimension(): Int {
            return 3
        }

        //
        @JvmOverloads
        fun ToVectors(forward: idVec3, right: idVec3? = null, up: idVec3? = null) {
            val sr = CFloat()
            val sp = CFloat()
            val sy = CFloat()
            val cr = CFloat()
            val cp = CFloat()
            val cy = CFloat()
            idMath.SinCos(Math_h.DEG2RAD(yaw), sy, cy)
            idMath.SinCos(Math_h.DEG2RAD(pitch), sp, cp)
            idMath.SinCos(Math_h.DEG2RAD(roll), sr, cr)
            forward.set(cp._val * cy._val, cp._val * sy._val, -sp._val)
            right?.set(
                -sr._val * sp._val * cy._val + cr._val * sy._val,
                -sr._val * sp._val * sy._val + -cr._val * cy._val,
                -sr._val * cp._val
            )
            up?.set(
                cr._val * sp._val * cy._val + -sr._val * -sy._val,
                cr._val * sp._val * sy._val + -sr._val * cy._val,
                cr._val * cp._val
            )
        }

        fun ToForward(): idVec3 {
            val sp = CFloat()
            val sy = CFloat()
            val cp = CFloat()
            val cy = CFloat()
            idMath.SinCos(Math_h.DEG2RAD(yaw), sy, cy)
            idMath.SinCos(Math_h.DEG2RAD(pitch), sp, cp)
            return idVec3(cp._val * cy._val, cp._val * sy._val, -sp._val)
        }

        fun ToQuat(): idQuat {
            val sx = CFloat()
            val cx = CFloat()
            val sy = CFloat()
            val cy = CFloat()
            val sz = CFloat()
            val cz = CFloat()
            val sxcy: Float
            val cxcy: Float
            val sxsy: Float
            val cxsy: Float
            idMath.SinCos(Math_h.DEG2RAD(yaw) * 0.5f, sz, cz)
            idMath.SinCos(Math_h.DEG2RAD(pitch) * 0.5f, sy, cy)
            idMath.SinCos(Math_h.DEG2RAD(roll) * 0.5f, sx, cx)
            sxcy = sx._val * cy._val
            cxcy = cx._val * cy._val
            sxsy = sx._val * sy._val
            cxsy = cx._val * sy._val
            return idQuat(
                cxsy * sz._val - sxcy * cz._val,
                -cxsy * cz._val - sxcy * sz._val,
                sxsy * cz._val - cxcy * sz._val,
                cxcy * cz._val + sxsy * sz._val
            )
        }

        fun ToRotation(): idRotation {
            val vec = idVec3()
            var angle: Float
            val w: Float
            val sx = CFloat()
            val cx = CFloat()
            val sy = CFloat()
            val cy = CFloat()
            val sz = CFloat()
            val cz = CFloat()
            val sxcy: Float
            val cxcy: Float
            val sxsy: Float
            val cxsy: Float
            if (pitch == 0.0f) {
                if (yaw == 0.0f) {
                    return idRotation(Vector.getVec3_origin(), idVec3(-1.0f, 0.0f, 0.0f), roll)
                }
                if (roll == 0.0f) {
                    return idRotation(Vector.getVec3_origin(), idVec3(0.0f, 0.0f, -1.0f), yaw)
                }
            } else if (yaw == 0.0f && roll == 0.0f) {
                return idRotation(Vector.getVec3_origin(), idVec3(0.0f, -1.0f, 0.0f), pitch)
            }
            idMath.SinCos(Math_h.DEG2RAD(yaw) * 0.5f, sz, cz)
            idMath.SinCos(Math_h.DEG2RAD(pitch) * 0.5f, sy, cy)
            idMath.SinCos(Math_h.DEG2RAD(roll) * 0.5f, sx, cx)
            sxcy = sx._val * cy._val
            cxcy = cx._val * cy._val
            sxsy = sx._val * sy._val
            cxsy = cx._val * sy._val
            vec.x = cxsy * sz._val - sxcy * cz._val
            vec.y = -cxsy * cz._val - sxcy * sz._val
            vec.z = sxsy * cz._val - cxcy * sz._val
            w = cxcy * cz._val + sxsy * sz._val
            angle = idMath.ACos(w)
            if (angle == 0.0f) {
                vec.set(0.0f, 0.0f, 1.0f)
            } else {
                //vec *= (1.0f / sin( angle ));
                vec.Normalize()
                vec.FixDegenerateNormal()
                angle *= 2.0f * idMath.M_RAD2DEG
            }
            return idRotation(Vector.getVec3_origin(), vec, angle)
        }

        fun ToMat3(): idMat3 {
            val mat = idMat3()
            val sr = CFloat()
            val sp = CFloat()
            val sy = CFloat()
            val cr = CFloat()
            val cp = CFloat()
            val cy = CFloat()
            idMath.SinCos(Math_h.DEG2RAD(yaw), sy, cy)
            idMath.SinCos(Math_h.DEG2RAD(pitch), sp, cp)
            idMath.SinCos(Math_h.DEG2RAD(roll), sr, cr)
            mat.setRow(0, cp._val * cy._val, cp._val * sy._val, -sp._val)
            mat.setRow(
                1,
                sr._val * sp._val * cy._val + cr._val * -sy._val,
                sr._val * sp._val * sy._val + cr._val * cy._val,
                sr._val * cp._val
            )
            mat.setRow(
                2,
                cr._val * sp._val * cy._val + -sr._val * -sy._val,
                cr._val * sp._val * sy._val + -sr._val * cy._val,
                cr._val * cp._val
            )
            return mat
        }

        fun ToMat4(): idMat4 {
            return ToMat3().ToMat4()
        }

        fun ToAngularVelocity(): idVec3 {
            val rotation = ToRotation()
            return rotation.GetVec() * DEG2RAD(rotation.GetAngle())
        }

        fun ToFloatPtr(): FloatArray {
            throw TODO_Exception()
        }

        //public	float *			ToFloatPtr( void );
        @JvmOverloads
        fun ToString(precision: Int = 2): String {
            return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
        }

        override fun toString(): String {
            return "idAngles{" +
                    "pitch=" + pitch +
                    ", yaw=" + yaw +
                    ", roll=" + roll +
                    '}'
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
            fun times(a: Float, b: idAngles): idAngles {
                return idAngles(a * b.pitch, a * b.yaw, a * b.roll)
            }
        }
    }
}