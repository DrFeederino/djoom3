package neo.Game

import neo.CM.CollisionModel.trace_s
import neo.Game.*
import neo.Game.AFEntity.idAFEntity_Gibbable
import neo.Game.AI.AI.idAI
import neo.Game.AI.AI_Events
import neo.Game.Actor.idActor
import neo.Game.Animation.Anim
import neo.Game.Camera.idCamera
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.eventCallback_t4
import neo.Game.GameSys.Class.eventCallback_t6
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idEntityPtr
import neo.Game.Game_local.idGameLocal
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force_Field.forceFieldApplyType
import neo.Game.Physics.Force_Field.idForce_Field
import neo.Game.Physics.Force_Spring.idForce_Spring
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.*
import neo.Renderer.Model_liquid.idRenderModelLiquid
import neo.Renderer.RenderWorld.portalConnection_t
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Lib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.*

/**
 *
 */
object Misc {
    val EV_AnimDone: idEventDef? = idEventDef("<AnimDone>", "d")

    /*
     ===============================================================================

     idAnimated

     ===============================================================================
     */
    val EV_Animated_Start: idEventDef? = idEventDef("<start>")
    val EV_LaunchMissiles: idEventDef? = idEventDef("launchMissiles", "ssssdf")
    val EV_LaunchMissilesUpdate: idEventDef? = idEventDef("<launchMissiles>", "dddd")

    /*
     ===============================================================================

     idFuncRadioChatter

     ===============================================================================
     */
    val EV_ResetRadioHud: idEventDef? = idEventDef("<resetradiohud>", "e")

    /*
     ===============================================================================

     Object that fires targets and changes shader parms when damaged.

     ===============================================================================
     */
    val EV_RestoreDamagable: idEventDef? = idEventDef("<RestoreDamagable>")

    /*
     ===============================================================================

     idDamagable
	
     ===============================================================================
     */
    /*
     ===============================================================================

     idFuncSplat

     ===============================================================================
     */
    val EV_Splat: idEventDef? = idEventDef("<Splat>")
    val EV_StartRagdoll: idEventDef? = idEventDef("startRagdoll")

    /*
     ===============================================================================

     Potential spawning position for players.
     The first time a player enters the game, they will be at an 'initial' spot.
     Targets will be fired when someone spawns in on them.

     When triggered, will cause player to be teleported to spawn spot.

     ===============================================================================
     */
    val EV_TeleportStage: idEventDef? = idEventDef("<TeleportStage>", "e")

    /*
     ===============================================================================

     idForceField

     ===============================================================================
     */
    val EV_Toggle: idEventDef? = idEventDef("Toggle", null)

    /*
     ===============================================================================

     idSpawnableEntity

     A simple, spawnable entity with a model and no functionable ability of it's own.
     For example, it can be used as a placeholder during development, for marking
     locations on maps for script, or for simple placed models without any behavior
     that can be bound to other entities.  Should not be subclassed.
     ===============================================================================
     */
    class idSpawnableEntity : idEntity() {

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idPlayerStart

     ===============================================================================
     */
    class idPlayerStart     //
    //
        : idEntity() {
        companion object {
            // enum {
            val EVENT_TELEPORTPLAYER: Int = idEntity.Companion.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = EVENT_TELEPORTPLAYER + 1

            // public 	CLASS_PROTOTYPE( idPlayerStart );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            private fun Event_TeleportPlayer(p: idPlayerStart?, activator: idEventArg<idEntity?>?) {
                val player: idPlayer?
                player = if (activator.value is idPlayer) {
                    activator.value as idPlayer?
                } else {
                    Game_local.gameLocal.GetLocalPlayer()
                }
                if (player != null) {
                    if (p.spawnArgs.GetBool("visualFx")) {
                        p.teleportStage = 0
                        p.Event_TeleportStage(player)
                    } else {
                        if (Game_local.gameLocal.isServer) {
                            val msg = idBitMsg()
                            val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                            msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                            msg.BeginWriting()
                            msg.WriteBits(player.entityNumber, Game_local.GENTITYNUM_BITS)
                            p.ServerSendEvent(EVENT_TELEPORTPLAYER, msg, false, -1)
                        }
                        p.TeleportPlayer(player)
                    }
                }
            }

            /*
         ===============
         idPlayerStart::Event_TeleportStage

         FIXME: add functionality to fx system ( could be done with player scripting too )
         ================
         */
            private fun Event_TeleportStage(p: idPlayerStart?, _player: idEventArg<idEntity?>?) {
                val player: idPlayer?
                if (_player.value !is idPlayer) {
                    Common.common.Warning("idPlayerStart::Event_TeleportStage: entity is not an idPlayer\n")
                    return
                }
                player = _player.value as idPlayer?
                val teleportDelay = p.spawnArgs.GetFloat("teleportDelay")
                when (p.teleportStage) {
                    0 -> {
                        player.playerView.Flash(Lib.Companion.colorWhite, 125)
                        player.SetInfluenceLevel(Player.INFLUENCE_LEVEL3)
                        player.SetInfluenceView(p.spawnArgs.GetString("mtr_teleportFx"), null, 0.0f, null)
                        Game_local.gameSoundWorld.FadeSoundClasses(0, -20.0f, teleportDelay)
                        player.StartSound("snd_teleport_start", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false, null)
                        p.teleportStage++
                        p.PostEventSec(Misc.EV_TeleportStage, teleportDelay, player)
                    }
                    1 -> {
                        Game_local.gameSoundWorld.FadeSoundClasses(0, 0.0f, 0.25f)
                        p.teleportStage++
                        p.PostEventSec(Misc.EV_TeleportStage, 0.25f, player)
                    }
                    2 -> {
                        player.SetInfluenceView(null, null, 0.0f, null)
                        p.TeleportPlayer(player)
                        player.StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY2), false)
                        player.SetInfluenceLevel(Player.INFLUENCE_NONE)
                        p.teleportStage = 0
                    }
                    else -> {}
                }
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idPlayerStart?> { p: T?, activator: idEventArg<*>? ->
                        Event_TeleportPlayer(
                            neo.Game.p,
                            neo.Game.activator
                        )
                    } as eventCallback_t1<idPlayerStart?>
                eventCallbacks[Misc.EV_TeleportStage] =
                    eventCallback_t1<idPlayerStart?> { p: T?, _player: idEventArg<*>? ->
                        Event_TeleportStage(
                            neo.Game.p,
                            neo.Game._player
                        )
                    } as eventCallback_t1<idPlayerStart?>
            }
        }

        // };
        private var teleportStage = 0
        override fun Spawn() {
            super.Spawn()
            teleportStage = 0
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(teleportStage)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val teleportStage = CInt()
            savefile.ReadInt(teleportStage)
            this.teleportStage = teleportStage.getVal()
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
            val entityNumber: Int
            return when (event) {
                EVENT_TELEPORTPLAYER -> {
                    entityNumber = msg.ReadBits(Game_local.GENTITYNUM_BITS)
                    val player = Game_local.gameLocal.entities[entityNumber] as idPlayer
                    if (player != null && player is idPlayer) {
                        Event_TeleportPlayer(player)
                    }
                    true
                }
                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        private fun Event_TeleportPlayer(activator: idEntity?) {
            Event_TeleportPlayer(this, idEventArg.Companion.toArg(activator))
        }

        private fun Event_TeleportStage(_player: idEntity?) {
            Event_TeleportStage(this, idEventArg.Companion.toArg(_player))
        }

        private fun TeleportPlayer(player: idPlayer?) {
            val pushVel = spawnArgs.GetFloat("push", "300")
            val f = spawnArgs.GetFloat("visualEffect", "0")
            val viewName = spawnArgs.GetString("visualView", "")
            val ent =
                if (viewName != null) Game_local.gameLocal.FindEntity(viewName) else null //TODO:the standard C++ boolean checks if the bytes are switched on, which in the case of String means NOT NULL AND NOT EMPTY.
            if (f != 0f && ent != null) {
                // place in private camera view for some time
                // the entity needs to teleport to where the camera view is to have the PVS right
                player.Teleport(ent.GetPhysics().GetOrigin(), Angles.getAng_zero(), this)
                player.StartSound("snd_teleport_enter", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)
                player.SetPrivateCameraView(ent as idCamera?)
                // the player entity knows where to spawn from the previous Teleport call
                if (!Game_local.gameLocal.isClient) {
                    player.PostEventSec(Player.EV_Player_ExitTeleporter, f)
                }
            } else {
                // direct to exit, Teleport will take care of the killbox
                player.Teleport(GetPhysics().GetOrigin(), GetPhysics().GetAxis().ToAngles(), null)

                // multiplayer hijacked this entity, so only push the player in multiplayer
                if (Game_local.gameLocal.isMultiplayer) {
                    player.GetPhysics().SetLinearVelocity(GetPhysics().GetAxis().oGet(0).oMultiply(pushVel))
                }
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     Non-displayed entity used to activate triggers when it touches them.
     Bind to a mover to have the mover activate a trigger as it moves.
     When target by triggers, activating the trigger will toggle the
     activator on and off. Check "start_off" to have it spawn disabled.

     ===============================================================================
     */
    class idActivator : idEntity() {
        companion object {
            // public 	CLASS_PROTOTYPE( idActivator );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            //
            //
            private fun Event_Activate(a: idActivator?, activator: idEventArg<idEntity?>?) {
                if (a.thinkFlags and Entity.TH_THINK != 0) {
                    a.BecomeInactive(Entity.TH_THINK)
                } else {
                    a.BecomeActive(Entity.TH_THINK)
                }
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idActivator?> { a: T?, activator: idEventArg<*>? ->
                        Event_Activate(
                            neo.Game.a,
                            neo.Game.activator
                        )
                    } as eventCallback_t1<idActivator?>
            }
        }

        private val stay_on: CBool? = CBool(false)
        override fun Spawn() {
            super.Spawn()
            val start_off = CBool(false)
            spawnArgs.GetBool("stay_on", "0", stay_on)
            spawnArgs.GetBool("start_off", "0", start_off)
            GetPhysics().SetClipBox(idBounds(Vector.getVec3_origin()).Expand(4f), 1.0f)
            GetPhysics().SetContents(0)
            if (!start_off.isVal) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(stay_on.isVal())
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadBool(stay_on)
            if (stay_on.isVal()) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun Think() {
            RunPhysics()
            if (thinkFlags and Entity.TH_THINK != 0) {
                if (TouchTriggers()) {
                    if (!stay_on.isVal()) {
                        BecomeInactive(Entity.TH_THINK)
                    }
                }
            }
            Present()
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     Path entities for monsters to follow.

     ===============================================================================
     */
    /*
     ===============================================================================

     idPathCorner

     ===============================================================================
     */
    class idPathCorner : idEntity() {
        companion object {
            // public 	CLASS_PROTOTYPE( idPathCorner );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun DrawDebugInfo() {
                var ent: idEntity?
                val bnds = idBounds(idVec3(-4.0f, -4.0f, -8.0f), idVec3(4.0f, 4.0f, 64.0f))
                ent = Game_local.gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    if (ent !is idPathCorner) {
                        ent = ent.spawnNode.Next()
                        continue
                    }
                    val org = idVec3(ent.GetPhysics().GetOrigin())
                    Game_local.gameRenderWorld.DebugBounds(Lib.Companion.colorRed, bnds, org, 0)
                    ent = ent.spawnNode.Next()
                }
            }

            fun RandomPath(source: idEntity?, ignore: idEntity?): idPathCorner? {
                var i: Int
                val num: Int
                val which: Int
                var ent: idEntity?
                val path = arrayOfNulls<idPathCorner?>(Game_local.MAX_GENTITIES)
                num = 0
                i = 0
                while (i < source.targets.Num()) {
                    ent = source.targets.oGet(i).GetEntity()
                    if (ent != null && ent !== ignore && ent is idPathCorner) {
                        path[num++] = ent as idPathCorner?
                        if (num >= Game_local.MAX_GENTITIES) {
                            break
                        }
                    }
                    i++
                }
                if (0 == num) {
                    return null
                }
                which = Game_local.gameLocal.random.RandomInt(num.toDouble())
                return path[which]
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[AI_Events.AI_RandomPath] =
                    eventCallback_t0<idPathCorner?> { obj: T? -> neo.Game.obj.Event_RandomPath() } as eventCallback_t0<idPathCorner?>
            }
        }

        private fun Event_RandomPath() {
            val path: idPathCorner?
            path = RandomPath(this, null)
            idThread.Companion.ReturnEntity(path)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    class idDamagable : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idDamagable );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            private fun Event_BecomeBroken(d: idDamagable?, activator: idEventArg<idEntity?>?) {
                d.BecomeBroken(activator.value)
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idDamagable?> { d: T?, activator: idEventArg<*>? ->
                        Event_BecomeBroken(
                            neo.Game.d,
                            neo.Game.activator
                        )
                    } as eventCallback_t1<idDamagable?>
                eventCallbacks[Misc.EV_RestoreDamagable] =
                    eventCallback_t0<idDamagable?> { obj: T? -> neo.Game.obj.Event_RestoreDamagable() } as eventCallback_t0<idDamagable?>
            }
        }

        private val count: CInt? = CInt()
        private val nextTriggerTime: CInt? = CInt()
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(count.getVal())
            savefile.WriteInt(nextTriggerTime.getVal())
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadInt(count)
            savefile.ReadInt(nextTriggerTime)
        }

        override fun Spawn() {
            super.Spawn()
            val broken = idStr()
            health = spawnArgs.GetInt("health", "5")
            spawnArgs.GetInt("count", "1", count)
            nextTriggerTime.setVal(0)

            // make sure the model gets cached
            spawnArgs.GetString("broken", "", broken)
            if (broken.Length() != 0 && TempDump.NOT(ModelManager.renderModelManager.CheckModel(broken.toString()))) {
                idGameLocal.Companion.Error(
                    "idDamagable '%s' at (%s): cannot load broken model '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    broken
                )
            }
            fl.takedamage = true
            GetPhysics().SetContents(Material.CONTENTS_SOLID)
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3?, location: Int) {
            if (Game_local.gameLocal.time < nextTriggerTime.getVal()) {
                health += damage
                return
            }
            BecomeBroken(attacker)
        }

        private fun BecomeBroken(activator: idEntity?) {
            val forceState = CFloat()
            val numStates = CInt()
            val cycle = CInt()
            val wait = CFloat()
            if (Game_local.gameLocal.time < nextTriggerTime.getVal()) {
                return
            }
            spawnArgs.GetFloat("wait", "0.1", wait)
            nextTriggerTime.setVal((Game_local.gameLocal.time + Math_h.SEC2MS(wait.getVal())).toInt())
            if (count.getVal() > 0) {
                count.decrement()
                if (0 == count.getVal()) {
                    fl.takedamage = false
                } else {
                    health = spawnArgs.GetInt("health", "5")
                }
            }
            val broken = idStr()
            spawnArgs.GetString("broken", "", broken)
            if (broken.Length() != 0) {
                SetModel(broken.toString())
            }

            // offset the start time of the shader to sync it to the gameLocal time
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            spawnArgs.GetInt("numstates", "1", numStates)
            spawnArgs.GetInt("cycle", "0", cycle)
            spawnArgs.GetFloat("forcestate", "0", forceState)

            // set the state parm
            if (cycle.getVal() != 0) {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE]++
                if (renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] > numStates.getVal()) {
                    renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] = 0
                }
            } else if (forceState.getVal() != 0f) {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] = forceState.getVal()
            } else {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] =
                    Game_local.gameLocal.random.RandomInt(numStates.getVal().toDouble()) + 1
            }
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            ActivateTargets(activator)
            if (spawnArgs.GetBool("hideWhenBroken")) {
                Hide()
                PostEventMS(Misc.EV_RestoreDamagable, nextTriggerTime.getVal() - Game_local.gameLocal.time)
                BecomeActive(Entity.TH_THINK)
            }
        }

        private fun Event_RestoreDamagable() {
            health = spawnArgs.GetInt("health", "5")
            Show()
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            count.setVal(0)
            nextTriggerTime.setVal(0)
        }
    }

    /*
     ===============================================================================

     Hidden object that explodes when activated

     ===============================================================================
     */
    /*
     ===============================================================================

     idExplodable

     ===============================================================================
     */
    class idExplodable : idEntity() {
        companion object {
            //	CLASS_PROTOTYPE( idExplodable );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            private fun Event_Explode(e: idExplodable?, activator: idEventArg<idEntity?>?) {
                val temp = arrayOf<String?>(null)
                if (e.spawnArgs.GetString("def_damage", "damage_explosion", temp)) {
                    Game_local.gameLocal.RadiusDamage(
                        e.GetPhysics().GetOrigin(),
                        activator.value,
                        activator.value,
                        e,
                        e,
                        temp[0]
                    )
                }
                e.StartSound("snd_explode", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)

                // Show() calls UpdateVisuals, so we don't need to call it ourselves after setting the shaderParms
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] = 1.0f
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
                e.renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = 0.0f
                e.Show()
                e.PostEventMS(Class.EV_Remove, 2000)
                e.ActivateTargets(activator.value)
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idExplodable?> { e: T?, activator: idEventArg<*>? ->
                        Event_Explode(
                            neo.Game.e,
                            neo.Game.activator
                        )
                    } as eventCallback_t1<idExplodable?>
            }
        }

        override fun Spawn() {
            super.Spawn()
            Hide()
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idSpring

     ===============================================================================
     */
    class idSpring : idEntity() {
        companion object {
            //	CLASS_PROTOTYPE( idSpring );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_PostSpawn] =
                    eventCallback_t0<idSpring?> { obj: T? -> neo.Game.obj.Event_LinkSpring() } as eventCallback_t0<idSpring?>
            }
        }

        private val id1: CInt? = CInt()
        private val id2: CInt? = CInt()
        private var ent1: idEntity? = null
        private var ent2: idEntity? = null
        private val p1: idVec3? = idVec3()
        private val p2: idVec3? = idVec3()
        private val spring: idForce_Spring? = null
        override fun Spawn() {
            super.Spawn()
            val Kstretch = CFloat()
            val damping = CFloat()
            val restLength = CFloat()
            spawnArgs.GetInt("id1", "0", id1)
            spawnArgs.GetInt("id2", "0", id2)
            spawnArgs.GetVector("point1", "0 0 0", p1)
            spawnArgs.GetVector("point2", "0 0 0", p2)
            spawnArgs.GetFloat("constant", "100.0f", Kstretch)
            spawnArgs.GetFloat("damping", "10.0f", damping)
            spawnArgs.GetFloat("restlength", "0.0f", restLength)
            spring.InitSpring(Kstretch.getVal(), 0.0f, damping.getVal(), restLength.getVal())
            ent2 = null
            ent1 = ent2
            PostEventMS(Entity.EV_PostSpawn, 0)
        }

        override fun Think() {
            val start = idVec3()
            val end = idVec3()
            val origin = idVec3()
            var axis: idMat3?

            // run physics
            RunPhysics()
            if (thinkFlags and Entity.TH_THINK != 0) {
                // evaluate force
                spring.Evaluate(Game_local.gameLocal.time)
                start.oSet(p1)
                if (ent1.GetPhysics() != null) {
                    axis = ent1.GetPhysics().GetAxis()
                    origin.oSet(ent1.GetPhysics().GetOrigin())
                    start.oSet(origin.oPlus(start.oMultiply(axis)))
                }
                end.oSet(p2)
                if (ent2.GetPhysics() != null) {
                    axis = ent2.GetPhysics().GetAxis()
                    origin.oSet(ent2.GetPhysics().GetOrigin())
                    end.oSet(origin.oPlus(p2.oMultiply(axis)))
                }
                Game_local.gameRenderWorld.DebugLine(idVec4(1, 1, 0, 1), start, end, 0, true)
            }
            Present()
        }

        private fun Event_LinkSpring() {
            val name1 = idStr()
            val name2 = idStr()
            spawnArgs.GetString("ent1", "", name1)
            spawnArgs.GetString("ent2", "", name2)
            if (name1.Length() != 0) {
                ent1 = Game_local.gameLocal.FindEntity(name1.toString())
                if (null == ent1) {
                    idGameLocal.Companion.Error(
                        "idSpring '%s' at (%s): cannot find first entity '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        name1
                    )
                }
            } else {
                ent1 = Game_local.gameLocal.entities[Game_local.ENTITYNUM_WORLD]
            }
            if (name2.Length() != 0) {
                ent2 = Game_local.gameLocal.FindEntity(name2.toString())
                if (null == ent2) {
                    idGameLocal.Companion.Error(
                        "idSpring '%s' at (%s): cannot find second entity '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        name2
                    )
                }
            } else {
                ent2 = Game_local.gameLocal.entities[Game_local.ENTITYNUM_WORLD]
            }
            spring.SetPosition(ent1.GetPhysics(), id1.getVal(), p1, ent2.GetPhysics(), id2.getVal(), p2)
            BecomeActive(Entity.TH_THINK)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    class idForceField : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idForceField );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idForceField?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idForceField?>
                eventCallbacks[Misc.EV_Toggle] =
                    eventCallback_t0<idForceField?> { obj: T? -> neo.Game.obj.Event_Toggle() } as eventCallback_t0<idForceField?>
                eventCallbacks[Entity.EV_FindTargets] =
                    eventCallback_t0<idForceField?> { obj: T? -> neo.Game.obj.Event_FindTargets() } as eventCallback_t0<idForceField?>
            }
        }

        private val forceField: idForce_Field? = idForce_Field()
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteStaticObject(forceField)
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadStaticObject(forceField)
        }

        override fun Spawn() {
            super.Spawn()
            val uniform = idVec3()
            val explosion = CFloat()
            val implosion = CFloat()
            val randomTorque = CFloat()
            if (spawnArgs.GetVector("uniform", "0 0 0", uniform)) {
                forceField.Uniform(uniform)
            } else if (spawnArgs.GetFloat("explosion", "0", explosion)) {
                forceField.Explosion(explosion.getVal())
            } else if (spawnArgs.GetFloat("implosion", "0", implosion)) {
                forceField.Implosion(implosion.getVal())
            }
            if (spawnArgs.GetFloat("randomTorque", "0", randomTorque)) {
                forceField.RandomTorque(randomTorque.getVal())
            }
            if (spawnArgs.GetBool("applyForce", "0")) {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_FORCE)
            } else if (spawnArgs.GetBool("applyImpulse", "0")) {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_IMPULSE)
            } else {
                forceField.SetApplyType(forceFieldApplyType.FORCEFIELD_APPLY_VELOCITY)
            }
            forceField.SetPlayerOnly(spawnArgs.GetBool("playerOnly", "0"))
            forceField.SetMonsterOnly(spawnArgs.GetBool("monsterOnly", "0"))

            // set the collision model on the force field
            forceField.SetClipModel(idClipModel(GetPhysics().GetClipModel()))

            // remove the collision model from the physics object
            GetPhysics().SetClipModel(null, 1.0f)
            if (spawnArgs.GetBool("start_on")) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun Think() {
            if (thinkFlags and Entity.TH_THINK != 0) {
                // evaluate force
                forceField.Evaluate(Game_local.gameLocal.time)
            }
            Present()
        }

        private fun Toggle() {
            if (thinkFlags and Entity.TH_THINK != 0) {
                BecomeInactive(Entity.TH_THINK)
            } else {
                BecomeActive(Entity.TH_THINK)
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            val wait = CFloat()
            Toggle()
            if (spawnArgs.GetFloat("wait", "0.01", wait)) {
                PostEventSec(Misc.EV_Toggle, wait.getVal())
            }
        }

        private fun Event_Toggle() {
            Toggle()
        }

        private fun Event_FindTargets() {
            FindTargets()
            RemoveNullTargets()
            if (targets.Num() != 0) {
                forceField.Uniform(
                    targets.oGet(0).GetEntity().GetPhysics().GetOrigin().oMinus(GetPhysics().GetOrigin())
                )
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    class idAnimated : idAFEntity_Gibbable() {
        companion object {
            // CLASS_PROTOTYPE( idAnimated );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            // ~idAnimated();
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAFEntity_Gibbable.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idAnimated?> { obj: T?, _activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game._activator)
                    } as eventCallback_t1<idAnimated?>
                eventCallbacks[Misc.EV_Animated_Start] =
                    eventCallback_t0<idAnimated?> { obj: T? -> neo.Game.obj.Event_Start() } as eventCallback_t0<idAnimated?>
                eventCallbacks[Misc.EV_StartRagdoll] =
                    eventCallback_t0<idAnimated?> { obj: T? -> neo.Game.obj.Event_StartRagdoll() } as eventCallback_t0<idAnimated?>
                eventCallbacks[Misc.EV_AnimDone] = eventCallback_t1<idAnimated?> { obj: T?, animIndex: idEventArg<*>? ->
                    neo.Game.obj.Event_AnimDone(neo.Game.animIndex)
                } as eventCallback_t1<idAnimated?>
                eventCallbacks[Actor.EV_Footstep] =
                    eventCallback_t0<idAnimated?> { obj: T? -> neo.Game.obj.Event_Footstep() } as eventCallback_t0<idAnimated?>
                eventCallbacks[Actor.EV_FootstepLeft] =
                    eventCallback_t0<idAnimated?> { obj: T? -> neo.Game.obj.Event_Footstep() } as eventCallback_t0<idAnimated?>
                eventCallbacks[Actor.EV_FootstepRight] =
                    eventCallback_t0<idAnimated?> { obj: T? -> neo.Game.obj.Event_Footstep() } as eventCallback_t0<idAnimated?>
                eventCallbacks[Misc.EV_LaunchMissiles] =
                    eventCallback_t6<idAnimated?> { obj: T?, projectilename: idEventArg<*>? ->
                        neo.Game.obj.Event_LaunchMissiles(neo.Game.projectilename)
                    } as eventCallback_t6<idAnimated?>
                eventCallbacks[Misc.EV_LaunchMissilesUpdate] =
                    eventCallback_t4<idAnimated?> { obj: T?, launchjoint: idEventArg<*>? ->
                        neo.Game.obj.Event_LaunchMissilesUpdate(neo.Game.launchjoint)
                    } as eventCallback_t4<idAnimated?>
            }
        }

        private val activator: idEntityPtr<idEntity?>?
        private var activated: Boolean
        private var anim = 0
        private var blendFrames = 0
        private var current_anim_index: Int
        private var num_anims: Int
        private var   /*jointHandle_t*/soundJoint: Int
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(current_anim_index)
            savefile.WriteInt(num_anims)
            savefile.WriteInt(anim)
            savefile.WriteInt(blendFrames)
            savefile.WriteJoint(soundJoint)
            activator.Save(savefile)
            savefile.WriteBool(activated)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val current_anim_index = CInt()
            val num_anims = CInt()
            val anim = CInt()
            val blendFrames = CInt()
            val soundJoint = CInt()
            val activated = CBool(false)
            savefile.ReadInt(current_anim_index)
            savefile.ReadInt(num_anims)
            savefile.ReadInt(anim)
            savefile.ReadInt(blendFrames)
            savefile.ReadJoint(soundJoint)
            activator.Restore(savefile)
            savefile.ReadBool(activated)
            this.current_anim_index = current_anim_index.getVal()
            this.num_anims = num_anims.getVal()
            this.anim = anim.getVal()
            this.blendFrames = blendFrames.getVal()
            this.soundJoint = soundJoint.getVal()
            this.activated = activated.isVal
        }

        override fun Spawn() {
            super.Spawn()
            val animname = arrayOfNulls<String?>(1)
            val anim2: Int
            val wait = CFloat()
            val joint: String?
            val num_anims2 = CInt()
            joint = spawnArgs.GetString("sound_bone", "origin")
            soundJoint = animator.GetJointHandle(joint)
            if (soundJoint == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): cannot find joint '%s' for sound playback",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    joint
                )
            }
            LoadAF()

            // allow bullets to collide with a combat model
            if (spawnArgs.GetBool("combatModel", "0")) {
                combatModel = idClipModel(modelDefHandle)
            }

            // allow the entity to take damage
            if (spawnArgs.GetBool("takeDamage", "0")) {
                fl.takedamage = true
            }
            blendFrames = 0
            current_anim_index = 0
            spawnArgs.GetInt("num_anims", "0", num_anims2)
            num_anims = num_anims2.getVal()
            blendFrames = spawnArgs.GetInt("blend_in")
            animname[0] = spawnArgs.GetString(if (num_anims != 0) "anim1" else "anim")
            if (0 == animname[0].length) {
                anim = 0
            } else {
                anim = animator.GetAnim(animname[0])
                if (0 == anim) {
                    idGameLocal.Companion.Error(
                        "idAnimated '%s' at (%s): cannot find anim '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        animname[0]
                    )
                }
            }
            if (spawnArgs.GetBool("hide")) {
                Hide()
                if (0 == num_anims) {
                    blendFrames = 0
                }
            } else if (spawnArgs.GetString("start_anim", "", animname)) {
                anim2 = animator.GetAnim(animname[0])
                if (0 == anim2) {
                    idGameLocal.Companion.Error(
                        "idAnimated '%s' at (%s): cannot find anim '%s'",
                        name,
                        GetPhysics().GetOrigin().ToString(0),
                        animname[0]
                    )
                }
                animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim2, Game_local.gameLocal.time, 0)
            } else if (anim != 0) {
                // init joints to the first frame of the animation
                animator.SetFrame(Anim.ANIMCHANNEL_ALL, anim, 1, Game_local.gameLocal.time, 0)
                if (0 == num_anims) {
                    blendFrames = 0
                }
            }
            spawnArgs.GetFloat("wait", "-1", wait)
            if (wait.getVal() >= 0) {
                PostEventSec(Entity.EV_Activate, wait.getVal(), this)
            }
        }

        override fun LoadAF(): Boolean {
            val fileName = arrayOfNulls<String?>(1)
            if (!spawnArgs.GetString("ragdoll", "*unknown*", fileName)) {
                return false
            }
            af.SetAnimator(GetAnimator())
            return af.Load(this, fileName[0])
        }

        fun StartRagdoll(): Boolean {
            // if no AF loaded
            if (!af.IsLoaded()) {
                return false
            }

            // if the AF is already active
            if (af.IsActive()) {
                return true
            }

            // disable any collision model used
            GetPhysics().DisableClip()

            // start using the AF
            af.StartFromCurrentPose(spawnArgs.GetInt("velocityTime", "0"))
            return true
        }

        override fun GetPhysicsToSoundTransform(origin: idVec3?, axis: idMat3?): Boolean {
            animator.GetJointTransform(soundJoint, Game_local.gameLocal.time, origin, axis)
            axis.oSet(renderEntity.axis)
            return true
        }

        private fun PlayNextAnim() {
            val animName = arrayOfNulls<String?>(1)
            val len: Int
            val cycle = CInt()
            if (current_anim_index >= num_anims) {
                Hide()
                if (spawnArgs.GetBool("remove")) {
                    PostEventMS(Class.EV_Remove, 0)
                } else {
                    current_anim_index = 0
                }
                return
            }
            Show()
            current_anim_index++
            spawnArgs.GetString(Str.va("anim%d", current_anim_index), null, animName)
            if (animName[0].isEmpty()) {
                anim = 0
                animator.Clear(Anim.ANIMCHANNEL_ALL, Game_local.gameLocal.time, Anim.FRAME2MS(blendFrames))
                return
            }
            anim = animator.GetAnim(animName[0])
            if (0 == anim) {
                Game_local.gameLocal.Warning("missing anim '%s' on %s", animName[0], name)
                return
            }
            if (SysCvar.g_debugCinematic.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: '%s' start anim '%s'\n",
                    Game_local.gameLocal.framenum,
                    GetName(),
                    animName[0]
                )
            }
            spawnArgs.GetInt("cycle", "1", cycle)
            if (current_anim_index == num_anims && spawnArgs.GetBool("loop_last_anim")) {
                cycle.setVal(-1)
            }
            animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim, Game_local.gameLocal.time, Anim.FRAME2MS(blendFrames))
            animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(cycle.getVal())
            len = animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).PlayLength()
            if (len >= 0) {
                PostEventMS(Misc.EV_AnimDone, len.toFloat(), current_anim_index)
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            animator.ForceUpdate()
            UpdateAnimation()
            UpdateVisuals()
            Present()
        }

        private fun Event_Activate(_activator: idEventArg<idEntity?>?) {
            if (num_anims != 0) {
                PlayNextAnim()
                activator.oSet(_activator.value)
                return
            }
            if (activated) {
                // already activated
                return
            }
            activated = true
            activator.oSet(_activator.value)
            ProcessEvent(Misc.EV_Animated_Start)
        }

        private fun Event_Start() {
            val cycle = CInt()
            val len: Int
            Show()
            if (num_anims != 0) {
                PlayNextAnim()
                return
            }
            if (anim != 0) {
                if (SysCvar.g_debugCinematic.GetBool()) {
                    val animPtr = animator.GetAnim(anim)
                    Game_local.gameLocal.Printf(
                        "%d: '%s' start anim '%s'\n",
                        Game_local.gameLocal.framenum,
                        GetName(),
                        if (animPtr != null) animPtr.Name() else ""
                    )
                }
                spawnArgs.GetInt("cycle", "1", cycle)
                animator.CycleAnim(Anim.ANIMCHANNEL_ALL, anim, Game_local.gameLocal.time, Anim.FRAME2MS(blendFrames))
                animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).SetCycleCount(cycle.getVal())
                len = animator.CurrentAnim(Anim.ANIMCHANNEL_ALL).PlayLength()
                if (len >= 0) {
                    PostEventMS(Misc.EV_AnimDone, len.toFloat(), 1)
                }
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            animator.ForceUpdate()
            UpdateAnimation()
            UpdateVisuals()
            Present()
        }

        private fun Event_StartRagdoll() {
            StartRagdoll()
        }

        private fun Event_AnimDone(animIndex: idEventArg<Int?>?) {
            if (SysCvar.g_debugCinematic.GetBool()) {
                val animPtr = animator.GetAnim(anim)
                Game_local.gameLocal.Printf(
                    "%d: '%s' end anim '%s'\n",
                    Game_local.gameLocal.framenum,
                    GetName(),
                    if (animPtr != null) animPtr.Name() else ""
                )
            }
            if (animIndex.value >= num_anims && spawnArgs.GetBool("remove")) {
                Hide()
                PostEventMS(Class.EV_Remove, 0)
            } else if (spawnArgs.GetBool("auto_advance")) {
                PlayNextAnim()
            } else {
                activated = false
            }
            ActivateTargets(activator.GetEntity())
        }

        private fun Event_Footstep() {
            StartSound("snd_footstep", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
        }

        private fun Event_LaunchMissiles(
            projectilename: idEventArg<String?>?, sound: idEventArg<String?>?, launchjoint: idEventArg<String?>?,
            targetjoint: idEventArg<String?>?, numshots: idEventArg<Int?>?, framedelay: idEventArg<Int?>?
        ) {
            val projectileDef: idDict?
            val   /*jointHandle_t*/launch: Int
            val   /*jointHandle_t*/target: Int
            projectileDef = Game_local.gameLocal.FindEntityDefDict(projectilename.value, false)
            if (null == projectileDef) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown projectile '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename.value
                )
                return
            }
            launch = animator.GetJointHandle(launchjoint.value)
            if (launch == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown launch joint '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    launchjoint.value
                )
                idGameLocal.Companion.Error("Unknown joint '%s'", launchjoint.value)
            }
            target = animator.GetJointHandle(targetjoint.value)
            if (target == Model.INVALID_JOINT) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): unknown target joint '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    targetjoint.value
                )
            }
            spawnArgs.Set("projectilename", projectilename.value)
            spawnArgs.Set("missilesound", sound.value)
            CancelEvents(Misc.EV_LaunchMissilesUpdate)
            ProcessEvent(Misc.EV_LaunchMissilesUpdate, launch, target, numshots.value - 1, framedelay.value)
        }

        private fun Event_LaunchMissilesUpdate(
            launchjoint: idEventArg<Int?>?,
            targetjoint: idEventArg<Int?>?,
            numshots: idEventArg<Int?>?,
            framedelay: idEventArg<Int?>?
        ) {
            val launchPos = idVec3()
            val targetPos = idVec3()
            val axis = idMat3()
            val dir = idVec3()
            val ent = arrayOf<idEntity?>(null)
            val projectile: idProjectile?
            val projectileDef: idDict?
            val projectilename: String?
            projectilename = spawnArgs.GetString("projectilename")
            projectileDef = Game_local.gameLocal.FindEntityDefDict(projectilename, false)
            if (null == projectileDef) {
                Game_local.gameLocal.Warning(
                    "idAnimated '%s' at (%s): 'launchMissiles' called with unknown projectile '%s'",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename
                )
                return
            }
            StartSound("snd_missile", gameSoundChannel_t.SND_CHANNEL_WEAPON, 0, false, null)
            animator.GetJointTransform(launchjoint.value, Game_local.gameLocal.time, launchPos, axis)
            launchPos.oSet(renderEntity.origin.oPlus(launchPos.oMultiply(renderEntity.axis)))
            animator.GetJointTransform(targetjoint.value, Game_local.gameLocal.time, targetPos, axis)
            targetPos.oSet(renderEntity.origin.oPlus(targetPos.oMultiply(renderEntity.axis)))
            dir.oSet(targetPos.oMinus(launchPos))
            dir.Normalize()
            Game_local.gameLocal.SpawnEntityDef(projectileDef, ent, false)
            if (null == ent[0] || ent[0] !is idProjectile) {
                idGameLocal.Companion.Error(
                    "idAnimated '%s' at (%s): in 'launchMissiles' call '%s' is not an idProjectile",
                    name,
                    GetPhysics().GetOrigin().ToString(0),
                    projectilename
                )
            }
            projectile = ent[0] as idProjectile?
            projectile.Create(this, launchPos, dir)
            projectile.Launch(launchPos, dir, Vector.getVec3_origin())
            if (numshots.value > 0) {
                PostEventMS(
                    Misc.EV_LaunchMissilesUpdate,
                    Anim.FRAME2MS(framedelay.value),
                    launchjoint.value,
                    targetjoint.value,
                    numshots.value - 1,
                    framedelay.value
                )
            }
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            soundJoint = Model.INVALID_JOINT
            activated = false
            combatModel = null
            activator = idEntityPtr()
            current_anim_index = 0
            num_anims = 0
        }
    }

    /*
     ===============================================================================

     idStaticEntity

     Some static entities may be optimized into inline geometry by dmap

     ===============================================================================
     */
    open class idStaticEntity : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idStaticEntity );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idStaticEntity?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idStaticEntity?>
            }
        }

        private val fadeFrom: idVec4?
        private val fadeTo: idVec4?
        private var active = false
        private var fadeEnd: Int
        private var fadeStart: Int
        private var runGui: Boolean
        private var spawnTime = 0
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(spawnTime)
            savefile.WriteBool(active)
            savefile.WriteVec4(fadeFrom)
            savefile.WriteVec4(fadeTo)
            savefile.WriteInt(fadeStart)
            savefile.WriteInt(fadeEnd)
            savefile.WriteBool(runGui)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val spawnTime = CInt()
            val fadeStart = CInt()
            val fadeEnd = CInt() //TODO:make sure the dumbass compiler doesn't decide that all {0}'s are the same (lol)
            val active = CBool()
            val runGui = CBool()
            savefile.ReadInt(spawnTime)
            savefile.ReadBool(active)
            savefile.ReadVec4(fadeFrom)
            savefile.ReadVec4(fadeTo)
            savefile.ReadInt(fadeStart)
            savefile.ReadInt(fadeEnd)
            savefile.ReadBool(runGui)
            this.spawnTime = spawnTime.getVal()
            this.fadeStart = fadeStart.getVal()
            this.fadeEnd = fadeEnd.getVal()
            this.active = active.isVal
            this.runGui = runGui.isVal
        }

        override fun Spawn() {
            super.Spawn()
            val solid: Boolean
            val hidden: Boolean

            // an inline static model will not do anything at all
            if (spawnArgs.GetBool("inline") || Game_local.gameLocal.world.spawnArgs.GetBool("inlineAllStatics")) {
                Hide()
                return
            }
            solid = spawnArgs.GetBool("solid")
            hidden = spawnArgs.GetBool("hide")
            if (solid && !hidden) {
                GetPhysics().SetContents(Material.CONTENTS_SOLID)
            } else {
                GetPhysics().SetContents(0)
            }
            spawnTime = Game_local.gameLocal.time
            active = false
            val model = idStr(spawnArgs.GetString("model"))
            if (model.Find(".prt") >= 0) {
                // we want the parametric particles out of sync with each other
                renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    Game_local.gameLocal.random.RandomInt(32767.0)
            }
            fadeFrom.Set(1f, 1f, 1f, 1f)
            fadeTo.Set(1f, 1f, 1f, 1f)
            fadeStart = 0
            fadeEnd = 0

            // NOTE: this should be used very rarely because it is expensive
            runGui = spawnArgs.GetBool("runGui")
            if (runGui) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun ShowEditingDialog() {
            Common.common.InitTool(Common.EDITOR_PARTICLE, spawnArgs)
        }

        override fun Hide() {
            super.Hide()
            GetPhysics().SetContents(0)
        }

        override fun Show() {
            super.Show()
            if (spawnArgs.GetBool("solid")) {
                GetPhysics().SetContents(Material.CONTENTS_SOLID)
            }
        }

        fun Fade(to: idVec4?, fadeTime: Float) {
            GetColor(fadeFrom)
            fadeTo.oSet(to)
            fadeStart = Game_local.gameLocal.time
            fadeEnd = (Game_local.gameLocal.time + Math_h.SEC2MS(fadeTime)).toInt()
            BecomeActive(Entity.TH_THINK)
        }

        override fun Think() {
            super.Think()
            if (thinkFlags and Entity.TH_THINK != 0) {
                if (runGui && renderEntity.gui[0] != null) {
                    val player = Game_local.gameLocal.GetLocalPlayer()
                    if (player != null) {
                        if (!player.objectiveSystemOpen) {
                            renderEntity.gui[0].StateChanged(Game_local.gameLocal.time, true)
                            if (renderEntity.gui[1] != null) {
                                renderEntity.gui[1].StateChanged(Game_local.gameLocal.time, true)
                            }
                            if (renderEntity.gui[2] != null) {
                                renderEntity.gui[2].StateChanged(Game_local.gameLocal.time, true)
                            }
                        }
                    }
                }
                if (fadeEnd > 0) {
                    var color: idVec4? = idVec4()
                    if (Game_local.gameLocal.time < fadeEnd) {
                        color.Lerp(
                            fadeFrom,
                            fadeTo,
                            (Game_local.gameLocal.time - fadeStart).toFloat() / (fadeEnd - fadeStart).toFloat()
                        )
                    } else {
                        color = fadeTo
                        fadeEnd = 0
                        BecomeInactive(Entity.TH_THINK)
                    }
                    SetColor(color)
                }
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            GetPhysics().WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            WriteColorToSnapshot(msg)
            WriteGUIToSnapshot(msg)
            msg.WriteBits(if (IsHidden()) 1 else 0, 1)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            val hidden: Boolean
            GetPhysics().ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            ReadColorFromSnapshot(msg)
            ReadGUIFromSnapshot(msg)
            hidden = msg.ReadBits(1) == 1
            if (hidden != IsHidden()) {
                if (hidden) {
                    Hide()
                } else {
                    Show()
                }
            }
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            var activateGui: idStr
            spawnTime = Game_local.gameLocal.time
            active = !active
            val kv = spawnArgs.FindKey("hide")
            if (kv != null) {
                if (IsHidden()) {
                    Show()
                } else {
                    Hide()
                }
            }
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = -Math_h.MS2SEC(spawnTime.toFloat())
            renderEntity.shaderParms[5] = if (active) 1 else 0
            // this change should be a good thing, it will automatically turn on
            // lights etc.. when triggered so that does not have to be specifically done
            // with trigger parms.. it MIGHT break things so need to keep an eye on it
            renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] =
                if (renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] != 0) 0.0f else 1.0f
            BecomeActive(Entity.TH_UPDATEVISUALS)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            fadeFrom = idVec4(1, 1, 1, 1)
            fadeTo = idVec4(1, 1, 1, 1)
            fadeStart = 0
            fadeEnd = 0
            runGui = false
        }
    }

    /*
     ===============================================================================

     idFuncEmitter

     ===============================================================================
     */
    open class idFuncEmitter : idStaticEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncEmitter );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idStaticEntity.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncEmitter?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncEmitter?>
            }
        }

        private val hidden: CBool? = CBool(false)
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(hidden.isVal())
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadBool(hidden)
        }

        override fun Spawn() {
            super.Spawn()
            if (spawnArgs.GetBool("start_off")) {
                hidden.setVal(true)
                renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = Math_h.MS2SEC(1f)
                UpdateVisuals()
            } else {
                hidden.setVal(false)
            }
        }

        open fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (hidden.isVal() || spawnArgs.GetBool("cycleTrigger")) {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = 0
                renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
                hidden.setVal(false)
            } else {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] =
                    Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
                hidden.setVal(true)
            }
            UpdateVisuals()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            msg.WriteBits(if (hidden.isVal()) 1 else 0, 1)
            msg.WriteFloat(renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME])
            msg.WriteFloat(renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET])
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            hidden.setVal(msg.ReadBits(1) != 0)
            renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] = msg.ReadFloat()
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = msg.ReadFloat()
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            hidden.setVal(false)
        }
    }

    /*
     ===============================================================================

     idFuncSmoke

     ===============================================================================
     */
    class idFuncSmoke     //
    //
        : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncSmoke );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncSmoke?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncSmoke?>
            }
        }

        private var restart = false
        private var smoke: idDeclParticle? = null
        private var smokeTime = 0
        override fun Spawn() {
            super.Spawn()
            val smokeName = spawnArgs.GetString("smoke")
            smoke = if (!smokeName.isEmpty()) { // != '\0' ) {
                DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            if (spawnArgs.GetBool("start_off")) {
                smokeTime = 0
                restart = false
            } else if (smoke != null) {
                smokeTime = Game_local.gameLocal.time
                BecomeActive(Entity.TH_UPDATEPARTICLES)
                restart = true
            }
            GetPhysics().SetContents(0)
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(smokeTime)
            savefile.WriteParticle(smoke)
            savefile.WriteBool(restart)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val smokeTime = CInt()
            val restart = CBool()
            savefile.ReadInt(smokeTime)
            savefile.ReadParticle(smoke)
            savefile.ReadBool(restart)
            this.smokeTime = smokeTime.getVal()
            this.restart = restart.isVal
        }

        override fun Think() {

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant() || smoke == null || smokeTime == -1) {
                return
            }
            if (thinkFlags and Entity.TH_UPDATEPARTICLES != 0 && !IsHidden()) {
                if (!Game_local.gameLocal.smokeParticles.EmitSmoke(
                        smoke,
                        smokeTime,
                        Game_local.gameLocal.random.CRandomFloat(),
                        GetPhysics().GetOrigin(),
                        GetPhysics().GetAxis()
                    )
                ) {
                    if (restart) {
                        smokeTime = Game_local.gameLocal.time
                    } else {
                        smokeTime = 0
                        BecomeInactive(Entity.TH_UPDATEPARTICLES)
                    }
                }
            }
        }

        fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (thinkFlags and Entity.TH_UPDATEPARTICLES != 0) {
                restart = false
                //                return;
            } else {
                BecomeActive(Entity.TH_UPDATEPARTICLES)
                restart = true
                smokeTime = Game_local.gameLocal.time
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    class idFuncSplat : idFuncEmitter() {
        companion object {
            // CLASS_PROTOTYPE( idFuncSplat );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idFuncEmitter.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncSplat?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncSplat?>
                eventCallbacks[Misc.EV_Splat] =
                    eventCallback_t0<idFuncSplat?> { obj: T? -> neo.Game.obj.Event_Splat() } as eventCallback_t0<idFuncSplat?>
            }
        }

        override fun Event_Activate(activator: idEventArg<idEntity?>?) {
            super.Event_Activate(activator)
            PostEventSec(Misc.EV_Splat, spawnArgs.GetFloat("splatDelay", "0.25"))
            StartSound("snd_spurt", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)
        }

        private fun Event_Splat() {
            var splat: String?
            val count = spawnArgs.GetInt("splatCount", "1")
            for (i in 0 until count) {
                splat = spawnArgs.RandomPrefix("mtr_splat", Game_local.gameLocal.random)
                if (splat != null && !splat.isEmpty()) {
                    val size = spawnArgs.GetFloat("splatSize", "128")
                    val dist = spawnArgs.GetFloat("splatDistance", "128")
                    val angle = spawnArgs.GetFloat("splatAngle", "0")
                    Game_local.gameLocal.ProjectDecal(
                        GetPhysics().GetOrigin(),
                        GetPhysics().GetAxis().oGet(2),
                        dist,
                        true,
                        size,
                        splat,
                        angle
                    )
                }
            }
            StartSound("snd_splat", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idTextEntity

     ===============================================================================
     */
    class idTextEntity : idEntity() {
        // CLASS_PROTOTYPE( idTextEntity );
        private var playerOriented = false
        private val text: idStr? = null

        //
        //
        override fun Spawn() {
            super.Spawn()
            // these are cached as the are used each frame
            text.oSet(spawnArgs.GetString("text"))
            playerOriented = spawnArgs.GetBool("playerOriented")
            val force = spawnArgs.GetBool("force")
            if (Common.com_developer.GetBool() || force) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteString(text)
            savefile.WriteBool(playerOriented)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val playerOriented = CBool(false)
            savefile.ReadString(text)
            savefile.ReadBool(playerOriented)
            this.playerOriented = playerOriented.isVal
        }

        override fun Think() {
            if (thinkFlags and Entity.TH_THINK != 0) {
                Game_local.gameRenderWorld.DrawText(
                    text.toString(),
                    GetPhysics().GetOrigin(),
                    0.25f,
                    Lib.Companion.colorWhite,
                    if (playerOriented) Game_local.gameLocal.GetLocalPlayer().viewAngles.ToMat3() else GetPhysics().GetAxis()
                        .Transpose(),
                    1
                )
                for (i in 0 until targets.Num()) {
                    if (targets.oGet(i).GetEntity() != null) {
                        Game_local.gameRenderWorld.DebugArrow(
                            Lib.Companion.colorBlue,
                            GetPhysics().GetOrigin(),
                            targets.oGet(i).GetEntity().GetPhysics().GetOrigin(),
                            1
                        )
                    }
                }
            } else {
                BecomeInactive(Entity.TH_ALL)
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idLocationEntity

     ===============================================================================
     */
    class idLocationEntity : idEntity() {
        // CLASS_PROTOTYPE( idLocationEntity );
        override fun Spawn() {
            super.Spawn()
            val realName = arrayOfNulls<String?>(1)

            // this just holds dict information
            // if "location" not already set, use the entity name.
            if (!spawnArgs.GetString("location", "", realName)) {
                spawnArgs.Set("location", name)
            }
        }

        fun GetLocation(): String? {
            return spawnArgs.GetString("location")
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idLocationSeparatorEntity

     ===============================================================================
     */
    class idLocationSeparatorEntity : idEntity() {
        // CLASS_PROTOTYPE( idLocationSeparatorEntity );
        override fun Spawn() {
            super.Spawn()
            val b: idBounds?
            b = idBounds(spawnArgs.GetVector("origin")).Expand(16f)
            val   /*qhandle_t*/portal = Game_local.gameRenderWorld.FindPortal(b)
            if (0 == portal) {
                Game_local.gameLocal.Warning(
                    "LocationSeparator '%s' didn't contact a portal",
                    spawnArgs.GetString("name")
                )
            }
            Game_local.gameLocal.SetPortalState(portal, TempDump.etoi(portalConnection_t.PS_BLOCK_LOCATION))
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idVacuumSeperatorEntity

     Can be triggered to let vacuum through a portal (blown out window)

     ===============================================================================
     */
    class idVacuumSeparatorEntity : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idVacuumSeparatorEntity );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idVacuumSeparatorEntity?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idVacuumSeparatorEntity?>
            }
        }

        //
        //
        private var   /*qhandle_t*/portal = 0
        override fun Spawn() {
            super.Spawn()
            val b: idBounds?
            b = idBounds(spawnArgs.GetVector("origin")).Expand(16f)
            portal = Game_local.gameRenderWorld.FindPortal(b)
            if (0 == portal) {
                Game_local.gameLocal.Warning(
                    "VacuumSeparator '%s' didn't contact a portal",
                    spawnArgs.GetString("name")
                )
                return
            }
            Game_local.gameLocal.SetPortalState(
                portal,
                TempDump.etoi(portalConnection_t.PS_BLOCK_AIR) or TempDump.etoi(portalConnection_t.PS_BLOCK_LOCATION)
            )
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(portal)
            savefile.WriteInt(Game_local.gameRenderWorld.GetPortalState(portal))
        }

        override fun Restore(savefile: idRestoreGame?) {
            val state = CInt()
            val portal = CInt()
            savefile.ReadInt(portal)
            savefile.ReadInt(state)
            this.portal = portal.getVal()
            Game_local.gameLocal.SetPortalState(portal.getVal(), state.getVal())
        }

        fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (0 == portal) {
                return
            }
            Game_local.gameLocal.SetPortalState(portal, TempDump.etoi(portalConnection_t.PS_BLOCK_NONE))
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idVacuumEntity

     Levels should only have a single vacuum entity.

     ===============================================================================
     */
    class idVacuumEntity : idEntity() {
        // public:
        // CLASS_PROTOTYPE( idVacuumEntity );
        override fun Spawn() {
            super.Spawn()
            if (Game_local.gameLocal.vacuumAreaNum != -1) {
                Game_local.gameLocal.Warning("idVacuumEntity::Spawn: multiple idVacuumEntity in level")
                return
            }
            val org = idVec3(spawnArgs.GetVector("origin"))
            Game_local.gameLocal.vacuumAreaNum = Game_local.gameRenderWorld.PointInArea(org)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     idBeam

     ===============================================================================
     */
    class idBeam : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idBeam );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_PostSpawn] =
                    eventCallback_t0<idBeam?> { obj: T? -> neo.Game.obj.Event_MatchTarget() } as eventCallback_t0<idBeam?>
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idBeam?> { obj: T?, activator: idEventArg<*>? -> neo.Game.obj.Event_Activate(neo.Game.activator) } as eventCallback_t1<idBeam?>
            }
        }

        private val master: idEntityPtr<idBeam?>?
        private val target: idEntityPtr<idBeam?>?
        override fun Spawn() {
            super.Spawn()
            val width = CFloat()
            if (spawnArgs.GetFloat("width", "0", width)) {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] = width.getVal()
            }
            SetModel("_BEAM")
            Hide()
            PostEventMS(Entity.EV_PostSpawn, 0)
        }

        override fun Save(savefile: idSaveGame?) {
            target.Save(savefile)
            master.Save(savefile)
        }

        override fun Restore(savefile: idRestoreGame?) {
            target.Restore(savefile)
            master.Restore(savefile)
        }

        override fun Think() {
            val masterEnt: idBeam?
            if (!IsHidden() && null == target.GetEntity()) {
                // hide if our target is removed
                Hide()
            }
            RunPhysics()
            masterEnt = master.GetEntity()
            if (masterEnt != null) {
                val origin = GetPhysics().GetOrigin()
                masterEnt.SetBeamTarget(origin)
            }
            Present()
        }

        fun SetMaster(masterbeam: idBeam?) {
            master.oSet(masterbeam)
        }

        fun SetBeamTarget(origin: idVec3?) {
            if (renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] != origin.x || renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] != origin.y || renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] != origin.z) {
                renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] = origin.x
                renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] = origin.y
                renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] = origin.z
                UpdateVisuals()
            }
        }

        override fun Show() {
            val targetEnt: idBeam?
            super.Show()
            targetEnt = target.GetEntity()
            if (targetEnt != null) {
                val origin = targetEnt.GetPhysics().GetOrigin()
                SetBeamTarget(origin)
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            GetPhysics().WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
            WriteColorToSnapshot(msg)
            msg.WriteFloat(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X])
            msg.WriteFloat(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y])
            msg.WriteFloat(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z])
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            GetPhysics().ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            ReadColorFromSnapshot(msg)
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_X] = msg.ReadFloat()
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Y] = msg.ReadFloat()
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_END_Z] = msg.ReadFloat()
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        private fun Event_MatchTarget() {
            var i: Int
            var targetEnt: idEntity?
            var targetBeam: idBeam?
            if (0 == targets.Num()) {
                return
            }
            targetBeam = null
            i = 0
            while (i < targets.Num()) {
                targetEnt = targets.oGet(i).GetEntity()
                if (targetEnt != null && targetEnt is idBeam) {
                    targetBeam = targetEnt
                    break
                }
                i++
            }
            if (null == targetBeam) {
                idGameLocal.Companion.Error("Could not find valid beam target for '%s'", name)
            }
            target.oSet(targetBeam)
            targetBeam.SetMaster(this)
            if (!spawnArgs.GetBool("start_off")) {
                Show()
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (IsHidden()) {
                Show()
            } else {
                Hide()
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        //
        //
        init {
            target = idEntityPtr()
            master = idEntityPtr()
        }
    }

    /*
     ===============================================================================

     idLiquid

     ===============================================================================
     */
    @Deprecated("")
    class idLiquid : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idLiquid );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Touch] =
                    eventCallback_t2<idLiquid?> { obj: T?, other: idEventArg<*>? -> neo.Game.obj.Event_Touch(neo.Game.other) } as eventCallback_t2<idLiquid?>
            }
        }

        private val model: idRenderModelLiquid? = null

        override fun Save(savefile: idSaveGame?) {
            // Nothing to save
        }

        override fun Restore(savefile: idRestoreGame?) {
            //FIXME: NO!
            Spawn()
        }

        private fun Event_Touch(other: idEventArg<idEntity?>?, trace: idEventArg<trace_s?>?) {
            // FIXME: for QuakeCon
/*
             idVec3 pos;

             pos = other->GetPhysics()->GetOrigin() - GetPhysics()->GetOrigin();
             model->IntersectBounds( other->GetPhysics()->GetBounds().Translate( pos ), -10.0f );
             */
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idShaking

     ===============================================================================
     */
    class idShaking : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idShaking );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idShaking?> { obj: T?, activator: idEventArg<*>? -> neo.Game.obj.Event_Activate(neo.Game.activator) } as eventCallback_t1<idShaking?>
            }
        }

        private val physicsObj: idPhysics_Parametric?
        private var active: Boolean
        override fun Spawn() {
            super.Spawn()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            SetPhysics(physicsObj)
            active = false
            if (!spawnArgs.GetBool("start_off")) {
                BeginShaking()
            }
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(active)
            savefile.WriteStaticObject(physicsObj)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val active = CBool()
            savefile.ReadBool(active)
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            this.active = active.isVal
        }

        private fun BeginShaking() {
            val phase: Int
            val shake: idAngles?
            val period: Int
            active = true
            phase = Game_local.gameLocal.random.RandomInt(1000.0)
            shake = spawnArgs.GetAngles("shake", "0.5 0.5 0.5")
            period = (spawnArgs.GetFloat("period", "0.05") * 1000).toInt()
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                phase,
                (period * 0.25f).toInt(),
                GetPhysics().GetAxis().ToAngles(),
                shake,
                Angles.getAng_zero()
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (!active) {
                BeginShaking()
            } else {
                active = false
                physicsObj.SetAngularExtrapolation(
                    Extrapolate.EXTRAPOLATION_NONE,
                    0,
                    0,
                    physicsObj.GetAxis().ToAngles(),
                    Angles.getAng_zero(),
                    Angles.getAng_zero()
                )
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            physicsObj = idPhysics_Parametric()
            active = false
        }
    }

    /*
     ===============================================================================

     idEarthQuake

     ===============================================================================
     */
    class idEarthQuake     //
    //
        : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idEarthQuake );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idEarthQuake?> { obj: T?, _activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game._activator)
                    } as eventCallback_t1<idEarthQuake?>
            }
        }

        private var disabled = false
        private var nextTriggerTime = 0
        private var playerOriented = false
        private var random = 0.0f
        private var shakeStopTime = 0
        private var shakeTime = 0.0f
        private var triggered = false
        private var wait = 0.0f
        override fun Spawn() {
            super.Spawn()
            nextTriggerTime = 0
            shakeStopTime = 0
            wait = spawnArgs.GetFloat("wait", "15")
            random = spawnArgs.GetFloat("random", "5")
            triggered = spawnArgs.GetBool("triggered")
            playerOriented = spawnArgs.GetBool("playerOriented")
            disabled = false
            shakeTime = spawnArgs.GetFloat("shakeTime", "0")
            if (!triggered) {
                PostEventSec(Entity.EV_Activate, spawnArgs.GetFloat("wait"), this)
            }
            BecomeInactive(Entity.TH_THINK)
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(nextTriggerTime)
            savefile.WriteInt(shakeStopTime)
            savefile.WriteFloat(wait)
            savefile.WriteFloat(random)
            savefile.WriteBool(triggered)
            savefile.WriteBool(playerOriented)
            savefile.WriteBool(disabled)
            savefile.WriteFloat(shakeTime)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val nextTriggerTime = CInt()
            val shakeStopTime = CInt()
            val wait = CFloat()
            val random = CFloat()
            val shakeTime = CFloat()
            val triggered = CBool(false)
            val playerOriented = CBool(false)
            val disabled = CBool(false)
            savefile.ReadInt(nextTriggerTime)
            savefile.ReadInt(shakeStopTime)
            savefile.ReadFloat(wait)
            savefile.ReadFloat(random)
            savefile.ReadBool(triggered)
            savefile.ReadBool(playerOriented)
            savefile.ReadBool(disabled)
            savefile.ReadFloat(shakeTime)
            this.nextTriggerTime = nextTriggerTime.getVal()
            this.shakeStopTime = shakeStopTime.getVal()
            this.wait = wait.getVal()
            this.random = random.getVal()
            this.triggered = triggered.isVal
            this.playerOriented = playerOriented.isVal
            this.disabled = disabled.isVal
            this.shakeTime = shakeTime.getVal()
            if (shakeStopTime.getVal() > Game_local.gameLocal.time) {
                BecomeActive(Entity.TH_THINK)
            }
        }

        override fun Think() {}
        private fun Event_Activate(_activator: idEventArg<idEntity?>?) {
            val activator = _activator.value
            if (nextTriggerTime > Game_local.gameLocal.time) {
                return
            }
            if (disabled && activator === this) {
                return
            }
            val player = Game_local.gameLocal.GetLocalPlayer() ?: return
            nextTriggerTime = 0
            if (!triggered && activator !== this) {
                // if we are not triggered ( i.e. random ), disable or enable
                disabled = disabled xor true //1;
                if (disabled) {
                    return
                } else {
                    PostEventSec(Entity.EV_Activate, wait + random * Game_local.gameLocal.random.CRandomFloat(), this)
                }
            }
            ActivateTargets(activator)
            val shader = DeclManager.declManager.FindSound(spawnArgs.GetString("snd_quake"))
            if (playerOriented) {
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY, Sound.SSF_GLOBAL, false, null)
            } else {
                StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_ANY, Sound.SSF_GLOBAL, false, null)
            }
            if (shakeTime > 0.0f) {
                shakeStopTime = (Game_local.gameLocal.time + Math_h.SEC2MS(shakeTime)).toInt()
                BecomeActive(Entity.TH_THINK)
            }
            if (wait > 0.0f) {
                if (!triggered) {
                    PostEventSec(Entity.EV_Activate, wait + random * Game_local.gameLocal.random.CRandomFloat(), this)
                } else {
                    nextTriggerTime =
                        (Game_local.gameLocal.time + Math_h.SEC2MS(wait + random * Game_local.gameLocal.random.CRandomFloat())).toInt()
                }
            } else if (shakeTime == 0.0f) {
                PostEventMS(Class.EV_Remove, 0)
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idFuncPortal

     ===============================================================================
     */
    class idFuncPortal : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncPortal );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncPortal?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncPortal?>
            }
        }

        private val   /*qhandle_t*/portal: CInt? = CInt()
        private val state: CBool? = CBool()
        override fun Spawn() {
            super.Spawn()
            portal.setVal(Game_local.gameRenderWorld.FindPortal(GetPhysics().GetAbsBounds().Expand(32.0f)))
            if (portal.getVal() > 0) {
                state.setVal(spawnArgs.GetBool("start_on"))
                Game_local.gameLocal.SetPortalState(
                    portal.getVal(),
                    (if (state.isVal()) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
                )
            }
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteInt(portal.getVal())
            savefile.WriteBool(state.isVal())
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadInt(portal)
            savefile.ReadBool(state)
            Game_local.gameLocal.SetPortalState(
                portal.getVal(),
                (if (state.isVal()) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            if (portal.getVal() > 0) {
                state.setVal(!state.isVal())
                Game_local.gameLocal.SetPortalState(
                    portal.getVal(),
                    (if (state.isVal()) portalConnection_t.PS_BLOCK_ALL else portalConnection_t.PS_BLOCK_NONE).ordinal
                )
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            portal.setVal(0)
            state.setVal(false)
        }
    }

    /*
     ===============================================================================

     idFuncAASPortal

     ===============================================================================
     */
    class idFuncAASPortal     //
    //
        : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncAASPortal );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncAASPortal?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncAASPortal?>
            }
        }

        private var state = false
        override fun Spawn() {
            super.Spawn()
            state = spawnArgs.GetBool("start_on")
            Game_local.gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_CLUSTERPORTAL, state)
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(state)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val state = CBool()
            savefile.ReadBool(state)
            Game_local.gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(),
                AASFile.AREACONTENTS_CLUSTERPORTAL,
                state.isVal.also { this.state = it })
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            state = state xor true //1;
            Game_local.gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AASFile.AREACONTENTS_CLUSTERPORTAL, state)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idFuncAASObstacle

     ===============================================================================
     */
    class idFuncAASObstacle : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncAASObstacle );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncAASObstacle?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncAASObstacle?>
            }
        }

        private val state: CBool? = CBool(false)
        override fun Spawn() {
            super.Spawn()
            state.setVal(spawnArgs.GetBool("start_on"))
            Game_local.gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(),
                AASFile.AREACONTENTS_OBSTACLE,
                state.isVal()
            )
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(state.isVal())
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadBool(state)
            Game_local.gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(),
                AASFile.AREACONTENTS_OBSTACLE,
                state.isVal()
            )
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            state.setVal(state.isVal() xor true)
            Game_local.gameLocal.SetAASAreaState(
                GetPhysics().GetAbsBounds(),
                AASFile.AREACONTENTS_OBSTACLE,
                state.isVal()
            )
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            state.setVal(false)
        }
    }

    class idFuncRadioChatter     //
    //
        : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idFuncRadioChatter );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idFuncRadioChatter?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idFuncRadioChatter?>
                eventCallbacks[Misc.EV_ResetRadioHud] =
                    eventCallback_t1<idFuncRadioChatter?> { obj: T?, _activator: idEventArg<*>? ->
                        neo.Game.obj.Event_ResetRadioHud(neo.Game._activator)
                    } as eventCallback_t1<idFuncRadioChatter?>
            }
        }

        private var time = 0f
        override fun Spawn() {
            super.Spawn()
            time = spawnArgs.GetFloat("time", "5.0")
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteFloat(time)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val time = CFloat()
            savefile.ReadFloat(time)
            this.time = time.getVal()
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            val player: idPlayer?
            val sound: String?
            val shader: idSoundShader?
            val length = CInt()
            player = if (activator.value is idPlayer) {
                activator.value as idPlayer?
            } else {
                Game_local.gameLocal.GetLocalPlayer()
            }
            player.hud.HandleNamedEvent("radioChatterUp")
            sound = spawnArgs.GetString("snd_radiochatter", "")
            if (sound != null && !sound.isEmpty()) {
                shader = DeclManager.declManager.FindSound(sound)
                player.StartSoundShader(shader, gameSoundChannel_t.SND_CHANNEL_RADIO, Sound.SSF_GLOBAL, false, length)
                time = Math_h.MS2SEC((length.getVal() + 150).toFloat())
            }
            // we still put the hud up because this is used with no sound on
            // certain frame commands when the chatter is triggered
            PostEventSec(Misc.EV_ResetRadioHud, time, player)
        }

        private fun Event_ResetRadioHud(_activator: idEventArg<idEntity?>?) {
            val activator = _activator.value
            val player = if (activator is idPlayer) activator as idPlayer? else Game_local.gameLocal.GetLocalPlayer()
            player.hud.HandleNamedEvent("radioChatterDown")
            ActivateTargets(activator)
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }
    }

    /*
     ===============================================================================

     idPhantomObjects

     ===============================================================================
     */
    class idPhantomObjects : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idPhantomObjects );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idPhantomObjects?> { obj: T?, _activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game._activator)
                    } as eventCallback_t1<idPhantomObjects?>
            }
        }

        private val lastTargetPos: idList<idVec3?>?
        private val target: idEntityPtr<idActor?>? = null
        private val targetTime: idList<Int?>?
        private var end_time = 0
        private var max_wait: Int
        private var min_wait: Int
        private val shake_ang: idVec3?
        private var shake_time = 0.0f
        private var speed: Float
        private var throw_time = 0.0f
        override fun Spawn() {
            super.Spawn()
            throw_time = spawnArgs.GetFloat("time", "5")
            speed = spawnArgs.GetFloat("speed", "1200")
            shake_time = spawnArgs.GetFloat("shake_time", "1")
            throw_time -= shake_time
            if (throw_time < 0.0f) {
                throw_time = 0.0f
            }
            min_wait = Math_h.SEC2MS(spawnArgs.GetFloat("min_wait", "1")).toInt()
            max_wait = Math_h.SEC2MS(spawnArgs.GetFloat("max_wait", "3")).toInt()
            shake_ang.oSet(spawnArgs.GetVector("shake_ang", "65 65 65"))
            Hide()
            GetPhysics().SetContents(0)
        }

        override fun Save(savefile: idSaveGame?) {
            var i: Int
            savefile.WriteInt(end_time)
            savefile.WriteFloat(throw_time)
            savefile.WriteFloat(shake_time)
            savefile.WriteVec3(shake_ang)
            savefile.WriteFloat(speed)
            savefile.WriteInt(min_wait)
            savefile.WriteInt(max_wait)
            target.Save(savefile)
            savefile.WriteInt(targetTime.Num())
            i = 0
            while (i < targetTime.Num()) {
                savefile.WriteInt(targetTime.oGet(i))
                i++
            }
            i = 0
            while (i < lastTargetPos.Num()) {
                savefile.WriteVec3(lastTargetPos.oGet(i))
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame?) {
            val num: Int
            var i: Int
            end_time = savefile.ReadInt()
            throw_time = savefile.ReadFloat()
            shake_time = savefile.ReadFloat()
            savefile.ReadVec3(shake_ang)
            speed = savefile.ReadFloat()
            min_wait = savefile.ReadInt()
            max_wait = savefile.ReadInt()
            target.Restore(savefile)
            num = savefile.ReadInt()
            targetTime.SetGranularity(1)
            targetTime.SetNum(num)
            lastTargetPos.SetGranularity(1)
            lastTargetPos.SetNum(num)
            i = 0
            while (i < num) {
                targetTime.oSet(i, savefile.ReadInt())
                i++
            }
            if (savefile.GetBuildNumber() == SaveGame.INITIAL_RELEASE_BUILD_NUMBER) {
                // these weren't saved out in the first release
                i = 0
                while (i < num) {
                    lastTargetPos.oGet(i).Zero()
                    i++
                }
            } else {
                i = 0
                while (i < num) {
                    savefile.ReadVec3(lastTargetPos.oGet(i))
                    i++
                }
            }
        }

        override fun Think() {
            var i: Int
            var num: Int
            var time: Float
            val vel = idVec3()
            val ang = idVec3()
            var ent: idEntity?
            val targetEnt: idActor?
            var entPhys: idPhysics?
            val tr = trace_s()

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant()) {
                return
            }
            if (0 == thinkFlags and Entity.TH_THINK) {
                BecomeInactive(thinkFlags and Entity.TH_THINK.inv())
                return
            }
            targetEnt = target.GetEntity()
            if (null == targetEnt || targetEnt.health <= 0 || end_time != 0 && Game_local.gameLocal.time > end_time || Game_local.gameLocal.inCinematic) {
                BecomeInactive(Entity.TH_THINK)
            }
            val toPos = targetEnt.GetEyePosition()
            num = 0
            i = 0
            while (i < targets.Num()) {
                ent = targets.oGet(i).GetEntity()
                if (null == ent) {
                    i++
                    continue
                }
                if (ent.fl.hidden) {
                    // don't throw hidden objects
                    i++
                    continue
                }
                if (0 == targetTime.oGet(i)) {
                    // already threw this object
                    i++
                    continue
                }
                num++
                time = Math_h.MS2SEC((targetTime.oGet(i) - Game_local.gameLocal.time).toFloat())
                if (time > shake_time) {
                    i++
                    continue
                }
                entPhys = ent.GetPhysics()
                val entOrg = entPhys.GetOrigin()
                Game_local.gameLocal.clip.TracePoint(tr, entOrg, toPos, Game_local.MASK_OPAQUE, ent)
                if (tr.fraction >= 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == targetEnt) {
                    lastTargetPos.oSet(i, toPos)
                }
                if (time < 0.0f) {
                    idAI.Companion.PredictTrajectory(
                        entPhys.GetOrigin(),
                        lastTargetPos.oGet(i),
                        speed,
                        entPhys.GetGravity(),
                        entPhys.GetClipModel(),
                        entPhys.GetClipMask(),
                        256.0f,
                        ent,
                        targetEnt,
                        if (SysCvar.ai_debugTrajectory.GetBool()) 1 else 0,
                        vel
                    )
                    vel.oMulSet(speed)
                    entPhys.SetLinearVelocity(vel)
                    if (0 == end_time) {
                        targetTime.oSet(i, 0)
                    } else {
                        targetTime.oSet(
                            i,
                            Game_local.gameLocal.time + Game_local.gameLocal.random.RandomInt((max_wait - min_wait).toDouble()) + min_wait
                        )
                    }
                    if (ent is idMoveable) {
                        val ment = ent as idMoveable?
                        ment.EnableDamage(true, 2.5f)
                    }
                } else {
                    // this is not the right way to set the angular velocity, but the effect is nice, so I'm keeping it. :)
                    ang.Set(
                        Game_local.gameLocal.random.CRandomFloat() * shake_ang.x,
                        Game_local.gameLocal.random.CRandomFloat() * shake_ang.y,
                        Game_local.gameLocal.random.CRandomFloat() * shake_ang.z
                    )
                    ang.oMulSet(1.0f - time / shake_time)
                    entPhys.SetAngularVelocity(ang)
                }
                i++
            }
            if (0 == num) {
                BecomeInactive(Entity.TH_THINK)
            }
        }

        private fun Event_Activate(_activator: idEventArg<idEntity?>?) {
            val activator = _activator.value
            var i: Int
            var time: Float
            var frac: Float
            val scale: Float
            if (thinkFlags and Entity.TH_THINK != 0) {
                BecomeInactive(Entity.TH_THINK)
                return
            }
            RemoveNullTargets()
            if (0 == targets.Num()) {
                return
            }
            if (null == activator || activator !is idActor) {
                target.oSet(Game_local.gameLocal.GetLocalPlayer())
            } else {
                target.oSet(activator as idActor?)
            }
            end_time = (Game_local.gameLocal.time + Math_h.SEC2MS(spawnArgs.GetFloat("end_time", "0"))).toInt()
            targetTime.SetNum(targets.Num())
            lastTargetPos.SetNum(targets.Num())
            val toPos = target.GetEntity().GetEyePosition()

            // calculate the relative times of all the objects
            time = 0.0f
            i = 0
            while (i < targetTime.Num()) {
                targetTime.oSet(i, Math_h.SEC2MS(time))
                lastTargetPos.oSet(i, toPos)
                frac = 1.0f - i.toFloat() / targetTime.Num().toFloat()
                time += (Game_local.gameLocal.random.RandomFloat() + 1.0f) * 0.5f * frac + 0.1f
                i++
            }

            // scale up the times to fit within throw_time
            scale = throw_time / time
            i = 0
            while (i < targetTime.Num()) {
                targetTime.oSet(i, Game_local.gameLocal.time + Math_h.SEC2MS(shake_time) + targetTime.oGet(i) * scale)
                i++
            }
            BecomeActive(Entity.TH_THINK)
        }

        //        private void Event_Throw();
        //
        //        private void Event_ShakeObject(idEntity object, int starttime);
        //
        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        //
        //
        init {
            shake_ang = idVec3()
            speed = 0.0f
            min_wait = 0
            max_wait = 0
            fl.neverDormant = false
            targetTime = idList()
            lastTargetPos = idList()
        }
    }
}