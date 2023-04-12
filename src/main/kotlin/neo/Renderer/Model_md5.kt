package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idMD5Joint
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.deformInfo_s
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_trisurf.R_DeriveTangents
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.Session
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.Simd
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object Model_md5 {
    val MD5_SnapshotName: String = "_MD5_Snapshot_"

    /**
     * *********************************************************************
     *
     *
     * idMD5Mesh
     *
     *
     * *********************************************************************
     */
    var c_numVerts = 0
    var c_numWeightJoints = 0
    var c_numWeights = 0

    internal class vertexWeight_s {
        var joint = 0
        var jointWeight = 0f
        val offset: idVec3
        var vert = 0

        init {
            offset = idVec3()
        }
    }

    /*
     ===============================================================================

     MD5 animated model

     ===============================================================================
     */
    class idMD5Mesh {
        // friend class				idRenderModelMD5;
        lateinit var deformInfo // used to create srfTriangles_t from base frames and new vertexes
                : deformInfo_s
        var numTris // number of triangles
                : Int
        var numWeights // number of weights
                = 0
        private var scaledWeights // joint weights
                : ArrayList<idVec4> = ArrayList()
        lateinit var shader // material applied to mesh
                : idMaterial
        var surfaceNum // number of the static surface created for this mesh
                : Int
        var texCoords // texture coordinates
                : ArrayList<idVec2> = ArrayList()
        private lateinit var weightIndex // pairs of: joint offset + bool true if next weight is for next vertex
                : IntArray

        // ~idMD5Mesh();
        @Throws(idException::class)
        fun ParseMesh(parser: idLexer, numJoints: Int, joints: Array<idJointMat>) {
            val token = idToken()
            val name = idToken()
            var num: Int
            var count: Int
            var jointnum: Int
            val shaderName: idStr
            var i: Int
            var j: Int
            val tris = ArrayList<Int>()
            val firstWeightForVertex = ArrayList<Int>()
            val numWeightsForVertex = ArrayList<Int>()
            var maxweight: Int
            val tempWeights = ArrayList<vertexWeight_s>()
            parser.ExpectTokenString("{")

            //
            // parse name
            //
            if (parser.CheckTokenString("name")) {
                parser.ReadToken(name)
            }

            //
            // parse shader
            //
            parser.ExpectTokenString("shader")
            parser.ReadToken(token)
            shaderName = token
            shader = DeclManager.declManager.FindMaterial(shaderName)!!

            //
            // parse texture coordinates
            //
            parser.ExpectTokenString("numverts")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %s", token.toString())
            }
            val texCoordsSize = count
            texCoords.ensureCapacity(count)
            firstWeightForVertex.ensureCapacity(count)
            numWeightsForVertex.ensureCapacity(count)
            numWeights = 0
            maxweight = 0
            i = 0
            while (i < texCoordsSize) { // texCoords.size
                parser.ExpectTokenString("vert")
                parser.ParseInt()
                texCoords.add(i, idVec2())
                parser.Parse1DMatrix(2, texCoords[i])
                firstWeightForVertex.add(i, parser.ParseInt())
                numWeightsForVertex.add(i, parser.ParseInt())
                if (0 == numWeightsForVertex[i]) {
                    parser.Error("Vertex without any joint weights.")
                }
                numWeights += numWeightsForVertex[i]
                if (numWeightsForVertex[i] + firstWeightForVertex[i] > maxweight) {
                    maxweight = numWeightsForVertex[i] + firstWeightForVertex[i]
                }
                i++
            }

            //
            // parse tris
            //
            parser.ExpectTokenString("numtris")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %d", count)
            }
            tris.ensureCapacity(count * 3)
            numTris = count
            i = 0
            while (i < count) {
                parser.ExpectTokenString("tri")
                parser.ParseInt()
                tris.add(i * 3 + 0, parser.ParseInt())
                tris.add(i * 3 + 1, parser.ParseInt())
                tris.add(i * 3 + 2, parser.ParseInt())
                i++
            }

            //
            // parse weights
            //
            parser.ExpectTokenString("numweights")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %d", count)
            }
            if (maxweight > count) {
                parser.Warning("Vertices reference out of range weights in model (%d of %d weights).", maxweight, count)
            }
            tempWeights.ensureCapacity(count)
            i = 0
            while (i < count) {
                parser.ExpectTokenString("weight")
                parser.ParseInt()
                jointnum = parser.ParseInt()
                if (jointnum < 0 || jointnum >= numJoints) {
                    parser.Error("Joint Index out of range(%d): %d", numJoints, jointnum)
                }
                tempWeights.add(i, vertexWeight_s())
                tempWeights[i].joint = jointnum
                tempWeights[i].jointWeight = parser.ParseFloat()
                parser.Parse1DMatrix(3, tempWeights[i].offset)
                i++
            }

            // create pre-scaled weights and an index for the vertex/joint lookup
            scaledWeights.ensureCapacity(numWeights)
            weightIndex = IntArray(numWeights * 2) // Mem_Alloc16(numWeights * 2 /* sizeof( weightIndex[0] ) */);
            //	memset( weightIndex, 0, numWeights * 2 * sizeof( weightIndex[0] ) );
            count = 0
            i = 0
            while (i < texCoords.size) {
                num = firstWeightForVertex[i]
                j = 0
                while (j < numWeightsForVertex[i]) {
                    scaledWeights.add(count, idVec4())
                    scaledWeights[count]
                        .set(tempWeights[num].offset.times(tempWeights[num].jointWeight))
                    scaledWeights[count].w = tempWeights[num].jointWeight
                    weightIndex[count * 2 + 0] = tempWeights[num].joint * idJointMat.SIZE
                    j++
                    num++
                    count++
                }
                weightIndex[count * 2 - 1] = 1
                i++
            }
            tempWeights.clear()
            numWeightsForVertex.clear()
            firstWeightForVertex.clear()
            parser.ExpectTokenString("}")

            // update counters
            Model_md5.c_numVerts += texCoords.size
            Model_md5.c_numWeights += numWeights
            Model_md5.c_numWeightJoints++
            i = 0
            while (i < numWeights) {
                Model_md5.c_numWeightJoints += weightIndex[i * 2 + 1]
                i++
            }

            //
            // build the information that will be common to all animations of this mesh:
            // silhouette edge connectivity and normal / tangent generation information
            //
            val verts = ArrayList<idDrawVert>(texCoords.size)
            i = 0
            while (i < texCoords.size) {
                verts.add(i, idDrawVert())
                verts[i].Clear()
                verts[i].st = texCoords[i]
                i++
            }
            TransformVerts(verts.toTypedArray(), joints)
            deformInfo =
                tr_trisurf.R_BuildDeformInfo(texCoords.size, verts, tris.size, tris, shader.UseUnsmoothedTangents())
        }

        fun UpdateSurface(ent: renderEntity_s, entJoints: Array<idJointMat>, surf: modelSurface_s) {
            var i: Int
            val base: Int
            val tri: srfTriangles_s
            tr_local.tr.pc.c_deformedSurfaces++
            tr_local.tr.pc.c_deformedVerts += deformInfo.numOutputVerts
            tr_local.tr.pc.c_deformedIndexes += deformInfo.numIndexes
            surf.shader = shader
            if (surf.geometry != null) {
                // if the number of verts and indexes are the same we can re-use the triangle surface
                // the number of indexes must be the same to assure the correct amount of memory is allocated for the facePlanes
                if (surf.geometry!!.numVerts == deformInfo.numOutputVerts && surf.geometry!!.numIndexes == deformInfo.numIndexes) {
                    tr_trisurf.R_FreeStaticTriSurfVertexCaches(surf.geometry!!)
                } else {
                    tr_trisurf.R_FreeStaticTriSurf(surf.geometry)
                    surf.geometry = tr_trisurf.R_AllocStaticTriSurf()
                }
            } else {
                surf.geometry = tr_trisurf.R_AllocStaticTriSurf()
            }
            tri = surf.geometry!!

            // note that some of the data is references, and should not be freed
            tri.deformedSurface = true
            tri.tangentsCalculated = false
            tri.facePlanesCalculated = false
            tri.numIndexes = deformInfo.numIndexes
            tri.indexes = deformInfo.indexes
            tri.silIndexes = deformInfo.silIndexes
            tri.numMirroredVerts = deformInfo.numMirroredVerts
            tri.mirroredVerts = deformInfo.mirroredVerts
            tri.numDupVerts = deformInfo.numDupVerts
            tri.dupVerts = deformInfo.dupVerts
            tri.numSilEdges = deformInfo.numSilEdges
            tri.silEdges = deformInfo.silEdges
            tri.dominantTris = deformInfo.dominantTris
            tri.numVerts = deformInfo.numOutputVerts
            if (tri.verts.isEmpty()) {
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                i = 0
                while (i < deformInfo.numSourceVerts) {
                    tri.verts[i].Clear()
                    tri.verts[i].st.set(texCoords[i])
                    i++
                }
            }
            if (ent.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE] != 0.0f) {
                TransformScaledVerts(
                    tri.verts.toTypedArray(),
                    entJoints,
                    ent.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE]
                )
            } else {
                TransformVerts(tri.verts.toTypedArray(), entJoints)
            }

            // replicate the mirror seam vertexes
            base = deformInfo.numOutputVerts - deformInfo.numMirroredVerts
            i = 0
            while (i < deformInfo.numMirroredVerts) {
                tri.verts[base + i] = tri.verts[deformInfo.mirroredVerts[i]]
                i++
            }
            tr_trisurf.R_BoundTriSurf(tri)

            // If a surface is going to be have a lighting interaction generated, it will also have to call
            // R_DeriveTangents() to get normals, tangents, and face planes.  If it only
            // needs shadows generated, it will only have to generate face planes.  If it only
            // has ambient drawing, or is culled, no additional work will be necessary
            if (!RenderSystem_init.r_useDeferredTangents.GetBool()) {
                // set face planes, vertex normals, tangents
                R_DeriveTangents(tri)
            }
        }

        fun CalcBounds(entJoints: Array<idJointMat>): idBounds {
            val bounds = idBounds()
            val verts = Array<idDrawVert>(texCoords.size) { idDrawVert() }
            TransformVerts(verts, entJoints)
            Simd.SIMDProcessor.MinMax(bounds[0], bounds[1], verts, texCoords.size)
            return bounds
        }

        fun NearestJoint(a: Int, b: Int, c: Int): Int {
            var i: Int
            var bestJoint: Int
            val vertNum: Int
            var weightVertNum: Int
            var bestWeight: Float

            // duplicated vertices might not have weights
            vertNum = if (a >= 0 && a < texCoords.size) {
                a
            } else if (b >= 0 && b < texCoords.size) {
                b
            } else if (c >= 0 && c < texCoords.size) {
                c
            } else {
                // all vertices are duplicates which shouldn't happen
                return 0
            }

            // find the first weight for this vertex
            weightVertNum = 0
            i = 0
            while (weightVertNum < vertNum) {
                weightVertNum += weightIndex[i * 2 + 1] * idJointMat.SIZE
                i++
            }

            // get the joint for the largest weight
            bestWeight = scaledWeights[i].w
            bestJoint = weightIndex[i * 2 + 0] / idJointMat.SIZE
            while (weightIndex[i * 2 + 1] == 0) {
                if (scaledWeights[i].w > bestWeight) {
                    bestWeight = scaledWeights[i].w
                    bestJoint = weightIndex[i * 2 + 0] / idJointMat.SIZE
                }
                i++
            }
            return bestJoint
        }

        fun NumVerts(): Int {
            return texCoords.size
        }

        fun NumTris(): Int {
            return numTris
        }

        fun NumWeights(): Int {
            return numWeights
        }

        private fun TransformVerts(verts: Array<idDrawVert>, entJoints: Array<idJointMat>) {
            Simd.SIMDProcessor.TransformVerts(
                verts,
                texCoords.size,
                entJoints,
                scaledWeights.toTypedArray(),
                weightIndex,
                numWeights
            )
        }

        /*
         ====================
         idMD5Mesh::TransformScaledVerts

         Special transform to make the mesh seem fat or skinny.  May be used for zombie deaths
         ====================
         */
        private fun TransformScaledVerts(verts: Array<idDrawVert>, entJoints: Array<idJointMat>, scale: Float) {
            val scaledWeights = Array<idVec4>(numWeights) { idVec4() }
            Simd.SIMDProcessor.Mul(scaledWeights[0].ToFloatPtr(), scale, scaledWeights[0].ToFloatPtr(), numWeights * 4)
            Simd.SIMDProcessor.TransformVerts(verts, texCoords.size, entJoints, scaledWeights, weightIndex, numWeights)
        }

        //
        //
        init {
            texCoords = ArrayList()
            scaledWeights = ArrayList()
            numTris = 0
            surfaceNum = 0
        }
    }

    class idRenderModelMD5 : idRenderModelStatic() {
        private val defaultPose: ArrayList<idJointQuat>
        private val joints: ArrayList<idMD5Joint>
        private val meshes: ArrayList<idMD5Mesh>
        override fun InitFromFile(fileName: String) {
            name = idStr(fileName)
            LoadModel()
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CACHED
        }

        /*
         ====================
         idRenderModelMD5::Bounds

         This calculates a rough bounds by using the joint radii without
         transforming all the points
         ====================
         */
        override fun Bounds(ent: renderEntity_s?): idBounds {
//            if (false) {
//                // we can't calculate a rational bounds without an entity,
//                // because joints could be positioned to deform it into an
//                // arbitrarily large shape
//                if (null == ent) {
//                    common.Error("idRenderModelMD5::Bounds: called without entity");
//                }
//            }
            return if (null == ent) {
                // this is the bounds for the reference pose
                bounds
            } else ent.bounds
        }

        override fun Print() {
            var i = 0
            Common.common.Printf("%s\n", name.toString())
            Common.common.Printf("Dynamic model.\n")
            Common.common.Printf("Generated smooth normals.\n")
            Common.common.Printf("    verts  tris weights material\n")
            var totalVerts = 0
            var totalTris = 0
            var totalWeights = 0
            for (mesh in meshes) {
                totalVerts += mesh.NumVerts()
                totalTris += mesh.NumTris()
                totalWeights += mesh.NumWeights()
                Common.common.Printf(
                    "%2d: %5d %5d %7d %s\n",
                    i++,
                    mesh.NumVerts(),
                    mesh.NumTris(),
                    mesh.NumWeights(),
                    mesh.shader.GetName()
                )
            }
            Common.common.Printf("-----\n")
            Common.common.Printf("%4d verts.\n", totalVerts)
            Common.common.Printf("%4d tris.\n", totalTris)
            Common.common.Printf("%4d weights.\n", totalWeights)
            Common.common.Printf("%4d joints.\n", joints.size)
        }

        override fun List() {
            var totalTris = 0
            var totalVerts = 0
            for (mesh in meshes) {
                totalTris += mesh.numTris
                totalVerts += mesh.NumVerts()
            }
            Common.common.Printf(
                " %4dk %3d %4d %4d %s(MD5)",
                Memory() / 1024,
                meshes.size,
                totalVerts,
                totalTris,
                Name()
            )
            if (defaulted) {
                Common.common.Printf(" (DEFAULTED)")
            }
            Common.common.Printf("\n")
        }

        /*
         ====================
         idRenderModelMD5::TouchData

         models that are already loaded at level start time
         will still touch their materials to make sure they
         are kept loaded
         ====================
         */
        override fun TouchData() {
            for (mesh in meshes) {
                DeclManager.declManager.FindMaterial(mesh.shader.GetName())
            }
        }

        /*
         ===================
         idRenderModelMD5::PurgeModel

         frees all the data, but leaves the class around for dangling references,
         which can regenerate the data with LoadModel()
         ===================
         */
        override fun PurgeModel() {
            purged = true
            joints.clear()
            defaultPose.clear()
            meshes.clear()
        }

        /*
         ====================
         idRenderModelMD5::LoadModel

         used for initial loads, reloadModel, and reloading the data of purged models
         Upon exit, the model will absolutely be valid, but possibly as a default model
         ====================
         */
        override fun LoadModel() {
            val version: Int
            var i: Int
            var num: Int
            var parentNum: Int
            val token = idToken()
            val parser = idLexer(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS)
            val poseMat3: Array<idJointMat>
            if (!purged) {
                PurgeModel()
            }
            purged = false
            if (!parser.LoadFile(name)) {
                MakeDefaultModel()
                return
            }
            parser.ExpectTokenString(Model.MD5_VERSION_STRING)
            version = parser.ParseInt()
            if (version != Model.MD5_VERSION) {
                parser.Error("Invalid version %d.  Should be version %d\n", version, Model.MD5_VERSION)
            }

            //
            // skip commandline
            //
            parser.ExpectTokenString("commandline")
            parser.ReadToken(token)

            // parse num joints
            parser.ExpectTokenString("numJoints")
            num = parser.ParseInt()
            val jointsSize = num
            joints.clear()
            joints.ensureCapacity(num) //TODO: useless! Need to make sure it's actually sized for N elements
            defaultPose.clear()
            defaultPose.ensureCapacity(num)
            poseMat3 = Array<idJointMat>(num) { idJointMat() }

            // parse num meshes
            parser.ExpectTokenString("numMeshes")
            num = parser.ParseInt()
            if (num < 0) {
                parser.Error("Invalid size: %d", num)
            }
            meshes.clear()
            meshes.ensureCapacity(num)
            val meshSize = num
            //
            // parse joints
            //
            parser.ExpectTokenString("joints")
            parser.ExpectTokenString("{")
            i = 0
            while (i < jointsSize) { //joint.size == jointsSize
                defaultPose.add(i, idJointQuat())
                val pose = defaultPose[i]
                joints.add(i, idMD5Joint())
                val joint = joints[i]
                ParseJoint(parser, joint, pose)
                //poseMat3[i] = idJointMat()
                poseMat3[i].SetRotation(pose.q.ToMat3())
                poseMat3[i].SetTranslation(pose.t)
                if (joint.parent != null) {
                    parentNum = joints.indexOf(joint.parent)
                    pose.q.set(poseMat3[i].ToMat3().times(poseMat3[parentNum].ToMat3().Transpose()).ToQuat())
                    pose.t.set(
                        poseMat3[i].ToVec3().minus(poseMat3[parentNum].ToVec3())
                            .times(poseMat3[parentNum].ToMat3().Transpose())
                    )
                }
                i++
            }
            parser.ExpectTokenString("}")
            i = 0
            while (i < meshSize) { // meshes.size == meshSize
                meshes.add(i, idMD5Mesh())
                val mesh = meshes[i]
                parser.ExpectTokenString("mesh")
                mesh.ParseMesh(parser, defaultPose.size, poseMat3)
                i++
            }

            //
            // calculate the bounds of the model
            //
            CalculateBounds(poseMat3)

            // set the timestamp for reloadmodels
            idLib.fileSystem.ReadFile(name, null, timeStamp)
        }

        override fun Memory(): Int {
            var total: Int
            total = BYTES
            //total += joints.MemoryUsed() + defaultPose.MemoryUsed() + meshes.MemoryUsed()

            // count up strings
            for (joint in joints) {
                total += joint.name.DynamicMemoryUsed()
            }

            // count up meshes
            for (mesh in meshes) {
                total += idVec4.BYTES * mesh.texCoords.size + mesh.numWeights * idVec4.BYTES + Integer.BYTES * 2

                // sum up deform info
                total += deformInfo_s.BYTES
                total += tr_trisurf.R_DeformInfoMemoryUsed(mesh.deformInfo)
            }
            return total
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel = cachedModel
            val surfaceNum = CInt()
            val staticModel: idRenderModelStatic?
            if (cachedModel != null && !RenderSystem_init.r_useCachedDynamicModels.GetBool()) {
                cachedModel = null
            }
            if (purged) {
                Common.common.DWarning("model %s instantiated while purged", Name())
                LoadModel()
            }
            if (ent.joints.isEmpty()) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: NULL joints on renderEntity for '%s'\n",
                    Name()
                )
                return null
            } else if (ent.numJoints != joints.size) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: renderEntity has different number of joints than model for '%s'\n",
                    Name()
                )
                return null
            }
            tr_local.tr.pc.c_generateMd5++
            if (cachedModel != null) {
                assert(cachedModel is idRenderModelStatic)
                assert(idStr.Icmp(cachedModel.Name(), Model_md5.MD5_SnapshotName) == 0)
                staticModel = cachedModel as idRenderModelStatic
            } else {
                staticModel = idRenderModelStatic()
                staticModel.InitEmpty(Model_md5.MD5_SnapshotName)
            }
            staticModel.bounds.Clear()
            if (RenderSystem_init.r_showSkel.GetInteger() != 0) {
                if (view != null && (!RenderSystem_init.r_skipSuppress.GetBool() || 0 == ent.suppressSurfaceInViewID || ent.suppressSurfaceInViewID != view.renderView.viewID)) {
                    // only draw the skeleton
                    DrawJoints(ent, view)
                }
                if (RenderSystem_init.r_showSkel.GetInteger() > 1) {
                    // turn off the model when showing the skeleton
                    staticModel.InitEmpty(Model_md5.MD5_SnapshotName)
                    return staticModel
                }
            }

            // create all the surfaces
            for (i in 0 until meshes.size) {
                val mesh = meshes[i]

                // avoid deforming the surface if it will be a nodraw due to a skin remapping
                // FIXME: may have to still deform clipping hulls
                var shader: idMaterial? = mesh.shader
                shader = RenderWorld.R_RemapShaderBySkin(shader, ent.customSkin, ent.customShader)
                if (null == shader || !shader.IsDrawn() && !shader.SurfaceCastsShadow()) {
                    staticModel.DeleteSurfaceWithId(i)
                    mesh.surfaceNum = -1
                    continue
                }
                var surf: modelSurface_s?
                if (staticModel.FindSurfaceWithId(i, surfaceNum)) {
                    mesh.surfaceNum = surfaceNum._val
                    surf = staticModel.surfaces[surfaceNum._val]
                } else {

                    // Remove Overlays before adding new surfaces
                    idRenderModelOverlay.RemoveOverlaySurfacesFromModel(staticModel)
                    mesh.surfaceNum = staticModel.NumSurfaces()
                    surf = modelSurface_s()
                    staticModel.surfaces.add(surf)
                    surf.geometry = null
                    surf.shader = null
                    surf.id = i
                }
                mesh.UpdateSurface(ent, ent.joints.toTypedArray(), surf)
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[0])
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[1])
                val a = 0
            }
            return staticModel
        }

        override fun NumJoints(): Int {
            return joints.size
        }

        override fun GetJoints(): ArrayList<idMD5Joint> {
            return joints
        }

        override fun GetJointHandle(name: String): Int {
            var i = 0
            for (joint in joints) {
                if (idStr.Companion.Icmp(joint.name, name) == 0) {
                    return i
                }
                i++
            }
            return Model.INVALID_JOINT
        }

        override fun GetJointName(handle: Int): String {
            return if (handle < 0 || handle >= joints.size) {
                "<invalid joint>"
            } else joints[handle].name.toString()
        }

        override fun GetDefaultPose(): ArrayList<idJointQuat> {
            return ArrayList(defaultPose)
        }

        override fun NearestJoint(surfaceNum: Int, a: Int, c: Int, b: Int): Int {
            if (surfaceNum > meshes.size) {
                Common.common.Error("idRenderModelMD5::NearestJoint: surfaceNum > meshes.size")
            }
            for (mesh in meshes) {
                if (mesh.surfaceNum == surfaceNum) {
                    return mesh.NearestJoint(a, b, c)
                }
            }
            return 0
        }

        private fun CalculateBounds(entJoints: Array<idJointMat>) {
            var i: Int
            bounds.Clear()
            i = 0
            while (i < meshes.size) {
                bounds.AddBounds(meshes[i].CalcBounds(entJoints))
                val a = 0
                ++i
            }
        }

        //        private void GetFrameBounds(final renderEntity_t ent, idBounds bounds);
        private fun DrawJoints(ent: renderEntity_s, view: viewDef_s) {
            var i: Int
            var num: Int
            val pos = idVec3()
            var joint: idJointMat
            var md5Joint: idMD5Joint
            var parentNum: Int
            num = ent.numJoints
            joint = ent.joints[0]
            md5Joint = joints[0]
            i = 0
            while (i < num) {
                pos.set(ent.origin.plus(joint.ToVec3().times(ent.axis)))
                if (md5Joint.parent != null) {
//                    parentNum = indexOf(md5Joint.parent, joints.Ptr());
                    parentNum = joints.indexOf(md5Joint.parent)
                    Session.session.rw.DebugLine(
                        Lib.colorWhite,
                        ent.origin.plus(ent.joints[parentNum].ToVec3().times(ent.axis)),
                        pos
                    )
                }
                Session.session.rw.DebugLine(
                    Lib.colorRed,
                    pos,
                    pos.plus(joint.ToMat3()[0].times(2.0f).times(ent.axis))
                )
                Session.session.rw.DebugLine(
                    Lib.colorGreen,
                    pos,
                    pos.plus(joint.ToMat3()[1].times(2.0f).times(ent.axis))
                )
                Session.session.rw.DebugLine(
                    Lib.colorBlue,
                    pos,
                    pos.plus(joint.ToMat3()[2].times(2.0f).times(ent.axis))
                )
                joint = ent.joints[++i]
                md5Joint = joints[i]
            }
            val bounds = idBounds()
            bounds.FromTransformedBounds(ent.bounds, Vector.getVec3_zero(), ent.axis)
            Session.session.rw.DebugBounds(Lib.colorMagenta, bounds, ent.origin)
            if (RenderSystem_init.r_jointNameScale.GetFloat() != 0.0f && bounds.Expand(128.0f)
                    .ContainsPoint(view!!.renderView.vieworg.minus(ent.origin))
            ) {
                val offset = idVec3(0f, 0f, RenderSystem_init.r_jointNameOffset.GetFloat())
                val scale: Float
                scale = RenderSystem_init.r_jointNameScale.GetFloat()
                joint = ent.joints[0]
                num = ent.numJoints
                i = 0
                while (i < num) {
                    pos.set(ent.origin.plus(joint.ToVec3().times(ent.axis)))
                    Session.session.rw.DrawText(
                        joints[i].name.toString(),
                        pos.plus(offset),
                        scale,
                        Lib.colorWhite,
                        view.renderView.viewaxis,
                        1
                    )
                    joint = ent.joints[++i]
                }
            }
        }

        @Throws(idException::class)
        private fun ParseJoint(parser: idLexer, joint: idMD5Joint, defaultPose: idJointQuat) {
            val token = idToken()
            val num: Int

            //
            // parse name
            //
            parser.ReadToken(token)
            joint.name.set(token)

            //
            // parse parent
            //
            num = parser.ParseInt()
            if (num < 0) {
                joint.parent = null
            } else {
                if (num >= joints.size - 1) {
                    parser.Error("Invalid parent for joint '%s'", joint.name)
                }
                joint.parent = joints[num]
            }

            //
            // parse default pose
            //
            parser.Parse1DMatrix(3, defaultPose.t)
            parser.Parse1DMatrix(3, defaultPose.q)
            defaultPose.q.w = defaultPose.q.CalcW()
        }

        companion object {
            const val BYTES = Integer.BYTES * 3
        }

        //
        //
        init {
            joints = ArrayList()
            defaultPose = ArrayList()
            meshes = ArrayList()
        }
    }
}