package neo.Game.Script

import neo.CM.CollisionModel.trace_s
import neo.Game.*
import neo.Game.AFEntity.idAFEntity_Base
import neo.Game.Camera.idCamera
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
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.*
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
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object Script_Thread {
    val EV_Thread_SetCallback: idEventDef? = idEventDef("<script_setcallback>", null)
    val EV_Thread_Wait: idEventDef? = idEventDef("wait", "f")
    val EV_Thread_WaitFrame: idEventDef? = idEventDef("waitFrame")
    val EV_Thread_AngToForward: idEventDef? = idEventDef("angToForward", "v", 'v')
    val EV_Thread_AngToRight: idEventDef? = idEventDef("angToRight", "v", 'v')
    val EV_Thread_AngToUp: idEventDef? = idEventDef("angToUp", "v", 'v')
    val EV_Thread_Assert: idEventDef? = idEventDef("assert", "f")
    val EV_Thread_ClearPersistantArgs: idEventDef? = idEventDef("clearPersistantArgs")
    val EV_Thread_ClearSignal: idEventDef? = idEventDef("clearSignalThread", "de")
    val EV_Thread_CopySpawnArgs: idEventDef? = idEventDef("copySpawnArgs", "e")
    val EV_Thread_Cosine: idEventDef? = idEventDef("cos", "f", 'f')
    val EV_Thread_DebugArrow: idEventDef? = idEventDef("debugArrow", "vvvdf")
    val EV_Thread_DebugBounds: idEventDef? = idEventDef("debugBounds", "vvvf")
    val EV_Thread_DebugCircle: idEventDef? = idEventDef("debugCircle", "vvvfdf")
    val EV_Thread_DebugLine: idEventDef? = idEventDef("debugLine", "vvvf")
    val EV_Thread_DrawText: idEventDef? = idEventDef("drawText", "svfvdf")
    val EV_Thread_Error: idEventDef? = idEventDef("error", "s")
    val EV_Thread_Execute: idEventDef? = idEventDef("<execute>", null)
    val EV_Thread_FadeIn: idEventDef? = idEventDef("fadeIn", "vf")
    val EV_Thread_FadeOut: idEventDef? = idEventDef("fadeOut", "vf")
    val EV_Thread_FadeTo: idEventDef? = idEventDef("fadeTo", "vff")
    val EV_Thread_FirstPerson: idEventDef? = idEventDef("firstPerson", null)
    val EV_Thread_GetCvar: idEventDef? = idEventDef("getcvar", "s", 's')
    val EV_Thread_GetEntity: idEventDef? = idEventDef("getEntity", "s", 'e')
    val EV_Thread_GetFrameTime: idEventDef? = idEventDef("getFrameTime", null, 'f')
    val EV_Thread_GetPersistantFloat: idEventDef? = idEventDef("getPersistantFloat", "s", 'f')
    val EV_Thread_GetPersistantString: idEventDef? = idEventDef("getPersistantString", "s", 's')
    val EV_Thread_GetPersistantVector: idEventDef? = idEventDef("getPersistantVector", "s", 'v')
    val EV_Thread_GetTicsPerSecond: idEventDef? = idEventDef("getTicsPerSecond", null, 'f')
    val EV_Thread_GetTime: idEventDef? = idEventDef("getTime", null, 'f')
    val EV_Thread_GetTraceBody: idEventDef? = idEventDef("getTraceBody", null, 's')
    val EV_Thread_GetTraceEndPos: idEventDef? = idEventDef("getTraceEndPos", null, 'v')
    val EV_Thread_GetTraceEntity: idEventDef? = idEventDef("getTraceEntity", null, 'e')
    val EV_Thread_GetTraceFraction: idEventDef? = idEventDef("getTraceFraction", null, 'f')
    val EV_Thread_GetTraceJoint: idEventDef? = idEventDef("getTraceJoint", null, 's')
    val EV_Thread_GetTraceNormal: idEventDef? = idEventDef("getTraceNormal", null, 'v')
    val EV_Thread_InfluenceActive: idEventDef? = idEventDef("influenceActive", null, 'd')
    val EV_Thread_IsClient: idEventDef? = idEventDef("isClient", null, 'f')
    val EV_Thread_IsMultiplayer: idEventDef? = idEventDef("isMultiplayer", null, 'f')
    val EV_Thread_KillThread: idEventDef? = idEventDef("killthread", "s")
    val EV_Thread_Normalize: idEventDef? = idEventDef("vecNormalize", "v", 'v')
    val EV_Thread_OnSignal: idEventDef? = idEventDef("onSignal", "des")
    val EV_Thread_Pause: idEventDef? = idEventDef("pause", null)
    val EV_Thread_Print: idEventDef? = idEventDef("print", "s")
    val EV_Thread_PrintLn: idEventDef? = idEventDef("println", "s")
    val EV_Thread_RadiusDamage: idEventDef? = idEventDef("radiusDamage", "vEEEsf")
    val EV_Thread_Random: idEventDef? = idEventDef("random", "f", 'f')
    val EV_Thread_Say: idEventDef? = idEventDef("say", "s")
    val EV_Thread_SetCamera: idEventDef? = idEventDef("setCamera", "e")
    val EV_Thread_SetCvar: idEventDef? = idEventDef("setcvar", "ss")
    val EV_Thread_SetPersistantArg: idEventDef? = idEventDef("setPersistantArg", "ss")
    val EV_Thread_SetSpawnArg: idEventDef? = idEventDef("setSpawnArg", "ss")
    val EV_Thread_SetThreadName: idEventDef? = idEventDef("threadname", "s")
    val EV_Thread_Sine: idEventDef? = idEventDef("sin", "f", 'f')
    val EV_Thread_Spawn: idEventDef? = idEventDef("spawn", "s", 'e')
    val EV_Thread_SpawnFloat: idEventDef? = idEventDef("SpawnFloat", "sf", 'f')
    val EV_Thread_SpawnString: idEventDef? = idEventDef("SpawnString", "ss", 's')
    val EV_Thread_SpawnVector: idEventDef? = idEventDef("SpawnVector", "sv", 'v')
    val EV_Thread_SquareRoot: idEventDef? = idEventDef("sqrt", "f", 'f')
    val EV_Thread_StartMusic: idEventDef? = idEventDef("music", "s")
    val EV_Thread_StrLeft: idEventDef? = idEventDef("strLeft", "sd", 's')
    val EV_Thread_StrLen: idEventDef? = idEventDef("strLength", "s", 'd')
    val EV_Thread_StrMid: idEventDef? = idEventDef("strMid", "sdd", 's')
    val EV_Thread_StrRight: idEventDef? = idEventDef("strRight", "sd", 's')
    val EV_Thread_StrSkip: idEventDef? = idEventDef("strSkip", "sd", 's')
    val EV_Thread_StrToFloat: idEventDef? = idEventDef("strToFloat", "s", 'f')

    //
    // script callable events
    val EV_Thread_TerminateThread: idEventDef? = idEventDef("terminate", "d")
    val EV_Thread_Trace: idEventDef? = idEventDef("trace", "vvvvde", 'f')
    val EV_Thread_TracePoint: idEventDef? = idEventDef("tracePoint", "vvde", 'f')
    val EV_Thread_Trigger: idEventDef? = idEventDef("trigger", "e")
    val EV_Thread_VecCrossProduct: idEventDef? = idEventDef("CrossProduct", "vv", 'v')
    val EV_Thread_VecDotProduct: idEventDef? = idEventDef("DotProduct", "vv", 'f')
    val EV_Thread_VecLength: idEventDef? = idEventDef("vecLength", "v", 'f')
    val EV_Thread_VecToAngles: idEventDef? = idEventDef("VecToAngles", "v", 'v')
    val EV_Thread_WaitFor: idEventDef? = idEventDef("waitFor", "e")
    val EV_Thread_WaitForThread: idEventDef? = idEventDef("waitForThread", "d")
    val EV_Thread_Warning: idEventDef? = idEventDef("warning", "s")

    class idThread : idClass {
        companion object {
            const val BYTES = Integer.BYTES * 14 //TODO
            protected var eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            //        // CLASS_PROTOTYPE( idThread );
            //        public static final idTypeInfo Type = new idTypeInfo(null, null, eventCallbacks, null, null, null, null);
            //
            //
            private var currentThread: idThread? = null

            //
            private var threadIndex = 0
            private val threadList: idList<idThread?>? = idList()

            //
            private var trace: trace_s? = trace_s()
            private fun Event_SetThreadName(t: idThread?, name: idEventArg<String?>?) {
                t.SetThreadName(name.value)
            }

            //
            // script callable Events
            //
            private fun Event_TerminateThread(t: idThread?, num: idEventArg<Int?>?) {
                val thread: idThread?
                thread = GetThread(num.value)
                KillThread(num.value)
            }

            private fun Event_Wait(t: idThread?, time: idEventArg<Float?>?) {
                t.WaitSec(time.value)
            }

            private fun Event_WaitFor(t: idThread?, e: idEventArg<idEntity?>?) {
                val ent = e.value
                if (ent != null && ent.RespondsTo(Script_Thread.EV_Thread_SetCallback)) {
                    ent.ProcessEvent(Script_Thread.EV_Thread_SetCallback)
                    if (Game_local.gameLocal.program.GetReturnedInteger() != 0) {
                        t.Pause()
                        t.waitingFor = ent.entityNumber
                    }
                }
            }

            private fun Event_WaitForThread(t: idThread?, num: idEventArg<Int?>?) {
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

            private fun Event_Print(t: idThread?, text: idEventArg<String?>?) {
                Game_local.gameLocal.Printf("%s", text.value)
            }

            private fun Event_PrintLn(t: idThread?, text: idEventArg<String?>?) {
                Game_local.gameLocal.Printf("%s\n", text.value)
            }

            private fun Event_Say(t: idThread?, text: idEventArg<String?>?) {
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("say \"%s\"", text.value))
            }

            private fun Event_Assert(t: idThread?, value: idEventArg<Float?>?) {
                assert(value.value != 0)
            }

            private fun Event_Trigger(t: idThread?, e: idEventArg<idEntity?>?) {
                val ent = e.value
                if (ent != null) {
                    ent.Signal(signalNum_t.SIG_TRIGGER)
                    ent.ProcessEvent(Entity.EV_Activate, Game_local.gameLocal.GetLocalPlayer())
                    ent.TriggerGuis()
                }
            }

            private fun Event_SetCvar(t: idThread?, name: idEventArg<String?>?, value: idEventArg<String?>?) {
                idLib.cvarSystem.SetCVarString(name.value, value.value)
            }

            private fun Event_GetCvar(t: idThread?, name: idEventArg<String?>?) {
                ReturnString(idLib.cvarSystem.GetCVarString(name.value))
            }

            private fun Event_Random(t: idThread?, range: idEventArg<Float?>?) {
                val result: Float
                result = Game_local.gameLocal.random.RandomFloat()
                ReturnFloat(range.value * result)
            }

            private fun Event_KillThread(t: idThread?, name: idEventArg<String?>?) {
                KillThread(name.value)
            }

            private fun Event_GetEntity(t: idThread?, n: idEventArg<String?>?) {
                val entnum: Int
                val ent: idEntity?
                val name = n.value!!
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

            private fun Event_Spawn(t: idThread?, classname: idEventArg<String?>?) {
                val ent = arrayOf<idEntity?>(null)
                t.spawnArgs.Set("classname", classname.value)
                Game_local.gameLocal.SpawnEntityDef(t.spawnArgs, ent)
                ReturnEntity(ent[0])
                t.spawnArgs.Clear()
            }

            private fun Event_CopySpawnArgs(t: idThread?, ent: idEventArg<idEntity?>?) {
                t.spawnArgs.Copy(ent.value.spawnArgs)
            }

            private fun Event_SetSpawnArg(t: idThread?, key: idEventArg<String?>?, value: idEventArg<String?>?) {
                t.spawnArgs.Set(key.value, value.value)
            }

            private fun Event_SpawnString(t: idThread?, key: idEventArg<String?>?, defaultvalue: idEventArg<String?>?) {
                val result = arrayOf<String?>(null)
                t.spawnArgs.GetString(key.value, defaultvalue.value, result)
                ReturnString(result[0])
            }

            private fun Event_SpawnFloat(t: idThread?, key: idEventArg<String?>?, defaultvalue: idEventArg<Float?>?) {
                val result = CFloat()
                t.spawnArgs.GetFloat(key.value, Str.va("%f", defaultvalue.value), result)
                ReturnFloat(result.getVal())
            }

            private fun Event_SpawnVector(t: idThread?, key: idEventArg<String?>?, d: idEventArg<idVec3?>?) {
                val result = idVec3()
                val defaultvalue = idVec3(d.value)
                t.spawnArgs.GetVector(
                    key.value,
                    Str.va("%f %f %f", defaultvalue.x, defaultvalue.y, defaultvalue.z),
                    result
                )
                ReturnVector(result)
            }

            private fun Event_SetPersistantArg(t: idThread?, key: idEventArg<String?>?, value: idEventArg<String?>?) {
                Game_local.gameLocal.persistentLevelInfo.Set(key.value, value.value)
            }

            private fun Event_GetPersistantString(t: idThread?, key: idEventArg<String?>?) {
                val result = arrayOf<String?>(null)
                Game_local.gameLocal.persistentLevelInfo.GetString(key.value, "", result)
                ReturnString(result[0])
            }

            private fun Event_GetPersistantFloat(t: idThread?, key: idEventArg<String?>?) {
                val result = CFloat()
                Game_local.gameLocal.persistentLevelInfo.GetFloat(key.value, "0", result)
                ReturnFloat(result.getVal())
            }

            private fun Event_GetPersistantVector(t: idThread?, key: idEventArg<String?>?) {
                val result = idVec3()
                Game_local.gameLocal.persistentLevelInfo.GetVector(key.value, "0 0 0", result)
                ReturnVector(result)
            }

            private fun Event_AngToForward(t: idThread?, ang: idEventArg<idVec3?>?) {
                ReturnVector(idAngles(ang.value).ToForward())
            }

            private fun Event_AngToRight(t: idThread?, ang: idEventArg<idAngles?>?) {
                val vec = idVec3()
                ang.value.ToVectors(null, vec)
                ReturnVector(vec)
            }

            //        private static void Event_AngToForward(idThread t, idEventArg<idAngles> ang) {
            //            ReturnVector(ang.value.ToForward());
            //        }
            private fun Event_AngToUp(t: idThread?, ang: idEventArg<idAngles?>?) {
                val vec = idVec3()
                ang.value.ToVectors(null, null, vec)
                ReturnVector(vec)
            }

            private fun Event_GetSine(t: idThread?, angle: idEventArg<Float?>?) {
                ReturnFloat(idMath.Sin(Math_h.DEG2RAD(angle.value)))
            }

            private fun Event_GetCosine(t: idThread?, angle: idEventArg<Float?>?) {
                ReturnFloat(idMath.Cos(Math_h.DEG2RAD(angle.value)))
            }

            private fun Event_GetSquareRoot(t: idThread?, theSquare: idEventArg<Float?>?) {
                ReturnFloat(idMath.Sqrt(theSquare.value))
            }

            private fun Event_VecNormalize(t: idThread?, vec: idEventArg<idVec3?>?) {
                val n = idVec3(vec.value)
                n.Normalize()
                ReturnVector(n)
            }

            private fun Event_VecLength(t: idThread?, vec: idEventArg<idVec3?>?) {
                ReturnFloat(vec.value.Length())
            }

            private fun Event_VecDotProduct(t: idThread?, vec1: idEventArg<idVec3?>?, vec2: idEventArg<idVec3?>?) {
                ReturnFloat(vec1.value.oMultiply(vec2.value))
            }

            private fun Event_VecCrossProduct(t: idThread?, vec1: idEventArg<idVec3?>?, vec2: idEventArg<idVec3?>?) {
                ReturnVector(vec1.value.Cross(vec2.value))
            }

            private fun Event_VecToAngles(t: idThread?, vec: idEventArg<idVec3?>?) {
                val ang = vec.value.ToAngles()
                ReturnVector(idVec3(ang.oGet(0), ang.oGet(1), ang.oGet(2)))
            }

            private fun Event_OnSignal(
                t: idThread?,
                s: idEventArg<Int?>?,
                e: idEventArg<idEntity?>?,
                f: idEventArg<String?>?
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

            private fun Event_ClearSignalThread(t: idThread?, s: idEventArg<Int?>?, e: idEventArg<idEntity?>?) {
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

            private fun Event_SetCamera(t: idThread?, e: idEventArg<idEntity?>?) {
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
                t: idThread?, s: idEventArg<idVec3?>?, e: idEventArg<idVec3?>?, mi: idEventArg<idVec3?>?,
                ma: idEventArg<idVec3?>?, c: idEventArg<Int?>?, p: idEventArg<idEntity?>?
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
                t: idThread?,
                startA: idEventArg<idVec3?>?,
                endA: idEventArg<idVec3?>?,
                c: idEventArg<Int?>?,
                p: idEventArg<idEntity?>?
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

            private fun Event_FadeIn(t: idThread?, colorA: idEventArg<idVec3?>?, time: idEventArg<Float?>?) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.Set(color.oGet(0), color.oGet(1), color.oGet(2), 0.0f)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_FadeOut(t: idThread?, colorA: idEventArg<idVec3?>?, time: idEventArg<Float?>?) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.Set(color.oGet(0), color.oGet(1), color.oGet(2), 1.0f)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_FadeTo(
                t: idThread?,
                colorA: idEventArg<idVec3?>?,
                alpha: idEventArg<Float?>?,
                time: idEventArg<Float?>?
            ) {
                val fadeColor = idVec4()
                val player: idPlayer?
                val color = idVec3(colorA.value)
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player != null) {
                    fadeColor.Set(color.oGet(0), color.oGet(1), color.oGet(2), alpha.value)
                    player.playerView.Fade(fadeColor, Math_h.SEC2MS(time.value).toInt())
                }
            }

            private fun Event_SetShaderParm(t: idThread?, parmnumA: idEventArg<Int?>?, value: idEventArg<Float?>?) {
                val parmnum: Int = parmnumA.value
                if (parmnum < 0 || parmnum >= RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                    t.Error("shader parm index (%d) out of range", parmnum)
                }
                Game_local.gameLocal.globalShaderParms[parmnum] = value.value
            }

            private fun Event_StartMusic(t: idThread?, text: idEventArg<String?>?) {
                Game_local.gameSoundWorld.PlayShaderDirectly(text.value)
            }

            private fun Event_Warning(t: idThread?, text: idEventArg<String?>?) {
                t.Warning("%s", text.value)
            }

            private fun Event_Error(t: idThread?, text: idEventArg<String?>?) {
                t.Error("%s", text.value)
            }

            private fun Event_StrLen(t: idThread?, string: idEventArg<String?>?) {
                val len: Int
                len = string.value.length
                ReturnInt(len)
            }

            private fun Event_StrLeft(t: idThread?, stringA: idEventArg<String?>?, numA: idEventArg<Int?>?) {
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

            private fun Event_StrRight(t: idThread?, stringA: idEventArg<String?>?, numA: idEventArg<Int?>?) {
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

            private fun Event_StrSkip(t: idThread?, stringA: idEventArg<String?>?, numA: idEventArg<Int?>?) {
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
                t: idThread?,
                stringA: idEventArg<String?>?,
                startA: idEventArg<Int?>?,
                numA: idEventArg<Int?>?
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

            private fun Event_StrToFloat(t: idThread?, string: idEventArg<String?>?) {
                val result: Float
                result = string.value.toFloat()
                ReturnFloat(result)
            }

            private fun Event_RadiusDamage(
                t: idThread?,
                origin: idEventArg<idVec3?>?,
                inflictor: idEventArg<idEntity?>?,
                attacker: idEventArg<idEntity?>?,
                ignore: idEventArg<idEntity?>?,
                damageDefName: idEventArg<String?>?,
                dmgPower: idEventArg<Float?>?
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

            private fun Event_CacheSoundShader(t: idThread?, soundName: idEventArg<String?>?) {
                DeclManager.declManager.FindSound(soundName.value)
            }

            private fun Event_DebugLine(
                t: idThread?,
                colorA: idEventArg<idVec3?>?,
                start: idEventArg<idVec3?>?,
                end: idEventArg<idVec3?>?,
                lifetime: idEventArg<Float?>?
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
                t: idThread?,
                colorA: idEventArg<idVec3?>?,
                start: idEventArg<idVec3?>?,
                end: idEventArg<idVec3?>?,
                size: idEventArg<Int?>?,
                lifetime: idEventArg<Float?>?
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
                t: idThread?,
                colorA: idEventArg<idVec3?>?,
                origin: idEventArg<idVec3?>?,
                dir: idEventArg<idVec3?>?,
                radius: idEventArg<Float?>?,
                numSteps: idEventArg<Int?>?,
                lifetime: idEventArg<Float?>?
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
                t: idThread?,
                colorA: idEventArg<idVec3?>?,
                mins: idEventArg<idVec3?>?,
                maxs: idEventArg<idVec3?>?,
                lifetime: idEventArg<Float?>?
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
                t: idThread?,
                text: idEventArg<String?>?,
                origin: idEventArg<idVec3?>?,
                scale: idEventArg<Float?>?,
                colorA: idEventArg<idVec3?>?,
                align: idEventArg<Int?>?,
                lifetime: idEventArg<Float?>?
            ) {
                val color = idVec3(colorA.value)
                Game_local.gameRenderWorld.DrawText(
                    text.value,
                    origin.value,
                    scale.value,
                    idVec4(color.x, color.y, color.z, 0.0f),
                    Game_local.gameLocal.GetLocalPlayer().viewAngles.ToMat3(),
                    align.value,
                    Math_h.SEC2MS(lifetime.value).toInt()
                )
            }

            fun GetThread(num: Int): idThread? {
                var i: Int
                val n: Int
                var thread: idThread?
                n = threadList.Num()
                i = 0
                while (i < n) {
                    thread = threadList.oGet(i)
                    if (thread.GetThreadNum() == num) {
                        return thread
                    }
                    i++
                }
                return null
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

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

            fun GetThreads(): idList<idThread?>? {
                return threadList
            }

            fun KillThread(name: String?) {
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
                num = threadList.Num()
                i = 0
                while (i < num) {
                    thread = threadList.oGet(i)
                    if (0 == idStr.Companion.Cmpn(thread.GetThreadName(), name, len)) {
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
                    currentThread.GetThreadNum()
                } else {
                    0
                }
            }

            fun BeginMultiFrameEvent(ent: idEntity?, event: idEventDef?): Boolean {
                if (null == currentThread) {
                    idGameLocal.Companion.Error("idThread::BeginMultiFrameEvent called without a current thread")
                }
                return currentThread.interpreter.BeginMultiFrameEvent(ent, event)
            }

            fun EndMultiFrameEvent(ent: idEntity?, event: idEventDef?) {
                if (null == currentThread) {
                    idGameLocal.Companion.Error("idThread::EndMultiFrameEvent called without a current thread")
                }
                currentThread.interpreter.EndMultiFrameEvent(ent, event)
            }

            fun ReturnString(text: String?) {
                Game_local.gameLocal.program.ReturnString(text)
            }

            fun ReturnString(text: idStr?) {
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

            fun delete(thread: idThread?) {
                thread._deconstructor()
            }

            init {
                eventCallbacks.putAll(idClass.Companion.getEventCallBacks())
                eventCallbacks[Script_Thread.EV_Thread_Execute] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_Execute() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_TerminateThread] =
                    eventCallback_t1<idThread?> { t: T?, num: idEventArg<*>? ->
                        Event_TerminateThread(
                            neo.Game.Script.t,
                            neo.Game.Script.num
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Pause] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_Pause() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Wait] =
                    eventCallback_t1<idThread?> { t: T?, time: idEventArg<*>? ->
                        Event_Wait(
                            neo.Game.Script.t,
                            neo.Game.Script.time
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_WaitFrame] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_WaitFrame() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_WaitFor] =
                    eventCallback_t1<idThread?> { t: T?, e: idEventArg<*>? ->
                        Event_WaitFor(
                            neo.Game.Script.t,
                            neo.Game.Script.e
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_WaitForThread] =
                    eventCallback_t1<idThread?> { t: T?, num: idEventArg<*>? ->
                        Event_WaitForThread(
                            neo.Game.Script.t,
                            neo.Game.Script.num
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Print] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_Print(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_PrintLn] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_PrintLn(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Say] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_Say(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Assert] =
                    eventCallback_t1<idThread?> { t: T?, value: idEventArg<*>? ->
                        Event_Assert(
                            neo.Game.Script.t,
                            neo.Game.Script.value
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Trigger] =
                    eventCallback_t1<idThread?> { t: T?, e: idEventArg<*>? ->
                        Event_Trigger(
                            neo.Game.Script.t,
                            neo.Game.Script.e
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SetCvar] =
                    eventCallback_t2<idThread?> { t: T?, name: idEventArg<*>? ->
                        Event_SetCvar(
                            neo.Game.Script.t,
                            neo.Game.Script.name
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetCvar] =
                    eventCallback_t1<idThread?> { t: T?, name: idEventArg<*>? ->
                        Event_GetCvar(
                            neo.Game.Script.t,
                            neo.Game.Script.name
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Random] =
                    eventCallback_t1<idThread?> { t: T?, range: idEventArg<*>? ->
                        Event_Random(
                            neo.Game.Script.t,
                            neo.Game.Script.range
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTime] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTime() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_KillThread] =
                    eventCallback_t1<idThread?> { t: T?, name: idEventArg<*>? ->
                        Event_KillThread(
                            neo.Game.Script.t,
                            neo.Game.Script.name
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SetThreadName] =
                    eventCallback_t1<idThread?> { t: T?, name: idEventArg<*>? ->
                        Event_SetThreadName(
                            neo.Game.Script.t,
                            neo.Game.Script.name
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetEntity] =
                    eventCallback_t1<idThread?> { t: T?, n: idEventArg<*>? ->
                        Event_GetEntity(
                            neo.Game.Script.t,
                            neo.Game.Script.n
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Spawn] =
                    eventCallback_t1<idThread?> { t: T?, classname: idEventArg<*>? ->
                        Event_Spawn(
                            neo.Game.Script.t,
                            neo.Game.Script.classname
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_CopySpawnArgs] =
                    eventCallback_t1<idThread?> { t: T?, ent: idEventArg<*>? ->
                        Event_CopySpawnArgs(
                            neo.Game.Script.t,
                            neo.Game.Script.ent
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SetSpawnArg] =
                    eventCallback_t2<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_SetSpawnArg(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SpawnString] =
                    eventCallback_t2<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_SpawnString(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SpawnFloat] =
                    eventCallback_t2<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_SpawnFloat(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SpawnVector] =
                    eventCallback_t2<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_SpawnVector(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_ClearPersistantArgs] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_ClearPersistantArgs() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SetPersistantArg] =
                    eventCallback_t2<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_SetPersistantArg(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetPersistantString] =
                    eventCallback_t1<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_GetPersistantString(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetPersistantFloat] =
                    eventCallback_t1<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_GetPersistantFloat(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetPersistantVector] =
                    eventCallback_t1<idThread?> { t: T?, key: idEventArg<*>? ->
                        Event_GetPersistantVector(
                            neo.Game.Script.t,
                            neo.Game.Script.key
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_AngToForward] =
                    eventCallback_t1<idThread?> { t: T?, ang: idEventArg<*>? ->
                        Event_AngToForward(
                            neo.Game.Script.t,
                            neo.Game.Script.ang
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_AngToRight] =
                    eventCallback_t1<idThread?> { t: T?, ang: idEventArg<*>? ->
                        Event_AngToRight(
                            neo.Game.Script.t,
                            neo.Game.Script.ang
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_AngToUp] =
                    eventCallback_t1<idThread?> { t: T?, ang: idEventArg<*>? ->
                        Event_AngToUp(
                            neo.Game.Script.t,
                            neo.Game.Script.ang
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Sine] =
                    eventCallback_t1<idThread?> { t: T?, angle: idEventArg<*>? ->
                        Event_GetSine(
                            neo.Game.Script.t,
                            neo.Game.Script.angle
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Cosine] =
                    eventCallback_t1<idThread?> { t: T?, angle: idEventArg<*>? ->
                        Event_GetCosine(
                            neo.Game.Script.t,
                            neo.Game.Script.angle
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SquareRoot] =
                    eventCallback_t1<idThread?> { t: T?, theSquare: idEventArg<*>? ->
                        Event_GetSquareRoot(
                            neo.Game.Script.t,
                            neo.Game.Script.theSquare
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Normalize] =
                    eventCallback_t1<idThread?> { t: T?, vec: idEventArg<*>? ->
                        Event_VecNormalize(
                            neo.Game.Script.t,
                            neo.Game.Script.vec
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_VecLength] =
                    eventCallback_t1<idThread?> { t: T?, vec: idEventArg<*>? ->
                        Event_VecLength(
                            neo.Game.Script.t,
                            neo.Game.Script.vec
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_VecDotProduct] =
                    eventCallback_t2<idThread?> { t: T?, vec1: idEventArg<*>? ->
                        Event_VecDotProduct(
                            neo.Game.Script.t,
                            neo.Game.Script.vec1
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_VecCrossProduct] =
                    eventCallback_t2<idThread?> { t: T?, vec1: idEventArg<*>? ->
                        Event_VecCrossProduct(
                            neo.Game.Script.t,
                            neo.Game.Script.vec1
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_VecToAngles] =
                    eventCallback_t1<idThread?> { t: T?, vec: idEventArg<*>? ->
                        Event_VecToAngles(
                            neo.Game.Script.t,
                            neo.Game.Script.vec
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_OnSignal] =
                    eventCallback_t3<idThread?> { t: T?, s: idEventArg<*>? ->
                        Event_OnSignal(
                            neo.Game.Script.t,
                            neo.Game.Script.s
                        )
                    } as eventCallback_t3<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_ClearSignal] =
                    eventCallback_t2<idThread?> { t: T?, s: idEventArg<*>? ->
                        Event_ClearSignalThread(
                            neo.Game.Script.t,
                            neo.Game.Script.s
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_SetCamera] =
                    eventCallback_t1<idThread?> { t: T?, e: idEventArg<*>? ->
                        Event_SetCamera(
                            neo.Game.Script.t,
                            neo.Game.Script.e
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_FirstPerson] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_FirstPerson() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Trace] =
                    eventCallback_t6<idThread?> { t: T?, s: idEventArg<*>? ->
                        Event_Trace(
                            neo.Game.Script.t,
                            neo.Game.Script.s
                        )
                    } as eventCallback_t6<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_TracePoint] =
                    eventCallback_t4<idThread?> { t: T?, startA: idEventArg<*>? ->
                        Event_TracePoint(
                            neo.Game.Script.t,
                            neo.Game.Script.startA
                        )
                    } as eventCallback_t4<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceFraction] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceFraction() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceEndPos] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceEndPos() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceNormal] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceNormal() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceEntity] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceEntity() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceJoint] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceJoint() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTraceBody] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTraceBody() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_FadeIn] =
                    eventCallback_t2<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_FadeIn(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_FadeOut] =
                    eventCallback_t2<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_FadeOut(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_FadeTo] =
                    eventCallback_t3<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_FadeTo(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t3<idThread?>
                eventCallbacks[Entity.EV_SetShaderParm] =
                    eventCallback_t2<idThread?> { t: T?, parmnumA: idEventArg<*>? ->
                        Event_SetShaderParm(
                            neo.Game.Script.t,
                            neo.Game.Script.parmnumA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StartMusic] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_StartMusic(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Warning] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_Warning(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_Error] =
                    eventCallback_t1<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_Error(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrLen] =
                    eventCallback_t1<idThread?> { t: T?, string: idEventArg<*>? ->
                        Event_StrLen(
                            neo.Game.Script.t,
                            neo.Game.Script.string
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrLeft] =
                    eventCallback_t2<idThread?> { t: T?, stringA: idEventArg<*>? ->
                        Event_StrLeft(
                            neo.Game.Script.t,
                            neo.Game.Script.stringA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrRight] =
                    eventCallback_t2<idThread?> { t: T?, stringA: idEventArg<*>? ->
                        Event_StrRight(
                            neo.Game.Script.t,
                            neo.Game.Script.stringA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrSkip] =
                    eventCallback_t2<idThread?> { t: T?, stringA: idEventArg<*>? ->
                        Event_StrSkip(
                            neo.Game.Script.t,
                            neo.Game.Script.stringA
                        )
                    } as eventCallback_t2<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrMid] =
                    eventCallback_t3<idThread?> { t: T?, stringA: idEventArg<*>? ->
                        Event_StrMid(
                            neo.Game.Script.t,
                            neo.Game.Script.stringA
                        )
                    } as eventCallback_t3<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_StrToFloat] =
                    eventCallback_t1<idThread?> { t: T?, string: idEventArg<*>? ->
                        Event_StrToFloat(
                            neo.Game.Script.t,
                            neo.Game.Script.string
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_RadiusDamage] =
                    eventCallback_t6<idThread?> { t: T?, origin: idEventArg<*>? ->
                        Event_RadiusDamage(
                            neo.Game.Script.t,
                            neo.Game.Script.origin
                        )
                    } as eventCallback_t6<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_IsClient] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_IsClient() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_IsMultiplayer] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_IsMultiplayer() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetFrameTime] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetFrameTime() } as eventCallback_t0<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_GetTicsPerSecond] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_GetTicsPerSecond() } as eventCallback_t0<idThread?>
                eventCallbacks[Entity.EV_CacheSoundShader] =
                    eventCallback_t1<idThread?> { t: T?, soundName: idEventArg<*>? ->
                        Event_CacheSoundShader(
                            neo.Game.Script.t,
                            neo.Game.Script.soundName
                        )
                    } as eventCallback_t1<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_DebugLine] =
                    eventCallback_t4<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_DebugLine(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t4<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_DebugArrow] =
                    eventCallback_t5<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_DebugArrow(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t5<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_DebugCircle] =
                    eventCallback_t6<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_DebugCircle(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t6<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_DebugBounds] =
                    eventCallback_t4<idThread?> { t: T?, colorA: idEventArg<*>? ->
                        Event_DebugBounds(
                            neo.Game.Script.t,
                            neo.Game.Script.colorA
                        )
                    } as eventCallback_t4<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_DrawText] =
                    eventCallback_t6<idThread?> { t: T?, text: idEventArg<*>? ->
                        Event_DrawText(
                            neo.Game.Script.t,
                            neo.Game.Script.text
                        )
                    } as eventCallback_t6<idThread?>
                eventCallbacks[Script_Thread.EV_Thread_InfluenceActive] =
                    eventCallback_t0<idThread?> { obj: T? -> neo.Game.Script.obj.Event_InfluenceActive() } as eventCallback_t0<idThread?>
            }
        }

        private var creationTime = 0
        private val interpreter: idInterpreter? = idInterpreter()

        //
        private var lastExecuteTime = 0

        //
        private var manualControl = false

        //
        private var spawnArgs: idDict? = null
        private val threadName: idStr? = idStr()

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

        constructor(self: idEntity?, func: function_t?) {
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

        constructor(func: function_t?) {
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

        constructor(source: idInterpreter?, func: function_t?, args: Int) {
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

        constructor(source: idInterpreter?, self: idEntity?, func: function_t?, args: Int) {
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
            ReturnFloat(Math_h.MS2SEC(idGameLocal.Companion.msec.toFloat()))
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
            threadList.Remove(this)
            n = threadList.Num()
            i = 0
            while (i < n) {
                thread = threadList.oGet(i)
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
        override fun Save(savefile: idSaveGame?) {                // archives object for save game file

            // We will check on restore that threadNum is still the same,
            // threads should have been restored in the same order.
            savefile.WriteInt(threadNum)
            savefile.WriteObject(waitingForThread)
            savefile.WriteInt(waitingFor)
            savefile.WriteInt(waitingUntil)
            interpreter.Save(savefile)
            savefile.WriteDict(spawnArgs)
            savefile.WriteString(threadName)
            savefile.WriteInt(lastExecuteTime)
            savefile.WriteInt(creationTime)
            savefile.WriteBool(manualControl)
        }

        override fun Restore(savefile: idRestoreGame?) {                // unarchives object from save game file
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
                waitingUntil = Game_local.gameLocal.time + idGameLocal.Companion.msec
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
                        waitingForThread.GetThreadNum(),
                        waitingForThread.GetThreadName()
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

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun  /*idTypeInfo*/GetType(): Class<*>? {
            return javaClass
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
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
                    PostEventMS(Script_Thread.EV_Thread_Execute, waitingUntil - lastExecuteTime)
                } else if (interpreter.MultiFrameEventInProgress()) {
                    PostEventMS(Script_Thread.EV_Thread_Execute, idGameLocal.Companion.msec)
                }
            }
            currentThread = oldThread
            return done
        }

        fun ManualControl() {
            manualControl = true
            CancelEvents(Script_Thread.EV_Thread_Execute)
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
            return waitingFor == obj.entityNumber
        }

        fun ObjectMoveDone(obj: idEntity?) {
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
            CancelEvents(Script_Thread.EV_Thread_Execute)
            if (Game_local.gameLocal.time <= 0) {
                delay++
            }
            PostEventMS(Script_Thread.EV_Thread_Execute, delay)
        }

        fun Start(): Boolean {
            val result: Boolean
            CancelEvents(Script_Thread.EV_Thread_Execute)
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
            threadName.oSet(name)
        }

        fun GetThreadName(): String? {
            return threadName.toString()
        }

        fun Error(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            interpreter.Error(text)
        }

        fun Warning(fmt: String?, vararg objects: Any?) { // const id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            interpreter.Warning(text)
        }

        /*
         ================
         idThread::ListThreads_f
         ================
         */
        class ListThreads_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                val n: Int
                n = threadList.Num()
                i = 0
                while (i < n) {

                    //threadList[ i ].DisplayInfo();
                    Game_local.gameLocal.Printf(
                        "%3d: %-20s : %s(%d)\n",
                        threadList.oGet(i).threadNum,
                        threadList.oGet(i).threadName,
                        threadList.oGet(i).interpreter.CurrentFile(),
                        threadList.oGet(i).interpreter.CurrentLine()
                    )
                    i++
                }
                Game_local.gameLocal.Printf("%d active threads\n\n", n)
            }

            companion object {
                private val instance: cmdFunction_t? = ListThreads_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }
    }
}