package neo.Game.Animation

import neo.Game.*
import neo.Game.AI.AI_Events
import neo.Game.Animation.Anim.AFJointModType_t
import neo.Game.Animation.Anim.animFlags_t
import neo.Game.Animation.Anim.frameBlend_t
import neo.Game.Animation.Anim.frameCommandType_t
import neo.Game.Animation.Anim.frameCommand_t
import neo.Game.Animation.Anim.frameLookup_t
import neo.Game.Animation.Anim.idAFPoseJointMod
import neo.Game.Animation.Anim.idMD5Anim
import neo.Game.Animation.Anim.jointInfo_t
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Animation.Anim.jointMod_t
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.FX.idEntityFx
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Renderer.*
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.DeclManager
import neo.framework.DeclManager.declState_t
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.BinSearch
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Quat.idQuat
import neo.idlib.math.Vector.idVec3
import java.util.*
import java.util.function.Consumer
import java.util.function.IntFunction
import java.util.stream.Stream

/**
 *
 */
object Anim_Blend {
    val channelNames /*[ ANIM_NumAnimChannels ]*/: Array<String?>? = arrayOf(
        "all", "torso", "legs", "head", "eyelids"
    )
    const val VELOCITY_MOVE = false
    fun ANIM_GetModelDefFromEntityDef(args: idDict?): idDeclModelDef? {
        val modelDef: idDeclModelDef
        val name = args.GetString("model")
        modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name, false)
        return if (modelDef != null && modelDef.ModelHandle() != null) {
            modelDef
        } else null
    }

    /*
     ==============================================================================================

     idAnim

     ==============================================================================================
     */
    class idAnim {
        private val frameCommands: ArrayList<frameCommand_t?>? = ArrayList()
        private val frameLookup: ArrayList<frameLookup_t?>? = ArrayList<Any?>()
        private var anims: Array<idMD5Anim?>? = arrayOfNulls<idMD5Anim?>(Anim.ANIM_MaxSyncedAnims)
        private var flags: animFlags_t?
        private var modelDef: idDeclModelDef?
        private val name: idStr? = idStr()
        private var numAnims: Int
        private val realname: idStr? = idStr()

        //
        //
        constructor() {
            modelDef = null
            numAnims = 0
            flags = animFlags_t()
        }

        constructor(modelDef: idDeclModelDef?, anim: idAnim?) {
            var i: Int
            this.modelDef = modelDef
            numAnims = anim.numAnims
            name.oSet(anim.name)
            realname.oSet(anim.realname)
            flags = animFlags_t(anim.flags)
            anims = arrayOfNulls<idMD5Anim?>(anims.size)
            i = 0
            while (i < numAnims) {
                anims.get(i) = anim.anims.get(i)
                anims.get(i).IncreaseRefs()
                i++
            }

            //frameLookup.SetNum(anim.frameLookup.Num());
            if (anim.frameLookup.size > 0) {
                i = 0
                while (i < anim.frameLookup.size) {
                    val frameLookup_t = anim.frameLookup.get(i)
                    // this is probably overkill, since it's an object creation, frameLookup is "empty"
                    //if (i >= frameLookup.size()) {
                    frameLookup.add(frameLookup_t)
                    i++
                }
            }

            //frameCommands.SetNum(anim.frameCommands.Num());
            i = 0
            while (i < frameCommands.size) {
                if (i >= frameCommands.size) {
                    frameCommands.add(anim.frameCommands.get(i))
                } else {
                    frameCommands.set(i, anim.frameCommands.get(i))
                }
                if (frameCommands.get(i).string != null) {
                    frameCommands.get(i).string = idStr(anim.frameCommands.get(i).string)
                }
                i++
            }
        }

        // ~idAnim();
        fun SetAnim(
            modelDef: idDeclModelDef?,
            sourceName: String?,
            animName: String?,
            num: Int,
            md5anims: Array<idMD5Anim?>? /*[ ANIM_MaxSyncedAnims ]*/
        ) {
            var i: Int
            this.modelDef = modelDef
            i = 0
            while (i < numAnims) {
                anims.get(i).DecreaseRefs()
                anims.get(i) = null
                i++
            }
            assert(num > 0 && num <= Anim.ANIM_MaxSyncedAnims)
            numAnims = num
            realname.oSet(sourceName)
            name.oSet(animName)
            i = 0
            while (i < num) {
                anims.get(i) = md5anims.get(i)
                anims.get(i).IncreaseRefs()
                i++
            }

//	memset( &flags, 0, sizeof( flags ) );
            flags = animFlags_t()
            frameCommands.forEach(Consumer { frame: frameCommand_t? -> frame.string = null })
            frameLookup.clear()
            frameCommands.clear()
        }

        fun Name(): String? {
            return name.toString()
        }

        fun FullName(): String? {
            return realname.toString()
        }

        /*
         =====================
         idAnim::MD5Anim

         index 0 will never be NULL.  Any anim >= NumAnims will return NULL.
         =====================
         */
        fun MD5Anim(num: Int): idMD5Anim? {
            return if (anims == null || anims.get(0) == null) {
                null
            } else anims.get(num)
        }

        fun modelDef(): idDeclModelDef? {
            return modelDef
        }

        fun Length(): Int {
            return if (null == anims.get(0)) {
                0
            } else anims.get(0).Length()
        }

        fun NumFrames(): Int {
            return if (null == anims.get(0)) {
                0
            } else anims.get(0).NumFrames()
        }

        fun NumAnims(): Int {
            return numAnims
        }

        fun TotalMovementDelta(): idVec3? {
            return if (null == anims.get(0)) {
                Vector.getVec3_zero()
            } else anims.get(0).TotalMovementDelta()
        }

        fun GetOrigin(offset: idVec3?, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (null == anims.get(animNum)) {
                offset.Zero()
                return false
            }
            anims.get(animNum).GetOrigin(offset, currentTime, cyclecount)
            return true
        }

        fun GetOriginRotation(rotation: idQuat?, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (null == anims.get(animNum)) {
                rotation.Set(0.0f, 0.0f, 0.0f, 1.0f)
                return false
            }
            anims.get(animNum).GetOriginRotation(rotation, currentTime, cyclecount)
            return true
        }

        fun GetBounds(bounds: idBounds?, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (null == anims.get(animNum)) {
                return false
            }
            anims.get(animNum).GetBounds(bounds, currentTime, cyclecount)
            return true
        }

        /*
         =====================
         idAnim::AddFrameCommand

         Returns NULL if no error.
         =====================
         */
        @Throws(idException::class)
        fun AddFrameCommand(modelDef: idDeclModelDef?, framenum: Int, src: idLexer?, def: idDict?): String? {
            var framenum = framenum
            var i: Int
            val index: Int
            var text: idStr
            var funcname: idStr
            val fc: frameCommand_t
            val token = idToken()
            val jointInfo: jointInfo_t?

            // make sure we're within bounds
            if (framenum < 1 || framenum > anims.get(0).NumFrames()) {
                return Str.va("Frame %d out of range", framenum)
            }

            // frame numbers are 1 based in .def files, but 0 based internally
            framenum--

//	memset( &fc, 0, sizeof( fc ) );
            fc = frameCommand_t()
            if (!src.ReadTokenOnLine(token)) {
                return "Unexpected end of line"
            }
            //System.out.printf("Anim Token is %s%n", token.toString());
            if (token.toString() == "call") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SCRIPTFUNCTION
                fc.function = Game_local.gameLocal.program.FindFunction(token.toString())
                if (TempDump.NOT(fc.function)) {
                    return Str.va("Function '%s' not found", token)
                }
            } else if (token.toString() == "object_call") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SCRIPTFUNCTIONOBJECT
                fc.string = idStr(token)
            } else if (token.toString() == "event") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_EVENTFUNCTION
                val ev: idEventDef = idEventDef.Companion.FindEvent(token.toString())
                    ?: return Str.va("Event '%s' not found", token)
                if (ev.GetNumArgs() != 0) {
                    return Str.va("Event '%s' has arguments", token)
                }
                fc.string = idStr(token)
            } else if (token.toString() == "sound_voice2") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_VOICE2
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_voice") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_VOICE
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body2") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY2
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body3") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY3
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_weapon") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_WEAPON
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_global") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_GLOBAL
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_item") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_ITEM
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_chatter") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_CHATTER
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string = idStr(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "skin") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SKIN
                if (token.toString() == "none") {
                    fc.skin = null
                } else {
                    fc.skin = DeclManager.declManager.FindSkin(token)
                    if (TempDump.NOT(fc.skin)) {
                        return Str.va("Skin '%s' not found", token)
                    }
                }
            } else if (token.toString() == "fx") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_FX
                if (TempDump.NOT(DeclManager.declManager.FindType(declType_t.DECL_FX, token))) {
                    return Str.va("fx '%s' not found", token)
                }
                fc.string = idStr(token)
            } else if (token.toString() == "trigger") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_TRIGGER
                fc.string = idStr(token)
            } else if (token.toString() == "triggerSmokeParticle") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_TRIGGER_SMOKE_PARTICLE
                fc.string = idStr(token)
            } else if (token.toString() == "melee") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_MELEE
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string = idStr(token)
            } else if (token.toString() == "direct_damage") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_DIRECTDAMAGE
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string = idStr(token)
            } else if (token.toString() == "attack_begin") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_BEGINATTACK
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string = idStr(token)
            } else if (token.toString() == "attack_end") {
                fc.type = frameCommandType_t.FC_ENDATTACK
            } else if (token.toString() == "muzzle_flash") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (!token.IsEmpty() && TempDump.NOT(modelDef.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_MUZZLEFLASH
                fc.string = idStr(token)
            } else if (token.toString() == "muzzle_flash") {
                fc.type = frameCommandType_t.FC_MUZZLEFLASH
                fc.string = idStr("")
            } else if (token.toString() == "create_missile") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (TempDump.NOT(modelDef.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_CREATEMISSILE
                fc.string = idStr(token)
            } else if (token.toString() == "launch_missile") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (TempDump.NOT(modelDef.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_LAUNCHMISSILE
                fc.string = idStr(token)
            } else if (token.toString() == "fire_missile_at_target") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                jointInfo = modelDef.FindJoint(token.toString())
                if (TempDump.NOT(jointInfo)) {
                    return Str.va("Joint '%s' not found", token)
                }
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_FIREMISSILEATTARGET
                fc.string = idStr(token)
                fc.index = jointInfo.num
            } else if (token.toString() == "footstep") {
                fc.type = frameCommandType_t.FC_FOOTSTEP
            } else if (token.toString() == "leftfoot") {
                fc.type = frameCommandType_t.FC_LEFTFOOT
            } else if (token.toString() == "rightfoot") {
                fc.type = frameCommandType_t.FC_RIGHTFOOT
            } else if (token.toString() == "enableEyeFocus") {
                fc.type = frameCommandType_t.FC_ENABLE_EYE_FOCUS
            } else if (token.toString() == "disableEyeFocus") {
                fc.type = frameCommandType_t.FC_DISABLE_EYE_FOCUS
            } else if (token.toString() == "disableGravity") {
                fc.type = frameCommandType_t.FC_DISABLE_GRAVITY
            } else if (token.toString() == "enableGravity") {
                fc.type = frameCommandType_t.FC_ENABLE_GRAVITY
            } else if (token.toString() == "jump") {
                fc.type = frameCommandType_t.FC_JUMP
            } else if (token.toString() == "enableClip") {
                fc.type = frameCommandType_t.FC_ENABLE_CLIP
            } else if (token.toString() == "disableClip") {
                fc.type = frameCommandType_t.FC_DISABLE_CLIP
            } else if (token.toString() == "enableWalkIK") {
                fc.type = frameCommandType_t.FC_ENABLE_WALK_IK
            } else if (token.toString() == "disableWalkIK") {
                fc.type = frameCommandType_t.FC_DISABLE_WALK_IK
            } else if (token.toString() == "enableLegIK") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_ENABLE_LEG_IK
                fc.index = TempDump.atoi(token.toString())
            } else if (token.toString() == "disableLegIK") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_DISABLE_LEG_IK
                fc.index = TempDump.atoi(token.toString())
            } else if (token.toString() == "recordDemo") {
                fc.type = frameCommandType_t.FC_RECORDDEMO
                if (src.ReadTokenOnLine(token)) {
                    fc.string = idStr(token)
                }
            } else if (token.toString() == "aviGame") {
                fc.type = frameCommandType_t.FC_AVIGAME
                if (src.ReadTokenOnLine(token)) {
                    fc.string = idStr(token)
                }
            } else {
                println(String.format("didnt find anim token %s", token.toString()))
                return Str.va("Unknown command '%s'", token)
            }

            // check if we've initialized the frame lookup table
            if (0 == frameLookup.size) {
                frameLookup.clear() // just in cae?
                // we haven't, so allocate the table and initialize it
//                frameLookup.SetGranularity(1);
//                frameLookup.SetNum(anims[0].NumFrames());
                i = 0
                while (i < anims.get(0).NumFrames()) {

                    // init with setting size as anims[0].NumFrames()!
                    frameLookup.add(frameLookup_t())
                    i++
                }
                //frameLookup.trimToSize();
            }

            // allocate space for a new command
            //frameCommands.Alloc();

            // calculate the index of the new command
            index = frameLookup.get(framenum).firstCommand + frameLookup.get(framenum).num

            // move all commands from our index onward up one to give us space for our new command
            // size is the actual size of the list, not size + 1 like it is in idList
            i = frameCommands.size
            while (i > index) {
                if (i >= frameCommands.size) {
                    frameCommands.add(frameCommands.get(i - 1))
                } else {
                    frameCommands.set(i, frameCommands.get(i - 1))
                }
                i--
            }

            // fix the indices of any later frames to account for the inserted command
            i = framenum + 1
            while (i < frameLookup.size) {
                frameLookup.get(i).firstCommand++
                i++
            }

            // store the new command
            if (index >= frameCommands.size) {
                frameCommands.add(fc)
            } else {
                frameCommands.set(index, fc)
            }

            // increase the number of commands on this frame
            frameLookup.get(framenum).num++

            // return with no error
            return null
        }

        fun CallFrameCommands(ent: idEntity?, from: Int, to: Int) {
            var index: Int
            var end: Int
            var frame: Int
            val numframes: Int
            numframes = anims.get(0).NumFrames()
            frame = from
            while (frame != to) {
                frame++
                if (frame >= numframes) {
                    frame = 0
                }
                index = frameLookup.get(frame).firstCommand
                end = index + frameLookup.get(frame).num
                while (index < end) {
                    val command = frameCommands.get(index++)
                    when (command.type) {
                        frameCommandType_t.FC_SCRIPTFUNCTION -> {
                            Game_local.gameLocal.CallFrameCommand(ent, command.function)
                        }
                        frameCommandType_t.FC_SCRIPTFUNCTIONOBJECT -> {
                            Game_local.gameLocal.CallObjectFrameCommand(ent, command.string.toString())
                        }
                        frameCommandType_t.FC_EVENTFUNCTION -> {
                            val ev: idEventDef = idEventDef.Companion.FindEvent(command.string.toString())
                            ent.ProcessEvent(ev)
                        }
                        frameCommandType_t.FC_SOUND -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ANY,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ANY,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_VOICE -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_VOICE,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_voice' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_VOICE,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_VOICE2 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_VOICE2,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_voice2' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_VOICE2,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY2 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY2,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body2' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY2,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY3 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY3,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body3' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY3,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_WEAPON -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_WEAPON,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_weapon' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_WEAPON,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_GLOBAL -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ANY,
                                        Sound.SSF_GLOBAL,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_global' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ANY,
                                    Sound.SSF_GLOBAL,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_ITEM -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ITEM,
                                        0,
                                        false,
                                        null
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_item' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ITEM,
                                    0,
                                    false,
                                    null
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_CHATTER -> {
                            if (ent.CanPlayChatterSounds()) {
                                if (TempDump.NOT(command.soundShader)) {
                                    if (!ent.StartSound(
                                            command.string.toString(),
                                            gameSoundChannel_t.SND_CHANNEL_VOICE,
                                            0,
                                            false,
                                            null
                                        )
                                    ) {
                                        Game_local.gameLocal.Warning(
                                            "Framecommand 'sound_chatter' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                            ent.name, FullName(), frame + 1, command.string
                                        )
                                    }
                                } else {
                                    ent.StartSoundShader(
                                        command.soundShader,
                                        gameSoundChannel_t.SND_CHANNEL_VOICE,
                                        0,
                                        false,
                                        null
                                    )
                                }
                            }
                        }
                        frameCommandType_t.FC_FX -> {
                            idEntityFx.Companion.StartFx(command.string.toString(), null, null, ent, true)
                        }
                        frameCommandType_t.FC_SKIN -> {
                            ent.SetSkin(command.skin)
                        }
                        frameCommandType_t.FC_TRIGGER -> {
                            var target: idEntity?
                            target = Game_local.gameLocal.FindEntity(command.string.toString())
                            if (target != null) {
                                target.Signal(signalNum_t.SIG_TRIGGER)
                                target.ProcessEvent(Entity.EV_Activate, ent)
                                target.TriggerGuis()
                            } else {
                                Game_local.gameLocal.Warning(
                                    "Framecommand 'trigger' on entity '%s', anim '%s', frame %d: Could not find entity '%s'",
                                    ent.name, FullName(), frame + 1, command.string
                                )
                            }
                        }
                        frameCommandType_t.FC_TRIGGER_SMOKE_PARTICLE -> {
                            ent.ProcessEvent(AI_Events.AI_TriggerParticles, command.string.toString())
                        }
                        frameCommandType_t.FC_MELEE -> {
                            ent.ProcessEvent(AI_Events.AI_AttackMelee, command.string.toString())
                        }
                        frameCommandType_t.FC_DIRECTDAMAGE -> {
                            ent.ProcessEvent(AI_Events.AI_DirectDamage, command.string.toString())
                        }
                        frameCommandType_t.FC_BEGINATTACK -> {
                            ent.ProcessEvent(AI_Events.AI_BeginAttack, command.string.toString())
                        }
                        frameCommandType_t.FC_ENDATTACK -> {
                            ent.ProcessEvent(AI_Events.AI_EndAttack)
                        }
                        frameCommandType_t.FC_MUZZLEFLASH -> {
                            ent.ProcessEvent(AI_Events.AI_MuzzleFlash, command.string.toString())
                        }
                        frameCommandType_t.FC_CREATEMISSILE -> {
                            ent.ProcessEvent(AI_Events.AI_CreateMissile, command.string.toString())
                        }
                        frameCommandType_t.FC_LAUNCHMISSILE -> {
                            ent.ProcessEvent(AI_Events.AI_AttackMissile, command.string.toString())
                        }
                        frameCommandType_t.FC_FIREMISSILEATTARGET -> {
                            ent.ProcessEvent(
                                AI_Events.AI_FireMissileAtTarget,
                                modelDef.GetJointName(command.index),
                                command.string.toString()
                            )
                        }
                        frameCommandType_t.FC_FOOTSTEP -> {
                            ent.ProcessEvent(Actor.EV_Footstep)
                        }
                        frameCommandType_t.FC_LEFTFOOT -> {
                            ent.ProcessEvent(Actor.EV_FootstepLeft)
                        }
                        frameCommandType_t.FC_RIGHTFOOT -> {
                            ent.ProcessEvent(Actor.EV_FootstepRight)
                        }
                        frameCommandType_t.FC_ENABLE_EYE_FOCUS -> {
                            ent.ProcessEvent(Actor.AI_EnableEyeFocus)
                        }
                        frameCommandType_t.FC_DISABLE_EYE_FOCUS -> {
                            ent.ProcessEvent(Actor.AI_DisableEyeFocus)
                        }
                        frameCommandType_t.FC_DISABLE_GRAVITY -> {
                            ent.ProcessEvent(AI_Events.AI_DisableGravity)
                        }
                        frameCommandType_t.FC_ENABLE_GRAVITY -> {
                            ent.ProcessEvent(AI_Events.AI_EnableGravity)
                        }
                        frameCommandType_t.FC_JUMP -> {
                            ent.ProcessEvent(AI_Events.AI_JumpFrame)
                        }
                        frameCommandType_t.FC_ENABLE_CLIP -> {
                            ent.ProcessEvent(AI_Events.AI_EnableClip)
                        }
                        frameCommandType_t.FC_DISABLE_CLIP -> {
                            ent.ProcessEvent(AI_Events.AI_DisableClip)
                        }
                        frameCommandType_t.FC_ENABLE_WALK_IK -> {
                            ent.ProcessEvent(Actor.EV_EnableWalkIK)
                        }
                        frameCommandType_t.FC_DISABLE_WALK_IK -> {
                            ent.ProcessEvent(Actor.EV_DisableWalkIK)
                        }
                        frameCommandType_t.FC_ENABLE_LEG_IK -> {
                            ent.ProcessEvent(Actor.EV_EnableLegIK, command.index)
                        }
                        frameCommandType_t.FC_DISABLE_LEG_IK -> {
                            ent.ProcessEvent(Actor.EV_DisableLegIK, command.index)
                        }
                        frameCommandType_t.FC_RECORDDEMO -> {
                            if (command.string != null) {
                                CmdSystem.cmdSystem.BufferCommandText(
                                    cmdExecution_t.CMD_EXEC_NOW,
                                    Str.va("recordDemo %s", command.string)
                                )
                            } else {
                                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "stoprecording")
                            }
                        }
                        frameCommandType_t.FC_AVIGAME -> {
                            if (command.string != null) {
                                CmdSystem.cmdSystem.BufferCommandText(
                                    cmdExecution_t.CMD_EXEC_NOW,
                                    Str.va("aviGame %s", command.string)
                                )
                            } else {
                                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "aviGame")
                            }
                        }
                    }
                }
            }
        }

        fun HasFrameCommands(): Boolean {
            return 0 != frameCommands.size
        }

        // returns first frame (zero based) that command occurs.  returns -1 if not found.
        fun FindFrameForFrameCommand(framecommand: frameCommandType_t?, command: Array<frameCommand_t?>?): Int {
            var frame: Int
            var index: Int
            val numframes: Int
            var end: Int
            if (0 == frameCommands.size) {
                return -1
            }
            numframes = anims.get(0).NumFrames()
            frame = 0
            while (frame < numframes) {
                end = frameLookup.get(frame).firstCommand + frameLookup.get(frame).num
                index = frameLookup.get(frame).firstCommand
                while (index < end) {
                    if (frameCommands.get(index).type == framecommand) {
                        if (command != null) {
                            command[0] = frameCommands.get(index)
                        }
                        return frame
                    }
                    index++
                }
                frame++
            }
            if (command != null) {
                command[0] = null
            }
            return -1
        }

        fun SetAnimFlags(animflags: animFlags_t?) {
            flags = animFlags_t(animflags)
        }

        fun GetAnimFlags(): animFlags_t? {
            return flags
        }
    }

    /*
     ==============================================================================================

     idDeclModelDef

     ==============================================================================================
     */
    class idDeclModelDef : idDecl {
        private val anims: ArrayList<idAnim?>? = ArrayList()
        private val channelJoints: Array<ArrayList<Int?>?>? = arrayOfNulls<ArrayList<*>?>(Anim.ANIM_NumAnimChannels)
        private val jointParents: ArrayList<Int?>? = ArrayList()
        private val joints: ArrayList<jointInfo_t?>? = ArrayList()
        private val offset: idVec3? = idVec3()
        private var modelHandle: idRenderModel?

        //
        //
        private var skin: idDeclSkin? = null

        // ~idDeclModelDef();
        constructor() {
            modelHandle = null
            skin = null
            offset.Zero()
            for (i in 0 until Anim.ANIM_NumAnimChannels) {
                channelJoints.get(i) = ArrayList()
            }
        }

        constructor(def: idDeclModelDef?) {
            Collections.copy(anims, def.anims)
            for (i in channelJoints.indices) {
                Collections.copy(channelJoints.get(i), def.channelJoints.get(i))
            }
            Collections.copy(jointParents, def.jointParents)
            Collections.copy(joints, def.joints)
            offset.oSet(def.offset)
            modelHandle = def.modelHandle
        }

        override fun DefaultDefinition(): String? {
            return "{ }"
        }

        @Throws(idException::class)
        override fun Parse(text: String?, textLength: Int): Boolean {
            var i: Int
            var num: Int
            val filename = idStr()
            val extension = idStr()
            var md5joint: Int
            var md5joints: Array<idMD5Joint?>?
            val src = idLexer()
            val token = idToken()
            val token2 = idToken()
            var jointnames: String
            var channel: Int
            var   /*jointHandle_t*/jointnum: Int
            val jointList = ArrayList<Int?>()
            var numDefaultAnims: Int
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")
            numDefaultAnims = 0
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (token.toString() == "inherit") {
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    val copy =
                        DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, token2, false) as idDeclModelDef
                    if (null == copy) {
                        idLib.common.Warning("Unknown model definition '%s'", token2)
                    } else if (copy.GetState() == declState_t.DS_DEFAULTED) {
                        idLib.common.Warning("inherited model definition '%s' defaulted", token2)
                        MakeDefault()
                        return false
                    } else {
                        CopyDecl(copy)
                        numDefaultAnims = anims.size
                    }
                } else if (token.toString() == "skin") {
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    skin = DeclManager.declManager.FindSkin(token2)
                    if (null == skin) {
                        src.Warning("Skin '%s' not found", token2)
                        MakeDefault()
                        return false
                    }
                } else if (token.toString() == "mesh") {
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    filename.oSet(token2)
                    filename.ExtractFileExtension(extension)
                    if (extension != Model.MD5_MESH_EXT) {
                        src.Warning("Invalid model for MD5 mesh")
                        MakeDefault()
                        return false
                    }
                    modelHandle = ModelManager.renderModelManager.FindModel(filename.toString())
                    if (null == modelHandle) {
                        src.Warning("Model '%s' not found", filename)
                        MakeDefault()
                        return false
                    }
                    if (modelHandle.IsDefaultModel()) {
                        src.Warning("Model '%s' defaulted", filename)
                        MakeDefault()
                        return false
                    }

                    // get the number of joints
                    num = modelHandle.NumJoints()
                    if (0 == num) {
                        src.Warning("Model '%s' has no joints", filename)
                    }

                    // set up the joint hierarchy
                    //joints.SetGranularity(1);
                    //joints.SetNum(num);
                    //jointParents.SetNum(num);
                    //channelJoints[0].SetNum(num);
                    md5joints = modelHandle.GetJoints()
                    md5joint = 0 //md5joints;
                    i = 0
                    while (i < num) {
                        val jointInfo_t = jointInfo_t()
                        jointInfo_t.channel = Anim.ANIMCHANNEL_ALL
                        if (i >= joints.size) {
                            joints.add(jointInfo_t)
                        } else {
                            joints.set(i, jointInfo_t)
                        }
                        joints.get(i).num = i
                        if (md5joints[md5joint].parent != null) {
                            joints.get(i).parentNum = TempDump.indexOf(md5joints[md5joint].parent, md5joints)
                        } else {
                            joints.get(i).parentNum = Model.INVALID_JOINT
                        }
                        if (i >= jointParents.size) {
                            jointParents.add(joints.get(i).parentNum)
                        } else {
                            jointParents.set(i, joints.get(i).parentNum)
                        }
                        if (i >= channelJoints.get(0).size) {
                            channelJoints.get(0).add(i)
                        } else {
                            channelJoints.get(0).set(i, i)
                        }
                        i++
                        md5joint++
                    }
                } else if (token.toString() == "remove") {
                    // removes any anims whos name matches
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    num = 0
                    i = 0
                    while (i < anims.size) {
                        if (token2.toString() == anims.get(i).Name() || token2.toString() == anims.get(i).FullName()) {
//					delete anims[ i ];
                            anims.removeAt(i) // remove handles both delete and RemoveIndex
                            if (i >= numDefaultAnims) {
                                src.Warning(
                                    "Anim '%s' was not inherited.  Anim should be removed from the model def.",
                                    token2
                                )
                                MakeDefault()
                                return false
                            }
                            i--
                            numDefaultAnims--
                            num++
                            i++
                            continue
                        }
                        i++
                    }
                    if (0 == num) {
                        src.Warning("Couldn't find anim '%s' to remove", token2)
                        MakeDefault()
                        return false
                    }
                } else if (token.toString() == "anim") {
                    if (null == modelHandle) {
                        src.Warning("Must specify mesh before defining anims")
                        MakeDefault()
                        return false
                    }
                    if (!ParseAnim(src, numDefaultAnims)) {
                        MakeDefault()
                        return false
                    }
                } else if (token.toString() == "offset") {
                    if (!src.Parse1DMatrix(3, offset)) {
                        src.Warning("Expected vector following 'offset'")
                        MakeDefault()
                        return false
                    }
                } else if (token.toString() == "channel") {
                    if (null == modelHandle) {
                        src.Warning("Must specify mesh before defining channels")
                        MakeDefault()
                        return false
                    }
                    // set the channel for a group of joints
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    if (!src.CheckTokenString("(")) {
                        src.Warning("Expected { after '%s'\n", token2)
                        MakeDefault()
                        return false
                    }
                    i = Anim.ANIMCHANNEL_ALL + 1
                    while (i < Anim.ANIM_NumAnimChannels) {
                        if (0 == idStr.Companion.Icmp(Anim_Blend.channelNames[i], token2.toString())) {
                            break
                        }
                        i++
                    }
                    if (i >= Anim.ANIM_NumAnimChannels) {
                        src.Warning("Unknown channel '%s'", token2)
                        MakeDefault()
                        return false
                    }
                    channel = i
                    jointnames = ""
                    while (!src.CheckTokenString(")")) {
                        if (!src.ReadToken(token2)) {
                            src.Warning("Unexpected end of file")
                            MakeDefault()
                            return false
                        }
                        jointnames += token2.toString()
                        if (token2.toString() != "*" && token2.toString() != "-") {
                            jointnames += " "
                        }
                    }
                    GetJointList(jointnames, jointList)

                    //channelJoints[channel].SetNum(jointList.Num());
                    //channelJoints[channel].trimToSize();
                    num = 0.also { i = it }
                    while (i < jointList.size) {
                        jointnum = jointList[i]
                        if (joints.get(jointnum).channel != Anim.ANIMCHANNEL_ALL) {
                            src.Warning("Joint '%s' assigned to multiple channels", modelHandle.GetJointName(jointnum))
                            i++
                            continue
                        }
                        joints.get(jointnum).channel = channel
                        if (i >= channelJoints.get(channel).size) {
                            num++
                            channelJoints.get(channel).add(jointnum)
                        } else {
                            channelJoints.get(channel).set(num++, jointnum)
                        }
                        i++
                    }
                    //channelJoints[channel].SetNum(num);
                    //channelJoints[channel].trimToSize();
                } else {
                    src.Warning("unknown token '%s'", token)
                    MakeDefault()
                    return false
                }
            }

            // shrink the anim list down to save space
            //anims.SetGranularity(1);
            // anims.trimToSize();
            return true
        }

        override fun FreeData() {
            anims.clear()
            joints.clear()
            jointParents.clear()
            modelHandle = null
            skin = null
            offset.Zero()
            for (i in 0 until Anim.ANIM_NumAnimChannels) {
                channelJoints.get(i).clear()
            }
        }

        fun Touch() {
            if (modelHandle != null) {
                ModelManager.renderModelManager.FindModel(modelHandle.Name())
            }
        }

        fun GetDefaultSkin(): idDeclSkin? {
            return skin
        }

        fun GetDefaultPose(): Array<idJointQuat?>? {
            return modelHandle.GetDefaultPose()
        }

        fun SetupJoints(
            numJoints: CInt?,
            jointList: Array<idJointMat?>?,
            frameBounds: idBounds?,
            removeOriginOffset: Boolean
        ): Array<idJointMat?>? {
            val num: Int
            val pose: Array<idJointQuat?>?
            val list: Array<idJointMat?>?
            if (null == modelHandle || modelHandle.IsDefaultModel()) {
//                Mem_Free16(jointList);
                for (i in jointList.indices) {
                    jointList.get(i) = null
                }
                frameBounds.Clear()
                return null
            }

            // get the number of joints
            num = modelHandle.NumJoints()
            if (0 == num) {
                idGameLocal.Companion.Error("model '%s' has no joints", modelHandle.Name())
            }

            // set up initial pose for model (with no pose, model is just a jumbled mess)
            list = Stream.generate { idJointMat() }.limit(num.toLong()).toArray { _Dummy_.__Array__() }
            pose = GetDefaultPose()

            // convert the joint quaternions to joint matrices
            Simd.SIMDProcessor.ConvertJointQuatsToJointMats(list, pose, joints.size)

            // check if we offset the model by the origin joint
            if (removeOriginOffset) {
                if (Anim_Blend.VELOCITY_MOVE) {
                    list[0].SetTranslation(idVec3(offset.x, offset.y + pose.get(0).t.y, offset.z + pose.get(0).t.z))
                } else {
                    list[0].SetTranslation(offset)
                }
            } else {
                list[0].SetTranslation(pose.get(0).t.oPlus(offset))
            }

            // transform the joint hierarchy
            Simd.SIMDProcessor.TransformJoints(
                list,
                TempDump.itoi(jointParents.toArray(IntFunction<Array<Int?>?> { _Dummy_.__Array__() })),
                1,
                joints.size - 1
            )
            numJoints.setVal(num)


            // get the bounds of the default pose
            frameBounds.oSet(modelHandle.Bounds(null))
            return list
        }

        fun ModelHandle(): idRenderModel? {
            return modelHandle
        }

        fun GetJointList(jointnames: String?, jointList: ArrayList<Int?>?) {
            var jointname: String?
            var joint: jointInfo_t?
            var child: jointInfo_t?
            var child_i: Int
            var i: Int
            val num: Int
            var getChildren: Boolean
            var subtract: Boolean
            if (null == modelHandle || jointnames.isEmpty()) {
                return
            }
            jointList.clear()
            num = modelHandle.NumJoints()

            // split on and skip whitespaces
            for (name in jointnames.split("\\s+").toTypedArray()) {
                // copy joint name
                jointname = name
                if (jointname.startsWith("-")) {
                    subtract = true
                    jointname = jointname.substring(1)
                } else {
                    subtract = false
                }
                if (jointname.startsWith("*")) {
                    getChildren = true
                    jointname = jointname.substring(1)
                } else {
                    getChildren = false
                }
                joint = FindJoint(jointname)
                if (null == joint) {
                    Game_local.gameLocal.Warning(
                        "Unknown joint '%s' in '%s' for model '%s'",
                        jointname,
                        jointnames,
                        GetName()
                    )
                    continue
                }
                if (!subtract) {
                    jointList.add(joint.num)
                } else {
                    jointList.remove(joint.num as Int)
                }
                if (getChildren) {
                    // include all joint's children
                    child_i = joints.indexOf(joint) + 1
                    i = joint.num + 1
                    while (i < num) {

                        // all children of the joint should follow it in the list.
                        // once we reach a joint without a parent or with a parent
                        // who is earlier in the list than the specified joint, then
                        // we've gone through all it's children.
                        child = joints.get(child_i)
                        if (child.parentNum < joint.num) {
                            break
                        }
                        if (!subtract) {
                            jointList.add(child.num)
                        } else {
                            jointList.remove(child.num as Int)
                        }
                        i++
                        child_i++
                    }
                }
            }
        }

        fun FindJoint(name: String?): jointInfo_t? {
            var i: Int
            val joint: Array<idMD5Joint?>?
            if (null == modelHandle) {
                return null
            }
            joint = modelHandle.GetJoints()
            i = 0
            while (i < joints.size) {
                if (TempDump.NOT(joint[i].name.Icmp(name).toDouble())) {
                    return joints.get(i)
                }
                i++
            }
            return null
        }

        fun NumAnims(): Int {
            return anims.size + 1
        }

        fun GetAnim(index: Int): idAnim? {
            return if (index < 1 || index > anims.size) {
                null
            } else anims.get(index - 1)
        }

        /*
         =====================
         idDeclModelDef::GetSpecificAnim

         Gets the exact anim for the name, without randomization.
         =====================
         */
        fun GetSpecificAnim(name: String?): Int {
            var i: Int

            // find a specific animation
            i = 0
            while (i < anims.size) {
                if (name.startsWith(anims.get(i).FullName())) {
                    return i + 1 // makes no sense, we found it at i, but we return i + 1? is this because all idList entries are shifted by 1?
                    //return i;
                }
                i++
            }

            // didn't find it
            return 0
        }

        fun GetAnim(name: String?): Int {
            var i: Int
            val which: Int
            val animList = IntArray(MAX_ANIMS)
            val numAnims: Int
            val len: Int
            len = name.length
            if (len != 0 && idStr.Companion.CharIsNumeric(name.get(len - 1).code)) {
                // find a specific animation
                return GetSpecificAnim(name)
            }

            // find all animations with same name
            numAnims = 0
            i = 0
            while (i < anims.size) {
                if (anims.get(i).Name() == name) {
                    animList[numAnims++] = i
                    if (numAnims >= MAX_ANIMS) {
                        break
                    }
                }
                i++
            }
            if (0 == numAnims) {
                return 0
            }

            // get a random anim
            //FIXME: don't access gameLocal here?
            which = Game_local.gameLocal.random.RandomInt(numAnims.toDouble())
            return animList[which] + 1
        }

        fun HasAnim(name: String?): Boolean {
            var i: Int

            // find any animations with same name
            i = 0
            while (i < anims.size) {
                if (anims.get(i).Name() == name) {
                    return true
                }
                i++
            }
            return false
        }

        fun GetSkin(): idDeclSkin? {
            return skin
        }

        fun GetModelName(): String? {
            return if (modelHandle != null) {
                modelHandle.Name()
            } else {
                ""
            }
        }

        fun Joints(): ArrayList<jointInfo_t?>? {
            return joints
        }

        fun JointParents(): Array<Int?>? {
            return jointParents.toArray(IntFunction<Array<Int?>?> { _Dummy_.__Array__() })
        }

        fun NumJoints(): Int {
            return joints.size
        }

        fun GetJoint(jointHandle: Int): jointInfo_t? {
            if (jointHandle < 0 || jointHandle > joints.size) {
                idGameLocal.Companion.Error("idDeclModelDef::GetJoint : joint handle out of range")
            }
            return joints.get(jointHandle)
        }

        fun GetJointName(jointHandle: Int): String? {
            val joint: Array<idMD5Joint?>?
            if (null == modelHandle) {
                return null
            }
            if (jointHandle < 0 || jointHandle > joints.size) {
                idGameLocal.Companion.Error("idDeclModelDef::GetJointName : joint handle out of range")
            }
            joint = modelHandle.GetJoints()
            return joint[jointHandle].name.toString()
        }

        fun NumJointsOnChannel(channel: Int): Int {
            if (channel < 0 || channel >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idDeclModelDef::NumJointsOnChannel : channel out of range")
            }
            return channelJoints.get(channel).size
        }

        fun GetChannelJoints(channel: Int): Array<Int?>? {
            if (channel < 0 || channel >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idDeclModelDef::GetChannelJoints : channel out of range")
            }
            return channelJoints.get(channel).toArray(IntFunction<Array<Int?>?> { _Dummy_.__Array__() })
        }

        fun GetVisualOffset(): idVec3? {
            return offset
        }

        private fun CopyDecl(decl: idDeclModelDef?) {
            var i: Int
            FreeData()
            offset.oSet(decl.offset)
            modelHandle = decl.modelHandle
            skin = decl.skin

            //anims.SetNum(decl.anims.Num());
            i = 0
            while (i < decl.anims.size) {
                if (i >= anims.size) {
                    anims.add(idAnim(this, decl.anims.get(i)))
                } else {
                    anims.set(i, idAnim(this, decl.anims.get(i)))
                }
                i++
            }

            //joints.SetNum(decl.joints.Num());
//            memcpy(joints.Ptr(), decl.joints.Ptr(), decl.joints.Num() * sizeof(joints[0]));
            i = 0
            while (i < decl.joints.size) {
                if (i >= joints.size) {
                    joints.add(decl.joints.get(i))
                } else {
                    joints.set(i, decl.joints.get(i))
                }
                i++
            }
            //jointParents.SetNum(decl.jointParents.Num());
//            memcpy(jointParents.Ptr(), decl.jointParents.Ptr(), decl.jointParents.Num() * sizeof(jointParents[0]));
            i = 0
            while (i < decl.jointParents.size) {
                if (i >= jointParents.size) {
                    jointParents.add(decl.jointParents.get(i))
                } else {
                    jointParents.set(i, decl.jointParents.get(i))
                }
                i++
            }
            i = 0
            while (i < Anim.ANIM_NumAnimChannels) {
                channelJoints.get(i) = ArrayList(decl.channelJoints.get(i)) //idList's = is overloaded!
                i++
            }
        }

        private fun ParseAnim(src: idLexer?, numDefaultAnims: Int): Boolean {
            var i: Int
            val len: Int
            val anim: idAnim?
            val md5anims = arrayOfNulls<idMD5Anim?>(Anim.ANIM_MaxSyncedAnims)
            var md5anim: idMD5Anim?
            val alias = idStr()
            val realname = idToken()
            val token = idToken()
            var numAnims: Int
            val flags = animFlags_t()
            numAnims = 0
            if (!src.ReadToken(realname)) {
                src.Warning("Unexpected end of file")
                MakeDefault()
                return false
            }
            alias.oSet(realname)
            i = 0
            while (i < anims.size) {
                if (anims.get(i).FullName().equals(realname.toString(), ignoreCase = true)) {
                    break
                }
                i++
            }
            if (i < anims.size && i >= numDefaultAnims) {
                src.Warning("Duplicate anim '%s'", realname)
                MakeDefault()
                return false
            }
            if (i < numDefaultAnims) {
                anim = anims.get(i)
            } else {
                // create the alias associated with this animation
                anim = idAnim()
                anims.add(anim)
            }

            // random anims end with a number.  find the numeric suffix of the animation.
            len = alias.Length()
            i = len - 1
            while (i > 0) {
                if (!Character.isDigit(alias.oGet(i))) {
                    break
                }
                i--
            }

            // check for zero length name, or a purely numeric name
            if (i <= 0) {
                src.Warning("Invalid animation name '%s'", alias)
                MakeDefault()
                return false
            }

            // remove the numeric suffix
            alias.CapLength(i + 1)

            // parse the anims from the string
            do {
                if (!src.ReadToken(token)) {
                    src.Warning("Unexpected end of file")
                    MakeDefault()
                    return false
                }

                // lookup the animation
                md5anim = Game_local.animationLib.GetAnim(token.toString())
                if (null == md5anim) {
                    src.Warning("Couldn't load anim '%s'", token)
                    MakeDefault()
                    return false
                }
                md5anim.CheckModelHierarchy(modelHandle)
                if (numAnims > 0) {
                    // make sure it's the same length as the other anims
                    if (md5anim.Length() != md5anims[0].Length()) {
                        src.Warning("Anim '%s' does not match length of anim '%s'", md5anim.Name(), md5anims[0].Name())
                        MakeDefault()
                        return false
                    }
                }
                if (numAnims >= Anim.ANIM_MaxSyncedAnims) {
                    src.Warning("Exceeded max synced anims (%d)", Anim.ANIM_MaxSyncedAnims)
                    MakeDefault()
                    return false
                }

                // add it to our list
                md5anims[numAnims] = md5anim
                numAnims++
            } while (src.CheckTokenString(","))
            if (0 == numAnims) {
                src.Warning("No animation specified")
                MakeDefault()
                return false
            }
            anim.SetAnim(this, realname.toString(), alias.toString(), numAnims, md5anims)

            // parse any frame commands or animflags
            if (src.CheckTokenString("{")) {
                while (true) {
                    if (!src.ReadToken(token)) {
                        src.Warning("Unexpected end of file")
                        MakeDefault()
                        return false
                    }
                    if (token.toString() == "}") {
                        break
                    } else if (token.toString() == "prevent_idle_override") {
                        flags.prevent_idle_override = true
                    } else if (token.toString() == "random_cycle_start") {
                        flags.random_cycle_start = true
                    } else if (token.toString() == "ai_no_turn") {
                        flags.ai_no_turn = true
                    } else if (token.toString() == "anim_turn") {
                        flags.anim_turn = true
                    } else if (token.toString() == "frame") {
                        // create a frame command
                        var framenum: Int
                        var err: String?

                        // make sure we don't have any line breaks while reading the frame command so the error line # will be correct
                        if (!src.ReadTokenOnLine(token)) {
                            src.Warning("Missing frame # after 'frame'")
                            MakeDefault()
                            return false
                        }
                        if (token.type == Token.TT_PUNCTUATION && token.toString() == "-") {
                            src.Warning("Invalid frame # after 'frame'")
                            MakeDefault()
                            return false
                        } else if (token.type != Token.TT_NUMBER || token.subtype == Token.TT_FLOAT) {
                            src.Error("expected integer value, found '%s'", token)
                        }

                        // get the frame number
                        framenum = token.GetIntValue()

                        // put the command on the specified frame of the animation
                        err = anim.AddFrameCommand(this, framenum, src, null)
                        if (err != null) {
                            src.Warning("%s", err)
                            MakeDefault()
                            return false
                        }
                    } else {
                        src.Warning("Unknown command '%s'", token)
                        MakeDefault()
                        return false
                    }
                }
            }

            // set the flags
            anim.SetAnimFlags(flags)
            return true
        }

        companion object {
            private const val MAX_ANIMS = 64
        }
    }

    /*
     ==============================================================================================

     idAnimBlend

     ==============================================================================================
     */
    class idAnimBlend {
        private val DBG_count = DBG_counter++
        private var allowFrameCommands = false
        private var allowMove = false
        private var animNum: Short = 0

        //
        private var animWeights: FloatArray? = FloatArray(Anim.ANIM_MaxSyncedAnims)
        private var blendDuration = 0
        private var blendEndValue = 0f

        //
        private var blendStartTime = 0
        private var blendStartValue = 0f
        private var cycle: Short = 0
        private var endtime = 0
        private var frame: Short = 0
        private var modelDef: idDeclModelDef? = null
        private var rate = 0f
        private var starttime = 0
        private var timeOffset = 0

        constructor() {
            Reset(null)
        }

        constructor(blend: idAnimBlend?) {
            modelDef = blend.modelDef
            starttime = blend.starttime
            endtime = blend.endtime
            timeOffset = blend.timeOffset
            rate = blend.rate
            blendStartTime = blend.blendStartTime
            blendDuration = blend.blendDuration
            blendStartValue = blend.blendStartValue
            blendEndValue = blend.blendEndValue
            System.arraycopy(blend.animWeights, 0, animWeights, 0, Anim.ANIM_MaxSyncedAnims)
            cycle = blend.cycle
            frame = blend.frame
            animNum = blend.animNum
            allowMove = blend.allowMove
            allowFrameCommands = blend.allowFrameCommands
        }

        private fun Reset(_modelDef: idDeclModelDef?) {
            modelDef = _modelDef
            cycle = 1
            starttime = 0
            endtime = 0
            timeOffset = 0
            rate = 1.0f
            frame = 0
            allowMove = true
            allowFrameCommands = true
            animNum = 0

//	memset( animWeights, 0, sizeof( animWeights ) );
            animWeights = FloatArray(animWeights.size)
            blendStartValue = 0.0f
            blendEndValue = 0.0f
            blendStartTime = 0
            blendDuration = 0
        }

        private fun CallFrameCommands(ent: idEntity?, fromtime: Int, totime: Int) {
            val md5anim: idMD5Anim
            val frame1 = frameBlend_t()
            val frame2 = frameBlend_t()
            val fromFrameTime: Int
            var toFrameTime: Int
            if (!allowFrameCommands || null == ent || frame.toInt() != 0 || endtime > 0 && fromtime > endtime) {
                return
            }
            val anim = Anim()
            if (null == anim || !anim.HasFrameCommands()) {
                return
            }
            if (totime <= starttime) {
                // don't play until next frame or we'll play commands twice.
                // this happens on the player sometimes.
                return
            }
            fromFrameTime = AnimTime(fromtime)
            toFrameTime = AnimTime(totime)
            if (toFrameTime < fromFrameTime) {
                toFrameTime += Length()
            }
            md5anim = anim.MD5Anim(0)
            md5anim.ConvertTimeToFrame(fromFrameTime, cycle.toInt(), frame1)
            md5anim.ConvertTimeToFrame(toFrameTime, cycle.toInt(), frame2)
            if (fromFrameTime <= 0) {
                // make sure first frame is called
                anim.CallFrameCommands(ent, -1, frame2.frame1)
            } else {
                anim.CallFrameCommands(ent, frame1.frame1, frame2.frame1)
            }
        }

        private fun SetFrame(modelDef: idDeclModelDef?, _animNum: Int, _frame: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)
            if (modelDef.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum.toShort()
            starttime = currentTime
            endtime = -1
            cycle = -1
            animWeights.get(0) = 1.0f
            frame = _frame.toShort()

            // a frame of 0 means it's not a single frame blend, so we set it to frame + 1
            if (frame <= 0) {
                frame = 1
            } else if (frame > _anim.NumFrames()) {
                frame = _anim.NumFrames().toShort()
            }

            // set up blend
            blendEndValue = 1.0f
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            blendStartValue = 0.0f
        }

        private fun CycleAnim(modelDef: idDeclModelDef?, _animNum: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)
            if (modelDef.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum.toShort()
            animWeights.get(0) = 1.0f
            endtime = -1
            cycle = -1
            starttime = if (_anim.GetAnimFlags().random_cycle_start) {
                // start the animation at a random time so that characters don't walk in sync
                (currentTime - Game_local.gameLocal.random.RandomFloat() * _anim.Length()).toInt()
            } else {
                currentTime
            }

            // set up blend
            blendEndValue = 1.0f
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            blendStartValue = 0.0f
        }

        private fun PlayAnim(modelDef: idDeclModelDef?, _animNum: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)
            if (modelDef.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum.toShort()
            starttime = currentTime
            endtime = starttime + _anim.Length()
            cycle = 1
            animWeights.get(0) = 1.0f

            // set up blend
            blendEndValue = 1.0f
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            blendStartValue = 0.0f
        }

        private fun BlendAnim(
            currentTime: Int,
            channel: Int,
            numJoints: Int,
            blendFrame: Array<idJointQuat?>?,
            blendWeight: CFloat?,
            removeOriginOffset: Boolean,
            overrideBlend: Boolean,
            printInfo: Boolean
        ): Boolean {
            var i: Int
            var lerp: Float
            var mixWeight: Float
            var md5anim: idMD5Anim
            var ptr: Array<idJointQuat?>?
            val frametime = frameBlend_t()
            val jointFrame: Array<idJointQuat?>?
            val mixFrame: Array<idJointQuat?>
            val numAnims: Int
            val time: Int
            val anim = Anim() ?: return false
            val weight = GetWeight(currentTime)
            if (blendWeight.getVal() > 0.0f) {
                if (endtime >= 0 && currentTime >= endtime) {
                    return false
                }
                if (0f == weight) {
                    return false
                }
                if (overrideBlend) {
                    blendWeight.setVal(1.0f - weight)
                }
            }
            jointFrame = if (channel == Anim.ANIMCHANNEL_ALL && 0f == blendWeight.getVal()) {
                // we don't need a temporary buffer, so just store it directly in the blend frame
                blendFrame
            } else {
                // allocate a temporary buffer to copy the joints from
                arrayOfNulls<idJointQuat?>(numJoints)
            }
            time = AnimTime(currentTime)
            numAnims = anim.NumAnims()
            if (numAnims == 1) {
                md5anim = anim.MD5Anim(0)
                if (frame.toInt() != 0) {
                    md5anim.GetSingleFrame(
                        frame - 1,
                        jointFrame,
                        TempDump.itoi(modelDef.GetChannelJoints(channel)),
                        modelDef.NumJointsOnChannel(channel)
                    )
                } else {
                    md5anim.ConvertTimeToFrame(time, cycle.toInt(), frametime)
                    md5anim.GetInterpolatedFrame(
                        frametime,
                        jointFrame,
                        TempDump.itoi(modelDef.GetChannelJoints(channel)),
                        modelDef.NumJointsOnChannel(channel)
                    )
                }
            } else {
                //
                // need to mix the multipoint anim together first
                //
                // allocate a temporary buffer to copy the joints to
                mixFrame = arrayOfNulls<idJointQuat?>(numJoints)
                if (0 == frame.toInt()) {
                    anim.MD5Anim(0).ConvertTimeToFrame(time, cycle.toInt(), frametime)
                }
                ptr = jointFrame
                mixWeight = 0.0f
                i = 0
                while (i < numAnims) {
                    if (animWeights.get(i) > 0.0f) {
                        mixWeight += animWeights.get(i)
                        lerp = animWeights.get(i) / mixWeight
                        md5anim = anim.MD5Anim(i)
                        if (frame.toInt() != 0) {
                            md5anim.GetSingleFrame(
                                frame - 1,
                                ptr,
                                TempDump.itoi(modelDef.GetChannelJoints(channel)),
                                modelDef.NumJointsOnChannel(channel)
                            )
                        } else {
                            md5anim.GetInterpolatedFrame(
                                frametime,
                                ptr,
                                TempDump.itoi(modelDef.GetChannelJoints(channel)),
                                modelDef.NumJointsOnChannel(channel)
                            )
                        }

                        // only blend after the first anim is mixed in
                        if (ptr != jointFrame) {
                            Simd.SIMDProcessor.BlendJoints(
                                jointFrame,
                                ptr,
                                lerp,
                                TempDump.itoi(modelDef.GetChannelJoints(channel)),
                                modelDef.NumJointsOnChannel(channel)
                            )
                        }
                        ptr = mixFrame
                    }
                    i++
                }
                if (0f == mixWeight) {
                    return false
                }
            }
            if (removeOriginOffset) {
                if (allowMove) {
                    if (Anim_Blend.VELOCITY_MOVE) {
                        jointFrame.get(0).t.x = 0.0f
                    } else {
                        jointFrame.get(0).t.Zero()
                    }
                }
                if (anim.GetAnimFlags().anim_turn) {
                    jointFrame.get(0).q.Set(-0.70710677f, 0.0f, 0.0f, 0.70710677f)
                }
            }
            if (0f == blendWeight.getVal()) {
                blendWeight.setVal(weight)
                if (channel != Anim.ANIMCHANNEL_ALL) {
                    val index = modelDef.GetChannelJoints(channel)
                    val num = modelDef.NumJointsOnChannel(channel)
                    i = 0
                    while (i < num) {
                        val j: Int = index.get(i)
                        blendFrame.get(j).t.oSet(jointFrame.get(j).t)
                        blendFrame.get(j).q.oSet(jointFrame.get(j).q)
                        i++
                    }
                }
            } else {
                blendWeight.setVal(blendWeight.getVal() + weight)
                lerp = weight / blendWeight.getVal()
                Simd.SIMDProcessor.BlendJoints(
                    blendFrame,
                    jointFrame,
                    lerp,
                    TempDump.itoi(modelDef.GetChannelJoints(channel)),
                    modelDef.NumJointsOnChannel(channel)
                )
            }
            if (printInfo) {
                if (frame.toInt() != 0) {
                    Game_local.gameLocal.Printf(
                        "  %s: '%s', %d, %.2f%%\n",
                        Anim_Blend.channelNames[channel],
                        anim.FullName(),
                        frame,
                        weight * 100.0f
                    )
                } else {
                    Game_local.gameLocal.Printf(
                        "  %s: '%s', %.3f, %.2f%%\n",
                        Anim_Blend.channelNames[channel],
                        anim.FullName(),
                        frametime.frame1.toFloat() + frametime.backlerp,
                        weight * 100.0f
                    )
                }
            }
            return true
        }

        private fun BlendOrigin(
            currentTime: Int,
            blendPos: idVec3?,
            blendWeight: CFloat?,
            removeOriginOffset: Boolean
        ) {
            val lerp: Float
            val animpos = idVec3()
            val pos = idVec3()
            val time: Int
            val num: Int
            var i: Int
            if (frame.toInt() != 0 || endtime > 0 && currentTime > endtime) {
                return
            }
            val anim = Anim() ?: return
            if (allowMove && removeOriginOffset) {
                return
            }
            val weight = GetWeight(currentTime)
            if (0f == weight) {
                return
            }
            time = AnimTime(currentTime)
            pos.Zero()
            num = anim.NumAnims()
            i = 0
            while (i < num) {
                anim.GetOrigin(animpos, i, time, cycle.toInt())
                pos.oPluSet(animpos.oMultiply(animWeights.get(i)))
                i++
            }
            if (0f == blendWeight.getVal()) {
                blendPos.oSet(pos)
                blendWeight.setVal(weight)
            } else {
                lerp = weight / (blendWeight.getVal() + weight)
                blendPos.oPluSet(pos.oMinus(blendPos).oMultiply(lerp))
                blendWeight.setVal(blendWeight.getVal() + weight)
            }
        }

        private fun BlendDelta(fromtime: Int, totime: Int, blendDelta: idVec3?, blendWeight: CFloat?) {
            val pos1 = idVec3()
            val pos2 = idVec3()
            val animpos = idVec3()
            val delta = idVec3()
            val time1: Int
            var time2: Int
            val lerp: Float
            val num: Int
            var i: Int
            if (frame.toInt() != 0 || !allowMove || endtime > 0 && fromtime > endtime) {
                return
            }
            val anim = Anim() ?: return
            val weight = GetWeight(totime)
            if (0f == weight) {
                return
            }
            time1 = AnimTime(fromtime)
            time2 = AnimTime(totime)
            if (time2 < time1) {
                time2 += Length()
            }
            num = anim.NumAnims()
            pos1.Zero()
            pos2.Zero()
            i = 0
            while (i < num) {
                anim.GetOrigin(animpos, i, time1, cycle.toInt())
                pos1.oPluSet(animpos.oMultiply(animWeights.get(i)))
                anim.GetOrigin(animpos, i, time2, cycle.toInt())
                pos2.oPluSet(animpos.oMultiply(animWeights.get(i)))
                i++
            }
            delta.oSet(pos2.oMinus(pos1))
            if (0f == blendWeight.getVal()) {
                blendDelta.oSet(delta)
                blendWeight.setVal(weight)
            } else {
                lerp = weight / (blendWeight.getVal() + weight)
                blendDelta.oPluSet(delta.oMinus(blendDelta).oMultiply(lerp))
                blendWeight.setVal(blendWeight.getVal() + weight)
            }
        }

        private fun BlendDeltaRotation(fromtime: Int, totime: Int, blendDelta: idQuat?, blendWeight: CFloat?) {
            val q1 = idQuat()
            val q2 = idQuat()
            val q3 = idQuat()
            val time1: Int
            var time2: Int
            var lerp: Float
            var mixWeight: Float
            val num: Int
            var i: Int
            if (frame.toInt() != 0 || !allowMove || endtime > 0 && fromtime > endtime) {
                return
            }
            val anim = Anim()
            if (null == anim || !anim.GetAnimFlags().anim_turn) {
                return
            }
            val weight = GetWeight(totime)
            if (0f == weight) {
                return
            }
            time1 = AnimTime(fromtime)
            time2 = AnimTime(totime)
            if (time2 < time1) {
                time2 += Length()
            }
            q1.Set(0.0f, 0.0f, 0.0f, 1.0f)
            q2.Set(0.0f, 0.0f, 0.0f, 1.0f)
            mixWeight = 0.0f
            num = anim.NumAnims()
            i = 0
            while (i < num) {
                if (animWeights.get(i) > 0.0f) {
                    mixWeight += animWeights.get(i)
                    if (animWeights.get(i) == mixWeight) {
                        anim.GetOriginRotation(q1, i, time1, cycle.toInt())
                        anim.GetOriginRotation(q2, i, time2, cycle.toInt())
                    } else {
                        lerp = animWeights.get(i) / mixWeight
                        anim.GetOriginRotation(q3, i, time1, cycle.toInt())
                        q1.Slerp(q1, q3, lerp)
                        anim.GetOriginRotation(q3, i, time2, cycle.toInt())
                        q2.Slerp(q1, q3, lerp)
                    }
                }
                i++
            }
            q3.oSet(q1.Inverse().oMultiply(q2))
            if (0f == blendWeight.getVal()) {
                blendDelta.oSet(q3)
                blendWeight.setVal(weight)
            } else {
                lerp = weight / (blendWeight.getVal() + weight)
                blendDelta.Slerp(blendDelta, q3, lerp)
                blendWeight.setVal(blendWeight.getVal() + weight)
            }
        }

        private fun AddBounds(currentTime: Int, bounds: idBounds?, removeOriginOffset: Boolean): Boolean {
            var i: Int
            val num: Int
            val b = idBounds()
            val time: Int
            val pos = idVec3()
            val addorigin: Boolean
            if (endtime > 0 && currentTime > endtime) {
                return false
            }
            val anim = Anim() ?: return false
            val weight = GetWeight(currentTime)
            if (0f == weight) {
                return false
            }
            time = AnimTime(currentTime)
            num = anim.NumAnims()
            addorigin = !allowMove || !removeOriginOffset
            i = 0
            while (i < num) {
                if (anim.GetBounds(b, i, time, cycle.toInt())) {
                    if (addorigin) {
                        anim.GetOrigin(pos, i, time, cycle.toInt())
                        b.TranslateSelf(pos)
                    }
                    bounds.AddBounds(b)
                }
                i++
            }
            return true
        }

        fun Save(savefile: idSaveGame?) {
            var i: Int
            savefile.WriteInt(starttime)
            savefile.WriteInt(endtime)
            savefile.WriteInt(timeOffset)
            savefile.WriteFloat(rate)
            savefile.WriteInt(blendStartTime)
            savefile.WriteInt(blendDuration)
            savefile.WriteFloat(blendStartValue)
            savefile.WriteFloat(blendEndValue)
            i = 0
            while (i < Anim.ANIM_MaxSyncedAnims) {
                savefile.WriteFloat(animWeights.get(i))
                i++
            }
            savefile.WriteShort(cycle)
            savefile.WriteShort(frame)
            savefile.WriteShort(animNum)
            savefile.WriteBool(allowMove)
            savefile.WriteBool(allowFrameCommands)
        }

        /*
         =====================
         idAnimBlend::Restore

         unarchives object from save game file
         =====================
         */
        fun Restore(savefile: idRestoreGame?, modelDef: idDeclModelDef?) {
            var i: Int
            this.modelDef = modelDef
            starttime = savefile.ReadInt()
            endtime = savefile.ReadInt()
            timeOffset = savefile.ReadInt()
            rate = savefile.ReadFloat()
            blendStartTime = savefile.ReadInt()
            blendDuration = savefile.ReadInt()
            blendStartValue = savefile.ReadFloat()
            blendEndValue = savefile.ReadFloat()
            i = 0
            while (i < Anim.ANIM_MaxSyncedAnims) {
                animWeights.get(i) = savefile.ReadFloat()
                i++
            }
            cycle = savefile.ReadShort()
            frame = savefile.ReadShort()
            animNum = savefile.ReadShort()
            if (null == modelDef) {
                animNum = 0
            } else if (animNum < 0 || animNum > modelDef.NumAnims()) {
                Game_local.gameLocal.Warning(
                    "Anim number %d out of range for model '%s' during save game",
                    animNum,
                    modelDef.GetModelName()
                )
                animNum = 0
            }
            allowMove = savefile.ReadBool()
            allowFrameCommands = savefile.ReadBool()
        }

        fun AnimName(): String? {
            val anim = Anim() ?: return ""
            return anim.Name()
        }

        fun AnimFullName(): String? {
            val anim = Anim() ?: return ""
            return anim.FullName()
        }

        fun GetWeight(currentTime: Int): Float {
            val timeDelta: Int
            val frac: Float
            val w: Float
            timeDelta = currentTime - blendStartTime
            if (timeDelta <= 0) {
                w = blendStartValue
            } else if (timeDelta >= blendDuration) {
                w = blendEndValue
            } else {
                frac = timeDelta.toFloat() / blendDuration.toFloat()
                w = blendStartValue + (blendEndValue - blendStartValue) * frac
            }
            return w
        }

        fun GetFinalWeight(): Float {
            return blendEndValue
        }

        fun SetWeight(newWeight: Float, currentTime: Int, blendTime: Int) {
            blendStartValue = GetWeight(currentTime)
            blendEndValue = newWeight
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            if (0f == newWeight) {
                endtime = currentTime + blendTime
            }
        }

        fun NumSyncedAnims(): Int {
            val anim = Anim() ?: return 0
            return anim.NumAnims()
        }

        fun SetSyncedAnimWeight(num: Int, weight: Float): Boolean {
            val anim = Anim() ?: return false
            if (num < 0 || num > anim.NumAnims()) {
                return false
            }
            animWeights.get(num) = weight
            return true
        }

        fun Clear(currentTime: Int, clearTime: Int) {
            if (0 == clearTime) {
                Reset(modelDef)
            } else {
                SetWeight(0.0f, currentTime, clearTime)
            }
        }

        fun IsDone(currentTime: Int): Boolean {
            if (0 == frame.toInt() && endtime > 0 && currentTime >= endtime) {
                return true
            }
            return blendEndValue <= 0.0f && currentTime >= blendStartTime + blendDuration
        }

        fun FrameHasChanged(currentTime: Int): Boolean {
            // if we don't have an anim, no change
            if (0 == animNum.toInt()) {
                return false
            }

            // if anim is done playing, no change
            if (endtime > 0 && currentTime > endtime) {
                return false
            }

            // if our blend weight changes, we need to update
            if (currentTime < blendStartTime + blendDuration && blendStartValue != blendEndValue) {
                return true
            }

            // if we're a single frame anim and this isn't the frame we started on, we don't need to update
            return !((frame.toInt() != 0 || NumFrames() == 1) && currentTime != starttime)
        }

        fun GetCycleCount(): Int {
            return cycle
        }

        fun SetCycleCount(count: Int) {
            val anim = Anim()
            if (null == anim) {
                cycle = -1
                endtime = 0
            } else {
                cycle = count.toShort()
                if (cycle < 0) {
                    cycle = -1
                    endtime = -1
                } else if (cycle.toInt() == 0) {
                    cycle = 1

                    // most of the time we're running at the original frame rate, so avoid the int-to-float-to-int conversion
                    endtime = if (rate == 1.0f) {
                        starttime - timeOffset + Length()
                    } else if (rate != 0.0f) {
                        (starttime - timeOffset + Length() / rate).toInt()
                    } else {
                        -1
                    }
                } else {
                    // most of the time we're running at the original frame rate, so avoid the int-to-float-to-int conversion
                    endtime = if (rate == 1.0f) {
                        starttime - timeOffset + Length() * cycle
                    } else if (rate != 0.0f) {
                        (starttime - timeOffset + Length() * cycle / rate).toInt()
                    } else {
                        -1
                    }
                }
            }
        }

        fun SetPlaybackRate(currentTime: Int, newRate: Float) {
            val animTime: Int
            if (rate == newRate) {
                return
            }
            animTime = AnimTime(currentTime)
            timeOffset = if (newRate == 1.0f) {
                animTime - (currentTime - starttime)
            } else {
                (animTime - (currentTime - starttime) * newRate).toInt()
            }
            rate = newRate

            // update the anim endtime
            SetCycleCount(cycle.toInt())
        }

        fun GetPlaybackRate(): Float {
            return rate
        }

        fun SetStartTime(_startTime: Int) {
            starttime = _startTime

            // update the anim endtime
            SetCycleCount(cycle.toInt())
        }

        fun GetStartTime(): Int {
            return if (0 == animNum.toInt()) {
                0
            } else starttime
        }

        fun GetEndTime(): Int {
            return if (0 == animNum.toInt()) {
                0
            } else endtime
        }

        fun GetFrameNumber(currentTime: Int): Int {
            val md5anim: idMD5Anim
            val frameinfo = frameBlend_t()
            val animTime: Int
            val anim = Anim() ?: return 1
            if (frame.toInt() != 0) {
                return frame
            }
            md5anim = anim.MD5Anim(0)
            animTime = AnimTime(currentTime)
            md5anim.ConvertTimeToFrame(animTime, cycle.toInt(), frameinfo)
            return frameinfo.frame1 + 1
        }

        fun AnimTime(currentTime: Int): Int {
            var time: Int
            val length: Int
            val anim = Anim()
            return if (anim != null) {
                if (frame.toInt() != 0) {
                    return Anim.FRAME2MS(frame - 1)
                }

                // most of the time we're running at the original frame rate, so avoid the int-to-float-to-int conversion
                time = if (rate == 1.0f) {
                    currentTime - starttime + timeOffset
                } else {
                    ((currentTime - starttime) * rate + timeOffset).toInt()
                }

                // given enough time, we can easily wrap time around in our frame calculations, so
                // keep cycling animations' time within the length of the
                length = Length()
                if (cycle < 0 && length > 0) {
                    time %= length

                    // time will wrap after 24 days (oh no!), resulting in negative results for the %.
                    // adding the length gives us the proper result.
                    if (time < 0) {
                        time += length
                    }
                }
                time
            } else {
                0
            }
        }

        fun NumFrames(): Int {
            val anim = Anim() ?: return 0
            return anim.NumFrames()
        }

        fun Length(): Int {
            val anim = Anim() ?: return 0
            return anim.Length()
        }

        fun PlayLength(): Int {
            if (0 == animNum.toInt()) {
                return 0
            }
            return if (endtime < 0) {
                -1
            } else endtime - starttime + timeOffset
        }

        fun AllowMovement(allow: Boolean) {
            allowMove = allow
        }

        fun AllowFrameCommands(allow: Boolean) {
            allowFrameCommands = allow
        }

        fun Anim(): idAnim? {
            return if (null == modelDef) {
                null
            } else modelDef.GetAnim(animNum.toInt())
        }

        fun AnimNum(): Int {
            return animNum
        }

        companion object {
            // friend class				idAnimator;
            //
            //
            private var DBG_counter = 0
        }
    }

    /* **********************************************************************

     Util functions

     ***********************************************************************/
    /*
     =====================
     ANIM_GetModelDefFromEntityDef
     =====================
     */
    /*
     ==============================================================================================

     idAnimator

     ==============================================================================================
     */
    class idAnimator {
        private val AFPoseBounds: idBounds?
        private val AFPoseJointFrame: ArrayList<idJointQuat?>?
        private val AFPoseJointMods: ArrayList<idAFPoseJointMod?>?
        private val AFPoseJoints: ArrayList<Int?>?

        //
        private val channels: Array<Array<idAnimBlend?>?>? =
            Array(Anim.ANIM_NumAnimChannels) { arrayOfNulls<idAnimBlend?>(Anim.ANIM_MaxAnimsPerChannel) }
        private val jointMods: ArrayList<jointMod_t?>?

        //
        private var AFPoseBlendWeight = 0f
        private var AFPoseTime = 0
        private var entity: idEntity?
        private var forceUpdate: Boolean

        //
        private val frameBounds: idBounds?
        private var joints: Array<idJointMat?>?

        //
        private var lastTransformTime // mutable because the value is updated in CreateFrame
                : Int
        private var modelDef: idDeclModelDef?
        private val numJoints: CInt?
        private var removeOriginOffset: Boolean

        //
        //
        private var stoppedAnimatingUpdate: Boolean
        fun  /*size_t*/Allocated(): Int {
            val   /*size_t*/size: Int
            size =
                jointMods.size + numJoints.getVal() + AFPoseJointMods.size + AFPoseJointFrame.size + AFPoseJoints.size
            return size
        }

        /*
         =====================
         idAnimator::Save

         archives object for save game file
         =====================
         */
        fun Save(savefile: idSaveGame?) {                // archives object for save game file
            var i: Int
            var j: Int
            savefile.WriteModelDef(modelDef)
            savefile.WriteObject(entity)
            savefile.WriteInt(jointMods.size)
            i = 0
            while (i < jointMods.size) {
                savefile.WriteInt(jointMods.get(i).jointnum)
                savefile.WriteMat3(jointMods.get(i).mat)
                savefile.WriteVec3(jointMods.get(i).pos)
                savefile.WriteInt(TempDump.etoi(jointMods.get(i).transform_pos))
                savefile.WriteInt(TempDump.etoi(jointMods.get(i).transform_axis))
                i++
            }
            savefile.WriteInt(numJoints.getVal())
            i = 0
            while (i < numJoints.getVal()) {
                val data = joints.get(i).ToFloatPtr()
                j = 0
                while (j < 12) {
                    savefile.WriteFloat(data[j])
                    j++
                }
                i++
            }
            savefile.WriteInt(lastTransformTime)
            savefile.WriteBool(stoppedAnimatingUpdate)
            savefile.WriteBool(forceUpdate)
            savefile.WriteBounds(frameBounds)
            savefile.WriteFloat(AFPoseBlendWeight)
            savefile.WriteInt(AFPoseJoints.size)
            i = 0
            while (i < AFPoseJoints.size) {
                savefile.WriteInt(AFPoseJoints.get(i))
                i++
            }
            savefile.WriteInt(AFPoseJointMods.size)
            i = 0
            while (i < AFPoseJointMods.size) {
                savefile.WriteInt(TempDump.etoi(AFPoseJointMods.get(i).mod))
                savefile.WriteMat3(AFPoseJointMods.get(i).axis)
                savefile.WriteVec3(AFPoseJointMods.get(i).origin)
                i++
            }
            savefile.WriteInt(AFPoseJointFrame.size)
            i = 0
            while (i < AFPoseJointFrame.size) {
                savefile.WriteFloat(AFPoseJointFrame.get(i).q.x)
                savefile.WriteFloat(AFPoseJointFrame.get(i).q.y)
                savefile.WriteFloat(AFPoseJointFrame.get(i).q.z)
                savefile.WriteFloat(AFPoseJointFrame.get(i).q.w)
                savefile.WriteVec3(AFPoseJointFrame.get(i).t)
                i++
            }
            savefile.WriteBounds(AFPoseBounds)
            savefile.WriteInt(AFPoseTime)
            savefile.WriteBool(removeOriginOffset)
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels.get(i).get(j).Save(savefile)
                    j++
                }
                i++
            }
        }

        /*
         =====================
         idAnimator::Restore

         unarchives object from save game file
         =====================
         */
        fun Restore(savefile: idRestoreGame?) {                    // unarchives object from save game file
            var i: Int
            var j: Int
            val num = CInt()
            savefile.ReadModelDef(modelDef)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/entity)
            savefile.ReadInt(num)
            //jointMods.SetNum(num.getVal());
            i = 0
            while (i < num.getVal()) {
                if (i >= jointMods.size) {
                    jointMods.add(jointMod_t())
                } else {
                    jointMods.set(i, jointMod_t())
                }
                jointMods.get(i).jointnum = savefile.ReadInt()
                savefile.ReadMat3(jointMods.get(i).mat)
                savefile.ReadVec3(jointMods.get(i).pos)
                jointMods.get(i).transform_pos = jointModTransform_t.values()[savefile.ReadInt()]
                jointMods.get(i).transform_axis = jointModTransform_t.values()[savefile.ReadInt()]
                i++
            }
            numJoints.setVal(savefile.ReadInt())
            joints = arrayOfNulls<idJointMat?>(numJoints.getVal())
            i = 0
            while (i < numJoints.getVal()) {
                val data = joints.get(i).ToFloatPtr()
                j = 0
                while (j < 12) {
                    data[j] = savefile.ReadFloat()
                    j++
                }
                i++
            }
            lastTransformTime = savefile.ReadInt()
            stoppedAnimatingUpdate = savefile.ReadBool()
            forceUpdate = savefile.ReadBool()
            savefile.ReadBounds(frameBounds)
            AFPoseBlendWeight = savefile.ReadFloat()
            savefile.ReadInt(num)
            //AFPoseJoints.SetGranularity(1);
            //AFPoseJoints.SetNum(num.getVal());
            i = 0
            while (i < num.getVal()) {
                if (i >= AFPoseJoints.size) {
                    AFPoseJoints.add(savefile.ReadInt())
                } else {
                    AFPoseJoints.set(i, savefile.ReadInt())
                }
                i++
            }
            savefile.ReadInt(num)
            //AFPoseJointMods.SetGranularity(1);
            //AFPoseJointMods.SetNum(num.getVal());
            i = 0
            while (i < num.getVal()) {
                AFPoseJointMods.get(i).mod = AFJointModType_t.values()[savefile.ReadInt()]
                savefile.ReadMat3(AFPoseJointMods.get(i).axis)
                savefile.ReadVec3(AFPoseJointMods.get(i).origin)
                i++
            }
            savefile.ReadInt(num)
            //AFPoseJointFrame.SetGranularity(1);
            //AFPoseJointFrame.SetNum(num.getVal());
            i = 0
            while (i < num.getVal()) {
                AFPoseJointFrame.get(i).q.x = savefile.ReadFloat()
                AFPoseJointFrame.get(i).q.y = savefile.ReadFloat()
                AFPoseJointFrame.get(i).q.z = savefile.ReadFloat()
                AFPoseJointFrame.get(i).q.w = savefile.ReadFloat()
                savefile.ReadVec3(AFPoseJointFrame.get(i).t)
                i++
            }
            savefile.ReadBounds(AFPoseBounds)
            AFPoseTime = savefile.ReadInt()
            removeOriginOffset = savefile.ReadBool()
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels.get(i).get(j).Restore(savefile, modelDef)
                    j++
                }
                i++
            }
        }

        fun SetEntity(ent: idEntity?) {
            entity = ent
        }

        fun GetEntity(): idEntity? {
            return entity
        }

        fun RemoveOriginOffset(remove: Boolean) {
            removeOriginOffset = remove
        }

        fun RemoveOrigin(): Boolean {
            return removeOriginOffset
        }

        fun GetJointList(jointnames: String?, jointList: ArrayList<Int?>?) {
            if (modelDef != null) {
                modelDef.GetJointList(jointnames, jointList)
            }
        }

        fun GetJointList(jointnames: idStr?, jointList: ArrayList<Int?>?) {
            GetJointList(jointnames.toString(), jointList)
        }

        fun NumAnims(): Int {
            return if (null == modelDef) {
                0
            } else modelDef.NumAnims()
        }

        fun GetAnim(index: Int): idAnim? {
            return if (null == modelDef) {
                null
            } else modelDef.GetAnim(index)
        }

        fun GetAnim(name: String?): Int {
            return if (null == modelDef) {
                0
            } else modelDef.GetAnim(name)
        }

        fun HasAnim(name: String?): Boolean {
            return if (null == modelDef) {
                false
            } else modelDef.HasAnim(name)
        }

        fun HasAnim(name: idStr?): Boolean {
            return HasAnim(name.toString())
        }

        fun ServiceAnims(fromtime: Int, totime: Int) {
            var i: Int
            var j: Int
            val blend: Array<Array<idAnimBlend?>?>?
            if (null == modelDef) {
                return
            }
            if (modelDef.ModelHandle() != null) {
                blend = channels
                i = 0
                while (i < Anim.ANIM_NumAnimChannels) {
                    j = 0
                    while (j < Anim.ANIM_MaxAnimsPerChannel) {
                        blend.get(i).get(j).CallFrameCommands(entity, fromtime, totime)
                        j++
                    }
                    i++
                }
            }
            if (!IsAnimating(totime)) {
                stoppedAnimatingUpdate = true
                if (entity != null) {
                    entity.BecomeInactive(Entity.TH_ANIMATE)

                    // present one more time with stopped animations so the renderer can properly recreate interactions
                    entity.BecomeActive(Entity.TH_UPDATEVISUALS)
                }
            }
        }

        fun IsAnimating(currentTime: Int): Boolean {
            var i: Int
            var j: Int
            val blend: Array<Array<idAnimBlend?>?>?
            if (null == modelDef || TempDump.NOT(modelDef.ModelHandle())) {
                return false
            }

            // if animating with an articulated figure
            if (AFPoseJoints.size != 0 && currentTime <= AFPoseTime) {
                return true
            }
            blend = channels
            i = 0
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (!blend.get(i).get(j).IsDone(currentTime)) {
                        return true
                    }
                    j++
                }
                i++
            }
            return false
        }

        fun GetJoints(renderEntity: renderEntity_s?): Int {
            renderEntity.joints = joints
            return numJoints.getVal()
        }

        fun NumJoints(): Int {
            return numJoints.getVal()
        }

        fun  /*jointHandle_t*/GetFirstChild(   /*jointHandle_t*/jointnum: Int): Int {
            var i: Int
            val num: Int
            var joint: jointInfo_t?
            if (null == modelDef) {
                return Model.INVALID_JOINT
            }
            num = modelDef.NumJoints()
            if (0 == num) {
                return jointnum
            }
            joint = modelDef.GetJoint(0)
            i = 0
            while (i < num) {
                if (joint.parentNum == jointnum) {
                    return joint.num
                }
                joint = modelDef.GetJoint(++i)
            }
            return jointnum
        }

        fun  /*jointHandle_t*/GetFirstChild(name: String?): Int {
            return GetFirstChild(GetJointHandle(name))
        }

        fun SetModel(modelname: String?): idRenderModel? {
            var i: Int
            var j: Int
            //int[] numJoints = {0};
            FreeData()

            // check if we're just clearing the model
            if (!TempDump.isNotNullOrEmpty(modelname)) {
                return null
            }
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelname, false)
            if (null == modelDef) {
                return null
            }
            val renderModel = modelDef.ModelHandle()
            if (null == renderModel) {
                modelDef = null
                return null
            }

            // make sure model hasn't been purged
            modelDef.Touch()
            joints = modelDef.SetupJoints(numJoints, joints, frameBounds, removeOriginOffset)
            modelDef.ModelHandle().Reset()

            // set the modelDef on all channels
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels.get(i).get(j).Reset(modelDef)
                    j++
                }
                i++
            }
            return modelDef.ModelHandle()
        }

        fun ModelHandle(): idRenderModel? {
            return if (null == modelDef) {
                null
            } else modelDef.ModelHandle()
        }

        fun ModelDef(): idDeclModelDef? {
            return modelDef
        }

        fun ForceUpdate() {
            lastTransformTime = -1
            forceUpdate = true
        }

        fun ClearForceUpdate() {
            forceUpdate = false
        }

        fun CreateFrame(currentTime: Int, force: Boolean): Boolean {
            var i: Int
            var j: Int
            val numJoints: Int
            var parentNum: Int
            var hasAnim: Boolean
            val debugInfo: Boolean
            val baseBlend = CFloat()
            val blendWeight = CFloat()
            var blend: Array<idAnimBlend?>?
            val jointParent: Array<Int?>?
            var jointMod: jointMod_t?
            val defaultPose: ArrayList<idJointQuat?>?
            if (Game_local.gameLocal.inCinematic && Game_local.gameLocal.skipCinematic) {
                return false
            }
            if (null == modelDef || null == modelDef.ModelHandle()) {
                return false
            }
            if (!force && 0 == r_showSkel.GetInteger()) {
                if (lastTransformTime == currentTime) {
                    return false
                }
                if (lastTransformTime != -1 && !stoppedAnimatingUpdate && !IsAnimating(currentTime)) {
                    return false
                }
            }
            lastTransformTime = currentTime
            stoppedAnimatingUpdate = false
            //numJoints = modelDef.Joints().size();
            if (entity != null && (SysCvar.g_debugAnim.GetInteger() == entity.entityNumber || SysCvar.g_debugAnim.GetInteger() == -2)) {
                debugInfo = true
                Game_local.gameLocal.Printf(
                    "---------------\n%d: entity '%s':\n",
                    Game_local.gameLocal.time,
                    entity.GetName()
                )
                Game_local.gameLocal.Printf("model '%s':\n", modelDef.GetModelName())
            } else {
                debugInfo = false
            }

            // init the joint buffer
            if (AFPoseJoints.size != 0) {
                // initialize with AF pose anim for the case where there are no other animations and no AF pose joint modifications
                defaultPose = AFPoseJointFrame
            } else {
                defaultPose = ArrayList<Any?>(Arrays.asList(*modelDef.GetDefaultPose()))
            }
            if (null == defaultPose) {
                //gameLocal.Warning( "idAnimator::CreateFrame: no defaultPose on '%s'", modelDef.Name() );
                return false
            }
            numJoints = modelDef.Joints().size
            val jointFrame = arrayOfNulls<idJointQuat?>(numJoints)
            //SIMDProcessor.Memcpy(jointFrame, defaultPose, numJoints /* sizeof( jointFrame[0] )*/);
            for (index in 0 until numJoints) {
                jointFrame[index] = defaultPose[index]
            }
            hasAnim = false

            // blend the all channel
            baseBlend.setVal(0.0f)
            blend = channels.get(Anim.ANIMCHANNEL_ALL)
            j = Anim.ANIMCHANNEL_ALL
            while (j < Anim.ANIM_MaxAnimsPerChannel) {
                if (blend.get(j).BlendAnim(
                        currentTime,
                        Anim.ANIMCHANNEL_ALL,
                        numJoints,
                        jointFrame,
                        baseBlend,
                        removeOriginOffset,
                        false,
                        debugInfo
                    )
                ) {
                    hasAnim = true
                    if (baseBlend.getVal() >= 1.0f) {
                        break
                    }
                }
                j++
            }

            // only blend other channels if there's enough space to blend into
            if (baseBlend.getVal() < 1.0f) {
                i = Anim.ANIMCHANNEL_ALL + 1
                while (i < Anim.ANIM_NumAnimChannels) {
                    if (0 == modelDef.NumJointsOnChannel(i)) {
                        i++
                        continue
                    }
                    if (i == Anim.ANIMCHANNEL_EYELIDS) {
                        // eyelids blend over any previous anims, so skip it and blend it later
                        i++
                        continue
                    }
                    blendWeight.setVal(baseBlend.getVal())
                    blend = channels.get(i)
                    j = 0
                    while (j < Anim.ANIM_MaxAnimsPerChannel) {
                        if (blend.get(j).BlendAnim(
                                currentTime,
                                i,
                                numJoints,
                                jointFrame,
                                blendWeight,
                                removeOriginOffset,
                                false,
                                debugInfo
                            )
                        ) {
                            hasAnim = true
                            if (blendWeight.getVal() >= 1.0f) {
                                // fully blended
                                break
                            }
                        }
                        j++
                    }
                    if (debugInfo && 0 == AFPoseJoints.size && 0f == blendWeight.getVal()) {
                        Game_local.gameLocal.Printf(
                            "%d: %s using default pose in model '%s'\n",
                            Game_local.gameLocal.time,
                            Anim_Blend.channelNames[i],
                            modelDef.GetModelName()
                        )
                    }
                    i++
                }
            }

            // blend in the eyelids
            if (modelDef.NumJointsOnChannel(Anim.ANIMCHANNEL_EYELIDS) != 0) {
                blend = channels.get(Anim.ANIMCHANNEL_EYELIDS)
                blendWeight.setVal(baseBlend.getVal())
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (blend.get(j).BlendAnim(
                            currentTime,
                            Anim.ANIMCHANNEL_EYELIDS,
                            numJoints,
                            jointFrame,
                            blendWeight,
                            removeOriginOffset,
                            true,
                            debugInfo
                        )
                    ) {
                        hasAnim = true
                        if (blendWeight.getVal() >= 1.0f) {
                            // fully blended
                            break
                        }
                    }
                    j++
                }
            }

            // blend the articulated figure pose
            if (BlendAFPose(jointFrame)) {
                hasAnim = true
            }
            if (!hasAnim && 0 == jointMods.size) {
                // no animations were updated
                return false
            }

            // convert the joint quaternions to rotation matrices
            Simd.SIMDProcessor.ConvertJointQuatsToJointMats(joints, jointFrame, numJoints)

            // check if we need to modify the origin
            if (jointMods.size != 0 && jointMods.get(0).jointnum == 0) {
                jointMod = jointMods.get(0)
                when (jointMod.transform_axis) {
                    jointModTransform_t.JOINTMOD_NONE -> {}
                    jointModTransform_t.JOINTMOD_LOCAL -> joints.get(0)
                        .SetRotation(jointMod.mat.oMultiply(joints.get(0).ToMat3()))
                    jointModTransform_t.JOINTMOD_WORLD -> joints.get(0)
                        .SetRotation(joints.get(0).ToMat3().oMultiply(jointMod.mat))
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints.get(
                        0
                    ).SetRotation(jointMod.mat)
                }
                when (jointMod.transform_pos) {
                    jointModTransform_t.JOINTMOD_NONE -> {}
                    jointModTransform_t.JOINTMOD_LOCAL -> joints.get(0)
                        .SetTranslation(joints.get(0).ToVec3().oPlus(jointMod.pos))
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE, jointModTransform_t.JOINTMOD_WORLD, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints.get(
                        0
                    ).SetTranslation(jointMod.pos)
                }
                j = 1
            } else {
                j = 0
            }

            // add in the model offset
            joints.get(0).SetTranslation(joints.get(0).ToVec3().oPlus(modelDef.GetVisualOffset()))

            // pointer to joint info
            jointParent = modelDef.JointParents()

            // add in any joint modifications
            i = 1
            while (j < jointMods.size) {
                jointMod = jointMods.get(j)

                // transform any joints preceding the joint modifier
                Simd.SIMDProcessor.TransformJoints(joints, TempDump.itoi(jointParent), i, jointMod.jointnum - 1)
                i = jointMod.jointnum
                parentNum = jointParent.get(i)
                when (jointMod.transform_axis) {
                    jointModTransform_t.JOINTMOD_NONE -> joints.get(i)
                        .SetRotation(joints.get(i).ToMat3().oMultiply(joints.get(parentNum).ToMat3()))
                    jointModTransform_t.JOINTMOD_LOCAL -> joints.get(i).SetRotation(
                        jointMod.mat.oMultiply(
                            joints.get(i).ToMat3().oMultiply(joints.get(parentNum).ToMat3())
                        )
                    )
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE -> joints.get(i)
                        .SetRotation(jointMod.mat.oMultiply(joints.get(parentNum).ToMat3()))
                    jointModTransform_t.JOINTMOD_WORLD -> joints.get(i).SetRotation(
                        joints.get(i).ToMat3().oMultiply(joints.get(parentNum).ToMat3()).oMultiply(jointMod.mat)
                    )
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints.get(i).SetRotation(jointMod.mat)
                }
                when (jointMod.transform_pos) {
                    jointModTransform_t.JOINTMOD_NONE -> joints.get(i).SetTranslation(
                        joints.get(parentNum).ToVec3()
                            .oPlus(joints.get(i).ToVec3().oMultiply(joints.get(parentNum).ToMat3()))
                    )
                    jointModTransform_t.JOINTMOD_LOCAL -> joints.get(i).SetTranslation(
                        joints.get(parentNum).ToVec3().oPlus(joints.get(i).ToVec3().oPlus(jointMod.pos))
                            .oMultiply(joints.get(parentNum).ToMat3())
                    )
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE -> joints.get(i).SetTranslation(
                        joints.get(parentNum).ToVec3().oPlus(jointMod.pos.oMultiply(joints.get(parentNum).ToMat3()))
                    )
                    jointModTransform_t.JOINTMOD_WORLD ->                         //joints[i].SetTranslation(joints[parentNum].ToVec3().oPlus(joints[i].ToVec3().oMultiply(joints[parentNum].ToMat3())).oPlus(jointMod.pos));
                        joints.get(i).SetTranslation(
                            joints.get(parentNum).ToVec3().oPlus(joints.get(i).ToVec3())
                                .oMultiply(joints.get(parentNum).ToMat3()).oPlus(jointMod.pos)
                        )
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints.get(i).SetTranslation(jointMod.pos)
                }
                j++
                i++
            }

            // transform the rest of the hierarchy
            Simd.SIMDProcessor.TransformJoints(joints, TempDump.itoi(jointParent), i, numJoints - 1)
            return true
        }

        fun FrameHasChanged(currentTime: Int): Boolean {
            var i: Int
            var j: Int
            val blend: Array<Array<idAnimBlend?>?>?
            if (null == modelDef || null == modelDef.ModelHandle()) {
                return false
            }

            // if animating with an articulated figure
            if (AFPoseJoints.size != 0 && currentTime <= AFPoseTime) {
                return true
            }
            blend = channels
            i = 0
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (blend.get(i).get(j).FrameHasChanged(currentTime)) {
                        return true
                    }
                    j++
                }
                i++
            }
            return forceUpdate && IsAnimating(currentTime)
        }

        fun GetDelta(fromtime: Int, totime: Int, delta: idVec3?) {
            var i: Int
            var blend: Array<idAnimBlend?>?
            val blendWeight = CFloat()
            if (null == modelDef || null == modelDef.ModelHandle() || fromtime == totime) {
                delta.Zero()
                return
            }
            delta.Zero()
            blendWeight.setVal(0.0f)
            blend = channels.get(Anim.ANIMCHANNEL_ALL)
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend.get(i).BlendDelta(fromtime, totime, delta, blendWeight)
                i++
            }
            if (modelDef.Joints().get(0).channel != 0) {
                val c = modelDef.Joints().get(0).channel
                blend = channels.get(c)
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend.get(i).BlendDelta(fromtime, totime, delta, blendWeight)
                    i++
                }
            }
        }

        fun GetDeltaRotation(fromtime: Int, totime: Int, delta: idMat3?): Boolean {
            var i: Int
            var blend: Array<idAnimBlend?>?
            val blendWeight = CFloat()
            val q = idQuat(0.0f, 0.0f, 0.0f, 1.0f)
            if (null == modelDef || null == modelDef.ModelHandle() || fromtime == totime) {
                delta.Identity()
                return false
            }
            blendWeight.setVal(0.0f)
            blend = channels.get(Anim.ANIMCHANNEL_ALL)
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend.get(i).BlendDeltaRotation(fromtime, totime, q, blendWeight)
                i++
            }
            if (modelDef.Joints().get(0).channel != 0) {
                val c = modelDef.Joints().get(0).channel
                blend = channels.get(c)
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend.get(i).BlendDeltaRotation(fromtime, totime, q, blendWeight)
                    i++
                }
            }
            return if (blendWeight.getVal() > 0.0f) {
                delta.oSet(q.ToMat3())
                true
            } else {
                delta.Identity()
                false
            }
        }

        fun GetOrigin(currentTime: Int, pos: idVec3?) {
            var i: Int
            var blend: Array<idAnimBlend?>?
            val blendWeight = CFloat()
            if (null == modelDef || null == modelDef.ModelHandle()) {
                pos.Zero()
                return
            }
            pos.Zero()
            blendWeight.setVal(0.0f)
            blend = channels.get(Anim.ANIMCHANNEL_ALL)
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend.get(i).BlendOrigin(currentTime, pos, blendWeight, removeOriginOffset)
                i++
            }
            if (modelDef.Joints().get(0).channel != 0) {
                val k = modelDef.Joints().get(0).channel
                blend = channels.get(k)
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend.get(i).BlendOrigin(currentTime, pos, blendWeight, removeOriginOffset)
                    i++
                }
            }
            pos.oPluSet(modelDef.GetVisualOffset())
        }

        fun GetBounds(currentTime: Int, bounds: idBounds?): Boolean {
            var i: Int
            var j: Int
            var blend: Array<idAnimBlend?>
            var count: Int
            if (null == modelDef || null == modelDef.ModelHandle()) {
                return false
            }
            count = if (AFPoseJoints.size != 0) {
                bounds.oSet(AFPoseBounds)
                1
            } else {
                bounds.Clear()
                0
            }
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (channels.get(i).get(j).AddBounds(currentTime, bounds, removeOriginOffset)) {
                        count++
                    }
                    j++
                }
                i++
            }
            if (0 == count) {
                return if (!frameBounds.IsCleared()) {
                    bounds.oSet(frameBounds)
                    true
                } else {
                    bounds.Zero()
                    false
                }
            }
            bounds.TranslateSelf(modelDef.GetVisualOffset())
            if (SysCvar.g_debugBounds.GetBool()) {
                if (bounds.oGet(1, 0) - bounds.oGet(0, 0) > 2048 || bounds.oGet(1, 1) - bounds.oGet(0, 1) > 2048) {
                    if (entity != null) {
                        Game_local.gameLocal.Warning(
                            "big frameBounds on entity '%s' with model '%s': %f,%f",
                            entity.name,
                            modelDef.ModelHandle().Name(),
                            bounds.oGet(1, 0) - bounds.oGet(0, 0),
                            bounds.oGet(1, 1) - bounds.oGet(0, 1)
                        )
                    } else {
                        Game_local.gameLocal.Warning(
                            "big frameBounds on model '%s': %f,%f",
                            modelDef.ModelHandle().Name(),
                            bounds.oGet(1, 0) - bounds.oGet(0, 0),
                            bounds.oGet(1, 1) - bounds.oGet(0, 1)
                        )
                    }
                }
            }
            frameBounds.oSet(bounds)
            return true
        }

        fun CurrentAnim(channelNum: Int): idAnimBlend? {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::CurrentAnim : channel out of range")
            }
            return channels.get(channelNum).get(0)
        }

        fun Clear(channelNum: Int, currentTime: Int, cleartime: Int) {
            var i: Int
            val blend: Array<idAnimBlend?>?
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::Clear : channel out of range")
            }
            blend = channels.get(channelNum)
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend.get(i).Clear(currentTime, cleartime)
                i++
            }
            ForceUpdate()
        }

        fun SetFrame(channelNum: Int, animNum: Int, frame: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::SetFrame : channel out of range")
            }
            if (null == modelDef || null == modelDef.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels.get(channelNum).get(0).SetFrame(modelDef, animNum, frame, currentTime, blendTime)
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        fun CycleAnim(channelNum: Int, animNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::CycleAnim : channel out of range")
            }
            if (null == modelDef || null == modelDef.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels.get(channelNum).get(0).CycleAnim(modelDef, animNum, currentTime, blendTime)
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        fun PlayAnim(channelNum: Int, animNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::PlayAnim : channel out of range")
            }
            if (null == modelDef || null == modelDef.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels.get(channelNum).get(0).PlayAnim(modelDef, animNum, currentTime, blendTime)
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        // copies the current anim from fromChannelNum to channelNum.
        // the copied anim will have frame commands disabled to avoid executing them twice.
        fun SyncAnimChannels(channelNum: Int, fromChannelNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels || fromChannelNum < 0 || fromChannelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Companion.Error("idAnimator::SyncToChannel : channel out of range")
            }
            val fromBlend = idAnimBlend(channels.get(fromChannelNum).get(0))
            var toBlend = idAnimBlend(channels.get(channelNum).get(0))
            val weight = fromBlend.blendEndValue
            if (fromBlend.Anim() !== toBlend.Anim() || fromBlend.GetStartTime() != toBlend.GetStartTime() || fromBlend.GetEndTime() != toBlend.GetEndTime()) {
                PushAnims(channelNum, currentTime, blendTime)
                Simd.SIMDProcessor.Memcpy(
                    channels.get(channelNum),
                    channels.get(fromChannelNum),
                    Anim.ANIM_MaxAnimsPerChannel
                )
                toBlend = fromBlend
                toBlend.blendStartValue = 0.0f
                toBlend.blendEndValue = 0.0f
            }
            toBlend.SetWeight(weight, currentTime - 1, blendTime)

            // disable framecommands on the current channel so that commands aren't called twice
            toBlend.AllowFrameCommands(false)
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        fun SetJointPos(   /*jointHandle_t*/jointnum: Int, transform_type: jointModTransform_t?, pos: idVec3?) {
            var i: Int
            var jointMod: jointMod_t?
            if (null == modelDef || null == modelDef.ModelHandle() || jointnum < 0 || jointnum >= numJoints.getVal()) {
                return
            }
            jointMod = null
            i = 0
            while (i < jointMods.size) {
                if (jointMods.get(i).jointnum == jointnum) {
                    jointMod = jointMods.get(i)
                    break
                } else if (jointMods.get(i).jointnum > jointnum) {
                    break
                }
                i++
            }
            if (null == jointMod) {
                jointMod = jointMod_t()
                jointMod.jointnum = jointnum
                jointMod.mat.Identity()
                jointMod.transform_axis = jointModTransform_t.JOINTMOD_NONE
                if (i >= jointMods.size) {
                    jointMods.add(jointMod)
                } else {
                    jointMods.set(i, jointMod)
                }
            }
            jointMod.pos.oSet(pos)
            jointMod.transform_pos = transform_type
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
            ForceUpdate()
        }

        fun SetJointAxis(   /*jointHandle_t*/jointnum: Int, transform_type: jointModTransform_t?, mat: idMat3?) {
            var i: Int
            var jointMod: jointMod_t?
            if (null == modelDef || null == modelDef.ModelHandle() || jointnum < 0 || jointnum >= numJoints.getVal()) {
                return
            }
            jointMod = null
            i = 0
            while (i < jointMods.size) {
                if (jointMods.get(i).jointnum == jointnum) {
                    jointMod = jointMods.get(i)
                    break
                } else if (jointMods.get(i).jointnum > jointnum) {
                    break
                }
                i++
            }
            if (null == jointMod) {
                jointMod = jointMod_t()
                jointMod.jointnum = jointnum
                jointMod.pos.Zero()
                jointMod.transform_pos = jointModTransform_t.JOINTMOD_NONE
                if (i >= jointMods.size) {
                    jointMods.add(jointMod)
                } else {
                    jointMods.set(i, jointMod)
                }
            }
            jointMod.mat.oSet(mat)
            jointMod.transform_axis = transform_type
            if (entity != null) {
                entity.BecomeActive(Entity.TH_ANIMATE)
            }
            ForceUpdate()
        }

        fun ClearJoint(   /*jointHandle_t*/jointnum: Int) {
            var i: Int
            if (null == modelDef || null == modelDef.ModelHandle() || jointnum < 0 || jointnum >= numJoints.getVal()) {
                return
            }
            i = 0
            while (i < jointMods.size) {
                if (jointMods.get(i).jointnum == jointnum) {
//			delete jointMods[ i ];
                    jointMods.removeAt(i)
                    ForceUpdate()
                    break
                } else if (jointMods.get(i).jointnum > jointnum) {
                    break
                }
                i++
            }
        }

        fun ClearAllJoints() {
            if (jointMods.size != 0) {
                ForceUpdate()
            }
            jointMods.clear()
        }

        fun InitAFPose() {
            if (null == modelDef) {
                return
            }

            //AFPoseJoints.SetNum(modelDef.Joints().size(), false);
            //AFPoseJoints.SetNum(0, false);
//            AFPoseJoints.clear();
//            AFPoseJoints.trimToSize();
//            AFPoseJoints.addAll(new ArrayList<>(modelDef.Joints().size()));
//            //AFPoseJointFrame.clear();
////            for (int i = 0; i < modelDef.Joints().size(); i++) {
////                AFPoseJoints.add(i, -1);
////                AFPoseJointFrame.add(i, new idJointQuat());
////            }
//            AFPoseJointFrame.clear();
//            AFPoseJointFrame.trimToSize();
//            AFPoseJointFrame.addAll(new ArrayList<>(modelDef.Joints().size()));
            //AFPoseJointMods.SetNum(modelDef.Joints().size(), false);
            //AFPoseJointFrame.SetNum(modelDef.Joints().size(), false);
        }

        fun SetAFPoseJointMod(   /*jointHandle_t*/jointNum: Int,
                                                  mod: AFJointModType_t?,
                                                  axis: idMat3?,
                                                  origin: idVec3?
        ) {
            if (jointNum >= AFPoseJointMods.size) {
                for (i in AFPoseJointMods.size..jointNum) {
                    AFPoseJointMods.add(i, idAFPoseJointMod())
                }
                AFPoseJointMods.add(jointNum, idAFPoseJointMod())
            } else {
                AFPoseJointMods.set(jointNum, idAFPoseJointMod())
            }
            AFPoseJointMods.get(jointNum).mod = mod
            AFPoseJointMods.get(jointNum).axis.oSet(axis)
            AFPoseJointMods.get(jointNum).origin.oSet(origin)
            val index =
                BinSearch.idBinSearch_GreaterEqual<Any?>(AFPoseJoints.toTypedArray(), AFPoseJoints.size, jointNum)
            if (index >= AFPoseJoints.size || jointNum != AFPoseJoints.get(index)) {
                if (index >= AFPoseJoints.size) {
                    AFPoseJoints.add(index)
                } else {
                    AFPoseJoints.set(jointNum, index)
                }
            }
        }

        fun FinishAFPose(animNum: Int, bounds: idBounds?, time: Int) {
            var i: Int
            var j: Int
            val numJoints: Int
            var parentNum: Int
            var jointMod: Int
            var jointNum: Int
            val jointParent: Array<Int?>?
            if (null == modelDef) {
                return
            }
            val anim = modelDef.GetAnim(animNum) ?: return
            numJoints = modelDef.Joints().size
            if (0 == numJoints) {
                return
            }
            val md5 = modelDef.ModelHandle()
            val md5anim = anim.MD5Anim(0)
            if (numJoints != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    md5.Name(),
                    md5anim.Name()
                )
                return
            }
            val jointFrame = arrayOfNulls<idJointQuat?>(numJoints)
            md5anim.GetSingleFrame(
                0,
                jointFrame,
                TempDump.itoi(modelDef.GetChannelJoints(Anim.ANIMCHANNEL_ALL)),
                modelDef.NumJointsOnChannel(Anim.ANIMCHANNEL_ALL)
            )
            if (removeOriginOffset) {
                if (Anim_Blend.VELOCITY_MOVE) {
                    jointFrame[0].t.x = 0.0f
                } else {
                    jointFrame[0].t.Zero()
                }
            }
            val joints =
                Stream.generate { idJointMat() }.limit(numJoints.toLong()).toArray<idJointMat?> { _Dummy_.__Array__() }

            // convert the joint quaternions to joint matrices
            Simd.SIMDProcessor.ConvertJointQuatsToJointMats(joints, jointFrame, numJoints)

            // first joint is always root of entire hierarchy
            j = if (AFPoseJoints.size != 0 && AFPoseJoints.get(0) == 0) {
                when (AFPoseJointMods.get(0).mod) {
                    AFJointModType_t.AF_JOINTMOD_AXIS -> {
                        joints[0].SetRotation(AFPoseJointMods.get(0).axis)
                    }
                    AFJointModType_t.AF_JOINTMOD_ORIGIN -> {
                        joints[0].SetTranslation(AFPoseJointMods.get(0).origin)
                    }
                    AFJointModType_t.AF_JOINTMOD_BOTH -> {
                        joints[0].SetRotation(AFPoseJointMods.get(0).axis)
                        joints[0].SetTranslation(AFPoseJointMods.get(0).origin)
                    }
                }
                1
            } else {
                0
            }

            // pointer to joint info
            jointParent = modelDef.JointParents()

            // transform the child joints
            i = 1
            while (j < AFPoseJoints.size) {
                jointMod = AFPoseJoints.get(j)

                // transform any joints preceding the joint modifier
                Simd.SIMDProcessor.TransformJoints(joints, TempDump.itoi(jointParent), i, jointMod - 1)
                i = jointMod
                parentNum = jointParent.get(i)
                when (AFPoseJointMods.get(jointMod).mod) {
                    AFJointModType_t.AF_JOINTMOD_AXIS -> {
                        joints[i].SetRotation(AFPoseJointMods.get(jointMod).axis)
                        joints[i].SetTranslation(
                            joints[parentNum].ToVec3().oPlus(joints[i].ToVec3().oMultiply(joints[parentNum].ToMat3()))
                        )
                    }
                    AFJointModType_t.AF_JOINTMOD_ORIGIN -> {
                        joints[i].SetRotation(joints[i].ToMat3().oMultiply(joints[parentNum].ToMat3()))
                        joints[i].SetTranslation(AFPoseJointMods.get(jointMod).origin)
                    }
                    AFJointModType_t.AF_JOINTMOD_BOTH -> {
                        joints[i].SetRotation(AFPoseJointMods.get(jointMod).axis)
                        joints[i].SetTranslation(AFPoseJointMods.get(jointMod).origin)
                    }
                }
                j++
                i++
            }

            // transform the rest of the hierarchy
            Simd.SIMDProcessor.TransformJoints(joints, TempDump.itoi(jointParent), i, numJoints - 1)

            // untransform hierarchy
            Simd.SIMDProcessor.UntransformJoints(joints, TempDump.itoi(jointParent), 1, numJoints - 1)

            // convert joint matrices back to joint quaternions
            Simd.SIMDProcessor.ConvertJointMatsToJointQuats(AFPoseJointFrame, joints, numJoints)

            // find all modified joints and their parents
            val blendJoints = BooleanArray(numJoints) //memset( blendJoints, 0, numJoints * sizeof( bool ) );

            // mark all modified joints and their parents
            i = 0
            while (i < AFPoseJoints.size) {
                jointNum = AFPoseJoints.get(i)
                while (jointNum != Model.INVALID_JOINT) {
                    blendJoints[jointNum] = true
                    jointNum = jointParent.get(jointNum)
                }
                i++
            }

            // lock all parents of modified joints
            //AFPoseJoints.SetNum(0, false);
            AFPoseJoints.clear()
            i = 0
            while (i < numJoints) {
                if (blendJoints[i]) {
                    AFPoseJoints.add(i)
                }
                i++
            }
            AFPoseBounds.oSet(bounds)
            AFPoseTime = time
            ForceUpdate()
        }

        fun SetAFPoseBlendWeight(blendWeight: Float) {
            AFPoseBlendWeight = blendWeight
        }

        fun BlendAFPose(blendFrame: Array<idJointQuat?>?): Boolean {
            if (0 == AFPoseJoints.size) {
                return false
            }
            Simd.SIMDProcessor.BlendJoints(
                blendFrame,
                AFPoseJointFrame,
                AFPoseBlendWeight,
                TempDump.itoi(AFPoseJoints.toArray(IntFunction<Array<Int?>?> { _Dummy_.__Array__() })),
                AFPoseJoints.size
            )
            return true
        }

        fun ClearAFPose() {
            if (AFPoseJoints.size != 0) {
                ForceUpdate()
            }
            AFPoseBlendWeight = 1.0f
            //AFPoseJoints.SetNum(0, false);
            AFPoseJoints.clear()
            AFPoseBounds.Clear()
            AFPoseTime = 0
        }

        fun ClearAllAnims(currentTime: Int, cleartime: Int) {
            var i: Int
            i = 0
            while (i < Anim.ANIM_NumAnimChannels) {
                Clear(i, currentTime, cleartime)
                i++
            }
            ClearAFPose()
            ForceUpdate()
        }

        fun  /*jointHandle_t*/GetJointHandle(name: String?): Int {
            return if (null == modelDef || null == modelDef.ModelHandle()) {
                Model.INVALID_JOINT
            } else modelDef.ModelHandle().GetJointHandle(name)
        }

        fun  /*jointHandle_t*/GetJointHandle(name: idStr?): Int {
            return GetJointHandle(name.toString())
        }

        fun GetJointName(   /*jointHandle_t*/handle: Int): String? {
            return if (null == modelDef || null == modelDef.ModelHandle()) {
                ""
            } else modelDef.ModelHandle().GetJointName(handle)
        }

        fun GetChannelForJoint(   /*jointHandle_t*/joint: Int): Int {
            if (null == modelDef) {
                idGameLocal.Companion.Error("idAnimator::GetChannelForJoint: NULL model")
            }
            if (joint < 0 || joint >= numJoints.getVal()) {
                idGameLocal.Companion.Error("idAnimator::GetChannelForJoint: invalid joint num (%d)", joint)
            }
            return modelDef.GetJoint(joint).channel
        }

        fun GetJointTransform(   /*jointHandle_t*/jointHandle: Int,
                                                  currentTime: Int,
                                                  offset: idVec3?,
                                                  axis: idMat3?
        ): Boolean {
            if (null == modelDef || jointHandle < 0 || jointHandle >= modelDef.NumJoints()) {
                return false
            }
            CreateFrame(currentTime, false)
            offset.oSet(joints.get(jointHandle).ToVec3())
            axis.oSet(joints.get(jointHandle).ToMat3())
            return true
        }

        fun GetJointLocalTransform(   /*jointHandle_t*/jointHandle: Int,
                                                       currentTime: Int,
                                                       offset: idVec3?,
                                                       axis: idMat3?
        ): Boolean {
            if (null == modelDef) {
                return false
            }
            val modelJoints = modelDef.Joints()
            if (jointHandle < 0 || jointHandle >= modelJoints.size) {
                return false
            }

            // FIXME: overkill
            CreateFrame(currentTime, false)
            if (jointHandle == 0) {
                offset.oSet(joints.get(jointHandle).ToVec3())
                axis.oSet(joints.get(jointHandle).ToMat3())
                return true
            }
            val m = idJointMat(joints.get(jointHandle))
            m.oDivSet(joints.get(modelJoints.get(jointHandle).parentNum))
            offset.oSet(m.ToVec3())
            axis.oSet(m.ToMat3())
            return true
        }

        fun GetAnimFlags(animNum: Int): animFlags_t? {
            val result: animFlags_t
            val anim = GetAnim(animNum)
            if (anim != null) {
                return anim.GetAnimFlags()
            }

//	memset( &result, 0, sizeof( result ) );
            result = animFlags_t()
            return result
        }

        fun NumFrames(animNum: Int): Int {
            val anim = GetAnim(animNum)
            return anim?.NumFrames() ?: 0
        }

        fun NumSyncedAnims(animNum: Int): Int {
            val anim = GetAnim(animNum)
            return anim?.NumAnims() ?: 0
        }

        fun AnimName(animNum: Int): String? {
            val anim = GetAnim(animNum)
            return if (anim != null) {
                anim.Name()
            } else {
                ""
            }
        }

        fun AnimFullName(animNum: Int): String? {
            val anim = GetAnim(animNum)
            return if (anim != null) {
                anim.FullName()
            } else {
                ""
            }
        }

        fun AnimLength(animNum: Int): Int {
            val anim = GetAnim(animNum)
            return anim?.Length() ?: 0
        }

        fun TotalMovementDelta(animNum: Int): idVec3? {
            val anim = GetAnim(animNum)
            return if (anim != null) {
                anim.TotalMovementDelta()
            } else {
                Vector.getVec3_origin()
            }
        }

        private fun FreeData() {
            var i: Int
            var j: Int
            if (entity != null) {
                entity.BecomeInactive(Entity.TH_ANIMATE)
            }
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels.get(i).get(j).Reset(null)
                    j++
                }
                i++
            }
            jointMods.clear()

//	Mem_Free16( joints );
            joints = null
            numJoints.setVal(0)
            modelDef = null
            ForceUpdate()
        }

        private fun PushAnims(channelNum: Int, currentTime: Int, blendTime: Int) {
            var i: Int
            val channel: Array<idAnimBlend?>?
            channel = channels.get(channelNum)
            if (0f == channel.get(0).GetWeight(currentTime) || channel.get(0).starttime == currentTime) {
                return
            }
            i = Anim.ANIM_MaxAnimsPerChannel - 1
            while (i > 0) {
                channel.get(i) = idAnimBlend(channel.get(i - 1))
                i--
            }
            channel.get(0).Reset(modelDef)
            channel.get(1).Clear(currentTime, blendTime)
            ForceUpdate()
        }

        companion object {
            private val r_showSkel: idCVar? = idCVar(
                "r_showSkel",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER,
                "",
                0,
                2,
                ArgCompletion_Integer(0, 2)
            )
        }

        // ~idAnimator();
        init {
            var i: Int
            var j: Int
            modelDef = null
            entity = null
            jointMods = ArrayList()
            numJoints = CInt()
            joints = null
            lastTransformTime = -1
            stoppedAnimatingUpdate = false
            removeOriginOffset = false
            forceUpdate = false
            frameBounds = idBounds()
            frameBounds.Clear()
            AFPoseJoints = ArrayList()
            AFPoseJointMods = ArrayList()
            AFPoseJointFrame = ArrayList()
            AFPoseBounds = idBounds()
            ClearAFPose()
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels.get(i).get(j) = idAnimBlend()
                    j++
                }
                i++
            }
        }
    }
}