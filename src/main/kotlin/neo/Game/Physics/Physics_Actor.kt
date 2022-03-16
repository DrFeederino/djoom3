package neo.Game.Physics

import neo.CM.CollisionModel.trace_s
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idEntityPtr
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Physics_Actor {
    /*
     ===================================================================================

     Actor physics base class

     An actor typically uses one collision model which is aligned with the gravity
     direction. The collision model is usually a simple box with the origin at the
     bottom center.

     ===================================================================================
     */
    open class idPhysics_Actor : idPhysics_Base() {
        // CLASS_PROTOTYPE( idPhysics_Actor );
        protected var clipModel // clip model used for collision detection
                : idClipModel? = null
        protected var clipModelAxis // axis of clip model aligned with gravity direction
                : idMat3

        //
        // results of last evaluate
        protected val groundEntityPtr: idEntityPtr<idEntity?>?
        protected var invMass: Float

        //
        // derived properties
        protected var mass: Float
        protected var masterDeltaYaw: Float

        //
        // master
        protected var masterEntity: idEntity?
        protected var masterYaw: Float

        // ~idPhysics_Actor();
        override fun _deconstructor() {
            idClipModel.Companion.delete(clipModel)
            clipModel = null
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame?) {
            savefile.WriteClipModel(clipModel)
            savefile.WriteMat3(clipModelAxis)
            savefile.WriteFloat(mass)
            savefile.WriteFloat(invMass)
            savefile.WriteObject(masterEntity)
            savefile.WriteFloat(masterYaw)
            savefile.WriteFloat(masterDeltaYaw)
            groundEntityPtr.Save(savefile)
        }

        override fun Restore(savefile: idRestoreGame?) {
            savefile.ReadClipModel(clipModel)
            savefile.ReadMat3(clipModelAxis)
            mass = savefile.ReadFloat()
            invMass = savefile.ReadFloat()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/masterEntity)
            masterYaw = savefile.ReadFloat()
            masterDeltaYaw = savefile.ReadFloat()
            groundEntityPtr.Restore(savefile)
        }

        // get delta yaw of master
        fun GetMasterDeltaYaw(): Float {
            return masterDeltaYaw
        }

        // returns the ground entity
        fun GetGroundEntity(): idEntity? {
            return groundEntityPtr.GetEntity()
        }

        // align the clip model with the gravity direction
        fun SetClipModelAxis() {
            // align clip model to gravity direction
            if (gravityNormal.get(2) == -1.0f || gravityNormal == Vector.getVec3_zero()) {
                clipModelAxis.Identity()
            } else {
                clipModelAxis.set(2, gravityNormal.oNegative())
                clipModelAxis.get(2).NormalVectors(clipModelAxis.get(0), clipModelAxis.get(1))
                clipModelAxis.set(1, clipModelAxis.get(1).oNegative())
            }
            if (clipModel != null) {
                clipModel.Link(Game_local.gameLocal.clip, self, 0, clipModel.GetOrigin(), clipModelAxis)
            }
        }

        // common physics interface
        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {
            assert(self != null)
            assert(
                model != null // a clip model is required
            )
            assert(
                model.IsTraceModel() // and it should be a trace model
            )
            assert(
                density > 0.0f // density should be valid
            )
            if (clipModel != null && clipModel !== model && freeOld) {
                idClipModel.Companion.delete(clipModel)
            }
            clipModel = model
            clipModel.Link(Game_local.gameLocal.clip, self, 0, clipModel.GetOrigin(), clipModelAxis)
        }

        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return clipModel
        }

        override fun GetNumClipModels(): Int {
            return 1
        }

        override fun SetMass(_mass: Float, id: Int /*= -1*/) {
            assert(_mass > 0.0f)
            mass = _mass
            invMass = 1.0f / _mass
        }

        override fun GetMass(id: Int /*= -1*/): Float {
            return mass
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {
            clipModel.SetContents(contents)
        }

        override fun GetContents(id: Int /*= -1*/): Int {
            return clipModel.GetContents()
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds? {
            return clipModel.GetBounds()
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds? {
            return clipModel.GetAbsBounds()
        }

        override fun IsPushable(): Boolean {
            return masterEntity == null
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3? {
            return clipModel.GetOrigin()
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3? {
            return clipModel.GetAxis()
        }

        override fun SetGravity(newGravity: idVec3?) {
            if (newGravity != gravityVector) {
                super.SetGravity(newGravity)
                SetClipModelAxis()
            }
        }

        fun GetGravityAxis(): idMat3 {
            return clipModelAxis
        }

        override fun ClipTranslation(results: trace_s?, translation: idVec3?, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.TranslationModel(
                    results, clipModel.GetOrigin(), clipModel.GetOrigin().oPlus(translation),
                    clipModel, clipModel.GetAxis(), clipMask, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Translation(
                    results, clipModel.GetOrigin(), clipModel.GetOrigin().oPlus(translation),
                    clipModel, clipModel.GetAxis(), clipMask, self
                )
            }
        }

        override fun ClipRotation(results: trace_s?, rotation: idRotation?, model: idClipModel?) {
            if (model != null) {
                Game_local.gameLocal.clip.RotationModel(
                    results, clipModel.GetOrigin(), rotation,
                    clipModel, clipModel.GetAxis(), clipMask, model.Handle(), model.GetOrigin(), model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Rotation(
                    results, clipModel.GetOrigin(), rotation,
                    clipModel, clipModel.GetAxis(), clipMask, self
                )
            }
        }

        override fun ClipContents(model: idClipModel?): Int {
            return if (model != null) {
                Game_local.gameLocal.clip.ContentsModel(
                    clipModel.GetOrigin(),
                    clipModel,
                    clipModel.GetAxis(),
                    -1,
                    model.Handle(),
                    model.GetOrigin(),
                    model.GetAxis()
                )
            } else {
                Game_local.gameLocal.clip.Contents(clipModel.GetOrigin(), clipModel, clipModel.GetAxis(), -1, null)
            }
        }

        override fun DisableClip() {
            clipModel.Disable()
        }

        override fun EnableClip() {
            clipModel.Enable()
        }

        override fun UnlinkClip() {
            clipModel.Unlink()
        }

        override fun LinkClip() {
            clipModel.Link(Game_local.gameLocal.clip, self, 0, clipModel.GetOrigin(), clipModel.GetAxis())
        }

        override fun EvaluateContacts(): Boolean {

            // get all the ground contacts
            ClearContacts()
            AddGroundContacts(clipModel)
            AddContactEntitiesForContacts()
            return contacts.Num() != 0
        }

        //
        //
        init {
            clipModelAxis = idMat3()
            SetClipModelAxis()
            mass = 100.0f
            invMass = 1.0f / mass
            masterEntity = null
            masterYaw = 0.0f
            masterDeltaYaw = 0.0f
            groundEntityPtr = idEntityPtr(null)
        }
    }
}