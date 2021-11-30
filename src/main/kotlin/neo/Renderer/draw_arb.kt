package neo.Renderer

import neo.Renderer.*
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.Model.lightingCache_s
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_render.DrawInteraction
import neo.TempDump
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Vector.idVec4
import org.lwjgl.opengl.ARBTextureEnvCombine
import org.lwjgl.opengl.ARBTextureEnvDot3
import org.lwjgl.opengl.GL11

/**
 *
 */
object draw_arb {
    /*

     with standard calls, we can't do bump mapping or vertex colors with
     shader colors

     2 texture units:

     falloff
     --
     light cube
     bump
     --
     light projection
     diffuse


     3 texture units:

     light cube
     bump
     --
     falloff
     light projection
     diffuse


     5 texture units:

     light cube
     bump
     falloff
     light projection
     diffuse

     */
    /*
     ==================
     RB_CreateDrawInteractions
     ==================
     */
    fun RB_CreateDrawInteractions(surfs: drawSurf_s?) {
        var surf = surfs
        if (TempDump.NOT(surf)) {
            return
        }

        // force a space calculation
        tr_local.backEnd.currentSpace = null
        if (RenderSystem_init.r_useTripleTextureARB.GetBool() && tr_local.glConfig.maxTextureUnits >= 3) {
            while (surf != null) {

                // break it up into multiple primitive draw interactions if necessary
                tr_render.RB_CreateSingleDrawInteractions(surf, RB_ARB_DrawThreeTextureInteraction.INSTANCE)
                surf = surf.nextOnLight
            }
        } else {
            while (surf != null) {

                // break it up into multiple primitive draw interactions if necessary
                tr_render.RB_CreateSingleDrawInteractions(surf, RB_ARB_DrawInteraction.INSTANCE)
                surf = surf.nextOnLight
            }
        }
    }

    /*
     ==================
     RB_RenderViewLight

     ==================
     */
    fun RB_RenderViewLight(vLight: viewLight_s?) {
        tr_local.backEnd.vLight = vLight

        // do fogging later
        if (vLight.lightShader.IsFogLight()) {
            return
        }
        if (vLight.lightShader.IsBlendLight()) {
            return
        }
        tr_backend.RB_LogComment("---------- RB_RenderViewLight 0x%p ----------\n", vLight)

        // clear the stencil buffer if needed
        if (vLight.globalShadows[0] != null || vLight.localShadows[0] != null) {
            tr_local.backEnd.currentScissor = idScreenRect(vLight.scissorRect)
            if (RenderSystem_init.r_useScissor.GetBool()) {
                qgl.qglScissor(
                    tr_local.backEnd.viewDef.viewport.x1 + tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.viewDef.viewport.y1 + tr_local.backEnd.currentScissor.y1,
                    tr_local.backEnd.currentScissor.x2 + 1 - tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.currentScissor.y2 + 1 - tr_local.backEnd.currentScissor.y1
                )
            }
            qgl.qglClear(GL11.GL_STENCIL_BUFFER_BIT)
        } else {
            // no shadows, so no need to read or write the stencil buffer
            // we might in theory want to use GL_ALWAYS instead of disabling
            // completely, to satisfy the invarience rules
            qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
        }
        tr_local.backEnd.depthFunc = tr_local.GLS_DEPTHFUNC_EQUAL
        draw_common.RB_StencilShadowPass(vLight.globalShadows[0])
        draw_arb.RB_CreateDrawInteractions(vLight.localInteractions[0])
        draw_common.RB_StencilShadowPass(vLight.localShadows[0])
        draw_arb.RB_CreateDrawInteractions(vLight.globalInteractions[0])
        if (RenderSystem_init.r_skipTranslucent.GetBool()) {
            return
        }

        // disable stencil testing for translucent interactions, because
        // the shadow isn't calculated at their point, and the shadow
        // behind them may be depth fighting with a back side, so there
        // isn't any reasonable thing to do
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
        tr_local.backEnd.depthFunc = tr_local.GLS_DEPTHFUNC_LESS
        draw_arb.RB_CreateDrawInteractions(vLight.translucentInteractions[0])
    }

    /*
     ==================
     RB_ARB_DrawInteractions
     ==================
     */
    fun RB_ARB_DrawInteractions() {
        qgl.qglEnable(GL11.GL_STENCIL_TEST)
        var vLight = tr_local.backEnd.viewDef.viewLights
        while (vLight != null) {
            draw_arb.RB_RenderViewLight(vLight)
            vLight = vLight.next
        }
    }

    /*
     ==================
     RB_ARB_DrawInteraction

     backEnd.vLight

     backEnd.depthFunc must be equal for alpha tested surfaces to work right,
     it is set to lessThan for blended transparent surfaces

     ==================
     */
    class RB_ARB_DrawInteraction private constructor() : DrawInteraction() {
        override fun run(din: drawInteraction_t?) {
            val surf = din.surf
            val tri = din.surf.geo

            // set the vertex arrays, which may not all be enabled on a given pass
            val ac =
                idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts.
            qgl.qglVertexPointer(3, GL11.GL_FLOAT, 0 /*sizeof(idDrawVert)*/, ac.xyz.ToFloatPtr())
            tr_backend.GL_SelectTexture(0)
            qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, 0 /*sizeof(idDrawVert)*/,  /*(void *)&*/ac.st.ToFloatPtr())

            //-----------------------------------------------------
            //
            // bump / falloff
            //
            //-----------------------------------------------------
            // render light falloff * bumpmap lighting
            //
            // draw light falloff to the alpha channel
            //
            tr_backend.GL_State(tr_local.GLS_COLORMASK or tr_local.GLS_DEPTHMASK or tr_local.backEnd.depthFunc)
            qgl.qglColor3f(1f, 1f, 1f)
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, din.lightProjection[3].ToFloatPtr())
            qgl.qglTexCoord2f(0f, 0.5f)

// ATI R100 can't do partial texgens
            val NO_MIXED_TEXGEN = true
            if (NO_MIXED_TEXGEN) {
                val plane = idVec4(0, 0, 0, 0.5f)
                //plane[0] = 0;
//plane[1] = 0;
//plane[2] = 0;
//plane[3] = 0.5;
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane.ToFloatPtr())
                plane.set(0, 0f)
                plane.set(1, 0f)
                plane.set(2, 0f)
                plane.set(3, 1f)
                qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
                qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, plane.ToFloatPtr())
            }
            din.lightFalloffImage.Bind()

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            if (NO_MIXED_TEXGEN) {
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
                qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
            }

//if (false){
//GL_State( GLS_SRCBLEND_ONE | GLS_DSTBLEND_ZERO | GLS_DEPTHMASK
//			| backEnd.depthFunc );
//// the texccords are the non-normalized vector towards the light origin
//GL_SelectTexture( 0 );
//globalImages.normalCubeMapImage.Bind();
//qglEnableClientState( GL_TEXTURE_COORD_ARRAY );
//qglTexCoordPointer( 3, GL_FLOAT, sizeof( lightingCache_t ), ((lightingCache_t *)vertexCache.Position(tri.lightingCache)).localLightVector.ToFloatPtr() );
//// draw it
//RB_DrawElementsWithCounters( tri );
//return;
//}
            // we can't do bump mapping with standard calls, so skip it
            if (tr_local.glConfig.envDot3Available && tr_local.glConfig.cubeMapAvailable) {
                //
                // draw the bump map result onto the alpha channel
                //
                tr_backend.GL_State(
                    tr_local.GLS_SRCBLEND_DST_ALPHA or tr_local.GLS_DSTBLEND_ZERO or tr_local.GLS_COLORMASK or tr_local.GLS_DEPTHMASK
                            or tr_local.backEnd.depthFunc
                )

                // texture 0 will be the per-surface bump map
                tr_backend.GL_SelectTexture(0)
                qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
                //	FIXME: matrix work!	RB_BindStageTexture( surfaceRegs, &surfaceStage.texture, surf );
                din.bumpImage.Bind()

                // texture 1 is the normalization cube map
                // the texccords are the non-normalized vector towards the light origin
                tr_backend.GL_SelectTexture(1)
                if (din.ambientLight != 0) {
                    Image.globalImages.ambientNormalMap.Bind() // fixed value
                } else {
                    Image.globalImages.normalCubeMapImage.Bind()
                }
                qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
                //TODO:figure out how to work these damn casts.
                val c = lightingCache_s(VertexCache.vertexCache.Position(tri.lightingCache))
                qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, 0 /*sizeof(lightingCache_s)*/, c.localLightVector.ToFloatPtr())

                // I just want alpha = Dot( texture0, texture1 )
                tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
                qgl.qglTexEnvi(
                    GL11.GL_TEXTURE_ENV,
                    ARBTextureEnvCombine.GL_COMBINE_RGB_ARB,
                    ARBTextureEnvDot3.GL_DOT3_RGBA_ARB
                )
                qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB, GL11.GL_TEXTURE)
                qgl.qglTexEnvi(
                    GL11.GL_TEXTURE_ENV,
                    ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                    ARBTextureEnvCombine.GL_PREVIOUS_ARB
                )
                qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL11.GL_SRC_COLOR)
                qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB, GL11.GL_SRC_COLOR)
                qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
                qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, 1)

                // draw it
                tr_render.RB_DrawElementsWithCounters(tri)
                tr_backend.GL_TexEnv(GL11.GL_MODULATE)
                Image.globalImages.BindNull()
                qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
                tr_backend.GL_SelectTexture(0)
                //		RB_FinishStageTexture( &surfaceStage.texture, surf );
            }

            //-----------------------------------------------------
            //
            // projected light / surface color for diffuse maps
            //
            //-----------------------------------------------------
            // don't trash alpha
            tr_backend.GL_State(
                tr_local.GLS_SRCBLEND_DST_ALPHA or tr_local.GLS_DSTBLEND_ONE or tr_local.GLS_ALPHAMASK or tr_local.GLS_DEPTHMASK
                        or tr_local.backEnd.depthFunc
            )

            // texture 0 will get the surface color texture
            tr_backend.GL_SelectTexture(0)

            // select the vertex color source
            if (din.vertexColor == stageVertexColor_t.SVC_IGNORE) {
                qgl.qglColor4fv(din.diffuseColor.ToFloatPtr())
            } else {
                // FIXME: does this not get diffuseColor blended in?
                qgl.qglColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0 /*sizeof(idDrawVert)*/,  /*(void *)&*/ac.color)
                qgl.qglEnableClientState(GL11.GL_COLOR_ARRAY)
                if (din.vertexColor == stageVertexColor_t.SVC_INVERSE_MODULATE) {
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
            }
            qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            // FIXME: does this not get the texture matrix?
//	RB_BindStageTexture( surfaceRegs, &surfaceStage.texture, surf );
            din.diffuseImage.Bind()

            // texture 1 will get the light projected texture
            tr_backend.GL_SelectTexture(1)
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, din.lightProjection[0].ToFloatPtr())
            qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, din.lightProjection[1].ToFloatPtr())
            qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, din.lightProjection[2].ToFloatPtr())
            din.lightImage.Bind()

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
            Image.globalImages.BindNull()
            tr_backend.GL_SelectTexture(0)
            if (din.vertexColor != stageVertexColor_t.SVC_IGNORE) {
                qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)
                tr_backend.GL_TexEnv(GL11.GL_MODULATE)
            }

//	RB_FinishStageTexture( &surfaceStage.texture, surf );
        }

        companion object {
            val INSTANCE: DrawInteraction? = RB_ARB_DrawInteraction()
        }
    }

    /*
     ==================
     RB_ARB_DrawThreeTextureInteraction

     Used by radeon R100 and Intel graphics parts

     backEnd.vLight

     backEnd.depthFunc must be equal for alpha tested surfaces to work right,
     it is set to lessThan for blended transparent surfaces

     ==================
     */
    class RB_ARB_DrawThreeTextureInteraction private constructor() : DrawInteraction() {
        override fun run(din: drawInteraction_t?) {
            val surf = din.surf
            val tri = din.surf.geo

            // set the vertex arrays, which may not all be enabled on a given pass
            val ac =
                idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts.
            qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.xyzOffset().toLong())
            tr_backend.GL_SelectTexture(0)
            qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, idDrawVert.Companion.BYTES, ac.stOffset().toLong())
            qgl.qglColor3f(1f, 1f, 1f)

            //
            // bump map dot cubeMap into the alpha channel
            //
            tr_backend.GL_State(
                tr_local.GLS_SRCBLEND_ONE or tr_local.GLS_DSTBLEND_ZERO or tr_local.GLS_COLORMASK or tr_local.GLS_DEPTHMASK
                        or tr_local.backEnd.depthFunc
            )

            // texture 0 will be the per-surface bump map
            tr_backend.GL_SelectTexture(0)
            qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            //	FIXME: matrix work!	RB_BindStageTexture( surfaceRegs, &surfaceStage.texture, surf );
            din.bumpImage.Bind()

            // texture 1 is the normalization cube map
            // the texccords are the non-normalized vector towards the light origin
            tr_backend.GL_SelectTexture(1)
            if (din.ambientLight != 0) {
                Image.globalImages.ambientNormalMap.Bind() // fixed value
            } else {
                Image.globalImages.normalCubeMapImage.Bind()
            }
            qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            val c =
                lightingCache_s(VertexCache.vertexCache.Position(tri.lightingCache)) //{//TODO:figure out how to work these damn casts.
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, 0 /*sizeof(lightingCache_s)*/, c.localLightVector.ToFloatPtr())

            // I just want alpha = Dot( texture0, texture1 )
            tr_backend.GL_TexEnv(ARBTextureEnvCombine.GL_COMBINE_ARB)
            qgl.qglTexEnvi(
                GL11.GL_TEXTURE_ENV,
                ARBTextureEnvCombine.GL_COMBINE_RGB_ARB,
                ARBTextureEnvDot3.GL_DOT3_RGBA_ARB
            )
            qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_SOURCE0_RGB_ARB, GL11.GL_TEXTURE)
            qgl.qglTexEnvi(
                GL11.GL_TEXTURE_ENV,
                ARBTextureEnvCombine.GL_SOURCE1_RGB_ARB,
                ARBTextureEnvCombine.GL_PREVIOUS_ARB
            )
            qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND0_RGB_ARB, GL11.GL_SRC_COLOR)
            qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_OPERAND1_RGB_ARB, GL11.GL_SRC_COLOR)
            qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, ARBTextureEnvCombine.GL_RGB_SCALE_ARB, 1)
            qgl.qglTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, 1)

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            tr_backend.GL_TexEnv(GL11.GL_MODULATE)
            Image.globalImages.BindNull()
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            tr_backend.GL_SelectTexture(0)
            //		RB_FinishStageTexture( &surfaceStage.texture, surf );

            //-----------------------------------------------------
            //
            // light falloff / projected light / surface color for diffuse maps
            //
            //-----------------------------------------------------
            // multiply result by alpha, but don't trash alpha
            tr_backend.GL_State(
                tr_local.GLS_SRCBLEND_DST_ALPHA or tr_local.GLS_DSTBLEND_ONE or tr_local.GLS_ALPHAMASK or tr_local.GLS_DEPTHMASK
                        or tr_local.backEnd.depthFunc
            )

            // texture 0 will get the surface color texture
            tr_backend.GL_SelectTexture(0)

            // select the vertex color source
            if (din.vertexColor == stageVertexColor_t.SVC_IGNORE) {
                qgl.qglColor4fv(din.diffuseColor.ToFloatPtr())
            } else {
                // FIXME: does this not get diffuseColor blended in?
                qgl.qglColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0 /*sizeof(idDrawVert)*/,  /*(void *)&*/ac.color)
                qgl.qglEnableClientState(GL11.GL_COLOR_ARRAY)
                if (din.vertexColor == stageVertexColor_t.SVC_INVERSE_MODULATE) {
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
            }
            qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            // FIXME: does this not get the texture matrix?
//	RB_BindStageTexture( surfaceRegs, &surfaceStage.texture, surf );
            din.diffuseImage.Bind()

            // texture 1 will get the light projected texture
            tr_backend.GL_SelectTexture(1)
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, din.lightProjection[0].ToFloatPtr())
            qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, din.lightProjection[1].ToFloatPtr())
            qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, din.lightProjection[2].ToFloatPtr())
            din.lightImage.Bind()

            // texture 2 will get the light falloff texture
            tr_backend.GL_SelectTexture(2)
            qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_Q)
            qgl.qglTexGenfv(GL11.GL_S, GL11.GL_OBJECT_PLANE, din.lightProjection[3].ToFloatPtr())
            val plane = idVec4()
            plane.set(0, 0f)
            plane.set(1, 0f)
            plane.set(2, 0f)
            plane.set(3, 0.5f)
            qgl.qglTexGenfv(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane.ToFloatPtr())
            plane.set(0, 0f)
            plane.set(1, 0f)
            plane.set(2, 0f)
            plane.set(3, 1f)
            qgl.qglTexGenfv(GL11.GL_Q, GL11.GL_OBJECT_PLANE, plane.ToFloatPtr())
            din.lightFalloffImage.Bind()

            // draw it
            tr_render.RB_DrawElementsWithCounters(tri)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
            Image.globalImages.BindNull()
            tr_backend.GL_SelectTexture(1)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglDisable(GL11.GL_TEXTURE_GEN_Q)
            Image.globalImages.BindNull()
            tr_backend.GL_SelectTexture(0)
            if (din.vertexColor != stageVertexColor_t.SVC_IGNORE) {
                qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)
                tr_backend.GL_TexEnv(GL11.GL_MODULATE)
            }

//	RB_FinishStageTexture( &surfaceStage.texture, surf );
        }

        companion object {
            val INSTANCE: DrawInteraction? = RB_ARB_DrawThreeTextureInteraction()
        }
    }
}