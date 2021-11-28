package neo.idlib.math

import neo.framework.DeclAF.idAFVector.type
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object Extrapolate {
    const val EXTRAPOLATION_ACCELLINEAR =
        0x04 // linear acceleration, covered distance = duration * 0.001 * ( baseSpeed + 0.5 * speed )
    const val EXTRAPOLATION_ACCELSINE =
        0x10 // sinusoidal acceleration, covered distance = duration * 0.001 * ( baseSpeed + sqrt( 0.5 ) * speed )
    const val EXTRAPOLATION_DECELLINEAR =
        0x08 // linear deceleration, covered distance = duration * 0.001 * ( baseSpeed + 0.5 * speed )
    const val EXTRAPOLATION_DECELSINE =
        0x20 // sinusoidal deceleration, covered distance = duration * 0.001 * ( baseSpeed + sqrt( 0.5 ) * speed )
    const val EXTRAPOLATION_LINEAR =
        0x02 // linear extrapolation, covered distance = duration * 0.001 * ( baseSpeed + speed )
    const val EXTRAPOLATION_NONE = 0x01 // no extrapolation, covered distance = duration * 0.001 * ( baseSpeed )
    const val EXTRAPOLATION_NOSTOP = 0x40 // do not stop at startTime + duration

    /*
     ==============================================================================================

     Extrapolate

     ==============================================================================================
     */
    class idExtrapolate<type> {
        private val DBG_count = DBG_counter++
        private var baseSpeed: type? = null
        private var currentTime: Float
        private var currentValue: type? = null
        private var duration: Float
        private /*extrapolation_t*/  var extrapolationType: Int
        private var speed: type? = null
        private var startTime: Float
        private var startValue: type? = null
        fun Init(
            startTime: Float,
            duration: Float,
            startValue: type?,
            baseSpeed: type?,
            speed: type?,
            extrapolationType: Int
        ) {
            this.extrapolationType = extrapolationType
            this.startTime = startTime
            this.duration = duration
            this.startValue = startValue
            this.baseSpeed = baseSpeed
            this.speed = speed
            currentTime = -1f
            currentValue = startValue
        }

        fun GetCurrentValue(time: Float): type? {
            var time = time
            val deltaTime: Float
            val s: Float
            if (time == currentTime) {
                return currentValue
            }
            currentTime = time
            if (time < startTime) {
                return startValue
            }
            if (0 == extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP && time > startTime + duration) {
                time = startTime + duration
            }
            when (extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP.inv()) {
                Extrapolate.EXTRAPOLATION_NONE -> {
                    deltaTime = (time - startTime) * 0.001f
                    currentValue = _Plus(startValue, _Multiply(deltaTime, baseSpeed))
                }
                Extrapolate.EXTRAPOLATION_LINEAR -> {
                    deltaTime = (time - startTime) * 0.001f
                    currentValue = _Plus(startValue, _Multiply(deltaTime, _Plus(baseSpeed, speed)))
                }
                Extrapolate.EXTRAPOLATION_ACCELLINEAR -> {
                    if (0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = 0.5f * deltaTime * deltaTime * (duration * 0.001f)
                        currentValue = _Plus(startValue, _Plus(_Multiply(deltaTime, baseSpeed), _Multiply(s, speed)))
                    }
                }
                Extrapolate.EXTRAPOLATION_DECELLINEAR -> {
                    if (0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = (deltaTime - 0.5f * deltaTime * deltaTime) * (duration * 0.001f)
                        currentValue = _Plus(startValue, _Plus(_Multiply(deltaTime, baseSpeed), _Multiply(s, speed)))
                    }
                }
                Extrapolate.EXTRAPOLATION_ACCELSINE -> {
                    if (0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = (1.0f - idMath.Cos(deltaTime * idMath.HALF_PI)) * duration * 0.001f * idMath.SQRT_1OVER2
                        currentValue = _Plus(startValue, _Plus(_Multiply(deltaTime, baseSpeed), _Multiply(s, speed)))
                    }
                }
                Extrapolate.EXTRAPOLATION_DECELSINE -> {
                    if (0f == duration) {
                        currentValue = startValue
                    } else {
                        deltaTime = (time - startTime) / duration
                        s = idMath.Sin(deltaTime * idMath.HALF_PI) * duration * 0.001f * idMath.SQRT_1OVER2
                        currentValue = _Plus(startValue, _Plus(_Multiply(deltaTime, baseSpeed), _Multiply(s, speed)))
                    }
                }
            }
            return currentValue
        }

        fun GetCurrentSpeed(time: Float): type? {
            val deltaTime: Float
            val s: Float
            if (time < startTime || 0f == duration) {
                return _Minus(startValue, startValue)
            }
            return if (0 == extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP && time > startTime + duration) {
                _Minus(startValue, startValue)
            } else when (extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP.inv()) {
                Extrapolate.EXTRAPOLATION_NONE -> {
                    baseSpeed
                }
                Extrapolate.EXTRAPOLATION_LINEAR -> {
                    _Plus(baseSpeed, speed)
                }
                Extrapolate.EXTRAPOLATION_ACCELLINEAR -> {
                    deltaTime = (time - startTime) / duration
                    s = deltaTime
                    _Plus(baseSpeed, _Multiply(s, speed))
                }
                Extrapolate.EXTRAPOLATION_DECELLINEAR -> {
                    deltaTime = (time - startTime) / duration
                    s = 1.0f - deltaTime
                    _Plus(baseSpeed, _Multiply(s, speed))
                }
                Extrapolate.EXTRAPOLATION_ACCELSINE -> {
                    deltaTime = (time - startTime) / duration
                    s = idMath.Sin(deltaTime * idMath.HALF_PI)
                    _Plus(baseSpeed, _Multiply(s, speed))
                }
                Extrapolate.EXTRAPOLATION_DECELSINE -> {
                    deltaTime = (time - startTime) / duration
                    s = idMath.Cos(deltaTime * idMath.HALF_PI)
                    _Plus(baseSpeed, _Multiply(s, speed))
                }
                else -> {
                    baseSpeed
                }
            }
        }

        fun IsDone(time: Float): Boolean {
            return 0 == extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP && time >= startTime + duration
        }

        fun SetStartTime(time: Float) {
            startTime = time
            currentTime = -1f
        }

        fun GetStartTime(): Float {
            return startTime
        }

        fun GetEndTime(): Float {
            return if (0 == extrapolationType and Extrapolate.EXTRAPOLATION_NOSTOP && duration > 0) startTime + duration else 0
        }

        fun GetDuration(): Float {
            return duration
        }

        fun SetStartValue(value: type?) {
            startValue = value
            currentTime = -1f
        }

        fun GetStartValue(): type? {
            return startValue
        }

        fun GetBaseSpeed(): type? {
            return baseSpeed
        }

        fun GetSpeed(): type? {
            return speed
        }

        /*extrapolation_t*/   fun GetExtrapolationType(): Int {
            return extrapolationType
        }

        private fun _Multiply(f: Float, t: type?): type? {
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

        companion object {
            //
            //
            private var DBG_counter = 0
        }

        init {
            extrapolationType = Extrapolate.EXTRAPOLATION_NONE
            duration = 0.0f
            startTime = duration
            //	memset( &startValue, 0, sizeof( startValue ) );
//	memset( &baseSpeed, 0, sizeof( baseSpeed ) );
//	memset( &speed, 0, sizeof( speed ) );
            currentTime = -1f
            //            currentValue = startValue;
        }
    }
}