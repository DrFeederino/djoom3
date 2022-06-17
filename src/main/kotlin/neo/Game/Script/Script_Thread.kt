package neo.Game.Script

import neo.CM.CollisionModel.trace_s
import neo.Game.AFEntity.idAFEntity_Base
import neo.Game.Camera.idCamera
import neo.Game.Entity
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.eventCallback_t3
import neo.Game.GameSys.Class.eventCallback_t4
import neo.Game.GameSys.Class.eventCallback_t5
import neo.Game.GameSys.Class.eventCallback_t6
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Script.Script_Program.function_t
import neo.Renderer.RenderWorld
import neo.TempDump
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DeclManager
import neo.framework.UsercmdGen
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector
import neo.idlib.math.Vector.getVec3_zero
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object Script_Thread {
    val EV_Thread_SetCallback: idEventDef = idEventDef("<script_setcallback>", null)
    val EV_Thread_Wait: idEventDef = idEventDef("wait", "f")
    val EV_Thread_WaitFrame: idEventDef = idEventDef("waitFrame")
    val EV_Thread_AngToForward: idEventDef = idEventDef("angToForward", "v", 'v')
    val EV_Thread_AngToRight: idEventDef = idEventDef("angToRight", "v", 'v')
    val EV_Thread_AngToUp: idEventDef = idEventDef("angToUp", "v", 'v')
    val EV_Thread_Assert: idEventDef = idEventDef("assert", "f")
    val EV_Thread_ClearPersistantArgs: idEventDef = idEventDef("clearPersistantArgs")
    val EV_Thread_ClearSignal: idEventDef = idEventDef("clearSignalThread", "de")
    val EV_Thread_CopySpawnArgs: idEventDef = idEventDef("copySpawnArgs", "e")
    val EV_Thread_Cosine: idEventDef = idEventDef("cos", "f", 'f')
    val EV_Thread_DebugArrow: idEventDef = idEventDef("debugArrow", "vvvdf")
    val EV_Thread_DebugBounds: idEventDef = idEventDef("debugBounds", "vvvf")
    val EV_Thread_DebugCircle: idEventDef = idEventDef("debugCircle", "vvvfdf")
    val EV_Thread_DebugLine: idEventDef = idEventDef("debugLine", "vvvf")
    val EV_Thread_DrawText: idEventDef = idEventDef("drawText", "svfvdf")
    val EV_Thread_Error: idEventDef = idEventDef("error", "s")
    val EV_Thread_Execute: idEventDef = idEventDef("<execute>", null)
    val EV_Thread_FadeIn: idEventDef = idEventDef("fadeIn", "vf")
    val EV_Thread_FadeOut: idEventDef = idEventDef("fadeOut", "vf")
    val EV_Thread_FadeTo: idEventDef = idEventDef("fadeTo", "vff")
    val EV_Thread_FirstPerson: idEventDef = idEventDef("firstPerson", null)
    val EV_Thread_GetCvar: idEventDef = idEventDef("getcvar", "s", 's')
    val EV_Thread_GetEntity: idEventDef = idEventDef("getEntity", "s", 'e')
    val EV_Thread_GetFrameTime: idEventDef = idEventDef("getFrameTime", null, 'f')
    val EV_Thread_GetPersistantFloat: idEventDef = idEventDef("getPersistantFloat", "s", 'f')
    val EV_Thread_GetPersistantString: idEventDef = idEventDef("getPersistantString", "s", 's')
    val EV_Thread_GetPersistantVector: idEventDef = idEventDef("getPersistantVector", "s", 'v')
    val EV_Thread_GetTicsPerSecond: idEventDef = idEventDef("getTicsPerSecond", null, 'f')
    val EV_Thread_GetTime: idEventDef = idEventDef("getTime", null, 'f')
    val EV_Thread_GetTraceBody: idEventDef = idEventDef("getTraceBody", null, 's')
    val EV_Thread_GetTraceEndPos: idEventDef = idEventDef("getTraceEndPos", null, 'v')
    val EV_Thread_GetTraceEntity: idEventDef = idEventDef("getTraceEntity", null, 'e')
    val EV_Thread_GetTraceFraction: idEventDef = idEventDef("getTraceFraction", null, 'f')
    val EV_Thread_GetTraceJoint: idEventDef = idEventDef("getTraceJoint", null, 's')
    val EV_Thread_GetTraceNormal: idEventDef = idEventDef("getTraceNormal", null, 'v')
    val EV_Thread_InfluenceActive: idEventDef = idEventDef("influenceActive", null, 'd')
    val EV_Thread_IsClient: idEventDef = idEventDef("isClient", null, 'f')
    val EV_Thread_IsMultiplayer: idEventDef = idEventDef("isMultiplayer", null, 'f')
    val EV_Thread_KillThread: idEventDef = idEventDef("killthread", "s")
    val EV_Thread_Normalize: idEventDef = idEventDef("vecNormalize", "v", 'v')
    val EV_Thread_OnSignal: idEventDef = idEventDef("onSignal", "des")
    val EV_Thread_Pause: idEventDef = idEventDef("pause", null)
    val EV_Thread_Print: idEventDef = idEventDef("print", "s")
    val EV_Thread_PrintLn: idEventDef = idEventDef("println", "s")
    val EV_Thread_RadiusDamage: idEventDef = idEventDef("radiusDamage", "vEEEsf")
    val EV_Thread_Random: idEventDef = idEventDef("random", "f", 'f')
    val EV_Thread_Say: idEventDef = idEventDef("say", "s")
    val EV_Thread_SetCamera: idEventDef = idEventDef("setCamera", "e")
    val EV_Thread_SetCvar: idEventDef = idEventDef("setcvar", "ss")
    val EV_Thread_SetPersistantArg: idEventDef = idEventDef("setPersistantArg", "ss")
    val EV_Thread_SetSpawnArg: idEventDef = idEventDef("setSpawnArg", "ss")
    val EV_Thread_SetThreadName: idEventDef = idEventDef("threadname", "s")
    val EV_Thread_Sine: idEventDef = idEventDef("sin", "f", 'f')
    val EV_Thread_Spawn: idEventDef = idEventDef("spawn", "s", 'e')
    val EV_Thread_SpawnFloat: idEventDef = idEventDef("SpawnFloat", "sf", 'f')
    val EV_Thread_SpawnString: idEventDef = idEventDef("SpawnString", "ss", 's')
    val EV_Thread_SpawnVector: idEventDef = idEventDef("SpawnVector", "sv", 'v')
    val EV_Thread_SquareRoot: idEventDef = idEventDef("sqrt", "f", 'f')
    val EV_Thread_StartMusic: idEventDef = idEventDef("music", "s")
    val EV_Thread_StrLeft: idEventDef = idEventDef("strLeft", "sd", 's')
    val EV_Thread_StrLen: idEventDef = idEventDef("strLength", "s", 'd')
    val EV_Thread_StrMid: idEventDef = idEventDef("strMid", "sdd", 's')
    val EV_Thread_StrRight: idEventDef = idEventDef("strRight", "sd", 's')
    val EV_Thread_StrSkip: idEventDef = idEventDef("strSkip", "sd", 's')
    val EV_Thread_StrToFloat: idEventDef = idEventDef("strToFloat", "s", 'f')

    //
    // script callable events
    val EV_Thread_TerminateThread: idEventDef = idEventDef("terminate", "d")
    val EV_Thread_Trace: idEventDef = idEventDef("trace", "vvvvde", 'f')
    val EV_Thread_TracePoint: idEventDef = idEventDef("tracePoint", "vvde", 'f')
    val EV_Thread_Trigger: idEventDef = idEventDef("trigger", "e")
    val EV_Thread_VecCrossProduct: idEventDef = idEventDef("CrossProduct", "vv", 'v')
    val EV_Thread_VecDotProduct: idEventDef = idEventDef("DotProduct", "vv", 'f')
    val EV_Thread_VecLength: idEventDef = idEventDef("vecLength", "v", 'f')
    val EV_Thread_VecToAngles: idEventDef = idEventDef("VecToAngles", "v", 'v')
    val EV_Thread_WaitFor: idEventDef = idEventDef("waitFor", "e")
    val EV_Thread_WaitForThread: idEventDef = idEventDef("waitForThread", "d")
    val EV_Thread_Warning: idEventDef = idEventDef("warning", "s")

    class idThread : idClass {
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
            private val threadList: ArrayList<idThread> = ArrayList()

            //
            private var trace: trace_s = trace_s()
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
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("say \"%s\"", text.value))
            }

            private fun Event_Assert(t: idThread, value: idEventArg<Float>) {
                assert(value.value != 0f)
            }

            private fun Event_Trigger(t: idThread, e: idEventArg<idEntity>) {
                val ent = e.value
                if (ent != null) {
                    ent.Signal(signalNum_t.SIG_TRIGGER)
                    ent.ProcessEvent(Entity.EV_Activate, Game_local.gameLocal.GetLocalPlayer())
                    ent.TriggerGuis()
                }
            }

            private fun Event_SetCvar(t: idThread, name: idEventArg<String>, value: idEventArg<String>) {
                idLib.cvarSystem.SetCVarString(name.value, value.value)
            }

            private fun Event_GetCvar(t: idThread, name: idEventArg<String>) {
                ReturnString(idLib.cvarSystem.GetCVarString(name.value))
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

            private fun Event_Spawn(t: idThread, classname: idEventArg<String>) {
                val ent = arrayListOf<idEntity>()
                t.spawnArgs.Set("classname", classname.value)
                Game_local.gameLocal.SpawnEntityDef(t.spawnArgs, ent)
                ReturnEntity(ent[0])
                t.spawnArgs.Clear()
            }

            private fun Event_CopySpawnArgs(t: idThread, ent: idEventArg<idEntity>) {
                t.spawnArgs.Copy(ent.value.spawnArgs)
            }

            private fun Event_SetSpawnArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                t.spawnArgs.Set(key.value, value.value)
            }

            private fun Event_SpawnString(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<String>) {
                val result = arrayOf("")
                t.spawnArgs.GetString(key.value, defaultvalue.value, result)
                ReturnString(result[0])
            }

            private fun Event_SpawnFloat(t: idThread, key: idEventArg<String>, defaultvalue: idEventArg<Float>) {
                val result = CFloat()
                t.spawnArgs.GetFloat(key.value, Str.va("%f", defaultvalue.value), result)
                ReturnFloat(result._val)
            }

            private fun Event_SpawnVector(t: idThread, key: idEventArg<String>, d: idEventArg<idVec3>) {
                val result = idVec3()
                val defaultvalue = idVec3(d.value)
                t.spawnArgs.GetVector(
                    key.value,
                    Str.va("%f %f %f", defaultvalue.x, defaultvalue.y, defaultvalue.z),
                    result
                )
                ReturnVector(result)
            }

            private fun Event_SetPersistantArg(t: idThread, key: idEventArg<String>, value: idEventArg<String>) {
                Game_local.gameLocal.persistentLevelInfo.Set(key.value, value.value)
            }

            private fun Event_GetPersistantString(t: idThread, key: idEventArg<String>) {
                val result = arrayOf("")
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
            //            ReturnVector(ang.value.ToForward());
            //        }
            private fun Event_AngToUp(t: idThread, ang: idEventArg<idAngles>) {
                val vec = idVec3()
                ang.value.ToVectors(getVec3_zero(), null, vec)
                ReturnVector(vec)
            }

            private fun Event_GetSine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(idMath.Sin(Math_h.DEG2RAD(angle.value)))
            }

            private fun Event_GetCosine(t: idThread, angle: idEventArg<Float>) {
                ReturnFloat(idMath.Cos(Math_h.DEG2RAD(angle.value)))
            }

            private fun Event_GetSquareRoot(t: idThread, theSquare: idEventArg<Float>) {
                ReturnFloat(idMath.Sqrt(theSquare.value))
            }

            private fun Event_VecNormalize(t: idThread, vec: idEventArg<idVec3>) {
                val n = idVec3(vec.value)
                n.Normalize()
                ReturnVector(n)
            }

            private fun Event_VecLength(t: idThread, vec: idEventArg<idVec3>) {
                ReturnFloat(vec.value.Length())
            }

            private fun Event_VecDotProduct(t: idThread, vec1: idEventArg<idVec3>, vec2: idEventArg<idVec3>) {
                ReturnFloat(vec1.value.times(vec2.value))
            }

            private fun Event_VecCrossProduct(t: idThread, vec1: idEventArg<idVec3>, vec2: idEventArg<idVec3>) {
                ReturnVector(vec1.value.Cross(vec2.value))
            }

            private fun Event_VecToAngles(t: idThread, vec: idEventArg<idVec3>) {
                val ang = vec.value.ToAngles()
                ReturnVector(idVec3(ang[0], ang[1], ang[2]))
            }

            private fun Event_OnSignal(
                t: idThread,
                s: idEventArg<Int>,
                e: idEventArg<idEntity>,
                f: idEventArg<String>
            ) {
                val function: function_t?
                val signal: Int = s.value
                val ent = e.value
                val func = f.value!!
                if (null == ent) {
                    t.Error("Entity not found")
                }
                if (signal < 0 || signal >= TempDump.etoi(signalNum_t.NUM_SIGNALS)) {
                    t.Error("Signal out of range")
                }
                function = Game_local.gameLocal.program.FindFunction(func)
                if (null == function) {
                    t.Error("Function '%s' not found", func)
                }
                ent.SetSignal(signal, t, function)
            }

            private fun Event_ClearSignalThread(t: idThread, s: idEventArg<Int>, e: idEventArg<idEntity>) {
                val signal: Int = s.value
                val ent = e.value
                if (null == ent) {
                    t.Error("Entity not found")
                }
                if (signal < 0 || signal >= TempDump.etoi(signalNum_t.NUM_SIGNALS)) {
                    t.Error("Signal out of range")
                }
                ent.ClearSignalThread(signal, t)
            }

            private fun Event_SetCamera(t: idThread, e: idEventArg<idEntity>) {
                val ent = e.value
                if (null == ent) {
                    t.Error("Entity not found")
                    return
                }
                if (ent !is idCamera) {
                    t.Error("Entity is not a camera")
                    return
                }
                Game_local.gameLocal.SetCamera(ent as idCamera?)
            }

            private fun Event_Trace(
                t: idThread, s: idEventArg<idVec3>, e: idEventArg<idVec3>, mi: idEventArg<idVec3>,
                ma: idEventArg<idVec3>, c: idEventArg<Int>, p: idEventArg<idEntity>
            ) {
                val start = idVec3(s.value)
                val end = idVec3(e.value)
                val mins = idVec3(mi.value)
                val maxs = idVec3(ma.value)
                val contents_mask: Int = c.value
                val passEntity = p.value
                run {
                    val trace = trace
                    if (mins == Vector.getVec3_origin() && maxs == Vector.getVec3_origin()) {
                        Game_local.gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity)
                    } else {
                        Game_local.gameLocal.clip.TraceBounds(
                            trace,
                            start,
                            end,
                            idBounds(mins, maxs),
                            contents_mask,
                            passEntity
                        )
                    }
                    Companion.trace = trace
                }
                ReturnFloat(trace.fraction)
            }

            private fun Event_TracePoint(
                t: idThread,
                startA: idEventArg<idVec3>,
                endA: idEventArg<idVec3>,
                c: idEventArg<Int>,
                p: idEventArg<idEntity>
            ) {
                val start = idVec3(startA.value)
                val end = idVec3(endA.value)
                val contents_mask: Int = c.value
                val passEntity = p.value
                run {
                    val trace = trace
                    Game_local.gameLocal.clip.TracePoint(trace, start, end, contents_mask, passEntity)
                    Companion.trace = trace
                }
                ReturnFloat(trace.fraction)
            }

            private fun Event_FadeIn(t: idThread, colorA: idEventArg<idVec3>, time: idEventArg<Float>) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], 0.0f)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_FadeOut(t: idThread, colorA: idEventArg<idVec3>, time: idEventArg<Float>) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], 1.0f)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_FadeTo(
                t: idThread,
                colorA: idEventArg<idVec3>,
                alpha: idEventArg<Float>,
                time: idEventArg<Float>
            ) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.set(color[0], color[1], color[2], alpha.value)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_SetShaderParm(t: idThread, parmnumA: idEventArg<Int>, value: idEventArg<Float>) {
                val parmnum: Int = parmnumA.value
                if (parmnum < 0 || parmnum >= RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                    t.Error("shader parm index (%d) out of range", parmnum)
                }
                Game_local.gameLocal.globalShaderParms[parmnum] = value.value
            }

            private fun Event_StartMusic(t: idThread, text: idEventArg<String>) {
                Game_local.gameSoundWorld.PlayShaderDirectly(text.value)
            }

            private fun Event_Warning(t: idThread, text: idEventArg<String>) {
                t.Warning("%s", text.value)
            }

            private fun Event_Error(t: idThread, text: idEventArg<String>) {
                t.Error("%s", text.value)
            }

            private fun Event_StrLen(t: idThread, string: idEventArg<String>) {
                val len: Int
                len = string.value.length
                ReturnInt(len)
            }

            private fun Event_StrLeft(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num: Int = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString(string)
                    return
                }
                val result = idStr(string, 0, num)
                ReturnString(result)
            }

            private fun Event_StrRight(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num: Int = numA.value
                if (num < 0) {
                    ReturnString("")
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString(string)
                    return
                }
                ReturnString(string + (len - num))
            }

            private fun Event_StrSkip(t: idThread, stringA: idEventArg<String>, numA: idEventArg<Int>) {
                val len: Int
                val string = stringA.value
                val num: Int = numA.value
                if (num < 0) {
                    ReturnString(string)
                    return
                }
                len = string.length
                if (len < num) {
                    ReturnString("")
                    return
                }
                ReturnString(string + num)
            }

            private fun Event_StrMid(
                t: idThread,
                stringA: idEventArg<String>,
                startA: idEventArg<Int>,
                numA: idEventArg<Int>
            ) {
                val len: Int
                val string = stringA.value
                var start: Int = startA.value
                var num: Int = numA.value
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

            private fun Event_DebugLine(
                t: idThread,
                colorA: idEventArg<idVec3>,
                start: idEventArg<idVec3>,
                end: idEventArg<idVec3>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DebugLine(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    start.value,
                    end.value,
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            private fun Event_DebugArrow(
                t: idThread,
                colorA: idEventArg<idVec3>,
                start: idEventArg<idVec3>,
                end: idEventArg<idVec3>,
                size: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DebugArrow(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    start.value,
                    end.value,
                    size.value,
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            private fun Event_DebugCircle(
                t: idThread,
                colorA: idEventArg<idVec3>,
                origin: idEventArg<idVec3>,
                dir: idEventArg<idVec3>,
                radius: idEventArg<Float>,
                numSteps: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DebugCircle(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    origin.value,
                    dir.value,
                    radius.value,
                    numSteps.value,
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            private fun Event_DebugBounds(
                t: idThread,
                colorA: idEventArg<idVec3>,
                mins: idEventArg<idVec3>,
                maxs: idEventArg<idVec3>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DebugBounds(
                    idVec4(color.x, color.y, color.z, 0.0f),
                    idBounds(mins.value, maxs.value),
                    Vector.getVec3_origin(),
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            private fun Event_DrawText(
                t: idThread,
                text: idEventArg<String>,
                origin: idEventArg<idVec3>,
                scale: idEventArg<Float>,
                colorA: idEventArg<idVec3>,
                align: idEventArg<Int>,
                lifetime: idEventArg<Float>
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DrawText(
                    text.value,
                    origin.value,
                    scale.value,
                    idVec4(color.x, color.y, color.z, 0.0f),
                    Game_local.gameLocal.GetLocalPlayer()!!.viewAngles.ToMat3(),
                    align.value,
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            fun GetThread(num: Int): idThread? {
                var i: Int
                val n: Int
                var thread: idThread?
                n = threadList.size
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

            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            fun Restart() {
                var i: Int
                val n: Int

                // reset the threadIndex
                threadIndex = 0
                currentThread = null
                n = threadList.size
                //	for( i = n - 1; i >= 0; i-- ) {
//		delete threadList[ i ];
//	}
                threadList.clear()

//	memset( &trace, 0, sizeof( trace ) );
                trace = trace_s()
                trace.c.entityNum = Game_local.ENTITYNUM_NONE
            }

            fun ObjectMoveDone(threadnum: Int, obj: idEntity) {
                val thread: idThread?
                if (0 == threadnum) {
                    return
                }
                thread = GetThread(threadnum)
                thread?.ObjectMoveDone(obj)
            }

            fun GetThreads(): ArrayList<idThread> {
                return threadList
            }

            fun KillThread(name: String) {
                var i: Int
                val num: Int
                val len: Int
                val ptr: Int
                var thread: idThread?

                // see if the name uses a wild card
                ptr = name.indexOf('*')
                len = if (ptr != -1) {
                    ptr - name.length //TODO:double check this puhlease!
                } else {
                    name.length
                }

                // kill only those threads whose name matches name
                num = threadList.size
                i = 0
                while (i < num) {
                    thread = threadList[i]
                    if (0 == idStr.Cmpn(thread.GetThreadName(), name, len)) {
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

            fun BeginMultiFrameEvent(ent: idEntity?, event: idEventDef): Boolean {
                if (null == currentThread) {
                    idGameLocal.Error("idThread::BeginMultiFrameEvent called without a current thread")
                }
                return currentThread!!.interpreter.BeginMultiFrameEvent(ent, event)
            }

            fun EndMultiFrameEvent(ent: idEntity, event: idEventDef) {
                if (null == currentThread) {
                    idGameLocal.Error("idThread::EndMultiFrameEvent called without a current thread")
                }
                currentThread!!.interpreter.EndMultiFrameEvent(ent, event)
            }

            fun ReturnString(text: String) {
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

            fun ReturnVector(vec: idVec3) {
                Game_local.gameLocal.program.ReturnVector(vec)
            }

            fun ReturnEntity(ent: idEntity?) {
                Game_local.gameLocal.program.ReturnEntity(ent)
            }

            fun delete(thread: idThread) {
                thread._deconstructor()
            }

            init {
                eventCallbacks.putAll(idClass.getEventCallBacks())
                eventCallbacks[EV_Thread_Execute] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_Execute }
                eventCallbacks[EV_Thread_TerminateThread] =
                    eventCallback_t1<idThread> { t: Any?, num: idEventArg<*>? ->
                        idThread::Event_TerminateThread
                    }
                eventCallbacks[EV_Thread_Pause] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_Pause }
                eventCallbacks[EV_Thread_Wait] =
                    eventCallback_t1<idThread> { t: Any?, time: idEventArg<*>? ->
                        idThread::Event_Wait
                    }
                eventCallbacks[EV_Thread_WaitFrame] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_WaitFrame }
                eventCallbacks[EV_Thread_WaitFor] =
                    eventCallback_t1<idThread> { t: Any?, e: idEventArg<*>? ->
                        idThread::Event_WaitFor
                    }
                eventCallbacks[EV_Thread_WaitForThread] =
                    eventCallback_t1<idThread> { t: Any?, num: idEventArg<*>? ->
                        idThread::Event_WaitForThread
                    }
                eventCallbacks[EV_Thread_Print] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_Print
                    }
                eventCallbacks[EV_Thread_PrintLn] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_PrintLn
                    }
                eventCallbacks[EV_Thread_Say] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_Say
                    }
                eventCallbacks[EV_Thread_Assert] =
                    eventCallback_t1<idThread> { t: Any?, value: idEventArg<*>? ->
                        idThread::Event_Assert
                    }
                eventCallbacks[EV_Thread_Trigger] =
                    eventCallback_t1<idThread> { t: Any?, e: idEventArg<*>? ->
                        idThread::Event_Trigger
                    }
                eventCallbacks[EV_Thread_SetCvar] =
                    eventCallback_t2<idThread> { t: Any, name: idEventArg<*>, value: idEventArg<*> ->
                        idThread::Event_SetCvar
                    }
                eventCallbacks[EV_Thread_GetCvar] =
                    eventCallback_t1<idThread> { t: Any?, name: idEventArg<*>? ->
                        idThread::Event_GetCvar
                    }
                eventCallbacks[EV_Thread_Random] =
                    eventCallback_t1<idThread> { t: Any?, range: idEventArg<*>? ->
                        idThread::Event_Random
                    }
                eventCallbacks[EV_Thread_GetTime] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTime }
                eventCallbacks[EV_Thread_KillThread] =
                    eventCallback_t1<idThread> { t: Any?, name: idEventArg<*>? ->
                        idThread::Event_KillThread
                    }
                eventCallbacks[EV_Thread_SetThreadName] =
                    eventCallback_t1<idThread> { t: Any?, name: idEventArg<*>? ->
                        idThread::Event_SetThreadName
                    }
                eventCallbacks[EV_Thread_GetEntity] =
                    eventCallback_t1<idThread> { t: Any?, n: idEventArg<*>? ->
                        idThread::Event_GetEntity
                    }
                eventCallbacks[EV_Thread_Spawn] =
                    eventCallback_t1<idThread> { t: Any?, classname: idEventArg<*>? ->
                        idThread::Event_Spawn
                    }
                eventCallbacks[EV_Thread_CopySpawnArgs] =
                    eventCallback_t1<idThread> { t: Any?, ent: idEventArg<*>? ->
                        idThread::Event_CopySpawnArgs
                    }
                eventCallbacks[EV_Thread_SetSpawnArg] =
                    eventCallback_t2<idThread> { t: Any?, key: idEventArg<*>?, value: idEventArg<*>? ->
                        idThread::Event_SetSpawnArg
                    }
                eventCallbacks[EV_Thread_SpawnString] =
                    eventCallback_t2<idThread> { t: Any?, key: idEventArg<*>?, defaultvalue: idEventArg<*> ->
                        idThread::Event_SpawnString
                    }
                eventCallbacks[EV_Thread_SpawnFloat] =
                    eventCallback_t2<idThread> { t: Any?, key: idEventArg<*>?, defaultvalue: idEventArg<*> ->
                        idThread::Event_SpawnFloat
                    }
                eventCallbacks[EV_Thread_SpawnVector] =
                    eventCallback_t2<idThread> { t: Any?, key: idEventArg<*>, d: idEventArg<*> ->
                        idThread::Event_SpawnVector
                    }
                eventCallbacks[EV_Thread_ClearPersistantArgs] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_ClearPersistantArgs }
                eventCallbacks[EV_Thread_SetPersistantArg] =
                    eventCallback_t2<idThread> { t: Any?, key: idEventArg<*>, value: idEventArg<*> ->
                        idThread::Event_SetPersistantArg
                    }
                eventCallbacks[EV_Thread_GetPersistantString] =
                    eventCallback_t1<idThread> { t: Any?, key: idEventArg<*>? ->
                        idThread::Event_GetPersistantString
                    }
                eventCallbacks[EV_Thread_GetPersistantFloat] =
                    eventCallback_t1<idThread> { t: Any?, key: idEventArg<*>? ->
                        idThread::Event_GetPersistantFloat
                    }
                eventCallbacks[EV_Thread_GetPersistantVector] =
                    eventCallback_t1<idThread> { t: Any?, key: idEventArg<*>? ->
                        idThread::Event_GetPersistantVector
                    }
                eventCallbacks[EV_Thread_AngToForward] =
                    eventCallback_t1<idThread> { t: Any?, ang: idEventArg<*>? ->
                        idThread::Event_AngToForward
                    }
                eventCallbacks[EV_Thread_AngToRight] =
                    eventCallback_t1<idThread> { t: Any?, ang: idEventArg<*>? ->
                        idThread::Event_AngToRight
                    }
                eventCallbacks[EV_Thread_AngToUp] =
                    eventCallback_t1<idThread> { t: Any?, ang: idEventArg<*>? ->
                        idThread::Event_AngToUp
                    }
                eventCallbacks[EV_Thread_Sine] =
                    eventCallback_t1<idThread> { t: Any?, angle: idEventArg<*>? ->
                        idThread::Event_GetSine
                    }
                eventCallbacks[EV_Thread_Cosine] =
                    eventCallback_t1<idThread> { t: Any?, angle: idEventArg<*>? ->
                        idThread::Event_GetCosine
                    }
                eventCallbacks[EV_Thread_SquareRoot] =
                    eventCallback_t1<idThread> { t: Any?, theSquare: idEventArg<*>? ->
                        idThread::Event_GetSquareRoot
                    }
                eventCallbacks[EV_Thread_Normalize] =
                    eventCallback_t1<idThread> { t: Any?, vec: idEventArg<*>? ->
                        idThread::Event_VecNormalize
                    }
                eventCallbacks[EV_Thread_VecLength] =
                    eventCallback_t1<idThread> { t: Any?, vec: idEventArg<*>? ->
                        idThread::Event_VecLength
                    }
                eventCallbacks[EV_Thread_VecDotProduct] =
                    eventCallback_t2<idThread> { t: Any?, vec1: idEventArg<*>, vec2: idEventArg<*> ->
                        idThread::Event_VecDotProduct
                    }
                eventCallbacks[EV_Thread_VecCrossProduct] =
                    eventCallback_t2<idThread> { t: Any?, vec1: idEventArg<*>, vec2: idEventArg<*> ->
                        idThread::Event_VecCrossProduct
                    }
                eventCallbacks[EV_Thread_VecToAngles] =
                    eventCallback_t1<idThread> { t: Any?, vec: idEventArg<*>? ->
                        idThread::Event_VecToAngles
                    }
                eventCallbacks[EV_Thread_OnSignal] =
                    eventCallback_t3<idThread> { t: Any?, s: idEventArg<*>,
                                                 e: idEventArg<*>,
                                                 f: idEventArg<*> ->
                        idThread::Event_OnSignal
                    }
                eventCallbacks[EV_Thread_ClearSignal] =
                    eventCallback_t2<idThread> { t: Any?, s: idEventArg<*>, e: idEventArg<*> ->
                        idThread::Event_ClearSignalThread
                    }
                eventCallbacks[EV_Thread_SetCamera] =
                    eventCallback_t1<idThread> { t: Any?, e: idEventArg<*>? ->
                        idThread::Event_SetCamera
                    }
                eventCallbacks[EV_Thread_FirstPerson] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_FirstPerson }
                eventCallbacks[EV_Thread_Trace] =
                    eventCallback_t6<idThread> { t: Any?, s: idEventArg<*>, e: idEventArg<*>, mi: idEventArg<*>,
                                                 ma: idEventArg<*>, c: idEventArg<*>, p: idEventArg<*> ->
                        idThread::Event_Trace
                    }
                eventCallbacks[EV_Thread_TracePoint] =
                    eventCallback_t4<idThread> { t: Any?, startA: idEventArg<*>,
                                                 endA: idEventArg<*>,
                                                 c: idEventArg<*>,
                                                 p: idEventArg<*> ->
                        idThread::Event_TracePoint
                    }
                eventCallbacks[EV_Thread_GetTraceFraction] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceFraction }
                eventCallbacks[EV_Thread_GetTraceEndPos] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceEndPos }
                eventCallbacks[EV_Thread_GetTraceNormal] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceNormal }
                eventCallbacks[EV_Thread_GetTraceEntity] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceEntity }
                eventCallbacks[EV_Thread_GetTraceJoint] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceJoint }
                eventCallbacks[EV_Thread_GetTraceBody] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTraceBody }
                eventCallbacks[EV_Thread_FadeIn] =
                    eventCallback_t2<idThread> { t: Any?, colorA: idEventArg<*>, time: idEventArg<*> ->
                        idThread::Event_FadeIn
                    }
                eventCallbacks[EV_Thread_FadeOut] =
                    eventCallback_t2<idThread> { t: Any?, colorA: idEventArg<*>, time: idEventArg<*> ->
                        idThread::Event_FadeOut
                    }
                eventCallbacks[EV_Thread_FadeTo] =
                    eventCallback_t3<idThread> { t: Any?, colorA: idEventArg<*>,
                                                 alpha: idEventArg<*>,
                                                 time: idEventArg<*> ->
                        idThread::Event_FadeTo
                    }
                eventCallbacks[Entity.EV_SetShaderParm] =
                    eventCallback_t2<idThread> { t: Any?, parmnumA: idEventArg<*>, value: idEventArg<*> ->
                        idThread::Event_SetShaderParm
                    }
                eventCallbacks[EV_Thread_StartMusic] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_StartMusic
                    }
                eventCallbacks[EV_Thread_Warning] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_Warning
                    }
                eventCallbacks[EV_Thread_Error] =
                    eventCallback_t1<idThread> { t: Any?, text: idEventArg<*>? ->
                        idThread::Event_Error
                    }
                eventCallbacks[EV_Thread_StrLen] =
                    eventCallback_t1<idThread> { t: Any?, string: idEventArg<*>? ->
                        idThread::Event_StrLen
                    }
                eventCallbacks[EV_Thread_StrLeft] =
                    eventCallback_t2<idThread> { t: Any?, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        idThread::Event_StrLeft
                    }
                eventCallbacks[EV_Thread_StrRight] =
                    eventCallback_t2<idThread> { t: Any?, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        idThread::Event_StrRight
                    }
                eventCallbacks[EV_Thread_StrSkip] =
                    eventCallback_t2<idThread> { t: Any?, stringA: idEventArg<*>, numA: idEventArg<*> ->
                        idThread::Event_StrSkip
                    }
                eventCallbacks[EV_Thread_StrMid] =
                    eventCallback_t3<idThread> { t: Any?, stringA: idEventArg<*>,
                                                 startA: idEventArg<*>,
                                                 numA: idEventArg<*> ->
                        idThread::Event_StrMid
                    }
                eventCallbacks[EV_Thread_StrToFloat] =
                    eventCallback_t1<idThread> { t: Any?, string: idEventArg<*>? ->
                        idThread::Event_StrToFloat
                    }
                eventCallbacks[EV_Thread_RadiusDamage] =
                    eventCallback_t6<idThread> { t: Any?, origin: idEventArg<*>,
                                                 inflictor: idEventArg<*>,
                                                 attacker: idEventArg<*>,
                                                 ignore: idEventArg<*>,
                                                 damageDefName: idEventArg<*>,
                                                 dmgPower: idEventArg<*> ->
                        idThread::Event_RadiusDamage
                    }
                eventCallbacks[EV_Thread_IsClient] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_IsClient }
                eventCallbacks[EV_Thread_IsMultiplayer] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_IsMultiplayer }
                eventCallbacks[EV_Thread_GetFrameTime] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetFrameTime }
                eventCallbacks[EV_Thread_GetTicsPerSecond] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_GetTicsPerSecond }
                eventCallbacks[Entity.EV_CacheSoundShader] =
                    eventCallback_t1<idThread> { t: Any?, soundName: idEventArg<*>? ->
                        idThread::Event_CacheSoundShader
                    }
                eventCallbacks[EV_Thread_DebugLine] =
                    eventCallback_t4<idThread> { t: Any?, colorA: idEventArg<*>,
                                                 start: idEventArg<*>,
                                                 end: idEventArg<*>,
                                                 lifetime: idEventArg<*> ->
                        idThread::Event_DebugLine
                    }
                eventCallbacks[EV_Thread_DebugArrow] =
                    eventCallback_t5<idThread> { t: Any?, colorA: idEventArg<*>,
                                                 start: idEventArg<*>,
                                                 end: idEventArg<*>,
                                                 size: idEventArg<*>,
                                                 lifetime: idEventArg<*> ->
                        idThread::Event_DebugArrow
                    }
                eventCallbacks[EV_Thread_DebugCircle] =
                    eventCallback_t6<idThread> { t: Any, colorA: idEventArg<*>,
                                                 origin: idEventArg<*>,
                                                 dir: idEventArg<*>,
                                                 radius: idEventArg<*>,
                                                 numSteps: idEventArg<*>,
                                                 lifetime: idEventArg<*> ->
                        idThread::Event_DebugCircle
                    }
                eventCallbacks[EV_Thread_DebugBounds] =
                    eventCallback_t4<idThread> { t: Any?, colorA: idEventArg<*>,
                                                 mins: idEventArg<*>,
                                                 maxs: idEventArg<*>,
                                                 lifetime: idEventArg<*> ->
                        idThread::Event_DebugBounds
                    }
                eventCallbacks[EV_Thread_DrawText] =
                    eventCallback_t6<idThread> { t: Any?, text: idEventArg<*>,
                                                 origin: idEventArg<*>,
                                                 scale: idEventArg<*>,
                                                 colorA: idEventArg<*>,
                                                 align: idEventArg<*>,
                                                 lifetime: idEventArg<*> ->
                        idThread::Event_DrawText
                    }
                eventCallbacks[EV_Thread_InfluenceActive] =
                    eventCallback_t0<idThread> { obj: Any? -> idThread::Event_InfluenceActive }
            }
        }

        private var creationTime = 0
        private val interpreter: idInterpreter = idInterpreter()

        //
        private var lastExecuteTime = 0

        //
        private var manualControl = false

        //
        private var spawnArgs: idDict = idDict()
        private val threadName: idStr = idStr()

        //
        private var threadNum = 0
        private var waitingFor = 0

        //
        private var waitingForThread: idThread? = null
        private var waitingUntil = 0

        //
        //
        constructor() {
            Init()
            SetThreadName(Str.va("thread_%d", threadIndex))
            if (SysCvar.g_debugScript.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: create thread (%d) '%s'\n",
                    Game_local.gameLocal.time,
                    threadNum,
                    threadName
                )
            }
        }

        constructor(self: idEntity, func: function_t) {
            assert(self != null)
            Init()
            SetThreadName(self.name.toString())
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

        constructor(func: function_t) {
            assert(func != null)
            Init()
            SetThreadName(func.Name())
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

        constructor(source: idInterpreter, self: idEntity, func: function_t, args: Int) {
            assert(self != null)
            Init()
            SetThreadName(self.name.toString())
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
            threadList.add(this)
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
            ReturnFloat(Math_h.MS2SEC(Game_local.gameLocal.realClientTime.toFloat()))
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
                ReturnVector(Vector.getVec3_origin())
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
                val af = Game_local.gameLocal.entities[trace.c.entityNum] as idAFEntity_Base
                if (af != null && af is idAFEntity_Base && af.IsActiveAF()) {
                    ReturnString(af.GetAnimator().GetJointName(Clip.CLIPMODEL_ID_TO_JOINT_HANDLE(trace.c.id)))
                    return
                }
            }
            ReturnString("")
        }

        private fun Event_GetTraceBody() {
            if (trace.fraction < 1.0f && trace.c.id < 0) {
                val af = Game_local.gameLocal.entities[trace.c.entityNum] as idAFEntity_Base
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
            ReturnFloat(TempDump.btoi(Game_local.gameLocal.isClient).toFloat())
        }

        private fun Event_IsMultiplayer() {
            ReturnFloat(TempDump.btoi(Game_local.gameLocal.isMultiplayer).toFloat())
        }

        private fun Event_GetFrameTime() {
            ReturnFloat(Math_h.MS2SEC(idGameLocal.msec.toFloat()))
        }

        private fun Event_GetTicsPerSecond() {
            ReturnFloat(UsercmdGen.USERCMD_HZ.toFloat())
        }

        private fun Event_InfluenceActive() {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            ReturnInt(player != null && player.GetInfluenceLevel() != 0)
        }

        // virtual						~idThread();
        override fun _deconstructor() {
            var thread: idThread?
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
            threadList.remove(this)
            n = threadList.size
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
            savefile.ReadDict(spawnArgs)
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
            WaitMS(Math_h.SEC2MS(time).toInt())
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
        fun CallFunction(func: function_t, clearStack: Boolean) {
            ClearWaitFor()
            interpreter.EnterFunction(func, clearStack)
        }

        /*
         ================
         idThread::CallFunction

         NOTE: If this is called from within a event called by this thread, the function arguments will be invalid after calling this function.
         ================
         */
        fun CallFunction(self: idEntity, func: function_t, clearStack: Boolean) {
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
                        Game_local.gameLocal.entities[waitingFor].name
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

        override fun oSet(oGet: idClass?) {
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

        fun IsWaitingFor(obj: idEntity): Boolean {
            assert(obj != null)
            return waitingFor == obj.entityNumber
        }

        fun ObjectMoveDone(obj: idEntity) {
            assert(obj != null)
            if (IsWaitingFor(obj)) {
                ClearWaitFor()
                DelayedStart(0)
            }
        }

        fun ThreadCallback(thread: idThread?) {
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

        fun SetThreadName(name: String) {
            threadName.set(name)
        }

        fun GetThreadName(): String {
            return threadName.toString()
        }

        fun Error(fmt: String, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            interpreter.Error(text)
        }

        fun Warning(fmt: String, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            interpreter.Warning(text)
        }

        /*
         ================
         idThread::ListThreads_f
         ================
         */
        class ListThreads_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                var i: Int
                val n: Int
                n = threadList.size
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
                private val instance: cmdFunction_t = ListThreads_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }
    }
}