package neo.Renderer

import neo.Renderer.Material.deform_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_ase.aseFace_t
import neo.Renderer.Model_ase.aseMaterial_t
import neo.Renderer.Model_ase.aseMesh_t
import neo.Renderer.Model_ase.aseModel_s
import neo.Renderer.Model_ase.aseObject_t
import neo.Renderer.Model_lwo.lwObject
import neo.Renderer.Model_lwo.lwSurface
import neo.Renderer.Model_ma.maMaterial_t
import neo.Renderer.Model_ma.maMesh_t
import neo.Renderer.Model_ma.maModel_s
import neo.Renderer.Model_ma.maObject_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.demoCommand_t
import neo.Renderer.tr_local.viewDef_s
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.DemoFile.idDemoFile
import neo.framework.FileSystem_h
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Lib.idException
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.VectorSet.idVectorSubset
import neo.idlib.geometry.DrawVert
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Simd
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.sqrt

/**
 *
 */
object Model_local {
    /*
     ================
     AddCubeFace
     ================
     */
    fun AddCubeFace(tri: srfTriangles_s, v1: idVec3, v2: idVec3, v3: idVec3, v4: idVec3) {
        tri.verts.add(DrawVert.idDrawVert())
        tri.verts.add(DrawVert.idDrawVert())
        tri.verts.add(DrawVert.idDrawVert())
        tri.verts.add(DrawVert.idDrawVert())
        tri.verts[tri.numVerts + 0].Clear()
        tri.verts[tri.numVerts + 0].xyz.set(v1.times(8f))
        tri.verts[tri.numVerts + 0].st[0] = 0f
        tri.verts[tri.numVerts + 0].st[1] = 0f
        tri.verts[tri.numVerts + 1].Clear()
        tri.verts[tri.numVerts + 1].xyz.set(v2.times(8f))
        tri.verts[tri.numVerts + 1].st[0] = 1f
        tri.verts[tri.numVerts + 1].st[1] = 0f
        tri.verts[tri.numVerts + 2].Clear()
        tri.verts[tri.numVerts + 2].xyz.set(v3.times(8f))
        tri.verts[tri.numVerts + 2].st[0] = 1f
        tri.verts[tri.numVerts + 2].st[1] = 1f
        tri.verts[tri.numVerts + 3].Clear()
        tri.verts[tri.numVerts + 3].xyz.set(v4.times(8f))
        tri.verts[tri.numVerts + 3].st[0] = 0f
        tri.verts[tri.numVerts + 3].st[1] = 1f
        tri.indexes[tri.numIndexes + 0] = tri.numVerts + 0
        tri.indexes[tri.numIndexes + 1] = tri.numVerts + 1
        tri.indexes[tri.numIndexes + 2] = tri.numVerts + 2
        tri.indexes[tri.numIndexes + 3] = tri.numVerts + 0
        tri.indexes[tri.numIndexes + 4] = tri.numVerts + 2
        tri.indexes[tri.numIndexes + 5] = tri.numVerts + 3
        tri.numVerts += 4
        tri.numIndexes += 6
    }

    /*
     ===============================================================================

     Static model

     ===============================================================================
     */
    open class idRenderModelStatic : idRenderModel() {
        val surfaces: ArrayList<modelSurface_s>
        protected val   /*ID_TIME_T*/timeStamp: LongArray = LongArray(1)
        var bounds: idBounds = idBounds()
        var overlaysAdded: Int
        protected var defaulted: Boolean
        protected var fastLoad // don't generate tangents and shadow data
                : Boolean
        protected var isStaticWorldModel: Boolean
        protected var lastArchivedFrame: Int

        //
        //
        protected var lastModifiedFrame: Int
        protected var levelLoadReferenced // for determining if it needs to be freed
                : Boolean

        //
        //
        //
        protected var name: idStr = idStr()
        protected var purged // eventually we will have dynamic reloading
                : Boolean
        protected var reloadable // if not, reloadModels won't check timestamp
                : Boolean
        protected var shadowHull: srfTriangles_s?

        @Throws(idException::class)
        override fun InitFromFile(fileName: String) {
            val loaded: Boolean
            val extension = idStr()
            InitEmpty(fileName)

            // FIXME: load new .proc map format
            name.ExtractFileExtension(extension)
            if (extension.Icmp("ase") == 0) {
                loaded = LoadASE(name.toString())
                reloadable = true
            } else if (extension.Icmp("lwo") == 0) {
                loaded = LoadLWO(name.toString())
                reloadable = true
            } else if (extension.Icmp("flt") == 0) {
                loaded = LoadFLT(name.toString())
                reloadable = true
            } else if (extension.Icmp("ma") == 0) {
                loaded = LoadMA(name.toString())
                reloadable = true
            } else {
                Common.common.Warning("idRenderModelStatic::InitFromFile: unknown type for model: '%s'", name)
                loaded = false
            }
            if (!loaded) {
                Common.common.Warning("Couldn't load model: '%s'", name)
                MakeDefaultModel()
                return
            }

            // it is now available for use
            purged = false

            // create the bounds for culling and dynamic surface creation
            FinishSurfaces()
        }

        override fun PartialInitFromFile(fileName: String) {
            fastLoad = true
            InitFromFile(fileName)
        }

        override fun PurgeModel() {
            var i: Int
            var surf: modelSurface_s?
            i = 0
            while (i < surfaces.size) {
                surf = surfaces[i]
                if (surf.geometry != null) {
                    tr_trisurf.R_FreeStaticTriSurf(surf.geometry)
                }
                i++
            }
            surfaces.clear()
            purged = true
        }

        override fun Reset() {}
        override fun LoadModel() {
            PurgeModel()
            InitFromFile(name.toString())
        }

        override fun IsLoaded(): Boolean {
            return !purged
        }

        override fun SetLevelLoadReferenced(referenced: Boolean) {
            levelLoadReferenced = referenced
        }

        override fun IsLevelLoadReferenced(): Boolean {
            return levelLoadReferenced
        }

        override fun TouchData() {
            for (i in 0 until surfaces.size) {
                val surf = surfaces[i]

                // re-find the material to make sure it gets added to the
                // level keep list
                DeclManager.declManager.FindMaterial(surf.shader!!.GetName())
            }
        }

        override fun InitEmpty(fileName: String) {
            // model names of the form _area* are static parts of the
            // world, and have already been considered for optimized shadows
            // other model names are inline entity models, and need to be
            // shadowed normally
            isStaticWorldModel = 0 == idStr.Cmpn(fileName, "_area", 5)
            name = idStr(fileName)
            reloadable = false // if it didn't come from a file, we can't reload it
            PurgeModel()
            purged = false
            bounds.Zero()
        }

        override fun AddSurface(surface: modelSurface_s) {
            surfaces.add(surface)
            //surfaces.addClone(surface);
            if (surface.geometry != null) {
                bounds.timesAssign(surface.geometry!!.bounds)
            }
        }

        override fun FinishSurfaces() {
            DBG_FinishSurfaces++
            var i: Int
            var totalVerts: Int
            var totalIndexes: Int
            purged = false

            // make sure we don't have a huge bounds even if we don't finish everything
            bounds.Zero()
            if (surfaces.size == 0) {
                return
            }

            // renderBump doesn't care about most of this
            if (fastLoad) {
                bounds.Zero()
                i = 0
                while (i < surfaces.size) {
                    val surf = surfaces[i]
                    tr_trisurf.R_BoundTriSurf(surf.geometry!!)
                    bounds.AddBounds(surf.geometry!!.bounds)
                    i++
                }
                return
            }

            // cleanup all the final surfaces, but don't create sil edges
            totalVerts = 0
            totalIndexes = 0

            // decide if we are going to merge all the surfaces into one shadower
            val numOriginalSurfaces = surfaces.size

            // make sure there aren't any NULL shaders or geometry
            i = 0
            while (i < numOriginalSurfaces) {
                val surf = surfaces[i]
                if (surf.geometry == null || surf.shader == null) {
                    MakeDefaultModel()
                    Common.common.Error("Model %s, surface %d had NULL geometry", name, i)
                }
                if (surf.shader == null) {
                    MakeDefaultModel()
                    Common.common.Error("Model %s, surface %d had NULL shader", name, i)
                }
                i++
            }

            // duplicate and reverse triangles for two sided bump mapped surfaces
            // note that this won't catch surfaces that have their shaders dynamically
            // changed, and won't work with animated models.
            // It is better to create completely separate surfaces, rather than
            // add vertexes and indexes to the existing surface, because the
            // tangent generation wouldn't like the acute shared edges
            i = 0
            while (i < numOriginalSurfaces) {
                val surf = surfaces[i]
                if (surf.shader!!.ShouldCreateBackSides()) {
                    var newTri: srfTriangles_s?
                    newTri = tr_trisurf.R_CopyStaticTriSurf(surf.geometry!!)
                    tr_trisurf.R_ReverseTriangles(newTri)
                    val newSurf = modelSurface_s()
                    newSurf.shader = surf.shader
                    newSurf.geometry = newTri
                    AddSurface(newSurf)
                }
                i++
            }

            // clean the surfaces
            i = 0
            while (i < surfaces.size) {
                val surf = surfaces[i]
                tr_trisurf.R_CleanupTriangles(
                    surf.geometry!!,
                    surf.geometry!!.generateNormals,
                    true,
                    surf.shader!!.UseUnsmoothedTangents()
                )
                if (surf.shader!!.SurfaceCastsShadow()) {
                    totalVerts += surf.geometry!!.numVerts
                    totalIndexes += surf.geometry!!.numIndexes
                }
                i++
            }

            // add up the total surface area for development information
            i = 0
            while (i < surfaces.size) {
                val surf = surfaces[i]
                val tri = surf.geometry!!
                var j = 0
                while (j < tri.numIndexes) {
                    val area: Float = idWinding.TriangleArea(
                        tri.verts[tri.indexes[j]].xyz,
                        tri.verts[tri.indexes[j + 1]].xyz, tri.verts[tri.indexes[j + 2]].xyz
                    )
                    surf.shader!!.AddToSurfaceArea(area)
                    j += 3
                }
                i++
            }

            // calculate the bounds
            if (surfaces.size == 0) {
                bounds.Zero()
            } else {
                bounds.Clear()
                i = 0
                while (i < surfaces.size) {
                    val surf = surfaces[i]

                    // if the surface has a deformation, increase the bounds
                    // the amount here is somewhat arbitrary, designed to handle
                    // autosprites and flares, but could be done better with exact
                    // deformation information.
                    // Note that this doesn't handle deformations that are skinned in
                    // at run time...
                    if (surf.shader!!.Deform() != deform_t.DFRM_NONE) {
                        val tri = surf.geometry!!
                        val mid = idVec3(tri.bounds[1].plus(tri.bounds[0]).times(0.5f))
                        var radius = tri.bounds[0].minus(mid).Length()
                        radius += 20.0f
                        tri.bounds[0, 0] = mid[0] - radius
                        tri.bounds[0, 1] = mid[1] - radius
                        tri.bounds[0, 2] = mid[2] - radius
                        tri.bounds[1, 0] = mid[0] + radius
                        tri.bounds[1, 1] = mid[1] + radius
                        tri.bounds[1, 2] = mid[2] + radius
                    }

                    // add to the model bounds
                    bounds.AddBounds(surf.geometry!!.bounds)
                    i++
                }
            }
        }

        /*
         ==============
         idRenderModelStatic::FreeVertexCache

         We are about to restart the vertex cache, so dump everything
         ==============
         */
        override fun FreeVertexCache() {
            for (j in 0 until surfaces.size) {
                val tri = surfaces[j].geometry ?: continue
                if (tri.ambientCache != null) {
                    VertexCache.vertexCache.Free(tri.ambientCache)
                    tri.ambientCache = null
                }
                // static shadows may be present
                if (tri.shadowCache != null) {
                    VertexCache.vertexCache.Free(tri.shadowCache)
                    tri.shadowCache = null
                }
            }
        }

        override fun Name(): String {
            return name.toString()
        }

        override fun Print() {
            var totalTris = 0
            var totalVerts = 0
            val totalBytes: Int // = 0;
            totalBytes = Memory()
            var closed: Char = 'C'
            for (j in 0 until NumSurfaces()) {
                val surf = Surface(j)
                if (null == surf.geometry) {
                    continue
                }
                if (!surf.geometry!!.perfectHull) {
                    closed = ' '
                }
                totalTris += surf.geometry!!.numIndexes / 3
                totalVerts += surf.geometry!!.numVerts
            }
            Common.common.Printf(
                "%c%4dk %3d %4d %4d %s",
                closed,
                totalBytes / 1024,
                NumSurfaces(),
                totalVerts,
                totalTris,
                Name()
            )
            if (IsDynamicModel() == dynamicModel_t.DM_CACHED) {
                Common.common.Printf(" (DM_CACHED)")
            }
            if (IsDynamicModel() == dynamicModel_t.DM_CONTINUOUS) {
                Common.common.Printf(" (DM_CONTINUOUS)")
            }
            if (defaulted) {
                Common.common.Printf(" (DEFAULTED)")
            }
            if (bounds[0][0] >= bounds[1][0]) {
                Common.common.Printf(" (EMPTY BOUNDS)")
            }
            if (bounds[1][0] - bounds[0][0] > 100000) {
                Common.common.Printf(" (HUGE BOUNDS)")
            }
            Common.common.Printf("\n")
        }

        override fun List() {
            var totalTris = 0
            var totalVerts = 0
            val totalBytes: Int //= 0;
            totalBytes = Memory()
            var closed: Char = 'C'
            for (j in 0 until NumSurfaces()) {
                val surf = Surface(j)
                if (null == surf.geometry) {
                    continue
                }
                if (!surf.geometry!!.perfectHull) {
                    closed = ' '
                }
                totalTris += surf.geometry!!.numIndexes / 3
                totalVerts += surf.geometry!!.numVerts
            }
            Common.common.Printf(
                "%c%4dk %3d %4d %4d %s",
                closed,
                totalBytes / 1024,
                NumSurfaces(),
                totalVerts,
                totalTris,
                Name()
            )
            if (IsDynamicModel() == dynamicModel_t.DM_CACHED) {
                Common.common.Printf(" (DM_CACHED)")
            }
            if (IsDynamicModel() == dynamicModel_t.DM_CONTINUOUS) {
                Common.common.Printf(" (DM_CONTINUOUS)")
            }
            if (defaulted) {
                Common.common.Printf(" (DEFAULTED)")
            }
            if (bounds[0][0] >= bounds[1][0]) {
                Common.common.Printf(" (EMPTY BOUNDS)")
            }
            if (bounds[1][0] - bounds[0][0] > 100000) {
                Common.common.Printf(" (HUGE BOUNDS)")
            }
            Common.common.Printf("\n")
        }

        override fun Memory(): Int {
            var totalBytes = 0
            totalBytes += 4
            totalBytes += name.DynamicMemoryUsed()
            //totalBytes += surfaces.MemoryUsed()
            if (shadowHull != null) {
                totalBytes += tr_trisurf.R_TriSurfMemory(shadowHull)
            }
            for (j in 0 until NumSurfaces()) {
                val surf = Surface(j)
                if (null == surf.geometry) {
                    continue
                }
                totalBytes += tr_trisurf.R_TriSurfMemory(surf.geometry)
            }
            return totalBytes
        }

        override fun  /*ID_TIME_T*/Timestamp(): LongArray {
            return timeStamp
        }

        override fun NumSurfaces(): Int {
            return surfaces.size
        }

        override fun NumBaseSurfaces(): Int {
            return surfaces.size - overlaysAdded
        }

        override fun Surface(surfaceNum: Int): modelSurface_s {
            return surfaces[surfaceNum]
        }

        override fun AllocSurfaceTriangles(numVerts: Int, numIndexes: Int): srfTriangles_s {
            val tri = tr_trisurf.R_AllocStaticTriSurf()
            tr_trisurf.R_AllocStaticTriSurfVerts(tri, numVerts)
            tr_trisurf.R_AllocStaticTriSurfIndexes(tri, numIndexes)
            return tri
        }

        override fun FreeSurfaceTriangles(tris: srfTriangles_s?) {
            tr_trisurf.R_FreeStaticTriSurf(tris)
        }

        override fun ShadowHull(): srfTriangles_s? {
            return shadowHull
        }

        override fun IsStaticWorldModel(): Boolean {
            return isStaticWorldModel
        }

        override fun IsReloadable(): Boolean {
            return reloadable
        }

        override fun IsDynamicModel(): dynamicModel_t {
            // dynamic subclasses will override this
            return dynamicModel_t.DM_STATIC
        }

        override fun IsDefaultModel(): Boolean {
            return defaulted
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            if (cachedModel != null) {
//		delete cachedModel;
//		cachedModel = NULL;
            }
            Common.common.Error("InstantiateDynamicModel called on static model '%s'", name.toString())
            return null
        }

        override fun NumJoints(): Int {
            return 0
        }

        override fun GetJoints(): kotlin.collections.ArrayList<idMD5Joint> {
            return emptyList<idMD5Joint>() as ArrayList<idMD5Joint>
        }

        override fun GetJointHandle(name: String): Int {
            return Model.INVALID_JOINT
        }

        override fun GetJointName(jointHandle_t: Int): String {
            return ""
        }

        override fun GetDefaultPose(): ArrayList<idJointQuat> {
            return ArrayList<idJointQuat>()
        }

        override fun NearestJoint(surfaceNum: Int, a: Int, c: Int, b: Int): Int {
            return Model.INVALID_JOINT
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            return idBounds(bounds[0], bounds[1])
        }

        override fun Bounds(): idBounds {
            return Bounds(null)
        }

        override fun ReadFromDemoFile(f: idDemoFile) {
            PurgeModel()
            InitEmpty(f.ReadHashString())
            var i: Int
            var j: Int
            val numSurfaces = CInt()
            val index = CInt()
            val vert = CInt()
            f.ReadInt(numSurfaces)
            i = 0
            while (i < numSurfaces._val) {
                val surf = modelSurface_s()
                surf.shader = DeclManager.declManager.FindMaterial(f.ReadHashString())
                val tri = tr_trisurf.R_AllocStaticTriSurf()
                f.ReadInt(index)
                tri.numIndexes = index._val
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
                j = 0
                while (j < tri.numIndexes) {
                    f.ReadInt(index)
                    tri.indexes[j] = index._val
                    ++j
                }
                f.ReadInt(vert)
                tri.numVerts = vert._val
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    val color = Array<CharArray>(4) { CharArray(1) }
                    f.ReadVec3(tri.verts[j].xyz)
                    f.ReadVec2(tri.verts[j].st)
                    f.ReadVec3(tri.verts[j].normal)
                    f.ReadVec3(tri.verts[j].tangents[0])
                    f.ReadVec3(tri.verts[j].tangents[1])
                    f.ReadUnsignedChar(color[0])
                    tri.verts[j].color[0] = color[0][0].code as Byte
                    f.ReadUnsignedChar(color[0])
                    tri.verts[j].color[1] = color[1][0].code as Byte
                    f.ReadUnsignedChar(color[0])
                    tri.verts[j].color[2] = color[2][0].code as Byte
                    f.ReadUnsignedChar(color[0])
                    tri.verts[j].color[3] = color[3][0].code as Byte
                    ++j
                }
                surf.geometry = tri
                AddSurface(surf)
                i++
            }
            FinishSurfaces()
        }

        override fun WriteToDemoFile(f: idDemoFile) {
//            int[] data = new int[1];

            // note that it has been updated
            lastArchivedFrame = tr_local.tr.frameCount

//            data = DC_DEFINE_MODEL.ordinal();//FIXME:WHY?
            f.WriteInt(demoCommand_t.DC_DEFINE_MODEL)
            f.WriteHashString(Name())
            var i: Int
            var j: Int
            val iData = surfaces.size
            f.WriteInt(iData)
            i = 0
            while (i < surfaces.size) {
                val surf = surfaces[i]
                f.WriteHashString(surf.shader!!.GetName())
                val tri = surf.geometry!!
                f.WriteInt(tri.numIndexes)
                j = 0
                while (j < tri.numIndexes) {
                    f.WriteInt(tri.indexes[j])
                    ++j
                }
                f.WriteInt(tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    f.WriteVec3(tri.verts[j].xyz)
                    f.WriteVec2(tri.verts[j].st)
                    f.WriteVec3(tri.verts[j].normal)
                    f.WriteVec3(tri.verts[j].tangents[0])
                    f.WriteVec3(tri.verts[j].tangents[1])
                    f.WriteUnsignedChar(tri.verts[j].color[0] as Char)
                    f.WriteUnsignedChar(tri.verts[j].color[1] as Char)
                    f.WriteUnsignedChar(tri.verts[j].color[2] as Char)
                    f.WriteUnsignedChar(tri.verts[j].color[3] as Char)
                    ++j
                }
                i++
            }
        }

        override fun DepthHack(): Float {
            return 0.0f
        }

        fun MakeDefaultModel() {
            defaulted = true

            // throw out any surfaces we already have
            PurgeModel()

            // create one new surface
            val surf = modelSurface_s()
            val tri = srfTriangles_s()
            surf.shader = tr_local.tr.defaultMaterial
            surf.geometry = tri
            tr_trisurf.R_AllocStaticTriSurfVerts(tri, 24)
            tr_trisurf.R_AllocStaticTriSurfIndexes(tri, 36)
            AddCubeFace(tri, idVec3(-1, 1, 1), idVec3(1, 1, 1), idVec3(1, -1, 1), idVec3(-1, -1, 1))
            AddCubeFace(tri, idVec3(-1, 1, -1), idVec3(-1, -1, -1), idVec3(1, -1, -1), idVec3(1, 1, -1))
            AddCubeFace(tri, idVec3(1, -1, 1), idVec3(1, 1, 1), idVec3(1, 1, -1), idVec3(1, -1, -1))
            AddCubeFace(tri, idVec3(-1, -1, 1), idVec3(-1, -1, -1), idVec3(-1, 1, -1), idVec3(-1, 1, 1))
            AddCubeFace(tri, idVec3(-1, -1, 1), idVec3(1, -1, 1), idVec3(1, -1, -1), idVec3(-1, -1, -1))
            AddCubeFace(tri, idVec3(-1, 1, 1), idVec3(-1, 1, -1), idVec3(1, 1, -1), idVec3(1, 1, 1))
            tri.generateNormals = true
            AddSurface(surf)
            FinishSurfaces()
        }

        fun LoadASE(fileName: String): Boolean {
            val ase: aseModel_s?
            ase = Model_ase.ASE_Load(fileName)
            if (ase == null) {
                return false
            }
            ConvertASEToModelSurfaces(ase)
            Model_ase.ASE_Free(ase)
            return true
        }

        fun LoadLWO(fileName: String): Boolean {
            val failID = intArrayOf(0)
            val failPos = intArrayOf(0)
            val lwo: lwObject?
            lwo = Model_lwo.lwGetObject(fileName, failID, failPos)
            if (null == lwo) {
                return false
            }
            ConvertLWOToModelSurfaces(lwo)
            //
//            lwFreeObject(lwo);
            return true
        }

        /*
         =================
         idRenderModelStatic::LoadFLT

         USGS height map data for megaTexture experiments
         =================
         */
        fun LoadFLT(fileName: String): Boolean {
            val buffer = arrayOfNulls<ByteBuffer>(1)
            val data: FloatBuffer
            val len: Int
            len = FileSystem_h.fileSystem.ReadFile(fileName, buffer)
            if (len <= 0) {
                return false
            }
            val size = sqrt((len / 4.0f).toDouble()).toInt()
            data = buffer[0]!!.asFloatBuffer()

            // bound the altitudes
            var min = 9999999f
            var max = -9999999f
            for (i in 0 until len / 4) {
                data.put(i, Lib.BigFloat(data[i]))
                if (data[i] == -9999f) {
                    data.put(i, 0f) // unscanned areas
                }
                if (data[i] < min) {
                    min = data[i]
                }
                if (data[i] > max) {
                    max = data[i]
                }
            }
            if (true) {
                // write out a gray scale height map
                val image = ByteBuffer.allocate(len) // R_StaticAlloc(len);
                var image_p = 0
                for (i in 0 until len / 4) {
                    val v = (data[i] - min) / (max - min)
                    image.putFloat(image_p, v * 255)
                    image.put(image_p + 3, 255.toByte())
                    image_p += 4
                }
                val tgaName = idStr(fileName)
                tgaName.StripFileExtension()
                tgaName.Append(".tga")
                Image_files.R_WriteTGA(tgaName.toString(), image, size, size, false)
                //                R_StaticFree(image);
//return false;
            }

            // find the island above sea level
            var minX: Int
            var maxX: Int
            var minY: Int
            var maxY: Int
            run {
                var i: Int
                minX = 0
                while (minX < size) {
                    i = 0
                    while (i < size) {
                        if (data.get(i * size + minX) > 1.0) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    minX++
                }
                maxX = size - 1
                while (maxX > 0) {
                    i = 0
                    while (i < size) {
                        if (data.get(i * size + maxX) > 1.0) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    maxX--
                }
                minY = 0
                while (minY < size) {
                    i = 0
                    while (i < size) {
                        if (data.get(minY * size + i) > 1.0) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    minY++
                }
                maxY = size - 1
                while (maxY < size) {
                    i = 0
                    while (i < size) {
                        if (data.get(maxY * size + i) > 1.0) {
                            break
                        }
                        i++
                    }
                    if (i != size) {
                        break
                    }
                    maxY--
                }
            }
            val width = maxX - minX + 1
            val height = maxY - minY + 1

//width /= 2;
            // allocate triangle surface
            val tri = tr_trisurf.R_AllocStaticTriSurf()
            tri.numVerts = width * height
            tri.numIndexes = (width - 1) * (height - 1) * 6
            fastLoad = true // don't do all the sil processing
            tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
            tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val v = i * width + j
                    tri.verts[v].Clear()
                    tri.verts[v].xyz[0] = (j * 10).toFloat() // each sample is 10 meters
                    tri.verts[v].xyz[1] = (-i * 10).toFloat()
                    tri.verts[v].xyz[2] = data[(minY + i) * size + minX + j] // height is in meters
                    tri.verts[v].st[0] = j.toFloat() / (width - 1)
                    tri.verts[v].st[1] = 1.0f - i.toFloat() / (height - 1)
                }
            }
            for (i in 0 until height - 1) {
                for (j in 0 until width - 1) {
                    val v = (i * (width - 1) + j) * 6
                    //if (false){
//			tri.indexes[ v + 0 ] = i * width + j;
//			tri.indexes[ v + 1 ] = (i+1) * width + j;
//			tri.indexes[ v + 2 ] = (i+1) * width + j + 1;
//			tri.indexes[ v + 3 ] = i * width + j;
//			tri.indexes[ v + 4 ] = (i+1) * width + j + 1;
//			tri.indexes[ v + 5 ] = i * width + j + 1;
//}else
                    run {
                        tri.indexes[v + 0] = i * width + j
                        tri.indexes[v + 1] = i * width + j + 1
                        tri.indexes[v + 2] = (i + 1) * width + j + 1
                        tri.indexes[v + 3] = i * width + j
                        tri.indexes[v + 4] = (i + 1) * width + j + 1
                        tri.indexes[v + 5] = (i + 1) * width + j
                    }
                }
            }

//            fileSystem.FreeFile(data);
            //data = null
            val surface = modelSurface_s()
            surface.geometry = tri
            surface.id = 0
            surface.shader = tr_local.tr.defaultMaterial // declManager.FindMaterial( "shaderDemos/megaTexture" );
            AddSurface(surface)
            return true
        }

        fun LoadMA(filename: String): Boolean {
            val ma: maModel_s?
            ma = Model_ma.MA_Load(filename)
            if (ma == null) {
                return false
            }
            ConvertMAToModelSurfaces(ma)
            Model_ma.MA_Free(ma)
            return true
        }

        override fun oSet(FindModel: idRenderModel) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun ConvertASEToModelSurfaces(ase: aseModel_s?): Boolean {
            var `object`: aseObject_t?
            var mesh: aseMesh_t?
            var material: aseMaterial_t?
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s?
            var objectNum: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            var vRemap: IntArray
            var tvRemap: IntArray
            var mvTable: kotlin.collections.ArrayList<matchVert_s> // all of the match verts
            var mvHash: ArrayList<matchVert_s> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            var uOffset: Float
            var vOffset: Float
            var textureSin: Float
            var textureCos: Float
            var uTiling: Float
            var vTiling: Float
            val mergeTo: IntArray
            var color: ByteArray?
            val surf = modelSurface_s()
            var modelSurf: modelSurface_s?
            if (null == ase) {
                return false
            }
            if (ase.objects.size < 1) {
                return false
            }
            timeStamp[0] = ase.timeStamp[0]

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to mege them tgether where possible
            // meaning that this.NumSurfaces() <= ase.objects.currentElements
            mergeTo = IntArray(ase.objects.size)
            surf.geometry = null
            if (ase.materials.size == 0) {
                // if we don't have any materials, dump everything into a single surface
                surf.shader = tr_local.tr.defaultMaterial
                surf.id = 0
                AddSurface(surf)
                i = 0
                while (i < ase.objects.size) {
                    mergeTo[i] = 0
                    i++
                }
            } else if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                i = 0
                while (i < ase.objects.size) {
                    mergeTo[i] = i
                    `object` = ase.objects[i]
                    material = ase.materials[`object`.materialRef]
                    surf.shader = DeclManager.declManager.FindMaterial(TempDump.ctos(material.name))
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    i++
                }
            } else {
                // search for material matches
                i = 0
                while (i < ase.objects.size) {
                    `object` = ase.objects[i]
                    material = ase.materials[`object`.materialRef]
                    im1 = DeclManager.declManager.FindMaterial(TempDump.ctos(material.name))!!
                    if (im1.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    i++
                }
            }
            val vertexSubset = idVectorSubset<idVec3>(3)
            val texCoordSubset = idVectorSubset<idVec2>(2)

            // build the surfaces
            objectNum = 0
            while (objectNum < ase.objects.size) {
                `object` = ase.objects[objectNum]
                mesh = `object`.mesh
                material = ase.materials[`object`.materialRef]
                im1 = DeclManager.declManager.FindMaterial(TempDump.ctos(material.name))
                var normalsParsed = mesh.normalsParsed

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && rb.isNotEmpty()) {
                    normalsParsed = false
                }

                // It seems like the tools our artists are using often generate
                // verts and texcoords slightly separated that should be merged
                // note that we really should combine the surfaces with common materials
                // before doing this operation, because we can miss a slop combination
                // if they are in different surfaces
                vRemap = IntArray(mesh.numVertexes) // R_StaticAlloc(mesh.numVertexes /* sizeof( vRemap[0] ) */);
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = j
                        j++
                    }
                } else {
                    val vertexEpsilon = r_slopVertex.GetFloat()
                    val expand = 2 * 32 * vertexEpsilon
                    val mins = idVec3()
                    val maxs = idVec3()
                    Simd.SIMDProcessor.MinMax(mins, maxs, mesh.vertexes.toTypedArray(), mesh.numVertexes)
                    mins.minusAssign(idVec3(expand, expand, expand))
                    maxs.plusAssign(idVec3(expand, expand, expand))
                    vertexSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = vertexSubset.FindVector(mesh.vertexes.toTypedArray(), j, vertexEpsilon)
                        j++
                    }
                }
                tvRemap = IntArray(mesh.numTVertexes) // R_StaticAlloc(mesh.numTVertexes /* sizeof( tvRemap[0] )*/);
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] = j
                        j++
                    }
                } else {
                    val texCoordEpsilon = r_slopTexCoord.GetFloat()
                    val expand = 2 * 32 * texCoordEpsilon
                    val mins = idVec2()
                    val maxs = idVec2()
                    Simd.SIMDProcessor.MinMax(mins, maxs, mesh.tvertexes.toTypedArray(), mesh.numTVertexes)
                    mins.minusAssign(idVec2(expand, expand))
                    maxs.plusAssign(idVec2(expand, expand))
                    texCoordSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] = texCoordSubset.FindVector(mesh.tvertexes.toTypedArray(), j, texCoordEpsilon)
                        j++
                    }
                }

                // we need to find out how many unique vertex / texcoord combinations
                // there are, because ASE tracks them separately but we need them unified
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable = ArrayList<matchVert_s>(mesh.numFaces * 3)

                // we will have a hash chain based on the xyz values
                mvHash = ArrayList<matchVert_s>(mesh.numVertexes)

                // allocate triangle surface
                tri = tr_trisurf.R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, mesh.numFaces * 3)
                tri.generateNormals = !normalsParsed

                // init default normal, color and tex coord index
                normal.Zero()
                color = identityColor
                tv = 0

                // find all the unique combinations
                val normalEpsilon = 1.0f - r_slopNormal.GetFloat()
                j = 0
                while (j < mesh.numFaces) {
                    k = 0
                    while (k < 3) {
                        v = mesh.faces[j].vertexNum[k]
                        if (v < 0 || v >= mesh.numVertexes) {
                            Common.common.Error("ConvertASEToModelSurfaces: bad vertex index in ASE file %s", name)
                        }

                        // collapse the position if it was slightly offset
                        v = vRemap[v]

                        // we may or may not have texcoords to compare
                        if (mesh.numTVFaces == mesh.numFaces && mesh.numTVertexes != 0) {
                            tv = mesh.faces[j].tVertexNum[k]
                            if (tv < 0 || tv >= mesh.numTVertexes) {
                                Common.common.Error(
                                    "ConvertASEToModelSurfaces: bad tex coord index in ASE file %s",
                                    name
                                )
                            }
                            // collapse the tex coord if it was slightly offset
                            tv = tvRemap[tv]
                        }

                        // we may or may not have normals to compare
                        if (normalsParsed) {
                            normal.set(mesh.faces[j].vertexNormals[k])
                        }

                        // we may or may not have colors to compare
                        if (mesh.colorsParsed) {
                            color[0] = mesh.faces[j].vertexColors[k][0]
                            color[1] = mesh.faces[j].vertexColors[k][1]
                            color[2] = mesh.faces[j].vertexColors[k][2]
                            color[3] = mesh.faces[j].vertexColors[k][3]
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash.getOrNull(v)
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!Arrays.equals(mv.color, color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]
                            mv.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes[tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertASEToModelSurfaces: index miscount in ASE file %s", name)
                }
                if (tri.numVerts > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertASEToModelSurfaces: vertex miscount in ASE file %s", name)
                }

                // an ASE allows the texture coordinates to be scaled, translated, and rotated
                if (ase.materials.size == 0) {
                    vOffset = 0.0f
                    uOffset = vOffset
                    vTiling = 1.0f
                    uTiling = vTiling
                    textureSin = 0.0f
                    textureCos = 1.0f
                } else {
                    material = ase.materials[`object`.materialRef]
                    uOffset = -material.uOffset
                    vOffset = material.vOffset
                    uTiling = material.uTiling
                    vTiling = material.vTiling
                    textureSin = idMath.Sin(material.angle)
                    textureCos = idMath.Cos(material.angle)
                }

                // now allocate and generate the combined vertexes
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]
                    tri.verts[j].Clear()
                    tri.verts[j].xyz.set(mesh.vertexes[mv.v])
                    tri.verts[j].normal.set(mv.normal)
                    System.arraycopy(mv.color, 0, mv.color.also { tri.verts[j].color = it }, 0, mv.color.size)
                    if (mesh.numTVFaces == mesh.numFaces && mesh.numTVertexes != 0) {
                        val tv2 = mesh.tvertexes[mv.tv]
                        val u = tv2.x * uTiling + uOffset
                        val V = tv2.y * vTiling + vOffset
                        tri.verts[j].st[0] = u * textureCos + V * textureSin
                        tri.verts[j].st[1] = u * -textureSin + V * textureCos
                    }
                    j++
                }
                //
//                R_StaticFree(mvTable);
//                R_StaticFree(mvHash);
//                R_StaticFree(tvRemap);
//                R_StaticFree(vRemap);

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[objectNum]]
                val mergeTri = modelSurf.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = tr_trisurf.R_MergeTriangles(mergeTri, tri)
                    tr_trisurf.R_FreeStaticTriSurf(tri)
                    tr_trisurf.R_FreeStaticTriSurf(mergeTri)
                }
                objectNum++
            }
            return true
        }

        fun ConvertLWOToModelSurfaces(lwo: lwObject?): Boolean {
            DBG_ConvertLWOToModelSurfaces++
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s?
            var lwoSurf: lwSurface?
            var numTVertexes: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            val vRemap: IntArray
            val tvList: kotlin.collections.ArrayList<idVec2>
            val tvRemap: IntArray
            var mvTable: Array<matchVert_s?> // all of the match verts
            var mvHash: Array<matchVert_s?> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            val mergeTo: IntArray
            val color = ByteArray(4)
            var surf: modelSurface_s
            var modelSurf: modelSurface_s?
            if (null == lwo) {
                return false
            }
            if (lwo.surf == null) {
                return false
            }
            timeStamp[0] = lwo.timeStamp[0]

            // count the number of surfaces
            i = 0
            lwoSurf = lwo.surf
            while (lwoSurf != null) {
                i++
                lwoSurf = lwoSurf.next
            }

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to merge them tgether where possible
            mergeTo = IntArray(i)
            //	memset( &surf, 0, sizeof( surf ) );
            if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                lwoSurf = lwo.surf
                i = 0
                while (lwoSurf != null) {
                    surf = modelSurface_s()
                    mergeTo[i] = i
                    surf.shader = DeclManager.declManager.FindMaterial(lwoSurf.name)
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    lwoSurf = lwoSurf.next
                    i++
                }
            } else {
                // search for material matches
                lwoSurf = lwo.surf
                i = 0
                while (lwoSurf != null) {
                    surf = modelSurface_s()
                    im1 = DeclManager.declManager.FindMaterial(lwoSurf.name)!!
                    if (im1.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    lwoSurf = lwoSurf.next
                    i++
                }
            }
            val vertexSubset = idVectorSubset<idVec3>(3)
            val texCoordSubset = idVectorSubset<idVec2>(2)

            // we only ever use the first layer
            val layer = lwo.layer

            // vertex positions
            if (layer.point.count <= 0) {
                Common.common.Warning("ConvertLWOToModelSurfaces: model '%s' has bad or missing vertex data", name)
                return false
            }
            val vList: Array<idVec3> =
                idVec3.generateArray(layer.point.count) // R_StaticAlloc(layer.point.count /* sizeof( vList[0] ) */);
            j = 0
            while (j < layer.point.count) {
                vList[j].set(
                    idVec3(
                        layer.point.pt[j].pos[0],
                        layer.point.pt[j].pos[2],
                        layer.point.pt[j].pos[1]
                    )
                )
                j++
            }

            // vertex texture coords
            numTVertexes = 0
            if (layer.nvmaps != 0) {
                var vm = layer.vmap as Model_lwo.lwVMap?
                while (vm != null) {
                    if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                        numTVertexes += vm.nverts
                    }
                    vm = vm.next
                }
            }
            if (numTVertexes != 0) {
                tvList = ArrayList(
                    arrayListOf(* idVec2.generateArray(numTVertexes))
                )
                var offset = 0
                var vm = layer.vmap as Model_lwo.lwVMap?
                while (vm != null) {
                    if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                        vm.offset = offset
                        k = 0
                        while (k < vm.nverts) {
                            tvList[k + offset].x = vm.`val`[k][0]
                            tvList[k + offset].y = 1.0f - vm.`val`[k][1] // invert the t
                            k++
                        }
                        offset += vm.nverts
                    }
                    vm = vm.next
                }
            } else {
                Common.common.Warning("ConvertLWOToModelSurfaces: model '%s' has bad or missing uv data", name)
                numTVertexes = 1
                tvList = ArrayList<idVec2>(numTVertexes) // Mem_ClearedAlloc(numTVertexes /* sizeof( tvList[0] )*/);
                tvList.add(0, idVec2())
            }

            // It seems like the tools our artists are using often generate
            // verts and texcoords slightly separated that should be merged
            // note that we really should combine the surfaces with common materials
            // before doing this operation, because we can miss a slop combination
            // if they are in different surfaces
            vRemap = IntArray(layer.point.count) // R_StaticAlloc(layer.point.count /* sizeof( vRemap[0] )*/);
            if (fastLoad) {
                // renderbump doesn't care about vertex count
                j = 0
                while (j < layer.point.count) {
                    vRemap[j] = j
                    j++
                }
            } else {
                val vertexEpsilon = r_slopVertex.GetFloat()
                val expand = 2 * 32 * vertexEpsilon
                val mins = idVec3()
                val maxs = idVec3()
                Simd.SIMDProcessor.MinMax(mins, maxs, vList, layer.point.count)
                mins.minusAssign(idVec3(expand, expand, expand))
                maxs.plusAssign(idVec3(expand, expand, expand))
                vertexSubset.Init(mins, maxs, 32, 1024)
                j = 0
                while (j < layer.point.count) {
                    vRemap[j] = vertexSubset.FindVector(vList as Array<Vector.idVec<*>>, j, vertexEpsilon)
                    j++
                }
            }
            tvRemap = IntArray(numTVertexes) // R_StaticAlloc(numTVertexes /* sizeof( tvRemap[0] )*/);
            if (fastLoad) {
                // renderbump doesn't care about vertex count
                j = 0
                while (j < numTVertexes) {
                    tvRemap[j] = j
                    j++
                }
            } else {
                val texCoordEpsilon = r_slopTexCoord.GetFloat()
                val expand = 2 * 32 * texCoordEpsilon
                val mins = idVec2()
                val maxs = idVec2()
                Simd.SIMDProcessor.MinMax(mins, maxs, tvList.toTypedArray(), numTVertexes)
                mins.minusAssign(idVec2(expand, expand))
                maxs.plusAssign(idVec2(expand, expand))
                texCoordSubset.Init(mins, maxs, 32, 1024)
                j = 0
                while (j < numTVertexes) {
                    tvRemap[j] = texCoordSubset.FindVector(tvList.toTypedArray(), j, texCoordEpsilon)
                    j++
                }
            }

            // build the surfaces
            lwoSurf = lwo.surf
            i = 0
            while (lwoSurf != null) {
                im1 = DeclManager.declManager.FindMaterial(lwoSurf.name)
                var normalsParsed = true

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && !rb.isEmpty()) {
                    normalsParsed = false
                }

                // we need to find out how many unique vertex / texcoord combinations there are
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable = arrayOfNulls<matchVert_s?>(layer.polygon.count * 3)

                // we will have a hash chain based on the xyz values
                mvHash =
                    arrayOfNulls<matchVert_s?>(layer.point.count) // R_ClearedStaticAlloc(layer.point.count, matchVert_s.class/* sizeof( mvHash[0] ) */);

                // allocate triangle surface
                tri = tr_trisurf.R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, layer.polygon.count * 3)
                tri.generateNormals = !normalsParsed

                // find all the unique combinations
                var normalEpsilon: Float
                normalEpsilon = if (fastLoad) {
                    1.0f // don't merge unless completely exact
                } else {
                    1.0f - r_slopNormal.GetFloat()
                }
                j = 0
                while (j < layer.polygon.count) {
                    val poly = layer.polygon.pol[j]
                    if (poly.surf != lwoSurf) {
                        j++
                        continue
                    }
                    if (poly.nverts != 3) {
                        Common.common.Warning(
                            "ConvertLWOToModelSurfaces: model %s has too many verts for a poly! Make sure you triplet it down",
                            name
                        )
                        j++
                        continue
                    }
                    k = 0
                    while (k < 3) {
                        v = vRemap[poly.getV(k).index]
                        normal.x = poly.getV(k).norm[0]
                        normal.y = poly.getV(k).norm[2]
                        normal.z = poly.getV(k).norm[1]

                        // LWO models aren't all that pretty when it comes down to the floating point values they store
                        normal.FixDegenerateNormal()
                        tv = 0
                        color[0] = (lwoSurf.color.rgb[0] * 255).toInt().toByte()
                        color[1] = (lwoSurf.color.rgb[1] * 255).toInt().toByte()
                        color[2] = (lwoSurf.color.rgb[2] * 255).toInt().toByte()
                        color[3] = 255.toByte()

                        // first set attributes from the vertex
                        val pt = layer.point.pt[poly.getV(k).index]
                        var nvm: Int
                        nvm = 0
                        while (nvm < pt.nvmaps) {
                            val vm = pt.vm[nvm]
                            if (vm.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                tv = tvRemap[vm.index + vm.vmap.offset]
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    color[chan] = (255 * vm.vmap.`val`[vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // then override with polygon attributes
                        nvm = 0
                        while (nvm < poly.getV(k).nvmaps) {
                            val vm = poly.getV(k).vm[nvm]
                            if (vm.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                tv = tvRemap[vm.index + vm.vmap.offset]
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    color[chan] = (255 * vm.vmap.`val`[vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash[v]
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!mv.color.contentEquals(color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]!!
                            mv.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes[tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > layer.polygon.count * 3) {
                    Common.common.FatalError("ConvertLWOToModelSurfaces: index miscount in LWO file %s", name)
                }
                if (tri.numVerts > layer.polygon.count * 3) {
                    Common.common.FatalError("ConvertLWOToModelSurfaces: vertex miscount in LWO file %s", name)
                }

                // now allocate and generate the combined vertexes
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]!!
                    tri.verts[j].Clear()
                    tri.verts[j].xyz.set(vList[mv.v])
                    tri.verts[j].st = tvList[mv.tv]
                    tri.verts[j].normal.set(mv.normal)
                    tri.verts[j].color = mv.color
                    j++
                }
                //
//                R_StaticFree(mvTable);
//                R_StaticFree(mvHash);

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[i]]
                val mergeTri = modelSurf.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = tr_trisurf.R_MergeTriangles(mergeTri, tri)
                    tr_trisurf.R_FreeStaticTriSurf(tri)
                    tr_trisurf.R_FreeStaticTriSurf(mergeTri)
                }
                lwoSurf = lwoSurf.next
                i++
            }
            //
//            R_StaticFree(tvRemap);
//            R_StaticFree(vRemap);
//            R_StaticFree(tvList);
//            R_StaticFree(vList);
            return true
        }

        fun ConvertMAToModelSurfaces(ma: maModel_s): Boolean {
            var `object`: maObject_t?
            var mesh: maMesh_t
            var material: maMaterial_t?
            var im1: idMaterial?
            var im2: idMaterial?
            var tri: srfTriangles_s?
            var objectNum: Int
            var i: Int
            var j: Int
            var k: Int
            var v: Int
            var tv: Int
            var vRemap: IntArray
            var tvRemap: IntArray
            var mvTable: kotlin.collections.ArrayList<matchVert_s> // all of the match verts
            var mvHash: ArrayList<matchVert_s> // points inside mvTable for each xyz index
            var lastmv: matchVert_s?
            var mv: matchVert_s?
            val normal = idVec3()
            var uOffset: Float
            var vOffset: Float
            var textureSin: Float
            var textureCos: Float
            var uTiling: Float
            var vTiling: Float
            val mergeTo: IntArray
            var color: ByteArray
            val surf = modelSurface_s()
            var modelSurf: modelSurface_s?
            if (TempDump.NOT(ma)) {
                return false
            }
            if (ma.objects.size < 1) {
                return false
            }
            timeStamp[0] = ma.timeStamp[0]

            // the modeling programs can save out multiple surfaces with a common
            // material, but we would like to mege them tgether where possible
            // meaning that this.NumSurfaces() <= ma.objects.currentElements
            mergeTo = IntArray(ma.objects.size)
            surf.geometry = null
            if (ma.materials.size == 0) {
                // if we don't have any materials, dump everything into a single surface
                surf.shader = tr_local.tr.defaultMaterial
                surf.id = 0
                AddSurface(surf)
                i = 0
                while (i < ma.objects.size) {
                    mergeTo[i] = 0
                    i++
                }
            } else if (!r_mergeModelSurfaces.GetBool()) {
                // don't merge any
                i = 0
                while (i < ma.objects.size) {
                    mergeTo[i] = i
                    `object` = ma.objects[i]
                    if (`object`.materialRef >= 0) {
                        material = ma.materials[`object`.materialRef]
                        surf.shader = DeclManager.declManager.FindMaterial(material.name)
                    } else {
                        surf.shader = tr_local.tr.defaultMaterial
                    }
                    surf.id = NumSurfaces()
                    AddSurface(surf)
                    i++
                }
            } else {
                // search for material matches
                i = 0
                while (i < ma.objects.size) {
                    `object` = ma.objects[i]
                    if (`object`.materialRef >= 0) {
                        material = ma.materials[`object`.materialRef]
                        im1 = DeclManager.declManager.FindMaterial(material.name)
                    } else {
                        im1 = tr_local.tr.defaultMaterial
                    }
                    if (im1!!.IsDiscrete()) {
                        // flares, autosprites, etc
                        j = NumSurfaces()
                    } else {
                        j = 0
                        while (j < NumSurfaces()) {
                            modelSurf = surfaces[j]
                            im2 = modelSurf.shader
                            if (im1 === im2) {
                                // merge this
                                mergeTo[i] = j
                                break
                            }
                            j++
                        }
                    }
                    if (j == NumSurfaces()) {
                        // didn't merge
                        mergeTo[i] = j
                        surf.shader = im1
                        surf.id = NumSurfaces()
                        AddSurface(surf)
                    }
                    i++
                }
            }
            val vertexSubset = idVectorSubset<idVec3>(3)
            val texCoordSubset = idVectorSubset<idVec2>(3)

            // build the surfaces
            objectNum = 0
            while (objectNum < ma.objects.size) {
                `object` = ma.objects[objectNum]
                mesh = `object`.mesh!!
                if (`object`.materialRef >= 0) {
                    material = ma.materials[`object`.materialRef]
                    im1 = DeclManager.declManager.FindMaterial(material.name)
                } else {
                    im1 = tr_local.tr.defaultMaterial
                }
                var normalsParsed = mesh.normalsParsed

                // completely ignore any explict normals on surfaces with a renderbump command
                // which will guarantee the best contours and least vertexes.
                val rb: String = im1!!.GetRenderBump()
                if (rb != null && !rb.isEmpty()) {
                    normalsParsed = false
                }

                // It seems like the tools our artists are using often generate
                // verts and texcoords slightly separated that should be merged
                // note that we really should combine the surfaces with common materials
                // before doing this operation, because we can miss a slop combination
                // if they are in different surfaces
                vRemap = IntArray(mesh.numVertexes) // R_StaticAlloc(mesh.numVertexes /* sizeof( vRemap[0] )*/);
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = j
                        j++
                    }
                } else {
                    val vertexEpsilon = r_slopVertex.GetFloat()
                    val expand = 2 * 32 * vertexEpsilon
                    val mins = idVec3()
                    val maxs = idVec3()
                    Simd.SIMDProcessor.MinMax(mins, maxs, mesh.vertexes.toTypedArray(), mesh.numVertexes)
                    mins.minusAssign(idVec3(expand, expand, expand))
                    maxs.plusAssign(idVec3(expand, expand, expand))
                    vertexSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numVertexes) {
                        vRemap[j] = vertexSubset.FindVector(mesh.vertexes as Array<Vector.idVec<*>>, j, vertexEpsilon)
                        j++
                    }
                }
                tvRemap = IntArray(mesh.numTVertexes) // R_StaticAlloc(mesh.numTVertexes /* sizeof( tvRemap[0] ) */);
                if (fastLoad) {
                    // renderbump doesn't care about vertex count
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] = j
                        j++
                    }
                } else {
                    val texCoordEpsilon = r_slopTexCoord.GetFloat()
                    val expand = 2 * 32 * texCoordEpsilon
                    val mins = idVec2()
                    val maxs = idVec2()
                    Simd.SIMDProcessor.MinMax(mins, maxs, mesh.tvertexes.toTypedArray(), mesh.numTVertexes)
                    mins.minusAssign(idVec2(expand, expand))
                    maxs.plusAssign(idVec2(expand, expand))
                    texCoordSubset.Init(mins, maxs, 32, 1024)
                    j = 0
                    while (j < mesh.numTVertexes) {
                        tvRemap[j] =
                            texCoordSubset.FindVector(mesh.tvertexes as Array<Vector.idVec<*>>, j, texCoordEpsilon)
                        j++
                    }
                }

                // we need to find out how many unique vertex / texcoord / color combinations
                // there are, because MA tracks them separately but we need them unified
                // the maximum possible number of combined vertexes is the number of indexes
                mvTable =
                    ArrayList<matchVert_s>(mesh.numFaces * 3) // R_ClearedStaticAlloc(mesh.numFaces * 3 /* sizeof( mvTable[0] )*/);

                // we will have a hash chain based on the xyz values
                mvHash =
                    ArrayList<matchVert_s>(mesh.numFaces) // R_ClearedStaticAlloc(mesh.numVertexes /* sizeof( mvHash[0] )*/);

                // allocate triangle surface
                tri = tr_trisurf.R_AllocStaticTriSurf()
                tri.numVerts = 0
                tri.numIndexes = 0
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, mesh.numFaces * 3)
                tri.generateNormals = !normalsParsed

                // init default normal, color and tex coord index
                normal.Zero()
                color = identityColor
                tv = 0

                // find all the unique combinations
                val normalEpsilon = 1.0f - r_slopNormal.GetFloat()
                j = 0
                while (j < mesh.numFaces) {
                    k = 0
                    while (k < 3) {
                        v = mesh.faces[j].vertexNum[k]
                        if (v < 0 || v >= mesh.numVertexes) {
                            Common.common.Error("ConvertMAToModelSurfaces: bad vertex index in MA file %s", name)
                        }

                        // collapse the position if it was slightly offset
                        v = vRemap[v]

                        // we may or may not have texcoords to compare
                        if (mesh.numTVertexes != 0) {
                            tv = mesh.faces[j].tVertexNum[k]
                            if (tv < 0 || tv >= mesh.numTVertexes) {
                                Common.common.Error("ConvertMAToModelSurfaces: bad tex coord index in MA file %s", name)
                            }
                            // collapse the tex coord if it was slightly offset
                            tv = tvRemap[tv]
                        }

                        // we may or may not have normals to compare
                        if (normalsParsed) {
                            normal.set(mesh.faces[j].vertexNormals[k])
                        }

                        //BSM: Todo: Fix the vertex colors
                        // we may or may not have colors to compare
                        if (mesh.faces[j].vertexColors[k] != -1 && mesh.faces[j].vertexColors[k] != -999) {
                            val offset = mesh.faces[j].vertexColors[k] * 4
                            color = Arrays.copyOfRange(mesh.colors, offset, offset + 4)
                        }

                        // find a matching vert
                        lastmv = null
                        mv = mvHash.getOrNull(v)
                        while (mv != null) {
                            if (mv.tv != tv) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!Arrays.equals(mv.color, color)) {
                                lastmv = mv
                                mv = mv.next
                                continue
                            }
                            if (!normalsParsed) {
                                // if we are going to create the normals, just
                                // matching texcoords is enough
                                break
                            }
                            if (mv.normal.times(normal) > normalEpsilon) {
                                break // we already have this one
                            }
                            lastmv = mv
                            mv = mv.next
                        }
                        if (null == mv) {
                            // allocate a new match vert and link to hash chain
                            mvTable[tri.numVerts] = matchVert_s(tri.numVerts)
                            mv = mvTable[tri.numVerts]
                            mv.v = v
                            mv.tv = tv
                            mv.normal.set(normal)
                            System.arraycopy(color, 0, mv.color, 0, color.size)
                            mv.next = null
                            if (lastmv != null) {
                                lastmv.next = mv
                            } else {
                                mvHash[v] = mv
                            }
                            tri.numVerts++
                        }
                        tri.indexes[tri.numIndexes] = mv.index
                        tri.numIndexes++
                        k++
                    }
                    j++
                }

                // allocate space for the indexes and copy them
                if (tri.numIndexes > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertMAToModelSurfaces: index miscount in MA file %s", name)
                }
                if (tri.numVerts > mesh.numFaces * 3) {
                    Common.common.FatalError("ConvertMAToModelSurfaces: vertex miscount in MA file %s", name)
                }

                // an MA allows the texture coordinates to be scaled, translated, and rotated
                //BSM: Todo: Does Maya support this and if so how
                //if ( ase.materials.size == 0 ) {
                vOffset = 0.0f
                uOffset = vOffset
                vTiling = 1.0f
                uTiling = vTiling
                textureSin = 0.0f
                textureCos = 1.0f
                //} else {
                //	material = ase.materials[object.materialRef];
                //	uOffset = -material.uOffset;
                //	vOffset = material.vOffset;
                //	uTiling = material.uTiling;
                //	vTiling = material.vTiling;
                //	textureSin = idMath::Sin( material.angle );
                //	textureCos = idMath::Cos( material.angle );
                //}

                // now allocate and generate the combined vertexes
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    mv = mvTable[j]
                    tri.verts[j].Clear()
                    tri.verts[j].xyz.set(mesh.vertexes[mv.v])
                    tri.verts[j].normal.set(mv.normal)
                    tri.verts[j].color = mv.color
                    if (mesh.numTVertexes != 0) {
                        val tv2 = mesh.tvertexes[mv.tv]
                        val U = tv2.x * uTiling + uOffset
                        val V = tv2.y * vTiling + vOffset
                        tri.verts[j].st[0] = U * textureCos + V * textureSin
                        tri.verts[j].st[1] = U * -textureSin + V * textureCos
                    }
                    j++
                }
                //
//                R_StaticFree(mvTable);
//                R_StaticFree(mvHash);
//                R_StaticFree(tvRemap);
//                R_StaticFree(vRemap);

                // see if we need to merge with a previous surface of the same material
                modelSurf = surfaces[mergeTo[objectNum]]
                val mergeTri = modelSurf.geometry
                if (null == mergeTri) {
                    modelSurf.geometry = tri
                } else {
                    modelSurf.geometry = tr_trisurf.R_MergeTriangles(mergeTri, tri)
                    tr_trisurf.R_FreeStaticTriSurf(tri)
                    tr_trisurf.R_FreeStaticTriSurf(mergeTri)
                }
                objectNum++
            }
            return true
        }

        //	static short []identityColor/*[4]*/ = { 255, 255, 255, 255 };
        fun ConvertLWOToASE(obj: lwObject?, fileName: String): aseModel_s? {
            var j: Int
            var k: Int
            val ase: aseModel_s
            if (null == obj) {
                return null
            }

            // NOTE: using new operator because aseModel_s contains idList class objects
            ase = aseModel_s()
            ase.timeStamp[0] = obj.timeStamp[0]
            ase.objects.ensureCapacity(obj.nlayers)
            var materialRef = 0
            var surf = obj.surf
            while (surf != null) {
                val mat = aseMaterial_t() // Mem_ClearedAlloc(sizeof( * mat));
                System.arraycopy(surf.name.toCharArray(), 0, mat.name, 0, surf.name.length)
                mat.vTiling = 1f
                mat.uTiling = mat.vTiling
                mat.vOffset = 0f
                mat.uOffset = mat.vOffset
                mat.angle = mat.uOffset
                ase.materials.add(mat)
                val layer = obj.layer
                val `object` = aseObject_t() // Mem_ClearedAlloc(sizeof( * object));
                `object`.materialRef = materialRef++
                val mesh = `object`.mesh
                ase.objects.add(`object`)
                mesh.numFaces = layer.polygon.count
                mesh.numTVFaces = mesh.numFaces
                mesh.faces = ArrayList(mesh.numFaces) // Mem_Alloc(mesh.numFaces /* sizeof( mesh.faces[0] )*/);
                mesh.numVertexes = layer.point.count
                mesh.vertexes = ArrayList(arrayListOf(*idVec3.generateArray(mesh.numVertexes)))
                // Mem_Alloc(mesh.numVertexes /* sizeof( mesh.vertexes[0] )*/);

                // vertex positions
                if (layer.point.count <= 0) {
                    Common.common.Warning("ConvertLWOToASE: model '%s' has bad or missing vertex data", name)
                }
                j = 0
                while (j < layer.point.count) {
                    mesh.vertexes[j].x = layer.point.pt[j].pos[0]
                    mesh.vertexes[j].y = layer.point.pt[j].pos[2]
                    mesh.vertexes[j].z = layer.point.pt[j].pos[1]
                    j++
                }

                // vertex texture coords
                mesh.numTVertexes = 0
                if (layer.nvmaps != 0) {
                    var vm = layer.vmap as Model_lwo.lwVMap?
                    while (vm != null) {
                        if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                            mesh.numTVertexes += vm.nverts
                        }
                        vm = vm.next
                    }
                }
                if (mesh.numTVertexes != 0) {
                    mesh.tvertexes =
                        ArrayList(mesh.numTVertexes) // Mem_Alloc(mesh.numTVertexes /* sizeof( mesh.tvertexes[0] )*/);
                    var offset = 0
                    var vm = layer.vmap as Model_lwo.lwVMap?
                    while (vm != null) {
                        if (vm.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                            vm.offset = offset
                            k = 0
                            while (k < vm.nverts) {
                                mesh.tvertexes[k + offset].x = vm.`val`[k][0]
                                mesh.tvertexes[k + offset].y = 1.0f - vm.`val`[k][1] // invert the t
                                k++
                            }
                            offset += vm.nverts
                        }
                        vm = vm.next
                    }
                } else {
                    Common.common.Warning("ConvertLWOToASE: model '%s' has bad or missing uv data", fileName)
                    mesh.numTVertexes = 1
                    mesh.tvertexes =
                        ArrayList(mesh.numTVertexes) // Mem_ClearedAlloc(mesh.numTVertexes /* sizeof( mesh.tvertexes[0] )*/);
                }
                mesh.normalsParsed = true
                mesh.colorsParsed = true // because we are falling back to the surface color

                // triangles
                var faceIndex = 0
                j = 0
                while (j < layer.polygon.count) {
                    val poly = layer.polygon.pol[j]
                    if (poly.surf !== surf) {
                        j++
                        continue
                    }
                    if (poly.nverts != 3) {
                        Common.common.Warning(
                            "ConvertLWOToASE: model %s has too many verts for a poly! Make sure you triplet it down",
                            fileName
                        )
                        j++
                        continue
                    }
                    mesh.faces[faceIndex].faceNormal.x = poly.norm[0]
                    mesh.faces[faceIndex].faceNormal.y = poly.norm[2]
                    mesh.faces[faceIndex].faceNormal.z = poly.norm[1]
                    k = 0
                    while (k < 3) {
                        mesh.faces[faceIndex].vertexNum[k] = poly.getV(k).index
                        mesh.faces[faceIndex].vertexNormals[k].x = poly.getV(k).norm[0]
                        mesh.faces[faceIndex].vertexNormals[k].y = poly.getV(k).norm[2]
                        mesh.faces[faceIndex].vertexNormals[k].z = poly.getV(k).norm[1]

                        // complete fallbacks
                        mesh.faces[faceIndex].tVertexNum[k] = 0
                        mesh.faces[faceIndex].vertexColors[k][0] = (surf.color.rgb[0] * 255).toInt().toByte()
                        mesh.faces[faceIndex].vertexColors[k][1] = (surf.color.rgb[1] * 255).toInt().toByte()
                        mesh.faces[faceIndex].vertexColors[k][2] = (surf.color.rgb[2] * 255).toInt().toByte()
                        mesh.faces[faceIndex].vertexColors[k][3] = 255.toByte()

                        // first set attributes from the vertex
                        val pt = layer.point.pt[poly.getV(k).index]
                        var nvm: Int
                        nvm = 0
                        while (nvm < pt.nvmaps) {
                            val vm = pt.vm[nvm]
                            if (vm.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                mesh.faces[faceIndex].tVertexNum[k] = vm.index + vm.vmap.offset
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    mesh.faces[faceIndex].vertexColors[k][chan] =
                                        (255 * vm.vmap.`val`[vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }

                        // then override with polygon attributes
                        nvm = 0
                        while (nvm < poly.getV(k).nvmaps) {
                            val vm = poly.getV(k).vm[nvm]
                            if (vm.vmap.type == Model_lwo.LWID_('T', 'X', 'U', 'V').toLong()) {
                                mesh.faces[faceIndex].tVertexNum[k] = vm.index + vm.vmap.offset
                            }
                            if (vm.vmap.type == Model_lwo.LWID_('R', 'G', 'B', 'A').toLong()) {
                                for (chan in 0..3) {
                                    mesh.faces[faceIndex].vertexColors[k][chan] =
                                        (255 * vm.vmap.`val`[vm.index][chan]).toInt().toByte()
                                }
                            }
                            nvm++
                        }
                        k++
                    }
                    faceIndex++
                    j++
                }
                mesh.numFaces = faceIndex
                mesh.numTVFaces = faceIndex
                val newFaces =
                    kotlin.collections.ArrayList<aseFace_t>(mesh.numFaces) // Mem_Alloc(mesh.numFaces /* sizeof ( mesh.faces[0] ) */);
                //		memcpy( newFaces, mesh.faces, sizeof( mesh.faces[0] ) * mesh.numFaces );
                for (i in 0 until mesh.numFaces) {
                    newFaces[i] = mesh.faces[i]
                }
                //                Mem_Free(mesh.faces);
                mesh.faces = newFaces
                surf = surf.next
            }
            return ase
        }

        fun DeleteSurfaceWithId(id: Int): Boolean {
            var i: Int
            i = 0
            while (i < surfaces.size) {
                if (surfaces[i].id == id) {
                    tr_trisurf.R_FreeStaticTriSurf(surfaces[i].geometry)
                    surfaces.removeAt(i)
                    return true
                }
                i++
            }
            return false
        }

        fun DeleteSurfacesWithNegativeId() {
            var i: Int
            i = 0
            while (i < surfaces.size) {
                if (surfaces[i].id < 0) {
                    tr_trisurf.R_FreeStaticTriSurf(surfaces[i].geometry)
                    surfaces.removeAt(i)
                    i--
                }
                i++
            }
        }

        fun FindSurfaceWithId(id: Int, surfaceNum: CInt): Boolean {
            var i: Int
            i = 0
            while (i < surfaces.size) {
                if (surfaces[i].id == id) {
                    surfaceNum._val = (i)
                    return true
                }
                i++
            }
            return false
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        internal class matchVert_s     //                return Stream.
        //                        generate(matchVert_s::new).
        //                        limit(length).
        //                        toArray(matchVert_s[]::new);
        //            }
            (val index: Int) {
            var color: ByteArray = ByteArray(4)
            var next: matchVert_s? = null
            val normal: idVec3 = idVec3()
            var v = 0
            var tv = 0

            //            static int getPosition(matchVert_s v1, matchVert_s[] vList) {
            //                int i;
            //
            //                for (i = 0; i < vList.length; i++) {
            //                    if (vList[i].equals(v1)) {
            //                        break;
            //                    }
            //                }
            //
            //                return i;
            //            }
            override fun hashCode(): Int {
                var result = v
                result = 31 * result + tv
                return result
            }

            override fun equals(o: Any?): Boolean {
                if (this === o) return true
                if (o == null || javaClass != o.javaClass) return false
                val that = o as matchVert_s
                return if (v != that.v) false else tv == that.tv
            } //            static matchVert_s[] generateArray(final int length) {
        }

        companion object {
            //
            protected val r_mergeModelSurfaces: idCVar = idCVar(
                "r_mergeModelSurfaces",
                "1",
                CVarSystem.CVAR_BOOL or CVarSystem.CVAR_RENDERER,
                "combine model surfaces with the same material"
            )
            protected val r_slopNormal: idCVar =
                idCVar("r_slopNormal", "0.02", CVarSystem.CVAR_RENDERER, "merge normals that dot less than this")
            protected val r_slopTexCoord: idCVar =
                idCVar("r_slopTexCoord", "0.001", CVarSystem.CVAR_RENDERER, "merge texture coordinates this far apart")
            protected val r_slopVertex: idCVar =
                idCVar("r_slopVertex", "0.01", CVarSystem.CVAR_RENDERER, "merge xyz coordinates this far apart")
            val identityColor /*[4]*/: ByteArray = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())
            private const val DBG_ConvertASEToModelSurfaces = 0
            private var DBG_ConvertLWOToModelSurfaces = 0

            /*
         ================
         idRenderModelStatic::FinishSurfaces

         The mergeShadows option allows surfaces with different textures to share
         silhouette edges for shadow calculation, instead of leaving shared edges
         hanging.

         If any of the original shaders have the noSelfShadow flag set, the surfaces
         can't be merged, because they will need to be drawn in different order.

         If there is only one surface, a separate merged surface won't be generated.

         A model with multiple surfaces can't later have a skinned shader change the
         state of the noSelfShadow flag.

         -----------------

         Creates mirrored copies of two sided surfaces with normal maps, which would
         otherwise light funny.

         Extends the bounds of deformed surfaces so they don't cull incorrectly at screen edges.

         ================
         */
            private var DBG_FinishSurfaces = 0
        }

        // the inherited public interface
        init {
            surfaces = kotlin.collections.ArrayList()
            name = idStr("<undefined>")
            idBounds().also { bounds = it }.Clear()
            lastModifiedFrame = 0
            lastArchivedFrame = 0
            overlaysAdded = 0
            shadowHull = null
            isStaticWorldModel = false
            defaulted = false
            purged = false
            fastLoad = false
            reloadable = true
            levelLoadReferenced = false
            timeStamp[0] = 0
        }
    }
}