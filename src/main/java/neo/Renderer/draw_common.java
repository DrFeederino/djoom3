package neo.Renderer;

import neo.Renderer.Model.shadowCache_s;
import neo.Renderer.Model.srfTriangles_s;
import neo.idlib.containers.CInt;
import neo.idlib.geometry.DrawVert.idDrawVert;
import neo.idlib.math.Plane.idPlane;
import neo.idlib.math.Vector.idVec3;
import neo.idlib.math.Vector.idVec4;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.stream.Stream;

import static neo.Renderer.Image.globalImages;
import static neo.Renderer.Material.*;
import static neo.Renderer.Material.cullType_t.*;
import static neo.Renderer.Material.materialCoverage_t.*;
import static neo.Renderer.Material.stageLighting_t.SL_AMBIENT;
import static neo.Renderer.Material.stageVertexColor_t.SVC_IGNORE;
import static neo.Renderer.Material.stageVertexColor_t.SVC_INVERSE_MODULATE;
import static neo.Renderer.Material.texgen_t.*;
import static neo.Renderer.Model.SHADOW_CAP_INFINITE;
import static neo.Renderer.RenderSystem_init.*;
import static neo.Renderer.VertexCache.vertexCache;
import static neo.Renderer.draw_arb.RB_ARB_DrawInteractions;
import static neo.Renderer.draw_arb2.RB_ARB2_DrawInteractions;
import static neo.Renderer.qgl.*;
import static neo.Renderer.tr_backend.*;
import static neo.Renderer.tr_local.*;
import static neo.Renderer.tr_local.backEndName_t.BE_ARB2;
import static neo.Renderer.tr_local.programParameter_t.PP_LIGHT_ORIGIN;
import static neo.Renderer.tr_local.program_t.*;
import static neo.Renderer.tr_main.*;
import static neo.Renderer.tr_render.*;
import static neo.Renderer.tr_rendertools.RB_RenderDebugTools;
import static neo.TempDump.NOT;
import static neo.framework.Common.common;
import static org.lwjgl.opengl.ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB;
import static org.lwjgl.opengl.ARBTextureEnvCombine.*;
import static org.lwjgl.opengl.ARBVertexProgram.GL_VERTEX_PROGRAM_ARB;
import static org.lwjgl.opengl.EXTDepthBoundsTest.GL_DEPTH_BOUNDS_TEST_EXT;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_REFLECTION_MAP;

/**
 *
 */
public class draw_common {
    /*
     =====================
     RB_BakeTextureMatrixIntoTexgen
     =====================
     */

    //========================================================================
    private static final idPlane[] fogPlanes = Stream.generate(idPlane::new).limit(4).toArray(idPlane[]::new);
    /*
     ================
     RB_FinishStageTexturing
     ================
     */private static int DBG_RB_FinishStageTexturing = 0;
    /*
     ==================
     RB_STD_T_RenderShaderPasses

     This is also called for the generated 2D rendering
     ==================
     */private static int DBG_RB_STD_T_RenderShaderPasses = 0;
    private static final int DBG_hasMatrix = 0;

    public static void RB_BakeTextureMatrixIntoTexgen(idVec4[]/*idPlane[]*/ lightProject/*[3]*/, final float[] textureMatrix) {
        float[] genMatrix = new float[16];
        float[] finale = new float[16];

        genMatrix[0] = lightProject[0].oGet(0);
        genMatrix[4] = lightProject[0].oGet(1);
        genMatrix[8] = lightProject[0].oGet(2);
        genMatrix[12] = lightProject[0].oGet(3);

        genMatrix[1] = lightProject[1].oGet(0);
        genMatrix[5] = lightProject[1].oGet(1);
        genMatrix[9] = lightProject[1].oGet(2);
        genMatrix[13] = lightProject[1].oGet(3);

        genMatrix[2] = 0;
        genMatrix[6] = 0;
        genMatrix[10] = 0;
        genMatrix[14] = 0;

        genMatrix[3] = lightProject[2].oGet(0);
        genMatrix[7] = lightProject[2].oGet(1);
        genMatrix[11] = lightProject[2].oGet(2);
        genMatrix[15] = lightProject[2].oGet(3);

        myGlMultMatrix(genMatrix, backEnd.lightTextureMatrix, finale);

        lightProject[0].oSet(0, finale[0]);
        lightProject[0].oSet(1, finale[4]);
        lightProject[0].oSet(2, finale[8]);
        lightProject[0].oSet(3, finale[12]);

        lightProject[1].oSet(0, finale[1]);
        lightProject[1].oSet(1, finale[5]);
        lightProject[1].oSet(2, finale[9]);
        lightProject[1].oSet(3, finale[13]);
    }

    /*
     ================
     RB_PrepareStageTexturing
     ================
     */
    public static void RB_PrepareStageTexturing(final shaderStage_t pStage, final drawSurf_s surf, idDrawVert ac) {
        // set privatePolygonOffset if necessary
        if (pStage.privatePolygonOffset != 0) {
            qglEnable(GL_POLYGON_OFFSET_FILL);
            qglPolygonOffset(r_offsetFactor.GetFloat(), r_offsetUnits.GetFloat() * pStage.privatePolygonOffset);
        }

        // set the texture matrix if needed
        if (pStage.texture.hasMatrix) {
            RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture);
        }

        // texgens
        if (pStage.texture.texgen == TG_DIFFUSE_CUBE) {
            qglTexCoordPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.normalOffset());
        }
        if (pStage.texture.texgen == TG_SKYBOX_CUBE || pStage.texture.texgen == TG_WOBBLESKY_CUBE) {
            qglTexCoordPointer(3, GL_FLOAT, 0, vertexCache.Position(surf.dynamicTexCoords));
        }
        if (pStage.texture.texgen == TG_SCREEN) {
            qglEnable(GL_TEXTURE_GEN_S);
            qglEnable(GL_TEXTURE_GEN_T);
            qglEnable(GL_TEXTURE_GEN_Q);

            float[] mat = new float[16], plane = new float[4];
            myGlMultMatrix(surf.space.modelViewMatrix, backEnd.viewDef.projectionMatrix, mat);

            plane[0] = mat[0];
            plane[1] = mat[4];
            plane[2] = mat[8];
            plane[3] = mat[12];
            qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane);

            plane[0] = mat[1];
            plane[1] = mat[5];
            plane[2] = mat[9];
            plane[3] = mat[13];
            qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane);

            plane[0] = mat[3];
            plane[1] = mat[7];
            plane[2] = mat[11];
            plane[3] = mat[15];
            qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane);
        }

        if (pStage.texture.texgen == TG_SCREEN2) {
            qglEnable(GL_TEXTURE_GEN_S);
            qglEnable(GL_TEXTURE_GEN_T);
            qglEnable(GL_TEXTURE_GEN_Q);

            float[] mat = new float[16], plane = new float[4];
            myGlMultMatrix(surf.space.modelViewMatrix, backEnd.viewDef.projectionMatrix, mat);

            plane[0] = mat[0];
            plane[1] = mat[4];
            plane[2] = mat[8];
            plane[3] = mat[12];
            qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane);

            plane[0] = mat[1];
            plane[1] = mat[5];
            plane[2] = mat[9];
            plane[3] = mat[13];
            qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane);

            plane[0] = mat[3];
            plane[1] = mat[7];
            plane[2] = mat[11];
            plane[3] = mat[15];
            qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane);
        }

        if (pStage.texture.texgen == TG_GLASSWARP) {
            if (tr.backEndRenderer == BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, FPROG_GLASSWARP);
                qglEnable(GL_FRAGMENT_PROGRAM_ARB);

                GL_SelectTexture(2);
                globalImages.scratchImage.Bind();

                GL_SelectTexture(1);
                globalImages.scratchImage2.Bind();

                qglEnable(GL_TEXTURE_GEN_S);
                qglEnable(GL_TEXTURE_GEN_T);
                qglEnable(GL_TEXTURE_GEN_Q);

                float[] mat = new float[16], plane = new float[4];
                myGlMultMatrix(surf.space.modelViewMatrix, backEnd.viewDef.projectionMatrix, mat);

                plane[0] = mat[0];
                plane[1] = mat[4];
                plane[2] = mat[8];
                plane[3] = mat[12];
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane);

                plane[0] = mat[1];
                plane[1] = mat[5];
                plane[2] = mat[9];
                plane[3] = mat[13];
                qglTexGenfv(GL_T, GL_OBJECT_PLANE, plane);

                plane[0] = mat[3];
                plane[1] = mat[7];
                plane[2] = mat[11];
                plane[3] = mat[15];
                qglTexGenfv(GL_Q, GL_OBJECT_PLANE, plane);

                GL_SelectTexture(0);
            }
        }

        if (pStage.texture.texgen == TG_REFLECT_CUBE) {
            if (tr.backEndRenderer == BE_ARB2) {
                // see if there is also a bump map specified
                final shaderStage_t bumpStage = surf.material.GetBumpStage();
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    GL_SelectTexture(1);
                    bumpStage.texture.image[0].Bind();
                    GL_SelectTexture(0);

                    qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset());
                    qglVertexAttribPointerARB(10, 3, GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_1());
                    qglVertexAttribPointerARB(9, 3, GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_0());

                    qglEnableVertexAttribArrayARB(9);
                    qglEnableVertexAttribArrayARB(10);
                    qglEnableClientState(GL_NORMAL_ARRAY);

                    // Program env 5, 6, 7, 8 have been set in RB_SetProgramEnvironmentSpace
                    qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, FPROG_BUMPY_ENVIRONMENT);
                    qglEnable(GL_FRAGMENT_PROGRAM_ARB);
                    qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, VPROG_BUMPY_ENVIRONMENT);
                    qglEnable(GL_VERTEX_PROGRAM_ARB);
                } else {
                    // per-pixel reflection mapping without a normal map
                    qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset());
                    qglEnableClientState(GL_NORMAL_ARRAY);

                    qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, FPROG_ENVIRONMENT);
                    qglEnable(GL_FRAGMENT_PROGRAM_ARB);
                    qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, VPROG_ENVIRONMENT);
                    qglEnable(GL_VERTEX_PROGRAM_ARB);
                }
            } else {
                qglEnable(GL_TEXTURE_GEN_S);
                qglEnable(GL_TEXTURE_GEN_T);
                qglEnable(GL_TEXTURE_GEN_R);
                qglTexGenf(GL_S, GL_TEXTURE_GEN_MODE, GL_REFLECTION_MAP/*_EXT*/);
                qglTexGenf(GL_T, GL_TEXTURE_GEN_MODE, GL_REFLECTION_MAP/*_EXT*/);
                qglTexGenf(GL_R, GL_TEXTURE_GEN_MODE, GL_REFLECTION_MAP/*_EXT*/);
                qglEnableClientState(GL_NORMAL_ARRAY);
                qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset());

                qglMatrixMode(GL_TEXTURE);
                float[] mat = new float[16];

                R_TransposeGLMatrix(backEnd.viewDef.worldSpace.modelViewMatrix, mat);

                qglLoadMatrixf(mat);
                qglMatrixMode(GL_MODELVIEW);
            }
        }
    }

    public static void RB_FinishStageTexturing(final shaderStage_t pStage, final drawSurf_s surf, idDrawVert ac) {
        DBG_RB_FinishStageTexturing++;
        // unset privatePolygonOffset if necessary
        if (pStage.privatePolygonOffset != 0 && !surf.material.TestMaterialFlag(MF_POLYGONOFFSET)) {
            qglDisable(GL_POLYGON_OFFSET_FILL);
        }

        if (pStage.texture.texgen == TG_DIFFUSE_CUBE || pStage.texture.texgen == TG_SKYBOX_CUBE
                || pStage.texture.texgen == TG_WOBBLESKY_CUBE) {
            qglTexCoordPointer(2, GL_FLOAT, idDrawVert.BYTES, ac.stOffset());
        }

        if (pStage.texture.texgen == TG_SCREEN) {
            qglDisable(GL_TEXTURE_GEN_S);
            qglDisable(GL_TEXTURE_GEN_T);
            qglDisable(GL_TEXTURE_GEN_Q);
        }
        if (pStage.texture.texgen == TG_SCREEN2) {
            qglDisable(GL_TEXTURE_GEN_S);
            qglDisable(GL_TEXTURE_GEN_T);
            qglDisable(GL_TEXTURE_GEN_Q);
        }

        if (pStage.texture.texgen == TG_GLASSWARP) {
            if (tr.backEndRenderer == BE_ARB2 /*|| tr.backEndRenderer == BE_NV30*/) {
                GL_SelectTexture(2);
                globalImages.BindNull();

                GL_SelectTexture(1);
                if (pStage.texture.hasMatrix) {
                    RB_LoadShaderTextureMatrix(surf.shaderRegisters, pStage.texture);
                }
                qglDisable(GL_TEXTURE_GEN_S);
                qglDisable(GL_TEXTURE_GEN_T);
                qglDisable(GL_TEXTURE_GEN_Q);
                qglDisable(GL_FRAGMENT_PROGRAM_ARB);
                globalImages.BindNull();
                GL_SelectTexture(0);
            }
        }

        if (pStage.texture.texgen == TG_REFLECT_CUBE) {
            if (tr.backEndRenderer == BE_ARB2) {
                // see if there is also a bump map specified
                final shaderStage_t bumpStage = surf.material.GetBumpStage();
                if (bumpStage != null) {
                    // per-pixel reflection mapping with bump mapping
                    GL_SelectTexture(1);
                    globalImages.BindNull();
                    GL_SelectTexture(0);

                    qglDisableVertexAttribArrayARB(9);
                    qglDisableVertexAttribArrayARB(10);
                } else {
                    // per-pixel reflection mapping without bump mapping
                }

                qglDisableClientState(GL_NORMAL_ARRAY);
                qglDisable(GL_FRAGMENT_PROGRAM_ARB);
                qglDisable(GL_VERTEX_PROGRAM_ARB);
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, 0);
            } else {
                qglDisable(GL_TEXTURE_GEN_S);
                qglDisable(GL_TEXTURE_GEN_T);
                qglDisable(GL_TEXTURE_GEN_R);
                qglTexGenf(GL_S, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
                qglTexGenf(GL_T, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
                qglTexGenf(GL_R, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
                qglDisableClientState(GL_NORMAL_ARRAY);

                qglMatrixMode(GL_TEXTURE);
                qglLoadIdentity();
                qglMatrixMode(GL_MODELVIEW);
            }
        }

        if (pStage.texture.hasMatrix) {
//            DBG_hasMatrix++;
//            System.out.println(DBG_RB_FinishStageTexturing + "---" + DBG_hasMatrix);
            qglMatrixMode(GL_TEXTURE);
            qglLoadIdentity();
            qglMatrixMode(GL_MODELVIEW);
            if (qglGetError() != 0) {
                System.err.println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
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
    public static void RB_STD_FillDepthBuffer(drawSurf_s[] drawSurfs, int numDrawSurfs) {
        // if we are just doing 2D rendering, no need to fill the depth buffer
        if (NOT(backEnd.viewDef.viewEntitys)) {
            return;
        }

        RB_LogComment("---------- RB_STD_FillDepthBuffer ----------\n");

        // enable the second texture for mirror plane clipping if needed
        if (backEnd.viewDef.numClipPlanes != 0) {
            GL_SelectTexture(1);
            globalImages.alphaNotchImage.Bind();
            qglDisableClientState(GL_TEXTURE_COORD_ARRAY);
            qglEnable(GL_TEXTURE_GEN_S);
            qglTexCoord2f(1, 0.5f);
        }

        // the first texture will be used for alpha tested surfaces
        GL_SelectTexture(0);
        qglEnableClientState(GL_TEXTURE_COORD_ARRAY);

        // decal surfaces may enable polygon offset
        qglPolygonOffset(r_offsetFactor.GetFloat(), r_offsetUnits.GetFloat());

        GL_State(GLS_DEPTHFUNC_LESS);

        // Enable stencil test if we are going to be using it for shadows.
        // If we didn't do this, it would be legal behavior to get z fighting
        // from the ambient pass and the light passes.
        qglEnable(GL_STENCIL_TEST);
        qglStencilFunc(GL_ALWAYS, 1, 255);

        RB_RenderDrawSurfListWithFunction(drawSurfs, numDrawSurfs, RB_T_FillDepthBuffer.INSTANCE);

        if (backEnd.viewDef.numClipPlanes != 0) {
            GL_SelectTexture(1);
            globalImages.BindNull();
            qglDisable(GL_TEXTURE_GEN_S);
            GL_SelectTexture(0);
        }
    }

    /*
     ==================
     RB_SetProgramEnvironment

     Sets variables that can be used by all vertex programs
     ==================
     */
    public static void RB_SetProgramEnvironment() {
        FloatBuffer parm = BufferUtils.createFloatBuffer(4);
        int pot;

        if (!glConfig.ARBVertexProgramAvailable) {
            return;
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
        int w = backEnd.viewDef.viewport.x2 - backEnd.viewDef.viewport.x1 + 1;
        pot = globalImages.currentRenderImage.uploadWidth.getVal();
        parm.put(0, (float) w / pot);

        int h = backEnd.viewDef.viewport.y2 - backEnd.viewDef.viewport.y1 + 1;
        pot = globalImages.currentRenderImage.uploadHeight.getVal();
        parm.put(1, (float) h / pot);

        parm.put(2, 0f);
        parm.put(3, 1f);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 0, parm);
//}

        qglProgramEnvParameter4fvARB(GL_FRAGMENT_PROGRAM_ARB, 0, parm);

        // window coord to 0.0 to 1.0 conversion
        parm.put(0, 1.0f / w);
        parm.put(1, 1.0f / h);
        parm.put(2, 0f);
        parm.put(3, 1f);
        qglProgramEnvParameter4fvARB(GL_FRAGMENT_PROGRAM_ARB, 1, parm);

        //
        // set eye position in global space
        //
        parm.put(0, backEnd.viewDef.renderView.vieworg.oGet(0));
        parm.put(1, backEnd.viewDef.renderView.vieworg.oGet(1));
        parm.put(2, backEnd.viewDef.renderView.vieworg.oGet(2));
        parm.put(3, 1f);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 1, parm);

    }

    /*
     ==================
     RB_SetProgramEnvironmentSpace

     Sets variables related to the current space that can be used by all vertex programs
     ==================
     */
    public static void RB_SetProgramEnvironmentSpace() {
        if (!glConfig.ARBVertexProgramAvailable) {
            return;
        }

        final viewEntity_s space = backEnd.currentSpace;
        FloatBuffer parm = BufferUtils.createFloatBuffer(4);

        // set eye position in local space
        R_GlobalPointToLocal(space.modelMatrix, backEnd.viewDef.renderView.vieworg, parm);
        parm.put(3, 1.0f);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 5, parm);

        // we need the model matrix without it being combined with the view matrix
        // so we can transform local vectors to global coordinates
        parm.put(0, space.modelMatrix[0]);
        parm.put(1, space.modelMatrix[4]);
        parm.put(2, space.modelMatrix[8]);
        parm.put(3, space.modelMatrix[12]);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 6, parm);
        parm.put(0, space.modelMatrix[1]);
        parm.put(1, space.modelMatrix[5]);
        parm.put(2, space.modelMatrix[9]);
        parm.put(3, space.modelMatrix[13]);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 7, parm);
        parm.put(0, space.modelMatrix[2]);
        parm.put(1, space.modelMatrix[6]);
        parm.put(2, space.modelMatrix[10]);
        parm.put(3, space.modelMatrix[14]);
        qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, 8, parm);
    }

    public static void RB_STD_T_RenderShaderPasses(final drawSurf_s surf) {
        int stage;
        DBG_RB_STD_T_RenderShaderPasses++;
        idMaterial shader;
        shaderStage_t pStage;
        final float[] regs;
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        srfTriangles_s tri;

        tri = surf.geo;
        shader = surf.material;

        if (!shader.HasAmbient()) {
            return;
        }

        if (shader.IsPortalSky()) {
            return;
        }

        // change the matrix if needed
        if (surf.space != backEnd.currentSpace) {
            qglLoadMatrixf(surf.space.modelViewMatrix);
            backEnd.currentSpace = surf.space;
            RB_SetProgramEnvironmentSpace();
        }

        // change the scissor if needed
        if (r_useScissor.GetBool() && !backEnd.currentScissor.Equals(surf.scissorRect)) {
            backEnd.currentScissor = surf.scissorRect;
            qglScissor(backEnd.viewDef.viewport.x1 + backEnd.currentScissor.x1,
                    backEnd.viewDef.viewport.y1 + backEnd.currentScissor.y1,
                    backEnd.currentScissor.x2 + 1 - backEnd.currentScissor.x1,
                    backEnd.currentScissor.y2 + 1 - backEnd.currentScissor.y1);
        }

        // some deforms may disable themselves by setting numIndexes = 0
        if (0 == tri.numIndexes) {
            return;
        }

        if (NOT(tri.ambientCache)) {
            common.Printf("RB_T_RenderShaderPasses: !tri.ambientCache\n");
            return;
        }

        // get the expressions for conditionals / color / texcoords
        regs = surf.shaderRegisters;

        // set face culling appropriately
        GL_Cull(shader.GetCullType());

        // set polygon offset if necessary
        if (shader.TestMaterialFlag(MF_POLYGONOFFSET)) {
            qglEnable(GL_POLYGON_OFFSET_FILL);
            qglPolygonOffset(r_offsetFactor.GetFloat(), r_offsetUnits.GetFloat() * shader.GetPolygonOffset());
        }

        if (surf.space.weaponDepthHack) {
            RB_EnterWeaponDepthHack();
        }

        if (surf.space.modelDepthHack != 0.0f) {
            RB_EnterModelDepthHack(surf.space.modelDepthHack);
        }

        idDrawVert ac = new idDrawVert(vertexCache.Position(tri.ambientCache));//TODO:figure out how to work these damn casts. EDIT:easy peasy.
        qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset());
        qglTexCoordPointer(2, GL_FLOAT, idDrawVert.BYTES, ac.stOffset());

        for (stage = 0; stage < shader.GetNumStages(); stage++) {
            if (stage == 2 || stage == 3) {
//                System.out.printf("RB_STD_T_RenderShaderPasses(%d)\n", DBG_RB_STD_T_RenderShaderPasses++);
//                continue;//HACKME::4:our blending doesn't seem to work properly.
            }

            pStage = shader.GetStage(stage);

//            if(pStage.texture.image[0].imgName.equals("guis/assets/caverns/testmat2"))continue;
            // check the enable condition
            if (regs[pStage.conditionRegister] == 0) {
                continue;
            }

            // skip the stages involved in lighting
            if (pStage.lighting != SL_AMBIENT) {
                continue;
            }

            // skip if the stage is ( GL_ZERO, GL_ONE ), which is used for some alpha masks
            if ((pStage.drawStateBits & (GLS_SRCBLEND_BITS | GLS_DSTBLEND_BITS)) == (GLS_SRCBLEND_ZERO | GLS_DSTBLEND_ONE)) {
                continue;
            }

            // see if we are a new-style stage
            newShaderStage_t newStage = pStage.newStage;
            if (newStage != null) {
                //--------------------------
                //
                // new style stages
                //
                //--------------------------

                // completely skip the stage if we don't have the capability
                if (tr.backEndRenderer != BE_ARB2) {
                    continue;
                }
                if (r_skipNewAmbient.GetBool()) {
                    continue;
                }
                qglColorPointer(4, GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset());
                qglVertexAttribPointerARB(9, 3, GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_0());
                qglVertexAttribPointerARB(10, 3, GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_1());
                qglNormalPointer(GL_FLOAT, idDrawVert.BYTES, ac.normalOffset());

                qglEnableClientState(GL_COLOR_ARRAY);
                qglEnableVertexAttribArrayARB(9);
                qglEnableVertexAttribArrayARB(10);
                qglEnableClientState(GL_NORMAL_ARRAY);

                GL_State(pStage.drawStateBits);

                qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, newStage.vertexProgram);
                qglEnable(GL_VERTEX_PROGRAM_ARB);

                // megaTextures bind a lot of images and set a lot of parameters
                if (newStage.megaTexture != null) {
                    newStage.megaTexture.SetMappingForSurface(tri);
                    final idVec3 localViewer = new idVec3();
                    R_GlobalPointToLocal(surf.space.modelMatrix, backEnd.viewDef.renderView.vieworg, localViewer);
                    newStage.megaTexture.BindForViewOrigin(localViewer);
                }

                for (int i = 0; i < newStage.numVertexParms; i++) {
                    FloatBuffer parm = BufferUtils.createFloatBuffer(4);
                    parm.put(0, regs[newStage.vertexParms[i][0]]);
                    parm.put(1, regs[newStage.vertexParms[i][1]]);
                    parm.put(2, regs[newStage.vertexParms[i][2]]);
                    parm.put(3, regs[newStage.vertexParms[i][3]]);
                    qglProgramLocalParameter4fvARB(GL_VERTEX_PROGRAM_ARB, i, parm);
                }

                for (int i = 0; i < newStage.numFragmentProgramImages; i++) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        GL_SelectTexture(i);
                        newStage.fragmentProgramImages[i].Bind();
                    }
                }
                qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, newStage.fragmentProgram);
                qglEnable(GL_FRAGMENT_PROGRAM_ARB);

                // draw it
                RB_DrawElementsWithCounters(tri);

                for (int i = 1; i < newStage.numFragmentProgramImages; i++) {
                    if (newStage.fragmentProgramImages[i] != null) {
                        GL_SelectTexture(i);
                        globalImages.BindNull();
                    }
                }
                if (newStage.megaTexture != null) {
                    newStage.megaTexture.Unbind();
                }

                GL_SelectTexture(0);

                qglDisable(GL_VERTEX_PROGRAM_ARB);
                qglDisable(GL_FRAGMENT_PROGRAM_ARB);
                // Fixme: Hack to get around an apparent bug in ATI drivers.  Should remove as soon as it gets fixed.
                qglBindProgramARB(GL_VERTEX_PROGRAM_ARB, 0);

                qglDisableClientState(GL_COLOR_ARRAY);
                qglDisableVertexAttribArrayARB(9);
                qglDisableVertexAttribArrayARB(10);
                qglDisableClientState(GL_NORMAL_ARRAY);
                continue;
            }

            //--------------------------
            //
            // old style stages
            //
            //--------------------------
            // set the color
            color.put(0, regs[pStage.color.registers[0]]);
            color.put(1, regs[pStage.color.registers[1]]);
            color.put(2, regs[pStage.color.registers[2]]);
            color.put(3, regs[pStage.color.registers[3]]);

            // skip the entire stage if an add would be black
            if ((pStage.drawStateBits & (GLS_SRCBLEND_BITS | GLS_DSTBLEND_BITS)) == (GLS_SRCBLEND_ONE | GLS_DSTBLEND_ONE)
                    && color.get(0) <= 0 && color.get(1) <= 0 && color.get(2) <= 0) {
                continue;
            }

            // skip the entire stage if a blend would be completely transparent
            if ((pStage.drawStateBits & (GLS_SRCBLEND_BITS | GLS_DSTBLEND_BITS)) == (GLS_SRCBLEND_SRC_ALPHA | GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
                    && color.get(3) <= 0) {
                continue;
            }

            // select the vertex color source
            if (pStage.vertexColor == SVC_IGNORE) {
                qglColor4f(color.get(0), color.get(1), color.get(2), color.get(3));//marquis logo
//                System.out.printf("qglColor4f(%f, %f, %f, %f)\n",color.get(0), color.get(1), color.get(2), color.get(3));
            } else {
                qglColorPointer(4, GL_UNSIGNED_BYTE, idDrawVert.BYTES, /*(void *)&*/ ac.colorOffset());
                qglEnableClientState(GL_COLOR_ARRAY);

                if (pStage.vertexColor == SVC_INVERSE_MODULATE) {
                    GL_TexEnv(GL_COMBINE_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB_ARB, GL_MODULATE);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_RGB_ARB, GL_TEXTURE);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE1_RGB_ARB, GL_PRIMARY_COLOR_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND0_RGB_ARB, GL_SRC_COLOR);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND1_RGB_ARB, GL_ONE_MINUS_SRC_COLOR);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_RGB_SCALE_ARB, 1);
                }

                // for vertex color and modulated color, we need to enable a second
                // texture stage
                if (color.get(0) != 1 || color.get(1) != 1 || color.get(2) != 1 || color.get(3) != 1) {
                    GL_SelectTexture(1);

                    globalImages.whiteImage.Bind();
                    GL_TexEnv(GL_COMBINE_ARB);

                    qglTexEnvfv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, color);

                    qglTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB_ARB, GL_MODULATE);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_RGB_ARB, GL_PREVIOUS_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE1_RGB_ARB, GL_CONSTANT_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND0_RGB_ARB, GL_SRC_COLOR);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND1_RGB_ARB, GL_SRC_COLOR);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_RGB_SCALE_ARB, 1);

                    qglTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_ALPHA_ARB, GL_MODULATE);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_ALPHA_ARB, GL_PREVIOUS_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_SOURCE1_ALPHA_ARB, GL_CONSTANT_ARB);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND0_ALPHA_ARB, GL_SRC_ALPHA);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_OPERAND1_ALPHA_ARB, GL_SRC_ALPHA);
                    qglTexEnvi(GL_TEXTURE_ENV, GL_ALPHA_SCALE, 1);

                    GL_SelectTexture(0);
                }
            }

            // bind the texture
            RB_BindVariableStageImage(pStage.texture, regs);

            // set the state
            GL_State(pStage.drawStateBits);//marquisDeSade

            RB_PrepareStageTexturing(pStage, surf, ac);

            // draw it
            RB_DrawElementsWithCounters(tri);

            RB_FinishStageTexturing(pStage, surf, ac);

            if (pStage.vertexColor != SVC_IGNORE) {
                qglDisableClientState(GL_COLOR_ARRAY);

                GL_SelectTexture(1);
                GL_TexEnv(GL_MODULATE);
                globalImages.BindNull();
                GL_SelectTexture(0);
                GL_TexEnv(GL_MODULATE);
            }
        }

        // reset polygon offset
        if (shader.TestMaterialFlag(MF_POLYGONOFFSET)) {
            qglDisable(GL_POLYGON_OFFSET_FILL);
        }
        if (surf.space.weaponDepthHack || surf.space.modelDepthHack != 0.0f) {
            RB_LeaveDepthHack();
        }
    }

    /*
     =====================
     RB_STD_DrawShaderPasses

     Draw non-light dependent passes
     =====================
     */
    public static int RB_STD_DrawShaderPasses(drawSurf_s[] drawSurfs, int numDrawSurfs) {
        int i;

        // only obey skipAmbient if we are rendering a view
        if (backEnd.viewDef.viewEntitys != null && r_skipAmbient.GetBool()) {
            return numDrawSurfs;
        }

        RB_LogComment("---------- RB_STD_DrawShaderPasses ----------\n");

        // if we are about to draw the first surface that needs
        // the rendering in a texture, copy it over
        if (drawSurfs[0].material.GetSort() >= SS_POST_PROCESS) {
            if (r_skipPostProcess.GetBool()) {
                return 0;
            }

            // only dump if in a 3d view
            if (backEnd.viewDef.viewEntitys != null && tr.backEndRenderer == BE_ARB2) {
                CInt imageWidth = new CInt(backEnd.viewDef.viewport.x2 - backEnd.viewDef.viewport.x1 + 1);
                CInt imageHeight = new CInt(backEnd.viewDef.viewport.y2 - backEnd.viewDef.viewport.y1 + 1);
                globalImages.currentRenderImage.CopyFramebuffer(backEnd.viewDef.viewport.x1, backEnd.viewDef.viewport.y1,
                        imageWidth, imageHeight, true);
            }
            backEnd.currentRenderCopied = true;
        }

        GL_SelectTexture(1);
        globalImages.BindNull();

        GL_SelectTexture(0);
        qglEnableClientState(GL_TEXTURE_COORD_ARRAY);

        RB_SetProgramEnvironment();

        // we don't use RB_RenderDrawSurfListWithFunction()
        // because we want to defer the matrix load because many
        // surfaces won't draw any ambient passes
        backEnd.currentSpace = null;
        for (i = 0; i < numDrawSurfs /*&& numDrawSurfs == 5*/; i++) {
            if (drawSurfs[i].material.SuppressInSubview()) {
                continue;
            }

            if (backEnd.viewDef.isXraySubview && drawSurfs[i].space.entityDef != null) {
                if (drawSurfs[i].space.entityDef.parms.xrayIndex != 2) {
                    continue;
                }
            }

            // we need to draw the post process shaders after we have drawn the fog lights
            if (drawSurfs[i].material.GetSort() >= SS_POST_PROCESS
                    && !backEnd.currentRenderCopied) {
                break;
            }

            RB_STD_T_RenderShaderPasses(drawSurfs[i]);
        }

        GL_Cull(CT_FRONT_SIDED);
        qglColor3f(1, 1, 1);

        return i;
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
    public static void RB_StencilShadowPass(final drawSurf_s drawSurfs) {
        if (!r_shadows.GetBool()) {
            return;
        }

        if (NOT(drawSurfs)) {
            return;
        }

        RB_LogComment("---------- RB_StencilShadowPass ----------\n");

        globalImages.BindNull();
        qglDisableClientState(GL_TEXTURE_COORD_ARRAY);

        // for visualizing the shadows
        if (r_showShadows.GetInteger() != 0) {
            if (r_showShadows.GetInteger() == 2) {
                // draw filled in
                GL_State(GLS_DEPTHMASK | GLS_SRCBLEND_ONE | GLS_DSTBLEND_ONE | GLS_DEPTHFUNC_LESS);
            } else {
                // draw as lines, filling the depth buffer
                GL_State(GLS_SRCBLEND_ONE | GLS_DSTBLEND_ZERO | GLS_POLYMODE_LINE | GLS_DEPTHFUNC_ALWAYS);
            }
        } else {
            // don't write to the color buffer, just the stencil buffer
            GL_State(GLS_DEPTHMASK | GLS_COLORMASK | GLS_ALPHAMASK | GLS_DEPTHFUNC_LESS);
        }

        if (r_shadowPolygonFactor.GetFloat() != 0 || r_shadowPolygonOffset.GetFloat() != 0) {
            qglPolygonOffset(r_shadowPolygonFactor.GetFloat(), -r_shadowPolygonOffset.GetFloat());
            qglEnable(GL_POLYGON_OFFSET_FILL);
        }

        qglStencilFunc(GL_ALWAYS, 1, 255);

        if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest.GetBool()) {
            qglEnable(GL_DEPTH_BOUNDS_TEST_EXT);
        }

        RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_Shadow.INSTANCE);

        GL_Cull(CT_FRONT_SIDED);

        if (r_shadowPolygonFactor.GetFloat() != 0 || r_shadowPolygonOffset.GetFloat() != 0) {
            qglDisable(GL_POLYGON_OFFSET_FILL);
        }

        if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest.GetBool()) {
            qglDisable(GL_DEPTH_BOUNDS_TEST_EXT);
        }

        qglEnableClientState(GL_TEXTURE_COORD_ARRAY);

        qglStencilFunc(GL_GEQUAL, 128, 255);
        qglStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }

    /*
     =====================
     RB_BlendLight

     Dual texture together the falloff and projection texture with a blend
     mode to the framebuffer, instead of interacting with the surface texture
     =====================
     */
    public static void RB_BlendLight(final drawSurf_s drawSurfs, final drawSurf_s drawSurfs2) {
        idMaterial lightShader;
        shaderStage_t stage;
        int i;
        final float[] regs;

        if (NOT(drawSurfs)) {
            return;
        }
        if (r_skipBlendLights.GetBool()) {
            return;
        }
        RB_LogComment("---------- RB_BlendLight ----------\n");

        lightShader = backEnd.vLight.lightShader;
        regs = backEnd.vLight.shaderRegisters;

        // texture 1 will get the falloff texture
        GL_SelectTexture(1);
        qglDisableClientState(GL_TEXTURE_COORD_ARRAY);
        qglEnable(GL_TEXTURE_GEN_S);
        qglTexCoord2f(0, 0.5f);
        backEnd.vLight.falloffImage.Bind();

        // texture 0 will get the projected texture
        GL_SelectTexture(0);
        qglDisableClientState(GL_TEXTURE_COORD_ARRAY);
        qglEnable(GL_TEXTURE_GEN_S);
        qglEnable(GL_TEXTURE_GEN_T);
        qglEnable(GL_TEXTURE_GEN_Q);

        for (i = 0; i < lightShader.GetNumStages(); i++) {
            stage = lightShader.GetStage(i);

            if (0 == regs[stage.conditionRegister]) {
                continue;
            }

            GL_State(GLS_DEPTHMASK | stage.drawStateBits | GLS_DEPTHFUNC_EQUAL);

            GL_SelectTexture(0);
            stage.texture.image[0].Bind();

            if (stage.texture.hasMatrix) {
                RB_LoadShaderTextureMatrix(regs, stage.texture);
            }

            // get the modulate values from the light, including alpha, unlike normal lights
            backEnd.lightColor[0] = regs[stage.color.registers[0]];
            backEnd.lightColor[1] = regs[stage.color.registers[1]];
            backEnd.lightColor[2] = regs[stage.color.registers[2]];
            backEnd.lightColor[3] = regs[stage.color.registers[3]];
            qglColor4fv(backEnd.lightColor);

            RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BlendLight.INSTANCE);
            RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BlendLight.INSTANCE);

            if (stage.texture.hasMatrix) {
                GL_SelectTexture(0);
                qglMatrixMode(GL_TEXTURE);
                qglLoadIdentity();
                qglMatrixMode(GL_MODELVIEW);
            }
        }

        GL_SelectTexture(1);
        qglDisable(GL_TEXTURE_GEN_S);
        globalImages.BindNull();

        GL_SelectTexture(0);
        qglDisable(GL_TEXTURE_GEN_S);
        qglDisable(GL_TEXTURE_GEN_T);
        qglDisable(GL_TEXTURE_GEN_Q);
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
    public static void RB_FogPass(final drawSurf_s drawSurfs, final drawSurf_s drawSurfs2) {
        srfTriangles_s frustumTris;
        drawSurf_s ds = new drawSurf_s();//memset( &ds, 0, sizeof( ds ) );
        idMaterial lightShader;
        shaderStage_t stage;
        final float[] regs;

        RB_LogComment("---------- RB_FogPass ----------\n");

        // create a surface for the light frustom triangles, which are oriented drawn side out
        frustumTris = backEnd.vLight.frustumTris;

        // if we ran out of vertex cache memory, skip it
        if (NOT(frustumTris.ambientCache)) {
            return;
        }
        ds.space = backEnd.viewDef.worldSpace;
        ds.geo = frustumTris;
        ds.scissorRect = new idScreenRect(backEnd.viewDef.scissor);

        // find the current color and density of the fog
        lightShader = backEnd.vLight.lightShader;
        regs = backEnd.vLight.shaderRegisters;
        // assume fog shaders have only a single stage
        stage = lightShader.GetStage(0);

        backEnd.lightColor[0] = regs[stage.color.registers[0]];
        backEnd.lightColor[1] = regs[stage.color.registers[1]];
        backEnd.lightColor[2] = regs[stage.color.registers[2]];
        backEnd.lightColor[3] = regs[stage.color.registers[3]];

        qglColor3fv(backEnd.lightColor);

        // calculate the falloff planes
        float a;

        // if they left the default value on, set a fog distance of 500
        if (backEnd.lightColor[3] <= 1.0) {
            a = -0.5f / DEFAULT_FOG_DISTANCE;
        } else {
            // otherwise, distance = alpha color
            a = -0.5f / backEnd.lightColor[3];
        }

        GL_State(GLS_DEPTHMASK | GLS_SRCBLEND_SRC_ALPHA | GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA | GLS_DEPTHFUNC_EQUAL);

        // texture 0 is the falloff image
        GL_SelectTexture(0);
        globalImages.fogImage.Bind();
        //GL_Bind( tr.whiteImage );
        qglDisableClientState(GL_TEXTURE_COORD_ARRAY);
        qglEnable(GL_TEXTURE_GEN_S);
        qglEnable(GL_TEXTURE_GEN_T);
        qglTexCoord2f(0.5f, 0.5f);        // make sure Q is set

        fogPlanes[0].oSet(0, a * backEnd.viewDef.worldSpace.modelViewMatrix[2]);
        fogPlanes[0].oSet(1, a * backEnd.viewDef.worldSpace.modelViewMatrix[6]);
        fogPlanes[0].oSet(2, a * backEnd.viewDef.worldSpace.modelViewMatrix[10]);
        fogPlanes[0].oSet(3, a * backEnd.viewDef.worldSpace.modelViewMatrix[14]);

        fogPlanes[1].oSet(0, a * backEnd.viewDef.worldSpace.modelViewMatrix[0]);
        fogPlanes[1].oSet(1, a * backEnd.viewDef.worldSpace.modelViewMatrix[4]);
        fogPlanes[1].oSet(2, a * backEnd.viewDef.worldSpace.modelViewMatrix[8]);
        fogPlanes[1].oSet(3, a * backEnd.viewDef.worldSpace.modelViewMatrix[12]);

        // texture 1 is the entering plane fade correction
        GL_SelectTexture(1);
        globalImages.fogEnterImage.Bind();
        qglDisableClientState(GL_TEXTURE_COORD_ARRAY);
        qglEnable(GL_TEXTURE_GEN_S);
        qglEnable(GL_TEXTURE_GEN_T);

        // T will get a texgen for the fade plane, which is always the "top" plane on unrotated lights
        fogPlanes[2].oSet(0, 0.001f * backEnd.vLight.fogPlane.oGet(0));
        fogPlanes[2].oSet(1, 0.001f * backEnd.vLight.fogPlane.oGet(1));
        fogPlanes[2].oSet(2, 0.001f * backEnd.vLight.fogPlane.oGet(2));
        fogPlanes[2].oSet(3, 0.001f * backEnd.vLight.fogPlane.oGet(3));

        // S is based on the view origin
        float s = backEnd.viewDef.renderView.vieworg.oMultiply(fogPlanes[2].Normal()) + fogPlanes[2].oGet(3);

        fogPlanes[3].oSet(0, 0);
        fogPlanes[3].oSet(1, 0);
        fogPlanes[3].oSet(2, 0);
        fogPlanes[3].oSet(3, FOG_ENTER + s);

        qglTexCoord2f(FOG_ENTER + s, FOG_ENTER);

        // draw it
        RB_RenderDrawSurfChainWithFunction(drawSurfs, RB_T_BasicFog.INSTANCE);
        RB_RenderDrawSurfChainWithFunction(drawSurfs2, RB_T_BasicFog.INSTANCE);

        // the light frustum bounding planes aren't in the depth buffer, so use depthfunc_less instead
        // of depthfunc_equal
        GL_State(GLS_DEPTHMASK | GLS_SRCBLEND_SRC_ALPHA | GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA | GLS_DEPTHFUNC_LESS);
        GL_Cull(CT_BACK_SIDED);
        RB_RenderDrawSurfChainWithFunction(ds, RB_T_BasicFog.INSTANCE);
        GL_Cull(CT_FRONT_SIDED);

        GL_SelectTexture(1);
        qglDisable(GL_TEXTURE_GEN_S);
        qglDisable(GL_TEXTURE_GEN_T);
        globalImages.BindNull();

        GL_SelectTexture(0);
        qglDisable(GL_TEXTURE_GEN_S);
        qglDisable(GL_TEXTURE_GEN_T);
    }

    /*
     ==================
     RB_STD_FogAllLights
     ==================
     */
    public static void RB_STD_FogAllLights() {
        viewLight_s vLight;

        if (r_skipFogLights.GetBool() || r_showOverDraw.GetInteger() != 0
                || backEnd.viewDef.isXraySubview /* dont fog in xray mode*/) {
            return;
        }

        RB_LogComment("---------- RB_STD_FogAllLights ----------\n");

        qglDisable(GL_STENCIL_TEST);

        for (vLight = backEnd.viewDef.viewLights; vLight != null; vLight = vLight.next) {
            backEnd.vLight = vLight;

            if (!vLight.lightShader.IsFogLight() && !vLight.lightShader.IsBlendLight()) {
                continue;
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
                RB_FogPass(vLight.globalInteractions[0], vLight.localInteractions[0]);
            } else if (vLight.lightShader.IsBlendLight()) {
                RB_BlendLight(vLight.globalInteractions[0], vLight.localInteractions[0]);
            }
            qglDisable(GL_STENCIL_TEST);
        }

        qglEnable(GL_STENCIL_TEST);
    }

    /*
     ==================
     RB_STD_LightScale

     Perform extra blending passes to multiply the entire buffer by
     a floating point value
     ==================
     */
    public static void RB_STD_LightScale() {
        float v, f;

        if (1.0f == backEnd.overBright) {
            return;
        }

        if (r_skipLightScale.GetBool()) {
            return;
        }

        RB_LogComment("---------- RB_STD_LightScale ----------\n");

        // the scissor may be smaller than the viewport for subviews
        if (r_useScissor.GetBool()) {
            qglScissor(backEnd.viewDef.viewport.x1 + backEnd.viewDef.scissor.x1,
                    backEnd.viewDef.viewport.y1 + backEnd.viewDef.scissor.y1,
                    backEnd.viewDef.scissor.x2 - backEnd.viewDef.scissor.x1 + 1,
                    backEnd.viewDef.scissor.y2 - backEnd.viewDef.scissor.y1 + 1);
            backEnd.currentScissor = backEnd.viewDef.scissor;
        }

        // full screen blends
        qglLoadIdentity();
        qglMatrixMode(GL_PROJECTION);
        qglPushMatrix();
        qglLoadIdentity();
        qglOrtho(0, 1, 0, 1, -1, 1);

        GL_State(GLS_SRCBLEND_DST_COLOR | GLS_DSTBLEND_SRC_COLOR);
        GL_Cull(CT_TWO_SIDED);    // so mirror views also get it
        globalImages.BindNull();
        qglDisable(GL_DEPTH_TEST);
        qglDisable(GL_STENCIL_TEST);

        v = 1;
        while (Math.abs(v - backEnd.overBright) > 0.01) {    // a little extra slop
            f = backEnd.overBright / v;
            f /= 2;
            if (f > 1) {
                f = 1;
            }
            qglColor3f(f, f, f);
            v = v * f * 2;

            qglBegin(GL_QUADS);
            qglVertex2f(0, 0);
            qglVertex2f(0, 1);
            qglVertex2f(1, 1);
            qglVertex2f(1, 0);
            qglEnd();
        }

        qglPopMatrix();
        qglEnable(GL_DEPTH_TEST);
        qglMatrixMode(GL_MODELVIEW);
        GL_Cull(CT_FRONT_SIDED);
    }

    /*
     =============
     RB_STD_DrawView

     =============
     */
    public static void RB_STD_DrawView() {
        drawSurf_s[] drawSurfs;
        int numDrawSurfs;

        RB_LogComment("---------- RB_STD_DrawView ----------\n");

        backEnd.depthFunc = GLS_DEPTHFUNC_EQUAL;

        drawSurfs = backEnd.viewDef.drawSurfs;
        numDrawSurfs = backEnd.viewDef.numDrawSurfs;

        // clear the z buffer, set the projection matrix, etc
        RB_BeginDrawingView();

        // decide how much overbrighting we are going to do
        RB_DetermineLightScale();

        // fill the depth buffer and clear color buffer to black except on
        // subviews
        RB_STD_FillDepthBuffer(drawSurfs, numDrawSurfs);

        // main light renderer
        switch (tr.backEndRenderer) {
            case BE_ARB:
                RB_ARB_DrawInteractions();
                break;
            case BE_ARB2:
                RB_ARB2_DrawInteractions();
//                break;
//            case BE_NV20:
//                RB_NV20_DrawInteractions();
//                break;
//            case BE_NV10:
//                RB_NV10_DrawInteractions();
//                break;
//            case BE_R200:
//                RB_R200_DrawInteractions();
//                break;
        }

        // disable stencil shadow test
        qglStencilFunc(GL_ALWAYS, 128, 255);

        // uplight the entire screen to crutch up not having better blending range
        RB_STD_LightScale();

        // now draw any non-light dependent shading passes
        int processed = RB_STD_DrawShaderPasses(drawSurfs, numDrawSurfs);

        // fob and blend lights
        RB_STD_FogAllLights();

        // now draw any post-processing effects using _currentRender
        if (processed < numDrawSurfs) {
            RB_STD_DrawShaderPasses(Arrays.copyOfRange(drawSurfs, processed, numDrawSurfs), numDrawSurfs - processed);
        }

        RB_RenderDebugTools(drawSurfs, numDrawSurfs);
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
    public static class RB_T_FillDepthBuffer extends triFunc {

        static final triFunc INSTANCE = new RB_T_FillDepthBuffer();

        private RB_T_FillDepthBuffer() {
        }

        @Override
        void run(final drawSurf_s surf) {
            int stage;
            idMaterial shader;
            shaderStage_t pStage;
            float[] regs;
            float[] color = new float[4];
            srfTriangles_s tri;

            tri = surf.geo;
            shader = surf.material;

            // update the clip plane if needed
            if (backEnd.viewDef.numClipPlanes != 0 && surf.space != backEnd.currentSpace) {
                GL_SelectTexture(1);

                idPlane plane = new idPlane();

                R_GlobalPlaneToLocal(surf.space.modelMatrix, backEnd.viewDef.clipPlanes[0], plane);
                plane.oPluSet(3, 0.5f);    // the notch is in the middle
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, plane.ToFloatPtr());
                GL_SelectTexture(0);
            }

            if (!shader.IsDrawn()) {
                return;
            }

            // some deforms may disable themselves by setting numIndexes = 0
            if (0 == tri.numIndexes) {
                return;
            }

            // translucent surfaces don't put anything in the depth buffer and don't
            // test against it, which makes them fail the mirror clip plane operation
            if (shader.Coverage() == MC_TRANSLUCENT) {
                return;
            }

            if (NOT(tri.ambientCache)) {
                common.Printf("RB_T_FillDepthBuffer: !tri.ambientCache\n");
                return;
            }

            // get the expressions for conditionals / color / texcoords
            regs = surf.shaderRegisters;

            // if all stages of a material have been conditioned off, don't do anything
            for (stage = 0; stage < shader.GetNumStages(); stage++) {
                pStage = shader.GetStage(stage);
                // check the stage enable condition
                if (regs[pStage.conditionRegister] != 0) {
                    break;
                }
            }
            if (stage == shader.GetNumStages()) {
                return;
            }

            // set polygon offset if necessary
            if (shader.TestMaterialFlag(MF_POLYGONOFFSET)) {
                qglEnable(GL_POLYGON_OFFSET_FILL);
                qglPolygonOffset(r_offsetFactor.GetFloat(), r_offsetUnits.GetFloat() * shader.GetPolygonOffset());
            }

            // subviews will just down-modulate the color buffer by overbright
            if (shader.GetSort() == SS_SUBVIEW) {
                GL_State(GLS_SRCBLEND_DST_COLOR | GLS_DSTBLEND_ZERO | GLS_DEPTHFUNC_LESS);
                color[0] = color[1] = color[2] = (1.0f / backEnd.overBright);
                color[3] = 1;
            } else {
                // others just draw black
                color[0] = color[1] = color[2] = 0;
                color[3] = 1;
            }

            idDrawVert ac = new idDrawVert(vertexCache.Position(tri.ambientCache));//TODO:figure out how to work these damn casts.
            qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset());
            qglTexCoordPointer(2, GL_FLOAT, idDrawVert.BYTES, /*reinterpret_cast<void *>*/ ac.stOffset());

            boolean drawSolid = shader.Coverage() == MC_OPAQUE;

            // we may have multiple alpha tested stages
            if (shader.Coverage() == MC_PERFORATED) {
                // if the only alpha tested stages are condition register omitted,
                // draw a normal opaque surface
                boolean didDraw = false;

                qglEnable(GL_ALPHA_TEST);
                // perforated surfaces may have multiple alpha tested stages
                for (stage = 0; stage < shader.GetNumStages(); stage++) {
                    pStage = shader.GetStage(stage);

                    if (!pStage.hasAlphaTest) {
                        continue;
                    }

                    // check the stage enable condition
                    if (regs[pStage.conditionRegister] == 0) {
                        continue;
                    }

                    // if we at least tried to draw an alpha tested stage,
                    // we won't draw the opaque surface
                    didDraw = true;

                    // set the alpha modulate
                    color[3] = regs[pStage.color.registers[3]];

                    // skip the entire stage if alpha would be black
                    if (color[3] <= 0) {
                        continue;
                    }
                    qglColor4fv(color);

                    qglAlphaFunc(GL_GREATER, regs[pStage.alphaTestRegister]);

                    // bind the texture
                    pStage.texture.image[0].Bind();

                    // set texture matrix and texGens
                    RB_PrepareStageTexturing(pStage, surf, ac);

                    // draw it
                    RB_DrawElementsWithCounters(tri);

                    RB_FinishStageTexturing(pStage, surf, ac);
                }
                qglDisable(GL_ALPHA_TEST);
                if (!didDraw) {
                    drawSolid = true;
                }
            }

            // draw the entire surface solid
            if (drawSolid) {
                qglColor4fv(color);
                globalImages.whiteImage.Bind();

                // draw it
                RB_DrawElementsWithCounters(tri);
            }

            // reset polygon offset
            if (shader.TestMaterialFlag(MF_POLYGONOFFSET)) {
                qglDisable(GL_POLYGON_OFFSET_FILL);
            }

            // reset blending
            if (shader.GetSort() == SS_SUBVIEW) {
                GL_State(GLS_DEPTHFUNC_LESS);
            }
        }
    }

    /*
     =====================
     RB_T_Shadow

     the shadow volumes face INSIDE
     =====================
     */
    public static class RB_T_Shadow extends triFunc {

        static final triFunc INSTANCE = new RB_T_Shadow();

        private RB_T_Shadow() {
        }

        @Override
        void run(final drawSurf_s surf) {
            srfTriangles_s tri;//TODO: should this be an array?

            // set the light position if we are using a vertex program to project the rear surfaces
            if (tr.backEndRendererHasVertexPrograms && r_useShadowVertexProgram.GetBool()
                    && surf.space != backEnd.currentSpace) {
                idVec4 localLight = new idVec4();
                FloatBuffer lightBuffer = BufferUtils.createFloatBuffer(4);

                R_GlobalPointToLocal(surf.space.modelMatrix, backEnd.vLight.globalLightOrigin, localLight);
                lightBuffer.put(localLight.ToFloatPtr()).rewind();//localLight.w = 0.0f;
                qglProgramEnvParameter4fvARB(GL_VERTEX_PROGRAM_ARB, PP_LIGHT_ORIGIN, lightBuffer);
            }

            tri = surf.geo;

            if (NOT(tri.shadowCache)) {
                return;
            }

            qglVertexPointer(4, GL_FLOAT, shadowCache_s.BYTES, vertexCache.Position(tri.shadowCache).getInt());

            // we always draw the sil planes, but we may not need to draw the front or rear caps
            int numIndexes;
            boolean external = false;

            if (0 == r_useExternalShadows.GetInteger()) {
                numIndexes = tri.numIndexes;
            } else if (r_useExternalShadows.GetInteger() == 2) { // force to no caps for testing
                numIndexes = tri.numShadowIndexesNoCaps;
            } else if (0 == (surf.dsFlags & DSF_VIEW_INSIDE_SHADOW)) {
                // if we aren't inside the shadow projection, no caps are ever needed needed
                numIndexes = tri.numShadowIndexesNoCaps;
                external = true;
            } else if (!backEnd.vLight.viewInsideLight && (0 == (surf.geo.shadowCapPlaneBits & SHADOW_CAP_INFINITE))) {
                // if we are inside the shadow projection, but outside the light, and drawing
                // a non-infinite shadow, we can skip some caps
                if ((backEnd.vLight.viewSeesShadowPlaneBits & surf.geo.shadowCapPlaneBits) != 0) {
                    // we can see through a rear cap, so we need to draw it, but we can skip the
                    // caps on the actual surface
                    numIndexes = tri.numShadowIndexesNoFrontCaps;
                } else {
                    // we don't need to draw any caps
                    numIndexes = tri.numShadowIndexesNoCaps;
                }
                external = true;
            } else {
                // must draw everything
                numIndexes = tri.numIndexes;
            }

            // set depth bounds
            if (glConfig.depthBoundsTestAvailable && r_useDepthBoundsTest.GetBool()) {
                qglDepthBoundsEXT(surf.scissorRect.zmin, surf.scissorRect.zmax);
            }

            // debug visualization
            if (r_showShadows.GetInteger() != 0) {
                if (r_showShadows.GetInteger() == 3) {
                    if (external) {
                        qglColor3f(0.1f / backEnd.overBright, 1 / backEnd.overBright, 0.1f / backEnd.overBright);
                    } else {
                        // these are the surfaces that require the reverse
                        qglColor3f(1 / backEnd.overBright, 0.1f / backEnd.overBright, 0.1f / backEnd.overBright);
                    }
                } else {
                    // draw different color for turboshadows
                    if ((surf.geo.shadowCapPlaneBits & SHADOW_CAP_INFINITE) != 0) {
                        if (numIndexes == tri.numIndexes) {
                            qglColor3f(1 / backEnd.overBright, 0.1f / backEnd.overBright, 0.1f / backEnd.overBright);
                        } else {
                            qglColor3f(1 / backEnd.overBright, 0.4f / backEnd.overBright, 0.1f / backEnd.overBright);
                        }
                    } else {
                        if (numIndexes == tri.numIndexes) {
                            qglColor3f(0.1f / backEnd.overBright, 1 / backEnd.overBright, 0.1f / backEnd.overBright);
                        } else if (numIndexes == tri.numShadowIndexesNoFrontCaps) {
                            qglColor3f(0.1f / backEnd.overBright, 1 / backEnd.overBright, 0.6f / backEnd.overBright);
                        } else {
                            qglColor3f(0.6f / backEnd.overBright, 1 / backEnd.overBright, 0.1f / backEnd.overBright);
                        }
                    }
                }

                qglStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
                qglDisable(GL_STENCIL_TEST);
                GL_Cull(CT_TWO_SIDED);
                RB_DrawShadowElementsWithCounters(tri, numIndexes);
                GL_Cull(CT_FRONT_SIDED);
                qglEnable(GL_STENCIL_TEST);

                return;
            }

            // patent-free work around
            if (!external) {
                // "preload" the stencil buffer with the number of volumes
                // that get clipped by the near or far clip plane
                qglStencilOp(GL_KEEP, tr.stencilDecr, tr.stencilDecr);
                GL_Cull(CT_FRONT_SIDED);
                RB_DrawShadowElementsWithCounters(tri, numIndexes);
                qglStencilOp(GL_KEEP, tr.stencilIncr, tr.stencilIncr);
                GL_Cull(CT_BACK_SIDED);
                RB_DrawShadowElementsWithCounters(tri, numIndexes);
            }

            // traditional depth-pass stencil shadows
            qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilIncr);
            GL_Cull(CT_FRONT_SIDED);
            RB_DrawShadowElementsWithCounters(tri, numIndexes);

            qglStencilOp(GL_KEEP, GL_KEEP, tr.stencilDecr);
            GL_Cull(CT_BACK_SIDED);
            RB_DrawShadowElementsWithCounters(tri, numIndexes);
        }
    }

//=========================================================================================

    /*
     =====================
     RB_T_BlendLight

     =====================
     */
    public static class RB_T_BlendLight extends triFunc {

        static final triFunc INSTANCE = new RB_T_BlendLight();

        private RB_T_BlendLight() {
        }

        @Override
        void run(final drawSurf_s surf) {
            srfTriangles_s tri;

            tri = surf.geo;

            if (backEnd.currentSpace != surf.space) {
                idPlane[] lightProject = new idPlane[4];
                int i;

                for (i = 0; i < 4; i++) {
                    lightProject[i] = new idPlane();
                    R_GlobalPlaneToLocal(surf.space.modelMatrix, backEnd.vLight.lightProject[i], lightProject[i]);
                }

                GL_SelectTexture(0);
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, lightProject[0].ToFloatPtr());
                qglTexGenfv(GL_T, GL_OBJECT_PLANE, lightProject[1].ToFloatPtr());
                qglTexGenfv(GL_Q, GL_OBJECT_PLANE, lightProject[2].ToFloatPtr());

                GL_SelectTexture(1);
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, lightProject[3].ToFloatPtr());
            }

            // this gets used for both blend lights and shadow draws
            if (tri.ambientCache != null) {
                idDrawVert ac = new idDrawVert(vertexCache.Position(tri.ambientCache));//TODO:figure out how to work these damn casts.
                qglVertexPointer(3, GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset());
            } else if (tri.shadowCache != null) {
                shadowCache_s sc = new shadowCache_s(vertexCache.Position(tri.shadowCache));//TODO:figure out how to work these damn casts.
                qglVertexPointer(3, GL_FLOAT, shadowCache_s.BYTES, sc.xyz.ToFloatPtr());
            }

            RB_DrawElementsWithCounters(tri);
        }
    }

//=========================================================================================

    /*
     =====================
     RB_T_BasicFog

     =====================
     */
    public static class RB_T_BasicFog extends triFunc {

        static final triFunc INSTANCE = new RB_T_BasicFog();

        private RB_T_BasicFog() {
        }

        @Override
        void run(final drawSurf_s surf) {
            if (backEnd.currentSpace != surf.space) {
                idPlane local = new idPlane();

                GL_SelectTexture(0);

                R_GlobalPlaneToLocal(surf.space.modelMatrix, fogPlanes[0], local);
                local.oPluSet(3, 0.5f);
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, local.ToFloatPtr());

//		R_GlobalPlaneToLocal( surf.space.modelMatrix, fogPlanes[1], local );
//		local[3] += 0.5;
                local.oSet(0, local.oSet(1, local.oSet(2, local.oSet(3, 0.5f))));
                qglTexGenfv(GL_T, GL_OBJECT_PLANE, local.ToFloatPtr());

                GL_SelectTexture(1);

                // GL_S is constant per viewer
                R_GlobalPlaneToLocal(surf.space.modelMatrix, fogPlanes[2], local);
                local.oPluSet(3, FOG_ENTER);
                qglTexGenfv(GL_T, GL_OBJECT_PLANE, local.ToFloatPtr());

                R_GlobalPlaneToLocal(surf.space.modelMatrix, fogPlanes[3], local);
                qglTexGenfv(GL_S, GL_OBJECT_PLANE, local.ToFloatPtr());
            }

            RB_T_RenderTriangleSurface.INSTANCE.run(surf);
        }
    }
}
