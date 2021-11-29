package neo.Game.Physics

import neo.CM.CollisionModel.contactInfo_t
import neo.CM.CollisionModel.trace_s
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idEntityPtr
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.idlib.BV.Bounds
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Lib
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec6

/**
 *
 */
class Physics_Base {
    /*
     ===============================================================================

     Physics base for a moving object using one or more collision models.

     ===============================================================================
     */
    class contactEntity_t : idEntityPtr<idEntity?>()
    open class idPhysics_Base : idPhysics() {
        // CLASS_PROTOTYPE( idPhysics_Base );
        protected var clipMask // contents the physics object collides with
                = 0
        protected val contactEntities // entities touching this physics object
                : idList<contactEntity_t?>?
        protected val contacts // contacts with other physics objects
                : idList<contactInfo_t?>?
        protected val gravityNormal // normalized direction of gravity
                : idVec3?
        protected val gravityVector // direction and magnitude of gravity
                : idVec3?

        //
        //
        protected var self // entity using this physics object
                : idEntity? = null

        // ~idPhysics_Base( void );
        override fun _deconstructor() {
            if (self != null && self.GetPhysics() === this) {
                self.SetPhysics(null)
            }
            idForce.Companion.DeletePhysics(this)
            ClearContacts()
            super._deconstructor()
        }

        override fun Save(savefile: idSaveGame?) {
            var i: Int
            savefile.WriteObject(self)
            savefile.WriteInt(clipMask)
            savefile.WriteVec3(gravityVector)
            savefile.WriteVec3(gravityNormal)
            savefile.WriteInt(contacts.Num())
            i = 0
            while (i < contacts.Num()) {
                savefile.WriteContactInfo(contacts.oGet(i))
                i++
            }
            savefile.WriteInt(contactEntities.Num())
            i = 0
            while (i < contactEntities.Num()) {
                contactEntities.oGet(i).Save(savefile)
                i++
            }
        }

        override fun GetType(): Class<out idClass?>? {
            return this.javaClass
        }

        override fun Restore(savefile: idRestoreGame?) {
            var i: Int
            val num = CInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/self)
            clipMask = savefile.ReadInt()
            savefile.ReadVec3(gravityVector)
            savefile.ReadVec3(gravityNormal)
            savefile.ReadInt(num)
            contacts.SetNum(num.getVal())
            i = 0
            while (i < contacts.Num()) {
                savefile.ReadContactInfo(contacts.oGet(i))
                i++
            }
            savefile.ReadInt(num)
            contactEntities.SetNum(num.getVal())
            i = 0
            while (i < contactEntities.Num()) {
                contactEntities.oGet(i).Restore(savefile)
                i++
            }
        }

        // common physics interface
        override fun SetSelf(e: idEntity?) {
            assert(e != null)
            self = e
        }

        override fun SetClipModel(model: idClipModel?, density: Float, id: Int /*= 0*/, freeOld: Boolean /*= true*/) {}
        override fun GetClipModel(id: Int /*= 0*/): idClipModel? {
            return null
        }

        override fun GetNumClipModels(): Int {
            return 0
        }

        override fun SetMass(mass: Float, id: Int /*= -1*/) {}
        override fun GetMass(id: Int /*= -1*/): Float {
            return 0
        }

        override fun SetContents(contents: Int, id: Int /*= -1*/) {}
        override fun GetContents(id: Int /*= -1*/): Int {
            return 0
        }

        override fun SetClipMask(mask: Int, id: Int /*= -1*/) {
            clipMask = mask
        }

        override fun GetClipMask(id: Int /*= -1*/): Int {
            return clipMask
        }

        override fun GetBounds(id: Int /*= -1*/): idBounds? {
            return Bounds.bounds_zero
        }

        override fun GetAbsBounds(id: Int /*= -1*/): idBounds? {
            return Bounds.bounds_zero
        }

        override fun Evaluate(timeStepMSec: Int, endTimeMSec: Int): Boolean {
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
            return true
        }

        override fun SaveState() {}
        override fun RestoreState() {}
        override fun SetOrigin(newOrigin: idVec3?, id: Int /*= -1*/) {}
        override fun SetAxis(newAxis: idMat3?, id: Int /*= -1*/) {}
        override fun Translate(translation: idVec3?, id: Int /*= -1*/) {}
        override fun Translate(translation: idVec3?) {
            Translate(translation, -1)
        }

        override fun Rotate(rotation: idRotation?, id: Int /*= -1*/) {}
        override fun Rotate(rotation: idRotation?) {
            Rotate(rotation, -1)
        }

        override fun GetOrigin(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun GetAxis(id: Int /*= 0*/): idMat3? {
            return idMat3.Companion.getMat3_identity()
        }

        override fun SetLinearVelocity(newLinearVelocity: idVec3?, id: Int /*= 0*/) {}
        override fun SetAngularVelocity(newAngularVelocity: idVec3?, id: Int /*= 0*/) {}
        override fun GetLinearVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun GetAngularVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun SetGravity(newGravity: idVec3?) {
            gravityVector.oSet(newGravity)
            gravityNormal.oSet(newGravity)
            gravityNormal.Normalize()
        }

        override fun GetGravity(): idVec3? {
            return gravityVector
        }

        override fun GetGravityNormal(): idVec3? {
            return gravityNormal
        }

        override fun ClipTranslation(results: trace_s?, translation: idVec3?, model: idClipModel?) {
//	memset( &results, 0, sizeof( trace_t ) );
            results.fraction = 0.0f
            results.endAxis = idMat3()
            results.c = contactInfo_t()
        }

        override fun ClipRotation(results: trace_s?, rotation: idRotation?, model: idClipModel?) {
//	memset( &results, 0, sizeof( trace_t ) );
            //results = new trace_s();
            // wtf??
        }

        override fun ClipContents(model: idClipModel?): Int {
            return 0
        }

        override fun DisableClip() {}
        override fun EnableClip() {}
        override fun UnlinkClip() {}
        override fun LinkClip() {}
        override fun EvaluateContacts(): Boolean {
            return false
        }

        override fun GetNumContacts(): Int {
            return contacts.Num()
        }

        override fun GetContact(num: Int): contactInfo_t? {
            return contacts.oGet(num)
        }

        override fun ClearContacts() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contacts.Num()) {
                ent = Game_local.gameLocal.entities[contacts.oGet(i).entityNum]
                ent?.RemoveContactEntity(self)
                i++
            }
            contacts.SetNum(0, false)
        }

        override fun AddContactEntity(e: idEntity?) {
            var i: Int
            var ent: idEntity?
            var found = false
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities.oGet(i).GetEntity()
                if (ent == null) {
                    contactEntities.RemoveIndex(i--)
                }
                if (ent === e) {
                    found = true
                }
                i++
            }
            if (!found) {
                contactEntities.Alloc().oSet(e)
            }
        }

        override fun RemoveContactEntity(e: idEntity?) {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities.oGet(i).GetEntity()
                if (null == ent) {
                    contactEntities.RemoveIndex(i--)
                    i++
                    continue
                }
                if (ent === e) {
                    contactEntities.RemoveIndex(i--)
                    return
                }
                i++
            }
        }

        override fun HasGroundContacts(): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts.oGet(i).normal.times(gravityNormal.oNegative()) > 0.0f) {
                    return true
                }
                i++
            }
            return false
        }

        override fun IsGroundEntity(entityNum: Int): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts.oGet(i).entityNum == entityNum && contacts.oGet(i).normal.times(gravityNormal.oNegative()) > 0.0f) {
                    return true
                }
                i++
            }
            return false
        }

        override fun IsGroundClipModel(entityNum: Int, id: Int): Boolean {
            var i: Int
            i = 0
            while (i < contacts.Num()) {
                if (contacts.oGet(i).entityNum == entityNum && contacts.oGet(i).id == id && contacts.oGet(i).normal.times(
                        gravityNormal.oNegative()
                    ) > 0.0f
                ) {
                    return true
                }
                i++
            }
            return false
        }

        override fun SetPushed(deltaTime: Int) {}
        override fun GetPushedLinearVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun GetPushedAngularVelocity(id: Int /*= 0*/): idVec3? {
            return Vector.getVec3_origin()
        }

        override fun SetMaster(master: idEntity?, orientated: Boolean /*= true*/) {}
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

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {}
        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {}

        // add ground contacts for the clip model
        protected fun AddGroundContacts(clipModel: idClipModel?) {
            val dir = idVec6()
            val index: Int
            val num: Int
            index = contacts.Num()
            contacts.SetNum(index + 10, false)
            val contactz = arrayOfNulls<contactInfo_t?>(10)
            dir.SubVec3_oSet(0, gravityNormal)
            dir.SubVec3_oSet(1, Vector.getVec3_origin())
            num = Game_local.gameLocal.clip.Contacts(
                contactz,
                10,
                clipModel.GetOrigin(),
                dir,
                Physics.CONTACT_EPSILON,
                clipModel,
                clipModel.GetAxis(),
                clipMask,
                self
            )
            for (i in 0 until num) {
                contacts.oSet(index + i, contactz[i])
            }
            contacts.SetNum(index + num, false)
        }

        // add contact entity links to contact entities
        protected fun AddContactEntitiesForContacts() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contacts.Num()) {
                ent = Game_local.gameLocal.entities[contacts.oGet(i).entityNum]
                if (ent != null && ent != self) {
                    ent.AddContactEntity(self)
                }
                i++
            }
        }

        // active all contact entities
        protected fun ActivateContactEntities() {
            var i: Int
            var ent: idEntity?
            i = 0
            while (i < contactEntities.Num()) {
                ent = contactEntities.oGet(i).GetEntity()
                ent?.ActivatePhysics(self) ?: contactEntities.RemoveIndex(i--)
                i++
            }
        }

        // returns true if the whole physics object is outside the world bounds
        protected fun IsOutsideWorld(): Boolean {
            return !Game_local.gameLocal.clip.GetWorldBounds().Expand(128.0f).IntersectsBounds(GetAbsBounds())
        }

        // draw linear and angular velocity
        protected fun DrawVelocity(id: Int, linearScale: Float, angularScale: Float) {
            val dir = idVec3()
            val org = idVec3()
            val vec = idVec3()
            val start = idVec3()
            val end = idVec3()
            val axis: idMat3?
            var length: Float
            var a: Float
            dir.oSet(GetLinearVelocity(id))
            dir.timesAssign(linearScale)
            if (dir.LengthSqr() > Math_h.Square(0.1f)) {
                dir.Truncate(10.0f)
                org.oSet(GetOrigin(id))
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorRed, org, org.oPlus(dir), 1)
            }
            dir.oSet(GetAngularVelocity(id))
            length = dir.Normalize()
            length *= angularScale
            if (length > 0.1f) {
                if (length < 60.0f) {
                    length = 60.0f
                } else if (length > 360.0f) {
                    length = 360.0f
                }
                axis = GetAxis(id)
                vec.oSet(axis.oGet(2))
                if (Math.abs(dir.times(vec)) > 0.99f) {
                    vec.oSet(axis.oGet(0))
                }
                vec.minusAssign(vec.times(dir.times(vec)))
                vec.Normalize()
                vec.timesAssign(4.0f)
                start.oSet(org.oPlus(vec))
                a = 20.0f
                while (a < length) {
                    end.oSet(org.oPlus(idRotation(Vector.getVec3_origin(), dir, -a).ToMat3().times(vec)))
                    Game_local.gameRenderWorld.DebugLine(Lib.Companion.colorBlue, start, end, 1)
                    start.oSet(end)
                    a += 20.0f
                }
                end.oSet(org.oPlus(idRotation(Vector.getVec3_origin(), dir, -length).ToMat3().times(vec)))
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorBlue, start, end, 1)
            }
        }

        override fun CreateInstance(): idClass? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        init {
            contacts = idList(contactInfo_t::class.java)
            contactEntities = idList(contactEntity_t::class.java)
            //SetGravity(gameLocal.GetGravity());
            gravityVector = idVec3(Game_local.gameLocal.GetGravity())
            gravityNormal = idVec3(Game_local.gameLocal.GetGravity())
            gravityNormal.Normalize()
            ClearContacts()
        }
    }
}