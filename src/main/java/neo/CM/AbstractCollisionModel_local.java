package neo.CM;

import neo.Renderer.Material;
import neo.idlib.BV.Bounds;
import neo.idlib.Text.Str;
import neo.idlib.geometry.Winding;
import neo.idlib.math.Matrix.idMat3;
import neo.idlib.math.Plane;
import neo.idlib.math.Pluecker;
import neo.idlib.math.Rotation;
import neo.idlib.math.Vector;

import java.util.Arrays;
import java.util.stream.Stream;

import static neo.idlib.geometry.TraceModel.*;

/*
    Represents CollisionModel.h
 */
public abstract class AbstractCollisionModel_local {

    public static final float CHOP_EPSILON = 0.1f;
    public static final float CIRCLE_APPROXIMATION_LENGTH = 64.0f;
    public static final int CM_MAX_POLYGON_EDGES = 64;
    public static final int EDGE_HASH_SIZE = (1 << 14);
    public static final float INTEGRAL_EPSILON = 0.01f;
    public static final int MAX_NODE_POLYGONS = 128;
    public static final int MAX_SUBMODELS = 2048;
    public static final int MAX_WINDING_LIST = 128;        // quite a few are generated at times
    public static final float MIN_NODE_SIZE = 64.0f;
    public static final int NODE_BLOCK_SIZE_LARGE = 256;
    public static final int NODE_BLOCK_SIZE_SMALL = 8;
    public static final int REFERENCE_BLOCK_SIZE_LARGE = 256;
    public static final int REFERENCE_BLOCK_SIZE_SMALL = 8;
    public static final int TRACE_MODEL_HANDLE = MAX_SUBMODELS;
    public static final int VERTEX_HASH_BOXSIZE = (1 << 6);    // must be power of 2
    public static final int VERTEX_HASH_SIZE = (VERTEX_HASH_BOXSIZE * VERTEX_HASH_BOXSIZE);
    static final float VERTEX_EPSILON = 0.1f;

    public static class cm_windingList_s {

        Bounds.idBounds bounds = new Bounds.idBounds();                             // bounds of all windings in list
        int contents;                                            // winding surface contents
        Vector.idVec3 normal;                                              // normal for all windings
        int numWindings;                                              // number of windings
        Vector.idVec3 origin;                                              // origin for radius
        int primitiveNum;                                        // number of primitive the windings came from
        float radius;                                              // radius relative to origin for all windings
        Winding.idFixedWinding[] w = new Winding.idFixedWinding[MAX_WINDING_LIST];    // windings
    }

    /*
     ===============================================================================

     Collision model

     ===============================================================================
     */
    public static class cm_vertex_s {
        static final int SIZE = Vector.idVec3.SIZE + Integer.SIZE + Long.SIZE + Long.SIZE;
        static final int BYTES = SIZE / Byte.SIZE;
        int checkcount = 0;                  // for multi-check avoidance
        Vector.idVec3 p;                 // vertex point
        long side = 0L;                       // each bit tells at which side this vertex passes one of the trace model edges
        long sideSet = 0L;                    // each bit tells if sidedness for the trace model edge has been calculated yet

        public cm_vertex_s() {
            this.p = new Vector.idVec3();
        }

        public cm_vertex_s(cm_vertex_s val) {
            if (val == null) {
                this.p = new Vector.idVec3();
            } else {
                this.checkcount = val.checkcount;
                this.p = new Vector.idVec3(val.p);
                this.side = val.side;
                this.sideSet = val.sideSet;
            }
        }

        static cm_vertex_s[] generateArray(final int length) {
            return Stream.generate(cm_vertex_s::new).
                    limit(length).
                    toArray(cm_vertex_s[]::new);
        }
    }

    public static class cm_edge_s {
        static final int SIZE = Integer.SIZE + Short.SIZE + Short.SIZE + Long.SIZE + Long.SIZE + Integer.SIZE + Vector.idVec3.SIZE;
        static final int BYTES = SIZE / Byte.SIZE;

        int checkcount;                   // for multi-check avoidance
        boolean internal;                   // a trace model can never collide with internal edges
        Vector.idVec3 normal;       // edge normal
        short numUsers;                   // number of polygons using this edge
        long side;                        // each bit tells at which side of this edge one of the trace model vertices passes
        long sideSet;                      // each bit tells if sidedness for the trace model vertex has been calculated yet
        int[] vertexNum = new int[2];       // start and end point of edge

        public cm_edge_s() {
            this.normal = new Vector.idVec3();
        }

        public cm_edge_s(cm_edge_s val) {
            if (val == null) {
                this.normal = new Vector.idVec3();
            } else {
                this.checkcount = val.checkcount;
                this.internal = val.internal;
                this.normal = new Vector.idVec3(val.normal);
                this.numUsers = val.numUsers;
                this.side = val.side;
                this.sideSet = val.sideSet;
                this.vertexNum = val.vertexNum;
            }
        }

        static cm_edge_s[] generateArray(final int length) {
            return Stream.generate(cm_edge_s::new).
                    limit(length).
                    toArray(cm_edge_s[]::new);
        }
    }


    public static class cm_polygonBlock_s {
        int bytesRemaining;
        cm_polygonBlock_s next;
    }

    public static class cm_polygon_s {
        public static final int BYTES
                = Bounds.idBounds.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + Plane.idPlane.BYTES
                + Integer.BYTES
                + Integer.BYTES;
        Bounds.idBounds bounds;                  // polygon bounds
        int checkcount;              // for multi-check avoidance
        int contents;                // contents behind polygon
        int[] edges = new int[1];           // variable sized, indexes into cm_edge_t list
        Material.idMaterial material;                // material
        int numEdges;                // number of edges
        Plane.idPlane plane;                   // polygon plane

        public cm_polygon_s() {
            this.bounds = new Bounds.idBounds();
            this.material = new Material.idMaterial();
            this.plane = new Plane.idPlane();
        }

        public cm_polygon_s(cm_polygon_s val) {
            if (val != null) {
                this.bounds = new Bounds.idBounds(val.bounds);
                this.checkcount = val.checkcount;
                this.contents = val.contents;
                this.edges = val.edges;
                this.material = new Material.idMaterial(val.material);
                this.numEdges = val.numEdges;
                this.plane = new Plane.idPlane(val.plane);
            } else {
                this.bounds = new Bounds.idBounds();
                this.material = new Material.idMaterial();
                this.plane = new Plane.idPlane();
            }
        }

        public void oSet(final cm_polygon_s p) {
            this.bounds = new Bounds.idBounds(p.bounds);
            this.checkcount = p.checkcount;
            this.contents = p.contents;
            this.material = p.material;
            this.plane = new Plane.idPlane(p.plane);
            this.numEdges = p.numEdges;
            this.edges[0] = p.edges[0];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof cm_polygon_s)) return false;

            cm_polygon_s that = (cm_polygon_s) o;

            if (checkcount != that.checkcount) return false;
            if (contents != that.contents) return false;
            if (numEdges != that.numEdges) return false;
            if (bounds != null ? !bounds.equals(that.bounds) : that.bounds != null) return false;
            if (!Arrays.equals(edges, that.edges)) return false;
            if (material != null ? !material.equals(that.material) : that.material != null) return false;
            return plane != null ? plane.equals(that.plane) : that.plane == null;
        }

        @Override
        public int hashCode() {
            int result = bounds != null ? bounds.hashCode() : 0;
            result = 31 * result + checkcount;
            result = 31 * result + contents;
            result = 31 * result + Arrays.hashCode(edges);
            result = 31 * result + (material != null ? material.hashCode() : 0);
            result = 31 * result + numEdges;
            result = 31 * result + (plane != null ? plane.hashCode() : 0);
            return result;
        }
    }

    public static class cm_polygonRef_s {
        public static final int BYTES = cm_polygon_s.BYTES + Integer.BYTES;
        cm_polygonRef_s next;               // next polygon in chain
        cm_polygon_s p;                  // pointer to polygon

        public cm_polygonRef_s() {
            this.p = new cm_polygon_s();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof cm_polygonRef_s)) return false;

            cm_polygonRef_s that = (cm_polygonRef_s) o;

            if (next != null ? !next.equals(that.next) : that.next != null) return false;
            return p != null ? p.equals(that.p) : that.p == null;
        }

        @Override
        public int hashCode() {
            int result = next != null ? next.hashCode() : 0;
            result = 31 * result + (p != null ? p.hashCode() : 0);
            return result;
        }
    }

    public static class cm_polygonRefBlock_s {
        cm_polygonRefBlock_s next;          // next block with polygon references
        cm_polygonRef_s nextRef;       // next polygon reference in block
    }

    public static class cm_brushBlock_s {
        int bytesRemaining;
        cm_brushBlock_s next;
    }

    public static class cm_brush_s {
        public static final int BYTES
                = Integer.BYTES
                + Bounds.idBounds.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + Plane.idPlane.BYTES;
        Bounds.idBounds bounds;                  // brush bounds
        int checkcount;              // for multi-check avoidance
        int contents;                // contents of brush
        Material.idMaterial material;                // material
        int numPlanes;               // number of bounding planes
        Plane.idPlane[] planes = new Plane.idPlane[1];  // variable sized
        int primitiveNum;            // number of brush primitive

        public cm_brush_s() {
            this.bounds = new Bounds.idBounds();
        }
    }

    public static class cm_brushRef_s {
        public static final int BYTES = cm_brush_s.BYTES + Integer.BYTES;
        cm_brush_s b;                    // pointer to brush
        cm_brushRef_s next;                 // next brush in chain

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof cm_brushRef_s)) return false;

            cm_brushRef_s that = (cm_brushRef_s) o;

            if (b != null ? !b.equals(that.b) : that.b != null) return false;
            return next != null ? next.equals(that.next) : that.next == null;
        }

        @Override
        public int hashCode() {
            int result = b != null ? b.hashCode() : 0;
            result = 31 * result + (next != null ? next.hashCode() : 0);
            return result;
        }
    }

    public static class cm_brushRefBlock_s {
        cm_brushRefBlock_s next;            // next block with brush references
        cm_brushRef_s nextRef;         // next brush reference in block
    }

    public static class cm_node_s {
        public static final int BYTES
                = Integer.BYTES
                + Float.BYTES
                + cm_polygonRef_s.BYTES
                + cm_brushRef_s.BYTES
                + Integer.BYTES
                + Integer.BYTES;
        cm_brushRef_s brushes;            // brushes in node
        cm_node_s[] children = new cm_node_s[2];// node children
        cm_node_s parent;             // parent of this node
        float planeDist;          // node plane distance
        int planeType;          // node axial plane type
        cm_polygonRef_s polygons;           // polygons in node
    }

    public static class cm_nodeBlock_s {
        cm_nodeBlock_s next;                // next block with nodes
        cm_node_s nextNode;            // next node in block
    }

    public static class cm_model_s {
        Bounds.idBounds bounds;        // model bounds
        cm_brushBlock_s brushBlock;    // memory block with all brushes
        int brushMemory;
        cm_brushRefBlock_s brushRefBlocks;// list with blocks of brush references
        int contents;      // all contents of the model ored together
        cm_edge_s[] edges;         // array with all edges used by the model
        boolean isConvex;      // set if model is convex
        int maxEdges;      // size of edge array
        // model geometry
        int maxVertices;   // size of vertex array
        Str.idStr name;          // model name
        cm_node_s node;          // first node of spatial subdivision
        // blocks with allocated memory
        cm_nodeBlock_s nodeBlocks;    // list with blocks of nodes
        int numBrushRefs;
        int numBrushes;
        int numEdges;      // number of edges
        int numInternalEdges;
        int numMergedPolys;
        int numNodes;
        int numPolygonRefs;
        // statistics
        int numPolygons;
        int numRemovedPolys;
        int numSharpEdges;
        int numVertices;   // number of vertices
        cm_polygonBlock_s polygonBlock;  // memory block with all polygons
        int polygonMemory;
        cm_polygonRefBlock_s polygonRefBlocks;// list with blocks of polygon references
        int usedMemory;
        cm_vertex_s[] vertices;      // array with all vertices used by the model

        public cm_model_s() {
            bounds = new Bounds.idBounds();
        }

        public cm_model_s(cm_model_s val) {
            if (val != null) {
                this.bounds = val.bounds;
                this.brushBlock = val.brushBlock;
                this.brushMemory = val.brushMemory;
                this.brushRefBlocks = val.brushRefBlocks;
                this.contents = val.contents;
                this.edges = val.edges;
                isConvex = val.isConvex;
                this.maxEdges = val.maxEdges;
                this.maxVertices = val.maxVertices;   // size of vertex array
                this.name = val.name;          // model name
                this.node = val.node;          // first node of spatial subdivision
                this.nodeBlocks = val.nodeBlocks;    // list with blocks of nodes
                this.numBrushRefs = val.numBrushRefs;
                this.numBrushes = val.numBrushes;
                this.numEdges = val.numEdges;      // number of edges
                this.numInternalEdges = val.numInternalEdges;
                this.numMergedPolys = val.numMergedPolys;
                this.numNodes = val.numNodes;
                this.numPolygonRefs = val.numPolygonRefs;
                this.numPolygons = val.numPolygons;
                this.numRemovedPolys = val.numRemovedPolys;
                this.numSharpEdges = val.numSharpEdges;
                this.numVertices = val.numVertices;   // number of vertices
                this.polygonBlock = val.polygonBlock;  // memory block with all polygons
                this.polygonMemory = val.polygonMemory;
                this.polygonRefBlocks = val.polygonRefBlocks;// list with blocks of polygon references
                this.usedMemory = val.usedMemory;
                this.vertices = val.vertices;      // a
            }
        }

        public static cm_model_s[] generateArray(final int length) {
            return Stream.generate(cm_model_s::new).limit(length).toArray(cm_model_s[]::new);
        }
    }

    /*
     ===============================================================================

     Data used during collision detection calculations

     ===============================================================================
     */
    public static class cm_trmVertex_s {

        Vector.idVec3 endp;                    // end point of vertex after movement
        Vector.idVec3 p;                       // vertex position
        Pluecker.idPluecker pl;                      // pluecker coordinate for vertex movement
        int polygonSide;             // side of polygon this vertex is on (rotational collision)
        Bounds.idBounds rotationBounds;          // rotation bounds for this vertex
        Vector.idVec3 rotationOrigin;          // rotation origin for this vertex
        boolean used;                    // true if this vertex is used for collision detection

        public cm_trmVertex_s() {
            p = new Vector.idVec3();
            endp = new Vector.idVec3();
            pl = new Pluecker.idPluecker();
            rotationOrigin = new Vector.idVec3();
            rotationBounds = new Bounds.idBounds();
        }
    }

    public static class cm_trmEdge_s {

        short bitNum;                  // vertex bit number
        Vector.idVec3 cross;                   // (z,-y,x) of cross product between edge dir and movement dir
        Vector.idVec3 end;                         // end of edge
        Pluecker.idPluecker pl;                      // pluecker coordinate for edge
        Pluecker.idPluecker plzaxis;                 // pluecker coordinate for rotation about the z-axis
        Bounds.idBounds rotationBounds;          // rotation bounds for this edge
        Vector.idVec3 start;                       // start of edge
        boolean used;                       // true when vertex is used for collision detection
        int[] vertexNum = new int[2];       // indexes into cm_sraceWork_s->vertices

        public cm_trmEdge_s() {
            this.start = new Vector.idVec3();
            this.end = new Vector.idVec3();
            this.pl = new Pluecker.idPluecker();
            this.cross = new Vector.idVec3();
            this.rotationBounds = new Bounds.idBounds();
            this.plzaxis = new Pluecker.idPluecker();
        }
    }

    public static class cm_trmPolygon_s {

        int[] edges = new int[MAX_TRACEMODEL_POLYEDGES];// index into cm_sraceWork_s->edges
        int numEdges;                   // number of edges
        Plane.idPlane plane;                      // polygon plane
        Bounds.idBounds rotationBounds;            // rotation bounds for this polygon
        int used;

        public cm_trmPolygon_s() {
            this.plane = new Plane.idPlane();
            this.rotationBounds = new Bounds.idBounds();
        }
    }

    public static class cm_traceWork_s {
        float angle;              // angle for rotational collision
        Vector.idVec3 axis;               // rotation axis in model space
        boolean axisIntersectsTrm;  // true if the rotation axis intersects the trace model
        Bounds.idBounds bounds;             // bounds of full trace
        CollisionModel.contactInfo_t[] contacts;           // array with contacts
        int contents;           // ignore polygons that do not have any of these contents flags
        Vector.idVec3 dir;                // trace direction
        cm_trmEdge_s[] edges;              // trm edges
        Vector.idVec3 end;                // end of trace
        Vector.idVec3 extents;            // largest of abs(size[0]) and abs(size[1]) for BSP trace
        boolean getContacts;        // true if retrieving contacts
        Plane.idPlane heartPlane1;        // polygons should be near anough the trace heart planes
        Plane.idPlane heartPlane2;
        boolean isConvex;           // true if the trace model is convex
        idMat3 matrix;             // rotates axis of rotation to the z-axis
        int maxContacts;        // max size of contact array
        float maxDistFromHeartPlane1;
        float maxDistFromHeartPlane2;
        float maxTan;             // max tangent of half the positive angle used instead of fraction
        cm_model_s model;              // model colliding with
        Rotation.idRotation modelVertexRotation;// inverse rotation for model vertices
        int numContacts;        // number of contacts found
        int numEdges;
        int numPolys;
        int numVerts;
        Vector.idVec3 origin;             // origin of rotation in model space
        boolean pointTrace;         // true if only tracing a point
        Pluecker.idPluecker[] polygonEdgePlueckerCache;
        Vector.idVec3[] polygonRotationOriginCache;
        Pluecker.idPluecker[] polygonVertexPlueckerCache;
        cm_trmPolygon_s[] polys;              // trm polygons
        boolean positionTest;       // true if not tracing but doing a position test
        boolean quickExit;          // set to quickly stop the collision detection calculations
        float radius;             // rotation radius of trm start
        boolean rotation;           // true if calculating rotational collision
        Bounds.idBounds size;               // bounds of transformed trm relative to start
        Vector.idVec3 start;              // start of trace
        CollisionModel.trace_s trace;              // collision detection result
        cm_trmVertex_s[] vertices;           // trm vertices

        public cm_traceWork_s() {
            vertices = Stream.generate(cm_trmVertex_s::new).limit(MAX_TRACEMODEL_VERTS).toArray(cm_trmVertex_s[]::new);
            edges = Stream.generate(cm_trmEdge_s::new).limit(MAX_TRACEMODEL_EDGES + 1L).toArray(cm_trmEdge_s[]::new);
            polys = Stream.generate(cm_trmPolygon_s::new).limit(MAX_TRACEMODEL_POLYS).toArray(cm_trmPolygon_s[]::new);

            trace = new CollisionModel.trace_s();
            start = new Vector.idVec3();
            end = new Vector.idVec3();
            dir = new Vector.idVec3();
            bounds = new Bounds.idBounds();
            size = new Bounds.idBounds();
            extents = new Vector.idVec3();

            origin = new Vector.idVec3();
            axis = new Vector.idVec3();
            matrix = new idMat3();
            modelVertexRotation = new Rotation.idRotation();

            heartPlane1 = new Plane.idPlane();
            heartPlane2 = new Plane.idPlane();
            this.polygonEdgePlueckerCache = Stream.generate(Pluecker.idPluecker::new).limit(CM_MAX_POLYGON_EDGES).toArray(Pluecker.idPluecker[]::new);
            this.polygonVertexPlueckerCache = Stream.generate(Pluecker.idPluecker::new).limit(CM_MAX_POLYGON_EDGES).toArray(Pluecker.idPluecker[]::new);
            this.polygonRotationOriginCache = Stream.generate(Vector.idVec3::new).limit(CM_MAX_POLYGON_EDGES).toArray(Vector.idVec3[]::new);
        }
    }

    /*
     ===============================================================================

     Collision Map

     ===============================================================================
     */
    public static class cm_procNode_s {
        int[] children = new int[2];        // negative numbers are (-1 - areaNumber), 0 = solid
        Plane.idPlane plane;
    }

}
