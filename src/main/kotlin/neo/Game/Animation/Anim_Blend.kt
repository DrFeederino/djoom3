package neo.Game.Animation

import neo.Game.AI.AI_Events
import neo.Game.Actor
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
import neo.Game.Entity
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.FX.idEntityFx
import neo.Game.GameSys.Class
import neo.Game.GameSys.Event
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Sound
import neo.Renderer.Model
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.DeclManager
import neo.framework.DeclManager.*
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
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Quat.idQuat
import neo.idlib.math.Simd
import neo.idlib.math.Vector.getVec3_origin
import neo.idlib.math.Vector.getVec3_zero
import neo.idlib.math.Vector.idVec3
import java.util.*
import java.util.function.Consumer

/**
 *
 */
object Anim_Blend {
    val channelNames /*[ ANIM_NumAnimChannels ]*/: Array<String> = arrayOf(
        "all", "torso", "legs", "head", "eyelids"
    )
    const val VELOCITY_MOVE = false
    fun ANIM_GetModelDefFromEntityDef(args: idDict): idDeclModelDef? {
        val modelDef: idDeclModelDef?
        val name = args.GetString("model")
        modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, name, false) as idDeclModelDef
        return if (modelDef?.ModelHandle() != null) {
            modelDef
        } else null
    }

    /*
     ==============================================================================================

     idAnim

     ==============================================================================================
     */
    class idAnim {
        private val frameCommands: ArrayList<frameCommand_t> = ArrayList()
        private val frameLookup: ArrayList<frameLookup_t> = ArrayList()
        private var anims: ArrayList<idMD5Anim> = ArrayList(Anim.ANIM_MaxSyncedAnims)
        private var flags: animFlags_t
        private var modelDef: idDeclModelDef?
        private val name: idStr = idStr()
        private var numAnims: Int
        private val realname: idStr = idStr()

        //
        //
        constructor() {
            modelDef = null
            numAnims = 0
            flags = animFlags_t()
        }

        constructor(modelDef: idDeclModelDef?, anim: idAnim) {
            var i: Int
            this.modelDef = modelDef
            numAnims = anim.numAnims
            name.set(anim.name)
            realname.set(anim.realname)
            flags = animFlags_t(anim.flags)
            anims = ArrayList(anims.size)
            i = 0
            while (i < numAnims) {
                anims[i] = anim.anims[i]
                anims[i].IncreaseRefs()
                i++
            }

            //frameLookup.SetNum(anim.frameLookup.Num());
            if (anim.frameLookup.size > 0) {
                i = 0
                while (i < anim.frameLookup.size) {
                    val frameLookup_t = anim.frameLookup[i]
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
                    frameCommands.add(anim.frameCommands[i])
                } else {
                    frameCommands[i] = anim.frameCommands[i]
                }
                frameCommands[i].string.set(idStr(anim.frameCommands[i].string))
                i++
            }
        }

        // ~idAnim();
        fun SetAnim(
            modelDef: idDeclModelDef?,
            sourceName: String,
            animName: String,
            num: Int,
            md5anims: kotlin.collections.ArrayList<idMD5Anim> /*[ ANIM_MaxSyncedAnims ]*/
        ) {
            var i: Int
            this.modelDef = modelDef
            i = 0
            while (i < numAnims) {
                anims[i].DecreaseRefs()
                anims.removeAt(i)
                i++
            }
            assert(num > 0 && num <= Anim.ANIM_MaxSyncedAnims)
            numAnims = num
            realname.set(sourceName)
            name.set(animName)
            i = 0
            while (i < num) {
                anims[i] = md5anims[i]
                anims[i].IncreaseRefs()
                i++
            }

//	memset( &flags, 0, sizeof( flags ) );
            flags = animFlags_t()
            frameCommands.forEach(Consumer { frame: frameCommand_t -> frame.string.set("") })
            frameLookup.clear()
            frameCommands.clear()
        }

        fun Name(): String {
            return name.toString()
        }

        fun FullName(): String {
            return realname.toString()
        }

        /*
         =====================
         idAnim::MD5Anim

         index 0 will never be NULL.  Any anim >= NumAnims will return NULL.
         =====================
         */
        fun MD5Anim(num: Int): idMD5Anim? {
            return if (anims.isNullOrEmpty()) {
                null
            } else anims[num]
        }

        fun modelDef(): idDeclModelDef? {
            return modelDef
        }

        fun Length(): Int {
            return if (anims.isNullOrEmpty()) {
                0
            } else anims[0].Length()
        }

        fun NumFrames(): Int {
            return if (anims.isNullOrEmpty()) {
                0
            } else anims[0].NumFrames()
        }

        fun NumAnims(): Int {
            return numAnims
        }

        fun TotalMovementDelta(): idVec3 {
            return if (anims.isNullOrEmpty()) {
                getVec3_zero()
            } else anims[0].TotalMovementDelta()
        }

        fun GetOrigin(offset: idVec3, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (animNum > anims.size) {
                offset.Zero()
                return false
            }
            anims[animNum].GetOrigin(offset, currentTime, cyclecount)
            return true
        }

        fun GetOriginRotation(rotation: idQuat, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (animNum > anims.size) {
                rotation.set(0.0f, 0.0f, 0.0f, 1.0f)
                return false
            }
            anims[animNum].GetOriginRotation(rotation, currentTime, cyclecount)
            return true
        }

        fun GetBounds(bounds: idBounds, animNum: Int, currentTime: Int, cyclecount: Int): Boolean {
            if (animNum > anims.size) {
                return false
            }
            anims[animNum].GetBounds(bounds, currentTime, cyclecount)
            return true
        }

        /*
         =====================
         idAnim::AddFrameCommand

         Returns NULL if no error.
         =====================
         */
        @Throws(idException::class)
        fun AddFrameCommand(modelDef: idDeclModelDef, framenum: Int, src: idLexer, def: idDict?): String? {
            var framenum = framenum
            var i: Int
            val index: Int
            var text: idStr
            var funcname: idStr
            val fc: frameCommand_t
            val token = idToken()
            val jointInfo: jointInfo_t?

            // make sure we're within bounds
            if (framenum < 1 || framenum > anims[0].NumFrames()) {
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
                fc.string.set(token)
            } else if (token.toString() == "event") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_EVENTFUNCTION
                val ev: Event.idEventDef = Event.idEventDef.FindEvent(token.toString())!!
                if (ev.GetNumArgs() != 0) {
                    return Str.va("Event '%s' has arguments", token)
                }
                fc.string.set(token)
            } else if (token.toString() == "sound_voice2") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_VOICE2
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_voice") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_VOICE
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body2") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY2
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body3") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY3
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_body") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_BODY
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_weapon") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_WEAPON
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_global") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_GLOBAL
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_item") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_ITEM
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound_chatter") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND_CHATTER
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
                        Game_local.gameLocal.Warning("Sound '%s' not found", token)
                    }
                }
            } else if (token.toString() == "sound") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_SOUND
                if (0 == token.Cmpn("snd_", 4)) {
                    fc.string.set(token)
                } else {
                    fc.soundShader = DeclManager.declManager.FindSound(token)
                    if (fc.soundShader!!.GetState() == declState_t.DS_DEFAULTED) {
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
                fc.string.set(token)
            } else if (token.toString() == "trigger") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_TRIGGER
                fc.string.set(token)
            } else if (token.toString() == "triggerSmokeParticle") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_TRIGGER_SMOKE_PARTICLE
                fc.string.set(token)
            } else if (token.toString() == "melee") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_MELEE
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string.set(token)
            } else if (token.toString() == "direct_damage") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_DIRECTDAMAGE
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string.set(token)
            } else if (token.toString() == "attack_begin") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_BEGINATTACK
                if (TempDump.NOT(Game_local.gameLocal.FindEntityDef(token.toString(), false))) {
                    return Str.va("Unknown entityDef '%s'", token)
                }
                fc.string.set(token)
            } else if (token.toString() == "attack_end") {
                fc.type = frameCommandType_t.FC_ENDATTACK
            } else if (token.toString() == "muzzle_flash") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (!token.IsEmpty() && TempDump.NOT(modelDef!!.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_MUZZLEFLASH
                fc.string.set(token)
            } else if (token.toString() == "muzzle_flash") {
                fc.type = frameCommandType_t.FC_MUZZLEFLASH
                fc.string.set("")
            } else if (token.toString() == "create_missile") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (TempDump.NOT(modelDef!!.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_CREATEMISSILE
                fc.string.set(token)
            } else if (token.toString() == "launch_missile") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                if (TempDump.NOT(modelDef!!.FindJoint(token.toString()))) {
                    return Str.va("Joint '%s' not found", token)
                }
                fc.type = frameCommandType_t.FC_LAUNCHMISSILE
                fc.string.set(token)
            } else if (token.toString() == "fire_missile_at_target") {
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                jointInfo = modelDef!!.FindJoint(token.toString())
                if (null == jointInfo) {
                    return Str.va("Joint '%s' not found", token)
                }
                if (!src.ReadTokenOnLine(token)) {
                    return "Unexpected end of line"
                }
                fc.type = frameCommandType_t.FC_FIREMISSILEATTARGET
                fc.string.set(token)
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
                    fc.string.set(token)
                }
            } else if (token.toString() == "aviGame") {
                fc.type = frameCommandType_t.FC_AVIGAME
                if (src.ReadTokenOnLine(token)) {
                    fc.string.set(token)
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
                while (i < anims[0].NumFrames()) {

                    // init with setting size as anims[0].NumFrames()!
                    frameLookup.add(frameLookup_t())
                    i++
                }
                //frameLookup.trimToSize();
            }

            // allocate space for a new command
            //frameCommands.Alloc();

            // calculate the index of the new command
            index = frameLookup[framenum].firstCommand + frameLookup[framenum].num

            // move all commands from our index onward up one to give us space for our new command
            // size is the actual size of the list, not size + 1 like it is in idList
            i = frameCommands.size
            while (i > index) {
                if (i >= frameCommands.size) {
                    frameCommands.add(frameCommands[i - 1])
                } else {
                    frameCommands[i] = frameCommands[i - 1]
                }
                i--
            }

            // fix the indices of any later frames to account for the inserted command
            i = framenum + 1
            while (i < frameLookup.size) {
                frameLookup[i].firstCommand++
                i++
            }

            // store the new command
            if (index >= frameCommands.size) {
                frameCommands.add(fc)
            } else {
                frameCommands[index] = fc
            }

            // increase the number of commands on this frame
            frameLookup[framenum].num++

            // return with no error
            return null
        }

        fun CallFrameCommands(ent: idEntity, from: Int, to: Int) {
            var index: Int
            var end: Int
            var frame: Int
            val numframes: Int
            numframes = anims[0].NumFrames()
            frame = from
            while (frame != to) {
                frame++
                if (frame >= numframes) {
                    frame = 0
                }
                index = frameLookup[frame].firstCommand
                end = index + frameLookup[frame].num
                while (index < end) {
                    val command = frameCommands[index++]
                    when (command.type) {
                        frameCommandType_t.FC_SCRIPTFUNCTION -> {
                            Game_local.gameLocal.CallFrameCommand(ent, command.function!!)
                        }
                        frameCommandType_t.FC_SCRIPTFUNCTIONOBJECT -> {
                            Game_local.gameLocal.CallObjectFrameCommand(ent, command.string.toString())
                        }
                        frameCommandType_t.FC_EVENTFUNCTION -> {
                            val ev: Event.idEventDef = Event.idEventDef.FindEvent(command.string.toString())!!
                            ent!!.ProcessEvent(ev)
                        }
                        frameCommandType_t.FC_SOUND -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ANY,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ANY.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_VOICE -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_VOICE,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_voice' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_VOICE.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_VOICE2 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_VOICE2,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_voice2' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_VOICE2.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY,
                                        0,
                                        false,
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY2 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY2,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body2' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY2.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_BODY3 -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_BODY3,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_body3' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_BODY3.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_WEAPON -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_WEAPON,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_weapon' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_WEAPON.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_GLOBAL -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ANY,
                                        Sound.SSF_GLOBAL,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_global' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ANY.ordinal,
                                    Sound.SSF_GLOBAL,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_ITEM -> {
                            if (TempDump.NOT(command.soundShader)) {
                                if (!ent!!.StartSound(
                                        command.string.toString(),
                                        gameSoundChannel_t.SND_CHANNEL_ITEM,
                                        0,
                                        false
                                    )
                                ) {
                                    Game_local.gameLocal.Warning(
                                        "Framecommand 'sound_item' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                        ent!!.name, FullName(), frame + 1, command.string
                                    )
                                }
                            } else {
                                ent!!.StartSoundShader(
                                    command.soundShader,
                                    gameSoundChannel_t.SND_CHANNEL_ITEM.ordinal,
                                    0,
                                    false
                                )
                            }
                        }
                        frameCommandType_t.FC_SOUND_CHATTER -> {
                            if (ent!!.CanPlayChatterSounds()) {
                                if (TempDump.NOT(command.soundShader)) {
                                    if (!ent!!.StartSound(
                                            command.string.toString(),
                                            gameSoundChannel_t.SND_CHANNEL_VOICE,
                                            0,
                                            false
                                        )
                                    ) {
                                        Game_local.gameLocal.Warning(
                                            "Framecommand 'sound_chatter' on entity '%s', anim '%s', frame %d: Could not find sound '%s'",
                                            ent!!.name, FullName(), frame + 1, command.string
                                        )
                                    }
                                } else {
                                    ent!!.StartSoundShader(
                                        command.soundShader,
                                        gameSoundChannel_t.SND_CHANNEL_VOICE.ordinal,
                                        0,
                                        false
                                    )
                                }
                            }
                        }
                        frameCommandType_t.FC_FX -> {
                            idEntityFx.StartFx(
                                command.string.toString(),
                                getVec3_zero(),
                                idMat3.getMat3_zero(),
                                ent,
                                true
                            )
                        }
                        frameCommandType_t.FC_SKIN -> {
                            ent!!.SetSkin(command.skin)
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
                                    ent!!.name, FullName(), frame + 1, command.string
                                )
                            }
                        }
                        frameCommandType_t.FC_TRIGGER_SMOKE_PARTICLE -> {
                            ent!!.ProcessEvent(AI_Events.AI_TriggerParticles, command.string.toString())
                        }
                        frameCommandType_t.FC_MELEE -> {
                            ent!!.ProcessEvent(AI_Events.AI_AttackMelee, command.string.toString())
                        }
                        frameCommandType_t.FC_DIRECTDAMAGE -> {
                            ent!!.ProcessEvent(AI_Events.AI_DirectDamage, command.string.toString())
                        }
                        frameCommandType_t.FC_BEGINATTACK -> {
                            ent!!.ProcessEvent(AI_Events.AI_BeginAttack, command.string.toString())
                        }
                        frameCommandType_t.FC_ENDATTACK -> {
                            ent!!.ProcessEvent(AI_Events.AI_EndAttack)
                        }
                        frameCommandType_t.FC_MUZZLEFLASH -> {
                            ent!!.ProcessEvent(AI_Events.AI_MuzzleFlash, command.string.toString())
                        }
                        frameCommandType_t.FC_CREATEMISSILE -> {
                            ent!!.ProcessEvent(AI_Events.AI_CreateMissile, command.string.toString())
                        }
                        frameCommandType_t.FC_LAUNCHMISSILE -> {
                            ent!!.ProcessEvent(AI_Events.AI_AttackMissile, command.string.toString())
                        }
                        frameCommandType_t.FC_FIREMISSILEATTARGET -> {
                            ent!!.ProcessEvent(
                                AI_Events.AI_FireMissileAtTarget,
                                modelDef!!.GetJointName(command.index),
                                command.string.toString()
                            )
                        }
                        frameCommandType_t.FC_FOOTSTEP -> {
                            ent!!.ProcessEvent(Actor.EV_Footstep)
                        }
                        frameCommandType_t.FC_LEFTFOOT -> {
                            ent!!.ProcessEvent(Actor.EV_FootstepLeft)
                        }
                        frameCommandType_t.FC_RIGHTFOOT -> {
                            ent!!.ProcessEvent(Actor.EV_FootstepRight)
                        }
                        frameCommandType_t.FC_ENABLE_EYE_FOCUS -> {
                            ent!!.ProcessEvent(Actor.AI_EnableEyeFocus)
                        }
                        frameCommandType_t.FC_DISABLE_EYE_FOCUS -> {
                            ent!!.ProcessEvent(Actor.AI_DisableEyeFocus)
                        }
                        frameCommandType_t.FC_DISABLE_GRAVITY -> {
                            ent!!.ProcessEvent(AI_Events.AI_DisableGravity)
                        }
                        frameCommandType_t.FC_ENABLE_GRAVITY -> {
                            ent!!.ProcessEvent(AI_Events.AI_EnableGravity)
                        }
                        frameCommandType_t.FC_JUMP -> {
                            ent!!.ProcessEvent(AI_Events.AI_JumpFrame)
                        }
                        frameCommandType_t.FC_ENABLE_CLIP -> {
                            ent!!.ProcessEvent(AI_Events.AI_EnableClip)
                        }
                        frameCommandType_t.FC_DISABLE_CLIP -> {
                            ent!!.ProcessEvent(AI_Events.AI_DisableClip)
                        }
                        frameCommandType_t.FC_ENABLE_WALK_IK -> {
                            ent!!.ProcessEvent(Actor.EV_EnableWalkIK)
                        }
                        frameCommandType_t.FC_DISABLE_WALK_IK -> {
                            ent!!.ProcessEvent(Actor.EV_DisableWalkIK)
                        }
                        frameCommandType_t.FC_ENABLE_LEG_IK -> {
                            ent!!.ProcessEvent(Actor.EV_EnableLegIK, command.index)
                        }
                        frameCommandType_t.FC_DISABLE_LEG_IK -> {
                            ent!!.ProcessEvent(Actor.EV_DisableLegIK, command.index)
                        }
                        frameCommandType_t.FC_RECORDDEMO -> {
                            if (!command.string.toString().isNullOrEmpty()) {
                                CmdSystem.cmdSystem.BufferCommandText(
                                    cmdExecution_t.CMD_EXEC_NOW,
                                    Str.va("recordDemo %s", command.string)
                                )
                            } else {
                                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "stoprecording")
                            }
                        }
                        frameCommandType_t.FC_AVIGAME -> {
                            if (!command.string.toString().isNullOrEmpty()) {
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
        fun FindFrameForFrameCommand(framecommand: frameCommandType_t, command: Array<frameCommand_t?>?): Int {
            var frame: Int
            var index: Int
            val numframes: Int
            var end: Int
            if (0 == frameCommands.size) {
                return -1
            }
            numframes = anims[0].NumFrames()
            frame = 0
            while (frame < numframes) {
                end = frameLookup[frame].firstCommand + frameLookup[frame].num
                index = frameLookup[frame].firstCommand
                while (index < end) {
                    if (frameCommands[index].type == framecommand) {
                        if (command != null) {
                            command[0] = frameCommands[index]
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

        fun SetAnimFlags(animflags: animFlags_t) {
            flags = animFlags_t(animflags)
        }

        fun GetAnimFlags(): animFlags_t {
            return flags
        }
    }

    /*
     ==============================================================================================

     idDeclModelDef

     ==============================================================================================
     */
    class idDeclModelDef : idDecl {
        private val anims: ArrayList<idAnim> = ArrayList()
        private val channelJoints: ArrayList<ArrayList<Int>> = ArrayList<ArrayList<Int>>(Anim.ANIM_NumAnimChannels)
        private val jointParents: ArrayList<Int> = ArrayList()
        private val joints: ArrayList<jointInfo_t> = ArrayList()
        private val offset: idVec3 = idVec3()
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
                channelJoints[i] = ArrayList()
            }
        }

        constructor(def: idDeclModelDef) {
            Collections.copy(anims, def.anims)
            for (i in channelJoints.indices) {
                Collections.copy(channelJoints[i], def.channelJoints[i])
            }
            Collections.copy(jointParents, def.jointParents)
            Collections.copy(joints, def.joints)
            offset.set(def.offset)
            modelHandle = def.modelHandle
        }

        override fun DefaultDefinition(): String {
            return "{ }"
        }

        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            var i: Int
            var num: Int
            val filename = idStr()
            val extension = idStr()
            var md5joint: Int
            var md5joints: ArrayList<idMD5Joint>
            val src = idLexer()
            val token = idToken()
            val token2 = idToken()
            var jointnames: String
            var channel: Int
            var   /*jointHandle_t*/jointnum: Int
            val jointList = ArrayList<Int>()
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
                    filename.set(token2)
                    filename.ExtractFileExtension(extension)
                    if (extension.toString() != Model.MD5_MESH_EXT) {
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
                    if (modelHandle!!.IsDefaultModel()) {
                        src.Warning("Model '%s' defaulted", filename)
                        MakeDefault()
                        return false
                    }

                    // get the number of joints
                    num = modelHandle!!.NumJoints()
                    if (0 == num) {
                        src.Warning("Model '%s' has no joints", filename)
                    }

                    // set up the joint hierarchy
                    //joints.SetGranularity(1);
                    //joints.SetNum(num);
                    //jointParents.SetNum(num);
                    //channelJoints[0].SetNum(num);
                    md5joints = modelHandle!!.GetJoints()
                    md5joint = 0 //md5joints;
                    i = 0
                    while (i < num) {
                        val jointInfo_t = jointInfo_t()
                        jointInfo_t.channel = Anim.ANIMCHANNEL_ALL
                        if (i >= joints.size) {
                            joints.add(jointInfo_t)
                        } else {
                            joints[i] = jointInfo_t
                        }
                        joints[i].num = i
                        if (md5joints[md5joint].parent != null) {
                            joints[i].parentNum = md5joints.indexOf(md5joints[md5joint].parent)
                        } else {
                            joints[i].parentNum = Model.INVALID_JOINT
                        }
                        if (i >= jointParents.size) {
                            jointParents.add(joints[i].parentNum)
                        } else {
                            jointParents[i] = joints[i].parentNum
                        }
                        if (i >= channelJoints[0].size) {
                            channelJoints[0].add(i)
                        } else {
                            channelJoints[0][i] = i
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
                        if (token2.toString() == anims[i].Name() || token2.toString() == anims[i].FullName()) {
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
                        if (0 == idStr.Icmp(channelNames[i], token2.toString())) {
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
                        if (joints[jointnum].channel != Anim.ANIMCHANNEL_ALL) {
                            src.Warning(
                                "Joint '%s' assigned to multiple channels",
                                modelHandle!!.GetJointName(jointnum)
                            )
                            i++
                            continue
                        }
                        joints[jointnum].channel = channel
                        if (i >= channelJoints[channel].size) {
                            num++
                            channelJoints[channel].add(jointnum)
                        } else {
                            channelJoints[channel][num++] = jointnum
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
                channelJoints[i].clear()
            }
        }

        fun Touch() {
            if (modelHandle != null) {
                ModelManager.renderModelManager.FindModel(modelHandle!!.Name())
            }
        }

        fun GetDefaultSkin(): idDeclSkin? {
            return skin
        }

        fun GetDefaultPose(): ArrayList<idJointQuat> {
            return modelHandle!!.GetDefaultPose()
        }

        fun SetupJoints(
            numJoints: CInt,
            jointList: ArrayList<idJointMat>,
            frameBounds: idBounds,
            removeOriginOffset: Boolean
        ): kotlin.collections.ArrayList<idJointMat> {
            val num: Int
            val pose: ArrayList<idJointQuat>
            val list: kotlin.collections.ArrayList<idJointMat>
            if (null == modelHandle || modelHandle!!.IsDefaultModel()) {
//                Mem_Free16(jointList);
                for (i in jointList.indices) {
                    jointList.removeAt(i)
                }
                frameBounds.Clear()
                return arrayListOf()
            }

            // get the number of joints
            num = modelHandle!!.NumJoints()
            if (0 == num) {
                idGameLocal.Error("model '%s' has no joints", modelHandle!!.Name())
            }

            // set up initial pose for model (with no pose, model is just a jumbled mess)
            list = arrayListOf(*Array(num) { idJointMat() })
            pose = GetDefaultPose()

            // convert the joint quaternions to joint matrices
            Simd.SIMDProcessor.ConvertJointQuatsToJointMats(list, pose, joints.size)

            // check if we offset the model by the origin joint
            if (removeOriginOffset) {
                if (VELOCITY_MOVE) {
                    list[0].SetTranslation(idVec3(offset.x, offset.y + pose[0].t.y, offset.z + pose[0].t.z))
                } else {
                    list[0].SetTranslation(offset)
                }
            } else {
                list[0].SetTranslation(pose[0].t + offset)
            }

            // transform the joint hierarchy
            Simd.SIMDProcessor.TransformJoints(
                list,
                jointParents.toIntArray(),
                1,
                joints.size - 1
            )
            numJoints._val = num


            // get the bounds of the default pose
            frameBounds.set(modelHandle!!.Bounds(null))
            return list
        }

        fun ModelHandle(): idRenderModel? {
            return modelHandle
        }

        fun GetJointList(jointnames: String, jointList: ArrayList<Int>) {
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
            num = modelHandle!!.NumJoints()

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
                    jointList.remove(joint.num)
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
                        child = joints[child_i]
                        if (child.parentNum < joint.num) {
                            break
                        }
                        if (!subtract) {
                            jointList.add(child.num)
                        } else {
                            jointList.remove(child.num)
                        }
                        i++
                        child_i++
                    }
                }
            }
        }

        fun FindJoint(name: String): jointInfo_t? {
            var i: Int
            val joint: ArrayList<idMD5Joint>
            if (null == modelHandle) {
                return null
            }
            joint = modelHandle!!.GetJoints()
            i = 0
            while (i < joints.size) {
                if (TempDump.NOT(joint[i].name.Icmp(name).toDouble())) {
                    return joints[i]
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
            } else anims[index - 1]
        }

        /*
         =====================
         idDeclModelDef::GetSpecificAnim

         Gets the exact anim for the name, without randomization.
         =====================
         */
        fun GetSpecificAnim(name: String): Int {
            var i: Int

            // find a specific animation
            i = 0
            while (i < anims.size) {
                if (name.startsWith(anims[i].FullName())) {
                    return i + 1 // makes no sense, we found it at i, but we return i + 1? is this because all idList entries are shifted by 1?
                    //return i;
                }
                i++
            }

            // didn't find it
            return 0
        }

        fun GetAnim(name: String): Int {
            var i: Int
            val which: Int
            val animList = IntArray(MAX_ANIMS)
            var numAnims: Int
            val len: Int
            len = name.length
            if (len != 0 && idStr.CharIsNumeric(name[len - 1].code)) {
                // find a specific animation
                return GetSpecificAnim(name)
            }

            // find all animations with same name
            numAnims = 0
            i = 0
            while (i < anims.size) {
                if (anims[i].Name() == name) {
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

        fun HasAnim(name: String): Boolean {
            var i: Int

            // find any animations with same name
            i = 0
            while (i < anims.size) {
                if (anims[i].Name() == name) {
                    return true
                }
                i++
            }
            return false
        }

        fun GetSkin(): idDeclSkin? {
            return skin
        }

        fun GetModelName(): String {
            return if (modelHandle != null) {
                modelHandle!!.Name()
            } else {
                ""
            }
        }

        fun Joints(): ArrayList<jointInfo_t> {
            return joints
        }

        fun JointParents(): kotlin.collections.ArrayList<Int> {
            return jointParents
        }

        fun NumJoints(): Int {
            return joints.size
        }

        fun GetJoint(jointHandle: Int): jointInfo_t {
            if (jointHandle < 0 || jointHandle > joints.size) {
                idGameLocal.Error("idDeclModelDef::GetJoint : joint handle out of range")
            }
            return joints[jointHandle]
        }

        fun GetJointName(jointHandle: Int): String? {
            val joint: kotlin.collections.ArrayList<idMD5Joint>
            if (null == modelHandle) {
                return null
            }
            if (jointHandle < 0 || jointHandle > joints.size) {
                idGameLocal.Error("idDeclModelDef::GetJointName : joint handle out of range")
            }
            joint = modelHandle!!.GetJoints()
            return joint[jointHandle].name.toString()
        }

        fun NumJointsOnChannel(channel: Int): Int {
            if (channel < 0 || channel >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idDeclModelDef::NumJointsOnChannel : channel out of range")
            }
            return channelJoints[channel].size
        }

        fun GetChannelJoints(channel: Int): kotlin.collections.ArrayList<Int> {
            if (channel < 0 || channel >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idDeclModelDef::GetChannelJoints : channel out of range")
            }
            return channelJoints[channel]
        }

        fun GetVisualOffset(): idVec3 {
            return offset
        }

        private fun CopyDecl(decl: idDeclModelDef) {
            var i: Int
            FreeData()
            offset.set(decl.offset)
            modelHandle = decl.modelHandle
            skin = decl.skin

            //anims.SetNum(decl.anims.Num());
            i = 0
            while (i < decl.anims.size) {
                if (i >= anims.size) {
                    anims.add(idAnim(this, decl.anims[i]))
                } else {
                    anims[i] = idAnim(this, decl.anims[i])
                }
                i++
            }

            //joints.SetNum(decl.joints.Num());
//            memcpy(joints.Ptr(), decl.joints.Ptr(), decl.joints.Num() * sizeof(joints[0]));
            i = 0
            while (i < decl.joints.size) {
                if (i >= joints.size) {
                    joints.add(decl.joints[i])
                } else {
                    joints[i] = decl.joints[i]
                }
                i++
            }
            //jointParents.SetNum(decl.jointParents.Num());
//            memcpy(jointParents.Ptr(), decl.jointParents.Ptr(), decl.jointParents.Num() * sizeof(jointParents[0]));
            i = 0
            while (i < decl.jointParents.size) {
                if (i >= jointParents.size) {
                    jointParents.add(decl.jointParents[i])
                } else {
                    jointParents[i] = decl.jointParents[i]
                }
                i++
            }
            i = 0
            while (i < Anim.ANIM_NumAnimChannels) {
                channelJoints[i] = ArrayList(decl.channelJoints[i]) //idList's = is overloaded!
                i++
            }
        }

        private fun ParseAnim(src: idLexer, numDefaultAnims: Int): Boolean {
            var i: Int
            val len: Int
            val anim: idAnim?
            val md5anims = kotlin.collections.ArrayList<idMD5Anim>(Anim.ANIM_MaxSyncedAnims)
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
            alias.set(realname)
            i = 0
            while (i < anims.size) {
                if (anims[i].FullName().equals(realname.toString(), ignoreCase = true)) {
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
                anim = anims[i]
            } else {
                // create the alias associated with this animation
                anim = idAnim()
                anims.add(anim)
            }

            // random anims end with a number.  find the numeric suffix of the animation.
            len = alias.Length()
            i = len - 1
            while (i > 0) {
                if (!Character.isDigit(alias[i])) {
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
                md5anim.CheckModelHierarchy(modelHandle!!)
                if (numAnims > 0) {
                    // make sure it's the same length as the other anims
                    if (md5anim.Length() != md5anims[0].Length()) {
                        src.Warning(
                            "Anim '%s' does not match length of anim '%s'",
                            md5anim!!.Name(),
                            md5anims[0].Name()
                        )
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
        private var animNum: Int = 0

        //
        private var animWeights: FloatArray = FloatArray(Anim.ANIM_MaxSyncedAnims)
        private var blendDuration = 0
        var blendEndValue = 0f

        //
        private var blendStartTime = 0
        var blendStartValue = 0f
        private var cycle: Int = 0
        private var endtime = 0
        private var frame: Int = 0
        private var modelDef: idDeclModelDef? = null
        private var rate = 0f
        var starttime = 0
        private var timeOffset = 0

        constructor() {
            Reset(null)
        }

        constructor(blend: idAnimBlend) {
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

        fun Reset(_modelDef: idDeclModelDef?) {
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

        fun CallFrameCommands(ent: idEntity?, fromtime: Int, totime: Int) {
            val md5anim: idMD5Anim
            val frame1 = frameBlend_t()
            val frame2 = frameBlend_t()
            val fromFrameTime: Int
            var toFrameTime: Int
            if (!allowFrameCommands || null == ent || frame != 0 || endtime > 0 && fromtime > endtime) {
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
            md5anim = anim.MD5Anim(0)!!
            md5anim.ConvertTimeToFrame(fromFrameTime, cycle, frame1)
            md5anim.ConvertTimeToFrame(toFrameTime, cycle, frame2)
            if (fromFrameTime <= 0) {
                // make sure first frame is called
                anim.CallFrameCommands(ent, -1, frame2.frame1)
            } else {
                anim.CallFrameCommands(ent, frame1.frame1, frame2.frame1)
            }
        }

        fun SetFrame(modelDef: idDeclModelDef?, _animNum: Int, _frame: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef!!.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)!!
            if (modelDef!!.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef!!.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum
            starttime = currentTime
            endtime = -1
            cycle = -1
            animWeights[0] = 1.0f
            frame = _frame

            // a frame of 0 means it's not a single frame blend, so we set it to frame + 1
            if (frame <= 0) {
                frame = 1
            } else if (frame > _anim.NumFrames()) {
                frame = _anim.NumFrames()
            }

            // set up blend
            blendEndValue = 1.0f
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            blendStartValue = 0.0f
        }

        fun CycleAnim(modelDef: idDeclModelDef?, _animNum: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef!!.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)!!
            if (modelDef!!.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef!!.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum
            animWeights[0] = 1.0f
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

        fun PlayAnim(modelDef: idDeclModelDef?, _animNum: Int, currentTime: Int, blendTime: Int) {
            Reset(modelDef)
            if (null == modelDef) {
                return
            }
            val _anim = modelDef!!.GetAnim(_animNum) ?: return
            val md5anim = _anim.MD5Anim(0)!!
            if (modelDef!!.Joints().size != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    modelDef!!.GetModelName(),
                    md5anim.Name()
                )
                return
            }
            animNum = _animNum
            starttime = currentTime
            endtime = starttime + _anim.Length()
            cycle = 1
            animWeights[0] = 1.0f

            // set up blend
            blendEndValue = 1.0f
            blendStartTime = currentTime - 1
            blendDuration = blendTime
            blendStartValue = 0.0f
        }

        fun BlendAnim(
            currentTime: Int,
            channel: Int,
            numJoints: Int,
            blendFrame: kotlin.collections.ArrayList<idJointQuat>,
            blendWeight: CFloat,
            removeOriginOffset: Boolean,
            overrideBlend: Boolean,
            printInfo: Boolean
        ): Boolean {
            var i: Int
            var lerp: Float
            var mixWeight: Float
            var md5anim: idMD5Anim
            var ptr: kotlin.collections.ArrayList<idJointQuat>
            val frametime = frameBlend_t()
            val jointFrame: kotlin.collections.ArrayList<idJointQuat>
            val mixFrame: kotlin.collections.ArrayList<idJointQuat>
            val numAnims: Int
            val time: Int
            val anim = Anim() ?: return false
            val weight = GetWeight(currentTime)
            if (blendWeight._val > 0.0f) {
                if (endtime >= 0 && currentTime >= endtime) {
                    return false
                }
                if (0f == weight) {
                    return false
                }
                if (overrideBlend) {
                    blendWeight._val = 1.0f - weight
                }
            }
            jointFrame = if (channel == Anim.ANIMCHANNEL_ALL && 0f == blendWeight._val) {
                // we don't need a temporary buffer, so just store it directly in the blend frame
                blendFrame
            } else {
                // allocate a temporary buffer to copy the joints from
                ArrayList<idJointQuat>(numJoints)
            }
            time = AnimTime(currentTime)
            numAnims = anim.NumAnims()
            if (numAnims == 1) {
                md5anim = anim.MD5Anim(0)!!
                if (frame != 0) {
                    md5anim.GetSingleFrame(
                        frame - 1,
                        jointFrame,
                        modelDef!!.GetChannelJoints(channel).toIntArray(),
                        modelDef!!.NumJointsOnChannel(channel)
                    )
                } else {
                    md5anim.ConvertTimeToFrame(time, cycle, frametime)
                    md5anim.GetInterpolatedFrame(
                        frametime,
                        jointFrame,
                        modelDef!!.GetChannelJoints(channel).toIntArray(),
                        modelDef!!.NumJointsOnChannel(channel)
                    )
                }
            } else {
                //
                // need to mix the multipoint anim together first
                //
                // allocate a temporary buffer to copy the joints to
                mixFrame = kotlin.collections.ArrayList<idJointQuat>(numJoints)
                if (0 == frame) {
                    anim.MD5Anim(0)!!.ConvertTimeToFrame(time, cycle, frametime)
                }
                ptr = jointFrame
                mixWeight = 0.0f
                i = 0
                while (i < numAnims) {
                    if (animWeights[i] > 0.0f) {
                        mixWeight += animWeights[i]
                        lerp = animWeights[i] / mixWeight
                        md5anim = anim.MD5Anim(i)!!
                        if (frame != 0) {
                            md5anim.GetSingleFrame(
                                frame - 1,
                                ptr,
                                modelDef!!.GetChannelJoints(channel).toIntArray(),
                                modelDef!!.NumJointsOnChannel(channel)
                            )
                        } else {
                            md5anim.GetInterpolatedFrame(
                                frametime,
                                ptr,
                                modelDef!!.GetChannelJoints(channel).toIntArray(),
                                modelDef!!.NumJointsOnChannel(channel)
                            )
                        }

                        // only blend after the first anim is mixed in
                        if (ptr != jointFrame) {
                            Simd.SIMDProcessor.BlendJoints(
                                jointFrame,
                                ptr,
                                lerp,
                                modelDef!!.GetChannelJoints(channel).toIntArray(),
                                modelDef!!.NumJointsOnChannel(channel)
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
                    if (VELOCITY_MOVE) {
                        jointFrame[0].t.x = 0.0f
                    } else {
                        jointFrame[0].t.Zero()
                    }
                }
                if (anim.GetAnimFlags().anim_turn) {
                    jointFrame[0].q.set(-0.70710677f, 0.0f, 0.0f, 0.70710677f)
                }
            }
            if (0f == blendWeight._val) {
                blendWeight._val = weight
                if (channel != Anim.ANIMCHANNEL_ALL) {
                    val index = modelDef!!.GetChannelJoints(channel)
                    val num = modelDef!!.NumJointsOnChannel(channel)
                    i = 0
                    while (i < num) {
                        val j: Int = index[i]
                        blendFrame[j].t.set(jointFrame[j].t)
                        blendFrame[j].q.set(jointFrame[j].q)
                        i++
                    }
                }
            } else {
                blendWeight._val = blendWeight._val + weight
                lerp = weight / blendWeight._val
                Simd.SIMDProcessor.BlendJoints(
                    blendFrame,
                    jointFrame,
                    lerp,
                    modelDef!!.GetChannelJoints(channel).toIntArray(),
                    modelDef!!.NumJointsOnChannel(channel)
                )
            }
            if (printInfo) {
                if (frame != 0) {
                    Game_local.gameLocal.Printf(
                        "  %s: '%s', %d, %.2f%%\n",
                        channelNames[channel],
                        anim.FullName(),
                        frame,
                        weight * 100.0f
                    )
                } else {
                    Game_local.gameLocal.Printf(
                        "  %s: '%s', %.3f, %.2f%%\n",
                        channelNames[channel],
                        anim.FullName(),
                        frametime.frame1.toFloat() + frametime.backlerp,
                        weight * 100.0f
                    )
                }
            }
            return true
        }

        fun BlendOrigin(
            currentTime: Int,
            blendPos: idVec3,
            blendWeight: CFloat,
            removeOriginOffset: Boolean
        ) {
            val lerp: Float
            val animpos = idVec3()
            val pos = idVec3()
            val time: Int
            val num: Int
            var i: Int
            if (frame != 0 || endtime > 0 && currentTime > endtime) {
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
                anim.GetOrigin(animpos, i, time, cycle)
                pos.plusAssign(animpos.times(animWeights[i]))
                i++
            }
            if (0f == blendWeight._val) {
                blendPos.set(pos)
                blendWeight._val = (weight)
            } else {
                lerp = weight / (blendWeight._val + weight)
                blendPos.plusAssign(pos.minus(blendPos).times(lerp))
                blendWeight._val = (blendWeight._val + weight)
            }
        }

        fun BlendDelta(fromtime: Int, totime: Int, blendDelta: idVec3, blendWeight: CFloat) {
            val pos1 = idVec3()
            val pos2 = idVec3()
            val animpos = idVec3()
            val delta = idVec3()
            val time1: Int
            var time2: Int
            val lerp: Float
            val num: Int
            var i: Int
            if (frame != 0 || !allowMove || endtime > 0 && fromtime > endtime) {
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
                anim.GetOrigin(animpos, i, time1, cycle)
                pos1.plusAssign(animpos.times(animWeights[i]))
                anim.GetOrigin(animpos, i, time2, cycle)
                pos2.plusAssign(animpos.times(animWeights[i]))
                i++
            }
            delta.set(pos2.minus(pos1))
            if (0f == blendWeight._val) {
                blendDelta.set(delta)
                blendWeight._val = (weight)
            } else {
                lerp = weight / (blendWeight._val + weight)
                blendDelta.plusAssign(delta.minus(blendDelta).times(lerp))
                blendWeight._val = (blendWeight._val + weight)
            }
        }

        fun BlendDeltaRotation(fromtime: Int, totime: Int, blendDelta: idQuat, blendWeight: CFloat) {
            val q1 = idQuat()
            val q2 = idQuat()
            val q3 = idQuat()
            val time1: Int
            var time2: Int
            var lerp: Float
            var mixWeight: Float
            val num: Int
            var i: Int
            if (frame != 0 || !allowMove || endtime > 0 && fromtime > endtime) {
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
            q1.set(0.0f, 0.0f, 0.0f, 1.0f)
            q2.set(0.0f, 0.0f, 0.0f, 1.0f)
            mixWeight = 0.0f
            num = anim.NumAnims()
            i = 0
            while (i < num) {
                if (animWeights[i] > 0.0f) {
                    mixWeight += animWeights[i]
                    if (animWeights[i] == mixWeight) {
                        anim.GetOriginRotation(q1, i, time1, cycle)
                        anim.GetOriginRotation(q2, i, time2, cycle)
                    } else {
                        lerp = animWeights[i] / mixWeight
                        anim.GetOriginRotation(q3, i, time1, cycle)
                        q1.Slerp(q1, q3, lerp)
                        anim.GetOriginRotation(q3, i, time2, cycle)
                        q2.Slerp(q1, q3, lerp)
                    }
                }
                i++
            }
            q3.set(q1.Inverse().times(q2))
            if (0f == blendWeight._val) {
                blendDelta.set(q3)
                blendWeight._val = (weight)
            } else {
                lerp = weight / (blendWeight._val + weight)
                blendDelta.Slerp(blendDelta, q3, lerp)
                blendWeight._val = (blendWeight._val + weight)
            }
        }

        fun AddBounds(currentTime: Int, bounds: idBounds, removeOriginOffset: Boolean): Boolean {
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
                if (anim.GetBounds(b, i, time, cycle)) {
                    if (addorigin) {
                        anim.GetOrigin(pos, i, time, cycle)
                        b.TranslateSelf(pos)
                    }
                    bounds.AddBounds(b)
                }
                i++
            }
            return true
        }

        fun Save(savefile: idSaveGame) {
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
                savefile.WriteFloat(animWeights[i])
                i++
            }
            savefile.WriteInt(cycle)
            savefile.WriteInt(frame)
            savefile.WriteInt(animNum)
            savefile.WriteBool(allowMove)
            savefile.WriteBool(allowFrameCommands)
        }

        /*
         =====================
         idAnimBlend::Restore

         unarchives object from save game file
         =====================
         */
        fun Restore(savefile: idRestoreGame, modelDef: idDeclModelDef?) {
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
                animWeights[i] = savefile.ReadFloat()
                i++
            }
            cycle = savefile.ReadInt()
            frame = savefile.ReadInt()
            animNum = savefile.ReadInt()
            if (null == modelDef) {
                animNum = 0
            } else if (animNum < 0 || animNum > modelDef!!.NumAnims()) {
                Game_local.gameLocal.Warning(
                    "Anim number %d out of range for model '%s' during save game",
                    animNum,
                    modelDef!!.GetModelName()
                )
                animNum = 0
            }
            allowMove = savefile.ReadBool()
            allowFrameCommands = savefile.ReadBool()
        }

        fun AnimName(): String {
            val anim = Anim() ?: return ""
            return anim.Name()
        }

        fun AnimFullName(): String {
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
            animWeights[num] = weight
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
            if (0 == frame && endtime > 0 && currentTime >= endtime) {
                return true
            }
            return blendEndValue <= 0.0f && currentTime >= blendStartTime + blendDuration
        }

        fun FrameHasChanged(currentTime: Int): Boolean {
            // if we don't have an anim, no change
            if (0 == animNum) {
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
            return !((frame != 0 || NumFrames() == 1) && currentTime != starttime)
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
                cycle = count
                if (cycle < 0) {
                    cycle = -1
                    endtime = -1
                } else if (cycle == 0) {
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
            SetCycleCount(cycle)
        }

        fun GetPlaybackRate(): Float {
            return rate
        }

        fun SetStartTime(_startTime: Int) {
            starttime = _startTime

            // update the anim endtime
            SetCycleCount(cycle)
        }

        fun GetStartTime(): Int {
            return if (0 == animNum) {
                0
            } else starttime
        }

        fun GetEndTime(): Int {
            return if (0 == animNum) {
                0
            } else endtime
        }

        fun GetFrameNumber(currentTime: Int): Int {
            val md5anim: idMD5Anim
            val frameinfo = frameBlend_t()
            val animTime: Int
            val anim = Anim() ?: return 1
            if (frame != 0) {
                return frame
            }
            md5anim = anim.MD5Anim(0)!!
            animTime = AnimTime(currentTime)
            md5anim.ConvertTimeToFrame(animTime, cycle, frameinfo)
            return frameinfo.frame1 + 1
        }

        fun AnimTime(currentTime: Int): Int {
            var time: Int
            val length: Int
            val anim = Anim()
            return if (anim != null) {
                if (frame != 0) {
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
            if (0 == animNum) {
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
            } else modelDef!!.GetAnim(animNum)
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
        private val AFPoseBounds: idBounds
        private val AFPoseJointFrame: ArrayList<idJointQuat>
        private val AFPoseJointMods: ArrayList<idAFPoseJointMod>
        private val AFPoseJoints: ArrayList<Int>

        //
        private val channels: Array<ArrayList<idAnimBlend>> =
            Array(Anim.ANIM_NumAnimChannels) { ArrayList<idAnimBlend>(Anim.ANIM_MaxAnimsPerChannel) }
        private val jointMods: ArrayList<jointMod_t>

        //
        private var AFPoseBlendWeight = 0f
        private var AFPoseTime = 0
        private var entity: idEntity?
        private var forceUpdate: Boolean

        //
        private val frameBounds: idBounds
        private var joints: ArrayList<idJointMat>

        //
        private var lastTransformTime // mutable because the value is updated in CreateFrame
                : Int
        private var modelDef: idDeclModelDef?
        private val numJoints: CInt
        private var removeOriginOffset: Boolean

        //
        //
        private var stoppedAnimatingUpdate: Boolean
        fun  /*size_t*/Allocated(): Int {
            val   /*size_t*/size: Int
            size =
                jointMods.size + numJoints._val + AFPoseJointMods.size + AFPoseJointFrame.size + AFPoseJoints.size
            return size
        }

        /*
         =====================
         idAnimator::Save

         archives object for save game file
         =====================
         */
        fun Save(savefile: idSaveGame) {                // archives object for save game file
            var i: Int
            var j: Int
            savefile.WriteModelDef(modelDef)
            savefile.WriteObject(entity as Class.idClass)
            savefile.WriteInt(jointMods.size)
            i = 0
            while (i < jointMods.size) {
                savefile.WriteInt(jointMods[i].jointnum)
                savefile.WriteMat3(jointMods[i].mat)
                savefile.WriteVec3(jointMods[i].pos)
                savefile.WriteInt(TempDump.etoi(jointMods[i].transform_pos))
                savefile.WriteInt(TempDump.etoi(jointMods[i].transform_axis))
                i++
            }
            savefile.WriteInt(numJoints._val)
            i = 0
            while (i < numJoints._val) {
                val data = joints[i].ToFloatPtr()
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
                savefile.WriteInt(AFPoseJoints[i])
                i++
            }
            savefile.WriteInt(AFPoseJointMods.size)
            i = 0
            while (i < AFPoseJointMods.size) {
                savefile.WriteInt(TempDump.etoi(AFPoseJointMods[i].mod))
                savefile.WriteMat3(AFPoseJointMods[i].axis)
                savefile.WriteVec3(AFPoseJointMods[i].origin)
                i++
            }
            savefile.WriteInt(AFPoseJointFrame.size)
            i = 0
            while (i < AFPoseJointFrame.size) {
                savefile.WriteFloat(AFPoseJointFrame[i].q.x)
                savefile.WriteFloat(AFPoseJointFrame[i].q.y)
                savefile.WriteFloat(AFPoseJointFrame[i].q.z)
                savefile.WriteFloat(AFPoseJointFrame[i].q.w)
                savefile.WriteVec3(AFPoseJointFrame[i].t)
                i++
            }
            savefile.WriteBounds(AFPoseBounds)
            savefile.WriteInt(AFPoseTime)
            savefile.WriteBool(removeOriginOffset)
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels[i][j].Save(savefile)
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
        fun Restore(savefile: idRestoreGame) {                    // unarchives object from save game file
            var i: Int
            var j: Int
            val num = CInt()
            savefile.ReadModelDef(modelDef!!)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/entity)
            savefile.ReadInt(num)
            //jointMods.SetNum(num._val);
            i = 0
            while (i < num._val) {
                if (i >= jointMods.size) {
                    jointMods.add(jointMod_t())
                } else {
                    jointMods[i] = jointMod_t()
                }
                jointMods[i].jointnum = savefile.ReadInt()
                savefile.ReadMat3(jointMods[i].mat)
                savefile.ReadVec3(jointMods[i].pos)
                jointMods[i].transform_pos = jointModTransform_t.values()[savefile.ReadInt()]
                jointMods[i].transform_axis = jointModTransform_t.values()[savefile.ReadInt()]
                i++
            }
            numJoints._val = (savefile.ReadInt())
            joints = ArrayList<idJointMat>(numJoints._val)
            i = 0
            while (i < numJoints._val) {
                val data = joints[i].ToFloatPtr()
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
            //AFPoseJoints.SetNum(num._val);
            i = 0
            while (i < num._val) {
                if (i >= AFPoseJoints.size) {
                    AFPoseJoints.add(savefile.ReadInt())
                } else {
                    AFPoseJoints[i] = savefile.ReadInt()
                }
                i++
            }
            savefile.ReadInt(num)
            //AFPoseJointMods.SetGranularity(1);
            //AFPoseJointMods.SetNum(num._val);
            i = 0
            while (i < num._val) {
                AFPoseJointMods[i].mod = AFJointModType_t.values()[savefile.ReadInt()]
                savefile.ReadMat3(AFPoseJointMods[i].axis)
                savefile.ReadVec3(AFPoseJointMods[i].origin)
                i++
            }
            savefile.ReadInt(num)
            //AFPoseJointFrame.SetGranularity(1);
            //AFPoseJointFrame.SetNum(num._val);
            i = 0
            while (i < num._val) {
                AFPoseJointFrame[i].q.x = savefile.ReadFloat()
                AFPoseJointFrame[i].q.y = savefile.ReadFloat()
                AFPoseJointFrame[i].q.z = savefile.ReadFloat()
                AFPoseJointFrame[i].q.w = savefile.ReadFloat()
                savefile.ReadVec3(AFPoseJointFrame[i].t)
                i++
            }
            savefile.ReadBounds(AFPoseBounds)
            AFPoseTime = savefile.ReadInt()
            removeOriginOffset = savefile.ReadBool()
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels[i][j].Restore(savefile, modelDef)
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

        fun GetJointList(jointnames: String, jointList: ArrayList<Int>) {
            if (modelDef != null) {
                modelDef!!.GetJointList(jointnames, jointList)
            }
        }

        fun GetJointList(jointnames: idStr, jointList: ArrayList<Int>) {
            GetJointList(jointnames.toString(), jointList)
        }

        fun NumAnims(): Int {
            return if (null == modelDef) {
                0
            } else modelDef!!.NumAnims()
        }

        fun GetAnim(index: Int): idAnim? {
            return if (null == modelDef) {
                null
            } else modelDef!!.GetAnim(index)
        }

        fun GetAnim(name: String): Int {
            return if (null == modelDef) {
                0
            } else modelDef!!.GetAnim(name)
        }

        fun HasAnim(name: String): Boolean {
            return if (null == modelDef) {
                false
            } else modelDef!!.HasAnim(name)
        }

        fun HasAnim(name: idStr): Boolean {
            return HasAnim(name.toString())
        }

        fun ServiceAnims(fromtime: Int, totime: Int) {
            var i: Int
            var j: Int
            val blend: Array<kotlin.collections.ArrayList<idAnimBlend>>
            if (null == modelDef) {
                return
            }
            if (modelDef!!.ModelHandle() != null) {
                blend = channels
                i = 0
                while (i < Anim.ANIM_NumAnimChannels) {
                    j = 0
                    while (j < Anim.ANIM_MaxAnimsPerChannel) {
                        blend[i][j].CallFrameCommands(entity, fromtime, totime)
                        j++
                    }
                    i++
                }
            }
            if (!IsAnimating(totime)) {
                stoppedAnimatingUpdate = true
                if (entity != null) {
                    entity!!.BecomeInactive(Entity.TH_ANIMATE)

                    // present one more time with stopped animations so the renderer can properly recreate interactions
                    entity!!.BecomeActive(Entity.TH_UPDATEVISUALS)
                }
            }
        }

        fun IsAnimating(currentTime: Int): Boolean {
            var i: Int
            var j: Int
            val blend: Array<kotlin.collections.ArrayList<idAnimBlend>>
            if (null == modelDef || TempDump.NOT(modelDef!!.ModelHandle())) {
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
                    if (!blend[i][j].IsDone(currentTime)) {
                        return true
                    }
                    j++
                }
                i++
            }
            return false
        }

        fun GetJoints(renderEntity: renderEntity_s): Int {
            renderEntity.joints = joints
            return numJoints._val
        }

        fun NumJoints(): Int {
            return numJoints._val
        }

        fun  /*jointHandle_t*/GetFirstChild(   /*jointHandle_t*/jointnum: Int): Int {
            var i: Int
            val num: Int
            var joint: jointInfo_t
            if (null == modelDef) {
                return Model.INVALID_JOINT
            }
            num = modelDef!!.NumJoints()
            if (0 == num) {
                return jointnum
            }
            joint = modelDef!!.GetJoint(0)
            i = 0
            while (i < num) {
                if (joint.parentNum == jointnum) {
                    return joint.num
                }
                joint = modelDef!!.GetJoint(++i)
            }
            return jointnum
        }

        fun  /*jointHandle_t*/GetFirstChild(name: String): Int {
            return GetFirstChild(GetJointHandle(name))
        }

        fun SetModel(modelname: String): idRenderModel? {
            var i: Int
            var j: Int
            //int[] numJoints = {0};
            FreeData()

            // check if we're just clearing the model
            if (!TempDump.isNotNullOrEmpty(modelname)) {
                return null
            }
            modelDef = DeclManager.declManager.FindType(declType_t.DECL_MODELDEF, modelname, false) as idDeclModelDef
            if (null == modelDef) {
                return null
            }
            val renderModel = modelDef!!.ModelHandle()
            if (null == renderModel) {
                modelDef = null
                return null
            }

            // make sure model hasn't been purged
            modelDef!!.Touch()
            joints = modelDef!!.SetupJoints(numJoints, joints, frameBounds, removeOriginOffset)
            modelDef!!.ModelHandle()!!.Reset()

            // set the modelDef on all channels
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels[i][j].Reset(modelDef)
                    j++
                }
                i++
            }
            return modelDef!!.ModelHandle()
        }

        fun ModelHandle(): idRenderModel? {
            return if (null == modelDef) {
                null
            } else modelDef!!.ModelHandle()
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
            var blend: kotlin.collections.ArrayList<idAnimBlend>
            val jointParent: kotlin.collections.ArrayList<Int>
            var jointMod: jointMod_t?
            val defaultPose: ArrayList<idJointQuat>
            if (Game_local.gameLocal.inCinematic && Game_local.gameLocal.skipCinematic) {
                return false
            }
            if (null == modelDef || null == modelDef!!.ModelHandle()) {
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
            //numJoints = modelDef!!.Joints().size();
            if (entity != null && (SysCvar.g_debugAnim.GetInteger() == entity!!.entityNumber || SysCvar.g_debugAnim.GetInteger() == -2)) {
                debugInfo = true
                Game_local.gameLocal.Printf(
                    "---------------\n%d: entity '%s':\n",
                    Game_local.gameLocal.time,
                    entity!!.GetName()
                )
                Game_local.gameLocal.Printf("model '%s':\n", modelDef!!.GetModelName())
            } else {
                debugInfo = false
            }

            // init the joint buffer
            if (AFPoseJoints.size != 0) {
                // initialize with AF pose anim for the case where there are no other animations and no AF pose joint modifications
                defaultPose = AFPoseJointFrame
            } else {
                defaultPose = modelDef!!.GetDefaultPose()
            }
            if (null == defaultPose) {
                //gameLocal.Warning( "idAnimator::CreateFrame: no defaultPose on '%s'", modelDef!!.Name() );
                return false
            }
            numJoints = modelDef!!.Joints().size
            val jointFrame = kotlin.collections.ArrayList<idJointQuat>(numJoints)
            //SIMDProcessor.Memcpy(jointFrame, defaultPose, numJoints /* sizeof( jointFrame[0] )*/);
            for (index in 0 until numJoints) {
                jointFrame[index] = defaultPose[index]
            }
            hasAnim = false

            // blend the all channel
            baseBlend._val = (0.0f)
            blend = channels[Anim.ANIMCHANNEL_ALL]
            j = Anim.ANIMCHANNEL_ALL
            while (j < Anim.ANIM_MaxAnimsPerChannel) {
                if (blend[j].BlendAnim(
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
                    if (baseBlend._val >= 1.0f) {
                        break
                    }
                }
                j++
            }

            // only blend other channels if there's enough space to blend into
            if (baseBlend._val < 1.0f) {
                i = Anim.ANIMCHANNEL_ALL + 1
                while (i < Anim.ANIM_NumAnimChannels) {
                    if (0 == modelDef!!.NumJointsOnChannel(i)) {
                        i++
                        continue
                    }
                    if (i == Anim.ANIMCHANNEL_EYELIDS) {
                        // eyelids blend over any previous anims, so skip it and blend it later
                        i++
                        continue
                    }
                    blendWeight._val = (baseBlend._val)
                    blend = channels[i]
                    j = 0
                    while (j < Anim.ANIM_MaxAnimsPerChannel) {
                        if (blend[j].BlendAnim(
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
                            if (blendWeight._val >= 1.0f) {
                                // fully blended
                                break
                            }
                        }
                        j++
                    }
                    if (debugInfo && 0 == AFPoseJoints.size && 0f == blendWeight._val) {
                        Game_local.gameLocal.Printf(
                            "%d: %s using default pose in model '%s'\n",
                            Game_local.gameLocal.time,
                            channelNames[i],
                            modelDef!!.GetModelName()
                        )
                    }
                    i++
                }
            }

            // blend in the eyelids
            if (modelDef!!.NumJointsOnChannel(Anim.ANIMCHANNEL_EYELIDS) != 0) {
                blend = channels[Anim.ANIMCHANNEL_EYELIDS]
                blendWeight._val = (baseBlend._val)
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (blend[j].BlendAnim(
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
                        if (blendWeight._val >= 1.0f) {
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
            if (jointMods.size != 0 && jointMods[0].jointnum == 0) {
                jointMod = jointMods[0]
                when (jointMod.transform_axis) {
                    jointModTransform_t.JOINTMOD_NONE -> {}
                    jointModTransform_t.JOINTMOD_LOCAL -> joints[0]
                        .SetRotation(jointMod.mat.times(joints[0].ToMat3()))
                    jointModTransform_t.JOINTMOD_WORLD -> joints[0]
                        .SetRotation(joints[0].ToMat3().times(jointMod.mat))
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints[0].SetRotation(
                        jointMod.mat
                    )
                }
                when (jointMod.transform_pos) {
                    jointModTransform_t.JOINTMOD_NONE -> {}
                    jointModTransform_t.JOINTMOD_LOCAL -> joints[0]
                        .SetTranslation(joints[0].ToVec3().plus(jointMod.pos))
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE, jointModTransform_t.JOINTMOD_WORLD, jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints[0].SetTranslation(
                        jointMod.pos
                    )
                }
                j = 1
            } else {
                j = 0
            }

            // add in the model offset
            joints[0].SetTranslation(joints[0].ToVec3().plus(modelDef!!.GetVisualOffset()))

            // pointer to joint info
            jointParent = modelDef!!.JointParents()

            // add in any joint modifications
            i = 1
            while (j < jointMods.size) {
                jointMod = jointMods[j]

                // transform any joints preceding the joint modifier
                Simd.SIMDProcessor.TransformJoints(joints, jointParent.toIntArray(), i, jointMod.jointnum - 1)
                i = jointMod.jointnum
                parentNum = jointParent!![i]
                when (jointMod.transform_axis) {
                    jointModTransform_t.JOINTMOD_NONE -> joints[i]
                        .SetRotation(joints[i].ToMat3().times(joints[parentNum].ToMat3()))
                    jointModTransform_t.JOINTMOD_LOCAL -> joints[i].SetRotation(
                        jointMod.mat.times(
                            joints[i].ToMat3().times(joints[parentNum].ToMat3())
                        )
                    )
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE -> joints[i]
                        .SetRotation(jointMod.mat.times(joints[parentNum].ToMat3()))
                    jointModTransform_t.JOINTMOD_WORLD -> joints[i].SetRotation(
                        joints[i].ToMat3().times(joints[parentNum].ToMat3()).times(jointMod.mat)
                    )
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints[i].SetRotation(jointMod.mat)
                }
                when (jointMod.transform_pos) {
                    jointModTransform_t.JOINTMOD_NONE -> joints[i].SetTranslation(
                        joints[parentNum].ToVec3()
                            .plus(joints[i].ToVec3().times(joints[parentNum].ToMat3()))
                    )
                    jointModTransform_t.JOINTMOD_LOCAL -> joints[i].SetTranslation(
                        joints[parentNum].ToVec3().plus(joints[i].ToVec3().plus(jointMod.pos))
                            .times(joints[parentNum].ToMat3())
                    )
                    jointModTransform_t.JOINTMOD_LOCAL_OVERRIDE -> joints[i].SetTranslation(
                        joints[parentNum].ToVec3().plus(jointMod.pos.times(joints[parentNum].ToMat3()))
                    )
                    jointModTransform_t.JOINTMOD_WORLD ->                         //joints[i].SetTranslation(joints[parentNum].ToVec3().plus(joints[i].ToVec3().times(joints[parentNum].ToMat3())).plus(jointMod.pos));
                        joints[i].SetTranslation(
                            joints[parentNum].ToVec3().plus(joints[i].ToVec3())
                                .times(joints[parentNum].ToMat3()).plus(jointMod.pos)
                        )
                    jointModTransform_t.JOINTMOD_WORLD_OVERRIDE -> joints[i].SetTranslation(jointMod.pos)
                }
                j++
                i++
            }

            // transform the rest of the hierarchy
            Simd.SIMDProcessor.TransformJoints(joints, jointParent.toIntArray(), i, numJoints - 1)
            return true
        }

        fun FrameHasChanged(currentTime: Int): Boolean {
            var i: Int
            var j: Int
            val blend: Array<kotlin.collections.ArrayList<idAnimBlend>>
            if (null == modelDef || null == modelDef!!.ModelHandle()) {
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
                    if (blend[i][j].FrameHasChanged(currentTime)) {
                        return true
                    }
                    j++
                }
                i++
            }
            return forceUpdate && IsAnimating(currentTime)
        }

        fun GetDelta(fromtime: Int, totime: Int, delta: idVec3) {
            var i: Int
            var blend: kotlin.collections.ArrayList<idAnimBlend>
            val blendWeight = CFloat()
            if (null == modelDef || null == modelDef!!.ModelHandle() || fromtime == totime) {
                delta.Zero()
                return
            }
            delta.Zero()
            blendWeight._val = (0.0f)
            blend = channels[Anim.ANIMCHANNEL_ALL]
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend[i].BlendDelta(fromtime, totime, delta, blendWeight)
                i++
            }
            if (modelDef!!.Joints()[0].channel != 0) {
                val c = modelDef!!.Joints()[0].channel
                blend = channels[c]
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend[i].BlendDelta(fromtime, totime, delta, blendWeight)
                    i++
                }
            }
        }

        fun GetDeltaRotation(fromtime: Int, totime: Int, delta: idMat3): Boolean {
            var i: Int
            var blend: kotlin.collections.ArrayList<idAnimBlend>
            val blendWeight = CFloat()
            val q = idQuat(0.0f, 0.0f, 0.0f, 1.0f)
            if (null == modelDef || null == modelDef!!.ModelHandle() || fromtime == totime) {
                delta.Identity()
                return false
            }
            blendWeight._val = (0.0f)
            blend = channels[Anim.ANIMCHANNEL_ALL]
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend[i].BlendDeltaRotation(fromtime, totime, q, blendWeight)
                i++
            }
            if (modelDef!!.Joints()[0].channel != 0) {
                val c = modelDef!!.Joints()[0].channel
                blend = channels[c]
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend[i].BlendDeltaRotation(fromtime, totime, q, blendWeight)
                    i++
                }
            }
            return if (blendWeight._val > 0.0f) {
                delta.set(q.ToMat3())
                true
            } else {
                delta.Identity()
                false
            }
        }

        fun GetOrigin(currentTime: Int, pos: idVec3) {
            var i: Int
            var blend: kotlin.collections.ArrayList<idAnimBlend>
            val blendWeight = CFloat()
            if (null == modelDef || null == modelDef!!.ModelHandle()) {
                pos.Zero()
                return
            }
            pos.Zero()
            blendWeight._val = (0.0f)
            blend = channels[Anim.ANIMCHANNEL_ALL]
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend[i].BlendOrigin(currentTime, pos, blendWeight, removeOriginOffset)
                i++
            }
            if (modelDef!!.Joints()[0].channel != 0) {
                val k = modelDef!!.Joints()[0].channel
                blend = channels[k]
                i = 0
                while (i < Anim.ANIM_MaxAnimsPerChannel) {
                    blend[i].BlendOrigin(currentTime, pos, blendWeight, removeOriginOffset)
                    i++
                }
            }
            pos.plusAssign(modelDef!!.GetVisualOffset())
        }

        fun GetBounds(currentTime: Int, bounds: idBounds): Boolean {
            var i: Int
            var j: Int
            var blend: Array<idAnimBlend?>
            var count: Int
            if (null == modelDef || null == modelDef!!.ModelHandle()) {
                return false
            }
            count = if (AFPoseJoints.size != 0) {
                bounds.set(AFPoseBounds)
                1
            } else {
                bounds.Clear()
                0
            }
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    if (channels[i][j].AddBounds(currentTime, bounds, removeOriginOffset)) {
                        count++
                    }
                    j++
                }
                i++
            }
            if (0 == count) {
                return if (!frameBounds.IsCleared()) {
                    bounds.set(frameBounds)
                    true
                } else {
                    bounds.Zero()
                    false
                }
            }
            bounds.TranslateSelf(modelDef!!.GetVisualOffset())
            if (SysCvar.g_debugBounds.GetBool()) {
                if (bounds[1, 0] - bounds[0, 0] > 2048 || bounds[1, 1] - bounds[0, 1] > 2048) {
                    if (entity != null) {
                        Game_local.gameLocal.Warning(
                            "big frameBounds on entity '%s' with model '%s': %f,%f",
                            entity!!.name,
                            modelDef!!.ModelHandle()!!.Name(),
                            bounds[1, 0] - bounds[0, 0],
                            bounds[1, 1] - bounds[0, 1]
                        )
                    } else {
                        Game_local.gameLocal.Warning(
                            "big frameBounds on model '%s': %f,%f",
                            modelDef!!.ModelHandle()!!.Name(),
                            bounds[1, 0] - bounds[0, 0],
                            bounds[1, 1] - bounds[0, 1]
                        )
                    }
                }
            }
            frameBounds.set(bounds)
            return true
        }

        fun CurrentAnim(channelNum: Int): idAnimBlend {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::CurrentAnim : channel out of range")
            }
            return channels[channelNum][0]
        }

        fun Clear(channelNum: Int, currentTime: Int, cleartime: Int) {
            var i: Int
            val blend: kotlin.collections.ArrayList<idAnimBlend>
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::Clear : channel out of range")
            }
            blend = channels[channelNum]
            i = 0
            while (i < Anim.ANIM_MaxAnimsPerChannel) {
                blend[i].Clear(currentTime, cleartime)
                i++
            }
            ForceUpdate()
        }

        fun SetFrame(channelNum: Int, animNum: Int, frame: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::SetFrame : channel out of range")
            }
            if (null == modelDef || null == modelDef!!.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels[channelNum][0].SetFrame(modelDef, animNum, frame, currentTime, blendTime)
            if (entity != null) {
                entity!!.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        fun CycleAnim(channelNum: Int, animNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::CycleAnim : channel out of range")
            }
            if (null == modelDef || null == modelDef!!.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels[channelNum][0].CycleAnim(modelDef, animNum, currentTime, blendTime)
            if (entity != null) {
                entity!!.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        fun PlayAnim(channelNum: Int, animNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::PlayAnim : channel out of range")
            }
            if (null == modelDef || null == modelDef!!.GetAnim(animNum)) {
                return
            }
            PushAnims(channelNum, currentTime, blendTime)
            channels[channelNum][0].PlayAnim(modelDef, animNum, currentTime, blendTime)
            if (entity != null) {
                entity!!.BecomeActive(Entity.TH_ANIMATE)
            }
        }

        // copies the current anim from fromChannelNum to channelNum.
        // the copied anim will have frame commands disabled to avoid executing them twice.
        fun SyncAnimChannels(channelNum: Int, fromChannelNum: Int, currentTime: Int, blendTime: Int) {
            if (channelNum < 0 || channelNum >= Anim.ANIM_NumAnimChannels || fromChannelNum < 0 || fromChannelNum >= Anim.ANIM_NumAnimChannels) {
                idGameLocal.Error("idAnimator::SyncToChannel : channel out of range")
            }
            val fromBlend = idAnimBlend(channels[fromChannelNum][0])
            var toBlend = idAnimBlend(channels[channelNum][0])
            val weight = fromBlend.blendEndValue
            if (fromBlend.Anim() !== toBlend.Anim() || fromBlend.GetStartTime() != toBlend.GetStartTime() || fromBlend.GetEndTime() != toBlend.GetEndTime()) {
                PushAnims(channelNum, currentTime, blendTime)
                Simd.SIMDProcessor.Memcpy(
                    channels[channelNum],
                    channels[fromChannelNum],
                    Anim.ANIM_MaxAnimsPerChannel
                )
                toBlend = fromBlend
                toBlend.blendStartValue = 0.0f
                toBlend.blendEndValue = 0.0f
            }
            toBlend.SetWeight(weight, currentTime - 1, blendTime)

            // disable framecommands on the current channel so that commands aren't called twice
            toBlend.AllowFrameCommands(false)
            entity?.BecomeActive(Entity.TH_ANIMATE)
        }

        fun SetJointPos(   /*jointHandle_t*/jointnum: Int, transform_type: jointModTransform_t, pos: idVec3) {
            var i: Int
            var jointMod: jointMod_t?
            if (null == modelDef || null == modelDef!!.ModelHandle() || jointnum < 0 || jointnum >= numJoints._val) {
                return
            }
            jointMod = null
            i = 0
            while (i < jointMods.size) {
                if (jointMods[i].jointnum == jointnum) {
                    jointMod = jointMods[i]
                    break
                } else if (jointMods[i].jointnum > jointnum) {
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
                    jointMods[i] = jointMod
                }
            }
            jointMod.pos.set(pos)
            jointMod.transform_pos = transform_type
            entity?.BecomeActive(Entity.TH_ANIMATE)
            ForceUpdate()
        }

        fun SetJointAxis(   /*jointHandle_t*/jointnum: Int, transform_type: jointModTransform_t, mat: idMat3) {
            var i: Int
            var jointMod: jointMod_t?
            if (null == modelDef || null == modelDef!!.ModelHandle() || jointnum < 0 || jointnum >= numJoints._val) {
                return
            }
            jointMod = null
            i = 0
            while (i < jointMods.size) {
                if (jointMods[i].jointnum == jointnum) {
                    jointMod = jointMods[i]
                    break
                } else if (jointMods[i].jointnum > jointnum) {
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
                    jointMods[i] = jointMod
                }
            }
            jointMod.mat.set(mat)
            jointMod.transform_axis = transform_type
            entity?.BecomeActive(Entity.TH_ANIMATE)
            ForceUpdate()
        }

        fun ClearJoint(   /*jointHandle_t*/jointnum: Int) {
            var i: Int
            if (null == modelDef || null == modelDef!!.ModelHandle() || jointnum < 0 || jointnum >= numJoints._val) {
                return
            }
            i = 0
            while (i < jointMods.size) {
                if (jointMods[i].jointnum == jointnum) {
//			delete jointMods[ i ];
                    jointMods.removeAt(i)
                    ForceUpdate()
                    break
                } else if (jointMods[i].jointnum > jointnum) {
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

            //AFPoseJoints.SetNum(modelDef!!.Joints().size(), false);
            //AFPoseJoints.SetNum(0, false);
//            AFPoseJoints.clear();
//            AFPoseJoints.trimToSize();
//            AFPoseJoints.addAll(new ArrayList<>(modelDef!!.Joints().size()));
//            //AFPoseJointFrame.clear();
////            for (int i = 0; i < modelDef!!.Joints().size(); i++) {
////                AFPoseJoints.add(i, -1);
////                AFPoseJointFrame.add(i, new idJointQuat());
////            }
//            AFPoseJointFrame.clear();
//            AFPoseJointFrame.trimToSize();
//            AFPoseJointFrame.addAll(new ArrayList<>(modelDef!!.Joints().size()));
            //AFPoseJointMods.SetNum(modelDef!!.Joints().size(), false);
            //AFPoseJointFrame.SetNum(modelDef!!.Joints().size(), false);
        }

        fun SetAFPoseJointMod(   /*jointHandle_t*/jointNum: Int,
                                                  mod: AFJointModType_t,
                                                  axis: idMat3,
                                                  origin: idVec3
        ) {
            if (jointNum >= AFPoseJointMods.size) {
                for (i in AFPoseJointMods.size..jointNum) {
                    AFPoseJointMods.add(i, idAFPoseJointMod())
                }
                AFPoseJointMods.add(jointNum, idAFPoseJointMod())
            } else {
                AFPoseJointMods[jointNum] = idAFPoseJointMod()
            }
            AFPoseJointMods[jointNum].mod = mod
            AFPoseJointMods[jointNum].axis.set(axis)
            AFPoseJointMods[jointNum].origin.set(origin)
            val index =
                BinSearch.idBinSearch_GreaterEqual<Any?>(AFPoseJoints.toTypedArray(), AFPoseJoints.size, jointNum)
            if (index >= AFPoseJoints.size || jointNum != AFPoseJoints[index]) {
                if (index >= AFPoseJoints.size) {
                    AFPoseJoints.add(index)
                } else {
                    AFPoseJoints[jointNum] = index
                }
            }
        }

        fun FinishAFPose(animNum: Int, bounds: idBounds, time: Int) {
            var i: Int
            var j: Int
            val numJoints: Int
            var parentNum: Int
            var jointMod: Int
            var jointNum: Int
            val jointParent: kotlin.collections.ArrayList<Int>
            if (null == modelDef) {
                return
            }
            val anim = modelDef!!.GetAnim(animNum) ?: return
            numJoints = modelDef!!.Joints().size
            if (0 == numJoints) {
                return
            }
            val md5 = modelDef!!.ModelHandle()
            val md5anim = anim.MD5Anim(0)!!
            if (numJoints != md5anim.NumJoints()) {
                Game_local.gameLocal.Warning(
                    "Model '%s' has different # of joints than anim '%s'",
                    md5!!.Name(),
                    md5anim.Name()
                )
                return
            }
            val jointFrame = ArrayList<idJointQuat>(numJoints)
            md5anim.GetSingleFrame(
                0,
                jointFrame,
                modelDef!!.GetChannelJoints(Anim.ANIMCHANNEL_ALL).toIntArray(),
                modelDef!!.NumJointsOnChannel(Anim.ANIMCHANNEL_ALL)
            )
            if (removeOriginOffset) {
                if (VELOCITY_MOVE) {
                    jointFrame[0].t.x = 0.0f
                } else {
                    jointFrame[0].t.Zero()
                }
            }
            val joints = arrayListOf<idJointMat>(* Array(numJoints) { idJointMat() })

            // convert the joint quaternions to joint matrices
            Simd.SIMDProcessor.ConvertJointQuatsToJointMats(joints, jointFrame, numJoints)

            // first joint is always root of entire hierarchy
            j = if (AFPoseJoints.size != 0 && AFPoseJoints[0] == 0) {
                when (AFPoseJointMods[0].mod) {
                    AFJointModType_t.AF_JOINTMOD_AXIS -> {
                        joints[0].SetRotation(AFPoseJointMods[0].axis)
                    }
                    AFJointModType_t.AF_JOINTMOD_ORIGIN -> {
                        joints[0].SetTranslation(AFPoseJointMods[0].origin)
                    }
                    AFJointModType_t.AF_JOINTMOD_BOTH -> {
                        joints[0].SetRotation(AFPoseJointMods[0].axis)
                        joints[0].SetTranslation(AFPoseJointMods[0].origin)
                    }
                }
                1
            } else {
                0
            }

            // pointer to joint info
            jointParent = modelDef!!.JointParents()

            // transform the child joints
            i = 1
            while (j < AFPoseJoints.size) {
                jointMod = AFPoseJoints[j]

                // transform any joints preceding the joint modifier
                Simd.SIMDProcessor.TransformJoints(joints, jointParent.toIntArray(), i, jointMod - 1)
                i = jointMod
                parentNum = jointParent!![i]
                when (AFPoseJointMods[jointMod].mod) {
                    AFJointModType_t.AF_JOINTMOD_AXIS -> {
                        joints[i].SetRotation(AFPoseJointMods[jointMod].axis)
                        joints[i].SetTranslation(
                            joints[parentNum].ToVec3().plus(joints[i].ToVec3().times(joints[parentNum].ToMat3()))
                        )
                    }
                    AFJointModType_t.AF_JOINTMOD_ORIGIN -> {
                        joints[i].SetRotation(joints[i].ToMat3().times(joints[parentNum].ToMat3()))
                        joints[i].SetTranslation(AFPoseJointMods[jointMod].origin)
                    }
                    AFJointModType_t.AF_JOINTMOD_BOTH -> {
                        joints[i].SetRotation(AFPoseJointMods[jointMod].axis)
                        joints[i].SetTranslation(AFPoseJointMods[jointMod].origin)
                    }
                }
                j++
                i++
            }

            // transform the rest of the hierarchy
            Simd.SIMDProcessor.TransformJoints(joints, jointParent.toIntArray(), i, numJoints - 1)

            // untransform hierarchy
            Simd.SIMDProcessor.UntransformJoints(joints, jointParent.toIntArray(), 1, numJoints - 1)

            // convert joint matrices back to joint quaternions
            Simd.SIMDProcessor.ConvertJointMatsToJointQuats(AFPoseJointFrame, joints, numJoints)

            // find all modified joints and their parents
            val blendJoints = BooleanArray(numJoints) //memset( blendJoints, 0, numJoints * sizeof( bool ) );

            // mark all modified joints and their parents
            i = 0
            while (i < AFPoseJoints.size) {
                jointNum = AFPoseJoints[i]
                while (jointNum != Model.INVALID_JOINT) {
                    blendJoints[jointNum] = true
                    jointNum = jointParent!![jointNum]
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
            AFPoseBounds.set(bounds)
            AFPoseTime = time
            ForceUpdate()
        }

        fun SetAFPoseBlendWeight(blendWeight: Float) {
            AFPoseBlendWeight = blendWeight
        }

        fun BlendAFPose(blendFrame: kotlin.collections.ArrayList<idJointQuat>): Boolean {
            if (0 == AFPoseJoints.size) {
                return false
            }
            Simd.SIMDProcessor.BlendJoints(
                blendFrame,
                AFPoseJointFrame,
                AFPoseBlendWeight,
                AFPoseJoints.toIntArray(),
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

        fun  /*jointHandle_t*/GetJointHandle(name: String): Int {
            return if (null == modelDef || null == modelDef!!.ModelHandle()) {
                Model.INVALID_JOINT
            } else modelDef!!.ModelHandle()!!.GetJointHandle(name)
        }

        fun  /*jointHandle_t*/GetJointHandle(name: idStr): Int {
            return GetJointHandle(name.toString())
        }

        fun GetJointName(   /*jointHandle_t*/handle: Int): String {
            return if (null == modelDef || null == modelDef!!.ModelHandle()) {
                ""
            } else modelDef!!.ModelHandle()!!.GetJointName(handle)
        }

        fun GetChannelForJoint(   /*jointHandle_t*/joint: Int): Int {
            if (null == modelDef) {
                idGameLocal.Error("idAnimator::GetChannelForJoint: NULL model")
            }
            if (joint < 0 || joint >= numJoints._val) {
                idGameLocal.Error("idAnimator::GetChannelForJoint: invalid joint num (%d)", joint)
            }
            return modelDef!!.GetJoint(joint).channel
        }

        fun GetJointTransform(   /*jointHandle_t*/jointHandle: Int,
                                                  currentTime: Int,
                                                  offset: idVec3,
                                                  axis: idMat3
        ): Boolean {
            if (null == modelDef || jointHandle < 0 || jointHandle >= modelDef!!.NumJoints()) {
                return false
            }
            CreateFrame(currentTime, false)
            offset.set(joints[jointHandle].ToVec3())
            axis.set(joints[jointHandle].ToMat3())
            return true
        }

        fun GetJointLocalTransform(   /*jointHandle_t*/jointHandle: Int,
                                                       currentTime: Int,
                                                       offset: idVec3,
                                                       axis: idMat3
        ): Boolean {
            if (null == modelDef) {
                return false
            }
            val modelJoints = modelDef!!.Joints()
            if (jointHandle < 0 || jointHandle >= modelJoints.size) {
                return false
            }

            // FIXME: overkill
            CreateFrame(currentTime, false)
            if (jointHandle == 0) {
                offset.set(joints[jointHandle].ToVec3())
                axis.set(joints[jointHandle].ToMat3())
                return true
            }
            val m = idJointMat(joints[jointHandle])
            m.oDivSet(joints[modelJoints[jointHandle].parentNum])
            offset.set(m.ToVec3())
            axis.set(m.ToMat3())
            return true
        }

        fun GetAnimFlags(animNum: Int): animFlags_t {
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

        fun AnimName(animNum: Int): String {
            val anim = GetAnim(animNum)
            return if (anim != null) {
                anim.Name()
            } else {
                ""
            }
        }

        fun AnimFullName(animNum: Int): String {
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

        fun TotalMovementDelta(animNum: Int): idVec3 {
            val anim = GetAnim(animNum)
            return if (anim != null) {
                anim.TotalMovementDelta()
            } else {
                getVec3_origin()
            }
        }

        private fun FreeData() {
            var i: Int
            var j: Int
            if (entity != null) {
                entity!!.BecomeInactive(Entity.TH_ANIMATE)
            }
            i = Anim.ANIMCHANNEL_ALL
            while (i < Anim.ANIM_NumAnimChannels) {
                j = 0
                while (j < Anim.ANIM_MaxAnimsPerChannel) {
                    channels[i][j].Reset(null)
                    j++
                }
                i++
            }
            jointMods.clear()

//	Mem_Free16( joints );
            joints.clear()
            numJoints._val = 0
            modelDef = null
            ForceUpdate()
        }

        private fun PushAnims(channelNum: Int, currentTime: Int, blendTime: Int) {
            var i: Int
            val channel: kotlin.collections.ArrayList<idAnimBlend>
            channel = channels[channelNum]
            if (0f == channel[0].GetWeight(currentTime) || channel[0].starttime == currentTime) {
                return
            }
            i = Anim.ANIM_MaxAnimsPerChannel - 1
            while (i > 0) {
                channel[i] = idAnimBlend(channel[i - 1])
                i--
            }
            channel[0].Reset(modelDef)
            channel[1].Clear(currentTime, blendTime)
            ForceUpdate()
        }

        companion object {
            private val r_showSkel: idCVar = idCVar(
                "r_showSkel",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER,
                "",
                0f,
                2f,
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
            joints = ArrayList()
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
                    channels[i][j] = idAnimBlend()
                    j++
                }
                i++
            }
        }
    }
}