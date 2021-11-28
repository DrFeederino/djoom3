package neo.Game.Physics

import neo.CM.CollisionModel.contactInfo_t
import neo.CM.CollisionModel.trace_s
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.idlib.BV.Bounds
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Quat.idCQuat
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Physics_Static {
    /*
     ===============================================================================

     Physics for a non moving object using at most one collision model.

     ===============================================================================
     */
    class staticPState_s {
        var axis: idMat3?
        var localAxis: idMat3?
        val localOrigin: idVec3?
        val origin: idVec3?

        init {
            origin = idVec3()
            axis = idMat3()
            localOrigin = idVec3()
            localAxis = idMat3()
        }
    }

    class idPhysics_Static : idPhysics() {
        protected var clipModel // collision model
                : idClipModel? = null

        //
        //
        protected var current // physics state
                : staticPState_s?

        //
        // master
        protected var hasMaster: Boolean
        protected var isOrientated: Boolean
        protected var self // entity using this physics object
                : idEntity? = null

        // ~idPhysics_Static();
        override fun _deconstructor() {
            if (self != null && self.GetPhysics() === this) {
                self.SetPhysics(null)
            }
            idForce.Companion.DeletePhysics(this)
            if (clipModel != null) {
                idClipModel.Companion.delete(clipModel)
            }
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteObject(self)
            savefile.WriteVec3(current.origin)
            savefile.WriteMat3(current.axis)
            savefile.WriteVec3(current.localOrigin)
            savefile.WriteMat3(current.localAxis)
            savefile.WriteClipModel(clipModel)
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadObject( /*reinterpret_cast<idClass*&>*/self)
            savefile.ReadVec3(current.origin)
            savefile.ReadMat3(current.axis)
            savefile.ReadVec3(current.localOrigin)
            savefile.ReadMat3(current.localAxis)
            savefile.ReadClipModel(clipModel)
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }

        // common physics interface
        override fun SetSelf(e: idEntity?) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.Companion.delete(clipModel)
            }
            clipModel = model
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return if (clipModel != null) {
                clipModel
            } else Game_local.gameLocal.clip.DefaultClipModel()
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

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {}
        override fun GetClipMask(id: Int /*= -1*/): Int {
            return 0
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds? {
            return if (clipModel != null) {
                clipModel.GetBounds()
            } else Bounds.bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds? {
            if (clipModel != null) {
                return clipModel.GetAbsBounds()
            }
            absBounds = idBounds(current.origin, current.origin)
            return absBounds
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            val masterOrigin = idVec3()
            val oldOrigin = idVec3()
            val masterAxis = idMat3()
            val oldAxis = idMat3()
            if (hasMaster) {
                oldOrigin.oSet(current.origin)
                oldAxis.oSet(current.axis)
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.oSet(masterOrigin.oPlus(current.localOrigin.oMultiply(masterAxis)))
                if (isOrientated) {
                    current.axis.oSet(current.localAxis.oMultiply(masterAxis))
                } else {
                    current.axis.oSet(current.localAxis)
                }
                if (clipModel != null) {
                    clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
                }
                return current.origin != oldOrigin || current.axis != oldAxis
            }
            return false
        }

        override fun UpdateTime(endTimeMSec: Int) {}
        override fun GetTime(): Int {
            return 0
        }

        override fun GetImpactInfo(id: Int, point: idVec3?): impactInfo_s? {
            return impactInfo_s()
        }

        override fun ApplyImpulse(id: Int, point: idVec3?, impulse: idVec3?) {}
        override fun AddForce(id: Int, point: idVec3?, force: idVec3?) {}
        override fun Activate() {}
        override fun PutToRest() {}
        override fun IsAtRest(): Boolean {
            return true
        }

        override fun GetRestStartTime(): Int {
            return 0
        }

        override fun IsPushable(): Boolean {
            return false
        }

        override fun SaveState() {}
        override fun RestoreState() {}
        override fun SetOrigin(newOrigin: idVec3?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localOrigin.oSet(newOrigin)
            if (hasMaster) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.origin.oSet(masterOrigin.oPlus(newOrigin.oMultiply(masterAxis)))
            } else {
                current.origin.oSet(newOrigin)
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun SetAxis(newAxis: idMat3?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.localAxis.oSet(newAxis)
            if (hasMaster && isOrientated) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.axis.oSet(newAxis.oMultiply(masterAxis))
            } else {
                current.axis.oSet(newAxis)
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun Translate(translation: idVec3?, id: Int /*= -1*/) {
            current.localOrigin.oPluSet(translation)
            current.origin.oPluSet(translation)
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun Rotate(rotation: idRotation?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            current.origin.oMulSet(rotation)
            current.axis.oMulSet(rotation.ToMat3())
            if (hasMaster) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                current.localAxis.oMulSet(rotation.ToMat3())
                current.localOrigin.oSet(current.origin.oMinus(masterOrigin).oMultiply(masterAxis.Transpose()))
            } else {
                current.localAxis.oSet(current.axis)
                current.localOrigin.oSet(current.origin)
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, current.origin, current.axis)
            }
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3? {
            return current.origin
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3? {
            return current.axis
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3?, id: Int /*= 0*/) {}
        override fun SetAngularVelocity(newAngularVelocity: idVec3?, id: Int /*= 0*/) {}
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun SetGravity(newGravity: idVec3?) {}
        override fun GetGravity(): idVec3? {
            return gravity
        }

        override fun GetGravityNormal(): idVec3? {
            return gravityNormal
        }

        override fun ClipTranslation(results: trace_s?, translation: idVec3?, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results, current.origin, current.origin.oPlus(translation),
                    clipModel, current.axis, Game_local.MASK_SOLID, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Translation(
                    results, current.origin, current.origin.oPlus(translation),
                    clipModel, current.axis, Game_local.MASK_SOLID, self
                )
            }
        }

        override fun ClipRotation(results: trace_s?, rotation: idRotation?, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.RotationModel(
                    results, current.origin, rotation,
                    clipModel, current.axis, Game_local.MASK_SOLID, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Rotation(
                    results,
                    current.origin,
                    rotation,
                    clipModel,
                    current.axis,
                    Game_local.MASK_SOLID,
                    self
                )
            }
        }

        override fun ClipContents(model: idClipModel?): Int {
            return if (clipModel != null) {
                if (model != null) {
                    Game_local.gameLocal.clip.ContentsModel(
                        clipModel.GetOrigin(), clipModel, clipModel.GetAxis(), -1,
                        model.Handle(), model.GetOrigin(), model.GetAxis()
                    )
                } else {
                    Game_local.gameLocal.clip.Contents(clipModel.GetOrigin(), clipModel, clipModel.GetAxis(), -1, null)
                }
            } else 0
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

        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return 0
        }

        override fun GetContact(num: Int): contactInfo_t? {
//	memset( &info, 0, sizeof( info ) );
            return null
        }

        override fun ClearContacts() {}
        override fun AddContactEntity(e: idEntity?) {}
        override fun RemoveContactEntity(e: idEntity?) {}
        override fun HasGroundContacts(): Boolean {
            return false
        }

        override fun IsGroundEntity(entityNum: Int): Boolean {
            return false
        }

        override fun IsGroundClipModel(entityNum: Int, id: Int): Boolean {
            return false
        }

        override fun SetPushed(deltaTime: Int) {}
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
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
                        current.localAxis.oSet(current.axis.oMultiply(masterAxis.Transpose()))
                    } else {
                        current.localAxis.oSet(current.axis)
                    }
                    hasMaster = true
                    isOrientated = orientated
                }
            } else {
                if (hasMaster) {
                    hasMaster = false
                }
            }
        }

        override fun GetBlockingInfo(): trace_s? {
            return null
        }

        override fun GetBlockingEntity(): idEntity? {
            return null
        }

        override fun GetLinearEndTime(): Int {
            return 0
        }

        override fun GetAngularEndTime(): Int {
            return 0
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            val quat: idCQuat?
            val localQuat: idCQuat?
            quat = current.axis.ToCQuat()
            localQuat = current.localAxis.ToCQuat()
            msg.WriteFloat(current.origin.oGet(0))
            msg.WriteFloat(current.origin.oGet(1))
            msg.WriteFloat(current.origin.oGet(2))
            msg.WriteFloat(quat.x)
            msg.WriteFloat(quat.y)
            msg.WriteFloat(quat.z)
            msg.WriteDeltaFloat(current.origin.oGet(0), current.localOrigin.oGet(0))
            msg.WriteDeltaFloat(current.origin.oGet(1), current.localOrigin.oGet(1))
            msg.WriteDeltaFloat(current.origin.oGet(2), current.localOrigin.oGet(2))
            msg.WriteDeltaFloat(quat.x, localQuat.x)
            msg.WriteDeltaFloat(quat.y, localQuat.y)
            msg.WriteDeltaFloat(quat.z, localQuat.z)
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            val quat = idCQuat()
            val localQuat = idCQuat()
            current.origin.oSet(0, msg.ReadFloat())
            current.origin.oSet(1, msg.ReadFloat())
            current.origin.oSet(2, msg.ReadFloat())
            quat.x = msg.ReadFloat()
            quat.y = msg.ReadFloat()
            quat.z = msg.ReadFloat()
            current.localOrigin.oSet(0, msg.ReadDeltaFloat(current.origin.oGet(0)))
            current.localOrigin.oSet(1, msg.ReadDeltaFloat(current.origin.oGet(1)))
            current.localOrigin.oSet(2, msg.ReadDeltaFloat(current.origin.oGet(2)))
            localQuat.x = msg.ReadDeltaFloat(quat.x)
            localQuat.y = msg.ReadDeltaFloat(quat.y)
            localQuat.z = msg.ReadDeltaFloat(quat.z)
            current.axis.oSet(quat.ToMat3())
            current.localAxis.oSet(localQuat.ToMat3())
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun  /*idTypeInfo*/GetType(): Class<*>? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            // CLASS_PROTOTYPE( idPhysics_Static );
            private val gravity: idVec3? = idVec3(0, 0, -SysCvar.g_gravity.GetFloat())
            private val gravityNormal: idVec3? = idVec3(0, 0, -1)
            private var absBounds: idBounds? = null
        }

        init {
            current = staticPState_s()
            current.origin.Zero()
            current.axis.Identity()
            current.localOrigin.Zero()
            current.localAxis.Identity()
            hasMaster = false
            isOrientated = false
        }
    }
}