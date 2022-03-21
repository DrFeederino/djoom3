package neo.Game

import neo.CM.CollisionModel.trace_s
import neo.CM.CollisionModel_local
import neo.Game.*
import neo.Game.Entity.idEntity
import neo.Game.FX.idEntityFx
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Light.idLight
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Player.idPlayer
import neo.Game.Pvs.pvsHandle_t
import neo.Renderer.Material
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.renderView_s
import neo.TempDump
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object SecurityCamera {
    val EV_SecurityCam_AddLight: idEventDef = idEventDef("<addLight>")
    val EV_SecurityCam_Alert: idEventDef = idEventDef("<alert>")
    val EV_SecurityCam_ContinueSweep: idEventDef = idEventDef("<continueSweep>")
    val EV_SecurityCam_Pause: idEventDef = idEventDef("<pause>")

    /*
     ===================================================================================

     Security camera

     ===================================================================================
     */
    val EV_SecurityCam_ReverseSweep: idEventDef = idEventDef("<reverseSweep>")

    class idSecurityCamera : idEntity() {
        companion object {
            private const val ACTIVATED = 3
            private const val ALERT = 2
            private const val LOSINGINTEREST = 1
            private const val SCANNING = 0
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[SecurityCamera.EV_SecurityCam_ReverseSweep] =
                    eventCallback_t0<idSecurityCamera?> { obj: T? -> neo.Game.obj.Event_ReverseSweep() } as eventCallback_t0<idSecurityCamera?>
                eventCallbacks[SecurityCamera.EV_SecurityCam_ContinueSweep] =
                    eventCallback_t0<idSecurityCamera?> { obj: T? -> neo.Game.obj.Event_ContinueSweep() } as eventCallback_t0<idSecurityCamera?>
                eventCallbacks[SecurityCamera.EV_SecurityCam_Pause] =
                    eventCallback_t0<idSecurityCamera?> { obj: T? -> neo.Game.obj.Event_Pause() } as eventCallback_t0<idSecurityCamera?>
                eventCallbacks[SecurityCamera.EV_SecurityCam_Alert] =
                    eventCallback_t0<idSecurityCamera?> { obj: T? -> neo.Game.obj.Event_Alert() } as eventCallback_t0<idSecurityCamera?>
                eventCallbacks[SecurityCamera.EV_SecurityCam_AddLight] =
                    eventCallback_t0<idSecurityCamera?> { obj: T? -> neo.Game.obj.Event_AddLight() } as eventCallback_t0<idSecurityCamera?>
            }
        }

        //
        private val viewOffset: idVec3
        private var alertMode = 0

        // enum { SCANNING, LOSINGINTEREST, ALERT, ACTIVATED };
        private var angle = 0f
        private var flipAxis = false
        private var modelAxis = 0
        private var negativeSweep = false
        private val physicsObj: idPhysics_RigidBody?

        //
        private var pvsArea = 0
        private var scanDist = 0f
        private var scanFov = 0f
        private var scanFovCos = 0f
        private var stopSweeping = 0f
        private var sweepAngle = 0f
        private var sweepEnd = 0f

        //
        private var sweepStart = 0f
        private var sweeping = false
        private val trm: idTraceModel?
        override fun Spawn() {
            super.Spawn()
            val str: idStr
            sweepAngle = spawnArgs.GetFloat("sweepAngle", "90")
            health = spawnArgs.GetInt("health", "100")
            scanFov = spawnArgs.GetFloat("scanFov", "90")
            scanDist = spawnArgs.GetFloat("scanDist", "200")
            flipAxis = spawnArgs.GetBool("flipAxis")
            modelAxis = spawnArgs.GetInt("modelAxis")
            if (modelAxis < 0 || modelAxis > 2) {
                modelAxis = 0
            }
            spawnArgs.GetVector("viewOffset", "0 0 0", viewOffset)
            if (spawnArgs.GetBool("spotLight")) {
                PostEventMS(SecurityCamera.EV_SecurityCam_AddLight, 0)
            }
            negativeSweep = sweepAngle < 0
            sweepAngle = Math.abs(sweepAngle)
            scanFovCos = Math.cos((scanFov * idMath.PI / 360.0f).toDouble()).toFloat()
            angle = GetPhysics().GetAxis().ToAngles().yaw
            StartSweep()
            SetAlertMode(SCANNING)
            BecomeActive(Entity.TH_THINK)
            if (health != 0) {
                fl.takedamage = true
            }
            pvsArea = Game_local.gameLocal.pvs.GetPVSArea(GetPhysics().GetOrigin())
            // if no target specified use ourself
            str = idStr(spawnArgs.GetString("cameraTarget"))
            if (str.Length() == 0) {
                spawnArgs.Set("cameraTarget", spawnArgs.GetString("name"))
            }

            // check if a clip model is set
            spawnArgs.GetString("clipmodel", "", str)
            if (!TempDump.isNotNullOrEmpty(str)) {
                str.set(spawnArgs.GetString("model")) // use the visual model
            }
            if (!CollisionModel_local.collisionModelManager.TrmFromModel(str, trm)) {
                idGameLocal.Companion.Error("idSecurityCamera '%s': cannot load collision model %s", name, str)
                return
            }
            GetPhysics().SetContents(Material.CONTENTS_SOLID)
            GetPhysics().SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_BODY or Material.CONTENTS_CORPSE or Material.CONTENTS_MOVEABLECLIP)
            // setup the physics
            UpdateChangeableSpawnArgs(null)
        }

        override fun Save(savefile: idSaveGame) {
            savefile.WriteFloat(angle)
            savefile.WriteFloat(sweepAngle)
            savefile.WriteInt(modelAxis)
            savefile.WriteBool(flipAxis)
            savefile.WriteFloat(scanDist)
            savefile.WriteFloat(scanFov)
            savefile.WriteFloat(sweepStart)
            savefile.WriteFloat(sweepEnd)
            savefile.WriteBool(negativeSweep)
            savefile.WriteBool(sweeping)
            savefile.WriteInt(alertMode)
            savefile.WriteFloat(stopSweeping)
            savefile.WriteFloat(scanFovCos)
            savefile.WriteVec3(viewOffset)
            savefile.WriteInt(pvsArea)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteTraceModel(trm)
        }

        override fun Restore(savefile: idRestoreGame) {
            angle = savefile.ReadFloat()
            sweepAngle = savefile.ReadFloat()
            modelAxis = savefile.ReadInt()
            flipAxis = savefile.ReadBool()
            scanDist = savefile.ReadFloat()
            scanFov = savefile.ReadFloat()
            sweepStart = savefile.ReadFloat()
            sweepEnd = savefile.ReadFloat()
            negativeSweep = savefile.ReadBool()
            sweeping = savefile.ReadBool()
            alertMode = savefile.ReadInt()
            stopSweeping = savefile.ReadFloat()
            scanFovCos = savefile.ReadFloat()
            savefile.ReadVec3(viewOffset)
            pvsArea = savefile.ReadInt()
            savefile.ReadStaticObject(physicsObj)
            savefile.ReadTraceModel(trm)
        }

        override fun Think() {
            val pct: Float
            val travel: Float
            if (thinkFlags and Entity.TH_THINK != 0) {
                if (SysCvar.g_showEntityInfo.GetBool()) {
                    DrawFov()
                }
                if (health <= 0) {
                    BecomeInactive(Entity.TH_THINK)
                    return
                }
            }

            // run physics
            RunPhysics()
            if (thinkFlags and Entity.TH_THINK != 0) {
                if (CanSeePlayer()) {
                    if (alertMode == SCANNING) {
                        val sightTime: Float
                        SetAlertMode(ALERT)
                        stopSweeping = Game_local.gameLocal.time.toFloat()
                        if (sweeping) {
                            CancelEvents(SecurityCamera.EV_SecurityCam_Pause)
                        } else {
                            CancelEvents(SecurityCamera.EV_SecurityCam_ReverseSweep)
                        }
                        sweeping = false
                        StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
                        StartSound("snd_sight", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
                        sightTime = spawnArgs.GetFloat("sightTime", "5")
                        PostEventSec(SecurityCamera.EV_SecurityCam_Alert, sightTime)
                    }
                } else {
                    if (alertMode == ALERT) {
                        val sightResume: Float
                        SetAlertMode(LOSINGINTEREST)
                        CancelEvents(SecurityCamera.EV_SecurityCam_Alert)
                        sightResume = spawnArgs.GetFloat("sightResume", "1.5")
                        PostEventSec(SecurityCamera.EV_SecurityCam_ContinueSweep, sightResume)
                    }
                    if (sweeping) {
                        val a = GetPhysics().GetAxis().ToAngles()
                        pct = (Game_local.gameLocal.time - sweepStart) / (sweepEnd - sweepStart)
                        travel = pct * sweepAngle
                        if (negativeSweep) {
                            a.yaw = angle + travel
                        } else {
                            a.yaw = angle - travel
                        }
                        SetAngles(a)
                    }
                }
            }
            Present()
        }

        override fun GetRenderView(): renderView_s? {
            val rv = super.GetRenderView()
            rv.fov_x = scanFov
            rv.fov_y = scanFov
            rv.viewaxis = GetAxis().ToAngles().ToMat3()
            rv.vieworg.set(GetPhysics().GetOrigin().oPlus(viewOffset))
            return rv
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            sweeping = false
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            val fx = spawnArgs.GetString("fx_destroyed")
            if (TempDump.isNotNullOrEmpty(fx)) { //fx[0] != '\0' ) {
                idEntityFx.Companion.StartFx(fx, null, null, this, true)
            }
            physicsObj.SetSelf(this)
            physicsObj.SetClipModel(idClipModel(trm), 0.02f)
            physicsObj.SetOrigin(GetPhysics().GetOrigin())
            physicsObj.SetAxis(GetPhysics().GetAxis())
            physicsObj.SetBouncyness(0.2f)
            physicsObj.SetFriction(0.6f, 0.6f, 0.2f)
            physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
            physicsObj.SetContents(Material.CONTENTS_SOLID)
            physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_BODY or Material.CONTENTS_CORPSE or Material.CONTENTS_MOVEABLECLIP)
            SetPhysics(physicsObj)
            physicsObj.DropToFloor()
        }

        override fun Pain(
            inflictor: idEntity?,
            attacker: idEntity?,
            damage: Int,
            dir: idVec3,
            location: Int
        ): Boolean {
            val fx = spawnArgs.GetString("fx_damage")
            if (TempDump.isNotNullOrEmpty(fx)) { //fx[0] != '\0' ) {
                idEntityFx.Companion.StartFx(fx, null, null, this, true)
            }
            return true
        }

        /*
         ================
         idSecurityCamera::Present

         Present is called to allow entities to generate refEntities, lights, etc for the renderer.
         ================
         */
        override fun Present() {
            // don't present to the renderer if the entity hasn't changed
            if (0 == thinkFlags and Entity.TH_UPDATEVISUALS) {
                return
            }
            BecomeInactive(Entity.TH_UPDATEVISUALS)

            // camera target for remote render views
            if (cameraTarget != null) {
                renderEntity.remoteRenderView = cameraTarget.GetRenderView()
            }

            // if set to invisible, skip
            if (null == renderEntity.hModel || IsHidden()) {
                return
            }

            // add to refresh list
            if (modelDefHandle == -1) {
                modelDefHandle = Game_local.gameRenderWorld.AddEntityDef(renderEntity)
                val a = 0
            } else {
                Game_local.gameRenderWorld.UpdateEntityDef(modelDefHandle, renderEntity)
            }
        }

        private fun StartSweep() {
            val speed: Int
            sweeping = true
            sweepStart = Game_local.gameLocal.time.toFloat()
            speed = Math_h.SEC2MS(SweepSpeed()).toInt()
            sweepEnd = sweepStart + speed
            PostEventMS(SecurityCamera.EV_SecurityCam_Pause, speed)
            StartSound("snd_moving", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
        }

        private fun CanSeePlayer(): Boolean {
            var i: Int
            var dist: Float
            var ent: idPlayer
            val tr = trace_s()
            val dir = idVec3()
            val handle: pvsHandle_t?
            handle = Game_local.gameLocal.pvs.SetupCurrentPVS(pvsArea)
            i = 0
            while (i < Game_local.gameLocal.numClients) {
                ent = Game_local.gameLocal.entities[i] as idPlayer
                if (TempDump.NOT(ent) || ent.fl.notarget) {
                    i++
                    continue
                }

                // if there is no way we can see this player
                if (!Game_local.gameLocal.pvs.InCurrentPVS(handle, ent.GetPVSAreas(), ent.GetNumPVSAreas())) {
                    i++
                    continue
                }
                dir.set(ent.GetPhysics().GetOrigin().minus(GetPhysics().GetOrigin()))
                dist = dir.Normalize()
                if (dist > scanDist) {
                    i++
                    continue
                }
                if (dir.times(GetAxis()) < scanFovCos) {
                    i++
                    continue
                }
                val eye = idVec3()
                eye.set(ent.EyeOffset())
                Game_local.gameLocal.clip.TracePoint(
                    tr,
                    GetPhysics().GetOrigin(),
                    ent.GetPhysics().GetOrigin().oPlus(eye),
                    Game_local.MASK_OPAQUE,
                    this
                )
                if (tr.fraction == 1.0f || Game_local.gameLocal.GetTraceEntity(tr) == ent) {
                    Game_local.gameLocal.pvs.FreeCurrentPVS(handle)
                    return true
                }
                i++
            }
            Game_local.gameLocal.pvs.FreeCurrentPVS(handle)
            return false
        }

        private fun SetAlertMode(alert: Int) {
            if (alert >= SCANNING && alert <= ACTIVATED) {
                alertMode = alert
            }
            renderEntity.shaderParms[RenderWorld.SHADERPARM_MODE] = alertMode
            UpdateVisuals()
        }

        private fun DrawFov() {
            var i: Int
            val radius: Float
            var a: Float
            val halfRadius: Float
            val s = CFloat()
            val c = CFloat()
            val right = idVec3()
            val up = idVec3()
            val color = idVec4(1, 0, 0, 1)
            val color2 = idVec4(0, 0, 1, 1)
            val lastPoint = idVec3()
            val point = idVec3()
            val lastHalfPoint = idVec3()
            val halfPoint = idVec3()
            val center = idVec3()
            val dir = idVec3(GetAxis())
            dir.NormalVectors(right, up)
            radius = Math.tan((scanFov * idMath.PI / 360.0f).toDouble()).toFloat()
            halfRadius = radius * 0.5f
            lastPoint.set(dir.oPlus(up.times(radius)))
            lastPoint.Normalize()
            lastPoint.set(GetPhysics().GetOrigin().oPlus(lastPoint.times(scanDist)))
            lastHalfPoint.set(dir.oPlus(up.times(halfRadius)))
            lastHalfPoint.Normalize()
            lastHalfPoint.set(GetPhysics().GetOrigin().oPlus(lastHalfPoint.times(scanDist)))
            center.set(GetPhysics().GetOrigin().oPlus(dir.times(scanDist)))
            i = 1
            while (i < 12) {
                a = idMath.TWO_PI * i / 12.0f
                idMath.SinCos(a, s, c)
                point.set(dir.oPlus(right.times(s._val * radius).oPlus(up.times(c._val * radius))))
                point.Normalize()
                point.set(GetPhysics().GetOrigin().oPlus(point.times(scanDist)))
                Game_local.gameRenderWorld.DebugLine(color, lastPoint, point)
                Game_local.gameRenderWorld.DebugLine(color, GetPhysics().GetOrigin(), point)
                lastPoint.set(point)
                halfPoint.set(
                    dir.oPlus(
                        right.times(s._val * halfRadius).oPlus(up.times(c._val * halfRadius))
                    )
                )
                halfPoint.Normalize()
                halfPoint.set(GetPhysics().GetOrigin().oPlus(halfPoint.times(scanDist)))
                Game_local.gameRenderWorld.DebugLine(color2, point, halfPoint)
                Game_local.gameRenderWorld.DebugLine(color2, lastHalfPoint, halfPoint)
                lastHalfPoint.set(halfPoint)
                Game_local.gameRenderWorld.DebugLine(color2, halfPoint, center)
                i++
            }
        }

        private fun GetAxis(): idVec3 {
            return if (flipAxis) GetPhysics().GetAxis().get(modelAxis).oNegative() else GetPhysics().GetAxis()
                .get(modelAxis)
        }

        private fun SweepSpeed(): Float {
            return spawnArgs.GetFloat("sweepSpeed", "5")
        }

        private fun Event_ReverseSweep() {
            angle = GetPhysics().GetAxis().ToAngles().yaw
            negativeSweep = !negativeSweep
            StartSweep()
        }

        private fun Event_ContinueSweep() {
            val pct = (stopSweeping - sweepStart) / (sweepEnd - sweepStart)
            val f = Game_local.gameLocal.time - (sweepEnd - sweepStart) * pct
            val speed: Int
            sweepStart = f
            speed = Math_h.MS2SEC(SweepSpeed()).toInt()
            sweepEnd = sweepStart + speed
            PostEventMS(SecurityCamera.EV_SecurityCam_Pause, (speed * (1.0f - pct)).toInt())
            StartSound("snd_moving", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
            SetAlertMode(SCANNING)
            sweeping = true
        }

        private fun Event_Pause() {
            val sweepWait: Float
            sweepWait = spawnArgs.GetFloat("sweepWait", "0.5")
            sweeping = false
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            StartSound("snd_stop", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
            PostEventSec(SecurityCamera.EV_SecurityCam_ReverseSweep, sweepWait)
        }

        private fun Event_Alert() {
            val wait: Float
            SetAlertMode(ACTIVATED)
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            StartSound("snd_activate", gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
            ActivateTargets(this)
            CancelEvents(SecurityCamera.EV_SecurityCam_ContinueSweep)
            wait = spawnArgs.GetFloat("wait", "20")
            PostEventSec(SecurityCamera.EV_SecurityCam_ContinueSweep, wait)
        }

        private fun Event_AddLight() {
            val args = idDict()
            val right = idVec3()
            val up = idVec3()
            val target = idVec3()
            val temp = idVec3()
            val dir = idVec3()
            val radius: Float
            val lightOffset = idVec3()
            val spotLight: idLight
            dir.set(GetAxis())
            dir.NormalVectors(right, up)
            target.set(GetPhysics().GetOrigin().oPlus(dir.times(scanDist)))
            radius = Math.tan((scanFov * idMath.PI / 360.0f).toDouble()).toFloat()
            up.set(dir.oPlus(up.times(radius)))
            up.Normalize()
            up.set(GetPhysics().GetOrigin().oPlus(up.times(scanDist)))
            up.minusAssign(target)
            right.set(dir.oPlus(right.times(radius)))
            right.Normalize()
            right.set(GetPhysics().GetOrigin().oPlus(right.times(scanDist)))
            right.minusAssign(target)
            spawnArgs.GetVector("lightOffset", "0 0 0", lightOffset)
            args.Set("origin", GetPhysics().GetOrigin().oPlus(lightOffset).ToString())
            args.Set("light_target", target.ToString())
            args.Set("light_right", right.ToString())
            args.Set("light_up", up.ToString())
            args.SetFloat("angle", GetPhysics().GetAxis().get(0).ToYaw())
            spotLight = Game_local.gameLocal.SpawnEntityType(idLight::class.java, args) as idLight
            spotLight.Bind(this, true)
            spotLight.UpdateVisuals()
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        init {
            viewOffset = idVec3()
            trm = idTraceModel()
            physicsObj = idPhysics_RigidBody()
        }
    }
}