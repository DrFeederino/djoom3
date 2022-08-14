package neo.Renderer

import neo.Renderer.Cinematic.cinData_t
import neo.Renderer.Image.idImage
import neo.Renderer.Material.cullType_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Material.textureStage_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.drawSurfsCommand_t
import neo.Renderer.tr_local.viewLight_s
import neo.TempDump
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec4
import neo.sys.win_glimp
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

/**
 *
 */
object tr_render {
    /*

     back end scene + lights rendering functions

     */
    //    
    //    
    //    
    //    
    //    
    //    
    /*
     ================
     RB_DrawElementsWithCounters
     ================
     */
    var DEBUG_RB_DrawElementsWithCounters = 0

    /*
     ======================
     RB_BindVariableStageImage

     Handles generating a cinematic frame if needed
     ======================
     */
    private var DBG_RB_BindVariableStageImage = 0

    /*
     ======================
     RB_GetShaderTextureMatrix
     ======================
     */
    private var DBG_RB_GetShaderTextureMatrix = 0

    /*
     ======================
     RB_RenderDrawSurfChainWithFunction
     ======================
     */
    private var DBG_RB_RenderDrawSurfChainWithFunction = 0

    /*
     =================
     RB_SubmittInteraction
     =================
     */
    private var DBG_RB_SubmittInteraction = 0

    /*
     =================
     RB_DrawElementsImmediate

     Draws with immediate mode commands, which is going to be very slow.
     This should never happen if the vertex cache is operating properly.
     =================
     */
    fun RB_DrawElementsImmediate(tri: srfTriangles_s) {
        tr_local.backEnd.pc.c_drawElements++
        tr_local.backEnd.pc.c_drawIndexes += tri.numIndexes
        tr_local.backEnd.pc.c_drawVertexes += tri.numVerts
        if (tri.ambientSurface != null) {
            if (tri.indexes.contentEquals(tri.ambientSurface!!.indexes)) {
                tr_local.backEnd.pc.c_drawRefIndexes += tri.numIndexes
            }
            if (tri.verts == tri.ambientSurface!!.verts) {
                tr_local.backEnd.pc.c_drawRefVertexes += tri.numVerts
            }
        }
        qgl.qglBegin(GL11.GL_TRIANGLES)
        for (i in 0 until tri.numIndexes) {
            qgl.qglTexCoord2fv(tri.verts[tri.indexes[i]].st.ToFloatPtr())
            qgl.qglVertex3fv(tri.verts[tri.indexes[i]].xyz.ToFloatPtr())
        }
        qgl.qglEnd()
    }

    fun RB_DrawElementsWithCounters(tri: srfTriangles_s) {
        tr_local.backEnd.pc.c_drawElements++
        tr_local.backEnd.pc.c_drawIndexes += tri.numIndexes
        tr_local.backEnd.pc.c_drawVertexes += tri.numVerts
        DEBUG_RB_DrawElementsWithCounters++
        //        TempDump.printCallStack("" + DEBUG_RB_DrawElementsWithCounters);
        if (tri.ambientSurface != null) {
            if (tri.indexes.contentEquals(tri.ambientSurface!!.indexes)) {
                tr_local.backEnd.pc.c_drawRefIndexes += tri.numIndexes
            }
            if (tri.verts == tri.ambientSurface!!.verts) {
                tr_local.backEnd.pc.c_drawRefVertexes += tri.numVerts
            }
        }
        val count = if (RenderSystem_init.r_singleTriangle.GetBool()) 3 else tri.numIndexes
        if (tri.indexCache != null && RenderSystem_init.r_useIndexBuffers.GetBool()) {
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                count,
                Model.GL_INDEX_TYPE,
                VertexCache.vertexCache.Position(tri.indexCache)
            )
            tr_local.backEnd.pc.c_vboIndexes += tri.numIndexes
        } else {
            if (RenderSystem_init.r_useIndexBuffers.GetBool()) {
                VertexCache.vertexCache.UnbindIndex()
            }
            //            if(tri.DBG_count!=11)
            qgl.qglDrawElements(GL11.GL_TRIANGLES, count, Model.GL_INDEX_TYPE /*GL_UNSIGNED_INT*/, tri.indexes)
        }
    }

    /*
     ================
     RB_DrawShadowElementsWithCounters

     May not use all the indexes in the surface if caps are skipped
     ================
     */
    fun RB_DrawShadowElementsWithCounters(tri: srfTriangles_s, numIndexes: Int) {
        tr_local.backEnd.pc.c_shadowElements++
        tr_local.backEnd.pc.c_shadowIndexes += numIndexes
        tr_local.backEnd.pc.c_shadowVertexes += tri.numVerts
        if (tri.indexCache != null && RenderSystem_init.r_useIndexBuffers.GetBool()) {
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                if (RenderSystem_init.r_singleTriangle.GetBool()) 3 else numIndexes,
                Model.GL_INDEX_TYPE,
                VertexCache.vertexCache.Position(tri.indexCache!!)
            )
            tr_local.backEnd.pc.c_vboIndexes += numIndexes
        } else {
            if (RenderSystem_init.r_useIndexBuffers.GetBool()) {
                VertexCache.vertexCache.UnbindIndex()
            }
            qgl.qglDrawElements(
                GL11.GL_TRIANGLES,
                if (RenderSystem_init.r_singleTriangle.GetBool()) 3 else numIndexes,
                Model.GL_INDEX_TYPE,
                tri.indexes
            )
        }
    }

    /*
     ===============
     RB_RenderTriangleSurface

     Sets texcoord and vertex pointers
     ===============
     */
    fun RB_RenderTriangleSurface(tri: srfTriangles_s) {
        if (null == tri.ambientCache) {
            RB_DrawElementsImmediate(tri)
            return
        }
        val ac =
            idDrawVert(VertexCache.vertexCache.Position(tri.ambientCache)) //TODO:figure out how to work these damn casts.
        qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())
        qgl.qglTexCoordPointer(2, GL11.GL_FLOAT, idDrawVert.BYTES, ac.stOffset().toLong())
        RB_DrawElementsWithCounters(tri)
    }

    /*
     ===============
     RB_EnterWeaponDepthHack
     ===============
     */
    fun RB_EnterWeaponDepthHack() {
        qgl.qglDepthRange(0.0, 0.5)
        val matrix = FloatArray(16)

//	memcpy( matrix, backEnd.viewDef!!.projectionMatrix, sizeof( matrix ) );
        System.arraycopy(tr_local.backEnd.viewDef!!.projectionMatrix, 0, matrix, 0, matrix.size)
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ===============
     RB_EnterModelDepthHack
     ===============
     */
    fun RB_EnterModelDepthHack(depth: Float) {
        qgl.qglDepthRange(0.0, 1.0)
        val matrix = FloatArray(16)

//	memcpy( matrix, backEnd.viewDef!!.projectionMatrix, sizeof( matrix ) );
        System.arraycopy(tr_local.backEnd.viewDef!!.projectionMatrix, 0, matrix, 0, matrix.size)
        matrix[14] -= depth
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ===============
     RB_LeaveDepthHack
     ===============
     */
    fun RB_LeaveDepthHack() {
        qgl.qglDepthRange(0.0, 1.0)
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(tr_local.backEnd.viewDef!!.projectionMatrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    /*
     ====================
     RB_RenderDrawSurfListWithFunction

     The triangle functions can check backEnd.currentSpace != surf.space
     to see if they need to perform any new matrix setup.  The modelview
     matrix will already have been loaded, and backEnd.currentSpace will
     be updated after the triangle function completes.
     ====================
     */
    fun RB_RenderDrawSurfListWithFunction(drawSurfs: Array<drawSurf_s>, numDrawSurfs: Int, triFunc_: triFunc) {
        var i: Int
        var drawSurf: drawSurf_s?
        tr_local.backEnd.currentSpace = null
        i = 0
        while (i < numDrawSurfs) {
            drawSurf = drawSurfs[i]

            // change the matrix if needed
            if (drawSurf.space !== tr_local.backEnd.currentSpace) {
                qgl.qglLoadMatrixf(drawSurf.space.modelViewMatrix)
            }
            if (drawSurf.space.weaponDepthHack) {
                RB_EnterWeaponDepthHack()
            }
            if (drawSurf.space.modelDepthHack != 0.0f) {
                RB_EnterModelDepthHack(drawSurf.space.modelDepthHack)
            }

            // change the scissor if needed
            if (RenderSystem_init.r_useScissor.GetBool() && !tr_local.backEnd.currentScissor.Equals(drawSurf.scissorRect)) {
                tr_local.backEnd.currentScissor = drawSurf.scissorRect
                qgl.qglScissor(
                    tr_local.backEnd.viewDef!!.viewport.x1 + tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.viewDef!!.viewport.y1 + tr_local.backEnd.currentScissor.y1,
                    tr_local.backEnd.currentScissor.x2 + 1 - tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.currentScissor.y2 + 1 - tr_local.backEnd.currentScissor.y1
                )
            }

            // render it
            triFunc_.run(drawSurf)
            if (drawSurf.space.weaponDepthHack || drawSurf.space.modelDepthHack != 0.0f) {
                RB_LeaveDepthHack()
            }
            tr_local.backEnd.currentSpace = drawSurf.space
            i++
        }
    }

    fun RB_RenderDrawSurfChainWithFunction(drawSurfs: drawSurf_s, triFunc_: triFunc) {
        var drawSurf: drawSurf_s?
        DBG_RB_RenderDrawSurfChainWithFunction++
        tr_local.backEnd.currentSpace = null
        drawSurf = drawSurfs
        while (drawSurf != null) {

            // change the matrix if needed
            if (drawSurf.space !== tr_local.backEnd.currentSpace) {
                qgl.qglLoadMatrixf(drawSurf.space.modelViewMatrix)
            }
            if (drawSurf.space.weaponDepthHack) {
                RB_EnterWeaponDepthHack()
            }
            if (drawSurf.space.modelDepthHack != 0f) {
                RB_EnterModelDepthHack(drawSurf.space.modelDepthHack)
            }

            // change the scissor if needed
            if (RenderSystem_init.r_useScissor.GetBool() && !tr_local.backEnd.currentScissor.Equals(drawSurf.scissorRect)) {
                tr_local.backEnd.currentScissor = drawSurf.scissorRect
                qgl.qglScissor(
                    tr_local.backEnd.viewDef!!.viewport.x1 + tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.viewDef!!.viewport.y1 + tr_local.backEnd.currentScissor.y1,
                    tr_local.backEnd.currentScissor.x2 + 1 - tr_local.backEnd.currentScissor.x1,
                    tr_local.backEnd.currentScissor.y2 + 1 - tr_local.backEnd.currentScissor.y1
                )
            }

            // render it
            triFunc_.run(drawSurf)
            if (drawSurf.space.weaponDepthHack || drawSurf.space.modelDepthHack != 0.0f) {
                RB_LeaveDepthHack()
            }
            tr_local.backEnd.currentSpace = drawSurf.space
            drawSurf = drawSurf.nextOnLight
        }
    }

    fun RB_GetShaderTextureMatrix(
        shaderRegisters: FloatArray,
        texture: textureStage_t,
        matrix: FloatArray /*[16]*/
    ) {
        matrix[0] = shaderRegisters[texture.matrix[0][0]]
        matrix[4] = shaderRegisters[texture.matrix[0][1]]
        matrix[8] = 0f
        matrix[12] = shaderRegisters[texture.matrix[0][2]]
        DBG_RB_GetShaderTextureMatrix++
        //        System.out.println(">>>>>>" + DBG_RB_GetShaderTextureMatrix);
//        System.out.println("0:" + Arrays.toString(texture.matrix[0]));
//        System.out.println("1:" + Arrays.toString(texture.matrix[1]));
//        System.out.println("<<<<<<" + DBG_RB_GetShaderTextureMatrix);

        // we attempt to keep scrolls from generating incredibly large texture values, but
        // center rotations and center scales can still generate offsets that need to be > 1
        if (matrix[12] < -40 || matrix[12] > 40) {
            matrix[12] -= matrix[12]
        }
        matrix[1] = shaderRegisters[texture.matrix[1][0]]
        matrix[5] = shaderRegisters[texture.matrix[1][1]]
        matrix[9] = 0f
        matrix[13] = shaderRegisters[texture.matrix[1][2]]
        if (matrix[13] < -40 || matrix[13] > 40) {
            matrix[13] -= matrix[13]
        }
        matrix[2] = 0f
        matrix[6] = 0f
        matrix[10] = 1f
        matrix[14] = 0f
        matrix[3] = 0f
        matrix[7] = 0f
        matrix[11] = 0f
        matrix[15] = 1f
    }

    /*
     ======================
     RB_LoadShaderTextureMatrix
     ======================
     */
    fun RB_LoadShaderTextureMatrix(shaderRegisters: FloatArray, texture: textureStage_t) {
        val matrix = FloatArray(16)
        RB_GetShaderTextureMatrix(shaderRegisters, texture, matrix)
        //        final float[] m = matrix;
//        System.out.printf("RB_LoadShaderTextureMatrix("
//                + "%f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f)\n",
//                m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11], m[12], m[13], m[14], m[15]);

//        TempDump.printCallStack("------->" + (DBG_RB_LoadShaderTextureMatrix++));
        qgl.qglMatrixMode(GL11.GL_TEXTURE)
        qgl.qglLoadMatrixf(matrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)
    }

    fun RB_BindVariableStageImage(texture: textureStage_t, shaderRegisters: FloatArray) {
        DBG_RB_BindVariableStageImage++
        //        if (DBG_RB_BindVariableStageImage == 50) {
//            for (drawSurf_s draw : backEnd.viewDef!!.drawSurfs) {
//                System.out.println("=============================");
//                for (int s = 0; draw != null && s < draw.material.GetNumStages(); s++) {
//                    if(draw.material.GetStage(s).texture.image[0]!=null)
//                        System.out.println("ss::"+draw.material.GetStage(s).texture.image[0].texNum);
//                }
//            }
//        }
        if (texture.cinematic[0] != null) {
            val cin: cinData_t?
            if (RenderSystem_init.r_skipDynamicTextures.GetBool()) {
                Image.globalImages.defaultImage.Bind()
                return
            }

            // offset time by shaderParm[7] (FIXME: make the time offset a parameter of the shader?)
            // We make no attempt to optimize for multiple identical cinematics being in view, or
            // for cinematics going at a lower framerate than the renderer.
            cin =
                texture.cinematic[0]!!.ImageForTime((1000 * (tr_local.backEnd.viewDef!!.floatTime + tr_local.backEnd.viewDef!!.renderView.shaderParms[11])).toInt())
            if (cin.image != null) {
                Image.globalImages.cinematicImage.UploadScratch(cin.image!!, cin.imageWidth, cin.imageHeight)
            } else {
                Image.globalImages.blackImage.Bind()
            }
        } else {
            //FIXME: see why image is invalid
            if (texture.image.isNotEmpty()) {
//                final int titty = texture.image[0].texNum;
//                if (titty != 58) return;
                texture.image[0]!!.Bind()
            }
        }
    }

    /*
     ======================
     RB_BindStageTexture
     ======================
     */
    fun RB_BindStageTexture(shaderRegisters: FloatArray, texture: textureStage_t, surf: drawSurf_s) {
        // image
        RB_BindVariableStageImage(texture, shaderRegisters)

        // texgens
        if (texture.texgen == texgen_t.TG_DIFFUSE_CUBE) {
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo.ambientCache)) //TODO:figure out how to work these damn casts.
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, vert.normal.ToFloatPtr())
        }
        if (texture.texgen == texgen_t.TG_SKYBOX_CUBE || texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            qgl.qglTexCoordPointer(3, GL11.GL_FLOAT, 0, VertexCache.vertexCache.Position(surf.dynamicTexCoords))
        }
        if (texture.texgen == texgen_t.TG_REFLECT_CUBE) {
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_S)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_T)
            qgl.qglEnable(GL11.GL_TEXTURE_GEN_R)
            qgl.qglTexGenf(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
            qgl.qglTexGenf(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
            qgl.qglTexGenf(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL13.GL_REFLECTION_MAP /*_EXT*/.toFloat())
            qgl.qglEnableClientState(GL11.GL_NORMAL_ARRAY)
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo.ambientCache)) // {//TODO:figure out how to work these damn casts.
            qgl.qglNormalPointer(GL11.GL_FLOAT, idDrawVert.BYTES, vert.normalOffset().toLong())
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            val mat = FloatArray(16)
            tr_main.R_TransposeGLMatrix(tr_local.backEnd.viewDef!!.worldSpace.modelViewMatrix, mat)
            qgl.qglLoadMatrixf(mat)
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        }

        // matrix
        if (texture.hasMatrix) {
            RB_LoadShaderTextureMatrix(shaderRegisters, texture)
        }
    }

    /*
     ======================
     RB_FinishStageTexture
     ======================
     */
    fun RB_FinishStageTexture(texture: textureStage_t, surf: drawSurf_s) {
        if (texture.texgen == texgen_t.TG_DIFFUSE_CUBE || texture.texgen == texgen_t.TG_SKYBOX_CUBE || texture.texgen == texgen_t.TG_WOBBLESKY_CUBE) {
            val vert =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo.ambientCache)) // {//TODO:figure out how to work these damn casts.
            qgl.qglTexCoordPointer(
                2,
                GL11.GL_FLOAT,
                idDrawVert.BYTES,  //			(void *)&(((idDrawVert *)vertexCache.Position( surf.geo.ambientCache )).st) );
                vert.st.ToFloatPtr()
            ) //TODO:WDF?
        }
        if (texture.texgen == texgen_t.TG_REFLECT_CUBE) {
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
        if (texture.hasMatrix) {
            qgl.qglMatrixMode(GL11.GL_TEXTURE)
            qgl.qglLoadIdentity()
            qgl.qglMatrixMode(GL11.GL_MODELVIEW)
        }
    }

    //=============================================================================================
    /*
     =================
     RB_DetermineLightScale

     Sets:
     backEnd.lightScale
     backEnd.overBright

     Find out how much we are going to need to overscale the lighting, so we
     can down modulate the pre-lighting passes.

     We only look at light calculations, but an argument could be made that
     we should also look at surface evaluations, which would let surfaces
     overbright past 1.0
     =================
     */
    fun RB_DetermineLightScale() {
        var vLight: viewLight_s?
        var shader: idMaterial?
        var max: Float
        var i: Int
        var j: Int
        var numStages: Int
        var stage: shaderStage_t

        // the light scale will be based on the largest color component of any surface
        // that will be drawn.
        // should we consider separating rgb scales?
        // if there are no lights, this will remain at 1.0, so GUI-only
        // rendering will not lose any bits of precision
        max = 1.0f
        vLight = tr_local.backEnd.viewDef!!.viewLights
        while (vLight != null) {

            // lights with no surfaces or shaderparms may still be present
            // for debug display
            if (null == vLight.localInteractions[0] && null == vLight.globalInteractions[0] && null == vLight.translucentInteractions[0]) {
                vLight = vLight.next
                continue
            }
            shader = vLight.lightShader
            numStages = shader.GetNumStages()
            i = 0
            while (i < numStages) {
                stage = shader.GetStage(i)
                j = 0
                while (j < 3) {
                    val v = RenderSystem_init.r_lightScale.GetFloat() * vLight.shaderRegisters[stage.color.registers[j]]
                    if (v > max) {
                        max = v
                    }
                    j++
                }
                i++
            }
            vLight = vLight.next
        }
        tr_local.backEnd.pc.maxLightValue = max
        if (max <= tr_local.tr.backEndRendererMaxLight) {
            tr_local.backEnd.lightScale = RenderSystem_init.r_lightScale.GetFloat()
            tr_local.backEnd.overBright = 1.0f
        } else {
            tr_local.backEnd.lightScale =
                RenderSystem_init.r_lightScale.GetFloat() * tr_local.tr.backEndRendererMaxLight / max
            tr_local.backEnd.overBright = max / tr_local.tr.backEndRendererMaxLight
        }
    }

    /*
     =================
     RB_BeginDrawingView

     Any mirrored or portaled views have already been drawn, so prepare
     to actually render the visible surfaces for this view
     =================
     */
    fun RB_BeginDrawingView() {
        // set the modelview matrix for the viewer
        qgl.qglMatrixMode(GL11.GL_PROJECTION)
        qgl.qglLoadMatrixf(tr_local.backEnd.viewDef!!.projectionMatrix)
        qgl.qglMatrixMode(GL11.GL_MODELVIEW)

        // set the window clipping
        qgl.qglViewport(
            tr_local.tr.viewportOffset[0] + tr_local.backEnd.viewDef!!.viewport.x1,
            tr_local.tr.viewportOffset[1] + tr_local.backEnd.viewDef!!.viewport.y1,
            tr_local.backEnd.viewDef!!.viewport.x2 + 1 - tr_local.backEnd.viewDef!!.viewport.x1,
            tr_local.backEnd.viewDef!!.viewport.y2 + 1 - tr_local.backEnd.viewDef!!.viewport.y1
        )

        // the scissor may be smaller than the viewport for subviews
        qgl.qglScissor(
            tr_local.tr.viewportOffset[0] + tr_local.backEnd.viewDef!!.viewport.x1 + tr_local.backEnd.viewDef!!.scissor.x1,
            tr_local.tr.viewportOffset[1] + tr_local.backEnd.viewDef!!.viewport.y1 + tr_local.backEnd.viewDef!!.scissor.y1,
            tr_local.backEnd.viewDef!!.scissor.x2 + 1 - tr_local.backEnd.viewDef!!.scissor.x1,
            tr_local.backEnd.viewDef!!.scissor.y2 + 1 - tr_local.backEnd.viewDef!!.scissor.y1
        )
        tr_local.backEnd.currentScissor = tr_local.backEnd.viewDef!!.scissor

        // ensures that depth writes are enabled for the depth clear
        tr_backend.GL_State(tr_local.GLS_DEFAULT)

        // we don't have to clear the depth / stencil buffer for 2D rendering
        if (tr_local.backEnd.viewDef!!.viewEntitys != null) {
            qgl.qglStencilMask(0xff)
            // some cards may have 7 bit stencil buffers, so don't assume this
            // should be 128
            qgl.qglClearStencil(1 shl tr_local.glConfig.stencilBits - 1)
            qgl.qglClear(GL11.GL_DEPTH_BUFFER_BIT or GL11.GL_STENCIL_BUFFER_BIT)
            qgl.qglEnable(GL11.GL_DEPTH_TEST)
        } else {
            qgl.qglDisable(GL11.GL_DEPTH_TEST)
            qgl.qglDisable(GL11.GL_STENCIL_TEST)
        }
        tr_local.backEnd.glState.faceCulling = -1 // force face culling to set next time
        tr_backend.GL_Cull(cullType_t.CT_FRONT_SIDED)
    }

    /*
     ==================
     R_SetDrawInteractions
     ==================
     */
    fun R_SetDrawInteraction(
        surfaceStage: shaderStage_t,
        surfaceRegs: FloatArray,
        image: Array<idImage?>,
        matrix: Array<idVec4> /*[2]*/,
        color: idVec4? /*[4]*/
    ) {
        image[0] = surfaceStage.texture.image[0]
        if (surfaceStage.texture.hasMatrix) {
            matrix[0][0] = surfaceRegs[surfaceStage.texture.matrix[0][0]]
            matrix[0][1] = surfaceRegs[surfaceStage.texture.matrix[0][1]]
            matrix[0][2] = 0f
            matrix[0][3] = surfaceRegs[surfaceStage.texture.matrix[0][2]]
            matrix[1][0] = surfaceRegs[surfaceStage.texture.matrix[1][0]]
            matrix[1][1] = surfaceRegs[surfaceStage.texture.matrix[1][1]]
            matrix[1][2] = 0f
            matrix[1][3] = surfaceRegs[surfaceStage.texture.matrix[1][2]]

            // we attempt to keep scrolls from generating incredibly large texture values, but
            // center rotations and center scales can still generate offsets that need to be > 1
            if (matrix[0][3] < -40 || matrix[0][3] > 40) {
                matrix[0].minusAssign(3, matrix[0][3].toInt().toFloat())
            }
            if (matrix[1][3] < -40 || matrix[1][3] > 40) {
                matrix[1].minusAssign(3, matrix[1][3].toInt().toFloat())
            }
        } else {
            matrix[0][0] = 1f
            matrix[0][1] = 0f
            matrix[0][2] = 0f
            matrix[0][3] = 0f
            matrix[1][0] = 0f
            matrix[1][1] = 1f
            matrix[1][2] = 0f
            matrix[1][3] = 0f
        }
        if (color != null) {
            for (i in 0..3) {
                color[i] = surfaceRegs[surfaceStage.color.registers[i]]
                // clamp here, so card with greater range don't look different.
                // we could perform overbrighting like we do for lights, but
                // it doesn't currently look worth it.
                if (color[i] < 0) {
                    color[i] = 0f
                } else if (color[i] > 1.0) {
                    color[i] = 1.0f
                }
            }
        }
    }

    fun RB_SubmittInteraction(din: drawInteraction_t, drawInteraction: DrawInteraction) {
        if (null == din.bumpImage) {
            return
        }
        if (null == din.diffuseImage || RenderSystem_init.r_skipDiffuse.GetBool()) {
            din.diffuseImage = Image.globalImages.blackImage
        }
        if (null == din.specularImage || RenderSystem_init.r_skipSpecular.GetBool() || din.ambientLight != 0) {
            din.specularImage = Image.globalImages.blackImage
        }
        if (null == din.bumpImage || RenderSystem_init.r_skipBump.GetBool()) {
            din.bumpImage = Image.globalImages.flatNormalMap
        }

        // if we wouldn't draw anything, don't call the Draw function
        if ((din.diffuseColor[0] > 0 || din.diffuseColor[1] > 0 || din.diffuseColor[2] > 0) && din.diffuseImage !== Image.globalImages.blackImage
            || (din.specularColor[0] > 0 || din.specularColor[1] > 0 || din.specularColor[2] > 0) && din.specularImage !== Image.globalImages.blackImage
        ) {
            DBG_RB_SubmittInteraction++
            drawInteraction.run(din)
        }
    }

    /*
     =============
     RB_CreateSingleDrawInteractions

     This can be used by different draw_* backends to decompose a complex light / surface
     interaction into primitive interactions
     =============
     */
    fun RB_CreateSingleDrawInteractions(surf: drawSurf_s, drawInteraction: DrawInteraction) {
        val surfaceShader: idMaterial = surf.material!!
        val surfaceRegs = surf.shaderRegisters
        val vLight = tr_local.backEnd.vLight
        val lightShader: idMaterial = vLight.lightShader
        val lightRegs = vLight.shaderRegisters
        val inter = drawInteraction_t()
        if (RenderSystem_init.r_skipInteractions.GetBool() || TempDump.NOT(surf.geo) || TempDump.NOT(surf.geo.ambientCache)) {
            return
        }
        if (tr_local.tr.logFile != null) {
            tr_backend.RB_LogComment(
                "---------- RB_CreateSingleDrawInteractions %s on %s ----------\n",
                lightShader.GetName(),
                surfaceShader.GetName()
            )
        }

        // change the matrix and light projection vectors if needed
        if (surf.space !== tr_local.backEnd.currentSpace) {
            tr_local.backEnd.currentSpace = surf.space
            qgl.qglLoadMatrixf(surf.space.modelViewMatrix)
        }

        // change the scissor if needed
        if (RenderSystem_init.r_useScissor.GetBool() && !tr_local.backEnd.currentScissor.Equals(surf.scissorRect)) {
            tr_local.backEnd.currentScissor = surf.scissorRect
            qgl.qglScissor(
                tr_local.backEnd.viewDef!!.viewport.x1 + tr_local.backEnd.currentScissor.x1,
                tr_local.backEnd.viewDef!!.viewport.y1 + tr_local.backEnd.currentScissor.y1,
                tr_local.backEnd.currentScissor.x2 + 1 - tr_local.backEnd.currentScissor.x1,
                tr_local.backEnd.currentScissor.y2 + 1 - tr_local.backEnd.currentScissor.y1
            )
        }

        // hack depth range if needed
        if (surf.space.weaponDepthHack) {
            RB_EnterWeaponDepthHack()
        }
        if (surf.space.modelDepthHack != 0f) {
            RB_EnterModelDepthHack(surf.space.modelDepthHack)
        }
        inter.surf = surf
        inter.lightFalloffImage = vLight.falloffImage
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, vLight.globalLightOrigin, inter.localLightOrigin)
        tr_main.R_GlobalPointToLocal(
            surf.space.modelMatrix,
            tr_local.backEnd.viewDef!!.renderView.vieworg,
            inter.localViewOrigin
        )
        inter.localLightOrigin[3] = 0f
        inter.localViewOrigin[3] = 1f
        inter.ambientLight = TempDump.btoi(lightShader.IsAmbientLight())

        // the base projections may be modified by texture matrix on light stages
        val lightProject: Array<idPlane> = idPlane.generateArray(4)
        for (i in 0..3) {
            tr_main.R_GlobalPlaneToLocal(
                surf.space.modelMatrix,
                tr_local.backEnd.vLight.lightProject[i],
                lightProject[i]
            )
        }
        for (lightStageNum in 0 until lightShader.GetNumStages()) {
            val lightStage: shaderStage_t = lightShader.GetStage(lightStageNum)

            // ignore stages that fail the condition
            if (0 == lightRegs[lightStage.conditionRegister].toInt()) {
                continue
            }
            inter.lightImage = lightStage.texture.image[0] //TODO:pointeR?

//            memcpy(inter.lightProjection, lightProject, sizeof(inter.lightProjection));
            for (i in inter.lightProjection.indices) {
                inter.lightProjection[i] = lightProject[i].ToVec4()
            }
            // now multiply the texgen by the light texture matrix
            if (lightStage.texture.hasMatrix) {
                RB_GetShaderTextureMatrix(lightRegs, lightStage.texture, tr_local.backEnd.lightTextureMatrix)
                draw_common.RB_BakeTextureMatrixIntoTexgen( /*reinterpret_cast<class idPlane *>*/inter.lightProjection,
                    tr_local.backEnd.lightTextureMatrix
                )
            }
            inter.bumpImage = null
            inter.specularImage = null
            inter.diffuseImage = null
            inter.diffuseColor.set(0f, 0f, 0f, 0f)
            inter.specularColor.set(0f, 0f, 0f, 0f)
            val lightColor = FloatArray(4)

            // backEnd.lightScale is calculated so that lightColor[] will never exceed
            // tr.backEndRendererMaxLight
            lightColor[0] = tr_local.backEnd.lightScale * lightRegs[lightStage.color.registers[0]]
            lightColor[1] = tr_local.backEnd.lightScale * lightRegs[lightStage.color.registers[1]]
            lightColor[2] = tr_local.backEnd.lightScale * lightRegs[lightStage.color.registers[2]]
            lightColor[3] = lightRegs[lightStage.color.registers[3]]

            // go through the individual stages
            for (surfaceStageNum in 0 until surfaceShader.GetNumStages()) {
                val surfaceStage: shaderStage_t = surfaceShader.GetStage(surfaceStageNum)
                when (surfaceStage.lighting) {
                    stageLighting_t.SL_AMBIENT -> {}
                    stageLighting_t.SL_BUMP -> {

                        // ignore stage that fails the condition
                        if (0 == surfaceRegs[surfaceStage.conditionRegister].toInt()) {
                            break
                        }
                        // draw any previous interaction
                        RB_SubmittInteraction(inter, drawInteraction)
                        inter.diffuseImage = null
                        inter.specularImage = null
                        run {
                            val bumpImage = arrayOfNulls<idImage>(1)
                            R_SetDrawInteraction(surfaceStage, surfaceRegs, bumpImage, inter.bumpMatrix, null)
                            inter.bumpImage = bumpImage[0]
                        }
                    }
                    stageLighting_t.SL_DIFFUSE -> {

                        // ignore stage that fails the condition
                        if (0 == surfaceRegs[surfaceStage.conditionRegister].toInt()) {
                            break
                        }
                        if (inter.diffuseImage != null) {
                            RB_SubmittInteraction(inter, drawInteraction)
                        }
                        run {
                            val diffuseImage = arrayOfNulls<idImage>(1)
                            R_SetDrawInteraction(
                                surfaceStage,
                                surfaceRegs,
                                diffuseImage,
                                inter.diffuseMatrix,
                                inter.diffuseColor
                            )
                            inter.diffuseImage = diffuseImage[0]
                        }
                        inter.diffuseColor.timesAssign(0, lightColor[0])
                        inter.diffuseColor.timesAssign(2, lightColor[2])
                        inter.diffuseColor.timesAssign(1, lightColor[1])
                        inter.diffuseColor.timesAssign(3, lightColor[3])
                        inter.vertexColor = surfaceStage.vertexColor
                    }
                    stageLighting_t.SL_SPECULAR -> {

                        // ignore stage that fails the condition
                        if (0 == surfaceRegs[surfaceStage.conditionRegister].toInt()) {
                            break
                        }
                        if (inter.specularImage != null) {
                            RB_SubmittInteraction(inter, drawInteraction)
                        }
                        run {
                            val specularImage = arrayOfNulls<idImage>(1)
                            R_SetDrawInteraction(
                                surfaceStage,
                                surfaceRegs,
                                specularImage,
                                inter.specularMatrix,
                                inter.specularColor
                            )
                            inter.specularImage = specularImage[0]
                        }
                        inter.specularColor.timesAssign(0, lightColor[0])
                        inter.specularColor.timesAssign(1, lightColor[1])
                        inter.specularColor.timesAssign(2, lightColor[2])
                        inter.specularColor.timesAssign(3, lightColor[3])
                        inter.vertexColor = surfaceStage.vertexColor
                    }
                }
            }

            // draw the final interaction
            RB_SubmittInteraction(inter, drawInteraction)
        }

        // unhack depth range if needed
        if (surf.space.weaponDepthHack || surf.space.modelDepthHack != 0.0f) {
            RB_LeaveDepthHack()
        }
    }

    /*
     =============
     RB_DrawView
     =============
     */
    fun RB_DrawView(data: Any) {
        val cmd: drawSurfsCommand_t?
        cmd = data as drawSurfsCommand_t
        tr_local.backEnd.viewDef = cmd.viewDef!!

        // we will need to do a new copyTexSubImage of the screen
        // when a SS_POST_PROCESS material is used
        tr_local.backEnd.currentRenderCopied = false

        // if there aren't any drawsurfs, do nothing
        if (0 == tr_local.backEnd.viewDef!!.numDrawSurfs) {
            return
        }

        // skip render bypasses everything that has models, assuming
        // them to be 3D views, but leaves 2D rendering visible
        if (RenderSystem_init.r_skipRender.GetBool() && tr_local.backEnd.viewDef!!.viewEntitys != null) {
            return
        }

        // skip render context sets the wgl context to NULL,
        // which should factor out the API cost, under the assumption
        // that all gl calls just return if the context isn't valid
        if (RenderSystem_init.r_skipRenderContext.GetBool() && tr_local.backEnd.viewDef!!.viewEntitys != null) {
            win_glimp.GLimp_DeactivateContext()
        }
        tr_local.backEnd.pc.c_surfaces += tr_local.backEnd.viewDef!!.numDrawSurfs
        tr_rendertools.RB_ShowOverdraw()

        // render the scene, jumping to the hardware specific interaction renderers
        draw_common.RB_STD_DrawView()

        // restore the context for 2D drawing if we were stubbing it out
        if (RenderSystem_init.r_skipRenderContext.GetBool() && tr_local.backEnd.viewDef!!.viewEntitys != null) {
            win_glimp.GLimp_ActivateContext()
            tr_backend.RB_SetDefaultGLState()
        }
    }

    abstract class DrawInteraction {
        abstract fun run(din: drawInteraction_t)
    }

    abstract class triFunc {
        abstract fun run(surf: drawSurf_s)
    }

    /*
     ===============
     RB_T_RenderTriangleSurface

     ===============
     */
    class RB_T_RenderTriangleSurface private constructor() : triFunc() {
        override fun run(surf: drawSurf_s) {
            RB_RenderTriangleSurface(surf.geo)
        }

        companion object {
            val INSTANCE: triFunc = RB_T_RenderTriangleSurface()
        }
    }
}