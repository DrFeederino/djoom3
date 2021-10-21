package neo.Game;

import neo.CM.CollisionModel.trace_s;
import neo.Game.AFEntity.idAFEntity_Gibbable;
import neo.Game.AI.AI.idAI;
import neo.Game.Animation.Anim_Blend.idAnim;
import neo.Game.Camera.idCamera;
import neo.Game.GameSys.Class;
import neo.Game.GameSys.Event.idEventDef;
import neo.Game.GameSys.SaveGame.idRestoreGame;
import neo.Game.GameSys.SaveGame.idSaveGame;
import neo.Game.Moveable.idMoveable;
import neo.Game.Physics.Clip.idClipModel;
import neo.Game.Physics.Force_Field.idForce_Field;
import neo.Game.Physics.Force_Spring.idForce_Spring;
import neo.Game.Physics.Physics.idPhysics;
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric;
import neo.Game.Projectile.idProjectile;
import neo.Game.Script.Script_Thread.idThread;
import neo.Renderer.Model_liquid.idRenderModelLiquid;
import neo.Sound.snd_shader.idSoundShader;
import neo.framework.DeclParticle.idDeclParticle;
import neo.idlib.BV.Bounds.idBounds;
import neo.idlib.BitMsg.idBitMsg;
import neo.idlib.BitMsg.idBitMsgDelta;
import neo.idlib.Dict_h.idDict;
import neo.idlib.Dict_h.idKeyValue;
import neo.idlib.Text.Str.idStr;
import neo.idlib.containers.CBool;
import neo.idlib.containers.CFloat;
import neo.idlib.containers.CInt;
import neo.idlib.containers.List.idList;
import neo.idlib.math.Angles.idAngles;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Vector.idVec3;
import neo.idlib.math.Vector.idVec4;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static neo.Game.AI.AI_Events.AI_RandomPath;
import static neo.Game.Actor.*;
import static neo.Game.Animation.Anim.ANIMCHANNEL_ALL;
import static neo.Game.Animation.Anim.FRAME2MS;
import static neo.Game.Entity.*;
import static neo.Game.GameSys.Class.*;
import static neo.Game.GameSys.SaveGame.INITIAL_RELEASE_BUILD_NUMBER;
import static neo.Game.GameSys.SysCvar.ai_debugTrajectory;
import static neo.Game.GameSys.SysCvar.g_debugCinematic;
import static neo.Game.Game_local.*;
import static neo.Game.Game_local.gameSoundChannel_t.*;
import static neo.Game.Physics.Force_Field.forceFieldApplyType.*;
import static neo.Game.Player.*;
import static neo.Game.Sound.SSF_GLOBAL;
import static neo.Renderer.Material.CONTENTS_SOLID;
import static neo.Renderer.Model.INVALID_JOINT;
import static neo.Renderer.ModelManager.renderModelManager;
import static neo.Renderer.RenderWorld.*;
import static neo.Renderer.RenderWorld.portalConnection_t.*;
import static neo.TempDump.NOT;
import static neo.TempDump.etoi;
import static neo.Tools.Compilers.AAS.AASFile.AREACONTENTS_CLUSTERPORTAL;
import static neo.Tools.Compilers.AAS.AASFile.AREACONTENTS_OBSTACLE;
import static neo.framework.Common.*;
import static neo.framework.DeclManager.declManager;
import static neo.framework.DeclManager.declType_t.DECL_PARTICLE;
import static neo.idlib.Lib.*;
import static neo.idlib.Text.Str.va;
import static neo.idlib.math.Angles.getAng_zero;
import static neo.idlib.math.Extrapolate.*;
import static neo.idlib.math.Math_h.MS2SEC;
import static neo.idlib.math.Math_h.SEC2MS;
import static neo.idlib.math.Vector.getVec3_origin;

/**
 *
 */
public class Misc {

    public static final idEventDef EV_AnimDone = new idEventDef("<AnimDone>", "d");

    /*
     ===============================================================================

     idAnimated

     ===============================================================================
     */
    public static final idEventDef EV_Animated_Start = new idEventDef("<start>");
    public static final idEventDef EV_LaunchMissiles = new idEventDef("launchMissiles", "ssssdf");

    public static final idEventDef EV_LaunchMissilesUpdate = new idEventDef("<launchMissiles>", "dddd");

    /*
     ===============================================================================

     idFuncRadioChatter

     ===============================================================================
     */
    public static final idEventDef EV_ResetRadioHud = new idEventDef("<resetradiohud>", "e");

    /*
     ===============================================================================

     Object that fires targets and changes shader parms when damaged.

     ===============================================================================
     */
    public static final idEventDef EV_RestoreDamagable = new idEventDef("<RestoreDamagable>");
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
    public static final idEventDef EV_Splat = new idEventDef("<Splat>");

    public static final idEventDef EV_StartRagdoll = new idEventDef("startRagdoll");

    /*
     ===============================================================================

     Potential spawning position for players.
     The first time a player enters the game, they will be at an 'initial' spot.
     Targets will be fired when someone spawns in on them.

     When triggered, will cause player to be teleported to spawn spot.

     ===============================================================================
     */
    public static final idEventDef EV_TeleportStage = new idEventDef("<TeleportStage>", "e");

    /*
     ===============================================================================

     idForceField

     ===============================================================================
     */
    public static final idEventDef EV_Toggle = new idEventDef("Toggle", null);

    /*
     ===============================================================================

     idSpawnableEntity

     A simple, spawnable entity with a model and no functionable ability of it's own.
     For example, it can be used as a placeholder during development, for marking
     locations on maps for script, or for simple placed models without any behavior
     that can be bound to other entities.  Should not be subclassed.
     ===============================================================================
     */
    public static class idSpawnableEntity extends idEntity {
        //public 	CLASS_PROTOTYPE( idSpawnableEntity );

        @Override
        public void Spawn() {
            // this just holds dict information
            super.Spawn();
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /*
     ===============================================================================

     idPlayerStart

     ===============================================================================
     */
    public static class idPlayerStart extends idEntity {
        // enum {
        public static final int EVENT_TELEPORTPLAYER = idEntity.EVENT_MAXEVENTS;
        public static final int EVENT_MAXEVENTS = EVENT_TELEPORTPLAYER + 1;
        // public 	CLASS_PROTOTYPE( idPlayerStart );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idPlayerStart>) idPlayerStart::Event_TeleportPlayer);
            eventCallbacks.put(EV_TeleportStage, (eventCallback_t1<idPlayerStart>) idPlayerStart::Event_TeleportStage);
        }

        // };
        private int teleportStage;
        //
        //

        public idPlayerStart() {
            teleportStage = 0;
        }

        private static void Event_TeleportPlayer(idPlayerStart p, idEventArg<idEntity> activator) {
            idPlayer player;

            if (activator.value instanceof idPlayer) {
                player = (idPlayer) activator.value;
            } else {
                player = gameLocal.GetLocalPlayer();
            }
            if (player != null) {
                if (p.spawnArgs.GetBool("visualFx")) {

                    p.teleportStage = 0;
                    p.Event_TeleportStage(player);

                } else {

                    if (gameLocal.isServer) {
                        idBitMsg msg = new idBitMsg();
                        ByteBuffer msgBuf = ByteBuffer.allocate(MAX_EVENT_PARAM_SIZE);

                        msg.Init(msgBuf, MAX_EVENT_PARAM_SIZE);
                        msg.BeginWriting();
                        msg.WriteBits(player.entityNumber, GENTITYNUM_BITS);
                        p.ServerSendEvent(EVENT_TELEPORTPLAYER, msg, false, -1);
                    }

                    p.TeleportPlayer(player);
                }
            }
        }

        /*
         ===============
         idPlayerStart::Event_TeleportStage

         FIXME: add functionality to fx system ( could be done with player scripting too )
         ================
         */
        private static void Event_TeleportStage(idPlayerStart p, idEventArg<idEntity> _player) {
            idPlayer player;
            if (!(_player.value instanceof idPlayer)) {
                common.Warning("idPlayerStart::Event_TeleportStage: entity is not an idPlayer\n");
                return;
            }
            player = (idPlayer) _player.value;
            float teleportDelay = p.spawnArgs.GetFloat("teleportDelay");
            switch (p.teleportStage) {
                case 0:
                    player.playerView.Flash(colorWhite, 125);
                    player.SetInfluenceLevel(INFLUENCE_LEVEL3);
                    player.SetInfluenceView(p.spawnArgs.GetString("mtr_teleportFx"), null, 0.0f, null);
                    gameSoundWorld.FadeSoundClasses(0, -20.0f, teleportDelay);
                    player.StartSound("snd_teleport_start", SND_CHANNEL_BODY2, 0, false, null);
                    p.teleportStage++;
                    p.PostEventSec(EV_TeleportStage, teleportDelay, player);
                    break;
                case 1:
                    gameSoundWorld.FadeSoundClasses(0, 0.0f, 0.25f);
                    p.teleportStage++;
                    p.PostEventSec(EV_TeleportStage, 0.25f, player);
                    break;
                case 2:
                    player.SetInfluenceView(null, null, 0.0f, null);
                    p.TeleportPlayer(player);
                    player.StopSound(etoi(SND_CHANNEL_BODY2), false);
                    player.SetInfluenceLevel(INFLUENCE_NONE);
                    p.teleportStage = 0;
                    break;
                default:
                    break;
            }
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            teleportStage = 0;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(teleportStage);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt teleportStage = new CInt();

            savefile.ReadInt(teleportStage);

            this.teleportStage = teleportStage.getVal();
        }

        @Override
        public boolean ClientReceiveEvent(int event, int time, final idBitMsg msg) {
            int entityNumber;

            switch (event) {
                case EVENT_TELEPORTPLAYER: {
                    entityNumber = msg.ReadBits(GENTITYNUM_BITS);
                    idPlayer player = (idPlayer) gameLocal.entities[entityNumber];
                    if (player != null && player instanceof idPlayer) {
                        Event_TeleportPlayer(player);
                    }
                    return true;
                }
                default: {
                    return super.ClientReceiveEvent(event, time, msg);
                }
            }
//            return false;
        }

        private void Event_TeleportPlayer(idEntity activator) {
            Event_TeleportPlayer(this, idEventArg.toArg(activator));
        }

        private void Event_TeleportStage(idEntity _player) {
            Event_TeleportStage(this, idEventArg.toArg(_player));
        }

        private void TeleportPlayer(idPlayer player) {
            float pushVel = spawnArgs.GetFloat("push", "300");
            float f = spawnArgs.GetFloat("visualEffect", "0");
            final String viewName = spawnArgs.GetString("visualView", "");
            idEntity ent = viewName != null ? gameLocal.FindEntity(viewName) : null;//TODO:the standard C++ boolean checks if the bytes are switched on, which in the case of String means NOT NULL AND NOT EMPTY.

            if (f != 0 && ent != null) {
                // place in private camera view for some time
                // the entity needs to teleport to where the camera view is to have the PVS right
                player.Teleport(ent.GetPhysics().GetOrigin(), getAng_zero(), this);
                player.StartSound("snd_teleport_enter", SND_CHANNEL_ANY, 0, false, null);
                player.SetPrivateCameraView((idCamera) ent);
                // the player entity knows where to spawn from the previous Teleport call
                if (!gameLocal.isClient) {
                    player.PostEventSec(EV_Player_ExitTeleporter, f);
                }
            } else {
                // direct to exit, Teleport will take care of the killbox
                player.Teleport(GetPhysics().GetOrigin(), GetPhysics().GetAxis().ToAngles(), null);

                // multiplayer hijacked this entity, so only push the player in multiplayer
                if (gameLocal.isMultiplayer) {
                    player.GetPhysics().SetLinearVelocity(GetPhysics().GetAxis().oGet(0).oMultiply(pushVel));
                }
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
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
    public static class idActivator extends idEntity {
        // public 	CLASS_PROTOTYPE( idActivator );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idActivator>) idActivator::Event_Activate);
        }

        private final CBool stay_on = new CBool(false);
        //
        //

        private static void Event_Activate(idActivator a, idEventArg<idEntity> activator) {
            if ((a.thinkFlags & TH_THINK) != 0) {
                a.BecomeInactive(TH_THINK);
            } else {
                a.BecomeActive(TH_THINK);
            }
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            CBool start_off = new CBool(false);

            spawnArgs.GetBool("stay_on", "0", stay_on);
            spawnArgs.GetBool("start_off", "0", start_off);

            GetPhysics().SetClipBox(new idBounds(getVec3_origin()).Expand(4), 1.0f);
            GetPhysics().SetContents(0);

            if (!start_off.isVal()) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteBool(stay_on.isVal());
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadBool(stay_on);

            if (stay_on.isVal()) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void Think() {
            RunPhysics();
            if ((thinkFlags & TH_THINK) != 0) {
                if (TouchTriggers()) {
                    if (!stay_on.isVal()) {
                        BecomeInactive(TH_THINK);
                    }
                }
            }
            Present();
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
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
    public static class idPathCorner extends idEntity {
        // public 	CLASS_PROTOTYPE( idPathCorner );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(AI_RandomPath, (eventCallback_t0<idPathCorner>) idPathCorner::Event_RandomPath);
        }

        public static void DrawDebugInfo() {
            idEntity ent;
            idBounds bnds = new idBounds(new idVec3(-4.0f, -4.0f, -8.0f), new idVec3(4.0f, 4.0f, 64.0f));

            for (ent = gameLocal.spawnedEntities.Next(); ent != null; ent = ent.spawnNode.Next()) {
                if (!(ent instanceof idPathCorner)) {
                    continue;
                }

                idVec3 org = ent.GetPhysics().GetOrigin();
                gameRenderWorld.DebugBounds(colorRed, bnds, org, 0);
            }
        }

        public static idPathCorner RandomPath(final idEntity source, final idEntity ignore) {
            int i;
            int num;
            int which;
            idEntity ent;
            idPathCorner[] path = new idPathCorner[MAX_GENTITIES];

            num = 0;
            for (i = 0; i < source.targets.Num(); i++) {
                ent = source.targets.oGet(i).GetEntity();
                if (ent != null && (ent != ignore) && ent instanceof idPathCorner) {
                    path[num++] = (idPathCorner) ent;
                    if (num >= MAX_GENTITIES) {
                        break;
                    }
                }
            }

            if (0 == num) {
                return null;
            }

            which = gameLocal.random.RandomInt(num);
            return path[which];
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        private void Event_RandomPath() {
            idPathCorner path;

            path = RandomPath(this, null);
            idThread.ReturnEntity(path);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    public static class idDamagable extends idEntity {
        // CLASS_PROTOTYPE( idDamagable );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idDamagable>) idDamagable::Event_BecomeBroken);
            eventCallbacks.put(EV_RestoreDamagable, (eventCallback_t0<idDamagable>) idDamagable::Event_RestoreDamagable);
        }

        private final CInt count = new CInt();
        private final CInt nextTriggerTime = new CInt();
        //
        //

        public idDamagable() {
            count.setVal(0);
            nextTriggerTime.setVal(0);
        }

        private static void Event_BecomeBroken(idDamagable d, idEventArg<idEntity> activator) {
            d.BecomeBroken(activator.value);
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(count.getVal());
            savefile.WriteInt(nextTriggerTime.getVal());
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadInt(count);
            savefile.ReadInt(nextTriggerTime);
        }

        @Override
        public void Spawn() {
            idStr broken = new idStr();

            health = spawnArgs.GetInt("health", "5");
            spawnArgs.GetInt("count", "1", count);
            nextTriggerTime.setVal(0);

            // make sure the model gets cached
            spawnArgs.GetString("broken", "", broken);
            if (broken.Length() != 0 && NOT(renderModelManager.CheckModel(broken.toString()))) {
                idGameLocal.Error("idDamagable '%s' at (%s): cannot load broken model '%s'", name, GetPhysics().GetOrigin().ToString(0), broken);
            }

            fl.takedamage = true;
            GetPhysics().SetContents(CONTENTS_SOLID);
        }

        @Override
        public void Killed(idEntity inflictor, idEntity attacker, int damage, final idVec3 dir, int location) {
            if (gameLocal.time < nextTriggerTime.getVal()) {
                health += damage;
                return;
            }

            BecomeBroken(attacker);
        }

        private void BecomeBroken(idEntity activator) {
            CFloat forceState = new CFloat();
            CInt numStates = new CInt();
            CInt cycle = new CInt();
            CFloat wait = new CFloat();

            if (gameLocal.time < nextTriggerTime.getVal()) {
                return;
            }

            spawnArgs.GetFloat("wait", "0.1", wait);
            nextTriggerTime.setVal((int) (gameLocal.time + SEC2MS(wait.getVal())));
            if (count.getVal() > 0) {
                count.decrement();
                if (0 == count.getVal()) {
                    fl.takedamage = false;
                } else {
                    health = spawnArgs.GetInt("health", "5");
                }
            }

            idStr broken = new idStr();

            spawnArgs.GetString("broken", "", broken);
            if (broken.Length() != 0) {
                SetModel(broken.toString());
            }

            // offset the start time of the shader to sync it to the gameLocal time
            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);

            spawnArgs.GetInt("numstates", "1", numStates);
            spawnArgs.GetInt("cycle", "0", cycle);
            spawnArgs.GetFloat("forcestate", "0", forceState);

            // set the state parm
            if (cycle.getVal() != 0) {
                renderEntity.shaderParms[SHADERPARM_MODE]++;
                if (renderEntity.shaderParms[SHADERPARM_MODE] > numStates.getVal()) {
                    renderEntity.shaderParms[SHADERPARM_MODE] = 0;
                }
            } else if (forceState.getVal() != 0) {
                renderEntity.shaderParms[SHADERPARM_MODE] = forceState.getVal();
            } else {
                renderEntity.shaderParms[SHADERPARM_MODE] = gameLocal.random.RandomInt(numStates.getVal()) + 1;
            }

            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);

            ActivateTargets(activator);

            if (spawnArgs.GetBool("hideWhenBroken")) {
                Hide();
                PostEventMS(EV_RestoreDamagable, nextTriggerTime.getVal() - gameLocal.time);
                BecomeActive(TH_THINK);
            }
        }

        private void Event_RestoreDamagable() {
            health = spawnArgs.GetInt("health", "5");
            Show();
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
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
    public static class idExplodable extends idEntity {
        //	CLASS_PROTOTYPE( idExplodable );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idExplodable>) idExplodable::Event_Explode);
        }

        private static void Event_Explode(idExplodable e, idEventArg<idEntity> activator) {
            String[] temp = {null};

            if (e.spawnArgs.GetString("def_damage", "damage_explosion", temp)) {
                gameLocal.RadiusDamage(e.GetPhysics().GetOrigin(), activator.value, activator.value, e, e, temp[0]);
            }

            e.StartSound("snd_explode", SND_CHANNEL_ANY, 0, false, null);

            // Show() calls UpdateVisuals, so we don't need to call it ourselves after setting the shaderParms
            e.renderEntity.shaderParms[SHADERPARM_RED] = 1.0f;
            e.renderEntity.shaderParms[SHADERPARM_GREEN] = 1.0f;
            e.renderEntity.shaderParms[SHADERPARM_BLUE] = 1.0f;
            e.renderEntity.shaderParms[SHADERPARM_ALPHA] = 1.0f;
            e.renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);
            e.renderEntity.shaderParms[SHADERPARM_DIVERSITY] = 0.0f;
            e.Show();

            e.PostEventMS(EV_Remove, 2000);

            e.ActivateTargets(activator.value);
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            Hide();
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idSpring

     ===============================================================================
     */
    public static class idSpring extends idEntity {
        //	CLASS_PROTOTYPE( idSpring );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_PostSpawn, (eventCallback_t0<idSpring>) idSpring::Event_LinkSpring);
        }

        private final CInt id1 = new CInt();
        private final CInt id2 = new CInt();
        private idEntity ent1;
        private idEntity ent2;
        private idVec3 p1;
        private idVec3 p2;
        private idForce_Spring spring;
        //
        //

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            CFloat Kstretch = new CFloat(), damping = new CFloat(), restLength = new CFloat();

            spawnArgs.GetInt("id1", "0", id1);
            spawnArgs.GetInt("id2", "0", id2);
            spawnArgs.GetVector("point1", "0 0 0", p1);
            spawnArgs.GetVector("point2", "0 0 0", p2);
            spawnArgs.GetFloat("constant", "100.0f", Kstretch);
            spawnArgs.GetFloat("damping", "10.0f", damping);
            spawnArgs.GetFloat("restlength", "0.0f", restLength);

            spring.InitSpring(Kstretch.getVal(), 0.0f, damping.getVal(), restLength.getVal());

            ent1 = ent2 = null;

            PostEventMS(EV_PostSpawn, 0);
        }

        @Override
        public void Think() {
            idVec3 start, end, origin;
            idMat3 axis;

            // run physics
            RunPhysics();

            if ((thinkFlags & TH_THINK) != 0) {
                // evaluate force
                spring.Evaluate(gameLocal.time);

                start = p1;
                if (ent1.GetPhysics() != null) {
                    axis = ent1.GetPhysics().GetAxis();
                    origin = ent1.GetPhysics().GetOrigin();
                    start = origin.oPlus(start.oMultiply(axis));
                }

                end = p2;
                if (ent2.GetPhysics() != null) {
                    axis = ent2.GetPhysics().GetAxis();
                    origin = ent2.GetPhysics().GetOrigin();
                    end = origin.oPlus(p2.oMultiply(axis));
                }

                gameRenderWorld.DebugLine(new idVec4(1, 1, 0, 1), start, end, 0, true);
            }

            Present();
        }

        private void Event_LinkSpring() {
            idStr name1 = new idStr(), name2 = new idStr();

            spawnArgs.GetString("ent1", "", name1);
            spawnArgs.GetString("ent2", "", name2);

            if (name1.Length() != 0) {
                ent1 = gameLocal.FindEntity(name1.toString());
                if (null == ent1) {
                    idGameLocal.Error("idSpring '%s' at (%s): cannot find first entity '%s'", name, GetPhysics().GetOrigin().ToString(0), name1);
                }
            } else {
                ent1 = gameLocal.entities[ENTITYNUM_WORLD];
            }

            if (name2.Length() != 0) {
                ent2 = gameLocal.FindEntity(name2.toString());
                if (null == ent2) {
                    idGameLocal.Error("idSpring '%s' at (%s): cannot find second entity '%s'", name, GetPhysics().GetOrigin().ToString(0), name2);
                }
            } else {
                ent2 = gameLocal.entities[ENTITYNUM_WORLD];
            }
            spring.SetPosition(ent1.GetPhysics(), id1.getVal(), p1, ent2.GetPhysics(), id2.getVal(), p2);
            BecomeActive(TH_THINK);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    public static class idForceField extends idEntity {
        // CLASS_PROTOTYPE( idForceField );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idForceField>) idForceField::Event_Activate);
            eventCallbacks.put(EV_Toggle, (eventCallback_t0<idForceField>) idForceField::Event_Toggle);
            eventCallbacks.put(EV_FindTargets, (eventCallback_t0<idForceField>) idForceField::Event_FindTargets);
        }

        private final idForce_Field forceField = new idForce_Field();
        //
        //

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteStaticObject(forceField);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadStaticObject(forceField);
        }

        @Override
        public void Spawn() {
            super.Spawn();

            idVec3 uniform = new idVec3();
            CFloat explosion = new CFloat(), implosion = new CFloat(), randomTorque = new CFloat();

            if (spawnArgs.GetVector("uniform", "0 0 0", uniform)) {
                forceField.Uniform(uniform);
            } else if (spawnArgs.GetFloat("explosion", "0", explosion)) {
                forceField.Explosion(explosion.getVal());
            } else if (spawnArgs.GetFloat("implosion", "0", implosion)) {
                forceField.Implosion(implosion.getVal());
            }

            if (spawnArgs.GetFloat("randomTorque", "0", randomTorque)) {
                forceField.RandomTorque(randomTorque.getVal());
            }

            if (spawnArgs.GetBool("applyForce", "0")) {
                forceField.SetApplyType(FORCEFIELD_APPLY_FORCE);
            } else if (spawnArgs.GetBool("applyImpulse", "0")) {
                forceField.SetApplyType(FORCEFIELD_APPLY_IMPULSE);
            } else {
                forceField.SetApplyType(FORCEFIELD_APPLY_VELOCITY);
            }

            forceField.SetPlayerOnly(spawnArgs.GetBool("playerOnly", "0"));
            forceField.SetMonsterOnly(spawnArgs.GetBool("monsterOnly", "0"));

            // set the collision model on the force field
            forceField.SetClipModel(new idClipModel(GetPhysics().GetClipModel()));

            // remove the collision model from the physics object
            GetPhysics().SetClipModel(null, 1.0f);

            if (spawnArgs.GetBool("start_on")) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void Think() {
            if ((thinkFlags & TH_THINK) != 0) {
                // evaluate force
                forceField.Evaluate(gameLocal.time);
            }
            Present();
        }

        private void Toggle() {
            if ((thinkFlags & TH_THINK) != 0) {
                BecomeInactive(TH_THINK);
            } else {
                BecomeActive(TH_THINK);
            }
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            CFloat wait = new CFloat();

            Toggle();
            if (spawnArgs.GetFloat("wait", "0.01", wait)) {
                PostEventSec(EV_Toggle, wait.getVal());
            }
        }

        private void Event_Toggle() {
            Toggle();
        }

        private void Event_FindTargets() {
            FindTargets();
            RemoveNullTargets();
            if (targets.Num() != 0) {
                forceField.Uniform(targets.oGet(0).GetEntity().GetPhysics().GetOrigin().oMinus(GetPhysics().GetOrigin()));
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    public static class idAnimated extends idAFEntity_Gibbable {
        // CLASS_PROTOTYPE( idAnimated );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idAFEntity_Gibbable.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idAnimated>) idAnimated::Event_Activate);
            eventCallbacks.put(EV_Animated_Start, (eventCallback_t0<idAnimated>) idAnimated::Event_Start);
            eventCallbacks.put(EV_StartRagdoll, (eventCallback_t0<idAnimated>) idAnimated::Event_StartRagdoll);
            eventCallbacks.put(EV_AnimDone, (eventCallback_t1<idAnimated>) idAnimated::Event_AnimDone);
            eventCallbacks.put(EV_Footstep, (eventCallback_t0<idAnimated>) idAnimated::Event_Footstep);
            eventCallbacks.put(EV_FootstepLeft, (eventCallback_t0<idAnimated>) idAnimated::Event_Footstep);
            eventCallbacks.put(EV_FootstepRight, (eventCallback_t0<idAnimated>) idAnimated::Event_Footstep);
            eventCallbacks.put(EV_LaunchMissiles, (eventCallback_t6<idAnimated>) idAnimated::Event_LaunchMissiles);
            eventCallbacks.put(EV_LaunchMissilesUpdate, (eventCallback_t4<idAnimated>) idAnimated::Event_LaunchMissilesUpdate);
        }

        private final idEntityPtr<idEntity> activator;
        private boolean activated;
        private int anim;
        private int blendFrames;
        private int current_anim_index;
        private int num_anims;
        private int/*jointHandle_t*/  soundJoint;
        //
        //

        public idAnimated() {
            anim = 0;
            blendFrames = 0;
            soundJoint = INVALID_JOINT;
            activated = false;
            combatModel = null;
            activator = new idEntityPtr<>();
            current_anim_index = 0;
            num_anims = 0;

        }
        // ~idAnimated();

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(current_anim_index);
            savefile.WriteInt(num_anims);
            savefile.WriteInt(anim);
            savefile.WriteInt(blendFrames);
            savefile.WriteJoint(soundJoint);
            activator.Save(savefile);
            savefile.WriteBool(activated);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt current_anim_index = new CInt(), num_anims = new CInt(), anim = new CInt(), blendFrames = new CInt(), soundJoint = new CInt();
            CBool activated = new CBool(false);

            savefile.ReadInt(current_anim_index);
            savefile.ReadInt(num_anims);
            savefile.ReadInt(anim);
            savefile.ReadInt(blendFrames);
            savefile.ReadJoint(soundJoint);
            activator.Restore(savefile);
            savefile.ReadBool(activated);

            this.current_anim_index = current_anim_index.getVal();
            this.num_anims = num_anims.getVal();
            this.anim = anim.getVal();
            this.blendFrames = blendFrames.getVal();
            this.soundJoint = soundJoint.getVal();
            this.activated = activated.isVal();
        }

        @Override
        public void Spawn() {
            super.Spawn();

            String[] animname = new String[1];
            int anim2;
            CFloat wait = new CFloat();
            final String joint;
            CInt num_anims2 = new CInt();

            joint = spawnArgs.GetString("sound_bone", "origin");
            soundJoint = animator.GetJointHandle(joint);
            if (soundJoint == INVALID_JOINT) {
                gameLocal.Warning("idAnimated '%s' at (%s): cannot find joint '%s' for sound playback", name, GetPhysics().GetOrigin().ToString(0), joint);
            }

            LoadAF();

            // allow bullets to collide with a combat model
            if (spawnArgs.GetBool("combatModel", "0")) {
                combatModel = new idClipModel(modelDefHandle);
            }

            // allow the entity to take damage
            if (spawnArgs.GetBool("takeDamage", "0")) {
                fl.takedamage = true;
            }

            blendFrames = 0;

            current_anim_index = 0;
            spawnArgs.GetInt("num_anims", "0", num_anims2);
            num_anims = num_anims2.getVal();

            blendFrames = spawnArgs.GetInt("blend_in");

            animname[0] = spawnArgs.GetString(num_anims != 0 ? "anim1" : "anim");
            if (0 == animname[0].length()) {
                anim = 0;
            } else {
                anim = animator.GetAnim(animname[0]);
                if (0 == anim) {
                    idGameLocal.Error("idAnimated '%s' at (%s): cannot find anim '%s'", name, GetPhysics().GetOrigin().ToString(0), animname[0]);
                }
            }

            if (spawnArgs.GetBool("hide")) {
                Hide();

                if (0 == num_anims) {
                    blendFrames = 0;
                }
            } else if (spawnArgs.GetString("start_anim", "", animname)) {
                anim2 = animator.GetAnim(animname[0]);
                if (0 == anim2) {
                    idGameLocal.Error("idAnimated '%s' at (%s): cannot find anim '%s'", name, GetPhysics().GetOrigin().ToString(0), animname[0]);
                }
                animator.CycleAnim(ANIMCHANNEL_ALL, anim2, gameLocal.time, 0);
            } else if (anim != 0) {
                // init joints to the first frame of the animation
                animator.SetFrame(ANIMCHANNEL_ALL, anim, 1, gameLocal.time, 0);

                if (0 == num_anims) {
                    blendFrames = 0;
                }
            }

            spawnArgs.GetFloat("wait", "-1", wait);

            if (wait.getVal() >= 0) {
                PostEventSec(EV_Activate, wait.getVal(), this);
            }
        }

        @Override
        public boolean LoadAF() {
            String[] fileName = new String[1];

            if (!spawnArgs.GetString("ragdoll", "*unknown*", fileName)) {
                return false;
            }
            af.SetAnimator(GetAnimator());
            return af.Load(this, fileName[0]);
        }

        public boolean StartRagdoll() {
            // if no AF loaded
            if (!af.IsLoaded()) {
                return false;
            }

            // if the AF is already active
            if (af.IsActive()) {
                return true;
            }

            // disable any collision model used
            GetPhysics().DisableClip();

            // start using the AF
            af.StartFromCurrentPose(spawnArgs.GetInt("velocityTime", "0"));

            return true;
        }

        @Override
        public boolean GetPhysicsToSoundTransform(idVec3 origin, idMat3 axis) {
            animator.GetJointTransform(soundJoint, gameLocal.time, origin, axis);
            axis.oSet(renderEntity.axis);
            return true;
        }

        private void PlayNextAnim() {
            String[] animName = new String[1];
            int len;
            CInt cycle = new CInt();

            if (current_anim_index >= num_anims) {
                Hide();
                if (spawnArgs.GetBool("remove")) {
                    PostEventMS(EV_Remove, 0);
                } else {
                    current_anim_index = 0;
                }
                return;
            }

            Show();
            current_anim_index++;

            spawnArgs.GetString(va("anim%d", current_anim_index), null, animName);
            if (animName[0].isEmpty()) {
                anim = 0;
                animator.Clear(ANIMCHANNEL_ALL, gameLocal.time, FRAME2MS(blendFrames));
                return;
            }

            anim = animator.GetAnim(animName[0]);
            if (0 == anim) {
                gameLocal.Warning("missing anim '%s' on %s", animName[0], name);
                return;
            }

            if (g_debugCinematic.GetBool()) {
                gameLocal.Printf("%d: '%s' start anim '%s'\n", gameLocal.framenum, GetName(), animName[0]);
            }

            spawnArgs.GetInt("cycle", "1", cycle);
            if ((current_anim_index == num_anims) && spawnArgs.GetBool("loop_last_anim")) {
                cycle.setVal(-1);
            }

            animator.CycleAnim(ANIMCHANNEL_ALL, anim, gameLocal.time, FRAME2MS(blendFrames));
            animator.CurrentAnim(ANIMCHANNEL_ALL).SetCycleCount(cycle.getVal());

            len = animator.CurrentAnim(ANIMCHANNEL_ALL).PlayLength();
            if (len >= 0) {
                PostEventMS(EV_AnimDone, len, current_anim_index);
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);

            animator.ForceUpdate();
            UpdateAnimation();
            UpdateVisuals();
            Present();
        }

        private void Event_Activate(idEventArg<idEntity> _activator) {
            if (num_anims != 0) {
                PlayNextAnim();
                activator.oSet(_activator.value);
                return;
            }

            if (activated) {
                // already activated
                return;
            }

            activated = true;
            activator.oSet(_activator.value);
            ProcessEvent(EV_Animated_Start);
        }

        private void Event_Start() {
            CInt cycle = new CInt();
            int len;

            Show();

            if (num_anims != 0) {
                PlayNextAnim();
                return;
            }

            if (anim != 0) {
                if (g_debugCinematic.GetBool()) {
                    final idAnim animPtr = animator.GetAnim(anim);
                    gameLocal.Printf("%d: '%s' start anim '%s'\n", gameLocal.framenum, GetName(), animPtr != null ? animPtr.Name() : "");
                }
                spawnArgs.GetInt("cycle", "1", cycle);
                animator.CycleAnim(ANIMCHANNEL_ALL, anim, gameLocal.time, FRAME2MS(blendFrames));
                animator.CurrentAnim(ANIMCHANNEL_ALL).SetCycleCount(cycle.getVal());

                len = animator.CurrentAnim(ANIMCHANNEL_ALL).PlayLength();
                if (len >= 0) {
                    PostEventMS(EV_AnimDone, len, 1);
                }
            }

            // offset the start time of the shader to sync it to the game time
            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);

            animator.ForceUpdate();
            UpdateAnimation();
            UpdateVisuals();
            Present();
        }

        private void Event_StartRagdoll() {
            StartRagdoll();
        }

        private void Event_AnimDone(idEventArg<Integer> animIndex) {
            if (g_debugCinematic.GetBool()) {
                final idAnim animPtr = animator.GetAnim(anim);
                gameLocal.Printf("%d: '%s' end anim '%s'\n", gameLocal.framenum, GetName(), animPtr != null ? animPtr.Name() : "");
            }

            if ((animIndex.value >= num_anims) && spawnArgs.GetBool("remove")) {
                Hide();
                PostEventMS(EV_Remove, 0);
            } else if (spawnArgs.GetBool("auto_advance")) {
                PlayNextAnim();
            } else {
                activated = false;
            }

            ActivateTargets(activator.GetEntity());
        }

        private void Event_Footstep() {
            StartSound("snd_footstep", SND_CHANNEL_BODY, 0, false, null);
        }

        private void Event_LaunchMissiles(final idEventArg<String> projectilename, final idEventArg<String> sound, final idEventArg<String> launchjoint,
                                          final idEventArg<String> targetjoint, idEventArg<Integer> numshots, idEventArg<Integer> framedelay) {
            idDict projectileDef;
            int/*jointHandle_t*/ launch;
            int/*jointHandle_t*/ target;

            projectileDef = gameLocal.FindEntityDefDict(projectilename.value, false);
            if (null == projectileDef) {
                gameLocal.Warning("idAnimated '%s' at (%s): unknown projectile '%s'", name, GetPhysics().GetOrigin().ToString(0), projectilename.value);
                return;
            }

            launch = animator.GetJointHandle(launchjoint.value);
            if (launch == INVALID_JOINT) {
                gameLocal.Warning("idAnimated '%s' at (%s): unknown launch joint '%s'", name, GetPhysics().GetOrigin().ToString(0), launchjoint.value);
                idGameLocal.Error("Unknown joint '%s'", launchjoint.value);
            }

            target = animator.GetJointHandle(targetjoint.value);
            if (target == INVALID_JOINT) {
                gameLocal.Warning("idAnimated '%s' at (%s): unknown target joint '%s'", name, GetPhysics().GetOrigin().ToString(0), targetjoint.value);
            }

            spawnArgs.Set("projectilename", projectilename.value);
            spawnArgs.Set("missilesound", sound.value);

            CancelEvents(EV_LaunchMissilesUpdate);
            ProcessEvent(EV_LaunchMissilesUpdate, launch, target, numshots.value - 1, framedelay.value);
        }

        private void Event_LaunchMissilesUpdate(idEventArg<Integer> launchjoint, idEventArg<Integer> targetjoint, idEventArg<Integer> numshots, idEventArg<Integer> framedelay) {
            idVec3 launchPos = new idVec3();
            idVec3 targetPos = new idVec3();
            idMat3 axis = new idMat3();
            idVec3 dir;
            idEntity[] ent = {null};
            idProjectile projectile;
            idDict projectileDef;
            String projectilename;

            projectilename = spawnArgs.GetString("projectilename");
            projectileDef = gameLocal.FindEntityDefDict(projectilename, false);
            if (null == projectileDef) {
                gameLocal.Warning("idAnimated '%s' at (%s): 'launchMissiles' called with unknown projectile '%s'", name, GetPhysics().GetOrigin().ToString(0), projectilename);
                return;
            }

            StartSound("snd_missile", SND_CHANNEL_WEAPON, 0, false, null);

            animator.GetJointTransform(launchjoint.value, gameLocal.time, launchPos, axis);
            launchPos = renderEntity.origin.oPlus(launchPos.oMultiply(renderEntity.axis));

            animator.GetJointTransform(targetjoint.value, gameLocal.time, targetPos, axis);
            targetPos = renderEntity.origin.oPlus(targetPos.oMultiply(renderEntity.axis));

            dir = targetPos.oMinus(launchPos);
            dir.Normalize();

            gameLocal.SpawnEntityDef(projectileDef, ent, false);
            if (null == ent[0] || !(ent[0] instanceof idProjectile)) {
                idGameLocal.Error("idAnimated '%s' at (%s): in 'launchMissiles' call '%s' is not an idProjectile", name, GetPhysics().GetOrigin().ToString(0), projectilename);
            }
            projectile = (idProjectile) ent[0];
            projectile.Create(this, launchPos, dir);
            projectile.Launch(launchPos, dir, getVec3_origin());

            if (numshots.value > 0) {
                PostEventMS(EV_LaunchMissilesUpdate, FRAME2MS(framedelay.value), launchjoint.value, targetjoint.value, numshots.value - 1, framedelay.value);
            }
        }

        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idStaticEntity

     Some static entities may be optimized into inline geometry by dmap

     ===============================================================================
     */
    public static class idStaticEntity extends idEntity {
        // CLASS_PROTOTYPE( idStaticEntity );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idStaticEntity>) idStaticEntity::Event_Activate);
        }

        private final idVec4 fadeFrom;
        private final idVec4 fadeTo;
        private boolean active;
        private int fadeEnd;
        private int fadeStart;
        private boolean runGui;
        private int spawnTime;
        //
        //

        public idStaticEntity() {
            spawnTime = 0;
            active = false;
            fadeFrom = new idVec4(1, 1, 1, 1);
            fadeTo = new idVec4(1, 1, 1, 1);
            fadeStart = 0;
            fadeEnd = 0;
            runGui = false;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(spawnTime);
            savefile.WriteBool(active);
            savefile.WriteVec4(fadeFrom);
            savefile.WriteVec4(fadeTo);
            savefile.WriteInt(fadeStart);
            savefile.WriteInt(fadeEnd);
            savefile.WriteBool(runGui);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt spawnTime = new CInt(), fadeStart = new CInt(), fadeEnd = new CInt();//TODO:make sure the dumbass compiler doesn't decide that all {0}'s are the same (lol)
            CBool active = new CBool(), runGui = new CBool();

            savefile.ReadInt(spawnTime);
            savefile.ReadBool(active);
            savefile.ReadVec4(fadeFrom);
            savefile.ReadVec4(fadeTo);
            savefile.ReadInt(fadeStart);
            savefile.ReadInt(fadeEnd);
            savefile.ReadBool(runGui);

            this.spawnTime = spawnTime.getVal();
            this.fadeStart = fadeStart.getVal();
            this.fadeEnd = fadeEnd.getVal();
            this.active = active.isVal();
            this.runGui = runGui.isVal();
        }

        @Override
        public void Spawn() {
            super.Spawn();

            boolean solid;
            boolean hidden;

            // an inline static model will not do anything at all
            if (spawnArgs.GetBool("inline") || gameLocal.world.spawnArgs.GetBool("inlineAllStatics")) {
                Hide();
                return;
            }

            solid = spawnArgs.GetBool("solid");
            hidden = spawnArgs.GetBool("hide");

            if (solid && !hidden) {
                GetPhysics().SetContents(CONTENTS_SOLID);
            } else {
                GetPhysics().SetContents(0);
            }

            spawnTime = gameLocal.time;
            active = false;

            idStr model = new idStr(spawnArgs.GetString("model"));
            if (model.Find(".prt") >= 0) {
                // we want the parametric particles out of sync with each other
                renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = gameLocal.random.RandomInt(32767);
            }

            fadeFrom.Set(1, 1, 1, 1);
            fadeTo.Set(1, 1, 1, 1);
            fadeStart = 0;
            fadeEnd = 0;

            // NOTE: this should be used very rarely because it is expensive
            runGui = spawnArgs.GetBool("runGui");
            if (runGui) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void ShowEditingDialog() {
            common.InitTool(EDITOR_PARTICLE, spawnArgs);
        }

        @Override
        public void Hide() {
            super.Hide();
            GetPhysics().SetContents(0);
        }

        @Override
        public void Show() {
            super.Show();
            if (spawnArgs.GetBool("solid")) {
                GetPhysics().SetContents(CONTENTS_SOLID);
            }
        }

        public void Fade(final idVec4 to, float fadeTime) {
            GetColor(fadeFrom);
            fadeTo.oSet(to);
            fadeStart = gameLocal.time;
            fadeEnd = (int) (gameLocal.time + SEC2MS(fadeTime));
            BecomeActive(TH_THINK);
        }

        @Override
        public void Think() {
            super.Think();
            if ((thinkFlags & TH_THINK) != 0) {
                if (runGui && renderEntity.gui[0] != null) {
                    idPlayer player = gameLocal.GetLocalPlayer();
                    if (player != null) {
                        if (!player.objectiveSystemOpen) {
                            renderEntity.gui[0].StateChanged(gameLocal.time, true);
                            if (renderEntity.gui[1] != null) {
                                renderEntity.gui[1].StateChanged(gameLocal.time, true);
                            }
                            if (renderEntity.gui[2] != null) {
                                renderEntity.gui[2].StateChanged(gameLocal.time, true);
                            }
                        }
                    }
                }
                if (fadeEnd > 0) {
                    idVec4 color = new idVec4();
                    if (gameLocal.time < fadeEnd) {
                        color.Lerp(fadeFrom, fadeTo, (float) (gameLocal.time - fadeStart) / (float) (fadeEnd - fadeStart));
                    } else {
                        color = fadeTo;
                        fadeEnd = 0;
                        BecomeInactive(TH_THINK);
                    }
                    SetColor(color);
                }
            }
        }

        @Override
        public void WriteToSnapshot(idBitMsgDelta msg) {
            GetPhysics().WriteToSnapshot(msg);
            WriteBindToSnapshot(msg);
            WriteColorToSnapshot(msg);
            WriteGUIToSnapshot(msg);
            msg.WriteBits(IsHidden() ? 1 : 0, 1);
        }

        @Override
        public void ReadFromSnapshot(final idBitMsgDelta msg) {
            boolean hidden;

            GetPhysics().ReadFromSnapshot(msg);
            ReadBindFromSnapshot(msg);
            ReadColorFromSnapshot(msg);
            ReadGUIFromSnapshot(msg);
            hidden = msg.ReadBits(1) == 1;
            if (hidden != IsHidden()) {
                if (hidden) {
                    Hide();
                } else {
                    Show();
                }
            }
            if (msg.HasChanged()) {
                UpdateVisuals();
            }
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            idStr activateGui;

            spawnTime = gameLocal.time;
            active = !active;

            final idKeyValue kv = spawnArgs.FindKey("hide");
            if (kv != null) {
                if (IsHidden()) {
                    Show();
                } else {
                    Hide();
                }
            }

            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(spawnTime);
            renderEntity.shaderParms[5] = active ? 1 : 0;
            // this change should be a good thing, it will automatically turn on
            // lights etc.. when triggered so that does not have to be specifically done
            // with trigger parms.. it MIGHT break things so need to keep an eye on it
            renderEntity.shaderParms[SHADERPARM_MODE] = (renderEntity.shaderParms[SHADERPARM_MODE] != 0) ? 0.0f : 1.0f;
            BecomeActive(TH_UPDATEVISUALS);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idFuncEmitter

     ===============================================================================
     */
    public static class idFuncEmitter extends idStaticEntity {
        // CLASS_PROTOTYPE( idFuncEmitter );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idStaticEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncEmitter>) idFuncEmitter::Event_Activate);
        }

        private final CBool hidden = new CBool(false);
        //
        //

        public idFuncEmitter() {
            hidden.setVal(false);
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteBool(hidden.isVal());
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadBool(hidden);
        }

        @Override
        public void Spawn() {
            super.Spawn();

            if (spawnArgs.GetBool("start_off")) {
                hidden.setVal(true);
                renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] = MS2SEC(1);
                UpdateVisuals();
            } else {
                hidden.setVal(false);
            }
        }

        public void Event_Activate(idEventArg<idEntity> activator) {
            if (hidden.isVal() || spawnArgs.GetBool("cycleTrigger")) {
                renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] = 0;
                renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = -MS2SEC(gameLocal.time);
                hidden.setVal(false);
            } else {
                renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] = MS2SEC(gameLocal.time);
                hidden.setVal(true);
            }
            UpdateVisuals();
        }

        @Override
        public void WriteToSnapshot(idBitMsgDelta msg) {
            msg.WriteBits(hidden.isVal() ? 1 : 0, 1);
            msg.WriteFloat(renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME]);
            msg.WriteFloat(renderEntity.shaderParms[SHADERPARM_TIMEOFFSET]);
        }

        @Override
        public void ReadFromSnapshot(final idBitMsgDelta msg) {
            hidden.setVal(msg.ReadBits(1) != 0);
            renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] = msg.ReadFloat();
            renderEntity.shaderParms[SHADERPARM_TIMEOFFSET] = msg.ReadFloat();
            if (msg.HasChanged()) {
                UpdateVisuals();
            }
        }

        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idFuncSmoke

     ===============================================================================
     */
    public static class idFuncSmoke extends idEntity {
        // CLASS_PROTOTYPE( idFuncSmoke );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncSmoke>) idFuncSmoke::Event_Activate);
        }

        private boolean restart;
        private idDeclParticle smoke;
        private int smokeTime;
        //
        //

        public idFuncSmoke() {
            smokeTime = 0;
            smoke = null;
            restart = false;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            final String smokeName = spawnArgs.GetString("smoke");
            if (!smokeName.isEmpty()) {// != '\0' ) {
                smoke = (idDeclParticle) declManager.FindType(DECL_PARTICLE, smokeName);
            } else {
                smoke = null;
            }
            if (spawnArgs.GetBool("start_off")) {
                smokeTime = 0;
                restart = false;
            } else if (smoke != null) {
                smokeTime = gameLocal.time;
                BecomeActive(TH_UPDATEPARTICLES);
                restart = true;
            }
            GetPhysics().SetContents(0);
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(smokeTime);
            savefile.WriteParticle(smoke);
            savefile.WriteBool(restart);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt smokeTime = new CInt();
            CBool restart = new CBool();

            savefile.ReadInt(smokeTime);
            savefile.ReadParticle(smoke);
            savefile.ReadBool(restart);

            this.smokeTime = smokeTime.getVal();
            this.restart = restart.isVal();
        }

        @Override
        public void Think() {

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant() || smoke == null || smokeTime == -1) {
                return;
            }

            if ((thinkFlags & TH_UPDATEPARTICLES) != 0 && !IsHidden()) {
                if (!gameLocal.smokeParticles.EmitSmoke(smoke, smokeTime, gameLocal.random.CRandomFloat(), GetPhysics().GetOrigin(), GetPhysics().GetAxis())) {
                    if (restart) {
                        smokeTime = gameLocal.time;
                    } else {
                        smokeTime = 0;
                        BecomeInactive(TH_UPDATEPARTICLES);
                    }
                }
            }

        }

        public void Event_Activate(idEventArg<idEntity> activator) {
            if ((thinkFlags & TH_UPDATEPARTICLES) != 0) {
                restart = false;
//                return;
            } else {
                BecomeActive(TH_UPDATEPARTICLES);
                restart = true;
                smokeTime = gameLocal.time;
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    public static class idFuncSplat extends idFuncEmitter {
        // CLASS_PROTOTYPE( idFuncSplat );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idFuncEmitter.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncSplat>) idFuncSplat::Event_Activate);
            eventCallbacks.put(EV_Splat, (eventCallback_t0<idFuncSplat>) idFuncSplat::Event_Splat);
        }

        public idFuncSplat() {
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
        }

        @Override
        public void Event_Activate(idEventArg<idEntity> activator) {
            super.Event_Activate(activator);
            PostEventSec(EV_Splat, spawnArgs.GetFloat("splatDelay", "0.25"));
            StartSound("snd_spurt", SND_CHANNEL_ANY, 0, false, null);
        }

        private void Event_Splat() {
            String splat;
            int count = spawnArgs.GetInt("splatCount", "1");
            for (int i = 0; i < count; i++) {
                splat = spawnArgs.RandomPrefix("mtr_splat", gameLocal.random);
                if (splat != null && !splat.isEmpty()) {
                    float size = spawnArgs.GetFloat("splatSize", "128");
                    float dist = spawnArgs.GetFloat("splatDistance", "128");
                    float angle = spawnArgs.GetFloat("splatAngle", "0");
                    gameLocal.ProjectDecal(GetPhysics().GetOrigin(), GetPhysics().GetAxis().oGet(2), dist, true, size, splat, angle);
                }
            }
            StartSound("snd_splat", SND_CHANNEL_ANY, 0, false, null);
        }

        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idTextEntity

     ===============================================================================
     */
    public static class idTextEntity extends idEntity {
        // CLASS_PROTOTYPE( idTextEntity );

        private boolean playerOriented;
        private idStr text;
        //
        //

        @Override
        public void Spawn() {
            // these are cached as the are used each frame
            text.oSet(spawnArgs.GetString("text"));
            playerOriented = spawnArgs.GetBool("playerOriented");
            boolean force = spawnArgs.GetBool("force");
            if (com_developer.GetBool() || force) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteString(text);
            savefile.WriteBool(playerOriented);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CBool playerOriented = new CBool(false);

            savefile.ReadString(text);
            savefile.ReadBool(playerOriented);

            this.playerOriented = playerOriented.isVal();
        }

        @Override
        public void Think() {
            if ((thinkFlags & TH_THINK) != 0) {
                gameRenderWorld.DrawText(text.toString(), GetPhysics().GetOrigin(), 0.25f, colorWhite, playerOriented ? gameLocal.GetLocalPlayer().viewAngles.ToMat3() : GetPhysics().GetAxis().Transpose(), 1);
                for (int i = 0; i < targets.Num(); i++) {
                    if (targets.oGet(i).GetEntity() != null) {
                        gameRenderWorld.DebugArrow(colorBlue, GetPhysics().GetOrigin(), targets.oGet(i).GetEntity().GetPhysics().GetOrigin(), 1);
                    }
                }
            } else {
                BecomeInactive(TH_ALL);
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


    }

    /*
     ===============================================================================

     idLocationEntity

     ===============================================================================
     */
    public static class idLocationEntity extends idEntity {
        // CLASS_PROTOTYPE( idLocationEntity );

        @Override
        public void Spawn() {
            super.Spawn();

            String[] realName = new String[1];

            // this just holds dict information
            // if "location" not already set, use the entity name.
            if (!spawnArgs.GetString("location", "", realName)) {
                spawnArgs.Set("location", name);
            }
        }

        public String GetLocation() {
            return spawnArgs.GetString("location");
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


    }

    /*
     ===============================================================================

     idLocationSeparatorEntity

     ===============================================================================
     */
    public static class idLocationSeparatorEntity extends idEntity {
        // CLASS_PROTOTYPE( idLocationSeparatorEntity );

        @Override
        public void Spawn() {
            super.Spawn();

            idBounds b;

            b = new idBounds(spawnArgs.GetVector("origin")).Expand(16);
            int/*qhandle_t*/ portal = gameRenderWorld.FindPortal(b);
            if (0 == portal) {
                gameLocal.Warning("LocationSeparator '%s' didn't contact a portal", spawnArgs.GetString("name"));
            }
            gameLocal.SetPortalState(portal, etoi(PS_BLOCK_LOCATION));
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /*
     ===============================================================================

     idVacuumSeperatorEntity

     Can be triggered to let vacuum through a portal (blown out window)

     ===============================================================================
     */
    public static class idVacuumSeparatorEntity extends idEntity {
        // CLASS_PROTOTYPE( idVacuumSeparatorEntity );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idVacuumSeparatorEntity>) idVacuumSeparatorEntity::Event_Activate);
        }

        //
//
        private int/*qhandle_t*/ portal;

        public idVacuumSeparatorEntity() {
            portal = 0;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            idBounds b;

            b = new idBounds(spawnArgs.GetVector("origin")).Expand(16);
            portal = gameRenderWorld.FindPortal(b);
            if (0 == portal) {
                gameLocal.Warning("VacuumSeparator '%s' didn't contact a portal", spawnArgs.GetString("name"));
                return;
            }
            gameLocal.SetPortalState(portal, (etoi(PS_BLOCK_AIR) | etoi(PS_BLOCK_LOCATION)));
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(portal);
            savefile.WriteInt(gameRenderWorld.GetPortalState(portal));
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt state = new CInt(), portal = new CInt();

            savefile.ReadInt(portal);
            savefile.ReadInt(state);

            this.portal = portal.getVal();
            gameLocal.SetPortalState(portal.getVal(), state.getVal());
        }

        public void Event_Activate(idEventArg<idEntity> activator) {
            if (0 == portal) {
                return;
            }
            gameLocal.SetPortalState(portal, etoi(PS_BLOCK_NONE));
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idVacuumEntity

     Levels should only have a single vacuum entity.

     ===============================================================================
     */
    public static class idVacuumEntity extends idEntity {
// public:
        // CLASS_PROTOTYPE( idVacuumEntity );

        @Override
        public void Spawn() {
            super.Spawn();

            if (gameLocal.vacuumAreaNum != -1) {
                gameLocal.Warning("idVacuumEntity::Spawn: multiple idVacuumEntity in level");
                return;
            }

            idVec3 org = spawnArgs.GetVector("origin");

            gameLocal.vacuumAreaNum = gameRenderWorld.PointInArea(org);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /*
     ===============================================================================

     idBeam

     ===============================================================================
     */
    public static class idBeam extends idEntity {
        // CLASS_PROTOTYPE( idBeam );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_PostSpawn, (eventCallback_t0<idBeam>) idBeam::Event_MatchTarget);
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idBeam>) idBeam::Event_Activate);
        }

        private final idEntityPtr<idBeam> master;
        private final idEntityPtr<idBeam> target;
        //
        //

        public idBeam() {
            target = new idEntityPtr<>();
            master = new idEntityPtr<>();
        }

        @Override
        public void Spawn() {
            super.Spawn();

            CFloat width = new CFloat();

            if (spawnArgs.GetFloat("width", "0", width)) {
                renderEntity.shaderParms[SHADERPARM_BEAM_WIDTH] = width.getVal();
            }

            SetModel("_BEAM");
            Hide();
            PostEventMS(EV_PostSpawn, 0);
        }

        @Override
        public void Save(idSaveGame savefile) {
            target.Save(savefile);
            master.Save(savefile);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            target.Restore(savefile);
            master.Restore(savefile);
        }

        @Override
        public void Think() {
            idBeam masterEnt;

            if (!IsHidden() && null == target.GetEntity()) {
                // hide if our target is removed
                Hide();
            }

            RunPhysics();

            masterEnt = master.GetEntity();
            if (masterEnt != null) {
                final idVec3 origin = GetPhysics().GetOrigin();
                masterEnt.SetBeamTarget(origin);
            }
            Present();
        }

        public void SetMaster(idBeam masterbeam) {
            master.oSet(masterbeam);
        }

        public void SetBeamTarget(final idVec3 origin) {
            if ((renderEntity.shaderParms[SHADERPARM_BEAM_END_X] != origin.x) || (renderEntity.shaderParms[SHADERPARM_BEAM_END_Y] != origin.y) || (renderEntity.shaderParms[SHADERPARM_BEAM_END_Z] != origin.z)) {
                renderEntity.shaderParms[SHADERPARM_BEAM_END_X] = origin.x;
                renderEntity.shaderParms[SHADERPARM_BEAM_END_Y] = origin.y;
                renderEntity.shaderParms[SHADERPARM_BEAM_END_Z] = origin.z;
                UpdateVisuals();
            }
        }

        @Override
        public void Show() {
            idBeam targetEnt;

            super.Show();

            targetEnt = target.GetEntity();
            if (targetEnt != null) {
                final idVec3 origin = targetEnt.GetPhysics().GetOrigin();
                SetBeamTarget(origin);
            }
        }

        @Override
        public void WriteToSnapshot(idBitMsgDelta msg) {
            GetPhysics().WriteToSnapshot(msg);
            WriteBindToSnapshot(msg);
            WriteColorToSnapshot(msg);
            msg.WriteFloat(renderEntity.shaderParms[SHADERPARM_BEAM_END_X]);
            msg.WriteFloat(renderEntity.shaderParms[SHADERPARM_BEAM_END_Y]);
            msg.WriteFloat(renderEntity.shaderParms[SHADERPARM_BEAM_END_Z]);
        }

        @Override
        public void ReadFromSnapshot(final idBitMsgDelta msg) {
            GetPhysics().ReadFromSnapshot(msg);
            ReadBindFromSnapshot(msg);
            ReadColorFromSnapshot(msg);
            renderEntity.shaderParms[SHADERPARM_BEAM_END_X] = msg.ReadFloat();
            renderEntity.shaderParms[SHADERPARM_BEAM_END_Y] = msg.ReadFloat();
            renderEntity.shaderParms[SHADERPARM_BEAM_END_Z] = msg.ReadFloat();
            if (msg.HasChanged()) {
                UpdateVisuals();
            }
        }

        private void Event_MatchTarget() {
            int i;
            idEntity targetEnt;
            idBeam targetBeam;

            if (0 == targets.Num()) {
                return;
            }

            targetBeam = null;
            for (i = 0; i < targets.Num(); i++) {
                targetEnt = targets.oGet(i).GetEntity();
                if (targetEnt != null && targetEnt instanceof idBeam) {
                    targetBeam = (idBeam) targetEnt;
                    break;
                }
            }

            if (null == targetBeam) {
                idGameLocal.Error("Could not find valid beam target for '%s'", name);
            }

            target.oSet(targetBeam);
            targetBeam.SetMaster(this);
            if (!spawnArgs.GetBool("start_off")) {
                Show();
            }
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            if (IsHidden()) {
                Show();
            } else {
                Hide();
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


    }

    /*
     ===============================================================================

     idLiquid

     ===============================================================================
     */
    @Deprecated
    public static class idLiquid extends idEntity {
        // CLASS_PROTOTYPE( idLiquid );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Touch, (eventCallback_t2<idLiquid>) idLiquid::Event_Touch);
        }

        private idRenderModelLiquid model;
        //
        //

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            /*
             model = dynamic_cast<idRenderModelLiquid *>( renderEntity.hModel );
             if ( !model ) {
             gameLocal.Error( "Entity '%s' must have liquid model", name.c_str() );
             }
             model->Reset();
             GetPhysics()->SetContents( CONTENTS_TRIGGER );
             */
        }

        @Override
        public void Save(idSaveGame savefile) {
            // Nothing to save
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            //FIXME: NO!
            Spawn();
        }

        private void Event_Touch(idEventArg<idEntity> other, idEventArg<trace_s> trace) {
            // FIXME: for QuakeCon
/*
             idVec3 pos;

             pos = other->GetPhysics()->GetOrigin() - GetPhysics()->GetOrigin();
             model->IntersectBounds( other->GetPhysics()->GetBounds().Translate( pos ), -10.0f );
             */
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idShaking

     ===============================================================================
     */
    public static class idShaking extends idEntity {
        // CLASS_PROTOTYPE( idShaking );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idShaking>) idShaking::Event_Activate);
        }

        private final idPhysics_Parametric physicsObj;
        private boolean active;
        //
        //

        public idShaking() {
            physicsObj = new idPhysics_Parametric();
            active = false;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            physicsObj.SetSelf(this);
            physicsObj.SetClipModel(new idClipModel(GetPhysics().GetClipModel()), 1.0f);
            physicsObj.SetOrigin(GetPhysics().GetOrigin());
            physicsObj.SetAxis(GetPhysics().GetAxis());
            physicsObj.SetClipMask(MASK_SOLID);
            SetPhysics(physicsObj);

            active = false;
            if (!spawnArgs.GetBool("start_off")) {
                BeginShaking();
            }
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteBool(active);
            savefile.WriteStaticObject(physicsObj);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CBool active = new CBool();

            savefile.ReadBool(active);
            savefile.ReadStaticObject(physicsObj);
            RestorePhysics(physicsObj);

            this.active = active.isVal();
        }

        private void BeginShaking() {
            int phase;
            idAngles shake;
            int period;

            active = true;
            phase = gameLocal.random.RandomInt(1000);
            shake = spawnArgs.GetAngles("shake", "0.5 0.5 0.5");
            period = (int) (spawnArgs.GetFloat("period", "0.05") * 1000);
            physicsObj.SetAngularExtrapolation((EXTRAPOLATION_DECELSINE | EXTRAPOLATION_NOSTOP), phase, (int) (period * 0.25f), GetPhysics().GetAxis().ToAngles(), shake, getAng_zero());
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            if (!active) {
                BeginShaking();
            } else {
                active = false;
                physicsObj.SetAngularExtrapolation(EXTRAPOLATION_NONE, 0, 0, physicsObj.GetAxis().ToAngles(), getAng_zero(), getAng_zero());
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idEarthQuake

     ===============================================================================
     */
    public static class idEarthQuake extends idEntity {
        // CLASS_PROTOTYPE( idEarthQuake );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idEarthQuake>) idEarthQuake::Event_Activate);
        }

        private boolean disabled;
        private int nextTriggerTime;
        private boolean playerOriented;
        private float random;
        private int shakeStopTime;
        private float shakeTime;
        private boolean triggered;
        private float wait;
        //
        //

        public idEarthQuake() {
            wait = 0.0f;
            random = 0.0f;
            nextTriggerTime = 0;
            shakeStopTime = 0;
            triggered = false;
            playerOriented = false;
            disabled = false;
            shakeTime = 0.0f;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            nextTriggerTime = 0;
            shakeStopTime = 0;
            wait = spawnArgs.GetFloat("wait", "15");
            random = spawnArgs.GetFloat("random", "5");
            triggered = spawnArgs.GetBool("triggered");
            playerOriented = spawnArgs.GetBool("playerOriented");
            disabled = false;
            shakeTime = spawnArgs.GetFloat("shakeTime", "0");

            if (!triggered) {
                PostEventSec(EV_Activate, spawnArgs.GetFloat("wait"), this);
            }
            BecomeInactive(TH_THINK);
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(nextTriggerTime);
            savefile.WriteInt(shakeStopTime);
            savefile.WriteFloat(wait);
            savefile.WriteFloat(random);
            savefile.WriteBool(triggered);
            savefile.WriteBool(playerOriented);
            savefile.WriteBool(disabled);
            savefile.WriteFloat(shakeTime);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CInt nextTriggerTime = new CInt(), shakeStopTime = new CInt();
            CFloat wait = new CFloat(), random = new CFloat(), shakeTime = new CFloat();
            CBool triggered = new CBool(false), playerOriented = new CBool(false), disabled =new CBool(false);

            savefile.ReadInt(nextTriggerTime);
            savefile.ReadInt(shakeStopTime);
            savefile.ReadFloat(wait);
            savefile.ReadFloat(random);
            savefile.ReadBool(triggered);
            savefile.ReadBool(playerOriented);
            savefile.ReadBool(disabled);
            savefile.ReadFloat(shakeTime);

            this.nextTriggerTime = nextTriggerTime.getVal();
            this.shakeStopTime = shakeStopTime.getVal();
            this.wait = wait.getVal();
            this.random = random.getVal();
            this.triggered = triggered.isVal();
            this.playerOriented = playerOriented.isVal();
            this.disabled = disabled.isVal();
            this.shakeTime = shakeTime.getVal();

            if (shakeStopTime.getVal() > gameLocal.time) {
                BecomeActive(TH_THINK);
            }
        }

        @Override
        public void Think() {
        }

        private void Event_Activate(idEventArg<idEntity> _activator) {
            idEntity activator = _activator.value;

            if (nextTriggerTime > gameLocal.time) {
                return;
            }

            if (disabled && activator == this) {
                return;
            }

            idPlayer player = gameLocal.GetLocalPlayer();
            if (player == null) {
                return;
            }

            nextTriggerTime = 0;

            if (!triggered && activator != this) {
                // if we are not triggered ( i.e. random ), disable or enable
                disabled ^= true;//1;
                if (disabled) {
                    return;
                } else {
                    PostEventSec(EV_Activate, wait + random * gameLocal.random.CRandomFloat(), this);
                }
            }

            ActivateTargets(activator);

            final idSoundShader shader = declManager.FindSound(spawnArgs.GetString("snd_quake"));
            if (playerOriented) {
                player.StartSoundShader(shader, SND_CHANNEL_ANY, SSF_GLOBAL, false, null);
            } else {
                StartSoundShader(shader, SND_CHANNEL_ANY, SSF_GLOBAL, false, null);
            }

            if (shakeTime > 0.0f) {
                shakeStopTime = (int) (gameLocal.time + SEC2MS(shakeTime));
                BecomeActive(TH_THINK);
            }

            if (wait > 0.0f) {
                if (!triggered) {
                    PostEventSec(EV_Activate, wait + random * gameLocal.random.CRandomFloat(), this);
                } else {
                    nextTriggerTime = (int) (gameLocal.time + SEC2MS(wait + random * gameLocal.random.CRandomFloat()));
                }
            } else if (shakeTime == 0.0f) {
                PostEventMS(EV_Remove, 0);
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }



        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idFuncPortal

     ===============================================================================
     */
    public static class idFuncPortal extends idEntity {
        // CLASS_PROTOTYPE( idFuncPortal );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncPortal>) idFuncPortal::Event_Activate);
        }

        private final CInt/*qhandle_t*/ portal = new CInt();
        private final CBool state = new CBool();
        //
        //

        public idFuncPortal() {
            portal.setVal(0);
            state.setVal(false);
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            portal.setVal( gameRenderWorld.FindPortal(GetPhysics().GetAbsBounds().Expand(32.0f)));
            if (portal.getVal() > 0) {
                state.setVal(spawnArgs.GetBool("start_on"));
                gameLocal.SetPortalState(portal.getVal(), (state.isVal() ? PS_BLOCK_ALL : PS_BLOCK_NONE).ordinal());
            }
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteInt(portal.getVal());
            savefile.WriteBool(state.isVal());
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadInt(portal);
            savefile.ReadBool(state);
            gameLocal.SetPortalState(portal.getVal(), (state.isVal() ? PS_BLOCK_ALL : PS_BLOCK_NONE).ordinal());
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            if (portal.getVal() > 0) {
                state.setVal(!state.isVal());
                gameLocal.SetPortalState(portal.getVal(), (state.isVal() ? PS_BLOCK_ALL : PS_BLOCK_NONE).ordinal());
            }
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idFuncAASPortal

     ===============================================================================
     */
    public static class idFuncAASPortal extends idEntity {
        // CLASS_PROTOTYPE( idFuncAASPortal );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncAASPortal>) idFuncAASPortal::Event_Activate);
        }

        private boolean state;
        //
        //

        public idFuncAASPortal() {
            state = false;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            state = spawnArgs.GetBool("start_on");
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_CLUSTERPORTAL, state);
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteBool(state);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            final CBool state = new CBool();
            savefile.ReadBool(state);
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_CLUSTERPORTAL, this.state = state.isVal());
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            state ^= true;//1;
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_CLUSTERPORTAL, state);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    /*
     ===============================================================================

     idFuncAASObstacle

     ===============================================================================
     */
    public static class idFuncAASObstacle extends idEntity {
        // CLASS_PROTOTYPE( idFuncAASObstacle );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncAASObstacle>) idFuncAASObstacle::Event_Activate);
        }

        private final CBool state = new CBool(false);
        //
        //

        public idFuncAASObstacle() {
            state.setVal(false);
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            state.setVal(spawnArgs.GetBool("start_on"));
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_OBSTACLE, state.isVal());
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteBool(state.isVal());
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            savefile.ReadBool(state);
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_OBSTACLE, state.isVal());
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            state.setVal(state.isVal() ^ true);
            gameLocal.SetAASAreaState(GetPhysics().GetAbsBounds(), AREACONTENTS_OBSTACLE, state.isVal());
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

    public static class idFuncRadioChatter extends idEntity {
        // CLASS_PROTOTYPE( idFuncRadioChatter );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idFuncRadioChatter>) idFuncRadioChatter::Event_Activate);
            eventCallbacks.put(EV_ResetRadioHud, (eventCallback_t1<idFuncRadioChatter>) idFuncRadioChatter::Event_ResetRadioHud);
        }

        private float time;
        //
        //

        public idFuncRadioChatter() {
            time = 0;
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            time = spawnArgs.GetFloat("time", "5.0");
        }

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteFloat(time);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            CFloat time = new CFloat();

            savefile.ReadFloat(time);

            this.time = time.getVal();
        }

        private void Event_Activate(idEventArg<idEntity> activator) {
            idPlayer player;
            final String sound;
            idSoundShader shader;
            CInt length = new CInt();

            if (activator.value instanceof idPlayer) {
                player = (idPlayer) activator.value;
            } else {
                player = gameLocal.GetLocalPlayer();
            }

            player.hud.HandleNamedEvent("radioChatterUp");

            sound = spawnArgs.GetString("snd_radiochatter", "");
            if (sound != null && !sound.isEmpty()) {
                shader = declManager.FindSound(sound);
                player.StartSoundShader(shader, SND_CHANNEL_RADIO, SSF_GLOBAL, false, length);
                time = MS2SEC(length.getVal() + 150);
            }
            // we still put the hud up because this is used with no sound on
            // certain frame commands when the chatter is triggered
            PostEventSec(EV_ResetRadioHud, time, player);

        }

        private void Event_ResetRadioHud(idEventArg<idEntity> _activator) {
            idEntity activator = _activator.value;
            idPlayer player = (activator instanceof idPlayer) ? (idPlayer) activator : gameLocal.GetLocalPlayer();
            player.hud.HandleNamedEvent("radioChatterDown");
            ActivateTargets(activator);
        }

        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }


    /*
     ===============================================================================

     idPhantomObjects

     ===============================================================================
     */
    public static class idPhantomObjects extends idEntity {
        // CLASS_PROTOTYPE( idPhantomObjects );
        private static final Map<idEventDef, eventCallback_t> eventCallbacks = new HashMap<>();

        static {
            eventCallbacks.putAll(idEntity.getEventCallBacks());
            eventCallbacks.put(EV_Activate, (eventCallback_t1<idPhantomObjects>) idPhantomObjects::Event_Activate);
        }

        private final idList<idVec3> lastTargetPos;
        private final idEntityPtr<idActor> target;
        private final idList<Integer> targetTime;
        private int end_time;
        private int max_wait;
        private int min_wait;
        private idVec3 shake_ang;
        private float shake_time;
        private float speed;
        private float throw_time;
        //
        //

        public idPhantomObjects() {
            target = null;
            end_time = 0;
            throw_time = 0.0f;
            shake_time = 0.0f;
            shake_ang = new idVec3();
            speed = 0.0f;
            min_wait = 0;
            max_wait = 0;
            fl.neverDormant = false;
            targetTime = new idList<>();
            lastTargetPos = new idList<>();
        }

        public static Map<idEventDef, eventCallback_t> getEventCallBacks() {
            return eventCallbacks;
        }

        @Override
        public void Spawn() {
            super.Spawn();

            throw_time = spawnArgs.GetFloat("time", "5");
            speed = spawnArgs.GetFloat("speed", "1200");
            shake_time = spawnArgs.GetFloat("shake_time", "1");
            throw_time -= shake_time;
            if (throw_time < 0.0f) {
                throw_time = 0.0f;
            }
            min_wait = (int) SEC2MS(spawnArgs.GetFloat("min_wait", "1"));
            max_wait = (int) SEC2MS(spawnArgs.GetFloat("max_wait", "3"));

            shake_ang = spawnArgs.GetVector("shake_ang", "65 65 65");
            Hide();
            GetPhysics().SetContents(0);
        }

        @Override
        public void Save(idSaveGame savefile) {
            int i;

            savefile.WriteInt(end_time);
            savefile.WriteFloat(throw_time);
            savefile.WriteFloat(shake_time);
            savefile.WriteVec3(shake_ang);
            savefile.WriteFloat(speed);
            savefile.WriteInt(min_wait);
            savefile.WriteInt(max_wait);
            target.Save(savefile);
            savefile.WriteInt(targetTime.Num());
            for (i = 0; i < targetTime.Num(); i++) {
                savefile.WriteInt(targetTime.oGet(i));
            }

            for (i = 0; i < lastTargetPos.Num(); i++) {
                savefile.WriteVec3(lastTargetPos.oGet(i));
            }
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            int num;
            int i;

            end_time = savefile.ReadInt();
            throw_time = savefile.ReadFloat();
            shake_time = savefile.ReadFloat();
            savefile.ReadVec3(shake_ang);
            speed = savefile.ReadFloat();
            min_wait = savefile.ReadInt();
            max_wait = savefile.ReadInt();
            target.Restore(savefile);

            num = savefile.ReadInt();
            targetTime.SetGranularity(1);
            targetTime.SetNum(num);
            lastTargetPos.SetGranularity(1);
            lastTargetPos.SetNum(num);

            for (i = 0; i < num; i++) {
                targetTime.oSet(i, savefile.ReadInt());
            }

            if (savefile.GetBuildNumber() == INITIAL_RELEASE_BUILD_NUMBER) {
                // these weren't saved out in the first release
                for (i = 0; i < num; i++) {
                    lastTargetPos.oGet(i).Zero();
                }
            } else {
                for (i = 0; i < num; i++) {
                    savefile.ReadVec3(lastTargetPos.oGet(i));
                }
            }
        }

        @Override
        public void Think() {
            int i;
            int num;
            float time;
            idVec3 vel = new idVec3();
            idVec3 ang = new idVec3();
            idEntity ent;
            idActor targetEnt;
            idPhysics entPhys;
            trace_s tr = new trace_s();

            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant()) {
                return;
            }

            if (0 == (thinkFlags & TH_THINK)) {
                BecomeInactive(thinkFlags & ~TH_THINK);
                return;
            }

            targetEnt = target.GetEntity();
            if (null == targetEnt || (targetEnt.health <= 0) || (end_time != 0 && (gameLocal.time > end_time)) || gameLocal.inCinematic) {
                BecomeInactive(TH_THINK);
            }

            final idVec3 toPos = targetEnt.GetEyePosition();

            num = 0;
            for (i = 0; i < targets.Num(); i++) {
                ent = targets.oGet(i).GetEntity();
                if (null == ent) {
                    continue;
                }

                if (ent.fl.hidden) {
                    // don't throw hidden objects
                    continue;
                }

                if (0 == targetTime.oGet(i)) {
                    // already threw this object
                    continue;
                }

                num++;

                time = MS2SEC(targetTime.oGet(i) - gameLocal.time);
                if (time > shake_time) {
                    continue;
                }

                entPhys = ent.GetPhysics();
                final idVec3 entOrg = entPhys.GetOrigin();

                gameLocal.clip.TracePoint(tr, entOrg, toPos, MASK_OPAQUE, ent);
                if (tr.fraction >= 1.0f || gameLocal.GetTraceEntity(tr).equals(targetEnt)) {
                    lastTargetPos.oSet(i, toPos);
                }

                if (time < 0.0f) {
                    idAI.PredictTrajectory(entPhys.GetOrigin(), lastTargetPos.oGet(i), speed, entPhys.GetGravity(),
                            entPhys.GetClipModel(), entPhys.GetClipMask(), 256.0f, ent, targetEnt, ai_debugTrajectory.GetBool() ? 1 : 0, vel);
                    vel.oMulSet(speed);
                    entPhys.SetLinearVelocity(vel);
                    if (0 == end_time) {
                        targetTime.oSet(i, 0);
                    } else {
                        targetTime.oSet(i, gameLocal.time + gameLocal.random.RandomInt(max_wait - min_wait) + min_wait);
                    }
                    if (ent instanceof idMoveable) {
                        idMoveable ment = (idMoveable) ent;
                        ment.EnableDamage(true, 2.5f);
                    }
                } else {
                    // this is not the right way to set the angular velocity, but the effect is nice, so I'm keeping it. :)
                    ang.Set(gameLocal.random.CRandomFloat() * shake_ang.x, gameLocal.random.CRandomFloat() * shake_ang.y, gameLocal.random.CRandomFloat() * shake_ang.z);
                    ang.oMulSet(1.0f - time / shake_time);
                    entPhys.SetAngularVelocity(ang);
                }
            }

            if (0 == num) {
                BecomeInactive(TH_THINK);
            }
        }

        private void Event_Activate(idEventArg<idEntity> _activator) {
            idEntity activator = _activator.value;
            int i;
            float time;
            float frac;
            float scale;

            if ((thinkFlags & TH_THINK) != 0) {
                BecomeInactive(TH_THINK);
                return;
            }

            RemoveNullTargets();
            if (0 == targets.Num()) {
                return;
            }

            if (null == activator || !(activator instanceof idActor)) {
                target.oSet(gameLocal.GetLocalPlayer());
            } else {
                target.oSet((idActor) activator);
            }

            end_time = (int) (gameLocal.time + SEC2MS(spawnArgs.GetFloat("end_time", "0")));

            targetTime.SetNum(targets.Num());
            lastTargetPos.SetNum(targets.Num());

            final idVec3 toPos = target.GetEntity().GetEyePosition();

            // calculate the relative times of all the objects
            time = 0.0f;
            for (i = 0; i < targetTime.Num(); i++) {
                targetTime.oSet(i, SEC2MS(time));
                lastTargetPos.oSet(i, toPos);

                frac = 1.0f - (float) i / (float) targetTime.Num();
                time += (gameLocal.random.RandomFloat() + 1.0f) * 0.5f * frac + 0.1f;
            }

            // scale up the times to fit within throw_time
            scale = throw_time / time;
            for (i = 0; i < targetTime.Num(); i++) {
                targetTime.oSet(i, gameLocal.time + SEC2MS(shake_time) + targetTime.oGet(i) * scale);
            }

            BecomeActive(TH_THINK);
        }

        //        private void Event_Throw();
//
//        private void Event_ShakeObject(idEntity object, int starttime);
//
        @Override
        public Class.idClass CreateInstance() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        @Override
        public eventCallback_t getEventCallBack(idEventDef event) {
            return eventCallbacks.get(event);
        }

    }

}
