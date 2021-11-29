package neo.Tools.Compilers.DMap

import neo.Renderer.Interaction
import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.optimizedShadow_t
import neo.Renderer.tr_stencilshadow
import neo.Renderer.tr_stencilshadow.shadowGen_t
import neo.Renderer.tr_trisurf
import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.shadowOptLevel_t
import neo.framework.Common
import neo.idlib.containers.List.cmp_t
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.util.*

/**
 *
 */
object shadowopt3 {
    //
    const val EDGE_EPSILON = 0.1f

    /*

     given a set of faces that are clipped to the required frustum

     make 2D projection for each vertex

     for each edge
     add edge, generating new points at each edge intersection

     ?add all additional edges to make a full triangulation

     make full triangulation

     for each triangle
     find midpoint
     find original triangle with midpoint closest to view
     annotate triangle with that data
     project all vertexes to that plane
     output the triangle as a front cap

     snap all vertexes
     make a back plane projection for all vertexes

     for each edge
     if one side doesn't have a triangle
     make a sil edge to back plane projection
     continue
     if triangles on both sides have two verts in common
     continue
     make a sil edge from one triangle to the other




     classify triangles on common planes, so they can be optimized

     what about interpenetrating triangles???

     a perfect shadow volume will have every edge exactly matched with
     an opposite, and no two triangles covering the same area on either
     the back projection or a silhouette edge.

     Optimizing the triangles on the projected plane can give a significant
     improvement, but the quadratic time nature of the optimization process
     probably makes it untenable.

     There exists some small room for further triangle count optimizations of the volumes
     by collapsing internal surface geometry in some cases, or allowing original triangles
     to extend outside the exactly light frustum without being clipped, but it probably
     isn't worth it.

     Triangle count optimizations at the expense of a slight fill rate cost
     may be apropriate in some cases.


     Perform the complete clipping on all triangles
     for each vertex
     project onto the apropriate plane and mark plane bit as in use
     for each triangle
     if points project onto different planes, clip
     */
    const val MAX_SHADOW_TRIS = 32768

    //
    const val MAX_SIL_EDGES = shadowopt3.MAX_SHADOW_TRIS * 3
    var silEdges: Array<shadowOptEdge_s?>? = arrayOfNulls<shadowOptEdge_s?>(shadowopt3.MAX_SIL_EDGES)

    //
    const val MAX_SIL_QUADS = shadowopt3.MAX_SHADOW_TRIS * 3
    var silQuads: Array<silQuad_s?>? = arrayOfNulls<silQuad_s?>(shadowopt3.MAX_SIL_QUADS)

    //
    var EDGE_PLANE_EPSILON = 0.1f
    var UNIQUE_EPSILON = 0.1f

    /*
     ===================
     ClipTriangle_r
     ===================
     */
    var c_removedFragments = 0
    var maxRetIndexes = 0
    var maxUniqued = 0
    var numOutputTris = 0
    var numSilEdges = 0

    //
    var numSilPlanes = 0
    var numSilQuads = 0

    //
    // the uniqued verts are still in projection centered space, not global space
    var numUniqued = 0
    var numUniquedBeforeProjection = 0

    //
    var outputTris: Array<shadowTri_t?>? = arrayOfNulls<shadowTri_t?>(shadowopt3.MAX_SHADOW_TRIS)

    //
    var ret: optimizedShadow_t? = null
    var silPlanes: Array<silPlane_t?>?
    var uniqued: Array<idVec3?>?

    /*
     =================
     CreateEdgesForTri
     =================
     */
    fun CreateEdgesForTri(tri: shadowTri_t?) {
        for (j in 0..2) {
            val v1 = tri.v.get(j)
            val v2 = tri.v.get((j + 1) % 3)
            tri.edge.get(j).Cross(v2, v1)
            tri.edge.get(j).Normalize()
        }
    }

    fun TriOutsideTri(a: shadowTri_t?, b: shadowTri_t?): Boolean {
//#if 0
//	if ( a.v[0] * b.edge[0] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[0] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[0] <= EDGE_EPSILON ) {
//			return true;
//		}
//	if ( a.v[0] * b.edge[1] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[1] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[1] <= EDGE_EPSILON ) {
//			return true;
//		}
//	if ( a.v[0] * b.edge[2] <= EDGE_EPSILON
//		&& a.v[1] * b.edge[2] <= EDGE_EPSILON
//		&& a.v[2] * b.edge[2] <= EDGE_EPSILON ) {
//			return true;
//		}
//#else
        for (i in 0..2) {
            var j: Int
            j = 0
            while (j < 3) {
                val d = a.v.get(j).times(b.edge.get(i))
                if (d > shadowopt3.EDGE_EPSILON) {
                    break
                }
                j++
            }
            if (j == 3) {
                return true
            }
        }
        //#endif
        return false
    }

    fun TriBehindTri(a: shadowTri_t?, b: shadowTri_t?): Boolean {
        var d: Float
        d = b.plane.Distance(a.v.get(0))
        if (d > 0) {
            return true
        }
        d = b.plane.Distance(a.v.get(1))
        if (d > 0) {
            return true
        }
        d = b.plane.Distance(a.v.get(2))
        return d > 0
    }

    //static int FindUniqueVert( idVec3 v );
    //=====================================================================================
    fun ClipTriangle_r(tri: shadowTri_t?, startTri: Int, skipTri: Int, numTris: Int, tris: Array<shadowTri_t?>?) {
        // create edge planes for this triangle

        // compare against all the other triangles
        for (i in startTri until numTris) {
            if (i == skipTri) {
                continue
            }
            val other = tris.get(i)
            if (shadowopt3.TriOutsideTri(tri, other)) {
                continue
            }
            if (shadowopt3.TriOutsideTri(other, tri)) {
                continue
            }
            // they overlap to some degree

            // if other is behind tri, it doesn't clip it
            if (!shadowopt3.TriBehindTri(tri, other)) {
                continue
            }

            // clip it
            var w: idWinding? = idWinding(tri.v, 3)
            var j = 0
            while (j < 4 && !w.isNULL()) {
                val front = idWinding()
                val back = idWinding()

                // keep any portion in front of other's plane
                if (j == 0) {
                    w.Split(other.plane, Plane.ON_EPSILON, front, back)
                } else {
                    w.Split(idPlane(other.edge.get(j - 1), 0.0f), Plane.ON_EPSILON, front, back)
                }
                if (!back.isNULL) {
                    // recursively clip these triangles to all subsequent triangles
                    for (k in 2 until back.GetNumPoints()) {
                        tri.v.get(0).oSet(back.oGet(0).ToVec3())
                        tri.v.get(1).oSet(back.oGet(k - 1).ToVec3())
                        tri.v.get(2).oSet(back.oGet(k).ToVec3())
                        shadowopt3.CreateEdgesForTri(tri)
                        shadowopt3.ClipTriangle_r(tri, i + 1, skipTri, numTris, tris)
                    }
                    //				delete back;
                }

//			delete w;
                w = front
                j++
            }
            //		if ( w ) {
//			delete w;
//		}
            shadowopt3.c_removedFragments++
            // any fragments will have been added recursively
            return
        }

        // this fragment is frontmost, so add it to the output list
        if (shadowopt3.numOutputTris == shadowopt3.MAX_SHADOW_TRIS) {
            Common.common.Error("numOutputTris == MAX_SHADOW_TRIS")
        }
        shadowopt3.outputTris[shadowopt3.numOutputTris] = tri
        shadowopt3.numOutputTris++
    }

    /*
     ====================
     ClipOccluders

     Generates outputTris by clipping all the triangles against each other,
     retaining only those closest to the projectionOrigin
     ====================
     */
    fun ClipOccluders(verts: Array<idVec4?>?, indexes: IntArray?, numIndexes: Int, projectionOrigin: idVec3?) {
        val numTris = numIndexes / 3
        var i: Int
        val tris = arrayOfNulls<shadowTri_t?>(numTris)
        var tri: shadowTri_t?
        Common.common.Printf("ClipOccluders: %d triangles\n", numTris)
        i = 0
        while (i < numTris) {
            tri = tris[i]

            // the indexes are in reversed order from tr_stencilshadow
            tri.v.get(0).oSet(verts.get(indexes.get(i * 3 + 2)).ToVec3().oMinus(projectionOrigin))
            tri.v.get(1).oSet(verts.get(indexes.get(i * 3 + 1)).ToVec3().oMinus(projectionOrigin))
            tri.v.get(2).oSet(verts.get(indexes.get(i * 3 + 0)).ToVec3().oMinus(projectionOrigin))
            val d1 = idVec3(tri.v.get(1).oMinus(tri.v.get(0)))
            val d2 = idVec3(tri.v.get(2).oMinus(tri.v.get(0)))
            tri.plane.ToVec4_ToVec3_Cross(d2, d1)
            tri.plane.ToVec4_ToVec3_Normalize()
            tri.plane.oSet(3, tri.v.get(0).times(tri.plane.ToVec4().ToVec3()))

            // get the plane number before any clipping
            // we should avoid polluting the regular dmap planes with these
            // that are offset from the light origin...
            tri.planeNum = FindFloatPlane(tri.plane)
            shadowopt3.CreateEdgesForTri(tri)
            i++
        }

        // clear our output buffer
        shadowopt3.numOutputTris = 0

        // for each triangle, clip against all other triangles
        var numRemoved = 0
        var numComplete = 0
        var numFragmented = 0
        i = 0
        while (i < numTris) {
            val oldOutput = shadowopt3.numOutputTris
            shadowopt3.c_removedFragments = 0
            shadowopt3.ClipTriangle_r(tris[i], 0, i, numTris, tris)
            if (shadowopt3.numOutputTris == oldOutput) {
                numRemoved++ // completely unused
            } else if (shadowopt3.c_removedFragments == 0) {
                // the entire triangle is visible
                numComplete++
                shadowopt3.outputTris[oldOutput] = tris[i]
                val out: shadowTri_t? = shadowopt3.outputTris[oldOutput]
                shadowopt3.numOutputTris = oldOutput + 1
            } else {
                numFragmented++
                // we made at least one fragment

                // if we are at the low optimization level, just use a single
                // triangle if it produced any fragments
                if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_CULL_OCCLUDED) {
                    shadowopt3.outputTris[oldOutput] = tris[i]
                    val out: shadowTri_t? = shadowopt3.outputTris[oldOutput] //TODO:useless
                    shadowopt3.numOutputTris = oldOutput + 1
                }
            }
            i++
        }
        Common.common.Printf("%d triangles completely invisible\n", numRemoved)
        Common.common.Printf("%d triangles completely visible\n", numComplete)
        Common.common.Printf("%d triangles fragmented\n", numFragmented)
        Common.common.Printf("%d shadowing fragments before optimization\n", shadowopt3.numOutputTris)
    }

    /*
     ================
     OptimizeOutputTris
     ================
     */
    fun OptimizeOutputTris() {
        var i: Int

        // optimize the clipped surfaces
        var optGroups: optimizeGroup_s? = null
        var checkGroup: optimizeGroup_s?
        i = 0
        while (i < shadowopt3.numOutputTris) {
            val tri: shadowTri_t? = shadowopt3.outputTris[i]
            val planeNum = tri.planeNum

            // add it to an optimize group
            checkGroup = optGroups
            while (checkGroup != null) {
                if (checkGroup.planeNum == planeNum) {
                    break
                }
                checkGroup = checkGroup.nextGroup
            }
            if (TempDump.NOT(checkGroup)) {
                // create a new optGroup
                checkGroup = optimizeGroup_s() // Mem_ClearedAlloc(sizeof(checkGroup));
                checkGroup.planeNum = planeNum
                checkGroup.nextGroup = optGroups
                optGroups = checkGroup
            }

            // create a mapTri for the optGroup
            val mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
            mtri.v[0].xyz.oSet(tri.v.get(0))
            mtri.v[1].xyz.oSet(tri.v.get(1))
            mtri.v[2].xyz.oSet(tri.v.get(2))
            mtri.next = checkGroup.triList
            checkGroup.triList = mtri
            i++
        }
        optimize.OptimizeGroupList(optGroups)
        shadowopt3.numOutputTris = 0
        checkGroup = optGroups
        while (checkGroup != null) {
            var mtri = checkGroup.triList
            while (mtri != null) {
                val tri: shadowTri_t? = shadowopt3.outputTris[shadowopt3.numOutputTris]
                shadowopt3.numOutputTris++
                tri.v.get(0).oSet(mtri.v[0].xyz)
                tri.v.get(1).oSet(mtri.v[1].xyz)
                tri.v.get(2).oSet(mtri.v[2].xyz)
                mtri = mtri.next
            }
            checkGroup = checkGroup.nextGroup
        }
        map.FreeOptimizeGroupList(optGroups)
    }

    /*
     =====================
     GenerateSilEdges

     Output tris must be tjunction fixed and vertex uniqued
     A edge that is not exactly matched is a silhouette edge
     We could skip this and rely completely on the matched quad removal
     for all sil edges, but this will avoid the bulk of the checks.
     =====================
     */
    fun GenerateSilEdges() {
        var i: Int
        var j: Int

//	unsigned	*edges = (unsigned *)_alloca( (numOutputTris*3+1)*sizeof(*edges) );
        val edges = LongArray(shadowopt3.numOutputTris * 3 + 1)
        var numEdges = 0
        shadowopt3.numSilEdges = 0
        i = 0
        while (i < shadowopt3.numOutputTris) {
            val a = shadowopt3.outputTris[i].index[0]
            val b = shadowopt3.outputTris[i].index[1]
            val c = shadowopt3.outputTris[i].index[2]
            if (a == b || a == c || b == c) {
                i++
                continue  // degenerate
            }
            j = 0
            while (j < 3) {
                var v1: Int
                var v2: Int
                v1 = shadowopt3.outputTris[i].index[j]
                v2 = shadowopt3.outputTris[i].index[(j + 1) % 3]
                if (v1 == v2) {
                    j++
                    continue  // degenerate
                }
                if (v1 > v2) {
                    edges[numEdges] = v1 shl 16 or (v2 shl 1)
                } else {
                    edges[numEdges] = v2 shl 16 or (v1 shl 1) or 1
                }
                numEdges++
                j++
            }
            i++
        }

//        qsort(edges, numEdges, sizeof(edges[0]), EdgeSort);
        Arrays.sort(edges, 0, numEdges) //, new EdgeSort());//TODO:check whether the default sort is enough
        edges[numEdges] = -1 // force the last to make an edge if no matched to previous
        i = 0
        while (i < numEdges) {
            if (edges[i] xor edges[i + 1] == 1L) {
                // skip the next one, because we matched and
                // removed both
                i++
                i++
                continue
            }
            // this is an unmatched edge, so we need to generate a sil plane
            var v1: Int
            var v2: Int
            if (edges[i] and 1 != 0L) {
                v2 = (edges[i] shr 16).toInt()
                v1 = (edges[i] shr 1 and 0x7fff).toInt()
            } else {
                v1 = (edges[i] shr 16).toInt()
                v2 = (edges[i] shr 1 and 0x7fff).toInt()
            }
            if (shadowopt3.numSilEdges == shadowopt3.MAX_SIL_EDGES) {
                Common.common.Error("numSilEdges == MAX_SIL_EDGES")
            }
            shadowopt3.silEdges[shadowopt3.numSilEdges].index[0] = v1
            shadowopt3.silEdges[shadowopt3.numSilEdges].index[1] = v2
            shadowopt3.numSilEdges++
            i++
        }
    }

    /*
     =====================
     GenerateSilPlanes

     Groups the silEdges into common planes
     =====================
     */
    fun GenerateSilPlanes() {
        shadowopt3.numSilPlanes = 0
        shadowopt3.silPlanes = arrayOfNulls<silPlane_t?>(shadowopt3.numSilEdges) // Mem_Alloc(numSilEdges);

        // identify the silPlanes
        shadowopt3.numSilPlanes = 0
        for (i in 0 until shadowopt3.numSilEdges) {
            if (shadowopt3.silEdges[i].index[0] == shadowopt3.silEdges[i].index[1]) {
                continue  // degenerate
            }
            val v1 = shadowopt3.uniqued[shadowopt3.silEdges[i].index[0]]
            val v2 = shadowopt3.uniqued[shadowopt3.silEdges[i].index[1]]

            // search for an existing plane
            var j: Int
            j = 0
            while (j < shadowopt3.numSilPlanes) {
                val d = v1.times(shadowopt3.silPlanes[j].normal)
                val d2 = v2.times(shadowopt3.silPlanes[j].normal)
                if (Math.abs(d) < shadowopt3.EDGE_PLANE_EPSILON
                    && Math.abs(d2) < shadowopt3.EDGE_PLANE_EPSILON
                ) {
                    shadowopt3.silEdges[i].nextEdge = shadowopt3.silPlanes[j].edges
                    shadowopt3.silPlanes[j].edges = shadowopt3.silEdges[i]
                    break
                }
                j++
            }
            if (j == shadowopt3.numSilPlanes) {
                // create a new silPlane
                shadowopt3.silPlanes[j].normal.Cross(v2, v1)
                shadowopt3.silPlanes[j].normal.Normalize()
                shadowopt3.silEdges[i].nextEdge = null
                shadowopt3.silPlanes[j].edges = shadowopt3.silEdges[i]
                shadowopt3.silPlanes[j].fragmentedQuads = null
                shadowopt3.numSilPlanes++
            }
        }
    }

    /*
     =============
     SaveQuad
     =============
     */
    fun SaveQuad(silPlane: silPlane_t?, quad: silQuad_s?) {
        // this fragment is a final fragment
        if (shadowopt3.numSilQuads == shadowopt3.MAX_SIL_QUADS) {
            Common.common.Error("numSilQuads == MAX_SIL_QUADS")
        }
        shadowopt3.silQuads[shadowopt3.numSilQuads] = quad
        shadowopt3.silQuads[shadowopt3.numSilQuads].nextQuad = silPlane.fragmentedQuads
        silPlane.fragmentedQuads = shadowopt3.silQuads[shadowopt3.numSilQuads]
        shadowopt3.numSilQuads++
    }

    //=====================================================================================
    /*
     ===================
     FragmentSilQuad

     Clip quads, or reconstruct?
     Generate them T-junction free, or require another pass of fix-tjunc?
     Call optimizer on a per-sil-plane basis?
     will this ever introduce tjunctions with the front faces?
     removal of planes can allow the rear projection to be farther optimized

     For quad clipping
     PlaneThroughEdge

     quad clipping introduces new vertexes

     Cannot just fragment edges, must emit full indexes

     what is the bounds on max indexes?
     the worst case is that all edges but one carve an existing edge in the middle,
     giving twice the input number of indexes (I think)

     can we avoid knowing about projected positions and still optimize?

     Fragment all edges first
     Introduces T-junctions
     create additional silEdges, linked to silPlanes

     In theory, we should never have more than one edge clipping a given
     fragment, but it is more robust if we check them all
     ===================
     */
    fun FragmentSilQuad(
        quad: silQuad_s?, silPlane: silPlane_t?,
        startEdge: shadowOptEdge_s?, skipEdge: shadowOptEdge_s?
    ) {
        if (quad.nearV.get(0) == quad.nearV.get(1)) {
            return
        }
        var check = startEdge
        while (check != null) {
            if (check == skipEdge) {
                // don't clip against self
                check = check.nextEdge
                continue
            }
            if (check.index.get(0) == check.index.get(1)) {
                check = check.nextEdge
                continue
            }

            // make planes through both points of check
            for (i in 0..1) {
                val plane = idVec3()
                plane.Cross(shadowopt3.uniqued[check.index.get(i)], silPlane.normal)
                plane.Normalize()
                if (plane.Length() < 0.9) {
                    continue
                }

                // if the other point on check isn't on the negative side of the plane,
                // flip the plane
                if (shadowopt3.uniqued[check.index.get( /*!i*/1 xor i)].times(plane) > 0) {
                    plane.oSet(plane.oNegative())
                }
                val d1 = shadowopt3.uniqued[quad.nearV.get(0)].times(plane)
                val d2 = shadowopt3.uniqued[quad.nearV.get(1)].times(plane)
                val d3 = shadowopt3.uniqued[quad.farV.get(0)].times(plane)
                val d4 = shadowopt3.uniqued[quad.farV.get(1)].times(plane)

                // it is better to conservatively NOT split the quad, which, at worst,
                // will leave some extra overdraw
                // if the plane divides the incoming edge, split it and recurse
                // with the outside fraction before continuing with the inside fraction
                if (d1 > shadowopt3.EDGE_PLANE_EPSILON && d3 > shadowopt3.EDGE_PLANE_EPSILON && d2 < -shadowopt3.EDGE_PLANE_EPSILON && d4 < -shadowopt3.EDGE_PLANE_EPSILON
                    || d2 > shadowopt3.EDGE_PLANE_EPSILON && d4 > shadowopt3.EDGE_PLANE_EPSILON && d1 < -shadowopt3.EDGE_PLANE_EPSILON && d3 < -shadowopt3.EDGE_PLANE_EPSILON
                ) {
                    var f = d1 / (d1 - d2)
                    val f2 = d3 / (d3 - d4)
                    f = f2
                    if (f <= 0.0001 || f >= 0.9999) {
                        Common.common.Error("Bad silQuad fraction")
                    }

                    // finding uniques may be causing problems here
                    val nearMid = idVec3(
                        shadowopt3.uniqued[quad.nearV.get(0)].times(1 - f)
                            .oPlus(shadowopt3.uniqued[quad.nearV.get(1)].times(f))
                    )
                    val nearMidIndex = shadowopt3.FindUniqueVert(nearMid)
                    val farMid = idVec3(
                        shadowopt3.uniqued[quad.farV.get(0)].times(1 - f)
                            .oPlus(shadowopt3.uniqued[quad.farV.get(1)].times(f))
                    )
                    val farMidIndex = shadowopt3.FindUniqueVert(farMid)
                    if (d1 > shadowopt3.EDGE_PLANE_EPSILON) {
                        quad.nearV.get(1) = nearMidIndex
                        quad.farV.get(1) = farMidIndex
                        shadowopt3.FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
                        quad.nearV.get(0) = nearMidIndex
                        quad.farV.get(0) = farMidIndex
                    } else {
                        quad.nearV.get(0) = nearMidIndex
                        quad.farV.get(0) = farMidIndex
                        shadowopt3.FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
                        quad.nearV.get(1) = nearMidIndex
                        quad.farV.get(1) = farMidIndex
                    }
                }
            }

            // make a plane through the line of check
            val separate = idPlane()
            val dir = idVec3(shadowopt3.uniqued[check.index.get(1)].oMinus(shadowopt3.uniqued[check.index.get(0)]))
            separate.Normal().Cross(dir, silPlane.normal)
            separate.Normal().Normalize()
            separate.oSet(3, -shadowopt3.uniqued[check.index.get(1)].times(separate.Normal()))

            // this may miss a needed separation when the quad would be
            // clipped into a triangle and a quad
            var d1 = separate.Distance(shadowopt3.uniqued[quad.nearV.get(0)])
            var d2 = separate.Distance(shadowopt3.uniqued[quad.farV.get(0)])
            if (d1 < shadowopt3.EDGE_PLANE_EPSILON && d2 < shadowopt3.EDGE_PLANE_EPSILON
                || d1 > -shadowopt3.EDGE_PLANE_EPSILON && d2 > -shadowopt3.EDGE_PLANE_EPSILON
            ) {
                check = check.nextEdge
                continue
            }

            // split the quad at this plane
            var f = d1 / (d1 - d2)
            val mid0 = idVec3(
                shadowopt3.uniqued[quad.nearV.get(0)].times(1 - f)
                    .oPlus(shadowopt3.uniqued[quad.farV.get(0)].times(f))
            )
            val mid0Index = shadowopt3.FindUniqueVert(mid0)
            d1 = separate.Distance(shadowopt3.uniqued[quad.nearV.get(1)])
            d2 = separate.Distance(shadowopt3.uniqued[quad.farV.get(1)])
            f = d1 / (d1 - d2)
            if (f < 0 || f > 1) {
                check = check.nextEdge
                continue
            }
            val mid1 = idVec3(
                shadowopt3.uniqued[quad.nearV.get(1)].times(1 - f)
                    .oPlus(shadowopt3.uniqued[quad.farV.get(1)].times(f))
            )
            val mid1Index = shadowopt3.FindUniqueVert(mid1)
            quad.nearV.get(0) = mid0Index
            quad.nearV.get(1) = mid1Index
            shadowopt3.FragmentSilQuad(quad, silPlane, check.nextEdge, skipEdge)
            quad.farV.get(0) = mid0Index
            quad.farV.get(1) = mid1Index
            check = check.nextEdge
        }
        shadowopt3.SaveQuad(silPlane, quad)
    }

    /*
     ===============
     FragmentSilQuads
     ===============
     */
    fun FragmentSilQuads() {
        // group the edges into common planes
        shadowopt3.GenerateSilPlanes()
        shadowopt3.numSilQuads = 0

        // fragment overlapping edges
        for (i in 0 until shadowopt3.numSilPlanes) {
            val sil: silPlane_t? = shadowopt3.silPlanes[i]
            var e1 = sil.edges
            while (e1 != null) {
                val quad = silQuad_s()
                quad.nearV.get(0) = e1.index.get(0)
                quad.nearV.get(1) = e1.index.get(1)
                if (e1.index.get(0) == e1.index.get(1)) {
                    Common.common.Error("FragmentSilQuads: degenerate edge")
                }
                quad.farV.get(0) = e1.index.get(0) + shadowopt3.numUniquedBeforeProjection
                quad.farV.get(1) = e1.index.get(1) + shadowopt3.numUniquedBeforeProjection
                shadowopt3.FragmentSilQuad(quad, sil, sil.edges, e1)
                e1 = e1.nextEdge
            }
        }
    }

    /*
     =====================
     EmitFragmentedSilQuads

     =====================
     */
    fun EmitFragmentedSilQuads() {
        var i: Int
        var j: Int
        var k: Int
        var mtri: mapTri_s?
        i = 0
        while (i < shadowopt3.numSilPlanes) {
            val sil: silPlane_t? = shadowopt3.silPlanes[i]

            // prepare for optimizing the sil quads on each side of the sil plane
            val groups = arrayOfNulls<optimizeGroup_s?>(2)
            //		memset( &groups, 0, sizeof( groups ) );
            val planes: Array<idPlane?> = idPlane.Companion.generateArray(2)
            planes[0].SetNormal(sil.normal) //TODO:reinterpret cast
            planes[0].oSet(3, 0f)
            planes[1].oSet(planes[0].oNegative())
            groups[0].planeNum = FindFloatPlane(planes[0])
            groups[1].planeNum = FindFloatPlane(planes[1])

            // emit the quads that aren't matched
            var f1 = sil.fragmentedQuads
            while (f1 != null) {
                var f2: silQuad_s?
                f2 = sil.fragmentedQuads
                while (f2 != null) {
                    if (f2 == f1) {
                        f2 = f2.nextQuad
                        continue
                    }
                    // in theory, this is sufficient, but we might
                    // have some cases of tripple+ matching, or unclipped rear projections
                    if (f1.nearV.get(0) == f2.nearV.get(1) && f1.nearV.get(1) == f2.nearV.get(0)) {
                        break
                    }
                    f2 = f2.nextQuad
                }
                // if we went through all the quads without finding a match, emit the quad
                if (TempDump.NOT(f2)) {
                    var gr: optimizeGroup_s?
                    val v1 = idVec3()
                    val v2 = idVec3()
                    val normal = idVec3()
                    mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
                    mtri.v[0].xyz.oSet(shadowopt3.uniqued[f1.nearV.get(0)])
                    mtri.v[1].xyz.oSet(shadowopt3.uniqued[f1.nearV.get(1)])
                    mtri.v[2].xyz.oSet(shadowopt3.uniqued[f1.farV.get(1)])
                    v1.oSet(mtri.v[1].xyz.oMinus(mtri.v[0].xyz))
                    v2.oSet(mtri.v[2].xyz.oMinus(mtri.v[0].xyz))
                    normal.Cross(v2, v1)
                    gr = if (normal.times(planes[0].Normal()) > 0) {
                        groups[0]
                    } else {
                        groups[1]
                    }
                    mtri.next = gr.triList
                    gr.triList = mtri
                    mtri = mapTri_s() // Mem_ClearedAlloc(sizeof(mtri));
                    mtri.v[0].xyz.oSet(shadowopt3.uniqued[f1.farV.get(0)])
                    mtri.v[1].xyz.oSet(shadowopt3.uniqued[f1.nearV.get(0)])
                    mtri.v[2].xyz.oSet(shadowopt3.uniqued[f1.farV.get(1)])
                    mtri.next = gr.triList
                    gr.triList = mtri

//#if 0
//				// emit a sil quad all the way to the projection plane
//				int index = ret.totalIndexes;
//				if ( index + 6 > maxRetIndexes ) {
//					common.Error( "maxRetIndexes exceeded" );
//				}
//				ret.indexes[index+0] = f1.nearV[0];
//				ret.indexes[index+1] = f1.nearV[1];
//				ret.indexes[index+2] = f1.farV[1];
//				ret.indexes[index+3] = f1.farV[0];
//				ret.indexes[index+4] = f1.nearV[0];
//				ret.indexes[index+5] = f1.farV[1];
//				ret.totalIndexes += 6;
//#endif
                }
                f1 = f1.nextQuad
            }

            // optimize
            j = 0
            while (j < 2) {
                if (TempDump.NOT(groups[j].triList)) {
                    j++
                    continue
                }
                if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_SIL_OPTIMIZE) {
                    optimize.OptimizeGroupList(groups[j])
                }
                // add as indexes
                mtri = groups[j].triList
                while (mtri != null) {
                    k = 0
                    while (k < 3) {
                        if (shadowopt3.ret.totalIndexes == shadowopt3.maxRetIndexes) {
                            Common.common.Error("maxRetIndexes exceeded")
                        }
                        shadowopt3.ret.indexes[shadowopt3.ret.totalIndexes] = shadowopt3.FindUniqueVert(mtri.v[k].xyz)
                        shadowopt3.ret.totalIndexes++
                        k++
                    }
                    mtri = mtri.next
                }
                tritools.FreeTriList(groups[j].triList)
                j++
            }
            i++
        }

        // we don't need the silPlane grouping anymore
        shadowopt3.silPlanes = null //Mem_Free(silPlanes);
    }

    //==================================================================================
    /*
     =================
     EmitUnoptimizedSilEdges
     =================
     */
    fun EmitUnoptimizedSilEdges() {
        var i: Int
        i = 0
        while (i < shadowopt3.numSilEdges) {
            val v1 = shadowopt3.silEdges[i].index[0]
            val v2 = shadowopt3.silEdges[i].index[1]
            val index = shadowopt3.ret.totalIndexes
            shadowopt3.ret.indexes[index + 0] = v1
            shadowopt3.ret.indexes[index + 1] = v2
            shadowopt3.ret.indexes[index + 2] = v2 + shadowopt3.numUniquedBeforeProjection
            shadowopt3.ret.indexes[index + 3] = v1 + shadowopt3.numUniquedBeforeProjection
            shadowopt3.ret.indexes[index + 4] = v1
            shadowopt3.ret.indexes[index + 5] = v2 + shadowopt3.numUniquedBeforeProjection
            shadowopt3.ret.totalIndexes += 6
            i++
        }
    }

    //==================================================================================
    /*
     ================
     FindUniqueVert
     ================
     */
    fun FindUniqueVert(v: idVec3?): Int {
        var k: Int
        k = 0
        while (k < shadowopt3.numUniqued) {
            val check = shadowopt3.uniqued[k]
            if (Math.abs(v.oGet(0) - check.oGet(0)) < shadowopt3.UNIQUE_EPSILON && Math.abs(v.oGet(1) - check.oGet(1)) < shadowopt3.UNIQUE_EPSILON && Math.abs(
                    v.oGet(2) - check.oGet(2)
                ) < shadowopt3.UNIQUE_EPSILON
            ) {
                return k
            }
            k++
        }
        if (shadowopt3.numUniqued == shadowopt3.maxUniqued) {
            Common.common.Error("FindUniqueVert: numUniqued == maxUniqued")
        }
        shadowopt3.uniqued[shadowopt3.numUniqued] = v
        shadowopt3.numUniqued++
        return k
    }

    /*
     ===================
     UniqueVerts

     Snaps all triangle verts together, setting tri.index[]
     and generating numUniqued and uniqued.
     These are still in projection-centered space, not global space
     ===================
     */
    fun UniqueVerts() {
        var i: Int
        var j: Int

        // we may add to uniqued later when splitting sil edges, so leave
        // some extra room
        shadowopt3.maxUniqued = 100000 // numOutputTris * 10 + 1000;
        shadowopt3.uniqued = idVec3.Companion.generateArray(shadowopt3.maxUniqued) // Mem_Alloc(maxUniqued);
        shadowopt3.numUniqued = 0
        i = 0
        while (i < shadowopt3.numOutputTris) {
            j = 0
            while (j < 3) {
                shadowopt3.outputTris[i].index[j] = shadowopt3.FindUniqueVert(shadowopt3.outputTris[i].v[j])
                j++
            }
            i++
        }
    }

    /*
     ======================
     ProjectUniqued
     ======================
     */
    fun ProjectUniqued(projectionOrigin: idVec3?, projectionPlane: idPlane?) {
        // calculate the projection
        val mat = arrayOfNulls<idVec4?>(4)
        tr_stencilshadow.R_LightProjectionMatrix(projectionOrigin, projectionPlane, mat)
        if (shadowopt3.numUniqued * 2 > shadowopt3.maxUniqued) {
            Common.common.Error("ProjectUniqued: numUniqued * 2 > maxUniqued")
        }

        // this is goofy going back and forth between the spaces,
        // but I don't want to change R_LightProjectionMatrix righ tnow...
        for (i in 0 until shadowopt3.numUniqued) {
            // put the vert back in global space, instead of light centered space
            val `in` = idVec3(shadowopt3.uniqued[i].oPlus(projectionOrigin))

            // project to far plane
            var w: Float
            var oow: Float
            val out = idVec3()
            w = `in`.oMultiply(mat[3].ToVec3()) + mat[3].oGet(3)
            oow = 1.0f / w
            out.x = (`in`.oMultiply(mat[0].ToVec3()) + mat[0].oGet(3)) * oow
            out.y = (`in`.oMultiply(mat[1].ToVec3()) + mat[1].oGet(3)) * oow
            out.z = (`in`.oMultiply(mat[2].ToVec3()) + mat[2].oGet(3)) * oow
            shadowopt3.uniqued[shadowopt3.numUniqued + i] = out.oMinus(projectionOrigin)
        }
        shadowopt3.numUniqued *= 2
    }

    //=======================================================================
    /*
     ====================
     SuperOptimizeOccluders

     This is the callback from the renderer shadow generation routine, after
     verts have been culled against individual frustums of point lights

     ====================
     */
    fun SuperOptimizeOccluders(
        verts: Array<idVec4?>?,
        indexes: IntArray?,
        numIndexes: Int,
        projectionPlane: idPlane?,
        projectionOrigin: idVec3?
    ): optimizedShadow_t? {
//	memset( &ret, 0, sizeof( ret ) );
        shadowopt3.ret = optimizedShadow_t()

        // generate outputTris, removing fragments that are occluded by closer fragments
        shadowopt3.ClipOccluders(verts, indexes, numIndexes, projectionOrigin)
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CULL_OCCLUDED)) {
            shadowopt3.OptimizeOutputTris()
        }

        // match up common verts
        shadowopt3.UniqueVerts()

        // now that we have uniqued the vertexes, we can find unmatched
        // edges, which are silhouette planes
        shadowopt3.GenerateSilEdges()

        // generate the projected verts
        shadowopt3.numUniquedBeforeProjection = shadowopt3.numUniqued
        shadowopt3.ProjectUniqued(projectionOrigin, projectionPlane)

        // fragment the sil edges where the overlap,
        // possibly generating some additional unique verts
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            shadowopt3.FragmentSilQuads()
        }

        // indexes for face and projection caps
        shadowopt3.ret.numFrontCapIndexes = shadowopt3.numOutputTris * 3
        shadowopt3.ret.numRearCapIndexes = shadowopt3.numOutputTris * 3
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            shadowopt3.ret.numSilPlaneIndexes = shadowopt3.numSilQuads * 12 // this is the worst case with clipping
        } else {
            shadowopt3.ret.numSilPlaneIndexes = shadowopt3.numSilEdges * 6 // this is the worst case with clipping
        }
        shadowopt3.ret.totalIndexes = 0
        shadowopt3.maxRetIndexes =
            shadowopt3.ret.numFrontCapIndexes + shadowopt3.ret.numRearCapIndexes + shadowopt3.ret.numSilPlaneIndexes
        shadowopt3.ret.indexes = IntArray(shadowopt3.maxRetIndexes) // Mem_Alloc(maxRetIndexes);
        for (i in 0 until shadowopt3.numOutputTris) {
            // flip the indexes so the surface triangle faces outside the shadow volume
            shadowopt3.ret.indexes[i * 3 + 0] = shadowopt3.outputTris[i].index[2]
            shadowopt3.ret.indexes[i * 3 + 1] = shadowopt3.outputTris[i].index[1]
            shadowopt3.ret.indexes[i * 3 + 2] = shadowopt3.outputTris[i].index[0]
            shadowopt3.ret.indexes[(shadowopt3.numOutputTris + i) * 3 + 0] =
                shadowopt3.numUniquedBeforeProjection + shadowopt3.outputTris[i].index[0]
            shadowopt3.ret.indexes[(shadowopt3.numOutputTris + i) * 3 + 1] =
                shadowopt3.numUniquedBeforeProjection + shadowopt3.outputTris[i].index[1]
            shadowopt3.ret.indexes[(shadowopt3.numOutputTris + i) * 3 + 2] =
                shadowopt3.numUniquedBeforeProjection + shadowopt3.outputTris[i].index[2]
        }
        // emit the sil planes
        shadowopt3.ret.totalIndexes = shadowopt3.ret.numFrontCapIndexes + shadowopt3.ret.numRearCapIndexes
        if (TempDump.etoi(dmap.dmapGlobals.shadowOptLevel) >= TempDump.etoi(shadowOptLevel_t.SO_CLIP_SILS)) {
            // re-optimize the sil planes, cutting
            shadowopt3.EmitFragmentedSilQuads()
        } else {
            // indexes for silhouette edges
            shadowopt3.EmitUnoptimizedSilEdges()
        }

        // we have all the verts now
        // create twice the uniqued verts
        shadowopt3.ret.numVerts = shadowopt3.numUniqued
        shadowopt3.ret.verts = idVec3.Companion.generateArray(shadowopt3.ret.numVerts) // Mem_Alloc(ret.numVerts);
        for (i in 0 until shadowopt3.numUniqued) {
            // put the vert back in global space, instead of light centered space
            shadowopt3.ret.verts[i].oSet(shadowopt3.uniqued[i].oPlus(projectionOrigin))
        }

        // set the final index count
        shadowopt3.ret.numSilPlaneIndexes =
            shadowopt3.ret.totalIndexes - (shadowopt3.ret.numFrontCapIndexes + shadowopt3.ret.numRearCapIndexes)

        // free out local data
        shadowopt3.uniqued = null //Mem_Free(uniqued);
        return shadowopt3.ret
    }

    /*
     =================
     RemoveDegenerateTriangles
     =================
     */
    fun RemoveDegenerateTriangles(tri: srfTriangles_s?) {
        var c_removed: Int
        var i: Int
        var a: Int
        var b: Int
        var c: Int

        // check for completely degenerate triangles
        c_removed = 0
        i = 0
        while (i < tri.numIndexes) {
            a = tri.indexes[i]
            b = tri.indexes[i + 1]
            c = tri.indexes[i + 2]
            if (a == b || a == c || b == c) {
                c_removed++
                //			memmove( tri.indexes + i, tri.indexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.indexes[0] ) );
                System.arraycopy(tri.indexes, i + 3, tri.indexes, i, tri.numIndexes - i - 3)
                tri.numIndexes -= 3
                if (i < tri.numShadowIndexesNoCaps) {
                    tri.numShadowIndexesNoCaps -= 3
                }
                if (i < tri.numShadowIndexesNoFrontCaps) {
                    tri.numShadowIndexesNoFrontCaps -= 3
                }
                i -= 3
            }
            i += 3
        }

        // this doesn't free the memory used by the unused verts
        if (c_removed != 0) {
            Common.common.Printf("removed %d degenerate triangles from shadow\n", c_removed)
        }
    }

    //==================================================================================
    /*
     ====================
     CleanupOptimizedShadowTris

     Uniques all verts across the frustums
     removes matched sil quads at frustum seams
     removes degenerate tris
     ====================
     */
    fun CleanupOptimizedShadowTris(tri: srfTriangles_s?) {
        var i: Int

        // unique all the verts
        shadowopt3.maxUniqued = tri.numVerts
        shadowopt3.uniqued = idVec3.Companion.generateArray(shadowopt3.maxUniqued) //new idVec3[maxUniqued];
        shadowopt3.numUniqued = 0
        val remap = IntArray(tri.numVerts)
        i = 0
        while (i < tri.numIndexes) {
            if (tri.indexes[i] > tri.numVerts || tri.indexes[i] < 0) {
                Common.common.Error("CleanupOptimizedShadowTris: index out of range")
            }
            i++
        }
        i = 0
        while (i < tri.numVerts) {
            remap[i] = shadowopt3.FindUniqueVert(tri.shadowVertexes[i].xyz.ToVec3())
            i++
        }
        tri.numVerts = shadowopt3.numUniqued
        i = 0
        while (i < tri.numVerts) {
            tri.shadowVertexes[i].xyz.oSet(shadowopt3.uniqued[i])
            tri.shadowVertexes[i].xyz.oSet(3, 1f)
            i++
        }
        i = 0
        while (i < tri.numIndexes) {
            tri.indexes[i] = remap[tri.indexes[i]]
            i++
        }

        // remove matched quads
        var numSilIndexes = tri.numShadowIndexesNoCaps
        var i2 = 0
        while (i2 < numSilIndexes) {
            var j: Int
            j = i2 + 6
            while (j < numSilIndexes) {

                // if there is a reversed quad match, we can throw both of them out
                // this is not a robust check, it relies on the exact ordering of
                // quad indexes
                if (tri.indexes[i2 + 0] == tri.indexes[j + 1] && tri.indexes[i2 + 1] == tri.indexes[j + 0] && tri.indexes[i2 + 2] == tri.indexes[j + 3] && tri.indexes[i2 + 3] == tri.indexes[j + 5] && tri.indexes[i2 + 4] == tri.indexes[j + 1] && tri.indexes[i2 + 5] == tri.indexes[j + 3]) {
                    break
                }
                j += 6
            }
            if (j == numSilIndexes) {
                i2 += 6
                continue
            }
            var k: Int
            // remove first quad
            k = i2 + 6
            while (k < j) {
                tri.indexes[k - 6] = tri.indexes[k]
                k++
            }
            // remove second quad
            k = j + 6
            while (k < tri.numIndexes) {
                tri.indexes[k - 12] = tri.indexes[k]
                k++
            }
            numSilIndexes -= 12
            i2 -= 6
            i2 += 6
        }
        val removed = tri.numShadowIndexesNoCaps - numSilIndexes
        tri.numIndexes -= removed
        tri.numShadowIndexesNoCaps -= removed
        tri.numShadowIndexesNoFrontCaps -= removed

        // remove degenerates after we have removed quads, so the double
        // triangle pairing isn't disturbed
        shadowopt3.RemoveDegenerateTriangles(tri)
    }

    /*
     ========================
     CreateLightShadow

     This is called from dmap in util/surface.cpp
     shadowerGroups should be exactly clipped to the light frustum before calling.
     shadowerGroups is optimized by this function, but the contents can be freed, because the returned
     lightShadow_t list is a further culling and optimization of the data.
     ========================
     */
    fun CreateLightShadow(shadowerGroups: optimizeGroup_s?, light: mapLight_t?): srfTriangles_s? {
        Common.common.Printf("----- CreateLightShadow %p -----\n", light)

        // optimize all the groups
        optimize.OptimizeGroupList(shadowerGroups)

        // combine all the triangles into one list
        var combined: mapTri_s?
        combined = null
        var group = shadowerGroups
        while (group != null) {
            combined = tritools.MergeTriLists(combined, tritools.CopyTriList(group.triList))
            group = group.nextGroup
        }
        if (TempDump.NOT(combined)) {
            return null
        }

        // find uniqued vertexes
        val occluders = output.ShareMapTriVerts(combined)
        tritools.FreeTriList(combined)

        // find silhouette information for the triSurf
        tr_trisurf.R_CleanupTriangles(occluders, false, true, false)

        // let the renderer build the shadow volume normally
        val space = idRenderEntityLocal()
        space.modelMatrix[0] = 1
        space.modelMatrix[5] = 1
        space.modelMatrix[10] = 1
        space.modelMatrix[15] = 1
        val cullInfo = srfCullInfo_t()
        //	memset( &cullInfo, 0, sizeof( cullInfo ) );

        // call the normal shadow creation, but with the superOptimize flag set, which will
        // call back to SuperOptimizeOccluders after clipping the triangles to each frustum
        val shadowTris: srfTriangles_s?
        shadowTris = if (dmap.dmapGlobals.shadowOptLevel == shadowOptLevel_t.SO_MERGE_SURFACES) {
            tr_stencilshadow.R_CreateShadowVolume(space, occluders, light.def, shadowGen_t.SG_STATIC, cullInfo)
        } else {
            tr_stencilshadow.R_CreateShadowVolume(space, occluders, light.def, shadowGen_t.SG_OFFLINE, cullInfo)
        }
        tr_trisurf.R_FreeStaticTriSurf(occluders)
        Interaction.R_FreeInteractionCullInfo(cullInfo)
        if (shadowTris != null) {
            dmap.dmapGlobals.totalShadowTriangles += shadowTris.numIndexes / 3
            dmap.dmapGlobals.totalShadowVerts += shadowTris.numVerts / 3
        }
        return shadowTris
    }

    internal class shadowTri_t {
        var edge: Array<idVec3?>? = idVec3.Companion.generateArray(3) // positive side is inside the triangle
        var index: IntArray? = IntArray(3)
        val plane: idPlane? = idPlane() // positive side is forward for the triangle, which is away from the light
        var planeNum // from original triangle, not calculated from the clipped verts
                = 0
        var v: Array<idVec3?>? = idVec3.Companion.generateArray(3)
    }

    internal class shadowOptEdge_s {
        var index: IntArray? = IntArray(2)
        var nextEdge: shadowOptEdge_s? = null
    }

    internal class silQuad_s {
        var farV: IntArray? = IntArray(2) // will always be a projection of near[]
        var nearV: IntArray? = IntArray(2)
        var nextQuad: silQuad_s? = null
    }

    internal class silPlane_t {
        var edges: shadowOptEdge_s? = null
        var fragmentedQuads: silQuad_s? = null
        val normal: idVec3? = idVec3() // all sil planes go through the projection origin
    }

    //==================================================================================
    @Deprecated("")
    internal class EdgeSort : cmp_t<Long?> {
        override fun compare(a: Long?, b: Long?): Int {
//	if ( *(unsigned *)a < *(unsigned *)b ) {
//		return -1;
//	}
//	if ( *(unsigned *)a > *(unsigned *)b ) {
//		return 1;
//	}
            if (a < b) {
                return -1
            }
            return if (a > b) {
                1
            } else 0
        }
    }
}