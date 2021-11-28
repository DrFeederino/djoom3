package neo.Renderer

import neo.Renderer.Model.dominantTri_s
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.deformInfo_s
import neo.Renderer.tr_local.frameData_t
import neo.TempDump
import neo.framework.BuildDefines
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.*
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object tr_trisurf {
    //
    // instead of using the texture T vector, cross the normal and S vector for an orthogonal axis
    const val DERIVE_UNSMOOTHED_BITANGENT = true

    //
    const val MAX_SIL_EDGES = 0x10000
    const val SILEDGE_HASH_SIZE = 1024

    /*
     ==============================================================================

     TRIANGLE MESH PROCESSING

     The functions in this file have no vertex / index count limits.

     Truly identical vertexes that match in position, normal, and texcoord can
     be merged away.

     Vertexes that match in position and texcoord, but have distinct normals will
     remain distinct for all purposes.  This is usually a poor choice for models,
     as adding a bevel face will not add any more vertexes, and will tend to
     look better.

     Match in position and normal, but differ in texcoords are referenced together
     for calculating tangent vectors for bump mapping.
     Artists should take care to have identical texels in all maps (bump/diffuse/specular)
     in this case

     Vertexes that only match in position are merged for shadow edge finding.

     Degenerate triangles.

     Overlapped triangles, even if normals or texcoords differ, must be removed.
     for the silhoette based stencil shadow algorithm to function properly.
     Is this true???
     Is the overlapped triangle problem just an example of the trippled edge problem?

     Interpenetrating triangles are not currently clipped to surfaces.
     Do they effect the shadows?

     if vertexes are intended to deform apart, make sure that no vertexes
     are on top of each other in the base frame, or the sil edges may be
     calculated incorrectly.

     We might be able to identify this from topology.

     Dangling edges are acceptable, but three way edges are not.

     Are any combinations of two way edges unacceptable, like one facing
     the backside of the other?


     Topology is determined by a collection of triangle indexes.

     The edge list can be built up from this, and stays valid even under
     deformations.

     Somewhat non-intuitively, concave edges cannot be optimized away, or the
     stencil shadow algorithm miscounts.

     Face normals are needed for generating shadow volumes and for calculating
     the silhouette, but they will change with any deformation.

     Vertex normals and vertex tangents will change with each deformation,
     but they may be able to be transformed instead of recalculated.

     bounding volume, both box and sphere will change with deformation.

     silhouette indexes
     shade indexes
     texture indexes

     shade indexes will only be > silhouette indexes if there is facet shading present

     lookups from texture to sil and texture to shade?

     The normal and tangent vector smoothing is simple averaging, no attempt is
     made to better handle the cases where the distribution around the shared vertex
     is highly uneven.


     we may get degenerate triangles even with the uniquing and removal
     if the vertexes have different texcoords.

     ==============================================================================
     */
    // this shouldn't change anything, but previously renderbumped models seem to need it
    const val USE_INVA = true

    //
    private const val ID_DEBUG_MEMORY = false

    /*
     =================
     R_DeriveTangentsWithoutNormals

     Build texture space tangents for bump mapping
     If a surface is deformed, this must be recalculated

     This assumes that any mirrored vertexes have already been duplicated, so
     any shared vertexes will have the tangent spaces smoothed across.

     Texture wrapping slightly complicates this, but as long as the normals
     are shared, and the tangent vectors are projected onto the normals, the
     separate vertexes should wind up with identical tangent spaces.

     mirroring a normalmap WILL cause a slightly visible seam unless the normals
     are completely flat around the edge's full bilerp support.

     Vertexes which are smooth shaded must have their tangent vectors
     in the same plane, which will allow a seamless
     rendering as long as the normal map is even on both sides of the
     seam.

     A smooth shaded surface may have multiple tangent vectors at a vertex
     due to texture seams or mirroring, but it should only have a single
     normal vector.

     Each triangle has a pair of tangent vectors in it's plane

     Should we consider having vertexes point at shared tangent spaces
     to save space or speed transforms?

     this version only handles bilateral symetry
     =================
     */
    var DEBUG_R_DeriveTangentsWithoutNormals = 0

    /*
     =================
     R_IdentifySilEdges

     If the surface will not deform, coplanar edges (polygon interiors)
     can never create silhouette plains, and can be omited
     =================
     */
    var c_coplanarSilEdges = 0

    /*
     ===============
     R_DefineEdge
     ===============
     */
    var c_duplicatedEdges = 0
    var c_tripledEdges = 0
    var c_totalSilEdges = 0

    //
    //    static final idBlockAlloc<srfTriangles_s> srfTrianglesAllocator = new idBlockAlloc<>(1 << 8);
    //
    //    static final idDynamicBlockAlloc<idDrawVert> triVertexAllocator;
    //    static final idDynamicBlockAlloc</*glIndex_t*/Integer> triIndexAllocator;
    //    static final idDynamicBlockAlloc<shadowCache_s> triShadowVertexAllocator;
    //    static final idDynamicBlockAlloc<idPlane> triPlaneAllocator;
    //    static final idDynamicBlockAlloc</*glIndex_t*/Integer> triSilIndexAllocator;
    //    static final idDynamicBlockAlloc<silEdge_t> triSilEdgeAllocator;
    //    static final idDynamicBlockAlloc<dominantTri_s> triDominantTrisAllocator;
    //    static final idDynamicBlockAlloc<Integer> triMirroredVertAllocator;
    //    static final idDynamicBlockAlloc<Integer> triDupVertAllocator;
    //
    //    static {
    //        if (USE_TRI_DATA_ALLOCATOR) {
    //            triVertexAllocator = new idDynamicBlockAlloc(1 << 20, 1 << 10);
    //            triIndexAllocator = new idDynamicBlockAlloc(1 << 18, 1 << 10);
    //            triShadowVertexAllocator = new idDynamicBlockAlloc(1 << 18, 1 << 10);
    //            triPlaneAllocator = new idDynamicBlockAlloc(1 << 17, 1 << 10);
    //            triSilIndexAllocator = new idDynamicBlockAlloc(1 << 17, 1 << 10);
    //            triSilEdgeAllocator = new idDynamicBlockAlloc(1 << 17, 1 << 10);
    //            triDominantTrisAllocator = new idDynamicBlockAlloc(1 << 16, 1 << 10);
    //            triMirroredVertAllocator = new idDynamicBlockAlloc(1 << 16, 1 << 10);
    //            triDupVertAllocator = new idDynamicBlockAlloc(1 << 16, 1 << 10);
    ////        } else {
    ////            triVertexAllocator = new idDynamicAlloc(1 << 20, 1 << 10);
    ////            triIndexAllocator = new idDynamicAlloc(1 << 18, 1 << 10);
    ////            triShadowVertexAllocator = new idDynamicAlloc(1 << 18, 1 << 10);
    ////            triPlaneAllocator = new idDynamicAlloc(1 << 17, 1 << 10);
    ////            triSilIndexAllocator = new idDynamicAlloc(1 << 17, 1 << 10);
    ////            triSilEdgeAllocator = new idDynamicAlloc(1 << 17, 1 << 10);
    ////            triDominantTrisAllocator = new idDynamicAlloc(1 << 16, 1 << 10);
    ////            triMirroredVertAllocator = new idDynamicAlloc(1 << 16, 1 << 10);
    ////            triDupVertAllocator = new idDynamicAlloc(1 << 16, 1 << 10);
    //        }
    //    }
    var numPlanes = 0

    //
    var numSilEdges = 0
    var silEdgeHash: idHashIndex? = idHashIndex(tr_trisurf.SILEDGE_HASH_SIZE, tr_trisurf.MAX_SIL_EDGES)
    var silEdges: Array<silEdge_t?>?

    /*
     ==============
     R_AllocStaticTriSurf
     ==============
     */
    private const val DBG_R_AllocStaticTriSurf = 0

    /*
     =================
     R_CleanupTriangles

     FIXME: allow createFlat and createSmooth normals, as well as explicit
     =================
     */
    private const val DBG_R_CleanupTriangles = 0

    /*
     ===============
     R_InitTriSurfData
     ===============
     */
    fun R_InitTriSurfData() {
        tr_trisurf.silEdges = silEdge_t.Companion.generateArray(tr_trisurf.MAX_SIL_EDGES)

//
//        // initialize allocators for triangle surfaces
//        triVertexAllocator.Init();
//        triIndexAllocator.Init();
//        triShadowVertexAllocator.Init();
//        triPlaneAllocator.Init();
//        triSilIndexAllocator.Init();
//        triSilEdgeAllocator.Init();
//        triDominantTrisAllocator.Init();
//        triMirroredVertAllocator.Init();
//        triDupVertAllocator.Init();
//
//        // never swap out triangle surfaces
//        triVertexAllocator.SetLockMemory(true);
//        triIndexAllocator.SetLockMemory(true);
//        triShadowVertexAllocator.SetLockMemory(true);
//        triPlaneAllocator.SetLockMemory(true);
//        triSilIndexAllocator.SetLockMemory(true);
//        triSilEdgeAllocator.SetLockMemory(true);
//        triDominantTrisAllocator.SetLockMemory(true);
//        triMirroredVertAllocator.SetLockMemory(true);
//        triDupVertAllocator.SetLockMemory(true);
    }

    /*
     ===============
     R_ShutdownTriSurfData
     ===============
     */
    fun R_ShutdownTriSurfData() {
        tr_trisurf.silEdges = null //R_StaticFree(silEdges);
        tr_trisurf.silEdgeHash.Free()
        //        srfTrianglesAllocator.Shutdown();
//        triVertexAllocator.Shutdown();
//        triIndexAllocator.Shutdown();
//        triShadowVertexAllocator.Shutdown();
//        triPlaneAllocator.Shutdown();
//        triSilIndexAllocator.Shutdown();
//        triSilEdgeAllocator.Shutdown();
//        triDominantTrisAllocator.Shutdown();
//        triMirroredVertAllocator.Shutdown();
//        triDupVertAllocator.Shutdown();
    }

    /*
     ===============
     R_PurgeTriSurfData
     ===============
     */
    fun R_PurgeTriSurfData(frame: frameData_t?) {
        // free deferred triangle surfaces
        tr_trisurf.R_FreeDeferredTriSurfs(frame)

        // free empty base blocks
//        triVertexAllocator.FreeEmptyBaseBlocks();
//        triIndexAllocator.FreeEmptyBaseBlocks();
//        triShadowVertexAllocator.FreeEmptyBaseBlocks();
//        triPlaneAllocator.FreeEmptyBaseBlocks();
//        triSilIndexAllocator.FreeEmptyBaseBlocks();
//        triSilEdgeAllocator.FreeEmptyBaseBlocks();
//        triDominantTrisAllocator.FreeEmptyBaseBlocks();
//        triMirroredVertAllocator.FreeEmptyBaseBlocks();
//        triDupVertAllocator.FreeEmptyBaseBlocks();
    }

    /*
     =================
     R_TriSurfMemory

     For memory profiling
     =================
     */
    fun R_TriSurfMemory(tri: srfTriangles_s?): Int {
        var total = 0
        if (null == tri) {
            return total
        }

        // used as a flag in interactions
        if (tri === Interaction.LIGHT_TRIS_DEFERRED) {
            return total
        }
        if (tri.shadowVertexes != null) {
            total += tri.numVerts //* sizeof( tri.shadowVertexes[0] );
        } else if (tri.verts != null) {
            if (tri.ambientSurface == null || tri.verts != tri.ambientSurface.verts) {
                total += tri.numVerts // * sizeof( tri.verts[0] );
            }
        }
        if (tri.facePlanes != null) {
            total += tri.numIndexes / 3 //* sizeof( tri.facePlanes[0] );
        }
        if (tri.indexes != null) {
            if (tri.ambientSurface == null || tri.indexes != tri.ambientSurface.indexes) {
                total += tri.numIndexes // * sizeof( tri.indexes[0] );
            }
        }
        if (tri.silIndexes != null) {
            total += tri.numIndexes //* sizeof( tri.silIndexes[0] );
        }
        if (tri.silEdges != null) {
            total += tri.numSilEdges * 4
        }
        if (tri.dominantTris != null) {
            total += tri.numVerts //* sizeof( tri.dominantTris[0] );
        }
        if (tri.mirroredVerts != null) {
            total += tri.numMirroredVerts //* sizeof( tri.mirroredVerts[0] );
        }
        if (tri.dupVerts != null) {
            total += tri.numDupVerts // * sizeof( tri.dupVerts[0] );
        }
        total += 4
        return total
    }

    fun R_TriSurfMemory(tri: Array<srfTriangles_s?>?): Int {
        throw UnsupportedOperationException()
    }

    /*
     ==============
     R_FreeStaticTriSurfVertexCaches
     ==============
     */
    fun R_FreeStaticTriSurfVertexCaches(tri: srfTriangles_s?) {
        if (tri.ambientSurface == null) {
            // this is a real model surface
            VertexCache.vertexCache.Free(tri.ambientCache)
            tri.ambientCache = null
        } else {
            // this is a light interaction surface that references
            // a different ambient model surface
            VertexCache.vertexCache.Free(tri.lightingCache)
            tri.lightingCache = null
        }
        if (tri.indexCache != null) {
            VertexCache.vertexCache.Free(tri.indexCache)
            tri.indexCache = null
        }
        if (tri.shadowCache != null && (tri.shadowVertexes != null || tri.verts != null)) {
            // if we don't have tri.shadowVertexes, these are a reference to a
            // shadowCache on the original surface, which a vertex program
            // will take care of making unique for each light
            VertexCache.vertexCache.Free(tri.shadowCache)
            tri.shadowCache = null
        }
    }

    /*
     ==============
     R_ReallyFreeStaticTriSurf

     This does the actual free
     ==============
     */
    fun R_ReallyFreeStaticTriSurf(tri: srfTriangles_s?) {
        var tri = tri ?: return
        tr_trisurf.R_FreeStaticTriSurfVertexCaches(tri)
        //
//        if (tri.verts != null) {
//            // R_CreateLightTris points tri.verts at the verts of the ambient surface
//            if (tri.ambientSurface == null || tri.verts != tri.ambientSurface.verts) {
//                triVertexAllocator.Free(tri.verts);
//            }
//        }
//
//        if (!tri.deformedSurface) {
//            if (tri.indexes != null) {
//                // if a surface is completely inside a light volume R_CreateLightTris points tri.indexes at the indexes of the ambient surface
//                if (tri.ambientSurface == null || tri.indexes != tri.ambientSurface.indexes) {
//                    triIndexAllocator.Free(tri.indexes);
//                }
//            }
//            if (tri.silIndexes != null) {
//                triSilIndexAllocator.Free(tri.silIndexes);
//            }
//            if (tri.silEdges != null) {
//                triSilEdgeAllocator.Free(tri.silEdges);
//            }
//            if (tri.dominantTris != null) {
//                triDominantTrisAllocator.Free(tri.dominantTris);
//            }
//            if (tri.mirroredVerts != null) {
//                triMirroredVertAllocator.Free(tri.mirroredVerts);
//            }
//            if (tri.dupVerts != null) {
//                triDupVertAllocator.Free(tri.dupVerts);
//            }
//        }
//
//        if (tri.facePlanes != null) {
//            triPlaneAllocator.Free(tri.facePlanes);
//        }
//
//        if (tri.shadowVertexes != null) {
//            triShadowVertexAllocator.Free(tri.shadowVertexes);
//        }
        if (BuildDefines._DEBUG) {
//            memset(tri, 0, sizeof(srfTriangles_t));
            tri = srfTriangles_s()
        }
        //
//        srfTrianglesAllocator.Free(tri);
    }

    /*
     ==============
     R_CheckStaticTriSurfMemory
     ==============
     */
    fun R_CheckStaticTriSurfMemory(tri: srfTriangles_s?) {
        if (null == tri) {
            return
        }
        //
//        if (tri.verts != null) {
//            // R_CreateLightTris points tri.verts at the verts of the ambient surface
//            if (tri.ambientSurface == null || tri.verts != tri.ambientSurface.verts) {
//                final String error = triVertexAllocator.CheckMemory(tri.verts);
//                assert (error == null);
//            }
//        }
//
//        if (!tri.deformedSurface) {
//            if (tri.indexes != null) {
//                // if a surface is completely inside a light volume R_CreateLightTris points tri.indexes at the indexes of the ambient surface
//                if (tri.ambientSurface == null || tri.indexes != tri.ambientSurface.indexes) {
//                    final String error = triIndexAllocator.CheckMemory(tri.indexes);
//                    assert (error == null);
//                }
//            }
//        }
//
//        if (tri.shadowVertexes != null) {
//            final String error = triShadowVertexAllocator.CheckMemory(tri.shadowVertexes);
//            assert (error == null);
//        }
    }

    /*
     ==================
     R_FreeDeferredTriSurfs
     ==================
     */
    fun R_FreeDeferredTriSurfs(frame: frameData_t?) {
        var tri: srfTriangles_s?
        var next: srfTriangles_s?
        if (null == frame) {
            return
        }
        tri = frame.firstDeferredFreeTriSurf
        while (tri != null) {
            next = tri.nextDeferredFree
            tr_trisurf.R_ReallyFreeStaticTriSurf(tri)
            tri = next
        }
        frame.firstDeferredFreeTriSurf = null
        frame.lastDeferredFreeTriSurf = null
    }

    /*
     ==============
     R_FreeStaticTriSurf

     This will defer the free until the current frame has run through the back end.
     ==============
     */
    fun R_FreeStaticTriSurf(tri: srfTriangles_s?) {
        val frame: frameData_t?
        if (null == tri) {
            return
        }
        if (tri.nextDeferredFree != null) {
            Common.common.Error("R_FreeStaticTriSurf: freed a freed triangle")
        }
        frame = tr_local.frameData
        if (TempDump.NOT(frame)) {
            // command line utility, or rendering in editor preview mode ( force )
            tr_trisurf.R_ReallyFreeStaticTriSurf(tri)
        } else {
            if (tr_trisurf.ID_DEBUG_MEMORY) {
                tr_trisurf.R_CheckStaticTriSurfMemory(tri)
            }
            tri.nextDeferredFree = null
            if (frame.lastDeferredFreeTriSurf != null) {
                frame.lastDeferredFreeTriSurf.nextDeferredFree = tri
            } else {
                frame.firstDeferredFreeTriSurf = tri
            }
            frame.lastDeferredFreeTriSurf = tri
        }
    }

    fun R_FreeStaticTriSurf(tri: Array<srfTriangles_s?>?) {
        throw UnsupportedOperationException()
    }

    @Deprecated("")
    fun R_AllocStaticTriSurf(): srfTriangles_s? {
        tr_trisurf.DBG_R_AllocStaticTriSurf++
        //        srfTriangles_s tris = srfTrianglesAllocator.Alloc();
//        memset(tris, 0, sizeof(srfTriangles_t));
        return srfTriangles_s()
    }

    /*
     =================
     R_CopyStaticTriSurf

     This only duplicates the indexes and verts, not any of the derived data.
     =================
     */
    fun R_CopyStaticTriSurf(tri: srfTriangles_s?): srfTriangles_s? {
        val newTri: srfTriangles_s?
        newTri = tr_trisurf.R_AllocStaticTriSurf()
        tr_trisurf.R_AllocStaticTriSurfVerts(newTri, tri.numVerts)
        tr_trisurf.R_AllocStaticTriSurfIndexes(newTri, tri.numIndexes)
        newTri.numVerts = tri.numVerts
        newTri.numIndexes = tri.numIndexes
        //	memcpy( newTri.verts, tri.verts, tri.numVerts * sizeof( newTri.verts[0] ) );
        for (i in 0 until tri.numVerts) {
            newTri.verts[i] = idDrawVert(tri.verts[i])
        }
        //	memcpy( newTri.indexes, tri.indexes, tri.numIndexes * sizeof( newTri.indexes[0] ) );
        System.arraycopy(tri.indexes, 0, newTri.indexes, 0, tri.numIndexes)
        return newTri
    }

    /*
     =================
     R_AllocStaticTriSurfVerts
     =================
     */
    fun R_AllocStaticTriSurfVerts(tri: srfTriangles_s?, numVerts: Int) {
        assert(tri.verts == null)
        tri.verts = arrayOfNulls(numVerts) //triVertexAllocator.Alloc(numVerts);
        for (a in tri.verts.indices) {
            tri.verts[a] = idDrawVert()
        }
    }

    /*
     =================
     R_AllocStaticTriSurfIndexes
     =================
     */
    fun R_AllocStaticTriSurfIndexes(tri: srfTriangles_s?, numIndexes: Int) {
        assert(tri.indexes == null)
        tri.indexes = IntArray(numIndexes) // triIndexAllocator.Alloc(numIndexes);
    }

    /*
     =================
     R_AllocStaticTriSurfShadowVerts
     =================
     */
    fun R_AllocStaticTriSurfShadowVerts(tri: srfTriangles_s?, numVerts: Int) {
        assert(tri.shadowVertexes == null)
        tri.shadowVertexes = shadowCache_s.Companion.generateArray(numVerts) //triShadowVertexAllocator.Alloc(numVerts);
    }

    /*
     =================
     R_AllocStaticTriSurfPlanes
     =================
     */
    fun R_AllocStaticTriSurfPlanes(tri: srfTriangles_s?, numIndexes: Int) {
        tri.facePlanes = idPlane.Companion.generateArray(numIndexes / 3) //triPlaneAllocator.Alloc(numIndexes / 3);
    }

    /*
     =================
     R_ResizeStaticTriSurfVerts
     =================
     */
    fun R_ResizeStaticTriSurfVerts(tri: srfTriangles_s?, numVerts: Int) {
        if (tr_local.USE_TRI_DATA_ALLOCATOR) {
            tri.verts =  /*triVertexAllocator.*/tr_trisurf.Resize(tri.verts, numVerts)
        } else {
            assert(false)
        }
    }

    /*
     =================
     R_ResizeStaticTriSurfIndexes
     =================
     */
    fun R_ResizeStaticTriSurfIndexes(tri: srfTriangles_s?, numIndexes: Int) {
        if (tr_local.USE_TRI_DATA_ALLOCATOR) {
            tri.indexes =  /*triIndexAllocator.*/tr_trisurf.Resize(tri.indexes, numIndexes)
        } else {
            assert(false)
        }
    }

    /*
     =================
     R_ResizeStaticTriSurfShadowVerts
     =================
     */
    fun R_ResizeStaticTriSurfShadowVerts(tri: srfTriangles_s?, numVerts: Int) {
        if (tr_local.USE_TRI_DATA_ALLOCATOR) {
            tri.shadowVertexes =  /*triShadowVertexAllocator.*/tr_trisurf.Resize(tri.shadowVertexes, numVerts)
        } else {
            assert(false)
        }
    }

    /*
     =================
     R_ReferenceStaticTriSurfVerts
     =================
     */
    fun R_ReferenceStaticTriSurfVerts(tri: srfTriangles_s?, reference: srfTriangles_s?) {
        tri.verts = reference.verts
    }

    /*
     =================
     R_ReferenceStaticTriSurfIndexes
     =================
     */
    fun R_ReferenceStaticTriSurfIndexes(tri: srfTriangles_s?, reference: srfTriangles_s?) {
        tri.indexes = reference.indexes
    }

    /*
     =================
     R_FreeStaticTriSurfSilIndexes
     =================
     */
    fun R_FreeStaticTriSurfSilIndexes(tri: srfTriangles_s?) {
//        triSilIndexAllocator.Free(tri.silIndexes);
        tri.silIndexes = null
    }

    /*
     ===============
     R_RangeCheckIndexes

     Check for syntactically incorrect indexes, like out of range values.
     Does not check for semantics, like degenerate triangles.

     No vertexes is acceptable if no indexes.
     No indexes is acceptable.
     More vertexes than are referenced by indexes are acceptable.
     ===============
     */
    fun R_RangeCheckIndexes(tri: srfTriangles_s?) {
        var i: Int
        if (tri.numIndexes < 0) {
            Common.common.Error("R_RangeCheckIndexes: numIndexes < 0")
        }
        if (tri.numVerts < 0) {
            Common.common.Error("R_RangeCheckIndexes: numVerts < 0")
        }

        // must specify an integral number of triangles
        if (tri.numIndexes % 3 != 0) {
            Common.common.Error("R_RangeCheckIndexes: numIndexes %% 3")
        }
        i = 0
        while (i < tri.numIndexes) {
            if (tri.indexes[i] < 0 || tri.indexes[i] >= tri.numVerts) {
                Common.common.Error("R_RangeCheckIndexes: index out of range")
            }
            i++
        }

        // this should not be possible unless there are unused verts
        if (tri.numVerts > tri.numIndexes) {
            // FIXME: find the causes of these
            // common.Printf( "R_RangeCheckIndexes: tri.numVerts > tri.numIndexes\n" );
        }
    }

    /*
     =================
     R_BoundTriSurf
     =================
     */
    fun R_BoundTriSurf(tri: srfTriangles_s?) {
        Simd.SIMDProcessor.MinMax(tri.bounds.oGet(0), tri.bounds.oGet(1), tri.verts, tri.numVerts)
    }

    /*
     =================
     R_CreateSilRemap
     =================
     */
    fun R_CreateSilRemap(tri: srfTriangles_s?): IntArray? {
        var c_removed: Int
        var c_unique: Int
        val remap: IntArray
        var i: Int
        var j: Int
        var hashKey: Int
        var v1: idDrawVert?
        var v2: idDrawVert?
        remap = IntArray(tri.numVerts) // R_ClearedStaticAlloc(tri.numVerts);
        if (!RenderSystem_init.r_useSilRemap.GetBool()) {
            i = 0
            while (i < tri.numVerts) {
                remap[i] = i
                i++
            }
            return remap
        }
        val hash = idHashIndex(1024, tri.numVerts)
        c_removed = 0
        c_unique = 0
        i = 0
        while (i < tri.numVerts) {
            v1 = tri.verts[i]

            // see if there is an earlier vert that it can map to
            hashKey = hash.GenerateKey(v1.xyz)
            j = hash.First(hashKey)
            while (j >= 0) {
                v2 = tri.verts[j]
                if (v2.xyz.oGet(0) == v1.xyz.oGet(0) && v2.xyz.oGet(1) == v1.xyz.oGet(1) && v2.xyz.oGet(2) == v1.xyz.oGet(
                        2
                    )
                ) {
                    c_removed++
                    remap[i] = j
                    break
                }
                j = hash.Next(j)
            }
            if (j < 0) {
                c_unique++
                remap[i] = i
                hash.Add(hashKey, i)
            }
            i++
        }
        return remap
    }

    /*
     =================
     R_CreateSilIndexes

     Uniquing vertexes only on xyz before creating sil edges reduces
     the edge count by about 20% on Q3 models
     =================
     */
    fun R_CreateSilIndexes(tri: srfTriangles_s?) {
        var i: Int
        val remap: IntArray?
        if (tri.silIndexes != null) {
//            triSilIndexAllocator.Free(tri.silIndexes);
            tri.silIndexes = null
        }
        remap = tr_trisurf.R_CreateSilRemap(tri)

        // remap indexes to the first one
        tri.silIndexes = IntArray(tri.numIndexes) //triSilIndexAllocator.Alloc(tri.numIndexes);
        i = 0
        while (i < tri.numIndexes) {
            tri.silIndexes[i] = remap[tri.indexes[i]]
            i++
        }

//        R_StaticFree(remap);
    }

    /*
     =====================
     R_CreateDupVerts
     =====================
     */
    fun R_CreateDupVerts(tri: srfTriangles_s?) {
        var i: Int
        val remap = IntArray(tri.numVerts)

        // initialize vertex remap in case there are unused verts
        i = 0
        while (i < tri.numVerts) {
            remap[i] = i
            i++
        }

        // set the remap based on how the silhouette indexes are remapped
        i = 0
        while (i < tri.numIndexes) {
            remap[tri.indexes[i]] = tri.silIndexes[i]
            i++
        }

        // create duplicate vertex index based on the vertex remap
        val tempDupVerts = IntArray(tri.numVerts * 2)
        tri.numDupVerts = 0
        i = 0
        while (i < tri.numVerts) {
            if (remap[i] != i) {
                tempDupVerts[tri.numDupVerts * 2 + 0] = i
                tempDupVerts[tri.numDupVerts * 2 + 1] = remap[i]
                tri.numDupVerts++
            }
            i++
        }
        tri.dupVerts = IntArray(tri.numDupVerts * 2) // triDupVertAllocator.Alloc(tri.numDupVerts * 2);
        //	memcpy( tri.dupVerts, tempDupVerts, tri.numDupVerts * 2 * sizeof( tri.dupVerts[0] ) );
        System.arraycopy(tempDupVerts, 0, tri.dupVerts, 0, tri.numDupVerts * 2)
    }

    /*
     =====================
     R_DeriveFacePlanes

     Writes the facePlanes values, overwriting existing ones if present
     =====================
     */
    fun R_DeriveFacePlanes(tri: srfTriangles_s?) {
        val planes: Array<idPlane?>?
        if (null == tri.facePlanes) {
            tr_trisurf.R_AllocStaticTriSurfPlanes(tri, tri.numIndexes)
        }
        planes = tri.facePlanes
        if (true) {
            Simd.SIMDProcessor.DeriveTriPlanes(planes, tri.verts, tri.numVerts, tri.indexes, tri.numIndexes)
        }
        tri.facePlanesCalculated = true
    }

    /*
     =====================
     R_CreateVertexNormals

     Averages together the contributions of all faces that are
     used by a vertex, creating drawVert.normal
     =====================
     */
    fun R_CreateVertexNormals(tri: srfTriangles_s?) {
        var i: Int
        var j: Int
        var p: Int
        var plane: idPlane?
        i = 0
        while (i < tri.numVerts) {
            tri.verts[i].normal.Zero()
            i++
        }
        if (null == tri.facePlanes || !tri.facePlanesCalculated) {
            tr_trisurf.R_DeriveFacePlanes(tri)
        }
        if (null == tri.silIndexes) {
            tr_trisurf.R_CreateSilIndexes(tri)
        }
        plane = tri.facePlanes[0.also { p = it }]
        i = 0
        while (i < tri.numIndexes) {
            j = 0
            while (j < 3) {
                val index = tri.silIndexes[i + j]
                tri.verts[index].normal.oPluSet(plane.Normal())
                j++
            }
            i += 3
            plane = tri.facePlanes[++p]
        }

        // normalize and replicate from silIndexes to all indexes
        i = 0
        while (i < tri.numIndexes) {
            tri.verts[tri.indexes[i]].normal.oSet(tri.verts[tri.silIndexes[i]].normal)
            tri.verts[tri.indexes[i]].normal.Normalize()
            i++
        }
    }

    fun R_DefineEdge(v1: Int, v2: Int, planeNum: Int) {
        var i: Int
        val hashKey: Int

        // check for degenerate edge
        if (v1 == v2) {
            return
        }
        hashKey = tr_trisurf.silEdgeHash.GenerateKey(v1, v2)
        // search for a matching other side
        i = tr_trisurf.silEdgeHash.First(hashKey)
        while (i >= 0 && i < tr_trisurf.MAX_SIL_EDGES) {
            if (tr_trisurf.silEdges[i].v1 == v1 && tr_trisurf.silEdges[i].v2 == v2) {
                tr_trisurf.c_duplicatedEdges++
                i = tr_trisurf.silEdgeHash.Next(i)
                // allow it to still create a new edge
                continue
            }
            if (tr_trisurf.silEdges[i].v2 == v1 && tr_trisurf.silEdges[i].v1 == v2) {
                if (tr_trisurf.silEdges[i].p2 != tr_trisurf.numPlanes) {
                    tr_trisurf.c_tripledEdges++
                    i = tr_trisurf.silEdgeHash.Next(i)
                    // allow it to still create a new edge
                    continue
                }
                // this is a matching back side
                tr_trisurf.silEdges[i].p2 = planeNum
                return
            }
            i = tr_trisurf.silEdgeHash.Next(i)
        }

        // define the new edge
        if (tr_trisurf.numSilEdges == tr_trisurf.MAX_SIL_EDGES) {
            Common.common.DWarning("MAX_SIL_EDGES")
            return
        }
        tr_trisurf.silEdgeHash.Add(hashKey, tr_trisurf.numSilEdges)
        tr_trisurf.silEdges[tr_trisurf.numSilEdges].p1 = planeNum
        tr_trisurf.silEdges[tr_trisurf.numSilEdges].p2 = tr_trisurf.numPlanes
        tr_trisurf.silEdges[tr_trisurf.numSilEdges].v1 = v1
        tr_trisurf.silEdges[tr_trisurf.numSilEdges].v2 = v2
        tr_trisurf.numSilEdges++
    }

    fun R_IdentifySilEdges(tri: srfTriangles_s?, omitCoplanarEdges: Boolean) {
        var omitCoplanarEdges = omitCoplanarEdges
        var i: Int
        val numTris: Int
        var shared: Int
        var single: Int
        omitCoplanarEdges = false // optimization doesn't work for some reason
        numTris = tri.numIndexes / 3
        tr_trisurf.numSilEdges = 0
        tr_trisurf.silEdgeHash.Clear()
        tr_trisurf.numPlanes = numTris
        tr_trisurf.c_duplicatedEdges = 0
        tr_trisurf.c_tripledEdges = 0
        i = 0
        while (i < numTris) {
            var i1: Int
            var i2: Int
            var i3: Int
            i1 = tri.silIndexes[i * 3 + 0]
            i2 = tri.silIndexes[i * 3 + 1]
            i3 = tri.silIndexes[i * 3 + 2]

            // create the edges
            tr_trisurf.R_DefineEdge(i1, i2, i)
            tr_trisurf.R_DefineEdge(i2, i3, i)
            tr_trisurf.R_DefineEdge(i3, i1, i)
            i++
        }
        if (tr_trisurf.c_duplicatedEdges != 0 || tr_trisurf.c_tripledEdges != 0) {
            Common.common.DWarning(
                "%d duplicated edge directions, %d tripled edges",
                tr_trisurf.c_duplicatedEdges,
                tr_trisurf.c_tripledEdges
            )
        }

        // if we know that the vertexes aren't going
        // to deform, we can remove interior triangulation edges
        // on otherwise planar polygons.
        // I earlier believed that I could also remove concave
        // edges, because they are never silhouettes in the conventional sense,
        // but they are still needed to balance out all the true sil edges
        // for the shadow algorithm to function
        var c_coplanarCulled: Int
        c_coplanarCulled = 0
        if (omitCoplanarEdges) {
            i = 0
            while (i < tr_trisurf.numSilEdges) {
                var i1: Int
                var i2: Int
                var i3: Int
                val plane = idPlane()
                var base: Int
                var j: Int
                var d: Float
                if (tr_trisurf.silEdges[i].p2 == tr_trisurf.numPlanes) {    // the fake dangling edge
                    i++
                    continue
                }
                base = tr_trisurf.silEdges[i].p1 * 3
                i1 = tri.silIndexes[base + 0]
                i2 = tri.silIndexes[base + 1]
                i3 = tri.silIndexes[base + 2]
                plane.FromPoints(tri.verts[i1].xyz, tri.verts[i2].xyz, tri.verts[i3].xyz)

                // check to see if points of second triangle are not coplanar
                base = tr_trisurf.silEdges[i].p2 * 3
                j = 0
                while (j < 3) {
                    i1 = tri.silIndexes[base + j]
                    d = plane.Distance(tri.verts[i1].xyz)
                    if (d != 0f) {        // even a small epsilon causes problems
                        break
                    }
                    j++
                }
                if (j == 3) {
                    // we can cull this sil edge
//				memmove( &silEdges[i], &silEdges[i+1], (numSilEdges-i-1) * sizeof( silEdges[i] ) );
                    for (k in i until tr_trisurf.numSilEdges - i - 1) {
                        tr_trisurf.silEdges[i] = silEdge_t(tr_trisurf.silEdges[i + 1])
                    }
                    c_coplanarCulled++
                    tr_trisurf.numSilEdges--
                    i--
                }
                i++
            }
            if (c_coplanarCulled != 0) { //TODO:should it be >0?
                tr_trisurf.c_coplanarSilEdges += c_coplanarCulled
                //			common.Printf( "%i of %i sil edges coplanar culled\n", c_coplanarCulled,
//				c_coplanarCulled + numSilEdges );
            }
        }
        tr_trisurf.c_totalSilEdges += tr_trisurf.numSilEdges

        // sort the sil edges based on plane number
//        qsort(silEdges, numSilEdges, sizeof(silEdges[0]), SilEdgeSort);
        Arrays.sort(tr_trisurf.silEdges, 0, tr_trisurf.numSilEdges, SilEdgeSort())

        // count up the distribution.
        // a perfectly built model should only have shared
        // edges, but most models will have some interpenetration
        // and dangling edges
        shared = 0
        single = 0
        i = 0
        while (i < tr_trisurf.numSilEdges) {
            if (tr_trisurf.silEdges[i].p2 == tr_trisurf.numPlanes) {
                single++
            } else {
                shared++
            }
            i++
        }
        tri.perfectHull = single != 0
        tri.numSilEdges = tr_trisurf.numSilEdges
        tri.silEdges = arrayOfNulls(tr_trisurf.numSilEdges)
        i = 0
        while (i < tri.numSilEdges) {
            tri.silEdges[i] = silEdge_t(tr_trisurf.silEdges[i])
            i++
        }
    }

    /*
     ===============
     R_FaceNegativePolarity

     Returns true if the texture polarity of the face is negative, false if it is positive or zero
     ===============
     */
    fun R_FaceNegativePolarity(tri: srfTriangles_s?, firstIndex: Int): Boolean {
        val a: idDrawVert?
        val b: idDrawVert?
        val c: idDrawVert?
        val area: Float
        val d0 = FloatArray(5)
        val d1 = FloatArray(5)
        a = tri.verts[tri.indexes[firstIndex + 0]]
        b = tri.verts[tri.indexes[firstIndex + 1]]
        c = tri.verts[tri.indexes[firstIndex + 2]]
        d0[3] = b.st.oGet(0) - a.st.oGet(0)
        d0[4] = b.st.oGet(1) - a.st.oGet(1)
        d1[3] = c.st.oGet(0) - a.st.oGet(0)
        d1[4] = c.st.oGet(1) - a.st.oGet(1)
        area = d0[3] * d1[4] - d0[4] * d1[3]
        return area < 0
    }

    fun R_DeriveFaceTangents(tri: srfTriangles_s?, faceTangents: Array<faceTangents_t?>?) {
        var i: Int
        var c_textureDegenerateFaces: Int
        var c_positive: Int
        var c_negative: Int
        var ft: faceTangents_t?
        var a: idDrawVert?
        var b: idDrawVert?
        var c: idDrawVert?

        //
        // calculate tangent vectors for each face in isolation
        //
        c_positive = 0
        c_negative = 0
        c_textureDegenerateFaces = 0
        i = 0
        while (i < tri.numIndexes) {
            var area: Float
            val temp = idVec3()
            val d0 = FloatArray(5)
            val d1 = FloatArray(5)
            ft = faceTangents.get(i / 3)
            a = tri.verts[tri.indexes[i + 0]]
            b = tri.verts[tri.indexes[i + 1]]
            c = tri.verts[tri.indexes[i + 2]]
            d0[0] = b.xyz.oGet(0) - a.xyz.oGet(0)
            d0[1] = b.xyz.oGet(1) - a.xyz.oGet(1)
            d0[2] = b.xyz.oGet(2) - a.xyz.oGet(2)
            d0[3] = b.st.oGet(0) - a.st.oGet(0)
            d0[4] = b.st.oGet(1) - a.st.oGet(1)
            d1[0] = c.xyz.oGet(0) - a.xyz.oGet(0)
            d1[1] = c.xyz.oGet(1) - a.xyz.oGet(1)
            d1[2] = c.xyz.oGet(2) - a.xyz.oGet(2)
            d1[3] = c.st.oGet(0) - a.st.oGet(0)
            d1[4] = c.st.oGet(1) - a.st.oGet(1)
            area = d0[3] * d1[4] - d0[4] * d1[3]
            if (Math.abs(area) < 1e-20f) {
                ft.negativePolarity = false
                ft.degenerate = true
                ft.tangents.get(0).Zero()
                ft.tangents.get(1).Zero()
                c_textureDegenerateFaces++
                i += 3
                continue
            }
            if (area > 0.0f) {
                ft.negativePolarity = false
                c_positive++
            } else {
                ft.negativePolarity = true
                c_negative++
            }
            ft.degenerate = false
            if (tr_trisurf.USE_INVA) {
                val inva: Float = if (area < .0f) -1 else 1.toFloat() // was = 1.0f / area;
                temp.oSet(
                    idVec3(
                        (d0[0] * d1[4] - d0[4] * d1[0]) * inva,
                        (d0[1] * d1[4] - d0[4] * d1[1]) * inva,
                        (d0[2] * d1[4] - d0[4] * d1[2]) * inva
                    )
                )
                temp.Normalize()
                ft.tangents.get(0).oSet(temp)
                temp.oSet(
                    idVec3(
                        (d0[3] * d1[0] - d0[0] * d1[3]) * inva,
                        (d0[3] * d1[1] - d0[1] * d1[3]) * inva,
                        (d0[3] * d1[2] - d0[2] * d1[3]) * inva
                    )
                )
                temp.Normalize()
                ft.tangents.get(1).oSet(temp)
            } else {
                temp.oSet(
                    idVec3(
                        d0[0] * d1[4] - d0[4] * d1[0],
                        d0[1] * d1[4] - d0[4] * d1[1],
                        d0[2] * d1[4] - d0[4] * d1[2]
                    )
                )
                temp.Normalize()
                ft.tangents.get(0).oSet(temp)
                temp.oSet(
                    idVec3(
                        d0[3] * d1[0] - d0[0] * d1[3],
                        d0[3] * d1[1] - d0[1] * d1[3],
                        d0[3] * d1[2] - d0[2] * d1[3]
                    )
                )
                temp.Normalize()
                ft.tangents.get(1).oSet(temp)
            }
            i += 3
        }
    }

    fun R_DuplicateMirroredVertexes(tri: srfTriangles_s?) {
        val tVerts: Array<tangentVert_t?>
        var vert: tangentVert_t?
        var i: Int
        var j: Int
        var totalVerts: Int
        var numMirror: Int
        tVerts = arrayOfNulls<tangentVert_t?>(tri.numVerts)
        for (t in tVerts.indices) {
//	memset( tverts, 0, tri.numVerts * sizeof( *tverts ) );
            tVerts[t] = tangentVert_t()
        }

        // determine texture polarity of each surface
        // mark each vert with the polarities it uses
        i = 0
        while (i < tri.numIndexes) {
            var polarity: Int
            polarity = TempDump.btoi(tr_trisurf.R_FaceNegativePolarity(tri, i))
            j = 0
            while (j < 3) {
                tVerts[tri.indexes[i + j]].polarityUsed.get(polarity) = true
                j++
            }
            i += 3
        }

        // now create new verts as needed
        totalVerts = tri.numVerts
        i = 0
        while (i < tri.numVerts) {
            vert = tVerts[i]
            if (vert.polarityUsed.get(0) && vert.polarityUsed.get(1)) {
                vert.negativeRemap = totalVerts
                totalVerts++
            }
            i++
        }
        tri.numMirroredVerts = totalVerts - tri.numVerts

        // now create the new list
        if (totalVerts == tri.numVerts) {
            tri.mirroredVerts = null
            return
        }
        tri.mirroredVerts = IntArray(tri.numMirroredVerts) //triMirroredVertAllocator.Alloc(tri.numMirroredVerts);
        if (tr_local.USE_TRI_DATA_ALLOCATOR) {
            tri.verts =  /*triVertexAllocator.*/tr_trisurf.Resize(tri.verts, totalVerts)
        } else {
            val oldVerts = tri.verts
            tr_trisurf.R_AllocStaticTriSurfVerts(tri, totalVerts)
            //	memcpy( tri.verts, oldVerts, tri.numVerts * sizeof( tri.verts[0] ) );
            i = 0
            while (i < tri.numVerts) {
                tri.verts[i] = idDrawVert(oldVerts[i])
                i++
            }
            //            triVertexAllocator.Free(oldVerts);
        }

        // create the duplicates
        numMirror = 0
        i = 0
        while (i < tri.numVerts) {
            j = tVerts[i].negativeRemap
            if (j != 0) {
                tri.verts[j] = idDrawVert(tri.verts[i])
                tri.mirroredVerts[numMirror] = i
                numMirror++
            }
            i++
        }
        tri.numVerts = totalVerts
        // change the indexes
        i = 0
        while (i < tri.numIndexes) {
            if (tVerts[tri.indexes[i]].negativeRemap != 0
                && tr_trisurf.R_FaceNegativePolarity(tri, 3 * (i / 3))
            ) {
                tri.indexes[i] = tVerts[tri.indexes[i]].negativeRemap
            }
            i++
        }
        tri.numVerts = totalVerts
    }

    fun R_DeriveTangentsWithoutNormals(tri: srfTriangles_s?) {
        var i: Int
        var j: Int
        val faceTangents: Array<faceTangents_t?>?
        var ft: faceTangents_t?
        var vert: idDrawVert?
        faceTangents = faceTangents_t.generateArray(tri.numIndexes / 3)
        tr_trisurf.R_DeriveFaceTangents(tri, faceTangents)

        // clear the tangents
        i = 0
        while (i < tri.numVerts) {
            tri.verts[i].tangents[0].Zero()
            tri.verts[i].tangents[1].Zero()
            i++
        }

        // sum up the neighbors
        i = 0
        while (i < tri.numIndexes) {
            ft = faceTangents.get(i / 3)

            // for each vertex on this face
            j = 0
            while (j < 3) {
                tr_trisurf.DEBUG_R_DeriveTangentsWithoutNormals++
                vert = tri.verts[tri.indexes[i + j]]

//                System.out.println("--" + System.identityHashCode(vert.tangents[0])
//                        + "--" + i + j
//                        + "--" + tri.indexes[i + j]);
                vert.tangents[0].oPluSet(ft.tangents.get(0))
                vert.tangents[1].oPluSet(ft.tangents.get(1))
                j++
            }
            i += 3
        }

//if (false){
//	// sum up both sides of the mirrored verts
//	// so the S vectors exactly mirror, and the T vectors are equal
//	for ( i = 0 ; i < tri.numMirroredVerts ; i++ ) {
//		idDrawVert	v1, v2;
//
//		v1 = tri.verts[ tri.numVerts - tri.numMirroredVerts + i ];
//		v2 = tri.verts[ tri.mirroredVerts[i] ];
//
//		v1.tangents[0] -= v2.tangents[0];
//		v1.tangents[1] += v2.tangents[1];
//
//		v2.tangents[0] = vec3_origin - v1.tangents[0];
//		v2.tangents[1] = v1.tangents[1];
//	}
//}
        // project the summed vectors onto the normal plane
        // and normalize.  The tangent vectors will not necessarily
        // be orthogonal to each other, but they will be orthogonal
        // to the surface normal.
        i = 0
        while (i < tri.numVerts) {
            vert = tri.verts[i]
            j = 0
            while (j < 2) {
                var d: Float
                d = vert.tangents[j].oMultiply(vert.normal)
                vert.tangents[j] = vert.tangents[j].oMinus(vert.normal.oMultiply(d))
                vert.tangents[j].Normalize()
                j++
            }
            i++
        }
        tri.tangentsCalculated = true
    }

    fun  /*ID_INLINE*/VectorNormalizeFast2(v: idVec3?, out: idVec3?) {
        val length: Float
        length = idMath.RSqrt(v.oGet(0) * v.oGet(0) + v.oGet(1) * v.oGet(1) + v.oGet(2) * v.oGet(2))
        out.oSet(0, v.oGet(0) * length)
        out.oSet(1, v.oGet(1) * length)
        out.oSet(2, v.oGet(2) * length)
    }

    fun R_BuildDominantTris(tri: srfTriangles_s?) {
        var i: Int
        var j: Int
        val dt: Array<dominantTri_s?>
        val ind = arrayOfNulls<indexSort_t?>(tri.numIndexes) // R_StaticAlloc(tri.numIndexes);
        i = 0
        while (i < tri.numIndexes) {
            ind[i] = indexSort_t()
            ind[i].vertexNum = tri.indexes[i]
            ind[i].faceNum = i / 3
            i++
        }
        //        qsort(ind, tri.numIndexes, sizeof(ind[]), IndexSort);
        Arrays.sort(ind, 0, tri.numIndexes, IndexSort())
        dt = arrayOfNulls<dominantTri_s?>(tri.numVerts)
        tri.dominantTris = dt // triDominantTrisAllocator.Alloc(tri.numVerts);
        //	memset( dt, 0, tri.numVerts * sizeof( dt[0] ) );
        i = 0
        while (i < tri.numIndexes) {
            var maxArea = 0f
            val vertNum = ind[i].vertexNum
            j = 0
            while (i + j < tri.numIndexes && ind[i + j].vertexNum == vertNum) {
                val d0 = FloatArray(5)
                val d1 = FloatArray(5)
                var a: idDrawVert?
                var b: idDrawVert?
                var c: idDrawVert?
                val normal = idVec3()
                val tangent = idVec3()
                val bitangent = idVec3()
                val i1 = tri.indexes[ind[i + j].faceNum * 3 + 0]
                val i2 = tri.indexes[ind[i + j].faceNum * 3 + 1]
                val i3 = tri.indexes[ind[i + j].faceNum * 3 + 2]
                a = tri.verts[i1]
                b = tri.verts[i2]
                c = tri.verts[i3]
                d0[0] = b.xyz.oGet(0) - a.xyz.oGet(0)
                d0[1] = b.xyz.oGet(1) - a.xyz.oGet(1)
                d0[2] = b.xyz.oGet(2) - a.xyz.oGet(2)
                d0[3] = b.st.oGet(0) - a.st.oGet(0)
                d0[4] = b.st.oGet(1) - a.st.oGet(1)
                d1[0] = c.xyz.oGet(0) - a.xyz.oGet(0)
                d1[1] = c.xyz.oGet(1) - a.xyz.oGet(1)
                d1[2] = c.xyz.oGet(2) - a.xyz.oGet(2)
                d1[3] = c.st.oGet(0) - a.st.oGet(0)
                d1[4] = c.st.oGet(1) - a.st.oGet(1)
                normal.oSet(0, d1[1] * d0[2] - d1[2] * d0[1])
                normal.oSet(1, d1[2] * d0[0] - d1[0] * d0[2])
                normal.oSet(2, d1[0] * d0[1] - d1[1] * d0[0])
                var area = normal.Length()

                // if this is smaller than what we already have, skip it
                if (area < maxArea) {
                    j++
                    continue
                }
                maxArea = area
                dt[vertNum] = dominantTri_s()
                if (i1 == vertNum) {
                    dt[vertNum].v2 = i2
                    dt[vertNum].v3 = i3
                } else if (i2 == vertNum) {
                    dt[vertNum].v2 = i3
                    dt[vertNum].v3 = i1
                } else {
                    dt[vertNum].v2 = i1
                    dt[vertNum].v3 = i2
                }
                var len = area
                if (len < 0.001f) {
                    len = 0.001f
                }
                dt[vertNum].normalizationScale[2] = 1.0f / len // normal

                // texture area
                area = d0[3] * d1[4] - d0[4] * d1[3]
                tangent.oSet(0, d0[0] * d1[4] - d0[4] * d1[0])
                tangent.oSet(1, d0[1] * d1[4] - d0[4] * d1[1])
                tangent.oSet(2, d0[2] * d1[4] - d0[4] * d1[2])
                len = tangent.Length()
                if (len < 0.001f) {
                    len = 0.001f
                }
                dt[vertNum].normalizationScale[0] = (if (area > 0) 1 else -1) / len // tangents[0]
                bitangent.oSet(0, d0[3] * d1[0] - d0[0] * d1[3])
                bitangent.oSet(1, d0[3] * d1[1] - d0[1] * d1[3])
                bitangent.oSet(2, d0[3] * d1[2] - d0[2] * d1[3])
                len = bitangent.Length()
                if (len < 0.001f) {
                    len = 0.001f
                }
                if (tr_trisurf.DERIVE_UNSMOOTHED_BITANGENT) {
                    dt[vertNum].normalizationScale[1] = if (area > 0) 1 else -1
                } else {
                    dt[vertNum].normalizationScale[1] = (if (area > 0) 1 else -1) / len // tangents[1]
                }
                j++
            }
            i += j
        }

//        R_StaticFree(ind);
    }

    /*
     ====================
     R_DeriveUnsmoothedTangents

     Uses the single largest area triangle for each vertex, instead of smoothing over all
     ====================
     */
    fun R_DeriveUnsmoothedTangents(tri: srfTriangles_s?) {
        if (tri.tangentsCalculated) {
            return
        }
        if (true) {
            Simd.SIMDProcessor.DeriveUnsmoothedTangents(tri.verts, tri.dominantTris, tri.numVerts)
        }
        tri.tangentsCalculated = true
    }

    /*
     ==================
     R_DeriveTangents

     This is called once for static surfaces, and every frame for deforming surfaces

     Builds tangents, normals, and face planes
     ==================
     */
    @JvmOverloads
    fun R_DeriveTangents(tri: srfTriangles_s?, allocFacePlanes: Boolean = true) {
        var i: Int
        var planes: Array<idPlane?>?
        if (tri.dominantTris != null) {
            tr_trisurf.R_DeriveUnsmoothedTangents(tri)
            return
        }
        if (tri.tangentsCalculated) {
            return
        }
        tr_local.tr.pc.c_tangentIndexes += tri.numIndexes
        if (null == tri.facePlanes && allocFacePlanes) {
            tr_trisurf.R_AllocStaticTriSurfPlanes(tri, tri.numIndexes)
        }
        planes = tri.facePlanes
        if (true) {
            if (null == planes) {
                planes = idPlane.Companion.generateArray(tri.numIndexes / 3)
            }
            Simd.SIMDProcessor.DeriveTangents(planes, tri.verts, tri.numVerts, tri.indexes, tri.numIndexes)

//}else{
//
//	for ( i = 0; i < tri.numVerts; i++ ) {
//		tri.verts[i].normal.Zero();
//		tri.verts[i].tangents[0].Zero();
//		tri.verts[i].tangents[1].Zero();
//	}
//
//	for ( i = 0; i < tri.numIndexes; i += 3 ) {
//		// make face tangents
//		float		d0[5], d1[5];
//		idDrawVert	*a, *b, *c;
//		idVec3		temp, normal, tangents[2];
//
//		a = tri.verts + tri.indexes[i + 0];
//		b = tri.verts + tri.indexes[i + 1];
//		c = tri.verts + tri.indexes[i + 2];
//
//		d0[0] = b.xyz[0] - a.xyz[0];
//		d0[1] = b.xyz[1] - a.xyz[1];
//		d0[2] = b.xyz[2] - a.xyz[2];
//		d0[3] = b.st[0] - a.st[0];
//		d0[4] = b.st[1] - a.st[1];
//
//		d1[0] = c.xyz[0] - a.xyz[0];
//		d1[1] = c.xyz[1] - a.xyz[1];
//		d1[2] = c.xyz[2] - a.xyz[2];
//		d1[3] = c.st[0] - a.st[0];
//		d1[4] = c.st[1] - a.st[1];
//
//		// normal
//		temp[0] = d1[1] * d0[2] - d1[2] * d0[1];
//		temp[1] = d1[2] * d0[0] - d1[0] * d0[2];
//		temp[2] = d1[0] * d0[1] - d1[1] * d0[0];
//		VectorNormalizeFast2( temp, normal );
//
//if (USE_INVA){
//		float area = d0[3] * d1[4] - d0[4] * d1[3];
//		float inva = area < 0.0f ? -1 : 1;		// was = 1.0f / area;
//
//        temp[0] = (d0[0] * d1[4] - d0[4] * d1[0]) * inva;
//        temp[1] = (d0[1] * d1[4] - d0[4] * d1[1]) * inva;
//        temp[2] = (d0[2] * d1[4] - d0[4] * d1[2]) * inva;
//		VectorNormalizeFast2( temp, tangents[0] );
//
//        temp[0] = (d0[3] * d1[0] - d0[0] * d1[3]) * inva;
//        temp[1] = (d0[3] * d1[1] - d0[1] * d1[3]) * inva;
//        temp[2] = (d0[3] * d1[2] - d0[2] * d1[3]) * inva;
//		VectorNormalizeFast2( temp, tangents[1] );
//}else{
//        temp[0] = (d0[0] * d1[4] - d0[4] * d1[0]);
//        temp[1] = (d0[1] * d1[4] - d0[4] * d1[1]);
//        temp[2] = (d0[2] * d1[4] - d0[4] * d1[2]);
//		VectorNormalizeFast2( temp, tangents[0] );
//
//        temp[0] = (d0[3] * d1[0] - d0[0] * d1[3]);
//        temp[1] = (d0[3] * d1[1] - d0[1] * d1[3]);
//        temp[2] = (d0[3] * d1[2] - d0[2] * d1[3]);
//		VectorNormalizeFast2( temp, tangents[1] );
//}
//
//		// sum up the tangents and normals for each vertex on this face
//		for ( int j = 0 ; j < 3 ; j++ ) {
//			vert = &tri.verts[tri.indexes[i+j]];
//			vert.normal += normal;
//			vert.tangents[0] += tangents[0];
//			vert.tangents[1] += tangents[1];
//		}
//
//		if ( planes ) {
//			planes.Normal() = normal;
//			planes.FitThroughPoint( a.xyz );
//			planes++;
//		}
//	}
        }

//if (false){
//
//	if ( tri.silIndexes != null ) {
//		for ( i = 0; i < tri.numVerts; i++ ) {
//			tri.verts[i].normal.Zero();
//		}
//		for ( i = 0; i < tri.numIndexes; i++ ) {
//			tri.verts[tri.silIndexes[i]].normal += planes[i/3].Normal();
//		}
//		for ( i = 0 ; i < tri.numIndexes ; i++ ) {
//			tri.verts[tri.indexes[i]].normal = tri.verts[tri.silIndexes[i]].normal;
//		}
//	}
//
//}else
        run {
            val dupVerts = tri.dupVerts
            val verts = tri.verts

            // add the normal of a duplicated vertex to the normal of the first vertex with the same XYZ
            i = 0
            while (i < tri.numDupVerts) {
                verts[dupVerts[i * 2 + 0]].normal.oPluSet(verts[dupVerts[i * 2 + 1]].normal)
                i++
            }

            // copy vertex normals to duplicated vertices
            i = 0
            while (i < tri.numDupVerts) {
                verts[dupVerts[i * 2 + 1]].normal.oSet(verts[dupVerts[i * 2 + 0]].normal)
                i++
            }
        }

//if (false){
//	// sum up both sides of the mirrored verts
//	// so the S vectors exactly mirror, and the T vectors are equal
//	for ( i = 0 ; i < tri.numMirroredVerts ; i++ ) {
//		idDrawVert	*v1, *v2;
//
//		v1 = &tri.verts[ tri.numVerts - tri.numMirroredVerts + i ];
//		v2 = &tri.verts[ tri.mirroredVerts[i] ];
//
//		v1.tangents[0] -= v2.tangents[0];
//		v1.tangents[1] += v2.tangents[1];
//
//		v2.tangents[0] = vec3_origin - v1.tangents[0];
//		v2.tangents[1] = v1.tangents[1];
//	}
//}
        // project the summed vectors onto the normal plane
        // and normalize.  The tangent vectors will not necessarily
        // be orthogonal to each other, but they will be orthogonal
        // to the surface normal.
        if (true) {
            Simd.SIMDProcessor.NormalizeTangents(tri.verts, tri.numVerts)

//}else{
//
//	for ( i = 0 ; i < tri.numVerts ; i++ ) {
//		idDrawVert *vert = &tri.verts[i];
//
//		VectorNormalizeFast2( vert.normal, vert.normal );
//
//		// project the tangent vectors
//		for ( int j = 0 ; j < 2 ; j++ ) {
//			float d;
//
//			d = vert.tangents[j] * vert.normal;
//			vert.tangents[j] = vert.tangents[j] - d * vert.normal;
//			VectorNormalizeFast2( vert.tangents[j], vert.tangents[j] );
//		}
//	}
//
        }
        tri.tangentsCalculated = true
        tri.facePlanesCalculated = true
    }

    fun R_RemoveDuplicatedTriangles(tri: srfTriangles_s?) {
        var c_removed: Int
        var i: Int
        var j: Int
        var r: Int
        var a: Int
        var b: Int
        var c: Int
        c_removed = 0

        // check for completely duplicated triangles
        // any rotation of the triangle is still the same, but a mirroring
        // is considered different
        i = 0
        while (i < tri.numIndexes) {
            r = 0
            while (r < 3) {
                a = tri.silIndexes[i + r]
                b = tri.silIndexes[i + (r + 1) % 3]
                c = tri.silIndexes[i + (r + 2) % 3]
                j = i + 3
                while (j < tri.numIndexes) {
                    if (tri.silIndexes[j] == a && tri.silIndexes[j + 1] == b && tri.silIndexes[j + 2] == c) {
                        c_removed++
                        //					memmove( tri.indexes + j, tri.indexes + j + 3, ( tri.numIndexes - j - 3 ) * sizeof( tri.indexes[0] ) );
                        System.arraycopy(tri.indexes, j + 3, tri.indexes, j, tri.numIndexes - j - 3)
                        //					memmove( tri.silIndexes + j, tri.silIndexes + j + 3, ( tri.numIndexes - j - 3 ) * sizeof( tri.silIndexes[0] ) );
                        System.arraycopy(tri.silIndexes, j + 3, tri.silIndexes, j, tri.numIndexes - j - 3)
                        tri.numIndexes -= 3
                        j -= 3
                    }
                    j += 3
                }
                r++
            }
            i += 3
        }
        if (c_removed != 0) {
            Common.common.Printf("removed %d duplicated triangles\n", c_removed)
        }
    }

    /*
     =================
     R_RemoveDegenerateTriangles

     silIndexes must have already been calculated
     =================
     */
    fun R_RemoveDegenerateTriangles(tri: srfTriangles_s?) {
        var c_removed: Int
        var i: Int
        var a: Int
        var b: Int
        var c: Int

        // check for completely degenerate triangles
        c_removed = 0
        i = 0
        while (i < tri.numIndexes) {
            a = tri.silIndexes[i]
            b = tri.silIndexes[i + 1]
            c = tri.silIndexes[i + 2]
            if (a == b || a == c || b == c) {
                c_removed++
                //			memmove( tri.indexes + i, tri.indexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.indexes[0] ) );
                System.arraycopy(tri.indexes, i + 3, tri.indexes, i, tri.numIndexes - i - 3)
                if (tri.silIndexes != null) {
//				memmove( tri.silIndexes + i, tri.silIndexes + i + 3, ( tri.numIndexes - i - 3 ) * sizeof( tri.silIndexes[0] ) );
                    System.arraycopy(tri.silIndexes, i + 3, tri.silIndexes, i, tri.numIndexes - i - 3)
                }
                tri.numIndexes -= 3
                i -= 3
            }
            i += 3
        }

        // this doesn't free the memory used by the unused verts
        if (c_removed != 0) {
            Common.common.Printf("removed %d degenerate triangles\n", c_removed)
        }
    }

    /*
     =================
     R_TestDegenerateTextureSpace
     =================
     */
    fun R_TestDegenerateTextureSpace(tri: srfTriangles_s?) {
        var c_degenerate: Int
        var i: Int

        // check for triangles with a degenerate texture space
        c_degenerate = 0
        i = 0
        while (i < tri.numIndexes) {
            val a = tri.verts[tri.indexes[i + 0]]
            val b = tri.verts[tri.indexes[i + 1]]
            val c = tri.verts[tri.indexes[i + 2]]
            if (a.st === b.st || b.st === c.st || c.st === a.st) {
                c_degenerate++
            }
            i += 3
        }
        if (c_degenerate != 0) {
//		common.Printf( "%d triangles with a degenerate texture space\n", c_degenerate );
        }
    }

    /*
     =================
     R_RemoveUnusedVerts
     =================
     */
    fun R_RemoveUnusedVerts(tri: srfTriangles_s?) {
        var i: Int
        val mark: IntArray
        var index: Int
        var used: Int
        mark = IntArray(tri.numVerts) // R_ClearedStaticAlloc(tri.numVerts);
        i = 0
        while (i < tri.numIndexes) {
            index = tri.indexes[i]
            if (index < 0 || index >= tri.numVerts) {
                Common.common.Error("R_RemoveUnusedVerts: bad index")
            }
            mark[index] = 1
            if (tri.silIndexes != null) {
                index = tri.silIndexes[i]
                if (index < 0 || index >= tri.numVerts) {
                    Common.common.Error("R_RemoveUnusedVerts: bad index")
                }
                mark[index] = 1
            }
            i++
        }
        used = 0
        i = 0
        while (i < tri.numVerts) {
            if (0 == mark[i]) {
                i++
                continue
            }
            mark[i] = used + 1
            used++
            i++
        }
        if (used != tri.numVerts) {
            i = 0
            while (i < tri.numIndexes) {
                tri.indexes[i] = mark[tri.indexes[i]] - 1
                if (tri.silIndexes != null) {
                    tri.silIndexes[i] = mark[tri.silIndexes[i]] - 1
                }
                i++
            }
            tri.numVerts = used
            i = 0
            while (i < tri.numVerts) {
                index = mark[i]
                if (0 == index) {
                    i++
                    continue
                }
                tri.verts[index - 1] = tri.verts[i]
                i++
            }

            // this doesn't realloc the arrays to save the memory used by the unused verts
        }

//        R_StaticFree(mark);
    }

    /*
     =================
     R_MergeSurfaceList

     Only deals with vertexes and indexes, not silhouettes, planes, etc.
     Does NOT perform a cleanup triangles, so there may be duplicated verts in the result.
     =================
     */
    fun R_MergeSurfaceList(surfaces: Array<srfTriangles_s?>?, numSurfaces: Int): srfTriangles_s? {
        val newTri: srfTriangles_s?
        var tri: srfTriangles_s?
        var i: Int
        var j: Int
        var totalVerts: Int
        var totalIndexes: Int
        totalVerts = 0
        totalIndexes = 0
        i = 0
        while (i < numSurfaces) {
            totalVerts += surfaces.get(i).numVerts
            totalIndexes += surfaces.get(i).numIndexes
            i++
        }
        newTri = tr_trisurf.R_AllocStaticTriSurf()
        newTri.numVerts = totalVerts
        newTri.numIndexes = totalIndexes
        tr_trisurf.R_AllocStaticTriSurfVerts(newTri, newTri.numVerts)
        tr_trisurf.R_AllocStaticTriSurfIndexes(newTri, newTri.numIndexes)
        totalVerts = 0
        totalIndexes = 0
        i = 0
        while (i < numSurfaces) {
            tri = surfaces.get(i)
            //		memcpy( newTri.verts + totalVerts, tri.verts, tri.numVerts * sizeof( *tri.verts ) );
            var k = 0
            var tv = totalVerts
            while (k < tri.numVerts) {
                newTri.verts[tv] = idDrawVert(tri.verts[k])
                k++
                tv++
            }
            j = 0
            while (j < tri.numIndexes) {
                newTri.indexes[totalIndexes + j] = totalVerts + tri.indexes[j]
                j++
            }
            totalVerts += tri.numVerts
            totalIndexes += tri.numIndexes
            i++
        }
        return newTri
    }

    /*
     =================
     R_RemoveDuplicatedTriangles

     silIndexes must have already been calculated

     silIndexes are used instead of indexes, because duplicated
     triangles could have different texture coordinates.
     =================
     */
    /*
     =================
     R_MergeTriangles

     Only deals with vertexes and indexes, not silhouettes, planes, etc.
     Does NOT perform a cleanup triangles, so there may be duplicated verts in the result.
     =================
     */
    fun R_MergeTriangles(tri1: srfTriangles_s?, tri2: srfTriangles_s?): srfTriangles_s? {
        val tris = arrayOfNulls<srfTriangles_s?>(2)
        tris[0] = tri1
        tris[1] = tri2
        return tr_trisurf.R_MergeSurfaceList(tris, 2)
    }

    /*
     =================
     R_ReverseTriangles

     Lit two sided surfaces need to have the triangles actually duplicated,
     they can't just turn on two sided lighting, because the normal and tangents
     are wrong on the other sides.

     This should be called before R_CleanupTriangles
     =================
     */
    fun R_ReverseTriangles(tri: srfTriangles_s?) {
        var i: Int

        // flip the normal on each vertex
        // If the surface is going to have generated normals, this won't matter,
        // but if it has explicit normals, this will keep it on the correct side
        i = 0
        while (i < tri.numVerts) {
            tri.verts[i].normal.oSet(Vector.getVec3_origin().oMinus(tri.verts[i].normal))
            i++
        }

        // flip the index order to make them back sided
        i = 0
        while (i < tri.numIndexes) {
            var   /*glIndex_t*/temp: Int
            temp = tri.indexes[i + 0]
            tri.indexes[i + 0] = tri.indexes[i + 1]
            tri.indexes[i + 1] = temp
            i += 3
        }
    }

    fun R_CleanupTriangles(
        tri: srfTriangles_s?,
        createNormals: Boolean,
        identifySilEdges: Boolean,
        useUnsmoothedTangents: Boolean
    ) {
        tr_trisurf.DBG_R_CleanupTriangles++
        tr_trisurf.R_RangeCheckIndexes(tri)
        tr_trisurf.R_CreateSilIndexes(tri)

//	R_RemoveDuplicatedTriangles( tri );	// this may remove valid overlapped transparent triangles
        tr_trisurf.R_RemoveDegenerateTriangles(tri)
        tr_trisurf.R_TestDegenerateTextureSpace(tri)

//	R_RemoveUnusedVerts( tri );
        if (identifySilEdges) {
            tr_trisurf.R_IdentifySilEdges(tri, true) // assume it is non-deformable, and omit coplanar edges
        }

        // bust vertexes that share a mirrored edge into separate vertexes
        tr_trisurf.R_DuplicateMirroredVertexes(tri)

        // optimize the index order (not working?)
//	R_OrderIndexes( tri.numIndexes, tri.indexes );
        tr_trisurf.R_CreateDupVerts(tri)
        tr_trisurf.R_BoundTriSurf(tri)
        if (useUnsmoothedTangents) {
            tr_trisurf.R_BuildDominantTris(tri)
            tr_trisurf.R_DeriveUnsmoothedTangents(tri)
        } else if (!createNormals) {
            tr_trisurf.R_DeriveFacePlanes(tri)
            tr_trisurf.R_DeriveTangentsWithoutNormals(tri)
        } else {
            R_DeriveTangents(tri)
        }
    }

    /*
     ===================
     R_BuildDeformInfo
     ===================
     */
    fun R_BuildDeformInfo(
        numVerts: Int,
        verts: idDrawVert?,
        numIndexes: Int,
        indexes: IntArray?,
        useUnsmoothedTangents: Boolean
    ): deformInfo_s? {
        val deform: deformInfo_s
        val tri: srfTriangles_s
        var i: Int
        tri = srfTriangles_s()
        tri.numVerts = numVerts
        tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
        Simd.SIMDProcessor.Memcpy(tri.verts, verts, tri.numVerts)
        tri.numIndexes = numIndexes
        tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)

        // don't memcpy, so we can change the index type from int to short without changing the interface
        i = 0
        while (i < tri.numIndexes) {
            tri.indexes[i] = indexes.get(i)
            i++
        }
        tr_trisurf.R_RangeCheckIndexes(tri)
        tr_trisurf.R_CreateSilIndexes(tri)

// should we order the indexes here?
//	R_RemoveDuplicatedTriangles( &tri );
//	R_RemoveDegenerateTriangles( &tri );
//	R_RemoveUnusedVerts( &tri );
        tr_trisurf.R_IdentifySilEdges(tri, false) // we cannot remove coplanar edges, because
        // they can deform to silhouettes
        tr_trisurf.R_DuplicateMirroredVertexes(tri) // split mirror points into multiple points
        tr_trisurf.R_CreateDupVerts(tri)
        if (useUnsmoothedTangents) {
            tr_trisurf.R_BuildDominantTris(tri)
        }
        deform = deformInfo_s() //R_ClearedStaticAlloc(sizeof(deform));
        deform.numSourceVerts = numVerts
        deform.numOutputVerts = tri.numVerts
        deform.numIndexes = numIndexes
        deform.indexes = tri.indexes
        deform.silIndexes = tri.silIndexes
        deform.numSilEdges = tri.numSilEdges
        deform.silEdges = tri.silEdges
        deform.dominantTris = tri.dominantTris
        deform.numMirroredVerts = tri.numMirroredVerts
        deform.mirroredVerts = tri.mirroredVerts
        deform.numDupVerts = tri.numDupVerts
        deform.dupVerts = tri.dupVerts
        if (tri.verts != null) {
//            triVertexAllocator.Free(tri.verts);
            tri.verts = null
        }
        if (tri.facePlanes != null) {
//            triPlaneAllocator.Free(tri.facePlanes);
            tri.facePlanes = null
        }
        return deform
    }

    fun R_BuildDeformInfo(
        numVerts: Int,
        verts: Array<idDrawVert?>?,
        numIndexes: Int,
        indexes: idList<Int?>?,
        useUnsmoothedTangents: Boolean
    ): deformInfo_s? {
        val deform: deformInfo_s
        val tri: srfTriangles_s
        var i: Int
        tri = srfTriangles_s() //memset( &tri, 0, sizeof( tri ) );
        tri.numVerts = numVerts
        tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
        Simd.SIMDProcessor.Memcpy(tri.verts, verts, tri.numVerts)
        tri.numIndexes = numIndexes
        tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)

        // don't memcpy, so we can change the index type from int to short without changing the interface
        i = 0
        while (i < tri.numIndexes) {
            tri.indexes[i] = indexes.oGet(i)
            i++
        }
        tr_trisurf.R_RangeCheckIndexes(tri)
        tr_trisurf.R_CreateSilIndexes(tri)

        // should we order the indexes here?
//	R_RemoveDuplicatedTriangles( &tri );
//	R_RemoveDegenerateTriangles( &tri );
//	R_RemoveUnusedVerts( &tri );
        tr_trisurf.R_IdentifySilEdges(tri, false) // we cannot remove coplanar edges, because
        //                                              // they can deform to silhouettes
        tr_trisurf.R_DuplicateMirroredVertexes(tri) // split mirror points into multiple points
        tr_trisurf.R_CreateDupVerts(tri)
        if (useUnsmoothedTangents) {
            tr_trisurf.R_BuildDominantTris(tri)
        }
        deform = deformInfo_s() //deformInfo_t *)R_ClearedStaticAlloc( sizeof( *deform ) );
        deform.numSourceVerts = numVerts
        deform.numOutputVerts = tri.numVerts
        deform.numIndexes = numIndexes
        deform.indexes = tri.indexes
        deform.silIndexes = tri.silIndexes
        deform.numSilEdges = tri.numSilEdges
        deform.silEdges = tri.silEdges
        deform.dominantTris = tri.dominantTris
        deform.numMirroredVerts = tri.numMirroredVerts
        deform.mirroredVerts = tri.mirroredVerts
        deform.numDupVerts = tri.numDupVerts
        deform.dupVerts = tri.dupVerts

//	if ( tri.verts ) {
//		triVertexAllocator.Free( tri.verts );
//	}
//
//	if ( tri.facePlanes ) {
//		triPlaneAllocator.Free( tri.facePlanes );
//	}
        return deform
    }

    /*
     ===================
     R_FreeDeformInfo
     ===================
     */
    fun R_FreeDeformInfo(deformInfo: deformInfo_s?) {
//        if (deformInfo.indexes != null) {
//            triIndexAllocator.Free(deformInfo.indexes);
//        }
//        if (deformInfo.silIndexes != null) {
//            triSilIndexAllocator.Free(deformInfo.silIndexes);
//        }
//        if (deformInfo.silEdges != null) {
//            triSilEdgeAllocator.Free(deformInfo.silEdges);
//        }
//        if (deformInfo.dominantTris != null) {
//            triDominantTrisAllocator.Free(deformInfo.dominantTris);
//        }
//        if (deformInfo.mirroredVerts != null) {
//            triMirroredVertAllocator.Free(deformInfo.mirroredVerts);
//        }
//        if (deformInfo.dupVerts != null) {
//            triDupVertAllocator.Free(deformInfo.dupVerts);
//        }
//        R_StaticFree(deformInfo);
    }

    /*
     ===================
     R_DeformInfoMemoryUsed
     ===================
     */
    fun R_DeformInfoMemoryUsed(deformInfo: deformInfo_s?): Int {
        var total = 0
        if (deformInfo.indexes != null) {
            total += deformInfo.numIndexes // * sizeof( deformInfo.indexes[0] );
        }
        if (deformInfo.silIndexes != null) {
            total += deformInfo.numIndexes // * sizeof( deformInfo.silIndexes[0] );
        }
        if (deformInfo.silEdges != null) {
            total += deformInfo.numSilEdges //* sizeof( deformInfo.silEdges[0] );
        }
        if (deformInfo.dominantTris != null) {
            total += deformInfo.numSourceVerts //* sizeof( deformInfo.dominantTris[0] );
        }
        if (deformInfo.mirroredVerts != null) {
            total += deformInfo.numMirroredVerts //* sizeof( deformInfo.mirroredVerts[0] );
        }
        if (deformInfo.dupVerts != null) {
            total += deformInfo.numDupVerts // * sizeof( deformInfo.dupVerts[0] );
        }
        total += 4
        return total
    }

    private fun Resize(verts: Array<idDrawVert?>?, totalVerts: Int): Array<idDrawVert?>? {
        val newVerts = arrayOfNulls<idDrawVert?>(totalVerts)
        for (i in verts.indices) {
            newVerts[i] = idDrawVert(verts.get(i))
        }
        return newVerts
    }

    private fun Resize(shadowVertexes: Array<shadowCache_s?>?, numVerts: Int): Array<shadowCache_s?>? {
        val newArray = arrayOfNulls<shadowCache_s?>(numVerts)
        val length = Math.min(shadowVertexes.size, numVerts)
        System.arraycopy(shadowVertexes, 0, newArray, 0, length)
        return newArray
    }

    /*
     ===================================================================================

     DEFORMED SURFACES

     ===================================================================================
     */
    private fun Resize(indexes: IntArray?, numIndexes: Int): IntArray? {
        if (indexes == null) {
            return IntArray(numIndexes)
        }
        if (numIndexes <= 0) {
            return null
        }
        val size = if (numIndexes > indexes.size) indexes.size else numIndexes
        val newIndexes = IntArray(numIndexes)
        System.arraycopy(indexes, 0, newIndexes, 0, size)
        return newIndexes
    }

    /*
     ===============
     R_ShowTriMemory_f
     ===============
     */
    @Deprecated("")
    class R_ShowTriSurfMemory_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
//            common.Printf("%6d kB in %d triangle surfaces\n",
//                    (srfTrianglesAllocator.GetAllocCount() /* sizeof( srfTriangles_t )*/) >> 10,
//                    srfTrianglesAllocator.GetAllocCount());
//
//            common.Printf("%6d kB vertex memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triVertexAllocator.GetBaseBlockMemory() >> 10, triVertexAllocator.GetFreeBlockMemory() >> 10,
//                    triVertexAllocator.GetNumFreeBlocks(), triVertexAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB index memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triIndexAllocator.GetBaseBlockMemory() >> 10, triIndexAllocator.GetFreeBlockMemory() >> 10,
//                    triIndexAllocator.GetNumFreeBlocks(), triIndexAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB shadow vert memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triShadowVertexAllocator.GetBaseBlockMemory() >> 10, triShadowVertexAllocator.GetFreeBlockMemory() >> 10,
//                    triShadowVertexAllocator.GetNumFreeBlocks(), triShadowVertexAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB tri plane memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triPlaneAllocator.GetBaseBlockMemory() >> 10, triPlaneAllocator.GetFreeBlockMemory() >> 10,
//                    triPlaneAllocator.GetNumFreeBlocks(), triPlaneAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB sil index memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triSilIndexAllocator.GetBaseBlockMemory() >> 10, triSilIndexAllocator.GetFreeBlockMemory() >> 10,
//                    triSilIndexAllocator.GetNumFreeBlocks(), triSilIndexAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB sil edge memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triSilEdgeAllocator.GetBaseBlockMemory() >> 10, triSilEdgeAllocator.GetFreeBlockMemory() >> 10,
//                    triSilEdgeAllocator.GetNumFreeBlocks(), triSilEdgeAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB dominant tri memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triDominantTrisAllocator.GetBaseBlockMemory() >> 10, triDominantTrisAllocator.GetFreeBlockMemory() >> 10,
//                    triDominantTrisAllocator.GetNumFreeBlocks(), triDominantTrisAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB mirror vert memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triMirroredVertAllocator.GetBaseBlockMemory() >> 10, triMirroredVertAllocator.GetFreeBlockMemory() >> 10,
//                    triMirroredVertAllocator.GetNumFreeBlocks(), triMirroredVertAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB dup vert memory (%d kB free in %d blocks, %d empty base blocks)\n",
//                    triDupVertAllocator.GetBaseBlockMemory() >> 10, triDupVertAllocator.GetFreeBlockMemory() >> 10,
//                    triDupVertAllocator.GetNumFreeBlocks(), triDupVertAllocator.GetNumEmptyBaseBlocks());
//
//            common.Printf("%6d kB total triangle memory\n",
//                    (srfTrianglesAllocator.GetAllocCount() /* sizeof( srfTriangles_t )*/ + triVertexAllocator.GetBaseBlockMemory()
//                    + triIndexAllocator.GetBaseBlockMemory()
//                    + triShadowVertexAllocator.GetBaseBlockMemory()
//                    + triPlaneAllocator.GetBaseBlockMemory()
//                    + triSilIndexAllocator.GetBaseBlockMemory()
//                    + triSilEdgeAllocator.GetBaseBlockMemory()
//                    + triDominantTrisAllocator.GetBaseBlockMemory()
//                    + triMirroredVertAllocator.GetBaseBlockMemory()
//                    + triDupVertAllocator.GetBaseBlockMemory()) >> 10);
        }

        companion object {
            private val instance: cmdFunction_t? = R_ShowTriSurfMemory_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     =================
     SilEdgeSort
     =================
     */
    class SilEdgeSort : cmp_t<silEdge_t?> {
        override fun compare(a: silEdge_t?, b: silEdge_t?): Int {
            if (a.p1 < b.p1) {
                return -1
            }
            if (a.p1 > b.p1) {
                return 1
            }
            if (a.p2 < b.p2) {
                return -1 //TODO:returning 1 is like true, 0 false...what is -1 then?
            }
            return if (a.p2 > b.p2) {
                1
            } else 0
        }
    }

    /*
     ==================
     R_DeriveFaceTangents
     ==================
     */
    class faceTangents_t {
        var degenerate = false
        var negativePolarity = false
        val tangents: Array<idVec3?>? = idVec3.Companion.generateArray(2)

        companion object {
            fun generateArray(length: Int): Array<faceTangents_t?>? {
                return Stream.generate { faceTangents_t() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }
    }

    /*
     ===================
     R_DuplicateMirroredVertexes

     Modifies the surface to bust apart any verts that are shared by both positive and
     negative texture polarities, so tangent space smoothing at the vertex doesn't
     degenerate.

     This will create some identical vertexes (which will eventually get different tangent
     vectors), so never optimize the resulting mesh, or it will get the mirrored edges back.

     Reallocates tri.verts and changes tri.indexes in place
     Silindexes are unchanged by this.

     sets mirroredVerts and mirroredVerts[]

     ===================
     */
    internal class tangentVert_t {
        val polarityUsed: BooleanArray? = BooleanArray(2)
        var negativeRemap = 0
    }

    /*
     ===================
     R_BuildDominantTris

     Find the largest triangle that uses each vertex
     ===================
     */
    internal class indexSort_t {
        var faceNum = 0
        var vertexNum = 0
    }

    internal class IndexSort : cmp_t<indexSort_t?> {
        override fun compare(a: indexSort_t?, b: indexSort_t?): Int {
            if (a.vertexNum < b.vertexNum) {
                return -1
            }
            return if (a.vertexNum > b.vertexNum) {
                1
            } else 0
        }
    }
}