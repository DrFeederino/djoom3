package neo.Renderer

import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Renderer.tr_local.optimizedShadow_t
import neo.Tools.Compilers.DMap.shadowopt3
import neo.framework.Common
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object tr_stencilshadow {
    //#define	LIGHT_CLIP_EPSILON	0.001f
    const val LIGHT_CLIP_EPSILON = 0.1f

    // tr_stencilShadow.c -- creator of stencil shadow volumes
    const val MAX_CLIPPED_POINTS = 20

    //
    const val MAX_CLIP_SIL_EDGES = 2048

    //
    const val MAX_SHADOW_INDEXES = 0x18000
    const val MAX_SHADOW_VERTS = 0x18000
    val clipSilEdges: Array<IntArray?>? = Array(tr_stencilshadow.MAX_CLIP_SIL_EDGES) { IntArray(2) }

    //
    val pointLightFrustums /*[6][6]*/: Array<Array<idPlane>?>? = arrayOf(
        arrayOf(
            idPlane(1, 0, 0, 0),
            idPlane(1, 1, 0, 0),
            idPlane(1, -1, 0, 0),
            idPlane(1, 0, 1, 0),
            idPlane(1, 0, -1, 0),
            idPlane(-1, 0, 0, 0)
        ), arrayOf(
            idPlane(-1, 0, 0, 0),
            idPlane(-1, 1, 0, 0),
            idPlane(-1, -1, 0, 0),
            idPlane(-1, 0, 1, 0),
            idPlane(-1, 0, -1, 0),
            idPlane(1, 0, 0, 0)
        ), arrayOf(
            idPlane(0, 1, 0, 0),
            idPlane(0, 1, 1, 0),
            idPlane(0, 1, -1, 0),
            idPlane(1, 1, 0, 0),
            idPlane(-1, 1, 0, 0),
            idPlane(0, -1, 0, 0)
        ), arrayOf(
            idPlane(0, -1, 0, 0),
            idPlane(0, -1, 1, 0),
            idPlane(0, -1, -1, 0),
            idPlane(1, -1, 0, 0),
            idPlane(-1, -1, 0, 0),
            idPlane(0, 1, 0, 0)
        ), arrayOf(
            idPlane(0, 0, 1, 0),
            idPlane(1, 0, 1, 0),
            idPlane(-1, 0, 1, 0),
            idPlane(0, 1, 1, 0),
            idPlane(0, -1, 1, 0),
            idPlane(0, 0, -1, 0)
        ), arrayOf(
            idPlane(0, 0, -1, 0),
            idPlane(1, 0, -1, 0),
            idPlane(-1, 0, -1, 0),
            idPlane(0, 1, -1, 0),
            idPlane(0, -1, -1, 0),
            idPlane(0, 0, 1, 0)
        )
    )
    val shadowVerts = Stream.generate { idVec4() }.limit(tr_stencilshadow.MAX_SHADOW_VERTS.toLong())
        .toArray<idVec4> { _Dummy_.__Array__() }

    /*
     ===================
     R_MakeShadowFrustums

     Called at definition derivation time
     ===================
     */
    private val faceCorners /*[6][4]*/: Array<IntArray?>? = arrayOf(
        intArrayOf(7, 5, 1, 3),
        intArrayOf(4, 6, 2, 0),
        intArrayOf(6, 7, 3, 2),
        intArrayOf(5, 4, 0, 1),
        intArrayOf(6, 4, 5, 7),
        intArrayOf(3, 1, 0, 2)
    )
    private val faceEdgeAdjacent /*[6][4]*/: Array<IntArray?>? = arrayOf(
        intArrayOf(4, 4, 2, 2),
        intArrayOf(7, 7, 1, 1),
        intArrayOf(5, 5, 0, 0),
        intArrayOf(6, 6, 3, 3),
        intArrayOf(0, 0, 3, 3),
        intArrayOf(5, 5, 6, 6)
    )

    //
    var c_caps = 0
    var c_sils = 0

    //
    var callOptimizer // call the preprocessor optimizer after clipping occluders
            = false

    //
    // faceCastsShadow will be 1 if the face is in the projection
    // and facing the apropriate direction
    var faceCastsShadow: ByteArray?

    //
    // facing will be 0 if forward facing, 1 if backwards facing
    // grabbed with alloca
    var globalFacing: ByteArray?
    var indexFrustumNumber // which shadow generating side of a light the indexRef is for
            = 0
    var indexRef = Stream.generate { indexRef_t() }.limit(6).toArray<indexRef_t?> { _Dummy_.__Array__() }
    var numClipSilEdges = 0
    var numShadowIndexes = 0
    var numShadowVerts = 0
    var overflowed = false

    //
    var remap: IntArray?
    var shadowIndexes: IntArray? = IntArray(tr_stencilshadow.MAX_SHADOW_INDEXES)

    /*

     Should we split shadow volume surfaces when they exceed max verts
     or max indexes?

     a problem is that the number of vertexes needed for the
     shadow volume will be twice the number in the original,
     and possibly up to 8/3 when near plane clipped.

     The maximum index count is 7x when not clipped and all
     triangles are completely discrete.  Near plane clipping
     can increase this to 10x.

     The maximum expansions are always with discrete triangles.
     Meshes of triangles will result in less index expansion because
     there will be less silhouette edges, although it will always be
     greater than the source if a cap is present.

     can't just project onto a plane if some surface points are
     behind the light.

     The cases when a face is edge on to a light is robustly handled
     with closed volumes, because only a single one of it's neighbors
     will pass the edge test.  It may be an issue with non-closed models.

     It is crucial that the shadow volumes be completely enclosed.
     The triangles identified as shadow sources will be projected
     directly onto the light far plane.
     The sil edges must be handled carefully.
     A partially clipped explicit sil edge will still generate a sil
     edge.
     EVERY new edge generated by clipping the triangles to the view
     will generate a sil edge.

     If a triangle has no points inside the frustum, it is completely
     culled away.  If a sil edge is either in or on the frustum, it is
     added.
     If a triangle has no points outside the frustum, it does not
     need to be clipped.



     USING THE STENCIL BUFFER FOR SHADOWING

     basic triangle property

     view plane inside shadow volume problem

     quad triangulation issue

     issues with silhouette optimizations

     the shapes of shadow projections are poor for sphere or box culling

     the gouraud shading problem


     // epsilon culling rules:

     // the positive side of the frustum is inside
     d = tri.verts[i].xyz * frustum[j].Normal() + frustum[j][3];
     if ( d < LIGHT_CLIP_EPSILON ) {
     pointCull[i] |= ( 1 << j );
     }
     if ( d > -LIGHT_CLIP_EPSILON ) {
     pointCull[i] |= ( 1 << (6+j) );
     }

     If a low order bit is set, the point is on or outside the plane
     If a high order bit is set, the point is on or inside the plane
     If a low order bit is clear, the point is inside the plane (definately positive)
     If a high order bit is clear, the point is outside the plane (definately negative)


     */
    fun TRIANGLE_CULLED(p1: Int, p2: Int, p3: Int, pointCull: IntArray?): Int {
        return pointCull.get(p1) and pointCull.get(p2) and pointCull.get(p3) and 0x3f
    }

    //
    //#define TRIANGLE_CLIPPED(p1,p2,p3) ( ( pointCull[p1] | pointCull[p2] | pointCull[p3] ) & 0xfc0 )
    fun TRIANGLE_CLIPPED(p1: Int, p2: Int, p3: Int, pointCull: IntArray?): Boolean {
        return pointCull.get(p1) and pointCull.get(p2) and pointCull.get(p3) and 0xfc0 != 0xfc0
    }

    // an edge that is on the plane is NOT culled
    fun EDGE_CULLED(p1: Int, p2: Int, pointCull: IntArray?): Int {
        return pointCull.get(p1) xor 0xfc0 and (pointCull.get(p2) xor 0xfc0) and 0xfc0
    }

    fun EDGE_CLIPPED(p1: Int, p2: Int, pointCull: IntArray?): Boolean {
        return pointCull.get(p1) and pointCull.get(p2) and 0xfc0 != 0xfc0
    }

    //
    /*
     ===============
     PointsOrdered

     To make sure the triangulations of the sil edges is consistant,
     we need to be able to order two points.  We don't care about how
     they compare with any other points, just that when the same two
     points are passed in (in either order), they will always specify
     the same one as leading.

     Currently we need to have separate faces in different surfaces
     order the same way, so we must look at the actual coordinates.
     If surfaces are ever guaranteed to not have to edge match with
     other surfaces, we could just compare indexes.
     ===============
     */
    // a point that is on the plane is NOT culled
    //#define	POINT_CULLED(p1) ( ( pointCull[p1] ^ 0xfc0 ) & 0xfc0 )
    fun POINT_CULLED(p1: Int, pointCull: IntArray?): Boolean {
        return pointCull.get(p1) and 0xfc0 != 0xfc0
    }

    fun PointsOrdered(a: idVec3, b: idVec3): Boolean {
        val i: Float
        val j: Float

        // vectors that wind up getting an equal hash value will
        // potentially cause a misorder, which can show as a couple
        // crack pixels in a shadow
        // scale by some odd numbers so -8, 8, 8 will not be equal
        // to 8, -8, 8
        // in the very rare case that these might be equal, all that would
        // happen is an oportunity for a tiny rasterization shadow crack
        i = a.get(0) + a.get(1) * 127 + a.get(2) * 1023
        j = b.get(0) + b.get(1) * 127 + b.get(2) * 1023
        return i < j
    }

    /*
     ====================
     R_LightProjectionMatrix

     ====================
     */
    fun R_LightProjectionMatrix(origin: idVec3, rearPlane: idPlane, mat: Array<idVec4> /*[4]*/) {
        val lv = idVec4()
        val lg: Float

        // calculate the homogenious light vector
        lv.x = origin.x
        lv.y = origin.y
        lv.z = origin.z
        lv.w = 1f
        lg = rearPlane.ToVec4().times(lv)

        // outer product
        mat.get(0).set(0, lg - rearPlane.get(0) * lv.get(0))
        mat.get(0).set(1, -rearPlane.get(1) * lv.get(0))
        mat.get(0).set(2, -rearPlane.get(2) * lv.get(0))
        mat.get(0).set(3, -rearPlane.get(3) * lv.get(0))
        mat.get(1).set(0, -rearPlane.get(0) * lv.get(1))
        mat.get(1).set(1, lg - rearPlane.get(1) * lv.get(1))
        mat.get(1).set(2, -rearPlane.get(2) * lv.get(1))
        mat.get(1).set(3, -rearPlane.get(3) * lv.get(1))
        mat.get(2).set(0, -rearPlane.get(0) * lv.get(2))
        mat.get(2).set(1, -rearPlane.get(1) * lv.get(2))
        mat.get(2).set(2, lg - rearPlane.get(2) * lv.get(2))
        mat.get(2).set(3, -rearPlane.get(3) * lv.get(2))
        mat.get(3).set(0, -rearPlane.get(0) * lv.get(3))
        mat.get(3).set(1, -rearPlane.get(1) * lv.get(3))
        mat.get(3).set(2, -rearPlane.get(2) * lv.get(3))
        mat.get(3).set(3, lg - rearPlane.get(3) * lv.get(3))
    }

    /*
     ===================
     R_ProjectPointsToFarPlane

     make a projected copy of the even verts into the odd spots
     that is on the far light clip plane
     ===================
     */
    fun R_ProjectPointsToFarPlane(
        ent: idRenderEntityLocal?, light: idRenderLightLocal?,
        lightPlaneLocal: idPlane,
        firstShadowVert: Int, numShadowVerts: Int
    ) {
        val lv = idVec3()
        val mat = Stream.generate { idVec4() }.limit(4).toArray<idVec4> { _Dummy_.__Array__() }
        var i: Int
        var `in`: Int
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, lv)
        tr_stencilshadow.R_LightProjectionMatrix(lv, lightPlaneLocal, mat)
        if (true) {
            // make a projected copy of the even verts into the odd spots
            `in` = firstShadowVert
            i = firstShadowVert
            while (i < numShadowVerts) {
                var w: Float
                var oow: Float
                tr_stencilshadow.shadowVerts[`in` + 0].w = 1f
                w = tr_stencilshadow.shadowVerts[`in`].ToVec3().times(mat[3].ToVec3()) + mat[3].get(3)
                if (w == 0f) {
                    tr_stencilshadow.shadowVerts[`in` + 1] = tr_stencilshadow.shadowVerts[`in` + 0]
                    i += 2
                    `in` += 2
                    continue
                }
                oow = 1.0f / w
                tr_stencilshadow.shadowVerts[`in` + 1].x =
                    (tr_stencilshadow.shadowVerts[`in`].ToVec3().times(mat[0].ToVec3()) + mat[0].get(3)) * oow
                tr_stencilshadow.shadowVerts[`in` + 1].y =
                    (tr_stencilshadow.shadowVerts[`in`].ToVec3().times(mat[1].ToVec3()) + mat[1].get(3)) * oow
                tr_stencilshadow.shadowVerts[`in` + 1].z =
                    (tr_stencilshadow.shadowVerts[`in`].ToVec3().times(mat[2].ToVec3()) + mat[2].get(3)) * oow
                tr_stencilshadow.shadowVerts[`in` + 1].w = 1f
                i += 2
                `in` += 2
            }

//}else{
//	// messing with W seems to cause some depth precision problems
//
//	// make a projected copy of the even verts into the odd spots
//	in = &shadowVerts[firstShadowVert];
//	for ( i = firstShadowVert ; i < numShadowVerts ; i+= 2, in += 2 ) {
//		in[0].w = 1;
//		in[1].x = *in * mat[0].ToVec3() + mat[0][3];
//		in[1].y = *in * mat[1].ToVec3() + mat[1][3];
//		in[1].z = *in * mat[2].ToVec3() + mat[2][3];
//		in[1].w = *in * mat[3].ToVec3() + mat[3][3];
//	}
        }
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
    fun R_ChopWinding(clipTris: Array<tr_stencilshadow.clipTri_t?>? /*[2]*/, inNum: Int, plane: idPlane): Int {
        val `in`: tr_stencilshadow.clipTri_t?
        val out: tr_stencilshadow.clipTri_t?
        val dists = FloatArray(tr_stencilshadow.MAX_CLIPPED_POINTS)
        val sides = IntArray(tr_stencilshadow.MAX_CLIPPED_POINTS)
        val counts = IntArray(3)
        var dot: Float
        var i: Int
        var j: Int
        val p1 = idVec3()
        val p2 = idVec3()
        val mid = idVec3()
        `in` = clipTris.get(inNum)
        out = clipTris.get(inNum xor 1)
        counts[2] = 0
        counts[1] = counts[2]
        counts[0] = counts[1]

        // determine sides for each point
        i = 0
        while (i < `in`.numVerts) {
            dot = plane.Distance(`in`.verts[i])
            dists[i] = dot
            if (dot < -tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                sides[i] = Plane.SIDE_BACK
            } else if (dot > tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                sides[i] = Plane.SIDE_FRONT
            } else {
                sides[i] = Plane.SIDE_ON
            }
            counts[sides[i]]++
            i++
        }

        // if none in front, it is completely clipped away
        if (0 == counts[Plane.SIDE_FRONT]) {
            `in`.numVerts = 0
            return inNum
        }
        if (0 == counts[Plane.SIDE_BACK]) {
            return inNum // inout stays the same
        }

        // avoid wrapping checks by duplicating first value to end
        sides[i] = sides[0]
        dists[i] = dists[0]
        `in`.verts[`in`.numVerts].set(`in`.verts[0])
        `in`.edgeFlags[`in`.numVerts] = `in`.edgeFlags[0]
        out.numVerts = 0
        i = 0
        while (i < `in`.numVerts) {
            p1.set(`in`.verts[i])
            if (sides[i] != Plane.SIDE_BACK) {
                out.verts[out.numVerts].set(p1)
                if (sides[i] == Plane.SIDE_ON && sides[i + 1] == Plane.SIDE_BACK) {
                    out.edgeFlags[out.numVerts] = 1
                } else {
                    out.edgeFlags[out.numVerts] = `in`.edgeFlags[i]
                }
                out.numVerts++
            }
            if (sides[i] == Plane.SIDE_FRONT && sides[i + 1] == Plane.SIDE_BACK
                || sides[i] == Plane.SIDE_BACK && sides[i + 1] == Plane.SIDE_FRONT
            ) {
                // generate a split point
                p2.set(`in`.verts[i + 1])
                dot = dists[i] / (dists[i] - dists[i + 1])
                j = 0
                while (j < 3) {
                    mid.set(j, p1.get(j) + dot * (p2.get(j) - p1.get(j)))
                    j++
                }
                out.verts[out.numVerts].set(mid)

                // set the edge flag
                if (sides[i + 1] != Plane.SIDE_FRONT) {
                    out.edgeFlags[out.numVerts] = 1
                } else {
                    out.edgeFlags[out.numVerts] = `in`.edgeFlags[i]
                }
                out.numVerts++
            }
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
        a: idVec3,
        b: idVec3,
        c: idVec3,
        planeBits: Int,
        frustum: Array<idPlane>? /*[6] */
    ): Boolean {
        var i: Int
        val base: Int
        val pingPong = arrayOf<tr_stencilshadow.clipTri_t?>(tr_stencilshadow.clipTri_t(), tr_stencilshadow.clipTri_t())
        val ct: tr_stencilshadow.clipTri_t?
        var p: Int
        pingPong[0].numVerts = 3
        pingPong[0].edgeFlags[0] = 0
        pingPong[0].edgeFlags[1] = 0
        pingPong[0].edgeFlags[2] = 0
        pingPong[0].verts[0] = a
        pingPong[0].verts[1] = b
        pingPong[0].verts[2] = c
        p = 0
        i = 0
        while (i < 6) {
            if (planeBits and (1 shl i) != 0) {
                p = tr_stencilshadow.R_ChopWinding(pingPong, p, frustum.get(i))
                if (pingPong[p].numVerts < 1) {
                    return false
                }
            }
            i++
        }
        ct = pingPong[p]

        // copy the clipped points out to shadowVerts
        if (tr_stencilshadow.numShadowVerts + ct.numVerts * 2 > tr_stencilshadow.MAX_SHADOW_VERTS) {
            tr_stencilshadow.overflowed = true
            return false
        }
        base = tr_stencilshadow.numShadowVerts
        i = 0
        while (i < ct.numVerts) {
            tr_stencilshadow.shadowVerts[base + i * 2].set(ct.verts[i])
            i++
        }
        tr_stencilshadow.numShadowVerts += ct.numVerts * 2
        if (tr_stencilshadow.numShadowIndexes + 3 * (ct.numVerts - 2) > tr_stencilshadow.MAX_SHADOW_INDEXES) {
            tr_stencilshadow.overflowed = true
            return false
        }
        i = 2
        while (i < ct.numVerts) {
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = base + i * 2
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = base + (i - 1) * 2
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = base
            i++
        }

        // any edges that were created by the clipping process will
        // have a silhouette quad created for it, because it is one
        // of the exterior bounds of the shadow volume
        i = 0
        while (i < ct.numVerts) {
            if (ct.edgeFlags[i] != 0) {
                if (tr_stencilshadow.numClipSilEdges == tr_stencilshadow.MAX_CLIP_SIL_EDGES) {
                    break
                }
                tr_stencilshadow.clipSilEdges[tr_stencilshadow.numClipSilEdges][0] = base + i * 2
                if (i == ct.numVerts - 1) {
                    tr_stencilshadow.clipSilEdges[tr_stencilshadow.numClipSilEdges][1] = base
                } else {
                    tr_stencilshadow.clipSilEdges[tr_stencilshadow.numClipSilEdges][1] = base + (i + 1) * 2
                }
                tr_stencilshadow.numClipSilEdges++
            }
            i++
        }
        return true
    }

    /*
     ===================
     R_ClipLineToLight

     If neither point is clearly behind the clipping
     plane, the edge will be passed unmodified.  A sil edge that
     is on a border plane must be drawn.

     If one point is clearly clipped by the plane and the
     other point is on the plane, it will be completely removed.
     ===================
     */
    fun R_ClipLineToLight(
        a: idVec3,
        b: idVec3,
        frustum: Array<idPlane>? /*[4]*/,
        p1: idVec3,
        p2: idVec3
    ): Boolean {
        var clip: FloatArray?
        var j: Int
        var d1: Float
        var d2: Float
        var f: Float
        p1.set(a)
        p2.set(b)

        // clip it
        j = 0
        while (j < 6) {
            d1 = frustum.get(j).Distance(p1)
            d2 = frustum.get(j).Distance(p2)

            // if both on or in front, not clipped to this plane
            if (d1 > -tr_stencilshadow.LIGHT_CLIP_EPSILON && d2 > -tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                j++
                continue
            }

            // if one is behind and the other isn't clearly in front, the edge is clipped off
            if (d1 <= -tr_stencilshadow.LIGHT_CLIP_EPSILON && d2 < tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                return false
            }
            if (d2 <= -tr_stencilshadow.LIGHT_CLIP_EPSILON && d1 < tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                return false
            }

            // clip it, keeping the negative side
            clip = if (d1 < 0) {
                p1.ToFloatPtr()
            } else {
                p2.ToFloatPtr()
            }

//if (false){
//		if ( Math.abs(d1 - d2) < 0.001 ) {
//			d2 = d1 - 0.1;
//		}
//}
            f = d1 / (d1 - d2)
            clip[0] = p1.get(0) + f * (p2.get(0) - p1.get(0))
            clip[1] = p1.get(1) + f * (p2.get(1) - p1.get(1))
            clip[2] = p1.get(2) + f * (p2.get(2) - p1.get(2))
            j++
        }
        return true // retain a fragment
    }

    /*
     ==================
     R_AddClipSilEdges

     Add sil edges for each triangle clipped to the side of
     the frustum.

     Only done for simple projected lights, not point lights.
     ==================
     */
    fun R_AddClipSilEdges() {
        var v1: Int
        var v2: Int
        var v1_back: Int
        var v2_back: Int
        var i: Int

        // don't allow it to overflow
        if (tr_stencilshadow.numShadowIndexes + tr_stencilshadow.numClipSilEdges * 6 > tr_stencilshadow.MAX_SHADOW_INDEXES) {
            tr_stencilshadow.overflowed = true
            return
        }
        i = 0
        while (i < tr_stencilshadow.numClipSilEdges) {
            v1 = tr_stencilshadow.clipSilEdges[i][0]
            v2 = tr_stencilshadow.clipSilEdges[i][1]
            v1_back = v1 + 1
            v2_back = v2 + 1
            if (tr_stencilshadow.PointsOrdered(
                    tr_stencilshadow.shadowVerts[v1].ToVec3(),
                    tr_stencilshadow.shadowVerts[v2].ToVec3()
                )
            ) {
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1_back
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2_back
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1_back
            } else {
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2_back
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2_back
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1_back
            }
            i++
        }
    }

    /*
     =================
     R_AddSilEdges

     Add quads from the front points to the projected points
     for each silhouette edge in the light
     =================
     */
    fun R_AddSilEdges(tri: srfTriangles_s?, pointCull: IntArray?, frustum: Array<idPlane>? /*[6]*/) {
        var v1: Int
        var v2: Int
        var i: Int
        var sil: silEdge_t?
        val numPlanes: Int
        numPlanes = tri.numIndexes / 3

        // add sil edges for any true silhouette boundaries on the surface
        i = 0
        while (i < tri.numSilEdges) {
            sil = tri.silEdges[i]
            if (sil.p1 < 0 || sil.p1 > numPlanes || sil.p2 < 0 || sil.p2 > numPlanes) {
                Common.common.Error("Bad sil planes")
            }

            // an edge will be a silhouette edge if the face on one side
            // casts a shadow, but the face on the other side doesn't.
            // "casts a shadow" means that it has some surface in the projection,
            // not just that it has the correct facing direction
            // This will cause edges that are exactly on the frustum plane
            // to be considered sil edges if the face inside casts a shadow.
            if (0 == tr_stencilshadow.faceCastsShadow[sil.p1] xor tr_stencilshadow.faceCastsShadow[sil.p2]) {
                i++
                continue
            }

            // if the edge is completely off the negative side of
            // a frustum plane, don't add it at all.  This can still
            // happen even if the face is visible and casting a shadow
            // if it is partially clipped
            if (tr_stencilshadow.EDGE_CULLED(sil.v1, sil.v2, pointCull) != 0) {
                i++
                continue
            }

            // see if the edge needs to be clipped
            if (tr_stencilshadow.EDGE_CLIPPED(sil.v1, sil.v2, pointCull)) {
                if (tr_stencilshadow.numShadowVerts + 4 > tr_stencilshadow.MAX_SHADOW_VERTS) {
                    tr_stencilshadow.overflowed = true
                    return
                }
                v1 = tr_stencilshadow.numShadowVerts
                v2 = v1 + 2
                if (!tr_stencilshadow.R_ClipLineToLight(
                        tri.verts[sil.v1].xyz, tri.verts[sil.v2].xyz,
                        frustum, tr_stencilshadow.shadowVerts[v1].ToVec3(), tr_stencilshadow.shadowVerts[v2].ToVec3()
                    )
                ) {
                    i++
                    continue  // clipped away
                }
                tr_stencilshadow.numShadowVerts += 4
            } else {
                // use the entire edge
                v1 = tr_stencilshadow.remap[sil.v1]
                v2 = tr_stencilshadow.remap[sil.v2]
                if (v1 < 0 || v2 < 0) {
                    Common.common.Error("R_AddSilEdges: bad remap[]")
                }
            }

            // don't overflow
            if (tr_stencilshadow.numShadowIndexes + 6 > tr_stencilshadow.MAX_SHADOW_INDEXES) {
                tr_stencilshadow.overflowed = true
                return
            }

            // we need to choose the correct way of triangulating the silhouette quad
            // consistantly between any two points, no matter which order they are specified.
            // If this wasn't done, slight rasterization cracks would show in the shadow
            // volume when two sil edges were exactly coincident
            if (tr_stencilshadow.faceCastsShadow[sil.p2] != 0) {
                if (tr_stencilshadow.PointsOrdered(
                        tr_stencilshadow.shadowVerts[v1].ToVec3(),
                        tr_stencilshadow.shadowVerts[v2].ToVec3()
                    )
                ) {
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                } else {
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                }
            } else {
                if (tr_stencilshadow.PointsOrdered(
                        tr_stencilshadow.shadowVerts[v1].ToVec3(),
                        tr_stencilshadow.shadowVerts[v2].ToVec3()
                    )
                ) {
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                } else {
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v2 + 1
                    tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = v1 + 1
                }
            }
            i++
        }
    }

    /*
     ================
     R_CalcPointCull

     Also inits the remap[] array to all -1
     ================
     */
    fun R_CalcPointCull(tri: srfTriangles_s?, frustum: Array<idPlane>? /*[6]*/, pointCull: IntArray?) {
        var i: Int
        var frontBits: Int
        val planeSide: FloatArray
        val side1: ByteArray
        val side2: ByteArray
        Simd.SIMDProcessor.Memset(
            tr_stencilshadow.remap,
            -1,
            tri.numVerts /* sizeof( remap[0] )*/
        ) //TODO:create template functions that call the standard functions, that way we just have to replace the template body.
        frontBits = 0
        i = 0
        while (i < 6) {

            // get front bits for the whole surface
            if (tri.bounds.PlaneDistance(frustum.get(i)) >= tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                frontBits = frontBits or (1 shl i + 6)
            }
            i++
        }

        // initialize point cull
        i = 0
        while (i < tri.numVerts) {
            pointCull.get(i) = frontBits
            i++
        }

        // if the surface is not completely inside the light frustum
        if (frontBits == (1 shl 6) - 1 shl 6) {
            return
        }
        planeSide = FloatArray(tri.numVerts)
        side1 = ByteArray(tri.numVerts) //SIMDProcessor.Memset( side1, 0, tri.numVerts * sizeof( byte ) );
        side2 = ByteArray(tri.numVerts) //SIMDProcessor.Memset( side2, 0, tri.numVerts * sizeof( byte ) );
        i = 0
        while (i < 6) {
            if (frontBits and (1 shl i + 6) != 0) {
                i++
                continue
            }
            Simd.SIMDProcessor.Dot(planeSide, frustum.get(i), tri.verts, tri.numVerts)
            Simd.SIMDProcessor.CmpLT(side1, i.toByte(), planeSide, tr_stencilshadow.LIGHT_CLIP_EPSILON, tri.numVerts)
            Simd.SIMDProcessor.CmpGT(side2, i.toByte(), planeSide, -tr_stencilshadow.LIGHT_CLIP_EPSILON, tri.numVerts)
            i++
        }
        i = 0
        while (i < tri.numVerts) {
            pointCull.get(i) = pointCull.get(i) or (side1[i] or (side2[i] shl 6))
            i++
        }
    }

    /*
     =================
     R_CreateShadowVolumeInFrustum

     Adds new verts and indexes to the shadow volume.

     If the frustum completely defines the projected light,
     makeClippedPlanes should be true, which will cause sil quads to
     be added along all clipped edges.

     If the frustum is just part of a point light, clipped planes don't
     need to be added.
     =================
     */
    fun R_CreateShadowVolumeInFrustum(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s?,
        light: idRenderLightLocal?,
        lightOrigin: idVec3,
        frustum: Array<idPlane>? /*[6]*/,
        farPlane: idPlane,
        makeClippedPlanes: Boolean
    ) {
        var i: Int
        val numTris: Int
        val pointCull: IntArray
        val numCapIndexes: Int
        val firstShadowIndex: Int
        val firstShadowVert: Int
        var cullBits: Int
        pointCull = IntArray(tri.numVerts)

        // test the vertexes for inside the light frustum, which will allow
        // us to completely cull away some triangles from consideration.
        tr_stencilshadow.R_CalcPointCull(tri, frustum, pointCull)

        // this may not be the first frustum added to the volume
        firstShadowIndex = tr_stencilshadow.numShadowIndexes
        firstShadowVert = tr_stencilshadow.numShadowVerts

        // decide which triangles front shadow volumes, clipping as needed
        tr_stencilshadow.numClipSilEdges = 0
        numTris = tri.numIndexes / 3
        i = 0
        while (i < numTris) {
            var i1: Int
            var i2: Int
            var i3: Int
            tr_stencilshadow.faceCastsShadow[i] = 0 // until shown otherwise

            // if it isn't facing the right way, don't add it
            // to the shadow volume
            if (tr_stencilshadow.globalFacing[i] != 0) {
                i++
                continue
            }
            i1 = tri.silIndexes[i * 3 + 0]
            i2 = tri.silIndexes[i * 3 + 1]
            i3 = tri.silIndexes[i * 3 + 2]

            // if all the verts are off one side of the frustum,
            // don't add any of them
            if (tr_stencilshadow.TRIANGLE_CULLED(i1, i2, i3, pointCull) != 0) {
                i++
                continue
            }

            // make sure the verts that are not on the negative sides
            // of the frustum are copied over.
            // we need to get the original verts even from clipped triangles
            // so the edges reference correctly, because an edge may be unclipped
            // even when a triangle is clipped.
            if (tr_stencilshadow.numShadowVerts + 6 > tr_stencilshadow.MAX_SHADOW_VERTS) {
                tr_stencilshadow.overflowed = true
                return
            }
            if (!tr_stencilshadow.POINT_CULLED(i1, pointCull) && tr_stencilshadow.remap[i1] == -1) {
                tr_stencilshadow.remap[i1] = tr_stencilshadow.numShadowVerts
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts].set(tri.verts[i1].xyz)
                tr_stencilshadow.numShadowVerts += 2
            }
            if (!tr_stencilshadow.POINT_CULLED(i2, pointCull) && tr_stencilshadow.remap[i2] == -1) {
                tr_stencilshadow.remap[i2] = tr_stencilshadow.numShadowVerts
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts].set(tri.verts[i2].xyz)
                tr_stencilshadow.numShadowVerts += 2
            }
            if (!tr_stencilshadow.POINT_CULLED(i3, pointCull) && tr_stencilshadow.remap[i3] == -1) {
                tr_stencilshadow.remap[i3] = tr_stencilshadow.numShadowVerts
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts].set(tri.verts[i3].xyz)
                tr_stencilshadow.numShadowVerts += 2
            }

            // clip the triangle if any points are on the negative sides
            if (tr_stencilshadow.TRIANGLE_CLIPPED(i1, i2, i3, pointCull)) {
                cullBits = pointCull[i1] xor 0xfc0 or (pointCull[i2] xor 0xfc0) or (pointCull[i3] xor 0xfc0) shr 6
                // this will also define clip edges that will become
                // silhouette planes
                if (tr_stencilshadow.R_ClipTriangleToLight(
                        tri.verts[i1].xyz, tri.verts[i2].xyz,
                        tri.verts[i3].xyz, cullBits, frustum
                    )
                ) {
                    tr_stencilshadow.faceCastsShadow[i] = 1
                }
            } else {
                // instead of overflowing or drawing a streamer shadow, don't draw a shadow at all
                if (tr_stencilshadow.numShadowIndexes + 3 > tr_stencilshadow.MAX_SHADOW_INDEXES) {
                    tr_stencilshadow.overflowed = true
                    return
                }
                if (tr_stencilshadow.remap[i1] == -1 || tr_stencilshadow.remap[i2] == -1 || tr_stencilshadow.remap[i3] == -1) {
                    Common.common.Error("R_CreateShadowVolumeInFrustum: bad remap[]")
                }
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = tr_stencilshadow.remap[i3]
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = tr_stencilshadow.remap[i2]
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes++] = tr_stencilshadow.remap[i1]
                tr_stencilshadow.faceCastsShadow[i] = 1
            }
            i++
        }

        // add indexes for the back caps, which will just be reversals of the
        // front caps using the back vertexes
        numCapIndexes = tr_stencilshadow.numShadowIndexes - firstShadowIndex

        // if no faces have been defined for the shadow volume,
        // there won't be anything at all
        if (numCapIndexes == 0) {
            return
        }

        //--------------- off-line processing ------------------
        // if we are running from dmap, perform the (very) expensive shadow optimizations
        // to remove internal sil edges and optimize the caps
        if (tr_stencilshadow.callOptimizer) {
            val opt: optimizedShadow_t?

            // project all of the vertexes to the shadow plane, generating
            // an equal number of back vertexes
//		R_ProjectPointsToFarPlane( ent, light, farPlane, firstShadowVert, numShadowVerts );
            opt = shadowopt3.SuperOptimizeOccluders(
                tr_stencilshadow.shadowVerts,
                Arrays.copyOf(tr_stencilshadow.shadowIndexes, firstShadowIndex),
                numCapIndexes,
                farPlane,
                lightOrigin
            )

            // pull off the non-optimized data
            tr_stencilshadow.numShadowIndexes = firstShadowIndex
            tr_stencilshadow.numShadowVerts = firstShadowVert

            // add the optimized data
            if (tr_stencilshadow.numShadowIndexes + opt.totalIndexes > tr_stencilshadow.MAX_SHADOW_INDEXES
                || tr_stencilshadow.numShadowVerts + opt.numVerts > tr_stencilshadow.MAX_SHADOW_VERTS
            ) {
                tr_stencilshadow.overflowed = true
                Common.common.Printf("WARNING: overflowed MAX_SHADOW tables, shadow discarded\n")
                opt.verts = null
                opt.indexes = null //Mem_Free(opt.indexes);
                return
            }
            i = 0
            while (i < opt.numVerts) {
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts + i].set(0, opt.verts[i].get(0))
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts + i].set(1, opt.verts[i].get(1))
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts + i].set(2, opt.verts[i].get(2))
                tr_stencilshadow.shadowVerts[tr_stencilshadow.numShadowVerts + i].set(3, 1f)
                i++
            }
            i = 0
            while (i < opt.totalIndexes) {
                val index = opt.indexes[i]
                if (index < 0 || index > opt.numVerts) {
                    Common.common.Error("optimized shadow index out of range")
                }
                tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes + i] =
                    index + tr_stencilshadow.numShadowVerts
                i++
            }
            tr_stencilshadow.numShadowVerts += opt.numVerts
            tr_stencilshadow.numShadowIndexes += opt.totalIndexes

            // note the index distribution so we can sort all the caps after all the sils
            tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].frontCapStart = firstShadowIndex
            tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].rearCapStart =
                firstShadowIndex + opt.numFrontCapIndexes
            tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].silStart =
                firstShadowIndex + opt.numFrontCapIndexes + opt.numRearCapIndexes
            tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].end = tr_stencilshadow.numShadowIndexes
            tr_stencilshadow.indexFrustumNumber++
            opt.verts = null //Mem_Free(opt.verts);
            opt.indexes = null //Mem_Free(opt.indexes);
            return
        }

        //--------------- real-time processing ------------------
        // the dangling edge "face" is never considered to cast a shadow,
        // so any face with dangling edges that casts a shadow will have
        // it's dangling sil edge trigger a sil plane
        tr_stencilshadow.faceCastsShadow[numTris] = 0

        // instead of overflowing or drawing a streamer shadow, don't draw a shadow at all
        // if we ran out of space
        if (tr_stencilshadow.numShadowIndexes + numCapIndexes > tr_stencilshadow.MAX_SHADOW_INDEXES) {
            tr_stencilshadow.overflowed = true
            return
        }
        i = 0
        while (i < numCapIndexes) {
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes + i + 0] =
                tr_stencilshadow.shadowIndexes[firstShadowIndex + i + 2] + 1
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes + i + 1] =
                tr_stencilshadow.shadowIndexes[firstShadowIndex + i + 1] + 1
            tr_stencilshadow.shadowIndexes[tr_stencilshadow.numShadowIndexes + i + 2] =
                tr_stencilshadow.shadowIndexes[firstShadowIndex + i + 0] + 1
            i += 3
        }
        tr_stencilshadow.numShadowIndexes += numCapIndexes
        tr_stencilshadow.c_caps += numCapIndexes * 2
        val preSilIndexes = tr_stencilshadow.numShadowIndexes

        // if any triangles were clipped, we will have a list of edges
        // on the frustum which must now become sil edges
        if (makeClippedPlanes) {
            tr_stencilshadow.R_AddClipSilEdges()
        }

        // any edges that are a transition between a shadowing and
        // non-shadowing triangle will cast a silhouette edge
        tr_stencilshadow.R_AddSilEdges(tri, pointCull, frustum)
        tr_stencilshadow.c_sils += tr_stencilshadow.numShadowIndexes - preSilIndexes

        // project all of the vertexes to the shadow plane, generating
        // an equal number of back vertexes
        tr_stencilshadow.R_ProjectPointsToFarPlane(
            ent,
            light,
            farPlane,
            firstShadowVert,
            tr_stencilshadow.numShadowVerts
        )

        // note the index distribution so we can sort all the caps after all the sils
        tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].frontCapStart = firstShadowIndex
        tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].rearCapStart = firstShadowIndex + numCapIndexes
        tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].silStart = preSilIndexes
        tr_stencilshadow.indexRef[tr_stencilshadow.indexFrustumNumber].end = tr_stencilshadow.numShadowIndexes
        tr_stencilshadow.indexFrustumNumber++
    }

    fun R_MakeShadowFrustums(light: idRenderLightLocal?) {
        var i: Int
        var j: Int
        if (light.parms.pointLight) {

            // exact projection,taking into account asymetric frustums when
            // globalLightOrigin isn't centered
            val centerOutside = Math.abs(light.parms.lightCenter.get(0)) > light.parms.lightRadius.get(0) || Math.abs(
                light.parms.lightCenter.get(1)
            ) > light.parms.lightRadius.get(1) || Math.abs(light.parms.lightCenter.get(2)) > light.parms.lightRadius.get(
                2
            )

            // if the light center of projection is outside the light bounds,
            // we will need to build the planes a little differently

            // make the corners
            val corners: Array<idVec3> = idVec3.Companion.generateArray(8)
            i = 0
            while (i < 8) {
                val temp = idVec3()
                j = 0
                while (j < 3) {
                    if (i and (1 shl j) != 0) {
                        temp.set(j, light.parms.lightRadius.get(j))
                    } else {
                        temp.set(j, -light.parms.lightRadius.get(j))
                    }
                    j++
                }

                // transform to global space
                corners[i].set(light.parms.origin.oPlus(light.parms.axis.times(temp)))
                i++
            }
            light.numShadowFrustums = 0
            for (side in 0..5) {
                val frust = light.shadowFrustums[light.numShadowFrustums]
                val p1 = corners[tr_stencilshadow.faceCorners[side][0]]
                val p2 = corners[tr_stencilshadow.faceCorners[side][1]]
                val p3 = corners[tr_stencilshadow.faceCorners[side][2]]
                val backPlane = idPlane()

                // plane will have positive side inward
                backPlane.FromPoints(p1, p2, p3)

                // if center of projection is on the wrong side, skip
                var d = backPlane.Distance(light.globalLightOrigin)
                if (d < 0) {
                    continue
                }
                frust.numPlanes = 6
                frust.planes[5] = idPlane(backPlane)
                frust.planes[4] = idPlane(backPlane) // we don't really need the extra plane

                // make planes with positive side facing inwards in light local coordinates
                for (edge in 0..3) {
                    val p4 = corners[tr_stencilshadow.faceCorners[side][edge]]
                    val p5 = corners[tr_stencilshadow.faceCorners[side][edge + 1 and 3]]

                    // create a plane that goes through the center of projection
                    frust.planes[edge].FromPoints(p5, p4, light.globalLightOrigin)

                    // see if we should use an adjacent plane instead
                    if (centerOutside) {
                        val p6 = corners[tr_stencilshadow.faceEdgeAdjacent[side][edge]]
                        val sidePlane = idPlane()
                        sidePlane.FromPoints(p5, p4, p6)
                        d = sidePlane.Distance(light.globalLightOrigin)
                        if (d < 0) {
                            // use this plane instead of the edged plane
                            frust.planes[edge] = sidePlane
                        }
                        // we can't guarantee a neighbor, so add sill planes at edge
                        light.shadowFrustums[light.numShadowFrustums].makeClippedPlanes = true
                    }
                }
                light.numShadowFrustums++
            }
            return
        }

        // projected light
        light.numShadowFrustums = 1
        val frust = light.shadowFrustums[0]

        // flip and transform the frustum planes so the positive side faces
        // inward in local coordinates
        // it is important to clip against even the near clip plane, because
        // many projected lights that are faking area lights will have their
        // origin behind solid surfaces.
        i = 0
        while (i < 6) {
            val plane = frust.planes[i]
            plane.SetNormal(light.frustum[i].Normal().oNegative())
            plane.SetDist(-light.frustum[i].Dist())
            i++
        }
        frust.numPlanes = 6
        frust.makeClippedPlanes = true
        // projected lights don't have shared frustums, so any clipped edges
        // right on the planes must have a sil plane created for them
    }

    /*
     =================
     R_CreateShadowVolume

     The returned surface will have a valid bounds and radius for culling.

     Triangles are clipped to the light frustum before projecting.

     A single triangle can clip to as many as 7 vertexes, so
     the worst case expansion is 2*(numindexes/3)*7 verts when counting both
     the front and back caps, although it will usually only be a modest
     increase in vertexes for closed modesl

     The worst case index count is much larger, when the 7 vertex clipped triangle
     needs 15 indexes for the front, 15 for the back, and 42 (a quad on seven sides)
     for the sides, for a total of 72 indexes from the original 3.  Ouch.

     NULL may be returned if the surface doesn't create a shadow volume at all,
     as with a single face that the light is behind.

     If an edge is within an epsilon of the border of the volume, it must be treated
     as if it is clipped for triangles, generating a new sil edge, and act
     as if it was culled for edges, because the sil edge will have been
     generated by the triangle irregardless of if it actually was a sil edge.
     =================
     */
    fun R_CreateShadowVolume(
        ent: idRenderEntityLocal?,
        tri: srfTriangles_s?, light: idRenderLightLocal?,
        optimize: shadowGen_t?, cullInfo: srfCullInfo_t?
    ): srfTriangles_s? {
        var i: Int
        var j: Int
        val lightOrigin = idVec3()
        val newTri: srfTriangles_s?
        var capPlaneBits: Int
        if (!RenderSystem_init.r_shadows.GetBool()) {
            return null
        }
        if (tri.numSilEdges == 0 || tri.numIndexes == 0 || tri.numVerts == 0) {
            return null
        }
        if (tri.numIndexes < 0) {
            Common.common.Error("R_CreateShadowVolume: tri.numIndexes = %d", tri.numIndexes)
        }
        if (tri.numVerts < 0) {
            Common.common.Error("R_CreateShadowVolume: tri.numVerts = %d", tri.numVerts)
        }
        tr_local.tr.pc.c_createShadowVolumes++

        // use the fast infinite projection in dynamic situations, which
        // trades somewhat more overdraw and no cap optimizations for
        // a very simple generation process
        if (optimize == shadowGen_t.SG_DYNAMIC && RenderSystem_init.r_useTurboShadow.GetBool()) {
            return if (tr_local.tr.backEndRendererHasVertexPrograms && RenderSystem_init.r_useShadowVertexProgram.GetBool()) {
                tr_turboshadow.R_CreateVertexProgramTurboShadowVolume(ent, tri, light, cullInfo)
            } else {
                tr_turboshadow.R_CreateTurboShadowVolume(ent, tri, light, cullInfo)
            }
        }
        Interaction.R_CalcInteractionFacing(ent, tri, light, cullInfo)
        val numFaces = tri.numIndexes / 3
        var allFront = 1
        i = 0
        while (i < numFaces && allFront != 0) {
            allFront = allFront and cullInfo.facing[i]
            i++
        }
        if (allFront != 0) {
            // if no faces are the right direction, don't make a shadow at all
            return null
        }

        // clear the shadow volume
        tr_stencilshadow.numShadowIndexes = 0
        tr_stencilshadow.numShadowVerts = 0
        tr_stencilshadow.overflowed = false
        tr_stencilshadow.indexFrustumNumber = 0
        capPlaneBits = 0
        tr_stencilshadow.callOptimizer = optimize == shadowGen_t.SG_OFFLINE

        // the facing information will be the same for all six projections
        // from a point light, as well as for any directed lights
        tr_stencilshadow.globalFacing = cullInfo.facing
        tr_stencilshadow.faceCastsShadow = ByteArray(tri.numIndexes / 3 + 1) // + 1 for fake dangling edge face
        tr_stencilshadow.remap = IntArray(tri.numVerts)
        tr_main.R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, lightOrigin)

        // run through all the shadow frustums, which is one for a projected light,
        // and usually six for a point light, but point lights with centers outside
        // the box may have less
        for (frustumNum in 0 until light.numShadowFrustums) {
            val frust = light.shadowFrustums[frustumNum]
            //		ALIGN16( idPlane[] frustum=new idPlane[6] );
            val frustum = arrayOfNulls<idPlane>(6)

            // transform the planes into entity space
            // we could share and reverse some of the planes between frustums for a minor
            // speed increase
            // the cull test is redundant for a single shadow frustum projected light, because
            // the surface has already been checked against the main light frustums
            j = 0
            while (j < frust.numPlanes) {
                frustum[j] = idPlane()
                tr_main.R_GlobalPlaneToLocal(ent.modelMatrix, frust.planes[j], frustum[j])

                // try to cull the entire surface against this frustum
                val d = tri.bounds.PlaneDistance(frustum[j])
                if (d < -tr_stencilshadow.LIGHT_CLIP_EPSILON) {
                    break
                }
                j++
            }
            if (j != frust.numPlanes) {
                continue
            }
            // we need to check all the triangles
            val oldFrustumNumber = tr_stencilshadow.indexFrustumNumber
            tr_stencilshadow.R_CreateShadowVolumeInFrustum(
                ent,
                tri,
                light,
                lightOrigin,
                frustum,
                frustum[5],
                frust.makeClippedPlanes
            )

            // if we couldn't make a complete shadow volume, it is better to
            // not draw one at all, avoiding streamer problems
            if (tr_stencilshadow.overflowed) {
                return null
            }
            if (tr_stencilshadow.indexFrustumNumber != oldFrustumNumber) {
                // note that we have caps projected against this frustum,
                // which may allow us to skip drawing the caps if all projected
                // planes face away from the viewer and the viewer is outside the light volume
                capPlaneBits = capPlaneBits or (1 shl frustumNum)
            }
        }

        // if no faces have been defined for the shadow volume,
        // there won't be anything at all
        if (tr_stencilshadow.numShadowIndexes == 0) {
            return null
        }

        // this should have been prevented by the overflowed flag, so if it ever happens,
        // it is a code error
        if (tr_stencilshadow.numShadowVerts > tr_stencilshadow.MAX_SHADOW_VERTS || tr_stencilshadow.numShadowIndexes > tr_stencilshadow.MAX_SHADOW_INDEXES) {
            Common.common.FatalError("Shadow volume exceeded allocation")
        }

        // allocate a new surface for the shadow volume
        newTri = tr_trisurf.R_AllocStaticTriSurf()

        // we might consider setting this, but it would only help for
        // large lights that are partially off screen
        newTri.bounds.Clear()

        // copy off the verts and indexes
        newTri.numVerts = tr_stencilshadow.numShadowVerts
        newTri.numIndexes = tr_stencilshadow.numShadowIndexes

        // the shadow verts will go into a main memory buffer as well as a vertex
        // cache buffer, so they can be copied back if they are purged
        tr_trisurf.R_AllocStaticTriSurfShadowVerts(newTri, newTri.numVerts)
        Simd.SIMDProcessor.Memcpy(newTri.shadowVertexes, tr_stencilshadow.shadowVerts, newTri.numVerts)
        tr_trisurf.R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes)
        if (true /* sortCapIndexes */) {
            newTri.shadowCapPlaneBits = capPlaneBits

            // copy the sil indexes first
            newTri.numShadowIndexesNoCaps = 0
            i = 0
            while (i < tr_stencilshadow.indexFrustumNumber) {
                val c = tr_stencilshadow.indexRef[i].end - tr_stencilshadow.indexRef[i].silStart
                Simd.SIMDProcessor.Memcpy(
                    newTri.indexes,
                    newTri.numShadowIndexesNoCaps,
                    tr_stencilshadow.shadowIndexes,
                    tr_stencilshadow.indexRef[i].silStart,
                    c
                )
                newTri.numShadowIndexesNoCaps += c
                i++
            }
            // copy rear cap indexes next
            newTri.numShadowIndexesNoFrontCaps = newTri.numShadowIndexesNoCaps
            i = 0
            while (i < tr_stencilshadow.indexFrustumNumber) {
                val c = tr_stencilshadow.indexRef[i].silStart - tr_stencilshadow.indexRef[i].rearCapStart
                Simd.SIMDProcessor.Memcpy(
                    newTri.indexes,
                    newTri.numShadowIndexesNoFrontCaps,
                    tr_stencilshadow.shadowIndexes,
                    tr_stencilshadow.indexRef[i].rearCapStart,
                    c
                )
                newTri.numShadowIndexesNoFrontCaps += c
                i++
            }
            // copy front cap indexes last
            newTri.numIndexes = newTri.numShadowIndexesNoFrontCaps
            i = 0
            while (i < tr_stencilshadow.indexFrustumNumber) {
                val c = tr_stencilshadow.indexRef[i].rearCapStart - tr_stencilshadow.indexRef[i].frontCapStart
                Simd.SIMDProcessor.Memcpy(
                    newTri.indexes,
                    newTri.numIndexes,
                    tr_stencilshadow.shadowIndexes,
                    tr_stencilshadow.indexRef[i].frontCapStart,
                    c
                )
                newTri.numIndexes += c
                i++
            }
        } else {
            newTri.shadowCapPlaneBits = 63 // we don't have optimized index lists
            Simd.SIMDProcessor.Memcpy(newTri.indexes, tr_stencilshadow.shadowIndexes, newTri.numIndexes)
        }
        if (optimize == shadowGen_t.SG_OFFLINE) {
            shadowopt3.CleanupOptimizedShadowTris(newTri)
        }
        return newTri
    }

    /*
     ============================================================

     TR_STENCILSHADOWS

     "facing" should have one more element than tri->numIndexes / 3, which should be set to 1

     ============================================================
     */
    enum class shadowGen_t {
        SG_DYNAMIC,  // use infinite projections
        SG_STATIC,  // clip to bounds
        SG_OFFLINE // perform very time consuming optimizations
    }

    internal class indexRef_t {
        var end = 0
        var frontCapStart = 0
        var rearCapStart = 0
        var silStart = 0
    }

    internal class clipTri_t {
        var edgeFlags: IntArray? = IntArray(tr_stencilshadow.MAX_CLIPPED_POINTS)
        var numVerts = 0
        val verts: Array<idVec3>? = idVec3.Companion.generateArray(tr_stencilshadow.MAX_CLIPPED_POINTS)
    }
}