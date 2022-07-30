package neo.Renderer

import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.VertexCache.vertCache_s
import neo.Renderer.tr_local.viewDef_s
import neo.TempDump.SERiAL
import neo.framework.DemoFile.idDemoFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib.idException
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer
import java.util.*

/**
 *
 */
object Model {
    //typedef enum {
    const val INVALID_JOINT = -1
    val MD5_ANIM_EXT: String = "md5anim"
    val MD5_CAMERA_EXT: String = "md5camera"
    val MD5_MESH_EXT: String = "md5mesh"
    const val MD5_VERSION = 10

    /*
     ===============================================================================

     Render Model

     ===============================================================================
     */
    // shared between the renderer, game, and Maya export DLL
    val MD5_VERSION_STRING: String = "MD5Version"

    //
    // using shorts for triangle indexes can save a significant amount of traffic, but
    // to support the large models that renderBump loads, they need to be 32 bits
    var GL_INDEX_TYPE = 0
    const val SHADOW_CAP_INFINITE = 64

    enum class dynamicModel_t {
        DM_STATIC,  // never creates a dynamic model
        DM_CACHED,  // once created, stays constant until the entity is updated (animating characters)
        DM_CONTINUOUS // must be recreated for every single view (time dependent things like particles)
    }

    class silEdge_t {
        // NOTE: making this a glIndex is dubious, as there can be 2x the faces as verts
        var   /*glIndex_t*/p1 = 0
        var p2 // planes defining the edge
                = 0
        var   /*glIndex_t*/v1 = 0
        var v2 // verts defining the edge
                = 0

        companion object {
            fun generateArray(length: Int): Array<silEdge_t> {
                return Array(length) { silEdge_t() }
            }
        }
    }

    // this is used for calculating unsmoothed normals and tangents for deformed models
    class dominantTri_s {
        val normalizationScale: FloatArray = FloatArray(3)
        var   /*glIndex_t*/v2 = 0
        var v3 = 0
    }

    class lightingCache_s(Position: ByteBuffer?) {
        val localLightVector: idVec3 = idVec3() // this is the statically computed vector to the light

        companion object {
            val BYTES: Int = idVec3.Companion.BYTES
            fun toByteBuffer(cache: Array<lightingCache_s>): ByteBuffer {
                val data = BufferUtils.createByteBuffer(BYTES * cache.size)
                for (c in cache) {
                    data.put(c.localLightVector.Write())
                }
                return data.flip()
            }
        }

        // in texture space for cards without vertex programs
        init {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class shadowCache_s {
        var xyz: idVec4 = idVec4() // we use homogenous coordinate tricks

        constructor()
        internal constructor(Position: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            val BYTES: Int = idVec4.Companion.BYTES
            fun toByteBuffer(cache: Array<shadowCache_s>): ByteBuffer {
                val data = BufferUtils.createByteBuffer(BYTES * cache.size)
                for (c in cache) {
                    data.put(c.xyz.Write())
                }
                return data.flip()
            }

            fun generateArray(length: Int): Array<shadowCache_s> {
                return Array(length) { shadowCache_s() }
            }
        }
    }

    // our only drawing geometry type
    class srfTriangles_s {
        val DBG_count = DBG_counter++
        val bounds: idBounds = idBounds() // for culling
        var facePlanes // [numIndexes/3] plane equations
                : ArrayList<idPlane> = ArrayList()
        var indexes // indexes, allocated with special allocator
                : IntArray = IntArray(0)
        var numIndexes // for shadows, this has both front and rear end caps and silhouette planes
                = 0
        var numShadowIndexesNoCaps // shadow volumes with the front and rear caps omitted
                = 0

        // pointers into the original surface, and should not be freed
        var numShadowIndexesNoFrontCaps // shadow volumes with front caps omitted
                = 0
        var numVerts // number of vertices
                = 0
        var shadowCapPlaneBits // bits 0-5 are set when that plane of the interacting light has triangles
                = 0
        var shadowVertexes // these will be copied to shadowCache when it is going to be drawn.
                : ArrayList<shadowCache_s> = ArrayList()
        var silIndexes // indexes changed to be the first vertex with same XYZ, ignoring normal and texcoords
                : IntArray = IntArray(0)
        var tangentsCalculated // set when the vertex tangents have been calculated
                = false
        var verts // vertices, allocated with special allocator
                : ArrayList<idDrawVert> = ArrayList()
        var ambientCache // idDrawVert
                : vertCache_s? = null
        var ambientSurface // for light interactions, point back at the original surface that generated
                : srfTriangles_s? = null
        var ambientViewCount // if == tr.viewCount, it is visible this view
                = 0
        var deformedSurface // if true, indexes, silIndexes, mirrorVerts, and silEdges are
                = false
        var dominantTris // [numVerts] for deformed surface fast tangent calculation
                : ArrayList<dominantTri_s> = ArrayList()
        var dupVerts // pairs of the number of the first vertex and the number of the duplicate vertex
                : IntArray = IntArray(0)
        var facePlanesCalculated // set when the face planes have been calculated
                = false
        var generateNormals // create normals from geometry, instead of using explicit ones
                = false

        // data in vertex object space, not directly readable by the CPU
        var indexCache // int
                : vertCache_s? = null

        // projected on it, which means that if the view is on the outside of that
        // plane, we need to draw the rear caps of the shadow volume
        // turboShadows will have SHADOW_CAP_INFINITE
        var lightingCache // lightingCache_t
                : vertCache_s? = null

        // these are NULL when vertex programs are available
        var mirroredVerts // tri->mirroredVerts[0] is the mirror of tri->numVerts - tri->numMirroredVerts + 0
                : IntArray = IntArray(0)

        // the interaction, which we will get the ambientCache from
        var nextDeferredFree // chain of tris to free next frame
                : srfTriangles_s? = null
        var numDupVerts // number of duplicate vertexes
                = 0
        var numMirroredVerts // this many verts at the end of the vert list are tangent mirrors
                = 0
        var numSilEdges // number of silhouette edges
                = 0
        var perfectHull // true if there aren't any dangling edges
                = false
        var shadowCache // shadowCache_t
                : vertCache_s? = null
        var silEdges // silhouette edges
                : ArrayList<silEdge_t> = ArrayList()

        override fun toString(): String {
            return "srfTriangles_s{" +
                    "DBG_count=" + DBG_count +
                    ", bounds=" + bounds +
                    ", facePlanes=" + facePlanes.toString() +
                    ", indexes=" + Arrays.toString(indexes) +
                    ", numIndexes=" + numIndexes +
                    ", numShadowIndexesNoCaps=" + numShadowIndexesNoCaps +
                    ", numShadowIndexesNoFrontCaps=" + numShadowIndexesNoFrontCaps +
                    ", numVerts=" + numVerts +
                    ", shadowCapPlaneBits=" + shadowCapPlaneBits +
                    ", shadowVertexes=" + shadowVertexes.toString() +
                    ", silIndexes=" + Arrays.toString(silIndexes) +
                    ", tangentsCalculated=" + tangentsCalculated +
                    ", verts=" + verts.toString() +
                    ", ambientCache=" + ambientCache +
                    ", ambientSurface=" + ambientSurface +
                    ", ambientViewCount=" + ambientViewCount +
                    ", deformedSurface=" + deformedSurface +
                    ", dominantTris=" + dominantTris.toString() +
                    ", dupVerts=" + Arrays.toString(dupVerts) +
                    ", facePlanesCalculated=" + facePlanesCalculated +
                    ", generateNormals=" + generateNormals +
                    ", indexCache=" + indexCache +
                    ", lightingCache=" + lightingCache +
                    ", mirroredVerts=" + Arrays.toString(mirroredVerts) +
                    ", nextDeferredFree=" + nextDeferredFree +
                    ", numDupVerts=" + numDupVerts +
                    ", numMirroredVerts=" + numMirroredVerts +
                    ", numSilEdges=" + numSilEdges +
                    ", perfectHull=" + perfectHull +
                    ", shadowCache=" + shadowCache +
                    ", silEdges=" + silEdges.toString() +
                    '}'
        }

        companion object {
            private var DBG_counter = 0
        }
    }

    internal class idTriList : idList<srfTriangles_s>()
    class modelSurface_s {
        var geometry: srfTriangles_s? = null
        var id = 0
        var shader: Material.idMaterial? = null
    }

    //} jointHandle_t;
    class idMD5Joint {
        val name: idStr = idStr()
        var parent: idMD5Joint? = null
    }

    // the init methods may be called again on an already created model when
    // a reloadModels is issued
    abstract class idRenderModel : SERiAL {
        protected val DBG_count = DBG_counter++

        // public abstract						~idRenderModel() {};
        // Loads static models only, dynamic models must be loaded by the modelManager
        @Throws(idException::class)
        abstract fun InitFromFile(fileName: String)

        // renderBump uses this to load the very high poly count models, skipping the
        // shadow and tangent generation, along with some surface cleanup to make it load faster
        abstract fun PartialInitFromFile(fileName: String)

        // this is used for dynamically created surfaces, which are assumed to not be reloadable.
        // It can be called again to clear out the surfaces of a dynamic model for regeneration.
        abstract fun InitEmpty(name: String)

        // dynamic model instantiations will be created with this
        // the geometry data will be owned by the model, and freed when it is freed
        // the geoemtry should be raw triangles, with no extra processing
        abstract fun AddSurface(surface: modelSurface_s)

        // cleans all the geometry and performs cross-surface processing
        // like shadow hulls
        // Creates the duplicated back side geometry for two sided, alpha tested, lit materials
        // This does not need to be called if none of the surfaces added with AddSurface require
        // light interaction, and all the triangles are already well formed.
        abstract fun FinishSurfaces()

        // frees all the data, but leaves the class around for dangling references,
        // which can regenerate the data with LoadModel()
        abstract fun PurgeModel()

        // resets any model information that needs to be reset on a same level load etc.. 
        // currently only implemented for liquids
        abstract fun Reset()

        // used for initial loads, reloadModel, and reloading the data of purged models
        // Upon exit, the model will absolutely be valid, but possibly as a default model
        abstract fun LoadModel()

        // internal use
        abstract fun IsLoaded(): Boolean
        abstract fun SetLevelLoadReferenced(referenced: Boolean)
        abstract fun IsLevelLoadReferenced(): Boolean

        // models that are already loaded at level start time
        // will still touch their data to make sure they
        // are kept loaded
        abstract fun TouchData()

        // dump any ambient caches on the model surfaces
        abstract fun FreeVertexCache()

        // returns the name of the model
        abstract fun Name(): String

        // prints a detailed report on the model for printModel
        abstract fun Print()

        // prints a single line report for listModels
        abstract fun List()

        // reports the amount of memory (roughly) consumed by the model
        abstract fun Memory(): Int

        // for reloadModels
        abstract fun  /*ID_TIME_T*/Timestamp(): LongArray

        // returns the number of surfaces
        abstract fun NumSurfaces(): Int

        // NumBaseSurfaces will not count any overlays added to dynamic models
        abstract fun NumBaseSurfaces(): Int

        // get a pointer to a surface
        abstract fun Surface(surfaceNum: Int): modelSurface_s

        // Allocates surface triangles.
        // Allocates memory for srfTriangles_t::verts and srfTriangles_t::indexes
        // The allocated memory is not initialized.
        // srfTriangles_t::numVerts and srfTriangles_t::numIndexes are set to zero.
        abstract fun AllocSurfaceTriangles(numVerts: Int, numIndexes: Int): srfTriangles_s

        // Frees surfaces triangles.
        abstract fun FreeSurfaceTriangles(tris: srfTriangles_s?)

        // created at load time by stitching together all surfaces and sharing
        // the maximum number of edges.  This may be incorrect if a skin file
        // remaps surfaces between shadow casting and non-shadow casting, or
        // if some surfaces are noSelfShadow and others aren't
        abstract fun ShadowHull(): srfTriangles_s?

        // models of the form "_area*" may have a prelight shadow model associated with it
        abstract fun IsStaticWorldModel(): Boolean

        // models parsed from inside map files or dynamically created cannot be reloaded by
        // reloadmodels
        abstract fun IsReloadable(): Boolean

        // md3, md5, particles, etc
        abstract fun IsDynamicModel(): dynamicModel_t

        // if the load failed for any reason, this will return true
        abstract fun IsDefaultModel(): Boolean

        // dynamic models should return a fast, conservative approximation
        // static models should usually return the exact value
        abstract fun Bounds(ent: renderEntity_s? /*= NULL*/): idBounds
        abstract fun Bounds(): idBounds

        // returns value != 0.0f if the model requires the depth hack
        abstract fun DepthHack(): Float

        // returns a static model based on the definition and view
        // currently, this will be regenerated for every view, even though
        // some models, like character meshes, could be used for multiple (mirror)
        // views in a frame, or may stay static for multiple frames (corpses)
        // The renderer will delete the returned dynamic model the next view
        // This isn't const, because it may need to reload a purged model if it
        // wasn't precached correctly.
        abstract fun InstantiateDynamicModel(
            ent: renderEntity_s,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel?

        // Returns the number of joints or 0 if the model is not an MD5
        abstract fun NumJoints(): Int

        // Returns the MD5 joints or NULL if the model is not an MD5
        abstract fun GetJoints(): ArrayList<idMD5Joint>

        // Returns the handle for the joint with the given name.
        abstract /*jointHandle_t*/  fun GetJointHandle(name: String): Int

        // Returns the name for the joint with the given handle.
        abstract fun GetJointName(jointHandle_t: Int): String

        // Returns the default animation pose or NULL if the model is not an MD5.
        abstract fun GetDefaultPose(): ArrayList<idJointQuat>

        // Returns number of the joint nearest to the given triangle.
        abstract fun NearestJoint(surfaceNum: Int, a: Int, c: Int, b: Int): Int

        // Writing to and reading from a demo file.
        abstract fun ReadFromDemoFile(f: idDemoFile)
        abstract fun WriteToDemoFile(f: idDemoFile)
        abstract fun oSet(FindModel: idRenderModel)

        companion object {
            private var DBG_counter = 0
        }
    }

    init {
        if (true) {
            GL_INDEX_TYPE = GL11.GL_UNSIGNED_INT
            //        } else {
            //            GL_INDEX_TYPE = GL_UNSIGNED_SHORT;
        }
    }
}