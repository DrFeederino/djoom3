package neo.Game.Animation;

import neo.Game.Game_local;
import neo.Game.Script.Script_Program.function_t;
import neo.Sound.snd_shader.idSoundShader;
import neo.framework.DeclSkin.idDeclSkin;
import neo.idlib.BV.Bounds.idBounds;
import neo.idlib.Lib.idException;
import neo.idlib.Text.Str.idStr;
import neo.idlib.Text.Token.idToken;
import neo.idlib.containers.HashIndex.idHashIndex;
import neo.idlib.containers.idStrList;
import neo.idlib.geometry.JointTransform.idJointQuat;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Quat.idCQuat;
import neo.idlib.math.Quat.idQuat;
import neo.idlib.math.Vector.idVec3;

import java.util.ArrayList;
import java.util.HashMap;

import static neo.Game.Animation.Anim.AFJointModType_t.AF_JOINTMOD_AXIS;
import static neo.Game.Game_local.animationLib;
import static neo.Game.Game_local.gameLocal;
import static neo.Renderer.Model.*;
import static neo.TempDump.NOT;
import static neo.TempDump.indexOf;
import static neo.idlib.Text.Lexer.*;
import static neo.idlib.math.Matrix.idMat3.getMat3_identity;
import static neo.idlib.math.Simd.SIMDProcessor;

/**
 *
 */
public class Anim {

    //
//
// animation channels.  make sure to change script/doom_defs.script if you add any channels, or change their order
//
    public static final int ANIMCHANNEL_ALL = 0;
    public static final int ANIMCHANNEL_EYELIDS = 4;
    public static final int ANIMCHANNEL_HEAD = 3;
    public static final int ANIMCHANNEL_LEGS = 2;
    public static final int ANIMCHANNEL_TORSO = 1;
    public static final int ANIM_MaxAnimsPerChannel = 3;
    public static final int ANIM_MaxSyncedAnims = 3;
    //
// animation channels
// these can be changed by modmakers and licensees to be whatever they need.
    public static final int ANIM_NumAnimChannels = 5;
    //
    public static final int ANIM_QX = (1 << 3);// BIT(3);
    public static final int ANIM_QY = (1 << 4);// BIT(4);

    public static final int ANIM_QZ = (1 << 5);// BIT(5);

    public static final int ANIM_TX = (1 << 0);// BIT(0);

    public static final int ANIM_TY = (1 << 1);// BIT(1);

    public static final int ANIM_TZ = (1 << 2);// BIT(2);

    // for converting from 24 frames per second to milliseconds
    public static int FRAME2MS(int framenum) {
        return (framenum * 1000) / 24;
    }

    /*
     ==============================================================================================

     idAFPoseJointMod

     ==============================================================================================
     */
    public enum AFJointModType_t {
        AF_JOINTMOD_AXIS,
        AF_JOINTMOD_ORIGIN,
        AF_JOINTMOD_BOTH
    }

    public enum frameCommandType_t {
        FC_SCRIPTFUNCTION,
        FC_SCRIPTFUNCTIONOBJECT,
        FC_EVENTFUNCTION,
        FC_SOUND,
        FC_SOUND_VOICE,
        FC_SOUND_VOICE2,
        FC_SOUND_BODY,
        FC_SOUND_BODY2,
        FC_SOUND_BODY3,
        FC_SOUND_WEAPON,
        FC_SOUND_ITEM,
        FC_SOUND_GLOBAL,
        FC_SOUND_CHATTER,
        FC_SKIN,
        FC_TRIGGER,
        FC_TRIGGER_SMOKE_PARTICLE,
        FC_MELEE,
        FC_DIRECTDAMAGE,
        FC_BEGINATTACK,
        FC_ENDATTACK,
        FC_MUZZLEFLASH,
        FC_CREATEMISSILE,
        FC_LAUNCHMISSILE,
        FC_FIREMISSILEATTARGET,
        FC_FOOTSTEP,
        FC_LEFTFOOT,
        FC_RIGHTFOOT,
        FC_ENABLE_EYE_FOCUS,
        FC_DISABLE_EYE_FOCUS,
        FC_FX,
        FC_DISABLE_GRAVITY,
        FC_ENABLE_GRAVITY,
        FC_JUMP,
        FC_ENABLE_CLIP,
        FC_DISABLE_CLIP,
        FC_ENABLE_WALK_IK,
        FC_DISABLE_WALK_IK,
        FC_ENABLE_LEG_IK,
        FC_DISABLE_LEG_IK,
        FC_RECORDDEMO,
        FC_AVIGAME
    }

    //
// joint modifier modes.  make sure to change script/doom_defs.script if you add any, or change their order.
//
    public enum jointModTransform_t {
        JOINTMOD_NONE, // no modification
        JOINTMOD_LOCAL, // modifies the joint's position or orientation in joint local space
        JOINTMOD_LOCAL_OVERRIDE, // sets the joint's position or orientation in joint local space
        JOINTMOD_WORLD, // modifies joint's position or orientation in model space
        JOINTMOD_WORLD_OVERRIDE        // sets the joint's position or orientation in model space
    }

    public static final class frameBlend_t {
        float backlerp;
        int cycleCount;    // how many times the anim has wrapped to the begining (0 for clamped anims)
        int frame1;
        int frame2;
        float frontlerp;
    }

    public static final class jointAnimInfo_t {
        int animBits;
        int firstComponent;
        int nameIndex;
        int parentNum;
    }

    public static final class jointInfo_t {
        int channel;
        int/*jointHandle_t*/ num;
        int/*jointHandle_t*/ parentNum;
    }

    public static final class jointMod_t {

        final idMat3 mat = new idMat3();
        int/*jointHandle_t*/ jointnum;
        final idVec3 pos = new idVec3();
        jointModTransform_t transform_axis;
        jointModTransform_t transform_pos;
    }

    public static final class frameLookup_t {
        int firstCommand;
        int num;
    }

    public static final class frameCommand_t {
        public function_t function;
        public int index;
        public idDeclSkin skin;
        // union {
        public idSoundShader soundShader;
        public idStr string;
        public frameCommandType_t type;
        // };
    }

    public static final class animFlags_t {
        public boolean ai_no_turn;//: 1;
        public boolean anim_turn;//: 1;
        public boolean prevent_idle_override;//: 1;
        public boolean random_cycle_start;//: 1;

        public animFlags_t() {
        }

        public animFlags_t(animFlags_t val) {
            this.ai_no_turn = val.ai_no_turn;
            this.anim_turn = val.anim_turn;
            this.prevent_idle_override = val.prevent_idle_override;
            this.random_cycle_start = val.random_cycle_start;
        }
    }

    /*
     ==============================================================================================

     idMD5Anim

     ==============================================================================================
     */
    public static class idMD5Anim {

        private final ArrayList<idJointQuat> baseFrame;
        private final ArrayList<idBounds> bounds;
        private final ArrayList<Float> componentFrames;
        private final ArrayList<jointAnimInfo_t> jointInfo;
        private final idStr name;
        private final idVec3 totaldelta;
        private int animLength;
        private int frameRate;
        private int numAnimatedComponents;
        private int numFrames;
        private int numJoints;
        private int ref_count;
//
//

        public idMD5Anim() {
            ref_count = 0;
            numFrames = 0;
            numJoints = 0;
            frameRate = 24;
            animLength = 0;
            bounds = new ArrayList<>();
            jointInfo = new ArrayList<>();
            baseFrame = new ArrayList<>();
            componentFrames = new ArrayList<>();
            name = new idStr();
            totaldelta = new idVec3();
        }
        // ~idMD5Anim();

        public void Free() {
            numFrames = 0;
            numJoints = 0;
            frameRate = 24;
            animLength = 0;
            name.oSet("");
            totaldelta.Zero();
            jointInfo.clear();
            bounds.clear();
            componentFrames.clear();
            baseFrame.clear();
        }

        public boolean Reload() {
            String filename;

            filename = name.toString();
            Free();

            return LoadAnim(filename);
        }

        public int/*size_t*/ Allocated() {
            int/*size_t*/ size = bounds.size() + jointInfo.size() + componentFrames.size() + name.Allocated();
            return size;
        }

        public boolean LoadAnim(final String filename) throws idException {
            int version;
            idLexer parser = new idLexer(LEXFL_ALLOWPATHNAMES | LEXFL_NOSTRINGESCAPECHARS | LEXFL_NOSTRINGCONCAT);
            final idToken token = new idToken();
            int i, j;
            int num;

            if (!parser.LoadFile(filename)) {
                return false;
            }

            Free();

            name.oSet(filename);

            parser.ExpectTokenString(MD5_VERSION_STRING);
            version = parser.ParseInt();
            if (version != MD5_VERSION) {
                parser.Error("Invalid version %d.  Should be version %d\n", version, MD5_VERSION);
            }

            // skip the commandline
            parser.ExpectTokenString("commandline");
            parser.ReadToken(token);

            // parse num frames
            parser.ExpectTokenString("numFrames");
            numFrames = parser.ParseInt();
            if (numFrames <= 0) {
                parser.Error("Invalid number of frames: %d", numFrames);
            }

            // parse num joints
            parser.ExpectTokenString("numJoints");
            numJoints = parser.ParseInt();
            if (numJoints <= 0) {
                parser.Error("Invalid number of joints: %d", numJoints);
            }

            // parse frame rate
            parser.ExpectTokenString("frameRate");
            frameRate = parser.ParseInt();
            if (frameRate < 0) {
                parser.Error("Invalid frame rate: %d", frameRate);
            }

            // parse number of animated components
            parser.ExpectTokenString("numAnimatedComponents");
            numAnimatedComponents = parser.ParseInt();
            if ((numAnimatedComponents < 0) || (numAnimatedComponents > numJoints * 6)) {
                parser.Error("Invalid number of animated components: %d", numAnimatedComponents);
            }

            // parse the hierarchy
//            jointInfo.SetGranularity(1);
//            jointInfo.SetNum(numJoints);
            parser.ExpectTokenString("hierarchy");
            parser.ExpectTokenString("{");
            for (i = 0; i < numJoints; i++) {
                parser.ReadToken(token);
                final jointAnimInfo_t joint = new jointAnimInfo_t(); //jointInfo[ i ].nameIndex = animationLib.JointIndex( token );
                if (i >= jointInfo.size()) {
                    jointInfo.add(i, joint);
                } else {
                    jointInfo.set(i, joint);
                }
                joint.nameIndex = animationLib.JointIndex(token.toString());

                // parse parent num
                joint.parentNum = parser.ParseInt();
                if (joint.parentNum >= i) {
                    parser.Error("Invalid parent num: %d", joint.parentNum);
                }

                if ((i != 0) && (joint.parentNum < 0)) {
                    parser.Error("Animations may have only one root joint");
                }

                // parse anim bits
                joint.animBits = parser.ParseInt();
                if ((joint.animBits & ~63) != 0) {
                    parser.Error("Invalid anim bits: %d", joint.animBits);
                }

                // parse first component
                joint.firstComponent = parser.ParseInt();
                if ((numAnimatedComponents > 0) && ((joint.firstComponent < 0) || (joint.firstComponent >= numAnimatedComponents))) {
                    parser.Error("Invalid first component: %d", joint.firstComponent);
                }
            }

            parser.ExpectTokenString("}");

            // parse bounds
            parser.ExpectTokenString("bounds");
            parser.ExpectTokenString("{");
//            bounds.SetGranularity(1);
//            bounds.SetNum(numFrames);
            for (i = 0; i < numFrames; i++) {
                final idBounds bound = new idBounds();
                if (i >= bounds.size()) {
                    bounds.add(i, bound);
                } else {
                    bounds.set(i, bound);
                }
                parser.Parse1DMatrix(3, bound.oGet(0));
                parser.Parse1DMatrix(3, bound.oGet(1));
            }
            parser.ExpectTokenString("}");

            // parse base frame
//            baseFrame.SetGranularity(1);
//            baseFrame.SetNum(numJoints);
            parser.ExpectTokenString("baseframe");
            parser.ExpectTokenString("{");
            for (i = 0; i < numJoints; i++) {
                idCQuat q = new idCQuat();
                final idJointQuat frame = new idJointQuat();
                if (i >= baseFrame.size()) {
                    baseFrame.add(i, frame);
                } else {
                    baseFrame.set(i, frame);
                }
                parser.Parse1DMatrix(3, frame.t);
                parser.Parse1DMatrix(3, q);
                baseFrame.get(i).q.oSet(q.ToQuat());
            }
            parser.ExpectTokenString("}");

            // parse frames
//            componentFrames.SetGranularity(1);
//            componentFrames.SetNum(numAnimatedComponents * numFrames);

            int c_ptr = 0;
            for (i = 0; i < numFrames; i++) {
                parser.ExpectTokenString("frame");
                num = parser.ParseInt();
                if (num != i) {
                    parser.Error("Expected frame number %d", i);
                }
                parser.ExpectTokenString("{");

                for (j = 0; j < numAnimatedComponents; j++, c_ptr++) {
                    if (c_ptr >= componentFrames.size()) {
                        componentFrames.add(c_ptr, parser.ParseFloat());
                    } else {
                        componentFrames.set(c_ptr, parser.ParseFloat());
                    }
                }

                parser.ExpectTokenString("}");
            }


            // get total move delta
            if (0 == numAnimatedComponents) {
                totaldelta.Zero();
            } else {
                c_ptr = jointInfo.get(0).firstComponent;
                if ((jointInfo.get(0).animBits & ANIM_TX) != 0) {
                    for (i = 0; i < numFrames; i++) {
                        final int index = c_ptr + numAnimatedComponents * i;
                        componentFrames.set(index, componentFrames.get(index) - baseFrame.get(0).t.x);
                    }
                    totaldelta.x = componentFrames.get(numAnimatedComponents * (numFrames - 1));
                    c_ptr++;
                } else {
                    totaldelta.x = 0.0f;
                }
                if ((jointInfo.get(0).animBits & ANIM_TY) != 0) {
                    for (i = 0; i < numFrames; i++) {
                        final int index = c_ptr + numAnimatedComponents * i;
                        componentFrames.set(index, componentFrames.get(index) - baseFrame.get(0).t.y);
                    }
                    totaldelta.y = componentFrames.get(c_ptr + numAnimatedComponents * (numFrames - 1));
                    c_ptr++;
                } else {
                    totaldelta.y = 0.0f;
                }
                if ((jointInfo.get(0).animBits & ANIM_TZ) != 0) {
                    for (i = 0; i < numFrames; i++) {
                        final int index = c_ptr + numAnimatedComponents * i;
                        componentFrames.set(index, componentFrames.get(index) - baseFrame.get(0).t.z);
                    }
                    totaldelta.z = componentFrames.get(c_ptr + numAnimatedComponents * (numFrames - 1));
                } else {
                    totaldelta.z = 0.0f;
                }
            }
            baseFrame.get(0).t.Zero();

            // we don't count last frame because it would cause a 1 frame pause at the end
            animLength = ((numFrames - 1) * 1000 + frameRate - 1) / frameRate;

            // done
            return true;
        }

        public boolean LoadAnim(final idStr filename) throws idException {
            return LoadAnim(filename.toString());
        }

        public void IncreaseRefs() {
            ref_count++;
        }

        public void DecreaseRefs() {
            ref_count--;
        }

        public int NumRefs() {
            return ref_count;
        }

        public void CheckModelHierarchy(final idRenderModel model) {
            int i;
            int jointNum;
            int parent;

            if (jointInfo.size() != model.NumJoints()) {
                Game_local.idGameLocal.Error("Model '%s' has different # of joints than anim '%s'", model.Name(), name);
            }

            final idMD5Joint[] modelJoints = model.GetJoints();
            for (i = 0; i < jointInfo.size(); i++) {
                jointNum = jointInfo.get(i).nameIndex;
                if (!modelJoints[i].name.toString().equals(animationLib.JointName(jointNum))) {
                    Game_local.idGameLocal.Error("Model '%s''s joint names don't match anim '%s''s", model.Name(), name);
                }
                if (modelJoints[i].parent != null) {
                    parent = indexOf(modelJoints[i].parent, modelJoints);
                } else {
                    parent = -1;
                }
                if (parent != jointInfo.get(i).parentNum) {
                    Game_local.idGameLocal.Error("Model '%s' has different joint hierarchy than anim '%s'", model.Name(), name);
                }
            }
        }

        public void GetInterpolatedFrame(final frameBlend_t frame, final idJointQuat[] joints, final int[] index, int numIndexes) {
            int i, numLerpJoints;
//	 Float				[]frame1;
//	 Float				[]frame2;
            final int f1_ptr, f2_ptr;
            final Float[] jointframe1, jointframe2;
            int jf1_ptr, jf2_ptr;
            jointAnimInfo_t infoPtr;
            int animBits;
            idJointQuat[] blendJoints;
            idJointQuat jointPtr;
            idJointQuat blendPtr;
            int[] lerpIndex;

            // copy the baseframe
            System.arraycopy(baseFrame.toArray(), 0, joints, 0, baseFrame.size());
            if (0 == numAnimatedComponents) {
                // just use the base frame
                return;
            }

            blendJoints = new idJointQuat[baseFrame.size()];
            lerpIndex = new int[baseFrame.size()];
            numLerpJoints = 0;

//	frame1 = componentFrames.Ptr()   ;
//	frame2 = componentFrames.Ptr();
            f1_ptr = frame.frame1 * numAnimatedComponents;
            f2_ptr = frame.frame2 * numAnimatedComponents;
            jointframe1 = jointframe2 = componentFrames.toArray(Float[]::new);

            for (i = 0; i < numIndexes; i++) {
                int j = index[i];
                jointPtr = joints[j];
                blendPtr = blendJoints[j] = new idJointQuat();
                infoPtr = jointInfo.get(j);

                animBits = infoPtr.animBits;
                if (animBits != 0) {

                    lerpIndex[numLerpJoints++] = j;

//			jointframe2 = frame2 ;
                    jf1_ptr = f1_ptr + infoPtr.firstComponent;
                    jf2_ptr = f2_ptr + infoPtr.firstComponent;

                    switch (animBits & (ANIM_TX | ANIM_TY | ANIM_TZ)) {
                        case 0:
                            blendPtr.t.oSet(jointPtr.t);
                            break;
                        case ANIM_TX:
                            jointPtr.t.x = jointframe1[jf1_ptr + 0];
                            blendPtr.t.x = jointframe2[jf2_ptr + 0];
                            blendPtr.t.y = jointPtr.t.y;
                            blendPtr.t.z = jointPtr.t.z;
                            jf1_ptr++;
                            jf2_ptr++;
                            break;
                        case ANIM_TY:
                            jointPtr.t.y = jointframe1[jf1_ptr + 0];
                            blendPtr.t.y = jointframe2[jf2_ptr + 0];
                            blendPtr.t.x = jointPtr.t.x;
                            blendPtr.t.z = jointPtr.t.z;
                            jf1_ptr++;
                            jf2_ptr++;
                            break;
                        case ANIM_TZ:
                            jointPtr.t.z = jointframe1[jf1_ptr + 0];
                            blendPtr.t.z = jointframe2[jf2_ptr + 0];
                            blendPtr.t.x = jointPtr.t.x;
                            blendPtr.t.y = jointPtr.t.y;
                            jf1_ptr++;
                            jf2_ptr++;
                            break;
                        case ANIM_TX | ANIM_TY:
                            jointPtr.t.x = jointframe1[jf1_ptr + 0];
                            jointPtr.t.y = jointframe1[jf1_ptr + 1];
                            blendPtr.t.x = jointframe2[jf2_ptr + 0];
                            blendPtr.t.y = jointframe2[jf2_ptr + 1];
                            blendPtr.t.z = jointPtr.t.z;
                            jf1_ptr += 2;
                            jf2_ptr += 2;
                            break;
                        case ANIM_TX | ANIM_TZ:
                            jointPtr.t.x = jointframe1[jf1_ptr + 0];
                            jointPtr.t.z = jointframe1[jf1_ptr + 1];
                            blendPtr.t.x = jointframe2[jf2_ptr + 0];
                            blendPtr.t.z = jointframe2[jf2_ptr + 1];
                            blendPtr.t.y = jointPtr.t.y;
                            jf1_ptr += 2;
                            jf2_ptr += 2;
                            break;
                        case ANIM_TY | ANIM_TZ:
                            jointPtr.t.y = jointframe1[jf1_ptr + 0];
                            jointPtr.t.z = jointframe1[jf1_ptr + 1];
                            blendPtr.t.y = jointframe2[jf2_ptr + 0];
                            blendPtr.t.z = jointframe2[jf2_ptr + 1];
                            blendPtr.t.x = jointPtr.t.x;
                            jf1_ptr += 2;
                            jf2_ptr += 2;
                            break;
                        case ANIM_TX | ANIM_TY | ANIM_TZ:
                            jointPtr.t.x = jointframe1[jf1_ptr + 0];
                            jointPtr.t.y = jointframe1[jf1_ptr + 1];
                            jointPtr.t.z = jointframe1[jf1_ptr + 2];
                            blendPtr.t.x = jointframe2[jf2_ptr + 0];
                            blendPtr.t.y = jointframe2[jf2_ptr + 1];
                            blendPtr.t.z = jointframe2[jf2_ptr + 2];
                            jf1_ptr += 3;
                            jf2_ptr += 3;
                            break;
                    }

                    switch (animBits & (ANIM_QX | ANIM_QY | ANIM_QZ)) {
                        case 0:
                            blendPtr.q.oSet(jointPtr.q);
                            break;
                        case ANIM_QX:
                            jointPtr.q.x = jointframe1[jf1_ptr + 0];
                            blendPtr.q.x = jointframe2[jf2_ptr + 0];
                            blendPtr.q.y = jointPtr.q.y;
                            blendPtr.q.z = jointPtr.q.z;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QY:
                            jointPtr.q.y = jointframe1[jf1_ptr + 0];
                            blendPtr.q.y = jointframe2[jf2_ptr + 0];
                            blendPtr.q.x = jointPtr.q.x;
                            blendPtr.q.z = jointPtr.q.z;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QZ:
                            jointPtr.q.z = jointframe1[jf1_ptr + 0];
                            blendPtr.q.z = jointframe2[jf2_ptr + 0];
                            blendPtr.q.x = jointPtr.q.x;
                            blendPtr.q.y = jointPtr.q.y;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QX | ANIM_QY:
                            jointPtr.q.x = jointframe1[jf1_ptr + 0];
                            jointPtr.q.y = jointframe1[jf1_ptr + 1];
                            blendPtr.q.x = jointframe2[jf2_ptr + 0];
                            blendPtr.q.y = jointframe2[jf2_ptr + 1];
                            blendPtr.q.z = jointPtr.q.z;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QX | ANIM_QZ:
                            jointPtr.q.x = jointframe1[jf1_ptr + 0];
                            jointPtr.q.z = jointframe1[jf1_ptr + 1];
                            blendPtr.q.x = jointframe2[jf2_ptr + 0];
                            blendPtr.q.z = jointframe2[jf2_ptr + 1];
                            blendPtr.q.y = jointPtr.q.y;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QY | ANIM_QZ:
                            jointPtr.q.y = jointframe1[jf1_ptr + 0];
                            jointPtr.q.z = jointframe1[jf1_ptr + 1];
                            blendPtr.q.y = jointframe2[jf2_ptr + 0];
                            blendPtr.q.z = jointframe2[jf2_ptr + 1];
                            blendPtr.q.x = jointPtr.q.x;
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                        case ANIM_QX | ANIM_QY | ANIM_QZ:
                            jointPtr.q.x = jointframe1[jf1_ptr + 0];
                            jointPtr.q.y = jointframe1[jf1_ptr + 1];
                            jointPtr.q.z = jointframe1[jf1_ptr + 2];
                            blendPtr.q.x = jointframe2[jf2_ptr + 0];
                            blendPtr.q.y = jointframe2[jf2_ptr + 1];
                            blendPtr.q.z = jointframe2[jf2_ptr + 2];
                            jointPtr.q.w = jointPtr.q.CalcW();
                            blendPtr.q.w = blendPtr.q.CalcW();
                            break;
                    }
                }
            }

            SIMDProcessor.BlendJoints(joints, blendJoints, frame.backlerp, lerpIndex, numLerpJoints);

            if (frame.cycleCount != 0) {
                joints[0].t.oPluSet(totaldelta.oMultiply(frame.cycleCount));
            }
        }

        public void GetSingleFrame(int framenum, final idJointQuat[] joints, final int[] index, int numIndexes) {
            int i;
//	float				[]frame;
            final int f_ptr;
            Float[] jointframe;
            int jf_ptr;
            int animBits;
            idJointQuat jointPtr;
            jointAnimInfo_t infoPtr;

            // copy the baseframe
            //SIMDProcessor.Memcpy(joints, baseFrame, baseFrame.size() /* sizeof( baseFrame[ 0 ] )*/);
            System.arraycopy(baseFrame.toArray(), 0, joints, 0, baseFrame.size());
            if ((framenum == 0) || 0 == numAnimatedComponents) {
                // just use the base frame
                return;
            }

//	frame = &componentFrames[ framenum * numAnimatedComponents ];
            f_ptr = framenum * numAnimatedComponents;

            for (i = 0; i < numIndexes; i++) {
                int j = index[i];
                jointPtr = joints[j];
                infoPtr = jointInfo.get(j);

                animBits = infoPtr.animBits;
                if (animBits != 0) {

                    jointframe = componentFrames.toArray(Float[]::new);
                    jf_ptr = f_ptr + infoPtr.firstComponent;

                    if ((animBits & (ANIM_TX | ANIM_TY | ANIM_TZ)) != 0) {

                        if ((animBits & ANIM_TX) != 0) {
                            jointPtr.t.x = jointframe[jf_ptr++];
                        }

                        if ((animBits & ANIM_TY) != 0) {
                            jointPtr.t.y = jointframe[jf_ptr++];
                        }

                        if ((animBits & ANIM_TZ) != 0) {
                            jointPtr.t.z = jointframe[jf_ptr++];
                        }
                    }

                    if ((animBits & (ANIM_QX | ANIM_QY | ANIM_QZ)) != 0) {

                        if ((animBits & ANIM_QX) != 0) {
                            jointPtr.q.x = jointframe[jf_ptr++];
                        }

                        if ((animBits & ANIM_QY) != 0) {
                            jointPtr.q.y = jointframe[jf_ptr++];
                        }

                        if ((animBits & ANIM_QZ) != 0) {
                            jointPtr.q.z = jointframe[jf_ptr];
                        }

                        jointPtr.q.w = jointPtr.q.CalcW();
                    }
                }
            }
        }

        public int Length() {
            return animLength;
        }

        public int NumFrames() {
            return numFrames;
        }

        public int NumJoints() {
            return numJoints;
        }

        public idVec3 TotalMovementDelta() {
            return totaldelta;
        }

        public String Name() {
            return name.toString();
        }

        public void GetFrameBlend(int framenum, final frameBlend_t frame) {    // frame 1 is first frame
            frame.cycleCount = 0;
            frame.backlerp = 0.0f;
            frame.frontlerp = 1.0f;

            // frame 1 is first frame
            framenum--;
            if (framenum < 0) {
                framenum = 0;
            } else if (framenum >= numFrames) {
                framenum = numFrames - 1;
            }

            frame.frame1 = framenum;
            frame.frame2 = framenum;
        }

        public void ConvertTimeToFrame(int time, int cyclecount, final frameBlend_t frame) {
            int frameTime;
            int frameNum;

            if (numFrames <= 1) {
                frame.frame1 = 0;
                frame.frame2 = 0;
                frame.backlerp = 0.0f;
                frame.frontlerp = 1.0f;
                frame.cycleCount = 0;
                return;
            }

            if (time <= 0) {
                frame.frame1 = 0;
                frame.frame2 = 1;
                frame.backlerp = 0.0f;
                frame.frontlerp = 1.0f;
                frame.cycleCount = 0;
                return;
            }

            frameTime = time * frameRate;
            frameNum = frameTime / 1000;
            frame.cycleCount = frameNum / (numFrames - 1);

            if ((cyclecount > 0) && (frame.cycleCount >= cyclecount)) {
                frame.cycleCount = cyclecount - 1;
                frame.frame1 = numFrames - 1;
                frame.frame2 = frame.frame1;
                frame.backlerp = 0.0f;
                frame.frontlerp = 1.0f;
                return;
            }

            frame.frame1 = frameNum % (numFrames - 1);
            frame.frame2 = frame.frame1 + 1;
            if (frame.frame2 >= numFrames) {
                frame.frame2 = 0;
            }

            frame.backlerp = (frameTime % 1000) * 0.001f;
            frame.frontlerp = 1.0f - frame.backlerp;
        }

        public void GetOrigin(final idVec3 offset, int time, int cyclecount) {
            frameBlend_t frame = new frameBlend_t();
            int c1_ptr, c2_ptr;

            offset.oSet(baseFrame.get(0).t);
            if (0 == (jointInfo.get(0).animBits & (ANIM_TX | ANIM_TY | ANIM_TZ))) {
                // just use the baseframe
                return;
            }

            ConvertTimeToFrame(time, cyclecount, frame);

            Float[] componentPtr1 = componentFrames.toArray(Float[]::new);
            c1_ptr = numAnimatedComponents * frame.frame1 + jointInfo.get(0).firstComponent;
            Float[] componentPtr2 = componentFrames.toArray(Float[]::new);
            c2_ptr = numAnimatedComponents * frame.frame2 + jointInfo.get(0).firstComponent;

            if ((jointInfo.get(0).animBits & ANIM_TX) != 0) {
                offset.x = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
                c1_ptr++;
                c2_ptr++;
            }

            if ((jointInfo.get(0).animBits & ANIM_TY) != 0) {
                offset.y = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
                c1_ptr++;
                c2_ptr++;
            }

            if ((jointInfo.get(0).animBits & ANIM_TZ) != 0) {
                offset.z = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
            }

            if (frame.cycleCount != 0) {
                offset.oPluSet(totaldelta.oMultiply(frame.cycleCount));
            }
        }

        public void GetOriginRotation(final idQuat rotation, int time, int cyclecount) {
            frameBlend_t frame = new frameBlend_t();
            int animBits;
            int j1_ptr, j2_ptr;

            animBits = jointInfo.get(0).animBits;
            if (NOT(animBits & (ANIM_QX | ANIM_QY | ANIM_QZ))) {
                // just use the baseframe
                rotation.oSet(baseFrame.get(0).q);
                return;
            }

            ConvertTimeToFrame(time, cyclecount, frame);

            Float[] jointframe1 = componentFrames.toArray(Float[]::new);
            j1_ptr = numAnimatedComponents * frame.frame1 + jointInfo.get(0).firstComponent;
            Float[] jointframe2 = componentFrames.toArray(Float[]::new);
            j2_ptr = numAnimatedComponents * frame.frame2 + jointInfo.get(0).firstComponent;

            if ((animBits & ANIM_TX) != 0) {
                j1_ptr++;
                j2_ptr++;
            }

            if ((animBits & ANIM_TY) != 0) {
                j1_ptr++;
                j2_ptr++;
            }

            if ((animBits & ANIM_TZ) != 0) {
                j1_ptr++;
                j2_ptr++;
            }

            final idQuat q1 = new idQuat();
            final idQuat q2 = new idQuat();

            switch (animBits & (ANIM_QX | ANIM_QY | ANIM_QZ)) {
                case ANIM_QX:
                    q1.x = jointframe1[j1_ptr + 0];
                    q2.x = jointframe2[j2_ptr + 0];
                    q1.y = baseFrame.get(0).q.y;
                    q2.y = q1.y;
                    q1.z = baseFrame.get(0).q.z;
                    q2.z = q1.z;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QY:
                    q1.y = jointframe1[j1_ptr + 0];
                    q2.y = jointframe2[j2_ptr + 0];
                    q1.x = baseFrame.get(0).q.x;
                    q2.x = q1.x;
                    q1.z = baseFrame.get(0).q.z;
                    q2.z = q1.z;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QZ:
                    q1.z = jointframe1[j1_ptr + 0];
                    q2.z = jointframe2[j2_ptr + 0];
                    q1.x = baseFrame.get(0).q.x;
                    q2.x = q1.x;
                    q1.y = baseFrame.get(0).q.y;
                    q2.y = q1.y;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QX | ANIM_QY:
                    q1.x = jointframe1[j1_ptr + 0];
                    q1.y = jointframe1[j1_ptr + 1];
                    q2.x = jointframe2[j2_ptr + 0];
                    q2.y = jointframe2[j2_ptr + 1];
                    q1.z = baseFrame.get(0).q.z;
                    q2.z = q1.z;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QX | ANIM_QZ:
                    q1.x = jointframe1[j1_ptr + 0];
                    q1.z = jointframe1[j1_ptr + 1];
                    q2.x = jointframe2[j2_ptr + 0];
                    q2.z = jointframe2[j2_ptr + 1];
                    q1.y = baseFrame.get(0).q.y;
                    q2.y = q1.y;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QY | ANIM_QZ:
                    q1.y = jointframe1[j1_ptr + 0];
                    q1.z = jointframe1[j1_ptr + 1];
                    q2.y = jointframe2[j2_ptr + 0];
                    q2.z = jointframe2[j2_ptr + 1];
                    q1.x = baseFrame.get(0).q.x;
                    q2.x = q1.x;
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
                case ANIM_QX | ANIM_QY | ANIM_QZ:
                    q1.x = jointframe1[j1_ptr + 0];
                    q1.y = jointframe1[j1_ptr + 1];
                    q1.z = jointframe1[j1_ptr + 2];
                    q2.x = jointframe2[j2_ptr + 0];
                    q2.y = jointframe2[j2_ptr + 1];
                    q2.z = jointframe2[j2_ptr + 2];
                    q1.w = q1.CalcW();
                    q2.w = q2.CalcW();
                    break;
            }

            rotation.Slerp(q1, q2, frame.backlerp);
        }

        public void GetBounds(final idBounds bnds, int time, int cyclecount) {
            frameBlend_t frame = new frameBlend_t();
            final idVec3 offset = new idVec3();
            int c1_ptr, c2_ptr;

            ConvertTimeToFrame(time, cyclecount, frame);

            bnds.oSet(bounds.get(frame.frame1));
            bnds.AddBounds(bounds.get(frame.frame2));

            // origin position
            offset.oSet(baseFrame.get(0).t);
            if ((jointInfo.get(0).animBits & (ANIM_TX | ANIM_TY | ANIM_TZ)) != 0) {
                Float[] componentPtr1 = componentFrames.toArray(Float[]::new);
                c1_ptr = numAnimatedComponents * frame.frame1 + jointInfo.get(0).firstComponent;
                Float[] componentPtr2 = componentFrames.toArray(Float[]::new);
                c2_ptr = numAnimatedComponents * frame.frame2 + jointInfo.get(0).firstComponent;

                if ((jointInfo.get(0).animBits & ANIM_TX) != 0) {
                    offset.x = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
                    c1_ptr++;
                    c2_ptr++;
                }

                if ((jointInfo.get(0).animBits & ANIM_TY) != 0) {
                    offset.y = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
                    c1_ptr++;
                    c2_ptr++;
                }

                if ((jointInfo.get(0).animBits & ANIM_TZ) != 0) {
                    offset.z = componentPtr1[c1_ptr] * frame.frontlerp + componentPtr2[c2_ptr] * frame.backlerp;
                }
            }

            bnds.oMinSet(offset);
        }
    }

    public static class idAFPoseJointMod {
        public final idVec3 origin;
        public final idMat3 axis;
        public AFJointModType_t mod;
        //
        //

        public idAFPoseJointMod() {
            mod = AF_JOINTMOD_AXIS;
            axis = getMat3_identity();
            origin = new idVec3();
        }
    }

    /*
     ==============================================================================================

     idAnimManager

     ==============================================================================================
     */
    public static class idAnimManager {

        // ~idAnimManager();
        public static boolean forceExport = false;
        private final HashMap<String, idMD5Anim> animations;
        private final idStrList jointnames;
        //
        //
        private final idHashIndex jointnamesHash;

        public idAnimManager() {
            animations = new HashMap<>();
            jointnames = new idStrList();
            jointnamesHash = new idHashIndex();
        }

        public void Shutdown() {
            animations.clear();
            jointnames.clear();
            jointnamesHash.Free();
        }

        public idMD5Anim GetAnim(final String name) {
            idMD5Anim[] animPtr = {null};
            idMD5Anim anim;

            // see if it has been asked for before
            anim = animations.get(name);
            if (anim == null) {
                idStr extension = new idStr();
                idStr filename = new idStr(name);

                filename.ExtractFileExtension(extension);
                if (!extension.toString().equals(MD5_ANIM_EXT)) {
                    return null;
                }
                anim = new idMD5Anim();
                if (!anim.LoadAnim(filename)) {
                    gameLocal.Warning("Couldn't load anim: '%s'", filename);
                    anim = null;
                }
                animations.put(filename.toString(), anim);
            }
            return anim;
        }

        public void ReloadAnims() {
            int i;
            idMD5Anim animptr;
            idMD5Anim[] animValues;

            for (i = 0, animValues = animations.values().toArray(idMD5Anim[]::new); i < animations.values().size(); i++) {
                animptr = animValues[i];
                if (animptr != null) {// && *animptr ) {
                    animptr.Reload();
                }
            }
        }

        public void ListAnims() {
            int i;
            idMD5Anim animptr;
            idMD5Anim anim;
            int/*size_t*/ size;
            int/*size_t*/ s;
            int/*size_t*/ namesize;
            int num;
            idMD5Anim[] animValues;
            num = 0;
            size = 0;
            for (i = 0, animValues = animations.values().toArray(idMD5Anim[]::new); i < animations.values().size(); i++) {
                animptr = animValues[i];
                if (animptr != null) {// && *animptr ) {//TODO:check this locl shit
                    anim = animptr;
                    s = 0;
                    gameLocal.Printf("%8d bytes : %2d refs : %s\n", s, anim.NumRefs(), anim.Name());
                    size += s;
                    num++;
                }
            }

            namesize = jointnames.sizeStrings() + jointnamesHash.Size();
            for (i = 0; i < jointnames.size(); i++) {
                namesize += jointnames.get(i).Size();
            }

            gameLocal.Printf("\n%d memory used in %d anims\n", size, num);
            gameLocal.Printf("%d memory used in %d joint names\n", namesize, jointnames.size());
        }

        public int JointIndex(final String name) {
            int i, hash;

            hash = jointnamesHash.GenerateKey(name);
            for (i = jointnamesHash.First(hash); i != -1; i = jointnamesHash.Next(i)) {
                if (jointnames.get(i).Cmp(name) == 0) {
                    return i;
                }
            }

            i = jointnames.add(name);
            jointnamesHash.Add(hash, i);
            return i;
        }

        public String JointName(int index) {
            return jointnames.get(index).toString();
        }
//
//        public void ClearAnimsInUse();
//

        public void FlushUnusedAnims() {
            int i;
            idMD5Anim animptr;
            idMD5Anim[] animValues;
            final ArrayList<idMD5Anim> removeAnims = new ArrayList<>();

            for (i = 0, animValues = animations.values().toArray(idMD5Anim[]::new); i < animations.values().size(); i++) {
                animptr = animValues[i];
                if (animptr != null) {//&& *animptr ) {
                    if (animptr.NumRefs() <= 0) {
                        removeAnims.add(animptr);
                    }
                }
            }

            for (i = 0; i < removeAnims.size(); i++) {
                animations.remove(removeAnims.get(i).Name());
            }
        }
    }

}
