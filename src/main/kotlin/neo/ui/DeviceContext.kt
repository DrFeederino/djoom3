package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.Renderer.RenderSystem
import neo.Renderer.RenderSystem.fontInfoEx_t
import neo.Renderer.RenderSystem.fontInfo_t
import neo.Renderer.RenderSystem.glyphInfo_t
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.Rectangle.idRectangle
import neo.ui.Rectangle.idRegion

/**
 *
 */
class DeviceContext {

    class idDeviceContext {
        private val cursorImages: Array<idMaterial> = Array(CURSOR.CURSOR_COUNT.ordinal) { idMaterial() }
        private val scrollBarImages: Array<idMaterial> = Array(SCROLLBAR.SCROLLBAR_COUNT.ordinal) { idMaterial() }
        private var activeFont: fontInfoEx_t? = null

        //
        private val clipRects: idList<idRectangle> = idList()

        //
        private lateinit var cursor: CURSOR

        //
        //
        //
        private var enableClipping = false

        // ~idDeviceContext() { }
        private val fontLang: idStr = idStr()
        private val fontName: idStr = idStr()
        private var initialized = false

        //
        //        public void EnableLocalization();
        //
        //
        private val mat: idMat3 = idMat3()

        //
        private var mbcs = false
        private val origin: idVec3 = idVec3()

        //
        private var overStrikeMode = false
        private var useFont: fontInfo_t? = null

        //
        private var vidHeight = 0f
        private var vidWidth = 0f
        private var whiteImage: idMaterial = idMaterial()
        private var xScale = 0f
        private var yScale = 0f

        fun Init() {
            xScale = 0f
            SetSize(VIRTUAL_WIDTH.toFloat(), VIRTUAL_HEIGHT.toFloat())
            whiteImage = DeclManager.declManager.FindMaterial("guis/assets/white.tga")!!
            whiteImage.SetSort(Material.SS_GUI.toFloat())
            mbcs = false
            SetupFonts()
            activeFont = fonts[0]
            colorPurple.set(idVec4(1f, 0f, 1f, 1f))
            colorOrange.set(idVec4(1f, 1f, 0f, 1f))
            colorYellow.set(idVec4(0f, 1f, 1f, 1f))
            colorGreen.set(idVec4(0f, 1f, 0f, 1f))
            colorBlue.set(idVec4(0f, 0f, 1f, 1f))
            colorRed.set(idVec4(1f, 0f, 0f, 1f))
            colorWhite.set(idVec4(1f, 1f, 1f, 1f))
            colorBlack.set(idVec4(0f, 0f, 0f, 1f))
            colorNone.set(idVec4(0f, 0f, 0f, 0f))
            cursorImages[CURSOR.CURSOR_ARROW.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/guicursor_arrow.tga")!!
            cursorImages[CURSOR.CURSOR_HAND.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/guicursor_hand.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_HBACK.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbarh.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_VBACK.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbarv.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_THUMB.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbar_thumb.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_RIGHT.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbar_right.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_LEFT.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbar_left.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_UP.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbar_up.tga")!!
            scrollBarImages[SCROLLBAR.SCROLLBAR_DOWN.ordinal] =
                DeclManager.declManager.FindMaterial("ui/assets/scrollbar_down.tga")!!
            cursorImages[CURSOR.CURSOR_ARROW.ordinal].SetSort(Material.SS_GUI.toFloat())
            cursorImages[CURSOR.CURSOR_HAND.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_HBACK.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_VBACK.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_THUMB.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_RIGHT.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_LEFT.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_UP.ordinal].SetSort(Material.SS_GUI.toFloat())
            scrollBarImages[SCROLLBAR.SCROLLBAR_DOWN.ordinal].SetSort(Material.SS_GUI.toFloat())
            cursor = CURSOR.CURSOR_ARROW
            enableClipping = true
            overStrikeMode = true
            mat.Identity()
            origin.Zero()
            initialized = true
        }

        fun Shutdown() {
            fontName.Clear()
            clipRects.Clear()
            fonts.Clear()
            Clear()
        }

        fun Initialized(): Boolean {
            return initialized
        }

        fun GetTransformInfo(origin: idVec3, mat: idMat3) {
            mat.set(this.mat)
            origin.set(this.origin)
        }

        fun SetTransformInfo(origin: idVec3, mat: idMat3) {
            this.origin.set(origin)
            this.mat.set(mat)
        }

        @JvmOverloads
        fun DrawMaterial(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            mat: idMaterial,
            color: idVec4,
            scalex: Float = 1.0f /*= 1.0f*/
        ) {
            DrawMaterial(x, y, w, h, mat, color, scalex, 1.0f)
        }

        fun DrawMaterial(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            mat: idMaterial,
            color: idVec4,
            scaleX: Float /*= 1.0f*/,
            scaleY: Float /*= 1.0f*/
        ) {
            var scaleX = scaleX
            var scaleY = scaleY
            RenderSystem.renderSystem.SetColor(color)
            val s0 = floatArrayOf(0f)
            val s1 = floatArrayOf(0f)
            val t0 = floatArrayOf(0f)
            val t1 = floatArrayOf(0f)
            val x1 = floatArrayOf(x)
            val y1 = floatArrayOf(y)
            val w1 = floatArrayOf(w)
            val h1 = floatArrayOf(h)
            //
//  handle negative scales as well
            if (scaleX < 0) {
                w1[0] = w1[0] * -1
                scaleX *= -1f
            }
            if (scaleY < 0) {
                h1[0] = h1[0] * -1
                scaleY *= -1f
            }
            //
            if (w1[0] < 0) {    // flip about vertical
                w1[0] = -w1[0]
                s0[0] = 1 * scaleX
                s1[0] = 0f
            } else {
                s0[0] = 0f
                s1[0] = 1 * scaleX
            }
            if (h1[0] < 0) {    // flip about horizontal
                h1[0] = -h1[0]
                t0[0] = 1 * scaleY
                t1[0] = 0f
            } else {
                t0[0] = 0f
                t1[0] = 1 * scaleY
            }
            if (ClippedCoords(x1, y1, w1, h1, s0, t0, s1, t1)) {
                return
            }
            AdjustCoords(x1, y1, w1, h1)
            DrawStretchPic(x1[0], y1[0], w1[0], h1[0], s0[0], t0[0], s1[0], t1[0], mat)
            bla99++
        }

        fun DrawRect(x: Float, y: Float, width: Float, height: Float, size: Float, color: idVec4) {
            val x1 = floatArrayOf(x)
            val y1 = floatArrayOf(y)
            val w1 = floatArrayOf(width)
            val h1 = floatArrayOf(height)
            if (color.w == 0f) {
                return
            }
            RenderSystem.renderSystem.SetColor(color)
            if (ClippedCoords(x1, y1, w1, h1, null, null, null, null)) {
                return
            }
            AdjustCoords(x1, y1, w1, h1)
            DrawStretchPic(x1[0], y1[0], size, h1[0], 0f, 0f, 0f, 0f, whiteImage)
            DrawStretchPic(x1[0] + w1[0] - size, y1[0], size, h1[0], 0f, 0f, 0f, 0f, whiteImage)
            DrawStretchPic(x1[0], y1[0], w1[0], size, 0f, 0f, 0f, 0f, whiteImage)
            DrawStretchPic(x1[0], y1[0] + h1[0] - size, w1[0], size, 0f, 0f, 0f, 0f, whiteImage)
        }

        fun DrawFilledRect(x: Float, y: Float, width: Float, height: Float, color: idVec4) {
            val x1 = floatArrayOf(x)
            val y1 = floatArrayOf(y)
            val w1 = floatArrayOf(width)
            val h1 = floatArrayOf(height)
            if (color.w == 0f) {
                return
            }
            RenderSystem.renderSystem.SetColor(color)
            if (ClippedCoords(x1, y1, w1, h1, null, null, null, null)) {
                return
            }
            AdjustCoords(x1, y1, w1, h1)
            DrawStretchPic(x1[0], y1[0], w1[0], h1[0], 0f, 0f, 0f, 0f, whiteImage)
            aaaa++
        }

        @JvmOverloads
        fun DrawText(
            text: String?,
            textScale: Float,
            textAlign: Int,
            color: idVec4,
            rectDraw: idRectangle,
            wrap: Boolean,
            cursor: Int = -1 /*= -1*/,
            calcOnly: Boolean = false /*= false*/,
            breaks: idList<Int>? = null /*= NULL*/,
            limit: Int = 0 /*= 0*/
        ): Int {
            var text = text
            var cursor = cursor
            if (text == null || text.isEmpty()) {
                return -1 // why call DrawText if text is garbage?
            }
            var p: Char
            var textPtr: Char
            var p_i: Int
            var newLinePtr = 0
            val buff = CharArray(1024)
            var len: Int
            var newLine: Int
            var newLineWidth: Int
            var count: Int
            var y: Float
            var textWidth: Float
            val charSkip = (MaxCharWidth(textScale) + 1).toFloat()
            val lineSkip = MaxCharHeight(textScale).toFloat()
            val cursorSkip: Float = if (cursor >= 0) charSkip else 0f
            var lineBreak: Boolean
            var wordBreak: Boolean
            SetFontByScale(textScale)
            if (!calcOnly) {
                if (cursor == 0) {
                    RenderSystem.renderSystem.SetColor(color)
                    DrawEditCursor(rectDraw.x, lineSkip + rectDraw.y, textScale)
                }
                return idMath.FtoiFast(rectDraw.w / charSkip)
            }
            if (!text.contains("\\0")) {
                text += '\u0000' //TODO:we temporarily append a '\0' here, but we should refactor the code.
            }
            y = lineSkip + rectDraw.y
            len = 0
            buff[0] = '\u0000'
            newLine = 0
            newLineWidth = 0
            p_i = 0
            breaks?.Append(0)
            count = 0
            textWidth = 0f
            lineBreak = false
            wordBreak = false
            while (p_i < text.length) {
                p = text[p_i]
                if (p == '\n' || p == '\r' || p == '\u0000') {
                    lineBreak = true
                    if (p == '\n' && text[p_i + 1] == '\r' || p == '\r' && text[p_i + 1] == '\n') {
                        p = text[p_i++]
                    }
                }
                var nextCharWidth =
                    (if (idStr.CharIsPrintable(p.code)) CharWidth(p, textScale) else cursorSkip).toInt()
                // FIXME: this is a temp hack until the guis can be fixed not not overflow the bounding rectangles
                //	      the side-effect is that list boxes and edit boxes will draw over their scroll bars
                //  The following line and the !linebreak in the if statement below should be removed
                nextCharWidth = 0
                if (!lineBreak && textWidth + nextCharWidth > rectDraw.w) {
                    // The next character will cause us to overflow, if we haven't yet found a suitable
                    // break spot, set it to be this character
                    if (len > 0 && newLine == 0) {
                        newLine = len
                        newLinePtr = p_i
                        newLineWidth = textWidth.toInt()
                    }
                    wordBreak = true
                } else if (lineBreak || wrap && (p == ' ' || p == '\t')) {
                    // The next character is in view, so if we are a break character, store our position
                    newLine = len
                    newLinePtr = p_i + 1
                    newLineWidth = textWidth.toInt()
                }
                if (lineBreak || wordBreak) {
                    var x = rectDraw.x
                    if (textAlign == TempDump.etoi(ALIGN.ALIGN_RIGHT)) {
                        x = rectDraw.x + rectDraw.w - newLineWidth
                    } else if (textAlign == TempDump.etoi(ALIGN.ALIGN_CENTER)) {
                        x = rectDraw.x + (rectDraw.w - newLineWidth) / 2
                    }
                    if (wrap || newLine > 0) {
                        buff[newLine] = '\u0000'

                        // This is a special case to handle breaking in the middle of a word.
                        // if we didn't do this, the cursor would appear on the end of this line
                        // and the beginning of the next.
                        if (wordBreak && cursor >= newLine && newLine == len) {
                            cursor++
                        }
                    }
                    if (!calcOnly) {
                        count += DrawText(x, y, textScale, color, TempDump.ctos(buff), 0f, 0, 0, cursor)
                    }
                    if (cursor < newLine) {
                        cursor = -1
                    } else if (cursor >= 0) {
                        cursor -= newLine + 1
                    }
                    if (!wrap) {
                        return newLine
                    }
                    if (limit != 0 && count > limit || p == '\u0000') {
                        break
                    }
                    y += lineSkip + 5
                    if (!calcOnly && y > rectDraw.Bottom()) {
                        break
                    }
                    p_i = newLinePtr //TODO:check if any of the pointers are actually incremented.
                    breaks?.Append(p_i)
                    len = 0
                    newLine = 0
                    newLineWidth = 0
                    textWidth = 0f
                    lineBreak = false
                    wordBreak = false
                    continue
                }
                buff[len++] = p
                p_i++
                buff[len] = '\u0000'
                // update the width
                bla++
                if (buff[len - 1].code != Str.C_COLOR_ESCAPE && (len <= 1 || buff[len - 2].code != Str.C_COLOR_ESCAPE)) {
                    textWidth += textScale * useFont!!.glyphScale * useFont!!.glyphs[buff[len - 1].code].xSkip
                    // Jim Dosé, I don't know who you are..but I hate you.
                }
            }
            return idMath.FtoiFast(rectDraw.w / charSkip)
        }

        fun DrawText(
            text: idStr,
            textScale: Float,
            textAlign: Int,
            color: idVec4,
            rectDraw: idRectangle,
            wrap: Boolean,
            cursor: Int /*= -1*/,
            calcOnly: Boolean /*= false*/,
            breaks: idList<Int>? = null /*= NULL*/
        ): Int {
            return DrawText(text.toString(), textScale, textAlign, color, rectDraw, wrap, cursor, calcOnly, breaks, 0)
        }

        fun DrawText(
            text: idStr,
            textScale: Float,
            textAlign: Int,
            color: idVec4,
            rectDraw: idRectangle,
            wrap: Boolean,
            cursor: Int
        ): Int {
            return DrawText(text.toString(), textScale, textAlign, color, rectDraw, wrap, cursor, false, null, 0)
        }

        fun DrawMaterialRect(x: Float, y: Float, w: Float, h: Float, size: Float, mat: idMaterial, color: idVec4) {
            if (color.w == 0f) {
                return
            }
            RenderSystem.renderSystem.SetColor(color)
            DrawMaterial(x, y, size, h, mat, color)
            DrawMaterial(x + w - size, y, size, h, mat, color)
            DrawMaterial(x, y, w, size, mat, color)
            DrawMaterial(x, y + h - size, w, size, mat, color)
        }

        fun DrawStretchPic(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            s0: Float,
            t0: Float,
            s1: Float,
            t1: Float,
            shader: idMaterial
        ) {
            val verts = arrayOf(idDrawVert(), idDrawVert(), idDrawVert(), idDrawVert())
            val indexes = IntArray(6)
            indexes[0] = 3
            indexes[1] = 0
            indexes[2] = 2
            indexes[3] = 2
            indexes[4] = 0
            indexes[5] = 1
            verts[0].xyz[0] = x
            verts[0].xyz[1] = y
            verts[0].xyz[2] = 0f
            verts[0].st[0] = s0
            verts[0].st[1] = t0
            verts[0].normal[0] = 0f
            verts[0].normal[1] = 0f
            verts[0].normal[2] = 1f
            verts[0].tangents[0][0] = 1f
            verts[0].tangents[0][1] = 0f
            verts[0].tangents[0][2] = 0f
            verts[0].tangents[1][0] = 0f
            verts[0].tangents[1][1] = 1f
            verts[0].tangents[1][2] = 0f
            verts[1].xyz[0] = x + w
            verts[1].xyz[1] = y
            verts[1].xyz[2] = 0f
            verts[1].st[0] = s1
            verts[1].st[1] = t0
            verts[1].normal[0] = 0f
            verts[1].normal[1] = 0f
            verts[1].normal[2] = 1f
            verts[1].tangents[0][0] = 1f
            verts[1].tangents[0][1] = 0f
            verts[1].tangents[0][2] = 0f
            verts[1].tangents[1][0] = 0f
            verts[1].tangents[1][1] = 1f
            verts[1].tangents[1][2] = 0f
            verts[2].xyz[0] = x + w
            verts[2].xyz[1] = y + h
            verts[2].xyz[2] = 0f
            verts[2].st[0] = s1
            verts[2].st[1] = t1
            verts[2].normal[0] = 0f
            verts[2].normal[1] = 0f
            verts[2].normal[2] = 1f
            verts[2].tangents[0][0] = 1f
            verts[2].tangents[0][1] = 0f
            verts[2].tangents[0][2] = 0f
            verts[2].tangents[1][0] = 0f
            verts[2].tangents[1][1] = 1f
            verts[2].tangents[1][2] = 0f
            verts[3].xyz[0] = x
            verts[3].xyz[1] = y + h
            verts[3].xyz[2] = 0f
            verts[3].st[0] = s0
            verts[3].st[1] = t1
            verts[3].normal[0] = 0f
            verts[3].normal[1] = 0f
            verts[3].normal[2] = 1f
            verts[3].tangents[0][0] = 1f
            verts[3].tangents[0][1] = 0f
            verts[3].tangents[0][2] = 0f
            verts[3].tangents[1][0] = 0f
            verts[3].tangents[1][1] = 1f
            verts[3].tangents[1][2] = 0f
            val identity = !mat.IsIdentity()
            if (identity) {
                verts[0].xyz.minusAssign(origin)
                verts[0].xyz.timesAssign(mat)
                verts[0].xyz.plusAssign(origin)
                verts[1].xyz.minusAssign(origin)
                verts[1].xyz.timesAssign(mat)
                verts[1].xyz.plusAssign(origin)
                verts[2].xyz.minusAssign(origin)
                verts[2].xyz.timesAssign(mat)
                verts[2].xyz.plusAssign(origin)
                verts[3].xyz.minusAssign(origin)
                verts[3].xyz.timesAssign(mat)
                verts[3].xyz.plusAssign(origin)
            }
            RenderSystem.renderSystem.DrawStretchPic(verts, indexes, 4, 6, shader, identity)
        }

        fun DrawMaterialRotated(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            mat: idMaterial,
            color: idVec4,
            scalex: Float /*= 1.0*/,
            scaley: Float /*= 1.0*/,
            angle: Float /*= 0.0f*/
        ) {
            var scalex = scalex
            var scaley = scaley
            RenderSystem.renderSystem.SetColor(color)
            val s0 = FloatArray(1)
            val s1 = FloatArray(1)
            val t0 = FloatArray(1)
            val t1 = FloatArray(1)
            val x1 = floatArrayOf(x)
            val y1 = floatArrayOf(y)
            val w1 = floatArrayOf(w)
            val h1 = floatArrayOf(h)
            //
            //  handle negative scales as well
            if (scalex < 0) {
                w1[0] = w1[0] * -1
                scalex *= -1f
            }
            if (scaley < 0) {
                h1[0] = h1[0] * -1
                scaley *= -1f
            }
            //
            if (w1[0] < 0) {    // flip about vertical
                w1[0] = -w1[0]
                s0[0] = 1 * scalex
                s1[0] = 0f
            } else {
                s0[0] = 0f
                s1[0] = 1 * scalex
            }
            if (h1[0] < 0) {    // flip about horizontal
                h1[0] = -h1[0]
                t0[0] = 1 * scaley
                t1[0] = 0f
            } else {
                t0[0] = 0f
                t1[0] = 1 * scaley
            }
            if (angle == 0f && ClippedCoords(x1, y1, w1, h1, s0, t0, s1, t1)) {
                return
            }
            AdjustCoords(x1, y1, w1, h1)
            DrawStretchPicRotated(x1[0], y1[0], w1[0], h1[0], s0[0], t0[0], s1[0], t1[0], mat, angle)
        }

        fun DrawStretchPicRotated(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            s0: Float,
            t0: Float,
            s1: Float,
            t1: Float,
            shader: idMaterial,
            angle: Float /*= 0.0f*/
        ) {
            val verts = Array(4) { idDrawVert() }
            val indexes = IntArray(6)
            indexes[0] = 3
            indexes[1] = 0
            indexes[2] = 2
            indexes[3] = 2
            indexes[4] = 0
            indexes[5] = 1
            verts[0].xyz[0] = x
            verts[0].xyz[1] = y
            verts[0].xyz[2] = 0f
            verts[0].st[0] = s0
            verts[0].st[1] = t0
            verts[0].normal[0] = 0f
            verts[0].normal[1] = 0f
            verts[0].normal[2] = 1f
            verts[0].tangents[0][0] = 1f
            verts[0].tangents[0][1] = 0f
            verts[0].tangents[0][2] = 0f
            verts[0].tangents[1][0] = 0f
            verts[0].tangents[1][1] = 1f
            verts[0].tangents[1][2] = 0f
            verts[1].xyz[0] = x + w
            verts[1].xyz[1] = y
            verts[1].xyz[2] = 0f
            verts[1].st[0] = s1
            verts[1].st[1] = t0
            verts[1].normal[0] = 0f
            verts[1].normal[1] = 0f
            verts[1].normal[2] = 1f
            verts[1].tangents[0][0] = 1f
            verts[1].tangents[0][1] = 0f
            verts[1].tangents[0][2] = 0f
            verts[1].tangents[1][0] = 0f
            verts[1].tangents[1][1] = 1f
            verts[1].tangents[1][2] = 0f
            verts[2].xyz[0] = x + w
            verts[2].xyz[1] = y + h
            verts[2].xyz[2] = 0f
            verts[2].st[0] = s1
            verts[2].st[1] = t1
            verts[2].normal[0] = 0f
            verts[2].normal[1] = 0f
            verts[2].normal[2] = 1f
            verts[2].tangents[0][0] = 1f
            verts[2].tangents[0][1] = 0f
            verts[2].tangents[0][2] = 0f
            verts[2].tangents[1][0] = 0f
            verts[2].tangents[1][1] = 1f
            verts[2].tangents[1][2] = 0f
            verts[3].xyz[0] = x
            verts[3].xyz[1] = y + h
            verts[3].xyz[2] = 0f
            verts[3].st[0] = s0
            verts[3].st[1] = t1
            verts[3].normal[0] = 0f
            verts[3].normal[1] = 0f
            verts[3].normal[2] = 1f
            verts[3].tangents[0][0] = 1f
            verts[3].tangents[0][1] = 0f
            verts[3].tangents[0][2] = 0f
            verts[3].tangents[1][0] = 0f
            verts[3].tangents[1][1] = 1f
            verts[3].tangents[1][2] = 0f
            val ident = !mat.IsIdentity()
            if (ident) {
                verts[0].xyz.minusAssign(origin)
                verts[0].xyz.timesAssign(mat)
                verts[0].xyz.plusAssign(origin)
                verts[1].xyz.minusAssign(origin)
                verts[1].xyz.timesAssign(mat)
                verts[1].xyz.plusAssign(origin)
                verts[2].xyz.minusAssign(origin)
                verts[2].xyz.timesAssign(mat)
                verts[2].xyz.plusAssign(origin)
                verts[3].xyz.minusAssign(origin)
                verts[3].xyz.timesAssign(mat)
                verts[3].xyz.plusAssign(origin)
            }

            //Generate a translation so we can translate to the center of the image rotate and draw
            val origTrans = idVec3()
            origTrans.x = x + w / 2
            origTrans.y = y + h / 2
            origTrans.z = 0f

            //Rotate the verts about the z axis before drawing them
            val rotz = idMat4()
            rotz.Identity()
            val sinAng = idMath.Sin(angle)
            val cosAng = idMath.Cos(angle)
            rotz[0, 0] = cosAng
            rotz[0, 1] = sinAng
            rotz[1, 0] = -sinAng
            rotz[1, 1] = cosAng
            for (i in 0..3) {
                //Translate to origin
                verts[i].xyz.minusAssign(origTrans)

                //Rotate
                verts[i].xyz.set(rotz.times(verts[i].xyz))

                //Translate back
                verts[i].xyz.plusAssign(origTrans)
            }
            RenderSystem.renderSystem.DrawStretchPic(verts, indexes, 4, 6, shader, angle != 0f)
        }

        fun CharWidth(c: Char, scale: Float): Int {
            val glyph: glyphInfo_t
            val useScale: Float
            SetFontByScale(scale)
            val font = useFont!!
            useScale = scale * font.glyphScale
            glyph = font.glyphs[c.code]
            return idMath.FtoiFast(glyph.xSkip * useScale)
        }

        fun TextWidth(text: String?, scale: Float, limit: Int): Int {
            var i: Int
            var width: Int
            SetFontByScale(scale)
            val glyphs = useFont!!.glyphs
            if (text == null) {
                return 0
            }
            width = 0
            if (limit > 0) {
                i = 0
                while (text[i] != '\u0000' && i < limit) {
                    if (idStr.IsColor(text.substring(i))) {
                        i++
                    } else {
                        width += glyphs[text[i].code].xSkip
                    }
                    i++
                }
            } else {
                i = 0
                while (text[i] != '\u0000') {
                    if (idStr.IsColor(text.substring(i))) {
                        i++
                    } else {
                        width += glyphs[text[i].code].xSkip
                    }
                    i++
                }
            }
            return idMath.FtoiFast(scale * useFont!!.glyphScale * width)
        }

        fun TextWidth(text: idStr?, scale: Float, limit: Int): Int {
            return TextWidth(text?.toString(), scale, limit)
        }

        fun TextHeight(text: String?, scale: Float, limit: Int): Int {
            var len: Int
            var count: Int
            var max: Float
            var glyph: glyphInfo_t
            val useScale: Float
            var s = 0 //text;
            SetFontByScale(scale)
            val font = useFont!!
            useScale = scale * font.glyphScale
            max = 0f
            if (text != null) {
                len = text.length
                if (limit > 0 && len > limit) {
                    len = limit
                }
                count = 0
                while (count < len) {
                    if (idStr.IsColor(text.substring(s))) {
                        s += 2
                        //                        continue;
                    } else {
                        glyph = font.glyphs[text[s].code]
                        if (max < glyph.height) {
                            max = glyph.height.toFloat()
                        }
                        s++
                        count++
                    }
                }
            }
            return idMath.FtoiFast(max * useScale)
        }

        fun MaxCharHeight(scale: Float): Int {
            SetFontByScale(scale)
            val useScale = scale * useFont!!.glyphScale
            return idMath.FtoiFast(activeFont!!.maxHeight * useScale)
        }

        fun MaxCharWidth(scale: Float): Int {
            SetFontByScale(scale)
            val useScale = scale * useFont!!.glyphScale
            return idMath.FtoiFast(activeFont!!.maxWidth * useScale)
        }

        fun FindFont(name: String): Int {
            val c = fonts.Num()
            for (i in 0 until c) {
                if (idStr.Icmp(name, fonts[i].name) == 0) {
                    return i
                }
            }

            // If the font was not found, try to register it
            val fileName = idStr(name)
            fileName.Replace("fonts", Str.va("fonts/%s", fontLang))
            val fontInfo = fontInfoEx_t()
            val index = fonts.Append(fontInfo)
            return if (RenderSystem.renderSystem.RegisterFont(fileName.toString(), fonts[index])) {
                fonts[index].name =
                    name //idStr.Copynz(fonts.oGet(index).name, name, fonts.oGet(index).name.length());
                index
            } else {
                idLib.common.Printf("Could not register font %s [%s]\n", name, fileName)
                -1
            }
        }

        fun SetupFonts() {
            fonts.SetGranularity(1)

            fontLang.set(idLib.cvarSystem.GetCVarString("sys_lang"))
            val font = fontLang.toString()

            // western european languages can use the english font
            if ("french" == font || "german" == font || "spanish" == font || "italian" == font) {
                fontLang.set("english")
            }

            // Default font has to be added first
            FindFont("fonts")
        }

        fun GetTextRegion(
            text: String?,
            textScale: Float,
            rectDraw: idRectangle?,
            xStart: Float,
            yStart: Float
        ): idRegion? {
// if (false){
            // const char	*p, *textPtr, *newLinePtr;
            // char		buff[1024];
            // int			len, textWidth, newLine, newLineWidth;
            // float		y;

            // float charSkip = MaxCharWidth(textScale) + 1;
            // float lineSkip = MaxCharHeight(textScale);
            // textWidth = 0;
            // newLinePtr = NULL;
// }
            return null
            /*
             if (text == NULL) {
             return;
             }

             textPtr = text;
             if (*textPtr == '\0') {
             return;
             }

             y = lineSkip + rectDraw.y + yStart;
             len = 0;
             buff[0] = '\0';
             newLine = 0;
             newLineWidth = 0;
             p = textPtr;

             textWidth = 0;
             while (p) {
             if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\0') {
             newLine = len;
             newLinePtr = p + 1;
             newLineWidth = textWidth;
             }

             if ((newLine && textWidth > rectDraw.w) || *p == '\n' || *p == '\0') {
             if (len) {

             float x = rectDraw.x ;

             buff[newLine] = '\0';
             DrawText(x, y, textScale, color, buff, 0, 0, 0);
             if (!wrap) {
             return;
             }
             }

             if (*p == '\0') {
             break;
             }

             y += lineSkip + 5;
             p = newLinePtr;
             len = 0;
             newLine = 0;
             newLineWidth = 0;
             continue;
             }

             buff[len++] = *p++;
             buff[len] = '\0';
             textWidth = TextWidth( buff, textScale, -1 );
             }
             */
        }

        fun SetSize(width: Float, height: Float) {
            vidWidth = VIRTUAL_WIDTH.toFloat()
            vidHeight = VIRTUAL_HEIGHT.toFloat()
            yScale = 0f
            xScale = yScale
            if (width != 0f && height != 0f) {
                xScale = vidWidth * (1.0f / width)
                yScale = vidHeight * (1.0f / height)
            }
        }

        fun GetScrollBarImage(index: Int): idMaterial? {
//if (false){
//	const char	*p, *textPtr, *newLinePtr;
//	char		buff[1024];
//	int			len, textWidth, newLine, newLineWidth;
//	float		y;
//
//	float charSkip = MaxCharWidth(textScale) + 1;
//	float lineSkip = MaxCharHeight(textScale);
//
//	textWidth = 0;
//	newLinePtr = NULL;
//}
            return null
            /*
             if (text == NULL) {
             return;
             }

             textPtr = text;
             if (*textPtr == '\0') {
             return;
             }

             y = lineSkip + rectDraw.y + yStart;
             len = 0;
             buff[0] = '\0';
             newLine = 0;
             newLineWidth = 0;
             p = textPtr;

             textWidth = 0;
             while (p) {
             if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\0') {
             newLine = len;
             newLinePtr = p + 1;
             newLineWidth = textWidth;
             }

             if ((newLine && textWidth > rectDraw.w) || *p == '\n' || *p == '\0') {
             if (len) {

             float x = rectDraw.x ;

             buff[newLine] = '\0';
             DrawText(x, y, textScale, color, buff, 0, 0, 0);
             if (!wrap) {
             return;
             }
             }

             if (*p == '\0') {
             break;
             }

             y += lineSkip + 5;
             p = newLinePtr;
             len = 0;
             newLine = 0;
             newLineWidth = 0;
             continue;
             }

             buff[len++] = *p++;
             buff[len] = '\0';
             textWidth = TextWidth( buff, textScale, -1 );
             }
             */
        }

        fun DrawCursor(x: FloatArray, y: FloatArray, size: Float) {
            val s = floatArrayOf(size)
            if (x[0] < 0) {
                x[0] = 0f
            }
            if (x[0] >= vidWidth) {
                x[0] = vidWidth
            }
            if (y[0] < 0) {
                y[0] = 0f
            }
            if (y[0] >= vidHeight) {
                y[0] = vidHeight
            }
            RenderSystem.renderSystem.SetColor(colorWhite)
            AdjustCoords(x, y, s, s)
            DrawStretchPic(x[0], y[0], s[0], s[0], 0f, 0f, 1f, 1f, cursorImages[cursor.ordinal])
        }

        fun SetCursor(n: Int) {
            cursor =
                if (n < CURSOR.CURSOR_ARROW.ordinal || n >= CURSOR.CURSOR_COUNT.ordinal) CURSOR.CURSOR_ARROW else CURSOR.values()[n]
        }

        fun AdjustCoords(x: FloatArray?, y: FloatArray?, w: FloatArray?, h: FloatArray?) {
            if (x != null) {
                x[0] *= xScale
            }
            if (y != null) {
                y[0] *= yScale
            }
            if (w != null) {
                w[0] *= xScale
            }
            if (h != null) {
                h[0] *= yScale
            }
        }

        @JvmOverloads
        fun ClippedCoords(
            x: FloatArray,
            y: FloatArray,
            w: FloatArray,
            h: FloatArray,
            s1: FloatArray? = null,
            t1: FloatArray? = null,
            s2: FloatArray? = null,
            t2: FloatArray? = null
        ): Boolean {
            if (enableClipping == false || clipRects.Num() == 0) {
                return false
            }
            var c = clipRects.Num()
            while (--c > 0) {
                val clipRect = clipRects[c]!!
                val ox = x[0]
                val oy = y[0]
                val ow = w[0]
                val oh = h[0]
                if (ow <= 0.0f || oh <= 0.0f) {
                    break
                }
                if (x[0] < clipRect.x) {
                    w[0] -= clipRect.x - x[0]
                    x[0] = clipRect.x
                } else if (x[0] > clipRect.x + clipRect.w) {
                    h[0] = 0f
                    y[0] = h[0]
                    w[0] = y[0]
                    x[0] = w[0]
                }
                if (y[0] < clipRect.y) {
                    h[0] -= clipRect.y - y[0]
                    y[0] = clipRect.y
                } else if (y[0] > clipRect.y + clipRect.h) {
                    h[0] = 0f
                    y[0] = h[0]
                    w[0] = y[0]
                    x[0] = w[0]
                }
                if (w[0] > clipRect.w) {
                    w[0] = clipRect.w - x[0] + clipRect.x
                } else if (x[0] + w[0] > clipRect.x + clipRect.w) {
                    w[0] = clipRect.Right() - x[0]
                }
                if (h[0] > clipRect.h) {
                    h[0] = clipRect.h - y[0] + clipRect.y
                } else if (y[0] + h[0] > clipRect.y + clipRect.h) {
                    h[0] = clipRect.Bottom() - y[0]
                }
                if (s1 != null && s2 != null && t1 != null && t2 != null && ow > 0) {
                    var ns1: Float
                    var ns2: Float
                    var nt1: Float
                    var nt2: Float
                    // upper left
                    var u = (x[0] - ox) / ow
                    ns1 = s1[0] * (1.0f - u) + s2[0] * u

                    // upper right
                    u = (x[0] + w[0] - ox) / ow
                    ns2 = s1[0] * (1.0f - u) + s2[0] * u

                    // lower left
                    u = (y[0] - oy) / oh
                    nt1 = t1[0] * (1.0f - u) + t2[0] * u

                    // lower right
                    u = (y[0] + h[0] - oy) / oh
                    nt2 = t1[0] * (1.0f - u) + t2[0] * u

                    // set values
                    s1[0] = ns1
                    s2[0] = ns2
                    t1[0] = nt1
                    t2[0] = nt2
                }
            }
            return w[0] == 0f || h[0] == 0f
        }

        fun PushClipRect(x: Float, y: Float, w: Float, h: Float) {
            clipRects.Append(idRectangle(x, y, w, h))
        }

        fun PushClipRect(r: idRectangle) {
            clipRects.Append(r)
        }

        fun PopClipRect() {
            if (clipRects.Num() != 0) {
                clipRects.RemoveIndex(clipRects.Num() - 1)
            }
        }

        fun EnableClipping(b: Boolean) {
            enableClipping = b
        }

        fun SetFont(num: Int) {
            activeFont = if (num >= 0 && num < fonts.Num()) {
                fonts[num]
            } else {
                fonts[0]
            }
        }

        fun SetOverStrike(b: Boolean) {
            overStrikeMode = b
        }

        fun GetOverStrike(): Boolean {
            return overStrikeMode
        }

        fun DrawEditCursor(x: Float, y: Float, scale: Float) {
            if (Common.com_ticNumber shr 4 and 1 != 0) {
                return
            }
            SetFontByScale(scale)
            val useScale = scale * useFont!!.glyphScale
            val glyph2 = useFont!!.glyphs[if (overStrikeMode) '_'.code else '|'.code]
            val yadj = useScale * glyph2.top
            PaintChar(
                x,
                y - yadj,
                glyph2.imageWidth.toFloat(),
                glyph2.imageHeight.toFloat(),
                useScale,
                glyph2.s,
                glyph2.t,
                glyph2.s2,
                glyph2.t2,
                glyph2.glyph!!
            )
        }

        private fun DrawText(
            x: Float,
            y: Float,
            scale: Float,
            color: idVec4,
            text: String?,
            adjust: Float,
            limit: Int,
            style: Int,
            cursor: Int /*= -1*/
        ): Int {
            var x = x
            var len: Int
            var count: Int
            var newColor: idVec4
            var glyph: glyphInfo_t
            val useScale: Float
            SetFontByScale(scale)
            useScale = scale * useFont!!.glyphScale
            count = 0
            if (text != null && text.isNotEmpty() && color.w != 0.0f) {
                var s: Char = text[0] //(const unsigned char*)text;
                var s_i = 0
                RenderSystem.renderSystem.SetColor(color)
                //		memcpy(newColor[0], color[0], sizeof(idVec4));
                newColor = idVec4(color)
                len = text.length
                if (limit > 0 && len > limit) {
                    len = limit
                }
                while (s_i < len && text[s_i].also { s = it }.code != 0 && count < len) {
                    if (s.code < RenderSystem.GLYPH_START || s.code > RenderSystem.GLYPH_END) {
                        s_i++
                        continue
                    }
                    glyph = useFont!!.glyphs[text[s_i].also { s = it }.code]

                    //
                    // int yadj = Assets.textFont.glyphs[text[i]].bottom +
                    // Assets.textFont.glyphs[text[i]].top; float yadj = scale *
                    // (Assets.textFont.glyphs[text[i]].imageHeight -
                    // Assets.textFont.glyphs[text[i]].height);
                    //
                    if (idStr.IsColor(TempDump.ctos(s.toString().toCharArray()))) {
                        d1++
                        if (text[s_i + 1].code == Str.C_COLOR_DEFAULT) {
                            newColor = color
                        } else {
                            newColor = idStr.ColorForIndex(text[s_i + 1].code)
                            newColor[3] = color[3]
                        }
                        if (cursor == count || cursor == count + 1) {
                            var partialSkip = (glyph.xSkip * useScale + adjust) / 5.0f
                            if (cursor == count) {
                                partialSkip *= 2.0f
                            } else {
                                RenderSystem.renderSystem.SetColor(newColor)
                            }
                            DrawEditCursor(x - partialSkip, y, scale)
                        }
                        RenderSystem.renderSystem.SetColor(newColor)
                        s_i += 2
                        count += 2
                        continue
                    } else {
                        d2++
                        val yadj = useScale * glyph.top
                        PaintChar(
                            x,
                            y - yadj,
                            glyph.imageWidth.toFloat(),
                            glyph.imageHeight.toFloat(),
                            useScale,
                            glyph.s,
                            glyph.t,
                            glyph.s2,
                            glyph.t2,
                            glyph.glyph!!
                        )
                        if (cursor == count) {
                            DrawEditCursor(x, y, scale)
                        }
                        x += glyph.xSkip * useScale + adjust
                        s_i++
                        count++
                    }
                }
                if (cursor == len) {
                    DrawEditCursor(x, y, scale)
                }
            }
            return count
        }

        private fun PaintChar(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            scale: Float,
            s: Float,
            t: Float,
            s2: Float,
            t2: Float,
            hShader: idMaterial
        ) {
            val w = floatArrayOf(width * scale)
            val h = floatArrayOf(height * scale)
            val x1 = floatArrayOf(x)
            val y1 = floatArrayOf(y)
            val s1 = floatArrayOf(s)
            val t1 = floatArrayOf(t)
            val s3 = floatArrayOf(s2)
            val t3 = floatArrayOf(t2)
            if (ClippedCoords(x1, y1, w, h, s1, t1, s3, t3)) {
                return
            }
            AdjustCoords(x1, y1, w, h)
            DrawStretchPic(x1[0], y1[0], w[0], h[0], s1[0], t1[0], s3[0], t3[0], hShader)
            asdasdasd++
        }

        private fun SetFontByScale(scale: Float) {
            if (scale <= gui_smallFontLimit.GetFloat()) {
                useFont = activeFont!!.fontInfoSmall
                activeFont!!.maxHeight = activeFont!!.maxHeightSmall
                activeFont!!.maxWidth = activeFont!!.maxWidthSmall
            } else if (scale <= gui_mediumFontLimit.GetFloat()) {
                useFont = activeFont!!.fontInfoMedium
                activeFont!!.maxHeight = activeFont!!.maxHeightMedium
                activeFont!!.maxWidth = activeFont!!.maxWidthMedium
            } else {
                useFont = activeFont!!.fontInfoLarge
                activeFont!!.maxHeight = activeFont!!.maxHeightLarge
                activeFont!!.maxWidth = activeFont!!.maxWidthLarge
            }
        }

        private fun Clear() {
            initialized = false
            useFont = null
            activeFont = null
            mbcs = false
        }

        enum class ALIGN {
            ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT
        }

        enum class CURSOR {
            CURSOR_ARROW, CURSOR_HAND, CURSOR_COUNT
        }

        enum class SCROLLBAR {
            SCROLLBAR_HBACK, SCROLLBAR_VBACK, SCROLLBAR_THUMB, SCROLLBAR_RIGHT, SCROLLBAR_LEFT, SCROLLBAR_UP, SCROLLBAR_DOWN, SCROLLBAR_COUNT
        }

        companion object {
            val colorBlack: idVec4 = idVec4()
            val colorBlue: idVec4 = idVec4()
            val colorGreen: idVec4 = idVec4()
            val colorNone: idVec4 = idVec4()
            val colorOrange: idVec4 = idVec4()
            val colorPurple: idVec4 = idVec4()
            val colorRed: idVec4 = idVec4()
            val colorWhite: idVec4 = idVec4()
            val colorYellow: idVec4 = idVec4()
            var aaaa = 0
            var asdasdasd = 0
            var bla = 0
            var bla99 = 0
            var d1 = 0
            var d2 = 0

            //
            private val fonts: idList<fontInfoEx_t> = idList()
        }

        init {
            Clear()
        }
    }

    companion object {
        const val BLINK_DIVISOR = 200
        const val VIRTUAL_HEIGHT = 480
        const val VIRTUAL_WIDTH = 640
        val gui_mediumFontLimit: idCVar =
            idCVar("gui_mediumFontLimit", "0.60", CVarSystem.CVAR_GUI or CVarSystem.CVAR_ARCHIVE, "")
        val gui_smallFontLimit: idCVar =
            idCVar("gui_smallFontLimit", "0.30", CVarSystem.CVAR_GUI or CVarSystem.CVAR_ARCHIVE, "")
    }
}