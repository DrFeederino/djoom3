package neo.Renderer

import neo.Renderer.*
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
import neo.framework.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.*
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object Model_md5 {
    val MD5_SnapshotName: String? = "_MD5_Snapshot_"

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
        val offset: idVec3?
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
        private var deformInfo // used to create srfTriangles_t from base frames and new vertexes
                : deformInfo_s?
        private var numTris // number of triangles
                : Int
        private var numWeights // number of weights
                = 0
        private var scaledWeights // joint weights
                : Array<idVec4?>?
        private var shader // material applied to mesh
                : idMaterial?
        private val surfaceNum // number of the static surface created for this mesh
                : Int
        private val texCoords // texture coordinates
                : idList<idVec2?>?
        private var weightIndex // pairs of: joint offset + bool true if next weight is for next vertex
                : IntArray?

        // ~idMD5Mesh();
        @Throws(idException::class)
        fun ParseMesh(parser: idLexer?, numJoints: Int, joints: Array<idJointMat?>?) {
            val token = idToken()
            val name = idToken()
            var num: Int
            var count: Int
            var jointnum: Int
            val shaderName: idStr
            var i: Int
            var j: Int
            val tris = idList<Int?>()
            val firstWeightForVertex = idList<Int?>()
            val numWeightsForVertex = idList<Int?>()
            var maxweight: Int
            val tempWeights = idList<vertexWeight_s?>()
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
            shader = DeclManager.declManager.FindMaterial(shaderName)

            //
            // parse texture coordinates
            //
            parser.ExpectTokenString("numverts")
            count = parser.ParseInt()
            if (count < 0) {
                parser.Error("Invalid size: %s", token.toString())
            }
            texCoords.SetNum(count)
            firstWeightForVertex.SetNum(count)
            numWeightsForVertex.SetNum(count)
            numWeights = 0
            maxweight = 0
            i = 0
            while (i < texCoords.Num()) {
                parser.ExpectTokenString("vert")
                parser.ParseInt()
                parser.Parse1DMatrix(2, texCoords.set(i, idVec2()))
                firstWeightForVertex.set(i, parser.ParseInt())
                numWeightsForVertex.set(i, parser.ParseInt())
                if (0 == numWeightsForVertex.get(i)) {
                    parser.Error("Vertex without any joint weights.")
                }
                numWeights += numWeightsForVertex.get(i)
                if (numWeightsForVertex.get(i) + firstWeightForVertex.get(i) > maxweight) {
                    maxweight = numWeightsForVertex.get(i) + firstWeightForVertex.get(i)
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
            tris.SetNum(count * 3)
            numTris = count
            i = 0
            while (i < count) {
                parser.ExpectTokenString("tri")
                parser.ParseInt()
                tris.set(i * 3 + 0, parser.ParseInt())
                tris.set(i * 3 + 1, parser.ParseInt())
                tris.set(i * 3 + 2, parser.ParseInt())
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
            tempWeights.SetNum(count)
            i = 0
            while (i < count) {
                parser.ExpectTokenString("weight")
                parser.ParseInt()
                jointnum = parser.ParseInt()
                if (jointnum < 0 || jointnum >= numJoints) {
                    parser.Error("Joint Index out of range(%d): %d", numJoints, jointnum)
                }
                tempWeights.set(i, vertexWeight_s())
                tempWeights.get(i).joint = jointnum
                tempWeights.get(i).jointWeight = parser.ParseFloat()
                parser.Parse1DMatrix(3, tempWeights.get(i).offset)
                i++
            }

            // create pre-scaled weights and an index for the vertex/joint lookup
            scaledWeights = arrayOfNulls<idVec4?>(numWeights)
            weightIndex = IntArray(numWeights * 2) // Mem_Alloc16(numWeights * 2 /* sizeof( weightIndex[0] ) */);
            //	memset( weightIndex, 0, numWeights * 2 * sizeof( weightIndex[0] ) );
            count = 0
            i = 0
            while (i < texCoords.Num()) {
                num = firstWeightForVertex.get(i)
                j = 0
                while (j < numWeightsForVertex.get(i)) {
                    scaledWeights.get(count) = idVec4()
                    scaledWeights.get(count)
                        .set(tempWeights.get(num).offset.times(tempWeights.get(num).jointWeight))
                    scaledWeights.get(count).w = tempWeights.get(num).jointWeight
                    weightIndex.get(count * 2 + 0) = tempWeights.get(num).joint * idJointMat.Companion.SIZE
                    j++
                    num++
                    count++
                }
                weightIndex.get(count * 2 - 1) = 1
                i++
            }
            tempWeights.Clear()
            numWeightsForVertex.Clear()
            firstWeightForVertex.Clear()
            parser.ExpectTokenString("}")

            // update counters
            Model_md5.c_numVerts += texCoords.Num()
            Model_md5.c_numWeights += numWeights
            Model_md5.c_numWeightJoints++
            i = 0
            while (i < numWeights) {
                Model_md5.c_numWeightJoints += weightIndex.get(i * 2 + 1)
                i++
            }

            //
            // build the information that will be common to all animations of this mesh:
            // silhouette edge connectivity and normal / tangent generation information
            //
            val verts = arrayOfNulls<idDrawVert?>(texCoords.Num())
            i = 0
            while (i < texCoords.Num()) {
                verts[i] = idDrawVert()
                verts[i].Clear()
                verts[i].st = texCoords.get(i)
                i++
            }
            TransformVerts(verts, joints)
            deformInfo =
                tr_trisurf.R_BuildDeformInfo(texCoords.Num(), verts, tris.Num(), tris, shader.UseUnsmoothedTangents())
        }

        fun UpdateSurface(ent: renderEntity_s?, entJoints: Array<idJointMat?>?, surf: modelSurface_s?) {
            var i: Int
            val base: Int
            val tri: srfTriangles_s?
            tr_local.tr.pc.c_deformedSurfaces++
            tr_local.tr.pc.c_deformedVerts += deformInfo.numOutputVerts
            tr_local.tr.pc.c_deformedIndexes += deformInfo.numIndexes
            surf.shader = shader
            if (surf.geometry != null) {
                // if the number of verts and indexes are the same we can re-use the triangle surface
                // the number of indexes must be the same to assure the correct amount of memory is allocated for the facePlanes
                if (surf.geometry.numVerts == deformInfo.numOutputVerts && surf.geometry.numIndexes == deformInfo.numIndexes) {
                    tr_trisurf.R_FreeStaticTriSurfVertexCaches(surf.geometry)
                } else {
                    tr_trisurf.R_FreeStaticTriSurf(surf.geometry)
                    surf.geometry = tr_trisurf.R_AllocStaticTriSurf()
                }
            } else {
                surf.geometry = tr_trisurf.R_AllocStaticTriSurf()
            }
            tri = surf.geometry

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
            if (tri.verts == null) {
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                i = 0
                while (i < deformInfo.numSourceVerts) {
                    tri.verts[i].Clear()
                    tri.verts[i].st.set(texCoords.get(i))
                    i++
                }
            }
            if (ent.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE] != 0.0f) {
                TransformScaledVerts(tri.verts, entJoints, ent.shaderParms[RenderWorld.SHADERPARM_MD5_SKINSCALE])
            } else {
                TransformVerts(tri.verts, entJoints)
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

        fun CalcBounds(entJoints: Array<idJointMat?>?): idBounds? {
            val bounds = idBounds()
            val verts = arrayOfNulls<idDrawVert?>(texCoords.Num())
            TransformVerts(verts, entJoints)
            Simd.SIMDProcessor.MinMax(bounds.get(0), bounds.get(1), verts, texCoords.Num())
            return bounds
        }

        fun NearestJoint(a: Int, b: Int, c: Int): Int {
            var i: Int
            var bestJoint: Int
            val vertNum: Int
            val weightVertNum: Int
            var bestWeight: Float

            // duplicated vertices might not have weights
            vertNum = if (a >= 0 && a < texCoords.Num()) {
                a
            } else if (b >= 0 && b < texCoords.Num()) {
                b
            } else if (c >= 0 && c < texCoords.Num()) {
                c
            } else {
                // all vertices are duplicates which shouldn't happen
                return 0
            }

            // find the first weight for this vertex
            weightVertNum = 0
            i = 0
            while (weightVertNum < vertNum) {
                weightVertNum += weightIndex.get(i * 2 + 1) * idJointMat.Companion.SIZE
                i++
            }

            // get the joint for the largest weight
            bestWeight = scaledWeights.get(i).w
            bestJoint = weightIndex.get(i * 2 + 0) / idJointMat.Companion.SIZE
            while (weightIndex.get(i * 2 + 1) == 0) {
                if (scaledWeights.get(i).w > bestWeight) {
                    bestWeight = scaledWeights.get(i).w
                    bestJoint = weightIndex.get(i * 2 + 0) / idJointMat.Companion.SIZE
                }
                i++
            }
            return bestJoint
        }

        fun NumVerts(): Int {
            return texCoords.Num()
        }

        fun NumTris(): Int {
            return numTris
        }

        fun NumWeights(): Int {
            return numWeights
        }

        private fun TransformVerts(verts: Array<idDrawVert?>?, entJoints: Array<idJointMat?>?) {
            Simd.SIMDProcessor.TransformVerts(verts, texCoords.Num(), entJoints, scaledWeights, weightIndex, numWeights)
        }

        /*
         ====================
         idMD5Mesh::TransformScaledVerts

         Special transform to make the mesh seem fat or skinny.  May be used for zombie deaths
         ====================
         */
        private fun TransformScaledVerts(verts: Array<idDrawVert?>?, entJoints: Array<idJointMat?>?, scale: Float) {
            val scaledWeights = arrayOfNulls<idVec4?>(numWeights)
            Simd.SIMDProcessor.Mul(scaledWeights[0].ToFloatPtr(), scale, scaledWeights[0].ToFloatPtr(), numWeights * 4)
            Simd.SIMDProcessor.TransformVerts(verts, texCoords.Num(), entJoints, scaledWeights, weightIndex, numWeights)
        }

        //
        //
        init {
            texCoords = idList()
            scaledWeights = null
            weightIndex = null
            shader = null
            numTris = 0
            deformInfo = null
            surfaceNum = 0
        }
    }

    class idRenderModelMD5 : idRenderModelStatic() {
        private val defaultPose: idList<idJointQuat?>?
        private val joints: idList<idMD5Joint?>?
        private val meshes: idList<idMD5Mesh?>?
        override fun InitFromFile(fileName: String?) {
            name = idStr(fileName)
            LoadModel()
        }

        override fun IsDynamicModel(): dynamicModel_t? {
            return dynamicModel_t.DM_CACHED
        }

        /*
         ====================
         idRenderModelMD5::Bounds

         This calculates a rough bounds by using the joint radii without
         transforming all the points
         ====================
         */
        override fun Bounds(ent: renderEntity_s?): idBounds? {
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
            for (mesh in meshes.getList()) {
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
            Common.common.Printf("%4d joints.\n", joints.Num())
        }

        override fun List() {
            var totalTris = 0
            var totalVerts = 0
            for (mesh in meshes.getList()) {
                totalTris += mesh.numTris
                totalVerts += mesh.NumVerts()
            }
            Common.common.Printf(
                " %4dk %3d %4d %4d %s(MD5)",
                Memory() / 1024,
                meshes.Num(),
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
            for (mesh in meshes.getList(Array<idMD5Mesh>::class.java)) {
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
            joints.Clear()
            defaultPose.Clear()
            meshes.Clear()
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
            val poseMat3: Array<idJointMat?>
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
            joints.SetGranularity(1)
            joints.SetNum(num)
            defaultPose.SetGranularity(1)
            defaultPose.SetNum(num)
            poseMat3 = arrayOfNulls<idJointMat?>(num)

            // parse num meshes
            parser.ExpectTokenString("numMeshes")
            num = parser.ParseInt()
            if (num < 0) {
                parser.Error("Invalid size: %d", num)
            }
            meshes.SetGranularity(1)
            meshes.SetNum(num)

            //
            // parse joints
            //
            parser.ExpectTokenString("joints")
            parser.ExpectTokenString("{")
            i = 0
            while (i < joints.Num()) {
                val pose = defaultPose.set(i, idJointQuat())
                val joint = joints.set(i, idMD5Joint())
                ParseJoint(parser, joint, pose)
                poseMat3[i] = idJointMat()
                poseMat3[i].SetRotation(pose.q.ToMat3())
                poseMat3[i].SetTranslation(pose.t)
                if (joint.parent != null) {
                    parentNum = joints.Find(joint.parent)
                    pose.q.set(poseMat3[i].ToMat3().times(poseMat3[parentNum].ToMat3().Transpose()).ToQuat())
                    pose.t.set(
                        poseMat3[i].ToVec3().minus(poseMat3[parentNum].ToVec3())
                            .oMultiply(poseMat3[parentNum].ToMat3().Transpose())
                    )
                }
                i++
            }
            parser.ExpectTokenString("}")
            i = 0
            while (i < meshes.Num()) {
                val mesh = meshes.set(i, idMD5Mesh())
                parser.ExpectTokenString("mesh")
                mesh.ParseMesh(parser, defaultPose.Num(), poseMat3)
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
            total += joints.MemoryUsed() + defaultPose.MemoryUsed() + meshes.MemoryUsed()

            // count up strings
            for (joint in joints.getList()) {
                total += joint.name.DynamicMemoryUsed()
            }

            // count up meshes
            for (mesh in meshes.getList()) {
                total += mesh.texCoords.MemoryUsed() + mesh.numWeights * idVec4.Companion.BYTES + Integer.BYTES * 2

                // sum up deform info
                total += deformInfo_s.Companion.BYTES
                total += tr_trisurf.R_DeformInfoMemoryUsed(mesh.deformInfo)
            }
            return total
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s?,
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
            if (null == ent.joints) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: NULL joints on renderEntity for '%s'\n",
                    Name()
                )
                return null
            } else if (ent.numJoints != joints.Num()) {
                Common.common.Printf(
                    "idRenderModelMD5::InstantiateDynamicModel: renderEntity has different number of joints than model for '%s'\n",
                    Name()
                )
                return null
            }
            tr_local.tr.pc.c_generateMd5++
            if (cachedModel != null) {
                assert(cachedModel is idRenderModelStatic)
                assert(idStr.Companion.Icmp(cachedModel.Name(), Model_md5.MD5_SnapshotName) == 0)
                staticModel = cachedModel as idRenderModelStatic?
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
            for (i in 0 until meshes.Num()) {
                val mesh = meshes.getList(Array<idMD5Mesh>::class.java)[i]

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
                    mesh.surfaceNum = surfaceNum.getVal()
                    surf = staticModel.surfaces.get(surfaceNum.getVal())
                } else {

                    // Remove Overlays before adding new surfaces
                    idRenderModelOverlay.Companion.RemoveOverlaySurfacesFromModel(staticModel)
                    mesh.surfaceNum = staticModel.NumSurfaces()
                    surf = staticModel.surfaces.Alloc()
                    surf.geometry = null
                    surf.shader = null
                    surf.id = i
                }
                mesh.UpdateSurface(ent, ent.joints, surf)
                staticModel.bounds.AddPoint(surf.geometry.bounds.get(0))
                staticModel.bounds.AddPoint(surf.geometry.bounds.get(1))
                val a = 0
            }
            return staticModel
        }

        override fun NumJoints(): Int {
            return joints.Num()
        }

        override fun GetJoints(): Array<idMD5Joint?>? {
            return joints.getList(Array<idMD5Joint>::class.java)
        }

        override fun GetJointHandle(name: String?): Int {
            var i = 0
            for (joint in joints.getList(Array<idMD5Joint>::class.java)) {
                if (idStr.Companion.Icmp(joint.name, name) == 0) {
                    return i
                }
                i++
            }
            return Model.INVALID_JOINT
        }

        override fun GetJointName(handle: Int): String? {
            return if (handle < 0 || handle >= joints.Num()) {
                "<invalid joint>"
            } else joints.get(handle).name.toString()
        }

        override fun GetDefaultPose(): Array<idJointQuat?>? {
            return defaultPose.getList(Array<idJointQuat>::class.java)
        }

        override fun NearestJoint(surfaceNum: Int, a: Int, c: Int, b: Int): Int {
            if (surfaceNum > meshes.Num()) {
                Common.common.Error("idRenderModelMD5::NearestJoint: surfaceNum > meshes.Num()")
            }
            for (mesh in meshes.getList(Array<idMD5Mesh>::class.java)) {
                if (mesh.surfaceNum == surfaceNum) {
                    return mesh.NearestJoint(a, b, c)
                }
            }
            return 0
        }

        private fun CalculateBounds(entJoints: Array<idJointMat?>?) {
            var i: Int
            bounds.Clear()
            i = 0
            while (i < meshes.Num()) {
                bounds.AddBounds(meshes.get(i).CalcBounds(entJoints))
                val a = 0
                ++i
            }
        }

        //        private void GetFrameBounds(final renderEntity_t ent, idBounds bounds);
        private fun DrawJoints(ent: renderEntity_s?, view: viewDef_s?) {
            var i: Int
            var num: Int
            val pos = idVec3()
            var joint: idJointMat?
            var md5Joint: idMD5Joint?
            var parentNum: Int
            num = ent.numJoints
            joint = ent.joints[0]
            md5Joint = joints.get(0)
            i = 0
            while (i < num) {
                pos.set(ent.origin.oPlus(joint.ToVec3().times(ent.axis)))
                if (md5Joint.parent != null) {
//                    parentNum = indexOf(md5Joint.parent, joints.Ptr());
                    parentNum = joints.IndexOf(md5Joint.parent)
                    Session.Companion.session.rw.DebugLine(
                        Lib.Companion.colorWhite,
                        ent.origin.oPlus(ent.joints[parentNum].ToVec3().times(ent.axis)),
                        pos
                    )
                }
                Session.Companion.session.rw.DebugLine(
                    Lib.Companion.colorRed,
                    pos,
                    pos.oPlus(joint.ToMat3().get(0).times(2.0f).oMultiply(ent.axis))
                )
                Session.Companion.session.rw.DebugLine(
                    Lib.Companion.colorGreen,
                    pos,
                    pos.oPlus(joint.ToMat3().get(1).times(2.0f).oMultiply(ent.axis))
                )
                Session.Companion.session.rw.DebugLine(
                    Lib.Companion.colorBlue,
                    pos,
                    pos.oPlus(joint.ToMat3().get(2).times(2.0f).oMultiply(ent.axis))
                )
                joint = ent.joints[++i]
                md5Joint = joints.get(i)
            }
            val bounds = idBounds()
            bounds.FromTransformedBounds(ent.bounds, Vector.getVec3_zero(), ent.axis)
            Session.Companion.session.rw.DebugBounds(Lib.Companion.colorMagenta, bounds, ent.origin)
            if (RenderSystem_init.r_jointNameScale.GetFloat() != 0.0f && bounds.Expand(128.0f)
                    .ContainsPoint(view.renderView.vieworg.minus(ent.origin))
            ) {
                val offset = idVec3(0, 0, RenderSystem_init.r_jointNameOffset.GetFloat())
                val scale: Float
                scale = RenderSystem_init.r_jointNameScale.GetFloat()
                joint = ent.joints[0]
                num = ent.numJoints
                i = 0
                while (i < num) {
                    pos.set(ent.origin.oPlus(joint.ToVec3().times(ent.axis)))
                    Session.Companion.session.rw.DrawText(
                        joints.get(i).name.toString(),
                        pos.oPlus(offset),
                        scale,
                        Lib.Companion.colorWhite,
                        view.renderView.viewaxis,
                        1
                    )
                    joint = ent.joints[++i]
                }
            }
        }

        @Throws(idException::class)
        private fun ParseJoint(parser: idLexer?, joint: idMD5Joint?, defaultPose: idJointQuat?) {
            val token = idToken()
            val num: Int

            //
            // parse name
            //
            parser.ReadToken(token)
            joint.name = token

            //
            // parse parent
            //
            num = parser.ParseInt()
            if (num < 0) {
                joint.parent = null
            } else {
                if (num >= joints.Num() - 1) {
                    parser.Error("Invalid parent for joint '%s'", joint.name)
                }
                joint.parent = joints.get(num)
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
            joints = idList()
            defaultPose = idList()
            meshes = idList()
        }
    }
}