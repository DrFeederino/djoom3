package neo.CM;

import neo.Renderer.Model.idRenderModel;
import neo.Renderer.Model.modelSurface_s;
import neo.framework.File_h.idFile;
import neo.idlib.BV.Bounds.idBounds;
import neo.idlib.Text.Lexer.idLexer;
import neo.idlib.Text.Str.idStr;
import neo.idlib.Timer.idTimer;
import neo.idlib.containers.CFloat;
import neo.idlib.containers.CInt;
import neo.idlib.containers.HashIndex.idHashIndex;
import neo.idlib.containers.StrPool;
import neo.idlib.geometry.Surface_Patch.idSurface_Patch;
import neo.idlib.geometry.Winding.idFixedWinding;
import neo.idlib.math.Angles.idAngles;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Pluecker.idPluecker;
import neo.idlib.math.Random.idRandom;
import neo.idlib.math.Rotation.idRotation;
import neo.idlib.math.Vector.idVec3;
import neo.idlib.math.Vector.idVec6;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Stream;

import static neo.CM.CollisionModel.*;
import static neo.CM.CollisionModel.contactType_t.*;
import static neo.CM.CollisionModel_contents.CM_SetTrmEdgeSidedness;
import static neo.CM.CollisionModel_contents.CM_SetTrmPolygonSidedness;
import static neo.CM.CollisionModel_debug.*;
import static neo.CM.CollisionModel_files.*;
import static neo.CM.CollisionModel_load.*;
import static neo.CM.CollisionModel_rotate.CM_RotateEdge;
import static neo.CM.CollisionModel_rotate.CM_RotatePoint;
import static neo.CM.CollisionModel_translate.*;
import static neo.Renderer.Material.*;
import static neo.Renderer.Material.cullType_t.CT_TWO_SIDED;
import static neo.Renderer.ModelManager.renderModelManager;
import static neo.Renderer.RenderWorld.PROC_FILE_EXT;
import static neo.Renderer.RenderWorld.PROC_FILE_ID;
import static neo.TempDump.ctos;
import static neo.framework.Common.common;
import static neo.framework.DeclManager.declManager;
import static neo.framework.FileSystem_h.fileSystem;
import static neo.framework.Session.session;
import static neo.idlib.Lib.*;
import static neo.idlib.MapFile.*;
import static neo.idlib.Text.Lexer.LEXFL_NODOLLARPRECOMPILE;
import static neo.idlib.Text.Lexer.LEXFL_NOSTRINGCONCAT;
import static neo.idlib.Text.Str.va;
import static neo.idlib.Text.Token.*;
import static neo.idlib.geometry.TraceModel.*;
import static neo.idlib.geometry.TraceModel.traceModel_t.TRM_CUSTOM;
import static neo.idlib.geometry.TraceModel.traceModel_t.TRM_INVALID;
import static neo.idlib.geometry.Winding.MAX_POINTS_ON_WINDING;
import static neo.idlib.math.Math_h.*;
import static neo.idlib.math.Matrix.idMat3.getMat3_identity;
import static neo.idlib.math.Plane.*;
import static neo.idlib.math.Vector.getVec3_origin;

public class CollisionModel_local extends AbstractCollisionModel_local {

    /*
     ===============================================================================

     Trace model vs. polygonal model collision detection.

     ===============================================================================
     */
    private static final boolean _DEBUG = false;
    static idHashIndex cm_edgeHash;
    static idBounds cm_modelBounds;
    static cm_windingList_s cm_outList;
    static cm_windingList_s cm_tmpList;
    static idHashIndex cm_vertexHash;
    static int cm_vertexShift;
    static cm_windingList_s cm_windingList;
    private static idCollisionModelManagerLocal collisionModelManagerLocal = new idCollisionModelManagerLocal();
    public static idCollisionModelManager collisionModelManager = collisionModelManagerLocal;

    public static void setCollisionModelManager(idCollisionModelManager collisionModelManager) {
        CollisionModel_local.collisionModelManager
                = CollisionModel_local.collisionModelManagerLocal
                = (idCollisionModelManagerLocal) collisionModelManager;
    }

    public static class idCollisionModelManagerLocal extends idCollisionModelManager {
        /*
         ===============================================================================

         Collision detection for rotational motion

         ===============================================================================
         */
        // epsilon for round-off errors in epsilon calculations
        static final float CM_PL_RANGE_EPSILON = 1e-4f;
        static final float CONTINUOUS_EPSILON = 0.005f;
        static final float NORMAL_EPSILON = 0.01f;
        static final boolean NO_SPATIAL_SUBDIVISION = false;
        // if the collision point is this close to the rotation axis it is not considered a collision
        static final float ROTATION_AXIS_EPSILON = (CM_CLIP_EPSILON * 0.25f);
        /*
         ===============================================================================

         Edge normals

         ===============================================================================
         */
        static final float SHARP_EDGE_DOT = -0.7f;
        static char[] contentsString = new char[MAX_STRING_CHARS];
        private static int entered = 0;
        private static cm_traceWork_s tw = new cm_traceWork_s();
        private final cm_brushRef_s[] trmBrushes = new cm_brushRef_s[1];
        // for multi-check avoidance
        private int checkCount;
        private contactInfo_t[] contacts;
        // for retrieving contact points
        private boolean getContacts;
        private boolean loaded;
        private long mapFileTime;
        private idStr mapName;
        private int maxContacts;
        // models
        private int maxModels;
        private cm_model_s[] models;
        private int numContacts;
        private int numModels;
        // for data pruning
        private int numProcNodes;
        private cm_procNode_s[] procNodes;
        private idMaterial trmMaterial;
        // polygons and brush for trm model
        private cm_polygonRef_s[] trmPolygons;

        public idCollisionModelManagerLocal() {
            this.mapName = new idStr();
            this.trmPolygons = new cm_polygonRef_s[MAX_TRACEMODEL_POLYS];
        }

        @Override// load collision models from a map file
        public void LoadMap(final idMapFile mapFile) {

            if (mapFile == null) {
                common.Error("idCollisionModelManagerLocal::LoadMap: null mapFile");
            }

            // check whether we can keep the current collision map based on the mapName and mapFileTime
            if (loaded) {
                if (mapName.Icmp(mapFile.GetName()) == 0) {
                    if (mapFile.GetFileTime() == mapFileTime) {
                        common.DPrintf("Using loaded version\n");
                        return;
                    }
                    common.DPrintf("Reloading modified map\n");
                }
                FreeMap();
            }

            // clear the collision map
            Clear();

            // models
            maxModels = MAX_SUBMODELS;
            numModels = 0;
            models = new cm_model_s[maxModels + 1]; //cm_model_s.generateArray(maxModels + 1);

            // setup hash to speed up finding shared vertices and edges
            SetupHash();

            // setup trace model structure
            SetupTrmModelStructure();

            // build collision models
            BuildModels(mapFile);

            // save name and time stamp
            mapName = mapFile.GetNameStr();
            mapFileTime = mapFile.GetFileTime();
            loaded = true;

            // shutdown the hash
            ShutdownHash();
        }

        // frees all the collision models
        @Override
        public void FreeMap() {
            int i;

            if (!loaded) {
                Clear();
                return;
            }

            for (i = 0; i < maxModels; i++) {
                if (null == models[i]) {
                    continue;
                }
                FreeModel(models[i]);
            }

            FreeTrmModelStructure();

            models = null;//Mem_Free(models);

            Clear();

            ShutdownHash();
        }

        @Override// get clip handle for model
        public int LoadModel(final idStr modelName, final boolean precache) {
            int handle;

            handle = FindModel(modelName);
            if (handle >= 0) {
                return handle;
            }

            if (numModels >= MAX_SUBMODELS) {
                common.Error("idCollisionModelManagerLocal::LoadModel: no free slots\n");
                return 0;
            }

            // try to load a .cm file
            if (LoadCollisionModelFile(modelName, 0)) {
                handle = FindModel(modelName);
                if (handle >= 0) {
                    return handle;
                } else {
                    common.Warning("idCollisionModelManagerLocal::LoadModel: collision file for '%s' contains different model", modelName);
                }
            }

            // if only precaching .cm files do not waste memory converting render models
            if (precache) {
                return 0;
            }

            // try to load a .ASE or .LWO model and convert it to a collision model
            models[numModels] = LoadRenderModel(modelName);
            if (models[numModels] != null) {
                numModels++;
                return (numModels - 1);
            }

            return 0;
        }

        /*
         ================
         idCollisionModelManagerLocal::SetupTrmModel

         Trace models (item boxes, etc) are converted to collision models on the fly, using the last model slot
         as a reusable temporary buffer
         ================
         */
        @Override// sets up a trace model for collision with other trace models
        public int SetupTrmModel(final idTraceModel trm, idMaterial material) {
            int i, j;
            cm_vertex_s[] vertex;
            cm_edge_s[] edge;
            cm_polygon_s poly;
            cm_model_s model;
            traceModelVert_t[] trmVert;
            traceModelEdge_t[] trmEdge;
            traceModelPoly_t[] trmPoly;

            assert (models != null);

            if (material == null) {
                material = trmMaterial;
            }

            model = models[MAX_SUBMODELS];
            model.node.brushes = null;
            model.node.polygons = null;
            // if not a valid trace model
            if (trm.type == TRM_INVALID || 0 == trm.numPolys) {
                return TRACE_MODEL_HANDLE;
            }
            // vertices
            model.numVertices = trm.numVerts;
            vertex = model.vertices;
            trmVert = trm.verts;
            for (i = 0; i < trm.numVerts; i++) {
                vertex[i] = new cm_vertex_s(trmVert[i]);
                vertex[i].p.oSet(trm.verts[i]);
                vertex[i].sideSet = 0;
            }
            // edges
            model.numEdges = trm.numEdges;
            edge = model.edges;
            trmEdge = trm.edges;
            for (i = 0; i < trm.numEdges; i++) {
                edge[i + 1].vertexNum[0] = trmEdge[i + 1].v[0];
                edge[i + 1].vertexNum[1] = trmEdge[i + 1].v[1];
                edge[i + 1].normal = new idVec3(trmEdge[i].normal);
                edge[i + 1].internal = 0;
                edge[i + 1].sideSet = 0;
//                trmEdge = trm.edges[i + 1];
//                edge = model.edges[i + 1];
//                edge.vertexNum[0] = trmEdge.v[0];
//                edge.vertexNum[1] = trmEdge.v[1];
//                edge.normal.oSet(trmEdge.normal);
//                edge.internal = 0;//false
//                edge.sideSet = 0;
            }
            // polygons
            model.numPolygons = trm.numPolys;
            trmPoly = trm.polys;
            for (i = 0; i < trm.numPolys; i++) {
                poly = trmPolygons[i].p;
                poly.numEdges = trmPoly[i].numEdges;
                for (j = 0; j < trmPoly[i].numEdges; j++) {
                    poly.edges[j] = trmPoly[j].edges[j];
                }
                poly.plane.SetNormal(trmPoly[i].normal);
                poly.plane.SetDist(trmPoly[i].dist);
                poly.bounds.oSet(trmPoly[i].bounds);
                poly.material = material;
                // link polygon at node
                trmPolygons[i].next = model.node.polygons;
                model.node.polygons = trmPolygons[i];
            }
            // if the trace model is convex
            if (trm.isConvex) {
                // setup brush for position test
                trmBrushes[0].b.numPlanes = trm.numPolys;
                for (i = 0; i < trm.numPolys; i++) {
                    trmBrushes[0].b.planes[i] = new idPlane(trmPolygons[i].p.plane);
                }
                trmBrushes[0].b.bounds.oSet(trm.bounds);
                // link brush at node
                trmBrushes[0].next = model.node.brushes;
                model.node.brushes = trmBrushes[0];
            }
            // model bounds
            model.bounds.oSet(trm.bounds);
            // convex
            model.isConvex = trm.isConvex;

            return TRACE_MODEL_HANDLE;
        }

        @Override// create trace model from a collision model, returns true if successful
        public boolean TrmFromModel(final idStr modelName, idTraceModel trm) {
            /*cmHandle_t*/
            int handle;

            handle = LoadModel(modelName, false);
            if (0 == handle) {
                common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s not found.\n", modelName);
                return false;
            }

            return TrmFromModel(models[handle], trm);
        }
//

        @Override// name of the model
        public String GetModelName( /*cmHandle_t*/int model) {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelBounds: invalid model handle\n");
                return "";
            }
            return models[model].name.toString();
        }

        @Override// bounds of the model
        public boolean GetModelBounds( /*cmHandle_t*/int model, idBounds bounds) {

            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelBounds: invalid model handle\n");
                return false;
            }

            bounds.oSet(models[model].bounds);
            return true;
        }

        @Override// all contents flags of brushes and polygons ored together
        public boolean GetModelContents( /*cmHandle_t*/int model, int[] contents) {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelContents: invalid model handle\n");
                return false;
            }

            contents[0] = models[model].contents;

            return true;
        }

        @Override// get the vertex of a model
        public boolean GetModelVertex( /*cmHandle_t*/int model, int vertexNum, idVec3 vertex) {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelVertex: invalid model handle\n");
                return false;
            }

            if (vertexNum < 0 || vertexNum >= models[model].numVertices) {
                common.Printf("idCollisionModelManagerLocal::GetModelVertex: invalid vertex number\n");
                return false;
            }

            vertex.oSet(models[model].vertices[vertexNum].p);

            return true;
        }
        /*
         ===============================================================================

         Writing of collision model file

         ===============================================================================
         */

        @Override// get the edge of a model
        public boolean GetModelEdge( /*cmHandle_t*/int model, int edgeNum, idVec3 start, idVec3 end) {
            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelEdge: invalid model handle\n");
                return false;
            }

            edgeNum = Math.abs(edgeNum);
            if (edgeNum >= models[model].numEdges) {
                common.Printf("idCollisionModelManagerLocal::GetModelEdge: invalid edge number\n");
                return false;
            }

            start.oSet(models[model].vertices[models[model].edges[edgeNum].vertexNum[0]].p);
            end.oSet(models[model].vertices[models[model].edges[edgeNum].vertexNum[1]].p);

            return true;
        }

        @Override// get the polygon of a model
        public boolean GetModelPolygon( /*cmHandle_t*/int model, cm_polygon_s polygonNum, idFixedWinding winding) {
            int i, edgeNum;
            cm_polygon_s poly;

            if (model < 0 || model > MAX_SUBMODELS || model >= numModels || null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::GetModelPolygon: invalid model handle\n");
                return false;
            }

            poly = /* reinterpret_cast<cm_polygon_t **>*/ (polygonNum);
            winding.Clear();
            for (i = 0; i < poly.numEdges; i++) {
                edgeNum = poly.edges[i];
                winding.oPluSet(models[model].vertices[models[model].edges[Math.abs(edgeNum)].vertexNum[INTSIGNBITSET(edgeNum)]].p);
            }

            return true;
        }

        /*
         ===============================================================================

         Collision detection for translational motion

         ===============================================================================
         */

        @Override// translates a trm and reports the first collision if any
        public void Translation(trace_s[] results, final idVec3 start, final idVec3 end, final idTraceModel trm,
                                final idMat3 trmAxis, int contentMask, int model, final idVec3 modelOrigin, final idMat3 modelAxis) {

            int i, j;
            float dist;
            boolean model_rotated, trm_rotated;
            idVec3 dir = new idVec3();
            idMat3 invModelAxis = new idMat3(), tmpAxis;
            cm_trmPolygon_s poly;
            cm_trmEdge_s edge;
            cm_trmVertex_s vert;

            results[0] = new trace_s();

            if (model < 0 || model > MAX_SUBMODELS || model > this.maxModels) {
                common.Printf("idCollisionModelManagerLocal::Translation: invalid model handle\n");
                return;
            }
            if (null == this.models[model]) {
                common.Printf("idCollisionModelManagerLocal::Translation: invalid model\n");
                return;
            }

            // if case special position test
            if (start.oGet(0) == end.oGet(0) && start.oGet(1) == end.oGet(1) && start.oGet(2) == end.oGet(2)) {
                this.ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
                return;
            }

            if (_DEBUG) {
                boolean startsolid = false;
                // test whether or not stuck to begin with
                if (cm_debugCollision.GetBool()) {
                    if (0 == entered && !this.getContacts) {
                        entered = 1;
                        // if already messed up to begin with
                        if ((this.Contents(start, trm, trmAxis, -1, model, modelOrigin, modelAxis) & contentMask) != 0) {
                            startsolid = true;
                        }
                        entered = 0;
                    }
                }
            }

            this.checkCount++;

            tw.trace.fraction = 1.0f;
            tw.trace.c.contents = 0;
            tw.trace.c.type = CONTACT_NONE;
            tw.contents = contentMask;
            tw.isConvex = true;
            tw.rotation = false;
            tw.positionTest = false;
            tw.quickExit = false;
            tw.getContacts = this.getContacts;
            tw.contacts = this.contacts;
            tw.maxContacts = this.maxContacts;
            tw.numContacts = 0;
            tw.model = this.models[model];
            tw.start.oSet(start.oMinus(modelOrigin));
            tw.end.oSet(end.oMinus(modelOrigin));
            tw.dir.oSet(end.oMinus(start));

            model_rotated = modelAxis.IsRotated();
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose();
            }

            // if optimized point trace
            if (null == trm
                    || (trm.bounds.oGet(1).oGet(0) - trm.bounds.oGet(0).oGet(0) <= 0.0f
                    && trm.bounds.oGet(1).oGet(1) - trm.bounds.oGet(0).oGet(1) <= 0.0f
                    && trm.bounds.oGet(1).oGet(2) - trm.bounds.oGet(0).oGet(2) <= 0.0f)) {

                if (model_rotated) {
                    // rotate trace instead of model
                    tw.start.oMulSet(invModelAxis);
                    tw.end.oMulSet(invModelAxis);
                    tw.dir.oMulSet(invModelAxis);
                }

                // trace bounds
                for (i = 0; i < 3; i++) {
                    if (tw.start.oGet(i) < tw.end.oGet(i)) {
                        tw.bounds.oSet(0, i, tw.start.oGet(i) - CM_BOX_EPSILON);
                        tw.bounds.oSet(1, i, tw.end.oGet(i) + CM_BOX_EPSILON);
                    } else {
                        tw.bounds.oSet(0, i, tw.end.oGet(i) - CM_BOX_EPSILON);
                        tw.bounds.oSet(1, i, tw.start.oGet(i) + CM_BOX_EPSILON);
                    }
                }
                tw.extents.oSet(0, tw.extents.oSet(1, tw.extents.oSet(2, CM_BOX_EPSILON)));
                tw.size.Zero();

                // setup trace heart planes
                this.SetupTranslationHeartPlanes();
                tw.maxDistFromHeartPlane1 = CM_BOX_EPSILON;
                tw.maxDistFromHeartPlane2 = CM_BOX_EPSILON;
                // collision with single point
                tw.numVerts = 1;
                tw.vertices[0].p.oSet(tw.start);
                tw.vertices[0].endp.oSet(tw.vertices[0].p.oPlus(tw.dir));
                tw.vertices[0].pl.FromRay(tw.vertices[0].p, tw.dir);
                tw.numEdges = tw.numPolys = 0;
                tw.pointTrace = true;
                // trace through the model
                this.TraceThroughModel(tw);
                // store results
                results[0].oSet(tw.trace);
                results[0].endpos.oSet(start.oPlus(end.oMinus(start).oMultiply(results[0].fraction)));
                results[0].endAxis.oSet(getMat3_identity());

                if (results[0].fraction < 1.0f) {
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results[0].c.normal.oMulSet(modelAxis);
                        results[0].c.point.oMulSet(modelAxis);
                    }
                    results[0].c.point.oPluSet(modelOrigin);
                    results[0].c.dist += modelOrigin.oMultiply(results[0].c.normal);
                }
                this.numContacts = tw.numContacts;
                return;
            }

            // the trace fraction is too inaccurate to describe translations over huge distances
            if (tw.dir.LengthSqr() > Square(CM_MAX_TRACE_DIST)) {
                results[0].fraction = 0.0f;
                results[0].endpos.oSet(start);
                results[0].endAxis.oSet(trmAxis);
                results[0].c.normal.oSet(getVec3_origin());
                results[0].c.material = null;
                results[0].c.point.oSet(start);
                if (session.rw != null) {
                    session.rw.DebugArrow(colorRed, start, end, 1);
                }
                common.Printf("idCollisionModelManagerLocal::Translation: huge translation\n");
                return;
            }

            tw.pointTrace = false;
            tw.size.Clear();

            // setup trm structure
            this.SetupTrm(tw, trm);

            trm_rotated = trmAxis.IsRotated();

            // calculate vertex positions
            if (trm_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    // rotate trm around the start position
                    tw.vertices[i].p.oMulSet(trmAxis);
                }
            }
            for (i = 0; i < tw.numVerts; i++) {
                // set trm at start position
                tw.vertices[i].p.oPluSet(tw.start);
            }
            if (model_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    // rotate trm around model instead of rotating the model
                    tw.vertices[i].p.oMulSet(invModelAxis);
                }
            }

            // add offset to start point
            if (trm_rotated) {
                dir.oSet(trm.offset.oMultiply(trmAxis));
                tw.start.oPluSet(dir);
                tw.end.oPluSet(dir);
            } else {
                tw.start.oPluSet(trm.offset);
                tw.end.oPluSet(trm.offset);
            }
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.oMulSet(invModelAxis);
                tw.end.oMulSet(invModelAxis);
                tw.dir.oMulSet(invModelAxis);
            }

            // rotate trm polygon planes
            if (trm_rotated & model_rotated) {
                tmpAxis = trmAxis.oMultiply(invModelAxis);
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(tmpAxis);
                }
            } else if (trm_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(trmAxis);
                }
            } else if (model_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(invModelAxis);
                }
            }

            // setup trm polygons
            for (i = 0; i < tw.numPolys; i++) {
                poly = tw.polys[i];
                // if the trm poly plane is facing in the movement direction
                dist = poly.plane.Normal().oMultiply(tw.dir);
                if (dist > 0.0f || (!trm.isConvex && dist == 0.0f)) {
                    // this trm poly and it's edges and vertices need to be used for collision
                    poly.used = 1;//true;
                    for (j = 0; j < poly.numEdges; j++) {
                        edge = tw.edges[Math.abs(poly.edges[j])];
                        edge.used = true;
                        tw.vertices[edge.vertexNum[0]].used = true;//true;
                        tw.vertices[edge.vertexNum[1]].used = true;//true;
                    }
                }
            }

            // setup trm vertices
            for (i = 0; i < tw.numVerts; i++) {
                vert = tw.vertices[i];
                if (!vert.used) {
                    continue;
                }
                // get axial trm size after rotations
                tw.size.AddPoint(vert.p.oMinus(tw.start));
                // calculate the end position of each vertex for a full trace
                vert.endp.oSet(vert.p.oPlus(tw.dir));
                // pluecker coordinate for vertex movement line
                vert.pl.FromRay(vert.p, tw.dir);
            }

            // setup trm edges
            for (i = 1; i <= tw.numEdges; i++) {
                edge = tw.edges[i];
                if (!edge.used) {
                    continue;
                }
                // edge start, end and pluecker coordinate
                edge.start.oSet(tw.vertices[edge.vertexNum[0]].p);
                edge.end.oSet(tw.vertices[edge.vertexNum[1]].p);
                edge.pl.FromLine(edge.start, edge.end);
                // calculate normal of plane through movement plane created by the edge
                dir.oSet(edge.start.oMinus(edge.end));
                edge.cross.oSet(0, dir.oGet(0) * tw.dir.oGet(1) - dir.oGet(1) * tw.dir.oGet(0));
                edge.cross.oSet(1, dir.oGet(0) * tw.dir.oGet(2) - dir.oGet(2) * tw.dir.oGet(0));
                edge.cross.oSet(2, dir.oGet(1) * tw.dir.oGet(2) - dir.oGet(2) * tw.dir.oGet(1));
                // bit for vertex sidedness bit cache
                edge.bitNum = (short) i;
            }

            // set trm plane distances
            for (i = 0; i < tw.numPolys; i++) {
                poly = tw.polys[i];
                if (poly.used != 0) {
                    poly.plane.FitThroughPoint(tw.edges[Math.abs(poly.edges[0])].start);
                }
            }

            // bounds for full trace, a little bit larger for epsilons
            for (i = 0; i < 3; i++) {
                if (tw.start.oGet(i) < tw.end.oGet(i)) {
                    tw.bounds.oSet(0, i, tw.start.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                    tw.bounds.oSet(1, i, tw.end.oGet(i) + tw.size.oGet(1).oGet(i) + CM_BOX_EPSILON);
                } else {
                    tw.bounds.oSet(0, i, tw.end.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                    tw.bounds.oSet(1, i, tw.start.oGet(i) + tw.size.oGet(1).oGet(i) + CM_BOX_EPSILON);
                }
                if (Math.abs(tw.size.oGet(0).oGet(i)) > Math.abs(tw.size.oGet(1).oGet(i))) {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(0).oGet(i)) + CM_BOX_EPSILON);
                } else {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(1).oGet(i)) + CM_BOX_EPSILON);
                }
            }

            // setup trace heart planes
            this.SetupTranslationHeartPlanes();
            tw.maxDistFromHeartPlane1 = 0;
            tw.maxDistFromHeartPlane2 = 0;
            // calculate maximum trm vertex distance from both heart planes
            for (i = 0; i < tw.numVerts; i++) {
                vert = tw.vertices[i];
                if (!vert.used) {
                    continue;
                }
                dist = Math.abs(tw.heartPlane1.Distance(vert.p));
                if (dist > tw.maxDistFromHeartPlane1) {
                    tw.maxDistFromHeartPlane1 = dist;
                }
                dist = Math.abs(tw.heartPlane2.Distance(vert.p));
                if (dist > tw.maxDistFromHeartPlane2) {
                    tw.maxDistFromHeartPlane2 = dist;
                }
            }
            // for epsilons
            tw.maxDistFromHeartPlane1 += CM_BOX_EPSILON;
            tw.maxDistFromHeartPlane2 += CM_BOX_EPSILON;

            // trace through the model
            this.TraceThroughModel(tw);

            // if we're getting contacts
            if (tw.getContacts) {
                // move all contacts to world space
                if (model_rotated) {
                    for (i = 0; i < tw.numContacts; i++) {
                        tw.contacts[i].normal.oMulSet(modelAxis);
                        tw.contacts[i].point.oMulSet(modelAxis);
                    }
                }
                if (!modelOrigin.equals(getVec3_origin())) {
                    for (i = 0; i < tw.numContacts; i++) {
                        tw.contacts[i].point.oPluSet(modelOrigin);
                        tw.contacts[i].dist += modelOrigin.oMultiply(tw.contacts[i].normal);
                    }
                }
                this.numContacts = tw.numContacts;
            } else {
                // store results
                results[0].oSet(tw.trace);
                results[0].endpos.oSet(start.oPlus(end.oMinus(start).oMultiply(results[0].fraction)));
                results[0].endAxis.oSet(trmAxis);

                if (results[0].fraction < 1.0f) {
                    // if the fraction is tiny the actual movement could end up zero
                    if (results[0].fraction > 0.0f && results[0].endpos.Compare(start)) {
                        results[0].fraction = 0.0f;
                    }
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results[0].c.normal.oMulSet(modelAxis);
                        results[0].c.point.oMulSet(modelAxis);
                    }
                    results[0].c.point.oPluSet(modelOrigin);
                    results[0].c.dist += modelOrigin.oMultiply(results[0].c.normal);
                }
            }

            if (_DEBUG) {
                // test for missed collisions
                if (cm_debugCollision.GetBool()) {
                    if (0 == entered && !this.getContacts) {
                        entered = 1;
                        // if the trm is stuck in the model
                        if ((this.Contents(results[0].endpos, trm, trmAxis, -1, model, modelOrigin, modelAxis) & contentMask) != 0) {
                            trace_s[] tr = new trace_s[1];

                            // test where the trm is stuck in the model
                            this.Contents(results[0].endpos, trm, trmAxis, -1, model, modelOrigin, modelAxis);
                            // re-run collision detection to find out where it failed
                            this.Translation(tr, start, end, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
                        }
                        entered = 0;
                    }
                }
            }
        }

        @Override// rotates a trm and reports the first collision if any
        public void Rotation(trace_s[] results, final idVec3 start, final idRotation rotation, final idTraceModel trm,
                             final idMat3 trmAxis, int contentMask, int model, final idVec3 modelOrigin, final idMat3 modelAxis) {
            float maxa, stepa, a, lasta;

            results[0] = new trace_s();

            // if special position test
            if (rotation.GetAngle() == 0.0f) {
                this.ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
                return;
            }

            if (_DEBUG) {
                boolean startsolid = false;
                // test whether or not stuck to begin with
                if (cm_debugCollision.GetBool()) {
                    if (0 == entered) {
                        entered = 1;
                        // if already messed up to begin with
                        if ((this.Contents(start, trm, trmAxis, -1, model, modelOrigin, modelAxis) & contentMask) != 0) {
                            startsolid = true;
                        }
                        entered = 0;
                    }
                }
            }

            if (rotation.GetAngle() >= 180.0f || rotation.GetAngle() <= -180.0f) {
                if (rotation.GetAngle() >= 360.0f) {
                    maxa = 360.0f;
                    stepa = 120.0f;            // three steps strictly < 180 degrees
                } else if (rotation.GetAngle() <= -360.0f) {
                    maxa = -360.0f;
                    stepa = -120.0f;        // three steps strictly < 180 degrees
                } else {
                    maxa = rotation.GetAngle();
                    stepa = rotation.GetAngle() * 0.5f;    // two steps strictly < 180 degrees
                }
                for (lasta = 0.0f, a = stepa; Math.abs(a) < Math.abs(maxa) + 1.0f; lasta = a, a += stepa) {
                    // partial rotation
                    this.Rotation180(results, rotation.GetOrigin(), rotation.GetVec(), lasta, a, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
                    // if there is a collision
                    if (results[0].fraction < 1.0f) {
                        // fraction of total rotation
                        results[0].fraction = (lasta + stepa * results[0].fraction) / rotation.GetAngle();
                        return;
                    }
                }
                results[0].fraction = 1.0f;
                return;
            }

            this.Rotation180(results, rotation.GetOrigin(), rotation.GetVec(), 0.0f, rotation.GetAngle(), start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);

            if (_DEBUG) {
                // test for missed collisions
                if (cm_debugCollision.GetBool()) {
                    if (0 == entered) {
                        entered = 1;
                        // if the trm is stuck in the model
                        if ((this.Contents(results[0].endpos, trm, results[0].endAxis, -1, model, modelOrigin, modelAxis) & contentMask) != 0) {
                            trace_s[] tr = new trace_s[1];

                            // test where the trm is stuck in the model
                            this.Contents(results[0].endpos, trm, results[0].endAxis, -1, model, modelOrigin, modelAxis);
                            // re-run collision detection to find out where it failed
                            this.Rotation(tr, start, rotation, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
                        }
                        entered = 0;
                    }
                }
            }
        }

        @Override// returns the contents the trm is stuck in or 0 if the trm is in free space
        public int Contents(final idVec3 start, final idTraceModel trm, final idMat3 trmAxis,
                            int contentMask, int model, final idVec3 modelOrigin, final idMat3 modelAxis) {
            trace_s[] results = {new trace_s()};

            if (model < 0 || model > this.maxModels || model > MAX_SUBMODELS) {
                common.Printf("idCollisionModelManagerLocal::Contents: invalid model handle\n");
                return 0;
            }
            if (null == this.models || null == this.models[model]) {
                common.Printf("idCollisionModelManagerLocal::Contents: invalid model\n");
                return 0;
            }

            return ContentsTrm(results, start, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
        }

        /*
         ===============================================================================

         Retrieving contacts

         ===============================================================================
         */
        @Override// stores all contact points of the trm with the model, returns the number of contacts
        public int Contacts(contactInfo_t[] contacts, final int maxContacts, final idVec3 start, final idVec6 dir, final float depth,
                            final idTraceModel trm, final idMat3 trmAxis, int contentMask, int model, final idVec3 modelOrigin, final idMat3 modelAxis) {
            trace_s[] results = new trace_s[1];
            idVec3 end;

            // same as Translation but instead of storing the first collision we store all collisions as contacts
            this.getContacts = true;
            this.contacts = contacts;
            this.maxContacts = maxContacts;
            this.numContacts = 0;
            end = new idVec3(start.oPlus(dir.SubVec3(0).oMultiply(depth)));
            this.Translation(results, start, end, trm, trmAxis, contentMask, model, modelOrigin, modelAxis);
            if (dir.SubVec3(1).LengthSqr() != 0.0f) {
                // FIXME: rotational contacts
            }
            this.getContacts = false;
            this.maxContacts = 0;

            return this.numContacts;
        }

        @Override// test collision detection
        public void DebugOutput(final idVec3 origin) {
            int i, k, t;
            String buf;
            idAngles boxAngles = new idAngles();
            idMat3 modelAxis = new idMat3(), boxAxis;
            idBounds bounds = new idBounds();
            trace_s[] trace = new trace_s[1];
            Scanner sscanf;

            if (!cm_testCollision.GetBool()) {
                return;
            }

            testend = new idVec3[cm_testTimes.GetInteger()];// = (idVec3 []) Mem_Alloc( cm_testTimes.GetInteger()  );

            if (cm_testReset.GetBool() || (cm_testWalk.GetBool() && !start.Compare(start))) {
                total_translation = total_rotation = 0;
                min_translation = min_rotation = 999999;
                max_translation = max_rotation = -999999;
                num_translation = num_rotation = 0;
                cm_testReset.SetBool(false);
            }

            if (cm_testWalk.GetBool()) {
                start = origin;
                cm_testOrigin.SetString(va("%1.2f %1.2f %1.2f", start.oGet(0), start.oGet(1), start.oGet(2)));
            } else {
                sscanf = new Scanner(cm_testOrigin.GetString());
                start.Set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat());
            }

            sscanf = new Scanner(cm_testBox.GetString());
            bounds.set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat());
//	sscanf( cm_testBox.GetString(), "%f %f %f %f %f %f", &bounds[0][0], &bounds[0][1], &bounds[0][2],
//										&bounds[1][0], &bounds[1][1], &bounds[1][2] );
            sscanf = new Scanner(cm_testBoxRotation.GetString());
            boxAngles.Set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat());
//	sscanf( cm_testBoxRotation.GetString(), "%f %f %f", &boxAngles[0], &boxAngles[1], &boxAngles[2] );
            boxAxis = boxAngles.ToMat3();
            modelAxis.Identity();

            idTraceModel itm = new idTraceModel(bounds);
            idRandom random = new idRandom(0);
            idTimer timer = new idTimer();

            if (cm_testRandomMany.GetBool()) {
                // if many traces in one random direction
                for (i = 0; i < 3; i++) {
                    testend[0].oSet(i, start.oGet(i) + random.CRandomFloat() * cm_testLength.GetFloat());
                }
                for (k = 1; k < cm_testTimes.GetInteger(); k++) {
                    testend[k] = testend[0];
                }
            } else {
                // many traces each in a different random direction
                for (k = 0; k < cm_testTimes.GetInteger(); k++) {
                    for (i = 0; i < 3; i++) {
                        testend[k].oSet(i, start.oGet(i) + random.CRandomFloat() * cm_testLength.GetFloat());
                    }
                }
            }

            // translational collision detection
            timer.Clear();
            timer.Start();
            for (i = 0; i < cm_testTimes.GetInteger(); i++) {
                Translation(trace, start, testend[i], itm, boxAxis, CONTENTS_SOLID | CONTENTS_PLAYERCLIP, cm_testModel.GetInteger(), getVec3_origin(), modelAxis);
            }
            timer.Stop();
            t = (int) timer.Milliseconds();
            if (t < min_translation) {
                min_translation = t;
            }
            if (t > max_translation) {
                max_translation = t;
            }
            num_translation++;
            total_translation += t;
            if (cm_testTimes.GetInteger() > 9999) {
                buf = String.format("%3dK", cm_testTimes.GetInteger() / 1000);
            } else {
                buf = String.format("%4d", cm_testTimes.GetInteger());
            }
            common.Printf("%s translations: %4d milliseconds, (min = %d, max = %d, av = %1.1f)\n", buf, t, min_translation, max_translation, (float) total_translation / num_translation);

            if (cm_testRandomMany.GetBool()) {
                // if many traces in one random direction
                for (i = 0; i < 3; i++) {
                    testend[0].oSet(i, start.oGet(i) + random.CRandomFloat() * cm_testRadius.GetFloat());
                }
                for (k = 1; k < cm_testTimes.GetInteger(); k++) {
                    testend[k] = testend[0];
                }
            } else {
                // many traces each in a different random direction
                for (k = 0; k < cm_testTimes.GetInteger(); k++) {
                    for (i = 0; i < 3; i++) {
                        testend[k].oSet(i, start.oGet(i) + random.CRandomFloat() * cm_testRadius.GetFloat());
                    }
                }
            }

            if (cm_testRotation.GetBool()) {
                // rotational collision detection
                idVec3 vec = new idVec3(random.CRandomFloat(), random.CRandomFloat(), random.RandomFloat());
                vec.Normalize();
                idRotation rotation = new idRotation(getVec3_origin(), vec, cm_testAngle.GetFloat());

                timer.Clear();
                timer.Start();
                for (i = 0; i < cm_testTimes.GetInteger(); i++) {
                    rotation.SetOrigin(testend[i]);
                    Rotation(trace, start, rotation, itm, boxAxis, CONTENTS_SOLID | CONTENTS_PLAYERCLIP, cm_testModel.GetInteger(), getVec3_origin(), modelAxis);
                }
                timer.Stop();
                t = (int) timer.Milliseconds();
                if (t < min_rotation) {
                    min_rotation = t;
                }
                if (t > max_rotation) {
                    max_rotation = t;
                }
                num_rotation++;
                total_rotation += t;
                if (cm_testTimes.GetInteger() > 9999) {
                    buf = String.format("%3dK", cm_testTimes.GetInteger() / 1000);
                } else {
                    buf = String.format("%4d", cm_testTimes.GetInteger());
                }
                common.Printf("%s rotation: %4d milliseconds, (min = %d, max = %d, av = %1.1f)\n", buf, t, min_rotation, max_rotation, (float) total_rotation / num_rotation);
            }

            testend = null;
            sscanf.close();
        }

        @Override// draw a model
        public void DrawModel( /*cmHandle_t*/int handle, final idVec3 modelOrigin,
                                             final idMat3 modelAxis, final idVec3 viewOrigin, final float radius) {

            cm_model_s model;
            idVec3 viewPos;
            Scanner sscanf;

            if (handle < 0 && handle >= numModels) {
                return;
            }

            if (cm_drawColor.IsModified()) {
                sscanf = new Scanner(cm_drawColor.GetString());//, "%f %f %f %f", &cm_color.x, &cm_color.y, &cm_color.z, &cm_color.w );
                cm_color.Set(sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat(), sscanf.nextFloat());
                cm_drawColor.ClearModified();
            }

            model = models[handle];
            viewPos = new idVec3(viewOrigin.oMinus(modelOrigin).oMultiply(modelAxis.Transpose()));
            checkCount++;
            DrawNodePolygons(model, model.node, modelOrigin, modelAxis, viewPos, radius);
        }

        @Override// print model information, use -1 handle for accumulated model info
        public void ModelInfo( /*cmHandle_t*/int model) {
            cm_model_s modelInfo = new cm_model_s();

            if (model == -1) {
                AccumulateModelInfo(modelInfo);
                PrintModelInfo(modelInfo);
                return;
            }
            if (model < 0 || model > MAX_SUBMODELS || model > maxModels) {
                common.Printf("idCollisionModelManagerLocal::ModelInfo: invalid model handle\n");
                return;
            }
            if (null == models[model]) {
                common.Printf("idCollisionModelManagerLocal::ModelInfo: invalid model\n");
                return;
            }

            PrintModelInfo(models[model]);
        }

        @Override// list all loaded models
        public void ListModels() {
            int i, totalMemory;

            totalMemory = 0;
            for (i = 0; i < numModels; i++) {
                common.Printf("%4d: %5d KB   %s\n", i, (models[i].usedMemory >> 10), models[i].name);
                totalMemory += models[i].usedMemory;
            }
            common.Printf("%4d KB in %d models\n", (totalMemory >> 10), numModels);
        }

        @Override// write a collision model file for the map entity
        public boolean WriteCollisionModelForMapEntity(final idMapEntity mapEnt, final String filename, final boolean testTraceModel) {
            idFile fp;
            idStr name;
            cm_model_s model;

            SetupHash();
            model = CollisionModelForMapEntity(mapEnt);
            model.name = name = new idStr(filename);

            name.SetFileExtension(CM_FILE_EXT);

            common.Printf("writing %s\n", name);
            fp = fileSystem.OpenFileWrite(filename, "fs_devpath");
            if (null == fp) {
                common.Printf("idCollisionModelManagerLocal::WriteCollisionModelForMapEntity: Error opening file %s\n", name);
                FreeModel(model);
                return false;
            }

            // write file id and version
            fp.WriteFloatString("%s \"%s\"\n\n", CM_FILEID, CM_FILEVERSION);
            // write the map file crc
            fp.WriteFloatString("%u\n\n", 0);

            // write the collision model
            WriteCollisionModel(fp, model);

            fileSystem.CloseFile(fp);

            if (testTraceModel) {
                idTraceModel trm = new idTraceModel();
                TrmFromModel(model, trm);
            }

            FreeModel(model);

            return true;
        }

        /*
         ================
         idCollisionModelManagerLocal::TranslateEdgeThroughEdge

         calculates fraction of the translation completed at which the edges collide
         ================
         */
        // CollisionMap_translate.cpp
        private boolean TranslateEdgeThroughEdge(idVec3 cross, idPluecker l1, idPluecker l2, CFloat fraction) {
            float d, t;
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
             */
            d = l2.oGet(4) * cross.oGet(0) + l2.oGet(5) * cross.oGet(1) + l2.oGet(2) * cross.oGet(2);

            if (d == 0.0f) {
                fraction.setVal(1.0f);
                // no collision ever
                return false;
            }

            t = -l1.PermutedInnerProduct(l2);
            // if the lines cross each other to begin with
            if (t == 0.0f) {
                fraction.setVal(0.0f);
                return true;
            }
            // fraction of movement at the time the lines cross each other
            fraction.setVal(t / d);
            return true;
        }

        private void TranslateTrmEdgeThroughPolygon(cm_traceWork_s tw, cm_polygon_s poly, cm_trmEdge_s trmEdge) {
            int i, edgeNum;
            float dist, d1, d2;
            CFloat f1 = new CFloat(), f2 = new CFloat();
            idVec3 start, end, normal = new idVec3();
            cm_edge_s edge;
            cm_vertex_s v1, v2;
            idPluecker pl, epsPl = new idPluecker();

            // check edges for a collision
            for (i = 0; i < poly.numEdges; i++) {
                edgeNum = poly.edges[i];
                edge = tw.model.edges[Math.abs(edgeNum)];
                // if this edge is already checked
                if (edge.checkcount == this.checkCount) {
                    continue;
                }
                // can never collide with internal edges
                if (edge.internal != 0) {
                    continue;
                }
                pl = tw.polygonEdgePlueckerCache[i];
                // get the sides at which the trm edge vertices pass the polygon edge
                CM_SetEdgeSidedness(edge, pl, tw.vertices[trmEdge.vertexNum[0]].pl, trmEdge.vertexNum[0]);
                CM_SetEdgeSidedness(edge, pl, tw.vertices[trmEdge.vertexNum[1]].pl, trmEdge.vertexNum[1]);
                // if the trm edge start and end vertex do not pass the polygon edge at different sides
                if (0 == (((edge.side >> trmEdge.vertexNum[0]) ^ (edge.side >> trmEdge.vertexNum[1])) & 1)) {
                    continue;
                }
                // get the sides at which the polygon edge vertices pass the trm edge
                v1 = tw.model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]];
                CM_SetVertexSidedness(v1, tw.polygonVertexPlueckerCache[i], trmEdge.pl, trmEdge.bitNum);
                v2 = tw.model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]];
                CM_SetVertexSidedness(v2, tw.polygonVertexPlueckerCache[i + 1], trmEdge.pl, trmEdge.bitNum);
                // if the polygon edge start and end vertex do not pass the trm edge at different sides
                if (0 == ((v1.side ^ v2.side) & (1 << trmEdge.bitNum))) {
                    continue;
                }
                // if there is no possible collision between the trm edge and the polygon edge
                if (!this.TranslateEdgeThroughEdge(trmEdge.cross, trmEdge.pl, pl, f1)) {
                    continue;
                }
                // if moving away from edge
                if (f1.getVal() < 0.0f) {
                    continue;
                }

                // pluecker coordinate for epsilon expanded edge
                epsPl.FromLine(tw.model.vertices[edge.vertexNum[0]].p.oPlus(edge.normal.oMultiply(CM_CLIP_EPSILON)),
                        tw.model.vertices[edge.vertexNum[1]].p.oPlus(edge.normal.oMultiply(CM_CLIP_EPSILON)));
                // calculate collision fraction with epsilon expanded edge
                if (!this.TranslateEdgeThroughEdge(trmEdge.cross, trmEdge.pl, epsPl, f2)) {
                    continue;
                }
                // if no collision with epsilon edge or moving away from edge
                if (f2.getVal() > 1.0f || f1.getVal() < f2.getVal()) {
                    continue;
                }

                if (f2.getVal() < 0.0f) {
                    f2.setVal(0.0f);
                }

                if (f2.getVal() < tw.trace.fraction) {
                    tw.trace.fraction = f2.getVal();
                    // create plane with normal vector orthogonal to both the polygon edge and the trm edge
                    start = new idVec3(tw.model.vertices[edge.vertexNum[0]].p);
                    end = new idVec3(tw.model.vertices[edge.vertexNum[1]].p);
                    tw.trace.c.normal.oSet((end.oMinus(start)).Cross(trmEdge.end.oMinus(trmEdge.start)));
                    // FIXME: do this normalize when we know the first collision
                    tw.trace.c.normal.Normalize();
                    tw.trace.c.dist = tw.trace.c.normal.oMultiply(start);
                    // make sure the collision plane faces the trace model
                    if (tw.trace.c.normal.oMultiply(trmEdge.start) - tw.trace.c.dist < 0.0f) {
                        tw.trace.c.normal.oSet(tw.trace.c.normal.oNegative());
                        tw.trace.c.dist = -tw.trace.c.dist;
                    }
                    tw.trace.c.contents = poly.contents;
                    tw.trace.c.material = poly.material;
                    tw.trace.c.type = CONTACT_EDGE;
                    tw.trace.c.modelFeature = edgeNum;
                    tw.trace.c.trmFeature = Arrays.asList(tw.edges).indexOf(trmEdge);
                    // calculate collision point
                    normal.oSet(0, trmEdge.cross.oGet(2));
                    normal.oSet(1, -trmEdge.cross.oGet(1));
                    normal.oSet(2, trmEdge.cross.oGet(0));
                    dist = normal.oMultiply(trmEdge.start);
                    d1 = normal.oMultiply(start) - dist;
                    d2 = normal.oMultiply(end) - dist;
                    f1.setVal(d1 / (d1 - d2));
                    //assert( f1 >= 0.0f && f1 <= 1.0f );
                    tw.trace.c.point.oSet(start.oPlus(end.oMinus(start).oMultiply(f1.getVal())));
                    // if retrieving contacts
                    if (tw.getContacts) {
                        CM_AddContact(tw);
                    }
                }
            }
        }

        private void TranslateTrmVertexThroughPolygon(cm_traceWork_s tw, cm_polygon_s poly, cm_trmVertex_s v, final int bitNum) {
            int i, edgeNum;
            float f;
            cm_edge_s edge;

            f = CM_TranslationPlaneFraction(poly.plane, v.p, v.endp);
            if (f < tw.trace.fraction) {

                for (i = 0; i < poly.numEdges; i++) {
                    edgeNum = poly.edges[i];
                    edge = tw.model.edges[Math.abs(edgeNum)];
                    CM_SetEdgeSidedness(edge, tw.polygonEdgePlueckerCache[i], v.pl, bitNum);
                    if ((INTSIGNBITSET(edgeNum) ^ ((edge.side >> bitNum) & 1)) != 0) {
                        return;
                    }
                }
                if (f < 0.0f) {
                    f = 0.0f;
                }
                tw.trace.fraction = f;
                // collision plane is the polygon plane
                tw.trace.c.normal.oSet(poly.plane.Normal());
                tw.trace.c.dist = poly.plane.Dist();
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_TRMVERTEX;
                tw.trace.c.modelFeature = poly.hashCode();
                tw.trace.c.trmFeature = Arrays.asList(tw.vertices).indexOf(v);
                tw.trace.c.point.oSet(v.p.oPlus((v.endp.oMinus(v.p)).oMultiply(tw.trace.fraction)));
                // if retrieving contacts
                if (tw.getContacts) {
                    CM_AddContact(tw);
                    // no need to store the trm vertex more than once as a contact
                    v.used = false;//false;
                }
            }
        }

        private void TranslatePointThroughPolygon(cm_traceWork_s tw, cm_polygon_s poly, cm_trmVertex_s v) {
            int i, edgeNum;
            float f;
            cm_edge_s edge;
            idPluecker pl = new idPluecker();

            f = CM_TranslationPlaneFraction(poly.plane, v.p, v.endp);
            if (f < tw.trace.fraction) {

                for (i = 0; i < poly.numEdges; i++) {
                    edgeNum = poly.edges[i];
                    edge = tw.model.edges[Math.abs(edgeNum)];
                    // if we didn't yet calculate the sidedness for this edge
                    if (edge.checkcount != this.checkCount) {
                        float fl;
                        edge.checkcount = this.checkCount;
                        pl.FromLine(tw.model.vertices[edge.vertexNum[0]].p, tw.model.vertices[edge.vertexNum[1]].p);
                        fl = v.pl.PermutedInnerProduct(pl);
                        edge.side = FLOATSIGNBITSET(fl);
                    }
                    // if the point passes the edge at the wrong side
                    //if ( (edgeNum > 0) == edge.side ) {
                    if ((INTSIGNBITSET(edgeNum) ^ edge.side) != 0) {
                        return;
                    }
                }
                if (f < 0.0f) {
                    f = 0.0f;
                }
                tw.trace.fraction = f;
                // collision plane is the polygon plane
                tw.trace.c.normal.oSet(poly.plane.Normal());
                tw.trace.c.dist = poly.plane.Dist();
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_TRMVERTEX;
                tw.trace.c.modelFeature = poly.hashCode(); // need to check
                tw.trace.c.trmFeature = Arrays.asList(tw.vertices).indexOf(v);
                tw.trace.c.point.oSet(v.p.oPlus((v.endp.oMinus(v.p)).oMultiply(tw.trace.fraction)));
                // if retrieving contacts
                if (tw.getContacts) {
                    CM_AddContact(tw);
                    // no need to store the trm vertex more than once as a contact
                    v.used = false;//false;
                }
            }
        }

        private void TranslateVertexThroughTrmPolygon(cm_traceWork_s tw, cm_trmPolygon_s trmpoly, cm_polygon_s poly, cm_vertex_s v, idVec3 endp, idPluecker pl) {
            int i, edgeNum;
            float f;
            cm_trmEdge_s edge;

            f = CM_TranslationPlaneFraction(trmpoly.plane, v.p, endp);
            if (f < tw.trace.fraction) {

                for (i = 0; i < trmpoly.numEdges; i++) {
                    edgeNum = trmpoly.edges[i];
                    edge = tw.edges[Math.abs(edgeNum)];

                    CM_SetVertexSidedness(v, pl, edge.pl, edge.bitNum);
                    if ((INTSIGNBITSET(edgeNum) ^ ((v.side >> edge.bitNum) & 1)) != 0) {
                        return;
                    }
                }
                if (f < 0.0f) {
                    f = 0.0f;
                }
                tw.trace.fraction = f;
                // collision plane is the inverse trm polygon plane
                tw.trace.c.normal.oSet(trmpoly.plane.Normal().oNegative());
                tw.trace.c.dist = -trmpoly.plane.Dist();
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_MODELVERTEX;
                tw.trace.c.modelFeature = poly.hashCode();
                tw.trace.c.trmFeature = Arrays.asList(tw.vertices).indexOf(v);
                tw.trace.c.point.oSet(v.p.oPlus((endp.oMinus(v.p)).oMultiply(tw.trace.fraction)));
                // if retrieving contacts
                if (tw.getContacts) {
                    CM_AddContact(tw);
                }
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::TranslateTrmThroughPolygon

         returns true if the polygon blocks the complete translation
         ================
         */
        private boolean TranslateTrmThroughPolygon(cm_traceWork_s tw, cm_polygon_s p) {
            int i, j, k, edgeNum;
            float fraction, d;
            idVec3 endp;
            idPluecker pl;
            cm_trmVertex_s bv;
            cm_trmEdge_s be;
            cm_trmPolygon_s bp;
            cm_vertex_s v;
            cm_edge_s e;

            // if already checked this polygon
            if (p.checkcount == this.checkCount) {
                return false;
            }
            p.checkcount = this.checkCount;

            // if this polygon does not have the right contents behind it
            if (0 == (p.contents & tw.contents)) {
                return false;
            }

            // if the the trace bounds do not intersect the polygon bounds
            if (!tw.bounds.IntersectsBounds(p.bounds)) {
                return false;
            }

            // only collide with the polygon if approaching at the front
            if ((p.plane.Normal().oMultiply(tw.dir)) > 0.0f) {
                return false;
            }

            // if the polygon is too far from the first heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane1);
            if (Math.abs(d) > tw.maxDistFromHeartPlane1) {
                return false;
            }

            // if the polygon is too far from the second heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane2);
            if (Math.abs(d) > tw.maxDistFromHeartPlane2) {
                return false;
            }
            fraction = tw.trace.fraction;

            // fast point trace
            if (tw.pointTrace) {
                this.TranslatePointThroughPolygon(tw, p, tw.vertices[0]);
            } else {

                // trace bounds should cross polygon plane
                switch (tw.bounds.PlaneSide(p.plane)) {
                    case PLANESIDE_CROSS:
                        break;
                    case PLANESIDE_FRONT:
                        if (tw.model.isConvex) {
                            tw.quickExit = true;
                            return true;
                        }
                    default:
                        return false;
                }

                // calculate pluecker coordinates for the polygon edges and polygon vertices
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    e = tw.model.edges[Math.abs(edgeNum)];
                    // reset sidedness cache if this is the first time we encounter this edge during this trace
                    if (e.checkcount != this.checkCount) {
                        e.sideSet = 0;
                    }
                    // pluecker coordinate for edge
                    tw.polygonEdgePlueckerCache[i].FromLine(tw.model.vertices[e.vertexNum[0]].p,
                            tw.model.vertices[e.vertexNum[1]].p);

                    v = tw.model.vertices[e.vertexNum[INTSIGNBITSET(edgeNum)]];
                    // reset sidedness cache if this is the first time we encounter this vertex during this trace
                    if (v.checkcount != this.checkCount) {
                        v.sideSet = 0;
                    }
                    // pluecker coordinate for vertex movement vector
                    tw.polygonVertexPlueckerCache[i].FromRay(v.p, tw.dir.oNegative());
                }
                // copy first to last so we can easily cycle through for the edges
                tw.polygonVertexPlueckerCache[p.numEdges].Set(tw.polygonVertexPlueckerCache[0]);

                // trace trm vertices through polygon
                for (i = 0; i < tw.numVerts; i++) {
                    bv = tw.vertices[i];
                    if (bv.used) {
                        this.TranslateTrmVertexThroughPolygon(tw, p, bv, i);
                    }
                }

                // trace trm edges through polygon
                for (i = 1; i <= tw.numEdges; i++) {
                    be = tw.edges[i];
                    if (be.used) {
                        this.TranslateTrmEdgeThroughPolygon(tw, p, be);
                    }
                }

                // trace all polygon vertices through the trm
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    e = tw.model.edges[Math.abs(edgeNum)];

                    if (e.checkcount == this.checkCount) {
                        continue;
                    }
                    // set edge check count
                    e.checkcount = this.checkCount;
                    // can never collide with internal edges
                    if (e.internal != 0) {
                        continue;
                    }
                    // got to check both vertices because we skip internal edges
                    for (k = 0; k < 2; k++) {

                        v = tw.model.vertices[e.vertexNum[k ^ INTSIGNBITSET(edgeNum)]];
                        // if this vertex is already checked
                        if (v.checkcount == this.checkCount) {
                            continue;
                        }
                        // set vertex check count
                        v.checkcount = this.checkCount;

                        // if the vertex is outside the trace bounds
                        if (!tw.bounds.ContainsPoint(v.p)) {
                            continue;
                        }

                        // vertex end point after movement
                        endp = new idVec3(v.p.oMinus(tw.dir));
                        // pluecker coordinate for vertex movement vector
                        pl = tw.polygonVertexPlueckerCache[i + k];

                        for (j = 0; j < tw.numPolys; j++) {
                            bp = tw.polys[j];
                            if (bp.used != 0) {
                                this.TranslateVertexThroughTrmPolygon(tw, bp, p, v, endp, pl);
                            }
                        }
                    }
                }
            }

            // if there was a collision with this polygon and we are not retrieving contacts
            if (tw.trace.fraction < fraction && !tw.getContacts) {
                fraction = tw.trace.fraction;
                endp = new idVec3(tw.start.oPlus(tw.dir.oMultiply(fraction)));
                // decrease bounds
                for (i = 0; i < 3; i++) {
                    if (tw.start.oGet(i) < endp.oGet(i)) {
                        tw.bounds.oSet(0, i, tw.start.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                        tw.bounds.oSet(1, i, endp.oGet(i) + tw.size.oGet(1).oGet(i) - CM_BOX_EPSILON);
                    } else {
                        tw.bounds.oSet(0, i, endp.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                        tw.bounds.oSet(1, i, tw.start.oGet(i) + tw.size.oGet(1).oGet(i) - CM_BOX_EPSILON);
                    }
                }
            }

            return (tw.trace.fraction == 0.0f);
        }

        private void SetupTranslationHeartPlanes() {
            idVec3 dir, normal1 = new idVec3(), normal2 = new idVec3();

            // calculate trace heart planes
            dir = new idVec3(idCollisionModelManagerLocal.tw.dir);
            dir.Normalize();
            dir.NormalVectors(normal1, normal2);
            idCollisionModelManagerLocal.tw.heartPlane1.SetNormal(normal1);
            idCollisionModelManagerLocal.tw.heartPlane1.FitThroughPoint(idCollisionModelManagerLocal.tw.start);
            idCollisionModelManagerLocal.tw.heartPlane2.SetNormal(normal2);
            idCollisionModelManagerLocal.tw.heartPlane2.FitThroughPoint(idCollisionModelManagerLocal.tw.start);
        }

        private void SetupTrm(cm_traceWork_s tw, final idTraceModel trm) {
            int i, j;

            // vertices
            tw.numVerts = trm.numVerts;
            for (i = 0; i < trm.numVerts; i++) {
                tw.vertices[i].p.oSet(trm.verts[i]);
                tw.vertices[i].used = false;//false;
            }
            // edges
            tw.numEdges = trm.numEdges;
            for (i = 1; i <= trm.numEdges; i++) {
                tw.edges[i].vertexNum[0] = trm.edges[i].v[0];
                tw.edges[i].vertexNum[1] = trm.edges[i].v[1];
                tw.edges[i].used = false;
            }
            // polygons
            tw.numPolys = trm.numPolys;
            for (i = 0; i < trm.numPolys; i++) {
                tw.polys[i].numEdges = trm.polys[i].numEdges;
                System.arraycopy(trm.polys[i].edges, 0, tw.polys[i].edges, 0, trm.polys[i].numEdges);
                tw.polys[i].plane.SetNormal(trm.polys[i].normal);
                tw.polys[i].used = 0;//false;
            }
            // is the trace model convex or not
            tw.isConvex = trm.isConvex;
        }

        /*
         ================
         idCollisionModelManagerLocal::CollisionBetweenEdgeBounds

         verifies if the collision of two edges occurs between the edge bounds
         also calculates the collision point and collision plane normal if the collision occurs between the bounds
         ================
         */
        // CollisionMap_rotate.cpp
        private boolean CollisionBetweenEdgeBounds(cm_traceWork_s tw, final idVec3 va, final idVec3 vb, final idVec3 vc,
                                                   final idVec3 vd, CFloat tanHalfAngle, idVec3 collisionPoint, idVec3 collisionNormal) {
            float d1, d2, d;
            idVec3 at, bt, dir, dir1, dir2;
            idPluecker pl1 = new idPluecker(), pl2 = new idPluecker();

            at = new idVec3(va);
            bt = new idVec3(vb);
            if (tanHalfAngle.getVal() != 0.0f) {
                CM_RotateEdge(at, bt, tw.origin, tw.axis, tanHalfAngle);
            }

            dir1 = (at.oMinus(tw.origin)).Cross(tw.axis);
            dir2 = (bt.oMinus(tw.origin)).Cross(tw.axis);
            if (dir1.oMultiply(dir1) > dir2.oMultiply(dir2)) {
                dir = dir1;
            } else {
                dir = dir2;
            }
            if (tw.angle < 0.0f) {
                dir = dir.oNegative();
            }

            pl1.FromLine(at, bt);
            pl2.FromRay(vc, dir);
            d1 = pl1.PermutedInnerProduct(pl2);
            pl2.FromRay(vd, dir);
            d2 = pl1.PermutedInnerProduct(pl2);
            if ((d1 > 0.0f && d2 > 0.0f) || (d1 < 0.0f && d2 < 0.0f)) {
                return false;
            }

            pl1.FromLine(vc, vd);
            pl2.FromRay(at, dir);
            d1 = pl1.PermutedInnerProduct(pl2);
            pl2.FromRay(bt, dir);
            d2 = pl1.PermutedInnerProduct(pl2);
            if ((d1 > 0.0f && d2 > 0.0f) || (d1 < 0.0f && d2 < 0.0f)) {
                return false;
            }

            // collision point on the edge at-bt
            dir1 = (vd.oMinus(vc)).Cross(dir);
            d = dir1.oMultiply(vc);
            d1 = dir1.oMultiply(at) - d;
            d2 = dir1.oMultiply(bt) - d;
            if (d1 == d2) {
                return false;
            }
            collisionPoint.oSet(at.oPlus((bt.oMinus(at)).oMultiply((d1 / (d1 - d2)))));

            // normal is cross product of the rotated edge va-vb and the edge vc-vd
            collisionNormal.Cross(bt.oMinus(at), vd.oMinus(vc));

            return true;
        }

        /*
         ================
         idCollisionModelManagerLocal::RotateEdgeThroughEdge

         calculates the tangent of half the rotation angle at which the edges collide
         ================
         */
        private boolean RotateEdgeThroughEdge(cm_traceWork_s tw, final idPluecker pl1,
                                              final idVec3 vc, final idVec3 vd, final float minTan, CFloat tanHalfAngle) {
            double v0, v1, v2, a, b, c, d, sqrtd, q, frac1, frac2;
            idVec3 ct, dt;
            idPluecker pl2 = new idPluecker();

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

             */
            tanHalfAngle.setVal(tw.maxTan);

            // transform rotation axis to z-axis
            ct = new idVec3(vc.oMinus(tw.origin)).oMultiply(tw.matrix);
            dt = new idVec3(vd.oMinus(tw.origin)).oMultiply(tw.matrix);

            pl2.FromLine(ct, dt);

            v0 = pl2.oGet(0) * pl1.oGet(4) + pl2.oGet(4) * pl1.oGet(0);
            v1 = pl2.oGet(1) * pl1.oGet(2) - pl2.oGet(2) * pl1.oGet(1) + pl2.oGet(5) * pl1.oGet(3) - pl2.oGet(3) * pl1.oGet(5);
            v2 = pl2.oGet(1) * pl1.oGet(5) + pl2.oGet(2) * pl1.oGet(3) + pl2.oGet(5) * pl1.oGet(1) + pl2.oGet(3) * pl1.oGet(2);

            a = v0 - v2;
            b = v1;
            c = v0 + v2;
            if (a == 0.0f) {
                if (b == 0.0f) {
                    return false;
                }
                frac1 = -c / (2.0f * b);
                frac2 = 1e10;    // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a;
                if (d <= 0.0f) {
                    return false;
                }
                sqrtd = Math.sqrt(d);
                if (b > 0.0f) {
                    q = -b + sqrtd;
                } else {
                    q = -b - sqrtd;
                }
                frac1 = q / a;
                frac2 = c / q;
            }

            if (tw.angle < 0.0f) {
                frac1 = -frac1;
                frac2 = -frac2;
            }

            // get smallest tangent for which a collision occurs
            if (frac1 >= minTan && frac1 < tanHalfAngle.getVal()) {
                tanHalfAngle.setVal((float) frac1);
            }
            if (frac2 >= minTan && frac2 < tanHalfAngle.getVal()) {
                tanHalfAngle.setVal((float) frac2);
            }

            if (tw.angle < 0.0f) {
                tanHalfAngle.setVal(-tanHalfAngle.getVal());
            }

            return true;
        }

        /*
         ================
         idCollisionModelManagerLocal::EdgeFurthestFromEdge

         calculates the direction of motion at the initial position, where dir < 0 means the edges move towards each other
         if the edges move away from each other the tangent of half the rotation angle at which
         the edges are furthest apart is also calculated
         ================
         */
        private boolean EdgeFurthestFromEdge(cm_traceWork_s tw, final idPluecker pl1,
                                             final idVec3 vc, final idVec3 vd, CFloat tanHalfAngle, CFloat dir) {
            double v0, v1, v2, a, b, c, d, sqrtd, q, frac1, frac2;
            idVec3 ct, dt;
            idPluecker pl2 = new idPluecker();

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

             */
            tanHalfAngle.setVal(0.0f);

            // transform rotation axis to z-axis
            ct = new idVec3((vc.oMinus(tw.origin)).oMultiply(tw.matrix));
            dt = new idVec3((vd.oMinus(tw.origin)).oMultiply(tw.matrix));

            pl2.FromLine(ct, dt);

            v0 = pl2.oGet(0) * pl1.oGet(4) + pl2.oGet(4) * pl1.oGet(0);
            v1 = pl2.oGet(1) * pl1.oGet(2) - pl2.oGet(2) * pl1.oGet(1) + pl2.oGet(5) * pl1.oGet(3) - pl2.oGet(3) * pl1.oGet(5);
            v2 = pl2.oGet(1) * pl1.oGet(5) + pl2.oGet(2) * pl1.oGet(3) + pl2.oGet(5) * pl1.oGet(1) + pl2.oGet(3) * pl1.oGet(2);

            // get the direction of motion at the initial position
            c = v0 + v2;
            if (tw.angle > 0.0f) {
                if (c > 0.0f) {
                    dir.setVal((float) v1);
                } else {
                    dir.setVal((float) -v1);
                }
            } else {
                if (c > 0.0f) {
                    dir.setVal((float) -v1);
                } else {
                    dir.setVal((float) v1);
                }
            }
            // negative direction means the edges move towards each other at the initial position
            if (dir.getVal() <= 0.0f) {
                return true;
            }

            a = -v1;
            b = -v2;
            c = v1;
            if (a == 0.0f) {
                if (b == 0.0f) {
                    return false;
                }
                frac1 = -c / (2.0f * b);
                frac2 = 1e10;    // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a;
                if (d <= 0.0f) {
                    return false;
                }
                sqrtd = Math.sqrt(d);
                if (b > 0.0f) {
                    q = -b + sqrtd;
                } else {
                    q = -b - sqrtd;
                }
                frac1 = q / a;
                frac2 = c / q;
            }

            if (tw.angle < 0.0f) {
                frac1 = -frac1;
                frac2 = -frac2;
            }

            if (frac1 < 0.0f && frac2 < 0.0f) {
                return false;
            }

            if (frac1 > frac2) {
                tanHalfAngle.setVal((float) frac1);
            } else {
                tanHalfAngle.setVal((float) frac2);
            }

            if (tw.angle < 0.0f) {
                tanHalfAngle.setVal(-tanHalfAngle.getVal());
            }

            return true;
        }

        private void RotateTrmEdgeThroughPolygon(cm_traceWork_s tw, cm_polygon_s poly, cm_trmEdge_s trmEdge) {
            int i, j, edgeNum;
            float f1, f2;
            CFloat startTan = new CFloat();
            CFloat dir = new CFloat();
            CFloat tanHalfAngle = new CFloat();
            cm_edge_s edge;
            cm_vertex_s v1, v2;
            idVec3 collisionPoint = new idVec3(), collisionNormal = new idVec3(), origin, epsDir;
            idPluecker epsPl = new idPluecker();
            idBounds bounds = new idBounds();

            // if the trm is convex and the rotation axis intersects the trm
            if (tw.isConvex && tw.axisIntersectsTrm) {
                // if both points are behind the polygon the edge cannot collide within a 180 degrees rotation
                if ((tw.vertices[trmEdge.vertexNum[0]].polygonSide & tw.vertices[trmEdge.vertexNum[1]].polygonSide) != 0) {
                    return;
                }
            }

            // if the trace model edge rotation bounds do not intersect the polygon bounds
            if (!trmEdge.rotationBounds.IntersectsBounds(poly.bounds)) {
                return;
            }

            // edge rotation bounds should cross polygon plane
            if (trmEdge.rotationBounds.PlaneSide(poly.plane) != SIDE_CROSS) {
                return;
            }

            // check edges for a collision
            for (i = 0; i < poly.numEdges; i++) {
                edgeNum = poly.edges[i];
                edge = tw.model.edges[Math.abs(edgeNum)];

                // if this edge is already checked
                if (edge.checkcount == this.checkCount) {
                    continue;
                }

                // can never collide with internal edges
                if (edge.internal != 0) {
                    continue;
                }

                v1 = tw.model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]];
                v2 = tw.model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]];

                // edge bounds
                for (j = 0; j < 3; j++) {
                    if (v1.p.oGet(j) > v2.p.oGet(j)) {
                        bounds.oSet(0, j, v2.p.oGet(j));
                        bounds.oSet(1, j, v1.p.oGet(j));
                    } else {
                        bounds.oSet(0, j, v1.p.oGet(j));
                        bounds.oSet(1, j, v2.p.oGet(j));
                    }
                }

                // if the trace model edge rotation bounds do not intersect the polygon edge bounds
                if (!trmEdge.rotationBounds.IntersectsBounds(bounds)) {
                    continue;
                }

                f1 = trmEdge.pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i]);

                // pluecker coordinate for epsilon expanded edge
                epsDir = new idVec3(edge.normal.oMultiply(CM_CLIP_EPSILON + CM_PL_RANGE_EPSILON));
                epsPl.FromLine(tw.model.vertices[edge.vertexNum[0]].p.oPlus(epsDir),
                        tw.model.vertices[edge.vertexNum[1]].p.oPlus(epsDir));

                f2 = trmEdge.pl.PermutedInnerProduct(epsPl);

                // if the rotating edge is inbetween the polygon edge and the epsilon expanded edge
                if ((f1 < 0.0f && f2 > 0.0f) || (f1 > 0.0f && f2 < 0.0f)) {

                    if (!EdgeFurthestFromEdge(tw, trmEdge.plzaxis, v1.p, v2.p, startTan, dir)) {
                        continue;
                    }

                    if (dir.getVal() <= 0.0f) {
                        // moving towards the polygon edge so stop immediately
                        tanHalfAngle.setVal(0.0f);
                    } else if (Math.abs(startTan.getVal()) >= tw.maxTan) {
                        // never going to get beyond the start tangent during the current rotation
                        continue;
                    } else {
                        // collide with the epsilon expanded edge
                        if (!RotateEdgeThroughEdge(tw, trmEdge.plzaxis, v1.p.oPlus(epsDir), v2.p.oPlus(epsDir), Math.abs(startTan.getVal()), tanHalfAngle)) {
                            tanHalfAngle.setVal(startTan.getVal());
                        }
                    }
                } else {
                    // collide with the epsilon expanded edge
                    epsDir = new idVec3(edge.normal.oMultiply(CM_CLIP_EPSILON));
                    if (!RotateEdgeThroughEdge(tw, trmEdge.plzaxis, v1.p.oPlus(epsDir), v2.p.oPlus(epsDir), 0.0f, tanHalfAngle)) {
                        continue;
                    }
                }

                if (Math.abs(tanHalfAngle.getVal()) >= tw.maxTan) {
                    continue;
                }

                // check if the collision is between the edge bounds
                if (!CollisionBetweenEdgeBounds(tw, trmEdge.start, trmEdge.end, v1.p, v2.p, tanHalfAngle, collisionPoint, collisionNormal)) {
                    continue;
                }

                // allow rotation if the rotation axis goes through the collisionPoint
                origin = new idVec3(tw.origin.oPlus(tw.axis.oMultiply(tw.axis.oMultiply(collisionPoint.oMinus(tw.origin)))));
                if ((collisionPoint.oMinus(origin)).LengthSqr() < ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                    continue;
                }

                // fill in trace structure
                tw.maxTan = Math.abs(tanHalfAngle.getVal());
                tw.trace.c.normal.oSet(collisionNormal);
                tw.trace.c.normal.Normalize();
                tw.trace.c.dist = tw.trace.c.normal.oMultiply(v1.p);
                // make sure the collision plane faces the trace model
                if ((tw.trace.c.normal.oMultiply(trmEdge.start)) - tw.trace.c.dist < 0) {
                    tw.trace.c.normal.oSet(tw.trace.c.normal.oNegative());
                    tw.trace.c.dist = -tw.trace.c.dist;
                }
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_EDGE;
                tw.trace.c.modelFeature = edgeNum;
                tw.trace.c.trmFeature = Arrays.asList(tw.edges).indexOf(trmEdge);
                tw.trace.c.point.oSet(collisionPoint);
                // if no collision can be closer
                if (tw.maxTan == 0.0f) {
                    break;
                }
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::RotatePointThroughPlane

         calculates the tangent of half the rotation angle at which the point collides with the plane
         ================
         */
        private boolean RotatePointThroughPlane(final cm_traceWork_s tw, final idVec3 point, final idPlane plane,
                                                final float angle, final float minTan, CFloat tanHalfAngle) {
            double v0, v1, v2, a, b, c, d, sqrtd, q, frac1, frac2;
            idVec3 p, normal;

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

             */
            tanHalfAngle.setVal(tw.maxTan);

            // transform rotation axis to z-axis
            p = new idVec3((point.oMinus(tw.origin)).oMultiply(tw.matrix));
            d = (plane.oGet(3) + plane.Normal().oMultiply(tw.origin));
            normal = new idVec3(plane.Normal().oMultiply(tw.matrix));

            v0 = normal.oGet(2) * p.oGet(2) + d;
            v1 = normal.oGet(0) * p.oGet(1) - normal.oGet(1) * p.oGet(0);
            v2 = normal.oGet(0) * p.oGet(0) + normal.oGet(1) * p.oGet(1);

            a = v0 - v2;
            b = v1;
            c = v0 + v2;
            if (a == 0.0f) {
                if (b == 0.0f) {
                    return false;
                }
                frac1 = -c / (2.0f * b);
                frac2 = 1e10;    // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a;
                if (d <= 0.0f) {
                    return false;
                }
                sqrtd = Math.sqrt(d);
                if (b > 0.0f) {
                    q = -b + sqrtd;
                } else {
                    q = -b - sqrtd;
                }
                frac1 = q / a;
                frac2 = c / q;
            }

            if (angle < 0.0f) {
                frac1 = -frac1;
                frac2 = -frac2;
            }

            // get smallest tangent for which a collision occurs
            if (frac1 >= minTan && frac1 < tanHalfAngle.getVal()) {
                tanHalfAngle.setVal((float) frac1);
            }
            if (frac2 >= minTan && frac2 < tanHalfAngle.getVal()) {
                tanHalfAngle.setVal((float) frac2);
            }

            if (angle < 0.0f) {
                tanHalfAngle.setVal(-tanHalfAngle.getVal());
            }

            return true;
        }

        /*
         ================
         idCollisionModelManagerLocal::PointFurthestFromPlane

         calculates the direction of motion at the initial position, where dir < 0 means the point moves towards the plane
         if the point moves away from the plane the tangent of half the rotation angle at which
         the point is furthest away from the plane is also calculated
         ================
         */
        private boolean PointFurthestFromPlane(final cm_traceWork_s tw, final idVec3 point, final idPlane plane,
                                               final float angle, CFloat tanHalfAngle, CFloat dir) {

            double v1, v2, a, b, c, d, sqrtd, q, frac1, frac2;
            idVec3 p, normal;

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

             */
            tanHalfAngle.setVal(0.0f);

            // transform rotation axis to z-axis
            p = new idVec3((point.oMinus(tw.origin)).oMultiply(tw.matrix));
            normal = new idVec3(plane.Normal().oMultiply(tw.matrix));

            v1 = normal.oGet(0) * p.oGet(1) - normal.oGet(1) * p.oGet(0);
            v2 = normal.oGet(0) * p.oGet(0) + normal.oGet(1) * p.oGet(1);

            // the point will always start at the front of the plane, therefore v0 + v2 > 0 is always true
            if (angle < 0.0f) {
                dir.setVal((float) -v1);
            } else {
                dir.setVal((float) v1);
            }
            // negative direction means the point moves towards the plane at the initial position
            if (dir.getVal() <= 0.0f) {
                return true;
            }

            a = -v1;
            b = -v2;
            c = v1;
            if (a == 0.0f) {
                if (b == 0.0f) {
                    return false;
                }
                frac1 = -c / (2.0f * b);
                frac2 = 1e10;    // = tan( idMath::HALF_PI )
            } else {
                d = b * b - c * a;
                if (d <= 0.0f) {
                    return false;
                }
                sqrtd = Math.sqrt(d);
                if (b > 0.0f) {
                    q = -b + sqrtd;
                } else {
                    q = -b - sqrtd;
                }
                frac1 = q / a;
                frac2 = c / q;
            }

            if (angle < 0.0f) {
                frac1 = -frac1;
                frac2 = -frac2;
            }

            if (frac1 < 0.0f && frac2 < 0.0f) {
                return false;
            }

            if (frac1 > frac2) {
                tanHalfAngle.setVal((float) frac1);
            } else {
                tanHalfAngle.setVal((float) frac2);
            }

            if (angle < 0.0f) {
                tanHalfAngle.setVal(-tanHalfAngle.getVal());
            }

            return true;
        }

        /*
         ===============================================================================

         Contents test

         ===============================================================================
         */

        private boolean RotatePointThroughEpsilonPlane(final cm_traceWork_s tw, final idVec3 point, final idVec3 endPoint,
                                                       final idPlane plane, final float angle, final idVec3 origin,
                                                       CFloat tanHalfAngle, idVec3 collisionPoint, idVec3 endDir) {
            float d;
            CFloat dir = new CFloat();
            CFloat startTan = new CFloat();
            idVec3 vec, startDir;
            idPlane epsPlane;

            // epsilon expanded plane
            epsPlane = new idPlane(plane);
            epsPlane.SetDist(epsPlane.Dist() + CM_CLIP_EPSILON);

            // if the rotation sphere at the rotation origin is too far away from the polygon plane
            d = epsPlane.Distance(origin);
            vec = new idVec3(point.oMinus(origin));
            if (d * d > vec.oMultiply(vec)) {
                return false;
            }

            // calculate direction of motion at vertex start position
            startDir = new idVec3((point.oMinus(origin)).Cross(tw.axis));
            if (angle < 0.0f) {
                startDir.oSet(startDir.oNegative());
            }
            // if moving away from plane at start position
            if (startDir.oMultiply(epsPlane.Normal()) >= 0.0f) {
                // if end position is outside epsilon range
                d = epsPlane.Distance(endPoint);
                if (d >= 0.0f) {
                    return false;    // no collision
                }
                // calculate direction of motion at vertex end position
                endDir.oSet((endPoint.oMinus(origin)).Cross(tw.axis));
                if (angle < 0.0f) {
                    endDir.oSet(endDir.oNegative());
                }
                // if also moving away from plane at end position
                if (endDir.oMultiply(epsPlane.Normal()) > 0.0f) {
                    return false; // no collision
                }
            }

            // if the start position is in the epsilon range
            d = epsPlane.Distance(point);
            if (d <= CM_PL_RANGE_EPSILON) {

                // calculate tangent of half the rotation for which the vertex is furthest away from the plane
                if (!PointFurthestFromPlane(tw, point, plane, angle, startTan, dir)) {
                    return false;
                }

                if (dir.getVal() <= 0.0f) {
                    // moving towards the polygon plane so stop immediately
                    tanHalfAngle.setVal(0.0f);
                } else if (Math.abs(startTan.getVal()) >= tw.maxTan) {
                    // never going to get beyond the start tangent during the current rotation
                    return false;
                } else {
                    // calculate collision with epsilon expanded plane
                    if (!RotatePointThroughPlane(tw, point, epsPlane, angle, Math.abs(startTan.getVal()), tanHalfAngle)) {
                        tanHalfAngle.setVal(startTan.getVal());
                    }
                }
            } else {
                // calculate collision with epsilon expanded plane
                if (!RotatePointThroughPlane(tw, point, epsPlane, angle, 0.0f, tanHalfAngle)) {
                    return false;
                }
            }

            // calculate collision point
            collisionPoint.oSet(point);
            if (tanHalfAngle.getVal() != 0.0f) {
                CM_RotatePoint(collisionPoint, tw.origin, tw.axis, tanHalfAngle.getVal());
            }
            // calculate direction of motion at collision point
            endDir.oSet((collisionPoint.oMinus(origin)).Cross(tw.axis));
            if (angle < 0.0f) {
                endDir.oSet(endDir.oNegative());
            }
            return true;
        }

        private void RotateTrmVertexThroughPolygon(cm_traceWork_s tw, cm_polygon_s poly, cm_trmVertex_s v, int vertexNum) {
            int i;
            CFloat tanHalfAngle = new CFloat();
            idVec3 endDir = new idVec3(), collisionPoint = new idVec3();
            idPluecker pl = new idPluecker();

            // if the trm vertex is behind the polygon plane it cannot collide with the polygon within a 180 degrees rotation
            if (tw.isConvex && tw.axisIntersectsTrm && v.polygonSide != 0) {
                return;
            }

            // if the trace model vertex rotation bounds do not intersect the polygon bounds
            if (!v.rotationBounds.IntersectsBounds(poly.bounds)) {
                return;
            }

            // vertex rotation bounds should cross polygon plane
            if (v.rotationBounds.PlaneSide(poly.plane) != SIDE_CROSS) {
                return;
            }

            // rotate the vertex through the epsilon plane
            if (!RotatePointThroughEpsilonPlane(tw, v.p, v.endp, poly.plane, tw.angle, v.rotationOrigin,
                    tanHalfAngle, collisionPoint, endDir)) {
                return;
            }

            if (Math.abs(tanHalfAngle.getVal()) < tw.maxTan) {
                // verify if 'collisionPoint' moving along 'endDir' moves between polygon edges
                pl.FromRay(collisionPoint, endDir);
                for (i = 0; i < poly.numEdges; i++) {
                    if (poly.edges[i] < 0) {
                        if (pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i]) > 0.0f) {
                            return;
                        }
                    } else {
                        if (pl.PermutedInnerProduct(tw.polygonEdgePlueckerCache[i]) < 0.0f) {
                            return;
                        }
                    }
                }
                tw.maxTan = Math.abs(tanHalfAngle.getVal());
                // collision plane is the polygon plane
                tw.trace.c.normal.oSet(poly.plane.Normal());
                tw.trace.c.dist = poly.plane.Dist();
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_TRMVERTEX;
                tw.trace.c.modelFeature = poly.hashCode();
                //tw.trace.c.modelFeature = vertexNum;
                tw.trace.c.trmFeature = Arrays.asList(tw.vertices).indexOf(v);
                tw.trace.c.point.oSet(collisionPoint);
            }
        }

        private void RotateVertexThroughTrmPolygon(cm_traceWork_s tw, cm_trmPolygon_s trmpoly, cm_polygon_s poly, cm_vertex_s v, idVec3 rotationOrigin) {
            int i, edgeNum;
            CFloat tanHalfAngle = new CFloat();
            idVec3 dir, endp, endDir = new idVec3(), collisionPoint = new idVec3();
            idPluecker pl = new idPluecker();
            cm_trmEdge_s edge;

            // if the polygon vertex is behind the trm plane it cannot collide with the trm polygon within a 180 degrees rotation
            if (tw.isConvex && tw.axisIntersectsTrm && trmpoly.plane.Distance(v.p) < 0.0f) {
                return;
            }

            // if the model vertex is outside the trm polygon rotation bounds
            if (!trmpoly.rotationBounds.ContainsPoint(v.p)) {
                return;
            }

            // if the rotation axis goes through the polygon vertex
            dir = new idVec3(v.p.oMinus(rotationOrigin));
            if (dir.oMultiply(dir) < ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                return;
            }

            // calculate vertex end position
            endp = new idVec3(v.p);
            tw.modelVertexRotation.RotatePoint(endp);

            // rotate the vertex through the epsilon plane
            if (!RotatePointThroughEpsilonPlane(tw, v.p, endp, trmpoly.plane, -tw.angle, rotationOrigin,
                    tanHalfAngle, collisionPoint, endDir)) {
                return;
            }

            if (Math.abs(tanHalfAngle.getVal()) < tw.maxTan) {
                // verify if 'collisionPoint' moving along 'endDir' moves between polygon edges
                pl.FromRay(collisionPoint, endDir);
                for (i = 0; i < trmpoly.numEdges; i++) {
                    edgeNum = trmpoly.edges[i];
                    edge = tw.edges[Math.abs(edgeNum)];
                    if (edgeNum < 0) {
                        if (pl.PermutedInnerProduct(edge.pl) > 0.0f) {
                            return;
                        }
                    } else {
                        if (pl.PermutedInnerProduct(edge.pl) < 0.0f) {
                            return;
                        }
                    }
                }
                tw.maxTan = Math.abs(tanHalfAngle.getVal());
                // collision plane is the flipped trm polygon plane
                tw.trace.c.normal = trmpoly.plane.Normal().oNegative();
                tw.trace.c.dist = tw.trace.c.normal.oMultiply(v.p);
                tw.trace.c.contents = poly.contents;
                tw.trace.c.material = poly.material;
                tw.trace.c.type = CONTACT_MODELVERTEX;
                tw.trace.c.modelFeature = Arrays.asList(tw.model.vertices).indexOf(v);
                tw.trace.c.trmFeature = Arrays.asList(tw.polys).indexOf(trmpoly);
                tw.trace.c.point = v.p;
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::RotateTrmThroughPolygon

         returns true if the polygon blocks the complete rotation
         ================
         */
        private boolean RotateTrmThroughPolygon(cm_traceWork_s tw, cm_polygon_s p) {
            int i, j, k, edgeNum;
            float d;
            cm_trmVertex_s bv;
            cm_trmEdge_s be;
            cm_trmPolygon_s bp;
            cm_vertex_s v;
            cm_edge_s e;
            idVec3 rotationOrigin;

            // if already checked this polygon
            if (p.checkcount == this.checkCount) {
                return false;
            }
            p.checkcount = this.checkCount;

            // if this polygon does not have the right contents behind it
            if (0 == (p.contents & tw.contents)) {
                return false;
            }

            // if the the trace bounds do not intersect the polygon bounds
            if (!tw.bounds.IntersectsBounds(p.bounds)) {
                return false;
            }

            // back face culling
            if (tw.isConvex) {
                // if the center of the convex trm is behind the polygon plane
                if (p.plane.Distance(tw.start) < 0.0f) {
                    // if the rotation axis intersects the trace model
                    if (tw.axisIntersectsTrm) {
                        return false;
                    } else {
                        // if the direction of motion at the start and end position of the
                        // center of the trm both go towards or away from the polygon plane
                        // or if the intersections of the rotation axis with the expanded heart planes
                        // are both in front of the polygon plane
                    }
                }
            }

            // if the polygon is too far from the first heart plane
            d = p.bounds.PlaneDistance(tw.heartPlane1);
            if (Math.abs(d) > tw.maxDistFromHeartPlane1) {
                return false;
            }

            // rotation bounds should cross polygon plane
            switch (tw.bounds.PlaneSide(p.plane)) {
                case PLANESIDE_CROSS:
                    break;
                case PLANESIDE_FRONT:
                    if (tw.model.isConvex) {
                        tw.quickExit = true;
                        return true;
                    }
                default:
                    return false;
            }

            for (i = 0; i < tw.numVerts; i++) {
                bv = tw.vertices[i];
                // calculate polygon side this vertex is on
                d = p.plane.Distance(bv.p);
                bv.polygonSide = FLOATSIGNBITSET(d);
            }

            for (i = 0; i < p.numEdges; i++) {
                edgeNum = p.edges[i];
                e = tw.model.edges[Math.abs(edgeNum)];
                v = tw.model.vertices[e.vertexNum[INTSIGNBITSET(edgeNum)]];

                // pluecker coordinate for edge
                tw.polygonEdgePlueckerCache[i].FromLine(tw.model.vertices[e.vertexNum[0]].p,
                        tw.model.vertices[e.vertexNum[1]].p);

                // calculate rotation origin projected into rotation plane through the vertex
                tw.polygonRotationOriginCache[i] = tw.origin.oPlus(tw.axis.oMultiply(tw.axis.oMultiply(v.p.oMinus(tw.origin))));
            }
            // copy first to last so we can easily cycle through
            tw.polygonRotationOriginCache[p.numEdges] = tw.polygonRotationOriginCache[0];

            // fast point rotation
            if (tw.pointTrace) {
                RotateTrmVertexThroughPolygon(tw, p, tw.vertices[0], 0);
            } else {
                // rotate trm vertices through polygon
                for (i = 0; i < tw.numVerts; i++) {
                    bv = tw.vertices[i];
                    if (bv.used) {
                        RotateTrmVertexThroughPolygon(tw, p, bv, i);
                    }
                }

                // rotate trm edges through polygon
                for (i = 1; i <= tw.numEdges; i++) {
                    be = tw.edges[i];
                    if (be.used) {
                        RotateTrmEdgeThroughPolygon(tw, p, be);
                    }
                }

                // rotate all polygon vertices through the trm
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    e = tw.model.edges[Math.abs(edgeNum)];

                    if (e.checkcount == this.checkCount) {
                        continue;
                    }
                    // set edge check count
                    e.checkcount = this.checkCount;
                    // can never collide with internal edges
                    if (e.internal != 0) {
                        continue;
                    }
                    // got to check both vertices because we skip internal edges
                    for (k = 0; k < 2; k++) {

                        v = tw.model.vertices[e.vertexNum[k ^ INTSIGNBITSET(edgeNum)]];

                        // if this vertex is already checked
                        if (v.checkcount == this.checkCount) {
                            continue;
                        }
                        // set vertex check count
                        v.checkcount = this.checkCount;

                        // if the vertex is outside the trm rotation bounds
                        if (!tw.bounds.ContainsPoint(v.p)) {
                            continue;
                        }

                        rotationOrigin = new idVec3(tw.polygonRotationOriginCache[i + k]);

                        for (j = 0; j < tw.numPolys; j++) {
                            bp = tw.polys[j];
                            if (bp.used != 0) {
                                RotateVertexThroughTrmPolygon(tw, bp, p, v, rotationOrigin);
                            }
                        }
                    }
                }
            }

            return (tw.maxTan == 0.0f);
        }

        /*
         ================
         idCollisionModelManagerLocal::BoundsForRotation

         only for rotations < 180 degrees
         ================
         */
        private void BoundsForRotation(final idVec3 origin, final idVec3 axis, final idVec3 start, final idVec3 end, idBounds bounds) {
            int i;
            float radiusSqr;

            radiusSqr = (start.oMinus(origin)).LengthSqr();
            idVec3 v1 = (start.oMinus(origin)).Cross(axis);
            idVec3 v2 = (end.oMinus(origin)).Cross(axis);

            for (i = 0; i < 3; i++) {
                // if the derivative changes sign along this axis during the rotation from start to end
                if ((v1.oGet(i) > 0.0f && v2.oGet(i) < 0.0f) || (v1.oGet(i) < 0.0f && v2.oGet(i) > 0.0f)) {
                    if ((0.5f * (start.oGet(i) + end.oGet(i)) - origin.oGet(i)) > 0.0f) {
                        bounds.oSet(0, i, Min(start.oGet(i), end.oGet(i)));
                        bounds.oSet(1, i, origin.oGet(i) + idMath.Sqrt(radiusSqr * (1.0f - axis.oGet(i) * axis.oGet(i))));
                    } else {
                        bounds.oSet(0, i, origin.oGet(i) - idMath.Sqrt(radiusSqr * (1.0f - axis.oGet(i) * axis.oGet(i))));
                        bounds.oSet(1, i, Max(start.oGet(i), end.oGet(i)));
                    }
                } else if (start.oGet(i) > end.oGet(i)) {
                    bounds.oSet(0, i, end.oGet(i));
                    bounds.oSet(1, i, start.oGet(i));
                } else {
                    bounds.oSet(0, i, start.oGet(i));
                    bounds.oSet(1, i, end.oGet(i));
                }
                // expand for epsilons
                bounds.oGet(0).oMinSet(i, CM_BOX_EPSILON);
                bounds.oGet(1).oPluSet(i, CM_BOX_EPSILON);
            }
        }

        private void Rotation180(trace_s[] results, final idVec3 rorg, final idVec3 axis,
                                 final float startAngle, final float endAngle, final idVec3 start,
                                 final idTraceModel trm, final idMat3 trmAxis, int contentMask,
                /*cmHandle_t*/ int model, final idVec3 modelOrigin, final idMat3 modelAxis) {
            int i, j, edgeNum;
            float d, maxErr, initialTan;
            boolean model_rotated, trm_rotated;
            idVec3 vr = new idVec3(), vup = new idVec3();
            idMat3 invModelAxis = new idMat3(), endAxis, tmpAxis;
            idRotation startRotation = new idRotation(), endRotation = new idRotation();
            idPluecker plaxis = new idPluecker();
            cm_trmPolygon_s poly;
            cm_trmEdge_s edge;
            cm_trmVertex_s vert;

            if (model < 0 || model > MAX_SUBMODELS || model > this.maxModels) {
                common.Printf("idCollisionModelManagerLocal::Rotation180: invalid model handle\n");
                return;
            }
            if (null == this.models[model]) {
                common.Printf("idCollisionModelManagerLocal::Rotation180: invalid model\n");
                return;
            }

            this.checkCount++;

            tw.trace.fraction = 1.0f;
            tw.trace.c.contents = 0;
            tw.trace.c.type = CONTACT_NONE;
            tw.contents = contentMask;
            tw.isConvex = true;
            tw.rotation = true;
            tw.positionTest = false;
            tw.axisIntersectsTrm = false;
            tw.quickExit = false;
            tw.angle = endAngle - startAngle;
            assert (tw.angle > -180.0f && tw.angle < 180.0f);
            tw.maxTan = initialTan = Math.abs((float) Math.tan((idMath.PI / 360.0f) * tw.angle));
            tw.model = this.models[model];
            tw.start.oSet(start.oMinus(modelOrigin));
            // rotation axis, axis is assumed to be normalized
            tw.axis.oSet(axis);
            assert (tw.axis.oGet(0) * tw.axis.oGet(0) + tw.axis.oGet(1) * tw.axis.oGet(1) + tw.axis.oGet(2) * tw.axis.oGet(2) > 0.99f);
            // rotation origin projected into rotation plane through tw.start
            tw.origin.oSet(rorg.oMinus(modelOrigin));
            d = (tw.axis.oMultiply(tw.origin)) - (tw.axis.oMultiply(tw.start));
            tw.origin.oSet(tw.origin.oMinus(tw.axis.oMultiply(d)));
            // radius of rotation
            tw.radius = (tw.start.oMinus(tw.origin)).Length();
            // maximum error of the circle approximation traced through the axial BSP tree
            d = tw.radius * tw.radius - (CIRCLE_APPROXIMATION_LENGTH * CIRCLE_APPROXIMATION_LENGTH * 0.25f);
            if (d > 0.0f) {
                maxErr = (float) (tw.radius - Math.sqrt(d));
            } else {
                maxErr = tw.radius;
            }

            model_rotated = modelAxis.IsRotated();
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose();
                tw.axis.oMulSet(invModelAxis);
                tw.origin.oMulSet(invModelAxis);
            }

            startRotation.Set(tw.origin, tw.axis, startAngle);
            endRotation.Set(tw.origin, tw.axis, endAngle);

            // create matrix which rotates the rotation axis to the z-axis
            tw.axis.NormalVectors(vr, vup);
            tw.matrix.oSet(0, 0, vr.oGet(0));
            tw.matrix.oSet(1, 0, vr.oGet(1));
            tw.matrix.oSet(2, 0, vr.oGet(2));
            tw.matrix.oSet(0, 1, -vup.oGet(0));
            tw.matrix.oSet(1, 1, -vup.oGet(1));
            tw.matrix.oSet(2, 1, -vup.oGet(2));
            tw.matrix.oSet(0, 2, tw.axis.oGet(0));
            tw.matrix.oSet(1, 2, tw.axis.oGet(1));
            tw.matrix.oSet(2, 2, tw.axis.oGet(2));

            // if optimized point trace
            if (null == trm
                    || (trm.bounds.oGet(1).oGet(0) - trm.bounds.oGet(0).oGet(0) <= 0.0f
                    && trm.bounds.oGet(1).oGet(1) - trm.bounds.oGet(0).oGet(1) <= 0.0f
                    && trm.bounds.oGet(1).oGet(2) - trm.bounds.oGet(0).oGet(2) <= 0.0f)) {

                if (model_rotated) {
                    // rotate trace instead of model
                    tw.start.oMulSet(invModelAxis);
                }
                tw.end.oSet(tw.start);
                // if we start at a specific angle
                if (startAngle != 0.0f) {
                    startRotation.RotatePoint(tw.start);
                }
                // calculate end position of rotation
                endRotation.RotatePoint(tw.end);

                // calculate rotation origin projected into rotation plane through the vertex
                tw.numVerts = 1;
                tw.vertices[0].p.oSet(tw.start);
                tw.vertices[0].endp.oSet(tw.end);
                tw.vertices[0].used = true;
                tw.vertices[0].rotationOrigin.oSet(tw.origin.oPlus(tw.axis.oMultiply(tw.axis.oMultiply(tw.vertices[0].p.oMinus(tw.origin)))));
                BoundsForRotation(tw.vertices[0].rotationOrigin, tw.axis, tw.start, tw.end, tw.vertices[0].rotationBounds);
                // rotation bounds
                tw.bounds.oSet(tw.vertices[0].rotationBounds);
                tw.numEdges = tw.numPolys = 0;

                // collision with single point
                tw.pointTrace = true;

                // extents is set to maximum error of the circle approximation traced through the axial BSP tree
                tw.extents.oSet(0, tw.extents.oSet(1, tw.extents.oSet(2, maxErr + CM_BOX_EPSILON)));

                // setup rotation heart plane
                tw.heartPlane1.SetNormal(tw.axis);
                tw.heartPlane1.FitThroughPoint(tw.start);
                tw.maxDistFromHeartPlane1 = CM_BOX_EPSILON;

                // trace through the model
                TraceThroughModel(tw);

                // store results
                results[0].oSet(tw.trace);
                results[0].endpos.oSet(start);
                if (tw.maxTan == initialTan) {
                    results[0].fraction = 1.0f;
                } else {
                    results[0].fraction = Math.abs((float) Math.atan(tw.maxTan) * (2.0f * 180.0f / idMath.PI) / tw.angle);
                }
                assert (results[0].fraction <= 1.0f);
                endRotation.Set(rorg, axis, startAngle + (endAngle - startAngle) * results[0].fraction);
                endRotation.RotatePoint(results[0].endpos);
                results[0].endAxis.Identity();

                if (results[0].fraction < 1.0f) {
                    // rotate trace plane normal if there was a collision with a rotated model
                    if (model_rotated) {
                        results[0].c.normal.oMulSet(modelAxis);
                        results[0].c.point.oMulSet(modelAxis);
                    }
                    results[0].c.point.oPluSet(modelOrigin);
                    results[0].c.dist += modelOrigin.oMultiply(results[0].c.normal);
                }
                return;
            }

            tw.pointTrace = false;

            // setup trm structure
            SetupTrm(tw, trm);

            trm_rotated = trmAxis.IsRotated();

            // calculate vertex positions
            if (trm_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    // rotate trm around the start position
                    tw.vertices[i].p.oMulSet(trmAxis);
                }
            }
            for (i = 0; i < tw.numVerts; i++) {
                // set trm at start position
                tw.vertices[i].p.oPluSet(tw.start);
            }
            if (model_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    tw.vertices[i].p.oMulSet(invModelAxis);
                }
            }
            for (i = 0; i < tw.numVerts; i++) {
                tw.vertices[i].endp.oSet(tw.vertices[i].p);
            }
            // if we start at a specific angle
            if (startAngle != 0.0f) {
                for (i = 0; i < tw.numVerts; i++) {
                    startRotation.RotatePoint(tw.vertices[i].p);
                }
            }
            for (i = 0; i < tw.numVerts; i++) {
                // end position of vertex
                endRotation.RotatePoint(tw.vertices[i].endp);
            }

            // add offset to start point
            if (trm_rotated) {
                tw.start.oPluSet(trm.offset.oMultiply(trmAxis));
            } else {
                tw.start.oPluSet(trm.offset);
            }
            // if the model is rotated
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.oMulSet(invModelAxis);
            }
            tw.end.oSet(tw.start);
            // if we start at a specific angle
            if (startAngle != 0.0f) {
                startRotation.RotatePoint(tw.start);
            }
            // calculate end position of rotation
            endRotation.RotatePoint(tw.end);

            // setup trm vertices
            for (i = 0; i < tw.numVerts; i++) {
                vert = tw.vertices[i];
                // calculate rotation origin projected into rotation plane through the vertex
                vert.rotationOrigin.oSet(tw.origin.oPlus(tw.axis.oMultiply(tw.axis.oMultiply(vert.p.oMinus(tw.origin)))));
                // calculate rotation bounds for this vertex
                BoundsForRotation(vert.rotationOrigin, tw.axis, vert.p, vert.endp, vert.rotationBounds);
                // if the rotation axis goes through the vertex then the vertex is not used
                d = (vert.p.oMinus(vert.rotationOrigin)).LengthSqr();
                if (d > ROTATION_AXIS_EPSILON * ROTATION_AXIS_EPSILON) {
                    vert.used = true;
                }
            }

            // setup trm edges
            for (i = 1; i <= tw.numEdges; i++) {
                edge = tw.edges[i];
                // if the rotation axis goes through both the edge vertices then the edge is not used
                if ((tw.vertices[edge.vertexNum[0]].used | tw.vertices[edge.vertexNum[1]].used)) {
                    edge.used = true;
                }
                // edge start, end and pluecker coordinate
                edge.start.oSet(tw.vertices[edge.vertexNum[0]].p);
                edge.end.oSet(tw.vertices[edge.vertexNum[1]].p);
                edge.pl.FromLine(edge.start, edge.end);
                // pluecker coordinate for edge being rotated about the z-axis
                idVec3 at = (edge.start.oMinus(tw.origin)).oMultiply(tw.matrix);
                idVec3 bt = (edge.end.oMinus(tw.origin)).oMultiply(tw.matrix);
                edge.plzaxis.FromLine(at, bt);
                // get edge rotation bounds from the rotation bounds of both vertices
                edge.rotationBounds.oSet(tw.vertices[edge.vertexNum[0]].rotationBounds);
                edge.rotationBounds.AddBounds(tw.vertices[edge.vertexNum[1]].rotationBounds);
                // used to calculate if the rotation axis intersects the trm
                edge.bitNum = 0;
            }

            tw.bounds.Clear();

            // rotate trm polygon planes
            if (trm_rotated && model_rotated) {
                tmpAxis = trmAxis.oMultiply(invModelAxis);
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(tmpAxis);
                }
            } else if (trm_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(trmAxis);
                }
            } else if (model_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(invModelAxis);
                }
            }

            // setup trm polygons
            for (i = 0; i < tw.numPolys; i++) {
                poly = tw.polys[i];
                poly.used = 1;//true;
                // set trm polygon plane distance
                poly.plane.FitThroughPoint(tw.edges[Math.abs(poly.edges[0])].start);
                // get polygon bounds from edge bounds
                poly.rotationBounds.Clear();
                for (j = 0; j < poly.numEdges; j++) {
                    // add edge rotation bounds to polygon rotation bounds
                    edge = tw.edges[Math.abs(poly.edges[j])];
                    poly.rotationBounds.AddBounds(edge.rotationBounds);
                }
                // get trace bounds from polygon bounds
                tw.bounds.AddBounds(poly.rotationBounds);
            }

            // extents including the maximum error of the circle approximation traced through the axial BSP tree
            for (i = 0; i < 3; i++) {
                tw.size.oSet(0, i, tw.bounds.oGet(0).oGet(i) - tw.start.oGet(i));
                tw.size.oSet(1, i, tw.bounds.oGet(1).oGet(i) - tw.start.oGet(i));
                if (Math.abs(tw.size.oGet(0).oGet(i)) > Math.abs(tw.size.oGet(1).oGet(i))) {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(0).oGet(i)) + maxErr + CM_BOX_EPSILON);
                } else {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(1).oGet(i)) + maxErr + CM_BOX_EPSILON);
                }
            }

            // for back-face culling
            if (tw.isConvex) {
                if (tw.start.equals(tw.origin)) {
                    tw.axisIntersectsTrm = true;
                } else {
                    // determine if the rotation axis intersects the trm
                    plaxis.FromRay(tw.origin, tw.axis);
                    for (poly = tw.polys[i], i = 0; i < tw.numPolys; i++, poly = tw.polys[i]) {
                        // back face cull polygons
                        if (poly.plane.Normal().oMultiply(tw.axis) > 0.0f) {
                            continue;
                        }
                        // test if the axis goes between the polygon edges
                        for (j = 0; j < poly.numEdges; j++) {
                            edgeNum = poly.edges[j];
                            edge = tw.edges[Math.abs(edgeNum)];
                            if (0 == (edge.bitNum & 2)) {
                                d = plaxis.PermutedInnerProduct(edge.pl);
                                edge.bitNum = (short) (FLOATSIGNBITSET(d) | 2);
                            }
                            if (((edge.bitNum ^ INTSIGNBITSET(edgeNum)) & 1) == 1) {
                                break;
                            }
                        }
                        if (j >= poly.numEdges) {
                            tw.axisIntersectsTrm = true;
                            break;
                        }
                    }
                }
            }

            // setup rotation heart plane
            tw.heartPlane1.SetNormal(tw.axis);
            tw.heartPlane1.FitThroughPoint(tw.start);
            tw.maxDistFromHeartPlane1 = 0.0f;
            for (i = 0; i < tw.numVerts; i++) {
                d = Math.abs(tw.heartPlane1.Distance(tw.vertices[i].p));
                if (d > tw.maxDistFromHeartPlane1) {
                    tw.maxDistFromHeartPlane1 = d;
                }
            }
            tw.maxDistFromHeartPlane1 += CM_BOX_EPSILON;

            // inverse rotation to rotate model vertices towards trace model
            tw.modelVertexRotation.Set(tw.origin, tw.axis, -tw.angle);

            // trace through the model
            TraceThroughModel(tw);

            // store results
            results[0].oSet(tw.trace);
            results[0].endpos.oSet(start);
            if (tw.maxTan == initialTan) {
                results[0].fraction = 1.0f;
            } else {
                results[0].fraction = Math.abs((float) Math.atan(tw.maxTan) * (2.0f * 180.0f / idMath.PI) / tw.angle);
            }
            assert (results[0].fraction <= 1.0f);
            endRotation.Set(rorg, axis, startAngle + (endAngle - startAngle) * results[0].fraction);
            endRotation.RotatePoint(results[0].endpos);
            results[0].endAxis.oSet(trmAxis.oMultiply(endRotation.ToMat3()));

            if (results[0].fraction < 1.0f) {
                // rotate trace plane normal if there was a collision with a rotated model
                if (model_rotated) {
                    results[0].c.normal.oMulSet(modelAxis);
                    results[0].c.point.oMulSet(modelAxis);
                }
                results[0].c.point.oPluSet(modelOrigin);
                results[0].c.dist += modelOrigin.oMultiply(results[0].c.normal);
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::TestTrmVertsInBrush

         returns true if any of the trm vertices is inside the brush
         ================
         */
        // CollisionMap_contents.cpp
        private boolean TestTrmVertsInBrush(cm_traceWork_s tw, cm_brush_s b) {
            int i, j, numVerts, bestPlane;
            float d, bestd;

            if (b.checkcount == this.checkCount) {
                return false;
            }
            b.checkcount = this.checkCount;

            if (0 == (b.contents & tw.contents)) {
                return false;
            }

            // if the brush bounds don't intersect the trace bounds
            if (!b.bounds.IntersectsBounds(tw.bounds)) {
                return false;
            }

            if (tw.pointTrace) {
                numVerts = 1;
            } else {
                numVerts = tw.numVerts;
            }

            for (j = 0; j < numVerts; j++) {
                idVec3 p = tw.vertices[j].p;

                // see if the point is inside the brush
                bestPlane = 0;
                bestd = -idMath.INFINITY;
                for (i = 0; i < b.numPlanes; i++) {
                    d = b.planes[i].Distance(p);
                    if (d >= 0.0f) {
                        break;
                    }
                    if (d > bestd) {
                        bestd = d;
                        bestPlane = i;
                    }
                }
                if (i >= b.numPlanes) {
                    tw.trace.fraction = 0.0f;
                    tw.trace.c.type = CONTACT_TRMVERTEX;
                    tw.trace.c.normal.oSet(b.planes[bestPlane].Normal());
                    tw.trace.c.dist = b.planes[bestPlane].Dist();
                    tw.trace.c.contents = b.contents;
                    tw.trace.c.material = b.material;
                    tw.trace.c.point.oSet(p);
                    tw.trace.c.modelFeature = 0;
                    tw.trace.c.trmFeature = j;
                    return true;
                }
            }
            return false;
        }

        /*
         ================
         idCollisionModelManagerLocal::TestTrmInPolygon

         returns true if the trm intersects the polygon
         ================
         */
        private boolean TestTrmInPolygon(cm_traceWork_s tw, cm_polygon_s p, final int modelFeature) {
            int i, j, k, edgeNum, flip, trmEdgeNum, bitNum, bestPlane;
            int[] sides = new int[MAX_TRACEMODEL_VERTS];
            float d, bestd;
            cm_trmEdge_s trmEdge;
            cm_edge_s edge;
            cm_vertex_s v, v1, v2;

            // if already checked this polygon
            if (p.checkcount == this.checkCount) {
                return false;
            }
            p.checkcount = this.checkCount;

            // if this polygon does not have the right contents behind it
            if (0 == (p.contents & tw.contents)) {
                return false;
            }

            // if the polygon bounds don't intersect the trace bounds
            if (!p.bounds.IntersectsBounds(tw.bounds)) {
                return false;
            }

            // bounds should cross polygon plane
            switch (tw.bounds.PlaneSide(p.plane)) {
                case PLANESIDE_CROSS:
                    break;
                case PLANESIDE_FRONT:
                    if (tw.model.isConvex) {
                        tw.quickExit = true;
                        return true;
                    }
                default:
                    return false;
            }

            // if the trace model is convex
            if (tw.isConvex) {
                // test if any polygon vertices are inside the trm
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    edge = tw.model.edges[Math.abs(edgeNum)];
                    // if this edge is already tested
                    if (edge.checkcount == this.checkCount) {
                        continue;
                    }

                    for (j = 0; j < 2; j++) {
                        v = tw.model.vertices[edge.vertexNum[j]];
                        // if this vertex is already tested
                        if (v.checkcount == this.checkCount) {
                            continue;
                        }

                        bestPlane = 0;
                        bestd = -idMath.INFINITY;
                        for (k = 0; k < tw.numPolys; k++) {
                            d = tw.polys[k].plane.Distance(v.p);
                            if (d >= 0.0f) {
                                break;
                            }
                            if (d > bestd) {
                                bestd = d;
                                bestPlane = k;
                            }
                        }
                        if (k >= tw.numPolys) {
                            tw.trace.fraction = 0.0f;
                            tw.trace.c.type = CONTACT_MODELVERTEX;
                            tw.trace.c.normal.oSet(tw.polys[bestPlane].plane.Normal().oNegative());
                            tw.trace.c.dist = -tw.polys[bestPlane].plane.Dist();
                            tw.trace.c.contents = p.contents;
                            tw.trace.c.material = p.material;
                            tw.trace.c.point.oSet(v.p);
                            tw.trace.c.modelFeature = edge.vertexNum[j];
                            tw.trace.c.trmFeature = 0;
                            return true;
                        }
                    }
                }
            }

            for (i = 0; i < p.numEdges; i++) {
                edgeNum = p.edges[i];
                edge = tw.model.edges[Math.abs(edgeNum)];
                // reset sidedness cache if this is the first time we encounter this edge
                if (edge.checkcount != this.checkCount) {
                    edge.sideSet = 0;
                }
                // pluecker coordinate for edge
                tw.polygonEdgePlueckerCache[i].FromLine(tw.model.vertices[edge.vertexNum[0]].p,
                        tw.model.vertices[edge.vertexNum[1]].p);
                v = tw.model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]];
                // reset sidedness cache if this is the first time we encounter this vertex
                if (v.checkcount != this.checkCount) {
                    v.sideSet = 0;
                }
                v.checkcount = this.checkCount;
            }

            // get side of polygon for each trm vertex
            for (i = 0; i < tw.numVerts; i++) {
                d = p.plane.Distance(tw.vertices[i].p);
                sides[i] = d < 0.0f ? -1 : 1;
            }

            // test if any trm edges go through the polygon
            for (i = 1; i <= tw.numEdges; i++) {
                // if the trm edge does not cross the polygon plane
                if (sides[tw.edges[i].vertexNum[0]] == sides[tw.edges[i].vertexNum[1]]) {
                    continue;
                }
                // check from which side to which side the trm edge goes
                flip = INTSIGNBITSET(sides[tw.edges[i].vertexNum[0]]);
                // test if trm edge goes through the polygon between the polygon edges
                for (j = 0; j < p.numEdges; j++) {
                    edgeNum = p.edges[j];
                    edge = tw.model.edges[Math.abs(edgeNum)];
                    CM_SetTrmEdgeSidedness(edge, tw.edges[i].pl, tw.polygonEdgePlueckerCache[j], i);
                    if ((INTSIGNBITSET(edgeNum) ^ ((edge.side >> i) & 1) ^ flip) != 0) {
                        break;
                    }
                }
                if (j >= p.numEdges) {
                    tw.trace.fraction = 0.0f;
                    tw.trace.c.type = CONTACT_EDGE;
                    tw.trace.c.normal.oSet(p.plane.Normal());
                    tw.trace.c.dist = p.plane.Dist();
                    tw.trace.c.contents = p.contents;
                    tw.trace.c.material = p.material;
                    tw.trace.c.point.oSet(tw.vertices[tw.edges[i].vertexNum[0 == flip ? 1 : 0]].p);
                    tw.trace.c.modelFeature = modelFeature;
                    tw.trace.c.trmFeature = i;
                    return true;
                }
            }

            // test if any polygon edges go through the trm polygons
            for (i = 0; i < p.numEdges; i++) {
                edgeNum = p.edges[i];
                edge = tw.model.edges[Math.abs(edgeNum)];
                if (edge.checkcount == this.checkCount) {
                    continue;
                }
                edge.checkcount = this.checkCount;

                for (j = 0; j < tw.numPolys; j++) {
                    v1 = tw.model.vertices[edge.vertexNum[0]];
                    CM_SetTrmPolygonSidedness(v1, tw.polys[j].plane, j);
                    v2 = tw.model.vertices[edge.vertexNum[1]];
                    CM_SetTrmPolygonSidedness(v2, tw.polys[j].plane, j);
                    // if the polygon edge does not cross the trm polygon plane
                    if (0 == (((v1.side ^ v2.side) >> j) & 1)) {
                        continue;
                    }
                    flip = (int) ((v1.side >> j) & 1);
                    // test if polygon edge goes through the trm polygon between the trm polygon edges
                    for (k = 0; k < tw.polys[j].numEdges; k++) {
                        trmEdgeNum = tw.polys[j].edges[k];
                        trmEdge = tw.edges[Math.abs(trmEdgeNum)];
                        bitNum = Math.abs(trmEdgeNum);
                        CM_SetTrmEdgeSidedness(edge, trmEdge.pl, tw.polygonEdgePlueckerCache[i], bitNum);
                        if ((INTSIGNBITSET(trmEdgeNum) ^ ((edge.side >> bitNum) & 1) ^ flip) != 0) {
                            break;
                        }
                    }
                    if (k >= tw.polys[j].numEdges) {
                        tw.trace.fraction = 0.0f;
                        tw.trace.c.type = CONTACT_EDGE;
                        tw.trace.c.normal.oSet(tw.polys[j].plane.Normal().oNegative());
                        tw.trace.c.dist = -tw.polys[j].plane.Dist();
                        tw.trace.c.contents = p.contents;
                        tw.trace.c.material = p.material;
                        tw.trace.c.point.oSet(tw.model.vertices[edge.vertexNum[0 == flip ? 1 : 0]].p);
                        tw.trace.c.modelFeature = edgeNum;
                        tw.trace.c.trmFeature = j;
                        return true;
                    }
                }
            }
            return false;
        }

        private cm_node_s PointNode(final idVec3 p, cm_model_s model) {
            cm_node_s node;

            node = model.node;
            while (node.planeType != -1) {
                if (p.oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else {
                    node = node.children[1];
                }

                assert (node != null);
            }
            return node;
        }

        private int PointContents(final idVec3 p, /*cmHandle_t*/ int model) {
            int i;
            float d;
            cm_node_s node;
            cm_brushRef_s bref;
            cm_brush_s b;

            node = PointNode(p, this.models[model]);
            for (bref = node.brushes; bref != null; bref = bref.next) {
                b = bref.b;
                // test if the point is within the brush bounds
                for (i = 0; i < 3; i++) {
                    if (p.oGet(i) < b.bounds.oGet(0).oGet(i)) {
                        break;
                    }
                    if (p.oGet(i) > b.bounds.oGet(1).oGet(i)) {
                        break;
                    }
                }
                if (i < 3) {
                    continue;
                }
                // test if the point is inside the brush
                for (i = 0; i < b.numPlanes; i++) {
                    d = b.planes[i].Distance(p);
                    if (d >= 0) {
                        break;
                    }
                }
                if (i >= b.numPlanes) {
                    return b.contents;
                }
            }
            return 0;
        }

        private int TransformedPointContents(final idVec3 p, /*cmHandle_t*/ int model, final idVec3 origin, final idMat3 modelAxis) {
            // subtract origin offset
            idVec3 p_l = p.oMinus(origin);
            if (modelAxis.IsRotated()) {
                p_l.oMulSet(modelAxis);
            }
            return PointContents(p_l, model);
        }

        private int ContentsTrm(trace_s[] results, final idVec3 start, final idTraceModel trm, final idMat3 trmAxis,
                                int contentMask, /*cmHandle_t*/ int model, final idVec3 modelOrigin, final idMat3 modelAxis) {
            int i;
            boolean model_rotated, trm_rotated;
            idMat3 invModelAxis = new idMat3(), tmpAxis;

            // fast point case
            if (null == trm
                    || (trm.bounds.oGet(1).oGet(0) - trm.bounds.oGet(0).oGet(0) <= 0.0f
                    && trm.bounds.oGet(1).oGet(1) - trm.bounds.oGet(0).oGet(1) <= 0.0f
                    && trm.bounds.oGet(1).oGet(2) - trm.bounds.oGet(0).oGet(2) <= 0.0f)) {

                results[0].c.contents = this.TransformedPointContents(start, model, modelOrigin, modelAxis);
                results[0].fraction = (results[0].c.contents == 0 ? 1 : 0);
                results[0].endpos.oSet(start);
                results[0].endAxis.oSet(trmAxis);

                return results[0].c.contents;
            }

            this.checkCount++;

            tw.trace.fraction = 1.0f;
            tw.trace.c.contents = 0;
            tw.trace.c.type = CONTACT_NONE;
            tw.contents = contentMask;
            tw.isConvex = true;
            tw.rotation = false;
            tw.positionTest = true;
            tw.pointTrace = false;
            tw.quickExit = false;
            tw.numContacts = 0;
            tw.model = this.models[model];
            tw.start.oSet(start.oMinus(modelOrigin));
            tw.end.oSet(tw.start);

            model_rotated = modelAxis.IsRotated();
            if (model_rotated) {
                invModelAxis = modelAxis.Transpose();
            }

            // setup trm structure
            this.SetupTrm(tw, trm);

            trm_rotated = trmAxis.IsRotated();

            // calculate vertex positions
            if (trm_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    // rotate trm around the start position
                    tw.vertices[i].p.oMulSet(trmAxis);
                }
            }
            for (i = 0; i < tw.numVerts; i++) {
                // set trm at start position
                tw.vertices[i].p.oPluSet(tw.start);
            }
            if (model_rotated) {
                for (i = 0; i < tw.numVerts; i++) {
                    // rotate trm around model instead of rotating the model
                    tw.vertices[i].p.oMulSet(invModelAxis);
                }
            }

            // add offset to start point
            if (trm_rotated) {
                idVec3 dir = trm.offset.oMultiply(trmAxis);
                tw.start.oPluSet(dir);
                tw.end.oPluSet(dir);
            } else {
                tw.start.oPluSet(trm.offset);
                tw.end.oPluSet(trm.offset);
            }
            if (model_rotated) {
                // rotate trace instead of model
                tw.start.oMulSet(invModelAxis);
                tw.end.oMulSet(invModelAxis);
            }

            // setup trm vertices
            tw.size.Clear();
            for (i = 0; i < tw.numVerts; i++) {
                // get axial trm size after rotations
                tw.size.AddPoint(tw.vertices[i].p.oMinus(tw.start));
            }

            // setup trm edges
            for (i = 1; i <= tw.numEdges; i++) {
                // edge start, end and pluecker coordinate
                tw.edges[i].start.oSet(tw.vertices[tw.edges[i].vertexNum[0]].p);
                tw.edges[i].end.oSet(tw.vertices[tw.edges[i].vertexNum[1]].p);
                tw.edges[i].pl.FromLine(tw.edges[i].start, tw.edges[i].end);
            }

            // setup trm polygons
            if (trm_rotated && model_rotated) {
                tmpAxis = trmAxis.oMultiply(invModelAxis);
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(tmpAxis);
                }
            } else if (trm_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(trmAxis);
                }
            } else if (model_rotated) {
                for (i = 0; i < tw.numPolys; i++) {
                    tw.polys[i].plane.oMulSet(invModelAxis);
                }
            }
            for (i = 0; i < tw.numPolys; i++) {
                tw.polys[i].plane.FitThroughPoint(tw.edges[Math.abs(tw.polys[i].edges[0])].start);
            }

            // bounds for full trace, a little bit larger for epsilons
            for (i = 0; i < 3; i++) {
                if (tw.start.oGet(i) < tw.end.oGet(i)) {
                    tw.bounds.oSet(0, i, tw.start.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                    tw.bounds.oSet(1, i, tw.end.oGet(i) + tw.size.oGet(1).oGet(i) + CM_BOX_EPSILON);
                } else {
                    tw.bounds.oSet(0, i, tw.end.oGet(i) + tw.size.oGet(0).oGet(i) - CM_BOX_EPSILON);
                    tw.bounds.oSet(1, i, tw.start.oGet(i) + tw.size.oGet(1).oGet(i) + CM_BOX_EPSILON);
                }
                if (Math.abs(tw.size.oGet(0).oGet(i)) > Math.abs(tw.size.oGet(1).oGet(i))) {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(0).oGet(i)) + CM_BOX_EPSILON);
                } else {
                    tw.extents.oSet(i, Math.abs(tw.size.oGet(1).oGet(i)) + CM_BOX_EPSILON);
                }
            }

            // trace through the model
            TraceThroughModel(tw);

            results[0] = tw.trace;
            results[0].fraction = (results[0].c.contents == 0 ? 1 : 0);
            results[0].endpos.oSet(start);
            results[0].endAxis.oSet(trmAxis);

            return results[0].c.contents;
        }

        /*
         ===============================================================================

         Trace through the spatial subdivision

         ===============================================================================
         */
        // CollisionMap_trace.cpp
        private void TraceTrmThroughNode(cm_traceWork_s tw, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_brushRef_s bref;

            // position test
            if (tw.positionTest) {
                // if already stuck in solid
                if (tw.trace.fraction == 0.0f) {
                    return;
                }
                // test if any of the trm vertices is inside a brush
                for (bref = node.brushes; bref != null; bref = bref.next) {
                    if (this.TestTrmVertsInBrush(tw, bref.b)) {
                        return;
                    }
                }
                // if just testing a point we're done
                if (tw.pointTrace) {
                    return;
                }
                int modelFeature = 0;
                // test if the trm is stuck in any polygons
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    if (this.TestTrmInPolygon(tw, pref.p, modelFeature++)) {
                        return;
                    }
                }
            } else if (tw.rotation) {
                // rotate through all polygons in this leaf
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    if (this.RotateTrmThroughPolygon(tw, pref.p)) {
                        return;
                    }
                }
            } else {
                // trace through all polygons in this leaf
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    if (this.TranslateTrmThroughPolygon(tw, pref.p)) {
                        return;
                    }
                }
            }
        }

        private void TraceThroughAxialBSPTree_r(cm_traceWork_s tw, cm_node_s node, float p1f, float p2f, idVec3 p1, idVec3 p2) {
            float t1, t2, offset;
            float frac, frac2;
            float idist;
            idVec3 mid = new idVec3();
            int side;
            float midf;

            if (null == node) {
                return;
            }

            if (tw.quickExit) {
                return;        // stop immediately
            }

            if (tw.trace.fraction <= p1f) {
                return;        // already hit something nearer
            }

            // if we need to test this node for collisions
            if (node.polygons != null || (tw.positionTest && node.brushes != null)) {
                // trace through node with collision data
                this.TraceTrmThroughNode(tw, node);
            }
            // if already stuck in solid
            if (tw.positionTest && tw.trace.fraction == 0.0f) {
                return;
            }
            // if this is a leaf node
            if (node.planeType == -1) {
                return;
            }
            if (NO_SPATIAL_SUBDIVISION) {
                this.TraceThroughAxialBSPTree_r(tw, node.children[0], p1f, p2f, p1, p2);
                this.TraceThroughAxialBSPTree_r(tw, node.children[1], p1f, p2f, p1, p2);
                return;
            }
            // distance from plane for trace start and end
            t1 = p1.oGet(node.planeType) - node.planeDist;
            t2 = p2.oGet(node.planeType) - node.planeDist;
            // adjust the plane distance appropriately for mins/maxs
            offset = tw.extents.oGet(node.planeType);
            // see which sides we need to consider
            if (t1 >= offset && t2 >= offset) {
                this.TraceThroughAxialBSPTree_r(tw, node.children[0], p1f, p2f, p1, p2);
                return;
            }

            if (t1 < -offset && t2 < -offset) {
                this.TraceThroughAxialBSPTree_r(tw, node.children[1], p1f, p2f, p1, p2);
                return;
            }

            if (t1 < t2) {
                idist = 1.0f / (t1 - t2);
                side = 1;
                frac2 = (t1 + offset) * idist;
                frac = (t1 - offset) * idist;
            } else if (t1 > t2) {
                idist = 1.0f / (t1 - t2);
                side = 0;
                frac2 = (t1 - offset) * idist;
                frac = (t1 + offset) * idist;
            } else {
                side = 0;
                frac = 1.0f;
                frac2 = 0.0f;
            }

            // move up to the node
            if (frac < 0.0f) {
                frac = 0.0f;
            } else if (frac > 1.0f) {
                frac = 1.0f;
            }

            midf = p1f + (p2f - p1f) * frac;

            mid.oSet(0, p1.oGet(0) + frac * (p2.oGet(0) - p1.oGet(0)));
            mid.oSet(1, p1.oGet(1) + frac * (p2.oGet(1) - p1.oGet(1)));
            mid.oSet(2, p1.oGet(2) + frac * (p2.oGet(2) - p1.oGet(2)));

            this.TraceThroughAxialBSPTree_r(tw, node.children[side], p1f, midf, p1, mid);

            // go past the node
            if (frac2 < 0.0f) {
                frac2 = 0.0f;
            } else if (frac2 > 1.0f) {
                frac2 = 1.0f;
            }

            midf = p1f + (p2f - p1f) * frac2;

            mid.oSet(0, p1.oGet(0) + frac2 * (p2.oGet(0) - p1.oGet(0)));
            mid.oSet(1, p1.oGet(1) + frac2 * (p2.oGet(1) - p1.oGet(1)));
            mid.oSet(2, p1.oGet(2) + frac2 * (p2.oGet(2) - p1.oGet(2)));

            this.TraceThroughAxialBSPTree_r(tw, node.children[side ^ 1], midf, p2f, mid, p2);
        }

        private void TraceThroughModel(cm_traceWork_s tw) {
            float d;
            int i, numSteps;
            idVec3 start, end;
            idRotation rot = new idRotation();

            if (!tw.rotation) {
                // trace through spatial subdivision and then through leafs
                this.TraceThroughAxialBSPTree_r(tw, tw.model.node, 0, 1, tw.start, tw.end);
            } else {
                // approximate the rotation with a series of straight line movements
                // total length covered along circle
                d = tw.radius * DEG2RAD(tw.angle);
                // if more than one step
                if (d > CIRCLE_APPROXIMATION_LENGTH) {
                    // number of steps for the approximation
                    numSteps = (int) (CIRCLE_APPROXIMATION_LENGTH / d);
                    // start of approximation
                    start = new idVec3(tw.start);
                    // trace circle approximation steps through the BSP tree
                    for (i = 0; i < numSteps; i++) {
                        // calculate next point on approximated circle
                        rot.Set(tw.origin, tw.axis, tw.angle * ((float) (i + 1) / numSteps));
                        end = rot.oMultiply(start);
                        // trace through spatial subdivision and then through leafs
                        this.TraceThroughAxialBSPTree_r(tw, tw.model.node, 0, 1, start, end);
                        // no need to continue if something was hit already
                        if (tw.trace.fraction < 1.0f) {
                            return;
                        }
                        start.oSet(end);
                    }
                } else {
                    start = new idVec3(tw.start);
                }
                // last step of the approximation
                this.TraceThroughAxialBSPTree_r(tw, tw.model.node, 0, 1, start, tw.end);
            }
        }

        //        private void RecurseProcBSP_r(trace_t results, int parentNodeNum, int nodeNum, float p1f, float p2f, final idVec3 p1, final idVec3 p2);
        /*
         ===============================================================================

         Free map

         ===============================================================================
         */
        // CollisionMap_load.cpp
        private void Clear() {
            mapName.Clear();
            mapFileTime = 0;
            loaded = false;
            checkCount = 0;
            maxModels = 0;
            numModels = 0;
            models = null;
            trmPolygons = Stream.generate(cm_polygonRef_s::new)
                    .limit(trmPolygons.length)
                    .toArray(cm_polygonRef_s[]::new);
            trmBrushes[0] = null;
            trmMaterial = null;
            numProcNodes = 0;
            procNodes = null;
            getContacts = false;
            contacts = null;
            maxContacts = 0;
            numContacts = 0;
        }

        private void FreeTrmModelStructure() {
            assert (models != null);
            if (null == models[MAX_SUBMODELS]) {
                return;
            }

            for (int i = 0; i < MAX_TRACEMODEL_POLYS; i++) {
                FreePolygon(models[MAX_SUBMODELS], trmPolygons[i].p);
            }
            FreeBrush(models[MAX_SUBMODELS], trmBrushes[0].b);

            models[MAX_SUBMODELS].node.polygons = null;
            models[MAX_SUBMODELS].node.brushes = null;
            FreeModel(models[MAX_SUBMODELS]);
        }

        // model deallocation
        private void RemovePolygonReferences_r(cm_node_s node, cm_polygon_s p) {
            cm_polygonRef_s pref;

            while (node != null) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    if (pref.p == p) {
                        pref.p = null;
                        // cannot return here because we can have links down the tree due to polygon merging
                        //return;
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (p.bounds.oGet(0).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (p.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    RemovePolygonReferences_r(node.children[1], p);
                    node = node.children[0];
                }
            }
        }

        private void RemoveBrushReferences_r(cm_node_s node, cm_brush_s b) {
            cm_brushRef_s bref;

            while (node != null) {
                for (bref = node.brushes; bref != null; bref = bref.next) {
                    if (bref.b == b) {
                        bref.b = null;
                        return;
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (b.bounds.oGet(0).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (b.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    RemoveBrushReferences_r(node.children[1], b);
                    node = node.children[0];
                }
            }
        }

        private void FreePolygon(cm_model_s model, cm_polygon_s poly) {
            model.numPolygons--;
            model.polygonMemory -= cm_polygon_s.BYTES + (poly.numEdges - 1) * Integer.BYTES;
            model.polygonBlock = null;
        }

        private void FreeBrush(cm_model_s model, cm_brush_s brush) {
            model.numBrushes--;
            model.brushMemory -= cm_brush_s.BYTES + (brush.numPlanes - 1) * idPlane.BYTES;
            model.brushBlock = null;
        }

        private void FreeTree_r(cm_model_s model, cm_node_s headNode, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            cm_brushRef_s bref;
            cm_brush_s b;

            // free all polygons at this node
            for (pref = node.polygons; pref != null; pref = node.polygons) {
                p = pref.p;
                if (p != null) {
                    // remove all other references to this polygon
                    RemovePolygonReferences_r(headNode, p);
                    FreePolygon(model, p);
                }
                node.polygons = pref.next;
            }
            // free all brushes at this node
            for (bref = node.brushes; bref != null; bref = node.brushes) {
                b = bref.b;
                if (b != null) {
                    // remove all other references to this brush
                    RemoveBrushReferences_r(headNode, b);
                    FreeBrush(model, b);
                }
                node.brushes = bref.next;
            }
            // recurse down the tree
            if (node.planeType != -1) {
                FreeTree_r(model, headNode, node.children[0]);
                node.children[0] = null;
                FreeTree_r(model, headNode, node.children[1]);
                node.children[1] = null;
            }
        }

        private void FreeModel(cm_model_s model) {
            cm_polygonRefBlock_s polygonRefBlock, nextPolygonRefBlock;
            cm_brushRefBlock_s brushRefBlock, nextBrushRefBlock;
            cm_nodeBlock_s nodeBlock, nextNodeBlock;

            // free the tree structure
            if (model.node != null) {
                FreeTree_r(model, model.node, model.node);
            }
            // free blocks with polygon references
            for (polygonRefBlock = model.polygonRefBlocks; polygonRefBlock != null; polygonRefBlock = nextPolygonRefBlock) {
                nextPolygonRefBlock = polygonRefBlock.next;
                polygonRefBlock = null;
            }
            // free blocks with brush references
            for (brushRefBlock = model.brushRefBlocks; brushRefBlock != null; brushRefBlock = nextBrushRefBlock) {
                nextBrushRefBlock = brushRefBlock.next;
                brushRefBlock = null;//Mem_Free(brushRefBlock);
            }
            // free blocks with nodes
            for (nodeBlock = model.nodeBlocks; nodeBlock != null; nodeBlock = nextNodeBlock) {
                nextNodeBlock = nodeBlock.next;
                nodeBlock = null;//Mem_Free(nodeBlock);
            }
            // free block allocated polygons
            model.polygonBlock = null;//Mem_Free(model.polygonBlock);
            // free block allocated brushes
            model.brushBlock = null;//Mem_Free(model.brushBlock);
            // free edges
            model.edges = null;//Mem_Free(model.edges);
            // free vertices
            model.vertices = null;//Mem_Free(model.vertices);
            model = null;
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
        private void ReplacePolygons(cm_model_s model, cm_node_s node, cm_polygon_s p1, cm_polygon_s p2, cm_polygon_s newp) {
            cm_polygonRef_s pref, lastpref, nextpref;
            cm_polygon_s p;
            boolean linked;

            while (true) {
                linked = false;
                lastpref = null;
                for (pref = node.polygons; pref != null; pref = nextpref) {
                    nextpref = pref.next;
                    //
                    p = pref.p;
                    // if this polygon reference should change
                    if (p == p1 || p == p2) {
                        // if the new polygon is already linked at this node
                        if (linked) {
                            if (lastpref != null) {
                                lastpref.next = nextpref;
                            } else {
                                node.polygons = nextpref;
                            }
                            model.numPolygonRefs--;
                        } else {
                            pref.p = newp;
                            linked = true;
                            lastpref = pref;
                        }
                    } else {
                        lastpref = pref;
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (p1.bounds.oGet(0).oGet(node.planeType) > node.planeDist && p2.bounds.oGet(0).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (p1.bounds.oGet(1).oGet(node.planeType) < node.planeDist && p2.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    ReplacePolygons(model, node.children[1], p1, p2, newp);
                    node = node.children[0];
                }
            }
        }

        private cm_polygon_s TryMergePolygons(cm_model_s model, cm_polygon_s p1, cm_polygon_s p2) {
            int i, j, nexti, prevj;
            int p1BeforeShare, p1AfterShare, p2BeforeShare, p2AfterShare;
            int[] newEdges = new int[CM_MAX_POLYGON_EDGES];
            int newNumEdges;
            int edgeNum, edgeNum1, edgeNum2;
            int[] newEdgeNum1 = new int[1];
            int[] newEdgeNum2 = new int[1];
            cm_edge_s edge;
            cm_polygon_s newp;
            idVec3 delta = new idVec3();
            idVec3 normal = new idVec3();
            float dot;
            boolean keep1, keep2;

            if (p1.material != p2.material) {
                return null;
            }
            if (Math.abs(p1.plane.Dist() - p2.plane.Dist()) > NORMAL_EPSILON) {
                return null;
            }
            for (i = 0; i < 3; i++) {
                if (Math.abs(p1.plane.Normal().oGet(i) - p2.plane.Normal().oGet(i)) > NORMAL_EPSILON) {
                    return null;
                }
                if (p1.bounds.oGet(0).oGet(i) > p2.bounds.oGet(1).oGet(i)) {
                    return null;
                }
                if (p1.bounds.oGet(1).oGet(i) < p2.bounds.oGet(0).oGet(i)) {
                    return null;
                }
            }
            // this allows for merging polygons with multiple shared edges
            // polygons with multiple shared edges probably never occur tho ;)
            p1BeforeShare = p1AfterShare = p2BeforeShare = p2AfterShare = -1;
            for (i = 0; i < p1.numEdges; i++) {
                nexti = (i + 1) % p1.numEdges;
                for (j = 0; j < p2.numEdges; j++) {
                    prevj = (j + p2.numEdges - 1) % p2.numEdges;
                    //
                    if (Math.abs(p1.edges[i]) != Math.abs(p2.edges[j])) {
                        // if the next edge of p1 and the previous edge of p2 are the same
                        if (Math.abs(p1.edges[nexti]) == Math.abs(p2.edges[prevj])) {
                            // if both polygons don't use the edge in the same direction
                            if (p1.edges[nexti] != p2.edges[prevj]) {
                                p1BeforeShare = i;
                                p2AfterShare = j;
                            }
                            break;
                        }
                    } // if both polygons don't use the edge in the same direction
                    else if (p1.edges[i] != p2.edges[j]) {
                        // if the next edge of p1 and the previous edge of p2 are not the same
                        if (Math.abs(p1.edges[nexti]) != Math.abs(p2.edges[prevj])) {
                            p1AfterShare = nexti;
                            p2BeforeShare = prevj;
                            break;
                        }
                    }
                }
            }
            if (p1BeforeShare < 0 || p1AfterShare < 0 || p2BeforeShare < 0 || p2AfterShare < 0) {
                return null;
            }

            // check if the new polygon would still be convex
            edgeNum = p1.edges[p1BeforeShare];
            edge = model.edges[Math.abs(edgeNum)];
            delta.oSet(model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p.oMinus(
                    model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p));
            normal.oSet(p1.plane.Normal().Cross(delta));
            normal.Normalize();

            edgeNum = p2.edges[p2AfterShare];
            edge = model.edges[Math.abs(edgeNum)];
            delta.oSet(model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p.oMinus(
                    model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p));

            dot = delta.oMultiply(normal);
            if (dot < -CONTINUOUS_EPSILON) {
                return null;            // not a convex polygon
            }
            keep1 = (dot > CONTINUOUS_EPSILON);

            edgeNum = p2.edges[p2BeforeShare];
            edge = model.edges[Math.abs(edgeNum)];
            delta = model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p.oMinus(
                    model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p);
            normal = p1.plane.Normal().Cross(delta);
            normal.Normalize();

            edgeNum = p1.edges[p1AfterShare];
            edge = model.edges[Math.abs(edgeNum)];
            delta.oSet(model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p.oMinus(
                    model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p));

            dot = delta.oMultiply(normal);
            if (dot < -CONTINUOUS_EPSILON) {
                return null;            // not a convex polygon
            }
            keep2 = (dot > CONTINUOUS_EPSILON);

            newEdgeNum1[0] = newEdgeNum2[0] = 0;
            // get new edges if we need to replace colinear ones
            if (!keep1) {
                edgeNum1 = p1.edges[p1BeforeShare];
                edgeNum2 = p2.edges[p2AfterShare];
                GetEdge(model, model.vertices[model.edges[Math.abs(edgeNum1)].vertexNum[INTSIGNBITSET(edgeNum1)]].p,
                        model.vertices[model.edges[Math.abs(edgeNum2)].vertexNum[INTSIGNBITNOTSET(edgeNum2)]].p,
                        newEdgeNum1, new CInt(-1));
                if (newEdgeNum1[0] == 0) {
                    keep1 = true;
                }
            }
            if (!keep2) {
                edgeNum1 = p2.edges[p2BeforeShare];
                edgeNum2 = p1.edges[p1AfterShare];
                GetEdge(model, model.vertices[model.edges[Math.abs(edgeNum1)].vertexNum[INTSIGNBITSET(edgeNum1)]].p,
                        model.vertices[model.edges[Math.abs(edgeNum2)].vertexNum[INTSIGNBITNOTSET(edgeNum2)]].p,
                        newEdgeNum2, new CInt(-1));
                if (newEdgeNum2[0] == 0) {
                    keep2 = true;
                }
            }
            // set edges for new polygon
            newNumEdges = 0;
            if (!keep2) {
                newEdges[newNumEdges++] = newEdgeNum2[0];
            }
            if (p1AfterShare < p1BeforeShare) {
                for (i = p1AfterShare + (!keep2 ? 1 : 0); i <= p1BeforeShare - (!keep1 ? 1 : 0); i++) {
                    newEdges[newNumEdges++] = p1.edges[i];
                }
            } else {
                for (i = p1AfterShare + (!keep2 ? 1 : 0); i < p1.numEdges; i++) {
                    newEdges[newNumEdges++] = p1.edges[i];
                }
                for (i = 0; i <= p1BeforeShare - (!keep1 ? 1 : 0); i++) {
                    newEdges[newNumEdges++] = p1.edges[i];
                }
            }
            if (!keep1) {
                newEdges[newNumEdges++] = newEdgeNum1[0];
            }
            if (p2AfterShare < p2BeforeShare) {
                for (i = p2AfterShare + (!keep1 ? 1 : 0); i <= p2BeforeShare - (!keep2 ? 1 : 0); i++) {
                    newEdges[newNumEdges++] = p2.edges[i];
                }
            } else {
                for (i = p2AfterShare + (!keep1 ? 1 : 0); i < p2.numEdges; i++) {
                    newEdges[newNumEdges++] = p2.edges[i];
                }
                for (i = 0; i <= p2BeforeShare - (!keep2 ? 1 : 0); i++) {
                    newEdges[newNumEdges++] = p2.edges[i];
                }
            }

            newp = AllocPolygon(model, newNumEdges);
            newp.oSet(p1);//memcpy( newp, p1, sizeof(cm_polygon_t) );
            System.arraycopy(newEdges, 0, newp.edges, 0, newNumEdges);//memcpy( newp.edges, newEdges, newNumEdges * sizeof(int) );
            newp.numEdges = newNumEdges;
            newp.checkcount = 0;
            // increase usage count for the edges of this polygon
            for (i = 0; i < newp.numEdges; i++) {
                if (!keep1 && newp.edges[i] == newEdgeNum1[0]) {
                    continue;
                }
                if (!keep2 && newp.edges[i] == newEdgeNum2[0]) {
                    continue;
                }
                model.edges[Math.abs(newp.edges[i])].numUsers++;
            }
            // create new bounds from the merged polygons
            newp.bounds = p1.bounds.oPlus(p2.bounds);

            return newp;
        }

        /*
         ===============================================================================

         Find internal edges

         ===============================================================================
         */

        private boolean MergePolygonWithTreePolygons(cm_model_s model, cm_node_s node, cm_polygon_s polygon) {
            int i;
            cm_polygonRef_s pref;
            cm_polygon_s p, newp;

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    //
                    if (p == polygon) {
                        continue;
                    }
                    //
                    newp = TryMergePolygons(model, polygon, p);
                    // if polygons were merged
                    if (newp != null) {
                        model.numMergedPolys++;
                        // replace links to the merged polygons with links to the new polygon
                        ReplacePolygons(model, model.node, polygon, p, newp);
                        // decrease usage count for edges of both merged polygons
                        for (i = 0; i < polygon.numEdges; i++) {
                            model.edges[Math.abs(polygon.edges[i])].numUsers--;
                        }
                        for (i = 0; i < p.numEdges; i++) {
                            model.edges[Math.abs(p.edges[i])].numUsers--;
                        }
                        // free merged polygons
                        FreePolygon(model, polygon);
                        FreePolygon(model, p);

                        return true;
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (polygon.bounds.oGet(0).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (polygon.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    if (MergePolygonWithTreePolygons(model, node.children[1], polygon)) {
                        return true;
                    }
                    node = node.children[0];
                }
            }
            return false;
        }

        /*
         =============
         idCollisionModelManagerLocal::MergeTreePolygons

         try to merge any two polygons with the same surface flags and the same contents
         =============
         */
        private void MergeTreePolygons(cm_model_s model, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            boolean merge;

            while (true) {
                do {
                    merge = false;
                    for (pref = node.polygons; pref != null; pref = pref.next) {
                        p = pref.p;
                        // if we checked this polygon already
                        if (p.checkcount == checkCount) {
                            continue;
                        }
                        p.checkcount = checkCount;
                        // try to merge this polygon with other polygons in the tree
                        if (MergePolygonWithTreePolygons(model, model.node, p)) {
                            merge = true;
                            break;
                        }
                    }
                } while (merge);
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                MergeTreePolygons(model, node.children[1]);
                node = node.children[0];
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
        private boolean PointInsidePolygon(cm_model_s model, cm_polygon_s p, idVec3 v) {
            int i, edgeNum;
            idVec3 v1 = new idVec3(), v2 = new idVec3(), dir1 = new idVec3(), dir2 = new idVec3(), vec = new idVec3();
            cm_edge_s edge;

            for (i = 0; i < p.numEdges; i++) {
                edgeNum = p.edges[i];
                edge = model.edges[Math.abs(edgeNum)];
                //
                v1.oSet(model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p);
                v2.oSet(model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p);
                dir1.oSet(v2.oMinus(v1));
                vec.oSet(v.oMinus(v1));
                dir2.oSet(dir1.Cross(p.plane.Normal()));
                if (vec.oMultiply(dir2) > VERTEX_EPSILON) {
                    return false;
                }
            }
            return true;
        }

        private void FindInternalEdgesOnPolygon(cm_model_s model, cm_polygon_s p1, cm_polygon_s p2) {
            int i, j, k, edgeNum;
            cm_edge_s edge;
            idVec3 v1 = new idVec3(), v2 = new idVec3(), dir1 = new idVec3(), dir2 = new idVec3();
            float d;

            // bounds of polygons should overlap or touch
            for (i = 0; i < 3; i++) {
                if (p1.bounds.oGet(0).oGet(i) > p2.bounds.oGet(1).oGet(i)) {
                    return;
                }
                if (p1.bounds.oGet(1).oGet(i) < p2.bounds.oGet(0).oGet(i)) {
                    return;
                }
            }
            //
            // FIXME: doubled geometry causes problems
            //
            for (i = 0; i < p1.numEdges; i++) {
                edgeNum = p1.edges[i];
                edge = model.edges[Math.abs(edgeNum)];
                // if already an internal edge
                if (edge.internal != 0) {
                    continue;
                }
                //
                v1.oSet(model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p);
                v2.oSet(model.vertices[edge.vertexNum[INTSIGNBITNOTSET(edgeNum)]].p);
                // if either of the two vertices is outside the bounds of the other polygon
                for (k = 0; k < 3; k++) {
                    d = p2.bounds.oGet(1).oGet(k) + VERTEX_EPSILON;
                    if (v1.oGet(k) > d || v2.oGet(k) > d) {
                        break;
                    }
                    d = p2.bounds.oGet(0).oGet(k) - VERTEX_EPSILON;
                    if (v1.oGet(k) < d || v2.oGet(k) < d) {
                        break;
                    }
                }
                if (k < 3) {
                    continue;
                }
                //
                k = Math.abs(edgeNum);
                for (j = 0; j < p2.numEdges; j++) {
                    if (k == Math.abs(p2.edges[j])) {
                        break;
                    }
                }
                // if the edge is shared between the two polygons
                if (j < p2.numEdges) {
                    // if the edge is used by more than 2 polygons
                    if (edge.numUsers > 2) {
                        // could still be internal but we'd have to test all polygons using the edge
                        continue;
                    }
                    // if the edge goes in the same direction for both polygons
                    if (edgeNum == p2.edges[j]) {
                        // the polygons can lay ontop of each other or one can obscure the other
                        continue;
                    }
                } // the edge was not shared
                else {
                    // both vertices should be on the plane of the other polygon
                    d = p2.plane.Distance(v1);
                    if (Math.abs(d) > VERTEX_EPSILON) {
                        continue;
                    }
                    d = p2.plane.Distance(v2);
                    if (Math.abs(d) > VERTEX_EPSILON) {
                        continue;
                    }
                }
                // the two polygon plane normals should face towards each other
                dir1.oSet(v2.oMinus(v1));
                dir2.oSet(p1.plane.Normal().Cross(dir1));
                if (p2.plane.Normal().oMultiply(dir2) < 0) {
                    //continue;
                    break;
                }
                // if the edge was not shared
                if (j >= p2.numEdges) {
                    // both vertices of the edge should be inside the winding of the other polygon
                    if (!PointInsidePolygon(model, p2, v1)) {
                        continue;
                    }
                    if (!PointInsidePolygon(model, p2, v2)) {
                        continue;
                    }
                }
                // we got another internal edge
                edge.internal = 1;//true;
                model.numInternalEdges++;
            }
        }

        private void FindInternalPolygonEdges(cm_model_s model, cm_node_s node, cm_polygon_s polygon) {
            cm_polygonRef_s pref;
            cm_polygon_s p;

            if (polygon.material.GetCullType() == CT_TWO_SIDED || polygon.material.ShouldCreateBackSides()) {
                return;
            }

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    //
                    // FIXME: use some sort of additional checkcount because currently
                    //			polygons can be checked multiple times
                    //
                    // if the polygons don't have the same contents
                    if (p.contents != polygon.contents) {
                        continue;
                    }
                    if (p == polygon) {
                        continue;
                    }
                    FindInternalEdgesOnPolygon(model, polygon, p);
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (polygon.bounds.oGet(1).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (polygon.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    FindInternalPolygonEdges(model, node.children[1], polygon);
                    node = node.children[0];
                }
            }
        }

        private void FindInternalEdges(cm_model_s model, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        continue;
                    }
                    p.checkcount = checkCount;

                    FindInternalPolygonEdges(model, model.node, p);

                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                FindInternalEdges(model, node.children[1]);
                node = node.children[0];
            }
        }


        /*
         ===============================================================================

         Proc BSP tree for data pruning

         ===============================================================================
         */
        // loading of proc BSP tree
        private void ParseProcNodes(idLexer src) {
            src.ExpectTokenString("{");

            numProcNodes = src.ParseInt();
            if (numProcNodes < 0) {
                src.Error("ParseProcNodes: bad numProcNodes");
            }
            procNodes = new cm_procNode_s[numProcNodes];//Mem_ClearedAlloc(numProcNodes /*sizeof( cm_procNode_t )*/);

            for (int i = 0; i < numProcNodes; i++) {
                cm_procNode_s node;

                node = procNodes[i] = new cm_procNode_s();

                src.Parse1DMatrix(4, node.plane);
                node.children[0] = src.ParseInt();
                node.children[1] = src.ParseInt();
            }

            src.ExpectTokenString("}");
        }

        /*
         ================
         idCollisionModelManagerLocal::LoadProcBSP

         FIXME: if the nodes would be at the start of the .proc file it would speed things up considerably
         ================
         */
        private void LoadProcBSP(final String name) {
            idStr filename;
            idToken token = new idToken();
            idLexer src;

            // load it
            filename = new idStr(name);
            filename.SetFileExtension(PROC_FILE_EXT);
            src = new idLexer(name, LEXFL_NOSTRINGCONCAT | LEXFL_NODOLLARPRECOMPILE);
            if (!src.IsLoaded()) {
                common.Warning("idCollisionModelManagerLocal::LoadProcBSP: couldn't load %s", filename.toString());
                src = null;
                return;
            }

            if (!src.ReadToken(token) || token.Icmp(PROC_FILE_ID) != 0) {
                common.Warning("idCollisionModelManagerLocal::LoadProcBSP: bad id '%s' instead of '%s'", token.toString(), PROC_FILE_ID);
                src = null;
                return;
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("model")) {
                    src.SkipBracedSection();
                    continue;
                }

                if (token.equals("shadowModel")) {
                    src.SkipBracedSection();
                    continue;
                }

                if (token.equals("interAreaPortals")) {
                    src.SkipBracedSection();
                    continue;
                }

                if (token.equals("nodes")) {
                    ParseProcNodes(src);
                    break;
                }

                src.Error("idCollisionModelManagerLocal::LoadProcBSP: bad token \"%s\"", token.toString());
            }

//	delete src;
        }

        /*
         ===============================================================================

         Optimisation, removal of polygons contained within brushes or solid

         ===============================================================================
         */
        // removal of contained polygons
        private boolean R_ChoppedAwayByProcBSP(int nodeNum, idFixedWinding w, final idVec3 normal, final idVec3 origin, final float radius) {
            int res;
            idFixedWinding back = new idFixedWinding();
            cm_procNode_s node;
            float dist;

            do {
                node = procNodes[nodeNum];
                dist = node.plane.Normal().oMultiply(origin) + node.plane.oGet(3);
                if (dist > radius) {
                    res = SIDE_FRONT;
                } else if (dist < -radius) {
                    res = SIDE_BACK;
                } else {
                    res = w.Split(back, node.plane, CHOP_EPSILON);
                }
                if (res == SIDE_FRONT) {
                    nodeNum = node.children[0];
                } else if (res == SIDE_BACK) {
                    nodeNum = node.children[1];
                } else if (res == SIDE_ON) {
                    // continue with the side the winding faces
                    if (node.plane.Normal().oMultiply(normal) > 0.0f) {
                        nodeNum = node.children[0];
                    } else {
                        nodeNum = node.children[1];
                    }
                } else {
                    // if either node is not solid
                    if (node.children[0] < 0 || node.children[1] < 0) {
                        return false;
                    }
                    // only recurse if the node is not solid
                    if (node.children[1] > 0) {
                        if (!R_ChoppedAwayByProcBSP(node.children[1], back, normal, origin, radius)) {
                            return false;
                        }
                    }
                    nodeNum = node.children[0];
                }
            } while (nodeNum > 0);

            return nodeNum >= 0;
        }

        private boolean ChoppedAwayByProcBSP(final idFixedWinding w, final idPlane plane, int contents) {
            idFixedWinding neww;
            idBounds bounds = new idBounds();
            float radius;
            idVec3 origin = new idVec3();

            // if the .proc file has no BSP tree
            if (procNodes == null) {
                return false;
            }
            // don't chop if the polygon is not solid
            if (0 == (contents & CONTENTS_SOLID)) {
                return false;
            }
            // make a local copy of the winding
            neww = new idFixedWinding(w);
            neww.GetBounds(bounds);
            origin.oSet((bounds.oGet(1).oMinus(bounds.oGet(0))).oMultiply(0.5f));
            radius = origin.Length() + CHOP_EPSILON;
            origin.oSet(bounds.oGet(0).oPlus(origin));
            //
            return R_ChoppedAwayByProcBSP(0, neww, plane.Normal(), origin, radius);
        }

        /*
         =============
         idCollisionModelManagerLocal::ChopWindingWithBrush

         returns the least number of winding fragments outside the brush
         =============
         */
        private void ChopWindingListWithBrush(cm_windingList_s list, cm_brush_s b) {
            int i, k, res, startPlane, planeNum, bestNumWindings;
            idFixedWinding back = new idFixedWinding(), front;
            idPlane plane;
            boolean chopped;
            int[] sidedness = new int[MAX_POINTS_ON_WINDING];
            float dist;

            if (b.numPlanes > MAX_POINTS_ON_WINDING) {
                return;
            }

            // get sidedness for the list of windings
            for (i = 0; i < b.numPlanes; i++) {
                plane = b.planes[i].oNegative();

                dist = plane.Distance(list.origin);
                if (dist > list.radius) {
                    sidedness[i] = SIDE_FRONT;
                } else if (dist < -list.radius) {
                    sidedness[i] = SIDE_BACK;
                } else {
                    sidedness[i] = list.bounds.PlaneSide(plane);
                    if (sidedness[i] == PLANESIDE_FRONT) {
                        sidedness[i] = SIDE_FRONT;
                    } else if (sidedness[i] == PLANESIDE_BACK) {
                        sidedness[i] = SIDE_BACK;
                    } else {
                        sidedness[i] = SIDE_CROSS;
                    }
                }
            }

            cm_outList.numWindings = 0;
            for (k = 0; k < list.numWindings; k++) {
                //
                startPlane = 0;
                bestNumWindings = 1 + b.numPlanes;
                chopped = false;
                do {
                    front = list.w[k];
                    cm_tmpList.numWindings = 0;
                    for (planeNum = startPlane, i = 0; i < b.numPlanes; i++, planeNum++) {

                        if (planeNum >= b.numPlanes) {
                            planeNum = 0;
                        }

                        res = sidedness[planeNum];

                        if (res == SIDE_CROSS) {
                            plane = b.planes[planeNum].oNegative();
                            res = front.Split(back, plane, CHOP_EPSILON);
                        }

                        // NOTE:	disabling this can create gaps at places where Z-fighting occurs
                        //			Z-fighting should not occur but what if there is a decal brush side
                        //			with exactly the same size as another brush side ?
                        // only leave windings on a brush if the winding plane and brush side plane face the same direction
                        if (res == SIDE_ON && list.primitiveNum >= 0 && (list.normal.oMultiply(b.planes[planeNum].Normal())) > 0) {
                            // return because all windings in the list will be on this brush side plane
                            return;
                        }

                        if (res == SIDE_BACK) {
                            if (cm_outList.numWindings >= MAX_WINDING_LIST) {
                                common.Warning("idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings", list.primitiveNum, MAX_WINDING_LIST);
                                return;
                            }
                            // winding and brush didn't intersect, store the original winding
                            cm_outList.w[cm_outList.numWindings] = list.w[k];
                            cm_outList.numWindings++;
                            chopped = false;
                            break;
                        }

                        if (res == SIDE_CROSS) {
                            if (cm_tmpList.numWindings >= MAX_WINDING_LIST) {
                                common.Warning("idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings", list.primitiveNum, MAX_WINDING_LIST);
                                return;
                            }
                            // store the front winding in the temporary list
                            cm_tmpList.w[cm_tmpList.numWindings] = back;
                            cm_tmpList.numWindings++;
                            chopped = true;
                        }

                        // if already found a start plane which generates less fragments
                        if (cm_tmpList.numWindings >= bestNumWindings) {
                            break;
                        }
                    }

                    // find the best start plane to get the least number of fragments outside the brush
                    if (cm_tmpList.numWindings < bestNumWindings) {
                        bestNumWindings = cm_tmpList.numWindings;
                        // store windings from temporary list in the out list
                        for (i = 0; i < cm_tmpList.numWindings; i++) {
                            if (cm_outList.numWindings + i >= MAX_WINDING_LIST) {
                                common.Warning("idCollisionModelManagerLocal::ChopWindingWithBrush: primitive %d more than %d windings", list.primitiveNum, MAX_WINDING_LIST);
                                return;
                            }
                            cm_outList.w[cm_outList.numWindings + i] = cm_tmpList.w[i];
                        }
                        // if only one winding left then we can't do any better
                        if (bestNumWindings == 1) {
                            break;
                        }
                    }

                    // try the next start plane
                    startPlane++;

                } while (chopped && startPlane < b.numPlanes);
                //
                if (chopped) {
                    cm_outList.numWindings += bestNumWindings;
                }
            }
            for (k = 0; k < cm_outList.numWindings; k++) {
                list.w[k] = cm_outList.w[k];
            }
            list.numWindings = cm_outList.numWindings;
        }

        private void R_ChopWindingListWithTreeBrushes(cm_windingList_s list, cm_node_s node) {
            int i;
            cm_brushRef_s bref;
            cm_brush_s b;

            while (true) {
                for (bref = node.brushes; bref != null; bref = bref.next) {
                    b = bref.b;
                    // if we checked this brush already
                    if (b.checkcount == checkCount) {
                        continue;
                    }
                    b.checkcount = checkCount;
                    // if the windings in the list originate from this brush
                    if (b.primitiveNum == list.primitiveNum) {
                        continue;
                    }
                    // if brush has a different contents
                    if (b.contents != list.contents) {
                        continue;
                    }
                    // brush bounds and winding list bounds should overlap
                    for (i = 0; i < 3; i++) {
                        if (list.bounds.oGet(0).oGet(i) > b.bounds.oGet(1).oGet(i)) {
                            break;
                        }
                        if (list.bounds.oGet(1).oGet(i) < b.bounds.oGet(0).oGet(i)) {
                            break;
                        }
                    }
                    if (i < 3) {
                        continue;
                    }
                    // chop windings in the list with brush
                    ChopWindingListWithBrush(list, b);
                    // if all windings are chopped away we're done
                    if (0 == list.numWindings) {
                        return;
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                if (list.bounds.oGet(0).oGet(node.planeType) > node.planeDist) {
                    node = node.children[0];
                } else if (list.bounds.oGet(1).oGet(node.planeType) < node.planeDist) {
                    node = node.children[1];
                } else {
                    R_ChopWindingListWithTreeBrushes(list, node.children[1]);
                    if (0 == list.numWindings) {
                        return;
                    }
                    node = node.children[0];
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
        private idFixedWinding WindingOutsideBrushes(idFixedWinding w, final idPlane plane, int contents, int patch, cm_node_s headNode) {
            int i, windingLeft;

            cm_windingList.bounds.Clear();
            for (i = 0; i < w.GetNumPoints(); i++) {
                cm_windingList.bounds.AddPoint(w.oGet(i).ToVec3());
            }

            // new idVec3(val)?
            cm_windingList.origin = new idVec3((cm_windingList.bounds.oGet(1).oMinus(cm_windingList.bounds.oGet(0)).oMultiply(0.5f)));
            cm_windingList.radius = cm_windingList.origin.Length() + CHOP_EPSILON;
            cm_windingList.origin = new idVec3(cm_windingList.bounds.oGet(0).oPlus(cm_windingList.origin));
            cm_windingList.bounds.oGet(0).oMinSet(new idVec3(CHOP_EPSILON, CHOP_EPSILON, CHOP_EPSILON));
            cm_windingList.bounds.oGet(1).oPluSet(new idVec3(CHOP_EPSILON, CHOP_EPSILON, CHOP_EPSILON));

            cm_windingList.w[0] = new idFixedWinding(w);
            cm_windingList.numWindings = 1;
            cm_windingList.normal = new idVec3(plane.Normal());
            cm_windingList.contents = contents;
            cm_windingList.primitiveNum = patch;
            //
            checkCount++;
            R_ChopWindingListWithTreeBrushes(cm_windingList, headNode);
            //
            if (0 == cm_windingList.numWindings) {
                return null;
            }
            if (cm_windingList.numWindings == 1) {
                return cm_windingList.w[0];
            }
            // if not the world model
            if (numModels != 0) {
                return w;
            }
            // check if winding fragments would be chopped away by the proc BSP tree
            windingLeft = -1;
            for (i = 0; i < cm_windingList.numWindings; i++) {
                if (!ChoppedAwayByProcBSP(cm_windingList.w[i], plane, contents)) {
                    if (windingLeft >= 0) {
                        return w;
                    }
                    windingLeft = i;
                }
            }
            if (windingLeft >= 0) {
                return cm_windingList.w[windingLeft];
            }
            return null;
        }

        /*
         ===============================================================================

         Trace model to general collision model

         ===============================================================================
         */
        // creation of axial BSP tree
        private cm_model_s AllocModel() {
            cm_model_s model;

            model = new cm_model_s();
            model.contents = 0;
            model.isConvex = false;
            model.maxVertices = 0;
            model.numVertices = 0;
            model.vertices = null;
            model.maxEdges = 0;
            model.numEdges = 0;
            model.edges = null;
            model.node = null;
            model.nodeBlocks = null;
            model.polygonRefBlocks = null;
            model.brushRefBlocks = null;
            model.polygonBlock = null;
            model.brushBlock = null;
            model.numPolygons = model.polygonMemory
                    = model.numBrushes = model.brushMemory
                    = model.numNodes = model.numBrushRefs
                    = model.numPolygonRefs = model.numInternalEdges
                    = model.numSharpEdges = model.numRemovedPolys
                    = model.numMergedPolys = model.usedMemory = 0;

            return model;
        }

        private cm_node_s AllocNode(cm_model_s model, int blockSize) {
            int i;
            cm_node_s node;
            cm_nodeBlock_s nodeBlock;

            if (null == model.nodeBlocks || null == model.nodeBlocks.nextNode) {
                nodeBlock = new cm_nodeBlock_s();
                nodeBlock.nextNode = new cm_node_s();
                nodeBlock.next = model.nodeBlocks;
                model.nodeBlocks = nodeBlock;
                node = nodeBlock.nextNode;
                for (i = 0; i < blockSize - 1; i++) {
                    node.parent = new cm_node_s();
                    node = node.parent;
                }
                node.parent = null;
            }

            node = model.nodeBlocks.nextNode;
            model.nodeBlocks.nextNode = node.parent;
            node.parent = null;

            return node;
        }

        private cm_polygonRef_s AllocPolygonReference(cm_model_s model, int blockSize) {
            int i;
            cm_polygonRef_s pref;
            cm_polygonRefBlock_s prefBlock;

            if (null == model.polygonRefBlocks || null == model.polygonRefBlocks.nextRef) {
                prefBlock = new cm_polygonRefBlock_s();
                prefBlock.nextRef = new cm_polygonRef_s();
                prefBlock.next = model.polygonRefBlocks;
                model.polygonRefBlocks = prefBlock;
                pref = prefBlock.nextRef;
                for (i = 0; i < blockSize - 1; i++) {
                    pref.next = new cm_polygonRef_s();
                    pref = pref.next;
                }
                pref.next = null;
            }

            pref = model.polygonRefBlocks.nextRef;
            model.polygonRefBlocks.nextRef = pref.next;

            return pref;
        }

        private cm_brushRef_s AllocBrushReference(cm_model_s model, int blockSize) {
            int i;
            cm_brushRef_s bref;
            cm_brushRefBlock_s brefBlock;

            if (null == model.brushRefBlocks || null == model.brushRefBlocks.nextRef) {
                brefBlock = new cm_brushRefBlock_s();
                brefBlock.nextRef = new cm_brushRef_s();
                brefBlock.next = model.brushRefBlocks;
                model.brushRefBlocks = brefBlock;
                bref = brefBlock.nextRef;
                for (i = 0; i < blockSize - 1; i++) {
                    bref.next = new cm_brushRef_s();
                    bref = bref.next;
                }
                bref.next = null;
            }

            bref = model.brushRefBlocks.nextRef;
            model.brushRefBlocks.nextRef = bref.next;

            return bref;
        }

        private cm_polygon_s AllocPolygon(cm_model_s model, int numEdges) {
            cm_polygon_s poly;
            int size;

            size = cm_polygon_s.BYTES + (numEdges - 1) * Integer.SIZE;//sizeof( cm_polygon_t ) + ( numEdges - 1 ) * sizeof( poly.edges[0] );
            model.numPolygons++;
            model.polygonMemory += size;
            poly = new cm_polygon_s();// Mem_Alloc(size);
            poly.edges = new int[numEdges];
            return poly;
        }

        private cm_brush_s AllocBrush(cm_model_s model, int numPlanes) {
            cm_brush_s brush;
            int size;

            size = cm_brush_s.BYTES + (numPlanes - 1) * Integer.SIZE;//sizeof( cm_brush_t ) + ( numPlanes - 1 ) * sizeof( brush.planes[0] );
            model.numBrushes++;
            model.brushMemory += size;
            brush = new cm_brush_s();// Mem_Alloc(size);
            brush.planes = new idPlane[numPlanes];
            return brush;
        }

        private void AddPolygonToNode(cm_model_s model, cm_node_s node, cm_polygon_s p) {
            cm_polygonRef_s pref;

            pref = AllocPolygonReference(model, model.numPolygonRefs < REFERENCE_BLOCK_SIZE_SMALL ? REFERENCE_BLOCK_SIZE_SMALL : REFERENCE_BLOCK_SIZE_LARGE);
            pref.p = p;
            pref.next = node.polygons;
            node.polygons = pref;
            model.numPolygonRefs++;
        }

        private void AddBrushToNode(cm_model_s model, cm_node_s node, cm_brush_s b) {
            cm_brushRef_s bref;

            bref = AllocBrushReference(model, model.numBrushRefs < REFERENCE_BLOCK_SIZE_SMALL ? REFERENCE_BLOCK_SIZE_SMALL : REFERENCE_BLOCK_SIZE_LARGE);
            bref.b = b;
            bref.next = node.brushes;
            node.brushes = bref;
            model.numBrushRefs++;
        }

        private void SetupTrmModelStructure() {
            int i;
            cm_node_s node;
            cm_model_s model;

            // setup model
            model = AllocModel();

            assert (models != null);
            models[MAX_SUBMODELS] = model;
            // create node to hold the collision data
            node = AllocNode(model, 1);
            node.planeType = -1;
            model.node = node;
            // allocate vertex and edge arrays
            model.numVertices = 0;
            model.maxVertices = MAX_TRACEMODEL_VERTS;
            model.vertices = cm_vertex_s.generateArray(model.maxVertices);
            model.numEdges = 0;
            model.maxEdges = MAX_TRACEMODEL_EDGES + 1;
            model.edges = cm_edge_s.generateArray(model.maxEdges);
            // create a material for the trace model polygons
            trmMaterial = declManager.FindMaterial("_tracemodel", false);
            if (null == trmMaterial) {
                common.FatalError("_tracemodel material not found");
            }

            // allocate polygons
            for (i = 0; i < MAX_TRACEMODEL_POLYS; i++) {
                trmPolygons[i] = AllocPolygonReference(model, MAX_TRACEMODEL_POLYS);
                trmPolygons[i].p = AllocPolygon(model, MAX_TRACEMODEL_POLYEDGES);
                trmPolygons[i].p.bounds.Clear();
                trmPolygons[i].p.plane.Zero();
                trmPolygons[i].p.checkcount = 0;
                trmPolygons[i].p.contents = -1;        // all contents
                trmPolygons[i].p.material = trmMaterial;
                trmPolygons[i].p.numEdges = 0;
            }
            // allocate brush for position test
            trmBrushes[0] = AllocBrushReference(model, 1);
            trmBrushes[0].b = AllocBrush(model, MAX_TRACEMODEL_POLYS);
            trmBrushes[0].b.primitiveNum = 0;
            trmBrushes[0].b.bounds.Clear();
            trmBrushes[0].b.checkcount = 0;
            trmBrushes[0].b.contents = -1;        // all contents
            trmBrushes[0].b.numPlanes = 0;
        }

        private void R_FilterPolygonIntoTree(cm_model_s model, cm_node_s node, cm_polygonRef_s pref, cm_polygon_s p) {
            assert (node != null);
            while (node.planeType != -1) {
                if (CM_R_InsideAllChildren(node, p.bounds)) {
                    break;
                }
                if (p.bounds.oGet(0).oGet(node.planeType) >= node.planeDist) {
                    node = node.children[0];
                } else if (p.bounds.oGet(1).oGet(node.planeType) <= node.planeDist) {
                    node = node.children[1];
                } else {
                    R_FilterPolygonIntoTree(model, node.children[1], null, p);
                    node = node.children[0];
                }
            }
            if (pref != null) {
                pref.next = node.polygons;
                node.polygons = pref;
            } else {
                AddPolygonToNode(model, node, p);
            }
        }

        private void R_FilterBrushIntoTree(cm_model_s model, cm_node_s node, cm_brushRef_s pref, cm_brush_s b) {
            assert (node != null);
            while (node.planeType != -1) {
                if (CM_R_InsideAllChildren(node, b.bounds)) {
                    break;
                }
                if (b.bounds.oGet(0).oGet(node.planeType) >= node.planeDist) {
                    node = node.children[0];
                } else if (b.bounds.oGet(1).oGet(node.planeType) <= node.planeDist) {
                    node = node.children[1];
                } else {
                    R_FilterBrushIntoTree(model, node.children[1], null, b);
                    node = node.children[0];
                }
            }
            if (pref != null) {
                pref.next = node.brushes;
                node.brushes = pref;
            } else {
                AddBrushToNode(model, node, b);
            }
        }

        /*
         ================
         idCollisionModelManagerLocal::R_CreateAxialBSPTree

         a brush or polygon is linked in the node closest to the root where
         the brush or polygon is inside all children
         ================
         */
        private cm_node_s R_CreateAxialBSPTree(cm_model_s model, cm_node_s node, final idBounds bounds) {
            CInt planeType = new CInt();
            CFloat planeDist = new CFloat();
            cm_polygonRef_s pref, nextpref, prevpref;
            cm_brushRef_s bref, nextbref, prevbref;
            cm_node_s frontNode, backNode, n;
            idBounds frontBounds, backBounds;

            if (!CM_FindSplitter(node, bounds, planeType, planeDist)) {
                node.planeType = -1;
                return node;
            }
            // create two child nodes
            frontNode = AllocNode(model, NODE_BLOCK_SIZE_LARGE);//	memset( frontNode, 0, sizeof(cm_node_t) );
            frontNode.parent = node;
            frontNode.planeType = -1;
            //
            backNode = AllocNode(model, NODE_BLOCK_SIZE_LARGE);//	memset( backNode, 0, sizeof(cm_node_t) );
            backNode.parent = node;
            backNode.planeType = -1;
            //
            model.numNodes += 2;
            // set front node bounds
            frontBounds = new idBounds(bounds);
            frontBounds.oGet(0).oSet(planeType.getVal(), planeDist.getVal());
            // set back node bounds
            backBounds = new idBounds(bounds);
            backBounds.oGet(1).oSet(planeType.getVal(), planeDist.getVal());
            //
            node.planeType = planeType.getVal();
            node.planeDist = planeDist.getVal();
            node.children[0] = frontNode;
            node.children[1] = backNode;
            // filter polygons and brushes down the tree if necesary
            for (n = node; n != null; n = n.parent) {
                prevpref = null;
                for (pref = n.polygons; pref != null; pref = nextpref) {
                    nextpref = pref.next;
                    // if polygon is not inside all children
                    if (!CM_R_InsideAllChildren(n, pref.p.bounds)) {
                        // filter polygon down the tree
                        R_FilterPolygonIntoTree(model, n, pref, pref.p);
                        if (prevpref != null) {
                            prevpref.next = nextpref;
                        } else {
                            n.polygons = nextpref;
                        }
                    } else {
                        prevpref = pref;
                    }
                }
                prevbref = null;
                for (bref = n.brushes; bref != null; bref = nextbref) {
                    nextbref = bref.next;
                    // if brush is not inside all children
                    if (!CM_R_InsideAllChildren(n, bref.b.bounds)) {
                        // filter brush down the tree
                        R_FilterBrushIntoTree(model, n, bref, bref.b);
                        if (prevbref != null) {
                            prevbref.next = nextbref;
                        } else {
                            n.brushes = nextbref;
                        }
                    } else {
                        prevbref = bref;
                    }
                }
            }
            R_CreateAxialBSPTree(model, frontNode, frontBounds);
            R_CreateAxialBSPTree(model, backNode, backBounds);
            return node;
        }

        private cm_node_s CreateAxialBSPTree(cm_model_s model, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_brushRef_s bref;
            idBounds bounds = new idBounds();

            // get head node bounds
            bounds.Clear();
            for (pref = node.polygons; pref != null; pref = pref.next) {
                bounds.oPluSet(pref.p.bounds);
            }
            for (bref = node.brushes; bref != null; bref = bref.next) {
                bounds.oPluSet(bref.b.bounds);
            }

            // create axial BSP tree from head node
            node = R_CreateAxialBSPTree(model, node, bounds);

            return node;
        }

        /*
         ===============================================================================

         Raw polygon and brush data

         ===============================================================================
         */
        // creation of raw polygons
        private void SetupHash() {
            if (null == cm_vertexHash) {
                cm_vertexHash = new idHashIndex(VERTEX_HASH_SIZE, 1024);
            }
            if (null == cm_edgeHash) {
                cm_edgeHash = new idHashIndex(EDGE_HASH_SIZE, 1024);
            }
            // init variables used during loading and optimization
            if (null == cm_windingList) {
                cm_windingList = new cm_windingList_s();
            }
            if (null == cm_outList) {
                cm_outList = new cm_windingList_s();
            }
            if (null == cm_tmpList) {
                cm_tmpList = new cm_windingList_s();
            }
        }

        private void ShutdownHash() {
            cm_vertexHash = null;
            cm_edgeHash = null;
            cm_tmpList = null;
            cm_outList = null;
            cm_windingList = null;
        }

        private void ClearHash(idBounds bounds) {
            int i;
            float f, max;

            cm_vertexHash.Clear();
            cm_edgeHash.Clear();

            cm_modelBounds = new idBounds(bounds);
            max = bounds.oGet(1).x - bounds.oGet(0).x;
            f = bounds.oGet(1).y - bounds.oGet(0).y;
            if (f > max) {
                max = f;
            }
            cm_vertexShift = (int) (max / VERTEX_HASH_BOXSIZE);
            for (i = 0; (1 << i) < cm_vertexShift; i++) {
            }
            if (i == 0) {
                cm_vertexShift = 1;
            } else {
                cm_vertexShift = i;
            }
        }

        private int HashVec(final idVec3 vec) {
            /*
             int x, y;

             x = (((int)(vec[0] - cm_modelBounds[0].x + 0.5 )) >> cm_vertexShift) & (VERTEX_HASH_BOXSIZE-1);
             y = (((int)(vec[1] - cm_modelBounds[0].y + 0.5 )) >> cm_vertexShift) & (VERTEX_HASH_BOXSIZE-1);

             assert (x >= 0 && x < VERTEX_HASH_BOXSIZE && y >= 0 && y < VERTEX_HASH_BOXSIZE);

             return y * VERTEX_HASH_BOXSIZE + x;
             */
            int x, y, z;

            x = (((int) (vec.oGet(0) - cm_modelBounds.oGet(0).x + 0.5)) + 2) >> 2;
            y = (((int) (vec.oGet(1) - cm_modelBounds.oGet(0).y + 0.5)) + 2) >> 2;
            z = (((int) (vec.oGet(2) - cm_modelBounds.oGet(0).z + 0.5)) + 2) >> 2;
            return (x + y * VERTEX_HASH_BOXSIZE + z) & (VERTEX_HASH_SIZE - 1);
        }

        private boolean GetVertex(cm_model_s model, final idVec3 v, CInt vertexNum) {
            int i, hashKey, vn;
            idVec3 vert = new idVec3(), p = new idVec3();

            for (i = 0; i < 3; i++) {
                if (Math.abs(v.oGet(i) - idMath.Rint(v.oGet(i))) < INTEGRAL_EPSILON) {
                    vert.oSet(i, idMath.Rint(v.oGet(i)));
                } else {
                    vert.oSet(i, v.oGet(i));
                }
            }

            hashKey = HashVec(vert);

            for (vn = cm_vertexHash.First(hashKey); vn >= 0; vn = cm_vertexHash.Next(vn)) {
                p.oSet(model.vertices[vn].p);
                // first compare z-axis because hash is based on x-y plane
                if (Math.abs(vert.oGet(2) - p.oGet(2)) < VERTEX_EPSILON
                        && Math.abs(vert.oGet(0) - p.oGet(0)) < VERTEX_EPSILON
                        && Math.abs(vert.oGet(1) - p.oGet(1)) < VERTEX_EPSILON) {
                    vertexNum.setVal(vn);
                    return true;
                }
            }

            if (model.numVertices >= model.maxVertices) {
                cm_vertex_s[] oldVertices;

                // resize vertex array
                model.maxVertices = (int) (model.maxVertices * 1.5f + 1);
                oldVertices = model.vertices;
                model.vertices = cm_vertex_s.generateArray(model.maxVertices);
                System.arraycopy(oldVertices, 0, model.vertices, 0, model.numVertices);

                cm_vertexHash.ResizeIndex(model.maxVertices);
            }
            model.vertices[model.numVertices].p.oSet(vert);
            model.vertices[model.numVertices].checkcount = 0;
            vertexNum.setVal(model.numVertices);
            // add vertice to hash
            cm_vertexHash.Add(hashKey, model.numVertices);
            //
            model.numVertices++;
            return false;
        }

        private boolean GetEdge(cm_model_s model, final idVec3 v1, final idVec3 v2, int[] edgeNum, CInt v1num) {
            return GetEdge(model, v1, v2, edgeNum, 0, v1num);
        }

        private boolean GetEdge(cm_model_s model, final idVec3 v1, final idVec3 v2, int[] edgeNum, final int edgeOffset, CInt v1num) {
            int hashKey, e;
            boolean found;
            int[] vertexNum;
            CInt v2num = new CInt();

            // the first edge is a dummy
            if (model.numEdges == 0) {
                model.numEdges = 1;
            }

            if (v1num.getVal() != -1) {
                found = true;
            } else {
                found = GetVertex(model, v1, v1num);
            }
            found &= GetVertex(model, v2, v2num);
            // if both vertices are the same or snapped onto each other
            if (v1num.getVal() == v2num.getVal()) {
                edgeNum[edgeOffset] = 0;
                return true;
            }
            hashKey = cm_edgeHash.GenerateKey(v1num.getVal(), v2num.getVal());
            // if both vertices where already stored
            if (found) {
                for (e = cm_edgeHash.First(hashKey); e >= 0; e = cm_edgeHash.Next(e)) {
                    // NOTE: only allow at most two users that use the edge in opposite direction
                    if (model.edges[e].numUsers != 1) {
                        continue;
                    }

                    vertexNum = model.edges[e].vertexNum;
                    if (vertexNum[0] == v2num.getVal()) {
                        if (vertexNum[1] == v1num.getVal()) {
                            // negative for a reversed edge
                            edgeNum[edgeOffset] = -e;
                            break;
                        }
                    }
                }
                // if edge found in hash
                if (e >= 0) {
                    model.edges[e].numUsers++;
                    return true;
                }
            }
            if (model.numEdges >= model.maxEdges) {
                cm_edge_s[] oldEdges;

                // resize edge array
                model.maxEdges = (int) (model.maxEdges * 1.5f + 1);
                oldEdges = model.edges;
                model.edges = cm_edge_s.generateArray(model.maxEdges);
                System.arraycopy(oldEdges, 0, model.edges, 0, model.numEdges);

                cm_edgeHash.ResizeIndex(model.maxEdges);
            }
            // setup edge
            model.edges[model.numEdges].vertexNum[0] = v1num.getVal();
            model.edges[model.numEdges].vertexNum[1] = v2num.getVal();
            model.edges[model.numEdges].internal = 0;//false;
            model.edges[model.numEdges].checkcount = 0;
            model.edges[model.numEdges].numUsers = 1; // used by one polygon atm
            model.edges[model.numEdges].normal.Zero();
            //
            edgeNum[edgeOffset] = model.numEdges;
            // add edge to hash
            cm_edgeHash.Add(hashKey, model.numEdges);

            model.numEdges++;

            return false;
        }

        private void CreatePolygon(cm_model_s model, idFixedWinding w, final idPlane plane, final idMaterial material, int primitiveNum) {
            int i, j, edgeNum;
            CInt v1num = new CInt();
            int numPolyEdges;
            int[] polyEdges = new int[MAX_POINTS_ON_WINDING];
            idBounds bounds = new idBounds();
            cm_polygon_s p;

            // turn the winding into a sequence of edges
            numPolyEdges = 0;
            v1num.setVal(-1);        // first vertex unknown
            for (i = 0, j = 1; i < w.GetNumPoints(); i++, j++) {
                if (j >= w.GetNumPoints()) {
                    j = 0;
                }
                GetEdge(model, w.oGet(i).ToVec3(), w.oGet(j).ToVec3(), polyEdges, numPolyEdges, v1num);
                if (polyEdges[numPolyEdges] != 0) {
                    // last vertex of this edge is the first vertex of the next edge
                    v1num.setVal(model.edges[Math.abs(polyEdges[numPolyEdges])].vertexNum[INTSIGNBITNOTSET(polyEdges[numPolyEdges])]);
                    // this edge is valid so keep it
                    numPolyEdges++;
                }
            }
            // should have at least 3 edges
            if (numPolyEdges < 3) {
                return;
            }
            // the polygon is invalid if some edge is found twice
            for (i = 0; i < numPolyEdges; i++) {
                for (j = i + 1; j < numPolyEdges; j++) {
                    if (Math.abs(polyEdges[i]) == Math.abs(polyEdges[j])) {
                        return;
                    }
                }
            }
            // don't overflow max edges
            if (numPolyEdges > CM_MAX_POLYGON_EDGES) {
                common.Warning("idCollisionModelManagerLocal::CreatePolygon: polygon has more than %d edges", numPolyEdges);
                numPolyEdges = CM_MAX_POLYGON_EDGES;
            }

            w.GetBounds(bounds);

            p = AllocPolygon(model, numPolyEdges);
            p.numEdges = numPolyEdges;
            p.contents = material.GetContentFlags();
            p.material = material;
            p.checkcount = 0;
            p.plane.oSet(plane);
            p.bounds.oSet(bounds);
            for (i = 0; i < numPolyEdges; i++) {
                edgeNum = polyEdges[i];
                p.edges[i] = edgeNum;
            }
            R_FilterPolygonIntoTree(model, model.node, null, p);
        }

        /*
         ================
         idCollisionModelManagerLocal::PolygonFromWinding

         NOTE: for patches primitiveNum < 0 and abs(primitiveNum) is the real number
         ================
         */
        private void PolygonFromWinding(cm_model_s model, idFixedWinding w, final idPlane plane, final idMaterial material, int primitiveNum) {
            int contents;

            contents = material.GetContentFlags();

            // if this polygon is part of the world model
            if (numModels == 0) {
                // if the polygon is fully chopped away by the proc bsp tree
                if (ChoppedAwayByProcBSP(w, plane, contents)) {
                    model.numRemovedPolys++;
                    return;
                }
            }

            // get one winding that is not or only partly contained in brushes
            w = WindingOutsideBrushes(w, plane, contents, primitiveNum, model.node);

            // if the polygon is fully contained within a brush
            if (null == w) {
                model.numRemovedPolys++;
                return;
            }

            if (w.IsHuge()) {
                common.Warning("idCollisionModelManagerLocal::PolygonFromWinding: model %s primitive %d is degenerate", model.name, Math.abs(primitiveNum));
                return;
            }

            CreatePolygon(model, w, plane, material, primitiveNum);

            if (material.GetCullType() == CT_TWO_SIDED || material.ShouldCreateBackSides()) {
                w.ReverseSelf();
                CreatePolygon(model, w, plane.oNegative(), material, primitiveNum);
            }
        }

        private void CalculateEdgeNormals(cm_model_s model, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            cm_edge_s edge;
            float dot, s;
            int i, edgeNum;
            idVec3 dir = new idVec3();

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        continue;
                    }
                    p.checkcount = checkCount;

                    for (i = 0; i < p.numEdges; i++) {
                        edgeNum = p.edges[i];
                        edge = model.edges[Math.abs(edgeNum)];
                        if (edge.normal.oGet(0) == 0.0f && edge.normal.oGet(1) == 0.0f && edge.normal.oGet(2) == 0.0f) {
                            // if the edge is only used by this polygon
                            if (edge.numUsers == 1) {
                                dir.oSet(model.vertices[edge.vertexNum[edgeNum < 0 ? 1 : 0]].p.oMinus(model.vertices[edge.vertexNum[edgeNum > 0 ? 1 : 0]].p));
                                edge.normal = p.plane.Normal().Cross(dir);
                                edge.normal.Normalize();
                            } else {
                                // the edge is used by more than one polygon
                                edge.normal = p.plane.Normal();
                            }
                        } else {
                            dot = edge.normal.oMultiply(p.plane.Normal());
                            // if the two planes make a very sharp edge
                            if (dot < SHARP_EDGE_DOT) {
                                // max length normal pointing outside both polygons
                                dir.oSet(model.vertices[edge.vertexNum[edgeNum > 0 ? 1 : 0]].p.oMinus(model.vertices[edge.vertexNum[edgeNum < 0 ? 1 : 0]].p));
                                edge.normal.oSet(edge.normal.Cross(dir).oPlus(p.plane.Normal().Cross(dir.oNegative())));
                                edge.normal.oMulSet((0.5f / (0.5f + 0.5f * SHARP_EDGE_DOT)) / edge.normal.Length());
                                model.numSharpEdges++;
                            } else {
                                s = 0.5f / (0.5f + 0.5f * dot);
                                edge.normal.oSet((edge.normal.oPlus(p.plane.Normal())).oMultiply(s));
                            }
                        }
                    }
                }
                // if leaf node
                if (node.planeType == -1) {
                    break;
                }
                CalculateEdgeNormals(model, node.children[1]);
                node = node.children[0];
            }
        }

        private void CreatePatchPolygons(cm_model_s model, idSurface_Patch mesh, final idMaterial material, int primitiveNum) {
            int i, j;
            float dot;
            int v1, v2, v3, v4;
            idFixedWinding w = new idFixedWinding();
            idPlane plane = new idPlane();
            idVec3 d1 = new idVec3(), d2 = new idVec3();

            for (i = 0; i < mesh.GetWidth() - 1; i++) {
                for (j = 0; j < mesh.GetHeight() - 1; j++) {

                    v1 = j * mesh.GetWidth() + i;
                    v2 = v1 + 1;
                    v3 = v1 + mesh.GetWidth() + 1;
                    v4 = v1 + mesh.GetWidth();

                    d1.oSet(mesh.oGet(v2).xyz.oMinus(mesh.oGet(v1).xyz));
                    d2.oSet(mesh.oGet(v3).xyz.oMinus(mesh.oGet(v1).xyz));
                    plane.SetNormal(d1.Cross(d2));
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh.oGet(v1).xyz);
                        dot = plane.Distance(mesh.oGet(v4).xyz);
                        // if we can turn it into a quad
                        if (Math.abs(dot) < 0.1f) {
                            w.Clear();
                            w.oPluSet(mesh.oGet(v1).xyz);
                            w.oPluSet(mesh.oGet(v2).xyz);
                            w.oPluSet(mesh.oGet(v3).xyz);
                            w.oPluSet(mesh.oGet(v4).xyz);

                            PolygonFromWinding(model, w, plane, material, -primitiveNum);
                            continue;
                        } else {
                            // create one of the triangles
                            w.Clear();
                            w.oPluSet(mesh.oGet(v1).xyz);
                            w.oPluSet(mesh.oGet(v2).xyz);
                            w.oPluSet(mesh.oGet(v3).xyz);

                            PolygonFromWinding(model, w, plane, material, -primitiveNum);
                        }
                    }
                    // create the other triangle
                    d1.oSet(mesh.oGet(v3).xyz.oMinus(mesh.oGet(v1).xyz));
                    d2.oSet(mesh.oGet(v4).xyz.oMinus(mesh.oGet(v1).xyz));
                    plane.SetNormal(d1.Cross(d2));
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh.oGet(v1).xyz);

                        w.Clear();
                        w.oPluSet(mesh.oGet(v1).xyz);
                        w.oPluSet(mesh.oGet(v3).xyz);
                        w.oPluSet(mesh.oGet(v4).xyz);

                        PolygonFromWinding(model, w, plane, material, -primitiveNum);
                    }
                }
            }
        }

        private void ConvertPatch(cm_model_s model, final idMapPatch patch, int primitiveNum) {
            final idMaterial material;
            idSurface_Patch cp;

            material = declManager.FindMaterial(patch.GetMaterial());
            if (0 == (material.GetContentFlags() & CONTENTS_REMOVE_UTIL)) {
                return;
            }

            // copy the patch
            cp = new idSurface_Patch(patch);

            // if the patch has an explicit number of subdivisions use it to avoid cracks
            if (patch.GetExplicitlySubdivided()) {
                cp.SubdivideExplicit(patch.GetHorzSubdivisions(), patch.GetVertSubdivisions(), false, true);
            } else {
                cp.Subdivide(DEFAULT_CURVE_MAX_ERROR_CD, DEFAULT_CURVE_MAX_ERROR_CD, DEFAULT_CURVE_MAX_LENGTH_CD, false);
            }

            // create collision polygons for the patch
            CreatePatchPolygons(model, cp, material, primitiveNum);

            cp = null;
        }

        private void ConvertBrushSides(cm_model_s model, final idMapBrush mapBrush, int primitiveNum) {
            int i, j;
            idMapBrushSide mapSide;
            idFixedWinding w = new idFixedWinding();
            idPlane[] planes;
            idMaterial material;

            planes = new idPlane[mapBrush.GetNumSides()];
            for (i = 0; i < mapBrush.GetNumSides(); i++) {
                planes[i] = mapBrush.GetSide(i).GetPlane();
                planes[i].FixDegeneracies(DEGENERATE_DIST_EPSILON);
            }

            // create a collision polygon for each brush side
            for (i = 0; i < mapBrush.GetNumSides(); i++) {
                mapSide = mapBrush.GetSide(i);
                material = declManager.FindMaterial(mapSide.GetMaterial());
                if (0 == (material.GetContentFlags() & CONTENTS_REMOVE_UTIL)) {
                    continue;
                }
                w.BaseForPlane(planes[i].oNegative());
                for (j = 0; j < mapBrush.GetNumSides() && w.GetNumPoints() != 0; j++) {
                    if (i == j) {
                        continue;
                    }
                    w.ClipInPlace(planes[j].oNegative(), 0);
                }

                if (w.GetNumPoints() != 0) {
                    PolygonFromWinding(model, w, planes[i], material, primitiveNum);
                }
            }
        }

        private void ConvertBrush(cm_model_s model, final idMapBrush mapBrush, int primitiveNum) {
            int i, j, contents;
            idBounds bounds = new idBounds();
            idMapBrushSide mapSide;
            cm_brush_s brush;
            idPlane[] planes;
            idFixedWinding w = new idFixedWinding();
            idMaterial material = null;

            contents = 0;
            bounds.Clear();

            planes = new idPlane[mapBrush.GetNumSides()];
            for (i = 0; i < mapBrush.GetNumSides(); i++) {
                planes[i] = mapBrush.GetSide(i).GetPlane();
                planes[i].FixDegeneracies(DEGENERATE_DIST_EPSILON);
            }

            // we are only getting the bounds for the brush so there's no need
            // to create a winding for the last brush side
            for (i = 0; i < mapBrush.GetNumSides() - 1; i++) {
                mapSide = mapBrush.GetSide(i);
                material = declManager.FindMaterial(mapSide.GetMaterial());
                contents |= (material.GetContentFlags() & CONTENTS_REMOVE_UTIL);
                w.BaseForPlane(planes[i].oNegative());
                for (j = 0; j < mapBrush.GetNumSides() && w.GetNumPoints() != 0; j++) {
                    if (i == j) {
                        continue;
                    }
                    w.ClipInPlace(planes[j].oNegative(), 0);
                }

                for (j = 0; j < w.GetNumPoints(); j++) {
                    bounds.AddPoint(w.oGet(j).ToVec3());
                }
            }
            if (0 == contents) {
                return;
            }
            // create brush for position test
            brush = AllocBrush(model, mapBrush.GetNumSides());
            brush.checkcount = 0;
            brush.contents = contents;
            brush.material = material;
            brush.primitiveNum = primitiveNum;
            brush.bounds = bounds;
            brush.numPlanes = mapBrush.GetNumSides();
            for (i = 0; i < mapBrush.GetNumSides(); i++) {
                brush.planes[i] = new idPlane(planes[i]);
            }
            AddBrushToNode(model, model.node, brush);
        }

        private void PrintModelInfo(final cm_model_s model) {
            common.Printf("%6d vertices (%d KB)\n", model.numVertices, (model.numVertices /* sizeof(cm_vertex_t)*/) >> 10);
            common.Printf("%6d edges (%d KB)\n", model.numEdges, (model.numEdges /* sizeof(cm_edge_t)*/) >> 10);
            common.Printf("%6d polygons (%d KB)\n", model.numPolygons, model.polygonMemory >> 10);
            common.Printf("%6d brushes (%d KB)\n", model.numBrushes, model.brushMemory >> 10);
            common.Printf("%6d nodes (%d KB)\n", model.numNodes, (model.numNodes /* sizeof(cm_node_t)*/) >> 10);
            common.Printf("%6d polygon refs (%d KB)\n", model.numPolygonRefs, (model.numPolygonRefs /* sizeof(cm_polygonRef_t)*/) >> 10);
            common.Printf("%6d brush refs (%d KB)\n", model.numBrushRefs, (model.numBrushRefs /* sizeof(cm_brushRef_t)*/) >> 10);
            common.Printf("%6d internal edges\n", model.numInternalEdges);
            common.Printf("%6d sharp edges\n", model.numSharpEdges);
            common.Printf("%6d contained polygons removed\n", model.numRemovedPolys);
            common.Printf("%6d polygons merged\n", model.numMergedPolys);
            common.Printf("%6d KB total memory used\n", model.usedMemory >> 10);
        }

        private void AccumulateModelInfo(cm_model_s model) {
            int i;

            // accumulate statistics of all loaded models
            for (i = 0; i < numModels; i++) {
                model.numVertices += models[i].numVertices;
                model.numEdges += models[i].numEdges;
                model.numPolygons += models[i].numPolygons;
                model.polygonMemory += models[i].polygonMemory;
                model.numBrushes += models[i].numBrushes;
                model.brushMemory += models[i].brushMemory;
                model.numNodes += models[i].numNodes;
                model.numBrushRefs += models[i].numBrushRefs;
                model.numPolygonRefs += models[i].numPolygonRefs;
                model.numInternalEdges += models[i].numInternalEdges;
                model.numSharpEdges += models[i].numSharpEdges;
                model.numRemovedPolys += models[i].numRemovedPolys;
                model.numMergedPolys += models[i].numMergedPolys;
                model.usedMemory += models[i].usedMemory;
            }
        }

        private void RemapEdges(cm_node_s node, int[] edgeRemap) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            int i;

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    // if we checked this polygon already
                    if (p.checkcount == checkCount) {
                        continue;
                    }
                    p.checkcount = checkCount;
                    for (i = 0; i < p.numEdges; i++) {
                        if (p.edges[i] < 0) {
                            p.edges[i] = -edgeRemap[Math.abs(p.edges[i])];
                        } else {
                            p.edges[i] = edgeRemap[p.edges[i]];
                        }
                    }
                }
                if (node.planeType == -1) {
                    break;
                }

                RemapEdges(node.children[1], edgeRemap);
                node = node.children[0];
            }
        }

        /*
         ==================
         idCollisionModelManagerLocal::OptimizeArrays

         due to polygon merging and polygon removal the vertex and edge array
         can have a lot of unused entries.
         ==================
         */
        private void OptimizeArrays(cm_model_s model) {
            int i, newNumVertices, newNumEdges;
            int[] v;
            int[] remap;
            cm_edge_s[] oldEdges;
            cm_vertex_s[] oldVertices;

            remap = new int[Max(model.numVertices, model.numEdges)];// Mem_ClearedAlloc(Max(model.numVertices, model.numEdges) /*sizeof( int )*/);
            // get all used vertices
            for (i = 0; i < model.numEdges; i++) {
                remap[model.edges[i].vertexNum[0]] = 1;//true;
                remap[model.edges[i].vertexNum[1]] = 1;//true;
            }
            // create remap index and move vertices
            newNumVertices = 0;
            for (i = 0; i < model.numVertices; i++) {
                if (remap[i] != 0) {
                    remap[i] = newNumVertices;
                    model.vertices[newNumVertices] = model.vertices[i];
                    newNumVertices++;
                }
            }
            model.numVertices = newNumVertices;
            // change edge vertex indexes
            for (i = 1; i < model.numEdges; i++) {
                v = model.edges[i].vertexNum;
                v[0] = remap[v[0]];
                v[1] = remap[v[1]];
            }

            // create remap index and move edges
            newNumEdges = 1;
            for (i = 1; i < model.numEdges; i++) {
                // if the edge is used
                if (model.edges[i].numUsers != 0) {
                    remap[i] = newNumEdges;
                    model.edges[newNumEdges] = model.edges[i];
                    newNumEdges++;
                }
            }
            // change polygon edge indexes
            checkCount++;
            RemapEdges(model.node, remap);
            model.numEdges = newNumEdges;

            remap = null;//Mem_Free(remap);

            // realloc vertices
            oldVertices = model.vertices;
            if (oldVertices != null && oldVertices.length != 0) {
                model.vertices = cm_vertex_s.generateArray(model.numVertices);
                System.arraycopy(oldVertices, 0, model.vertices, 0, model.numVertices);
            }

            // realloc edges
            oldEdges = model.edges;
            if (oldEdges != null && oldEdges.length != 0) {
                model.edges = cm_edge_s.generateArray(model.numEdges);
                System.arraycopy(oldEdges, 0, model.edges, 0, model.numEdges);
            }
        }

        private void FinishModel(cm_model_s model) {
            // try to merge polygons
            checkCount++;
            MergeTreePolygons(model, model.node);
            // find internal edges (no mesh can ever collide with internal edges)
            checkCount++;
            FindInternalEdges(model, model.node);
            // calculate edge normals
            checkCount++;
            CalculateEdgeNormals(model, model.node);

            //common.Printf( "%s vertex hash spread is %d\n", model.name.c_str(), cm_vertexHash.GetSpread() );
            //common.Printf( "%s edge hash spread is %d\n", model.name.c_str(), cm_edgeHash.GetSpread() );
            // remove all unused vertices and edges
            OptimizeArrays(model);
            // get model bounds from brush and polygon bounds
            CM_GetNodeBounds(model.bounds, model.node);
            // get model contents
            model.contents = CM_GetNodeContents(model.node);
            // total memory used by this model
            model.usedMemory = model.numVertices * cm_vertex_s.BYTES
                    + model.numEdges * cm_edge_s.BYTES
                    + model.polygonMemory
                    + model.brushMemory
                    + model.numNodes * cm_node_s.BYTES
                    + model.numPolygonRefs * cm_polygonRef_s.BYTES
                    + model.numBrushRefs * cm_brushRef_s.BYTES;
        }

        private void BuildModels(final idMapFile mapFile) {
            int i;
            idMapEntity mapEnt;

            idTimer timer = new idTimer();
            timer.Start();

            if (!LoadCollisionModelFile(mapFile.GetNameStr(), mapFile.GetGeometryCRC())) {

                if (0 == mapFile.GetNumEntities()) {
                    return;
                }

                // load the .proc file bsp for data optimisation
                LoadProcBSP(mapFile.GetName());

                // convert brushes and patches to collision data
                for (i = 0; i < mapFile.GetNumEntities(); i++) {
                    mapEnt = mapFile.GetEntity(i);

                    if (numModels >= MAX_SUBMODELS) {
                        common.Error("idCollisionModelManagerLocal::BuildModels: more than %d collision models", MAX_SUBMODELS);
                        break;
                    }
                    models[numModels] = CollisionModelForMapEntity(mapEnt);
                    if (models[numModels] != null) {
                        numModels++;
                    }
                }

                // free the proc bsp which is only used for data optimization
                procNodes = null;

                // write the collision models to a file
                WriteCollisionModelsToFile(mapFile.GetName(), 0, numModels, mapFile.GetGeometryCRC());
            }

            timer.Stop();

            // print statistics on collision data
            cm_model_s model = new cm_model_s();
            AccumulateModelInfo(model);
            common.Printf("collision data:\n");
            common.Printf("%6d models\n", numModels);
            PrintModelInfo(model);
            common.Printf("%.0f msec to load collision data.\n", timer.Milliseconds());
        }

        private /*cmHandle_t*/ int FindModel(final idStr name) {
            int i;

            // check if this model is already loaded
            for (i = 0; i < numModels; i++) {
                if (0 == models[i].name.Icmp(name)) {
                    break;
                }
            }
            // if the model is already loaded
            if (i < numModels) {
                return i;
            }
            return -1;
        }

        private cm_model_s CollisionModelForMapEntity(final idMapEntity mapEnt) {    // brush/patch model from .map
            cm_model_s model;
            idBounds bounds = new idBounds();
            String[] name = new String[1];
            int i, brushCount;

            // if the entity has no primitives
            if (mapEnt.GetNumPrimitives() < 1) {
                return null;
            }

            // get a name for the collision model
            mapEnt.epairs.GetString("model", "", name);
            if (name[0].isEmpty()) {
                mapEnt.epairs.GetString("name", "", name);
                if (name[0].isEmpty()) {
                    if (0 == numModels) {
                        // first model is always the world
                        name[0] = "worldMap";
                    } else {
                        name[0] = "unnamed inline model";
                    }
                }
            }

            model = AllocModel();
            model.node = AllocNode(model, NODE_BLOCK_SIZE_SMALL);

            CInt maxVertices = new CInt();
            CInt maxEdges = new CInt();
            CM_EstimateVertsAndEdges(mapEnt, maxVertices, maxEdges);
            model.maxVertices = maxVertices.getVal();
            model.maxEdges = maxEdges.getVal();
            model.numVertices = 0;
            model.numEdges = 0;
            model.vertices = cm_vertex_s.generateArray(model.maxVertices);
            model.edges = cm_edge_s.generateArray(model.maxEdges);

            cm_vertexHash.ResizeIndex(model.maxVertices);
            cm_edgeHash.ResizeIndex(model.maxEdges);

            model.name = new idStr(name[0]);
            model.isConvex = false;

            // convert brushes
            for (i = 0; i < mapEnt.GetNumPrimitives(); i++) {
                idMapPrimitive mapPrim;

                mapPrim = mapEnt.GetPrimitive(i);
                if (mapPrim.GetType() == idMapPrimitive.TYPE_BRUSH) {
                    ConvertBrush(model, (idMapBrush) mapPrim, i);
                    continue;
                }
            }

            // create an axial bsp tree for the model if it has more than just a bunch brushes
            brushCount = CM_CountNodeBrushes(model.node);
            if (brushCount > 4) {
                model.node = CreateAxialBSPTree(model, model.node);
            } else {
                model.node.planeType = -1;
            }

            // get bounds for hash
            if (brushCount != 0) {
                CM_GetNodeBounds(bounds, model.node);
            } else {
                bounds.oGet(0).Set(-256, -256, -256);
                bounds.oGet(1).Set(256, 256, 256);
            }

            // different models do not share edges and vertices with each other, so clear the hash
            ClearHash(bounds);

            // create polygons from patches and brushes
            for (i = 0; i < mapEnt.GetNumPrimitives(); i++) {
                idMapPrimitive mapPrim;

                mapPrim = mapEnt.GetPrimitive(i);
                if (mapPrim.GetType() == idMapPrimitive.TYPE_PATCH) {
                    ConvertPatch(model, (idMapPatch) mapPrim, i);
                    continue;
                }
                if (mapPrim.GetType() == idMapPrimitive.TYPE_BRUSH) {
                    ConvertBrushSides(model, (idMapBrush) mapPrim, i);
                    continue;
                }
            }

            FinishModel(model);

            return model;
        }

        private cm_model_s LoadRenderModel(final idStr fileName) {                    // ASE/LWO models
            int i, j;
            idRenderModel renderModel;
            modelSurface_s surf;
            idFixedWinding w = new idFixedWinding();
            cm_node_s node;
            cm_model_s model;
            idPlane plane = new idPlane();
            idBounds bounds;
            boolean collisionSurface;
            idStr extension = new StrPool.idPoolStr();

            // only load ASE and LWO models
            new idStr(fileName).ExtractFileExtension(extension);
            if ((extension.Icmp("ase") != 0) && (extension.Icmp("lwo") != 0) && (extension.Icmp("ma") != 0)) {
                return null;
            }

            if (null == renderModelManager.CheckModel(fileName.toString())) {
                return null;
            }

            renderModel = renderModelManager.FindModel(fileName.toString());

            model = AllocModel();
            model.name = new idStr(fileName);
            node = AllocNode(model, NODE_BLOCK_SIZE_SMALL);
            node.planeType = -1;
            model.node = node;

            model.maxVertices = 0;
            model.numVertices = 0;
            model.maxEdges = 0;
            model.numEdges = 0;

            bounds = renderModel.Bounds(null);

            collisionSurface = false;
            for (i = 0; i < renderModel.NumSurfaces(); i++) {
                surf = renderModel.Surface(i);
                if ((surf.shader.GetSurfaceFlags() & SURF_COLLISION) != 0) {
                    collisionSurface = true;
                }
            }

            for (i = 0; i < renderModel.NumSurfaces(); i++) {
                surf = renderModel.Surface(i);
                // if this surface has no contents
                if (0 == (surf.shader.GetContentFlags() & CONTENTS_REMOVE_UTIL)) {
                    continue;
                }
                // if the model has a collision surface and this surface is not a collision surface
                if (collisionSurface && 0 == (surf.shader.GetSurfaceFlags() & SURF_COLLISION)) {
                    continue;
                }
                // get max verts and edges
                model.maxVertices += surf.geometry.numVerts;
                model.maxEdges += surf.geometry.numIndexes;
            }

            model.vertices = cm_vertex_s.generateArray(model.maxVertices);
            model.edges = cm_edge_s.generateArray(model.maxEdges);

            // setup hash to speed up finding shared vertices and edges
            SetupHash();

            cm_vertexHash.ResizeIndex(model.maxVertices);
            cm_edgeHash.ResizeIndex(model.maxEdges);

            ClearHash(bounds);

            for (i = 0; i < renderModel.NumSurfaces(); i++) {
                surf = renderModel.Surface(i);
                // if this surface has no contents
                if (0 == (surf.shader.GetContentFlags() & CONTENTS_REMOVE_UTIL)) {
                    continue;
                }
                // if the model has a collision surface and this surface is not a collision surface
                if (collisionSurface && 0 == (surf.shader.GetSurfaceFlags() & SURF_COLLISION)) {
                    continue;
                }

                for (j = 0; j < surf.geometry.numIndexes; j += 3) {
                    w.Clear();
                    w.oPluSet(surf.geometry.verts[surf.geometry.indexes[j + 2]].xyz);
                    w.oPluSet(surf.geometry.verts[surf.geometry.indexes[j + 1]].xyz);
                    w.oPluSet(surf.geometry.verts[surf.geometry.indexes[j]].xyz);
                    w.GetPlane(plane);
                    plane = plane.oNegative();
                    PolygonFromWinding(model, w, plane, surf.shader, 1);
                }
            }

            // create a BSP tree for the model
            model.node = CreateAxialBSPTree(model, model.node);

            model.isConvex = false;

            FinishModel(model);

            // shutdown the hash
            ShutdownHash();

            common.Printf("loaded collision model %s\n", model.name);

            return model;
        }

        private boolean TrmFromModel_r(idTraceModel trm, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            int i;

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;

                    if (p.checkcount == checkCount) {
                        continue;
                    }

                    p.checkcount = checkCount;

                    if (trm.numPolys >= MAX_TRACEMODEL_POLYS) {
                        return false;
                    }
                    // copy polygon properties
                    trm.polys[trm.numPolys].bounds.oSet(p.bounds);
                    trm.polys[trm.numPolys].normal.oSet(p.plane.Normal());
                    trm.polys[trm.numPolys].dist = p.plane.Dist();
                    trm.polys[trm.numPolys].numEdges = p.numEdges;
                    // copy edge index
                    for (i = 0; i < p.numEdges; i++) {
                        trm.polys[trm.numPolys].edges[i] = p.edges[i];
                    }
                    trm.numPolys++;
                }
                if (node.planeType == -1) {
                    break;
                }
                if (!TrmFromModel_r(trm, node.children[1])) {
                    return false;
                }
                node = node.children[0];
            }
            return true;
        }
//

        /*
         ==================
         idCollisionModelManagerLocal::TrmFromModel

         NOTE: polygon merging can merge colinear edges and as such might cause dangling edges.
         ==================
         */
        private boolean TrmFromModel(final cm_model_s model, idTraceModel trm) {
            int i, j;
            int[] numEdgeUsers = new int[MAX_TRACEMODEL_EDGES + 1];

            // if the model has too many vertices to fit in a trace model
            if (model.numVertices > MAX_TRACEMODEL_VERTS) {
                common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s has too many vertices.\n", model.name);
                PrintModelInfo(model);
                return false;
            }

            // plus one because the collision model accounts for the first unused edge
            if (model.numEdges > MAX_TRACEMODEL_EDGES + 1) {
                common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s has too many edges.\n", model.name);
                PrintModelInfo(model);
                return false;
            }

            trm.type = TRM_CUSTOM;
            trm.numVerts = 0;
            trm.numEdges = 1;
            trm.numPolys = 0;
            trm.bounds.Clear();

            // copy polygons
            checkCount++;
            if (!TrmFromModel_r(trm, model.node)) {
                common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s has too many polygons.\n", model.name);
                PrintModelInfo(model);
                return false;//HACKME::9
            }

            // copy vertices
            for (i = 0; i < model.numVertices; i++) {
                trm.verts[i].oSet(model.vertices[i].p);
                trm.bounds.AddPoint(trm.verts[i]);
            }
            trm.numVerts = model.numVertices;

            // copy edges
            for (i = 0; i < model.numEdges; i++) {
                trm.edges[i].v[0] = model.edges[i].vertexNum[0];
                trm.edges[i].v[1] = model.edges[i].vertexNum[1];
            }
            // minus one because the collision model accounts for the first unused edge
            trm.numEdges = model.numEdges - 1;

            // each edge should be used exactly twice
            for (i = 0; i < trm.numPolys; i++) {
                for (j = 0; j < trm.polys[i].numEdges; j++) {
                    numEdgeUsers[Math.abs(trm.polys[i].edges[j])]++;
                }
            }
            for (i = 1; i <= trm.numEdges; i++) {
                if (numEdgeUsers[i] != 2) {
                    common.Printf("idCollisionModelManagerLocal::TrmFromModel: model %s has dangling edges, the model has to be an enclosed hull.\n", model.name);
                    PrintModelInfo(model);
                    return false;//HACKME::9
                }
            }

            // assume convex
            trm.isConvex = true;
            // check if really convex
            for (i = 0; i < trm.numPolys; i++) {
                // to be convex no vertices should be in front of any polygon plane
                for (j = 0; j < trm.numVerts; j++) {
                    if (trm.polys[i].normal.oMultiply(trm.verts[j]) - trm.polys[i].dist > 0.01f) {
                        trm.isConvex = false;
                        break;
                    }
                }
                if (j < trm.numVerts) {
                    break;
                }
            }

            // offset to center of model
            trm.offset = new idVec3(trm.bounds.GetCenter());

            trm.GenerateEdgeNormals();

            return true;
        }

        /*
         ===============================================================================

         Writing of collision model file

         ===============================================================================
         */
        // CollisionMap_files.cpp
        // writing
        private void WriteNodes(idFile fp, cm_node_s node) {
            fp.WriteFloatString("\t( %d %f )\n", node.planeType, node.planeDist);
            if (node.planeType != -1) {
                WriteNodes(fp, node.children[0]);
                WriteNodes(fp, node.children[1]);
            }
        }

        private int CountPolygonMemory(cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            int memory;

            memory = 0;
            for (pref = node.polygons; pref != null; pref = pref.next) {
                p = pref.p;
                if (p.checkcount == checkCount) {
                    continue;
                }
                p.checkcount = checkCount;

                memory += cm_polygon_s.BYTES + (p.numEdges - 1) * Integer.BYTES;
            }
            if (node.planeType != -1) {
                memory += CountPolygonMemory(node.children[0]);
                memory += CountPolygonMemory(node.children[1]);
            }
            return memory;
        }

        private void WritePolygons(idFile fp, cm_node_s node) {
            cm_polygonRef_s pref;
            cm_polygon_s p;
            int i;

            for (pref = node.polygons; pref != null; pref = pref.next) {
                p = pref.p;
                if (p.checkcount == checkCount) {
                    continue;
                }
                p.checkcount = checkCount;
                fp.WriteFloatString("\t%d (", p.numEdges);
                for (i = 0; i < p.numEdges; i++) {
                    fp.WriteFloatString(" %d", p.edges[i]);
                }
                fp.WriteFloatString(" ) ( %f %f %f ) %f", p.plane.Normal().oGet(0), p.plane.Normal().oGet(1), p.plane.Normal().oGet(2), p.plane.Dist());
                fp.WriteFloatString(" ( %f %f %f )", p.bounds.oGet(0).oGet(0), p.bounds.oGet(0).oGet(1), p.bounds.oGet(0).oGet(2));
                fp.WriteFloatString(" ( %f %f %f )", p.bounds.oGet(1).oGet(0), p.bounds.oGet(1).oGet(1), p.bounds.oGet(1).oGet(2));
                fp.WriteFloatString(" \"%s\"\n", p.material.GetName());
            }
            if (node.planeType != -1) {
                WritePolygons(fp, node.children[0]);
                WritePolygons(fp, node.children[1]);
            }
        }

        private int CountBrushMemory(cm_node_s node) {
            cm_brushRef_s bref;
            cm_brush_s b;
            int memory;

            memory = 0;
            for (bref = node.brushes; bref != null; bref = bref.next) {
                b = bref.b;
                if (b.checkcount == checkCount) {
                    continue;
                }
                b.checkcount = checkCount;

                memory += cm_brush_s.BYTES + (b.numPlanes - 1) * idPlane.BYTES;
            }
            if (node.planeType != -1) {
                memory += CountBrushMemory(node.children[0]);
                memory += CountBrushMemory(node.children[1]);
            }
            return memory;
        }

        private void WriteBrushes(idFile fp, cm_node_s node) {
            cm_brushRef_s bref;
            cm_brush_s b;
            int i;

            for (bref = node.brushes; bref != null; bref = bref.next) {
                b = bref.b;
                if (b.checkcount == checkCount) {
                    continue;
                }
                b.checkcount = checkCount;
                fp.WriteFloatString("\t%d {\n", b.numPlanes);
                for (i = 0; i < b.numPlanes; i++) {
                    fp.WriteFloatString("\t\t( %f %f %f ) %f\n", b.planes[i].Normal().oGet(0), b.planes[i].Normal().oGet(1), b.planes[i].Normal().oGet(2), b.planes[i].Dist());
                }
                fp.WriteFloatString("\t} ( %f %f %f )", b.bounds.oGet(0).oGet(0), b.bounds.oGet(0).oGet(1), b.bounds.oGet(0).oGet(2));
                fp.WriteFloatString(" ( %f %f %f ) \"%s\"\n", b.bounds.oGet(1).oGet(0), b.bounds.oGet(1).oGet(1), b.bounds.oGet(1).oGet(2), StringFromContents(b.contents));
            }
            if (node.planeType != -1) {
                WriteBrushes(fp, node.children[0]);
                WriteBrushes(fp, node.children[1]);
            }
        }

        private void WriteCollisionModel(idFile fp, cm_model_s model) {
            int i, polygonMemory, brushMemory;

            fp.WriteFloatString("collisionModel \"%s\" {\n", model.name);
            // vertices
            fp.WriteFloatString("\tvertices { /* numVertices = */ %d\n", model.numVertices);
            for (i = 0; i < model.numVertices; i++) {
                fp.WriteFloatString("\t/* %d */ ( %f %f %f )\n", i, model.vertices[i].p.oGet(0), model.vertices[i].p.oGet(1), model.vertices[i].p.oGet(2));
            }
            fp.WriteFloatString("\t}\n");
            // edges
            fp.WriteFloatString("\tedges { /* numEdges = */ %d\n", model.numEdges);
            for (i = 0; i < model.numEdges; i++) {
                fp.WriteFloatString("\t/* %d */ ( %d %d ) %d %d\n", i, model.edges[i].vertexNum[0], model.edges[i].vertexNum[1], model.edges[i].internal, model.edges[i].numUsers);
            }
            fp.WriteFloatString("\t}\n");
            // nodes
            fp.WriteFloatString("\tnodes {\n");
            WriteNodes(fp, model.node);
            fp.WriteFloatString("\t}\n");
            // polygons
            checkCount++;
            polygonMemory = CountPolygonMemory(model.node);
            fp.WriteFloatString("\tpolygons /* polygonMemory = */ %d {\n", polygonMemory);
            checkCount++;
            WritePolygons(fp, model.node);
            fp.WriteFloatString("\t}\n");
            // brushes
            checkCount++;
            brushMemory = CountBrushMemory(model.node);
            fp.WriteFloatString("\tbrushes /* brushMemory = */ %d {\n", brushMemory);
            checkCount++;
            WriteBrushes(fp, model.node);
            fp.WriteFloatString("\t}\n");
            // closing brace
            fp.WriteFloatString("}\n");
        }

        private void WriteCollisionModelsToFile(final String filename, int firstModel, int lastModel, long mapFileCRC) {
            int i;
            idFile fp;
            idStr name;

            name = new idStr(filename);
            name.SetFileExtension(CM_FILE_EXT);

            common.Printf("writing %s\n", filename);
            // _D3XP was saving to fs_cdpath
            fp = fileSystem.OpenFileWrite(filename, "fs_devpath");
            if (null == fp) {
                common.Warning("idCollisionModelManagerLocal::WriteCollisionModelsToFile: Error opening file %s\n", filename);
                return;
            }

            // write file id and version
            fp.WriteFloatString("%s \"%s\"\n\n", CM_FILEID, CM_FILEVERSION);
            // write the map file crc
            fp.WriteFloatString("%u\n\n", mapFileCRC);

            // write the collision models
            for (i = firstModel; i < lastModel; i++) {
                WriteCollisionModel(fp, models[i]);
            }

            fileSystem.CloseFile(fp);
        }

        /*
         ===============================================================================

         Loading of collision model file

         ===============================================================================
         */
        // loading
        private cm_node_s ParseNodes(idLexer src, cm_model_s model, cm_node_s parent) {
            cm_node_s node;

            model.numNodes++;
            node = AllocNode(model, model.numNodes < NODE_BLOCK_SIZE_SMALL ? NODE_BLOCK_SIZE_SMALL : NODE_BLOCK_SIZE_LARGE);
            node.brushes = null;
            node.polygons = null;
            node.parent = parent;
            src.ExpectTokenString("(");
            node.planeType = src.ParseInt();
            node.planeDist = src.ParseFloat();
            src.ExpectTokenString(")");
            if (node.planeType != -1) {
                node.children[0] = ParseNodes(src, model, node);
                node.children[1] = ParseNodes(src, model, node);
            }
            return node;
        }

        private void ParseVertices(idLexer src, cm_model_s model) {
            int i;

            src.ExpectTokenString("{");
            model.numVertices = src.ParseInt();
            model.maxVertices = model.numVertices;
            model.vertices = new cm_vertex_s[model.maxVertices];// Mem_Alloc(model.maxVertices);
            for (i = 0; i < model.numVertices; i++) {
                model.vertices[i] = new cm_vertex_s();
                src.Parse1DMatrix(3, model.vertices[i].p);
                model.vertices[i].side = 0;
                model.vertices[i].sideSet = 0;
                model.vertices[i].checkcount = 0;
            }
            src.ExpectTokenString("}");
        }

        private void ParseEdges(idLexer src, cm_model_s model) {
            int i;

            src.ExpectTokenString("{");
            model.numEdges = src.ParseInt();
            model.maxEdges = model.numEdges;
            model.edges = new cm_edge_s[model.maxEdges];// Mem_Alloc(model.maxEdges);
            for (i = 0; i < model.numEdges; i++) {
                src.ExpectTokenString("(");
                model.edges[i] = new cm_edge_s();
                model.edges[i].vertexNum[0] = src.ParseInt();
                model.edges[i].vertexNum[1] = src.ParseInt();
                src.ExpectTokenString(")");
                model.edges[i].side = 0;
                model.edges[i].sideSet = 0;
                model.edges[i].internal = (short) src.ParseInt();
                model.edges[i].numUsers = (short) src.ParseInt();
                model.edges[i].normal = getVec3_origin();
                model.edges[i].checkcount = 0;
                model.numInternalEdges += model.edges[i].internal;
            }
            src.ExpectTokenString("}");
        }

        private void ParsePolygons(idLexer src, cm_model_s model) {
            cm_polygon_s p;
            int i, numEdges;
            idVec3 normal = new idVec3();
            idToken token = new idToken();

            if (src.CheckTokenType(TT_NUMBER, 0, token) != 0) {
                model.polygonBlock = new cm_polygonBlock_s();
                model.polygonBlock.bytesRemaining = token.GetIntValue();
                model.polygonBlock.next = new cm_polygonBlock_s();
            }

            src.ExpectTokenString("{");
            while (!src.CheckTokenString("}")) {
                // parse polygon
                numEdges = src.ParseInt();
                p = AllocPolygon(model, numEdges);
                p.numEdges = numEdges;
                src.ExpectTokenString("(");
                for (i = 0; i < p.numEdges; i++) {
                    p.edges[i] = src.ParseInt();
                }
                src.ExpectTokenString(")");
                src.Parse1DMatrix(3, normal);
                p.plane.SetNormal(normal);
                p.plane.SetDist(src.ParseFloat());
                src.Parse1DMatrix(3, p.bounds.oGet(0));
                src.Parse1DMatrix(3, p.bounds.oGet(1));
                src.ExpectTokenType(TT_STRING, 0, token);
                // get material
                p.material = declManager.FindMaterial(token);
                p.contents = p.material.GetContentFlags();
                p.checkcount = 0;
                // filter polygon into tree
                R_FilterPolygonIntoTree(model, model.node, null, p);
            }
        }

        private void ParseBrushes(idLexer src, cm_model_s model) {
            cm_brush_s b;
            int i, numPlanes;
            idVec3 normal = new idVec3();
            idToken token = new idToken();

            if (src.CheckTokenType(TT_NUMBER, 0, token) != 0) {
                model.brushBlock = new cm_brushBlock_s();
                model.brushBlock.bytesRemaining = token.GetIntValue();
                model.brushBlock.next = new cm_brushBlock_s();
            }

            src.ExpectTokenString("{");
            while (!src.CheckTokenString("}")) {
                // parse brush
                numPlanes = src.ParseInt();
                b = AllocBrush(model, numPlanes);
                b.numPlanes = numPlanes;
                src.ExpectTokenString("{");
                for (i = 0; i < b.numPlanes; i++) {
                    src.Parse1DMatrix(3, normal);
                    b.planes[i] = new idPlane();
                    b.planes[i].SetNormal(normal);
                    b.planes[i].SetDist(src.ParseFloat());
                }
                src.ExpectTokenString("}");
                src.Parse1DMatrix(3, b.bounds.oGet(0));
                src.Parse1DMatrix(3, b.bounds.oGet(1));
                src.ReadToken(token);
                if (token.type == TT_NUMBER) {
                    b.contents = token.GetIntValue();        // old .cm files use a single integer
                } else {
                    b.contents = ContentsFromString(token.toString());
                }
                b.checkcount = 0;
                b.primitiveNum = 0;
                // filter brush into tree
                R_FilterBrushIntoTree(model, model.node, null, b);
            }
        }

        private boolean ParseCollisionModel(idLexer src) {
            cm_model_s model;
            idToken token = new idToken();

            if (numModels >= MAX_SUBMODELS) {
                common.Error("LoadModel: no free slots");
                return false;
            }
            model = AllocModel();
            models[numModels] = model;
            numModels++;
            // parse the file
            src.ExpectTokenType(TT_STRING, 0, token);
            model.name = new idStr(token);
            src.ExpectTokenString("{");
            while (!src.CheckTokenString("}")) {

                src.ReadToken(token);

                if (token.equals("vertices")) {
                    ParseVertices(src, model);
                    continue;
                }

                if (token.equals("edges")) {
                    ParseEdges(src, model);
                    continue;
                }

                if (token.equals("nodes")) {
                    src.ExpectTokenString("{");
                    model.node = ParseNodes(src, model, null);
                    src.ExpectTokenString("}");
                    continue;
                }

                if (token.equals("polygons")) {
                    ParsePolygons(src, model);
                    continue;
                }

                if (token.equals("brushes")) {
                    ParseBrushes(src, model);
                    continue;
                }

                src.Error("ParseCollisionModel: bad token \"%s\"", token.toString());
            }
            // calculate edge normals
            checkCount++;
            CalculateEdgeNormals(model, model.node);
            // get model bounds from brush and polygon bounds
            CM_GetNodeBounds(model.bounds, model.node);
            // get model contents
            model.contents = CM_GetNodeContents(model.node);
            // total memory used by this model
            model.usedMemory = model.numVertices * cm_vertex_s.BYTES
                    + model.numEdges * cm_edge_s.BYTES
                    + model.polygonMemory
                    + model.brushMemory
                    + model.numNodes       //* cm_node_s.Bytes
                    + model.numPolygonRefs //* cm_polygonRef_s.Bytes
                    + model.numBrushRefs;  //* cm_brushRef_s.Bytes;

            return true;
        }
//

        private boolean LoadCollisionModelFile(final idStr name, int mapFileCRC) {
            idStr fileName;
            idToken token = new idToken();
            idLexer src;
            int crc;

            // load it
            fileName = new idStr(name);
            fileName.SetFileExtension(CM_FILE_EXT);
            src = new idLexer(fileName.toString());
            src.SetFlags(LEXFL_NOSTRINGCONCAT | LEXFL_NODOLLARPRECOMPILE);
            if (!src.IsLoaded()) {
                src = null;
                return false;
            }

            if (!src.ExpectTokenString(CM_FILEID)) {
                common.Warning("%s is not an CM file.", fileName);
                src = null;
                return false;
            }

            if (!src.ReadToken(token) || !token.equals(CM_FILEVERSION)) {
                common.Warning("%s has version %s instead of %s", fileName, token, CM_FILEVERSION);
                src = null;
                return false;
            }

            if (0 == src.ExpectTokenType(TT_NUMBER, TT_INTEGER, token)) {
                common.Warning("%s has no map file CRC", fileName);
                src = null;
                return false;
            }

            crc = (int) token.GetUnsignedLongValue();
            if (mapFileCRC != 0 && crc != mapFileCRC) {
                common.Printf("%s is out of date\n", fileName);
                src = null;
                return false;
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break;
                }

                if (token.equals("collisionModel")) {
                    if (!ParseCollisionModel(src)) {
                        src = null;
                        return false;
                    }
                    continue;
                }

                src.Error("idCollisionModelManagerLocal::LoadCollisionModelFile: bad token \"%s\"", token);
            }
            src = null;
            return true;
        }

        // CollisionMap_debug
        private int ContentsFromString(final String string) {
            int i, contents = 0;
            idLexer src = new idLexer(string, string.length(), "ContentsFromString");
            idToken token = new idToken();

            while (src.ReadToken(token)) {
                if (token.equals(",")) {
                    continue;
                }
                for (i = 1; cm_contentsNameByIndex[i] != null; i++) {
                    if (token.Icmp(cm_contentsNameByIndex[i]) == 0) {
                        contents |= cm_contentsFlagByIndex[i];
                        break;
                    }
                }
            }

            return contents;
        }

        private String StringFromContents(final int contents) {
            int i, length = 0;

            contentsString[0] = '\0';

            for (i = 1; cm_contentsFlagByIndex[i] != 0; i++) {
                if ((contents & cm_contentsFlagByIndex[i]) != 0) {
                    if (length != 0) {
                        length += idStr.snPrintf(length, contentsString, MAX_STRING_CHARS - length, ",");
                    }
                    length += idStr.snPrintf(length, contentsString, MAX_STRING_CHARS - length, cm_contentsNameByIndex[i]);
                }
            }

            return ctos(contentsString);
        }

        private void DrawEdge(cm_model_s model, int edgeNum, final idVec3 origin, final idMat3 axis) {
            boolean side;
            cm_edge_s edge;
            idVec3 start, end, mid;
            boolean isRotated;

            isRotated = axis.IsRotated();

            edge = model.edges[Math.abs(edgeNum)];
            side = edgeNum < 0;

            start = new idVec3(model.vertices[edge.vertexNum[side ? 1 : 0]].p);
            end = new idVec3(model.vertices[edge.vertexNum[side ? 0 : 1]].p);
            if (isRotated) {
                start.oMulSet(axis);
                end.oMulSet(axis);
            }
            start.oPluSet(origin);
            end.oPluSet(origin);

            if (edge.internal != 0) {
                if (cm_drawInternal.GetBool()) {
                    session.rw.DebugArrow(colorGreen, start, end, 1);
                }
            } else {
                if (edge.numUsers > 2) {
                    session.rw.DebugArrow(colorBlue, start, end, 1);
                } else {
                    session.rw.DebugArrow(cm_color, start, end, 1);
                }
            }

            if (cm_drawNormals.GetBool()) {
                mid = new idVec3(start.oPlus(end)).oMultiply(0.5f);
                if (isRotated) {
                    end = mid.oPlus(axis.oMultiply(edge.normal)).oMultiply(5);
                } else {
                    end = mid.oPlus(edge.normal.oMultiply(5));
                }
                session.rw.DebugArrow(colorCyan, mid, end, 1);
            }
        }

        private void DrawPolygon(cm_model_s model, cm_polygon_s p, final idVec3 origin, final idMat3 axis, final idVec3 viewOrigin) {
            int i, edgeNum;
            cm_edge_s edge;
            idVec3 center, end, dir;

            if (cm_backFaceCull.GetBool()) {
                edgeNum = p.edges[0];
                edge = model.edges[Math.abs(edgeNum)];
                dir = new idVec3(model.vertices[edge.vertexNum[0]].p.oMinus(viewOrigin));
                if (dir.oMultiply(p.plane.Normal()) > 0.0f) {
                    return;
                }
            }

            if (cm_drawNormals.GetBool()) {
                center = getVec3_origin();
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    edge = model.edges[Math.abs(edgeNum)];
                    center.oPluSet(model.vertices[edge.vertexNum[edgeNum < 0 ? 1 : 0]].p);
                }
                center.oMulSet(1.0f / p.numEdges);
                if (axis.IsRotated()) {
                    center.oSet(center.oMultiply(axis).oPluSet(origin));
                    end = new idVec3(center.oPlus( // center +
                            axis.oMultiply(p.plane.Normal()).oMultiply(5) // axis * p.planeNormal * 5
                    ));
                } else {
                    center.oPluSet(origin);
                    end = new idVec3(center.oPlus(p.plane.Normal().oMultiply(5)));
                }
                session.rw.DebugArrow(colorMagenta, center, end, 1);
            }

            if (cm_drawFilled.GetBool()) {
                idFixedWinding winding = new idFixedWinding();
                for (i = p.numEdges - 1; i >= 0; i--) {
                    edgeNum = p.edges[i];
                    edge = model.edges[Math.abs(edgeNum)];
                    winding.oPluSet(origin.oPlus(model.vertices[edge.vertexNum[INTSIGNBITSET(edgeNum)]].p.oMultiply(axis)));
                }
                session.rw.DebugPolygon(cm_color, winding);
            } else {
                for (i = 0; i < p.numEdges; i++) {
                    edgeNum = p.edges[i];
                    edge = model.edges[Math.abs(edgeNum)];
                    if (edge.checkcount == checkCount) {
                        continue;
                    }
                    edge.checkcount = checkCount;
                    DrawEdge(model, edgeNum, origin, axis);
                }
            }
        }

        private void DrawNodePolygons(cm_model_s model, cm_node_s node, final idVec3 origin, final idMat3 axis,
                                      final idVec3 viewOrigin, final float radius) {
            int i;
            cm_polygon_s p;
            cm_polygonRef_s pref;

            while (true) {
                for (pref = node.polygons; pref != null; pref = pref.next) {
                    p = pref.p;
                    if (radius != 0.0f) {
                        // polygon bounds should overlap with trace bounds
                        for (i = 0; i < 3; i++) {
                            if (p.bounds.oGet(0).oGet(i) > viewOrigin.oGet(i) + radius) {
                                break;
                            }
                            if (p.bounds.oGet(1).oGet(i) < viewOrigin.oGet(i) - radius) {
                                break;
                            }
                        }
                        if (i < 3) {
                            continue;
                        }
                    }
                    if (p.checkcount == checkCount) {
                        continue;
                    }
                    if (0 == (p.contents & cm_contentsFlagByIndex[cm_drawMask.GetInteger()])) {
                        continue;
                    }

                    DrawPolygon(model, p, origin, axis, viewOrigin);
                    p.checkcount = checkCount;
                }
                if (node.planeType == -1) {
                    break;
                }
                if (radius != 0.0f && viewOrigin.oGet(node.planeType) > node.planeDist + radius) {
                    node = node.children[0];
                } else if (radius != 0.0f && viewOrigin.oGet(node.planeType) < node.planeDist - radius) {
                    node = node.children[1];
                } else {
                    DrawNodePolygons(model, node.children[1], origin, axis, viewOrigin, radius);
                    node = node.children[0];
                }
            }
        }
    }

}
