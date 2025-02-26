package neo.Game

import neo.CM.CollisionModel.trace_s
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.eventCallback_t3
import neo.Game.GameSys.Class.eventCallback_t5
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.*
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Player.idPlayer
import neo.Game.Script.Script_Thread
import neo.Game.Script.Script_Thread.idThread
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.portalConnection_t
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile
import neo.framework.UsercmdGen
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.Angles
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Curve.idCurve_Spline
import neo.idlib.math.Extrapolate
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector
import neo.idlib.math.Vector.getVec3_origin
import neo.idlib.math.Vector.idVec3
import kotlin.math.abs

/**
 *
 */
object Mover {
    val EV_AccelSound: idEventDef = idEventDef("accelSound", "s")
    val EV_AccelTime: idEventDef = idEventDef("accelTime", "f")
    val EV_Bob: idEventDef = idEventDef("bob", "ffv")
    val EV_DecelSound: idEventDef = idEventDef("decelSound", "s")
    val EV_DecelTime: idEventDef = idEventDef("decelTime", "f")
    val EV_DisableSplineAngles: idEventDef = idEventDef("disableSplineAngles", null)
    val EV_Door_Close: idEventDef = idEventDef("close", null)
    val EV_Door_IsLocked: idEventDef = idEventDef("isLocked", null, 'f')
    val EV_Door_IsOpen: idEventDef = idEventDef("isOpen", null, 'f')
    val EV_Door_Lock: idEventDef = idEventDef("lock", "d")
    val EV_Door_Open: idEventDef = idEventDef("open", null)
    val EV_Door_SpawnDoorTrigger: idEventDef = idEventDef("<spawnDoorTrigger>", null)
    val EV_Door_SpawnSoundTrigger: idEventDef = idEventDef("<spawnSoundTrigger>", null)

    //
    val EV_Door_StartOpen: idEventDef = idEventDef("<startOpen>", null)
    val EV_EnableSplineAngles: idEventDef = idEventDef("enableSplineAngles", null)
    val EV_FindGuiTargets: idEventDef = idEventDef("<FindGuiTargets>", null)
    val EV_GotoFloor: idEventDef = idEventDef("gotoFloor", "d")
    val EV_IsMoving: idEventDef = idEventDef("isMoving", null, 'd')
    val EV_IsRotating: idEventDef = idEventDef("isRotating", null, 'd')
    val EV_Move: idEventDef = idEventDef("move", "ff")
    val EV_MoveAccelerateTo: idEventDef = idEventDef("accelTo", "ff")
    val EV_MoveDecelerateTo: idEventDef = idEventDef("decelTo", "ff")
    val EV_MoveSound: idEventDef = idEventDef("moveSound", "s")
    val EV_MoveTo: idEventDef = idEventDef("moveTo", "e")
    val EV_MoveToPos: idEventDef = idEventDef("moveToPos", "v")
    val EV_Mover_ClosePortal: idEventDef = idEventDef("closePortal")
    val EV_Mover_Disable: idEventDef = idEventDef("disable", null)
    val EV_Mover_Enable: idEventDef = idEventDef("enable", null)
    val EV_Mover_InitGuiTargets: idEventDef = idEventDef("<initguitargets>", null)
    val EV_Mover_MatchTeam: idEventDef = idEventDef("<matchteam>", "dd")
    val EV_Mover_OpenPortal: idEventDef = idEventDef("openPortal")

    //
    val EV_Mover_ReturnToPos1: idEventDef = idEventDef("<returntopos1>", null)
    val EV_PartBlocked: idEventDef = idEventDef("<partblocked>", "e")

    //
    val EV_PostArrival: idEventDef = idEventDef("postArrival", null)
    val EV_PostRestore: idEventDef = idEventDef("<postrestore>", "ddddd")
    val EV_ReachedAng: idEventDef = idEventDef("<reachedang>", null)
    val EV_ReachedPos: idEventDef = idEventDef("<reachedpos>", null)
    val EV_RemoveInitialSplineAngles: idEventDef = idEventDef("removeInitialSplineAngles", null)
    val EV_Rotate: idEventDef = idEventDef("rotate", "v")
    val EV_RotateDownTo: idEventDef = idEventDef("rotateDownTo", "df")
    val EV_RotateOnce: idEventDef = idEventDef("rotateOnce", "v")
    val EV_RotateTo: idEventDef = idEventDef("rotateTo", "v")
    val EV_RotateUpTo: idEventDef = idEventDef("rotateUpTo", "df")
    val EV_Speed: idEventDef = idEventDef("speed", "f")
    val EV_StartSpline: idEventDef = idEventDef("startSpline", "e")
    val EV_StopMoving: idEventDef = idEventDef("stopMoving", null)
    val EV_StopRotating: idEventDef = idEventDef("stopRotating", null)
    val EV_StopSpline: idEventDef = idEventDef("stopSpline", null)
    val EV_Sway: idEventDef = idEventDef("sway", "ffv")
    val EV_TeamBlocked: idEventDef = idEventDef("<teamblocked>", "ee")
    val EV_Time: idEventDef = idEventDef("time", "f")

    /*
     ===============================================================================

     General movers.

     ===============================================================================
     */
    // a mover will update any gui entities in it's target list with
    // a key/val pair of "mover" "state" from below.. guis can represent
    // realtime info like this
    // binary only
    val guiBinaryMoverStates: Array<String> = arrayOf(
        "1",  // pos 1
        "2",  // pos 2
        "3",  // moving 1 to 2
        "4" // moving 2 to 1
    )

    /*
     ===============================================================================

     Binary movers.

     ===============================================================================
     */
    enum class moverState_t {
        MOVER_POS1, MOVER_POS2, MOVER_1TO2, MOVER_2TO1
    }

    /*
     ===============================================================================

     idMover

     ===============================================================================
     */
    open class idMover : idEntity() {
        companion object {
            protected const val DIR_BACK = -6
            protected const val DIR_DOWN = -2
            protected const val DIR_FORWARD = -5
            protected const val DIR_LEFT = -3
            protected const val DIR_REL_BACK = -12
            protected const val DIR_REL_DOWN = -8
            protected const val DIR_REL_FORWARD = -11
            protected const val DIR_REL_LEFT = -9
            protected const val DIR_REL_RIGHT = -10
            protected const val DIR_REL_UP = -7
            protected const val DIR_RIGHT = -4

            //
            // mover directions.  make sure to change script/doom_defs.script if you add any, or change their order
            //
            // typedef enum {
            protected const val DIR_UP = -1

            // CLASS_PROTOTYPE( idMover );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_FindGuiTargets] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_FindGuiTargets }
                eventCallbacks[Script_Thread.EV_Thread_SetCallback] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_SetCallback }
                eventCallbacks[EV_TeamBlocked] =
                    eventCallback_t2<idMover> { obj: Any?, blockedPart: idEventArg<*>?, blockingEntity: idEventArg<*>? ->
                        idMover::Event_TeamBlocked
                    }
                eventCallbacks[EV_PartBlocked] =
                    eventCallback_t1<idMover> { obj: Any?, blockingEntity: idEventArg<*>? ->
                        idMover::Event_PartBlocked
                    }
                eventCallbacks[EV_ReachedPos] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_UpdateMove }
                eventCallbacks[EV_ReachedAng] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_UpdateRotation }
                eventCallbacks[EV_PostRestore] =
                    eventCallback_t5<idMover> { obj: Any?, start: idEventArg<*>?, total: idEventArg<*>?, accel: idEventArg<*>?,
                                                decel: idEventArg<*>?, useSplineAng: idEventArg<*>? ->
                        idMover::Event_PostRestore
                    }
                eventCallbacks[EV_StopMoving] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_StopMoving }
                eventCallbacks[EV_StopRotating] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_StopRotating }
                eventCallbacks[EV_Speed] =
                    eventCallback_t1<idMover> { obj: Any?, speed: idEventArg<*>? -> idMover::Event_SetMoveSpeed }
                eventCallbacks[EV_Time] =
                    eventCallback_t1<idMover> { obj: Any?, time: idEventArg<*>? -> idMover::Event_SetMoveTime }
                eventCallbacks[EV_AccelTime] = eventCallback_t1<idMover> { obj: Any?, time: idEventArg<*>? ->
                    idMover::Event_SetAccellerationTime
                }
                eventCallbacks[EV_DecelTime] = eventCallback_t1<idMover> { obj: Any?, time: idEventArg<*>? ->
                    idMover::Event_SetDecelerationTime
                }
                eventCallbacks[EV_MoveTo] =
                    eventCallback_t1<idMover> { obj: Any?, ent: idEventArg<*>? -> idMover::Event_MoveTo }
                eventCallbacks[EV_MoveToPos] =
                    eventCallback_t1<idMover> { obj: Any?, pos: idEventArg<*>? -> idMover::Event_MoveToPos }
                eventCallbacks[EV_Move] =
                    eventCallback_t2<idMover> { obj: Any?, angle: idEventArg<*>?, distance: idEventArg<*>? -> idMover::Event_MoveDir }
                eventCallbacks[EV_MoveAccelerateTo] =
                    eventCallback_t2<idMover> { obj: Any?, speed: idEventArg<*>?, time: idEventArg<*>? ->
                        idMover::Event_MoveAccelerateTo
                    }
                eventCallbacks[EV_MoveDecelerateTo] =
                    eventCallback_t2<idMover> { obj: Any?, speed: idEventArg<*>?, time: idEventArg<*>? ->
                        idMover::Event_MoveDecelerateTo
                    }
                eventCallbacks[EV_RotateDownTo] =
                    eventCallback_t2<idMover> { obj: Any?, _axis: idEventArg<*>?, angle: idEventArg<*>? -> idMover::Event_RotateDownTo }
                eventCallbacks[EV_RotateUpTo] =
                    eventCallback_t2<idMover> { obj: Any?, _axis: idEventArg<*>?, angle: idEventArg<*>? -> idMover::Event_RotateUpTo }
                eventCallbacks[EV_RotateTo] =
                    eventCallback_t1<idMover> { obj: Any?, angles: idEventArg<*>? -> idMover::Event_RotateTo }
                eventCallbacks[EV_Rotate] =
                    eventCallback_t1<idMover> { obj: Any?, angles: idEventArg<*>? -> idMover::Event_Rotate }
                eventCallbacks[EV_RotateOnce] =
                    eventCallback_t1<idMover> { obj: Any?, angles: idEventArg<*>? -> idMover::Event_RotateOnce }
                eventCallbacks[EV_Bob] =
                    eventCallback_t3<idMover> { obj: Any?, speed: idEventArg<*>?, phase: idEventArg<*>?, depth: idEventArg<*>? -> idMover::Event_Bob }
                eventCallbacks[EV_Sway] =
                    eventCallback_t3<idMover> { obj: Any?, speed: idEventArg<*>?, phase: idEventArg<*>?, _depth: idEventArg<*>? -> idMover::Event_Sway }
                eventCallbacks[EV_Mover_OpenPortal] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_OpenPortal }
                eventCallbacks[EV_Mover_ClosePortal] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_ClosePortal }
                eventCallbacks[EV_AccelSound] =
                    eventCallback_t1<idMover> { obj: Any?, sound: idEventArg<*>? -> idMover::Event_SetAccelSound }
                eventCallbacks[EV_DecelSound] =
                    eventCallback_t1<idMover> { obj: Any?, sound: idEventArg<*>? -> idMover::Event_SetDecelSound }
                eventCallbacks[EV_MoveSound] =
                    eventCallback_t1<idMover> { obj: Any?, sound: idEventArg<*>? -> idMover::Event_SetMoveSound }
                eventCallbacks[EV_Mover_InitGuiTargets] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_InitGuiTargets }
                eventCallbacks[EV_EnableSplineAngles] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_EnableSplineAngles }
                eventCallbacks[EV_DisableSplineAngles] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_DisableSplineAngles }
                eventCallbacks[EV_RemoveInitialSplineAngles] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_RemoveInitialSplineAngles }
                eventCallbacks[EV_StartSpline] =
                    eventCallback_t1<idMover> { obj: Any?, _splineEntity: idEventArg<*>? ->
                        idMover::Event_StartSpline
                    }
                eventCallbacks[EV_StopSpline] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_StopSpline }
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idMover> { obj: Any?, activator: idEventArg<*>? -> idMover::Event_Activate }
                eventCallbacks[EV_IsMoving] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_IsMoving }
                eventCallbacks[EV_IsRotating] =
                    eventCallback_t0<idMover> { obj: Any? -> idMover::Event_IsRotating }
            }
        }

        //
        private val guiTargets: ArrayList<idEntityPtr<idEntity>> =
            ArrayList<idEntityPtr<idEntity>>()
        protected var move: moveState_t

        //
        protected var physicsObj: idPhysics_Parametric
        private var acceltime: Int
        private var angle_delta: idAngles

        //
        private var   /*qhandle_t*/areaPortal // 0 = no portal
                : Int
        private var damage: Float
        private var deceltime: Int
        private var dest_angles: idAngles
        private val dest_position: idVec3
        private var lastCommand: moverCommand_t = moverCommand_t.MOVER_NONE
        private val move_delta: idVec3
        private var move_speed: Float

        //
        private var move_thread: Int
        private var move_time: Int

        //
        private val rot: rotationState_t
        private var rotate_thread: Int
        private val splineEnt: idEntityPtr<idEntity>
        private var stopRotation: Boolean
        private var useSplineAngles: Boolean
        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        // } moverDir_t;
        override fun Spawn() {
            super.Spawn()
            val damage = CFloat()
            move_thread = 0
            rotate_thread = 0
            stopRotation = false
            lastCommand = moverCommand_t.MOVER_NONE
            acceltime = (1000 * spawnArgs.GetFloat("accel_time", "0")).toInt()
            deceltime = (1000 * spawnArgs.GetFloat("decel_time", "0")).toInt()
            move_time = (1000 * spawnArgs.GetFloat("move_time", "1")).toInt() // safe default value
            move_speed = spawnArgs.GetFloat("move_speed", "0")
            spawnArgs.GetFloat("damage", "0", damage)
            this.damage = damage._val
            dest_position.set(GetPhysics().GetOrigin())
            dest_angles = GetPhysics().GetAxis().ToAngles()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("solid", "1")) {
                physicsObj.SetContents(0)
            }
            if (null == renderEntity.hModel || !spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                dest_position,
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                dest_angles,
                Angles.getAng_zero(),
                Angles.getAng_zero()
            )
            SetPhysics(physicsObj)

            // see if we are on an areaportal
            areaPortal = Game_local.gameRenderWorld.FindPortal(GetPhysics().GetAbsBounds())
            if (spawnArgs.MatchPrefix("guiTarget") != null) {
                if (Game_local.gameLocal.GameState() == gameState_t.GAMESTATE_STARTUP) {
                    PostEventMS(EV_FindGuiTargets, 0)
                } else {
                    // not during spawn, so it's ok to get the targets
                    FindGuiTargets()
                }
            }
            health = spawnArgs.GetInt("health")
            if (health != 0) {
                fl.takedamage = true
            }
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteInt(TempDump.etoi(move.stage))
            savefile.WriteInt(move.acceleration)
            savefile.WriteInt(move.movetime)
            savefile.WriteInt(move.deceleration)
            savefile.WriteVec3(move.dir)
            savefile.WriteInt(TempDump.etoi(rot.stage))
            savefile.WriteInt(rot.acceleration)
            savefile.WriteInt(rot.movetime)
            savefile.WriteInt(rot.deceleration)
            savefile.WriteFloat(rot.rot.pitch)
            savefile.WriteFloat(rot.rot.yaw)
            savefile.WriteFloat(rot.rot.roll)
            savefile.WriteInt(move_thread)
            savefile.WriteInt(rotate_thread)
            savefile.WriteAngles(dest_angles)
            savefile.WriteAngles(angle_delta)
            savefile.WriteVec3(dest_position)
            savefile.WriteVec3(move_delta)
            savefile.WriteFloat(move_speed)
            savefile.WriteInt(move_time)
            savefile.WriteInt(deceltime)
            savefile.WriteInt(acceltime)
            savefile.WriteBool(stopRotation)
            savefile.WriteBool(useSplineAngles)
            savefile.WriteInt(TempDump.etoi(lastCommand))
            savefile.WriteFloat(damage)
            savefile.WriteInt(areaPortal)
            if (areaPortal > 0) {
                savefile.WriteInt(Game_local.gameRenderWorld.GetPortalState(areaPortal))
            }
            savefile.WriteInt(guiTargets.size)
            i = 0
            while (i < guiTargets.size) {
                guiTargets[i].Save(savefile)
                i++
            }
            if (splineEnt.GetEntity() != null && splineEnt.GetEntity()!!.GetSpline() != null) {
                val spline = physicsObj.GetSpline()!!
                savefile.WriteBool(true)
                splineEnt.Save(savefile)
                savefile.WriteInt(spline.GetTime(0).toInt())
                savefile.WriteInt((spline.GetTime(spline.GetNumValues() - 1) - spline.GetTime(0)).toInt())
                savefile.WriteInt(physicsObj.GetSplineAcceleration())
                savefile.WriteInt(physicsObj.GetSplineDeceleration())
                savefile.WriteInt(TempDump.btoi(physicsObj.UsingSplineAngles()))
            } else {
                savefile.WriteBool(false)
            }
        }

        //
        //
        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num = CInt()
            val hasSpline = CBool(false)
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            move.stage = Mover.idMover.moveStage_t.values()[savefile.ReadInt()]
            move.acceleration = savefile.ReadInt()
            move.movetime = savefile.ReadInt()
            move.deceleration = savefile.ReadInt()
            savefile.ReadVec3(move.dir)
            rot.stage = Mover.idMover.moveStage_t.values()[savefile.ReadInt()]
            rot.acceleration = savefile.ReadInt()
            rot.movetime = savefile.ReadInt()
            rot.deceleration = savefile.ReadInt()
            rot.rot.pitch = savefile.ReadFloat()
            rot.rot.yaw = savefile.ReadFloat()
            rot.rot.roll = savefile.ReadFloat()
            move_thread = savefile.ReadInt()
            rotate_thread = savefile.ReadInt()
            savefile.ReadAngles(dest_angles)
            savefile.ReadAngles(angle_delta)
            savefile.ReadVec3(dest_position)
            savefile.ReadVec3(move_delta)
            move_speed = savefile.ReadFloat()
            move_time = savefile.ReadInt()
            deceltime = savefile.ReadInt()
            acceltime = savefile.ReadInt()
            stopRotation = savefile.ReadBool()
            useSplineAngles = savefile.ReadBool()
            lastCommand = Mover.idMover.moverCommand_t.values()[savefile.ReadInt()]
            damage = savefile.ReadFloat()
            areaPortal = savefile.ReadInt()
            if (areaPortal > 0) {
                val portalState = CInt()
                savefile.ReadInt(portalState)
                Game_local.gameLocal.SetPortalState(areaPortal, portalState._val)
            }
            guiTargets.clear()
            savefile.ReadInt(num)
            guiTargets.ensureCapacity(num._val)
            i = 0
            while (i < num._val) {
                guiTargets[i].Restore(savefile)
                i++
            }
            savefile.ReadBool(hasSpline)
            if (hasSpline._val) {
                val starttime = CInt()
                val totaltime = CInt()
                val accel = CInt()
                val decel = CInt()
                val useAngles = CInt()
                splineEnt.Restore(savefile)
                savefile.ReadInt(starttime)
                savefile.ReadInt(totaltime)
                savefile.ReadInt(accel)
                savefile.ReadInt(decel)
                savefile.ReadInt(useAngles)
                PostEventMS(EV_PostRestore, 0, starttime, totaltime, accel, decel, useAngles)
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            fl.takedamage = false
            ActivateTargets(this)
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            physicsObj.WriteToSnapshot(msg)
            msg.WriteBits(TempDump.etoi(move.stage), 3)
            msg.WriteBits(TempDump.etoi(rot.stage), 3)
            WriteBindToSnapshot(msg)
            WriteGUIToSnapshot(msg)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val oldMoveStage = move.stage
            val oldRotStage = rot.stage
            physicsObj.ReadFromSnapshot(msg)
            move.stage = Mover.idMover.moveStage_t.values()[msg.ReadBits(3)]
            rot.stage = Mover.idMover.moveStage_t.values()[msg.ReadBits(3)]
            ReadBindFromSnapshot(msg)
            ReadGUIFromSnapshot(msg)
            if (msg.HasChanged()) {
                if (move.stage != oldMoveStage) {
                    UpdateMoveSound(oldMoveStage)
                }
                if (rot.stage != oldRotStage) {
                    UpdateRotationSound(oldRotStage)
                }
                UpdateVisuals()
            }
        }

        override fun Hide() {
            super.Hide()
            physicsObj.SetContents(0)
        }

        override fun Show() {
            super.Show()
            if (spawnArgs.GetBool("solid", "1")) {
                physicsObj.SetContents(Material.CONTENTS_SOLID)
            }
            SetPhysics(physicsObj)
        }

        fun SetPortalState(open: Boolean) {
            assert(areaPortal != 0)
            Game_local.gameLocal.SetPortalState(
                areaPortal,
                (if (open) portalConnection_t.PS_BLOCK_NONE else portalConnection_t.PS_BLOCK_ALL).ordinal
            )
        }

        /*
         ================
         idMover::Event_OpenPortal

         Sets the portal associtated with this mover to be open
         ================
         */
        protected fun Event_OpenPortal() {
            if (areaPortal != 0) {
                SetPortalState(true)
            }
        }

        /*
         ================
         idMover::Event_ClosePortal

         Sets the portal associtated with this mover to be closed
         ================
         */
        protected fun Event_ClosePortal() {
            if (areaPortal != 0) {
                SetPortalState(false)
            }
        }

        protected fun Event_PartBlocked(blockingEntity: idEventArg<idEntity>) {
            if (damage > 0.0f) {
                blockingEntity.value.Damage(
                    this,
                    this,
                    Vector.getVec3_origin(),
                    "damage_moverCrush",
                    damage,
                    Model.INVALID_JOINT
                )
            }
            if (SysCvar.g_debugMover.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: '%s' blocked by '%s'\n",
                    Game_local.gameLocal.time,
                    name,
                    blockingEntity.value.name
                )
            }
        }

        protected fun MoveToPos(pos: idVec3) {
            dest_position.set(GetLocalCoordinates(pos))
            BeginMove(null)
        }

        protected fun UpdateMoveSound(stage: moveStage_t?) {
            when (stage) {
                moveStage_t.ACCELERATION_STAGE -> {
                    StartSound("snd_accel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                    StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
                }
                moveStage_t.LINEAR_STAGE -> {
                    StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
                }
                moveStage_t.DECELERATION_STAGE -> {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                    StartSound("snd_decel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                }
                moveStage_t.FINISHED_STAGE -> {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                }
            }
        }

        protected fun UpdateRotationSound(stage: moveStage_t?) {
            when (stage) {
                moveStage_t.ACCELERATION_STAGE -> {
                    StartSound("snd_accel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                    StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
                }
                moveStage_t.LINEAR_STAGE -> {
                    StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
                }
                moveStage_t.DECELERATION_STAGE -> {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                    StartSound("snd_decel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
                }
                moveStage_t.FINISHED_STAGE -> {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                }
            }
        }

        protected fun SetGuiStates(state: String) {
            var i: Int
            if (guiTargets.size != 0) {
                SetGuiState("movestate", state)
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                if (renderEntity.gui[i] != null) {
                    renderEntity.gui[i].SetStateString("movestate", state)
                    renderEntity.gui[i].StateChanged(Game_local.gameLocal.time, true)
                }
                i++
            }
        }

        protected fun FindGuiTargets() {
            Game_local.gameLocal.GetTargets(spawnArgs, guiTargets, "guiTarget")
        }

        /*
         ==============================
         idMover::SetGuiState

         key/val will be set to any renderEntity->gui's on the list
         ==============================
         */
        protected fun SetGuiState(key: String, `val`: String) {
            Game_local.gameLocal.Printf("Setting %s to %s\n", key, `val`)
            for (i in 0 until guiTargets.size) {
                val ent = guiTargets[i].GetEntity()
                if (ent != null) {
                    for (j in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                        if (ent.GetRenderEntity() != null && ent.GetRenderEntity().gui[j] != null) {
                            ent.GetRenderEntity().gui[j].SetStateString(key, `val`)
                            ent.GetRenderEntity().gui[j].StateChanged(Game_local.gameLocal.time, true)
                        }
                    }
                    ent.UpdateVisuals()
                }
            }
        }

        protected open fun DoneMoving() {
            if (lastCommand != moverCommand_t.MOVER_SPLINE) {
                // set our final position so that we get rid of any numerical inaccuracy
                physicsObj.SetLinearExtrapolation(
                    Extrapolate.EXTRAPOLATION_NONE,
                    0,
                    0,
                    dest_position,
                    Vector.getVec3_origin(),
                    Vector.getVec3_origin()
                )
            }
            lastCommand = moverCommand_t.MOVER_NONE
            idThread.ObjectMoveDone(move_thread, this)
            move_thread = 0
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
        }

        protected fun DoneRotating() {
            lastCommand = moverCommand_t.MOVER_NONE
            idThread.ObjectMoveDone(rotate_thread, this)
            rotate_thread = 0
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
        }

        protected open fun BeginMove(thread: idThread?) {
            val stage: moveStage_t
            val org = idVec3()
            val dist: Float
            val acceldist: Float
            val totalacceltime: Int
            var at: Int
            var dt: Int
            lastCommand = moverCommand_t.MOVER_MOVING
            move_thread = 0
            physicsObj.GetLocalOrigin(org)
            move_delta.set(dest_position.minus(org))
            if (move_delta.Compare(Vector.getVec3_zero())) {
                DoneMoving()
                return
            }

            // scale times up to whole physics frames
            at = idPhysics.SnapTimeToPhysicsFrame(acceltime)
            move_time += at - acceltime
            acceltime = at
            dt = idPhysics.SnapTimeToPhysicsFrame(deceltime)
            move_time += dt - deceltime
            deceltime = dt

            // if we're moving at a specific speed, we need to calculate the move time
            if (move_speed != 0f) {
                dist = move_delta.Length()
                totalacceltime = acceltime + deceltime

                // calculate the distance we'll move during acceleration and deceleration
                acceldist = totalacceltime * 0.5f * 0.001f * move_speed
                move_time = if (acceldist >= dist) {
                    // going too slow for this distance to move at a constant speed
                    totalacceltime
                } else {
                    // calculate move time taking acceleration into account
                    (totalacceltime + 1000.0f * (dist - acceldist) / move_speed).toInt()
                }
            }

            // scale time up to a whole physics frames
            move_time = idPhysics.SnapTimeToPhysicsFrame(move_time)
            stage = if (acceltime != 0) {
                moveStage_t.ACCELERATION_STAGE
            } else if (move_time <= deceltime) {
                moveStage_t.DECELERATION_STAGE
            } else {
                moveStage_t.LINEAR_STAGE
            }
            at = acceltime
            dt = deceltime
            if (at + dt > move_time) {
                // there's no real correct way to handle this, so we just scale
                // the times to fit into the move time in the same proportions
                at = idPhysics.SnapTimeToPhysicsFrame(at * move_time / (at + dt))
                dt = move_time - at
            }
            move_delta.set(move_delta.times(1000.0f / (move_time.toFloat() - (at + dt) * 0.5f)))
            move.stage = stage
            move.acceleration = at
            move.movetime = move_time - at - dt
            move.deceleration = dt
            move.dir.set(move_delta)
            ProcessEvent(EV_ReachedPos)
        }

        protected fun BeginRotation(thread: idThread?, stopwhendone: Boolean) {
            val stage: moveStage_t
            val ang = idAngles()
            var at: Int
            var dt: Int
            lastCommand = moverCommand_t.MOVER_ROTATING
            rotate_thread = 0

            // rotation always uses move_time so that if a move was started before the rotation,
            // the rotation will take the same amount of time as the move.  If no move has been
            // started and no time is set, the rotation takes 1 second.
            if (0 == move_time) {
                move_time = 1
            }
            physicsObj.GetLocalAngles(ang)
            angle_delta = dest_angles.minus(ang)
            if (angle_delta == Angles.getAng_zero()) {
                // set our final angles so that we get rid of any numerical inaccuracy
                dest_angles.Normalize360()
                physicsObj.SetAngularExtrapolation(
                    Extrapolate.EXTRAPOLATION_NONE,
                    0,
                    0,
                    dest_angles,
                    Angles.getAng_zero(),
                    Angles.getAng_zero()
                )
                stopRotation = false
                DoneRotating()
                return
            }

            // scale times up to whole physics frames
            at = idPhysics.SnapTimeToPhysicsFrame(acceltime)
            move_time += at - acceltime
            acceltime = at
            dt = idPhysics.SnapTimeToPhysicsFrame(deceltime)
            move_time += dt - deceltime
            deceltime = dt
            move_time = idPhysics.SnapTimeToPhysicsFrame(move_time)
            stage = if (acceltime != 0) {
                moveStage_t.ACCELERATION_STAGE
            } else if (move_time <= deceltime) {
                moveStage_t.DECELERATION_STAGE
            } else {
                moveStage_t.LINEAR_STAGE
            }
            at = acceltime
            dt = deceltime
            if (at + dt > move_time) {
                // there's no real correct way to handle this, so we just scale
                // the times to fit into the move time in the same proportions
                at = idPhysics.SnapTimeToPhysicsFrame(at * move_time / (at + dt))
                dt = move_time - at
            }
            angle_delta = angle_delta.times(1000.0f / (move_time.toFloat() - (at + dt) * 0.5f))
            stopRotation = stopwhendone || dt != 0
            rot.stage = stage
            rot.acceleration = at
            rot.movetime = move_time - at - dt
            rot.deceleration = dt
            rot.rot = angle_delta
            ProcessEvent(EV_ReachedAng)
        }

        private fun VectorForDir(dir: Float, vec: idVec3) {
            val ang = idAngles()
            when (dir.toInt()) {
                DIR_UP -> vec.set(0f, 0f, 1f)
                DIR_DOWN -> vec.set(0f, 0f, -1f)
                DIR_LEFT -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.pitch = 0f
                    ang.roll = 0f
                    ang.yaw += 90f
                    vec.set(ang.ToForward())
                }
                DIR_RIGHT -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.pitch = 0f
                    ang.roll = 0f
                    ang.yaw -= 90f
                    vec.set(ang.ToForward())
                }
                DIR_FORWARD -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.pitch = 0f
                    ang.roll = 0f
                    vec.set(ang.ToForward())
                }
                DIR_BACK -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.pitch = 0f
                    ang.roll = 0f
                    ang.yaw += 180f
                    vec.set(ang.ToForward())
                }
                DIR_REL_UP -> vec.set(0f, 0f, 1f)
                DIR_REL_DOWN -> vec.set(0f, 0f, -1f)
                DIR_REL_LEFT -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.ToVectors(getVec3_origin(), vec)
                    vec.timesAssign(-1f)
                }
                DIR_REL_RIGHT -> {
                    physicsObj.GetLocalAngles(ang)
                    ang.ToVectors(getVec3_origin(), vec)
                }
                DIR_REL_FORWARD -> {
                    physicsObj.GetLocalAngles(ang)
                    vec.set(ang.ToForward())
                }
                DIR_REL_BACK -> {
                    physicsObj.GetLocalAngles(ang)
                    vec.set(ang.ToForward().times(-1f))
                }
                else -> {
                    ang.set(0f, dir, 0f)
                    vec.set(GetWorldVector(ang.ToForward()))
                }
            }
        }

        //        private idCurve_Spline<idVec3> GetSpline(idEntity splineEntity);
        private fun Event_SetCallback() {
            if (lastCommand == moverCommand_t.MOVER_ROTATING && 0 == rotate_thread) {
                lastCommand = moverCommand_t.MOVER_NONE
                rotate_thread = idThread.CurrentThreadNum()
                idThread.ReturnInt(true)
            } else if ((lastCommand == moverCommand_t.MOVER_MOVING || lastCommand == moverCommand_t.MOVER_SPLINE) && 0 == move_thread) {
                lastCommand = moverCommand_t.MOVER_NONE
                move_thread = idThread.CurrentThreadNum()
                idThread.ReturnInt(true)
            } else {
                idThread.ReturnInt(false)
            }
        }

        private fun Event_TeamBlocked(blockedPart: idEventArg<idEntity>, blockingEntity: idEventArg<idEntity>) {
            if (SysCvar.g_debugMover.GetBool()) {
                Game_local.gameLocal.Printf(
                    "%d: '%s' stopped due to team member '%s' blocked by '%s'\n",
                    Game_local.gameLocal.time,
                    name,
                    blockedPart.value.name,
                    blockingEntity.value.name
                )
            }
        }

        private fun Event_StopMoving() {
            physicsObj.GetLocalOrigin(dest_position)
            DoneMoving()
        }

        private fun Event_StopRotating() {
            physicsObj.GetLocalAngles(dest_angles)
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                dest_angles,
                Angles.getAng_zero(),
                Angles.getAng_zero()
            )
            DoneRotating()
        }

        private fun Event_UpdateMove() {
            val org = idVec3()
            physicsObj.GetLocalOrigin(org)
            UpdateMoveSound(move.stage)
            when (move.stage) {
                moveStage_t.ACCELERATION_STAGE -> {
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_ACCELLINEAR,
                        Game_local.gameLocal.time,
                        move.acceleration,
                        org,
                        move.dir,
                        Vector.getVec3_origin()
                    )
                    if (move.movetime > 0) {
                        move.stage = moveStage_t.LINEAR_STAGE
                    } else if (move.deceleration > 0) {
                        move.stage = moveStage_t.DECELERATION_STAGE
                    } else {
                        move.stage = moveStage_t.FINISHED_STAGE
                    }
                }
                moveStage_t.LINEAR_STAGE -> {
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_LINEAR,
                        Game_local.gameLocal.time,
                        move.movetime,
                        org,
                        move.dir,
                        Vector.getVec3_origin()
                    )
                    if (move.deceleration != 0) {
                        move.stage = moveStage_t.DECELERATION_STAGE
                    } else {
                        move.stage = moveStage_t.FINISHED_STAGE
                    }
                }
                moveStage_t.DECELERATION_STAGE -> {
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_DECELLINEAR,
                        Game_local.gameLocal.time,
                        move.deceleration,
                        org,
                        move.dir,
                        Vector.getVec3_origin()
                    )
                    move.stage = moveStage_t.FINISHED_STAGE
                }
                moveStage_t.FINISHED_STAGE -> {
                    if (SysCvar.g_debugMover.GetBool()) {
                        Game_local.gameLocal.Printf("%d: '%s' move done\n", Game_local.gameLocal.time, name)
                    }
                    DoneMoving()
                }
            }
        }

        private fun Event_UpdateRotation() {
            val ang = idAngles()
            physicsObj.GetLocalAngles(ang)
            UpdateRotationSound(rot.stage)
            when (rot.stage) {
                moveStage_t.ACCELERATION_STAGE -> {
                    physicsObj.SetAngularExtrapolation(
                        Extrapolate.EXTRAPOLATION_ACCELLINEAR,
                        Game_local.gameLocal.time,
                        rot.acceleration,
                        ang,
                        rot.rot,
                        Angles.getAng_zero()
                    )
                    if (rot.movetime > 0) {
                        rot.stage = moveStage_t.LINEAR_STAGE
                    } else if (rot.deceleration > 0) {
                        rot.stage = moveStage_t.DECELERATION_STAGE
                    } else {
                        rot.stage = moveStage_t.FINISHED_STAGE
                    }
                }
                moveStage_t.LINEAR_STAGE -> {
                    if (!stopRotation && 0 == rot.deceleration) {
                        physicsObj.SetAngularExtrapolation(
                            Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                            Game_local.gameLocal.time,
                            rot.movetime,
                            ang,
                            rot.rot,
                            Angles.getAng_zero()
                        )
                    } else {
                        physicsObj.SetAngularExtrapolation(
                            Extrapolate.EXTRAPOLATION_LINEAR,
                            Game_local.gameLocal.time,
                            rot.movetime,
                            ang,
                            rot.rot,
                            Angles.getAng_zero()
                        )
                    }
                    if (rot.deceleration != 0) {
                        rot.stage = moveStage_t.DECELERATION_STAGE
                    } else {
                        rot.stage = moveStage_t.FINISHED_STAGE
                    }
                }
                moveStage_t.DECELERATION_STAGE -> {
                    physicsObj.SetAngularExtrapolation(
                        Extrapolate.EXTRAPOLATION_DECELLINEAR,
                        Game_local.gameLocal.time,
                        rot.deceleration,
                        ang,
                        rot.rot,
                        Angles.getAng_zero()
                    )
                    rot.stage = moveStage_t.FINISHED_STAGE
                }
                moveStage_t.FINISHED_STAGE -> {
                    lastCommand = moverCommand_t.MOVER_NONE
                    if (stopRotation) {
                        // set our final angles so that we get rid of any numerical inaccuracy
                        dest_angles.Normalize360()
                        physicsObj.SetAngularExtrapolation(
                            Extrapolate.EXTRAPOLATION_NONE,
                            0,
                            0,
                            dest_angles,
                            Angles.getAng_zero(),
                            Angles.getAng_zero()
                        )
                        stopRotation = false
                    } else if (physicsObj.GetAngularExtrapolationType() == Extrapolate.EXTRAPOLATION_ACCELLINEAR) {
                        // keep our angular velocity constant
                        physicsObj.SetAngularExtrapolation(
                            Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                            Game_local.gameLocal.time,
                            0,
                            ang,
                            rot.rot,
                            Angles.getAng_zero()
                        )
                    }
                    if (SysCvar.g_debugMover.GetBool()) {
                        Game_local.gameLocal.Printf("%d: '%s' rotation done\n", Game_local.gameLocal.time, name)
                    }
                    DoneRotating()
                }
            }
        }

        private fun Event_SetMoveSpeed(speed: idEventArg<Float>) {
            if (speed.value <= 0) {
                idGameLocal.Error("Cannot set speed less than or equal to 0.")
            }
            move_speed = speed.value
            move_time = 0 // move_time is calculated for each move when move_speed is non-0
        }

        private fun Event_SetMoveTime(time: idEventArg<Float>) {
            if (time.value <= 0) {
                idGameLocal.Error("Cannot set time less than or equal to 0.")
            }
            move_speed = 0f
            move_time = Math_h.SEC2MS(time.value).toInt()
        }

        private fun Event_SetDecelerationTime(time: idEventArg<Float>) {
            if (time.value < 0) {
                idGameLocal.Error("Cannot set deceleration time less than 0.")
            }
            deceltime = Math_h.SEC2MS(time.value).toInt()
        }

        private fun Event_SetAccellerationTime(time: idEventArg<Float>) {
            if (time.value < 0) {
                idGameLocal.Error("Cannot set acceleration time less than 0.")
            }
            acceltime = Math_h.SEC2MS(time.value).toInt()
        }

        private fun Event_MoveTo(ent: idEventArg<idEntity>) {
            if (null == ent.value) {
                Game_local.gameLocal.Warning("Entity not found")
            }
            dest_position.set(GetLocalCoordinates(ent.value.GetPhysics().GetOrigin()))
            BeginMove(idThread.CurrentThread())
        }

        private fun Event_MoveToPos(pos: idEventArg<idVec3>) {
            dest_position.set(GetLocalCoordinates(pos.value))
            BeginMove(null)
        }

        private fun Event_MoveDir(angle: idEventArg<Float>, distance: idEventArg<Float>) {
            val dir = idVec3()
            val org = idVec3()
            physicsObj.GetLocalOrigin(org)
            VectorForDir(angle.value, dir)
            dest_position.set(org.plus(dir.times(distance.value)))
            BeginMove(idThread.CurrentThread())
        }

        private fun Event_MoveAccelerateTo(speed: idEventArg<Float>, time: idEventArg<Float>) {
            val v: Float
            val org = idVec3()
            val dir = idVec3()
            val at: Int
            if (time.value < 0) {
                idGameLocal.Error("idMover::Event_MoveAccelerateTo: cannot set acceleration time less than 0.")
            }
            dir.set(physicsObj.GetLinearVelocity())
            v = dir.Normalize()

            // if not moving already
            if (v == 0.0f) {
                idGameLocal.Error("idMover::Event_MoveAccelerateTo: not moving.")
            }

            // if already moving faster than the desired speed
            if (v >= speed.value) {
                return
            }
            at = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(time.value).toInt())
            lastCommand = moverCommand_t.MOVER_MOVING
            physicsObj.GetLocalOrigin(org)
            move.stage = moveStage_t.ACCELERATION_STAGE
            move.acceleration = at
            move.movetime = 0
            move.deceleration = 0
            StartSound("snd_accel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
            StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_ACCELLINEAR,
                Game_local.gameLocal.time,
                move.acceleration,
                org,
                dir.times(speed.value - v),
                dir.times(v)
            )
        }

        private fun Event_MoveDecelerateTo(speed: idEventArg<Float>, time: idEventArg<Float>) {
            val v: Float
            val org = idVec3()
            val dir = idVec3()
            val dt: Int
            if (time.value < 0) {
                idGameLocal.Error("idMover::Event_MoveDecelerateTo: cannot set deceleration time less than 0.")
            }
            dir.set(physicsObj.GetLinearVelocity())
            v = dir.Normalize()

            // if not moving already
            if (v == 0.0f) {
                idGameLocal.Error("idMover::Event_MoveDecelerateTo: not moving.")
            }

            // if already moving slower than the desired speed
            if (v <= speed.value) {
                return
            }
            dt = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(time.value).toInt())
            lastCommand = moverCommand_t.MOVER_MOVING
            physicsObj.GetLocalOrigin(org)
            move.stage = moveStage_t.DECELERATION_STAGE
            move.acceleration = 0
            move.movetime = 0
            move.deceleration = dt
            StartSound("snd_decel", gameSoundChannel_t.SND_CHANNEL_BODY2, 0, false)
            StartSound("snd_move", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false)
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELLINEAR,
                Game_local.gameLocal.time,
                move.deceleration,
                org,
                dir.times(v - speed.value),
                dir.times(speed.value)
            )
        }

        private fun Event_RotateDownTo(_axis: idEventArg<Int>, angle: idEventArg<Float>) {
            val axis: Int = _axis.value
            val ang = idAngles()
            if (axis < 0 || axis > 2) {
                idGameLocal.Error("Invalid axis")
            }
            physicsObj.GetLocalAngles(ang)
            dest_angles.set(axis, angle.value)
            if (dest_angles[axis] > ang[axis]) {
                dest_angles.minusAssign(axis, 360f)
            }
            BeginRotation(idThread.CurrentThread(), true)
        }

        private fun Event_RotateUpTo(_axis: idEventArg<Int>, angle: idEventArg<Float>) {
            val axis: Int = _axis.value
            val ang = idAngles()
            if (axis < 0 || axis > 2) {
                idGameLocal.Error("Invalid axis")
            }
            physicsObj.GetLocalAngles(ang)
            dest_angles.set(axis, angle.value)
            if (dest_angles[axis] < ang[axis]) {
                dest_angles.plusAssign(axis, 360f)
            }
            BeginRotation(idThread.CurrentThread(), true)
        }

        private fun Event_RotateTo(angles: idEventArg<idAngles>) {
            dest_angles.set(angles.value)
            BeginRotation(idThread.CurrentThread(), true)
        }

        private fun Event_Rotate(angles: idEventArg<idVec3>) {
            val ang = idAngles()
            if (rotate_thread != 0) {
                DoneRotating()
            }
            physicsObj.GetLocalAngles(ang)
            dest_angles =
                ang.plus(angles.value.times((move_time - (acceltime + deceltime) / 2).toFloat()).times(0.001f))
            BeginRotation(idThread.CurrentThread(), false)
        }

        private fun Event_RotateOnce(angles: idEventArg<idVec3>) {
            val ang = idAngles()
            if (rotate_thread != 0) {
                DoneRotating()
            }
            physicsObj.GetLocalAngles(ang)
            dest_angles = ang.plus(angles.value)
            BeginRotation(idThread.CurrentThread(), true)
        }

        private fun Event_Bob(speed: idEventArg<Float>, phase: idEventArg<Float>, depth: idEventArg<idVec3>) {
            val org = idVec3()
            physicsObj.GetLocalOrigin(org)
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                (speed.value * 1000 * phase.value).toInt(),
                (speed.value * 500).toInt(),
                org,
                depth.value.times(2.0f),
                Vector.getVec3_origin()
            )
        }

        private fun Event_Sway(speed: idEventArg<Float>, phase: idEventArg<Float>, _depth: idEventArg<idVec3>) {
            val depth = idAngles(_depth.value)
            val ang = idAngles()
            val angSpeed: idAngles?
            val duration: Float
            physicsObj.GetLocalAngles(ang)
            assert(speed.value > 0.0f)
            duration =
                idMath.Sqrt(depth[0] * depth[0] + depth[1] * depth[1] + depth[2] * depth[2]) / speed.value
            angSpeed = depth.div(duration * idMath.SQRT_1OVER2)
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                (duration * 1000.0f * phase.value).toInt(),
                (duration * 1000.0f).toInt(),
                ang,
                angSpeed,
                Angles.getAng_zero()
            )
        }

        private fun Event_SetAccelSound(sound: idEventArg<String?>?) {
//	refSound.SetSound( "accel", sound );
        }

        private fun Event_SetDecelSound(sound: idEventArg<String?>?) {
//	refSound.SetSound( "decel", sound );
        }

        private fun Event_SetMoveSound(sound: idEventArg<String?>?) {
//	refSound.SetSound( "move", sound );
        }

        private fun Event_FindGuiTargets() {
            FindGuiTargets()
        }

        private fun Event_InitGuiTargets() {
            SetGuiStates(guiBinaryMoverStates[TempDump.etoi(moverState_t.MOVER_POS1)])
        }

        private fun Event_EnableSplineAngles() {
            useSplineAngles = true
        }

        private fun Event_DisableSplineAngles() {
            useSplineAngles = false
        }

        private fun Event_RemoveInitialSplineAngles() {
            val spline: idCurve_Spline<idVec3>?
            val ang: idAngles?
            spline = physicsObj.GetSpline()
            if (null == spline) {
                return
            }
            ang = spline.GetCurrentFirstDerivative(0f).ToAngles()
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                ang.unaryMinus(),
                Angles.getAng_zero(),
                Angles.getAng_zero()
            )
        }

        private fun Event_StartSpline(_splineEntity: idEventArg<idEntity?>) {
            val splineEntity = _splineEntity.value
            val spline: idCurve_Spline<idVec3>?
            if (null == splineEntity) {
                return
            }

            // Needed for savegames
            splineEnt.oSet(splineEntity)
            spline = splineEntity.GetSpline()
            if (null == spline) {
                return
            }
            lastCommand = moverCommand_t.MOVER_SPLINE
            move_thread = 0
            if (acceltime + deceltime > move_time) {
                acceltime = move_time / 2
                deceltime = move_time - acceltime
            }
            move.stage = moveStage_t.FINISHED_STAGE
            move.acceleration = acceltime
            move.movetime = move_time
            move.deceleration = deceltime
            spline.MakeUniform(move_time.toFloat())
            spline.ShiftTime(Game_local.gameLocal.time - spline.GetTime(0))
            physicsObj.SetSpline(spline, move.acceleration, move.deceleration, useSplineAngles)
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                dest_position,
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
        }

        private fun Event_StopSpline() {
            physicsObj.SetSpline(null, 0, 0, useSplineAngles)
            splineEnt.oSet(null)
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            Show()
            Event_StartSpline(idEventArg.toArg(this))
        }

        private fun Event_PostRestore(
            start: idEventArg<Int>, total: idEventArg<Int>, accel: idEventArg<Int>,
            decel: idEventArg<Int>, useSplineAng: idEventArg<Int>
        ) {
            val spline: idCurve_Spline<idVec3>
            val splineEntity = splineEnt.GetEntity()
            if (null == splineEntity) {
                // We should never get this event if splineEnt is invalid
                idLib.common.Warning("Invalid spline entity during restore\n")
                return
            }
            spline = splineEntity.GetSpline()!!
            spline.MakeUniform(total.value.toFloat())
            spline.ShiftTime(start.value - spline.GetTime(0))
            physicsObj.SetSpline(spline, accel.value, decel.value, useSplineAng.value != 0)
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                dest_position,
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
        }

        private fun Event_IsMoving() {
            idThread.ReturnInt(physicsObj.GetLinearExtrapolationType() != Extrapolate.EXTRAPOLATION_NONE)
        }

        private fun Event_IsRotating() {
            idThread.ReturnInt(physicsObj.GetAngularExtrapolationType() != Extrapolate.EXTRAPOLATION_NONE)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        enum class moveStage_t {
            ACCELERATION_STAGE, LINEAR_STAGE, DECELERATION_STAGE, FINISHED_STAGE
        }

        enum class moverCommand_t {
            MOVER_NONE, MOVER_ROTATING, MOVER_MOVING, MOVER_SPLINE
        }

        protected class moveState_t {
            var acceleration = 0
            var deceleration = 0
            val dir: idVec3 = idVec3()
            var movetime = 0
            var stage: moveStage_t = moveStage_t.ACCELERATION_STAGE
        }

        protected class rotationState_t {
            var acceleration = 0
            var deceleration = 0
            var movetime = 0
            var rot: idAngles = idAngles()
            var stage: moveStage_t = moveStage_t.ACCELERATION_STAGE
        }

        init {
//	memset( &move, 0, sizeof( move ) );
            move = moveState_t()
            //	memset( &rot, 0, sizeof( rot ) );
            rot = rotationState_t()
            move_thread = 0
            rotate_thread = 0
            dest_angles = idAngles()
            angle_delta = idAngles()
            dest_position = idVec3()
            move_delta = idVec3()
            move_speed = 0.0f
            move_time = 0
            deceltime = 0
            acceltime = 0
            stopRotation = false
            useSplineAngles = true
            lastCommand = moverCommand_t.MOVER_NONE
            damage = 0.0f
            areaPortal = 0
            fl.networkSync = true
            physicsObj = idPhysics_Parametric()
            splineEnt = idEntityPtr()
        }
    }

    /*
     ===============================================================================

     idSplinePath, holds a spline path to be used by an idMover

     ===============================================================================
     */
    class idSplinePath  //	CLASS_PROTOTYPE( idSplinePath );
        : idEntity() {
        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class floorInfo_s {
        var door: idStr = idStr()
        var floor = 0
        val pos: idVec3 = idVec3()
    }

    /*
     ===============================================================================

     idElevator

     ===============================================================================
     */
    class idElevator : idMover() {
        companion object {
            // CLASS_PROTOTYPE( idElevator );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMover.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idElevator> { obj: Any?, activator: idEventArg<*>? ->
                        idElevator::Event_Activate
                    }
                eventCallbacks[EV_TeamBlocked] =
                    eventCallback_t2<idElevator> { obj: Any?, blockedEntity: idEventArg<*>?, blockingEntity: idEventArg<*>? ->
                        idElevator::Event_TeamBlocked
                    }
                eventCallbacks[EV_PartBlocked] =
                    eventCallback_t1<idElevator> { obj: Any?, blockingEntity: idEventArg<*>? ->
                        idElevator::Event_PartBlocked
                    }
                eventCallbacks[EV_PostArrival] =
                    eventCallback_t0<idElevator> { obj: Any? -> idElevator::Event_PostFloorArrival }
                eventCallbacks[EV_GotoFloor] =
                    eventCallback_t1<idElevator> { obj: Any?, floor: idEventArg<*>? -> idElevator::Event_GotoFloor }
                eventCallbacks[Entity.EV_Touch] =
                    eventCallback_t2<idElevator> { obj: Any?, other: idEventArg<*>?, trace: idEventArg<*>? -> idElevator::Event_Touch }
            }
        }

        private var controlsDisabled: Boolean
        private var currentFloor: Int
        private val floorInfo: ArrayList<floorInfo_s>
        private var lastFloor: Int
        private var lastTouchTime: Int
        private var pendingFloor: Int
        private var returnFloor: Int
        private var returnTime: Float

        //
        private var state: elevatorState_t = elevatorState_t.INIT
        override fun Spawn() {
            super.Spawn()
            var str: idStr
            val len1: Int
            lastFloor = 0
            currentFloor = 0
            pendingFloor = spawnArgs.GetInt("floor", "1")
            SetGuiStates(if (pendingFloor == 1) guiBinaryMoverStates[0] else guiBinaryMoverStates[1])
            returnTime = spawnArgs.GetFloat("returnTime")
            returnFloor = spawnArgs.GetInt("returnFloor")
            len1 = "floorPos_".length
            var kv = spawnArgs.MatchPrefix("floorPos_", null)
            while (kv != null) {
                str = kv.GetKey().Right(kv.GetKey().Length() - len1)
                val fi = floorInfo_s()
                fi.floor = str.toString().toInt()
                fi.door = idStr(spawnArgs.GetString(Str.va("floorDoor_%d", fi.floor)))
                fi.pos.set(spawnArgs.GetVector(kv.GetKey().toString()))
                floorInfo.add(fi)
                kv = spawnArgs.MatchPrefix("floorPos_", kv)
            }
            lastTouchTime = 0
            state = elevatorState_t.INIT
            BecomeActive(Entity.TH_THINK or Entity.TH_PHYSICS)
            PostEventMS(EV_Mover_InitGuiTargets, 0)
            controlsDisabled = false
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteInt(TempDump.etoi(state))
            savefile.WriteInt(floorInfo.size)
            i = 0
            while (i < floorInfo.size) {
                savefile.WriteVec3(floorInfo[i].pos)
                savefile.WriteString(floorInfo[i].door.toString())
                savefile.WriteInt(floorInfo[i].floor)
                i++
            }
            savefile.WriteInt(currentFloor)
            savefile.WriteInt(pendingFloor)
            savefile.WriteInt(lastFloor)
            savefile.WriteBool(controlsDisabled)
            savefile.WriteFloat(returnTime)
            savefile.WriteInt(returnFloor)
            savefile.WriteInt(lastTouchTime)
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            val num: Int
            state = Mover.idElevator.elevatorState_t.values()[savefile.ReadInt()]
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                val floor = floorInfo_s()
                savefile.ReadVec3(floor.pos)
                savefile.ReadString(floor.door)
                floor.floor = savefile.ReadInt()
                floorInfo.add(floor)
                i++
            }
            currentFloor = savefile.ReadInt()
            pendingFloor = savefile.ReadInt()
            lastFloor = savefile.ReadInt()
            controlsDisabled = savefile.ReadBool()
            returnTime = savefile.ReadFloat()
            returnFloor = savefile.ReadInt()
            lastTouchTime = savefile.ReadInt()
        }

        override fun HandleSingleGuiCommand(entityGui: idEntity?, src: idLexer): Boolean {
            val token = idToken()
            if (controlsDisabled) {
                return false
            }
            if (!src.ReadToken(token)) {
                return false
            }
            if (token.toString() == ";") {
                return false
            }
            if (token.Icmp("changefloor") == 0) {
                if (src.ReadToken(token)) {
                    val newFloor = token.toString().toInt()
                    if (newFloor == currentFloor) {
                        // open currentFloor and interior doors
                        OpenInnerDoor()
                        OpenFloorDoor(currentFloor)
                    } else {
                        val door = GetDoor(spawnArgs.GetString("innerdoor"))
                        if (door != null && door.IsOpen()) {
                            PostEventSec(EV_GotoFloor, 0.5f, newFloor)
                        } else {
                            ProcessEvent(EV_GotoFloor, newFloor)
                        }
                    }
                    return true
                }
            }
            src.UnreadToken(token)
            return false
        }

        fun Event_GotoFloor(floor: idEventArg<Int>) {
            val fi = GetFloorInfo(floor.value)
            if (fi != null) {
                val door = GetDoor(spawnArgs.GetString("innerdoor"))
                if (door != null) {
                    if (door.IsBlocked() || door.IsOpen()) {
                        PostEventSec(EV_GotoFloor, 0.5f, floor)
                        return
                    }
                }
                DisableAllDoors()
                CloseAllDoors()
                state = elevatorState_t.WAITING_ON_DOORS
                pendingFloor = floor.value
            }
        }

        fun GetFloorInfo(floor: Int): floorInfo_s? {
            for (i in 0 until floorInfo.size) {
                if (floorInfo[i].floor == floor) {
                    return floorInfo[i]
                }
            }
            return null
        }

        override fun DoneMoving() {
            super.DoneMoving()
            EnableProperDoors()
            var kv = spawnArgs.MatchPrefix("statusGui")
            while (kv != null) {
                val ent = Game_local.gameLocal.FindEntity(kv.GetValue().toString())
                if (ent != null) {
                    for (j in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                        if (ent.GetRenderEntity() != null && ent.GetRenderEntity().gui[j] != null) {
                            ent.GetRenderEntity().gui[j].SetStateString("floor", Str.va("%d", currentFloor))
                            ent.GetRenderEntity().gui[j].StateChanged(Game_local.gameLocal.time, true)
                        }
                    }
                    ent.UpdateVisuals()
                }
                kv = spawnArgs.MatchPrefix("statusGui", kv)
            }
            if (spawnArgs.GetInt("pauseOnFloor", "-1") == currentFloor) {
                PostEventSec(EV_PostArrival, spawnArgs.GetFloat("pauseTime"))
            } else {
                Event_PostFloorArrival()
            }
        }

        override fun BeginMove(thread: idThread? /*= NULL*/) {
            controlsDisabled = true
            CloseAllDoors()
            DisableAllDoors()
            var kv = spawnArgs.MatchPrefix("statusGui")
            while (kv != null) {
                val ent = Game_local.gameLocal.FindEntity(kv.GetValue().toString())
                if (ent != null) {
                    for (j in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                        if (ent.GetRenderEntity() != null && ent.GetRenderEntity().gui[j] != null) {
                            ent.GetRenderEntity().gui[j].SetStateString("floor", "")
                            ent.GetRenderEntity().gui[j].StateChanged(Game_local.gameLocal.time, true)
                        }
                    }
                    ent.UpdateVisuals()
                }
                kv = spawnArgs.MatchPrefix("statusGui", kv)
            }
            SetGuiStates(if (pendingFloor == 1) guiBinaryMoverStates[3] else guiBinaryMoverStates[2])
            super.BeginMove(thread)
        }

        //
        //        protected void SpawnTrigger(final idVec3 pos);
        //
        //        protected void GetLocalTriggerPosition();
        //
        protected fun Event_Touch(other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            if (Game_local.gameLocal.time < lastTouchTime + 2000) {
                return
            }
            if (other.value !is idPlayer) {
                return
            }
            lastTouchTime = Game_local.gameLocal.time
            if (thinkFlags and Entity.TH_PHYSICS != 0) {
                return
            }
            val triggerFloor = spawnArgs.GetInt("triggerFloor")
            if (spawnArgs.GetBool("trigger") && triggerFloor != currentFloor) {
                PostEventSec(EV_GotoFloor, 0.25f, triggerFloor)
            }
        }

        private fun GetDoor(name: String): idDoor? {
            val ent: idEntity?
            val master: idMover_Binary?
            var doorEnt: idDoor?
            doorEnt = null
            if (name != null && !name.isNullOrEmpty()) {
                ent = Game_local.gameLocal.FindEntity(name)
                if (ent != null && ent is idDoor) {
                    doorEnt = ent
                    master = doorEnt.GetMoveMaster()
                    if (master !== doorEnt) {
                        doorEnt = if (master is idDoor) {
                            master
                        } else {
                            null
                        }
                    }
                }
            }
            return doorEnt
        }

        override fun Think() {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            val doorEnt = GetDoor(spawnArgs.GetString("innerdoor"))
            if (state == elevatorState_t.INIT) {
                state = elevatorState_t.IDLE
                if (doorEnt != null) {
                    doorEnt.BindTeam(this)
                    doorEnt.spawnArgs.Set("snd_open", "")
                    doorEnt.spawnArgs.Set("snd_close", "")
                    doorEnt.spawnArgs.Set("snd_opened", "")
                }
                for (i in 0 until floorInfo.size) {
                    val door = GetDoor(floorInfo[i].door.toString())
                    door?.SetCompanion(doorEnt)
                }
                Event_GotoFloor(idEventArg.toArg(pendingFloor))
                DisableAllDoors()
                SetGuiStates(if (pendingFloor == 1) guiBinaryMoverStates[0] else guiBinaryMoverStates[1])
            } else if (state == elevatorState_t.WAITING_ON_DOORS) {
                state = if (doorEnt != null) {
                    if (doorEnt.IsOpen()) elevatorState_t.WAITING_ON_DOORS else elevatorState_t.IDLE
                } else {
                    elevatorState_t.IDLE
                }
                if (state == elevatorState_t.IDLE) {
                    lastFloor = currentFloor
                    currentFloor = pendingFloor
                    val fi = GetFloorInfo(currentFloor)
                    if (fi != null) {
                        MoveToPos(fi.pos)
                    }
                }
            }
            RunPhysics()
            Present()
        }

        private fun OpenInnerDoor() {
            val door = GetDoor(spawnArgs.GetString("innerdoor"))
            door?.Open()
        }

        private fun OpenFloorDoor(floor: Int) {
            val fi = GetFloorInfo(floor)
            if (fi != null) {
                val door = GetDoor(fi.door.toString())
                door?.Open()
            }
        }

        private fun CloseAllDoors() {
            var door = GetDoor(spawnArgs.GetString("innerdoor"))
            door?.Close()
            for (i in 0 until floorInfo.size) {
                door = GetDoor(floorInfo[i].door.toString())
                door?.Close()
            }
        }

        private fun DisableAllDoors() {
            var door = GetDoor(spawnArgs.GetString("innerdoor"))
            door?.Enable(false)
            for (i in 0 until floorInfo.size) {
                door = GetDoor(floorInfo[i].door.toString())
                door?.Enable(false)
            }
        }

        private fun EnableProperDoors() {
            var door = GetDoor(spawnArgs.GetString("innerdoor"))
            door?.Enable(true)
            for (i in 0 until floorInfo.size) {
                if (floorInfo[i].floor == currentFloor) {
                    door = GetDoor(floorInfo[i].door.toString())
                    if (door != null) {
                        door.Enable(true)
                        break
                    }
                }
            }
        }

        private fun Event_TeamBlocked(blockedEntity: idEventArg<idEntity>, blockingEntity: idEventArg<idEntity>) {
            if (blockedEntity.value === this) {
                Event_GotoFloor(idEventArg.toArg(lastFloor))
            } else if (blockedEntity != null && blockedEntity.value is idDoor) {
                // open the inner doors if one is blocked
                val blocked = blockedEntity.value as idDoor
                val door = GetDoor(spawnArgs.GetString("innerdoor"))
                if (door != null && blocked.GetMoveMaster() === door.GetMoveMaster()) { //TODO:equalds
                    door.SetBlocked(true)
                    OpenInnerDoor()
                    OpenFloorDoor(currentFloor)
                }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val triggerFloor = spawnArgs.GetInt("triggerFloor")
            if (spawnArgs.GetBool("trigger") && triggerFloor != currentFloor) {
                Event_GotoFloor(idEventArg.toArg(triggerFloor))
            }
        }

        private fun Event_PostFloorArrival() {
            OpenFloorDoor(currentFloor)
            OpenInnerDoor()
            SetGuiStates(if (currentFloor == 1) guiBinaryMoverStates[0] else guiBinaryMoverStates[1])
            controlsDisabled = false
            if (returnTime > 0.0f && returnFloor != currentFloor) {
                PostEventSec(EV_GotoFloor, returnTime, returnFloor)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        enum class elevatorState_t {
            INIT, IDLE, WAITING_ON_DOORS
        }

        init {
            state = elevatorState_t.INIT
            floorInfo = ArrayList()
            currentFloor = 0
            pendingFloor = 0
            lastFloor = 0
            controlsDisabled = false
            lastTouchTime = 0
            returnFloor = 0
            returnTime = 0f
        }
    }

    /*
     ===============================================================================

     idMover_Binary

     Doors, plats, and buttons are all binary (two position) movers
     Pos1 is "at rest", pos2 is "activated"

     ===============================================================================
     */
    open class idMover_Binary : idEntity() {
        protected fun GetMovedir(dir: Float, movedir: idVec3) {
            if (dir == -1f) {
                movedir.set(0f, 0f, 1f)
            } else if (dir == -2f) {
                movedir.set(0f, 0f, -1f)
            } else {
                movedir.set(idAngles(0f, dir, 0f).ToForward())
            }
        }

        companion object {
            // CLASS_PROTOTYPE( idMover_Binary );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            /*
         ===============
         idMover_Binary::GetMovedir

         The editor only specifies a single value for angles (yaw),
         but we have special constants to generate an up or down direction.
         Angles will be cleared, because it is being used to represent a direction
         instead of an orientation.
         ===============
         */


            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_FindGuiTargets] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_FindGuiTargets }
                eventCallbacks[Script_Thread.EV_Thread_SetCallback] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_SetCallback }
                eventCallbacks[EV_Mover_ReturnToPos1] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_ReturnToPos1 }
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idMover_Binary> { obj: Any?, activator: idEventArg<*>? ->
                        idMover_Binary::Event_Use_BinaryMover
                    }
                eventCallbacks[EV_ReachedPos] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_Reached_BinaryMover }
                eventCallbacks[EV_Mover_MatchTeam] =
                    eventCallback_t2<idMover_Binary> { obj: Any?, newstate: idEventArg<*>?, time: idEventArg<*>? ->
                        idMover_Binary::Event_MatchActivateTeam
                    }
                eventCallbacks[EV_Mover_Enable] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_Enable }
                eventCallbacks[EV_Mover_Disable] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_Disable }
                eventCallbacks[EV_Mover_OpenPortal] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_OpenPortal }
                eventCallbacks[EV_Mover_ClosePortal] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_ClosePortal }
                eventCallbacks[EV_Mover_InitGuiTargets] =
                    eventCallback_t0<idMover_Binary> { obj: Any? -> idMover_Binary::Event_InitGuiTargets }
            }
        }

        protected var accelTime: Int
        protected var activateChain: idMover_Binary?
        protected val activatedBy: idEntityPtr<idEntity>
        protected var   /*qhandle_t*/areaPortal // 0 = no portal
                : Int
        protected var blocked: Boolean
        protected var buddies: ArrayList<idStr> = ArrayList()
        protected var damage: Float
        protected var decelTime: Int
        protected var duration: Int
        protected var enabled: Boolean
        protected val guiTargets: ArrayList<idEntityPtr<idEntity>>
        protected var moveMaster: idMover_Binary?
        protected var move_thread: Int
        protected var moverState: moverState_t = moverState_t.MOVER_1TO2
        protected var physicsObj: idPhysics_Parametric
        protected val pos1: idVec3
        protected val pos2: idVec3
        protected var sound1to2: Int
        protected var sound2to1: Int
        protected var soundLoop: Int
        protected var soundPos1: Int
        protected var soundPos2: Int
        protected var stateStartTime: Int
        protected var team: idStr = idStr()
        protected var updateStatus // 1 = lock behaviour, 2 = open close status
                : Int
        protected var wait: Float

        // ~idMover_Binary();
        /*
         ================
         idMover_Binary::Spawn

         Base class for all movers.

         "wait"		wait before returning (3 default, -1 = never return)
         "speed"		movement speed
         ================
         */
        override fun Spawn() {
            super.Spawn()
            var ent: idEntity?
            val temp = arrayOf("")
            move_thread = 0
            enabled = true
            areaPortal = 0
            activateChain = null
            wait = spawnArgs.GetFloat("wait", "0")
            updateStatus = spawnArgs.GetInt("updateStatus", "0")
            var kv = spawnArgs.MatchPrefix("buddy", null)
            while (kv != null) {
                buddies.add(kv.GetValue())
                kv = spawnArgs.MatchPrefix("buddy", kv)
            }
            spawnArgs.GetString("team", "", temp)
            team = idStr(temp[0])
            if (0 == team.Length()) {
                ent = this
            } else {
                // find the first entity spawned on this team (which could be us)
                ent = Game_local.gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    if (ent is idMover_Binary && TempDump.NOT(
                            idStr.Icmp(
                                ent.team.toString(),
                                temp[0]
                            ).toDouble()
                        )
                    ) {
                        break
                    }
                    ent = ent.spawnNode.Next()
                }
                if (null == ent) {
                    ent = this
                }
            }
            moveMaster = ent as idMover_Binary

            // create a physics team for the binary mover parts
            if (ent !== this) {
                JoinTeam(ent)
            }
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("solid", "1")) {
                physicsObj.SetContents(0)
            }
            if (!spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                GetPhysics().GetOrigin(),
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                GetPhysics().GetAxis().ToAngles(),
                Angles.getAng_zero(),
                Angles.getAng_zero()
            )
            SetPhysics(physicsObj)
            if (moveMaster !== this) {
                JoinActivateTeam(moveMaster!!)
            }
            val soundOrigin = idBounds()
            var slave: idMover_Binary?
            soundOrigin.Clear()
            slave = moveMaster
            while (slave != null) {
                soundOrigin.timesAssign(slave.GetPhysics().GetAbsBounds())
                slave = slave.activateChain
            }
            moveMaster!!.refSound.origin.set(soundOrigin.GetCenter())
            if (spawnArgs.MatchPrefix("guiTarget") != null) {
                if (Game_local.gameLocal.GameState() == gameState_t.GAMESTATE_STARTUP) {
                    PostEventMS(EV_FindGuiTargets, 0)
                } else {
                    // not during spawn, so it's ok to get the targets
                    FindGuiTargets()
                }
            }
        }

        override fun Save(savefile: idSaveGame) {
            var i: Int
            savefile.WriteVec3(pos1)
            savefile.WriteVec3(pos2)
            savefile.WriteInt(TempDump.etoi(moverState))
            savefile.WriteObject(moveMaster!!)
            savefile.WriteObject(activateChain!!)
            savefile.WriteInt(soundPos1)
            savefile.WriteInt(sound1to2)
            savefile.WriteInt(sound2to1)
            savefile.WriteInt(soundPos2)
            savefile.WriteInt(soundLoop)
            savefile.WriteFloat(wait)
            savefile.WriteFloat(damage)
            savefile.WriteInt(duration)
            savefile.WriteInt(accelTime)
            savefile.WriteInt(decelTime)
            activatedBy.Save(savefile)
            savefile.WriteInt(stateStartTime)
            savefile.WriteString(team)
            savefile.WriteBool(enabled)
            savefile.WriteInt(move_thread)
            savefile.WriteInt(updateStatus)
            savefile.WriteInt(buddies.size)
            i = 0
            while (i < buddies.size) {
                savefile.WriteString(buddies[i])
                i++
            }
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteInt(areaPortal)
            if (areaPortal != 0) {
                savefile.WriteInt(Game_local.gameRenderWorld.GetPortalState(areaPortal))
            }
            savefile.WriteBool(blocked)
            savefile.WriteInt(guiTargets.size)
            i = 0
            while (i < guiTargets.size) {
                guiTargets[i].Save(savefile)
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            var num: Int
            val portalState: Int
            val temp = idStr()
            savefile.ReadVec3(pos1)
            savefile.ReadVec3(pos2)
            moverState = Mover.moverState_t.values()[savefile.ReadInt()]
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/moveMaster)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/activateChain)
            soundPos1 = savefile.ReadInt()
            sound1to2 = savefile.ReadInt()
            sound2to1 = savefile.ReadInt()
            soundPos2 = savefile.ReadInt()
            soundLoop = savefile.ReadInt()
            wait = savefile.ReadFloat()
            damage = savefile.ReadFloat()
            duration = savefile.ReadInt()
            accelTime = savefile.ReadInt()
            decelTime = savefile.ReadInt()
            activatedBy.Restore(savefile)
            stateStartTime = savefile.ReadInt()
            savefile.ReadString(team)
            enabled = savefile.ReadBool()
            move_thread = savefile.ReadInt()
            updateStatus = savefile.ReadInt()
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                savefile.ReadString(temp)
                buddies.add(temp)
                i++
            }
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            areaPortal = savefile.ReadInt()
            if (areaPortal != 0) {
                portalState = savefile.ReadInt()
                Game_local.gameLocal.SetPortalState(areaPortal, portalState)
            }
            blocked = savefile.ReadBool()
            guiTargets.clear()
            num = savefile.ReadInt()
            guiTargets.ensureCapacity(num)
            i = 0
            while (i < num) {
                guiTargets[i].Restore(savefile)
                i++
            }
        }

        override fun PreBind() {
            pos1.set(GetWorldCoordinates(pos1))
            pos2.set(GetWorldCoordinates(pos2))
        }

        override fun PostBind() {
            pos1.set(GetLocalCoordinates(pos1))
            pos2.set(GetLocalCoordinates(pos2))
        }

        fun Enable(b: Boolean) {
            enabled = b
        }

        /*
         ================
         idMover_Binary::InitSpeed

         pos1, pos2, and speed are passed in so the movement delta can be calculated
         ================
         */
        fun InitSpeed(mpos1: idVec3, mpos2: idVec3, mspeed: Float, maccelTime: Float, mdecelTime: Float) {
            val move = idVec3()
            val distance: Float
            val speed: Float
            pos1.set(mpos1)
            pos2.set(mpos2)
            accelTime = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(maccelTime).toInt())
            decelTime = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(mdecelTime).toInt())
            speed = if (mspeed != 0f) mspeed else 100f

            // calculate time to reach second position from speed
            move.set(pos2.minus(pos1))
            distance = move.Length()
            duration = idPhysics.SnapTimeToPhysicsFrame((distance * 1000 / speed).toInt())
            if (duration <= 0) {
                duration = 1
            }
            moverState = moverState_t.MOVER_POS1
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                pos1,
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetLinearInterpolation(0, 0, 0, 0, Vector.getVec3_origin(), Vector.getVec3_origin())
            SetOrigin(pos1)
            PostEventMS(EV_Mover_InitGuiTargets, 0)
        }

        /*
         ================
         idMover_Binary::InitTime

         pos1, pos2, and time are passed in so the movement delta can be calculated
         ================
         */
        fun InitTime(mpos1: idVec3, mpos2: idVec3, mtime: Float, maccelTime: Float, mdecelTime: Float) {
            pos1.set(mpos1)
            pos2.set(mpos2)
            accelTime = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(maccelTime).toInt())
            decelTime = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(mdecelTime).toInt())
            duration = idPhysics.SnapTimeToPhysicsFrame(Math_h.SEC2MS(mtime).toInt())
            if (duration <= 0) {
                duration = 1
            }
            moverState = moverState_t.MOVER_POS1
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                pos1,
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetLinearInterpolation(0, 0, 0, 0, Vector.getVec3_origin(), Vector.getVec3_origin())
            SetOrigin(pos1)
            PostEventMS(EV_Mover_InitGuiTargets, 0)
        }

        fun GotoPosition1() {
            var slave: idMover_Binary?
            var partial: Int

            // only the master should control this
            if (moveMaster !== this) {
                moveMaster!!.GotoPosition1()
                return
            }
            SetGuiStates(guiBinaryMoverStates[TempDump.etoi(moverState_t.MOVER_2TO1)])
            if (moverState == moverState_t.MOVER_POS1 || moverState == moverState_t.MOVER_2TO1) {
                // already there, or on the way
                return
            }
            if (moverState == moverState_t.MOVER_POS2) {
                slave = this
                while (slave != null) {
                    slave.CancelEvents(EV_Mover_ReturnToPos1)
                    slave = slave.activateChain
                }
                if (!spawnArgs.GetBool("toggle")) {
                    ProcessEvent(EV_Mover_ReturnToPos1)
                }
                return
            }

            // only partway up before reversing
            if (moverState == moverState_t.MOVER_1TO2) {
                // use the physics times because this might be executed during the physics simulation
                partial = physicsObj.GetLinearEndTime() - physicsObj.GetTime()
                assert(partial >= 0)
                if (partial < 0) {
                    partial = 0
                }
                MatchActivateTeam(moverState_t.MOVER_2TO1, physicsObj.GetTime() - partial)
                // if already at at position 1 (partial == duration) execute the reached event
                if (partial >= duration) {
                    Event_Reached_BinaryMover()
                }
            }
        }

        fun GotoPosition2() {
            var partial: Int

            // only the master should control this
            if (moveMaster !== this) {
                moveMaster!!.GotoPosition2()
                return
            }
            SetGuiStates(guiBinaryMoverStates[TempDump.etoi(moverState_t.MOVER_1TO2)])
            if (moverState == moverState_t.MOVER_POS2 || moverState == moverState_t.MOVER_1TO2) {
                // already there, or on the way
                return
            }
            if (moverState == moverState_t.MOVER_POS1) {
                MatchActivateTeam(moverState_t.MOVER_1TO2, Game_local.gameLocal.time)

                // open areaportal
                ProcessEvent(EV_Mover_OpenPortal)
                return
            }

            // only partway up before reversing
            if (moverState == moverState_t.MOVER_2TO1) {
                // use the physics times because this might be executed during the physics simulation
                partial = physicsObj.GetLinearEndTime() - physicsObj.GetTime()
                assert(partial >= 0)
                if (partial < 0) {
                    partial = 0
                }
                MatchActivateTeam(moverState_t.MOVER_1TO2, physicsObj.GetTime() - partial)
                // if already at at position 2 (partial == duration) execute the reached event
                if (partial >= duration) {
                    Event_Reached_BinaryMover()
                }
            }
        }

        fun Use_BinaryMover(activator: idEntity?) {
            // only the master should be used
            if (moveMaster !== this) {
                moveMaster!!.Use_BinaryMover(activator)
                return
            }
            if (!enabled) {
                return
            }
            activatedBy.oSet(activator)
            if (moverState == moverState_t.MOVER_POS1) {
                // FIXME: start moving USERCMD_MSEC later, because if this was player
                // triggered, gameLocal.time hasn't been advanced yet
                MatchActivateTeam(moverState_t.MOVER_1TO2, Game_local.gameLocal.time + UsercmdGen.USERCMD_MSEC)
                SetGuiStates(guiBinaryMoverStates[TempDump.etoi(moverState_t.MOVER_1TO2)])
                // open areaportal
                ProcessEvent(EV_Mover_OpenPortal)
                return
            }

            // if all the way up, just delay before coming down
            if (moverState == moverState_t.MOVER_POS2) {
                var slave: idMover_Binary?
                if (wait == -1f) {
                    return
                }
                SetGuiStates(guiBinaryMoverStates[TempDump.etoi(moverState_t.MOVER_2TO1)])
                slave = this
                while (slave != null) {
                    slave.CancelEvents(EV_Mover_ReturnToPos1)
                    slave.PostEventSec(EV_Mover_ReturnToPos1, if (spawnArgs.GetBool("toggle")) 0f else wait)
                    slave = slave.activateChain
                }
                return
            }

            // only partway down before reversing
            if (moverState == moverState_t.MOVER_2TO1) {
                GotoPosition2()
                return
            }

            // only partway up before reversing
            if (moverState == moverState_t.MOVER_1TO2) {
                GotoPosition1()
                return
            }
        }

        fun SetGuiStates(state: String) {
            if (guiTargets.size != 0) {
                SetGuiState("movestate", state)
            }
            var mb = activateChain
            while (mb != null) {
                if (mb.guiTargets.size != 0) {
                    mb.SetGuiState("movestate", state)
                }
                mb = mb.activateChain
            }
        }

        fun UpdateBuddies(`val`: Int) {
            var i: Int
            val c: Int
            if (updateStatus == 2) {
                c = buddies.size
                i = 0
                while (i < c) {
                    val buddy = Game_local.gameLocal.FindEntity(buddies[i])
                    if (buddy != null) {
                        buddy.SetShaderParm(RenderWorld.SHADERPARM_MODE, `val`.toFloat())
                        buddy.UpdateVisuals()
                    }
                    i++
                }
            }
        }

        fun GetActivateChain(): idMover_Binary? {
            return activateChain
        }

        fun GetMoveMaster(): idMover_Binary? {
            return moveMaster
        }

        /*
         ================
         idMover_Binary::BindTeam

         All entities in a mover team will be bound
         ================
         */
        fun BindTeam(bindTo: idEntity?) {
            var slave: idMover_Binary?
            slave = this
            while (slave != null) {
                slave.Bind(bindTo, true)
                slave = slave.activateChain
            }
        }

        fun SetBlocked(b: Boolean) {
            var slave = moveMaster
            while (slave != null) {
                slave.blocked = b
                if (b) {
                    var kv = slave.spawnArgs.MatchPrefix("triggerBlocked")
                    while (kv != null) {
                        val ent = Game_local.gameLocal.FindEntity(kv.GetValue().toString())
                        ent?.PostEventMS(Entity.EV_Activate, 0f, moveMaster!!.GetActivator())
                        kv = slave.spawnArgs.MatchPrefix("triggerBlocked", kv)
                    }
                }
                slave = slave.activateChain
            }
        }

        fun IsBlocked(): Boolean {
            return blocked
        }

        fun GetActivator(): idEntity? {
            return activatedBy.GetEntity()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            physicsObj.WriteToSnapshot(msg)
            msg.WriteBits(TempDump.etoi(moverState), 3)
            WriteBindToSnapshot(msg)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            val oldMoverState = moverState
            physicsObj.ReadFromSnapshot(msg)
            moverState = Mover.moverState_t.values()[msg.ReadBits(3)]
            ReadBindFromSnapshot(msg)
            if (msg.HasChanged()) {
                if (moverState != oldMoverState) {
                    UpdateMoverSound(moverState)
                }
                UpdateVisuals()
            }
        }

        fun SetPortalState(open: Boolean) {
            assert(areaPortal != 0)
            Game_local.gameLocal.SetPortalState(
                areaPortal,
                (if (open) portalConnection_t.PS_BLOCK_NONE else portalConnection_t.PS_BLOCK_ALL).ordinal
            )
        }

        /*
         ================
         idMover_Binary::MatchActivateTeam

         All entities in a mover team will move from pos1 to pos2
         in the same amount of time
         ================
         */
        protected fun MatchActivateTeam(newstate: moverState_t, time: Int) {
            var slave: idMover_Binary?
            slave = this
            while (slave != null) {
                slave.SetMoverState(newstate, time)
                slave = slave.activateChain
            }
        }

        /*
         ================
         idMover_Binary::JoinActivateTeam

         Set all entities in a mover team to be enabled
         ================
         */
        protected fun JoinActivateTeam(master: idMover_Binary) {
            activateChain = master.activateChain
            master.activateChain = this
        }

        protected fun UpdateMoverSound(state: moverState_t) {
            if (moveMaster === this) {
                when (state) {
                    moverState_t.MOVER_POS1 -> {}
                    moverState_t.MOVER_POS2 -> {}
                    moverState_t.MOVER_1TO2 -> StartSound(
                        "snd_open",
                        gameSoundChannel_t.SND_CHANNEL_ANY,
                        0,
                        false
                    )
                    moverState_t.MOVER_2TO1 -> StartSound(
                        "snd_close",
                        gameSoundChannel_t.SND_CHANNEL_ANY,
                        0,
                        false
                    )
                }
            }
        }

        protected fun SetMoverState(newstate: moverState_t, time: Int) {
            val delta = idVec3()
            moverState = newstate
            move_thread = 0
            UpdateMoverSound(newstate)
            stateStartTime = time
            when (moverState) {
                moverState_t.MOVER_POS1 -> {
                    Signal(signalNum_t.SIG_MOVER_POS1)
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_NONE,
                        time,
                        0,
                        pos1,
                        Vector.getVec3_origin(),
                        Vector.getVec3_origin()
                    )
                }
                moverState_t.MOVER_POS2 -> {
                    Signal(signalNum_t.SIG_MOVER_POS2)
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_NONE,
                        time,
                        0,
                        pos2,
                        Vector.getVec3_origin(),
                        Vector.getVec3_origin()
                    )
                }
                moverState_t.MOVER_1TO2 -> {
                    Signal(signalNum_t.SIG_MOVER_1TO2)
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_LINEAR,
                        time,
                        duration,
                        pos1,
                        pos2.minus(pos1).times(1000.0f).div(duration.toFloat()),
                        Vector.getVec3_origin()
                    )
                    if (accelTime != 0 || decelTime != 0) {
                        physicsObj.SetLinearInterpolation(time, accelTime, decelTime, duration, pos1, pos2)
                    } else {
                        physicsObj.SetLinearInterpolation(0, 0, 0, 0, pos1, pos2)
                    }
                }
                moverState_t.MOVER_2TO1 -> {
                    Signal(signalNum_t.SIG_MOVER_2TO1)
                    physicsObj.SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_LINEAR,
                        time,
                        duration,
                        pos2,
                        pos1.minus(pos2).times(1000.0f).div(duration.toFloat()),
                        Vector.getVec3_origin()
                    )
                    if (accelTime != 0 || decelTime != 0) {
                        physicsObj.SetLinearInterpolation(time, accelTime, decelTime, duration, pos2, pos1)
                    } else {
                        physicsObj.SetLinearInterpolation(0, 0, 0, 0, pos1, pos2)
                    }
                }
            }
        }

        protected fun GetMoverState(): moverState_t {
            return moverState
        }

        protected fun FindGuiTargets() {
            Game_local.gameLocal.GetTargets(spawnArgs, guiTargets, "guiTarget")
        }

        /*
         ==============================
         idMover_Binary::SetGuiState

         key/val will be set to any renderEntity->gui's on the list
         ==============================
         */
        protected fun SetGuiState(key: String, `val`: String) {
            var i: Int
            i = 0
            while (i < guiTargets.size) {
                val ent = guiTargets[i].GetEntity()
                if (ent != null) {
                    for (j in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
                        if (ent.GetRenderEntity() != null && ent.GetRenderEntity().gui[j] != null) {
                            ent.GetRenderEntity().gui[j].SetStateString(key, `val`)
                            ent.GetRenderEntity().gui[j].StateChanged(Game_local.gameLocal.time, true)
                        }
                    }
                    ent.UpdateVisuals()
                }
                i++
            }
        }

        protected fun Event_SetCallback() {
            if (moverState == moverState_t.MOVER_1TO2 || moverState == moverState_t.MOVER_2TO1) {
                move_thread = idThread.CurrentThreadNum()
                idThread.ReturnInt(true)
            } else {
                idThread.ReturnInt(false)
            }
        }

        protected fun Event_ReturnToPos1() {
            MatchActivateTeam(moverState_t.MOVER_2TO1, Game_local.gameLocal.time)
        }

        protected fun Event_Use_BinaryMover(activator: idEventArg<idEntity>) {
            Use_BinaryMover(activator.value)
        }

        protected open fun Event_Reached_BinaryMover() {
            if (moverState == moverState_t.MOVER_1TO2) {
                // reached pos2
                idThread.ObjectMoveDone(move_thread, this)
                move_thread = 0
                if (moveMaster === this) {
                    StartSound("snd_opened", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                }
                SetMoverState(moverState_t.MOVER_POS2, Game_local.gameLocal.time)
                SetGuiStates(guiBinaryMoverStates[moverState_t.MOVER_POS2.ordinal])
                UpdateBuddies(1)
                if (enabled && wait >= 0 && !spawnArgs.GetBool("toggle")) {
                    // return to pos1 after a delay
                    PostEventSec(EV_Mover_ReturnToPos1, wait)
                }

                // fire targets
                ActivateTargets(moveMaster!!.GetActivator())
                SetBlocked(false)
            } else if (moverState == moverState_t.MOVER_2TO1) {
                // reached pos1
                idThread.ObjectMoveDone(move_thread, this)
                move_thread = 0
                SetMoverState(moverState_t.MOVER_POS1, Game_local.gameLocal.time)
                SetGuiStates(guiBinaryMoverStates[moverState_t.MOVER_POS1.ordinal])
                UpdateBuddies(0)

                // close areaportals
                if (moveMaster === this) {
                    ProcessEvent(EV_Mover_ClosePortal)
                }
                if (enabled && wait >= 0 && spawnArgs.GetBool("continuous")) {
                    PostEventSec(Entity.EV_Activate, wait, this)
                }
                SetBlocked(false)
            } else {
                idGameLocal.Error("Event_Reached_BinaryMover: bad moverState")
            }
        }

        protected fun Event_MatchActivateTeam(newstate: idEventArg<moverState_t>, time: idEventArg<Int>) {
            MatchActivateTeam(newstate.value, time.value)
        }

        /*
         ================
         idMover_Binary::Event_Enable

         Set all entities in a mover team to be enabled
         ================
         */
        protected fun Event_Enable() {
            var slave: idMover_Binary?
            slave = moveMaster
            while (slave != null) {
                slave.Enable(true) //TODO: this is false in the original code.
                slave = slave.activateChain
            }
        }

        /*
         ================
         idMover_Binary::Event_Disable

         Set all entities in a mover team to be disabled
         ================
         */
        protected fun Event_Disable() {
            var slave: idMover_Binary?
            slave = moveMaster
            while (slave != null) {
                slave.Enable(false)
                slave = slave.activateChain
            }
        }

        /*
         ================
         idMover_Binary::Event_OpenPortal

         Sets the portal associtated with this mover to be open
         ================
         */
        protected open fun Event_OpenPortal() {
            var slave: idMover_Binary?
            slave = moveMaster
            while (slave != null) {
                if (slave.areaPortal != 0) {
                    slave.SetPortalState(true)
                }
                slave = slave.activateChain
            }
        }

        /*
         ================
         idMover_Binary::Event_ClosePortal

         Sets the portal associtated with this mover to be closed
         ================
         */
        protected open fun Event_ClosePortal() {
            var slave: idMover_Binary?
            slave = moveMaster
            while (slave != null) {
                if (!slave.IsHidden()) {
                    if (slave.areaPortal != 0) {
                        slave.SetPortalState(false)
                    }
                }
                slave = slave.activateChain
            }
        }

        protected fun Event_FindGuiTargets() {
            FindGuiTargets()
        }

        protected fun Event_InitGuiTargets() {
            if (guiTargets.size != 0) {
                SetGuiState("movestate", guiBinaryMoverStates[moverState_t.MOVER_POS1.ordinal])
            }
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        init {
            pos1 = idVec3()
            pos2 = idVec3()
            moverState = moverState_t.MOVER_POS1
            moveMaster = null
            activateChain = null
            soundPos1 = 0
            sound1to2 = 0
            sound2to1 = 0
            soundPos2 = 0
            soundLoop = 0
            wait = 0.0f
            damage = 0.0f
            duration = 0
            accelTime = 0
            decelTime = 0
            activatedBy = idEntityPtr(this)
            stateStartTime = 0
            team = idStr()
            enabled = false
            move_thread = 0
            updateStatus = 0
            buddies = ArrayList()
            physicsObj = idPhysics_Parametric()
            areaPortal = 0
            blocked = false
            fl.networkSync = true
            guiTargets = ArrayList()
        }
    }

    /*
     ===============================================================================

     idDoor

     A use can be triggered either by a touch function, by being shot, or by being
     targeted by another entity.

     ===============================================================================
     */
    class idDoor : idMover_Binary() {
        companion object {
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            // ~idDoor( void );
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMover_Binary.getEventCallBacks())
                eventCallbacks[EV_TeamBlocked] =
                    eventCallback_t2<idDoor> { obj: Any?, blockedEntity: idEventArg<*>?, blockingEntity: idEventArg<*>? ->
                        idDoor::Event_TeamBlocked
                    }
                eventCallbacks[EV_PartBlocked] =
                    eventCallback_t1<idDoor> { obj: Any?, blockingEntity: idEventArg<*>? ->
                        idDoor::Event_PartBlocked
                    }
                eventCallbacks[Entity.EV_Touch] =
                    eventCallback_t2<idDoor> { obj: Any?, _other: idEventArg<*>?, _trace: idEventArg<*>? -> idDoor::Event_Touch }
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idDoor> { obj: Any?, activator: idEventArg<*>? -> idDoor::Event_Activate }
                eventCallbacks[EV_Door_StartOpen] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_StartOpen }
                eventCallbacks[EV_Door_SpawnDoorTrigger] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_SpawnDoorTrigger }
                eventCallbacks[EV_Door_SpawnSoundTrigger] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_SpawnSoundTrigger }
                eventCallbacks[EV_Door_Open] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_Open }
                eventCallbacks[EV_Door_Close] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_Close }
                eventCallbacks[EV_Door_Lock] =
                    eventCallback_t1<idDoor> { obj: Any?, f: idEventArg<*>? -> idDoor::Event_Lock }
                eventCallbacks[EV_Door_IsOpen] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_IsOpen }
                eventCallbacks[EV_Door_IsLocked] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_Locked }
                eventCallbacks[EV_ReachedPos] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_Reached_BinaryMover }
                eventCallbacks[Player.EV_SpectatorTouch] =
                    eventCallback_t2<idDoor> { obj: Any?, _other: idEventArg<*>?, trace: idEventArg<*>? ->
                        idDoor::Event_SpectatorTouch
                    }
                eventCallbacks[EV_Mover_OpenPortal] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_OpenPortal }
                eventCallbacks[EV_Mover_ClosePortal] =
                    eventCallback_t0<idDoor> { obj: Any? -> idDoor::Event_ClosePortal }
            }
        }

        private var aas_area_closed = false
        private val buddyStr: idStr
        private var companionDoor: idDoor?
        private var crusher = false
        private var localTriggerAxis: idMat3
        private val localTriggerOrigin: idVec3
        private var nextSndTriggerTime: Int
        private var noTouch = false
        private var normalAxisIndex // door faces X or Y for spectator teleports
                : Int
        private var removeItem: Int
        private val requires: idStr
        private var sndTrigger: idClipModel?
        private val syncLock: idStr
        private var trigger: idClipModel?
        private var triggersize = 1.0f
        override fun Spawn() {
            super.Spawn()
            val abs_movedir = idVec3()
            val distance: Float
            val size = idVec3()
            val moveDir = idVec3()
            val dir = CFloat()
            val lip = CFloat()
            val time = CFloat()
            val speed = CFloat()
            val start_open = CBool()

            // get the direction to move
            if (!spawnArgs.GetFloat("movedir", "0", dir)) {
                // no movedir, so angle defines movement direction and not orientation,
                // a la oldschool Quake
                SetAngles(Angles.getAng_zero())
                spawnArgs.GetFloat("angle", "0", dir)
            }
            GetMovedir(dir._val, moveDir)

            // default speed of 400
            spawnArgs.GetFloat("speed", "400", speed)

            // default wait of 2 seconds
            wait = spawnArgs.GetFloat("wait", "3")

            // default lip of 8 units
            spawnArgs.GetFloat("lip", "8", lip)

            // by default no damage
            damage = spawnArgs.GetFloat("damage", "0")

            // trigger size
            triggersize = spawnArgs.GetFloat("triggersize", "120")
            crusher = spawnArgs.GetBool("crusher", "0")
            spawnArgs.GetBool("start_open", "0", start_open)
            noTouch = spawnArgs.GetBool("no_touch", "0")

            // expects syncLock to be a door that must be closed before this door will open
            spawnArgs.GetString("syncLock", "", syncLock)
            spawnArgs.GetString("buddy", "", buddyStr)
            spawnArgs.GetString("requires", "", requires)
            removeItem = spawnArgs.GetInt("removeItem", "0")

            // ever separate piece of a door is considered solid when other team mates push entities
            fl.solidForTeam = true

            // first position at start
            pos1.set(GetPhysics().GetOrigin())

            // calculate second position
            abs_movedir[0] = abs(moveDir[0])
            abs_movedir[1] = abs(moveDir[1])
            abs_movedir[2] = abs(moveDir[2])
            size.set(GetPhysics().GetAbsBounds()[1].minus(GetPhysics().GetAbsBounds()[0]))
            distance = abs_movedir.times(size) - lip._val
            pos2.set(pos1.plus(moveDir.times(distance)))

            // if "start_open", reverse position 1 and 2
            if (start_open._val) {
                // post it after EV_SpawnBind
                PostEventMS(EV_Door_StartOpen, 1)
            }
            if (spawnArgs.GetFloat("time", "1", time)) {
                InitTime(pos1, pos2, time._val, 0f, 0f)
            } else {
                InitSpeed(pos1, pos2, speed._val, 0f, 0f)
            }
            if (moveMaster === this) {
                if (health != 0) {
                    fl.takedamage = true
                }
                if (noTouch || health != 0) {
                    // non touch/shoot doors
                    PostEventMS(EV_Mover_MatchTeam, 0, moverState, Game_local.gameLocal.time)
                    val sndtemp = spawnArgs.GetString("snd_locked")
                    if (spawnArgs.GetInt("locked") != 0 && sndtemp != null && !sndtemp.isEmpty()) {
                        PostEventMS(EV_Door_SpawnSoundTrigger, 0)
                    }
                } else {
                    // spawn trigger
                    PostEventMS(EV_Door_SpawnDoorTrigger, 0)
                }
            }

            // see if we are on an areaportal
            areaPortal = Game_local.gameRenderWorld.FindPortal(GetPhysics().GetAbsBounds())
            if (!start_open._val) {
                // start closed
                ProcessEvent(EV_Mover_ClosePortal)
            }
            val locked = spawnArgs.GetInt("locked")
            if (locked != 0) {
                // make sure all members of the team get locked
                PostEventMS(EV_Door_Lock, 0f, locked)
            }
            if (spawnArgs.GetBool("continuous")) {
                PostEventSec(Entity.EV_Activate, spawnArgs.GetFloat("delay"), this)
            }

            // sounds have a habit of stuttering when portals close, so make them unoccluded
            refSound.parms.soundShaderFlags = refSound.parms.soundShaderFlags or Sound.SSF_NO_OCCLUSION
            companionDoor = null
            enabled = true
            blocked = false
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(triggersize)
            savefile.WriteBool(crusher)
            savefile.WriteBool(noTouch)
            savefile.WriteBool(aas_area_closed)
            savefile.WriteString(buddyStr)
            savefile.WriteInt(nextSndTriggerTime)
            savefile.WriteVec3(localTriggerOrigin)
            savefile.WriteMat3(localTriggerAxis)
            savefile.WriteString(requires)
            savefile.WriteInt(removeItem)
            savefile.WriteString(syncLock)
            savefile.WriteInt(normalAxisIndex)
            savefile.WriteClipModel(trigger)
            savefile.WriteClipModel(sndTrigger)
            savefile.WriteObject(companionDoor!!)
        }

        override fun Restore(savefile: idRestoreGame) {
            triggersize = savefile.ReadFloat()
            crusher = savefile.ReadBool()
            noTouch = savefile.ReadBool()
            aas_area_closed = savefile.ReadBool()
            SetAASAreaState(aas_area_closed)
            savefile.ReadString(buddyStr)
            nextSndTriggerTime = savefile.ReadInt()
            savefile.ReadVec3(localTriggerOrigin)
            savefile.ReadMat3(localTriggerAxis)
            savefile.ReadString(requires)
            removeItem = savefile.ReadInt()
            savefile.ReadString(syncLock)
            normalAxisIndex = savefile.ReadInt()
            savefile.ReadClipModel(trigger!!)
            savefile.ReadClipModel(sndTrigger!!)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/companionDoor)
        }

        override fun Think() {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            super.Think()
            if (thinkFlags and Entity.TH_PHYSICS != 0) {
                // update trigger position
                if (GetMasterPosition(masterOrigin, masterAxis)) {
                    if (trigger != null) {
                        trigger!!.Link(
                            Game_local.gameLocal.clip,
                            this,
                            0,
                            masterOrigin.plus(localTriggerOrigin.times(masterAxis)),
                            localTriggerAxis.times(masterAxis)
                        )
                    }
                    if (sndTrigger != null) {
                        sndTrigger!!.Link(
                            Game_local.gameLocal.clip,
                            this,
                            0,
                            masterOrigin.plus(localTriggerOrigin.times(masterAxis)),
                            localTriggerAxis.times(masterAxis)
                        )
                    }
                }
            }
        }

        override fun PostBind() {
            super.PostBind()
            GetLocalTriggerPosition(if (trigger != null) trigger else sndTrigger)
        }

        override fun Hide() {
            var slave: idMover_Binary?
            val master: idMover_Binary?
            var slaveDoor: idDoor?
            var companion: idDoor?
            master = GetMoveMaster()
            if (this != master) {
                master!!.Hide()
            } else {
                slave = this
                while (slave != null) {
                    if (slave is idDoor) {
                        slaveDoor = slave
                        companion = slaveDoor.companionDoor
                        if (companion != null && companion != master && companion.GetMoveMaster() != master) {
                            companion.Hide()
                        }
                        if (slaveDoor.trigger != null) {
                            slaveDoor.trigger!!.Disable()
                        }
                        if (slaveDoor.sndTrigger != null) {
                            slaveDoor.sndTrigger!!.Disable()
                        }
                        if (slaveDoor.areaPortal != 0) {
                            slaveDoor.SetPortalState(true)
                        }
                        slaveDoor.SetAASAreaState(false)
                    }
                    slave.GetPhysics().GetClipModel()!!.Disable()
                    slave.Hide()
                    slave = slave.GetActivateChain()
                }
            }
        }

        override fun Show() {
            var slave: idMover_Binary?
            val master: idMover_Binary?
            var slaveDoor: idDoor?
            var companion: idDoor?
            master = GetMoveMaster()
            if (this != master) {
                master!!.Show()
            } else {
                slave = this
                while (slave != null) {
                    if (slave is idDoor) {
                        slaveDoor = slave
                        companion = slaveDoor.companionDoor
                        if (companion != null && companion != master && companion.GetMoveMaster() != master) {
                            companion.Show()
                        }
                        if (slaveDoor.trigger != null) {
                            slaveDoor.trigger!!.Enable()
                        }
                        if (slaveDoor.sndTrigger != null) {
                            slaveDoor.sndTrigger!!.Enable()
                        }
                        if (slaveDoor.areaPortal != 0 && slaveDoor.moverState == moverState_t.MOVER_POS1) {
                            slaveDoor.SetPortalState(false)
                        }
                        slaveDoor.SetAASAreaState(IsLocked() != 0 || IsNoTouch())
                    }
                    slave.GetPhysics().GetClipModel()!!.Enable()
                    slave.Show()
                    slave = slave.GetActivateChain()
                }
            }
        }

        fun IsOpen(): Boolean {
            return moverState != moverState_t.MOVER_POS1
        }

        fun IsNoTouch(): Boolean {
            return noTouch
        }

        fun IsLocked(): Int {
            return spawnArgs.GetInt("locked")
        }

        fun Lock(f: Int) {
            var other: idMover_Binary?

            // lock all the doors on the team
            other = moveMaster
            while (other != null) {
                if (other is idDoor) {
                    val door = other as idDoor
                    if (other == moveMaster) {
                        if (door.sndTrigger == null) {
                            // in this case the sound trigger never got spawned
                            val sndtemp = door.spawnArgs.GetString("snd_locked")
                            if (sndtemp != null && !sndtemp.isEmpty()) {
                                door.PostEventMS(EV_Door_SpawnSoundTrigger, 0)
                            }
                        }
                        if (0 == f && door.spawnArgs.GetInt("locked") != 0) {
                            door.StartSound("snd_unlocked", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                        }
                    }
                    door.spawnArgs.SetInt("locked", f)
                    if (f == 0 || !IsHidden() && door.moverState == moverState_t.MOVER_POS1) {
                        door.SetAASAreaState(f != 0)
                    }
                }
                other = other.GetActivateChain()
            }
            if (f != 0) {
                Close()
            }
        }

        fun Use(other: idEntity?, activator: idEntity) {
            if (Game_local.gameLocal.RequirementMet(activator, requires, removeItem)) {
                if (syncLock.Length() != 0) {
                    val sync = Game_local.gameLocal.FindEntity(syncLock)
                    if (sync != null && sync is idDoor) {
                        if (sync.IsOpen()) {
                            return
                        }
                    }
                }
                ActivateTargets(activator)
                Use_BinaryMover(activator)
            }
        }

        fun Close() {
            GotoPosition1()
        }

        fun Open() {
            GotoPosition2()
        }

        fun SetCompanion(door: idDoor?) {
            companionDoor = door
        }

        private fun SetAASAreaState(closed: Boolean) {
            aas_area_closed = closed
            Game_local.gameLocal.SetAASAreaState(
                physicsObj.GetAbsBounds(),
                AASFile.AREACONTENTS_CLUSTERPORTAL or AASFile.AREACONTENTS_OBSTACLE,
                closed
            )
        }

        private fun GetLocalTriggerPosition(trigger: idClipModel?) {
            val origin = idVec3()
            val axis = idMat3()
            if (null == trigger) {
                return
            }
            GetMasterPosition(origin, axis)
            localTriggerOrigin.set(trigger.GetOrigin().minus(origin).times(axis.Transpose()))
            localTriggerAxis = trigger.GetAxis().times(axis.Transpose())
        }

        /*
         ======================
         idDoor::CalcTriggerBounds

         Calcs bounds for a trigger.
         ======================
         */
        private fun CalcTriggerBounds(size: Float, bounds: idBounds) {
            var other: idMover_Binary?
            var i: Int
            var best: Int

            // find the bounds of everything on the team
            bounds.set(GetPhysics().GetAbsBounds())
            fl.takedamage = true
            other = activateChain
            while (other != null) {
                if (other is idDoor) {
                    // find the bounds of everything on the team
                    bounds.AddBounds(other.GetPhysics().GetAbsBounds())

                    // set all of the slaves as shootable
                    other.fl.takedamage = true
                }
                other = other.GetActivateChain()
            }

            // find the thinnest axis, which will be the one we expand
            best = 0
            i = 1
            while (i < 3) {
                if (bounds[1, i] - bounds[0, i] < bounds[1, best] - bounds[0, best]) {
                    best = i
                }
                i++
            }
            normalAxisIndex = best
            bounds[0].minusAssign(best, size)
            bounds[1].plusAssign(best, size)
            bounds.minusAssign(GetPhysics().GetOrigin())
        }

        override fun Event_Reached_BinaryMover() {
            if (moverState == moverState_t.MOVER_2TO1) {
                SetBlocked(false)
                var kv = spawnArgs.MatchPrefix("triggerClosed")
                while (kv != null) {
                    val ent = Game_local.gameLocal.FindEntity(kv.GetValue().toString())
                    ent?.PostEventMS(Entity.EV_Activate, 0f, moveMaster!!.GetActivator())
                    kv = spawnArgs.MatchPrefix("triggerClosed", kv)
                }
            } else if (moverState == moverState_t.MOVER_1TO2) {
                var kv = spawnArgs.MatchPrefix("triggerOpened")
                while (kv != null) {
                    val ent = Game_local.gameLocal.FindEntity(kv.GetValue().toString())
                    ent?.PostEventMS(Entity.EV_Activate, 0f, moveMaster!!.GetActivator())
                    kv = spawnArgs.MatchPrefix("triggerOpened", kv)
                }
            }
            super.Event_Reached_BinaryMover()
        }

        private fun Event_TeamBlocked(blockedEntity: idEventArg<idEntity>, blockingEntity: idEventArg<idEntity>) {
            SetBlocked(true)
            if (crusher) {
                return  // crushers don't reverse
            }

            // reverse direction
            Use_BinaryMover(moveMaster!!.GetActivator())
            if (companionDoor != null) {
                companionDoor!!.ProcessEvent(EV_TeamBlocked, blockedEntity.value, blockingEntity.value)
            }
        }

        private fun Event_PartBlocked(blockingEntity: idEventArg<idEntity>) {
            if (damage > 0.0f) {
                blockingEntity.value.Damage(
                    this,
                    this,
                    Vector.getVec3_origin(),
                    "damage_moverCrush",
                    damage,
                    Model.INVALID_JOINT
                )
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity>, _trace: idEventArg<trace_s>) {
            val other = _other.value
            val trace = _trace.value
            //            idVec3 contact, translate;
//            idVec3 planeaxis1, planeaxis2, normal;
//            idBounds bounds;
            if (!enabled) {
                return
            }
            if (trigger != null && trace.c.id == trigger!!.GetId()) {
                if (!IsNoTouch() && 0 == IsLocked() && GetMoverState() != moverState_t.MOVER_1TO2) {
                    Use(this, other)
                }
            } else if (sndTrigger != null && trace.c.id == sndTrigger!!.GetId()) {
                if (other != null && other is idPlayer && IsLocked() != 0 && Game_local.gameLocal.time > nextSndTriggerTime) {
                    StartSound("snd_locked", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false)
                    nextSndTriggerTime = Game_local.gameLocal.time + 10000
                }
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val old_lock: Int
            if (spawnArgs.GetInt("locked") != 0) {
                if (TempDump.NOT(trigger)) {
                    PostEventMS(EV_Door_SpawnDoorTrigger, 0)
                }
                if (buddyStr.Length() != 0) {
                    val buddy = Game_local.gameLocal.FindEntity(buddyStr)
                    if (buddy != null) {
                        buddy.SetShaderParm(RenderWorld.SHADERPARM_MODE, 1f)
                        buddy.UpdateVisuals()
                    }
                }
                old_lock = spawnArgs.GetInt("locked")
                Lock(0)
                if (old_lock == 2) {
                    return
                }
            }
            if (syncLock.Length() != 0) {
                val sync = Game_local.gameLocal.FindEntity(syncLock)
                if (sync != null && sync is idDoor) {
                    if (sync.IsOpen()) {
                        return
                    }
                }
            }
            ActivateTargets(activator.value)
            renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] = 1f
            UpdateVisuals()
            Use_BinaryMover(activator.value)
        }

        /*
         ======================
         idDoor::Event_StartOpen

         if "start_open", reverse position 1 and 2
         ======================
         */
        private fun Event_StartOpen() {
            val time = CFloat()
            val speed = CFloat()

            // if "start_open", reverse position 1 and 2
            pos1.set(pos2)
            pos2.set(GetPhysics().GetOrigin())
            spawnArgs.GetFloat("speed", "400", speed)
            if (spawnArgs.GetFloat("time", "1", time)) {
                InitTime(pos1, pos2, time._val, 0f, 0f)
            } else {
                InitSpeed(pos1, pos2, speed._val, 0f, 0f)
            }
        }

        /*
         ======================
         idDoor::Event_SpawnDoorTrigger

         All of the parts of a door have been spawned, so create
         a trigger that encloses all of them.
         ======================
         */
        private fun Event_SpawnDoorTrigger() {
            val bounds = idBounds()
            var other: idMover_Binary?
            var toggle: Boolean
            if (trigger != null) {
                // already have a trigger, so don't spawn a new one.
                return
            }

            // check if any of the doors are marked as toggled
            toggle = false
            other = moveMaster
            while (other != null) {
                if (other is idDoor && other.spawnArgs.GetBool("toggle")) {
                    toggle = true
                    break
                }
                other = other.GetActivateChain()
            }
            if (toggle) {
                // mark them all as toggled
                other = moveMaster
                while (other != null) {
                    (other as? idDoor)?.spawnArgs?.Set("toggle", "1")
                    other = other.GetActivateChain()
                }
                // don't spawn trigger
                return
            }
            val sndtemp = spawnArgs.GetString("snd_locked")
            if (spawnArgs.GetInt("locked") != 0 && sndtemp != null && !sndtemp.isEmpty()) {
                PostEventMS(EV_Door_SpawnSoundTrigger, 0)
            }
            CalcTriggerBounds(triggersize, bounds)

            // create a trigger clip model
            trigger = idClipModel(idTraceModel(bounds))
            trigger!!.Link(
                Game_local.gameLocal.clip,
                this,
                255,
                GetPhysics().GetOrigin(),
                idMat3.getMat3_identity()
            )
            trigger!!.SetContents(Material.CONTENTS_TRIGGER)
            GetLocalTriggerPosition(trigger)
            MatchActivateTeam(moverState, Game_local.gameLocal.time)
        }

        /*
         ======================
         idDoor::Event_SpawnSoundTrigger

         Spawn a sound trigger to activate locked sound if it exists.
         ======================
         */
        private fun Event_SpawnSoundTrigger() {
            val bounds = idBounds()
            if (sndTrigger != null) {
                return
            }
            CalcTriggerBounds(triggersize * 0.5f, bounds)

            // create a trigger clip model
            sndTrigger = idClipModel(idTraceModel(bounds))
            sndTrigger!!.Link(
                Game_local.gameLocal.clip,
                this,
                254,
                GetPhysics().GetOrigin(),
                idMat3.getMat3_identity()
            )
            sndTrigger!!.SetContents(Material.CONTENTS_TRIGGER)
            GetLocalTriggerPosition(sndTrigger)
        }

        private fun Event_Close() {
            Close()
        }

        private fun Event_Open() {
            Open()
        }

        private fun Event_Lock(f: idEventArg<Int>) {
            Lock(f.value)
        }

        private fun Event_IsOpen() {
            val state: Int
            state = if (IsOpen()) 1 else 0
            idThread.ReturnFloat(state.toFloat())
        }

        private fun Event_Locked() {
            idThread.ReturnFloat(spawnArgs.GetInt("locked").toFloat())
        }

        private fun Event_SpectatorTouch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            val other = _other.value
            val contact = idVec3()
            val translate = idVec3()
            val normal = idVec3()
            val bounds: idBounds
            val p: idPlayer?
            assert(other != null && other is idPlayer && other.spectating)
            p = other as idPlayer
            // avoid flicker when stopping right at clip box boundaries
            if (p.lastSpectateTeleport > Game_local.gameLocal.time - 1000) {
                return
            }
            if (trigger != null && !IsOpen()) {
                // teleport to the other side, center to the middle of the trigger brush
                bounds = trigger!!.GetAbsBounds()
                contact.set(trace.value.endpos.minus(bounds.GetCenter()))
                translate.set(bounds.GetCenter())
                normal.Zero()
                normal[normalAxisIndex] = 1.0f
                if (normal.times(contact) > 0) {
                    translate.plusAssign(
                        normalAxisIndex,
                        (bounds[0, normalAxisIndex] - translate[normalAxisIndex]) * 0.5f
                    )
                } else {
                    translate.plusAssign(
                        normalAxisIndex,
                        (bounds[1, normalAxisIndex] - translate[normalAxisIndex]) * 0.5f
                    )
                }
                p.SetOrigin(translate)
                p.lastSpectateTeleport = Game_local.gameLocal.time
            }
        }

        /*
         ================
         idDoor::Event_OpenPortal

         Sets the portal associtated with this door to be open
         ================
         */
        override fun Event_OpenPortal() {
            var slave: idMover_Binary?
            var slaveDoor: idDoor?
            slave = this
            while (slave != null) {
                if (slave is idDoor) {
                    slaveDoor = slave
                    if (slaveDoor.areaPortal != 0) {
                        slaveDoor.SetPortalState(true)
                    }
                    slaveDoor.SetAASAreaState(false)
                }
                slave = slave.GetActivateChain()
            }
        }

        /*
         ================
         idDoor::Event_ClosePortal

         Sets the portal associtated with this door to be closed
         ================
         */
        override fun Event_ClosePortal() {
            var slave: idMover_Binary?
            var slaveDoor: idDoor?
            slave = this
            while (slave != null) {
                if (!slave.IsHidden()) {
                    if (slave is idDoor) {
                        slaveDoor = slave
                        if (slaveDoor.areaPortal != 0) {
                            slaveDoor.SetPortalState(false)
                        }
                        slaveDoor.SetAASAreaState(IsLocked() != 0 || IsNoTouch())
                    }
                }
                slave = slave.GetActivateChain()
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        // public:
        // CLASS_PROTOTYPE( idDoor );
        init {
            buddyStr = idStr()
            trigger = null
            sndTrigger = null
            nextSndTriggerTime = 0
            localTriggerOrigin = idVec3()
            localTriggerAxis = idMat3.getMat3_identity()
            requires = idStr()
            removeItem = 0
            syncLock = idStr()
            companionDoor = null
            normalAxisIndex = 0
        }
    }

    /*
     ===============================================================================

     idPlat

     ===============================================================================
     */
    class idPlat : idMover_Binary() {
        companion object {
            // CLASS_PROTOTYPE( idPlat );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            // ~idPlat( void );
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMover_Binary.getEventCallBacks())
                eventCallbacks[Entity.EV_Touch] =
                    eventCallback_t2<idPlat> { obj: Any?, _other: idEventArg<*>?, trace: idEventArg<*>? -> idPlat::Event_Touch }
                eventCallbacks[EV_TeamBlocked] =
                    eventCallback_t2<idPlat> { obj: Any?, blockedEntity: idEventArg<*>?, blockingEntity: idEventArg<*>? ->
                        idPlat::Event_TeamBlocked
                    }
                eventCallbacks[EV_PartBlocked] =
                    eventCallback_t1<idPlat> { obj: Any?, blockingEntity: idEventArg<*>? ->
                        idPlat::Event_PartBlocked
                    }
            }
        }

        private var localTriggerAxis: idMat3
        private val localTriggerOrigin: idVec3
        private var trigger: idClipModel? = null
        override fun Spawn() {
            super.Spawn()
            val lip = CFloat()
            val height = CFloat()
            val time = CFloat()
            val speed = CFloat()
            val accel = CFloat()
            val decel = CFloat()
            val noTouch = CBool(false)
            spawnArgs.GetFloat("speed", "100", speed)
            damage = spawnArgs.GetFloat("damage", "0")
            wait = spawnArgs.GetFloat("wait", "1")
            spawnArgs.GetFloat("lip", "8", lip)
            spawnArgs.GetFloat("accel_time", "0.25", accel)
            spawnArgs.GetFloat("decel_time", "0.25", decel)

            // create second position
            if (!spawnArgs.GetFloat("height", "0", height)) {
                height._val = (GetPhysics().GetBounds()[1, 2] - GetPhysics().GetBounds()[0, 2] - lip._val)
            }
            spawnArgs.GetBool("no_touch", "0", noTouch)

            // pos1 is the rest (bottom) position, pos2 is the top
            pos2.set(GetPhysics().GetOrigin())
            pos1.set(pos2)
            pos1.minusAssign(2, height._val)
            if (spawnArgs.GetFloat("time", "1", time)) {
                InitTime(pos1, pos2, time._val, accel._val, decel._val)
            } else {
                InitSpeed(pos1, pos2, speed._val, accel._val, decel._val)
            }
            SetMoverState(moverState_t.MOVER_POS1, Game_local.gameLocal.time)
            UpdateVisuals()

            // spawn the trigger if one hasn't been custom made
            if (!noTouch._val) {
                // spawn trigger
                SpawnPlatTrigger(pos1)
            }
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteClipModel(trigger)
            savefile.WriteVec3(localTriggerOrigin)
            savefile.WriteMat3(localTriggerAxis)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadClipModel(trigger!!)
            savefile.ReadVec3(localTriggerOrigin)
            savefile.ReadMat3(localTriggerAxis)
        }

        override fun Think() {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            super.Think()
            if (thinkFlags and Entity.TH_PHYSICS != 0) {
                // update trigger position
                if (GetMasterPosition(masterOrigin, masterAxis)) {
                    if (trigger != null) {
                        trigger!!.Link(
                            Game_local.gameLocal.clip,
                            this,
                            0,
                            masterOrigin.plus(localTriggerOrigin.times(masterAxis)),
                            localTriggerAxis.times(masterAxis)
                        )
                    }
                }
            }
        }

        override fun PostBind() {
            super.PostBind()
            GetLocalTriggerPosition(trigger)
        }

        private fun GetLocalTriggerPosition(trigger: idClipModel?) {
            val origin = idVec3()
            val axis = idMat3()
            if (null == trigger) {
                return
            }
            GetMasterPosition(origin, axis)
            localTriggerOrigin.set(trigger.GetOrigin().minus(origin).times(axis.Transpose()))
            localTriggerAxis = trigger.GetAxis().times(axis.Transpose())
        }

        private fun SpawnPlatTrigger(pos: idVec3) {
            val bounds: idBounds
            val tmin = idVec3()
            val tmax = idVec3()

            // the middle trigger will be a thin trigger just
            // above the starting position
            bounds = GetPhysics().GetBounds()
            tmin[0] = bounds[0, 0] + 33
            tmin[1] = bounds[0, 1] + 33
            tmin[2] = bounds[0, 2]
            tmax[0] = bounds[1, 0] - 33
            tmax[1] = bounds[1, 1] - 33
            tmax[2] = bounds[1, 2] + 8
            if (tmax[0] <= tmin[0]) {
                tmin[0] = (bounds[0, 0] + bounds[1, 0]) * 0.5f
                tmax[0] = tmin[0] + 1
            }
            if (tmax[1] <= tmin[1]) {
                tmin[0] = (bounds[0, 1] + bounds[1, 1]) * 0.5f
                tmax[0] = tmin[1] + 1
            }
            trigger = idClipModel(idTraceModel(idBounds(tmin, tmax)))
            trigger!!.Link(
                Game_local.gameLocal.clip,
                this,
                255,
                GetPhysics().GetOrigin(),
                idMat3.getMat3_identity()
            )
            trigger!!.SetContents(Material.CONTENTS_TRIGGER)
        }

        private fun Event_TeamBlocked(blockedEntity: idEventArg<idEntity>, blockingEntity: idEventArg<idEntity>) {
            // reverse direction
            Use_BinaryMover(activatedBy.GetEntity())
        }

        private fun Event_PartBlocked(blockingEntity: idEventArg<idEntity>) {
            if (damage > 0) {
                blockingEntity.value.Damage(
                    this,
                    this,
                    Vector.getVec3_origin(),
                    "damage_moverCrush",
                    damage,
                    Model.INVALID_JOINT
                )
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity>, trace: idEventArg<trace_s>) {
            val other = _other.value as? idPlayer ?: return
            if (GetMoverState() == moverState_t.MOVER_POS1 && trigger != null && trace.value.c.id == trigger!!.GetId() && other.health > 0) {
                Use_BinaryMover(other)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        init {
            localTriggerAxis = idMat3()
            localTriggerOrigin = idVec3()
            localTriggerOrigin.Zero()
            localTriggerAxis.Identity()
        }
    }

    /*
     ===============================================================================

     Special periodic movers.

     ===============================================================================
     */
    /*
     ===============================================================================

     idMover_Periodic

     ===============================================================================
     */
    open class idMover_Periodic : idEntity() {
        companion object {
            // CLASS_PROTOTYPE( idMover_Periodic );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_TeamBlocked] =
                    eventCallback_t2<idMover_Periodic> { obj: Any?, blockedEntity: idEventArg<*>?,
                                                         blockingEntity: idEventArg<*>? ->
                        idMover_Periodic::Event_TeamBlocked
                    }
                eventCallbacks[EV_PartBlocked] =
                    eventCallback_t1<idMover_Periodic> { obj: Any?, blockingEntity: idEventArg<*>? ->
                        idMover_Periodic::Event_PartBlocked
                    }
            }
        }

        protected var damage: CFloat = CFloat()
        protected var physicsObj: idPhysics_Parametric
        override fun Spawn() {
            super.Spawn()
            spawnArgs.GetFloat("damage", "0", damage)
            if (!spawnArgs.GetBool("solid", "1")) {
                GetPhysics().SetContents(0)
            }
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(damage._val)
            savefile.WriteStaticObject(physicsObj)
        }

        override fun Restore(savefile: idRestoreGame) {
            savefile.ReadFloat(damage)
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
        }

        override fun Think() {
            // if we are completely closed off from the player, don't do anything at all
            if (CheckDormant()) {
                return
            }
            RunPhysics()
            Present()
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta) {
            physicsObj.WriteToSnapshot(msg)
            WriteBindToSnapshot(msg)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta) {
            physicsObj.ReadFromSnapshot(msg)
            ReadBindFromSnapshot(msg)
            if (msg.HasChanged()) {
                UpdateVisuals()
            }
        }

        protected fun Event_TeamBlocked(
            blockedEntity: idEventArg<idEntity>,
            blockingEntity: idEventArg<idEntity>
        ) {
        }

        protected fun Event_PartBlocked(blockingEntity: idEventArg<idEntity>) {
            if (damage._val > 0) {
                blockingEntity.value.Damage(
                    this,
                    this,
                    Vector.getVec3_origin(),
                    "damage_moverCrush",
                    damage._val,
                    Model.INVALID_JOINT
                )
            }
        }

        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        init {
            damage._val = 0f
            physicsObj = idPhysics_Parametric()
            fl.neverDormant = false
        }
    }

    /*
     ===============================================================================

     idRotater

     ===============================================================================
     */
    class idRotater : idMover_Periodic() {
        companion object {
            // CLASS_PROTOTYPE( idRotater );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMover_Periodic.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idRotater> { obj: Any?, activator: idEventArg<*>? -> idRotater::Event_Activate }
            }
        }

        private val activatedBy: idEntityPtr<idEntity>
        override fun Spawn() {
            super.Spawn()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                Game_local.gameLocal.time,
                0,
                GetPhysics().GetOrigin(),
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                Game_local.gameLocal.time,
                0,
                GetPhysics().GetAxis().ToAngles(),
                Angles.getAng_zero(),
                Angles.getAng_zero()
            )
            SetPhysics(physicsObj)
            if (spawnArgs.GetBool("start_on")) {
                ProcessEvent(Entity.EV_Activate, this)
            }
        }

        override fun Save(savefile: idSaveGame) {
            activatedBy.Save(savefile)
        }

        override fun Restore(savefile: idRestoreGame) {
            activatedBy.Restore(savefile)
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            val speed = CFloat()
            val x_axis = CBool(false)
            val y_axis = CBool(false)
            val delta = idAngles()
            activatedBy.oSet(activator.value)
            delta.Zero()
            if (!spawnArgs.GetBool("rotate")) {
                spawnArgs.Set("rotate", "1")
                spawnArgs.GetFloat("speed", "100", speed)
                spawnArgs.GetBool("x_axis", "0", x_axis)
                spawnArgs.GetBool("y_axis", "0", y_axis)

                // set the axis of rotation
                if (x_axis._val) {
                    delta[2] = speed._val
                } else if (y_axis._val) {
                    delta[0] = speed._val
                } else {
                    delta[1] = speed._val
                }
            } else {
                spawnArgs.Set("rotate", "0")
            }
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                Game_local.gameLocal.time,
                0,
                physicsObj.GetAxis().ToAngles(),
                delta,
                Angles.getAng_zero()
            )
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

        //
        //
        init {
            activatedBy = idEntityPtr<idEntity?>().oSet(this)
        }
    }

    /*
     ===============================================================================

     idBobber

     ===============================================================================
     */
    class idBobber  // CLASS_PROTOTYPE( idBobber );
        : idMover_Periodic() {
        override fun Spawn() {
            super.Spawn()
            val speed = CFloat()
            val height = CFloat()
            val phase = CFloat()
            val x_axis = CBool(false)
            val y_axis = CBool(false)
            val delta = idVec3()
            spawnArgs.GetFloat("speed", "4", speed)
            spawnArgs.GetFloat("height", "32", height)
            spawnArgs.GetFloat("phase", "0", phase)
            spawnArgs.GetBool("x_axis", "0", x_axis)
            spawnArgs.GetBool("y_axis", "0", y_axis)

            // set the axis of bobbing
            delta.set(Vector.getVec3_origin())
            if (x_axis._val) {
                delta[0] = height._val
            } else if (y_axis._val) {
                delta[1] = height._val
            } else {
                delta[2] = height._val
            }
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                (phase._val * 1000).toInt(),
                (speed._val * 500).toInt(),
                GetPhysics().GetOrigin(),
                delta.times(2.0f),
                Vector.getVec3_origin()
            )
            SetPhysics(physicsObj)
        }
    }

    /*
     ===============================================================================

     idPendulum

     ===============================================================================
     */
    class idPendulum : idMover_Periodic() {
        // CLASS_PROTOTYPE( idPendulum );
        //        public idPendulum() {//TODO:remove default constructor override
        //        }
        override fun Spawn() {
            super.Spawn()
            val speed = CFloat()
            val freq = CFloat()
            val length = CFloat()
            val phase = CFloat()
            spawnArgs.GetFloat("speed", "30", speed)
            spawnArgs.GetFloat("phase", "0", phase)
            if (spawnArgs.GetFloat("freq", "", freq)) {
                if (freq._val <= 0.0f) {
                    idGameLocal.Error("Invalid frequency on entity '%s'", GetName())
                }
            } else {
                // find pendulum length
                length._val = (abs(GetPhysics().GetBounds()[0, 2]))
                if (length._val < 8) {
                    length._val = (8f)
                }
                freq._val = (1 / idMath.TWO_PI * idMath.Sqrt(SysCvar.g_gravity.GetFloat() / (3 * length._val)))
            }
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                GetPhysics().GetOrigin(),
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            physicsObj.SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_DECELSINE or Extrapolate.EXTRAPOLATION_NOSTOP,
                (phase._val * 1000).toInt(),
                (500 / freq._val).toInt(),
                GetPhysics().GetAxis().ToAngles(),
                idAngles(0f, 0f, speed._val * 2.0f),
                Angles.getAng_zero()
            )
            SetPhysics(physicsObj)
        }
    }

    /*
     ===============================================================================

     idRiser

     ===============================================================================
     */
    class idRiser : idMover_Periodic() {
        companion object {
            // CLASS_PROTOTYPE( idRiser );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idMover_Periodic.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idRiser> { obj: Any?, activator: idEventArg<*>? -> idRiser::Event_Activate }
            }
        }

        //public	idRiser( ){}
        override fun Spawn() {
            super.Spawn()
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(GetPhysics().GetClipModel()!!), 1.0f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetClipMask(Game_local.MASK_SOLID)
            if (!spawnArgs.GetBool("solid", "1")) {
                physicsObj.SetContents(0)
            }
            if (!spawnArgs.GetBool("nopush")) {
                physicsObj.SetPusher(0)
            }
            physicsObj.SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_NONE,
                0,
                0,
                GetPhysics().GetOrigin(),
                Vector.getVec3_origin(),
                Vector.getVec3_origin()
            )
            SetPhysics(physicsObj)
        }

        private fun Event_Activate(activator: idEventArg<idEntity>) {
            if (!IsHidden() && spawnArgs.GetBool("hide")) {
                Hide()
            } else {
                Show()
                val time = CFloat()
                val height = CFloat()
                val delta = idVec3()
                spawnArgs.GetFloat("time", "4", time)
                spawnArgs.GetFloat("height", "32", height)
                delta.set(Vector.getVec3_origin())
                delta[2] = height._val
                physicsObj.SetLinearExtrapolation(
                    Extrapolate.EXTRAPOLATION_LINEAR,
                    Game_local.gameLocal.time,
                    (time._val * 1000).toInt(),
                    physicsObj.GetOrigin(),
                    delta,
                    Vector.getVec3_origin()
                )
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }
    }
}