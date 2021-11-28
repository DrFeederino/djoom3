package neo.idlib.math.Matrix

import neo.idlib.Text.Str.idStr
import neo.idlib.math.Vector.idVec2
import java.util.*

/**
 * 1x1 is too complex, so we'll skip to 2x2.
 */
//===============================================================
//
//	idMat2 - 2x2 matrix
//
//===============================================================
class idMat2 {
    private val mat: Array<idVec2?>? = arrayOf(idVec2(), idVec2())

    constructor()
    constructor(x: idVec2?, y: idVec2?) {
        mat.get(0).x = x.x
        mat.get(0).y = x.y
        mat.get(1).x = y.x
        mat.get(1).y = y.y
    }

    constructor(xx: Float, xy: Float, yx: Float, yy: Float) {
        mat.get(0).x = xx
        mat.get(0).y = xy
        mat.get(1).x = yx
        mat.get(1).y = yy
    }

    constructor(src: Array<FloatArray?>?) {
//	memcpy( mat, src, 2 * 2 * sizeof( float ) );
        mat.get(0) = idVec2(src.get(0).get(0), src.get(0).get(1))
        mat.get(1) = idVec2(src.get(1).get(0), src.get(1).get(1))
    }

    constructor(m: idMat2?) : this(m.mat[0], m.mat[1])

    //public	const idVec2 &	operator[]( int index ) const;
    //public	idVec2 &		operator[]( int index );
    fun oGet(index: Int): idVec2? {
        return mat.get(index)
    }

    //public	idMat2			operator-() const;
    fun oNegative(): idMat2? {
        return idMat2(
            -mat.get(0).x, -mat.get(0).y,
            -mat.get(1).x, -mat.get(1).y
        )
    }

    //public	idMat2			operator*( const float a ) const;
    fun oMultiply(a: Float): idMat2? {
        return idMat2(
            mat.get(0).x * a, mat.get(0).y * a,
            mat.get(1).x * a, mat.get(1).y * a
        )
    }

    //public	idVec2			operator*( const idVec2 &vec ) const;
    fun oMultiply(vec: idVec2?): idVec2? {
        return idVec2(
            mat.get(0).x * vec.x + mat.get(0).y * vec.y,
            mat.get(1).x * vec.x + mat.get(1).y * vec.y
        )
    }

    //public	idMat2			operator*( const idMat2 &a ) const;
    fun oMultiply(a: idMat2?): idMat2? {
        return idMat2(
            mat.get(0).x * a.mat[0].x + mat.get(0).y * a.mat[1].x,
            mat.get(0).x * a.mat[0].y + mat.get(0).y * a.mat[1].y,
            mat.get(1).x * a.mat[0].x + mat.get(1).y * a.mat[1].x,
            mat.get(1).x * a.mat[0].y + mat.get(1).y * a.mat[1].y
        )
    }

    //public	idMat2			operator+( const idMat2 &a ) const;
    fun oPlus(a: idMat2?): idMat2? {
        return idMat2(
            mat.get(0).x + a.mat[0].x, mat.get(0).y + a.mat[0].y,
            mat.get(1).x + a.mat[1].x, mat.get(1).y + a.mat[1].y
        )
    }

    fun oMinus(a: idMat2?): idMat2? {
        return idMat2(
            mat.get(0).x - a.mat[0].x, mat.get(0).y - a.mat[0].y,
            mat.get(1).x - a.mat[1].x, mat.get(1).y - a.mat[1].y
        )
    }

    //public	idMat2 &		operator*=( const float a );
    fun oMulSet(a: Float): idMat2? {
        mat.get(0).x *= a
        mat.get(0).y *= a
        mat.get(1).x *= a
        mat.get(1).y *= a
        return this
    }

    //public	idMat2 &		operator*=( const idMat2 &a );
    fun oMulSet(a: idMat2?): idMat2? {
        var x: Float
        var y: Float
        x = mat.get(0).x
        y = mat.get(0).y
        mat.get(0).x = x * a.mat[0].x + y * a.mat[1].x
        mat.get(0).y = x * a.mat[0].y + y * a.mat[1].y
        x = mat.get(1).x
        y = mat.get(1).y
        mat.get(1).x = x * a.mat[0].x + y * a.mat[1].x
        mat.get(1).y = x * a.mat[0].y + y * a.mat[1].y
        return this
    }

    //public	idMat2 &		operator+=( const idMat2 &a );
    fun oPluSet(a: idMat2?): idMat2? {
        mat.get(0).x += a.mat[0].x
        mat.get(0).y += a.mat[0].y
        mat.get(1).x += a.mat[1].x
        mat.get(1).y += a.mat[1].y
        return this
    }

    //public	idMat2 &		operator-=( const idMat2 &a );
    fun oMinSet(a: idMat2?): idMat2? {
        mat.get(0).x -= a.mat[0].x
        mat.get(0).y -= a.mat[0].y
        mat.get(1).x -= a.mat[1].x
        mat.get(1).y -= a.mat[1].y
        return this
    }

    //public	friend idMat2	operator*( const float a, const idMat2 &mat );
    //public	friend idVec2	operator*( const idVec2 &vec, const idMat2 &mat );
    //public	friend idVec2 &	operator*=( idVec2 &vec, const idMat2 &mat );
    //public	bool			Compare( const idMat2 &a ) const;						// exact compare, no epsilon
    fun Compare(a: idMat2?): Boolean { // exact compare, no epsilon
        return (mat.get(0).Compare(a.mat[0])
                && mat.get(1).Compare(a.mat[1]))
    }

    //public	bool			Compare( const idMat2 &a, const float epsilon ) const;	// compare with epsilon
    fun Compare(a: idMat2?, epsilon: Float): Boolean { // compare with epsilon
        return (mat.get(0).Compare(a.mat[0], epsilon)
                && mat.get(1).Compare(a.mat[1], epsilon))
    }

    //public	bool			operator==( const idMat2 &a ) const;					// exact compare, no epsilon
    //public	bool			operator!=( const idMat2 &a ) const;					// exact compare, no epsilon
    override fun hashCode(): Int {
        var hash = 7
        hash = 83 * hash + Arrays.deepHashCode(mat)
        return hash
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (javaClass != obj.javaClass) {
            return false
        }
        val other = obj as idMat2?
        return Arrays.deepEquals(mat, other.mat)
    }

    fun Zero() {
        mat.get(0).Zero()
        mat.get(1).Zero()
    }

    fun Identity() {
        mat.get(0) = idMat2.Companion.getMat2_identity().mat.get(0)
        mat.get(1) = idMat2.Companion.getMat2_identity().mat.get(1)
    }

    @JvmOverloads
    fun IsIdentity(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return Compare(idMat2.Companion.getMat2_identity(), epsilon)
    }

    @JvmOverloads
    fun IsSymmetric(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return Math.abs(mat.get(0).y - mat.get(1).x) < epsilon
    }

    @JvmOverloads
    fun IsDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        return (Math.abs(mat.get(0).y) <= epsilon
                && Math.abs(mat.get(1).x) <= epsilon)
    }

    fun Trace(): Float {
        return mat.get(0).x + mat.get(1).y
    }

    fun Determinant(): Float {
        return mat.get(0).x * mat.get(1).y - mat.get(0).y * mat.get(1).x
    }

    fun Transpose(): idMat2? { // returns transpose
        return idMat2(
            mat.get(0).x, mat.get(1).x,
            mat.get(0).y, mat.get(1).y
        )
    }

    fun TransposeSelf(): idMat2? {
        val tmp: Float
        tmp = mat.get(0).x
        mat.get(0).y = mat.get(1).x
        mat.get(1).x = tmp
        return this
    }

    fun Inverse(): idMat2? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat2
        invMat = this
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        // 2+4 = 6 multiplications
        //		 1 division
//	double det, invDet, a;
        val det: Double
        val invDet: Double
        val a: Double
        det = Determinant().toDouble() //	det = mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0];
        if (Math.abs(det.toFloat()) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = mat.get(0).x.toDouble()
        mat.get(0).x = (mat.get(1).y * invDet).toFloat()
        mat.get(0).y = (-mat.get(0).y * invDet).toFloat()
        mat.get(1).x = (-mat.get(1).x * invDet).toFloat()
        mat.get(1).y = (a * invDet).toFloat()
        return true
    }

    fun InverseFast(): idMat2? { // returns the inverse ( m * m.Inverse() = identity )
        val invMat: idMat2
        invMat = this
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
//#if 1
        // 2+4 = 6 multiplications
        //		 1 division
        val det: Double
        val invDet: Double
        val a: Double
        det = Determinant().toDouble() //	det = mat[0][0] * mat[1][1] - mat[0][1] * mat[1][0];
        if (Math.abs(det.toFloat()) < idMat0.MATRIX_INVERSE_EPSILON) {
            return false
        }
        invDet = 1.0f / det
        a = mat.get(0).x.toDouble()
        mat.get(0).x = (mat.get(1).y * invDet).toFloat()
        mat.get(0).y = (-mat.get(0).y * invDet).toFloat()
        mat.get(1).x = (-mat.get(1).x * invDet).toFloat()
        mat.get(1).y = (a * invDet).toFloat()
        return true
    }

    fun GetDimension(): Int {
        return 4
    }

    @Deprecated("")
    fun ToFloatPtr(): FloatArray? {
        return mat.get(0).ToFloatPtr()
    }

    //public	float *			ToFloatPtr( void );
    @JvmOverloads
    fun ToString(precision: Int = 2): String? {
        return idStr.Companion.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    fun reinterpret_cast(): FloatArray? {
        val size = 2
        val temp = FloatArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                temp[x * size + y] = mat.get(x).oGet(y)
            }
        }
        return temp
    }

    companion object {
        private val mat2_identity: idMat2? = idMat2(idVec2(1, 0), idVec2(0, 1))
        private val mat2_zero: idMat2? = idMat2(idVec2(0, 0), idVec2(0, 0))
        fun getMat2_zero(): idMat2? {
            return idMat2(idMat2.Companion.mat2_zero)
        }

        fun getMat2_identity(): idMat2? {
            return idMat2(idMat2.Companion.mat2_identity)
        }
    }
}