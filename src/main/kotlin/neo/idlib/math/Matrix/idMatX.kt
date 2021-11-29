package neo.idlib.math.Matrix

import neo.TempDump
import neo.idlib.Lib
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idSwap
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec5
import neo.idlib.math.Vector.idVec6
import neo.idlib.math.Vector.idVecX
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.abs

class idMatX {
    private var alloced // floats allocated, if -1 then mat points to data set with SetData
            = 0
    private var mat // memory the matrix is stored
            : FloatArray = FloatArray(0)
    private var numColumns // number of columns
            = 0

    //
    private var numRows // number of rows
            = 0

    //
    //
    constructor() {
        alloced = 0
        numColumns = alloced
        numRows = numColumns
    }

    constructor(rows: Int, columns: Int) {
        alloced = 0
        numColumns = alloced
        numRows = numColumns
        SetSize(rows, columns)
    }

    constructor(rows: Int, columns: Int, src: FloatArray) {
        alloced = 0
        numColumns = alloced
        numRows = numColumns
        SetData(rows, columns, src)
    }

    //#define MATX_SIMD
    constructor(matX: idMatX) {
        this.oSet(matX)
    }

    fun MATX_CLEAREND() {
        var s = numRows * numColumns
        while (s < s + 3 and 3.inv()) {
            mat[s++] = 0.0f
        }
    }

    fun Set(rows: Int, columns: Int, src: FloatArray) {
        SetSize(rows, columns)
        //	memcpy( this->mat, src, rows * columns * sizeof( float ) );
        System.arraycopy(src, 0, mat, 0, src.size)
    }

    fun Set(m1: idMat3, m2: idMat3) {
        var j: Int
        SetSize(3, 6)
        var i = 0
        while (i < 3) {
            j = 0
            while (j < 3) {
                mat[(i + 0) * numColumns + (j + 0)] = m1.mat[i].oGet(j)
                mat[(i + 0) * numColumns + (j + 3)] = m2.mat[i].oGet(j)
                j++
            }
            i++
        }
    }

    //public	idMatX			operator*( const float a ) const;
    fun Set(m1: idMat3, m2: idMat3, m3: idMat3, m4: idMat3) {
        var j: Int
        SetSize(6, 6)
        var i = 0
        while (i < 3) {
            j = 0
            while (j < 3) {
                mat[(i + 0) * numColumns + (j + 0)] = m1.mat[i].oGet(j)
                mat[(i + 0) * numColumns + (j + 3)] = m2.mat[i].oGet(j)
                mat[(i + 3) * numColumns + (j + 0)] = m3.mat[i].oGet(j)
                mat[(i + 3) * numColumns + (j + 3)] = m4.mat[i].oGet(j)
                j++
            }
            i++
        }
    }

    //public	idVecX			operator*( const idVecX &vec ) const;
    //public	const float *	operator[]( int index ) const;
    //public	float *			operator[]( int index );
    @Deprecated("")
    fun oGet(index: Int): FloatArray { ////TODO:by sub array by reference
        return mat.copyOfRange(index * numColumns, mat.size)
    }

    //public	idMatX			operator*( const idMatX &a ) const;
    //public	idMatX &		operator=( const idMatX &a );
    fun oSet(a: idMatX): idMatX {
        SetSize(a.numRows, a.numColumns)
        //#ifdef MATX_SIMD
//	SIMDProcessor->Copy16( mat, a.mat, a.numRows * a.numColumns );
//#else
//	memcpy( mat, a.mat, a.numRows * a.numColumns * sizeof( float ) );
//#endif
        tempIndex = 0
        System.arraycopy(a.mat, 0, mat, 0, a.numRows * a.numColumns)
        return this
    }

    //public	idMatX			operator+( const idMatX &a ) const;
    fun oMultiply(a: Float): idMatX {
        val m = idMatX()
        m.SetTempSize(numRows, numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.Mul16(m.mat, mat, a, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                m.mat[i] = mat[i] * a
                i++
            }
        }
        return m
    }

    //public	idMatX			operator-( const idMatX &a ) const;
    fun oMultiply(vec: idVecX): idVecX {
        val dst = idVecX()
        assert(numColumns == vec.GetSize())
        dst.SetTempSize(numRows)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyVecX(dst, this, vec)
        } else {
            Multiply(dst, vec)
        }
        return dst
    }

    //public	idMatX &		operator*=( const float a );
    fun oMultiply(a: idMatX): idMatX {
        val dst = idMatX()
        assert(numColumns == a.numRows)
        dst.SetTempSize(numRows, a.numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyMatX(dst, this, a)
        } else {
            Multiply(dst, a)
        }
        return dst
    }

    //public	idMatX &		operator*=( const idMatX &a );
    operator fun plus(a: idMatX): idMatX {
        val m = idMatX()
        assert(numRows == a.numRows && numColumns == a.numColumns)
        m.SetTempSize(numRows, numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.Add16(m.mat, mat, a.mat, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                m.mat[i] = mat[i] + a.mat[i]
                i++
            }
        }
        return m
    }

    //public	idMatX &		operator+=( const idMatX &a );
    operator fun minus(a: idMatX): idMatX {
        val m = idMatX()
        assert(numRows == a.numRows && numColumns == a.numColumns)
        m.SetTempSize(numRows, numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.Sub16(m.mat, mat, a.mat, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                m.mat[i] = mat[i] - a.mat[i]
                i++
            }
        }
        return m
    }

    fun timesAssign(a: Float): idMatX {
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MulAssign16(mat, a, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                mat[i] *= a
                i++
            }
        }
        tempIndex = 0
        return this
    }

    fun timesAssign(a: idMatX): idMatX {
        this.oSet(this.oMultiply(a))
        tempIndex = 0
        return this
    }

    //public	friend idVecX	operator*( const idVecX &vec, const idMatX &m );
    //public	static idVecX	oMultiply( final idVecX vec, final idMatX m ){
    //	return m.oMultiply(vec);
    //}
    //public	friend idVecX &	operator*=( idVecX &vec, const idMatX &m );
    fun plusAssign(a: idMatX): idMatX {
        assert(numRows == a.numRows && numColumns == a.numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.AddAssign16(mat, a.mat, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                mat[i] += a.mat[i]
                i++
            }
        }
        tempIndex = 0
        return this
    }

    //public	idMatX &		operator-=( const idMatX &a );
    fun minusAssign(a: idMatX): idMatX {
        assert(numRows == a.numRows && numColumns == a.numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.SubAssign16(mat, a.mat, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                mat[i] -= a.mat[i]
                i++
            }
        }
        tempIndex = 0
        return this
    }

    // exact compare, no epsilon
    fun Compare(a: idMatX): Boolean {
        assert(numRows == a.numRows && numColumns == a.numColumns)
        val s: Int = numRows * numColumns
        var i = 0
        while (i < s) {
            if (mat[i] != a.mat[i]) {
                return false
            }
            i++
        }
        return true
    }

    //public	bool			operator==( const idMatX &a ) const;							// exact compare, no epsilon
    //public	bool			operator!=( const idMatX &a ) const;							// exact compare, no epsilon
    // compare with epsilon
    fun Compare(a: idMatX, epsilon: Float): Boolean {
        assert(numRows == a.numRows && numColumns == a.numColumns)
        val s: Int = numRows * numColumns
        var i = 0
        while (i < s) {
            val res = abs(mat[i] - a.mat[i])
            if (res > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 53 * hash + mat.contentHashCode()
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val matX = other as idMatX
        return mat.contentEquals(matX.mat)
    }

    // set the number of rows/columns
    fun SetSize(rows: Int, columns: Int) {
//            assert (mat < idMatX.tempPtr || mat > idMatX.tempPtr + MATX_MAX_TEMP);
        val alloc = rows * columns + 3 and 3.inv()
        if (alloc > alloced && alloced != -1) {
//            if (mat != null) {
////			Mem_Free16( mat );
//                mat = null //useless, but gives you a feeling of superiority.
//            }
            //		mat = (float *) Mem_Alloc16( alloc * sizeof( float ) );
            mat = FloatArray(alloc)
            alloced = alloc
        }
        numRows = rows
        numColumns = columns
        //	MATX_CLEAREND();
    }

    // change the size keeping data intact where possible
    @JvmOverloads
    fun ChangeSize(rows: Int, columns: Int, makeZero: Boolean = false) {
        val alloc = rows * columns + 3 and 3.inv()
        if (alloc > alloced && alloced != -1) {
            val oldMat = mat
            mat = FloatArray(alloc)
            if (makeZero) {
//			memset( mat, 0, alloc * sizeof( float ) );
                Arrays.fill(mat, 0, alloc, 0f)
            }
            alloced = alloc
            if (oldMat != null) { //TODO:wthfuck
                val minRow: Int = Lib.Min(numRows, rows)
                val minColumn: Int = Lib.Min(numColumns, columns)
                for (i in 0 until minRow) {
                    System.arraycopy(oldMat, i * numColumns + 0, mat, i * columns + 0, minColumn)
                }
                //			Mem_Free16( oldMat );
            }
        } else {
            if (columns < numColumns) {
                val minRow: Int = Lib.Min(numRows, rows)
                for (i in 0 until minRow) {
                    System.arraycopy(mat, i * numColumns + 0, mat, i * columns + 0, columns)
                }
            } else if (columns > numColumns) {
                for (i in Lib.Min(numRows, rows) - 1 downTo 0) {
                    if (makeZero) {
                        for (j in columns - 1 downTo numColumns) {
                            mat[i * columns + j] = 0.0f
                        }
                    }
                    System.arraycopy(mat, i * numColumns + 0, mat, i * columns + 0, numColumns - 1 + 1)
                }
            }
            if (makeZero && rows > numRows) {
//			memset( mat + numRows * columns, 0, ( rows - numRows ) * columns * sizeof( float ) );
                val from = numRows * columns
                val length = (rows - numRows) * columns
                val to = from + length
                Arrays.fill(mat, from, to, 0f)
            }
        }
        numRows = rows
        numColumns = columns
        //	MATX_CLEAREND();
    }

    fun GetNumRows(): Int {
        return numRows
    } // get the number of rows

    fun GetNumColumns(): Int {
        return numColumns
    } // get the number of columns

    fun SetData(rows: Int, columns: Int, data: FloatArray) { // set float array pointer
//            assert (mat < idMatX.tempPtr || mat > idMatX.tempPtr + MATX_MAX_TEMP);
        //if (mat != null && alloced != -1) {
//		Mem_Free16( mat );
        //}
        //assert ((data.length & 15) == 0); // data must be 16 byte aligned
        mat = data
        alloced = -1
        numRows = rows
        numColumns = columns
        //	MATX_CLEAREND();
    }

    // clear matrix
    fun Zero() {
        Arrays.fill(mat, 0f)
    }

    // set size and clear matrix
    fun Zero(rows: Int, columns: Int) {
        SetSize(rows, columns)
        Arrays.fill(mat, 0, rows * columns, 0f)
    }

    // clear to identity matrix
    fun Identity() {
        assert(numRows == numColumns)
        //#ifdef MATX_SIMD
//	SIMDProcessor->Zero16( mat, numRows * numColumns );
//#else
//	memset( mat, 0, numRows * numColumns * sizeof( float ) );
        Arrays.fill(mat, 0, numRows * numColumns, 0f)
        //#endif
        for (i in 0 until numRows) {
            mat[i * numColumns + i] = 1.0f
        }
    }

    fun Identity(rows: Int, columns: Int) { // set size and clear to identity matrix
        assert(rows == columns)
        SetSize(rows, columns)
        this.Identity()
    }

    // create diagonal matrix from vector
    fun Diag(v: idVecX) {
        Zero(v.GetSize(), v.GetSize())
        for (i in 0 until v.GetSize()) {
            mat[i * numColumns + i] = v.oGet(i)
        }
    }

    @JvmOverloads
    fun Random(seed: Int, l: Float = 0.0f, u: Float = 1.0f) { // fill matrix with random values
        val rnd = idRandom(seed)
        val c: Float = u - l
        val s: Int = numRows * numColumns
        var i = 0
        while (i < s) {
            mat[i] = l + rnd.RandomFloat() * c
            i++
        }
    }

    @JvmOverloads
    fun Random(rows: Int, columns: Int, seed: Int, l: Float = 0.0f, u: Float = 1.0f) {
        val rnd = idRandom(seed)
        SetSize(rows, columns)
        val c: Float = u - l
        val s: Int = numRows * numColumns
        var i = 0
        while (i < s) {
            if (DISABLE_RANDOM_TEST) { //for testing.
                mat[i] = i.toFloat()
            } else {
                mat[i] = l + rnd.RandomFloat() * c
            }
            i++
        }
    }

    fun Negate() { // (*this) = - (*this)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.Negate16(mat, numRows * numColumns)
        } else {
            val s: Int = numRows * numColumns
            var i = 0
            while (i < s) {
                mat[i] = -mat[i]
                i++
            }
        }
    }

    fun Clamp(min: Float, max: Float) { // clamp all values
        val s: Int = numRows * numColumns
        var i = 0
        while (i < s) {
            if (mat[i] < min) {
                mat[i] = min
            } else if (mat[i] > max) {
                mat[i] = max
            }
            i++
        }
    }

    fun SwapRows(r1: Int, r2: Int): idMatX { // swap rows
        val ptr = FloatArray(numColumns)

//	ptr = (float *) _alloca16( numColumns * sizeof( float ) );
//	memcpy( ptr, mat + r1 * numColumns, numColumns * sizeof( float ) );
        System.arraycopy(mat, r1 * numColumns, ptr, 0, numColumns)
        //	memcpy( mat + r1 * numColumns, mat + r2 * numColumns, numColumns * sizeof( float ) );
        System.arraycopy(mat, r2 * numColumns, mat, r1 * numColumns, numColumns)
        //	memcpy( mat + r2 * numColumns, ptr, numColumns * sizeof( float ) );
        System.arraycopy(ptr, 0, mat, r2 * numColumns, numColumns)
        return this
    }

    fun SwapColumns(r1: Int, r2: Int): idMatX { // swap columns
        var ptr: Int
        var tmp: Float
        var i = 0
        while (i < numRows) {
            ptr = i * numColumns
            tmp = mat[ptr + r1]
            mat[ptr + r1] = mat[ptr + r2]
            mat[ptr + r2] = tmp
            i++
        }
        return this
    }

    fun SwapRowsColumns(r1: Int, r2: Int): idMatX { // swap rows and columns
        SwapRows(r1, r2)
        SwapColumns(r1, r2)
        return this
    }

    fun RemoveRow(r: Int): idMatX { // remove a row
        assert(r < numRows)
        numRows--

//        this.SetSize(numRows, numColumns);
        var i: Int = r
        while (i < numRows) {
            //TODO:create new array to save memory
//		memcpy( &mat[i * numColumns], &mat[( i + 1 ) * numColumns], numColumns * sizeof( float ) );
            System.arraycopy(mat, (i + 1) * numColumns, mat, i * numColumns, numColumns)
            i++
        }
        return this
    }

    fun RemoveColumn(r: Int): idMatX { // remove a column
        assert(r < numColumns)
        numColumns--
        var i = 0
        while (i < numRows - 1) {

//		memmove( &mat[i * numColumns + r], &mat[i * ( numColumns + 1 ) + r + 1], numColumns * sizeof( float ) );
            System.arraycopy(mat, 1 + r + (1 + numColumns) * i, mat, r + numColumns * i, numColumns)
            i++
        }
        //	memmove( &mat[i * numColumns + r], &mat[i * ( numColumns + 1 ) + r + 1], ( numColumns - r ) * sizeof( float ) );
        System.arraycopy(mat, 1 + r + (1 + numColumns) * i, mat, r + numColumns * i, numColumns - r)
        return this
    }

    fun RemoveRowColumn(r: Int): idMatX { // remove a row and column
//            int i;
//
//            assert (r < numRows && r < numColumns);
//
//            numRows--;
//            numColumns--;
//
//            if (r > 0) {
//                for (i = 0; i < r - 1; i++) {
////			memmove( &mat[i * numColumns + r], &mat[i * ( numColumns + 1 ) + r + 1], numColumns * sizeof( float ) );
//                    System.arraycopy(mat, i * (numColumns + 1) + r + 1, mat, i * numColumns + r, numColumns);
//                }
////		memmove( &mat[i * numColumns + r], &mat[i * ( numColumns + 1 ) + r + 1], ( numColumns - r ) * sizeof( float ) );
//                System.arraycopy(mat, i * (numColumns + 1) + r + 1, mat, i * numColumns + r, numColumns - r);
//            }
//
////	memcpy( &mat[r * numColumns], &mat[( r + 1 ) * ( numColumns + 1 )], r * sizeof( float ) );
//            System.arraycopy(mat, (r + 1) * (numColumns + 1), mat, r * numColumns, r);
//
//            for (i = r; i < numRows - 1; i++) {
////		memcpy( &mat[i * numColumns + r], &mat[( i + 1 ) * ( numColumns + 1 ) + r + 1], numColumns * sizeof( float ) );
//                System.arraycopy(mat, (i + 1) * (numColumns + 1) + r + 1, mat, i * numColumns + r, numColumns);
//            }
////	memcpy( &mat[i * numColumns + r], &mat[( i + 1 ) * ( numColumns + 1 ) + r + 1], ( numColumns - r ) * sizeof( float ) );
//            System.arraycopy(mat, (i + 1) * (numColumns + 1) + r + 1, mat, i * numColumns + r, numColumns - r);
        RemoveRow(r)
        RemoveColumn(r)
        return this
    }

    // clear the upper triangle
    fun ClearUpperTriangle() {
        assert(numRows == numColumns)
        for (i in numRows - 2 downTo 0) {
//		memset( mat + i * numColumns + i + 1, 0, (numColumns - 1 - i) * sizeof(float) );
            val start = i * numColumns + i + 1
            val end = start + (numColumns - 1 - i)
            Arrays.fill(mat, start, end, 0f)
        }
    }

    fun ClearLowerTriangle() { // clear the lower triangle
        assert(numRows == numColumns)
        for (i in 1 until numRows) {
//		memset( mat + i * numColumns, 0, i * sizeof(float) );
            val start = i * numColumns
            val end = start + i
            Arrays.fill(mat, start, end, 0f)
        }
    }

    fun SquareSubMatrix(m: idMatX, size: Int) { // get square sub-matrix from 0,0 to size,size
        assert(size <= m.numRows && size <= m.numColumns)
        SetSize(size, size)
        var i = 0
        while (i < size) {

//		memcpy( mat + i * numColumns, m.mat + i * m.numColumns, size * sizeof( float ) );
            System.arraycopy(m.mat, i * m.numColumns, mat, i * numColumns, size)
            i++
        }
    }

    fun MaxDifference(m: idMatX): Float { // return maximum element difference between this and m
        var j: Int
        var diff: Float
        var maxDiff: Float
        assert(numRows == m.numRows && numColumns == m.numColumns)
        maxDiff = -1.0f
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                diff = abs(mat[i * numColumns + j] - m.mat[i + j * m.numRows])
                if (maxDiff < 0.0f || diff > maxDiff) {
                    maxDiff = diff
                }
                j++
            }
            i++
        }
        return maxDiff
    }

    fun IsSquare(): Boolean {
        return numRows == numColumns
    }

    @JvmOverloads
    fun IsZero(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // returns true if (*this) == Zero
        for (i in 0 until numRows) {
            for (j in 0 until numColumns) {
                if (abs(mat[i * numColumns + j]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsIdentity(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // returns true if (*this) == Identity
        assert(numRows == numColumns)
        for (i in 0 until numRows) {
            for (j in 0 until numColumns) {
                if (abs(
                        mat[i * numColumns + j]
                                - if (i == j) 1.0f else 0.0f
                    ) > epsilon
                ) { //TODO:i==j
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // returns true if all elements are zero except for the elements on the diagonal
        assert(numRows == numColumns)
        for (i in 0 until numRows) {
            for (j in 0 until numColumns) {
                if (i != j && abs(oGet(i, j)) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsTriDiagonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // returns true if all elements are zero except for the elements on the diagonal plus or minus one column
        if (numRows != numColumns) {
            return false
        }
        for (i in 0 until numRows - 2) {
            for (j in i + 2 until numColumns) {
                if (abs(oGet(i, j)) > epsilon) {
                    return false
                }
                if (abs(oGet(j, i)) > epsilon) {
                    return false
                }
            }
        }
        return true
    }

    @JvmOverloads
    fun IsSymmetric(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // (*this)[i][j] == (*this)[j][i]
        if (numRows != numColumns) {
            return false
        }
        for (i in 0 until numRows) {
            for (j in 0 until numColumns) {
                if (abs(mat[i * numColumns + j] - mat[j * numColumns + i]) > epsilon) {
                    return false
                }
            }
        }
        return true
    }
    /*
     ============
     idMatX::IsOrthonormal

     returns true if (*this) * this->Transpose() == Identity and the length of each column vector is 1
     ============
     */
    /**
     * ============ idMatX::IsOrthogonal
     *
     *
     * returns true if (*this) * this->Transpose() == Identity ============
     */
    @JvmOverloads
    fun IsOrthogonal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var ptr2: Int
        var sum: Float
        if (!IsSquare()) {
            return false
        }
        var ptr1: Int = 0
        for (i in 0 until numRows) {
            for (j in 0 until numColumns) {
                ptr2 = j
                sum = mat[ptr1] * mat[ptr2] - if (i == j) 1 else 0
                for (n in 1 until numColumns) {
                    ptr2 += numColumns
                    sum += mat[ptr1 + n] * mat[ptr2]
                }
                if (abs(sum) > epsilon) {
                    return false
                }
            }
            ptr1 += numColumns
        }
        return true
    }

    @JvmOverloads
    fun IsOrthonormal(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var ptr2: Int
        var sum: Float
        if (!IsSquare()) {
            return false
        }
        var ptr1: Int = 0
        var i = 0
        while (i < numRows) {
            for (j in 0 until numColumns) {
                ptr2 = j
                sum = mat[ptr1] * mat[ptr2] - if (i == j) 1 else 0
                for (n in 1 until numColumns) {
                    ptr2 += numColumns
                    sum += mat[ptr1 + n] * mat[ptr2]
                }
                if (abs(sum) > epsilon) {
                    return false
                }
            }
            ptr1 += numColumns
            ptr2 = i
            sum = mat[ptr2] * mat[ptr2] - 1.0f
            i = 1
            while (i < numRows) {
                ptr2 += numColumns
                sum += mat[ptr2 + i] * mat[ptr2 + i]
                i++
            }
            if (abs(sum) > epsilon) {
                return false
            }
            i++
        }
        return true
    }

    /**
     * ============ idMatX::IsPMatrix
     *
     *
     * returns true if the matrix is a P-matrix A square matrix is a P-matrix if
     * all its principal minors are positive. ============
     */
    @JvmOverloads
    fun IsPMatrix(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var j: Int
        var d: Float
        val m = idMatX()
        if (!IsSquare()) {
            return false
        }
        if (numRows <= 0) {
            return true
        }
        if (oGet(0, 0) <= epsilon) {
            return false
        }
        if (numRows <= 1) {
            return true
        }

//	m.SetData( numRows - 1, numColumns - 1, MATX_ALLOCA( ( numRows - 1 ) * ( numColumns - 1 ) ) );
        m.SetSize(numRows - 1, numColumns - 1)
        var i: Int = 1
        while (i < numRows) {
            j = 1
            while (j < numColumns) {
                m.oSet(i - 1, j - 1, oGet(i, j))
                j++
            }
            i++
        }
        if (!m.IsPMatrix(epsilon)) {
            return false
        }
        i = 1
        while (i < numRows) {
            d = oGet(i, 0) / oGet(0, 0)
            j = 1
            while (j < numColumns) {
                m.oSet(i - 1, j - 1, oGet(i, j) - d * oGet(0, j))
                j++
            }
            i++
        }
        return m.IsPMatrix(epsilon)
    }
    /*
     ============
     idMatX::IsPositiveDefinite

     returns true if the matrix is Positive Definite (PD)
     A square matrix M of order n is said to be PD if y'My > 0 for all vectors y of dimension n, y != 0.
     ============
     */
    /**
     * ============ idMatX::IsZMatrix
     *
     *
     * returns true if the matrix is a Z-matrix A square matrix M is a Z-matrix
     * if M[i][j] <= 0 for all i != j. ============
     */
    @JvmOverloads
    fun IsZMatrix(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var j: Int
        if (!IsSquare()) {
            return false
        }
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                if (oGet(i, j) > epsilon && i != j) {
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    @JvmOverloads
    fun IsPositiveDefinite(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var j: Int
        var k: Int
        var d: Float
        var s: Float
        val m = idMatX()

        // the matrix must be square
        if (!IsSquare()) {
            return false
        }

        // copy matrix
//	m.SetData( numRows, numColumns, MATX_ALLOCA( numRows * numColumns ) );
//	m = *this;
        m.SetData(numRows, numColumns, m.mat)

        // add transpose
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                m.plusAssign(i, j, oGet(j, i))
                j++
            }
            i++
        }

        // test Positive Definiteness with Gaussian pivot steps
        i = 0
        while (i < numRows) {
            j = i
            while (j < numColumns) {
                if (oGet(j, j) <= epsilon) {
                    return false
                }
                j++
            }
            d = 1.0f / m.oGet(i, i)
            j = i + 1
            while (j < numColumns) {
                s = d * m.oGet(j, i)
                m.oSet(i, j, 0.0f)
                k = i + 1
                while (k < numRows) {
                    m.minusAssign(j, k, s * m.oGet(i, k))
                    k++
                }
                j++
            }
            i++
        }
        return true
    }

    /**
     * ============ idMatX::IsSymmetricPositiveDefinite
     *
     *
     * returns true if the matrix is Symmetric Positive Definite (PD)
     * ============
     */
    @JvmOverloads
    fun IsSymmetricPositiveDefinite(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        val m = idMatX()

        // the matrix must be symmetric
        if (!IsSymmetric(epsilon)) {
            return false
        }

        // copy matrix
//	m.SetData( numRows, numColumns, MATX_ALLOCA( numRows * numColumns ) );
//	m = *this;
        m.SetData(numRows, numColumns, mat)

        // being able to obtain Cholesky factors is both a necessary and sufficient condition for positive definiteness
        return m.Cholesky_Factor()
    }

    /**
     * ============ idMatX::IsPositiveSemiDefinite
     *
     *
     * returns true if the matrix is Positive Semi Definite (PSD) A square
     * matrix M of order n is said to be PSD if y'My >= 0 for all vectors y of
     * dimension n, y != 0. ============
     */
    @JvmOverloads
    fun IsPositiveSemiDefinite(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        var j: Int
        var k: Int
        var d: Float
        var s: Float
        val m = idMatX()

        // the matrix must be square
        if (!IsSquare()) {
            return false
        }

        // copy original matrix
//	m.SetData( numRows, numColumns, MATX_ALLOCA( numRows * numColumns ) );
//	m = *this;
        m.SetData(numRows, numColumns, mat)

        // add transpose
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                m.plusAssign(i, j, this.oGet(j, i))
                j++
            }
            i++
        }

        // test Positive Semi Definiteness with Gaussian pivot steps
        i = 0
        while (i < numRows) {
            j = i
            while (j < numColumns) {
                if (m.oGet(j, j) < -epsilon) {
                    return false
                }
                if (m.oGet(j, j) > epsilon) {
                    j++
                    continue
                }
                k = 0
                while (k < numRows) {
                    if (abs(m.oGet(k, j)) > epsilon) {
                        return false
                    }
                    if (abs(m.oGet(j, k)) > epsilon) {
                        return false
                    }
                    k++
                }
                j++
            }
            if (m.oGet(i, i) <= epsilon) {
                i++
                continue
            }
            d = 1.0f / m.oGet(i, i)
            j = i + 1
            while (j < numColumns) {
                s = d * m.oGet(j, i)
                m.oSet(j, i, 0.0f)
                k = i + 1
                while (k < numRows) {
                    m.minusAssign(j, k, s * m.oGet(i, k))
                    k++
                }
                j++
            }
            i++
        }
        return true
    }

    @JvmOverloads
    fun IsSymmetricPositiveSemiDefinite(epsilon: Float = idMat0.MATRIX_EPSILON.toFloat()): Boolean {
        // the matrix must be symmetric
        return if (!IsSymmetric(epsilon)) {
            false
        } else IsPositiveSemiDefinite(epsilon)
    }

    fun Trace(): Float { // returns product of diagonal elements
        var trace = 0.0f
        assert(numRows == numColumns)

        // sum of elements on the diagonal
        for (i in 0 until numRows) {
            trace += mat[i * numRows + i]
        }
        return trace
    }

    fun Determinant(): Float { // returns determinant of matrix
        assert(numRows == numColumns)
        return when (numRows) {
            1 -> mat[0]
            2 -> //			return reinterpret_cast<const idMat2 *>(mat)->Determinant();
                mat[0] + mat[3]
            3 -> //			return reinterpret_cast<const idMat3 *>(mat)->Determinant();
                mat[0] + mat[4] + mat[8]
            4 -> //			return reinterpret_cast<const idMat4 *>(mat)->Determinant();
                mat[0] + mat[5] + mat[10] + mat[15]
            5 -> //			return reinterpret_cast<const idMat5 *>(mat)->Determinant();
                mat[0] + mat[6] + mat[12] + mat[18] + mat[24]
            6 -> //			return reinterpret_cast<const idMat6 *>(mat)->Determinant();
                mat[0] + mat[7] + mat[14] + mat[21] + mat[28] + mat[35]
            else -> DeterminantGeneric()
        }
        //            return 0.0f;
    }

    fun Transpose(): idMatX { // returns transpose
        val transpose = idMatX()
        var j: Int
        transpose.SetTempSize(numColumns, numRows)
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                transpose.mat[j * transpose.numColumns + i] = mat[i * numColumns + j]
                j++
            }
            i++
        }
        return transpose
    }

    // transposes the matrix itself
    fun TransposeSelf(): idMatX {
        this.oSet(Transpose())
        return this
    }

    fun Inverse(): idMatX { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMatX()

//	invMat.SetTempSize( numRows, numColumns );
//	memcpy( invMat.mat, mat, numRows * numColumns * sizeof( float ) );
        invMat.SetData(numRows, numColumns, mat)
        val r = invMat.InverseSelf()
        assert(r)
        return invMat
    }

    fun InverseSelf(): Boolean { // returns false if determinant is zero
        assert(numRows == numColumns)
        val result: Boolean
        return when (numRows) {
            1 -> {
                if (abs(mat[0]) < idMat0.MATRIX_INVERSE_EPSILON) {
                    return false
                }
                mat[0] = 1.0f / mat[0]
                true
            }
            2 -> {
                val mat2 = idMat2(
                    mat[0], mat[1],
                    mat[2], mat[3]
                )
                result = mat2.InverseSelf()
                mat = mat2.reinterpret_cast()
                result
            }
            3 -> {
                val mat3 = idMat3(
                    mat[0], mat[1], mat[2],
                    mat[3], mat[4], mat[5],
                    mat[6], mat[7], mat[8]
                )
                result = mat3.InverseSelf()
                mat = mat3.reinterpret_cast()
                result
            }
            4 -> {
                val mat4 = idMat4(
                    mat[0], mat[1], mat[2], mat[3],
                    mat[0], mat[1], mat[2], mat[3],
                    mat[0], mat[1], mat[2], mat[3],
                    mat[0], mat[1], mat[2], mat[3]
                )
                result = mat4.InverseSelf()
                mat = mat4.reinterpret_cast()
                result
            }
            5 -> {
                val mat5 = idMat5(
                    idVec5(mat[0], mat[1], mat[2], mat[3], mat[4]),
                    idVec5(mat[5], mat[6], mat[2], mat[3], mat[4]),
                    idVec5(mat[10], mat[11], mat[12], mat[13], mat[14]),
                    idVec5(mat[15], mat[16], mat[17], mat[18], mat[19]),
                    idVec5(mat[20], mat[21], mat[22], mat[23], mat[24])
                )
                result = mat5.InverseSelf()
                mat = mat5.reinterpret_cast()
                result
            }
            6 -> {
                val mat6 = idMat6(
                    idVec6(mat[0], mat[1], mat[2], mat[3], mat[4], mat[5]),
                    idVec6(mat[6], mat[7], mat[8], mat[9], mat[10], mat[11]),
                    idVec6(mat[12], mat[13], mat[14], mat[15], mat[16], mat[17]),
                    idVec6(mat[18], mat[19], mat[20], mat[21], mat[22], mat[23]),
                    idVec6(mat[24], mat[25], mat[26], mat[27], mat[28], mat[29]),
                    idVec6(mat[30], mat[31], mat[32], mat[33], mat[34], mat[35])
                )
                result = mat6.InverseSelf()
                mat = mat6.reinterpret_cast()
                result
            }
            else -> InverseSelfGeneric()
        }
    }

    fun InverseFast(): idMatX { // returns the inverse ( m * m.Inverse() = identity )
        val invMat = idMatX()
        invMat.SetTempSize(numRows, numColumns)
        System.arraycopy(mat, 0, invMat.mat, 0, numRows * numColumns)
        val r = invMat.InverseFastSelf()
        assert(r)
        return invMat
    }

    fun InverseFastSelf(): Boolean { // returns false if determinant is zero
        assert(numRows == numColumns)
        val result: Boolean
        return when (numRows) {
            1 -> {
                if (abs(mat[0]) < idMat0.MATRIX_INVERSE_EPSILON) {
                    return false
                }
                mat[0] = 1.0f / mat[0]
                true
            }
            2 -> {
                val mat2 = idMat2(
                    mat[0], mat[1],
                    mat[2], mat[3]
                )
                result = mat2.InverseFastSelf()
                mat = mat2.reinterpret_cast()
                result
            }
            3 -> {
                val mat3 = idMat3(
                    mat[0], mat[1], mat[2],
                    mat[3], mat[4], mat[5],
                    mat[6], mat[7], mat[8]
                )
                result = mat3.InverseFastSelf()
                mat = mat3.reinterpret_cast()
                result
            }
            4 -> {
                val mat4 = idMat4(
                    mat[0], mat[1], mat[2], mat[3],
                    mat[4], mat[5], mat[6], mat[7],
                    mat[8], mat[9], mat[10], mat[11],
                    mat[12], mat[13], mat[14], mat[15]
                )
                result = mat4.InverseFastSelf()
                mat = mat4.reinterpret_cast()
                result
            }
            5 -> {
                val mat5 = idMat5(
                    idVec5(mat[0], mat[1], mat[2], mat[3], mat[4]),
                    idVec5(mat[5], mat[6], mat[7], mat[8], mat[9]),
                    idVec5(mat[10], mat[11], mat[12], mat[13], mat[14]),
                    idVec5(mat[15], mat[16], mat[17], mat[18], mat[19]),
                    idVec5(mat[20], mat[21], mat[22], mat[23], mat[24])
                )
                result = mat5.InverseFastSelf()
                mat = mat5.reinterpret_cast()
                result
            }
            6 -> {
                val mat6 = idMat6(
                    idVec6(mat[0], mat[1], mat[2], mat[3], mat[4], mat[5]),
                    idVec6(mat[6], mat[7], mat[8], mat[9], mat[10], mat[11]),
                    idVec6(mat[12], mat[13], mat[14], mat[15], mat[16], mat[17]),
                    idVec6(mat[18], mat[19], mat[20], mat[21], mat[22], mat[23]),
                    idVec6(mat[24], mat[25], mat[26], mat[27], mat[28], mat[29]),
                    idVec6(mat[30], mat[31], mat[32], mat[33], mat[34], mat[35])
                )
                result = mat6.InverseFastSelf() //TODO: merge fast and slow
                mat = mat6.reinterpret_cast()
                result
            }
            else -> InverseSelfGeneric()
        }
        //            return false;
    }

    /**
     * ============ idMatX::LowerTriangularInverse
     *
     *
     * in-place inversion of the lower triangular matrix ============
     */
    fun LowerTriangularInverse(): Boolean { // in-place inversion, returns false if determinant is zero
        var j: Int
        var k: Int
        var d: Double
        var sum: Double
        var i: Int = 0
        while (i < numRows) {
            d = this.oGet(i, i).toDouble()
            //                System.out.println("1:" + d);
            if (d == 0.0) {
                return false
            }
            this.oSet(i, i, (1.0f / d.also { d = it }).toFloat())
            //                System.out.println("2:" + d);
            j = 0
            while (j < i) {
                sum = 0.0
                k = j
                while (k < i) {
                    sum -= (this.oGet(i, k) * this.oGet(k, j)).toDouble()
                    k++
                }
                this.oSet(i, j, (sum * d).toFloat())
                j++
            }
            i++
        }
        return true
    }

    /**
     * ============ idMatX::UpperTriangularInverse
     *
     *
     * in-place inversion of the upper triangular matrix ============
     */
    fun UpperTriangularInverse(): Boolean { // in-place inversion, returns false if determinant is zero
        var j: Int
        var k: Int
        var d: Double
        var sum: Double
        var i: Int = numRows - 1
        while (i >= 0) {
            d = this.oGet(i, i).toDouble()
            if (d == 0.0) {
                return false
            }
            this.oSet(i, i, (1.0f / d.also { d = it }).toFloat())
            j = numRows - 1
            while (j > i) {
                sum = 0.0
                k = j
                while (k > i) {
                    sum -= (this.oGet(i, k) * this.oGet(k, j)).toDouble()
                    k--
                }
                this.oSet(i, j, (sum * d).toFloat())
                j--
            }
            i--
        }
        return true
    }

    fun Multiply(vec: idVecX): idVecX { // (*this) * vec
        val dst = idVecX()
        assert(numColumns == vec.GetSize())
        dst.SetTempSize(numRows)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyVecX(dst, this, vec)
        } else {
            Multiply(dst, vec)
        }
        return dst
    }

    fun TransposeMultiply(vec: idVecX): idVecX { // this->Transpose() * vec
        val dst = idVecX()
        assert(numRows == vec.GetSize())
        dst.SetTempSize(numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplyVecX(dst, this, vec)
        } else {
            TransposeMultiply(dst, vec)
        }
        return dst
    }

    fun Multiply(a: idMatX): idMatX { // (*this) * a
        val dst = idMatX()
        assert(numColumns == a.numRows)
        dst.SetTempSize(numRows, a.numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyMatX(dst, this, a)
        } else {
            Multiply(dst, a)
        }
        return dst
    }

    fun TransposeMultiply(a: idMatX): idMatX { // this->Transpose() * a
        val dst = idMatX()
        assert(numRows == a.numRows)
        dst.SetTempSize(numColumns, a.numColumns)
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplyMatX(dst, this, a)
        } else {
            TransposeMultiply(dst, a)
        }
        return dst
    }

    fun Multiply(dst: idVecX, vec: idVecX) { // dst = (*this) * vec
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyVecX(dst, this, vec)
        } else {
            var j: Int
            var m = 0
            val mPtr: FloatArray = mat
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numRows) {
                var sum = mPtr[m + 0] * vPtr[0]
                j = 1
                while (j < numColumns) {
                    sum += mPtr[m + j] * vPtr[j]
                    j++
                }
                dstPtr[i] = sum
                m += numColumns
                i++
            }
        }
    }

    fun MultiplyAdd(dst: idVecX, vec: idVecX) { // dst += (*this) * vec
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyAddVecX(dst, this, vec)
        } else {
            var j: Int
            var m = 0
            val mPtr: FloatArray = mat
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numRows) {
                var sum = mPtr[0 + m] * vPtr[0]
                j = 1
                while (j < numColumns) {
                    sum += mPtr[j + m] * vPtr[j]
                    j++
                }
                dstPtr[i] += sum
                m += numColumns
                i++
            }
        }
    }

    fun MultiplySub(dst: idVecX, vec: idVecX) { // dst -= (*this) * vec
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplySubVecX(dst, this, vec)
        } else {
            var j: Int
            var m = 0
            val mPtr: FloatArray = mat
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numRows) {
                var sum = mPtr[0 + m] * vPtr[0]
                j = 1
                while (j < numColumns) {
                    sum += mPtr[j + m] * vPtr[j]
                    j++
                }
                dstPtr[i] -= sum
                m += numColumns
                i++
            }
        }
    }

    fun TransposeMultiply(dst: idVecX, vec: idVecX) { // dst = this->Transpose() * vec
        if (!MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplyVecX(dst, this, vec) // <- buggy
        } else {
            var j: Int
            var mPtr: Int
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numColumns) {
                mPtr = i
                var sum = mat[mPtr] * vPtr[0]
                j = 1
                while (j < numRows) {
                    mPtr += numColumns
                    sum += mat[mPtr] * vPtr[j]
                    j++
                }
                dstPtr[i] = sum
                i++
            }
        }
    }

    fun TransposeMultiplyAdd(dst: idVecX, vec: idVecX) { // dst += this->Transpose() * vec
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplyAddVecX(dst, this, vec)
        } else {
            var j: Int
            var mPtr: Int
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numColumns) {
                mPtr = i
                var sum = mat[mPtr] * vPtr[0]
                j = 1
                while (j < numRows) {
                    mPtr += numColumns
                    sum += mat[mPtr] * vPtr[j]
                    j++
                }
                dstPtr[i] += sum
                i++
            }
        }
    }

    fun TransposeMultiplySub(dst: idVecX, vec: idVecX) { // dst -= this->Transpose() * vec
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplySubVecX(dst, this, vec)
        } else {
            var j: Int
            var mPtr: Int
            val vPtr: FloatArray = vec.ToFloatPtr()
            val dstPtr: FloatArray = dst.ToFloatPtr()
            var i: Int = 0
            while (i < numColumns) {
                mPtr = i
                var sum = mat[mPtr] * vPtr[0]
                j = 1
                while (j < numRows) {
                    mPtr += numColumns
                    sum += mat[mPtr] * vPtr[j]
                    j++
                }
                dstPtr[i] -= sum
                i++
            }
        }
    }

    fun Multiply(dst: idMatX, a: idMatX) { // dst = (*this) * a
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_MultiplyMatX(dst, this, a)
        } else {
            var j: Int
            var n: Int
            var sum: Double //double, the difference between life and death.
            var m1 = 0
            var m2 = 0
            var d0 = 0 //indices
            assert(numColumns == a.numRows)
            val dstPtr: FloatArray = dst.ToFloatPtr()
            val m1Ptr: FloatArray = ToFloatPtr()
            val m2Ptr: FloatArray = a.ToFloatPtr()
            val k: Int = numRows
            val l: Int = a.GetNumColumns()
            var i: Int = 0
            while (i < k) {
                j = 0
                while (j < l) {
                    m2 = j
                    sum = (m1Ptr[0 + m1] * m2Ptr[0 + m2]).toDouble()
                    n = 1
                    while (n < numColumns) {
                        m2 += l
                        sum += (m1Ptr[n + m1] * m2Ptr[0 + m2]).toDouble()
                        n++
                    }
                    dstPtr[d0++] = sum.toFloat()
                    j++
                }
                m1 += numColumns
                i++
            }
        }
    }

    fun TransposeMultiply(dst: idMatX, a: idMatX) { // dst = this->Transpose() * a
        if (MATX_SIMD) {
            Simd.SIMDProcessor.MatX_TransposeMultiplyMatX(dst, this, a)
        } else {
            var j: Int
            var n: Int
            var sum: Double
            var m1: Int
            var m2: Int
            var d0 = 0 //indices
            assert(
                numRows == a.numRows //TODO:check if these pseudo indices work like the pointers
            )
            val dstPtr: FloatArray = dst.ToFloatPtr()
            val m1Ptr: FloatArray = ToFloatPtr()
            val m2Ptr: FloatArray = a.ToFloatPtr()
            val k: Int = numColumns
            val l: Int = a.numColumns
            var i = 0
            while (i < k) {
                j = 0
                while (j < l) {
                    m1 = i
                    m2 = j
                    sum = (m1Ptr[0 + m1] * m2Ptr[0 + m2]).toDouble()
                    n = 1
                    while (n < numRows) {
                        m1 += numColumns
                        m2 += a.numColumns
                        sum += (m1Ptr[0 + m1] * m2Ptr[0 + m2]).toDouble()
                        n++
                    }
                    dstPtr[d0++] = sum.toFloat()
                    j++
                }
                i++
            }
        }
    }

    fun GetDimension(): Int { // returns total number of values in matrix
        return numRows * numColumns
    }
    //public	idVec6 &		SubVec6( int row );												// interpret beginning of row as an idVec6

    @Deprecated("returns readonly vector")
    fun SubVec6(row: Int): idVec6 { // interpret beginning of row as a const idVec6
        assert(numColumns >= 6 && row >= 0 && row < numRows)
        //	return *reinterpret_cast<const idVec6 *>(mat + row * numColumns);
        val temp = FloatArray(6)
        System.arraycopy(mat, row * numColumns, temp, 0, 6)
        return idVec6(temp)
    }

    //public	idVecX			SubVecX( int row );												// interpret complete row as an idVecX
    fun SubVecX(row: Int): idVecX { // interpret complete row as a const idVecX
        val v = idVecX()
        assert(row >= 0 && row < numRows)
        val temp = FloatArray(numColumns)
        System.arraycopy(mat, row * numColumns, temp, 0, numColumns)
        v.SetData(numColumns, temp)
        return v
    }

    fun ToFloatPtr(): FloatArray { // pointer to const matrix float array
        return mat
    }

    @JvmOverloads
    fun ToFloatBufferPtr(offset: Int = 0): FloatBuffer {
        return FloatBuffer.wrap(mat).position(offset).slice()
    }

    fun GetRowPtr(row: Int): FloatBuffer {
        val start = row * numColumns
        //        final int end = start + numColumns;
//        return ((FloatBuffer)FloatBuffer.wrap(mat).position(start).limit(end)).slice();
        return ToFloatBufferPtr(start)
    }

    fun FromFloatPtr(mat: FloatArray) {
        this.mat = mat
    }

    //public	float *			ToFloatPtr( void );												// pointer to matrix float array
    fun ToString(precision: Int): String {
        return idStr.FloatArrayToString(ToFloatPtr(), GetDimension(), precision)
    }

    /**
     * ============ idMatX::Update_RankOne
     *
     *
     * Updates the matrix to obtain the matrix: A + alpha * v * w' ============
     */
    fun Update_RankOne(v: idVecX, w: idVecX, alpha: Float) {
        var j: Int
        var s: Float
        assert(v.GetSize() >= numRows)
        assert(w.GetSize() >= numColumns)
        var i: Int = 0
        while (i < numRows) {
            s = alpha * v.p[i]
            j = 0
            while (j < numColumns) {
                this.plusAssign(i, j, s * w.p[j])
                j++
            }
            i++
        }
    }

    /*
     ============
     idMatX::Update_RankOneSymmetric

     Updates the matrix to obtain the matrix: A + alpha * v * v'
     ============
     */
    fun Update_RankOneSymmetric(v: idVecX, alpha: Float) {
        var j: Int
        var s: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        var i: Int = 0
        while (i < numRows) {
            s = alpha * v.p[i]
            j = 0
            while (j < numColumns) {
                this.plusAssign(i, j, s * v.p[j])
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::Update_RowColumn
     *
     *
     * Updates the matrix to obtain the matrix:
     *
     *
     * [ 0 a 0 ]
     * A + [ d b e ]
     * [ 0 c 0 ]
     *
     *
     * where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1], d = w[0,r-1], w[r] =
     * 0.0f, e = w[r+1,numColumns-1] ============
     */
    fun Update_RowColumn(v: idVecX, w: idVecX, r: Int) {
        assert(w.p[r] == 0.0f)
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        var i: Int = 0
        while (i < numRows) {
            this.plusAssign(i, r, v.p[i])
            i++
        }
        i = 0
        while (i < numColumns) {
            this.plusAssign(r, i, w.p[i])
            i++
        }
    }

    /**
     * ============ idMatX::Update_RowColumnSymmetric
     *
     *
     * Updates the matrix to obtain the matrix:
     *
     *
     * [ 0 a 0 ]
     * A + [ a b c ]
     * [ 0 c 0 ]
     *
     *
     * where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1] ============
     */
    fun Update_RowColumnSymmetric(v: idVecX, r: Int) {
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        var i: Int = 0
        while (i < r) {
            this.plusAssign(i, r, v.p[i])
            this.plusAssign(r, i, v.p[i])
            i++
        }
        this.oSet(r, r, this.oGet(r, r) + v.p[r])
        i = r + 1
        while (i < numRows) {
            this.plusAssign(i, r, v.p[i])
            this.plusAssign(r, i, v.p[i])
            i++
        }
    }

    /**
     * ============ idMatX::Update_Increment
     *
     *
     * Updates the matrix to obtain the matrix:
     *
     *
     * [ A a ]
     * [ c b ]
     *
     *
     * where: a = v[0,numRows-1], b = v[numRows], c = w[0,numColumns-1]],
     * w[numColumns] = 0 ============
     */
    fun Update_Increment(v: idVecX, w: idVecX) {
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        assert(w.GetSize() >= numColumns + 1)
        ChangeSize(numRows + 1, numColumns + 1, false)
        var i: Int = 0
        while (i < numRows) {
            this.oSet(i, numColumns - 1, v.p[i])
            i++
        }
        i = 0
        while (i < numColumns - 1) {
            this.oSet(numRows - 1, i, w.p[i])
            i++
        }
    }

    /**
     * ============ idMatX::Update_IncrementSymmetric
     *
     *
     * Updates the matrix to obtain the matrix:
     *
     *
     * [ A a ]
     * [ a b ]
     *
     *
     * where: a = v[0,numRows-1], b = v[numRows] ============
     */
    fun Update_IncrementSymmetric(v: idVecX) {
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        ChangeSize(numRows + 1, numColumns + 1, false)
        var i: Int = 0
        while (i < numRows - 1) {
            this.oSet(i, numColumns - 1, v.p[i])
            i++
        }
        i = 0
        while (i < numColumns) {
            this.oSet(numRows - 1, i, v.p[i])
            i++
        }
    }

    /*
     ============
     idMatX::Update_Decrement

     Updates the matrix to obtain a matrix with row r and column r removed.
     ============
     */
    fun Update_Decrement(r: Int) {
        RemoveRowColumn(r)
    }

    /*
     ============
     idMatX::Inverse_UpdateRankOne

     Updates the in-place inverse using the Sherman-Morrison formula to obtain the inverse for the matrix: A + alpha * v * w'
     ============
     */
    /*
     ============
     idMatX::Inverse_GaussJordan

     in-place inversion using Gauss-Jordan elimination
     ============
     */
    fun Inverse_GaussJordan(): Boolean { // invert in-place with Gauss-Jordan elimination
        var j: Int
        var k: Int
        var r: Int
        var c: Int
        var d: Float
        var max: Float
        assert(numRows == numColumns)
        val columnIndex = IntArray(numRows)
        val rowIndex = IntArray(numRows)
        val pivot = BooleanArray(numRows) //memset( pivot, 0, numRows * sizeof( bool ) );

        // elimination with full pivoting
        var i: Int = 0
        while (i < numRows) {


            // search the whole matrix except for pivoted rows for the maximum absolute value
            max = 0.0f
            c = 0
            r = c
            j = 0
            while (j < numRows) {
                if (!pivot[j]) {
                    k = 0
                    while (k < numRows) {
                        if (!pivot[k]) {
                            d = abs(this.oGet(j, k))
                            if (d > max) {
                                max = d
                                r = j
                                c = k
                            }
                        }
                        k++
                    }
                }
                j++
            }
            if (max == 0.0f) {
                // matrix is not invertible
                return false
            }
            pivot[c] = true

            // swap rows such that entry (c,c) has the pivot entry
            if (r != c) {
                SwapRows(r, c)
            }

            // keep track of the row permutation
            rowIndex[i] = r
            columnIndex[i] = c

            // scale the row to make the pivot entry equal to 1
            d = 1.0f / this.oGet(c, c)
            this.oSet(c, c, 1.0f)
            k = 0
            while (k < numRows) {
                this.timesAssign(c, k, d)
                k++
            }

            // zero out the pivot column entries in the other rows
            j = 0
            while (j < numRows) {
                if (j != c) {
                    d = this.oGet(j, c)
                    this.oSet(j, c, 0.0f)
                    k = 0
                    while (k < numRows) {
                        this.minusAssign(j, k, this.oGet(c, k) * d)
                        k++
                    }
                }
                j++
            }
            i++
        }

        // reorder rows to store the inverse of the original matrix
        j = numRows - 1
        while (j >= 0) {
            if (rowIndex[j] != columnIndex[j]) {
                k = 0
                while (k < numRows) {
                    d = this.oGet(k, rowIndex[j])
                    this.oSet(k, rowIndex[j], this.oGet(k, columnIndex[j]))
                    this.oSet(k, columnIndex[j], d)
                    k++
                }
            }
            j--
        }
        return true
    }

    fun Inverse_UpdateRankOne(v: idVecX, w: idVecX, alpha: Float): Boolean {
        var alpha = alpha
        var j: Int
        val beta: Float
        var s: Float
        val y = idVecX()
        val z = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        y.SetData(numRows, FloatArray(numRows))
        z.SetData(numRows, FloatArray(numRows))
        Multiply(y, v)
        TransposeMultiply(z, w)
        beta = 1.0f + w.times(y)
        if (beta == 0.0f) {
            return false
        }
        alpha /= beta
        var i: Int = 0
        while (i < numRows) {
            s = y.p[i] * alpha
            j = 0
            while (j < numColumns) {

                // (*this)[i][j]
                val result = s * z.p[j]
                this.minusAssign(i, j, result)
                j++
            }
            i++
        }
        return true
    }

    /**
     * ============ idMatX::Inverse_UpdateRowColumn
     *
     *
     * Updates the in-place inverse to obtain the inverse for the matrix:
     *
     *
     * [ 0 a 0 ]
     * A + [ d b e ]
     * [ 0 c 0 ]
     *
     *
     * where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1], d = w[0,r-1], w[r] =
     * 0.0f, e = w[r+1,numColumns-1] ============
     */
    fun Inverse_UpdateRowColumn(v: idVecX, w: idVecX, r: Int): Boolean {
        val s = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        assert(r >= 0 && r < numRows && r < numColumns)
        assert(w.p[r] == 0.0f)
        s.SetData(Lib.Max(numRows, numColumns), FloatArray(Lib.Max(numRows, numColumns)))
        s.Zero()
        s.oSet(r, 1.0f)
        if (!Inverse_UpdateRankOne(v, s, 1.0f)) {
            return false
        }
        return Inverse_UpdateRankOne(s, w, 1.0f)
    }

    /**
     * ============ idMatX::Inverse_UpdateIncrement
     *
     *
     * Updates the in-place inverse to obtain the inverse for the matrix:
     *
     *
     * [ A a ]
     * [ c b ]
     *
     *
     * where: a = v[0,numRows-1], b = v[numRows], c = w[0,numColumns-1],
     * w[numColumns] = 0 ============
     */
    fun Inverse_UpdateIncrement(v: idVecX, w: idVecX): Boolean {
        var v2: idVecX = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        assert(w.GetSize() >= numColumns + 1)
        ChangeSize(numRows + 1, numColumns + 1, true)
        this.oSet(numRows - 1, numRows - 1, 1.0f)
        v2.SetData(numRows, FloatArray(numRows))
        v2 = v
        v2.oSet(numRows - 1, v.oGet(numRows - 1) - 1.0f) //v2.p[numRows - 1] -= 1.0f;
        return Inverse_UpdateRowColumn(v2, w, numRows - 1)
    }

    /**
     * ============ idMatX::Inverse_UpdateDecrement
     *
     *
     * Updates the in-place inverse to obtain the inverse of the matrix with row
     * r and column r removed. v and w should store the column and row of the
     * original matrix respectively. ============
     */
    fun Inverse_UpdateDecrement(v: idVecX, w: idVecX, r: Int): Boolean {
        val v1 = idVecX()
        val w1 = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(w.GetSize() >= numColumns)
        assert(r >= 0 && r < numRows && r < numColumns)
        v1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        w1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))

        // update the row and column to identity
        v1.oSet(v.unaryMinus())
        w1.oSet(w.unaryMinus())
        v1.p[r] += 1.0f
        w1.p[r] = 0.0f
        if (!Inverse_UpdateRowColumn(v1, w1, r)) {
            return false
        }

        // physically remove the row and column
        Update_Decrement(r)
        return true
    }

    /**
     * ============ idMatX::Inverse_Solve
     *
     *
     * Solve Ax = b with A inverted ============
     */
    fun Inverse_Solve(x: idVecX, b: idVecX) {
        Multiply(x, b)
    }

    /*
     ============
     idMatX::LU_Factor

     in-place factorization: LU
     L is a triangular matrix stored in the lower triangle.
     L has ones on the diagonal that are not stored.
     U is a triangular matrix stored in the upper triangle.
     If index != NULL partial pivoting is used for numerical stability.
     If index != NULL it must point to an array of numRow integers and is used to keep track of the row permutation.
     If det != NULL the determinant of the matrix is calculated and stored.
     ============
     */
    @JvmOverloads
    fun LU_Factor(index: IntArray?, det: FloatArray? = null): Boolean {
        var i: Int
        var j: Int
        var k: Int
        var newi: Int
        var s: Double
        var t: Double
        var d: Double
        var w: Double

        // if partial pivoting should be used
        if (index != null) {
            i = 0
            while (i < numRows) {
                index[i] = i
                i++
            }
        }
        w = 1.0
        val min: Int = Lib.Min(numRows, numColumns)
        i = 0
        while (i < min) {
            newi = i
            s = abs(this.oGet(i, i)).toDouble()
            if (index != null) {
                // find the largest absolute pivot
                j = i + 1
                while (j < numRows) {
                    t = abs(this.oGet(j, i)).toDouble()
                    //                    System.out.println(t);
                    if (t > s) {
                        newi = j
                        s = t
                    }
                    j++
                }
            }
            if (s == 0.0) {
                return false
            }
            if (newi != i) {
                w = -w

                // swap index elements
                k = index!![i]
                index[i] = index[newi]
                index[newi] = k

                // swap rows
                j = 0
                while (j < numColumns) {
                    t = this.oGet(newi, j).toDouble()
                    this.oSet(newi, j, this.oGet(i, j))
                    this.oSet(i, j, t.toFloat())
                    j++
                }
            }
            if (i < numRows) {
                d = (1.0f / this.oGet(i, i)).toDouble()
                j = i + 1
                while (j < numRows) {
                    this.timesAssign(j, i, d)
                    j++
                }
            }
            if (i < min - 1) {
                j = i + 1
                while (j < numRows) {
                    d = this.oGet(j, i).toDouble()
                    k = i + 1
                    while (k < numColumns) {
                        this.minusAssign(j, k, d * this.oGet(i, k))
                        k++
                    }
                    j++
                }
            }
            i++
        }
        if (det != null) {
            i = 0
            while (i < numRows) {
                w *= this.oGet(i, i).toDouble()
                i++
            }
            det[0] = w.toFloat() //TODO:check back ref
        }
        return true
    }

    /*
     ============
     idMatX::LU_UpdateRankOne

     Updates the in-place LU factorization to obtain the factors for the matrix: LU + alpha * v * w'
     ============
     */
    fun LU_UpdateRankOne(v: idVecX, w: idVecX, alpha: Float, index: IntArray?): Boolean {
        var i: Int
        var j: Int
        var diag: Float
        var beta: Float
        var p0: Float
        var p1: Float
        var d: Float
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)

//	y = (float *) _alloca16( v.GetSize() * sizeof( float ) );
//	z = (float *) _alloca16( w.GetSize() * sizeof( float ) );
        val y: FloatArray = FloatArray(v.GetSize())
        val z: FloatArray = FloatArray(w.GetSize())
        if (index != null) {
            i = 0
            while (i < numRows) {
                y[i] = alpha * v.p[index[i]]
                i++
            }
        } else {
            i = 0
            while (i < numRows) {
                y[i] = alpha * v.p[i]
                i++
            }
        }

//	memcpy( z, w.ToFloatPtr(), w.GetSize() * sizeof( float ) );
        System.arraycopy(w.ToFloatPtr(), 0, z, 0, w.GetSize())
        val max: Int = Lib.Min(numRows, numColumns)
        i = 0
        while (i < max) {
            diag = this.oGet(i, i)
            p0 = y[i]
            p1 = z[i]
            diag += p0 * p1
            if (diag == 0.0f) {
                return false
            }
            beta = p1 / diag
            this.oSet(i, i, diag)
            j = i + 1
            while (j < numColumns) {
                d = this.oGet(i, j)
                d += p0 * z[j]
                z[j] -= beta * d
                this.oSet(i, j, d)
                j++
            }
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i)
                y[j] -= p0 * d
                d += beta * y[j]
                this.oSet(j, i, d)
                j++
            }
            i++
        }
        return true
    }

    /*
     ============
     idMatX::LU_UpdateRowColumn

     Updates the in-place LU factorization to obtain the factors for the matrix:

     [ 0  a  0 ]
     LU + [ d  b  e ]
     [ 0  c  0 ]

     where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1], d = w[0,r-1], w[r] = 0.0f, e = w[r+1,numColumns-1]
     ============
     */
    fun LU_UpdateRowColumn(v: idVecX, w: idVecX, r: Int, index: IntArray?): Boolean {
//    #else
        var i: Int
        var j: Int
        val min: Int
        var rp: Int
        var diag: Float
        var beta0: Float
        var beta1: Float
        var p0: Float
        var p1: Float
        var q0: Float
        var q1: Float
        var d: Float
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        assert(r >= 0 && r < numColumns && r < numRows)
        assert(w.p[r] == 0.0f)
        val y0: FloatArray = FloatArray(v.GetSize())
        val z0: FloatArray = FloatArray(w.GetSize())
        val y1: FloatArray = FloatArray(v.GetSize())
        val z1: FloatArray = FloatArray(w.GetSize())
        if (index != null) {
            i = 0
            while (i < numRows) {
                y0[i] = v.p[index[i]]
                i++
            }
            rp = r
            i = 0
            while (i < numRows) {
                if (index[i] == r) {
                    rp = i
                    break
                }
                i++
            }
        } else {
            System.arraycopy(v.ToFloatPtr(), 0, y0, 0, v.GetSize())
            rp = r
        }

//	memset( y1, 0, v.GetSize() * sizeof( float ) );
        y1[rp] = 1.0f

//	memset( z0, 0, w.GetSize() * sizeof( float ) );
        z0[r] = 1.0f

//	memcpy( z1, w.ToFloatPtr(), w.GetSize() * sizeof( float ) );
        System.arraycopy(w.ToFloatPtr(), 0, z1, 0, w.GetSize())

        // update the beginning of the to be updated row and column
        min = Lib.Min(r, rp)
        i = 0
        while (i < min) {
            p0 = y0[i]
            beta1 = (z1[i] / this.oGet(i, i))
            this.plusAssign(i, r, p0)
            j = i + 1
            while (j < numColumns) {
                z1[j] -= beta1 * this.oGet(i, j)
                j++
            }
            j = i + 1
            while (j < numRows) {
                y0[j] -= p0 * this.oGet(j, i)
                j++
            }
            this.plusAssign(rp, i, beta1)
            i++
        }

        // update the lower right corner starting at r,r
        val max: Int = Lib.Min(numRows, numColumns)
        i = min
        while (i < max) {
            diag = this.oGet(i, i)
            p0 = y0[i]
            p1 = z0[i]
            diag += p0 * p1
            if (diag == 0.0f) {
                return false
            }
            beta0 = p1 / diag
            q0 = y1[i]
            q1 = z1[i]
            diag += q0 * q1
            if (diag == 0.0f) {
                return false
            }
            beta1 = q1 / diag
            this.oSet(i, i, diag)
            j = i + 1
            while (j < numColumns) {
                d = this.oGet(i, j)
                d += p0 * z0[j]
                z0[j] -= beta0 * d
                d += q0 * z1[j]
                z1[j] -= beta1 * d
                this.oSet(i, j, d)
                j++
            }
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i)
                y0[j] -= p0 * d
                d += beta0 * y0[j]
                y1[j] -= q0 * d
                d += beta1 * y1[j]
                this.oSet(j, i, d)
                j++
            }
            i++
        }
        return true
        //#endif
    }

    /*
     ============
     idMatX::LU_UpdateIncrement

     Updates the in-place LU factorization to obtain the factors for the matrix:

     [ A  a ]
     [ c  b ]

     where: a = v[0,numRows-1], b = v[numRows], c = w[0,numColumns-1], w[numColumns] = 0
     ============
     */
    fun LU_UpdateIncrement(v: idVecX, w: idVecX, index: IntArray?): Boolean {
        var j: Int
        var sum: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        assert(w.GetSize() >= numColumns + 1)
        ChangeSize(numRows + 1, numColumns + 1, true)

        // add row to L
        var i: Int = 0
        while (i < numRows - 1) {
            sum = w.p[i]
            j = 0
            while (j < i) {
                sum -= this.oGet(numRows - 1, j) * this.oGet(j, i)
                j++
            }
            this.oSet(numRows - 1, i, sum / this.oGet(i, i))
            i++
        }

        // add row to the permutation index
        if (index != null) {
            index[numRows - 1] = numRows - 1 //TODO:check back reference, non final array
        }

        // add column to U
        i = 0
        while (i < numRows) {
            sum = if (index != null) {
                v.p[index[i]]
            } else {
                v.p[i]
            }
            j = 0
            while (j < i) {
                sum -= this.oGet(i, j) * this.oGet(j, numRows - 1)
                j++
            }
            this.oSet(i, numRows - 1, sum)
            i++
        }
        return true
    }

    /*
     ============
     idMatX::LU_UpdateDecrement

     Updates the in-place LU factorization to obtain the factors for the matrix with row r and column r removed.
     v and w should store the column and row of the original matrix respectively.
     If index != NULL then u should store row index[r] of the original matrix. If index == NULL then u = w.
     ============
     */
    fun LU_UpdateDecrement(v: idVecX, w: idVecX, u: idVecX, r: Int, index: IntArray?): Boolean {
        var i: Int
        var p: Int
        var v1 = idVecX()
        var w1 = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        assert(r >= 0 && r < numRows && r < numColumns)
        v1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        w1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        if (index != null) {

            // find the pivot row
            p = 0.also { i = it }
            while (i < numRows) {
                if (index[i] == r) {
                    p = i
                    break
                }
                i++
            }

            // update the row and column to identity
            v1 = v.unaryMinus()
            w1 = u.unaryMinus()
            if (p != r) {
                idSwap(v1.p, v1.p, index[r], index[p])
                idSwap(index, index, r, p)
            }
            v1.p[r] += 1.0f
            w1.p[r] = 0.0f
            if (!LU_UpdateRowColumn(v1, w1, r, index)) {
                return false
            }
            if (p != r) {
                if (abs(u.p[p]) < 1e-4f) {
                    // NOTE: an additional row interchange is required for numerical stability
                }

                // move row index[r] of the original matrix to row index[p] of the original matrix
                v1.Zero()
                v1.p[index[p]] = 1.0f
                w1 = u - w
                if (!LU_UpdateRankOne(v1, w1, 1.0f, index)) {
                    return false
                }
            }

            // remove the row from the permutation index
            i = r
            while (i < numRows - 1) {
                index[i] = index[i + 1]
                i++
            }
            i = 0
            while (i < numRows - 1) {
                if (index[i] > r) {
                    index[i]--
                }
                i++
            }
        } else {
            v1 = v.unaryMinus()
            w1 = w.unaryMinus()
            v1.p[r] += 1.0f
            w1.p[r] = 0.0f
            if (!LU_UpdateRowColumn(v1, w1, r, index)) {
                return false
            }
        }

        // physically remove the row and column
        Update_Decrement(r)
        return true
    }

    /*
     ============
     idMatX::LU_Solve

     Solve Ax = b with A factored in-place as: LU
     ============
     */
    fun LU_Solve(x: idVecX, b: idVecX, index: IntArray?) {
        var j: Int
        var sum: Float
        assert(x.GetSize() == numColumns && b.GetSize() == numRows)

        // solve L
        var i: Int = 0
        while (i < numRows) {
            if (index != null) {
                sum = b.p[index[i]]
            } else {
                sum = b.p[i]
            }
            j = 0
            while (j < i) {
                sum -= (this.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = sum
            i++
        }

        // solve U
        i = numRows - 1
        while (i >= 0) {
            sum = x.p[i]
            j = i + 1
            while (j < numRows) {
                sum -= (this.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = (sum / this.oGet(i, i))
            i--
        }
    }

    /**
     * ============ idMatX::LU_Inverse
     *
     *
     * Calculates the inverse of the matrix which is factored in-place as LU
     * ============
     */
    fun LU_Inverse(inv: idMatX, index: IntArray?) {
        var j: Int
        val x = idVecX()
        val b = idVecX()
        assert(numRows == numColumns)
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        inv.SetSize(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            LU_Solve(x, b, index)
            j = 0
            while (j < numRows) {
                inv.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
    }

    /**
     * ============ idMatX::LU_UnpackFactors
     *
     *
     * Unpacks the in-place LU factorization. ============
     */
    fun LU_UnpackFactors(L: idMatX, U: idMatX) {
        var j: Int
        L.Zero(numRows, numColumns)
        U.Zero(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < i) {
                L.oSet(i, j, this.oGet(i, j))
                j++
            }
            L.oSet(i, i, 1.0f)
            j = i
            while (j < numColumns) {
                U.oSet(i, j, this.oGet(i, j))
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::LU_MultiplyFactors
     *
     *
     * Multiplies the factors of the in-place LU factorization to form the
     * original matrix. ============
     */
    fun LU_MultiplyFactors(m: idMatX, index: IntArray) {
        var rp: Int
        var i: Int
        var j: Int
        var sum: Double
        m.SetSize(numRows, numColumns)
        var r: Int = 0
        while (r < numRows) {
            rp = if (index != null) {
                index[r]
            } else {
                r
            }

            // calculate row of matrix
            i = 0
            while (i < numColumns) {
                sum = if (i >= r) {
                    this.oGet(r, i).toDouble()
                } else {
                    0.0
                }
                j = 0
                while (j <= i && j < r) {
                    sum += (this.oGet(r, j) * this.oGet(j, i)).toDouble()
                    j++
                }
                m.oSet(rp, i, sum.toFloat())
                i++
            }
            r++
        }
    }

    /**
     * ============ idMatX::QR_Factor
     *
     *
     * in-place factorization: QR Q is an orthogonal matrix represented as a
     * product of Householder matrices stored in the lower triangle and c. R is
     * a triangular matrix stored in the upper triangle except for the diagonal
     * elements which are stored in d. The initial matrix has to be square.
     * ============
     */
    fun QR_Factor(c: idVecX, d: idVecX): Boolean { // factor in-place: Q * R
        var i: Int
        var j: Int
        var scale: Double
        var s: Double
        var t: Double
        var sum: Double
        var singular = false
        assert(numRows == numColumns)
        assert(c.GetSize() >= numRows && d.GetSize() >= numRows)
        var k: Int = 0
        while (k < numRows - 1) {
            scale = 0.0
            i = k
            while (i < numRows) {
                s = abs(this.oGet(i, k)).toDouble()
                if (s > scale) {
                    scale = s
                }
                i++
            }
            if (scale == 0.0) {
                singular = true
                d.p[k] = 0.0f
                c.p[k] = d.p[k]
            } else {
                s = 1.0f / scale
                i = k
                while (i < numRows) {
                    this.timesAssign(i, k, s)
                    i++
                }
                sum = 0.0
                i = k
                while (i < numRows) {
                    s = this.oGet(i, k).toDouble()
                    sum += s * s
                    i++
                }
                s = idMath.Sqrt(sum.toFloat()).toDouble()
                if (this.oGet(k, k) < 0.0f) {
                    s = -s
                }
                this.plusAssign(k, k, s)
                c.p[k] = (s * this.oGet(k, k)).toFloat()
                d.p[k] = (-scale * s).toFloat()
                j = k + 1
                while (j < numRows) {
                    sum = 0.0
                    i = k
                    while (i < numRows) {
                        sum += (this.oGet(i, k) * this.oGet(i, j)).toDouble()
                        i++
                    }
                    t = sum / c.p[k]
                    i = k
                    while (i < numRows) {
                        this.minusAssign(i, j, t * this.oGet(i, k))
                        i++
                    }
                    j++
                }
            }
            k++
        }
        d.p[numRows - 1] = this.oGet(numRows - 1, numRows - 1)
        if (d.p[numRows - 1] == 0.0f) {
            singular = true
        }
        return !singular
    }

    /**
     * ============ idMatX::QR_Rotate
     *
     *
     * Performs a Jacobi rotation on the rows i and i+1 of the unpacked QR
     * factors. ============
     */
    fun QR_UpdateRankOne(R: idMatX, v: idVecX, w: idVecX, alpha: Float): Boolean {
        var i: Int
        var f: Float
        val u = idVecX()
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        u.SetData(v.GetSize(), idVecX.VECX_ALLOCA(v.GetSize()))
        TransposeMultiply(u, v)
        u.timesAssign(alpha)
        var k: Int = v.GetSize() - 1
        while (k > 0) {
            if (u.p[k] != 0.0f) {
                break
            }
            k--
        }
        i = k - 1
        while (i >= 0) {
            QR_Rotate(R, i, u.p[i], -u.p[i + 1])
            if (u.p[i] == 0.0f) {
                u.p[i] = abs(u.p[i + 1])
            } else if (abs(u.p[i]) > abs(u.p[i + 1])) {
                f = u.p[i + 1] / u.p[i]
                u.p[i] = abs(u.p[i]) * idMath.Sqrt(1.0f + f * f)
            } else {
                f = u.p[i] / u.p[i + 1]
                u.p[i] = abs(u.p[i + 1]) * idMath.Sqrt(1.0f + f * f)
            }
            i--
        }
        i = 0
        while (i < v.GetSize()) {
            R.plusAssign(0, i, u.p[0] * w.p[i])
            i++
        }
        i = 0
        while (i < k) {
            QR_Rotate(R, i, -R.oGet(i, i), R.oGet(i + 1, i))
            i++
        }
        return true
    }

    /**
     * ============ idMatX::QR_UpdateRowColumn
     *
     *
     * Updates the unpacked QR factorization to obtain the factors for the
     * matrix:
     *
     *
     * [ 0 a 0 ]
     * QR + [ d b e ] [ 0 c 0 ]
     *
     *
     * where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1], d = w[0,r-1], w[r] =
     * 0.0f, e = w[r+1,numColumns-1] ============
     */
    fun QR_UpdateRowColumn(R: idMatX, v: idVecX, w: idVecX, r: Int): Boolean {
        val s = idVecX()
        assert(v.GetSize() >= numColumns)
        assert(w.GetSize() >= numRows)
        assert(r >= 0 && r < numRows && r < numColumns)
        assert(w.p[r] == 0.0f)
        s.SetData(
            Lib.Max(numRows, numColumns),
            idVecX.VECX_ALLOCA(Lib.Max(numRows, numColumns))
        )
        s.Zero()
        s.p[r] = 1.0f
        return if (!QR_UpdateRankOne(R, v, s, 1.0f)) {
            false
        } else QR_UpdateRankOne(R, s, w, 1.0f)
    }

    /**
     * ============ idMatX::QR_UpdateIncrement
     *
     *
     * Updates the unpacked QR factorization to obtain the factors for the
     * matrix:
     *
     *
     * [ A a ]
     * [ c b ]
     *
     *
     * where: a = v[0,numRows-1], b = v[numRows], c = w[0,numColumns-1],
     * w[numColumns] = 0 ============
     */
    fun QR_UpdateIncrement(R: idMatX, v: idVecX, w: idVecX): Boolean {
        var v2: idVecX = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        assert(w.GetSize() >= numColumns + 1)
        ChangeSize(numRows + 1, numColumns + 1, true)
        this.oSet(numRows - 1, numRows - 1, 1.0f)
        R.ChangeSize(R.numRows + 1, R.numColumns + 1, true)
        R.oSet(R.numRows - 1, R.numRows - 1, 1.0f)
        v2.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        v2 = v
        v2.p[numRows - 1] -= 1.0f
        return QR_UpdateRowColumn(R, v2, w, numRows - 1)
    }

    /**
     * ============ idMatX::QR_UpdateDecrement
     *
     *
     * Updates the unpacked QR factorization to obtain the factors for the
     * matrix with row r and column r removed. v and w should store the column
     * and row of the original matrix respectively. ============
     */
    fun QR_UpdateDecrement(R: idMatX, v: idVecX, w: idVecX, r: Int): Boolean {
        var v1: idVecX = idVecX()
        var w1: idVecX = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(w.GetSize() >= numColumns)
        assert(r >= 0 && r < numRows && r < numColumns)
        v1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        w1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))

        // update the row and column to identity
        v1 = v.unaryMinus()
        w1 = w.unaryMinus()
        v1.p[r] += 1.0f
        w1.p[r] = 0.0f
        if (!QR_UpdateRowColumn(R, v1, w1, r)) {
            return false
        }

        // physically remove the row and column
        Update_Decrement(r)
        R.Update_Decrement(r)
        return true
    }

    fun QR_Solve(x: idVecX, b: idVecX, c: idVecX, d: idVecX) {
        var j: Int
        var sum: Float
        var t: Float
        assert(numRows == numColumns)
        assert(x.GetSize() >= numRows && b.GetSize() >= numRows)
        assert(c.GetSize() >= numRows && d.GetSize() >= numRows)
        var i: Int = 0
        while (i < numRows) {
            x.p[i] = b.p[i]
            i++
        }

        // multiply b with transpose of Q
        i = 0
        while (i < numRows - 1) {
            sum = 0.0f
            j = i
            while (j < numRows) {
                sum += (this.oGet(j, i) * x.p[j])
                j++
            }
            t = sum / c.p[i]
            j = i
            while (j < numRows) {
                x.p[j] -= t * this.oGet(j, i)
                j++
            }
            i++
        }

        // backsubstitution with R
        i = numRows - 1
        while (i >= 0) {
            sum = x.p[i]
            j = i + 1
            while (j < numRows) {
                sum -= (this.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = (sum / d.p[i])
            i--
        }
    }

    /**
     * ============ idMatX::QR_Solve
     *
     *
     * Solve Ax = b with A factored as: QR ============
     */
    fun QR_Solve(x: idVecX, b: idVecX, R: idMatX) {
        var j: Int
        var sum: Float
        assert(numRows == numColumns)

        // multiply b with transpose of Q
        TransposeMultiply(x, b)

        // backsubstitution with R
        var i: Int = numRows - 1
        while (i >= 0) {
            sum = x.p[i]
            j = i + 1
            while (j < numRows) {
                sum -= (R.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = (sum / R.oGet(i, i))
            i--
        }
    }

    /**
     * ============ idMatX::QR_Inverse
     *
     *
     * Calculates the inverse of the matrix which is factored in-place as: QR
     * ============
     */
    fun QR_Inverse(inv: idMatX, c: idVecX, d: idVecX) {
        var j: Int
        val x = idVecX()
        val b = idVecX()
        assert(numRows == numColumns)
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        inv.SetSize(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            QR_Solve(x, b, c, d)
            j = 0
            while (j < numRows) {
                inv.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
    }

    /**
     * ============ idMatX::QR_UnpackFactors
     *
     *
     * Unpacks the in-place QR factorization. ============
     */
    fun QR_UnpackFactors(Q: idMatX, R: idMatX, c: idVecX, d: idVecX) {
        var j: Int
        var k: Int
        var sum: Float
        Q.Identity(numRows, numColumns)
        var i: Int = 0
        while (i < numColumns - 1) {
            if (c.p[i] == 0.0f) {
                i++
                continue
            }
            j = 0
            while (j < numRows) {
                sum = 0.0f
                k = i
                while (k < numColumns) {
                    sum += (this.oGet(k, i) * Q.oGet(j, k))
                    k++
                }
                sum /= c.p[i]
                k = i
                while (k < numColumns) {
                    Q.minusAssign(j, k, sum * this.oGet(k, i))
                    k++
                }
                j++
            }
            i++
        }
        R.Zero(numRows, numColumns)
        i = 0
        while (i < numRows) {
            R.oSet(i, i, d.p[i])
            j = i + 1
            while (j < numColumns) {
                R.oSet(i, j, this.oGet(i, j))
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::QR_MultiplyFactors
     *
     *
     * Multiplies the factors of the in-place QR factorization to form the
     * original matrix. ============
     */
    fun QR_MultiplyFactors(m: idMatX, c: idVecX, d: idVecX) {
        var j: Int
        var k: Int
        var sum: Float
        val Q = idMatX()
        Q.Identity(numRows, numColumns)
        var i: Int = 0
        while (i < numColumns - 1) {
            if (c.p[i] == 0.0f) {
                i++
                continue
            }
            j = 0
            while (j < numRows) {
                sum = 0.0f
                k = i
                while (k < numColumns) {
                    sum += (this.oGet(k, i) * Q.oGet(j, k))
                    k++
                }
                sum /= c.p[i]
                k = i
                while (k < numColumns) {
                    Q.minusAssign(j, k, sum * this.oGet(k, i))
                    k++
                }
                j++
            }
            i++
        }
        i = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                sum = (Q.oGet(i, j) * d.p[i])
                k = 0
                while (k < i) {
                    sum += (Q.oGet(i, k) * this.oGet(j, k))
                    k++
                }
                m.oSet(i, j, sum)
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::SVD_Factor
     *
     *
     * in-place factorization: U * Diag(w) * V.Transpose() known as the Singular
     * Value Decomposition. U is a column-orthogonal matrix which overwrites the
     * original matrix. w is a diagonal matrix with all elements >= 0 which are
     * the singular values. V is the transpose of an orthogonal matrix.
     * ============
     */
    fun SVD_Factor(w: idVecX, V: idMatX): Boolean // factor in-place: U * Diag(w) * V.Transpose()
    {
        var flag: Int
        var i: Int
        var its: Int
        var j: Int
        var jj: Int
        var l: Int
        var nm: Int
        var c: Float
        var f: Float
        var h: Float
        var s: Float
        var x: Float
        var y: Float
        var z: Float
        var r: Float
        var g = 0.0f
        val anorm = floatArrayOf(0f)
        val rv1 = idVecX()
        if (numRows < numColumns) {
            return false
        }
        rv1.SetData(numColumns, idVecX.VECX_ALLOCA(numColumns))
        rv1.Zero()
        w.Zero(numColumns)
        V.Zero(numColumns, numColumns)
        SVD_BiDiag(w, rv1, anorm)
        SVD_InitialWV(w, V, rv1)
        var k: Int = numColumns - 1
        while (k >= 0) {
            its = 1
            while (its <= 30) {
                flag = 1
                nm = 0
                l = k
                while (l >= 0) {
                    nm = l - 1
                    if (abs(rv1.p[l]) + anorm[0] == anorm[0] /* idMath::Fabs( rv1.p[l] ) < idMath::FLT_EPSILON */) {
                        flag = 0
                        break
                    }
                    if (abs(w.p[nm]) + anorm[0] == anorm[0] /* idMath::Fabs( w[nm] ) < idMath::FLT_EPSILON */) {
                        break
                    }
                    l--
                }
                if (flag != 0) {
                    c = 0.0f
                    s = 1.0f
                    i = l
                    while (i <= k) {
                        f = s * rv1.p[i]
                        if (abs(f) + anorm[0] != anorm[0] /* idMath::Fabs( f ) > idMath::FLT_EPSILON */) {
                            g = w.p[i]
                            h = Pythag(f, g)
                            w.p[i] = h
                            h = 1.0f / h
                            c = g * h
                            s = -f * h
                            j = 0
                            while (j < numRows) {
                                y = this.oGet(j, nm)
                                z = this.oGet(j, i)
                                this.oSet(j, nm, (y * c + z * s))
                                this.oSet(j, i, (z * c - y * s))
                                j++
                            }
                        }
                        i++
                    }
                }
                z = w.p[k]
                if (l == k) {
                    if (z < 0.0f) {
                        w.p[k] = -z
                        j = 0
                        while (j < numColumns) {
                            V.oNegative(j, k)
                            j++
                        }
                    }
                    break
                }
                if (its == 30) {
                    return false // no convergence
                }
                x = w.p[l]
                nm = k - 1
                y = w.p[nm]
                g = rv1.p[nm]
                h = rv1.p[k]
                f = ((y - z) * (y + z) + (g - h) * (g + h)) / (2.0f * h * y)
                g = Pythag(f, 1.0f)
                r = if (f >= 0.0f) g else -g
                f = ((x - z) * (x + z) + h * (y / (f + r) - h)) / x
                s = 1.0f
                c = s
                j = l
                while (j <= nm) {
                    i = j + 1
                    g = rv1.p[i]
                    y = w.p[i]
                    h = s * g
                    g = c * g
                    z = Pythag(f, h)
                    rv1.p[j] = z
                    c = f / z
                    s = h / z
                    f = x * c + g * s
                    g = g * c - x * s
                    h = y * s
                    y = y * c
                    jj = 0
                    while (jj < numColumns) {
                        x = V.oGet(jj, j)
                        z = V.oGet(jj, i)
                        V.oSet(jj, j, (x * c + z * s))
                        V.oSet(jj, i, (z * c - x * s))
                        jj++
                    }
                    z = Pythag(f, h)
                    w.p[j] = z
                    if (z != 0.0f) {
                        z = 1.0f / z
                        c = f * z
                        s = h * z
                    }
                    f = c * g + s * y
                    x = c * y - s * g
                    jj = 0
                    while (jj < numRows) {
                        y = this.oGet(jj, j)
                        z = this.oGet(jj, i)
                        this.oSet(jj, j, (y * c + z * s))
                        this.oSet(jj, i, (z * c - y * s))
                        jj++
                    }
                    j++
                }
                rv1.p[l] = 0.0f
                rv1.p[k] = f
                w.p[k] = x
                its++
            }
            k--
        }
        return true
    }
    /*
     ============
     idMatX::SVD_Inverse

     Calculates the inverse of the matrix which is factored in-place as: U * Diag(w) * V.Transpose()
     ============
     */
    /**
     * ============ idMatX::SVD_Solve
     *
     *
     * Solve Ax = b with A factored as: U * Diag(w) * V.Transpose() ============
     */
    fun SVD_Solve(x: idVecX, b: idVecX, w: idVecX, V: idMatX) {
        var j: Int
        var sum: Float
        val tmp = idVecX()
        assert(x.GetSize() >= numColumns)
        assert(b.GetSize() >= numColumns)
        assert(w.GetSize() == numColumns)
        assert(V.GetNumRows() == numColumns && V.GetNumColumns() == numColumns)
        tmp.SetData(numColumns, idVecX.VECX_ALLOCA(numColumns))
        var i: Int = 0
        while (i < numColumns) {
            sum = 0.0f
            if (w.p[i] >= idMath.FLT_EPSILON) {
                j = 0
                while (j < numRows) {
                    sum += (this.oGet(j, i) * b.p[j])
                    j++
                }
                sum /= w.p[i]
            }
            tmp.p[i] = sum
            i++
        }
        i = 0
        while (i < numColumns) {
            sum = 0.0f
            j = 0
            while (j < numColumns) {
                sum += (V.oGet(i, j) * tmp.p[j])
                j++
            }
            x.p[i] = sum
            i++
        }
    }

    fun SVD_Inverse(inv: idMatX, w: idVecX, V: idMatX) {
        var j: Int
        var k: Int
        var wi: Float
        var sum: Float
        assert(numRows == numColumns)
        val V2: idMatX = V //= new idMatX();

        // V * [diag(1/w[i])]
        var i: Int = 0
        while (i < numRows) {
            wi = w.p[i]
            wi = if (wi < idMath.FLT_EPSILON) 0.0f else 1.0f / wi
            j = 0
            while (j < numColumns) {
                V2.timesAssign(j, i, wi)
                j++
            }
            i++
        }

        // V * [diag(1/w[i])] * Ut
        i = 0
        while (i < numRows) {
            j = 0
            while (j < numColumns) {
                sum = (V2.oGet(i, 0) * this.oGet(j, 0))
                k = 1
                while (k < numColumns) {
                    sum += (V2.oGet(i, k) * this.oGet(j, k))
                    k++
                }
                inv.oSet(i, j, sum)
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::SVD_MultiplyFactors
     *
     *
     * Multiplies the factors of the in-place SVD factorization to form the
     * original matrix. ============
     */
    fun SVD_MultiplyFactors(m: idMatX, w: idVecX, V: idMatX) {
        var i: Int
        var j: Int
        var sum: Float
        m.SetSize(numRows, V.GetNumRows())
        var r: Int = 0
        while (r < numRows) {

            // calculate row of matrix
            if (w.p[r] >= idMath.FLT_EPSILON) {
                i = 0
                while (i < V.GetNumRows()) {
                    sum = 0.0f
                    j = 0
                    while (j < numColumns) {
                        sum += (this.oGet(r, j) * V.oGet(i, j))
                        j++
                    }
                    m.oSet(r, i, (sum * w.p[r]))
                    i++
                }
            } else {
                i = 0
                while (i < V.GetNumRows()) {
                    m.oSet(r, i, 0.0f)
                    i++
                }
            }
            r++
        }
    }

    /**
     * ============ idMatX::Cholesky_Factor
     *
     *
     * in-place Cholesky factorization: LL' L is a triangular matrix stored in
     * the lower triangle. The upper triangle is not cleared. The initial matrix
     * has to be symmetric positive definite. ============
     */
    fun Cholesky_Factor(): Boolean { // factor in-place: L * L.Transpose()
        var j: Int
        var k: Int
        val invSqrt = FloatArray(numRows)
        var sum: Float
        assert(numRows == numColumns)

//	invSqrt = (float *) _alloca16( numRows * sizeof( float ) );
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < i) {
                sum = this.oGet(i, j)
                k = 0
                while (k < j) {
                    sum -= (this.oGet(i, k) * this.oGet(j, k))
                    k++
                }
                this.oSet(i, j, (sum * invSqrt[j]))
                j++
            }
            sum = this.oGet(i, i)
            k = 0
            while (k < i) {
                sum -= (this.oGet(i, k) * this.oGet(i, k))
                k++
            }
            if (sum <= 0.0f) {
                return false
            }
            invSqrt[i] = idMath.InvSqrt(sum)
            this.oSet(i, i, (invSqrt[i] * sum))
            i++
        }
        return true
    }

    /**
     * ============ idMatX::Cholesky_UpdateRankOne
     *
     *
     * Updates the in-place Cholesky factorization to obtain the factors for the
     * matrix: LL' + alpha * v * v' If offset > 0 only the lower right corner
     * starting at (offset, offset) is updated. ============
     */
    @JvmOverloads
    fun Cholesky_UpdateRankOne(v: idVecX, alpha: Float, offset: Int = 0): Boolean {
        var alpha = alpha
        var j: Int
        var diag: Float
        var invDiag: Float
        var diagSqr: Float
        var newDiag: Float
        var newDiagSqr: Float
        var beta: Float
        var p: Float
        var d: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(offset in 0 until numRows)

//	y = (float *) _alloca16( v.GetSize() * sizeof( float ) );
//	memcpy( y, v.ToFloatPtr(), v.GetSize() * sizeof( float ) );
        val y: FloatArray = v.ToFloatPtr()
        var i: Int = offset
        while (i < numColumns) {
            p = y[i]
            diag = this.oGet(i, i)
            invDiag = 1.0f / diag
            diagSqr = diag * diag
            newDiagSqr = diagSqr + alpha * p * p
            if (newDiagSqr <= 0.0f) {
                return false
            }
            this.oSet(i, i, idMath.Sqrt(newDiagSqr).also { newDiag = it }.toFloat())
            alpha /= newDiagSqr
            beta = p * alpha
            alpha *= diagSqr
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i) * invDiag
                y[j] -= p * d
                d += beta * y[j]
                this.oSet(j, i, (d * newDiag))
                j++
            }
            i++
        }
        return true
    }

    /*
     ============
     idMatX::Cholesky_UpdateRowColumn

     Updates the in-place Cholesky factorization to obtain the factors for the matrix:

     [ 0  a  0 ]
     LL' + [ a  b  c ]
     [ 0  c  0 ]

     where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1]
     ============
     */
    fun Cholesky_UpdateRowColumn(v: idVecX, r: Int): Boolean {
        var i: Int
        var j: Int
        var sum: Float
        val original: FloatArray
        val y: FloatArray
        val addSub = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(r >= 0 && r < numRows)

//	addSub.SetData( numColumns, (float *) _alloca16( numColumns * sizeof( float ) ) );
        addSub.SetData(numColumns, FloatArray(numColumns))
        if (r == 0) {
            if (numColumns == 1) {
                val v0 = v.p[0]
                sum = this.oGet(0, 0)
                sum *= sum
                sum += v0
                if (sum <= 0.0f) {
                    return false
                }
                this.oSet(0, 0, idMath.Sqrt(sum))
                return true
            }
            i = 0
            while (i < numColumns) {
                addSub.p[i] = v.p[i]
                i++
            }
        } else {

//		original = (float *) _alloca16( numColumns * sizeof( float ) );
//		y = (float *) _alloca16( numColumns * sizeof( float ) );
            original = FloatArray(numColumns)
            y = FloatArray(numColumns)

            // calculate original row/column of matrix
            i = 0
            while (i < numRows) {
                sum = 0.0f
                j = 0
                while (j <= i) {
                    sum += (this.oGet(r, j) * this.oGet(i, j))
                    j++
                }
                original[i] = sum
                i++
            }

            // solve for y in L * y = original + v
            i = 0
            while (i < r) {
                sum = (original[i] + v.p[i])
                j = 0
                while (j < i) {
                    sum -= (this.oGet(r, j) * this.oGet(i, j))
                    j++
                }
                this.oSet(r, i, (sum / this.oGet(i, i)))
                i++
            }

            // if the last row/column of the matrix is updated
            if (r == numColumns - 1) {
                // only calculate new diagonal
                sum = (original[r] + v.p[r])
                j = 0
                while (j < r) {
                    sum -= (this.oGet(r, j) * this.oGet(r, j))
                    j++
                }
                if (sum <= 0.0f) {
                    return false
                }
                this.oSet(r, r, idMath.Sqrt(sum))
                return true
            }

            // calculate the row/column to be added to the lower right sub matrix starting at (r, r)
            i = r
            while (i < numColumns) {
                sum = 0.0f
                j = 0
                while (j <= r) {
                    sum += (this.oGet(r, j) * this.oGet(i, j))
                    j++
                }
                addSub.p[i] = (v.p[i] - (sum - original[i]))
                i++
            }
        }

        // add row/column to the lower right sub matrix starting at (r, r)
//#else
        var diag: Float
        var invDiag: Float
        var diagSqr: Float
        var newDiag: Float
        var newDiagSqr: Float
        var beta1: Float
        var beta2: Float
        var p1: Float
        var p2: Float
        var d: Float

//	v1 = (float *) _alloca16( numColumns * sizeof( float ) );
//	v2 = (float *) _alloca16( numColumns * sizeof( float ) );
        val v1: FloatArray = FloatArray(numColumns)
        val v2: FloatArray = FloatArray(numColumns)
        d = idMath.SQRT_1OVER2
        v1[r] = ((0.5f * addSub.p[r] + 1.0f) * d)
        v2[r] = ((0.5f * addSub.p[r] - 1.0f) * d)
        i = r + 1
        while (i < numColumns) {
            v2[i] = (addSub.p[i] * d)
            v1[i] = v2[i]
            i++
        }
        var alpha1: Float = 1.0f
        var alpha2: Float = -1.0f

        // simultaneous update/downdate of the sub matrix starting at (r, r)
        i = r
        while (i < numColumns) {
            p1 = v1[i]
            diag = this.oGet(i, i)
            invDiag = 1.0f / diag
            diagSqr = diag * diag
            newDiagSqr = diagSqr + alpha1 * p1 * p1
            if (newDiagSqr <= 0.0f) {
                return false
            }
            alpha1 /= newDiagSqr
            beta1 = p1 * alpha1
            alpha1 *= diagSqr
            p2 = v2[i]
            diagSqr = newDiagSqr
            newDiagSqr = diagSqr + alpha2 * p2 * p2
            if (newDiagSqr <= 0.0f) {
                return false
            }
            this.oSet(i, i, idMath.Sqrt(newDiagSqr).also { newDiag = it }.toFloat())
            alpha2 /= newDiagSqr
            beta2 = p2 * alpha2
            alpha2 *= diagSqr
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i) * invDiag
                v1[j] -= p1 * d
                d += beta1 * v1[j]
                v2[j] -= p2 * d
                d += beta2 * v2[j]
                this.oSet(j, i, (d * newDiag))
                j++
            }
            i++
        }

//#endif
        return true
    }

    /**
     * ============ idMatX::Cholesky_UpdateIncrement
     *
     *
     * Updates the in-place Cholesky factorization to obtain the factors for the
     * matrix:
     *
     *
     * [ A a ]
     * [ a b ]
     *
     *
     * where: a = v[0,numRows-1], b = v[numRows] ============
     */
    fun Cholesky_UpdateIncrement(v: idVecX): Boolean {
        var j: Int
        var sum: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        ChangeSize(numRows + 1, numColumns + 1, false)

//	x = (float *) _alloca16( numRows * sizeof( float ) );
        val x: FloatArray = FloatArray(numRows)

        // solve for x in L * x = v
        var i: Int = 0
        while (i < numRows - 1) {
            sum = v.p[i]
            j = 0
            while (j < i) {
                sum -= (this.oGet(i, j) * x[j])
                j++
            }
            x[i] = (sum / this.oGet(i, i))
            i++
        }

        // calculate new row of L and calculate the square of the diagonal entry
        sum = v.p[numRows - 1]
        i = 0
        while (i < numRows - 1) {
            this.oSet(numRows - 1, i, x[i])
            sum -= (x[i] * x[i])
            i++
        }
        if (sum <= 0.0f) {
            return false
        }

        // store the diagonal entry
        this.oSet(numRows - 1, numRows - 1, idMath.Sqrt(sum))
        return true
    }

    /**
     * ============ idMatX::Cholesky_UpdateDecrement
     *
     *
     * Updates the in-place Cholesky factorization to obtain the factors for the
     * matrix with row r and column r removed. v should store the row of the
     * original matrix. ============
     */
    fun Cholesky_UpdateDecrement(v: idVecX, r: Int): Boolean {
        var v1: idVecX = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(r >= 0 && r < numRows)
        v1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))

        // update the row and column to identity
        v1 = v.unaryMinus()
        v1.p[r] += 1.0f

        // NOTE:	msvc compiler bug: the this pointer stored in edi is expected to stay
        //			untouched when calling Cholesky_UpdateRowColumn in the if statement
//#if 0
//	if ( !Cholesky_UpdateRowColumn( v1, r ) ) {
//#else
        val ret = Cholesky_UpdateRowColumn(v1, r)
        if (!ret) {
//#endif
            return false
        }

        // physically remove the row and column
        Update_Decrement(r)
        return true
    }

    /**
     * ============ idMatX::Cholesky_Solve
     *
     *
     * Solve Ax = b with A factored in-place as: LL' ============
     */
    fun Cholesky_Solve(x: idVecX, b: idVecX) {
        var j: Int
        var sum: Float
        assert(numRows == numColumns)
        assert(x.GetSize() >= numRows && b.GetSize() >= numRows)

        // solve L
        var i: Int = 0
        while (i < numRows) {
            sum = b.p[i]
            j = 0
            while (j < i) {
                sum -= (this.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = (sum / this.oGet(i, i))
            i++
        }

        // solve Lt
        i = numRows - 1
        while (i >= 0) {
            sum = x.p[i]
            j = i + 1
            while (j < numRows) {
                sum -= (this.oGet(j, i) * x.p[j])
                j++
            }
            x.p[i] = (sum / this.oGet(i, i))
            i--
        }
    }

    /**
     * ============ idMatX::Cholesky_Inverse
     *
     *
     * Calculates the inverse of the matrix which is factored in-place as: LL'
     * ============
     */
    fun Cholesky_Inverse(inv: idMatX) {
        var j: Int
        val x = idVecX()
        val b = idVecX()
        assert(numRows == numColumns)
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        inv.SetSize(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            Cholesky_Solve(x, b)
            j = 0
            while (j < numRows) {
                inv.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
    }

    /**
     * ============ idMatX::Cholesky_MultiplyFactors
     *
     *
     * Multiplies the factors of the in-place Cholesky factorization to form the
     * original matrix. ============
     */
    fun Cholesky_MultiplyFactors(m: idMatX) {
        var i: Int
        var j: Int
        var sum: Double
        m.SetSize(numRows, numColumns)
        var r: Int = 0
        while (r < numRows) {
            // calculate row of matrix
            i = 0
            while (i < numRows) {
                sum = 0.0
                j = 0
                while (j <= i && j <= r) {
                    sum += (this.oGet(r, j) * this.oGet(i, j)).toDouble()
                    j++
                }
                m.oSet(r, i, sum.toFloat())
                i++
            }
            r++
        }
    }

    /*
     ============
     idMatX::LDLT_Factor

     in-place factorization: LDL'
     L is a triangular matrix stored in the lower triangle.
     L has ones on the diagonal that are not stored.
     D is a diagonal matrix stored on the diagonal.
     The upper triangle is not cleared.
     The initial matrix has to be symmetric.
     ============
     */
    fun LDLT_Factor(): Boolean { // factor in-place: L * D * L.Transpose()
        var j: Int
        var k: Int
        var d: Double
        var sum: Double
        assert(numRows == numColumns)

//	v = (float *) _alloca16( numRows * sizeof( float ) );
        val v: FloatArray = FloatArray(numRows)
        var i: Int = 0
        while (i < numRows) {
            sum = this.oGet(i, i).toDouble()
            j = 0
            while (j < i) {
                d = this.oGet(i, j).toDouble()
                v[j] = (this.oGet(j, j) * d).toFloat()
                sum -= v[j] * d
                j++
            }
            if (sum == 0.0) {
                return false
            }
            this.oSet(i, i, sum.toFloat())
            d = 1.0f / sum
            j = i + 1
            while (j < numRows) {
                sum = this.oGet(j, i).toDouble()
                k = 0
                while (k < i) {
                    sum -= (this.oGet(j, k) * v[k]).toDouble()
                    k++
                }
                this.oSet(j, i, (sum * d).toFloat())
                j++
            }
            i++
        }
        return true
    }

    /*
     ============
     idMatX::LDLT_UpdateRankOne

     Updates the in-place LDL' factorization to obtain the factors for the matrix: LDL' + alpha * v * v'
     If offset > 0 only the lower right corner starting at (offset, offset) is updated.
     ============
     */
    fun LDLT_UpdateRankOne(v: idVecX, alpha: Float, offset: Int): Boolean {
        var alpha = alpha
        var j: Int
        var diag: Float
        var newDiag: Float
        var beta: Float
        var p: Float
        var d: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(offset >= 0 && offset < numRows)

//	y = (float *) _alloca16( v.GetSize() * sizeof( float ) );
//	memcpy( y, v.ToFloatPtr(), v.GetSize() * sizeof( float ) );
        val y: FloatArray = v.ToFloatPtr()
        var i: Int = offset
        while (i < numColumns) {
            p = y[i]
            diag = this.oGet(i, i)
            this.oSet(i, i, (diag + alpha * p * p.also { newDiag = it }))
            if (newDiag == 0.0f) {
                return false
            }
            alpha /= newDiag
            beta = p * alpha
            alpha *= diag
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i)
                y[j] -= p * d
                d += beta * y[j]
                this.oSet(j, i, d)
                j++
            }
            i++
        }
        return true
    }

    /*
     ============
     idMatX::LDLT_UpdateIncrement

     Updates the in-place LDL' factorization to obtain the factors for the matrix:

     [ A  a ]
     [ a  b ]

     where: a = v[0,numRows-1], b = v[numRows]
     ============
     */
    /*
     ============
     idMatX::LDLT_UpdateRowColumn

     Updates the in-place LDL' factorization to obtain the factors for the matrix:

     [ 0  a  0 ]
     LDL' + [ a  b  c ]
     [ 0  c  0 ]

     where: a = v[0,r-1], b = v[r], c = v[r+1,numRows-1]
     ============
     */
    fun LDLT_UpdateRowColumn(v: idVecX, r: Int): Boolean {
        var i: Int
        var j: Int
        var sum: Double
        val original: FloatArray
        val y: FloatArray
        val addSub = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(r >= 0 && r < numRows)
        addSub.SetData(numColumns, FloatArray(numColumns))
        if (r == 0) {
            if (numColumns == 1) {
                this.plusAssign(0, 0, v.p[0])
                return true
            }
            i = 0
            while (i < numColumns) {
                addSub.p[i] = v.p[i]
                i++
            }
        } else {
            original = FloatArray(numColumns)
            y = FloatArray(numColumns)

            // calculate original row/column of matrix
            i = 0
            while (i < r) {
                y[i] = this.oGet(r, i) * this.oGet(i, i)
                i++
            }
            i = 0
            while (i < numColumns) {
                sum = if (i < r) {
                    (this.oGet(i, i) * this.oGet(r, i)).toDouble()
                } else if (i == r) {
                    this.oGet(r, r).toDouble()
                } else {
                    (this.oGet(r, r) * this.oGet(i, r)).toDouble()
                }
                j = 0
                while (j < i && j < r) {
                    sum += (this.oGet(i, j) * y[j]).toDouble()
                    j++
                }
                original[i] = sum.toFloat()
                i++
            }

            // solve for y in L * y = original + v
            i = 0
            while (i < r) {
                sum = (original[i] + v.p[i]).toDouble()
                j = 0
                while (j < i) {
                    sum -= (this.oGet(i, j) * y[j]).toDouble()
                    j++
                }
                y[i] = sum.toFloat()
                i++
            }

            // calculate new row of L
            i = 0
            while (i < r) {
                this.oSet(r, i, y[i] / this.oGet(i, i))
                i++
            }

            // if the last row/column of the matrix is updated
            if (r == numColumns - 1) {
                // only calculate new diagonal
                sum = (original[r] + v.p[r]).toDouble()
                j = 0
                while (j < r) {
                    sum -= (this.oGet(r, j) * y[j]).toDouble()
                    j++
                }
                if (sum == 0.0) {
                    return false
                }
                this.oSet(r, r, sum.toFloat())
                return true
            }

            // calculate the row/column to be added to the lower right sub matrix starting at (r, r)
            i = 0
            while (i < r) {
                y[i] = this.oGet(r, i) * this.oGet(i, i)
                i++
            }
            i = r
            while (i < numColumns) {
                sum = if (i == r) {
                    this.oGet(r, r).toDouble()
                } else {
                    (this.oGet(r, r) * this.oGet(i, r)).toDouble()
                }
                j = 0
                while (j < r) {
                    sum += (this.oGet(i, j) * y[j]).toDouble()
                    j++
                }
                addSub.p[i] = (v.p[i] - (sum - original[i])).toFloat()
                i++
            }
        }

        // add row/column to the lower right sub matrix starting at (r, r)
//#else
        var d: Float
        var diag: Float
        var newDiag: Float
        var p1: Float
        var p2: Float
        var beta1: Float
        var beta2: Float
        val v1: FloatArray = FloatArray(numColumns)
        val v2: FloatArray = FloatArray(numColumns)
        d = idMath.SQRT_1OVER2
        v1[r] = ((0.5f * addSub.p[r] + 1.0f) * d)
        v2[r] = ((0.5f * addSub.p[r] - 1.0f) * d)
        i = r + 1
        while (i < numColumns) {
            v2[i] = (addSub.p[i] * d)
            v1[i] = v2[i]
            i++
        }
        var alpha1: Float = 1.0f
        var alpha2: Float = -1.0f

        // simultaneous update/downdate of the sub matrix starting at (r, r)
        i = r
        while (i < numColumns) {
            diag = this.oGet(i, i)
            p1 = v1[i]
            newDiag = diag + alpha1 * p1 * p1
            if (newDiag == 0.0f) {
                return false
            }
            alpha1 /= newDiag
            beta1 = p1 * alpha1
            alpha1 *= diag
            diag = newDiag
            p2 = v2[i]
            newDiag = diag + alpha2 * p2 * p2
            if (newDiag == 0.0f) {
                return false
            }
            alpha2 /= newDiag
            beta2 = p2 * alpha2
            alpha2 *= diag
            this.oSet(i, i, newDiag)
            j = i + 1
            while (j < numRows) {
                d = this.oGet(j, i)
                v1[j] -= p1 * d
                d += beta1 * v1[j]
                v2[j] -= p2 * d
                d += beta2 * v2[j]
                this.oSet(j, i, d)
                j++
            }
            i++
        }

//#endif
        return true
    }

    fun LDLT_UpdateIncrement(v: idVecX): Boolean {
        var j: Int
        var sum: Float
        var d: Float
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows + 1)
        ChangeSize(numRows + 1, numColumns + 1, false)
        val x: FloatArray = FloatArray(numRows)

        // solve for x in L * x = v
        var i: Int = 0
        while (i < numRows - 1) {
            sum = v.p[i]
            j = 0
            while (j < i) {
                sum -= (this.oGet(i, j) * x[j])
                j++
            }
            x[i] = sum.toFloat()
            i++
        }

        // calculate new row of L and calculate the diagonal entry
        sum = v.p[numRows - 1]
        i = 0
        while (i < numRows - 1) {
            this.oSet(numRows - 1, i, x[i] / this.oGet(i, i).also { d = it })
            sum -= d * x[i]
            i++
        }
        if (sum == 0.0f) {
            return false
        }

        // store the diagonal entry
        this.oSet(numRows - 1, numRows - 1, sum.toFloat())
        return true
    }

    /**
     * ============ idMatX::LDLT_UpdateDecrement
     *
     *
     * Updates the in-place LDL' factorization to obtain the factors for the
     * matrix with row r and column r removed. v should store the row of the
     * original matrix. ============
     */
    fun LDLT_UpdateDecrement(v: idVecX, r: Int): Boolean {
        var v1: idVecX = idVecX()
        assert(numRows == numColumns)
        assert(v.GetSize() >= numRows)
        assert(r >= 0 && r < numRows)
        v1.SetData(numRows, idVecX.VECX_ALLOCA(numRows))

        // update the row and column to identity
        v1 = v.unaryMinus()
        v1.p[r] += 1.0f

        // NOTE:	msvc compiler bug: the this pointer stored in edi is expected to stay
        //			untouched when calling LDLT_UpdateRowColumn in the if statement
//#if 0
//	if ( !LDLT_UpdateRowColumn( v1, r ) ) {
//#else
        val ret = LDLT_UpdateRowColumn(v1, r)
        if (!ret) {
//#endif
            return false
        }

        // physically remove the row and column
        Update_Decrement(r)
        return true
    }

    /**
     * ============ idMatX::LDLT_Solve
     *
     *
     * Solve Ax = b with A factored in-place as: LDL' ============
     */
    fun LDLT_Solve(x: idVecX, b: idVecX) {
        var j: Int
        var sum: Float
        assert(numRows == numColumns)
        assert(x.GetSize() >= numRows && b.GetSize() >= numRows)

        // solve L
        var i: Int = 0
        while (i < numRows) {
            sum = b.p[i]
            j = 0
            while (j < i) {
                sum -= (this.oGet(i, j) * x.p[j])
                j++
            }
            x.p[i] = sum.toFloat()
            i++
        }

        // solve D
        i = 0
        while (i < numRows) {
            x.p[i] /= this.oGet(i, i)
            i++
        }

        // solve Lt
        i = numRows - 2
        while (i >= 0) {
            sum = x.p[i]
            j = i + 1
            while (j < numRows) {
                sum -= (this.oGet(j, i) * x.p[j])
                j++
            }
            x.p[i] = sum.toFloat()
            i--
        }
    }

    /**
     * ============ idMatX::LDLT_Inverse
     *
     *
     * Calculates the inverse of the matrix which is factored in-place as: LDL'
     * ============
     */
    fun LDLT_Inverse(inv: idMatX) {
        var j: Int
        val x = idVecX()
        val b = idVecX()
        assert(numRows == numColumns)
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        inv.SetSize(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            LDLT_Solve(x, b)
            j = 0
            while (j < numRows) {
                inv.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
    }

    /*
     ============
     idMatX::LDLT_UnpackFactors

     Unpacks the in-place LDL' factorization.
     ============
     */
    fun LDLT_UnpackFactors(L: idMatX, D: idMatX) {
        var j: Int
        L.Zero(numRows, numColumns)
        D.Zero(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            j = 0
            while (j < i) {
                L.oSet(i, j, this.oGet(i, j))
                j++
            }
            L.oSet(i, i, 1.0f)
            D.oSet(i, i, this.oGet(i, i))
            i++
        }
    }

    /*
     ============
     idMatX::LDLT_MultiplyFactors

     Multiplies the factors of the in-place LDL' factorization to form the original matrix.
     ============
     */
    fun LDLT_MultiplyFactors(m: idMatX) {
        var i: Int
        var j: Int
        var sum: Float
        val v: FloatArray = FloatArray(numRows)
        m.SetSize(numRows, numColumns)
        var r: Int = 0
        while (r < numRows) {


            // calculate row of matrix
            i = 0
            while (i < r) {
                v[i] = this.oGet(r, i) * this.oGet(i, i)
                i++
            }
            i = 0
            while (i < numColumns) {
                sum = if (i < r) {
                    (this.oGet(i, i) * this.oGet(r, i))
                } else if (i == r) {
                    this.oGet(r, r)
                } else {
                    (this.oGet(r, r) * this.oGet(i, r))
                }
                j = 0
                while (j < i && j < r) {
                    sum += (this.oGet(i, j) * v[j])
                    j++
                }
                m.oSet(r, i, sum.toFloat())
                i++
            }
            r++
        }
    }

    fun TriDiagonal_ClearTriangles() {
        var j: Int
        assert(numRows == numColumns)
        var i: Int = 0
        while (i < numRows - 2) {
            j = i + 2
            while (j < numColumns) {
                this.oSet(i, j, 0.0f)
                this.oSet(j, i, 0.0f)
                j++
            }
            i++
        }
    }

    /**
     * ============ idMatX::TriDiagonal_Solve
     *
     *
     * Solve Ax = b with A being tridiagonal. ============
     */
    fun TriDiagonal_Solve(x: idVecX, b: idVecX): Boolean {
        var d: Float
        val tmp = idVecX()
        assert(numRows == numColumns)
        assert(x.GetSize() >= numRows && b.GetSize() >= numRows)
        tmp.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        d = this.oGet(0, 0)
        if (d == 0.0f) {
            return false
        }
        d = 1.0f / d
        x.p[0] = b.p[0] * d
        var i: Int = 1
        while (i < numRows) {
            tmp.p[i] = this.oGet(i - 1, i) * d
            d = this.oGet(i, i) - this.oGet(i, i - 1) * tmp.p[i]
            if (d == 0.0f) {
                return false
            }
            d = 1.0f / d
            x.p[i] = (b.p[i] - this.oGet(i, i - 1) * x.p[i - 1]) * d
            i++
        }
        i = numRows - 2
        while (i >= 0) {
            x.p[i] -= tmp.p[i + 1] * x.p[i + 1]
            i--
        }
        return true
    }

    /**
     * ============ idMatX::TriDiagonal_Inverse
     *
     *
     * Calculates the inverse of a tri-diagonal matrix. ============
     */
    fun TriDiagonal_Inverse(inv: idMatX) {
        var j: Int
        val x = idVecX()
        val b = idVecX()
        assert(numRows == numColumns)
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        inv.SetSize(numRows, numColumns)
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            TriDiagonal_Solve(x, b)
            j = 0
            while (j < numRows) {
                inv.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
    }
    /*
     ============
     idMatX::Eigen_SolveSymmetric

     Determine eigen values and eigen vectors for a symmetric matrix.
     The eigen values are stored in 'eigenValues'.
     Column i of the original matrix will store the eigen vector corresponding to the eigenValues[i].
     The initial matrix has to be symmetric.
     ============
     */
    /**
     * ============ idMatX::Eigen_SolveSymmetricTriDiagonal
     *
     *
     * Determine eigen values and eigen vectors for a symmetric tri-diagonal
     * matrix. The eigen values are stored in 'eigenValues'. Column i of the
     * original matrix will store the eigen vector corresponding to the
     * eigenValues[i]. The initial matrix has to be symmetric tri-diagonal.
     * ============
     */
    fun Eigen_SolveSymmetricTriDiagonal(eigenValues: idVecX): Boolean {
        val subd = idVecX()
        assert(numRows == numColumns)
        subd.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        eigenValues.SetSize(numRows)
        var i: Int = 0
        while (i < numRows - 1) {
            eigenValues.p[i] = this.oGet(i, i)
            subd.p[i] = this.oGet(i + 1, i)
            i++
        }
        eigenValues.p[numRows - 1] = this.oGet(numRows - 1, numRows - 1)
        Identity()
        return QL(eigenValues, subd)
    }

    fun Eigen_SolveSymmetric(eigenValues: idVecX): Boolean {
        val subd = idVecX()
        assert(numRows == numColumns)
        subd.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        eigenValues.SetSize(numRows)
        HouseholderReduction(eigenValues, subd)
        return QL(eigenValues, subd)
    }

    /**
     * ============ idMatX::Eigen_Solve
     *
     *
     * Determine eigen values and eigen vectors for a square matrix. The eigen
     * values are stored in 'realEigenValues' and 'imaginaryEigenValues'. Column
     * i of the original matrix will store the eigen vector corresponding to the
     * realEigenValues[i] and imaginaryEigenValues[i]. ============
     */
    fun Eigen_Solve(realEigenValues: idVecX, imaginaryEigenValues: idVecX): Boolean {
        val H = idMatX()
        assert(numRows == numColumns)
        realEigenValues.SetSize(numRows)
        imaginaryEigenValues.SetSize(numRows)
        H.oSet(this)

        // reduce to Hessenberg form
        HessenbergReduction(H)

        // reduce Hessenberg to real Schur form
        return HessenbergToRealSchur(H, realEigenValues, imaginaryEigenValues)
    }

    fun Eigen_SortIncreasing(eigenValues: idVecX) {
        var i: Int
        var j: Int
        var k: Int
        var min: Float
        i = 0.also { j = it }
        while (i <= numRows - 2) {
            j = i
            min = eigenValues.p[j]
            k = i + 1
            while (k < numRows) {
                if (eigenValues.p[k] < min) {
                    j = k
                    min = eigenValues.p[j]
                }
                k++
            }
            if (j != i) {
                eigenValues.SwapElements(i, j)
                SwapColumns(i, j)
            }
            i++
        }
    }

    fun Eigen_SortDecreasing(eigenValues: idVecX) {
        var i: Int
        var j: Int
        var k: Int
        var max: Float
        i = 0.also { j = it }
        while (i <= numRows - 2) {
            j = i
            max = eigenValues.p[j]
            k = i + 1
            while (k < numRows) {
                if (eigenValues.p[k] > max) {
                    j = k
                    max = eigenValues.p[j]
                }
                k++
            }
            if (j != i) {
                eigenValues.SwapElements(i, j)
                SwapColumns(i, j)
            }
            i++
        }
    }

    private fun SetTempSize(rows: Int, columns: Int) {
        val newSize: Int = rows * columns + 3 and 3.inv()
        assert(newSize < MATX_MAX_TEMP)
        if (tempIndex + newSize > MATX_MAX_TEMP) {
            tempIndex = 0
        }
        //            mat = idMatX::tempPtr + idMatX::tempIndex;
        mat = FloatArray(newSize)
        tempIndex += newSize
        alloced = newSize
        numRows = rows
        numColumns = columns
        MATX_CLEAREND()
    }

    private fun DeterminantGeneric(): Float {
        val det = FloatArray(1)
        var tmp = idMatX()
        val index: IntArray = IntArray(numRows)
        tmp.SetData(numRows, numColumns, MATX_ALLOCA(numRows * numColumns))
        tmp = this
        return if (!tmp.LU_Factor(index, det)) {
            0.0f
        } else det[0]
    }

    private fun InverseSelfGeneric(): Boolean {
        var j: Int
        var tmp = idMatX()
        val x = idVecX()
        val b = idVecX()
        val index: IntArray = IntArray(numRows)
        tmp.SetData(numRows, numColumns, MATX_ALLOCA(numRows * numColumns))
        tmp = this
        if (!tmp.LU_Factor(index)) {
            return false
        }
        x.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        b.Zero()
        var i: Int = 0
        while (i < numRows) {
            b.p[i] = 1.0f
            tmp.LU_Solve(x, b, index)
            j = 0
            while (j < numRows) {
                this.oSet(j, i, x.p[j])
                j++
            }
            b.p[i] = 0.0f
            i++
        }
        return true
    }

    /**
     * ============ idMatX::QR_Rotate
     *
     *
     * Performs a Jacobi rotation on the rows i and i+1 of the unpacked QR
     * factors. ============
     */
    private fun QR_Rotate(R: idMatX, i: Int, a: Float, b: Float) {
        val f: Float
        var c: Float
        var s: Float
        var w: Float
        var y: Float
        if (a == 0.0f) {
            c = 0.0f
            s = if (b >= 0.0f) 1.0f else -1.0f
        } else if (abs(a) > abs(b)) {
            f = b / a
            c = abs(1.0f / idMath.Sqrt(1.0f + f * f))
            if (a < 0.0f) {
                c = -c
            }
            s = f * c
        } else {
            f = a / b
            s = abs(1.0f / idMath.Sqrt(1.0f + f * f))
            if (b < 0.0f) {
                s = -s
            }
            c = f * s
        }
        var j: Int = i
        while (j < numRows) {
            y = R.oGet(i, j)
            w = R.oGet(i + 1, j)
            R.oSet(i, j, c * y - s * w)
            R.oSet(i + 1, j, s * y + c * w)
            j++
        }
        j = 0
        while (j < numRows) {
            y = this.oGet(j, i)
            w = this.oGet(j, i + 1)
            this.oSet(j, i, c * y - s * w)
            this.oSet(j, i + 1, s * y + c * w)
            j++
        }
    }

    /**
     * ============ idMatX::Pythag
     *
     *
     * Computes (a^2 + b^2)^1/2 without underflow or overflow. ============
     */
    private fun Pythag(a: Float, b: Float): Float {
        val ct: Double
        val at: Double = abs(a).toDouble()
        val bt: Double = abs(b).toDouble()
        return if (at > bt) {
            ct = bt / at
            (at * idMath.Sqrt((1.0f + ct * ct).toFloat())).toFloat()
        } else {
            if (bt != 0.0) {
                ct = at / bt
                (bt * idMath.Sqrt((1.0f + ct * ct).toFloat())).toFloat()
            } else {
                0.0f
            }
        }
    }

    private fun SVD_BiDiag(w: idVecX, rv1: idVecX, anorm: FloatArray) {
        var j: Int
        var k: Int
        var l: Int
        var f: Double
        var h: Double
        var r: Double
        var g: Double
        var s: Double
        var scale: Double
        anorm[0] = 0.0f
        scale = 0.0
        s = scale
        g = s
        var i: Int = 0
        while (i < numColumns) {
            l = i + 1
            rv1.p[i] = (scale * g).toFloat()
            scale = 0.0
            s = scale
            g = s
            if (i < numRows) {
                k = i
                while (k < numRows) {
                    scale += abs(this.oGet(k, i)).toDouble()
                    k++
                }
                if (scale != 0.0) {
                    k = i
                    while (k < numRows) {
                        this.divAssign(k, i, scale)
                        s += (this.oGet(k, i) * this.oGet(k, i)).toDouble()
                        k++
                    }
                    f = this.oGet(i, i).toDouble()
                    g = idMath.Sqrt(s.toFloat()).toDouble()
                    if (f >= 0.0f) {
                        g = -g
                    }
                    h = f * g - s
                    this.oSet(i, i, (f - g).toFloat())
                    if (i != numColumns - 1) {
                        j = l
                        while (j < numColumns) {
                            s = 0.0
                            k = i
                            while (k < numRows) {
                                s += (this.oGet(k, i) * this.oGet(k, j)).toDouble()
                                k++
                            }
                            f = s / h
                            k = i
                            while (k < numRows) {
                                this.plusAssign(k, j, f * this.oGet(k, i))
                                k++
                            }
                            j++
                        }
                    }
                    k = i
                    while (k < numRows) {
                        this.timesAssign(k, i, scale)
                        k++
                    }
                }
            }
            w.p[i] = (scale * g).toFloat()
            scale = 0.0
            s = scale
            g = s
            if (i < numRows && i != numColumns - 1) {
                k = l
                while (k < numColumns) {
                    scale += abs(this.oGet(i, k)).toDouble()
                    k++
                }
                if (scale != 0.0) {
                    k = l
                    while (k < numColumns) {
                        this.divAssign(i, k, scale) //TODO:add oDivSit
                        s += (this.oGet(i, k) * this.oGet(i, k)).toDouble()
                        k++
                    }
                    f = this.oGet(i, l).toDouble()
                    g = idMath.Sqrt(s.toFloat()).toDouble()
                    if (f >= 0.0f) {
                        g = -g
                    }
                    h = 1.0f / (f * g - s)
                    this.oSet(i, l, (f - g).toFloat())
                    k = l
                    while (k < numColumns) {
                        rv1.p[k] = (this.oGet(i, k) * h).toFloat()
                        k++
                    }
                    if (i != numRows - 1) {
                        j = l
                        while (j < numRows) {
                            s = 0.0
                            k = l
                            while (k < numColumns) {
                                s += (this.oGet(j, k) * this.oGet(i, k)).toDouble()
                                k++
                            }
                            k = l
                            while (k < numColumns) {
                                this.plusAssign(j, k, s * rv1.p[k])
                                k++
                            }
                            j++
                        }
                    }
                    k = l
                    while (k < numColumns) {
                        this.timesAssign(i, k, scale)
                        k++
                    }
                }
            }
            r = (abs(w.p[i]) + abs(rv1.p[i])).toDouble()
            if (r > anorm[0]) {
                anorm[0] = r.toFloat()
            }
            i++
        }
    }

    private fun SVD_InitialWV(w: idVecX, V: idMatX, rv1: idVecX) {
        var j: Int
        var k: Int
        var l: Int
        var f: Float
        var g: Float
        var s: Float
        g = 0.0f
        var i: Int = numColumns - 1
        while (i >= 0) {
            l = i + 1
            if (i < numColumns - 1) {
                if (g != 0.0f) {
                    j = l
                    while (j < numColumns) {
                        V.oSet(j, i, (this.oGet(i, j) / this.oGet(i, l) / g).toFloat())
                        j++
                    }
                    // double division to reduce underflow
                    j = l
                    while (j < numColumns) {
                        s = 0.0f
                        k = l
                        while (k < numColumns) {
                            s += (this.oGet(i, k) * V.oGet(k, j))
                            k++
                        }
                        k = l
                        while (k < numColumns) {
                            V.plusAssign(k, j, s * V.oGet(k, i))
                            k++
                        }
                        j++
                    }
                }
                j = l
                while (j < numColumns) {
                    V.oSet(j, i, V.oSet(i, j, 0.0f))
                    j++
                }
            }
            V.oSet(i, i, 1.0f)
            g = rv1.p[i]
            i--
        }
        i = numColumns - 1
        while (i >= 0) {
            l = i + 1
            g = w.p[i]
            if (i < numColumns - 1) {
                j = l
                while (j < numColumns) {
                    this.oSet(i, j, 0.0f)
                    j++
                }
            }
            if (g != 0.0f) {
                g = 1.0f / g
                if (i != numColumns - 1) {
                    j = l
                    while (j < numColumns) {
                        s = 0.0f
                        k = l
                        while (k < numRows) {
                            s += (this.oGet(k, i) * this.oGet(k, j))
                            k++
                        }
                        f = s / this.oGet(i, i) * g
                        k = i
                        while (k < numRows) {
                            this.plusAssign(k, j, f * this.oGet(k, i))
                            k++
                        }
                        j++
                    }
                }
                j = i
                while (j < numRows) {
                    this.timesAssign(j, i, g)
                    j++
                }
            } else {
                j = i
                while (j < numRows) {
                    this.oSet(j, i, 0.0f)
                    j++
                }
            }
            this.plusAssign(i, i, 1.0f)
            i--
        }
    }

    /**
     * ============ idMatX::HouseholderReduction
     *
     *
     * Householder reduction to symmetric tri-diagonal form. The original matrix
     * is replaced by an orthogonal matrix effecting the accumulated householder
     * transformations. The diagonal elements of the diagonal matrix are stored
     * in diag. The off-diagonal elements of the diagonal matrix are stored in
     * subd. The initial matrix has to be symmetric. ============
     */
    private fun HouseholderReduction(diag: idVecX, subd: idVecX) {
        var i1: Int
        var i2: Int
        var h: Float
        var f: Float
        var g: Float
        var invH: Float
        var halfFdivH: Float
        var scale: Float
        var invScale: Float
        var sum: Float
        assert(numRows == numColumns)
        diag.SetSize(numRows)
        subd.SetSize(numRows)
        var i0: Int = numRows - 1
        var i3: Int = numRows - 2
        while (i0 >= 1) {
            h = 0.0f
            scale = 0.0f
            if (i3 > 0) {
                i2 = 0
                while (i2 <= i3) {
                    scale += abs(this.oGet(i0, i2))
                    i2++
                }
                if (scale == 0f) {
                    subd.p[i0] = this.oGet(i0, i3)
                } else {
                    invScale = 1.0f / scale
                    i2 = 0
                    while (i2 <= i3) {
                        this.timesAssign(i0, i2, invScale)
                        h += this.oGet(i0, i2) * this.oGet(i0, i2)
                        i2++
                    }
                    f = this.oGet(i0, i3)
                    g = idMath.Sqrt(h)
                    if (f > 0.0f) {
                        g = -g
                    }
                    subd.p[i0] = scale * g
                    h -= f * g
                    this.oSet(i0, i3, f - g)
                    f = 0.0f
                    invH = 1.0f / h
                    i1 = 0
                    while (i1 <= i3) {
                        this.oSet(i1, i0, this.oGet(i0, i1) * invH)
                        g = 0.0f
                        i2 = 0
                        while (i2 <= i1) {
                            g += this.oGet(i1, i2) * this.oGet(i0, i2)
                            i2++
                        }
                        i2 = i1 + 1
                        while (i2 <= i3) {
                            g += this.oGet(i2, i1) * this.oGet(i0, i2)
                            i2++
                        }
                        subd.p[i1] = g * invH
                        f += subd.p[i1] * this.oGet(i0, i1)
                        i1++
                    }
                    halfFdivH = 0.5f * f * invH
                    i1 = 0
                    while (i1 <= i3) {
                        f = this.oGet(i0, i1)
                        g = subd.p[i1] - halfFdivH * f
                        subd.p[i1] = g
                        i2 = 0
                        while (i2 <= i1) {
                            this.minusAssign(i1, i2, f * subd.p[i2] + g * this.oGet(i0, i2))
                            i2++
                        }
                        i1++
                    }
                }
            } else {
                subd.p[i0] = this.oGet(i0, i3)
            }
            diag.p[i0] = h
            i0--
            i3--
        }
        diag.p[0] = 0.0f
        subd.p[0] = 0.0f
        i0 = 0
        i3 = -1
        while (i0 <= numRows - 1) {
            if (diag.p[i0] != 0f) {
                i1 = 0
                while (i1 <= i3) {
                    sum = 0.0f
                    i2 = 0
                    while (i2 <= i3) {
                        sum += this.oGet(i0, i2) * this.oGet(i2, i1)
                        i2++
                    }
                    i2 = 0
                    while (i2 <= i3) {
                        this.minusAssign(i2, i1, sum * this.oGet(i2, i0))
                        i2++
                    }
                    i1++
                }
            }
            diag.p[i0] = this.oGet(i0, i0)
            this.oSet(i0, i0, 1.0f)
            i1 = 0
            while (i1 <= i3) {
                this.oSet(i1, i0, 0.0f)
                this.oSet(i0, i1, 0.0f)
                i1++
            }
            i0++
            i3++
        }

        // re-order
        i0 = 1
        i3 = 0
        while (i0 < numRows) {
            subd.p[i3] = subd.p[i0]
            i0++
            i3++
        }
        subd.p[numRows - 1] = 0.0f
    }

    /**
     * ============ idMatX::QL
     *
     *
     * QL algorithm with implicit shifts to determine the eigenvalues and
     * eigenvectors of a symmetric tri-diagonal matrix. diag contains the
     * diagonal elements of the symmetric tri-diagonal matrix on input and is
     * overwritten with the eigenvalues. subd contains the off-diagonal elements
     * of the symmetric tri-diagonal matrix and is destroyed. This matrix has to
     * be either the identity matrix to determine the eigenvectors for a
     * symmetric tri-diagonal matrix, or the matrix returned by the Householder
     * reduction to determine the eigenvalues for the original symmetric matrix.
     * ============
     */
    private fun QL(diag: idVecX, subd: idVecX): Boolean {
        val maxIter = 32
        var i1: Int
        var i2: Int
        var i3: Int
        var a: Float
        var b: Float
        var f: Float
        var g: Float
        var r: Float
        var p: Float
        var s: Float
        var c: Float
        assert(numRows == numColumns)
        var i0: Int = 0
        while (i0 < numRows) {
            i1 = 0
            while (i1 < maxIter) {
                i2 = i0
                while (i2 <= numRows - 2) {
                    a = abs(diag.p[i2]) + abs(diag.p[i2 + 1])
                    if (abs(subd.p[i2]) + a == a) {
                        break
                    }
                    i2++
                }
                if (i2 == i0) {
                    break
                }
                g = (diag.p[i0 + 1] - diag.p[i0]) / (2.0f * subd.p[i0])
                r = idMath.Sqrt(g * g + 1.0f)
                g = if (g < 0.0f) {
                    diag.p[i2] - diag.p[i0] + subd.p[i0] / (g - r)
                } else {
                    diag.p[i2] - diag.p[i0] + subd.p[i0] / (g + r)
                }
                s = 1.0f
                c = 1.0f
                p = 0.0f
                i3 = i2 - 1
                while (i3 >= i0) {
                    f = s * subd.p[i3]
                    b = c * subd.p[i3]
                    if (abs(f) >= abs(g)) {
                        c = g / f
                        r = idMath.Sqrt(c * c + 1.0f)
                        subd.p[i3 + 1] = f * r
                        s = 1.0f / r
                        c *= s
                    } else {
                        s = f / g
                        r = idMath.Sqrt(s * s + 1.0f)
                        subd.p[i3 + 1] = g * r
                        c = 1.0f / r
                        s *= c
                    }
                    g = diag.p[i3 + 1] - p
                    r = (diag.p[i3] - g) * s + 2.0f * b * c
                    p = s * r
                    diag.p[i3 + 1] = g + p
                    g = c * r - b
                    for (i4 in 0 until numRows) {
                        f = this.oGet(i4, i3 + 1)
                        this.oSet(i4, i3 + 1, s * this.oGet(i4, i3) + c * f)
                        this.oSet(i4, i3, c * this.oGet(i4, i3) - s * f)
                    }
                    i3--
                }
                diag.p[i0] -= p
                subd.p[i0] = g
                subd.p[i2] = 0.0f
                i1++
            }
            if (i1 == maxIter) {
                return false
            }
            i0++
        }
        return true
    }

    /*
     ============
     idMatX::HessenbergReduction

     Reduction to Hessenberg form.
     ============
     */
    private fun HessenbergReduction(H: idMatX) {
        var i: Int
        var j: Int
        var m: Int
        val low = 0
        val high = numRows - 1
        var scale: Float
        var f: Float
        var g: Float
        var h: Float
        val v = idVecX()
        v.SetData(numRows, idVecX.VECX_ALLOCA(numRows))
        m = low + 1
        while (m <= high - 1) {
            scale = 0.0f
            i = m
            while (i <= high) {
                scale = scale + abs(H.oGet(i, m - 1))
                i++
            }
            if (scale != 0.0f) {

                // compute Householder transformation.
                h = 0.0f
                i = high
                while (i >= m) {
                    v.p[i] = H.oGet(i, m - 1) / scale
                    h += v.p[i] * v.p[i]
                    i--
                }
                g = idMath.Sqrt(h)
                if (v.p[m] > 0.0f) {
                    g = -g
                }
                h = h - v.p[m] * g
                v.p[m] = v.p[m] - g

                // apply Householder similarity transformation
                // H = (I-u*u'/h)*H*(I-u*u')/h)
                j = m
                while (j < numRows) {
                    f = 0.0f
                    i = high
                    while (i >= m) {
                        f += v.p[i] * H.oGet(i, j)
                        i--
                    }
                    f = f / h
                    i = m
                    while (i <= high) {
                        H.minusAssign(i, j, f * v.p[i])
                        i++
                    }
                    j++
                }
                i = 0
                while (i <= high) {
                    f = 0.0f
                    j = high
                    while (j >= m) {
                        f += v.p[j] * H.oGet(i, j)
                        j--
                    }
                    f = f / h
                    j = m
                    while (j <= high) {
                        H.minusAssign(i, j, f * v.p[j])
                        j++
                    }
                    i++
                }
                v.p[m] = scale * v.p[m]
                H.oSet(m, m - 1, scale * g)
            }
            m++
        }

        // accumulate transformations
        Identity()
        m = high - 1
        while (m >= low + 1) {
            if (H.oGet(m, m - 1) != 0.0f) {
                i = m + 1
                while (i <= high) {
                    v.p[i] = H.oGet(i, m - 1)
                    i++
                }
                j = m
                while (j <= high) {
                    g = 0.0f
                    i = m
                    while (i <= high) {
                        g += v.p[i] * this.oGet(i, j)
                        i++
                    }
                    // float division to avoid possible underflow
                    g = g / v.p[m] / H.oGet(m, m - 1)
                    i = m
                    while (i <= high) {
                        this.plusAssign(i, j, g * v.p[i])
                        i++
                    }
                    j++
                }
            }
            m--
        }
    }

    /**
     * ============ idMatX::ComplexDivision
     *
     *
     * Complex scalar division. ============
     */
    private fun ComplexDivision(xr: Float, xi: Float, yr: Float, yi: Float, cdivr: CFloat, cdivi: CFloat) {
        val r: Float
        val d: Float
        if (abs(yr) > abs(yi)) {
            r = yi / yr
            d = yr + r * yi
            cdivr._val = ((xr + r * xi) / d)
            cdivi._val = ((xi - r * xr) / d)
        } else {
            r = yr / yi
            d = yi + r * yr
            cdivr._val = ((r * xr + xi) / d)
            cdivi._val = ((r * xi - xr) / d)
        }
    }

    /**
     * ============ idMatX::HessenbergToRealSchur
     *
     *
     * Reduction from Hessenberg to real Schur form. ============
     */
    private fun HessenbergToRealSchur(H: idMatX, realEigenValues: idVecX, imaginaryEigenValues: idVecX): Boolean {
        var i: Int
        var j: Int
        var k: Int
        var n = numRows - 1
        val low = 0
        val high = numRows - 1
        val eps = 2e-16f
        var exshift = 0.0f
        var p = 0.0f
        var q = 0.0f
        var r = 0.0f
        var s = 0.0f
        var z = 0.0f
        var t: Float
        var w: Float
        var x: Float
        var y: Float

        // store roots isolated by balanc and compute matrix norm
        var norm = 0.0f
        i = 0
        while (i < numRows) {
            if (i < low || i > high) {
                realEigenValues.p[i] = H.oGet(i, i)
                imaginaryEigenValues.p[i] = 0.0f
            }
            j = Lib.Max(i - 1, 0)
            while (j < numRows) {
                norm = norm + abs(H.oGet(i, j))
                j++
            }
            i++
        }
        var iter = 0
        while (n >= low) {

            // look for single small sub-diagonal element
            var l = n
            while (l > low) {
                s = abs(H.oGet(l - 1, l - 1)) + abs(H.oGet(l, l))
                if (s == 0.0f) {
                    s = norm
                }
                if (abs(H.oGet(l, l - 1)) < eps * s) {
                    break
                }
                l--
            }

            // check for convergence
            if (l == n) {            // one root found
                H.plusAssign(n, n, exshift)
                realEigenValues.p[n] = H.oGet(n, n)
                imaginaryEigenValues.p[n] = 0.0f
                n--
                iter = 0
            } else if (l == n - 1) {    // two roots found
                w = H.oGet(n, n - 1) * H.oGet(n - 1, n)
                p = (H.oGet(n - 1, n - 1) - H.oGet(n, n)) / 2.0f
                q = p * p + w
                z = idMath.Sqrt(abs(q))
                H.plusAssign(n, n, exshift)
                H.plusAssign(n - 1, n - 1, exshift)
                x = H.oGet(n, n)
                if (q >= 0.0f) {        // real pair
                    z = if (p >= 0.0f) {
                        p + z
                    } else {
                        p - z
                    }
                    realEigenValues.p[n - 1] = x + z
                    realEigenValues.p[n] = realEigenValues.p[n - 1]
                    if (z != 0.0f) {
                        realEigenValues.p[n] = x - w / z
                    }
                    imaginaryEigenValues.p[n - 1] = 0.0f
                    imaginaryEigenValues.p[n] = 0.0f
                    x = H.oGet(n, n - 1)
                    s = abs(x) + abs(z)
                    p = x / s
                    q = z / s
                    r = idMath.Sqrt(p * p + q * q)
                    p = p / r
                    q = q / r

                    // modify row
                    j = n - 1
                    while (j < numRows) {
                        z = H.oGet(n - 1, j)
                        H.oSet(n - 1, j, q * z + p * H.oGet(n, j))
                        H.oSet(n, j, q * H.oGet(n, j) - p * z)
                        j++
                    }

                    // modify column
                    i = 0
                    while (i <= n) {
                        z = H.oGet(i, n - 1)
                        H.oSet(i, n - 1, q * z + p * H.oGet(i, n))
                        H.oSet(i, n, q * H.oGet(i, n) - p * z)
                        i++
                    }

                    // accumulate transformations
                    i = low
                    while (i <= high) {
                        z = this.oGet(i, n - 1)
                        this.oSet(i, n - 1, q * z + p * this.oGet(i, n))
                        this.oSet(i, n, q * this.oGet(i, n) - p * z)
                        i++
                    }
                } else {        // complex pair
                    realEigenValues.p[n - 1] = x + p
                    realEigenValues.p[n] = x + p
                    imaginaryEigenValues.p[n - 1] = z
                    imaginaryEigenValues.p[n] = -z
                }
                n = n - 2
                iter = 0
            } else {    // no convergence yet

                // form shift
                x = H.oGet(n, n)
                y = 0.0f
                w = 0.0f
                if (l < n) {
                    y = H.oGet(n - 1, n - 1)
                    w = H.oGet(n, n - 1) * H.oGet(n - 1, n)
                }

                // Wilkinson's original ad hoc shift
                if (iter == 10) {
                    exshift += x
                    i = low
                    while (i <= n) {
                        H.minusAssign(i, i, x)
                        i++
                    }
                    s = abs(H.oGet(n, n - 1)) + abs(H.oGet(n - 1, n - 2))
                    y = 0.75f * s
                    x = y
                    w = -0.4375f * s * s
                }

                // new ad hoc shift
                if (iter == 30) {
                    s = (y - x) / 2.0f
                    s = s * s + w
                    if (s > 0) {
                        s = idMath.Sqrt(s)
                        if (y < x) {
                            s = -s
                        }
                        s = x - w / ((y - x) / 2.0f + s)
                        i = low
                        while (i <= n) {
                            H.plusAssign(i, i, -s)
                            i++
                        }
                        exshift += s
                        w = 0.964f
                        y = w
                        x = y
                    }
                }
                iter = iter + 1

                // look for two consecutive small sub-diagonal elements
                var m: Int = n - 2
                while (m >= l) {
                    z = H.oGet(m, m)
                    r = x - z
                    s = y - z
                    p = (r * s - w) / H.oGet(m + 1, m) + H.oGet(m, m + 1)
                    q = H.oGet(m + 1, m + 1) - z - r - s
                    r = H.oGet(m + 2, m + 1)
                    s = abs(p) + abs(q) + abs(r)
                    p = p / s
                    q = q / s
                    r = r / s
                    if (m == l) {
                        break
                    }
                    if (abs(H.oGet(m, m - 1)) * (abs(q) + abs(r))
                        < eps * (abs(p) * (abs(H.oGet(m - 1, m - 1)) + abs(z) + abs(
                            H.oGet(
                                m + 1,
                                m + 1
                            )
                        )))
                    ) {
                        break
                    }
                    m--
                }
                i = m + 2
                while (i <= n) {
                    H.oSet(i, i - 2, 0.0f)
                    if (i > m + 2) {
                        H.oSet(i, i - 3, 0.0f)
                    }
                    i++
                }

                // double QR step involving rows l:n and columns m:n
                k = m
                while (k <= n - 1) {
                    val notlast = k != n - 1
                    if (k != m) {
                        p = H.oGet(k, k - 1)
                        q = H.oGet(k + 1, k - 1)
                        r = if (notlast) H.oGet(k + 2, k - 1) else 0.0f
                        x = abs(p) + abs(q) + abs(r)
                        if (x != 0.0f) {
                            p = p / x
                            q = q / x
                            r = r / x
                        }
                    }
                    if (x == 0.0f) {
                        break
                    }
                    s = idMath.Sqrt(p * p + q * q + r * r)
                    if (p < 0.0f) {
                        s = -s
                    }
                    if (s != 0.0f) {
                        if (k != m) {
                            H.oSet(k, k - 1, -s * x)
                        } else if (l != m) {
                            H.oSet(k, k - 1, -H.oGet(k, k - 1))
                        }
                        p = p + s
                        x = p / s
                        y = q / s
                        z = r / s
                        q = q / p
                        r = r / p

                        // modify row
                        j = k
                        while (j < numRows) {
                            p = H.oGet(k, j) + q * H.oGet(k + 1, j)
                            if (notlast) {
                                p = p + r * H.oGet(k + 2, j)
                                H.minusAssign(k + 2, j, p * z)
                            }
                            H.plusAssign(k, j, -p * x)
                            H.minusAssign(k + 1, j, p * y)
                            j++
                        }

                        // modify column
                        i = 0
                        while (i <= Lib.Min(n, k + 3)) {
                            p = x * H.oGet(i, k) + y * H.oGet(i, k + 1)
                            if (notlast) {
                                p = p + z * H.oGet(i, k + 2)
                                H.minusAssign(i, k + 2, p * r)
                            }
                            H.minusAssign(i, k, p)
                            H.minusAssign(i, k + 1, p * q)
                            i++
                        }

                        // accumulate transformations
                        i = low
                        while (i <= high) {
                            p = x * this.oGet(i, k) + y * this.oGet(i, k + 1)
                            if (notlast) {
                                p = p + z * this.oGet(i, k + 2)
                                this.minusAssign(i, k + 2, p * r)
                            }
                            this.minusAssign(i, k, p)
                            this.minusAssign(i, k + 1, p * q)
                            i++
                        }
                    }
                    k++
                }
            }
        }

        // backsubstitute to find vectors of upper triangular form
        if (norm == 0.0f) {
            return false
        }
        n = numRows - 1
        while (n >= 0) {
            p = realEigenValues.p[n]
            q = imaginaryEigenValues.p[n]
            if (q == 0.0f) {        // real vector
                var l = n
                H.oSet(n, n, 1.0f)
                i = n - 1
                while (i >= 0) {
                    w = H.oGet(i, i) - p
                    r = 0.0f
                    j = l
                    while (j <= n) {
                        r = r + H.oGet(i, j) * H.oGet(j, n)
                        j++
                    }
                    if (imaginaryEigenValues.p[i] < 0.0f) {
                        z = w
                        s = r
                    } else {
                        l = i
                        if (imaginaryEigenValues.p[i] == 0.0f) {
                            if (w != 0.0f) {
                                H.oSet(i, n, -r / w)
                            } else {
                                H.oSet(i, n, -r / (eps * norm))
                            }
                        } else {        // solve real equations
                            x = H.oGet(i, i + 1)
                            y = H.oGet(i + 1, i)
                            q =
                                (realEigenValues.p[i] - p) * (realEigenValues.p[i] - p) + imaginaryEigenValues.p[i] * imaginaryEigenValues.p[i]
                            t = (x * s - z * r) / q
                            H.oSet(i, n, t)
                            if (abs(x) > abs(z)) {
                                H.oSet(i + 1, n, (-r - w * t) / x)
                            } else {
                                H.oSet(i + 1, n, (-s - y * t) / z)
                            }
                        }

                        // overflow control
                        t = abs(H.oGet(i, n))
                        if (eps * t * t > 1) {
                            j = i
                            while (j <= n) {
                                H.oSet(j, n, H.oGet(j, n) / t)
                                j++
                            }
                        }
                    }
                    i--
                }
            } else if (q < 0.0f) {    // complex vector
                var l = n - 1
                val cr = CFloat()
                val ci = CFloat()

                // last vector component imaginary so matrix is triangular
                if (abs(H.oGet(n, n - 1)) > abs(H.oGet(n - 1, n))) {
                    H.oSet(n - 1, n - 1, q / H.oGet(n, n - 1))
                    H.oSet(n - 1, n, -(H.oGet(n, n) - p) / H.oGet(n, n - 1))
                } else {
                    ComplexDivision(0.0f, -H.oGet(n - 1, n), H.oGet(n - 1, n - 1) - p, q, cr, ci)
                    H.oSet(n - 1, n - 1, cr._val)
                    H.oSet(n - 1, n, ci._val)
                }
                H.oSet(n, n - 1, 0.0f)
                H.oSet(n, n, 1.0f)
                i = n - 2
                while (i >= 0) {
                    var ra: Float
                    var sa: Float
                    var vr: Float
                    var vi: Float
                    ra = 0.0f
                    sa = 0.0f
                    j = l
                    while (j <= n) {
                        ra = ra + H.oGet(i, j) * H.oGet(j, n - 1)
                        sa = sa + H.oGet(i, j) * H.oGet(j, n)
                        j++
                    }
                    w = H.oGet(i, i) - p
                    if (imaginaryEigenValues.p[i] < 0.0f) {
                        z = w
                        r = ra
                        s = sa
                    } else {
                        l = i
                        if (imaginaryEigenValues.p[i] == 0.0f) {
                            ComplexDivision(-ra, -sa, w, q, cr, ci)
                            H.oSet(i, n - 1, cr._val)
                            H.oSet(i, n, ci._val)
                        } else {
                            // solve complex equations
                            x = H.oGet(i, i + 1)
                            y = H.oGet(i + 1, i)
                            vr =
                                (realEigenValues.p[i] - p) * (realEigenValues.p[i] - p) + imaginaryEigenValues.p[i] * imaginaryEigenValues.p[i] - q * q
                            vi = (realEigenValues.p[i] - p) * 2.0f * q
                            if (vr == 0.0f && vi == 0.0f) {
                                vr = eps * norm * (abs(w) + abs(q) + abs(x) + abs(y) + abs(z))
                            }
                            ComplexDivision(x * r - z * ra + q * sa, x * s - z * sa - q * ra, vr, vi, cr, ci)
                            H.oSet(i, n - 1, cr._val)
                            H.oSet(i, n, ci._val)
                            if (abs(x) > abs(z) + abs(q)) {
                                H.oSet(i + 1, n - 1, (-ra - w * H.oGet(i, n - 1) + q * H.oGet(i, n)) / x)
                                H.oSet(i + 1, n, (-sa - w * H.oGet(i, n) - q * H.oGet(i, n - 1)) / x)
                            } else {
                                ComplexDivision(-r - y * H.oGet(i, n - 1), -s - y * H.oGet(i, n), z, q, cr, ci)
                                H.oSet(i + 1, n - 1, cr._val)
                                H.oSet(i + 1, n, ci._val)
                            }
                        }

                        // overflow control
                        t = Lib.Max(abs(H.oGet(i, n - 1)), abs(H.oGet(i, n)))
                        if (eps * t * t > 1) {
                            j = i
                            while (j <= n) {
                                H.oSet(j, n - 1, H.oGet(j, n - 1) / t)
                                H.oSet(j, n, H.oGet(j, n) / t)
                                j++
                            }
                        }
                    }
                    i--
                }
            }
            n--
        }

        // vectors of isolated roots
        i = 0
        while (i < numRows) {
            if (i < low || i > high) {
                j = i
                while (j < numRows) {
                    this.oSet(i, j, H.oGet(i, j))
                    j++
                }
            }
            i++
        }

        // back transformation to get eigenvectors of original matrix
        j = numRows - 1
        while (j >= low) {
            i = low
            while (i <= high) {
                z = 0.0f
                k = low
                while (k <= Lib.Min(j, high)) {
                    z = z + this.oGet(i, k) * H.oGet(k, j)
                    k++
                }
                this.oSet(i, j, z)
                i++
            }
            j--
        }
        return true
    }

    fun oGet(row: Int, column: Int): Float {
        return mat[column + row * numColumns]
    }

    fun oSet(row: Int, column: Int, value: Float): Float {
        return value.also { mat[column + row * numColumns] = it }
    }

    @Deprecated("")
    fun plusAssign(row: Int, column: Int, value: Double) {
        mat[column + row * numColumns] += value.toFloat()
    }

    @Deprecated("")
    fun minusAssign(row: Int, column: Int, value: Double) {
        mat[column + row * numColumns] -= value.toFloat()
    }

    @Deprecated("")
    fun timesAssign(row: Int, column: Int, value: Double) {
        mat[column + row * numColumns] *= value.toFloat()
    }

    @Deprecated("")
    fun divAssign(row: Int, column: Int, value: Double) {
        mat[column + row * numColumns] /= value.toFloat()
    }

    fun plusAssign(row: Int, column: Int, value: Float) {
        mat[column + row * numColumns] += value
    }

    fun minusAssign(row: Int, column: Int, value: Float) {
        mat[column + row * numColumns] -= value
    }

    fun timesAssign(row: Int, column: Int, value: Float) {
        mat[column + row * numColumns] *= value
    }

    fun divAssign(row: Int, column: Int, value: Float) {
        mat[column + row * numColumns] /= value
    }

    private fun oNegative(row: Int, column: Int) {
        mat[column + row * numColumns] = -mat[column + row * numColumns]
    }

    fun arraycopy(src: FloatArray, srcPos: Int, destPos: Int, length: Int) {
        System.arraycopy(src, srcPos, mat, destPos * numColumns, length)
    }

    fun arraycopy(src: FloatArray, destPos: Int, length: Int) {
        arraycopy(src, 0, destPos, length)
    }

    fun arraycopy(src: FloatBuffer, destPos: Int, length: Int) {
        arraycopy(TempDump.fbtofa(src), destPos, length)
    }

    fun SubVec63_oSet(vec6: Int, vec3: Int, v: idVec3) {
        assert(numColumns >= 6 && vec6 >= 0 && vec6 < numRows)
        val offset = vec6 * 6 + vec3 * 3
        mat[offset + 0] = v.x
        mat[offset + 1] = v.y
        mat[offset + 2] = v.z
    }

    fun SubVec63_Zero(vec6: Int, vec3: Int) {
        assert(numColumns >= 6 && vec6 >= 0 && vec6 < numRows)
        val offset = vec6 * 6 + vec3 * 3
        mat[offset + 2] = 0f
        mat[offset + 1] = mat[offset + 2]
        mat[offset + 0] = mat[offset + 1]
    }

    companion object {
        //===============================================================
        //
        //	idMatX - arbitrary sized dense real matrix
        //
        //  The matrix lives on 16 byte aligned and 16 byte padded memory.
        //
        //	NOTE: due to the temporary memory pool idMatX cannot be used by multiple threads.
        //
        //===============================================================
        const val MATX_MAX_TEMP = 1024
        private val temp: FloatArray =
            FloatArray(MATX_MAX_TEMP + 4) // used to store intermediate results

        //
        var DISABLE_RANDOM_TEST = false
        var MATX_SIMD = true
        private var tempIndex // index into memory pool, wraps around
                = 0
        private const val tempPtr // pointer to 16 byte aligned temporary memory
                = 0

        fun MATX_QUAD(x: Int): FloatArray {
            return FloatArray(x + 3 and 3.inv())
        }

        fun MATX_ALLOCA(n: Int): FloatArray {
            return MATX_QUAD(n)
        }

        //public	friend idMatX	operator*( const float a, const idMatX &m );
        fun times(a: Float, m: idMatX): idMatX {
            return m.oMultiply(a)
        }

        //public					~idMatX( void );
        fun times(vec: idVecX, m: idMatX): idVecX {
            var vec = vec
            vec = m.oMultiply(vec)
            return vec
        }

        fun Test() {
            var original: idMatX = idMatX()
            var m1 = idMatX()
            var m2 = idMatX()
            var m3: idMatX = idMatX()
            val q1 = idMatX()
            val q2 = idMatX()
            val r1 = idMatX()
            val r2 = idMatX()
            val v = idVecX()
            val w = idVecX()
            val u = idVecX()
            val c = idVecX()
            val d = idVecX()
            val size: Int = 6
            original.Random(size, size, 0)
            original = original.oMultiply(original.Transpose())
            val index1: IntArray = IntArray(size + 1)
            val index2: IntArray = IntArray(size + 1)

            /*
         idMatX::LowerTriangularInverse
         */m1.oSet(original)
            m1.ClearUpperTriangle()
            m2.oSet(m1)
            m2.InverseSelf()
            m1.LowerTriangularInverse()
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LowerTriangularInverse failed")
            }

            /*
         idMatX::UpperTriangularInverse
         */m1.oSet(original)
            m1.ClearLowerTriangle()
            m2.oSet(m1)
            m2.InverseSelf()
            m1.UpperTriangularInverse()
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::UpperTriangularInverse failed")
            }

            /*
         idMatX::Inverse_GaussJordan
         */m1.oSet(original)
            m1.Inverse_GaussJordan()
            m1.oSet(m1.timesAssign(original))
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::Inverse_GaussJordan failed")
            }

            /*
         idMatX::Inverse_UpdateRankOne
         */m1.oSet(original)
            m2.oSet(original)
            w.Random(size, 1)
            v.Random(size, 2)

            // invert m1
            m1.Inverse_GaussJordan()

            // modify and invert m2
            m2.Update_RankOne(v, w, 1.0f)
            if (!m2.Inverse_GaussJordan()) {
                assert(false)
            }

            // update inverse of m1
            m1.Inverse_UpdateRankOne(v, w, 1.0f)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Inverse_UpdateRankOne failed")
            }

            /*
         idMatX::Inverse_UpdateRowColumn
         */
            var offset: Int = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                v.Random(size, 1)
                w.Random(size, 2)
                w.p[offset] = 0.0f

                // invert m1
                m1.Inverse_GaussJordan()

                // modify and invert m2
                m2.Update_RowColumn(v, w, offset)
                if (!m2.Inverse_GaussJordan()) {
                    assert(false)
                }

                // update inverse of m1
                m1.Inverse_UpdateRowColumn(v, w, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::Inverse_UpdateRowColumn failed")
                }
                offset++
            }

            /*
         idMatX::Inverse_UpdateIncrement
         */m1.oSet(original)
            m2.oSet(original)
            v.Random(size + 1, 1)
            w.Random(size + 1, 2)
            w.p[size] = 0.0f

            // invert m1
            m1.Inverse_GaussJordan()

            // modify and invert m2
            m2.Update_Increment(v, w)
            if (!m2.Inverse_GaussJordan()) {
                assert(false)
            }

            // update inverse of m1
            m1.Inverse_UpdateIncrement(v, w)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Inverse_UpdateIncrement failed")
            }

            /*
         idMatX::Inverse_UpdateDecrement
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                v.SetSize(6)
                w.SetSize(6)
                for (i in 0 until size) {
                    v.p[i] = original.oGet(i, offset)
                    w.p[i] = original.oGet(offset, i)
                }

                // invert m1
                m1.Inverse_GaussJordan()

                // modify and invert m2
                m2.Update_Decrement(offset)
                if (!m2.Inverse_GaussJordan()) {
                    assert(false)
                }

                // update inverse of m1
                m1.Inverse_UpdateDecrement(v, w, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::Inverse_UpdateDecrement failed")
                }
                offset++
            }

            /*
         idMatX::LU_Factor
         */m1.oSet(original)
            m1.LU_Factor(null) // no pivoting
            m1.LU_UnpackFactors(m2, m3)
            m1.oSet(m2.oMultiply(m3))
            if (!original.Compare(m1, 1e-4f)) {
                idLib.common.Warning("idMatX::LU_Factor failed")
            }

            /*
         idMatX::LU_UpdateRankOne
         */m1.oSet(original)
            m2.oSet(original)
            w.Random(size, 1)
            v.Random(size, 2)

            // factor m1
            m1.LU_Factor(index1)

            // modify and factor m2
            m2.Update_RankOne(v, w, 1.0f)
            if (!m2.LU_Factor(index2)) {
                assert(false)
            }
            m2.LU_MultiplyFactors(m3, index2)
            m2.oSet(m3)

            // update factored m1
            m1.LU_UpdateRankOne(v, w, 1.0f, index1)
            m1.LU_MultiplyFactors(m3, index1)
            m1.oSet(m3)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LU_UpdateRankOne failed")
            }

            /*
         idMatX::LU_UpdateRowColumn
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                v.Random(size, 1)
                w.Random(size, 2)
                w.p[offset] = 0.0f

                // factor m1
                m1.LU_Factor(index1)

                // modify and factor m2
                m2.Update_RowColumn(v, w, offset)
                if (!m2.LU_Factor(index2)) {
                    assert(false)
                }
                m2.LU_MultiplyFactors(m3, index2)
                m2.oSet(m3)

                // update m1
                m1.LU_UpdateRowColumn(v, w, offset, index1)
                m1.LU_MultiplyFactors(m3, index1)
                m1.oSet(m3)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::LU_UpdateRowColumn failed")
                }
                offset++
            }

            /*
         idMatX::LU_UpdateIncrement
         */m1.oSet(original)
            m2.oSet(original)
            v.Random(size + 1, 1)
            w.Random(size + 1, 2)
            w.p[size] = 0.0f

            // factor m1
            m1.LU_Factor(index1)

            // modify and factor m2
            m2.Update_Increment(v, w)
            if (!m2.LU_Factor(index2)) {
                assert(false)
            }
            m2.LU_MultiplyFactors(m3, index2)
            m2.oSet(m3)

            // update factored m1
            m1.LU_UpdateIncrement(v, w, index1)
            m1.LU_MultiplyFactors(m3, index1)
            m1.oSet(m3)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LU_UpdateIncrement failed")
            }

            /*
         idMatX::LU_UpdateDecrement
         */offset = 0
            while (offset < size) {
                m1 = idMatX(original)
                m2 = idMatX(original)
                v.SetSize(6)
                w.SetSize(6)
                for (i in 0 until size) {
                    v.p[i] = original.oGet(i, offset)
                    w.p[i] = original.oGet(offset, i)
                }

                // factor m1
                m1.LU_Factor(index1)

                // modify and factor m2
                m2.Update_Decrement(offset)
                if (!m2.LU_Factor(index2)) {
                    assert(false)
                }
                m2.LU_MultiplyFactors(m3, index2)
                m2.oSet(m3)
                u.SetSize(6)
                for (i in 0 until size) {
                    u.p[i] = original.oGet(index1[offset], i)
                }

                // update factors of m1
                m1.LU_UpdateDecrement(v, w, u, offset, index1)
                m1.LU_MultiplyFactors(m3, index1)
                m1.oSet(m3)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::LU_UpdateDecrement failed")
                }
                offset++
            }

            /*
         idMatX::LU_Inverse
         */m2.oSet(original)
            m2.LU_Factor(null)
            m2.LU_Inverse(m1, null)
            m1.timesAssign(original)
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::LU_Inverse failed")
                //System.exit(9);
            }

            /*
         idMatX::QR_Factor
         */c.SetSize(size)
            d.SetSize(size)
            m1.oSet(original)
            m1.QR_Factor(c, d)
            m1.QR_UnpackFactors(q1, r1, c, d)
            m1.oSet(q1.oMultiply(r1))
            if (!original.Compare(m1, 1e-4f)) {
                idLib.common.Warning("idMatX::QR_Factor failed")
            }

            /*
         idMatX::QR_UpdateRankOne
         */c.SetSize(size)
            d.SetSize(size)
            m1.oSet(original)
            m2.oSet(original)
            w.Random(size, 0)
            v.oSet(w)

            // factor m1
            m1.QR_Factor(c, d)
            m1.QR_UnpackFactors(q1, r1, c, d)

            // modify and factor m2
            m2.Update_RankOne(v, w, 1.0f)
            if (!m2.QR_Factor(c, d)) {
                assert(false)
            }
            m2.QR_UnpackFactors(q2, r2, c, d)
            m2 = q2.oMultiply(r2)

            // update factored m1
            q1.QR_UpdateRankOne(r1, v, w, 1.0f)
            m1 = q1.oMultiply(r1)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::QR_UpdateRankOne failed")
            }

            /*
         idMatX::QR_UpdateRowColumn
         */offset = 0
            while (offset < size) {
                c.SetSize(size)
                d.SetSize(size)
                m1.oSet(original)
                m2.oSet(original)
                v.Random(size, 1)
                w.Random(size, 2)
                w.p[offset] = 0.0f

                // factor m1
                m1.QR_Factor(c, d)
                m1.QR_UnpackFactors(q1, r1, c, d)

                // modify and factor m2
                m2.Update_RowColumn(v, w, offset)
                if (!m2.QR_Factor(c, d)) {
                    assert(false)
                }
                m2.QR_UnpackFactors(q2, r2, c, d)
                m2 = q2.oMultiply(r2)

                // update m1
                q1.QR_UpdateRowColumn(r1, v, w, offset)
                m1 = q1.oMultiply(r1)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::QR_UpdateRowColumn failed")
                }
                offset++
            }

            /*
         idMatX::QR_UpdateIncrement
         */c.SetSize(size + 1)
            d.SetSize(size + 1)
            m1.oSet(original)
            m2.oSet(original)
            v.Random(size + 1, 1)
            w.Random(size + 1, 2)
            w.p[size] = 0.0f

            // factor m1
            m1.QR_Factor(c, d)
            m1.QR_UnpackFactors(q1, r1, c, d)

            // modify and factor m2
            m2.Update_Increment(v, w)
            if (!m2.QR_Factor(c, d)) {
                assert(false)
            }
            m2.QR_UnpackFactors(q2, r2, c, d)
            m2 = q2.oMultiply(r2)

            // update factored m1
            q1.QR_UpdateIncrement(r1, v, w)
            m1 = q1.oMultiply(r1)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::QR_UpdateIncrement failed")
            }

            /*
         idMatX::QR_UpdateDecrement
         */offset = 0
            while (offset < size) {
                c.SetSize(size + 1)
                d.SetSize(size + 1)
                m1.oSet(original)
                m2.oSet(original)
                v.SetSize(6)
                w.SetSize(6)
                for (i in 0 until size) {
                    v.p[i] = original.oGet(i, offset)
                    w.p[i] = original.oGet(offset, i)
                }

                // factor m1
                m1.QR_Factor(c, d)
                m1.QR_UnpackFactors(q1, r1, c, d)

                // modify and factor m2
                m2.Update_Decrement(offset)
                if (!m2.QR_Factor(c, d)) {
                    assert(false)
                }
                m2.QR_UnpackFactors(q2, r2, c, d)
                m2 = q2.oMultiply(r2)

                // update factors of m1
                q1.QR_UpdateDecrement(r1, v, w, offset)
                m1.oSet(q1.oMultiply(r1))
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::QR_UpdateDecrement failed")
                }
                offset++
            }

            /*
         idMatX::QR_Inverse
         */m2.oSet(original)
            m2.QR_Factor(c, d)
            m2.QR_Inverse(m1, c, d)
            m1.timesAssign(original)
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::QR_Inverse failed")
            }

            /*
         idMatX::SVD_Factor
         */m1.oSet(original)
            m3.Zero(size, size)
            w.Zero(size)
            m1.SVD_Factor(w, m3)
            m2.Diag(w)
            m3.TransposeSelf()
            m1.oSet(m1.oMultiply(m2).oMultiply(m3))
            if (!original.Compare(m1, 1e-4f)) {
                idLib.common.Warning("idMatX::SVD_Factor failed")
            }

            /*
         idMatX::SVD_Inverse
         */m2.oSet(original)
            m2.SVD_Factor(w, m3)
            m2.SVD_Inverse(m1, w, m3)
            m1.timesAssign(original)
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::SVD_Inverse failed")
            }

            /*
         idMatX::Cholesky_Factor
         */m1.oSet(original)
            m1.Cholesky_Factor()
            m1.Cholesky_MultiplyFactors(m2)
            if (!original.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Cholesky_Factor failed")
            }

            /*
         idMatX::Cholesky_UpdateRankOne
         */m1.oSet(original)
            m2.oSet(original)
            w.Random(size, 0)

            // factor m1
            m1.Cholesky_Factor()
            m1.ClearUpperTriangle()

            // modify and factor m2
            m2.Update_RankOneSymmetric(w, 1.0f)
            if (!m2.Cholesky_Factor()) {
                assert(false)
            }
            m2.ClearUpperTriangle()

            // update factored m1
            m1.Cholesky_UpdateRankOne(w, 1.0f, 0)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Cholesky_UpdateRankOne failed")
            }

            /*
         idMatX::Cholesky_UpdateRowColumn
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)

                // factor m1
                m1.Cholesky_Factor()
                m1.ClearUpperTriangle()
                val pdtable = intArrayOf(1, 0, 1, 0, 0, 0)
                w.Random(size, pdtable[offset])
                w.timesAssign(0.1f)

                // modify and factor m2
                m2.Update_RowColumnSymmetric(w, offset)
                if (!m2.Cholesky_Factor()) {
                    assert(false)
                }
                m2.ClearUpperTriangle()

                // update m1
                m1.Cholesky_UpdateRowColumn(w, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::Cholesky_UpdateRowColumn failed")
                }
                offset++
            }

            /*
         idMatX::Cholesky_UpdateIncrement
         */m1.Random(size + 1, size + 1, 0)
            m3.oSet(m1.oMultiply(m1.Transpose()))
            m1.SquareSubMatrix(m3, size)
            m2.oSet(m1)
            w.SetSize(size + 1)
            for (i in 0 until size + 1) {
                w.p[i] = m3.oGet(size, i)
            }

            // factor m1
            m1.Cholesky_Factor()

            // modify and factor m2
            m2.Update_IncrementSymmetric(w)
            if (!m2.Cholesky_Factor()) {
                assert(false)
            }

            // update factored m1
            m1.Cholesky_UpdateIncrement(w)
            m1.ClearUpperTriangle()
            m2.ClearUpperTriangle()
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Cholesky_UpdateIncrement failed")
            }

            /*
         idMatX::Cholesky_UpdateDecrement
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                v.SetSize(6)
                for (i in 0 until size) {
                    v.p[i] = original.oGet(i, offset)
                }

                // factor m1
                m1.Cholesky_Factor()

                // modify and factor m2
                m2.Update_Decrement(offset)
                if (!m2.Cholesky_Factor()) {
                    assert(false)
                }

                // update factors of m1
                m1.Cholesky_UpdateDecrement(v, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::Cholesky_UpdateDecrement failed")
                }
                offset += size - 1
            }

            /*
         idMatX::Cholesky_Inverse
         */m2.oSet(original)
            m2.Cholesky_Factor()
            m2.Cholesky_Inverse(m1)
            m1.timesAssign(original)
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::Cholesky_Inverse failed")
            }

            /*
         idMatX::LDLT_Factor
         */m1.oSet(original)
            m1.LDLT_Factor()
            m1.LDLT_MultiplyFactors(m2)
            if (!original.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LDLT_Factor failed")
            }
            m1.LDLT_UnpackFactors(m2, m3)
            m2 = m2.oMultiply(m3).oMultiply(m2.Transpose())
            if (!original.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LDLT_Factor failed")
            }

            /*
         idMatX::LDLT_UpdateRankOne
         */m1.oSet(original)
            m2.oSet(original)
            w.Random(size, 0)

            // factor m1
            m1.LDLT_Factor()
            m1.ClearUpperTriangle()

            // modify and factor m2
            m2.Update_RankOneSymmetric(w, 1.0f)
            if (!m2.LDLT_Factor()) {
                assert(false)
            }
            m2.ClearUpperTriangle()

            // update factored m1
            m1.LDLT_UpdateRankOne(w, 1.0f, 0)
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LDLT_UpdateRankOne failed")
            }

            /*
         idMatX::LDLT_UpdateRowColumn
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                w.Random(size, 0)

                // factor m1
                m1.LDLT_Factor()
                m1.ClearUpperTriangle()

                // modify and factor m2
                m2.Update_RowColumnSymmetric(w, offset)
                if (!m2.LDLT_Factor()) {
                    assert(false)
                }
                m2.ClearUpperTriangle()

                // update m1
                m1.LDLT_UpdateRowColumn(w, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::LDLT_UpdateRowColumn failed")
                }
                offset++
            }

            /*
         idMatX::LDLT_UpdateIncrement
         */m1.Random(size + 1, size + 1, 0)
            m3 = m1.oMultiply(m1.Transpose())
            m1.SquareSubMatrix(m3, size)
            m2.oSet(m1)
            w.SetSize(size + 1)
            for (i in 0 until size + 1) {
                w.p[i] = m3.oGet(size, i)
            }

            // factor m1
            m1.LDLT_Factor()

            // modify and factor m2
            m2.Update_IncrementSymmetric(w)
            if (!m2.LDLT_Factor()) {
                assert(false)
            }

            // update factored m1
            m1.LDLT_UpdateIncrement(w)
            m1.ClearUpperTriangle()
            m2.ClearUpperTriangle()
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::LDLT_UpdateIncrement failed")
            }

            /*
         idMatX::LDLT_UpdateDecrement
         */offset = 0
            while (offset < size) {
                m1.oSet(original)
                m2.oSet(original)
                v.SetSize(6)
                for (i in 0 until size) {
                    v.p[i] = original.oGet(i, offset)
                }

                // factor m1
                m1.LDLT_Factor()

                // modify and factor m2
                m2.Update_Decrement(offset)
                if (!m2.LDLT_Factor()) {
                    assert(false)
                }

                // update factors of m1
                m1.LDLT_UpdateDecrement(v, offset)
                if (!m1.Compare(m2, 1e-3f)) {
                    idLib.common.Warning("idMatX::LDLT_UpdateDecrement failed")
                }
                offset++
            }

            /*
         idMatX::LDLT_Inverse
         */m2.oSet(original)
            m2.LDLT_Factor()
            m2.LDLT_Inverse(m1)
            m1.timesAssign(original)
            if (!m1.IsIdentity(1e-4f)) {
                idLib.common.Warning("idMatX::LDLT_Inverse failed")
            }

            /*
         idMatX::Eigen_SolveSymmetricTriDiagonal
         */m3.oSet(original)
            m3.TriDiagonal_ClearTriangles()
            m1.oSet(m3)
            v.SetSize(size)
            m1.Eigen_SolveSymmetricTriDiagonal(v)
            m3.TransposeMultiply(m2, m1)
            for (i in 0 until size) {
                for (j in 0 until size) {
                    m1.timesAssign(i, j, v.p[j])
                }
            }
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Eigen_SolveSymmetricTriDiagonal failed")
            }

            /*
         idMatX::Eigen_SolveSymmetric
         */m3.oSet(original)
            m1.oSet(m3)
            v.SetSize(size)
            m1.Eigen_SolveSymmetric(v)
            m3.TransposeMultiply(m2, m1)
            for (i in 0 until size) {
                for (j in 0 until size) {
                    m1.timesAssign(i, j, v.p[j])
                }
            }
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Eigen_SolveSymmetric failed")
            }

            /*
         idMatX::Eigen_Solve
         */m3.oSet(original)
            m1.oSet(m3)
            v.SetSize(size)
            w.SetSize(size)
            m1.Eigen_Solve(v, w)
            m3.TransposeMultiply(m2, m1)
            for (i in 0 until size) {
                for (j in 0 until size) {
                    m1.timesAssign(i, j, v.p[j])
                }
            }
            if (!m1.Compare(m2, 1e-4f)) {
                idLib.common.Warning("idMatX::Eigen_Solve failed")
            }
        }
    }
}