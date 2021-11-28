package neo.Renderer

import neo.Renderer.*
import neo.Renderer.Material.cullType_t
import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.backEndName_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.programParameter_t
import neo.Renderer.tr_local.program_t
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_render.RB_T_RenderTriangleSurface
import neo.Renderer.tr_render.triFunc
import neo.TempDump
import neo.framework.Common
import neo.idlib.containers.CInt
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.util.*

/**
 *
 */
object draw_common {
    /*
     =====================
     RB_BakeTextureMatrixIntoTexgen
     =====================
     */
    //========================================================================
    private val fogPlanes: Array<idPlane?>? = idPlane.Companion.generateArray(4)

    /*
     ================
     RB_FinishStageTexturing
     ================
     */
    private const val DBG_RB_FinishStageTexturing = 0

    /*
     ==================
     RB_STD_T_RenderShaderPasses

     This is also called for the generated 2D rendering
     ==================
     */
    private const val DBG_RB_STD_T_RenderShaderPasses = 0
    private const val DBG_hasMatrix = 0
    fun RB_BakeTextureMatrixIntoTexgen(   /*idPlane[]*/lightProject: Array<idVec4?>? /*[3]*/,
                                                       textureMatrix: FloatArray?
    ) {
        val genMatrix = FloatArray(16)
        val finale = FloatArray(16)
        genMatrix[0] = lightProject.get(0).oGet(0)
        genMatrix[4] = lightProject.get(0).oGet(1)
        genMatrix[8] = lightProject.get(0).oGet(2)
        genMatrix[12] = lightProject.get(0).oGet(3)
        genMatrix[1] = lightProject.get(1).oGet(0)
        genMatrix[5] = lightProject.get(1).oGet(1)
        genMatrix[9] = lightProject.get(1).oGet(2)
        genMatrix[13] = lightProject.get(1).oGet(3)
        genMatrix[2] = 0
        genMatrix[6] = 0
        genMatrix[10] = 0
        genMatrix[14] = 0
        genMatrix[3] = lightProject.get(2).oGet(0)
        genMatrix[7] = lightProject.get(2).oGet(1)
        genMatrix[11] = lightProject.get(2).oGet(2)
        genMatrix[15] = lightProject.get(2).oGet(3)
        tr_main.myGlMultMatrix(genMatrix, tr_local.backEnd.lightTextureMatrix, finale)
        lightProject.get(0).oSet(0, finale[0])
        lightProject.get(0).oSet(1, finale[4])
        lightProject.get(0).oSet(2, finale[8])
        lightProject.get(0).oSet(3, finale[12])
        lightProject.get(1).oSet(0, finale[1])
        lightProject.get(1).oSet(1, finale[5])
        lightProject.get(1).oSet(2, finale[9])
        lightProject.get(1).oSet(3, finale[13])
    }

    /*
     ================
     RB_PrepareStageTexturing
     ================
     */
    fun RB_PrepareStageTexturing(pStage: shaderStage_t?, surf: drawSurf_s?, ac: idDrawVert?) {
        // set privatePolygonOffset if necessary
        if (pStage.privatePolygonOffset != 0f) {
            qgl.qglEnable(GL11.GL_POLYGON_OFFSET_FILL)
            qgl.qglPolygonOffset(
                RenderSystem_init.r_offsetFactor.GetFloat(),
                RenderSystem_init.r_offsetUnits.GetFloat() * pStage.privatePolygonOffset
            )
        }

        // set the texture matrix if needed
        if (pStage.texture.hasMatrix) {
            tr_render.RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture)
        }

        // texgens
        if (pStage.texture.texgen == texgen_t.TG_DIFFUSE_CUBE) {
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.normalOffset().toLong())
        }
        if (pStage.texture.texgen == texgen_t.TG_SKYBOX_CUBE || pStage.texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, 0, VertexCache.vertexCache.Position(surf.dynamicTexCoords))
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN) {
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
            val mat = FloatArray(16)
            val plane = FloatArray(4)
            tr_main.myGlMultMatrix(surf.space.modelViewMatrix, tr_local.backEnd.viewDef.projectionMatrix, mat)
            plane[0] = mat[0]
            plane[1] = mat[4]
            plane[2] = mat[8]
            plane[3] = mat[12]
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, plane)
            plane[0] = mat[1]
            plane[1] = mat[5]
            plane[2] = mat[9]
            plane[3] = mat[13]
            qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane)
            plane[0] = mat[3]
            plane[1] = mat[7]
            plane[2] = mat[11]
            plane[3] = mat[15]
            qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, plane)
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN2) {
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
            val mat = FloatArray(16)
            val plane = FloatArray(4)
            tr_main.myGlMultMatrix(surf.space.modelViewMatrix, tr_local.backEnd.viewDef.projectionMatrix, mat)
            plane[0] = mat[0]
            plane[1] = mat[4]
            plane[2] = mat[8]
            plane[3] = mat[12]
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, plane)
            plane[0] = mat[1]
            plane[1] = mat[5]
            plane[2] = mat[9]
            plane[3] = mat[13]
            qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane)
            plane[0] = mat[3]
            plane[1] = mat[7]
            plane[2] = mat[11]
            plane[3] = mat[15]
            qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, plane)
        }
        if (pStage.texture.texgen == texgen_t.TG_GLASSWARP) {
            if (tr_local.tr.backEndRenderer == backEndName_t.BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_GLASSWARP)
                qgl.qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                tr_backend.GL_SelectTexture(2)
                Image.globalImages.scratchImage.Bind()
                tr_backend.GL_SelectTexture(1)
                Image.globalImages.scratchImage2.Bind()
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
                val mat = FloatArray(16)
                val plane = FloatArray(4)
                tr_main.myGlMultMatrix(surf.space.modelViewMatrix, tr_local.backEnd.viewDef.projectionMatrix, mat)
                plane[0] = mat[0]
                plane[1] = mat[4]
                plane[2] = mat[8]
                plane[3] = mat[12]
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, plane)
                plane[0] = mat[1]
                plane[1] = mat[5]
                plane[2] = mat[9]
                plane[3] = mat[13]
                qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane)
                plane[0] = mat[3]
                plane[1] = mat[7]
                plane[2] = mat[11]
                plane[3] = mat[15]
                qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, plane)
                tr_backend.GL_SelectTexture(0)
            }
        }
        if (pStage.texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            if (tr_local.tr.backEndRenderer == backEndName_t.BE_ARB2) {
                // see if there is also a bump map specified
                val bumpStage = surf.material.GetBumpStage()
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    tr_backend.GL_SelectTexture(1)
                    bumpStage.texture.image[0].Bind()
                    tr_backend.GL_SelectTexture(0)
                    qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.normalOffset().toLong())
                    qgl.qglVertexAttribPointerARB(
                        10,
                        3,
                        GL11.GL_FLOAT,
                        false,
                        idDrawVert.Companion.BYTES,
                        ac.tangentsOffset_1().toLong()
                    )
                    qgl.qglVertexAttribPointerARB(
                        9,
                        3,
                        GL11.GL_FLOAT,
                        false,
                        idDrawVert.Companion.BYTES,
                        ac.tangentsOffset_0().toLong()
                    )
                    qgl.qglEnableVertexAttribArrayARB(9)
                    qgl.qglEnableVertexAttribArrayARB(10)
                    qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)

                    // Program env 5, 6, 7, 8 have been set in RB_SetProgramEnvironmentSpace
                    qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_BUMPY_ENVIRONMENT)
                    qgl.qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                    qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_BUMPY_ENVIRONMENT)
                    qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                } else {
                    // per-pixel reflection mapping without a normal map
                    qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.normalOffset().toLong())
                    qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)
                    qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_ENVIRONMENT)
                    qgl.qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                    qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_ENVIRONMENT)
                    qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                }
            } else {
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_R)
                qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
                qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)
                qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.normalOffset().toLong())
                qgl.qglMatrixMode(GL11.GL_TEXTURE)
                val mat = FloatArray(16)
                tr_main.R_TransposeGLMatrix(tr_local.backEnd.viewDef.worldSpace.modelViewMatrix, mat)
                qgl.qglLoadMatrixf(mat)
                qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            }
        }
    }

    fun RB_FinishStageTexturing(pStage: shaderStage_t?, surf: drawSurf_s?, ac: idDrawVert?) {
        // unset privatePolygonOffset if necessary
        if (pStage.privatePolygonOffset != 0f && !surf.material.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qgl.qglDisable(GL11.GL_POLYGON_OFFSET_FILL)
        }
        if (pStage.texture.texgen == texgen_t.TG_DIFFUSE_CUBE || pStage.texture.texgen == texgen_t.TG_SKYBOX_CUBE || pStage.texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.stOffset().toLong())
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN) {
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
        }
        if (pStage.texture.texgen == texgen_t.TG_SCREEN2) {
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
        }
        if (pStage.texture.texgen == texgen_t.TG_GLASSWARP) {
            if (tr_local.tr.backEndRenderer == backEndName_t.BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                tr_backend.GL_SelectTexture(2)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(1)
                if (pStage.texture.hasMatrix) {
                    tr_render.RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture)
                }
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
                qgl.qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(0)
            }
        }
        if (pStage.texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            if (tr_local.tr.backEndRenderer == backEndName_t.BE_ARB2) {
                // see if there is also a bump map specified
                val bumpStage = surf.material.GetBumpStage()
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    tr_backend.GL_SelectTexture(1)
                    Image.globalImages.BindNull()
                    tr_backend.GL_SelectTexture(0)
                    qgl.qglDisableVertexAttribArrayARB(9)
                    qgl.qglDisableVertexAttribArrayARB(10)
                } else {
                    // per-pixel reflection mapping without bump mapping
                }
                qgl.qglDisableClientState(GL11.GL_NORMAL_ARRAY)
                qgl.qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                qgl.qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 0)
            } else {
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_R)
                qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
                qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
                qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR.toFloat())
                qgl.qglDisableClientState(GL11.GL_NORMAL_ARRAY)
                qgl.qglMatrixMode(GL11.GL_TEXTURE)
                qgl.qglLoadIdentity()
                qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            }
        }
        if (pStage.texture.hasMatrix) {
//            DBG_hasMatrix++;
//            System.out.println(DBG_RB_FinishStageTexturing + "---" + DBG_hasMatrix);
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            qgl.qglLoadIdentity()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            val qglGetError = qgl.qglGetError()
            if (qglGetError != 0) {
                System.err.println(String.format("GL Error code: %d", qglGetError))
            }
        }
    }

    /*
     =============================================================================================

     SHADER PASSES

     =============================================================================================
     */
    /*
     =====================
     RB_STD_FillDepthBuffer

     If we are rendering a subview with a near clip plane, use a second texture
     to force the alpha test to fail when behind that clip plane
     =====================
     */
    fun RB_STD_FillDepthBuffer(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int) {
        // if we are just doing 2D rendering, no need to fill the depth buffer
        if (TempDump.NOT(tr_local.backEnd.viewDef.viewEntitys)) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_STD_FillDepthBuffer ----------\n")

        // enable the second texture for mirror plane clipping if needed
        if (tr_local.backEnd.viewDef.numClipPlanes != 0) {
            tr_backend.GL_SelectTexture(1)
            Image.globalImages.alphaNotchImage.Bind()
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglTexCoord2f(1f, 0.5f)
        }

        // the first texture will be used for alpha tested surfaces
        tr_backend.GL_SelectTexture(0)
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        // decal surfaces may enable polygon offset
        qgl.qglPolygonOffset(RenderSystem_init.r_offsetFactor.GetFloat(), RenderSystem_init.r_offsetUnits.GetFloat())
        tr_backend.GL_State(tr_local.GLS_DEPTHFUNC_LESS)

        // Enable stencil test if we are going to be using it for shadows.
        // If we didn't do this, it would be legal behavior to get z fighting
        // from the ambient pass and the light passes.
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 1, 255)
        tr_render.RB_RenderDrawSurfListWithFunction(drawSurfs, numDrawSurfs, RB_T_FillDepthBuffer.INSTANCE)
        if (tr_local.backEnd.viewDef.numClipPlanes != 0) {
            tr_backend.GL_SelectTexture(1)
            Image.globalImages.BindNull()
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            tr_backend.GL_SelectTexture(0)
        }
    }

    /*
     ==================
     RB_SetProgramEnvironment

     Sets variables that can be used by all vertex programs
     ==================
     */
    fun RB_SetProgramEnvironment() {
        val parm = BufferUtils.createFloatBuffer(4)
        var pot: Int
        if (!tr_local.glConfig.ARBVertexProgramAvailable) {
            return
        }

//if (false){
//	// screen power of two correction factor, one pixel in so we don't get a bilerp
//	// of an uncopied pixel
//	int	 w = backEnd.viewDef.viewport.x2 - backEnd.viewDef.viewport.x1 + 1;
//	pot = globalImages.currentRenderImage.uploadWidth;
//	if ( w == pot ) {
//		parm0[0] = 1.0f;
//	} else {
//		parm0[0] = (float)(w-1) / pot;
//	}
//
//	int	 h = backEnd.viewDef.viewport.y2 - backEnd.viewDef.viewport.y1 + 1;
//	pot = globalImages.currentRenderImage.uploadHeight;
//	if ( h == pot ) {
//		parm0[1] = 1.0;
//	} else {
//		parm0[1] = (float)(h-1) / pot;
//	}
//
//	parm0[2] = 0;
//	parm0[3] = 1;
//	qglProgramEnvParameter4fvARB( GL_VERTEX_PROGRAM_ARB, 0, parm0 );
//}else{
        // screen power of two correction factor, assuming the copy to _currentRender
        // also copied an extra row and column for the bilerp
        val w = tr_local.backEnd.viewDef.viewport.x2 - tr_local.backEnd.viewDef.viewport.x1 + 1
        pot = Image.globalImages.currentRenderImage.uploadWidth.getVal()
        parm.put(0, w.toFloat() / pot)
        val h = tr_local.backEnd.viewDef.viewport.y2 - tr_local.backEnd.viewDef.viewport.y1 + 1
        pot = Image.globalImages.currentRenderImage.uploadHeight.getVal()
        parm.put(1, h.toFloat() / pot)
        parm.put(2, 0f)
        parm.put(3, 1f)
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 0, parm)
        //}
        qgl.qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 0, parm)

        // window coord to 0.0 to 1.0 conversion
        parm.put(0, 1.0f / w)
        parm.put(1, 1.0f / h)
        parm.put(2, 0f)
        parm.put(3, 1f)
        qgl.qglProgramEnvParameter4fvARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, 1, parm)

        //
        // set eye position in global space
        //
        parm.put(0, tr_local.backEnd.viewDef.renderView.vieworg.oGet(0))
        parm.put(1, tr_local.backEnd.viewDef.renderView.vieworg.oGet(1))
        parm.put(2, tr_local.backEnd.viewDef.renderView.vieworg.oGet(2))
        parm.put(3, 1f)
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 1, parm)
    }

    /*
     ==================
     RB_SetProgramEnvironmentSpace

     Sets variables related to the current space that can be used by all vertex programs
     ==================
     */
    fun RB_SetProgramEnvironmentSpace() {
        if (!tr_local.glConfig.ARBVertexProgramAvailable) {
            return
        }
        val space = tr_local.backEnd.currentSpace
        val parm = BufferUtils.createFloatBuffer(4)

        // set eye position in local space
        tr_main.R_GlobalPointToLocal(space.modelMatrix, tr_local.backEnd.viewDef.renderView.vieworg, parm)
        parm.put(3, 1.0f)
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 5, parm)

        // we need the model matrix without it being combined with the view matrix
        // so we can transform local vectors to global coordinates
        parm.put(0, space.modelMatrix[0])
        parm.put(1, space.modelMatrix[4])
        parm.put(2, space.modelMatrix[8])
        parm.put(3, space.modelMatrix[12])
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 6, parm)
        parm.put(0, space.modelMatrix[1])
        parm.put(1, space.modelMatrix[5])
        parm.put(2, space.modelMatrix[9])
        parm.put(3, space.modelMatrix[13])
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 7, parm)
        parm.put(0, space.modelMatrix[2])
        parm.put(1, space.modelMatrix[6])
        parm.put(2, space.modelMatrix[10])
        parm.put(3, space.modelMatrix[14])
        qgl.qglProgramEnvParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 8, parm)
    }

    fun RB_STD_T_RenderShaderPasses(surf: drawSurf_s?) {
        var stage: Int
        draw_common.DBG_RB_STD_T_RenderShaderPasses++
        val shader: idMaterial?
        var pStage: shaderStage_t
        val regs: FloatArray?
        val color = BufferUtils.createFloatBuffer(4)
        val tri: srfTriangles_s?
        tri = surf.geo
        shader = surf.material
        if (!shader.HasAmbient()) {
            return
        }
        if (shader.IsPortalSky()) {
            return
        }

        // change the matrix if needed
        if (surf.space !== tr_local.backEnd.currentSpace) {
            qgl.qglLoadMatrixf(surf.space.modelViewMatrix)
            tr_local.backEnd.currentSpace = surf.space
            draw_common.RB_SetProgramEnvironmentSpace()
        }

        // change the scissor if needed
        if (RenderSystem_init.r_useScissor.GetBool() && !tr_local.backEnd.currentScissor.Equals(surf.scissorRect)) {
            tr_local.backEnd.currentScissor = surf.scissorRect
            qgl.qglScissor(
                tr_local.backEnd.viewDef.viewport.x1 + tr_local.backEnd.currentScissor.x1,
                tr_local.backEnd.viewDef.viewport.y1 + tr_local.backEnd.currentScissor.y1,
                tr_local.backEnd.currentScissor.x2 + 1 - tr_local.backEnd.currentScissor.x1,
                tr_local.backEnd.currentScissor.y2 + 1 - tr_local.backEnd.currentScissor.y1
            )
        }

        // some deforms may disable themselves by setting numIndexes = 0
        if (0 == tri.numIndexes) {
            return
        }
        if (TempDump.NOT(tri.ambientCache)) {
            Common.common.Printf("RB_T_RenderShaderPasses: !tri.ambientCache\n")
            return
        }

        // get the expressions for conditionals / color / texcoords
        regs = surf.shaderRegisters

        // set face culling appropriately
        tr_backend.GL_Cull(shader.GetCullType())

        // set polygon offset if necessary
        if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qgl.qglEnable(GL11.GL_POLYGON_OFFSET_FILL)
            qgl.qglPolygonOffset(
                RenderSystem_init.r_offsetFactor.GetFloat(),
                RenderSystem_init.r_offsetUnits.GetFloat() * shader.GetPolygonOffset()
            )
        }
        if (surf.space.weaponDepthHack) {
            tr_render.RB_EnterWeaponDepthHack()
        }
        if (surf.space.modelDepthHack != 0.0f) {
            tr_render.RB_EnterModelDepthHack(surf.space.modelDepthHack)
        }
        val ac =
            idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts. EDIT:easy peasy.
        qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.xyzOffset().toLong())
        qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.stOffset().toLong())
        stage = 0
        while (stage < shader.GetNumStages()) {
            if (stage == 2 || stage == 3) {
//                System.out.printf("RB_STD_T_RenderShaderPasses(%d)\n", DBG_RB_STD_T_RenderShaderPasses++);
//                continue;//HACKME::4:our blending doesn't seem to work properly.
            }
            pStage = shader.GetStage(stage)

//            if(pStage.texture.image[0].imgName.equals("guis/assets/caverns/testmat2"))continue;
            // check the enable condition
            if (regs[pStage.conditionRegister] == 0) {
                stage++
                continue
            }

            // skip the stages involved in lighting
            if (pStage.lighting != stageLighting_t.SL_AMBIENT) {
                stage++
                continue
            }

            // skip if the stage is ( GL_ZERO, GL_ONE ), which is used for some alpha masks
            if (pStage.drawStateBits and (tr_local.GLS_SRCBLEND_BITS or tr_local.GLS_DSTBLEND_BITS) == tr_local.GLS_SRCBLEND_ZERO or tr_local.GLS_DSTBLEND_ONE) {
                stage++
                continue
            }

            // see if we are a new-style stage
            val newStage = pStage.newStage
            if (newStage != null) {
                //--------------------------
                //
                // new style stages
                //
                //--------------------------

                // completely skip the stage if we don't have the capability
                if (tr_local.tr.backEndRenderer != backEndName_t.BE_ARB2) {
                    stage++
                    continue
                }
                if (RenderSystem_init.r_skipNewAmbient.GetBool()) {
                    stage++
                    continue
                }
                qgl.qglColorPointer(4, GL11.GL_UNSIGNED_BYTE, idDrawVert.Companion.BYTES, ac.colorOffset().toLong())
                qgl.qglVertexAttribPointerARB(
                    9,
                    3,
                    GL11.GL_FLOAT,
                    false,
                    idDrawVert.Companion.BYTES,
                    ac.tangentsOffset_0().toLong()
                )
                qgl.qglVertexAttribPointerARB(
                    10,
                    3,
                    GL11.GL_FLOAT,
                    false,
                    idDrawVert.Companion.BYTES,
                    ac.tangentsOffset_1().toLong()
                )
                qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.normalOffset().toLong())
                qgl.qglEnableClientState(GL11.GL_COLOR_ARRAY)
                qgl.qglEnableVertexAttribArrayARB(9)
                qgl.qglEnableVertexAttribArrayARB(10)
                qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)
                tr_backend.GL_State(pStage.drawStateBits)
                qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, newStage.vertexProgram)
                qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)

                // megaTextures bind a lot of images and set a lot of parameters
                if (newStage.megaTexture != null) {
                    newStage.megaTexture.SetMappingForSurface(tri)
                    val localViewer = idVec3()
                    tr_main.R_GlobalPointToLocal(
                        surf.space.modelMatrix,
                        tr_local.backEnd.viewDef.renderView.vieworg,
                        localViewer
                    )
                    newStage.megaTexture.BindForViewOrigin(localViewer)
                }
                for (i in 0 until newStage.numVertexParms) {
                    val parm = BufferUtils.createFloatBuffer(4)
                    parm.put(0, regs[newStage.vertexParms[i][0]])
                    parm.put(1, regs[newStage.vertexParms[i][1]])
                    parm.put(2, regs[newStage.vertexParms[i][2]])
                    parm.put(3, regs[newStage.vertexParms[i][3]])
                    qgl.qglProgramLocalParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, i, parm)
                }
                for (i in 0 until newStage.numFragmentProgramImages) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        tr_backend.GL_SelectTexture(i)
                        newStage.fragmentProgramImages[i].Bind()
                    }
                }
                qgl.qglBindProgramARB(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB, newStage.fragmentProgram)
                qgl.qglEnable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)
                for (i in 1 until newStage.numFragmentProgramImages) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        tr_backend.GL_SelectTexture(i)
                        Image.globalImages.BindNull()
                    }
                }
                if (newStage.megaTexture != null) {
                    newStage.megaTexture.Unbind()
                }
                tr_backend.GL_SelectTexture(0)
                qgl.qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                qgl.qglDisable(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB)
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 0)
                qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)
                qgl.qglDisableVertexAttribArrayARB(9)
                qgl.qglDisableVertexAttribArrayARB(10)
                qgl.qglDisableClientState(GL11.GL_NORMAL_ARRAY)
                stage++
                continue
            }

            //--------------------------
            //
            // old style stages
            //
            //--------------------------
            // set the color
            color.put(0, regs[pStage.color.registers[0]])
            color.put(1, regs[pStage.color.registers[1]])
            color.put(2, regs[pStage.color.registers[2]])
            color.put(3, regs[pStage.color.registers[3]])

            // skip the entire stage if an add would be black
            if (pStage.drawStateBits and (tr_local.GLS_SRCBLEND_BITS or tr_local.GLS_DSTBLEND_BITS) == tr_local.GLS_SRCBLEND_ONE or tr_local.GLS_DSTBLEND_ONE && color[0] <= 0 && color[1] <= 0 && color[2] <= 0) {
                stage++
                continue
            }

            // skip the entire stage if a blend would be completely transparent
            if (pStage.drawStateBits and (tr_local.GLS_SRCBLEND_BITS or tr_local.GLS_DSTBLEND_BITS) == tr_local.GLS_SRCBLEND_SRC_ALPHA or tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA
                && color[3] <= 0
            ) {
                stage++
                continue
            }

            // select the vertex color source
            if (pStage.vertexColor == stageVertexColor_t.SVC_IGNORE) {
                qgl.qglColor4f(color[0], color[1], color[2], color[3]) //marquis logo
                //                System.out.printf("qglColor4f(%f, %f, %f, %f)\n",color.get(0), color.get(1), color.get(2), color.get(3));
            } else {
                qgl.qglColorPointer(
                    4,
                    GL11.GL_UNSIGNED_BYTE,
                    idDrawVert.Companion.BYTES,  /*(void *)&*/
                    ac.colorOffset().toLong()
                )
                qgl.qglEnableClientState(GL11.GL_COLOR_ARRAY)
                if (pStage.vertexColor == stageVertexColor_t.SVC_INVERSE_MODULATE) {
                    tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_RGB_ARB, GL11.GL_MODULATE)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB, GL11.GL_TEXTURE)
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                        ARBTextureEnvCombine.GL_PRIMARY_COLOR_ARB
                    )
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL11.GL_SRC_COLOR)
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB,
                        GL11.GL_ONE_MINUS_SRC_COLOR
                    )
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
                }

                // for vertex color and modulated color, we need to enable a second
                // texture stage
                if (color[0] != 1f || color[1] != 1f || color[2] != 1f || color[3] != 1f) {
                    tr_backend.GL_SelectTexture(1)
                    Image.globalImages.whiteImage.Bind()
                    tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
                    qgl.qglTexEnvfv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, color)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_RGB_ARB, GL11.GL_MODULATE)
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB,
                        ARBTextureEnvCombine.GL_PREVIOUS_ARB
                    )
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                        ARBTextureEnvCombine.GL_CONSTANT_ARB
                    )
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL11.GL_SRC_COLOR)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB, GL11.GL_SRC_COLOR)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_COMBINE_ALPHA_ARB, GL11.GL_MODULATE)
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE0_ALPHA_ARB,
                        ARBTextureEnvCombine.GL_PREVIOUS_ARB
                    )
                    qgl.qglTexEnvi(
                        GL11.GL_TEXTURE_ENV,
                        ARBTextureEnvCombine.GL_SOURCE1_ALPHA_ARB,
                        ARBTextureEnvCombine.GL_CONSTANT_ARB
                    )
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_ALPHA_ARB, GL11.GL_SRC_ALPHA)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_ALPHA_ARB, GL11.GL_SRC_ALPHA)
                    qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, 1)
                    tr_backend.GL_SelectTexture(0)
                }
            }

            // bind the texture
            tr_render.RB_BindVariableStageImage(pStage.texture, regs)

            // set the state
            tr_backend.GL_State(pStage.drawStateBits) //marquisDeSade
            draw_common.RB_PrepareStageTexturing(pStage, surf, ac)

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            draw_common.RB_FinishStageTexturing(pStage, surf, ac)
            if (pStage.vertexColor != stageVertexColor_t.SVC_IGNORE) {
                qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)
                tr_backend.GL_SelectTexture(1)
                tr_backend.GL_TexEnv(GL11.GL_MODULATE)
                Image.globalImages.BindNull()
                tr_backend.GL_SelectTexture(0)
                tr_backend.GL_TexEnv(GL11.GL_MODULATE)
            }
            stage++
        }

        // reset polygon offset
        if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
            qgl.qglDisable(GL11.GL_POLYGON_OFFSET_FILL)
        }
        if (surf.space.weaponDepthHack || surf.space.modelDepthHack != 0.0f) {
            tr_render.RB_LeaveDepthHack()
        }
    }

    /*
     =====================
     RB_STD_DrawShaderPasses

     Draw non-light dependent passes
     =====================
     */
    fun RB_STD_DrawShaderPasses(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int): Int {
        var i: Int

        // only obey skipAmbient if we are rendering a view
        if (tr_local.backEnd.viewDef.viewEntitys != null && RenderSystem_init.r_skipAmbient.GetBool()) {
            return numDrawSurfs
        }
        tr_backend.RB_LogComment("---------- RB_STD_DrawShaderPasses ----------\n")

        // if we are about to draw the first surface that needs
        // the rendering in a texture, copy it over
        if (drawSurfs.get(0).material.GetSort() >= Material.SS_POST_PROCESS) {
            if (RenderSystem_init.r_skipPostProcess.GetBool()) {
                return 0
            }

            // only dump if in a 3d view
            if (tr_local.backEnd.viewDef.viewEntitys != null && tr_local.tr.backEndRenderer == backEndName_t.BE_ARB2) {
                val imageWidth = CInt(tr_local.backEnd.viewDef.viewport.x2 - tr_local.backEnd.viewDef.viewport.x1 + 1)
                val imageHeight = CInt(tr_local.backEnd.viewDef.viewport.y2 - tr_local.backEnd.viewDef.viewport.y1 + 1)
                Image.globalImages.currentRenderImage.CopyFramebuffer(
                    tr_local.backEnd.viewDef.viewport.x1, tr_local.backEnd.viewDef.viewport.y1,
                    imageWidth, imageHeight, true
                )
            }
            tr_local.backEnd.currentRenderCopied = true
        }
        tr_backend.GL_SelectTexture(1)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        draw_common.RB_SetProgramEnvironment()

        // we don't use RB_RenderDrawSurfListWithFunction()
        // because we want to defer the matrix load because many
        // surfaces won't draw any ambient passes
        tr_local.backEnd.currentSpace = null
        i = 0
        while (i < numDrawSurfs /*&& numDrawSurfs == 5*/) {
            if (drawSurfs.get(i).material.SuppressInSubview()) {
                i++
                continue
            }
            if (tr_local.backEnd.viewDef.isXraySubview && drawSurfs.get(i).space.entityDef != null) {
                if (drawSurfs.get(i).space.entityDef.parms.xrayIndex != 2) {
                    i++
                    continue
                }
            }

            // we need to draw the post process shaders after we have drawn the fog lights
            if (drawSurfs.get(i).material.GetSort() >= Material.SS_POST_PROCESS
                && !tr_local.backEnd.currentRenderCopied
            ) {
                break
            }
            draw_common.RB_STD_T_RenderShaderPasses(drawSurfs.get(i))
            i++
        }
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
        qgl.qglColor3f(1f, 1f, 1f)
        return i
    }

    /*
     ==============================================================================

     BACK END RENDERING OF STENCIL SHADOWS

     ==============================================================================
     */
    /*
     =====================
     RB_StencilShadowPass

     Stencil test should already be enabled, and the stencil buffer should have
     been set to 128 on any surfaces that might receive shadows
     =====================
     */
    fun RB_StencilShadowPass(drawSurfs: drawSurf_s?) {
        if (!RenderSystem_init.r_shadows.GetBool()) {
            return
        }
        if (TempDump.NOT(drawSurfs)) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_StencilShadowPass ----------\n")
        Image.globalImages.BindNull()
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        // for visualizing the shadows
        if (RenderSystem_init.r_showShadows.GetInteger() != 0) {
            if (RenderSystem_init.r_showShadows.GetInteger() == 2) {
                // draw filled in
                tr_backend.GL_State(tr_local.GLS_DEPTHMASK or tr_local.GLS_SRCBLEND_ONE or tr_local.GLS_DSTBLEND_ONE or tr_local.GLS_DEPTHFUNC_LESS)
            } else {
                // draw as lines, filling the depth buffer
                tr_backend.GL_State(tr_local.GLS_SRCBLEND_ONE or tr_local.GLS_DSTBLEND_ZERO or tr_local.GLS_POLYMODE_LINE or tr_local.GLS_DEPTHFUNC_ALWAYS)
            }
        } else {
            // don't write to the color buffer, just the stencil buffer
            tr_backend.GL_State(tr_local.GLS_DEPTHMASK or tr_local.GLS_COLORMASK or tr_local.GLS_ALPHAMASK or tr_local.GLS_DEPTHFUNC_LESS)
        }
        if (RenderSystem_init.r_shadowPolygonFactor.GetFloat() != 0f || RenderSystem_init.r_shadowPolygonOffset.GetFloat() != 0f) {
            qgl.qglPolygonOffset(
                RenderSystem_init.r_shadowPolygonFactor.GetFloat(),
                -RenderSystem_init.r_shadowPolygonOffset.GetFloat()
            )
            qgl.qglEnable(GL11.GL_POLYGON_OFFSET_FILL)
        }
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 1, 255)
        if (tr_local.glConfig.depthBoundsTestAvailable && RenderSystem_init.r_useDepthBoundsTest.GetBool()) {
            qgl.qglEnable(EXTDepthBoundsTest.GL_DEPTH_BOUNDS_TEST_EXT)
        }
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_Shadow.INSTANCE)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
        if (RenderSystem_init.r_shadowPolygonFactor.GetFloat() != 0f || RenderSystem_init.r_shadowPolygonOffset.GetFloat() != 0f) {
            qgl.qglDisable(GL11.GL_POLYGON_OFFSET_FILL)
        }
        if (tr_local.glConfig.depthBoundsTestAvailable && RenderSystem_init.r_useDepthBoundsTest.GetBool()) {
            qgl.qglDisable(EXTDepthBoundsTest.GL_DEPTH_BOUNDS_TEST_EXT)
        }
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglStencilFunc(GL11.GL_GEQUAL, 128, 255)
        qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
    }

    /*
     =====================
     RB_BlendLight

     Dual texture together the falloff and projection texture with a blend
     mode to the framebuffer, instead of interacting with the surface texture
     =====================
     */
    fun RB_BlendLight(drawSurfs: drawSurf_s?, drawSurfs2: drawSurf_s?) {
        val lightShader: idMaterial?
        var stage: shaderStage_t
        var i: Int
        val regs: FloatArray?
        if (TempDump.NOT(drawSurfs)) {
            return
        }
        if (RenderSystem_init.r_skipBlendLights.GetBool()) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_BlendLight ----------\n")
        lightShader = tr_local.backEnd.vLight.lightShader
        regs = tr_local.backEnd.vLight.shaderRegisters

        // texture 1 will get the falloff texture
        tr_backend.GL_SelectTexture(1)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglTexCoord2f(0f, 0.5f)
        tr_local.backEnd.vLight.falloffImage.Bind()

        // texture 0 will get the projected texture
        tr_backend.GL_SelectTexture(0)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
        i = 0
        while (i < lightShader.GetNumStages()) {
            stage = lightShader.GetStage(i)
            if (0 == regs[stage.conditionRegister]) {
                i++
                continue
            }
            tr_backend.GL_State(tr_local.GLS_DEPTHMASK or stage.drawStateBits or tr_local.GLS_DEPTHFUNC_EQUAL)
            tr_backend.GL_SelectTexture(0)
            stage.texture.image[0].Bind()
            if (stage.texture.hasMatrix) {
                tr_render.RB_LoadShaderTextureMatrix(regs, stage.texture)
            }

            // get the modulate values from the light, including alpha, unlike normal lights
            tr_local.backEnd.lightColor[0] = regs[stage.color.registers[0]]
            tr_local.backEnd.lightColor[1] = regs[stage.color.registers[1]]
            tr_local.backEnd.lightColor[2] = regs[stage.color.registers[2]]
            tr_local.backEnd.lightColor[3] = regs[stage.color.registers[3]]
            qgl.qglColor4fv(tr_local.backEnd.lightColor)
            tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BlendLight.INSTANCE)
            tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BlendLight.INSTANCE)
            if (stage.texture.hasMatrix) {
                tr_backend.GL_SelectTexture(0)
                qgl.qglMatrixMode(GL11.GL_TEXTURE)
                qgl.qglLoadIdentity()
                qgl.qglMatrixMode(GL11.GL_MODELVIEW)
            }
            i++
        }
        tr_backend.GL_SelectTexture(1)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
    }

    /*
     =============================================================================================

     BLEND LIGHT PROJECTION

     =============================================================================================
     */
    /*
     ==================
     RB_FogPass
     ==================
     */
    fun RB_FogPass(drawSurfs: drawSurf_s?, drawSurfs2: drawSurf_s?) {
        val frustumTris: srfTriangles_s?
        val ds = drawSurf_s() //memset( &ds, 0, sizeof( ds ) );
        val lightShader: idMaterial?
        val stage: shaderStage_t
        val regs: FloatArray?
        tr_backend.RB_LogComment("---------- RB_FogPass ----------\n")

        // create a surface for the light frustom triangles, which are oriented drawn side out
        frustumTris = tr_local.backEnd.vLight.frustumTris

        // if we ran out of vertex cache memory, skip it
        if (TempDump.NOT(frustumTris.ambientCache)) {
            return
        }
        ds.space = tr_local.backEnd.viewDef.worldSpace
        ds.geo = frustumTris
        ds.scissorRect = idScreenRect(tr_local.backEnd.viewDef.scissor)

        // find the current color and density of the fog
        lightShader = tr_local.backEnd.vLight.lightShader
        regs = tr_local.backEnd.vLight.shaderRegisters
        // assume fog shaders have only a single stage
        stage = lightShader.GetStage(0)
        tr_local.backEnd.lightColor[0] = regs[stage.color.registers[0]]
        tr_local.backEnd.lightColor[1] = regs[stage.color.registers[1]]
        tr_local.backEnd.lightColor[2] = regs[stage.color.registers[2]]
        tr_local.backEnd.lightColor[3] = regs[stage.color.registers[3]]
        qgl.qglColor3fv(tr_local.backEnd.lightColor)

        // calculate the falloff planes
        val a: Float

        // if they left the default value on, set a fog distance of 500
        a = if (tr_local.backEnd.lightColor[3] <= 1.0) {
            -0.5f / tr_local.DEFAULT_FOG_DISTANCE
        } else {
            // otherwise, distance = alpha color
            -0.5f / tr_local.backEnd.lightColor[3]
        }
        tr_backend.GL_State(tr_local.GLS_DEPTHMASK or tr_local.GLS_SRCBLEND_SRC_ALPHA or tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or tr_local.GLS_DEPTHFUNC_EQUAL)

        // texture 0 is the falloff image
        tr_backend.GL_SelectTexture(0)
        Image.globalImages.fogImage.Bind()
        //GL_Bind( tr.whiteImage );
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
        qgl.qglTexCoord2f(0.5f, 0.5f) // make sure Q is set
        draw_common.fogPlanes[0].oSet(0, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[2])
        draw_common.fogPlanes[0].oSet(1, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[6])
        draw_common.fogPlanes[0].oSet(2, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[10])
        draw_common.fogPlanes[0].oSet(3, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[14])
        draw_common.fogPlanes[1].oSet(0, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[0])
        draw_common.fogPlanes[1].oSet(1, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[4])
        draw_common.fogPlanes[1].oSet(2, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[8])
        draw_common.fogPlanes[1].oSet(3, a * tr_local.backEnd.viewDef.worldSpace.modelViewMatrix[12])

        // texture 1 is the entering plane fade correction
        tr_backend.GL_SelectTexture(1)
        Image.globalImages.fogEnterImage.Bind()
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)

        // T will get a texgen for the fade plane, which is always the "top" plane on unrotated lights
        draw_common.fogPlanes[2].oSet(0, 0.001f * tr_local.backEnd.vLight.fogPlane.oGet(0))
        draw_common.fogPlanes[2].oSet(1, 0.001f * tr_local.backEnd.vLight.fogPlane.oGet(1))
        draw_common.fogPlanes[2].oSet(2, 0.001f * tr_local.backEnd.vLight.fogPlane.oGet(2))
        draw_common.fogPlanes[2].oSet(3, 0.001f * tr_local.backEnd.vLight.fogPlane.oGet(3))

        // S is based on the view origin
        val s =
            tr_local.backEnd.viewDef.renderView.vieworg.oMultiply(draw_common.fogPlanes[2].Normal()) + draw_common.fogPlanes[2].oGet(
                3
            )
        draw_common.fogPlanes[3].oSet(0, 0f)
        draw_common.fogPlanes[3].oSet(1, 0f)
        draw_common.fogPlanes[3].oSet(2, 0f)
        draw_common.fogPlanes[3].oSet(3, tr_local.FOG_ENTER + s)
        qgl.qglTexCoord2f(tr_local.FOG_ENTER + s, tr_local.FOG_ENTER)

        // draw it
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BasicFog.INSTANCE)
        tr_render.RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BasicFog.INSTANCE)

        // the light frustum bounding planes aren't in the depth buffer, so use depthfunc_less instead
        // of depthfunc_equal
        tr_backend.GL_State(tr_local.GLS_DEPTHMASK or tr_local.GLS_SRCBLEND_SRC_ALPHA or tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA or tr_local.GLS_DEPTHFUNC_LESS)
        tr_backend.GL_Cull(cullType_t.CT_BACK_SIDED)
        tr_render.RB_RenderDrawSurfChainWithFunction(ds, RB_T_BasicFog.INSTANCE)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
        tr_backend.GL_SelectTexture(1)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
        Image.globalImages.BindNull()
        tr_backend.GL_SelectTexture(0)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
        qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
    }

    /*
     ==================
     RB_STD_FogAllLights
     ==================
     */
    fun RB_STD_FogAllLights() {
        var vLight: viewLight_s?
        if (RenderSystem_init.r_skipFogLights.GetBool() || RenderSystem_init.r_showOverDraw.GetInteger() != 0 || tr_local.backEnd.viewDef.isXraySubview /* dont fog in xray mode*/) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_STD_FogAllLights ----------\n")
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        vLight = tr_local.backEnd.viewDef.viewLights
        while (vLight != null) {
            tr_local.backEnd.vLight = vLight
            if (!vLight.lightShader.IsFogLight() && !vLight.lightShader.IsBlendLight()) {
                vLight = vLight.next
                continue
            }

//if(false){ // _D3XP disabled that
//		if ( r_ignore.GetInteger() ) {
//			// we use the stencil buffer to guarantee that no pixels will be
//			// double fogged, which happens in some areas that are thousands of
//			// units from the origin
//			backEnd.currentScissor = vLight.scissorRect;
//			if ( r_useScissor.GetBool() ) {
//				qglScissor( backEnd.viewDef.viewport.x1 + backEnd.currentScissor.x1,
//					backEnd.viewDef.viewport.y1 + backEnd.currentScissor.y1,
//					backEnd.currentScissor.x2 + 1 - backEnd.currentScissor.x1,
//					backEnd.currentScissor.y2 + 1 - backEnd.currentScissor.y1 );
//			}
//			qglClear( GL_STENCIL_BUFFER_BIT );
//
//			qglEnable( GL_STENCIL_TEST );
//
//			// only pass on the cleared stencil values
//			qglStencilFunc( GL_EQUAL, 128, 255 );
//
//			// when we pass the stencil test and depth test and are going to draw,
//			// increment the stencil buffer so we don't ever draw on that pixel again
//			qglStencilOp( GL_KEEP, GL_KEEP, GL_INCR );
//		}
//}
            if (vLight.lightShader.IsFogLight()) {
                draw_common.RB_FogPass(vLight.globalInteractions[0], vLight.localInteractions[0])
            } else if (vLight.lightShader.IsBlendLight()) {
                draw_common.RB_BlendLight(vLight.globalInteractions[0], vLight.localInteractions[0])
            }
            qgl.qglDisable(GL11.GL_STENCIL_TEST)
            vLight = vLight.next
        }
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
    }

    /*
     ==================
     RB_STD_LightScale

     Perform extra blending passes to multiply the entire buffer by
     a floating point value
     ==================
     */
    fun RB_STD_LightScale() {
        var v: Float
        var f: Float
        if (1.0f == tr_local.backEnd.overBright) {
            return
        }
        if (RenderSystem_init.r_skipLightScale.GetBool()) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_STD_LightScale ----------\n")

        // the scissor may be smaller than the viewport for subviews
        if (RenderSystem_init.r_useScissor.GetBool()) {
            qgl.qglScissor(
                tr_local.backEnd.viewDef.viewport.x1 + tr_local.backEnd.viewDef.scissor.x1,
                tr_local.backEnd.viewDef.viewport.y1 + tr_local.backEnd.viewDef.scissor.y1,
                tr_local.backEnd.viewDef.scissor.x2 - tr_local.backEnd.viewDef.scissor.x1 + 1,
                tr_local.backEnd.viewDef.scissor.y2 - tr_local.backEnd.viewDef.scissor.y1 + 1
            )
            tr_local.backEnd.currentScissor = tr_local.backEnd.viewDef.scissor
        }

        // full screen blends
        qgl.qglLoadIdentity()
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglPushMatrix()
        qgl.qglLoadIdentity()
        qgl.qglOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0)
        tr_backend.GL_State(tr_local.GLS_SRCBLEND_DST_COLOR or tr_local.GLS_DSTBLEND_SRC_COLOR)
        tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED) // so mirror views also get it
        Image.globalImages.BindNull()
        qgl.qglDisable(GL11.GL_DEPTH_TEST)
        qgl.qglDisable(GL11.GL_STENCIL_TEST)
        v = 1f
        while (Math.abs(v - tr_local.backEnd.overBright) > 0.01) {    // a little extra slop
            f = tr_local.backEnd.overBright / v
            f /= 2f
            if (f > 1) {
                f = 1f
            }
            qgl.qglColor3f(f, f, f)
            v = v * f * 2
            qgl.qglBegin(GL11.GL_QUADS)
            qgl.qglVertex2f(0f, 0f)
            qgl.qglVertex2f(0f, 1f)
            qgl.qglVertex2f(1f, 1f)
            qgl.qglVertex2f(1f, 0f)
            qgl.qglEnd()
        }
        qgl.qglPopMatrix()
        qgl.qglEnable(GL11.GL_DEPTH_TEST)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     =============
     RB_STD_DrawView

     =============
     */
    fun RB_STD_DrawView() {
        val drawSurfs: Array<drawSurf_s?>?
        val numDrawSurfs: Int
        tr_backend.RB_LogComment("---------- RB_STD_DrawView ----------\n")
        tr_local.backEnd.depthFunc = tr_local.GLS_DEPTHFUNC_EQUAL
        drawSurfs = tr_local.backEnd.viewDef.drawSurfs
        numDrawSurfs = tr_local.backEnd.viewDef.numDrawSurfs

        // clear the z buffer, set the projection matrix, etc
        tr_render.RB_BeginDrawingView()

        // decide how much overbrighting we are going to do
        tr_render.RB_DetermineLightScale()

        // fill the depth buffer and clear color buffer to black except on
        // subviews
        draw_common.RB_STD_FillDepthBuffer(drawSurfs, numDrawSurfs)
        when (tr_local.tr.backEndRenderer) {
            backEndName_t.BE_ARB -> draw_arb.RB_ARB_DrawInteractions()
            backEndName_t.BE_ARB2 -> draw_arb2.RB_ARB2_DrawInteractions()
        }

        // disable stencil shadow test
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)

        // uplight the entire screen to crutch up not having better blending range
        draw_common.RB_STD_LightScale()

        // now draw any non-light dependent shading passes
        val processed = draw_common.RB_STD_DrawShaderPasses(drawSurfs, numDrawSurfs)

        // fob and blend lights
        draw_common.RB_STD_FogAllLights()

        // now draw any post-processing effects using _currentRender
        if (processed < numDrawSurfs) {
            draw_common.RB_STD_DrawShaderPasses(
                Arrays.copyOfRange(drawSurfs, processed, numDrawSurfs),
                numDrawSurfs - processed
            )
        }
        tr_rendertools.RB_RenderDebugTools(drawSurfs, numDrawSurfs)
    }

    /*
     =============================================================================================

     FILL DEPTH BUFFER

     =============================================================================================
     */
    /*
     ==================
     RB_T_FillDepthBuffer
     ==================
     */
    class RB_T_FillDepthBuffer private constructor() : triFunc() {
        override fun run(surf: drawSurf_s?) {
            var stage: Int
            val shader: idMaterial?
            var pStage: shaderStage_t
            val regs: FloatArray?
            val color = FloatArray(4)
            val tri: srfTriangles_s?
            tri = surf.geo
            shader = surf.material

            // update the clip plane if needed
            if (tr_local.backEnd.viewDef.numClipPlanes != 0 && surf.space !== tr_local.backEnd.currentSpace) {
                tr_backend.GL_SelectTexture(1)
                val plane = idPlane()
                tr_main.R_GlobalPlaneToLocal(surf.space.modelMatrix, tr_local.backEnd.viewDef.clipPlanes[0], plane)
                plane.oPluSet(3, 0.5f) // the notch is in the middle
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, plane.ToFloatPtr())
                tr_backend.GL_SelectTexture(0)
            }
            if (!shader.IsDrawn()) {
                return
            }

            // some deforms may disable themselves by setting numIndexes = 0
            if (0 == tri.numIndexes) {
                return
            }

            // translucent surfaces don't put anything in the depth buffer and don't
            // test against it, which makes them fail the mirror clip plane operation
            if (shader.Coverage() == materialCoverage_t.MC_TRANSLUCENT) {
                return
            }
            if (TempDump.NOT(tri.ambientCache)) {
                Common.common.Printf("RB_T_FillDepthBuffer: !tri.ambientCache\n")
                return
            }

            // get the expressions for conditionals / color / texcoords
            regs = surf.shaderRegisters

            // if all stages of a material have been conditioned off, don't do anything
            stage = 0
            while (stage < shader.GetNumStages()) {
                pStage = shader.GetStage(stage)
                // check the stage enable condition
                if (regs[pStage.conditionRegister] != 0) {
                    break
                }
                stage++
            }
            if (stage == shader.GetNumStages()) {
                return
            }

            // set polygon offset if necessary
            if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
                qgl.qglEnable(GL11.GL_POLYGON_OFFSET_FILL)
                qgl.qglPolygonOffset(
                    RenderSystem_init.r_offsetFactor.GetFloat(),
                    RenderSystem_init.r_offsetUnits.GetFloat() * shader.GetPolygonOffset()
                )
            }

            // subviews will just down-modulate the color buffer by overbright
            if (shader.GetSort() == Material.SS_SUBVIEW.toFloat()) {
                tr_backend.GL_State(tr_local.GLS_SRCBLEND_DST_COLOR or tr_local.GLS_DSTBLEND_ZERO or tr_local.GLS_DEPTHFUNC_LESS)
                color[2] = 1.0f / tr_local.backEnd.overBright
                color[1] = color[2]
                color[0] = color[1]
                color[3] = 1
            } else {
                // others just draw black
                color[2] = 0
                color[1] = color[2]
                color[0] = color[1]
                color[3] = 1
            }
            val ac =
                idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts.
            qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.xyzOffset().toLong())
            qgl.qglTexCoordPointer(
                2,
                GL11.GL_FLOAT,
                idDrawVert.Companion.BYTES,  /*reinterpret_cast<void *>*/
                ac.stOffset().toLong()
            )
            var drawSolid = shader.Coverage() == materialCoverage_t.MC_OPAQUE

            // we may have multiple alpha tested stages
            if (shader.Coverage() == materialCoverage_t.MC_PERFORATED) {
                // if the only alpha tested stages are condition register omitted,
                // draw a normal opaque surface
                var didDraw = false
                qgl.qglEnable(GL11.GL_ALPHA_TEST)
                // perforated surfaces may have multiple alpha tested stages
                stage = 0
                while (stage < shader.GetNumStages()) {
                    pStage = shader.GetStage(stage)
                    if (!pStage.hasAlphaTest) {
                        stage++
                        continue
                    }

                    // check the stage enable condition
                    if (regs[pStage.conditionRegister] == 0) {
                        stage++
                        continue
                    }

                    // if we at least tried to draw an alpha tested stage,
                    // we won't draw the opaque surface
                    didDraw = true

                    // set the alpha modulate
                    color[3] = regs[pStage.color.registers[3]]

                    // skip the entire stage if alpha would be black
                    if (color[3] <= 0) {
                        stage++
                        continue
                    }
                    qgl.qglColor4fv(color)
                    qgl.qglAlphaFunc(GL11.GL_GREATER, regs[pStage.alphaTestRegister])

                    // bind the texture
                    pStage.texture.image[0].Bind()

                    // set texture matrix and texGens
                    draw_common.RB_PrepareStageTexturing(pStage, surf, ac)

                    // draw it
                    tr_render.RB_DrawElementsWithCounters(tri)
                    draw_common.RB_FinishStageTexturing(pStage, surf, ac)
                    stage++
                }
                qgl.qglDisable(GL11.GL_ALPHA_TEST)
                if (!didDraw) {
                    drawSolid = true
                }
            }

            // draw the entire surface solid
            if (drawSolid) {
                qgl.qglColor4fv(color)
                Image.globalImages.whiteImage.Bind()

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)
            }

            // reset polygon offset
            if (shader.TestMaterialFlag(Material.MF_POLYGONOFFSET)) {
                qgl.qglDisable(GL11.GL_POLYGON_OFFSET_FILL)
            }

            // reset blending
            if (shader.GetSort() == Material.SS_SUBVIEW.toFloat()) {
                tr_backend.GL_State(tr_local.GLS_DEPTHFUNC_LESS)
            }
        }

        companion object {
            val INSTANCE: triFunc? = RB_T_FillDepthBuffer()
        }
    }

    /*
     =====================
     RB_T_Shadow

     the shadow volumes face INSIDE
     =====================
     */
    class RB_T_Shadow private constructor() : triFunc() {
        override fun run(surf: drawSurf_s?) {
            val tri: srfTriangles_s? //TODO: should this be an array?

            // set the light position if we are using a vertex program to project the rear surfaces
            if (tr_local.tr.backEndRendererHasVertexPrograms && RenderSystem_init.r_useShadowVertexProgram.GetBool()
                && surf.space !== tr_local.backEnd.currentSpace
            ) {
                val localLight = idVec4()
                val lightBuffer = BufferUtils.createFloatBuffer(4)
                tr_main.R_GlobalPointToLocal(
                    surf.space.modelMatrix,
                    tr_local.backEnd.vLight.globalLightOrigin,
                    localLight
                )
                lightBuffer.put(localLight.ToFloatPtr()).rewind() //localLight.w = 0.0f;
                qgl.qglProgramEnvParameter4fvARB(
                    ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                    programParameter_t.PP_LIGHT_ORIGIN,
                    lightBuffer
                )
            }
            tri = surf.geo
            if (TempDump.NOT(tri.shadowCache)) {
                return
            }
            qgl.qglVertexPointer(
                4,
                GL11.GL_FLOAT,
                shadowCache_s.Companion.BYTES,
                VertexCache.vertexCache.Position(tri.shadowCache).int.toLong()
            )

            // we always draw the sil planes, but we may not need to draw the front or rear caps
            val numIndexes: Int
            var external = false
            if (0 == RenderSystem_init.r_useExternalShadows.GetInteger()) {
                numIndexes = tri.numIndexes
            } else if (RenderSystem_init.r_useExternalShadows.GetInteger() == 2) { // force to no caps for testing
                numIndexes = tri.numShadowIndexesNoCaps
            } else if (0 == surf.dsFlags and tr_local.DSF_VIEW_INSIDE_SHADOW) {
                // if we aren't inside the shadow projection, no caps are ever needed needed
                numIndexes = tri.numShadowIndexesNoCaps
                external = true
            } else if (!tr_local.backEnd.vLight.viewInsideLight && 0 == surf.geo.shadowCapPlaneBits and Model.SHADOW_CAP_INFINITE) {
                // if we are inside the shadow projection, but outside the light, and drawing
                // a non-infinite shadow, we can skip some caps
                numIndexes = if (tr_local.backEnd.vLight.viewSeesShadowPlaneBits and surf.geo.shadowCapPlaneBits != 0) {
                    // we can see through a rear cap, so we need to draw it, but we can skip the
                    // caps on the actual surface
                    tri.numShadowIndexesNoFrontCaps
                } else {
                    // we don't need to draw any caps
                    tri.numShadowIndexesNoCaps
                }
                external = true
            } else {
                // must draw everything
                numIndexes = tri.numIndexes
            }

            // set depth bounds
            if (tr_local.glConfig.depthBoundsTestAvailable && RenderSystem_init.r_useDepthBoundsTest.GetBool()) {
                qgl.qglDepthBoundsEXT(surf.scissorRect.zmin.toDouble(), surf.scissorRect.zmax.toDouble())
            }

            // debug visualization
            if (RenderSystem_init.r_showShadows.GetInteger() != 0) {
                if (RenderSystem_init.r_showShadows.GetInteger() == 3) {
                    if (external) {
                        qgl.qglColor3f(
                            0.1f / tr_local.backEnd.overBright,
                            1 / tr_local.backEnd.overBright,
                            0.1f / tr_local.backEnd.overBright
                        )
                    } else {
                        // these are the surfaces that require the reverse
                        qgl.qglColor3f(
                            1 / tr_local.backEnd.overBright,
                            0.1f / tr_local.backEnd.overBright,
                            0.1f / tr_local.backEnd.overBright
                        )
                    }
                } else {
                    // draw different color for turboshadows
                    if (surf.geo.shadowCapPlaneBits and Model.SHADOW_CAP_INFINITE != 0) {
                        if (numIndexes == tri.numIndexes) {
                            qgl.qglColor3f(
                                1 / tr_local.backEnd.overBright,
                                0.1f / tr_local.backEnd.overBright,
                                0.1f / tr_local.backEnd.overBright
                            )
                        } else {
                            qgl.qglColor3f(
                                1 / tr_local.backEnd.overBright,
                                0.4f / tr_local.backEnd.overBright,
                                0.1f / tr_local.backEnd.overBright
                            )
                        }
                    } else {
                        if (numIndexes == tri.numIndexes) {
                            qgl.qglColor3f(
                                0.1f / tr_local.backEnd.overBright,
                                1 / tr_local.backEnd.overBright,
                                0.1f / tr_local.backEnd.overBright
                            )
                        } else if (numIndexes == tri.numShadowIndexesNoFrontCaps) {
                            qgl.qglColor3f(
                                0.1f / tr_local.backEnd.overBright,
                                1 / tr_local.backEnd.overBright,
                                0.6f / tr_local.backEnd.overBright
                            )
                        } else {
                            qgl.qglColor3f(
                                0.6f / tr_local.backEnd.overBright,
                                1 / tr_local.backEnd.overBright,
                                0.1f / tr_local.backEnd.overBright
                            )
                        }
                    }
                }
                qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
                qgl.qglDisable(GL11.GL_STENCIL_TEST)
                tr_backend.GL_Cull(cullType_t.CT_TWO_SIDED)
                tr_render.RB_DrawShadowElementsWithCounters(tri, numIndexes)
                tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
                qgl.qglEnable(GL11.GL_STENCIL_TEST)
                return
            }

            // patent-free work around
            if (!external) {
                // "preload" the stencil buffer with the number of volumes
                // that get clipped by the near or far clip plane
                qgl.qglStencilOp(GL11.GL_KEEP, tr_local.tr.stencilDecr, tr_local.tr.stencilDecr)
                tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
                tr_render.RB_DrawShadowElementsWithCounters(tri, numIndexes)
                qgl.qglStencilOp(GL11.GL_KEEP, tr_local.tr.stencilIncr, tr_local.tr.stencilIncr)
                tr_backend.GL_Cull(cullType_t.CT_BACK_SIDED)
                tr_render.RB_DrawShadowElementsWithCounters(tri, numIndexes)
            }

            // traditional depth-pass stencil shadows
            qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, tr_local.tr.stencilIncr)
            tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
            tr_render.RB_DrawShadowElementsWithCounters(tri, numIndexes)
            qgl.qglStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, tr_local.tr.stencilDecr)
            tr_backend.GL_Cull(cullType_t.CT_BACK_SIDED)
            tr_render.RB_DrawShadowElementsWithCounters(tri, numIndexes)
        }

        companion object {
            val INSTANCE: triFunc? = RB_T_Shadow()
        }
    }

    //=========================================================================================
    /*
     =====================
     RB_T_BlendLight

     =====================
     */
    class RB_T_BlendLight private constructor() : triFunc() {
        override fun run(surf: drawSurf_s?) {
            val tri: srfTriangles_s?
            tri = surf.geo
            if (tr_local.backEnd.currentSpace !== surf.space) {
                val lightProject: Array<idPlane?> = idPlane.Companion.generateArray(4)
                var i: Int
                i = 0
                while (i < 4) {
                    tr_main.R_GlobalPlaneToLocal(
                        surf.space.modelMatrix,
                        tr_local.backEnd.vLight.lightProject[i],
                        lightProject[i]
                    )
                    i++
                }
                tr_backend.GL_SelectTexture(0)
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, lightProject[0].ToFloatPtr())
                qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, lightProject[1].ToFloatPtr())
                qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, lightProject[2].ToFloatPtr())
                tr_backend.GL_SelectTexture(1)
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, lightProject[3].ToFloatPtr())
            }

            // this gets used for both blend lights and shadow draws
            if (tri.ambientCache != null) {
                val ac =
                    idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts.
                qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.xyzOffset().toLong())
            } else if (tri.shadowCache != null) {
                val sc =
                    shadowCache_s(VertexCache.vertexCache.Position(tri.shadowCache)) //TODO:figure out how to work these damn casts.
                qgl.qglVertexPointer(3, GL11.GL_FLOAT, shadowCache_s.Companion.BYTES, sc.xyz.ToFloatPtr())
            }
            tr_render.RB_DrawElementsWithCounters(tri)
        }

        companion object {
            val INSTANCE: triFunc? = RB_T_BlendLight()
        }
    }

    //=========================================================================================
    /*
     =====================
     RB_T_BasicFog

     =====================
     */
    class RB_T_BasicFog private constructor() : triFunc() {
        override fun run(surf: drawSurf_s?) {
            if (tr_local.backEnd.currentSpace !== surf.space) {
                val local = idPlane()
                tr_backend.GL_SelectTexture(0)
                tr_main.R_GlobalPlaneToLocal(surf.space.modelMatrix, draw_common.fogPlanes[0], local)
                local.oPluSet(3, 0.5f)
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, local.ToFloatPtr())

//		R_GlobalPlaneToLocal( surf.space.modelMatrix, fogPlanes[1], local );
//		local[3] += 0.5;
                local.oSet(0, local.oSet(1, local.oSet(2, local.oSet(3, 0.5f))))
                qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, local.ToFloatPtr())
                tr_backend.GL_SelectTexture(1)

                // GL_S is constant per viewer
                tr_main.R_GlobalPlaneToLocal(surf.space.modelMatrix, draw_common.fogPlanes[2], local)
                local.oPluSet(3, tr_local.FOG_ENTER)
                qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, local.ToFloatPtr())
                tr_main.R_GlobalPlaneToLocal(surf.space.modelMatrix, draw_common.fogPlanes[3], local)
                qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, local.ToFloatPtr())
            }
            RB_T_RenderTriangleSurface.Companion.INSTANCE.run(surf)
        }

        companion object {
            val INSTANCE: triFunc? = RB_T_BasicFog()
        }
    }
}