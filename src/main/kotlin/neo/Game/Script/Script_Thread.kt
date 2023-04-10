package neo.Game.Script

import neo.CM.CollisionModel.trace_s
import neo.Game.AFEntity.idAFEntity_Base
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.*
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Game_local.idGameLocal.Companion.Error
import neo.Game.Physics.Clip.CLIPMODEL_ID_TO_JOINT_HANDLE
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Script.Script_Program.function_t
import neo.TempDump.btoi
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.DeclManager
import neo.framework.UsercmdGen.USERCMD_HZ
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmpn
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.MS2SEC
import neo.idlib.math.Math_h.SEC2MS
import neo.idlib.math.Vector.getVec3_zero
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object Script_Thread {
    val EV_Thread_SetCallback = idEventDef("<script_setcallback>", null)
    val EV_Thread_Wait = idEventDef("wait", "f")
    val EV_Thread_WaitFrame = idEventDef("waitFrame")
    val EV_Thread_AngToForward = idEventDef("angToForward", "v", 'v')
    val EV_Thread_AngToRight = idEventDef("angToRight", "v", 'v')
    val EV_Thread_AngToUp = idEventDef("angToUp", "v", 'v')
    val EV_Thread_Assert = idEventDef("assert", "f")
    val EV_Thread_ClearPersistantArgs = idEventDef("clearPersistantArgs")
    val EV_Thread_ClearSignal = idEventDef("clearSignalThread", "de")
    val EV_Thread_CopySpawnArgs = idEventDef("copySpawnArgs", "e")
    val EV_Thread_Cosine = idEventDef("cos", "f", 'f')
    val EV_Thread_DebugArrow = idEventDef("debugArrow", "vvvdf")
    val EV_Thread_DebugBounds = idEventDef("debugBounds", "vvvf")
    val EV_Thread_DebugCircle = idEventDef("debugCircle", "vvvfdf")
    val EV_Thread_DebugLine = idEventDef("debugLine", "vvvf")
    val EV_Thread_DrawText = idEventDef("drawText", "svfvdf")
    val EV_Thread_Error = idEventDef("error", "s")
    val EV_Thread_Execute = idEventDef("<execute>", null)
    val EV_Thread_FadeIn = idEventDef("fadeIn", "vf")
    val EV_Thread_FadeOut = idEventDef("fadeOut", "vf")
    val EV_Thread_FadeTo = idEventDef("fadeTo", "vff")
    val EV_Thread_FirstPerson = idEventDef("firstPerson", null)
    val EV_Thread_GetCvar = idEventDef("getcvar", "s", 's')
    val EV_Thread_GetEntity = idEventDef("getEntity", "s", 'e')
    val EV_Thread_GetFrameTime = idEventDef("getFrameTime", null, 'f')
    val EV_Thread_GetPersistantFloat = idEventDef("getPersistantFloat", "s", 'f')
    val EV_Thread_GetPersistantString = idEventDef("getPersistantString", "s", 's')
    val EV_Thread_GetPersistantVector = idEventDef("getPersistantVector", "s", 'v')
    val EV_Thread_GetTicsPerSecond = idEventDef("getTicsPerSecond", null, 'f')
    val EV_Thread_GetTime = idEventDef("getTime", null, 'f')
    val EV_Thread_GetTraceBody = idEventDef("getTraceBody", null, 's')
    val EV_Thread_GetTraceEndPos = idEventDef("getTraceEndPos", null, 'v')
    val EV_Thread_GetTraceEntity = idEventDef("getTraceEntity", null, 'e')
    val EV_Thread_GetTraceFraction = idEventDef("getTraceFraction", null, 'f')
    val EV_Thread_GetTraceJoint = idEventDef("getTraceJoint", null, 's')
    val EV_Thread_GetTraceNormal = idEventDef("getTraceNormal", null, 'v')
    val EV_Thread_InfluenceActive = idEventDef("influenceActive", null, 'd')
    val EV_Thread_IsClient = idEventDef("isClient", null, 'f')
    val EV_Thread_IsMultiplayer = idEventDef("isMultiplayer", null, 'f')
    val EV_Thread_KillThread = idEventDef("killthread", "s")
    val EV_Thread_Normalize = idEventDef("vecNormalize", "v", 'v')
    val EV_Thread_OnSignal = idEventDef("onSignal", "des")
    val EV_Thread_Pause = idEventDef("pause", null)
    val EV_Thread_Print = idEventDef("print", "s")
    val EV_Thread_PrintLn = idEventDef("println", "s")
    val EV_Thread_RadiusDamage = idEventDef("radiusDamage", "vEEEsf")
    val EV_Thread_Random = idEventDef("random", "f", 'f')
    val EV_Thread_Say = idEventDef("say", "s")
    val EV_Thread_SetCamera = idEventDef("setCamera", "e")
    val EV_Thread_SetCvar = idEventDef("setcvar", "ss")
    val EV_Thread_SetPersistantArg = idEventDef("setPersistantArg", "ss")
    val EV_Thread_SetSpawnArg = idEventDef("setSpawnArg", "ss")
    val EV_Thread_SetThreadName = idEventDef("threadname", "s")
    val EV_Thread_Sine = idEventDef("sin", "f", 'f')
    val EV_Thread_Spawn = idEventDef("spawn", "s", 'e')
    val EV_Thread_SpawnFloat = idEventDef("SpawnFloat", "sf", 'f')
    val EV_Thread_SpawnString = idEventDef("SpawnString", "ss", 's')
    val EV_Thread_SpawnVector = idEventDef("SpawnVector", "sv", 'v')
    val EV_Thread_SquareRoot = idEventDef("sqrt", "f", 'f')
    val EV_Thread_StartMusic = idEventDef("music", "s")
    val EV_Thread_StrLeft = idEventDef("strLeft", "sd", 's')
    val EV_Thread_StrLen = idEventDef("strLength", "s", 'd')
    val EV_Thread_StrMid = idEventDef("strMid", "sdd", 's')
    val EV_Thread_StrRight = idEventDef("strRight", "sd", 's')
    val EV_Thread_StrSkip = idEventDef("strSkip", "sd", 's')
    val EV_Thread_StrToFloat = idEventDef("strToFloat", "s", 'f')

    //
    // script callable events
    val EV_Thread_TerminateThread = idEventDef("terminate", "d")
    val EV_Thread_Trace = idEventDef("trace", "vvvvde", 'f')
    val EV_Thread_TracePoint = idEventDef("tracePoint", "vvde", 'f')
    val EV_Thread_Trigger = idEventDef("trigger", "e")
    val EV_Thread_VecCrossProduct = idEventDef("CrossProduct", "vv", 'v')
    val EV_Thread_VecDotProduct = idEventDef("DotProduct", "vv", 'f')
    val EV_Thread_VecLength = idEventDef("vecLength", "v", 'f')
    val EV_Thread_VecToAngles = idEventDef("VecToAngles", "v", 'v')
    val EV_Thread_WaitFor = idEventDef("waitFor", "e")
    val EV_Thread_WaitForThread = idEventDef("waitForThread", "d")
    val EV_Thread_Warning = idEventDef("warning", "s")

    class idThread : idClass {
        private var creationTime = 0
        private val interpreter = idInterpreter()

        //
        private var lastExecuteTime = 0

        //
        private var manualControl = false

        //
        private var spawnArgs: idDict? = null
        private val threadName = idStr()

        //
        private var threadNum = 0
        private var waitingFor = 0

        //
        private var waitingForThread: idThread? = null
        private var waitingUntil = 0

        constructor() {
            Init()
            SetThreadName(String.format("thread_%d", threadIndex))
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        constructor(self: idEntity?, func: function_t?) {
            assert(self != null)
            Init()
            SetThreadName(self!!.name.toString())
            interpreter.EnterObjectFunction(self, func, false)
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        constructor(func: function_t?) {
            assert(func != null)
            Init()
            SetThreadName(func!!.Name())
            interpreter.EnterFunction(func, false)
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        constructor(source: idInterpreter, func: function_t?, args: Int) {
            Init()
            interpreter.ThreadCall(source, func, args)
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        constructor(source: idInterpreter, self: idEntity?, func: function_t?, args: Int) {
            assert(self != null)
            Init()
            SetThreadName(self!!.name.toString())
            interpreter.ThreadCall(source, func, args)
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        override fun Init() {
            // create a unique threadNum
            do {
                threadIndex++
                if (threadIndex == 0) {
                    threadIndex = 1
                }
            } while (GetThread(threadIndex) != null)
            threadNum = threadIndex
            threadList.Append(this)
            creationTime = Game_local.gameLocal.time
            lastExecuteTime = 0
            manualControl = false
            ClearWaitFor()
            interpreter.SetThread(this)
            spawnArgs = idDict()
        }

        private fun Pause() {
            ClearWaitFor()
            interpreter.doneProcessing = true
        }

        private fun Event_Execute() {
            Execute()
        }

        private fun Event_Pause() {
            Pause()
        }

        private fun Event_WaitFrame() {
            WaitFrame()
        }

        private fun Event_GetTime() {
            ReturnFloat(MS2SEC(Game_local.gameLocal.realClientTime.toFloat()))
        }

        private fun Event_ClearPersistantArgs() {
            Game_local.gameLocal.persistentLevelInfo.Clear()
        }

        private fun Event_FirstPerson() {
            Game_local.gameLocal.SetCamera(null)
        }

        private fun Event_GetTraceFraction() {
            ReturnFloat(trace.fraction)
        }

        private fun Event_GetTraceEndPos() {
            ReturnVector(trace.endpos)
        }

        private fun Event_GetTraceNormal() {
            if (trace.fraction < 1.0f) {
                ReturnVector(trace.c.normal)
            } else {
                ReturnVector(idVec3())
            }
        }

        private fun Event_GetTraceEntity() {
            if (trace.fraction < 1.0f) {
                ReturnEntity(Game_local.gameLocal.entities[trace.c.entityNum])
            } else {
                ReturnEntity(null)
            }
        }

        private fun Event_GetTraceJoint() {
            if (trace.fraction < 1.0f && trace.c.id < 0) {
                val af = Game_local.gameLocal.entities[trace.c.entityNum] as idAFEntity_Base?
                if (af != null && af is idAFEntity_Base && af.IsActiveAF()) {
                    ReturnString(af.GetAnimator().GetJointName(CLIPMODEL_ID_TO_JOINT_HANDLE(trace.c.id)))
                    return
                }
            }
            ReturnString("")
        }

        private fun Event_GetTraceBody() {
            if (trace.fraction < 1.0f && trace.c.id < 0) {
                val af = Game_local.gameLocal.entities[trace.c.entityNum] as idAFEntity_Base?
                if (af != null && af is idAFEntity_Base && af.IsActiveAF()) {
                    val bodyId = af.BodyForClipModelId(trace.c.id)
                    val body = af.GetAFPhysics().GetBody(bodyId)
                    if (body != null) {
                        ReturnString(body.GetName())
                        return
                    }
                }
            }
            ReturnString("")
        }

        private fun Event_IsClient() {
            ReturnFloat(btoi(Game_local.gameLocal.isClient).toFloat())
        }

        private fun Event_IsMultiplayer() {
            ReturnFloat(btoi(Game_local.gameLocal.isMultiplayer).toFloat())
        }

        private fun Event_GetFrameTime() {
            ReturnFloat(MS2SEC(idGameLocal.msec.toFloat()))
        }

        private fun Event_GetTicsPerSecond() {
            ReturnFloat(USERCMD_HZ.toFloat())
        }

        private fun Event_InfluenceActive() {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            ReturnInt(player != null && player.GetInfluenceLevel() != 0)
        }

        // virtual						~idThread();
        override fun _deconstructor() {
            var thread: idThread
            var i: Int
            val n: Int
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: end thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
            threadList.Remove(this)
            n = threadList.Num()
            i = 0
            while (i < n) {
                thread = threadList[i]
                if (thread.WaitingOnThread() === this) {
                    thread.ThreadCallback(this)
                }
                i++
            }
            if (currentThread === this) {
                currentThread = null
            }
            super._deconstructor()
        }

        // tells the thread manager not to delete this thread when it ends
        fun ManualDelete() {
            interpreter.terminateOnExit = false
        }

        // save games
        override fun Save(savefile: idSaveGame) {                // archives object for save game file

            // We will check on restore that threadNum is still the same,
            // threads should have been restored in the same order.
            savefile.WriteInt(threadNum)
            savefile.WriteObject(waitingForThread!!)
            savefile.WriteInt(waitingFor)
            savefile.WriteInt(waitingUntil)
            interpreter.Save(savefile)
            savefile.WriteDict(spawnArgs)
            savefile.WriteString(threadName)
            savefile.WriteInt(lastExecuteTime)
            savefile.WriteInt(creationTime)
            savefile.WriteBool(manualControl)
        }

        override fun Restore(savefile: idRestoreGame) {                // unarchives object from save game file
            threadNum = savefile.ReadInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/waitingForThread)
            waitingFor = savefile.ReadInt()
            waitingUntil = savefile.ReadInt()
            interpreter.Restore(savefile)
            savefile.ReadDict(spawnArgs!!)
            savefile.ReadString(threadName)
            lastExecuteTime = savefile.ReadInt()
            creationTime = savefile.ReadInt()
            manualControl = savefile.ReadBool()
        }

        fun EnableDebugInfo() {
            interpreter.debug = true
        }

        fun DisableDebugInfo() {
            interpreter.debug = false
        }

        fun WaitMS(time: Int) {
            Pause()
            waitingUntil = Game_local.gameLocal.time + time
        }

        fun WaitSec(time: Float) {
            WaitMS(SEC2MS(time).toInt())
        }

        fun WaitFrame() {
            Pause()

            // manual control threads don't set waitingUntil so that they can be run again
            // that frame if necessary.
            if (!manualControl) {
                waitingUntil = Game_local.gameLocal.time + idGameLocal.msec
            }
        }

        /*
         ================
         idThread::CallFunction

         NOTE: If this is called from within a event called by this thread, the function arguments will be invalid after calling this function.
         ================
         */
        fun CallFunction(func: function_t?, clearStack: Boolean) {
            ClearWaitFor()
            interpreter.EnterFunction(func, clearStack)
        }

        /*
         ================
         idThread::CallFunction

         NOTE: If this is called from within a event called by this thread, the function arguments will be invalid after calling this function.
         ================
         */
        fun CallFunction(self: idEntity?, func: function_t?, clearStack: Boolean) {
            assert(self != null)
            ClearWaitFor()
            interpreter.EnterObjectFunction(self, func, clearStack)
        }

        fun DisplayInfo() {
            Game_local.gameLocal.Printf(
                """%12i: '%s'
        File: %s(%d)
     Created: %d (%d ms ago)
      Status: """,
                threadNum, threadName,
                interpreter.CurrentFile(), interpreter.CurrentLine(),
                creationTime, Game_local.gameLocal.time - creationTime
            )
            if (interpreter.threadDying) {
                Game_local.gameLocal.Printf("Dying\n")
            } else if (interpreter.doneProcessing) {
                Game_local.gameLocal.Printf(
                    """Paused since %d (%d ms)
      Reason: """, lastExecuteTime, Game_local.gameLocal.time - lastExecuteTime
                )
                if (waitingForThread != null) {
                    Game_local.gameLocal.Printf(
                        "Waiting for thread #%3d '%s'\n",
                        waitingForThread!!.GetThreadNum(),
                        waitingForThread!!.GetThreadName()
                    )
                } else if (waitingFor != Game_local.ENTITYNUM_NONE && Game_local.gameLocal.entities[waitingFor] != null) {
                    Game_local.gameLocal.Printf(
                        "Waiting for entity #%3d '%s'\n",
                        waitingFor,
                        Game_local.gameLocal.entities[waitingFor]!!.name
                    )
                } else if (waitingUntil != 0) {
                    Game_local.gameLocal.Printf(
                        "Waiting until %d (%d ms total wait time)\n",
                        waitingUntil,
                        waitingUntil - lastExecuteTime
                    )
                } else {
                    Game_local.gameLocal.Printf("None\n")
                }
            } else {
                Game_local.gameLocal.Printf("Processing\n")
            }
            interpreter.DisplayInfo()
            Game_local.gameLocal.Printf("\n")
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun  /*idTypeInfo*/GetType(): Class<out idClass> {
            return javaClass
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        fun set(get: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun IsDoneProcessing(): Boolean {
            return interpreter.doneProcessing
        }

        fun IsDying(): Boolean {
            return interpreter.threadDying
        }

        fun End() {
            // Tell thread to die.  It will exit on its own.
            Pause()
            interpreter.threadDying = true
        }

        fun Execute(): Boolean {
//            return false;//HACKME::6
            val oldThread: idThread?
            val done: Boolean
            if (manualControl && waitingUntil > Game_local.gameLocal.time) {
                return false
            }
            oldThread = currentThread
            currentThread = this
            lastExecuteTime = Game_local.gameLocal.time
            ClearWaitFor()
            done = interpreter.Execute()
            if (done) {
                End()
                if (interpreter.terminateOnExit) {
                    PostEventMS(neo.Game.GameSys.Class.EV_Remove, 0)
                }
            } else if (!manualControl) {
                if (waitingUntil > lastExecuteTime) {
                    PostEventMS(EV_Thread_Execute, waitingUntil - lastExecuteTime)
                } else if (interpreter.MultiFrameEventInProgress()) {
                    PostEventMS(EV_Thread_Execute, idGameLocal.msec)
                }
            }
            currentThread = oldThread
            return done
        }

        fun ManualControl() {
            manualControl = true
            CancelEvents(EV_Thread_Execute)
        }

        fun DoneProcessing() {
            interpreter.doneProcessing = true
        }

        fun ContinueProcessing() {
            interpreter.doneProcessing = false
        }

        fun ThreadDying(): Boolean {
            return interpreter.threadDying
        }

        fun EndThread() {
            interpreter.threadDying = true
        }

        /*
         ================
         idThread::IsWaiting

         Checks if thread is still waiting for some event to occur.
         ================
         */
        fun IsWaiting(): Boolean {
            return if (waitingForThread != null || waitingFor != Game_local.ENTITYNUM_NONE) {
                true
            } else waitingUntil != 0 && waitingUntil > Game_local.gameLocal.time
        }

        fun ClearWaitFor() {
            waitingFor = Game_local.ENTITYNUM_NONE
            waitingForThread = null
            waitingUntil = 0
        }

        fun IsWaitingFor(obj: idEntity?): Boolean {
            assert(obj != null)
            return waitingFor == obj!!.entityNumber
        }

        fun ObjectMoveDone(obj: idEntity?) {
            assert(obj != null)
            if (IsWaitingFor(obj)) {
                ClearWaitFor()
                DelayedStart(0)
            }
        }

        fun ThreadCallback(thread: idThread) {
            if (interpreter.threadDying) {
                return
            }
            if (thread === waitingForThread) {
                ClearWaitFor()
                DelayedStart(0)
            }
        }

        fun DelayedStart(delay: Int) {
            var delay = delay
            CancelEvents(EV_Thread_Execute)
            if (Game_local.gameLocal.time <= 0) {
                delay++
            }
            PostEventMS(EV_Thread_Execute, delay)
        }

        fun Start(): Boolean {
            val result: Boolean
            CancelEvents(EV_Thread_Execute)
            result = Execute()
            return result
        }

        fun WaitingOnThread(): idThread? {
            return waitingForThread
        }

        fun SetThreadNum(num: Int) {
            threadNum = num
        }

        fun GetThreadNum(): Int {
            return threadNum
        }

        fun SetThreadName(name: String?) {
            threadName.set(name)
        }

        fun GetThreadName(): String {
            return threadName.toString()
        }

        fun Error(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt!!, *objects)
            interpreter.Error(text)
        }

        fun Warning(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt!!, *objects)
            interpreter.Warning(text)
        }

        override fun oSet(oGet: idClass?) {}

        /*
         ================
         idThread::ListThreads_f
         ================
         */
        class ListThreads_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                var i: Int
                val n: Int
                n = threadList.Num()
                i = 0
                while (i < n) {

                    //threadList[ i ].DisplayInfo();
                    Game_local.gameLocal.Printf(
                        "%3d: %-20s : %s(%d)\n",
                        threadList[i].threadNum,
                        threadList[i].threadName,
                        threadList[i].interpreter.CurrentFile(),
                        threadList[i].interpreter.CurrentLine()
                    )
                    i++
                }
                Game_local.gameLocal.Printf("%d active threads\n\n", n)
            }

            companion object {
                val instance: cmdFunction_t = ListThreads_f()
            }
        }

        companion object {
            const val BYTES = Integer.BYTES * 14 //TODO
            protected var eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //        // CLASS_PROTOTYPE( idThread );
            //        public static final idTypeInfo Type = new idTypeInfo(null, null, eventCallbacks, null, null, null, null);
            //
            //
            private var currentThread: idThread? = null

            //
            private var threadIndex = 0
            private val threadList = idList<idThread>()

            //
            private var trace = trace_s()

            init {
                eventCallbacks.putAll(getEventCallBacks())
                eventCallbacks.putAll(getEventCallBacks())
                //            eventCallbacks.put(EV_Thread_Execute, (eventCallback_t0<idThread>) idThread::Event_Execute);
//            eventCallbacks.put(EV_Thread_TerminateThread, (eventCallback_t1<idThread>)  (idEventArg<?>) idThread::Event_TerminateThread);
//            eventCallbacks.put(EV_Thread_Pause, (eventCallback_t0<idThread>) idThread::Event_Pause);
//            eventCallbacks.put(EV_Thread_Wait, (eventCallback_t1<idThread>) idThread::Event_Wait);
//            eventCallbacks.put(EV_Thread_WaitFrame, (eventCallback_t0<idThread>) idThread::Event_WaitFrame);
//            eventCallbacks.put(EV_Thread_WaitFor, (eventCallback_t1<idThread>) idThread::Event_WaitFor);
//            eventCallbacks.put(EV_Thread_WaitForThread, (eventCallback_t1<idThread>) idThread::Event_WaitForThread);
//            eventCallbacks.put(EV_Thread_Print, (eventCallback_t1<idThread>) idThread::Event_Print);
//            eventCallbacks.put(EV_Thread_PrintLn, (eventCallback_t1<idThread>) idThread::Event_PrintLn);
//            eventCallbacks.put(EV_Thread_Say, (eventCallback_t1<idThread>) idThread::Event_Say);
//            eventCallbacks.put(EV_Thread_Assert, (eventCallback_t1<idThread>) idThread::Event_Assert);
//            eventCallbacks.put(EV_Thread_Trigger, (eventCallback_t1<idThread>) idThread::Event_Trigger);
//            eventCallbacks.put(EV_Thread_SetCvar, (eventCallback_t2<idThread>) idThread::Event_SetCvar);
//            eventCallbacks.put(EV_Thread_GetCvar, (eventCallback_t1<idThread>) idThread::Event_GetCvar);
//            eventCallbacks.put(EV_Thread_Random, (eventCallback_t1<idThread>) idThread::Event_Random);
//            eventCallbacks.put(EV_Thread_GetTime, (eventCallback_t0<idThread>) idThread::Event_GetTime);
//            eventCallbacks.put(EV_Thread_KillThread, (eventCallback_t1<idThread>) idThread::Event_KillThread);
//            eventCallbacks.put(EV_Thread_SetThreadName, (eventCallback_t1<idThread>) idThread::Event_SetThreadName);
//            eventCallbacks.put(EV_Thread_GetEntity, (eventCallback_t1<idThread>) idThread::Event_GetEntity);
//            eventCallbacks.put(EV_Thread_Spawn, (eventCallback_t1<idThread>) idThread::Event_Spawn);
//            eventCallbacks.put(EV_Thread_CopySpawnArgs, (eventCallback_t1<idThread>) idThread::Event_CopySpawnArgs);
//            eventCallbacks.put(EV_Thread_SetSpawnArg, (eventCallback_t2<idThread>) idThread::Event_SetSpawnArg);
//            eventCallbacks.put(EV_Thread_SpawnString, (eventCallback_t2<idThread>) idThread::Event_SpawnString);
//            eventCallbacks.put(EV_Thread_SpawnFloat, (eventCallback_t2<idThread>) idThread::Event_SpawnFloat);
//            eventCallbacks.put(EV_Thread_SpawnVector, (eventCallback_t2<idThread>) idThread::Event_SpawnVector);
//            eventCallbacks.put(EV_Thread_ClearPersistantArgs, (eventCallback_t0<idThread>) idThread::Event_ClearPersistantArgs);
//            eventCallbacks.put(EV_Thread_SetPersistantArg, (eventCallback_t2<idThread>) idThread::Event_SetPersistantArg);
//            eventCallbacks.put(EV_Thread_GetPersistantString, (eventCallback_t1<idThread>) idThread::Event_GetPersistantString);
//            eventCallbacks.put(EV_Thread_GetPersistantFloat, (eventCallback_t1<idThread>) idThread::Event_GetPersistantFloat);
//            eventCallbacks.put(EV_Thread_GetPersistantVector, (eventCallback_t1<idThread>) idThread::Event_GetPersistantVector);
//            eventCallbacks.put(EV_Thread_AngToForward, (eventCallback_t1<idThread>) idThread::Event_AngToForward);
//            eventCallbacks.put(EV_Thread_AngToRight, (eventCallback_t1<idThread>) idThread::Event_AngToRight);
//            eventCallbacks.put(EV_Thread_AngToUp, (eventCallback_t1<idThread>) idThread::Event_AngToUp);
//            eventCallbacks.put(EV_Thread_Sine, (eventCallback_t1<idThread>) idThread::Event_GetSine);
//            eventCallbacks.put(EV_Thread_Cosine, (eventCallback_t1<idThread>) idThread::Event_GetCosine);
//            eventCallbacks.put(EV_Thread_SquareRoot, (eventCallback_t1<idThread>) idThread::Event_GetSquareRoot);
//            eventCallbacks.put(EV_Thread_Normalize, (eventCallback_t1<idThread>) idThread::Event_VecNormalize);
//            eventCallbacks.put(EV_Thread_VecLength, (eventCallback_t1<idThread>) idThread::Event_VecLength);
//            eventCallbacks.put(EV_Thread_VecDotProduct, (eventCallback_t2<idThread>) idThread::Event_VecDotProduct);
//            eventCallbacks.put(EV_Thread_VecCrossProduct, (eventCallback_t2<idThread>) idThread::Event_VecCrossProduct);
//            eventCallbacks.put(EV_Thread_VecToAngles, (eventCallback_t1<idThread>) idThread::Event_VecToAngles);
//            eventCallbacks.put(EV_Thread_OnSignal, (eventCallback_t3<idThread>) idThread::Event_OnSignal);
//            eventCallbacks.put(EV_Thread_ClearSignal, (eventCallback_t2<idThread>) idThread::Event_ClearSignalThread);
//            eventCallbacks.put(EV_Thread_SetCamera, (eventCallback_t1<idThread>) idThread::Event_SetCamera);
//            eventCallbacks.put(EV_Thread_FirstPerson, (eventCallback_t0<idThread>) idThread::Event_FirstPerson);
//            eventCallbacks.put(EV_Thread_Trace, (eventCallback_t6<idThread>) idThread::Event_Trace);
//            eventCallbacks.put(EV_Thread_TracePoint, (eventCallback_t4<idThread>) idThread::Event_TracePoint);
//            eventCallbacks.put(EV_Thread_GetTraceFraction, (eventCallback_t0<idThread>) idThread::Event_GetTraceFraction);
//            eventCallbacks.put(EV_Thread_GetTraceEndPos, (eventCallback_t0<idThread>) idThread::Event_GetTraceEndPos);
//            eventCallbacks.put(EV_Thread_GetTraceNormal, (eventCallback_t0<idThread>) idThread::Event_GetTraceNormal);
//            eventCallbacks.put(EV_Thread_GetTraceEntity, (eventCallback_t0<idThread>) idThread::Event_GetTraceEntity);
//            eventCallbacks.put(EV_Thread_GetTraceJoint, (eventCallback_t0<idThread>) idThread::Event_GetTraceJoint);
//            eventCallbacks.put(EV_Thread_GetTraceBody, (eventCallback_t0<idThread>) idThread::Event_GetTraceBody);
//            eventCallbacks.put(EV_Thread_FadeIn, (eventCallback_t2<idThread>) idThread::Event_FadeIn);
//            eventCallbacks.put(EV_Thread_FadeOut, (eventCallback_t2<idThread>) idThread::Event_FadeOut);
//            eventCallbacks.put(EV_Thread_FadeTo, (eventCallback_t3<idThread>) idThread::Event_FadeTo);
//            eventCallbacks.put(Entity.INSTANCE.getEV_SetShaderParm(), (eventCallback_t2<idThread>) idThread::Event_SetShaderParm);
//            eventCallbacks.put(EV_Thread_StartMusic, (eventCallback_t1<idThread>) idThread::Event_StartMusic);
//            eventCallbacks.put(EV_Thread_Warning, (eventCallback_t1<idThread>) idThread::Event_Warning);
//            eventCallbacks.put(EV_Thread_Error, (eventCallback_t1<idThread>) idThread::Event_Error);
//            eventCallbacks.put(EV_Thread_StrLen, (eventCallback_t1<idThread>) idThread::Event_StrLen);
//            eventCallbacks.put(EV_Thread_StrLeft, (eventCallback_t2<idThread>) idThread::Event_StrLeft);
//            eventCallbacks.put(EV_Thread_StrRight, (eventCallback_t2<idThread>) idThread::Event_StrRight);
//            eventCallbacks.put(EV_Thread_StrSkip, (eventCallback_t2<idThread>) idThread::Event_StrSkip);
//            eventCallbacks.put(EV_Thread_StrMid, (eventCallback_t3<idThread>) idThread::Event_StrMid);
//            eventCallbacks.put(EV_Thread_StrToFloat, (eventCallback_t1<idThread>) idThread::Event_StrToFloat);
//            eventCallbacks.put(EV_Thread_RadiusDamage, (eventCallback_t6<idThread>) idThread::Event_RadiusDamage);
//            eventCallbacks.put(EV_Thread_IsClient, (eventCallback_t0<idThread>) idThread::Event_IsClient);
//            eventCallbacks.put(EV_Thread_IsMultiplayer, (eventCallback_t0<idThread>) idThread::Event_IsMultiplayer);
//            eventCallbacks.put(EV_Thread_GetFrameTime, (eventCallback_t0<idThread>) idThread::Event_GetFrameTime);
//            eventCallbacks.put(EV_Thread_GetTicsPerSecond, (eventCallback_t0<idThread>) idThread::Event_GetTicsPerSecond);
//            eventCallbacks.put(Entity.INSTANCE.getEV_CacheSoundShader(), (eventCallback_t1<idThread>) idThread::Event_CacheSoundShader);
//            eventCallbacks.put(EV_Thread_DebugLine, (eventCallback_t4<idThread>) idThread::Event_DebugLine);
//            eventCallbacks.put(EV_Thread_DebugArrow, (eventCallback_t5<idThread>) idThread::Event_DebugArrow);
//            eventCallbacks.put(EV_Thread_DebugCircle, (eventCallback_t6<idThread>) idThread::Event_DebugCircle);
//            eventCallbacks.put(EV_Thread_DebugBounds, (eventCallback_t4<idThread>) idThread::Event_DebugBounds);
//            eventCallbacks.put(EV_Thread_DrawText, (eventCallback_t6<idThread>) idThread::Event_DrawText);
//            eventCallbacks.put(EV_Thread_InfluenceActive, (eventCallback_t0<idThread>) idThread::Event_InfluenceActive);
            }

            private fun Event_SetThreadName(t: idThread, name: idEventArg<String>) {
                t.SetThreadName(name.value)
            }

            //
            // script callable Events
            //
            private fun Event_TerminateThread(t: idThread, num: idEventArg<Int>) {
                val thread: idThread?
                thread = GetThread(num.value)
                KillThread(num.value)
            }

            private fun Event_Wait(t: idThread, time: idEventArg<Float>) {
                t.WaitSec(time.value)
            }

            private fun Event_WaitFor(t: idThread, e: idEventArg<idEntity>) {
                val ent = e.value
                if (ent != null && ent.RespondsTo(EV_Thread_SetCallback)) {
                    ent.ProcessEvent(EV_Thread_SetCallback)
                    if (Game_local.gameLocal.program.GetReturnedInteger() != 0) {
                        t.Pause()
                        t.waitingFor = ent.entityNumber
                    }
                }
            }

            private fun Event_WaitForThread(t: idThread, num: idEventArg<Int>) {
                val thread: idThread?
                thread = GetThread(num.value)
                if (null == thread) {
                    if (SysCvar.g_debugScript.GetBool()) {
                        // just print a warning and continue executing
                        t.Warning("Thread %d not running", num.value)
                    }
                } else {
                    t.Pause()
                    t.waitingForThread = thread
                }
            }

            private fun Event_Print(t: idThread, text: idEventArg<String>) {
                Game_local.gameLocal.Printf("%s", text.value)
            }

            private fun Event_PrintLn(t: idThread, text: idEventArg<String>) {
                Game_local.gameLocal.Printf("%s\n", text.value)
            }

            private fun Event_Say(t: idThread, text: idEventArg<String>) {
                cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, String.format("say \"%s\"", text.value))
            }

            private fun Event_Assert(t: idThread, value: idEventArg<Float>) {
                assert(value.value != 0f)
            }

            //
            //        private static void Event_Trigger(idThread t, idEventArg<idEntity> e) {
            //            idEntity ent = e.getValue();
            //            if (ent != null) {
            //                ent.Signal(SIG_TRIGGER);
            //                ent.ProcessEvent(EV_Activate, gameLocal.GetLocalPlayer());
            //                ent.TriggerGuis();
            //            }
            //        }
            private fun Event_SetCvar(t: idThread, name: idEventArg<String>, value: idEventArg<String>) {
                cvarSystem.SetCVarString(name.value, value.value)
            }

            private fun Event_GetCvar(t: idThread, name: idEventArg<String>) {
                ReturnString(cvarSystem.GetCVarString(name.value))
            }

            private fun Event_Random(t: idThread, range: idEventArg<Float>) {
                val result: Float
                result = Game_local.gameLocal.random.RandomFloat()
                ReturnFloat(range.value * result)
            }

            private fun Event_KillThread(t: idThread, name: idEventArg<String>) {
                KillThread(name.value)
            }

            private fun Event_GetEntity(t: idThread, n: idEventArg<String>) {
                val entnum: Int
                val ent: idEntity?
                val name = n.value
                if (name.startsWith("*")) {
                    entnum = name.substring(1).toInt()
                    if (entnum < 0 || entnum >= Game_local.MAX_GENTITIES) {
                        t.Error("Entity number in string out of range.")
                    }
                    ReturnEntity(Game_local.gameLocal.entities[entnum])
                } else {
                    ent = Game_local.gameLocal.FindEntity(name)
                    ReturnEntity(ent)
                }
            }

            //
            //        private static void Event_Spawn(idThread t, final idEventArg<String> classname) {
            //            idEntity[] ent = {null};
            //
            //            t.spawnArgs.Set("classname", classname.getValue());
            //            gameLocal.SpawnEntityDef(t.spawnArgs, ent);
            //            ReturnEntity(ent[0]);
            //            t.spawnArgs.Clear();
            //        }
            private fun Event_CopySpawnArgs(t: idThread, ent: idEventArg<idEntity>) {
                t.spawnArgs!!.Copy(ent.value.spawnArgs)
            }

            private fun Event_SetSpawnArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                t.spawnArgs!!.Set(key.value, value.value)
            }

            private fun Event_SpawnString(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<String>) {
                val result = arrayOf<String>()
                t.spawnArgs!!.GetString(key.value, defaultvalue.value, result)
                ReturnString(result[0])
            }

            private fun Event_SpawnFloat(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<Float>) {
                val result = CFloat()
                t.spawnArgs!!.GetFloat(key.value, String.format("%f", defaultvalue.value), result)
                ReturnFloat(result._val)
            }

            //
            //        private static void Event_SpawnVector(idThread t, final idEventArg<String> key, final idEventArg<idVec3> d) {
            //            final idVec3 result = new idVec3();
            //            final idVec3 defaultvalue = new idVec3(d.getValue());
            //
            //            t.spawnArgs.GetVector(key.getValue(), String.format("%f %f %f", defaultvalue.x, defaultvalue.y, defaultvalue.z), result);
            //            ReturnVector(result);
            //        }
            private fun Event_SetPersistantArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                Game_local.gameLocal.persistentLevelInfo.Set(key.value, value.value)
            }

            private fun Event_GetPersistantString(t: idThread, key: idEventArg<String>) {
                val result = arrayOf<String>()
                Game_local.gameLocal.persistentLevelInfo.GetString(key.value, "", result)
                ReturnString(result[0])
            }

            private fun Event_GetPersistantFloat(t: idThread, key: idEventArg<String>) {
                val result = CFloat()
                Game_local.gameLocal.persistentLevelInfo.GetFloat(key.value, "0", result)
                ReturnFloat(result._val)
            }

            private fun Event_GetPersistantVector(t: idThread, key: idEventArg<String>) {
                val result = idVec3()
                Game_local.gameLocal.persistentLevelInfo.GetVector(key.value, "0 0 0", result)
                ReturnVector(result)
            }

            private fun Event_AngToForward(t: idThread, ang: idEventArg<idVec3>) {
                ReturnVector(idAngles(ang.value).ToForward())
            }

            private fun Event_AngToRight(t: idThread, ang: idEventArg<idAngles>) {
                val vec = idVec3()
                ang.value.ToVectors(getVec3_zero(), vec)
                ReturnVector(vec)
            }

            //        private static void Event_AngToForward(idThread t, idEventArg<idAngles> ang) {
            //            ReturnVector(ang.getValue().ToForward());
            //        }
            //
            //        private static void Event_AngToUp(idThread t, idEventArg<idAngles> ang) {
            //            final idVec3 vec = new idVec3();
            //
            //            ang.getValue().ToVectors(null, null, vec);
            //            ReturnVector(vec);
            //        }
            //
            //        private static void Event_GetSine(idThread t, idEventArg<Float> angle) {
            //            ReturnFloat(idMath.Sin(DEG2RAD(angle.getValue())));
            //        }
            //
            //        private static void Event_GetCosine(idThread t, idEventArg<Float> angle) {
            //            ReturnFloat(idMath.Cos(DEG2RAD(angle.getValue())));
            //        }
            //
            //        private static void Event_GetSquareRoot(idThread t, idEventArg<Float> theSquare) {
            //            ReturnFloat(idMath.Sqrt(theSquare.getValue()));
            //        }
            //
            //        private static void Event_VecNormalize(idThread t, final idEventArg<idVec3> vec) {
            //            final idVec3 n = new idVec3(vec.getValue());
            //            n.Normalize();
            //            ReturnVector(n);
            //        }
            //
            //        private static void Event_VecLength(idThread t, final idEventArg<idVec3> vec) {
            //            ReturnFloat(vec.getValue().Length());
            //        }
            //
            //        private static void Event_VecDotProduct(idThread t, final idEventArg<idVec3> vec1, idEventArg<idVec3> vec2) {
            //            ReturnFloat(vec1.getValue().oMultiply(vec2.getValue()));
            //        }
            //
            //        private static void Event_VecCrossProduct(idThread t, final idEventArg<idVec3> vec1, final idEventArg<idVec3> vec2) {
            //            ReturnVector(vec1.getValue().Cross(vec2.getValue()));
            //        }
            //
            //        private static void Event_VecToAngles(idThread t, final idEventArg<idVec3> vec) {
            //            idAngles ang = vec.getValue().ToAngles();
            //            ReturnVector(new idVec3(ang.get(0), ang.get(1), ang.get(2)));
            //        }
            //
            //        private static void Event_OnSignal(idThread t, idEventArg<Integer> s, idEventArg<idEntity> e, final idEventArg<String> f) {
            //            function_t function;
            //            int signal = s.getValue();
            //            idEntity ent = e.getValue();
            //            String func = f.getValue();
            //
            //            assert (func != null);
            //
            //            if (null == ent) {
            //                t.Error("Entity not found");
            //            }
            //
            //            if ((signal < 0) || (signal >= etoi(NUM_SIGNALS))) {
            //                t.Error("Signal out of range");
            //            }
            //
            //            function = gameLocal.program.FindFunction(func);
            //            if (null == function) {
            //                t.Error("Function '%s' not found", func);
            //            }
            //
            //            ent.SetSignal(signal, t, function);
            //        }
            //
            //        private static void Event_ClearSignalThread(idThread t, idEventArg<Integer> s, idEventArg<idEntity> e) {
            //            int signal = s.getValue();
            //            idEntity ent = e.getValue();
            //
            //            if (null == ent) {
            //                t.Error("Entity not found");
            //            }
            //
            //            if ((signal < 0) || (signal >= etoi(NUM_SIGNALS))) {
            //                t.Error("Signal out of range");
            //            }
            //
            //            ent.ClearSignalThread(signal, t);
            //        }
            //
            //        private static void Event_SetCamera(idThread t, idEventArg<idEntity> e) {
            //            idEntity ent = e.getValue();
            //
            //            if (null == ent) {
            //                t.Error("Entity not found");
            //                return;
            //            }
            //
            //            if (!(ent instanceof idCamera)) {
            //                t.Error("Entity is not a camera");
            //                return;
            //            }
            //
            //            gameLocal.SetCamera((idCamera) ent);
            //        }
            //
            //        private static void Event_Trace(idThread t, final idEventArg<idVec3> s, final idEventArg<idVec3> e, final idEventArg<idVec3> mi,
            //                                        final idEventArg<idVec3> ma, idEventArg<Integer> c, idEventArg<idEntity> p) {
            //            final idVec3 start = new idVec3(s.getValue());
            //            final idVec3 end = new idVec3(e.getValue());
            //            final idVec3 mins = new idVec3(mi.getValue());
            //            final idVec3 maxs = new idVec3(ma.getValue());
            //            int contents_mask = c.getValue();
            //            idEntity passEntity = p.getValue();
            //
            //            {
            //                trace_s trace = idThread.trace;
            //                if (mins.equals(getVec3_origin()) && maxs.equals(getVec3_origin())) {
            //                    gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity);
            //                } else {
            //                    gameLocal.clip.TraceBounds(trace, start, end, new idBounds(mins, maxs), contents_mask, passEntity);
            //                }
            //                idThread.trace = trace;
            //            }
            //            ReturnFloat(trace.fraction);
            //        }
            //
            //        private static void Event_TracePoint(idThread t, final idEventArg<idVec3> startA, final idEventArg<idVec3> endA, idEventArg<Integer> c, idEventArg<idEntity> p) {
            //            final idVec3 start = new idVec3(startA.getValue());
            //            final idVec3 end = new idVec3(endA.getValue());
            //            int contents_mask = c.getValue();
            //            idEntity passEntity = p.getValue();
            //            {
            //                trace_s trace = idThread.trace;
            //                gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity);
            //                idThread.trace = trace;
            //            }
            //            ReturnFloat(trace.fraction);
            //        }
            //
            //        private static void Event_FadeIn(idThread t, idEventArg<idVec3> colorA, idEventArg<Float> time) {
            //            idVec4 fadeColor = new idVec4();
            //            idPlayer player;
            //            final idVec3 color = new idVec3(colorA.getValue());
            //
            //            player = gameLocal.GetLocalPlayer();
            //            if (player != null) {
            //                fadeColor.Set(color.get(0), color.get(1), color.get(2), 0.0f);
            //                player.playerView.Fade(fadeColor, (int) SEC2MS(time.getValue()));
            //            }
            //        }
            //
            //        private static void Event_FadeOut(idThread t, final idEventArg<idVec3> colorA, idEventArg<Float> time) {
            //            idVec4 fadeColor = new idVec4();
            //            idPlayer player;
            //            final idVec3 color = new idVec3(colorA.getValue());
            //
            //            player = gameLocal.GetLocalPlayer();
            //            if (player != null) {
            //                fadeColor.Set(color.get(0), color.get(1), color.get(2), 1.0f);
            //                player.playerView.Fade(fadeColor, (int) SEC2MS(time.getValue()));
            //            }
            //        }
            //
            //        private static void Event_FadeTo(idThread t, idEventArg<idVec3> colorA, idEventArg<Float> alpha, idEventArg<Float> time) {
            //            idVec4 fadeColor = new idVec4();
            //            idPlayer player;
            //            final idVec3 color = new idVec3(colorA.getValue());
            //
            //            player = gameLocal.GetLocalPlayer();
            //            if (player != null) {
            //                fadeColor.Set(color.get(0), color.get(1), color.get(2), alpha.getValue());
            //                player.playerView.Fade(fadeColor, (int) SEC2MS(time.getValue()));
            //            }
            //        }
            //
            //        private static void Event_SetShaderParm(idThread t, idEventArg<Integer> parmnumA, idEventArg<Float> value) {
            //            int parmnum = parmnumA.getValue();
            //
            //            if ((parmnum < 0) || (parmnum >= MAX_GLOBAL_SHADER_PARMS)) {
            //                t.Error("shader parm index (%d) out of range", parmnum);
            //            }
            //
            //            gameLocal.globalShaderParms[parmnum] = value.getValue();
            //        }
            //
            //        private static void Event_StartMusic(idThread t, final idEventArg<String> text) {
            //            gameSoundWorld.PlayShaderDirectly(text.getValue());
            //        }
            //
            //        private static void Event_Warning(idThread t, final idEventArg<String> text) {
            //            t.Warning("%s", text.getValue());
            //        }
            //
            //        private static void Event_Error(idThread t, final idEventArg<String> text) {
            //            t.Error("%s", text.getValue());
            //        }
            //
            //        private static void Event_StrLen(idThread t, final idEventArg<String> string) {
            //            int len;
            //
            //            len = string.getValue().length();
            //            idThread.ReturnInt(len);
            //        }
            //
            //        private static void Event_StrLeft(idThread t, final idEventArg<String> stringA, idEventArg<Integer> numA) {
            //            int len;
            //            String string = stringA.getValue();
            //            int num = numA.getValue();
            //
            //            if (num < 0) {
            //                idThread.ReturnString("");
            //                return;
            //            }
            //
            //            len = string.length();
            //            if (len < num) {
            //                idThread.ReturnString(string);
            //                return;
            //            }
            //
            //            idStr result = new idStr(string, 0, num);
            //            idThread.ReturnString(result);
            //        }
            //
            //        private static void Event_StrRight(idThread t, final idEventArg<String> stringA, idEventArg<Integer> numA) {
            //            int len;
            //            String string = stringA.getValue();
            //            int num = numA.getValue();
            //
            //            if (num < 0) {
            //                idThread.ReturnString("");
            //                return;
            //            }
            //
            //            len = string.length();
            //            if (len < num) {
            //                idThread.ReturnString(string);
            //                return;
            //            }
            //
            //            idThread.ReturnString(string + (len - num));
            //        }
            //
            //        private static void Event_StrSkip(idThread t, final idEventArg<String> stringA, idEventArg<Integer> numA) {
            //            int len;
            //            String string = stringA.getValue();
            //            int num = numA.getValue();
            //
            //            if (num < 0) {
            //                idThread.ReturnString(string);
            //                return;
            //            }
            //
            //            len = string.length();
            //            if (len < num) {
            //                idThread.ReturnString("");
            //                return;
            //            }
            //
            //            idThread.ReturnString(string + num);
            //        }
            private fun Event_StrMid(
                t: idThread,
                stringA: idEventArg<String>,
                startA: idEventArg<Int>,
                numA: idEventArg<Int>
            ) {
                val len: Int
                val string = stringA.value
                var start = startA.value
                var num = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                if (start < 0) {
                    start = 0
                }
                len = string.length
                if (start > len) {
                    start = len
                }
                if (start + num > len) {
                    num = len - start
                }
                val result = idStr(string, start, start + num)
                ReturnString(result)
            }

            private fun Event_StrToFloat(t: idThread, string: idEventArg<String>) {
                val result: Float
                result = string.value.toFloat()
                ReturnFloat(result)
            }

            private fun Event_RadiusDamage(
                t: idThread,
                origin: idEventArg<idVec3>,
                inflictor: idEventArg<idEntity>,
                attacker: idEventArg<idEntity>,
                ignore: idEventArg<idEntity>,
                damageDefName: idEventArg<String>,
                dmgPower: idEventArg<Float>
            ) {
                Game_local.gameLocal.RadiusDamage(
                    origin.value,
                    inflictor.value,
                    attacker.value,
                    ignore.value,
                    ignore.value,
                    damageDefName.value,
                    dmgPower.value
                )
            }

            private fun Event_CacheSoundShader(t: idThread, soundName: idEventArg<String>) {
                DeclManager.declManager.FindSound(soundName.value)
            }

            //
            //        private static void Event_DebugLine(idThread t, final idEventArg<idVec3> colorA, final idEventArg<idVec3> start, final idEventArg<idVec3> end, final idEventArg<Float> lifetime) {
            //            final idVec3 color = new idVec3(colorA.getValue());
            //            Game_local.gameRenderWorld.DebugLine(new idVec4(color.x, color.y, color.z, 0.0f), start.getValue(), end.getValue(), (int) SEC2MS(lifetime.getValue()));
            //        }
            //
            //        private static void Event_DebugArrow(idThread t, final idEventArg<idVec3> colorA, final idEventArg<idVec3> start, final idEventArg<idVec3> end, final idEventArg<Integer> size, final idEventArg<Float> lifetime) {
            //            final idVec3 color = new idVec3(colorA.getValue());
            //            gameRenderWorld.DebugArrow(new idVec4(color.x, color.y, color.z, 0.0f), start.getValue(), end.getValue(), size.getValue(), (int) SEC2MS(lifetime.getValue()));
            //        }
            //
            //        private static void Event_DebugCircle(idThread t, final idEventArg<idVec3> colorA, final idEventArg<idVec3> origin, final idEventArg<idVec3> dir, final idEventArg<Float> radius, final idEventArg<Integer> numSteps, final idEventArg<Float> lifetime) {
            //            final idVec3 color = new idVec3(colorA.getValue());
            //            gameRenderWorld.DebugCircle(new idVec4(color.x, color.y, color.z, 0.0f), origin.getValue(), dir.getValue(), radius.getValue(), numSteps.getValue(), (int) SEC2MS(lifetime.getValue()));
            //        }
            //
            //        private static void Event_DebugBounds(idThread t, final idEventArg<idVec3> colorA, final idEventArg<idVec3> mins, final idEventArg<idVec3> maxs, final idEventArg<Float> lifetime) {
            //            final idVec3 color = new idVec3(colorA.getValue());
            //            gameRenderWorld.DebugBounds(new idVec4(color.x, color.y, color.z, 0.0f), new idBounds(mins.getValue(), maxs.getValue()), getVec3_origin(), (int) SEC2MS(lifetime.getValue()));
            //        }
            //
            //        private static void Event_DrawText(idThread t, final idEventArg<String> text, final idEventArg<idVec3> origin, idEventArg<Float> scale, final idEventArg<idVec3> colorA, final idEventArg<Integer> align, final idEventArg<Float> lifetime) {
            //            final idVec3 color = new idVec3(colorA.getValue());
            //            gameRenderWorld.DrawText(text.getValue(), origin.getValue(), scale.getValue(), new idVec4(color.x, color.y, color.z, 0.0f), gameLocal.GetLocalPlayer().getViewAngles().ToMat3(), align.getValue(), (int) Math_h.INSTANCE.SEC2MS(lifetime.getValue()));
            //        }
            fun GetThread(num: Int): idThread? {
                var i: Int
                val n: Int
                var thread: idThread
                n = threadList.Num()
                i = 0
                while (i < n) {
                    thread = threadList[i]
                    if (thread.GetThreadNum() == num) {
                        return thread
                    }
                    i++
                }
                return null
            }

            val eventCallBacks: Map<idEventDef, eventCallback_t<*>>
                get() = eventCallbacks

            fun Restart() {
                var i: Int
                val n: Int

                // reset the threadIndex
                threadIndex = 0
                currentThread = null
                n = threadList.Num()
                //	for( i = n - 1; i >= 0; i-- ) {
//		delete threadList[ i ];
//	}
                threadList.Clear()

//	memset( &trace, 0, sizeof( trace ) );
                trace = trace_s()
                trace.c.entityNum = Game_local.ENTITYNUM_NONE
            }

            fun ObjectMoveDone(threadnum: Int, obj: idEntity?) {
                val thread: idThread?
                if (0 == threadnum) {
                    return
                }
                thread = GetThread(threadnum)
                thread?.ObjectMoveDone(obj)
            }

            fun GetThreads(): idList<idThread> {
                return threadList
            }

            fun KillThread(name: String) {
                var i: Int
                val num: Int
                val len: Int
                val ptr: Int
                var thread: idThread

                // see if the name uses a wild card
                ptr = name.indexOf('*')
                len = if (ptr != -1) {
                    ptr - name.length //TODO:double check this puhlease!
                } else {
                    name.length
                }

                // kill only those threads whose name matches name
                num = threadList.Num()
                i = 0
                while (i < num) {
                    thread = threadList[i]
                    if (0 == Cmpn(thread.GetThreadName(), name, len)) {
                        thread.End()
                    }
                    i++
                }
            }

            fun KillThread(num: Int) {
                val thread: idThread?
                thread = GetThread(num)
                thread?.End()
            }

            fun CurrentThread(): idThread? {
                return currentThread
            }

            fun CurrentThreadNum(): Int {
                return if (currentThread != null) {
                    currentThread!!.GetThreadNum()
                } else {
                    0
                }
            }

            fun BeginMultiFrameEvent(ent: idEntity, event: idEventDef?): Boolean {
                if (null == currentThread) {
                    Error("idThread::BeginMultiFrameEvent called without a current thread")
                }
                return currentThread!!.interpreter.BeginMultiFrameEvent(ent, event)
            }

            fun EndMultiFrameEvent(ent: idEntity?, event: idEventDef?) {
                if (null == currentThread) {
                    Error("idThread::EndMultiFrameEvent called without a current thread")
                }
                currentThread!!.interpreter.EndMultiFrameEvent(ent, event)
            }

            fun ReturnString(text: String?) {
                Game_local.gameLocal.program.ReturnString(text)
            }

            fun ReturnString(text: idStr) {
                ReturnString(text.toString())
            }

            fun ReturnFloat(value: Float) {
                Game_local.gameLocal.program.ReturnFloat(value)
            }

            fun ReturnInt(value: Int) {
                // true integers aren't supported in the compiler,
                // so int values are stored as floats
                Game_local.gameLocal.program.ReturnFloat(value.toFloat())
            }

            fun ReturnInt(value: Boolean) {
                ReturnInt(if (value) 1 else 0)
            }

            fun ReturnVector(vec: idVec3?) {
                Game_local.gameLocal.program.ReturnVector(vec)
            }

            fun ReturnEntity(ent: idEntity?) {
                Game_local.gameLocal.program.ReturnEntity(ent)
            }

            fun delete(thread: idThread) {
                thread._deconstructor()
            }
        }
    }
}