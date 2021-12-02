package neo.ui

import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.KeyInput
import neo.idlib.Lib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.idStrList
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idMultiWinVar
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar

/**
 *
 */
class ChoiceWindow {
    class idChoiceWindow : idWindow {
        private var choiceType = 0
        private val choiceVals: idWinStr? = idWinStr()
        private val choices: idStrList? = idStrList()
        private val choicesStr: idWinStr? = idWinStr()
        private var currentChoice = 0
        private var cvar: idCVar? = null
        private val cvarStr: idWinStr? = idWinStr()

        //
        private val guiStr: idWinStr? = idWinStr()
        private val latchedChoices: idStr? = idStr()
        private val latchedVals: idStr? = idStr()

        //
        private val liveUpdate: idWinBool? = idWinBool()
        private val updateGroup: idWinStr? = idWinStr()
        private val updateStr: idMultiWinVar? = idMultiWinVar()
        private val values: idStrList? = idStrList()

        //
        //
        constructor(gui: idUserInterfaceLocal?) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        //	virtual				~idChoiceWindow();
        //
        override fun HandleEvent(event: sysEvent_s?, updateVisuals: BooleanArray?): String? {
            val key: Int
            var runAction = false
            var runAction2 = false
            if (event.evType == sysEventType_t.SE_KEY) {
                key = event.evValue
                if (key == KeyInput.K_RIGHTARROW || key == KeyInput.K_KP_RIGHTARROW || key == KeyInput.K_MOUSE1) {
                    // never affects the state, but we want to execute script handlers anyway
                    if (0 == event.evValue2) {
                        RunScript(TempDump.etoi(ON.ON_ACTIONRELEASE))
                        return cmd.toString()
                    }
                    currentChoice++
                    if (currentChoice >= choices.size()) {
                        currentChoice = 0
                    }
                    runAction = true
                }
                if (key == KeyInput.K_LEFTARROW || key == KeyInput.K_KP_LEFTARROW || key == KeyInput.K_MOUSE2) {
                    // never affects the state, but we want to execute script handlers anyway
                    if (0 == event.evValue2) {
                        RunScript(TempDump.etoi(ON.ON_ACTIONRELEASE))
                        return cmd.toString()
                    }
                    currentChoice--
                    if (currentChoice < 0) {
                        currentChoice = choices.size() - 1
                    }
                    runAction = true
                }
                if (0 == event.evValue2) {
                    // is a key release with no action catch
                    return ""
                }
            } else if (event.evType == sysEventType_t.SE_CHAR) {
                key = event.evValue
                var potentialChoice = -1
                for (i in 0 until choices.size()) {
                    if (key.uppercaseChar() == choices.get(i).oGet(0).uppercaseChar()) {
                        if (i < currentChoice && potentialChoice < 0) {
                            potentialChoice = i
                        } else if (i > currentChoice) {
                            potentialChoice = -1
                            currentChoice = i
                            break
                        }
                    }
                }
                if (potentialChoice >= 0) {
                    currentChoice = potentialChoice
                }
                runAction = true
                runAction2 = true
            } else {
                return ""
            }
            if (runAction) {
                RunScript(TempDump.etoi(ON.ON_ACTION))
            }
            if (choiceType == 0) {
                cvarStr.Set(Str.va("%d", currentChoice))
            } else if (values.size() != 0) {
                cvarStr.Set(values.get(currentChoice))
            } else {
                cvarStr.Set(choices.get(currentChoice))
            }
            UpdateVars(false)
            if (runAction2) {
                RunScript(TempDump.etoi(ON.ON_ACTIONRELEASE))
            }
            return cmd.toString()
        }

        override fun PostParse() {
            super.PostParse()
            UpdateChoicesAndVals()
            InitVars()
            UpdateChoice()
            UpdateVars(false)
            flags = flags or Window.WIN_CANFOCUS
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var color = foreColor.oCastIdVec4()
            UpdateChoicesAndVals()
            UpdateChoice()

            // FIXME: It'd be really cool if textAlign worked, but a lot of the guis have it set wrong because it used to not work
            textAlign = 0.toChar()
            if (textShadow.code != 0) {
                val shadowText = choices.get(currentChoice)
                val shadowRect = idRectangle(textRect)
                shadowText.RemoveColors()
                shadowRect.x += textShadow.code.toFloat()
                shadowRect.y += textShadow.code.toFloat()
                dc.DrawText(shadowText, textScale.data, textAlign.code, Lib.Companion.colorBlack, shadowRect, false, -1)
            }
            if (hover && TempDump.NOT(noEvents) && Contains(gui.CursorX(), gui.CursorY())) {
                color = hoverColor.oCastIdVec4()
            } else {
                hover = false
            }
            if (flags and Window.WIN_FOCUS != 0) {
                color = hoverColor.oCastIdVec4()
            }
            dc.DrawText(choices.get(currentChoice), textScale.data, textAlign.code, color, textRect, false, -1)
        }

        override fun Activate(activate: Boolean, act: idStr?) {
            super.Activate(activate, act)
            if (activate) {
                // sets the gui state based on the current choice the window contains
                UpdateChoice()
            }
        }

        override fun GetWinVarByName(
            _name: String?,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            if (idStr.Companion.Icmp(_name, "choices") == 0) {
                return choicesStr
            }
            if (idStr.Companion.Icmp(_name, "values") == 0) {
                return choiceVals
            }
            if (idStr.Companion.Icmp(_name, "cvar") == 0) {
                return cvarStr
            }
            if (idStr.Companion.Icmp(_name, "gui") == 0) {
                return guiStr
            }
            if (idStr.Companion.Icmp(_name, "liveUpdate") == 0) {
                return liveUpdate
            }
            return if (idStr.Companion.Icmp(_name, "updateGroup") == 0) {
                updateGroup
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        override fun RunNamedEvent(eventName: String?) {
            val event: idStr
            val group: idStr?
            if (0 == idStr.Companion.Cmpn(eventName, "cvar read ", 10)) {
                event = idStr(eventName)
                group = event.Mid(10, event.Length() - 10)
                if (0 == group.Cmp(updateGroup.data)) {
                    UpdateVars(true, true)
                }
            } else if (0 == idStr.Companion.Cmpn(eventName, "cvar write ", 11)) {
                event = idStr(eventName)
                group = event.Mid(11, event.Length() - 11)
                if (0 == group.Cmp(updateGroup.data)) {
                    UpdateVars(false, true)
                }
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser?): Boolean {
            if (idStr.Companion.Icmp(_name, "choicetype") == 0) {
                choiceType = src.ParseInt()
                return true
            }
            if (idStr.Companion.Icmp(_name, "currentchoice") == 0) {
                currentChoice = src.ParseInt()
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            currentChoice = 0
            choiceType = 0
            cvar = null
            liveUpdate.data = true
            choices.clear()
        }

        private fun UpdateChoice() {
            if (0 == updateStr.Num()) {
                return
            }
            UpdateVars(true)
            updateStr.Update()
            if (choiceType == 0) {
                // ChoiceType 0 stores current as an integer in either cvar or gui
                // If both cvar and gui are defined then cvar wins, but they are both updated
                if (updateStr.get(0).NeedsUpdate()) {
                    currentChoice = try {
                        updateStr.get(0).c_str().toInt()
                    } catch (e: NumberFormatException) {
                        0
                    }
                }
                ValidateChoice()
            } else {
                // ChoiceType 1 stores current as a cvar string
                val c = if (values.size() != 0) values.size() else choices.size()
                var i: Int
                i = 0
                while (i < c) {
                    if (idStr.Companion.Icmp(
                            cvarStr.c_str(),
                            (if (values.size() != 0) values.get(i) else choices.get(i)).toString()
                        ) == 0
                    ) {
                        break
                    }
                    i++
                }
                if (i == c) {
                    i = 0
                }
                currentChoice = i
                ValidateChoice()
            }
        }

        private fun ValidateChoice() {
            if (currentChoice < 0 || currentChoice >= choices.size()) {
                currentChoice = 0
            }
            if (choices.size() == 0) {
                choices.add("No Choices Defined")
            }
        }

        private fun InitVars() {
            if (cvarStr.Length() != 0) {
                cvar = CVarSystem.cvarSystem.Find(cvarStr.c_str())
                if (null == cvar) {
                    Common.common.Warning(
                        "idChoiceWindow::InitVars: gui '%s' window '%s' references undefined cvar '%s'",
                        gui.GetSourceFile(),
                        name,
                        cvarStr.c_str()
                    )
                    return
                }
                updateStr.Append(cvarStr)
            }
            if (guiStr.Length() != 0) {
                updateStr.Append(guiStr)
            }
            updateStr.SetGuiInfo(gui.GetStateDict())
            updateStr.Update()
        }

        // true: read the updated cvar from cvar system, gui from dict
        // false: write to the cvar system, to the gui dict
        // force == true overrides liveUpdate 0
        private fun UpdateVars(read: Boolean, force: Boolean = false /*= false*/) {
            if (force || liveUpdate.data) {
                if (cvar != null && cvarStr.NeedsUpdate()) {
                    if (read) {
                        cvarStr.Set(cvar.GetString())
                    } else {
                        cvar.SetString(cvarStr.c_str())
                    }
                }
                if (!read && guiStr.NeedsUpdate()) {
                    guiStr.Set(Str.va("%d", currentChoice))
                }
            }
        }

        private fun UpdateChoicesAndVals() {
            val token = idToken()
            val str2 = idStr()
            val str3 = idStr()
            val src = idLexer()
            if (latchedChoices.Icmp(choicesStr.data) != 0) {
                choices.clear()
                src.FreeSource()
                src.SetFlags(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
                src.LoadMemory(choicesStr.data, choicesStr.Length(), "<ChoiceList>")
                if (src.IsLoaded()) {
                    while (src.ReadToken(token)) {
                        if (token == ";") {
                            if (str2.Length() != 0) {
                                str2.StripTrailingWhitespace()
                                str2.set(Common.common.GetLanguageDict().GetString(str2))
                                choices.add(str2)
                                str2.set("")
                            }
                            continue
                        }
                        str2.Append(token)
                        str2.Append(" ")
                    }
                    if (str2.Length() != 0) {
                        str2.StripTrailingWhitespace()
                        choices.add(str2)
                    }
                }
                latchedChoices.set(choicesStr.c_str())
            }
            if (choiceVals.Length() != 0 && latchedVals.Icmp(choiceVals.data) != 0) {
                values.clear()
                src.FreeSource()
                src.SetFlags(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
                src.LoadMemory(choiceVals.data, choiceVals.Length(), "<ChoiceVals>")
                str2.set("")
                var negNum = false
                if (src.IsLoaded()) {
                    while (src.ReadToken(token)) {
                        if (token == "-") {
                            negNum = true
                            continue
                        }
                        if (token == ";") {
                            if (str2.Length() != 0) {
                                str2.StripTrailingWhitespace()
                                values.add(str2)
                                str2.set("") //TODO:what Da fuk? EDIT:yes yes, vision gets blury at 4 in teh morning!
                            }
                            continue
                        }
                        if (negNum) {
                            str2.plusAssign("-")
                            negNum = false
                        }
                        str2.plusAssign(token)
                        str2.plusAssign(" ")
                    }
                    if (str2.Length() != 0) {
                        str2.StripTrailingWhitespace()
                        values.add(str2)
                    }
                }
                if (choices.size() != values.size()) {
                    Common.common.Warning(
                        "idChoiceWindow:: gui '%s' window '%s' has value count unequal to choices count",
                        gui.GetSourceFile(),
                        name
                    )
                }
                latchedVals.set(choiceVals.c_str())
            }
        }
    }
}