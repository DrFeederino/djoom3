package neo.Renderer

import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.RenderSystem_init.Companion.r_brightness
import neo.Renderer.RenderSystem_init.Companion.r_gamma
import neo.Renderer.RenderSystem_init.Companion.r_gammaInShader
import neo.Renderer.qgl.qglDisableClientState
import neo.Renderer.qgl.qglProgramEnvParameter4fvARB
import neo.Renderer.tr_backend.GL_SelectTexture
import neo.Renderer.tr_local.backEnd
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.programParameter_t
import neo.Renderer.tr_local.program_t
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_render.DrawInteraction
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB
import org.lwjgl.opengl.ARBMultitexture.GL_TEXTURE0_ARB
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.ARBVertexProgram.GL_VERTEX_PROGRAM_ARB
import org.lwjgl.opengl.GL14
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
    private val NEG_ONE: FloatArray = floatArrayOf(-1f, -1f, -1f, -1f)
    private val ONE: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)
    private val ZERO: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)

    // a single file can have both a vertex program and a fragment program
    var progs: ArrayList<progDef_t> = ArrayList<progDef_t>(MAX_GLPROGS)
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
        qgl.qglActiveTextureARB(GL_TEXTURE0_ARB + unit)
        tr_backend.RB_LogComment("glActiveTextureARB( %d )\n", unit)
    }

    /*
     =============
     RB_ARB2_CreateDrawInteractions

     =============
     */
    fun RB_ARB2_CreateDrawInteractions(surf: drawSurf_s?) {
        var surf = surf
        if (null == surf) {
            return
        }

        // perform setup here that will be constant for all interactions
        tr_backend.GL_State(tr_local.GLS_SRCBLEND_ONE or tr_local.GLS_DSTBLEND_ONE or tr_local.GLS_DEPTHMASK or backEnd.depthFunc)

        // bind the vertex program
        if (RenderSystem_init.r_testARBProgram.GetBool()) {
            qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST)
            qgl.qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST)
        } else {
            qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION)
            qgl.qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION)
        }
        qgl.qglEnable(GL_VERTEX_PROGRAM_ARB)
        qgl.qglEnable(GL_FRAGMENT_PROGRAM_ARB)

        // enable the vertex arrays
        qgl.qglEnableVertexAttribArrayARB(8)
        qgl.qglEnableVertexAttribArrayARB(9)
        qgl.qglEnableVertexAttribArrayARB(10)
        qgl.qglEnableVertexAttribArrayARB(11)
        qgl.qglEnableClientState(GL14.GL_COLOR_ARRAY)

        // texture 0 is the normalization cube map for the vector towards the light
        GL_SelectTextureNoClient(0)
        if (backEnd.vLight.lightShader.IsAmbientLight()) {
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
                idDrawVert(VertexCache.vertexCache.Position(surf.geo.ambientCache)) //TODO:figure out how to work these damn casts.
            //            qglColorPointer(4, GL_UNSIGNED_BYTE, 0/*sizeof(idDrawVert)*/, ac.colorOffset());
//            qglVertexAttribPointerARB(11, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.normalOffset());
//            qglVertexAttribPointerARB(10, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.tangentsOffset_1());
//            qglVertexAttribPointerARB(9, 3, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.tangentsOffset_0());
//            qglVertexAttribPointerARB(8, 2, GL_FLOAT, false, 0/*sizeof(idDrawVert)*/, ac.stOffset());
//            qglVertexPointer(3, GL_FLOAT, 0/*sizeof(idDrawVert)*/, ac.xyzOffset());
            qgl.qglColorPointer(4, GL14.GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
            qgl.qglVertexAttribPointerARB(
                11, 3, GL14.GL_FLOAT, false, idDrawVert.BYTES, ac.normalOffset().toLong()
            )
            qgl.qglVertexAttribPointerARB(
                10, 3, GL14.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_1().toLong()
            )
            qgl.qglVertexAttribPointerARB(
                9, 3, GL14.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_0().toLong()
            )
            qgl.qglVertexAttribPointerARB(
                8, 2, GL14.GL_FLOAT, false, idDrawVert.BYTES, ac.stOffset().toLong()
            )
            qgl.qglVertexPointer(3, GL14.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())

            // this may cause RB_ARB2_DrawInteraction to be exacuted multiple
            // times with different colors and images if the surface or light have multiple layers
            tr_render.RB_CreateSingleDrawInteractions(surf, RB_ARB2_DrawInteraction.INSTANCE)
            surf = surf.nextOnLight
        }
        qgl.qglDisableVertexAttribArrayARB(8)
        qgl.qglDisableVertexAttribArrayARB(9)
        qgl.qglDisableVertexAttribArrayARB(10)
        qgl.qglDisableVertexAttribArrayARB(11)
        qglDisableClientState(GL14.GL_COLOR_ARRAY)

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

        qgl.qglDisable(GL_VERTEX_PROGRAM_ARB)
        qgl.qglDisable(GL_FRAGMENT_PROGRAM_ARB)
    }

    /*
     ==================
     RB_ARB2_DrawInteractions
     ==================
     */
    fun RB_ARB2_DrawInteractions() {
        var vLight: viewLight_s?

        GL_SelectTexture(0)
        qglDisableClientState(GL14.GL_TEXTURE_COORD_ARRAY)

        //
        // for each light, perform adding and shadowing
        //
        vLight = backEnd.viewDef!!.viewLights
        while (vLight != null) {
            backEnd.vLight = vLight

            // do fogging later
            if (vLight.lightShader.IsFogLight()) {
                vLight = vLight.next
                continue
            }
            if (vLight.lightShader.IsBlendLight()) {
                vLight = vLight.next
                continue
            }
            if (vLight.localInteractions[0] == null && vLight.globalInteractions[0] == null &&
                vLight.translucentInteractions[0] == null
            ) {
                vLight = vLight.next
                continue
            }

            // clear the stencil buffer if needed
            if (vLight.globalShadows[0] != null || vLight.localShadows[0] != null) {
                backEnd.currentScissor = vLight.scissorRect
                if (RenderSystem_init.r_useScissor.GetBool()) {
                    qgl.qglScissor(
                        backEnd.viewDef!!.viewport.x1 + backEnd.currentScissor.x1,
                        backEnd.viewDef!!.viewport.y1 + backEnd.currentScissor.y1,
                        backEnd.currentScissor.x2 + 1 - backEnd.currentScissor.x1,
                        backEnd.currentScissor.y2 + 1 - backEnd.currentScissor.y1
                    )
                }
                qgl.qglClear(GL14.GL_STENCIL_BUFFER_BIT)
            } else {
                // no shadows, so no need to read or write the stencil buffer
                // we might in theory want to use GL_ALWAYS instead of disabling
                // completely, to satisfy the invarience rules
                qgl.qglStencilFunc(GL14.GL_ALWAYS, 128, 255)
            }
            if (RenderSystem_init.r_useShadowVertexProgram.GetBool()) {
                qgl.qglEnable(GL_VERTEX_PROGRAM_ARB)
                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                draw_common.RB_StencilShadowPass(vLight.globalShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                qgl.qglEnable(GL_VERTEX_PROGRAM_ARB)
                qgl.qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                draw_common.RB_StencilShadowPass(vLight.localShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
                qgl.qglDisable(GL_VERTEX_PROGRAM_ARB) // if there weren't any globalInteractions, it would have stayed on
            } else {
                draw_common.RB_StencilShadowPass(vLight.globalShadows[0]!!)
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                draw_common.RB_StencilShadowPass(vLight.localShadows[0]!!)
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
            }

            // translucent surfaces never get stencil shadowed
            if (RenderSystem_init.r_skipTranslucent.GetBool()) {
                vLight = vLight.next
                continue
            }
            qgl.qglStencilFunc(GL14.GL_ALWAYS, 128, 255)

            backEnd.depthFunc = tr_local.GLS_DEPTHFUNC_LESS
            RB_ARB2_CreateDrawInteractions(vLight.translucentInteractions[0])
            backEnd.depthFunc = tr_local.GLS_DEPTHFUNC_EQUAL

            vLight = vLight.next
        }

        // disable stencil shadow test
        qgl.qglStencilFunc(GL14.GL_ALWAYS, 128, 255)

        GL_SelectTexture(0)
        qgl.qglEnableClientState(GL14.GL_TEXTURE_COORD_ARRAY)
    }

    /*
     =================
     R_LoadARBProgram
     =================
     */
    fun R_LoadARBProgram(progIndex: Int) {
        val ofs = BufferUtils.createIntBuffer(16)
        val err: Int
        val fullPath = idStr("glprogs/" + progs[progIndex].name)
        val fileBuffer = arrayOfNulls<ByteBuffer>(1)
        var buffer: String
        var start = 0
        var end: Int
        Common.common.Printf("%s", fullPath)

        // load the program even if we don't support it, so
        // fs_copyfiles can generate cross-platform data dumps
        idLib.fileSystem.ReadFile(fullPath.toString(),  /*(void **)&*/fileBuffer, null)
        if (fileBuffer[0] == null) {
            Common.common.Printf(": File not found\n")
            return
        }

        // copy to stack memory and free
//        buffer = /*(char *)*/ _alloca(strlen(fileBuffer) + 1);
        buffer = String(fileBuffer[0]!!.array())
        //        fileSystem.FreeFile(fileBuffer);
        fileBuffer[0] = null
        if (!tr_local.glConfig.isInitialized) {
            return
        }

        //
        // submit the program string at start to GL
        //
        if (progs[progIndex].ident == program_t.PROG_INVALID.ordinal) {
            // allocate a new identifier for this program
            progs[progIndex].ident = program_t.PROG_USER.ordinal + progIndex
        }

        // vertex and fragment programs can both be present in a single file, so
        // scan for the proper header to be the start point, and stamp a 0 in after the end
        if (progs[progIndex].target == GL_VERTEX_PROGRAM_ARB) {
            if (!tr_local.glConfig.ARBVertexProgramAvailable) {
                Common.common.Printf(": GL_VERTEX_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBvp")
        }
        if (progs[progIndex].target == GL_FRAGMENT_PROGRAM_ARB) {
            if (!tr_local.glConfig.ARBFragmentProgramAvailable) {
                Common.common.Printf(": GL_FRAGMENT_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBfp")
        }
        if (-1 == start) {
            Common.common.Printf(": !!ARB not found\n")
            return
        }
        end = buffer.indexOf("END", start)
        if (-1 == end) {
            Common.common.Printf(": END not found\n")
            return
        }
        buffer = buffer.substring(start, end + 3)
        if (r_gammaInShader.GetBool() && progs[progIndex].target == GL_FRAGMENT_PROGRAM_ARB) {
            // note that strlen("dhewm3tmpres") == strlen("result.color")
            val tmpres = "TEMP dhewm3tmpres; # injected by dhewm3 for gamma correction\n"

            // Note: program.env[4].xyz = r_brightness; program.env[4].w = 1.0/r_gamma
            // outColor.rgb = pow(dhewm3tmpres.rgb*r_brightness, vec3(1.0/r_gamma))
            // outColor.a = dhewm3tmpres.a;

            // MUL_SAT clamps the result to [0, 1] - it must not be negative because
            // POW might not work with a negative base (it looks wrong with intel's Linux driver)
            // and clamping values >1 to 1 is ok because when writing to result.color
            // it's clamped anyway and pow(base, exp) is always >= 1 for base >= 1
            // first multiply with brightness
            // then do pow(dhewm3tmpres.xyz, vec3(1/gamma))
            // (apparently POW only supports scalars, not whole vectors)
            // alpha remains unmodified
            val extraLines =
                "# gamma correction in shader, injected by dhewm3 \r\nMUL_SAT dhewm3tmpres.xyz, program.env[4], dhewm3tmpres;\r\nPOW result.color.x, dhewm3tmpres.x, program.env[4].w;\r\nPOW result.color.y, dhewm3tmpres.y, program.env[4].w;\r\nPOW result.color.z, dhewm3tmpres.z, program.env[4].w;\r\nMOV result.color.w, dhewm3tmpres.w;\r\n \r\nEND\r\n\r\n" // we add this block right at the end, replacing the original "END" string
            val fullLen = start + tmpres.length + extraLines.length
            val outStr = StringBuilder(fullLen)
            // add tmpres right after OPTION line (if any)
            start = buffer.indexOf("!!ARBfp1.0 \r\n") + "!!ARBfp1.0 \r\n".length
            end = buffer.indexOf("END", start) + 3
            var insertPos = buffer.indexOf("OPTION", start)

            if (insertPos == -1) {
                // no OPTION? then just put it after the first line (usually sth like "!!ARBfp1.0\n")
                insertPos = start
            }
            // but we want the position *after* that line
            while (buffer[insertPos] != '\n' && buffer[insertPos] != '\r') {
                ++insertPos;
            }
            // skip  the newline character(s) as well
            while (buffer[insertPos] == '\n' || buffer[insertPos] == '\r') {
                ++insertPos;
            }
            // copy text up to insertPos
            outStr.append(buffer.substring(0, insertPos))
            // copy tmpres ("TEMP dhewm3tmpres; # ..")
            outStr.append(tmpres)
            // copy remaining original shader up to (excluding) "END"
            outStr.append(buffer.substring(insertPos, end - 3))

            // Stupid replace creates a "new" string instead of replacing sequence of chars in the builder!
            val out = outStr.replace("result.color".toRegex(), "dhewm3tmpres")
            outStr.clear()
            outStr.append(out)

            outStr.append(extraLines)
            buffer = outStr.toString()
        }
        //end[3] = 0;
        val substring = BufferUtils.createByteBuffer(buffer.length)
        substring.put(buffer.toByteArray()).flip()

        qgl.qglBindProgramARB(progs[progIndex].target, progs[progIndex].ident)
        qgl.qglGetError()

        qgl.qglProgramStringARB(
            progs[progIndex].target, ARBVertexProgram.GL_PROGRAM_FORMAT_ASCII_ARB, 0,  /*(unsigned char *)*/
            substring
        )
        err = qgl.qglGetError()
        qgl.qglGetIntegerv(ARBVertexProgram.GL_PROGRAM_ERROR_POSITION_ARB, ofs)
        if (err == GL14.GL_INVALID_OPERATION) {
            val outputString = substring.asCharBuffer().toString()
            val   /*GLubyte*/str = qgl.qglGetString(ARBVertexProgram.GL_PROGRAM_ERROR_STRING_ARB)
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
        var i: Int = 0
        val stripped = idStr(program)
        stripped.StripFileExtension()

        // see if it is already loaded
        while (progs.getOrNull(i) != null && progs[i].name.isNotEmpty()) {
            if (progs[i].target != target) {
                i++
                continue
            }
            val compare = idStr(progs[i].name)
            compare.StripFileExtension()
            if (TempDump.NOT(idStr.Icmp(stripped, compare).toDouble())) {
                return progs[i].ident
            }
            i++
        }
        if (i == MAX_GLPROGS) {
            Common.common.Error("R_FindARBProgram: MAX_GLPROGS")
        }

        // add it to the list and load it
        progs.add(i, progDef_t(target, program_t.PROG_INVALID, program)) // will be gen'd by R_LoadARBProgram

        R_LoadARBProgram(i)

        return progs[i].ident
    }

    /*
     ==================
     R_ARB2_Init

     ==================
     */
    fun R_ARB2_Init() {
        tr_local.glConfig.allowARB2Path = false

        Common.common.Printf("ARB2 renderer: ")

        if (!tr_local.glConfig.ARBVertexProgramAvailable || !tr_local.glConfig.ARBFragmentProgramAvailable) {
            Common.common.Printf("Not available.\n")
            return
        }

        Common.common.Printf("Available.\n")

        tr_local.glConfig.allowARB2Path = true
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
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_LIGHT_ORIGIN, din.localLightOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_VIEW_ORIGIN, din.localViewOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_LIGHT_PROJECT_S, din.lightProjection[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_LIGHT_PROJECT_T, din.lightProjection[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_LIGHT_PROJECT_Q, din.lightProjection[2].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_LIGHT_FALLOFF_S, din.lightProjection[3].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_BUMP_MATRIX_S, din.bumpMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_BUMP_MATRIX_T, din.bumpMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_DIFFUSE_MATRIX_S, din.diffuseMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_DIFFUSE_MATRIX_T, din.diffuseMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_SPECULAR_MATRIX_S, din.specularMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_SPECULAR_MATRIX_T, din.specularMatrix[1].ToFloatPtr()
            )

            // testing fragment based normal mapping
            if (RenderSystem_init.r_testARBProgram.GetBool()) {
                qglProgramEnvParameter4fvARB(
                    GL_FRAGMENT_PROGRAM_ARB, 2, din.localLightOrigin.ToFloatPtr()
                )
                qglProgramEnvParameter4fvARB(
                    GL_FRAGMENT_PROGRAM_ARB, 3, din.localViewOrigin.ToFloatPtr()
                )
            }

            val NEG_ONE: FloatArray = floatArrayOf(-1f, -1f, -1f, -1f)
            val ONE: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)
            val ZERO: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)

            when (din.vertexColor) {
                stageVertexColor_t.SVC_IGNORE -> {
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_MODULATE, ZERO
                    )
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_ADD, ONE
                    )
                }

                stageVertexColor_t.SVC_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_MODULATE, ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_ADD, ZERO
                    )
                }

                stageVertexColor_t.SVC_INVERSE_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_MODULATE, NEG_ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        GL_VERTEX_PROGRAM_ARB, programParameter_t.PP_COLOR_ADD, ONE
                    )
                }
            }

            // set the constant colors
            qglProgramEnvParameter4fvARB(
                GL_FRAGMENT_PROGRAM_ARB, 0, din.diffuseColor.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_FRAGMENT_PROGRAM_ARB, 1, din.specularColor.ToFloatPtr()
            )

            // DG: brightness and gamma in shader as program.env[4]
            if (r_gammaInShader.GetBool()) {
                // program.env[4].xyz are all r_brightness, program.env[4].w is 1.0/r_gamma
                val parm = FloatArray(4)
                parm[0] = r_brightness.GetFloat()
                parm[1] = parm[0]
                parm[1] = parm[2]
                parm[3] = 1.0f / r_gamma.GetFloat() // 1.0/gamma so the shader doesn't have to do this calculation
                qglProgramEnvParameter4fvARB(GL_FRAGMENT_PROGRAM_ARB, 4, parm)
            }


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
            tr_render.RB_DrawElementsWithCounters(din.surf!!.geo)
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
            Common.common.Printf("----- R_ReloadARBPrograms -----\n")
            progs.forEachIndexed { index, progdefT -> R_LoadARBProgram(index) }
        }

        companion object {
            private val instance: cmdFunction_t = R_ReloadARBPrograms_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    var a = 0

    //        
    init {

        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST, "test.vfp")
        )
        progs.add(
            a++, progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST, "test.vfp")
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION, "interaction.vfp")
        )
        progs.add(
            a++, progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION, "interaction.vfp")
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_BUMPY_ENVIRONMENT, "bumpyEnvironment.vfp")
        )
        progs.add(
            a++, progDef_t(
                GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_BUMPY_ENVIRONMENT, "bumpyEnvironment.vfp"
            )
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_AMBIENT, "ambientLight.vfp")
        )
        progs.add(
            a++, progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_AMBIENT, "ambientLight.vfp")
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW, "shadow.vp")
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_R200_INTERACTION, "R200_interaction.vp")
        )
        progs.add(
            a++, progDef_t(
                GL_VERTEX_PROGRAM_ARB, program_t.VPROG_NV20_BUMP_AND_LIGHT, "nv20_bumpAndLight.vp"
            )
        )
        progs.add(
            a++, progDef_t(
                GL_VERTEX_PROGRAM_ARB, program_t.VPROG_NV20_DIFFUSE_COLOR, "nv20_diffuseColor.vp"
            )
        )
        progs.add(
            a++, progDef_t(
                GL_VERTEX_PROGRAM_ARB, program_t.VPROG_NV20_SPECULAR_COLOR, "nv20_specularColor.vp"
            )
        )
        progs.add(
            a++, progDef_t(
                GL_VERTEX_PROGRAM_ARB,
                program_t.VPROG_NV20_DIFFUSE_AND_SPECULAR_COLOR,
                "nv20_diffuseAndSpecularColor.vp"
            )
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_ENVIRONMENT, "environment.vfp")
        )
        progs.add(
            a++, progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_ENVIRONMENT, "environment.vfp")
        )
        progs.add(
            a++, progDef_t(GL_VERTEX_PROGRAM_ARB, program_t.VPROG_GLASSWARP, "arbVP_glasswarp.txt")
        )
        progs.add(
            a++, progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_GLASSWARP, "arbFP_glasswarp.txt")
        )

        // additional programs can be dynamically specified in materials
    }
}