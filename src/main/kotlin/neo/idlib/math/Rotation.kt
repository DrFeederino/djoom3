package neo.idlib.math

import neo.idlib.containers.CFloat
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector.idVec3
import kotlin.math.floor

/**
 *
 */
class Rotation {
    class idRotation() {
        var angle // angle of rotation in degrees
                = 0f
        var axis // rotation axis
                : idMat3
        var axisValid // true if rotation axis is valid
                = false

        //private:
        val origin // origin of rotation
                : idVec3
        val vec // normalized vector to rotate around
                : idVec3

        constructor(rotationOrigin: idVec3, rotationVec: idVec3, rotationAngle: Float) : this() {
            origin.oSet(rotationOrigin)
            vec.oSet(rotationVec)
            angle = rotationAngle
            axisValid = false
        }

        constructor(rotation: idRotation) : this() {
            oSet(rotation)
        }

        fun Set(rotationOrigin: idVec3, rotationVec: idVec3, rotationAngle: Float) {
            origin.oSet(rotationOrigin)
            vec.oSet(rotationVec)
            angle = rotationAngle
            axisValid = false
        }

        fun SetOrigin(rotationOrigin: idVec3) {
            origin.oSet(rotationOrigin)
        }

        // has to be normalized	
        fun SetVec(rotationVec: idVec3) {
            vec.oSet(rotationVec)
            axisValid = false
        }

        // has to be normalized
        fun SetVec(x: Float, y: Float, z: Float) {
            vec.oSet(0, x)
            vec.oSet(1, y)
            vec.oSet(2, z)
            axisValid = false
        }

        fun SetAngle(rotationAngle: Float) {
            angle = rotationAngle
            axisValid = false
        }

        fun Scale(s: Float) {
            angle *= s
            axisValid = false
        }

        fun ReCalculateMatrix() {
            axisValid = false
            ToMat3()
        }

        fun GetOrigin(): idVec3 {
            return origin
        }

        fun GetVec(): idVec3 {
            return vec
        }

        fun GetAngle(): Float {
            return angle
        }

        //
        //	idRotation			operator-() const;										// flips rotation
        fun times(s: Float): idRotation { // scale rotation
            return idRotation(origin, vec, angle * s)
        }

        //	idRotation			operator/( const float s ) const;						// scale rotation
        //	idRotation &		operator*=( const float s );							// scale rotation
        //	idRotation &		operator/=( const float s );							// scale rotation
        operator fun times(v: idVec3): idVec3 { // rotate vector
            if (!axisValid) {
                ToMat3()
            }
            return (v - origin) * axis + origin
        }

        //
        //	friend idRotation	operator*( const float s, const idRotation &r );		// scale rotation
        //	friend idVec3		operator*( const idVec3 &v, const idRotation &r );		// rotate vector
        //	friend idVec3 &		operator*=( idVec3 &v, const idRotation &r );			// rotate vector
        fun ToAngles(): idAngles {
            return ToMat3().ToAngles()
        }

        //	idQuat				ToQuat( void ) const;
        fun ToMat3(): idMat3 {
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
            val a: Float
            val x: Float
            val y: Float
            val z: Float
            val c = CFloat()
            val s = CFloat()
            if (axisValid) {
                return axis
            }
            a = angle * (idMath.M_DEG2RAD * 0.5f)
            idMath.SinCos(a, s, c)
            x = vec.oGet(0) * s._val
            y = vec.oGet(1) * s._val
            z = vec.oGet(2) * s._val
            x2 = x + x
            y2 = y + y
            z2 = z + z
            xx = x * x2
            xy = x * y2
            xz = x * z2
            yy = y * y2
            yz = y * z2
            zz = z * z2
            wx = c._val * x2
            wy = c._val * y2
            wz = c._val * z2
            axis.oSet(0, 0, 1.0f - (yy + zz))
            axis.oSet(0, 1, xy - wz)
            axis.oSet(0, 2, xz + wy)
            axis.oSet(1, 0, xy + wz)
            axis.oSet(1, 1, 1.0f - (xx + zz))
            axis.oSet(1, 2, yz - wx)
            axis.oSet(2, 0, xz - wy)
            axis.oSet(2, 1, yz + wx)
            axis.oSet(2, 2, 1.0f - (xx + yy))
            axisValid = true
            return axis
        }

        //	idMat4				ToMat4( void ) const;
        fun ToAngularVelocity(): idVec3 {
            return vec.times(Math_h.DEG2RAD(angle))
        }

        fun RotatePoint(point: idVec3) {
            if (!axisValid) {
                ToMat3()
            }
            point.oSet((point - origin) * axis + origin)
        }

        fun Normalize180() {
            angle -= (floor((angle / 360.0f).toDouble()) * 360.0f).toFloat()
            if (angle > 180.0f) {
                angle -= 360.0f
            } else if (angle < -180.0f) {
                angle += 360.0f
            }
        }

        //	void				Normalize360( void );
        //
        fun oSet(other: idRotation) {
            origin.oSet(other.origin)
            vec.oSet(other.vec)
            angle = other.angle
            axisValid = other.axisValid
        }

        //
        //
        //        friend class idAngles;
        //	friend class idQuat;
        //	friend class idMat3;
        //
        //public:
        init {
            origin = idVec3()
            vec = idVec3()
            axis = idMat3()
        }
    }
}