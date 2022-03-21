package neo.Renderer

import neo.Game.Game_local
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.lightingCache_s
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.backEndName_t
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewEntity_s
import neo.Renderer.tr_local.viewLight_s
import neo.TempDump
import neo.framework.Common
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.Lib.idException
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.UserInterface.idUserInterface
import java.util.stream.Stream

/**
 *
 */
object tr_light {
    const val CHECK_BOUNDS_EPSILON = 1.0f

    /*
     ====================
     R_TestPointInViewLight
     ====================
     */
    const val INSIDE_LIGHT_FRUSTUM_SLOP = 32f

    /*
     =================
     R_AddDrawSurf
     =================
     */
    private val refRegs: FloatArray? =
        FloatArray(precompiled.MAX_EXPRESSION_REGISTERS) // don't put on stack, or VC++ will do a page touch

    //==================================================================================================================================================================================================
    /*
     =============
     R_SetEntityDefViewEntity

     If the entityDef isn't already on the viewEntity list, create
     a viewEntity and add it to the list with an empty scissor rect.

     This does not instantiate dynamic models for the entity yet.
     =============
     */
    var DBG_R_SetEntityDefViewEntity = 0
    var DEBUG_drawZurf = 0

    /*
     ==================
     R_CalcLightScissorRectangle

     The light screen bounds will be used to crop the scissor rect during
     stencil clears and interaction drawing
     ==================
     */
    var c_clippedLight = 0
    var c_unclippedLight = 0

    /*
     ======================================================================================================================================================================================

     VERTEX CACHE GENERATORS

     ======================================================================================================================================================================================
     */
    /*
     ==================
     R_CreateAmbientCache

     Create it if needed
     ==================
     */
    fun R_CreateAmbientCache(tri: srfTriangles_s?, needsLighting: Boolean): Boolean {
        if (tri.ambientCache != null) {
            return true
        }
        // we are going to use it for drawing, so make sure we have the tangents and normals
        if (needsLighting && !tri.tangentsCalculated) {
            R_DeriveTangents(tri)
        }
        tri.ambientCache = VertexCache.vertexCache.Alloc(tri.verts, tri.numVerts * idDrawVert.Companion.BYTES)
        return !TempDump.NOT(tri.ambientCache)
    }

    /*
     ==================
     R_CreateLightingCache

     Returns false if the cache couldn't be allocated, in which case the surface should be skipped.
     ==================
     */
    fun R_CreateLightingCache(ent: idRenderEntityLocal?, light: idRenderLightLocal?, tri: srfTriangles_s?): Boolean {
        val localLightOrigin = idVec3()

        // fogs and blends don't need light vectors
        if (light.lightShader.IsFogLight() || light.lightShader.IsBlendLight()) {
            return true
        }

        // not needed if we have vertex programs
        if (tr_local.tr.backEndRendererHasVertexPrograms) {
            return true
        }
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, localLightOrigin)
        val size: Int = tri.ambientSurface.numVerts * lightingCache_s.Companion.BYTES
        val cache = arrayOfNulls<lightingCache_s?>(size)
        if (true) {
            Simd.SIMDProcessor.CreateTextureSpaceLightVectors(
                cache[0].localLightVector,
                localLightOrigin,
                tri.ambientSurface.verts,
                tri.ambientSurface.numVerts,
                tri.indexes,
                tri.numIndexes
            )
        } else {
//	boolean []used = new boolean[tri.ambientSurface.numVerts];
//	memset( used, 0, tri.ambientSurface.numVerts * sizeof( used[0] ) );
//
//	// because the interaction may be a very small subset of the full surface,
//	// it makes sense to only deal with the verts used
//	for ( int j = 0; j < tri.numIndexes; j++ ) {
//		int i = tri.indexes[j];
//		if ( used[i] ) {
//			continue;
//		}
//		used[i] = true;
//
//		idVec3 lightDir;
//		const idDrawVert *v;
//
//		v = &tri.ambientSurface.verts[i];
//
//		lightDir = localLightOrigin - v.xyz;
//
//		cache[i].localLightVector[0] = lightDir * v.tangents[0];
//		cache[i].localLightVector[1] = lightDir * v.tangents[1];
//		cache[i].localLightVector[2] = lightDir * v.normal;
//	}
        }
        tri.lightingCache = VertexCache.vertexCache.Alloc(cache, size)
        return !TempDump.NOT(tri.lightingCache)
    }

    /*
     ==================
     R_CreatePrivateShadowCache

     This is used only for a specific light
     ==================
     */
    fun R_CreatePrivateShadowCache(tri: srfTriangles_s?) {
        if (null == tri.shadowVertexes) {
            return
        }
        tri.shadowCache =
            VertexCache.vertexCache.Alloc(tri.shadowVertexes, tri.numVerts * shadowCache_s.Companion.BYTES)
    }

    /*
     ==================
     R_CreateVertexProgramShadowCache

     This is constant for any number of lights, the vertex program
     takes care of projecting the verts to infinity.
     ==================
     */
    fun R_CreateVertexProgramShadowCache(tri: srfTriangles_s?) {
        if (tri.verts == null) {
            return
        }
        val temp = Stream.generate { shadowCache_s() }.limit((tri.numVerts * 2).toLong())
            .toArray<shadowCache_s?> { _Dummy_.__Array__() }

//        if (true) {
//
//            SIMDProcessor.CreateVertexProgramShadowCache(temp[0].xyz, tri.verts, tri.numVerts);
//
//        } else {
        for (i in 0 until tri.numVerts) {
            val v = tri.verts[i].xyz.ToFloatPtr()
            temp[i * 2 + 0].xyz.set(0, v[0])
            temp[i * 2 + 1].xyz.set(0, v[0])
            temp[i * 2 + 0].xyz.set(1, v[1])
            temp[i * 2 + 1].xyz.set(1, v[1])
            temp[i * 2 + 0].xyz.set(2, v[2])
            temp[i * 2 + 1].xyz.set(2, v[2])
            temp[i * 2 + 0].xyz.set(3, 1.0f) // on the model surface
            temp[i * 2 + 1].xyz.set(3, 0.0f) // will be projected to infinity
        }
        tri.shadowCache = VertexCache.vertexCache.Alloc(temp, tri.numVerts * 2 * shadowCache_s.Companion.BYTES)
    }

    /*
     ==================
     R_SkyboxTexGen
     ==================
     */
    fun R_SkyboxTexGen(surf: drawSurf_s?, viewOrg: idVec3) {
        var i: Int
        val localViewOrigin = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, viewOrg, localViewOrigin)
        val numVerts = surf.geo.numVerts
        val texCoords: Array<idVec3> = idVec3.Companion.generateArray(numVerts)
        val verts = surf.geo.verts
        i = 0
        while (i < numVerts) {
            texCoords[i].set(verts[i].xyz.minus(localViewOrigin))
            i++
        }
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, numVerts)
    }

    // this needs to be greater than the dist from origin to corner of near clip plane
    /*
     ==================
     R_WobbleskyTexGen
     ==================
     */
    fun R_WobbleskyTexGen(surf: drawSurf_s?, viewOrg: idVec3) {
        var i: Int
        val localViewOrigin = idVec3()
        val parms = surf.material.GetTexGenRegisters()
        var wobbleDegrees = surf.shaderRegisters[parms[0]]
        var wobbleSpeed = surf.shaderRegisters[parms[1]]
        var rotateSpeed = surf.shaderRegisters[parms[2]]
        wobbleDegrees = wobbleDegrees * idMath.PI / 180
        wobbleSpeed = wobbleSpeed * 2 * idMath.PI / 60
        rotateSpeed = rotateSpeed * 2 * idMath.PI / 60

        // very ad-hoc "wobble" transform
        val transform = FloatArray(16)
        val a = tr_local.tr.viewDef.floatTime * wobbleSpeed
        var s = (Math.sin(a.toDouble()) * Math.sin(wobbleDegrees.toDouble())).toFloat()
        var c = (Math.cos(a.toDouble()) * Math.sin(wobbleDegrees.toDouble())).toFloat()
        val z = Math.cos(wobbleDegrees.toDouble()).toFloat()
        val axis: Array<idVec3> = idVec3.Companion.generateArray(3)
        axis[2].set(0, c)
        axis[2].set(1, s)
        axis[2].set(2, z)
        axis[1].set(0, (-Math.sin((a * 2).toDouble()) * Math.sin(wobbleDegrees.toDouble())).toFloat())
        axis[1].set(2, (-s * Math.sin(wobbleDegrees.toDouble())).toFloat())
        axis[1].set(
            1,
            Math.sqrt((1.0f - (axis[1].get(0) * axis[1].get(0) + axis[1].get(2) * axis[1].get(2))).toDouble())
                .toFloat()
        )

        // make the second vector exactly perpendicular to the first
        axis[1].minusAssign(axis[2].times(axis[2].times(axis[1])))
        axis[1].Normalize()

        // construct the third with a cross
        axis[0].Cross(axis[1], axis[2])

        // add the rotate
        s = Math.sin((rotateSpeed * tr_local.tr.viewDef.floatTime).toDouble()).toFloat()
        c = Math.cos((rotateSpeed * tr_local.tr.viewDef.floatTime).toDouble()).toFloat()
        transform[0] = axis[0].get(0) * c + axis[1].get(0) * s
        transform[4] = axis[0].get(1) * c + axis[1].get(1) * s
        transform[8] = axis[0].get(2) * c + axis[1].get(2) * s
        transform[1] = axis[1].get(0) * c - axis[0].get(0) * s
        transform[5] = axis[1].get(1) * c - axis[0].get(1) * s
        transform[9] = axis[1].get(2) * c - axis[0].get(2) * s
        transform[2] = axis[2].get(0)
        transform[6] = axis[2].get(1)
        transform[10] = axis[2].get(2)
        transform[11] = 0.0f
        transform[7] = transform[11]
        transform[3] = transform[7]
        transform[14] = 0.0f
        transform[13] = transform[14]
        transform[12] = transform[13]
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, viewOrg, localViewOrigin)
        val numVerts = surf.geo.numVerts
        val texCoords: Array<idVec3> = idVec3.Companion.generateArray(numVerts)
        val verts = surf.geo.verts
        i = 0
        while (i < numVerts) {
            val v = idVec3()
            v.set(0, verts[i].xyz.get(0) - localViewOrigin.get(0))
            v.set(1, verts[i].xyz.get(1) - localViewOrigin.get(1))
            v.set(2, verts[i].xyz.get(2) - localViewOrigin.get(2))
            texCoords[i].set(tr_main.R_LocalPointToGlobal(transform, v))
            i++
        }
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, numVerts)
    }

    /*
     =================
     R_SpecularTexGen

     Calculates the specular coordinates for cards without vertex programs.
     =================
     */
    fun R_SpecularTexGen(surf: drawSurf_s?, globalLightOrigin: idVec3, viewOrg: idVec3) {
        val tri: srfTriangles_s?
        val localLightOrigin = idVec3()
        val localViewOrigin = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, globalLightOrigin, localLightOrigin)
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, viewOrg, localViewOrigin)
        tri = surf.geo

        // FIXME: change to 3 component?
        val size = tri.numVerts // * sizeof( idVec4 );
        val texCoords = arrayOfNulls<idVec4>(size)
        if (true) {
            Simd.SIMDProcessor.CreateSpecularTextureCoords(
                texCoords, localLightOrigin, localViewOrigin,
                tri.verts, tri.numVerts, tri.indexes, tri.numIndexes
            )
        } else {
//	bool *used = (bool *)_alloca16( tri.numVerts * sizeof( used[0] ) );
//	memset( used, 0, tri.numVerts * sizeof( used[0] ) );
//
//	// because the interaction may be a very small subset of the full surface,
//	// it makes sense to only deal with the verts used
//	for ( int j = 0; j < tri.numIndexes; j++ ) {
//		int i = tri.indexes[j];
//		if ( used[i] ) {
//			continue;
//		}
//		used[i] = true;
//
//		float ilength;
//
//		const idDrawVert *v = &tri.verts[i];
//
//		idVec3 lightDir = localLightOrigin - v.xyz;
//		idVec3 viewDir = localViewOrigin - v.xyz;
//
//		ilength = idMath::RSqrt( lightDir * lightDir );
//		lightDir[0] *= ilength;
//		lightDir[1] *= ilength;
//		lightDir[2] *= ilength;
//
//		ilength = idMath::RSqrt( viewDir * viewDir );
//		viewDir[0] *= ilength;
//		viewDir[1] *= ilength;
//		viewDir[2] *= ilength;
//
//		lightDir += viewDir;
//
//		texCoords[i][0] = lightDir * v.tangents[0];
//		texCoords[i][1] = lightDir * v.tangents[1];
//		texCoords[i][2] = lightDir * v.normal;
//		texCoords[i][3] = 1;
//	}
        }
        surf.dynamicTexCoords = VertexCache.vertexCache.AllocFrameTemp(texCoords, size)
    }

    fun R_SetEntityDefViewEntity(def: idRenderEntityLocal?): viewEntity_s? {
        val vModel: viewEntity_s
        if (def.viewCount == tr_local.tr.viewCount) {
            return def.viewEntity
        }
        tr_light.DBG_R_SetEntityDefViewEntity++
        def.viewCount = tr_local.tr.viewCount

        // set the model and modelview matricies
        vModel = viewEntity_s() // R_ClearedFrameAlloc(sizeof(vModel));
        //        TempDump.printCallStack("~~~~~~~~~~~~~~~~~" + vModel.DBG_COUNTER + "\\\\//" + tr.viewCount);
        vModel.entityDef = def

        // the scissorRect will be expanded as the model bounds is accepted into visible portal chains
        vModel.scissorRect.Clear()


        // copy the model and weapon depth hack for back-end use
        vModel.modelDepthHack = def.parms.modelDepthHack
        vModel.weaponDepthHack = def.parms.weaponDepthHack
        tr_main.R_AxisToModelMatrix(def.parms.axis, def.parms.origin, vModel.modelMatrix)

        // we may not have a viewDef if we are just creating shadows at entity creation time
        if (tr_local.tr.viewDef != null) {
            tr_main.myGlMultMatrix(
                vModel.modelMatrix,
                tr_local.tr.viewDef.worldSpace.modelViewMatrix,
                vModel.modelViewMatrix
            )
            vModel.next = tr_local.tr.viewDef.viewEntitys
            tr_local.tr.viewDef.viewEntitys = vModel
            tr_local.tr.viewDef.numViewEntitys++
        }
        def.viewEntity = vModel
        return vModel
    }

    //=============================================================================================================================================================================================
    fun R_TestPointInViewLight(org: idVec3, light: idRenderLightLocal?): Boolean {
        var i: Int
        //	idVec3	local;
        i = 0
        while (i < 6) {
            val d = light.frustum[i].Distance(org)
            if (d > tr_light.INSIDE_LIGHT_FRUSTUM_SLOP) {
                return false
            }
            i++
        }
        return true
    }

    /*
     ===================
     R_PointInFrustum

     Assumes positive sides face outward
     ===================
     */
    fun R_PointInFrustum(p: idVec3, planes: Array<idPlane>?, numPlanes: Int): Boolean {
        for (i in 0 until numPlanes) {
            val d = planes.get(i).Distance(p)
            if (d > 0) {
                return false
            }
        }
        return true
    }

    /*
     =============
     R_SetLightDefViewLight

     If the lightDef isn't already on the viewLight list, create
     a viewLight and add it to the list with an empty scissor rect.
     =============
     */
    fun R_SetLightDefViewLight(light: idRenderLightLocal?): viewLight_s? {
        val vLight: viewLight_s
        if (light.viewCount == tr_local.tr.viewCount) {
            return light.viewLight
        }
        light.viewCount = tr_local.tr.viewCount

        // add to the view light chain
        vLight = viewLight_s() // R_ClearedFrameAlloc(sizeof(vLight));
        vLight.lightDef = light

        // the scissorRect will be expanded as the light bounds is accepted into visible portal chains
        vLight.scissorRect = idScreenRect()
        vLight.scissorRect.Clear()

        // calculate the shadow cap optimization states
        vLight.viewInsideLight = tr_light.R_TestPointInViewLight(tr_local.tr.viewDef.renderView.vieworg, light)
        if (!vLight.viewInsideLight) {
            vLight.viewSeesShadowPlaneBits = 0
            for (i in 0 until light.numShadowFrustums) {
                val d = light.shadowFrustums[i].planes[5].Distance(tr_local.tr.viewDef.renderView.vieworg)
                if (d < tr_light.INSIDE_LIGHT_FRUSTUM_SLOP) {
                    vLight.viewSeesShadowPlaneBits = vLight.viewSeesShadowPlaneBits or (1 shl i)
                }
            }
        } else {
            // this should not be referenced in this case
            vLight.viewSeesShadowPlaneBits = 63
        }

        // see if the light center is in view, which will allow us to cull invisible shadows
        vLight.viewSeesGlobalLightOrigin =
            tr_light.R_PointInFrustum(light.globalLightOrigin, tr_local.tr.viewDef.frustum, 4)

        // copy data used by backend
        vLight.globalLightOrigin.set(light.globalLightOrigin)
        vLight.lightProject[0] = idPlane(light.lightProject[0])
        vLight.lightProject[1] = idPlane(light.lightProject[1])
        vLight.lightProject[2] = idPlane(light.lightProject[2])
        vLight.lightProject[3] = idPlane(light.lightProject[3])
        vLight.fogPlane = idPlane(light.frustum[5])
        vLight.frustumTris = light.frustumTris
        vLight.falloffImage = light.falloffImage
        vLight.lightShader = light.lightShader
        vLight.shaderRegisters = null // allocated and evaluated in R_AddLightSurfaces

        // link the view light
        vLight.next = tr_local.tr.viewDef.viewLights
        tr_local.tr.viewDef.viewLights = vLight
        light.viewLight = vLight
        return vLight
    }

    /*
     =================
     R_LinkLightSurf
     =================
     */
    fun R_LinkLightSurf(
        link: Array<drawSurf_s?>?, tri: srfTriangles_s?, spaceView: viewEntity_s?,
        light: idRenderLightLocal?, shader: idMaterial?, scissor: idScreenRect?, viewInsideShadow: Boolean
    ) {
        val drawSurf: drawSurf_s
        var space = spaceView //TODO:should a back reference be set here?
        if (null == space) {
            space = tr_local.tr.viewDef.worldSpace
        }
        drawSurf = drawSurf_s() //R_FrameAlloc(sizeof(drawSurf));
        drawSurf.geo = tri
        drawSurf.space = space
        drawSurf.material = shader
        drawSurf.scissorRect = idScreenRect(scissor)
        drawSurf.dsFlags = 0
        if (viewInsideShadow) {
            drawSurf.dsFlags = drawSurf.dsFlags or tr_local.DSF_VIEW_INSIDE_SHADOW
        }
        if (null == shader) {
            // shadows won't have a shader
            drawSurf.shaderRegisters = null
        } else {
            // process the shader expressions for conditionals / color / texcoords
            val constRegs: FloatArray = shader.ConstantRegisters()
            if (constRegs != null) {
                // this shader has only constants for parameters
                drawSurf.shaderRegisters = constRegs.clone()
            } else {
                // FIXME: share with the ambient surface?
                val regs = FloatArray(shader.GetNumRegisters()) //R_FrameAlloc(shader.GetNumRegisters());
                drawSurf.shaderRegisters = regs
                shader.EvaluateRegisters(
                    regs,
                    space.entityDef.parms.shaderParms,
                    tr_local.tr.viewDef,
                    space.entityDef.parms.referenceSound
                )
            }

            // calculate the specular coordinates if we aren't using vertex programs
            if (!tr_local.tr.backEndRendererHasVertexPrograms && !RenderSystem_init.r_skipSpecular.GetBool() && tr_local.tr.backEndRenderer != backEndName_t.BE_ARB) {
                tr_light.R_SpecularTexGen(drawSurf, light.globalLightOrigin, tr_local.tr.viewDef.renderView.vieworg)
                // if we failed to allocate space for the specular calculations, drop the surface
                if (TempDump.NOT(drawSurf.dynamicTexCoords)) {
                    return
                }
            }
        }

        // actually link it in
        drawSurf.nextOnLight = link.get(0)
        link.get(0) = drawSurf
    }

    /*
     ======================
     R_ClippedLightScissorRectangle
     ======================
     */
    fun R_ClippedLightScissorRectangle(vLight: viewLight_s?): idScreenRect? {
        var i: Int
        var j: Int
        val light = vLight.lightDef
        val r = idScreenRect()
        val w = idFixedWinding()
        r.Clear()
        i = 0
        while (i < 6) {
            val ow = light.frustumWindings[i]

            // projected lights may have one of the frustums degenerated
            if (null == ow) {
                i++
                continue
            }

            // the light frustum planes face out from the light,
            // so the planes that have the view origin on the negative
            // side will be the "back" faces of the light, which must have
            // some fragment inside the portalStack to be visible
            if (light.frustum[i].Distance(tr_local.tr.viewDef.renderView.vieworg) >= 0) {
                i++
                continue
            }
            w.set(ow)

            // now check the winding against each of the frustum planes
            j = 0
            while (j < 5) {
                if (!w.ClipInPlace(tr_local.tr.viewDef.frustum[j].unaryMinus())) {
                    break
                }
                j++
            }

            // project these points to the screen and add to bounds
            j = 0
            while (j < w.GetNumPoints()) {
                val eye = idPlane()
                val clip = idPlane()
                val ndc = idVec3()
                tr_main.R_TransformModelToClip(
                    w.get(j).ToVec3(),
                    tr_local.tr.viewDef.worldSpace.modelViewMatrix,
                    tr_local.tr.viewDef.projectionMatrix,
                    eye,
                    clip
                )
                if (clip.get(3) <= 0.01f) {
                    clip.set(3, 0.01f)
                }
                tr_main.R_TransformClipToDevice(clip, tr_local.tr.viewDef, ndc)
                var windowX =
                    0.5f * (1.0f + ndc.get(0)) * (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1)
                var windowY =
                    0.5f * (1.0f + ndc.get(1)) * (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1)
                if (windowX > tr_local.tr.viewDef.scissor.x2) {
                    windowX = tr_local.tr.viewDef.scissor.x2.toFloat()
                } else if (windowX < tr_local.tr.viewDef.scissor.x1) {
                    windowX = tr_local.tr.viewDef.scissor.x1.toFloat()
                }
                if (windowY > tr_local.tr.viewDef.scissor.y2) {
                    windowY = tr_local.tr.viewDef.scissor.y2.toFloat()
                } else if (windowY < tr_local.tr.viewDef.scissor.y1) {
                    windowY = tr_local.tr.viewDef.scissor.y1.toFloat()
                }
                r.AddPoint(windowX, windowY)
                j++
            }
            i++
        }

        // add the fudge boundary
        r.Expand()
        return r
    }

    //================================================================================================================================================================================================
    fun R_CalcLightScissorRectangle(vLight: viewLight_s?): idScreenRect? {
        val r = idScreenRect()
        val tri: srfTriangles_s?
        val eye = idPlane()
        val clip = idPlane()
        val ndc = idVec3()
        if (vLight.lightDef.parms.pointLight) {
            val bounds = idBounds()
            val lightDef = vLight.lightDef
            tr_local.tr.viewDef.viewFrustum.ProjectionBounds(
                idBox(
                    lightDef.parms.origin,
                    lightDef.parms.lightRadius,
                    lightDef.parms.axis
                ), bounds
            )
            return tr_main.R_ScreenRectFromViewFrustumBounds(bounds)
        }
        if (RenderSystem_init.r_useClippedLightScissors.GetInteger() == 2) {
            return tr_light.R_ClippedLightScissorRectangle(vLight)
        }
        r.Clear()
        tri = vLight.lightDef.frustumTris
        for (i in 0 until tri.numVerts) {
            tr_main.R_TransformModelToClip(
                tri.verts[i].xyz, tr_local.tr.viewDef.worldSpace.modelViewMatrix,
                tr_local.tr.viewDef.projectionMatrix, eye, clip
            )

            // if it is near clipped, clip the winding polygons to the view frustum
            if (clip.get(3) <= 1) {
                tr_light.c_clippedLight++
                return if (RenderSystem_init.r_useClippedLightScissors.GetInteger() != 0) {
                    tr_light.R_ClippedLightScissorRectangle(vLight)
                } else {
                    r.y1 = 0
                    r.x1 = r.y1
                    r.x2 = tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1 - 1
                    r.y2 = tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1 - 1
                    r
                }
            }
            tr_main.R_TransformClipToDevice(clip, tr_local.tr.viewDef, ndc)
            var windowX =
                0.5f * (1.0f + ndc.get(0)) * (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1)
            var windowY =
                0.5f * (1.0f + ndc.get(1)) * (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1)
            if (windowX > tr_local.tr.viewDef.scissor.x2) {
                windowX = tr_local.tr.viewDef.scissor.x2.toFloat()
            } else if (windowX < tr_local.tr.viewDef.scissor.x1) {
                windowX = tr_local.tr.viewDef.scissor.x1.toFloat()
            }
            if (windowY > tr_local.tr.viewDef.scissor.y2) {
                windowY = tr_local.tr.viewDef.scissor.y2.toFloat()
            } else if (windowY < tr_local.tr.viewDef.scissor.y1) {
                windowY = tr_local.tr.viewDef.scissor.y1.toFloat()
            }
            r.AddPoint(windowX, windowY)
        }

        // add the fudge boundary
        r.Expand()
        tr_light.c_unclippedLight++
        return r
    }

    /*
     =================
     R_AddLightSurfaces

     Calc the light shader values, removing any light from the viewLight list
     if it is determined to not have any visible effect due to being flashed off or turned off.

     Adds entities to the viewEntity list if they are needed for shadow casting.

     Add any precomputed shadow volumes.

     Removes lights from the viewLights list if they are completely
     turned off, or completely off screen.

     Create any new interactions needed between the viewLights
     and the viewEntitys due to game movement
     =================
     */
    @Throws(idException::class)
    fun R_AddLightSurfaces() {
        var vLight: viewLight_s?
        var light: idRenderLightLocal?
        var ptr: viewLight_s?
        var prevPtr: viewLight_s?
        var z = 0

        // go through each visible light, possibly removing some from the list
        ptr = tr_local.tr.viewDef.viewLights
        prevPtr = null
        while (ptr != null) {
            z++
            vLight = ptr
            light = vLight.lightDef
            val lightShader: idMaterial? = light.lightShader
            if (null == lightShader) {
                Common.common.Error("R_AddLightSurfaces: NULL lightShader")
            }

            // see if we are suppressing the light in this view
            if (!RenderSystem_init.r_skipSuppress.GetBool()) {
                if (light.parms.suppressLightInViewID != 0
                    && light.parms.suppressLightInViewID == tr_local.tr.viewDef.renderView.viewID
                ) {
                    if (vLight === tr_local.tr.viewDef.viewLights) {
                        ptr = vLight.next
                        tr_local.tr.viewDef.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
                if (light.parms.allowLightInViewID != 0
                    && light.parms.allowLightInViewID != tr_local.tr.viewDef.renderView.viewID
                ) {
                    if (vLight === tr_local.tr.viewDef.viewLights) {
                        ptr = vLight.next
                        tr_local.tr.viewDef.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
            }

            // evaluate the light shader registers
            val lightRegs = FloatArray(lightShader.GetNumRegisters()) // R_FrameAlloc(lightShader.GetNumRegisters());
            vLight.shaderRegisters = lightRegs
            lightShader.EvaluateRegisters(
                lightRegs,
                light.parms.shaderParms,
                tr_local.tr.viewDef,
                light.parms.referenceSound
            )

            // if this is a purely additive light and no stage in the light shader evaluates
            // to a positive light value, we can completely skip the light
            if (!lightShader.IsFogLight() && !lightShader.IsBlendLight()) {
                var lightStageNum: Int
                lightStageNum = 0
                while (lightStageNum < lightShader.GetNumStages()) {
                    val lightStage: shaderStage_t = lightShader.GetStage(lightStageNum)

                    // ignore stages that fail the condition
                    if (0 == lightRegs[lightStage.conditionRegister]) {
                        lightStageNum++
                        continue
                    }
                    val registers = lightStage.color.registers

                    // snap tiny values to zero to avoid lights showing up with the wrong color
                    if (lightRegs[registers[0]] < 0.001f) {
                        lightRegs[registers[0]] = 0.0f
                    }
                    if (lightRegs[registers[1]] < 0.001f) {
                        lightRegs[registers[1]] = 0.0f
                    }
                    if (lightRegs[registers[2]] < 0.001f) {
                        lightRegs[registers[2]] = 0.0f
                    }

                    // FIXME:	when using the following values the light shows up bright red when using nvidia drivers/hardware
                    //			this seems to have been fixed ?
                    //lightRegs[ registers[0] ] = 1.5143074e-005f;
                    //lightRegs[ registers[1] ] = 1.5483369e-005f;
                    //lightRegs[ registers[2] ] = 1.7014690e-005f;
                    if (lightRegs[registers[0]] > 0.0f || lightRegs[registers[1]] > 0.0f || lightRegs[registers[2]] > 0.0f) {
                        break
                    }
                    lightStageNum++
                }
                if (lightStageNum == lightShader.GetNumStages()) {
                    // we went through all the stages and didn't find one that adds anything
                    // remove the light from the viewLights list, and change its frame marker
                    // so interaction generation doesn't think the light is visible and
                    // create a shadow for it
                    if (vLight === tr_local.tr.viewDef.viewLights) {
                        ptr = vLight.next
                        tr_local.tr.viewDef.viewLights = ptr
                    } else {
                        ptr = vLight.next
                        prevPtr.next = ptr
                    }
                    light.viewCount = -1
                    continue
                }
            }
            if (RenderSystem_init.r_useLightScissors.GetBool()) {
                // calculate the screen area covered by the light frustum
                // which will be used to crop the stencil cull
                val scissorRect = tr_light.R_CalcLightScissorRectangle(vLight)
                // intersect with the portal crossing scissor rectangle
                vLight.scissorRect.Intersect(scissorRect)
                //                System.out.println("LoveTheRide===="+vLight.scissorRect);
                if (RenderSystem_init.r_showLightScissors.GetBool()) {
                    tr_main.R_ShowColoredScreenRect(vLight.scissorRect, light.index)
                }
            }

//            if (false) {
//		// this never happens, because CullLightByPortals() does a more precise job
//		if ( vLight.scissorRect.IsEmpty() ) {
//			// this light doesn't touch anything on screen, so remove it from the list
//			ptr = vLight.next;
//			continue;
//		}
//            }
            // this one stays on the list
            prevPtr = ptr
            ptr = vLight.next

            // if we are doing a soft-shadow novelty test, regenerate the light with
            // a random offset every time
            if (RenderSystem_init.r_lightSourceRadius.GetFloat() != 0.0f) {
                for (i in 0..2) {
                    light.globalLightOrigin.plusAssign(
                        i,
                        RenderSystem_init.r_lightSourceRadius.GetFloat() * (-1 + 2 * (Math.random()
                            .toInt() and 0xfff) / 0xf)
                    )
                }
            }

            // create interactions with all entities the light may touch, and add viewEntities
            // that may cast shadows, even if they aren't directly visible.  Any real work
            // will be deferred until we walk through the viewEntities
            tr_local.tr.viewDef.renderWorld.CreateLightDefInteractions(light)
            tr_local.tr.pc.c_viewLights++

            // fog lights will need to draw the light frustum triangles, so make sure they
            // are in the vertex cache
            if (lightShader.IsFogLight()) {
                if (TempDump.NOT(light.frustumTris.ambientCache)) {
                    if (!tr_light.R_CreateAmbientCache(light.frustumTris, false)) {
                        // skip if we are out of vertex memory
                        continue
                    }
                }
                // touch the surface so it won't get purged
                VertexCache.vertexCache.Touch(light.frustumTris.ambientCache)
            }

            // add the prelight shadows for the static world geometry
            if (light.parms.prelightModel != null && RenderSystem_init.r_useOptimizedShadows.GetBool()) {
                if (0 == light.parms.prelightModel.NumSurfaces()) {
                    Common.common.Error("no surfs in prelight model '%s'", light.parms.prelightModel.Name())
                }
                val tri = light.parms.prelightModel.Surface(0).geometry
                if (null == tri.shadowVertexes) {
                    Common.common.Error(
                        "R_AddLightSurfaces: prelight model '%s' without shadowVertexes",
                        light.parms.prelightModel.Name()
                    )
                }

                // these shadows will all have valid bounds, and can be culled normally
                if (RenderSystem_init.r_useShadowCulling.GetBool()) {
                    if (tr_main.R_CullLocalBox(
                            tri.bounds,
                            tr_local.tr.viewDef.worldSpace.modelMatrix,
                            5,
                            tr_local.tr.viewDef.frustum
                        )
                    ) {
                        continue
                    }
                }

                // if we have been purged, re-upload the shadowVertexes
                if (TempDump.NOT(tri.shadowCache)) {
                    tr_light.R_CreatePrivateShadowCache(tri)
                    if (TempDump.NOT(tri.shadowCache)) {
                        continue
                    }
                }

                // touch the shadow surface so it won't get purged
                VertexCache.vertexCache.Touch(tri.shadowCache)
                if (TempDump.NOT(tri.indexCache) && RenderSystem_init.r_useIndexBuffers.GetBool()) {
                    tri.indexCache = VertexCache.vertexCache.Alloc(tri.indexes, tri.numIndexes * Integer.BYTES, true)
                }
                if (tri.indexCache != null) {
                    VertexCache.vertexCache.Touch(tri.indexCache)
                }
                tr_light.R_LinkLightSurf(
                    vLight.globalShadows,
                    tri,
                    null,
                    light,
                    null,
                    vLight.scissorRect,
                    true /* FIXME? */
                )
            }
        }
    }

    /*
     ==================
     R_IssueEntityDefCallback
     ==================
     */
    fun R_IssueEntityDefCallback(def: idRenderEntityLocal?): Boolean {
        val update: Boolean
        var oldBounds: idBounds = null
        if (RenderSystem_init.r_checkBounds.GetBool()) {
            oldBounds = def.referenceBounds
        }
        def.archived = false // will need to be written to the demo file
        tr_local.tr.pc.c_entityDefCallbacks++
        update = if (tr_local.tr.viewDef != null) {
            def.parms.callback.run(def.parms, tr_local.tr.viewDef.renderView)
        } else {
            def.parms.callback.run(def.parms, null)
        }
        if (null == def.parms.hModel) {
            Common.common.Error("R_IssueEntityDefCallback: dynamic entity callback didn't set model")
        }
        if (RenderSystem_init.r_checkBounds.GetBool()) {
            if (oldBounds.get(0, 0) > def.referenceBounds.get(0, 0) + tr_light.CHECK_BOUNDS_EPSILON || oldBounds.get(
                    0,
                    1
                ) > def.referenceBounds.get(0, 1) + tr_light.CHECK_BOUNDS_EPSILON || oldBounds.get(
                    0,
                    2
                ) > def.referenceBounds.get(0, 2) + tr_light.CHECK_BOUNDS_EPSILON || oldBounds.get(
                    1,
                    0
                ) < def.referenceBounds.get(1, 0) - tr_light.CHECK_BOUNDS_EPSILON || oldBounds.get(
                    1,
                    1
                ) < def.referenceBounds.get(1, 1) - tr_light.CHECK_BOUNDS_EPSILON || oldBounds.get(
                    1,
                    2
                ) < def.referenceBounds.get(1, 2) - tr_light.CHECK_BOUNDS_EPSILON
            ) {
                Common.common.Printf("entity %d callback extended reference bounds\n", def.index)
            }
        }
        return update
    }

    /*
     ===================
     R_EntityDefDynamicModel

     Issues a deferred entity callback if necessary.
     If the model isn't dynamic, it returns the original.
     Returns the cached dynamic model if present, otherwise creates
     it and any necessary overlays
     ===================
     */
    fun R_EntityDefDynamicModel(def: idRenderEntityLocal?): idRenderModel? {
        val callbackUpdate: Boolean

        // allow deferred entities to construct themselves
        callbackUpdate = if (def.parms.callback != null) {
            tr_light.R_IssueEntityDefCallback(def)
        } else {
            false
        }
        val model = def.parms.hModel
        if (null == model) {
            Common.common.Error("R_EntityDefDynamicModel: NULL model")
        }
        if (model.IsDynamicModel() == dynamicModel_t.DM_STATIC) {
            def.dynamicModel = null
            def.dynamicModelFrameCount = 0
            return model
        }

        // continously animating models (particle systems, etc) will have their snapshot updated every single view
        if (callbackUpdate || model.IsDynamicModel() == dynamicModel_t.DM_CONTINUOUS && def.dynamicModelFrameCount != tr_local.tr.frameCount) {
            tr_lightrun.R_ClearEntityDefDynamicModel(def)
        }

        // if we don't have a snapshot of the dynamic model, generate it now
        if (null == def.dynamicModel) {

            // instantiate the snapshot of the dynamic model, possibly reusing memory from the cached snapshot
            def.cachedDynamicModel =
                model.InstantiateDynamicModel(def.parms, tr_local.tr.viewDef, def.cachedDynamicModel)
            if (def.cachedDynamicModel != null) {

                // add any overlays to the snapshot of the dynamic model
                if (def.overlay != null && !RenderSystem_init.r_skipOverlays.GetBool()) {
                    def.overlay.AddOverlaySurfacesToModel(def.cachedDynamicModel)
                } else {
                    idRenderModelOverlay.Companion.RemoveOverlaySurfacesFromModel(def.cachedDynamicModel)
                }
                if (RenderSystem_init.r_checkBounds.GetBool()) {
                    val b = def.cachedDynamicModel.Bounds()
                    if (b.get(0, 0) < def.referenceBounds.get(0, 0) - tr_light.CHECK_BOUNDS_EPSILON || b.get(
                            0,
                            1
                        ) < def.referenceBounds.get(0, 1) - tr_light.CHECK_BOUNDS_EPSILON || b.get(
                            0,
                            2
                        ) < def.referenceBounds.get(0, 2) - tr_light.CHECK_BOUNDS_EPSILON || b.get(
                            1,
                            0
                        ) > def.referenceBounds.get(1, 0) + tr_light.CHECK_BOUNDS_EPSILON || b.get(
                            1,
                            1
                        ) > def.referenceBounds.get(1, 1) + tr_light.CHECK_BOUNDS_EPSILON || b.get(
                            1,
                            2
                        ) > def.referenceBounds.get(1, 2) + tr_light.CHECK_BOUNDS_EPSILON
                    ) {
                        Common.common.Printf("entity %d dynamic model exceeded reference bounds\n", def.index)
                    }
                }
            }
            def.dynamicModel = def.cachedDynamicModel
            def.dynamicModelFrameCount = tr_local.tr.frameCount
        }

        // set model depth hack value
        if (def.dynamicModel != null && model.DepthHack() != 0.0f && tr_local.tr.viewDef != null) {
            val eye = idPlane()
            val clip = idPlane()
            val ndc = idVec3()
            tr_main.R_TransformModelToClip(
                def.parms.origin,
                tr_local.tr.viewDef.worldSpace.modelViewMatrix,
                tr_local.tr.viewDef.projectionMatrix,
                eye,
                clip
            )
            tr_main.R_TransformClipToDevice(clip, tr_local.tr.viewDef, ndc)
            def.parms.modelDepthHack = model.DepthHack() * (1.0f - ndc.z)
        }

        // FIXME: if any of the surfaces have deforms, create a frame-temporary model with references to the
        // undeformed surfaces.  This would allow deforms to be light interacting.
        return def.dynamicModel
    }

    fun R_AddDrawSurf(
        tri: srfTriangles_s?, space: viewEntity_s?, renderEntity: renderEntity_s?,
        shader: idMaterial?, scissor: idScreenRect?
    ) {
        tr_light.DEBUG_drawZurf++
        //        TempDump.printCallStack("" + drawZurf);
        val drawSurf: drawSurf_s
        val shaderParms: FloatArray?
        val generatedShaderParms = FloatArray(Material.MAX_ENTITY_SHADER_PARMS)
        drawSurf = drawSurf_s() // R_FrameAlloc(sizeof(drawSurf));
        drawSurf.geo = tri
        drawSurf.space = space
        drawSurf.material = shader
        drawSurf.scissorRect = idScreenRect(scissor)
        drawSurf.sort = shader.GetSort() + tr_local.tr.sortOffset
        drawSurf.dsFlags = 0

        // bumping this offset each time causes surfaces with equal sort orders to still
        // deterministically draw in the order they are added
        tr_local.tr.sortOffset += 0.000001f

        // if it doesn't fit, resize the list
        if (tr_local.tr.viewDef.numDrawSurfs == tr_local.tr.viewDef.maxDrawSurfs) {
            val old = tr_local.tr.viewDef.drawSurfs
            val count: Int
            if (tr_local.tr.viewDef.maxDrawSurfs == 0) {
                tr_local.tr.viewDef.maxDrawSurfs = tr_local.INITIAL_DRAWSURFS
                count = 0
            } else {
                count = tr_local.tr.viewDef.maxDrawSurfs /*sizeof(tr.viewDef.drawSurfs[0])*/
                tr_local.tr.viewDef.maxDrawSurfs *= 2
            }
            tr_local.tr.viewDef.drawSurfs =
                drawSurf_s.Companion.generateArray(tr_local.tr.viewDef.maxDrawSurfs) // R_FrameAlloc(tr.viewDef.maxDrawSurfs);
            //		memcpy( tr.viewDef.drawSurfs, old, count );
            System.arraycopy(old, 0, tr_local.tr.viewDef.drawSurfs, 0, count)
        }
        tr_local.tr.viewDef.drawSurfs[tr_local.tr.viewDef.numDrawSurfs++] = drawSurf

        // process the shader expressions for conditionals / color / texcoords
        val constRegs: FloatArray = shader.ConstantRegisters()
        if (constRegs != null) {
            // shader only uses constant values
            drawSurf.shaderRegisters = constRegs.clone()
        } else {
            val regs = FloatArray(shader.GetNumRegisters()) // R_FrameAlloc(shader.GetNumRegisters());
            drawSurf.shaderRegisters = regs

            // a reference shader will take the calculated stage color value from another shader
            // and use that for the parm0-parm3 of the current shader, which allows a stage of
            // a light model and light flares to pick up different flashing tables from
            // different light shaders
            if (renderEntity.referenceShader != null) {
                // evaluate the reference shader to find our shader parms
                val pStage: shaderStage_t?
                renderEntity.referenceShader.EvaluateRegisters(
                    tr_light.refRegs,
                    renderEntity.shaderParms,
                    tr_local.tr.viewDef,
                    renderEntity.referenceSound
                )
                pStage = renderEntity.referenceShader.GetStage(0)

//			memcpy( generatedShaderParms, renderEntity.shaderParms, sizeof( generatedShaderParms ) );
                System.arraycopy(renderEntity.shaderParms, 0, generatedShaderParms, 0, renderEntity.shaderParms.size)
                generatedShaderParms[0] = tr_light.refRegs[pStage.color.registers[0]]
                generatedShaderParms[1] = tr_light.refRegs[pStage.color.registers[1]]
                generatedShaderParms[2] = tr_light.refRegs[pStage.color.registers[2]]
                shaderParms = generatedShaderParms
            } else {
                // evaluate with the entityDef's shader parms
                shaderParms = renderEntity.shaderParms
            }
            var oldFloatTime = 0f
            var oldTime = 0
            if (space.entityDef != null && space.entityDef.parms.timeGroup != 0) {
                oldFloatTime = tr_local.tr.viewDef.floatTime
                oldTime = tr_local.tr.viewDef.renderView.time
                tr_local.tr.viewDef.floatTime =
                    Game_local.game.GetTimeGroupTime(space.entityDef.parms.timeGroup) * 0.001f
                tr_local.tr.viewDef.renderView.time = Game_local.game.GetTimeGroupTime(space.entityDef.parms.timeGroup)
            }
            shader.EvaluateRegisters(regs, shaderParms, tr_local.tr.viewDef, renderEntity.referenceSound)
            if (space.entityDef != null && space.entityDef.parms.timeGroup != 0) {
                tr_local.tr.viewDef.floatTime = oldFloatTime
                tr_local.tr.viewDef.renderView.time = oldTime
            }
        }

        // check for deformations
        tr_deform.R_DeformDrawSurf(drawSurf)
        when (shader.Texgen()) {
            texgen_t.TG_SKYBOX_CUBE -> tr_light.R_SkyboxTexGen(drawSurf, tr_local.tr.viewDef.renderView.vieworg)
            texgen_t.TG_WOBBLESKY_CUBE -> tr_light.R_WobbleskyTexGen(drawSurf, tr_local.tr.viewDef.renderView.vieworg)
        }

        // check for gui surfaces
        var gui: idUserInterface? = null
        if (null == space.entityDef) {
            gui = shader.GlobalGui()
        } else {
            val guiNum: Int = shader.GetEntityGui() - 1
            if (guiNum >= 0 && guiNum < RenderWorld.MAX_RENDERENTITY_GUI) {
                gui = renderEntity.gui[guiNum]
            }
            if (gui == null) {
                gui = shader.GlobalGui()
            }
        }
        if (gui != null) {
            // force guis on the fast time
            val oldFloatTime: Float
            val oldTime: Int
            oldFloatTime = tr_local.tr.viewDef.floatTime
            oldTime = tr_local.tr.viewDef.renderView.time
            tr_local.tr.viewDef.floatTime = Game_local.game.GetTimeGroupTime(1) * 0.001f
            tr_local.tr.viewDef.renderView.time = Game_local.game.GetTimeGroupTime(1)
            val ndcBounds = idBounds()
            if (!tr_subview.R_PreciseCullSurface(drawSurf, ndcBounds)) {
                // did we ever use this to forward an entity color to a gui that didn't set color?
//			memcpy( tr.guiShaderParms, shaderParms, sizeof( tr.guiShaderParms ) );
                tr_guisurf.R_RenderGuiSurf(gui, drawSurf)
            }
            tr_local.tr.viewDef.floatTime = oldFloatTime
            tr_local.tr.viewDef.renderView.time = oldTime
        }

        // we can't add subviews at this point, because that would
        // increment tr.viewCount, messing up the rest of the surface
        // adds for this view
    }

    /*
     ===============
     R_AddAmbientDrawsurfs

     Adds surfaces for the given viewEntity
     Walks through the viewEntitys list and creates drawSurf_t for each surface of
     each viewEntity that has a non-empty scissorRect
     ===============
     */
    fun R_AddAmbientDrawsurfs(vEntity: viewEntity_s?) {
        var i: Int
        val total: Int
        val def: idRenderEntityLocal?
        var tri: srfTriangles_s
        val model: idRenderModel?
        val shader: Array<idMaterial?> = arrayOf(null)
        def = vEntity.entityDef
        model = if (def.dynamicModel != null) {
            def.dynamicModel
        } else {
            def.parms.hModel
        }

        // add all the surfaces
        total = model.NumSurfaces()
        i = 0
        while (i < total) {
            val surf = model.Surface(i)

            // for debugging, only show a single surface at a time
            if (RenderSystem_init.r_singleSurface.GetInteger() >= 0 && i != RenderSystem_init.r_singleSurface.GetInteger()) {
                i++
                continue
            }
            tri = surf.geometry
            if (null == tri) {
                i++
                continue
            }
            if (0 == tri.numIndexes) {
                i++
                continue
            }
            surf.shader = RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)
            shader[0] = surf.shader
            RenderWorld.R_GlobalShaderOverride(shader)
            if (null == shader[0]) {
                i++
                continue
            }
            if (!shader[0].IsDrawn()) {
                i++
                continue
            }

            // debugging tool to make sure we are have the correct pre-calculated bounds
            if (RenderSystem_init.r_checkBounds.GetBool()) {
                var j: Int
                var k: Int
                j = 0
                while (j < tri.numVerts) {
                    k = 0
                    while (k < 3) {
                        if (tri.verts[j].xyz.get(k) > tri.bounds.get(1, k) + tr_light.CHECK_BOUNDS_EPSILON
                            || tri.verts[j].xyz.get(k) < tri.bounds.get(0, k) - tr_light.CHECK_BOUNDS_EPSILON
                        ) {
                            Common.common.Printf(
                                "bad tri.bounds on %s:%s\n",
                                def.parms.hModel.Name(),
                                shader[0].GetName()
                            )
                            break
                        }
                        if (tri.verts[j].xyz.get(k) > def.referenceBounds.get(1, k) + tr_light.CHECK_BOUNDS_EPSILON
                            || tri.verts[j].xyz.get(k) < def.referenceBounds.get(0, k) - tr_light.CHECK_BOUNDS_EPSILON
                        ) {
                            Common.common.Printf(
                                "bad referenceBounds on %s:%s\n",
                                def.parms.hModel.Name(),
                                shader[0].GetName()
                            )
                            break
                        }
                        k++
                    }
                    if (k != 3) {
                        break
                    }
                    j++
                }
            }
            if (!tr_main.R_CullLocalBox(tri.bounds, vEntity.modelMatrix, 5, tr_local.tr.viewDef.frustum)) {
                def.visibleCount = tr_local.tr.viewCount

                // make sure we have an ambient cache
                if (!tr_light.R_CreateAmbientCache(tri, shader[0].ReceivesLighting())) {
                    // don't add anything if the vertex cache was too full to give us an ambient cache
                    return
                }
                // touch it so it won't get purged
                VertexCache.vertexCache.Touch(tri.ambientCache)
                if (RenderSystem_init.r_useIndexBuffers.GetBool() && TempDump.NOT(tri.indexCache)) {
                    tri.indexCache = VertexCache.vertexCache.Alloc(tri.indexes, tri.numIndexes * Integer.BYTES, true)
                }
                if (tri.indexCache != null) {
                    VertexCache.vertexCache.Touch(tri.indexCache)
                }

                // add the surface for drawing
                tr_light.R_AddDrawSurf(tri, vEntity, vEntity.entityDef.parms, shader[0], vEntity.scissorRect)

                // ambientViewCount is used to allow light interactions to be rejected
                // if the ambient surface isn't visible at all
                tri.ambientViewCount = tr_local.tr.viewCount
            }
            i++
        }

        // add the lightweight decal surfaces
        var decal = def.decals
        while (decal != null) {
            decal.AddDecalDrawSurf(vEntity)
            decal = decal.Next()
        }
    }

    /*
     ==================
     R_CalcEntityScissorRectangle
     ==================
     */
    fun R_CalcEntityScissorRectangle(vEntity: viewEntity_s?): idScreenRect? {
        val bounds = idBounds()
        val def = vEntity.entityDef
        tr_local.tr.viewDef.viewFrustum.ProjectionBounds(
            idBox(def.referenceBounds, def.parms.origin, def.parms.axis),
            bounds
        )
        return tr_main.R_ScreenRectFromViewFrustumBounds(bounds)
    }

    /*
     ===================
     R_ListRenderLightDefs_f
     ===================
     */
    fun R_ListRenderLightDefs_f(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var ldef: idRenderLightLocal?
        if (null == tr_local.tr.primaryWorld) {
            return
        }
        var active = 0
        var totalRef = 0
        var totalIntr = 0
        i = 0
        while (i < tr_local.tr.primaryWorld.lightDefs.Num()) {
            ldef = tr_local.tr.primaryWorld.lightDefs.get(i)
            if (null == ldef) {
                Common.common.Printf("%4d: FREED\n", i)
                i++
                continue
            }

            // count up the interactions
            var iCount = 0
            var inter = ldef.firstInteraction
            while (inter != null) {
                iCount++
                inter = inter.lightNext
            }
            totalIntr += iCount

            // count up the references
            var rCount = 0
            var ref = ldef.references
            while (ref != null) {
                rCount++
                ref = ref.ownerNext
            }
            totalRef += rCount
            Common.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, ldef.lightShader.GetName())
            active++
            i++
        }
        Common.common.Printf("%d lightDefs, %d interactions, %d areaRefs\n", active, totalIntr, totalRef)
    }

    /*
     ===================
     R_ListRenderEntityDefs_f
     ===================
     */
    fun R_ListRenderEntityDefs_f(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var mdef: idRenderEntityLocal?
        if (null == tr_local.tr.primaryWorld) {
            return
        }
        var active = 0
        var totalRef = 0
        var totalIntr = 0
        i = 0
        while (i < tr_local.tr.primaryWorld.entityDefs.Num()) {
            mdef = tr_local.tr.primaryWorld.entityDefs.get(i)
            if (null == mdef) {
                Common.common.Printf("%4d: FREED\n", i)
                i++
                continue
            }

            // count up the interactions
            var iCount = 0
            var inter = mdef.firstInteraction
            while (inter != null) {
                iCount++
                inter = inter.entityNext
            }
            totalIntr += iCount

            // count up the references
            var rCount = 0
            var ref = mdef.entityRefs
            while (ref != null) {
                rCount++
                ref = ref.ownerNext
            }
            totalRef += rCount
            Common.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, mdef.parms.hModel.Name())
            active++
            i++
        }
        Common.common.Printf("total active: %d\n", active)
    }

    /*
     ===================
     R_AddModelSurfaces

     Here is where dynamic models actually get instantiated, and necessary
     interactions get created.  This is all done on a sort-by-model basis
     to keep source data in cache (most likely L2) as any interactions and
     shadows are generated, since dynamic models will typically be lit by
     two or more lights.
     ===================
     */
    fun R_AddModelSurfaces() {
        var vEntity: viewEntity_s?
        var inter: idInteraction?
        var next: idInteraction?
        var model: idRenderModel?
        var i = 0

        // clear the ambient surface list
        tr_local.tr.viewDef.numDrawSurfs = 0
        tr_local.tr.viewDef.maxDrawSurfs = 0 // will be set to INITIAL_DRAWSURFS on R_AddDrawSurf

        // go through each entity that is either visible to the view, or to
        // any light that intersects the view (for shadows)
        vEntity = tr_local.tr.viewDef.viewEntitys
        while (vEntity != null) {
            if (RenderSystem_init.r_useEntityScissors.GetBool()) {
                // calculate the screen area covered by the entity
                val scissorRect = tr_light.R_CalcEntityScissorRectangle(vEntity)
                // intersect with the portal crossing scissor rectangle
                vEntity.scissorRect.Intersect(scissorRect)
                if (RenderSystem_init.r_showEntityScissors.GetBool()) {
                    tr_main.R_ShowColoredScreenRect(vEntity.scissorRect, vEntity.entityDef.index)
                }
            }
            var oldFloatTime = 0f
            var oldTime = 0
            Game_local.game.SelectTimeGroup(vEntity.entityDef.parms.timeGroup)
            if (vEntity.entityDef.parms.timeGroup != 0) {
                oldFloatTime = tr_local.tr.viewDef.floatTime
                oldTime = tr_local.tr.viewDef.renderView.time
                tr_local.tr.viewDef.floatTime =
                    Game_local.game.GetTimeGroupTime(vEntity.entityDef.parms.timeGroup) * 0.001f
                tr_local.tr.viewDef.renderView.time =
                    Game_local.game.GetTimeGroupTime(vEntity.entityDef.parms.timeGroup)
            }
            if (tr_local.tr.viewDef.isXraySubview && vEntity.entityDef.parms.xrayIndex == 1) {
                if (vEntity.entityDef.parms.timeGroup != 0) {
                    tr_local.tr.viewDef.floatTime = oldFloatTime
                    tr_local.tr.viewDef.renderView.time = oldTime
                }
                vEntity = vEntity.next
                i++
                continue
            } else if (!tr_local.tr.viewDef.isXraySubview && vEntity.entityDef.parms.xrayIndex == 2) {
                if (vEntity.entityDef.parms.timeGroup != 0) {
                    tr_local.tr.viewDef.floatTime = oldFloatTime
                    tr_local.tr.viewDef.renderView.time = oldTime
                }
                vEntity = vEntity.next
                i++
                continue
            }

            // add the ambient surface if it has a visible rectangle
            if (!vEntity.scissorRect.IsEmpty()) {
                model = tr_light.R_EntityDefDynamicModel(vEntity.entityDef)
                if (model == null || model.NumSurfaces() <= 0) {
                    if (vEntity.entityDef.parms.timeGroup != 0) {
                        tr_local.tr.viewDef.floatTime = oldFloatTime
                        tr_local.tr.viewDef.renderView.time = oldTime
                    }
                    vEntity = vEntity.next
                    i++
                    continue
                }
                tr_light.R_AddAmbientDrawsurfs(vEntity)
                tr_local.tr.pc.c_visibleViewEntities++
            } else {
                tr_local.tr.pc.c_shadowViewEntities++ //what happens after the scissorsView is set??
            }

            //
            // for all the entity / light interactions on this entity, add them to the view
            //
            if (tr_local.tr.viewDef.isXraySubview) {
                if (vEntity.entityDef.parms.xrayIndex == 2) {
                    inter = vEntity.entityDef.firstInteraction
                    while (inter != null && !inter.IsEmpty()) {
                        next = inter.entityNext
                        if (inter.lightDef.viewCount != tr_local.tr.viewCount) {
                            inter = next
                            continue
                        }
                        inter.AddActiveInteraction()
                        inter = next
                    }
                }
            } else {
                // all empty interactions are at the end of the list so once the
                // first is encountered all the remaining interactions are empty
                inter = vEntity.entityDef.firstInteraction
                while (inter != null && !inter.IsEmpty()) {
                    next = inter.entityNext

                    // skip any lights that aren't currently visible
                    // this is run after any lights that are turned off have already
                    // been removed from the viewLights list, and had their viewCount cleared
                    if (inter.lightDef.viewCount != tr_local.tr.viewCount) {
                        inter = next
                        continue
                    }
                    inter.AddActiveInteraction()
                    inter = next
                }
            }
            if (vEntity.entityDef.parms.timeGroup != 0) {
                tr_local.tr.viewDef.floatTime = oldFloatTime
                tr_local.tr.viewDef.renderView.time = oldTime
            }
            vEntity = vEntity.next
            i++
        }
    }

    /*
     =====================
     R_RemoveUnecessaryViewLights
     =====================
     */
    fun R_RemoveUnecessaryViewLights() {
        var vLight: viewLight_s?

        // go through each visible light
        vLight = tr_local.tr.viewDef.viewLights
        while (vLight != null) {

            // if the light didn't have any lit surfaces visible, there is no need to
            // draw any of the shadows.  We still keep the vLight for debugging
            // draws
            if (TempDump.NOT(vLight.localInteractions[0]) && TempDump.NOT(vLight.globalInteractions[0]) && TempDump.NOT(
                    vLight.translucentInteractions[0]
                )
            ) {
                vLight.localShadows[0] = null
                vLight.globalShadows[0] = null
            }
            vLight = vLight.next
        }
        if (RenderSystem_init.r_useShadowSurfaceScissor.GetBool()) {
            // shrink the light scissor rect to only intersect the surfaces that will actually be drawn.
            // This doesn't seem to actually help, perhaps because the surface scissor
            // rects aren't actually the surface, but only the portal clippings.
            vLight = tr_local.tr.viewDef.viewLights
            while (vLight != null) {
                var surf: drawSurf_s?
                val surfRect = idScreenRect()
                if (!vLight.lightShader.LightCastsShadows()) {
                    vLight = vLight.next
                    continue
                }
                surfRect.Clear()
                surf = vLight.globalInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.localShadows[0]
                while (surf != null) {
                    surf.scissorRect.Intersect(surfRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.localInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.globalShadows[0]
                while (surf != null) {
                    surf.scissorRect.Intersect(surfRect)
                    surf = surf.nextOnLight
                }
                surf = vLight.translucentInteractions[0]
                while (surf != null) {
                    surfRect.Union(surf.scissorRect)
                    surf = surf.nextOnLight
                }
                vLight.scissorRect.Intersect(surfRect)
                vLight = vLight.next
            }
        }
    }
}