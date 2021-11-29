package neo.Renderer

import neo.Renderer.Material.deform_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.drawSurf_s
import neo.framework.Common
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclParticle.particleGen_t
import neo.framework.DeclTable.idDeclTable
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.BinSearch
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Vector.idVec3
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object tr_deform {
    const val MAX_EYEBALL_ISLANDS = 6

    /*
     =====================
     AddTriangleToIsland_r

     =====================
     */
    const val MAX_EYEBALL_TRIS = 10

    /*
     =====================
     R_WindingFromTriangles

     =====================
     */
    const val MAX_TRI_WINDING_INDEXES = 16

    /*
     =====================
     R_FlareDeform

     =====================
     */
    /*
     static void R_FlareDeform( drawSurf_t *surf ) {
     const srfTriangles_t *tri;
     srfTriangles_t		*newTri;
     idPlane	plane;
     float	dot;
     idVec3	localViewer;
     int		j;

     tri = surf.geo;

     if ( tri.numVerts != 4 || tri.numIndexes != 6 ) {
     //FIXME: temp hack for flares on tripleted models
     common.Warning( "R_FlareDeform: not a single quad" );
     return;
     }

     // this srfTriangles_t and all its indexes and caches are in frame
     // memory, and will be automatically disposed of
     newTri = (srfTriangles_t *)R_ClearedFrameAlloc( sizeof( *newTri ) );
     newTri.numVerts = 4;
     newTri.numIndexes = 2*3;
     newTri.indexes = (glIndex_t *)R_FrameAlloc( newTri.numIndexes * sizeof( newTri.indexes[0] ) );

     idDrawVert *ac = (idDrawVert *)_alloca16( newTri.numVerts * sizeof( idDrawVert ) );

     // find the plane
     plane.FromPoints( tri.verts[tri.indexes[0]].xyz, tri.verts[tri.indexes[1]].xyz, tri.verts[tri.indexes[2]].xyz );

     // if viewer is behind the plane, draw nothing
     R_GlobalPointToLocal( surf.space.modelMatrix, tr.viewDef.renderView.vieworg, localViewer );
     float distFromPlane = localViewer * plane.Normal() + plane[3];
     if ( distFromPlane <= 0 ) {
     newTri.numIndexes = 0;
     surf.geo = newTri;
     return;
     }

     idVec3	center;
     center = tri.verts[0].xyz;
     for ( j = 1 ; j < tri.numVerts ; j++ ) {
     center += tri.verts[j].xyz;
     }
     center *= 1.0/tri.numVerts;

     idVec3	dir = localViewer - center;
     dir.Normalize();

     dot = dir * plane.Normal();

     // set vertex colors based on plane angle
     int	color = (int)(dot * 8 * 256);
     if ( color > 255 ) {
     color = 255;
     }
     for ( j = 0 ; j < newTri.numVerts ; j++ ) {
     ac[j].color[0] =
     ac[j].color[1] =
     ac[j].color[2] = color;
     ac[j].color[3] = 255;
     }

     float	spread = surf.shaderRegisters[ surf.material.GetDeformRegister(0) ] * r_flareSize.GetFloat();
     idVec3	edgeDir[4][3];
     glIndex_t		indexes[MAX_TRI_WINDING_INDEXES];
     int		numIndexes = R_WindingFromTriangles( tri, indexes );

     surf.material = declManager.FindMaterial( "textures/smf/anamorphicFlare" );

     // only deal with quads
     if ( numIndexes != 4 ) {
     return;
     }

     // compute centroid
     idVec3 centroid, toeye, forward, up, left;
     centroid.Set( 0, 0, 0 );
     for ( int i = 0; i < 4; i++ ) {
     centroid += tri.verts[ indexes[i] ].xyz;
     }
     centroid /= 4;

     // compute basis vectors
     up.Set( 0, 0, 1 );

     toeye = centroid - localViewer;
     toeye.Normalize();
     left = toeye.Cross( up );
     up = left.Cross( toeye );

     left = left * 40 * 6;
     up = up * 40;

     // compute flares
     struct flare_t {
     float	angle;
     float	length;
     };

     static flare_t flares[] = {
     { 0, 100 },
     { 90, 100 }
     };

     for ( int i = 0; i < 4; i++ ) {
     memset( ac + i, 0, sizeof( ac[i] ) );
     }

     ac[0].xyz = centroid - left;
     ac[0].st[0] = 0; ac[0].st[1] = 0;

     ac[1].xyz = centroid + up;
     ac[1].st[0] = 1; ac[1].st[1] = 0;

     ac[2].xyz = centroid + left;
     ac[2].st[0] = 1; ac[2].st[1] = 1;

     ac[3].xyz = centroid - up;
     ac[3].st[0] = 0; ac[3].st[1] = 1;

     // setup colors
     for ( j = 0 ; j < newTri.numVerts ; j++ ) {
     ac[j].color[0] =
     ac[j].color[1] =
     ac[j].color[2] = 255;
     ac[j].color[3] = 255;
     }

     // setup indexes
     static glIndex_t	triIndexes[2*3] = {
     0,1,2,  0,2,3
     };

     memcpy( newTri.indexes, triIndexes, sizeof( triIndexes ) );

     R_FinishDeform( surf, newTri, ac );
     }
     */
    val   /*glIndex_t	*/triIndexes /*[18*3]*/: IntArray? = intArrayOf(
        0, 4, 5,
        0, 5, 6,
        0, 6, 7,
        0, 7, 1,
        1, 7, 8,
        1, 8, 9,
        15, 4, 0,
        15, 0, 3,
        3, 0, 1,
        3, 1, 2,
        2, 1, 9,
        2, 9, 10,
        14, 15, 3,
        14, 3, 13,
        13, 3, 2,
        13, 2, 12,
        12, 2, 11,
        11, 2, 10
    )

    /*
     =====================
     R_TubeDeform

     will pivot a rectangular quad along the center of its long axis

     Note that a geometric tube with even quite a few sides tube will almost certainly render much faster
     than this, so this should only be for faked volumetric tubes.
     Make sure this is used with twosided translucent shaders, because the exact side
     order may not be correct.
     =====================
     */
    private val edgeVerts /*[6][2]*/: Array<IntArray?>? = arrayOf(
        intArrayOf(0, 1),
        intArrayOf(1, 2),
        intArrayOf(2, 0),
        intArrayOf(3, 4),
        intArrayOf(4, 5),
        intArrayOf(5, 3)
    )

    /*
     =================
     R_FinishDeform

     The ambientCache is on the stack, so we don't want to leave a reference
     to it that would try to be freed later.  Create the ambientCache immediately.
     =================
     */
    fun R_FinishDeform(drawSurf: drawSurf_s?, newTri: srfTriangles_s?, ac: Array<idDrawVert?>?) {
        if (null == newTri) {
            return
        }

        // generate current normals, tangents, and bitangents
        // We might want to support the possibility of deform functions generating
        // explicit normals, and we might also want to allow the cached deformInfo
        // optimization for these.
        // FIXME: this doesn't work, because the deformed surface is just the
        // ambient one, and there isn't an opportunity to generate light interactions
        if (drawSurf.material.ReceivesLighting()) {
            newTri.verts = ac
            tr_trisurf.R_DeriveTangents(newTri, false)
            newTri.verts = null
        }
        newTri.ambientCache = VertexCache.vertexCache.AllocFrameTemp(ac, newTri.numVerts * idDrawVert.Companion.BYTES)
        // if we are out of vertex cache, leave it the way it is
        if (newTri.ambientCache != null) {
            drawSurf.geo = newTri
        }
    }

    /*
     =====================
     R_AutospriteDeform

     Assuming all the triangles for this shader are independant
     quads, rebuild them as forward facing sprites
     =====================
     */
    fun R_AutospriteDeform(surf: drawSurf_s?) {
        var i: Int
        var v: idDrawVert
        val mid = idVec3()
        val delta = idVec3()
        var radius: Float
        val left = idVec3()
        val up = idVec3()
        val leftDir = idVec3()
        val upDir = idVec3()
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        tri = surf.geo
        if (tri.numVerts and 3 != 0) {
            Common.common.Warning("R_AutospriteDeform: shader had odd vertex count")
            return
        }
        if (tri.numIndexes != (tri.numVerts shr 2) * 6) {
            Common.common.Warning("R_AutospriteDeform: autosprite had odd index count")
            return
        }
        tr_main.R_GlobalVectorToLocal(surf.space.modelMatrix, tr_local.tr.viewDef.renderView.viewaxis.oGet(1), leftDir)
        tr_main.R_GlobalVectorToLocal(surf.space.modelMatrix, tr_local.tr.viewDef.renderView.viewaxis.oGet(2), upDir)
        if (tr_local.tr.viewDef.isMirror) {
            leftDir.oSet(Vector.getVec3_origin().oMinus(leftDir))
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(newTri.numIndexes) // R_FrameAlloc(newTri.numIndexes);
        val ac = Stream.generate { idDrawVert() }.limit(newTri.numVerts.toLong())
            .toArray<idDrawVert?> { _Dummy_.__Array__() }
        i = 0
        while (i < tri.numVerts) {

            // find the midpoint
            v = tri.verts[i]
            val v1 = tri.verts[i + 1]
            val v2 = tri.verts[i + 2]
            val v3 = tri.verts[i + 3]
            mid.oSet(0, 0.25f * (v.xyz.oGet(0) + v1.xyz.oGet(0) + v2.xyz.oGet(0) + v3.xyz.oGet(0)))
            mid.oSet(1, 0.25f * (v.xyz.oGet(1) + v1.xyz.oGet(1) + v2.xyz.oGet(1) + v3.xyz.oGet(1)))
            mid.oSet(2, 0.25f * (v.xyz.oGet(2) + v1.xyz.oGet(2) + v2.xyz.oGet(2) + v3.xyz.oGet(2)))
            delta.oSet(v.xyz.oMinus(mid))
            radius = delta.Length() * 0.707f // / sqrt(2)
            left.oSet(leftDir.times(radius))
            up.oSet(upDir.times(radius))
            ac[i + 0].xyz.oSet(mid.oPlus(left.oPlus(up)))
            ac[i + 0].st.oSet(0, 0f)
            ac[i + 0].st.oSet(1, 0f)
            ac[i + 1].xyz.oSet(mid.oMinus(left.oPlus(up)))
            ac[i + 1].st.oSet(0, 1f)
            ac[i + 1].st.oSet(1, 0f)
            ac[i + 2].xyz.oSet(mid.oMinus(left.oMinus(up)))
            ac[i + 2].st.oSet(0, 1f)
            ac[i + 2].st.oSet(1, 1f)
            ac[i + 3].xyz.oSet(mid.oPlus(left.oMinus(up)))
            ac[i + 3].st.oSet(0, 0f)
            ac[i + 3].st.oSet(1, 1f)
            newTri.indexes[6 * (i shr 2) + 0] = i
            newTri.indexes[6 * (i shr 2) + 1] = i + 1
            newTri.indexes[6 * (i shr 2) + 2] = i + 2
            newTri.indexes[6 * (i shr 2) + 3] = i
            newTri.indexes[6 * (i shr 2) + 4] = i + 2
            newTri.indexes[6 * (i shr 2) + 5] = i + 3
            i += 4
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    fun R_TubeDeform(surf: drawSurf_s?) {
        var i: Int
        var j: Int
        var indexes: Int
        val tri: srfTriangles_s?
        tri = surf.geo
        if (tri.numVerts and 3 != 0) {
            Common.common.Error("R_AutospriteDeform: shader had odd vertex count")
        }
        if (tri.numIndexes != (tri.numVerts shr 2) * 6) {
            Common.common.Error("R_AutospriteDeform: autosprite had odd index count")
        }

        // we need the view direction to project the minor axis of the tube
        // as the view changes
        val localView = idVec3()
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, tr_local.tr.viewDef.renderView.vieworg, localView)

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        val newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(newTri.numIndexes) // R_FrameAlloc(newTri.numIndexes);
        System.arraycopy(
            tri.indexes,
            0,
            newTri.indexes,
            0,
            newTri.numIndexes
        ) //memcpy( newTri.indexes, tri.indexes, newTri.numIndexes * sizeof( newTri.indexes[0] ) );
        val ac = Stream.generate { idDrawVert() }.limit(newTri.numVerts.toLong())
            .toArray<idDrawVert?> { _Dummy_.__Array__() } //memset( ac, 0, sizeof( idDrawVert ) * newTri.numVerts );

        // this is a lot of work for two triangles...
        // we could precalculate a lot if it is an issue, but it would mess up
        // the shader abstraction
        i = 0
        indexes = 0
        while (i < tri.numVerts) {
            val lengths = FloatArray(2)
            val nums = IntArray(2)
            val mid: Array<idVec3?> = idVec3.Companion.generateArray(2)
            val major = idVec3()
            val minor = idVec3()
            var v1: idDrawVert?
            var v2: idDrawVert?

            // identify the two shortest edges out of the six defined by the indexes
            nums[1] = 0
            nums[0] = nums[1]
            lengths[1] = 999999
            lengths[0] = lengths[1]
            j = 0
            while (j < 6) {
                var l: Float
                v1 = tri.verts[tri.indexes[i + tr_deform.edgeVerts[j][0]]]
                v2 = tri.verts[tri.indexes[i + tr_deform.edgeVerts[j][1]]]
                l = v1.xyz.oMinus(v2.xyz).Length()
                if (l < lengths[0]) {
                    nums[1] = nums[0]
                    lengths[1] = lengths[0]
                    nums[0] = j
                    lengths[0] = l
                } else if (l < lengths[1]) {
                    nums[1] = j
                    lengths[1] = l
                }
                j++
            }

            // find the midpoints of the two short edges, which
            // will give us the major axis in object coordinates
            j = 0
            while (j < 2) {
                v1 = tri.verts[tri.indexes[i + tr_deform.edgeVerts[nums[j]][0]]]
                v2 = tri.verts[tri.indexes[i + tr_deform.edgeVerts[nums[j]][1]]]
                mid[j].oSet(
                    idVec3(
                        0.5f * (v1.xyz.oGet(0) + v2.xyz.oGet(0)),
                        0.5f * (v1.xyz.oGet(1) + v2.xyz.oGet(1)),
                        0.5f * (v1.xyz.oGet(2) + v2.xyz.oGet(2))
                    )
                )
                j++
            }

            // find the vector of the major axis
            major.oSet(mid[1].oMinus(mid[0]))

            // re-project the points
            j = 0
            while (j < 2) {
                var l: Float
                val i1 = tri.indexes[i + tr_deform.edgeVerts[nums[j]][0]]
                val i2 = tri.indexes[i + tr_deform.edgeVerts[nums[j]][1]]
                ac[i1] = tri.verts[i1]
                val av1 = ac[i1]
                ac[i2] = tri.verts[i2]
                val av2 = ac[i2]
                //                av1 = tri.verts[i1];
//                av2 = tri.verts[i2];
                l = 0.5f * lengths[j]

                // cross this with the view direction to get minor axis
                val dir = idVec3(mid[j].oMinus(localView))
                minor.Cross(major, dir)
                minor.Normalize()
                if (j != 0) {
                    av1.xyz.oSet(mid[j].oMinus(minor.times(l)))
                    av2.xyz.oSet(mid[j].oPlus(minor.times(l)))
                } else {
                    av1.xyz.oSet(mid[j].oPlus(minor.times(l)))
                    av2.xyz.oSet(mid[j].oMinus(minor.times(l)))
                }
                j++
            }
            i += 4
            indexes += 6
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    fun R_WindingFromTriangles(
        tri: srfTriangles_s?,    /*glIndex_t*/
        indexes: IntArray? /*[MAX_TRI_WINDING_INDEXES]*/
    ): Int {
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        indexes.get(0) = tri.indexes[0]
        var numIndexes = 1
        val numTris = tri.numIndexes / 3
        do {
            // find an edge that goes from the current index to another
            // index that isn't already used, and isn't an internal edge
            i = 0
            while (i < numTris) {
                j = 0
                while (j < 3) {
                    if (tri.indexes[i * 3 + j] != indexes.get(numIndexes - 1)) {
                        j++
                        continue
                    }
                    val next = tri.indexes[i * 3 + (j + 1) % 3]

                    // make sure it isn't already used
                    if (numIndexes == 1) {
                        if (next == indexes.get(0)) {
                            j++
                            continue
                        }
                    } else {
                        k = 1
                        while (k < numIndexes) {
                            if (indexes.get(k) == next) {
                                break
                            }
                            k++
                        }
                        if (k != numIndexes) {
                            j++
                            continue
                        }
                    }

                    // make sure it isn't an interior edge
                    k = 0
                    while (k < numTris) {
                        if (k == i) {
                            k++
                            continue
                        }
                        l = 0
                        while (l < 3) {
                            var a: Int
                            var b: Int
                            a = tri.indexes[k * 3 + l]
                            if (a != next) {
                                l++
                                continue
                            }
                            b = tri.indexes[k * 3 + (l + 1) % 3]
                            if (b != indexes.get(numIndexes - 1)) {
                                l++
                                continue
                            }

                            // this is an interior edge
                            break
                            l++
                        }
                        if (l != 3) {
                            break
                        }
                        k++
                    }
                    if (k != numTris) {
                        j++
                        continue
                    }

                    // add this to the list
                    indexes.get(numIndexes) = next
                    numIndexes++
                    break
                    j++
                }
                if (j != 3) {
                    break
                }
                i++
            }
            if (numIndexes == tri.numVerts) {
                break
            }
        } while (i != numTris)
        return numIndexes
    }

    fun R_FlareDeform(surf: drawSurf_s?) {
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        val plane = idPlane()
        val dot: Float
        val localViewer = idVec3()
        var j: Int
        tri = surf.geo
        if (tri.numVerts != 4 || tri.numIndexes != 6) {
            //FIXME: temp hack for flares on tripleted models
            Common.common.Warning("R_FlareDeform: not a single quad")
            return
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = 16
        newTri.numIndexes = 18 * 3
        newTri.indexes = IntArray(newTri.numIndexes)
        val ac = arrayOfNulls<idDrawVert?>(newTri.numVerts)

        // find the plane
        plane.FromPoints(tri.verts[tri.indexes[0]].xyz, tri.verts[tri.indexes[1]].xyz, tri.verts[tri.indexes[2]].xyz)

        // if viewer is behind the plane, draw nothing
        tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, tr_local.tr.viewDef.renderView.vieworg, localViewer)
        val distFromPlane = localViewer.times(plane.Normal()) + plane.oGet(3)
        if (distFromPlane <= 0) {
            newTri.numIndexes = 0
            surf.geo = newTri
            return
        }
        val center = idVec3()
        center.oSet(tri.verts[0].xyz)
        j = 1
        while (j < tri.numVerts) {
            center.plusAssign(tri.verts[j].xyz)
            j++
        }
        center.timesAssign(1.0f / tri.numVerts)
        val dir = idVec3(localViewer.oMinus(center))
        dir.Normalize()
        dot = dir.oMultiply(plane.Normal())

        // set vertex colors based on plane angle
        var color = (dot * 8 * 256).toInt()
        if (color > 255) {
            color = 255
        }
        j = 0
        while (j < newTri.numVerts) {
            ac[j] = idDrawVert()
            ac[j].color[2] = color.toByte()
            ac[j].color[1] = ac[j].color[2]
            ac[j].color[0] = ac[j].color[1]
            ac[j].color[3] = 255.toByte()
            j++
        }
        val spread = surf.shaderRegisters[surf.material.GetDeformRegister(0)] * RenderSystem_init.r_flareSize.GetFloat()
        val edgeDir: Array<Array<idVec3?>?> = idVec3.Companion.generateArray(4, 3)
        val   /*glIndex_t*/indexes = IntArray(tr_deform.MAX_TRI_WINDING_INDEXES)
        val numIndexes = tr_deform.R_WindingFromTriangles(tri, indexes)

        // only deal with quads
        if (numIndexes != 4) {
            return
        }
        var i: Int
        // calculate vector directions
        i = 0
        while (i < 4) {
            ac[i].xyz.oSet(tri.verts[indexes[i]].xyz)
            ac[i].st.oSet(0, ac[i].st.oSet(1, 0.5f))
            val toEye = idVec3(tri.verts[indexes[i]].xyz.oMinus(localViewer))
            toEye.Normalize()
            val d1 = idVec3(tri.verts[indexes[(i + 1) % 4]].xyz.oMinus(localViewer))
            d1.Normalize()
            edgeDir[i].get(1).Cross(toEye, d1)
            edgeDir[i].get(1).Normalize()
            edgeDir[i].get(1).oSet(Vector.getVec3_origin().oMinus(edgeDir[i].get(1)))
            val d2 = idVec3(tri.verts[indexes[(i + 3) % 4]].xyz.oMinus(localViewer))
            d2.Normalize()
            edgeDir[i].get(0).Cross(toEye, d2)
            edgeDir[i].get(0).Normalize()
            edgeDir[i].get(2).oSet(edgeDir[i].get(0).oPlus(edgeDir[i].get(1)))
            edgeDir[i].get(2).Normalize()
            i++
        }

        // build all the points
        ac[4].xyz.oSet(tri.verts[indexes[0]].xyz.oPlus(edgeDir[0].get(0).times(spread)))
        ac[4].st.oSet(0, 0f)
        ac[4].st.oSet(1, 0.5f)
        ac[5].xyz.oSet(tri.verts[indexes[0]].xyz.oPlus(edgeDir[0].get(2).times(spread)))
        ac[5].st.oSet(0, 0f)
        ac[5].st.oSet(1, 0f)
        ac[6].xyz.oSet(tri.verts[indexes[0]].xyz.oPlus(edgeDir[0].get(1).times(spread)))
        ac[6].st.oSet(0, 0.5f)
        ac[6].st.oSet(1, 0f)
        ac[7].xyz.oSet(tri.verts[indexes[1]].xyz.oPlus(edgeDir[1].get(0).times(spread)))
        ac[7].st.oSet(0, 0.5f)
        ac[7].st.oSet(1, 0f)
        ac[8].xyz.oSet(tri.verts[indexes[1]].xyz.oPlus(edgeDir[1].get(2).times(spread)))
        ac[8].st.oSet(0, 1f)
        ac[8].st.oSet(1, 0f)
        ac[9].xyz.oSet(tri.verts[indexes[1]].xyz.oPlus(edgeDir[1].get(1).times(spread)))
        ac[9].st.oSet(0, 1f)
        ac[9].st.oSet(1, 0.5f)
        ac[10].xyz.oSet(tri.verts[indexes[2]].xyz.oPlus(edgeDir[2].get(0).times(spread)))
        ac[10].st.oSet(0, 1f)
        ac[10].st.oSet(1, 0.5f)
        ac[11].xyz.oSet(tri.verts[indexes[2]].xyz.oPlus(edgeDir[2].get(2).times(spread)))
        ac[11].st.oSet(0, 1f)
        ac[11].st.oSet(1, 1f)
        ac[12].xyz.oSet(tri.verts[indexes[2]].xyz.oPlus(edgeDir[2].get(1).times(spread)))
        ac[12].st.oSet(0, 0.5f)
        ac[12].st.oSet(1, 1f)
        ac[13].xyz.oSet(tri.verts[indexes[3]].xyz.oPlus(edgeDir[3].get(0).times(spread)))
        ac[13].st.oSet(0, 0.5f)
        ac[13].st.oSet(1, 1f)
        ac[14].xyz.oSet(tri.verts[indexes[3]].xyz.oPlus(edgeDir[3].get(2).times(spread)))
        ac[14].st.oSet(0, 0f)
        ac[14].st.oSet(1, 1f)
        ac[15].xyz.oSet(tri.verts[indexes[3]].xyz.oPlus(edgeDir[3].get(1).times(spread)))
        ac[15].st.oSet(0, 0f)
        ac[15].st.oSet(1, 0.5f)
        i = 4
        while (i < 16) {
            dir.oSet(ac[i].xyz.oMinus(localViewer))
            val len = dir.Normalize()
            val ang = dir.oMultiply(plane.Normal())

//		ac[i].xyz -= dir * spread * 2;
            val newLen = -(distFromPlane / ang)
            if (newLen > 0 && newLen < len) {
                ac[i].xyz.oSet(localViewer.oPlus(dir.oMultiply(newLen)))
            }
            ac[i].st.oSet(0, 0f)
            ac[i].st.oSet(1, 0.5f)
            i++
        }

//if (true){
//	static glIndex_t	triIndexes[18*3] = {
//		0,4,5,  0,5,6, 0,6,7, 0,7,1, 1,7,8, 1,8,9,
//		15,4,0, 15,0,3, 3,0,1, 3,1,2, 2,1,9, 2,9,10,
//		14,15,3, 14,3,13, 13,3,2, 13,2,12, 12,2,11, 11,2,10
//	};
//}else{
//	newTri.numIndexes = 12;
//	static glIndex_t triIndexes[4*3] = {
//		0,1,2, 0,2,3, 0,4,5,0,5,6
//	};
//}
//        memcpy(newTri.indexes, triIndexes, sizeof(triIndexes));
        System.arraycopy(tr_deform.triIndexes, 0, newTri.indexes, 0, tr_deform.triIndexes.size)
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    //=====================================================================================
    /*
     =====================
     R_ExpandDeform

     Expands the surface along it's normals by a shader amount
     =====================
     */
    fun R_ExpandDeform(surf: drawSurf_s?) {
        var i: Int
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        tri = surf.geo

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac = arrayOfNulls<idDrawVert?>(newTri.numVerts)
        val dist = surf.shaderRegisters[surf.material.GetDeformRegister(0)]
        i = 0
        while (i < tri.numVerts) {
            ac[i] = tri.verts[i]
            ac[i].xyz.oSet(tri.verts[i].xyz.oPlus(tri.verts[i].normal.times(dist)))
            i++
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    //=====================================================================================
    /*
     =====================
     R_MoveDeform

     Moves the surface along the X axis, mostly just for demoing the deforms
     =====================
     */
    fun R_MoveDeform(surf: drawSurf_s?) {
        var i: Int
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        tri = surf.geo

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac = arrayOfNulls<idDrawVert?>(newTri.numVerts)
        val dist = surf.shaderRegisters[surf.material.GetDeformRegister(0)]
        i = 0
        while (i < tri.numVerts) {
            ac[i] = tri.verts[i]
            ac[i].xyz.plusAssign(0, dist)
            i++
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    /*
     =====================
     R_TurbulentDeform

     Turbulently deforms the XYZ, S, and T values
     =====================
     */
    fun R_TurbulentDeform(surf: drawSurf_s?) {
        var i: Int
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        tri = surf.geo

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = tri.indexes
        val ac = arrayOfNulls<idDrawVert?>(newTri.numVerts)
        val table = surf.material.GetDeformDecl() as idDeclTable
        val range = surf.shaderRegisters[surf.material.GetDeformRegister(0)]
        val timeOfs = surf.shaderRegisters[surf.material.GetDeformRegister(1)]
        val domain = surf.shaderRegisters[surf.material.GetDeformRegister(2)]
        val tOfs = 0.5f
        i = 0
        while (i < tri.numVerts) {
            var f =
                tri.verts[i].xyz.oGet(0) * 0.003f + tri.verts[i].xyz.oGet(1) * 0.007f + tri.verts[i].xyz.oGet(2) * 0.011f
            f = timeOfs + domain * f
            f += timeOfs
            ac[i] = tri.verts[i]
            ac[i].st.oPluSet(0, range * table.TableLookup(f))
            ac[i].st.oPluSet(1, range * table.TableLookup(f + tOfs))
            i++
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    fun AddTriangleToIsland_r(tri: srfTriangles_s?, triangleNum: Int, usedList: BooleanArray?, island: eyeIsland_t?) {
        val a: Int
        val b: Int
        val c: Int
        usedList.get(triangleNum) = true

        // add to the current island
        if (island.numTris == tr_deform.MAX_EYEBALL_TRIS) {
            Common.common.Error("MAX_EYEBALL_TRIS")
        }
        island.tris.get(island.numTris) = triangleNum
        island.numTris++

        // recurse into all neighbors
        a = tri.indexes[triangleNum * 3]
        b = tri.indexes[triangleNum * 3 + 1]
        c = tri.indexes[triangleNum * 3 + 2]
        island.bounds.AddPoint(tri.verts[a].xyz)
        island.bounds.AddPoint(tri.verts[b].xyz)
        island.bounds.AddPoint(tri.verts[c].xyz)
        val numTri = tri.numIndexes / 3
        for (i in 0 until numTri) {
            if (usedList.get(i)) {
                continue
            }
            if (tri.indexes[i * 3 + 0] == a || tri.indexes[i * 3 + 1] == a || tri.indexes[i * 3 + 2] == a || tri.indexes[i * 3 + 0] == b || tri.indexes[i * 3 + 1] == b || tri.indexes[i * 3 + 2] == b || tri.indexes[i * 3 + 0] == c || tri.indexes[i * 3 + 1] == c || tri.indexes[i * 3 + 2] == c) {
                tr_deform.AddTriangleToIsland_r(tri, i, usedList, island)
            }
        }
    }

    /*
     =====================
     R_EyeballDeform

     Each eyeball surface should have an separate upright triangle behind it, long end
     pointing out the eye, and another single triangle in front of the eye for the focus point.
     =====================
     */
    fun R_EyeballDeform(surf: drawSurf_s?) {
        var i: Int
        var j: Int
        var k: Int
        val tri: srfTriangles_s?
        val newTri: srfTriangles_s
        val islands = arrayOfNulls<eyeIsland_t?>(tr_deform.MAX_EYEBALL_ISLANDS)
        var numIslands: Int
        val triUsed = BooleanArray(tr_deform.MAX_EYEBALL_ISLANDS * tr_deform.MAX_EYEBALL_TRIS)
        tri = surf.geo

        // separate all the triangles into islands
        val numTri = tri.numIndexes / 3
        if (numTri > tr_deform.MAX_EYEBALL_ISLANDS * tr_deform.MAX_EYEBALL_TRIS) {
            Common.common.Printf("R_EyeballDeform: too many triangles in surface")
            return
        }
        //	memset( triUsed, 0, sizeof( triUsed ) );
        numIslands = 0
        while (numIslands < tr_deform.MAX_EYEBALL_ISLANDS) {
            islands[numIslands] = eyeIsland_t()
            islands[numIslands].numTris = 0
            islands[numIslands].bounds.Clear()
            i = 0
            while (i < numTri) {
                if (!triUsed[i]) {
                    tr_deform.AddTriangleToIsland_r(tri, i, triUsed, islands[numIslands])
                    break
                }
                i++
            }
            if (i == numTri) {
                break
            }
            numIslands++
        }

        // assume we always have two eyes, two origins, and two targets
        if (numIslands != 3) {
            Common.common.Printf("R_EyeballDeform: %d triangle islands\n", numIslands)
            return
        }

        // this srfTriangles_t and all its indexes and caches are in frame
        // memory, and will be automatically disposed of
        // the surface cannot have more indexes or verts than the original
        newTri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(newTri));
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        newTri.indexes = IntArray(tri.numIndexes)
        val ac =
            Stream.generate { idDrawVert() }.limit(tri.numVerts.toLong()).toArray<idDrawVert?> { _Dummy_.__Array__() }
        newTri.numIndexes = 0

        // decide which islands are the eyes and points
        i = 0
        while (i < numIslands) {
            islands[i].mid.oSet(islands[i].bounds.GetCenter())
            i++
        }
        i = 0
        while (i < numIslands) {
            val island = islands[i]
            if (island.numTris == 1) {
                i++
                continue
            }

            // the closest single triangle point will be the eye origin
            // and the next-to-farthest will be the focal point
            val origin = idVec3()
            val focus = idVec3()
            var originIsland = 0
            val dist = FloatArray(tr_deform.MAX_EYEBALL_ISLANDS)
            val sortOrder = IntArray(tr_deform.MAX_EYEBALL_ISLANDS)
            j = 0
            while (j < numIslands) {
                val dir = idVec3(islands[j].mid.oMinus(island.mid))
                dist[j] = dir.Length()
                sortOrder[j] = j
                k = j - 1
                while (k >= 0) {
                    if (dist[k] > dist[k + 1]) {
                        val temp = sortOrder[k]
                        sortOrder[k] = sortOrder[k + 1]
                        sortOrder[k + 1] = temp
                        val ftemp = dist[k]
                        dist[k] = dist[k + 1]
                        dist[k + 1] = ftemp
                    }
                    k--
                }
                j++
            }
            originIsland = sortOrder[1]
            origin.oSet(islands[originIsland].mid)
            focus.oSet(islands[sortOrder[2]].mid)

            // determine the projection directions based on the origin island triangle
            val dir = idVec3(focus.oMinus(origin))
            dir.Normalize()
            val p1 = tri.verts[tri.indexes[islands[originIsland].tris.get(0) + 0]].xyz
            val p2 = tri.verts[tri.indexes[islands[originIsland].tris.get(0) + 1]].xyz
            val p3 = tri.verts[tri.indexes[islands[originIsland].tris.get(0) + 2]].xyz
            val v1 = idVec3(p2.oMinus(p1))
            v1.Normalize()
            val v2 = idVec3(p3.oMinus(p1))
            v2.Normalize()

            // texVec[0] will be the normal to the origin triangle
            val texVec: Array<idVec3?> = idVec3.Companion.generateArray(2)
            texVec[0].Cross(v1, v2)
            texVec[1].Cross(texVec[0], dir)
            j = 0
            while (j < 2) {
                texVec[j].minusAssign(dir.oMultiply(texVec[j].times(dir)))
                texVec[j].Normalize()
                j++
            }

            // emit these triangles, generating the projected texcoords
            j = 0
            while (j < islands[i].numTris) {
                k = 0
                while (k < 3) {
                    var index = islands[i].tris.get(j) * 3
                    index = tri.indexes[index + k]
                    newTri.indexes[newTri.numIndexes++] = index
                    ac[index].xyz.oSet(tri.verts[index].xyz)
                    val local = idVec3(tri.verts[index].xyz.oMinus(origin))
                    ac[index].st.oSet(0, 0.5f + local.oMultiply(texVec[0]))
                    ac[index].st.oSet(1, 0.5f + local.oMultiply(texVec[1]))
                    k++
                }
                j++
            }
            i++
        }
        tr_deform.R_FinishDeform(surf, newTri, ac)
    }

    //==========================================================================================
    /*
     =====================
     R_ParticleDeform

     Emit particles from the surface instead of drawing it
     =====================
     */
    fun R_ParticleDeform(surf: drawSurf_s?, useArea: Boolean) {
        val renderEntity = surf.space.entityDef.parms
        val viewDef = tr_local.tr.viewDef
        val particleSystem = surf.material.GetDeformDecl() as idDeclParticle
        if (RenderSystem_init.r_skipParticles.GetBool()) {
            return
        }

//    if (false) {
//        if (renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME] != 0f
//                && viewDef.renderView.time * 0.001 >= renderEntity.shaderParms[SHADERPARM_PARTICLE_STOPTIME]) {
//            // the entire system has faded out
//            return null;
//        }
//    }
        //
        // calculate the area of all the triangles
        //
        val numSourceTris = surf.geo.numIndexes / 3
        var totalArea = 0f
        var sourceTriAreas: Array<Float?>? = null
        val srcTri = surf.geo
        if (useArea) {
            sourceTriAreas = arrayOfNulls<Float?>(numSourceTris)
            var triNum = 0
            var i = 0
            while (i < srcTri.numIndexes) {
                var area: Float
                area = idWinding.Companion.TriangleArea(
                    srcTri.verts[srcTri.indexes[i]].xyz,
                    srcTri.verts[srcTri.indexes[i + 1]].xyz,
                    srcTri.verts[srcTri.indexes[i + 2]].xyz
                )
                sourceTriAreas[triNum] = totalArea
                totalArea += area
                i += 3
                triNum++
            }
        }

        //
        // create the particles almost exactly the way idRenderModelPrt does
        //
        val g = particleGen_t()
        g.renderEnt = renderEntity
        g.renderView = viewDef.renderView
        g.origin.Zero()
        g.axis.oSet(idMat3.Companion.getMat3_identity())
        for (currentTri in 0 until if (useArea) 1 else numSourceTris) {
            for (stageNum in 0 until particleSystem.stages.Num()) {
                val stage = particleSystem.stages.oGet(stageNum)
                if (null == stage.material) {
                    continue
                }
                if (0 == stage.cycleMsec) {
                    continue
                }
                if (stage.hidden) {        // just for gui particle editor use
                    continue
                }

                // we interpret stage.totalParticles as "particles per map square area"
                // so the systems look the same on different size surfaces
                val totalParticles =
                    (if (useArea) stage.totalParticles * totalArea / 4096.0 else stage.totalParticles).toInt()
                val count = totalParticles * stage.NumQuadsPerParticle()

                // allocate a srfTriangles in temp memory that can hold all the particles
                var tri: srfTriangles_s
                tri = srfTriangles_s() // R_ClearedFrameAlloc(sizeof(tri));
                tri.numVerts = 4 * count
                tri.numIndexes = 6 * count
                tri.verts = arrayOfNulls(tri.numVerts) // R_FrameAlloc(tri.numVerts);
                tri.indexes = IntArray(tri.numIndexes) // R_FrameAlloc(tri.numIndexes);

                // just always draw the particles
                tri.bounds.oSet(stage.bounds)
                tri.numVerts = 0
                val steppingRandom = idRandom()
                val steppingRandom2 = idRandom()
                val stageAge =
                    (g.renderView.time + renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] * 1000 - stage.timeOffset * 1000).toInt()
                val stageCycle = stageAge / stage.cycleMsec
                var inCycleTime = stageAge - stageCycle * stage.cycleMsec

                // some particles will be in this cycle, some will be in the previous cycle
                steppingRandom.SetSeed(stageCycle shl 10 and idRandom.Companion.MAX_RAND xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.Companion.MAX_RAND).toInt())
                steppingRandom2.SetSeed(stageCycle - 1 shl 10 and idRandom.Companion.MAX_RAND xor (renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] * idRandom.Companion.MAX_RAND).toInt())
                for (index in 0 until totalParticles) {
                    g.index = index

                    // bump the random
                    steppingRandom.RandomInt()
                    steppingRandom2.RandomInt()

                    // calculate local age for this index
                    val bunchOffset = (stage.particleLife * 1000 * stage.spawnBunching * index / totalParticles).toInt()
                    val particleAge = stageAge - bunchOffset
                    val particleCycle = particleAge / stage.cycleMsec
                    if (particleCycle < 0) {
                        // before the particleSystem spawned
                        continue
                    }
                    if (stage.cycles != 0f && particleCycle >= stage.cycles) {
                        // cycled systems will only run cycle times
                        continue
                    }
                    if (particleCycle == stageCycle) {
                        g.random = idRandom(steppingRandom)
                    } else {
                        g.random = idRandom(steppingRandom2)
                    }
                    inCycleTime = particleAge - particleCycle * stage.cycleMsec
                    if (renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] != 0
                        && g.renderView.time - inCycleTime >= renderEntity.shaderParms[RenderWorld.SHADERPARM_PARTICLE_STOPTIME] * 1000
                    ) {
                        // don't fire any more particles
                        continue
                    }

                    // supress particles before or after the age clamp
                    g.frac = inCycleTime.toFloat() / (stage.particleLife * 1000)
                    if (g.frac < 0) {
                        // yet to be spawned
                        continue
                    }
                    if (g.frac > 1.0) {
                        // this particle is in the deadTime band
                        continue
                    }

                    //---------------
                    // locate the particle origin and axis somewhere on the surface
                    //---------------
                    var pointTri = currentTri
                    if (useArea) {
                        // select a triangle based on an even area distribution
                        pointTri = BinSearch.idBinSearch_LessEqual(
                            sourceTriAreas,
                            numSourceTris,
                            g.random.RandomFloat() * totalArea
                        )
                    }

                    // now pick a random point inside pointTri
                    val v1 = srcTri.verts[srcTri.indexes[pointTri * 3 + 0]]
                    val v2 = srcTri.verts[srcTri.indexes[pointTri * 3 + 1]]
                    val v3 = srcTri.verts[srcTri.indexes[pointTri * 3 + 2]]
                    var f1 = g.random.RandomFloat()
                    var f2 = g.random.RandomFloat()
                    var f3 = g.random.RandomFloat()
                    val ft = 1.0f / (f1 + f2 + f3 + 0.0001f)
                    f1 *= ft
                    f2 *= ft
                    f3 *= ft
                    g.origin.oSet(v1.xyz.times(f1).oPlus(v2.xyz.times(f2).oPlus(v3.xyz.times(f3))))
                    g.axis.oSet(
                        0,
                        v1.tangents[0].times(f1)
                            .oPlus(v2.tangents[0].times(f2).oPlus(v3.tangents[0].times(f3)))
                    )
                    g.axis.oSet(
                        1,
                        v1.tangents[1].times(f1)
                            .oPlus(v2.tangents[1].times(f2).oPlus(v3.tangents[1].times(f3)))
                    )
                    g.axis.oSet(
                        2,
                        v1.normal.times(f1).oPlus(v2.normal.times(f2).oPlus(v3.normal.times(f3)))
                    )

                    //-----------------------
                    // this is needed so aimed particles can calculate origins at different times
                    g.originalRandom = idRandom(g.random)
                    g.age = g.frac * stage.particleLife

                    // if the particle doesn't get drawn because it is faded out or beyond a kill region,
                    // don't increment the verts
                    tri.numVerts += stage.CreateParticle(g, Arrays.copyOfRange(tri.verts, tri.numVerts, tri.verts.size))
                }
                if (tri.numVerts > 0) {
                    // build the index list
                    var indexes = 0
                    var i = 0
                    while (i < tri.numVerts) {
                        tri.indexes[indexes + 0] = i
                        tri.indexes[indexes + 1] = i + 2
                        tri.indexes[indexes + 2] = i + 3
                        tri.indexes[indexes + 3] = i
                        tri.indexes[indexes + 4] = i + 3
                        tri.indexes[indexes + 5] = i + 1
                        indexes += 6
                        i += 4
                    }
                    tri.numIndexes = indexes
                    tri.ambientCache =
                        VertexCache.vertexCache.AllocFrameTemp(tri.verts, tri.numVerts * idDrawVert.Companion.BYTES)
                    if (tri.ambientCache != null) {
                        // add the drawsurf
                        tr_light.R_AddDrawSurf(tri, surf.space, renderEntity, stage.material, surf.scissorRect)
                    }
                }
            }
        }
    }

    /*
     =================
     R_DeformDrawSurf
     =================
     */
    fun R_DeformDrawSurf(drawSurf: drawSurf_s?) {
        if (null == drawSurf.material) {
            return
        }
        if (RenderSystem_init.r_skipDeforms.GetBool()) {
            return
        }
        when (drawSurf.material.Deform()) {
            deform_t.DFRM_NONE -> return
            deform_t.DFRM_SPRITE -> tr_deform.R_AutospriteDeform(drawSurf)
            deform_t.DFRM_TUBE -> tr_deform.R_TubeDeform(drawSurf)
            deform_t.DFRM_FLARE -> tr_deform.R_FlareDeform(drawSurf)
            deform_t.DFRM_EXPAND -> tr_deform.R_ExpandDeform(drawSurf)
            deform_t.DFRM_MOVE -> tr_deform.R_MoveDeform(drawSurf)
            deform_t.DFRM_TURB -> tr_deform.R_TurbulentDeform(drawSurf)
            deform_t.DFRM_EYEBALL -> tr_deform.R_EyeballDeform(drawSurf)
            deform_t.DFRM_PARTICLE -> tr_deform.R_ParticleDeform(drawSurf, true)
            deform_t.DFRM_PARTICLE2 -> tr_deform.R_ParticleDeform(drawSurf, false)
        }
    }

    //========================================================================================
    internal class eyeIsland_t {
        var bounds: idBounds?
        val mid: idVec3?
        var numTris = 0
        var tris: IntArray? = IntArray(tr_deform.MAX_EYEBALL_TRIS)

        init {
            bounds = idBounds()
            mid = idVec3()
        }
    }
}