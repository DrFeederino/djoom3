package neo.Renderer

import neo.Renderer.*
import neo.Renderer.Material.dynamicidImage_t
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Material.textureStage_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewDef_s
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object tr_subview {
    /*
     =================
     R_MirrorPoint
     =================
     */
    fun R_MirrorPoint(`in`: idVec3?, surface: orientation_t?, camera: orientation_t?, out: idVec3?) {
        var i: Int
        val local = idVec3()
        val transformed = idVec3()
        var d: Float
        local.set(`in`.minus(surface.origin))
        transformed.set(Vector.getVec3_origin())
        i = 0
        while (i < 3) {
            d = local.times(surface.axis.get(i))
            transformed.plusAssign(camera.axis.get(i).times(d))
            i++
        }
        out.set(transformed.oPlus(camera.origin))
    }

    /*
     =================
     R_MirrorVector
     =================
     */
    fun R_MirrorVector(`in`: idVec3?, surface: orientation_t?, camera: orientation_t?, out: idVec3?) {
        var i: Int
        var d: Float
        out.set(Vector.getVec3_origin())
        i = 0
        while (i < 3) {
            d = `in`.times(surface.axis.get(i))
            out.plusAssign(camera.axis.get(i).times(d))
            i++
        }
    }

    /*
     =============
     R_PlaneForSurface

     Returns the plane for the first triangle in the surface
     FIXME: check for degenerate triangle?
     =============
     */
    fun R_PlaneForSurface(tri: srfTriangles_s?, plane: idPlane?) {
        val v1: idDrawVert?
        val v2: idDrawVert?
        val v3: idDrawVert?
        v1 = tri.verts[tri.indexes[0]]
        v2 = tri.verts[tri.indexes[1]]
        v3 = tri.verts[tri.indexes[2]]
        plane.FromPoints(v1.xyz, v2.xyz, v3.xyz)
    }

    /*
     =========================
     R_PreciseCullSurface

     Check the surface for visibility on a per-triangle basis
     for cases when it is going to be VERY expensive to draw (subviews)

     If not culled, also returns the bounding box of the surface in
     Normalized Device Coordinates, so it can be used to crop the scissor rect.

     OPTIMIZE: we could also take exact portal passing into consideration
     =========================
     */
    fun R_PreciseCullSurface(drawSurf: drawSurf_s?, ndcBounds: idBounds?): Boolean {
        val tri: srfTriangles_s?
        val numTriangles: Int
        val clip = idPlane()
        val eye = idPlane()
        var i: Int
        var j: Int
        var pointOr: Int
        var pointAnd: Int
        val localView = idVec3()
        val w = idFixedWinding()
        tri = drawSurf.geo
        pointOr = 0
        pointAnd = 0.inv()

        // get an exact bounds of the triangles for scissor cropping
        ndcBounds.Clear()
        i = 0
        while (i < tri.numVerts) {

//		int j;
            var pointFlags: Int
            tr_main.R_TransformModelToClip(
                tri.verts[i].xyz, drawSurf.space.modelViewMatrix,
                tr_local.tr.viewDef.projectionMatrix, eye, clip
            )
            pointFlags = 0
            j = 0
            while (j < 3) {
                if (clip.get(j) >= clip.get(3)) {
                    pointFlags = pointFlags or (1 shl j * 2)
                } else if (clip.get(j) <= -clip.get(3)) {
                    pointFlags = pointFlags or (1 shl j * 2 + 1)
                }
                j++
            }
            pointAnd = pointAnd and pointFlags
            pointOr = pointOr or pointFlags
            i++
        }

        // trivially reject
        if (pointAnd != 0) {
            return true
        }

        // backface and frustum cull
        numTriangles = tri.numIndexes / 3
        tr_main.R_GlobalPointToLocal(drawSurf.space.modelMatrix, tr_local.tr.viewDef.renderView.vieworg, localView)
        i = 0
        while (i < tri.numIndexes) {
            val dir = idVec3()
            val normal = idVec3()
            var dot: Float
            val d1 = idVec3()
            val d2 = idVec3()
            val v1 = tri.verts[tri.indexes[i]].xyz
            val v2 = tri.verts[tri.indexes[i + 1]].xyz
            val v3 = tri.verts[tri.indexes[i + 2]].xyz

            // this is a hack, because R_GlobalPointToLocal doesn't work with the non-normalized
            // axis that we get from the gui view transform.  It doesn't hurt anything, because
            // we know that all gui generated surfaces are front facing
            if (tr_local.tr.guiRecursionLevel == 0) {
                // we don't care that it isn't normalized,
                // all we want is the sign
                d1.set(v2.minus(v1))
                d2.set(v3.minus(v1))
                normal.set(d2.Cross(d1))
                dir.set(v1.minus(localView))
                dot = normal.times(dir)
                if (dot >= 0.0f) {
                    return true
                }
            }

            // now find the exact screen bounds of the clipped triangle
            w.SetNumPoints(3)
            w.oSet(0, tr_main.R_LocalPointToGlobal(drawSurf.space.modelMatrix, v1))
            w.oSet(1, tr_main.R_LocalPointToGlobal(drawSurf.space.modelMatrix, v2))
            w.oSet(2, tr_main.R_LocalPointToGlobal(drawSurf.space.modelMatrix, v3))
            w.oGet(2).t = 0.0f
            w.oGet(2).s = w.oGet(2).t
            w.oGet(1).t = w.oGet(2).s
            w.oGet(1).s = w.oGet(1).t
            w.oGet(0).t = w.oGet(1).s
            w.oGet(0).s = w.oGet(0).t
            j = 0
            while (j < 4) {
                if (!w.ClipInPlace(tr_local.tr.viewDef.frustum[j].unaryMinus(), 0.1f)) {
                    break
                }
                j++
            }
            j = 0
            while (j < w.GetNumPoints()) {
                val screen = idVec3()
                tr_main.R_GlobalToNormalizedDeviceCoordinates(w.oGet(j).ToVec3(), screen)
                ndcBounds.AddPoint(screen)
                j++
            }
            i += 3
        }

        // if we don't enclose any area, return
        return ndcBounds.IsCleared()
    }

    /*
     ========================
     R_MirrorViewBySurface
     ========================
     */
    fun R_MirrorViewBySurface(drawSurf: drawSurf_s?): viewDef_s? {
        val parms: viewDef_s
        val surface = orientation_t()
        val camera = orientation_t()
        val originalPlane = idPlane()
        val plane = idPlane()

        // copy the viewport size from the original
        parms = viewDef_s(tr_local.tr.viewDef) //        parms = (viewDef_s) R_FrameAlloc(sizeof(parms));
        parms.renderView.viewID = 0 // clear to allow player bodies to show up, and suppress view weapons
        parms.isSubview = true
        parms.isMirror = true

        // create plane axis for the portal we are seeing
        tr_subview.R_PlaneForSurface(drawSurf.geo, originalPlane)
        tr_main.R_LocalPlaneToGlobal(drawSurf.space.modelMatrix, originalPlane, plane)
        surface.origin.set(plane.Normal().times(-plane.get(3)))
        surface.axis.set(0, plane.Normal())
        surface.axis.get(0).NormalVectors(surface.axis.get(1), surface.axis.get(2))
        surface.axis.set(2, surface.axis.get(2).oNegative())
        camera.origin.set(surface.origin)
        camera.axis.set(0, surface.axis.get(0).oNegative())
        camera.axis.set(1, surface.axis.get(1))
        camera.axis.set(2, surface.axis.get(2))

        // set the mirrored origin and axis
        tr_subview.R_MirrorPoint(tr_local.tr.viewDef.renderView.vieworg, surface, camera, parms.renderView.vieworg)
        tr_subview.R_MirrorVector(
            tr_local.tr.viewDef.renderView.viewaxis.get(0),
            surface,
            camera,
            parms.renderView.viewaxis.get(0)
        )
        tr_subview.R_MirrorVector(
            tr_local.tr.viewDef.renderView.viewaxis.get(1),
            surface,
            camera,
            parms.renderView.viewaxis.get(1)
        )
        tr_subview.R_MirrorVector(
            tr_local.tr.viewDef.renderView.viewaxis.get(2),
            surface,
            camera,
            parms.renderView.viewaxis.get(2)
        )

        // make the view origin 16 units away from the center of the surface
        val viewOrigin = idVec3(drawSurf.geo.bounds.get(0).oPlus(drawSurf.geo.bounds.get(1)).oMultiply(0.5f))
        viewOrigin.oPluSet(originalPlane.Normal().times(16f))
        parms.initialViewAreaOrigin.set(tr_main.R_LocalPointToGlobal(drawSurf.space.modelMatrix, viewOrigin))

        // set the mirror clip plane
        parms.numClipPlanes = 1
        parms.clipPlanes[0] = idPlane()
        parms.clipPlanes[0].set(camera.axis.get(0).oNegative())
        parms.clipPlanes[0].set(3, -camera.origin.times(parms.clipPlanes[0].Normal()))
        return parms
    }

    /*
     ========================
     R_XrayViewBySurface
     ========================
     */
    fun R_XrayViewBySurface(drawSurf: drawSurf_s?): viewDef_s? {
        val parms: viewDef_s?
        //	orientation_t	surface, camera;
//	idPlane			originalPlane, plane;

        // copy the viewport size from the original
//	parms = (viewDef_s )R_FrameAlloc( sizeof( parms ) );
        parms = tr_local.tr.viewDef
        parms.renderView.viewID = 0 // clear to allow player bodies to show up, and suppress view weapons
        parms.isSubview = true
        parms.isXraySubview = true
        return parms
    }

    /*
     ===============
     R_RemoteRender
     ===============
     */
    fun R_RemoteRender(surf: drawSurf_s?, stage: textureStage_t?) {
        val parms: viewDef_s?

        // remote views can be reused in a single frame
        if (stage.dynamicFrameCount == tr_local.tr.frameCount) {
            return
        }

        // if the entity doesn't have a remoteRenderView, do nothing
        if (null == surf.space.entityDef.parms.remoteRenderView) {
            return
        }

        // copy the viewport size from the original
//	parms = (viewDef_t *)R_FrameAlloc( sizeof( *parms ) );
        parms = tr_local.tr.viewDef
        parms.isSubview = true
        parms.isMirror = false
        parms.renderView = surf.space.entityDef.parms.remoteRenderView
        parms.renderView.viewID = 0 // clear to allow player bodies to show up, and suppress view weapons
        parms.initialViewAreaOrigin.set(parms.renderView.vieworg)
        tr_local.tr.CropRenderSize(stage.width, stage.height, true)
        parms.renderView.x = 0
        parms.renderView.y = 0
        parms.renderView.width = RenderSystem.SCREEN_WIDTH
        parms.renderView.height = RenderSystem.SCREEN_HEIGHT
        tr_local.tr.RenderViewToViewport(parms.renderView, parms.viewport)
        parms.scissor.x1 = 0
        parms.scissor.y1 = 0
        parms.scissor.x2 = parms.viewport.x2 - parms.viewport.x1
        parms.scissor.y2 = parms.viewport.y2 - parms.viewport.y1
        parms.superView = tr_local.tr.viewDef
        parms.subviewSurface = surf

        // generate render commands for it
        tr_main.R_RenderView(parms)

        // copy this rendering to the image
        stage.dynamicFrameCount = tr_local.tr.frameCount
        if (null == stage.image[0]) {
            stage.image[0] = Image.globalImages.scratchImage
        }
        tr_local.tr.CaptureRenderToImage(stage.image[0].imgName.toString())
        tr_local.tr.UnCrop()
    }

    /*
     =================
     R_MirrorRender
     =================
     */
    fun R_MirrorRender(surf: drawSurf_s?, stage: textureStage_t?, scissor: idScreenRect?) {
        val parms: viewDef_s?

        // remote views can be reused in a single frame
        if (stage.dynamicFrameCount == tr_local.tr.frameCount) {
            return
        }

        // issue a new view command
        parms = tr_subview.R_MirrorViewBySurface(surf)
        if (null == parms) {
            return
        }
        tr_local.tr.CropRenderSize(stage.width, stage.height, true)
        parms.renderView.x = 0
        parms.renderView.y = 0
        parms.renderView.width = RenderSystem.SCREEN_WIDTH
        parms.renderView.height = RenderSystem.SCREEN_HEIGHT
        tr_local.tr.RenderViewToViewport(parms.renderView, parms.viewport)
        parms.scissor.x1 = 0
        parms.scissor.y1 = 0
        parms.scissor.x2 = parms.viewport.x2 - parms.viewport.x1
        parms.scissor.y2 = parms.viewport.y2 - parms.viewport.y1
        parms.superView = tr_local.tr.viewDef
        parms.subviewSurface = surf

        // triangle culling order changes with mirroring
        parms.isMirror = parms.isMirror xor tr_local.tr.viewDef.isMirror

        // generate render commands for it
        tr_main.R_RenderView(parms)

        // copy this rendering to the image
        stage.dynamicFrameCount = tr_local.tr.frameCount
        stage.image[0] = Image.globalImages.scratchImage
        tr_local.tr.CaptureRenderToImage(stage.image[0].imgName.toString())
        tr_local.tr.UnCrop()
    }

    /*
     =================
     R_XrayRender
     =================
     */
    fun R_XrayRender(surf: drawSurf_s?, stage: textureStage_t?, scissor: idScreenRect?) {
        val parms: viewDef_s?

        // remote views can be reused in a single frame
        if (stage.dynamicFrameCount == tr_local.tr.frameCount) {
            return
        }

        // issue a new view command
        parms = tr_subview.R_XrayViewBySurface(surf)
        if (null == parms) {
            return
        }
        tr_local.tr.CropRenderSize(stage.width, stage.height, true)
        parms.renderView.x = 0
        parms.renderView.y = 0
        parms.renderView.width = RenderSystem.SCREEN_WIDTH
        parms.renderView.height = RenderSystem.SCREEN_HEIGHT
        tr_local.tr.RenderViewToViewport(parms.renderView, parms.viewport)
        parms.scissor.x1 = 0
        parms.scissor.y1 = 0
        parms.scissor.x2 = parms.viewport.x2 - parms.viewport.x1
        parms.scissor.y2 = parms.viewport.y2 - parms.viewport.y1
        parms.superView = tr_local.tr.viewDef
        parms.subviewSurface = surf

        // triangle culling order changes with mirroring
        parms.isMirror = parms.isMirror xor tr_local.tr.viewDef.isMirror // != 0 );

        // generate render commands for it
        tr_main.R_RenderView(parms)

        // copy this rendering to the image
        stage.dynamicFrameCount = tr_local.tr.frameCount
        stage.image[0] = Image.globalImages.scratchImage2
        tr_local.tr.CaptureRenderToImage(stage.image[0].imgName.toString())
        tr_local.tr.UnCrop()
    }

    /*
     ==================
     R_GenerateSurfaceSubview
     ==================
     */
    fun R_GenerateSurfaceSubview(drawSurf: drawSurf_s?): Boolean {
        val ndcBounds = idBounds()
        var parms: viewDef_s?
        val shader: idMaterial?

        // for testing the performance hit
        if (RenderSystem_init.r_skipSubviews.GetBool()) {
            return false
        }
        if (tr_subview.R_PreciseCullSurface(drawSurf, ndcBounds)) {
            return false
        }
        shader = drawSurf.material

        // never recurse through a subview surface that we are
        // already seeing through
        parms = tr_local.tr.viewDef
        while (parms != null) {
            if (parms.subviewSurface != null && parms.subviewSurface.geo === drawSurf.geo && parms.subviewSurface.space.entityDef === drawSurf.space.entityDef) {
                break
            }
            parms = parms.superView
        }
        if (parms != null) {
            return false
        }

        // crop the scissor bounds based on the precise cull
        val scissor = idScreenRect()
        val v = tr_local.tr.viewDef.viewport
        scissor.x1 = v.x1 + ((v.x2 - v.x1 + 1) * 0.5f * (ndcBounds.get(0, 0) + 1.0f)).toInt()
        scissor.y1 = v.y1 + ((v.y2 - v.y1 + 1) * 0.5f * (ndcBounds.get(0, 1) + 1.0f)).toInt()
        scissor.x2 = v.x1 + ((v.x2 - v.x1 + 1) * 0.5f * (ndcBounds.get(1, 0) + 1.0f)).toInt()
        scissor.y2 = v.y1 + ((v.y2 - v.y1 + 1) * 0.5f * (ndcBounds.get(1, 1) + 1.0f)).toInt()

        // nudge a bit for safety
        scissor.Expand()
        scissor.Intersect(tr_local.tr.viewDef.scissor)
        if (scissor.IsEmpty()) {
            // cropped out
            return false
        }

        // see what kind of subview we are making
        if (shader.GetSort() != Material.SS_SUBVIEW.toFloat()) {
            for (i in 0 until shader.GetNumStages()) {
                val stage: shaderStage_t = shader.GetStage(i)
                when (stage.texture.dynamic) {
                    dynamicidImage_t.DI_REMOTE_RENDER -> tr_subview.R_RemoteRender(drawSurf, stage.texture)
                    dynamicidImage_t.DI_MIRROR_RENDER -> tr_subview.R_MirrorRender(
                        drawSurf,  /*const_cast<textureStage_t *>*/
                        stage.texture,
                        scissor
                    )
                    dynamicidImage_t.DI_XRAY_RENDER -> tr_subview.R_XrayRender(
                        drawSurf,  /*const_cast<textureStage_t *>*/
                        stage.texture,
                        scissor
                    )
                }
            }
            return true
        }

        // issue a new view command
        parms = tr_subview.R_MirrorViewBySurface(drawSurf)
        if (null == parms) {
            return false
        }
        parms.scissor = scissor
        parms.superView = tr_local.tr.viewDef
        parms.subviewSurface = drawSurf

        // triangle culling order changes with mirroring
        parms.isMirror = parms.isMirror xor tr_local.tr.viewDef.isMirror // != 0 );

        // generate render commands for it
        tr_main.R_RenderView(parms)
        return true
    }

    /*
     ================
     R_GenerateSubViews

     If we need to render another view to complete the current view,
     generate it first.

     It is important to do this after all drawSurfs for the current
     view have been generated, because it may create a subview which
     would change tr.viewCount.
     ================
     */
    fun R_GenerateSubViews(): Boolean {
        var drawSurf: drawSurf_s?
        var i: Int
        var subviews: Boolean
        var shader: idMaterial?

        // for testing the performance hit
        if (RenderSystem_init.r_skipSubviews.GetBool()) {
            return false
        }
        subviews = false

        // scan the surfaces until we either find a subview, or determine
        // there are no more subview surfaces.
        i = 0
        while (i < tr_local.tr.viewDef.numDrawSurfs) {
            drawSurf = tr_local.tr.viewDef.drawSurfs[i]
            shader = drawSurf.material
            if (null == shader || !shader.HasSubview()) {
                i++
                continue
            }
            if (tr_subview.R_GenerateSurfaceSubview(drawSurf)) {
                subviews = true
            }
            i++
        }
        return subviews
    }

    internal class orientation_t {
        var axis: idMat3? = idMat3()
        val origin: idVec3? = idVec3()
    }
}