package neo.idlib.math.Matrix

import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.util.*

//===============================================================
//
//	idMat4 - 4x4 matrix
//
//===============================================================
class idMat4 {
    private val mat: Array<idVec4?>? = arrayOf(idVec4(), idVec4(), idVec4(), idVec4())

    constructor()
    constructor(x: idVec4?, y: idVec4?, z: idVec4?, w: idVec4?) {
        mat.get(0).oSet(x)
        mat.get(1).oSet(y)
        mat.get(2).oSet(z)
        mat.get(3).oSet(w)
    }

    constructor(
        xx: Float, xy: Float, xz: Float, xw: Float,
        yx: Float, yy: Float, yz: Float, yw: Float,
        zx: Float, zy: Float, zz: Float, zw: Float,
        wx: Float, wy: Float, wz: Float, ww: Float
    ) {
        mat.get(0).x = xx
        mat.get(0).y = xy
        mat.get(0).z = xz
        mat.get(0).w = xw
        //
        mat.get(1).x = yx
        mat.get(1).y = yy
        mat.get(1).z = yz
        mat.get(1).w = yw
        //
        mat.get(2).x = zx
        mat.get(2).y = zy
        mat.get(2).z = zz
        mat.get(2).w = zw
        //
        mat.get(3).x = wx
        mat.get(3).y = wy
        mat.get(3).z = wz
        mat.get(3).w = ww
    }

    constructor(rotation: idMat3?, translation: idVec3?) {
        // NOTE: idMat3 is transposed because it is column-major
        mat.get(0).x = rotation.mat[0].x
        mat.get(0).y = rotation.mat[1].x
        mat.get(0).z = rotation.mat[2].x
        mat.get(0).w = translation.x
        mat.get(1).x = rotation.mat[0].y
        mat.get(1).y = rotation.mat[1].y
        mat.get(1).z = rotation.mat[2].y
        mat.get(1).w = translation.y
        mat.get(2).x = rotation.mat[0].z
        mat.get(2).y = rotation.mat[1].z
        mat.get(2).z = rotation.mat[2].z
        mat.get(2).w = translation.z
        mat.get(3).x = 0.0f
        mat.get(3).y = 0.0f
        mat.get(3).z = 0.0f
        mat.get(3).w = 1.0f
    }

    constructor(src: Array<FloatArray?>?) {
//	memcpy( mat, src, 4 * 4 * sizeof( float ) );
        mat.get(0).x = src.get(0).get(0)
        mat.get(0).y = src.get(0).get(1)
        mat.get(0).z = src.get(0).get(2)
        mat.get(0).w = src.get(0).get(3)
        //
        mat.get(1).x = src.get(1).get(0)
        mat.get(1).y = src.get(1).get(1)
        mat.get(1).z = src.get(1).get(2)
        mat.get(1).w = src.get(1).get(3)
        //
        mat.get(2).x = src.get(2).get(0)
        mat.get(2).y = src.get(2).get(1)
        mat.get(2).z = src.get(2).get(2)
        mat.get(2).w = src.get(2).get(3)
        //
        mat.get(3).x = src.get(3).get(0)
        mat.get(3).y = src.get(3).get(1)
        mat.get(3).z = src.get(3).get(2)
        mat.get(3).w = src.get(3).get(3)
    }

    constructor(m: idMat4?) {
        this.oSet(m)
    }

    //public	idVec3			operator*( const idVec3 &vec ) const;
    //public	const idVec4 &	operator[]( int index ) const;
    fun oGet(index: Int): idVec4? {
        return mat.get(index)
    }

    //public	idMat4			operator*( const idMat4 &a ) const;
    fun oSet(index: Int, value: idVec4?): idVec4? {
        return value.also { mat.get(index) = it }
    }

    //public	idMat4			operator+( const idMat4 &a ) const;
    fun oSet(index1: Int, index2: Int, value: Float): Float {
        return mat.get(index1).oSet(index2, value)
    }

    //public	idMat4			operator-( const idMat4 &a ) const;
    fun oMultiply(a: Float): idMat4? {
        return idMat4(
            mat.get(0).x * a, mat.get(0).y * a, mat.get(0).z * a, mat.get(0).w * a,
            mat.get(1).x * a, mat.get(1).y * a, mat.get(1).z * a, mat.get(1).w * a,
            mat.get(2).x * a, mat.get(2).y * a, mat.get(2).z * a, mat.get(2).w * a,
            mat.get(3).x * a, mat.get(3).y * a, mat.get(3).z * a, mat.get(3).w * a
        )
    }

    //public	idMat4 &		operator*=( const float a );
    fun oMultiply(vec: idVec4?): idVec4? {
        return idVec4(
            mat.get(0).x * vec.x + mat.get(0).y * vec.y + mat.get(0).z * vec.z + mat.get(0).w * vec.w,
            mat.get(1).x * vec.x + mat.get(1).y * vec.y + mat.get(1).z * vec.z + mat.get(1).w * vec.w,
            mat.get(2).x * vec.x + mat.get(2).y * vec.y + mat.get(2).z * vec.z + mat.get(2).w * vec.w,
            mat.get(3).x * vec.x + mat.get(3).y * vec.y + mat.get(3).z * vec.z + mat.get(3).w * vec.w
        )
    }

    //public	idMat4 &		operator*=( const idMat4 &a );
    fun oMultiply(vec: idVec3?): idVec3? {
        val s = mat.get(3).x * vec.x + mat.get(3).y * vec.y + mat.get(3).z * vec.z + mat.get(3).w
        if (s == 0.0f) {
            return idVec3(0.0f, 0.0f, 0.0f)
        }
        return if (s == 1.0f) {
            idVec3(
                mat.get(0).x * vec.x + mat.get(0).y * vec.y + mat.get(0).z * vec.z + mat.get(0).w,
                mat.get(1).x * vec.x + mat.get(1).y * vec.y + mat.get(1).z * vec.z + mat.get(1).w,
                mat.get(2).x * vec.x + mat.get(2).y * vec.y + mat.get(2).z * vec.z + mat.get(2).w
            )
        } else {
            val invS = 1.0f / s
            idVec3(
                (mat.get(0).x * vec.x + mat.get(0).y * vec.y + mat.get(0).z * vec.z + mat.get(0).w) * invS,
                (mat.get(1).x * vec.x + mat.get(1).y * vec.y + mat.get(1).z * vec.z + mat.get(1).w) * invS,
                (mat.get(2).x * vec.x + mat.get(2).y * vec.y + mat.get(2).z * vec.z + mat.get(2).w) * invS
            )
        }
    }

    //public	idMat4 &		operator+=( const idMat4 &a );
    fun oMultiply(a: idMat4?): idMat4? {
        var i: Int
        var j: Int
        val dst = idMat4()
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                val value = (mat.get(0).oMultiply(a.mat[0 * 4 + j])
                        + mat.get(1).oMultiply(a.mat[1 * 4 + j])
                        + mat.get(2).oMultiply(a.mat[2 * 4 + j])
                        + mat.get(2).oMultiply(a.mat[3 * 4 + j]))
                dst.setCell(i, j, value)
                j++
            }
            i++
        }
        return dst
    }

    fun oPlus(a: idMat4?): idMat4? {
        return idMat4(
            mat.get(0).x + a.mat[0].x, mat.get(0).y + a.mat[0].y, mat.get(0).z + a.mat[0].z, mat.get(0).w + a.mat[0].w,
            mat.get(1).x + a.mat[1].x, mat.get(1).y + a.mat[1].y, mat.get(1).z + a.mat[1].z, mat.get(1).w + a.mat[1].w,
            mat.get(2).x + a.mat[2].x, mat.get(2).y + a.mat[2].y, mat.get(2).z + a.mat[2].z, mat.get(2).w + a.mat[2].w,
            mat.get(3).x + a.mat[3].x, mat.get(3).y + a.mat[3].y, mat.get(3).z + a.mat[3].z, mat.get(3).w + a.mat[3].w
        )
    }

    fun oMinus(a: idMat4?): idMat4? {
        return idMat4(
            mat.get(0).x - a.mat[0].x, mat.get(0).y - a.mat[0].y, mat.get(0).z - a.mat[0].z, mat.get(0).w - a.mat[0].w,
            mat.get(1).x - a.mat[1].x, mat.get(1).y - a.mat[1].y, mat.get(1).z - a.mat[1].z, mat.get(1).w - a.mat[1].w,
            mat.get(2).x - a.mat[2].x, mat.get(2).y - a.mat[2].y, mat.get(2).z - a.mat[2].z, mat.get(2).w - a.mat[2].w,
            mat.get(3).x - a.mat[3].x, mat.get(3).y - a.mat[3].y, mat.get(3).z - a.mat[3].z, mat.get(3).w - a.mat[3].w
        )
    }

    fun oMulSet(a: Float): idMat4? {
        mat.get(0).x *= a
        mat.get(0).y *= a
        mat.get(0).z *= a
        mat.get(0).w *= a
        //
        mat.get(1).x *= a
        mat.get(1).y *= a
        mat.get(1).z *= a
        mat.get(1).w *= a
        //
        mat.get(2).x *= a
        mat.get(2).y *= a
        mat.get(2).z *= a
        mat.get(2).w *= a
        //
        mat.get(3).x *= a
        mat.get(3).y *= a
        mat.get(3).z *= a
        mat.get(3).w *= a
        return this
    }

    //public	friend idVec3	operator*( const idVec3 &vec, const idMat4 &mat );
    fun oMultSet(a: idMat4?): idMat4? {
        var i: Int
        var j: Int
        val dst = idMat4()
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                val value = (mat.get(0).oMultiply(a.mat[0 * 4 + j])
                        + mat.get(1).oMultiply(a.mat[1 * 4 + j])
                        + mat.get(2).oMultiply(a.mat[2 * 4 + j])
                        + mat.get(2).oMultiply(a.mat[3 * 4 + j]))
                dst.setCell(i, j, value)
                j++
            }
            i++
        }
        return this
    }

    //public	friend idVec4 &	operator*=( idVec4 &vec, const idMat4 &mat );
    fun oPluSet(a: idMat4?): idMat4? {
        mat.get(0).x += a.mat[0].x
        mat.get(0).y += a.mat[0].y
        mat.get(0).z += a.mat[0].z
        mat.get(0).w += a.mat[0].w
        //
        mat.get(1).x += a.mat[1].x
        mat.get(1).y += a.mat[1].y
        mat.get(1).z += a.mat[1].z
        mat.get(1).w += a.mat[1].w
        //
        mat.get(2).x += a.mat[2].x
        mat.get(2).y += a.mat[2].y
        mat.get(2).z += a.mat[2].z
        mat.get(2).w += a.mat[2].w
        //
        mat.get(3).x += a.mat[3].x
        mat.get(3).y += a.mat[3].y
        mat.get(3).z += a.mat[3].z
        mat.get(3).w += a.mat[3].w
        return this
    }

    //public	friend idVec3 &	operator*=( idVec3 &vec, const idMat4 &mat );
    //public	idMat4 &		operator-=( const idMat4 &a );
    fun oMinSet(a: idMat4?): idMat4? {
        mat.get(0).x -= a.mat[0].x
        mat.get(0).y -= a.mat[0].y
        mat.get(0).z -= a.mat[0].z
        mat.get(0).w -= a.mat[0].w
        //
        mat.get(1).x -= a.mat[1].x
        mat.get(1).y -= a.mat[1].y
        mat.get(1).z -= a.mat[1].z
        mat.get(1).w -= a.mat[1].w
        //
        mat.get(2).x -= a.mat[2].x
        mat.get(2).y -= a.mat[2].y
        mat.get(2).z -= a.mat[2].z
        mat.get(2).w -= a.mat[2].w
        //
        mat.get(3).x -= a.mat[3].x
        mat.get(3).y -= a.mat[3].y
        mat.get(3).z -= a.mat[3].z
        mat.get(3).w -= a.mat[3].w
        return this
    }

    fun Compare(a: idMat4?): Boolean { // exact compare, no epsilon
        var i: Int
        var j: Int
        val ptr1 = mat
        val ptr2 = a.mat
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                if (ptr1.get(i).oGet(j) != ptr2[i].oGet(j)) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    fun Compare(a: idMat4?, epsilon: Float): Boolean // compare with epsilon
    {
        var i: Int
        var j: Int
        val ptr1 = mat
        val ptr2 = a.mat
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                if (Math.abs(ptr1.get(i).oGet(j) - ptr2[i].oGet(j)) > epsilon) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    //public	bool			operator==( const idMat4 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat4 &a ) const;					// exact compare, no epsilon
    override fun hashCode(): Int {
        var hash = 3
        hash = 89 * hash + Arrays.deepHashCode(mat)
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat4?
        return Arrays.deepEquals(mat, other.mat)
    }

    fun Zero() {
        this.oSet(idMat4.Companion.getMat4_zero())
    }

    fun Identity() {
        this.oSet(idMat4.Companion.getMat4_identity())
    }

    @JvmOverloads
    fun IsIdentity(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return Compare(idMat4.Companion.getMat4_identity(), epsilon)
    }

    @JvmOverloads
    fun IsSymmetric(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        for (i in 1..3) {
            for (j in 0 until i) {
                if (Math.abs(mat.get(i).oGet(j) - mat.get(j).oGet(i)) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        for (i in 0..3) {
            for (j in 0..3) {
                if (i != j && Math.abs(mat.get(i).oGet(j)) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun IsRotated(): Boolean {
        return 0f != (mat.get(0).oGet(1) + mat.get(0).oGet(2)
                + mat.get(1).oGet(0) + mat.get(1).oGet(2)
                + mat.get(2).oGet(0) + mat.get(2).oGet(1))
    }

    fun ProjectVector(src: idVec4?, dst: idVec4?) {
        dst.x = src.oMultiply(mat.get(0))
        dst.y = src.oMultiply(mat.get(1))
        dst.z = src.oMultiply(mat.get(2))
        dst.w = src.oMultiply(mat.get(3))
    }

    fun UnprojectVector(src: idVec4?, dst: idVec4?) {
//	dst = mat[ 0 ] * src.x + mat[ 1 ] * src.y + mat[ 2 ] * src.z + mat[ 3 ] * src.w;
        dst.oSet(
            mat.get(0).oMultiply(src.x).oPlus(
                mat.get(1).oMultiply(src.y).oPlus(
                    mat.get(2).oMultiply(src.z).oPlus(
                        mat.get(3).oMultiply(src.w)
                    )
                )
            )
        )
    }

    fun Trace(): Float {
        return mat.get(0).oGet(0) + mat.get(1).oGet(1) + mat.get(2).oGet(2) + mat.get(3).oGet(3)
    }

    fun Determinant(): Float {

        // 2x2 sub-determinants
        val det2_01_01 = mat.get(0).oGet(0) * mat.get(1).oGet(1) - mat.get(0).oGet(1) * mat.get(1).oGet(0)
        val det2_01_02 = mat.get(0).oGet(0) * mat.get(1).oGet(2) - mat.get(0).oGet(2) * mat.get(1).oGet(0)
        val det2_01_03 = mat.get(0).oGet(0) * mat.get(1).oGet(3) - mat.get(0).oGet(3) * mat.get(1).oGet(0)
        val det2_01_12 = mat.get(0).oGet(1) * mat.get(1).oGet(2) - mat.get(0).oGet(2) * mat.get(1).oGet(1)
        val det2_01_13 = mat.get(0).oGet(1) * mat.get(1).oGet(3) - mat.get(0).oGet(3) * mat.get(1).oGet(1)
        val det2_01_23 = mat.get(0).oGet(2) * mat.get(1).oGet(3) - mat.get(0).oGet(3) * mat.get(1).oGet(2)

        // 3x3 sub-determinants
        val det3_201_012 =
            mat.get(2).oGet(0) * det2_01_12 - mat.get(2).oGet(1) * det2_01_02 + mat.get(2).oGet(2) * det2_01_01
        val det3_201_013 =
            mat.get(2).oGet(0) * det2_01_13 - mat.get(2).oGet(1) * det2_01_03 + mat.get(2).oGet(3) * det2_01_01
        val det3_201_023 =
            mat.get(2).oGet(0) * det2_01_23 - mat.get(2).oGet(2) * det2_01_03 + mat.get(2).oGet(3) * det2_01_02
        val det3_201_123 =
            mat.get(2).oGet(1) * det2_01_23 - mat.get(2).oGet(2) * det2_01_13 + mat.get(2).oGet(3) * det2_01_12
        return -det3_201_123 * mat.get(3).oGet(0) + det3_201_023 * mat.get(3).oGet(1) - det3_201_013 * mat.get(3)
            .oGet(2) + det3_201_012 * mat.get(3).oGet(3)
    }

    fun Transpose(): idMat4? { // returns transpose
        val transpose = idMat4()
        var i: Int
        var j: Int
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                transpose.mat[i].oSet(j, mat.get(j).oGet(i))
                j++
            }
            i++
        }
        return transpose
    }

    fun TransposeSelf(): idMat4? {
        var temp: Float
        var i: Int
        var j: Int
        i = 0
        while (i < 4) {
            j = i + 1
            while (j < 4) {
                temp = mat.get(i).oGet(j)
                mat.get(i).oSet(j, mat.get(j).oGet(i))
                mat.get(j).oSet(i, temp)
                j++
            }
            i++
        }
        return this
    }

    fun Inverse(): idMat4? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat4
        invMat = this
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean // returns false if determinant is zero
    {
        // 84+4+16 = 104 multiplications
        //			   1 division
        val det: Float
        val invDet: Float

        // 2x2 sub-determinants required to calculate 4x4 determinant
        val det2_01_01 = mat.get(0).x * mat.get(1).y - mat.get(0).y * mat.get(1).x
        val det2_01_02 = mat.get(0).x * mat.get(1).z - mat.get(0).z * mat.get(1).x
        val det2_01_03 = mat.get(0).x * mat.get(1).w - mat.get(0).w * mat.get(1).x
        val det2_01_12 = mat.get(0).y * mat.get(1).z - mat.get(0).z * mat.get(1).y
        val det2_01_13 = mat.get(0).y * mat.get(1).w - mat.get(0).w * mat.get(1).y
        val det2_01_23 = mat.get(0).z * mat.get(1).w - mat.get(0).w * mat.get(1).z

        // 3x3 sub-determinants required to calculate 4x4 determinant
        val det3_201_012 = mat.get(2).x * det2_01_12 - mat.get(2).y * det2_01_02 + mat.get(2).z * det2_01_01
        val det3_201_013 = mat.get(2).x * det2_01_13 - mat.get(2).y * det2_01_03 + mat.get(2).w * det2_01_01
        val det3_201_023 = mat.get(2).x * det2_01_23 - mat.get(2).z * det2_01_03 + mat.get(2).w * det2_01_02
        val det3_201_123 = mat.get(2).y * det2_01_23 - mat.get(2).z * det2_01_13 + mat.get(2).w * det2_01_12
        det =
            -det3_201_123 * mat.get(3).x + det3_201_023 * mat.get(3).y - det3_201_013 * mat.get(3).z + det3_201_012 * mat.get(
                3
            ).w
        if (Math.abs(det) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det

        // remaining 2x2 sub-determinants
        val det2_03_01 = mat.get(0).x * mat.get(3).y - mat.get(0).y * mat.get(3).x
        val det2_03_02 = mat.get(0).x * mat.get(3).z - mat.get(0).z * mat.get(3).x
        val det2_03_03 = mat.get(0).x * mat.get(3).w - mat.get(0).w * mat.get(3).x
        val det2_03_12 = mat.get(0).y * mat.get(3).z - mat.get(0).z * mat.get(3).y
        val det2_03_13 = mat.get(0).y * mat.get(3).w - mat.get(0).w * mat.get(3).y
        val det2_03_23 = mat.get(0).z * mat.get(3).w - mat.get(0).w * mat.get(3).z
        val det2_13_01 = mat.get(1).x * mat.get(3).y - mat.get(1).y * mat.get(3).x
        val det2_13_02 = mat.get(1).x * mat.get(3).z - mat.get(1).z * mat.get(3).x
        val det2_13_03 = mat.get(1).x * mat.get(3).w - mat.get(1).w * mat.get(3).x
        val det2_13_12 = mat.get(1).y * mat.get(3).z - mat.get(1).z * mat.get(3).y
        val det2_13_13 = mat.get(1).y * mat.get(3).w - mat.get(1).w * mat.get(3).y
        val det2_13_23 = mat.get(1).z * mat.get(3).w - mat.get(1).w * mat.get(3).z

        // remaining 3x3 sub-determinants
        val det3_203_012 = mat.get(2).x * det2_03_12 - mat.get(2).y * det2_03_02 + mat.get(2).z * det2_03_01
        val det3_203_013 = mat.get(2).x * det2_03_13 - mat.get(2).y * det2_03_03 + mat.get(2).w * det2_03_01
        val det3_203_023 = mat.get(2).x * det2_03_23 - mat.get(2).z * det2_03_03 + mat.get(2).w * det2_03_02
        val det3_203_123 = mat.get(2).y * det2_03_23 - mat.get(2).z * det2_03_13 + mat.get(2).w * det2_03_12
        val det3_213_012 = mat.get(2).x * det2_13_12 - mat.get(2).y * det2_13_02 + mat.get(2).z * det2_13_01
        val det3_213_013 = mat.get(2).x * det2_13_13 - mat.get(2).y * det2_13_03 + mat.get(2).w * det2_13_01
        val det3_213_023 = mat.get(2).x * det2_13_23 - mat.get(2).z * det2_13_03 + mat.get(2).w * det2_13_02
        val det3_213_123 = mat.get(2).y * det2_13_23 - mat.get(2).z * det2_13_13 + mat.get(2).w * det2_13_12
        val det3_301_012 = mat.get(3).x * det2_01_12 - mat.get(3).y * det2_01_02 + mat.get(3).z * det2_01_01
        val det3_301_013 = mat.get(3).x * det2_01_13 - mat.get(3).y * det2_01_03 + mat.get(3).w * det2_01_01
        val det3_301_023 = mat.get(3).x * det2_01_23 - mat.get(3).z * det2_01_03 + mat.get(3).w * det2_01_02
        val det3_301_123 = mat.get(3).y * det2_01_23 - mat.get(3).z * det2_01_13 + mat.get(3).w * det2_01_12
        mat.get(0).x = -det3_213_123 * invDet
        mat.get(1).x = +det3_213_023 * invDet
        mat.get(2).x = -det3_213_013 * invDet
        mat.get(3).x = +det3_213_012 * invDet
        mat.get(0).y = +det3_203_123 * invDet
        mat.get(1).y = -det3_203_023 * invDet
        mat.get(2).y = +det3_203_013 * invDet
        mat.get(3).y = -det3_203_012 * invDet
        mat.get(0).z = +det3_301_123 * invDet
        mat.get(1).z = -det3_301_023 * invDet
        mat.get(2).z = +det3_301_013 * invDet
        mat.get(3).z = -det3_301_012 * invDet
        mat.get(0).w = -det3_201_123 * invDet
        mat.get(1).w = +det3_201_023 * invDet
        mat.get(2).w = -det3_201_013 * invDet
        mat.get(3).w = +det3_201_012 * invDet
        return true
    }

    fun InverseFast(): idMat4? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat4
        invMat = this
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean // returns false if determinant is zero
    {
//    #else
        //	6*8+2*6 = 60 multiplications
        //		2*1 =  2 divisions
        val r0 = Array<FloatArray?>(2) { FloatArray(2) }
        val r1 = Array<FloatArray?>(2) { FloatArray(2) }
        val r2 = Array<FloatArray?>(2) { FloatArray(2) }
        val r3 = Array<FloatArray?>(2) { FloatArray(2) }
        val a: Float
        var det: Float
        var invDet: Float

        // r0 = m0.Inverse();
        det = mat.get(0).x * mat.get(1).y - mat.get(0).y * mat.get(1).x
        if (Math.abs(det) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r0[0].get(0) = mat.get(1).y * invDet
        r0[0].get(1) = -mat.get(0).y * invDet
        r0[1].get(0) = -mat.get(1).x * invDet
        r0[1].get(1) = mat.get(0).x * invDet

        // r1 = r0 * m1;
        r1[0].get(0) = r0[0].get(0) * mat.get(0).z + r0[0].get(1) * mat.get(1).z
        r1[0].get(1) = r0[0].get(0) * mat.get(0).w + r0[0].get(1) * mat.get(1).w
        r1[1].get(0) = r0[1].get(0) * mat.get(0).z + r0[1].get(1) * mat.get(1).z
        r1[1].get(1) = r0[1].get(0) * mat.get(0).w + r0[1].get(1) * mat.get(1).w

        // r2 = m2 * r1;
        r2[0].get(0) = mat.get(2).x * r1[0].get(0) + mat.get(2).y * r1[1].get(0)
        r2[0].get(1) = mat.get(2).x * r1[0].get(1) + mat.get(2).y * r1[1].get(1)
        r2[1].get(0) = mat.get(3).x * r1[0].get(0) + mat.get(3).y * r1[1].get(0)
        r2[1].get(1) = mat.get(3).x * r1[0].get(1) + mat.get(3).y * r1[1].get(1)

        // r3 = r2 - m3;
        r3[0].get(0) = r2[0].get(0) - mat.get(2).z
        r3[0].get(1) = r2[0].get(1) - mat.get(2).w
        r3[1].get(0) = r2[1].get(0) - mat.get(3).z
        r3[1].get(1) = r2[1].get(1) - mat.get(3).w

        // r3.InverseSelf();
        det = r3[0].get(0) * r3[1].get(1) - r3[0].get(1) * r3[1].get(0)
        if (Math.abs(det) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = r3[0].get(0)
        r3[0].get(0) = r3[1].get(1) * invDet
        r3[0].get(1) = -r3[0].get(1) * invDet
        r3[1].get(0) = -r3[1].get(0) * invDet
        r3[1].get(1) = a * invDet

        // r2 = m2 * r0;
        r2[0].get(0) = mat.get(2).x * r0[0].get(0) + mat.get(2).y * r0[1].get(0)
        r2[0].get(1) = mat.get(2).x * r0[0].get(1) + mat.get(2).y * r0[1].get(1)
        r2[1].get(0) = mat.get(3).x * r0[0].get(0) + mat.get(3).y * r0[1].get(0)
        r2[1].get(1) = mat.get(3).x * r0[0].get(1) + mat.get(3).y * r0[1].get(1)

        // m2 = r3 * r2;
        mat.get(2).x = r3[0].get(0) * r2[0].get(0) + r3[0].get(1) * r2[1].get(0)
        mat.get(2).y = r3[0].get(0) * r2[0].get(1) + r3[0].get(1) * r2[1].get(1)
        mat.get(3).x = r3[1].get(0) * r2[0].get(0) + r3[1].get(1) * r2[1].get(0)
        mat.get(3).y = r3[1].get(0) * r2[0].get(1) + r3[1].get(1) * r2[1].get(1)

        // m0 = r0 - r1 * m2;
        mat.get(0).x = r0[0].get(0) - r1[0].get(0) * mat.get(2).x - r1[0].get(1) * mat.get(3).x
        mat.get(0).y = r0[0].get(1) - r1[0].get(0) * mat.get(2).y - r1[0].get(1) * mat.get(3).y
        mat.get(1).x = r0[1].get(0) - r1[1].get(0) * mat.get(2).x - r1[1].get(1) * mat.get(3).x
        mat.get(1).y = r0[1].get(1) - r1[1].get(0) * mat.get(2).y - r1[1].get(1) * mat.get(3).y

        // m1 = r1 * r3;
        mat.get(0).z = r1[0].get(0) * r3[0].get(0) + r1[0].get(1) * r3[1].get(0)
        mat.get(0).w = r1[0].get(0) * r3[0].get(1) + r1[0].get(1) * r3[1].get(1)
        mat.get(1).z = r1[1].get(0) * r3[0].get(0) + r1[1].get(1) * r3[1].get(0)
        mat.get(1).w = r1[1].get(0) * r3[0].get(1) + r1[1].get(1) * r3[1].get(1)

        // m3 = -r3;
        mat.get(2).z = -r3[0].get(0)
        mat.get(2).w = -r3[0].get(1)
        mat.get(3).z = -r3[1].get(0)
        mat.get(3).w = -r3[1].get(1)
        return true
    }

    //public	idMat4			TransposeMultiply( const idMat4 &b ) const;
    fun GetDimension(): Int {
        return 16
    }

    //public	const float *	ToFloatPtr( void ) const;
    //public	float *			ToFloatPtr( void );
    //public	const char *	ToString( int precision = 2 ) const;
    private fun setCell(x: Int, y: Int, value: Float) {
        when (y) {
            0 -> mat.get(x).x = value
            1 -> mat.get(x).y = value
            2 -> mat.get(x).z = value
            3 -> mat.get(x).w = value
        }
    }

    private fun oSet(mat4: idMat4?) {
        mat.get(0).oSet(mat4.mat[0])
        mat.get(1).oSet(mat4.mat[1])
        mat.get(2).oSet(mat4.mat[2])
        mat.get(3).oSet(mat4.mat[3])
    }

    fun reinterpret_cast(): FloatArray? {
        val size = 4
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat.get(x).oGet(y)
            }
        }
        return temp
    }

    companion object {
        private val mat4_identity: idMat4? =
            idMat4(idVec4(1, 0, 0, 0), idVec4(0, 1, 0, 0), idVec4(0, 0, 1, 0), idVec4(0, 0, 0, 1))
        private val mat4_zero: idMat4? =
            idMat4(idVec4(0, 0, 0, 0), idVec4(0, 0, 0, 0), idVec4(0, 0, 0, 0), idVec4(0, 0, 0, 0))

        fun getMat4_zero(): idMat4? {
            return idMat4(idMat4.Companion.mat4_zero)
        }

        fun getMat4_identity(): idMat4? {
            return idMat4(idMat4.Companion.mat4_identity)
        }

        //public	friend idMat4	operator*( const float a, const idMat4 &mat );
        fun oMultiply(a: Float, mat: idMat4?): idMat4? {
            return mat.oMultiply(a)
        }

        //public	friend idVec4	operator*( const idVec4 &vec, const idMat4 &mat );
        fun oMultiply(vec: idVec4?, mat: idMat4?): idVec4? {
            return mat.oMultiply(vec)
        }

        fun oMultiply(vec: idVec3?, mat: idMat4?): idVec3? {
            return mat.oMultiply(vec)
        }

        //public	idVec4 &		operator[]( int index );
        //public	idMat4			operator*( const float a ) const;
        fun oMulSet(vec: idVec4?, mat: idMat4?): idVec4? {
            vec.oSet(mat.oMultiply(vec))
            return vec
        }

        //public	idVec4			operator*( const idVec4 &vec ) const;
        fun oMulSet(vec: idVec3?, mat: idMat4?): idVec3? {
            vec.oSet(mat.oMultiply(vec))
            return vec
        }
    }
}