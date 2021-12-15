package neo.framework

import neo.Renderer.RenderSystem
import neo.TempDump
import neo.TempDump.void_callback
import neo.framework.KeyInput.idKeyInput
import neo.idlib.*
import neo.idlib.Lib.idException
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.sys.win_main

/**
 *
 */
object EditField {
    /*
     ===============================================================================

     Edit field

     ===============================================================================
     */
    const val MAX_EDIT_LINE = 256
    var globalAutoComplete: autoComplete_s? = null

    internal class autoComplete_s {
        var completionString: CharArray? = CharArray(EditField.MAX_EDIT_LINE)
        var currentMatch: CharArray? = CharArray(EditField.MAX_EDIT_LINE)
        var findMatchIndex = 0
        var length = 0
        var matchCount = 0
        var matchIndex = 0
        var valid = false
    } /*autoComplete_t*/

    class idEditField {
        private var autoComplete: autoComplete_s?
        private val buffer: CharArray = CharArray(EditField.MAX_EDIT_LINE)
        private var cursor = 0
        private var scroll = 0
        private var widthInChars = 0

        //public					~idEditField();
        fun Clear() {
            buffer.get(0) = 0
            cursor = 0
            scroll = 0
            autoComplete.length = 0
            autoComplete.valid = false
        }

        fun SetWidthInChars(w: Int) {
            assert(w <= EditField.MAX_EDIT_LINE)
            widthInChars = w
        }

        fun SetCursor(c: Int) {
            assert(c <= EditField.MAX_EDIT_LINE)
            cursor = c
        }

        fun GetCursor(): Int {
            return cursor
        }

        fun ClearAutoComplete() {
            if (autoComplete.length > 0 && autoComplete.length <= TempDump.ctos(buffer).length) {
                buffer.get(autoComplete.length) = '\u0000'
                if (cursor > autoComplete.length) {
                    cursor = autoComplete.length
                }
            }
            autoComplete.length = 0
            autoComplete.valid = false
        }

        fun GetAutoCompleteLength(): Int {
            return autoComplete.length
        }

        @Throws(idException::class)
        fun AutoComplete() {
            val completionArgString = CharArray(EditField.MAX_EDIT_LINE)
            val args = CmdArgs.idCmdArgs()
            val findMatches = FindMatches.getInstance()
            val findIndexMatch = FindIndexMatch.getInstance()
            val printMatches = PrintMatches.getInstance()
            if (!autoComplete.valid) {
                args.TokenizeString(TempDump.ctos(buffer), false)
                idStr.Companion.Copynz(autoComplete.completionString, args.Argv(0), autoComplete.completionString.size)
                idStr.Companion.Copynz(completionArgString, args.Args(), completionArgString.size)
                autoComplete.matchCount = 0
                autoComplete.matchIndex = 0
                autoComplete.currentMatch.get(0) = 0
                if (TempDump.strLen(autoComplete.completionString) == 0) {
                    return
                }
                EditField.globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(findMatches)
                CVarSystem.cvarSystem.CommandCompletion(findMatches)
                autoComplete = EditField.globalAutoComplete
                if (autoComplete.matchCount == 0) {
                    return  // no matches
                }

                // when there's only one match or there's an argument
                if (autoComplete.matchCount == 1 || completionArgString[0] != '\u0000') {

                    /// try completing arguments
                    idStr.Companion.Append(autoComplete.completionString, autoComplete.completionString.size, " ")
                    idStr.Companion.Append(
                        autoComplete.completionString,
                        autoComplete.completionString.size,
                        TempDump.ctos(completionArgString)
                    )
                    autoComplete.matchCount = 0
                    EditField.globalAutoComplete = autoComplete
                    CmdSystem.cmdSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), findMatches)
                    CVarSystem.cvarSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), findMatches)
                    autoComplete = EditField.globalAutoComplete
                    idStr.Companion.snPrintf(buffer, buffer.size, "%s", *autoComplete.currentMatch)
                    if (autoComplete.matchCount == 0) {
                        // no argument matches
                        idStr.Companion.Append(buffer, buffer.size, " ")
                        idStr.Companion.Append(buffer, buffer.size, TempDump.ctos(completionArgString))
                        SetCursor(TempDump.strLen(buffer))
                        return
                    }
                } else {

                    // multiple matches, complete to shortest
                    idStr.Companion.snPrintf(buffer, buffer.size, "%s", TempDump.ctos(autoComplete.currentMatch))
                    if (TempDump.strLen(completionArgString) != 0) {
                        idStr.Companion.Append(buffer, buffer.size, " ")
                        idStr.Companion.Append(buffer, buffer.size, TempDump.ctos(completionArgString))
                    }
                }
                autoComplete.length = TempDump.strLen(buffer)
                autoComplete.valid = autoComplete.matchCount != 1
                SetCursor(autoComplete.length)
                Common.common.Printf("]%s\n", TempDump.ctos(buffer))

                // run through again, printing matches
                EditField.globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(printMatches)
                CmdSystem.cmdSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), printMatches)
                CVarSystem.cvarSystem.CommandCompletion(PrintCvarMatches.getInstance())
                CmdSystem.cmdSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), printMatches)
            } else if (autoComplete.matchCount != 1) {

                // get the next match and show instead
                autoComplete.matchIndex++
                if (autoComplete.matchIndex == autoComplete.matchCount) {
                    autoComplete.matchIndex = 0
                }
                autoComplete.findMatchIndex = 0
                EditField.globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(findIndexMatch)
                CmdSystem.cmdSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), findIndexMatch)
                CVarSystem.cvarSystem.CommandCompletion(findIndexMatch)
                CmdSystem.cmdSystem.ArgCompletion(TempDump.ctos(autoComplete.completionString), findIndexMatch)
                autoComplete = EditField.globalAutoComplete

                // and print it
                idStr.Companion.snPrintf(buffer, buffer.size, TempDump.ctos(autoComplete.currentMatch))
                if (autoComplete.length > TempDump.strLen(buffer)) {
                    autoComplete.length = TempDump.strLen(buffer)
                }
                SetCursor(autoComplete.length)
            }
        }

        fun CharEvent(ch: Int) {
            val len: Int
            if (ch == 'v' - 'a' + 1) {    // ctrl-v is paste
                Paste()
                return
            }
            if (ch == 'c' - 'a' + 1) {    // ctrl-c clears the field
                Clear()
                return
            }
            len = TempDump.strLen(buffer)
            if (ch == 'h' - 'a' + 1 || ch == KeyInput.K_BACKSPACE) {    // ctrl-h is backspace
                if (cursor > 0) {
//			memmove( buffer + cursor - 1, buffer + cursor, len + 1 - cursor );
                    System.arraycopy(buffer, cursor, buffer, cursor - 1, len + 1 - cursor)
                    cursor--
                    if (cursor < scroll) {
                        scroll--
                    }
                }
                return
            }
            if (ch == 'a' - 'a' + 1) {    // ctrl-a is home
                cursor = 0
                scroll = 0
                return
            }
            if (ch == 'e' - 'a' + 1) {    // ctrl-e is end
                cursor = len
                scroll = cursor - widthInChars
                return
            }

            //
            // ignore any other non printable chars
            //
            if (ch < 32 || ch > 125) {
                return
            }
            if (idKeyInput.GetOverstrikeMode()) {
                if (cursor == EditField.MAX_EDIT_LINE - 1) {
                    return
                }
                buffer.get(cursor) = ch.toChar()
                cursor++
            } else {    // insert mode
                if (len == EditField.MAX_EDIT_LINE - 1) {
                    return  // all full
                }
                //		memmove( buffer + cursor + 1, buffer + cursor, len + 1 - cursor );
                System.arraycopy(buffer, cursor, buffer, cursor + 1, len + 1 - cursor)
                buffer.get(cursor) = ch.toChar()
                cursor++
            }
            if (cursor >= widthInChars) {
                scroll++
            }
            if (cursor == len + 1) {
                buffer.get(cursor) = 0
            }
        }

        fun KeyDownEvent(key: Int) {
            val len: Int

            // shift-insert is paste
            if ((key == KeyInput.K_INS || key == KeyInput.K_KP_INS) && idKeyInput.IsDown(KeyInput.K_SHIFT)) {
                ClearAutoComplete()
                Paste()
                return
            }
            len = TempDump.strLen(buffer)
            if (key == KeyInput.K_DEL) {
                if (autoComplete.length != 0) {
                    ClearAutoComplete()
                } else if (cursor < len) {
//			memmove( buffer + cursor, buffer + cursor + 1, len - cursor );
                    System.arraycopy(buffer, cursor + 1, buffer, cursor, len - cursor)
                }
                return
            }
            if (key == KeyInput.K_RIGHTARROW) {
                if (idKeyInput.IsDown(KeyInput.K_CTRL)) {
                    // skip to next word
                    while (cursor < len && buffer.get(cursor) != ' ') {
                        cursor++
                    }
                    while (cursor < len && buffer.get(cursor) == ' ') {
                        cursor++
                    }
                } else {
                    cursor++
                }
                if (cursor > len) {
                    cursor = len
                }
                if (cursor >= scroll + widthInChars) {
                    scroll = cursor - widthInChars + 1
                }
                if (autoComplete.length > 0) {
                    autoComplete.length = cursor
                }
                return
            }
            if (key == KeyInput.K_LEFTARROW) {
                if (idKeyInput.IsDown(KeyInput.K_CTRL)) {
                    // skip to previous word
                    while (cursor > 0 && buffer.get(cursor - 1) == ' ') {
                        cursor--
                    }
                    while (cursor > 0 && buffer.get(cursor - 1) != ' ') {
                        cursor--
                    }
                } else {
                    cursor--
                }
                if (cursor < 0) {
                    cursor = 0
                }
                if (cursor < scroll) {
                    scroll = cursor
                }
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                }
                return
            }
            if (key == KeyInput.K_HOME || key.lowercaseChar() == 'a' && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                cursor = 0
                scroll = 0
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                    autoComplete.valid = false
                }
                return
            }
            if (key == KeyInput.K_END || key.lowercaseChar() == 'e' && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                cursor = len
                if (cursor >= scroll + widthInChars) {
                    scroll = cursor - widthInChars + 1
                }
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                    autoComplete.valid = false
                }
                return
            }
            if (key == KeyInput.K_INS) {
                idKeyInput.SetOverstrikeMode(!idKeyInput.GetOverstrikeMode())
                return
            }

            // clear autocompletion buffer on normal key input
            if (key != KeyInput.K_CAPSLOCK && key != KeyInput.K_ALT && key != KeyInput.K_CTRL && key != KeyInput.K_SHIFT) {
                ClearAutoComplete()
            }
        }

        fun Paste() {
            val cbd: String?
            val pasteLen: Int
            var i: Int
            cbd = win_main.Sys_GetClipboardData()
            if (null == cbd) {
                return
            }

            // send as if typed, so insert / overstrike works properly
            pasteLen = cbd.length
            i = 0
            while (i < pasteLen) {
                CharEvent(cbd[i].code)
                i++
            }

//            Heap.Mem_Free(cbd);
        }

        fun GetBuffer(): CharArray {
            return buffer
        }

        @Throws(idException::class)
        fun Draw(x: Int, y: Int, width: Int, showCursor: Boolean, shader: idMaterial?) {
            val len: Int
            var drawLen: Int
            var prestep: Int
            val cursorChar: Int
            val str = CharArray(EditField.MAX_EDIT_LINE)
            val size: Int
            size = RenderSystem.SMALLCHAR_WIDTH
            drawLen = widthInChars
            len = TempDump.strLen(buffer) + 1

            // guarantee that cursor will be visible
            if (len <= drawLen) {
                prestep = 0
            } else {
                if (scroll + drawLen > len) {
                    scroll = len - drawLen
                    if (scroll < 0) {
                        scroll = 0
                    }
                }
                prestep = scroll

                // Skip color code
                if (idStr.Companion.IsColor(TempDump.ctos(buffer).substring(prestep))) {
                    prestep += 2
                }
                if (prestep > 0 && idStr.Companion.IsColor(TempDump.ctos(buffer).substring(prestep - 1))) {
                    prestep++
                }
            }
            if (prestep + drawLen > len) {
                drawLen = len - prestep
            }

            // extract <drawLen> characters from the field at <prestep>
            if (drawLen >= EditField.MAX_EDIT_LINE) {
                Common.common.Error("drawLen >= MAX_EDIT_LINE")
            }

//	memcpy( str, buffer + prestep, drawLen );
            System.arraycopy(buffer, prestep, str, 0, drawLen)
            str[drawLen] = 0

            // draw it
            RenderSystem.renderSystem.DrawSmallStringExt(x, y, str, Lib.Companion.colorWhite, false, shader)

            // draw the cursor
            if (!showCursor) {
                return
            }
            if (Common.com_ticNumber shr 4 and 1 == 1) {
                return  // off blink
            }
            cursorChar = if (idKeyInput.GetOverstrikeMode()) {
                11
            } else {
                10
            }

            // Move the cursor back to account for color codes
            var i = 0
            while (i < cursor) {
                if (idStr.Companion.IsColor(TempDump.ctos(str[i]))) { //TODO:check
                    i++
                    prestep += 2
                }
                i++
            }
            RenderSystem.renderSystem.DrawSmallChar(x + (cursor - prestep) * size, y, cursorChar, shader)
        }

        fun SetBuffer(buf: String?) {
            Clear()
            idStr.Companion.Copynz(buffer, buf, buffer.size)
            SetCursor(TempDump.strLen(buffer))
        }

        //
        //
        init {
            autoComplete = autoComplete_s()
            Clear()
        }
    }

    /*
     ===============
     FindMatches
     ===============
     */
    internal class FindMatches : void_callback<String?>() {
        override fun run(vararg objects: String?) {
            val s = objects[0]
            var i: Int
            if (idStr.Companion.Icmpn(
                    s,
                    TempDump.ctos(EditField.globalAutoComplete.completionString),
                    TempDump.strLen(EditField.globalAutoComplete.completionString)
                ) != 0
            ) {
                return
            }
            EditField.globalAutoComplete.matchCount++
            if (EditField.globalAutoComplete.matchCount == 1) {
                idStr.Companion.Copynz(
                    EditField.globalAutoComplete.currentMatch,
                    s,
                    EditField.globalAutoComplete.currentMatch.size
                )
                return
            }

            // cut currentMatch to the amount common with s
            i = 0
            while (i < s.length) {
                if (EditField.globalAutoComplete.currentMatch[i].lowercaseChar() != s.get(i).lowercaseChar()) {
                    EditField.globalAutoComplete.currentMatch[i] = 0
                    break
                }
                i++
            }
            EditField.globalAutoComplete.currentMatch[i] = 0
        }

        companion object {
            private val instance: void_callback<*>? = FindMatches()
            fun getInstance(): void_callback<*>? {
                return instance
            }
        }
    }

    /*
     ===============
     FindIndexMatch
     ===============
     */
    internal class FindIndexMatch : void_callback<String?>() {
        override fun run(vararg objects: String?) {
            val s = objects[0]
            val completionStr = TempDump.ctos(EditField.globalAutoComplete.completionString)
            if (idStr.Companion.Icmpn(s, completionStr, completionStr.length) != 0) {
                return
            }
            if (EditField.globalAutoComplete.findMatchIndex == EditField.globalAutoComplete.matchIndex) {
                idStr.Companion.Copynz(
                    EditField.globalAutoComplete.currentMatch,
                    s,
                    EditField.globalAutoComplete.currentMatch.size
                )
            }
            EditField.globalAutoComplete.findMatchIndex++
        }

        companion object {
            private val instance: void_callback<*>? = FindIndexMatch()
            fun getInstance(): void_callback<*>? {
                return instance
            }
        }
    }

    /*
     ===============
     PrintMatches
     ===============
     */
    internal class PrintMatches : void_callback<String?>() {
        @Throws(idException::class)
        override fun run(vararg objects: String?) {
            val s = objects[0]
            val currentMatch = TempDump.ctos(EditField.globalAutoComplete.currentMatch)
            if (idStr.Companion.Icmpn(s, currentMatch, currentMatch.length) == 0) {
                Common.common.Printf("    %s\n", s)
            }
        }

        companion object {
            private val instance: void_callback<*>? = PrintMatches()
            fun getInstance(): void_callback<*>? {
                return instance
            }
        }
    }

    /*
     ===============
     PrintCvarMatches
     ===============
     */
    internal class PrintCvarMatches : void_callback<String?>() {
        @Throws(idException::class)
        override fun run(vararg objects: String?) {
            val s = objects[0]
            val currentMatch = TempDump.ctos(EditField.globalAutoComplete.currentMatch)
            if (idStr.Companion.Icmpn(s, currentMatch, currentMatch.length) == 0) {
                Common.common.Printf(
                    """    %s${Str.S_COLOR_WHITE} = "%s"
""", s, CVarSystem.cvarSystem.GetCVarString(s)
                )
            }
        }

        companion object {
            private val instance: void_callback<*>? = PrintCvarMatches()
            fun getInstance(): void_callback<*>? {
                return instance
            }
        }
    }
}