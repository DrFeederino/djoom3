package neo.Game.GameSys

import neo.CM.CollisionModel_local
import neo.Game.*
import neo.Game.AFEntity.idAFEntity_Base
import neo.Game.AFEntity.idAFEntity_Generic
import neo.Game.AFEntity.idAFEntity_WithAttachedHead
import neo.Game.AI.AI.idAI
import neo.Game.Animation.Anim.idAnimManager
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.Animation.Anim_Import.idModelExport
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.FX.idEntityFx
import neo.Game.Game_local.idGameLocal
import neo.Game.Light.idLight
import neo.Game.Moveable.idMoveable
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Weapon.idWeapon
import neo.Renderer.*
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.RenderWorld.renderEntity_s
import neo.TempDump
import neo.TempDump.CPP_class.Char
import neo.TempDump.void_callback
import neo.framework.Async.NetworkSystem
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.*
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Dict_h.idDict
import neo.idlib.Lib.idLib
import neo.idlib.MapFile.idMapEntity
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.idStrList
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.*
import java.util.stream.Stream

/**
 *
 */
object SysCmds {
    const val MAX_DEBUGLINES = 128
    var debugLines = Stream.generate { gameDebugLine_t() }.limit(SysCmds.MAX_DEBUGLINES.toLong())
        .toArray<gameDebugLine_t?> { _Dummy_.__Array__() }

    /*
     ==================
     Cmd_GetFloatArg
     ==================
     */
    fun Cmd_GetFloatArg(args: CmdArgs.idCmdArgs?, argNum: IntArray?): Float {
        val value: String?
        value = args.Argv(argNum.get(0)++)
        return value.toFloat()
    }

    /*
     ==================
     KillEntities

     Kills all the entities of the given class in a level.
     ==================
     */
    fun KillEntities(args: CmdArgs.idCmdArgs?,    /*idTypeInfo*/superClass: Class<*>?) {
        var ent: idEntity?
        val ignore = idStrList()
        var name: String?
        var i: Int
        if (TempDump.NOT(Game_local.gameLocal.GetLocalPlayer()) || !Game_local.gameLocal.CheatsOk(false)) {
            return
        }
        i = 1
        while (i < args.Argc()) {
            name = args.Argv(i)
            ignore.add(idStr.Companion.parseStr(name))
            i++
        }
        ent = Game_local.gameLocal.spawnedEntities.Next()
        while (ent != null) {
            if (superClass.isInstance(ent)) {
                i = 0
                while (i < ignore.size()) {
                    if (ignore[i] == ent.name) {
                        break
                    }
                    i++
                }
                if (i >= ignore.size()) {
                    ent.PostEventMS(neo.Game.GameSys.Class.EV_Remove, 0)
                }
            }
            ent = ent.spawnNode.Next()
        }
    }

    /*
     ==================
     Cmd_Say
     ==================
     */
    fun Cmd_Say(team: Boolean, args: CmdArgs.idCmdArgs?) {
        var name: String
        val text: idStr
        val cmd = if (team) "sayTeam" else "say"
        if (!Game_local.gameLocal.isMultiplayer) {
            Game_local.gameLocal.Printf("%s can only be used in a multiplayer game\n", cmd)
            return
        }
        if (args.Argc() < 2) {
            Game_local.gameLocal.Printf("usage: %s <text>\n", cmd)
            return
        }
        text = idStr(args.Args())
        if (text.Length() == 0) {
            return
        }
        if (text.oGet(text.Length() - 1) == '\n') {
            text.oSet(text.Length() - 1, '\u0000')
        }
        name = "player"
        val player: idPlayer?

        // here we need to special case a listen server to use the real client name instead of "server"
        // "server" will only appear on a dedicated server
        if (Game_local.gameLocal.isClient || idLib.cvarSystem.GetCVarInteger("net_serverDedicated") == 0) {
            player =
                if (Game_local.gameLocal.localClientNum >= 0) Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] as idPlayer else null
            if (player != null) {
                name = player.GetUserInfo().GetString("ui_name", "player")
            }
        } else {
            name = "server"
        }
        if (Game_local.gameLocal.isClient) {
            val outMsg = idBitMsg()
            val msgBuf = ByteBuffer.allocate(256)
            outMsg.Init(msgBuf, msgBuf.capacity())
            outMsg.WriteByte(if (team) Game_local.GAME_RELIABLE_MESSAGE_TCHAT else Game_local.GAME_RELIABLE_MESSAGE_CHAT)
            outMsg.WriteString(name)
            outMsg.WriteString(text.toString(), -1, false)
            NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
        } else {
            Game_local.gameLocal.mpGame.ProcessChatMessage(
                Game_local.gameLocal.localClientNum,
                team,
                name,
                text.toString(),
                null
            )
        }
    }

    /*
     ==================
     PrintFloat
     ==================
     */
    fun PrintFloat(f: Float) {
//        char[] buf = new char[128];
//        char i;
//
//        for (i = sprintf(buf, "%3.2f", f); i < 7; i++) {
//            buf[i] = ' ';
//        }
//        buf[i] = '\0';
//        gameLocal.Printf(buf);
        Game_local.gameLocal.Printf(String.format("%3.2f", f))
    }

    /*
     ==================
     D_DrawDebugLines
     ==================
     */
    fun D_DrawDebugLines() {
        var i: Int
        val forward = idVec3()
        val right = idVec3()
        val up = idVec3()
        val p1 = idVec3()
        val p2 = idVec3()
        var color: idVec4
        var l: Float
        i = 0
        while (i < SysCmds.MAX_DEBUGLINES) {
            if (SysCmds.debugLines[i].used) {
                if (!SysCmds.debugLines[i].blink || Game_local.gameLocal.time and (1 shl 9) != 0) {
                    color = idVec4(
                        SysCmds.debugLines[i].color and 1,
                        SysCmds.debugLines[i].color shr 1 and 1,
                        SysCmds.debugLines[i].color shr 2 and 1,
                        1
                    )
                    Game_local.gameRenderWorld.DebugLine(color, SysCmds.debugLines[i].start, SysCmds.debugLines[i].end)
                    //
                    if (SysCmds.debugLines[i].arrow) {
                        // draw a nice arrow
                        forward.oSet(SysCmds.debugLines[i].end.oMinus(SysCmds.debugLines[i].start))
                        l = forward.Normalize() * 0.2f
                        forward.NormalVectors(right, up)
                        if (l > 3.0f) {
                            l = 3.0f
                        }
                        p1.oSet(SysCmds.debugLines[i].end.oMinus(forward.times(l).oPlus(right.times(l * 0.4f))))
                        p2.oSet(
                            SysCmds.debugLines[i].end.oMinus(
                                forward.times(l).oMinus(right.times(l * 0.4f))
                            )
                        )
                        Game_local.gameRenderWorld.DebugLine(color, SysCmds.debugLines[i].end, p1)
                        Game_local.gameRenderWorld.DebugLine(color, SysCmds.debugLines[i].end, p2)
                        Game_local.gameRenderWorld.DebugLine(color, p1, p2)
                    }
                }
            }
            i++
        }
    }

    /*
     ===================
     Cmd_EntityList_f
     ===================
     */
    class Cmd_EntityList_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var check: idEntity?
            var count: Int
            var   /*size_t*/size: Int
            var match: String?
            if (args.Argc() > 1) {
                match = args.Args()
                match = match.replace(" ".toRegex(), "")
            } else {
                match = ""
            }
            count = 0
            size = 0
            Game_local.gameLocal.Printf("%-4s  %-20s %-20s %s\n", " Num", "EntityDef", "Class", "Name")
            Game_local.gameLocal.Printf("--------------------------------------------------------------------\n")
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                check = Game_local.gameLocal.entities[e]
                if (null == check) {
                    e++
                    continue
                }
                if (!check.name.Filter(match, true)) {
                    e++
                    continue
                }
                Game_local.gameLocal.Printf(
                    "%4d: %-20s %-20s %s\n", e,
                    check.GetEntityDefName(), check.GetClassname(), check.name
                )
                count++
                size += check.spawnArgs.Allocated().toInt()
                e++
            }
            Game_local.gameLocal.Printf("...%d entities\n...%d bytes of spawnargs\n", count, size)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_EntityList_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_ActiveEntityList_f
     ===================
     */
    class Cmd_ActiveEntityList_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var check: idEntity?
            var count: Int
            count = 0
            Game_local.gameLocal.Printf("%-4s  %-20s %-20s %s\n", " Num", "EntityDef", "Class", "Name")
            Game_local.gameLocal.Printf("--------------------------------------------------------------------\n")
            check = Game_local.gameLocal.activeEntities.Next()
            while (check != null) {
                val dormant: Char = if (check.fl.isDormant) '-' else ' '
                Game_local.gameLocal.Printf(
                    "%4d:%c%-20s %-20s %s\n",
                    check.entityNumber,
                    dormant,
                    check.GetEntityDefName(),
                    check.GetClassname(),
                    check.name
                )
                count++
                check = check.activeNode.Next()
            }
            Game_local.gameLocal.Printf("...%d active entities\n", count)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ActiveEntityList_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_ListSpawnArgs_f
     ===================
     */
    class Cmd_ListSpawnArgs_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val ent: idEntity?
            ent = Game_local.gameLocal.FindEntity(args.Argv(1))
            if (null == ent) {
                Game_local.gameLocal.Printf("entity not found\n")
                return
            }
            i = 0
            while (i < ent.spawnArgs.GetNumKeyVals()) {
                val kv = ent.spawnArgs.GetKeyVal(i)
                Game_local.gameLocal.Printf(
                    """"%s"  ${Str.S_COLOR_WHITE}"%s"
""", kv.GetKey(), kv.GetValue()
                )
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ListSpawnArgs_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_ReloadScript_f
     ===================
     */
    class Cmd_ReloadScript_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            // shutdown the map because entities may point to script objects
            Game_local.gameLocal.MapShutdown()

            // recompile the scripts
            Game_local.gameLocal.program.Startup(Game.SCRIPT_DEFAULT)

            // error out so that the user can rerun the scripts
            idGameLocal.Companion.Error("Exiting map to reload scripts")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ReloadScript_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_Script_f
     ===================
     */
    class Cmd_Script_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val script: String?
            val text: String
            val funcname: String
            val thread: idThread
            val func: function_t?
            var ent: idEntity?
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            funcname = String.format("ConsoleFunction_%d", funcCount++)
            script = args.Args()
            text = String.format("void %s() {%s;}\n", funcname, script)
            if (Game_local.gameLocal.program.CompileText("console", text, true)) {
                func = Game_local.gameLocal.program.FindFunction(funcname)
                if (func != null) {
                    // set all the entity names in case the user named one in the script that wasn't referenced in the default script
                    ent = Game_local.gameLocal.spawnedEntities.Next()
                    while (ent != null) {
                        Game_local.gameLocal.program.SetEntity(ent.name.toString(), ent)
                        ent = ent.spawnNode.Next()
                    }
                    thread = idThread(func)
                    thread.Start()
                }
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Script_f()
            private var funcCount = 0
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_KillMonsters_f

     Kills all the monsters in a level.
     ==================
     */
    class Cmd_KillMonsters_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            SysCmds.KillEntities(args, idAI::class.java)

            // kill any projectiles as well since they have pointers to the monster that created them
            SysCmds.KillEntities(args, idProjectile::class.java)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_KillMonsters_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_KillMovables_f

     Kills all the moveables in a level.
     ==================
     */
    class Cmd_KillMovables_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (TempDump.NOT(Game_local.gameLocal.GetLocalPlayer()) || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            SysCmds.KillEntities(args, idMoveable::class.java)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_KillMovables_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_KillRagdolls_f

     Kills all the ragdolls in a level.
     ==================
     */
    class Cmd_KillRagdolls_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (TempDump.NOT(Game_local.gameLocal.GetLocalPlayer()) || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            SysCmds.KillEntities(args, idAFEntity_Generic::class.java)
            SysCmds.KillEntities(args, idAFEntity_WithAttachedHead::class.java)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_KillRagdolls_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Give_f

     Give items to a client
     ==================
     */
    class Cmd_Give_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val name: String?
            var i: Int
            val give_all: Boolean
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            name = args.Argv(1)
            give_all = idStr.Companion.Icmp(name, "all") == 0
            if (give_all || idStr.Companion.Cmpn(name, "weapon", 6) == 0) {
                if (Game_local.gameLocal.world.spawnArgs.GetBool("no_Weapons")) {
                    Game_local.gameLocal.world.spawnArgs.SetBool("no_Weapons", false)
                    i = 0
                    while (i < Game_local.gameLocal.numClients) {
                        if (Game_local.gameLocal.entities[i] != null) {
                            Game_local.gameLocal.entities[i].PostEventSec(
                                Player.EV_Player_SelectWeapon,
                                0.5f,
                                Game_local.gameLocal.entities[i].spawnArgs.GetString("def_weapon1")
                            )
                        }
                        i++
                    }
                }
            }
            if (idStr.Companion.Cmpn(name, "weapon_", 7) == 0 || idStr.Companion.Cmpn(
                    name,
                    "item_",
                    5
                ) == 0 || idStr.Companion.Cmpn(name, "ammo_", 5) == 0
            ) {
                player.GiveItem(name)
                return
            }
            if (give_all || idStr.Companion.Icmp(name, "health") == 0) {
                player.health = player.inventory.maxHealth
                if (!give_all) {
                    return
                }
            }
            if (give_all || idStr.Companion.Icmp(name, "weapons") == 0) {
                player.inventory.weapons = Lib.Companion.BIT(Player.MAX_WEAPONS) - 1
                player.CacheWeapons()
                if (!give_all) {
                    return
                }
            }
            if (give_all || idStr.Companion.Icmp(name, "ammo") == 0) {
                i = 0
                while (i < Weapon.AMMO_NUMTYPES) {
                    player.inventory.ammo[i] =
                        player.inventory.MaxAmmoForAmmoClass(player, idWeapon.Companion.GetAmmoNameForNum(i))
                    i++
                }
                if (!give_all) {
                    return
                }
            }
            if (give_all || idStr.Companion.Icmp(name, "armor") == 0) {
                player.inventory.armor = player.inventory.maxarmor
                if (!give_all) {
                    return
                }
            }
            if (idStr.Companion.Icmp(name, "berserk") == 0) {
                player.GivePowerUp(Player.BERSERK, Math_h.SEC2MS(30.0f).toInt())
                return
            }
            if (idStr.Companion.Icmp(name, "invis") == 0) {
                player.GivePowerUp(Player.INVISIBILITY, Math_h.SEC2MS(30.0f).toInt())
                return
            }
            if (idStr.Companion.Icmp(name, "pda") == 0) {
                player.GivePDA(idStr.Companion.parseStr(args.Argv(2)), null)
                return
            }
            if (idStr.Companion.Icmp(name, "video") == 0) {
                player.GiveVideo(args.Argv(2), null)
                return
            }
            if (!give_all && !player.Give(args.Argv(1), args.Argv(2))) {
                Game_local.gameLocal.Printf("unknown item\n")
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Give_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_CenterView_f

     Centers the players pitch
     ==================
     */
    class Cmd_CenterView_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val ang: idAngles
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null) {
                return
            }
            ang = idAngles(player.viewAngles)
            ang.pitch = 0.0f
            player.SetViewAngles(ang)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_CenterView_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_God_f

     Sets client to godmode

     argv(0) god
     ==================
     */
    class Cmd_God_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val msg: String
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (player.godmode) {
                player.godmode = false
                msg = "godmode OFF\n"
            } else {
                player.godmode = true
                msg = "godmode ON\n"
            }
            Game_local.gameLocal.Printf("%s", msg)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_God_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Notarget_f

     Sets client to notarget

     argv(0) notarget
     ==================
     */
    class Cmd_Notarget_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val msg: String
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (player.fl.notarget) {
                player.fl.notarget = false
                msg = "notarget OFF\n"
            } else {
                player.fl.notarget = true
                msg = "notarget ON\n"
            }
            Game_local.gameLocal.Printf("%s", msg)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Notarget_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Noclip_f

     argv(0) noclip
     ==================
     */
    class Cmd_Noclip_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val msg: String
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            msg = if (player.noclip) {
                "noclip OFF\n"
            } else {
                "noclip ON\n"
            }
            player.noclip = !player.noclip
            Game_local.gameLocal.Printf("%s", msg)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Noclip_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_Kill_f
     =================
     */
    class Cmd_Kill_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            if (Game_local.gameLocal.isMultiplayer) {
                if (Game_local.gameLocal.isClient) {
                    val outMsg = idBitMsg()
                    val msgBuf = ByteBuffer.allocate(Game_local.MAX_GAME_MESSAGE_SIZE)
                    outMsg.Init(msgBuf, msgBuf.capacity())
                    outMsg.WriteByte(Game_local.GAME_RELIABLE_MESSAGE_KILL)
                    NetworkSystem.networkSystem.ClientSendReliableMessage(outMsg)
                } else {
                    player = Game_local.gameLocal.GetClientByCmdArgs(args)
                    if (player == null) {
                        Common.common.Printf("kill <client nickname> or kill <client index>\n")
                        return
                    }
                    player.Kill(false, false)
                    CmdSystem.cmdSystem.BufferCommandText(
                        cmdExecution_t.CMD_EXEC_NOW,
                        Str.va(
                            "say killed client %d '%s^0'\n",
                            player.entityNumber,
                            Game_local.gameLocal.userInfo[player.entityNumber].GetString("ui_name")
                        )
                    )
                }
            } else {
                player = Game_local.gameLocal.GetLocalPlayer()
                if (player == null) {
                    return
                }
                player.Kill(false, false)
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Kill_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_PlayerModel_f
     =================
     */
    class Cmd_PlayerModel_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val name: String?
            val pos = idVec3()
            val ang: idAngles
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 2) {
                Game_local.gameLocal.Printf("usage: playerModel <modelname>\n")
                return
            }
            name = args.Argv(1)
            player.spawnArgs.Set("model", name)
            pos.oSet(player.GetPhysics().GetOrigin())
            ang = idAngles(player.viewAngles)
            player.SpawnToPoint(pos, ang)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_PlayerModel_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Say_f
     ==================
     */
    class Cmd_Say_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            SysCmds.Cmd_Say(false, args)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Say_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SayTeam_f
     ==================
     */
    class Cmd_SayTeam_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            SysCmds.Cmd_Say(true, args)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SayTeam_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_AddChatLine_f
     ==================
     */
    class Cmd_AddChatLine_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            Game_local.gameLocal.mpGame.AddChatLine(args.Argv(1))
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_AddChatLine_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Kick_f
     ==================
     */
    class Cmd_Kick_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            if (!Game_local.gameLocal.isMultiplayer) {
                Game_local.gameLocal.Printf("kick can only be used in a multiplayer game\n")
                return
            }
            if (Game_local.gameLocal.isClient) {
                Game_local.gameLocal.Printf("You have no such power. This is a server command\n")
                return
            }
            player = Game_local.gameLocal.GetClientByCmdArgs(args)
            if (player == null) {
                Game_local.gameLocal.Printf("usage: kick <client nickname> or kick <client index>\n")
                return
            }
            CmdSystem.cmdSystem.BufferCommandText(
                cmdExecution_t.CMD_EXEC_NOW,
                Str.va(
                    "say kicking out client %d '%s^0'\n",
                    player.entityNumber,
                    Game_local.gameLocal.userInfo[player.entityNumber].GetString("ui_name")
                )
            )
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, Str.va("kick %d\n", player.entityNumber))
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Kick_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_GetViewpos_f
     ==================
     */
    class Cmd_GetViewpos_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val origin = idVec3()
            val axis = idMat3()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null) {
                return
            }
            val view = player.GetRenderView()
            if (view != null) {
                Game_local.gameLocal.Printf("(%s) %.1f\n", view.vieworg.ToString(), view.viewaxis.oGet(0).ToYaw())
            } else {
                player.GetViewPos(origin, axis)
                Game_local.gameLocal.Printf("(%s) %.1f\n", origin.ToString(), axis.oGet(0).ToYaw())
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_GetViewpos_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_SetViewpos_f
     =================
     */
    class Cmd_SetViewpos_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val origin = idVec3()
            val angels = idAngles()
            var i: Int
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() != 4 && args.Argc() != 5) {
                Game_local.gameLocal.Printf("usage: setviewpos <x> <y> <z> <yaw>\n")
                return
            }
            angels.Zero()
            if (args.Argc() == 5) {
                angels.yaw = args.Argv(4).toFloat()
            }
            i = 0
            while (i < 3) {
                origin.oSet(i, args.Argv(i + 1).toFloat())
                i++
            }
            origin.z -= SysCvar.pm_normalviewheight.GetFloat() - 0.25f
            player.Teleport(origin, angels, null)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SetViewpos_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_Teleport_f
     =================
     */
    class Cmd_Teleport_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val origin = idVec3()
            val angles = idAngles()
            val player: idPlayer?
            val ent: idEntity?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() != 2) {
                Game_local.gameLocal.Printf("usage: teleport <name of entity to teleport to>\n")
                return
            }
            ent = Game_local.gameLocal.FindEntity(args.Argv(1))
            if (TempDump.NOT(ent)) {
                Game_local.gameLocal.Printf("entity not found\n")
                return
            }
            angles.Zero()
            angles.yaw = ent.GetPhysics().GetAxis().oGet(0).ToYaw()
            origin.oSet(ent.GetPhysics().GetOrigin())
            player.Teleport(origin, angles, ent)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Teleport_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_Trigger_f
     =================
     */
    class Cmd_Trigger_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val origin = idVec3()
            var angles: idAngles
            val player: idPlayer?
            val ent: idEntity?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() != 2) {
                Game_local.gameLocal.Printf("usage: trigger <name of entity to trigger>\n")
                return
            }
            ent = Game_local.gameLocal.FindEntity(args.Argv(1))
            if (TempDump.NOT(ent)) {
                Game_local.gameLocal.Printf("entity not found\n")
                return
            }
            ent.Signal(signalNum_t.SIG_TRIGGER)
            ent.ProcessEvent(Entity.EV_Activate, player)
            ent.TriggerGuis()
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Trigger_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_Spawn_f
     ===================
     */
    class Cmd_Spawn_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var key: String?
            var value: String?
            var i: Int
            val yaw: Float
            val org = idVec3()
            val player: idPlayer?
            val dict = idDict()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            if (args.Argc() and 1 != 0) {    // must always have an even number of arguments
                Game_local.gameLocal.Printf("usage: spawn classname [key/value pairs]\n")
                return
            }
            yaw = player.viewAngles.yaw
            value = args.Argv(1)
            dict.Set("classname", value)
            dict.Set("angle", Str.va("%f", yaw + 180))
            org.oSet(
                player.GetPhysics().GetOrigin()
                    .oPlus(idAngles(0, yaw, 0).ToForward().times(80f).oPlus(idVec3(0, 0, 1)))
            )
            dict.Set("origin", org.ToString())
            i = 2
            while (i < args.Argc() - 1) {
                key = args.Argv(i)
                value = args.Argv(i + 1)
                dict.Set(key, value)
                i += 2
            }
            Game_local.gameLocal.SpawnEntityDef(dict)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Spawn_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Damage_f

     Damages the specified entity
     ==================
     */
    class Cmd_Damage_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (TempDump.NOT(Game_local.gameLocal.GetLocalPlayer()) || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            if (args.Argc() != 3) {
                Game_local.gameLocal.Printf("usage: damage <name of entity to damage> <damage>\n")
                return
            }
            val ent = Game_local.gameLocal.FindEntity(args.Argv(1))
            if (TempDump.NOT(ent)) {
                Game_local.gameLocal.Printf("entity not found\n")
                return
            }
            ent.Damage(
                Game_local.gameLocal.world,
                Game_local.gameLocal.world,
                idVec3(0, 0, 1),
                "damage_moverCrush",
                args.Argv(2).toInt().toFloat(),
                Model.INVALID_JOINT
            )
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Damage_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_Remove_f

     Removes the specified entity
     ==================
     */
    class Cmd_Remove_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (TempDump.NOT(Game_local.gameLocal.GetLocalPlayer()) || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            if (args.Argc() != 2) {
                Game_local.gameLocal.Printf("usage: remove <name of entity to remove>\n")
                return
            }
            val ent = Game_local.gameLocal.FindEntity(args.Argv(1))
            if (TempDump.NOT(ent)) {
                Game_local.gameLocal.Printf("entity not found\n")
                return
            }

//        delete ent;
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_Remove_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_TestLight_f
     ===================
     */
    class Cmd_TestLight_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val filename = idStr()
            var key: String?
            var value: String?
            var name: String? = null
            val player: idPlayer?
            val dict = idDict()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            val rv = player.GetRenderView()
            val fov = Math.tan((idMath.M_DEG2RAD * rv.fov_x / 2).toDouble()).toFloat()
            dict.SetMatrix("rotation", idMat3.Companion.getMat3_default())
            dict.SetVector("origin", rv.vieworg)
            dict.SetVector("light_target", rv.viewaxis.oGet(0))
            dict.SetVector("light_right", rv.viewaxis.oGet(1).times(-fov))
            dict.SetVector("light_up", rv.viewaxis.oGet(2).times(fov))
            dict.SetVector("light_start", rv.viewaxis.oGet(0).times(16f))
            dict.SetVector("light_end", rv.viewaxis.oGet(0).times(1000f))
            if (args.Argc() >= 2) {
                value = args.Argv(1)
                filename.oSet(args.Argv(1))
                filename.DefaultFileExtension(".tga")
                dict.Set("texture", filename)
            }
            dict.Set("classname", "light")
            i = 2
            while (i < args.Argc() - 1) {
                key = args.Argv(i)
                value = args.Argv(i + 1)
                dict.Set(key, value)
                i += 2
            }
            i = 0
            while (i < Game_local.MAX_GENTITIES) {
                name = Str.va("spawned_light_%d", i) // not just light_, or it might pick up a prelight shadow
                if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                    break
                }
                i++
            }
            dict.Set("name", name)
            Game_local.gameLocal.SpawnEntityDef(dict)
            Game_local.gameLocal.Printf("Created new light\n")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestLight_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Cmd_TestPointLight_f
     ===================
     */
    class Cmd_TestPointLight_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var key: String?
            var value: String?
            var name: String? = null
            var i: Int
            val player: idPlayer?
            val dict = idDict()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            dict.SetVector("origin", player.GetRenderView().vieworg)
            if (args.Argc() >= 2) {
                value = args.Argv(1)
                dict.Set("light", value)
            } else {
                dict.Set("light", "300")
            }
            dict.Set("classname", "light")
            i = 2
            while (i < args.Argc() - 1) {
                key = args.Argv(i)
                value = args.Argv(i + 1)
                dict.Set(key, value)
                i += 2
            }
            i = 0
            while (i < Game_local.MAX_GENTITIES) {
                name = Str.va("light_%d", i)
                if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                    break
                }
                i++
            }
            dict.Set("name", name)
            Game_local.gameLocal.SpawnEntityDef(dict)
            Game_local.gameLocal.Printf("Created new point light\n")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestPointLight_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_PopLight_f
     ==================
     */
    class Cmd_PopLight_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var ent: idEntity?
            val mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            var lastLight: idLight?
            var last: Int
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            val removeFromMap = args.Argc() > 1
            lastLight = null
            last = -1
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                if (ent !is idLight) {
                    ent = ent.spawnNode.Next()
                    continue
                }
                if (Game_local.gameLocal.spawnIds[ent.entityNumber] > last) {
                    last = Game_local.gameLocal.spawnIds[ent.entityNumber]
                    lastLight = ent as idLight?
                }
                ent = ent.spawnNode.Next()
            }
            if (lastLight != null) {
                // find map file entity
                mapEnt = mapFile.FindEntity(lastLight.name.toString())
                if (removeFromMap && mapEnt != null) {
                    mapFile.RemoveEntity(mapEnt)
                }
                Game_local.gameLocal.Printf("Removing light %d\n", lastLight.GetLightDefHandle())
                //            delete lastLight;
            } else {
                Game_local.gameLocal.Printf("No lights to clear.\n")
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_PopLight_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ====================
     Cmd_ClearLights_f
     ====================
     */
    class Cmd_ClearLights_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var ent: idEntity?
            var next: idEntity?
            var light: idLight?
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val removeFromMap = args.Argc() > 1
            Game_local.gameLocal.Printf("Clearing all lights.\n")
            ent = Game_local.gameLocal.spawnedEntities.Next()
            while (ent != null) {
                next = ent.spawnNode.Next()
                if (ent !is idLight) {
                    ent = next
                    continue
                }
                light = ent as idLight?
                mapEnt = mapFile.FindEntity(light.name.toString())
                if (removeFromMap && mapEnt != null) {
                    mapFile.RemoveEntity(mapEnt)
                }
                ent = next
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ClearLights_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_TestFx_f
     ==================
     */
    class Cmd_TestFx_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val offset = idVec3()
            val name: String?
            val player: idPlayer?
            val dict = idDict()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }

            // delete the testModel if active
            if (Game_local.gameLocal.testFx != null) {
//            delete gameLocal.testFx;
                Game_local.gameLocal.testFx = null
            }
            if (args.Argc() < 2) {
                return
            }
            name = args.Argv(1)
            offset.oSet(player.GetPhysics().GetOrigin().oPlus(player.viewAngles.ToForward().times(100.0f)))
            dict.Set("origin", offset.ToString())
            dict.Set("test", "1")
            dict.Set("fx", name)
            Game_local.gameLocal.testFx =
                Game_local.gameLocal.SpawnEntityType(idEntityFx::class.java, dict) as idEntityFx
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestFx_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    class gameDebugLine_t {
        var arrow = false
        var blink = false
        var color = 0
        val start: idVec3? = idVec3()
        val end: idVec3? = idVec3()
        var used = false
    }

    /*
     ==================
     Cmd_AddDebugLine_f
     ==================
     */
    class Cmd_AddDebugLine_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val argNum = intArrayOf(0)
            val value: String?
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 7) {
                Game_local.gameLocal.Printf("usage: addline <x y z> <x y z> <color>\n")
                return
            }
            i = 0
            while (i < SysCmds.MAX_DEBUGLINES) {
                if (!SysCmds.debugLines[i].used) {
                    break
                }
                i++
            }
            if (i >= SysCmds.MAX_DEBUGLINES) {
                Game_local.gameLocal.Printf("no free debug lines\n")
                return
            }
            value = args.Argv(0)
            SysCmds.debugLines[i].arrow = TempDump.NOT(idStr.Companion.Icmp(value, "addarrow").toDouble())
            SysCmds.debugLines[i].used = true
            SysCmds.debugLines[i].blink = false
            argNum[0] = 1
            SysCmds.debugLines[i].start.x = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].start.y = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].start.z = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].end.x = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].end.y = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].end.z = SysCmds.Cmd_GetFloatArg(args, argNum)
            SysCmds.debugLines[i].color = SysCmds.Cmd_GetFloatArg(args, argNum).toInt()
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_AddDebugLine_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_RemoveDebugLine_f
     ==================
     */
    class Cmd_RemoveDebugLine_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var num: Int
            val value: String?
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 2) {
                Game_local.gameLocal.Printf("usage: removeline <num>\n")
                return
            }
            value = args.Argv(1)
            num = value.toInt()
            i = 0
            while (i < SysCmds.MAX_DEBUGLINES) {
                if (SysCmds.debugLines[i].used) {
                    if (--num < 0) {
                        break
                    }
                }
                i++
            }
            if (i >= SysCmds.MAX_DEBUGLINES) {
                Game_local.gameLocal.Printf("line not found\n")
                return
            }
            SysCmds.debugLines[i].used = false
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_RemoveDebugLine_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_BlinkDebugLine_f
     ==================
     */
    class Cmd_BlinkDebugLine_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var num: Int
            val value: String?
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 2) {
                Game_local.gameLocal.Printf("usage: blinkline <num>\n")
                return
            }
            value = args.Argv(1)
            num = value.toInt()
            i = 0
            while (i < SysCmds.MAX_DEBUGLINES) {
                if (SysCmds.debugLines[i].used) {
                    if (--num < 0) {
                        break
                    }
                }
                i++
            }
            if (i >= SysCmds.MAX_DEBUGLINES) {
                Game_local.gameLocal.Printf("line not found\n")
                return
            }
            SysCmds.debugLines[i].blink = !SysCmds.debugLines[i].blink
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_BlinkDebugLine_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ListDebugLines_f
     ==================
     */
    class Cmd_ListDebugLines_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var num: Int
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            num = 0
            Game_local.gameLocal.Printf("line num: x1     y1     z1     x2     y2     z2     c  b  a\n")
            i = 0
            while (i < SysCmds.MAX_DEBUGLINES) {
                if (SysCmds.debugLines[i].used) {
                    Game_local.gameLocal.Printf("line %3d: ", num)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].start.x)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].start.y)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].start.z)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].end.x)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].end.y)
                    SysCmds.PrintFloat(SysCmds.debugLines[i].end.z)
                    Game_local.gameLocal.Printf(
                        "%d  %d  %d\n",
                        SysCmds.debugLines[i].color,
                        SysCmds.debugLines[i].blink,
                        SysCmds.debugLines[i].arrow
                    )
                    num++
                }
                i++
            }
            if (TempDump.NOT(num.toDouble())) {
                Game_local.gameLocal.Printf("no debug lines\n")
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ListDebugLines_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ListCollisionModels_f
     ==================
     */
    class Cmd_ListCollisionModels_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            CollisionModel_local.collisionModelManager.ListModels()
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ListCollisionModels_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_CollisionModelInfo_f
     ==================
     */
    class Cmd_CollisionModelInfo_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val value: String?
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 2) {
                Game_local.gameLocal.Printf(
                    """
    usage: collisionModelInfo <modelNum>
    use 'all' instead of the model number for accumulated info
    
    """.trimIndent()
                )
                return
            }
            value = args.Argv(1)
            if (TempDump.NOT(idStr.Companion.Icmp(value, "all").toDouble())) {
                CollisionModel_local.collisionModelManager.ModelInfo(-1)
            } else {
                CollisionModel_local.collisionModelManager.ModelInfo(value.toInt())
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_CollisionModelInfo_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ExportModels_f
     ==================
     */
    class Cmd_ExportModels_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val exporter = idModelExport()
            val name = idStr()

            // don't allow exporting models when cheats are disabled,
            // but if we're not in the game, it's ok
            if (Game_local.gameLocal.GetLocalPlayer() != null && !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            if (args.Argc() < 2) {
                exporter.ExportModels("def", ".def")
            } else {
                name.oSet(args.Argv(1))
                name.oSet("def/$name")
                name.DefaultFileExtension(".def")
                exporter.ExportDefFile(name.toString())
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ExportModels_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ReexportModels_f
     ==================
     */
    class Cmd_ReexportModels_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val exporter = idModelExport()
            val name = idStr()

            // don't allow exporting models when cheats are disabled,
            // but if we're not in the game, it's ok
            if (Game_local.gameLocal.GetLocalPlayer() != null && !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            idAnimManager.Companion.forceExport = true
            if (args.Argc() < 2) {
                exporter.ExportModels("def", ".def")
            } else {
                name.oSet(args.Argv(1))
                name.oSet("def/$name")
                name.DefaultFileExtension(".def")
                exporter.ExportDefFile(name.toString())
            }
            idAnimManager.Companion.forceExport = false
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ReexportModels_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ReloadAnims_f
     ==================
     */
    class Cmd_ReloadAnims_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            // don't allow reloading anims when cheats are disabled,
            // but if we're not in the game, it's ok
            if (Game_local.gameLocal.GetLocalPlayer() != null && !Game_local.gameLocal.CheatsOk(false)) {
                return
            }
            Game_local.animationLib.ReloadAnims()
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ReloadAnims_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ListAnims_f
     ==================
     */
    class Cmd_ListAnims_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var ent: idEntity?
            var num: Int
            var   /*size_t*/size: Int
            var   /*size_t*/alloced: Int
            var animator: idAnimator?
            val classname: String?
            val dict: idDict?
            var i: Int
            if (args.Argc() > 1) {
                animator = idAnimator()
                classname = args.Argv(1)
                dict = Game_local.gameLocal.FindEntityDefDict(classname, false)
                if (TempDump.NOT(dict)) {
                    Game_local.gameLocal.Printf("Entitydef '%s' not found\n", classname)
                    return
                }
                animator.SetModel(dict.GetString("model"))
                Game_local.gameLocal.Printf("----------------\n")
                num = animator.NumAnims()
                i = 0
                while (i < num) {
                    Game_local.gameLocal.Printf("%s\n", animator.AnimFullName(i))
                    i++
                }
                Game_local.gameLocal.Printf("%d anims\n", num)
            } else {
                Game_local.animationLib.ListAnims()
                size = 0
                num = 0
                ent = Game_local.gameLocal.spawnedEntities.Next()
                while (ent != null) {
                    animator = ent.GetAnimator()
                    if (animator != null) {
                        alloced = animator.Allocated()
                        size += alloced
                        num++
                    }
                    ent = ent.spawnNode.Next()
                }
                Game_local.gameLocal.Printf("%d memory used in %d entity animators\n", size, num)
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_ListAnims_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_AASStats_f
     ==================
     */
    class Cmd_AASStats_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val aasNum: Int
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            aasNum = SysCvar.aas_test.GetInteger()
            val aas = Game_local.gameLocal.GetAAS(aasNum)
            if (TempDump.NOT(aas)) {
                Game_local.gameLocal.Printf("No aas #%d loaded\n", aasNum)
            } else {
                aas.Stats()
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_AASStats_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_TestDamage_f
     ==================
     */
    class Cmd_TestDamage_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val damageDefName: String?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 2 || args.Argc() > 3) {
                Game_local.gameLocal.Printf("usage: testDamage <damageDefName> [angle]\n")
                return
            }
            damageDefName = args.Argv(1)
            val dir = idVec3()
            if (args.Argc() == 3) {
                val angle = args.Argv(2).toFloat()
                val d1 = CFloat()
                val d0 = CFloat()
                idMath.SinCos(Math_h.DEG2RAD(angle), d1, d0)
                dir.oSet(idVec3(d0.getVal(), d1.getVal(), 0))
            } else {
                dir.oSet(idVec3())
                //            dir.Zero();
            }

            // give the player full health before and after
            // running the damage
            player.health = player.inventory.maxHealth
            player.Damage(null, null, dir, damageDefName, 1.0f, Model.INVALID_JOINT)
            player.health = player.inventory.maxHealth
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestDamage_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_TestBoneFx_f
     ==================
     */
    class Cmd_TestBoneFx_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val bone: String?
            val fx: String?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() < 3 || args.Argc() > 4) {
                Game_local.gameLocal.Printf("usage: testBoneFx <fxName> <boneName>\n")
                return
            }
            fx = args.Argv(1)
            bone = args.Argv(2)
            player.StartFxOnBone(fx, bone)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestBoneFx_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_TestDamage_f
     ==================
     */
    class Cmd_TestDeath_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            val dir = idVec3()
            val d1 = CFloat()
            val d0 = CFloat()
            idMath.SinCos(Math_h.DEG2RAD(45.0f), d1, d0)
            dir.oSet(idVec3(d0.getVal(), d1.getVal(), 0))
            SysCvar.g_testDeath.SetBool(true)
            player.Damage(null, null, dir, "damage_triggerhurt_1000", 1.0f, Model.INVALID_JOINT)
            if (args.Argc() >= 2) {
                player.SpawnGibs(dir, "damage_triggerhurt_1000")
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestDeath_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_WeaponSplat_f
     ==================
     */
    class Cmd_WeaponSplat_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            player.weapon.GetEntity().BloodSplat(2.0f)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_WeaponSplat_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SaveSelected_f
     ==================
     */
    class Cmd_SaveSelected_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val player: idPlayer?
            val s: idEntity?
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val dict = idDict()
            val mapName: idStr
            var name: String? = null
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            s = player.dragEntity.GetSelected()
            if (TempDump.NOT(s)) {
                Game_local.gameLocal.Printf("no entity selected, set g_dragShowSelection 1 to show the current selection\n")
                return
            }
            if (args.Argc() > 1) {
                mapName = idStr(args.Argv(1))
                mapName.oSet("maps/$mapName")
            } else {
                mapName = idStr(mapFile.GetName())
            }

            // find map file entity
            mapEnt = mapFile.FindEntity(s.name.toString())
            // create new map file entity if there isn't one for this articulated figure
            if (TempDump.NOT(mapEnt)) {
                mapEnt = idMapEntity()
                mapFile.AddEntity(mapEnt)
                i = 0
                while (i < 9999) {
                    name = Str.va("%s_%d", s.GetEntityDefName(), i)
                    if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                        break
                    }
                    i++
                }
                s.name.oSet(name)
                mapEnt.epairs.Set("classname", s.GetEntityDefName())
                mapEnt.epairs.Set("name", s.name)
            }
            if (s is idMoveable) {
                // save the moveable state
                mapEnt.epairs.Set("origin", s.GetPhysics().GetOrigin().ToString(8))
                mapEnt.epairs.Set("rotation", s.GetPhysics().GetAxis().ToString(8))
            } else if (s is idAFEntity_Generic || s is idAFEntity_WithAttachedHead) {
                // save the articulated figure state
                dict.Clear()
                (s as idAFEntity_Base?).SaveState(dict)
                mapEnt.epairs.Copy(dict)
            }

            // write out the map file
            mapFile.Write(mapName.toString(), ".map")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SaveSelected_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_DeleteSelected_f
     ==================
     */
    class Cmd_DeleteSelected_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (player != null) {
                player.dragEntity.DeleteSelected()
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_DeleteSelected_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SaveMoveables_f
     ==================
     */
    class Cmd_SaveMoveables_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var i: Int
            var m: idMoveable
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val mapName = idStr()
            var name: String? = null
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                m = Game_local.gameLocal.entities[e] as idMoveable
                if (TempDump.NOT(m) || m !is idMoveable) {
                    e++
                    continue
                }
                if (m.IsBound()) {
                    e++
                    continue
                }
                if (!m.IsAtRest()) {
                    break
                }
                e++
            }
            if (e < Game_local.MAX_GENTITIES) {
                Game_local.gameLocal.Warning(
                    "map not saved because the moveable entity %s is not at rest",
                    Game_local.gameLocal.entities[e].name
                )
                return
            }
            if (args.Argc() > 1) {
                mapName.oSet(args.Argv(1))
                mapName.oSet("maps/$mapName")
            } else {
                mapName.oSet(mapFile.GetName())
            }
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                m = Game_local.gameLocal.entities[e] as idMoveable
                if (TempDump.NOT(m) || m !is idMoveable) {
                    e++
                    continue
                }
                if (m.IsBound()) {
                    e++
                    continue
                }

                // find map file entity
                mapEnt = mapFile.FindEntity(m.name)
                // create new map file entity if there isn't one for this articulated figure
                if (TempDump.NOT(mapEnt)) {
                    mapEnt = idMapEntity()
                    mapFile.AddEntity(mapEnt)
                    i = 0
                    while (i < 9999) {
                        name = Str.va("%s_%d", m.GetEntityDefName(), i)
                        if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                            break
                        }
                        i++
                    }
                    m.name.oSet(name)
                    mapEnt.epairs.Set("classname", m.GetEntityDefName())
                    mapEnt.epairs.Set("name", m.name)
                }
                // save the moveable state
                mapEnt.epairs.Set("origin", m.GetPhysics().GetOrigin().ToString(8))
                mapEnt.epairs.Set("rotation", m.GetPhysics().GetAxis().ToString(8))
                e++
            }

            // write out the map file
            mapFile.Write(mapName.toString(), ".map")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SaveMoveables_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SaveRagdolls_f
     ==================
     */
    class Cmd_SaveRagdolls_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var i: Int
            var af: idAFEntity_Base
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val dict = idDict()
            val mapName = idStr()
            var name: String? = null
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() > 1) {
                mapName.oSet(args.Argv(1))
                mapName.oSet("maps/$mapName")
            } else {
                mapName.oSet(mapFile.GetName())
            }
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                af = Game_local.gameLocal.entities[e] as idAFEntity_Base
                if (TempDump.NOT(af)) {
                    e++
                    continue
                }
                if (af !is idAFEntity_WithAttachedHead && af !is idAFEntity_Generic) {
                    e++
                    continue
                }
                if (af.IsBound()) {
                    e++
                    continue
                }
                if (!af.IsAtRest()) {
                    Game_local.gameLocal.Warning(
                        "the articulated figure for entity %s is not at rest",
                        Game_local.gameLocal.entities[e].name
                    )
                }
                dict.Clear()
                af.SaveState(dict)

                // find map file entity
                mapEnt = mapFile.FindEntity(af.name.toString())
                // create new map file entity if there isn't one for this articulated figure
                if (TempDump.NOT(mapEnt)) {
                    mapEnt = idMapEntity()
                    mapFile.AddEntity(mapEnt)
                    i = 0
                    while (i < 9999) {
                        name = Str.va("%s_%d", af.GetEntityDefName(), i)
                        if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                            break
                        }
                        i++
                    }
                    af.name.oSet(name)
                    mapEnt.epairs.Set("classname", af.GetEntityDefName())
                    mapEnt.epairs.Set("name", af.name)
                }
                // save the articulated figure state
                mapEnt.epairs.Copy(dict)
                e++
            }

            // write out the map file
            mapFile.Write(mapName.toString(), ".map")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SaveRagdolls_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_BindRagdoll_f
     ==================
     */
    class Cmd_BindRagdoll_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (player != null) {
                player.dragEntity.BindSelected()
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_BindRagdoll_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_UnbindRagdoll_f
     ==================
     */
    class Cmd_UnbindRagdoll_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (player != null) {
                player.dragEntity.UnbindSelected()
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_UnbindRagdoll_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_GameError_f
     ==================
     */
    class Cmd_GameError_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            idGameLocal.Companion.Error("game error")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_GameError_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SaveLights_f
     ==================
     */
    class Cmd_SaveLights_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var i: Int
            var light: idLight
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val dict = idDict()
            val mapName = idStr()
            var name: String? = null
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() > 1) {
                mapName.oSet(args.Argv(1))
                mapName.oSet("maps/$mapName")
            } else {
                mapName.oSet(mapFile.GetName())
            }
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                light = Game_local.gameLocal.entities[e] as idLight
                if (TempDump.NOT(light) || light !is idLight) {
                    e++
                    continue
                }
                dict.Clear()
                light.SaveState(dict)

                // find map file entity
                mapEnt = mapFile.FindEntity(light.name.toString())
                // create new map file entity if there isn't one for this light
                if (TempDump.NOT(mapEnt)) {
                    mapEnt = idMapEntity()
                    mapFile.AddEntity(mapEnt)
                    i = 0
                    while (i < 9999) {
                        name = Str.va("%s_%d", light.GetEntityDefName(), i)
                        if (TempDump.NOT(Game_local.gameLocal.FindEntity(name))) {
                            break
                        }
                        i++
                    }
                    light.name.oSet(name)
                    mapEnt.epairs.Set("classname", light.GetEntityDefName())
                    mapEnt.epairs.Set("name", light.name)
                }
                // save the light state
                mapEnt.epairs.Copy(dict)
                e++
            }

            // write out the map file
            mapFile.Write(mapName.toString(), ".map")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SaveLights_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_SaveParticles_f
     ==================
     */
    class Cmd_SaveParticles_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var e: Int
            var ent: idEntity?
            var mapEnt: idMapEntity
            val mapFile = Game_local.gameLocal.GetLevelMap()
            val dict = idDict()
            val mapName = idStr()
            var strModel: idStr
            if (!Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() > 1) {
                mapName.oSet(args.Argv(1))
                mapName.oSet("maps/$mapName")
            } else {
                mapName.oSet(mapFile.GetName())
            }
            e = 0
            while (e < Game_local.MAX_GENTITIES) {
                ent = Game_local.gameLocal.entities[e]
                if (TempDump.NOT(ent)) {
                    e++
                    continue
                }
                strModel = idStr(ent.spawnArgs.GetString("model"))
                if (strModel.Length() != 0 && strModel.Find(".prt") > 0) {
                    dict.Clear()
                    dict.Set("model", ent.spawnArgs.GetString("model"))
                    dict.SetVector("origin", ent.GetPhysics().GetOrigin())

                    // find map file entity
                    mapEnt = mapFile.FindEntity(ent.name.toString())
                    // create new map file entity if there isn't one for this entity
                    if (TempDump.NOT(mapEnt)) {
                        e++
                        continue
                    }
                    // save the particle state
                    mapEnt.epairs.Copy(dict)
                }
                e++
            }

            // write out the map file
            mapFile.Write(mapName.toString(), ".map")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_SaveParticles_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_DisasmScript_f
     ==================
     */
    class Cmd_DisasmScript_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            Game_local.gameLocal.program.Disassemble()
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_DisasmScript_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_TestSave_f
     ==================
     */
    class Cmd_TestSave_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileWrite("test.sav")
            Game_local.gameLocal.SaveGame(f)
            FileSystem_h.fileSystem.CloseFile(f)
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestSave_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_RecordViewNotes_f
     ==================
     */
    class Cmd_RecordViewNotes_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player: idPlayer?
            val origin = idVec3()
            val axis = idMat3()
            if (args.Argc() <= 3) {
                return
            }
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null) {
                return
            }
            player.GetViewPos(origin, axis)

            // Argv(1) = filename for map (viewnotes/mapname/person)
            // Argv(2) = note number (person0001)
            // Argv(3) = comments
            val str = idStr(args.Argv(1))
            str.SetFileExtension(".txt")
            val file = FileSystem_h.fileSystem.OpenFileAppend(str.toString())
            if (file != null) {
                file.WriteFloatString("\"view\"\t( %s )\t( %s )\r\n", origin.ToString(), axis.ToString())
                file.WriteFloatString("\"comments\"\t\"%s: %s\"\r\n\r\n", args.Argv(2), args.Argv(3))
                FileSystem_h.fileSystem.CloseFile(file)
            }
            val viewComments = idStr(args.Argv(1))
            viewComments.StripLeading("viewnotes/")
            viewComments.oPluSet(" -- Loc: ")
            viewComments.oPluSet(origin.ToString())
            viewComments.oPluSet("\n")
            viewComments.oPluSet(args.Argv(3))
            player.hud.SetStateString("viewcomments", viewComments.toString())
            player.hud.HandleNamedEvent("showViewComments")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_RecordViewNotes_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_CloseViewNotes_f
     ==================
     */
    class Cmd_CloseViewNotes_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val player = Game_local.gameLocal.GetLocalPlayer() ?: return
            player.hud.SetStateString("viewcomments", "")
            player.hud.HandleNamedEvent("hideViewComments")
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_CloseViewNotes_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ==================
     Cmd_ShowViewNotes_f
     ==================
     */
    class Cmd_ShowViewNotes_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val token = idToken()
            val player: idPlayer?
            val origin = idVec3()
            val axis = idMat3()
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null) {
                return
            }
            if (!parser.IsLoaded()) {
                val str = idStr("viewnotes/")
                str.oPluSet(Game_local.gameLocal.GetMapName())
                str.StripFileExtension()
                str.oPluSet("/")
                if (args.Argc() > 1) {
                    str.oPluSet(args.Argv(1))
                } else {
                    str.oPluSet("comments")
                }
                str.SetFileExtension(".txt")
                if (!parser.LoadFile(str.toString())) {
                    Game_local.gameLocal.Printf("No view notes for %s\n", Game_local.gameLocal.GetMapName())
                    return
                }
            }
            if (parser.ExpectTokenString("view") && parser.Parse1DMatrix(3, origin)
                && parser.Parse1DMatrix(9, axis) && parser.ExpectTokenString("comments") && parser.ReadToken(token)
            ) {
                player.hud.SetStateString("viewcomments", token.toString())
                player.hud.HandleNamedEvent("showViewComments")
                player.Teleport(origin, axis.ToAngles(), null)
            } else {
                parser.FreeSource()
                player.hud.HandleNamedEvent("hideViewComments")
                return
            }
        }

        companion object {
            val parser: idLexer? =
                idLexer(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOFATALERRORS)
            private val instance: cmdFunction_t? = Cmd_ShowViewNotes_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     Cmd_NextGUI_f
     =================
     */
    class Cmd_NextGUI_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val origin = idVec3()
            val angles: idAngles
            val player: idPlayer?
            var ent: idEntity?
            val guiSurfaces = CInt()
            var newEnt: Boolean
            val renderEnt: renderEntity_s?
            val surfIndex: Int
            val geom: srfTriangles_s
            val modelMatrix: idMat4
            val normal = idVec3()
            val center = idVec3()
            val surfaces = Stream.generate { modelSurface_s() }
                .limit(RenderWorld.MAX_RENDERENTITY_GUI.toLong())
                .toArray<modelSurface_s?> { _Dummy_.__Array__() }
            player = Game_local.gameLocal.GetLocalPlayer()
            if (player == null || !Game_local.gameLocal.CheatsOk()) {
                return
            }
            if (args.Argc() != 1) {
                Game_local.gameLocal.Printf("usage: nextgui\n")
                return
            }

            // start at the last entity
            ent = Game_local.gameLocal.lastGUIEnt.GetEntity()

            // see if we have any gui surfaces left to go to on the current entity.
//        guiSurfaces = 0;
            newEnt = false
            if (ent == null) {
                newEnt = true
            } else if (FindEntityGUIs(ent, surfaces, RenderWorld.MAX_RENDERENTITY_GUI, guiSurfaces) == true) {
                if (Game_local.gameLocal.lastGUI >= guiSurfaces.getVal()) {
                    newEnt = true
                }
            } else {
                // no actual gui surfaces on this ent, so skip it
                newEnt = true
            }
            if (newEnt == true) {
                // go ahead and skip to the next entity with a gui...
                ent = if (ent == null) {
                    Game_local.gameLocal.spawnedEntities.Next()
                } else {
                    ent.spawnNode.Next()
                }
                while (ent != null) {
                    if (ent.spawnArgs.GetString("gui", null) != null) {
                        break
                    }
                    if (ent.spawnArgs.GetString("gui2", null) != null) {
                        break
                    }
                    if (ent.spawnArgs.GetString("gui3", null) != null) {
                        break
                    }

                    // try the next entity
                    Game_local.gameLocal.lastGUIEnt.oSet(ent)
                    ent = ent.spawnNode.Next()
                }
                Game_local.gameLocal.lastGUIEnt.oSet(ent)
                Game_local.gameLocal.lastGUI = 0
                if (TempDump.NOT(ent)) {
                    Game_local.gameLocal.Printf("No more gui entities. Starting over...\n")
                    return
                }
            }
            if (FindEntityGUIs(ent, surfaces, RenderWorld.MAX_RENDERENTITY_GUI, guiSurfaces) == false) {
                Game_local.gameLocal.Printf("Entity \"%s\" has gui properties but no gui surfaces.\n", ent.name)
            }
            if (guiSurfaces.getVal() == 0) {
                Game_local.gameLocal.Printf("Entity \"%s\" has gui properties but no gui surfaces!\n", ent.name)
                return
            }
            Game_local.gameLocal.Printf(
                "Teleporting to gui entity \"%s\", gui #%d.\n",
                ent.name,
                Game_local.gameLocal.lastGUI
            )
            renderEnt = ent.GetRenderEntity()
            surfIndex = Game_local.gameLocal.lastGUI++
            geom = surfaces[surfIndex].geometry
            if (geom == null) {
                Game_local.gameLocal.Printf("Entity \"%s\" has gui surface %d without geometry!\n", ent.name, surfIndex)
                return
            }
            assert(geom.facePlanes != null)
            modelMatrix = idMat4(renderEnt.axis, renderEnt.origin)
            normal.oSet(geom.facePlanes[0].Normal().times(renderEnt.axis))
            center.oSet(geom.bounds.GetCenter().times(modelMatrix))
            origin.oSet(center.oPlus(normal.times(32.0f)))
            origin.z -= player.EyeHeight()
            normal.timesAssign(-1.0f)
            angles = normal.ToAngles()

            //	make sure the player is in noclip
            player.noclip = true
            player.Teleport(origin, angles, null)
        }

        /*
         =================
         FindEntityGUIs

         helper function for Cmd_NextGUI_f.  Checks the passed entity to determine if it
         has any valid gui surfaces.
         =================
         */
        fun FindEntityGUIs(
            ent: idEntity?,
            surfaces: Array<modelSurface_s?>?,
            maxSurfs: Int,
            guiSurfaces: CInt?
        ): Boolean {
            val renderEnt: renderEntity_s?
            val renderModel: idRenderModel?
            var surf: modelSurface_s?
            var shader: idMaterial?
            var i: Int
            assert(surfaces != null)
            assert(ent != null)
            guiSurfaces.setVal(0)
            renderEnt = ent.GetRenderEntity()
            renderModel = renderEnt.hModel
            if (renderModel == null) {
                return false
            }
            i = 0
            while (i < renderModel.NumSurfaces()) {
                surf = renderModel.Surface(i)
                if (surf == null) {
                    i++
                    continue
                }
                shader = surf.shader
                if (shader == null) {
                    i++
                    continue
                }
                if (shader.GetEntityGui() > 0) {
                    surfaces.get(guiSurfaces.increment()) = surf
                }
                i++
            }
            return guiSurfaces.getVal() != 0
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_NextGUI_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    class ArgCompletion_DefFile private constructor() : CmdSystem.argCompletion_t() {
        override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String?>?) {
            CmdSystem.cmdSystem.ArgCompletion_FolderExtension(args, callback, "def/", true, ".def", null)
        }

        companion object {
            private val instance: CmdSystem.argCompletion_t? = ArgCompletion_DefFile()
            fun getInstance(): CmdSystem.argCompletion_t? {
                return instance
            }
        }
    }

    /*
     ===============
     Cmd_TestId_f
     outputs a string from the string table for the specified id
     ===============
     */
    class Cmd_TestId_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var id: String? = ""
            var i: Int
            if (args.Argc() == 1) {
                Common.common.Printf("usage: testid <string id>\n")
                return
            }
            i = 1
            while (i < args.Argc()) {
                id += args.Argv(i)
                i++
            }
            if (idStr.Companion.Cmpn(id, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) != 0) {
                id = Common.STRTABLE_ID + id
            }
            Game_local.gameLocal.mpGame.AddChatLine(
                Common.common.GetLanguageDict().GetString(id),
                "<nothing>",
                "<nothing>",
                "<nothing>"
            )
        }

        companion object {
            private val instance: cmdFunction_t? = Cmd_TestId_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}