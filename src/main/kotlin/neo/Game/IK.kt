package neo.Game

import neo.CM.CollisionModel.trace_s
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.idGameLocal
import neo.Game.Mover.idPlat
import neo.Game.Physics.Clip.idClipModel
import neo.Renderer.Material
import neo.Renderer.Model
import neo.Renderer.Model.idRenderModel
import neo.idlib.Lib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3
import kotlin.math.abs

/**
 *
 */
object IK /*ea*/ {
    /*
     ===============================================================================

     IK base class with a simple fast two bone solver.

     ===============================================================================
     */
    val IK_ANIM: String = "ik_pose"

    /*
     ===============================================================================

     idIK

     ===============================================================================
     */
    open class idIK {
        protected val modelOffset: idVec3
        protected var animator // animator on entity
                : idAnimator? = null
        protected var ik_activate = false
        protected var initialized = false
        protected var modifiedAnim // animation modified by the IK
                = 0
        protected var self // entity using the animated model
                : idEntity? = null

        // virtual					~idIK( void );
        open fun Save(savefile: idSaveGame) {
            savefile.WriteBool(initialized)
            savefile.WriteBool(ik_activate)
            savefile.WriteObject(self!!)
            savefile.WriteString(
                if (animator != null && animator!!.GetAnim(modifiedAnim) != null) animator!!.GetAnim(
                    modifiedAnim
                )!!.Name() else ""
            )
            savefile.WriteVec3(modelOffset)
        }

        open fun Restore(savefile: idRestoreGame) {
            val anim = idStr()
            initialized = savefile.ReadBool()
            ik_activate = savefile.ReadBool()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/self)
            savefile.ReadString(anim)
            savefile.ReadVec3(modelOffset)
            if (self != null) {
                animator = self!!.GetAnimator()
                if (animator == null || animator!!.ModelDef() == null) {
                    Game_local.gameLocal.Warning(
                        "idIK::Restore: IK for entity '%s' at (%s) has no model set.",
                        self!!.name, self!!.GetPhysics().GetOrigin().ToString(0)
                    )
                }
                modifiedAnim = animator!!.GetAnim(anim.toString())
                if (modifiedAnim == 0) {
                    Game_local.gameLocal.Warning(
                        "idIK::Restore: IK for entity '%s' at (%s) has no modified animation.",
                        self!!.name, self!!.GetPhysics().GetOrigin().ToString(0)
                    )
                }
            } else {
                animator = null
                modifiedAnim = 0
            }
        }

        fun IsInitialized(): Boolean {
            return initialized && SysCvar.ik_enable.GetBool()
        }

        open fun Init(self: idEntity?, anim: String, modelOffset: idVec3): Boolean {
            val model: idRenderModel? //TODO:finalize objects that can be finalized. hint <- <- <-
            if (self == null) {
                return false
            }
            this.self = self
            animator = self.GetAnimator()
            if (animator == null || animator!!.ModelDef() == null) {
                Game_local.gameLocal.Warning(
                    "idIK::Init: IK for entity '%s' at (%s) has no model set.",
                    self.name, self.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            if (animator!!.ModelDef()!!.ModelHandle() == null) {
                Game_local.gameLocal.Warning(
                    "idIK::Init: IK for entity '%s' at (%s) uses default model.",
                    self.name, self.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            model = animator!!.ModelHandle()
            if (model == null) {
                Game_local.gameLocal.Warning(
                    "idIK::Init: IK for entity '%s' at (%s) has no model set.",
                    self.name, self.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            modifiedAnim = animator!!.GetAnim(anim)
            if (modifiedAnim == 0) {
                Game_local.gameLocal.Warning(
                    "idIK::Init: IK for entity '%s' at (%s) has no modified animation.",
                    self.name, self.GetPhysics().GetOrigin().ToString(0)
                )
                return false
            }
            this.modelOffset.set(modelOffset)
            return true
        }

        open fun Evaluate() {}
        open fun ClearJointMods() {
            ik_activate = false
        }

        fun SolveTwoBones(
            startPos: idVec3,
            endPos: idVec3,
            dir: idVec3,
            len0: Float,
            len1: Float,
            jointPos: idVec3
        ): Boolean {
            val length: Float
            val lengthSqr: Float
            val lengthInv: Float
            val x: Float
            val y: Float
            val vec0 = idVec3()
            val vec1 = idVec3()
            vec0.set(endPos.minus(startPos))
            lengthSqr = vec0.LengthSqr()
            lengthInv = idMath.InvSqrt(lengthSqr)
            length = lengthInv * lengthSqr

            // if the start and end position are too far out or too close to each other
            if (length > len0 + len1 || length < abs(len0 - len1)) {
                jointPos.set(startPos.plus(vec0.times(0.5f)))
                return false
            }
            vec0.timesAssign(lengthInv)
            vec1.set(dir.minus(vec0.times(dir.times(vec0))))
            vec1.Normalize()
            x = (length * length + len0 * len0 - len1 * len1) * (0.5f * lengthInv)
            y = idMath.Sqrt(len0 * len0 - x * x)
            jointPos.set(startPos.plus(vec0.times(x).plus(vec1.times(y))))
            return true
        }

        fun GetBoneAxis(startPos: idVec3, endPos: idVec3, dir: idVec3, axis: idMat3): Float {
            val length: Float
            axis[0] = endPos.minus(startPos)
            length = axis[0].Normalize()
            axis[1] = dir.minus(axis[0].times(dir.times(axis[0])))
            axis[1].Normalize()
            axis[2].Cross(axis[1], axis[0])
            return length
        }

        //
        //
        init {
            modelOffset = idVec3()
        }
    }

    /*
     ===============================================================================

     IK controller for a walking character with an arbitrary number of legs.	

     ===============================================================================
     */
    /*
     ===============================================================================

     idIK_Walk

     ===============================================================================
     */
    class idIK_Walk : idIK() {
        private val ankleJoints: IntArray = IntArray(MAX_LEGS)
        private val dirJoints: IntArray = IntArray(MAX_LEGS)
        private val footJoints: IntArray = IntArray(MAX_LEGS)

        //
        private val hipForward: Array<idVec3> = idVec3.generateArray(MAX_LEGS)
        private val hipJoints: IntArray = IntArray(MAX_LEGS)
        private val kneeForward: Array<idVec3> = idVec3.generateArray(MAX_LEGS)
        private val kneeJoints: IntArray = IntArray(MAX_LEGS)
        private val lowerLegLength: FloatArray = FloatArray(MAX_LEGS)
        private val lowerLegToKneeJoint: Array<idMat3> = Array<idMat3>(MAX_LEGS) { idMat3() }
        private val oldAnkleHeights: FloatArray = FloatArray(MAX_LEGS)
        private val pivotPos: idVec3

        //
        private val upperLegLength: FloatArray = FloatArray(MAX_LEGS)

        //
        private val upperLegToHipJoint: Array<idMat3> = Array(MAX_LEGS) { idMat3() }
        private val waistOffset: idVec3
        private var enabledLegs: Int
        private var footDownTrace: Float

        //
        private var footModel: idClipModel?
        private var footShift: Float
        private var footUpTrace: Float
        private var minWaistAnkleDist: Float
        private var minWaistFloorDist: Float

        //
        private var numLegs: Int
        private var oldHeightsValid: Boolean
        private var oldWaistHeight: Float

        //
        // state
        private var pivotFoot: Int
        private var pivotYaw: Float

        //
        private var smoothing: Float
        private var tiltWaist: Boolean
        private var usePivot: Boolean
        private var   /*jointHandle_t*/waistJoint: Int
        private var waistShift: Float

        //
        //
        private var waistSmoothing: Float
        override fun Save(savefile: idSaveGame) {
            var i: Int
            super.Save(savefile)
            savefile.WriteClipModel(footModel)
            savefile.WriteInt(numLegs)
            savefile.WriteInt(enabledLegs)
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteInt(footJoints[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteInt(ankleJoints[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteInt(kneeJoints[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteInt(hipJoints[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteInt(dirJoints[i])
                i++
            }
            savefile.WriteInt(waistJoint)
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteVec3(hipForward[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteVec3(kneeForward[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteFloat(upperLegLength[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteFloat(lowerLegLength[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteMat3(upperLegToHipJoint[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteMat3(lowerLegToKneeJoint[i])
                i++
            }
            savefile.WriteFloat(smoothing)
            savefile.WriteFloat(waistSmoothing)
            savefile.WriteFloat(footShift)
            savefile.WriteFloat(waistShift)
            savefile.WriteFloat(minWaistFloorDist)
            savefile.WriteFloat(minWaistAnkleDist)
            savefile.WriteFloat(footUpTrace)
            savefile.WriteFloat(footDownTrace)
            savefile.WriteBool(tiltWaist)
            savefile.WriteBool(usePivot)
            savefile.WriteInt(pivotFoot)
            savefile.WriteFloat(pivotYaw)
            savefile.WriteVec3(pivotPos)
            savefile.WriteBool(oldHeightsValid)
            savefile.WriteFloat(oldWaistHeight)
            i = 0
            while (i < MAX_LEGS) {
                savefile.WriteFloat(oldAnkleHeights[i])
                i++
            }
            savefile.WriteVec3(waistOffset)
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            super.Restore(savefile)
            savefile.ReadClipModel(footModel!!)
            numLegs = savefile.ReadInt()
            enabledLegs = savefile.ReadInt()
            i = 0
            while (i < MAX_LEGS) {
                footJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                ankleJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                kneeJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                hipJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                dirJoints[i] = savefile.ReadInt()
                i++
            }
            waistJoint = savefile.ReadInt()
            i = 0
            while (i < MAX_LEGS) {
                savefile.ReadVec3(hipForward[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.ReadVec3(kneeForward[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                upperLegLength[i] = savefile.ReadFloat()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                lowerLegLength[i] = savefile.ReadFloat()
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.ReadMat3(upperLegToHipJoint[i])
                i++
            }
            i = 0
            while (i < MAX_LEGS) {
                savefile.ReadMat3(lowerLegToKneeJoint[i])
                i++
            }
            smoothing = savefile.ReadFloat()
            waistSmoothing = savefile.ReadFloat()
            footShift = savefile.ReadFloat()
            waistShift = savefile.ReadFloat()
            minWaistFloorDist = savefile.ReadFloat()
            minWaistAnkleDist = savefile.ReadFloat()
            footUpTrace = savefile.ReadFloat()
            footDownTrace = savefile.ReadFloat()
            tiltWaist = savefile.ReadBool()
            usePivot = savefile.ReadBool()
            pivotFoot = savefile.ReadInt()
            pivotYaw = savefile.ReadFloat()
            savefile.ReadVec3(pivotPos)
            oldHeightsValid = savefile.ReadBool()
            oldWaistHeight = savefile.ReadFloat()
            i = 0
            while (i < MAX_LEGS) {
                oldAnkleHeights[i] = savefile.ReadFloat()
                i++
            }
            savefile.ReadVec3(waistOffset)
        }

        override fun Init(self: idEntity?, anim: String, modelOffset: idVec3): Boolean {
            var i: Int
            val footSize: Float
            val verts: Array<idVec3> = idVec3.generateArray(4)
            val trm = idTraceModel()
            var jointName: String?
            val dir = idVec3()
            val ankleOrigin = idVec3()
            val kneeOrigin = idVec3()
            val hipOrigin = idVec3()
            val dirOrigin = idVec3()
            val axis = idMat3()
            var ankleAxis: idMat3
            var kneeAxis: idMat3
            var hipAxis: idMat3
            if (null == self) {
                return false
            }
            numLegs = Lib.Min(self.spawnArgs.GetInt("ik_numLegs", "0"), MAX_LEGS)
            if (numLegs == 0) {
                return true
            }
            if (!super.Init(self, anim, modelOffset)) {
                return false
            }
            val numJoints = animator!!.NumJoints()
            val joints = Array(numJoints) { idJointMat() }

            // create the animation frame used to setup the IK
            GameEdit.gameEdit.ANIM_CreateAnimFrame(
                animator!!.ModelHandle(),
                animator!!.GetAnim(modifiedAnim)!!.MD5Anim(0),
                numJoints,
                joints,
                1,
                animator!!.ModelDef()!!.GetVisualOffset().plus(modelOffset),
                animator!!.RemoveOrigin()
            )
            enabledLegs = 0

            // get all the joints
            i = 0
            while (i < numLegs) {
                jointName = self.spawnArgs.GetString(Str.va("ik_foot%d", i + 1))
                footJoints[i] = animator!!.GetJointHandle(jointName)
                if (footJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Walk::Init: invalid foot joint '%s'", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_ankle%d", i + 1))
                ankleJoints[i] = animator!!.GetJointHandle(jointName)
                if (ankleJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Walk::Init: invalid ankle joint '%s'", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_knee%d", i + 1))
                kneeJoints[i] = animator!!.GetJointHandle(jointName)
                if (kneeJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Walk::Init: invalid knee joint '%s'\n", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_hip%d", i + 1))
                hipJoints[i] = animator!!.GetJointHandle(jointName)
                if (hipJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Walk::Init: invalid hip joint '%s'\n", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_dir%d", i + 1))
                dirJoints[i] = animator!!.GetJointHandle(jointName)
                enabledLegs = enabledLegs or (1 shl i)
                i++
            }
            jointName = self.spawnArgs.GetString("ik_waist")
            waistJoint = animator!!.GetJointHandle(jointName)
            if (waistJoint == Model.INVALID_JOINT) {
                idGameLocal.Error("idIK_Walk::Init: invalid waist joint '%s'\n", jointName)
            }

            // get the leg bone lengths and rotation matrices
            i = 0
            while (i < numLegs) {
                oldAnkleHeights[i] = 0f
                ankleAxis = joints[ankleJoints[i]].ToMat3()
                ankleOrigin.set(joints[ankleJoints[i]].ToVec3())
                kneeAxis = joints[kneeJoints[i]].ToMat3()
                kneeOrigin.set(joints[kneeJoints[i]].ToVec3())
                hipAxis = joints[hipJoints[i]].ToMat3()
                hipOrigin.set(joints[hipJoints[i]].ToVec3())

                // get the IK direction
                if (dirJoints[i] != Model.INVALID_JOINT) {
                    dirOrigin.set(joints[dirJoints[i]].ToVec3())
                    dir.set(dirOrigin.minus(kneeOrigin))
                } else {
                    dir.set(1.0f, 0f, 0f)
                }
                hipForward[i].set(dir.times(hipAxis.Transpose()))
                kneeForward[i].set(dir.times(kneeAxis.Transpose()))

                // conversion from upper leg bone axis to hip joint axis
                upperLegLength[i] = GetBoneAxis(hipOrigin, kneeOrigin, dir, axis)
                upperLegToHipJoint[i] = hipAxis.times(axis.Transpose())

                // conversion from lower leg bone axis to knee joint axis
                lowerLegLength[i] = GetBoneAxis(kneeOrigin, ankleOrigin, dir, axis)
                lowerLegToKneeJoint[i] = kneeAxis.times(axis.Transpose())
                i++
            }
            smoothing = self.spawnArgs.GetFloat("ik_smoothing", "0.75")
            waistSmoothing = self.spawnArgs.GetFloat("ik_waistSmoothing", "0.75")
            footShift = self.spawnArgs.GetFloat("ik_footShift", "0")
            waistShift = self.spawnArgs.GetFloat("ik_waistShift", "0")
            minWaistFloorDist = self.spawnArgs.GetFloat("ik_minWaistFloorDist", "0")
            minWaistAnkleDist = self.spawnArgs.GetFloat("ik_minWaistAnkleDist", "0")
            footUpTrace = self.spawnArgs.GetFloat("ik_footUpTrace", "32")
            footDownTrace = self.spawnArgs.GetFloat("ik_footDownTrace", "32")
            tiltWaist = self.spawnArgs.GetBool("ik_tiltWaist", "0")
            usePivot = self.spawnArgs.GetBool("ik_usePivot", "0")

            // setup a clip model for the feet
            footSize = self.spawnArgs.GetFloat("ik_footSize", "4") * 0.5f
            if (footSize > 0) {
                i = 0
                while (i < 4) {
                    verts[i].set(footWinding[i].times(footSize))
                    i++
                }
                trm.SetupPolygon(verts, 4)
                footModel = idClipModel(trm)
            }
            initialized = true
            return true
        }

        override fun Evaluate() {
            var i: Int
            var newPivotFoot = 0
            val modelHeight: Float
            var jointHeight: Float
            var lowestHeight: Float
            val floorHeights = FloatArray(MAX_LEGS)
            var shift: Float
            var smallestShift: Float
            var newHeight: Float
            var step: Float
            val newPivotYaw: Float
            var height: Float
            var largestAnkleHeight: Float
            val modelOrigin = idVec3()
            val normal = idVec3()
            val hipDir = idVec3()
            val kneeDir = idVec3()
            val start = idVec3()
            val end = idVec3()
            val jointOrigins: Array<idVec3> = idVec3.generateArray(MAX_LEGS)
            val footOrigin = idVec3()
            val ankleOrigin = idVec3()
            val kneeOrigin = idVec3()
            val hipOrigin = idVec3()
            val waistOrigin = idVec3()
            val modelAxis: idMat3
            val waistAxis = idMat3()
            val axis = idMat3()
            val hipAxis = Array<idMat3>(MAX_LEGS) { idMat3() }
            val kneeAxis = Array<idMat3>(MAX_LEGS) { idMat3() }
            val ankleAxis = Array<idMat3>(MAX_LEGS) { idMat3() }
            val results = trace_s()
            if (null == self || !Game_local.gameLocal.isNewFrame) {
                return
            }

            // if no IK enabled on any legs
            if (0 == enabledLegs) { //TODO:make booleans out of ints that are boolean anyways. damn you C programmers!!
                return
            }
            normal.set(self!!.GetPhysics().GetGravityNormal().unaryMinus())
            modelOrigin.set(self!!.GetPhysics().GetOrigin())
            modelAxis = self!!.GetRenderEntity().axis
            modelHeight = modelOrigin.times(normal)
            modelOrigin.plusAssign(modelOffset.times(modelAxis))

            // create frame without joint mods
            animator!!.CreateFrame(Game_local.gameLocal.time, false)

            // get the joint positions for the feet
            lowestHeight = idMath.INFINITY
            i = 0
            while (i < numLegs) {
                animator!!.GetJointTransform(footJoints[i], Game_local.gameLocal.time, footOrigin, axis)
                jointOrigins[i].set(modelOrigin.plus(footOrigin.times(modelAxis)))
                jointHeight = jointOrigins[i].times(normal)
                if (jointHeight < lowestHeight) {
                    lowestHeight = jointHeight
                    newPivotFoot = i
                }
                i++
            }
            if (usePivot) {
                newPivotYaw = modelAxis[0].ToYaw()

                // change pivot foot
                if (newPivotFoot != pivotFoot || abs(idMath.AngleNormalize180(newPivotYaw - pivotYaw)) > 30.0f) {
                    pivotFoot = newPivotFoot
                    pivotYaw = newPivotYaw
                    animator!!.GetJointTransform(footJoints[pivotFoot], Game_local.gameLocal.time, footOrigin, axis)
                    pivotPos.set(modelOrigin.plus(footOrigin.times(modelAxis)))
                }

                // keep pivot foot in place
                jointOrigins[pivotFoot].set(pivotPos)
            }

            // get the floor heights for the feet
            i = 0
            while (i < numLegs) {
                if (0 == enabledLegs and (1 shl i)) {
                    i++
                    continue
                }
                start.set(jointOrigins[i].plus(normal.times(footUpTrace)))
                end.set(jointOrigins[i].minus(normal.times(footDownTrace)))
                Game_local.gameLocal.clip.Translation(
                    results,
                    start,
                    end,
                    footModel,
                    idMat3.getMat3_identity(),
                    Material.CONTENTS_SOLID or Material.CONTENTS_IKCLIP,
                    self
                )
                floorHeights[i] = results.endpos.times(normal)
                if (SysCvar.ik_debug.GetBool() && footModel != null) {
                    val w = idFixedWinding()
                    for (j in 0 until footModel!!.GetTraceModel()!!.numVerts) {
                        w.plusAssign(footModel!!.GetTraceModel()!!.verts[j])
                    }
                    Game_local.gameRenderWorld.DebugWinding(Lib.colorRed, w, results.endpos, results.endAxis)
                }
                i++
            }
            val phys = self!!.GetPhysics()

            // test whether or not the character standing on the ground
            val onGround = phys.HasGroundContacts()

            // test whether or not the character is standing on a plat
            var onPlat = false
            i = 0
            while (i < phys.GetNumContacts()) {
                val ent = Game_local.gameLocal.entities[phys.GetContact(i)!!.entityNum]
                if (ent != null && ent is idPlat) {
                    onPlat = true
                    break
                }
                i++
            }

            // adjust heights of the ankles
            smallestShift = idMath.INFINITY
            largestAnkleHeight = -idMath.INFINITY
            i = 0
            while (i < numLegs) {
                shift = if (onGround && enabledLegs and (1 shl i) != 0) {
                    floorHeights[i] - modelHeight + footShift
                } else {
                    0f
                }
                if (shift < smallestShift) {
                    smallestShift = shift
                }
                ankleAxis[i] = idMat3()
                animator!!.GetJointTransform(ankleJoints[i], Game_local.gameLocal.time, ankleOrigin, ankleAxis[i])
                jointOrigins[i] = modelOrigin.plus(ankleOrigin.times(modelAxis))
                height = jointOrigins[i].times(normal)
                if (oldHeightsValid && !onPlat) {
                    step = height + shift - oldAnkleHeights[i]
                    shift -= smoothing * step
                }
                newHeight = height + shift
                if (newHeight > largestAnkleHeight) {
                    largestAnkleHeight = newHeight
                }
                oldAnkleHeights[i] = newHeight
                jointOrigins[i].plusAssign(normal.times(shift))
                i++
            }
            animator!!.GetJointTransform(waistJoint, Game_local.gameLocal.time, waistOrigin, waistAxis)
            waistOrigin.set(modelOrigin.plus(waistOrigin.times(modelAxis)))

            // adjust position of the waist
            waistOffset.set(normal.times(smallestShift + waistShift))

            // if the waist should be at least a certain distance above the floor
            if (minWaistFloorDist > 0 && waistOffset.times(normal) < 0) {
                start.set(waistOrigin)
                end.set(waistOrigin.plus(waistOffset.minus(normal.times(minWaistFloorDist))))
                Game_local.gameLocal.clip.Translation(
                    results,
                    start,
                    end,
                    footModel,
                    modelAxis,
                    Material.CONTENTS_SOLID or Material.CONTENTS_IKCLIP,
                    self
                )
                height = waistOrigin.plus(waistOffset.minus(results.endpos)).times(normal)
                if (height < minWaistFloorDist) {
                    waistOffset.plusAssign(normal.times(minWaistFloorDist - height))
                }
            }

            // if the waist should be at least a certain distance above the ankles
            if (minWaistAnkleDist > 0) {
                height = waistOrigin.plus(waistOffset).times(normal)
                if (height - largestAnkleHeight < minWaistAnkleDist) {
                    waistOffset.plusAssign(normal.times(minWaistAnkleDist - (height - largestAnkleHeight)))
                }
            }
            if (oldHeightsValid) {
                // smoothly adjust height of waist
                newHeight = waistOrigin.plus(waistOffset).times(normal)
                step = newHeight - oldWaistHeight
                waistOffset.minusAssign(normal.times(waistSmoothing * step))
            }

            // save height of waist for smoothing
            oldWaistHeight = waistOrigin.plus(waistOffset).times(normal)
            if (!oldHeightsValid) {
                oldHeightsValid = true
                return
            }

            // solve IK
            i = 0
            while (i < numLegs) {


                // get the position of the hip in world space
                animator!!.GetJointTransform(hipJoints[i], Game_local.gameLocal.time, hipOrigin, axis)
                hipOrigin.set(modelOrigin.plus(waistOffset.plus(hipOrigin.times(modelAxis))))
                hipDir.set(hipForward[i].times(axis.times(modelAxis)))

                // get the IK bend direction
                animator!!.GetJointTransform(kneeJoints[i], Game_local.gameLocal.time, kneeOrigin, axis)
                kneeDir.set(kneeForward[i].times(axis.times(modelAxis)))

                // solve IK and calculate knee position
                SolveTwoBones(
                    hipOrigin,
                    jointOrigins[i],
                    kneeDir,
                    upperLegLength[i],
                    lowerLegLength[i],
                    kneeOrigin
                )
                if (SysCvar.ik_debug.GetBool()) {
                    Game_local.gameRenderWorld.DebugLine(Lib.colorCyan, hipOrigin, kneeOrigin)
                    Game_local.gameRenderWorld.DebugLine(Lib.colorRed, kneeOrigin, jointOrigins[i])
                    Game_local.gameRenderWorld.DebugLine(
                        Lib.colorYellow,
                        kneeOrigin,
                        kneeOrigin.plus(hipDir)
                    )
                    Game_local.gameRenderWorld.DebugLine(
                        Lib.colorGreen,
                        kneeOrigin,
                        kneeOrigin.plus(kneeDir)
                    )
                }

                // get the axis for the hip joint
                GetBoneAxis(hipOrigin, kneeOrigin, hipDir, axis)
                hipAxis[i] = upperLegToHipJoint[i].times(axis.times(modelAxis.Transpose()))

                // get the axis for the knee joint
                GetBoneAxis(kneeOrigin, jointOrigins[i], kneeDir, axis)
                kneeAxis[i] = lowerLegToKneeJoint[i].times(axis.times(modelAxis.Transpose()))
                i++
            }

            // set the joint mods
            animator!!.SetJointAxis(waistJoint, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, waistAxis)
            animator!!.SetJointPos(
                waistJoint,
                jointModTransform_t.JOINTMOD_WORLD_OVERRIDE,
                waistOrigin.plus(waistOffset.minus(modelOrigin)).times(modelAxis.Transpose())
            )
            i = 0
            while (i < numLegs) {
                animator!!.SetJointAxis(hipJoints[i], jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, hipAxis[i])
                animator!!.SetJointAxis(kneeJoints[i], jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, kneeAxis[i])
                animator!!.SetJointAxis(ankleJoints[i], jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, ankleAxis[i])
                i++
            }
            ik_activate = true
        }

        override fun ClearJointMods() {
            var i: Int
            if (null == self || !ik_activate) {
                return
            }
            animator!!.SetJointAxis(waistJoint, jointModTransform_t.JOINTMOD_NONE, idMat3.getMat3_identity())
            animator!!.SetJointPos(waistJoint, jointModTransform_t.JOINTMOD_NONE, Vector.getVec3_origin())
            i = 0
            while (i < numLegs) {
                animator!!.SetJointAxis(
                    hipJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                animator!!.SetJointAxis(
                    kneeJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                animator!!.SetJointAxis(
                    ankleJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                i++
            }
            ik_activate = false
        }

        fun EnableAll() {
            enabledLegs = (1 shl numLegs) - 1
            oldHeightsValid = false
        }

        fun DisableAll() {
            enabledLegs = 0
            oldHeightsValid = false
        }

        fun EnableLeg(num: Int) {
            enabledLegs = enabledLegs or (1 shl num)
        }

        fun DisableLeg(num: Int) {
            enabledLegs = enabledLegs and (1 shl num).inv()
        }

        companion object {
            private const val MAX_LEGS = 8
            private val footWinding /*[4]*/: Array<idVec3> = arrayOf(
                idVec3(1.0f, 1.0f, 0f),
                idVec3(-1.0f, 1.0f, 0f),
                idVec3(-1.0f, -1.0f, 0f),
                idVec3(1.0f, -1.0f, 0f)
            )
        }

        // virtual					~idIK_Walk( void );
        init {
            var i: Int
            initialized = false
            footModel = null
            numLegs = 0
            enabledLegs = 0
            i = 0
            while (i < MAX_LEGS) {
                footJoints[i] = Model.INVALID_JOINT
                ankleJoints[i] = Model.INVALID_JOINT
                kneeJoints[i] = Model.INVALID_JOINT
                hipJoints[i] = Model.INVALID_JOINT
                dirJoints[i] = Model.INVALID_JOINT
                upperLegLength[i] = 0f
                lowerLegLength[i] = 0f
                upperLegToHipJoint[i] = idMat3.getMat3_identity()
                lowerLegToKneeJoint[i] = idMat3.getMat3_identity()
                oldAnkleHeights[i] = 0f
                i++
            }
            waistJoint = Model.INVALID_JOINT
            smoothing = 0.75f
            waistSmoothing = 0.5f
            footShift = 0f
            waistShift = 0f
            minWaistFloorDist = 0f
            minWaistAnkleDist = 0f
            footUpTrace = 32.0f
            footDownTrace = 32.0f
            tiltWaist = false
            usePivot = false
            pivotFoot = -1
            pivotYaw = 0f
            pivotPos = idVec3()
            oldHeightsValid = false
            oldWaistHeight = 0f
            waistOffset = idVec3()
        }
    }

    /*
     ===============================================================================

     IK controller for reaching a position with an arm or leg.

     ===============================================================================
     */
    /*
     ===============================================================================

     idIK_Reach

     ===============================================================================
     */
    class idIK_Reach : idIK() {
        private val dirJoints: IntArray = IntArray(MAX_ARMS)
        private val elbowForward: Array<idVec3> = idVec3.generateArray(MAX_ARMS)
        private val elbowJoints: IntArray = IntArray(MAX_ARMS)
        private val handJoints: IntArray = IntArray(MAX_ARMS)
        private val lowerArmLength: FloatArray = FloatArray(MAX_ARMS)
        private val lowerArmToElbowJoint: Array<idMat3> = Array<idMat3>(MAX_ARMS) { idMat3() }

        //
        private val shoulderForward: Array<idVec3> = idVec3.generateArray(MAX_ARMS)
        private val shoulderJoints: IntArray = IntArray(MAX_ARMS)

        //
        private val upperArmLength: FloatArray = FloatArray(MAX_ARMS)

        //
        private val upperArmToShoulderJoint: Array<idMat3> = Array<idMat3>(MAX_ARMS) { idMat3() }
        private var enabledArms: Int

        //
        private var numArms: Int

        // virtual					~idIK_Reach( void );
        override fun Save(savefile: idSaveGame) {
            var i: Int
            super.Save(savefile)
            savefile.WriteInt(numArms)
            savefile.WriteInt(enabledArms)
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteInt(handJoints[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteInt(elbowJoints[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteInt(shoulderJoints[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteInt(dirJoints[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteVec3(shoulderForward[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteVec3(elbowForward[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteFloat(upperArmLength[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteFloat(lowerArmLength[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteMat3(upperArmToShoulderJoint[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.WriteMat3(lowerArmToElbowJoint[i])
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            super.Restore(savefile)
            numArms = savefile.ReadInt()
            enabledArms = savefile.ReadInt()
            i = 0
            while (i < MAX_ARMS) {
                handJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                elbowJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                shoulderJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                dirJoints[i] = savefile.ReadInt()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.ReadVec3(shoulderForward[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.ReadVec3(elbowForward[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                upperArmLength[i] = savefile.ReadFloat()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                lowerArmLength[i] = savefile.ReadFloat()
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.ReadMat3(upperArmToShoulderJoint[i])
                i++
            }
            i = 0
            while (i < MAX_ARMS) {
                savefile.ReadMat3(lowerArmToElbowJoint[i])
                i++
            }
        }

        override fun Init(self: idEntity?, anim: String, modelOffset: idVec3): Boolean {
            var i: Int
            var jointName: String
            val trm = idTraceModel()
            val dir = idVec3()
            val handOrigin = idVec3()
            val elbowOrigin = idVec3()
            val shoulderOrigin = idVec3()
            val dirOrigin = idVec3()
            val axis = idMat3()
            var handAxis = idMat3()
            var elbowAxis: idMat3
            var shoulderAxis: idMat3
            if (null == self) {
                return false
            }
            numArms = Lib.Min(self.spawnArgs.GetInt("ik_numArms", "0"), MAX_ARMS)
            if (numArms == 0) {
                return true
            }
            if (!super.Init(self, anim, modelOffset)) {
                return false
            }
            val numJoints = animator!!.NumJoints()
            val joints = Array<idJointMat>(numJoints) { idJointMat() }

            // create the animation frame used to setup the IK
            GameEdit.gameEdit.ANIM_CreateAnimFrame(
                animator!!.ModelHandle(),
                animator!!.GetAnim(modifiedAnim)!!.MD5Anim(0),
                numJoints,
                joints,
                1,
                animator!!.ModelDef()!!.GetVisualOffset().plus(modelOffset),
                animator!!.RemoveOrigin()
            )
            enabledArms = 0

            // get all the joints
            i = 0
            while (i < numArms) {
                jointName = self.spawnArgs.GetString(Str.va("ik_hand%d", i + 1))
                handJoints[i] = animator!!.GetJointHandle(jointName)
                if (handJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Reach::Init: invalid hand joint '%s'", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_elbow%d", i + 1))
                elbowJoints[i] = animator!!.GetJointHandle(jointName)
                if (elbowJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Reach::Init: invalid elbow joint '%s'\n", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_shoulder%d", i + 1))
                shoulderJoints[i] = animator!!.GetJointHandle(jointName)
                if (shoulderJoints[i] == Model.INVALID_JOINT) {
                    idGameLocal.Error("idIK_Reach::Init: invalid shoulder joint '%s'\n", jointName)
                }
                jointName = self.spawnArgs.GetString(Str.va("ik_elbowDir%d", i + 1))
                dirJoints[i] = animator!!.GetJointHandle(jointName)
                enabledArms = enabledArms or (1 shl i)
                i++
            }

            // get the arm bone lengths and rotation matrices
            i = 0
            while (i < numArms) {
                handAxis = joints[handJoints[i]].ToMat3()
                handOrigin.set(joints[handJoints[i]].ToVec3())
                elbowAxis = joints[elbowJoints[i]].ToMat3()
                elbowOrigin.set(joints[elbowJoints[i]].ToVec3())
                shoulderAxis = joints[shoulderJoints[i]].ToMat3()
                shoulderOrigin.set(joints[shoulderJoints[i]].ToVec3())

                // get the IK direction
                if (dirJoints[i] != Model.INVALID_JOINT) {
                    dirOrigin.set(joints[dirJoints[i]].ToVec3())
                    dir.set(dirOrigin.minus(elbowOrigin))
                } else {
                    dir.set(-1.0f, 0.0f, 0.0f)
                }
                shoulderForward[i].set(dir.times(shoulderAxis.Transpose()))
                elbowForward[i].set(dir.times(elbowAxis.Transpose()))

                // conversion from upper arm bone axis to should joint axis
                upperArmLength[i] = GetBoneAxis(shoulderOrigin, elbowOrigin, dir, axis)
                upperArmToShoulderJoint[i] = shoulderAxis.times(axis.Transpose())

                // conversion from lower arm bone axis to elbow joint axis
                lowerArmLength[i] = GetBoneAxis(elbowOrigin, handOrigin, dir, axis)
                lowerArmToElbowJoint[i] = elbowAxis.times(axis.Transpose())
                i++
            }
            initialized = true
            return true
        }

        override fun Evaluate() {
            var i: Int
            val modelOrigin = idVec3()
            val shoulderOrigin = idVec3()
            val elbowOrigin = idVec3()
            val handOrigin = idVec3()
            val shoulderDir = idVec3()
            val elbowDir = idVec3()
            val modelAxis: idMat3
            val axis = idMat3()
            val shoulderAxis = Array<idMat3>(MAX_ARMS) { idMat3() }
            val elbowAxis = Array<idMat3>(MAX_ARMS) { idMat3() }
            val trace = trace_s()
            modelOrigin.set(self!!.GetRenderEntity().origin)
            modelAxis = self!!.GetRenderEntity().axis

            // solve IK
            i = 0
            while (i < numArms) {


                // get the position of the shoulder in world space
                animator!!.GetJointTransform(shoulderJoints[i], Game_local.gameLocal.time, shoulderOrigin, axis)
                shoulderOrigin.set(modelOrigin.plus(shoulderOrigin.times(modelAxis)))
                shoulderDir.set(shoulderForward[i].times(axis.times(modelAxis)))

                // get the position of the hand in world space
                animator!!.GetJointTransform(handJoints[i], Game_local.gameLocal.time, handOrigin, axis)
                handOrigin.set(modelOrigin.plus(handOrigin.times(modelAxis)))

                // get first collision going from shoulder to hand
                Game_local.gameLocal.clip.TracePoint(trace, shoulderOrigin, handOrigin, Material.CONTENTS_SOLID, self)
                handOrigin.set(trace.endpos)

                // get the IK bend direction
                animator!!.GetJointTransform(elbowJoints[i], Game_local.gameLocal.time, elbowOrigin, axis)
                elbowDir.set(elbowForward[i].times(axis.times(modelAxis)))

                // solve IK and calculate elbow position
                SolveTwoBones(
                    shoulderOrigin,
                    handOrigin,
                    elbowDir,
                    upperArmLength[i],
                    lowerArmLength[i],
                    elbowOrigin
                )
                if (SysCvar.ik_debug.GetBool()) {
                    Game_local.gameRenderWorld.DebugLine(Lib.colorCyan, shoulderOrigin, elbowOrigin)
                    Game_local.gameRenderWorld.DebugLine(Lib.colorRed, elbowOrigin, handOrigin)
                    Game_local.gameRenderWorld.DebugLine(
                        Lib.colorYellow,
                        elbowOrigin,
                        elbowOrigin.plus(elbowDir)
                    )
                    Game_local.gameRenderWorld.DebugLine(
                        Lib.colorGreen,
                        elbowOrigin,
                        elbowOrigin.plus(shoulderDir)
                    )
                }

                // get the axis for the shoulder joint
                GetBoneAxis(shoulderOrigin, elbowOrigin, shoulderDir, axis)
                shoulderAxis[i] = upperArmToShoulderJoint[i].times(axis.times(modelAxis.Transpose()))

                // get the axis for the elbow joint
                GetBoneAxis(elbowOrigin, handOrigin, elbowDir, axis)
                elbowAxis[i] = lowerArmToElbowJoint[i].times(axis.times(modelAxis.Transpose()))
                i++
            }
            i = 0
            while (i < numArms) {
                animator!!.SetJointAxis(
                    shoulderJoints[i],
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE,
                    shoulderAxis[i]
                )
                animator!!.SetJointAxis(elbowJoints[i], jointModTransform_t.JOINTMOD_WORLD_OVERRIDE, elbowAxis[i])
                i++
            }
            ik_activate = true
        }

        override fun ClearJointMods() {
            var i: Int
            if (null == self || !ik_activate) {
                return
            }
            i = 0
            while (i < numArms) {
                animator!!.SetJointAxis(
                    shoulderJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                animator!!.SetJointAxis(
                    elbowJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                animator!!.SetJointAxis(
                    handJoints[i],
                    jointModTransform_t.JOINTMOD_NONE,
                    idMat3.getMat3_identity()
                )
                i++
            }
            ik_activate = false
        }

        companion object {
            private const val MAX_ARMS = 2
        }

        //
        //
        init {
            var i: Int
            initialized = false
            numArms = 0
            enabledArms = 0
            i = 0
            while (i < MAX_ARMS) {
                handJoints[i] = Model.INVALID_JOINT
                elbowJoints[i] = Model.INVALID_JOINT
                shoulderJoints[i] = Model.INVALID_JOINT
                dirJoints[i] = Model.INVALID_JOINT
                shoulderForward[i].Zero()
                elbowForward[i].Zero()
                upperArmLength[i] = 0f
                lowerArmLength[i] = 0f
                upperArmToShoulderJoint[i].Identity()
                lowerArmToElbowJoint[i].Identity()
                i++
            }
        }
    }
}