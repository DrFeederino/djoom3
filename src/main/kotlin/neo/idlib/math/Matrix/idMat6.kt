package neo.idlib.math.Matrix

import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec6
import java.util.*

//===============================================================
//
//	idMat6 - 6x6 matrix
//
//===============================================================
class idMat6 {
    private val mat: Array<idVec6?>? = arrayOf(idVec6(), idVec6(), idVec6(), idVec6(), idVec6(), idVec6())

    constructor()
    constructor(v0: idVec6?, v1: idVec6?, v2: idVec6?, v3: idVec6?, v4: idVec6?, v5: idVec6?) {
        mat.get(0).oSet(v0)
        mat.get(1).oSet(v1)
        mat.get(2).oSet(v2)
        mat.get(3).oSet(v3)
        mat.get(4).oSet(v4)
        mat.get(5).oSet(v5)
    }

    constructor(m0: idMat3?, m1: idMat3?, m2: idMat3?, m3: idMat3?) {
        mat.get(0).oSet(idVec6(m0.mat[0].x, m0.mat[0].y, m0.mat[0].z, m1.mat[0].x, m1.mat[0].y, m1.mat[0].z))
        mat.get(1).oSet(idVec6(m0.mat[1].x, m0.mat[1].y, m0.mat[1].z, m1.mat[1].x, m1.mat[1].y, m1.mat[1].z))
        mat.get(2).oSet(idVec6(m0.mat[2].x, m0.mat[2].y, m0.mat[2].z, m1.mat[2].x, m1.mat[2].y, m1.mat[2].z))
        mat.get(3).oSet(idVec6(m2.mat[0].x, m2.mat[0].y, m2.mat[0].z, m3.mat[0].x, m3.mat[0].y, m3.mat[0].z))
        mat.get(4).oSet(idVec6(m2.mat[1].x, m2.mat[1].y, m2.mat[1].z, m3.mat[1].x, m3.mat[1].y, m3.mat[1].z))
        mat.get(5).oSet(idVec6(m2.mat[2].x, m2.mat[2].y, m2.mat[2].z, m3.mat[2].x, m3.mat[2].y, m3.mat[2].z))
    }

    constructor(src: Array<FloatArray?>?) {
//	memcpy( mat, src, 6 * 6 * sizeof( float ) );
        mat.get(0).oSet(
            idVec6(
                src.get(0).get(0),
                src.get(0).get(1),
                src.get(0).get(2),
                src.get(0).get(3),
                src.get(0).get(4),
                src.get(0).get(5)
            )
        )
        mat.get(1).oSet(
            idVec6(
                src.get(1).get(0),
                src.get(1).get(1),
                src.get(1).get(2),
                src.get(1).get(3),
                src.get(1).get(4),
                src.get(1).get(5)
            )
        )
        mat.get(2).oSet(
            idVec6(
                src.get(2).get(0),
                src.get(2).get(1),
                src.get(2).get(2),
                src.get(2).get(3),
                src.get(2).get(4),
                src.get(2).get(5)
            )
        )
        mat.get(3).oSet(
            idVec6(
                src.get(3).get(0),
                src.get(3).get(1),
                src.get(3).get(2),
                src.get(3).get(3),
                src.get(3).get(4),
                src.get(3).get(5)
            )
        )
        mat.get(4).oSet(
            idVec6(
                src.get(4).get(0),
                src.get(4).get(1),
                src.get(4).get(2),
                src.get(4).get(3),
                src.get(4).get(4),
                src.get(4).get(5)
            )
        )
        mat.get(5).oSet(
            idVec6(
                src.get(5).get(0),
                src.get(5).get(1),
                src.get(5).get(2),
                src.get(5).get(3),
                src.get(5).get(4),
                src.get(5).get(5)
            )
        )
    }

    constructor(m: idMat6?) {
        oSet(m)
    }

    //public	idMat6			operator+( const idMat6 &a ) const;
    //public	const idVec6 &	operator[]( int index ) const;
    //public	idVec6 &		operator[]( int index );
    //public	idMat6			operator*( const float a ) const;
    fun oMultiply(a: Float): idMat6? {
        return idMat6(
            idVec6(
                mat.get(0).p[0] * a,
                mat.get(0).p[1] * a,
                mat.get(0).p[2] * a,
                mat.get(0).p[3] * a,
                mat.get(0).p[4] * a,
                mat.get(0).p[5] * a
            ),
            idVec6(
                mat.get(1).p[0] * a,
                mat.get(1).p[1] * a,
                mat.get(1).p[2] * a,
                mat.get(1).p[3] * a,
                mat.get(1).p[4] * a,
                mat.get(1).p[5] * a
            ),
            idVec6(
                mat.get(2).p[0] * a,
                mat.get(2).p[1] * a,
                mat.get(2).p[2] * a,
                mat.get(2).p[3] * a,
                mat.get(2).p[4] * a,
                mat.get(2).p[5] * a
            ),
            idVec6(
                mat.get(3).p[0] * a,
                mat.get(3).p[1] * a,
                mat.get(3).p[2] * a,
                mat.get(3).p[3] * a,
                mat.get(3).p[4] * a,
                mat.get(3).p[5] * a
            ),
            idVec6(
                mat.get(4).p[0] * a,
                mat.get(4).p[1] * a,
                mat.get(4).p[2] * a,
                mat.get(4).p[3] * a,
                mat.get(4).p[4] * a,
                mat.get(4).p[5] * a
            ),
            idVec6(
                mat.get(5).p[0] * a,
                mat.get(5).p[1] * a,
                mat.get(5).p[2] * a,
                mat.get(5).p[3] * a,
                mat.get(5).p[4] * a,
                mat.get(5).p[5] * a
            )
        )
    }

    fun oMultiply(vec: idVec6?): idVec6? {
        return idVec6(
            mat.get(0).p[0] * vec.p[0] + mat.get(0).p[1] * vec.p[1] + mat.get(0).p[2] * vec.p[2] + mat.get(0).p[3] * vec.p[3] + mat.get(
                0
            ).p[4] * vec.p[4] + mat.get(0).p[5] * vec.p[5],
            mat.get(1).p[0] * vec.p[0] + mat.get(1).p[1] * vec.p[1] + mat.get(1).p[2] * vec.p[2] + mat.get(1).p[3] * vec.p[3] + mat.get(
                1
            ).p[4] * vec.p[4] + mat.get(1).p[5] * vec.p[5],
            mat.get(2).p[0] * vec.p[0] + mat.get(2).p[1] * vec.p[1] + mat.get(2).p[2] * vec.p[2] + mat.get(2).p[3] * vec.p[3] + mat.get(
                2
            ).p[4] * vec.p[4] + mat.get(2).p[5] * vec.p[5],
            mat.get(3).p[0] * vec.p[0] + mat.get(3).p[1] * vec.p[1] + mat.get(3).p[2] * vec.p[2] + mat.get(3).p[3] * vec.p[3] + mat.get(
                3
            ).p[4] * vec.p[4] + mat.get(3).p[5] * vec.p[5],
            mat.get(4).p[0] * vec.p[0] + mat.get(4).p[1] * vec.p[1] + mat.get(4).p[2] * vec.p[2] + mat.get(4).p[3] * vec.p[3] + mat.get(
                4
            ).p[4] * vec.p[4] + mat.get(4).p[5] * vec.p[5],
            mat.get(5).p[0] * vec.p[0] + mat.get(5).p[1] * vec.p[1] + mat.get(5).p[2] * vec.p[2] + mat.get(5).p[3] * vec.p[3] + mat.get(
                5
            ).p[4] * vec.p[4] + mat.get(5).p[5] * vec.p[5]
        )
    }

    //public	idMat6 &		operator*=( const float a );
    fun oMultiply(a: idMat6?): idMat6? {
        var i: Int
        var j: Int
        val m1Ptr: FloatArray?
        val m2Ptr: FloatArray?
        //	float *dstPtr;
        val dst = idMat6()
        m1Ptr = reinterpret_cast()
        m2Ptr = a.reinterpret_cast()
        //	dstPtr = reinterpret_cast<float *>(&dst);
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                dst.mat[i].p[i] =
                    m1Ptr.get(0) * m2Ptr[0 * 6 + j] + m1Ptr.get(1) * m2Ptr[1 * 6 + j] + m1Ptr.get(2) * m2Ptr[2 * 6 + j] + m1Ptr.get(
                        3
                    ) * m2Ptr[3 * 6 + j] + m1Ptr.get(4) * m2Ptr[4 * 6 + j] + m1Ptr.get(5) * m2Ptr[5 * 6 + j]
                j++
            }
            i++
        }
        return dst
    }

    //public	idMat6 &		operator*=( const idMat6 &a );
    fun oPlus(a: idMat6?): idMat6? {
        return idMat6(
            idVec6(
                mat.get(0).p[0] + a.mat[0].p[0],
                mat.get(0).p[1] + a.mat[0].p[1],
                mat.get(0).p[2] + a.mat[0].p[2],
                mat.get(0).p[3] + a.mat[0].p[3],
                mat.get(0).p[4] + a.mat[0].p[4],
                mat.get(0).p[5] + a.mat[0].p[5]
            ),
            idVec6(
                mat.get(1).p[0] + a.mat[1].p[0],
                mat.get(1).p[1] + a.mat[1].p[1],
                mat.get(1).p[2] + a.mat[1].p[2],
                mat.get(1).p[3] + a.mat[1].p[3],
                mat.get(1).p[4] + a.mat[1].p[4],
                mat.get(1).p[5] + a.mat[1].p[5]
            ),
            idVec6(
                mat.get(2).p[0] + a.mat[2].p[0],
                mat.get(2).p[1] + a.mat[2].p[1],
                mat.get(2).p[2] + a.mat[2].p[2],
                mat.get(2).p[3] + a.mat[2].p[3],
                mat.get(2).p[4] + a.mat[2].p[4],
                mat.get(2).p[5] + a.mat[2].p[5]
            ),
            idVec6(
                mat.get(3).p[0] + a.mat[3].p[0],
                mat.get(3).p[1] + a.mat[3].p[1],
                mat.get(3).p[2] + a.mat[3].p[2],
                mat.get(3).p[3] + a.mat[3].p[3],
                mat.get(3).p[4] + a.mat[3].p[4],
                mat.get(3).p[5] + a.mat[3].p[5]
            ),
            idVec6(
                mat.get(4).p[0] + a.mat[4].p[0],
                mat.get(4).p[1] + a.mat[4].p[1],
                mat.get(4).p[2] + a.mat[4].p[2],
                mat.get(4).p[3] + a.mat[4].p[3],
                mat.get(4).p[4] + a.mat[4].p[4],
                mat.get(4).p[5] + a.mat[4].p[5]
            ),
            idVec6(
                mat.get(5).p[0] + a.mat[5].p[0],
                mat.get(5).p[1] + a.mat[5].p[1],
                mat.get(5).p[2] + a.mat[5].p[2],
                mat.get(5).p[3] + a.mat[5].p[3],
                mat.get(5).p[4] + a.mat[5].p[4],
                mat.get(5).p[5] + a.mat[5].p[5]
            )
        )
    }

    //public	idMat6 &		operator+=( const idMat6 &a );
    //public	idMat6			operator-( const idMat6 &a ) const;
    fun oMinus(a: idMat6?): idMat6? {
        return idMat6(
            idVec6(
                mat.get(0).p[0] - a.mat[0].p[0],
                mat.get(0).p[1] - a.mat[0].p[1],
                mat.get(0).p[2] - a.mat[0].p[2],
                mat.get(0).p[3] - a.mat[0].p[3],
                mat.get(0).p[4] - a.mat[0].p[4],
                mat.get(0).p[5] - a.mat[0].p[5]
            ),
            idVec6(
                mat.get(1).p[0] - a.mat[1].p[0],
                mat.get(1).p[1] - a.mat[1].p[1],
                mat.get(1).p[2] - a.mat[1].p[2],
                mat.get(1).p[3] - a.mat[1].p[3],
                mat.get(1).p[4] - a.mat[1].p[4],
                mat.get(1).p[5] - a.mat[1].p[5]
            ),
            idVec6(
                mat.get(2).p[0] - a.mat[2].p[0],
                mat.get(2).p[1] - a.mat[2].p[1],
                mat.get(2).p[2] - a.mat[2].p[2],
                mat.get(2).p[3] - a.mat[2].p[3],
                mat.get(2).p[4] - a.mat[2].p[4],
                mat.get(2).p[5] - a.mat[2].p[5]
            ),
            idVec6(
                mat.get(3).p[0] - a.mat[3].p[0],
                mat.get(3).p[1] - a.mat[3].p[1],
                mat.get(3).p[2] - a.mat[3].p[2],
                mat.get(3).p[3] - a.mat[3].p[3],
                mat.get(3).p[4] - a.mat[3].p[4],
                mat.get(3).p[5] - a.mat[3].p[5]
            ),
            idVec6(
                mat.get(4).p[0] - a.mat[4].p[0],
                mat.get(4).p[1] - a.mat[4].p[1],
                mat.get(4).p[2] - a.mat[4].p[2],
                mat.get(4).p[3] - a.mat[4].p[3],
                mat.get(4).p[4] - a.mat[4].p[4],
                mat.get(4).p[5] - a.mat[4].p[5]
            ),
            idVec6(
                mat.get(5).p[0] - a.mat[5].p[0],
                mat.get(5).p[1] - a.mat[5].p[1],
                mat.get(5).p[2] - a.mat[5].p[2],
                mat.get(5).p[3] - a.mat[5].p[3],
                mat.get(5).p[4] - a.mat[5].p[4],
                mat.get(5).p[5] - a.mat[5].p[5]
            )
        )
    }

    //public	idMat6 &		operator-=( const idMat6 &a );
    fun oMulSet(a: Float): idMat6? {
        mat.get(0).p[0] *= a
        mat.get(0).p[1] *= a
        mat.get(0).p[2] *= a
        mat.get(0).p[3] *= a
        mat.get(0).p[4] *= a
        mat.get(0).p[5] *= a
        mat.get(1).p[0] *= a
        mat.get(1).p[1] *= a
        mat.get(1).p[2] *= a
        mat.get(1).p[3] *= a
        mat.get(1).p[4] *= a
        mat.get(1).p[5] *= a
        mat.get(2).p[0] *= a
        mat.get(2).p[1] *= a
        mat.get(2).p[2] *= a
        mat.get(2).p[3] *= a
        mat.get(2).p[4] *= a
        mat.get(2).p[5] *= a
        mat.get(3).p[0] *= a
        mat.get(3).p[1] *= a
        mat.get(3).p[2] *= a
        mat.get(3).p[3] *= a
        mat.get(3).p[4] *= a
        mat.get(3).p[5] *= a
        mat.get(4).p[0] *= a
        mat.get(4).p[1] *= a
        mat.get(4).p[2] *= a
        mat.get(4).p[3] *= a
        mat.get(4).p[4] *= a
        mat.get(4).p[5] *= a
        mat.get(5).p[0] *= a
        mat.get(5).p[1] *= a
        mat.get(5).p[2] *= a
        mat.get(5).p[3] *= a
        mat.get(5).p[4] *= a
        mat.get(5).p[5] *= a
        return this
    }

    fun oMulSet(a: idMat6?): idMat6? {
        oSet(this.oMultiply(a))
        return this
    }

    //public	friend idVec6	operator*( const idVec6 &vec, const idMat6 &mat );
    fun oPluSet(a: idMat6?): idMat6? {
        mat.get(0).p[0] += a.mat[0].p[0]
        mat.get(0).p[1] += a.mat[0].p[1]
        mat.get(0).p[2] += a.mat[0].p[2]
        mat.get(0).p[3] += a.mat[0].p[3]
        mat.get(0).p[4] += a.mat[0].p[4]
        mat.get(0).p[5] += a.mat[0].p[5]
        mat.get(1).p[0] += a.mat[1].p[0]
        mat.get(1).p[1] += a.mat[1].p[1]
        mat.get(1).p[2] += a.mat[1].p[2]
        mat.get(1).p[3] += a.mat[1].p[3]
        mat.get(1).p[4] += a.mat[1].p[4]
        mat.get(1).p[5] += a.mat[1].p[5]
        mat.get(2).p[0] += a.mat[2].p[0]
        mat.get(2).p[1] += a.mat[2].p[1]
        mat.get(2).p[2] += a.mat[2].p[2]
        mat.get(2).p[3] += a.mat[2].p[3]
        mat.get(2).p[4] += a.mat[2].p[4]
        mat.get(2).p[5] += a.mat[2].p[5]
        mat.get(3).p[0] += a.mat[3].p[0]
        mat.get(3).p[1] += a.mat[3].p[1]
        mat.get(3).p[2] += a.mat[3].p[2]
        mat.get(3).p[3] += a.mat[3].p[3]
        mat.get(3).p[4] += a.mat[3].p[4]
        mat.get(3).p[5] += a.mat[3].p[5]
        mat.get(4).p[0] += a.mat[4].p[0]
        mat.get(4).p[1] += a.mat[4].p[1]
        mat.get(4).p[2] += a.mat[4].p[2]
        mat.get(4).p[3] += a.mat[4].p[3]
        mat.get(4).p[4] += a.mat[4].p[4]
        mat.get(4).p[5] += a.mat[4].p[5]
        mat.get(5).p[0] += a.mat[5].p[0]
        mat.get(5).p[1] += a.mat[5].p[1]
        mat.get(5).p[2] += a.mat[5].p[2]
        mat.get(5).p[3] += a.mat[5].p[3]
        mat.get(5).p[4] += a.mat[5].p[4]
        mat.get(5).p[5] += a.mat[5].p[5]
        return this
    }

    //public	friend idVec6 &	operator*=( idVec6 &vec, const idMat6 &mat );
    fun oMinSet(a: idMat6?): idMat6? {
        mat.get(0).p[0] -= a.mat[0].p[0]
        mat.get(0).p[1] -= a.mat[0].p[1]
        mat.get(0).p[2] -= a.mat[0].p[2]
        mat.get(0).p[3] -= a.mat[0].p[3]
        mat.get(0).p[4] -= a.mat[0].p[4]
        mat.get(0).p[5] -= a.mat[0].p[5]
        mat.get(1).p[0] -= a.mat[1].p[0]
        mat.get(1).p[1] -= a.mat[1].p[1]
        mat.get(1).p[2] -= a.mat[1].p[2]
        mat.get(1).p[3] -= a.mat[1].p[3]
        mat.get(1).p[4] -= a.mat[1].p[4]
        mat.get(1).p[5] -= a.mat[1].p[5]
        mat.get(2).p[0] -= a.mat[2].p[0]
        mat.get(2).p[1] -= a.mat[2].p[1]
        mat.get(2).p[2] -= a.mat[2].p[2]
        mat.get(2).p[3] -= a.mat[2].p[3]
        mat.get(2).p[4] -= a.mat[2].p[4]
        mat.get(2).p[5] -= a.mat[2].p[5]
        mat.get(3).p[0] -= a.mat[3].p[0]
        mat.get(3).p[1] -= a.mat[3].p[1]
        mat.get(3).p[2] -= a.mat[3].p[2]
        mat.get(3).p[3] -= a.mat[3].p[3]
        mat.get(3).p[4] -= a.mat[3].p[4]
        mat.get(3).p[5] -= a.mat[3].p[5]
        mat.get(4).p[0] -= a.mat[4].p[0]
        mat.get(4).p[1] -= a.mat[4].p[1]
        mat.get(4).p[2] -= a.mat[4].p[2]
        mat.get(4).p[3] -= a.mat[4].p[3]
        mat.get(4).p[4] -= a.mat[4].p[4]
        mat.get(4).p[5] -= a.mat[4].p[5]
        mat.get(5).p[0] -= a.mat[5].p[0]
        mat.get(5).p[1] -= a.mat[5].p[1]
        mat.get(5).p[2] -= a.mat[5].p[2]
        mat.get(5).p[3] -= a.mat[5].p[3]
        mat.get(5).p[4] -= a.mat[5].p[4]
        mat.get(5).p[5] -= a.mat[5].p[5]
        return this
    }

    fun Compare(a: idMat6?): Boolean { // exact compare, no epsilon
        var i: Int
        val ptr1: FloatArray?
        val ptr2: FloatArray?
        ptr1 = reinterpret_cast()
        ptr2 = a.reinterpret_cast()
        i = 0
        while (i < 6 * 6) {
            if (ptr1.get(i) != ptr2[i]) {
                return false
            }
            i++
        }
        return true
    }

    fun Compare(a: idMat6?, epsilon: Float): Boolean { // compare with epsilon
        var i: Int
        val ptr1: FloatArray?
        val ptr2: FloatArray?
        ptr1 = reinterpret_cast()
        ptr2 = a.reinterpret_cast()
        i = 0
        while (i < 6 * 6) {
            if (Math.abs(ptr1.get(i) - ptr2[i]) > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    //public	bool			operator==( const idMat6 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat6 &a ) const;					// exact compare, no epsilon
    override fun hashCode(): Int {
        var hash = 3
        hash = 13 * hash + Arrays.deepHashCode(mat)
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat6?
        return Arrays.deepEquals(mat, other.mat)
    }

    fun Zero() {
        oSet(idMat6.Companion.getMat6_zero())
    }

    fun Identity() {
        oSet(idMat6.Companion.getMat6_zero())
    }

    @JvmOverloads
    fun IsIdentity(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return Compare(idMat6.Companion.getMat6_identity(), epsilon)
    }

    @JvmOverloads
    fun IsSymmetric(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        for (i in 1..5) {
            for (j in 0 until i) {
                if (Math.abs(mat.get(i).p[j] - mat.get(j).p[i]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        for (i in 0..5) {
            for (j in 0..5) {
                if (i != j && Math.abs(mat.get(i).p[j]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    fun SubMat3(n: Int): idMat3? {
        assert(n >= 0 && n < 4)
        val b0 = (n and 2 shr 1) * 3
        val b1 = (n and 1) * 3
        return idMat3(
            mat.get(b0 + 0).p[b1 + 0], mat.get(b0 + 0).p[b1 + 1], mat.get(b0 + 0).p[b1 + 2],
            mat.get(b0 + 1).p[b1 + 0], mat.get(b0 + 1).p[b1 + 1], mat.get(b0 + 1).p[b1 + 2],
            mat.get(b0 + 2).p[b1 + 0], mat.get(b0 + 2).p[b1 + 1], mat.get(b0 + 2).p[b1 + 2]
        )
    }

    fun Trace(): Float {
        return mat.get(0).p[0] + mat.get(1).p[1] + mat.get(2).p[2] + mat.get(3).p[3] + mat.get(4).p[4] + mat.get(5).p[5]
    }

    fun Determinant(): Float {
        // 2x2 sub-determinants required to calculate 6x6 determinant
        val det2_45_01 = mat.get(4).p[0] * mat.get(5).p[1] - mat.get(4).p[1] * mat.get(5).p[0]
        val det2_45_02 = mat.get(4).p[0] * mat.get(5).p[2] - mat.get(4).p[2] * mat.get(5).p[0]
        val det2_45_03 = mat.get(4).p[0] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[0]
        val det2_45_04 = mat.get(4).p[0] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[0]
        val det2_45_05 = mat.get(4).p[0] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[0]
        val det2_45_12 = mat.get(4).p[1] * mat.get(5).p[2] - mat.get(4).p[2] * mat.get(5).p[1]
        val det2_45_13 = mat.get(4).p[1] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[1]
        val det2_45_14 = mat.get(4).p[1] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[1]
        val det2_45_15 = mat.get(4).p[1] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[1]
        val det2_45_23 = mat.get(4).p[2] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[2]
        val det2_45_24 = mat.get(4).p[2] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[2]
        val det2_45_25 = mat.get(4).p[2] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[2]
        val det2_45_34 = mat.get(4).p[3] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[3]
        val det2_45_35 = mat.get(4).p[3] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[3]
        val det2_45_45 = mat.get(4).p[4] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[4]

        // 3x3 sub-determinants required to calculate 6x6 determinant
        val det3_345_012 = mat.get(3).p[0] * det2_45_12 - mat.get(3).p[1] * det2_45_02 + mat.get(3).p[2] * det2_45_01
        val det3_345_013 = mat.get(3).p[0] * det2_45_13 - mat.get(3).p[1] * det2_45_03 + mat.get(3).p[3] * det2_45_01
        val det3_345_014 = mat.get(3).p[0] * det2_45_14 - mat.get(3).p[1] * det2_45_04 + mat.get(3).p[4] * det2_45_01
        val det3_345_015 = mat.get(3).p[0] * det2_45_15 - mat.get(3).p[1] * det2_45_05 + mat.get(3).p[5] * det2_45_01
        val det3_345_023 = mat.get(3).p[0] * det2_45_23 - mat.get(3).p[2] * det2_45_03 + mat.get(3).p[3] * det2_45_02
        val det3_345_024 = mat.get(3).p[0] * det2_45_24 - mat.get(3).p[2] * det2_45_04 + mat.get(3).p[4] * det2_45_02
        val det3_345_025 = mat.get(3).p[0] * det2_45_25 - mat.get(3).p[2] * det2_45_05 + mat.get(3).p[5] * det2_45_02
        val det3_345_034 = mat.get(3).p[0] * det2_45_34 - mat.get(3).p[3] * det2_45_04 + mat.get(3).p[4] * det2_45_03
        val det3_345_035 = mat.get(3).p[0] * det2_45_35 - mat.get(3).p[3] * det2_45_05 + mat.get(3).p[5] * det2_45_03
        val det3_345_045 = mat.get(3).p[0] * det2_45_45 - mat.get(3).p[4] * det2_45_05 + mat.get(3).p[5] * det2_45_04
        val det3_345_123 = mat.get(3).p[1] * det2_45_23 - mat.get(3).p[2] * det2_45_13 + mat.get(3).p[3] * det2_45_12
        val det3_345_124 = mat.get(3).p[1] * det2_45_24 - mat.get(3).p[2] * det2_45_14 + mat.get(3).p[4] * det2_45_12
        val det3_345_125 = mat.get(3).p[1] * det2_45_25 - mat.get(3).p[2] * det2_45_15 + mat.get(3).p[5] * det2_45_12
        val det3_345_134 = mat.get(3).p[1] * det2_45_34 - mat.get(3).p[3] * det2_45_14 + mat.get(3).p[4] * det2_45_13
        val det3_345_135 = mat.get(3).p[1] * det2_45_35 - mat.get(3).p[3] * det2_45_15 + mat.get(3).p[5] * det2_45_13
        val det3_345_145 = mat.get(3).p[1] * det2_45_45 - mat.get(3).p[4] * det2_45_15 + mat.get(3).p[5] * det2_45_14
        val det3_345_234 = mat.get(3).p[2] * det2_45_34 - mat.get(3).p[3] * det2_45_24 + mat.get(3).p[4] * det2_45_23
        val det3_345_235 = mat.get(3).p[2] * det2_45_35 - mat.get(3).p[3] * det2_45_25 + mat.get(3).p[5] * det2_45_23
        val det3_345_245 = mat.get(3).p[2] * det2_45_45 - mat.get(3).p[4] * det2_45_25 + mat.get(3).p[5] * det2_45_24
        val det3_345_345 = mat.get(3).p[3] * det2_45_45 - mat.get(3).p[4] * det2_45_35 + mat.get(3).p[5] * det2_45_34

        // 4x4 sub-determinants required to calculate 6x6 determinant
        val det4_2345_0123 =
            mat.get(2).p[0] * det3_345_123 - mat.get(2).p[1] * det3_345_023 + mat.get(2).p[2] * det3_345_013 - mat.get(2).p[3] * det3_345_012
        val det4_2345_0124 =
            mat.get(2).p[0] * det3_345_124 - mat.get(2).p[1] * det3_345_024 + mat.get(2).p[2] * det3_345_014 - mat.get(2).p[4] * det3_345_012
        val det4_2345_0125 =
            mat.get(2).p[0] * det3_345_125 - mat.get(2).p[1] * det3_345_025 + mat.get(2).p[2] * det3_345_015 - mat.get(2).p[5] * det3_345_012
        val det4_2345_0134 =
            mat.get(2).p[0] * det3_345_134 - mat.get(2).p[1] * det3_345_034 + mat.get(2).p[3] * det3_345_014 - mat.get(2).p[4] * det3_345_013
        val det4_2345_0135 =
            mat.get(2).p[0] * det3_345_135 - mat.get(2).p[1] * det3_345_035 + mat.get(2).p[3] * det3_345_015 - mat.get(2).p[5] * det3_345_013
        val det4_2345_0145 =
            mat.get(2).p[0] * det3_345_145 - mat.get(2).p[1] * det3_345_045 + mat.get(2).p[4] * det3_345_015 - mat.get(2).p[5] * det3_345_014
        val det4_2345_0234 =
            mat.get(2).p[0] * det3_345_234 - mat.get(2).p[2] * det3_345_034 + mat.get(2).p[3] * det3_345_024 - mat.get(2).p[4] * det3_345_023
        val det4_2345_0235 =
            mat.get(2).p[0] * det3_345_235 - mat.get(2).p[2] * det3_345_035 + mat.get(2).p[3] * det3_345_025 - mat.get(2).p[5] * det3_345_023
        val det4_2345_0245 =
            mat.get(2).p[0] * det3_345_245 - mat.get(2).p[2] * det3_345_045 + mat.get(2).p[4] * det3_345_025 - mat.get(2).p[5] * det3_345_024
        val det4_2345_0345 =
            mat.get(2).p[0] * det3_345_345 - mat.get(2).p[3] * det3_345_045 + mat.get(2).p[4] * det3_345_035 - mat.get(2).p[5] * det3_345_034
        val det4_2345_1234 =
            mat.get(2).p[1] * det3_345_234 - mat.get(2).p[2] * det3_345_134 + mat.get(2).p[3] * det3_345_124 - mat.get(2).p[4] * det3_345_123
        val det4_2345_1235 =
            mat.get(2).p[1] * det3_345_235 - mat.get(2).p[2] * det3_345_135 + mat.get(2).p[3] * det3_345_125 - mat.get(2).p[5] * det3_345_123
        val det4_2345_1245 =
            mat.get(2).p[1] * det3_345_245 - mat.get(2).p[2] * det3_345_145 + mat.get(2).p[4] * det3_345_125 - mat.get(2).p[5] * det3_345_124
        val det4_2345_1345 =
            mat.get(2).p[1] * det3_345_345 - mat.get(2).p[3] * det3_345_145 + mat.get(2).p[4] * det3_345_135 - mat.get(2).p[5] * det3_345_134
        val det4_2345_2345 =
            mat.get(2).p[2] * det3_345_345 - mat.get(2).p[3] * det3_345_245 + mat.get(2).p[4] * det3_345_235 - mat.get(2).p[5] * det3_345_234

        // 5x5 sub-determinants required to calculate 6x6 determinant
        val det5_12345_01234 =
            mat.get(1).p[0] * det4_2345_1234 - mat.get(1).p[1] * det4_2345_0234 + mat.get(1).p[2] * det4_2345_0134 - mat.get(
                1
            ).p[3] * det4_2345_0124 + mat.get(1).p[4] * det4_2345_0123
        val det5_12345_01235 =
            mat.get(1).p[0] * det4_2345_1235 - mat.get(1).p[1] * det4_2345_0235 + mat.get(1).p[2] * det4_2345_0135 - mat.get(
                1
            ).p[3] * det4_2345_0125 + mat.get(1).p[5] * det4_2345_0123
        val det5_12345_01245 =
            mat.get(1).p[0] * det4_2345_1245 - mat.get(1).p[1] * det4_2345_0245 + mat.get(1).p[2] * det4_2345_0145 - mat.get(
                1
            ).p[4] * det4_2345_0125 + mat.get(1).p[5] * det4_2345_0124
        val det5_12345_01345 =
            mat.get(1).p[0] * det4_2345_1345 - mat.get(1).p[1] * det4_2345_0345 + mat.get(1).p[3] * det4_2345_0145 - mat.get(
                1
            ).p[4] * det4_2345_0135 + mat.get(1).p[5] * det4_2345_0134
        val det5_12345_02345 =
            mat.get(1).p[0] * det4_2345_2345 - mat.get(1).p[2] * det4_2345_0345 + mat.get(1).p[3] * det4_2345_0245 - mat.get(
                1
            ).p[4] * det4_2345_0235 + mat.get(1).p[5] * det4_2345_0234
        val det5_12345_12345 =
            mat.get(1).p[1] * det4_2345_2345 - mat.get(1).p[2] * det4_2345_1345 + mat.get(1).p[3] * det4_2345_1245 - mat.get(
                1
            ).p[4] * det4_2345_1235 + mat.get(1).p[5] * det4_2345_1234

        // determinant of 6x6 matrix
        return mat.get(0).p[0] * det5_12345_12345 - mat.get(0).p[1] * det5_12345_02345 + mat.get(0).p[2] * det5_12345_01345
        -mat.get(0).p[3] * det5_12345_01245 + mat.get(0).p[4] * det5_12345_01235 - mat.get(0).p[5] * det5_12345_01234
    }

    fun Transpose(): idMat6? { // returns transpose
        val transpose = idMat6()
        var i: Int
        var j: Int
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                transpose.mat[i].p[j] = mat.get(j).p[i]
                j++
            }
            i++
        }
        return transpose
    }

    fun TransposeSelf(): idMat6? {
        var temp: Float
        var i: Int
        var j: Int
        i = 0
        while (i < 6) {
            j = i + 1
            while (j < 6) {
                temp = mat.get(i).p[j]
                mat.get(i).p[j] = mat.get(j).p[i]
                mat.get(j).p[i] = temp
                j++
            }
            i++
        }
        return this
    }

    fun Inverse(): idMat6? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat6
        invMat = this
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 810+6+36 = 852 multiplications
        //				1 division
        val det: Double
        val invDet: Double

        // 2x2 sub-determinants required to calculate 6x6 determinant
        val det2_45_01 = mat.get(4).p[0] * mat.get(5).p[1] - mat.get(4).p[1] * mat.get(5).p[0]
        val det2_45_02 = mat.get(4).p[0] * mat.get(5).p[2] - mat.get(4).p[2] * mat.get(5).p[0]
        val det2_45_03 = mat.get(4).p[0] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[0]
        val det2_45_04 = mat.get(4).p[0] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[0]
        val det2_45_05 = mat.get(4).p[0] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[0]
        val det2_45_12 = mat.get(4).p[1] * mat.get(5).p[2] - mat.get(4).p[2] * mat.get(5).p[1]
        val det2_45_13 = mat.get(4).p[1] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[1]
        val det2_45_14 = mat.get(4).p[1] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[1]
        val det2_45_15 = mat.get(4).p[1] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[1]
        val det2_45_23 = mat.get(4).p[2] * mat.get(5).p[3] - mat.get(4).p[3] * mat.get(5).p[2]
        val det2_45_24 = mat.get(4).p[2] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[2]
        val det2_45_25 = mat.get(4).p[2] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[2]
        val det2_45_34 = mat.get(4).p[3] * mat.get(5).p[4] - mat.get(4).p[4] * mat.get(5).p[3]
        val det2_45_35 = mat.get(4).p[3] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[3]
        val det2_45_45 = mat.get(4).p[4] * mat.get(5).p[5] - mat.get(4).p[5] * mat.get(5).p[4]

        // 3x3 sub-determinants required to calculate 6x6 determinant
        val det3_345_012 = mat.get(3).p[0] * det2_45_12 - mat.get(3).p[1] * det2_45_02 + mat.get(3).p[2] * det2_45_01
        val det3_345_013 = mat.get(3).p[0] * det2_45_13 - mat.get(3).p[1] * det2_45_03 + mat.get(3).p[3] * det2_45_01
        val det3_345_014 = mat.get(3).p[0] * det2_45_14 - mat.get(3).p[1] * det2_45_04 + mat.get(3).p[4] * det2_45_01
        val det3_345_015 = mat.get(3).p[0] * det2_45_15 - mat.get(3).p[1] * det2_45_05 + mat.get(3).p[5] * det2_45_01
        val det3_345_023 = mat.get(3).p[0] * det2_45_23 - mat.get(3).p[2] * det2_45_03 + mat.get(3).p[3] * det2_45_02
        val det3_345_024 = mat.get(3).p[0] * det2_45_24 - mat.get(3).p[2] * det2_45_04 + mat.get(3).p[4] * det2_45_02
        val det3_345_025 = mat.get(3).p[0] * det2_45_25 - mat.get(3).p[2] * det2_45_05 + mat.get(3).p[5] * det2_45_02
        val det3_345_034 = mat.get(3).p[0] * det2_45_34 - mat.get(3).p[3] * det2_45_04 + mat.get(3).p[4] * det2_45_03
        val det3_345_035 = mat.get(3).p[0] * det2_45_35 - mat.get(3).p[3] * det2_45_05 + mat.get(3).p[5] * det2_45_03
        val det3_345_045 = mat.get(3).p[0] * det2_45_45 - mat.get(3).p[4] * det2_45_05 + mat.get(3).p[5] * det2_45_04
        val det3_345_123 = mat.get(3).p[1] * det2_45_23 - mat.get(3).p[2] * det2_45_13 + mat.get(3).p[3] * det2_45_12
        val det3_345_124 = mat.get(3).p[1] * det2_45_24 - mat.get(3).p[2] * det2_45_14 + mat.get(3).p[4] * det2_45_12
        val det3_345_125 = mat.get(3).p[1] * det2_45_25 - mat.get(3).p[2] * det2_45_15 + mat.get(3).p[5] * det2_45_12
        val det3_345_134 = mat.get(3).p[1] * det2_45_34 - mat.get(3).p[3] * det2_45_14 + mat.get(3).p[4] * det2_45_13
        val det3_345_135 = mat.get(3).p[1] * det2_45_35 - mat.get(3).p[3] * det2_45_15 + mat.get(3).p[5] * det2_45_13
        val det3_345_145 = mat.get(3).p[1] * det2_45_45 - mat.get(3).p[4] * det2_45_15 + mat.get(3).p[5] * det2_45_14
        val det3_345_234 = mat.get(3).p[2] * det2_45_34 - mat.get(3).p[3] * det2_45_24 + mat.get(3).p[4] * det2_45_23
        val det3_345_235 = mat.get(3).p[2] * det2_45_35 - mat.get(3).p[3] * det2_45_25 + mat.get(3).p[5] * det2_45_23
        val det3_345_245 = mat.get(3).p[2] * det2_45_45 - mat.get(3).p[4] * det2_45_25 + mat.get(3).p[5] * det2_45_24
        val det3_345_345 = mat.get(3).p[3] * det2_45_45 - mat.get(3).p[4] * det2_45_35 + mat.get(3).p[5] * det2_45_34

        // 4x4 sub-determinants required to calculate 6x6 determinant
        val det4_2345_0123 =
            mat.get(2).p[0] * det3_345_123 - mat.get(2).p[1] * det3_345_023 + mat.get(2).p[2] * det3_345_013 - mat.get(2).p[3] * det3_345_012
        val det4_2345_0124 =
            mat.get(2).p[0] * det3_345_124 - mat.get(2).p[1] * det3_345_024 + mat.get(2).p[2] * det3_345_014 - mat.get(2).p[4] * det3_345_012
        val det4_2345_0125 =
            mat.get(2).p[0] * det3_345_125 - mat.get(2).p[1] * det3_345_025 + mat.get(2).p[2] * det3_345_015 - mat.get(2).p[5] * det3_345_012
        val det4_2345_0134 =
            mat.get(2).p[0] * det3_345_134 - mat.get(2).p[1] * det3_345_034 + mat.get(2).p[3] * det3_345_014 - mat.get(2).p[4] * det3_345_013
        val det4_2345_0135 =
            mat.get(2).p[0] * det3_345_135 - mat.get(2).p[1] * det3_345_035 + mat.get(2).p[3] * det3_345_015 - mat.get(2).p[5] * det3_345_013
        val det4_2345_0145 =
            mat.get(2).p[0] * det3_345_145 - mat.get(2).p[1] * det3_345_045 + mat.get(2).p[4] * det3_345_015 - mat.get(2).p[5] * det3_345_014
        val det4_2345_0234 =
            mat.get(2).p[0] * det3_345_234 - mat.get(2).p[2] * det3_345_034 + mat.get(2).p[3] * det3_345_024 - mat.get(2).p[4] * det3_345_023
        val det4_2345_0235 =
            mat.get(2).p[0] * det3_345_235 - mat.get(2).p[2] * det3_345_035 + mat.get(2).p[3] * det3_345_025 - mat.get(2).p[5] * det3_345_023
        val det4_2345_0245 =
            mat.get(2).p[0] * det3_345_245 - mat.get(2).p[2] * det3_345_045 + mat.get(2).p[4] * det3_345_025 - mat.get(2).p[5] * det3_345_024
        val det4_2345_0345 =
            mat.get(2).p[0] * det3_345_345 - mat.get(2).p[3] * det3_345_045 + mat.get(2).p[4] * det3_345_035 - mat.get(2).p[5] * det3_345_034
        val det4_2345_1234 =
            mat.get(2).p[1] * det3_345_234 - mat.get(2).p[2] * det3_345_134 + mat.get(2).p[3] * det3_345_124 - mat.get(2).p[4] * det3_345_123
        val det4_2345_1235 =
            mat.get(2).p[1] * det3_345_235 - mat.get(2).p[2] * det3_345_135 + mat.get(2).p[3] * det3_345_125 - mat.get(2).p[5] * det3_345_123
        val det4_2345_1245 =
            mat.get(2).p[1] * det3_345_245 - mat.get(2).p[2] * det3_345_145 + mat.get(2).p[4] * det3_345_125 - mat.get(2).p[5] * det3_345_124
        val det4_2345_1345 =
            mat.get(2).p[1] * det3_345_345 - mat.get(2).p[3] * det3_345_145 + mat.get(2).p[4] * det3_345_135 - mat.get(2).p[5] * det3_345_134
        val det4_2345_2345 =
            mat.get(2).p[2] * det3_345_345 - mat.get(2).p[3] * det3_345_245 + mat.get(2).p[4] * det3_345_235 - mat.get(2).p[5] * det3_345_234

        // 5x5 sub-determinants required to calculate 6x6 determinant
        val det5_12345_01234 =
            mat.get(1).p[0] * det4_2345_1234 - mat.get(1).p[1] * det4_2345_0234 + mat.get(1).p[2] * det4_2345_0134 - mat.get(
                1
            ).p[3] * det4_2345_0124 + mat.get(1).p[4] * det4_2345_0123
        val det5_12345_01235 =
            mat.get(1).p[0] * det4_2345_1235 - mat.get(1).p[1] * det4_2345_0235 + mat.get(1).p[2] * det4_2345_0135 - mat.get(
                1
            ).p[3] * det4_2345_0125 + mat.get(1).p[5] * det4_2345_0123
        val det5_12345_01245 =
            mat.get(1).p[0] * det4_2345_1245 - mat.get(1).p[1] * det4_2345_0245 + mat.get(1).p[2] * det4_2345_0145 - mat.get(
                1
            ).p[4] * det4_2345_0125 + mat.get(1).p[5] * det4_2345_0124
        val det5_12345_01345 =
            mat.get(1).p[0] * det4_2345_1345 - mat.get(1).p[1] * det4_2345_0345 + mat.get(1).p[3] * det4_2345_0145 - mat.get(
                1
            ).p[4] * det4_2345_0135 + mat.get(1).p[5] * det4_2345_0134
        val det5_12345_02345 =
            mat.get(1).p[0] * det4_2345_2345 - mat.get(1).p[2] * det4_2345_0345 + mat.get(1).p[3] * det4_2345_0245 - mat.get(
                1
            ).p[4] * det4_2345_0235 + mat.get(1).p[5] * det4_2345_0234
        val det5_12345_12345 =
            mat.get(1).p[1] * det4_2345_2345 - mat.get(1).p[2] * det4_2345_1345 + mat.get(1).p[3] * det4_2345_1245 - mat.get(
                1
            ).p[4] * det4_2345_1235 + mat.get(1).p[5] * det4_2345_1234

        // determinant of 6x6 matrix
        det =
            (mat.get(0).p[0] * det5_12345_12345 - mat.get(0).p[1] * det5_12345_02345 + mat.get(0).p[2] * det5_12345_01345
                    - mat.get(0).p[3] * det5_12345_01245 + mat.get(0).p[4] * det5_12345_01235 - mat.get(0).p[5] * det5_12345_01234).toDouble()
        if (Math.abs(det.toFloat()) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det

        // remaining 2x2 sub-determinants
        val det2_34_01 = mat.get(3).p[0] * mat.get(4).p[1] - mat.get(3).p[1] * mat.get(4).p[0]
        val det2_34_02 = mat.get(3).p[0] * mat.get(4).p[2] - mat.get(3).p[2] * mat.get(4).p[0]
        val det2_34_03 = mat.get(3).p[0] * mat.get(4).p[3] - mat.get(3).p[3] * mat.get(4).p[0]
        val det2_34_04 = mat.get(3).p[0] * mat.get(4).p[4] - mat.get(3).p[4] * mat.get(4).p[0]
        val det2_34_05 = mat.get(3).p[0] * mat.get(4).p[5] - mat.get(3).p[5] * mat.get(4).p[0]
        val det2_34_12 = mat.get(3).p[1] * mat.get(4).p[2] - mat.get(3).p[2] * mat.get(4).p[1]
        val det2_34_13 = mat.get(3).p[1] * mat.get(4).p[3] - mat.get(3).p[3] * mat.get(4).p[1]
        val det2_34_14 = mat.get(3).p[1] * mat.get(4).p[4] - mat.get(3).p[4] * mat.get(4).p[1]
        val det2_34_15 = mat.get(3).p[1] * mat.get(4).p[5] - mat.get(3).p[5] * mat.get(4).p[1]
        val det2_34_23 = mat.get(3).p[2] * mat.get(4).p[3] - mat.get(3).p[3] * mat.get(4).p[2]
        val det2_34_24 = mat.get(3).p[2] * mat.get(4).p[4] - mat.get(3).p[4] * mat.get(4).p[2]
        val det2_34_25 = mat.get(3).p[2] * mat.get(4).p[5] - mat.get(3).p[5] * mat.get(4).p[2]
        val det2_34_34 = mat.get(3).p[3] * mat.get(4).p[4] - mat.get(3).p[4] * mat.get(4).p[3]
        val det2_34_35 = mat.get(3).p[3] * mat.get(4).p[5] - mat.get(3).p[5] * mat.get(4).p[3]
        val det2_34_45 = mat.get(3).p[4] * mat.get(4).p[5] - mat.get(3).p[5] * mat.get(4).p[4]
        val det2_35_01 = mat.get(3).p[0] * mat.get(5).p[1] - mat.get(3).p[1] * mat.get(5).p[0]
        val det2_35_02 = mat.get(3).p[0] * mat.get(5).p[2] - mat.get(3).p[2] * mat.get(5).p[0]
        val det2_35_03 = mat.get(3).p[0] * mat.get(5).p[3] - mat.get(3).p[3] * mat.get(5).p[0]
        val det2_35_04 = mat.get(3).p[0] * mat.get(5).p[4] - mat.get(3).p[4] * mat.get(5).p[0]
        val det2_35_05 = mat.get(3).p[0] * mat.get(5).p[5] - mat.get(3).p[5] * mat.get(5).p[0]
        val det2_35_12 = mat.get(3).p[1] * mat.get(5).p[2] - mat.get(3).p[2] * mat.get(5).p[1]
        val det2_35_13 = mat.get(3).p[1] * mat.get(5).p[3] - mat.get(3).p[3] * mat.get(5).p[1]
        val det2_35_14 = mat.get(3).p[1] * mat.get(5).p[4] - mat.get(3).p[4] * mat.get(5).p[1]
        val det2_35_15 = mat.get(3).p[1] * mat.get(5).p[5] - mat.get(3).p[5] * mat.get(5).p[1]
        val det2_35_23 = mat.get(3).p[2] * mat.get(5).p[3] - mat.get(3).p[3] * mat.get(5).p[2]
        val det2_35_24 = mat.get(3).p[2] * mat.get(5).p[4] - mat.get(3).p[4] * mat.get(5).p[2]
        val det2_35_25 = mat.get(3).p[2] * mat.get(5).p[5] - mat.get(3).p[5] * mat.get(5).p[2]
        val det2_35_34 = mat.get(3).p[3] * mat.get(5).p[4] - mat.get(3).p[4] * mat.get(5).p[3]
        val det2_35_35 = mat.get(3).p[3] * mat.get(5).p[5] - mat.get(3).p[5] * mat.get(5).p[3]
        val det2_35_45 = mat.get(3).p[4] * mat.get(5).p[5] - mat.get(3).p[5] * mat.get(5).p[4]

        // remaining 3x3 sub-determinants
        val det3_234_012 = mat.get(2).p[0] * det2_34_12 - mat.get(2).p[1] * det2_34_02 + mat.get(2).p[2] * det2_34_01
        val det3_234_013 = mat.get(2).p[0] * det2_34_13 - mat.get(2).p[1] * det2_34_03 + mat.get(2).p[3] * det2_34_01
        val det3_234_014 = mat.get(2).p[0] * det2_34_14 - mat.get(2).p[1] * det2_34_04 + mat.get(2).p[4] * det2_34_01
        val det3_234_015 = mat.get(2).p[0] * det2_34_15 - mat.get(2).p[1] * det2_34_05 + mat.get(2).p[5] * det2_34_01
        val det3_234_023 = mat.get(2).p[0] * det2_34_23 - mat.get(2).p[2] * det2_34_03 + mat.get(2).p[3] * det2_34_02
        val det3_234_024 = mat.get(2).p[0] * det2_34_24 - mat.get(2).p[2] * det2_34_04 + mat.get(2).p[4] * det2_34_02
        val det3_234_025 = mat.get(2).p[0] * det2_34_25 - mat.get(2).p[2] * det2_34_05 + mat.get(2).p[5] * det2_34_02
        val det3_234_034 = mat.get(2).p[0] * det2_34_34 - mat.get(2).p[3] * det2_34_04 + mat.get(2).p[4] * det2_34_03
        val det3_234_035 = mat.get(2).p[0] * det2_34_35 - mat.get(2).p[3] * det2_34_05 + mat.get(2).p[5] * det2_34_03
        val det3_234_045 = mat.get(2).p[0] * det2_34_45 - mat.get(2).p[4] * det2_34_05 + mat.get(2).p[5] * det2_34_04
        val det3_234_123 = mat.get(2).p[1] * det2_34_23 - mat.get(2).p[2] * det2_34_13 + mat.get(2).p[3] * det2_34_12
        val det3_234_124 = mat.get(2).p[1] * det2_34_24 - mat.get(2).p[2] * det2_34_14 + mat.get(2).p[4] * det2_34_12
        val det3_234_125 = mat.get(2).p[1] * det2_34_25 - mat.get(2).p[2] * det2_34_15 + mat.get(2).p[5] * det2_34_12
        val det3_234_134 = mat.get(2).p[1] * det2_34_34 - mat.get(2).p[3] * det2_34_14 + mat.get(2).p[4] * det2_34_13
        val det3_234_135 = mat.get(2).p[1] * det2_34_35 - mat.get(2).p[3] * det2_34_15 + mat.get(2).p[5] * det2_34_13
        val det3_234_145 = mat.get(2).p[1] * det2_34_45 - mat.get(2).p[4] * det2_34_15 + mat.get(2).p[5] * det2_34_14
        val det3_234_234 = mat.get(2).p[2] * det2_34_34 - mat.get(2).p[3] * det2_34_24 + mat.get(2).p[4] * det2_34_23
        val det3_234_235 = mat.get(2).p[2] * det2_34_35 - mat.get(2).p[3] * det2_34_25 + mat.get(2).p[5] * det2_34_23
        val det3_234_245 = mat.get(2).p[2] * det2_34_45 - mat.get(2).p[4] * det2_34_25 + mat.get(2).p[5] * det2_34_24
        val det3_234_345 = mat.get(2).p[3] * det2_34_45 - mat.get(2).p[4] * det2_34_35 + mat.get(2).p[5] * det2_34_34
        val det3_235_012 = mat.get(2).p[0] * det2_35_12 - mat.get(2).p[1] * det2_35_02 + mat.get(2).p[2] * det2_35_01
        val det3_235_013 = mat.get(2).p[0] * det2_35_13 - mat.get(2).p[1] * det2_35_03 + mat.get(2).p[3] * det2_35_01
        val det3_235_014 = mat.get(2).p[0] * det2_35_14 - mat.get(2).p[1] * det2_35_04 + mat.get(2).p[4] * det2_35_01
        val det3_235_015 = mat.get(2).p[0] * det2_35_15 - mat.get(2).p[1] * det2_35_05 + mat.get(2).p[5] * det2_35_01
        val det3_235_023 = mat.get(2).p[0] * det2_35_23 - mat.get(2).p[2] * det2_35_03 + mat.get(2).p[3] * det2_35_02
        val det3_235_024 = mat.get(2).p[0] * det2_35_24 - mat.get(2).p[2] * det2_35_04 + mat.get(2).p[4] * det2_35_02
        val det3_235_025 = mat.get(2).p[0] * det2_35_25 - mat.get(2).p[2] * det2_35_05 + mat.get(2).p[5] * det2_35_02
        val det3_235_034 = mat.get(2).p[0] * det2_35_34 - mat.get(2).p[3] * det2_35_04 + mat.get(2).p[4] * det2_35_03
        val det3_235_035 = mat.get(2).p[0] * det2_35_35 - mat.get(2).p[3] * det2_35_05 + mat.get(2).p[5] * det2_35_03
        val det3_235_045 = mat.get(2).p[0] * det2_35_45 - mat.get(2).p[4] * det2_35_05 + mat.get(2).p[5] * det2_35_04
        val det3_235_123 = mat.get(2).p[1] * det2_35_23 - mat.get(2).p[2] * det2_35_13 + mat.get(2).p[3] * det2_35_12
        val det3_235_124 = mat.get(2).p[1] * det2_35_24 - mat.get(2).p[2] * det2_35_14 + mat.get(2).p[4] * det2_35_12
        val det3_235_125 = mat.get(2).p[1] * det2_35_25 - mat.get(2).p[2] * det2_35_15 + mat.get(2).p[5] * det2_35_12
        val det3_235_134 = mat.get(2).p[1] * det2_35_34 - mat.get(2).p[3] * det2_35_14 + mat.get(2).p[4] * det2_35_13
        val det3_235_135 = mat.get(2).p[1] * det2_35_35 - mat.get(2).p[3] * det2_35_15 + mat.get(2).p[5] * det2_35_13
        val det3_235_145 = mat.get(2).p[1] * det2_35_45 - mat.get(2).p[4] * det2_35_15 + mat.get(2).p[5] * det2_35_14
        val det3_235_234 = mat.get(2).p[2] * det2_35_34 - mat.get(2).p[3] * det2_35_24 + mat.get(2).p[4] * det2_35_23
        val det3_235_235 = mat.get(2).p[2] * det2_35_35 - mat.get(2).p[3] * det2_35_25 + mat.get(2).p[5] * det2_35_23
        val det3_235_245 = mat.get(2).p[2] * det2_35_45 - mat.get(2).p[4] * det2_35_25 + mat.get(2).p[5] * det2_35_24
        val det3_235_345 = mat.get(2).p[3] * det2_35_45 - mat.get(2).p[4] * det2_35_35 + mat.get(2).p[5] * det2_35_34
        val det3_245_012 = mat.get(2).p[0] * det2_45_12 - mat.get(2).p[1] * det2_45_02 + mat.get(2).p[2] * det2_45_01
        val det3_245_013 = mat.get(2).p[0] * det2_45_13 - mat.get(2).p[1] * det2_45_03 + mat.get(2).p[3] * det2_45_01
        val det3_245_014 = mat.get(2).p[0] * det2_45_14 - mat.get(2).p[1] * det2_45_04 + mat.get(2).p[4] * det2_45_01
        val det3_245_015 = mat.get(2).p[0] * det2_45_15 - mat.get(2).p[1] * det2_45_05 + mat.get(2).p[5] * det2_45_01
        val det3_245_023 = mat.get(2).p[0] * det2_45_23 - mat.get(2).p[2] * det2_45_03 + mat.get(2).p[3] * det2_45_02
        val det3_245_024 = mat.get(2).p[0] * det2_45_24 - mat.get(2).p[2] * det2_45_04 + mat.get(2).p[4] * det2_45_02
        val det3_245_025 = mat.get(2).p[0] * det2_45_25 - mat.get(2).p[2] * det2_45_05 + mat.get(2).p[5] * det2_45_02
        val det3_245_034 = mat.get(2).p[0] * det2_45_34 - mat.get(2).p[3] * det2_45_04 + mat.get(2).p[4] * det2_45_03
        val det3_245_035 = mat.get(2).p[0] * det2_45_35 - mat.get(2).p[3] * det2_45_05 + mat.get(2).p[5] * det2_45_03
        val det3_245_045 = mat.get(2).p[0] * det2_45_45 - mat.get(2).p[4] * det2_45_05 + mat.get(2).p[5] * det2_45_04
        val det3_245_123 = mat.get(2).p[1] * det2_45_23 - mat.get(2).p[2] * det2_45_13 + mat.get(2).p[3] * det2_45_12
        val det3_245_124 = mat.get(2).p[1] * det2_45_24 - mat.get(2).p[2] * det2_45_14 + mat.get(2).p[4] * det2_45_12
        val det3_245_125 = mat.get(2).p[1] * det2_45_25 - mat.get(2).p[2] * det2_45_15 + mat.get(2).p[5] * det2_45_12
        val det3_245_134 = mat.get(2).p[1] * det2_45_34 - mat.get(2).p[3] * det2_45_14 + mat.get(2).p[4] * det2_45_13
        val det3_245_135 = mat.get(2).p[1] * det2_45_35 - mat.get(2).p[3] * det2_45_15 + mat.get(2).p[5] * det2_45_13
        val det3_245_145 = mat.get(2).p[1] * det2_45_45 - mat.get(2).p[4] * det2_45_15 + mat.get(2).p[5] * det2_45_14
        val det3_245_234 = mat.get(2).p[2] * det2_45_34 - mat.get(2).p[3] * det2_45_24 + mat.get(2).p[4] * det2_45_23
        val det3_245_235 = mat.get(2).p[2] * det2_45_35 - mat.get(2).p[3] * det2_45_25 + mat.get(2).p[5] * det2_45_23
        val det3_245_245 = mat.get(2).p[2] * det2_45_45 - mat.get(2).p[4] * det2_45_25 + mat.get(2).p[5] * det2_45_24
        val det3_245_345 = mat.get(2).p[3] * det2_45_45 - mat.get(2).p[4] * det2_45_35 + mat.get(2).p[5] * det2_45_34

        // remaining 4x4 sub-determinants
        val det4_1234_0123 =
            mat.get(1).p[0] * det3_234_123 - mat.get(1).p[1] * det3_234_023 + mat.get(1).p[2] * det3_234_013 - mat.get(1).p[3] * det3_234_012
        val det4_1234_0124 =
            mat.get(1).p[0] * det3_234_124 - mat.get(1).p[1] * det3_234_024 + mat.get(1).p[2] * det3_234_014 - mat.get(1).p[4] * det3_234_012
        val det4_1234_0125 =
            mat.get(1).p[0] * det3_234_125 - mat.get(1).p[1] * det3_234_025 + mat.get(1).p[2] * det3_234_015 - mat.get(1).p[5] * det3_234_012
        val det4_1234_0134 =
            mat.get(1).p[0] * det3_234_134 - mat.get(1).p[1] * det3_234_034 + mat.get(1).p[3] * det3_234_014 - mat.get(1).p[4] * det3_234_013
        val det4_1234_0135 =
            mat.get(1).p[0] * det3_234_135 - mat.get(1).p[1] * det3_234_035 + mat.get(1).p[3] * det3_234_015 - mat.get(1).p[5] * det3_234_013
        val det4_1234_0145 =
            mat.get(1).p[0] * det3_234_145 - mat.get(1).p[1] * det3_234_045 + mat.get(1).p[4] * det3_234_015 - mat.get(1).p[5] * det3_234_014
        val det4_1234_0234 =
            mat.get(1).p[0] * det3_234_234 - mat.get(1).p[2] * det3_234_034 + mat.get(1).p[3] * det3_234_024 - mat.get(1).p[4] * det3_234_023
        val det4_1234_0235 =
            mat.get(1).p[0] * det3_234_235 - mat.get(1).p[2] * det3_234_035 + mat.get(1).p[3] * det3_234_025 - mat.get(1).p[5] * det3_234_023
        val det4_1234_0245 =
            mat.get(1).p[0] * det3_234_245 - mat.get(1).p[2] * det3_234_045 + mat.get(1).p[4] * det3_234_025 - mat.get(1).p[5] * det3_234_024
        val det4_1234_0345 =
            mat.get(1).p[0] * det3_234_345 - mat.get(1).p[3] * det3_234_045 + mat.get(1).p[4] * det3_234_035 - mat.get(1).p[5] * det3_234_034
        val det4_1234_1234 =
            mat.get(1).p[1] * det3_234_234 - mat.get(1).p[2] * det3_234_134 + mat.get(1).p[3] * det3_234_124 - mat.get(1).p[4] * det3_234_123
        val det4_1234_1235 =
            mat.get(1).p[1] * det3_234_235 - mat.get(1).p[2] * det3_234_135 + mat.get(1).p[3] * det3_234_125 - mat.get(1).p[5] * det3_234_123
        val det4_1234_1245 =
            mat.get(1).p[1] * det3_234_245 - mat.get(1).p[2] * det3_234_145 + mat.get(1).p[4] * det3_234_125 - mat.get(1).p[5] * det3_234_124
        val det4_1234_1345 =
            mat.get(1).p[1] * det3_234_345 - mat.get(1).p[3] * det3_234_145 + mat.get(1).p[4] * det3_234_135 - mat.get(1).p[5] * det3_234_134
        val det4_1234_2345 =
            mat.get(1).p[2] * det3_234_345 - mat.get(1).p[3] * det3_234_245 + mat.get(1).p[4] * det3_234_235 - mat.get(1).p[5] * det3_234_234
        val det4_1235_0123 =
            mat.get(1).p[0] * det3_235_123 - mat.get(1).p[1] * det3_235_023 + mat.get(1).p[2] * det3_235_013 - mat.get(1).p[3] * det3_235_012
        val det4_1235_0124 =
            mat.get(1).p[0] * det3_235_124 - mat.get(1).p[1] * det3_235_024 + mat.get(1).p[2] * det3_235_014 - mat.get(1).p[4] * det3_235_012
        val det4_1235_0125 =
            mat.get(1).p[0] * det3_235_125 - mat.get(1).p[1] * det3_235_025 + mat.get(1).p[2] * det3_235_015 - mat.get(1).p[5] * det3_235_012
        val det4_1235_0134 =
            mat.get(1).p[0] * det3_235_134 - mat.get(1).p[1] * det3_235_034 + mat.get(1).p[3] * det3_235_014 - mat.get(1).p[4] * det3_235_013
        val det4_1235_0135 =
            mat.get(1).p[0] * det3_235_135 - mat.get(1).p[1] * det3_235_035 + mat.get(1).p[3] * det3_235_015 - mat.get(1).p[5] * det3_235_013
        val det4_1235_0145 =
            mat.get(1).p[0] * det3_235_145 - mat.get(1).p[1] * det3_235_045 + mat.get(1).p[4] * det3_235_015 - mat.get(1).p[5] * det3_235_014
        val det4_1235_0234 =
            mat.get(1).p[0] * det3_235_234 - mat.get(1).p[2] * det3_235_034 + mat.get(1).p[3] * det3_235_024 - mat.get(1).p[4] * det3_235_023
        val det4_1235_0235 =
            mat.get(1).p[0] * det3_235_235 - mat.get(1).p[2] * det3_235_035 + mat.get(1).p[3] * det3_235_025 - mat.get(1).p[5] * det3_235_023
        val det4_1235_0245 =
            mat.get(1).p[0] * det3_235_245 - mat.get(1).p[2] * det3_235_045 + mat.get(1).p[4] * det3_235_025 - mat.get(1).p[5] * det3_235_024
        val det4_1235_0345 =
            mat.get(1).p[0] * det3_235_345 - mat.get(1).p[3] * det3_235_045 + mat.get(1).p[4] * det3_235_035 - mat.get(1).p[5] * det3_235_034
        val det4_1235_1234 =
            mat.get(1).p[1] * det3_235_234 - mat.get(1).p[2] * det3_235_134 + mat.get(1).p[3] * det3_235_124 - mat.get(1).p[4] * det3_235_123
        val det4_1235_1235 =
            mat.get(1).p[1] * det3_235_235 - mat.get(1).p[2] * det3_235_135 + mat.get(1).p[3] * det3_235_125 - mat.get(1).p[5] * det3_235_123
        val det4_1235_1245 =
            mat.get(1).p[1] * det3_235_245 - mat.get(1).p[2] * det3_235_145 + mat.get(1).p[4] * det3_235_125 - mat.get(1).p[5] * det3_235_124
        val det4_1235_1345 =
            mat.get(1).p[1] * det3_235_345 - mat.get(1).p[3] * det3_235_145 + mat.get(1).p[4] * det3_235_135 - mat.get(1).p[5] * det3_235_134
        val det4_1235_2345 =
            mat.get(1).p[2] * det3_235_345 - mat.get(1).p[3] * det3_235_245 + mat.get(1).p[4] * det3_235_235 - mat.get(1).p[5] * det3_235_234
        val det4_1245_0123 =
            mat.get(1).p[0] * det3_245_123 - mat.get(1).p[1] * det3_245_023 + mat.get(1).p[2] * det3_245_013 - mat.get(1).p[3] * det3_245_012
        val det4_1245_0124 =
            mat.get(1).p[0] * det3_245_124 - mat.get(1).p[1] * det3_245_024 + mat.get(1).p[2] * det3_245_014 - mat.get(1).p[4] * det3_245_012
        val det4_1245_0125 =
            mat.get(1).p[0] * det3_245_125 - mat.get(1).p[1] * det3_245_025 + mat.get(1).p[2] * det3_245_015 - mat.get(1).p[5] * det3_245_012
        val det4_1245_0134 =
            mat.get(1).p[0] * det3_245_134 - mat.get(1).p[1] * det3_245_034 + mat.get(1).p[3] * det3_245_014 - mat.get(1).p[4] * det3_245_013
        val det4_1245_0135 =
            mat.get(1).p[0] * det3_245_135 - mat.get(1).p[1] * det3_245_035 + mat.get(1).p[3] * det3_245_015 - mat.get(1).p[5] * det3_245_013
        val det4_1245_0145 =
            mat.get(1).p[0] * det3_245_145 - mat.get(1).p[1] * det3_245_045 + mat.get(1).p[4] * det3_245_015 - mat.get(1).p[5] * det3_245_014
        val det4_1245_0234 =
            mat.get(1).p[0] * det3_245_234 - mat.get(1).p[2] * det3_245_034 + mat.get(1).p[3] * det3_245_024 - mat.get(1).p[4] * det3_245_023
        val det4_1245_0235 =
            mat.get(1).p[0] * det3_245_235 - mat.get(1).p[2] * det3_245_035 + mat.get(1).p[3] * det3_245_025 - mat.get(1).p[5] * det3_245_023
        val det4_1245_0245 =
            mat.get(1).p[0] * det3_245_245 - mat.get(1).p[2] * det3_245_045 + mat.get(1).p[4] * det3_245_025 - mat.get(1).p[5] * det3_245_024
        val det4_1245_0345 =
            mat.get(1).p[0] * det3_245_345 - mat.get(1).p[3] * det3_245_045 + mat.get(1).p[4] * det3_245_035 - mat.get(1).p[5] * det3_245_034
        val det4_1245_1234 =
            mat.get(1).p[1] * det3_245_234 - mat.get(1).p[2] * det3_245_134 + mat.get(1).p[3] * det3_245_124 - mat.get(1).p[4] * det3_245_123
        val det4_1245_1235 =
            mat.get(1).p[1] * det3_245_235 - mat.get(1).p[2] * det3_245_135 + mat.get(1).p[3] * det3_245_125 - mat.get(1).p[5] * det3_245_123
        val det4_1245_1245 =
            mat.get(1).p[1] * det3_245_245 - mat.get(1).p[2] * det3_245_145 + mat.get(1).p[4] * det3_245_125 - mat.get(1).p[5] * det3_245_124
        val det4_1245_1345 =
            mat.get(1).p[1] * det3_245_345 - mat.get(1).p[3] * det3_245_145 + mat.get(1).p[4] * det3_245_135 - mat.get(1).p[5] * det3_245_134
        val det4_1245_2345 =
            mat.get(1).p[2] * det3_245_345 - mat.get(1).p[3] * det3_245_245 + mat.get(1).p[4] * det3_245_235 - mat.get(1).p[5] * det3_245_234
        val det4_1345_0123 =
            mat.get(1).p[0] * det3_345_123 - mat.get(1).p[1] * det3_345_023 + mat.get(1).p[2] * det3_345_013 - mat.get(1).p[3] * det3_345_012
        val det4_1345_0124 =
            mat.get(1).p[0] * det3_345_124 - mat.get(1).p[1] * det3_345_024 + mat.get(1).p[2] * det3_345_014 - mat.get(1).p[4] * det3_345_012
        val det4_1345_0125 =
            mat.get(1).p[0] * det3_345_125 - mat.get(1).p[1] * det3_345_025 + mat.get(1).p[2] * det3_345_015 - mat.get(1).p[5] * det3_345_012
        val det4_1345_0134 =
            mat.get(1).p[0] * det3_345_134 - mat.get(1).p[1] * det3_345_034 + mat.get(1).p[3] * det3_345_014 - mat.get(1).p[4] * det3_345_013
        val det4_1345_0135 =
            mat.get(1).p[0] * det3_345_135 - mat.get(1).p[1] * det3_345_035 + mat.get(1).p[3] * det3_345_015 - mat.get(1).p[5] * det3_345_013
        val det4_1345_0145 =
            mat.get(1).p[0] * det3_345_145 - mat.get(1).p[1] * det3_345_045 + mat.get(1).p[4] * det3_345_015 - mat.get(1).p[5] * det3_345_014
        val det4_1345_0234 =
            mat.get(1).p[0] * det3_345_234 - mat.get(1).p[2] * det3_345_034 + mat.get(1).p[3] * det3_345_024 - mat.get(1).p[4] * det3_345_023
        val det4_1345_0235 =
            mat.get(1).p[0] * det3_345_235 - mat.get(1).p[2] * det3_345_035 + mat.get(1).p[3] * det3_345_025 - mat.get(1).p[5] * det3_345_023
        val det4_1345_0245 =
            mat.get(1).p[0] * det3_345_245 - mat.get(1).p[2] * det3_345_045 + mat.get(1).p[4] * det3_345_025 - mat.get(1).p[5] * det3_345_024
        val det4_1345_0345 =
            mat.get(1).p[0] * det3_345_345 - mat.get(1).p[3] * det3_345_045 + mat.get(1).p[4] * det3_345_035 - mat.get(1).p[5] * det3_345_034
        val det4_1345_1234 =
            mat.get(1).p[1] * det3_345_234 - mat.get(1).p[2] * det3_345_134 + mat.get(1).p[3] * det3_345_124 - mat.get(1).p[4] * det3_345_123
        val det4_1345_1235 =
            mat.get(1).p[1] * det3_345_235 - mat.get(1).p[2] * det3_345_135 + mat.get(1).p[3] * det3_345_125 - mat.get(1).p[5] * det3_345_123
        val det4_1345_1245 =
            mat.get(1).p[1] * det3_345_245 - mat.get(1).p[2] * det3_345_145 + mat.get(1).p[4] * det3_345_125 - mat.get(1).p[5] * det3_345_124
        val det4_1345_1345 =
            mat.get(1).p[1] * det3_345_345 - mat.get(1).p[3] * det3_345_145 + mat.get(1).p[4] * det3_345_135 - mat.get(1).p[5] * det3_345_134
        val det4_1345_2345 =
            mat.get(1).p[2] * det3_345_345 - mat.get(1).p[3] * det3_345_245 + mat.get(1).p[4] * det3_345_235 - mat.get(1).p[5] * det3_345_234

        // remaining 5x5 sub-determinants
        val det5_01234_01234 =
            mat.get(0).p[0] * det4_1234_1234 - mat.get(0).p[1] * det4_1234_0234 + mat.get(0).p[2] * det4_1234_0134 - mat.get(
                0
            ).p[3] * det4_1234_0124 + mat.get(0).p[4] * det4_1234_0123
        val det5_01234_01235 =
            mat.get(0).p[0] * det4_1234_1235 - mat.get(0).p[1] * det4_1234_0235 + mat.get(0).p[2] * det4_1234_0135 - mat.get(
                0
            ).p[3] * det4_1234_0125 + mat.get(0).p[5] * det4_1234_0123
        val det5_01234_01245 =
            mat.get(0).p[0] * det4_1234_1245 - mat.get(0).p[1] * det4_1234_0245 + mat.get(0).p[2] * det4_1234_0145 - mat.get(
                0
            ).p[4] * det4_1234_0125 + mat.get(0).p[5] * det4_1234_0124
        val det5_01234_01345 =
            mat.get(0).p[0] * det4_1234_1345 - mat.get(0).p[1] * det4_1234_0345 + mat.get(0).p[3] * det4_1234_0145 - mat.get(
                0
            ).p[4] * det4_1234_0135 + mat.get(0).p[5] * det4_1234_0134
        val det5_01234_02345 =
            mat.get(0).p[0] * det4_1234_2345 - mat.get(0).p[2] * det4_1234_0345 + mat.get(0).p[3] * det4_1234_0245 - mat.get(
                0
            ).p[4] * det4_1234_0235 + mat.get(0).p[5] * det4_1234_0234
        val det5_01234_12345 =
            mat.get(0).p[1] * det4_1234_2345 - mat.get(0).p[2] * det4_1234_1345 + mat.get(0).p[3] * det4_1234_1245 - mat.get(
                0
            ).p[4] * det4_1234_1235 + mat.get(0).p[5] * det4_1234_1234
        val det5_01235_01234 =
            mat.get(0).p[0] * det4_1235_1234 - mat.get(0).p[1] * det4_1235_0234 + mat.get(0).p[2] * det4_1235_0134 - mat.get(
                0
            ).p[3] * det4_1235_0124 + mat.get(0).p[4] * det4_1235_0123
        val det5_01235_01235 =
            mat.get(0).p[0] * det4_1235_1235 - mat.get(0).p[1] * det4_1235_0235 + mat.get(0).p[2] * det4_1235_0135 - mat.get(
                0
            ).p[3] * det4_1235_0125 + mat.get(0).p[5] * det4_1235_0123
        val det5_01235_01245 =
            mat.get(0).p[0] * det4_1235_1245 - mat.get(0).p[1] * det4_1235_0245 + mat.get(0).p[2] * det4_1235_0145 - mat.get(
                0
            ).p[4] * det4_1235_0125 + mat.get(0).p[5] * det4_1235_0124
        val det5_01235_01345 =
            mat.get(0).p[0] * det4_1235_1345 - mat.get(0).p[1] * det4_1235_0345 + mat.get(0).p[3] * det4_1235_0145 - mat.get(
                0
            ).p[4] * det4_1235_0135 + mat.get(0).p[5] * det4_1235_0134
        val det5_01235_02345 =
            mat.get(0).p[0] * det4_1235_2345 - mat.get(0).p[2] * det4_1235_0345 + mat.get(0).p[3] * det4_1235_0245 - mat.get(
                0
            ).p[4] * det4_1235_0235 + mat.get(0).p[5] * det4_1235_0234
        val det5_01235_12345 =
            mat.get(0).p[1] * det4_1235_2345 - mat.get(0).p[2] * det4_1235_1345 + mat.get(0).p[3] * det4_1235_1245 - mat.get(
                0
            ).p[4] * det4_1235_1235 + mat.get(0).p[5] * det4_1235_1234
        val det5_01245_01234 =
            mat.get(0).p[0] * det4_1245_1234 - mat.get(0).p[1] * det4_1245_0234 + mat.get(0).p[2] * det4_1245_0134 - mat.get(
                0
            ).p[3] * det4_1245_0124 + mat.get(0).p[4] * det4_1245_0123
        val det5_01245_01235 =
            mat.get(0).p[0] * det4_1245_1235 - mat.get(0).p[1] * det4_1245_0235 + mat.get(0).p[2] * det4_1245_0135 - mat.get(
                0
            ).p[3] * det4_1245_0125 + mat.get(0).p[5] * det4_1245_0123
        val det5_01245_01245 =
            mat.get(0).p[0] * det4_1245_1245 - mat.get(0).p[1] * det4_1245_0245 + mat.get(0).p[2] * det4_1245_0145 - mat.get(
                0
            ).p[4] * det4_1245_0125 + mat.get(0).p[5] * det4_1245_0124
        val det5_01245_01345 =
            mat.get(0).p[0] * det4_1245_1345 - mat.get(0).p[1] * det4_1245_0345 + mat.get(0).p[3] * det4_1245_0145 - mat.get(
                0
            ).p[4] * det4_1245_0135 + mat.get(0).p[5] * det4_1245_0134
        val det5_01245_02345 =
            mat.get(0).p[0] * det4_1245_2345 - mat.get(0).p[2] * det4_1245_0345 + mat.get(0).p[3] * det4_1245_0245 - mat.get(
                0
            ).p[4] * det4_1245_0235 + mat.get(0).p[5] * det4_1245_0234
        val det5_01245_12345 =
            mat.get(0).p[1] * det4_1245_2345 - mat.get(0).p[2] * det4_1245_1345 + mat.get(0).p[3] * det4_1245_1245 - mat.get(
                0
            ).p[4] * det4_1245_1235 + mat.get(0).p[5] * det4_1245_1234
        val det5_01345_01234 =
            mat.get(0).p[0] * det4_1345_1234 - mat.get(0).p[1] * det4_1345_0234 + mat.get(0).p[2] * det4_1345_0134 - mat.get(
                0
            ).p[3] * det4_1345_0124 + mat.get(0).p[4] * det4_1345_0123
        val det5_01345_01235 =
            mat.get(0).p[0] * det4_1345_1235 - mat.get(0).p[1] * det4_1345_0235 + mat.get(0).p[2] * det4_1345_0135 - mat.get(
                0
            ).p[3] * det4_1345_0125 + mat.get(0).p[5] * det4_1345_0123
        val det5_01345_01245 =
            mat.get(0).p[0] * det4_1345_1245 - mat.get(0).p[1] * det4_1345_0245 + mat.get(0).p[2] * det4_1345_0145 - mat.get(
                0
            ).p[4] * det4_1345_0125 + mat.get(0).p[5] * det4_1345_0124
        val det5_01345_01345 =
            mat.get(0).p[0] * det4_1345_1345 - mat.get(0).p[1] * det4_1345_0345 + mat.get(0).p[3] * det4_1345_0145 - mat.get(
                0
            ).p[4] * det4_1345_0135 + mat.get(0).p[5] * det4_1345_0134
        val det5_01345_02345 =
            mat.get(0).p[0] * det4_1345_2345 - mat.get(0).p[2] * det4_1345_0345 + mat.get(0).p[3] * det4_1345_0245 - mat.get(
                0
            ).p[4] * det4_1345_0235 + mat.get(0).p[5] * det4_1345_0234
        val det5_01345_12345 =
            mat.get(0).p[1] * det4_1345_2345 - mat.get(0).p[2] * det4_1345_1345 + mat.get(0).p[3] * det4_1345_1245 - mat.get(
                0
            ).p[4] * det4_1345_1235 + mat.get(0).p[5] * det4_1345_1234
        val det5_02345_01234 =
            mat.get(0).p[0] * det4_2345_1234 - mat.get(0).p[1] * det4_2345_0234 + mat.get(0).p[2] * det4_2345_0134 - mat.get(
                0
            ).p[3] * det4_2345_0124 + mat.get(0).p[4] * det4_2345_0123
        val det5_02345_01235 =
            mat.get(0).p[0] * det4_2345_1235 - mat.get(0).p[1] * det4_2345_0235 + mat.get(0).p[2] * det4_2345_0135 - mat.get(
                0
            ).p[3] * det4_2345_0125 + mat.get(0).p[5] * det4_2345_0123
        val det5_02345_01245 =
            mat.get(0).p[0] * det4_2345_1245 - mat.get(0).p[1] * det4_2345_0245 + mat.get(0).p[2] * det4_2345_0145 - mat.get(
                0
            ).p[4] * det4_2345_0125 + mat.get(0).p[5] * det4_2345_0124
        val det5_02345_01345 =
            mat.get(0).p[0] * det4_2345_1345 - mat.get(0).p[1] * det4_2345_0345 + mat.get(0).p[3] * det4_2345_0145 - mat.get(
                0
            ).p[4] * det4_2345_0135 + mat.get(0).p[5] * det4_2345_0134
        val det5_02345_02345 =
            mat.get(0).p[0] * det4_2345_2345 - mat.get(0).p[2] * det4_2345_0345 + mat.get(0).p[3] * det4_2345_0245 - mat.get(
                0
            ).p[4] * det4_2345_0235 + mat.get(0).p[5] * det4_2345_0234
        val det5_02345_12345 =
            mat.get(0).p[1] * det4_2345_2345 - mat.get(0).p[2] * det4_2345_1345 + mat.get(0).p[3] * det4_2345_1245 - mat.get(
                0
            ).p[4] * det4_2345_1235 + mat.get(0).p[5] * det4_2345_1234
        mat.get(0).p[0] = (det5_12345_12345 * invDet).toFloat()
        mat.get(0).p[1] = (-det5_02345_12345 * invDet).toFloat()
        mat.get(0).p[2] = (det5_01345_12345 * invDet).toFloat()
        mat.get(0).p[3] = (-det5_01245_12345 * invDet).toFloat()
        mat.get(0).p[4] = (det5_01235_12345 * invDet).toFloat()
        mat.get(0).p[5] = (-det5_01234_12345 * invDet).toFloat()
        mat.get(1).p[0] = (-det5_12345_02345 * invDet).toFloat()
        mat.get(1).p[1] = (det5_02345_02345 * invDet).toFloat()
        mat.get(1).p[2] = (-det5_01345_02345 * invDet).toFloat()
        mat.get(1).p[3] = (det5_01245_02345 * invDet).toFloat()
        mat.get(1).p[4] = (-det5_01235_02345 * invDet).toFloat()
        mat.get(1).p[5] = (det5_01234_02345 * invDet).toFloat()
        mat.get(2).p[0] = (det5_12345_01345 * invDet).toFloat()
        mat.get(2).p[1] = (-det5_02345_01345 * invDet).toFloat()
        mat.get(2).p[2] = (det5_01345_01345 * invDet).toFloat()
        mat.get(2).p[3] = (-det5_01245_01345 * invDet).toFloat()
        mat.get(2).p[4] = (det5_01235_01345 * invDet).toFloat()
        mat.get(2).p[5] = (-det5_01234_01345 * invDet).toFloat()
        mat.get(3).p[0] = (-det5_12345_01245 * invDet).toFloat()
        mat.get(3).p[1] = (det5_02345_01245 * invDet).toFloat()
        mat.get(3).p[2] = (-det5_01345_01245 * invDet).toFloat()
        mat.get(3).p[3] = (det5_01245_01245 * invDet).toFloat()
        mat.get(3).p[4] = (-det5_01235_01245 * invDet).toFloat()
        mat.get(3).p[5] = (det5_01234_01245 * invDet).toFloat()
        mat.get(4).p[0] = (det5_12345_01235 * invDet).toFloat()
        mat.get(4).p[1] = (-det5_02345_01235 * invDet).toFloat()
        mat.get(4).p[2] = (det5_01345_01235 * invDet).toFloat()
        mat.get(4).p[3] = (-det5_01245_01235 * invDet).toFloat()
        mat.get(4).p[4] = (det5_01235_01235 * invDet).toFloat()
        mat.get(4).p[5] = (-det5_01234_01235 * invDet).toFloat()
        mat.get(5).p[0] = (-det5_12345_01234 * invDet).toFloat()
        mat.get(5).p[1] = (det5_02345_01234 * invDet).toFloat()
        mat.get(5).p[2] = (-det5_01345_01234 * invDet).toFloat()
        mat.get(5).p[3] = (det5_01245_01234 * invDet).toFloat()
        mat.get(5).p[4] = (-det5_01235_01234 * invDet).toFloat()
        mat.get(5).p[5] = (det5_01234_01234 * invDet).toFloat()
        return true
    }

    fun InverseFast(): idMat6? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat6
        invMat = this
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
//    #else
        // 6*27+2*30 = 222 multiplications
        //		2*1  =	 2 divisions
        val r0: Array<idVec3?> = idVec3.Companion.generateArray(3)
        val r1: Array<idVec3?> = idVec3.Companion.generateArray(3)
        val r2: Array<idVec3?> = idVec3.Companion.generateArray(3)
        val r3: Array<idVec3?> = idVec3.Companion.generateArray(3)
        val c0: Float
        val c1: Float
        val c2: Float
        var det: Float
        var invDet: Float
        val mat = reinterpret_cast()

        // r0 = m0.Inverse();
        c0 = mat.get(1 * 6 + 1) * mat.get(2 * 6 + 2) - mat.get(1 * 6 + 2) * mat.get(2 * 6 + 1)
        c1 = mat.get(1 * 6 + 2) * mat.get(2 * 6 + 0) - mat.get(1 * 6 + 0) * mat.get(2 * 6 + 2)
        c2 = mat.get(1 * 6 + 0) * mat.get(2 * 6 + 1) - mat.get(1 * 6 + 1) * mat.get(2 * 6 + 0)
        det = mat.get(0 * 6 + 0) * c0 + mat.get(0 * 6 + 1) * c1 + mat.get(0 * 6 + 2) * c2
        if (Math.abs(det) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r0[0].x = c0 * invDet
        r0[0].y = (mat.get(0 * 6 + 2) * mat.get(2 * 6 + 1) - mat.get(0 * 6 + 1) * mat.get(2 * 6 + 2)) * invDet
        r0[0].z = (mat.get(0 * 6 + 1) * mat.get(1 * 6 + 2) - mat.get(0 * 6 + 2) * mat.get(1 * 6 + 1)) * invDet
        r0[1].x = c1 * invDet
        r0[1].y = (mat.get(0 * 6 + 0) * mat.get(2 * 6 + 2) - mat.get(0 * 6 + 2) * mat.get(2 * 6 + 0)) * invDet
        r0[1].z = (mat.get(0 * 6 + 2) * mat.get(1 * 6 + 0) - mat.get(0 * 6 + 0) * mat.get(1 * 6 + 2)) * invDet
        r0[2].x = c2 * invDet
        r0[2].y = (mat.get(0 * 6 + 1) * mat.get(2 * 6 + 0) - mat.get(0 * 6 + 0) * mat.get(2 * 6 + 1)) * invDet
        r0[2].z = (mat.get(0 * 6 + 0) * mat.get(1 * 6 + 1) - mat.get(0 * 6 + 1) * mat.get(1 * 6 + 0)) * invDet

        // r1 = r0 * m1;
        r1[0].x = r0[0].x * mat.get(0 * 6 + 3) + r0[0].y * mat.get(1 * 6 + 3) + r0[0].z * mat.get(2 * 6 + 3)
        r1[0].y = r0[0].x * mat.get(0 * 6 + 4) + r0[0].y * mat.get(1 * 6 + 4) + r0[0].z * mat.get(2 * 6 + 4)
        r1[0].z = r0[0].x * mat.get(0 * 6 + 5) + r0[0].y * mat.get(1 * 6 + 5) + r0[0].z * mat.get(2 * 6 + 5)
        r1[1].x = r0[1].x * mat.get(0 * 6 + 3) + r0[1].y * mat.get(1 * 6 + 3) + r0[1].z * mat.get(2 * 6 + 3)
        r1[1].y = r0[1].x * mat.get(0 * 6 + 4) + r0[1].y * mat.get(1 * 6 + 4) + r0[1].z * mat.get(2 * 6 + 4)
        r1[1].z = r0[1].x * mat.get(0 * 6 + 5) + r0[1].y * mat.get(1 * 6 + 5) + r0[1].z * mat.get(2 * 6 + 5)
        r1[2].x = r0[2].x * mat.get(0 * 6 + 3) + r0[2].y * mat.get(1 * 6 + 3) + r0[2].z * mat.get(2 * 6 + 3)
        r1[2].y = r0[2].x * mat.get(0 * 6 + 4) + r0[2].y * mat.get(1 * 6 + 4) + r0[2].z * mat.get(2 * 6 + 4)
        r1[2].z = r0[2].x * mat.get(0 * 6 + 5) + r0[2].y * mat.get(1 * 6 + 5) + r0[2].z * mat.get(2 * 6 + 5)

        // r2 = m2 * r1;
        r2[0].x = mat.get(3 * 6 + 0) * r1[0].x + mat.get(3 * 6 + 1) * r1[1].x + mat.get(3 * 6 + 2) * r1[2].x
        r2[0].y = mat.get(3 * 6 + 0) * r1[0].y + mat.get(3 * 6 + 1) * r1[1].y + mat.get(3 * 6 + 2) * r1[2].y
        r2[0].z = mat.get(3 * 6 + 0) * r1[0].z + mat.get(3 * 6 + 1) * r1[1].z + mat.get(3 * 6 + 2) * r1[2].z
        r2[1].x = mat.get(4 * 6 + 0) * r1[0].x + mat.get(4 * 6 + 1) * r1[1].x + mat.get(4 * 6 + 2) * r1[2].x
        r2[1].y = mat.get(4 * 6 + 0) * r1[0].y + mat.get(4 * 6 + 1) * r1[1].y + mat.get(4 * 6 + 2) * r1[2].y
        r2[1].z = mat.get(4 * 6 + 0) * r1[0].z + mat.get(4 * 6 + 1) * r1[1].z + mat.get(4 * 6 + 2) * r1[2].z
        r2[2].x = mat.get(5 * 6 + 0) * r1[0].x + mat.get(5 * 6 + 1) * r1[1].x + mat.get(5 * 6 + 2) * r1[2].x
        r2[2].y = mat.get(5 * 6 + 0) * r1[0].y + mat.get(5 * 6 + 1) * r1[1].y + mat.get(5 * 6 + 2) * r1[2].y
        r2[2].z = mat.get(5 * 6 + 0) * r1[0].z + mat.get(5 * 6 + 1) * r1[1].z + mat.get(5 * 6 + 2) * r1[2].z

        // r3 = r2 - m3;
        r3[0].x = r2[0].x - mat.get(3 * 6 + 3)
        r3[0].y = r2[0].y - mat.get(3 * 6 + 4)
        r3[0].z = r2[0].z - mat.get(3 * 6 + 5)
        r3[1].x = r2[1].x - mat.get(4 * 6 + 3)
        r3[1].y = r2[1].y - mat.get(4 * 6 + 4)
        r3[1].z = r2[1].z - mat.get(4 * 6 + 5)
        r3[2].x = r2[2].x - mat.get(5 * 6 + 3)
        r3[2].y = r2[2].y - mat.get(5 * 6 + 4)
        r3[2].z = r2[2].z - mat.get(5 * 6 + 5)

        // r3.InverseSelf();
        r2[0].x = r3[1].y * r3[2].z - r3[1].z * r3[2].y
        r2[1].x = r3[1].z * r3[2].x - r3[1].x * r3[2].z
        r2[2].x = r3[1].x * r3[2].y - r3[1].y * r3[2].x
        det = r3[0].x * r2[0].x + r3[0].y * r2[1].x + r3[0].z * r2[2].x
        if (Math.abs(det) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        r2[0].y = r3[0].z * r3[2].y - r3[0].y * r3[2].z
        r2[0].z = r3[0].y * r3[1].z - r3[0].z * r3[1].y
        r2[1].y = r3[0].x * r3[2].z - r3[0].z * r3[2].x
        r2[1].z = r3[0].z * r3[1].x - r3[0].x * r3[1].z
        r2[2].y = r3[0].y * r3[2].x - r3[0].x * r3[2].y
        r2[2].z = r3[0].x * r3[1].y - r3[0].y * r3[1].x
        r3[0].x = r2[0].x * invDet
        r3[0].y = r2[0].y * invDet
        r3[0].z = r2[0].z * invDet
        r3[1].x = r2[1].x * invDet
        r3[1].y = r2[1].y * invDet
        r3[1].z = r2[1].z * invDet
        r3[2].x = r2[2].x * invDet
        r3[2].y = r2[2].y * invDet
        r3[2].z = r2[2].z * invDet

        // r2 = m2 * r0;
        r2[0].x = mat.get(3 * 6 + 0) * r0[0].x + mat.get(3 * 6 + 1) * r0[1].x + mat.get(3 * 6 + 2) * r0[2].x
        r2[0].y = mat.get(3 * 6 + 0) * r0[0].y + mat.get(3 * 6 + 1) * r0[1].y + mat.get(3 * 6 + 2) * r0[2].y
        r2[0].z = mat.get(3 * 6 + 0) * r0[0].z + mat.get(3 * 6 + 1) * r0[1].z + mat.get(3 * 6 + 2) * r0[2].z
        r2[1].x = mat.get(4 * 6 + 0) * r0[0].x + mat.get(4 * 6 + 1) * r0[1].x + mat.get(4 * 6 + 2) * r0[2].x
        r2[1].y = mat.get(4 * 6 + 0) * r0[0].y + mat.get(4 * 6 + 1) * r0[1].y + mat.get(4 * 6 + 2) * r0[2].y
        r2[1].z = mat.get(4 * 6 + 0) * r0[0].z + mat.get(4 * 6 + 1) * r0[1].z + mat.get(4 * 6 + 2) * r0[2].z
        r2[2].x = mat.get(5 * 6 + 0) * r0[0].x + mat.get(5 * 6 + 1) * r0[1].x + mat.get(5 * 6 + 2) * r0[2].x
        r2[2].y = mat.get(5 * 6 + 0) * r0[0].y + mat.get(5 * 6 + 1) * r0[1].y + mat.get(5 * 6 + 2) * r0[2].y
        r2[2].z = mat.get(5 * 6 + 0) * r0[0].z + mat.get(5 * 6 + 1) * r0[1].z + mat.get(5 * 6 + 2) * r0[2].z

        // m2 = r3 * r2;
        this.mat.get(3).p[0] = r3[0].x * r2[0].x + r3[0].y * r2[1].x + r3[0].z * r2[2].x
        this.mat.get(3).p[1] = r3[0].x * r2[0].y + r3[0].y * r2[1].y + r3[0].z * r2[2].y
        this.mat.get(3).p[2] = r3[0].x * r2[0].z + r3[0].y * r2[1].z + r3[0].z * r2[2].z
        this.mat.get(4).p[0] = r3[1].x * r2[0].x + r3[1].y * r2[1].x + r3[1].z * r2[2].x
        this.mat.get(4).p[1] = r3[1].x * r2[0].y + r3[1].y * r2[1].y + r3[1].z * r2[2].y
        this.mat.get(4).p[2] = r3[1].x * r2[0].z + r3[1].y * r2[1].z + r3[1].z * r2[2].z
        this.mat.get(5).p[0] = r3[2].x * r2[0].x + r3[2].y * r2[1].x + r3[2].z * r2[2].x
        this.mat.get(5).p[1] = r3[2].x * r2[0].y + r3[2].y * r2[1].y + r3[2].z * r2[2].y
        this.mat.get(5).p[2] = r3[2].x * r2[0].z + r3[2].y * r2[1].z + r3[2].z * r2[2].z

        // m0 = r0 - r1 * m2;
        this.mat.get(0).p[0] =
            r0[0].x - r1[0].x * this.mat.get(3).p[0] - r1[0].y * this.mat.get(4).p[0] - r1[0].z * this.mat.get(5).p[0]
        this.mat.get(0).p[1] =
            r0[0].y - r1[0].x * this.mat.get(3).p[1] - r1[0].y * this.mat.get(4).p[1] - r1[0].z * this.mat.get(5).p[1]
        this.mat.get(0).p[2] =
            r0[0].z - r1[0].x * this.mat.get(3).p[2] - r1[0].y * this.mat.get(4).p[2] - r1[0].z * this.mat.get(5).p[2]
        this.mat.get(1).p[0] =
            r0[1].x - r1[1].x * this.mat.get(3).p[0] - r1[1].y * this.mat.get(4).p[0] - r1[1].z * this.mat.get(5).p[0]
        this.mat.get(1).p[1] =
            r0[1].y - r1[1].x * this.mat.get(3).p[1] - r1[1].y * this.mat.get(4).p[1] - r1[1].z * this.mat.get(5).p[1]
        this.mat.get(1).p[2] =
            r0[1].z - r1[1].x * this.mat.get(3).p[2] - r1[1].y * this.mat.get(4).p[2] - r1[1].z * this.mat.get(5).p[2]
        this.mat.get(2).p[0] =
            r0[2].x - r1[2].x * this.mat.get(3).p[0] - r1[2].y * this.mat.get(4).p[0] - r1[2].z * this.mat.get(5).p[0]
        this.mat.get(2).p[1] =
            r0[2].y - r1[2].x * this.mat.get(3).p[1] - r1[2].y * this.mat.get(4).p[1] - r1[2].z * this.mat.get(5).p[1]
        this.mat.get(2).p[2] =
            r0[2].z - r1[2].x * this.mat.get(3).p[2] - r1[2].y * this.mat.get(4).p[2] - r1[2].z * this.mat.get(5).p[2]

        // m1 = r1 * r3;
        this.mat.get(0).p[3] = r1[0].x * r3[0].x + r1[0].y * r3[1].x + r1[0].z * r3[2].x
        this.mat.get(0).p[4] = r1[0].x * r3[0].y + r1[0].y * r3[1].y + r1[0].z * r3[2].y
        this.mat.get(0).p[5] = r1[0].x * r3[0].z + r1[0].y * r3[1].z + r1[0].z * r3[2].z
        this.mat.get(1).p[3] = r1[1].x * r3[0].x + r1[1].y * r3[1].x + r1[1].z * r3[2].x
        this.mat.get(1).p[4] = r1[1].x * r3[0].y + r1[1].y * r3[1].y + r1[1].z * r3[2].y
        this.mat.get(1).p[5] = r1[1].x * r3[0].z + r1[1].y * r3[1].z + r1[1].z * r3[2].z
        this.mat.get(2).p[3] = r1[2].x * r3[0].x + r1[2].y * r3[1].x + r1[2].z * r3[2].x
        this.mat.get(2).p[4] = r1[2].x * r3[0].y + r1[2].y * r3[1].y + r1[2].z * r3[2].y
        this.mat.get(2).p[5] = r1[2].x * r3[0].z + r1[2].y * r3[1].z + r1[2].z * r3[2].z

        // m3 = -r3;
        this.mat.get(3).p[3] = -r3[0].x
        this.mat.get(3).p[4] = -r3[0].y
        this.mat.get(3).p[5] = -r3[0].z
        this.mat.get(4).p[3] = -r3[1].x
        this.mat.get(4).p[4] = -r3[1].y
        this.mat.get(4).p[5] = -r3[1].z
        this.mat.get(5).p[3] = -r3[2].x
        this.mat.get(5).p[4] = -r3[2].y
        this.mat.get(5).p[5] = -r3[2].z
        return true
        //#endif
    }

    fun GetDimension(): Int {
        return 36
    }

    //public	const float *	ToFloatPtr( void ) const;
    //public	float *			ToFloatPtr( void );
    //public	const char *	ToString( int precision = 2 ) const;
    private fun oSet(mat6: idMat6?) {
        mat.get(0).oSet(mat6.mat[0])
        mat.get(1).oSet(mat6.mat[1])
        mat.get(2).oSet(mat6.mat[2])
        mat.get(3).oSet(mat6.mat[3])
        mat.get(4).oSet(mat6.mat[4])
        mat.get(5).oSet(mat6.mat[5])
    }

    fun reinterpret_cast(): FloatArray? {
        val size = 6
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            System.arraycopy(mat.get(x).p, 0, temp, x * 6 + 0, size)
        }
        return temp
    }

    companion object {
        private val mat6_identity: idMat6? = idMat6(
            idVec6(1, 0, 0, 0, 0, 0),
            idVec6(0, 1, 0, 0, 0, 0),
            idVec6(0, 0, 1, 0, 0, 0),
            idVec6(0, 0, 0, 1, 0, 0),
            idVec6(0, 0, 0, 0, 1, 0),
            idVec6(0, 0, 0, 0, 0, 1)
        )
        private val mat6_zero: idMat6? = idMat6(
            idVec6(0, 0, 0, 0, 0, 0),
            idVec6(0, 0, 0, 0, 0, 0),
            idVec6(0, 0, 0, 0, 0, 0),
            idVec6(0, 0, 0, 0, 0, 0),
            idVec6(0, 0, 0, 0, 0, 0),
            idVec6(0, 0, 0, 0, 0, 0)
        )

        fun getMat6_zero(): idMat6? {
            return idMat6(idMat6.Companion.mat6_zero)
        }

        fun getMat6_identity(): idMat6? {
            return idMat6(idMat6.Companion.mat6_identity)
        }

        //public	friend idMat6	operator*( const float a, const idMat6 &mat );
        fun oMultiply(a: Float, mat: idMat6?): idMat6? {
            return mat.oMultiply(a)
        }

        //public	idVec6			operator*( const idVec6 &vec ) const;
        fun oMultiply(vec: idVec6?, mat: idMat6?): idVec6? {
            return mat.oMultiply(vec)
        }

        //public	idMat6			operator*( const idMat6 &a ) const;
        fun oMulSet(vec: idVec6?, mat: idMat6?): idVec6? {
            var vec = vec
            vec = mat.oMultiply(vec)
            return vec
        }
    }
}