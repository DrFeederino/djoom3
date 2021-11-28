package neo.Renderer

import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewEntity_s
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_stencilshadow.shadowGen_t
import neo.TempDump
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.util.stream.Stream

/**
 *
 */
object Interaction {
    /*
     ===============================================================================

     Interaction between entityDef surfaces and a lightDef.

     Interactions with no lightTris and no shadowTris are still
     valid, because they show that a given entityDef / lightDef
     do not interact, even though they share one or more areas.

     ===============================================================================
     */
    const val LIGHT_CLIP_EPSILON = 0.1f
    val LIGHT_TRIS_DEFERRED // = -03146;//((srfTriangles_s *)-1)
            : srfTriangles_s? = null
    const val MAX_CLIPPED_POINTS = 20
    var LIGHT_CULL_ALL_FRONT //((byte *)-1)
            : ByteArray?

    /**
     *
     */
    /*
     ================
     R_CalcInteractionFacing

     Determines which triangles of the surface are facing towards the light origin.

     The facing array should be allocated with one extra index than
     the number of surface triangles, which will be used to handle dangling
     edge silhouettes.
     ================
     */
    fun R_CalcInteractionFacing(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s?,
        light: idRenderLightLocal?,
        cullInfo: srfCullInfo_t?
    ) {
        val localLightOrigin = idVec3()
        if (cullInfo.facing != null) {
            return
        }
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, localLightOrigin)
        val numFaces = tri.numIndexes / 3
        if (TempDump.NOT(*tri.facePlanes) || !tri.facePlanesCalculated) {
            tr_trisurf.R_DeriveFacePlanes( /*const_cast<srfTriangles_s *>*/tri)
        }
        cullInfo.facing = ByteArray(numFaces + 1) // R_StaticAlloc((numFaces + 1) * sizeof(cullInfo.facing[0]));

        // calculate back face culling
        val planeSide = FloatArray(numFaces)

        // exact geometric cull against face
        Simd.SIMDProcessor.Dot(planeSide, localLightOrigin, tri.facePlanes, numFaces)
        Simd.SIMDProcessor.CmpGE(cullInfo.facing, planeSide, 0.0f, numFaces)
        cullInfo.facing.get(numFaces) = 1 // for dangling edges to reference
    }

    /*
     =====================
     R_CalcInteractionCullBits

     We want to cull a little on the sloppy side, because the pre-clipping
     of geometry to the lights in dmap will give many cases that are right
     at the border we throw things out on the border, because if any one
     vertex is clearly inside, the entire triangle will be accepted.
     =====================
     */
    fun R_CalcInteractionCullBits(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s?,
        light: idRenderLightLocal?,
        cullInfo: srfCullInfo_t?
    ) {
        var i: Int
        var frontBits: Int
        if (cullInfo.cullBits != null) {
            return
        }
        frontBits = 0

        // cull the triangle surface bounding box
        i = 0
        while (i < 6) {
            tr_main.R_GlobalPlaneToLocal(ent.modelMatrix, light.frustum[i].oNegative(), cullInfo.localClipPlanes.get(i))

            // get front bits for the whole surface
            if (tri.bounds.PlaneDistance(cullInfo.localClipPlanes.get(i)) >= Interaction.LIGHT_CLIP_EPSILON) {
                frontBits = frontBits or (1 shl i)
            }
            i++
        }

        // if the surface is completely inside the light frustum
        if (frontBits == (1 shl 6) - 1) {
            cullInfo.cullBits = Interaction.LIGHT_CULL_ALL_FRONT
            return
        }
        cullInfo.cullBits = ByteArray(tri.numVerts) // R_StaticAlloc(tri.numVerts /* sizeof(cullInfo.cullBits[0])*/);
        Simd.SIMDProcessor.Memset(cullInfo.cullBits, 0, tri.numVerts /* sizeof(cullInfo.cullBits[0])*/)
        val planeSide = FloatArray(tri.numVerts)
        i = 0
        while (i < 6) {

            // if completely infront of this clipping plane
            if (frontBits and (1 shl i) != 0) {
                i++
                continue
            }
            Simd.SIMDProcessor.Dot(planeSide, cullInfo.localClipPlanes.get(i), tri.verts, tri.numVerts)
            Simd.SIMDProcessor.CmpLT(
                cullInfo.cullBits,
                i.toByte(),
                planeSide,
                Interaction.LIGHT_CLIP_EPSILON,
                tri.numVerts
            )
            i++
        }
    }

    /*
     ================
     R_FreeInteractionCullInfo
     ================
     */
    fun R_FreeInteractionCullInfo(cullInfo: srfCullInfo_t?) {
//        if (cullInfo.facing != null) {
//            R_StaticFree(cullInfo.facing);
        cullInfo.facing = null
        //        }
//        if (cullInfo.cullBits != null) {
//            if (cullInfo.cullBits != LIGHT_CULL_ALL_FRONT) {
//                R_StaticFree(cullInfo.cullBits);
//            }
        cullInfo.cullBits = null
        //        }
    }

    /*
     =============
     R_ChopWinding

     Clips a triangle from one buffer to another, setting edge flags
     The returned buffer may be the same as inNum if no clipping is done
     If entirely clipped away, clipTris[returned].numVerts == 0

     I have some worries about edge flag cases when polygons are clipped
     multiple times near the epsilon.
     =============
     */
    fun R_ChopWinding(clipTris: Array<Interaction.clipTri_t?>? /*[2]*/, inNum: Int, plane: idPlane?): Int {
        val `in`: Interaction.clipTri_t?
        val out: Interaction.clipTri_t?
        val dists = FloatArray(Interaction.MAX_CLIPPED_POINTS)
        val sides = IntArray(Interaction.MAX_CLIPPED_POINTS)
        val counts = IntArray(3)
        var dot: Float
        var i: Int
        var j: Int
        val mid = idVec3()
        var front: Boolean
        `in` = clipTris.get(inNum)
        out = clipTris.get(inNum xor 1)
        counts[2] = 0
        counts[1] = counts[2]
        counts[0] = counts[1]

        // determine sides for each point
        front = false
        i = 0
        while (i < `in`.numVerts) {
            dot = `in`.verts[i].oMultiply(plane.Normal()) + plane.oGet(3)
            dists[i] = dot
            if (dot < Interaction.LIGHT_CLIP_EPSILON) {    // slop onto the back
                sides[i] = Plane.SIDE_BACK
            } else {
                sides[i] = Plane.SIDE_FRONT
                if (dot > Interaction.LIGHT_CLIP_EPSILON) {
                    front = true
                }
            }
            counts[sides[i]]++
            i++
        }

        // if none in front, it is completely clipped away
        if (!front) {
            `in`.numVerts = 0
            return inNum
        }
        if (0 == counts[Plane.SIDE_BACK]) {
            return inNum // inout stays the same
        }

        // avoid wrapping checks by duplicating first value to end
        sides[i] = sides[0]
        dists[i] = dists[0]
        `in`.verts[`in`.numVerts].oSet(`in`.verts[0])
        out.numVerts = 0
        i = 0
        while (i < `in`.numVerts) {
            val p1 = `in`.verts[i]
            if (sides[i] == Plane.SIDE_FRONT) {
                out.verts[out.numVerts].oSet(p1)
                out.numVerts++
            }
            if (sides[i + 1] == sides[i]) {
                i++
                continue
            }

            // generate a split point
            val p2 = `in`.verts[i + 1]
            dot = dists[i] / (dists[i] - dists[i + 1])
            j = 0
            while (j < 3) {
                mid.oSet(j, p1.oGet(j) + dot * (p2.oGet(j) - p1.oGet(j)))
                j++
            }
            out.verts[out.numVerts] = mid
            out.numVerts++
            i++
        }
        return inNum xor 1
    }

    /*
     ===================
     R_ClipTriangleToLight

     Returns false if nothing is left after clipping
     ===================
     */
    fun R_ClipTriangleToLight(
        a: idVec3?,
        b: idVec3?,
        c: idVec3?,
        planeBits: Int,
        frustum: Array<idPlane?>? /*[6]*/
    ): Boolean {
        var i: Int
        val pingPong = arrayOfNulls<Interaction.clipTri_t?>(2)
        var p: Int
        pingPong[0].numVerts = 3
        pingPong[0].verts[0].oSet(a)
        pingPong[0].verts[1].oSet(b)
        pingPong[0].verts[2].oSet(c)
        p = 0
        i = 0
        while (i < 6) {
            if (planeBits and (1 shl i) != 0) {
                p = Interaction.R_ChopWinding(pingPong, p, frustum.get(i))
                if (pingPong[p].numVerts < 1) {
                    return false
                }
            }
            i++
        }
        return true
    }

    /*
     ====================
     R_CreateLightTris

     The resulting surface will be a subset of the original triangles,
     it will never clip triangles, but it may cull on a per-triangle basis.
     ====================
     */
    fun R_CreateLightTris(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s?, light: idRenderLightLocal?,
        shader: idMaterial?, cullInfo: srfCullInfo_t?
    ): srfTriangles_s? {
        var i: Int
        var numIndexes: Int
        val indexes: IntArray
        val newTri: srfTriangles_s?
        var c_backfaced: Int
        var c_distance: Int
        var bounds = idBounds()
        val includeBackFaces: Boolean
        var faceNum: Int
        tr_local.tr.pc.c_createLightTris++
        c_backfaced = 0
        c_distance = 0
        numIndexes = 0
        //	indexes = null;

        // it is debatable if non-shadowing lights should light back faces. we aren't at the moment
        includeBackFaces = (RenderSystem_init.r_lightAllBackFaces.GetBool() || light.lightShader.LightEffectsBackSides()
                || shader.ReceivesLightingOnBackSides()
                || ent.parms.noSelfShadow || ent.parms.noShadow)

        // allocate a new surface for the lit triangles
        newTri = tr_trisurf.R_AllocStaticTriSurf()

        // save a reference to the original surface
        newTri.ambientSurface =  /*const_cast<srfTriangles_s *>*/tri

        // the light surface references the verts of the ambient surface
        newTri.numVerts = tri.numVerts
        tr_trisurf.R_ReferenceStaticTriSurfVerts(newTri, tri)

        // calculate cull information
        if (!includeBackFaces) {
            Interaction.R_CalcInteractionFacing(ent, tri, light, cullInfo)
        }
        Interaction.R_CalcInteractionCullBits(ent, tri, light, cullInfo)

        // if the surface is completely inside the light frustum
        if (cullInfo.cullBits == Interaction.LIGHT_CULL_ALL_FRONT) {

            // if we aren't self shadowing, let back facing triangles get
            // through so the smooth shaded bump maps light all the way around
            if (includeBackFaces) {

                // the whole surface is lit so the light surface just references the indexes of the ambient surface
                tr_trisurf.R_ReferenceStaticTriSurfIndexes(newTri, tri)
                numIndexes = tri.numIndexes
                bounds = idBounds(tri.bounds)
            } else {

                // the light tris indexes are going to be a subset of the original indexes so we generally
                // allocate too much memory here but we decrease the memory block when the number of indexes is known
                tr_trisurf.R_AllocStaticTriSurfIndexes(newTri, tri.numIndexes)

                // back face cull the individual triangles
                indexes = newTri.indexes
                val facing = cullInfo.facing
                faceNum = 0.also { i = it }
                while (i < tri.numIndexes) {
                    if (0 == facing.get(faceNum)) {
                        c_backfaced++
                        i += 3
                        faceNum++
                        continue
                    }
                    indexes[numIndexes + 0] = tri.indexes[i + 0]
                    indexes[numIndexes + 1] = tri.indexes[i + 1]
                    indexes[numIndexes + 2] = tri.indexes[i + 2]
                    numIndexes += 3
                    i += 3
                    faceNum++
                }

                // get bounds for the surface
                Simd.SIMDProcessor.MinMax(bounds.oGet(0), bounds.oGet(1), tri.verts, indexes, numIndexes)

                // decrease the size of the memory block to the size of the number of used indexes
                tr_trisurf.R_ResizeStaticTriSurfIndexes(newTri, numIndexes)
            }
        } else {

            // the light tris indexes are going to be a subset of the original indexes so we generally
            // allocate too much memory here but we decrease the memory block when the number of indexes is known
            tr_trisurf.R_AllocStaticTriSurfIndexes(newTri, tri.numIndexes)

            // cull individual triangles
            indexes = newTri.indexes
            val facing = cullInfo.facing
            val cullBits = cullInfo.cullBits
            faceNum = 0.also { i = it }
            while (i < tri.numIndexes) {
                var i1: Int
                var i2: Int
                var i3: Int

                // if we aren't self shadowing, let back facing triangles get
                // through so the smooth shaded bump maps light all the way around
                if (!includeBackFaces) {
                    // back face cull
                    if (0 == facing.get(faceNum)) {
                        c_backfaced++
                        i += 3
                        faceNum++
                        continue
                    }
                }
                i1 = tri.indexes[i + 0]
                i2 = tri.indexes[i + 1]
                i3 = tri.indexes[i + 2]

                // fast cull outside the frustum
                // if all three points are off one plane side, it definately isn't visible
                if (cullBits.get(i1) and cullBits.get(i2) and cullBits.get(i3) != 0) {
                    c_distance++
                    i += 3
                    faceNum++
                    continue
                }
                if (RenderSystem_init.r_usePreciseTriangleInteractions.GetBool()) {
                    // do a precise clipped cull if none of the points is completely inside the frustum
                    // note that we do not actually use the clipped triangle, which would have Z fighting issues.
                    if (cullBits.get(i1) and cullBits.get(i2) and cullBits.get(i3) != 0) {
                        val cull: Int = cullBits.get(i1) or cullBits.get(i2) or cullBits.get(i3)
                        if (!Interaction.R_ClipTriangleToLight(
                                tri.verts[i1].xyz,
                                tri.verts[i2].xyz,
                                tri.verts[i3].xyz,
                                cull,
                                cullInfo.localClipPlanes
                            )
                        ) {
                            i += 3
                            faceNum++
                            continue
                        }
                    }
                }

                // add to the list
                indexes[numIndexes + 0] = i1
                indexes[numIndexes + 1] = i2
                indexes[numIndexes + 2] = i3
                numIndexes += 3
                i += 3
                faceNum++
            }

            // get bounds for the surface
            Simd.SIMDProcessor.MinMax(bounds.oGet(0), bounds.oGet(1), tri.verts, indexes, numIndexes)

            // decrease the size of the memory block to the size of the number of used indexes
            tr_trisurf.R_ResizeStaticTriSurfIndexes(newTri, numIndexes)
        }
        if (0 == numIndexes) {
            tr_trisurf.R_ReallyFreeStaticTriSurf(newTri)
            return null
        }
        newTri.numIndexes = numIndexes
        newTri.bounds.oSet(bounds)
        return newTri
    }

    /*
     ======================
     R_PotentiallyInsideInfiniteShadow

     If we know that we are "off to the side" of an infinite shadow volume,
     we can draw it without caps in zpass mode
     ======================
     */
    fun R_PotentiallyInsideInfiniteShadow(occluder: srfTriangles_s?, localView: idVec3?, localLight: idVec3?): Boolean {
        val exp = idBounds()

        // expand the bounds to account for the near clip plane, because the
        // view could be mathematically outside, but if the near clip plane
        // chops a volume edge, the zpass rendering would fail.
        var znear = RenderSystem_init.r_znear.GetFloat()
        if (tr_local.tr.viewDef.renderView.cramZNear) {
            znear *= 0.25f
        }
        val stretch = znear * 2 // in theory, should vary with FOV
        exp.oSet(0, 0, occluder.bounds.oGet(0, 0) - stretch)
        exp.oSet(0, 1, occluder.bounds.oGet(0, 1) - stretch)
        exp.oSet(0, 2, occluder.bounds.oGet(0, 2) - stretch)
        exp.oSet(1, 0, occluder.bounds.oGet(1, 0) + stretch)
        exp.oSet(1, 1, occluder.bounds.oGet(1, 1) + stretch)
        exp.oSet(1, 2, occluder.bounds.oGet(1, 2) + stretch)
        if (exp.ContainsPoint(localView)) {
            return true
        }
        if (exp.ContainsPoint(localLight)) {
            return true
        }

        // if the ray from localLight to localView intersects a face of the
        // expanded bounds, we will be inside the projection
        val ray = idVec3(localView.oMinus(localLight))

        // intersect the ray from the view to the light with the near side of the bounds
        for (axis in 0..2) {
            var d: Float
            var frac: Float
            val hit = idVec3()
            val eza = exp.oGet(0, axis)
            val ezo = exp.oGet(1, axis) //eoa
            val l_axis = localLight.oGet(axis)
            if (l_axis < eza) {
                if (localView.oGet(axis) < eza) {
                    continue
                }
                d = eza - l_axis
                frac = d / ray.oGet(axis)
                hit.oSet(localLight.oPlus(ray.oMultiply(frac)))
                hit.oSet(axis, eza)
            } else if (l_axis > ezo) {
                if (localView.oGet(axis) > ezo) {
                    continue
                }
                d = ezo - l_axis
                frac = d / ray.oGet(axis)
                hit.oSet(localLight.oPlus(ray.oMultiply(frac)))
                hit.oSet(axis, ezo)
            } else {
                continue
            }
            if (exp.ContainsPoint(hit)) {
                return true
            }
        }

        // the view is definitely not inside the projected shadow
        return false
    }

    class srfCullInfo_t {
        //
        // Clip planes in surface space used to calculate the cull bits.
        val localClipPlanes: Array<idPlane?>? = idPlane.Companion.generateArray(6)

        //
        // For each vertex a byte with the bits [0-5] set if the
        // vertex is at the back side of the corresponding clip plane.
        // If the 'cullBits' pointer equals LIGHT_CULL_ALL_FRONT all
        // vertices are at the front of all the clip planes.
        var cullBits: ByteArray?

        // For each triangle a byte set to 1 if facing the light origin.
        var facing: ByteArray?
    }

    internal class surfaceInteraction_t {
        //
        // so we can check ambientViewCount before adding lightTris, and get
        // at the shared vertex and possibly shadowVertex caches
        var ambientTris: srfTriangles_s? = null

        //
        var cullInfo: srfCullInfo_t?
        var expCulled // only for the experimental shadow buffer renderer
                = 0

        // if lightTris == LIGHT_TRIS_DEFERRED, then the calculation of the
        // lightTris has been deferred, and must be done if ambientTris is visible
        var lightTris: srfTriangles_s? = null

        //
        var shader: idMaterial? = null

        //
        // shadow volume triangle surface
        var shadowTris: srfTriangles_s? = null

        companion object {
            fun generateArray(length: Int): Array<surfaceInteraction_t?>? {
                return Stream.generate { surfaceInteraction_t() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }

        //
        //
        init {
            cullInfo = srfCullInfo_t()
        }
    }

    internal class areaNumRef_s {
        var areaNum = 0
        var next: areaNumRef_s? = null
    }

    /*
     ===========================================================================

     idInteraction implementation

     ===========================================================================
     */
    class idInteraction {
        private val DBG_count = DBG_counter++
        private val frustum // frustum which contains the interaction
                : idFrustum?

        //
        // get space from here, if NULL, it is a pre-generated shadow volume from dmap
        var entityDef: idRenderEntityLocal? = null
        var entityNext // for entityDef chains
                : idInteraction? = null
        var entityPrev: idInteraction? = null
        var lightDef: idRenderLightLocal? = null

        //
        //
        var lightNext // for lightDef chains
                : idInteraction? = null
        var lightPrev: idInteraction? = null

        // this may be 0 if the light and entity do not actually intersect
        // -1 = an untested interaction
        var numSurfaces = 0

        //
        // if there is a whole-entity optimized shadow hull, it will
        // be present as a surfaceInteraction_t with a NULL ambientTris, but
        // possibly having a shader to specify the shadow sorting order
        var surfaces: Array<surfaceInteraction_t?>? = null

        //
        private var dynamicModelFrameCount // so we can tell if a callback model animated
                = 0
        private var frustumAreas // numbers of the areas the frustum touches
                : areaNumRef_s?
        private var frustumState: frustumStates?

        /*
         ===============
         idInteraction::UnlinkAndFree

         Removes links and puts it back on the free list.
         ===============
         */
        // unlinks from the entity and light, frees all surfaceInteractions,
        // and puts it back on the free list
        fun UnlinkAndFree() {

            // clear the table pointer
            val renderWorld = lightDef.world
            if (renderWorld.interactionTable != null) {
                val index = lightDef.index * renderWorld.interactionTableWidth + entityDef.index
                if (renderWorld.interactionTable[index] !== this) {
                    Common.common.Error("idInteraction::UnlinkAndFree: interactionTable wasn't set")
                }
                renderWorld.interactionTable[index] = null
            }
            Unlink()
            FreeSurfaces()

            // free the interaction area references
            var area: areaNumRef_s?
            var nextArea: areaNumRef_s?
            area = frustumAreas
            while (area != null) {
                nextArea = area.next
                area = nextArea
            }

            // put it back on the free list
//            renderWorld.interactionAllocator.Free(this);
        }

        /*
         ===============
         idInteraction::FreeSurfaces

         Frees the surfaces, but leaves the interaction linked in, so it
         will be regenerated automatically
         ===============
         */
        // free the interaction surfaces
        fun FreeSurfaces() {
            if (surfaces != null) {
                for (i in 0 until numSurfaces) {
                    val sint = surfaces.get(i)
                    if (sint.lightTris != null) {
                        if (sint.lightTris !== Interaction.LIGHT_TRIS_DEFERRED) {
                            tr_trisurf.R_FreeStaticTriSurf(sint.lightTris)
                        }
                        sint.lightTris = null
                    }
                    if (sint.shadowTris != null) {
                        // if it doesn't have an entityDef, it is part of a prelight
                        // model, not a generated interaction
                        if (entityDef != null) {
                            tr_trisurf.R_FreeStaticTriSurf(sint.shadowTris)
                            sint.shadowTris = null
                        }
                    }
                    Interaction.R_FreeInteractionCullInfo(sint.cullInfo)
                }

//                R_StaticFree(this.surfaces);
                surfaces = null
            }
            numSurfaces = -1
        }

        /*
         ===============
         idInteraction::MakeEmpty

         Makes the interaction empty and links it at the end of the entity's and light's interaction lists.
         ===============
         */
        // makes the interaction empty for when the light and entity do not actually intersect
        // all empty interactions are linked at the end of the light's and entity's interaction list
        fun MakeEmpty() {

            // an empty interaction has no surfaces
            numSurfaces = 0
            Unlink()

            // relink at the end of the entity's list
            entityNext = null
            entityPrev = entityDef.lastInteraction
            entityDef.lastInteraction = this
            if (entityPrev != null) {
                entityPrev.entityNext = this
            } else {
                entityDef.firstInteraction = this
            }

            // relink at the end of the light's list
            lightNext = null
            lightPrev = lightDef.lastInteraction
            lightDef.lastInteraction = this
            if (lightPrev != null) {
                lightPrev.lightNext = this
            } else {
                lightDef.firstInteraction = this
            }
        }

        // returns true if the interaction is empty
        fun IsEmpty(): Boolean {
            return numSurfaces == 0
        }

        // returns true if the interaction is not yet completely created
        fun IsDeferred(): Boolean {
            return numSurfaces == -1
        }

        // returns true if the interaction has shadows
        fun HasShadows(): Boolean {
            return !lightDef.parms.noShadows && !entityDef.parms.noShadow && lightDef.lightShader.LightCastsShadows()
        }

        /*
         ===============
         idInteraction::MemoryUsed

         Counts up the memory used by all the surfaceInteractions, which
         will be used to determine when we need to start purging old interactions.
         ===============
         */
        // counts up the memory used by all the surfaceInteractions, which
        // will be used to determine when we need to start purging old interactions
        fun MemoryUsed(): Int {
            var total = 0
            for (i in 0 until numSurfaces) {
                val inter = surfaces.get(i)
                total += tr_trisurf.R_TriSurfMemory(inter.lightTris)
                total += tr_trisurf.R_TriSurfMemory(inter.shadowTris)
            }
            return total
        }

        /*
         ==================
         idInteraction::AddActiveInteraction

         Create and add any necessary light and shadow triangles

         If the model doesn't have any surfaces that need interactions
         with this type of light, it can be skipped, but we might need to
         instantiate the dynamic model to find out
         ==================
         */
        // makes sure all necessary light surfaces and shadow surfaces are created, and
        // calls R_LinkLightSurf() for each one
        fun AddActiveInteraction() {
            val vLight: viewLight_s?
            val vEntity: viewEntity_s?
            val shadowScissor: idScreenRect
            val lightScissor: idScreenRect
            val localLightOrigin = idVec3()
            val localViewOrigin = idVec3()
            vLight = lightDef.viewLight
            vEntity = entityDef.viewEntity

            // do not waste time culling the interaction frustum if there will be no shadows
            shadowScissor = if (!HasShadows()) {

                // use the entity scissor rectangle
                idScreenRect(vEntity.scissorRect)

                // culling does not seem to be worth it for static world models
            } else if (entityDef.parms.hModel.IsStaticWorldModel()) {

                // use the light scissor rectangle
                idScreenRect(vLight.scissorRect)
            } else {

                // try to cull the interaction
                // this will also cull the case where the light origin is inside the
                // view frustum and the entity bounds are outside the view frustum
                if (CullInteractionByViewFrustum(tr_local.tr.viewDef.viewFrustum)) {
                    return
                }

                // calculate the shadow scissor rectangle
                idScreenRect(CalcInteractionScissorRectangle(tr_local.tr.viewDef.viewFrustum))
            }

            // get out before making the dynamic model if the shadow scissor rectangle is empty
            if (shadowScissor.IsEmpty()) {
                return
            }

            // We will need the dynamic surface created to make interactions, even if the
            // model itself wasn't visible.  This just returns a cached value after it
            // has been generated once in the view.
            val model = tr_light.R_EntityDefDynamicModel(entityDef)
            if (model == null || model.NumSurfaces() <= 0) {
                return
            }

            // the dynamic model may have changed since we built the surface list
            if (!IsDeferred() && entityDef.dynamicModelFrameCount != dynamicModelFrameCount) {
                FreeSurfaces()
            }
            dynamicModelFrameCount = entityDef.dynamicModelFrameCount

            // actually create the interaction if needed, building light and shadow surfaces as needed
            if (IsDeferred()) {
                CreateInteraction(model)
            }
            tr_main.R_GlobalPointToLocal(vEntity.modelMatrix, lightDef.globalLightOrigin, localLightOrigin)
            tr_main.R_GlobalPointToLocal(vEntity.modelMatrix, tr_local.tr.viewDef.renderView.vieworg, localViewOrigin)

            // calculate the scissor as the intersection of the light and model rects
            // this is used for light triangles, but not for shadow triangles
            lightScissor = idScreenRect(vLight.scissorRect)
            lightScissor.Intersect(vEntity.scissorRect)
            val lightScissorsEmpty = lightScissor.IsEmpty()

            // for each surface of this entity / light interaction
            for (i in 0 until numSurfaces) {
                val sint = surfaces.get(i)

                // see if the base surface is visible, we may still need to add shadows even if empty
                if (!lightScissorsEmpty && sint.ambientTris != null && sint.ambientTris.ambientViewCount == tr_local.tr.viewCount) {

                    // make sure we have created this interaction, which may have been deferred
                    // on a previous use that only needed the shadow
                    if (sint.lightTris === Interaction.LIGHT_TRIS_DEFERRED) {
                        sint.lightTris = Interaction.R_CreateLightTris(
                            vEntity.entityDef,
                            sint.ambientTris,
                            vLight.lightDef,
                            sint.shader,
                            sint.cullInfo
                        )
                        Interaction.R_FreeInteractionCullInfo(sint.cullInfo)
                    }
                    val lightTris = sint.lightTris
                    if (lightTris != null) {

                        // try to cull before adding
                        // FIXME: this may not be worthwhile. We have already done culling on the ambient,
                        // but individual surfaces may still be cropped somewhat more
                        if (!tr_main.R_CullLocalBox(
                                lightTris.bounds,
                                vEntity.modelMatrix,
                                5,
                                tr_local.tr.viewDef.frustum
                            )
                        ) {

                            // make sure the original surface has its ambient cache created
                            val tri = sint.ambientTris
                            if (TempDump.NOT(tri.ambientCache)) {
                                if (!tr_light.R_CreateAmbientCache(tri, sint.shader.ReceivesLighting())) {
                                    // skip if we were out of vertex memory
                                    continue
                                }
                            }

                            // reference the original surface's ambient cache
                            lightTris.ambientCache = tri.ambientCache

                            // touch the ambient surface so it won't get purged
                            VertexCache.vertexCache.Touch(lightTris.ambientCache)

                            // regenerate the lighting cache (for non-vertex program cards) if it has been purged
                            if (TempDump.NOT(lightTris.lightingCache)) {
                                if (!tr_light.R_CreateLightingCache(entityDef, lightDef, lightTris)) {
                                    // skip if we are out of vertex memory
                                    continue
                                }
                            }
                            // touch the light surface so it won't get purged
                            // (vertex program cards won't have a light cache at all)
                            if (lightTris.lightingCache != null) {
                                VertexCache.vertexCache.Touch(lightTris.lightingCache)
                            }
                            if (TempDump.NOT(lightTris.indexCache) && RenderSystem_init.r_useIndexBuffers.GetBool()) {
                                lightTris.indexCache = VertexCache.vertexCache.Alloc(
                                    lightTris.indexes,
                                    lightTris.numIndexes * Integer.BYTES,
                                    true
                                )
                            }
                            if (lightTris.indexCache != null) {
                                VertexCache.vertexCache.Touch(lightTris.indexCache)
                            }

                            // add the surface to the light list
                            val shader: Array<idMaterial?> = arrayOf<idMaterial?>(sint.shader)
                            RenderWorld.R_GlobalShaderOverride(shader)
                            sint.shader = shader[0]

                            // there will only be localSurfaces if the light casts shadows and
                            // there are surfaces with NOSELFSHADOW
                            if (sint.shader.Coverage() == materialCoverage_t.MC_TRANSLUCENT) {
                                tr_light.R_LinkLightSurf(
                                    vLight.translucentInteractions,
                                    lightTris,
                                    vEntity,
                                    lightDef,
                                    shader[0],
                                    lightScissor,
                                    false
                                )
                            } else if (!lightDef.parms.noShadows && sint.shader.TestMaterialFlag(Material.MF_NOSELFSHADOW)) {
                                tr_light.R_LinkLightSurf(
                                    vLight.localInteractions,
                                    lightTris,
                                    vEntity,
                                    lightDef,
                                    shader[0],
                                    lightScissor,
                                    false
                                )
                            } else {
                                tr_light.R_LinkLightSurf(
                                    vLight.globalInteractions,
                                    lightTris,
                                    vEntity,
                                    lightDef,
                                    shader[0],
                                    lightScissor,
                                    false
                                )
                            }
                        }
                    }
                }
                val shadowTris = sint.shadowTris

                // the shadows will always have to be added, unless we can tell they
                // are from a surface in an unconnected area
                if (shadowTris != null) {

                    // check for view specific shadow suppression (player shadows, etc)
                    if (!RenderSystem_init.r_skipSuppress.GetBool()) {
                        if (entityDef.parms.suppressShadowInViewID != 0
                            && entityDef.parms.suppressShadowInViewID == tr_local.tr.viewDef.renderView.viewID
                        ) {
                            continue
                        }
                        if (entityDef.parms.suppressShadowInLightID != 0
                            && entityDef.parms.suppressShadowInLightID == lightDef.parms.lightId
                        ) {
                            continue
                        }
                    }

                    // cull static shadows that have a non-empty bounds
                    // dynamic shadows that use the turboshadow code will not have valid
                    // bounds, because the perspective projection extends them to infinity
                    if (RenderSystem_init.r_useShadowCulling.GetBool() && !shadowTris.bounds.IsCleared()) {
                        if (tr_main.R_CullLocalBox(
                                shadowTris.bounds,
                                vEntity.modelMatrix,
                                5,
                                tr_local.tr.viewDef.frustum
                            )
                        ) {
                            continue
                        }
                    }

                    // copy the shadow vertexes to the vertex cache if they have been purged
                    // if we are using shared shadowVertexes and letting a vertex program fix them up,
                    // get the shadowCache from the parent ambient surface
                    if (TempDump.NOT(*shadowTris.shadowVertexes)) {
                        // the data may have been purged, so get the latest from the "home position"
                        shadowTris.shadowCache = sint.ambientTris.shadowCache
                    }

                    // if we have been purged, re-upload the shadowVertexes
                    if (TempDump.NOT(shadowTris.shadowCache)) {
                        if (shadowTris.shadowVertexes != null) {
                            // each interaction has unique vertexes
                            tr_light.R_CreatePrivateShadowCache(shadowTris)
                        } else {
                            tr_light.R_CreateVertexProgramShadowCache(sint.ambientTris)
                            shadowTris.shadowCache = sint.ambientTris.shadowCache
                        }
                        // if we are out of vertex cache space, skip the interaction
                        if (TempDump.NOT(shadowTris.shadowCache)) {
                            continue
                        }
                    }

                    // touch the shadow surface so it won't get purged
                    VertexCache.vertexCache.Touch(shadowTris.shadowCache)
                    if (TempDump.NOT(shadowTris.indexCache) && RenderSystem_init.r_useIndexBuffers.GetBool()) {
                        shadowTris.indexCache = VertexCache.vertexCache.Alloc(
                            shadowTris.indexes,
                            shadowTris.numIndexes * Integer.BYTES,
                            true
                        )
                        VertexCache.vertexCache.Touch(shadowTris.indexCache)
                    }

                    // see if we can avoid using the shadow volume caps
                    val inside = Interaction.R_PotentiallyInsideInfiniteShadow(
                        sint.ambientTris,
                        localViewOrigin,
                        localLightOrigin
                    )
                    if (sint.shader.TestMaterialFlag(Material.MF_NOSELFSHADOW)) {
                        tr_light.R_LinkLightSurf(
                            vLight.localShadows,
                            shadowTris,
                            vEntity,
                            lightDef,
                            null,
                            shadowScissor,
                            inside
                        )
                    } else {
                        tr_light.R_LinkLightSurf(
                            vLight.globalShadows,
                            shadowTris,
                            vEntity,
                            lightDef,
                            null,
                            shadowScissor,
                            inside
                        )
                    }
                }
            }
        }

        /*
         ====================
         idInteraction::CreateInteraction

         Called when a entityDef and a lightDef are both present in a
         portalArea, and might be visible.  Performs cull checking before doing the expensive
         computations.

         References tr.viewCount so lighting surfaces will only be created if the ambient surface is visible,
         otherwise it will be marked as deferred.

         The results of this are cached and valid until the light or entity change.
         ====================
         */
        // actually create the interaction
        private fun CreateInteraction(model: idRenderModel?) {
            val lightShader: idMaterial? = lightDef.lightShader
            var shader: idMaterial?
            var interactionGenerated: Boolean
            val bounds: idBounds?
            tr_local.tr.pc.c_createInteractions++
            bounds = model.Bounds(entityDef.parms)

            // if it doesn't contact the light frustum, none of the surfaces will
            if (tr_main.R_CullLocalBox(bounds, entityDef.modelMatrix, 6, lightDef.frustum)) {
                MakeEmpty()
                return
            }

            // use the turbo shadow path
            var shadowGen = shadowGen_t.SG_DYNAMIC

            // really large models, like outside terrain meshes, should use
            // the more exactly culled static shadow path instead of the turbo shadow path.
            // FIXME: this is a HACK, we should probably have a material flag.
            if (bounds.oGet(1).oGet(0) - bounds.oGet(0).oGet(0) > 3000) {
                shadowGen = shadowGen_t.SG_STATIC
            }

            //
            // create slots for each of the model's surfaces
            //
            numSurfaces = model.NumSurfaces()
            surfaces = surfaceInteraction_t.generateArray(numSurfaces)
            interactionGenerated = false

            // check each surface in the model
            for (c in 0 until model.NumSurfaces()) {
                val surf: modelSurface_s?
                var tri: srfTriangles_s?
                surf = model.Surface(c)
                tri = surf.geometry
                if (TempDump.NOT(tri)) {
                    continue
                }

                // determine the shader for this surface, possibly by skinning
                shader = surf.shader
                shader =
                    RenderWorld.R_RemapShaderBySkin(shader, entityDef.parms.customSkin, entityDef.parms.customShader)
                if (null == shader) {
                    continue
                }

                // try to cull each surface
                if (tr_main.R_CullLocalBox(tri.bounds, entityDef.modelMatrix, 6, lightDef.frustum)) {
                    continue
                }
                val sint = surfaces.get(c)
                sint.shader = shader

                // save the ambient tri pointer so we can reject lightTri interactions
                // when the ambient surface isn't in view, and we can get shared vertex
                // and shadow data from the source surface
                sint.ambientTris = tri

                // "invisible ink" lights and shaders
                if (shader.Spectrum() != lightShader.Spectrum()) {
                    continue
                }

                // generate a lighted surface and add it
                if (shader.ReceivesLighting()) {
                    if (tri.ambientViewCount == tr_local.tr.viewCount) {
                        sint.lightTris = Interaction.R_CreateLightTris(entityDef, tri, lightDef, shader, sint.cullInfo)
                    } else {
                        // this will be calculated when sint.ambientTris is actually in view
                        sint.lightTris =
                            Interaction.LIGHT_TRIS_DEFERRED //HACKME::1:this throws a null pointer after the planet goes out of the screen, hitting you in the head!
                    }
                    interactionGenerated = true
                }

                // if the interaction has shadows and this surface casts a shadow
                if (HasShadows() && shader.SurfaceCastsShadow() && tri.silEdges != null) {

                    // if the light has an optimized shadow volume, don't create shadows for any models that are part of the base areas
                    if (lightDef.parms.prelightModel == null || !model.IsStaticWorldModel() || !RenderSystem_init.r_useOptimizedShadows.GetBool()) {

                        // this is the only place during gameplay (outside the utilities) that R_CreateShadowVolume() is called
                        sint.shadowTris =
                            tr_stencilshadow.R_CreateShadowVolume(entityDef, tri, lightDef, shadowGen, sint.cullInfo)
                        if (sint.shadowTris != null) {
                            if (shader.Coverage() != materialCoverage_t.MC_OPAQUE || !RenderSystem_init.r_skipSuppress.GetBool() && entityDef.parms.suppressSurfaceInViewID != 0) {
                                // if any surface is a shadow-casting perforated or translucent surface, or the
                                // base surface is suppressed in the view (world weapon shadows) we can't use
                                // the external shadow optimizations because we can see through some of the faces
                                sint.shadowTris.numShadowIndexesNoCaps = sint.shadowTris.numIndexes
                                sint.shadowTris.numShadowIndexesNoFrontCaps = sint.shadowTris.numIndexes
                            }
                        }
                        interactionGenerated = true
                    }
                }

                // free the cull information when it's no longer needed
                if (sint.lightTris !== Interaction.LIGHT_TRIS_DEFERRED) { //HACKME::2:related to HACKME1
                    Interaction.R_FreeInteractionCullInfo(sint.cullInfo)
                }
            }

            // if none of the surfaces generated anything, don't even bother checking?
            if (!interactionGenerated) {
                MakeEmpty()
            }
        }

        // unlink from entity and light lists
        private fun Unlink() {

            // unlink from the entity's list
            if (entityPrev != null) {
                entityPrev.entityNext = entityNext
            } else {
                entityDef.firstInteraction = entityNext
            }
            if (entityNext != null) {
                entityNext.entityPrev = entityPrev
            } else {
                entityDef.lastInteraction = entityPrev
            }
            entityPrev = null
            entityNext = entityPrev

            // unlink from the light's list
            if (lightPrev != null) {
                lightPrev.lightNext = lightNext
            } else {
                lightDef.firstInteraction = lightNext
            }
            if (lightNext != null) {
                lightNext.lightPrev = lightPrev
            } else {
                lightDef.lastInteraction = lightPrev
            }
            lightPrev = null
            lightNext = lightPrev
        }

        // try to determine if the entire interaction, including shadows, is guaranteed
        // to be outside the view frustum
        private fun CullInteractionByViewFrustum(viewFrustum: idFrustum?): Boolean {
            if (!RenderSystem_init.r_useInteractionCulling.GetBool()) {
                return false
            }
            if (frustumState == frustumStates.FRUSTUM_INVALID) {
                return false
            }
            if (frustumState == frustumStates.FRUSTUM_UNINITIALIZED) {
                frustum.FromProjection(
                    idBox(entityDef.referenceBounds, entityDef.parms.origin, entityDef.parms.axis),
                    lightDef.globalLightOrigin,
                    Lib.Companion.MAX_WORLD_SIZE.toFloat()
                )
                if (!frustum.IsValid()) {
                    frustumState = frustumStates.FRUSTUM_INVALID
                    return false
                }
                if (lightDef.parms.pointLight) {
                    frustum.ConstrainToBox(
                        idBox(
                            lightDef.parms.origin,
                            lightDef.parms.lightRadius,
                            lightDef.parms.axis
                        )
                    )
                } else {
                    frustum.ConstrainToBox(idBox(lightDef.frustumTris.bounds))
                }
                frustumState = frustumStates.FRUSTUM_VALID
            }
            if (!viewFrustum.IntersectsFrustum(frustum)) {
                return true
            }
            if (RenderSystem_init.r_showInteractionFrustums.GetInteger() != 0) {
                tr_local.tr.viewDef.renderWorld.DebugFrustum(
                    colors.get(lightDef.index and 7),
                    frustum,
                    RenderSystem_init.r_showInteractionFrustums.GetInteger() > 1
                )
                if (RenderSystem_init.r_showInteractionFrustums.GetInteger() > 2) {
                    tr_local.tr.viewDef.renderWorld.DebugBox(
                        Lib.Companion.colorWhite,
                        idBox(entityDef.referenceBounds, entityDef.parms.origin, entityDef.parms.axis)
                    )
                }
            }
            return false
        }

        // determine the minimum scissor rect that will include the interaction shadows
        // projected to the bounds of the light
        private fun CalcInteractionScissorRectangle(viewFrustum: idFrustum?): idScreenRect? {
            val projectionBounds = idBounds()
            var portalRect: idScreenRect? = idScreenRect()
            val scissorRect: idScreenRect?
            if (RenderSystem_init.r_useInteractionScissors.GetInteger() == 0) {
                return lightDef.viewLight.scissorRect
            }
            if (RenderSystem_init.r_useInteractionScissors.GetInteger() < 0) {
                // this is the code from Cass at nvidia, it is more precise, but slower
                return tr_shadowbounds.R_CalcIntersectionScissor(lightDef, entityDef, tr_local.tr.viewDef)
            }

            // the following is Mr.E's code
            // frustum must be initialized and valid
            if (frustumState == frustumStates.FRUSTUM_UNINITIALIZED || frustumState == frustumStates.FRUSTUM_INVALID) {
                return lightDef.viewLight.scissorRect
            }

            // calculate scissors for the portals through which the interaction is visible
            if (RenderSystem_init.r_useInteractionScissors.GetInteger() > 1) {
                var area: areaNumRef_s?
                if (frustumState == frustumStates.FRUSTUM_VALID) {
                    // retrieve all the areas the interaction frustum touches
                    var ref = entityDef.entityRefs
                    while (ref != null) {
                        area = areaNumRef_s() //entityDef.world.areaNumRefAllocator.Alloc();
                        area.areaNum = ref.area.areaNum
                        area.next = frustumAreas
                        frustumAreas = area
                        ref = ref.ownerNext
                    }
                    frustumAreas = tr_local.tr.viewDef.renderWorld.FloodFrustumAreas(frustum, frustumAreas)
                    frustumState = frustumStates.FRUSTUM_VALIDAREAS
                }
                portalRect.Clear()
                area = frustumAreas
                while (area != null) {
                    portalRect.Union(entityDef.world.GetAreaScreenRect(area.areaNum))
                    area = area.next
                }
                portalRect.Intersect(lightDef.viewLight.scissorRect)
            } else {
                portalRect = lightDef.viewLight.scissorRect
            }

            // early out if the interaction is not visible through any portals
            if (portalRect.IsEmpty()) {
                return portalRect
            }

            // calculate bounds of the interaction frustum projected into the view frustum
            if (lightDef.parms.pointLight) {
                viewFrustum.ClippedProjectionBounds(
                    frustum,
                    idBox(lightDef.parms.origin, lightDef.parms.lightRadius, lightDef.parms.axis),
                    projectionBounds
                )
            } else {
                viewFrustum.ClippedProjectionBounds(frustum, idBox(lightDef.frustumTris.bounds), projectionBounds)
            }
            if (projectionBounds.IsCleared()) {
                return portalRect
            }

            // derive a scissor rectangle from the projection bounds
            scissorRect = tr_main.R_ScreenRectFromViewFrustumBounds(projectionBounds)

            // intersect with the portal crossing scissor rectangle
            scissorRect.Intersect(portalRect)
            if (RenderSystem_init.r_showInteractionScissors.GetInteger() > 0) {
                tr_main.R_ShowColoredScreenRect(scissorRect, lightDef.index)
            }
            return scissorRect
        }

        internal enum class frustumStates {
            FRUSTUM_UNINITIALIZED, FRUSTUM_INVALID, FRUSTUM_VALID, FRUSTUM_VALIDAREAS
        }

        companion object {
            private val colors: Array<idVec4?>? = arrayOf(
                Lib.Companion.colorRed,
                Lib.Companion.colorGreen,
                Lib.Companion.colorBlue,
                Lib.Companion.colorYellow,
                Lib.Companion.colorMagenta,
                Lib.Companion.colorCyan,
                Lib.Companion.colorWhite,
                Lib.Companion.colorPurple
            )

            //
            //
            private var DBG_counter = 0

            // because these are generated and freed each game tic for active elements all
            // over the world, we use a custom pool allocater to avoid memory allocation overhead
            // and fragmentation
            fun AllocAndLink(eDef: idRenderEntityLocal?, lDef: idRenderLightLocal?): idInteraction? {
                if (TempDump.NOT(eDef) || TempDump.NOT(lDef)) {
                    Common.common.Error("idInteraction::AllocAndLink: null parm")
                }
                val renderWorld = eDef.world
                val interaction = idInteraction() //renderWorld.interactionAllocator.Alloc();

                // link and initialize
                interaction.dynamicModelFrameCount = 0
                interaction.lightDef = lDef
                interaction.entityDef = eDef
                interaction.numSurfaces = -1 // not checked yet
                interaction.surfaces = null
                interaction.frustumState = frustumStates.FRUSTUM_UNINITIALIZED
                interaction.frustumAreas = null

                // link at the start of the entity's list
                interaction.lightNext = lDef.firstInteraction
                interaction.lightPrev = null
                lDef.firstInteraction = interaction
                if (interaction.lightNext != null) {
                    interaction.lightNext.lightPrev = interaction
                } else {
                    lDef.lastInteraction = interaction
                }

                // link at the start of the light's list
                interaction.entityNext = eDef.firstInteraction
                interaction.entityPrev = null
                eDef.firstInteraction = interaction
                if (interaction.entityNext != null) {
                    interaction.entityNext.entityPrev = interaction
                } else {
                    eDef.lastInteraction = interaction
                }

                // update the interaction table
                if (renderWorld.interactionTable != null) {
                    val index = lDef.index * renderWorld.interactionTableWidth + eDef.index
                    if (renderWorld.interactionTable[index] != null) {
                        Common.common.Error("idInteraction::AllocAndLink: non null table entry")
                    }
                    renderWorld.interactionTable[index] = interaction
                }
                return interaction
            }
        }

        init {
            frustumState = frustumStates.FRUSTUM_UNINITIALIZED
            frustum = idFrustum()
            frustumAreas = null
        }
    }

    internal class clipTri_t {
        var numVerts = 0
        val verts: Array<idVec3?>? = idVec3.Companion.generateArray(Interaction.MAX_CLIPPED_POINTS)
    }

    /*
     ===================
     R_ShowInteractionMemory_f
     ===================
     */
    internal class R_ShowInteractionMemory_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var total = 0
            var entities = 0
            var interactions = 0
            var deferredInteractions = 0
            var emptyInteractions = 0
            var lightTris = 0
            var lightTriVerts = 0
            var lightTriIndexes = 0
            var shadowTris = 0
            var shadowTriVerts = 0
            var shadowTriIndexes = 0
            for (i in 0 until tr_local.tr.primaryWorld.entityDefs.Num()) {
                val def = tr_local.tr.primaryWorld.entityDefs.oGet(i)
                if (TempDump.NOT(def)) {
                    continue
                }
                if (def.firstInteraction == null) {
                    continue
                }
                entities++
                var inter: idInteraction? = def.firstInteraction
                while (inter != null) {
                    interactions++
                    total += inter.MemoryUsed()
                    if (inter.IsDeferred()) {
                        deferredInteractions++
                        inter = inter.entityNext
                        continue
                    }
                    if (inter.IsEmpty()) {
                        emptyInteractions++
                        inter = inter.entityNext
                        continue
                    }
                    for (j in 0 until inter.numSurfaces) {
                        val srf = inter.surfaces.get(j)
                        if (srf.lightTris != null && srf.lightTris !== Interaction.LIGHT_TRIS_DEFERRED) {
                            lightTris++
                            lightTriVerts += srf.lightTris.numVerts
                            lightTriIndexes += srf.lightTris.numIndexes
                        }
                        if (srf.shadowTris != null) {
                            shadowTris++
                            shadowTriVerts += srf.shadowTris.numVerts
                            shadowTriIndexes += srf.shadowTris.numIndexes
                        }
                    }
                    inter = inter.entityNext
                }
            }
            Common.common.Printf(
                "%d entities with %d total interactions totalling %dk\n",
                entities,
                interactions,
                total / 1024
            )
            Common.common.Printf(
                "%d deferred interactions, %d empty interactions\n",
                deferredInteractions,
                emptyInteractions
            )
            Common.common.Printf("%5d indexes %5d verts in %5d light tris\n", lightTriIndexes, lightTriVerts, lightTris)
            Common.common.Printf(
                "%5d indexes %5d verts in %5d shadow tris\n",
                shadowTriIndexes,
                shadowTriVerts,
                shadowTris
            )
        }

        companion object {
            private val instance: cmdFunction_t? = R_ShowInteractionMemory_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    init {
        Interaction.LIGHT_TRIS_DEFERRED = srfTriangles_s()
        val s = Interaction.LIGHT_TRIS_DEFERRED
        neo.Renderer.s.shadowCapPlaneBits = -1638
        neo.Renderer.s.numSilEdges = neo.Renderer.s.shadowCapPlaneBits
        neo.Renderer.s.numShadowIndexesNoFrontCaps = neo.Renderer.s.numSilEdges
        neo.Renderer.s.numShadowIndexesNoCaps = neo.Renderer.s.numShadowIndexesNoFrontCaps
        neo.Renderer.s.numIndexes = neo.Renderer.s.numShadowIndexesNoCaps
        neo.Renderer.s.numMirroredVerts = neo.Renderer.s.numIndexes
        neo.Renderer.s.numVerts = neo.Renderer.s.numMirroredVerts
        neo.Renderer.s.numDupVerts = neo.Renderer.s.numVerts
        neo.Renderer.s.ambientViewCount = neo.Renderer.s.numDupVerts
    }
}