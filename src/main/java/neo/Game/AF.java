package neo.Game;

import neo.CM.CollisionModel.trace_s;
import neo.Game.Animation.Anim.AFJointModType_t;
import neo.Game.Animation.Anim_Blend.idAnimator;
import neo.Game.Animation.Anim_Blend.idDeclModelDef;
import neo.Game.Entity.idEntity;
import neo.Game.GameSys.SaveGame.idRestoreGame;
import neo.Game.GameSys.SaveGame.idSaveGame;
import neo.Game.Physics.Clip.idClipModel;
import neo.Game.Physics.Physics.impactInfo_s;
import neo.Game.Physics.Physics_AF.*;
import neo.Renderer.Model.idRenderModel;
import neo.Renderer.RenderWorld.renderEntity_s;
import neo.framework.DeclAF.getJointTransform_t;
import neo.framework.DeclAF.idDeclAF;
import neo.framework.DeclAF.idDeclAF_Body;
import neo.framework.DeclAF.idDeclAF_Constraint;
import neo.idlib.BV.Bounds.idBounds;
import neo.idlib.Dict_h.idDict;
import neo.idlib.Dict_h.idKeyValue;
import neo.idlib.Text.Lexer.idLexer;
import neo.idlib.Text.Str.idStr;
import neo.idlib.Text.Token.idToken;
import neo.idlib.containers.CFloat;
import neo.idlib.containers.List.idList;
import neo.idlib.geometry.JointTransform.idJointMat;
import neo.idlib.geometry.TraceModel.idTraceModel;
import neo.idlib.math.Angles.idAngles;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Rotation.idRotation;
import neo.idlib.math.Vector.idVec3;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Stream;

import static neo.Game.Animation.Anim.AFJointModType_t.*;
import static neo.Game.GameEdit.gameEdit;
import static neo.Game.GameSys.SysCvar.af_testSolid;
import static neo.Game.Game_local.MAX_GENTITIES;
import static neo.Game.Game_local.gameLocal;
import static neo.Game.Physics.Clip.CLIPMODEL_ID_TO_JOINT_HANDLE;
import static neo.Renderer.Model.INVALID_JOINT;
import static neo.TempDump.NOT;
import static neo.framework.DeclAF.declAFJointMod_t.*;
import static neo.framework.DeclManager.declManager;
import static neo.framework.DeclManager.declState_t.DS_DEFAULTED;
import static neo.framework.DeclManager.declType_t.DECL_AF;
import static neo.idlib.math.Math_h.MS2SEC;
import static neo.idlib.math.Matrix.idMat3.getMat3_identity;
import static neo.idlib.math.Vector.getVec3_origin;

/**
 *
 */
public class AF {

    //
    public static final String ARTICULATED_FIGURE_ANIM = "af_pose";

    public static final float POSE_BOUNDS_EXPANSION = 5.0f;

    /*
     ===============================================================================

     Articulated Figure controller.

     ===============================================================================
     */
    public static class jointConversion_s {

        final idVec3 jointBodyOrigin = new idVec3(); // origin of body relative to joint
        int bodyId;                       // id of the body
        idMat3 jointBodyAxis;   // axis of body relative to joint
        int/*jointHandle_t*/ jointHandle; // handle of joint this body modifies
        AFJointModType_t jointMod;        // modify joint axis, origin or both
    }

    public static class afTouch_s {

        public idAFBody touchedByBody;
        public idClipModel touchedClipModel;
        public idEntity touchedEnt;
    }
//

    public static class idAF {

        private static int DBG_LoadBody = 0;
        protected final idVec3 baseOrigin;          // offset of base body relative to skeletal model origin
        protected idAnimator animator;            // animator on entity
        protected idMat3 baseAxis;            // axis of base body relative to skeletal model origin
        protected boolean hasBindConstraints;  // true if the bind constraints have been added
        protected boolean isActive;            // true if the articulated figure physics is active
        protected boolean isLoaded;            // true when the articulated figure is properly loaded
        protected final idList<Integer> jointBody;           // table to find the nearest articulated figure body for a joint of the skeletal model
        protected final idList<jointConversion_s> jointMods;           // list with transforms from skeletal model joints to articulated figure bodies
        protected int modifiedAnim;        // anim to modify
        protected idStr name;                // name of the loaded .af file
        protected idPhysics_AF physicsObj;          // articulated figure physics
        protected int poseTime;            // last time the articulated figure was transformed to reflect the current animation pose
        protected int restStartTime;       // time the articulated figure came to rest
        //
        //
        protected idEntity self;                // entity using the animated model
        // ~idAF( void );

        public idAF() {
            name = new idStr();
            physicsObj = new idPhysics_AF();
            self = null;
            animator = null;
            modifiedAnim = 0;
            baseOrigin = new idVec3();
            baseAxis = getMat3_identity();
            jointMods = new idList<>();
            jointBody = new idList<>();
            poseTime = -1;
            restStartTime = -1;
            isLoaded = false;
            isActive = false;
            hasBindConstraints = false;
        }

        public void Save(idSaveGame savefile) {
            savefile.WriteObject(self);
            savefile.WriteString(GetName());
            savefile.WriteBool(hasBindConstraints);
            savefile.WriteVec3(baseOrigin);
            savefile.WriteMat3(baseAxis);
            savefile.WriteInt(poseTime);
            savefile.WriteInt(restStartTime);
            savefile.WriteBool(isLoaded);
            savefile.WriteBool(isActive);
            savefile.WriteStaticObject(physicsObj);
        }

        public void Restore(idRestoreGame savefile) {
            savefile.ReadObject(self);
            savefile.ReadString(name);
            hasBindConstraints = savefile.ReadBool();
            savefile.ReadVec3(baseOrigin);
            savefile.ReadMat3(baseAxis);
            poseTime = savefile.ReadInt();
            restStartTime = savefile.ReadInt();
            isLoaded = savefile.ReadBool();
            isActive = savefile.ReadBool();

            animator = null;
            modifiedAnim = 0;

            if (self != null) {
                SetAnimator(self.GetAnimator());
                Load(self, name.toString());
                if (hasBindConstraints) {
                    AddBindConstraints();
                }
            }

            savefile.ReadStaticObject(physicsObj);

            if (self != null) {
                if (isActive) {
                    // clear all animations
                    animator.ClearAllAnims(gameLocal.time, 0);
                    animator.ClearAllJoints();

                    // switch to articulated figure physics
                    self.RestorePhysics(physicsObj);
                    physicsObj.EnableClip();
                }
                UpdateAnimation();
            }
        }

        public void SetAnimator(idAnimator a) {
            animator = a;
        }

        public boolean Load(idEntity ent, final String fileName) {
            int i, j;
            idDeclAF file;
            idDeclModelDef modelDef;
            idRenderModel model;
            int numJoints;
            idJointMat[] joints;

            assert (ent != null);

            self = ent;
            physicsObj.SetSelf(self);

            if (animator == null) {
                gameLocal.Warning("Couldn't load af '%s' for entity '%s' at (%s): NULL animator\n", name, ent.name, ent.GetPhysics().GetOrigin().ToString(0));
                return false;
            }

            name.oSet(fileName);
            name.StripFileExtension();

            file = (idDeclAF) declManager.FindType(DECL_AF, name);
            if (null == file) {
                gameLocal.Warning("Couldn't load af '%s' for entity '%s' at (%s)\n", name, ent.name, ent.GetPhysics().GetOrigin().ToString(0));
                return false;
            }

            if (file.bodies.Num() == 0 || !file.bodies.oGet(0).jointName.toString().equals("origin")) {
                gameLocal.Warning("idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no body which modifies the origin joint.",
                        name.toString(), ent.name.toString(), ent.GetPhysics().GetOrigin().ToString(0));
                return false;
            }

            modelDef = animator.ModelDef();
            if (modelDef == null || modelDef.GetState() == DS_DEFAULTED) {
                gameLocal.Warning("idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no or defaulted modelDef '%s'",
                        name.toString(), ent.name.toString(), ent.GetPhysics().GetOrigin().ToString(0), modelDef != null ? modelDef.GetName() : "");
                return false;
            }

            model = animator.ModelHandle();
            if (model == null || model.IsDefaultModel()) {
                gameLocal.Warning("idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no or defaulted model '%s'",
                        name.toString(), ent.name.toString(), ent.GetPhysics().GetOrigin().ToString(0), model != null ? model.Name() : "");
                return false;
            }

            // get the modified animation
            modifiedAnim = animator.GetAnim(ARTICULATED_FIGURE_ANIM);
            if (0 == modifiedAnim) {
                gameLocal.Warning("idAF::Load: articulated figure '%s' for entity '%s' at (%s) has no modified animation '%s'",
                        name, ent.name, ent.GetPhysics().GetOrigin().ToString(0), ARTICULATED_FIGURE_ANIM);
                return false;
            }

            // create the animation frame used to setup the articulated figure
            numJoints = animator.NumJoints();
            joints = Stream.generate(idJointMat::new).limit(numJoints).toArray(idJointMat[]::new);
            gameEdit.ANIM_CreateAnimFrame(model, animator.GetAnim(modifiedAnim).MD5Anim(0), numJoints, joints, 1, animator.ModelDef().GetVisualOffset(), animator.RemoveOrigin());

            // set all vector positions from model joints
            file.Finish(GetJointTransform.INSTANCE, joints, animator);

            // initialize articulated figure physics
            physicsObj.SetGravity(gameLocal.GetGravity());
            physicsObj.SetClipMask(file.clipMask.getVal());
            physicsObj.SetDefaultFriction(file.defaultLinearFriction, file.defaultAngularFriction, file.defaultContactFriction);
            physicsObj.SetSuspendSpeed(file.suspendVelocity, file.suspendAcceleration);
            physicsObj.SetSuspendTolerance(file.noMoveTime, file.noMoveTranslation, file.noMoveRotation);
            physicsObj.SetSuspendTime(file.minMoveTime, file.maxMoveTime);
            physicsObj.SetSelfCollision(file.selfCollision);

            // clear the list with transforms from joints to bodies
            jointMods.SetNum(0, false);

            // clear the joint to body conversion list
            jointBody.AssureSize(animator.NumJoints());
            for (i = 0; i < jointBody.Num(); i++) {
                jointBody.oSet(i, -1);
            }

            // delete any bodies in the physicsObj that are no longer in the idDeclAF
            for (i = 0; i < physicsObj.GetNumBodies(); i++) {
                idAFBody body = physicsObj.GetBody(i);
                for (j = 0; j < file.bodies.Num(); j++) {
                    if (file.bodies.oGet(j).name.Icmp(body.GetName()) == 0) {
                        break;
                    }
                }
                if (j >= file.bodies.Num()) {
                    physicsObj.DeleteBody(i);
                    i--;
                }
            }

            // delete any constraints in the physicsObj that are no longer in the idDeclAF
            for (i = 0; i < physicsObj.GetNumConstraints(); i++) {
                idAFConstraint constraint = physicsObj.GetConstraint(i);
                for (j = 0; j < file.constraints.Num(); j++) {
                    if (file.constraints.oGet(j).name.Icmp(constraint.GetName()) == 0
                            && file.constraints.oGet(j).type.ordinal() == constraint.GetType().ordinal()) {
                        break;
                    }
                }
                if (j >= file.constraints.Num()) {
                    physicsObj.DeleteConstraint(i);
                    i--;
                }
            }

            // load bodies from the file
            for (i = 0; i < file.bodies.Num(); i++) {
                LoadBody(file.bodies.oGet(i), joints);
            }

            // load constraints from the file
            for (i = 0; i < file.constraints.Num(); i++) {
                LoadConstraint(file.constraints.oGet(i));
            }

            physicsObj.UpdateClipModels();

            // check if each joint is contained by a body
            for (i = 0; i < animator.NumJoints(); i++) {
                if (jointBody.oGet(i) == -1) {
                    /*jointHandle_t*/
                    gameLocal.Warning("idAF::Load: articulated figure '%s' for entity '%s' at (%s) joint '%s' is not contained by a body",
                            name, self.name, self.GetPhysics().GetOrigin().ToString(0), animator.GetJointName(i));
                }
            }

            physicsObj.SetMass(file.totalMass);
            physicsObj.SetChanged();

            // disable the articulated figure for collision detection until activated
            physicsObj.DisableClip();

            isLoaded = true;

            return true;
        }

        public boolean Load(idEntity ent, final idStr fileName) {
            return Load(ent, fileName.toString());
        }

        public boolean IsLoaded() {
            return isLoaded && self != null;
        }

        public String GetName() {
            return name.toString();
        }

        /*
         ================
         idAF::SetupPose

         Transforms the articulated figure to match the current animation pose of the given entity.
         ================
         */
        public void SetupPose(idEntity ent, int time) {
            int i;
            idAFBody body;
            final idVec3 origin = new idVec3();
            idMat3 axis = new idMat3();
            idAnimator animatorPtr;
            renderEntity_s renderEntity;

            if (!IsLoaded() || null == ent) {
                return;
            }

            animatorPtr = ent.GetAnimator();
            if (NOT(animatorPtr)) {
                return;
            }

            renderEntity = ent.GetRenderEntity();
            if (null == renderEntity) {
                return;
            }

            // if the animation is driven by the physics
            if (self.GetPhysics() == physicsObj) {
                return;
            }

            // if the pose was already updated this frame
            if (poseTime == time) {
                return;
            }
            poseTime = time;

            for (i = 0; i < jointMods.Num(); i++) {
                body = physicsObj.GetBody(jointMods.oGet(i).bodyId);
                animatorPtr.GetJointTransform(jointMods.oGet(i).jointHandle, time, origin, axis);
                body.SetWorldOrigin(renderEntity.origin.oPlus((origin.oPlus(jointMods.oGet(i).jointBodyOrigin.oMultiply(axis))).oMultiply(renderEntity.axis)));
                body.SetWorldAxis(jointMods.oGet(i).jointBodyAxis.oMultiply(axis).oMultiply(renderEntity.axis));
            }

            if (isActive) {
                physicsObj.UpdateClipModels();
            }
        }

        /*
         ================
         idAF::ChangePose

         Change the articulated figure to match the current animation pose of the given entity
         and set the velocity relative to the previous pose.
         ================
         */
        public void ChangePose(idEntity ent, int time) {
            int i;
            float invDelta;
            idAFBody body;
            final idVec3 origin = new idVec3(), lastOrigin = new idVec3();
            idMat3 axis = new idMat3();
            idAnimator animatorPtr;
            renderEntity_s renderEntity;

            if (!IsLoaded() || NOT(ent)) {
                return;
            }

            animatorPtr = ent.GetAnimator();
            if (NOT(animatorPtr)) {
                return;
            }

            renderEntity = ent.GetRenderEntity();
            if (NOT(renderEntity)) {
                return;
            }

            // if the animation is driven by the physics
            if (self.GetPhysics() == physicsObj) {
                return;
            }

            // if the pose was already updated this frame
            if (poseTime == time) {
                return;
            }
            invDelta = 1.0f / MS2SEC(time - poseTime);
            poseTime = time;

            for (i = 0; i < jointMods.Num(); i++) {
                body = physicsObj.GetBody(jointMods.oGet(i).bodyId);
                animatorPtr.GetJointTransform(jointMods.oGet(i).jointHandle, time, origin, axis);
                lastOrigin.oSet(body.GetWorldOrigin());
                body.SetWorldOrigin(renderEntity.origin.oPlus((origin.oPlus(jointMods.oGet(i).jointBodyOrigin.oMultiply(axis))).oMultiply(renderEntity.axis)));
                body.SetWorldAxis(jointMods.oGet(i).jointBodyAxis.oMultiply(axis).oMultiply(renderEntity.axis));
                body.SetLinearVelocity((body.GetWorldOrigin().oMinus(lastOrigin)).oMultiply(invDelta));
            }

            physicsObj.UpdateClipModels();
        }

        public int EntitiesTouchingAF(afTouch_s[] touchList/*[ MAX_GENTITIES ]*/) {
            int i, j, numClipModels;
            idAFBody body;
            idClipModel cm;
            idClipModel[] clipModels = new idClipModel[MAX_GENTITIES];
            int numTouching;

            if (!IsLoaded()) {
                return 0;
            }

            numTouching = 0;
            numClipModels = gameLocal.clip.ClipModelsTouchingBounds(physicsObj.GetAbsBounds(), -1, clipModels, MAX_GENTITIES);

            for (i = 0; i < jointMods.Num(); i++) {
                body = physicsObj.GetBody(jointMods.oGet(i).bodyId);

                for (j = 0; j < numClipModels; j++) {
                    cm = clipModels[j];

                    if (NOT(cm) || cm.GetEntity().equals(self)) {
                        continue;
                    }

                    if (!cm.IsTraceModel()) {
                        continue;
                    }

                    if (!body.GetClipModel().GetAbsBounds().IntersectsBounds(cm.GetAbsBounds())) {
                        continue;
                    }

                    if (gameLocal.clip.ContentsModel(body.GetWorldOrigin(), body.GetClipModel(), body.GetWorldAxis(), -1, cm.Handle(), cm.GetOrigin(), cm.GetAxis()) != 0) {
                        touchList[numTouching].touchedByBody = body;
                        touchList[numTouching].touchedClipModel = cm;
                        touchList[numTouching].touchedEnt = cm.GetEntity();
                        numTouching++;
                        clipModels[j] = null;
                    }
                }
            }

            return numTouching;
        }

        public void Start() {
            if (!IsLoaded()) {
                return;
            }
            // clear all animations
            animator.ClearAllAnims(gameLocal.time, 0);
            animator.ClearAllJoints();
            // switch to articulated figure physics
            self.SetPhysics(physicsObj);
            // start the articulated figure physics simulation
            physicsObj.EnableClip();
            physicsObj.Activate();
            isActive = true;
        }

        public void StartFromCurrentPose(int inheritVelocityTime) {

            if (!IsLoaded()) {
                return;
            }

            // if the ragdoll should inherit velocity from the animation
            if (inheritVelocityTime > 0) {

                // make sure the ragdoll is at rest
                physicsObj.PutToRest();

                // set the pose for some time back
                SetupPose(self, gameLocal.time - inheritVelocityTime);

                // change the pose for the current time and set velocities
                ChangePose(self, gameLocal.time);
            } else {
                // transform the articulated figure to reflect the current animation pose
                SetupPose(self, gameLocal.time);
            }

            physicsObj.UpdateClipModels();

            TestSolid();

            Start();

            UpdateAnimation();

            // update the render entity origin and axis
            self.UpdateModel();

            // make sure the renderer gets the updated origin and axis
            self.Present();
        }

        public void Stop() {
            // disable the articulated figure for collision detection
            physicsObj.UnlinkClip();
            isActive = false;
        }

        public void Rest() {
            physicsObj.PutToRest();
        }

        public boolean IsActive() {
            return isActive;
        }

        /*
         ================
         idAF::SetConstraintPosition

         Only moves constraints that bind the entity to another entity.
         ================
         */
        public void SetConstraintPosition(final String name, final idVec3 pos) {
            idAFConstraint constraint;

            constraint = GetPhysics().GetConstraint(name);

            if (null == constraint) {
                gameLocal.Warning("can't find a constraint with the name '%s'", name);
                return;
            }

            if (constraint.GetBody2() != null) {
                gameLocal.Warning("constraint '%s' does not bind to another entity", name);
                return;
            }

            switch (constraint.GetType()) {
                case CONSTRAINT_BALLANDSOCKETJOINT: {
                    idAFConstraint_BallAndSocketJoint bs = (idAFConstraint_BallAndSocketJoint) constraint;
                    bs.Translate(pos.oMinus(bs.GetAnchor()));
                    break;
                }
                case CONSTRAINT_UNIVERSALJOINT: {
                    idAFConstraint_UniversalJoint uj = (idAFConstraint_UniversalJoint) constraint;
                    uj.Translate(pos.oMinus(uj.GetAnchor()));
                    break;
                }
                case CONSTRAINT_HINGE: {
                    idAFConstraint_Hinge hinge = (idAFConstraint_Hinge) constraint;
                    hinge.Translate(pos.oMinus(hinge.GetAnchor()));
                    break;
                }
                default: {
                    gameLocal.Warning("cannot set the constraint position for '%s'", name);
                    break;
                }
            }
        }

        public idPhysics_AF GetPhysics() {
            return physicsObj;
        }

        /*
         ================
         idAF::GetBounds

         returns bounds for the current pose
         ================
         */
        public idBounds GetBounds() {
            int i;
            idAFBody body;
            final idVec3 origin = new idVec3(), entityOrigin = new idVec3();
            idMat3 axis, entityAxis;
            idBounds bounds = new idBounds(), b = new idBounds();

            bounds.Clear();

            // get model base transform
            origin.oSet(physicsObj.GetOrigin(0));
            axis = physicsObj.GetAxis(0);

            entityAxis = baseAxis.Transpose().oMultiply(axis);
            entityOrigin.oSet(origin.oMinus(baseOrigin.oMultiply(entityAxis)));

            // get bounds relative to base
            for (i = 0; i < jointMods.Num(); i++) {
                body = physicsObj.GetBody(jointMods.oGet(i).bodyId);
                origin.oSet((body.GetWorldOrigin().oMinus(entityOrigin)).oMultiply(entityAxis.Transpose()));
                axis = body.GetWorldAxis().oMultiply(entityAxis.Transpose());
                b.FromTransformedBounds(body.GetClipModel().GetBounds(), origin, axis);

                bounds.oPluSet(b);
            }

            return bounds;
        }

        public boolean UpdateAnimation() {
            int i;
            final idVec3 origin = new idVec3(), renderOrigin = new idVec3(), bodyOrigin = new idVec3();
            idMat3 axis, renderAxis, bodyAxis;
            renderEntity_s renderEntity;

            if (!IsLoaded()) {
                return false;
            }

            if (!IsActive()) {
                return false;
            }

            renderEntity = self.GetRenderEntity();
            if (null == renderEntity) {
                return false;
            }

            if (physicsObj.IsAtRest()) {
                if (restStartTime == physicsObj.GetRestStartTime()) {
                    return false;
                }
                restStartTime = physicsObj.GetRestStartTime();
            }

            // get the render position
            origin.oSet(physicsObj.GetOrigin(0));
            axis = physicsObj.GetAxis(0);
            renderAxis = baseAxis.Transpose().oMultiply(axis);
            renderOrigin.oSet(origin.oMinus(baseOrigin.oMultiply(renderAxis)));

            // create an animation frame which reflects the current pose of the articulated figure
            animator.InitAFPose();
            for (i = 0; i < jointMods.Num(); i++) {
                // check for the origin joint
                if (jointMods.oGet(i).jointHandle == 0) {
                    continue;
                }
                bodyOrigin.oSet(physicsObj.GetOrigin(jointMods.oGet(i).bodyId));
                bodyAxis = physicsObj.GetAxis(jointMods.oGet(i).bodyId);
                axis = jointMods.oGet(i).jointBodyAxis.Transpose().oMultiply(bodyAxis.oMultiply(renderAxis.Transpose()));
                origin.oSet((bodyOrigin.oMinus(jointMods.oGet(i).jointBodyOrigin.oMultiply(axis).oMinus(renderOrigin))).oMultiply(renderAxis.Transpose()));
                animator.SetAFPoseJointMod(jointMods.oGet(i).jointHandle, jointMods.oGet(i).jointMod, axis, origin);
            }
            animator.FinishAFPose(modifiedAnim, GetBounds().Expand(POSE_BOUNDS_EXPANSION), gameLocal.time);
            animator.SetAFPoseBlendWeight(1.0f);

            return true;
        }

        public void GetPhysicsToVisualTransform(final idVec3 origin, final idMat3 axis) {
            origin.oSet(baseOrigin.oNegative());
            axis.oSet(baseAxis.Transpose());
        }

        public impactInfo_s GetImpactInfo(idEntity ent, int id, final idVec3 point) {
            SetupPose(self, gameLocal.time);
            return physicsObj.GetImpactInfo(BodyForClipModelId(id), point);
        }

        public void ApplyImpulse(idEntity ent, int id, final idVec3 point, final idVec3 impulse) {
            SetupPose(self, gameLocal.time);
            physicsObj.ApplyImpulse(BodyForClipModelId(id), point, impulse);
        }

        public void AddForce(idEntity ent, int id, final idVec3 point, final idVec3 force) {
            SetupPose(self, gameLocal.time);
            physicsObj.AddForce(BodyForClipModelId(id), point, force);
        }

        public int BodyForClipModelId(int id) {
            if (id >= 0) {
                return id;
            } else {
                id = CLIPMODEL_ID_TO_JOINT_HANDLE(id);
                if (id < jointBody.Num()) {
                    return jointBody.oGet(id);
                } else {
                    return 0;
                }
            }
        }

        public void SaveState(idDict args) {
            int i;
            idAFBody body;
            String key, value;

            for (i = 0; i < jointMods.Num(); i++) {
                body = physicsObj.GetBody(jointMods.oGet(i).bodyId);

                key = "body " + body.GetName();
                value = body.GetWorldOrigin().ToString(8);
                value += " ";
                value += body.GetWorldAxis().ToAngles().ToString(8);
                args.Set(key, value);
            }
        }

        public void LoadState(final idDict args) {
            idKeyValue kv;
            idStr name = new idStr();
            idAFBody body;
            final idVec3 origin = new idVec3();
            idAngles angles = new idAngles();

            kv = args.MatchPrefix("body ", null);
            while (kv != null) {

                name.oSet(kv.GetKey());
                name.Strip("body ");
                body = physicsObj.GetBody(name.toString());
                if (body != null) {
                    Scanner sscanf = new Scanner(kv.GetValue().toString());
                    sscanf.useLocale(Locale.US);
//			sscanf( kv.GetValue(), "%f %f %f %f %f %f", &origin.x, &origin.y, &origin.z, &angles.pitch, &angles.yaw, &angles.roll );
                    origin.x = sscanf.nextFloat();
                    origin.y = sscanf.nextFloat();
                    origin.z = sscanf.nextFloat();
                    angles.pitch = sscanf.nextFloat();
                    angles.yaw = sscanf.nextFloat();
                    angles.roll = sscanf.nextFloat();
                    sscanf.close();
                    body.SetWorldOrigin(origin);
                    body.SetWorldAxis(angles.ToMat3());
                } else {
                    gameLocal.Warning("Unknown body part %s in articulated figure %s", name, this.name);
                }

                kv = args.MatchPrefix("body ", kv);
            }

            physicsObj.UpdateClipModels();
        }

        public void AddBindConstraints() {
            idKeyValue kv;
            idStr name = new idStr();
            idAFBody body;
            idLexer lexer = new idLexer();
            idToken type = new idToken(), bodyName = new idToken(), jointName = new idToken();
            final idVec3 origin = new idVec3(), renderOrigin = new idVec3();
            idMat3 axis, renderAxis;

            if (!IsLoaded()) {
                return;
            }

            final idDict args = self.spawnArgs;

            // get the render position
            origin.oSet(physicsObj.GetOrigin(0));
            axis = physicsObj.GetAxis(0);
            renderAxis = baseAxis.Transpose().oMultiply(axis);
            renderOrigin.oSet(origin.oMinus(baseOrigin.oMultiply(renderAxis)));

            // parse all the bind constraints
            for (kv = args.MatchPrefix("bindConstraint ", null); kv != null; kv = args.MatchPrefix("bindConstraint ", kv)) {
                name.oSet(kv.GetKey());
                name.Strip("bindConstraint ");

                lexer.LoadMemory(kv.GetValue(), kv.GetValue().Length(), kv.GetKey());
                lexer.ReadToken(type);

                lexer.ReadToken(bodyName);
                body = physicsObj.GetBody(bodyName);
                if (NOT(body)) {
                    gameLocal.Warning("idAF::AddBindConstraints: body '%s' not found on entity '%s'", bodyName, self.name);
                    lexer.FreeSource();
                    continue;
                }

                if (type.Icmp("fixed") == 0) {
                    idAFConstraint_Fixed c;

                    c = new idAFConstraint_Fixed(name, body, null);
                    physicsObj.AddConstraint(c);
                } else if (type.Icmp("ballAndSocket") == 0) {
                    idAFConstraint_BallAndSocketJoint c;

                    c = new idAFConstraint_BallAndSocketJoint(name, body, null);
                    physicsObj.AddConstraint(c);
                    lexer.ReadToken(jointName);

                    int/*jointHandle_t*/ joint = animator.GetJointHandle(jointName.toString());
                    if (joint == INVALID_JOINT) {
                        gameLocal.Warning("idAF::AddBindConstraints: joint '%s' not found", jointName);
                    }

                    animator.GetJointTransform(joint, gameLocal.time, origin, axis);
                    c.SetAnchor(renderOrigin.oPlus(origin.oMultiply(renderAxis)));
                } else if (type.Icmp("universal") == 0) {
                    idAFConstraint_UniversalJoint c;

                    c = new idAFConstraint_UniversalJoint(name, body, null);
                    physicsObj.AddConstraint(c);
                    lexer.ReadToken(jointName);

                    int/*jointHandle_t*/ joint = animator.GetJointHandle(jointName);
                    if (joint == INVALID_JOINT) {
                        gameLocal.Warning("idAF::AddBindConstraints: joint '%s' not found", jointName);
                    }
                    animator.GetJointTransform(joint, gameLocal.time, origin, axis);
                    c.SetAnchor(renderOrigin.oPlus(origin.oMultiply(renderAxis)));
                    c.SetShafts(new idVec3(0, 0, 1), new idVec3(0, 0, -1));
                } else {
                    gameLocal.Warning("idAF::AddBindConstraints: unknown constraint type '%s' on entity '%s'", type, self.name);
                }

                lexer.FreeSource();
            }

            hasBindConstraints = true;
        }

        public void RemoveBindConstraints() {
            idKeyValue kv;

            if (!IsLoaded()) {
                return;
            }

            final idDict args = self.spawnArgs;
            idStr name = new idStr();

            kv = args.MatchPrefix("bindConstraint ", null);
            while (kv != null) {
                name.oSet(kv.GetKey());
                name.Strip("bindConstraint ");

                if (physicsObj.GetConstraint(name.toString()) != null) {
                    physicsObj.DeleteConstraint(name.toString());
                }

                kv = args.MatchPrefix("bindConstraint ", kv);
            }

            hasBindConstraints = false;
        }

        /*
         ================
         idAF::SetBase

         Sets the base body.
         ================
         */
        protected void SetBase(idAFBody body, final idJointMat[] joints) {
            physicsObj.ForceBodyId(body, 0);
            baseOrigin.oSet(body.GetWorldOrigin());
            baseAxis.oSet(body.GetWorldAxis());
            AddBody(body, joints, animator.GetJointName(animator.GetFirstChild("origin")), AF_JOINTMOD_AXIS);
        }

        /*
         ================
         idAF::AddBody

         Adds a body.
         ================
         */
        protected void AddBody(idAFBody body, final idJointMat[] joints, final String jointName, final AFJointModType_t mod) {
            int index;
            int/*jointHandle_t*/ handle;
            final idVec3 origin = new idVec3();
            idMat3 axis;

            handle = animator.GetJointHandle(jointName);
            if (handle == INVALID_JOINT) {
                Game_local.idGameLocal.Error("idAF for entity '%s' at (%s) modifies unknown joint '%s'", self.name, self.GetPhysics().GetOrigin().ToString(0), jointName);
            }

            assert (handle < animator.NumJoints());
            origin.oSet(joints[handle].ToVec3());
            axis = joints[handle].ToMat3();

            index = jointMods.Num();
            jointMods.SetNum(index + 1, false);
            jointMods.oSet(index, new jointConversion_s());
            jointMods.oGet(index).bodyId = physicsObj.GetBodyId(body);
            jointMods.oGet(index).jointHandle = handle;
            jointMods.oGet(index).jointMod = mod;

            jointMods.oGet(index).jointBodyOrigin.oSet((body.GetWorldOrigin().oMinus(origin)).oMultiply(axis.Transpose()));
            jointMods.oGet(index).jointBodyAxis = body.GetWorldAxis().oMultiply(axis.Transpose());
        }

        protected boolean LoadBody(final idDeclAF_Body fb, final idJointMat[] joints) {
            int id, i;
            DBG_LoadBody++;
            float length;
            CFloat candleMass = new CFloat();
            idTraceModel trm = new idTraceModel();
            idClipModel clip;
            idAFBody body;
            idMat3 axis, inertiaTensor = new idMat3();
            final idVec3 centerOfMass = new idVec3(), origin = new idVec3();
            idBounds bounds = new idBounds();
            final ArrayList<Integer/*jointHandle_t*/> jointList = new ArrayList<>();

            origin.oSet(fb.origin.ToVec3());
            axis = fb.angles.ToMat3();
            bounds.oSet(0, fb.v1.ToVec3());
            bounds.oSet(1, fb.v2.ToVec3());

            switch (fb.modelType) {
                case TRM_BOX: {
                    trm.SetupBox(bounds);
                    break;
                }
                case TRM_OCTAHEDRON: {
                    trm.SetupOctahedron(bounds);
                    break;
                }
                case TRM_DODECAHEDRON: {
                    trm.SetupDodecahedron(bounds);
                    break;
                }
                case TRM_CYLINDER: {
                    trm.SetupCylinder(bounds, fb.numSides);
                    break;
                }
                case TRM_CONE: {
                    // place the apex at the origin
                    bounds.oGet(0).z -= bounds.oGet(1).z;
                    bounds.oGet(1).z = 0.0f;
                    trm.SetupCone(bounds, fb.numSides);
                    break;
                }
                case TRM_BONE: {
                    // direction of bone
                    axis.oSet(2, fb.v2.ToVec3().oMinus(fb.v1.ToVec3()));
                    length = axis.oGet(2).Normalize();
                    // axis of bone trace model
                    axis.oGet(2).NormalVectors(axis.oGet(0), axis.oGet(1));
                    axis.oSet(1, axis.oGet(1).oNegative());
                    // create bone trace model
                    trm.SetupBone(length, fb.width);
                    break;
                }
                default:
                    assert (false);
                    break;
            }
            trm.GetMassProperties(1.0f, candleMass, centerOfMass, inertiaTensor);
            trm.Translate(centerOfMass.oNegative());
            origin.oPluSet(centerOfMass.oMultiply(axis));

            body = physicsObj.GetBody(fb.name.toString());
            if (body != null) {
                clip = body.GetClipModel();
                if (!clip.IsEqual(trm)) {
                    clip = new idClipModel(trm);
                    clip.SetContents(fb.contents.getVal());
                    clip.Link(gameLocal.clip, self, 0, origin, axis);
                    body.SetClipModel(clip);
                }
                clip.SetContents(fb.contents.getVal());
                body.SetDensity(fb.density, fb.inertiaScale);
                body.SetWorldOrigin(origin);
                body.SetWorldAxis(axis);
                id = physicsObj.GetBodyId(body);
            } else {
                clip = new idClipModel(trm);
                clip.SetContents(fb.contents.getVal());
                clip.Link(gameLocal.clip, self, 0, origin, axis);
                body = new idAFBody(fb.name, clip, fb.density);
                if (!fb.inertiaScale.equals(getMat3_identity())) {
                    body.SetDensity(fb.density, fb.inertiaScale);
                }
                id = physicsObj.AddBody(body);
            }
            if (fb.linearFriction != -1.0f) {
                body.SetFriction(fb.linearFriction, fb.angularFriction, fb.contactFriction);
            }
            body.SetClipMask(fb.clipMask.getVal());
            body.SetSelfCollision(fb.selfCollision);

            if (fb.jointName.toString().equals("origin")) {
                SetBase(body, joints);
            } else {
                AFJointModType_t mod;
                if (fb.jointMod == DECLAF_JOINTMOD_AXIS) {
                    mod = AF_JOINTMOD_AXIS;
                } else if (fb.jointMod == DECLAF_JOINTMOD_ORIGIN) {
                    mod = AF_JOINTMOD_ORIGIN;
                } else if (fb.jointMod == DECLAF_JOINTMOD_BOTH) {
                    mod = AF_JOINTMOD_BOTH;
                } else {
                    mod = AF_JOINTMOD_AXIS;
                }
                AddBody(body, joints, fb.jointName.toString(), mod);
            }

            if (!fb.frictionDirection.ToVec3().equals(getVec3_origin())) {
                body.SetFrictionDirection(fb.frictionDirection.ToVec3());
            }
            if (!fb.contactMotorDirection.ToVec3().equals(getVec3_origin())) {
                body.SetContactMotorDirection(fb.contactMotorDirection.ToVec3());
            }

            // update table to find the nearest articulated figure body for a joint of the skeletal model
            animator.GetJointList(fb.containedJoints.toString(), jointList);
            for (i = 0; i < jointList.size(); i++) {
                if (jointBody.oGet(jointList.get(i)) != -1) {
                    /*jointHandle_t*/
                    gameLocal.Warning("%s: joint '%s' is already contained by body '%s'",
                            name, animator.GetJointName(jointList.get(i)),
                            physicsObj.GetBody(jointBody.oGet(jointList.get(i))).GetName());
                }
                jointBody.oSet(jointList.get(i), id);
            }

            return true;
        }

        protected boolean LoadConstraint(final idDeclAF_Constraint fc) {
            idAFBody body1, body2;
            idAngles angles = new idAngles();
            idMat3 axis = new idMat3();

            body1 = physicsObj.GetBody(fc.body1.toString());
            body2 = physicsObj.GetBody(fc.body2.toString());

            switch (fc.type) {
                case DECLAF_CONSTRAINT_FIXED: {
                    idAFConstraint_Fixed c;
                    c = (idAFConstraint_Fixed) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_Fixed(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    break;
                }
                case DECLAF_CONSTRAINT_BALLANDSOCKETJOINT: {
                    idAFConstraint_BallAndSocketJoint c;
                    c = (idAFConstraint_BallAndSocketJoint) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_BallAndSocketJoint(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    c.SetAnchor(fc.anchor.ToVec3());
                    c.SetFriction(fc.friction);
                    switch (fc.limit) {
                        case idDeclAF_Constraint.LIMIT_CONE: {
                            c.SetConeLimit(fc.limitAxis.ToVec3(), fc.limitAngles[0], fc.shaft[0].ToVec3());
                            break;
                        }
                        case idDeclAF_Constraint.LIMIT_PYRAMID: {
                            angles.oSet(fc.limitAxis.ToVec3().ToAngles());
                            angles.roll = fc.limitAngles[2];
                            axis.oSet(angles.ToMat3());
                            c.SetPyramidLimit(axis.oGet(0), axis.oGet(1), fc.limitAngles[0], fc.limitAngles[1], fc.shaft[0].ToVec3());
                            break;
                        }
                        default: {
                            c.SetNoLimit();
                            break;
                        }
                    }
                    break;
                }
                case DECLAF_CONSTRAINT_UNIVERSALJOINT: {
                    idAFConstraint_UniversalJoint c;
                    c = (idAFConstraint_UniversalJoint) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_UniversalJoint(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    c.SetAnchor(fc.anchor.ToVec3());
                    c.SetShafts(fc.shaft[0].ToVec3(), fc.shaft[1].ToVec3());
                    c.SetFriction(fc.friction);
                    switch (fc.limit) {
                        case idDeclAF_Constraint.LIMIT_CONE: {
                            c.SetConeLimit(fc.limitAxis.ToVec3(), fc.limitAngles[0]);
                            break;
                        }
                        case idDeclAF_Constraint.LIMIT_PYRAMID: {
                            angles.oSet(fc.limitAxis.ToVec3().ToAngles());
                            angles.roll = fc.limitAngles[2];
                            axis.oSet(angles.ToMat3());
                            c.SetPyramidLimit(axis.oGet(0), axis.oGet(1), fc.limitAngles[0], fc.limitAngles[1]);
                            break;
                        }
                        default: {
                            c.SetNoLimit();
                            break;
                        }
                    }
                    break;
                }
                case DECLAF_CONSTRAINT_HINGE: {
                    idAFConstraint_Hinge c;
                    c = (idAFConstraint_Hinge) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_Hinge(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    c.SetAnchor(fc.anchor.ToVec3());
                    c.SetAxis(fc.axis.ToVec3());
                    c.SetFriction(fc.friction);
                    switch (fc.limit) {
                        case idDeclAF_Constraint.LIMIT_CONE: {
                            final idVec3 left = new idVec3(), up = new idVec3();
                            final idVec3 axis2 = new idVec3(), shaft = new idVec3();
                            fc.axis.ToVec3().OrthogonalBasis(left, up);
                            axis2.oSet(left.oMultiply(new idRotation(getVec3_origin(), fc.axis.ToVec3(), fc.limitAngles[0])));
                            shaft.oSet(left.oMultiply(new idRotation(getVec3_origin(), fc.axis.ToVec3(), fc.limitAngles[2])));
                            c.SetLimit(axis2, fc.limitAngles[1], shaft);
                            break;
                        }
                        default: {
                            c.SetNoLimit();
                            break;
                        }
                    }
                    break;
                }
                case DECLAF_CONSTRAINT_SLIDER: {
                    idAFConstraint_Slider c;
                    c = (idAFConstraint_Slider) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_Slider(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    c.SetAxis(fc.axis.ToVec3());
                    break;
                }
                case DECLAF_CONSTRAINT_SPRING: {
                    idAFConstraint_Spring c;
                    c = (idAFConstraint_Spring) physicsObj.GetConstraint(fc.name.toString());
                    if (c != null) {
                        c.SetBody1(body1);
                        c.SetBody2(body2);
                    } else {
                        c = new idAFConstraint_Spring(fc.name, body1, body2);
                        physicsObj.AddConstraint(c);
                    }
                    c.SetAnchor(fc.anchor.ToVec3(), fc.anchor2.ToVec3());
                    c.SetSpring(fc.stretch, fc.compress, fc.damping, fc.restLength);
                    c.SetLimit(fc.minLength, fc.maxLength);
                    break;
                }
            }
            return true;
        }

        protected boolean TestSolid() {
            int i;
            idAFBody body;
            trace_s trace = new trace_s();
//	idStr str;
            boolean solid;

            if (!IsLoaded()) {
                return false;
            }

            if (!af_testSolid.GetBool()) {
                return false;
            }

            solid = false;

            for (i = 0; i < physicsObj.GetNumBodies(); i++) {
                body = physicsObj.GetBody(i);
                if (gameLocal.clip.Translation(trace, body.GetWorldOrigin(), body.GetWorldOrigin(), body.GetClipModel(), body.GetWorldAxis(), body.GetClipMask(), self)) {
                    float depth = Math.abs(trace.c.point.oMultiply(trace.c.normal) - trace.c.dist);

                    body.SetWorldOrigin(body.GetWorldOrigin().oPlus(trace.c.normal.oMultiply(depth + 8.0f)));

                    gameLocal.DWarning("%s: body '%s' stuck in %d (normal = %.2f %.2f %.2f, depth = %.2f)", self.name,
                            body.GetName(), trace.c.contents, trace.c.normal.x, trace.c.normal.y, trace.c.normal.z, depth);
                    solid = true;

                }
            }
            return solid;
        }
    }

    /*
     ================
     GetJointTransform
     ================
     */
    static class GetJointTransform extends getJointTransform_t {

        static final getJointTransform_t INSTANCE = new GetJointTransform();

        private GetJointTransform() {
        }

        @Override
        public boolean run(Object model, idJointMat[] frame, String jointName, final idVec3 origin, idMat3 axis) {
            int/*jointHandle_t*/ joint;

//	joint = reinterpret_cast<idAnimator *>(model).GetJointHandle( jointName );
            joint = ((idAnimator) model).GetJointHandle(jointName);
//	if ( ( joint >= 0 ) && ( joint < reinterpret_cast<idAnimator *>(model).NumJoints() ) ) {
            if ((joint >= 0) && (joint < ((idAnimator) model).NumJoints())) {
                origin.oSet(frame[joint].ToVec3());
                axis.oSet(frame[joint].ToMat3());
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean run(Object model, idJointMat[] frame, idStr jointName, final idVec3 origin, idMat3 axis) {
            return run(model, frame, jointName.toString(), origin, axis);
        }
    }

}
