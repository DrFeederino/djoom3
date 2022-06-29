package neo.Renderer

import neo.Renderer.Image.idImage
import neo.Renderer.Material.cullType_t
import neo.Renderer.tr_local.copyRenderCommand_t
import neo.Renderer.tr_local.drawSurfsCommand_t
import neo.Renderer.tr_local.emptyCommand_t
import neo.Renderer.tr_local.glstate_t
import neo.Renderer.tr_local.renderCommand_t
import neo.Renderer.tr_local.setBufferCommand_t
import neo.Renderer.tr_local.tmu_t
import neo.TempDump
import neo.framework.Common
import neo.idlib.containers.CInt
import neo.sys.win_glimp
import neo.sys.win_shared
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 *
 */
object tr_backend {
    /*
     ====================
     RB_ExecuteBackEndCommands

     This function will be called syncronously if running without
     smp extensions, or asyncronously by another thread.
     ====================
     */
    var backEndStartTime = 0
    var backEndFinishTime = 0

    /*
     ====================
     GL_State
     This routine is responsible for setting the most commonly changed state
     ====================
     */
    private var DBG_GL_State = 0

    /*
     ======================
     RB_SetDefaultGLState

     This should initialize all GL state that any part of the entire program
     may touch, including the editor.
     ======================
     */
    fun RB_SetDefaultGLState() {
        var i: Int
        RB_LogComment("--- R_SetDefaultGLState ---\n")
        qgl.qglClearDepth(1.0)
        qgl.qglColor4f(1f, 1f, 1f, 1f)

        // the vertex array is always enabled
        qgl.qglEnableClientState(GL11.GL_VERTEX_ARRAY)
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)

        //
        // make sure our GL state vector is set correctly
        //
        tr_local.backEnd.glState = glstate_t() //memset(backEnd.glState, 0, sizeof(backEnd.glState));
        tr_local.backEnd.glState.forceGlState = true
        qgl.qglColorMask(1, 1, 1, 1)
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglEnable(GL11.GL_BLEND)
        qgl.qglEnable(GL11.GL_SCISSOR_TEST)
        qgl.qglEnable(GL11.GL_CULL_FACE)
        qgl.qglDisable(GL11.GL_LIGHTING)
        qgl.qglDisable(GL11.GL_LINE_STIPPLE)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        qgl.qglDepthMask(qgl.qGL_TRUE)
        qgl.qglDepthFunc(GL11.GL_ALWAYS)
        qgl.qglCullFace(GL11.GL_FRONT_AND_BACK)
        qgl.qglShadeModel(GL11.GL_SMOOTH)
        if (RenderSystem_init.r_useScissor.GetBool()) {
            qgl.qglScissor(0, 0, tr_local.glConfig.vidWidth, tr_local.glConfig.vidHeight)
        }
        i = tr_local.glConfig.maxTextureUnits - 1
        while (i >= 0) {
            GL_SelectTexture(i)

            // object linear texgen is our default
            qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            qgl.qglTexGenf(GL11.GL_Q, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
            GL_TexEnv(GL11.GL_MODULATE)
            qgl.qglDisable(GL11.GL_TEXTURE_2D)
            if (tr_local.glConfig.texture3DAvailable) {
                qgl.qglDisable(GL12.GL_TEXTURE_3D)
            }
            if (tr_local.glConfig.cubeMapAvailable) {
                qgl.qglDisable(GL13.GL_TEXTURE_CUBE_MAP /*_EXT*/)
            }
            i--
        }
    }

    /*
     ====================
     RB_LogComment
     ====================
     */
    fun RB_LogComment(vararg comment: Any?) {
//   va_list marker;
        if (TempDump.NOT(tr_local.tr.logFile)) {
            return
        }
        fprintf(tr_local.tr.logFile, "// ")
        //	va_start( marker, comment );
        vfprintf(tr_local.tr.logFile, *comment)
        //	va_end( marker );
    }

    //=============================================================================
    /*
     ====================
     GL_SelectTexture
     ====================
     */
    fun GL_SelectTexture(unit: Int) {
        if (tr_local.backEnd.glState.currenttmu == unit) {
            return
        }
        if (unit < 0 || unit >= tr_local.glConfig.maxTextureUnits && unit >= tr_local.glConfig.maxTextureImageUnits) {
            Common.common.Warning("GL_SelectTexture: unit = %d", unit)
            return
        }
        qgl.qglActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
        qgl.qglClientActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
        RB_LogComment("glActiveTextureARB( %d );\nglClientActiveTextureARB( %d );\n", unit, unit)
        tr_local.backEnd.glState.currenttmu = unit
    }

    /*
     ====================
     GL_Cull
     This handles the flipping needed when the view being
     rendered is a mirored view.
     ====================
     */
    fun GL_Cull(cullType: Int) {
        if (tr_local.backEnd.glState.faceCulling == cullType) {
            return
        }
        if (cullType == cullType_t.CT_TWO_SIDED.ordinal) {
            qgl.qglDisable(GL11.GL_CULL_FACE)
        } else {
            if (tr_local.backEnd.glState.faceCulling == cullType_t.CT_TWO_SIDED.ordinal) {
                qgl.qglEnable(GL11.GL_CULL_FACE)
            }
            if (cullType == cullType_t.CT_BACK_SIDED.ordinal) {
                if (tr_local.backEnd.viewDef.isMirror) {
                    qgl.qglCullFace(GL11.GL_FRONT)
                } else {
                    qgl.qglCullFace(GL11.GL_BACK)
                }
            } else {
                if (tr_local.backEnd.viewDef.isMirror) {
                    qgl.qglCullFace(GL11.GL_BACK)
                } else {
                    qgl.qglCullFace(GL11.GL_FRONT)
                }
            }
        }
        tr_local.backEnd.glState.faceCulling = cullType
    }

    fun GL_Cull(cullType: Enum<*>) {
        GL_Cull(cullType.ordinal)
    }

    /*
     ====================
     GL_TexEnv
     ====================
     */
    fun GL_TexEnv(env: Int) {
        val tmu: tmu_t?
        tmu = tr_local.backEnd.glState.tmu[tr_local.backEnd.glState.currenttmu]
        if (env == tmu.texEnv) {
            return
        }
        tmu.texEnv = env
        when (env) {
            GL13.GL_COMBINE, GL11.GL_MODULATE, GL11.GL_REPLACE, GL11.GL_DECAL, GL11.GL_ADD -> qgl.qglTexEnvi(
                GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE,
                env
            )
            else -> Common.common.Error("GL_TexEnv: invalid env '%d' passed\n", env)
        }
    }

    /*
     =================
     GL_ClearStateDelta
     Clears the state delta bits, so the next GL_State
     will set every item
     =================
     */
    fun GL_ClearStateDelta() {
        tr_local.backEnd.glState.forceGlState = true
    }

    /*
     ============================================================================

     RENDER BACK END THREAD FUNCTIONS

     ============================================================================
     */
    fun GL_State(stateBits: Int) {
        val diff: Int
        DBG_GL_State++
        if (!RenderSystem_init.r_useStateCaching.GetBool() || tr_local.backEnd.glState.forceGlState) {
            // make sure everything is set all the time, so we
            // can see if our delta checking is screwing up
            diff = -1
            tr_local.backEnd.glState.forceGlState = false
        } else {
            diff = stateBits xor tr_local.backEnd.glState.glStateBits
            if (0 == diff) {
                return
            }
        }

        //
        // check depthFunc bits
        //
        if (diff and (tr_local.GLS_DEPTHFUNC_EQUAL or tr_local.GLS_DEPTHFUNC_LESS or tr_local.GLS_DEPTHFUNC_ALWAYS) != 0) {
            if (stateBits and tr_local.GLS_DEPTHFUNC_EQUAL != 0) {
                qgl.qglDepthFunc(GL11.GL_EQUAL)
            } else if (stateBits and tr_local.GLS_DEPTHFUNC_ALWAYS != 0) {
                qgl.qglDepthFunc(GL11.GL_ALWAYS)
            } else {
                qgl.qglDepthFunc(GL11.GL_LEQUAL)
            }
        }

        //
        // check blend bits
        //
        if (diff and (tr_local.GLS_SRCBLEND_BITS or tr_local.GLS_DSTBLEND_BITS) != 0) {
            val   /*GLenum*/srcFactor: Int
            val dstFactor: Int
            when (stateBits and tr_local.GLS_SRCBLEND_BITS) {
                tr_local.GLS_SRCBLEND_ZERO -> srcFactor = GL11.GL_ZERO
                tr_local.GLS_SRCBLEND_ONE -> srcFactor = GL11.GL_ONE
                tr_local.GLS_SRCBLEND_DST_COLOR -> srcFactor = GL11.GL_DST_COLOR
                tr_local.GLS_SRCBLEND_ONE_MINUS_DST_COLOR -> srcFactor = GL11.GL_ONE_MINUS_DST_COLOR
                tr_local.GLS_SRCBLEND_SRC_ALPHA -> srcFactor = GL11.GL_SRC_ALPHA
                tr_local.GLS_SRCBLEND_ONE_MINUS_SRC_ALPHA -> srcFactor = GL11.GL_ONE_MINUS_SRC_ALPHA
                tr_local.GLS_SRCBLEND_DST_ALPHA -> srcFactor = GL11.GL_DST_ALPHA
                tr_local.GLS_SRCBLEND_ONE_MINUS_DST_ALPHA -> srcFactor = GL11.GL_ONE_MINUS_DST_ALPHA
                tr_local.GLS_SRCBLEND_ALPHA_SATURATE -> srcFactor = GL11.GL_SRC_ALPHA_SATURATE
                else -> {
                    srcFactor = GL11.GL_ONE
                    Common.common.Error("GL_State: invalid src blend state bits\n")
                }
            }
            when (stateBits and tr_local.GLS_DSTBLEND_BITS) {
                tr_local.GLS_DSTBLEND_ZERO -> dstFactor = GL11.GL_ZERO
                tr_local.GLS_DSTBLEND_ONE -> dstFactor = GL11.GL_ONE
                tr_local.GLS_DSTBLEND_SRC_COLOR -> dstFactor = GL11.GL_SRC_COLOR
                tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_COLOR -> dstFactor = GL11.GL_ONE_MINUS_SRC_COLOR
                tr_local.GLS_DSTBLEND_SRC_ALPHA -> dstFactor = GL11.GL_SRC_ALPHA
                tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA -> dstFactor = GL11.GL_ONE_MINUS_SRC_ALPHA
                tr_local.GLS_DSTBLEND_DST_ALPHA -> dstFactor = GL11.GL_DST_ALPHA
                tr_local.GLS_DSTBLEND_ONE_MINUS_DST_ALPHA -> dstFactor = GL11.GL_ONE_MINUS_DST_ALPHA
                else -> {
                    dstFactor = GL11.GL_ONE
                    Common.common.Error("GL_State: invalid dst blend state bits\n")
                }
            }
            //            qglEnable(34336);
//            qglEnable(34820);
//            if (srcFactor == 770) {
            qgl.qglBlendFunc(srcFactor, dstFactor)
            //            }
//            System.out.printf("GL_State(%d, %d)--%d;\n", srcFactor, dstFactor, DBG_GL_State);
        }

        //
        // check depthmask
        //
        if (diff and tr_local.GLS_DEPTHMASK != 0) {
            if (stateBits and tr_local.GLS_DEPTHMASK != 0) {
                qgl.qglDepthMask(qgl.qGL_FALSE)
            } else {
                qgl.qglDepthMask(qgl.qGL_TRUE)
            }
        }

        //
        // check colormask
        //
        if (diff and (tr_local.GLS_REDMASK or tr_local.GLS_GREENMASK or tr_local.GLS_BLUEMASK or tr_local.GLS_ALPHAMASK) != 0) {
            val r = stateBits and tr_local.GLS_REDMASK == 0
            val g = stateBits and tr_local.GLS_GREENMASK == 0
            val b = stateBits and tr_local.GLS_BLUEMASK == 0
            val a = stateBits and tr_local.GLS_ALPHAMASK == 0
            qgl.qglColorMask(r, g, b, a) //solid backgroundus
        }

        //
        // fill/line mode
        //
        if (diff and tr_local.GLS_POLYMODE_LINE != 0) {
            if (stateBits and tr_local.GLS_POLYMODE_LINE != 0) {
                qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
            } else {
                qgl.qglPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
            }
        }

        //
        // alpha test
        //
        if (diff and tr_local.GLS_ATEST_BITS != 0) {
            if (tr_local.backEnd.viewDef.numDrawSurfs == 5) {
                val temp = tr_local.backEnd.viewDef.drawSurfs[3]
                //                backEnd.viewDef.drawSurfs[0] =
//                backEnd.viewDef.drawSurfs[1] =
//                backEnd.viewDef.drawSurfs[2] =
//                backEnd.viewDef.drawSurfs[3] =
//                backEnd.viewDef.drawSurfs[4] =
//                temp;
////                temp.shaderRegisters[0] = 330.102997f;
////                temp.shaderRegisters[1] = 1.00000000f;
////                temp.shaderRegisters[2] = 1.00000000f;
////                temp.shaderRegisters[3] = 1.00000000f;
////                temp.shaderRegisters[4] = 1.00000000f;
////                temp.shaderRegisters[5] = 0.000000000f;
////                temp.shaderRegisters[6] = 0.000000000f;
////                temp.shaderRegisters[7] = 0.000000000f;
////                temp.shaderRegisters[8] = 0.000000000f;
////                temp.shaderRegisters[9] = 0.000000000f;
////                temp.shaderRegisters[10] = 0.000000000f;
////                temp.shaderRegisters[11] = 0.000000000f;
////                temp.shaderRegisters[12] = 0.000000000f;
////                temp.shaderRegisters[13] = 0.000000000f;
////                temp.shaderRegisters[14] = 0.000000000f;
////                temp.shaderRegisters[15] = 0.000000000f;
////                temp.shaderRegisters[16] = 0.000000000f;
////                temp.shaderRegisters[17] = 0.000000000f;
////                temp.shaderRegisters[18] = 0.000000000f;
////                temp.shaderRegisters[19] = 0.000000000f;
////                temp.shaderRegisters[20] = 0.000000000f;
////                temp.shaderRegisters[21] = 1.00000000f;
////                temp.shaderRegisters[22] = 0.00999999978f;
////                temp.shaderRegisters[23] = 3.30102992f;
////                temp.shaderRegisters[24] = 0.0120000001f;
////                temp.shaderRegisters[25] = 3.96123600f;
////                temp.shaderRegisters[26] = 0.000000000f;
            }
            when (stateBits and tr_local.GLS_ATEST_BITS) {
                0 -> qgl.qglDisable(GL11.GL_ALPHA_TEST)
                tr_local.GLS_ATEST_EQ_255 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_EQUAL, 1f)
                }
                tr_local.GLS_ATEST_LT_128 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_LESS, 0.5f)
                }
                tr_local.GLS_ATEST_GE_128 -> {
                    qgl.qglEnable(GL11.GL_ALPHA_TEST)
                    qgl.qglAlphaFunc(GL11.GL_GEQUAL, 0.5f)
                }
                else -> assert(false)
            }
        }
        tr_local.backEnd.glState.glStateBits = stateBits
    }

    /*
     =============
     RB_SetGL2D

     This is not used by the normal game paths, just by some tools
     =============
     */
    fun RB_SetGL2D() {
        // set 2D virtual screen size
        qgl.qglViewport(0, 0, tr_local.glConfig.vidWidth, tr_local.glConfig.vidHeight)
        if (RenderSystem_init.r_useScissor.GetBool()) {
            qgl.qglScissor(0, 0, tr_local.glConfig.vidWidth, tr_local.glConfig.vidHeight)
        }
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 640.0, 480.0, 0.0, 0.0, 1.0) // always assume 640x480 virtual coordinates
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        qgl.qglLoadIdentity()
        GL_State(
            tr_local.GLS_DEPTHFUNC_ALWAYS
                    or tr_local.GLS_SRCBLEND_SRC_ALPHA
                    or tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA
        )
        GL_Cull(cullType_t.CT_TWO_SIDED)
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
    }

    /*
     =============
     RB_SetBuffer

     =============
     */
    fun RB_SetBuffer(data: Any) {
        val cmd: setBufferCommand_t?

        // see which draw buffer we want to render the frame to
        cmd = data as setBufferCommand_t
        tr_local.backEnd.frameCount = cmd.frameCount
        qgl.qglDrawBuffer(cmd.buffer)

        // clear screen for debugging
        // automatically enable this with several other debug tools
        // that might leave unrendered portions of the screen
        if (RenderSystem_init.r_clear.GetFloat() != 0f || RenderSystem_init.r_clear.GetString()!!.length != 1 || RenderSystem_init.r_lockSurfaces.GetBool() || RenderSystem_init.r_singleArea.GetBool() || RenderSystem_init.r_showOverDraw.GetBool()) {
            try {
                Scanner(RenderSystem_init.r_clear.GetString()).use { sscanf ->
//		if ( sscanf( r_clear.GetString(), "%f %f %f", c[0], c[1], c[2] ) == 3 ) {
                    sscanf.useLocale(Locale.US)
                    val c = floatArrayOf(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat())
                    //if 3 floats are parsed
                    qgl.qglClearColor(c[0], c[1], c[2], 1f)
                }
            } catch (elif: NoSuchElementException) {
                if (RenderSystem_init.r_clear.GetInteger() == 2) {
                    qgl.qglClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                } else if (RenderSystem_init.r_showOverDraw.GetBool()) {
                    qgl.qglClearColor(1.0f, 1.0f, 1.0f, 1.0f)
                } else {
                    qgl.qglClearColor(0.4f, 0.0f, 0.25f, 1.0f)
                }
            }
            qgl.qglClear(GL11.GL_COLOR_BUFFER_BIT)
        }
    }

    /*
     ===============
     RB_ShowImages

     Draw all the images to the screen, on top of whatever
     was there.  This is used to test for texture thrashing.
     ===============
     */
    fun RB_ShowImages() {
        var i: Int
        var image: idImage?
        var x: Float
        var y: Float
        var w: Float
        var h: Float
        val start: Int
        val end: Int
        RB_SetGL2D()

        //qglClearColor( 0.2, 0.2, 0.2, 1 );
        //qglClear( GL_COLOR_BUFFER_BIT );
        qgl.qglFinish()
        start = win_shared.Sys_Milliseconds()
        i = 0
        while (i < Image.globalImages.images.Num()) {
            image = Image.globalImages.images.get(i)
            if (image.texNum == idImage.Companion.TEXTURE_NOT_LOADED && image.partialImage == null) {
                i++
                continue
            }
            w = (tr_local.glConfig.vidWidth / 20).toFloat()
            h = (tr_local.glConfig.vidHeight / 15).toFloat()
            x = i % 20 * w
            y = i / 20 * h

            // show in proportional size in mode 2
            if (RenderSystem_init.r_showImages.GetInteger() == 2) {
                w *= image.uploadWidth._val / 512.0f
                h *= image.uploadHeight._val / 512.0f
            }
            image.Bind()
            qgl.qglBegin(GL11.GL_QUADS)
            qgl.qglTexCoord2f(0f, 0f)
            qgl.qglVertex2f(x, y)
            qgl.qglTexCoord2f(1f, 0f)
            qgl.qglVertex2f(x + w, y)
            qgl.qglTexCoord2f(1f, 1f)
            qgl.qglVertex2f(x + w, y + h)
            qgl.qglTexCoord2f(0f, 1f)
            qgl.qglVertex2f(x, y + h)
            qgl.qglEnd()
            i++
        }
        qgl.qglFinish()
        end = win_shared.Sys_Milliseconds()
        Common.common.Printf("%d msec to draw all images\n", end - start)
    }

    /*
     =============
     RB_SwapBuffers

     =============
     */
    fun RB_SwapBuffers(data: Any?) {
        // texture swapping test
        if (RenderSystem_init.r_showImages.GetInteger() != 0) {
            RB_ShowImages()
        }

        // force a gl sync if requested
        if (RenderSystem_init.r_finish.GetBool()) {
            qgl.qglFinish()
        }
        RB_LogComment("***************** RB_SwapBuffers *****************\n\n\n")

        // don't flip if drawing to front buffer
        if (!RenderSystem_init.r_frontBuffer.GetBool()) {
            win_glimp.GLimp_SwapBuffers()
        }
    }

    /*
     =============
     RB_CopyRender

     Copy part of the current framebuffer to an image
     =============
     */
    fun RB_CopyRender(data: Any?) {
        val cmd: copyRenderCommand_t?
        cmd = data as copyRenderCommand_t
        if (RenderSystem_init.r_skipCopyTexture.GetBool()) {
            return
        }
        RB_LogComment("***************** RB_CopyRender *****************\n")
        if (cmd.image != null) {
            val imageWidth = CInt(cmd.imageWidth)
            val imageHeight = CInt(cmd.imageHeight)
            cmd.image!!.CopyFramebuffer(cmd.x, cmd.y, imageWidth, imageHeight, false)
            cmd.imageWidth = imageWidth._val
            cmd.imageHeight = imageHeight._val
        }
    }

    fun RB_ExecuteBackEndCommands(cmds: emptyCommand_t) {
        // r_debugRenderToTexture
        var c_draw3d = 0
        var c_draw2d = 0
        var c_setBuffers = 0
        var c_swapBuffers = 0
        var c_copyRenders = 0
        if (renderCommand_t.RC_NOP == cmds.commandId && null == cmds.next) {
            return
        }
        backEndStartTime = win_shared.Sys_Milliseconds()

        // needed for editor rendering
        RB_SetDefaultGLState()

        // upload any image loads that have completed
        Image.globalImages.CompleteBackgroundImageLoads()
        var cmds = cmds as emptyCommand_t?
        while (cmds != null) {
            when (cmds.commandId) {
                renderCommand_t.RC_NOP -> {}
                renderCommand_t.RC_DRAW_VIEW -> {
                    tr_render.RB_DrawView(cmds)
                    if ((cmds as drawSurfsCommand_t).viewDef.viewEntitys != null) {
                        c_draw3d++
                    } else {
                        c_draw2d++
                    }
                }
                renderCommand_t.RC_SET_BUFFER -> {
                    RB_SetBuffer(cmds)
                    c_setBuffers++
                }
                renderCommand_t.RC_SWAP_BUFFERS -> {
                    RB_SwapBuffers(cmds)
                    c_swapBuffers++
                }
                renderCommand_t.RC_COPY_RENDER -> {
                    RB_CopyRender(cmds)
                    c_copyRenders++
                }
                else -> Common.common.Error("RB_ExecuteBackEndCommands: bad commandId")
            }
            cmds = cmds.next
        }

        // go back to the default texture so the editor doesn't mess up a bound image
        qgl.qglBindTexture(GL11.GL_TEXTURE_2D, 0)
        tr_local.backEnd.glState.tmu[0].current2DMap = -1

        // stop rendering on this thread
        backEndFinishTime = win_shared.Sys_Milliseconds()
        tr_local.backEnd.pc.msec = backEndFinishTime - backEndStartTime
        if (RenderSystem_init.r_debugRenderToTexture.GetInteger() == 1) {
            Common.common.Printf(
                "3d: %d, 2d: %d, SetBuf: %d, SwpBuf: %d, CpyRenders: %d, CpyFrameBuf: %d\n",
                c_draw3d,
                c_draw2d,
                c_setBuffers,
                c_swapBuffers,
                c_copyRenders,
                tr_local.backEnd.c_copyFrameBuffer
            )
            tr_local.backEnd.c_copyFrameBuffer = 0
        }
    }

    private fun fprintf(logFile: FileChannel?, string: String) {
        if (null == logFile) {
            return
        }
        try {
            logFile.write(ByteBuffer.wrap(string.toByteArray()))
        } catch (ex: IOException) {
            Logger.getLogger(tr_backend::class.java.name).log(Level.SEVERE, null, ex)
        }
    }

    private fun vfprintf(logFile: FileChannel?, vararg comments: Any?) {
        if (null == logFile) {
            return
        }
        try {
            var bla: String = ""
            for (c in comments) {
                bla += c
            }
            logFile.write(ByteBuffer.wrap(bla.toByteArray()))
        } catch (ex: IOException) {
            Logger.getLogger(tr_backend::class.java.name).log(Level.SEVERE, null, ex)
        }
    }
}