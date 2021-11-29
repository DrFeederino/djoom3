package neo.Tools.Compilers.AAS

import neo.Game.GameEdit
import neo.Renderer.Material
import neo.Renderer.RenderWorld
import neo.TempDump
import neo.Tools.Compilers.AAS.AASBuild_File.sizeEstimate_s
import neo.Tools.Compilers.AAS.AASBuild_ledge.idLedge
import neo.Tools.Compilers.AAS.AASBuild_local.aasProcNode_s
import neo.Tools.Compilers.AAS.AASCluster.idAASCluster
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasNode_s
import neo.Tools.Compilers.AAS.AASFile.idAASSettings
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.Tools.Compilers.AAS.AASReach.idAASReach
import neo.Tools.Compilers.AAS.Brush.idBrush
import neo.Tools.Compilers.AAS.Brush.idBrushList
import neo.Tools.Compilers.AAS.Brush.idBrushMap
import neo.Tools.Compilers.AAS.Brush.idBrushSide
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSP
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPNode
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPPortal
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.FileSystem_h
import neo.framework.FileSystem_h.idFileList
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib.idException
import neo.idlib.MapFile.idMapBrush
import neo.idlib.MapFile.idMapBrushSide
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.containers.idStrList
import neo.idlib.geometry.Surface_Patch.idSurface_Patch
import neo.idlib.geometry.Winding
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.sys.win_shared

/**
 *
 */
object AASBuild {
    const val BFL_PATCH = 0x1000

    /*
     ============
     ParseOptions
     ============
     */
    fun ParseOptions(args: CmdArgs.idCmdArgs?, settings: idAASSettings?): Int {
        var i: Int
        var str: idStr
        i = 1
        while (i < args.Argc()) {
            str = idStr(args.Argv(i))
            str.StripLeading('-')
            if (str.Icmp("usePatches") == 0) {
                settings.usePatches.isVal = true
                Common.common.Printf("usePatches = true\n")
            } else if (str.Icmp("writeBrushMap") == 0) {
                settings.writeBrushMap.isVal = true
                Common.common.Printf("writeBrushMap = true\n")
            } else if (str.Icmp("playerFlood") == 0) {
                settings.playerFlood.isVal = true
                Common.common.Printf("playerFlood = true\n")
            } else if (str.Icmp("noOptimize") == 0) {
                settings.noOptimize = true
                Common.common.Printf("noOptimize = true\n")
            }
            i++
        }
        return args.Argc() - 1
    }

    internal abstract class Allowance {
        abstract fun run(b1: idBrush?, b2: idBrush?): Boolean
    }

    //===============================================================
    //
    //	idAASBuild
    //
    //===============================================================
    internal class idAASBuild {
        private var aasSettings: idAASSettings? = null
        private var file: idAASFileLocal? = null
        private val ledgeList: idList<idLedge?>? = idList()
        private var ledgeMap: idBrushMap? = null
        private var numGravitationalSubdivisions = 0
        private var numLedgeSubdivisions = 0
        private var numMergedLeafNodes = 0

        //
        //
        private var numProcNodes = 0

        // ~idAASBuild();//TODO:deconstructors?
        private var procNodes: Array<aasProcNode_s?>? = null
        fun Build(fileName: idStr?, settings: idAASSettings?): Boolean {
            var i: Int
            var bit: Int
            var mask: Int
            val startTime: Int
            val mapFile: idMapFile
            var brushList: idBrushList? = idBrushList()
            val expandedBrushes = idList<idBrushList?>()
            var b: idBrush?
            val bsp = idBrushBSP()
            val name: idStr?
            val reach = idAASReach()
            val cluster = idAASCluster()
            val entityClassNames = idStrList()
            startTime = win_shared.Sys_Milliseconds()
            Shutdown()
            aasSettings = settings
            name = fileName
            name.SetFileExtension("map")
            mapFile = idMapFile()
            if (!mapFile.Parse(name.toString())) {
//		delete mapFile;
                Common.common.Error("Couldn't load map file: '%s'", name)
                return false
            }

            // check if this map has any entities that use this AAS file
            if (!CheckForEntities(mapFile, entityClassNames)) {
//		delete mapFile;
                Common.common.Printf("no entities in map that use %s\n", settings.fileExtension)
                return true
            }

            // load map file brushes
            brushList = AddBrushesForMapFile(mapFile, brushList)

            // if empty map
            if (brushList.Num() == 0) {
//		delete mapFile;
                Common.common.Error("%s is empty", name)
                return false
            }

            // merge as many brushes as possible before expansion
            brushList.Merge(MergeAllowed.INSTANCE) //TODO:like cmp_t

            // if there is a .proc file newer than the .map file
            if (LoadProcBSP(fileName.toString(), mapFile.GetFileTime())) {
                ClipBrushSidesWithProcBSP(brushList)
                DeleteProcBSP()
            }

            // make copies of the brush list
            expandedBrushes.Append(brushList)
            i = 1
            while (i < aasSettings.numBoundingBoxes) {
                expandedBrushes.Append(brushList.Copy())
                i++
            }

            // expand brushes for the axial bounding boxes
            mask = AASFile.AREACONTENTS_SOLID
            i = 0
            while (i < expandedBrushes.Num()) {
                b = expandedBrushes.oGet(i).Head()
                while (b != null) {
                    b.ExpandForAxialBox(aasSettings.boundingBoxes[i])
                    bit = 1 shl i + AASFile.AREACONTENTS_BBOX_BIT
                    mask = mask or bit
                    b.SetContents(b.GetContents() or bit)
                    b = b.Next()
                }
                i++
            }

            // move all brushes back into the original list
            i = 1
            while (i < aasSettings.numBoundingBoxes) {
                brushList.AddToTail(expandedBrushes.oGet(i))
                i++
            }
            if (aasSettings.writeBrushMap.isVal) {
                bsp.WriteBrushMap(fileName, idStr("_" + aasSettings.fileExtension), AASFile.AREACONTENTS_SOLID)
            }

            // build BSP tree from brushes
            bsp.Build(
                brushList,
                AASFile.AREACONTENTS_SOLID,
                ExpandedChopAllowed.INSTANCE,
                ExpandedMergeAllowed.INSTANCE
            )

            // only solid nodes with all bits set for all bounding boxes need to stay solid
            ChangeMultipleBoundingBoxContents_r(bsp.GetRootNode(), mask)

            // portalize the bsp tree
            bsp.Portalize()

            // remove subspaces not reachable by entities
            if (!bsp.RemoveOutside(mapFile, AASFile.AREACONTENTS_SOLID, entityClassNames)) {
                bsp.LeakFile(name)
                //                delete mapFile;
                Common.common.Printf("%s has no outside", name)
                return false
            }

            // gravitational subdivision
            GravitationalSubdivision(bsp)

            // merge portals where possible
            bsp.MergePortals(AASFile.AREACONTENTS_SOLID)

            // melt portal windings
            bsp.MeltPortals(AASFile.AREACONTENTS_SOLID)
            if (aasSettings.writeBrushMap.isVal) {
                WriteLedgeMap(fileName, idStr("_" + aasSettings.fileExtension + "_ledge"))
            }

            // ledge subdivisions
            LedgeSubdivision(bsp)

            // merge leaf nodes
            MergeLeafNodes(bsp)

            // merge portals where possible
            bsp.MergePortals(AASFile.AREACONTENTS_SOLID)

            // melt portal windings
            bsp.MeltPortals(AASFile.AREACONTENTS_SOLID)

            // store the file from the bsp tree
            StoreFile(bsp)
            file.settings = aasSettings

            // calculate reachability
            reach.Build(mapFile, file)

            // build clusters
            cluster.Build(file)

            // optimize the file
            if (!aasSettings.noOptimize) {
                file.Optimize()
            }

            // write the file
            name.SetFileExtension(aasSettings.fileExtension)
            file.Write(name, mapFile.GetGeometryCRC().toLong())

            // delete the map file
//	delete mapFile;
            Common.common.Printf("%6d seconds to create AAS\n", (win_shared.Sys_Milliseconds() - startTime) / 1000)
            return true
        }

        fun BuildReachability(fileName: idStr?, settings: idAASSettings?): Boolean {
            val startTime: Int
            val mapFile: idMapFile
            val name: idStr?
            val reach = idAASReach()
            val cluster = idAASCluster()
            startTime = win_shared.Sys_Milliseconds()
            aasSettings = settings
            name = fileName
            name.SetFileExtension("map")
            mapFile = idMapFile()
            if (!mapFile.Parse(name.toString())) {
//		delete mapFile;
                Common.common.Error("Couldn't load map file: '%s'", name)
                return false
            }
            file = idAASFileLocal()
            name.SetFileExtension(aasSettings.fileExtension)
            if (!file.Load(name, 0)) {
//		delete mapFile;
                Common.common.Error("Couldn't load AAS file: '%s'", name)
                return false
            }
            file.settings = aasSettings

            // calculate reachability
            reach.Build(mapFile, file)

            // build clusters
            cluster.Build(file)

            // write the file
            file.Write(name, mapFile.GetGeometryCRC().toLong())

//	// delete the map file
//	delete mapFile;
            Common.common.Printf(
                "%6d seconds to calculate reachability\n",
                (win_shared.Sys_Milliseconds() - startTime) / 1000
            )
            return true
        }

        fun Shutdown() {
            aasSettings = null
            if (file != null) {
//		delete file;
                file = null
            }
            DeleteProcBSP()
            numGravitationalSubdivisions = 0
            numMergedLeafNodes = 0
            numLedgeSubdivisions = 0
            ledgeList.Clear()
            if (ledgeMap != null) {
//		delete ledgeMap;
                ledgeMap = null
            }
        }

        // map loading
        private fun ParseProcNodes(src: idLexer?) {
            var i: Int
            src.ExpectTokenString("{")
            numProcNodes = src.ParseInt()
            if (numProcNodes < 0) {
                src.Error("idAASBuild::ParseProcNodes: bad numProcNodes")
            }
            procNodes =
                arrayOfNulls<aasProcNode_s?>(numProcNodes) // Mem_ClearedAlloc(idAASBuild.numProcNodes /* sizeof( aasProcNode_s )*/);
            i = 0
            while (i < numProcNodes) {
                var node: aasProcNode_s?
                node = procNodes.get(i)
                src.Parse1DMatrix(4, node.plane)
                node.children[0] = src.ParseInt()
                node.children[1] = src.ParseInt()
                i++
            }
            src.ExpectTokenString("}")
        }

        private fun LoadProcBSP(name: String?, minFileTime: Long): Boolean {
            val fileName: idStr
            val token = idToken()
            val src: idLexer

            // load it
            fileName = idStr(name)
            fileName.SetFileExtension(RenderWorld.PROC_FILE_EXT)
            src = idLexer(fileName.toString(), Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NODOLLARPRECOMPILE)
            if (!src.IsLoaded()) {
                Common.common.Warning("idAASBuild::LoadProcBSP: couldn't load %s", fileName)
                //		delete src;
                return false
            }

            // if the file is too old
            if (src.GetFileTime() < minFileTime) {
//		delete src;
                return false
            }
            if (!src.ReadToken(token) || token.Icmp(RenderWorld.PROC_FILE_ID) != 0) {
                Common.common.Warning(
                    "idAASBuild::LoadProcBSP: bad id '%s' instead of '%s'",
                    token,
                    RenderWorld.PROC_FILE_ID
                )
                //		delete src;
                return false
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token == "model") {
                    src.SkipBracedSection()
                    continue
                }
                if (token == "shadowModel") {
                    src.SkipBracedSection()
                    continue
                }
                if (token == "interAreaPortals") {
                    src.SkipBracedSection()
                    continue
                }
                if (token == "nodes") {
                    ParseProcNodes(src)
                    break
                }
                src.Error("idAASBuild::LoadProcBSP: bad token \"%s\"", token)
            }

//	delete src;
            return true
        }

        private fun DeleteProcBSP() {
            if (procNodes != null) {
//                Mem_Free(procNodes);
                procNodes = null
            }
            numProcNodes = 0
        }

        private fun ChoppedAwayByProcBSP(
            nodeNum: Int,
            w: idFixedWinding?,
            normal: idVec3?,
            origin: idVec3?,
            radius: Float
        ): Boolean {
            var nodeNum = nodeNum
            var res: Int
            val back = idFixedWinding()
            var node: aasProcNode_s?
            var dist: Float
            do {
                node = procNodes.get(nodeNum)
                dist = node.plane.Normal().times(origin) + node.plane.oGet(3)
                res = if (dist > radius) {
                    Plane.SIDE_FRONT
                } else if (dist < -radius) {
                    Plane.SIDE_BACK
                } else {
                    w.Split(back, node.plane, Plane.ON_EPSILON)
                }
                nodeNum = if (res == Plane.SIDE_FRONT) {
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
                        if (!ChoppedAwayByProcBSP(node.children[1], back, normal, origin, radius)) {
                            return false
                        }
                    }
                    node.children[0]
                }
            } while (nodeNum > 0)
            return nodeNum >= 0
        }

        private fun ClipBrushSidesWithProcBSP(brushList: idBrushList?) {
            var i: Int
            var clippedSides: Int
            var brush: idBrush?
            var neww: idFixedWinding
            val bounds = idBounds()
            var radius: Float
            val origin = idVec3()

            // if the .proc file has no BSP tree
            if (procNodes == null) {
                return
            }
            clippedSides = 0
            brush = brushList.Head()
            while (brush != null) {
                i = 0
                while (i < brush.GetNumSides()) {
                    if (TempDump.NOT(brush.GetSide(i).GetWinding())) {
                        i++
                        continue
                    }

                    // make a local copy of the winding
                    neww = brush.GetSide(i).GetWinding() as idFixedWinding
                    neww.GetBounds(bounds)
                    origin.oSet(bounds.oGet(1).oMinus(bounds.oGet(0)).oMultiply(0.5f))
                    radius = origin.Length() + Plane.ON_EPSILON
                    origin.oSet(bounds.oGet(0).oPlus(origin))
                    if (ChoppedAwayByProcBSP(0, neww, brush.GetSide(i).GetPlane().Normal(), origin, radius)) {
                        brush.GetSide(i).SetFlag(Brush.SFL_USED_SPLITTER)
                        clippedSides++
                    }
                    i++
                }
                brush = brush.Next()
            }
            Common.common.Printf("%6d brush sides clipped\n", clippedSides)
        }

        private fun ContentsForAAS(contents: Int): Int {
            var c: Int
            if (contents and (Material.CONTENTS_SOLID or Material.CONTENTS_AAS_SOLID or Material.CONTENTS_MONSTERCLIP) != 0) {
                return AASFile.AREACONTENTS_SOLID
            }
            c = 0
            if (contents and Material.CONTENTS_WATER != 0) {
                c = c or AASFile.AREACONTENTS_WATER
            }
            if (contents and Material.CONTENTS_AREAPORTAL != 0) {
                c = c or AASFile.AREACONTENTS_CLUSTERPORTAL
            }
            if (contents and Material.CONTENTS_AAS_OBSTACLE != 0) {
                c = c or AASFile.AREACONTENTS_OBSTACLE
            }
            return c
        }

        private fun AddBrushesForMapBrush(
            mapBrush: idMapBrush?,
            origin: idVec3?,
            axis: idMat3?,
            entityNum: Int,
            primitiveNum: Int,
            brushList: idBrushList?
        ): idBrushList? {
            var contents: Int
            var i: Int
            var mapSide: idMapBrushSide?
            var mat: idMaterial?
            val sideList = idList<idBrushSide?>()
            var brush: idBrush?
            var plane: idPlane?
            contents = 0
            i = 0
            while (i < mapBrush.GetNumSides()) {
                mapSide = mapBrush.GetSide(i)
                mat = DeclManager.declManager.FindMaterial(mapSide.GetMaterial())
                contents = contents or mat.GetContentFlags()
                plane = mapSide.GetPlane()
                plane.FixDegeneracies(Plane.DEGENERATE_DIST_EPSILON)
                sideList.Append(idBrushSide(plane, -1))
                i++
            }
            contents = ContentsForAAS(contents)
            if (0 == contents) {
                i = 0
                while (i < sideList.Num()) {

//			delete sideList[i];
                    sideList.oSet(i, null)
                    i++
                }
                return brushList
            }
            brush = idBrush()
            brush.SetContents(contents)
            if (!brush.FromSides(sideList)) {
                Common.common.Warning("brush primitive %d on entity %d is degenerate", primitiveNum, entityNum)
                //		delete brush;
                brush = null
                return brushList
            }
            brush.SetEntityNum(entityNum)
            brush.SetPrimitiveNum(primitiveNum)
            brush.Transform(origin, axis)
            brushList.AddToTail(brush)
            return brushList
        }

        private fun AddBrushesForMapPatch(
            mapPatch: idMapPatch?,
            origin: idVec3?,
            axis: idMat3?,
            entityNum: Int,
            primitiveNum: Int,
            brushList: idBrushList?
        ): idBrushList? {
            var i: Int
            var j: Int
            val contents: Int
            var validBrushes: Int
            var dot: Float
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            val w = idFixedWinding()
            val plane = idPlane()
            val d1 = idVec3()
            val d2 = idVec3()
            var brush: idBrush
            val mesh: idSurface_Patch
            val mat: idMaterial?
            mat = DeclManager.declManager.FindMaterial(mapPatch.GetMaterial())
            contents = ContentsForAAS(mat.GetContentFlags())
            if (0 == contents) {
                return brushList
            }
            mesh = idSurface_Patch(mapPatch)

            // if the patch has an explicit number of subdivisions use it to avoid cracks
            if (mapPatch.GetExplicitlySubdivided()) {
                mesh.SubdivideExplicit(mapPatch.GetHorzSubdivisions(), mapPatch.GetVertSubdivisions(), false, true)
            } else {
                mesh.Subdivide(
                    MapFile.DEFAULT_CURVE_MAX_ERROR_CD,
                    MapFile.DEFAULT_CURVE_MAX_ERROR_CD,
                    MapFile.DEFAULT_CURVE_MAX_LENGTH_CD,
                    false
                )
            }
            validBrushes = 0
            i = 0
            while (i < mesh.GetWidth() - 1) {
                j = 0
                while (j < mesh.GetHeight() - 1) {
                    v1 = j * mesh.GetWidth() + i
                    v2 = v1 + 1
                    v3 = v1 + mesh.GetWidth() + 1
                    v4 = v1 + mesh.GetWidth()
                    d1.oSet(mesh.oGet(v2).xyz.oMinus(mesh.oGet(v1).xyz))
                    d2.oSet(mesh.oGet(v3).xyz.oMinus(mesh.oGet(v1).xyz))
                    plane.SetNormal(d1.Cross(d2))
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh.oGet(v1).xyz)
                        dot = plane.Distance(mesh.oGet(v4).xyz)
                        // if we can turn it into a quad
                        if (Math.abs(dot) < 0.1f) {
                            w.Clear()
                            w.oPluSet(mesh.oGet(v1).xyz)
                            w.oPluSet(mesh.oGet(v2).xyz)
                            w.oPluSet(mesh.oGet(v3).xyz)
                            w.oPluSet(mesh.oGet(v4).xyz)
                            brush = idBrush()
                            brush.SetContents(contents)
                            if (brush.FromWinding(w, plane)) {
                                brush.SetEntityNum(entityNum)
                                brush.SetPrimitiveNum(primitiveNum)
                                brush.SetFlag(AASBuild.BFL_PATCH)
                                brush.Transform(origin, axis)
                                brushList.AddToTail(brush)
                                validBrushes++
                            } else {
//						delete brush;
//                                brush = null;
                            }
                            j++
                            continue
                        } else {
                            // create one of the triangles
                            w.Clear()
                            w.oPluSet(mesh.oGet(v1).xyz)
                            w.oPluSet(mesh.oGet(v2).xyz)
                            w.oPluSet(mesh.oGet(v3).xyz)
                            brush = idBrush()
                            brush.SetContents(contents)
                            if (brush.FromWinding(w, plane)) {
                                brush.SetEntityNum(entityNum)
                                brush.SetPrimitiveNum(primitiveNum)
                                brush.SetFlag(AASBuild.BFL_PATCH)
                                brush.Transform(origin, axis)
                                brushList.AddToTail(brush)
                                validBrushes++
                            } else {
//						delete brush;
//                                brush = null;
                            }
                        }
                    }
                    // create the other triangle
                    d1.oSet(mesh.oGet(v3).xyz.oMinus(mesh.oGet(v1).xyz))
                    d2.oSet(mesh.oGet(v4).xyz.oMinus(mesh.oGet(v1).xyz))
                    plane.SetNormal(d1.Cross(d2))
                    if (plane.Normalize() != 0.0f) {
                        plane.FitThroughPoint(mesh.oGet(v1).xyz)
                        w.Clear()
                        w.oPluSet(mesh.oGet(v1).xyz)
                        w.oPluSet(mesh.oGet(v3).xyz)
                        w.oPluSet(mesh.oGet(v4).xyz)
                        brush = idBrush()
                        brush.SetContents(contents)
                        if (brush.FromWinding(w, plane)) {
                            brush.SetEntityNum(entityNum)
                            brush.SetPrimitiveNum(primitiveNum)
                            brush.SetFlag(AASBuild.BFL_PATCH)
                            brush.Transform(origin, axis)
                            brushList.AddToTail(brush)
                            validBrushes++
                        } else {
//					delete brush;
//                            brush = null;
                        }
                    }
                    j++
                }
                i++
            }
            if (0 == validBrushes) {
                Common.common.Warning(
                    "patch primitive %d on entity %d is completely degenerate",
                    primitiveNum,
                    entityNum
                )
            }
            return brushList
        }

        private fun AddBrushesForMapEntity(
            mapEnt: idMapEntity?,
            entityNum: Int,
            brushList: idBrushList?
        ): idBrushList? {
            var brushList = brushList
            var i: Int
            val origin = idVec3()
            var axis: idMat3? = idMat3()
            if (mapEnt.GetNumPrimitives() < 1) {
                return brushList
            }
            mapEnt.epairs.GetVector("origin", "0 0 0", origin)
            if (!mapEnt.epairs.GetMatrix("rotation", "1 0 0 0 1 0 0 0 1", axis)) {
                val angle = mapEnt.epairs.GetFloat("angle")
                if (angle != 0.0f) {
                    axis = idAngles(0.0f, angle, 0.0f).ToMat3()
                } else {
                    axis.Identity()
                }
            }
            i = 0
            while (i < mapEnt.GetNumPrimitives()) {
                var mapPrim: idMapPrimitive?
                mapPrim = mapEnt.GetPrimitive(i)
                if (mapPrim.GetType() == idMapPrimitive.Companion.TYPE_BRUSH) {
                    brushList = AddBrushesForMapBrush(mapPrim as idMapBrush?, origin, axis, entityNum, i, brushList)
                    i++
                    continue
                }
                if (mapPrim.GetType() == idMapPrimitive.Companion.TYPE_PATCH) {
                    if (aasSettings.usePatches.isVal) {
                        brushList = AddBrushesForMapPatch(mapPrim as idMapPatch?, origin, axis, entityNum, i, brushList)
                    }
                    //                    continue;
                }
                i++
            }
            return brushList
        }

        private fun AddBrushesForMapFile(mapFile: idMapFile?, brushList: idBrushList?): idBrushList? {
            var brushList = brushList
            var i: Int
            Common.common.Printf("[Brush Load]\n")
            brushList = AddBrushesForMapEntity(mapFile.GetEntity(0), 0, brushList)
            i = 1
            while (i < mapFile.GetNumEntities()) {
                val classname = mapFile.GetEntity(i).epairs.GetString("classname")
                if (idStr.Companion.Icmp(classname, "func_aas_obstacle") == 0) {
                    brushList = AddBrushesForMapEntity(mapFile.GetEntity(i), i, brushList)
                }
                i++
            }
            Common.common.Printf("%6d brushes\n", brushList.Num())
            return brushList
        }

        private fun CheckForEntities(mapFile: idMapFile?, entityClassNames: idStrList?): Boolean {
            var i: Int
            val classname = idStr()
            Common.com_editors = Common.com_editors or Common.EDITOR_AAS
            i = 0
            while (i < mapFile.GetNumEntities()) {
                if (!mapFile.GetEntity(i).epairs.GetString("classname", "", classname)) {
                    i++
                    continue
                }
                if (aasSettings.ValidEntity(classname.toString())) {
                    entityClassNames.addUnique(classname)
                }
                i++
            }
            Common.com_editors = Common.com_editors and Common.EDITOR_AAS.inv()
            return entityClassNames.size() != 0
        }

        private fun ChangeMultipleBoundingBoxContents_r(node: idBrushBSPNode?, mask: Int) {
            var node = node
            while (node != null) {
                if (0 == node.GetContents() and mask) {
                    node.SetContents(node.GetContents() and AASFile.AREACONTENTS_SOLID.inv())
                }
                ChangeMultipleBoundingBoxContents_r(node.GetChild(0), mask)
                node = node.GetChild(1)
            }
        }

        // gravitational subdivision
        private fun SetPortalFlags_r(node: idBrushBSPNode?) {
            var s: Int
            var p: idBrushBSPPortal?
            val normal = idVec3()
            if (TempDump.NOT(node)) {
                return
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                p = node.GetPortals()
                while (p != null) {
                    s = if (p.GetNode(1) == node) 1 else 0

                    // if solid at the other side of the portal
                    if (p.GetNode( /*!s*/1 xor s)
                            .GetContents() and AASFile.AREACONTENTS_SOLID != 0
                    ) { //TODO:check that the answer is always 1 or 0.
                        if (s != 0) {
                            normal.oSet(p.GetPlane().Normal().oNegative())
                        } else {
                            normal.oSet(p.GetPlane().Normal())
                        }
                        if (normal.times(aasSettings.invGravityDir) > aasSettings.minFloorCos.getVal()) {
                            p.SetFlag(AASFile.FACE_FLOOR)
                        } else {
                            p.SetFlag(AASFile.FACE_SOLID)
                        }
                    }
                    p = p.Next(s)
                }
                return
            }
            SetPortalFlags_r(node.GetChild(0))
            SetPortalFlags_r(node.GetChild(1))
        }

        private fun PortalIsGap(portal: idBrushBSPPortal?, side: Int): Boolean {
            val normal = idVec3()

            // if solid at the other side of the portal
            if (portal.GetNode( /*!side*/1 xor side).GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return false
            }
            if (side != 0) {
                normal.oSet(portal.GetPlane().Normal().oNegative())
            } else {
                normal.oSet(portal.GetPlane().Normal())
            }
            return normal.times(aasSettings.invGravityDir) > aasSettings.minFloorCos.getVal()
        }

        private fun GravSubdivLeafNode(node: idBrushBSPNode?) {
            var s1: Int
            var s2: Int
            var i: Int
            var j: Int
            var k: Int
            var side1: Int
            var numSplits: Int
            var numSplitters: Int
            var p1: idBrushBSPPortal?
            var p2: idBrushBSPPortal?
            var w1: idWinding?
            var w2: idWinding?
            val normal = idVec3()
            val plane = idPlane()
            val planeList = idPlaneSet()
            var d: Float
            var min: Float
            var max: Float
            val splitterOrder: IntArray
            val bestNumSplits: IntArray
            var floor: Int
            var gap: Int
            var numFloorChecked: Int

            // if this leaf node is already classified it cannot have a combination of floor and gap portals
            if (node.GetFlags() and (AASFile.AREA_FLOOR or AASFile.AREA_GAP) != 0) {
                return
            }
            gap = 0
            floor = gap

            // check if the area has a floor
            p1 = node.GetPortals()
            while (p1 != null) {
                s1 = if (p1.GetNode(1) == node) 1 else 0
                if (p1.GetFlags() and AASFile.FACE_FLOOR != 0) {
                    floor++
                }
                p1 = p1.Next(s1)
            }

            // find seperating planes between gap and floor portals
            p1 = node.GetPortals()
            while (p1 != null) {
                s1 = if (p1.GetNode(1) == node) 1 else 0

                // if the portal is a gap seen from this side
                if (PortalIsGap(p1, s1)) {
                    gap++
                    // if the area doesn't have a floor
                    if (0 == floor) {
                        break
                    }
                } else {
                    p1 = p1.Next(s1)
                    continue
                }
                numFloorChecked = 0
                w1 = p1.GetWinding()

                // test all edges of the gap
                i = 0
                while (i < w1.GetNumPoints()) {


                    // create a plane through the edge of the gap parallel to the direction of gravity
                    normal.oSet(w1.oGet((i + 1) % w1.GetNumPoints()).ToVec3().oMinus(w1.oGet(i).ToVec3()))
                    normal.oSet(normal.Cross(aasSettings.invGravityDir))
                    if (normal.Normalize() < 0.2f) {
                        i++
                        continue
                    }
                    plane.SetNormal(normal)
                    plane.FitThroughPoint(w1.oGet(i).ToVec3())

                    // get the side of the plane the gap is on
                    side1 = w1.PlaneSide(plane, GRAVSUBDIV_EPSILON)
                    if (side1 == Plane.SIDE_ON) {
                        break
                    }

                    // test if the plane through the edge of the gap seperates the gap from a floor portal
                    p2 = node.GetPortals()
                    while (p2 != null) {
                        s2 = if (p2.GetNode(1) == node) 1 else 0
                        if (0 == p2.GetFlags() and AASFile.FACE_FLOOR) {
                            p2 = p2.Next(s2)
                            continue
                        }
                        if (p2.GetFlags() and FACE_CHECKED != 0) {
                            p2 = p2.Next(s2)
                            continue
                        }
                        w2 = p2.GetWinding()
                        min = 2.0f * GRAVSUBDIV_EPSILON
                        max = GRAVSUBDIV_EPSILON
                        if (side1 == Plane.SIDE_FRONT) {
                            j = 0
                            while (j < w2.GetNumPoints()) {
                                d = plane.Distance(w2.oGet(j).ToVec3())
                                if (d >= GRAVSUBDIV_EPSILON) {
                                    break // point at the same side of the plane as the gap
                                }
                                d = Math.abs(d)
                                if (d < min) {
                                    min = d
                                }
                                if (d > max) {
                                    max = d
                                }
                                j++
                            }
                        } else {
                            j = 0
                            while (j < w2.GetNumPoints()) {
                                d = plane.Distance(w2.oGet(j).ToVec3())
                                if (d <= -GRAVSUBDIV_EPSILON) {
                                    break // point at the same side of the plane as the gap
                                }
                                d = Math.abs(d)
                                if (d < min) {
                                    min = d
                                }
                                if (d > max) {
                                    max = d
                                }
                                j++
                            }
                        }

                        // a point of the floor portal was found to be at the same side of the plane as the gap
                        if (j < w2.GetNumPoints()) {
                            p2 = p2.Next(s2)
                            continue
                        }

                        // if the floor portal touches the plane
                        if (min < GRAVSUBDIV_EPSILON && max > GRAVSUBDIV_EPSILON) {
                            planeList.FindPlane(plane, 0.00001f, 0.1f)
                        }
                        p2.SetFlag(FACE_CHECKED)
                        numFloorChecked++
                        p2 = p2.Next(s2)
                    }
                    if (numFloorChecked == floor) {
                        break
                    }
                    i++
                }
                p2 = node.GetPortals()
                while (p2 != null) {
                    s2 = if (p2.GetNode(1) == node) 1 else 0
                    p2.RemoveFlag(FACE_CHECKED)
                    p2 = p2.Next(s2)
                }
                p1 = p1.Next(s1)
            }

            // if the leaf node does not have both floor and gap portals
            if (!(gap != 0 && floor != 0)) {
//                if (0 == (gap & floor)) {//TODO:check i this works better.
                if (floor != 0) {
                    node.SetFlag(AASFile.AREA_FLOOR)
                } else if (gap != 0) {
                    node.SetFlag(AASFile.AREA_GAP)
                }
                return
            }

            // if no valid seperators found
            if (planeList.Num() == 0) {
                // NOTE: this should never happend, if it does the leaf node has degenerate portals
                return
            }

//	splitterOrder = (int *) _alloca( planeList.Num() * sizeof( int ) );
//	bestNumSplits = (int *) _alloca( planeList.Num() * sizeof( int ) );
            splitterOrder = IntArray(planeList.Num())
            bestNumSplits = IntArray(planeList.Num())
            numSplitters = 0

            // test all possible seperators and sort them from best to worst
            i = 0
            while (i < planeList.Num()) {
                numSplits = 0
                p1 = node.GetPortals()
                while (p1 != null) {
                    s1 = if (p1.GetNode(1) == node) 1 else 0
                    if (p1.GetWinding().PlaneSide(planeList.oGet(i), 0.1f) == Plane.SIDE_CROSS) {
                        numSplits++
                    }
                    p1 = p1.Next(s1)
                }
                j = 0
                while (j < numSplitters) {
                    if (numSplits < bestNumSplits[j]) {
                        k = numSplitters
                        while (k > j) {
                            bestNumSplits[k] = bestNumSplits[k - 1]
                            splitterOrder[k] = splitterOrder[k - 1]
                            k--
                        }
                        bestNumSplits[j] = numSplits
                        splitterOrder[j] = i
                        numSplitters++
                        break
                    }
                    j++
                }
                if (j >= numSplitters) {
                    bestNumSplits[j] = numSplits
                    splitterOrder[j] = i
                    numSplitters++
                }
                i += 2
            }

            // try all seperators in order from best to worst
            i = 0
            while (i < numSplitters) {
                if (node.Split(planeList.oGet(splitterOrder[i]), -1)) {
                    // we found a seperator that works
                    break
                }
                i++
            }
            if (i >= numSplitters) {
                return
            }
            Brush.DisplayRealTimeString("\r%6d", ++numGravitationalSubdivisions)

            // test children for further splits
            GravSubdivLeafNode(node.GetChild(0))
            GravSubdivLeafNode(node.GetChild(1))
        }

        private fun GravSubdiv_r(node: idBrushBSPNode?) {
            if (TempDump.NOT(node)) {
                return
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                GravSubdivLeafNode(node)
                return
            }
            GravSubdiv_r(node.GetChild(0))
            GravSubdiv_r(node.GetChild(1))
        }

        private fun GravitationalSubdivision(bsp: idBrushBSP?) {
            numGravitationalSubdivisions = 0
            Common.common.Printf("[Gravitational Subdivision]\n")
            SetPortalFlags_r(bsp.GetRootNode())
            GravSubdiv_r(bsp.GetRootNode())
            Common.common.Printf("\r%6d subdivisions\n", numGravitationalSubdivisions)
        }

        // ledge subdivision
        private fun LedgeSubdivFlood_r(node: idBrushBSPNode?, ledge: idLedge?) {
            var s1: Int
            var i: Int
            var p1: idBrushBSPPortal?
            var w: idWinding?
            val nodeList = idList<idBrushBSPNode?>()
            if (node.GetFlags() and BrushBSP.NODE_VISITED != 0) {
                return
            }

            // if this is not already a ledge area
            if (0 == node.GetFlags() and AASFile.AREA_LEDGE) {
                p1 = node.GetPortals()
                while (p1 != null) {
                    s1 = if (p1.GetNode(1) == node) 1 else 0
                    if (0 == p1.GetFlags() and AASFile.FACE_FLOOR) {
                        p1 = p1.Next(s1)
                        continue
                    }

                    // split the area if some part of the floor portal is inside the expanded ledge
                    w = ledge.ChopWinding(p1.GetWinding())
                    if (TempDump.NOT(w)) {
                        p1 = p1.Next(s1)
                        continue
                    }
                    //			delete w;
//                    w = null;
                    i = 0
                    while (i < ledge.numSplitPlanes) {
                        if (node.PlaneSide(ledge.planes[i], 0.1f) != Plane.SIDE_CROSS) {
                            i++
                            continue
                        }
                        if (!node.Split(ledge.planes[i], -1)) {
                            i++
                            continue
                        }
                        numLedgeSubdivisions++
                        Brush.DisplayRealTimeString("\r%6d", numLedgeSubdivisions)
                        node.GetChild(0).SetFlag(BrushBSP.NODE_VISITED)
                        LedgeSubdivFlood_r(node.GetChild(1), ledge)
                        return
                        i++
                    }
                    node.SetFlag(AASFile.AREA_LEDGE)
                    break
                    p1 = p1.Next(s1)
                }
            }
            node.SetFlag(BrushBSP.NODE_VISITED)

            // get all nodes we might need to flood into
            p1 = node.GetPortals()
            while (p1 != null) {
                s1 = if (p1.GetNode(1) == node) 1 else 0
                if (p1.GetNode( /*!s1*/1 xor s1).GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                    p1 = p1.Next(s1)
                    continue
                }

                // flood through this portal if the portal is partly inside the expanded ledge
                w = ledge.ChopWinding(p1.GetWinding())
                if (TempDump.NOT(w)) {
                    p1 = p1.Next(s1)
                    continue
                }
                //		delete w;
//                w = null;
                // add to list, cannot flood directly cause portals might be split on the way
                nodeList.Append(p1.GetNode( /*!s1*/s1 xor 1))
                p1 = p1.Next(s1)
            }

            // flood into other nodes
            i = 0
            while (i < nodeList.Num()) {
                LedgeSubdivLeafNodes_r(nodeList.oGet(i), ledge)
                i++
            }
        }

        /*
         ============
         idAASBuild::LedgeSubdivLeafNodes_r

         The node the ledge was originally part of might be split by other ledges.
         Here we recurse down the tree from the original node to find all the new leaf nodes the ledge might be part of.
         ============
         */
        private fun LedgeSubdivLeafNodes_r(node: idBrushBSPNode?, ledge: idLedge?) {
            if (TempDump.NOT(node)) { //TODO:use NOT function
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                LedgeSubdivFlood_r(node, ledge)
                return
            }
            LedgeSubdivLeafNodes_r(node.GetChild(0), ledge)
            LedgeSubdivLeafNodes_r(node.GetChild(1), ledge)
        }

        private fun LedgeSubdiv(root: idBrushBSPNode?) {
            var i: Int
            var j: Int
            var brush: idBrush
            val sideList = idList<idBrushSide?>()

            // create ledge bevels and expand ledges
            i = 0
            while (i < ledgeList.Num()) {
                ledgeList.oGet(i).CreateBevels(aasSettings.gravityDir)
                ledgeList.oGet(i).Expand(aasSettings.boundingBoxes[0], aasSettings.maxStepHeight.getVal())

                // if we should write out a ledge map
                if (ledgeMap != null) {
                    sideList.SetNum(0)
                    j = 0
                    while (j < ledgeList.oGet(i).numPlanes) {
                        sideList.Append(idBrushSide(ledgeList.oGet(i).planes[j], -1))
                        j++
                    }
                    brush = idBrush()
                    brush.FromSides(sideList)
                    ledgeMap.WriteBrush(brush)

//			delete brush;
//                    brush = null;
                }

                // flood tree from the ledge node and subdivide areas with the ledge
                LedgeSubdivLeafNodes_r(ledgeList.oGet(i).node, ledgeList.oGet(i))

                // remove the node visited flags
                ledgeList.oGet(i).node.RemoveFlagRecurseFlood(BrushBSP.NODE_VISITED)
                i++
            }
        }

        private fun IsLedgeSide_r(
            node: idBrushBSPNode?,
            w: idFixedWinding?,
            plane: idPlane?,
            normal: idVec3?,
            origin: idVec3?,
            radius: Float
        ): Boolean {
            var node = node
            var res: Int
            var i: Int
            val back = idFixedWinding()
            var dist: Float
            if (TempDump.NOT(node)) {
                return false
            }
            while (node.GetChild(0) != null && node.GetChild(1) != null) {
                dist = node.GetPlane().Distance(origin)
                res = if (dist > radius) {
                    Plane.SIDE_FRONT
                } else if (dist < -radius) {
                    Plane.SIDE_BACK
                } else {
                    w.Split(back, node.GetPlane(), AASBuild_ledge.LEDGE_EPSILON)
                }
                node = if (res == Plane.SIDE_FRONT) {
                    node.GetChild(0)
                } else if (res == Plane.SIDE_BACK) {
                    node.GetChild(1)
                } else if (res == Plane.SIDE_ON) {
                    // continue with the side the winding faces
                    if (node.GetPlane().Normal().times(normal) > 0.0f) {
                        node.GetChild(0)
                    } else {
                        node.GetChild(1)
                    }
                } else {
                    if (IsLedgeSide_r(node.GetChild(1), back, plane, normal, origin, radius)) {
                        return true
                    }
                    node.GetChild(0)
                }
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return false
            }
            i = 0
            while (i < w.GetNumPoints()) {
                if (plane.Distance(w.oGet(i).ToVec3()) > 0.0f) {
                    return true
                }
                i++
            }
            return false
        }

        private fun AddLedge(v1: idVec3?, v2: idVec3?, node: idBrushBSPNode?) {
            var i: Int
            var j: Int
            var merged: Int

            // first try to merge the ledge with existing ledges
            merged = -1
            i = 0
            while (i < ledgeList.Num()) {
                j = 0
                while (j < 2) {
                    if (Math.abs(ledgeList.oGet(i).planes[j].Distance(v1)) > AASBuild_ledge.LEDGE_EPSILON) {
                        break
                    }
                    if (Math.abs(ledgeList.oGet(i).planes[j].Distance(v2)) > AASBuild_ledge.LEDGE_EPSILON) {
                        break
                    }
                    j++
                }
                if (j < 2) {
                    i++
                    continue
                }
                if (!ledgeList.oGet(i).PointBetweenBounds(v1)
                    && !ledgeList.oGet(i).PointBetweenBounds(v2)
                ) {
                    i++
                    continue
                }
                merged = if (merged == -1) {
                    ledgeList.oGet(i).AddPoint(v1)
                    ledgeList.oGet(i).AddPoint(v2)
                    i
                } else {
                    ledgeList.oGet(merged).AddPoint(ledgeList.oGet(i).start)
                    ledgeList.oGet(merged).AddPoint(ledgeList.oGet(i).end)
                    ledgeList.RemoveIndex(i)
                    break
                }
                i++
            }

            // if the ledge could not be merged
            if (merged == -1) {
                ledgeList.Append(idLedge(v1, v2, aasSettings.gravityDir, node))
            }
        }

        private fun FindLeafNodeLedges(root: idBrushBSPNode?, node: idBrushBSPNode?) {
            var s1: Int
            var i: Int
            var p1: idBrushBSPPortal?
            var w: idWinding?
            val v1 = idVec3()
            val v2 = idVec3()
            val normal = idVec3()
            val origin = idVec3()
            val winding = idFixedWinding()
            val bounds = idBounds()
            var plane: idPlane?
            var radius: Float
            p1 = node.GetPortals()
            while (p1 != null) {
                s1 = if (p1.GetNode(1) == node) 1 else 0
                if (0 == p1.GetFlags() and AASFile.FACE_FLOOR) {
                    p1 = p1.Next(s1)
                    continue
                }
                if (s1 != 0) {
                    plane = p1.GetPlane()
                    w = p1.GetWinding().Reverse()
                } else {
                    plane = p1.GetPlane().oNegative()
                    w = p1.GetWinding()
                }
                i = 0
                while (i < w.GetNumPoints()) {
                    v1.oSet(w.oGet(i).ToVec3())
                    v2.oSet(w.oGet((i + 1) % w.GetNumPoints()).ToVec3())
                    normal.oSet(v2.oMinus(v1).Cross(aasSettings.gravityDir))
                    if (normal.Normalize() < 0.5f) {
                        i++
                        continue
                    }
                    winding.Clear()
                    winding.oPluSet(v1.oPlus(normal.times(AASBuild_ledge.LEDGE_EPSILON * 0.5f)))
                    winding.oPluSet(v2.oPlus(normal.times(AASBuild_ledge.LEDGE_EPSILON * 0.5f)))
                    winding.oPluSet(
                        winding.oGet(1).ToVec3()
                            .oPlus(aasSettings.gravityDir.times(aasSettings.maxStepHeight.getVal() + 1.0f))
                    )
                    winding.oPluSet(
                        winding.oGet(0).ToVec3()
                            .oPlus(aasSettings.gravityDir.times(aasSettings.maxStepHeight.getVal() + 1.0f))
                    )
                    winding.GetBounds(bounds)
                    origin.oSet(bounds.oGet(1).oMinus(bounds.oGet(0)).oMultiply(0.5f))
                    radius = origin.Length() + AASBuild_ledge.LEDGE_EPSILON
                    origin.oSet(bounds.oGet(0).oPlus(origin))
                    plane.FitThroughPoint(v1.oPlus(aasSettings.gravityDir.times(aasSettings.maxStepHeight.getVal())))
                    if (!IsLedgeSide_r(root, winding, plane, normal, origin, radius)) {
                        i++
                        continue
                    }
                    AddLedge(v1, v2, node)
                    i++
                }
                p1 = p1.Next(s1)
            }
        }

        private fun FindLedges_r(root: idBrushBSPNode?, node: idBrushBSPNode?) {
            if (TempDump.NOT(node)) {
                return
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                if (node.GetFlags() and BrushBSP.NODE_VISITED != 0) {
                    return
                }
                FindLeafNodeLedges(root, node)
                node.SetFlag(BrushBSP.NODE_VISITED)
                return
            }
            FindLedges_r(root, node.GetChild(0))
            FindLedges_r(root, node.GetChild(1))
        }

        /*
         ============
         idAASBuild::LedgeSubdivision

         NOTE: this assumes the bounding box is higher than the maximum step height
         only ledges with vertical sides are considered
         ============
         */
        private fun LedgeSubdivision(bsp: idBrushBSP?) {
            numLedgeSubdivisions = 0
            ledgeList.Clear()
            Common.common.Printf("[Ledge Subdivision]\n")
            bsp.GetRootNode().RemoveFlagRecurse(BrushBSP.NODE_VISITED)
            FindLedges_r(bsp.GetRootNode(), bsp.GetRootNode())
            bsp.GetRootNode().RemoveFlagRecurse(BrushBSP.NODE_VISITED)
            Common.common.Printf("\r%6d ledges\n", ledgeList.Num())
            LedgeSubdiv(bsp.GetRootNode())
            Common.common.Printf("\r%6d subdivisions\n", numLedgeSubdivisions)
        }

        private fun WriteLedgeMap(fileName: idStr?, ext: idStr?) {
            ledgeMap = idBrushMap(fileName, ext)
            ledgeMap.SetTexture("textures/base_trim/bluetex4q_ed")
        }

        // merging
        private fun AllGapsLeadToOtherNode(nodeWithGaps: idBrushBSPNode?, otherNode: idBrushBSPNode?): Boolean {
            var s: Int
            var p: idBrushBSPPortal?
            p = nodeWithGaps.GetPortals()
            while (p != null) {
                s = if (p.GetNode(1) == nodeWithGaps) 1 else 0
                if (!PortalIsGap(p, s)) {
                    p = p.Next(s)
                    continue
                }
                if (p.GetNode( /*!s*/1 xor s) != otherNode) {
                    return false
                }
                p = p.Next(s)
            }
            return true
        }

        private fun MergeWithAdjacentLeafNodes(bsp: idBrushBSP?, node: idBrushBSPNode?): Boolean {
            var s: Int
            var numMerges = 0
            var otherNodeFlags: Int
            var p: idBrushBSPPortal?
            do {
                p = node.GetPortals()
                while (p != null) {
                    s = if (p.GetNode(1) == node) 1 else 0

                    // both leaf nodes must have the same contents
                    if (node.GetContents() != p.GetNode( /*!s*/1 xor s).GetContents()) {
                        p = p.Next(s)
                        continue
                    }

                    // cannot merge leaf nodes if one is near a ledge and the other is not
                    if (node.GetFlags() and AASFile.AREA_LEDGE != p.GetNode( /*!s*/1 xor s)
                            .GetFlags() and AASFile.AREA_LEDGE
                    ) {
                        p = p.Next(s)
                        continue
                    }

                    // cannot merge leaf nodes if one has a floor portal and the other a gap portal
                    if (node.GetFlags() and AASFile.AREA_FLOOR != 0) {
                        if (p.GetNode( /*!s*/1 xor s).GetFlags() and AASFile.AREA_GAP != 0) {
                            if (!AllGapsLeadToOtherNode(p.GetNode( /*!s*/1 xor s), node)) {
                                p = p.Next(s)
                                continue
                            }
                        }
                    } else if (node.GetFlags() and AASFile.AREA_GAP != 0) {
                        if (p.GetNode( /*!s*/1 xor s).GetFlags() and AASFile.AREA_FLOOR != 0) {
                            if (!AllGapsLeadToOtherNode(node, p.GetNode( /*!s*/1 xor s))) {
                                p = p.Next(s)
                                continue
                            }
                        }
                    }
                    otherNodeFlags = p.GetNode( /*!s*/1 xor s).GetFlags()

                    // try to merge the leaf nodes
                    if (bsp.TryMergeLeafNodes(p, s)) {
                        node.SetFlag(otherNodeFlags)
                        if (node.GetFlags() and AASFile.AREA_FLOOR != 0) {
                            node.RemoveFlag(AASFile.AREA_GAP)
                        }
                        numMerges++
                        Brush.DisplayRealTimeString("\r%6d", ++numMergedLeafNodes)
                        break
                    }
                    p = p.Next(s)
                }
            } while (p != null)
            return numMerges != 0
        }

        private fun MergeLeafNodes_r(bsp: idBrushBSP?, node: idBrushBSPNode?) {
            if (TempDump.NOT(node)) {
                return
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return
            }
            if (node.GetFlags() and BrushBSP.NODE_DONE != 0) {
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                MergeWithAdjacentLeafNodes(bsp, node)
                node.SetFlag(BrushBSP.NODE_DONE)
                return
            }
            MergeLeafNodes_r(bsp, node.GetChild(0))
            MergeLeafNodes_r(bsp, node.GetChild(1))

//            return;
        }

        private fun MergeLeafNodes(bsp: idBrushBSP?) {
            numMergedLeafNodes = 0
            Common.common.Printf("[Merge Leaf Nodes]\n")
            MergeLeafNodes_r(bsp, bsp.GetRootNode())
            bsp.GetRootNode().RemoveFlagRecurse(BrushBSP.NODE_DONE)
            bsp.PruneMergedTree_r(bsp.GetRootNode())
            Common.common.Printf("\r%6d leaf nodes merged\n", numMergedLeafNodes)
        }

        // storing file
        private fun SetupHash() {
            AASBuild_File.aas_vertexHash = idHashIndex(AASBuild_File.VERTEX_HASH_SIZE, 1024)
            AASBuild_File.aas_edgeHash = idHashIndex(AASBuild_File.EDGE_HASH_SIZE, 1024)
        }

        private fun ShutdownHash() {
//	delete aas_vertexHash;
//	delete aas_edgeHash;
            AASBuild_File.aas_vertexHash = null
            AASBuild_File.aas_edgeHash = null
        }

        private fun ClearHash(bounds: idBounds?) {
            var i: Int
            val f: Float
            var max: Float
            AASBuild_File.aas_vertexHash.Clear()
            AASBuild_File.aas_edgeHash.Clear()
            AASBuild_File.aas_vertexBounds = bounds
            max = bounds.oGet(1).x - bounds.oGet(0).x
            f = bounds.oGet(1).y - bounds.oGet(0).y
            if (f > max) {
                max = f
            }
            AASBuild_File.aas_vertexShift = (max / AASBuild_File.VERTEX_HASH_BOXSIZE).toInt()
            i = 0
            while (1 shl i < AASBuild_File.aas_vertexShift) {
                i++
            }
            if (i == 0) {
                AASBuild_File.aas_vertexShift = 1
            } else {
                AASBuild_File.aas_vertexShift = i
            }
        }

        private fun HashVec(vec: idVec3?): Int {
            val x: Int
            val y: Int
            x = (vec.oGet(0) - AASBuild_File.aas_vertexBounds.oGet(0).x + 0.5).toInt() + 2 shr 2
            y = (vec.oGet(1) - AASBuild_File.aas_vertexBounds.oGet(0).y + 0.5).toInt() + 2 shr 2
            return x + y * AASBuild_File.VERTEX_HASH_BOXSIZE and AASBuild_File.VERTEX_HASH_SIZE - 1
        }

        private fun GetVertex(v: idVec3?, vertexNum: IntArray?): Boolean {
            var i: Int
            val hashKey: Int
            var vn: Int
            val   /*aasVertex_t*/vert = idVec3()
            val p = idVec3()
            i = 0
            while (i < 3) {
                if (Math.abs(v.oGet(i) - idMath.Rint(v.oGet(i))) < AASBuild_File.INTEGRAL_EPSILON) {
                    vert.oSet(i, idMath.Rint(v.oGet(i)))
                } else {
                    vert.oSet(i, v.oGet(i))
                }
                i++
            }
            hashKey = HashVec(vert)
            vn = AASBuild_File.aas_vertexHash.First(hashKey)
            while (vn >= 0) {
                p.oSet(file.vertices.oGet(vn))
                // first compare z-axis because hash is based on x-y plane
                if (Math.abs(vert.z - p.z) < AASBuild_File.VERTEX_EPSILON && Math.abs(vert.x - p.x) < AASBuild_File.VERTEX_EPSILON && Math.abs(
                        vert.y - p.y
                    ) < AASBuild_File.VERTEX_EPSILON
                ) {
                    vertexNum.get(0) = vn
                    return true
                }
                vn = AASBuild_File.aas_vertexHash.Next(vn)
            }
            vertexNum.get(0) = file.vertices.Num()
            AASBuild_File.aas_vertexHash.Add(hashKey, file.vertices.Num())
            file.vertices.Append(vert)
            return false
        }

        private fun GetEdge(v1: idVec3?, v2: idVec3?, edgeNum: IntArray?, v1num: IntArray?): Boolean {
            return GetEdge(v1, v2, edgeNum, 0, v1num)
        }

        private fun GetEdge(v1: idVec3?, v2: idVec3?, edgeNum: IntArray?, edgeOffset: Int, v1num: IntArray?): Boolean {
            val hashKey: Int
            var e: Int
            val v2num = IntArray(1)
            var vertexNum: IntArray?
            val edge = aasEdge_s()
            var found: Boolean
            found = if (v1num.get(0) != -1) {
                true
            } else {
                GetVertex(v1, v1num)
            }
            found = found and GetVertex(v2, v2num)
            // if both vertexes are the same or snapped onto each other
            if (v1num.get(0) == v2num[0]) {
                edgeNum.get(edgeOffset + 0) = 0
                return true
            }
            hashKey = AASBuild_File.aas_edgeHash.GenerateKey(v1num.get(0), v2num[0])
            // if both vertexes where already stored
            if (found) {
                e = AASBuild_File.aas_edgeHash.First(hashKey)
                while (e >= 0) {
                    vertexNum = file.edges.oGet(e).vertexNum
                    if (vertexNum[0] == v2num[0]) {
                        if (vertexNum[1] == v1num.get(0)) {
                            // negative for a reversed edge
                            edgeNum.get(edgeOffset + 0) = -e
                            break
                        }
                    } else if (vertexNum[0] == v1num.get(0)) {
                        if (vertexNum[1] == v2num[0]) {
                            edgeNum.get(edgeOffset + 0) = e
                            break
                        }
                    }
                    e = AASBuild_File.aas_edgeHash.Next(e)
                }
                // if edge found in hash
                if (e >= 0) {
                    return true
                }
            }
            edgeNum.get(edgeOffset + 0) = file.edges.Num()
            AASBuild_File.aas_edgeHash.Add(hashKey, file.edges.Num())
            edge.vertexNum[0] = v1num.get(0)
            edge.vertexNum[1] = v2num[0]
            file.edges.Append(edge)
            return false
        }

        private fun GetFaceForPortal(portal: idBrushBSPPortal?, side: Int, faceNum: IntArray?): Boolean {
            var i: Int
            var j: Int
            val v1num = intArrayOf(0)
            var numFaceEdges: Int
            val faceEdges = IntArray(Winding.MAX_POINTS_ON_WINDING) //TODO:make these kind of arrays final?
            val w: idWinding?
            val face = aasFace_s()
            if (portal.GetFaceNum() > 0) {
                if (side != 0) {
                    faceNum.get(0) = -portal.GetFaceNum()
                } else {
                    faceNum.get(0) = portal.GetFaceNum()
                }
                return true
            }
            w = portal.GetWinding()
            // turn the winding into a sequence of edges
            numFaceEdges = 0
            v1num[0] = -1 // first vertex unknown
            i = 0
            while (i < w.GetNumPoints()) {
                GetEdge(w.oGet(i).ToVec3(), w.oGet((i + 1) % w.GetNumPoints()).ToVec3(), faceEdges, numFaceEdges, v1num)
                if (faceEdges[numFaceEdges] != 0) {
                    // last vertex of this edge is the first vertex of the next edge
                    v1num[0] =
                        file.edges.oGet(Math.abs(faceEdges[numFaceEdges])).vertexNum[Math_h.INTSIGNBITNOTSET(faceEdges[numFaceEdges])]

                    // this edge is valid so keep it
                    numFaceEdges++
                }
                i++
            }

            // should have at least 3 edges
            if (numFaceEdges < 3) {
                return false
            }

            // the polygon is invalid if some edge is found twice
            i = 0
            while (i < numFaceEdges) {
                j = i + 1
                while (j < numFaceEdges) {
                    if (faceEdges[i] == faceEdges[j] || faceEdges[i] == -faceEdges[j]) {
                        return false
                    }
                    j++
                }
                i++
            }
            portal.SetFaceNum(file.faces.Num())
            face.planeNum = file.planeList.FindPlane(
                portal.GetPlane(),
                AASBuild_File.AAS_PLANE_NORMAL_EPSILON,
                AASBuild_File.AAS_PLANE_DIST_EPSILON
            )
            face.flags = portal.GetFlags()
            face.areas[1] = 0
            face.areas[0] = face.areas[1]
            face.firstEdge = file.edgeIndex.Num()
            face.numEdges = numFaceEdges
            i = 0
            while (i < numFaceEdges) {
                file.edgeIndex.Append(faceEdges[i])
                i++
            }
            if (side != 0) {
                faceNum.get(0) = -file.faces.Num()
            } else {
                faceNum.get(0) = file.faces.Num()
            }
            file.faces.Append(face)
            return true
        }

        private fun GetAreaForLeafNode(node: idBrushBSPNode?, areaNum: IntArray?): Boolean {
            var s: Int
            val faceNum = IntArray(1)
            var p: idBrushBSPPortal?
            val area = aasArea_s()
            if (node.GetAreaNum() != 0) {
                areaNum.get(0) = -node.GetAreaNum()
                return true
            }
            area.flags = node.GetFlags()
            area.clusterAreaNum = 0
            area.cluster = area.clusterAreaNum
            area.contents = node.GetContents()
            area.firstFace = file.faceIndex.Num()
            area.numFaces = 0
            area.reach = null
            area.rev_reach = null
            p = node.GetPortals()
            while (p != null) {
                s = if (p.GetNode(1) == node) 1 else 0
                if (!GetFaceForPortal(p, s, faceNum)) {
                    p = p.Next(s)
                    continue
                }
                file.faceIndex.Append(faceNum[0])
                area.numFaces++
                if (faceNum[0] > 0) {
                    file.faces.oGet(Math.abs(faceNum[0])).areas[0] = file.areas.Num().toShort()
                } else {
                    file.faces.oGet(Math.abs(faceNum[0])).areas[1] = file.areas.Num().toShort()
                }
                p = p.Next(s)
            }
            if (0 == area.numFaces) {
                areaNum.get(0) = 0
                return false
            }
            areaNum.get(0) = -file.areas.Num()
            node.SetAreaNum(file.areas.Num())
            file.areas.Append(area)
            Brush.DisplayRealTimeString("\r%6d", file.areas.Num())
            return true
        }

        private fun StoreTree_r(node: idBrushBSPNode?): Int {
            val nodeNum: Int
            val child0: Int
            val child1: Int
            val areaNum = IntArray(1)
            val aasNode = aasNode_s()
            if (TempDump.NOT(node)) {
                return 0
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return 0
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                return if (GetAreaForLeafNode(node, areaNum)) {
                    areaNum[0]
                } else 0
            }
            aasNode.planeNum = file.planeList.FindPlane(
                node.GetPlane(),
                AASBuild_File.AAS_PLANE_NORMAL_EPSILON,
                AASBuild_File.AAS_PLANE_DIST_EPSILON
            )
            aasNode.children[1] = 0
            aasNode.children[0] = aasNode.children[1]
            nodeNum = file.nodes.Num()
            file.nodes.Append(aasNode)

            // !@#$%^ cause of some bug we cannot set the children directly with the StoreTree_r return value
            child0 = StoreTree_r(node.GetChild(0))
            file.nodes.oGet(nodeNum).children[0] = child0
            child1 = StoreTree_r(node.GetChild(1))
            file.nodes.oGet(nodeNum).children[1] = child1
            if (0 == child0 && 0 == child1) {
                file.nodes.SetNum(file.nodes.Num() - 1)
                return 0
            }
            return nodeNum
        }

        private fun GetSizeEstimate_r(parent: idBrushBSPNode?, node: idBrushBSPNode?, size: sizeEstimate_s?) {
            var p: idBrushBSPPortal?
            var s: Int
            if (TempDump.NOT(node)) {
                return
            }
            if (node.GetContents() and AASFile.AREACONTENTS_SOLID != 0) {
                return
            }
            if (TempDump.NOT(node.GetChild(0)) && TempDump.NOT(node.GetChild(1))) {
                // multiple branches of the bsp tree might point to the same leaf node
                if (node.GetParent() === parent) {
                    size.numAreas++
                    p = node.GetPortals()
                    while (p != null) {
                        s = if (p.GetNode(1) == node) 1 else 0
                        size.numFaceIndexes++
                        size.numEdgeIndexes += p.GetWinding().GetNumPoints()
                        p = p.Next(s)
                    }
                }
            } else {
                size.numNodes++
            }
            GetSizeEstimate_r(node, node.GetChild(0), size)
            GetSizeEstimate_r(node, node.GetChild(1), size)
        }

        private fun SetSizeEstimate(bsp: idBrushBSP?, file: idAASFileLocal?) {
            val size = sizeEstimate_s()
            size.numEdgeIndexes = 1
            size.numFaceIndexes = 1
            size.numAreas = 1
            size.numNodes = 1
            GetSizeEstimate_r(null, bsp.GetRootNode(), size)
            file.planeList.Resize(size.numNodes / 2, 1024)
            file.vertices.Resize(size.numEdgeIndexes / 3, 1024)
            file.edges.Resize(size.numEdgeIndexes / 2, 1024)
            file.edgeIndex.Resize(size.numEdgeIndexes, 4096)
            file.faces.Resize(size.numFaceIndexes, 1024)
            file.faceIndex.Resize(size.numFaceIndexes, 4096)
            file.areas.Resize(size.numAreas, 1024)
            file.nodes.Resize(size.numNodes, 1024)
        }

        private fun StoreFile(bsp: idBrushBSP?): Boolean {
            val edge: aasEdge_s
            val face: aasFace_s
            val area: aasArea_s
            val node: aasNode_s
            Common.common.Printf("[Store AAS]\n")
            SetupHash()
            ClearHash(bsp.GetTreeBounds())
            file = idAASFileLocal()
            file.Clear()
            SetSizeEstimate(bsp, file)

            // the first edge is a dummy
//	memset( &edge, 0, sizeof( edge ) );
            edge = aasEdge_s()
            file.edges.Append(edge)

            // the first face is a dummy
//	memset( &face, 0, sizeof( face ) );
            face = aasFace_s()
            file.faces.Append(face)

            // the first area is a dummy
//	memset( &area, 0, sizeof( area ) );
            area = aasArea_s()
            file.areas.Append(area)

            // the first node is a dummy
//	memset( &node, 0, sizeof( node ) );
            node = aasNode_s()
            file.nodes.Append(node)

            // store the tree
            StoreTree_r(bsp.GetRootNode())

            // calculate area bounds and a reachable point in the area
            file.FinishAreas()
            ShutdownHash()
            Common.common.Printf("\r%6d areas\n", file.areas.Num())
            return true
        }

        companion object {
            private val FACE_CHECKED: Int = Lib.Companion.BIT(31)
            private const val GRAVSUBDIV_EPSILON = 0.1f
        }
    }

    /*
     ============
     RunAAS_f
     ============
     */
    class RunAAS_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val aas = idAASBuild()
            val settings = idAASSettings()
            var mapName: idStr
            if (args.Argc() <= 1) {
                Common.common.Printf(
                    """runAAS [options] <mapfile>
options:
  -usePatches        = use bezier patches for collision detection.
  -writeBrushMap     = write a brush map with the AAS geometry.
  -playerFlood       = use player spawn points as valid AAS positions.
"""
                )
                return
            }
            Common.common.ClearWarnings("compiling AAS")
            Common.common.SetRefreshOnPrint(true)

            // get the aas settings definitions
            val dict = GameEdit.gameEdit.FindEntityDefDict("aas_types", false)
            if (TempDump.NOT(dict)) {
                Common.common.Error("Unable to find entityDef for 'aas_types'")
            }
            var kv = dict.MatchPrefix("type")
            while (kv != null) {
                val settingsDict = GameEdit.gameEdit.FindEntityDefDict(kv.GetValue().toString(), false)
                if (TempDump.NOT(settingsDict)) {
                    Common.common.Warning("Unable to find '%s' in def/aas.def", kv.GetValue())
                } else {
                    settings.FromDict(kv.GetValue().toString(), settingsDict)
                    i = AASBuild.ParseOptions(args, settings)
                    mapName = idStr(args.Argv(i))
                    mapName.BackSlashesToSlashes()
                    if (mapName.Icmpn("maps/", 4) != 0) {
                        mapName.oSet("maps/$mapName")
                    }
                    aas.Build(mapName, settings)
                }
                kv = dict.MatchPrefix("type", kv)
                if (kv != null) {
                    Common.common.Printf("=======================================================\n")
                }
            }
            Common.common.SetRefreshOnPrint(false)
            Common.common.PrintWarnings()
        }

        companion object {
            private val instance: cmdFunction_t? = RunAAS_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ============
     RunAASDir_f
     ============
     */
    class RunAASDir_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val aas = idAASBuild()
            val settings = idAASSettings()
            val mapFiles: idFileList?
            if (args.Argc() <= 1) {
                Common.common.Printf("runAASDir <folder>\n")
                return
            }
            Common.common.ClearWarnings("compiling AAS")
            Common.common.SetRefreshOnPrint(true)

            // get the aas settings definitions
            val dict = GameEdit.gameEdit.FindEntityDefDict("aas_types", false)
            if (TempDump.NOT(dict)) {
                Common.common.Error("Unable to find entityDef for 'aas_types'")
            }

            // scan for .map files
            mapFiles = FileSystem_h.fileSystem.ListFiles(idStr("maps/").toString() + args.Argv(1), ".map")

            // create AAS files for all the .map files
            i = 0
            while (i < mapFiles.GetNumFiles()) {
                if (i != 0) {
                    Common.common.Printf("=======================================================\n")
                }
                var kv = dict.MatchPrefix("type")
                while (kv != null) {
                    val settingsDict = GameEdit.gameEdit.FindEntityDefDict(kv.GetValue().toString(), false)
                    if (TempDump.NOT(settingsDict)) {
                        Common.common.Warning("Unable to find '%s' in def/aas.def", kv.GetValue())
                    } else {
                        settings.FromDict(kv.GetValue().toString(), settingsDict)
                        aas.Build(idStr("maps/" + args.Argv(1) + "/" + mapFiles.GetFile(i)), settings)
                    }
                    kv = dict.MatchPrefix("type", kv)
                    if (kv != null) {
                        Common.common.Printf("=======================================================\n")
                    }
                }
                i++
            }
            FileSystem_h.fileSystem.FreeFileList(mapFiles)
            Common.common.SetRefreshOnPrint(false)
            Common.common.PrintWarnings()
        }

        companion object {
            private val instance: cmdFunction_t? = RunAASDir_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ============
     RunReach_f
     ============
     */
    class RunReach_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val aas = idAASBuild()
            val settings = idAASSettings()
            if (args.Argc() <= 1) {
                Common.common.Printf("runReach [options] <mapfile>\n")
                return
            }
            Common.common.ClearWarnings("calculating AAS reachability")
            Common.common.SetRefreshOnPrint(true)

            // get the aas settings definitions
            val dict = GameEdit.gameEdit.FindEntityDefDict("aas_types", false)
            if (TempDump.NOT(dict)) {
                Common.common.Error("Unable to find entityDef for 'aas_types'")
            }
            var kv = dict.MatchPrefix("type")
            while (kv != null) {
                val settingsDict = GameEdit.gameEdit.FindEntityDefDict(kv.GetValue().toString(), false)
                if (TempDump.NOT(settingsDict)) {
                    Common.common.Warning("Unable to find '%s' in def/aas.def", kv.GetValue())
                } else {
                    settings.FromDict(kv.GetValue().toString(), settingsDict)
                    i = AASBuild.ParseOptions(args, settings)
                    aas.BuildReachability(idStr("maps/" + args.Argv(i)), settings)
                }
                kv = dict.MatchPrefix("type", kv)
                if (kv != null) {
                    Common.common.Printf("=======================================================\n")
                }
            }
            Common.common.SetRefreshOnPrint(false)
            Common.common.PrintWarnings()
        }

        companion object {
            private val instance: cmdFunction_t? = RunReach_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ============
     MergeAllowed
     ============
     */
    internal class MergeAllowed private constructor() : Allowance() {
        override fun run(b1: idBrush?, b2: idBrush?): Boolean {
            return b1.GetContents() == b2.GetContents() && TempDump.NOT((b1.GetFlags() or b2.GetFlags() and AASBuild.BFL_PATCH).toDouble())
        }

        companion object {
            val INSTANCE: Allowance? = MergeAllowed()
        }
    }

    /*
     ============
     ExpandedChopAllowed
     ============
     */
    internal class ExpandedChopAllowed private constructor() : Allowance() {
        override fun run(b1: idBrush?, b2: idBrush?): Boolean {
            return b1.GetContents() == b2.GetContents()
        }

        companion object {
            val INSTANCE: Allowance? = ExpandedChopAllowed()
        }
    }

    /*
     ============
     ExpandedMergeAllowed
     ============
     */
    internal class ExpandedMergeAllowed private constructor() : Allowance() {
        override fun run(b1: idBrush?, b2: idBrush?): Boolean {
            return b1.GetContents() == b2.GetContents()
        }

        companion object {
            val INSTANCE: Allowance? = ExpandedMergeAllowed()
        }
    }
}