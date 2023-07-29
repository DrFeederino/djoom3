package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.VertexCache.vertexCache
import neo.Renderer.draw_common.RB_StencilShadowPass
import neo.Renderer.qgl.qglActiveTextureARB
import neo.Renderer.qgl.qglBindProgramARB
import neo.Renderer.qgl.qglClear
import neo.Renderer.qgl.qglColorPointer
import neo.Renderer.qgl.qglDisable
import neo.Renderer.qgl.qglDisableClientState
import neo.Renderer.qgl.qglDisableVertexAttribArrayARB
import neo.Renderer.qgl.qglEnable
import neo.Renderer.qgl.qglEnableClientState
import neo.Renderer.qgl.qglEnableVertexAttribArrayARB
import neo.Renderer.qgl.qglGetError
import neo.Renderer.qgl.qglGetIntegerv
import neo.Renderer.qgl.qglGetString
import neo.Renderer.qgl.qglProgramEnvParameter4fvARB
import neo.Renderer.qgl.qglProgramStringARB
import neo.Renderer.qgl.qglScissor
import neo.Renderer.qgl.qglStencilFunc
import neo.Renderer.qgl.qglVertexAttribPointerARB
import neo.Renderer.qgl.qglVertexPointer
import neo.Renderer.tr_backend.GL_SelectTexture
import neo.Renderer.tr_backend.GL_State
import neo.Renderer.tr_backend.RB_LogComment
import neo.Renderer.tr_local.GLS_DEPTHFUNC_EQUAL
import neo.Renderer.tr_local.GLS_DEPTHFUNC_LESS
import neo.Renderer.tr_local.GLS_DEPTHMASK
import neo.Renderer.tr_local.GLS_DSTBLEND_ONE
import neo.Renderer.tr_local.GLS_SRCBLEND_ONE
import neo.Renderer.tr_local.backEnd
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.glConfig
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.programParameter_t
import neo.Renderer.tr_local.program_t
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_render.DrawInteraction
import neo.Renderer.tr_render.RB_CreateSingleDrawInteractions
import neo.Renderer.tr_render.RB_DrawElementsWithCounters
import neo.TempDump.NOT
import neo.TempDump.TODO_Exception
import neo.TempDump.isNotNullOrEmpty
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentProgram
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

/**
 *
 */
object draw_arb2 {
    const val MAX_GLPROGS = 200

    /*
     =========================================================================================

     GENERAL INTERACTION RENDERING

     =========================================================================================
     */
    private val NEG_ONE = floatArrayOf(-1f, -1f, -1f, -1f)
    private val ONE = floatArrayOf(1f, 1f, 1f, 1f)

    //
    private val ZERO = floatArrayOf(0f, 0f, 0f, 0f)

    // a single file can have both a vertex program and a fragment program
    var progs = arrayOfNulls<progDef_t>(MAX_GLPROGS)

    //
    init {
        var a = 0
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST, "test.vfp")
        progs[a++] = progDef_t(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST, "test.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION, "interaction.vfp")
        progs[a++] =
            progDef_t(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION, "interaction.vfp")
        progs[a++] =
            progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_BUMPY_ENVIRONMENT, "bumpyEnvironment.vfp")
        progs[a++] = progDef_t(
            ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
            program_t.FPROG_BUMPY_ENVIRONMENT,
            "bumpyEnvironment.vfp"
        )
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_AMBIENT, "ambientLight.vfp")
        progs[a++] = progDef_t(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_AMBIENT, "ambientLight.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW, "shadow.vp")
        progs[a++] =
            progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_R200_INTERACTION, "R200_interaction.vp")
        progs[a++] = progDef_t(
            ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
            program_t.VPROG_NV20_BUMP_AND_LIGHT,
            "nv20_bumpAndLight.vp"
        )
        progs[a++] = progDef_t(
            ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
            program_t.VPROG_NV20_DIFFUSE_COLOR,
            "nv20_diffuseColor.vp"
        )
        progs[a++] = progDef_t(
            ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
            program_t.VPROG_NV20_SPECULAR_COLOR,
            "nv20_specularColor.vp"
        )
        progs[a++] = progDef_t(
            ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
            program_t.VPROG_NV20_DIFFUSE_AND_SPECULAR_COLOR,
            "nv20_diffuseAndSpecularColor.vp"
        )
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_ENVIRONMENT, "environment.vfp")
        progs[a++] =
            progDef_t(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_ENVIRONMENT, "environment.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_GLASSWARP, "arbVP_glasswarp.txt")
        progs[a++] =
            progDef_t(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_GLASSWARP, "arbFP_glasswarp.txt")

        // additional programs can be dynamically specified in materials
    }

    fun cg_error_callback() {
        throw TODO_Exception()
        //        CGerror i = cgGetError();
//        common.Printf("Cg error (%d): %s\n", i, cgGetErrorString(i));
    }

    /*
     ====================
     GL_SelectTextureNoClient
     ====================
     */
    fun GL_SelectTextureNoClient(unit: Int) {
        backEnd.glState.currenttmu = unit
        qglActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
        RB_LogComment("glActiveTextureARB( %d )\n", unit)
    }

    /*
     =============
     RB_ARB2_CreateDrawInteractions

     =============
     */
    fun RB_ARB2_CreateDrawInteractions(surf: drawSurf_s?) {
        var surf = surf
        if (NOT(surf)) {
            return
        }

        // perform setup here that will be constant for all interactions
        GL_State(GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE or GLS_DEPTHMASK or backEnd.depthFunc)

        // bind the vertex program
        if (RenderSystem_init.r_testARBProgram.GetBool()) {
            qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST)
            qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST)
        } else {
            qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION)
            qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION)
        }
        qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
        qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)

        // enable the vertex arrays
        qglEnableVertexAttribArrayARB(8)
        qglEnableVertexAttribArrayARB(9)
        qglEnableVertexAttribArrayARB(10)
        qglEnableVertexAttribArrayARB(11)
        qglEnableClientState(GL11.GL_COLOR_ARRAY)

        // texture 0 is the normalization cube map for the vector towards the light
        GL_SelectTextureNoClient(0)
        if (backEnd.vLight.lightShader!!.IsAmbientLight()) {
            Image.globalImages.ambientNormalMap.Bind()
        } else {
            Image.globalImages.normalCubeMapImage.Bind()
        }

        // texture 6 is the specular lookup table
        GL_SelectTextureNoClient(6)
        if (RenderSystem_init.r_testARBProgram.GetBool()) {
            Image.globalImages.specular2DTableImage.Bind() // variable specularity in alpha channel
        } else {
            Image.globalImages.specularTableImage.Bind()
        }
        while (surf != null) {

            // perform setup here that will not change over multiple interaction passes

            // set the vertex pointers
            val ac =
                idDrawVert(vertexCache.Position(surf.geo!!.ambientCache)) //TODO:figure out how to work these damn casts.
            //            qglColorPointer(4, GL_UNSIGNED_BYTE, 0/*sizeof(idDrawVert)*/, ac.colorOffset());
//            qglVertexAttribPointerARB(11, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.normalOffset());
//            qglVertexAttribPointerARB(10, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.tangentsOffset_1());
//            qglVertexAttribPointerARB(9, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.tangentsOffset_0());
//            qglVertexAttribPointerARB(8, 2, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.stOffset());
//            qglVertexPointer(3, GL_FLOAT, 0/*sizeof(idDrawVert)*/, ac.xyzOffset());
            qglColorPointer(4, GL11.GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
            qglVertexAttribPointerARB(11, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.normalOffset().toLong())
            qglVertexAttribPointerARB(10, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_1().toLong())
            qglVertexAttribPointerARB(9, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_0().toLong())
            qglVertexAttribPointerARB(8, 2, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.stOffset().toLong())
            qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())

            // this may cause RB_ARB2_DrawInteraction to be exacuted multiple
            // times with different colors and images if the surface or light have multiple layers
            RB_CreateSingleDrawInteractions(surf, RB_ARB2_DrawInteraction.INSTANCE)
            surf = surf.nextOnLight
        }
        qglDisableVertexAttribArrayARB(8)
        qglDisableVertexAttribArrayARB(9)
        qglDisableVertexAttribArrayARB(10)
        qglDisableVertexAttribArrayARB(11)
        qglDisableClientState(GL11.GL_COLOR_ARRAY)

        // disable features
        GL_SelectTextureNoClient(6)
        Image.globalImages.BindNull()
        GL_SelectTextureNoClient(5)
        Image.globalImages.BindNull()
        GL_SelectTextureNoClient(4)
        Image.globalImages.BindNull()
        GL_SelectTextureNoClient(3)
        Image.globalImages.BindNull()
        GL_SelectTextureNoClient(2)
        Image.globalImages.BindNull()
        GL_SelectTextureNoClient(1)
        Image.globalImages.BindNull()
        backEnd.glState.currenttmu = -1
        GL_SelectTexture(0)
        qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
        qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
    }

    /*
     ==================
     RB_ARB2_DrawInteractions
     ==================
     */
    fun RB_ARB2_DrawInteractions() {
        var vLight: viewLight_s?
        var lightShader: idMaterial?
        GL_SelectTexture(0)
        qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        //
        // for each light, perform adding and shadowing
        //
        vLight = backEnd.viewDef!!.viewLights
        while (vLight != null) {
            backEnd.vLight = vLight

            // do fogging later
            if (vLight.lightShader!!.IsFogLight()) {
                vLight = vLight.next
                continue
            }
            if (vLight.lightShader!!.IsBlendLight()) {
                vLight = vLight.next
                continue
            }
            if (NOT(vLight.localInteractions[0]) && NOT(vLight.globalInteractions[0])
                && NOT(vLight.translucentInteractions[0])
            ) {
                vLight = vLight.next
                continue
            }
            lightShader = vLight.lightShader

            // clear the stencil buffer if needed
            if (vLight.globalShadows[0] != null || vLight.localShadows[0] != null) {
                backEnd.currentScissor = idScreenRect(vLight.scissorRect)
                if (RenderSystem_init.r_useScissor.GetBool()) {
                    qglScissor(
                        backEnd.viewDef!!.viewport.x1 + backEnd.currentScissor.x1,
                        backEnd.viewDef!!.viewport.y1 + backEnd.currentScissor.y1,
                        backEnd.currentScissor.x2 + 1 - backEnd.currentScissor.x1,
                        backEnd.currentScissor.y2 + 1 - backEnd.currentScissor.y1
                    )
                }
                qglClear(GL11.GL_STENCIL_BUFFER_BIT)
            } else {
                // no shadows, so no need to read or write the stencil buffer
                // we might in theory want to use GL_ALWAYS instead of disabling
                // completely, to satisfy the invarience rules
                qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
            }
            if (RenderSystem_init.r_useShadowVertexProgram.GetBool()) {
                qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                RB_StencilShadowPass(vLight.globalShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                RB_StencilShadowPass(vLight.localShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
                qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB) // if there weren't any globalInteractions, it would have stayed on
            } else {
                RB_StencilShadowPass(vLight.globalShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                RB_StencilShadowPass(vLight.localShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
            }

            // translucent surfaces never get stencil shadowed
            if (RenderSystem_init.r_skipTranslucent.GetBool()) {
                vLight = vLight.next
                continue
            }
            qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
            backEnd.depthFunc = GLS_DEPTHFUNC_LESS
            RB_ARB2_CreateDrawInteractions(vLight.translucentInteractions[0])
            backEnd.depthFunc = GLS_DEPTHFUNC_EQUAL
            vLight = vLight.next
        }

        // disable stencil shadow test
        qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
        GL_SelectTexture(0)
        qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
    }

    /*
     =================
     R_LoadARBProgram
     =================
     */
    fun R_LoadARBProgram(progIndex: Int) {
        val ofs = BufferUtils.createIntBuffer(16)
        val err: Int
        val fullPath = idStr("glprogs/" + progs[progIndex]!!.name)
        val fileBuffer = arrayOf<ByteBuffer?>(null)
        var buffer: String
        var start = 0
        val end: Int
        Common.common.Printf("%s", fullPath)

        // load the program even if we don't support it, so
        // fs_copyfiles can generate cross-platform data dumps
        fileSystem.ReadFile(fullPath.toString(),  /*(void **)&*/fileBuffer, null)
        if (NOT(fileBuffer[0])) {
            Common.common.Printf(": File not found\n")
            return
        }

        // copy to stack memory and free
//        buffer = /*(char *)*/ _alloca(strlen(fileBuffer) + 1);
        buffer = String(fileBuffer[0]!!.array())
        //        fileSystem.FreeFile(fileBuffer);
        if (!glConfig.isInitialized) {
            return
        }

        //
        // submit the program string at start to GL
        //
        if (progs[progIndex]!!.ident == program_t.PROG_INVALID.ordinal) {
            // allocate a new identifier for this program
            progs[progIndex]!!.ident = program_t.PROG_USER.ordinal + progIndex
        }

        // vertex and fragment programs can both be present in a single file, so
        // scan for the proper header to be the start point, and stamp a 0 in after the end
        if (progs[progIndex]!!.target == ARBVertexProgram.GL_VERTEX_PROGRAM_ARB) {
            if (!glConfig.ARBVertexProgramAvailable) {
                Common.common.Printf(": GL_VERTEX_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBvp")
        }
        if (progs[progIndex]!!.target == ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB) {
            if (!glConfig.ARBFragmentProgramAvailable) {
                Common.common.Printf(": GL_FRAGMENT_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBfp")
        }
        if (-1 == start) {
            Common.common.Printf(": !!ARB not found\n")
            return
        }
        end = start + buffer.substring(start).indexOf("END")
        if (-1 == end) {
            Common.common.Printf(": END not found\n")
            return
        }
        buffer = buffer.substring(start, end + 3) //end[3] = 0;
        val substring = BufferUtils.createByteBuffer(buffer.length)
        substring.put(buffer.toByteArray()).flip()
        qglBindProgramARB(progs[progIndex]!!.target, progs[progIndex]!!.ident)
        qglGetError()
        qglProgramStringARB(
            progs[progIndex]!!.target,
            ARBVertexProgram.GL_PROGRAM_FORMAT_ASCII_ARB,
            0,  /*(unsigned char *)*/
            substring
        )
        err = qglGetError()
        qglGetIntegerv(ARBVertexProgram.GL_PROGRAM_ERROR_POSITION_ARB, ofs)
        if (err == GL11.GL_INVALID_OPERATION) {
            val   /*GLubyte*/str: String = qglGetString(ARBVertexProgram.GL_PROGRAM_ERROR_STRING_ARB)
            Common.common.Printf("\nGL_PROGRAM_ERROR_STRING_ARB: %s\n", str)
            if (ofs[0] < 0) {
                Common.common.Printf("GL_PROGRAM_ERROR_POSITION_ARB < 0 with error\n")
            } else if (ofs[0] >= buffer.length - start) {
                Common.common.Printf("error at end of program\n")
            } else {
                Common.common.Printf("error at %d:\n%s", ofs[0], start + ofs[0])
            }
            return
        }
        if (ofs[0] != -1) {
            Common.common.Printf("\nGL_PROGRAM_ERROR_POSITION_ARB != -1 without error\n")
            return
        }
        Common.common.Printf("\n")
    }

    /*
     ==================
     R_FindARBProgram

     Returns a GL identifier that can be bound to the given target, parsing
     a text file if it hasn't already been loaded.
     ==================
     */
    fun R_FindARBProgram( /*GLenum */
                          target: Int, program: String
    ): Int {
        var i: Int
        val stripped = idStr(program)
        stripped.StripFileExtension()

        // see if it is already loaded
        i = 0
        while (progs[i] != null && isNotNullOrEmpty(progs[i]!!.name)) {
            if (progs[i]!!.target != target) {
                i++
                continue
            }
            val compare = idStr(progs[i]!!.name)
            compare.StripFileExtension()
            if (NOT(idStr.Icmp(stripped, compare))) {
                return progs[i]!!.ident
            }
            i++
        }
        if (i == MAX_GLPROGS) {
            Common.common.Error("R_FindARBProgram: MAX_GLPROGS")
        }

        // add it to the list and load it
        progs[i] = progDef_t(target, program_t.PROG_INVALID, program) // will be gen'd by R_LoadARBProgram
        R_LoadARBProgram(i)
        return progs[i]!!.ident
    }

    /*
     ==================
     R_ARB2_Init

     ==================
     */
    fun R_ARB2_Init() {
        glConfig.allowARB2Path = false
        Common.common.Printf("---------- R_ARB2_Init ----------\n")
        if (!glConfig.ARBVertexProgramAvailable || !glConfig.ARBFragmentProgramAvailable) {
            Common.common.Printf("Not available.\n")
            return
        }
        Common.common.Printf("Available.\n")
        Common.common.Printf("---------------------------------\n")
        glConfig.allowARB2Path = true
    }

    /*
     ==================
     RB_ARB2_DrawInteraction
     ==================
     */
    internal class RB_ARB2_DrawInteraction private constructor() : DrawInteraction() {
        override fun run(din: drawInteraction_t) {
            DBG_RB_ARB2_DrawInteraction++
            // load all the vertex program parameters
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_ORIGIN,
                din.localLightOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_VIEW_ORIGIN,
                din.localViewOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_S,
                din.lightProjection[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_T,
                din.lightProjection[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_Q,
                din.lightProjection[2].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_FALLOFF_S,
                din.lightProjection[3].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_BUMP_MATRIX_S,
                din.bumpMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_BUMP_MATRIX_T,
                din.bumpMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_DIFFUSE_MATRIX_S,
                din.diffuseMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_DIFFUSE_MATRIX_T,
                din.diffuseMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_SPECULAR_MATRIX_S,
                din.specularMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_SPECULAR_MATRIX_T,
                din.specularMatrix[1].ToFloatPtr()
            )

            // testing fragment based normal mapping
            if (RenderSystem_init.r_testARBProgram.GetBool()) {
                qglProgramEnvParameter4fvARB(
                    ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                    2,
                    din.localLightOrigin.ToFloatPtr()
                )
                qglProgramEnvParameter4fvARB(
                    ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB,
                    3,
                    din.localViewOrigin.ToFloatPtr()
                )
            }
            when (din.vertexColor) {
                stageVertexColor_t.SVC_IGNORE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        ZERO
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ONE
                    )
                }

                stageVertexColor_t.SVC_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ZERO
                    )
                }

                stageVertexColor_t.SVC_INVERSE_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        NEG_ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ONE
                    )
                }
            }

            // set the constant colors
            qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 0, din.diffuseColor.ToFloatPtr())
            qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 1, din.specularColor.ToFloatPtr())

            // set the textures
            // texture 1 will be the per-surface bump map
            GL_SelectTextureNoClient(1)
            din.bumpImage!!.Bind()

            // texture 2 will be the light falloff texture
            GL_SelectTextureNoClient(2)
            din.lightFalloffImage!!.Bind()

            // texture 3 will be the light projection texture
            GL_SelectTextureNoClient(3)
            din.lightImage!!.Bind()

            // texture 4 is the per-surface diffuse map
            GL_SelectTextureNoClient(4)
            din.diffuseImage!!.Bind()

            // texture 5 is the per-surface specular map
            GL_SelectTextureNoClient(5)
            din.specularImage!!.Bind()

            // draw it
            RB_DrawElementsWithCounters(din.surf!!.geo!!)
        }

        companion object {
            val INSTANCE: DrawInteraction = RB_ARB2_DrawInteraction()
            private var DBG_RB_ARB2_DrawInteraction = 0
        }
    }

    //===================================================================================
    class progDef_t(
        var target: Int, var ident: Int, // char			name[64];
        var name: String
    ) {
        constructor(target: Int, ident: program_t, name: String) : this(target, ident.ordinal, name)
    }

    /*
     ==================
     R_ReloadARBPrograms_f
     ==================
     */
    class R_ReloadARBPrograms_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs) {
            var i: Int
            Common.common.Printf("----- R_ReloadARBPrograms -----\n")
            i = 0
            while (progs[i] != null && isNotNullOrEmpty(progs[i]!!.name)) {
                R_LoadARBProgram(i)
                i++
            }
            Common.common.Printf("-------------------------------\n")
        }

        companion object {
            val instance: cmdFunction_t = R_ReloadARBPrograms_f()
        }
    }
}