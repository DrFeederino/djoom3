package neo.idlib.math.Matrix

import neo.idlib.Text.Str.idStr
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Quat.idCQuat
import neo.idlib.math.Quat.idQuat
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

//===============================================================
//
//	idMat3 - 3x3 matrix
//
//	NOTE:	matrix is column-major
//
//===============================================================
class idMat3 {
    val mat: Array<idVec3>? = idVec3.Companion.generateArray(3)
    private val DBG_count: Int = idMat3.Companion.DBG_counter++

    constructor()
    constructor(x: idVec3, y: idVec3, z: idVec3) {
        mat.get(0).x = x.x
        mat.get(0).y = x.y
        mat.get(0).z = x.z
        //
        mat.get(1).x = y.x
        mat.get(1).y = y.y
        mat.get(1).z = y.z
        //
        mat.get(2).x = z.x
        mat.get(2).y = z.y
        mat.get(2).z = z.z
    }

    constructor(xx: Float, xy: Float, xz: Float, yx: Float, yy: Float, yz: Float, zx: Float, zy: Float, zz: Float) {
        mat.get(0).x = xx
        mat.get(0).y = xy
        mat.get(0).z = xz
        //
        mat.get(1).x = yx
        mat.get(1).y = yy
        mat.get(1).z = yz
        //
        mat.get(2).x = zx
        mat.get(2).y = zy
        mat.get(2).z = zz
    }

    constructor(m: idMat3) {
        mat.get(0).x = m.mat[0].x
        mat.get(0).y = m.mat[0].y
        mat.get(0).z = m.mat[0].z
        //
        mat.get(1).x = m.mat[1].x
        mat.get(1).y = m.mat[1].y
        mat.get(1).z = m.mat[1].z
        //
        mat.get(2).x = m.mat[2].x
        mat.get(2).y = m.mat[2].y
        mat.get(2).z = m.mat[2].z
    }

    constructor(src: Array<FloatArray?>?) {
//	memcpy( mat, src, 3 * 3 * sizeof( float ) );
        mat.get(0).oSet(idVec3(src.get(0).get(0), src.get(0).get(1), src.get(0).get(2)))
        mat.get(1).oSet(idVec3(src.get(1).get(0), src.get(1).get(1), src.get(1).get(2)))
        mat.get(2).oSet(idVec3(src.get(2).get(0), src.get(2).get(1), src.get(2).get(2)))
    }

    //public	idVec3			operator*( const idVec3 &vec ) const;
    fun oGet(index: Int): idVec3 {
        return mat.get(index)
    }

    //public	idMat3			operator*( const idMat3 &a ) const;
    fun oGet(index1: Int, index2: Int): Float {
        return mat.get(index1).oGet(index2)
    }

    //public	idMat3			operator+( const idMat3 &a ) const;
    fun oSet(index: Int, vec3: idVec3) {
        mat.get(index).oSet(vec3)
    }

    //public	idMat3			operator-( const idMat3 &a ) const;
    //public	idMat3			operator-() const;
    fun oNegative(): idMat3 {
        return idMat3(
            -mat.get(0).x, -mat.get(0).y, -mat.get(0).z,
            -mat.get(1).x, -mat.get(1).y, -mat.get(1).z,
            -mat.get(2).x, -mat.get(2).y, -mat.get(2).z
        )
    }

    //public	idMat3 &		operator*=( const float a );
    fun oMultiply(a: Float): idMat3 {
        return idMat3(
            mat.get(0).x * a, mat.get(0).y * a, mat.get(0).z * a,
            mat.get(1).x * a, mat.get(1).y * a, mat.get(1).z * a,
            mat.get(2).x * a, mat.get(2).y * a, mat.get(2).z * a
        )
    }

    //public	idMat3 &		operator*=( const idMat3 &a );
    fun oMultiply(vec: idVec3): idVec3 {
        return idVec3(
            mat.get(0).x * vec.x + mat.get(1).x * vec.y + mat.get(2).x * vec.z,
            mat.get(0).y * vec.x + mat.get(1).y * vec.y + mat.get(2).y * vec.z,
            mat.get(0).z * vec.x + mat.get(1).z * vec.y + mat.get(2).z * vec.z
        )
    }

    //public	idMat3 &		operator+=( const idMat3 &a );
    fun oMultiply(a: idMat3): idMat3 {
        var i: Int
        var j: Int
        val m1Ptr: FloatArray?
        val m2Ptr: FloatArray?
        //            float dstPtr;
        val dst = idMat3()
        m1Ptr = ToFloatPtr() //reinterpret_cast<const float *>(this);
        m2Ptr = a.ToFloatPtr() //reinterpret_cast<const float *>(&a);
        //	dstPtr = reinterpret_cast<float *>(&dst);
        i = 0
        while (i < 3) {
            j = 0
            while (j < 3) {
                val value =
                    m1Ptr.get(i * 3 + 0) * m2Ptr[0 * 3 + j] + m1Ptr.get(i * 3 + 1) * m2Ptr[1 * 3 + j] + m1Ptr.get(i * 3 + 2) * m2Ptr[2 * 3 + j]
                dst.oSet(i, j, value)
                j++
            }
            i++
        }
        return dst
    }

    //public	idMat3 &		operator-=( const idMat3 &a );
    fun oPlus(a: idMat3): idMat3 {
        return idMat3(
            mat.get(0).x + a.mat[0].x, mat.get(0).y + a.mat[0].y, mat.get(0).z + a.mat[0].z,
            mat.get(1).x + a.mat[1].x, mat.get(1).y + a.mat[1].y, mat.get(1).z + a.mat[1].z,
            mat.get(2).x + a.mat[2].x, mat.get(2).y + a.mat[2].y, mat.get(2).z + a.mat[2].z
        )
    }

    //
    //public	friend idMat3	operator*( const float a, const idMat3 &mat );
    fun oMinus(a: idMat3): idMat3 {
        return idMat3(
            mat.get(0).x - a.mat[0].x, mat.get(0).y - a.mat[0].y, mat.get(0).z - a.mat[0].z,
            mat.get(1).x - a.mat[1].x, mat.get(1).y - a.mat[1].y, mat.get(1).z - a.mat[1].z,
            mat.get(2).x - a.mat[2].x, mat.get(2).y - a.mat[2].y, mat.get(2).z - a.mat[2].z
        )
    }

    //public	friend idVec3	operator*( const idVec3 &vec, const idMat3 &mat );
    fun oMulSet(a: Float): idMat3 {
        mat.get(0).x *= a
        mat.get(0).y *= a
        mat.get(0).z *= a
        //
        mat.get(1).x *= a
        mat.get(1).y *= a
        mat.get(1).z *= a
        //
        mat.get(2).x *= a
        mat.get(2).y *= a
        mat.get(2).z *= a
        return this
    }

    //public	friend idVec3 &	operator*=( idVec3 &vec, const idMat3 &mat );
    fun oMulSet(a: idMat3): idMat3 {
        var i: Int
        var j: Int
        val dst = FloatArray(3)
        i = 0
        while (i < 3) {
            j = 0
            while (j < 3) {
                dst[j] =
                    mat.get(i).x * a.mat[0].oGet(j) + mat.get(i).y * a.mat[1].oGet(j) + mat.get(i).z * a.mat[2].oGet(j)
                j++
            }
            this.oSet(i, 0, dst[0])
            this.oSet(i, 1, dst[1])
            this.oSet(i, 2, dst[2])
            i++
        }
        return this
    }

    //
    fun oPluSet(a: Float): idMat3 {
        mat.get(0).x += a
        mat.get(0).y += a
        mat.get(0).z += a
        //
        mat.get(1).x += a
        mat.get(1).y += a
        mat.get(1).z += a
        //
        mat.get(2).x += a
        mat.get(2).y += a
        mat.get(2).z += a
        return this
    }

    fun oMinSet(a: Float): idMat3 {
        mat.get(0).x -= a
        mat.get(0).y -= a
        mat.get(0).z -= a
        //
        mat.get(1).x -= a
        mat.get(1).y -= a
        mat.get(1).z -= a
        //
        mat.get(2).x -= a
        mat.get(2).y -= a
        mat.get(2).z -= a
        return this
    }

    //public	bool			operator==( const idMat3 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat3 &a ) const;					// exact compare, no epsilon
    fun Compare(a: idMat3): Boolean { // exact compare, no epsilon
        return (mat.get(0).Compare(a.mat[0])
                && mat.get(1).Compare(a.mat[1])
                && mat.get(2).Compare(a.mat[2]))
    }

    fun Compare(a: idMat3, epsilon: Float): Boolean { // compare with epsilon
        return (mat.get(0).Compare(a.mat[0], epsilon)
                && mat.get(1).Compare(a.mat[1], epsilon)
                && mat.get(2).Compare(a.mat[2], epsilon))
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 37 * hash + mat.get(0).hashCode()
        hash = 37 * hash + mat.get(1).hashCode()
        hash = 37 * hash + mat.get(2).hashCode()
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat3
        return mat.get(0) == other.mat[0] && mat.get(1) == other.mat[1] && mat.get(2) == other.mat[2]
    }

    fun Zero() {
        mat.get(0).Zero()
        mat.get(1).Zero()
        mat.get(2).Zero()
    }

    fun Identity() {
        this.oSet(idMat3.Companion.getMat3_identity())
    }

    @JvmOverloads
    fun IsIdentity(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return Compare(idMat3.Companion.getMat3_identity(), epsilon)
    }

    @JvmOverloads
    fun IsSymmetric(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        if (Math.abs(mat.get(0).y - mat.get(1).x) > epsilon) {
            return false
        }
        return if (Math.abs(mat.get(0).z - mat.get(2).x) > epsilon) {
            false
        } else Math.abs(mat.get(1).z - mat.get(2).y) <= epsilon
    }

    @JvmOverloads
    fun IsDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return (Math.abs(mat.get(0).y) <= epsilon
                && Math.abs(mat.get(0).z) <= epsilon
                && Math.abs(mat.get(1).x) <= epsilon
                && Math.abs(mat.get(1).z) <= epsilon
                && Math.abs(mat.get(2).x) <= epsilon
                && Math.abs(mat.get(2).y) <= epsilon)
    }

    fun IsRotated(): Boolean {
        return !Compare(idMat3.Companion.mat3_identity)
    }

    fun ProjectVector(src: idVec3, dst: idVec3) {
        dst.x = mat.get(0).oMultiply(src)
        dst.y = mat.get(1).oMultiply(src)
        dst.z = mat.get(2).oMultiply(src)
    }

    fun UnprojectVector(src: idVec3, dst: idVec3) {
        dst.oSet(
            mat.get(0).oMultiply(src.x).oPlus(
                mat.get(1).oMultiply(src.y).oPlus(
                    mat.get(2).oMultiply(src.z)
                )
            )
        )
    }

    fun FixDegeneracies(): Boolean { // fix degenerate axial cases
        var r = mat.get(0).FixDegenerateNormal()
        r = r or mat.get(1).FixDegenerateNormal()
        r = r or mat.get(2).FixDegenerateNormal()
        return r
    }

    fun FixDenormals(): Boolean { // change tiny numbers to zero
        var r = mat.get(0).FixDenormals()
        r = r or mat.get(1).FixDenormals()
        r = r or mat.get(2).FixDenormals()
        return r
    }

    fun Trace(): Float {
        return mat.get(0).x + mat.get(1).y + mat.get(2).z
    }

    fun Determinant(): Float {
        val det2_12_01 = mat.get(1).x * mat.get(2).y - mat.get(1).y * mat.get(2).x
        val det2_12_02 = mat.get(1).x * mat.get(2).z - mat.get(1).z * mat.get(2).x
        val det2_12_12 = mat.get(1).y * mat.get(2).z - mat.get(1).z * mat.get(2).y
        return mat.get(0).x * det2_12_12 - mat.get(0).y * det2_12_02 + mat.get(0).z * det2_12_01
    }

    fun OrthoNormalize(): idMat3 {
        val ortho: idMat3
        ortho = this
        ortho.mat.get(0).Normalize()
        ortho.mat.get(2).Cross(mat.get(0), mat.get(1))
        ortho.mat.get(2).Normalize()
        ortho.mat.get(1).Cross(mat.get(2), mat.get(0))
        ortho.mat.get(1).Normalize()
        return ortho
    }

    fun OrthoNormalizeSelf(): idMat3 {
        mat.get(0).Normalize()
        mat.get(2).Cross(mat.get(0), mat.get(1))
        mat.get(2).Normalize()
        mat.get(1).Cross(mat.get(2), mat.get(0))
        mat.get(1).Normalize()
        return this
    }

    fun Transpose(): idMat3 { // returns transpose
        return idMat3(
            mat.get(0).x, mat.get(1).x, mat.get(2).x,
            mat.get(0).y, mat.get(1).y, mat.get(2).y,
            mat.get(0).z, mat.get(1).z, mat.get(2).z
        )
    }

    fun TransposeSelf(): idMat3 {
        val tmp0: Float
        val tmp1: Float
        val tmp2: Float
        tmp0 = mat.get(0).y
        mat.get(0).y = mat.get(1).x
        mat.get(1).x = tmp0
        tmp1 = mat.get(0).z
        mat.get(0).z = mat.get(2).x
        mat.get(2).x = tmp1
        tmp2 = mat.get(1).z
        mat.get(1).z = mat.get(2).y
        mat.get(2).y = tmp2
        return this
    }

    fun Inverse(): idMat3 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat3
        invMat = idMat3(this)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 18+3+9 = 30 multiplications
        //			 1 division
        val inverse = idMat3()
        val det: Double
        val invDet: Double
        inverse.mat[0].x = mat.get(1).y * mat.get(2).z - mat.get(1).z * mat.get(2).y
        inverse.mat[1].x = mat.get(1).z * mat.get(2).x - mat.get(1).x * mat.get(2).z
        inverse.mat[2].x = mat.get(1).x * mat.get(2).y - mat.get(1).y * mat.get(2).x
        det =
            (mat.get(0).x * inverse.mat[0].x + mat.get(0).y * inverse.mat[1].x + mat.get(0).z * inverse.mat[2].x).toDouble()
        if (Math.abs(det.toFloat()) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        inverse.mat[0].y = mat.get(0).z * mat.get(2).y - mat.get(0).y * mat.get(2).z
        inverse.mat[0].z = mat.get(0).y * mat.get(1).z - mat.get(0).z * mat.get(1).y
        inverse.mat[1].y = mat.get(0).x * mat.get(2).z - mat.get(0).z * mat.get(2).x
        inverse.mat[1].z = mat.get(0).z * mat.get(1).x - mat.get(0).x * mat.get(1).z
        inverse.mat[2].y = mat.get(0).y * mat.get(2).x - mat.get(0).x * mat.get(2).y
        inverse.mat[2].z = mat.get(0).x * mat.get(1).y - mat.get(0).y * mat.get(1).x
        mat.get(0).x = (inverse.mat[0].x * invDet).toFloat()
        mat.get(0).y = (inverse.mat[0].y * invDet).toFloat()
        mat.get(0).z = (inverse.mat[0].z * invDet).toFloat()
        mat.get(1).x = (inverse.mat[1].x * invDet).toFloat()
        mat.get(1).y = (inverse.mat[1].y * invDet).toFloat()
        mat.get(1).z = (inverse.mat[1].z * invDet).toFloat()
        mat.get(2).x = (inverse.mat[2].x * invDet).toFloat()
        mat.get(2).y = (inverse.mat[2].y * invDet).toFloat()
        mat.get(2).z = (inverse.mat[2].z * invDet).toFloat()
        return true
    }

    fun InverseFast(): idMat3 { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat3
        invMat = this
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    //
    fun InverseFastSelf(): Boolean // returns false if determinant is zero
    { //TODO://#if 1
        return InverseSelf()
    }

    fun TransposeMultiply(b: idMat3): idMat3 {
        return idMat3(
            mat.get(0).x * b.mat[0].x + mat.get(1).x * b.mat[1].x + mat.get(2).x * b.mat[2].x,
            mat.get(0).x * b.mat[0].y + mat.get(1).x * b.mat[1].y + mat.get(2).x * b.mat[2].y,
            mat.get(0).x * b.mat[0].z + mat.get(1).x * b.mat[1].z + mat.get(2).x * b.mat[2].z,
            mat.get(0).y * b.mat[0].x + mat.get(1).y * b.mat[1].x + mat.get(2).y * b.mat[2].x,
            mat.get(0).y * b.mat[0].y + mat.get(1).y * b.mat[1].y + mat.get(2).y * b.mat[2].y,
            mat.get(0).y * b.mat[0].z + mat.get(1).y * b.mat[1].z + mat.get(2).y * b.mat[2].z,
            mat.get(0).z * b.mat[0].x + mat.get(1).z * b.mat[1].x + mat.get(2).z * b.mat[2].x,
            mat.get(0).z * b.mat[0].y + mat.get(1).z * b.mat[1].y + mat.get(2).z * b.mat[2].y,
            mat.get(0).z * b.mat[0].z + mat.get(1).z * b.mat[1].z + mat.get(2).z * b.mat[2].z
        )
    }

    fun InertiaTranslate(mass: Float, centerOfMass: idVec3, translation: idVec3): idMat3 {
        val m = idMat3()
        val newCenter = idVec3()
        newCenter.oSet(centerOfMass.oPlus(translation))
        m.mat[0].x = mass * (centerOfMass.y * centerOfMass.y + centerOfMass.z * centerOfMass.z
                - (newCenter.y * newCenter.y + newCenter.z * newCenter.z))
        m.mat[1].y = mass * (centerOfMass.x * centerOfMass.x + centerOfMass.z * centerOfMass.z
                - (newCenter.x * newCenter.x + newCenter.z * newCenter.z))
        m.mat[2].z = mass * (centerOfMass.x * centerOfMass.x + centerOfMass.y * centerOfMass.y
                - (newCenter.x * newCenter.x + newCenter.y * newCenter.y))
        m.mat[1].x = mass * (newCenter.x * newCenter.y - centerOfMass.x * centerOfMass.y)
        m.mat[0].y = m.mat[1].x
        m.mat[2].y = mass * (newCenter.y * newCenter.z - centerOfMass.y * centerOfMass.z)
        m.mat[1].z = m.mat[2].y
        m.mat[2].x = mass * (newCenter.x * newCenter.z - centerOfMass.x * centerOfMass.z)
        m.mat[0].z = m.mat[2].x
        return oPlus(m)
    }

    fun InertiaTranslateSelf(mass: Float, centerOfMass: idVec3, translation: idVec3): idMat3 {
        val m = idMat3()
        val newCenter = idVec3()
        newCenter.oSet(centerOfMass.oPlus(translation))
        m.mat[0].x = mass * (centerOfMass.y * centerOfMass.y + centerOfMass.z * centerOfMass.z
                - (newCenter.y * newCenter.y + newCenter.z * newCenter.z))
        m.mat[1].y = mass * (centerOfMass.x * centerOfMass.x + centerOfMass.z * centerOfMass.z
                - (newCenter.x * newCenter.x + newCenter.z * newCenter.z))
        m.mat[2].z = mass * (centerOfMass.x * centerOfMass.x + centerOfMass.y * centerOfMass.y
                - (newCenter.x * newCenter.x + newCenter.y * newCenter.y))
        m.mat[1].x = mass * (newCenter.x * newCenter.y - centerOfMass.x * centerOfMass.y)
        m.mat[0].y = m.mat[1].x
        m.mat[2].y = mass * (newCenter.y * newCenter.z - centerOfMass.y * centerOfMass.z)
        m.mat[1].z = m.mat[2].y
        m.mat[2].x = mass * (newCenter.x * newCenter.z - centerOfMass.x * centerOfMass.z)
        m.mat[0].z = m.mat[2].x
        return this.oPluSet(m)
    }

    fun InertiaRotate(rotation: idMat3): idMat3 {
        // NOTE: the rotation matrix is stored column-major
//            return rotation.Transpose() * (*this) * rotation;
        return rotation.Transpose().oMultiply(this).oMultiply(rotation)
    }

    fun InertiaRotateSelf(rotation: idMat3): idMat3 {
        // NOTE: the rotation matrix is stored column-major
//	*this = rotation.Transpose() * (*this) * rotation;
        this.oSet(rotation.Transpose().oMultiply(this).oMultiply(rotation))
        return this
    }

    fun GetDimension(): Int {
        return 9
    }

    fun ToAngles(): idAngles? {
        val angles = idAngles()
        val theta: Double
        val cp: Double
        var sp: Float
        sp = mat.get(0).z

        // cap off our sin value so that we don't get any NANs
        if (sp > 1.0f) {
            sp = 1.0f
        } else if (sp < -1.0f) {
            sp = -1.0f
        }
        theta = -Math.asin(sp.toDouble())
        cp = Math.cos(theta)
        if (cp > 8192.0f * idMath.FLT_EPSILON) {
            angles.pitch = Vector.RAD2DEG(theta)
            angles.yaw = Vector.RAD2DEG(Math.atan2(mat.get(0).y.toDouble(), mat.get(0).x.toDouble()))
            angles.roll = Vector.RAD2DEG(Math.atan2(mat.get(1).z.toDouble(), mat.get(2).z.toDouble()))
        } else {
            angles.pitch = Vector.RAD2DEG(theta)
            angles.yaw = Vector.RAD2DEG(-Math.atan2(mat.get(1).x.toDouble(), mat.get(1).y.toDouble()))
            angles.roll = 0f
        }
        return angles
    }

    fun ToQuat(): idQuat? {
        val q = idQuat()
        val trace: Float
        val s: Float
        val t: Float
        var i: Int
        val j: Int
        val k: Int
        val next = intArrayOf(1, 2, 0)

//	trace = mat[0 ][0 ] + mat[1 ][1 ] + mat[2 ][2 ];
        trace = Trace()
        if (trace > 0.0f) {
            t = trace + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            q.oSet(3, s * t)
            q.oSet(0, (mat.get(2).y - mat.get(1).z) * s)
            q.oSet(1, (mat.get(0).z - mat.get(2).x) * s)
            q.oSet(2, (mat.get(1).x - mat.get(0).y) * s)
        } else {
            i = 0
            if (mat.get(1).y > mat.get(0).x) {
                i = 1
            }
            if (mat.get(2).z > mat.get(i).oGet(i)) {
                i = 2
            }
            j = next[i]
            k = next[j]
            t = mat.get(i).oGet(i) - (mat.get(j).oGet(j) + mat.get(k).oGet(k)) + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            q.oSet(i, s * t)
            q.oSet(3, (mat.get(k).oGet(j) - mat.get(j).oGet(k)) * s)
            q.oSet(j, (mat.get(j).oGet(i) + mat.get(i).oGet(j)) * s)
            q.oSet(k, (mat.get(k).oGet(i) + mat.get(i).oGet(k)) * s)
        }
        return q
    }

    fun ToCQuat(): idCQuat? {
        val q = ToQuat()
        return if (q.w < 0.0f) {
            idCQuat(-q.x, -q.y, -q.z)
        } else idCQuat(q.x, q.y, q.z)
    }

    fun ToRotation(): idRotation? {
        val r = idRotation()
        val trace: Float
        val s: Float
        val t: Float
        var i: Int
        val j: Int
        val k: Int
        val next = intArrayOf(1, 2, 0)
        trace = mat.get(0).x + mat.get(1).y + mat.get(2).z
        if (trace > 0.0f) {
            t = trace + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            r.angle = s * t
            r.vec.oSet(0, (mat.get(2).y - mat.get(1).z) * s)
            r.vec.oSet(1, (mat.get(0).z - mat.get(2).x) * s)
            r.vec.oSet(2, (mat.get(1).x - mat.get(0).y) * s)
        } else {
            i = 0
            if (mat.get(1).y > mat.get(0).x) {
                i = 1
            }
            if (mat.get(2).z > mat.get(i).oGet(i)) {
                i = 2
            }
            j = next[i]
            k = next[j]
            t = mat.get(i).oGet(i) - (mat.get(j).oGet(j) + mat.get(k).oGet(k)) + 1.0f
            s = idMath.InvSqrt(t) * 0.5f
            r.vec.oSet(i, s * t)
            r.angle = (mat.get(k).oGet(j) - mat.get(j).oGet(k)) * s
            r.vec.oSet(j, (mat.get(j).oGet(i) + mat.get(i).oGet(j)) * s)
            r.vec.oSet(k, (mat.get(k).oGet(i) + mat.get(i).oGet(k)) * s)
        }
        r.angle = idMath.ACos(r.angle)
        if (Math.abs(r.angle) < 1e-10f) {
            r.vec.Set(0.0f, 0.0f, 1.0f)
            r.angle = 0.0f
        } else {
            //vec *= (1.0f / sin( angle ));
            r.vec.Normalize()
            r.vec.FixDegenerateNormal()
            r.angle *= 2.0f * idMath.M_RAD2DEG
        }
        r.origin.Zero()
        r.axis = this
        r.axisValid = true
        return r
    }

    fun ToMat4(): idMat4? {
        // NOTE: idMat3 is transposed because it is column-major
        return idMat4(
            mat.get(0).x, mat.get(1).x, mat.get(2).x, 0.0f,
            mat.get(0).y, mat.get(1).y, mat.get(2).y, 0.0f,
            mat.get(0).z, mat.get(1).z, mat.get(2).z, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )
    }

    //	public	float *			ToFloatPtr( void );
    fun ToAngularVelocity(): idVec3 {
        val rotation = ToRotation()
        return rotation.GetVec().oMultiply(Math_h.DEG2RAD(rotation.GetAngle()))
    }

    /**
     * Read-only array.
     */
    fun ToFloatPtr(): FloatArray? {
        return floatArrayOf(
            mat.get(0).x, mat.get(0).y, mat.get(0).z,
            mat.get(1).x, mat.get(1).y, mat.get(1).z,
            mat.get(2).x, mat.get(2).y, mat.get(2).z
        )
    }

    //
    @JvmOverloads
    fun ToString(precision: Int = 2): String? {
        return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun getRow(row: Int): idVec3 {
        return mat.get(row)
    }

    @Deprecated("")
    fun setRow(rowNumber: Int, row: idVec3) {
        mat.get(rowNumber) = row
    }

    @Deprecated("")
    fun setRow(rowNumber: Int, x: Float, y: Float, z: Float) {
        mat.get(rowNumber) = idVec3(x, y, z)
    }

    fun oSet(x: Int, y: Int, value: Float): Float {
        return when (y) {
            1 -> value.also { mat.get(x).y = it }
            2 -> value.also { mat.get(x).z = it }
            else -> value.also { mat.get(x).x = it }
        }
    }

    fun oSet(m: idMat3): idMat3 {
        mat.get(0).oSet(m.mat[0])
        mat.get(1).oSet(m.mat[1])
        mat.get(2).oSet(m.mat[2])
        return this
    }

    fun oMinSet(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat.get(x).x -= value
            1 -> mat.get(x).y -= value
            2 -> mat.get(x).z -= value
        }
    }

    fun oPluSet(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat.get(x).x -= value
            1 -> mat.get(x).y -= value
            2 -> mat.get(x).z -= value
        }
    }

    fun oPluSet(a: idMat3): idMat3 {
        mat.get(0).x += a.mat[0].x
        mat.get(0).y += a.mat[0].y
        mat.get(0).z += a.mat[0].z
        //
        mat.get(1).x += a.mat[1].x
        mat.get(1).y += a.mat[1].y
        mat.get(1).z += a.mat[1].z
        //
        mat.get(2).x += a.mat[2].x
        mat.get(2).y += a.mat[2].y
        mat.get(2).z += a.mat[2].z
        return this
    }

    fun reinterpret_cast(): FloatArray? {
        val size = 3
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat.get(x).oGet(y)
            }
        }
        return temp
    }

    override fun toString(): String {
        return """
            
            ${mat.get(0)},
            ${mat.get(1)},
            ${mat.get(2)}
            """.trimIndent()
    }

    companion object {
        val BYTES: Int = idVec3.Companion.BYTES * 3
        private val mat3_identity: idMat3 = idMat3(idVec3(1, 0, 0), idVec3(0, 1, 0), idVec3(0, 0, 1))
        private val mat3_default: idMat3 = idMat3.Companion.mat3_identity
        private val mat3_zero: idMat3 = idMat3(idVec3(0, 0, 0), idVec3(0, 0, 0), idVec3(0, 0, 0))
        private const val DBG_counter = 0
        fun getMat3_zero(): idMat3 {
            return idMat3(idMat3.Companion.mat3_zero)
        }

        fun getMat3_identity(): idMat3 {
            return idMat3(idMat3.Companion.mat3_identity)
        }

        fun getMat3_default(): idMat3 {
            return idMat3(idMat3.Companion.mat3_default)
        }

        //
        //public	const idVec3 &	operator[]( int index ) const;
        //public	idVec3 &		operator[]( int index );
        fun oMultiply(a: Float, mat: idMat3): idMat3 {
            return mat.oMultiply(a)
        }

        fun oMultiply(vec: idVec3, mat: idMat3): idVec3 {
            return mat.oMultiply(vec)
        }

        fun oMulSet(vec: idVec3, mat: idMat3): idVec3 {
            val x = mat.mat[0].x * vec.x + mat.mat[1].x * vec.y + mat.mat[2].x * vec.z
            val y = mat.getRow(0).y * vec.x + mat.mat[1].y * vec.y + mat.mat[2].y * vec.z
            vec.z = mat.mat[0].z * vec.x + mat.mat[1].z * vec.y + mat.mat[2].z * vec.z
            vec.x = x
            vec.y = y
            return vec
        }

        fun TransposeMultiply(transpose: idMat3, b: idMat3, dst: idMat3) {
            dst.mat[0].x =
                transpose.mat[0].x * b.mat[0].x + transpose.mat[1].x * b.mat[1].x + transpose.mat[2].x * b.mat[2].x
            dst.mat[0].y =
                transpose.mat[0].x * b.mat[0].y + transpose.mat[1].x * b.mat[1].y + transpose.mat[2].x * b.mat[2].y
            dst.mat[0].z =
                transpose.mat[0].x * b.mat[0].z + transpose.mat[1].x * b.mat[1].z + transpose.mat[2].x * b.mat[2].z
            dst.mat[1].x =
                transpose.mat[0].y * b.mat[0].x + transpose.mat[1].y * b.mat[1].x + transpose.mat[2].y * b.mat[2].x
            dst.mat[1].y =
                transpose.mat[0].y * b.mat[0].y + transpose.mat[1].y * b.mat[1].y + transpose.mat[2].y * b.mat[2].y
            dst.mat[1].z =
                transpose.mat[0].y * b.mat[0].z + transpose.mat[1].y * b.mat[1].z + transpose.mat[2].y * b.mat[2].z
            dst.mat[2].x =
                transpose.mat[0].z * b.mat[0].x + transpose.mat[1].z * b.mat[1].x + transpose.mat[2].z * b.mat[2].x
            dst.mat[2].y =
                transpose.mat[0].z * b.mat[0].y + transpose.mat[1].z * b.mat[1].y + transpose.mat[2].z * b.mat[2].y
            dst.mat[2].z =
                transpose.mat[0].z * b.mat[0].z + transpose.mat[1].z * b.mat[1].z + transpose.mat[2].z * b.mat[2].z
        }

        //public	idMat3			operator*( const float a ) const;
        fun SkewSymmetric(src: idVec3): idMat3 {
            return idMat3(0.0f, -src.z, src.y, src.z, 0.0f, -src.x, -src.y, src.x, 0.0f)
        }
    }
}