package neo.idlib

import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList

/**
 *
 */
class Timer {
    internal enum class State {
        TS_STARTED, TS_STOPPED
    }

    /*
     ===============================================================================

     Clock tick counter. Should only be used for profiling.

     ===============================================================================
     */
    class idTimer {
        private var clockTicks: Double
        private var start = 0.0
        private var state: Timer.State?

        //
        //
        constructor() {
            state = Timer.State.TS_STOPPED
            clockTicks = 0.0
        }

        constructor(_clockTicks: Double) {
            state = Timer.State.TS_STOPPED
            clockTicks = _clockTicks
        }

        //public					~idTimer( void );
        //
        fun oPlus(t: idTimer?): idTimer? {
            assert(state == Timer.State.TS_STOPPED && t.state == Timer.State.TS_STOPPED)
            return idTimer(clockTicks + t.clockTicks)
        }

        fun oMinus(t: idTimer?): idTimer? {
            assert(state == Timer.State.TS_STOPPED && t.state == Timer.State.TS_STOPPED)
            return idTimer(clockTicks - t.clockTicks)
        }

        fun oPluSet(t: idTimer?): idTimer? {
            assert(state == Timer.State.TS_STOPPED && t.state == Timer.State.TS_STOPPED)
            clockTicks += t.clockTicks
            return this
        }

        fun oMinSet(t: idTimer?): idTimer? {
            assert(state == Timer.State.TS_STOPPED && t.state == Timer.State.TS_STOPPED)
            clockTicks -= t.clockTicks
            return this
        }

        fun Start() {
            assert(state == Timer.State.TS_STOPPED)
            state = Timer.State.TS_STARTED
            start = idLib.sys.GetClockTicks()
        }

        fun Stop() {
            assert(state == Timer.State.TS_STARTED)
            clockTicks += idLib.sys.GetClockTicks() - start
            if (base < 0.0) {
                InitBaseClockTicks()
            }
            if (clockTicks > base) {
                clockTicks -= base
            }
            state = Timer.State.TS_STOPPED
        }

        fun Clear() {
            clockTicks = 0.0
        }

        fun ClockTicks(): Double {
            assert(state == Timer.State.TS_STOPPED)
            return clockTicks
        }

        fun Milliseconds(): Double {
            assert(state == Timer.State.TS_STOPPED)
            return clockTicks / (idLib.sys.ClockTicksPerSecond() * 0.001)
        }

        private fun InitBaseClockTicks() {
            val timer = idTimer()
            var ct: Double
            var b: Double
            var i: Int
            base = 0.0
            b = -1.0
            i = 0
            while (i < 1000) {
                timer.Clear()
                timer.Start()
                timer.Stop()
                ct = timer.ClockTicks()
                if (b < 0.0 || ct < b) {
                    b = ct
                }
                i++
            }
            base = b
        }

        companion object {
            private var base = -1.0
        }
    }

    /*
     ===============================================================================

     Report of multiple named timers.

     ===============================================================================
     */
    internal inner class idTimerReport  //
    //
    {
        private val names: idStrList? = null
        private var reportName: idStr? = null
        private val timers: idList<idTimer?>? = idList()

        //public					~idTimerReport( void );
        //
        fun SetReportName(name: String?) {
            reportName = idStr(name ?: "Timer Report")
        }

        fun AddReport(name: String?): Int {
            if (name != null) {
                names.add(idStr(name))
                return timers.Append(idTimer())
            }
            return -1
        }

        fun Clear() {
            timers.DeleteContents(true)
            names.clear()
            reportName.Clear()
        }

        fun Reset() {
            assert(timers.Num() == names.size())
            for (i in 0 until timers.Num()) {
                timers.get(i).Clear()
            }
        }

        @Throws(idException::class)
        fun PrintReport() {
            assert(timers.Num() == names.size())
            idLib.common.Printf("Timing Report for %s\n", reportName)
            idLib.common.Printf("-------------------------------\n")
            var total = 0.0f
            for (i in 0 until names.size()) {
                idLib.common.Printf("%s consumed %5.2f seconds\n", names.get(i), timers.get(i).Milliseconds() * 0.001f)
                total += timers.get(i).Milliseconds().toFloat()
            }
            idLib.common.Printf(
                "Total time for report %s was %5.2f\n\n",
                reportName,
                total * 0.001f
            ) //TODO:char[] OR string
        }

        fun AddTime(name: String?, time: idTimer?) {
            assert(timers.Num() == names.size())
            var i: Int
            i = 0
            while (i < names.size()) {
                if (names.get(i).Icmp(name) == 0) {
                    timers.oPluSet(i, time)
                    break
                }
                i++
            }
            if (i == names.size()) {
                val index = AddReport(name)
                if (index >= 0) {
                    timers.get(index).Clear()
                    timers.oPluSet(index, time)
                }
            }
        }
    }
}