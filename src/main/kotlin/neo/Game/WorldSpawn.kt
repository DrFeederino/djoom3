package neo.Game

import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.framework.FileSystem_h
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr

/*
 game_worldspawn.cpp

 Worldspawn class.  Each map has one worldspawn which handles global spawnargs.

 */
class WorldSpawn {
    /*
     ===============================================================================

     World entity.

     Every map should have exactly one worldspawn.

     ===============================================================================
     */
    class idWorldspawn : idEntity() {
        companion object {
            //	CLASS_PROTOTYPE( idWorldspawn );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Class.EV_Remove] =
                    eventCallback_t0<idWorldspawn?> { obj: T? -> neo.Game.obj.Event_Remove() } as eventCallback_t0<idWorldspawn?>
                eventCallbacks[Class.EV_SafeRemove] =
                    eventCallback_t0<idWorldspawn?> { obj: T? -> neo.Game.obj.Event_Remove() } as eventCallback_t0<idWorldspawn?>
            }
        }

        //					~idWorldspawn();
        override fun Spawn() {
            super.Spawn()
            val scriptname: idStr
            var thread: idThread
            var func: function_t?
            var kv: idKeyValue?
            assert(Game_local.gameLocal.world == null)
            Game_local.gameLocal.world = this
            SysCvar.g_gravity.SetFloat(spawnArgs.GetFloat("gravity", Str.va("%f", Game_local.DEFAULT_GRAVITY)))

            // disable stamina on hell levels
            if (spawnArgs.GetBool("no_stamina")) {
                SysCvar.pm_stamina.SetFloat(0.0f)
            }

            // load script
            scriptname = idStr(Game_local.gameLocal.GetMapName())
            scriptname.SetFileExtension(".script")
            if (FileSystem_h.fileSystem.ReadFile(scriptname.toString(), null, null) > 0) {
                Game_local.gameLocal.program.CompileFile(scriptname.toString())

                // call the main function by default
                func = Game_local.gameLocal.program.FindFunction("main")
                if (func != null) {
                    thread = idThread(func)
                    thread.DelayedStart(0)
                }
            }

            // call any functions specified in worldspawn
            kv = spawnArgs.MatchPrefix("call")
            while (kv != null) {
                func = Game_local.gameLocal.program.FindFunction(kv.GetValue().toString())
                if (func == null) {
                    idGameLocal.Companion.Error(
                        "Function '%s' not found in script for '%s' key on worldspawn",
                        kv.GetValue(),
                        kv.GetKey()
                    )
                }
                thread = idThread(func)
                thread.DelayedStart(0)
                kv = spawnArgs.MatchPrefix("call", kv)
            }
        }

        fun Save(savefile: idRestoreGame?) {}
        override fun Restore(savefile: idRestoreGame?) {
            assert(Game_local.gameLocal.world == this)
            SysCvar.g_gravity.SetFloat(spawnArgs.GetFloat("gravity", Str.va("%f", Game_local.DEFAULT_GRAVITY)))

            // disable stamina on hell levels
            if (spawnArgs.GetBool("no_stamina")) {
                SysCvar.pm_stamina.SetFloat(0.0f)
            }
        }

        override fun Event_Remove() {
            idGameLocal.Companion.Error("Tried to remove world")
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }
}