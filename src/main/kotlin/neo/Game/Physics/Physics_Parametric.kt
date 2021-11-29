package neo.Game.Physics

import neo.CM.CollisionModel.trace_s
import neo.Game.Entity
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.math.Angles
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Curve.idCurve_Spline
import neo.idlib.math.Extrapolate
import neo.idlib.math.Extrapolate.idExtrapolate
import neo.idlib.math.Interpolate.idInterpolateAccelDecelLinear
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object Physics_Parametric {
    /*
     ================
     idPhysics_Parametric_SavePState
     ================
     */
    fun idPhysics_Parametric_SavePState(savefile: idSaveGame?, state: parametricPState_s?) {
        savefile.WriteInt(state.time)
        savefile.WriteInt(state.atRest)
        savefile.WriteBool(state.useSplineAngles)
        savefile.WriteVec3(state.origin)
        savefile.WriteAngles(state.angles)
        savefile.WriteMat3(state.axis)
        savefile.WriteVec3(state.localOrigin)
        savefile.WriteAngles(state.localAngles)
        savefile.WriteInt(state.linearExtrapolation.GetExtrapolationType())
        savefile.WriteFloat(state.linearExtrapolation.GetStartTime())
        savefile.WriteFloat(state.linearExtrapolation.GetDuration())
        savefile.WriteVec3(state.linearExtrapolation.GetStartValue())
        savefile.WriteVec3(state.linearExtrapolation.GetBaseSpeed())
        savefile.WriteVec3(state.linearExtrapolation.GetSpeed())
        savefile.WriteInt(state.angularExtrapolation.GetExtrapolationType())
        savefile.WriteFloat(state.angularExtrapolation.GetStartTime())
        savefile.WriteFloat(state.angularExtrapolation.GetDuration())
        savefile.WriteAngles(state.angularExtrapolation.GetStartValue())
        savefile.WriteAngles(state.angularExtrapolation.GetBaseSpeed())
        savefile.WriteAngles(state.angularExtrapolation.GetSpeed())
        savefile.WriteFloat(state.linearInterpolation.GetStartTime())
        savefile.WriteFloat(state.linearInterpolation.GetAcceleration())
        savefile.WriteFloat(state.linearInterpolation.GetDeceleration())
        savefile.WriteFloat(state.linearInterpolation.GetDuration())
        savefile.WriteVec3(state.linearInterpolation.GetStartValue())
        savefile.WriteVec3(state.linearInterpolation.GetEndValue())
        savefile.WriteFloat(state.angularInterpolation.GetStartTime())
        savefile.WriteFloat(state.angularInterpolation.GetAcceleration())
        savefile.WriteFloat(state.angularInterpolation.GetDeceleration())
        savefile.WriteFloat(state.angularInterpolation.GetDuration())
        savefile.WriteAngles(state.angularInterpolation.GetStartValue())
        savefile.WriteAngles(state.angularInterpolation.GetEndValue())

        // spline is handled by owner
        savefile.WriteFloat(state.splineInterpolate.GetStartTime())
        savefile.WriteFloat(state.splineInterpolate.GetAcceleration())
        savefile.WriteFloat(state.splineInterpolate.GetDuration())
        savefile.WriteFloat(state.splineInterpolate.GetDeceleration())
        savefile.WriteFloat(state.splineInterpolate.GetStartValue())
        savefile.WriteFloat(state.splineInterpolate.GetEndValue())
    }

    /*
     ================
     idPhysics_Parametric_RestorePState
     ================
     */
    fun idPhysics_Parametric_RestorePState(savefile: idRestoreGame?, state: parametricPState_s?) {
        val startTime = CFloat()
        val duration = CFloat()
        val accelTime = CFloat()
        val decelTime = CFloat()
        val startValue = CFloat()
        val endValue = CFloat()
        val linearStartValue = idVec3()
        val linearBaseSpeed = idVec3()
        val linearSpeed = idVec3()
        val startPos = idVec3()
        val endPos = idVec3()
        val angularStartValue = idAngles()
        val angularBaseSpeed = idAngles()
        val angularSpeed = idAngles()
        val startAng = idAngles()
        val endAng = idAngles()
        val time = CInt()
        val atRest = CInt()
        val etype = CInt()
        val useSplineAngles = CBool(false)
        savefile.ReadInt(time)
        savefile.ReadInt(atRest)
        savefile.ReadBool(useSplineAngles)
        state.time = time.getVal()
        state.atRest = atRest.getVal()
        state.useSplineAngles = useSplineAngles.isVal
        savefile.ReadVec3(state.origin)
        savefile.ReadAngles(state.angles)
        savefile.ReadMat3(state.axis)
        savefile.ReadVec3(state.localOrigin)
        savefile.ReadAngles(state.localAngles)
        savefile.ReadInt(etype)
        savefile.ReadFloat(startTime)
        savefile.ReadFloat(duration)
        savefile.ReadVec3(linearStartValue)
        savefile.ReadVec3(linearBaseSpeed)
        savefile.ReadVec3(linearSpeed)
        state.linearExtrapolation.Init(
            startTime.getVal(),
            duration.getVal(),
            linearStartValue,
            linearBaseSpeed,
            linearSpeed,
            etype.getVal()
        )
        savefile.ReadInt(etype)
        savefile.ReadFloat(startTime)
        savefile.ReadFloat(duration)
        savefile.ReadAngles(angularStartValue)
        savefile.ReadAngles(angularBaseSpeed)
        savefile.ReadAngles(angularSpeed)
        state.angularExtrapolation.Init(
            startTime.getVal(),
            duration.getVal(),
            angularStartValue,
            angularBaseSpeed,
            angularSpeed,
            etype.getVal()
        )
        savefile.ReadFloat(startTime)
        savefile.ReadFloat(accelTime)
        savefile.ReadFloat(decelTime)
        savefile.ReadFloat(duration)
        savefile.ReadVec3(startPos)
        savefile.ReadVec3(endPos)
        state.linearInterpolation.Init(
            startTime.getVal(),
            accelTime.getVal(),
            decelTime.getVal(),
            duration.getVal(),
            startPos,
            endPos
        )
        savefile.ReadFloat(startTime)
        savefile.ReadFloat(accelTime)
        savefile.ReadFloat(decelTime)
        savefile.ReadFloat(duration)
        savefile.ReadAngles(startAng)
        savefile.ReadAngles(endAng)
        state.angularInterpolation.Init(
            startTime.getVal(),
            accelTime.getVal(),
            decelTime.getVal(),
            duration.getVal(),
            startAng,
            endAng
        )

        // spline is handled by owner
        savefile.ReadFloat(startTime)
        savefile.ReadFloat(accelTime)
        savefile.ReadFloat(duration)
        savefile.ReadFloat(decelTime)
        savefile.ReadFloat(startValue)
        savefile.ReadFloat(endValue)
        state.splineInterpolate.Init(
            startTime.getVal(),
            accelTime.getVal(),
            decelTime.getVal(),
            duration.getVal(),
            startValue.getVal(),
            endValue.getVal()
        )
    }

    /*
     ===================================================================================

     Parametric physics

     Used for predefined or scripted motion. The motion of an object is completely
     parametrized. By adjusting the parameters an object is forced to follow a
     predefined path. The parametric physics is typically used for doors, bridges,
     rotating fans etc.

     ===================================================================================
     */
    class parametricPState_s {
        var angles // world angles
                : idAngles? = null
        var angularExtrapolation // extrapolation based description of the orientation over time
                : idExtrapolate<idAngles?>? = null
        var angularInterpolation // interpolation based description of the orientation over time
                : idInterpolateAccelDecelLinear<idAngles?>? = null
        var atRest // set when simulation is suspended
                = 0
        var axis // world axis
                : idMat3? = null
        var linearExtrapolation // extrapolation based description of the position over time
                : idExtrapolate<idVec3?>? = null
        var linearInterpolation // interpolation based description of the position over time
                : idInterpolateAccelDecelLinear<idVec3?>? = null
        var localAngles // local angles
                : idAngles? = null
        val localOrigin: idVec3? = idVec3() // local origin
        val origin: idVec3? = idVec3() // world origin
        var spline // spline based description of the position over time
                : idCurve_Spline<idVec3?>? = null
        var splineInterpolate // position along the spline over time
                : idInterpolateAccelDecelLinear<Float?>? = null
        var time // physics time
                = 0
        var useSplineAngles // set the orientation using the spline
                = false
    }

    class idPhysics_Parametric : idPhysics_Base() {
        private var clipModel: idClipModel?

        // parametric physics state
        private var current: parametricPState_s?

        //
        // master
        private var hasMaster: Boolean
        private var isBlocked: Boolean
        private var isOrientated: Boolean

        //
        // pusher
        private var isPusher: Boolean
        private var pushFlags: Int

        //
        //
        //
        // results of last evaluate
        private var pushResults: trace_s?
        private var saved: parametricPState_s?

        // ~idPhysics_Parametric();
        override fun _deconstructor() {
            if (clipModel != null) {
                idClipModel.Companion.delete(clipModel)
            }
            if (current.spline != null) {
//                delete current.spline;
                current.spline = null
            }
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame?) {
            Physics_Parametric.idPhysics_Parametric_SavePState(savefile, current)
            Physics_Parametric.idPhysics_Parametric_SavePState(savefile, saved)
            savefile.WriteBool(isPusher)
            savefile.WriteClipModel(clipModel)
            savefile.WriteInt(pushFlags)
            savefile.WriteTrace(pushResults)
            savefile.WriteBool(isBlocked)
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        override fun Restore(savefile: idRestoreGame?) {
            val isPusher = CBool(false)
            val isBlocked = CBool(false)
            val hasMaster = CBool(false)
            val isOrientated = CBool(false)
            val pushFlags = CInt()
            Physics_Parametric.idPhysics_Parametric_RestorePState(savefile, current)
            Physics_Parametric.idPhysics_Parametric_RestorePState(savefile, saved)
            savefile.ReadBool(isPusher)
            savefile.ReadClipModel(clipModel)
            savefile.ReadInt(pushFlags)
            savefile.ReadTrace(pushResults)
            savefile.ReadBool(isBlocked)
            savefile.ReadBool(hasMaster)
            savefile.ReadBool(isOrientated)
            this.isPusher = isPusher.isVal
            this.isBlocked = isBlocked.isVal
            this.hasMaster = hasMaster.isVal
            this.isOrientated = isOrientated.isVal
            this.pushFlags = pushFlags.getVal()
        }

        fun SetPusher(flags: Int) {
            assert(clipModel != null)
            isPusher = true
            pushFlags = flags
        }

        fun IsPusher(): Boolean {
            return isPusher
        }

        fun SetLinearExtrapolation(   /*extrapolation_t*/type: Int,
                                                         time: Int,
                                                         duration: Int,
                                                         base: idVec3?,
                                                         speed: idVec3?,
                                                         baseSpeed: idVec3?
        ) {
            current.time = Game_local.gameLocal.time
            current.linearExtrapolation.Init(time.toFloat(), duration.toFloat(), base, baseSpeed, speed, type)
            current.localOrigin.oSet(base)
            Activate()
        }

        fun SetAngularExtrapolation(   /*extrapolation_t*/type: Int,
                                                          time: Int,
                                                          duration: Int,
                                                          base: idAngles?,
                                                          speed: idAngles?,
                                                          baseSpeed: idAngles?
        ) {
            current.time = Game_local.gameLocal.time
            current.angularExtrapolation.Init(time.toFloat(), duration.toFloat(), base, baseSpeed, speed, type)
            current.localAngles.oSet(base)
            Activate()
        }

        fun  /*extrapolation_t*/GetLinearExtrapolationType(): Int {
            return current.linearExtrapolation.GetExtrapolationType()
        }

        fun  /*extrapolation_t*/GetAngularExtrapolationType(): Int {
            return current.angularExtrapolation.GetExtrapolationType()
        }

        fun SetLinearInterpolation(
            time: Int,
            accelTime: Int,
            decelTime: Int,
            duration: Int,
            startPos: idVec3?,
            endPos: idVec3?
        ) {
            current.time = Game_local.gameLocal.time
            current.linearInterpolation.Init(
                time.toFloat(),
                accelTime.toFloat(),
                decelTime.toFloat(),
                duration.toFloat(),
                startPos,
                endPos
            )
            current.localOrigin.oSet(startPos)
            Activate()
        }

        fun SetAngularInterpolation(
            time: Int,
            accelTime: Int,
            decelTime: Int,
            duration: Int,
            startAng: idAngles?,
            endAng: idAngles?
        ) {
            current.time = Game_local.gameLocal.time
            current.angularInterpolation.Init(
                time.toFloat(),
                accelTime.toFloat(),
                decelTime.toFloat(),
                duration.toFloat(),
                startAng,
                endAng
            )
            current.localAngles.oSet(startAng)
            Activate()
        }

        fun SetSpline(spline: idCurve_Spline<idVec3?>?, accelTime: Int, decelTime: Int, useSplineAngles: Boolean) {
            if (current.spline != null) {
//		delete current.spline;
                current.spline = null
            }
            current.spline = spline
            if (current.spline != null) {
                val startTime = current.spline.GetTime(0)
                val endTime = current.spline.GetTime(current.spline.GetNumValues() - 1)
                val length = current.spline.GetLengthForTime(endTime)
                current.splineInterpolate.Init(
                    startTime,
                    accelTime.toFloat(),
                    decelTime.toFloat(),
                    endTime - startTime,
                    0.0f,
                    length
                )
            }
            current.useSplineAngles = useSplineAngles
            Activate()
        }

        fun GetSpline(): idCurve_Spline<idVec3?>? {
            return current.spline
        }

        fun GetSplineAcceleration(): Int {
            return current.splineInterpolate.GetAcceleration().toInt()
        }

        fun GetSplineDeceleration(): Int {
            return current.splineInterpolate.GetDeceleration().toInt()
        }

        fun UsingSplineAngles(): Boolean {
            return current.useSplineAngles
        }

        fun GetLocalOrigin(curOrigin: idVec3?) {
            curOrigin.oSet(current.localOrigin)
        }

        fun GetLocalAngles(curAngles: idAngles?) {
            curAngles.oSet(current.localAngles)
        }

        fun GetAngles(curAngles: idAngles?) {
            curAngles.oSet(current.angles)
        }

        // common physics interface
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            assert(model != null)
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.Companion.delete(clipModel)
            }
            clipModel = model
            clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return clipModel
        }

        override fun GetNumClipModels(): Int {
            return if (clipModel != null) 1 else 0
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            if (clipModel != null) {
                clipModel.SetContents(contents)
            }
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            return if (clipModel != null) {
                clipModel.GetContents()
            } else 0
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds? {
            return if (clipModel != null) {
                clipModel.GetBounds()
            } else super.GetBounds()
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds? {
            return if (clipModel != null) {
                clipModel.GetAbsBounds()
            } else super.GetAbsBounds()
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val oldLocalOrigin = idVec3()
            val oldOrigin = idVec3()
            val masterOrigin = idVec3()
            val oldLocalAngles: idAngles
            val oldAngles: idAngles
            val oldAxis: idMat3
            val masterAxis = idMat3()
            isBlocked = false
            oldLocalOrigin.oSet(current.localOrigin)
            oldOrigin.oSet(current.origin)
            oldLocalAngles = idAngles(current.localAngles)
            oldAngles = idAngles(current.angles)
            oldAxis = idMat3(current.axis)
            current.localOrigin.Zero()
            current.localAngles.Zero()
            if (current.spline != null) {
                val length: Float = current.splineInterpolate.GetCurrentValue(endTimeMSec.toFloat())
                val t = current.spline.GetTimeForLength(length, 0.01f)
                current.localOrigin.oSet(current.spline.GetCurrentValue(t))
                if (current.useSplineAngles) {
                    current.localAngles = current.spline.GetCurrentFirstDerivative(t).ToAngles()
                }
            } else if (current.linearInterpolation.GetDuration() != 0f) {
                current.localOrigin.plusAssign(current.linearInterpolation.GetCurrentValue(endTimeMSec.toFloat()))
            } else {
                current.localOrigin.plusAssign(current.linearExtrapolation.GetCurrentValue(endTimeMSec.toFloat()))
            }
            if (current.angularInterpolation.GetDuration() != 0f) {
                current.localAngles.plusAssign(current.angularInterpolation.GetCurrentValue(endTimeMSec.toFloat()))
            } else {
                current.localAngles.plusAssign(current.angularExtrapolation.GetCurrentValue(endTimeMSec.toFloat()))
            }
            current.localAngles.Normalize360()
            current.origin.oSet(current.localOrigin)
            current.angles.oSet(current.localAngles)
            current.axis.oSet(current.localAngles.ToMat3())
            if (hasMaster) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                if (masterAxis.IsRotated()) {
                    current.origin.oSet(current.origin.times(masterAxis).oPlus(masterOrigin))
                    if (isOrientated) {
                        current.axis.timesAssign(masterAxis)
                        current.angles = current.axis.ToAngles()
                    }
                } else {
                    current.origin.plusAssign(masterOrigin)
                }
            }
            if (isPusher) {
                run {
                    val pushResults = this.pushResults
                    Game_local.gameLocal.push.ClipPush(
                        pushResults,
                        self,
                        pushFlags,
                        oldOrigin,
                        oldAxis,
                        current.origin,
                        current.axis
                    )
                    this.pushResults = pushResults
                }
                if (pushResults.fraction < 1.0f) {
                    clipModel.Link(Game_local.gameLocal.clip, self, 0, oldOrigin, oldAxis)
                    current.localOrigin.oSet(oldLocalOrigin)
                    current.origin.oSet(oldOrigin)
                    current.localAngles = oldLocalAngles
                    current.angles = oldAngles
                    current.axis = oldAxis
                    isBlocked = true
                    return false
                }
                current.angles = current.axis.ToAngles()
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
            current.time = endTimeMSec
            if (TestIfAtRest()) {
                Rest()
            }
            return current.origin != oldOrigin || current.axis != oldAxis
        }

        override fun UpdateTime(endTimeMSec: Int) {
            val timeLeap = endTimeMSec - current.time
            current.time = endTimeMSec
            // move the trajectory start times to sync the trajectory with the current endTime
            current.linearExtrapolation.SetStartTime(current.linearExtrapolation.GetStartTime() + timeLeap)
            current.angularExtrapolation.SetStartTime(current.angularExtrapolation.GetStartTime() + timeLeap)
            current.linearInterpolation.SetStartTime(current.linearInterpolation.GetStartTime() + timeLeap)
            current.angularInterpolation.SetStartTime(current.angularInterpolation.GetStartTime() + timeLeap)
            if (current.spline != null) {
                current.spline.ShiftTime(timeLeap.toFloat())
                current.splineInterpolate.SetStartTime(current.splineInterpolate.GetStartTime() + timeLeap)
            }
        }

        override fun GetTime(): Int {
            return current.time
        }

        override fun Activate() {
            current.atRest = -1
            self.BecomeActive(Entity.TH_PHYSICS)
        }

        override fun IsAtRest(): Boolean {
            return current.atRest >= 0
        }

        override fun GetRestStartTime(): Int {
            return current.atRest
        }

        override fun IsPushable(): Boolean {
            return false
        }

        override fun SaveState() {
            saved = current
        }

        override fun RestoreState() {
            current = saved
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun SetOrigin(newOrigin: idVec3?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.linearExtrapolation.SetStartValue(newOrigin)
            current.linearInterpolation.SetStartValue(newOrigin)
            current.localOrigin.oSet(current.linearExtrapolation.GetCurrentValue(current.time.toFloat()))
            if (hasMaster) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.oSet(masterOrigin.oPlus(current.localOrigin.times(masterAxis)))
            } else {
                current.origin.oSet(current.localOrigin)
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
            Activate()
        }

        override fun SetAxis(newAxis: idMat3?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localAngles = newAxis.ToAngles()
            current.angularExtrapolation.SetStartValue(current.localAngles)
            current.angularInterpolation.SetStartValue(current.localAngles)
            current.localAngles = current.angularExtrapolation.GetCurrentValue(current.time.toFloat())
            if (hasMaster && isOrientated) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.axis = current.localAngles.ToMat3().times(masterAxis)
                current.angles = current.axis.ToAngles()
            } else {
                current.axis = current.localAngles.ToMat3()
                current.angles.oSet(current.localAngles)
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
            Activate()
        }

        override fun Translate(translation: idVec3?, id: Int /*= -1*/) {}
        override fun Rotate(rotation: idRotation?, id: Int /*= -1*/) {}
        override fun GetOrigin(id: Int /*= 0*/): idVec3? {
            return current.origin
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3? {
            return current.axis
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3?, id: Int /*= 0*/) {
            SetLinearExtrapolation(
                Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                Game_local.gameLocal.time,
                0,
                current.origin,
                newLinearVelocity,
                Vector.getVec3_origin()
            )
            current.linearInterpolation.Init(0f, 0f, 0f, 0f, Vector.getVec3_zero(), Vector.getVec3_zero())
            Activate()
        }

        override fun SetAngularVelocity(newAngularVelocity: idVec3?, id: Int /*= 0*/) {
            val rotation = idRotation()
            val vec = idVec3(newAngularVelocity)
            val angle: Float
            angle = vec.Normalize()
            rotation.Set(Vector.getVec3_origin(), vec, Vector.RAD2DEG(angle))
            SetAngularExtrapolation(
                Extrapolate.EXTRAPOLATION_LINEAR or Extrapolate.EXTRAPOLATION_NOSTOP,
                Game_local.gameLocal.time,
                0,
                current.angles,
                rotation.ToAngles(),
                Angles.getAng_zero()
            )
            current.angularInterpolation.Init(0f, 0f, 0f, 0f, Angles.getAng_zero(), Angles.getAng_zero())
            Activate()
        }

        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3? {
            curLinearVelocity.oSet(current.linearExtrapolation.GetCurrentSpeed(Game_local.gameLocal.time.toFloat()))
            return curLinearVelocity
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3? {
            val angles: idAngles?
            angles = current.angularExtrapolation.GetCurrentSpeed(Game_local.gameLocal.time.toFloat())
            curAngularVelocity.oSet(angles.ToAngularVelocity())
            return curAngularVelocity
        }

        override fun DisableClip() {
            if (clipModel != null) {
                clipModel.Disable()
            }
        }

        override fun EnableClip() {
            if (clipModel != null) {
                clipModel.Enable()
            }
        }

        override fun UnlinkClip() {
            if (clipModel != null) {
                clipModel.Unlink()
            }
        }

        override fun LinkClip() {
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (!hasMaster) {

                    // transform from world space to master space
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    current.localOrigin.oSet(current.origin.oMinus(masterOrigin).oMultiply(masterAxis.Transpose()))
                    if (orientated) {
                        current.localAngles = current.axis.times(masterAxis.Transpose()).ToAngles()
                    } else {
                        current.localAngles = current.axis.ToAngles()
                    }
                    current.linearExtrapolation.SetStartValue(current.localOrigin)
                    current.angularExtrapolation.SetStartValue(current.localAngles)
                    hasMaster = true
                    isOrientated = orientated
                }
            } else {
                if (hasMaster) {
                    // transform from master space to world space
                    current.localOrigin.oSet(current.origin)
                    current.localAngles.oSet(current.angles)
                    SetLinearExtrapolation(
                        Extrapolate.EXTRAPOLATION_NONE,
                        0,
                        0,
                        current.origin,
                        Vector.getVec3_origin(),
                        Vector.getVec3_origin()
                    )
                    SetAngularExtrapolation(
                        Extrapolate.EXTRAPOLATION_NONE,
                        0,
                        0,
                        current.angles,
                        Angles.getAng_zero(),
                        Angles.getAng_zero()
                    )
                    hasMaster = false
                }
            }
        }

        override fun GetBlockingInfo(): trace_s? {
            return if (isBlocked) pushResults else null
        }

        override fun GetBlockingEntity(): idEntity? {
            return if (isBlocked) {
                Game_local.gameLocal.entities[pushResults.c.entityNum]
            } else null
        }

        override fun GetLinearEndTime(): Int {
            return if (current.spline != null) {
                if (current.spline.GetBoundaryType() != idCurve_Spline.Companion.BT_CLOSED) {
                    current.spline.GetTime(current.spline.GetNumValues() - 1).toInt()
                } else {
                    0
                }
            } else if (current.linearInterpolation.GetDuration() != 0f) {
                current.linearInterpolation.GetEndTime().toInt()
            } else {
                current.linearExtrapolation.GetEndTime().toInt()
            }
        }

        override fun GetAngularEndTime(): Int {
            return if (current.angularInterpolation.GetDuration() != 0f) {
                current.angularInterpolation.GetEndTime().toInt()
            } else {
                current.angularExtrapolation.GetEndTime().toInt()
            }
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            msg.WriteLong(current.time)
            msg.WriteLong(current.atRest)
            msg.WriteFloat(current.origin.oGet(0))
            msg.WriteFloat(current.origin.oGet(1))
            msg.WriteFloat(current.origin.oGet(2))
            msg.WriteFloat(current.angles.oGet(0))
            msg.WriteFloat(current.angles.oGet(1))
            msg.WriteFloat(current.angles.oGet(2))
            msg.WriteDeltaFloat(current.origin.oGet(0), current.localOrigin.oGet(0))
            msg.WriteDeltaFloat(current.origin.oGet(1), current.localOrigin.oGet(1))
            msg.WriteDeltaFloat(current.origin.oGet(2), current.localOrigin.oGet(2))
            msg.WriteDeltaFloat(current.angles.oGet(0), current.localAngles.oGet(0))
            msg.WriteDeltaFloat(current.angles.oGet(1), current.localAngles.oGet(1))
            msg.WriteDeltaFloat(current.angles.oGet(2), current.localAngles.oGet(2))
            msg.WriteBits(current.linearExtrapolation.GetExtrapolationType(), 8)
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetStartTime())
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetDuration())
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetStartValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetStartValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetStartValue().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetSpeed().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetSpeed().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetSpeed().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetBaseSpeed().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetBaseSpeed().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.linearExtrapolation.GetBaseSpeed().oGet(2))
            msg.WriteBits(current.angularExtrapolation.GetExtrapolationType(), 8)
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetStartTime())
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetDuration())
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetStartValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetStartValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetStartValue().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetSpeed().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetSpeed().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetSpeed().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetBaseSpeed().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetBaseSpeed().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.angularExtrapolation.GetBaseSpeed().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetStartTime())
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetAcceleration())
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetDeceleration())
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetDuration())
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetStartValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetStartValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetStartValue().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetEndValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetEndValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.linearInterpolation.GetEndValue().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetStartTime())
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetAcceleration())
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetDeceleration())
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetDuration())
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetStartValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetStartValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetStartValue().oGet(2))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetEndValue().oGet(0))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetEndValue().oGet(1))
            msg.WriteDeltaFloat(0.0f, current.angularInterpolation.GetEndValue().oGet(2))
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            val   /*extrapolation_t*/linearType: Int
            val angularType: Int
            var startTime: Float
            var duration: Float
            var accelTime: Float
            var decelTime: Float
            val linearStartValue = idVec3()
            val linearSpeed = idVec3()
            val linearBaseSpeed = idVec3()
            val startPos = idVec3()
            val endPos = idVec3()
            val angularStartValue = idAngles()
            val angularSpeed = idAngles()
            val angularBaseSpeed = idAngles()
            val startAng = idAngles()
            val endAng = idAngles()
            current.time = msg.ReadLong()
            current.atRest = msg.ReadLong()
            current.origin.oSet(0, msg.ReadFloat())
            current.origin.oSet(1, msg.ReadFloat())
            current.origin.oSet(2, msg.ReadFloat())
            current.angles.oSet(0, msg.ReadFloat())
            current.angles.oSet(1, msg.ReadFloat())
            current.angles.oSet(2, msg.ReadFloat())
            current.localOrigin.oSet(0, msg.ReadDeltaFloat(current.origin.oGet(0)))
            current.localOrigin.oSet(1, msg.ReadDeltaFloat(current.origin.oGet(1)))
            current.localOrigin.oSet(2, msg.ReadDeltaFloat(current.origin.oGet(2)))
            current.localAngles.oSet(0, msg.ReadDeltaFloat(current.angles.oGet(0)))
            current.localAngles.oSet(1, msg.ReadDeltaFloat(current.angles.oGet(1)))
            current.localAngles.oSet(2, msg.ReadDeltaFloat(current.angles.oGet(2)))
            linearType =  /*(extrapolation_t)*/msg.ReadBits(8)
            startTime = msg.ReadDeltaFloat(0.0f)
            duration = msg.ReadDeltaFloat(0.0f)
            linearStartValue.oSet(0, msg.ReadDeltaFloat(0.0f))
            linearStartValue.oSet(1, msg.ReadDeltaFloat(0.0f))
            linearStartValue.oSet(2, msg.ReadDeltaFloat(0.0f))
            linearSpeed.oSet(0, msg.ReadDeltaFloat(0.0f))
            linearSpeed.oSet(1, msg.ReadDeltaFloat(0.0f))
            linearSpeed.oSet(2, msg.ReadDeltaFloat(0.0f))
            linearBaseSpeed.oSet(0, msg.ReadDeltaFloat(0.0f))
            linearBaseSpeed.oSet(1, msg.ReadDeltaFloat(0.0f))
            linearBaseSpeed.oSet(2, msg.ReadDeltaFloat(0.0f))
            current.linearExtrapolation.Init(
                startTime,
                duration,
                linearStartValue,
                linearBaseSpeed,
                linearSpeed,
                linearType
            )
            angularType = msg.ReadBits(8)
            startTime = msg.ReadDeltaFloat(0.0f)
            duration = msg.ReadDeltaFloat(0.0f)
            angularStartValue.oSet(0, msg.ReadDeltaFloat(0.0f))
            angularStartValue.oSet(1, msg.ReadDeltaFloat(0.0f))
            angularStartValue.oSet(2, msg.ReadDeltaFloat(0.0f))
            angularSpeed.oSet(0, msg.ReadDeltaFloat(0.0f))
            angularSpeed.oSet(1, msg.ReadDeltaFloat(0.0f))
            angularSpeed.oSet(2, msg.ReadDeltaFloat(0.0f))
            angularBaseSpeed.oSet(0, msg.ReadDeltaFloat(0.0f))
            angularBaseSpeed.oSet(1, msg.ReadDeltaFloat(0.0f))
            angularBaseSpeed.oSet(2, msg.ReadDeltaFloat(0.0f))
            current.angularExtrapolation.Init(
                startTime,
                duration,
                angularStartValue,
                angularBaseSpeed,
                angularSpeed,
                angularType
            )
            startTime = msg.ReadDeltaFloat(0.0f)
            accelTime = msg.ReadDeltaFloat(0.0f)
            decelTime = msg.ReadDeltaFloat(0.0f)
            duration = msg.ReadDeltaFloat(0.0f)
            startPos.oSet(0, msg.ReadDeltaFloat(0.0f))
            startPos.oSet(1, msg.ReadDeltaFloat(0.0f))
            startPos.oSet(2, msg.ReadDeltaFloat(0.0f))
            endPos.oSet(0, msg.ReadDeltaFloat(0.0f))
            endPos.oSet(1, msg.ReadDeltaFloat(0.0f))
            endPos.oSet(2, msg.ReadDeltaFloat(0.0f))
            current.linearInterpolation.Init(startTime, accelTime, decelTime, duration, startPos, endPos)
            startTime = msg.ReadDeltaFloat(0.0f)
            accelTime = msg.ReadDeltaFloat(0.0f)
            decelTime = msg.ReadDeltaFloat(0.0f)
            duration = msg.ReadDeltaFloat(0.0f)
            startAng.oSet(0, msg.ReadDeltaFloat(0.0f))
            startAng.oSet(1, msg.ReadDeltaFloat(0.0f))
            startAng.oSet(2, msg.ReadDeltaFloat(0.0f))
            endAng.oSet(0, msg.ReadDeltaFloat(0.0f))
            endAng.oSet(1, msg.ReadDeltaFloat(0.0f))
            endAng.oSet(2, msg.ReadDeltaFloat(0.0f))
            current.angularInterpolation.Init(startTime, accelTime, decelTime, duration, startAng, endAng)
            current.axis = current.angles.ToMat3()
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        private fun TestIfAtRest(): Boolean {
            if (current.linearExtrapolation.GetExtrapolationType() and Extrapolate.EXTRAPOLATION_NOSTOP.inv() == Extrapolate.EXTRAPOLATION_NONE && current.angularExtrapolation.GetExtrapolationType() and Extrapolate.EXTRAPOLATION_NOSTOP.inv() == Extrapolate.EXTRAPOLATION_NONE && current.linearInterpolation.GetDuration() == 0f && current.angularInterpolation.GetDuration() == 0f && current.spline == null) {
                return true
            }
            if (!current.linearExtrapolation.IsDone(current.time.toFloat())) {
                return false
            }
            if (!current.angularExtrapolation.IsDone(current.time.toFloat())) {
                return false
            }
            if (!current.linearInterpolation.IsDone(current.time.toFloat())) {
                return false
            }
            return if (!current.angularInterpolation.IsDone(current.time.toFloat())) {
                false
            } else current.spline == null || current.spline.IsDone(current.time.toFloat())
        }

        private fun Rest() {
            current.atRest = Game_local.gameLocal.time
            self.BecomeInactive(Entity.TH_PHYSICS)
        }

        companion object {
            // CLASS_PROTOTYPE( idPhysics_Parametric );
            private val curAngularVelocity: idVec3? = idVec3()
            private val curLinearVelocity: idVec3? = idVec3()
        }

        init {
            current = parametricPState_s()
            current.time = Game_local.gameLocal.time
            current.atRest = -1
            current.useSplineAngles = false
            current.angles = idAngles()
            current.axis = idMat3.Companion.getMat3_identity()
            current.localAngles = idAngles()
            current.linearExtrapolation = idExtrapolate()
            current.linearExtrapolation.Init(
                0f,
                0f,
                Vector.getVec3_zero(),
                Vector.getVec3_zero(),
                Vector.getVec3_zero(),
                Extrapolate.EXTRAPOLATION_NONE
            )
            current.angularExtrapolation = idExtrapolate()
            current.angularExtrapolation.Init(
                0f,
                0f,
                Angles.getAng_zero(),
                Angles.getAng_zero(),
                Angles.getAng_zero(),
                Extrapolate.EXTRAPOLATION_NONE
            )
            current.linearInterpolation = idInterpolateAccelDecelLinear()
            current.linearInterpolation.Init(0f, 0f, 0f, 0f, Vector.getVec3_zero(), Vector.getVec3_zero())
            current.angularInterpolation = idInterpolateAccelDecelLinear()
            current.angularInterpolation.Init(0f, 0f, 0f, 0f, Angles.getAng_zero(), Angles.getAng_zero())
            current.spline = null
            current.splineInterpolate = idInterpolateAccelDecelLinear()
            current.splineInterpolate.Init(0f, 1f, 1f, 2f, 0f, 0f)
            saved = current
            isPusher = false
            pushFlags = 0
            clipModel = null
            isBlocked = false
            pushResults = trace_s() //memset( &pushResults, 0, sizeof(pushResults));
            hasMaster = false
            isOrientated = false
        }
    }
}