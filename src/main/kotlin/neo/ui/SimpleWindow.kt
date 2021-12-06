package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump
import neo.framework.DeclManager
import neo.framework.File_h.idFile
import neo.idlib.Lib
import neo.idlib.Text.Str.idStr
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBackground
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec2
import neo.ui.Winvar.idWinVec4

/**
 *
 */
class SimpleWindow {
    class drawWin_t {
        val DBG_index: Int
        var simp: idSimpleWindow? = null
        var win: idWindow? = null

        companion object {
            private var DBG_counter = 0
        }

        init {
            DBG_index = DBG_counter++
        }
    }

    class idSimpleWindow(win: idWindow) {
        val name: idStr = idStr()
        protected var backColor: idWinVec4 = idWinVec4()
        protected var backGroundName: idWinBackground = idWinBackground()
        protected var background: idMaterial?
        protected var borderColor: idWinVec4 = idWinVec4()
        protected var borderSize: Float
        protected val clientRect: idRectangle = idRectangle() // client area
        protected var dc: idDeviceContext
        protected val drawRect: idRectangle = idRectangle() // overall rect
        protected var flags: Int
        protected var fontNum: Int
        protected var foreColor: idWinVec4 = idWinVec4()
        protected var gui: idUserInterfaceLocal
        protected var hideCursor: idWinBool = idWinBool()
        protected var mParent: idWindow?
        protected var matColor: idWinVec4 = idWinVec4()
        protected var matScalex: Float
        protected var matScaley: Float
        protected val origin: idVec2 = idVec2()
        protected var rect: idWinRectangle = idWinRectangle() // overall rect
        protected var rotate: idWinFloat = idWinFloat()
        protected var shear: idWinVec2 = idWinVec2()

        //
        protected var text: idWinStr = idWinStr()
        protected var textAlign: Int
        protected var textAlignx: Float
        protected var textAligny: Float
        protected val textRect: idRectangle = idRectangle()
        protected var textScale: idWinFloat = idWinFloat()
        protected var textShadow: Int
        protected var visible: idWinBool = idWinBool()

        //	virtual			~idSimpleWindow();
        fun Redraw(x: Float, y: Float) {
            if (!visible.data) {
                return
            }
            CalcClientRect(0f, 0f)
            dc.SetFont(fontNum)
            drawRect.Offset(x, y)
            clientRect.Offset(x, y)
            textRect.Offset(x, y)
            SetupTransforms(x, y)
            if (flags and Window.WIN_NOCLIP != 0) {
                dc.EnableClipping(false)
            }
            DrawBackground(drawRect)
            DrawBorderAndCaption(drawRect)
            if (textShadow != 0) {
                val shadowText = text.data
                val shadowRect = idRectangle(textRect)
                shadowText.RemoveColors()
                shadowRect.x += textShadow.toFloat()
                shadowRect.y += textShadow.toFloat()
                dc.DrawText(
                    shadowText,
                    textScale.data,
                    textAlign,
                    Lib.colorBlack,
                    shadowRect,
                    !TempDump.itob(flags and Window.WIN_NOWRAP),
                    -1
                )
            }
            dc.DrawText(
                text.data,
                textScale.data,
                textAlign,
                foreColor.data,
                textRect,
                !TempDump.itob(flags and Window.WIN_NOWRAP),
                -1
            )
            dc.SetTransformInfo(Vector.getVec3_origin(), idMat3.getMat3_identity())
            if (flags and Window.WIN_NOCLIP != 0) {
                dc.EnableClipping(true)
            }
            drawRect.Offset(-x, -y)
            clientRect.Offset(-x, -y)
            textRect.Offset(-x, -y)
        }

        fun StateChanged(redraw: Boolean) {
            if (redraw && background != null && background!!.CinematicLength() != 0) {
                background!!.UpdateCinematic(gui.GetTime())
            }
        }

        fun GetWinVarByName(_name: String): idWinVar? {
            var retVar: idWinVar? = null
            if (idStr.Icmp(_name, "background") == 0) { //TODO:should this be a switch?
                retVar = backGroundName
            }
            if (idStr.Icmp(_name, "visible") == 0) {
                retVar = visible
            }
            if (idStr.Icmp(_name, "rect") == 0) {
                retVar = rect
            }
            if (idStr.Icmp(_name, "backColor") == 0) {
                retVar = backColor
            }
            if (idStr.Icmp(_name, "matColor") == 0) {
                retVar = matColor
            }
            if (idStr.Icmp(_name, "foreColor") == 0) {
                retVar = foreColor
            }
            if (idStr.Icmp(_name, "borderColor") == 0) {
                retVar = borderColor
            }
            if (idStr.Icmp(_name, "textScale") == 0) {
                retVar = textScale
            }
            if (idStr.Icmp(_name, "rotate") == 0) {
                retVar = rotate
            }
            if (idStr.Icmp(_name, "shear") == 0) {
                retVar = shear
            }
            if (idStr.Icmp(_name, "text") == 0) {
                retVar = text
            }
            return retVar
        }

        // TODO: check why this isn't implemented
        fun GetWinVarOffset(wv: idWinVar, owner: drawWin_t): Int {

//	if ( wv == &rect ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->rect;
//	}
//
//	if ( wv == &backColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->backColor;
//	}
//
//	if ( wv == &matColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->matColor;
//	}
//
//	if ( wv == &foreColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->foreColor;
//	}
//
//	if ( wv == &borderColor ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->borderColor;
//	}
//
//	if ( wv == &textScale ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->textScale;
//	}
//
//	if ( wv == &rotate ) {
//		ret = (int)&( ( idSimpleWindow * ) 0 )->rotate;
//	}
//
//	if ( ret != -1 ) {
//		owner->simp = this;
//	}
            return -1
        }

        fun GetParent(): idWindow {
            return mParent!!
        }

        fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteInt(flags)
            savefile.Write(drawRect)
            savefile.Write(clientRect)
            savefile.Write(textRect)
            savefile.Write(origin)
            savefile.WriteInt(fontNum)
            savefile.WriteFloat(matScalex)
            savefile.WriteFloat(matScaley)
            savefile.WriteFloat(borderSize)
            savefile.WriteInt(textAlign)
            savefile.WriteFloat(textAlignx)
            savefile.WriteFloat(textAligny)
            savefile.WriteInt(textShadow)
            text.WriteToSaveGame(savefile)
            visible.WriteToSaveGame(savefile)
            rect.WriteToSaveGame(savefile)
            backColor.WriteToSaveGame(savefile)
            matColor.WriteToSaveGame(savefile)
            foreColor.WriteToSaveGame(savefile)
            borderColor.WriteToSaveGame(savefile)
            textScale.WriteToSaveGame(savefile)
            rotate.WriteToSaveGame(savefile)
            shear.WriteToSaveGame(savefile)
            backGroundName.WriteToSaveGame(savefile)
            val stringLen: Int
            if (background != null) {
                stringLen = background!!.GetName().length
                savefile.WriteInt(stringLen)
                savefile.WriteString(background!!.GetName())
            } else {
                stringLen = 0
                savefile.WriteInt(stringLen)
            }
        }

        fun ReadFromSaveGame(savefile: idFile) {
            flags = savefile.ReadInt()
            savefile.Read(drawRect)
            savefile.Read(clientRect)
            savefile.Read(textRect)
            savefile.Read(origin)
            fontNum = savefile.ReadInt()
            matScalex = savefile.ReadFloat()
            matScaley = savefile.ReadFloat()
            borderSize = savefile.ReadFloat()
            textAlign = savefile.ReadInt()
            textAlignx = savefile.ReadFloat()
            textAligny = savefile.ReadFloat()
            textShadow = savefile.ReadInt()
            text.ReadFromSaveGame(savefile)
            visible.ReadFromSaveGame(savefile)
            rect.ReadFromSaveGame(savefile)
            backColor.ReadFromSaveGame(savefile)
            matColor.ReadFromSaveGame(savefile)
            foreColor.ReadFromSaveGame(savefile)
            borderColor.ReadFromSaveGame(savefile)
            textScale.ReadFromSaveGame(savefile)
            rotate.ReadFromSaveGame(savefile)
            shear.ReadFromSaveGame(savefile)
            backGroundName.ReadFromSaveGame(savefile)
            val stringLen: Int
            stringLen = savefile.ReadInt()
            if (stringLen > 0) {
                val backName = idStr()
                backName.Fill(' ', stringLen)
                savefile.ReadString(backName)
                background = DeclManager.declManager.FindMaterial(backName)
                background!!.SetSort(Material.SS_GUI.toFloat())
            } else {
                background = null
            }
        }

        protected fun CalcClientRect(xofs: Float, yofs: Float) {
            drawRect.set(rect.data)
            if (flags and Window.WIN_INVERTRECT != 0) {
                drawRect.x = rect.x() - rect.w()
                drawRect.y = rect.y() - rect.h()
            }
            drawRect.x += xofs
            drawRect.y += yofs
            clientRect.set(drawRect)
            if (rect.h() > 0.0 && rect.w() > 0.0) {
                if (flags and Window.WIN_BORDER != 0 && borderSize != 0f) {
                    clientRect.x += borderSize
                    clientRect.y += borderSize
                    clientRect.w -= borderSize
                    clientRect.h -= borderSize
                }
                textRect.set(clientRect)
                textRect.x += 2.0.toFloat()
                textRect.w -= 2.0.toFloat()
                textRect.y += 2.0.toFloat()
                textRect.h -= 2.0.toFloat()
                textRect.x += textAlignx
                textRect.y += textAligny
            }
            origin.set(rect.x() + rect.w() / 2, rect.y() + rect.h() / 2)
        }

        protected fun SetupTransforms(x: Float, y: Float) {
            trans.Identity()
            org.set(origin.x + x, origin.y + y, 0f)
            if (rotate != null && rotate.data != 0f) {
                rot.Set(org, vec, rotate.data)
                trans.set(rot.ToMat3())
            }
            smat.Identity()
            if (shear.x() != 0f || shear.y() != 0f) {
                smat.set(0, 1, shear.x())
                smat.set(1, 0, shear.y())
                trans.timesAssign(smat)
            }
            if (!trans.IsIdentity()) {
                dc.SetTransformInfo(org, trans)
            }
        }

        protected fun DrawBackground(drawRect: idRectangle) {
            if (backColor.w() > 0) {
                dc.DrawFilledRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, backColor.data)
            }
            if (background != null) {
                if (matColor.w() > 0) {
                    val scaleX: Float
                    val scaleY: Float
                    if (flags and Window.WIN_NATURALMAT != 0) {
                        scaleX = drawRect.w / background!!.GetImageWidth()
                        scaleY = drawRect.h / background!!.GetImageHeight()
                    } else {
                        scaleX = matScalex
                        scaleY = matScaley
                    }
                    dc.DrawMaterial(
                        drawRect.x,
                        drawRect.y,
                        drawRect.w,
                        drawRect.h,
                        background!!,
                        matColor.data,
                        scaleX,
                        scaleY
                    )
                }
            }
        }

        protected fun DrawBorderAndCaption(drawRect: idRectangle) {
            if (flags and Window.WIN_BORDER != 0) {
                if (borderSize != 0f) {
                    dc.DrawRect(drawRect.x, drawRect.y, drawRect.w, drawRect.h, borderSize, borderColor.data)
                }
            }
        }

        companion object {
            //	friend class idWindow;
            private val org: idVec3 = idVec3()
            private val rot: idRotation = idRotation()
            private val smat: idMat3 = idMat3()
            private val vec: idVec3 = idVec3(0f, 0f, 1f)
            private val trans: idMat3 = idMat3()
        }

        init {
            gui = win.GetGui()
            dc = win.dc!!
            drawRect.set(win.drawRect)
            clientRect.set(win.clientRect)
            textRect.set(win.textRect)
            origin.set(win.origin)
            fontNum = win.fontNum.code
            name.set(win.name)
            matScalex = win.matScalex
            matScaley = win.matScaley
            borderSize = win.borderSize
            textAlign = win.textAlign.code
            textAlignx = win.textAlignx
            textAligny = win.textAligny
            background = win.background!!
            flags = win.flags
            textShadow = win.textShadow.code
            text.set(win.text)
            visible.set(win.visible)
            rect.set(win.rect)
            backColor.set(win.backColor)
            matColor.set(win.matColor)
            foreColor.set(win.foreColor)
            borderColor.set(win.borderColor)
            textScale.set(win.textScale)
            rotate.set(win.rotate)
            shear.set(win.shear)
            backGroundName.set(win.backGroundName)
            if (backGroundName.Length() != 0) {
                background = DeclManager.declManager.FindMaterial(backGroundName.data)
                background!!.SetSort(Material.SS_GUI.toFloat())
                background!!.SetImageClassifications(1) // just for resource tracking
            }
            backGroundName.SetMaterialPtr(background!!)

            // 
            //  added parent
            mParent = win.GetParent()
            // 
            hideCursor.set(win.hideCursor)
            val parent = win.GetParent()
            if (parent != null) {
                if (text.NeedsUpdate()) {
                    //                    if(DBG_idSimpleWindow++==26)
//                    if(DBG_idSimpleWindow++==27)
                    parent.AddUpdateVar(text)
                    //                    System.out.println(">>" + this);
                }
                if (visible.NeedsUpdate()) {
                    parent.AddUpdateVar(visible)
                }
                if (rect.NeedsUpdate()) {
                    parent.AddUpdateVar(rect)
                }
                if (backColor.NeedsUpdate()) {
                    parent.AddUpdateVar(backColor)
                }
                if (matColor.NeedsUpdate()) {
                    parent.AddUpdateVar(matColor)
                }
                if (foreColor.NeedsUpdate()) {
                    parent.AddUpdateVar(foreColor)
                }
                if (borderColor.NeedsUpdate()) {
                    parent.AddUpdateVar(borderColor)
                }
                if (textScale.NeedsUpdate()) {
                    parent.AddUpdateVar(textScale)
                }
                if (rotate.NeedsUpdate()) {
                    parent.AddUpdateVar(rotate)
                }
                if (shear.NeedsUpdate()) {
                    parent.AddUpdateVar(shear)
                }
                if (backGroundName.NeedsUpdate()) {
                    parent.AddUpdateVar(backGroundName)
                }
            }
        }
    }
}