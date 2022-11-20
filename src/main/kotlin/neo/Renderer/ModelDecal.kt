package neo.Renderer

import neo.Renderer.Material.decalInfo_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.viewEntity_s
import neo.framework.Common
import neo.framework.DemoFile.idDemoFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.CFloat
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec5
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

/**
 *
 */
object ModelDecal {
    /*
     ===============================================================================

     Decals are lightweight primitives for bullet / blood marks.
     Decals with common materials will be merged together, but additional
     decals will be allocated as needed. The material should not be
     one that receives lighting, because no interactions are generated
     for these lightweight surfaces.

     FIXME:	Decals on models in portalled off areas do not get freed
     until the area becomes visible again.

     ===============================================================================
     */
    const val NUM_DECAL_BOUNDING_PLANES = 6

    class decalProjectionInfo_s {
        val boundingPlanes: Array<idPlane> = idPlane.generateArray(6)
        var fadeDepth = 0f
        val fadePlanes: Array<idPlane> = idPlane.generateArray(2)
        var force = false
        var material: idMaterial? = idMaterial()
        var parallel = false
        var projectionBounds: idBounds = idBounds()
        val projectionOrigin: idVec3 = idVec3()
        var startTime = 0
        val textureAxis: Array<idPlane> = idPlane.generateArray(2)
    }

    class idRenderModelDecal {
        private val indexStartTime: IntArray = IntArray(MAX_DECAL_INDEXES)
        private val   /*glIndex_t*/indexes: IntArray = IntArray(MAX_DECAL_INDEXES)

        //
        private var material: idMaterial?
        private var nextDecal: idRenderModelDecal?
        private val tri: srfTriangles_s
        private val vertDepthFade: FloatArray = FloatArray(MAX_DECAL_VERTS)
        private val verts: ArrayList<idDrawVert> = ArrayList<idDrawVert>(MAX_DECAL_VERTS)

        // Creates a deal on the given model.
        fun CreateDecal(model: idRenderModel, localInfo: decalProjectionInfo_s) {

            // check all model surfaces
            for (surfNum in 0 until model.NumSurfaces()) {
                val surf = model.Surface(surfNum)

                // if no geometry or no shader
                if (null == surf.geometry || null == surf.shader) {
                    continue
                }

                // decals and overlays use the same rules
                if (!localInfo.force && !surf.shader!!.AllowOverlays()) {
                    continue
                }
                val stri = surf.geometry!!

                // if the triangle bounds do not overlap with projection bounds
                if (!localInfo.projectionBounds.IntersectsBounds(stri.bounds)) {
                    continue
                }

                // allocate memory for the cull bits
                val cullBits = ByteArray(stri.numVerts)

                // catagorize all points by the planes
                Simd.SIMDProcessor.DecalPointCull(
                    cullBits,
                    localInfo.boundingPlanes,
                    stri.verts.toTypedArray(),
                    stri.numVerts
                )

                // find triangles inside the projection volume
                var triNum = 0
                var index = 0
                while (index < stri.numIndexes) {
                    val v1 = stri.indexes[index + 0]
                    val v2 = stri.indexes[index + 1]
                    val v3 = stri.indexes[index + 2]

                    // skip triangles completely off one side
                    if (cullBits[v1] and cullBits[v2] and cullBits[v3] != 0.toByte()) {
                        index += 3
                        triNum++
                        continue
                    }

                    // skip back facing triangles
                    if (stri.facePlanes.isNotEmpty() && stri.facePlanesCalculated
                        && stri.facePlanes[triNum].Normal().times(
                            localInfo.boundingPlanes[NUM_DECAL_BOUNDING_PLANES - 2].Normal()
                        ) < -0.1f
                    ) {
                        index += 3
                        triNum++
                        continue
                    }

                    // create a winding with texture coordinates for the triangle
                    val fw = idFixedWinding()
                    fw.SetNumPoints(3)
                    if (localInfo.parallel) {
                        for (j in 0..2) {
                            fw[j].set(stri.verts[stri.indexes[index + j]].xyz)
                            fw[j].s = localInfo.textureAxis[0].Distance(fw[j].ToVec3())
                            fw[j].t = localInfo.textureAxis[1].Distance(fw[j].ToVec3())
                        }
                    } else {
                        for (j in 0..2) {
                            val dir = idVec3()
                            val scale = CFloat()
                            fw[j].set(stri.verts[stri.indexes[index + j]].xyz)
                            dir.set(fw[j].ToVec3().minus(localInfo.projectionOrigin))
                            localInfo.boundingPlanes[NUM_DECAL_BOUNDING_PLANES - 1]
                                .RayIntersection(fw[j].ToVec3(), dir, scale)
                            dir.set(fw[j].ToVec3().plus(dir.times(scale._val)))
                            fw[j].s = localInfo.textureAxis[0].Distance(dir)
                            fw[j].t = localInfo.textureAxis[1].Distance(dir)
                        }
                    }
                    val orBits: Int = (cullBits[v1] or cullBits[v2] or cullBits[v3]).toInt()

                    // clip the exact surface triangle to the projection volume
                    for (j in 0 until NUM_DECAL_BOUNDING_PLANES) {
                        if (orBits and (1 shl j) != 0) {
                            if (!fw.ClipInPlace(localInfo.boundingPlanes[j].unaryMinus())) {
                                break
                            }
                        }
                    }
                    if (fw.GetNumPoints() == 0) {
                        index += 3
                        triNum++
                        continue
                    }
                    AddDepthFadedWinding(
                        fw,
                        localInfo.material!!,
                        localInfo.fadePlanes,
                        localInfo.fadeDepth,
                        localInfo.startTime
                    )
                    index += 3
                    triNum++
                }
            }
        }

        // Updates the vertex colors, removing any faded indexes,
        // then copy the verts to temporary vertex cache and adds a drawSurf.
        fun AddDecalDrawSurf(space: viewEntity_s) {
            var i: Int
            var j: Int
            val maxTime: Int
            var f: Float
            val decalInfo: decalInfo_t
            if (tri.numIndexes == 0) {
                return
            }

            // fade down all the verts with time
            decalInfo = material!!.GetDecalInfo()
            maxTime = decalInfo.stayTime + decalInfo.fadeTime

            // set vertex colors and remove faded triangles
            i = 0
            while (i < tri.numIndexes) {
                var deltaTime = tr_local.tr.viewDef!!.renderView.time - indexStartTime[i]
                if (deltaTime > maxTime) {
                    i += 3
                    continue
                }
                if (deltaTime <= decalInfo.stayTime) {
                    i += 3
                    continue
                }
                deltaTime -= decalInfo.stayTime
                f = deltaTime.toFloat() / decalInfo.fadeTime
                j = 0
                while (j < 3) {
                    val ind = tri.indexes[i + j]
                    for (k in 0..3) {
                        val fcolor = decalInfo.start[k] + (decalInfo.end[k] - decalInfo.start[k]) * f
                        var icolor = idMath.FtoiFast(fcolor * vertDepthFade[ind] * 255.0f)
                        if (icolor < 0) {
                            icolor = 0
                        } else if (icolor > 255) {
                            icolor = 255
                        }
                        tri.verts[ind].color[k] = icolor.toByte()
                    }
                    j++
                }
                i += 3
            }

            // copy the tri and indexes to temp heap memory,
            // because if we are running multi-threaded, we wouldn't
            // be able to reorganize the index list
            val newTri: srfTriangles_s? //(srfTriangles_s) R_FrameAlloc(sizeof(newTri));
            newTri = tri

            // copy the current vertexes to temp vertex cache
            newTri.ambientCache =
                VertexCache.vertexCache.AllocFrameTempIdDrawVert(tri.verts, tri.numVerts * idDrawVert.BYTES)

            // create the drawsurf
            tr_light.R_AddDrawSurf(newTri, space, space.entityDef.parms, material!!, space.scissorRect)
        }

        // Returns the next decal in the chain.
        fun Next(): idRenderModelDecal? {
            return nextDecal
        }

        fun ReadFromDemoFile(f: idDemoFile?) {
            // FIXME: implement
        }

        fun WriteToDemoFile(f: idDemoFile?) {
            // FIXME: implement
        }

        // Adds the winding triangles to the appropriate decal in the
        // chain, creating a new one if necessary.
        private fun AddWinding(
            w: idWinding,
            decalMaterial: idMaterial,
            fadePlanes: Array<idPlane> /*[2]*/,
            fadeDepth: Float,
            startTime: Int
        ) {
            var i: Int
            val invFadeDepth: Float
            var fade: Float
            val decalInfo: decalInfo_t
            if ((material == null || material === decalMaterial)
                && tri.numVerts + w.GetNumPoints() < MAX_DECAL_VERTS && tri.numIndexes + (w.GetNumPoints() - 2) * 3 < MAX_DECAL_INDEXES
            ) {
                material = decalMaterial

                // add to this decal
                decalInfo = material!!.GetDecalInfo()
                invFadeDepth = -1.0f / fadeDepth
                i = 0
                while (i < w.GetNumPoints()) {
                    fade = fadePlanes[0].Distance(w[i].ToVec3()) * invFadeDepth
                    if (fade < 0.0f) {
                        fade = fadePlanes[1].Distance(w[i].ToVec3()) * invFadeDepth
                    }
                    if (fade < 0.0f) {
                        fade = 0.0f
                    } else if (fade > 0.99f) {
                        fade = 1.0f
                    }
                    fade = 1.0f - fade
                    vertDepthFade[tri.numVerts + i] = fade
                    tri.verts[tri.numVerts + i].xyz.set(w[i].ToVec3())
                    tri.verts[tri.numVerts + i].st[0] = w[i].s
                    tri.verts[tri.numVerts + i].st[1] = w[i].t
                    for (k in 0..3) {
                        var icolor = idMath.FtoiFast(decalInfo.start[k] * fade * 255.0f)
                        if (icolor < 0) {
                            icolor = 0
                        } else if (icolor > 255) {
                            icolor = 255
                        }
                        tri.verts[tri.numVerts + i].color[k] = icolor.toByte()
                    }
                    i++
                }
                i = 2
                while (i < w.GetNumPoints()) {
                    tri.indexes[tri.numIndexes + 0] = tri.numVerts
                    tri.indexes[tri.numIndexes + 1] = tri.numVerts + i - 1
                    tri.indexes[tri.numIndexes + 2] = tri.numVerts + i
                    indexStartTime[tri.numIndexes + 2] = startTime
                    indexStartTime[tri.numIndexes + 1] = indexStartTime[tri.numIndexes + 2]
                    indexStartTime[tri.numIndexes] = indexStartTime[tri.numIndexes + 1]
                    tri.numIndexes += 3
                    i++
                }
                tri.numVerts += w.GetNumPoints()
                return
            }

            // if we are at the end of the list, create a new decal
            if (null == nextDecal) {
                nextDecal = Alloc()
            }
            // let the next decal on the chain take a look
            nextDecal!!.AddWinding(w, decalMaterial, fadePlanes, fadeDepth, startTime)
        }

        // Adds depth faded triangles for the winding to the appropriate
        // decal in the chain, creating a new one if necessary.
        // The part of the winding at the front side of both fade planes is not faded.
        // The parts at the back sides of the fade planes are faded with the given depth.
        private fun AddDepthFadedWinding(
            w: idWinding,
            decalMaterial: idMaterial,
            fadePlanes: Array<idPlane> /*[2]*/,
            fadeDepth: Float,
            startTime: Int
        ) {
            val front = idFixedWinding(w)
            val back: idFixedWinding
            back = idFixedWinding()
            if (front.Split(back, fadePlanes.get(0), 0.1f) == Plane.SIDE_CROSS) {
                AddWinding(back, decalMaterial, fadePlanes, fadeDepth, startTime)
            }
            if (front.Split(back, fadePlanes.get(1), 0.1f) == Plane.SIDE_CROSS) {
                AddWinding(back, decalMaterial, fadePlanes, fadeDepth, startTime)
            }
            AddWinding(front, decalMaterial, fadePlanes, fadeDepth, startTime)
        }

        companion object {
            private const val MAX_DECAL_INDEXES = 60
            private const val MAX_DECAL_VERTS = 40

            //								~idRenderModelDecal( void );
            //
            fun Alloc(): idRenderModelDecal {
                return idRenderModelDecal()
            }

            fun Free(decal: idRenderModelDecal?) {
//	delete decal;
            }

            // Creates decal projection info.
            fun CreateProjectionInfo(
                info: decalProjectionInfo_s,
                winding: idFixedWinding,
                projectionOrigin: idVec3,
                parallel: Boolean,
                fadeDepth: Float,
                material: idMaterial,
                startTime: Int
            ): Boolean {
                if (winding.GetNumPoints() != NUM_DECAL_BOUNDING_PLANES - 2) {
                    Common.common.Printf(
                        "idRenderModelDecal::CreateProjectionInfo: winding must have %d points\n",
                        NUM_DECAL_BOUNDING_PLANES - 2
                    )
                    return false
                }
                assert(material != null)
                info.projectionOrigin.set(projectionOrigin)
                info.material = material
                info.parallel = parallel
                info.fadeDepth = fadeDepth
                info.startTime = startTime
                info.force = false

                // get the winding plane and the depth of the projection volume
                val windingPlane = idPlane()
                winding.GetPlane(windingPlane)
                val depth = windingPlane.Distance(projectionOrigin)

                // find the bounds for the projection
                winding.GetBounds(info.projectionBounds)
                if (parallel) {
                    info.projectionBounds.ExpandSelf(depth)
                } else {
                    info.projectionBounds.AddPoint(projectionOrigin)
                }

                // calculate the world space projection volume bounding planes, positive sides face outside the decal
                if (parallel) {
                    for (i in 0 until winding.GetNumPoints()) {
                        val edge =
                            winding.get((i + 1) % winding.GetNumPoints()).ToVec3().minus(winding.get(i).ToVec3())
                        info.boundingPlanes[i].Normal().Cross(windingPlane.Normal(), edge)
                        info.boundingPlanes[i].Normalize()
                        info.boundingPlanes[i].FitThroughPoint(winding.get(i).ToVec3())
                    }
                } else {
                    for (i in 0 until winding.GetNumPoints()) {
                        info.boundingPlanes[i].FromPoints(
                            projectionOrigin,
                            winding.get(i).ToVec3(),
                            winding.get((i + 1) % winding.GetNumPoints()).ToVec3()
                        )
                    }
                }
                info.boundingPlanes[NUM_DECAL_BOUNDING_PLANES - 2].set(windingPlane)
                info.boundingPlanes[NUM_DECAL_BOUNDING_PLANES - 2].minusAssign(3, depth)
                info.boundingPlanes[NUM_DECAL_BOUNDING_PLANES - 1].set(windingPlane.unaryMinus())

                // fades will be from these plane
                info.fadePlanes[0].set(windingPlane)
                info.fadePlanes[0].minusAssign(3, fadeDepth)
                info.fadePlanes[1].set(windingPlane.unaryMinus())
                info.fadePlanes[1].plusAssign(3, depth - fadeDepth)

                // calculate the texture vectors for the winding
                var len: Float
                val texArea: Float
                val inva: Float
                val temp = idVec3()
                val d0 = idVec5()
                val d1 = idVec5()
                val a = winding.get(0)
                val b = winding.get(1)
                val c = winding.get(2)
                d0.set(b.ToVec3().minus(a.ToVec3()))
                d0.s = b.s - a.s
                d0.t = b.t - a.t
                d1.set(c.ToVec3().minus(a.ToVec3()))
                d1.s = c.s - a.s
                d1.t = c.t - a.t
                texArea = d0[3] * d1[4] - d0[4] * d1[3]
                inva = 1.0f / texArea
                temp[0] = (d0[0] * d1[4] - d0[4] * d1[0]) * inva
                temp[1] = (d0[1] * d1[4] - d0[4] * d1[1]) * inva
                temp[2] = (d0[2] * d1[4] - d0[4] * d1[2]) * inva
                len = temp.Normalize()
                info.textureAxis[0].SetNormal(temp.times(1.0f / len))
                info.textureAxis[0][3] = winding.get(0).s - winding.get(0).ToVec3().times(info.textureAxis[0].Normal())
                temp[0] = (d0[3] * d1[0] - d0[0] * d1[3]) * inva
                temp[1] = (d0[3] * d1[1] - d0[1] * d1[3]) * inva
                temp[2] = (d0[3] * d1[2] - d0[2] * d1[3]) * inva
                len = temp.Normalize()
                info.textureAxis[1].SetNormal(temp.times(1.0f / len))
                info.textureAxis[1][3] = winding.get(0).s - winding.get(0).ToVec3().times(info.textureAxis[1].Normal())
                return true
            }

            // Transform the projection info from global space to local.
            fun GlobalProjectionInfoToLocal(
                localInfo: decalProjectionInfo_s,
                info: decalProjectionInfo_s,
                origin: idVec3,
                axis: idMat3
            ) {
                val modelMatrix = FloatArray(16)
                tr_main.R_AxisToModelMatrix(axis, origin, modelMatrix)
                for (j in 0 until NUM_DECAL_BOUNDING_PLANES) {
                    tr_main.R_GlobalPlaneToLocal(
                        modelMatrix,
                        info.boundingPlanes[j],
                        localInfo.boundingPlanes[j]
                    )
                }
                tr_main.R_GlobalPlaneToLocal(modelMatrix, info.fadePlanes[0], localInfo.fadePlanes[0])
                tr_main.R_GlobalPlaneToLocal(modelMatrix, info.fadePlanes[1], localInfo.fadePlanes[1])
                tr_main.R_GlobalPlaneToLocal(modelMatrix, info.textureAxis[0], localInfo.textureAxis[0])
                tr_main.R_GlobalPlaneToLocal(modelMatrix, info.textureAxis[1], localInfo.textureAxis[1])
                tr_main.R_GlobalPointToLocal(modelMatrix, info.projectionOrigin, localInfo.projectionOrigin)
                localInfo.projectionBounds = info.projectionBounds
                localInfo.projectionBounds.TranslateSelf(origin.unaryMinus())
                localInfo.projectionBounds.RotateSelf(axis.Transpose())
                localInfo.material = info.material
                localInfo.parallel = info.parallel
                localInfo.fadeDepth = info.fadeDepth
                localInfo.startTime = info.startTime
                localInfo.force = info.force
            }

            // Remove decals that are completely faded away.
            fun RemoveFadedDecals(decals: idRenderModelDecal?, time: Int): idRenderModelDecal? {
                var i: Int
                var j: Int
                val minTime: Int
                var newNumIndexes: Int
                var newNumVerts: Int
                val inUse = IntArray(MAX_DECAL_VERTS)
                val decalInfo: decalInfo_t
                val nextDecal: idRenderModelDecal?
                if (decals == null) {
                    return null
                }

                // recursively free any next decals
                decals.nextDecal = RemoveFadedDecals(decals.nextDecal, time)

                // free the decals if no material set
                if (decals.material == null) {
                    nextDecal = decals.nextDecal
                    Free(decals)
                    return nextDecal
                }
                decalInfo = decals.material!!.GetDecalInfo()
                minTime = time - (decalInfo.stayTime + decalInfo.fadeTime)
                newNumIndexes = 0
                i = 0
                while (i < decals.tri.numIndexes) {
                    if (decals.indexStartTime[i] > minTime) {
                        // keep this triangle
                        if (newNumIndexes != i) {
                            j = 0
                            while (j < 3) {
                                decals.tri.indexes[newNumIndexes + j] = decals.tri.indexes[i + j]
                                decals.indexStartTime[newNumIndexes + j] = decals.indexStartTime[i + j]
                                j++
                            }
                        }
                        newNumIndexes += 3
                    }
                    i += 3
                }

                // free the decals if all trianges faded away
                if (newNumIndexes == 0) {
                    nextDecal = decals.nextDecal
                    Free(decals)
                    return nextDecal
                }
                decals.tri.numIndexes = newNumIndexes

//	memset( inUse, 0, sizeof( inUse ) );
                Arrays.fill(inUse, 0)
                i = 0
                while (i < decals.tri.numIndexes) {
                    inUse[decals.tri.indexes[i]] = 1
                    i++
                }
                newNumVerts = 0
                i = 0
                while (i < decals.tri.numVerts) {
                    if (0 == inUse[i]) {
                        i++
                        continue
                    }
                    decals.tri.verts[newNumVerts] = decals.tri.verts[i]
                    decals.vertDepthFade[newNumVerts] = decals.vertDepthFade[i]
                    inUse[i] = newNumVerts
                    newNumVerts++
                    i++
                }
                decals.tri.numVerts = newNumVerts
                i = 0
                while (i < decals.tri.numIndexes) {
                    decals.tri.indexes[i] = inUse[decals.tri.indexes[i]]
                    i++
                }
                return decals
            }
        }

        //
        //
        init {
//	memset( &tri, 0, sizeof( tri ) );
            tri = srfTriangles_s()
            tri.verts = verts
            tri.indexes = indexes
            material = null
            nextDecal = null
        }
    }
}