package neo.idlib;

import neo.idlib.Lib.idException;
import neo.idlib.Lib.idLib;
import neo.idlib.Text.Str.idStr;
import neo.idlib.containers.List.idList;
import neo.idlib.containers.idStrList;

import static neo.idlib.Timer.State.TS_STARTED;
import static neo.idlib.Timer.State.TS_STOPPED;

/**
 *
 */
public class Timer {

    enum State {

        TS_STARTED,
        TS_STOPPED
    }

    /*
     ===============================================================================

     Clock tick counter. Should only be used for profiling.

     ===============================================================================
     */
    public static class idTimer {

        private static double base = -1;
        private double clockTicks;
        private double start;
        private State state;
        //
        //

        public idTimer() {
            state = TS_STOPPED;
            clockTicks = 0.0;
        }

        public idTimer(double _clockTicks) {
            state = TS_STOPPED;
            clockTicks = _clockTicks;
        }
//public					~idTimer( void );
//

        public idTimer oPlus(final idTimer t) {
            assert (state == TS_STOPPED && t.state == TS_STOPPED);
            return new idTimer(clockTicks + t.clockTicks);
        }

        public idTimer oMinus(final idTimer t) {
            assert (state == TS_STOPPED && t.state == TS_STOPPED);
            return new idTimer(clockTicks - t.clockTicks);
        }

        public idTimer oPluSet(final idTimer t) {
            assert (state == TS_STOPPED && t.state == TS_STOPPED);
            clockTicks += t.clockTicks;
            return this;
        }

        public idTimer oMinSet(final idTimer t) {
            assert (state == TS_STOPPED && t.state == TS_STOPPED);
            clockTicks -= t.clockTicks;
            return this;
        }

        public void Start() {
            assert (state == TS_STOPPED);
            state = TS_STARTED;
            start = idLib.sys.GetClockTicks();
        }

        public void Stop() {
            assert (state == TS_STARTED);
            clockTicks += idLib.sys.GetClockTicks() - start;
            if (base < 0.0) {
                InitBaseClockTicks();
            }
            if (clockTicks > base) {
                clockTicks -= base;
            }
            state = TS_STOPPED;
        }

        public void Clear() {
            clockTicks = 0.0;
        }

        public double ClockTicks() {
            assert (state == TS_STOPPED);
            return clockTicks;
        }

        public double Milliseconds() {
            assert (state == TS_STOPPED);
            return clockTicks / (idLib.sys.ClockTicksPerSecond() * 0.001);
        }

        private void InitBaseClockTicks() {
            idTimer timer = new idTimer();
            double ct, b;
            int i;

            base = 0.0;
            b = -1.0;
            for (i = 0; i < 1000; i++) {
                timer.Clear();
                timer.Start();
                timer.Stop();
                ct = timer.ClockTicks();
                if (b < 0.0 || ct < b) {
                    b = ct;
                }
            }
            base = b;
        }
    }

    /*
     ===============================================================================

     Report of multiple named timers.

     ===============================================================================
     */
    class idTimerReport {

        private idStrList names;
        private idStr reportName;
        private idList<idTimer> timers;
        //
        //

        public idTimerReport() {
        }
//public					~idTimerReport( void );
//

        public void SetReportName(final String name) {
            reportName = new idStr((name != null) ? name : "Timer Report");
        }

        public int AddReport(final String name) {
            if (name != null) {
                names.add(new idStr(name));
                return timers.Append(new idTimer());
            }
            return -1;
        }

        public void Clear() {
            timers.DeleteContents(true);
            names.clear();
            reportName.Clear();
        }

        public void Reset() {
            assert (timers.Num() == names.size());
            for (int i = 0; i < timers.Num(); i++) {
                timers.oGet(i).Clear();
            }
        }

        public void PrintReport() throws idException {
            assert (timers.Num() == names.size());
            idLib.common.Printf("Timing Report for %s\n", reportName);
            idLib.common.Printf("-------------------------------\n");
            float total = 0.0f;
            for (int i = 0; i < names.size(); i++) {
                idLib.common.Printf("%s consumed %5.2f seconds\n", names.get(i), timers.oGet(i).Milliseconds() * 0.001f);
                total += timers.oGet(i).Milliseconds();
            }
            idLib.common.Printf("Total time for report %s was %5.2f\n\n", reportName, total * 0.001f);//TODO:char[] OR string
        }

        public void AddTime(final String name, idTimer time) {
            assert (timers.Num() == names.size());
            int i;
            for (i = 0; i < names.size(); i++) {
                if (names.get(i).Icmp(name) == 0) {
                    timers.oPluSet(i, time);
                    break;
                }
            }
            if (i == names.size()) {
                int index = AddReport(name);
                if (index >= 0) {
                    timers.oGet(index).Clear();
                    timers.oPluSet(index, time);
                }
            }
        }
    }

}
