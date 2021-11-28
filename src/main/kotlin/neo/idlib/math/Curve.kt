package neo.idlib.math

import neo.TempDump.TypeErasure_Expection
import neo.framework.DeclAF.idAFVector.type
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Vector.idVec
import neo.idlib.math.Vector.idVecX
import java.util.*

/**
 *
 */
class Curve {
    /**
     * ===============================================================================
     *
     *
     * Curve base template.
     *
     *
     * ===============================================================================
     */
    internal open class idCurve<type : idVec<*>?>(protected val clazz: Class<type?>?) {
        protected var changed: Boolean
        protected var currentIndex // cached index for fast lookup
                : Int
        protected val times: idList<Float?>? = idList() // knots
        protected val values: idList<type?>? = idList() // knot values

        //public	virtual				~idCurve( void );
        /*
         ====================
         idCurve::AddValue

         add a timed/value pair to the spline
         returns the index to the inserted pair
         ====================
         */
        open fun AddValue(time: Float, value: type?): Int {
            val i: Int
            i = IndexForTime(time)
            times.Insert(time, i)
            values.Insert(value, i)
            changed = true
            return i
        }

        open fun RemoveIndex(index: Int) {
            values.RemoveIndex(index)
            times.RemoveIndex(index)
            changed = true
        }

        open fun Clear() {
            values.Clear()
            times.Clear()
            currentIndex = -1
            changed = true
        }

        /*
         ====================
         idCurve::GetCurrentValue

         get the value for the given time
         ====================
         */
        open fun GetCurrentValue(time: Float): type? {
            val i: Int
            i = IndexForTime(time)
            return if (i >= values.Num()) {
                values.oGet(values.Num() - 1)
            } else {
                values.oGet(i)
            }
        }

        /*
         ====================
         idCurve::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        open fun GetCurrentFirstDerivative(time: Float): type? {
            return values.oGet(0).oMinus(values.oGet(0))
        }

        /*
         ====================
         idCurve::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        open fun GetCurrentSecondDerivative(time: Float): type? {
            return values.oGet(0).oMinus(values.oGet(0))
        }

        open fun IsDone(time: Float): Boolean {
            return time >= times.oGet(times.Num() - 1)
        }

        fun GetNumValues(): Int {
            return values.Num()
        }

        fun SetValue(index: Int, value: type?) {
            values.oSet(index, value)
            changed = true
        }

        fun GetValue(index: Int): type? {
            return values.oGet(index)
        }

        fun GetValueAddress(index: Int): type? { //TODO:pointer
            return values.oGet(index)
        }

        fun GetTime(index: Int): Float {
            return times.oGet(index)
        }

        fun GetLengthForTime(time: Float): Float {
            var length = 0.0f
            val index = IndexForTime(time)
            for (i in 0 until index) {
                length += RombergIntegral(times.oGet(i), times.oGet(i + 1), 5)
            }
            length += RombergIntegral(times.oGet(index), time, 5)
            return length
        }

        @JvmOverloads
        fun GetTimeForLength(length: Float, epsilon: Float = 1.0f): Float {
            var i: Int
            var index: Int
            val accumLength: FloatArray
            var totalLength: Float
            val len0: Float
            val len1: Float
            var t: Float
            var diff: Float
            if (length <= 0.0f) {
                return times.oGet(0)
            }
            accumLength =
                FloatArray(values.Num()) //	accumLength = (float *) _alloca16( values.Num() * sizeof( float ) );
            totalLength = 0.0f
            index = 0
            while (index < values.Num() - 1) {
                totalLength += GetLengthBetweenKnots(index, index + 1)
                accumLength[index] = totalLength
                if (length < accumLength[index]) {
                    break
                }
                index++
            }
            if (index >= values.Num() - 1) {
                return times.oGet(times.Num() - 1)
            }
            if (index == 0) {
                len0 = length
                len1 = accumLength[0]
            } else {
                len0 = length - accumLength[index - 1]
                len1 = accumLength[index] - accumLength[index - 1]
            }

            // invert the arc length integral using Newton's method
            t = (times.oGet(index + 1) - times.oGet(index)) * len0 / len1
            i = 0
            while (i < 32) {
                diff = RombergIntegral(times.oGet(index), times.oGet(index) + t, 5) - len0
                if (Math.abs(diff) <= epsilon) {
                    return times.oGet(index) + t
                }
                t -= diff / GetSpeed(times.oGet(index) + t)
                i++
            }
            return times.oGet(index) + t
        }

        fun GetLengthBetweenKnots(i0: Int, i1: Int): Float {
            var length = 0.0f
            for (i in i0 until i1) {
                length += RombergIntegral(times.oGet(i), times.oGet(i + 1), 5)
            }
            return length
        }

        fun MakeUniform(totalTime: Float) {
            var i: Int
            val n: Int
            n = times.Num() - 1
            i = 0
            while (i <= n) {
                times.oSet(i, i * totalTime / n)
                i++
            }
            changed = true
        }

        fun SetConstantSpeed(totalTime: Float) {
            var i: Int
            val length: FloatArray
            var totalLength: Float
            val scale: Float
            var t: Float
            length = FloatArray(values.Num()) //	length = (float *) _alloca16( values.Num() * sizeof( float ) );
            totalLength = 0.0f
            i = 0
            while (i < values.Num() - 1) {
                length[i] = GetLengthBetweenKnots(i, i + 1)
                totalLength += length[i]
                i++
            }
            scale = totalTime / totalLength
            t = 0.0f
            i = 0
            while (i < times.Num() - 1) {
                times.oSet(i, t)
                t += scale * length[i]
                i++
            }
            times.oSet(times.Num() - 1, totalTime)
            changed = true
        }

        fun ShiftTime(deltaTime: Float) {
            for (i in 0 until times.Num()) {
                times.oSet(i, times.oGet(i) + deltaTime)
            }
            changed = true
        }

        fun Translate(translation: type?) {
            for (i in 0 until values.Num()) {
                values.oSet(i, values.oGet(i).oPlus(translation))
            }
            changed = true
        } // set whenever the curve changes

        /*
         ====================
         idCurve::IndexForTime

         find the index for the first time greater than or equal to the given time
         ====================
         */
        protected fun IndexForTime(time: Float): Int {
            var len: Int
            var mid: Int
            var offset: Int
            var res: Int
            if (currentIndex >= 0 && currentIndex <= times.Num()) {
                // use the cached index if it is still valid
                if (currentIndex == 0) {
                    if (time <= times.oGet(currentIndex)) {
                        return currentIndex
                    }
                } else if (currentIndex == times.Num()) {
                    if (time > times.oGet(currentIndex - 1)) {
                        return currentIndex
                    }
                } else if (time > times.oGet(currentIndex - 1) && time <= times.oGet(currentIndex)) {
                    return currentIndex
                } else if (time > times.oGet(currentIndex) && (currentIndex + 1 == times.Num() || time <= times.oGet(
                        currentIndex + 1
                    ))
                ) {
                    // use the next index
                    currentIndex++
                    return currentIndex
                }
            }
            // use binary search to find the index for the given time
            len = times.Num()
            mid = len
            offset = 0
            res = 0
            while (mid
                > 0
            ) {
                mid = len shr 1
                if (time == times.oGet(offset + mid)) {
                    return offset + mid
                } else if (time > times.oGet(offset + mid)) {
                    offset += mid
                    len -= mid
                    res = 1
                } else {
                    len -= mid
                    res = 0
                }
            }
            currentIndex = offset + res
            return currentIndex
        }

        /*
         ====================
         idCurve::TimeForIndex

         get the value for the given time
         ====================
         */
        protected open fun TimeForIndex(index: Int): Float {
            val n = times.Num() - 1
            if (index < 0) {
                return (times.oGet(0)
                        + index * (times.oGet(1) - times.oGet(0)))
            } else if (index > n) {
                return times.oGet(n) + (index - n) * (times.oGet(n) - times.oGet(n - 1))
            }
            return times.oGet(index)
        }

        /*
         ====================
         idCurve::ValueForIndex

         get the value for the given time
         ====================
         */
        protected open fun ValueForIndex(index: Int): type? {
            val n = values.Num() - 1
            if (index < 0) {
                return values.oGet(0).oPlus(values.oGet(1).oMinus(values.oGet(0)).oMultiply(index.toFloat()))
            } else if (index > n) {
                return values.oGet(n).oPlus(values.oGet(n).oMinus(values.oGet(n - 1)).oMultiply((index - n).toFloat()))
            }
            return values.oGet(index)
        }

        protected fun GetSpeed(time: Float): Float {
            var i: Int
            var speed: Float
            val value: type?
            value = GetCurrentFirstDerivative(time)
            speed = 0.0f
            i = 0
            while (i < value.GetDimension()) {
                speed += value.oGet(i) * value.oGet(i)
                i++
            }
            return idMath.Sqrt(speed)
        }

        protected fun RombergIntegral(t0: Float, t1: Float, order: Int): Float {
            var i: Int
            var j: Int
            var k: Int
            var m: Int
            var n: Int
            var sum: Float
            var delta: Float
            val temp = arrayOfNulls<FloatArray?>(2)
            temp[0] = FloatArray(order) //	temp[0] = (float *) _alloca16( order * sizeof( float ) );
            temp[1] = FloatArray(order) //	temp[1] = (float *) _alloca16( order * sizeof( float ) );
            delta = t1 - t0
            temp[0].get(0) = 0.5f * delta * (GetSpeed(t0) + GetSpeed(t1))
            i = 2
            m = 1
            while (i <= order) {


                // approximate using the trapezoid rule
                sum = 0.0f
                j = 1
                while (j <= m) {
                    sum += GetSpeed(t0 + delta * (j - 0.5f))
                    j++
                }

                // Richardson extrapolation
                temp[1].get(0) = 0.5f * (temp[0].get(0) + delta * sum)
                k = 1
                n = 4
                while (k < i) {
                    temp[1].get(k) = (n * temp[1].get(k - 1) - temp[0].get(k - 1)) / (n - 1)
                    k++
                    n *= 4
                }
                j = 0
                while (j < i) {
                    temp[0].get(j) = temp[1].get(j)
                    j++
                }
                i++
                m *= 2
                delta *= 0.5f
            }
            return temp[0].get(order - 1)
        }

        protected fun newInstance(): type? {
            return try {
                clazz.newInstance()
            } catch (e: InstantiationException) {
                throw TypeErasure_Expection()
            } catch (e: IllegalAccessException) {
                throw TypeErasure_Expection()
            }
        }

        init {
            currentIndex = -1
            changed = false
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Bezier Curve template. The degree of the polynomial equals the number of
     * knots minus one.
     *
     *
     * ===============================================================================
     */
    internal class idCurve_Bezier<type : idVec<*>?>(clazz: Class<type?>?) : idCurve<type?>(clazz) {
        /*
         ====================
         idCurve_Bezier::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            run {
                var i: Int
                val bvals: FloatArray
                val v = this.newInstance()
                bvals =
                    FloatArray(this.values.Num()) //	bvals = (float *) _alloca16( this->values.Num() * sizeof( float ) );
                Basis(this.values.Num(), time, bvals)
                v.oSet(this.values.oGet(0).oMultiply(bvals[0]))
                i = 1
                while (i < this.values.Num()) {
                    v.oPluSet(this.values.oGet(i).oMultiply(bvals[i]))
                    i++
                }
                return v
            }
        }

        /*
         ====================
         idCurve_Bezier::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            var i: Int
            val bvals: FloatArray
            val d: Float
            val v = newInstance()
            bvals = FloatArray(values.Num()) //	bvals = (float *) _alloca16( this->values.Num() * sizeof( float ) );
            BasisFirstDerivative(values.Num(), time, bvals)
            v.oSet(values.oGet(0).oMultiply(bvals[0]))
            i = 1
            while (i < values.Num()) {
                v.oPluSet(values.oGet(i).oMultiply(bvals[i]))
                i++
            }
            d = times.oGet(times.Num() - 1) - times.oGet(0)
            return v.oMultiply((values.Num() - 1) / d) as type
        }

        /*
         ====================
         idCurve_Bezier::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            var i: Int
            val bvals: FloatArray
            val d: Float
            val v = newInstance()
            bvals = FloatArray(values.Num()) //	bvals = (float *) _alloca16( this->values.Num() * sizeof( float ) );
            BasisSecondDerivative(values.Num(), time, bvals)
            v.oSet(values.oGet(0).oMultiply(bvals[0]))
            i = 1
            while (i < values.Num()) {
                v.oPluSet(values.oGet(i).oMultiply(bvals[i]))
                i++
            }
            d = times.oGet(times.Num() - 1) - times.oGet(0)
            return v.oMultiply((values.Num() - 2) * (values.Num() - 1) / (d * d)) as type
        }

        /*
         ====================
         idCurve_Bezier::Basis

         bezier basis functions
         ====================
         */
        protected fun Basis(order: Int, t: Float, bvals: FloatArray?) {
            var i: Int
            var j: Int
            val d: Int
            val c: FloatArray
            var c1: Float
            var c2: Float
            val s: Float
            val o: Float
            var ps: Float
            var po: Float
            bvals.get(0) = 1.0f
            d = order - 1
            if (d <= 0) {
                return
            }
            c = FloatArray(d + 1) //	c = (float *) _alloca16( (d+1) * sizeof( float ) );
            s = (t - times.oGet(0)) / (times.oGet(times.Num() - 1) - times.oGet(0))
            o = 1.0f - s
            ps = s
            po = o
            i = 1
            while (i < d) {
                c[i] = 1.0f
                i++
            }
            i = 1
            while (i < d) {
                c[i - 1] = 0.0f
                c1 = c[i]
                c[i] = 1.0f
                j = i + 1
                while (j <= d) {
                    c2 = c[j]
                    c[j] = c1 + c[j - 1]
                    c1 = c2
                    j++
                }
                bvals.get(i) = c[d] * ps
                ps *= s
                i++
            }
            i = d - 1
            while (i >= 0) {
                bvals.get(i) *= po
                po *= o
                i--
            }
            bvals.get(d) = ps
        }

        /*
         ====================
         idCurve_Bezier::BasisFirstDerivative

         first derivative of bezier basis functions
         ====================
         */
        protected fun BasisFirstDerivative(order: Int, t: Float, bvals: FloatArray?) {
            var i: Int
            val bvals_1 = Arrays.copyOfRange(bvals, 1, bvals.size)
            Basis(order - 1, t, bvals_1)
            System.arraycopy(bvals_1, 0, bvals, 1, bvals_1.size)
            bvals.get(0) = 0.0f
            i = 0
            while (i < order - 1) {
                bvals.get(i) -= bvals.get(i + 1)
                i++
            }
        }

        /*
         ====================
         idCurve_Bezier::BasisSecondDerivative

         second derivative of bezier basis functions
         ====================
         */
        protected fun BasisSecondDerivative(order: Int, t: Float, bvals: FloatArray?) {
            var i: Int
            val bvals_1 = Arrays.copyOfRange(bvals, 1, bvals.size)
            BasisFirstDerivative(order - 1, t, bvals_1)
            System.arraycopy(bvals_1, 0, bvals, 1, bvals_1.size)
            bvals.get(0) = 0.0f
            i = 0
            while (i < order - 1) {
                bvals.get(i) -= bvals.get(i + 1)
                i++
            }
        }
    }

    /*
     ===============================================================================

     Quadratic Bezier Curve template.
     Should always have exactly three knots.

     ===============================================================================
     */
    internal class idCurve_QuadraticBezier<type : idVec<*>?>(clazz: Class<type?>?) : idCurve<type?>(clazz) {
        /*
         ====================
         idCurve_QuadraticBezier::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val bvals = FloatArray(3)
            assert(values.Num() == 3)
            Basis(time, bvals)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
        }

        /*
         ====================
         idCurve_QuadraticBezier::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val bvals = FloatArray(3)
            val d: Float
            assert(values.Num() == 3)
            BasisFirstDerivative(time, bvals)
            d = times.oGet(2) - times.oGet(0)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
                .oDivide(d)
        }

        /*
         ====================
         idCurve_QuadraticBezier::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val bvals = FloatArray(3)
            val d: Float
            assert(values.Num() == 3)
            BasisSecondDerivative(time, bvals)
            d = times.oGet(2) - times.oGet(0)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
                .oDivide(d * d)
        }

        /*
         ====================
         idCurve_QuadraticBezier::Basis

         quadratic bezier basis functions
         ====================
         */
        protected fun Basis(t: Float, bvals: FloatArray?) {
            val s1 = (t - times.oGet(0)) / (times.oGet(2) - times.oGet(0))
            val s2 = s1 * s1
            bvals.get(0) = s2 - 2.0f * s1 + 1.0f
            bvals.get(1) = -2.0f * s2 + 2.0f * s1
            bvals.get(2) = s2
        }

        /*
         ====================
         idCurve_QuadraticBezier::BasisFirstDerivative

         first derivative of quadratic bezier basis functions
         ====================
         */
        protected fun BasisFirstDerivative(t: Float, bvals: FloatArray?) {
            val s1 = (t - times.oGet(0)) / (times.oGet(2) - times.oGet(0))
            bvals.get(0) = 2.0f * s1 - 2.0f
            bvals.get(1) = -4.0f * s1 + 2.0f
            bvals.get(2) = 2.0f * s1
        }

        /*
         ====================
         idCurve_QuadraticBezier::BasisSecondDerivative

         second derivative of quadratic bezier basis functions
         ====================
         */
        protected fun BasisSecondDerivative(t: Float, bvals: FloatArray?) {
//	float s1 = (float) ( t - this->times.oGet(0] ) / ( this->times.oGet(2] - this->times.oGet(0] );
            bvals.get(0) = 2.0f
            bvals.get(1) = -4.0f
            bvals.get(2) = 2.0f
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Cubic Bezier Curve template. Should always have exactly four knots.
     *
     *
     * ===============================================================================
     */
    internal class idCurve_CubicBezier<type : idVec<*>?>(clazz: Class<type?>?) : idCurve<type?>(clazz) {
        /*
         ====================
         idCurve_CubicBezier::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val bvals = FloatArray(4)
            assert(values.Num() == 4)
            Basis(time, bvals)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
                .oPlus(values.oGet(3).oMultiply(bvals[3]))
        }

        /*
         ====================
         idCurve_CubicBezier::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val bvals = FloatArray(4)
            val d: Float
            assert(values.Num() == 4)
            BasisFirstDerivative(time, bvals)
            d = times.oGet(3) - times.oGet(0)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
                .oPlus(values.oGet(3).oMultiply(bvals[3]))
                .oDivide(d)
        }

        /*
         ====================
         idCurve_CubicBezier::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val bvals = FloatArray(4)
            val d: Float
            assert(values.Num() == 4)
            BasisSecondDerivative(time, bvals)
            d = times.oGet(3) - times.oGet(0)
            return values.oGet(0).oMultiply(bvals[0])
                .oPlus(values.oGet(1).oMultiply(bvals[1]))
                .oPlus(values.oGet(2).oMultiply(bvals[2]))
                .oPlus(values.oGet(3).oMultiply(bvals[3]))
                .oDivide(d * d)
        }

        /*
         ====================
         idCurve_CubicBezier::Basis

         cubic bezier basis functions
         ====================
         */
        protected fun Basis(t: Float, bvals: FloatArray?) {
            val s1 = (t - times.oGet(0)) / (times.oGet(3) - times.oGet(0))
            val s2 = s1 * s1
            val s3 = s2 * s1
            bvals.get(0) = -s3 + 3.0f * s2 - 3.0f * s1 + 1.0f
            bvals.get(1) = 3.0f * s3 - 6.0f * s2 + 3.0f * s1
            bvals.get(2) = -3.0f * s3 + 3.0f * s2
            bvals.get(3) = s3
        }

        /*
         ====================
         idCurve_CubicBezier::BasisFirstDerivative

         first derivative of cubic bezier basis functions
         ====================
         */
        protected fun BasisFirstDerivative(t: Float, bvals: FloatArray?) {
            val s1 = (t - times.oGet(0)) / (times.oGet(3) - times.oGet(0))
            val s2 = s1 * s1
            bvals.get(0) = -3.0f * s2 + 6.0f * s1 - 3.0f
            bvals.get(1) = 9.0f * s2 - 12.0f * s1 + 3.0f
            bvals.get(2) = -9.0f * s2 + 6.0f * s1
            bvals.get(3) = 3.0f * s2
        }

        /*
         ====================
         idCurve_CubicBezier::BasisSecondDerivative

         second derivative of cubic bezier basis functions
         ====================
         */
        protected fun BasisSecondDerivative(t: Float, bvals: FloatArray?) {
            val s1 = (t - times.oGet(0)) / (times.oGet(3) - times.oGet(0))
            bvals.get(0) = -6.0f * s1 + 6.0f
            bvals.get(1) = 18.0f * s1 - 12.0f
            bvals.get(2) = -18.0f * s1 + 6.0f
            bvals.get(3) = 6.0f * s1
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Spline base template.
     *
     *
     * ===============================================================================
     */
    open class idCurve_Spline<type : idVec<*>?>(clazz: Class<type?>?) : idCurve<type?>(clazz) {
        protected var boundaryType: Int
        protected var closeTime: Float
        override fun IsDone(time: Float): Boolean {
            return boundaryType != BT_CLOSED && time >= times.oGet(times.Num() - 1)
        }

        fun SetBoundaryType(boundary_t: Int) {
            boundaryType = boundary_t
            changed = true
        }

        fun GetBoundaryType(): Int {
            return boundaryType
        }

        fun SetCloseTime(t: Float) {
            closeTime = t
            changed = true
        }

        fun GetCloseTime(): Float {
            return if (boundaryType == BT_CLOSED) closeTime else 0.0f
        }

        /*
         ====================
         idCurve_Spline::ValueForIndex

         get the value for the given time
         ====================
         */
        override fun ValueForIndex(index: Int): type? {
            val n = values.Num() - 1
            if (index < 0) {
                return if (boundaryType == BT_CLOSED) {
                    values.oGet(values.Num() + index % values.Num())
                } else {
                    values.oGet(0).oPlus(values.oGet(1).oMinus(values.oGet(0)).oMultiply(index.toFloat()))
                }
            } else if (index > n) {
                return if (boundaryType == BT_CLOSED) {
                    values.oGet(index % values.Num())
                } else {
                    values.oGet(n).oPlus(values.oGet(n).oMinus(values.oGet(n - 1)).oMultiply((index - n).toFloat()))
                }
            }
            return values.oGet(index)
        }

        /*
         ====================
         idCurve_Spline::TimeForIndex

         get the value for the given time
         ====================
         */
        override fun TimeForIndex(index: Int): Float {
            val n = times.Num() - 1
            if (index < 0) {
                return if (boundaryType == BT_CLOSED) {
                    index / times.Num() * (times.oGet(n) + closeTime) - (times.oGet(n) + closeTime - times.oGet(times.Num() + index % times.Num()))
                } else {
                    times.oGet(0) + index * (times.oGet(1) - times.oGet(0))
                }
            } else if (index > n) {
                return if (boundaryType == BT_CLOSED) {
                    index / times.Num() * (times.oGet(n) + closeTime) + times.oGet(index % times.Num())
                } else {
                    times.oGet(n) + (index - n) * (times.oGet(n) - times.oGet(n - 1))
                }
            }
            return times.oGet(index)
        }

        /*
         ====================
         idCurve_Spline::ClampedTime

         return the clamped time based on the boundary type
         ====================
         */
        protected fun ClampedTime(t: Float): Float {
            if (boundaryType == BT_CLAMPED) {
                if (t < times.oGet(0)) {
                    return times.oGet(0)
                } else if (t >= times.oGet(times.Num() - 1)) {
                    return times.oGet(times.Num() - 1)
                }
            }
            return t
        }

        companion object {
            /**
             * enum	boundary_t { BT_FREE, BT_CLAMPED, BT_CLOSED };
             */
            const val BT_FREE = 0
            const val BT_CLAMPED = 1
            const val BT_CLOSED = 2
        }

        init {
            boundaryType = BT_FREE
            closeTime = 0.0f
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Cubic Interpolating Spline template. The curve goes through all the
     * knots.
     *
     *
     * ===============================================================================
     */
    internal class idCurve_NaturalCubicSpline<type : idVec<*>?>(clazz: Class<type?>?) : idCurve_Spline<type?>(clazz) {
        protected val b: idList<type?>? = idList()
        protected val c: idList<type?>? = idList()
        protected val d: idList<type?>? = idList()
        override fun Clear() {
            super.Clear()
            values.Clear()
            b.Clear()
            c.Clear()
            d.Clear()
        }

        /*
         ====================
         idCurve_NaturalCubicSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val clampedTime = ClampedTime(time)
            val i = IndexForTime(clampedTime)
            val s = time - TimeForIndex(i)
            Setup()
            val d: type? = d.oGet(i).oMultiply(s)
            val c: type? = c.oGet(i).oPlus(d)
            val b: type? = b.oGet(i).oPlus(c).oMultiply(s)
            return values.oGet(i).oPlus(b)
        }

        /*
         ====================
         idCurve_NaturalCubicSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val clampedTime = ClampedTime(time)
            val i = IndexForTime(clampedTime)
            val s = time - TimeForIndex(i)
            Setup()
            val c: type? = c.oGet(i).oMultiply(2.0f)
            val d: type? = d.oGet(i).oMultiply(3.0f * s)
            return b.oGet(i).oPlus(c.oPlus(d).oMultiply(s))
        }

        /*
         ====================
         idCurve_NaturalCubicSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val clampedTime = ClampedTime(time)
            val i = IndexForTime(clampedTime)
            val s = time - TimeForIndex(i)
            Setup()
            val c: type? = c.oGet(i).oMultiply(2.0f)
            val d: type? = d.oGet(i).oMultiply(6.0f * s)
            return c.oPlus(d) as type
        }

        protected fun Setup() {
            if (changed) {
                when (boundaryType) {
                    BT_FREE -> SetupFree()
                    BT_CLAMPED -> SetupClamped()
                    BT_CLOSED -> SetupClosed()
                }
                changed = false
            }
        }

        protected fun SetupFree() {
            var i: Int
            var inv: Float
            val d0: FloatArray
            val d1: FloatArray
            val beta: FloatArray
            val gamma: FloatArray
            val alpha: Array<type?>
            val delta: Array<type?>
            d0 = FloatArray(values.Num() - 1)
            d1 = FloatArray(values.Num() - 1)
            alpha = arrayOfNulls<Any?>(values.Num() - 1) as Array<type?>
            beta = FloatArray(values.Num())
            gamma = FloatArray(values.Num() - 1)
            delta = arrayOfNulls<Any?>(values.Num()) as Array<type?>
            i = 0
            while (i < values.Num() - 1) {
                d0[i] = times.oGet(i + 1) - times.oGet(i)
                i++
            }
            i = 1
            while (i < values.Num() - 1) {
                d1[i] = times.oGet(i + 1) - times.oGet(i - 1)
                i++
            }
            i = 1
            while (i < values.Num() - 1) {
                val sum: type? = values.oGet(i + 1).oMultiply(d0[i - 1])
                    .oMinus(
                        values.oGet(i).oMultiply(d1[i])
                            .oPlus(values.oGet(i - 1).oMultiply(d0[i]))
                    )
                    .oMultiply(3.0f)
                inv = 1.0f / (d0[i - 1] * d0[i])
                alpha[i] = sum.oMultiply(inv) as type
                i++
            }
            beta[0] = 1.0f
            gamma[0] = 0.0f
            delta[0] = values.oGet(0).oMinus(values.oGet(0)) as type
            i = 1
            while (i < values.Num() - 1) {
                beta[i] = 2.0f * d1[i] - d0[i - 1] * gamma[i - 1]
                inv = 1.0f / beta[i]
                gamma[i] = inv * d0[i]
                delta[i] = alpha[i].oMinus(delta[i - 1].oMultiply(d0[i - 1])).oMultiply(inv) as type
                i++
            }
            beta[values.Num() - 1] = 1.0f
            delta[values.Num() - 1] = values.oGet(0).oMinus(values.oGet(0)) as type
            b.AssureSize(values.Num())
            c.AssureSize(values.Num())
            d.AssureSize(values.Num())
            c.oSet(values.Num() - 1, values.oGet(0).oMinus(values.oGet(0)))
            i = values.Num() - 2
            while (i >= 0) {
                c.oSet(i, delta[i].oMinus(c.oGet(i + 1).oMultiply(gamma[i])))
                inv = 1.0f / d0[i]
                b.oSet(
                    i, values.oGet(i + 1).oMinus(values.oGet(i)).oMultiply(inv)
                        .oMinus(
                            c.oGet(i + 1).oPlus(c.oGet(i).oMultiply(2.0f))
                                .oMultiply(1.0f / 3.0f * d0[i])
                        )
                )
                d.oSet(i, c.oGet(i + 1).oMinus(c.oGet(i)).oMultiply(1.0f / 3.0f * inv))
                i--
            }
        }

        protected fun SetupClamped() {
            var i: Int
            var inv: Float
            val d0: FloatArray
            val d1: FloatArray
            val beta: FloatArray
            val gamma: FloatArray
            val alpha: Array<type?>
            val delta: Array<type?>
            d0 = FloatArray(values.Num() - 1)
            d1 = FloatArray(values.Num() - 1)
            alpha = arrayOfNulls<Any?>(values.Num() - 1) as Array<type?>
            beta = FloatArray(values.Num())
            gamma = FloatArray(values.Num() - 1)
            delta = arrayOfNulls<Any?>(values.Num()) as Array<type?>
            i = 0
            while (i < values.Num() - 1) {
                d0[i] = times.oGet(i + 1) - times.oGet(i)
                i++
            }
            i = 1
            while (i < values.Num() - 1) {
                d1[i] = times.oGet(i + 1) - times.oGet(i - 1)
                i++
            }
            inv = 1.0f / d0[0]
            alpha[0] = values.oGet(1).oMinus(values.oGet(0)).oMultiply(3.0f * (inv - 1.0f)) as type
            inv = 1.0f / d0[values.Num() - 2]
            alpha[values.Num() - 1] = values.oGet(values.Num() - 1).oMinus(values.oGet(values.Num() - 2))
                .oMultiply(3.0f * 1.0f - 3.0f * inv) as type
            i = 1
            while (i < values.Num() - 1) {
                val sum: type? = values.oGet(i + 1).oMultiply(d0[i - 1])
                    .oMinus(values.oGet(i).oMultiply(d1[i]))
                    .oPlus(values.oGet(i - 1).oMultiply(d0[i])).oMultiply(3.0f)
                inv = 1.0f / (d0[i - 1] * d0[i])
                alpha[i] = sum.oMultiply(inv) as type
                i++
            }
            beta[0] = 2.0f * d0[0]
            gamma[0] = 0.5f
            inv = 1.0f / beta[0]
            delta[0] = alpha[0].oMultiply(inv) as type
            i = 1
            while (i < values.Num() - 1) {
                beta[i] = 2.0f * d1[i] - d0[i - 1] * gamma[i - 1]
                inv = 1.0f / beta[i]
                gamma[i] = inv * d0[i]
                delta[i] = alpha[i].oMinus(delta[i - 1].oMultiply(d0[i - 1])).oMultiply(inv) as type
                i++
            }
            beta[values.Num() - 1] = d0[values.Num() - 2] * (2.0f - gamma[values.Num() - 2])
            inv = 1.0f / beta[values.Num() - 1]
            delta[values.Num() - 1] = alpha[values.Num() - 1]
                .oMinus(delta[values.Num() - 2].oMultiply(d0[values.Num() - 2])).oMultiply(inv) as type
            b.AssureSize(values.Num())
            c.AssureSize(values.Num())
            d.AssureSize(values.Num())
            c.oSet(values.Num() - 1, delta[values.Num() - 1])
            i = values.Num() - 2
            while (i >= 0) {
                c.oSet(i, delta[i].oMinus(c.oGet(i + 1).oMultiply(gamma[i])))
                inv = 1.0f / d0[i]
                b.oSet(
                    i, values.oGet(i + 1).oMinus(values.oGet(i)).oMultiply(inv)
                        .oMinus(c.oGet(i + 1).oPlus(c.oGet(i).oMultiply(2.0f)).oMultiply(1.0f / 3.0f * d0[i]))
                )
                d.oSet(i, c.oGet(i + 1).oMinus(c.oGet(i)).oMultiply(1.0f / 3.0f * inv))
                i--
            }
        }

        protected fun SetupClosed() {
            var i: Int
            var j: Int
            var c0: Float
            var c1: Float
            val d0: FloatArray
            val mat = idMatX()
            val x = idVecX()
            d0 = FloatArray(values.Num() - 1)
            x.SetData(values.Num(), idVecX.Companion.VECX_ALLOCA(values.Num()))
            mat.SetData(values.Num(), values.Num(), idMatX.Companion.MATX_ALLOCA(values.Num() * values.Num()))
            b.AssureSize(values.Num())
            c.AssureSize(values.Num())
            d.AssureSize(values.Num())
            i = 0
            while (i < values.Num() - 1) {
                d0[i] = times.oGet(i + 1) - times.oGet(i)
                i++
            }

            // matrix of system
            mat.oSet(0, 0, 1.0f)
            mat.oSet(0, values.Num() - 1, -1.0f)
            i = 1
            while (i <= values.Num() - 2) {
                mat.oSet(i, i - 1, d0[i - 1])
                mat.oSet(i, i, 2.0f * (d0[i - 1] + d0[i]))
                mat.oSet(i, i + 1, d0[i])
                i++
            }
            mat.oSet(values.Num() - 1, values.Num() - 2, d0[values.Num() - 2])
            mat.oSet(values.Num() - 1, 0, 2.0f * (d0[values.Num() - 2] + d0[0]))
            mat.oSet(values.Num() - 1, 1, d0[0])

            // right-hand side
            c.oGet(0).Zero()
            i = 1
            while (i <= values.Num() - 2) {
                c0 = 1.0f / d0[i]
                c1 = 1.0f / d0[i - 1]
                c.oSet(
                    i, values.oGet(i + 1).oMinus(values.oGet(i)).oMultiply(c0)
                        .oMinus(values.oGet(i).oMinus(values.oGet(i - 1)).oMultiply(c1)).oMultiply(3.0f)
                )
                i++
            }
            c0 = 1.0f / d0[0]
            c1 = 1.0f / d0[values.Num() - 2]
            c.oSet(
                values.Num() - 1, values.oGet(1).oMinus(values.oGet(0)).oMultiply(c0)
                    .oMinus(values.oGet(0).oMinus(values.oGet(values.Num() - 2)).oMultiply(c1)).oMultiply(3.0f)
            )

            // solve system for each dimension
            mat.LU_Factor(null)
            i = 0
            while (i < values.oGet(0).GetDimension()) {
                j = 0
                while (j < values.Num()) {
                    x.p[j] = c.oGet(j).oGet(i)
                    j++
                }
                mat.LU_Solve(x, x, null)
                j = 0
                while (j < values.Num()) {
                    c.oGet(j).oSet(i, x.oGet(j))
                    j++
                }
                i++
            }
            i = 0
            while (i < values.Num() - 1) {
                c0 = 1.0f / d0[i]
                b.oSet(
                    i, values.oGet(i + 1).oMinus(values.oGet(i)).oMultiply(c0)
                        .oMinus(c.oGet(i + 1).oPlus(c.oGet(i).oMultiply(2.0f)).oMultiply(1.0f / 3.0f).oMultiply(d0[i]))
                )
                d.oSet(i, c.oGet(i + 1).oMinus(c.oGet(i)).oMultiply(1.0f / 3.0f * c0))
                i++
            }
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Uniform Cubic Interpolating Spline template. The curve goes through all
     * the knots.
     *
     *
     * ===============================================================================
     */
    class idCurve_CatmullRomSpline<type : idVec<*>?>(clazz: Class<type?>?) : idCurve_Spline<type?>(clazz) {
        /*
         ====================
         idCurve_CatmullRomSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            Basis(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_CatmullRomSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisFirstDerivative(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d) as type
        }

        /*
         ====================
         idCurve_CatmullRomSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisSecondDerivative(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d * d) as type
        }

        /*
         ====================
         idCurve_CatmullRomSpline::Basis

         spline basis functions
         ====================
         */
        protected fun Basis(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = ((-s + 2.0f) * s - 1.0f) * s * 0.5f // -0.5f s * s * s + s * s - 0.5f * s
            bvals.get(1) = ((3.0f * s - 5.0f) * s * s + 2.0f) * 0.5f // 1.5f * s * s * s - 2.5f * s * s + 1.0f
            bvals.get(2) = ((-3.0f * s + 4.0f) * s + 1.0f) * s * 0.5f // -1.5f * s * s * s - 2.0f * s * s + 0.5f s
            bvals.get(3) = (s - 1.0f) * s * s * 0.5f // 0.5f * s * s * s - 0.5f * s * s
        }

        /*
         ====================
         idCurve_CatmullRomSpline::BasisFirstDerivative

         first derivative of spline basis functions
         ====================
         */
        protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = (-1.5f * s + 2.0f) * s - 0.5f // -1.5f * s * s + 2.0f * s - 0.5f
            bvals.get(1) = (4.5f * s - 5.0f) * s // 4.5f * s * s - 5.0f * s
            bvals.get(2) = (-4.5f * s + 4.0f) * s + 0.5f // -4.5 * s * s + 4.0f * s + 0.5f
            bvals.get(3) = 1.5f * s * s - s // 1.5f * s * s - s
        }

        /*
         ====================
         idCurve_CatmullRomSpline::BasisSecondDerivative

         second derivative of spline basis functions
         ====================
         */
        protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = -3.0f * s + 2.0f
            bvals.get(1) = 9.0f * s - 5.0f
            bvals.get(2) = -9.0f * s + 4.0f
            bvals.get(3) = 3.0f * s - 1.0f
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Cubic Interpolating Spline template. The curve goes through all the
     * knots. The curve becomes the Catmull-Rom spline if the tension,
     * continuity and bias are all set to zero.
     *
     *
     * ===============================================================================
     */
    internal class idCurve_KochanekBartelsSpline<type : idVec<*>?>(clazz: Class<type?>?) :
        idCurve_Spline<type?>(clazz) {
        protected val bias: idList<Float?>? = idList()
        protected val continuity: idList<Float?>? = idList()
        protected val tension: idList<Float?>? = idList()

        /*
         ====================
         idCurve_KochanekBartelsSpline::AddValue

         add a timed/value pair to the spline
         returns the index to the inserted pair
         ====================
         */
        override fun AddValue(time: Float, value: type?): Int {
            val i: Int
            i = IndexForTime(time)
            times.Insert(time, i)
            values.Insert(value, i)
            tension.Insert(0.0f, i)
            continuity.Insert(0.0f, i)
            bias.Insert(0.0f, i)
            return i
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::AddValue

         add a timed/value pair to the spline
         returns the index to the inserted pair
         ====================
         */
        fun AddValue(time: Float, value: type?, tension: Float, continuity: Float, bias: Float): Int {
            val i: Int
            i = IndexForTime(time)
            times.Insert(time, i)
            values.Insert(value, i)
            this.tension.Insert(tension, i)
            this.continuity.Insert(continuity, i)
            this.bias.Insert(bias, i)
            return i
        }

        override fun RemoveIndex(index: Int) {
            values.RemoveIndex(index)
            times.RemoveIndex(index)
            tension.RemoveIndex(index)
            continuity.RemoveIndex(index)
            bias.RemoveIndex(index)
        }

        override fun Clear() {
            values.Clear()
            times.Clear()
            tension.Clear()
            continuity.Clear()
            bias.Clear()
            currentIndex = -1
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            val bvals = FloatArray(4)
            val clampedTime: Float
            val t0 = arrayOfNulls<Any?>(1) as Array<type?>
            val t1 = arrayOfNulls<Any?>(1) as Array<type?>
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            TangentsForIndex(i - 1, t0, t1)
            Basis(i - 1, clampedTime, bvals)
            v.oSet(ValueForIndex(i - 1).oMultiply(bvals[0]))
            v.oPluSet(ValueForIndex(i).oMultiply(bvals[1]))
            v.oPluSet(t0[0].oMultiply(bvals[2]))
            v.oPluSet(t1[0].oMultiply(bvals[3]))
            return v
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val t0 = arrayOfNulls<Any?>(1) as Array<type?>
            val t1 = arrayOfNulls<Any?>(1) as Array<type?>
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            TangentsForIndex(i - 1, t0, t1)
            Basis(i - 1, clampedTime, bvals)
            v.oSet(ValueForIndex(i - 1).oMultiply(bvals[0]))
            v.oPluSet(ValueForIndex(i).oMultiply(bvals[1]))
            v.oPluSet(t0[0].oMultiply(bvals[2]))
            v.oPluSet(t1[0].oMultiply(bvals[3]))
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d) as type
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val t0 = arrayOfNulls<Any?>(1) as Array<type?>
            val t1 = arrayOfNulls<Any?>(1) as Array<type?>
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            TangentsForIndex(i - 1, t0, t1)
            Basis(i - 1, clampedTime, bvals)
            v.oSet(ValueForIndex(i - 1).oMultiply(bvals[0]))
            v.oPluSet(ValueForIndex(i).oMultiply(bvals[1]))
            v.oPluSet(t0[0].oMultiply(bvals[2]))
            v.oPluSet(t1[0].oMultiply(bvals[3]))
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d * d) as type
        }

        protected fun TangentsForIndex(index: Int, t0: Array<type?>?, t1: Array<type?>?) {
            val dt: Float
            var omt: Float
            var omc: Float
            var opc: Float
            var omb: Float
            var opb: Float
            var adj: Float
            var s0: Float
            var s1: Float
            val delta: type?
            delta = ValueForIndex(index + 1).oMinus(ValueForIndex(index)) as type
            dt = TimeForIndex(index + 1) - TimeForIndex(index)
            omt = 1.0f - tension.oGet(index)
            omc = 1.0f - continuity.oGet(index)
            opc = 1.0f + continuity.oGet(index)
            omb = 1.0f - bias.oGet(index)
            opb = 1.0f + bias.oGet(index)
            adj = 2.0f * dt / (TimeForIndex(index + 1) - TimeForIndex(index - 1))
            s0 = 0.5f * adj * omt * opc * opb
            s1 = 0.5f * adj * omt * omc * omb

            // outgoing tangent at first point
            t0.get(0) = delta.oMultiply(s1)
                .oPlus(ValueForIndex(index).oMinus(ValueForIndex(index - 1)).oMultiply(s0))
            omt = 1.0f - tension.oGet(index + 1)
            omc = 1.0f - continuity.oGet(index + 1)
            opc = 1.0f + continuity.oGet(index + 1)
            omb = 1.0f - bias.oGet(index + 1)
            opb = 1.0f + bias.oGet(index + 1)
            adj = 2.0f * dt / (TimeForIndex(index + 2) - TimeForIndex(index))
            s0 = 0.5f * adj * omt * omc * opb
            s1 = 0.5f * adj * omt * opc * omb

            // incoming tangent at second point
            t1.get(0) = ValueForIndex(index + 2).oMinus(ValueForIndex(index + 1)).oMultiply(s1)
                .oPlus(delta.oMultiply(s0))
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::Basis

         spline basis functions
         ====================
         */
        protected fun Basis(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = (2.0f * s - 3.0f) * s * s + 1.0f // 2.0f * s * s * s - 3.0f * s * s + 1.0f
            bvals.get(1) = (-2.0f * s + 3.0f) * s * s // -2.0f * s * s * s + 3.0f * s * s
            bvals.get(2) = (s - 2.0f) * s * s + s // s * s * s - 2.0f * s * s + s
            bvals.get(3) = (s - 1.0f) * s * s // s * s * s - s * s
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::BasisFirstDerivative

         first derivative of spline basis functions
         ====================
         */
        protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = (6.0f * s - 6.0f) * s // 6.0f * s * s - 6.0f * s
            bvals.get(1) = (-6.0f * s + 6.0f) * s // -6.0f * s * s + 6.0f * s
            bvals.get(2) = (3.0f * s - 4.0f) * s + 1.0f // 3.0f * s * s - 4.0f * s + 1.0f
            bvals.get(3) = (3.0f * s - 2.0f) * s // 3.0f * s * s - 2.0f * s
        }

        /*
         ====================
         idCurve_KochanekBartelsSpline::BasisSecondDerivative

         second derivative of spline basis functions
         ====================
         */
        protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = 12.0f * s - 6.0f
            bvals.get(1) = -12.0f * s + 6.0f
            bvals.get(2) = 6.0f * s - 4.0f
            bvals.get(3) = 6.0f * s - 2.0f
        }
    }

    /**
     * ===============================================================================
     *
     *
     * B-Spline base template. Uses recursive definition and is slow. Use
     * idCurve_UniformCubicBSpline or idCurve_NonUniformBSpline instead.
     *
     *
     * ===============================================================================
     */
    open class idCurve_BSpline<type : idVec<*>?>     // default to cubic
        (clazz: Class<type?>?) : idCurve_Spline<type?>(clazz) {
        protected var order = 4
        fun GetOrder(): Int {
            return order
        }

        fun SetOrder(i: Int) {
            assert(i > 0 && i < 10)
            order = i
        }

        /*
         ====================
         idCurve_BSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(Basis(k - 2, order, clampedTime)))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_BSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(BasisFirstDerivative(k - 2, order, clampedTime)))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_BSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(BasisSecondDerivative(k - 2, order, clampedTime)))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_BSpline::Basis

         spline basis function
         ====================
         */
        protected fun Basis(index: Int, order: Int, t: Float): Float {
            return if (order <= 1) {
                if (TimeForIndex(index) < t && t <= TimeForIndex(index + 1)) {
                    1.0f
                } else {
                    0.0f
                }
            } else {
                var sum = 0.0f
                val d1 = TimeForIndex(index + order - 1) - TimeForIndex(index)
                if (d1 != 0.0f) {
                    sum += (t - TimeForIndex(index)) * Basis(index, order - 1, t) / d1
                }
                val d2 = TimeForIndex(index + order) - TimeForIndex(index + 1)
                if (d2 != 0.0f) {
                    sum += (TimeForIndex(index + order) - t) * Basis(index + 1, order - 1, t) / d2
                }
                sum
            }
        }

        /*
         ====================
         idCurve_BSpline::BasisFirstDerivative

         first derivative of spline basis function
         ====================
         */
        protected fun BasisFirstDerivative(index: Int, order: Int, t: Float): Float {
            return Basis(index, order - 1, t) - Basis(index + 1, order - 1, t)
            * (order - 1).toFloat() / (TimeForIndex(index + (order - 1) - 2) - TimeForIndex(index - 2))
        }

        /*
         ====================
         idCurve_BSpline::BasisSecondDerivative

         second derivative of spline basis function
         ====================
         */
        protected fun BasisSecondDerivative(index: Int, order: Int, t: Float): Float {
            return BasisFirstDerivative(index, order - 1, t) - BasisFirstDerivative(index + 1, order - 1, t)
            * (order - 1).toFloat() / (TimeForIndex(index + (order - 1) - 2) - TimeForIndex(index - 2))
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Uniform Non-Rational Cubic B-Spline template.
     *
     *
     * ===============================================================================
     */
    internal class idCurve_UniformCubicBSpline<type : idVec<*>?>(clazz: Class<type?>?) : idCurve_BSpline<type?>(clazz) {
        /*
         ====================
         idCurve_UniformCubicBSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            Basis(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_UniformCubicBSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisFirstDerivative(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d) as type
        }

        /*
         ====================
         idCurve_UniformCubicBSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val bvals = FloatArray(4)
            val d: Float
            val clampedTime: Float
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisSecondDerivative(i - 1, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < 4) {
                k = i + j - 2
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            d = TimeForIndex(i) - TimeForIndex(i - 1)
            return v.oDivide(d * d) as type
        }

        /*
         ====================
         idCurve_UniformCubicBSpline::Basis

         spline basis functions
         ====================
         */
        protected fun Basis(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = (((-s + 3.0f) * s - 3.0f) * s + 1.0f) * (1.0f / 6.0f)
            bvals.get(1) = ((3.0f * s - 6.0f) * s * s + 4.0f) * (1.0f / 6.0f)
            bvals.get(2) = (((-3.0f * s + 3.0f) * s + 3.0f) * s + 1.0f) * (1.0f / 6.0f)
            bvals.get(3) = s * s * s * (1.0f / 6.0f)
        }

        /*
         ====================
         idCurve_UniformCubicBSpline::BasisFirstDerivative

         first derivative of spline basis functions
         ====================
         */
        protected fun BasisFirstDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = -0.5f * s * s + s - 0.5f
            bvals.get(1) = 1.5f * s * s - 2.0f * s
            bvals.get(2) = -1.5f * s * s + s + 0.5f
            bvals.get(3) = 0.5f * s * s
        }

        /*
         ====================
         idCurve_UniformCubicBSpline::BasisSecondDerivative

         second derivative of spline basis functions
         ====================
         */
        protected fun BasisSecondDerivative(index: Int, t: Float, bvals: FloatArray?) {
            val s = (t - TimeForIndex(index)) / (TimeForIndex(index + 1) - TimeForIndex(index))
            bvals.get(0) = -s + 1.0f
            bvals.get(1) = 3.0f * s - 2.0f
            bvals.get(2) = -3.0f * s + 1.0f
            bvals.get(3) = s
        }

        init {
            order = 4 // always cubic
        }
    }

    /**
     * ===============================================================================
     *
     *
     * Non-Uniform Non-Rational B-Spline (NUBS) template.
     *
     *
     * ===============================================================================
     */
    open class idCurve_NonUniformBSpline<type : idVec<*>?>(clazz: Class<type?>?) : idCurve_BSpline<type?>(clazz) {
        /*
         ====================
         idCurve_NonUniformBSpline::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            Basis(i - 1, order, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_NonUniformBSpline::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisFirstDerivative(i - 1, order, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_NonUniformBSpline::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            val clampedTime: Float
            val v = newInstance()
            val bvals = FloatArray(order) //	float *bvals = (float *) _alloca16( this.order * sizeof(float) );
            if (times.Num() == 1) {
                return values.oGet(0).oMinus(values.oGet(0))
            }
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            BasisSecondDerivative(i - 1, order, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                v.oPluSet(ValueForIndex(k).oMultiply(bvals[j]))
                j++
            }
            return v
        }

        /*
         ====================
         idCurve_NonUniformBSpline::Basis

         spline basis functions
         ====================
         */
        protected fun Basis(index: Int, order: Int, t: Float, bvals: FloatArray?) {
            var r: Int
            var s: Int
            var i: Int
            var omega: Float
            bvals.get(order - 1) = 1.0f
            r = 2
            while (r <= order) {
                i = index - r + 1
                bvals.get(order - r) = 0.0f
                s = order - r + 1
                while (s < order) {
                    i++
                    omega = (t - TimeForIndex(i)) / (TimeForIndex(i + r - 1) - TimeForIndex(i))
                    bvals.get(s - 1) += (1.0f - omega) * bvals.get(s)
                    bvals.get(s) *= omega
                    s++
                }
                r++
            }
        }

        /*
         ====================
         idCurve_NonUniformBSpline::BasisFirstDerivative

         first derivative of spline basis functions
         ====================
         */
        protected fun BasisFirstDerivative(index: Int, order: Int, t: Float, bvals: FloatArray?) {
            var i: Int
            val bvals_1 = Arrays.copyOfRange(bvals, 1, bvals.size)
            Basis(index, order - 1, t, bvals_1)
            System.arraycopy(bvals_1, 0, bvals, 1, bvals_1.size)
            bvals.get(0) = 0.0f
            i = 0
            while (i < order - 1) {
                bvals.get(i) -= bvals.get(i + 1)
                bvals.get(i) *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
                i++
            }
            bvals.get(i) *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
        }

        /*
         ====================
         idCurve_NonUniformBSpline::BasisSecondDerivative

         second derivative of spline basis functions
         ====================
         */
        protected fun BasisSecondDerivative(index: Int, order: Int, t: Float, bvals: FloatArray?) {
            var i: Int
            val bvals_1 = Arrays.copyOfRange(bvals, 1, bvals.size)
            BasisFirstDerivative(index, order - 1, t, bvals_1)
            System.arraycopy(bvals_1, 0, bvals, 1, bvals_1.size)
            bvals.get(0) = 0.0f
            i = 0
            while (i < order - 1) {
                bvals.get(i) -= bvals.get(i + 1)
                bvals.get(i) *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
                i++
            }
            bvals.get(i) *= (order - 1) / (TimeForIndex(index + i + (order - 1) - 2) - TimeForIndex(index + i - 2))
        }
    }

    /*
     ===============================================================================

     Non-Uniform Rational B-Spline (NURBS) template.

     ===============================================================================
     */
    class idCurve_NURBS<type : idVec<*>?>(clazz: Class<type?>?) : idCurve_NonUniformBSpline<type?>(clazz) {
        protected val weights: idList<Float?>? = idList()

        /*
         ====================
         idCurve_NURBS::AddValue

         add a timed/value pair to the spline
         returns the index to the inserted pair
         ====================
         */
        override fun AddValue(time: Float, value: type?): Int {
            val i: Int
            i = IndexForTime(time)
            times.Insert(time, i)
            values.Insert(value, i)
            weights.Insert(1.0f, i)
            return i
        }

        /*
         ====================
         idCurve_NURBS::AddValue

         add a timed/value pair to the spline
         returns the index to the inserted pair
         ====================
         */
        fun AddValue(time: Float, value: type?, weight: Float): Int {
            val i: Int
            i = IndexForTime(time)
            times.Insert(time, i)
            values.Insert(value, i)
            weights.Insert(weight, i)
            return i
        }

        override fun RemoveIndex(index: Int) {
            values.RemoveIndex(index)
            times.RemoveIndex(index)
            weights.RemoveIndex(index)
        }

        override fun Clear() {
            values.Clear()
            times.Clear()
            weights.Clear()
            currentIndex = -1
        }

        /*
         ====================
         idCurve_NURBS::GetCurrentValue

         get the value for the given time
         ====================
         */
        override fun GetCurrentValue(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            var w: Float
            var b: Float
            val clampedTime: Float
            val bvals: FloatArray
            val v = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            bvals = FloatArray(order)
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            this.Basis(i - 1, order, clampedTime, bvals)
            v.oSet(values.oGet(0).oMinus(values.oGet(0)))
            w = 0.0f
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                b = bvals[j] * WeightForIndex(k)
                w += b
                v.oPluSet(ValueForIndex(k).oMultiply(b))
                j++
            }
            return v.oDivide(w) as type
        }

        /*
         ====================
         idCurve_NURBS::GetCurrentFirstDerivative

         get the first derivative for the given time
         ====================
         */
        override fun GetCurrentFirstDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            var w: Float
            var wb: Float
            var wd1: Float
            var b: Float
            var d1: Float
            val clampedTime: Float
            val bvals: FloatArray
            val d1vals: FloatArray
            val v = newInstance()
            val vb = newInstance()
            val vd1 = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            bvals = FloatArray(order) //	bvals = (float *) _alloca16( this.order * sizeof(float) );
            d1vals = FloatArray(order) //	d1vals = (float *) _alloca16( this.order * sizeof(float) );
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            this.Basis(i - 1, order, clampedTime, bvals)
            this.BasisFirstDerivative(i - 1, order, clampedTime, d1vals)
            vb.oSet(vd1.oSet(values.oGet(0).oMinus(values.oGet(0))))
            wd1 = 0.0f
            wb = wd1
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                w = WeightForIndex(k)
                b = bvals[j] * w
                d1 = d1vals[j] * w
                wb += b
                wd1 += d1
                v.oSet(ValueForIndex(k))
                vb.oPluSet(v.oMultiply(b))
                vd1.oPluSet(v.oMultiply(d1))
                j++
            }
            return vd1.oMultiply(wb).oMinus(vb.oMultiply(wd1)).oDivide(wb * wb)
        }

        /*
         ====================
         idCurve_NURBS::GetCurrentSecondDerivative

         get the second derivative for the given time
         ====================
         */
        override fun GetCurrentSecondDerivative(time: Float): type? {
            val i: Int
            var j: Int
            var k: Int
            var w: Float
            var wb: Float
            var wd1: Float
            var wd2: Float
            var b: Float
            var d1: Float
            var d2: Float
            val clampedTime: Float
            val bvals: FloatArray
            val d1vals: FloatArray
            val d2vals: FloatArray
            val v = newInstance()
            val vb = newInstance()
            val vd1 = newInstance()
            val vd2 = newInstance()
            if (times.Num() == 1) {
                return values.oGet(0)
            }
            bvals = FloatArray(order)
            d1vals = FloatArray(order)
            d2vals = FloatArray(order)
            clampedTime = ClampedTime(time)
            i = IndexForTime(clampedTime)
            this.Basis(i - 1, order, clampedTime, bvals)
            this.BasisFirstDerivative(i - 1, order, clampedTime, d1vals)
            this.BasisSecondDerivative(i - 1, order, clampedTime, d2vals)
            vb.oSet(vd1.oSet(vd2.oSet(values.oGet(0).oMinus(values.oGet(0)))))
            wd2 = 0.0f
            wd1 = wd2
            wb = wd1
            j = 0
            while (j < order) {
                k = i + j - (order shr 1)
                w = WeightForIndex(k)
                b = bvals[j] * w
                d1 = d1vals[j] * w
                d2 = d2vals[j] * w
                wb += b
                wd1 += d1
                wd2 += d2
                v.oSet(ValueForIndex(k))
                vb.oPluSet(v.oMultiply(b))
                vd1.oPluSet(v.oMultiply(d1))
                vd2.oPluSet(v.oMultiply(d2))
                j++
            }
            val bla1: type? =
                vd2.oMultiply(wb).oMinus(vb.oMultiply(wd2)).oMultiply(wb * wb) //( wb * wb ) * ( wb * vd2 - vb * wd2 )
            val bla2: type? = vd1.oMultiply(wb).oMinus(vb.oMultiply(wd1))
                .oMultiply(2.0f * wb * wd1) //( wb * vd1 - vb * wd1 ) * 2.0f * wb * wd1
            return bla1.oMinus(bla2).oDivide(wb * wb * wb * wb)
        }

        protected fun WeightForIndex(index: Int): Float {
            val n = weights.Num() - 1
            if (index < 0) {
                return if (boundaryType == BT_CLOSED) {
                    weights.oGet(weights.Num() + index % weights.Num())
                } else {
                    weights.oGet(0) + index * (weights.oGet(1) - weights.oGet(0))
                }
            } else if (index > n) {
                return if (boundaryType == BT_CLOSED) {
                    weights.oGet(index % weights.Num())
                } else {
                    weights.oGet(n) + (index - n) * (weights.oGet(n) - weights.oGet(n - 1))
                }
            }
            return weights.oGet(index)
        }
    }
}