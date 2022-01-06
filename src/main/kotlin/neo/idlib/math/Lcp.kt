package neo.idlib.math

import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.idlib.Lib.idLib
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idSwap
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Vector.idVecX
import java.nio.FloatBuffer
import kotlin.math.abs

/**
 *
 */
class Lcp {
    const val LCP_ACCEL_EPSILON = 1e-5f
    const val LCP_BOUND_EPSILON = 1e-5f
    const val LCP_DELTA_ACCEL_EPSILON = 1e-9f
    const val LCP_DELTA_FORCE_EPSILON = 1e-9f
    val lcp_showFailures: idCVar =
        idCVar("lcp_showFailures", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "show LCP solver failures")

    /**
     *
     */
    fun clam(src: idMatX, numClamped: Int): FloatArray {
        return clam(src.ToFloatPtr(), numClamped * src.GetNumColumns())
    }

    fun clam(src: idVecX, numClamped: Int): FloatArray {
        return clam(src.ToFloatPtr(), numClamped)
    }

    fun clam(src: FloatArray, numClamped: Int): FloatArray {
        val clamped = FloatArray(src.size - numClamped)
        System.arraycopy(src, numClamped, clamped, 0, clamped.size)
        return clamped
    }

    fun unClam(dst: idMatX, clamArray: FloatArray): FloatArray {
        return unClam(dst.ToFloatPtr(), clamArray)
    }

    fun unClam(dst: idVecX, clamArray: FloatArray): FloatArray {
        return unClam(dst.ToFloatPtr(), clamArray)
    }

    fun unClam(dst: FloatArray, clamArray: FloatArray): FloatArray {
        System.arraycopy(clamArray, 0, dst, dst.size - clamArray.size, clamArray.size)
        return dst
    }

    fun clam(src: CharArray, numClamped: Int): CharArray {
        val clamped = CharArray(src.size - numClamped)
        System.arraycopy(src, numClamped, clamped, 0, clamped.size)
        return clamped
    }

    fun unClam(dst: CharArray, clamArray: CharArray): CharArray {
        System.arraycopy(clamArray, 0, dst, dst.size - clamArray.size, clamArray.size)
        return dst
    }

    /*
     ===============================================================================

     Box Constrained Mixed Linear Complementarity Problem solver

     A is a matrix of dimension n*n and x, b, lo, hi are vectors of dimension n

     Solve: Ax = b + t, where t is a vector of dimension n, with
     complementarity condition: (x[i] - lo[i]) * (x[i] - hi[i]) * t[i] = 0
     such that for each 0 <= i < n one of the following holds:

     1. lo[i] < x[i] < hi[i], t[i] == 0
     2. x[i] == lo[i], t[i] >= 0
     3. x[i] == hi[i], t[i] <= 0

     Partly bounded or unbounded variables can have lo[i] and/or hi[i]
     set to negative/positive idMath::INFITITY respectively.

     If boxIndex != NULL and boxIndex[i] != -1 then

     lo[i] = - fabs( lo[i] * x[boxIndex[i]] )
     hi[i] = fabs( hi[i] * x[boxIndex[i]] )
     boxIndex[boxIndex[i]] must be -1

     Before calculating any of the bounded x[i] with boxIndex[i] != -1 the
     solver calculates all unbounded x[i] and all x[i] with boxIndex[i] == -1.

     ===============================================================================
     */
    abstract class idLCP {
        protected var maxIterations = 0

        // A must be a square matrix
        fun AllocSquare(): idLCP {
            val lcp: idLCP = idLCP_Square()
            lcp.SetMaxIterations(32)
            return lcp
        }

        //public	virtual			~idLCP( void );
        fun Solve(A: idMatX, x: idVecX, b: idVecX, lo: idVecX, hi: idVecX): Boolean {
            return Solve(A, x, b, lo, hi, null)
        }

        abstract fun Solve(A: idMatX, x: idVecX, b: idVecX, lo: idVecX, hi: idVecX, boxIndex: IntArray?): Boolean
        fun SetMaxIterations(max: Int) {
            maxIterations = max
        }

        fun GetMaxIterations(): Int {
            return maxIterations
        }

        companion object {
            // A must be a symmetric matrix
            fun AllocSymmetric(): idLCP {
                val lcp: idLCP = idLCP_Symmetric()
                lcp.SetMaxIterations(32)
                return lcp
            }
        }
    }

    //===============================================================
    //                                                        M
    //  idLCP_Square                                         MrE
    //                                                        E
    //===============================================================
    internal class idLCP_Square : idLCP() {
        val b: idVecX = idVecX() // right hand side
        val clamped: idMatX = idMatX() // LU factored sub matrix for clamped variables
        val delta_f: idVecX = idVecX()
        val delta_a: idVecX = idVecX() // delta force and delta acceleration
        val diagonal: idVecX =
            idVecX() // reciprocal of diagonal of U of the LU factored sub matrix for clamped variables
        val f: idVecX = idVecX()
        val a: idVecX = idVecX() // force and acceleration
        val lo: idVecX = idVecX()
        val hi: idVecX = idVecX() // low and high bounds
        private val m: idMatX = idMatX() // original matrix
        var numClamped // number of clamped variables
                = 0
        var numUnbounded // number of unbounded variables
                = 0
        private var boxIndex // box index
                : IntArray? = null
        private var padded // set to true if the rows of the initial matrix are 16 byte padded
                = false
        private var permuted // index to keep track of the permutation
                : IntArray = IntArray(0)
        private var rowPtrs // pointers to the rows of m
                : Array<FloatBuffer> = Array(0) { FloatBuffer.allocate(0) }
        private var side // tells if a variable is at the low boundary = -1, high boundary = 1 or inbetween = 0
                : IntArray = IntArray(0)

        //
        //
        override fun Solve(
            o_m: idMatX,
            o_x: idVecX,
            o_b: idVecX,
            o_lo: idVecX,
            o_hi: idVecX,
            o_boxIndex: IntArray?
        ): Boolean {
            var i: Int
            var j: Int
            var n: Int
            var boxStartIndex: Int
            val limit = CInt()
            val limitSide = CInt()
            var dir: Float
            var s: Float
            val dot = CFloat()
            val maxStep = CFloat()
            var failed: String? = null

            // true when the matrix rows are 16 byte padded
            padded = o_m.GetNumRows() + 3 and 3.inv() == o_m.GetNumColumns()
            assert(padded || o_m.GetNumRows() == o_m.GetNumColumns())
            assert(o_x.GetSize() == o_m.GetNumRows())
            assert(o_b.GetSize() == o_m.GetNumRows())
            assert(o_lo.GetSize() == o_m.GetNumRows())
            assert(o_hi.GetSize() == o_m.GetNumRows())

            // allocate memory for permuted input
            f.SetData(o_m.GetNumRows(), idVecX.VECX_ALLOCA(o_m.GetNumRows()))
            a.SetData(o_b.GetSize(), idVecX.VECX_ALLOCA(o_b.GetSize()))
            b.SetData(o_b.GetSize(), idVecX.VECX_ALLOCA(o_b.GetSize()))
            lo.SetData(o_lo.GetSize(), idVecX.VECX_ALLOCA(o_lo.GetSize()))
            hi.SetData(o_hi.GetSize(), idVecX.VECX_ALLOCA(o_hi.GetSize()))
            if (o_boxIndex != null) {
//		boxIndex = (int *)_alloca16( o_x.GetSize() * sizeof( int ) );
//		memcpy( boxIndex, o_boxIndex, o_x.GetSize() * sizeof( int ) );
                boxIndex = IntArray(o_x.GetSize())
                System.arraycopy(o_boxIndex, 0, boxIndex, 0, o_x.GetSize())
            } else {
                boxIndex = null
            }

            // we override the const on o_m here but on exit the matrix is unchanged
            val const_cast = o_m.ToFloatPtr()
            m.SetData(o_m.GetNumRows(), o_m.GetNumColumns(), const_cast)
            o_m.FromFloatPtr(const_cast)
            f.Zero()
            a.Zero()
            b.set(o_b)
            lo.set(o_lo)
            hi.set(o_hi)

            // pointers to the rows of m
            rowPtrs =
                Array(m.GetNumRows()) { FloatBuffer.allocate(0) }//rowPtrs = (float **) _alloca16( m.GetNumRows() * sizeof( float * ) );
            i = 0
            while (i < m.GetNumRows()) {
                rowPtrs[i] = m.GetRowPtr(i)
                i++
            }

            // tells if a variable is at the low boundary, high boundary or inbetween
//	side = (int *) _alloca16( m.GetNumRows() * sizeof( int ) );
            side = IntArray(m.GetNumRows())

            // index to keep track of the permutation
//	permuted = (int *) _alloca16( m.GetNumRows() * sizeof( int ) );
            permuted = IntArray(m.GetNumRows())
            i = 0
            while (i < m.GetNumRows()) {
                permuted[i] = i
                i++
            }

            // permute input so all unbounded variables come first
            numUnbounded = 0
            i = 0
            while (i < m.GetNumRows()) {
                if (lo.p[i] == -idMath.INFINITY && hi.p[i] == idMath.INFINITY) {
                    if (numUnbounded != i) {
                        Swap(numUnbounded, i)
                    }
                    numUnbounded++
                }
                i++
            }

            // permute input so all variables using the boxIndex come last
            boxStartIndex = m.GetNumRows()
            if (boxIndex != null) {
                i = m.GetNumRows() - 1
                while (i >= numUnbounded) {
                    if (boxIndex!![i] >= 0 && (lo.p[i] != -idMath.INFINITY || hi.p[i] != idMath.INFINITY)) {
                        boxStartIndex--
                        if (boxStartIndex != i) {
                            Swap(boxStartIndex, i)
                        }
                    }
                    i--
                }
            }

            // sub matrix for factorization
            clamped.SetData(
                m.GetNumRows(),
                m.GetNumColumns(),
                idMatX.MATX_ALLOCA(m.GetNumRows() * m.GetNumColumns())
            )
            diagonal.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))

            // all unbounded variables are clamped
            numClamped = numUnbounded

            // if there are unbounded variables
            if (numUnbounded != 0) {

                // factor and solve for unbounded variables
                if (!FactorClamped()) {
                    idLib.common.Printf("idLCP_Square::Solve: unbounded factorization failed\n")
                    return false
                }
                SolveClamped(f, b.ToFloatPtr())

                // if there are no bounded variables we are done
                if (numUnbounded == m.GetNumRows()) {
                    o_x.set(f) // the vector is not permuted
                    return true
                }
            }

//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
            var numIgnored = 0
            //#endif

            // allocate for delta force and delta acceleration
            delta_f.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))
            delta_a.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))

            // solve for bounded variables
            failed = null
            i = numUnbounded
            while (i < m.GetNumRows()) {


                // once we hit the box start index we can initialize the low and high boundaries of the variables using the box index
                if (i == boxStartIndex) {
                    j = 0
                    while (j < boxStartIndex) {
                        o_x.p[permuted[j]] = f.p[j]
                        j++
                    }
                    j = boxStartIndex
                    while (j < m.GetNumRows()) {
                        s = o_x.p[boxIndex!![j]]
                        if (lo.p[j] != -idMath.INFINITY) {
                            lo.p[j] = -abs(lo.p[j] * s)
                        }
                        if (hi.p[j] != idMath.INFINITY) {
                            hi.p[j] = abs(hi.p[j] * s)
                        }
                        j++
                    }
                }

                // calculate acceleration for current variable
                Simd.SIMDProcessor.Dot(dot, rowPtrs[i], f.ToFloatPtr(), i)
                a.p[i] = dot._val - b.p[i]

                // if already at the low boundary
                if (lo.p[i] >= -LCP_BOUND_EPSILON && a.p[i] >= -LCP_ACCEL_EPSILON) {
                    side[i] = -1
                    i++
                    continue
                }

                // if already at the high boundary
                if (hi.p[i] <= LCP_BOUND_EPSILON && a.p[i] <= LCP_ACCEL_EPSILON) {
                    side[i] = 1
                    i++
                    continue
                }

                // if inside the clamped region
                if (abs(a.p[i]) <= LCP_ACCEL_EPSILON) {
                    side[i] = 0
                    AddClamped(i)
                    i++
                    continue
                }

                // drive the current variable into a valid region
                n = 0
                while (n < maxIterations) {


                    // direction to move
                    dir = if (a.p[i] <= 0.0f) {
                        1.0f
                    } else {
                        -1.0f
                    }

                    // calculate force delta
                    CalcForceDelta(i, dir)

                    // calculate acceleration delta: delta_a = m * delta_f;
                    CalcAccelDelta(i)

                    // maximum step we can take
                    GetMaxStep(i, dir, maxStep, limit, limitSide)
                    if (maxStep._val <= 0.0f) {
//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
                        // ignore the current variable completely
                        hi.p[i] = 0.0f
                        lo.p[i] = hi.p[i]
                        f.p[i] = 0.0f
                        side[i] = -1
                        numIgnored++
                        //#else
//				failed = va( "invalid step size %.4f", maxStep );
//#endif
                        break
                    }

                    // change force
                    ChangeForce(i, maxStep._val)

                    // change acceleration
                    ChangeAccel(i, maxStep._val)

                    // clamp/unclamp the variable that limited this step
                    side[limit._val] = limitSide._val
                    when (limitSide._val) {
                        0 -> {
                            a.p[limit._val] = 0.0f
                            AddClamped(limit._val)
                        }
                        -1 -> {
                            f.p[limit._val] = lo.p[limit._val]
                            if (limit._val != i) {
                                RemoveClamped(limit._val)
                            }
                        }
                        1 -> {
                            f.p[limit._val] = hi.p[limit._val]
                            if (limit._val != i) {
                                RemoveClamped(limit._val)
                            }
                        }
                    }

                    // if the current variable limited the step we can continue with the next variable
                    if (limit._val == i) {
                        break
                    }
                    n++
                }
                i++
                if (n >= maxIterations) {
                    failed = String.format("max iterations %d", maxIterations)
                    break
                }

                if (failed != null) {
                    break
                }
            }

//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
            if (numIgnored != 0) {
                if (lcp_showFailures.GetBool()) {
                    idLib.common.Printf(
                        "idLCP_Symmetric::Solve: %d of %d bounded variables ignored\n",
                        numIgnored,
                        m.GetNumRows() - numUnbounded
                    )
                }
            }
            //#endif

            // if failed clear remaining forces
            if (failed != null) {
                if (lcp_showFailures.GetBool()) {
                    idLib.common.Printf(
                        "idLCP_Square::Solve: %s (%d of %d bounded variables ignored)\n",
                        failed,
                        m.GetNumRows() - i,
                        m.GetNumRows() - numUnbounded
                    )
                }
                j = i
                while (j < m.GetNumRows()) {
                    f.p[j] = 0.0f
                    j++
                }
            }

//#if defined(_DEBUG) && 0
//	if ( !failed ) {
//		// test whether or not the solution satisfies the complementarity conditions
//		for ( i = 0; i < m.GetNumRows(); i++ ) {
//			a[i] = -b[i];
//			for ( j = 0; j < m.GetNumRows(); j++ ) {
//				a[i] += rowPtrs[i][j] * f[j];
//			}
//
//			if ( f[i] == lo[i] ) {
//				if ( lo[i] != hi[i] && a[i] < -LCP_ACCEL_EPSILON ) {
//					int bah1 = 1;
//				}
//			} else if ( f[i] == hi[i] ) {
//				if ( lo[i] != hi[i] && a[i] > LCP_ACCEL_EPSILON ) {
//					int bah2 = 1;
//				}
//			} else if ( f[i] < lo[i] || f[i] > hi[i] || Math.abs( a[i] ) > 1.0f ) {
//				int bah3 = 1;
//			}
//		}
//	}
//#endif
            // unpermute result
            i = 0
            while (i < f.GetSize()) {
                o_x.p[permuted[i]] = f.p[i]
                i++
            }

            // unpermute original matrix
            i = 0
            while (i < m.GetNumRows()) {
                j = 0
                while (j < m.GetNumRows()) {
                    if (permuted[j] == i) {
                        break
                    }
                    j++
                }
                if (i != j) {
                    m.SwapColumns(i, j)
                    idSwap(permuted, permuted, i, j)
                }
                i++
            }
            return true
        }

        private fun FactorClamped(): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var s: Float
            var d: Float
            i = 0
            while (i < numClamped) {

//		memcpy( clamped[i], rowPtrs[i], numClamped * sizeof( float ) );
                clamped.arraycopy(rowPtrs[i], i, numClamped) //TODO:check two dimensional array
                i++
            }
            i = 0
            while (i < numClamped) {
                s = abs(clamped.get(i)[i])
                if (s == 0.0f) {
                    return false
                }
                d = 1.0f / clamped.get(i)[i]
                diagonal.p[i] = d
                j = i + 1
                while (j < numClamped) {

//			clamped[j][i] *= d;
                    clamped.timesAssign(j, i, d)
                    j++
                }
                j = i + 1
                while (j < numClamped) {
                    d = clamped.get(j)[i]
                    k = i + 1
                    while (k < numClamped) {
                        clamped.minusAssign(j, k, d * clamped.get(i)[k])
                        k++
                    }
                    j++
                }
                i++
            }
            return true
        }

        fun SolveClamped(x: idVecX, b: FloatArray) {
            var i: Int
            var j: Int
            var sum: Float

            // solve L
            i = 0
            while (i < numClamped) {
                sum = b[i]
                j = 0
                while (j < i) {
                    sum -= clamped.get(i)[j] * x.p[j]
                    j++
                }
                x.p[i] = sum
                i++
            }

            // solve U
            i = numClamped - 1
            while (i >= 0) {
                sum = x.p[i]
                j = i + 1
                while (j < numClamped) {
                    sum -= clamped.get(i)[j] * x.p[j]
                    j++
                }
                x.p[i] = sum * diagonal.p[i]
                i--
            }
        }

        fun Swap(i: Int, j: Int) {
            if (i == j) {
                return
            }
            idSwap(rowPtrs, rowPtrs, i, j)
            m.SwapColumns(i, j)
            b.SwapElements(i, j)
            lo.SwapElements(i, j)
            hi.SwapElements(i, j)
            a.SwapElements(i, j)
            f.SwapElements(i, j)
            if (null != boxIndex) {
                idSwap(boxIndex!!, boxIndex!!, i, j)
            }
            idSwap(side, side, i, j)
            idSwap(permuted, permuted, i, j)
        }

        fun AddClamped(r: Int) {
            var i: Int
            var j: Int
            var sum: Float
            assert(r >= numClamped)

            // add a row at the bottom and a column at the right of the factored
            // matrix for the clamped variables
            Swap(numClamped, r)

            // add row to L
            i = 0
            while (i < numClamped) {
                sum = rowPtrs[numClamped].get(i)
                j = 0
                while (j < i) {
                    sum -= clamped.get(numClamped)[j] * clamped.get(j)[i]
                    j++
                }
                clamped.set(numClamped, i, sum * diagonal.p[i])
                i++
            }

            // add column to U
            i = 0
            while (i <= numClamped) {
                sum = rowPtrs[i].get(numClamped)
                j = 0
                while (j < i) {
                    sum -= clamped.get(i)[j] * clamped.get(j)[numClamped]
                    j++
                }
                clamped.set(i, numClamped, sum)
                i++
            }
            diagonal.p[numClamped] = 1.0f / clamped.get(numClamped)[numClamped]
            numClamped++
        }

        fun RemoveClamped(r: Int) {
            var i: Int
            var j: Int
            val y0: FloatArray
            val y1: FloatArray
            val z0: FloatArray
            val z1: FloatArray
            var diag: Float
            var beta0: Float
            var beta1: Float
            var p0: Float
            var p1: Float
            var q0: Float
            var q1: Float
            var d: Float
            assert(r < numClamped)
            numClamped--

            // no need to swap and update the factored matrix when the last row and column are removed
            if (r == numClamped) {
                return
            }

//	y0 = (float *) _alloca16( numClamped * sizeof( float ) );
//	z0 = (float *) _alloca16( numClamped * sizeof( float ) );
//	y1 = (float *) _alloca16( numClamped * sizeof( float ) );
//	z1 = (float *) _alloca16( numClamped * sizeof( float ) );
            y0 = FloatArray(numClamped)
            z0 = FloatArray(numClamped)
            y1 = FloatArray(numClamped)
            z1 = FloatArray(numClamped)

            // the row/column need to be subtracted from the factorization
            i = 0
            while (i < numClamped) {
                y0[i] = -rowPtrs[i].get(r)
                i++
            }

//	memset( y1, 0, numClamped * sizeof( float ) );
            y1[r] = 1.0f

//	memset( z0, 0, numClamped * sizeof( float ) );
            z0[r] = 1.0f
            i = 0
            while (i < numClamped) {
                z1[i] = -rowPtrs[r].get(i)
                i++
            }

            // swap the to be removed row/column with the last row/column
            Swap(r, numClamped)

            // the swapped last row/column need to be added to the factorization
            i = 0
            while (i < numClamped) {
                y0[i] += rowPtrs[i].get(r)
                i++
            }
            i = 0
            while (i < numClamped) {
                z1[i] += rowPtrs[r].get(i)
                i++
            }
            z1[r] = 0.0f

            // update the beginning of the to be updated row and column
            i = 0
            while (i < r) {
                p0 = y0[i]
                beta1 = (z1[i] * diagonal.p[i])
                clamped.plusAssign(i, r, p0)
                j = i + 1
                while (j < numClamped) {
                    z1[j] -= beta1 * clamped.get(i)[j]
                    j++
                }
                j = i + 1
                while (j < numClamped) {
                    y0[j] -= p0 * clamped.get(j)[i]
                    j++
                }
                clamped.plusAssign(r, i, beta1)
                i++
            }

            // update the lower right corner starting at r,r
            i = r
            while (i < numClamped) {
                diag = clamped.get(i)[i]
                p0 = y0[i]
                p1 = z0[i]
                diag += p0 * p1
                if (diag == 0.0f) {
                    idLib.common.Printf("idLCP_Square::RemoveClamped: updating factorization failed\n")
                    return
                }
                beta0 = p1 / diag
                q0 = y1[i]
                q1 = z1[i]
                diag += q0 * q1
                if (diag == 0.0f) {
                    idLib.common.Printf("idLCP_Square::RemoveClamped: updating factorization failed\n")
                    return
                }
                d = 1.0f / diag
                beta1 = q1 * d
                clamped.set(i, i, diag)
                diagonal.p[i] = d
                j = i + 1
                while (j < numClamped) {
                    d = clamped.get(i)[j]
                    d += p0 * z0[j]
                    z0[j] -= beta0 * d
                    d += q0 * z1[j]
                    z1[j] -= beta1 * d
                    clamped.set(i, j, d)
                    j++
                }
                j = i + 1
                while (j < numClamped) {
                    d = clamped.get(j)[i]
                    y0[j] -= p0 * d
                    d += beta0 * y0[j]
                    y1[j] -= q0 * d
                    d += beta1 * y1[j]
                    clamped.set(j, i, d)
                    j++
                }
                i++
            }
            return
        }

        /*
         ============
         idLCP_Square::CalcForceDelta

         modifies this->delta_f
         ============
         */
        private fun CalcForceDelta(d: Int, dir: Float) {
            var i: Int
            var ptr: FloatArray
            delta_f.p[d] = dir
            if (numClamped == 0) {
                return
            }

            // get column d of matrix
//	ptr = (float *) _alloca16( numClamped * sizeof( float ) );
            ptr = FloatArray(numClamped)
            i = 0
            while (i < numClamped) {
                ptr[i] = rowPtrs[i].get(d)
                i++
            }

            // solve force delta
            SolveClamped(delta_f, ptr)

            // flip force delta based on direction
            if (dir > 0.0f) {
                ptr = delta_f.ToFloatPtr()
                i = 0
                while (i < numClamped) {
                    ptr[i] = -ptr[i]
                    i++
                }
            }
        }

        /*
         ============
         idLCP_Square::CalcAccelDelta

         modifies this->delta_a and uses this->delta_f
         ============
         */
        private fun CalcAccelDelta(d: Int) {
            var j: Int
            val dot = CFloat()

            // only the not clamped variables, including the current variable, can have a change in acceleration
            j = numClamped
            while (j <= d) {

                // only the clamped variables and the current variable have a force delta unequal zero
                Simd.SIMDProcessor.Dot(dot, rowPtrs[j], delta_f.ToFloatPtr(), numClamped)
                delta_a.p[j] = dot._val + rowPtrs[j].get(d) * delta_f.p[d]
                j++
            }
        }

        /*
         ============
         idLCP_Square::ChangeForce

         modifies this->f and uses this->delta_f
         ============
         */
        private fun ChangeForce(d: Int, step: Float) {
            // only the clamped variables and current variable have a force delta unequal zero
            Simd.SIMDProcessor.MulAdd(f.ToFloatPtr(), step, delta_f.ToFloatPtr(), numClamped)
            f.p[d] += step * delta_f.p[d]
        }

        /*
         ============
         idLCP_Square::ChangeAccel

         modifies this->a and uses this->delta_a
         ============
         */
        private fun ChangeAccel(d: Int, step: Float) {
            val clampedA = clam(a, numClamped)
            val clampedDeltaA = clam(delta_a, numClamped)

            // only the not clamped variables, including the current variable, can have an acceleration unequal zero
            Simd.SIMDProcessor.MulAdd(clampedA, step, clampedDeltaA, d - numClamped + 1)
            unClam(a, clampedA)
            unClam(delta_a, clampedDeltaA)
        }

        private fun GetMaxStep(d: Int, dir: Float, maxStep: CFloat, limit: CInt, limitSide: CInt) {
            var i: Int
            var s: Float

            // default to a full step for the current variable
            if (abs(delta_a.p[d]) > LCP_DELTA_ACCEL_EPSILON) {
                maxStep._val = (-a.p[d] / delta_a.p[d])
            } else {
                maxStep._val = (0.0f)
            }
            limit._val = (d)
            limitSide._val = (0)

            // test the current variable
            if (dir < 0.0f) {
                if (lo.p[d] != -idMath.INFINITY) {
                    s = (lo.p[d] - f.p[d]) / dir
                    if (s < maxStep._val) {
                        maxStep._val = (s)
                        limitSide._val = (-1)
                    }
                }
            } else {
                if (hi.p[d] != idMath.INFINITY) {
                    s = (hi.p[d] - f.p[d]) / dir
                    if (s < maxStep._val) {
                        maxStep._val = (s)
                        limitSide._val = (1)
                    }
                }
            }

            // test the clamped bounded variables
            i = numUnbounded
            while (i < numClamped) {
                if (delta_f.p[i] < -LCP_DELTA_FORCE_EPSILON) {
                    // if there is a low boundary
                    if (lo.p[i] != -idMath.INFINITY) {
                        s = (lo.p[i] - f.p[i]) / delta_f.p[i]
                        if (s < maxStep._val) {
                            maxStep._val = (s)
                            limit._val = (i)
                            limitSide._val = (-1)
                        }
                    }
                } else if (delta_f.p[i] > LCP_DELTA_FORCE_EPSILON) {
                    // if there is a high boundary
                    if (hi.p[i] != idMath.INFINITY) {
                        s = (hi.p[i] - f.p[i]) / delta_f.p[i]
                        if (s < maxStep._val) {
                            maxStep._val = (s)
                            limit._val = (i)
                            limitSide._val = (1)
                        }
                    }
                }
                i++
            }

            // test the not clamped bounded variables
            i = numClamped
            while (i < d) {
                if (side[i] == -1) {
                    if (delta_a.p[i] >= -LCP_DELTA_ACCEL_EPSILON) {
                        i++
                        continue
                    }
                } else if (side[i] == 1) {
                    if (delta_a.p[i] <= LCP_DELTA_ACCEL_EPSILON) {
                        i++
                        continue
                    }
                } else {
                    i++
                    continue
                }
                // ignore variables for which the force is not allowed to take any substantial value
                if (lo.p[i] >= -LCP_BOUND_EPSILON && hi.p[i] <= LCP_BOUND_EPSILON) {
                    i++
                    continue
                }
                s = -a.p[i] / delta_a.p[i]
                if (s < maxStep._val) {
                    maxStep._val = (s)
                    limit._val = (i)
                    limitSide._val = (0)
                }
                i++
            }
        }
    }

    //===============================================================
    //                                                        M
    //  idLCP_Symmetric                                      MrE
    //                                                        E
    //===============================================================
    internal class idLCP_Symmetric : idLCP() {
        private val a // force and acceleration
                : idVecX
        private val b // right hand side
                : idVecX
        private val clamped // LDLt factored sub matrix for clamped variables
                : idMatX
        private val delta_a // delta force and delta acceleration
                : idVecX
        private val delta_f: idVecX
        private val diagonal // reciprocal of diagonal of LDLt factored sub matrix for clamped variables
                : idVecX
        private val f: idVecX
        private val hi // low and high bounds
                : idVecX
        private val lo: idVecX
        private val m // original matrix
                : idMatX
        private val solveCache1 // intermediate result cached in SolveClamped
                : idVecX
        private val solveCache2 // "
                : idVecX
        private var boxIndex // box index
                : IntArray? = null
        private var clampedChangeStart // lowest row/column changed in the clamped matrix during an iteration
                = 0
        private var numClamped // number of clamped variables
                = 0
        private var numUnbounded // number of unbounded variables
                = 0
        private var padded // set to true if the rows of the initial matrix are 16 byte padded
                = false
        private var permuted // index to keep track of the permutation
                : IntArray = IntArray(0)
        private var rowPtrs // pointers to the rows of m
                : Array<FloatBuffer> = Array(0) { FloatBuffer.allocate(0) }
        private var side // tells if a variable is at the low boundary = -1, high boundary = 1 or inbetween = 0
                : IntArray = IntArray(0)

        override fun Solve(
            o_m: idMatX,
            o_x: idVecX,
            o_b: idVecX,
            o_lo: idVecX,
            o_hi: idVecX,
            o_boxIndex: IntArray?
        ): Boolean {
            var i: Int
            var j: Int
            var n: Int
            var boxStartIndex: Int
            val limit = CInt()
            val limitSide = CInt()
            var dir: Float
            var s: Float
            val dot = CFloat()
            val maxStep = CFloat()
            var failed: String?

            // true when the matrix rows are 16 byte padded
            padded = o_m.GetNumRows() + 3 and 3.inv() == o_m.GetNumColumns()
            assert(padded || o_m.GetNumRows() == o_m.GetNumColumns())
            assert(o_x.GetSize() == o_m.GetNumRows())
            assert(o_b.GetSize() == o_m.GetNumRows())
            assert(o_lo.GetSize() == o_m.GetNumRows())
            assert(o_hi.GetSize() == o_m.GetNumRows())

            // allocate memory for permuted input
            f.SetData(o_m.GetNumRows(), idVecX.VECX_ALLOCA(o_m.GetNumRows()))
            a.SetData(o_b.GetSize(), idVecX.VECX_ALLOCA(o_b.GetSize()))
            b.SetData(o_b.GetSize(), idVecX.VECX_ALLOCA(o_b.GetSize()))
            lo.SetData(o_lo.GetSize(), idVecX.VECX_ALLOCA(o_lo.GetSize()))
            hi.SetData(o_hi.GetSize(), idVecX.VECX_ALLOCA(o_hi.GetSize()))
            if (null != o_boxIndex) {
//		boxIndex = (int *)_alloca16( o_x.GetSize() * sizeof( int ) );
                boxIndex = IntArray(o_x.GetSize())
                //		memcpy( boxIndex, o_boxIndex, o_x.GetSize() * sizeof( int ) );
                System.arraycopy(o_boxIndex, 0, boxIndex, 0, o_x.GetSize())
            } else {
                boxIndex = null
            }

            // we override the const on o_m here but on exit the matrix is unchanged
            m.SetData(o_m.GetNumRows(), o_m.GetNumColumns(), o_m.get(0))
            f.Zero()
            a.Zero()
            b.set(o_b)
            lo.set(o_lo)
            hi.set(o_hi)

            // pointers to the rows of m
            rowPtrs = Array(m.GetNumRows()) { FloatBuffer.allocate(0) }
            arrayOfNulls<FloatBuffer>(m.GetNumRows()) //rowPtrs = (float **) _alloca16( m.GetNumRows() * sizeof( float * ) );
            i = 0
            while (i < m.GetNumRows()) {
                rowPtrs[i] = m.GetRowPtr(i)
                i++
            }

            // tells if a variable is at the low boundary, high boundary or inbetween
//	side = (int *) _alloca16( m.GetNumRows() * sizeof( int ) );
            side = IntArray(m.GetNumRows())

            // index to keep track of the permutation
//	permuted = (int *) _alloca16( m.GetNumRows() * sizeof( int ) );
            permuted = IntArray(m.GetNumRows())
            i = 0
            while (i < m.GetNumRows()) {
                permuted[i] = i
                i++
            }

            // permute input so all unbounded variables come first
            numUnbounded = 0
            i = 0
            while (i < m.GetNumRows()) {
                if (lo.p[i] == -idMath.INFINITY && hi.p[i] == idMath.INFINITY) {
                    if (numUnbounded != i) {
                        Swap(numUnbounded, i)
                    }
                    numUnbounded++
                }
                i++
            }

            // permute input so all variables using the boxIndex come last
            boxStartIndex = m.GetNumRows()
            if (null != boxIndex) {
                i = m.GetNumRows() - 1
                while (i >= numUnbounded) {
                    if (boxIndex!![i] >= 0 && (lo.p[i] != -idMath.INFINITY || hi.p[i] != idMath.INFINITY)) {
                        boxStartIndex--
                        if (boxStartIndex != i) {
                            Swap(boxStartIndex, i)
                        }
                    }
                    i--
                }
            }

            // sub matrix for factorization
            clamped.SetData(
                m.GetNumRows(),
                m.GetNumColumns(),
                idMatX.MATX_ALLOCA(m.GetNumRows() * m.GetNumColumns())
            )
            diagonal.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))
            solveCache1.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))
            solveCache2.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))

            // all unbounded variables are clamped
            numClamped = numUnbounded

            // if there are unbounded variables
            if (0 != numUnbounded) {

                // factor and solve for unbounded variables
                if (!FactorClamped()) {
                    idLib.common.Printf("idLCP_Symmetric::Solve: unbounded factorization failed\n")
                    return false
                }
                SolveClamped(f, b.ToFloatPtr())

                // if there are no bounded variables we are done
                if (numUnbounded == m.GetNumRows()) {
                    o_x.set(f) // the vector is not permuted
                    return true
                }
            }

//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
            var numIgnored = 0
            //#endif

            // allocate for delta force and delta acceleration
            delta_f.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))
            delta_a.SetData(m.GetNumRows(), idVecX.VECX_ALLOCA(m.GetNumRows()))

            // solve for bounded variables
            failed = null
            i = numUnbounded
            while (i < m.GetNumRows()) {
                clampedChangeStart = 0

                // once we hit the box start index we can initialize the low and high boundaries of the variables using the box index
                if (i == boxStartIndex) {
                    j = 0
                    while (j < boxStartIndex) {
                        o_x.p[permuted[j]] = f.p[j]
                        j++
                    }
                    j = boxStartIndex
                    while (j < m.GetNumRows()) {
                        s = o_x.p[boxIndex!![j]]
                        if (lo.p[j] != -idMath.INFINITY) {
                            lo.p[j] = -abs(lo.p[j] * s)
                        }
                        if (hi.p[j] != idMath.INFINITY) {
                            hi.p[j] = abs(hi.p[j] * s)
                        }
                        j++
                    }
                }

                // calculate acceleration for current variable
                Simd.SIMDProcessor.Dot(dot, rowPtrs[i], f.ToFloatPtr(), i)
                a.p[i] = dot._val - b.p[i]

                // if already at the low boundary
                if (lo.p[i] >= -LCP_BOUND_EPSILON && a.p[i] >= -LCP_ACCEL_EPSILON) {
                    side[i] = -1
                    i++
                    continue
                }

                // if already at the high boundary
                if (hi.p[i] <= LCP_BOUND_EPSILON && a.p[i] <= LCP_ACCEL_EPSILON) {
                    side[i] = 1
                    i++
                    continue
                }

                // if inside the clamped region
                if (abs(a.p[i]) <= LCP_ACCEL_EPSILON) {
                    side[i] = 0
                    AddClamped(i, false)
                    i++
                    continue
                }

                // drive the current variable into a valid region
                n = 0
                while (n < maxIterations) {


                    // direction to move
                    dir = if (a.p[i] <= 0.0f) {
                        1.0f
                    } else {
                        -1.0f
                    }

                    // calculate force delta
                    CalcForceDelta(i, dir)

                    // calculate acceleration delta: delta_a = m * delta_f;
                    CalcAccelDelta(i)

                    // maximum step we can take
                    GetMaxStep(i, dir, maxStep, limit, limitSide)
                    if (maxStep._val <= 0.0f) {
//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
                        // ignore the current variable completely
                        hi.p[i] = 0.0f
                        lo.p[i] = hi.p[i]
                        f.p[i] = 0.0f
                        side[i] = -1
                        numIgnored++
                        //#else
//				failed = va( "invalid step size %.4f", maxStep );
//#endif
                        break
                    }

                    // change force
                    ChangeForce(i, maxStep._val)

                    // change acceleration
                    ChangeAccel(i, maxStep._val)

                    // clamp/unclamp the variable that limited this step
                    side[limit._val] = limitSide._val
                    when (limitSide._val) {
                        0 -> {
                            a.p[limit._val] = 0.0f
                            AddClamped(limit._val, limit._val == i)
                        }
                        -1 -> {
                            f.p[limit._val] = lo.p[limit._val]
                            if (limit._val != i) {
                                RemoveClamped(limit._val)
                            }
                        }
                        1 -> {
                            f.p[limit._val] = hi.p[limit._val]
                            if (limit._val != i) {
                                RemoveClamped(limit._val)
                            }
                        }
                    }

                    // if the current variable limited the step we can continue with the next variable
                    if (limit._val == i) {
                        break
                    }
                    n++
                }
                if (n >= maxIterations) {
                    failed = String.format("max iterations %d", maxIterations)
                    break
                }
                if (null != failed) {
                    break
                }
                i++
            }

//#ifdef IGNORE_UNSATISFIABLE_VARIABLES
            if (0 != numIgnored) {
                if (lcp_showFailures.GetBool()) {
                    idLib.common.Printf(
                        "idLCP_Symmetric::Solve: %d of %d bounded variables ignored\n",
                        numIgnored,
                        m.GetNumRows() - numUnbounded
                    )
                }
            }
            //#endif

            // if failed clear remaining forces
            if (null != failed) {
                if (lcp_showFailures.GetBool()) {
                    idLib.common.Printf(
                        "idLCP_Symmetric::Solve: %s (%d of %d bounded variables ignored)\n",
                        failed,
                        m.GetNumRows() - i,
                        m.GetNumRows() - numUnbounded
                    )
                }
                j = i
                while (j < m.GetNumRows()) {
                    f.p[j] = 0.0f
                    j++
                }
            }

//#if defined(_DEBUG) && 0
//	if ( !failed ) {
//		// test whether or not the solution satisfies the complementarity conditions
//		for ( i = 0; i < m.GetNumRows(); i++ ) {
//			a[i] = -b[i];
//			for ( j = 0; j < m.GetNumRows(); j++ ) {
//				a[i] += rowPtrs[i][j] * f[j];
//			}
//
//			if ( f[i] == lo[i] ) {
//				if ( lo[i] != hi[i] && a[i] < -LCP_ACCEL_EPSILON ) {
//					int bah1 = 1;
//				}
//			} else if ( f[i] == hi[i] ) {
//				if ( lo[i] != hi[i] && a[i] > LCP_ACCEL_EPSILON ) {
//					int bah2 = 1;
//				}
//			} else if ( f[i] < lo[i] || f[i] > hi[i] || idMath::Fabs( a[i] ) > 1.0f ) {
//				int bah3 = 1;
//			}
//		}
//	}
//#endif
            // unpermute result
            i = 0
            while (i < f.GetSize()) {
                o_x.p[permuted[i]] = f.p[i]
                i++
            }

            // unpermute original matrix
            i = 0
            while (i < m.GetNumRows()) {
                j = 0
                while (j < m.GetNumRows()) {
                    if (permuted[j] == i) {
                        break
                    }
                    j++
                }
                if (i != j) {
                    m.SwapColumns(i, j)
                    idSwap(permuted, i, j)
                }
                i++
            }
            return true
        }

        private fun FactorClamped(): Boolean {
            clampedChangeStart = 0
            for (i in 0 until numClamped) {
//		memcpy( clamped[i], rowPtrs[i], numClamped * sizeof( float ) );
                clamped.arraycopy(rowPtrs[i], i, numClamped)
                val a = 0
            }
            val b = Simd.SIMDProcessor.MatX_LDLTFactor(clamped, diagonal, numClamped)
            val a = 0
            return b
        }

        private fun SolveClamped(x: idVecX, b: FloatArray) {

            // solve L
            Simd.SIMDProcessor.MatX_LowerTriangularSolve(
                clamped,
                solveCache1.ToFloatPtr(),
                b,
                numClamped,
                clampedChangeStart
            )

            // solve D
            Simd.SIMDProcessor.Mul(
                solveCache2.ToFloatPtr(),
                solveCache1.ToFloatPtr(),
                diagonal.ToFloatPtr(),
                numClamped
            )

            // solve Lt
            Simd.SIMDProcessor.MatX_LowerTriangularSolveTranspose(
                clamped,
                x.ToFloatPtr(),
                solveCache2.ToFloatPtr(),
                numClamped
            )
            clampedChangeStart = numClamped
        }

        private fun SolveClamped(x: idVecX, b: FloatBuffer) {
            SolveClamped(x, TempDump.fbtofa(b))
        }

        private fun Swap(i: Int, j: Int) {
            if (i == j) {
                return
            }
            idSwap(rowPtrs, rowPtrs, i, j)
            m.SwapColumns(i, j)
            b.SwapElements(i, j)
            lo.SwapElements(i, j)
            hi.SwapElements(i, j)
            a.SwapElements(i, j)
            f.SwapElements(i, j)
            if (null != boxIndex) {
                idSwap(boxIndex!!, boxIndex!!, i, j)
            }
            idSwap(side, side, i, j)
            idSwap(permuted, permuted, i, j)
        }

        private fun AddClamped(r: Int, useSolveCache: Boolean) {
            val d: Float
            val dot = CFloat()
            assert(r >= numClamped)
            if (numClamped < clampedChangeStart) {
                clampedChangeStart = numClamped
            }

            // add a row at the bottom and a column at the right of the factored
            // matrix for the clamped variables
            Swap(numClamped, r)

            // solve for v in L * v = rowPtr[numClamped]
            if (useSolveCache) {

                // the lower triangular solve was cached in SolveClamped called by CalcForceDelta
                clamped.arraycopy(
                    solveCache2.ToFloatPtr(),
                    numClamped,
                    numClamped
                ) //memcpy(clamped[numClamped], solveCache2.ToFloatPtr(), numClamped * sizeof(float));
                val a = 0
                // calculate row dot product
                Simd.SIMDProcessor.Dot(dot, solveCache2.ToFloatPtr(), solveCache1.ToFloatPtr(), numClamped)
            } else {
                val v = FloatArray(numClamped) //(float *) _alloca16(numClamped * sizeof(float));
                val clampedArray = clam(clamped, numClamped)
                Simd.SIMDProcessor.MatX_LowerTriangularSolve(clamped, v, rowPtrs[numClamped], numClamped)
                // add bottom row to L
                Simd.SIMDProcessor.Mul(clampedArray, v, diagonal.ToFloatPtr(), numClamped)
                // calculate row dot product
                Simd.SIMDProcessor.Dot(dot, clampedArray, v, numClamped)
                unClam(clamped, clampedArray)
            }

            // update diagonal[numClamped]
            d = rowPtrs[numClamped].get(numClamped) - dot._val
            if (d == 0.0f) {
                idLib.common.Printf("idLCP_Symmetric::AddClamped: updating factorization failed\n")
                numClamped++
                return
            }
            clamped.set(numClamped, numClamped, d)
            diagonal.p[numClamped] = 1.0f / d
            numClamped++
        }

        private fun RemoveClamped(r: Int) {
            var i: Int
            var j: Int
            val n: Int
            val addSub: FloatArray
            val v: FloatArray
            val v1: FloatArray
            val v2: FloatArray
            val dot = CFloat()
            var sum: Float
            var diag: Float
            var newDiag: Float
            var invNewDiag: Float
            var p1: Float
            var p2: Float
            var alpha1: Float
            var alpha2: Float
            var beta1: Float
            var beta2: Float
            val original: FloatBuffer
            var ptr: FloatBuffer
            assert(r < numClamped)
            if (r < clampedChangeStart) {
                clampedChangeStart = r
            }
            numClamped--

            // no need to swap and update the factored matrix when the last row and column are removed
            if (r == numClamped) {
                return
            }

            // swap the to be removed row/column with the last row/column
            Swap(r, numClamped)

            // update the factored matrix
            addSub = FloatArray(numClamped) //	addSub = (float *) _alloca16( numClamped * sizeof( float ) );
            if (r == 0) {
                if (numClamped == 1) {
                    diag = rowPtrs[0].get(0)
                    if (diag == 0.0f) {
                        idLib.common.Printf("idLCP_Symmetric::RemoveClamped: updating factorization failed\n")
                        return
                    }
                    clamped.set(0, 0, diag)
                    diagonal.p[0] = (1.0f / diag)
                    return
                }

                // calculate the row/column to be added to the lower right sub matrix starting at (r, r)
                original = rowPtrs[numClamped]
                ptr = rowPtrs[r]
                addSub[0] = ptr.get(0) - original.get(numClamped)
                i = 1
                while (i < numClamped) {
                    addSub[i] = ptr.get(i) - original.get(i)
                    i++
                }
            } else {
                v = FloatArray(numClamped) //= (float *) _alloca16( numClamped * sizeof( float ) );
                val clampedArray = clam(clamped, r)

                // solve for v in L * v = rowPtr[r]
                Simd.SIMDProcessor.MatX_LowerTriangularSolve(clamped, v, rowPtrs[r], r)

                // update removed row
                Simd.SIMDProcessor.Mul(clampedArray, v, diagonal.ToFloatPtr(), r)

                // if the last row/column of the matrix is updated
                if (r == numClamped - 1) {
                    // only calculate new diagonal
                    Simd.SIMDProcessor.Dot(dot, clampedArray, v, r)
                    unClam(clamped, clampedArray)
                    diag = (rowPtrs[r].get(r) - dot._val)
                    if (diag == 0.0f) {
                        idLib.common.Printf("idLCP_Symmetric::RemoveClamped: updating factorization failed\n")
                        return
                    }
                    clamped.set(r, r, diag)
                    diagonal.p[r] = (1.0f / diag)
                    return
                }
                unClam(clamped, clampedArray)

                // calculate the row/column to be added to the lower right sub matrix starting at (r, r)
                i = 0
                while (i < r) {
                    v[i] = clamped.get(r)[i] * clamped.get(i)[i]
                    i++
                }
                i = r
                while (i < numClamped) {
                    if (i == r) {
                        sum = clamped.get(r)[r]
                    } else {
                        sum = (clamped.get(r)[r] * clamped.get(i)[r])
                    }
                    ptr = clamped.GetRowPtr(i)
                    j = 0
                    while (j < r) {
                        sum += (ptr[j] * v[j])
                        j++
                    }
                    addSub[i] = (rowPtrs[r].get(i) - sum)
                    i++
                }
            }

            // add row/column to the lower right sub matrix starting at (r, r)
            v1 = FloatArray(numClamped) //	v1 = (float *) _alloca16( numClamped * sizeof( float ) );
            v2 = FloatArray(numClamped) //	v2 = (float *) _alloca16( numClamped * sizeof( float ) );
            diag = idMath.SQRT_1OVER2
            v1[r] = ((0.5f * addSub[r] + 1.0f) * diag)
            v2[r] = ((0.5f * addSub[r] - 1.0f) * diag)
            i = r + 1
            while (i < numClamped) {
                v2[i] = (addSub[i] * diag)
                v1[i] = v2[i]
                i++
            }
            alpha1 = 1.0f
            alpha2 = -1.0f

            // simultaneous update/downdate of the sub matrix starting at (r, r)
            n = clamped.GetNumColumns()
            i = r
            while (i < numClamped) {
                diag = clamped.get(i)[i]
                p1 = v1[i]
                newDiag = diag + alpha1 * p1 * p1
                if (newDiag == 0.0f) {
                    idLib.common.Printf("idLCP_Symmetric::RemoveClamped: updating factorization failed\n")
                    return
                }
                alpha1 /= newDiag
                beta1 = p1 * alpha1
                alpha1 *= diag
                diag = newDiag
                p2 = v2[i]
                newDiag = diag + alpha2 * p2 * p2
                if (newDiag == 0.0f) {
                    idLib.common.Printf("idLCP_Symmetric::RemoveClamped: updating factorization failed\n")
                    return
                }
                clamped.set(i, i, newDiag)
                diagonal.p[i] = (1.0f / newDiag.also { invNewDiag = it })
                alpha2 *= invNewDiag
                beta2 = p2 * alpha2
                alpha2 *= diag

                // update column below diagonal (i,i)
                ptr = clamped.ToFloatBufferPtr(i)
                j = i + 1
                while (j < numClamped - 1) {
                    var sum0 = ptr[(j + 0) * n]
                    var sum1 = ptr[(j + 1) * n]
                    v1[j + 0] -= p1 * sum0
                    v1[j + 1] -= p1 * sum1
                    sum0 += (beta1 * v1[j + 0])
                    sum1 += (beta1 * v1[j + 1])
                    v2[j + 0] -= p2 * sum0
                    v2[j + 1] -= p2 * sum1
                    sum0 += (beta2 * v2[j + 0])
                    sum1 += (beta2 * v2[j + 1])
                    ptr.put((j + 0) * n, sum0)
                    ptr.put((j + 1) * n, sum1)
                    val a = 0
                    j += 2
                }
                while (j < numClamped) {
                    sum = ptr[j * n]
                    v1[j] -= p1 * sum
                    sum += beta1 * v1[j]
                    v2[j] -= p2 * sum
                    sum += beta2 * v2[j]
                    ptr.put(j * n, sum)
                    j++
                }
                i++
            }
        }

        /*
         ============
         idLCP_Symmetric::CalcForceDelta

         modifies this->delta_f
         ============
         */
        private fun CalcForceDelta(d: Int, dir: Float) {
            var i: Int
            val ptr: FloatArray
            delta_f.p[d] = dir
            if (numClamped == 0) {
                return
            }

            // solve force delta
            val clone = delta_f.p.clone()
            SolveClamped(delta_f, rowPtrs[d])

            // flip force delta based on direction
            if (dir > 0.0f) {
                ptr = delta_f.ToFloatPtr()
                i = 0
                while (i < numClamped) {
                    ptr[i] = -ptr[i]
                    val a = 0
                    i++
                }
            }
        }

        /*
         ============
         idLCP_Symmetric::CalcAccelDelta

         modifies this->delta_a and uses this->delta_f
         ============
         */
        private fun CalcAccelDelta(d: Int) {
            var j: Int
            val dot = CFloat()

            // only the not clamped variables, including the current variable, can have a change in acceleration
            j = numClamped
            while (j <= d) {

                // only the clamped variables and the current variable have a force delta unequal zero
                Simd.SIMDProcessor.Dot(dot, rowPtrs[j], delta_f.ToFloatPtr(), numClamped)
                delta_a.p[j] = dot._val + rowPtrs[j].get(d) * delta_f.p[d]
                val a = 0
                j++
            }
        }

        /*
         ============
         idLCP_Symmetric::ChangeForce

         modifies this->f and uses this->delta_f
         ============
         */
        private fun ChangeForce(d: Int, step: Float) {
            // only the clamped variables and current variable have a force delta unequal zero
            Simd.SIMDProcessor.MulAdd(f.ToFloatPtr(), step, delta_f.ToFloatPtr(), numClamped)
            f.p[d] += step * delta_f.p[d]
            val a = 0
        }

        /*
         ============
         idLCP_Symmetric::ChangeAccel

         modifies this->a and uses this->delta_a
         ============
         */
        private fun ChangeAccel(d: Int, step: Float) {
            val clampedA = clam(a, numClamped)
            val clampedDeltaA = clam(delta_a, numClamped)

            // only the not clamped variables, including the current variable, can have an acceleration unequal zero
            Simd.SIMDProcessor.MulAdd(clampedA, step, clampedDeltaA, d - numClamped + 1)
            unClam(a, clampedA)
            val a = 0
        }

        private fun GetMaxStep(d: Int, dir: Float, maxStep: CFloat, limit: CInt, limitSide: CInt) {
            var i: Int
            var s: Float

            // default to a full step for the current variable
            if (abs(delta_a.p[d]) > LCP_DELTA_ACCEL_EPSILON) {
                maxStep._val = (-a.p[d] / delta_a.p[d])
            } else {
                maxStep._val = (0.0f)
            }
            limit._val = (d)
            limitSide._val = (0)

            // test the current variable
            if (dir < 0.0f) {
                if (lo.p[d] != -idMath.INFINITY) {
                    s = (lo.p[d] - f.p[d]) / dir
                    if (s < maxStep._val) {
                        maxStep._val = (s)
                        limitSide._val = (-1)
                    }
                }
            } else {
                if (hi.p[d] != idMath.INFINITY) {
                    s = (hi.p[d] - f.p[d]) / dir
                    if (s < maxStep._val) {
                        maxStep._val = (s)
                        limitSide._val = (1)
                    }
                }
            }

            // test the clamped bounded variables
            i = numUnbounded
            while (i < numClamped) {
                if (delta_f.p[i] < -LCP_DELTA_FORCE_EPSILON) {
                    // if there is a low boundary
                    if (lo.p[i] != -idMath.INFINITY) {
                        s = (lo.p[i] - f.p[i]) / delta_f.p[i]
                        if (s < maxStep._val) {
                            maxStep._val = (s)
                            limit._val = (i)
                            limitSide._val = (-1)
                        }
                    }
                } else if (delta_f.p[i] > LCP_DELTA_FORCE_EPSILON) {
                    // if there is a high boundary
                    if (hi.p[i] != idMath.INFINITY) {
                        s = (hi.p[i] - f.p[i]) / delta_f.p[i]
                        if (s < maxStep._val) {
                            maxStep._val = (s)
                            limit._val = (i)
                            limitSide._val = (1)
                        }
                    }
                }
                i++
            }

            // test the not clamped bounded variables
            i = numClamped
            while (i < d) {
                if (side[i] == -1) {
                    if (delta_a.p[i] >= -LCP_DELTA_ACCEL_EPSILON) {
                        i++
                        continue
                    }
                } else if (side[i] == 1) {
                    if (delta_a.p[i] <= LCP_DELTA_ACCEL_EPSILON) {
                        i++
                        continue
                    }
                } else {
                    i++
                    continue
                }
                // ignore variables for which the force is not allowed to take any substantial value
                if (lo.p[i] >= -LCP_BOUND_EPSILON && hi.p[i] <= LCP_BOUND_EPSILON) {
                    i++
                    continue
                }
                s = -a.p[i] / delta_a.p[i]
                if (s < maxStep._val) {
                    maxStep._val = (s)
                    limit._val = (i)
                    limitSide._val = (0)
                }
                i++
            }
        }

        //
        //
        init {
            m = idMatX()
            b = idVecX()
            lo = idVecX()
            hi = idVecX()
            f = idVecX()
            a = idVecX()
            delta_f = idVecX()
            delta_a = idVecX()
            clamped = idMatX()
            diagonal = idVecX()
            solveCache1 = idVecX()
            solveCache2 = idVecX()
        }
    }
}