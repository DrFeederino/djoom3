package neo.CM

import neo.CM.CollisionModel.contactInfo_t
import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.idCollisionModelManager
import neo.CM.CollisionModel.trace_s
import neo.Renderer.Material
import neo.Renderer.Material.cullType_t
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.TempDump
import neo.framework.*
import neo.framework.File_h.idFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.MapFile
import neo.idlib.MapFile.idMapBrush
import neo.idlib.MapFile.idMapBrushSide
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.Timer.idTimer
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.StrPool.idPoolStr
import neo.idlib.geometry.Surface_Patch.idSurface_Patch
import neo.idlib.geometry.TraceModel
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.TraceModel.traceModelEdge_t
import neo.idlib.geometry.TraceModel.traceModelPoly_t
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.geometry.Winding
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Pluecker.idPluecker
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.getVec3_origin
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec6
import java.util.*
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

object CollisionModel_local : AbstractCollisionModel_local() {
    /*
     ===============================================================================

     Trace model vs. polygonal model collision detection.

     ===============================================================================
     */
    private const val _DEBUG = false
    var cm_edgeHash: idHashIndex? = null
    var cm_modelBounds: idBounds = idBounds()
    var cm_outList: cm_windingList_s? = null
    var cm_tmpList: cm_windingList_s? = null
    var cm_vertexHash: idHashIndex? = null
    var cm_vertexShift = 0
    var cm_windingList: cm_windingList_s? = null
    private var collisionModelManagerLocal: idCollisionModelManagerLocal = idCollisionModelManagerLocal()

    var collisionModelManager: idCollisionModelManager = collisionModelManagerLocal

    fun setCollisionModelManagers() {
        collisionModelManagerLocal = collisionModelManager as idCollisionModelManagerLocal
        collisionModelManager = collisionModelManagerLocal
    }

    class idCollisionModelManagerLocal : idCollisionModelManager() {
        private val trmBrushes: Array<cm_brushRef_s?> = Array<cm_brushRef_s?>(1) { null }

        // for multi-check avoidance
        private var checkCount = 0
        private var contacts: Array<contactInfo_t?>? = null

        // for retrieving contact points
        private var getContacts = false
        private var loaded = false
        private var mapFileTime: Long = 0
        private val mapName: idStr = idStr()
        private var maxContacts = 0

        // models
        private var maxModels = 0
        private var models: Array<cm_model_s?>? = null
        private var numContacts = 0
        private var numModels = 0

        // for data pruning
        private var numProcNodes = 0
        private var procNodes: Array<cm_procNode_s?>? = null
        private var trmMaterial: idMaterial? = null

        // polygons and brush for trm model
        private var trmPolygons: Array<cm_polygonRef_s?> =
            arrayOfNulls<cm_polygonRef_s?>(TraceModel.MAX_TRACEMODEL_POLYS)

        // load collision models from a map file
        override fun LoadMap(mapFile: idMapFile?) {
            if (mapFile == null) {
                Common.common.Error("idCollisionModelManagerLocal::LoadMap: null mapFile")
                return
            }

            // check whether we can keep the current collision map based on the mapName and mapFileTime
            if (loaded) {
                if (mapName.Icmp(mapFile.GetName()) == 0) {
                    if (mapFile.GetFileTime() == mapFileTime) {
                        Common.common.DPrintf("Using loaded version\n")
                        return
                    }
                    Common.common.DPrintf("Reloading modified map\n")
                }
                FreeMap()
            }

            // clear the collision map
            Clear()

            // models
            maxModels = MAX_SUBMODELS
            numModels = 0
            models =
                Array(maxModels + 1) { cm_model_s() } //new cm_model_s[maxModels + 1]; //cm_model_s.generateArray(maxModels + 1);

            // setup hash to speed up finding shared vertices and edges
            SetupHash()

            // setup trace model structure
            SetupTrmModelStructure()

            // build collision models
            BuildModels(mapFile)

            // save name and time stamp
            mapName.set(mapFile.GetNameStr())
            mapFileTime = mapFile.GetFileTime()
            loaded = true

            // shutdown the hash
            ShutdownHash()
        }

        // frees all the collision models
        override fun FreeMap() {
            var i: Int
            if (!loaded) {
                Clear()
                return
            }
            i = 0
            while (i < maxModels) {
                if (null == models!![i]) {
                    i++
                    continue
                }
                FreeModel(models?.get(i)!!)
                i++
            }
            FreeTrmModelStructure()
            models = emptyArray() //Mem_Free(models);
            Clear()
            ShutdownHash()
        }

        // get clip handle for model
        override fun LoadModel(modelName: idStr, precache: Boolean): Int {
            var handle: Int
            handle = FindModel(modelName)
            if (handle >= 0) {
                return handle
            }
            if (numModels >= MAX_SUBMODELS) {
                Common.common.Error("idCollisionModelManagerLocal::LoadModel: no free slots\n")
                return 0
            }

            // try to load a .cm file
            if (LoadCollisionModelFile(modelName, 0)) {
                handle = FindModel(modelName)
                if (handle >= 0) {
                    return handle
                } else {
                    Common.common.Warning(
                        "idCollisionModelManagerLocal::LoadModel: collision file for '%s' contains different model",
                        modelName
                    )
                }
            }

            // if only precaching .cm files do not waste memory converting render models
            if (precache) {
                return 0
            }

            // try to load a .ASE or .LWO model and convert it to a collision model
            models?.set(numModels, LoadRenderModel(modelName))
            if (models!!.size < numModels) {
                numModels++
                return numModels - 1
            }
            return 0
        }

        /*
         ================
         idCollisionModelManagerLocal::SetupTrmModel

         Trace models (item boxes, etc) are converted to collision models on the fly, using the last model slot
         as a reusable temporary buffer
         ================
         */
        // sets up a trace model for collision with other trace models
        override fun SetupTrmModel(trm: idTraceModel, material: idMaterial): Int {
            var j: Int
            val vertex: Array<cm_vertex_s>?
            val edge: Array<cm_edge_s>?
            var poly: cm_polygon_s
            val model: cm_model_s
            val trmPoly: Array<traceModelPoly_t>
            assert(models != null)
            // probably not the best fix for null pointer assignment?
            if (material.isNil) { // material == null
                material.oSet(trmMaterial) // material = trmMaterial <- modification of input parameter by pointer, oof
            }
            model = models?.get(MAX_SUBMODELS)!!
            model.node?.brushes = null
            model.node?.polygons = null
            // if not a valid trace model
            if (trm.type == traceModel_t.TRM_INVALID || 0 == trm.numPolys) {
                return TRACE_MODEL_HANDLE
            }
            // vertices
            model.numVertices = trm.numVerts
            vertex = model.vertices
            for (i in 0..trm.numVerts) {
                vertex?.get(i)!!.p.set(trm.verts[i])
                vertex[i].sideSet = 0
            }
            // edges
            model.numEdges = trm.numEdges
            edge = model.edges
            val trmEdge: Array<traceModelEdge_t> = trm.edges
            for (i in 0..trm.numEdges) {
                trmEdge[i] = trm.edges[i + 1]
                edge?.set(i, model.edges!![i + 1])
                edge?.get(i)!!.vertexNum[0] = trmEdge[i].v[0]
                edge[i].vertexNum[1] = trmEdge[i].v[1]
                edge[i].normal.set(trmEdge[i].normal)
                edge[i].internal = false
                edge[i].sideSet = 0
            }
            // polygons
            model.numPolygons = trm.numPolys
            trmPoly = trm.polys
            for (i in 0..trm.numPolys) {
                poly = trmPolygons[i]?.p!!
                poly.numEdges = trmPoly[i].numEdges
                j = 0
                while (j < trmPoly[i].numEdges) {
                    poly.edges[j] = trmPoly[j].edges[j]
                    j++
                }
                poly.plane.SetNormal(trmPoly[i].normal)
                poly.plane.SetDist(trmPoly[i].dist)
                poly.bounds.set(trmPoly[i].bounds)
                poly.material = material
                // link polygon at node
                trmPolygons[i]?.next = model.node!!.polygons
                model.node!!.polygons = trmPolygons[i]
            }
            // if the trace model is convex
            if (trm.isConvex) {
                // setup brush for position test
                trmBrushes[0]?.b!!.numPlanes = trm.numPolys
                for (i in 0..trm.numPolys) {
                    trmBrushes[0]?.b!!.planes[i] = trmPolygons[i]?.p!!.plane
                }
                trmBrushes[0]?.b!!.bounds.set(trm.bounds)
                // link brush at node
                trmBrushes[0]?.next = model.node!!.brushes
                model.node!!.brushes = trmBrushes[0]
            }
            // model bounds
            model.bounds.set(trm.bounds)
            // convex
            model.isConvex = trm.isConvex
            return TRACE_MODEL_HANDLE
        }

        // create trace model from a collision model, returns true if successful
        override fun TrmFromModel(modelName: idStr, trm: idTraceModel): Boolean {
            /*cmHandle_t*/
            val handle: Int
            handle = LoadModel(modelName, false)
            if (0 == handle) {
                Common.common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s not found.\n", modelName)
                return false
            }
            return TrmFromModel(models?.get(handle)!!, trm)
        }

        //
        // name of the model
        override fun GetModelName( /*cmHandle_t*/
            model: Int
        ): String {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelBounds: invalid model handle\n")
                return ""
            }
            return models!![model]?.name.toString()
        }

        // bounds of the model
        override fun GetModelBounds(model: Int, bounds: idBounds): Boolean {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelBounds: invalid model handle\n")
                return false
            }
            bounds.set(models!![model]!!.bounds)
            return true
        }

        // all contents flags of brushes and polygons ored together
        override fun GetModelContents(model: Int, contents: CInt): Boolean {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelContents: invalid model handle\n")
                return false
            }
            contents._val = (models!![model]!!.contents)
            return true
        }

        // get the vertex of a model
        override fun GetModelVertex(model: Int, vertexNum: Int, vertex: idVec3): Boolean {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelVertex: invalid model handle\n")
                return false
            }
            if (vertexNum < 0 || vertexNum >= models!![model]!!.numVertices) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelVertex: invalid vertex number\n")
                return false
            }
            vertex.set(models!![model]!!.vertices?.get(vertexNum)!!.p)
            return true
        }

        /*
         ===============================================================================

         Writing of collision model file

         ===============================================================================
         */
        // get the edge of a model
        override fun GetModelEdge(model: Int, edgeNum: Int, start: idVec3, end: idVec3): Boolean {
            var currentEdgeNum = edgeNum
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelEdge: invalid model handle\n")
                return false
            }
            currentEdgeNum = abs(currentEdgeNum)
            if (currentEdgeNum >= models!![model]!!.numEdges) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelEdge: invalid edge number\n")
                return false
            }
            start.set(models!![model]!!.vertices?.get(models!![model]!!.edges?.get(currentEdgeNum)!!.vertexNum[0])!!.p)
            end.set(models!![model]!!.vertices?.get(models!![model]!!.edges?.get(currentEdgeNum)!!.vertexNum[1])!!.p)
            return true
        }

        // get the polygon of a model
        override fun GetModelPolygon(model: Int, polygonNum: Int, winding: idFixedWinding): Boolean {
            var i: Int
            var edgeNum: Int
            val poly = cm_polygon_s()
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models?.get(
                    model
                )
            ) {
                Common.common.Printf("idCollisionModelManagerLocal::GetModelPolygon: invalid model handle\n")
                return false
            }

            // I have no idea how an *int* can be cast into an instance
            // Supposedly the func interprets int as bits and tries to "restore" object? but...why?
            //poly = /* reinterpret_cast<cm_polygon_t **>*/ (polygonNum);
            winding.Clear()
            i = 0
            while (i < poly.numEdges) {
                edgeNum = poly.edges[i]
                winding.plusAssign(
                    models!![model]!!.vertices?.get(
                        models!![model]!!.edges?.get(abs(edgeNum))!!.vertexNum[Math_h.INTSIGNBITSET(
                            edgeNum
                        )]
                    )!!.p
                )
                i++
            }
            return true
        }

        /*
         ===============================================================================

         Collision detection for translational motion

         ===============================================================================
         */
        // translates a trm and reports the first collision if any
        override fun Translation(
            results: trace_s, start: idVec3, end: idVec3, trm: idTraceModel?,
            trmAxis: idMat3, contentMask: Int, model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ) {
            var i: Int
            var j: Int
            var dist: Float
            val model_rotated: Boolean
            val trm_rotated: Boolean
            val dir = idVec3()
            var invModelAxis = idMat3()
            var poly: cm_trmPolygon_s?
            var edge: cm_trmEdge_s?
            var vert: cm_trmVertex_s?
            if (model < 0 || model > MAX_SUBMODELS || model > maxModels) {
                Common.common.Printf("idCollisionModelManagerLocal::Translation: invalid model handle\n")
                return
            }
            if (null == models?.get(model)) {
                Common.common.Printf("idCollisionModelManagerLocal::Translation: invalid model\n")
                return
            }

            // if case special position test
            if (start[0] == end[0] && start[1] == end[1] && start[2] == end[2]) {
                ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
                return
            }
            if (_DEBUG) {
                //var startsolid = false
                // test whether or not stuck to begin with
                if (CollisionModel_debug.cm_debugCollision.GetBool()) {
                    if (0 == entered && !getContacts) {
                        entered = 1
                        // if already messed up to begin with
                        Contents(start, trm!!, trmAxis, -1, model, modelOrigin, modelAxis)
//                        if (Contents(start, trm!!, trmAxis, -1, model, modelOrigin, modelAxis) and contentMask != 0) {
//                            //startsolid = true
//                        }
                        entered = 0
                    }
                }
            }
            checkCount++
            tw.trace.fraction = 1.0f
            tw.trace.c.contents = 0
            tw.trace.c.type = contactType_t.CONTACT_NONE
            tw.contents = contentMask
            tw.isConvex = true
            tw.rotation = false
            tw.positionTest = false
            tw.quickExit = false
            tw.getContacts = getContacts
            tw.contacts = contacts
            tw.maxContacts = maxContacts
            tw.numContacts = 0
            tw.model = models?.get(model)
            tw.start.set(start - modelOrigin)
            tw.end.set(end - modelOrigin)
            tw.dir.set(end - start)
            model_rotated = modelAxis.IsRotated()
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose()
            }

            // if optimized point trace
            if (null == trm
                || trm.bounds[1][0] - trm.bounds[0][0] <= 0.0f && trm.bounds[1][1] - trm.bounds[0][1] <= 0.0f && trm.bounds[1][2] - trm.bounds[0][2] <= 0.0f
            ) {
                if (model_rotated) {
                    // rotate trace instead of model
                    tw.start.timesAssign(invModelAxis)
                    tw.end.timesAssign(invModelAxis)
                    tw.dir.timesAssign(invModelAxis)
                }

                // trace bounds
                i = 0
                while (i < 3) {
                    if (tw.start[i] < tw.end[i]) {
                        tw.bounds[0, i] = tw.start[i] - CollisionModel.CM_BOX_EPSILON
                        tw.bounds[1, i] = tw.end[i] + CollisionModel.CM_BOX_EPSILON
                    } else {
                        tw.bounds[0, i] = tw.end[i] - CollisionModel.CM_BOX_EPSILON
                        tw.bounds[1, i] = tw.start[i] + CollisionModel.CM_BOX_EPSILON
                    }
                    i++
                }
                tw.extents[0] = tw.extents.set(1, tw.extents.set(2, CollisionModel.CM_BOX_EPSILON))
                tw.size.Zero()

                // setup trace heart planes
                SetupTranslationHeartPlanes()
                tw.maxDistFromHeartPlane1 = CollisionModel.CM_BOX_EPSILON
                tw.maxDistFromHeartPlane2 = CollisionModel.CM_BOX_EPSILON
                // collision with single point
                tw.numVerts = 1
                tw.vertices[0].p.set(tw.start)
                tw.vertices[0].endp.set(tw.vertices[0].p + tw.dir)
                tw.vertices[0].pl.FromRay(tw.vertices[0].p, tw.dir)
                tw.numPolys = 0
                tw.numEdges = tw.numPolys
                tw.pointTrace = true
                // trace through the model
                TraceThroughModel(tw)
                // store results
                results.oSet(tw.trace)
                results.endpos.set(start + (end - start) * results.fraction)
                results.endAxis.set(idMat3.getMat3_identity())
                if (results.fraction < 1.0f) {
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results.c.normal.timesAssign(modelAxis)
                        results.c.point.timesAssign(modelAxis)
                    }
                    results.c.point.plusAssign(modelOrigin)
                    results.c.dist += modelOrigin.times(results.c.normal)
                }
                numContacts = tw.numContacts
                return
            }

            // the trace fraction is too inaccurate to describe translations over huge distances
            if (tw.dir.LengthSqr() > Math_h.Square(CollisionModel.CM_MAX_TRACE_DIST)) {
                results.fraction = 0.0f
                results.endpos.set(start)
                results.endAxis.set(trmAxis)
                results.c.normal.set(getVec3_origin())
                results.c.material = null
                results.c.point.set(start)
                //if (Session.session.rw != null) {
                Session.session.rw?.DebugArrow(Lib.colorRed, start, end, 1)
                //}
                Common.common.Printf("idCollisionModelManagerLocal::Translation: huge translation\n")
                return
            }
            tw.pointTrace = false
            tw.size.Clear()

            // setup trm structure
            SetupTrm(tw, trm)
            trm_rotated = trmAxis.IsRotated()

            // calculate vertex positions
            if (trm_rotated) {
                i = 0
                while (i < tw.numVerts) {

                    // rotate trm around the start position
                    tw.vertices[i].p.timesAssign(trmAxis)
                    i++
                }
            }
            i = 0
            while (i < tw.numVerts) {

                // set trm at start position
                tw.vertices[i].p.plusAssign(tw.start)
                i++
            }
            if (model_rotated) {
                i = 0
                while (i < tw.numVerts) {

                    // rotate trm around model instead of rotating the model
                    tw.vertices[i].p.timesAssign(invModelAxis)
                    i++
                }
            }

            // add offset to start point
            if (trm_rotated) {
                dir.set(trm.offset.times(trmAxis))
                tw.start.plusAssign(dir)
                tw.end.plusAssign(dir)
            } else {
                tw.start.plusAssign(trm.offset)
                tw.end.plusAssign(trm.offset)
            }
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.timesAssign(invModelAxis)
                tw.end.timesAssign(invModelAxis)
                tw.dir.timesAssign(invModelAxis)
            }

            // rotate trm polygon planes
            if (trm_rotated and model_rotated) {
                val tmpAxis = trmAxis.times(invModelAxis)
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(tmpAxis)
                    i++
                }
            } else if (trm_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(trmAxis)
                    i++
                }
            } else if (model_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(invModelAxis)
                    i++
                }
            }

            // setup trm polygons
            i = 0
            while (i < tw.numPolys) {
                poly = tw.polys[i]
                // if the trm poly plane is facing in the movement direction
                dist = poly.plane.Normal().times(tw.dir)
                if (dist > 0.0f || !trm.isConvex && dist == 0.0f) {
                    // this trm poly and it's edges and vertices need to be used for collision
                    poly.used = true
                    j = 0
                    while (j < poly.numEdges) {
                        edge = tw.edges[abs(poly.edges[j])]
                        edge.used = true
                        tw.vertices[edge.vertexNum[0]].used = true //true;
                        tw.vertices[edge.vertexNum[1]].used = true //true;
                        j++
                    }
                }
                i++
            }

            // setup trm vertices
            i = 0
            while (i < tw.numVerts) {
                vert = tw.vertices[i]
                if (!vert.used) {
                    i++
                    continue
                }
                // get axial trm size after rotations
                tw.size.AddPoint(vert.p - (tw.start))
                // calculate the end position of each vertex for a full trace
                vert.endp.set(vert.p + (tw.dir))
                // pluecker coordinate for vertex movement line
                vert.pl.FromRay(vert.p, tw.dir)
                i++
            }

            // setup trm edges
            i = 1
            while (i <= tw.numEdges) {
                edge = tw.edges[i]
                if (!edge.used) {
                    i++
                    continue
                }
                // edge start, end and pluecker coordinate
                edge.start.set(tw.vertices[edge.vertexNum[0]].p)
                edge.end.set(tw.vertices[edge.vertexNum[1]].p)
                edge.pl.FromLine(edge.start, edge.end)
                // calculate normal of plane through movement plane created by the edge
                dir.set(edge.start - (edge.end))
                edge.cross[0] = dir[0] * tw.dir[1] - dir[1] * tw.dir[0]
                edge.cross[1] = dir[0] * tw.dir[2] - dir[2] * tw.dir[0]
                edge.cross[2] = dir[1] * tw.dir[2] - dir[2] * tw.dir[1]
                // bit for vertex sidedness bit cache
                edge.bitNum = i
                i++
            }

            // set trm plane distances
            i = 0
            while (i < tw.numPolys) {
                poly = tw.polys[i]
                if (!poly.used) {
                    poly.plane.FitThroughPoint(tw.edges[abs(poly.edges[0])].start)
                }
                i++
            }

            // bounds for full trace, a little bit larger for epsilons
            i = 0
            while (i < 3) {
                if (tw.start[i] < tw.end[i]) {
                    tw.bounds[0, i] = tw.start[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                    tw.bounds[1, i] = tw.end[i] + tw.size[1][i] + CollisionModel.CM_BOX_EPSILON
                } else {
                    tw.bounds[0, i] = tw.end[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                    tw.bounds[1, i] = tw.start[i] + tw.size[1][i] + CollisionModel.CM_BOX_EPSILON
                }
                if (abs(tw.size[0][i]) > abs(tw.size[1][i])) {
                    tw.extents[i] = abs(tw.size[0][i]) + CollisionModel.CM_BOX_EPSILON
                } else {
                    tw.extents[i] = abs(tw.size[1][i]) + CollisionModel.CM_BOX_EPSILON
                }
                i++
            }

            // setup trace heart planes
            SetupTranslationHeartPlanes()
            tw.maxDistFromHeartPlane1 = 0f
            tw.maxDistFromHeartPlane2 = 0f
            // calculate maximum trm vertex distance from both heart planes
            i = 0
            while (i < tw.numVerts) {
                vert = tw.vertices[i]
                if (!vert.used) {
                    i++
                    continue
                }
                dist = abs(tw.heartPlane1.Distance(vert.p))
                if (dist > tw.maxDistFromHeartPlane1) {
                    tw.maxDistFromHeartPlane1 = dist
                }
                dist = abs(tw.heartPlane2.Distance(vert.p))
                if (dist > tw.maxDistFromHeartPlane2) {
                    tw.maxDistFromHeartPlane2 = dist
                }
                i++
            }
            // for epsilons
            tw.maxDistFromHeartPlane1 += CollisionModel.CM_BOX_EPSILON
            tw.maxDistFromHeartPlane2 += CollisionModel.CM_BOX_EPSILON

            // trace through the model
            TraceThroughModel(tw)

            // if we're getting contacts
            if (tw.getContacts) {
                // move all contacts to world space
                if (model_rotated) {
                    i = 0
                    while (i < tw.numContacts) {
                        tw.contacts!![i]!!.normal.timesAssign(modelAxis)
                        tw.contacts!![i]!!.point.timesAssign(modelAxis)
                        i++
                    }
                }
                if (modelOrigin != getVec3_origin()) {
                    i = 0
                    while (i < tw.numContacts) {
                        tw.contacts!![i]!!.point.plusAssign(modelOrigin)
                        tw.contacts!![i]!!.dist += modelOrigin.times(tw.contacts!![i]!!.normal)
                        i++
                    }
                }
                numContacts = tw.numContacts
            } else {
                // store results
                results.oSet(tw.trace)
                results.endpos.set(start + (end - start) * results.fraction)
                results.endAxis.set(trmAxis)
                if (results.fraction < 1.0f) {
                    // if the fraction is tiny the actual movement could end up zero
                    if (results.fraction > 0.0f && results.endpos.Compare(start)) {
                        results.fraction = 0.0f
                    }
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results.c.normal.timesAssign(modelAxis)
                        results.c.point.timesAssign(modelAxis)
                    }
                    results.c.point.plusAssign(modelOrigin)
                    results.c.dist += modelOrigin.times(results.c.normal)
                }
            }
            if (_DEBUG) {
                // test for missed collisions
                if (CollisionModel_debug.cm_debugCollision.GetBool()) {
                    if (0 == entered && !getContacts) {
                        entered = 1
                        // if the trm is stuck in the model
                        if (Contents(
                                results.endpos,
                                trm,
                                trmAxis,
                                -1,
                                model,
                                modelOrigin,
                                modelAxis
                            ) and contentMask != 0
                        ) {
                            val tr = trace_s()

                            // test where the trm is stuck in the model
                            Contents(results.endpos, trm, trmAxis, -1, model, modelOrigin, modelAxis)
                            // re-run collision detection to find out where it failed
                            Translation(tr, start, end, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
                        }
                        entered = 0
                    }
                }
            }
        }

        // rotates a trm and reports the first collision if any
        override fun Rotation(
            results: trace_s, start: idVec3, rotation: idRotation, trm: idTraceModel,
            trmAxis: idMat3, contentMask: Int, model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ) {
            val maxa: Float
            val stepa: Float
            var a: Float
            var lasta: Float


            // if special position test
            if (rotation.GetAngle() == 0.0f) {
                ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
                return
            }
            if (_DEBUG) {
                //var startsolid = false
                // test whether or not stuck to begin with
                if (CollisionModel_debug.cm_debugCollision.GetBool()) {
                    if (0 == entered) {
                        entered = 1
                        // if already messed up to begin with
                        Contents(start, trm, trmAxis, -1, model, modelOrigin, modelAxis)
//                        if (Contents(start, trm, trmAxis, -1, model, modelOrigin, modelAxis) and contentMask != 0) {
//                            startsolid = true
//                        }
                        entered = 0
                    }
                }
            }
            if (rotation.GetAngle() >= 180.0f || rotation.GetAngle() <= -180.0f) {
                if (rotation.GetAngle() >= 360.0f) {
                    maxa = 360.0f
                    stepa = 120.0f // three steps strictly < 180 degrees
                } else if (rotation.GetAngle() <= -360.0f) {
                    maxa = -360.0f
                    stepa = -120.0f // three steps strictly < 180 degrees
                } else {
                    maxa = rotation.GetAngle()
                    stepa = rotation.GetAngle() * 0.5f // two steps strictly < 180 degrees
                }
                lasta = 0.0f
                a = stepa
                while (abs(a) < abs(maxa) + 1.0f) {

                    // partial rotation
                    Rotation180(
                        results,
                        rotation.GetOrigin(),
                        rotation.GetVec(),
                        lasta,
                        a,
                        start,
                        trm,
                        trmAxis,
                        contentMask,
                        model,
                        modelOrigin,
                        modelAxis
                    )
                    // if there is a collision
                    if (results.fraction < 1.0f) {
                        // fraction of total rotation
                        results.fraction = (lasta + stepa * results.fraction) / rotation.GetAngle()
                        return
                    }
                    lasta = a
                    a += stepa
                }
                results.fraction = 1.0f
                return
            }
            Rotation180(
                results,
                rotation.GetOrigin(),
                rotation.GetVec(),
                0.0f,
                rotation.GetAngle(),
                start,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
            if (_DEBUG) {
                // test for missed collisions
                if (CollisionModel_debug.cm_debugCollision.GetBool()) {
                    if (0 == entered) {
                        entered = 1
                        // if the trm is stuck in the model
                        if (Contents(
                                results.endpos,
                                trm,
                                results.endAxis,
                                -1,
                                model,
                                modelOrigin,
                                modelAxis
                            ) and contentMask != 0
                        ) {
                            val tr = trace_s()

                            // test where the trm is stuck in the model
                            Contents(results.endpos, trm, results.endAxis, -1, model, modelOrigin, modelAxis)
                            // re-run collision detection to find out where it failed
                            Rotation(tr, start, rotation, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
                        }
                        entered = 0
                    }
                }
            }
        }

        // returns the contents the trm is stuck in or 0 if the trm is in free space
        override fun Contents(
            start: idVec3, trm: idTraceModel, trmAxis: idMat3,
            contentMask: Int, model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ): Int {
            val results = trace_s()
            if (model < 0 || model > maxModels || model > MAX_SUBMODELS) {
                Common.common.Printf("idCollisionModelManagerLocal::Contents: invalid model handle\n")
                return 0
            }
            if (models == null || models?.get(model) == null) {
                Common.common.Printf("idCollisionModelManagerLocal::Contents: invalid model\n")
                return 0
            }
            return ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
        }

        /*
         ===============================================================================

         Retrieving contacts

         ===============================================================================
         */
        // stores all contact points of the trm with the model, returns the number of contacts
        override fun Contacts(
            contacts: Array<contactInfo_t?>?, maxContacts: Int, start: idVec3, dir: idVec6, depth: Float,
            trm: idTraceModel, trmAxis: idMat3, contentMask: Int, model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ): Int {
            val results = trace_s()
            val end = idVec3()

            // same as Translation but instead of storing the first collision we store all collisions as contacts
            getContacts = true
            this.contacts = contacts
            this.maxContacts = maxContacts
            numContacts = 0
            end.set(start + (dir.SubVec3(0).times(depth)))
            Translation(results, start, end, trm, trmAxis, contentMask, model, modelOrigin, modelAxis)
            if (dir.SubVec3(1).LengthSqr() != 0.0f) {
                // FIXME: rotational contacts
            }
            getContacts = false
            this.maxContacts = 0
            return numContacts
        }

        // test collision detection
        override fun DebugOutput(origin: idVec3) {
            var i: Int
            var k: Int
            var t: Int
            var buf: String
            val boxAngles = idAngles()
            val modelAxis = idMat3()
            val boxAxis: idMat3
            val bounds = idBounds()
            val trace = trace_s()
            var sscanf: Scanner
            if (!CollisionModel_debug.cm_testCollision.GetBool()) {
                return
            }
            CollisionModel_debug.testend =
                idVec3.generateArray(CollisionModel_debug.cm_testTimes.GetInteger()) // = (idVec3 []) Mem_Alloc( cm_testTimes.GetInteger()  );
            if (CollisionModel_debug.cm_testReset.GetBool() || CollisionModel_debug.cm_testWalk.GetBool() && !CollisionModel_debug.start.Compare(
                    CollisionModel_debug.start
                )
            ) {
                CollisionModel_debug.total_rotation = 0
                CollisionModel_debug.total_translation = CollisionModel_debug.total_rotation
                CollisionModel_debug.min_rotation = 999999
                CollisionModel_debug.min_translation = CollisionModel_debug.min_rotation
                CollisionModel_debug.max_rotation = -999999
                CollisionModel_debug.max_translation = CollisionModel_debug.max_rotation
                CollisionModel_debug.num_rotation = 0
                CollisionModel_debug.num_translation = CollisionModel_debug.num_rotation
                CollisionModel_debug.cm_testReset.SetBool(false)
            }
            if (CollisionModel_debug.cm_testWalk.GetBool()) {
                CollisionModel_debug.start.set(origin)
                CollisionModel_debug.cm_testOrigin.SetString(
                    Str.va(
                        "%1.2f %1.2f %1.2f",
                        CollisionModel_debug.start[0],
                        CollisionModel_debug.start[1],
                        CollisionModel_debug.start[2]
                    )
                )
            } else {
                sscanf = Scanner(CollisionModel_debug.cm_testOrigin.GetString())
                CollisionModel_debug.start.set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat())
            }
            sscanf = Scanner(CollisionModel_debug.cm_testBox.GetString())
            bounds.set(
                sscanf.nextFloat(),
                sscanf.nextFloat(),
                sscanf.nextFloat(),
                sscanf.nextFloat(),
                sscanf.nextFloat(),
                sscanf.nextFloat()
            )
            //	sscanf( cm_testBox.GetString(), "%f %f %f %f %f %f", &bounds[0][0], &bounds[0][1], &bounds[0][2],
//										&bounds[1][0], &bounds[1][1], &bounds[1][2] );
            sscanf = Scanner(CollisionModel_debug.cm_testBoxRotation.GetString())
            boxAngles.set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat())
            //	sscanf( cm_testBoxRotation.GetString(), "%f %f %f", &boxAngles[0], &boxAngles[1], &boxAngles[2] );
            boxAxis = boxAngles.ToMat3()
            modelAxis.Identity()
            val itm = idTraceModel(bounds)
            val random = idRandom(0)
            val timer = idTimer()
            if (CollisionModel_debug.cm_testRandomMany.GetBool()) {
                // if many traces in one random direction
                i = 0
                while (i < 3) {
                    CollisionModel_debug.testend[0][i] =
                        CollisionModel_debug.start[i] + random.CRandomFloat() * CollisionModel_debug.cm_testLength.GetFloat()
                    i++
                }
                k = 1
                while (k < CollisionModel_debug.cm_testTimes.GetInteger()) {
                    CollisionModel_debug.testend[k].set(CollisionModel_debug.testend[0])
                    k++
                }
            } else {
                // many traces each in a different random direction
                k = 0
                while (k < CollisionModel_debug.cm_testTimes.GetInteger()) {
                    i = 0
                    while (i < 3) {
                        CollisionModel_debug.testend[k][i] =
                            CollisionModel_debug.start[i] + random.CRandomFloat() * CollisionModel_debug.cm_testLength.GetFloat()
                        i++
                    }
                    k++
                }
            }

            // translational collision detection
            timer.Clear()
            timer.Start()
            i = 0
            while (i < CollisionModel_debug.cm_testTimes.GetInteger()) {
                Translation(
                    trace,
                    CollisionModel_debug.start,
                    CollisionModel_debug.testend[i],
                    itm,
                    boxAxis,
                    Material.CONTENTS_SOLID or Material.CONTENTS_PLAYERCLIP,
                    CollisionModel_debug.cm_testModel.GetInteger(),
                    getVec3_origin(),
                    modelAxis
                )
                i++
            }
            timer.Stop()
            t = timer.Milliseconds().toInt()
            if (t < CollisionModel_debug.min_translation) {
                CollisionModel_debug.min_translation = t
            }
            if (t > CollisionModel_debug.max_translation) {
                CollisionModel_debug.max_translation = t
            }
            CollisionModel_debug.num_translation++
            CollisionModel_debug.total_translation += t
            buf = if (CollisionModel_debug.cm_testTimes.GetInteger() > 9999) {
                String.format("%3dK", CollisionModel_debug.cm_testTimes.GetInteger() / 1000)
            } else {
                String.format("%4d", CollisionModel_debug.cm_testTimes.GetInteger())
            }
            Common.common.Printf(
                "%s translations: %4d milliseconds, (min = %d, max = %d, av = %1.1f)\n",
                buf,
                t,
                CollisionModel_debug.min_translation,
                CollisionModel_debug.max_translation,
                CollisionModel_debug.total_translation.toFloat() / CollisionModel_debug.num_translation
            )
            if (CollisionModel_debug.cm_testRandomMany.GetBool()) {
                // if many traces in one random direction
                i = 0
                while (i < 3) {
                    CollisionModel_debug.testend[0][i] =
                        CollisionModel_debug.start[i] + random.CRandomFloat() * CollisionModel_debug.cm_testRadius.GetFloat()
                    i++
                }
                k = 1
                while (k < CollisionModel_debug.cm_testTimes.GetInteger()) {
                    CollisionModel_debug.testend[k].set(CollisionModel_debug.testend[0])
                    k++
                }
            } else {
                // many traces each in a different random direction
                k = 0
                while (k < CollisionModel_debug.cm_testTimes.GetInteger()) {
                    i = 0
                    while (i < 3) {
                        CollisionModel_debug.testend[k][i] =
                            CollisionModel_debug.start[i] + random.CRandomFloat() * CollisionModel_debug.cm_testRadius.GetFloat()
                        i++
                    }
                    k++
                }
            }
            if (CollisionModel_debug.cm_testRotation.GetBool()) {
                // rotational collision detection
                val vec = idVec3(random.CRandomFloat(), random.CRandomFloat(), random.RandomFloat())
                vec.Normalize()
                val rotation = idRotation(getVec3_origin(), vec, CollisionModel_debug.cm_testAngle.GetFloat())
                timer.Clear()
                timer.Start()
                i = 0
                while (i < CollisionModel_debug.cm_testTimes.GetInteger()) {
                    rotation.SetOrigin(CollisionModel_debug.testend[i])
                    Rotation(
                        trace,
                        CollisionModel_debug.start,
                        rotation,
                        itm,
                        boxAxis,
                        Material.CONTENTS_SOLID or Material.CONTENTS_PLAYERCLIP,
                        CollisionModel_debug.cm_testModel.GetInteger(),
                        getVec3_origin(),
                        modelAxis
                    )
                    i++
                }
                timer.Stop()
                t = timer.Milliseconds().toInt()
                if (t < CollisionModel_debug.min_rotation) {
                    CollisionModel_debug.min_rotation = t
                }
                if (t > CollisionModel_debug.max_rotation) {
                    CollisionModel_debug.max_rotation = t
                }
                CollisionModel_debug.num_rotation++
                CollisionModel_debug.total_rotation += t
                buf = if (CollisionModel_debug.cm_testTimes.GetInteger() > 9999) {
                    String.format("%3dK", CollisionModel_debug.cm_testTimes.GetInteger() / 1000)
                } else {
                    String.format("%4d", CollisionModel_debug.cm_testTimes.GetInteger())
                }
                Common.common.Printf(
                    "%s rotation: %4d milliseconds, (min = %d, max = %d, av = %1.1f)\n",
                    buf,
                    t,
                    CollisionModel_debug.min_rotation,
                    CollisionModel_debug.max_rotation,
                    CollisionModel_debug.total_rotation.toFloat() / CollisionModel_debug.num_rotation
                )
            }
            CollisionModel_debug.testend = emptyArray()
            sscanf.close()
        }

        // draw a model
        override fun DrawModel( /*cmHandle_t*/
            handle: Int, modelOrigin: idVec3,
            modelAxis: idMat3, viewOrigin: idVec3, radius: Float
        ) {
            val model: cm_model_s?
            val viewPos = idVec3()
            val sscanf: Scanner
            if (handle in numModels..-1) {
                return
            }
            if (CollisionModel_debug.cm_drawColor.IsModified()) {
                sscanf =
                    Scanner(CollisionModel_debug.cm_drawColor.GetString()) //, "%f %f %f %f", &cm_color.x, &cm_color.y, &cm_color.z, &cm_color.w );
                sscanf.useLocale(Locale.US)
                CollisionModel_debug.cm_color.set(
                    sscanf.nextFloat(),
                    sscanf.nextFloat(),
                    sscanf.nextFloat(),
                    sscanf.nextFloat()
                )
                CollisionModel_debug.cm_drawColor.ClearModified()
            }
            model = models?.get(handle)
            viewPos.set(viewOrigin - (modelOrigin) * (modelAxis.Transpose()))
            checkCount++
            DrawNodePolygons(model!!, model.node!!, modelOrigin, modelAxis, viewPos, radius)
        }

        // print model information, use -1 handle for accumulated model info
        override fun ModelInfo( /*cmHandle_t*/
            model: Int
        ) {
            val modelInfo = cm_model_s()
            if (model == -1) {
                AccumulateModelInfo(modelInfo)
                PrintModelInfo(modelInfo)
                return
            }
            if (model < 0 || model > MAX_SUBMODELS || model > maxModels) {
                Common.common.Printf("idCollisionModelManagerLocal::ModelInfo: invalid model handle\n")
                return
            }
            if (null == models?.get(model)) {
                Common.common.Printf("idCollisionModelManagerLocal::ModelInfo: invalid model\n")
                return
            }
            PrintModelInfo(models!![model]!!)
        }

        // list all loaded models
        override fun ListModels() {
            var i: Int
            var totalMemory: Int
            totalMemory = 0
            i = 0
            while (i < numModels) {
                Common.common.Printf("%4d: %5d KB   %s\n", i, models!![i]!!.usedMemory shr 10, models!![i]!!.name)
                totalMemory += models!![i]!!.usedMemory
                i++
            }
            Common.common.Printf("%4d KB in %d models\n", totalMemory shr 10, numModels)
        }

        // write a collision model file for the map entity
        override fun WriteCollisionModelForMapEntity(
            mapEnt: idMapEntity,
            filename: String,
            testTraceModel: Boolean
        ): Boolean {
            val fp: idFile?
            val name: idStr
            val model: cm_model_s?
            SetupHash()
            model = CollisionModelForMapEntity(mapEnt)
            name = idStr(filename)
            model!!.name.set(name)
            name.SetFileExtension(CollisionModel_files.CM_FILE_EXT)
            Common.common.Printf("writing %s\n", name)
            fp = FileSystem_h.fileSystem.OpenFileWrite(filename, "fs_devpath")
            if (null == fp) {
                Common.common.Printf(
                    "idCollisionModelManagerLocal::WriteCollisionModelForMapEntity: Error opening file %s\n",
                    name
                )
                FreeModel(model)
                return false
            }

            // write file id and version
            fp.WriteFloatString("%s \"%s\"\n\n", CollisionModel_files.CM_FILEID, CollisionModel_files.CM_FILEVERSION)
            // write the map file crc
            fp.WriteFloatString("%u\n\n", 0)

            // write the collision model
            WriteCollisionModel(fp, model)
            FileSystem_h.fileSystem.CloseFile(fp)
            if (testTraceModel) {
                val trm = idTraceModel()
                TrmFromModel(model, trm)
            }
            FreeModel(model)
            return true
        }

        /*
         ================
         idCollisionModelManagerLocal::TranslateEdgeThroughEdge

         calculates fraction of the translation completed at which the edges collide
         ================
         */
        // CollisionMap_translate.cpp
        private fun TranslateEdgeThroughEdge(
            cross: idVec3,
            l1: idPluecker,
            l2: idPluecker,
            fraction: CFloat
        ): Boolean {
            val d: Float
            val t: Float
            /*

             a = start of line
             b = end of line
             dir = movement direction
             l1 = pluecker coordinate for line
             l2 = pluecker coordinate for edge we might collide with
             a+dir = start of line after movement
             b+dir = end of line after movement
             t = scale factor
             solve pluecker inner product for t of line (a+t*dir : b+t*dir) and line l2

             v[0] = (a[0]+t*dir[0]) * (b[1]+t*dir[1]) - (b[0]+t*dir[0]) * (a[1]+t*dir[1]);
             v[1] = (a[0]+t*dir[0]) * (b[2]+t*dir[2]) - (b[0]+t*dir[0]) * (a[2]+t*dir[2]);
             v[2] = (a[0]+t*dir[0]) - (b[0]+t*dir[0]);
             v[3] = (a[1]+t*dir[1]) * (b[2]+t*dir[2]) - (b[1]+t*dir[1]) * (a[2]+t*dir[2]);
             v[4] = (a[2]+t*dir[2]) - (b[2]+t*dir[2]);
             v[5] = (b[1]+t*dir[1]) - (a[1]+t*dir[1]);

             l2[0] * v[4] + l2[1] * v[5] + l2[2] * v[3] + l2[4] * v[0] + l2[5] * v[1] + l2[3] * v[2] = 0;

             solve t

             v[0] = (a[0]+t*dir[0]) * (b[1]+t*dir[1]) - (b[0]+t*dir[0]) * (a[1]+t*dir[1]);
             v[0] = (a[0]*b[1]) + a[0]*t*dir[1] + b[1]*t*dir[0] + (t*t*dir[0]*dir[1]) -
             ((b[0]*a[1]) + b[0]*t*dir[1] + a[1]*t*dir[0] + (t*t*dir[0]*dir[1]));
             v[0] = a[0]*b[1] + a[0]*t*dir[1] + b[1]*t*dir[0] - b[0]*a[1] - b[0]*t*dir[1] - a[1]*t*dir[0];

             v[1] = (a[0]+t*dir[0]) * (b[2]+t*dir[2]) - (b[0]+t*dir[0]) * (a[2]+t*dir[2]);
             v[1] = (a[0]*b[2]) + a[0]*t*dir[2] + b[2]*t*dir[0] + (t*t*dir[0]*dir[2]) -
             ((b[0]*a[2]) + b[0]*t*dir[2] + a[2]*t*dir[0] + (t*t*dir[0]*dir[2]));
             v[1] = a[0]*b[2] + a[0]*t*dir[2] + b[2]*t*dir[0] - b[0]*a[2] - b[0]*t*dir[2] - a[2]*t*dir[0];

             v[2] = (a[0]+t*dir[0]) - (b[0]+t*dir[0]);
             v[2] = a[0] - b[0];

             v[3] = (a[1]+t*dir[1]) * (b[2]+t*dir[2]) - (b[1]+t*dir[1]) * (a[2]+t*dir[2]);
             v[3] = (a[1]*b[2]) + a[1]*t*dir[2] + b[2]*t*dir[1] + (t*t*dir[1]*dir[2]) -
             ((b[1]*a[2]) + b[1]*t*dir[2] + a[2]*t*dir[1] + (t*t*dir[1]*dir[2]));
             v[3] = a[1]*b[2] + a[1]*t*dir[2] + b[2]*t*dir[1] - b[1]*a[2] - b[1]*t*dir[2] - a[2]*t*dir[1];

             v[4] = (a[2]+t*dir[2]) - (b[2]+t*dir[2]);
             v[4] = a[2] - b[2];

             v[5] = (b[1]+t*dir[1]) - (a[1]+t*dir[1]);
             v[5] = b[1] - a[1];


             v[0] = a[0]*b[1] + a[0]*t*dir[1] + b[1]*t*dir[0] - b[0]*a[1] - b[0]*t*dir[1] - a[1]*t*dir[0];
             v[1] = a[0]*b[2] + a[0]*t*dir[2] + b[2]*t*dir[0] - b[0]*a[2] - b[0]*t*dir[2] - a[2]*t*dir[0];
             v[2] = a[0] - b[0];
             v[3] = a[1]*b[2] + a[1]*t*dir[2] + b[2]*t*dir[1] - b[1]*a[2] - b[1]*t*dir[2] - a[2]*t*dir[1];
             v[4] = a[2] - b[2];
             v[5] = b[1] - a[1];

             v[0] = (a[0]*dir[1] + b[1]*dir[0] - b[0]*dir[1] - a[1]*dir[0]) * t + a[0]*b[1] - b[0]*a[1];
             v[1] = (a[0]*dir[2] + b[2]*dir[0] - b[0]*dir[2] - a[2]*dir[0]) * t + a[0]*b[2] - b[0]*a[2];
             v[2] = a[0] - b[0];
             v[3] = (a[1]*dir[2] + b[2]*dir[1] - b[1]*dir[2] - a[2]*dir[1]) * t + a[1]*b[2] - b[1]*a[2];
             v[4] = a[2] - b[2];
             v[5] = b[1] - a[1];

             l2[4] * (a[0]*dir[1] + b[1]*dir[0] - b[0]*dir[1] - a[1]*dir[0]) * t + l2[4] * (a[0]*b[1] - b[0]*a[1])
             + l2[5] * (a[0]*dir[2] + b[2]*dir[0] - b[0]*dir[2] - a[2]*dir[0]) * t + l2[5] * (a[0]*b[2] - b[0]*a[2])
             + l2[3] * (a[0] - b[0])
             + l2[2] * (a[1]*dir[2] + b[2]*dir[1] - b[1]*dir[2] - a[2]*dir[1]) * t + l2[2] * (a[1]*b[2] - b[1]*a[2])
             + l2[0] * (a[2] - b[2])
             + l2[1] * (b[1] - a[1]) = 0

             t = (- l2[4] * (a[0]*b[1] - b[0]*a[1]) -
             l2[5] * (a[0]*b[2] - b[0]*a[2]) -
             l2[3] * (a[0] - b[0]) -
             l2[2] * (a[1]*b[2] - b[1]*a[2]) -
             l2[0] * (a[2] - b[2]) -
             l2[1] * (b[1] - a[1])) /
             (l2[4] * (a[0]*dir[1] + b[1]*dir[0] - b[0]*dir[1] - a[1]*dir[0]) +
             l2[5] * (a[0]*dir[2] + b[2]*dir[0] - b[0]*dir[2] - a[2]*dir[0]) +
             l2[2] * (a[1]*dir[2] + b[2]*dir[1] - b[1]*dir[2] - a[2]*dir[1]));

             d = l2[4] * (a[0]*dir[1] + b[1]*dir[0] - b[0]*dir[1] - a[1]*dir[0]) +
             l2[5] * (a[0]*dir[2] + b[2]*dir[0] - b[0]*dir[2] - a[2]*dir[0]) +
             l2[2] * (a[1]*dir[2] + b[2]*dir[1] - b[1]*dir[2] - a[2]*dir[1]);

             t = - ( l2[4] * (a[0]*b[1] - b[0]*a[1]) +
             l2[5] * (a[0]*b[2] - b[0]*a[2]) +
             l2[3] * (a[0] - b[0]) +
             l2[2] * (a[1]*b[2] - b[1]*a[2]) +
             l2[0] * (a[2] - b[2]) +
             l2[1] * (b[1] - a[1]));
             t /= d;

             MrE pats Pluecker on the head.. good monkey

             edgeDir = a - b;
             d = l2[4] * (edgeDir[0]*dir[1] - edgeDir[1]*dir[0]) +
             l2[5] * (edgeDir[0]*dir[2] - edgeDir[2]*dir[0]) +
             l2[2] * (edgeDir[1]*dir[2] - edgeDir[2]*dir[1]);
             */d = l2.get(4) * cross[0] + l2.get(5) * cross[1] + l2.get(2) * cross[2]
            if (d == 0.0f) {
                fraction._val = (1.0f)
                // no collision ever
                return false
            }
            t = -l1.PermutedInnerProduct(l2)
            // if the lines cross each other to begin with
            if (t == 0.0f) {
                fraction._val = (0.0f)
                return true
            }
            // fraction of movement at the time the lines cross each other
            fraction._val = (t / d)
            return true
        }

        private fun TranslateTrmEdgeThroughPolygon(tw: cm_traceWork_s, poly: cm_polygon_s, trmEdge: cm_trmEdge_s) {
            var i: Int
            var edgeNum: Int
            var dist: Float
            var d1: Float
            var d2: Float
            val f1 = CFloat()
            val f2 = CFloat()
            val start = idVec3()
            val end = idVec3()
            val normal = idVec3()
            var edge: cm_edge_s
            var v1: cm_vertex_s
            var v2: cm_vertex_s
            var pl: idPluecker
            val epsPl = idPluecker()

            // check edges for a collision
            i = 0
            while (i < poly.numEdges) {
                edgeNum = poly.edges[i]
                edge = tw.model!!.edges!![abs(edgeNum)]
                // if this edge is already checked
                if (edge.checkcount == checkCount) {
                    i++
                    continue
                }
                // can never collide with internal edges
                if (edge.internal) {
                    i++
                    continue
                }
                pl = tw.polygonEdgePlueckerCache[i]
                // get the sides at which the trm edge vertices pass the polygon edge
                CollisionModel_translate.CM_SetEdgeSidedness(
                    edge,
                    pl,
                    tw.vertices[trmEdge.vertexNum[0]].pl,
                    trmEdge.vertexNum[0]
                )
                CollisionModel_translate.CM_SetEdgeSidedness(
                    edge,
                    pl,
                    tw.vertices[trmEdge.vertexNum[1]].pl,
                    trmEdge.vertexNum[1]
                )
                // if the trm edge start and end vertex do not pass the polygon edge at different sides
                if (0 == edge.side shr trmEdge.vertexNum[0] xor (edge.side shr trmEdge.vertexNum[1]) and 1) {
                    i++
                    continue
                }
                // get the sides at which the polygon edge vertices pass the trm edge
                v1 = tw.model!!.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]]
                CollisionModel_translate.CM_SetVertexSidedness(
                    v1,
                    tw.polygonVertexPlueckerCache[i],
                    trmEdge.pl,
                    trmEdge.bitNum
                )
                v2 = tw.model!!.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]]
                CollisionModel_translate.CM_SetVertexSidedness(
                    v2,
                    tw.polygonVertexPlueckerCache[i + 1],
                    trmEdge.pl,
                    trmEdge.bitNum
                )
                // if the polygon edge start and end vertex do not pass the trm edge at different sides
                if (0 == v1.side xor v2.side and (1 shl trmEdge.bitNum)) {
                    i++
                    continue
                }
                // if there is no possible collision between the trm edge and the polygon edge
                if (!TranslateEdgeThroughEdge(trmEdge.cross, trmEdge.pl, pl, f1)) {
                    i++
                    continue
                }
                // if moving away from edge
                if (f1._val < 0.0f) {
                    i++
                    continue
                }

                // pluecker coordinate for epsilon expanded edge
                epsPl.FromLine(
                    tw.model!!.vertices!![edge.vertexNum[0]].p + (edge.normal.times(CollisionModel.CM_CLIP_EPSILON)),
                    tw.model!!.vertices!![edge.vertexNum[1]].p + (edge.normal.times(CollisionModel.CM_CLIP_EPSILON))
                )
                // calculate collision fraction with epsilon expanded edge
                if (!TranslateEdgeThroughEdge(trmEdge.cross, trmEdge.pl, epsPl, f2)) {
                    i++
                    continue
                }
                // if no collision with epsilon edge or moving away from edge
                if (f2._val > 1.0f || f1._val < f2._val) {
                    i++
                    continue
                }
                if (f2._val < 0.0f) {
                    f2._val = (0.0f)
                }
                if (f2._val < tw.trace.fraction) {
                    tw.trace.fraction = f2._val
                    // create plane with normal vector orthogonal to both the polygon edge and the trm edge
                    start.set(tw.model!!.vertices!![edge.vertexNum[0]].p)
                    end.set(tw.model!!.vertices!![edge.vertexNum[1]].p)
                    tw.trace.c.normal.set(end - (start).Cross(trmEdge.end - (trmEdge.start)))
                    // FIXME: do this normalize when we know the first collision
                    tw.trace.c.normal.Normalize()
                    tw.trace.c.dist = tw.trace.c.normal.times(start)
                    // make sure the collision plane faces the trace model
                    if (tw.trace.c.normal.times(trmEdge.start) - tw.trace.c.dist < 0.0f) {
                        tw.trace.c.normal.set(-tw.trace.c.normal)
                        tw.trace.c.dist = -tw.trace.c.dist
                    }
                    tw.trace.c.contents = poly.contents
                    tw.trace.c.material = poly.material
                    tw.trace.c.type = contactType_t.CONTACT_EDGE
                    tw.trace.c.modelFeature = edgeNum
                    tw.trace.c.trmFeature = listOf(*tw.edges).indexOf(trmEdge)
                    // calculate collision point
                    normal[0] = trmEdge.cross[2]
                    normal[1] = -trmEdge.cross[1]
                    normal[2] = trmEdge.cross[0]
                    dist = normal.times(trmEdge.start)
                    d1 = normal.times(start) - dist
                    d2 = normal.times(end) - dist
                    f1._val = (d1 / (d1 - d2))
                    //assert( f1 >= 0.0f && f1 <= 1.0f );
                    tw.trace.c.point.set(start + (end - (start) * (f1._val)))
                    // if retrieving contacts
                    if (tw.getContacts) {
                        CollisionModel_translate.CM_AddContact(tw)
                    }
                }
                i++
            }
        }

        private fun TranslateTrmVertexThroughPolygon(
            tw: cm_traceWork_s,
            poly: cm_polygon_s,
            v: cm_trmVertex_s,
            bitNum: Int
        ) {
            var i: Int
            var edgeNum: Int
            var f: Float
            var edge: cm_edge_s?
            f = CollisionModel_translate.CM_TranslationPlaneFraction(poly.plane, v.p, v.endp)
            if (f < tw.trace.fraction) {
                i = 0
                while (i < poly.numEdges) {
                    edgeNum = poly.edges[i]
                    edge = tw.model!!.edges!![abs(edgeNum)]
                    CollisionModel_translate.CM_SetEdgeSidedness(edge, tw.polygonEdgePlueckerCache[i], v.pl, bitNum)
                    if (Math_h.INTSIGNBITSET(edgeNum) xor (edge.side shr bitNum and 1) != 0) {
                        return
                    }
                    i++
                }
                if (f < 0.0f) {
                    f = 0.0f
                }
                tw.trace.fraction = f
                // collision plane is the polygon plane
                tw.trace.c.normal.set(poly.plane.Normal())
                tw.trace.c.dist = poly.plane.Dist()
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_TRMVERTEX
                tw.trace.c.modelFeature = poly.hashCode()
                tw.trace.c.trmFeature = listOf(*tw.vertices).indexOf(v)
                tw.trace.c.point.set(v.p + (v.endp - (v.p) * (tw.trace.fraction)))
                // if retrieving contacts
                if (tw.getContacts) {
                    CollisionModel_translate.CM_AddContact(tw)
                    // no need to store the trm vertex more than once as a contact
                    v.used = false //false;
                }
            }
        }

        private fun TranslatePointThroughPolygon(tw: cm_traceWork_s, poly: cm_polygon_s, v: cm_trmVertex_s) {
            var i: Int
            var edgeNum: Int
            var f: Float
            var edge: cm_edge_s?
            val pl = idPluecker()
            f = CollisionModel_translate.CM_TranslationPlaneFraction(poly.plane, v.p, v.endp)
            if (f < tw.trace.fraction) {
                i = 0
                while (i < poly.numEdges) {
                    edgeNum = poly.edges[i]
                    edge = tw.model!!.edges!![abs(edgeNum)]
                    // if we didn't yet calculate the sidedness for this edge
                    if (edge.checkcount != checkCount) {
                        var fl: Float
                        edge.checkcount = checkCount
                        pl.FromLine(
                            tw.model!!.vertices!![edge.vertexNum[0]].p,
                            tw.model!!.vertices!![edge.vertexNum[1]].p
                        )
                        fl = v.pl.PermutedInnerProduct(pl)
                        edge.side = Math_h.FLOATSIGNBITSET(fl)
                    }
                    // if the point passes the edge at the wrong side
                    //if ( (edgeNum > 0) == edge.side ) {
                    if (Math_h.INTSIGNBITSET(edgeNum) xor edge.side != 0) {
                        return
                    }
                    i++
                }
                if (f < 0.0f) {
                    f = 0.0f
                }
                tw.trace.fraction = f
                // collision plane is the polygon plane
                tw.trace.c.normal.set(poly.plane.Normal())
                tw.trace.c.dist = poly.plane.Dist()
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_TRMVERTEX
                tw.trace.c.modelFeature = poly.hashCode() // need to check
                tw.trace.c.trmFeature = listOf(*tw.vertices).indexOf(v)
                tw.trace.c.point.set(v.p + (v.endp - (v.p) * (tw.trace.fraction)))
                // if retrieving contacts
                if (tw.getContacts) {
                    CollisionModel_translate.CM_AddContact(tw)
                    // no need to store the trm vertex more than once as a contact
                    v.used = false //false;
                }
            }
        }

        private fun TranslateVertexThroughTrmPolygon(
            tw: cm_traceWork_s,
            trmpoly: cm_trmPolygon_s,
            poly: cm_polygon_s,
            v: cm_vertex_s,
            endp: idVec3,
            pl: idPluecker
        ) {
            var i: Int
            var edgeNum: Int
            var f: Float
            var edge: cm_trmEdge_s?
            f = CollisionModel_translate.CM_TranslationPlaneFraction(trmpoly.plane, v.p, endp)
            if (f < tw.trace.fraction) {
                i = 0
                while (i < trmpoly.numEdges) {
                    edgeNum = trmpoly.edges[i]
                    edge = tw.edges[abs(edgeNum)]
                    CollisionModel_translate.CM_SetVertexSidedness(v, pl, edge.pl, edge.bitNum)
                    if (Math_h.INTSIGNBITSET(edgeNum) xor (v.side shr edge.bitNum and 1) != 0) {
                        return
                    }
                    i++
                }
                if (f < 0.0f) {
                    f = 0.0f
                }
                tw.trace.fraction = f
                // collision plane is the inverse trm polygon plane
                tw.trace.c.normal.set(-trmpoly.plane.Normal())
                tw.trace.c.dist = -trmpoly.plane.Dist()
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_MODELVERTEX
                tw.trace.c.modelFeature = poly.hashCode()
                tw.trace.c.trmFeature = listOf(*tw.polys).indexOf(trmpoly)
                tw.trace.c.point.set(v.p + (endp - (v.p) * (tw.trace.fraction)))
                // if retrieving contacts
                if (tw.getContacts) {
                    CollisionModel_translate.CM_AddContact(tw)
                }
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::TranslateTrmThroughPolygon

         returns true if the polygon blocks the complete translation
         ================
         */
        private fun TranslateTrmThroughPolygon(tw: cm_traceWork_s, p: cm_polygon_s): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var edgeNum: Int
            var fraction: Float
            var d: Float
            val endp = idVec3()
            var pl: idPluecker?
            var bv: cm_trmVertex_s?
            var be: cm_trmEdge_s?
            var bp: cm_trmPolygon_s?
            var v: cm_vertex_s?
            var e: cm_edge_s?

            // if already checked this polygon
            if (p.checkcount == checkCount) {
                return false
            }
            p.checkcount = checkCount

            // if this polygon does not have the right contents behind it
            if (0 == p.contents and tw.contents) {
                return false
            }

            // if the the trace bounds do not intersect the polygon bounds
            if (!tw.bounds.IntersectsBounds(p.bounds)) {
                return false
            }

            // only collide with the polygon if approaching at the front
            if (p.plane.Normal().times(tw.dir) > 0.0f) {
                return false
            }

            // if the polygon is too far from the first heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane1)
            if (abs(d) > tw.maxDistFromHeartPlane1) {
                return false
            }

            // if the polygon is too far from the second heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane2)
            if (abs(d) > tw.maxDistFromHeartPlane2) {
                return false
            }
            fraction = tw.trace.fraction

            // fast point trace
            if (tw.pointTrace) {
                TranslatePointThroughPolygon(tw, p, tw.vertices[0])
            } else {

                // trace bounds should cross polygon plane
                when (tw.bounds.PlaneSide(p.plane)) {
                    Plane.PLANESIDE_CROSS -> {}
                    Plane.PLANESIDE_FRONT -> {
                        if (tw.model!!.isConvex) {
                            tw.quickExit = true
                            return true
                        }
                        return false
                    }
                    else -> return false
                }

                // calculate pluecker coordinates for the polygon edges and polygon vertices
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    e = tw.model!!.edges!![abs(edgeNum)]
                    // reset sidedness cache if this is the first time we encounter this edge during this trace
                    if (e.checkcount != checkCount) {
                        e.sideSet = 0
                    }
                    // pluecker coordinate for edge
                    tw.polygonEdgePlueckerCache[i].FromLine(
                        tw.model!!.vertices!![e.vertexNum[0]].p,
                        tw.model!!.vertices!![e.vertexNum[1]].p
                    )
                    v = tw.model!!.vertices!![e.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]]
                    // reset sidedness cache if this is the first time we encounter this vertex during this trace
                    if (v.checkcount != checkCount) {
                        v.sideSet = 0
                    }
                    // pluecker coordinate for vertex movement vector
                    tw.polygonVertexPlueckerCache[i].FromRay(v.p, -tw.dir)
                    i++
                }
                // copy first to last so we can easily cycle through for the edges
                tw.polygonVertexPlueckerCache[p.numEdges].Set(tw.polygonVertexPlueckerCache[0])

                // trace trm vertices through polygon
                i = 0
                while (i < tw.numVerts) {
                    bv = tw.vertices[i]
                    if (bv.used) {
                        TranslateTrmVertexThroughPolygon(tw, p, bv, i)
                    }
                    i++
                }

                // trace trm edges through polygon
                i = 1
                while (i <= tw.numEdges) {
                    be = tw.edges[i]
                    if (be.used) {
                        TranslateTrmEdgeThroughPolygon(tw, p, be)
                    }
                    i++
                }

                // trace all polygon vertices through the trm
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    e = tw.model!!.edges!![abs(edgeNum)]
                    if (e.checkcount == checkCount) {
                        i++
                        continue
                    }
                    // set edge check count
                    e.checkcount = checkCount
                    // can never collide with internal edges
                    if (e.internal) {
                        i++
                        continue
                    }
                    // got to check both vertices because we skip internal edges
                    k = 0
                    while (k < 2) {
                        v = tw.model!!.vertices!![e.vertexNum[k xor Math_h.INTSIGNBITSET(edgeNum)]]
                        // if this vertex is already checked
                        if (v.checkcount == checkCount) {
                            k++
                            continue
                        }
                        // set vertex check count
                        v.checkcount = checkCount

                        // if the vertex is outside the trace bounds
                        if (!tw.bounds.ContainsPoint(v.p)) {
                            k++
                            continue
                        }

                        // vertex end point after movement
                        endp.set(v.p - (tw.dir))
                        // pluecker coordinate for vertex movement vector
                        pl = tw.polygonVertexPlueckerCache[i + k]
                        j = 0
                        while (j < tw.numPolys) {
                            bp = tw.polys[j]
                            if (!bp.used) {
                                TranslateVertexThroughTrmPolygon(tw, bp, p, v, endp, pl)
                            }
                            j++
                        }
                        k++
                    }
                    i++
                }
            }

            // if there was a collision with this polygon and we are not retrieving contacts
            if (tw.trace.fraction < fraction && !tw.getContacts) {
                fraction = tw.trace.fraction
                endp.set(tw.start + (tw.dir.times(fraction)))
                // decrease bounds
                i = 0
                while (i < 3) {
                    if (tw.start[i] < endp[i]) {
                        tw.bounds[0, i] = tw.start[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                        tw.bounds[1, i] = endp[i] + tw.size[1][i] - CollisionModel.CM_BOX_EPSILON
                    } else {
                        tw.bounds[0, i] = endp[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                        tw.bounds[1, i] = tw.start[i] + tw.size[1][i] - CollisionModel.CM_BOX_EPSILON
                    }
                    i++
                }
            }
            return tw.trace.fraction == 0.0f
        }

        private fun SetupTranslationHeartPlanes() {
            val dir = idVec3(tw.dir)
            val normal1 = idVec3()
            val normal2 = idVec3()

            // calculate trace heart planes
            dir.Normalize()
            dir.NormalVectors(normal1, normal2)
            tw.heartPlane1.SetNormal(normal1)
            tw.heartPlane1.FitThroughPoint(tw.start)
            tw.heartPlane2.SetNormal(normal2)
            tw.heartPlane2.FitThroughPoint(tw.start)
        }

        private fun SetupTrm(tw: cm_traceWork_s, trm: idTraceModel) {
            var i: Int

            // vertices
            tw.numVerts = trm.numVerts
            i = 0
            while (i < trm.numVerts) {
                tw.vertices[i].p.set(trm.verts[i])
                tw.vertices[i].used = false //false;
                i++
            }
            // edges
            tw.numEdges = trm.numEdges
            i = 1
            while (i <= trm.numEdges) {
                tw.edges[i].vertexNum[0] = trm.edges[i].v[0]
                tw.edges[i].vertexNum[1] = trm.edges[i].v[1]
                tw.edges[i].used = false
                i++
            }
            // polygons
            tw.numPolys = trm.numPolys
            i = 0
            while (i < trm.numPolys) {
                tw.polys[i].numEdges = trm.polys[i].numEdges
                System.arraycopy(trm.polys[i].edges, 0, tw.polys[i].edges, 0, trm.polys[i].numEdges)
                tw.polys[i].plane.SetNormal(trm.polys[i].normal)
                tw.polys[i].used = false
                i++
            }
            // is the trace model convex or not
            tw.isConvex = trm.isConvex
        }

        /*
         ================
         idCollisionModelManagerLocal::CollisionBetweenEdgeBounds

         verifies if the collision of two edges occurs between the edge bounds
         also calculates the collision point and collision plane normal if the collision occurs between the bounds
         ================
         */
        // CollisionMap_rotate.cpp
        private fun CollisionBetweenEdgeBounds(
            tw: cm_traceWork_s, va: idVec3, vb: idVec3, vc: idVec3,
            vd: idVec3, tanHalfAngle: CFloat, collisionPoint: idVec3, collisionNormal: idVec3
        ): Boolean {
            var d1: Float
            var d2: Float
            val d: Float
            val at = idVec3()
            val bt = idVec3()
            val dir = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            val pl1 = idPluecker()
            val pl2 = idPluecker()
            at.set(va)
            bt.set(vb)
            if (tanHalfAngle._val != 0.0f) {
                CollisionModel_rotate.CM_RotateEdge(at, bt, tw.origin, tw.axis, tanHalfAngle)
            }
            dir1.set(at - (tw.origin).Cross(tw.axis))
            dir2.set(bt - (tw.origin).Cross(tw.axis))
            if (dir1.times(dir1) > dir2.times(dir2)) {
                dir.set(dir1)
            } else {
                dir.set(dir2)
            }
            if (tw.angle < 0.0f) {
                dir.set(-dir)
            }
            pl1.FromLine(at, bt)
            pl2.FromRay(vc, dir)
            d1 = pl1.PermutedInnerProduct(pl2)
            pl2.FromRay(vd, dir)
            d2 = pl1.PermutedInnerProduct(pl2)
            if (d1 > 0.0f && d2 > 0.0f || d1 < 0.0f && d2 < 0.0f) {
                return false
            }
            pl1.FromLine(vc, vd)
            pl2.FromRay(at, dir)
            d1 = pl1.PermutedInnerProduct(pl2)
            pl2.FromRay(bt, dir)
            d2 = pl1.PermutedInnerProduct(pl2)
            if (d1 > 0.0f && d2 > 0.0f || d1 < 0.0f && d2 < 0.0f) {
                return false
            }

            // collision point on the edge at-bt
            dir1.set(vd - (vc).Cross(dir))
            d = dir1.times(vc)
            d1 = dir1.times(at) - d
            d2 = dir1.times(bt) - d
            if (d1 == d2) {
                return false
            }
            collisionPoint.set(at + (bt - (at) * (d1 / (d1 - d2))))

            // normal is cross product of the rotated edge va-vb and the edge vc-vd
            collisionNormal.Cross(bt - (at), vd - (vc))
            return true
        }

        /*
         ================
         idCollisionModelManagerLocal::RotateEdgeThroughEdge

         calculates the tangent of half the rotation angle at which the edges collide
         ================
         */
        private fun RotateEdgeThroughEdge(
            tw: cm_traceWork_s, pl1: idPluecker,
            vc: idVec3, vd: idVec3, minTan: Float, tanHalfAngle: CFloat
        ): Boolean {
            val v0: Double
            val v1: Double
            val v2: Double
            val a: Double
            val b: Double
            val c: Double
            val d: Double
            val sqrtd: Double
            val q: Double
            var frac1: Double
            var frac2: Double
            val ct = idVec3()
            val dt = idVec3()
            val pl2 = idPluecker()

            /*

             a = start of line being rotated
             b = end of line being rotated
             pl1 = pluecker coordinate for line (a - b)
             pl2 = pluecker coordinate for edge we might collide with (c - d)
             t = rotation angle around the z-axis
             solve pluecker inner product for t of rotating line a-b and line l2

             // start point of rotated line during rotation
             an[0] = a[0] * cos(t) + a[1] * sin(t)
             an[1] = a[0] * -sin(t) + a[1] * cos(t)
             an[2] = a[2];
             // end point of rotated line during rotation
             bn[0] = b[0] * cos(t) + b[1] * sin(t)
             bn[1] = b[0] * -sin(t) + b[1] * cos(t)
             bn[2] = b[2];

             pl1[0] = a[0] * b[1] - b[0] * a[1];
             pl1[1] = a[0] * b[2] - b[0] * a[2];
             pl1[2] = a[0] - b[0];
             pl1[3] = a[1] * b[2] - b[1] * a[2];
             pl1[4] = a[2] - b[2];
             pl1[5] = b[1] - a[1];

             v[0] = (a[0] * cos(t) + a[1] * sin(t)) * (b[0] * -sin(t) + b[1] * cos(t)) - (b[0] * cos(t) + b[1] * sin(t)) * (a[0] * -sin(t) + a[1] * cos(t));
             v[1] = (a[0] * cos(t) + a[1] * sin(t)) * b[2] - (b[0] * cos(t) + b[1] * sin(t)) * a[2];
             v[2] = (a[0] * cos(t) + a[1] * sin(t)) - (b[0] * cos(t) + b[1] * sin(t));
             v[3] = (a[0] * -sin(t) + a[1] * cos(t)) * b[2] - (b[0] * -sin(t) + b[1] * cos(t)) * a[2];
             v[4] = a[2] - b[2];
             v[5] = (b[0] * -sin(t) + b[1] * cos(t)) - (a[0] * -sin(t) + a[1] * cos(t));

             pl2[0] * v[4] + pl2[1] * v[5] + pl2[2] * v[3] + pl2[4] * v[0] + pl2[5] * v[1] + pl2[3] * v[2] = 0;

             v[0] = (a[0] * cos(t) + a[1] * sin(t)) * (b[0] * -sin(t) + b[1] * cos(t)) - (b[0] * cos(t) + b[1] * sin(t)) * (a[0] * -sin(t) + a[1] * cos(t));
             v[0] = (a[1] * b[1] - a[0] * b[0]) * cos(t) * sin(t) + (a[0] * b[1] + a[1] * b[0] * cos(t)^2) - (a[1] * b[0]) - ((b[1] * a[1] - b[0] * a[0]) * cos(t) * sin(t) + (b[0] * a[1] + b[1] * a[0]) * cos(t)^2 - (b[1] * a[0]))
             v[0] = - (a[1] * b[0]) - ( - (b[1] * a[0]))
             v[0] = (b[1] * a[0]) - (a[1] * b[0])

             v[0] = (a[0]*b[1]) - (a[1]*b[0]);
             v[1] = (a[0]*b[2] - b[0]*a[2]) * cos(t) + (a[1]*b[2] - b[1]*a[2]) * sin(t);
             v[2] = (a[0]-b[0]) * cos(t) + (a[1]-b[1]) * sin(t);
             v[3] = (b[0]*a[2] - a[0]*b[2]) * sin(t) + (a[1]*b[2] - b[1]*a[2]) * cos(t);
             v[4] = a[2] - b[2];
             v[5] = (a[0]-b[0]) * sin(t) + (b[1]-a[1]) * cos(t);

             v[0] = (a[0]*b[1]) - (a[1]*b[0]);
             v[1] = (a[0]*b[2] - b[0]*a[2]) * cos(t) + (a[1]*b[2] - b[1]*a[2]) * sin(t);
             v[2] = (a[0]-b[0]) * cos(t) - (b[1]-a[1]) * sin(t);
             v[3] = (a[0]*b[2] - b[0]*a[2]) * -sin(t) + (a[1]*b[2] - b[1]*a[2]) * cos(t);
             v[4] = a[2] - b[2];
             v[5] = (a[0]-b[0]) * sin(t) + (b[1]-a[1]) * cos(t);

             v[0] = pl1[0];
             v[1] = pl1[1] * cos(t) + pl1[3] * sin(t);
             v[2] = pl1[2] * cos(t) - pl1[5] * sin(t);
             v[3] = pl1[3] * cos(t) - pl1[1] * sin(t);
             v[4] = pl1[4];
             v[5] = pl1[5] * cos(t) + pl1[2] * sin(t);

             pl2[0] * v[4] + pl2[1] * v[5] + pl2[2] * v[3] + pl2[4] * v[0] + pl2[5] * v[1] + pl2[3] * v[2] = 0;

             0 =	pl2[0] * pl1[4] +
             pl2[1] * (pl1[5] * cos(t) + pl1[2] * sin(t)) +
             pl2[2] * (pl1[3] * cos(t) - pl1[1] * sin(t)) +
             pl2[4] * pl1[0] +
             pl2[5] * (pl1[1] * cos(t) + pl1[3] * sin(t)) +
             pl2[3] * (pl1[2] * cos(t) - pl1[5] * sin(t));

             v2 * cos(t) + v1 * sin(t) + v0 = 0;

             // rotation about the z-axis
             v0 = pl2[0] * pl1[4] + pl2[4] * pl1[0];
             v1 = pl2[1] * pl1[2] - pl2[2] * pl1[1] + pl2[5] * pl1[3] - pl2[3] * pl1[5];
             v2 = pl2[1] * pl1[5] + pl2[2] * pl1[3] + pl2[5] * pl1[1] + pl2[3] * pl1[2];

             // rotation about the x-axis
             //v0 = pl2[3] * pl1[2] + pl2[2] * pl1[3];
             //v1 = -pl2[5] * pl1[0] + pl2[4] * pl1[1] - pl2[1] * pl1[4] + pl2[0] * pl1[5];
             //v2 = pl2[4] * pl1[0] + pl2[5] * pl1[1] + pl2[0] * pl1[4] + pl2[1] * pl1[5];

             r = tan(t / 2);
             sin(t) = 2*r/(1+r*r);
             cos(t) = (1-r*r)/(1+r*r);

             v1 * 2 * r / (1 + r*r) + v2 * (1 - r*r) / (1 + r*r) + v0 = 0
             (v1 * 2 * r + v2 * (1 - r*r)) / (1 + r*r) = -v0
             (v1 * 2 * r + v2 - v2 * r*r) / (1 + r*r) = -v0
             v1 * 2 * r + v2 - v2 * r*r = -v0 * (1 + r*r)
             v1 * 2 * r + v2 - v2 * r*r = -v0 + -v0 * r*r
             (v0 - v2) * r * r + (2 * v1) * r + (v0 + v2) = 0;

             MrE gives Pluecker a banana.. good monkey

             */tanHalfAngle._val = (tw.maxTan)

            // transform rotation axis to z-axis
            ct.set(vc - (tw.origin) * (tw.matrix))
            dt.set(vd - (tw.origin) * (tw.matrix))
            pl2.FromLine(ct, dt)
            v0 = (pl2.get(0) * pl1.get(4) + pl2.get(4) * pl1.get(0)).toDouble()
            v1 =
                (pl2.get(1) * pl1.get(2) - pl2.get(2) * pl1.get(1) + pl2.get(5) * pl1.get(3) - pl2.get(3) * pl1.get(
                    5
                )).toDouble()
            v2 =
                (pl2.get(1) * pl1.get(5) + pl2.get(2) * pl1.get(3) + pl2.get(5) * pl1.get(1) + pl2.get(3) * pl1.get(
                    2
                )).toDouble()
            a = v0 - v2
            b = v1
            c = v0 + v2
            if (a == 0.0) {
                if (b == 0.0) {
                    return false
                }
                frac1 = -c / (2.0f * b)
                frac2 = 1e10 // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a
                if (d <= 0.0f) {
                    return false
                }
                sqrtd = sqrt(d)
                q = if (b > 0.0f) {
                    -b + sqrtd
                } else {
                    -b - sqrtd
                }
                frac1 = q / a
                frac2 = c / q
            }
            if (tw.angle < 0.0f) {
                frac1 = -frac1
                frac2 = -frac2
            }

            // get smallest tangent for which a collision occurs
            if (frac1 >= minTan && frac1 < tanHalfAngle._val) {
                tanHalfAngle._val = (frac1.toFloat())
            }
            if (frac2 >= minTan && frac2 < tanHalfAngle._val) {
                tanHalfAngle._val = (frac2.toFloat())
            }
            if (tw.angle < 0.0f) {
                tanHalfAngle._val = (-tanHalfAngle._val)
            }
            return true
        }

        /*
         ================
         idCollisionModelManagerLocal::EdgeFurthestFromEdge

         calculates the direction of motion at the initial position, where dir < 0 means the edges move towards each other
         if the edges move away from each other the tangent of half the rotation angle at which
         the edges are furthest apart is also calculated
         ================
         */
        private fun EdgeFurthestFromEdge(
            tw: cm_traceWork_s, pl1: idPluecker,
            vc: idVec3, vd: idVec3, tanHalfAngle: CFloat, dir: CFloat
        ): Boolean {
            val v0: Double
            val v1: Double
            val v2: Double
            val a: Double
            val b: Double
            var c: Double
            val d: Double
            val sqrtd: Double
            val q: Double
            var frac1: Double
            var frac2: Double
            val ct = idVec3()
            val dt = idVec3()
            val pl2 = idPluecker()

            /*

             v2 * cos(t) + v1 * sin(t) + v0 = 0;

             // rotation about the z-axis
             v0 = pl2[0] * pl1[4] + pl2[4] * pl1[0];
             v1 = pl2[1] * pl1[2] - pl2[2] * pl1[1] + pl2[5] * pl1[3] - pl2[3] * pl1[5];
             v2 = pl2[1] * pl1[5] + pl2[2] * pl1[3] + pl2[5] * pl1[1] + pl2[3] * pl1[2];

             derivative:
             v1 * cos(t) - v2 * sin(t) = 0;

             r = tan(t / 2);
             sin(t) = 2*r/(1+r*r);
             cos(t) = (1-r*r)/(1+r*r);

             -v2 * 2 * r / (1 + r*r) + v1 * (1 - r*r)/(1+r*r);
             -v2 * 2 * r + v1 * (1 - r*r) / (1 + r*r) = 0;
             -v2 * 2 * r + v1 * (1 - r*r) = 0;
             (-v1) * r * r + (-2 * v2) * r + (v1) = 0;

             */tanHalfAngle._val = (0.0f)

            // transform rotation axis to z-axis
            ct.set(vc - (tw.origin) * (tw.matrix))
            dt.set(vd - (tw.origin) * (tw.matrix))
            pl2.FromLine(ct, dt)
            v0 = (pl2.get(0) * pl1.get(4) + pl2.get(4) * pl1.get(0)).toDouble()
            v1 =
                (pl2.get(1) * pl1.get(2) - pl2.get(2) * pl1.get(1) + pl2.get(5) * pl1.get(3) - pl2.get(3) * pl1.get(
                    5
                )).toDouble()
            v2 =
                (pl2.get(1) * pl1.get(5) + pl2.get(2) * pl1.get(3) + pl2.get(5) * pl1.get(1) + pl2.get(3) * pl1.get(
                    2
                )).toDouble()

            // get the direction of motion at the initial position
            c = v0 + v2
            if (tw.angle > 0.0f) {
                if (c > 0.0f) {
                    dir._val = (v1.toFloat())
                } else {
                    dir._val = (-v1.toFloat())
                }
            } else {
                if (c > 0.0f) {
                    dir._val = (-v1.toFloat())
                } else {
                    dir._val = (v1.toFloat())
                }
            }
            // negative direction means the edges move towards each other at the initial position
            if (dir._val <= 0.0f) {
                return true
            }
            a = -v1
            b = -v2
            c = v1
            if (a == 0.0) {
                if (b == 0.0) {
                    return false
                }
                frac1 = -c / (2.0f * b)
                frac2 = 1e10 // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a
                if (d <= 0.0f) {
                    return false
                }
                sqrtd = sqrt(d)
                q = if (b > 0.0f) {
                    -b + sqrtd
                } else {
                    -b - sqrtd
                }
                frac1 = q / a
                frac2 = c / q
            }
            if (tw.angle < 0.0f) {
                frac1 = -frac1
                frac2 = -frac2
            }
            if (frac1 < 0.0f && frac2 < 0.0f) {
                return false
            }
            if (frac1 > frac2) {
                tanHalfAngle._val = (frac1.toFloat())
            } else {
                tanHalfAngle._val = (frac2.toFloat())
            }
            if (tw.angle < 0.0f) {
                tanHalfAngle._val = (-tanHalfAngle._val)
            }
            return true
        }

        private fun RotateTrmEdgeThroughPolygon(tw: cm_traceWork_s, poly: cm_polygon_s, trmEdge: cm_trmEdge_s) {
            var i: Int
            var j: Int
            var edgeNum: Int
            var f1: Float
            var f2: Float
            val startTan = CFloat()
            val dir = CFloat()
            val tanHalfAngle = CFloat()
            var edge: cm_edge_s?
            var v1: cm_vertex_s?
            var v2: cm_vertex_s?
            val collisionPoint = idVec3()
            val collisionNormal = idVec3()
            val origin = idVec3()
            val epsDir = idVec3()
            val epsPl = idPluecker()
            val bounds = idBounds()

            // if the trm is convex and the rotation axis intersects the trm
            if (tw.isConvex && tw.axisIntersectsTrm) {
                // if both points are behind the polygon the edge cannot collide within a 180 degrees rotation
                if (tw.vertices[trmEdge.vertexNum[0]].polygonSide and tw.vertices[trmEdge.vertexNum[1]].polygonSide != 0) {
                    return
                }
            }

            // if the trace model edge rotation bounds do not intersect the polygon bounds
            if (!trmEdge.rotationBounds.IntersectsBounds(poly.bounds)) {
                return
            }

            // edge rotation bounds should cross polygon plane
            if (trmEdge.rotationBounds.PlaneSide(poly.plane) != Plane.SIDE_CROSS) {
                return
            }

            // check edges for a collision
            i = 0
            while (i < poly.numEdges) {
                edgeNum = poly.edges[i]
                edge = tw.model!!.edges!![abs(edgeNum)]

                // if this edge is already checked
                if (edge.checkcount == checkCount) {
                    i++
                    continue
                }

                // can never collide with internal edges
                if (edge.internal) {
                    i++
                    continue
                }
                v1 = tw.model!!.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]]
                v2 = tw.model!!.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]]

                // edge bounds
                j = 0
                while (j < 3) {
                    if (v1.p[j] > v2.p[j]) {
                        bounds[0, j] = v2.p[j]
                        bounds[1, j] = v1.p[j]
                    } else {
                        bounds[0, j] = v1.p[j]
                        bounds[1, j] = v2.p[j]
                    }
                    j++
                }

                // if the trace model edge rotation bounds do not intersect the polygon edge bounds
                if (!trmEdge.rotationBounds.IntersectsBounds(bounds)) {
                    i++
                    continue
                }
                f1 = trmEdge.pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i])

                // pluecker coordinate for epsilon expanded edge
                epsDir.set(edge.normal.times(CollisionModel.CM_CLIP_EPSILON + CM_PL_RANGE_EPSILON))
                epsPl.FromLine(
                    tw.model!!.vertices?.get(edge.vertexNum[0])!!.p + (epsDir),
                    tw.model!!.vertices?.get(edge.vertexNum[1])!!.p + (epsDir)
                )
                f2 = trmEdge.pl.PermutedInnerProduct(epsPl)

                // if the rotating edge is inbetween the polygon edge and the epsilon expanded edge
                if (f1 < 0.0f && f2 > 0.0f || f1 > 0.0f && f2 < 0.0f) {
                    if (!EdgeFurthestFromEdge(tw, trmEdge.plzaxis, v1.p, v2.p, startTan, dir)) {
                        i++
                        continue
                    }
                    if (dir._val <= 0.0f) {
                        // moving towards the polygon edge so stop immediately
                        tanHalfAngle._val = (0.0f)
                    } else if (abs(startTan._val) >= tw.maxTan) {
                        // never going to get beyond the start tangent during the current rotation
                        i++
                        continue
                    } else {
                        // collide with the epsilon expanded edge
                        if (!RotateEdgeThroughEdge(
                                tw,
                                trmEdge.plzaxis,
                                v1.p + (epsDir),
                                v2.p + (epsDir),
                                abs(startTan._val),
                                tanHalfAngle
                            )
                        ) {
                            tanHalfAngle._val = (startTan._val)
                        }
                    }
                } else {
                    // collide with the epsilon expanded edge
                    epsDir.set(edge.normal.times(CollisionModel.CM_CLIP_EPSILON))
                    if (!RotateEdgeThroughEdge(
                            tw,
                            trmEdge.plzaxis,
                            v1.p + (epsDir),
                            v2.p + (epsDir),
                            0.0f,
                            tanHalfAngle
                        )
                    ) {
                        i++
                        continue
                    }
                }
                if (abs(tanHalfAngle._val) >= tw.maxTan) {
                    i++
                    continue
                }

                // check if the collision is between the edge bounds
                if (!CollisionBetweenEdgeBounds(
                        tw,
                        trmEdge.start,
                        trmEdge.end,
                        v1.p,
                        v2.p,
                        tanHalfAngle,
                        collisionPoint,
                        collisionNormal
                    )
                ) {
                    i++
                    continue
                }

                // allow rotation if the rotation axis goes through the collisionPoint
                origin.set(tw.origin + (tw.axis.times(tw.axis.times(collisionPoint - (tw.origin)))))
                if ((collisionPoint - origin).LengthSqr() < ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                    i++
                    continue
                }

                // fill in trace structure
                tw.maxTan = abs(tanHalfAngle._val)
                tw.trace.c.normal.set(collisionNormal)
                tw.trace.c.normal.Normalize()
                tw.trace.c.dist = tw.trace.c.normal.times(v1.p)
                // make sure the collision plane faces the trace model
                if (tw.trace.c.normal.times(trmEdge.start) - tw.trace.c.dist < 0) {
                    tw.trace.c.normal.set(-tw.trace.c.normal)
                    tw.trace.c.dist = -tw.trace.c.dist
                }
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_EDGE
                tw.trace.c.modelFeature = edgeNum
                tw.trace.c.trmFeature = listOf(*tw.edges).indexOf(trmEdge)
                tw.trace.c.point.set(collisionPoint)
                // if no collision can be closer
                if (tw.maxTan == 0.0f) {
                    break
                }
                i++
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::RotatePointThroughPlane

         calculates the tangent of half the rotation angle at which the point collides with the plane
         ================
         */
        private fun RotatePointThroughPlane(
            tw: cm_traceWork_s, point: idVec3, plane: idPlane,
            angle: Float, minTan: Float, tanHalfAngle: CFloat
        ): Boolean {
            val v0: Double
            val v1: Double
            val v2: Double
            val a: Double
            val b: Double
            val c: Double
            var d: Double
            val sqrtd: Double
            val q: Double
            var frac1: Double
            var frac2: Double
            val p = idVec3()
            val normal = idVec3()

            /*

             p[0] = point[0] * cos(t) + point[1] * sin(t)
             p[1] = point[0] * -sin(t) + point[1] * cos(t)
             p[2] = point[2];

             normal[0] * (p[0] * cos(t) + p[1] * sin(t)) +
             normal[1] * (p[0] * -sin(t) + p[1] * cos(t)) +
             normal[2] * p[2] + dist = 0

             normal[0] * p[0] * cos(t) + normal[0] * p[1] * sin(t) +
             -normal[1] * p[0] * sin(t) + normal[1] * p[1] * cos(t) +
             normal[2] * p[2] + dist = 0

             v2 * cos(t) + v1 * sin(t) + v0

             // rotation about the z-axis
             v0 = normal[2] * p[2] + dist
             v1 = normal[0] * p[1] - normal[1] * p[0]
             v2 = normal[0] * p[0] + normal[1] * p[1]

             r = tan(t / 2);
             sin(t) = 2*r/(1+r*r);
             cos(t) = (1-r*r)/(1+r*r);

             v1 * 2 * r / (1 + r*r) + v2 * (1 - r*r) / (1 + r*r) + v0 = 0
             (v1 * 2 * r + v2 * (1 - r*r)) / (1 + r*r) = -v0
             (v1 * 2 * r + v2 - v2 * r*r) / (1 + r*r) = -v0
             v1 * 2 * r + v2 - v2 * r*r = -v0 * (1 + r*r)
             v1 * 2 * r + v2 - v2 * r*r = -v0 + -v0 * r*r
             (v0 - v2) * r * r + (2 * v1) * r + (v0 + v2) = 0;

             */tanHalfAngle._val = (tw.maxTan)

            // transform rotation axis to z-axis
            p.set(point - (tw.origin) * (tw.matrix))
            d = (plane[3] + plane.Normal().times(tw.origin)).toDouble()
            normal.set(plane.Normal().times(tw.matrix))
            v0 = normal[2] * p[2] + d
            v1 = (normal[0] * p[1] - normal[1] * p[0]).toDouble()
            v2 = (normal[0] * p[0] + normal[1] * p[1]).toDouble()
            a = v0 - v2
            b = v1
            c = v0 + v2
            if (a == 0.0) {
                if (b == 0.0) {
                    return false
                }
                frac1 = -c / (2.0f * b)
                frac2 = 1e10 // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a
                if (d <= 0.0f) {
                    return false
                }
                sqrtd = sqrt(d)
                q = if (b > 0.0f) {
                    -b + sqrtd
                } else {
                    -b - sqrtd
                }
                frac1 = q / a
                frac2 = c / q
            }
            if (angle < 0.0f) {
                frac1 = -frac1
                frac2 = -frac2
            }

            // get smallest tangent for which a collision occurs
            if (frac1 >= minTan && frac1 < tanHalfAngle._val) {
                tanHalfAngle._val = (frac1.toFloat())
            }
            if (frac2 >= minTan && frac2 < tanHalfAngle._val) {
                tanHalfAngle._val = (frac2.toFloat())
            }
            if (angle < 0.0f) {
                tanHalfAngle._val = (-tanHalfAngle._val)
            }
            return true
        }

        /*
         ================
         idCollisionModelManagerLocal::PointFurthestFromPlane

         calculates the direction of motion at the initial position, where dir < 0 means the point moves towards the plane
         if the point moves away from the plane the tangent of half the rotation angle at which
         the point is furthest away from the plane is also calculated
         ================
         */
        private fun PointFurthestFromPlane(
            tw: cm_traceWork_s, point: idVec3, plane: idPlane,
            angle: Float, tanHalfAngle: CFloat, dir: CFloat
        ): Boolean {
            val v1: Double
            val v2: Double
            val a: Double
            val b: Double
            val c: Double
            val d: Double
            val sqrtd: Double
            val q: Double
            var frac1: Double
            var frac2: Double
            val p = idVec3()
            val normal = idVec3()

            /*

             v2 * cos(t) + v1 * sin(t) + v0 = 0;

             // rotation about the z-axis
             v0 = normal[2] * p[2] + dist
             v1 = normal[0] * p[1] - normal[1] * p[0]
             v2 = normal[0] * p[0] + normal[1] * p[1]

             derivative:
             v1 * cos(t) - v2 * sin(t) = 0;

             r = tan(t / 2);
             sin(t) = 2*r/(1+r*r);
             cos(t) = (1-r*r)/(1+r*r);

             -v2 * 2 * r / (1 + r*r) + v1 * (1 - r*r)/(1+r*r);
             -v2 * 2 * r + v1 * (1 - r*r) / (1 + r*r) = 0;
             -v2 * 2 * r + v1 * (1 - r*r) = 0;
             (-v1) * r * r + (-2 * v2) * r + (v1) = 0;

             */tanHalfAngle._val = (0.0f)

            // transform rotation axis to z-axis
            p.set(point - (tw.origin) * (tw.matrix))
            normal.set(plane.Normal().times(tw.matrix))
            v1 = (normal[0] * p[1] - normal[1] * p[0]).toDouble()
            v2 = (normal[0] * p[0] + normal[1] * p[1]).toDouble()

            // the point will always start at the front of the plane, therefore v0 + v2 > 0 is always true
            if (angle < 0.0f) {
                dir._val = (-v1.toFloat())
            } else {
                dir._val = (v1.toFloat())
            }
            // negative direction means the point moves towards the plane at the initial position
            if (dir._val <= 0.0f) {
                return true
            }
            a = -v1
            b = -v2
            c = v1
            if (a == 0.0) {
                if (b == 0.0) {
                    return false
                }
                frac1 = -c / (2.0f * b)
                frac2 = 1e10 // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a
                if (d <= 0.0f) {
                    return false
                }
                sqrtd = sqrt(d)
                q = if (b > 0.0f) {
                    -b + sqrtd
                } else {
                    -b - sqrtd
                }
                frac1 = q / a
                frac2 = c / q
            }
            if (angle < 0.0f) {
                frac1 = -frac1
                frac2 = -frac2
            }
            if (frac1 < 0.0f && frac2 < 0.0f) {
                return false
            }
            if (frac1 > frac2) {
                tanHalfAngle._val = (frac1.toFloat())
            } else {
                tanHalfAngle._val = (frac2.toFloat())
            }
            if (angle < 0.0f) {
                tanHalfAngle._val = (-tanHalfAngle._val)
            }
            return true
        }

        /*
         ===============================================================================

         Contents test

         ===============================================================================
         */
        private fun RotatePointThroughEpsilonPlane(
            tw: cm_traceWork_s, point: idVec3, endPoint: idVec3,
            plane: idPlane, angle: Float, origin: idVec3,
            tanHalfAngle: CFloat, collisionPoint: idVec3, endDir: idVec3
        ): Boolean {
            var d: Float
            val dir = CFloat()
            val startTan = CFloat()
            val vec = idVec3()
            val startDir = idVec3()
            val epsPlane = idPlane(plane)

            // epsilon expanded plane
            epsPlane.SetDist(epsPlane.Dist() + CollisionModel.CM_CLIP_EPSILON)

            // if the rotation sphere at the rotation origin is too far away from the polygon plane
            d = epsPlane.Distance(origin)
            vec.set(point - (origin))
            if (d * d > vec.times(vec)) {
                return false
            }

            // calculate direction of motion at vertex start position
            startDir.set(point - (origin).Cross(tw.axis))
            if (angle < 0.0f) {
                startDir.set(-startDir)
            }
            // if moving away from plane at start position
            if (startDir.times(epsPlane.Normal()) >= 0.0f) {
                // if end position is outside epsilon range
                d = epsPlane.Distance(endPoint)
                if (d >= 0.0f) {
                    return false // no collision
                }
                // calculate direction of motion at vertex end position
                endDir.set(endPoint - (origin).Cross(tw.axis))
                if (angle < 0.0f) {
                    endDir.set(-endDir)
                }
                // if also moving away from plane at end position
                if (endDir.times(epsPlane.Normal()) > 0.0f) {
                    return false // no collision
                }
            }

            // if the start position is in the epsilon range
            d = epsPlane.Distance(point)
            if (d <= CM_PL_RANGE_EPSILON) {

                // calculate tangent of half the rotation for which the vertex is furthest away from the plane
                if (!PointFurthestFromPlane(tw, point, plane, angle, startTan, dir)) {
                    return false
                }
                if (dir._val <= 0.0f) {
                    // moving towards the polygon plane so stop immediately
                    tanHalfAngle._val = (0.0f)
                } else if (abs(startTan._val) >= tw.maxTan) {
                    // never going to get beyond the start tangent during the current rotation
                    return false
                } else {
                    // calculate collision with epsilon expanded plane
                    if (!RotatePointThroughPlane(
                            tw,
                            point,
                            epsPlane,
                            angle,
                            abs(startTan._val),
                            tanHalfAngle
                        )
                    ) {
                        tanHalfAngle._val = (startTan._val)
                    }
                }
            } else {
                // calculate collision with epsilon expanded plane
                if (!RotatePointThroughPlane(tw, point, epsPlane, angle, 0.0f, tanHalfAngle)) {
                    return false
                }
            }

            // calculate collision point
            collisionPoint.set(point)
            if (tanHalfAngle._val != 0.0f) {
                CollisionModel_rotate.CM_RotatePoint(collisionPoint, tw.origin, tw.axis, tanHalfAngle._val)
            }
            // calculate direction of motion at collision point
            endDir.set(collisionPoint - (origin).Cross(tw.axis))
            if (angle < 0.0f) {
                endDir.set(-endDir)
            }
            return true
        }

        private fun RotateTrmVertexThroughPolygon(
            tw: cm_traceWork_s,
            poly: cm_polygon_s,
            v: cm_trmVertex_s
        ) {
            var i: Int
            val tanHalfAngle = CFloat()
            val endDir = idVec3()
            val collisionPoint = idVec3()
            val pl = idPluecker()

            // if the trm vertex is behind the polygon plane it cannot collide with the polygon within a 180 degrees rotation
            if (tw.isConvex && tw.axisIntersectsTrm && v.polygonSide != 0) {
                return
            }

            // if the trace model vertex rotation bounds do not intersect the polygon bounds
            if (!v.rotationBounds.IntersectsBounds(poly.bounds)) {
                return
            }

            // vertex rotation bounds should cross polygon plane
            if (v.rotationBounds.PlaneSide(poly.plane) != Plane.SIDE_CROSS) {
                return
            }

            // rotate the vertex through the epsilon plane
            if (!RotatePointThroughEpsilonPlane(
                    tw, v.p, v.endp, poly.plane, tw.angle, v.rotationOrigin,
                    tanHalfAngle, collisionPoint, endDir
                )
            ) {
                return
            }
            if (abs(tanHalfAngle._val) < tw.maxTan) {
                // verify if 'collisionPoint' moving along 'endDir' moves between polygon edges
                pl.FromRay(collisionPoint, endDir)
                i = 0
                while (i < poly.numEdges) {
                    if (poly.edges[i] < 0) {
                        if (pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i]) > 0.0f) {
                            return
                        }
                    } else {
                        if (pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i]) < 0.0f) {
                            return
                        }
                    }
                    i++
                }
                tw.maxTan = abs(tanHalfAngle._val)
                // collision plane is the polygon plane
                tw.trace.c.normal.set(poly.plane.Normal())
                tw.trace.c.dist = poly.plane.Dist()
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_TRMVERTEX
                tw.trace.c.modelFeature = poly.hashCode()
                //tw.trace.c.modelFeature = vertexNum;
                tw.trace.c.trmFeature = listOf(*tw.vertices).indexOf(v)
                tw.trace.c.point.set(collisionPoint)
            }
        }

        private fun RotateVertexThroughTrmPolygon(
            tw: cm_traceWork_s,
            trmpoly: cm_trmPolygon_s,
            poly: cm_polygon_s,
            v: cm_vertex_s,
            rotationOrigin: idVec3
        ) {
            var i: Int
            var edgeNum: Int
            val tanHalfAngle = CFloat()
            val dir = idVec3()
            val endp = idVec3()
            val endDir = idVec3()
            val collisionPoint = idVec3()
            val pl = idPluecker()
            var edge: cm_trmEdge_s?

            // if the polygon vertex is behind the trm plane it cannot collide with the trm polygon within a 180 degrees rotation
            if (tw.isConvex && tw.axisIntersectsTrm && trmpoly.plane.Distance(v.p) < 0.0f) {
                return
            }

            // if the model vertex is outside the trm polygon rotation bounds
            if (!trmpoly.rotationBounds.ContainsPoint(v.p)) {
                return
            }

            // if the rotation axis goes through the polygon vertex
            dir.set(v.p - (rotationOrigin))
            if (dir.times(dir) < ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                return
            }

            // calculate vertex end position
            endp.set(v.p)
            tw.modelVertexRotation.RotatePoint(endp)

            // rotate the vertex through the epsilon plane
            if (!RotatePointThroughEpsilonPlane(
                    tw, v.p, endp, trmpoly.plane, -tw.angle, rotationOrigin,
                    tanHalfAngle, collisionPoint, endDir
                )
            ) {
                return
            }
            if (abs(tanHalfAngle._val) < tw.maxTan) {
                // verify if 'collisionPoint' moving along 'endDir' moves between polygon edges
                pl.FromRay(collisionPoint, endDir)
                i = 0
                while (i < trmpoly.numEdges) {
                    edgeNum = trmpoly.edges[i]
                    edge = tw.edges[abs(edgeNum)]
                    if (edgeNum < 0) {
                        if (pl.PermutedInnerProduct(edge.pl) > 0.0f) {
                            return
                        }
                    } else {
                        if (pl.PermutedInnerProduct(edge.pl) < 0.0f) {
                            return
                        }
                    }
                    i++
                }
                tw.maxTan = abs(tanHalfAngle._val)
                // collision plane is the flipped trm polygon plane
                tw.trace.c.normal.set(-trmpoly.plane.Normal())
                tw.trace.c.dist = tw.trace.c.normal.times(v.p)
                tw.trace.c.contents = poly.contents
                tw.trace.c.material = poly.material
                tw.trace.c.type = contactType_t.CONTACT_MODELVERTEX
                tw.trace.c.modelFeature = listOf(*tw.model!!.vertices!!).indexOf(v)
                tw.trace.c.trmFeature = listOf(*tw.polys).indexOf(trmpoly)
                tw.trace.c.point.set(v.p)
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::RotateTrmThroughPolygon

         returns true if the polygon blocks the complete rotation
         ================
         */
        private fun RotateTrmThroughPolygon(tw: cm_traceWork_s, p: cm_polygon_s): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var edgeNum: Int
            var d: Float
            var bv: cm_trmVertex_s?
            var be: cm_trmEdge_s?
            var bp: cm_trmPolygon_s?
            var v: cm_vertex_s?
            var e: cm_edge_s?
            val rotationOrigin = idVec3()

            // if already checked this polygon
            if (p.checkcount == checkCount) {
                return false
            }
            p.checkcount = checkCount

            // if this polygon does not have the right contents behind it
            if (0 == p.contents and tw.contents) {
                return false
            }

            // if the the trace bounds do not intersect the polygon bounds
            if (!tw.bounds.IntersectsBounds(p.bounds)) {
                return false
            }

            // back face culling
            if (tw.isConvex) {
                // if the center of the convex trm is behind the polygon plane
                if (p.plane.Distance(tw.start) < 0.0f) {
                    // if the rotation axis intersects the trace model
                    if (tw.axisIntersectsTrm) {
                        return false
                    } else {
                        // if the direction of motion at the start and end position of the
                        // center of the trm both go towards or away from the polygon plane
                        // or if the intersections of the rotation axis with the expanded heart planes
                        // are both in front of the polygon plane
                    }
                }
            }

            // if the polygon is too far from the first heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane1)
            if (abs(d) > tw.maxDistFromHeartPlane1) {
                return false
            }
            when (tw.bounds.PlaneSide(p.plane)) {
                Plane.PLANESIDE_CROSS -> {}
                Plane.PLANESIDE_FRONT -> {
                    if (tw.model!!.isConvex) {
                        tw.quickExit = true
                        return true
                    }
                    return false
                }
                else -> return false
            }
            i = 0
            while (i < tw.numVerts) {
                bv = tw.vertices[i]
                // calculate polygon side this vertex is on
                d = p.plane.Distance(bv.p)
                bv.polygonSide = Math_h.FLOATSIGNBITSET(d)
                i++
            }
            i = 0
            while (i < p.numEdges) {
                edgeNum = p.edges[i]
                e = tw.model!!.edges!![abs(edgeNum)]
                v = tw.model!!.vertices!![e.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]]

                // pluecker coordinate for edge
                tw.polygonEdgePlueckerCache[i].FromLine(
                    tw.model!!.vertices!![e.vertexNum[0]].p,
                    tw.model!!.vertices!![e.vertexNum[1]].p
                )

                // calculate rotation origin projected into rotation plane through the vertex
                tw.polygonRotationOriginCache[i] =
                    tw.origin + (tw.axis.times(tw.axis.times(v.p - (tw.origin))))
                i++
            }
            // copy first to last so we can easily cycle through
            tw.polygonRotationOriginCache[p.numEdges] = tw.polygonRotationOriginCache[0]

            // fast point rotation
            if (tw.pointTrace) {
                RotateTrmVertexThroughPolygon(tw, p, tw.vertices[0])
            } else {
                // rotate trm vertices through polygon
                i = 0
                while (i < tw.numVerts) {
                    bv = tw.vertices[i]
                    if (bv.used) {
                        RotateTrmVertexThroughPolygon(tw, p, bv)
                    }
                    i++
                }

                // rotate trm edges through polygon
                i = 1
                while (i <= tw.numEdges) {
                    be = tw.edges[i]
                    if (be.used) {
                        RotateTrmEdgeThroughPolygon(tw, p, be)
                    }
                    i++
                }

                // rotate all polygon vertices through the trm
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    e = tw.model!!.edges!![abs(edgeNum)]
                    if (e.checkcount == checkCount) {
                        i++
                        continue
                    }
                    // set edge check count
                    e.checkcount = checkCount
                    // can never collide with internal edges
                    if (e.internal) {
                        i++
                        continue
                    }
                    // got to check both vertices because we skip internal edges
                    k = 0
                    while (k < 2) {
                        v = tw.model!!.vertices!![e.vertexNum[k xor Math_h.INTSIGNBITSET(edgeNum)]]

                        // if this vertex is already checked
                        if (v.checkcount == checkCount) {
                            k++
                            continue
                        }
                        // set vertex check count
                        v.checkcount = checkCount

                        // if the vertex is outside the trm rotation bounds
                        if (!tw.bounds.ContainsPoint(v.p)) {
                            k++
                            continue
                        }
                        rotationOrigin.set(tw.polygonRotationOriginCache[i + k])
                        j = 0
                        while (j < tw.numPolys) {
                            bp = tw.polys[j]
                            if (!bp.used) {
                                RotateVertexThroughTrmPolygon(tw, bp, p, v, rotationOrigin)
                            }
                            j++
                        }
                        k++
                    }
                    i++
                }
            }
            return tw.maxTan == 0.0f
        }

        /*
         ================
         idCollisionModelManagerLocal::BoundsForRotation

         only for rotations < 180 degrees
         ================
         */
        private fun BoundsForRotation(origin: idVec3, axis: idVec3, start: idVec3, end: idVec3, bounds: idBounds) {
            var i: Int
            val radiusSqr: Float
            radiusSqr = (start - origin).LengthSqr()
            val v1 = idVec3(start - (origin).Cross(axis))
            val v2 = idVec3(end - (origin).Cross(axis))
            i = 0
            while (i < 3) {

                // if the derivative changes sign along this axis during the rotation from start to end
                if (v1[i] > 0.0f && v2[i] < 0.0f || v1[i] < 0.0f && v2[i] > 0.0f) {
                    if (0.5f * (start[i] + end[i]) - origin[i] > 0.0f) {
                        bounds[0, i] = Lib.Min(start[i], end[i])
                        bounds[1, i] = origin[i] + idMath.Sqrt(radiusSqr * (1.0f - axis[i] * axis[i]))
                    } else {
                        bounds[0, i] = origin[i] - idMath.Sqrt(radiusSqr * (1.0f - axis[i] * axis[i]))
                        bounds[1, i] = Lib.Max(start[i], end[i])
                    }
                } else if (start[i] > end[i]) {
                    bounds[0, i] = end[i]
                    bounds[1, i] = start[i]
                } else {
                    bounds[0, i] = start[i]
                    bounds[1, i] = end[i]
                }
                // expand for epsilons
                bounds[0].minusAssign(i, CollisionModel.CM_BOX_EPSILON)
                bounds[1].plusAssign(i, CollisionModel.CM_BOX_EPSILON)
                i++
            }
        }

        private fun Rotation180(
            results: trace_s, rorg: idVec3, axis: idVec3,
            startAngle: Float, endAngle: Float, start: idVec3,
            trm: idTraceModel, trmAxis: idMat3, contentMask: Int,  /*cmHandle_t*/
            model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ) {
            var i: Int
            var j: Int
            var edgeNum: Int
            var d: Float
            val maxErr: Float
            val initialTan: Float
            val model_rotated: Boolean
            val trm_rotated: Boolean
            val vr = idVec3()
            val vup = idVec3()
            var invModelAxis = idMat3()
            val tmpAxis: idMat3?
            val startRotation = idRotation()
            val endRotation = idRotation()
            val plaxis = idPluecker()
            var poly: cm_trmPolygon_s
            var edge: cm_trmEdge_s
            var vert: cm_trmVertex_s
            if (model < 0 || model > MAX_SUBMODELS || model > maxModels) {
                Common.common.Printf("idCollisionModelManagerLocal::Rotation180: invalid model handle\n")
                return
            }
            if (null == models?.get(model)) {
                Common.common.Printf("idCollisionModelManagerLocal::Rotation180: invalid model\n")
                return
            }
            checkCount++
            tw.trace.fraction = 1.0f
            tw.trace.c.contents = 0
            tw.trace.c.type = contactType_t.CONTACT_NONE
            tw.contents = contentMask
            tw.isConvex = true
            tw.rotation = true
            tw.positionTest = false
            tw.axisIntersectsTrm = false
            tw.quickExit = false
            tw.angle = endAngle - startAngle
            assert(tw.angle > -180.0f && tw.angle < 180.0f)
            initialTan = abs(tan((idMath.PI / 360.0f * tw.angle).toDouble()).toFloat())
            tw.maxTan = initialTan
            tw.model = models?.get(model)
            tw.start.set(start - (modelOrigin))
            // rotation axis, axis is assumed to be normalized
            tw.axis.set(axis)
            assert(
                tw.axis[0] * tw.axis[0] + tw.axis[1] * tw.axis[1] + tw.axis[2] * tw.axis[2] > 0.99f
            )
            // rotation origin projected into rotation plane through tw.start
            tw.origin.set(rorg - (modelOrigin))
            d = tw.axis.times(tw.origin) - tw.axis.times(tw.start)
            tw.origin.set(tw.origin - (tw.axis.times(d)))
            // radius of rotation
            tw.radius = (tw.start - tw.origin).Length()
            // maximum error of the circle approximation traced through the axial BSP tree
            d =
                tw.radius * tw.radius - CIRCLE_APPROXIMATION_LENGTH * CIRCLE_APPROXIMATION_LENGTH * 0.25f
            maxErr = if (d > 0.0f) {
                (tw.radius - sqrt(d.toDouble())).toFloat()
            } else {
                tw.radius
            }
            model_rotated = modelAxis.IsRotated()
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose()
                tw.axis.timesAssign(invModelAxis)
                tw.origin.timesAssign(invModelAxis)
            }
            startRotation.Set(tw.origin, tw.axis, startAngle)
            endRotation.Set(tw.origin, tw.axis, endAngle)

            // create matrix which rotates the rotation axis to the z-axis
            tw.axis.NormalVectors(vr, vup)
            tw.matrix.set(0, 0, vr[0])
            tw.matrix.set(1, 0, vr[1])
            tw.matrix.set(2, 0, vr[2])
            tw.matrix.set(0, 1, -vup[0])
            tw.matrix.set(1, 1, -vup[1])
            tw.matrix.set(2, 1, -vup[2])
            tw.matrix.set(0, 2, tw.axis[0])
            tw.matrix.set(1, 2, tw.axis[1])
            tw.matrix.set(2, 2, tw.axis[2])

            // if optimized point trace
            if (null == trm
                || trm.bounds[1][0] - trm.bounds[0][0] <= 0.0f && trm.bounds[1][1] - trm.bounds[0][1] <= 0.0f && trm.bounds[1][2] - trm.bounds[0][2] <= 0.0f
            ) {
                if (model_rotated) {
                    // rotate trace instead of model
                    tw.start.timesAssign(invModelAxis)
                }
                tw.end.set(tw.start)
                // if we start at a specific angle
                if (startAngle != 0.0f) {
                    startRotation.RotatePoint(tw.start)
                }
                // calculate end position of rotation
                endRotation.RotatePoint(tw.end)

                // calculate rotation origin projected into rotation plane through the vertex
                tw.numVerts = 1
                tw.vertices[0].p.set(tw.start)
                tw.vertices[0].endp.set(tw.end)
                tw.vertices[0].used = true
                tw.vertices[0].rotationOrigin.set(
                    tw.origin + (
                            tw.axis.times(
                                tw.axis.times(
                                    tw.vertices[0].p - (
                                            tw.origin
                                            )
                                )
                            )
                            )
                )
                BoundsForRotation(
                    tw.vertices[0].rotationOrigin,
                    tw.axis,
                    tw.start,
                    tw.end,
                    tw.vertices[0].rotationBounds
                )
                // rotation bounds
                tw.bounds.set(tw.vertices[0].rotationBounds)
                tw.numPolys = 0
                tw.numEdges = tw.numPolys

                // collision with single point
                tw.pointTrace = true

                // extents is set to maximum error of the circle approximation traced through the axial BSP tree
                tw.extents[0] = tw.extents.set(1, tw.extents.set(2, maxErr + CollisionModel.CM_BOX_EPSILON))

                // setup rotation heart plane
                tw.heartPlane1.SetNormal(tw.axis)
                tw.heartPlane1.FitThroughPoint(tw.start)
                tw.maxDistFromHeartPlane1 = CollisionModel.CM_BOX_EPSILON

                // trace through the model
                TraceThroughModel(tw)

                // store results
                results.oSet(tw.trace)
                results.endpos.set(start)
                if (tw.maxTan == initialTan) {
                    results.fraction = 1.0f
                } else {
                    results.fraction =
                        abs(atan(tw.maxTan.toDouble()).toFloat() * (2.0f * 180.0f / idMath.PI) / tw.angle)
                }
                assert(results.fraction <= 1.0f)
                endRotation.Set(rorg, axis, startAngle + (endAngle - startAngle) * results.fraction)
                endRotation.RotatePoint(results.endpos)
                results.endAxis.Identity()
                if (results.fraction < 1.0f) {
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results.c.normal.timesAssign(modelAxis)
                        results.c.point.timesAssign(modelAxis)
                    }
                    results.c.point.plusAssign(modelOrigin)
                    results.c.dist += modelOrigin.times(results.c.normal)
                }
                return
            }
            tw.pointTrace = false

            // setup trm structure
            SetupTrm(tw, trm)
            trm_rotated = trmAxis.IsRotated()

            // calculate vertex positions
            if (trm_rotated) {
                i = 0
                while (i < tw.numVerts) {

                    // rotate trm around the start position
                    tw.vertices[i].p.timesAssign(trmAxis)
                    i++
                }
            }
            i = 0
            while (i < tw.numVerts) {

                // set trm at start position
                tw.vertices[i].p.plusAssign(tw.start)
                i++
            }
            if (model_rotated) {
                i = 0
                while (i < tw.numVerts) {
                    tw.vertices[i].p.timesAssign(invModelAxis)
                    i++
                }
            }
            i = 0
            while (i < tw.numVerts) {
                tw.vertices[i].endp.set(tw.vertices[i].p)
                i++
            }
            // if we start at a specific angle
            if (startAngle != 0.0f) {
                i = 0
                while (i < tw.numVerts) {
                    startRotation.RotatePoint(tw.vertices[i].p)
                    i++
                }
            }
            i = 0
            while (i < tw.numVerts) {

                // end position of vertex
                endRotation.RotatePoint(tw.vertices[i].endp)
                i++
            }

            // add offset to start point
            if (trm_rotated) {
                tw.start.plusAssign(trm.offset.times(trmAxis))
            } else {
                tw.start.plusAssign(trm.offset)
            }
            // if the model is rotated
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.timesAssign(invModelAxis)
            }
            tw.end.set(tw.start)
            // if we start at a specific angle
            if (startAngle != 0.0f) {
                startRotation.RotatePoint(tw.start)
            }
            // calculate end position of rotation
            endRotation.RotatePoint(tw.end)

            // setup trm vertices
            i = 0
            while (i < tw.numVerts) {
                vert = tw.vertices[i]
                // calculate rotation origin projected into rotation plane through the vertex
                vert.rotationOrigin.set(tw.origin + (tw.axis.times(tw.axis.times(vert.p - (tw.origin)))))
                // calculate rotation bounds for this vertex
                BoundsForRotation(vert.rotationOrigin, tw.axis, vert.p, vert.endp, vert.rotationBounds)
                // if the rotation axis goes through the vertex then the vertex is not used
                d = (vert.p - vert.rotationOrigin).LengthSqr()
                if (d > ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                    vert.used = true
                }
                i++
            }

            // setup trm edges
            i = 1
            while (i <= tw.numEdges) {
                edge = tw.edges[i]
                // if the rotation axis goes through both the edge vertices then the edge is not used
                if (tw.vertices[edge.vertexNum[0]].used or tw.vertices[edge.vertexNum[1]].used) {
                    edge.used = true
                }
                // edge start, end and pluecker coordinate
                edge.start.set(tw.vertices[edge.vertexNum[0]].p)
                edge.end.set(tw.vertices[edge.vertexNum[1]].p)
                edge.pl.FromLine(edge.start, edge.end)
                // pluecker coordinate for edge being rotated about the z-axis
                val at = idVec3(edge.start - (tw.origin) * (tw.matrix))
                val bt = idVec3(edge.end - (tw.origin) * (tw.matrix))
                edge.plzaxis.FromLine(at, bt)
                // get edge rotation bounds from the rotation bounds of both vertices
                edge.rotationBounds.set(tw.vertices[edge.vertexNum[0]].rotationBounds)
                edge.rotationBounds.AddBounds(tw.vertices[edge.vertexNum[1]].rotationBounds)
                // used to calculate if the rotation axis intersects the trm
                edge.bitNum = 0
                i++
            }
            tw.bounds.Clear()

            // rotate trm polygon planes
            if (trm_rotated && model_rotated) {
                tmpAxis = trmAxis.times(invModelAxis)
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(tmpAxis)
                    i++
                }
            } else if (trm_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(trmAxis)
                    i++
                }
            } else if (model_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(invModelAxis)
                    i++
                }
            }

            // setup trm polygons
            i = 0
            while (i < tw.numPolys) {
                poly = tw.polys[i]
                poly.used = true
                // set trm polygon plane distance
                poly.plane.FitThroughPoint(tw.edges[abs(poly.edges[0])].start)
                // get polygon bounds from edge bounds
                poly.rotationBounds.Clear()
                j = 0
                while (j < poly.numEdges) {

                    // add edge rotation bounds to polygon rotation bounds
                    edge = tw.edges[abs(poly.edges[j])]
                    poly.rotationBounds.AddBounds(edge.rotationBounds)
                    j++
                }
                // get trace bounds from polygon bounds
                tw.bounds.AddBounds(poly.rotationBounds)
                i++
            }

            // extents including the maximum error of the circle approximation traced through the axial BSP tree
            i = 0
            while (i < 3) {
                tw.size[0, i] = tw.bounds[0][i] - tw.start[i]
                tw.size[1, i] = tw.bounds[1][i] - tw.start[i]
                if (abs(tw.size[0][i]) > abs(tw.size[1][i])) {
                    tw.extents[i] = abs(tw.size[0][i]) + maxErr + CollisionModel.CM_BOX_EPSILON
                } else {
                    tw.extents[i] = abs(tw.size[1][i]) + maxErr + CollisionModel.CM_BOX_EPSILON
                }
                i++
            }

            // for back-face culling
            if (tw.isConvex) {
                if (tw.start == tw.origin) {
                    tw.axisIntersectsTrm = true
                } else {
                    // determine if the rotation axis intersects the trm
                    plaxis.FromRay(tw.origin, tw.axis)
                    poly = tw.polys[i]
                    i = 0
                    while (i < tw.numPolys) {

                        // back face cull polygons
                        if (poly.plane.Normal().times(tw.axis) > 0.0f) {
                            i++
                            poly = tw.polys[i]
                            continue
                        }
                        // test if the axis goes between the polygon edges
                        j = 0
                        while (j < poly.numEdges) {
                            edgeNum = poly.edges[j]
                            edge = tw.edges[abs(edgeNum)]
                            if (0 == edge.bitNum and 2) {
                                d = plaxis.PermutedInnerProduct(edge.pl)
                                edge.bitNum = (Math_h.FLOATSIGNBITSET(d) or 2)
                            }
                            if (edge.bitNum xor Math_h.INTSIGNBITSET(edgeNum) and 1 == 1) {
                                break
                            }
                            j++
                        }
                        if (j >= poly.numEdges) {
                            tw.axisIntersectsTrm = true
                            break
                        }
                        i++
                        poly = tw.polys[i]
                    }
                }
            }

            // setup rotation heart plane
            tw.heartPlane1.SetNormal(tw.axis)
            tw.heartPlane1.FitThroughPoint(tw.start)
            tw.maxDistFromHeartPlane1 = 0.0f
            i = 0
            while (i < tw.numVerts) {
                d = abs(tw.heartPlane1.Distance(tw.vertices[i].p))
                if (d > tw.maxDistFromHeartPlane1) {
                    tw.maxDistFromHeartPlane1 = d
                }
                i++
            }
            tw.maxDistFromHeartPlane1 += CollisionModel.CM_BOX_EPSILON

            // inverse rotation to rotate model vertices towards trace model
            tw.modelVertexRotation.Set(tw.origin, tw.axis, -tw.angle)

            // trace through the model
            TraceThroughModel(tw)

            // store results
            results.oSet(tw.trace)
            results.endpos.set(start)
            if (tw.maxTan == initialTan) {
                results.fraction = 1.0f
            } else {
                results.fraction =
                    abs(atan(tw.maxTan.toDouble()).toFloat() * (2.0f * 180.0f / idMath.PI) / tw.angle)
            }
            assert(results.fraction <= 1.0f)
            endRotation.Set(rorg, axis, startAngle + (endAngle - startAngle) * results.fraction)
            endRotation.RotatePoint(results.endpos)
            results.endAxis.set(trmAxis.times(endRotation.ToMat3()))
            if (results.fraction < 1.0f) {
                // rotate trace plane normal if there was a collision with a rotated model
                if (model_rotated) {
                    results.c.normal.timesAssign(modelAxis)
                    results.c.point.timesAssign(modelAxis)
                }
                results.c.point.plusAssign(modelOrigin)
                results.c.dist += modelOrigin.times(results.c.normal)
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::TestTrmVertsInBrush

         returns true if any of the trm vertices is inside the brush
         ================
         */
        // CollisionMap_contents.cpp
        private fun TestTrmVertsInBrush(tw: cm_traceWork_s, b: cm_brush_s): Boolean {
            var i: Int
            var j: Int
            val numVerts: Int
            var bestPlane: Int
            var d: Float
            var bestd: Float
            if (b.checkcount == checkCount) {
                return false
            }
            b.checkcount = checkCount
            if (0 == b.contents and tw.contents) {
                return false
            }

            // if the brush bounds don't intersect the trace bounds
            if (!b.bounds.IntersectsBounds(tw.bounds)) {
                return false
            }
            numVerts = if (tw.pointTrace) {
                1
            } else {
                tw.numVerts
            }
            j = 0
            while (j < numVerts) {
                val p = idVec3(tw.vertices[j].p)

                // see if the point is inside the brush
                bestPlane = 0
                bestd = -idMath.INFINITY
                i = 0
                while (i < b.numPlanes) {
                    d = b.planes[i].Distance(p)
                    if (d >= 0.0f) {
                        break
                    }
                    if (d > bestd) {
                        bestd = d
                        bestPlane = i
                    }
                    i++
                }
                if (i >= b.numPlanes) {
                    tw.trace.fraction = 0.0f
                    tw.trace.c.type = contactType_t.CONTACT_TRMVERTEX
                    tw.trace.c.normal.set(b.planes[bestPlane].Normal())
                    tw.trace.c.dist = b.planes[bestPlane].Dist()
                    tw.trace.c.contents = b.contents
                    tw.trace.c.material = b.material
                    tw.trace.c.point.set(p)
                    tw.trace.c.modelFeature = 0
                    tw.trace.c.trmFeature = j
                    return true
                }
                j++
            }
            return false
        }

        /*
         ================
         idCollisionModelManagerLocal::TestTrmInPolygon

         returns true if the trm intersects the polygon
         ================
         */
        private fun TestTrmInPolygon(tw: cm_traceWork_s, p: cm_polygon_s, modelFeature: Int): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var edgeNum: Int
            var flip: Int
            var trmEdgeNum: Int
            var bitNum: Int
            var bestPlane: Int
            val sides = IntArray(TraceModel.MAX_TRACEMODEL_VERTS)
            var d: Float
            var bestd: Float
            var trmEdge: cm_trmEdge_s?
            var edge: cm_edge_s?
            var v: cm_vertex_s?
            var v1: cm_vertex_s?
            var v2: cm_vertex_s?

            // if already checked this polygon
            if (p.checkcount == checkCount) {
                return false
            }
            p.checkcount = checkCount

            // if this polygon does not have the right contents behind it
            if (0 == p.contents and tw.contents) {
                return false
            }

            // if the polygon bounds don't intersect the trace bounds
            if (!p.bounds.IntersectsBounds(tw.bounds)) {
                return false
            }
            when (tw.bounds.PlaneSide(p.plane)) {
                Plane.PLANESIDE_CROSS -> {}
                Plane.PLANESIDE_FRONT -> {
                    if (tw.model!!.isConvex) {
                        tw.quickExit = true
                        return true
                    }
                    return false
                }
                else -> return false
            }

            // if the trace model is convex
            if (tw.isConvex) {
                // test if any polygon vertices are inside the trm
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    edge = tw.model!!.edges!![abs(edgeNum)]
                    // if this edge is already tested
                    if (edge.checkcount == checkCount) {
                        i++
                        continue
                    }
                    j = 0
                    while (j < 2) {
                        v = tw.model!!.vertices!![edge.vertexNum[j]]
                        // if this vertex is already tested
                        if (v.checkcount == checkCount) {
                            j++
                            continue
                        }
                        bestPlane = 0
                        bestd = -idMath.INFINITY
                        k = 0
                        while (k < tw.numPolys) {
                            d = tw.polys[k].plane.Distance(v.p)
                            if (d >= 0.0f) {
                                break
                            }
                            if (d > bestd) {
                                bestd = d
                                bestPlane = k
                            }
                            k++
                        }
                        if (k >= tw.numPolys) {
                            tw.trace.fraction = 0.0f
                            tw.trace.c.type = contactType_t.CONTACT_MODELVERTEX
                            tw.trace.c.normal.set(-tw.polys[bestPlane].plane.Normal())
                            tw.trace.c.dist = -tw.polys[bestPlane].plane.Dist()
                            tw.trace.c.contents = p.contents
                            tw.trace.c.material = p.material
                            tw.trace.c.point.set(v.p)
                            tw.trace.c.modelFeature = edge.vertexNum[j]
                            tw.trace.c.trmFeature = 0
                            return true
                        }
                        j++
                    }
                    i++
                }
            }
            i = 0
            while (i < p.numEdges) {
                edgeNum = p.edges[i]
                edge = tw.model!!.edges!![abs(edgeNum)]
                // reset sidedness cache if this is the first time we encounter this edge
                if (edge.checkcount != checkCount) {
                    edge.sideSet = 0
                }
                // pluecker coordinate for edge
                tw.polygonEdgePlueckerCache[i].FromLine(
                    tw.model!!.vertices!![edge.vertexNum[0]].p,
                    tw.model!!.vertices!![edge.vertexNum[1]].p
                )
                v = tw.model!!.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]]
                // reset sidedness cache if this is the first time we encounter this vertex
                if (v.checkcount != checkCount) {
                    v.sideSet = 0
                }
                v.checkcount = checkCount
                i++
            }

            // get side of polygon for each trm vertex
            i = 0
            while (i < tw.numVerts) {
                d = p.plane.Distance(tw.vertices[i].p)
                sides[i] = if (d < 0.0f) -1 else 1
                i++
            }

            // test if any trm edges go through the polygon
            i = 1
            while (i <= tw.numEdges) {

                // if the trm edge does not cross the polygon plane
                if (sides[tw.edges[i].vertexNum[0]] == sides[tw.edges[i].vertexNum[1]]) {
                    i++
                    continue
                }
                // check from which side to which side the trm edge goes
                flip = Math_h.INTSIGNBITSET(sides[tw.edges[i].vertexNum[0]])
                // test if trm edge goes through the polygon between the polygon edges
                j = 0
                while (j < p.numEdges) {
                    edgeNum = p.edges[j]
                    edge = tw.model!!.edges!![abs(edgeNum)]
                    CollisionModel_contents.CM_SetTrmEdgeSidedness(
                        edge,
                        tw.edges[i].pl,
                        tw.polygonEdgePlueckerCache[j],
                        i
                    )
                    if (Math_h.INTSIGNBITSET(edgeNum) xor (edge.side shr i and 1) xor flip != 0) {
                        break
                    }
                    j++
                }
                if (j >= p.numEdges) {
                    tw.trace.fraction = 0.0f
                    tw.trace.c.type = contactType_t.CONTACT_EDGE
                    tw.trace.c.normal.set(p.plane.Normal())
                    tw.trace.c.dist = p.plane.Dist()
                    tw.trace.c.contents = p.contents
                    tw.trace.c.material = p.material
                    tw.trace.c.point.set(tw.vertices[tw.edges[i].vertexNum[if (0 == flip) 1 else 0]].p)
                    tw.trace.c.modelFeature = modelFeature
                    tw.trace.c.trmFeature = i
                    return true
                }
                i++
            }

            // test if any polygon edges go through the trm polygons
            i = 0
            while (i < p.numEdges) {
                edgeNum = p.edges[i]
                edge = tw.model!!.edges!![abs(edgeNum)]
                if (edge.checkcount == checkCount) {
                    i++
                    continue
                }
                edge.checkcount = checkCount
                j = 0
                while (j < tw.numPolys) {
                    v1 = tw.model!!.vertices!![edge.vertexNum[0]]
                    CollisionModel_contents.CM_SetTrmPolygonSidedness(v1, tw.polys[j].plane, j)
                    v2 = tw.model!!.vertices!![edge.vertexNum[1]]
                    CollisionModel_contents.CM_SetTrmPolygonSidedness(v2, tw.polys[j].plane, j)
                    // if the polygon edge does not cross the trm polygon plane
                    if (0 == v1.side xor v2.side shr j and 1) {
                        j++
                        continue
                    }
                    flip = (v1.side shr j and 1)
                    // test if polygon edge goes through the trm polygon between the trm polygon edges
                    k = 0
                    while (k < tw.polys[j].numEdges) {
                        trmEdgeNum = tw.polys[j].edges[k]
                        trmEdge = tw.edges[abs(trmEdgeNum)]
                        bitNum = abs(trmEdgeNum)
                        CollisionModel_contents.CM_SetTrmEdgeSidedness(
                            edge,
                            trmEdge.pl,
                            tw.polygonEdgePlueckerCache[i],
                            bitNum
                        )
                        if (Math_h.INTSIGNBITSET(trmEdgeNum) xor (edge.side shr bitNum and 1) xor flip != 0) {
                            break
                        }
                        k++
                    }
                    if (k >= tw.polys[j].numEdges) {
                        tw.trace.fraction = 0.0f
                        tw.trace.c.type = contactType_t.CONTACT_EDGE
                        tw.trace.c.normal.set(-tw.polys[j].plane.Normal())
                        tw.trace.c.dist = -tw.polys[j].plane.Dist()
                        tw.trace.c.contents = p.contents
                        tw.trace.c.material = p.material
                        tw.trace.c.point.set(tw.model!!.vertices!![edge.vertexNum[if (0 == flip) 1 else 0]].p)
                        tw.trace.c.modelFeature = edgeNum
                        tw.trace.c.trmFeature = j
                        return true
                    }
                    j++
                }
                i++
            }
            return false
        }

        private fun PointNode(p: idVec3, model: cm_model_s): cm_node_s {
            var currentNode: cm_node_s
            currentNode = model.node!!
            while (currentNode.planeType != -1) {
                currentNode = if (p[currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]!!
                } else {
                    currentNode.children[1]!!
                }
                assert(currentNode != null)
            }
            return currentNode
        }

        private fun PointContents(
            p: idVec3,  /*cmHandle_t*/
            model: Int
        ): Int {
            var i: Int
            var d: Float
            val node: cm_node_s?
            var bref: cm_brushRef_s?
            var b: cm_brush_s
            node = PointNode(p, models!![model]!!)
            bref = node.brushes
            while (bref != null) {
                b = bref.b!!
                // test if the point is within the brush bounds
                i = 0
                while (i < 3) {
                    if (p[i] < b.bounds[0][i]) {
                        break
                    }
                    if (p[i] > b.bounds[1][i]) {
                        break
                    }
                    i++
                }
                if (i < 3) {
                    bref = bref.next
                    continue
                }
                // test if the point is inside the brush
                i = 0
                while (i < b.numPlanes) {
                    d = b.planes[i].Distance(p)
                    if (d >= 0) {
                        break
                    }
                    i++
                }
                if (i >= b.numPlanes) {
                    return b.contents
                }
                bref = bref.next
            }
            return 0
        }

        private fun TransformedPointContents(
            p: idVec3,  /*cmHandle_t*/
            model: Int, origin: idVec3, modelAxis: idMat3
        ): Int {
            // subtract origin offset
            val p_l = idVec3(p - (origin))
            if (modelAxis.IsRotated()) {
                p_l.timesAssign(modelAxis)
            }
            return PointContents(p_l, model)
        }

        private fun ContentsTrm(
            results: trace_s, start: idVec3, trm: idTraceModel?, trmAxis: idMat3,
            contentMask: Int,  /*cmHandle_t*/
            model: Int, modelOrigin: idVec3, modelAxis: idMat3
        ): Int {
            var i: Int
            val model_rotated: Boolean
            val trm_rotated: Boolean
            var invModelAxis = idMat3()
            val tmpAxis: idMat3

            // fast point case
            if (null == trm
                || trm.bounds[1][0] - trm.bounds[0][0] <= 0.0f && trm.bounds[1][1] - trm.bounds[0][1] <= 0.0f && trm.bounds[1][2] - trm.bounds[0][2] <= 0.0f
            ) {
                results.c.contents = TransformedPointContents(start, model, modelOrigin, modelAxis)
                results.fraction = (if (results.c.contents == 0) 1 else 0).toFloat()
                results.endpos.set(start)
                results.endAxis.set(trmAxis)
                return results.c.contents
            }
            checkCount++
            tw.trace.fraction = 1.0f
            tw.trace.c.contents = 0
            tw.trace.c.type = contactType_t.CONTACT_NONE
            tw.contents = contentMask
            tw.isConvex = true
            tw.rotation = false
            tw.positionTest = true
            tw.pointTrace = false
            tw.quickExit = false
            tw.numContacts = 0
            tw.model = models?.get(model)
            tw.start.set(start - (modelOrigin))
            tw.end.set(tw.start)
            model_rotated = modelAxis.IsRotated()
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose()
            }

            // setup trm structure
            SetupTrm(tw, trm)
            trm_rotated = trmAxis.IsRotated()

            // calculate vertex positions
            if (trm_rotated) {
                i = 0
                while (i < tw.numVerts) {

                    // rotate trm around the start position
                    tw.vertices[i].p.timesAssign(trmAxis)
                    i++
                }
            }
            i = 0
            while (i < tw.numVerts) {

                // set trm at start position
                tw.vertices[i].p.plusAssign(tw.start)
                i++
            }
            if (model_rotated) {
                i = 0
                while (i < tw.numVerts) {

                    // rotate trm around model instead of rotating the model
                    tw.vertices[i].p.timesAssign(invModelAxis)
                    i++
                }
            }

            // add offset to start point
            if (trm_rotated) {
                val dir = idVec3(trm.offset.times(trmAxis))
                tw.start.plusAssign(dir)
                tw.end.plusAssign(dir)
            } else {
                tw.start.plusAssign(trm.offset)
                tw.end.plusAssign(trm.offset)
            }
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.timesAssign(invModelAxis)
                tw.end.timesAssign(invModelAxis)
            }

            // setup trm vertices
            tw.size.Clear()
            i = 0
            while (i < tw.numVerts) {

                // get axial trm size after rotations
                tw.size.AddPoint(tw.vertices[i].p - (tw.start))
                i++
            }

            // setup trm edges
            i = 1
            while (i <= tw.numEdges) {

                // edge start, end and pluecker coordinate
                tw.edges[i].start.set(tw.vertices[tw.edges[i].vertexNum[0]].p)
                tw.edges[i].end.set(tw.vertices[tw.edges[i].vertexNum[1]].p)
                tw.edges[i].pl.FromLine(tw.edges[i].start, tw.edges[i].end)
                i++
            }

            // setup trm polygons
            if (trm_rotated && model_rotated) {
                tmpAxis = trmAxis.times(invModelAxis)
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(tmpAxis)
                    i++
                }
            } else if (trm_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(trmAxis)
                    i++
                }
            } else if (model_rotated) {
                i = 0
                while (i < tw.numPolys) {
                    tw.polys[i].plane.timesAssign(invModelAxis)
                    i++
                }
            }
            i = 0
            while (i < tw.numPolys) {
                tw.polys[i].plane.FitThroughPoint(tw.edges[abs(tw.polys[i].edges[0])].start)
                i++
            }

            // bounds for full trace, a little bit larger for epsilons
            i = 0
            while (i < 3) {
                if (tw.start[i] < tw.end[i]) {
                    tw.bounds[0, i] = tw.start[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                    tw.bounds[1, i] = tw.end[i] + tw.size[1][i] + CollisionModel.CM_BOX_EPSILON
                } else {
                    tw.bounds[0, i] = tw.end[i] + tw.size[0][i] - CollisionModel.CM_BOX_EPSILON
                    tw.bounds[1, i] = tw.start[i] + tw.size[1][i] + CollisionModel.CM_BOX_EPSILON
                }
                if (abs(tw.size[0][i]) > abs(tw.size[1][i])) {
                    tw.extents[i] = abs(tw.size[0][i]) + CollisionModel.CM_BOX_EPSILON
                } else {
                    tw.extents[i] = abs(tw.size[1][i]) + CollisionModel.CM_BOX_EPSILON
                }
                i++
            }

            // trace through the model
            TraceThroughModel(tw)
            results.oSet(tw.trace)
            results.fraction = (if (results.c.contents == 0) 1 else 0).toFloat()
            results.endpos.set(start)
            results.endAxis.set(trmAxis)
            return results.c.contents
        }

        /*
         ===============================================================================

         Trace through the spatial subdivision

         ===============================================================================
         */
        // CollisionMap_trace.cpp
        private fun TraceTrmThroughNode(tw: cm_traceWork_s, node: cm_node_s) {
            var pref: cm_polygonRef_s?
            var bref: cm_brushRef_s?

            // position test
            if (tw.positionTest) {
                // if already stuck in solid
                if (tw.trace.fraction == 0.0f) {
                    return
                }
                // test if any of the trm vertices is inside a brush
                bref = node.brushes
                while (bref != null) {
                    if (TestTrmVertsInBrush(tw, bref.b!!)) {
                        return
                    }
                    bref = bref.next
                }
                // if just testing a point we're done
                if (tw.pointTrace) {
                    return
                }
                var modelFeature = 0
                // test if the trm is stuck in any polygons
                pref = node.polygons
                while (pref != null) {
                    if (TestTrmInPolygon(tw, pref.p!!, modelFeature++)) {
                        return
                    }
                    pref = pref.next
                }
            } else if (tw.rotation) {
                // rotate through all polygons in this leaf
                pref = node.polygons
                while (pref != null) {
                    if (RotateTrmThroughPolygon(tw, pref.p!!)) {
                        return
                    }
                    pref = pref.next
                }
            } else {
                // trace through all polygons in this leaf
                pref = node.polygons
                while (pref != null) {
                    if (TranslateTrmThroughPolygon(tw, pref.p!!)) {
                        return
                    }
                    pref = pref.next
                }
            }
        }

        private fun TraceThroughAxialBSPTree_r(
            tw: cm_traceWork_s,
            node: cm_node_s?,
            p1f: Float,
            p2f: Float,
            p1: idVec3,
            p2: idVec3
        ) {
            val t1: Float
            val t2: Float
            val offset: Float
            var frac: Float
            var frac2: Float
            val idist: Float
            val mid = idVec3()
            val side: Int
            var midf: Float
            if (null == node) {
                return
            }
            if (tw.quickExit) {
                return  // stop immediately
            }
            if (tw.trace.fraction <= p1f) {
                return  // already hit something nearer
            }

            // if we need to test this node for collisions
            if (node.polygons != null || tw.positionTest && node.brushes != null) {
                // trace through node with collision data
                TraceTrmThroughNode(tw, node)
            }
            // if already stuck in solid
            if (tw.positionTest && tw.trace.fraction == 0.0f) {
                return
            }
            // if this is a leaf node
            if (node.planeType == -1) {
                return
            }
            if (NO_SPATIAL_SUBDIVISION) {
                TraceThroughAxialBSPTree_r(tw, node.children[0], p1f, p2f, p1, p2)
                TraceThroughAxialBSPTree_r(tw, node.children[1], p1f, p2f, p1, p2)
                return
            }
            // distance from plane for trace start and end
            t1 = p1[node.planeType] - node.planeDist
            t2 = p2[node.planeType] - node.planeDist
            // adjust the plane distance appropriately for mins/maxs
            offset = tw.extents[node.planeType]
            // see which sides we need to consider
            if (t1 >= offset && t2 >= offset) {
                TraceThroughAxialBSPTree_r(tw, node.children[0], p1f, p2f, p1, p2)
                return
            }
            if (t1 < -offset && t2 < -offset) {
                TraceThroughAxialBSPTree_r(tw, node.children[1], p1f, p2f, p1, p2)
                return
            }
            if (t1 < t2) {
                idist = 1.0f / (t1 - t2)
                side = 1
                frac2 = (t1 + offset) * idist
                frac = (t1 - offset) * idist
            } else if (t1 > t2) {
                idist = 1.0f / (t1 - t2)
                side = 0
                frac2 = (t1 - offset) * idist
                frac = (t1 + offset) * idist
            } else {
                side = 0
                frac = 1.0f
                frac2 = 0.0f
            }

            // move up to the node
            if (frac < 0.0f) {
                frac = 0.0f
            } else if (frac > 1.0f) {
                frac = 1.0f
            }
            midf = p1f + (p2f - p1f) * frac
            mid[0] = p1[0] + frac * (p2[0] - p1[0])
            mid[1] = p1[1] + frac * (p2[1] - p1[1])
            mid[2] = p1[2] + frac * (p2[2] - p1[2])
            TraceThroughAxialBSPTree_r(tw, node.children[side], p1f, midf, p1, mid)

            // go past the node
            if (frac2 < 0.0f) {
                frac2 = 0.0f
            } else if (frac2 > 1.0f) {
                frac2 = 1.0f
            }
            midf = p1f + (p2f - p1f) * frac2
            mid[0] = p1[0] + frac2 * (p2[0] - p1[0])
            mid[1] = p1[1] + frac2 * (p2[1] - p1[1])
            mid[2] = p1[2] + frac2 * (p2[2] - p1[2])
            TraceThroughAxialBSPTree_r(tw, node.children[side xor 1], midf, p2f, mid, p2)
        }

        private fun TraceThroughModel(tw: cm_traceWork_s) {
            val d: Float
            var i: Int
            val numSteps: Int
            val start = idVec3()
            val end = idVec3()
            val rot = idRotation()
            if (!tw.rotation) {
                // trace through spatial subdivision and then through leafs
                TraceThroughAxialBSPTree_r(tw, tw.model!!.node, 0f, 1f, tw.start, tw.end)
            } else {
                // approximate the rotation with a series of straight line movements
                // total length covered along circle
                d = tw.radius * Math_h.DEG2RAD(tw.angle)
                // if more than one step
                if (d > CIRCLE_APPROXIMATION_LENGTH) {
                    // number of steps for the approximation
                    numSteps = (CIRCLE_APPROXIMATION_LENGTH / d).toInt()
                    // start of approximation
                    start.set(tw.start)
                    // trace circle approximation steps through the BSP tree
                    i = 0
                    while (i < numSteps) {

                        // calculate next point on approximated circle
                        rot.Set(tw.origin, tw.axis, tw.angle * ((i + 1).toFloat() / numSteps))
                        end.set(rot.times(start))
                        // trace through spatial subdivision and then through leafs
                        TraceThroughAxialBSPTree_r(tw, tw.model!!.node, 0f, 1f, start, end)
                        // no need to continue if something was hit already
                        if (tw.trace.fraction < 1.0f) {
                            return
                        }
                        start.set(end)
                        i++
                    }
                } else {
                    start.set(tw.start)
                }
                // last step of the approximation
                TraceThroughAxialBSPTree_r(tw, tw.model!!.node, 0f, 1f, start, tw.end)
            }
        }

        //        private void RecurseProcBSP_r(trace_t results, int parentNodeNum, int nodeNum, float p1f, float p2f, final idVec3 p1, final idVec3 p2);
        /*
         ===============================================================================

         Free map

         ===============================================================================
         */
        // CollisionMap_load.cpp
        private fun Clear() {
            mapName.Clear()
            mapFileTime = 0
            loaded = false
            checkCount = 0
            maxModels = 0
            numModels = 0
            models = null
            trmPolygons = emptyArray()
            trmBrushes[0] = null
            trmMaterial = null
            numProcNodes = 0
            procNodes = null
            getContacts = false
            contacts = null
            maxContacts = 0
            numContacts = 0
        }

        private fun FreeTrmModelStructure() {
            assert(models != null)
            if (null == models?.get(MAX_SUBMODELS)) {
                return
            }
            for (i in 0 until TraceModel.MAX_TRACEMODEL_POLYS) {
                FreePolygon(models?.get(MAX_SUBMODELS)!!, trmPolygons[i]?.p!!)
            }
            FreeBrush(models?.get(MAX_SUBMODELS)!!, trmBrushes[0]?.b!!)
            models?.get(MAX_SUBMODELS)!!.node!!.polygons = null
            models?.get(MAX_SUBMODELS)!!.node!!.brushes = null
            FreeModel(models?.get(MAX_SUBMODELS)!!)
        }

        // model deallocation
        private fun RemovePolygonReferences_r(node: cm_node_s?, p: cm_polygon_s) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            while (currentNode != null) {
                pref = currentNode.polygons
                while (pref != null) {
                    if (pref.p === p) {
                        pref.p = null
                        // cannot return here because we can have links down the tree due to polygon merging
                        //return;
                    }
                    pref = pref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode = if (p.bounds[0][currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]
                } else if (p.bounds[1][currentNode.planeType] < currentNode.planeDist) {
                    currentNode.children[1]
                } else {
                    RemovePolygonReferences_r(currentNode.children[1], p)
                    currentNode.children[0]
                }
            }
        }

        private fun RemoveBrushReferences_r(node: cm_node_s?, b: cm_brush_s) {
            var currentNode = node
            var bref: cm_brushRef_s?
            while (currentNode != null) {
                bref = currentNode.brushes
                while (bref != null) {
                    if (bref.b === b) {
                        bref.b = null
                        return
                    }
                    bref = bref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode = if (b.bounds[0][currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]
                } else if (b.bounds[1][currentNode.planeType] < currentNode.planeDist) {
                    currentNode.children[1]
                } else {
                    RemoveBrushReferences_r(currentNode.children[1], b)
                    currentNode.children[0]
                }
            }
        }

        private fun FreePolygon(model: cm_model_s, poly: cm_polygon_s) {
            model.numPolygons--
            model.polygonMemory -= cm_polygon_s.BYTES + (poly.numEdges - 1) * Integer.BYTES
            model.polygonBlock = null
        }

        private fun FreeBrush(model: cm_model_s, brush: cm_brush_s) {
            model.numBrushes--
            model.brushMemory -= cm_brush_s.BYTES + (brush.numPlanes - 1) * idPlane.BYTES
            model.brushBlock = null
        }

        private fun FreeTree_r(model: cm_model_s, headNode: cm_node_s, node: cm_node_s) {
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s?
            var bref: cm_brushRef_s?
            var b: cm_brush_s?

            // free all polygons at this node
            pref = node.polygons
            while (pref != null) {
                p = pref.p
                if (p != null) {
                    // remove all other references to this polygon
                    RemovePolygonReferences_r(headNode, p)
                    FreePolygon(model, p)
                }
                node.polygons = pref.next
                pref = node.polygons
            }
            // free all brushes at this node
            bref = node.brushes
            while (bref != null) {
                b = bref.b
                if (b != null) {
                    // remove all other references to this brush
                    RemoveBrushReferences_r(headNode, b)
                    FreeBrush(model, b)
                }
                node.brushes = bref.next
                bref = node.brushes
            }
            // recurse down the tree
            if (node.planeType != -1) {
                FreeTree_r(model, headNode, node.children[0]!!)
                node.children[0] = null
                FreeTree_r(model, headNode, node.children[1]!!)
                node.children[1] = null
            }
        }

        private fun FreeModel(model: cm_model_s) {
            var polygonRefBlock: cm_polygonRefBlock_s?
            var nextPolygonRefBlock: cm_polygonRefBlock_s?
            var brushRefBlock: cm_brushRefBlock_s?
            var nextBrushRefBlock: cm_brushRefBlock_s?
            var nodeBlock: cm_nodeBlock_s?
            var nextNodeBlock: cm_nodeBlock_s?

            // free the tree structure
            if (model.node != null) {
                FreeTree_r(model, model.node!!, model.node!!)
            }
            // free blocks with polygon references
            polygonRefBlock = model.polygonRefBlocks
            while (polygonRefBlock != null) {
                nextPolygonRefBlock = polygonRefBlock.next
                polygonRefBlock = nextPolygonRefBlock
            }
            // free blocks with brush references
            brushRefBlock = model.brushRefBlocks
            while (brushRefBlock != null) {
                nextBrushRefBlock = brushRefBlock.next
                //Mem_Free(brushRefBlock);
                brushRefBlock = nextBrushRefBlock
            }
            // free blocks with nodes
            nodeBlock = model.nodeBlocks
            while (nodeBlock != null) {
                nextNodeBlock = nodeBlock.next
                //Mem_Free(nodeBlock);
                nodeBlock = nextNodeBlock
            }
            // free block allocated polygons
            model.polygonBlock = null //Mem_Free(model.polygonBlock);
            // free block allocated brushes
            model.brushBlock = null //Mem_Free(model.brushBlock);
            // free edges
            model.edges = null //Mem_Free(model.edges);
            // free vertices
            model.vertices = null //Mem_Free(model.vertices);
        }

        /*
         ===============================================================================

         Merging polygons

         ===============================================================================
         */
        /*
         =============
         idCollisionModelManagerLocal::ReplacePolygons

         does not allow for a node to have multiple references to the same polygon
         =============
         */
        // merging polygons
        private fun ReplacePolygons(
            model: cm_model_s,
            node: cm_node_s,
            p1: cm_polygon_s,
            p2: cm_polygon_s,
            newp: cm_polygon_s
        ) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var lastpref: cm_polygonRef_s?
            var nextpref: cm_polygonRef_s?
            var p: cm_polygon_s?
            var linked: Boolean
            while (true) {
                linked = false
                lastpref = null
                pref = currentNode.polygons
                while (pref != null) {
                    nextpref = pref.next
                    //
                    p = pref.p
                    // if this polygon reference should change
                    if (p === p1 || p === p2) {
                        // if the new polygon is already linked at this node
                        if (linked) {
                            if (lastpref != null) {
                                lastpref.next = nextpref
                            } else {
                                currentNode.polygons = nextpref
                            }
                            model.numPolygonRefs--
                        } else {
                            pref.p = newp
                            linked = true
                            lastpref = pref
                        }
                    } else {
                        lastpref = pref
                    }
                    pref = nextpref
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                if (p1.bounds[0][currentNode.planeType] > currentNode.planeDist && p2.bounds[0][currentNode.planeType] > currentNode.planeDist
                ) {
                    currentNode = currentNode.children[0]!!
                } else if (p1.bounds[1][currentNode.planeType] < currentNode.planeDist && p2.bounds[1][currentNode.planeType] < currentNode.planeDist
                ) {
                    currentNode = currentNode.children[1]!!
                } else {
                    currentNode = currentNode.children[0]!!
                }
            }
        }

        private fun TryMergePolygons(model: cm_model_s, p1: cm_polygon_s, p2: cm_polygon_s): cm_polygon_s? {
            var i: Int
            var j: Int
            var nexti: Int
            var prevj: Int
            var p1BeforeShare: Int
            var p1AfterShare: Int
            var p2BeforeShare: Int
            var p2AfterShare: Int
            val newEdges = IntArray(CM_MAX_POLYGON_EDGES)
            var newNumEdges: Int
            var edgeNum: Int
            var edgeNum1: Int
            var edgeNum2: Int
            val newEdgeNum1 = IntArray(1)
            val newEdgeNum2 = IntArray(1)
            var edge: cm_edge_s?
            val newp: cm_polygon_s?
            val delta = idVec3()
            val normal = idVec3()
            var dot: Float
            var keep1: Boolean
            var keep2: Boolean
            if (p1.material !== p2.material) {
                return null
            }
            if (abs(p1.plane.Dist() - p2.plane.Dist()) > NORMAL_EPSILON) {
                return null
            }
            i = 0
            while (i < 3) {
                if (abs(p1.plane.Normal()[i] - p2.plane.Normal()[i]) > NORMAL_EPSILON) {
                    return null
                }
                if (p1.bounds[0][i] > p2.bounds[1][i]) {
                    return null
                }
                if (p1.bounds[1][i] < p2.bounds[0][i]) {
                    return null
                }
                i++
            }
            // this allows for merging polygons with multiple shared edges
            // polygons with multiple shared edges probably never occur tho ;)
            p2AfterShare = -1
            p2BeforeShare = p2AfterShare
            p1AfterShare = p2BeforeShare
            p1BeforeShare = p1AfterShare
            i = 0
            while (i < p1.numEdges) {
                nexti = (i + 1) % p1.numEdges
                j = 0
                while (j < p2.numEdges) {
                    prevj = (j + p2.numEdges - 1) % p2.numEdges
                    //
                    if (abs(p1.edges[i]) != abs(p2.edges[j])) {
                        // if the next edge of p1 and the previous edge of p2 are the same
                        if (abs(p1.edges[nexti]) == abs(p2.edges[prevj])) {
                            // if both polygons don't use the edge in the same direction
                            if (p1.edges[nexti] != p2.edges[prevj]) {
                                p1BeforeShare = i
                                p2AfterShare = j
                            }
                            break
                        }
                    } // if both polygons don't use the edge in the same direction
                    else if (p1.edges[i] != p2.edges[j]) {
                        // if the next edge of p1 and the previous edge of p2 are not the same
                        if (abs(p1.edges[nexti]) != abs(p2.edges[prevj])) {
                            p1AfterShare = nexti
                            p2BeforeShare = prevj
                            break
                        }
                    }
                    j++
                }
                i++
            }
            if (p1BeforeShare < 0 || p1AfterShare < 0 || p2BeforeShare < 0 || p2AfterShare < 0) {
                return null
            }

            // check if the new polygon would still be convex
            edgeNum = p1.edges[p1BeforeShare]
            edge = model.edges!![abs(edgeNum)]
            delta.set(
                model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p - (
                        model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p
                        )
            )
            normal.set(p1.plane.Normal().Cross(delta))
            normal.Normalize()
            edgeNum = p2.edges[p2AfterShare]
            edge = model.edges!![abs(edgeNum)]
            delta.set(
                model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p - (
                        model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p
                        )
            )
            dot = delta.times(normal)
            if (dot < -CONTINUOUS_EPSILON) {
                return null // not a convex polygon
            }
            keep1 = dot > CONTINUOUS_EPSILON
            edgeNum = p2.edges[p2BeforeShare]
            edge = model.edges!![abs(edgeNum)]
            delta.set(
                model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p - (
                        model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p
                        )
            )
            normal.set(p1.plane.Normal().Cross(delta))
            normal.Normalize()
            edgeNum = p1.edges[p1AfterShare]
            edge = model.edges!![abs(edgeNum)]
            delta.set(
                model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p - (
                        model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p
                        )
            )
            dot = delta.times(normal)
            if (dot < -CONTINUOUS_EPSILON) {
                return null // not a convex polygon
            }
            keep2 = dot > CONTINUOUS_EPSILON

            // get new edges if we need to replace colinear ones
            if (!keep1) {
                edgeNum1 = p1.edges[p1BeforeShare]
                edgeNum2 = p2.edges[p2AfterShare]
                GetEdge(
                    model,
                    model.vertices!![model.edges!![abs(edgeNum1)].vertexNum[Math_h.INTSIGNBITSET(edgeNum1)]].p,
                    model.vertices!![model.edges!![abs(edgeNum2)].vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum2)]].p,
                    newEdgeNum1,
                    CInt(-1)
                )
                if (newEdgeNum1[0] == 0) {
                    keep1 = true
                }
            }
            if (!keep2) {
                edgeNum1 = p2.edges[p2BeforeShare]
                edgeNum2 = p1.edges[p1AfterShare]
                GetEdge(
                    model,
                    model.vertices!![model.edges!![abs(edgeNum1)].vertexNum[Math_h.INTSIGNBITSET(edgeNum1)]].p,
                    model.vertices!![model.edges!![abs(edgeNum2)].vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum2)]].p,
                    newEdgeNum2,
                    CInt(-1)
                )
                if (newEdgeNum2[0] == 0) {
                    keep2 = true
                }
            }
            // set edges for new polygon
            newNumEdges = 0
            if (!keep2) {
                newEdges[newNumEdges++] = newEdgeNum2[0]
            }
            if (p1AfterShare < p1BeforeShare) {
                i = p1AfterShare + if (!keep2) 1 else 0
                while (i <= p1BeforeShare - if (!keep1) 1 else 0) {
                    newEdges[newNumEdges++] = p1.edges[i]
                    i++
                }
            } else {
                i = p1AfterShare + if (!keep2) 1 else 0
                while (i < p1.numEdges) {
                    newEdges[newNumEdges++] = p1.edges[i]
                    i++
                }
                i = 0
                while (i <= p1BeforeShare - if (!keep1) 1 else 0) {
                    newEdges[newNumEdges++] = p1.edges[i]
                    i++
                }
            }
            if (!keep1) {
                newEdges[newNumEdges++] = newEdgeNum1[0]
            }
            if (p2AfterShare < p2BeforeShare) {
                i = p2AfterShare + if (!keep1) 1 else 0
                while (i <= p2BeforeShare - if (!keep2) 1 else 0) {
                    newEdges[newNumEdges++] = p2.edges[i]
                    i++
                }
            } else {
                i = p2AfterShare + if (!keep1) 1 else 0
                while (i < p2.numEdges) {
                    newEdges[newNumEdges++] = p2.edges[i]
                    i++
                }
                i = 0
                while (i <= p2BeforeShare - if (!keep2) 1 else 0) {
                    newEdges[newNumEdges++] = p2.edges[i]
                    i++
                }
            }
            newp = AllocPolygon(model, newNumEdges)
            newp.oSet(p1) //memcpy( newp, p1, sizeof(cm_polygon_t) );
            System.arraycopy(
                newEdges,
                0,
                newp.edges,
                0,
                newNumEdges
            ) //memcpy( newp.edges, newEdges, newNumEdges * sizeof(int) );
            newp.numEdges = newNumEdges
            newp.checkcount = 0
            // increase usage count for the edges of this polygon
            i = 0
            while (i < newp.numEdges) {
                if (!keep1 && newp.edges[i] == newEdgeNum1[0]) {
                    i++
                    continue
                }
                if (!keep2 && newp.edges[i] == newEdgeNum2[0]) {
                    i++
                    continue
                }
                model.edges!![abs(newp.edges[i])].numUsers++
                i++
            }
            // create new bounds from the merged polygons
            newp.bounds.set(p1.bounds + p2.bounds)
            return newp
        }

        /*
         ===============================================================================

         Find internal edges

         ===============================================================================
         */
        private fun MergePolygonWithTreePolygons(
            model: cm_model_s,
            node: cm_node_s,
            polygon: cm_polygon_s
        ): Boolean {
            var currentNode = node
            var i: Int
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var newp: cm_polygon_s?
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    //
                    if (p === polygon) {
                        pref = pref.next
                        continue
                    }
                    //
                    newp = TryMergePolygons(model, polygon, p)
                    // if polygons were merged
                    if (newp != null) {
                        model.numMergedPolys++
                        // replace links to the merged polygons with links to the new polygon
                        ReplacePolygons(model, model.node!!, polygon, p, newp)
                        // decrease usage count for edges of both merged polygons
                        i = 0
                        while (i < polygon.numEdges) {
                            model.edges!![abs(polygon.edges[i])].numUsers--
                            i++
                        }
                        i = 0
                        while (i < p.numEdges) {
                            model.edges!![abs(p.edges[i])].numUsers--
                            i++
                        }
                        // free merged polygons
                        FreePolygon(model, polygon)
                        FreePolygon(model, p)
                        return true
                    }
                    pref = pref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode = if (polygon.bounds[0][currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]!!
                } else if (polygon.bounds[1][currentNode.planeType] < currentNode.planeDist) {
                    currentNode.children[1]!!
                } else {
                    if (MergePolygonWithTreePolygons(model, currentNode.children[1]!!, polygon)) {
                        return true
                    }
                    currentNode.children[0]!!
                }
            }
            return false
        }

        /*
         =============
         idCollisionModelManagerLocal::MergeTreePolygons

         try to merge any two polygons with the same surface flags and the same contents
         =============
         */
        private fun MergeTreePolygons(model: cm_model_s, node: cm_node_s) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s?
            var merge: Boolean
            while (true) {
                do {
                    merge = false
                    pref = currentNode.polygons
                    while (pref != null) {
                        p = pref.p
                        // if we checked this polygon already
                        if (p!!.checkcount == checkCount) {
                            pref = pref.next
                            continue
                        }
                        p.checkcount = checkCount
                        // try to merge this polygon with other polygons in the tree
                        if (MergePolygonWithTreePolygons(model, model.node!!, p)) {
                            merge = true
                            break
                        }
                        pref = pref.next
                    }
                } while (merge)
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                MergeTreePolygons(model, currentNode.children[1]!!)
                currentNode = currentNode.children[0]!!
            }
        }

        /*

         if (two polygons have the same contents)
         if (the normals of the two polygon planes face towards each other)
         if (an edge is shared between the polygons)
         if (the edge is not shared in the same direction)
         then this is an internal edge
         else
         if (this edge is on the plane of the other polygon)
         if (this edge if fully inside the winding of the other polygon)
         then this edge is an internal edge

         */
        // finding internal edges
        private fun PointInsidePolygon(model: cm_model_s, p: cm_polygon_s, v: idVec3): Boolean {
            var i: Int
            var edgeNum: Int
            val v1 = idVec3()
            val v2 = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            val vec = idVec3()
            var edge: cm_edge_s
            i = 0
            while (i < p.numEdges) {
                edgeNum = p.edges[i]
                edge = model.edges!![abs(edgeNum)]
                //
                v1.set(model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p)
                v2.set(model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p)
                dir1.set(v2 - (v1))
                vec.set(v - (v1))
                dir2.set(dir1.Cross(p.plane.Normal()))
                if (vec.times(dir2) > VERTEX_EPSILON) {
                    return false
                }
                i++
            }
            return true
        }

        private fun FindInternalEdgesOnPolygon(model: cm_model_s, p1: cm_polygon_s, p2: cm_polygon_s) {
            var i: Int
            var j: Int
            var k: Int
            var edgeNum: Int
            var edge: cm_edge_s?
            val v1 = idVec3()
            val v2 = idVec3()
            val dir1 = idVec3()
            val dir2 = idVec3()
            var d: Float

            // bounds of polygons should overlap or touch
            i = 0
            while (i < 3) {
                if (p1.bounds[0][i] > p2.bounds[1][i]) {
                    return
                }
                if (p1.bounds[1][i] < p2.bounds[0][i]) {
                    return
                }
                i++
            }
            //
            // FIXME: doubled geometry causes problems
            //
            i = 0
            while (i < p1.numEdges) {
                edgeNum = p1.edges[i]
                edge = model.edges!![abs(edgeNum)]
                // if already an internal edge
                if (edge.internal) {
                    i++
                    continue
                }
                //
                v1.set(model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p)
                v2.set(model.vertices!![edge.vertexNum[Math_h.INTSIGNBITNOTSET(edgeNum)]].p)
                // if either of the two vertices is outside the bounds of the other polygon
                k = 0
                while (k < 3) {
                    d = p2.bounds[1][k] + VERTEX_EPSILON
                    if (v1[k] > d || v2[k] > d) {
                        break
                    }
                    d = p2.bounds[0][k] - VERTEX_EPSILON
                    if (v1[k] < d || v2[k] < d) {
                        break
                    }
                    k++
                }
                if (k < 3) {
                    i++
                    continue
                }
                //
                k = abs(edgeNum)
                j = 0
                while (j < p2.numEdges) {
                    if (k == abs(p2.edges[j])) {
                        break
                    }
                    j++
                }
                // if the edge is shared between the two polygons
                if (j < p2.numEdges) {
                    // if the edge is used by more than 2 polygons
                    if (edge.numUsers > 2) {
                        // could still be internal but we'd have to test all polygons using the edge
                        i++
                        continue
                    }
                    // if the edge goes in the same direction for both polygons
                    if (edgeNum == p2.edges[j]) {
                        // the polygons can lay ontop of each other or one can obscure the other
                        i++
                        continue
                    }
                } // the edge was not shared
                else {
                    // both vertices should be on the plane of the other polygon
                    d = p2.plane.Distance(v1)
                    if (abs(d) > VERTEX_EPSILON) {
                        i++
                        continue
                    }
                    d = p2.plane.Distance(v2)
                    if (abs(d) > VERTEX_EPSILON) {
                        i++
                        continue
                    }
                }
                // the two polygon plane normals should face towards each other
                dir1.set(v2 - (v1))
                dir2.set(p1.plane.Normal().Cross(dir1))
                if (p2.plane.Normal().times(dir2) < 0) {
                    //continue;
                    break
                }
                // if the edge was not shared
                if (j >= p2.numEdges) {
                    // both vertices of the edge should be inside the winding of the other polygon
                    if (!PointInsidePolygon(model, p2, v1)) {
                        i++
                        continue
                    }
                    if (!PointInsidePolygon(model, p2, v2)) {
                        i++
                        continue
                    }
                }
                // we got another internal edge
                edge.internal = true //true;
                model.numInternalEdges++
                i++
            }
        }

        private fun FindInternalPolygonEdges(model: cm_model_s, node: cm_node_s, polygon: cm_polygon_s) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            if (polygon.material!!.GetCullType() == cullType_t.CT_TWO_SIDED || polygon.material!!.ShouldCreateBackSides()) {
                return
            }
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    //
                    // FIXME: use some sort of additional checkcount because currently
                    //			polygons can be checked multiple times
                    //
                    // if the polygons don't have the same contents
                    if (p.contents != polygon.contents) {
                        pref = pref.next
                        continue
                    }
                    if (polygon == p) {
                        pref = pref.next
                        continue
                    }
                    FindInternalEdgesOnPolygon(model, polygon, p)
                    pref = pref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode = if (polygon.bounds[1][currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]!!
                } else if (polygon.bounds[1][currentNode.planeType] < currentNode.planeDist) {
                    currentNode.children[1]!!
                } else {
                    FindInternalPolygonEdges(model, currentNode.children[1]!!, polygon)
                    currentNode.children[0]!!
                }
            }
        }

        private fun FindInternalEdges(model: cm_model_s, node: cm_node_s) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        pref = pref.next
                        continue
                    }
                    p.checkcount = checkCount
                    FindInternalPolygonEdges(model, model.node!!, p)
                    pref = pref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                FindInternalEdges(model, currentNode.children[1]!!)
                currentNode = currentNode.children[0]!!
            }
        }

        /*
         ===============================================================================

         Proc BSP tree for data pruning

         ===============================================================================
         */
        // loading of proc BSP tree
        private fun ParseProcNodes(src: idLexer) {
            src.ExpectTokenString("{")
            numProcNodes = src.ParseInt()
            if (numProcNodes < 0) {
                src.Error("ParseProcNodes: bad numProcNodes")
            }
            procNodes =
                arrayOfNulls<cm_procNode_s?>(numProcNodes) //Mem_ClearedAlloc(numProcNodes /*sizeof( cm_procNode_t )*/);
            for (i in 0 until numProcNodes) {
                val node = cm_procNode_s()
                procNodes?.set(i, node)
                src.Parse1DMatrix(4, node.plane)
                node.children[0] = src.ParseInt()
                node.children[1] = src.ParseInt()
            }
            src.ExpectTokenString("}")
        }

        /*
         ================
         idCollisionModelManagerLocal::LoadProcBSP

         FIXME: if the nodes would be at the start of the .proc file it would speed things up considerably
         ================
         */
        private fun LoadProcBSP(name: String) {
            val filename: idStr
            val token = idToken()
            val src: idLexer?

            // load it
            filename = idStr(name)
            filename.SetFileExtension(RenderWorld.PROC_FILE_EXT)
            src = idLexer(name, Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NODOLLARPRECOMPILE)
            if (!src.IsLoaded()) {
                Common.common.Warning(
                    "idCollisionModelManagerLocal::LoadProcBSP: couldn't load %s",
                    filename.toString()
                )
                return
            }
            if (!src.ReadToken(token) || token.Icmp(RenderWorld.PROC_FILE_ID) != 0) {
                Common.common.Warning(
                    "idCollisionModelManagerLocal::LoadProcBSP: bad id '%s' instead of '%s'",
                    token.toString(),
                    RenderWorld.PROC_FILE_ID
                )
                return
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "model") {
                    src.SkipBracedSection()
                    continue
                }
                if (token.toString() == "shadowModel") {
                    src.SkipBracedSection()
                    continue
                }
                if (token.toString() == "interAreaPortals") {
                    src.SkipBracedSection()
                    continue
                }
                if (token.toString() == "nodes") {
                    ParseProcNodes(src)
                    break
                }
                src.Error("idCollisionModelManagerLocal::LoadProcBSP: bad token \"%s\"", token.toString())
            }

//	delete src;
        }

        /*
         ===============================================================================

         Optimisation, removal of polygons contained within brushes or solid

         ===============================================================================
         */
        // removal of contained polygons
        private fun R_ChoppedAwayByProcBSP(
            nodeNum: Int,
            w: idFixedWinding?,
            normal: idVec3,
            origin: idVec3,
            radius: Float
        ): Boolean {
            var curretnNodenUm = nodeNum
            var res: Int
            val back = idFixedWinding()
            var node: cm_procNode_s
            var dist: Float
            do {
                node = procNodes?.get(curretnNodenUm)!!
                dist = node.plane.Normal().times(origin) + node.plane[3]
                res = if (dist > radius) {
                    Plane.SIDE_FRONT
                } else if (dist < -radius) {
                    Plane.SIDE_BACK
                } else {
                    w!!.Split(back, node.plane, CHOP_EPSILON)
                }
                curretnNodenUm = if (res == Plane.SIDE_FRONT) {
                    node.children[0]
                } else if (res == Plane.SIDE_BACK) {
                    node.children[1]
                } else if (res == Plane.SIDE_ON) {
                    // continue with the side the winding faces
                    if (node.plane.Normal().times(normal) > 0.0f) {
                        node.children[0]
                    } else {
                        node.children[1]
                    }
                } else {
                    // if either node is not solid
                    if (node.children[0] < 0 || node.children[1] < 0) {
                        return false
                    }
                    // only recurse if the node is not solid
                    if (node.children[1] > 0) {
                        if (!R_ChoppedAwayByProcBSP(node.children[1], back, normal, origin, radius)) {
                            return false
                        }
                    }
                    node.children[0]
                }
            } while (curretnNodenUm > 0)
            return curretnNodenUm >= 0
        }

        private fun ChoppedAwayByProcBSP(w: idFixedWinding, plane: idPlane, contents: Int): Boolean {
            val neww: idFixedWinding
            val bounds = idBounds()
            val radius: Float
            val origin = idVec3()

            // if the .proc file has no BSP tree
            if (procNodes == null) {
                return false
            }
            // don't chop if the polygon is not solid
            if (0 == contents and Material.CONTENTS_SOLID) {
                return false
            }
            // make a local copy of the winding
            neww = idFixedWinding(w)
            neww.GetBounds(bounds)
            origin.set(bounds[1] - (bounds[0]) * (0.5f))
            radius = origin.Length() + CHOP_EPSILON
            origin.set(bounds[0] + (origin))
            //
            return R_ChoppedAwayByProcBSP(0, neww, plane.Normal(), origin, radius)
        }

        /*
         =============
         idCollisionModelManagerLocal::ChopWindingWithBrush

         returns the least number of winding fragments outside the brush
         =============
         */
        private fun ChopWindingListWithBrush(list: cm_windingList_s, b: cm_brush_s) {
            var i: Int
            var k: Int
            var res: Int
            var startPlane: Int
            var planeNum: Int
            var bestNumWindings: Int
            val back = idFixedWinding()
            var front: idFixedWinding?
            val plane = idPlane()
            var chopped: Boolean
            val sidedness = IntArray(Winding.MAX_POINTS_ON_WINDING)
            var dist: Float
            if (b.numPlanes > Winding.MAX_POINTS_ON_WINDING) {
                return
            }

            // get sidedness for the list of windings
            i = 0
            while (i < b.numPlanes) {
                plane.set(b.planes[i].unaryMinus())
                dist = plane.Distance(list.origin)
                if (dist > list.radius) {
                    sidedness[i] = Plane.SIDE_FRONT
                } else if (dist < -list.radius) {
                    sidedness[i] = Plane.SIDE_BACK
                } else {
                    sidedness[i] = list.bounds.PlaneSide(plane)
                    if (sidedness[i] == Plane.PLANESIDE_FRONT) {
                        sidedness[i] = Plane.SIDE_FRONT
                    } else if (sidedness[i] == Plane.PLANESIDE_BACK) {
                        sidedness[i] = Plane.SIDE_BACK
                    } else {
                        sidedness[i] = Plane.SIDE_CROSS
                    }
                }
                i++
            }
            cm_outList!!.numWindings = 0
            k = 0
            while (k < list.numWindings) {

                //
                startPlane = 0
                bestNumWindings = 1 + b.numPlanes
                chopped = false
                do {
                    front = list.w[k]
                    cm_tmpList!!.numWindings = 0
                    planeNum = startPlane
                    i = 0
                    while (i < b.numPlanes) {
                        if (planeNum >= b.numPlanes) {
                            planeNum = 0
                        }
                        res = sidedness[planeNum]
                        if (res == Plane.SIDE_CROSS) {
                            plane.set(b.planes[planeNum].unaryMinus())
                            res = front!!.Split(back, plane, CHOP_EPSILON)
                        }

                        // NOTE:	disabling this can create gaps at places where Z-fighting occurs
                        //			Z-fighting should not occur but what if there is a decal brush side
                        //			with exactly the same size as another brush side ?
                        // only leave windings on a brush if the winding plane and brush side plane face the same direction
                        if (res == Plane.SIDE_ON && list.primitiveNum >= 0 && list.normal.times(b.planes[planeNum].Normal()) > 0) {
                            // return because all windings in the list will be on this brush side plane
                            return
                        }
                        if (res == Plane.SIDE_BACK) {
                            if (cm_outList!!.numWindings >= MAX_WINDING_LIST) {
                                Common.common.Warning(
                                    "idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings",
                                    list.primitiveNum,
                                    MAX_WINDING_LIST
                                )
                                return
                            }
                            // winding and brush didn't intersect, store the original winding
                            cm_outList!!.w[cm_outList!!.numWindings] = list.w[k]
                            cm_outList!!.numWindings++
                            chopped = false
                            break
                        }
                        if (res == Plane.SIDE_CROSS) {
                            if (cm_tmpList!!.numWindings >= MAX_WINDING_LIST) {
                                Common.common.Warning(
                                    "idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings",
                                    list.primitiveNum,
                                    MAX_WINDING_LIST
                                )
                                return
                            }
                            // store the front winding in the temporary list
                            cm_tmpList!!.w[cm_tmpList!!.numWindings] = back
                            cm_tmpList!!.numWindings++
                            chopped = true
                        }

                        // if already found a start plane which generates less fragments
                        if (cm_tmpList!!.numWindings >= bestNumWindings) {
                            break
                        }
                        i++
                        planeNum++
                    }

                    // find the best start plane to get the least number of fragments outside the brush
                    if (cm_tmpList!!.numWindings < bestNumWindings) {
                        bestNumWindings = cm_tmpList!!.numWindings
                        // store windings from temporary list in the out list
                        i = 0
                        while (i < cm_tmpList!!.numWindings) {
                            if (cm_outList!!.numWindings + i >= MAX_WINDING_LIST) {
                                Common.common.Warning(
                                    "idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings",
                                    list.primitiveNum,
                                    MAX_WINDING_LIST
                                )
                                return
                            }
                            cm_outList!!.w[cm_outList!!.numWindings + i] = cm_tmpList!!.w[i]
                            i++
                        }
                        // if only one winding left then we can't do any better
                        if (bestNumWindings == 1) {
                            break
                        }
                    }

                    // try the next start plane
                    startPlane++
                } while (chopped && startPlane < b.numPlanes)
                //
                if (chopped) {
                    cm_outList!!.numWindings += bestNumWindings
                }
                k++
            }
            k = 0
            while (k < cm_outList!!.numWindings) {
                list.w[k] = cm_outList!!.w[k]
                k++
            }
            list.numWindings = cm_outList!!.numWindings
        }

        private fun R_ChopWindingListWithTreeBrushes(list: cm_windingList_s, node: cm_node_s) {
            var currentNode = node
            var i: Int
            var bref: cm_brushRef_s?
            var b: cm_brush_s
            while (true) {
                bref = currentNode.brushes
                while (bref != null) {
                    b = bref.b!!
                    // if we checked this brush already
                    if (b.checkcount == checkCount) {
                        bref = bref.next
                        continue
                    }
                    b.checkcount = checkCount
                    // if the windings in the list originate from this brush
                    if (b.primitiveNum == list.primitiveNum) {
                        bref = bref.next
                        continue
                    }
                    // if brush has a different contents
                    if (b.contents != list.contents) {
                        bref = bref.next
                        continue
                    }
                    // brush bounds and winding list bounds should overlap
                    i = 0
                    while (i < 3) {
                        if (list.bounds[0][i] > b.bounds[1][i]) {
                            break
                        }
                        if (list.bounds[1][i] < b.bounds[0][i]) {
                            break
                        }
                        i++
                    }
                    if (i < 3) {
                        bref = bref.next
                        continue
                    }
                    // chop windings in the list with brush
                    ChopWindingListWithBrush(list, b)
                    // if all windings are chopped away we're done
                    if (0 == list.numWindings) {
                        return
                    }
                    bref = bref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode = if (list.bounds[0][currentNode.planeType] > currentNode.planeDist) {
                    currentNode.children[0]!!
                } else if (list.bounds[1][currentNode.planeType] < currentNode.planeDist) {
                    currentNode.children[1]!!
                } else {
                    R_ChopWindingListWithTreeBrushes(list, currentNode.children[1]!!)
                    if (0 == list.numWindings) {
                        return
                    }
                    currentNode.children[0]!!
                }
            }
        }

        /*
         ============
         idCollisionModelManagerLocal::WindingOutsideBrushes

         Returns one winding which is not fully contained in brushes.
         We always favor less polygons over a stitched world.
         If the winding is partly contained and the contained pieces can be chopped off
         without creating multiple winding fragments then the chopped winding is returned.
         ============
         */
        private fun WindingOutsideBrushes(
            w: idFixedWinding,
            plane: idPlane,
            contents: Int,
            patch: Int,
            headNode: cm_node_s
        ): idFixedWinding? {
            var i: Int
            var windingLeft: Int
            cm_windingList!!.bounds.Clear()
            i = 0
            while (i < w.GetNumPoints()) {
                cm_windingList!!.bounds.AddPoint(w[i].ToVec3())
                i++
            }
            cm_windingList!!.origin.set(
                cm_windingList!!.bounds[1] - (cm_windingList!!.bounds[0]) * (0.5f)
            )
            cm_windingList!!.radius =
                cm_windingList!!.origin.Length() + CHOP_EPSILON
            cm_windingList!!.origin.set(cm_windingList!!.bounds[0] + (cm_windingList!!.origin))
            cm_windingList!!.bounds[0].minusAssign(
                idVec3(
                    CHOP_EPSILON,
                    CHOP_EPSILON,
                    CHOP_EPSILON
                )
            )
            cm_windingList!!.bounds[1].plusAssign(
                idVec3(
                    CHOP_EPSILON,
                    CHOP_EPSILON,
                    CHOP_EPSILON
                )
            )
            cm_windingList!!.w[0] = idFixedWinding(w)
            cm_windingList!!.numWindings = 1
            cm_windingList!!.normal.set(plane.Normal())
            cm_windingList!!.contents = contents
            cm_windingList!!.primitiveNum = patch
            //
            checkCount++
            R_ChopWindingListWithTreeBrushes(cm_windingList!!, headNode)
            //
            if (0 == cm_windingList!!.numWindings) {
                return null
            }
            if (cm_windingList!!.numWindings == 1) {
                return cm_windingList!!.w[0]
            }
            // if not the world model
            if (numModels != 0) {
                return w
            }
            // check if winding fragments would be chopped away by the proc BSP tree
            windingLeft = -1
            i = 0
            while (i < cm_windingList!!.numWindings) {
                if (!ChoppedAwayByProcBSP(cm_windingList!!.w[i]!!, plane, contents)) {
                    if (windingLeft >= 0) {
                        return w
                    }
                    windingLeft = i
                }
                i++
            }
            return if (windingLeft >= 0) {
                cm_windingList!!.w[windingLeft]
            } else null
        }

        /*
         ===============================================================================

         Trace model to general collision model

         ===============================================================================
         */
        // creation of axial BSP tree
        private fun AllocModel(): cm_model_s {
            val model: cm_model_s
            model = cm_model_s()
            model.contents = 0
            model.isConvex = false
            model.maxVertices = 0
            model.numVertices = 0
            model.vertices = null
            model.maxEdges = 0
            model.numEdges = 0
            model.edges = null
            model.node = null
            model.nodeBlocks = null
            model.polygonRefBlocks = null
            model.brushRefBlocks = null
            model.polygonBlock = null
            model.brushBlock = null
            model.usedMemory = 0
            model.numMergedPolys = model.usedMemory
            model.numRemovedPolys = model.numMergedPolys
            model.numSharpEdges = model.numRemovedPolys
            model.numInternalEdges = model.numSharpEdges
            model.numPolygonRefs = model.numInternalEdges
            model.numBrushRefs = model.numPolygonRefs
            model.numNodes = model.numBrushRefs
            model.brushMemory = model.numNodes
            model.numBrushes = model.brushMemory
            model.polygonMemory = model.numBrushes
            model.numPolygons = model.polygonMemory
            return model
        }

        private fun AllocNode(model: cm_model_s, blockSize: Int): cm_node_s {
            var i: Int
            var node: cm_node_s
            val nodeBlock: cm_nodeBlock_s
            if (null == model.nodeBlocks || null == model.nodeBlocks!!.nextNode) {
                nodeBlock = cm_nodeBlock_s()
                nodeBlock.nextNode = cm_node_s()
                nodeBlock.next = model.nodeBlocks
                model.nodeBlocks = nodeBlock
                node = nodeBlock.nextNode!!
                i = 0
                while (i < blockSize - 1) {
                    node.parent = cm_node_s()
                    node = node.parent!!
                    i++
                }
                node.parent = null
            }
            node = model.nodeBlocks!!.nextNode!!
            model.nodeBlocks?.nextNode = node.parent
            node.parent = null
            return node
        }

        private fun AllocPolygonReference(model: cm_model_s, blockSize: Int): cm_polygonRef_s {
            var i: Int
            var pref: cm_polygonRef_s
            val prefBlock: cm_polygonRefBlock_s
            if (null == model.polygonRefBlocks || null == model.polygonRefBlocks!!.nextRef) {
                prefBlock = cm_polygonRefBlock_s()
                prefBlock.nextRef = cm_polygonRef_s()
                prefBlock.next = model.polygonRefBlocks
                model.polygonRefBlocks = prefBlock
                pref = prefBlock.nextRef!!
                i = 0
                while (i < blockSize - 1) {
                    pref.next = cm_polygonRef_s()
                    pref = pref.next!!
                    i++
                }
                pref.next = null
            }
            pref = model.polygonRefBlocks!!.nextRef!!
            model.polygonRefBlocks!!.nextRef = pref.next
            return pref
        }

        private fun AllocBrushReference(model: cm_model_s, blockSize: Int): cm_brushRef_s {
            var i: Int
            var bref: cm_brushRef_s
            val brefBlock: cm_brushRefBlock_s
            if (null == model.brushRefBlocks || null == model.brushRefBlocks!!.nextRef) {
                brefBlock = cm_brushRefBlock_s()
                brefBlock.nextRef = cm_brushRef_s()
                brefBlock.next = model.brushRefBlocks
                model.brushRefBlocks = brefBlock
                bref = brefBlock.nextRef!!
                i = 0
                while (i < blockSize - 1) {
                    bref.next = cm_brushRef_s()
                    bref = bref.next!!
                    i++
                }
                bref.next = null
            }
            bref = model.brushRefBlocks!!.nextRef!!
            model.brushRefBlocks!!.nextRef = bref.next
            return bref
        }

        private fun AllocPolygon(model: cm_model_s, numEdges: Int): cm_polygon_s {
            val poly: cm_polygon_s
            val size: Int
            size =
                cm_polygon_s.BYTES + (numEdges - 1) * Integer.SIZE //sizeof( cm_polygon_t ) + ( numEdges - 1 ) * sizeof( poly.edges[0] );
            model.numPolygons++
            model.polygonMemory += size
            poly = cm_polygon_s() // Mem_Alloc(size);
            poly.edges = IntArray(numEdges)
            return poly
        }

        private fun AllocBrush(model: cm_model_s, numPlanes: Int): cm_brush_s {
            val brush: cm_brush_s
            val size: Int
            size =
                cm_brush_s.BYTES + (numPlanes - 1) * Integer.SIZE //sizeof( cm_brush_t ) + ( numPlanes - 1 ) * sizeof( brush.planes[0] );
            model.numBrushes++
            model.brushMemory += size
            brush = cm_brush_s() // Mem_Alloc(size);
            brush.planes = idPlane.generateArray(numPlanes)
            return brush
        }

        private fun AddPolygonToNode(model: cm_model_s, node: cm_node_s, p: cm_polygon_s) {
            val pref: cm_polygonRef_s
            pref = AllocPolygonReference(
                model,
                if (model.numPolygonRefs < REFERENCE_BLOCK_SIZE_SMALL) REFERENCE_BLOCK_SIZE_SMALL else REFERENCE_BLOCK_SIZE_LARGE
            )
            pref.p = p
            pref.next = node.polygons
            node.polygons = pref
            model.numPolygonRefs++
        }

        private fun AddBrushToNode(model: cm_model_s, node: cm_node_s, b: cm_brush_s) {
            val bref: cm_brushRef_s?
            bref = AllocBrushReference(
                model,
                if (model.numBrushRefs < REFERENCE_BLOCK_SIZE_SMALL) REFERENCE_BLOCK_SIZE_SMALL else REFERENCE_BLOCK_SIZE_LARGE
            )
            bref.b = b
            bref.next = node.brushes
            node.brushes = bref
            model.numBrushRefs++
        }

        private fun SetupTrmModelStructure() {
            var i: Int
            val node: cm_node_s?
            val model: cm_model_s

            // setup model
            model = AllocModel()
            assert(models != null)
            models?.set(MAX_SUBMODELS, model)
            // create node to hold the collision data
            node = AllocNode(model, 1)
            node.planeType = -1
            model.node = node
            // allocate vertex and edge arrays
            model.numVertices = 0
            model.maxVertices = TraceModel.MAX_TRACEMODEL_VERTS
            model.vertices = cm_vertex_s.generateArray(model.maxVertices)
            model.numEdges = 0
            model.maxEdges = TraceModel.MAX_TRACEMODEL_EDGES + 1
            model.edges = cm_edge_s.generateArray(model.maxEdges)
            // create a material for the trace model polygons
            trmMaterial = DeclManager.declManager!!.FindMaterial("_tracemodel", false)
            if (null == trmMaterial) {
                Common.common.FatalError("_tracemodel material not found")
            }

            // allocate polygons
            i = 0
            while (i < TraceModel.MAX_TRACEMODEL_POLYS) {
                trmPolygons[i] = AllocPolygonReference(model, TraceModel.MAX_TRACEMODEL_POLYS)
                trmPolygons[i]!!.p = AllocPolygon(model, TraceModel.MAX_TRACEMODEL_POLYEDGES)
                trmPolygons[i]!!.p!!.bounds.Clear()
                trmPolygons[i]!!.p!!.plane.Zero()
                trmPolygons[i]!!.p!!.checkcount = 0
                trmPolygons[i]!!.p!!.contents = -1 // all contents
                trmPolygons[i]!!.p!!.material = trmMaterial
                trmPolygons[i]!!.p!!.numEdges = 0
                i++
            }
            // allocate brush for position test
            trmBrushes[0] = AllocBrushReference(model, 1)
            trmBrushes[0]!!.b = AllocBrush(model, TraceModel.MAX_TRACEMODEL_POLYS)
            trmBrushes[0]!!.b!!.primitiveNum = 0
            trmBrushes[0]!!.b!!.bounds.Clear()
            trmBrushes[0]!!.b!!.checkcount = 0
            trmBrushes[0]!!.b!!.contents = -1 // all contents
            trmBrushes[0]!!.b!!.numPlanes = 0
        }

        private fun R_FilterPolygonIntoTree(
            model: cm_model_s,
            node: cm_node_s,
            pref: cm_polygonRef_s?,
            p: cm_polygon_s
        ) {
            var currentNode = node
            while (currentNode.planeType != -1) {
                if (CollisionModel_load.CM_R_InsideAllChildren(currentNode, p.bounds)) {
                    break
                }
                currentNode = if (p.bounds[0][currentNode.planeType] >= currentNode.planeDist) {
                    currentNode.children[0]!!
                } else if (p.bounds[1][currentNode.planeType] <= currentNode.planeDist) {
                    currentNode.children[1]!!
                } else {
                    R_FilterPolygonIntoTree(model, currentNode.children[1]!!, null, p)
                    currentNode.children[0]!!
                }
            }
            if (pref != null) {
                pref.next = currentNode.polygons
                currentNode.polygons = pref
            } else {
                AddPolygonToNode(model, currentNode, p)
            }
        }

        private fun R_FilterBrushIntoTree(model: cm_model_s, node: cm_node_s, pref: cm_brushRef_s?, b: cm_brush_s) {
            var currentNode = node
            while (currentNode.planeType != -1) {
                if (CollisionModel_load.CM_R_InsideAllChildren(currentNode, b.bounds)) {
                    break
                }
                currentNode = if (b.bounds[0][currentNode.planeType] >= currentNode.planeDist) {
                    currentNode.children[0]!!
                } else if (b.bounds[1][currentNode.planeType] <= currentNode.planeDist) {
                    currentNode.children[1]!!
                } else {
                    R_FilterBrushIntoTree(model, currentNode.children[1]!!, null, b)
                    currentNode.children[0]!!
                }
            }
            if (pref != null) {
                pref.next = currentNode.brushes
                currentNode.brushes = pref
            } else {
                AddBrushToNode(model, currentNode, b)
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::R_CreateAxialBSPTree

         a brush or polygon is linked in the node closest to the root where
         the brush or polygon is inside all children
         ================
         */
        private fun R_CreateAxialBSPTree(model: cm_model_s, node: cm_node_s, bounds: idBounds): cm_node_s {
            val planeType = CInt()
            val planeDist = CFloat()
            var pref: cm_polygonRef_s?
            var nextpref: cm_polygonRef_s?
            var prevpref: cm_polygonRef_s?
            var bref: cm_brushRef_s?
            var nextbref: cm_brushRef_s?
            var prevbref: cm_brushRef_s?
            val frontNode: cm_node_s?
            val backNode: cm_node_s?
            var n: cm_node_s?
            val frontBounds: idBounds
            val backBounds: idBounds
            if (!CollisionModel_load.CM_FindSplitter(node, bounds, planeType, planeDist)) {
                node.planeType = -1
                return node
            }
            // create two child nodes
            frontNode = AllocNode(
                model,
                NODE_BLOCK_SIZE_LARGE
            ) //	memset( frontNode, 0, sizeof(cm_node_t) );
            frontNode.parent = node
            frontNode.planeType = -1
            //
            backNode = AllocNode(
                model,
                NODE_BLOCK_SIZE_LARGE
            ) //	memset( backNode, 0, sizeof(cm_node_t) );
            backNode.parent = node
            backNode.planeType = -1
            //
            model.numNodes += 2
            // set front node bounds
            frontBounds = idBounds(bounds)
            frontBounds[0][planeType._val] = planeDist._val
            // set back node bounds
            backBounds = idBounds(bounds)
            backBounds[1][planeType._val] = planeDist._val
            //
            node.planeType = planeType._val
            node.planeDist = planeDist._val
            node.children[0] = frontNode
            node.children[1] = backNode
            // filter polygons and brushes down the tree if necesary
            n = node
            while (n != null) {
                prevpref = null
                pref = n.polygons
                while (pref != null) {
                    nextpref = pref.next
                    // if polygon is not inside all children
                    if (!CollisionModel_load.CM_R_InsideAllChildren(n, pref.p!!.bounds)) {
                        // filter polygon down the tree
                        R_FilterPolygonIntoTree(model, n, pref, pref.p!!)
                        if (prevpref != null) {
                            prevpref.next = nextpref
                        } else {
                            n.polygons = nextpref
                        }
                    } else {
                        prevpref = pref
                    }
                    pref = nextpref
                }
                prevbref = null
                bref = n.brushes
                while (bref != null) {
                    nextbref = bref.next
                    // if brush is not inside all children
                    if (!CollisionModel_load.CM_R_InsideAllChildren(n, bref.b!!.bounds)) {
                        // filter brush down the tree
                        R_FilterBrushIntoTree(model, n, bref, bref.b!!)
                        if (prevbref != null) {
                            prevbref.next = nextbref
                        } else {
                            n.brushes = nextbref
                        }
                    } else {
                        prevbref = bref
                    }
                    bref = nextbref
                }
                n = n.parent
            }
            R_CreateAxialBSPTree(model, frontNode, frontBounds)
            R_CreateAxialBSPTree(model, backNode, backBounds)
            return node
        }

        private fun CreateAxialBSPTree(model: cm_model_s, node: cm_node_s): cm_node_s {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var bref: cm_brushRef_s?
            val bounds = idBounds()

            // get head node bounds
            bounds.Clear()
            pref = currentNode.polygons
            while (pref != null) {
                bounds.timesAssign(pref.p!!.bounds)
                pref = pref.next
            }
            bref = currentNode.brushes
            while (bref != null) {
                bounds.timesAssign(bref.b!!.bounds)
                bref = bref.next
            }

            // create axial BSP tree from head node
            currentNode = R_CreateAxialBSPTree(model, currentNode, bounds)
            return currentNode
        }

        /*
         ===============================================================================

         Raw polygon and brush data

         ===============================================================================
         */
        // creation of raw polygons
        private fun SetupHash() {
            if (null == cm_vertexHash) {
                cm_vertexHash = idHashIndex(VERTEX_HASH_SIZE, 1024)
            }
            if (null == cm_edgeHash) {
                cm_edgeHash = idHashIndex(EDGE_HASH_SIZE, 1024)
            }
            // init variables used during loading and optimization
            if (null == cm_windingList) {
                cm_windingList = cm_windingList_s()
            }
            if (null == cm_outList) {
                cm_outList = cm_windingList_s()
            }
            if (null == cm_tmpList) {
                cm_tmpList = cm_windingList_s()
            }
        }

        private fun ShutdownHash() {
            cm_vertexHash = null
            cm_edgeHash = null
            cm_tmpList = null
            cm_outList = null
            cm_windingList = null
        }

        private fun ClearHash(bounds: idBounds) {
            var i: Int
            val f: Float
            var max: Float
            cm_vertexHash!!.Clear()
            cm_edgeHash!!.Clear()
            cm_modelBounds.set(bounds)
            max = bounds[1].x - bounds[0].x
            f = bounds[1].y - bounds[0].y
            if (f > max) {
                max = f
            }
            cm_vertexShift = (max / VERTEX_HASH_BOXSIZE).toInt()
            i = 0
            while (1 shl i < cm_vertexShift) {
                i++
            }
            if (i == 0) {
                cm_vertexShift = 1
            } else {
                cm_vertexShift = i
            }
        }

        private fun HashVec(vec: idVec3): Int {
            /*
             int x, y;

             x = (((int)(vec[0] - cm_modelBounds[0].x + 0.5 )) >> cm_vertexShift) & (VERTEX_HASH_BOXSIZE-1);
             y = (((int)(vec[1] - cm_modelBounds[0].y + 0.5 )) >> cm_vertexShift) & (VERTEX_HASH_BOXSIZE-1);

             assert (x >= 0 && x < VERTEX_HASH_BOXSIZE && y >= 0 && y < VERTEX_HASH_BOXSIZE);

             return y * VERTEX_HASH_BOXSIZE + x;
             */
            val x: Int = (vec[0] - cm_modelBounds[0].x + 0.5).toInt() + 2 shr 2
            val y: Int = (vec[1] - cm_modelBounds[0].y + 0.5).toInt() + 2 shr 2
            val z: Int = (vec[2] - cm_modelBounds[0].z + 0.5).toInt() + 2 shr 2
            return x + y * VERTEX_HASH_BOXSIZE + z and VERTEX_HASH_SIZE - 1
        }

        private fun GetVertex(model: cm_model_s, v: idVec3, vertexNum: CInt): Boolean {
            var i: Int
            val hashKey: Int
            var vn: Int
            val vert = idVec3()
            val p = idVec3()
            i = 0
            while (i < 3) {
                if (abs(v[i] - idMath.Rint(v[i])) < INTEGRAL_EPSILON) {
                    vert[i] = idMath.Rint(v[i])
                } else {
                    vert[i] = v[i]
                }
                i++
            }
            hashKey = HashVec(vert)
            vn = cm_vertexHash!!.First(hashKey)
            while (vn >= 0) {
                p.set(model.vertices!![vn].p)
                // first compare z-axis because hash is based on x-y plane
                if (abs(vert[2] - p[2]) < VERTEX_EPSILON && abs(
                        vert[0] - p[0]
                    ) < VERTEX_EPSILON && abs(vert[1] - p[1]) < VERTEX_EPSILON
                ) {
                    vertexNum._val = (vn)
                    return true
                }
                vn = cm_vertexHash!!.Next(vn)
            }
            if (model.numVertices >= model.maxVertices) {

                // resize vertex array
                model.maxVertices = (model.maxVertices * 1.5f + 1).toInt()
                val oldVertices: Array<cm_vertex_s> = model.vertices!!
                // TODO: Check source code
                model.vertices = cm_vertex_s.generateArray(model.maxVertices)
                System.arraycopy(oldVertices, 0, model.vertices, 0, model.numVertices)
                cm_vertexHash!!.ResizeIndex(model.maxVertices)
            }
            model.vertices!![model.numVertices].p.set(vert)
            model.vertices!![model.numVertices].checkcount = 0
            vertexNum._val = (model.numVertices)
            // add vertice to hash
            cm_vertexHash!!.Add(hashKey, model.numVertices)
            //
            model.numVertices++
            return false
        }

        private fun GetEdge(model: cm_model_s, v1: idVec3, v2: idVec3, edgeNum: IntArray, v1num: CInt): Boolean {
            return GetEdge(model, v1, v2, edgeNum, 0, v1num)
        }

        private fun GetEdge(
            model: cm_model_s,
            v1: idVec3,
            v2: idVec3,
            edgeNum: IntArray,
            edgeOffset: Int,
            v1num: CInt
        ): Boolean {
            val hashKey: Int
            var e: Int
            var found: Boolean
            var vertexNum: IntArray
            val v2num = CInt()

            // the first edge is a dummy
            if (model.numEdges == 0) {
                model.numEdges = 1
            }
            found = if (v1num._val != -1) {
                true
            } else {
                GetVertex(model, v1, v1num)
            }
            found = found and GetVertex(model, v2, v2num)
            // if both vertices are the same or snapped onto each other
            if (v1num._val == v2num._val) {
                edgeNum[edgeOffset] = 0
                return true
            }
            hashKey = cm_edgeHash!!.GenerateKey(v1num._val, v2num._val)
            // if both vertices where already stored
            if (found) {
                e = cm_edgeHash!!.First(hashKey)
                while (e >= 0) {

                    // NOTE: only allow at most two users that use the edge in opposite direction
                    if (model.edges!![e].numUsers.toInt() != 1) {
                        e = cm_edgeHash!!.Next(e)
                        continue
                    }
                    vertexNum = model.edges!![e].vertexNum
                    if (vertexNum[0] == v2num._val) {
                        if (vertexNum[1] == v1num._val) {
                            // negative for a reversed edge
                            edgeNum[edgeOffset] = -e
                            break
                        }
                    }
                    e = cm_edgeHash!!.Next(e)
                }
                // if edge found in hash
                if (e >= 0) {
                    model.edges!![e].numUsers++
                    return true
                }
            }
            if (model.numEdges >= model.maxEdges) {
                val oldEdges: Array<cm_edge_s>
                // resize edge array
                model.maxEdges = (model.maxEdges * 1.5f + 1).toInt()
                oldEdges = model.edges!!
                model.edges = cm_edge_s.generateArray(model.maxEdges)
                //System.arraycopy(oldEdges, 0, model.edges, 0, model.numEdges);
                // TODO: check Source code
                if (model.numEdges >= 0) {
                    System.arraycopy(oldEdges, 0, model.edges, 0, model.numEdges)
                }
                cm_edgeHash!!.ResizeIndex(model.maxEdges)
            }
            // setup edge
            model.edges!![model.numEdges].vertexNum[0] = v1num._val
            model.edges!![model.numEdges].vertexNum[1] = v2num._val
            model.edges!![model.numEdges].internal = false
            model.edges!![model.numEdges].checkcount = 0
            model.edges!![model.numEdges].numUsers = 1 // used by one polygon atm
            model.edges!![model.numEdges].normal.Zero()
            //
            edgeNum[edgeOffset] = model.numEdges
            // add edge to hash
            cm_edgeHash!!.Add(hashKey, model.numEdges)
            model.numEdges++
            return false
        }

        private fun CreatePolygon(
            model: cm_model_s,
            w: idFixedWinding,
            plane: idPlane,
            material: idMaterial,
        ) {
            var i: Int
            var j: Int
            var edgeNum: Int
            val v1num = CInt()
            var numPolyEdges: Int
            val polyEdges = IntArray(Winding.MAX_POINTS_ON_WINDING)
            val bounds = idBounds()
            val p: cm_polygon_s?

            // turn the winding into a sequence of edges
            numPolyEdges = 0
            v1num._val = (-1) // first vertex unknown
            i = 0
            j = 1
            while (i < w.GetNumPoints()) {
                if (j >= w.GetNumPoints()) {
                    j = 0
                }
                GetEdge(model, w[i].ToVec3(), w[j].ToVec3(), polyEdges, numPolyEdges, v1num)
                if (polyEdges[numPolyEdges] != 0) {
                    // last vertex of this edge is the first vertex of the next edge
                    v1num._val = (
                            model.edges!![abs(polyEdges[numPolyEdges])].vertexNum[Math_h.INTSIGNBITNOTSET(
                                polyEdges[numPolyEdges]
                            )]
                            )
                    // this edge is valid so keep it
                    numPolyEdges++
                }
                i++
                j++
            }
            // should have at least 3 edges
            if (numPolyEdges < 3) {
                return
            }
            // the polygon is invalid if some edge is found twice
            i = 0
            while (i < numPolyEdges) {
                j = i + 1
                while (j < numPolyEdges) {
                    if (abs(polyEdges[i]) == abs(polyEdges[j])) {
                        return
                    }
                    j++
                }
                i++
            }
            // don't overflow max edges
            if (numPolyEdges > CM_MAX_POLYGON_EDGES) {
                Common.common.Warning(
                    "idCollisionModelManagerLocal::CreatePolygon: polygon has more than %d edges",
                    numPolyEdges
                )
                numPolyEdges = CM_MAX_POLYGON_EDGES
            }
            w.GetBounds(bounds)
            p = AllocPolygon(model, numPolyEdges)
            p.numEdges = numPolyEdges
            p.contents = material.GetContentFlags()
            p.material = material
            p.checkcount = 0
            p.plane.set(plane)
            p.bounds.set(bounds)
            i = 0
            while (i < numPolyEdges) {
                edgeNum = polyEdges[i]
                p.edges[i] = edgeNum
                i++
            }
            R_FilterPolygonIntoTree(model, model.node!!, null, p)
        }

        /*
         ================
         idCollisionModelManagerLocal::PolygonFromWinding

         NOTE: for patches primitiveNum < 0 and abs(primitiveNum) is the real number
         ================
         */
        private fun PolygonFromWinding(
            model: cm_model_s,
            w: idFixedWinding?,
            plane: idPlane,
            material: idMaterial,
            primitiveNum: Int
        ) {
            var currentW = w
            val contents: Int
            contents = material.GetContentFlags()

            // if this polygon is part of the world model
            if (numModels == 0) {
                // if the polygon is fully chopped away by the proc bsp tree
                if (ChoppedAwayByProcBSP(currentW!!, plane, contents)) {
                    model.numRemovedPolys++
                    return
                }
            }

            // get one winding that is not or only partly contained in brushes
            currentW = WindingOutsideBrushes(currentW!!, plane, contents, primitiveNum, model.node!!)

            // if the polygon is fully contained within a brush
            if (null == currentW) {
                model.numRemovedPolys++
                return
            }
            if (currentW.IsHuge()) {
                Common.common.Warning(
                    "idCollisionModelManagerLocal::PolygonFromWinding: model %s primitive %d is degenerate",
                    model.name,
                    abs(primitiveNum)
                )
                return
            }
            CreatePolygon(model, currentW, plane, material)
            if (material.GetCullType() == cullType_t.CT_TWO_SIDED || material.ShouldCreateBackSides()) {
                currentW.ReverseSelf()
                CreatePolygon(model, currentW, plane.unaryMinus(), material)
            }
        }

        private fun CalculateEdgeNormals(model: cm_model_s, node: cm_node_s) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var edge: cm_edge_s?
            var dot: Float
            var s: Float
            var i: Int
            var edgeNum: Int
            val dir = idVec3()
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        pref = pref.next
                        continue
                    }
                    p.checkcount = checkCount
                    i = 0
                    while (i < p.numEdges) {
                        edgeNum = p.edges[i]
                        edge = model.edges!![abs(edgeNum)]
                        if (edge.normal[0] == 0.0f && edge.normal[1] == 0.0f && edge.normal[2] == 0.0f) {
                            // if the edge is only used by this polygon
                            if (edge.numUsers.toInt() == 1) {
                                dir.set(model.vertices!![edge.vertexNum[if (edgeNum < 0) 1 else 0]].p - (model.vertices!![edge.vertexNum[if (edgeNum > 0) 1 else 0]].p))
                                edge.normal.set(p.plane.Normal().Cross(dir))
                                edge.normal.Normalize()
                            } else {
                                // the edge is used by more than one polygon
                                edge.normal.set(p.plane.Normal())
                            }
                        } else {
                            dot = edge.normal.times(p.plane.Normal())
                            // if the two planes make a very sharp edge
                            if (dot < SHARP_EDGE_DOT) {
                                // max length normal pointing outside both polygons
                                dir.set(model.vertices!![edge.vertexNum[if (edgeNum > 0) 1 else 0]].p - (model.vertices!![edge.vertexNum[if (edgeNum < 0) 1 else 0]].p))
                                edge.normal.set(edge.normal.Cross(dir) + (p.plane.Normal().Cross(-dir)))
                                edge.normal.timesAssign(0.5f / (0.5f + 0.5f * SHARP_EDGE_DOT) / edge.normal.Length())
                                model.numSharpEdges++
                            } else {
                                s = 0.5f / (0.5f + 0.5f * dot)
                                edge.normal.set(edge.normal + (p.plane.Normal()) * (s))
                            }
                        }
                        i++
                    }
                    pref = pref.next
                }
                // if leaf node
                if (currentNode.planeType == -1) {
                    break
                }
                CalculateEdgeNormals(model, currentNode.children[1]!!)
                currentNode = currentNode.children[0]!!
            }
        }

        private fun CreatePatchPolygons(
            model: cm_model_s,
            mesh: idSurface_Patch,
            material: idMaterial,
            primitiveNum: Int
        ) {
            var i: Int
            var j: Int
            var dot: Float
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            val w = idFixedWinding()
            val plane = idPlane()
            val d1 = idVec3()
            val d2 = idVec3()
            i = 0
            while (i < mesh.GetWidth() - 1) {
                j = 0
                while (j < mesh.GetHeight() - 1) {
                    v1 = j * mesh.GetWidth() + i
                    v2 = v1 + 1
                    v3 = v1 + mesh.GetWidth() + 1
                    v4 = v1 + mesh.GetWidth()
                    d1.set(mesh[v2].xyz - (mesh[v1].xyz))
                    d2.set(mesh[v3].xyz - (mesh[v1].xyz))
                    plane.SetNormal(d1.Cross(d2))
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh[v1].xyz)
                        dot = plane.Distance(mesh[v4].xyz)
                        // if we can turn it into a quad
                        if (abs(dot) < 0.1f) {
                            w.Clear()
                            w.plusAssign(mesh[v1].xyz)
                            w.plusAssign(mesh[v2].xyz)
                            w.plusAssign(mesh[v3].xyz)
                            w.plusAssign(mesh[v4].xyz)
                            PolygonFromWinding(model, w, plane, material, -primitiveNum)
                            j++
                            continue
                        } else {
                            // create one of the triangles
                            w.Clear()
                            w.plusAssign(mesh[v1].xyz)
                            w.plusAssign(mesh[v2].xyz)
                            w.plusAssign(mesh[v3].xyz)
                            PolygonFromWinding(model, w, plane, material, -primitiveNum)
                        }
                    }
                    // create the other triangle
                    d1.set(mesh[v3].xyz - (mesh[v1].xyz))
                    d2.set(mesh[v4].xyz - (mesh[v1].xyz))
                    plane.SetNormal(d1.Cross(d2))
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh[v1].xyz)
                        w.Clear()
                        w.plusAssign(mesh[v1].xyz)
                        w.plusAssign(mesh[v3].xyz)
                        w.plusAssign(mesh[v4].xyz)
                        PolygonFromWinding(model, w, plane, material, -primitiveNum)
                    }
                    j++
                }
                i++
            }
        }

        private fun ConvertPatch(model: cm_model_s, patch: idMapPatch, primitiveNum: Int) {
            val material: idMaterial?
            val cp: idSurface_Patch?
            material = DeclManager.declManager!!.FindMaterial(patch.GetMaterial())
            if (0 == material.GetContentFlags() and Material.CONTENTS_REMOVE_UTIL) {
                return
            }

            // copy the patch
            cp = idSurface_Patch(patch)

            // if the patch has an explicit number of subdivisions use it to avoid cracks
            if (patch.GetExplicitlySubdivided()) {
                cp.SubdivideExplicit(patch.GetHorzSubdivisions(), patch.GetVertSubdivisions(), false, true)
            } else {
                cp.Subdivide(
                    MapFile.DEFAULT_CURVE_MAX_ERROR_CD,
                    MapFile.DEFAULT_CURVE_MAX_ERROR_CD,
                    MapFile.DEFAULT_CURVE_MAX_LENGTH_CD,
                    false
                )
            }

            // create collision polygons for the patch
            CreatePatchPolygons(model, cp, material, primitiveNum)
        }

        private fun ConvertBrushSides(model: cm_model_s, mapBrush: idMapBrush, primitiveNum: Int) {
            var i: Int
            var j: Int
            var mapSide: idMapBrushSide?
            val w = idFixedWinding()
            val planes: Array<idPlane> = idPlane.generateArray(mapBrush.GetNumSides())
            var material: idMaterial?
            i = 0
            while (i < mapBrush.GetNumSides()) {
                planes[i].set(mapBrush.GetSide(i).GetPlane())
                planes[i].FixDegeneracies(Plane.DEGENERATE_DIST_EPSILON)
                i++
            }

            // create a collision polygon for each brush side
            i = 0
            while (i < mapBrush.GetNumSides()) {
                mapSide = mapBrush.GetSide(i)
                material = DeclManager.declManager!!.FindMaterial(mapSide.GetMaterial())
                if (0 == material.GetContentFlags() and Material.CONTENTS_REMOVE_UTIL) {
                    i++
                    continue
                }
                w.BaseForPlane(planes[i].unaryMinus())
                j = 0
                while (j < mapBrush.GetNumSides() && w.GetNumPoints() != 0) {
                    if (i == j) {
                        j++
                        continue
                    }
                    w.ClipInPlace(planes[j].unaryMinus(), 0f)
                    j++
                }
                if (w.GetNumPoints() != 0) {
                    PolygonFromWinding(model, w, planes[i], material, primitiveNum)
                }
                i++
            }
        }

        private fun ConvertBrush(model: cm_model_s, mapBrush: idMapBrush, primitiveNum: Int) {
            var i: Int
            var j: Int
            var contents: Int
            val bounds = idBounds()
            var mapSide: idMapBrushSide?
            val brush: cm_brush_s?
            val planes: Array<idPlane> = idPlane.generateArray(mapBrush.GetNumSides())
            val w = idFixedWinding()
            var material: idMaterial? = null
            contents = 0
            bounds.Clear()
            i = 0
            while (i < mapBrush.GetNumSides()) {
                planes[i].set(mapBrush.GetSide(i).GetPlane())
                planes[i].FixDegeneracies(Plane.DEGENERATE_DIST_EPSILON)
                i++
            }

            // we are only getting the bounds for the brush so there's no need
            // to create a winding for the last brush side
            i = 0
            while (i < mapBrush.GetNumSides() - 1) {
                mapSide = mapBrush.GetSide(i)
                material = DeclManager.declManager!!.FindMaterial(mapSide.GetMaterial())
                contents = contents or (material.GetContentFlags() and Material.CONTENTS_REMOVE_UTIL)
                w.BaseForPlane(planes[i].unaryMinus())
                j = 0
                while (j < mapBrush.GetNumSides() && w.GetNumPoints() != 0) {
                    if (i == j) {
                        j++
                        continue
                    }
                    w.ClipInPlace(planes[j].unaryMinus(), 0f)
                    j++
                }
                j = 0
                while (j < w.GetNumPoints()) {
                    bounds.AddPoint(w[j].ToVec3())
                    j++
                }
                i++
            }
            if (0 == contents) {
                return
            }
            // create brush for position test
            brush = AllocBrush(model, mapBrush.GetNumSides())
            brush.checkcount = 0
            brush.contents = contents
            brush.material = material
            brush.primitiveNum = primitiveNum
            brush.bounds.set(bounds)
            brush.numPlanes = mapBrush.GetNumSides()
            i = 0
            while (i < mapBrush.GetNumSides()) {
                brush.planes[i] = idPlane(planes[i])
                i++
            }
            AddBrushToNode(model, model.node!!, brush)
        }

        private fun PrintModelInfo(model: cm_model_s) {
            Common.common.Printf(
                "%6d vertices (%d KB)\n",
                model.numVertices,
                model.numVertices /* sizeof(cm_vertex_t)*/ shr 10
            )
            Common.common.Printf("%6d edges (%d KB)\n", model.numEdges, model.numEdges /* sizeof(cm_edge_t)*/ shr 10)
            Common.common.Printf("%6d polygons (%d KB)\n", model.numPolygons, model.polygonMemory shr 10)
            Common.common.Printf("%6d brushes (%d KB)\n", model.numBrushes, model.brushMemory shr 10)
            Common.common.Printf("%6d nodes (%d KB)\n", model.numNodes, model.numNodes /* sizeof(cm_node_t)*/ shr 10)
            Common.common.Printf(
                "%6d polygon refs (%d KB)\n",
                model.numPolygonRefs,
                model.numPolygonRefs /* sizeof(cm_polygonRef_t)*/ shr 10
            )
            Common.common.Printf(
                "%6d brush refs (%d KB)\n",
                model.numBrushRefs,
                model.numBrushRefs /* sizeof(cm_brushRef_t)*/ shr 10
            )
            Common.common.Printf("%6d internal edges\n", model.numInternalEdges)
            Common.common.Printf("%6d sharp edges\n", model.numSharpEdges)
            Common.common.Printf("%6d contained polygons removed\n", model.numRemovedPolys)
            Common.common.Printf("%6d polygons merged\n", model.numMergedPolys)
            Common.common.Printf("%6d KB total memory used\n", model.usedMemory shr 10)
        }

        private fun AccumulateModelInfo(model: cm_model_s) {
            var i: Int

            // accumulate statistics of all loaded models
            i = 0
            while (i < numModels) {
                model.numVertices += models!![i]!!.numVertices
                model.numEdges += models!![i]!!.numEdges
                model.numPolygons += models!![i]!!.numPolygons
                model.polygonMemory += models!![i]!!.polygonMemory
                model.numBrushes += models!![i]!!.numBrushes
                model.brushMemory += models!![i]!!.brushMemory
                model.numNodes += models!![i]!!.numNodes
                model.numBrushRefs += models!![i]!!.numBrushRefs
                model.numPolygonRefs += models!![i]!!.numPolygonRefs
                model.numInternalEdges += models!![i]!!.numInternalEdges
                model.numSharpEdges += models!![i]!!.numSharpEdges
                model.numRemovedPolys += models!![i]!!.numRemovedPolys
                model.numMergedPolys += models!![i]!!.numMergedPolys
                model.usedMemory += models!![i]!!.usedMemory

                i++
            }
        }

        private fun RemapEdges(node: cm_node_s, edgeRemap: IntArray) {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var i: Int
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        pref = pref.next
                        continue
                    }
                    p.checkcount = checkCount
                    i = 0
                    while (i < p.numEdges) {
                        if (p.edges[i] < 0) {
                            p.edges[i] = -edgeRemap[abs(p.edges[i])]
                        } else {
                            p.edges[i] = edgeRemap[p.edges[i]]
                        }
                        i++
                    }
                    pref = pref.next
                }
                if (currentNode.planeType == -1) {
                    break
                }
                RemapEdges(currentNode.children[1]!!, edgeRemap)
                currentNode = currentNode.children[0]!!
            }
        }

        /*
         ==================
         idCollisionModelManagerLocal::OptimizeArrays

         due to polygon merging and polygon removal the vertex and edge array
         can have a lot of unused entries.
         ==================
         */
        private fun OptimizeArrays(model: cm_model_s) {
            var i: Int
            var newNumVertices: Int
            var newNumEdges: Int
            var v: IntArray?
            val remap: IntArray?
            val oldEdges: Array<cm_edge_s>?
            val oldVertices: Array<cm_vertex_s>?
            remap = IntArray(
                Lib.Max(
                    model.numVertices,
                    model.numEdges
                )
            ) // Mem_ClearedAlloc(Max(model.numVertices, model.numEdges) /*sizeof( int )*/);
            // get all used vertices
            i = 0
            while (i < model.numEdges) {
                remap[model.edges!![i].vertexNum[0]] = 1 //true;
                remap[model.edges!![i].vertexNum[1]] = 1 //true;
                i++
            }
            // create remap index and move vertices
            newNumVertices = 0
            i = 0
            while (i < model.numVertices) {
                if (remap[i] != 0) {
                    remap[i] = newNumVertices
                    model.vertices!![newNumVertices] = model.vertices!![i]
                    newNumVertices++
                }
                i++
            }
            model.numVertices = newNumVertices
            // change edge vertex indexes
            i = 1
            while (i < model.numEdges) {
                v = model.edges!![i].vertexNum
                v[0] = remap[v[0]]
                v[1] = remap[v[1]]
                i++
            }

            // create remap index and move edges
            newNumEdges = 1
            i = 1
            while (i < model.numEdges) {

                // if the edge is used
                if (model.edges!![i].numUsers.toInt() != 0) {
                    remap[i] = newNumEdges
                    model.edges!![newNumEdges] = model.edges!![i]
                    newNumEdges++
                }
                i++
            }
            // change polygon edge indexes
            checkCount++
            RemapEdges(model.node!!, remap)
            model.numEdges = newNumEdges
            //Mem_Free(remap);

            // realloc vertices
            oldVertices = model.vertices
            if (oldVertices != null && oldVertices.size != 0) {
                model.vertices = cm_vertex_s.generateArray(model.numVertices)
                System.arraycopy(oldVertices, 0, model.vertices, 0, model.numVertices)
            }

            // realloc edges
            oldEdges = model.edges
            if (oldEdges != null && oldEdges.size != 0) {
                model.edges = cm_edge_s.generateArray(model.numEdges)
                System.arraycopy(oldEdges, 0, model.edges, 0, model.numEdges)
            }
        }

        private fun FinishModel(model: cm_model_s) {
            // try to merge polygons
            checkCount++
            MergeTreePolygons(model, model.node!!)
            // find internal edges (no mesh can ever collide with internal edges)
            checkCount++
            FindInternalEdges(model, model.node!!)
            // calculate edge normals
            checkCount++
            CalculateEdgeNormals(model, model.node!!)

            //common.Printf( "%s vertex hash spread is %d\n", model.name.c_str(), cm_vertexHash.GetSpread() );
            //common.Printf( "%s edge hash spread is %d\n", model.name.c_str(), cm_edgeHash.GetSpread() );
            // remove all unused vertices and edges
            OptimizeArrays(model)
            // get model bounds from brush and polygon bounds
            CollisionModel_load.CM_GetNodeBounds(model.bounds, model.node!!)
            // get model contents
            model.contents = CollisionModel_load.CM_GetNodeContents(model.node!!)
            // total memory used by this model
            model.usedMemory =
                (model.numVertices * cm_vertex_s.BYTES + model.numEdges * cm_edge_s.BYTES + model.polygonMemory
                        + model.brushMemory
                        + model.numNodes * cm_node_s.BYTES + model.numPolygonRefs * cm_polygonRef_s.BYTES + model.numBrushRefs * cm_brushRef_s.BYTES)
        }

        private fun BuildModels(mapFile: idMapFile) {
            var i: Int
            var mapEnt: idMapEntity?
            val timer = idTimer()
            timer.Start()
            if (!LoadCollisionModelFile(mapFile.GetNameStr(), mapFile.GetGeometryCRC())) {
                if (0 == mapFile.GetNumEntities()) {
                    return
                }

                // load the .proc file bsp for data optimisation
                LoadProcBSP(mapFile.GetName())

                // convert brushes and patches to collision data
                i = 0
                while (i < mapFile.GetNumEntities()) {
                    mapEnt = mapFile.GetEntity(i)
                    if (numModels >= MAX_SUBMODELS) {
                        Common.common.Error(
                            "idCollisionModelManagerLocal::BuildModels: more than %d collision models",
                            MAX_SUBMODELS
                        )
                        break
                    }
                    models!![numModels] = CollisionModelForMapEntity(mapEnt)
                    if (models?.get(numModels) != null) {
                        numModels++
                    }
                    i++
                }

                // free the proc bsp which is only used for data optimization
                procNodes = null

                // write the collision models to a file
                WriteCollisionModelsToFile(mapFile.GetName(), 0, numModels, mapFile.GetGeometryCRC().toLong())
            }
            timer.Stop()

            // print statistics on collision data
            val model = cm_model_s()
            AccumulateModelInfo(model)
            Common.common.Printf("collision data:\n")
            Common.common.Printf("%6d models\n", numModels)
            PrintModelInfo(model)
            Common.common.Printf("%.0f msec to load collision data.\n", timer.Milliseconds())
        }

        private /*cmHandle_t*/   fun FindModel(name: idStr): Int {
            var i: Int

            // check if this model is already loaded
            i = 0
            while (i < numModels) {
                if (0 == models!![i]!!.name.Icmp(name)) {
                    break
                }
                i++
            }
            // if the model is already loaded
            return if (i < numModels) {
                i
            } else -1
        }

        private fun CollisionModelForMapEntity(mapEnt: idMapEntity): cm_model_s? {    // brush/patch model from .map
            val model: cm_model_s?
            val bounds = idBounds()
            val name = arrayOf("")
            var i: Int
            val brushCount: Int

            // if the entity has no primitives
            if (mapEnt.GetNumPrimitives() < 1) {
                return null
            }

            // get a name for the collision model
            mapEnt.epairs!!.GetString("model", "", name)
            if (name[0].isEmpty()) {
                mapEnt.epairs!!.GetString("name", "", name)
                if (name[0].isEmpty()) {
                    if (0 == numModels) {
                        // first model is always the world
                        name[0] = "worldMap"
                    } else {
                        name[0] = "unnamed inline model"
                    }
                }
            }
            model = AllocModel()
            model.node = AllocNode(model, NODE_BLOCK_SIZE_SMALL)
            val maxVertices = CInt()
            val maxEdges = CInt()
            CollisionModel_load.CM_EstimateVertsAndEdges(mapEnt, maxVertices, maxEdges)
            model.maxVertices = maxVertices._val
            model.maxEdges = maxEdges._val
            model.numVertices = 0
            model.numEdges = 0
            model.vertices = cm_vertex_s.generateArray(model.maxVertices)
            model.edges = cm_edge_s.generateArray(model.maxEdges)
            cm_vertexHash!!.ResizeIndex(model.maxVertices)
            cm_edgeHash!!.ResizeIndex(model.maxEdges)
            model.name.set(name[0])
            model.isConvex = false

            // convert brushes
            i = 0
            while (i < mapEnt.GetNumPrimitives()) {
                var mapPrim: idMapPrimitive?
                mapPrim = mapEnt.GetPrimitive(i)
                if (mapPrim.GetType() == idMapPrimitive.TYPE_BRUSH) {
                    ConvertBrush(model, mapPrim as idMapBrush, i)
                    i++
                    continue
                }
                i++
            }

            // create an axial bsp tree for the model if it has more than just a bunch brushes
            brushCount = CollisionModel_load.CM_CountNodeBrushes(model.node!!)
            if (brushCount > 4) {
                model.node = CreateAxialBSPTree(model, model.node!!)
            } else {
                model.node!!.planeType = -1
            }

            // get bounds for hash
            if (brushCount != 0) {
                CollisionModel_load.CM_GetNodeBounds(bounds, model.node!!)
            } else {
                bounds[0].set(-256f, -256f, -256f)
                bounds[1].set(256f, 256f, 256f)
            }

            // different models do not share edges and vertices with each other, so clear the hash
            ClearHash(bounds)

            // create polygons from patches and brushes
            i = 0
            while (i < mapEnt.GetNumPrimitives()) {
                var mapPrim: idMapPrimitive?
                mapPrim = mapEnt.GetPrimitive(i)
                if (mapPrim.GetType() == idMapPrimitive.TYPE_PATCH) {
                    ConvertPatch(model, mapPrim as idMapPatch, i)
                    i++
                    continue
                }
                if (mapPrim.GetType() == idMapPrimitive.TYPE_BRUSH) {
                    ConvertBrushSides(model, mapPrim as idMapBrush, i)
                    i++
                    continue
                }
                i++
            }
            FinishModel(model)
            return model
        }

        private fun LoadRenderModel(fileName: idStr): cm_model_s? {                    // ASE/LWO models
            var i: Int
            var j: Int
            val renderModel: idRenderModel
            var surf: modelSurface_s?
            val w = idFixedWinding()
            val node: cm_node_s?
            val model: cm_model_s
            val plane = idPlane()
            val bounds: idBounds?
            var collisionSurface: Boolean
            val extension: idStr = idPoolStr()

            // only load ASE and LWO models
            idStr(fileName).ExtractFileExtension(extension)
            if (extension.Icmp("ase") != 0 && extension.Icmp("lwo") != 0 && extension.Icmp("ma") != 0) {
                return null
            }
            if (null == ModelManager.renderModelManager.CheckModel(fileName.toString())) {
                return null
            }
            renderModel = ModelManager.renderModelManager.FindModel(fileName.toString())!!
            model = AllocModel()
            model.name.set(fileName)
            node = AllocNode(model, NODE_BLOCK_SIZE_SMALL)
            node.planeType = -1
            model.node = node
            model.maxVertices = 0
            model.numVertices = 0
            model.maxEdges = 0
            model.numEdges = 0
            bounds = renderModel.Bounds(null)
            collisionSurface = false
            i = 0
            while (i < renderModel.NumSurfaces()) {
                surf = renderModel.Surface(i)
                if (surf!!.shader.GetSurfaceFlags() and Material.SURF_COLLISION != 0) {
                    collisionSurface = true
                }
                i++
            }
            i = 0
            while (i < renderModel.NumSurfaces()) {
                surf = renderModel.Surface(i)
                // if this surface has no contents
                if (0 == surf!!.shader.GetContentFlags() and Material.CONTENTS_REMOVE_UTIL) {
                    i++
                    continue
                }
                // if the model has a collision surface and this surface is not a collision surface
                if (collisionSurface && 0 == surf.shader.GetSurfaceFlags() and Material.SURF_COLLISION) {
                    i++
                    continue
                }
                // get max verts and edges
                model.maxVertices += surf.geometry!!.numVerts
                model.maxEdges += surf.geometry!!.numIndexes
                i++
            }
            model.vertices = cm_vertex_s.generateArray(model.maxVertices)
            model.edges = cm_edge_s.generateArray(model.maxEdges)

            // setup hash to speed up finding shared vertices and edges
            SetupHash()
            cm_vertexHash!!.ResizeIndex(model.maxVertices)
            cm_edgeHash!!.ResizeIndex(model.maxEdges)
            ClearHash(bounds!!)
            i = 0
            while (i < renderModel.NumSurfaces()) {
                surf = renderModel.Surface(i)
                // if this surface has no contents
                if (0 == surf!!.shader.GetContentFlags() and Material.CONTENTS_REMOVE_UTIL) {
                    i++
                    continue
                }
                // if the model has a collision surface and this surface is not a collision surface
                if (collisionSurface && 0 == surf.shader.GetSurfaceFlags() and Material.SURF_COLLISION) {
                    i++
                    continue
                }
                j = 0
                while (j < surf.geometry!!.numIndexes) {
                    w.Clear()
                    w.plusAssign(surf.geometry!!.verts!![surf.geometry!!.indexes!![j + 2]]!!.xyz)
                    w.plusAssign(surf.geometry!!.verts!![surf.geometry!!.indexes!![j + 1]]!!.xyz)
                    w.plusAssign(surf.geometry!!.verts!![surf.geometry!!.indexes!![j]]!!.xyz)
                    w.GetPlane(plane)
                    plane.set(plane.unaryMinus())
                    PolygonFromWinding(model, w, plane, surf.shader, 1)
                    j += 3
                }
                i++
            }

            // create a BSP tree for the model
            model.node = CreateAxialBSPTree(model, model.node!!)
            model.isConvex = false
            FinishModel(model)

            // shutdown the hash
            ShutdownHash()
            Common.common.Printf("loaded collision model %s\n", model.name)
            return model
        }

        private fun TrmFromModel_r(trm: idTraceModel, node: cm_node_s): Boolean {
            var currentNode = node
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var i: Int
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    if (p.checkcount == checkCount) {
                        pref = pref.next
                        continue
                    }
                    p.checkcount = checkCount
                    if (trm.numPolys >= TraceModel.MAX_TRACEMODEL_POLYS) {
                        return false
                    }
                    // copy polygon properties
                    trm.polys[trm.numPolys].bounds.set(p.bounds)
                    trm.polys[trm.numPolys].normal.set(p.plane.Normal())
                    trm.polys[trm.numPolys].dist = p.plane.Dist()
                    trm.polys[trm.numPolys].numEdges = p.numEdges
                    // copy edge index
                    i = 0
                    while (i < p.numEdges) {
                        trm.polys[trm.numPolys].edges[i] = p.edges[i]
                        i++
                    }
                    trm.numPolys++
                    pref = pref.next
                }
                if (currentNode.planeType == -1) {
                    break
                }
                if (!TrmFromModel_r(trm, currentNode.children[1]!!)) {
                    return false
                }
                currentNode = currentNode.children[0]!!
            }
            return true
        }

        //
        /*
         ==================
         idCollisionModelManagerLocal::TrmFromModel

         NOTE: polygon merging can merge colinear edges and as such might cause dangling edges.
         ==================
         */
        private fun TrmFromModel(model: cm_model_s, trm: idTraceModel): Boolean {
            var i: Int
            var j: Int
            val numEdgeUsers = IntArray(TraceModel.MAX_TRACEMODEL_EDGES + 1)

            // if the model has too many vertices to fit in a trace model
            if (model.numVertices > TraceModel.MAX_TRACEMODEL_VERTS) {
                Common.common.Printf(
                    "idCollisionModelManagerLocal::TrmFromModel: model %s has too many vertices.\n",
                    model.name
                )
                PrintModelInfo(model)
                return false
            }

            // plus one because the collision model accounts for the first unused edge
            if (model.numEdges > TraceModel.MAX_TRACEMODEL_EDGES + 1) {
                Common.common.Printf(
                    "idCollisionModelManagerLocal::TrmFromModel: model %s has too many edges.\n",
                    model.name
                )
                PrintModelInfo(model)
                return false
            }
            trm.type = traceModel_t.TRM_CUSTOM
            trm.numVerts = 0
            trm.numEdges = 1
            trm.numPolys = 0
            trm.bounds.Clear()

            // copy polygons
            checkCount++
            if (!TrmFromModel_r(trm, model.node!!)) {
                Common.common.Printf(
                    "idCollisionModelManagerLocal::TrmFromModel: model %s has too many polygons.\n",
                    model.name
                )
                PrintModelInfo(model)
                return false //HACKME::9
            }

            // copy vertices
            i = 0
            while (i < model.numVertices) {
                trm.verts[i].set(model.vertices!![i].p)
                trm.bounds.AddPoint(trm.verts[i])
                i++
            }
            trm.numVerts = model.numVertices

            // copy edges
            i = 0
            while (i < model.numEdges) {
                trm.edges[i].v[0] = model.edges!![i].vertexNum[0]
                trm.edges[i].v[1] = model.edges!![i].vertexNum[1]
                i++
            }
            // minus one because the collision model accounts for the first unused edge
            trm.numEdges = model.numEdges - 1

            // each edge should be used exactly twice
            i = 0
            while (i < trm.numPolys) {
                j = 0
                while (j < trm.polys[i].numEdges) {
                    numEdgeUsers[abs(trm.polys[i].edges[j])]++
                    j++
                }
                i++
            }
            i = 1
            while (i <= trm.numEdges) {
                if (numEdgeUsers[i] != 2) {
                    Common.common.Printf(
                        "idCollisionModelManagerLocal::TrmFromModel: model %s has dangling edges, the model has to be an enclosed hull.\n",
                        model.name
                    )
                    PrintModelInfo(model)
                    return false //HACKME::9
                }
                i++
            }

            // assume convex
            trm.isConvex = true
            // check if really convex
            i = 0
            while (i < trm.numPolys) {

                // to be convex no vertices should be in front of any polygon plane
                j = 0
                while (j < trm.numVerts) {
                    if (trm.polys[i].normal.times(trm.verts[j]) - trm.polys[i].dist > 0.01f) {
                        trm.isConvex = false
                        break
                    }
                    j++
                }
                if (j < trm.numVerts) {
                    break
                }
                i++
            }

            // offset to center of model
            trm.offset.set(trm.bounds.GetCenter())
            trm.GenerateEdgeNormals()
            return true
        }

        /*
         ===============================================================================

         Writing of collision model file

         ===============================================================================
         */
        // CollisionMap_files.cpp
        // writing
        private fun WriteNodes(fp: idFile, node: cm_node_s) {
            fp.WriteFloatString("\t( %d %f )\n", node.planeType, node.planeDist)
            if (node.planeType != -1) {
                WriteNodes(fp, node.children[0]!!)
                WriteNodes(fp, node.children[1]!!)
            }
        }

        private fun CountPolygonMemory(node: cm_node_s): Int {
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var memory: Int
            memory = 0
            pref = node.polygons
            while (pref != null) {
                p = pref.p!!
                if (p.checkcount == checkCount) {
                    pref = pref.next
                    continue
                }
                p.checkcount = checkCount
                memory += cm_polygon_s.BYTES + (p.numEdges - 1) * Integer.BYTES
                pref = pref.next
            }
            if (node.planeType != -1) {
                memory += CountPolygonMemory(node.children[0]!!)
                memory += CountPolygonMemory(node.children[1]!!)
            }
            return memory
        }

        private fun WritePolygons(fp: idFile, node: cm_node_s) {
            var pref: cm_polygonRef_s?
            var p: cm_polygon_s
            var i: Int
            pref = node.polygons
            while (pref != null) {
                p = pref.p!!
                if (p.checkcount == checkCount) {
                    pref = pref.next
                    continue
                }
                p.checkcount = checkCount
                fp.WriteFloatString("\t%d (", p.numEdges)
                i = 0
                while (i < p.numEdges) {
                    fp.WriteFloatString(" %d", p.edges[i])
                    i++
                }
                fp.WriteFloatString(
                    " ) ( %f %f %f ) %f",
                    p.plane.Normal()[0],
                    p.plane.Normal()[1],
                    p.plane.Normal()[2],
                    p.plane.Dist()
                )
                fp.WriteFloatString(
                    " ( %f %f %f )",
                    p.bounds[0][0],
                    p.bounds[0][1],
                    p.bounds[0][2]
                )
                fp.WriteFloatString(
                    " ( %f %f %f )",
                    p.bounds[1][0],
                    p.bounds[1][1],
                    p.bounds[1][2]
                )
                fp.WriteFloatString(" \"%s\"\n", p.material!!.GetName())
                pref = pref.next
            }
            if (node.planeType != -1) {
                WritePolygons(fp, node.children[0]!!)
                WritePolygons(fp, node.children[1]!!)
            }
        }

        private fun CountBrushMemory(node: cm_node_s): Int {
            var bref: cm_brushRef_s?
            var b: cm_brush_s
            var memory: Int
            memory = 0
            bref = node.brushes
            while (bref != null) {
                b = bref.b!!
                if (b.checkcount == checkCount) {
                    bref = bref.next
                    continue
                }
                b.checkcount = checkCount
                memory += cm_brush_s.BYTES + (b.numPlanes - 1) * idPlane.BYTES
                bref = bref.next
            }
            if (node.planeType != -1) {
                memory += CountBrushMemory(node.children[0]!!)
                memory += CountBrushMemory(node.children[1]!!)
            }
            return memory
        }

        private fun WriteBrushes(fp: idFile, node: cm_node_s) {
            var bref: cm_brushRef_s?
            var b: cm_brush_s
            var i: Int
            bref = node.brushes
            while (bref != null) {
                b = bref.b!!
                if (b.checkcount == checkCount) {
                    bref = bref.next
                    continue
                }
                b.checkcount = checkCount
                fp.WriteFloatString("\t%d {\n", b.numPlanes)
                i = 0
                while (i < b.numPlanes) {
                    fp.WriteFloatString(
                        "\t\t( %f %f %f ) %f\n",
                        b.planes[i].Normal()[0],
                        b.planes[i].Normal()[1],
                        b.planes[i].Normal()[2],
                        b.planes[i].Dist()
                    )
                    i++
                }
                fp.WriteFloatString(
                    "\t} ( %f %f %f )",
                    b.bounds[0][0],
                    b.bounds[0][1],
                    b.bounds[0][2]
                )
                fp.WriteFloatString(
                    " ( %f %f %f ) \"%s\"\n",
                    b.bounds[1][0],
                    b.bounds[1][1],
                    b.bounds[1][2],
                    StringFromContents(b.contents)
                )
                bref = bref.next
            }
            if (node.planeType != -1) {
                WriteBrushes(fp, node.children[0]!!)
                WriteBrushes(fp, node.children[1]!!)
            }
        }

        private fun WriteCollisionModel(fp: idFile, model: cm_model_s) {
            var i: Int
            val polygonMemory: Int
            val brushMemory: Int
            fp.WriteFloatString("collisionModel \"%s\" {\n", model.name)
            // vertices
            fp.WriteFloatString("\tvertices { /* numVertices = */ %d\n", model.numVertices)
            i = 0
            while (i < model.numVertices) {
                fp.WriteFloatString(
                    "\t/* %d */ ( %f %f %f )\n",
                    i,
                    model.vertices!![i].p[0],
                    model.vertices!![i].p[1],
                    model.vertices!![i].p[2]
                )
                i++
            }
            fp.WriteFloatString("\t}\n")
            // edges
            fp.WriteFloatString("\tedges { /* numEdges = */ %d\n", model.numEdges)
            i = 0
            while (i < model.numEdges) {
                fp.WriteFloatString(
                    "\t/* %d */ ( %d %d ) %d %d\n",
                    i,
                    model.edges!![i].vertexNum[0],
                    model.edges!![i].vertexNum[1],
                    model.edges!![i].internal,
                    model.edges!![i].numUsers
                )
                i++
            }
            fp.WriteFloatString("\t}\n")
            // nodes
            fp.WriteFloatString("\tnodes {\n")
            WriteNodes(fp, model.node!!)
            fp.WriteFloatString("\t}\n")
            // polygons
            checkCount++
            polygonMemory = CountPolygonMemory(model.node!!)
            fp.WriteFloatString("\tpolygons /* polygonMemory = */ %d {\n", polygonMemory)
            checkCount++
            WritePolygons(fp, model.node!!)
            fp.WriteFloatString("\t}\n")
            // brushes
            checkCount++
            brushMemory = CountBrushMemory(model.node!!)
            fp.WriteFloatString("\tbrushes /* brushMemory = */ %d {\n", brushMemory)
            checkCount++
            WriteBrushes(fp, model.node!!)
            fp.WriteFloatString("\t}\n")
            // closing brace
            fp.WriteFloatString("}\n")
        }

        private fun WriteCollisionModelsToFile(filename: String, firstModel: Int, lastModel: Int, mapFileCRC: Long) {
            var i: Int
            val fp: idFile?
            val name: idStr
            name = idStr(filename)
            name.SetFileExtension(CollisionModel_files.CM_FILE_EXT)
            Common.common.Printf("writing %s\n", filename)
            // _D3XP was saving to fs_cdpath
            fp = FileSystem_h.fileSystem.OpenFileWrite(filename, "fs_devpath")
            if (null == fp) {
                Common.common.Warning(
                    "idCollisionModelManagerLocal::WriteCollisionModelsToFile: Error opening file %s\n",
                    filename
                )
                return
            }

            // write file id and version
            fp.WriteFloatString("%s \"%s\"\n\n", CollisionModel_files.CM_FILEID, CollisionModel_files.CM_FILEVERSION)
            // write the map file crc
            fp.WriteFloatString("%u\n\n", mapFileCRC)

            // write the collision models
            i = firstModel
            while (i < lastModel) {
                WriteCollisionModel(fp, models!![i]!!)
                i++
            }
            FileSystem_h.fileSystem.CloseFile(fp)
        }

        /*
         ===============================================================================

         Loading of collision model file

         ===============================================================================
         */
        // loading
        private fun ParseNodes(src: idLexer, model: cm_model_s, parent: cm_node_s?): cm_node_s {
            model.numNodes++
            val node: cm_node_s = AllocNode(
                model,
                if (model.numNodes < NODE_BLOCK_SIZE_SMALL) NODE_BLOCK_SIZE_SMALL else NODE_BLOCK_SIZE_LARGE
            )
            node.brushes = null
            node.polygons = null
            node.parent = parent
            src.ExpectTokenString("(")
            node.planeType = src.ParseInt()
            node.planeDist = src.ParseFloat()
            src.ExpectTokenString(")")
            if (node.planeType != -1) {
                node.children[0] = ParseNodes(src, model, node)
                node.children[1] = ParseNodes(src, model, node)
            }
            return node
        }

        private fun ParseVertices(src: idLexer, model: cm_model_s) {
            var i: Int
            src.ExpectTokenString("{")
            model.numVertices = src.ParseInt()
            model.maxVertices = model.numVertices
            model.vertices = Array(model.maxVertices) { cm_vertex_s() } // Mem_Alloc(model.maxVertices);
            i = 0
            while (i < model.numVertices) {
                src.Parse1DMatrix(3, model.vertices!![i].p)
                model.vertices!![i].side = 0
                model.vertices!![i].sideSet = 0
                model.vertices!![i].checkcount = 0
                i++
            }
            src.ExpectTokenString("}")
        }

        private fun ParseEdges(src: idLexer, model: cm_model_s) {
            var i: Int
            src.ExpectTokenString("{")
            model.numEdges = src.ParseInt()
            model.maxEdges = model.numEdges
            model.edges = Array(model.maxEdges) { cm_edge_s() } // Mem_Alloc(model.maxEdges);
            i = 0
            while (i < model.numEdges) {
                src.ExpectTokenString("(")
                model.edges!![i].vertexNum[0] = src.ParseInt()
                model.edges!![i].vertexNum[1] = src.ParseInt()
                src.ExpectTokenString(")")
                model.edges!![i].side = 0
                model.edges!![i].sideSet = 0
                model.edges!![i].internal = src.ParseInt() == 1
                model.edges!![i].numUsers = src.ParseInt().toShort()
                model.edges!![i].normal.set(getVec3_origin())
                model.edges!![i].checkcount = 0
                model.numInternalEdges += if (model.edges!![i].internal) 1 else 0
                i++
            }
            src.ExpectTokenString("}")
        }

        private fun ParsePolygons(src: idLexer, model: cm_model_s) {
            var p: cm_polygon_s
            var i: Int
            var numEdges: Int
            val normal = idVec3()
            val token = idToken()
            if (src.CheckTokenType(Token.TT_NUMBER, 0, token) != 0) {
                model.polygonBlock = cm_polygonBlock_s()
                model.polygonBlock!!.bytesRemaining = token.GetIntValue()
                model.polygonBlock!!.next = cm_polygonBlock_s()
            }
            src.ExpectTokenString("{")
            while (!src.CheckTokenString("}")) {
                // parse polygon
                numEdges = src.ParseInt()
                p = AllocPolygon(model, numEdges)
                p.numEdges = numEdges
                src.ExpectTokenString("(")
                i = 0
                while (i < p.numEdges) {
                    p.edges[i] = src.ParseInt()
                    i++
                }
                src.ExpectTokenString(")")
                src.Parse1DMatrix(3, normal)
                p.plane.SetNormal(normal)
                p.plane.SetDist(src.ParseFloat())
                src.Parse1DMatrix(3, p.bounds[0])
                src.Parse1DMatrix(3, p.bounds[1])
                src.ExpectTokenType(Token.TT_STRING, 0, token)
                // get material
                p.material = DeclManager.declManager!!.FindMaterial(token)
                p.contents = p.material!!.GetContentFlags()
                p.checkcount = 0
                // filter polygon into tree
                R_FilterPolygonIntoTree(model, model.node!!, null, p)
            }
        }

        private fun ParseBrushes(src: idLexer, model: cm_model_s) {
            var b: cm_brush_s?
            var i: Int
            var numPlanes: Int
            val normal = idVec3()
            val token = idToken()
            if (src.CheckTokenType(Token.TT_NUMBER, 0, token) != 0) {
                model.brushBlock = cm_brushBlock_s()
                model.brushBlock!!.bytesRemaining = token.GetIntValue()
                model.brushBlock!!.next = cm_brushBlock_s()
            }
            src.ExpectTokenString("{")
            while (!src.CheckTokenString("}")) {
                // parse brush
                numPlanes = src.ParseInt()
                b = AllocBrush(model, numPlanes)
                b.numPlanes = numPlanes
                src.ExpectTokenString("{")
                i = 0
                while (i < b.numPlanes) {
                    src.Parse1DMatrix(3, normal)
                    b.planes[i] = idPlane()
                    b.planes[i].SetNormal(normal)
                    b.planes[i].SetDist(src.ParseFloat())
                    i++
                }
                src.ExpectTokenString("}")
                src.Parse1DMatrix(3, b.bounds[0])
                src.Parse1DMatrix(3, b.bounds[1])
                src.ReadToken(token)
                if (token.type == Token.TT_NUMBER) {
                    b.contents = token.GetIntValue() // old .cm files use a single integer
                } else {
                    b.contents = ContentsFromString(token.toString())
                }
                b.checkcount = 0
                b.primitiveNum = 0
                // filter brush into tree
                R_FilterBrushIntoTree(model, model.node!!, null, b)
            }
        }

        private fun ParseCollisionModel(src: idLexer): Boolean {
            val model: cm_model_s
            val token = idToken()
            if (numModels >= MAX_SUBMODELS) {
                Common.common.Error("LoadModel: no free slots")
                return false
            }
            model = AllocModel()
            models!![numModels] = model
            numModels++
            // parse the file
            src.ExpectTokenType(Token.TT_STRING, 0, token)
            model.name.set(token)
            src.ExpectTokenString("{")
            while (!src.CheckTokenString("}")) {
                src.ReadToken(token)
                if (token.toString() == "vertices") {
                    ParseVertices(src, model)
                    continue
                }
                if (token.toString() == "edges") {
                    ParseEdges(src, model)
                    continue
                }
                if (token.toString() == "nodes") {
                    src.ExpectTokenString("{")
                    model.node = ParseNodes(src, model, null)
                    src.ExpectTokenString("}")
                    continue
                }
                if (token.toString() == "polygons") {
                    ParsePolygons(src, model)
                    continue
                }
                if (token.toString() == "brushes") {
                    ParseBrushes(src, model)
                    continue
                }
                src.Error("ParseCollisionModel: bad token \"%s\"", token.toString())
            }
            // calculate edge normals
            checkCount++
            CalculateEdgeNormals(model, model.node!!)
            // get model bounds from brush and polygon bounds
            CollisionModel_load.CM_GetNodeBounds(model.bounds, model.node!!)
            // get model contents
            model.contents = CollisionModel_load.CM_GetNodeContents(model.node!!)
            // total memory used by this model
            model.usedMemory =
                (model.numVertices * cm_vertex_s.BYTES + model.numEdges * cm_edge_s.BYTES + model.polygonMemory
                        + model.brushMemory
                        + model.numNodes //* cm_node_s.Bytes
                        + model.numPolygonRefs //* cm_polygonRef_s.Bytes
                        + model.numBrushRefs) //* cm_brushRef_s.Bytes;
            return true
        }

        //
        private fun LoadCollisionModelFile(name: idStr, mapFileCRC: Int): Boolean {
            val fileName: idStr
            val token = idToken()
            val src: idLexer?
            val crc: Int

            // load it
            fileName = idStr(name)
            fileName.SetFileExtension(CollisionModel_files.CM_FILE_EXT)
            src = idLexer(fileName.toString())
            src.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NODOLLARPRECOMPILE)
            if (!src.IsLoaded()) {
                return false
            }
            if (!src.ExpectTokenString(CollisionModel_files.CM_FILEID)) {
                Common.common.Warning("%s is not an CM file.", fileName)
                return false
            }
            if (!src.ReadToken(token) || token.toString() != CollisionModel_files.CM_FILEVERSION) {
                Common.common.Warning(
                    "%s has version %s instead of %s",
                    fileName,
                    token,
                    CollisionModel_files.CM_FILEVERSION
                )
                return false
            }
            if (0 == src.ExpectTokenType(Token.TT_NUMBER, Token.TT_INTEGER, token)) {
                Common.common.Warning("%s has no map file CRC", fileName)
                return false
            }
            crc = token.GetUnsignedLongValue().toInt()
            if (mapFileCRC != 0 && crc != mapFileCRC) {
                Common.common.Printf("%s is out of date\n", fileName)
                return false
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "collisionModel") {
                    if (!ParseCollisionModel(src)) {
                        return false
                    }
                    continue
                }
                src.Error("idCollisionModelManagerLocal::LoadCollisionModelFile: bad token \"%s\"", token)
            }
            return true
        }

        // CollisionMap_debug
        private fun ContentsFromString(string: String): Int {
            var i: Int
            var contents = 0
            val src = idLexer(string, string.length, "ContentsFromString")
            val token = idToken()
            while (src.ReadToken(token)) {
                if (token.toString() == ",") {
                    continue
                }
                i = 1
                while (CollisionModel_debug.cm_contentsNameByIndex[i] != null) {
                    if (token.Icmp(CollisionModel_debug.cm_contentsNameByIndex[i]) == 0) {
                        contents = contents or CollisionModel_debug.cm_contentsFlagByIndex[i]
                        break
                    }
                    i++
                }
            }
            return contents
        }

        private fun StringFromContents(contents: Int): String {
            var i: Int
            var length = 0
            contentsString[0] = '\u0000'
            i = 1
            while (CollisionModel_debug.cm_contentsFlagByIndex[i] != 0) {
                if (contents and CollisionModel_debug.cm_contentsFlagByIndex[i] != 0) {
                    if (length != 0) {
                        length += idStr.snPrintf(
                            length,
                            contentsString,
                            Lib.MAX_STRING_CHARS - length,
                            ","
                        )
                    }
                    length += idStr.snPrintf(
                        length,
                        contentsString,
                        Lib.MAX_STRING_CHARS - length,
                        CollisionModel_debug.cm_contentsNameByIndex[i]
                    )
                }
                i++
            }
            return TempDump.ctos(contentsString)!!
        }

        private fun DrawEdge(model: cm_model_s, edgeNum: Int, origin: idVec3, axis: idMat3) {
            val side: Boolean
            val edge: cm_edge_s?
            val start = idVec3()
            val end = idVec3()
            val mid = idVec3()
            val isRotated: Boolean
            isRotated = axis.IsRotated()
            edge = model.edges!![abs(edgeNum)]
            side = edgeNum < 0
            start.set(model.vertices!![edge.vertexNum[if (side) 1 else 0]].p)
            end.set(model.vertices!![edge.vertexNum[if (side) 0 else 1]].p)
            if (isRotated) {
                start.timesAssign(axis)
                end.timesAssign(axis)
            }
            start.plusAssign(origin)
            end.plusAssign(origin)
            if (edge.internal) {
                if (CollisionModel_debug.cm_drawInternal.GetBool()) {
                    Session.session.rw!!.DebugArrow(Lib.colorGreen, start, end, 1)
                }
            } else {
                if (edge.numUsers > 2) {
                    Session.session.rw!!.DebugArrow(Lib.colorBlue, start, end, 1)
                } else {
                    Session.session.rw!!.DebugArrow(CollisionModel_debug.cm_color, start, end, 1)
                }
            }
            if (CollisionModel_debug.cm_drawNormals.GetBool()) {
                mid.set(start + (end) * (0.5f))
                if (isRotated) {
                    end.set(mid + (axis.times(edge.normal)) * (5f))
                } else {
                    end.set(mid + (edge.normal.times(5f)))
                }
                Session.session.rw!!.DebugArrow(Lib.colorCyan, mid, end, 1)
            }
        }

        private fun DrawPolygon(
            model: cm_model_s,
            p: cm_polygon_s,
            origin: idVec3,
            axis: idMat3,
            viewOrigin: idVec3
        ) {
            var i: Int
            var edgeNum: Int
            var edge: cm_edge_s
            val center = idVec3()
            val end = idVec3()
            val dir = idVec3()
            if (CollisionModel_debug.cm_backFaceCull.GetBool()) {
                edgeNum = p.edges[0]
                edge = model.edges!![abs(edgeNum)]
                dir.set(model.vertices!![edge.vertexNum[0]].p - (viewOrigin))
                if (dir.times(p.plane.Normal()) > 0.0f) {
                    return
                }
            }
            if (CollisionModel_debug.cm_drawNormals.GetBool()) {
                center.set(getVec3_origin())
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    edge = model.edges!![abs(edgeNum)]
                    center.plusAssign(model.vertices!![edge.vertexNum[if (edgeNum < 0) 1 else 0]].p)
                    i++
                }
                center.timesAssign(1.0f / p.numEdges)
                if (axis.IsRotated()) {
                    center.set(center.times(axis).plusAssign(origin))
                    end.set(
                        center + ( // center +
                                axis.times(p.plane.Normal()).times(5f)
                                ) // axis * p.planeNormal * 5
                    )
                } else {
                    center.plusAssign(origin)
                    end.set(center + (p.plane.Normal().times(5f)))
                }
                Session.session.rw!!.DebugArrow(Lib.colorMagenta, center, end, 1)
            }
            if (CollisionModel_debug.cm_drawFilled.GetBool()) {
                val winding = idFixedWinding()
                i = p.numEdges - 1
                while (i >= 0) {
                    edgeNum = p.edges[i]
                    edge = model.edges!![abs(edgeNum)]
                    winding.plusAssign(
                        origin + (
                                model.vertices!![edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]].p.times(
                                    axis
                                )
                                )
                    )
                    i--
                }
                Session.session.rw!!.DebugPolygon(CollisionModel_debug.cm_color, winding)
            } else {
                i = 0
                while (i < p.numEdges) {
                    edgeNum = p.edges[i]
                    edge = model.edges!![abs(edgeNum)]
                    if (edge.checkcount == checkCount) {
                        i++
                        continue
                    }
                    edge.checkcount = checkCount
                    DrawEdge(model, edgeNum, origin, axis)
                    i++
                }
            }
        }

        private fun DrawNodePolygons(
            model: cm_model_s, node: cm_node_s, origin: idVec3, axis: idMat3,
            viewOrigin: idVec3, radius: Float
        ) {
            var currentNode = node
            var i: Int
            var p: cm_polygon_s
            var pref: cm_polygonRef_s?
            while (true) {
                pref = currentNode.polygons
                while (pref != null) {
                    p = pref.p!!
                    if (radius != 0.0f) {
                        // polygon bounds should overlap with trace bounds
                        i = 0
                        while (i < 3) {
                            if (p.bounds[0][i] > viewOrigin[i] + radius) {
                                break
                            }
                            if (p.bounds[1][i] < viewOrigin[i] - radius) {
                                break
                            }
                            i++
                        }
                        if (i < 3) {
                            pref = pref.next
                            continue
                        }
                    }
                    if (p.checkcount == checkCount) {
                        pref = pref.next
                        continue
                    }
                    if (0 == p.contents and CollisionModel_debug.cm_contentsFlagByIndex[CollisionModel_debug.cm_drawMask.GetInteger()]) {
                        pref = pref.next
                        continue
                    }
                    DrawPolygon(model, p, origin, axis, viewOrigin)
                    p.checkcount = checkCount
                    pref = pref.next
                }
                if (currentNode.planeType == -1) {
                    break
                }
                currentNode =
                    if (radius != 0.0f && viewOrigin[currentNode.planeType] > currentNode.planeDist + radius) {
                        currentNode.children[0]!!
                    } else if (radius != 0.0f && viewOrigin[currentNode.planeType] < currentNode.planeDist - radius) {
                        currentNode.children[1]!!
                    } else {
                        DrawNodePolygons(model, currentNode.children[1]!!, origin, axis, viewOrigin, radius)
                        currentNode.children[0]!!
                    }
            }
        }

        companion object {
            /*
             ===============================================================================

             Collision detection for rotational motion

             ===============================================================================
             */
            // epsilon for round-off errors in epsilon calculations
            const val CM_PL_RANGE_EPSILON = 1e-4f
            const val CONTINUOUS_EPSILON = 0.005f
            const val NORMAL_EPSILON = 0.01f
            const val NO_SPATIAL_SUBDIVISION = false

            // if the collision point is this close to the rotation axis it is not considered a collision
            const val ROTATION_AXIS_EPSILON = CollisionModel.CM_CLIP_EPSILON * 0.25f

            /*
             ===============================================================================

             Edge normals

             ===============================================================================
             */
            const val SHARP_EDGE_DOT = -0.7f
            private val tw: cm_traceWork_s = cm_traceWork_s()
            var contentsString: CharArray = CharArray(Lib.MAX_STRING_CHARS)
            private var entered = 0
        }
    }
}