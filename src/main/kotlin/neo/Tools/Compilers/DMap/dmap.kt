package neo.Tools.Compilers.DMap

import neo.CM.CollisionModel_local
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.idRenderLightLocal
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.AAS.AASBuild.RunAAS_f
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.Tools.Compilers.DMap.tritjunction.hashVert_s
import neo.framework.BuildDefines
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib.idLib
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.sys.win_shared

/**
 *
 */
object dmap {
    //
    const val MAX_GROUP_LIGHTS = 16
    const val MAX_PATCH_SIZE = 32

    //
    const val MAX_QPATH = 256 // max length of a game pathname

    //
    const val PLANENUM_LEAF = -1

    //
    var dmapGlobals: dmapGlobals_t? = null

    /*
     ============
     ProcessModel
     ============
     */
    fun ProcessModel(e: uEntity_t?, floodFill: Boolean): Boolean {
        val faces: bspface_s?

        // build a bsp tree using all of the sides
        // of all of the structural brushes
        faces = facebsp.MakeStructuralBspFaceList(e.primitives)
        e.tree = facebsp.FaceBSP(faces)

        // create portals at every leaf intersection
        // to allow flood filling
        portals.MakeTreePortals(e.tree)

        // classify the leafs as opaque or areaportal
        ubrush.FilterBrushesIntoTree(e)

        // see if the bsp is completely enclosed
        if (floodFill && !dmap.dmapGlobals.noFlood) {
            if (portals.FloodEntities(e.tree)) {
                // set the outside leafs to opaque
                portals.FillOutside(e)
            } else {
                idLib.common.Printf("**********************\n")
                idLib.common.Warning("******* leaked *******")
                idLib.common.Printf("**********************\n")
                leakfile.LeakFile(e.tree)
                // bail out here.  If someone really wants to
                // process a map that leaks, they should use
                // -noFlood
                return false
            }
        }

        // get minimum convex hulls for each visible side
        // this must be done before creating area portals,
        // because the visible hull is used as the portal
        usurface.ClipSidesByTree(e)

        // determine areas before clipping tris into the
        // tree, so tris will never cross area boundaries
        portals.FloodAreas(e)

        // we now have a BSP tree with solid and non-solid leafs marked with areas
        // all primitives will now be clipped into this, throwing away
        // fragments in the solid areas
        usurface.PutPrimitivesInAreas(e)

        // now build shadow volumes for the lights and split
        // the optimize lists by the light beam trees
        // so there won't be unneeded overdraw in the static
        // case
        usurface.Prelight(e)

        // optimizing is a superset of fixing tjunctions
        if (!dmap.dmapGlobals.noOptimize) {
            optimize.OptimizeEntity(e)
        } else if (!dmap.dmapGlobals.noTJunc) {
            tritjunction.FixEntityTjunctions(e)
        }

        // now fix t junctions across areas
        tritjunction.FixGlobalTjunctions(e)
        return true
    }

    /*
     ============
     ProcessModels
     ============
     */
    fun ProcessModels(): Boolean {
        val oldVerbose: Boolean
        var entity: uEntity_t?
        oldVerbose = dmap.dmapGlobals.verbose
        dmap.dmapGlobals.entityNum = 0
        while (dmap.dmapGlobals.entityNum < dmap.dmapGlobals.num_entities) {
            entity = dmap.dmapGlobals.uEntities[dmap.dmapGlobals.entityNum]
            if (TempDump.NOT(entity.primitives)) {
                dmap.dmapGlobals.entityNum++
                continue
            }
            idLib.common.Printf("############### entity %d ###############\n", dmap.dmapGlobals.entityNum)

            // if we leaked, stop without any more processing
            if (!dmap.ProcessModel(entity, dmap.dmapGlobals.entityNum == 0)) {
                return false
            }

            // we usually don't want to see output for submodels unless
            // something strange is going on
            if (!dmap.dmapGlobals.verboseentities) {
                dmap.dmapGlobals.verbose = false
            }
            dmap.dmapGlobals.entityNum++
        }
        dmap.dmapGlobals.verbose = oldVerbose
        return true
    }

    /*
     ============
     DmapHelp
     ============
     */
    fun DmapHelp() {
        idLib.common.Printf(
            """
                    Usage: dmap [options] mapfile
                    Options:
                    noCurves          = don't process curves
                    noCM              = don't create collision map
                    noAAS             = don't create AAS files
                    
                    """.trimIndent()
        )
    }

    /*
     ============
     ResetDmapGlobals
     ============
     */
    fun ResetDmapGlobals() {
        dmap.dmapGlobals.mapFileBase[0] = '\u0000'
        dmap.dmapGlobals.dmapFile = null
        dmap.dmapGlobals.mapPlanes.Clear()
        dmap.dmapGlobals.num_entities = 0
        dmap.dmapGlobals.uEntities = null
        dmap.dmapGlobals.entityNum = 0
        dmap.dmapGlobals.mapLights.Clear()
        dmap.dmapGlobals.verbose = false
        dmap.dmapGlobals.glview = false
        dmap.dmapGlobals.noOptimize = false
        dmap.dmapGlobals.verboseentities = false
        dmap.dmapGlobals.noCurves = false
        dmap.dmapGlobals.fullCarve = false
        dmap.dmapGlobals.noModelBrushes = false
        dmap.dmapGlobals.noTJunc = false
        dmap.dmapGlobals.nomerge = false
        dmap.dmapGlobals.noFlood = false
        dmap.dmapGlobals.noClipSides = false
        dmap.dmapGlobals.noLightCarve = false
        dmap.dmapGlobals.noShadow = false
        dmap.dmapGlobals.shadowOptLevel = shadowOptLevel_t.SO_NONE
        dmap.dmapGlobals.drawBounds.Clear()
        dmap.dmapGlobals.drawflag = false
        dmap.dmapGlobals.totalShadowTriangles = 0
        dmap.dmapGlobals.totalShadowVerts = 0
    }

    /*
     ============
     Dmap
     ============
     */
    fun Dmap(args: CmdArgs.idCmdArgs?) {
        var i: Int
        var start: Int
        var end: Int
        var path: String? //= new char[1024];
        var passedName: idStr? = idStr()
        var leaked = false
        var noCM = false
        var noAAS = false
        dmap.ResetDmapGlobals()
        if (args.Argc() < 2) {
            dmap.DmapHelp()
            return
        }
        idLib.common.Printf("---- dmap ----\n")
        dmap.dmapGlobals.fullCarve = true
        dmap.dmapGlobals.shadowOptLevel =
            shadowOptLevel_t.SO_MERGE_SURFACES // create shadows by merging all surfaces, but no super optimization
        //	dmapGlobals.shadowOptLevel = SO_CLIP_OCCLUDERS;		// remove occluders that are completely covered
//	dmapGlobals.shadowOptLevel = SO_SIL_OPTIMIZE;
//	dmapGlobals.shadowOptLevel = SO_CULL_OCCLUDED;
        dmap.dmapGlobals.noLightCarve = true
        i = 1
        while (i < args.Argc()) {
            var s: String?
            s = args.Argv(i)
            if (TempDump.isNotNullOrEmpty(s) && s.length > 0 && s.startsWith("-")) {
                s = s.substring(1)
                if (s.length == 0 || s.startsWith("\u0000")) {
                    i++
                    continue
                }
            }
            if (TempDump.NOT(idStr.Companion.Icmp(s, "glview").toDouble())) {
                dmap.dmapGlobals.glview = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "v").toDouble())) {
                idLib.common.Printf("verbose = true\n")
                dmap.dmapGlobals.verbose = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "draw").toDouble())) {
                idLib.common.Printf("drawflag = true\n")
                dmap.dmapGlobals.drawflag = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noFlood").toDouble())) {
                idLib.common.Printf("noFlood = true\n")
                dmap.dmapGlobals.noFlood = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noLightCarve").toDouble())) {
                idLib.common.Printf("noLightCarve = true\n")
                dmap.dmapGlobals.noLightCarve = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "lightCarve").toDouble())) {
                idLib.common.Printf("noLightCarve = false\n")
                dmap.dmapGlobals.noLightCarve = false
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noOpt").toDouble())) {
                idLib.common.Printf("noOptimize = true\n")
                dmap.dmapGlobals.noOptimize = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "verboseentities").toDouble())) {
                idLib.common.Printf("verboseentities = true\n")
                dmap.dmapGlobals.verboseentities = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noCurves").toDouble())) {
                idLib.common.Printf("noCurves = true\n")
                dmap.dmapGlobals.noCurves = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noModels").toDouble())) {
                idLib.common.Printf("noModels = true\n")
                dmap.dmapGlobals.noModelBrushes = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noClipSides").toDouble())) {
                idLib.common.Printf("noClipSides = true\n")
                dmap.dmapGlobals.noClipSides = true
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noCarve").toDouble())) {
                idLib.common.Printf("noCarve = true\n")
                dmap.dmapGlobals.fullCarve = false
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "shadowOpt").toDouble())) {
                dmap.dmapGlobals.shadowOptLevel = dmap.shadowOptLevel_t.values()[TempDump.atoi(args.Argv(i + 1))]
                idLib.common.Printf("shadowOpt = %d\n", dmap.dmapGlobals.shadowOptLevel)
                i += 1
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noTjunc").toDouble())) {
                // triangle optimization won't work properly without tjunction fixing
                idLib.common.Printf("noTJunc = true\n")
                dmap.dmapGlobals.noTJunc = true
                dmap.dmapGlobals.noOptimize = true
                idLib.common.Printf("forcing noOptimize = true\n")
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noCM").toDouble())) {
                noCM = true
                idLib.common.Printf("noCM = true\n")
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "noAAS").toDouble())) {
                noAAS = true
                idLib.common.Printf("noAAS = true\n")
            } else if (TempDump.NOT(idStr.Companion.Icmp(s, "editorOutput").toDouble())) {
                if (BuildDefines._WIN32) {
                    Common.com_outputMsg = true
                }
            } else {
                break
            }
            i++
        }
        if (i >= args.Argc()) {
            idLib.common.Error("usage: dmap [options] mapfile")
        }
        passedName.oSet(args.Argv(i)) // may have an extension
        passedName.BackSlashesToSlashes()
        if (passedName.Icmpn("maps/", 4) != 0) {
            passedName.oSet("maps/$passedName")
        }
        val stripped = passedName
        stripped.StripFileExtension()
        idStr.Companion.Copynz(dmap.dmapGlobals.mapFileBase, stripped.c_str(), dmap.dmapGlobals.mapFileBase.size)
        var region = false
        // if this isn't a regioned map, delete the last saved region map
        if (passedName.Right(4) != ".reg") {
            path = kotlin.String.format("%s.reg", *dmap.dmapGlobals.mapFileBase)
            FileSystem_h.fileSystem.RemoveFile(path)
        } else {
            region = true
        }
        passedName = stripped

        // delete any old line leak files
        path = kotlin.String.format("%s.lin", *dmap.dmapGlobals.mapFileBase)
        FileSystem_h.fileSystem.RemoveFile(path)

        //
        // start from scratch
        //
        start = win_shared.Sys_Milliseconds()
        if (!map.LoadDMapFile(passedName.toString())) {
            return
        }
        if (dmap.ProcessModels()) {
            output.WriteOutputFile()
        } else {
            leaked = true
        }
        map.FreeDMapFile()
        idLib.common.Printf("%d total shadow triangles\n", dmap.dmapGlobals.totalShadowTriangles)
        idLib.common.Printf("%d total shadow verts\n", dmap.dmapGlobals.totalShadowVerts)
        end = win_shared.Sys_Milliseconds()
        idLib.common.Printf("-----------------------\n")
        idLib.common.Printf("%5.0f seconds for dmap\n", (end - start) * 0.001f)
        if (!leaked) {
            if (!noCM) {

                // make sure the collision model manager is not used by the game
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_NOW, "disconnect")

                // create the collision map
                start = win_shared.Sys_Milliseconds()
                CollisionModel_local.collisionModelManager.LoadMap(dmap.dmapGlobals.dmapFile)
                CollisionModel_local.collisionModelManager.FreeMap()
                end = win_shared.Sys_Milliseconds()
                idLib.common.Printf("-------------------------------------\n")
                idLib.common.Printf("%5.0f seconds to create collision map\n", (end - start) * 0.001f)
            }
            if (!noAAS && !region) {
                // create AAS files
                RunAAS_f.Companion.getInstance().run(args)
            }
        }

        // free the common .map representation
//        delete dmapGlobals.dmapFile;
        dmap.dmapGlobals.dmapFile = null

        // clear the map plane list
        dmap.dmapGlobals.mapPlanes.Clear()
        if (BuildDefines._WIN32) {
            throw TODO_Exception()
            //            if (com_outputMsg && com_hwndMsg != 0) {
//                long msg = RegisterWindowMessage(DMAP_DONE);
//                PostMessage(com_hwndMsg, msg, 0, 0);
//            }
        }
    }

    // all primitives from the map are added to optimzeGroups, creating new ones as needed
    // each optimizeGroup is then split into the map areas, creating groups in each area
    // each optimizeGroup is then divided by each light, creating more groups
    // the final list of groups is then tjunction fixed against all groups, then optimized internally
    // multiple optimizeGroups will be merged together into .proc surfaces, but no further optimization
    // is done on them
    //=============================================================================
    internal enum class shadowOptLevel_t {
        SO_NONE,  // 0
        SO_MERGE_SURFACES,  // 1
        SO_CULL_OCCLUDED,  // 2
        SO_CLIP_OCCLUDERS,  // 3
        SO_CLIP_SILS,  // 4
        SO_SIL_OPTIMIZE // 5
    }

    internal class primitive_s {
        //
        // only one of these will be non-NULL
        var brush: bspbrush_s? = null
        var next: primitive_s? = null
        var tris: mapTri_s? = null
    }

    internal class uArea_t {
        var groups: optimizeGroup_s? = null // we might want to add other fields later
    }

    internal class uEntity_t {
        var areas: Array<uArea_t?>?
        var mapEntity // points into mapFile_t data
                : idMapEntity? = null

        //
        var numAreas = 0

        //
        val origin: idVec3? = idVec3()
        var primitives: primitive_s? = null
        var tree: tree_s? = null
    }

    // chains of mapTri_t are the general unit of processing
    internal class mapTri_s {
        var hashVert: Array<hashVert_s?>? = arrayOfNulls<hashVert_s?>(3)

        //
        var material: idMaterial? = null
        var mergeGroup // we want to avoid merging triangles
                : Any? = null
        var next: mapTri_s? = null
        var optVert: Array<optVertex_s?>? = arrayOfNulls<optVertex_s?>(3)

        // from different fixed groups, like guiSurfs and mirrors
        var planeNum // not set universally, just in some areas
                = 0

        //
        var v: Array<idDrawVert?>? = arrayOfNulls<idDrawVert?>(3)
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun oSet(next: mapTri_s?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class mesh_t {
        var verts: idDrawVert? = null
        var width = 0
        var height = 0
    }

    internal class parseMesh_s {
        var material: idMaterial? = null
        var mesh: mesh_t? = null
        var next: parseMesh_s? = null
    }

    internal class bspface_s {
        // any non-portals
        var checked // used by SelectSplitPlaneNum()
                = false
        var next: bspface_s? = null
        var planenum = 0
        var portal // all portals will be selected before
                = false
        var w: idWinding? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class textureVectors_t {
        var v: Array<idVec4?>? =
            idVec4.Companion.generateArray(2) // the offset value will always be in the 0.0 to 1.0 range
    }

    internal class side_s {
        //
        var material: idMaterial? = null
        var planenum = 0
        var texVec: textureVectors_t? = null
        var visibleHull // also clipped to the solid parts of the world
                : idWinding? = null

        //
        var winding // only clipped to the other sides of the brush
                : idWinding? = null

        constructor(`val`: side_s?) {
            material = `val`.material
            planenum = `val`.planenum
            texVec = `val`.texVec
            visibleHull = `val`.visibleHull
            winding = `val`.winding
        }

        constructor()
    }

    internal open class bspbrush_s {
        //
        var bounds: idBounds? = null
        var brushnum // editor numbering for messages
                = 0

        //
        var contentShader // one face's shader will determine the volume attributes
                : idMaterial? = null

        //
        var contents = 0

        //
        var entitynum // editor numbering for messages
                = 0
        var next: bspbrush_s? = null
        var numsides = 0
        var opaque = false
        var original // chopped up brushes will reference the originals
                : bspbrush_s? = null
        var outputNumber // set when the brush is written to the file list
                = 0
        var sides: Array<side_s?>? = arrayOfNulls<side_s?>(6) // variably sized
    }

    internal class uBrush_t : bspbrush_s {
        constructor()

        //copy constructor
        constructor(brush: uBrush_t?) {
            next = brush.next
            original = brush.original
            entitynum = brush.entitynum
            brushnum = brush.brushnum
            contentShader = brush.contentShader
            contents = brush.contents
            opaque = brush.opaque
            outputNumber = brush.outputNumber
            bounds = brush.bounds
            numsides = brush.numsides
            //System.arraycopy(brush.sides, 0, this.sides, 0, 6);
            for (i in 0..5) {
                sides.get(i) = side_s(brush.sides.get(i))
            }
        }

        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun oSet(CopyBrush: uBrush_t?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class drawSurfRef_s {
        var nextRef: drawSurfRef_s? = null
        var outputNumber = 0
    }

    internal class node_s {
        // both leafs and nodes
        // needed for FindSideForPortal
        //
        var area // determined by flood filling up to areaportals
                = 0
        var bounds // valid after portalization
                : idBounds? = null

        //
        var brushlist // fragments of all brushes in this leaf
                : uBrush_t? = null
        var children: Array<node_s?>? = arrayOfNulls<node_s?>(2)
        var nodeNumber // set after pruning
                = 0
        var occupant // for leak file testing
                : uEntity_t? = null
        var occupied // 1 or greater can reach entity
                = 0

        //
        // leafs only
        var opaque // view can never be inside
                = false
        var parent: node_s? = null
        var planenum // -1 = leaf node
                = 0

        //
        var portals // also on nodes during construction
                : uPortal_s? = null

        //
        // nodes only
        var side // the side that created the node
                : side_s? = null

        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    //=============================================================================
    internal class uPortal_s {
        var next: Array<uPortal_s?>? = arrayOfNulls<uPortal_s?>(2)
        var nodes: Array<node_s?>? = arrayOfNulls<node_s?>(2) // [0] = front side of plane
        var onnode // NULL = outside box
                : node_s? = null
        val plane: idPlane? = idPlane()
        var winding: idWinding? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    // a tree_t is created by FaceBSP()
    internal class tree_s {
        var bounds: idBounds? = null
        var headnode: node_s? = null
        var outside_node: node_s? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class mapLight_t {
        var def: idRenderLightLocal? = null
        var name: CharArray? = CharArray(dmap.MAX_QPATH) // for naming the shadow volume surface and interactions
        var shadowTris: srfTriangles_s? = null
    }

    internal class optimizeGroup_s {
        var areaNum = 0
        val axis: Array<idVec3?>? =
            idVec3.Companion.generateArray(2) // orthogonal to the plane, so optimization can be 2D

        //
        var bounds // set in CarveGroupsByLight
                : idBounds? = null
        var groupLights: Array<mapLight_t?>? =
            arrayOfNulls<mapLight_t?>(dmap.MAX_GROUP_LIGHTS) // lights effecting this list
        var material: idMaterial? = null
        var mergeGroup // if this differs (guiSurfs, mirrors, etc), the
                : Any? = null
        var nextGroup: optimizeGroup_s? = null
        var numGroupLights = 0
        var planeNum = 0
        var regeneratedTris // after each island optimization
                : mapTri_s? = null

        //
        // all of these must match to add a triangle to the triList
        var smoothed // curves will never merge with brushes
                = false

        //
        var surfaceEmited = false

        // groups will not be combined into model surfaces
        // after optimization
        var texVec: textureVectors_t? = null

        //
        var triList: mapTri_s? = null
        fun clear() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun oSet(nextGroup: optimizeGroup_s?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    class dmapGlobals_t {
        // mapFileBase will contain the qpath without any extension: "maps/test_box"
        //
        var dmapFile: idMapFile? = null

        //
        var drawBounds: idBounds? = null
        var drawflag = false

        //
        var entityNum = 0
        var fullCarve = false

        //
        var glview = false
        var mapFileBase: CharArray? = CharArray(1024)

        //
        val mapLights: idList<mapLight_t?>? = idList()

        //
        var mapPlanes: idPlaneSet? = null
        var noClipSides // don't cut sides by solid leafs, use the entire thing
                = false
        var noCurves = false
        var noFlood = false
        var noLightCarve // extra triangle subdivision by light frustums
                = false
        var noModelBrushes = false
        var noOptimize = false
        var noShadow // don't create optimized shadow volumes
                = false
        var noTJunc = false
        var nomerge = false

        //
        var num_entities = 0
        var shadowOptLevel: shadowOptLevel_t? = null

        //
        var totalShadowTriangles = 0
        var totalShadowVerts = 0
        var uEntities: Array<uEntity_t?>?

        //
        var verbose = false
        var verboseentities = false
    }

    /*
     ============
     Dmap_f
     ============
     */
    class Dmap_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            idLib.common.ClearWarnings("running dmap")

            // refresh the screen each time we print so it doesn't look
            // like it is hung
            idLib.common.SetRefreshOnPrint(true)
            dmap.Dmap(args)
            idLib.common.SetRefreshOnPrint(false)
            idLib.common.PrintWarnings()
        }

        companion object {
            private val instance: cmdFunction_t? = Dmap_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}