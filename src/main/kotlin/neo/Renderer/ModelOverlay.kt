package neo.Renderer

import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.framework.Common
import neo.framework.DemoFile.idDemoFile
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec2

/**
 *
 */
object ModelOverlay {
    /*
     ===============================================================================

     Render model overlay for adding decals on top of dynamic models.

     ===============================================================================
     */
    const val MAX_OVERLAY_SURFACES = 16

    internal class overlayVertex_s {
        var st: FloatArray = FloatArray(2)
        var vertexNum = 0

        constructor()
        constructor(`val`: overlayVertex_s) {
            vertexNum = `val`.vertexNum
            System.arraycopy(`val`.st, 0, st, 0, st.size)
        }
    }

    private class overlaySurface_s {
        var indexes: IntArray
        var numIndexes = 0
        var numVerts = 0
        var surfaceId = 0
        var surfaceNum: CInt = CInt()
        var verts: Array<overlayVertex_s>
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class overlayMaterial_s {
        var material: Material.idMaterial? = null
        val surfaces: idList<overlaySurface_s> = idList()
    }

    class idRenderModelOverlay  // ~idRenderModelOverlay();
    {
        //
        private val materials: idList<overlayMaterial_s> = idList()

        /*
         =====================
         idRenderModelOverlay::CreateOverlay

         This projects on both front and back sides to avoid seams
         The material should be clamped, because entire triangles are added, some of which
         may extend well past the 0.0 to 1.0 texture range
         =====================
         */
        // Projects an overlay onto deformable geometry and can be added to
        // a render entity to allow decals on top of dynamic models.
        // This does not generate tangent vectors, so it can't be used with
        // light interaction shaders. Materials for overlays should always
        // be clamped, because the projected texcoords can run well off the
        // texture since no new clip vertexes are generated.
        fun CreateOverlay(model: idRenderModel?, localTextureAxis: Array<idPlane?>? /*[2]*/, mtr: idMaterial?) {
            var i: Int
            var maxVerts: Int
            var maxIndexes: Int
            var surfNum: Int
            val overlay: idRenderModelOverlay? = null

            // count up the maximum possible vertices and indexes per surface
            maxVerts = 0
            maxIndexes = 0
            surfNum = 0
            while (surfNum < model.NumSurfaces()) {
                val surf = model.Surface(surfNum)
                if (surf.geometry.numVerts > maxVerts) {
                    maxVerts = surf.geometry.numVerts
                }
                if (surf.geometry.numIndexes > maxIndexes) {
                    maxIndexes = surf.geometry.numIndexes
                }
                surfNum++
            }

            // make temporary buffers for the building process
            val overlayVerts = arrayOfNulls<overlayVertex_s?>(maxVerts)
            val   /*glIndex_t*/overlayIndexes = IntArray(maxIndexes)

            // pull out the triangles we need from the base surfaces
            surfNum = 0
            while (surfNum < model.NumBaseSurfaces()) {
                val surf = model.Surface(surfNum)
                var d: Float
                if (null == surf.geometry || null == surf.shader) {
                    surfNum++
                    continue
                }

                // some surfaces can explicitly disallow overlays
                if (!surf.shader.AllowOverlays()) {
                    surfNum++
                    continue
                }
                val stri = surf.geometry

                // try to cull the whole surface along the first texture axis
                d = stri.bounds.PlaneDistance(localTextureAxis.get(0))
                if (d < 0.0f || d > 1.0f) {
                    surfNum++
                    continue
                }

                // try to cull the whole surface along the second texture axis
                d = stri.bounds.PlaneDistance(localTextureAxis.get(1))
                if (d < 0.0f || d > 1.0f) {
                    surfNum++
                    continue
                }
                val cullBits = ByteArray(stri.numVerts)
                val texCoords = arrayOfNulls<idVec2?>(stri.numVerts)
                Simd.SIMDProcessor.OverlayPointCull(cullBits, texCoords, localTextureAxis, stri.verts, stri.numVerts)
                val   /*glIndex_t */vertexRemap = IntArray(stri.numVerts)
                Simd.SIMDProcessor.Memset(vertexRemap, -1, stri.numVerts)

                // find triangles that need the overlay
                var numVerts = 0
                var numIndexes = 0
                var triNum = 0
                var index = 0
                while (index < stri.numIndexes) {
                    val v1 = stri.indexes[index + 0]
                    val v2 = stri.indexes[index + 1]
                    val v3 = stri.indexes[index + 2]

                    // skip triangles completely off one side
                    if (cullBits[v1] and cullBits[v2] and cullBits[v3] != 0) {
                        index += 3
                        triNum++
                        continue
                    }

                    // we could do more precise triangle culling, like the light interaction does, if desired
                    // keep this triangle
                    for (vnum in 0..2) {
                        val ind = stri.indexes[index + vnum]
                        if (vertexRemap[ind] == -1) {
                            vertexRemap[ind] = numVerts
                            overlayVerts[numVerts].vertexNum = ind
                            overlayVerts[numVerts].st.get(0) = texCoords[ind].get(0)
                            overlayVerts[numVerts].st.get(1) = texCoords[ind].get(1)
                            numVerts++
                        }
                        overlayIndexes[numIndexes++] = vertexRemap[ind]
                    }
                    index += 3
                    triNum++
                }
                if (0 == numIndexes) {
                    surfNum++
                    continue
                }
                val s = overlaySurface_s() // Mem_Alloc(sizeof(overlaySurface_t));
                s.surfaceNum.setVal(surfNum)
                s.surfaceId = surf.id
                s.verts = arrayOfNulls<overlayVertex_s?>(numVerts) // Mem_Alloc(numVerts);
                //                memcpy(s.verts, overlayVerts, numVerts * sizeof(s.verts[0]));
                i = 0
                while (i < numVerts) {
                    s.verts.get(i) = overlayVertex_s(overlayVerts[i])
                    i++
                }
                s.numVerts = numVerts
                s.indexes = IntArray(numIndexes) ///*(glIndex_t *)*/Mem_Alloc(numIndexes);
                //                memcpy(s.indexes, overlayIndexes, numIndexes * sizeof(s.indexes[0]));
                System.arraycopy(overlayIndexes, 0, s.indexes, 0, numIndexes)
                s.numIndexes = numIndexes
                i = 0
                while (i < materials.Num()) {
                    if (materials.get(i).material === mtr) {
                        break
                    }
                    i++
                }
                if (i < materials.Num()) {
                    materials.get(i).surfaces.Append(s)
                } else {
                    val mat = overlayMaterial_s()
                    mat.material = mtr
                    mat.surfaces.Append(s)
                    materials.Append(mat)
                }
                surfNum++
            }

            // remove the oldest overlay surfaces if there are too many per material
            i = 0
            while (i < materials.Num()) {
                while (materials.get(i).surfaces.Num() > ModelOverlay.MAX_OVERLAY_SURFACES) {
                    FreeSurface(materials.get(i).surfaces.get(0))
                    materials.get(i).surfaces.RemoveIndex(0)
                }
                i++
            }
        }

        // Creates new model surfaces for baseModel, which should be a static instantiation of a dynamic model.
        fun AddOverlaySurfacesToModel(baseModel: idRenderModel) {
            var i: Int
            var j: Int
            var k: Int
            var numVerts: Int
            var numIndexes: Int
            val surfaceNum = CInt()
            var baseSurf: modelSurface_s?
            val staticModel: idRenderModelStatic?
            var surf: overlaySurface_s?
            var newTri: srfTriangles_s
            var newSurf: modelSurface_s?
            if (baseModel == null || baseModel.IsDefaultModel()) {
                return
            }

            // md5 models won't have any surfaces when r_showSkel is set
            if (0 == baseModel.NumSurfaces()) {
                return
            }
            if (baseModel.IsDynamicModel() != dynamicModel_t.DM_STATIC) {
                Common.common.Error("idRenderModelOverlay::AddOverlaySurfacesToModel: baseModel is not a static model")
            }

//	assert( dynamic_cast<idRenderModelStatic *>(baseModel) != null );
            staticModel = baseModel as idRenderModelStatic?
            staticModel.overlaysAdded = 0
            if (0 == materials.Num()) {
                staticModel.DeleteSurfacesWithNegativeId()
                return
            }
            k = 0
            while (k < materials.Num()) {
                numIndexes = 0
                numVerts = numIndexes
                i = 0
                while (i < materials.get(k).surfaces.Num()) {
                    numVerts += materials.get(k).surfaces.get(i).numVerts
                    numIndexes += materials.get(k).surfaces.get(i).numIndexes
                    i++
                }
                if (staticModel.FindSurfaceWithId(-1 - k, surfaceNum)) {
                    newSurf = staticModel.surfaces.get(surfaceNum._val)
                } else {
                    newSurf = staticModel.surfaces.Alloc()
                    newSurf.geometry = null
                    newSurf.shader = materials.get(k).material
                    newSurf.id = -1 - k
                }
                if (newSurf.geometry == null || newSurf.geometry.numVerts < numVerts || newSurf.geometry.numIndexes < numIndexes) {
                    tr_trisurf.R_FreeStaticTriSurf(newSurf.geometry)
                    newSurf.geometry = tr_trisurf.R_AllocStaticTriSurf()
                    tr_trisurf.R_AllocStaticTriSurfVerts(newSurf.geometry, numVerts)
                    tr_trisurf.R_AllocStaticTriSurfIndexes(newSurf.geometry, numIndexes)
                    Simd.SIMDProcessor.Memset(newSurf.geometry.verts, 0, numVerts)
                } else {
                    tr_trisurf.R_FreeStaticTriSurfVertexCaches(newSurf.geometry)
                }
                newTri = newSurf.geometry
                numIndexes = 0
                numVerts = numIndexes
                i = 0
                while (i < materials.get(k).surfaces.Num()) {
                    surf = materials.get(k).surfaces.get(i)

                    // get the model surface for this overlay surface
                    baseSurf = if (surf.surfaceNum._val < staticModel.NumSurfaces()) {
                        staticModel.Surface(surf.surfaceNum._val)
                    } else {
                        null
                    }

                    // if the surface ids no longer match
                    if (null == baseSurf || baseSurf.id != surf.surfaceId) {
                        // find the surface with the correct id
                        baseSurf = if (staticModel.FindSurfaceWithId(surf.surfaceId, surf.surfaceNum)) {
                            staticModel.Surface(surf.surfaceNum._val)
                        } else {
                            // the surface with this id no longer exists
                            FreeSurface(surf)
                            materials.get(k).surfaces.RemoveIndex(i)
                            i--
                            i++
                            continue
                        }
                    }

                    // copy indexes;
                    j = 0
                    while (j < surf.numIndexes) {
                        newTri.indexes[numIndexes + j] = numVerts + surf.indexes.get(j)
                        j++
                    }
                    numIndexes += surf.numIndexes

                    // copy vertices
                    j = 0
                    while (j < surf.numVerts) {
                        val overlayVert = surf.verts.get(j)
                        newTri.verts[numVerts].st.set(0, overlayVert.st.get(0))
                        newTri.verts[numVerts].st.set(1, overlayVert.st.get(1))
                        if (overlayVert.vertexNum >= baseSurf.geometry.numVerts) {
                            // This can happen when playing a demofile and a model has been changed since it was recorded, so just issue a warning and go on.
                            Common.common.Warning("idRenderModelOverlay::AddOverlaySurfacesToModel: overlay vertex out of range.  Model has probably changed since generating the overlay.")
                            FreeSurface(surf)
                            materials.get(k).surfaces.RemoveIndex(i)
                            staticModel.DeleteSurfaceWithId(newSurf.id)
                            return
                        }
                        newTri.verts[numVerts].xyz.set(baseSurf.geometry.verts[overlayVert.vertexNum].xyz)
                        numVerts++
                        j++
                    }
                    i++
                }
                newTri.numVerts = numVerts
                newTri.numIndexes = numIndexes
                tr_trisurf.R_BoundTriSurf(newTri)
                staticModel.overlaysAdded++ // so we don't create an overlay on an overlay surface
                k++
            }
        }

        fun ReadFromDemoFile(f: idDemoFile) {
            // FIXME: implement
        }

        fun WriteToDemoFile(f: idDemoFile) {
            // FIXME: implement
        }

        //
        private fun FreeSurface(surface: overlaySurface_s) {
            if (surface.verts != null) {
//                Mem_Free(surface.verts);
                surface.verts = null
            }
            if (surface.indexes != null) {
//                Mem_Free(surface.indexes);
                surface.indexes = null
            }
            surface.clear()
        }

        companion object {
            fun Alloc(): idRenderModelOverlay {
                return idRenderModelOverlay()
            }

            @Deprecated("")
            fun Free(overlay: idRenderModelOverlay) {
//	delete overlay;
            }

            // Removes overlay surfaces from the model.
            fun RemoveOverlaySurfacesFromModel(baseModel: idRenderModel) {
                val staticModel: idRenderModelStatic?

//	assert( dynamic_cast<idRenderModelStatic *>(baseModel) != NULL );
                staticModel = baseModel as idRenderModelStatic
                staticModel.DeleteSurfacesWithNegativeId()
                staticModel.overlaysAdded = 0
            }
        }
    }
}