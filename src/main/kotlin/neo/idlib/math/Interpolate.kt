package neo.idlib.math

import neo.TempDump.SERiAL
import neo.framework.DeclAF.idAFVector.type
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Extrapolate.idExtrapolate
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.ByteBuffer

/**
 *
 */
class Interpolate {
    /*
     ==============================================================================================

     Linear interpolation.

     ==============================================================================================
     */
    class idInterpolate<type> {
        private var currentTime: Float
        private var currentValue: type? = null
        private var duration = 0f
        private var endValue: type? = null
        private var startTime: Float
        private var startValue: type? = null
        fun Init(startTime: Float, duration: Float, startValue: type?, endValue: type?) {
            this.startTime = startTime
            this.duration = duration
            this.startValue = startValue
            this.endValue = endValue
            currentTime = startTime - 1
            currentValue = startValue
        }

        fun SetStartTime(time: Float) {
            startTime = time
        }

        fun SetDuration(duration: Float) {
            this.duration = duration
        }

        fun SetStartValue(startValue: type?) {
            this.startValue = startValue
        }

        fun SetEndValue(endValue: type?) {
            this.endValue = endValue
        }

        fun GetCurrentValue(time: Float): type? {
            val deltaTime: Float
            deltaTime = time - startTime
            if (time != currentTime) {
                currentTime = time
                if (deltaTime <= 0) {
                    currentValue = startValue
                } else if (deltaTime >= duration) {
                    currentValue = endValue
                } else {
                    if (currentValue is Int) {
                        val e: Int = endValue as Int?
                        val s: Int = startValue as Int?
                        currentValue = (s + (e - s) * (deltaTime / duration)).toInt() as type
                    }
                    if (currentValue is Float) {
                        val e: Float = endValue as Float?
                        val s: Float = startValue as Float?
                        currentValue = (s + (e - s) * (deltaTime / duration)) as type
                    }
                }
            }
            return currentValue
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + duration
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + duration
        }

        fun GetDuration(): Float {
            return duration
        }

        fun GetStartValue(): type? {
            return startValue
        }

        fun GetEndValue(): type? {
            return endValue
        }

        //
        //
        init {
            startTime = duration
            currentTime = startTime
            //            memset( & currentValue, 0, sizeof(currentValue));
//            startValue = endValue = currentValue;
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with linear acceleration and deceleration phase.
     The velocity is continuous but the acceleration is not.

     ==============================================================================================
     */
    class idInterpolateAccelDecelLinear<type> : SERiAL {
        private var accelTime: Float
        private var decelTime = 0f
        private var endValue: type?
        private val extrapolate: idExtrapolate<type?>?
        private var linearTime: Float
        private var startTime: Float
        private var startValue: type? = null
        fun Init(
            startTime: Float,
            accelTime: Float,
            decelTime: Float,
            duration: Float,
            startValue: type?,
            endValue: type?
        ) {
            val speed: type?
            this.startTime = startTime
            this.accelTime = accelTime
            this.decelTime = decelTime
            this.startValue = startValue
            this.endValue = endValue
            if (duration <= 0.0f) {
                return
            }
            if (this.accelTime + this.decelTime > duration) {
                this.accelTime = this.accelTime * duration / (this.accelTime + this.decelTime)
                this.decelTime = duration - this.accelTime
            }
            linearTime = duration - this.accelTime - this.decelTime
            speed = _Multiply(
                _Minus(endValue, startValue),
                1000.0f / (linearTime + (this.accelTime + this.decelTime) * 0.5f)
            )
            if (0.0f != this.accelTime) {
                extrapolate.Init(
                    startTime,
                    this.accelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_ACCELLINEAR
                )
            } else if (0.0f != linearTime) {
                extrapolate.Init(
                    startTime,
                    linearTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_LINEAR
                )
            } else {
                extrapolate.Init(
                    startTime,
                    this.decelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_DECELLINEAR
                )
            }
        }

        fun SetStartTime(time: Float) {
            startTime = time
            Invalidate()
        }

        fun SetStartValue(startValue: type?) {
            this.startValue = startValue
            Invalidate()
        }

        fun SetEndValue(endValue: type?) {
            this.endValue = endValue
            Invalidate()
        }

        fun GetCurrentValue(time: Float): type? {
            SetPhase(time)
            return extrapolate.GetCurrentValue(time)
        }

        fun GetCurrentSpeed(time: Float): type? {
            SetPhase(time)
            return extrapolate.GetCurrentSpeed(time)
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + accelTime + linearTime + decelTime
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + accelTime + linearTime + decelTime
        }

        fun GetDuration(): Float {
            return accelTime + linearTime + decelTime
        }

        fun GetAcceleration(): Float {
            return accelTime
        }

        fun GetDeceleration(): Float {
            return decelTime
        }

        fun GetStartValue(): type? {
            return startValue
        }

        fun GetEndValue(): type? {
            return endValue
        }

        private fun Invalidate() {
            extrapolate.Init(
                0f,
                0f,
                extrapolate.GetStartValue(),
                extrapolate.GetBaseSpeed(),
                extrapolate.GetSpeed(),
                Extrapolate.EXTRAPOLATION_NONE
            )
        }

        private fun SetPhase(time: Float) {
            val deltaTime: Float
            deltaTime = time - startTime
            if (deltaTime < accelTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_ACCELLINEAR) {
                    extrapolate.Init(
                        startTime,
                        accelTime,
                        startValue,
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_ACCELLINEAR
                    )
                }
            } else if (deltaTime < accelTime + linearTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_LINEAR) {
                    extrapolate.Init(
                        startTime + accelTime,
                        linearTime,
                        _Plus(startValue, _Multiply(extrapolate.GetSpeed(), accelTime * 0.001f * 0.5f)),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_LINEAR
                    )
                }
            } else {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_DECELLINEAR) {
                    extrapolate.Init(
                        startTime + accelTime + linearTime,
                        decelTime,
                        _Minus(endValue, _Multiply(extrapolate.GetSpeed(), decelTime * 0.001f * 0.5f)),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_DECELLINEAR
                    )
                }
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

        private fun _Multiply(t: type?, f: Float): type? {
            if (t is idVec3) {
                return (t as idVec3?).oMultiply(f)
            } else if (t is idVec4) {
                return (t as idVec4?).oMultiply(f)
            } else if (t is idAngles) {
                return (t as idAngles?).oMultiply(f)
            } else if (t is Double) {
                return java.lang.Double.valueOf(f * t as Double?) as type
            }
            return java.lang.Float.valueOf(f * t as Float?) as type
        }

        private fun _Plus(t1: type?, t2: type?): type? {
            if (t1 is idVec3) {
                return (t1 as idVec3?).oPlus(t2 as idVec3?)
            } else if (t1 is idVec4) {
                return (t1 as idVec4?).oPlus(t2 as idVec4?)
            } else if (t1 is idAngles) {
                return (t1 as idAngles?).oPlus(t2 as idAngles?)
            } else if (t1 is Double) {
                return java.lang.Double.valueOf(t1 as Double? + t2 as Double?) as type
            }
            return java.lang.Float.valueOf(t1 as Float? + t2 as Float?) as type
        }

        private fun _Minus(t1: type?, t2: type?): type? {
            if (t1 is idVec3) {
                return (t1 as idVec3?).oMinus(t2 as idVec3?)
            } else if (t1 is idVec4) {
                return (t1 as idVec4?).oMinus(t2 as idVec4?)
            } else if (t1 is idAngles) {
                return (t1 as idAngles?).oMinus(t2 as idAngles?)
            }
            return java.lang.Float.valueOf(t1 as Float? - t2 as Float?) as type
        }

        //
        //
        init {
            linearTime = decelTime
            accelTime = linearTime
            startTime = accelTime
            //	memset( &startValue, 0, sizeof( startValue ) );
            endValue = startValue
            extrapolate = idExtrapolate()
        }
    }

    /*
     ==============================================================================================

     Continuous interpolation with sinusoidal acceleration and deceleration phase.
     Both the velocity and acceleration are continuous.

     ==============================================================================================
     */
    internal inner class idInterpolateAccelDecelSine<type> {
        private var accelTime: Float
        private var decelTime = 0f
        private var endValue: type?
        private val extrapolate: idExtrapolate<type?>? = null
        private var linearTime: Float
        private var startTime: Float
        private var startValue: type? = null
        fun Init(
            startTime: Float,
            accelTime: Float,
            decelTime: Float,
            duration: Float,
            startValue: type?,
            endValue: type?
        ) {
            val speed: type?
            this.startTime = startTime
            this.accelTime = accelTime
            this.decelTime = decelTime
            this.startValue = startValue
            this.endValue = endValue
            if (duration <= 0.0f) {
                return
            }
            if (this.accelTime + this.decelTime > duration) {
                this.accelTime = this.accelTime * duration / (this.accelTime + this.decelTime)
                this.decelTime = duration - this.accelTime
            }
            linearTime = duration - this.accelTime - this.decelTime
            speed = _Multiply(
                _Minus(endValue, startValue),
                1000.0f / (linearTime + (this.accelTime + this.decelTime) * idMath.SQRT_1OVER2)
            )
            if (0f != this.accelTime) {
                extrapolate.Init(
                    startTime,
                    this.accelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_ACCELSINE
                )
            } else if (0f != linearTime) {
                extrapolate.Init(
                    startTime,
                    linearTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_LINEAR
                )
            } else {
                extrapolate.Init(
                    startTime,
                    this.decelTime,
                    startValue,
                    _Minus(startValue, startValue),
                    speed,
                    Extrapolate.EXTRAPOLATION_DECELSINE
                )
            }
        }

        fun SetStartTime(time: Float) {
            startTime = time
            Invalidate()
        }

        fun SetStartValue(startValue: type?) {
            this.startValue = startValue
            Invalidate()
        }

        fun SetEndValue(endValue: type?) {
            this.endValue = endValue
            Invalidate()
        }

        fun GetCurrentValue(time: Float): type? {
            SetPhase(time)
            return extrapolate.GetCurrentValue(time)
        }

        fun GetCurrentSpeed(time: Float): type? {
            SetPhase(time)
            return extrapolate.GetCurrentSpeed(time)
        }

        fun IsDone(time: Float): Boolean {
            return time >= startTime + accelTime + linearTime + decelTime
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return startTime + accelTime + linearTime + decelTime
        }

        fun GetDuration(): Float {
            return accelTime + linearTime + decelTime
        }

        fun GetAcceleration(): Float {
            return accelTime
        }

        fun GetDeceleration(): Float {
            return decelTime
        }

        fun GetStartValue(): type? {
            return startValue
        }

        fun GetEndValue(): type? {
            return endValue
        }

        private fun Invalidate() {
            extrapolate.Init(
                0f,
                0f,
                extrapolate.GetStartValue(),
                extrapolate.GetBaseSpeed(),
                extrapolate.GetSpeed(),
                Extrapolate.EXTRAPOLATION_NONE
            )
        }

        private fun SetPhase(time: Float) {
            val deltaTime: Float
            deltaTime = time - startTime
            if (deltaTime < accelTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_ACCELSINE) {
                    extrapolate.Init(
                        startTime,
                        accelTime,
                        startValue,
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_ACCELSINE
                    )
                }
            } else if (deltaTime < accelTime + linearTime) {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_LINEAR) {
                    extrapolate.Init(
                        startTime + accelTime,
                        linearTime,
                        _Plus(startValue, _Plus(extrapolate.GetSpeed(), accelTime * 0.001f * idMath.SQRT_1OVER2)),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_LINEAR
                    )
                }
            } else {
                if (extrapolate.GetExtrapolationType() != Extrapolate.EXTRAPOLATION_DECELSINE) {
                    extrapolate.Init(
                        startTime + accelTime + linearTime,
                        decelTime,
                        _Plus(endValue, _Minus(extrapolate.GetSpeed(), decelTime * 0.001f * idMath.SQRT_1OVER2)),
                        extrapolate.GetBaseSpeed(),
                        extrapolate.GetSpeed(),
                        Extrapolate.EXTRAPOLATION_DECELSINE
                    )
                }
            }
        }

        private fun _Multiply(t: type?, f: Float): type? {
            if (t is idVec3) {
                return (t as idVec3?).oMultiply(f)
            } else if (t is idVec4) {
                return (t as idVec4?).oMultiply(f)
            } else if (t is idAngles) {
                return (t as idAngles?).oMultiply(f)
            } else if (t is Double) {
                return java.lang.Double.valueOf(f * t as Double?) as type
            }
            return java.lang.Float.valueOf(f * t as Float?) as type
        }

        private fun _Plus(t1: type?, t2: Any?): type? {
            if (t1 is idVec3) {
                return (t1 as idVec3?).oPlus(t2 as idVec3?)
            } else if (t1 is idVec4) {
                return (t1 as idVec4?).oPlus(t2 as idVec4?)
            } else if (t1 is idAngles) {
                return (t1 as idAngles?).oPlus(t2 as idAngles?)
            } else if (t1 is Double) {
                return java.lang.Double.valueOf(t1 as Double? + t2 as Double?) as type
            }
            return java.lang.Float.valueOf(t1 as Float? + t2 as Float?) as type
        }

        private fun _Minus(t1: type?, t2: Any?): type? {
            if (t1 is idVec3) {
                return (t1 as idVec3?).oMinus(t2 as idVec3?)
            } else if (t1 is idVec4) {
                return (t1 as idVec4?).oMinus(t2 as idVec4?)
            } else if (t1 is idAngles) {
                return (t1 as idAngles?).oMinus(t2 as idAngles?)
            }
            return java.lang.Float.valueOf(t1 as Float? - t2 as Float?) as type
        }

        //
        //
        init {
            linearTime = decelTime
            accelTime = linearTime
            startTime = accelTime
            //	memset( &startValue, 0, sizeof( startValue ) );
            endValue = startValue
        }
    }
}