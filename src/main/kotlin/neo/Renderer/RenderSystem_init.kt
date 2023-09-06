package neo.Renderer

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.idImage
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Image_files.R_WriteTGA
import neo.Renderer.Image_program.R_LoadImageProgram
import neo.Renderer.Interaction.R_ShowInteractionMemory_f
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.MegaTexture.idMegaTexture.MakeMegaTexture_f
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.draw_arb2.R_ReloadARBPrograms_f
import neo.Renderer.tr_guisurf.R_ListGuis_f
import neo.Renderer.tr_guisurf.R_ReloadGuis_f
import neo.Renderer.tr_local.backEndName_t
import neo.Renderer.tr_local.tr
import neo.Renderer.tr_local.viewDef_s
import neo.Sound.snd_system
import neo.TempDump.NOT
import neo.TempDump.atof
import neo.TempDump.btoi
import neo.TempDump.ctos
import neo.TempDump.itob
import neo.framework.BuildDefines._WIN32
import neo.framework.CVarSystem.CVAR_ARCHIVE
import neo.framework.CVarSystem.CVAR_BOOL
import neo.framework.CVarSystem.CVAR_CHEAT
import neo.framework.CVarSystem.CVAR_FLOAT
import neo.framework.CVarSystem.CVAR_INTEGER
import neo.framework.CVarSystem.CVAR_NOCHEAT
import neo.framework.CVarSystem.CVAR_RENDERER
import neo.framework.CVarSystem.cvarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.CMD_FL_CHEAT
import neo.framework.CmdSystem.CMD_FL_RENDERER
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.cmdSystem
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.Common.Companion.common
import neo.framework.Console
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.FileSystem_h.fileSystem
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindText
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.idStr.Companion.IsNumeric
import neo.idlib.Text.Str.idStr.Companion.snPrintf
import neo.idlib.containers.CInt
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector.idVec3
import neo.sys.win_glimp.GLimp_Init
import neo.sys.win_glimp.GLimp_SetScreenParms
import neo.sys.win_glimp.GLimp_Shutdown
import neo.sys.win_glimp.glimpParms_t
import neo.sys.win_input.Sys_GrabMouseCursor
import neo.sys.win_input.Sys_InitInput
import neo.sys.win_input.Sys_ShutdownInput
import neo.sys.win_main.Sys_GetProcessorString
import neo.sys.win_shared.Sys_Milliseconds
import neo.ui.UserInterface.uiManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.pow

/**
 *
 */
object RenderSystem_init {
    var r_brightness: idCVar? = null // changes gamma tables
    var r_cgFragmentProfile: idCVar? = null // arbfp1, fp30

    //
    var r_cgVertexProfile: idCVar? = null // arbvp1, vp20, vp30

    //
    var r_checkBounds: idCVar? = null // compare all surface bounds with precalculated ones
    var r_clear: idCVar? = null // force screen clear every frame
    var r_customHeight: idCVar? = null
    var r_customWidth: idCVar? = null
    var r_debugArrowStep: idCVar? = null // step size of arrow cone line rotation in degrees

    //
    var r_debugLineDepthTest: idCVar? = null // perform depth test on debug lines
    var r_debugLineWidth: idCVar? = null // width of debug lines
    var r_debugPolygonFilled: idCVar? = null

    //
    var r_debugRenderToTexture: idCVar? = null
    var r_demonstrateBug: idCVar? = null // used during development to show IHV's their problems
    var r_displayRefresh: idCVar? = null // optional display refresh rate option for vid mode

    //
    //
    //
    // cvars
    //
    var r_ext_vertex_array_range: idCVar? = null

    //
    var r_finish: idCVar? = null // force a call to glFinish() every frame
    var r_flareSize: idCVar? = null // scale the flare deforms from the material def

    //
    var r_forceLoadImages: idCVar? = null // draw all images to screen after registration
    var r_frontBuffer: idCVar? = null // draw to front buffer for debugging
    var r_fullscreen: idCVar? = null // 0 = windowed, 1 = full screen

    //
    var r_gamma: idCVar? = null // changes gamma tables
    var r_glDriver: idCVar? = null // "opengl32", etc

    //
    var r_ignore: idCVar? = null // used for random debugging without defining new vars
    var r_ignore2: idCVar? = null // used for random debugging without defining new vars

    //
    var r_ignoreGLErrors: idCVar? = null

    //
    var r_inhibitFragmentProgram: idCVar? = null

    //
    var r_jitter: idCVar? = null // randomly subpixel jitter the projection matrix
    var r_jointNameOffset: idCVar? = null // offset of joint names when r_showskel is set to 1
    var r_jointNameScale: idCVar? = null // size of joint names when r_showskel is set to 1
    var r_lightAllBackFaces: idCVar? = null // light all the back faces, even when they would be shadowed
    var r_lightScale: idCVar? = null // all light intensities are multiplied by this, which is normally 2
    var r_lightSourceRadius: idCVar? = null // for soft-shadow sampling
    var r_lockSurfaces: idCVar? = null
    var r_logFile: idCVar? = null // number of frames to emit GL logs

    //
    var r_materialOverride: idCVar? = null // override all materials
    var r_mode: idCVar? = null // video mode number
    var r_multiSamples: idCVar? = null // number of antialiasing samples
    var r_offsetFactor: idCVar? = null // polygon offset parameter
    var r_offsetUnits: idCVar? = null // polygon offset parameter
    var r_orderIndexes: idCVar? = null // perform index reorganization to optimize vertex use

    //
    var r_renderer: idCVar? = null // arb, nv10, nv20, r200, gl2, etc
    var r_screenFraction: idCVar? = null // for testing fill rate, the resolution of the entire screen can be changed
    var r_shadowPolygonFactor: idCVar? = null // scale value for stencil shadow drawing
    var r_shadowPolygonOffset: idCVar? = null // bias value added to depth test for stencil shadow drawing
    var r_shadows: idCVar? = null // enable shadows
    var r_showAlloc: idCVar? = null // report alloc/free counts
    var r_showCull: idCVar? = null // report sphere and box culling stats
    var r_showDefs: idCVar? = null // report the number of modeDefs and lightDefs in view
    var r_showDemo: idCVar? = null // report reads and writes to the demo file
    var r_showDepth: idCVar? = null // display the contents of the depth buffer and the depth range
    var r_showDominantTri: idCVar? = null // draw lines from vertexes to center of dominant triangles
    var r_showDynamic: idCVar? = null // report stats on dynamic surface generation
    var r_showEdges: idCVar? = null // draw the sil edges
    var r_showEntityScissors: idCVar? = null // show entity scissor rectangles
    var r_showImages: idCVar? = null // draw all images to screen instead of rendering
    var r_showIntensity: idCVar? = null // draw the screen colors based on intensity, red = 0, green = 128, blue = 255
    var r_showInteractionFrustums: idCVar? = null // show a frustum for each interaction

    //
    var r_showInteractionScissors: idCVar? = null // show screen rectangle which contains the interaction frustum
    var r_showInteractions: idCVar? = null // report interaction generation activity
    var r_showLightCount: idCVar? = null // colors surfaces based on light count
    var r_showLightScale: idCVar? = null // report the scale factor applied to drawing for overbrights
    var r_showLightScissors: idCVar? = null // show light scissor rectangles
    var r_showLights: idCVar? = null // 1 = print light info, 2 = also draw volumes
    var r_showMemory: idCVar? = null // print frame memory utilization
    var r_showNormals: idCVar? = null // draws wireframe normals
    var r_showOverDraw: idCVar? = null // show overdraw
    var r_showPortals: idCVar? = null // draw portal outlines in color based on passed / not passed
    var r_showPrimitives: idCVar? = null // report vertex/index/draw counts
    var r_showShadowCount: idCVar? = null // colors screen based on shadow volume depth complexity
    var r_showShadows: idCVar? = null // visualize the stencil shadow volumes
    var r_showSilhouette: idCVar? = null // highlight edges that are casting shadow planes
    var r_showSkel: idCVar? = null // draw the skeleton when model animates
    var r_showSmp: idCVar? = null // show which end (front or back) is blocking
    var r_showSurfaceInfo: idCVar? = null // show surface material name under crosshair
    var r_showSurfaces: idCVar? = null // report surface/light/shadow counts
    var r_showTangentSpace: idCVar? = null // shade triangles by tangent space
    var r_showTexturePolarity: idCVar? = null // shade triangles by texture area polarity
    var r_showTextureVectors: idCVar? = null // draw each triangles texture (tangent) vectors
    var r_showTrace: idCVar? = null // show the intersection of an eye trace with the world
    var r_showTris: idCVar? = null // enables wireframe rendering of the world

    //
    var r_showUnsmoothedTangents: idCVar? = null // highlight geometry rendered with unsmoothed tangents
    var r_showUpdates: idCVar? = null // report entity and light updates and ref counts
    var r_showVertexColor: idCVar? = null // draws all triangles with the solid vertex color
    var r_showViewEntitys: idCVar? = null // displays the bounding boxes of all view models and optionally the index
    var r_singleArea: idCVar? = null // only draw the portal area the view is actually in
    var r_singleEntity: idCVar? = null // suppress all but one entity

    //
    var r_singleLight: idCVar? = null // suppress all but one light
    var r_singleSurface: idCVar? = null // suppress all but one surface on each entity
    var r_singleTriangle: idCVar? = null // only draw a single triangle per primitive
    var r_skipAmbient: idCVar? = null // bypasses all non-interaction drawing
    var r_skipBackEnd: idCVar? = null // don't draw anything
    var r_skipBlendLights: idCVar? = null // skip all blend lights
    var r_skipBump: idCVar? = null // uses a flat surface instead of the bump map
    var r_skipCopyTexture: idCVar? = null // do all rendering, but don't actually copyTexSubImage2D
    var r_skipDeforms: idCVar? = null // leave all deform materials in their original state
    var r_skipDiffuse: idCVar? = null // use black for diffuse
    var r_skipDynamicTextures: idCVar? = null // don't dynamically create textures
    var r_skipFogLights: idCVar? = null // skip all fog lights
    var r_skipFrontEnd: idCVar? = null // bypasses all front end work, but 2D gui rendering still draws
    var r_skipGuiShaders: idCVar? = null // 1 = don't render any gui elements on surfaces
    var r_skipInteractions: idCVar? = null // skip all light/surface interaction drawing
    var r_skipLightScale: idCVar? =
        null // don't do any post-interaction light scaling, makes things dim on low-dynamic range cards
    var r_skipNewAmbient: idCVar? = null // bypasses all vertex/fragment program ambients
    var r_skipOverlays: idCVar? = null // skip overlay surfaces
    var r_skipParticles: idCVar? = null // 1 = don't render any particles

    //
    var r_skipPostProcess: idCVar? = null // skip all post-process renderings
    var r_skipROQ: idCVar? = null
    var r_skipRender: idCVar? = null // skip 3D rendering, but pass 2D
    var r_skipRenderContext: idCVar? = null // NULL the rendering context during backend 3D rendering
    var r_skipSpecular: idCVar? = null // use black for specular
    var r_skipSubviews: idCVar? = null // 1 = don't render any mirrors / cameras / etc
    var r_skipSuppress: idCVar? = null // ignore the per-view suppressions
    var r_skipTranslucent: idCVar? = null // skip the translucent interaction rendering
    var r_skipUpdates: idCVar? = null // 1 = don't accept any entity or light updates, making everything static
    var r_subviewOnly: idCVar? = null // 1 = don't render main view, allowing subviews to be debugged
    var r_swapInterval: idCVar? = null // changes wglSwapIntarval

    //
    var r_testARBProgram: idCVar? = null // experiment with vertex/fragment programs

    //
    var r_testGamma: idCVar? = null // draw a grid pattern to test gamma levels
    var r_testGammaBias: idCVar? = null // draw a grid pattern to test gamma levels
    var r_testStepGamma: idCVar? = null // draw a grid pattern to test gamma levels
    var r_useCachedDynamicModels: idCVar? = null // 1 = cache snapshots of dynamic models
    var r_useClippedLightScissors: idCVar? =
        null // 0 = full screen when near clipped, 1 = exact when near clipped, 2 = exact always
    var r_useCombinerDisplayLists: idCVar? = null // if 1, put all nvidia register combiner programming in display lists
    var r_useConstantMaterials: idCVar? = null // 1 = use pre-calculated material registers if possible
    var r_useCulling: idCVar? = null // 0 = none, 1 = sphere, 2 = sphere + box
    var r_useDeferredTangents: idCVar? = null // 1 = don't always calc tangents after deform
    var r_useDepthBoundsTest: idCVar? = null // use depth bounds test to reduce shadow fill
    var r_useEntityCallbacks: idCVar? =
        null // if 0, issue the callback immediately at update time, rather than defering
    var r_useEntityCulling: idCVar? = null // 0 = none, 1 = box
    var r_useEntityScissors: idCVar? = null // 1 = use custom scissor rectangle for each entity
    var r_useExternalShadows: idCVar? = null // 1 = skip drawing caps when outside the light volume
    var r_useFrustumFarDistance: idCVar? = null // if != 0 force the view frustum far distance to this distance
    var r_useIndexBuffers: idCVar? = null // if 0, don't use ARB_vertex_buffer_object for indexes
    var r_useInfiniteFarZ: idCVar? = null // 1 = use the no-far-clip-plane trick
    var r_useInteractionCulling: idCVar? = null // 1 = cull interactions
    var r_useInteractionScissors: idCVar? = null // 1 = use a custom scissor rectangle for each interaction
    var r_useInteractionTable: idCVar? =
        null // create a full entityDefs * lightDefs table to make finding interactions faster
    var r_useLightCulling: idCVar? = null // 0 = none, 1 = box, 2 = exact clip of polyhedron faces
    var r_useLightPortalFlow: idCVar? = null // 1 = do a more precise area reference determination
    var r_useLightScissors: idCVar? = null // 1 = use custom scissor rectangle for each light

    //
    var r_useNV20MonoLights: idCVar? = null // 1 = allow an interaction pass optimization
    var r_useNodeCommonChildren: idCVar? = null // stop pushing reference bounds early when possible
    var r_useOptimizedShadows: idCVar? = null // 1 = use the dmap generated static shadow volumes
    var r_usePortals: idCVar? = null // 1 = use portals to perform area culling, otherwise draw everything
    var r_usePreciseTriangleInteractions: idCVar? =
        null // 1 = do winding clipping to determine if each ambiguous tri should be lit
    var r_useScissor: idCVar? = null // 1 = scissor clip as portals and lights are processed
    var r_useShadowCulling: idCVar? = null // try to cull shadows from partially visible lights
    var r_useShadowProjectedCull: idCVar? = null // 1 = discard triangles outside light volume before shadowing
    var r_useShadowSurfaceScissor: idCVar? = null // 1 = scissor shadows by the scissor rect of the interaction surfaces
    var r_useShadowVertexProgram: idCVar? = null // 1 = do the shadow projection in the vertex program on capable cards
    var r_useSilRemap: idCVar? = null // 1 = consider verts with the same XYZ, but different ST the same for shadows
    var r_useStateCaching: idCVar? = null // avoid redundant state changes in GL_*() calls
    var r_useTripleTextureARB: idCVar? = null // 1 = cards with 3+ texture units do a two pass instead of three pass
    var r_useTurboShadow: idCVar? = null // 1 = use the infinite projection with W technique for dynamic shadows
    var r_useTwoSidedStencil: idCVar? = null // 1 = do stencil shadows in one pass with different ops on each side
    var r_useVertexBuffers: idCVar? = null // if 0, don't use ARB_vertex_buffer_object for vertexes
    var r_znear: idCVar? = null // near Z clip plane

    /*
     ==================
     R_BlendedScreenShot

     screenshot
     screenshot [filename]
     screenshot [width] [height]
     screenshot [width] [height] [samples]
     ==================
     */
    val MAX_BLENDS: Int = 256 // to keep the accumulation in shorts

    //============================================================================
    val cubeAxis: Array<idMat3?> = arrayOfNulls(6)
    val r_rendererArgs: Array<String?> = arrayOf("best", "arb", "arb2", "Cg", "exp", "nv10", "nv20", "r200", null)
    val r_vidModes: Array<vidmode_s> = arrayOf(
        vidmode_s("Mode  0: 320x240", 320, 240),
        vidmode_s("Mode  1: 400x300", 400, 300),
        vidmode_s("Mode  2: 512x384", 512, 384),
        vidmode_s("Mode  3: 640x480", 640, 480),
        vidmode_s("Mode  4: 800x600", 800, 600),
        vidmode_s("Mode  5: 1024x768", 1024, 768),
        vidmode_s("Mode  6: 1152x864", 1152, 864),
        vidmode_s("Mode  7: 1280x1024", 1280, 1024),
        vidmode_s("Mode  8: 1600x1200", 1600, 1200)
    )

    /*
     ==============================================================================

     THROUGHPUT BENCHMARKING

     ==============================================================================
     */
    private val SAMPLE_MSEC: Int = 1000

    /*
     ==================
     R_BlendedScreenShot

     screenshot
     screenshot [filename]
     screenshot [width] [height]
     screenshot [width] [height] [samples]
     ==================
     */
    private val lastNumber: CInt = CInt()
    var s_numVidModes: Int = r_vidModes.size

    /*
     ==================
     R_InitOpenGL

     This function is responsible for initializing a valid OpenGL subsystem
     for rendering.  This is done by calling the system specific GLimp_Init,
     which gives us a working OGL subsystem, then setting all necessary openGL
     state, including images, vertex programs, and display lists.

     Changes to the vertex cache size or smp state require a vid_restart.

     If glConfig.isInitialized is false, no rendering can take place, but
     all renderSystem functions will still operate properly, notably the material
     and model information functions.
     ==================
     */
    private var glCheck: Boolean = false

    init {
        r_ext_vertex_array_range = null
        r_inhibitFragmentProgram =
            idCVar("r_inhibitFragmentProgram", "0", CVAR_RENDERER or CVAR_BOOL, "ignore the fragment program extension")
        r_glDriver = idCVar("r_glDriver", "", CVAR_RENDERER, "\"opengl32\", etc.")
        r_useLightPortalFlow = idCVar(
            "r_useLightPortalFlow",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "use a more precise area reference determination"
        )
        r_multiSamples = idCVar(
            "r_multiSamples",
            "0",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
            "number of antialiasing samples"
        )
        r_mode =
            idCVar("r_mode", "3", CVAR_ARCHIVE or CVAR_RENDERER or CVAR_INTEGER, "video mode number")
        r_displayRefresh = idCVar(
            "r_displayRefresh",
            "0",
            CVAR_RENDERER or CVAR_INTEGER or CVAR_NOCHEAT,
            "optional display refresh rate option for vid mode",
            0.0f,
            200.0f
        )
        r_fullscreen =
            idCVar("r_fullscreen", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "0 = windowed, 1 = full screen")
        r_customWidth = idCVar(
            "r_customWidth",
            "720",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
            "custom screen width. set r_mode to -1 to activate"
        )
        r_customHeight = idCVar(
            "r_customHeight",
            "486",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
            "custom screen height. set r_mode to -1 to activate"
        )
        r_singleTriangle =
            idCVar("r_singleTriangle", "0", CVAR_RENDERER or CVAR_BOOL, "only draw a single triangle per primitive")
        r_checkBounds = idCVar(
            "r_checkBounds",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "compare all surface bounds with precalculated ones"
        )
        r_useNV20MonoLights =
            idCVar("r_useNV20MonoLights", "1", CVAR_RENDERER or CVAR_INTEGER, "use pass optimization for mono lights")
        r_useConstantMaterials = idCVar(
            "r_useConstantMaterials",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "use pre-calculated material registers if possible"
        )
        r_useTripleTextureARB = idCVar(
            "r_useTripleTextureARB",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "cards with 3+ texture units do a two pass instead of three pass"
        )
        r_useSilRemap = idCVar(
            "r_useSilRemap",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "consider verts with the same XYZ, but different ST the same for shadows"
        )
        r_useNodeCommonChildren = idCVar(
            "r_useNodeCommonChildren",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "stop pushing reference bounds early when possible"
        )
        r_useShadowProjectedCull = idCVar(
            "r_useShadowProjectedCull",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "discard triangles outside light volume before shadowing"
        )
        r_useShadowVertexProgram = idCVar(
            "r_useShadowVertexProgram",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "do the shadow projection in the vertex program on capable cards"
        )
        r_useShadowSurfaceScissor = idCVar(
            "r_useShadowSurfaceScissor",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "scissor shadows by the scissor rect of the interaction surfaces"
        )
        r_useInteractionTable = idCVar(
            "r_useInteractionTable",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "create a full entityDefs * lightDefs table to make finding interactions faster"
        )
        r_useTurboShadow = idCVar(
            "r_useTurboShadow",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "use the infinite projection with W technique for dynamic shadows"
        )
        r_useTwoSidedStencil = idCVar(
            "r_useTwoSidedStencil",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "do stencil shadows in one pass with different ops on each side"
        )
        r_useDeferredTangents =
            idCVar("r_useDeferredTangents", "1", CVAR_RENDERER or CVAR_BOOL, "defer tangents calculations after deform")
        r_useCachedDynamicModels =
            idCVar("r_useCachedDynamicModels", "1", CVAR_RENDERER or CVAR_BOOL, "cache snapshots of dynamic models")
        r_useVertexBuffers = idCVar(
            "r_useVertexBuffers",
            "1",
            CVAR_RENDERER or CVAR_INTEGER,
            "use ARB_vertex_buffer_object for vertexes",
            0f,
            1f,
            ArgCompletion_Integer(0, 1)
        )
        r_useIndexBuffers = idCVar(
            "r_useIndexBuffers",
            "0",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
            "use ARB_vertex_buffer_object for indexes",
            0f,
            1f,
            ArgCompletion_Integer(0, 1)
        )
        r_useStateCaching = idCVar(
            "r_useStateCaching",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "avoid redundant state changes in GL_*=new idCVar() calls"
        )
        r_useInfiniteFarZ =
            idCVar("r_useInfiniteFarZ", "1", CVAR_RENDERER or CVAR_BOOL, "use the no-far-clip-plane trick")
        r_znear =
            idCVar("r_znear", "3", CVAR_RENDERER or CVAR_FLOAT, "near Z clip plane distance", 0.001f, 200.0f)
        r_ignoreGLErrors =
            idCVar("r_ignoreGLErrors", "1", CVAR_RENDERER or CVAR_BOOL, "ignore GL errors")
        r_finish =
            idCVar("r_finish", "0", CVAR_RENDERER or CVAR_BOOL, "force a call to glFinish=new idCVar() every frame")
        r_swapInterval =
            idCVar("r_swapInterval", "0", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER, "changes wglSwapIntarval")
        r_gamma =
            idCVar("r_gamma", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_FLOAT, "changes gamma tables", 0.5f, 3.0f)
        r_brightness =
            idCVar("r_brightness", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_FLOAT, "changes gamma tables", 0.5f, 2.0f)
        r_renderer = idCVar(
            "r_renderer",
            "best",
            CVAR_RENDERER or CVAR_ARCHIVE,
            "hardware specific renderer path to use",
            r_rendererArgs,
            ArgCompletion_String(r_rendererArgs)
        )
        r_jitter =
            idCVar("r_jitter", "0", CVAR_RENDERER or CVAR_BOOL, "randomly subpixel jitter the projection matrix")
        r_skipSuppress =
            idCVar("r_skipSuppress", "0", CVAR_RENDERER or CVAR_BOOL, "ignore the per-view suppressions")
        r_skipPostProcess =
            idCVar("r_skipPostProcess", "0", CVAR_RENDERER or CVAR_BOOL, "skip all post-process renderings")
        r_skipLightScale = idCVar(
            "r_skipLightScale",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "don't do any post-interaction light scaling, makes things dim on low-dynamic range cards"
        )
        r_skipInteractions =
            idCVar("r_skipInteractions", "0", CVAR_RENDERER or CVAR_BOOL, "skip all light/surface interaction drawing")
        r_skipDynamicTextures =
            idCVar("r_skipDynamicTextures", "0", CVAR_RENDERER or CVAR_BOOL, "don't dynamically create textures")
        r_skipCopyTexture = idCVar(
            "r_skipCopyTexture",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "do all rendering, but don't actually copyTexSubImage2D"
        )
        r_skipBackEnd =
            idCVar("r_skipBackEnd", "0", CVAR_RENDERER or CVAR_BOOL, "don't draw anything")
        r_skipRender =
            idCVar("r_skipRender", "0", CVAR_RENDERER or CVAR_BOOL, "skip 3D rendering, but pass 2D")
        r_skipRenderContext = idCVar(
            "r_skipRenderContext",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "NULL the rendering context during backend 3D rendering"
        )
        r_skipTranslucent =
            idCVar("r_skipTranslucent", "0", CVAR_RENDERER or CVAR_BOOL, "skip the translucent interaction rendering")
        r_skipAmbient =
            idCVar("r_skipAmbient", "0", CVAR_RENDERER or CVAR_BOOL, "bypasses all non-interaction drawing")
        r_skipNewAmbient = idCVar(
            "r_skipNewAmbient",
            "0",
            CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE,
            "bypasses all vertex/fragment program ambient drawing"
        )
        r_skipBlendLights =
            idCVar("r_skipBlendLights", "0", CVAR_RENDERER or CVAR_BOOL, "skip all blend lights")
        r_skipFogLights =
            idCVar("r_skipFogLights", "0", CVAR_RENDERER or CVAR_BOOL, "skip all fog lights")
        r_skipDeforms = idCVar(
            "r_skipDeforms",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "leave all deform materials in their original state"
        )
        r_skipFrontEnd = idCVar(
            "r_skipFrontEnd",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "bypasses all front end work, but 2D gui rendering still draws"
        )
        r_skipUpdates = idCVar(
            "r_skipUpdates",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "1 = don't accept any entity or light updates, making everything static"
        )
        r_skipOverlays =
            idCVar("r_skipOverlays", "0", CVAR_RENDERER or CVAR_BOOL, "skip overlay surfaces")
        r_skipSpecular = idCVar(
            "r_skipSpecular",
            "0",
            CVAR_RENDERER or CVAR_BOOL or CVAR_CHEAT or CVAR_ARCHIVE,
            "use black for specular1"
        )
        r_skipBump = idCVar(
            "r_skipBump",
            "0",
            CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE,
            "uses a flat surface instead of the bump map"
        )
        r_skipDiffuse =
            idCVar("r_skipDiffuse", "0", CVAR_RENDERER or CVAR_BOOL, "use black for diffuse")
        r_skipROQ = idCVar("r_skipROQ", "0", CVAR_RENDERER or CVAR_BOOL, "skip ROQ decoding")
        r_ignore =
            idCVar("r_ignore", "0", CVAR_RENDERER, "used for random debugging without defining new vars")
        r_ignore2 =
            idCVar("r_ignore2", "0", CVAR_RENDERER, "used for random debugging without defining new vars")
        r_usePreciseTriangleInteractions = idCVar(
            "r_usePreciseTriangleInteractions",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "1 = do winding clipping to determine if each ambiguous tri should be lit"
        )
        r_useCulling = idCVar(
            "r_useCulling",
            "2",
            CVAR_RENDERER or CVAR_INTEGER,
            "0 = none, 1 = sphere, 2 = sphere + box",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_useLightCulling = idCVar(
            "r_useLightCulling",
            "3",
            CVAR_RENDERER or CVAR_INTEGER,
            "0 = none, 1 = box, 2 = exact clip of polyhedron faces, 3 = also areas",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_useLightScissors = idCVar(
            "r_useLightScissors",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "1 = use custom scissor rectangle for each light"
        )
        r_useClippedLightScissors = idCVar(
            "r_useClippedLightScissors",
            "1",
            CVAR_RENDERER or CVAR_INTEGER,
            "0 = full screen when near clipped, 1 = exact when near clipped, 2 = exact always",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_useEntityCulling =
            idCVar("r_useEntityCulling", "1", CVAR_RENDERER or CVAR_BOOL, "0 = none, 1 = box")
        r_useEntityScissors = idCVar(
            "r_useEntityScissors",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "1 = use custom scissor rectangle for each entity"
        )
        r_useInteractionCulling =
            idCVar("r_useInteractionCulling", "1", CVAR_RENDERER or CVAR_BOOL, "1 = cull interactions")
        r_useInteractionScissors = idCVar(
            "r_useInteractionScissors",
            "2",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = use a custom scissor rectangle for each shadow interaction, 2 = also crop using portal scissors",
            -2f,
            2f,
            ArgCompletion_Integer(-2, 2)
        )
        r_useShadowCulling = idCVar(
            "r_useShadowCulling",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "try to cull shadows from partially visible lights"
        )
        r_useFrustumFarDistance = idCVar(
            "r_useFrustumFarDistance",
            "0",
            CVAR_RENDERER or CVAR_FLOAT,
            "if != 0 force the view frustum far distance to this distance"
        )
        r_logFile =
            idCVar("r_logFile", "0", CVAR_RENDERER or CVAR_INTEGER, "number of frames to emit GL logs")
        r_clear = idCVar(
            "r_clear",
            "2",
            CVAR_RENDERER,
            "force screen clear every frame, 1 = purple, 2 = black, 'r g b' = custom"
        )
        r_offsetFactor =
            idCVar("r_offsetfactor", "0", CVAR_RENDERER or CVAR_FLOAT, "polygon offset parameter")
        r_offsetUnits =
            idCVar("r_offsetunits", "-600", CVAR_RENDERER or CVAR_FLOAT, "polygon offset parameter")
        r_shadowPolygonOffset = idCVar(
            "r_shadowPolygonOffset",
            "-1",
            CVAR_RENDERER or CVAR_FLOAT,
            "bias value added to depth test for stencil shadow drawing"
        )
        r_shadowPolygonFactor =
            idCVar("r_shadowPolygonFactor", "0", CVAR_RENDERER or CVAR_FLOAT, "scale value for stencil shadow drawing")
        r_frontBuffer =
            idCVar("r_frontBuffer", "0", CVAR_RENDERER or CVAR_BOOL, "draw to front buffer for debugging")
        r_skipSubviews = idCVar(
            "r_skipSubviews",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = don't render any gui elements on surfaces"
        )
        r_skipGuiShaders = idCVar(
            "r_skipGuiShaders",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = skip all gui elements on surfaces, 2 = skip drawing but still handle events, 3 = draw but skip events",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_skipParticles = idCVar(
            "r_skipParticles",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = skip all particle systems",
            0f,
            1f,
            ArgCompletion_Integer(0, 1)
        )
        r_subviewOnly = idCVar(
            "r_subviewOnly",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "1 = don't render main view, allowing subviews to be debugged"
        )
        r_shadows =
            idCVar("r_shadows", "1", CVAR_RENDERER or CVAR_BOOL or CVAR_ARCHIVE, "enable shadows")
        r_testARBProgram =
            idCVar("r_testARBProgram", "0", CVAR_RENDERER or CVAR_BOOL, "experiment with vertex/fragment programs")
        r_testGamma = idCVar(
            "r_testGamma",
            "0",
            CVAR_RENDERER or CVAR_FLOAT,
            "if > 0 draw a grid pattern to test gamma levels",
            0f,
            195f
        )
        r_testGammaBias = idCVar(
            "r_testGammaBias",
            "0",
            CVAR_RENDERER or CVAR_FLOAT,
            "if > 0 draw a grid pattern to test gamma levels"
        )
        r_testStepGamma = idCVar(
            "r_testStepGamma",
            "0",
            CVAR_RENDERER or CVAR_FLOAT,
            "if > 0 draw a grid pattern to test gamma levels"
        )
        r_lightScale =
            idCVar("r_lightScale", "2", CVAR_RENDERER or CVAR_FLOAT, "all light intensities are multiplied by this")
        r_lightSourceRadius =
            idCVar("r_lightSourceRadius", "0", CVAR_RENDERER or CVAR_FLOAT, "for soft-shadow sampling")
        r_flareSize =
            idCVar("r_flareSize", "1", CVAR_RENDERER or CVAR_FLOAT, "scale the flare deforms from the material def")
        r_useExternalShadows = idCVar(
            "r_useExternalShadows",
            "1",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = skip drawing caps when outside the light volume, 2 = force to no caps for testing",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_useOptimizedShadows = idCVar(
            "r_useOptimizedShadows",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "use the dmap generated static shadow volumes"
        )
        r_useScissor =
            idCVar("r_useScissor", "1", CVAR_RENDERER or CVAR_BOOL, "scissor clip as portals and lights are processed")
        r_useCombinerDisplayLists = idCVar(
            "r_useCombinerDisplayLists",
            "1",
            CVAR_RENDERER or CVAR_BOOL or CVAR_NOCHEAT,
            "put all nvidia register combiner programming in display lists"
        )
        r_useDepthBoundsTest = idCVar(
            "r_useDepthBoundsTest",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "use depth bounds test to reduce shadow fill"
        )
        r_screenFraction = idCVar(
            "r_screenFraction",
            "100",
            CVAR_RENDERER or CVAR_INTEGER,
            "for testing fill rate, the resolution of the entire screen can be changed"
        )
        r_demonstrateBug = idCVar(
            "r_demonstrateBug",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "used during development to show IHV's their problems"
        )
        r_usePortals = idCVar(
            "r_usePortals",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            " 1 = use portals to perform area culling, otherwise draw everything"
        )
        r_singleLight =
            idCVar("r_singleLight", "-1", CVAR_RENDERER or CVAR_INTEGER, "suppress all but one light")
        r_singleEntity =
            idCVar("r_singleEntity", "-1", CVAR_RENDERER or CVAR_INTEGER, "suppress all but one entity")
        r_singleSurface = idCVar(
            "r_singleSurface",
            "-1",
            CVAR_RENDERER or CVAR_INTEGER,
            "suppress all but one surface on each entity"
        )
        r_singleArea =
            idCVar("r_singleArea", "0", CVAR_RENDERER or CVAR_BOOL, "only draw the portal area the view is actually in")
        r_forceLoadImages = idCVar(
            "r_forceLoadImages",
            "0",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
            "draw all images to screen after registration"
        )
        r_orderIndexes = idCVar(
            "r_orderIndexes",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "perform index reorganization to optimize vertex use"
        )
        r_lightAllBackFaces = idCVar(
            "r_lightAllBackFaces",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "light all the back faces, even when they would be shadowed"
        )

        // visual debugging info
        r_showPortals = idCVar(
            "r_showPortals",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "draw portal outlines in color based on passed / not passed"
        )
        r_showUnsmoothedTangents = idCVar(
            "r_showUnsmoothedTangents",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "if 1, put all nvidia register combiner programming in display lists"
        )
        r_showSilhouette = idCVar(
            "r_showSilhouette",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "highlight edges that are casting shadow planes"
        )
        r_showVertexColor = idCVar(
            "r_showVertexColor",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "draws all triangles with the solid vertex color"
        )
        r_showUpdates =
            idCVar("r_showUpdates", "0", CVAR_RENDERER or CVAR_BOOL, "report entity and light updates and ref counts")
        r_showDemo =
            idCVar("r_showDemo", "0", CVAR_RENDERER or CVAR_BOOL, "report reads and writes to the demo file")
        r_showDynamic =
            idCVar("r_showDynamic", "0", CVAR_RENDERER or CVAR_BOOL, "report stats on dynamic surface generation")
        r_showLightScale = idCVar(
            "r_showLightScale",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "report the scale factor applied to drawing for overbrights"
        )
        r_showDefs =
            idCVar("r_showDefs", "0", CVAR_RENDERER or CVAR_BOOL, "report the number of modeDefs and lightDefs in view")
        r_showTrace = idCVar(
            "r_showTrace",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "show the intersection of an eye trace with the world",
            ArgCompletion_Integer(0, 2)
        )
        r_showIntensity = idCVar(
            "r_showIntensity",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "draw the screen colors based on intensity, red = 0, green = 128, blue = 255"
        )
        r_showImages = idCVar(
            "r_showImages",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = show all images instead of rendering, 2 = show in proportional size",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_showSmp =
            idCVar("r_showSmp", "0", CVAR_RENDERER or CVAR_BOOL, "show which end (front or back) is blocking")
        r_showLights = idCVar(
            "r_showLights",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = just print volumes numbers, highlighting ones covering the view, 2 = also draw planes of each volume, 3 = also draw edges of each volume",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showShadows = idCVar(
            "r_showShadows",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = visualize the stencil shadow volumes, 2 = draw filled in",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showShadowCount = idCVar(
            "r_showShadowCount",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "colors screen based on shadow volume depth complexity, >= 2 = print overdraw count based on stencil index values, 3 = only show turboshadows, 4 = only show static shadows",
            0f,
            4f,
            ArgCompletion_Integer(0, 4)
        )
        r_showLightScissors =
            idCVar("r_showLightScissors", "0", CVAR_RENDERER or CVAR_BOOL, "show light scissor rectangles")
        r_showEntityScissors =
            idCVar("r_showEntityScissors", "0", CVAR_RENDERER or CVAR_BOOL, "show entity scissor rectangles")
        r_showInteractionFrustums = idCVar(
            "r_showInteractionFrustums",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = show a frustum for each interaction, 2 = also draw lines to light origin, 3 = also draw entity bbox",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showInteractionScissors = idCVar(
            "r_showInteractionScissors",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = show screen rectangle which contains the interaction frustum, 2 = also draw construction lines",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_showLightCount = idCVar(
            "r_showLightCount",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = colors surfaces based on light count, 2 = also count everything through walls, 3 = also print overdraw",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showViewEntitys = idCVar(
            "r_showViewEntitys",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = displays the bounding boxes of all view models, 2 = print index numbers"
        )
        r_showTris = idCVar(
            "r_showTris",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "enables wireframe rendering of the world, 1 = only draw visible ones, 2 = draw all front facing, 3 = draw all",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showSurfaceInfo =
            idCVar("r_showSurfaceInfo", "0", CVAR_RENDERER or CVAR_BOOL, "show surface material name under crosshair")
        r_showNormals =
            idCVar("r_showNormals", "0", CVAR_RENDERER or CVAR_FLOAT, "draws wireframe normals")
        r_showMemory =
            idCVar("r_showMemory", "0", CVAR_RENDERER or CVAR_BOOL, "print frame memory utilization")
        r_showCull =
            idCVar("r_showCull", "0", CVAR_RENDERER or CVAR_BOOL, "report sphere and box culling stats")
        r_showInteractions =
            idCVar("r_showInteractions", "0", CVAR_RENDERER or CVAR_BOOL, "report interaction generation activity")
        r_showDepth = idCVar(
            "r_showDepth",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "display the contents of the depth buffer and the depth range"
        )
        r_showSurfaces =
            idCVar("r_showSurfaces", "0", CVAR_RENDERER or CVAR_BOOL, "report surface/light/shadow counts")
        r_showPrimitives =
            idCVar("r_showPrimitives", "0", CVAR_RENDERER or CVAR_INTEGER, "report drawsurf/index/vertex counts")
        r_showEdges = idCVar("r_showEdges", "0", CVAR_RENDERER or CVAR_BOOL, "draw the sil edges")
        r_showTexturePolarity =
            idCVar("r_showTexturePolarity", "0", CVAR_RENDERER or CVAR_BOOL, "shade triangles by texture area polarity")
        r_showTangentSpace = idCVar(
            "r_showTangentSpace",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "shade triangles by tangent space, 1 = use 1st tangent vector, 2 = use 2nd tangent vector, 3 = use normal vector",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_showDominantTri = idCVar(
            "r_showDominantTri",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "draw lines from vertexes to center of dominant triangles"
        )
        r_showAlloc =
            idCVar("r_showAlloc", "0", CVAR_RENDERER or CVAR_BOOL, "report alloc/free counts")
        r_showTextureVectors = idCVar(
            "r_showTextureVectors",
            "0",
            CVAR_RENDERER or CVAR_FLOAT,
            " if > 0 draw each triangles texture =new idCVar(tangent) vectors"
        )
        r_showOverDraw = idCVar(
            "r_showOverDraw",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "1 = geometry overdraw, 2 = light interaction overdraw, 3 = geometry and light interaction overdraw",
            0f,
            3f,
            ArgCompletion_Integer(0, 3)
        )
        r_lockSurfaces = idCVar(
            "r_lockSurfaces",
            "0",
            CVAR_RENDERER or CVAR_BOOL,
            "allow moving the view point without changing the composition of the scene, including culling"
        )
        r_useEntityCallbacks = idCVar(
            "r_useEntityCallbacks",
            "1",
            CVAR_RENDERER or CVAR_BOOL,
            "if 0, issue the callback immediately at update time, rather than defering"
        )
        r_showSkel = idCVar(
            "r_showSkel",
            "0",
            CVAR_RENDERER or CVAR_INTEGER,
            "draw the skeleton when model animates, 1 = draw model with skeleton, 2 = draw skeleton only",
            0f,
            2f,
            ArgCompletion_Integer(0, 2)
        )
        r_jointNameScale = idCVar(
            "r_jointNameScale",
            "0.02",
            CVAR_RENDERER or CVAR_FLOAT,
            "size of joint names when r_showskel is set to 1"
        )
        r_jointNameOffset = idCVar(
            "r_jointNameOffset",
            "0.5",
            CVAR_RENDERER or CVAR_FLOAT,
            "offset of joint names when r_showskel is set to 1"
        )
        r_cgVertexProfile =
            idCVar("r_cgVertexProfile", "best", CVAR_RENDERER or CVAR_ARCHIVE, "arbvp1, vp20, vp30")
        r_cgFragmentProfile =
            idCVar("r_cgFragmentProfile", "best", CVAR_RENDERER or CVAR_ARCHIVE, "arbfp1, fp30")
        r_debugLineDepthTest = idCVar(
            "r_debugLineDepthTest",
            "0",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL,
            "perform depth test on debug lines"
        )
        r_debugLineWidth =
            idCVar("r_debugLineWidth", "1", CVAR_RENDERER or CVAR_ARCHIVE or CVAR_BOOL, "width of debug lines")
        r_debugArrowStep = idCVar(
            "r_debugArrowStep",
            "120",
            CVAR_RENDERER or CVAR_ARCHIVE or CVAR_INTEGER,
            "step size of arrow cone line rotation in degrees",
            0f,
            120f
        )
        r_debugPolygonFilled =
            idCVar("r_debugPolygonFilled", "1", CVAR_RENDERER or CVAR_BOOL, "draw a filled polygon")
        r_materialOverride = idCVar(
            "r_materialOverride",
            "",
            CVAR_RENDERER,
            "overrides all materials",
            ArgCompletion_Decl(declType_t.DECL_MATERIAL)
        )
        r_debugRenderToTexture =
            idCVar("r_debugRenderToTexture", "0", CVAR_RENDERER or CVAR_INTEGER, "")
    }

    /*
     ==================
     GL_CheckErrors
     ==================
     */
    fun GL_CheckErrors() {
        var err: Int
        var s: String?
        var i: Int

        // check for up to 10 errors pending
        i = 0
        while (i < 10) {
            err = qgl.qglGetError()
            if (err == GL11.GL_NO_ERROR) {
                return
            }
            when (err) {
                GL11.GL_INVALID_ENUM -> s = "GL_INVALID_ENUM"
                GL11.GL_INVALID_VALUE -> s = "GL_INVALID_VALUE"
                GL11.GL_INVALID_OPERATION -> s = "GL_INVALID_OPERATION"
                GL11.GL_STACK_OVERFLOW -> s = "GL_STACK_OVERFLOW"
                GL11.GL_STACK_UNDERFLOW -> s = "GL_STACK_UNDERFLOW"
                GL11.GL_OUT_OF_MEMORY -> s = "GL_OUT_OF_MEMORY"
                else -> {
                    val ss: CharArray = CharArray(64)
                    snPrintf(ss, 64, "%d", err)
                    s = ctos(ss)
                }
            }
            if (!r_ignoreGLErrors!!.GetBool()) {
                common.Printf("GL_CheckErrors: %s\n", s)
            }
            i++
        }
    }

    /*
     ==================
     R_ScreenshotFilename

     Returns a filename with digits appended
     if we have saved a previous screenshot, don't scan
     from the beginning, because recording demo avis can involve
     thousands of shots
     ==================
     */
    fun R_ScreenshotFilename(lastNumber: CInt, base: String?, fileName: idStr) {
        var a: Int
        var b: Int
        var c: Int
        var d: Int
        var e: Int
        val restrict: Boolean = cvarSystem.GetCVarBool("fs_restrict")
        cvarSystem.SetCVarBool("fs_restrict", false)
        lastNumber.increment()
        if (lastNumber._val > 99999) {
            lastNumber._val = 99999
        }
        while (lastNumber._val < 99999) {
            var frac: Int = lastNumber._val
            a = frac / 10000
            frac -= a * 10000
            b = frac / 1000
            frac -= b * 1000
            c = frac / 100
            frac -= c * 100
            d = frac / 10
            frac -= d * 10
            e = frac
            fileName.set(String.format("%s%d%d%d%d%d.tga", base, a, b, c, d, e))
            if (lastNumber._val == 99999) {
                break
            }
            val len: Int = fileSystem.ReadFile(fileName.toString(), null, null)
            if (len <= 0) {
                break
            }
            lastNumber.increment()
        }
        cvarSystem.SetCVarBool("fs_restrict", restrict)
    }

    /*
     =================
     R_InitCvars
     =================
     */
    fun R_InitCvars() {
        // update latched cvars here
    }

    /*
     =================
     R_InitCommands
     =================
     */
    fun R_InitCommands() {
        cmdSystem.AddCommand(
            "MakeMegaTexture",
            MakeMegaTexture_f.instance,
            CMD_FL_RENDERER or CMD_FL_CHEAT,
            "processes giant images"
        )
        cmdSystem.AddCommand("sizeUp", R_SizeUp_f.instance, CMD_FL_RENDERER, "makes the rendered view larger")
        cmdSystem.AddCommand("sizeDown", R_SizeDown_f.instance, CMD_FL_RENDERER, "makes the rendered view smaller")
        cmdSystem.AddCommand("reloadGuis", R_ReloadGuis_f.instance, CMD_FL_RENDERER, "reloads guis")
        cmdSystem.AddCommand("listGuis", R_ListGuis_f.instance, CMD_FL_RENDERER, "lists guis")
        cmdSystem.AddCommand("touchGui", R_TouchGui_f.instance, CMD_FL_RENDERER, "touches a gui")
        cmdSystem.AddCommand("screenshot", R_ScreenShot_f.instance, CMD_FL_RENDERER, "takes a screenshot")
        cmdSystem.AddCommand("envshot", R_EnvShot_f.instance, CMD_FL_RENDERER, "takes an environment shot")
        cmdSystem.AddCommand(
            "makeAmbientMap",
            R_MakeAmbientMap_f.instance,
            CMD_FL_RENDERER or CMD_FL_CHEAT,
            "makes an ambient map"
        )
        cmdSystem.AddCommand("benchmark", R_Benchmark_f.instance, CMD_FL_RENDERER, "benchmark")
        cmdSystem.AddCommand("gfxInfo", GfxInfo_f.instance, CMD_FL_RENDERER, "show graphics info")
        cmdSystem.AddCommand(
            "modulateLights",
            tr_lightrun.R_ModulateLights_f.Companion.instance,
            CMD_FL_RENDERER or CMD_FL_CHEAT,
            "modifies shader parms on all lights"
        )
        cmdSystem.AddCommand(
            "testImage",
            R_TestImage_f.instance,
            CMD_FL_RENDERER or CMD_FL_CHEAT,
            "displays the given image centered on screen",
            ArgCompletion_ImageName.getInstance()
        )
        cmdSystem.AddCommand(
            "testVideo",
            R_TestVideo_f.instance,
            CMD_FL_RENDERER or CMD_FL_CHEAT,
            "displays the given cinematic",
            ArgCompletion_VideoName.getInstance()
        )
        cmdSystem.AddCommand(
            "reportSurfaceAreas",
            R_ReportSurfaceAreas_f.instance,
            CMD_FL_RENDERER,
            "lists all used materials sorted by surface area"
        )
        cmdSystem.AddCommand(
            "reportImageDuplication",
            R_ReportImageDuplication_f.instance,
            CMD_FL_RENDERER,
            "checks all referenced images for duplications"
        )
        cmdSystem.AddCommand(
            "regenerateWorld",
            tr_lightrun.R_RegenerateWorld_f.Companion.instance,
            CMD_FL_RENDERER,
            "regenerates all interactions"
        )
        cmdSystem.AddCommand(
            "showInteractionMemory",
            R_ShowInteractionMemory_f.Companion.instance,
            CMD_FL_RENDERER,
            "shows memory used by interactions"
        )
        cmdSystem.AddCommand(
            "showTriSurfMemory",
            tr_trisurf.R_ShowTriSurfMemory_f.Companion.instance,
            CMD_FL_RENDERER,
            "shows memory used by triangle surfaces"
        )
        cmdSystem.AddCommand("vid_restart", R_VidRestart_f.instance, CMD_FL_RENDERER, "restarts renderSystem")
        cmdSystem.AddCommand(
            "listRenderEntityDefs",
            RenderWorld.R_ListRenderEntityDefs_f.Companion.instance,
            CMD_FL_RENDERER,
            "lists the entity defs"
        )
        cmdSystem.AddCommand(
            "listRenderLightDefs",
            RenderWorld.R_ListRenderLightDefs_f.Companion.instance,
            CMD_FL_RENDERER,
            "lists the light defs"
        )
        cmdSystem.AddCommand("listModes", R_ListModes_f.instance, CMD_FL_RENDERER, "lists all video modes")
        cmdSystem.AddCommand(
            "reloadSurface",
            R_ReloadSurface_f.instance,
            CMD_FL_RENDERER,
            "reloads the decl and images for selected surface"
        )
    }

    /*
     =================
     R_InitMaterials
     =================
     */
    fun R_InitMaterials() {
        tr.defaultMaterial = DeclManager.declManager.FindMaterial("_default", false)
        if (NOT(tr.defaultMaterial)) {
            common.FatalError("_default material not found")
        }
        DeclManager.declManager.FindMaterial("_default", false)

        // needed by R_DeriveLightData
        DeclManager.declManager.FindMaterial("lights/defaultPointLight")
        DeclManager.declManager.FindMaterial("lights/defaultProjectedLight")
    }

    //#if MACOS_X
    //bool R_GetModeInfo( int *width, int *height, int mode ) {
    //#else
    fun R_GetModeInfo(width: IntArray?, height: IntArray?, mode: Int): Boolean {
//#endif
        val vm: vidmode_s
        if (mode < -1) {
            return false
        }
        if (mode >= s_numVidModes) {
            return false
        }
        if (mode == -1) {
            width!![0] = r_customWidth!!.GetInteger()
            height!![0] = r_customHeight!!.GetInteger()
            return true
        }
        vm = r_vidModes[mode]
        if (width != null) {
            width[0] = vm.width
        }
        if (height != null) {
            height[0] = vm.height
        }
        return true
    }

    /*
     ==================
     R_CheckPortableExtensions

     ==================
     */
    fun R_CheckPortableExtensions() {
//        throw new TempDump.TODO_Exception();
        tr_local.glConfig.glVersion = atof(
            tr_local.glConfig.version_string!!.replace(
                "(\\d+).(\\d+).(\\d+)".toRegex(),
                "$1.$2$3"
            )
        ) // converts openGL version from 1.1.x to 1.1x, which we can parse to float.
        //
        // GL_ARB_multitexture
        tr_local.glConfig.multitextureAvailable = R_CheckExtension("GL_ARB_multitexture")
        if (tr_local.glConfig.multitextureAvailable) {
            tr_local.glConfig.maxTextureUnits = qgl.qglGetInteger(ARBMultitexture.GL_MAX_TEXTURE_UNITS_ARB)
            if (tr_local.glConfig.maxTextureUnits > tr_local.MAX_MULTITEXTURE_UNITS) {
                tr_local.glConfig.maxTextureUnits = tr_local.MAX_MULTITEXTURE_UNITS
            }
            if (tr_local.glConfig.maxTextureUnits < 2) {
                tr_local.glConfig.multitextureAvailable = false // shouldn't ever happen
            }
            tr_local.glConfig.maxTextureCoords = qgl.qglGetInteger(ARBFragmentProgram.GL_MAX_TEXTURE_COORDS_ARB)
            tr_local.glConfig.maxTextureImageUnits =
                qgl.qglGetInteger(ARBFragmentProgram.GL_MAX_TEXTURE_IMAGE_UNITS_ARB)
        }
        //
        // GL_ARB_texture_env_combine
        tr_local.glConfig.textureEnvCombineAvailable = R_CheckExtension("GL_ARB_texture_env_combine")

        // GL_ARB_texture_cube_map
        tr_local.glConfig.cubeMapAvailable = R_CheckExtension("GL_ARB_texture_cube_map")

        // GL_ARB_texture_env_dot3
        tr_local.glConfig.envDot3Available = R_CheckExtension("GL_ARB_texture_env_dot3")

        // GL_ARB_texture_env_add
        tr_local.glConfig.textureEnvAddAvailable = R_CheckExtension("GL_ARB_texture_env_add")

        // GL_ARB_texture_non_power_of_two
        tr_local.glConfig.textureNonPowerOfTwoAvailable =
            R_CheckExtension("GL_ARB_texture_non_power_of_two")
        //
        // GL_ARB_texture_compression + GL_S3_s3tc
        // DRI drivers may have GL_ARB_texture_compression but no GL_EXT_texture_compression_s3tc
        tr_local.glConfig.textureCompressionAvailable =
            R_CheckExtension("GL_ARB_texture_compression") && R_CheckExtension("GL_EXT_texture_compression_s3tc")
        //
        // GL_EXT_texture_filter_anisotropic
        tr_local.glConfig.anisotropicAvailable = R_CheckExtension("GL_EXT_texture_filter_anisotropic")
        if (tr_local.glConfig.anisotropicAvailable) {
            val maxTextureAnisotropy: FloatBuffer = BufferUtils.createFloatBuffer(16)
            qgl.qglGetFloatv(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, maxTextureAnisotropy)
            common.Printf(
                "   maxTextureAnisotropy: %f\n",
                (maxTextureAnisotropy.get().also({ tr_local.glConfig.maxTextureAnisotropy = it }))
            )
        } else {
            tr_local.glConfig.maxTextureAnisotropy = 1f
        }
        //
        // GL_EXT_texture_lod_bias
        // The actual extension is broken as specificed, storing the state in the texture unit instead
        // of the texture object.  The behavior in GL 1.4 is the behavior we use.
        if (tr_local.glConfig.glVersion >= 1.4 || R_CheckExtension("GL_EXT_texture_lod")) {
            common.Printf("...using %s\n", "GL_1.4_texture_lod_bias")
            tr_local.glConfig.textureLODBiasAvailable = true
        } else {
            common.Printf("X..%s not found\n", "GL_1.4_texture_lod_bias")
            tr_local.glConfig.textureLODBiasAvailable = false
        }
        //
        // GL_EXT_shared_texture_palette
        tr_local.glConfig.sharedTexturePaletteAvailable =
            R_CheckExtension("GL_EXT_shared_texture_palette")
        //
        // GL_EXT_texture3D (not currently used for anything)
        tr_local.glConfig.texture3DAvailable = R_CheckExtension("GL_EXT_texture3D")
        //
        // EXT_stencil_wrap
        // This isn't very important, but some pathological case might cause a clamp error and give a shadow bug.
        // Nvidia also believes that future hardware may be able to run faster with this enabled to avoid the
        // serialization of clamping.
        if (R_CheckExtension("GL_EXT_stencil_wrap")) {
            tr.stencilIncr = EXTStencilWrap.GL_INCR_WRAP_EXT
            tr.stencilDecr = EXTStencilWrap.GL_DECR_WRAP_EXT
        } else {
            tr.stencilIncr = GL11.GL_INCR
            tr.stencilDecr = GL11.GL_DECR
        }
        //
        // GL_NV_register_combiners
        tr_local.glConfig.registerCombinersAvailable = R_CheckExtension("GL_NV_register_combiners")
        //
        // GL_EXT_stencil_two_side
        tr_local.glConfig.twoSidedStencilAvailable = R_CheckExtension("GL_EXT_stencil_two_side")
        if (tr_local.glConfig.twoSidedStencilAvailable) {
        } else {
            tr_local.glConfig.atiTwoSidedStencilAvailable =
                R_CheckExtension("GL_ATI_separate_stencil")
        }
        //
        // GL_ATI_fragment_shader
        tr_local.glConfig.atiFragmentShaderAvailable = R_CheckExtension("GL_ATI_fragment_shader")
        if (!tr_local.glConfig.atiFragmentShaderAvailable) {
            // only on OSX: ATI_fragment_shader is faked through ATI_text_fragment_shader (macosx_glimp.cpp)
            tr_local.glConfig.atiFragmentShaderAvailable =
                R_CheckExtension("GL_ATI_text_fragment_shader")
        }
        //
        // ARB_vertex_buffer_object
        tr_local.glConfig.ARBVertexBufferObjectAvailable =
            R_CheckExtension("GL_ARB_vertex_buffer_object")
        //
        // ARB_vertex_program
        tr_local.glConfig.ARBVertexProgramAvailable = R_CheckExtension("GL_ARB_vertex_program")
        //
        // ARB_fragment_program
        if (r_inhibitFragmentProgram!!.GetBool()) {
            tr_local.glConfig.ARBFragmentProgramAvailable = false
        } else {
            tr_local.glConfig.ARBFragmentProgramAvailable =
                R_CheckExtension("GL_ARB_fragment_program")
        }
        //
        // GL_EXT_depth_bounds_test
        tr_local.glConfig.depthBoundsTestAvailable = R_CheckExtension("EXT_depth_bounds_test")
    }

    /*
     ==================
     R_SampleCubeMap
     ==================
     */
    private fun R_SampleCubeMap(dir: idVec3, size: Int, buffers: Array<ByteBuffer> /*[6]*/, result: ByteArray /*[4]*/) {
        val adir: FloatArray = FloatArray(3)
        val axis: Int
        var x: Int
        var y: Int
        adir[0] = abs(dir[0].toDouble()).toFloat()
        adir[1] = abs(dir[1].toDouble()).toFloat()
        adir[2] = abs(dir[2].toDouble()).toFloat()
        if (dir[0] >= adir[1] && dir[0] >= adir[2]) {
            axis = 0
        } else if (-dir[0] >= adir[1] && -dir[0] >= adir[2]) {
            axis = 1
        } else if (dir[1] >= adir[0] && dir[1] >= adir[2]) {
            axis = 2
        } else if (-dir[1] >= adir[0] && -dir[1] >= adir[2]) {
            axis = 3
        } else if (dir[2] >= adir[1] && dir[2] >= adir[2]) {
            axis = 4
        } else {
            axis = 5
        }
        var fx: Float = (dir.times(cubeAxis[axis]!![1])) / (dir.times(
            cubeAxis[axis]!![0]
        ))
        var fy: Float = (dir.times(cubeAxis[axis]!![2])) / (dir.times(
            cubeAxis[axis]!![0]
        ))
        fx = -fx
        fy = -fy
        x = (size * 0.5 * (fx + 1)).toInt()
        y = (size * 0.5 * (fy + 1)).toInt()
        if (x < 0) {
            x = 0
        } else if (x >= size) {
            x = size - 1
        }
        if (y < 0) {
            y = 0
        } else if (y >= size) {
            y = size - 1
        }
        result[0] = buffers[axis].get((y * size + x) * 4 + 0)
        result[1] = buffers[axis].get((y * size + x) * 4 + 1)
        result[2] = buffers[axis].get((y * size + x) * 4 + 2)
        result[3] = buffers[axis].get((y * size + x) * 4 + 3)
    }

    /*
     ================
     R_RenderingFPS
     ================
     */
    fun R_RenderingFPS(renderView: renderView_s?): Float {
        qgl.qglFinish()
        val start: Int = Sys_Milliseconds()
        var end: Int
        var count: Int = 0
        while (true) {
            // render
            RenderSystem.renderSystem.BeginFrame(tr_local.glConfig.vidWidth, tr_local.glConfig.vidHeight)
            tr.primaryWorld!!.RenderScene(renderView!!)
            RenderSystem.renderSystem.EndFrame(null, null)
            qgl.qglFinish()
            count++
            end = Sys_Milliseconds()
            if (end - start > SAMPLE_MSEC) {
                break
            }
        }
        val fps: Float = (count * 1000.0 / (end - start)).toFloat()
        return fps
    }

    fun R_InitOpenGL() {
//	GLint			temp;
        val temp: IntBuffer = BufferUtils.createIntBuffer(16)
        val parms: glimpParms_t = glimpParms_t()
        var i: Int
        common.Printf("----- R_InitOpenGL -----\n")
        if (tr_local.glConfig.isInitialized) {
            common.FatalError("R_InitOpenGL called while active")
        }

        // in case we had an error while doing a tiled rendering
        tr.viewportOffset[0] = 0
        tr.viewportOffset[1] = 0

        //
        // initialize OS specific portions of the renderSystem
        //
        i = 0
        while (i < 2) {

            // set the parameters we are trying
            val vidWidth: IntArray = intArrayOf(0)
            val vidHeight: IntArray = intArrayOf(0)
            R_GetModeInfo(vidWidth, vidHeight, r_mode!!.GetInteger())
            tr_local.glConfig.vidWidth = vidWidth[0]
            tr_local.glConfig.vidHeight = vidHeight[0]
            parms.width = tr_local.glConfig.vidWidth
            parms.height = tr_local.glConfig.vidHeight
            parms.fullScreen = r_fullscreen!!.GetBool()
            parms.displayHz = r_displayRefresh!!.GetInteger()
            parms.multiSamples = r_multiSamples!!.GetInteger()
            parms.stereo = false
            if (GLimp_Init(parms)) {
                // it's ALIVE!
                break
            }
            if (i == 1) {
                common.FatalError("Unable to initialize OpenGL")
            }

            // if we failed, set everything back to "safe mode"
            // and try again
            r_mode!!.SetInteger(3)
            r_fullscreen!!.SetInteger(1)
            r_displayRefresh!!.SetInteger(0)
            r_multiSamples!!.SetInteger(0)
            i++
        }

        // input and sound systems need to be tied to the new window
        Sys_InitInput()
        snd_system.soundSystem.InitHW()

        // get our config strings
        tr_local.glConfig.vendor_string = qgl.qglGetString(GL11.GL_VENDOR)
        tr_local.glConfig.renderer_string = qgl.qglGetString(GL11.GL_RENDERER)
        tr_local.glConfig.version_string = qgl.qglGetString(GL11.GL_VERSION)
        val bla: StringBuilder = StringBuilder()
        var ext: String?
        //        for (int j = 0; (ext = qglGetStringi(GL_EXTENSIONS, j)) != null; j++) {
//            bla.append(ext).append(' ');
//        }
        tr_local.glConfig.extensions_string = qgl.qglGetStringi(GL11.GL_EXTENSIONS, 0)

        // OpenGL driver constants
        qgl.qglGetIntegerv(GL11.GL_MAX_TEXTURE_SIZE, temp)
        tr_local.glConfig.maxTextureSize = temp.get()

        // stubbed or broken drivers may have reported 0...
        if (tr_local.glConfig.maxTextureSize <= 0) {
            tr_local.glConfig.maxTextureSize = 256
        }
        tr_local.glConfig.isInitialized = true

        // recheck all the extensions (FIXME: this might be dangerous)
        R_CheckPortableExtensions()

        // parse our vertex and fragment programs, possibly disable support for
        // one of the paths if there was an error
//        R_NV10_Init();
//        R_NV20_Init();
//        R_R200_Init();
        draw_arb2.R_ARB2_Init()
        cmdSystem.AddCommand(
            "reloadARBprograms",
            R_ReloadARBPrograms_f.Companion.instance,
            CMD_FL_RENDERER,
            "reloads ARB programs"
        )
        R_ReloadARBPrograms_f.Companion.instance.run(null)

        // allocate the vertex array range or vertex objects
        VertexCache.vertexCache.Init()

        // select which renderSystem we are going to use
        r_renderer!!.SetModified()
        tr.SetBackEndRenderer()

        // allocate the frame data, which may be more if smp is enabled
        tr_main.R_InitFrameData()

        // Reset our gamma
        R_SetColorMappings()
        if (_WIN32) {
            if (!glCheck) { // && win32.osversion.dwMajorVersion == 6) {//TODO:should this be applicable?
                glCheck = true
                if (0 == Icmp(
                        tr_local.glConfig.vendor_string!!,
                        "Microsoft"
                    ) && FindText(tr_local.glConfig.renderer_string!!, "OpenGL-D3D") != -1
                ) {
                    if (cvarSystem.GetCVarBool("r_fullscreen")) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "vid_restart partial windowed\n")
                        Sys_GrabMouseCursor(true)
                    }
                    //TODO: messageBox below.
//                    int ret = MessageBox(null, "Please install OpenGL drivers from your graphics hardware vendor to run " + GAME_NAME + ".\nYour OpenGL functionality is limited.",
//                            "Insufficient OpenGL capabilities", MB_OKCANCEL | MB_ICONWARNING | MB_TASKMODAL);
//                    if (ret == IDCANCEL) {
//                        cmdSystem.BufferCommandText(CMD_EXEC_APPEND, "quit\n");
//                        cmdSystem.ExecuteCommandBuffer();
//                    }
                    if (cvarSystem.GetCVarBool("r_fullscreen")) {
                        cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "vid_restart\n")
                    }
                }
            }
        }
    }

    /*
     ===============
     R_SetColorMappings
     ===============
     */
    fun R_SetColorMappings() {
//        int i, j;
        val g: Float
        val b: Float
        var inf: Double
        b = r_brightness!!.GetFloat()
        g = r_gamma!!.GetFloat()

//        for (i = 0; i < 256; i++) {
//            j = (int) (i * b);
//            if (j > 255) {
//                j = 255;
//            }
        if (g == 1f) {
            inf = ((1 shl 8) or 1).toDouble()
        } else {
            inf = (0xffff * (b / 255.0f).pow((1.0f / g).toFloat()) + 0.5f).toDouble()
        }
        if (inf < 0) {
            inf = 0.0
        } else if (inf > 0xffff) {
            inf = TODO("Could not convert double literal '0xffff' to Kotlin")
        }

//            tr.gammaTable[i] = (short) inf;
//        }
//        GLimp_SetGamma(tr.gammaTable, tr.gammaTable, tr.gammaTable);
//        GLimp_SetGamma((float) inf, 0, 0);//TODO:differentiate between gamma and brightness.//TODO: the original function was rgb.
    }

    fun R_ScreenShot_f(args: CmdArgs.idCmdArgs?) {
        val checkName: idStr = idStr()
        var width: Int = tr_local.glConfig.vidWidth
        var height: Int = tr_local.glConfig.vidHeight
        val x: Int = 0
        val y: Int = 0
        var blends: Int = 0
        when (args!!.Argc()) {
            1 -> {
                width = tr_local.glConfig.vidWidth
                height = tr_local.glConfig.vidHeight
                blends = 1
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkName)
            }

            2 -> {
                width = tr_local.glConfig.vidWidth
                height = tr_local.glConfig.vidHeight
                blends = 1
                checkName.set(args!!.Argv(1))
            }

            3 -> {
                width = args!!.Argv(1).toInt()
                height = args!!.Argv(2).toInt()
                blends = 1
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkName)
            }

            4 -> {
                width = args!!.Argv(1).toInt()
                height = args!!.Argv(2).toInt()
                blends = args!!.Argv(3).toInt()
                if (blends < 1) {
                    blends = 1
                }
                if (blends > MAX_BLENDS) {
                    blends = MAX_BLENDS
                }
                R_ScreenshotFilename(lastNumber, "screenshots/shot", checkName)
            }

            else -> {
                common.Printf("usage: screenshot\n       screenshot <filename>\n       screenshot <width> <height>\n       screenshot <width> <height> <blends>\n")
                return
            }
        }

        // put the console away
        Console.console.Close()
        tr.TakeScreenshot(width, height, checkName.toString(), blends, null)
        common.Printf("Wrote %s\n", checkName.toString())
    }

    /*
     ===============
     R_StencilShot
     Save out a screenshot showing the stencil buffer expanded by 16x range
     ===============
     */
    fun R_StencilShot() {
        var buffer: ByteBuffer?
        var i: Int
        val c: Int
        val width: Int = tr.GetScreenWidth()
        val height: Int = tr.GetScreenHeight()
        val pix: Int = width * height
        c = pix * 3 + 18
        buffer = ByteBuffer.allocate(c) // Mem_Alloc(c);
        //        memset(buffer, 0, 18);
//        buffer = new int[18];//TODO:use c?
        var byteBuffer: ByteBuffer? = ByteBuffer.allocate(pix) // Mem_Alloc(pix);
        qgl.qglReadPixels(0, 0, width, height, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, byteBuffer)
        i = 0
        while (i < pix) {
            buffer.put(18 + i * 3, byteBuffer!!.get(i))
            buffer.put(18 + (i * 3) + 1, byteBuffer.get(i))
            //		buffer[18+i*3+2] = ( byteBuffer[i] & 15 ) * 16;
            buffer.put(18 + (i * 3) + 2, byteBuffer.get(i))
            i++
        }

        // fill in the header (this is vertically flipped, which qglReadPixels emits)
        buffer.put(2, 2.toByte()) // uncompressed type
        buffer.put(12, (width and 255).toByte()) //TODO: mayhaps use int[] instead of byte[]?
        buffer.put(13, (width shr 8).toByte())
        buffer.put(14, (height and 255).toByte())
        buffer.put(15, (height shr 8).toByte())
        buffer.put(16, 24.toByte()) // pixel size
        fileSystem.WriteFile("screenshots/stencilShot.tga", buffer, c, "fs_savepath")

//        Mem_Free(buffer);
//        Mem_Free(byteBuffer);
        buffer = null
        byteBuffer = null
    }

    /*
     =================
     R_CheckExtension
     =================
     */
    fun R_CheckExtension(name: String?): Boolean {
        if ((null == tr_local.glConfig.extensions_string
                    || !tr_local.glConfig.extensions_string!!.contains((name)!!))
        ) {
            common.Printf("X..%s not found\n", name!!)
            return false
        }
        common.Printf("...using %s\n", name)
        return true
    }

    /*
     ====================
     R_ReadTiledPixels

     Allows the rendering of an image larger than the actual window by
     tiling it into window-sized chunks and rendering each chunk separately

     If ref isn't specified, the full session UpdateScreen will be done.
     ====================
     */
    fun R_ReadTiledPixels(width: Int, height: Int, buffer: ByteArray?, offset: Int, ref: renderView_s? /*= NULL*/) {
        // include extra space for OpenGL padding to word boundaries
        var temp: ByteArray? =
            ByteArray((tr_local.glConfig.vidWidth + 3) * tr_local.glConfig.vidHeight * 3) //R_StaticAlloc( (glConfig.vidWidth+3) * glConfig.vidHeight * 3 );
        val oldWidth: Int = tr_local.glConfig.vidWidth
        val oldHeight: Int = tr_local.glConfig.vidHeight
        tr.tiledViewport[0] = width
        tr.tiledViewport[1] = height

        // disable scissor, so we don't need to adjust all those rects
        r_useScissor!!.SetBool(false)
        var xo: Int = 0
        while (xo < width) {
            var yo: Int = 0
            while (yo < height) {
                tr.viewportOffset[0] = -xo
                tr.viewportOffset[1] = -yo
                if (ref != null) {
                    tr.BeginFrame(oldWidth, oldHeight)
                    tr.primaryWorld!!.RenderScene(ref)
                    tr.EndFrame(null, null)
                } else {
                    Session.session.UpdateScreen()
                }
                var w: Int = oldWidth
                if (xo + w > width) {
                    w = width - xo
                }
                var h: Int = oldHeight
                if (yo + h > height) {
                    h = height - yo
                }
                qgl.qglReadBuffer(GL11.GL_FRONT)
                qgl.qglReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, ByteBuffer.wrap(temp))
                val row: Int = (w * 3 + 3) and 3.inv() // OpenGL pads to dword boundaries
                for (y in 0 until h) {
//				memcpy( buffer + ( ( yo + y )* width + xo ) * 3,
//					temp + y * row, w * 3 );
                    System.arraycopy(temp, y * row, buffer, offset + (((yo + y) * width + xo) * 3), w * 3)
                }
                yo += oldHeight
            }
            xo += oldWidth
        }
        r_useScissor!!.SetBool(true)
        tr.viewportOffset[0] = 0
        tr.viewportOffset[1] = 0
        tr.tiledViewport[0] = 0
        tr.tiledViewport[1] = 0

//	R_StaticFree( temp );
        temp = null
        tr_local.glConfig.vidWidth = oldWidth
        tr_local.glConfig.vidHeight = oldHeight
    }

    /*
     ====================
     R_GetModeInfo

     r_mode is normally a small non-negative integer that
     looks resolutions up in a table, but if it is set to -1,
     the values from r_customWidth, amd r_customHeight
     will be used instead.
     ====================
     */
    class vidmode_s(var description: String, var width: Int, var height: Int)

    /*
     =================
     R_SizeUp_f

     Keybinding command
     =================
     */
    internal class R_SizeUp_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            if (r_screenFraction!!.GetInteger() + 10 > 100) {
                r_screenFraction!!.SetInteger(100)
            } else {
                r_screenFraction!!.SetInteger(r_screenFraction!!.GetInteger() + 10)
            }
        }

        companion object {
            val instance: cmdFunction_t = R_SizeUp_f()
        }
    }

    /*
     =================
     R_SizeDown_f

     Keybinding command
     =================
     */
    internal class R_SizeDown_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            if (r_screenFraction!!.GetInteger() - 10 < 10) {
                r_screenFraction!!.SetInteger(10)
            } else {
                r_screenFraction!!.SetInteger(r_screenFraction!!.GetInteger() - 10)
            }
        }

        companion object {
            val instance: cmdFunction_t = R_SizeDown_f()
        }
    }

    /*
     ===============
     TouchGui_f

     this is called from the main thread
     ===============
     */
    internal class R_TouchGui_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            val gui: String? = args!!.Argv(1)
            if (gui == null || gui.isEmpty()) {
                common.Printf("USAGE: touchGui <guiName>\n")
                return
            }
            common.Printf("touchGui %s\n", gui)
            Session.session.UpdateScreen()
            uiManager.Touch(gui)
        }

        companion object {
            val instance: cmdFunction_t = R_TouchGui_f()
        }
    }

    internal class R_ScreenShot_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            val checkname: idStr = idStr()
            var width: Int = tr_local.glConfig.vidWidth
            var height: Int = tr_local.glConfig.vidHeight
            val x: Int = 0
            val y: Int = 0
            var blends: Int = 0
            when (args!!.Argc()) {
                1 -> {
                    width = tr_local.glConfig.vidWidth
                    height = tr_local.glConfig.vidHeight
                    blends = 1
                    R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
                }

                2 -> {
                    width = tr_local.glConfig.vidWidth
                    height = tr_local.glConfig.vidHeight
                    blends = 1
                    checkname.set(args!!.Argv(1))
                }

                3 -> {
                    width = args!!.Argv(1).toInt()
                    height = args!!.Argv(2).toInt()
                    blends = 1
                    R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
                }

                4 -> {
                    width = args!!.Argv(1).toInt()
                    height = args!!.Argv(2).toInt()
                    blends = args!!.Argv(3).toInt()
                    if (blends < 1) {
                        blends = 1
                    }
                    if (blends > MAX_BLENDS) {
                        blends = MAX_BLENDS
                    }
                    R_ScreenshotFilename(lastNumber, "screenshots/shot", checkname)
                }

                else -> {
                    common.Printf("usage: screenshot\n       screenshot <filename>\n       screenshot <width> <height>\n       screenshot <width> <height> <blends>\n")
                    return
                }
            }

            // put the console away
            Console.console.Close()
            tr.TakeScreenshot(width, height, checkname.toString(), blends, null)
            common.Printf("Wrote %s\n", checkname)
        }

        companion object {
            val instance: cmdFunction_t = R_ScreenShot_f()
            private val lastNumber: CInt = CInt()
        }
    }

    /*
     ==================
     R_EnvShot_f

     envshot <basename>

     Saves out env/<basename>_ft.tga, etc
     ==================
     */
    internal class R_EnvShot_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var fullname: String? = null
            val baseName: String
            var i: Int
            val axis: Array<idMat3?> = arrayOfNulls(6)
            var ref: renderView_s
            val primary: viewDef_s
            var blends: Int
            val extensions /*[6]*/: Array<String> =
                arrayOf("_px.tga", "_nx.tga", "_py.tga", "_ny.tga", "_pz.tga", "_nz.tga")
            val size: Int
            if ((args!!.Argc() != 2) && (args!!.Argc() != 3) && (args!!.Argc() != 4)) {
                common.Printf("USAGE: envshot <basename> [size] [blends]\n")
                return
            }
            baseName = args!!.Argv(1)
            blends = 1
            if (args!!.Argc() == 4) {
                size = args!!.Argv(2).toInt()
                blends = args!!.Argv(3).toInt()
            } else if (args!!.Argc() == 3) {
                size = args!!.Argv(2).toInt()
                blends = 1
            } else {
                size = 256
                blends = 1
            }
            if (NOT(tr.primaryView)) {
                common.Printf("No primary view.\n")
                return
            }
            primary = viewDef_s(tr.primaryView!!)

//	memset( &axis, 0, sizeof( axis ) );
            axis[0]!!.set(0, 0, 1f)
            axis[0]!!.set(1, 2, 1f)
            axis[0]!!.set(2, 1, 1f)
            axis[1]!!.set(0, 0, -1f)
            axis[1]!!.set(1, 2, -1f)
            axis[1]!!.set(2, 1, 1f)
            axis[2]!!.set(0, 1, 1f)
            axis[2]!!.set(1, 0, -1f)
            axis[2]!!.set(2, 2, -1f)
            axis[3]!!.set(0, 1, -1f)
            axis[3]!!.set(1, 0, -1f)
            axis[3]!!.set(2, 2, 1f)
            axis[4]!!.set(0, 2, 1f)
            axis[4]!!.set(1, 0, -1f)
            axis[4]!!.set(2, 1, 1f)
            axis[5]!!.set(0, 2, -1f)
            axis[5]!!.set(1, 0, 1f)
            axis[5]!!.set(2, 1, 1f)
            i = 0
            while (i < 6) {
                ref = renderView_s(primary.renderView)
                ref.y = 0
                ref.x = ref.y
                ref.fov_y = 90f
                ref.fov_x = ref.fov_y
                ref.width = tr_local.glConfig.vidWidth
                ref.height = tr_local.glConfig.vidHeight
                ref.viewaxis = idMat3((axis[i])!!)
                fullname = String.format("env/%s%s", baseName, extensions[i])
                tr.TakeScreenshot(size, size, fullname, blends, ref)
                i++
            }
            common.Printf("Wrote %s, etc\n", fullname!!)
        }

        companion object {
            val instance: cmdFunction_t = R_EnvShot_f()
        }
    }

    /*
     ==================
     R_MakeAmbientMap_f

     R_MakeAmbientMap_f <basename> [size]

     Saves out env/<basename>_amb_ft.tga, etc
     ==================
     */
    internal class R_MakeAmbientMap_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var fullname: String?
            val baseName: String
            var i: Int
            var ref: renderView_s?
            var primary: viewDef_s?
            val downSample: Int
            val extensions /*[6]*/: Array<String> = arrayOf(
                "_px.tga", "_nx.tga", "_py.tga", "_ny.tga",
                "_pz.tga", "_nz.tga"
            )
            val outSize: Int
            val buffers: Array<ByteBuffer?> = arrayOfNulls(6)
            val width: IntArray = intArrayOf(0)
            val height: IntArray = intArrayOf(0)
            if (args!!.Argc() != 2 && args!!.Argc() != 3) {
                common.Printf("USAGE: ambientshot <basename> [size]\n")
                return
            }
            baseName = args!!.Argv(1)
            downSample = 0
            if (args!!.Argc() == 3) {
                outSize = args!!.Argv(2).toInt()
            } else {
                outSize = 32
            }

//	memset( &cubeAxis, 0, sizeof( cubeAxis ) );
            cubeAxis[0]!!.set(0, 0, 1f)
            cubeAxis[0]!!.set(1, 2, 1f)
            cubeAxis[0]!!.set(2, 1, 1f)
            cubeAxis[1]!!.set(0, 0, -1f)
            cubeAxis[1]!!.set(1, 2, -1f)
            cubeAxis[1]!!.set(2, 1, 1f)
            cubeAxis[2]!!.set(0, 1, 1f)
            cubeAxis[2]!!.set(1, 0, -1f)
            cubeAxis[2]!!.set(2, 2, -1f)
            cubeAxis[3]!!.set(0, 1, -1f)
            cubeAxis[3]!!.set(1, 0, -1f)
            cubeAxis[3]!!.set(2, 2, 1f)
            cubeAxis[4]!!.set(0, 2, 1f)
            cubeAxis[4]!!.set(1, 0, -1f)
            cubeAxis[4]!!.set(2, 1, 1f)
            cubeAxis[5]!!.set(0, 2, -1f)
            cubeAxis[5]!!.set(1, 0, 1f)
            cubeAxis[5]!!.set(2, 1, 1f)

            // read all of the images
            i = 0
            while (i < 6) {
                fullname = String.format("env/%s%s", baseName, extensions[i])
                common.Printf("loading %s\n", fullname)
                Session.session.UpdateScreen()
                buffers[i] = Image_files.R_LoadImage(fullname, width, height, null, true)
                if (NOT(buffers[i])) {
                    common.Printf("failed.\n")
                    i--
                    while (i >= 0) {
                        buffers[i] = null
                        i--
                    }
                    return
                }
                i++
            }

            // resample with hemispherical blending
            val samples: Int = 1000
            val outBuffer: ByteBuffer = ByteBuffer.allocate(outSize * outSize * 4)
            for (map in 0..1) {
                i = 0
                while (i < 6) {
                    for (x in 0 until outSize) {
                        for (y in 0 until outSize) {
                            val dir: idVec3 = idVec3()
                            val total: FloatArray = FloatArray(3)
                            dir.set(
                                cubeAxis[i]!![0].plus(
                                    cubeAxis[i]!![1].times(-(-1 + 2.0f * x / (outSize - 1)))
                                ).plus(
                                    cubeAxis[i]!![2].times(-(-1 + 2.0f * y / (outSize - 1)))
                                )
                            )
                            dir.Normalize()
                            total[2] = 0f
                            total[1] = total[2]
                            total[0] = total[1]
                            //samples = 1;
                            val limit: Float =
                                if (itob(map)) 0.95f else 0.25f // small for specular, almost hemisphere for ambient
                            for (s in 0 until samples) {
                                // pick a random direction vector that is inside the unit sphere but not behind dir,
                                // which is a robust way to evenly sample a hemisphere
                                val test: idVec3 = idVec3()
                                while (true) {
                                    for (j in 0..2) {
                                        test[j] = (-1 + 2 * (Math.random()
                                            .toInt() and 0x7fff) / 0x7fff).toFloat()
                                    }
                                    if (test.Length() > 1.0) {
                                        continue
                                    }
                                    test.Normalize()
                                    if (test.times(dir) > limit) {    // don't do a complete hemisphere
                                        break
                                    }
                                }
                                val result: ByteArray = ByteArray(4)
                                //test = dir;
                                R_SampleCubeMap(test, width[0], buffers as Array<ByteBuffer>, result)
                                total[0] += result[0].toFloat()
                                total[1] += result[1].toFloat()
                                total[2] += result[2].toFloat()
                            }
                            outBuffer.put((y * outSize + x) * 4 + 0, (total[0] / samples).toInt().toByte())
                            outBuffer.put((y * outSize + x) * 4 + 1, (total[1] / samples).toInt().toByte())
                            outBuffer.put((y * outSize + x) * 4 + 2, (total[2] / samples).toInt().toByte())
                            outBuffer.put((y * outSize + x) * 4 + 3, 255.toByte())
                        }
                    }
                    if (map == 0) {
                        fullname = String.format("env/%s_amb%s", baseName, extensions[i])
                    } else {
                        fullname = String.format("env/%s_spec%s", baseName, extensions[i])
                    }
                    common.Printf("writing %s\n", fullname)
                    Session.session.UpdateScreen()
                    R_WriteTGA(fullname, outBuffer, outSize, outSize)
                    i++
                }
            }

//            for (i = 0; i < 6; i++) {
//                if (buffers[i]) {
//                    Mem_Free(buffers[i]);
//                }
//            }
        }

        companion object {
            private val cubeAxis: Array<idMat3?> = arrayOfNulls(6)
            val instance: cmdFunction_t = R_MakeAmbientMap_f()
        }
    }

    /*
     ================
     R_Benchmark_f
     ================
     */
    internal class R_Benchmark_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var fps: Float
            var msec: Float
            val view: renderView_s
            if (NOT(tr.primaryView)) {
                common.Printf("No primaryView for benchmarking\n")
                return
            }
            view = tr.primaryRenderView!!
            var size: Int = 100
            while (size >= 10) {
                r_screenFraction!!.SetInteger(size)
                fps = R_RenderingFPS(view)
                val kpix: Int =
                    (tr_local.glConfig.vidWidth * tr_local.glConfig.vidHeight * (size * 0.01) * (size * 0.01) * 0.001).toInt()
                msec = (1000.0f / fps)
                common.Printf("kpix: %4d  msec:%5.1f fps:%5.1f\n", kpix, msec, fps)
                size -= 10
            }

            // enable r_singleTriangle 1 while r_screenFraction is still at 10
            r_singleTriangle!!.SetBool(true)
            fps = R_RenderingFPS(view)
            msec = 1000.0f / fps
            common.Printf("single tri  msec:%5.1f fps:%5.1f\n", msec, fps)
            r_singleTriangle!!.SetBool(false)
            r_screenFraction!!.SetInteger(100)

            // enable r_skipRenderContext 1
            r_skipRenderContext!!.SetBool(true)
            fps = R_RenderingFPS(view)
            msec = 1000.0f / fps
            common.Printf("no context  msec:%5.1f fps:%5.1f\n", msec, fps)
            r_skipRenderContext!!.SetBool(false)
        }

        companion object {
            val instance: cmdFunction_t = R_Benchmark_f()
        }
    }

    /*
     ================
     GfxInfo_f
     ================
     */
    internal class GfxInfo_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            common.Printf("\nGL_VENDOR: %s\n", tr_local.glConfig.vendor_string!!)
            common.Printf("GL_RENDERER: %s\n", tr_local.glConfig.renderer_string!!)
            common.Printf("GL_VERSION: %s\n", tr_local.glConfig.version_string!!)
            common.Printf("GL_EXTENSIONS: %s\n", tr_local.glConfig.extensions_string!!)
            if (tr_local.glConfig.wgl_extensions_string != null) {
                common.Printf("WGL_EXTENSIONS: %s\n", tr_local.glConfig.wgl_extensions_string!!)
            }
            common.Printf("GL_MAX_TEXTURE_SIZE: %d\n", tr_local.glConfig.maxTextureSize)
            common.Printf("GL_MAX_TEXTURE_UNITS_ARB: %d\n", tr_local.glConfig.maxTextureUnits)
            common.Printf("GL_MAX_TEXTURE_COORDS_ARB: %d\n", tr_local.glConfig.maxTextureCoords)
            common.Printf("GL_MAX_TEXTURE_IMAGE_UNITS_ARB: %d\n", tr_local.glConfig.maxTextureImageUnits)
            common.Printf(
                "\nPIXELFORMAT: color(%d-bits) Z(%d-bit) stencil(%d-bits)\n",
                tr_local.glConfig.colorBits,
                tr_local.glConfig.depthBits,
                tr_local.glConfig.stencilBits
            )
            common.Printf(
                "MODE: %d, %d x %d %s hz:",
                r_mode!!.GetInteger(),
                tr_local.glConfig.vidWidth,
                tr_local.glConfig.vidHeight,
                fsstrings[btoi(r_fullscreen!!.GetBool())]
            )
            if (tr_local.glConfig.displayFrequency != 0) {
                common.Printf("%d\n", tr_local.glConfig.displayFrequency)
            } else {
                common.Printf("N/A\n")
            }
            common.Printf("CPU: %s\n", Sys_GetProcessorString())
            val active /*[2]*/: Array<String> = arrayOf("", " (ACTIVE)")
            common.Printf("ARB path ENABLED%s\n", active[btoi(tr.backEndRenderer == backEndName_t.BE_ARB)])

            if (tr_local.glConfig.allowARB2Path) {
                common.Printf(
                    "ARB2 path ENABLED%s\n",
                    active[btoi(tr.backEndRenderer == backEndName_t.BE_ARB2)]
                )
            } else {
                common.Printf("ARB2 path disabled\n")
            }

            //=============================
            common.Printf("-------\n")
            if (r_finish!!.GetBool()) {
                common.Printf("Forcing glFinish\n")
            } else {
                common.Printf("glFinish not forced\n")
            }
            if (_WIN32) {
// WGL_EXT_swap_interval
//                typedef BOOL (WINAPI * PFNWGLSWAPINTERVALEXTPROC) (int interval);
//                extern PFNWGLSWAPINTERVALEXTPROC wglSwapIntervalEXT;
                if (r_swapInterval!!.GetInteger() != 0) { //)  && NativeLibrary.isFunctionAvailableGlobal("wglSwapIntervalEXT")) {
                    common.Printf("Forcing swapInterval %d\n", r_swapInterval!!.GetInteger())
                } else {
                    common.Printf("swapInterval not forced\n")
                }
            }
            val tss: Boolean =
                tr_local.glConfig.twoSidedStencilAvailable || tr_local.glConfig.atiTwoSidedStencilAvailable
            if (!r_useTwoSidedStencil!!.GetBool() && tss) {
                common.Printf("Two sided stencil available but disabled\n")
            } else if (!tss) {
                common.Printf("Two sided stencil not available\n")
            } else if (tss) {
                common.Printf("Using two sided stencil\n")
            }
            if (VertexCache.vertexCache.IsFast()) {
                common.Printf("Vertex cache is fast\n")
            } else {
                common.Printf("Vertex cache is SLOW\n")
            }
        }

        companion object {
            private val fsstrings: Array<String> = arrayOf(
                "windowed",
                "fullscreen"
            )
            val instance: cmdFunction_t = GfxInfo_f()
        }
    }

    /*
     =============
     R_TestImage_f

     Display the given image centered on the screen.
     testimage <number>
     testimage <filename>
     =============
     */
    internal class R_TestImage_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            val imageNum: Int
            if (tr.testVideo != null) {
//		delete tr.testVideo;
                tr.testVideo = null
            }
            tr.testImage = null
            if (args!!.Argc() != 2) {
                return
            }
            if (IsNumeric(args!!.Argv(1))) {
                imageNum = args!!.Argv(1).toInt()
                if (imageNum >= 0 && imageNum < Image.globalImages.images.Num()) {
                    tr.testImage = Image.globalImages.images[imageNum]
                }
            } else {
                tr.testImage = Image.globalImages.ImageFromFile(
                    args!!.Argv(1),
                    textureFilter_t.TF_DEFAULT,
                    false,
                    textureRepeat_t.TR_REPEAT,
                    textureDepth_t.TD_DEFAULT
                )
            }
        }

        companion object {
            val instance: cmdFunction_t = R_TestImage_f()
        }
    }

    /*
     =============
     R_TestVideo_f

     Plays the cinematic file in a testImage
     =============
     */
    internal class R_TestVideo_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            if (tr.testVideo != null) {
//		delete tr.testVideo;
                tr.testVideo = null
            }
            tr.testImage = null
            if (args!!.Argc() < 2) {
                return
            }
            tr.testImage = Image.globalImages.ImageFromFile(
                "_scratch",
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
            tr.testVideo = idCinematic.Alloc()
            tr.testVideo!!.InitFromFile(args!!.Argv(1), true)
            val cin: cinData_t
            cin = tr.testVideo!!.ImageForTime(0)
            if (NOT(cin.image)) {
//		delete tr.testVideo;
                tr.testVideo = null
                tr.testImage = null
                return
            }
            common.Printf("%d x %d images\n", cin.imageWidth, cin.imageHeight)
            val len: Int = tr.testVideo!!.AnimationLength()
            common.Printf("%5.1f seconds of video\n", len * 0.001)
            tr.testVideoStartTime = (tr.primaryRenderView!!.time * 0.001).toFloat()

            // try to play the matching wav file
            val wavString: idStr = idStr(args!!.Argv(if ((args!!.Argc() == 2)) 1 else 2))
            wavString.StripFileExtension()
            wavString.plusAssign(".wav")
            Session.session.sw.PlayShaderDirectly(wavString.toString())
        }

        companion object {
            val instance: cmdFunction_t = R_TestVideo_f()
        }
    }

    /*
     ===================
     R_ReportSurfaceAreas_f

     Prints a list of the materials sorted by surface area
     ===================
     */
    internal class R_ReportSurfaceAreas_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val count: Int
            val list: Array<idMaterial?>
            count = DeclManager.declManager.GetNumDecls(declType_t.DECL_MATERIAL)
            list = arrayOfNulls(count)
            i = 0
            while (i < count) {
                list[i] = DeclManager.declManager.DeclByIndex(declType_t.DECL_MATERIAL, i, false) as idMaterial?
                i++
            }

//            qsort(list, count, sizeof(list[0]), new R_QsortSurfaceAreas());
            Arrays.sort(list, R_QsortSurfaceAreas())

            // skip over ones with 0 area
            i = 0
            while (i < count) {
                if (list[i]!!.GetSurfaceArea() > 0) {
                    break
                }
                i++
            }
            while (i < count) {

                // report size in "editor blocks"
                val blocks: Int = (list[i]!!.GetSurfaceArea() / 4096.0).toInt()
                common.Printf("%7i %s\n", blocks, list[i]!!.GetName())
                i++
            }
        }

        companion object {
            val instance: cmdFunction_t = R_ReportSurfaceAreas_f()
        }
    }

    /*
     ===================
     R_ReportImageDuplication_f

     Checks for images with the same hash value and does a better comparison
     ===================
     */
    internal class R_ReportImageDuplication_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            common.Printf("Images with duplicated contents:\n")
            var count: Int = 0
            i = 0
            while (i < Image.globalImages.images.Num()) {
                val image1: idImage? = Image.globalImages.images[i]
                if (image1!!.isPartialImage) {
                    // ignore background loading stubs
                    i++
                    continue
                }
                if (image1.generatorFunction != null) {
                    // ignore procedural images
                    i++
                    continue
                }
                if (image1.cubeFiles != cubeFiles_t.CF_2D) {
                    // ignore cube maps
                    i++
                    continue
                }
                if (image1.defaulted) {
                    i++
                    continue
                }
                val w1: IntArray = intArrayOf(0)
                val h1: IntArray = intArrayOf(0)
                val data1: ByteBuffer = R_LoadImageProgram(image1.imgName.toString(), w1, h1, null)!!
                j = 0
                while (j < i) {
                    val image2: idImage? = Image.globalImages.images[j]
                    if (image2!!.isPartialImage) {
                        j++
                        continue
                    }
                    if (image2.generatorFunction != null) {
                        j++
                        continue
                    }
                    if (image2.cubeFiles != cubeFiles_t.CF_2D) {
                        j++
                        continue
                    }
                    if (image2.defaulted) {
                        j++
                        continue
                    }
                    if (!(image1.imageHash == image2.imageHash)) {
                        j++
                        continue
                    }
                    if ((image2.uploadWidth != image1.uploadWidth
                                || image2.uploadHeight != image1.uploadHeight)
                    ) {
                        j++
                        continue
                    }
                    if (NOT(Icmp(image1.imgName, image2.imgName))) {
                        // ignore same image-with-different-parms
                        j++
                        continue
                    }
                    val w2: IntArray = intArrayOf(0)
                    val h2: IntArray = intArrayOf(0)
                    val data2: ByteBuffer = R_LoadImageProgram(image2.imgName.toString(), w2, h2, null)!!
                    if (w2 != w1 || h2 != h1) {
//                        R_StaticFree(data2);
                        j++
                        continue
                    }

//                    if (memcmp(data1, data2, w1 * h1 * 4)) {
                    if ((data1 == data2)) { //TODO: check range?
//                        R_StaticFree(data2);
                        j++
                        continue
                    }

//                    R_StaticFree(data2);
                    common.Printf("%s == %s\n", image1.imgName, image2.imgName)
                    Session.session.UpdateScreen(true)
                    count++
                    break
                    j++
                }
                i++
            }
            common.Printf("%d / %d collisions\n", count, Image.globalImages.images.Num())
        }

        companion object {
            val instance: cmdFunction_t = R_ReportImageDuplication_f()
        }
    }

    /*
     =================
     R_VidRestart_f
     =================
     */
    internal class R_VidRestart_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            val err: Int

            // if OpenGL isn't started, do nothing
            if (!tr_local.glConfig.isInitialized) {
                return
            }
            var full: Boolean = true
            var forceWindow: Boolean = false
            for (i in 1 until args!!.Argc()) {
                if (Icmp(args!!.Argv(i), "partial") == 0) {
                    full = false
                    continue
                }
                if (Icmp(args!!.Argv(i), "windowed") == 0) {
                    forceWindow = true
                    continue
                }
            }

            // this could take a while, so give them the cursor back ASAP
            Sys_GrabMouseCursor(false)

            // dump ambient caches
            ModelManager.renderModelManager.FreeModelVertexCaches()

            // free any current world interaction surfaces and vertex caches
            tr_lightrun.R_FreeDerivedData()

            // make sure the defered frees are actually freed
            tr_main.R_ToggleSmpFrame()
            tr_main.R_ToggleSmpFrame()

            // free the vertex caches so they will be regenerated again
            VertexCache.vertexCache.PurgeAll()

            // sound and input are tied to the window we are about to destroy
            if (full) {
                // free all of our texture numbers
                snd_system.soundSystem.ShutdownHW()
                Sys_ShutdownInput()
                Image.globalImages.PurgeAllImages()
                // free the context and close the window
                GLimp_Shutdown()
                tr_local.glConfig.isInitialized = false

                // create the new context and vertex cache
                val latch: Boolean = cvarSystem.GetCVarBool("r_fullscreen")
                if (forceWindow) {
                    cvarSystem.SetCVarBool("r_fullscreen", false)
                }
                R_InitOpenGL()
                cvarSystem.SetCVarBool("r_fullscreen", latch)

                // regenerate all images
                Image.globalImages.ReloadAllImages()
            } else {
                val parms: glimpParms_t = glimpParms_t()
                parms.width = tr_local.glConfig.vidWidth
                parms.height = tr_local.glConfig.vidHeight
                parms.fullScreen = !forceWindow && r_fullscreen!!.GetBool()
                parms.displayHz = r_displayRefresh!!.GetInteger()
                parms.multiSamples = r_multiSamples!!.GetInteger()
                parms.stereo = false
                GLimp_SetScreenParms(parms)
            }

            // make sure the regeneration doesn't use anything no longer valid
            tr.viewCount++
            //            System.out.println("tr.viewCount::R_VidRestart_f");
            tr.viewDef = null

            // regenerate all necessary interactions
            tr_lightrun.R_RegenerateWorld_f.Companion.instance.run(CmdArgs.idCmdArgs())

            // check for problems
            err = qgl.qglGetError()
            if (err != GL11.GL_NO_ERROR) {
                common.Printf("glGetError() = 0x%x\n", err)
            }

            // start sound playing again
            snd_system.soundSystem.SetMute(false)
        }

        companion object {
            val instance: cmdFunction_t = R_VidRestart_f()
        }
    }

    /*
     ==============
     R_ListModes_f
     ==============
     */
    internal class R_ListModes_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            common.Printf("\n")
            i = 0
            while (i < s_numVidModes) {
                common.Printf("%s\n", r_vidModes[i].description)
                i++
            }
            common.Printf("\n")
        }

        companion object {
            val instance: cmdFunction_t = R_ListModes_f()
        }
    }

    /*
     =====================
     R_ReloadSurface_f

     Reload the material displayed by r_showSurfaceInfo
     =====================
     */
    internal class R_ReloadSurface_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            val mt: modelTrace_s = modelTrace_s()
            val start: idVec3 = idVec3()
            val end: idVec3 = idVec3()

            // start far enough away that we don't hit the player model
            start.set(
                tr.primaryView!!.renderView.vieworg.plus(
                    tr.primaryView!!.renderView.viewaxis[0].times(16)
                )
            )
            end.set(start.plus(tr.primaryView!!.renderView.viewaxis[0].times(1000.0f)))
            if (!tr.primaryWorld!!.Trace(mt, start, end, 0.0f, false)) {
                return
            }
            common.Printf("Reloading %s\n", mt.material!!.GetName())

            // reload the decl
            mt.material!!.base!!.Reload()

            // reload any images used by the decl
            mt.material!!.ReloadImages(false)
        }

        companion object {
            val instance: cmdFunction_t = R_ReloadSurface_f()
        }
    }

    /* 
     ============================================================================== 
 
     SCREEN SHOTS 
 
     ============================================================================== 
     */
    internal class R_QsortSurfaceAreas() : cmp_t<idMaterial?> {
        public override fun compare(a: idMaterial?, b: idMaterial?): Int {
            val ac: Float
            val bc: Float
            if (!a!!.EverReferenced()) {
                ac = 0f
            } else {
                ac = a.GetSurfaceArea()
            }
            if (!b!!.EverReferenced()) {
                bc = 0f
            } else {
                bc = b.GetSurfaceArea()
            }
            if (ac < bc) {
                return -1
            }
            if (ac > bc) {
                return 1
            }
            return Icmp(a.GetName(), b.GetName())
        }
    }
}
