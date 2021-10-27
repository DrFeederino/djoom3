package neo.Renderer;

import neo.Renderer.Interaction.srfCullInfo_t;
import neo.Renderer.Model.silEdge_t;
import neo.Renderer.Model.srfTriangles_s;
import neo.Renderer.tr_local.idRenderEntityLocal;
import neo.Renderer.tr_local.idRenderLightLocal;
import neo.Renderer.tr_local.optimizedShadow_t;
import neo.Renderer.tr_local.shadowFrustum_t;
import neo.idlib.math.Vector.idVec3;
import neo.idlib.math.Vector.idVec4;

import java.util.Arrays;
import java.util.stream.Stream;

import static neo.Renderer.Interaction.R_CalcInteractionFacing;
import static neo.Renderer.tr_local.tr;
import static neo.Renderer.tr_main.R_GlobalPlaneToLocal;
import static neo.Renderer.tr_main.R_GlobalPointToLocal;
import static neo.Renderer.tr_stencilshadow.shadowGen_t.SG_DYNAMIC;
import static neo.Renderer.tr_stencilshadow.shadowGen_t.SG_OFFLINE;
import static neo.Renderer.tr_trisurf.*;
import static neo.Renderer.tr_turboshadow.R_CreateTurboShadowVolume;
import static neo.Renderer.tr_turboshadow.R_CreateVertexProgramTurboShadowVolume;
import static neo.Tools.Compilers.DMap.shadowopt3.CleanupOptimizedShadowTris;
import static neo.Tools.Compilers.DMap.shadowopt3.SuperOptimizeOccluders;
import static neo.framework.Common.common;
import static neo.idlib.math.Plane.*;
import static neo.idlib.math.Simd.SIMDProcessor;

/**
 *
 */
public class tr_stencilshadow {

    //#define	LIGHT_CLIP_EPSILON	0.001f
    static final float LIGHT_CLIP_EPSILON = 0.1f;

    // tr_stencilShadow.c -- creator of stencil shadow volumes
    static final int MAX_CLIPPED_POINTS = 20;
    //
    static final int MAX_CLIP_SIL_EDGES = 2048;
    //
    static final int MAX_SHADOW_INDEXES = 0x18000;
    static final int MAX_SHADOW_VERTS = 0x18000;
    static final int[][] clipSilEdges = new int[MAX_CLIP_SIL_EDGES][2];
    //
    static final idPlane[][] pointLightFrustums/*[6][6]*/ = {
            {
                    new idPlane(1, 0, 0, 0),
                    new idPlane(1, 1, 0, 0),
                    new idPlane(1, -1, 0, 0),
                    new idPlane(1, 0, 1, 0),
                    new idPlane(1, 0, -1, 0),
                    new idPlane(-1, 0, 0, 0)
            },
            {
                    new idPlane(-1, 0, 0, 0),
                    new idPlane(-1, 1, 0, 0),
                    new idPlane(-1, -1, 0, 0),
                    new idPlane(-1, 0, 1, 0),
                    new idPlane(-1, 0, -1, 0),
                    new idPlane(1, 0, 0, 0)
            },
            {
                    new idPlane(0, 1, 0, 0),
                    new idPlane(0, 1, 1, 0),
                    new idPlane(0, 1, -1, 0),
                    new idPlane(1, 1, 0, 0),
                    new idPlane(-1, 1, 0, 0),
                    new idPlane(0, -1, 0, 0)
            },
            {
                    new idPlane(0, -1, 0, 0),
                    new idPlane(0, -1, 1, 0),
                    new idPlane(0, -1, -1, 0),
                    new idPlane(1, -1, 0, 0),
                    new idPlane(-1, -1, 0, 0),
                    new idPlane(0, 1, 0, 0)
            },
            {
                    new idPlane(0, 0, 1, 0),
                    new idPlane(1, 0, 1, 0),
                    new idPlane(-1, 0, 1, 0),
                    new idPlane(0, 1, 1, 0),
                    new idPlane(0, -1, 1, 0),
                    new idPlane(0, 0, -1, 0)
            },
            {
                    new idPlane(0, 0, -1, 0),
                    new idPlane(1, 0, -1, 0),
                    new idPlane(-1, 0, -1, 0),
                    new idPlane(0, 1, -1, 0),
                    new idPlane(0, -1, -1, 0),
                    new idPlane(0, 0, 1, 0)
            }
    };
    static final idVec4[] shadowVerts = Stream.generate(idVec4::new).limit(MAX_SHADOW_VERTS).toArray(idVec4[]::new);
    /*
     ===================
     R_MakeShadowFrustums

     Called at definition derivation time
     ===================
     */
    private static final int[][] faceCorners/*[6][4]*/ = {
            {7, 5, 1, 3}, // positive X side
            {4, 6, 2, 0}, // negative X side
            {6, 7, 3, 2}, // positive Y side
            {5, 4, 0, 1}, // negative Y side
            {6, 4, 5, 7}, // positive Z side
            {3, 1, 0, 2} // negative Z side
    };
    private static final int[][] faceEdgeAdjacent/*[6][4]*/ = {
            {4, 4, 2, 2}, // positive X side
            {7, 7, 1, 1}, // negative X side
            {5, 5, 0, 0}, // positive Y side
            {6, 6, 3, 3}, // negative Y side
            {0, 0, 3, 3}, // positive Z side
            {5, 5, 6, 6} // negative Z side
    };
    //
    static int c_caps, c_sils;
    //
    static boolean callOptimizer;            // call the preprocessor optimizer after clipping occluders
    //
// faceCastsShadow will be 1 if the face is in the projection
// and facing the apropriate direction
    static byte[] faceCastsShadow;
    //
// facing will be 0 if forward facing, 1 if backwards facing
// grabbed with alloca
    static byte[] globalFacing;
    static int indexFrustumNumber;        // which shadow generating side of a light the indexRef is for
    static indexRef_t[] indexRef = Stream.generate(indexRef_t::new).limit(6).toArray(indexRef_t[]::new);
    static int numClipSilEdges;
    static int numShadowIndexes;
    static int numShadowVerts;
    static boolean overflowed;
    //
    static int[] remap;
    static int/*glIndex_t*/[] shadowIndexes = new int[MAX_SHADOW_INDEXES];

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
    static int TRIANGLE_CULLED(final int p1, final int p2, final int p3, final int[] pointCull) {
        return (pointCull[p1] & pointCull[p2] & pointCull[p3] & 0x3f);
    }
//

    //#define TRIANGLE_CLIPPED(p1,p2,p3) ( ( pointCull[p1] | pointCull[p2] | pointCull[p3] ) & 0xfc0 )
    static boolean TRIANGLE_CLIPPED(final int p1, final int p2, final int p3, final int[] pointCull) {
        return (pointCull[p1] & pointCull[p2] & pointCull[p3] & 0xfc0) != 0xfc0;
    }

    // an edge that is on the plane is NOT culled
    static int EDGE_CULLED(final int p1, final int p2, final int[] pointCull) {
        return ((pointCull[p1] ^ 0xfc0) & (pointCull[p2] ^ 0xfc0) & 0xfc0);
    }

    static boolean EDGE_CLIPPED(final int p1, final int p2, final int[] pointCull) {
        return ((pointCull[p1] & pointCull[p2] & 0xfc0) != 0xfc0);
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
    static boolean POINT_CULLED(final int p1, final int[] pointCull) {
        return ((pointCull[p1] & 0xfc0) != 0xfc0);
    }

    public static boolean PointsOrdered(final idVec3 a, final idVec3 b) {
        final float i, j;

        // vectors that wind up getting an equal hash value will
        // potentially cause a misorder, which can show as a couple
        // crack pixels in a shadow
        // scale by some odd numbers so -8, 8, 8 will not be equal
        // to 8, -8, 8
        // in the very rare case that these might be equal, all that would
        // happen is an oportunity for a tiny rasterization shadow crack
        i = a.oGet(0) + a.oGet(1) * 127 + a.oGet(2) * 1023;
        j = b.oGet(0) + b.oGet(1) * 127 + b.oGet(2) * 1023;

        return (i < j);
    }

    /*
     ====================
     R_LightProjectionMatrix

     ====================
     */
    public static void R_LightProjectionMatrix(final idVec3 origin, final idPlane rearPlane, idVec4[] mat/*[4]*/) {
        idVec4 lv = new idVec4();
        float lg;

        // calculate the homogenious light vector
        lv.x = origin.x;
        lv.y = origin.y;
        lv.z = origin.z;
        lv.w = 1;

        lg = rearPlane.ToVec4().oMultiply(lv);

        // outer product
        mat[0].oSet(0, lg - rearPlane.oGet(0) * lv.oGet(0));
        mat[0].oSet(1, -rearPlane.oGet(1) * lv.oGet(0));
        mat[0].oSet(2, -rearPlane.oGet(2) * lv.oGet(0));
        mat[0].oSet(3, -rearPlane.oGet(3) * lv.oGet(0));

        mat[1].oSet(0, -rearPlane.oGet(0) * lv.oGet(1));
        mat[1].oSet(1, lg - rearPlane.oGet(1) * lv.oGet(1));
        mat[1].oSet(2, -rearPlane.oGet(2) * lv.oGet(1));
        mat[1].oSet(3, -rearPlane.oGet(3) * lv.oGet(1));

        mat[2].oSet(0, -rearPlane.oGet(0) * lv.oGet(2));
        mat[2].oSet(1, -rearPlane.oGet(1) * lv.oGet(2));
        mat[2].oSet(2, lg - rearPlane.oGet(2) * lv.oGet(2));
        mat[2].oSet(3, -rearPlane.oGet(3) * lv.oGet(2));

        mat[3].oSet(0, -rearPlane.oGet(0) * lv.oGet(3));
        mat[3].oSet(1, -rearPlane.oGet(1) * lv.oGet(3));
        mat[3].oSet(2, -rearPlane.oGet(2) * lv.oGet(3));
        mat[3].oSet(3, lg - rearPlane.oGet(3) * lv.oGet(3));
    }

    /*
     ===================
     R_ProjectPointsToFarPlane

     make a projected copy of the even verts into the odd spots
     that is on the far light clip plane
     ===================
     */
    public static void R_ProjectPointsToFarPlane(final idRenderEntityLocal ent, final idRenderLightLocal light,
                                                 final idPlane lightPlaneLocal,
                                                 int firstShadowVert, int numShadowVerts) {
        final idVec3 lv = new idVec3();
        idVec4[] mat = Stream.generate(idVec4::new).limit(4).toArray(idVec4[]::new);
        int i;
        int in;

        R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, lv);
        R_LightProjectionMatrix(lv, lightPlaneLocal, mat);

        if (true) {
            // make a projected copy of the even verts into the odd spots
            in = firstShadowVert;
            for (i = firstShadowVert; i < numShadowVerts; i += 2, in += 2) {
                float w, oow;

                shadowVerts[in + 0].w = 1;

                w = shadowVerts[in].ToVec3().oMultiply(mat[3].ToVec3()) + mat[3].oGet(3);
                if (w == 0) {
                    shadowVerts[in + 1] = shadowVerts[in + 0];
                    continue;
                }

                oow = 1.0f / w;
                shadowVerts[in + 1].x = (shadowVerts[in].ToVec3().oMultiply(mat[0].ToVec3()) + mat[0].oGet(3)) * oow;
                shadowVerts[in + 1].y = (shadowVerts[in].ToVec3().oMultiply(mat[1].ToVec3()) + mat[1].oGet(3)) * oow;
                shadowVerts[in + 1].z = (shadowVerts[in].ToVec3().oMultiply(mat[2].ToVec3()) + mat[2].oGet(3)) * oow;
                shadowVerts[in + 1].w = 1;
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
    public static int R_ChopWinding(clipTri_t[] clipTris/*[2]*/, int inNum, final idPlane plane) {
        clipTri_t in, out;
        float[] dists = new float[MAX_CLIPPED_POINTS];
        int[] sides = new int[MAX_CLIPPED_POINTS];
        int[] counts = new int[3];
        float dot;
        int i, j;
        final idVec3 p1 = new idVec3(), p2 = new idVec3();
        final idVec3 mid = new idVec3();

        in = clipTris[inNum];
        out = clipTris[inNum ^ 1];
        counts[0] = counts[1] = counts[2] = 0;

        // determine sides for each point
        for (i = 0; i < in.numVerts; i++) {
            dot = plane.Distance(in.verts[i]);
            dists[i] = dot;
            if (dot < -LIGHT_CLIP_EPSILON) {
                sides[i] = SIDE_BACK;
            } else if (dot > LIGHT_CLIP_EPSILON) {
                sides[i] = SIDE_FRONT;
            } else {
                sides[i] = SIDE_ON;
            }
            counts[sides[i]]++;
        }

        // if none in front, it is completely clipped away
        if (0 == counts[SIDE_FRONT]) {
            in.numVerts = 0;
            return inNum;
        }
        if (0 == counts[SIDE_BACK]) {
            return inNum;        // inout stays the same
        }

        // avoid wrapping checks by duplicating first value to end
        sides[i] = sides[0];
        dists[i] = dists[0];
        in.verts[in.numVerts].oSet(in.verts[0]);
        in.edgeFlags[in.numVerts] = in.edgeFlags[0];

        out.numVerts = 0;
        for (i = 0; i < in.numVerts; i++) {
            p1.oSet(in.verts[i]);

            if (sides[i] != SIDE_BACK) {
                out.verts[out.numVerts].oSet(p1);
                if (sides[i] == SIDE_ON && sides[i + 1] == SIDE_BACK) {
                    out.edgeFlags[out.numVerts] = 1;
                } else {
                    out.edgeFlags[out.numVerts] = in.edgeFlags[i];
                }
                out.numVerts++;
            }

            if ((sides[i] == SIDE_FRONT && sides[i + 1] == SIDE_BACK)
                    || (sides[i] == SIDE_BACK && sides[i + 1] == SIDE_FRONT)) {
                // generate a split point
                p2.oSet(in.verts[i + 1]);

                dot = dists[i] / (dists[i] - dists[i + 1]);
                for (j = 0; j < 3; j++) {
                    mid.oSet(j, p1.oGet(j) + dot * (p2.oGet(j) - p1.oGet(j)));
                }

                out.verts[out.numVerts].oSet(mid);

                // set the edge flag
                if (sides[i + 1] != SIDE_FRONT) {
                    out.edgeFlags[out.numVerts] = 1;
                } else {
                    out.edgeFlags[out.numVerts] = in.edgeFlags[i];
                }

                out.numVerts++;
            }
        }

        return inNum ^ 1;
    }

    /*
     ===================
     R_ClipTriangleToLight

     Returns false if nothing is left after clipping
     ===================
     */
    public static boolean R_ClipTriangleToLight(final idVec3 a, final idVec3 b, final idVec3 c, int planeBits, final idPlane[] frustum/*[6] */) {
        int i;
        int base;
        clipTri_t[] pingPong = {new clipTri_t(), new clipTri_t()};
        clipTri_t ct;
        int p;

        pingPong[0].numVerts = 3;
        pingPong[0].edgeFlags[0] = 0;
        pingPong[0].edgeFlags[1] = 0;
        pingPong[0].edgeFlags[2] = 0;
        pingPong[0].verts[0] = a;
        pingPong[0].verts[1] = b;
        pingPong[0].verts[2] = c;

        p = 0;
        for (i = 0; i < 6; i++) {
            if ((planeBits & (1 << i)) != 0) {
                p = R_ChopWinding(pingPong, p, frustum[i]);
                if (pingPong[p].numVerts < 1) {
                    return false;
                }
            }
        }
        ct = pingPong[p];

        // copy the clipped points out to shadowVerts
        if (numShadowVerts + ct.numVerts * 2 > MAX_SHADOW_VERTS) {
            overflowed = true;
            return false;
        }

        base = numShadowVerts;
        for (i = 0; i < ct.numVerts; i++) {
            shadowVerts[base + i * 2].oSet(ct.verts[i]);
        }
        numShadowVerts += ct.numVerts * 2;

        if (numShadowIndexes + 3 * (ct.numVerts - 2) > MAX_SHADOW_INDEXES) {
            overflowed = true;
            return false;
        }

        for (i = 2; i < ct.numVerts; i++) {
            shadowIndexes[numShadowIndexes++] = base + i * 2;
            shadowIndexes[numShadowIndexes++] = base + (i - 1) * 2;
            shadowIndexes[numShadowIndexes++] = base;
        }

        // any edges that were created by the clipping process will
        // have a silhouette quad created for it, because it is one
        // of the exterior bounds of the shadow volume
        for (i = 0; i < ct.numVerts; i++) {
            if (ct.edgeFlags[i] != 0) {
                if (numClipSilEdges == MAX_CLIP_SIL_EDGES) {
                    break;
                }
                clipSilEdges[numClipSilEdges][0] = base + i * 2;
                if (i == ct.numVerts - 1) {
                    clipSilEdges[numClipSilEdges][1] = base;
                } else {
                    clipSilEdges[numClipSilEdges][1] = base + (i + 1) * 2;
                }
                numClipSilEdges++;
            }
        }

        return true;
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
    public static boolean R_ClipLineToLight(final idVec3 a, final idVec3 b, final idPlane[] frustum/*[4]*/, final idVec3 p1, final idVec3 p2) {
        float[] clip;
        int j;
        float d1, d2;
        float f;

        p1.oSet(a);
        p2.oSet(b);

        // clip it
        for (j = 0; j < 6; j++) {
            d1 = frustum[j].Distance(p1);
            d2 = frustum[j].Distance(p2);

            // if both on or in front, not clipped to this plane
            if (d1 > -LIGHT_CLIP_EPSILON && d2 > -LIGHT_CLIP_EPSILON) {
                continue;
            }

            // if one is behind and the other isn't clearly in front, the edge is clipped off
            if (d1 <= -LIGHT_CLIP_EPSILON && d2 < LIGHT_CLIP_EPSILON) {
                return false;
            }
            if (d2 <= -LIGHT_CLIP_EPSILON && d1 < LIGHT_CLIP_EPSILON) {
                return false;
            }

            // clip it, keeping the negative side
            if (d1 < 0) {
                clip = p1.ToFloatPtr();
            } else {
                clip = p2.ToFloatPtr();
            }

//if (false){
//		if ( Math.abs(d1 - d2) < 0.001 ) {
//			d2 = d1 - 0.1;
//		}
//}
            f = d1 / (d1 - d2);
            clip[0] = p1.oGet(0) + f * (p2.oGet(0) - p1.oGet(0));
            clip[1] = p1.oGet(1) + f * (p2.oGet(1) - p1.oGet(1));
            clip[2] = p1.oGet(2) + f * (p2.oGet(2) - p1.oGet(2));
        }

        return true;    // retain a fragment
    }

    /*
     ==================
     R_AddClipSilEdges

     Add sil edges for each triangle clipped to the side of
     the frustum.

     Only done for simple projected lights, not point lights.
     ==================
     */
    public static void R_AddClipSilEdges() {
        int v1, v2;
        int v1_back, v2_back;
        int i;

        // don't allow it to overflow
        if (numShadowIndexes + numClipSilEdges * 6 > MAX_SHADOW_INDEXES) {
            overflowed = true;
            return;
        }

        for (i = 0; i < numClipSilEdges; i++) {
            v1 = clipSilEdges[i][0];
            v2 = clipSilEdges[i][1];
            v1_back = v1 + 1;
            v2_back = v2 + 1;
            if (PointsOrdered(shadowVerts[v1].ToVec3(), shadowVerts[v2].ToVec3())) {
                shadowIndexes[numShadowIndexes++] = v1;
                shadowIndexes[numShadowIndexes++] = v2;
                shadowIndexes[numShadowIndexes++] = v1_back;
                shadowIndexes[numShadowIndexes++] = v2;
                shadowIndexes[numShadowIndexes++] = v2_back;
                shadowIndexes[numShadowIndexes++] = v1_back;
            } else {
                shadowIndexes[numShadowIndexes++] = v1;
                shadowIndexes[numShadowIndexes++] = v2;
                shadowIndexes[numShadowIndexes++] = v2_back;
                shadowIndexes[numShadowIndexes++] = v1;
                shadowIndexes[numShadowIndexes++] = v2_back;
                shadowIndexes[numShadowIndexes++] = v1_back;
            }
        }
    }

    /*
     =================
     R_AddSilEdges

     Add quads from the front points to the projected points
     for each silhouette edge in the light
     =================
     */
    public static void R_AddSilEdges(final srfTriangles_s tri, int[] pointCull, final idPlane[] frustum/*[6]*/) {
        int v1, v2;
        int i;
        silEdge_t sil;
        int numPlanes;

        numPlanes = tri.numIndexes / 3;

        // add sil edges for any true silhouette boundaries on the surface
        for (i = 0; i < tri.numSilEdges; i++) {
            sil = tri.silEdges[i];
            if (sil.p1 < 0 || sil.p1 > numPlanes || sil.p2 < 0 || sil.p2 > numPlanes) {
                common.Error("Bad sil planes");
            }

            // an edge will be a silhouette edge if the face on one side
            // casts a shadow, but the face on the other side doesn't.
            // "casts a shadow" means that it has some surface in the projection,
            // not just that it has the correct facing direction
            // This will cause edges that are exactly on the frustum plane
            // to be considered sil edges if the face inside casts a shadow.
            if (0 == (faceCastsShadow[sil.p1] ^ faceCastsShadow[sil.p2])) {
                continue;
            }

            // if the edge is completely off the negative side of
            // a frustum plane, don't add it at all.  This can still
            // happen even if the face is visible and casting a shadow
            // if it is partially clipped
            if (EDGE_CULLED(sil.v1, sil.v2, pointCull) != 0) {
                continue;
            }

            // see if the edge needs to be clipped
            if (EDGE_CLIPPED(sil.v1, sil.v2, pointCull)) {
                if (numShadowVerts + 4 > MAX_SHADOW_VERTS) {
                    overflowed = true;
                    return;
                }
                v1 = numShadowVerts;
                v2 = v1 + 2;
                if (!R_ClipLineToLight(tri.verts[sil.v1].xyz, tri.verts[sil.v2].xyz,
                        frustum, shadowVerts[v1].ToVec3(), shadowVerts[v2].ToVec3())) {
                    continue;    // clipped away
                }

                numShadowVerts += 4;
            } else {
                // use the entire edge
                v1 = remap[sil.v1];
                v2 = remap[sil.v2];
                if (v1 < 0 || v2 < 0) {
                    common.Error("R_AddSilEdges: bad remap[]");
                }
            }

            // don't overflow
            if (numShadowIndexes + 6 > MAX_SHADOW_INDEXES) {
                overflowed = true;
                return;
            }

            // we need to choose the correct way of triangulating the silhouette quad
            // consistantly between any two points, no matter which order they are specified.
            // If this wasn't done, slight rasterization cracks would show in the shadow
            // volume when two sil edges were exactly coincident
            if (faceCastsShadow[sil.p2] != 0) {
                if (PointsOrdered(shadowVerts[v1].ToVec3(), shadowVerts[v2].ToVec3())) {
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                } else {
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                }
            } else {
                if (PointsOrdered(shadowVerts[v1].ToVec3(), shadowVerts[v2].ToVec3())) {
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                } else {
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v2;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                    shadowIndexes[numShadowIndexes++] = v1;
                    shadowIndexes[numShadowIndexes++] = v2 + 1;
                    shadowIndexes[numShadowIndexes++] = v1 + 1;
                }
            }
        }
    }

    /*
     ================
     R_CalcPointCull

     Also inits the remap[] array to all -1
     ================
     */
    public static void R_CalcPointCull(final srfTriangles_s tri, final idPlane[] frustum/*[6]*/, int[] pointCull) {
        int i;
        int frontBits;
        float[] planeSide;
        byte[] side1, side2;

        SIMDProcessor.Memset(remap, -1, tri.numVerts /* sizeof( remap[0] )*/);//TODO:create template functions that call the standard functions, that way we just have to replace the template body.

        for (frontBits = 0, i = 0; i < 6; i++) {
            // get front bits for the whole surface
            if (tri.bounds.PlaneDistance(frustum[i]) >= LIGHT_CLIP_EPSILON) {
                frontBits |= 1 << (i + 6);
            }
        }

        // initialize point cull
        for (i = 0; i < tri.numVerts; i++) {
            pointCull[i] = frontBits;
        }

        // if the surface is not completely inside the light frustum
        if (frontBits == (((1 << 6) - 1)) << 6) {
            return;
        }

        planeSide = new float[tri.numVerts];
        side1 = new byte[tri.numVerts];//SIMDProcessor.Memset( side1, 0, tri.numVerts * sizeof( byte ) );
        side2 = new byte[tri.numVerts];//SIMDProcessor.Memset( side2, 0, tri.numVerts * sizeof( byte ) );

        for (i = 0; i < 6; i++) {

            if ((frontBits & (1 << (i + 6))) != 0) {
                continue;
            }

            SIMDProcessor.Dot(planeSide, frustum[i], tri.verts, tri.numVerts);
            SIMDProcessor.CmpLT(side1, (byte) i, planeSide, LIGHT_CLIP_EPSILON, tri.numVerts);
            SIMDProcessor.CmpGT(side2, (byte) i, planeSide, -LIGHT_CLIP_EPSILON, tri.numVerts);
        }
        for (i = 0; i < tri.numVerts; i++) {
            pointCull[i] |= side1[i] | (side2[i] << 6);
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
    public static void R_CreateShadowVolumeInFrustum(final idRenderEntityLocal ent,
                                                     final srfTriangles_s tri,
                                                     final idRenderLightLocal light,
                                                     final idVec3 lightOrigin,
                                                     final idPlane[] frustum/*[6]*/,
                                                     final idPlane farPlane,
                                                     boolean makeClippedPlanes) {
        int i;
        int numTris;
        int[] pointCull;
        int numCapIndexes;
        int firstShadowIndex;
        int firstShadowVert;
        int cullBits;

        pointCull = new int[tri.numVerts];

        // test the vertexes for inside the light frustum, which will allow
        // us to completely cull away some triangles from consideration.
        R_CalcPointCull(tri, frustum, pointCull);

        // this may not be the first frustum added to the volume
        firstShadowIndex = numShadowIndexes;
        firstShadowVert = numShadowVerts;

        // decide which triangles front shadow volumes, clipping as needed
        numClipSilEdges = 0;
        numTris = tri.numIndexes / 3;
        for (i = 0; i < numTris; i++) {
            int i1, i2, i3;

            faceCastsShadow[i] = 0;    // until shown otherwise

            // if it isn't facing the right way, don't add it
            // to the shadow volume
            if (globalFacing[i] != 0) {
                continue;
            }

            i1 = tri.silIndexes[i * 3 + 0];
            i2 = tri.silIndexes[i * 3 + 1];
            i3 = tri.silIndexes[i * 3 + 2];

            // if all the verts are off one side of the frustum,
            // don't add any of them
            if (TRIANGLE_CULLED(i1, i2, i3, pointCull) != 0) {
                continue;
            }

            // make sure the verts that are not on the negative sides
            // of the frustum are copied over.
            // we need to get the original verts even from clipped triangles
            // so the edges reference correctly, because an edge may be unclipped
            // even when a triangle is clipped.
            if (numShadowVerts + 6 > MAX_SHADOW_VERTS) {
                overflowed = true;
                return;
            }

            if (!POINT_CULLED(i1, pointCull) && remap[i1] == -1) {
                remap[i1] = numShadowVerts;
                shadowVerts[numShadowVerts].oSet(tri.verts[i1].xyz);
                numShadowVerts += 2;
            }
            if (!POINT_CULLED(i2, pointCull) && remap[i2] == -1) {
                remap[i2] = numShadowVerts;
                shadowVerts[numShadowVerts].oSet(tri.verts[i2].xyz);
                numShadowVerts += 2;
            }
            if (!POINT_CULLED(i3, pointCull) && remap[i3] == -1) {
                remap[i3] = numShadowVerts;
                shadowVerts[numShadowVerts].oSet(tri.verts[i3].xyz);
                numShadowVerts += 2;
            }

            // clip the triangle if any points are on the negative sides
            if (TRIANGLE_CLIPPED(i1, i2, i3, pointCull)) {
                cullBits = ((pointCull[i1] ^ 0xfc0) | (pointCull[i2] ^ 0xfc0) | (pointCull[i3] ^ 0xfc0)) >> 6;
                // this will also define clip edges that will become
                // silhouette planes
                if (R_ClipTriangleToLight(tri.verts[i1].xyz, tri.verts[i2].xyz,
                        tri.verts[i3].xyz, cullBits, frustum)) {
                    faceCastsShadow[i] = 1;
                }
            } else {
                // instead of overflowing or drawing a streamer shadow, don't draw a shadow at all
                if (numShadowIndexes + 3 > MAX_SHADOW_INDEXES) {
                    overflowed = true;
                    return;
                }
                if (remap[i1] == -1 || remap[i2] == -1 || remap[i3] == -1) {
                    common.Error("R_CreateShadowVolumeInFrustum: bad remap[]");
                }
                shadowIndexes[numShadowIndexes++] = remap[i3];
                shadowIndexes[numShadowIndexes++] = remap[i2];
                shadowIndexes[numShadowIndexes++] = remap[i1];
                faceCastsShadow[i] = 1;
            }
        }

        // add indexes for the back caps, which will just be reversals of the
        // front caps using the back vertexes
        numCapIndexes = numShadowIndexes - firstShadowIndex;

        // if no faces have been defined for the shadow volume,
        // there won't be anything at all
        if (numCapIndexes == 0) {
            return;
        }

        //--------------- off-line processing ------------------
        // if we are running from dmap, perform the (very) expensive shadow optimizations
        // to remove internal sil edges and optimize the caps
        if (callOptimizer) {
            optimizedShadow_t opt;

            // project all of the vertexes to the shadow plane, generating
            // an equal number of back vertexes
//		R_ProjectPointsToFarPlane( ent, light, farPlane, firstShadowVert, numShadowVerts );
            opt = SuperOptimizeOccluders(shadowVerts, Arrays.copyOf(shadowIndexes, firstShadowIndex), numCapIndexes, farPlane, lightOrigin);

            // pull off the non-optimized data
            numShadowIndexes = firstShadowIndex;
            numShadowVerts = firstShadowVert;

            // add the optimized data
            if (numShadowIndexes + opt.totalIndexes > MAX_SHADOW_INDEXES
                    || numShadowVerts + opt.numVerts > MAX_SHADOW_VERTS) {
                overflowed = true;
                common.Printf("WARNING: overflowed MAX_SHADOW tables, shadow discarded\n");
                opt.verts = null;
                opt.indexes = null;//Mem_Free(opt.indexes);
                return;
            }

            for (i = 0; i < opt.numVerts; i++) {
                shadowVerts[numShadowVerts + i].oSet(0, opt.verts[i].oGet(0));
                shadowVerts[numShadowVerts + i].oSet(1, opt.verts[i].oGet(1));
                shadowVerts[numShadowVerts + i].oSet(2, opt.verts[i].oGet(2));
                shadowVerts[numShadowVerts + i].oSet(3, 1);
            }
            for (i = 0; i < opt.totalIndexes; i++) {
                int index = opt.indexes[i];
                if (index < 0 || index > opt.numVerts) {
                    common.Error("optimized shadow index out of range");
                }
                shadowIndexes[numShadowIndexes + i] = index + numShadowVerts;
            }

            numShadowVerts += opt.numVerts;
            numShadowIndexes += opt.totalIndexes;

            // note the index distribution so we can sort all the caps after all the sils
            indexRef[indexFrustumNumber].frontCapStart = firstShadowIndex;
            indexRef[indexFrustumNumber].rearCapStart = firstShadowIndex + opt.numFrontCapIndexes;
            indexRef[indexFrustumNumber].silStart = firstShadowIndex + opt.numFrontCapIndexes + opt.numRearCapIndexes;
            indexRef[indexFrustumNumber].end = numShadowIndexes;
            indexFrustumNumber++;

            opt.verts = null;//Mem_Free(opt.verts);
            opt.indexes = null;//Mem_Free(opt.indexes);
            return;
        }

        //--------------- real-time processing ------------------
        // the dangling edge "face" is never considered to cast a shadow,
        // so any face with dangling edges that casts a shadow will have
        // it's dangling sil edge trigger a sil plane
        faceCastsShadow[numTris] = 0;

        // instead of overflowing or drawing a streamer shadow, don't draw a shadow at all
        // if we ran out of space
        if (numShadowIndexes + numCapIndexes > MAX_SHADOW_INDEXES) {
            overflowed = true;
            return;
        }
        for (i = 0; i < numCapIndexes; i += 3) {
            shadowIndexes[numShadowIndexes + i + 0] = shadowIndexes[firstShadowIndex + i + 2] + 1;
            shadowIndexes[numShadowIndexes + i + 1] = shadowIndexes[firstShadowIndex + i + 1] + 1;
            shadowIndexes[numShadowIndexes + i + 2] = shadowIndexes[firstShadowIndex + i + 0] + 1;
        }
        numShadowIndexes += numCapIndexes;

        c_caps += numCapIndexes * 2;

        int preSilIndexes = numShadowIndexes;

        // if any triangles were clipped, we will have a list of edges
        // on the frustum which must now become sil edges
        if (makeClippedPlanes) {
            R_AddClipSilEdges();
        }

        // any edges that are a transition between a shadowing and
        // non-shadowing triangle will cast a silhouette edge
        R_AddSilEdges(tri, pointCull, frustum);

        c_sils += numShadowIndexes - preSilIndexes;

        // project all of the vertexes to the shadow plane, generating
        // an equal number of back vertexes
        R_ProjectPointsToFarPlane(ent, light, farPlane, firstShadowVert, numShadowVerts);

        // note the index distribution so we can sort all the caps after all the sils
        indexRef[indexFrustumNumber].frontCapStart = firstShadowIndex;
        indexRef[indexFrustumNumber].rearCapStart = firstShadowIndex + numCapIndexes;
        indexRef[indexFrustumNumber].silStart = preSilIndexes;
        indexRef[indexFrustumNumber].end = numShadowIndexes;
        indexFrustumNumber++;
    }

    public static void R_MakeShadowFrustums(idRenderLightLocal light) {
        int i, j;

        if (light.parms.pointLight) {

            // exact projection,taking into account asymetric frustums when
                // globalLightOrigin isn't centered

                boolean centerOutside = Math.abs(light.parms.lightCenter.oGet(0)) > light.parms.lightRadius.oGet(0)
                        || Math.abs(light.parms.lightCenter.oGet(1)) > light.parms.lightRadius.oGet(1)
                        || Math.abs(light.parms.lightCenter.oGet(2)) > light.parms.lightRadius.oGet(2);

                // if the light center of projection is outside the light bounds,
                // we will need to build the planes a little differently

                // make the corners
            final idVec3[] corners = idVec3.generateArray(8);

                for (i = 0; i < 8; i++) {
                    final idVec3 temp = new idVec3();
                    for (j = 0; j < 3; j++) {
                        if ((i & (1 << j)) != 0) {
                            temp.oSet(j, light.parms.lightRadius.oGet(j));
                        } else {
                            temp.oSet(j, -light.parms.lightRadius.oGet(j));
                        }
                    }

                    // transform to global space
                    corners[i].oSet(light.parms.origin.oPlus(light.parms.axis.oMultiply(temp)));
                }

                light.numShadowFrustums = 0;
                for (int side = 0; side < 6; side++) {
                    shadowFrustum_t frust = light.shadowFrustums[light.numShadowFrustums];
                    final idVec3 p1 = corners[faceCorners[side][0]];
                    final idVec3 p2 = corners[faceCorners[side][1]];
                    final idVec3 p3 = corners[faceCorners[side][2]];
                    final idPlane backPlane = new idPlane();

                    // plane will have positive side inward
                    backPlane.FromPoints(p1, p2, p3);

                    // if center of projection is on the wrong side, skip
                    float d = backPlane.Distance(light.globalLightOrigin);
                    if (d < 0) {
                        continue;
                    }

                    frust.numPlanes = 6;
                    frust.planes[5] = new idPlane(backPlane);
                    frust.planes[4] = new idPlane(backPlane);    // we don't really need the extra plane

                    // make planes with positive side facing inwards in light local coordinates
                    for (int edge = 0; edge < 4; edge++) {
                        final idVec3 p4 = corners[faceCorners[side][edge]];
                        final idVec3 p5 = corners[faceCorners[side][(edge + 1) & 3]];

                        // create a plane that goes through the center of projection
                        frust.planes[edge].FromPoints(p5, p4, light.globalLightOrigin);

                        // see if we should use an adjacent plane instead
                        if (centerOutside) {
                            final idVec3 p6 = corners[faceEdgeAdjacent[side][edge]];
                            final idPlane sidePlane = new idPlane();

                            sidePlane.FromPoints(p5, p4, p6);
                            d = sidePlane.Distance(light.globalLightOrigin);
                            if (d < 0) {
                                // use this plane instead of the edged plane
                                frust.planes[edge] = sidePlane;
                            }
                            // we can't guarantee a neighbor, so add sill planes at edge
                            light.shadowFrustums[light.numShadowFrustums].makeClippedPlanes = true;
                        }
                    }
                    light.numShadowFrustums++;
                }

            return;
        }

        // projected light
        light.numShadowFrustums = 1;
        shadowFrustum_t frust = light.shadowFrustums[0];

        // flip and transform the frustum planes so the positive side faces
        // inward in local coordinates
        // it is important to clip against even the near clip plane, because
        // many projected lights that are faking area lights will have their
        // origin behind solid surfaces.
        for (i = 0; i < 6; i++) {
            final idPlane plane = frust.planes[i];

            plane.SetNormal(light.frustum[i].Normal().oNegative());
            plane.SetDist(-light.frustum[i].Dist());
        }

        frust.numPlanes = 6;

        frust.makeClippedPlanes = true;
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
    public static srfTriangles_s R_CreateShadowVolume(final idRenderEntityLocal ent,
                                                      final srfTriangles_s tri, final idRenderLightLocal light,
                                                      shadowGen_t optimize, srfCullInfo_t cullInfo) {
        int i, j;
        final idVec3 lightOrigin = new idVec3();
        srfTriangles_s newTri;
        int capPlaneBits;

        if (!RenderSystem_init.r_shadows.GetBool()) {
            return null;
        }

        if (tri.numSilEdges == 0 || tri.numIndexes == 0 || tri.numVerts == 0) {
            return null;
        }

        if (tri.numIndexes < 0) {
            common.Error("R_CreateShadowVolume: tri.numIndexes = %d", tri.numIndexes);
        }

        if (tri.numVerts < 0) {
            common.Error("R_CreateShadowVolume: tri.numVerts = %d", tri.numVerts);
        }

        tr.pc.c_createShadowVolumes++;

        // use the fast infinite projection in dynamic situations, which
        // trades somewhat more overdraw and no cap optimizations for
        // a very simple generation process
        if (optimize == SG_DYNAMIC && RenderSystem_init.r_useTurboShadow.GetBool()) {
            if (tr.backEndRendererHasVertexPrograms && RenderSystem_init.r_useShadowVertexProgram.GetBool()) {
                return R_CreateVertexProgramTurboShadowVolume(ent, tri, light, cullInfo);
            } else {
                return R_CreateTurboShadowVolume(ent, tri, light, cullInfo);
            }
        }

        R_CalcInteractionFacing(ent, tri, light, cullInfo);

        int numFaces = tri.numIndexes / 3;
        int allFront = 1;
        for (i = 0; i < numFaces && allFront != 0; i++) {
            allFront &= cullInfo.facing[i];
        }
        if (allFront != 0) {
            // if no faces are the right direction, don't make a shadow at all
            return null;
        }

        // clear the shadow volume
        numShadowIndexes = 0;
        numShadowVerts = 0;
        overflowed = false;
        indexFrustumNumber = 0;
        capPlaneBits = 0;
        callOptimizer = (optimize == SG_OFFLINE);

        // the facing information will be the same for all six projections
        // from a point light, as well as for any directed lights
        globalFacing = cullInfo.facing;
        faceCastsShadow = new byte[tri.numIndexes / 3 + 1];    // + 1 for fake dangling edge face
        remap = new int[tri.numVerts];

        R_GlobalPointToLocal(ent.modelMatrix, light.globalLightOrigin, lightOrigin);

        // run through all the shadow frustums, which is one for a projected light,
        // and usually six for a point light, but point lights with centers outside
        // the box may have less
        for (int frustumNum = 0; frustumNum < light.numShadowFrustums; frustumNum++) {
            final shadowFrustum_t frust = light.shadowFrustums[frustumNum];
//		ALIGN16( idPlane[] frustum=new idPlane[6] );
            idPlane[] frustum = new idPlane[6];

            // transform the planes into entity space
            // we could share and reverse some of the planes between frustums for a minor
            // speed increase
            // the cull test is redundant for a single shadow frustum projected light, because
            // the surface has already been checked against the main light frustums
            for (j = 0; j < frust.numPlanes; j++) {
                frustum[j] = new idPlane();
                R_GlobalPlaneToLocal(ent.modelMatrix, frust.planes[j], frustum[j]);

                // try to cull the entire surface against this frustum
                float d = tri.bounds.PlaneDistance(frustum[j]);
                if (d < -LIGHT_CLIP_EPSILON) {
                    break;
                }
            }
            if (j != frust.numPlanes) {
                continue;
            }
            // we need to check all the triangles
            int oldFrustumNumber = indexFrustumNumber;

            R_CreateShadowVolumeInFrustum(ent, tri, light, lightOrigin, frustum, frustum[5], frust.makeClippedPlanes);

            // if we couldn't make a complete shadow volume, it is better to
            // not draw one at all, avoiding streamer problems
            if (overflowed) {
                return null;
            }

            if (indexFrustumNumber != oldFrustumNumber) {
                // note that we have caps projected against this frustum,
                // which may allow us to skip drawing the caps if all projected
                // planes face away from the viewer and the viewer is outside the light volume
                capPlaneBits |= 1 << frustumNum;
            }
        }

        // if no faces have been defined for the shadow volume,
        // there won't be anything at all
        if (numShadowIndexes == 0) {
            return null;
        }

        // this should have been prevented by the overflowed flag, so if it ever happens,
        // it is a code error
        if (numShadowVerts > MAX_SHADOW_VERTS || numShadowIndexes > MAX_SHADOW_INDEXES) {
            common.FatalError("Shadow volume exceeded allocation");
        }

        // allocate a new surface for the shadow volume
        newTri = R_AllocStaticTriSurf();

        // we might consider setting this, but it would only help for
        // large lights that are partially off screen
        newTri.bounds.Clear();

        // copy off the verts and indexes
        newTri.numVerts = numShadowVerts;
        newTri.numIndexes = numShadowIndexes;

        // the shadow verts will go into a main memory buffer as well as a vertex
        // cache buffer, so they can be copied back if they are purged
        R_AllocStaticTriSurfShadowVerts(newTri, newTri.numVerts);
        SIMDProcessor.Memcpy(newTri.shadowVertexes, shadowVerts, newTri.numVerts);

        R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes);

        if (true /* sortCapIndexes */) {
            newTri.shadowCapPlaneBits = capPlaneBits;

            // copy the sil indexes first
            newTri.numShadowIndexesNoCaps = 0;
            for (i = 0; i < indexFrustumNumber; i++) {
                int c = indexRef[i].end - indexRef[i].silStart;
                SIMDProcessor.Memcpy(newTri.indexes, newTri.numShadowIndexesNoCaps, shadowIndexes, indexRef[i].silStart, c);
                newTri.numShadowIndexesNoCaps += c;
            }
            // copy rear cap indexes next
            newTri.numShadowIndexesNoFrontCaps = newTri.numShadowIndexesNoCaps;
            for (i = 0; i < indexFrustumNumber; i++) {
                int c = indexRef[i].silStart - indexRef[i].rearCapStart;
                SIMDProcessor.Memcpy(newTri.indexes, newTri.numShadowIndexesNoFrontCaps, shadowIndexes, indexRef[i].rearCapStart, c);
                newTri.numShadowIndexesNoFrontCaps += c;
            }
            // copy front cap indexes last
            newTri.numIndexes = newTri.numShadowIndexesNoFrontCaps;
            for (i = 0; i < indexFrustumNumber; i++) {
                int c = indexRef[i].rearCapStart - indexRef[i].frontCapStart;
                SIMDProcessor.Memcpy(newTri.indexes, newTri.numIndexes, shadowIndexes, indexRef[i].frontCapStart, c);
                newTri.numIndexes += c;
            }

        } else {
            newTri.shadowCapPlaneBits = 63;    // we don't have optimized index lists
            SIMDProcessor.Memcpy(newTri.indexes, shadowIndexes, newTri.numIndexes);
        }

        if (optimize == SG_OFFLINE) {
            CleanupOptimizedShadowTris(newTri);
        }

        return newTri;
    }
    /*
     ============================================================

     TR_STENCILSHADOWS

     "facing" should have one more element than tri->numIndexes / 3, which should be set to 1

     ============================================================
     */
    public enum shadowGen_t {

        SG_DYNAMIC,// use infinite projections
        SG_STATIC, // clip to bounds
        SG_OFFLINE // perform very time consuming optimizations
    }

    static class indexRef_t {

        int end;
        int frontCapStart;
        int rearCapStart;
        int silStart;
    }

    static class clipTri_t {

        int[] edgeFlags = new int[MAX_CLIPPED_POINTS];
        int numVerts;
        final idVec3[] verts = idVec3.generateArray(MAX_CLIPPED_POINTS);
    }
}
