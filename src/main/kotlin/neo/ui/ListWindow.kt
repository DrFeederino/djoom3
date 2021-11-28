package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.KeyInput
import neo.framework.KeyInput.idKeyInput
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.DeviceContext.idDeviceContext.ALIGN
import neo.ui.Rectangle.idRectangle
import neo.ui.SliderWindow.idSliderWindow
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow

/**
 *
 */
object ListWindow {
    const val TAB_TYPE_ICON = 1

    //
    // enum {
    const val TAB_TYPE_TEXT = 0

    //
    // Time in milliseconds between clicks to register as a double-click
    const val doubleClickSpeed = 300

    // Number of pixels above the text that the rect starts
    const val pixelOffset = 3

    //
    // number of pixels between columns
    const val tabBorder = 4

    // };
    class idTabRect {
        var align = 0
        var iconSize: idVec2? = idVec2()
        var iconVOffset = 0f
        var type = 0
        var valign = 0
        var w = 0
        var x = 0
    }

    class idListWindow : idWindow {
        //
        private var clickTime = 0
        private val currentSel: idList<Int?>? = idList()
        private var horizontal = false
        private val iconMaterials: HashMap<String?, idMaterial?>? = HashMap()

        //
        private val listItems: idStrList? = idStrList()
        private val listName: idStr? = idStr()
        private var multipleSel = false
        private var scroller: idSliderWindow? = null
        private var sizeBias = 0f
        private val tabAlignStr: idStr? = idStr()
        private val tabIconSizeStr: idStr? = idStr()
        private val tabIconVOffsetStr: idStr? = idStr()
        private val tabInfo: idList<idTabRect?>? = idList()
        private val tabStopStr: idStr? = idStr()
        private val tabTypeStr: idStr? = idStr()
        private val tabVAlignStr: idStr? = idStr()
        private var top = 0
        private val typed: idStr? = idStr()

        //
        private var typedTime = 0

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

        override fun HandleEvent(event: sysEvent_s?, updateVisuals: BooleanArray?): String? {
            // need to call this to allow proper focus and capturing on embedded children
            val ret = super.HandleEvent(event, updateVisuals)
            val vert = GetMaxCharHeight()
            val numVisibleLines = (textRect.h / vert).toInt()
            var key = event.evValue
            if (event.evType == sysEventType_t.SE_KEY) {
                if (0 == event.evValue2) {
                    // We only care about key down, not up
                    return ret
                }
                if (key == KeyInput.K_MOUSE1 || key == KeyInput.K_MOUSE2) {
                    // If the user clicked in the scroller, then ignore it
                    if (scroller.Contains(gui.CursorX(), gui.CursorY())) {
                        return ret
                    }
                }
                if (key == KeyInput.K_ENTER || key == KeyInput.K_KP_ENTER) {
                    RunScript(TempDump.etoi(ON.ON_ENTER))
                    return cmd.toString()
                }
                if (key == KeyInput.K_MWHEELUP) {
                    key = KeyInput.K_UPARROW
                } else if (key == KeyInput.K_MWHEELDOWN) {
                    key = KeyInput.K_DOWNARROW
                }
                if (key == KeyInput.K_MOUSE1) {
                    if (Contains(gui.CursorX(), gui.CursorY())) {
                        val cur = ((gui.CursorY() - actualY - pixelOffset) / vert).toInt() + top
                        if (cur >= 0 && cur < listItems.size()) {
                            if (multipleSel && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                                if (IsSelected(cur)) {
                                    ClearSelection(cur)
                                } else {
                                    AddCurrentSel(cur)
                                }
                            } else {
                                if (IsSelected(cur) && gui.GetTime() < clickTime + doubleClickSpeed) {
                                    // Double-click causes ON_ENTER to get run
                                    RunScript(TempDump.etoi(ON.ON_ENTER))
                                    return cmd.toString()
                                }
                                SetCurrentSel(cur)
                                clickTime = gui.GetTime()
                            }
                        } else {
                            SetCurrentSel(listItems.size() - 1)
                        }
                    }
                } else if (key == KeyInput.K_UPARROW || key == KeyInput.K_PGUP || key == KeyInput.K_DOWNARROW || key == KeyInput.K_PGDN) {
                    var numLines = 1
                    if (key == KeyInput.K_PGUP || key == KeyInput.K_PGDN) {
                        numLines = numVisibleLines / 2
                    }
                    if (key == KeyInput.K_UPARROW || key == KeyInput.K_PGUP) {
                        numLines = -numLines
                    }
                    if (idKeyInput.IsDown(KeyInput.K_CTRL)) {
                        top += numLines
                    } else {
                        SetCurrentSel(GetCurrentSel() + numLines)
                    }
                } else {
                    return ret
                }
            } else if (event.evType == sysEventType_t.SE_CHAR) {
                if (!idStr.Companion.CharIsPrintable(key)) {
                    return ret
                }
                if (gui.GetTime() > typedTime + 1000) {
                    typed.oSet("")
                }
                typedTime = gui.GetTime()
                typed.Append(key.toChar())
                for (i in 0 until listItems.size()) {
                    if (idStr.Companion.Icmpn(typed, listItems.get(i), typed.Length()) == 0) {
                        SetCurrentSel(i)
                        break
                    }
                }
            } else {
                return ret
            }
            if (GetCurrentSel() < 0) {
                SetCurrentSel(0)
            }
            if (GetCurrentSel() >= listItems.size()) {
                SetCurrentSel(listItems.size() - 1)
            }
            if (scroller.GetHigh() > 0.0f) {
                if (!idKeyInput.IsDown(KeyInput.K_CTRL)) {
                    if (top > GetCurrentSel() - 1) {
                        top = GetCurrentSel() - 1
                    }
                    if (top < GetCurrentSel() - numVisibleLines + 2) {
                        top = GetCurrentSel() - numVisibleLines + 2
                    }
                }
                if (top > listItems.size() - 2) {
                    top = listItems.size() - 2
                }
                if (top < 0) {
                    top = 0
                }
                scroller.SetValue(top.toFloat())
            } else {
                top = 0
                scroller.SetValue(0.0f)
            }
            if (key != KeyInput.K_MOUSE1) {
                // Send a fake mouse click event so onAction gets run in our parents
                val ev = idLib.sys.GenerateMouseButtonEvent(1, true)
                super.HandleEvent(ev, updateVisuals)
            }
            if (currentSel.Num() > 0) {
                for (i in 0 until currentSel.Num()) {
                    gui.SetStateInt(Str.va("%s_sel_%d", listName, i), currentSel.oGet(i))
                }
            } else {
                gui.SetStateInt(Str.va("%s_sel_0", listName), 0)
            }
            gui.SetStateInt(Str.va("%s_numsel", listName), currentSel.Num())
            return ret
        }

        override fun PostParse() {
            super.PostParse()
            InitScroller(horizontal)
            val tabStops = idList<Int?>()
            val tabAligns = idList<Int?>()
            if (tabStopStr.Length() != 0) {
                val src = idParser(
                    tabStopStr.toString(),
                    tabStopStr.Length(),
                    "tabstops",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    tabStops.Append(tok.toString().toInt())
                }
            }
            if (tabAlignStr.Length() != 0) {
                val src = idParser(
                    tabAlignStr.toString(),
                    tabAlignStr.Length(),
                    "tabaligns",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    tabAligns.Append(tok.toString().toInt())
                }
            }
            val tabVAligns = idList<Int?>()
            if (tabVAlignStr.Length() != 0) {
                val src = idParser(
                    tabVAlignStr.toString(),
                    tabVAlignStr.Length(),
                    "tabvaligns",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    tabVAligns.Append(tok.toString().toInt())
                }
            }
            val tabTypes = idList<Int?>()
            if (tabTypeStr.Length() != 0) {
                val src = idParser(
                    tabTypeStr.toString(),
                    tabTypeStr.Length(),
                    "tabtypes",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    tabTypes.Append(tok.toString().toInt())
                }
            }
            val tabSizes = idList<idVec2?>()
            if (tabIconSizeStr.Length() != 0) {
                val src = idParser(
                    tabIconSizeStr.toString(),
                    tabIconSizeStr.Length(),
                    "tabiconsizes",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    val size = idVec2()
                    size.x = tok.toString().toInt().toFloat()
                    src.ReadToken(tok) //","
                    src.ReadToken(tok)
                    size.y = tok.toString().toInt().toFloat()
                    tabSizes.Append(size)
                }
            }
            val tabIconVOffsets = idList<Float?>()
            if (tabIconVOffsetStr.Length() != 0) {
                val src = idParser(
                    tabIconVOffsetStr.toString(),
                    tabIconVOffsetStr.Length(),
                    "tabiconvoffsets",
                    Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS
                )
                val tok = idToken()
                while (src.ReadToken(tok)) {
                    if (tok == ",") {
                        continue
                    }
                    tabIconVOffsets.Append(tok.toString().toFloat())
                }
            }
            val c = tabStops.Num()
            val doAligns = tabAligns.Num() == tabStops.Num()
            for (i in 0 until c) {
                val r = idTabRect()
                r.x = tabStops.oGet(i)
                r.w = if (i < c - 1) tabStops.oGet(i + 1) - r.x - tabBorder else -1
                r.align = if (doAligns) tabAligns.oGet(i) else 0
                if (tabVAligns.Num() > 0) {
                    r.valign = tabVAligns.oGet(i)
                } else {
                    r.valign = 0
                }
                if (tabTypes.Num() > 0) {
                    r.type = tabTypes.oGet(i)
                } else {
                    r.type = TAB_TYPE_TEXT
                }
                if (tabSizes.Num() > 0) {
                    r.iconSize = tabSizes.oGet(i)
                } else {
                    r.iconSize.Zero()
                }
                if (tabIconVOffsets.Num() > 0) {
                    r.iconVOffset = tabIconVOffsets.oGet(i)
                } else {
                    r.iconVOffset = 0f
                }
                tabInfo.Append(r)
            }
            flags = flags or Window.WIN_CANFOCUS
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var color: idVec4?
            val work = idStr()
            val count = listItems.size()
            val rect = idRectangle(textRect)
            val scale = textScale.data
            val lineHeight = GetMaxCharHeight()
            var bottom = textRect.Bottom()
            var width = textRect.w
            if (scroller.GetHigh() > 0.0f) {
                if (horizontal) {
                    bottom -= sizeBias
                } else {
                    width -= sizeBias
                    rect.w = width
                }
            }
            if (noEvents.oCastBoolean() || !Contains(gui.CursorX(), gui.CursorY())) {
                hover = false
            }
            for (i in top until count) {
                if (IsSelected(i)) {
                    rect.h = lineHeight
                    dc.DrawFilledRect(rect.x, rect.y + pixelOffset, rect.w, rect.h, borderColor.data)
                    if (flags and Window.WIN_FOCUS != 0) {
                        val color2 = borderColor.data
                        color2.w = 1.0f
                        dc.DrawRect(rect.x, rect.y + pixelOffset, rect.w, rect.h, 1.0f, color2)
                    }
                }
                rect.y++
                rect.h = lineHeight - 1
                color = if (hover && !noEvents.oCastBoolean() && Contains(rect, gui.CursorX(), gui.CursorY())) {
                    hoverColor.data
                } else {
                    foreColor.data
                }
                rect.h = lineHeight + pixelOffset
                rect.y--
                if (tabInfo.Num() > 0) {
                    var start = 0
                    var tab = 0
                    var stop = listItems.get(i).Find('\t', 0)
                    while (start < listItems.get(i).Length()) {
                        if (tab >= tabInfo.Num()) {
                            Common.common.Warning(
                                "idListWindow::Draw: gui '%s' window '%s' tabInfo.Num() exceeded",
                                gui.GetSourceFile(),
                                name
                            )
                            break
                        }
                        listItems.get(i).Mid(start, stop - start, work)
                        rect.x = textRect.x + tabInfo.oGet(tab).x
                        rect.w = if (tabInfo.oGet(tab).w == -1) width - tabInfo.oGet(tab).x else tabInfo.oGet(tab).w
                        dc.PushClipRect(rect)
                        if (tabInfo.oGet(tab).type == TAB_TYPE_TEXT) {
                            dc.DrawText(work, scale, tabInfo.oGet(tab).align, color, rect, false, -1)
                        } else if (tabInfo.oGet(tab).type == TAB_TYPE_ICON) {
                            var hashMat: idMaterial?
                            var iconMat: idMaterial?

                            // leaving the icon name empty doesn't draw anything
                            if (TempDump.isNotNullOrEmpty(work)) {
                                hashMat = iconMaterials.get(work.toString())
                                iconMat = hashMat ?: DeclManager.declManager.FindMaterial("_default")
                                val iconRect = idRectangle()
                                iconRect.w = tabInfo.oGet(tab).iconSize.x
                                iconRect.h = tabInfo.oGet(tab).iconSize.y
                                if (tabInfo.oGet(tab).align == TempDump.etoi(ALIGN.ALIGN_LEFT)) {
                                    iconRect.x = rect.x
                                } else if (tabInfo.oGet(tab).align == TempDump.etoi(ALIGN.ALIGN_CENTER)) {
                                    iconRect.x = rect.x + rect.w / 2.0f - iconRect.w / 2.0f
                                } else if (tabInfo.oGet(tab).align == TempDump.etoi(ALIGN.ALIGN_RIGHT)) {
                                    iconRect.x = rect.x + rect.w - iconRect.w
                                }
                                if (tabInfo.oGet(tab).valign == 0) { //Top
                                    iconRect.y = rect.y + tabInfo.oGet(tab).iconVOffset
                                } else if (tabInfo.oGet(tab).valign == 1) { //Center
                                    iconRect.y =
                                        rect.y + rect.h / 2.0f - iconRect.h / 2.0f + tabInfo.oGet(tab).iconVOffset
                                } else if (tabInfo.oGet(tab).valign == 2) { //Bottom
                                    iconRect.y = rect.y + rect.h - iconRect.h + tabInfo.oGet(tab).iconVOffset
                                }
                                dc.DrawMaterial(
                                    iconRect.x,
                                    iconRect.y,
                                    iconRect.w,
                                    iconRect.h,
                                    iconMat,
                                    idVec4(1.0f, 1.0f, 1.0f, 1.0f),
                                    1.0f,
                                    1.0f
                                )
                            }
                        }
                        dc.PopClipRect()
                        start = stop + 1
                        stop = listItems.get(i).Find('\t', start)
                        if (stop < 0) {
                            stop = listItems.get(i).Length()
                        }
                        tab++
                    }
                    rect.x = textRect.x
                    rect.w = width
                } else {
                    dc.DrawText(listItems.get(i), scale, 0, color, rect, false, -1)
                }
                rect.y += lineHeight
                if (rect.y > bottom) {
                    break
                }
            }
        }

        override fun Activate(activate: Boolean, act: idStr?) {
            super.Activate(activate, act)
            if (activate) {
                UpdateList()
            }
        }

        override fun HandleBuddyUpdate(buddy: idWindow?) {
            top = scroller.GetValue().toInt()
        }

        override fun StateChanged(redraw: Boolean /*= false*/) {
            UpdateList()
        }

        fun UpdateList() {
            val str = idStr()
            var strName: idStr
            listItems.clear()
            for (i in 0 until Window.MAX_LIST_ITEMS) {
                if (gui.State().GetString(Str.va("%s_item_%d", listName, i), "", str)) {
                    if (str.Length() != 0) {
                        listItems.add(str)
                    }
                } else {
                    break
                }
            }
            val vert = GetMaxCharHeight()
            val fit = (textRect.h / vert).toInt()
            if (listItems.size() < fit) {
                scroller.SetRange(0.0f, 0.0f, 1.0f)
            } else {
                scroller.SetRange(0.0f, listItems.size() - fit + 1.0f, 1.0f)
            }
            SetCurrentSel(gui.State().GetInt(Str.va("%s_sel_0", listName)))
            var value = scroller.GetValue()
            if (value > listItems.size() - 1) {
                value = (listItems.size() - 1).toFloat()
            }
            if (value < 0.0f) {
                value = 0.0f
            }
            scroller.SetValue(value)
            top = value.toInt()
            typedTime = 0
            clickTime = 0
            typed.oSet("")
        }

        override fun ParseInternalVar(_name: String?, src: idParser?): Boolean {
            if (idStr.Companion.Icmp(_name, "horizontal") == 0) {
                horizontal = src.ParseBool()
                return true
            }
            if (idStr.Companion.Icmp(_name, "listname") == 0) {
                ParseString(src, listName)
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabstops") == 0) {
                ParseString(src, tabStopStr)
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabaligns") == 0) {
                ParseString(src, tabAlignStr)
                return true
            }
            if (idStr.Companion.Icmp(_name, "multipleSel") == 0) {
                multipleSel = src.ParseBool()
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabvaligns") == 0) {
                ParseString(src, tabVAlignStr)
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabTypes") == 0) {
                ParseString(src, tabTypeStr)
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabIconSizes") == 0) {
                ParseString(src, tabIconSizeStr)
                return true
            }
            if (idStr.Companion.Icmp(_name, "tabIconVOffset") == 0) {
                ParseString(src, tabIconVOffsetStr)
                return true
            }
            val strName = idStr(_name)
            if (idStr.Companion.Icmp(strName.Left(4), "mtr_") == 0) {
                val matName = idStr()
                val mat: idMaterial?
                ParseString(src, matName)
                mat = DeclManager.declManager.FindMaterial(matName)
                mat.SetImageClassifications(1) // just for resource tracking
                if (mat != null && !mat.TestMaterialFlag(Material.MF_DEFAULTED)) {
                    mat.SetSort(Material.SS_GUI.toFloat())
                }
                iconMaterials[_name] = mat
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            typed.oSet("")
            typedTime = 0
            clickTime = 0
            currentSel.Clear()
            top = 0
            sizeBias = 0f
            horizontal = false
            scroller = idSliderWindow(dc, gui)
            multipleSel = false
        }

        /*
         ================
         idListWindow::InitScroller

         This is the same as in idEditWindow
         ================
         */
        private fun InitScroller(horizontal: Boolean) {
            val thumbImage = "guis/assets/scrollbar_thumb.tga"
            var barImage = "guis/assets/scrollbarv.tga"
            var scrollerName = "_scrollerWinV"
            if (horizontal) {
                barImage = "guis/assets/scrollbarh.tga"
                scrollerName = "_scrollerWinH"
            }
            val mat = DeclManager.declManager.FindMaterial(barImage)
            mat.SetSort(Material.SS_GUI.toFloat())
            sizeBias = mat.GetImageWidth().toFloat()
            val scrollRect = idRectangle()
            if (horizontal) {
                sizeBias = mat.GetImageHeight().toFloat()
                scrollRect.x = 0f
                scrollRect.y = clientRect.h - sizeBias
                scrollRect.w = clientRect.w
                scrollRect.h = sizeBias
            } else {
                scrollRect.x = clientRect.w - sizeBias
                scrollRect.y = 0f
                scrollRect.w = sizeBias
                scrollRect.h = clientRect.h
            }
            scroller.InitWithDefaults(
                scrollerName,
                scrollRect,
                foreColor.data,
                matColor.data,
                mat.GetName(),
                thumbImage,
                !horizontal,
                true
            )
            InsertChild(scroller, null)
            scroller.SetBuddy(this)
        }

        private fun SetCurrentSel(sel: Int) {
            currentSel.Clear()
            currentSel.Append(sel)
        }

        private fun AddCurrentSel(sel: Int) {
            currentSel.Append(sel)
        }

        private fun GetCurrentSel(): Int {
            return if (currentSel.Num() != 0) currentSel.oGet(0) else 0
        }

        private fun IsSelected(index: Int): Boolean {
            return currentSel.FindIndex(index) >= 0
        }

        private fun ClearSelection(sel: Int) {
            val cur = currentSel.FindIndex(sel)
            if (cur >= 0) {
                currentSel.RemoveIndex(cur)
            }
        }
    }
}