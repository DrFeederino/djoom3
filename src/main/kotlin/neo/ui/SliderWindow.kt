package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.KeyInput
import neo.idlib.Lib.idLib
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.math.Vector.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar

/**
 *
 */
class SliderWindow {
    class idSliderWindow : idWindow {
        private var buddyWin: idWindow? = null
        private var cvar: idCVar? = null
        private val cvarGroup: idWinStr? = null

        //
        private val cvarStr: idWinStr = idWinStr()
        private var cvar_init = false
        private var high = 0f
        private val lastValue = 0f
        private val liveUpdate: idWinBool = idWinBool()
        private var low = 0f
        private var scrollbar = false
        private var stepSize = 0f
        private var thumbHeight = 0f
        private var thumbMat: idMaterial? = null
        private val thumbRect: idRectangle = idRectangle()
        private val thumbShader: idStr = idStr()
        private var thumbWidth = 0f
        private val value: idWinFloat = idWinFloat()
        private var vertical = false
        private var verticalFlip = false

        //
        //
        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext, gui: idUserInterfaceLocal) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        //	// virtual				~idSliderWindow();
        fun InitWithDefaults(
            _name: String,
            _rect: idRectangle,
            _foreColor: idVec4,
            _matColor: idVec4,
            _background: String,
            thumbShader: String,
            _vertical: Boolean,
            _scrollbar: Boolean
        ) {
            SetInitialState(_name)
            rect.set(_rect)
            foreColor.set(_foreColor)
            matColor.set(_matColor)
            thumbMat = DeclManager.declManager.FindMaterial(thumbShader)
            thumbMat!!.SetSort(Material.SS_GUI.toFloat())
            thumbWidth = thumbMat!!.GetImageWidth().toFloat()
            thumbHeight = thumbMat!!.GetImageHeight().toFloat()
            background = DeclManager.declManager.FindMaterial(_background)
            background!!.SetSort(Material.SS_GUI.toFloat())
            vertical = _vertical
            scrollbar = _scrollbar
            flags = flags or Window.WIN_HOLDCAPTURE
        }

        fun SetRange(_low: Float, _high: Float, _step: Float) {
            low = _low
            high = _high
            stepSize = _step
        }

        fun GetLow(): Float {
            return low
        }

        fun GetHigh(): Float {
            return high
        }

        fun SetValue(_value: Float) {
            value.data = _value
        }

        fun GetValue(): Float {
            return value.data
        }

        override fun GetWinVarByName(
            _name: String,
            winLookup: Boolean /*= false*/,
            owner: Array<drawWin_t?>? /*= NULL*/
        ): idWinVar? {
            if (idStr.Companion.Icmp(_name, "value") == 0) {
                return value
            }
            if (idStr.Companion.Icmp(_name, "cvar") == 0) {
                return cvarStr
            }
            if (idStr.Companion.Icmp(_name, "liveUpdate") == 0) {
                return liveUpdate
            }
            return if (idStr.Companion.Icmp(_name, "cvarGroup") == 0) {
                cvarGroup
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        override fun HandleEvent(event: sysEvent_s, updateVisuals: CBool): String {
            if (!(event.evType == sysEventType_t.SE_KEY && event.evValue2 != 0)) {
                return ""
            }
            val key = event.evValue
            if (event.evValue2 != 0 && key == KeyInput.K_MOUSE1) {
                SetCapture(this)
                RouteMouseCoords(0.0f, 0.0f)
                return ""
            }
            if (key == KeyInput.K_RIGHTARROW || key == KeyInput.K_KP_RIGHTARROW || key == KeyInput.K_MOUSE2 && gui!!.CursorY() > thumbRect.y) {
                value.data = value.data + stepSize
            }
            if (key == KeyInput.K_LEFTARROW || key == KeyInput.K_KP_LEFTARROW || key == KeyInput.K_MOUSE2 && gui!!.CursorY() < thumbRect.y) {
                value.data = value.data - stepSize
            }
            if (buddyWin != null) {
                buddyWin!!.HandleBuddyUpdate(this)
            } else {
                gui!!.SetStateFloat(cvarStr.data.toString(), value.data)
                UpdateCvar(false)
            }
            return ""
        }

        override fun PostParse() {
            super.PostParse()
            value.data = 0f
            thumbMat = DeclManager.declManager.FindMaterial(thumbShader)
            thumbMat!!.SetSort(Material.SS_GUI.toFloat())
            thumbWidth = thumbMat!!.GetImageWidth().toFloat()
            thumbHeight = thumbMat!!.GetImageHeight().toFloat()
            //vertical = state.GetBool("vertical");
            //scrollbar = state.GetBool("scrollbar");
            flags = flags or (Window.WIN_HOLDCAPTURE or Window.WIN_CANFOCUS)
            InitCvar()
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            val color = foreColor.data
            if (null == cvar && null == buddyWin) {
                return
            }
            if (0f == thumbWidth || 0f == thumbHeight) {
                thumbWidth = thumbMat!!.GetImageWidth().toFloat()
                thumbHeight = thumbMat!!.GetImageHeight().toFloat()
            }
            UpdateCvar(true)
            if (value.data > high) {
                value.data = high
            } else if (value.data < low) {
                value.data = low
            }
            val range = high - low
            if (range <= 0.0f) {
                return
            }
            var thumbPos: Float = if (range != 0f) (value.data - low) / range else 0f
            if (vertical) {
                if (verticalFlip) {
                    thumbPos = 1f - thumbPos
                }
                thumbPos *= drawRect.h - thumbHeight
                thumbPos += drawRect.y
                thumbRect.y = thumbPos
                thumbRect.x = drawRect.x
            } else {
                thumbPos *= drawRect.w - thumbWidth
                thumbPos += drawRect.x
                thumbRect.x = thumbPos
                thumbRect.y = drawRect.y
            }
            thumbRect.w = thumbWidth
            thumbRect.h = thumbHeight
            if (hover && !noEvents.oCastBoolean() && Contains(gui!!.CursorX(), gui!!.CursorY())) {
                color.set(hoverColor.data)
            } else {
                hover = false
            }
            if (flags and Window.WIN_CAPTURE != 0) {
                color.set(hoverColor.data)
                hover = true
            }
            dc!!.DrawMaterial(thumbRect.x, thumbRect.y, thumbRect.w, thumbRect.h, thumbMat!!, color)
            if (flags and Window.WIN_FOCUS != 0) {
                dc!!.DrawRect(
                    thumbRect.x + 1.0f,
                    thumbRect.y + 1.0f,
                    thumbRect.w - 2.0f,
                    thumbRect.h - 2.0f,
                    1.0f,
                    color
                )
            }
        }

        override fun DrawBackground(_drawRect: idRectangle) {
            if (null == cvar && null == buddyWin) {
                return
            }
            if (high - low <= 0.0f) {
                return
            }
            val r = idRectangle(_drawRect)
            if (!scrollbar) {
                if (vertical) {
                    r.y += thumbHeight / 2f
                    r.h -= thumbHeight
                } else {
                    r.x += (thumbWidth / 2.0).toFloat()
                    r.w -= thumbWidth
                }
            }
            super.DrawBackground(r)
        }

        override fun RouteMouseCoords(xd: Float, yd: Float): String {
            var pct: Float
            if (TempDump.NOT((flags and Window.WIN_CAPTURE).toDouble())) {
                return ""
            }
            val r = idRectangle(drawRect)
            r.x = actualX
            r.y = actualY
            r.x += (thumbWidth / 2.0).toFloat()
            r.w -= thumbWidth
            if (vertical) {
                r.y += thumbHeight / 2
                r.h -= thumbHeight
                if (gui!!.CursorY() >= r.y && gui!!.CursorY() <= r.Bottom()) {
                    pct = (gui!!.CursorY() - r.y) / r.h
                    if (verticalFlip) {
                        pct = 1f - pct
                    }
                    value.data = low + (high - low) * pct
                } else if (gui!!.CursorY() < r.y) {
                    if (verticalFlip) {
                        value.data = high
                    } else {
                        value.data = low
                    }
                } else {
                    if (verticalFlip) {
                        value.data = low
                    } else {
                        value.data = high
                    }
                }
            } else {
                r.x += thumbWidth / 2
                r.w -= thumbWidth
                if (gui!!.CursorX() >= r.x && gui!!.CursorX() <= r.Right()) {
                    pct = (gui!!.CursorX() - r.x) / r.w
                    value.data = low + (high - low) * pct
                } else if (gui!!.CursorX() < r.x) {
                    value.data = low
                } else {
                    value.data = high
                }
            }
            if (buddyWin != null) {
                buddyWin!!.HandleBuddyUpdate(this)
            } else {
                gui!!.SetStateFloat(cvarStr.data.toString(), value.data)
            }
            UpdateCvar(false)
            return ""
        }

        override fun Activate(activate: Boolean, act: idStr) {
            super.Activate(activate, act)
            if (activate) {
                UpdateCvar(true, true)
            }
        }

        override fun SetBuddy(buddy: idWindow) {
            buddyWin = buddy
        }

        override fun RunNamedEvent(eventName: String) {
            val event: idStr
            val group: idStr
            if (0 == idStr.Companion.Cmpn(eventName, "cvar read ", 10)) {
                event = idStr(eventName)
                group = idStr(event.Mid(10, event.Length() - 10))
                if (TempDump.NOT(group.Cmp(cvarGroup!!.data).toDouble())) {
                    UpdateCvar(true, true)
                }
            } else if (0 == idStr.Companion.Cmpn(eventName, "cvar write ", 11)) {
                event = idStr(eventName)
                group = idStr(event.Mid(11, event.Length() - 11))
                if (TempDump.NOT(group.Cmp(cvarGroup!!.data).toDouble())) {
                    UpdateCvar(false, true)
                }
            }
        }

        override fun ParseInternalVar(_name: String, src: idParser): Boolean {
            if (idStr.Companion.Icmp(_name, "stepsize") == 0 || idStr.Companion.Icmp(_name, "step") == 0) {
                stepSize = src.ParseFloat()
                return true
            }
            if (idStr.Companion.Icmp(_name, "low") == 0) {
                low = src.ParseFloat()
                return true
            }
            if (idStr.Companion.Icmp(_name, "high") == 0) {
                high = src.ParseFloat()
                return true
            }
            if (idStr.Companion.Icmp(_name, "vertical") == 0) {
                vertical = src.ParseBool()
                return true
            }
            if (idStr.Companion.Icmp(_name, "verticalflip") == 0) {
                verticalFlip = src.ParseBool()
                return true
            }
            if (idStr.Companion.Icmp(_name, "scrollbar") == 0) {
                scrollbar = src.ParseBool()
                return true
            }
            if (idStr.Companion.Icmp(_name, "thumbshader") == 0) {
                ParseString(src, thumbShader)
                DeclManager.declManager.FindMaterial(thumbShader)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            value.data = 0f
            low = 0f
            high = 100.0f
            stepSize = 1.0f
            thumbMat = DeclManager.declManager.FindMaterial("_default")
            buddyWin = null
            cvar = null
            cvar_init = false
            liveUpdate.data = true
            vertical = false
            scrollbar = false
            verticalFlip = false
        }

        private fun InitCvar() {
            if (cvarStr.c_str().isEmpty()) {
                if (null == buddyWin) {
                    Common.common.Warning(
                        "idSliderWindow.InitCvar: gui '%s' window '%s' has an empty cvar string",
                        gui!!.GetSourceFile(),
                        name
                    )
                }
                cvar_init = true
                cvar = null
                return
            }
            cvar = idLib.cvarSystem.Find(cvarStr.data.toString())
            if (null == cvar) {
                Common.common.Warning(
                    "idSliderWindow.InitCvar: gui '%s' window '%s' references undefined cvar '%s'",
                    gui!!.GetSourceFile(),
                    name,
                    cvarStr.c_str()
                )
                cvar_init = true
                return
            }
        }

        // true: read the updated cvar from cvar system
        // false: write to the cvar system
        // force == true overrides liveUpdate 0
        private fun UpdateCvar(read: Boolean, force: Boolean = false /*= false*/) {
            if (buddyWin != null || null == cvar) {
                return
            }
            if (force || liveUpdate.oCastBoolean()) {
                value.data = cvar!!.GetFloat()
                if (value.data != gui!!.State().GetFloat(cvarStr.data.toString())) {
                    if (read) {
                        gui!!.SetStateFloat(cvarStr.data.toString(), value.data)
                    } else {
                        value.data = gui!!.State().GetFloat(cvarStr.data.toString())
                        cvar!!.SetFloat(value.data)
                    }
                }
            }
        }
    }
}