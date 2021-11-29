package neo.Renderer

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.viewDef_s
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object Model_beam {
    /*

     This is a simple dynamic model that just creates a stretched quad between
     two points that faces the view, like a dynamic deform tube.

     */
    val beam_SnapshotName: String? = "_beam_Snapshot_"

    /*
     ===============================================================================

     Beam model

     ===============================================================================
     */
    class idRenderModelBeam : idRenderModelStatic() {
        override fun IsDynamicModel(): dynamicModel_t? {
            return dynamicModel_t.DM_CONTINUOUS // regenerate for every view
        }

        override fun IsLoaded(): Boolean {
            return true // don't ever need to load
        }

        override fun InstantiateDynamicModel(
            renderEntity: renderEntity_s?,
            viewDef: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel = cachedModel
            val staticModel: idRenderModelStatic?
            val tri: srfTriangles_s?
            var surf: modelSurface_s? = modelSurface_s()
            if (cachedModel != null) {
//		delete cachedModel;
                cachedModel = null
            }
            if (renderEntity == null || viewDef == null) {
//		delete cachedModel;
                return null
            }
            if (cachedModel != null) {

//		assert( dynamic_cast<idRenderModelStatic *>( cachedModel ) != null );
//		assert( idStr.Icmp( cachedModel.Name(), beam_SnapshotName ) == 0 );
                staticModel = cachedModel as idRenderModelStatic?
                surf = staticModel.Surface(0)
                tri = surf.geometry
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(Model_beam.beam_SnapshotName)
                tri = tr_trisurf.R_AllocStaticTriSurf()
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, 4)
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, 6)
                tri.verts[0].Clear()
                tri.verts[0].st.oSet(0, 0f)
                tri.verts[0].st.oSet(1, 0f)
                tri.verts[1].Clear()
                tri.verts[1].st.oSet(0, 0f)
                tri.verts[1].st.oSet(1, 1f)
                tri.verts[2].Clear()
                tri.verts[2].st.oSet(0, 1f)
                tri.verts[2].st.oSet(1, 0f)
                tri.verts[3].Clear()
                tri.verts[3].st.oSet(0, 1f)
                tri.verts[3].st.oSet(1, 1f)
                tri.indexes[0] = 0
                tri.indexes[1] = 2
                tri.indexes[2] = 1
                tri.indexes[3] = 2
                tri.indexes[4] = 3
                tri.indexes[5] = 1
                tri.numVerts = 4
                tri.numIndexes = 6
                surf.geometry = tri
                surf.id = 0
                surf.shader = tr_local.tr.defaultMaterial
                staticModel.AddSurface(surf)
            }
            val target = idVec3(renderEntity.shaderParms, RenderWorld.SHADERPARM_BEAM_END_X)

            // we need the view direction to project the minor axis of the tube
            // as the view changes
            val localView = idVec3()
            val localTarget = idVec3()
            val modelMatrix = FloatArray(16)
            tr_main.R_AxisToModelMatrix(renderEntity.axis, renderEntity.origin, modelMatrix)
            tr_main.R_GlobalPointToLocal(modelMatrix, viewDef.renderView.vieworg, localView)
            tr_main.R_GlobalPointToLocal(modelMatrix, target, localTarget)
            val major = idVec3(localTarget)
            val minor = idVec3()
            val mid = idVec3(localTarget.times(0.5f))
            val dir = idVec3(mid.oMinus(localView))
            minor.Cross(major, dir)
            minor.Normalize()
            if (renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] != 0.0f) {
                minor.timesAssign(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] * 0.5f)
            }
            val red = idMath.FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] * 255.0f).toByte()
            val green = idMath.FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] * 255.0f).toByte()
            val blue = idMath.FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] * 255.0f).toByte()
            val alpha = idMath.FtoiFast(renderEntity.shaderParms[RenderWorld.SHADERPARM_ALPHA] * 255.0f).toByte()
            tri.verts[0].xyz.oSet(minor)
            tri.verts[0].color[0] = red
            tri.verts[0].color[1] = green
            tri.verts[0].color[2] = blue
            tri.verts[0].color[3] = alpha
            tri.verts[1].xyz.oSet(minor.oNegative())
            tri.verts[1].color[0] = red
            tri.verts[1].color[1] = green
            tri.verts[1].color[2] = blue
            tri.verts[1].color[3] = alpha
            tri.verts[2].xyz.oSet(localTarget.oPlus(minor))
            tri.verts[2].color[0] = red
            tri.verts[2].color[1] = green
            tri.verts[2].color[2] = blue
            tri.verts[2].color[3] = alpha
            tri.verts[3].xyz.oSet(localTarget.oMinus(minor))
            tri.verts[3].color[0] = red
            tri.verts[3].color[1] = green
            tri.verts[3].color[2] = blue
            tri.verts[3].color[3] = alpha
            tr_trisurf.R_BoundTriSurf(tri)
            staticModel.bounds = idBounds(tri.bounds)
            return staticModel
        }

        override fun Bounds(renderEntity: renderEntity_s?): idBounds? {
            val b = idBounds()
            b.Zero()
            if (null == renderEntity) {
                b.ExpandSelf(8.0f)
            } else {
                val target = idVec3(renderEntity.shaderParms, RenderWorld.SHADERPARM_BEAM_END_X)
                val localTarget = idVec3()
                val modelMatrix = FloatArray(16)
                tr_main.R_AxisToModelMatrix(renderEntity.axis, renderEntity.origin, modelMatrix)
                tr_main.R_GlobalPointToLocal(modelMatrix, target, localTarget)
                b.AddPoint(localTarget)
                if (renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] != 0.0f) {
                    b.ExpandSelf(renderEntity.shaderParms[RenderWorld.SHADERPARM_BEAM_WIDTH] * 0.5f)
                }
            }
            return b
        }
    }
}