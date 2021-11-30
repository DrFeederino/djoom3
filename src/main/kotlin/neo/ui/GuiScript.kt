package neo.ui

import neo.Renderer.Material
import neo.TempDump
import neo.framework.*
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBackground
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec4

/**
 *
 */
object GuiScript {
    val commandList: Array<guiCommandDef_t?>? = arrayOf(
        guiCommandDef_t("set", Script_Set.getInstance(), 2, 999),
        guiCommandDef_t("setFocus", Script_SetFocus.getInstance(), 1, 1),
        guiCommandDef_t("endGame", Script_EndGame.getInstance(), 0, 0),
        guiCommandDef_t("resetTime", Script_ResetTime.getInstance(), 0, 2),
        guiCommandDef_t("showCursor", Script_ShowCursor.getInstance(), 1, 1),
        guiCommandDef_t("resetCinematics", Script_ResetCinematics.getInstance(), 0, 2),
        guiCommandDef_t("transition", Script_Transition.getInstance(), 4, 6),
        guiCommandDef_t("localSound", Script_LocalSound.getInstance(), 1, 1),
        guiCommandDef_t("runScript", Script_RunScript.getInstance(), 1, 1),
        guiCommandDef_t("evalRegs", Script_EvalRegs.getInstance(), 0, 0)
    )
    val scriptCommandCount = commandList.size

    class idGSWinVar {
        var own = false
        var `var`: idWinVar? = null
    }

    internal class guiCommandDef_t(
        var name: String?, var handler: Handler?, // void (*handler) (idWindow *window, idList<idGSWinVar> *src);
        var mMinParms: Int, var mMaxParms: Int
    )

    class idGuiScript {
        // friend class idGuiScriptList;
        // friend class idWindow;
        var conditionReg: Int
        var elseList: idGuiScriptList? = null
        var ifList: idGuiScriptList? = null
        private var handler: Handler?
        private val parms: idList<idGSWinVar?>?

        // ~idGuiScript();
        fun Parse(src: idParser?): Boolean {
            var i: Int

            // first token should be function call
            // then a potentially variable set of parms
            // ended with a ;
            var token: idToken? = idToken()
            if (!src.ReadToken(token)) {
                src.Error("Unexpected end of file")
                return false
            }
            handler = null
            i = 0
            while (i < scriptCommandCount) {
                if (idStr.Companion.Icmp(token, commandList.get(i).name) == 0) {
                    handler = commandList.get(i).handler
                    break
                }
                i++
            }
            if (handler == null) {
                src.Error("Uknown script call %s", token)
            }
            // now read parms til ;
            // all parms are read as idWinStr's but will be fixed up later 
            // to be proper types
            while (true) {
                if (!src.ReadToken(idToken().also { token = it })) {
                    src.Error("Unexpected end of file")
                    return false
                }
                if (idStr.Companion.Icmp(token, ";") == 0) {
                    break
                }
                if (idStr.Companion.Icmp(token, "}") == 0) {
                    src.UnreadToken(token)
                    break
                }
                val str = idWinStr()
                str.data = token
                val wv = idGSWinVar()
                wv.own = true
                wv.`var` = str
                parms.Append(wv)
            }

            // 
            //  verify min/max params
            if (handler != null && (parms.Num() < commandList.get(i).mMinParms || parms.Num() > commandList.get(i).mMaxParms)) {
                src.Error("incorrect number of parameters for script %s", commandList.get(i).name)
            }
            // 
            return true
        }

        fun Execute(win: idWindow?) {
            if (handler != null) {
                handler.run(win, parms)
            }
        }

        fun FixupParms(win: idWindow?) {
            if (handler === Script_Set.getInstance()) {
                var precacheBackground = false
                var precacheSounds = false
                var str = (parms.get(0).`var` as idWinStr?)!!
                var dest = win.GetWinVarByName(str.data.toString(), true)
                if (dest != null) {
//			delete parms[0].var;
                    parms.get(0).`var` = dest
                    parms.get(0).own = false
                    if (dest is idWinBackground) { //TODO:cast null comparison. EDIT: not possible with static typing, use "instanceof" instead.
                        precacheBackground = true
                    }
                } else if (idStr.Companion.Icmp(str.c_str(), "cmd") == 0) {
                    precacheSounds = true
                }
                val parmCount = parms.Num()
                for (i in 1 until parmCount) {
                    str = parms.get(i).`var`
                    if (idStr.Companion.Icmpn(str.data, "gui::", 5) == 0) {

                        //  always use a string here, no point using a float if it is one
                        //  FIXME: This creates duplicate variables, while not technically a problem since they
                        //  are all bound to the same guiDict, it does consume extra memory and is generally a bad thing
                        val defvar = idWinStr()
                        defvar.Init(str.data.toString(), win)
                        win.AddDefinedVar(defvar)
                        //				delete parms[i].var;
                        parms.get(0).`var` = defvar
                        parms.get(0).own = false

                        //dest = win.GetWinVarByName(*str, true);
                        //if (dest) {
                        //	delete parms[i].var;
                        //	parms[i].var = dest;
                        //	parms[i].own = false;
                        //}
                        // 
                    } else if (str == '$') {
                        // 
                        //  dont include the $ when asking for variable
                        dest = win.GetGui().GetDesktop().GetWinVarByName(str.c_str().substring(1), true)
                        // 					
                        if (dest != null) {
//					delete parms[i].var;
                            parms.get(i).`var` = dest
                            parms.get(i).own = false
                        }
                    } else if (idStr.Companion.Cmpn(str.c_str(), Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0) {
                        str.Set(Common.common.GetLanguageDict().GetString(str.c_str()))
                    } else if (precacheBackground) {
                        val mat = DeclManager.declManager.FindMaterial(str.c_str())
                        mat.SetSort(Material.SS_GUI.toFloat())
                    } else if (precacheSounds) {
                        // Search for "play <...>"
                        val token = idToken()
                        val parser =
                            idParser(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
                        parser.LoadMemory(str.c_str(), str.Length(), "command")
                        while (parser.ReadToken(token)) {
                            if (token.Icmp("play") == 0) {
                                if (parser.ReadToken(token) && !token.IsEmpty()) {
                                    DeclManager.declManager.FindSound(token)
                                }
                            }
                        }
                    }
                }
            } else if (handler === Script_Transition.getInstance()) {
                if (parms.Num() < 4) {
                    Common.common.Warning(
                        "Window %s in gui %s has a bad transition definition",
                        win.GetName(),
                        win.GetGui().GetSourceFile()
                    )
                }
                var str = (parms.get(0).`var` as idWinStr?)!!

                // 
                val destOwner = arrayOf<drawWin_t?>(null)
                var dest = win.GetWinVarByName(str.data.toString(), true, destOwner)
                // 
                if (dest != null) {
//			delete parms[0].var;
                    parms.get(0).`var` = dest
                    parms.get(0).own = false
                } else {
                    Common.common.Warning(
                        "Window %s in gui %s: a transition does not have a valid destination var %s",
                        win.GetName(),
                        win.GetGui().GetSourceFile(),
                        str.c_str()
                    )
                }

                // 
                //  support variables as parameters		
                var c: Int
                c = 1
                while (c < 3) {
                    str = parms.get(c).`var`
                    val v4 = idWinVec4()
                    parms.get(c).`var` = v4
                    parms.get(c).own = true
                    val owner = arrayOf<drawWin_t?>(null)
                    dest = if (str.data.oGet(0) == '$') {
                        win.GetWinVarByName(str.c_str().substring(1), true, owner)
                    } else {
                        null
                    }
                    if (dest != null) {
                        var ownerparent: idWindow?
                        var destparent: idWindow?
                        if (owner[0] != null) {
                            ownerparent =
                                if (owner[0].simp != null) owner[0].simp.GetParent() else owner[0].win.GetParent()
                            destparent =
                                if (destOwner[0].simp != null) destOwner[0].simp.GetParent() else destOwner[0].win.GetParent()

                            // If its the rectangle they are referencing then adjust it 
                            if (ownerparent != null && destparent != null && dest === if (owner[0].simp != null) owner[0].simp.GetWinVarByName(
                                    "rect"
                                ) else owner[0].win.GetWinVarByName("rect")
                            ) {
                                val rect = idRectangle()
                                rect.oSet((dest as idWinRectangle).data)
                                ownerparent.ClientToScreen(rect)
                                destparent.ScreenToClient(rect)
                                v4.oSet(rect.ToVec4())
                            } else {
                                v4.Set(dest.c_str())
                            }
                        } else {
                            v4.Set(dest.c_str())
                        }
                    } else {
                        v4.Set(str.data)
                    }
                    c++
                }
                // 
            } else {
                val c = parms.Num()
                for (i in 0 until c) {
                    parms.get(i).`var`.Init(parms.get(i).`var`.c_str(), win)
                }
            }
        }

        fun  /*size_t*/Size(): Int {
            var sz = 4
            for (i in 0 until parms.Num()) {
                sz += parms.get(i).`var`.Size()
            }
            return sz
        }

        fun WriteToSaveGame(savefile: idFile?) {
            var i: Int
            if (ifList != null) {
                ifList.WriteToSaveGame(savefile)
            }
            if (elseList != null) {
                elseList.WriteToSaveGame(savefile)
            }
            savefile.WriteInt(conditionReg)
            i = 0
            while (i < parms.Num()) {
                if (parms.get(i).own) {
                    parms.get(i).`var`.WriteToSaveGame(savefile)
                }
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile?) {
            var i: Int
            if (ifList != null) {
                ifList.ReadFromSaveGame(savefile)
            }
            if (elseList != null) {
                elseList.ReadFromSaveGame(savefile)
            }
            conditionReg = savefile.ReadInt()
            i = 0
            while (i < parms.Num()) {
                if (parms.get(i).own) {
                    parms.get(i).`var`.ReadFromSaveGame(savefile)
                }
                i++
            }
        } //protected	void (*handler) (idWindow *window, idList<idGSWinVar> *src);

        //
        //
        init {
            conditionReg = -1
            handler = null
            parms = idList()
            parms.SetGranularity(2)
        }
    }

    internal class idGuiScriptList {
        val list: idList<idGuiScript?>?

        // ~idGuiScriptList() { list.DeleteContents(true); };
        fun Execute(win: idWindow?) {
            val c = list.Num()
            for (i in 0 until c) {
                val gs = list.get(i)!!
                if (gs.conditionReg >= 0) {
                    if (win.HasOps()) {
                        val f = win.EvalRegs(gs.conditionReg)
                        if (f != 0f) {
                            if (gs.ifList != null) {
                                win.RunScriptList(gs.ifList)
                            }
                        } else if (gs.elseList != null) {
                            win.RunScriptList(gs.elseList)
                        }
                    }
                }
                gs.Execute(win)
            }
        }

        fun Append(gs: idGuiScript?) {
            list.Append(gs)
        }

        fun  /*size_t*/Size(): Int {
            var sz = 4
            for (i in 0 until list.Num()) {
                sz += list.get(i).Size()
            }
            return sz
        }

        fun FixupParms(win: idWindow?) {
            val c = list.Num()
            for (i in 0 until c) {
                val gs = list.get(i)
                gs.FixupParms(win)
                if (gs.ifList != null) {
                    gs.ifList.FixupParms(win)
                }
                if (gs.elseList != null) {
                    gs.elseList.FixupParms(win)
                }
            }
        }

        fun ReadFromDemoFile(f: idDemoFile?) {}
        fun WriteToDemoFile(f: idDemoFile?) {}
        fun WriteToSaveGame(savefile: idFile?) {
            var i: Int
            i = 0
            while (i < list.Num()) {
                list.get(i).WriteToSaveGame(savefile)
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile?) {
            var i: Int
            i = 0
            while (i < list.Num()) {
                list.get(i).ReadFromSaveGame(savefile)
                i++
            }
        }

        init {
            list = idList()
            list.SetGranularity(4)
        }
    }

    internal abstract class Handler {
        abstract fun run(window: idWindow?, src: idList<idGSWinVar?>?)
    }

    /*
     =========================
     Script_Set
     =========================
     */
    internal class Script_Set private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            scriptSetTotal++
            var key: String
            var `val`: String?
            var dest: idWinStr? = TempDump.dynamic_cast(idWinStr::class.java, src.get(0).`var`)
            if (dest != null) {
                if (idStr.Companion.Icmp(dest.data, "cmd") == 0) {
                    dest = src.get(1).`var`
                    val parmCount = src.Num()
                    if (parmCount > 2) {
                        `val` = dest.c_str()
                        var i = 2
                        while (i < parmCount) {
                            `val` += " \""
                            `val` += src.get(i).`var`.c_str()
                            `val` += "\""
                            i++
                        }
                        window.AddCommand(`val`)
                    } else {
                        window.AddCommand(dest.data.toString())
                    }
                    return
                }
            }
            src.get(0).`var`.Set(src.get(1).`var`.c_str())
            src.get(0).`var`.SetEval(false)
        }

        companion object {
            private val instance: Handler? = Script_Set()
            private var scriptSetTotal = 0
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_SetFocus
     =========================
     */
    internal class Script_SetFocus private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            val parm = src.get(0).`var` as idWinStr?
            if (parm != null) {
                val win = window.GetGui().GetDesktop().FindChildByName(parm.data.toString())
                if (win != null && win.win != null) {
                    window.SetFocus(win.win)
                }
            }
        }

        companion object {
            private val instance: Handler? = Script_SetFocus()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_ShowCursor
     =========================
     */
    internal class Script_ShowCursor private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            val parm = src.get(0).`var` as idWinStr?
            if (parm != null) {
                if (parm.data.toString().toInt() != 0) {
                    window.GetGui().GetDesktop().ClearFlag(Window.WIN_NOCURSOR)
                } else {
                    window.GetGui().GetDesktop().SetFlag(Window.WIN_NOCURSOR)
                }
            }
        }

        companion object {
            private val instance: Handler? = Script_ShowCursor()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_RunScript

     run scripts must come after any set cmd set's in the script
     =========================
     */
    internal class Script_RunScript private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            val parm = src.get(0).`var` as idWinStr?
            if (parm != null) {
                var str: String? = window.cmd.toString()
                str += " ; runScript "
                str += parm.c_str()
                window.cmd.oSet(str)
            }
        }

        companion object {
            private val instance: Handler? = Script_RunScript()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_LocalSound
     =========================
     */
    internal class Script_LocalSound private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            val parm = src.get(0).`var` as idWinStr?
            if (parm != null) {
                Session.Companion.session.sw.PlayShaderDirectly(parm.data.toString())
            }
        }

        companion object {
            private val instance: Handler? = Script_LocalSound()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_EvalRegs
     =========================
     */
    internal class Script_EvalRegs private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            window.EvalRegs(-1, true)
        }

        companion object {
            private val instance: Handler? = Script_EvalRegs()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_EndGame
     =========================
     */
    internal class Script_EndGame private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            idLib.cvarSystem.SetCVarBool("g_nightmare", true)
            CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "disconnect\n")
        }

        companion object {
            private val instance: Handler? = Script_EndGame()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_ResetTime
     =========================
     */
    internal class Script_ResetTime private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            var parm = src.get(0).`var` as idWinStr?
            var win: drawWin_t? = null
            if (parm != null && src.Num() > 1) {
                win = window.GetGui().GetDesktop().FindChildByName(parm.data.toString())
                parm = src.get(1).`var`
            }
            if (win != null && win.win != null) {
                win.win.ResetTime(parm.data.toString().toInt())
                win.win.EvalRegs(-1, true)
            } else {
                window.ResetTime(parm.data.toString().toInt())
                window.EvalRegs(-1, true)
            }
        }

        companion object {
            private val instance: Handler? = Script_ResetTime()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_ResetCinematics
     =========================
     */
    internal class Script_ResetCinematics private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            window.ResetCinematics()
        }

        companion object {
            private val instance: Handler? = Script_ResetCinematics()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }

    /*
     =========================
     Script_Transition
     =========================
     */
    internal class Script_Transition private constructor() : Handler() {
        override fun run(window: idWindow?, src: idList<idGSWinVar?>?) {
            // transitions always affect rect or vec4 vars
            if (src.Num() >= 4) {
                var rect: idWinRectangle? = null
                val vec4 = TempDump.dynamic_cast(idWinVec4::class.java, src.get(0).`var`) as idWinVec4
                // 
                //  added float variable
                var `val`: idWinFloat? = null
                // 
                if (null == vec4) {
                    rect = TempDump.dynamic_cast(idWinRectangle::class.java, src.get(0).`var`)
                    // 
                    //  added float variable					
                    if (null == rect) {
                        `val` = src.get(0).`var`
                    }
                    // 
                }
                val from = TempDump.dynamic_cast(idWinVec4::class.java, src.get(1).`var`) as idWinVec4
                val to = TempDump.dynamic_cast(idWinVec4::class.java, src.get(2).`var`) as idWinVec4
                val timeStr = TempDump.dynamic_cast(idWinStr::class.java, src.get(3).`var`) as idWinStr
                // 
                //  added float variable					
                if (!((vec4 != null || rect != null || `val` != null)
                            && from != null && to != null && timeStr != null)
                ) {
                    // 
                    Common.common.Warning(
                        "Bad transition in gui %s in window %s\n",
                        window.GetGui().GetSourceFile(),
                        window.GetName()
                    )
                    return
                }
                val time = TempDump.atoi(timeStr.data.toString())
                var ac = 0.0f
                var dc = 0.0f
                if (src.Num() > 4) {
                    val acv = TempDump.dynamic_cast(idWinStr::class.java, src.get(4).`var`) as idWinStr
                    val dcv = TempDump.dynamic_cast(idWinStr::class.java, src.get(5).`var`) as idWinStr
                    assert(acv != null && dcv != null)
                    ac = acv.data.toString().toFloat()
                    dc = dcv.data.toString().toFloat()
                }
                if (vec4 != null) {
                    vec4.SetEval(false)
                    window.AddTransition(vec4, from.data, to.data, time, ac, dc)
                    // 
                    //  added float variable					
                } else if (`val` != null) {
                    `val`.SetEval(false)
                    window.AddTransition(`val`, from.data, to.data, time, ac, dc)
                    // 
                } else {
                    rect.SetEval(false)
                    window.AddTransition(rect, from.data, to.data, time, ac, dc)
                }
                window.StartTransition()
            }
        }

        companion object {
            private val instance: Handler? = Script_Transition()
            fun getInstance(): Handler? {
                return instance
            }
        }
    }
}