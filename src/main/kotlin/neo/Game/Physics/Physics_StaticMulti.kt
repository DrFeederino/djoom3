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
import neo.Game.Physics.Physics_Static.staticPState_s
import neo.idlib.BV.Bounds
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Quat.idCQuat
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object Physics_StaticMulti {
    var defaultState: staticPState_s? = staticPState_s() //TODO:?

    /*
     ===============================================================================

     Physics for a non moving object using no or multiple collision models.

     ===============================================================================
     */
    class idPhysics_StaticMulti : idPhysics() {
        //
        //
        protected val clipModels // collision model
                : idList<idClipModel?>?
        protected val current // physics state
                : idList<staticPState_s?>?

        //
        // master
        protected var hasMaster = false
        protected var isOrientated = false
        protected var self // entity using this physics object
                : idEntity? = null

        // ~idPhysics_StaticMulti();
        override fun _deconstructor() {
            if (self != null && self.GetPhysics() === this) {
                self.SetPhysics(null)
            }
            idForce.Companion.DeletePhysics(this)
            for (i in 0 until clipModels.Num()) {
                idClipModel.Companion.delete(clipModels.get(i))
            }
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame?) {
            var i: Int
            savefile.WriteObject(self)
            savefile.WriteInt(current.Num())
            i = 0
            while (i < current.Num()) {
                savefile.WriteVec3(current.get(i).origin)
                savefile.WriteMat3(current.get(i).axis)
                savefile.WriteVec3(current.get(i).localOrigin)
                savefile.WriteMat3(current.get(i).localAxis)
                i++
            }
            savefile.WriteInt(clipModels.Num())
            i = 0
            while (i < clipModels.Num()) {
                savefile.WriteClipModel(clipModels.get(i))
                i++
            }
            savefile.WriteBool(hasMaster)
            savefile.WriteBool(isOrientated)
        }

        override fun Restore(savefile: idRestoreGame?) {
            var i: Int
            val num = CInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/self)
            savefile.ReadInt(num)
            current.AssureSize(num.getVal())
            i = 0
            while (i < num.getVal()) {
                savefile.ReadVec3(current.get(i).origin)
                savefile.ReadMat3(current.get(i).axis)
                savefile.ReadVec3(current.get(i).localOrigin)
                savefile.ReadMat3(current.get(i).localAxis)
                i++
            }
            savefile.ReadInt(num)
            clipModels.SetNum(num.getVal())
            i = 0
            while (i < num.getVal()) {
                savefile.ReadClipModel(clipModels.get(i))
                i++
            }
            hasMaster = savefile.ReadBool()
            isOrientated = savefile.ReadBool()
        }

        @JvmOverloads
        fun RemoveIndex(id: Int /*= 0*/, freeClipModel: Boolean = true /*= true*/) {
            if (id < 0 || id >= clipModels.Num()) {
                return
            }
            if (clipModels.get(id) != null && freeClipModel) {
                idClipModel.Companion.delete(clipModels.get(id))
                clipModels.set(id, null)
            }
            clipModels.RemoveIndex(id)
            current.RemoveIndex(id)
        }

        // common physics interface
        override fun SetSelf(e: idEntity?) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            var i: Int
            assert(self != null)
            if (id >= clipModels.Num()) {
                current.AssureSize(id + 1, Physics_StaticMulti.defaultState)
                clipModels.AssureSize(id + 1, null)
            }
            if (clipModels.get(id) != null && clipModels.get(id) !== model && freeOld) {
                idClipModel.Companion.delete(clipModels.get(id))
            }
            clipModels.set(id, model)
            if (clipModels.get(id) != null) {
                clipModels.get(id)
                    .Link(Game_local.gameLocal.clip, self, id, current.get(id).origin, current.get(id).axis)
            }
            i = clipModels.Num() - 1
            while (i >= 1) {
                if (clipModels.get(i) != null) {
                    break
                }
                i--
            }
            current.SetNum(i + 1, false)
            clipModels.SetNum(i + 1, false)
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return if (id >= 0 && id < clipModels.Num() && clipModels.get(id) != null) {
                clipModels.get(id)
            } else Game_local.gameLocal.clip.DefaultClipModel()
        }

        override fun GetNumClipModels(): Int {
            return clipModels.Num()
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0.0f
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                if (clipModels.get(id) != null) {
                    clipModels.get(id).SetContents(contents)
                }
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels.get(i) != null) {
                        clipModels.get(i).SetContents(contents)
                    }
                    i++
                }
            }
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            var i: Int
            var contents = 0
            if (id >= 0 && id < clipModels.Num()) {
                if (clipModels.get(id) != null) {
                    contents = clipModels.get(id).GetContents()
                }
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels.get(i) != null) {
                        contents = contents or clipModels.get(i).GetContents()
                    }
                    i++
                }
            }
            return contents
        }

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {}
        override fun GetClipMask(id: Int /*= -1*/): Int {
            return 0
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds? {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                if (clipModels.get(id) != null) {
                    return clipModels.get(id).GetBounds()
                }
            }
            if (id == -1) {
                bounds.Clear()
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels.get(i) != null) {
                        bounds.AddBounds(clipModels.get(i).GetAbsBounds())
                    }
                    i++
                }
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels.get(i) != null) {
                        bounds.minusAssign(0, clipModels.get(i).GetOrigin())
                        bounds.minusAssign(1, clipModels.get(i).GetOrigin())
                        break
                    }
                    i++
                }
                return bounds
            }
            return Bounds.bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds? {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                if (clipModels.get(id) != null) {
                    return clipModels.get(id).GetAbsBounds()
                }
            }
            if (id == -1) {
                absBounds.Clear()
                i = 0
                while (i < clipModels.Num()) {
                    if (clipModels.get(i) != null) {
                        absBounds.AddBounds(clipModels.get(i).GetAbsBounds())
                    }
                    i++
                }
                return absBounds
            }
            return Bounds.bounds_zero
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (hasMaster) {
                self.GetMasterPosition(masterOrigin, masterAxis)
                i = 0
                while (i < clipModels.Num()) {
                    current.get(i).origin.set(masterOrigin.oPlus(current.get(i).localOrigin.times(masterAxis)))
                    if (isOrientated) {
                        current.get(i).axis.set(current.get(i).localAxis.times(masterAxis))
                    } else {
                        current.get(i).axis.set(current.get(i).localAxis)
                    }
                    if (clipModels.get(i) != null) {
                        clipModels.get(i)
                            .Link(Game_local.gameLocal.clip, self, i, current.get(i).origin, current.get(i).axis)
                    }
                    i++
                }

                // FIXME: return false if master did not move
                return true
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
            if (id >= 0 && id < clipModels.Num()) {
                current.get(id).localOrigin.set(newOrigin)
                if (hasMaster) {
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    current.get(id).origin.set(masterOrigin.oPlus(newOrigin.times(masterAxis)))
                } else {
                    current.get(id).origin.set(newOrigin)
                }
                if (clipModels.get(id) != null) {
                    clipModels.get(id)
                        .Link(Game_local.gameLocal.clip, self, id, current.get(id).origin, current.get(id).axis)
                }
            } else if (id == -1) {
                if (hasMaster) {
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    Translate(masterOrigin.oPlus(masterAxis.times(newOrigin).minus(current.get(0).origin)))
                } else {
                    Translate(newOrigin.minus(current.get(0).origin))
                }
            }
        }

        override fun SetAxis(newAxis: idMat3?, id: Int /*= -1*/) {
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (id >= 0 && id < clipModels.Num()) {
                current.get(id).localAxis.set(newAxis)
                if (hasMaster && isOrientated) {
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    current.get(id).axis.set(newAxis.times(masterAxis))
                } else {
                    current.get(id).axis.set(newAxis)
                }
                if (clipModels.get(id) != null) {
                    clipModels.get(id)
                        .Link(Game_local.gameLocal.clip, self, id, current.get(id).origin, current.get(id).axis)
                }
            } else if (id == -1) {
                val axis: idMat3?
                val rotation: idRotation?
                axis = if (hasMaster) {
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    current.get(0).axis.Transpose().times(newAxis.times(masterAxis))
                } else {
                    current.get(0).axis.Transpose().times(newAxis)
                }
                rotation = axis.ToRotation()
                rotation.SetOrigin(current.get(0).origin)
                Rotate(rotation)
            }
        }

        override fun Translate(translation: idVec3?, id: Int /*= -1*/) {
            var i: Int
            if (id >= 0 && id < clipModels.Num()) {
                current.get(id).localOrigin.plusAssign(translation)
                current.get(id).origin.plusAssign(translation)
                if (clipModels.get(id) != null) {
                    clipModels.get(id)
                        .Link(Game_local.gameLocal.clip, self, id, current.get(id).origin, current.get(id).axis)
                }
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    current.get(i).localOrigin.plusAssign(translation)
                    current.get(i).origin.plusAssign(translation)
                    if (clipModels.get(i) != null) {
                        clipModels.get(i)
                            .Link(Game_local.gameLocal.clip, self, i, current.get(i).origin, current.get(i).axis)
                    }
                    i++
                }
            }
        }

        override fun Rotate(rotation: idRotation?, id: Int /*= -1*/) {
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (id >= 0 && id < clipModels.Num()) {
                current.get(id).origin.timesAssign(rotation)
                current.get(id).axis.timesAssign(rotation.ToMat3())
                if (hasMaster) {
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    current.get(id).localAxis.timesAssign(rotation.ToMat3())
                    current.get(id).localOrigin.set(
                        current.get(id).origin.minus(masterOrigin).oMultiply(masterAxis.Transpose())
                    )
                } else {
                    current.get(id).localAxis.set(current.get(id).axis)
                    current.get(id).localOrigin.set(current.get(id).origin)
                }
                if (clipModels.get(id) != null) {
                    clipModels.get(id)
                        .Link(Game_local.gameLocal.clip, self, id, current.get(id).origin, current.get(id).axis)
                }
            } else if (id == -1) {
                i = 0
                while (i < clipModels.Num()) {
                    current.get(i).origin.timesAssign(rotation)
                    current.get(i).axis.timesAssign(rotation.ToMat3())
                    if (hasMaster) {
                        self.GetMasterPosition(masterOrigin, masterAxis)
                        current.get(i).localAxis.timesAssign(rotation.ToMat3())
                        current.get(i).localOrigin.set(
                            current.get(i).origin.minus(masterOrigin).oMultiply(masterAxis.Transpose())
                        )
                    } else {
                        current.get(i).localAxis.set(current.get(i).axis)
                        current.get(i).localOrigin.set(current.get(i).origin)
                    }
                    if (clipModels.get(i) != null) {
                        clipModels.get(i)
                            .Link(Game_local.gameLocal.clip, self, i, current.get(i).origin, current.get(i).axis)
                    }
                    i++
                }
            }
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3? {
            if (id >= 0 && id < clipModels.Num()) {
                return current.get(id).origin
            }
            return if (clipModels.Num() != 0) {
                current.get(0).origin
            } else {
                Vector.getVec3_origin()
            }
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3? {
            if (id >= 0 && id < clipModels.Num()) {
                return current.get(id).axis
            }
            return if (clipModels.Num() != 0) {
                current.get(0).axis
            } else {
                idMat3.Companion.getMat3_identity()
            }
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
//	memset( &results, 0, sizeof( trace_t ) );//TODO:
            Game_local.gameLocal.Warning("idPhysics_StaticMulti::ClipTranslation called")
        }

        override fun ClipRotation(results: trace_s?, rotation: idRotation?, model: idClipModel?) {
//	memset( &results, 0, sizeof( trace_t ) );//TODO:
            Game_local.gameLocal.Warning("idPhysics_StaticMulti::ClipRotation called")
        }

        override fun ClipContents(model: idClipModel?): Int {
            var i: Int
            var contents: Int
            contents = 0
            i = 0
            while (i < clipModels.Num()) {
                if (clipModels.get(i) != null) {
                    contents = if (model != null) {
                        contents or Game_local.gameLocal.clip.ContentsModel(
                            clipModels.get(i).GetOrigin(), clipModels.get(i), clipModels.get(i).GetAxis(), -1,
                            model.Handle(), model.GetOrigin(), model.GetAxis()
                        )
                    } else {
                        contents or Game_local.gameLocal.clip.Contents(
                            clipModels.get(i).GetOrigin(),
                            clipModels.get(i),
                            clipModels.get(i).GetAxis(),
                            -1,
                            null
                        )
                    }
                }
                i++
            }
            return contents
        }

        override fun DisableClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                if (clipModels.get(i) != null) {
                    clipModels.get(i).Disable()
                }
                i++
            }
        }

        override fun EnableClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                if (clipModels.get(i) != null) {
                    clipModels.get(i).Enable()
                }
                i++
            }
        }

        override fun UnlinkClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                if (clipModels.get(i) != null) {
                    clipModels.get(i).Unlink()
                }
                i++
            }
        }

        override fun LinkClip() {
            var i: Int
            i = 0
            while (i < clipModels.Num()) {
                if (clipModels.get(i) != null) {
                    clipModels.get(i)
                        .Link(Game_local.gameLocal.clip, self, i, current.get(i).origin, current.get(i).axis)
                }
                i++
            }
        }

        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return 0
        }

        override fun GetContact(num: Int): contactInfo_t? {
            info = contactInfo_t()
            //	memset( &info, 0, sizeof( info ) );
            return info
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
            var i: Int
            val masterOrigin = idVec3()
            val masterAxis = idMat3()
            if (master != null) {
                if (!hasMaster) {
                    // transform from world space to master space
                    self.GetMasterPosition(masterOrigin, masterAxis)
                    i = 0
                    while (i < clipModels.Num()) {
                        current.get(i).localOrigin.set(
                            current.get(i).origin.minus(masterOrigin).oMultiply(masterAxis.Transpose())
                        )
                        if (orientated) {
                            current.get(i).localAxis.set(current.get(i).axis.times(masterAxis.Transpose()))
                        } else {
                            current.get(i).localAxis.set(current.get(i).axis)
                        }
                        i++
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
            var i: Int
            var quat: idCQuat?
            var localQuat: idCQuat?
            msg.WriteByte(current.Num())
            i = 0
            while (i < current.Num()) {
                quat = current.get(i).axis.ToCQuat()
                localQuat = current.get(i).localAxis.ToCQuat()
                msg.WriteFloat(current.get(i).origin.get(0))
                msg.WriteFloat(current.get(i).origin.get(1))
                msg.WriteFloat(current.get(i).origin.get(2))
                msg.WriteFloat(quat.x)
                msg.WriteFloat(quat.y)
                msg.WriteFloat(quat.z)
                msg.WriteDeltaFloat(current.get(i).origin.get(0), current.get(i).localOrigin.get(0))
                msg.WriteDeltaFloat(current.get(i).origin.get(1), current.get(i).localOrigin.get(1))
                msg.WriteDeltaFloat(current.get(i).origin.get(2), current.get(i).localOrigin.get(2))
                msg.WriteDeltaFloat(quat.x, localQuat.x)
                msg.WriteDeltaFloat(quat.y, localQuat.y)
                msg.WriteDeltaFloat(quat.z, localQuat.z)
                i++
            }
        }

        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            var i: Int
            val num: Int
            val quat = idCQuat()
            val localQuat = idCQuat()
            num = msg.ReadByte()
            assert(num == current.Num())
            i = 0
            while (i < current.Num()) {
                current.get(i).origin.set(0, msg.ReadFloat())
                current.get(i).origin.set(1, msg.ReadFloat())
                current.get(i).origin.set(2, msg.ReadFloat())
                quat.x = msg.ReadFloat()
                quat.y = msg.ReadFloat()
                quat.z = msg.ReadFloat()
                current.get(i).localOrigin.set(0, msg.ReadDeltaFloat(current.get(i).origin.get(0)))
                current.get(i).localOrigin.set(1, msg.ReadDeltaFloat(current.get(i).origin.get(1)))
                current.get(i).localOrigin.set(2, msg.ReadDeltaFloat(current.get(i).origin.get(2)))
                localQuat.x = msg.ReadDeltaFloat(quat.x)
                localQuat.y = msg.ReadDeltaFloat(quat.y)
                localQuat.z = msg.ReadDeltaFloat(quat.z)
                current.get(i).axis.set(quat.ToMat3())
                current.get(i).localAxis.set(localQuat.ToMat3())
                i++
            }
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
            // CLASS_PROTOTYPE( idPhysics_StaticMulti );
            private val gravity: idVec3? = idVec3(0, 0, -SysCvar.g_gravity.GetFloat())
            private val gravityNormal: idVec3? = idVec3(0, 0, -1)
            private val absBounds: idBounds? = idBounds()
            private val bounds: idBounds? = idBounds()
            private var info: contactInfo_t? = null
        }

        init {
            clipModels = idList<Any?>()
            current = idList<Any?>()
            Physics_StaticMulti.defaultState.origin.Zero()
            Physics_StaticMulti.defaultState.axis.Identity()
            Physics_StaticMulti.defaultState.localOrigin.Zero()
            Physics_StaticMulti.defaultState.localAxis.Identity()
            current.SetNum(1)
            current.set(0, Physics_StaticMulti.defaultState)
            clipModels.SetNum(1)
            clipModels.set(0, null)
        }
    }
}