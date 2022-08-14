package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.tr_local.backEndCounters_t
import neo.Renderer.tr_local.drawSurfsCommand_t
import neo.Renderer.tr_local.emptyCommand_t
import neo.Renderer.tr_local.idRenderSystemLocal
import neo.Renderer.tr_local.performanceCounters_t
import neo.Renderer.tr_local.renderCommand_t
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_local.viewEntity_s
import neo.TempDump
import neo.TempDump.CPP_class.Char
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.sys.win_glimp
import java.nio.ByteBuffer

/**
 *
 */
object RenderSystem {
    const val BIGCHAR_HEIGHT = 16
    const val BIGCHAR_WIDTH = 16
    const val GLYPH_CHAREND = 127
    const val GLYPH_CHARSTART = 32
    const val GLYPH_END = 255

    // font support
    const val GLYPH_START = 0
    const val GLYPHS_PER_FONT = GLYPH_END - GLYPH_START + 1
    const val SCREEN_HEIGHT = 480

    //
    // all drawing is done to a 640 x 480 virtual screen size
    // and will be automatically scaled to the real resolution
    const val SCREEN_WIDTH = 640
    const val SMALLCHAR_HEIGHT = 16
    const val SMALLCHAR_WIDTH = 8
    var renderSystem: idRenderSystem = tr_local.tr

    /*
     =====================
     R_PerformanceCounters

     This prints both front and back end counters, so it should
     only be called when the back end thread is idle.
     =====================
     */
    fun R_PerformanceCounters() {
        if (RenderSystem_init.r_showPrimitives.GetInteger() != 0) {
            val megaBytes = Image.globalImages.SumOfUsedImages() / (1024 * 1024.0f)
            if (RenderSystem_init.r_showPrimitives.GetInteger() > 1) {
                Common.common.Printf(
                    "v:%d ds:%d t:%d/%d v:%d/%d st:%d sv:%d image:%5.1f MB\n",
                    tr_local.tr.pc.c_numViews,
                    tr_local.backEnd.pc.c_drawElements + tr_local.backEnd.pc.c_shadowElements,
                    tr_local.backEnd.pc.c_drawIndexes / 3,
                    (tr_local.backEnd.pc.c_drawIndexes - tr_local.backEnd.pc.c_drawRefIndexes) / 3,
                    tr_local.backEnd.pc.c_drawVertexes,
                    tr_local.backEnd.pc.c_drawVertexes - tr_local.backEnd.pc.c_drawRefVertexes,
                    tr_local.backEnd.pc.c_shadowIndexes / 3,
                    tr_local.backEnd.pc.c_shadowVertexes,
                    megaBytes
                )
            } else {
                Common.common.Printf(
                    "views:%d draws:%d tris:%d (shdw:%d) (vbo:%d) image:%5.1f MB\n",
                    tr_local.tr.pc.c_numViews,
                    tr_local.backEnd.pc.c_drawElements + tr_local.backEnd.pc.c_shadowElements,
                    (tr_local.backEnd.pc.c_drawIndexes + tr_local.backEnd.pc.c_shadowIndexes) / 3,
                    tr_local.backEnd.pc.c_shadowIndexes / 3,
                    tr_local.backEnd.pc.c_vboIndexes / 3,
                    megaBytes
                )
            }
        }
        if (RenderSystem_init.r_showDynamic.GetBool()) {
            Common.common.Printf(
                "callback:%d md5:%d dfrmVerts:%d dfrmTris:%d tangTris:%d guis:%d\n",
                tr_local.tr.pc.c_entityDefCallbacks,
                tr_local.tr.pc.c_generateMd5,
                tr_local.tr.pc.c_deformedVerts,
                tr_local.tr.pc.c_deformedIndexes / 3,
                tr_local.tr.pc.c_tangentIndexes / 3,
                tr_local.tr.pc.c_guiSurfs
            )
        }
        if (RenderSystem_init.r_showCull.GetBool()) {
            Common.common.Printf(
                "%d sin %d sclip  %d sout %d bin %d bout\n",
                tr_local.tr.pc.c_sphere_cull_in, tr_local.tr.pc.c_sphere_cull_clip, tr_local.tr.pc.c_sphere_cull_out,
                tr_local.tr.pc.c_box_cull_in, tr_local.tr.pc.c_box_cull_out
            )
        }
        if (RenderSystem_init.r_showAlloc.GetBool()) {
            Common.common.Printf("alloc:%d free:%d\n", tr_local.tr.pc.c_alloc, tr_local.tr.pc.c_free)
        }
        if (RenderSystem_init.r_showInteractions.GetBool()) {
            Common.common.Printf(
                "createInteractions:%d createLightTris:%d createShadowVolumes:%d\n",
                tr_local.tr.pc.c_createInteractions,
                tr_local.tr.pc.c_createLightTris,
                tr_local.tr.pc.c_createShadowVolumes
            )
        }
        if (RenderSystem_init.r_showDefs.GetBool()) {
            Common.common.Printf(
                "viewEntities:%d  shadowEntities:%d  viewLights:%d\n", tr_local.tr.pc.c_visibleViewEntities,
                tr_local.tr.pc.c_shadowViewEntities, tr_local.tr.pc.c_viewLights
            )
        }
        if (RenderSystem_init.r_showUpdates.GetBool()) {
            Common.common.Printf(
                "entityUpdates:%d  entityRefs:%d  lightUpdates:%d  lightRefs:%d\n",
                tr_local.tr.pc.c_entityUpdates, tr_local.tr.pc.c_entityReferences,
                tr_local.tr.pc.c_lightUpdates, tr_local.tr.pc.c_lightReferences
            )
        }
        if (RenderSystem_init.r_showMemory.GetBool()) {
            val m1 = if (tr_local.frameData != null) tr_local.frameData.memoryHighwater else 0
            Common.common.Printf("frameData: %d (%d)\n", tr_main.R_CountFrameData(), m1)
        }
        if (RenderSystem_init.r_showLightScale.GetBool()) {
            Common.common.Printf("lightScale: %f\n", tr_local.backEnd.pc.maxLightValue)
        }

//        memset(tr.pc, 0, sizeof(tr.pc));
        tr_local.tr.pc = performanceCounters_t()
        //        memset(backEnd.pc, 0, sizeof(backEnd.pc));
        tr_local.backEnd.pc = backEndCounters_t()
    }

    /*
     ====================
     R_IssueRenderCommands

     Called by R_EndFrame each frame
     ====================
     */
    fun R_IssueRenderCommands() {
        if (renderCommand_t.RC_NOP == tr_local.frameData.cmdHead.commandId && TempDump.NOT(tr_local.frameData.cmdHead.next)) {
            // nothing to issue
            return
        }

        // r_skipBackEnd allows the entire time of the back end
        // to be removed from performance measurements, although
        // nothing will be drawn to the screen.  If the prints
        // are going to a file, or r_skipBackEnd is later disabled,
        // usefull data can be received.
        //
        // r_skipRender is usually more useful, because it will still
        // draw 2D graphics
        if (!RenderSystem_init.r_skipBackEnd.GetBool()) {
            tr_backend.RB_ExecuteBackEndCommands(tr_local.frameData.cmdHead)
        }
        R_ClearCommandChain()
    }

    /*
     ============
     R_GetCommandBuffer

     Returns memory for a command buffer (stretchPicCommand_t,
     drawSurfsCommand_t, etc) and links it to the end of the
     current command chain.
     ============
     */
    fun R_GetCommandBuffer(command_t: emptyCommand_t): emptyCommand_t {
        val cmd: emptyCommand_t

//        cmd = R_FrameAlloc(bytes);
//        cmd.next = null;
        cmd = command_t //our little trick for downcasting. EDIT:??
        tr_local.frameData.cmdTail.next = cmd
        tr_local.frameData.cmdTail = cmd
        return cmd
    }

    /*
     ====================
     R_ClearCommandChain

     Called after every buffer submission
     and by R_ToggleSmpFrame
     ====================
     */
    fun R_ClearCommandChain() {
        // clear the command chain
        tr_local.frameData.cmdTail = emptyCommand_t()
        tr_local.frameData.cmdHead = tr_local.frameData.cmdTail // R_FrameAlloc(sizeof(frameData.cmdHead));
        tr_local.frameData.cmdHead.commandId = renderCommand_t.RC_NOP
        tr_local.frameData.cmdHead.next = null
    }

    /*
     =================
     R_ViewStatistics
     =================
     */
    fun R_ViewStatistics(parms: viewDef_s) {
        // report statistics about this view
        if (!RenderSystem_init.r_showSurfaces.GetBool()) {
            return
        }
        Common.common.Printf("view:%p surfs:%d\n", parms, parms.numDrawSurfs)
    }

    /*
     =============
     R_AddDrawViewCmd

     This is the main 3D rendering command.  A single scene may
     have multiple views if a mirror, portal, or dynamic texture is present.
     =============
     */
    fun R_AddDrawViewCmd(parms: viewDef_s) {
        var cmd: drawSurfsCommand_t
        R_GetCommandBuffer(drawSurfsCommand_t().also { cmd = it /*sizeof(cmd)*/ })
        cmd.commandId = renderCommand_t.RC_DRAW_VIEW
        cmd.viewDef = parms
        if (parms.viewEntitys != null) {
            // save the command for r_lockSurfaces debugging
            tr_local.tr.lockSurfacesCmd = cmd
        }
        tr_local.tr.pc.c_numViews++
        R_ViewStatistics(parms)
    }

    //=================================================================================
    /*
     ======================
     R_LockSurfaceScene

     r_lockSurfaces allows a developer to move around
     without changing the composition of the scene, including
     culling.  The only thing that is modified is the
     view position and axis, no front end work is done at all


     Add the stored off command again, so the new rendering will use EXACTLY
     the same surfaces, including all the culling, even though the transformation
     matricies have been changed.  This allow the culling tightness to be
     evaluated interactively.
     ======================
     */
    fun R_LockSurfaceScene(parms: viewDef_s) {
        var cmd: drawSurfsCommand_t
        var vModel: viewEntity_s?

        // set the matrix for world space to eye space
        tr_main.R_SetViewMatrix(parms)
        tr_local.tr.lockSurfacesCmd.viewDef!!.worldSpace = parms.worldSpace

        // update the view origin and axis, and all
        // the entity matricies
        vModel = tr_local.tr.lockSurfacesCmd.viewDef!!.viewEntitys
        while (vModel != null) {
            tr_main.myGlMultMatrix(
                vModel.modelMatrix,
                tr_local.tr.lockSurfacesCmd.viewDef!!.worldSpace.modelViewMatrix,
                vModel.modelViewMatrix
            )
            vModel = vModel.next
        }

        // add the stored off surface commands again
//        cmd = (drawSurfsCommand_t) R_GetCommandBuffer(sizeof(cmd));
        R_GetCommandBuffer(tr_local.tr.lockSurfacesCmd) //TODO:double check to make sure the casting and casting back preserves our values.
    }

    /*
     =============
     R_CheckCvars

     See if some cvars that we watch have changed
     =============
     */
    fun R_CheckCvars() {
        Image.globalImages.CheckCvars()

        // gamma stuff
        if (RenderSystem_init.r_gamma.IsModified() || RenderSystem_init.r_brightness.IsModified()) {
            RenderSystem_init.r_gamma.ClearModified()
            RenderSystem_init.r_brightness.ClearModified()
            RenderSystem_init.R_SetColorMappings()
        }

        // check for changes to logging state
        win_glimp.GLimp_EnableLogging(RenderSystem_init.r_logFile.GetInteger() != 0)
    }

    fun setRenderSystems(renderSystem: idRenderSystem) {
        tr_local.tr = renderSystem as idRenderSystemLocal
        RenderSystem.renderSystem = tr_local.tr
    }

    /*
     ===============================================================================

     idRenderSystem is responsible for managing the screen, which can have
     multiple idRenderWorld and 2D drawing done on it.

     ===============================================================================
     */
    class glconfig_s {
        var ARBFragmentProgramAvailable = false
        var ARBVertexBufferObjectAvailable = false
        var ARBVertexProgramAvailable = false
        var allowARB2Path = true
        var allowNV10Path = false
        var allowNV20Path = false

        //
        var allowNV30Path = false
        var allowR200Path = false
        var anisotropicAvailable = false

        //
        // ati r200 extensions
        var atiFragmentShaderAvailable = false

        //
        // ati r300
        var atiTwoSidedStencilAvailable = false

        //
        var colorBits = 0
        var depthBits = 0
        var stencilBits = 8
        var cubeMapAvailable = false
        var depthBoundsTestAvailable = false

        //
        var displayFrequency = 0
        var envDot3Available = false
        var extensions_string: String = ""

        //
        var glVersion // atof( version_string )
                = 0f

        //
        var isFullscreen = false

        //
        var isInitialized = false
        var maxTextureAnisotropy = 0f
        var maxTextureCoords = 0
        var maxTextureImageUnits = 0

        //
        //
        var maxTextureSize // queried from GL
                = 0
        var maxTextureUnits = 0

        //
        var multitextureAvailable = false
        var registerCombinersAvailable = false
        var renderer_string: String = ""
        var sharedTexturePaletteAvailable = false
        var texture3DAvailable = false
        var textureCompressionAvailable = false
        var textureEnvAddAvailable = false
        var textureEnvCombineAvailable = false
        var textureLODBiasAvailable = false
        var textureNonPowerOfTwoAvailable = false
        var twoSidedStencilAvailable = false
        var vendor_string: String = ""
        var version_string: String = ""

        //
        var vidWidth = 0
        var vidHeight // passed to R_BeginFrame
                = 0
        var wgl_extensions_string: String = ""
    }

    class glyphInfo_t {
        var glyph // shader with the glyph
                : idMaterial? = null
        var height // number of scan lines
                = 0
        var imageHeight // height of actual image
                = 0
        var imageWidth // width of actual image
                = 0
        var s // x offset in image where glyph starts
                = 0f
        var s2 = 0f
        var t // y offset in image where glyph starts
                = 0f
        var t2 = 0f
        var top // top of glyph in buffer
                = 0
        var xSkip // x adjustment
                = 0
        var bottom // bottom of glyph in buffer
                = 0
        var pitch // width for copying
                = 0

        // char				shaderName[32];
        var shaderName: String = ""

        companion object {
            @Transient
            val SIZE = (Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + java.lang.Float.SIZE
                    + TempDump.CPP_class.Pointer.SIZE //const idMaterial *	glyph
                    + Char.SIZE * 32)
        }
    }

    class fontInfo_t {
        var glyphScale = 0f
        var glyphs: Array<glyphInfo_t> = Array(GLYPHS_PER_FONT) { glyphInfo_t() }
        var name: StringBuilder = StringBuilder(64)

        companion object {
            @Transient
            val SIZE = (glyphInfo_t.SIZE * GLYPHS_PER_FONT
                    + java.lang.Float.SIZE
                    + Char.SIZE * 64)

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE
        }

    }

    class fontInfoEx_t {
        var fontInfoLarge: fontInfo_t = fontInfo_t()
        var fontInfoMedium: fontInfo_t = fontInfo_t()
        var fontInfoSmall: fontInfo_t = fontInfo_t()
        var maxHeight = 0
        var maxHeightLarge = 0
        var maxHeightMedium = 0
        var maxHeightSmall = 0
        var maxWidth = 0
        var maxWidthLarge = 0
        var maxWidthMedium = 0
        var maxWidthSmall = 0

        // char				name[64];
        var name: String = ""

        /**
         * memset(font, 0, sizeof(font));
         */
        fun clear() {
            fontInfoSmall = fontInfo_t()
            fontInfoMedium = fontInfo_t()
            fontInfoLarge = fontInfo_t()
            maxWidthLarge = 0
            maxHeightLarge = maxWidthLarge
            maxWidthMedium = maxHeightLarge
            maxHeightMedium = maxWidthMedium
            maxWidthSmall = maxHeightMedium
            maxHeightSmall = maxWidthSmall
            maxWidth = maxHeightSmall
            maxHeight = maxWidth
            name = ""
        }
    }

    abstract class idRenderSystem {
        // virtual					~idRenderSystem() {}
        // set up cvars and basic data structures, but don't
        // init OpenGL, so it can also be used for dedicated servers
        abstract fun Init()

        // only called before quitting
        abstract fun Shutdown()
        abstract fun InitOpenGL()
        abstract fun ShutdownOpenGL()
        abstract fun IsOpenGLRunning(): Boolean
        abstract fun IsFullScreen(): Boolean
        abstract fun GetScreenWidth(): Int
        abstract fun GetScreenHeight(): Int

        // allocate a renderWorld to be used for drawing
        abstract fun AllocRenderWorld(): idRenderWorld
        abstract fun FreeRenderWorld(rw: idRenderWorld)

        // All data that will be used in a level should be
        // registered before rendering any frames to prevent disk hits,
        // but they can still be registered at a later time
        // if necessary.
        abstract fun BeginLevelLoad()
        abstract fun EndLevelLoad()

        // font support
        abstract fun RegisterFont(fontName: String, font: fontInfoEx_t): Boolean

        // GUI drawing just involves shader parameter setting and axial image subsections
        abstract fun SetColor(rgba: idVec4)
        abstract fun SetColor4(r: Float, g: Float, b: Float, a: Float)
        abstract fun DrawStretchPic(
            verts: Array<idDrawVert>,
            indexes: IntArray?,
            vertCount: Int,
            indexCount: Int,
            material: idMaterial?,
            clip: Boolean /*= true*/,
            min_x: Float /* = 0.0f*/,
            min_y: Float /*= 0.0f*/,
            max_x: Float /*= 640.0f*/,
            max_y: Float /*= 480.0f */
        )

        @JvmOverloads
        fun DrawStretchPic(
            verts: Array<idDrawVert>,
            indexes: IntArray?,
            vertCount: Int,
            indexCount: Int,
            material: idMaterial?,
            clip: Boolean = true /*= true*/,
            min_x: Float = 0.0f /* = 0.0f*/,
            min_y: Float = 0.0f /*= 0.0f*/,
            max_x: Float = 640.0f /*= 640.0f*/
        ) {
            DrawStretchPic(verts, indexes, vertCount, indexCount, material, clip, min_x, min_y, max_x, 480.0f)
        }

        abstract fun DrawStretchPic(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            s1: Float,
            t1: Float,
            s2: Float,
            t2: Float,
            material: idMaterial?
        )

        abstract fun DrawStretchTri(
            p1: idVec2,
            p2: idVec2,
            p3: idVec2,
            t1: idVec2,
            t2: idVec2,
            t3: idVec2,
            material: idMaterial?
        )

        abstract fun GlobalToNormalizedDeviceCoordinates(global: idVec3, ndc: idVec3)
        abstract fun GetGLSettings(width: IntArray, height: IntArray)
        abstract fun PrintMemInfo(mi: MemInfo_t)
        abstract fun DrawSmallChar(x: Int, y: Int, ch: Int, material: idMaterial?)
        abstract fun DrawSmallStringExt(
            x: Int,
            y: Int,
            string: CharArray,
            setColor: idVec4,
            forceColor: Boolean,
            material: idMaterial?
        )

        abstract fun DrawBigChar(x: Int, y: Int, ch: Int, material: idMaterial?)
        abstract fun DrawBigStringExt(
            x: Int,
            y: Int,
            string: String,
            setColor: idVec4,
            forceColor: Boolean,
            material: idMaterial?
        )

        // dump all 2D drawing so far this frame to the demo file
        abstract fun WriteDemoPics()

        // draw the 2D pics that were saved out with the current demo frame
        abstract fun DrawDemoPics()

        // FIXME: add an interface for arbitrary point/texcoord drawing
        // a frame cam consist of 2D drawing and potentially multiple 3D scenes
        // window sizes are needed to convert SCREEN_WIDTH / SCREEN_HEIGHT values
        abstract fun BeginFrame(windowWidth: Int, windowHeight: Int)

        // if the pointers are not NULL, timing info will be returned
        abstract fun EndFrame(frontEndMsec: IntArray?, backEndMsec: IntArray?)

        // aviDemo uses this.
        // Will automatically tile render large screen shots if necessary
        // Samples is the number of jittered frames for anti-aliasing
        // If ref == NULL, session->updateScreen will be used
        // This will perform swapbuffers, so it is NOT an approppriate way to
        // generate image files that happen during gameplay, as for savegame
        // markers.  Use WriteRender() instead.
        abstract fun TakeScreenshot(width: Int, height: Int, fileName: String, samples: Int, ref: renderView_s?)

        // the render output can be cropped down to a subset of the real screen, as
        // for save-game reviews and split-screen multiplayer.  Users of the renderer
        // will not know the actual pixel size of the area they are rendering to
        // the x,y,width,height values are in public abstract SCREEN_WIDTH / SCREEN_HEIGHT coordinates
        // to render to a texture, first set the crop size with makePowerOfTwo = true,
        // then perform all desired rendering, then capture to an image
        // if the specified physical dimensions are larger than the current cropped region, they will be cut down to fit
        abstract fun CropRenderSize(
            width: Int,
            height: Int,
            makePowerOfTwo: Boolean /*= false*/,
            forceDimensions: Boolean /*= false */
        )

        @JvmOverloads
        fun CropRenderSize(width: Int, height: Int, makePowerOfTwo: Boolean = false /*= false*/) {
            CropRenderSize(width, height, makePowerOfTwo, false)
        }

        abstract fun CaptureRenderToImage(imageName: String)

        // fixAlpha will set all the alpha channel values to 0xff, which allows screen captures
        // to use the default tga loading code without having dimmed down areas in many places
        abstract fun CaptureRenderToFile(fileName: String, fixAlpha: Boolean /* = false */)
        fun CaptureRenderToFile(fileName: String) {
            CaptureRenderToFile(fileName, false)
        }

        abstract fun UnCrop()

        // the image has to be already loaded ( most straightforward way would be through a FindMaterial )
        // texture filter / mipmapping / repeat won't be modified by the upload
        // returns false if the image wasn't found
        abstract fun UploadImage(imageName: String?, data: ByteBuffer, width: Int, height: Int): Boolean
    }
}