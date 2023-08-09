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
import neo.TempDump.CPP_class
import neo.TempDump.CPP_class.Char
import neo.TempDump.NOT
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.sys.win_glimp.GLimp_EnableLogging
import java.nio.ByteBuffer

/**
 *
 */
object RenderSystem {
    val BIGCHAR_HEIGHT: Int = 16
    val BIGCHAR_WIDTH: Int = 16
    val GLYPH_CHAREND: Int = 127
    val GLYPH_CHARSTART: Int = 32
    val GLYPH_END: Int = 255

    // font support
    val GLYPH_START: Int = 0
    val GLYPHS_PER_FONT: Int = GLYPH_END - GLYPH_START + 1
    val SCREEN_HEIGHT: Int = 480

    //
    // all drawing is done to a 640 x 480 virtual screen size
    // and will be automatically scaled to the real resolution
    val SCREEN_WIDTH: Int = 640
    val SMALLCHAR_HEIGHT: Int = 16
    val SMALLCHAR_WIDTH: Int = 8
    var renderSystem: idRenderSystem = tr_local.tr

    /*
     =====================
     R_PerformanceCounters

     This prints both front and back end counters, so it should
     only be called when the back end thread is idle.
     =====================
     */
    fun R_PerformanceCounters() {
        if (RenderSystem_init.r_showPrimitives!!.GetInteger() != 0) {
            val megaBytes: Float = Image.globalImages.SumOfUsedImages() / (1024 * 1024.0f)
            if (RenderSystem_init.r_showPrimitives!!.GetInteger() > 1) {
                Common.common.Printf(
                    "v:%d ds:%d t:%d/%d v:%d/%d st:%d sv:%d image:%5.1f MB\n",
                    tr_local.tr.pc!!.c_numViews,
                    tr_local.backEnd!!.pc.c_drawElements + tr_local.backEnd!!.pc.c_shadowElements,
                    tr_local.backEnd!!.pc.c_drawIndexes / 3,
                    (tr_local.backEnd!!.pc.c_drawIndexes - tr_local.backEnd!!.pc.c_drawRefIndexes) / 3,
                    tr_local.backEnd!!.pc.c_drawVertexes,
                    (tr_local.backEnd!!.pc.c_drawVertexes - tr_local.backEnd!!.pc.c_drawRefVertexes),
                    tr_local.backEnd!!.pc.c_shadowIndexes / 3,
                    tr_local.backEnd!!.pc.c_shadowVertexes,
                    megaBytes
                )
            } else {
                Common.common.Printf(
                    "views:%d draws:%d tris:%d (shdw:%d) (vbo:%d) image:%5.1f MB\n",
                    tr_local.tr.pc!!.c_numViews,
                    tr_local.backEnd!!.pc.c_drawElements + tr_local.backEnd!!.pc.c_shadowElements,
                    (tr_local.backEnd!!.pc.c_drawIndexes + tr_local.backEnd!!.pc.c_shadowIndexes) / 3,
                    tr_local.backEnd!!.pc.c_shadowIndexes / 3,
                    tr_local.backEnd!!.pc.c_vboIndexes / 3,
                    megaBytes
                )
            }
        }
        if (RenderSystem_init.r_showDynamic!!.GetBool()) {
            Common.common.Printf(
                "callback:%d md5:%d dfrmVerts:%d dfrmTris:%d tangTris:%d guis:%d\n",
                tr_local.tr.pc!!.c_entityDefCallbacks,
                tr_local.tr.pc!!.c_generateMd5,
                tr_local.tr.pc!!.c_deformedVerts,
                tr_local.tr.pc!!.c_deformedIndexes / 3,
                tr_local.tr.pc!!.c_tangentIndexes / 3,
                tr_local.tr.pc!!.c_guiSurfs
            )
        }
        if (RenderSystem_init.r_showCull!!.GetBool()) {
            Common.common.Printf(
                "%d sin %d sclip  %d sout %d bin %d bout\n",
                tr_local.tr.pc!!.c_sphere_cull_in,
                tr_local.tr.pc!!.c_sphere_cull_clip,
                tr_local.tr.pc!!.c_sphere_cull_out,
                tr_local.tr.pc!!.c_box_cull_in,
                tr_local.tr.pc!!.c_box_cull_out
            )
        }
        if (RenderSystem_init.r_showAlloc!!.GetBool()) {
            Common.common.Printf("alloc:%d free:%d\n", tr_local.tr.pc!!.c_alloc, tr_local.tr.pc!!.c_free)
        }
        if (RenderSystem_init.r_showInteractions!!.GetBool()) {
            Common.common.Printf(
                "createInteractions:%d createLightTris:%d createShadowVolumes:%d\n",
                tr_local.tr.pc!!.c_createInteractions,
                tr_local.tr.pc!!.c_createLightTris,
                tr_local.tr.pc!!.c_createShadowVolumes
            )
        }
        if (RenderSystem_init.r_showDefs!!.GetBool()) {
            Common.common.Printf(
                "viewEntities:%d  shadowEntities:%d  viewLights:%d\n", tr_local.tr.pc!!.c_visibleViewEntities,
                tr_local.tr.pc!!.c_shadowViewEntities, tr_local.tr.pc!!.c_viewLights
            )
        }
        if (RenderSystem_init.r_showUpdates!!.GetBool()) {
            Common.common.Printf(
                "entityUpdates:%d  entityRefs:%d  lightUpdates:%d  lightRefs:%d\n",
                tr_local.tr.pc!!.c_entityUpdates, tr_local.tr.pc!!.c_entityReferences,
                tr_local.tr.pc!!.c_lightUpdates, tr_local.tr.pc!!.c_lightReferences
            )
        }
        if (RenderSystem_init.r_showMemory!!.GetBool()) {
            val m1: Int = if (tr_local.frameData != null) tr_local.frameData!!.memoryHighwater else 0
            Common.common.Printf("frameData: %d (%d)\n", tr_main.R_CountFrameData(), m1)
        }
        if (RenderSystem_init.r_showLightScale!!.GetBool()) {
            Common.common.Printf("lightScale: %f\n", tr_local.backEnd!!.pc.maxLightValue)
        }

//        memset(tr.pc, 0, sizeof(tr.pc));
        tr_local.tr.pc = performanceCounters_t()
        //        memset(backEnd.pc, 0, sizeof(backEnd.pc));
        tr_local.backEnd!!.pc = backEndCounters_t()
    }

    /*
     ====================
     R_IssueRenderCommands

     Called by R_EndFrame each frame
     ====================
     */
    fun R_IssueRenderCommands() {
        if (renderCommand_t.RC_NOP == tr_local.frameData!!.cmdHead!!.commandId && NOT(tr_local.frameData!!.cmdHead!!.next)) {
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
        if (!RenderSystem_init.r_skipBackEnd!!.GetBool()) {
            tr_backend.RB_ExecuteBackEndCommands(tr_local.frameData!!.cmdHead)
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
        tr_local.frameData!!.cmdTail!!.next = cmd
        tr_local.frameData!!.cmdTail = cmd
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
        tr_local.frameData!!.cmdTail = emptyCommand_t()
        tr_local.frameData!!.cmdHead = tr_local.frameData!!.cmdTail // R_FrameAlloc(sizeof(frameData.cmdHead));
        tr_local.frameData!!.cmdHead!!.commandId = renderCommand_t.RC_NOP
        tr_local.frameData!!.cmdHead!!.next = null
    }

    /*
     =================
     R_ViewStatistics
     =================
     */
    fun R_ViewStatistics(parms: viewDef_s) {
        // report statistics about this view
        if (!RenderSystem_init.r_showSurfaces!!.GetBool()) {
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
        R_GetCommandBuffer(drawSurfsCommand_t().also({ cmd = it /*sizeof(cmd)*/ }))
        cmd.commandId = renderCommand_t.RC_DRAW_VIEW
        cmd.viewDef = parms
        if (parms.viewEntitys != null) {
            // save the command for r_lockSurfaces debugging
            tr_local.tr.lockSurfacesCmd = cmd
        }
        tr_local.tr.pc!!.c_numViews++
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
        var cmd: drawSurfsCommand_t?
        var vModel: viewEntity_s?

        // set the matrix for world space to eye space
        tr_main.R_SetViewMatrix(parms)
        tr_local.tr.lockSurfacesCmd!!.viewDef!!.worldSpace = parms.worldSpace

        // update the view origin and axis, and all
        // the entity matricies
        vModel = tr_local.tr.lockSurfacesCmd!!.viewDef!!.viewEntitys
        while (vModel != null) {
            tr_main.myGlMultMatrix(
                vModel.modelMatrix,
                tr_local.tr.lockSurfacesCmd!!.viewDef!!.worldSpace.modelViewMatrix,
                vModel.modelViewMatrix
            )
            vModel = vModel.next
        }

        // add the stored off surface commands again
//        cmd = (drawSurfsCommand_t) R_GetCommandBuffer(sizeof(cmd));
        R_GetCommandBuffer(tr_local.tr.lockSurfacesCmd!!) //TODO:double check to make sure the casting and casting back preserves our values.
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
        if (RenderSystem_init.r_gamma!!.IsModified() || RenderSystem_init.r_brightness!!.IsModified()) {
            RenderSystem_init.r_gamma!!.ClearModified()
            RenderSystem_init.r_brightness!!.ClearModified()
            RenderSystem_init.R_SetColorMappings()
        }

        // check for changes to logging state
        GLimp_EnableLogging(RenderSystem_init.r_logFile!!.GetInteger() != 0)
    }

    fun setRenderSystems(renderSystem: idRenderSystem?) {
        tr_local.tr = renderSystem as idRenderSystemLocal
        RenderSystem.renderSystem = tr_local.tr
    }

    /*
     ===============================================================================

     idRenderSystem is responsible for managing the screen, which can have
     multiple idRenderWorld and 2D drawing done on it.

     ===============================================================================
     */
    class glconfig_s() {
        var ARBFragmentProgramAvailable: Boolean = false
        var ARBVertexBufferObjectAvailable: Boolean = false
        var ARBVertexProgramAvailable: Boolean = false
        var allowARB2Path: Boolean = false
        var allowNV10Path: Boolean = false
        var allowNV20Path: Boolean = false

        //
        var allowNV30Path: Boolean = false
        var allowR200Path: Boolean = false
        var anisotropicAvailable: Boolean = false

        //
        // ati r200 extensions
        var atiFragmentShaderAvailable: Boolean = false

        //
        // ati r300
        var atiTwoSidedStencilAvailable: Boolean = false

        //
        var colorBits: Int = 0
        var depthBits: Int = 0
        var stencilBits: Int = 8
        var cubeMapAvailable: Boolean = false
        var depthBoundsTestAvailable: Boolean = false

        //
        var displayFrequency: Int = 0
        var envDot3Available: Boolean = false
        var extensions_string: String? = null

        //
        var glVersion: Float = 0f // atof( version_string )

        //
        var isFullscreen: Boolean = false

        //
        var isInitialized: Boolean = false
        var maxTextureAnisotropy: Float = 0f
        var maxTextureCoords: Int = 0
        var maxTextureImageUnits: Int = 0

        //
        //
        var maxTextureSize: Int = 0 // queried from GL
        var maxTextureUnits: Int = 0

        //
        var multitextureAvailable: Boolean = false
        var registerCombinersAvailable: Boolean = false
        var renderer_string: String? = null
        var sharedTexturePaletteAvailable: Boolean = false
        var texture3DAvailable: Boolean = false
        var textureCompressionAvailable: Boolean = false
        var textureEnvAddAvailable: Boolean = false
        var textureEnvCombineAvailable: Boolean = false
        var textureLODBiasAvailable: Boolean = false
        var textureNonPowerOfTwoAvailable: Boolean = false
        var twoSidedStencilAvailable: Boolean = false
        var vendor_string: String? = null
        var version_string: String? = null

        //
        var vidWidth: Int = 0
        var vidHeight: Int = 0 // passed to R_BeginFrame
        var wgl_extensions_string: String? = null
    }

    class glyphInfo_t() {
        var glyph: idMaterial? = null // shader with the glyph
        var height: Int = 0 // number of scan lines
        var imageHeight: Int = 0 // height of actual image
        var imageWidth: Int = 0 // width of actual image
        var s: Float = 0f // x offset in image where glyph starts
        var s2: Float = 0f
        var t: Float = 0f // y offset in image where glyph starts
        var t2: Float = 0f
        var top: Int = 0 // top of glyph in buffer
        var xSkip: Int = 0 // x adjustment
        var bottom: Int = 0 // bottom of glyph in buffer
        var pitch: Int = 0 // width for copying

        // char				shaderName[32];
        var shaderName: String? = null

        companion object {
            @Transient
            val SIZE: Int = (Integer.SIZE
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
                    + CPP_class.Pointer.SIZE //const idMaterial *	glyph
                    + (Char.SIZE * 32))
        }
    }

    class fontInfo_t() {
        var glyphScale: Float = 0f
        var glyphs: Array<glyphInfo_t?> = arrayOfNulls(GLYPHS_PER_FONT)
        var name: StringBuilder = StringBuilder(64)

        init {
            for (g in glyphs.indices) {
                glyphs[g] = glyphInfo_t()
            }
        }

        companion object {
            @Transient
            val SIZE: Int = ((glyphInfo_t.SIZE * GLYPHS_PER_FONT)
                    + java.lang.Float.SIZE
                    + (Char.SIZE * 64))

            @Transient
            val BYTES: Int = SIZE / java.lang.Byte.SIZE
        }
    }

    class fontInfoEx_t() {
        var fontInfoLarge: fontInfo_t = fontInfo_t()
        var fontInfoMedium: fontInfo_t = fontInfo_t()
        var fontInfoSmall: fontInfo_t = fontInfo_t()
        var maxHeight: Int = 0
        var maxHeightLarge: Int = 0
        var maxHeightMedium: Int = 0
        var maxHeightSmall: Int = 0
        var maxWidth: Int = 0
        var maxWidthLarge: Int = 0
        var maxWidthMedium: Int = 0
        var maxWidthSmall: Int = 0

        // char				name[64];
        var name: String? = null

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
            name = null
        }
    }

    abstract class idRenderSystem() {
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
        abstract fun RegisterFont(fontName: String?, font: fontInfoEx_t): Boolean

        // GUI drawing just involves shader parameter setting and axial image subsections
        abstract fun SetColor(rgba: idVec4)
        abstract fun SetColor4(r: Float, g: Float, b: Float, a: Float)
        abstract fun DrawStretchPic(
            verts: Array<idDrawVert>?,
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
            verts: Array<idDrawVert>?,
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
            p1: idVec2?,
            p2: idVec2?,
            p3: idVec2?,
            t1: idVec2?,
            t2: idVec2?,
            t3: idVec2?,
            material: idMaterial?
        )

        abstract fun GlobalToNormalizedDeviceCoordinates(global: idVec3?, ndc: idVec3?)
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

        abstract fun CaptureRenderToImage(imageName: String?)

        // fixAlpha will set all the alpha channel values to 0xff, which allows screen captures
        // to use the default tga loading code without having dimmed down areas in many places
        abstract fun CaptureRenderToFile(fileName: String?, fixAlpha: Boolean /* = false */)
        fun CaptureRenderToFile(fileName: String?) {
            CaptureRenderToFile(fileName, false)
        }

        abstract fun UnCrop()
        abstract fun GetCardCaps(oldCard: BooleanArray, nv10or20: BooleanArray)

        // the image has to be already loaded ( most straightforward way would be through a FindMaterial )
        // texture filter / mipmapping / repeat won't be modified by the upload
        // returns false if the image wasn't found
        abstract fun UploadImage(imageName: String?, data: ByteBuffer?, width: Int, height: Int): Boolean
    }
}
