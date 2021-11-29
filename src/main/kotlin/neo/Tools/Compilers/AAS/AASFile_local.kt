package neo.Tools.Compilers.AAS

import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasCluster_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasNode_s
import neo.Tools.Compilers.AAS.AASFile.aasPortal_s
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Tools.Compilers.AAS.AASFile.idAASFile
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Tools.Compilers.AAS.AASFile.idReachability_Special
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object AASFile_local {
    const val AAS_EDGE_GRANULARITY = 4096
    const val AAS_INDEX_GRANULARITY = 4096
    const val AAS_LIST_GRANULARITY = 1024
    const val AAS_PLANE_GRANULARITY = 4096
    const val AAS_VERTEX_GRANULARITY = 4096

    //
    const val TRACEPLANE_EPSILON = 0.125f

    /*
     ===============================================================================

     AAS File Local

     ===============================================================================
     */
    class idAASFileLocal : idAASFile() {
        // virtual 					~idAASFileLocal();
        override fun EdgeCenter(edgeNum: Int): idVec3? {
            val edge: aasEdge_s?
            edge = edges.oGet(edgeNum)
            return vertices.oGet(edge.vertexNum[0]).oPlus(vertices.oGet(edge.vertexNum[1])).oMultiply(0.5f)
        }

        override fun FaceCenter(faceNum: Int): idVec3? {
            var i: Int
            var edgeNum: Int
            val face: aasFace_s?
            var edge: aasEdge_s?
            val center = idVec3(Vector.getVec3_origin())
            face = faces.oGet(faceNum)
            if (face.numEdges > 0) {
                i = 0
                while (i < face.numEdges) {
                    edgeNum = edgeIndex.oGet(face.firstEdge + i)
                    edge = edges.oGet(Math.abs(edgeNum))
                    center.plusAssign(vertices.oGet(edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]))
                    i++
                }
                center.divAssign(face.numEdges.toFloat())
            }
            return center
        }

        override fun AreaCenter(areaNum: Int): idVec3? {
            var i: Int
            var faceNum: Int
            val area: aasArea_s?
            val center = idVec3(Vector.getVec3_origin())
            area = areas.oGet(areaNum)
            if (area.numFaces > 0) {
                i = 0
                while (i < area.numFaces) {
                    faceNum = faceIndex.oGet(area.firstFace + i)
                    center.plusAssign(FaceCenter(Math.abs(faceNum)))
                    i++
                }
                center.divAssign(area.numFaces.toFloat())
            }
            return center
        }

        override fun EdgeBounds(edgeNum: Int): idBounds? {
            val edge: aasEdge_s?
            val bounds = idBounds()
            edge = edges.oGet(Math.abs(edgeNum))
            bounds.oSet(0, bounds.oSet(1, vertices.oGet(edge.vertexNum[0])))
            bounds.timesAssign(vertices.oGet(edge.vertexNum[1]))
            return bounds
        }

        override fun FaceBounds(faceNum: Int): idBounds? {
            var i: Int
            var edgeNum: Int
            val face: aasFace_s?
            var edge: aasEdge_s?
            val bounds = idBounds()
            face = faces.oGet(faceNum)
            bounds.Clear()
            i = 0
            while (i < face.numEdges) {
                edgeNum = edgeIndex.oGet(face.firstEdge + i)
                edge = edges.oGet(Math.abs(edgeNum))
                bounds.AddPoint(vertices.oGet(edge.vertexNum[Math_h.INTSIGNBITSET(edgeNum)]))
                i++
            }
            return bounds
        }

        override fun AreaBounds(areaNum: Int): idBounds? {
            var i: Int
            var faceNum: Int
            val area: aasArea_s?
            val bounds = idBounds()
            area = areas.oGet(areaNum)
            bounds.Clear()
            i = 0
            while (i < area.numFaces) {
                faceNum = faceIndex.oGet(area.firstFace + i)
                bounds.timesAssign(FaceBounds(Math.abs(faceNum)))
                i++
            }
            return bounds
        }

        override fun PointAreaNum(origin: idVec3?): Int {
            var nodeNum: Int
            var node: aasNode_s?
            nodeNum = 1
            do {
                node = nodes.oGet(nodeNum)
                nodeNum = if (planeList.oGet(node.planeNum).Side(origin) == Plane.PLANESIDE_BACK) {
                    node.children[1]
                } else {
                    node.children[0]
                }
                if (nodeNum < 0) {
                    return -nodeNum
                }
            } while (nodeNum != 0)
            return 0
        }

        override fun PointReachableAreaNum(
            origin: idVec3?,
            searchBounds: idBounds?,
            areaFlags: Int,
            excludeTravelFlags: Int
        ): Int {
            var areaNum: Int
            var i: Int
            val start = idVec3(origin)
            val end = idVec3()
            val trace = aasTrace_s()
            val bounds = idBounds()
            var frak: Float
            val areaList = IntArray(32)
            val pointList: Array<idVec3?> = idVec3.Companion.generateArray(32)
            trace.areas = areaList
            trace.points = idVec3.Companion.generateArray(32)
            trace.maxAreas = areaList.size
            trace.getOutOfSolid = 1 // true;
            areaNum = PointAreaNum(start)
            if (areaNum != 0) {
                if (areas.oGet(areaNum).flags and areaFlags != 0 && areas.oGet(areaNum).travelFlags and excludeTravelFlags == 0) {
                    return areaNum
                }
            } else {
                // trace up
                end.oSet(start)
                end.plusAssign(2, 32.0f)
                Trace(trace, start, end)
                if (trace.numAreas >= 1) {
                    if (areas.oGet(0).flags and areaFlags != 0 && areas.oGet(0).travelFlags and excludeTravelFlags == 0) {
                        return areaList[0]
                    }
                    start.oSet(pointList[0])
                    start.plusAssign(2, 1.0f)
                }
            }

            // trace down
            end.oSet(start)
            end.minusAssign(2, 32.0f)
            Trace(trace, start, end)
            if (trace.lastAreaNum != 0) {
                if (areas.oGet(trace.lastAreaNum).flags and areaFlags != 0 && areas.oGet(trace.lastAreaNum).travelFlags and excludeTravelFlags == 0) {
                    return trace.lastAreaNum
                }
                start.oSet(trace.endpos)
            }

            // expand bounds until an area is found
            i = 1
            while (i <= 12) {
                frak = i * (1.0f / 12.0f)
                bounds.oSet(0, origin.oPlus(searchBounds.oGet(0).times(frak)))
                bounds.oSet(1, origin.oPlus(searchBounds.oGet(1).times(frak)))
                areaNum = BoundsReachableAreaNum(bounds, areaFlags, excludeTravelFlags)
                if (areaNum != 0 && areas.oGet(areaNum).flags and areaFlags != 0 && areas.oGet(areaNum).travelFlags and excludeTravelFlags == 0) {
                    return areaNum
                }
                i++
            }
            return 0
        }

        override fun BoundsReachableAreaNum(bounds: idBounds?, areaFlags: Int, excludeTravelFlags: Int): Int {
            return BoundsReachableAreaNum_r(1, bounds, areaFlags, excludeTravelFlags)
        }

        override fun PushPointIntoAreaNum(areaNum: Int, point: idVec3?) {
            var i: Int
            var faceNum: Int
            val area: aasArea_s?
            var face: aasFace_s?
            area = areas.oGet(areaNum)

            // push the point to the right side of all area face planes
            i = 0
            while (i < area.numFaces) {
                faceNum = faceIndex.oGet(area.firstFace + i)
                face = faces.oGet(Math.abs(faceNum))
                val plane = planeList.oGet(face.planeNum xor Math_h.INTSIGNBITSET(faceNum))
                val dist = plane.Distance(point)

                // project the point onto the face plane if it is on the wrong side
                if (dist < 0.0f) {
                    point.minusAssign(plane.Normal().times(dist))
                }
                i++
            }
        }

        override fun Trace(trace: aasTrace_s?, start: idVec3?, end: idVec3?): Boolean {
            var side: Int
            var nodeNum: Int
            var tmpPlaneNum: Int
            var front: Double
            var back: Double
            var frac: Double
            val cur_start = idVec3()
            val cur_end = idVec3()
            val cur_mid = idVec3()
            val v1 = idVec3()
            val v2 = idVec3()
            val tracestack = TempDump.allocArray(aasTraceStack_s::class.java, AASFile.MAX_AAS_TREE_DEPTH)
            var tstack_p: Int
            var node: aasNode_s?
            var plane: idPlane?
            trace.numAreas = 0
            trace.lastAreaNum = 0
            trace.blockingAreaNum = 0
            tstack_p = 0 //tracestack;
            tracestack[tstack_p].start.oSet(start)
            tracestack[tstack_p].end.oSet(end)
            tracestack[tstack_p].planeNum = 0
            tracestack[tstack_p].nodeNum = 1 //start with the root of the tree
            tstack_p++
            while (true) {
                tstack_p--
                // if the trace stack is empty
                if (tstack_p < 0) {
                    if (TempDump.NOT(trace.lastAreaNum.toDouble())) {
                        // completely in solid
                        trace.fraction = 0.0f
                        trace.endpos.oSet(start)
                    } else {
                        // nothing was hit
                        trace.fraction = 1.0f
                        trace.endpos.oSet(end)
                    }
                    trace.planeNum = 0
                    return false
                }

                // number of the current node to test the line against
                nodeNum = tracestack[tstack_p].nodeNum

                // if it is an area
                if (nodeNum < 0) {
                    // if can't enter the area
                    if (areas.oGet(-nodeNum).flags and trace.flags != 0 || areas.oGet(-nodeNum).travelFlags and trace.travelFlags != 0) {
                        if (TempDump.NOT(trace.lastAreaNum.toDouble())) {
                            trace.fraction = 0.0f
                            v1.oSet(Vector.getVec3_origin())
                        } else {
                            v1.oSet(end.oMinus(start))
                            v2.oSet(tracestack[tstack_p].start.oMinus(start))
                            trace.fraction = v2.Length() / v1.Length()
                        }
                        trace.endpos.oSet(tracestack[tstack_p].start)
                        trace.blockingAreaNum = -nodeNum
                        trace.planeNum = tracestack[tstack_p].planeNum
                        // always take the plane with normal facing towards the trace start
                        plane = planeList.oGet(trace.planeNum)
                        if (v1.times(plane.Normal()) > 0.0f) {
                            trace.planeNum = trace.planeNum xor 1
                        }
                        return true
                    }
                    trace.lastAreaNum = -nodeNum
                    if (trace.numAreas < trace.maxAreas) {
                        if (trace.areas != null) {
                            trace.areas[trace.numAreas] = -nodeNum
                        }
                        if (trace.points != null) {
                            trace.points[trace.numAreas].oSet(tracestack[tstack_p].start)
                        }
                        trace.numAreas++
                    }
                    continue
                }

                // if it is a solid leaf
                if (0 == nodeNum) {
                    if (0 == trace.lastAreaNum) {
                        trace.fraction = 0.0f
                        v1.oSet(Vector.getVec3_origin())
                    } else {
                        v1.oSet(end.oMinus(start))
                        v2.oSet(tracestack[tstack_p].start.oMinus(start))
                        trace.fraction = v2.Length() / v1.Length()
                    }
                    trace.endpos.oSet(tracestack[tstack_p].start)
                    trace.blockingAreaNum = 0 // hit solid leaf
                    trace.planeNum = tracestack[tstack_p].planeNum
                    // always take the plane with normal facing towards the trace start
                    plane = planeList.oGet(trace.planeNum)
                    if (v1.times(plane.Normal()) > 0.0f) {
                        trace.planeNum = trace.planeNum xor 1
                    }
                    return if (0 == trace.lastAreaNum && trace.getOutOfSolid != 0) {
                        continue
                    } else {
                        true
                    }
                }

                // the node to test against
                node = nodes.oGet(nodeNum)
                // start point of current line to test against node
                cur_start.oSet(tracestack[tstack_p].start)
                // end point of the current line to test against node
                cur_end.oSet(tracestack[tstack_p].end)
                // the current node plane
                plane = planeList.oGet(node.planeNum)
                front = plane.Distance(cur_start).toDouble()
                back = plane.Distance(cur_end).toDouble()

                // if the whole to be traced line is totally at the front of this node
                // only go down the tree with the front child
                if (front >= -Plane.ON_EPSILON && back >= -Plane.ON_EPSILON) {
                    // keep the current start and end point on the stack and go down the tree with the front child
                    tracestack[tstack_p].nodeNum = node.children[0]
                    tstack_p++
                    if (tstack_p >= AASFile.MAX_AAS_TREE_DEPTH) { //TODO:check that pointer to address comparison is the same as this.
                        Common.common.Error("idAASFileLocal::Trace: stack overflow\n")
                        return false
                    }
                } // if the whole to be traced line is totally at the back of this node
                else if (front < Plane.ON_EPSILON && back < Plane.ON_EPSILON) {
                    // keep the current start and end point on the stack and go down the tree with the back child
                    tracestack[tstack_p].nodeNum = node.children[1]
                    tstack_p++
                    if (tstack_p >= AASFile.MAX_AAS_TREE_DEPTH) {
                        Common.common.Error("idAASFileLocal::Trace: stack overflow\n")
                        return false
                    }
                } // go down the tree both at the front and back of the node
                else {
                    tmpPlaneNum = tracestack[tstack_p].planeNum
                    // calculate the hit point with the node plane
                    // put the cross point TRACEPLANE_EPSILON on the near side
                    frac = if (front < 0) {
                        (front + AASFile_local.TRACEPLANE_EPSILON) / (front - back)
                    } else {
                        (front - AASFile_local.TRACEPLANE_EPSILON) / (front - back)
                    }
                    if (frac < 0) {
                        frac = 0.001 //0
                    } else if (frac > 1) {
                        frac = 0.999 //1
                    }
                    cur_mid.oSet(cur_start.oPlus(cur_end.oMinus(cur_start).oMultiply(frac.toFloat()))) //TODO:downcast?

                    // side the front part of the line is on
                    side = if (front < 0) 1 else 0

                    // first put the end part of the line on the stack (back side)
                    tracestack[tstack_p].start.oSet(cur_mid)
                    tracestack[tstack_p].planeNum = node.planeNum
                    tracestack[tstack_p].nodeNum = node.children[1 xor side]
                    tstack_p++
                    if (tstack_p >= AASFile.MAX_AAS_TREE_DEPTH) {
                        Common.common.Error("idAASFileLocal::Trace: stack overflow\n")
                        return false
                    }
                    // now put the part near the start of the line on the stack so we will
                    // continue with that part first.
                    tracestack[tstack_p].start.oSet(cur_start)
                    tracestack[tstack_p].end.oSet(cur_mid)
                    tracestack[tstack_p].planeNum = tmpPlaneNum
                    tracestack[tstack_p].nodeNum = node.children[side]
                    tstack_p++
                    if (tstack_p >= AASFile.MAX_AAS_TREE_DEPTH) {
                        Common.common.Error("idAASFileLocal::Trace: stack overflow\n")
                        return false
                    }
                }
            }
            //            return false;
        }

        override fun PrintInfo() {
            Common.common.Printf("%6d KB file size\n", MemorySize() shr 10)
            Common.common.Printf("%6d areas\n", areas.Num())
            Common.common.Printf("%6d max tree depth\n", MaxTreeDepth())
            ReportRoutingEfficiency()
        }

        fun Load(fileName: idStr?,    /*unsigned int*/mapFileCRC: Long): Boolean {
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES)
            val token = idToken()
            val depth: Int
            val c: Int
            name = fileName
            crc = mapFileCRC
            Common.common.Printf("[Load AAS]\n")
            Common.common.Printf("loading %s\n", name)
            if (!src.LoadFile(name)) {
                return false
            }
            if (!src.ExpectTokenString(AASFile.AAS_FILEID)) {
                Common.common.Warning("Not an AAS file: '%s'", name)
                return false
            }
            if (!src.ReadToken(token) || token != AASFile.AAS_FILEVERSION) {
                Common.common.Warning(
                    "AAS file '%s' has version %s instead of %s",
                    name,
                    token,
                    AASFile.AAS_FILEVERSION
                )
                return false
            }
            if (0 == src.ExpectTokenType(Token.TT_NUMBER, Token.TT_INTEGER, token)) {
                Common.common.Warning("AAS file '%s' has no map file CRC", name)
                return false
            }
            c = token.GetUnsignedLongValue().toInt()
            if (mapFileCRC != 0L && c.toLong() != mapFileCRC) {
                Common.common.Warning("AAS file '%s' is out of date", name)
                return false
            }

            // clear the file in memory
            Clear()

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token == "settings") {
                    if (!settings.FromParser(src)) {
                        return false
                    }
                } else if (token == "planes") {
                    if (!ParsePlanes(src)) {
                        return false
                    }
                } else if (token == "vertices") {
                    if (!ParseVertices(src)) {
                        return false
                    }
                } else if (token == "edges") {
                    if (!ParseEdges(src)) {
                        return false
                    }
                } else if (token == "edgeIndex") {
                    if (!ParseIndex(src, edgeIndex)) {
                        return false
                    }
                } else if (token == "faces") {
                    if (!ParseFaces(src)) {
                        return false
                    }
                } else if (token == "faceIndex") {
                    if (!ParseIndex(src, faceIndex)) {
                        return false
                    }
                } else if (token == "areas") {
                    if (!ParseAreas(src)) {
                        return false
                    }
                } else if (token == "nodes") {
                    if (!ParseNodes(src)) {
                        return false
                    }
                } else if (token == "portals") {
                    if (!ParsePortals(src)) {
                        return false
                    }
                } else if (token == "portalIndex") {
                    if (!ParseIndex(src, portalIndex)) {
                        return false
                    }
                } else if (token == "clusters") {
                    if (!ParseClusters(src)) {
                        return false
                    }
                } else {
                    src.Error("idAASFileLocal::Load: bad token \"%s\"", token)
                    return false
                }
            }
            FinishAreas()
            depth = MaxTreeDepth()
            if (depth > AASFile.MAX_AAS_TREE_DEPTH) {
                src.Error("idAASFileLocal::Load: tree depth = %d", depth)
            }
            Common.common.Printf("done.\n")
            return true
        }

        fun Write(fileName: idStr?,    /*unsigned int*/mapFileCRC: Long): Boolean {
            var i: Int
            var num: Int
            val aasFile: idFile?
            var reach: idReachability?
            Common.common.Printf("[Write AAS]\n")
            Common.common.Printf("writing %s\n", fileName)
            name = fileName
            crc = mapFileCRC
            aasFile = FileSystem_h.fileSystem.OpenFileWrite(fileName.toString(), "fs_devpath")
            if (TempDump.NOT(aasFile)) {
                Common.common.Error("Error opening %s", fileName)
                return false
            }
            aasFile.WriteFloatString("%s \"%s\"\n\n", AASFile.AAS_FILEID, AASFile.AAS_FILEVERSION)
            aasFile.WriteFloatString("%u\n\n", mapFileCRC)

            // write out the settings
            aasFile.WriteFloatString("settings\n")
            settings.WriteToFile(aasFile)

            // write out planes
            aasFile.WriteFloatString("planes %d {\n", planeList.Num())
            i = 0
            while (i < planeList.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %f %f %f %f )\n",
                    i,
                    planeList.oGet(i).Normal().x,
                    planeList.oGet(i).Normal().y,
                    planeList.oGet(i).Normal().z,
                    planeList.oGet(i).Dist()
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out vertices
            aasFile.WriteFloatString("vertices %d {\n", vertices.Num())
            i = 0
            while (i < vertices.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %f %f %f )\n",
                    i,
                    vertices.oGet(i).x,
                    vertices.oGet(i).y,
                    vertices.oGet(i).z
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out edges
            aasFile.WriteFloatString("edges %d {\n", edges.Num())
            i = 0
            while (i < edges.Num()) {
                aasFile.WriteFloatString("\t%d ( %d %d )\n", i, edges.oGet(i).vertexNum[0], edges.oGet(i).vertexNum[1])
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out edgeIndex
            aasFile.WriteFloatString("edgeIndex %d {\n", edgeIndex.Num())
            i = 0
            while (i < edgeIndex.Num()) {
                aasFile.WriteFloatString("\t%d ( %d )\n", i, edgeIndex.oGet(i))
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out faces
            aasFile.WriteFloatString("faces %d {\n", faces.Num())
            i = 0
            while (i < faces.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %d %d %d %d %d %d )\n", i, faces.oGet(i).planeNum, faces.oGet(i).flags,
                    faces.oGet(i).areas[0], faces.oGet(i).areas[1], faces.oGet(i).firstEdge, faces.oGet(i).numEdges
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out faceIndex
            aasFile.WriteFloatString("faceIndex %d {\n", faceIndex.Num())
            i = 0
            while (i < faceIndex.Num()) {
                aasFile.WriteFloatString("\t%d ( %d )\n", i, faceIndex.oGet(i))
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out areas
            aasFile.WriteFloatString("areas %d {\n", areas.Num())
            i = 0
            while (i < areas.Num()) {
                num = 0
                reach = areas.oGet(i).reach
                while (reach != null) {
                    num++
                    reach = reach.next
                }
                aasFile.WriteFloatString(
                    "\t%d ( %d %d %d %d %d %d ) %d {\n",
                    i,
                    areas.oGet(i).flags,
                    areas.oGet(i).contents,
                    areas.oGet(i).firstFace,
                    areas.oGet(i).numFaces,
                    areas.oGet(i).cluster,
                    areas.oGet(i).clusterAreaNum,
                    num
                )
                reach = areas.oGet(i).reach
                while (reach != null) {
                    AASFile.Reachability_Write(aasFile, reach)
                    //                    switch (reach.travelType) {
//                        case TFL_SPECIAL:
//                            Reachability_Special_Write(aasFile, (idReachability_Special) reach);
//                            break;
//                    }
                    if (reach.travelType == AASFile.TFL_SPECIAL) {
                        AASFile.Reachability_Special_Write(aasFile, reach as idReachability_Special?)
                    }
                    aasFile.WriteFloatString("\n")
                    reach = reach.next
                }
                aasFile.WriteFloatString("\t}\n")
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out nodes
            aasFile.WriteFloatString("nodes %d {\n", nodes.Num())
            i = 0
            while (i < nodes.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %d %d %d )\n",
                    i,
                    nodes.oGet(i).planeNum,
                    nodes.oGet(i).children[0],
                    nodes.oGet(i).children[1]
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out portals
            aasFile.WriteFloatString("portals %d {\n", portals.Num())
            i = 0
            while (i < portals.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %d %d %d %d %d )\n", i, portals.oGet(i).areaNum, portals.oGet(i).clusters[0],
                    portals.oGet(i).clusters[1], portals.oGet(i).clusterAreaNum[0], portals.oGet(i).clusterAreaNum[1]
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out portalIndex
            aasFile.WriteFloatString("portalIndex %d {\n", portalIndex.Num())
            i = 0
            while (i < portalIndex.Num()) {
                aasFile.WriteFloatString("\t%d ( %d )\n", i, portalIndex.oGet(i))
                i++
            }
            aasFile.WriteFloatString("}\n")

            // write out clusters
            aasFile.WriteFloatString("clusters %d {\n", clusters.Num())
            i = 0
            while (i < clusters.Num()) {
                aasFile.WriteFloatString(
                    "\t%d ( %d %d %d %d )\n", i, clusters.oGet(i).numAreas, clusters.oGet(i).numReachableAreas,
                    clusters.oGet(i).firstPortal, clusters.oGet(i).numPortals
                )
                i++
            }
            aasFile.WriteFloatString("}\n")

            // close file
            FileSystem_h.fileSystem.CloseFile(aasFile)
            Common.common.Printf("done.\n")
            return true
        }

        fun MemorySize(): Int {
            var size: Int
            size = planeList.Size()
            size += vertices.Size()
            size += edges.Size()
            size += edgeIndex.Size()
            size += faces.Size()
            size += faceIndex.Size()
            size += areas.Size()
            size += nodes.Size()
            size += portals.Size()
            size += portalIndex.Size()
            size += clusters.Size()
            //	size += sizeof( idReachability_Walk ) * NumReachabilities();
            size += NumReachabilities()
            return size
        }

        fun ReportRoutingEfficiency() {
            var numReachableAreas: Int
            var total: Int
            var i: Int
            var n: Int
            numReachableAreas = 0
            total = 0
            i = 0
            while (i < clusters.Num()) {
                n = clusters.oGet(i).numReachableAreas
                numReachableAreas += n
                total += n * n
                i++
            }
            total += numReachableAreas * portals.Num()
            Common.common.Printf("%6d reachable areas\n", numReachableAreas)
            Common.common.Printf("%6d reachabilities\n", NumReachabilities())
            Common.common.Printf("%6d KB max routing cache\n", total * 3 shr 10)
        }

        fun Optimize() {
            var i: Int
            var j: Int
            var k: Int
            var faceNum: Int
            var edgeNum: Int
            var areaFirstFace: Int
            var faceFirstEdge: Int
            var area: aasArea_s?
            var face: aasFace_s?
            var edge: aasEdge_s?
            var reach: idReachability?
            val vertexRemap = idList<Int?>()
            val edgeRemap = idList<Int?>()
            val faceRemap = idList<Int?>()
            val newVertices = idList<idVec3?>()
            val newEdges = idList<aasEdge_s?>()
            val newEdgeIndex = idList<Int?>()
            val newFaces = idList<aasFace_s?>()
            val newFaceIndex = idList<Int?>()
            vertexRemap.AssureSize(vertices.Num(), -1)
            edgeRemap.AssureSize(edges.Num(), 0)
            faceRemap.AssureSize(faces.Num(), 0)
            newVertices.Resize(vertices.Num())
            newEdges.Resize(edges.Num())
            newEdges.SetNum(1, false)
            newEdgeIndex.Resize(edgeIndex.Num())
            newFaces.Resize(faces.Num())
            newFaces.SetNum(1, false)
            newFaceIndex.Resize(faceIndex.Num())
            i = 0
            while (i < areas.Num()) {
                area = areas.oGet(i)
                areaFirstFace = newFaceIndex.Num()
                j = 0
                while (j < area.numFaces) {
                    faceNum = faceIndex.oGet(area.firstFace + j)
                    face = faces.oGet(Math.abs(faceNum))

                    // store face
                    if (TempDump.NOT(faceRemap.oGet(Math.abs(faceNum)))) {
                        faceRemap.oSet(Math.abs(faceNum), newFaces.Num())
                        newFaces.Append(face)

                        // don't store edges for faces we don't care about
                        if (0 == face.flags and (AASFile.FACE_FLOOR or AASFile.FACE_LADDER)) {
                            newFaces.oGet(newFaces.Num() - 1).firstEdge = 0
                            newFaces.oGet(newFaces.Num() - 1).numEdges = 0
                        } else {

                            // store edges
                            faceFirstEdge = newEdgeIndex.Num()
                            k = 0
                            while (k < face.numEdges) {
                                edgeNum = edgeIndex.oGet(face.firstEdge + k)
                                edge = edges.oGet(Math.abs(edgeNum))
                                if (TempDump.NOT(edgeRemap.oGet(Math.abs(edgeNum)))) {
                                    if (edgeNum < 0) {
                                        edgeRemap.oSet(Math.abs(edgeNum), -newEdges.Num())
                                    } else {
                                        edgeRemap.oSet(Math.abs(edgeNum), newEdges.Num())
                                    }

                                    // remap vertices if not yet remapped
                                    if (vertexRemap.oGet(edge.vertexNum[0]) == -1) {
                                        vertexRemap.oSet(edge.vertexNum[0], newVertices.Num())
                                        newVertices.Append(vertices.oGet(edge.vertexNum[0]))
                                    }
                                    if (vertexRemap.oGet(edge.vertexNum[1]) == -1) {
                                        vertexRemap.oSet(edge.vertexNum[1], newVertices.Num())
                                        newVertices.Append(vertices.oGet(edge.vertexNum[1]))
                                    }
                                    newEdges.Append(edge)
                                    newEdges.oGet(newEdges.Num() - 1).vertexNum[0] = vertexRemap.oGet(edge.vertexNum[0])
                                    newEdges.oGet(newEdges.Num() - 1).vertexNum[1] = vertexRemap.oGet(edge.vertexNum[1])
                                }
                                newEdgeIndex.Append(edgeRemap.oGet(Math.abs(edgeNum)))
                                k++
                            }
                            newFaces.oGet(newFaces.Num() - 1).firstEdge = faceFirstEdge
                            newFaces.oGet(newFaces.Num() - 1).numEdges = newEdgeIndex.Num() - faceFirstEdge
                        }
                    }
                    if (faceNum < 0) {
                        newFaceIndex.Append(-faceRemap.oGet(Math.abs(faceNum)))
                    } else {
                        newFaceIndex.Append(faceRemap.oGet(Math.abs(faceNum)))
                    }
                    j++
                }
                area.firstFace = areaFirstFace
                area.numFaces = newFaceIndex.Num() - areaFirstFace

                // remap the reachability edges
                reach = area.reach
                while (reach != null) {
                    reach.edgeNum = Math.abs(edgeRemap.oGet(reach.edgeNum))
                    reach = reach.next
                }
                i++
            }

            // store new list
            vertices.oSet(newVertices)
            edges.oSet(newEdges)
            edgeIndex.oSet(newEdgeIndex)
            faces.oSet(newFaces)
            faceIndex.oSet(newFaceIndex)
        }

        fun LinkReversedReachability() {
            var i: Int
            var reach: idReachability?

            // link reversed reachabilities
            i = 0
            while (i < areas.Num()) {
                reach = areas.oGet(i).reach
                while (reach != null) {
                    reach.rev_next = areas.oGet(reach.toAreaNum.toInt()).rev_reach
                    areas.oGet(reach.toAreaNum.toInt()).rev_reach = reach
                    reach = reach.next
                }
                i++
            }
        }

        fun FinishAreas() {
            var i: Int
            i = 0
            while (i < areas.Num()) {
                areas.oGet(i).center.oSet(AreaReachableGoal(i))
                areas.oGet(i).bounds = AreaBounds(i)
                i++
            }
        }

        fun Clear() {
            planeList.Clear()
            vertices.Clear()
            edges.Clear()
            edgeIndex.Clear()
            faces.Clear()
            faceIndex.Clear()
            areas.Clear()
            nodes.Clear()
            portals.Clear()
            portalIndex.Clear()
            clusters.Clear()
        }

        fun DeleteReachabilities() {
            var i: Int
            var reach: idReachability?
            var nextReach: idReachability?
            i = 0
            while (i < areas.Num()) {
                reach = areas.oGet(i).reach
                while (reach != null) {
                    nextReach = reach.next
                    reach = nextReach
                }
                areas.oGet(i).reach = null
                areas.oGet(i).rev_reach = null
                i++
            }
        }

        fun DeleteClusters() {
            val portal: aasPortal_s
            val cluster: aasCluster_s
            portals.Clear()
            portalIndex.Clear()
            clusters.Clear()

            // first portal is a dummy
//	memset( &portal, 0, sizeof( portal ) );
            portal = aasPortal_s()
            portals.Append(portal)

            // first cluster is a dummy
//	memset( &cluster, 0, sizeof( portal ) );
            cluster = aasCluster_s()
            clusters.Append(cluster)
        }

        private fun ParseIndex(src: idLexer?, indexes: idList<Int?>?): Boolean {
            var   /*aasIndex_s*/index: Int
            val numIndexes = src.ParseInt()
            indexes.Resize(numIndexes)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numIndexes) {
                src.ParseInt()
                src.ExpectTokenString("(")
                index = src.ParseInt()
                src.ExpectTokenString(")")
                indexes.Append(index)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParsePlanes(src: idLexer?): Boolean {
            val numPlanes = src.ParseInt()
            planeList.Resize(numPlanes)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numPlanes) {
                val plane = idPlane()
                val vec = idVec4()
                src.ParseInt()
                if (!src.Parse1DMatrix(4, vec)) {
                    return false
                }
                plane.SetNormal(vec.ToVec3())
                plane.SetDist(vec.oGet(3))
                planeList.Append(plane)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParseVertices(src: idLexer?): Boolean {
            val numVertices = src.ParseInt()
            vertices.Resize(numVertices)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numVertices) {
                val vec = idVec3()
                src.ParseInt()
                if (!src.Parse1DMatrix(3, vec)) {
                    return false
                }
                vertices.Append(vec)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParseEdges(src: idLexer?): Boolean {
            val numEdges = src.ParseInt()
            edges.Resize(numEdges)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numEdges) {
                val edge = aasEdge_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                edge.vertexNum[0] = src.ParseInt()
                edge.vertexNum[1] = src.ParseInt()
                src.ExpectTokenString(")")
                edges.Append(edge)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParseFaces(src: idLexer?): Boolean {
            val numFaces = src.ParseInt()
            faces.Resize(numFaces)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numFaces) {
                val face = aasFace_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                face.planeNum = src.ParseInt()
                face.flags = src.ParseInt()
                face.areas[0] = src.ParseInt().toShort()
                face.areas[1] = src.ParseInt().toShort()
                face.firstEdge = src.ParseInt()
                face.numEdges = src.ParseInt()
                src.ExpectTokenString(")")
                faces.Append(face)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParseReachabilities(src: idLexer?, areaNum: Int): Boolean {
            val area = areas.oGet(areaNum)
            val num = src.ParseInt()
            src.ExpectTokenString("{")
            area.reach = null
            area.rev_reach = null
            area.travelFlags = AreaContentsTravelFlags(areaNum)
            for (j in 0 until num) {
                val reach = idReachability()
                var newReach: idReachability?
                var special: idReachability_Special
                AASFile.Reachability_Read(src, reach)
                //		switch( reach.travelType ) {
//			case TFL_SPECIAL:
//				newReach = special = new idReachability_Special();
//				Reachability_Special_Read( src, special );
//				break;
//			default:
//				newReach = new idReachability();
//				break;
//		}
                if (reach.travelType == AASFile.TFL_SPECIAL) {
                    special = idReachability_Special()
                    newReach = special
                    AASFile.Reachability_Special_Read(src, special)
                } else {
                    newReach = idReachability()
                }
                newReach.CopyBase(reach)
                newReach.fromAreaNum = areaNum.toShort()
                newReach.next = area.reach
                area.reach = newReach
            }
            src.ExpectTokenString("}")
            return true
        }

        private fun ParseAreas(src: idLexer?): Boolean {
            val numAreas = src.ParseInt()
            areas.Resize(numAreas)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numAreas) {
                val area = aasArea_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                area.flags = src.ParseInt()
                area.contents = src.ParseInt()
                area.firstFace = src.ParseInt()
                area.numFaces = src.ParseInt()
                area.cluster = src.ParseInt().toShort()
                area.clusterAreaNum = src.ParseInt().toShort()
                src.ExpectTokenString(")")
                areas.Append(area)
                ParseReachabilities(src, i)
            }
            if (!src.ExpectTokenString("}")) {
                return false
            }
            LinkReversedReachability()
            return true
        }

        private fun ParseNodes(src: idLexer?): Boolean {
            val numNodes = src.ParseInt()
            nodes.Resize(numNodes)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numNodes) {
                val node = aasNode_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                node.planeNum = src.ParseInt()
                node.children[0] = src.ParseInt()
                node.children[1] = src.ParseInt()
                src.ExpectTokenString(")")
                nodes.Append(node)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParsePortals(src: idLexer?): Boolean {
            val numPortals = src.ParseInt()
            portals.Resize(numPortals)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numPortals) {
                val portal = aasPortal_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                portal.areaNum = src.ParseInt().toShort()
                portal.clusters[0] = src.ParseInt().toShort()
                portal.clusters[1] = src.ParseInt().toShort()
                portal.clusterAreaNum[0] = src.ParseInt().toShort()
                portal.clusterAreaNum[1] = src.ParseInt().toShort()
                src.ExpectTokenString(")")
                portals.Append(portal)
            }
            return src.ExpectTokenString("}")
        }

        private fun ParseClusters(src: idLexer?): Boolean {
            val numClusters = src.ParseInt()
            clusters.Resize(numClusters)
            if (!src.ExpectTokenString("{")) {
                return false
            }
            for (i in 0 until numClusters) {
                val cluster = aasCluster_s()
                src.ParseInt()
                src.ExpectTokenString("(")
                cluster.numAreas = src.ParseInt()
                cluster.numReachableAreas = src.ParseInt()
                cluster.firstPortal = src.ParseInt()
                cluster.numPortals = src.ParseInt()
                src.ExpectTokenString(")")
                clusters.Append(cluster)
            }
            return src.ExpectTokenString("}")
        }

        private fun BoundsReachableAreaNum_r(
            nodeNum: Int,
            bounds: idBounds?,
            areaFlags: Int,
            excludeTravelFlags: Int
        ): Int {
            var nodeNum = nodeNum
            var res: Int
            var node: aasNode_s?
            while (nodeNum != 0) {
                if (nodeNum < 0) {
                    return if (areas.oGet(-nodeNum).flags and areaFlags != 0 && areas.oGet(-nodeNum).travelFlags and excludeTravelFlags == 0) {
                        -nodeNum
                    } else 0
                }
                node = nodes.oGet(nodeNum)
                res = bounds.PlaneSide(planeList.oGet(node.planeNum))
                if (res == Plane.PLANESIDE_BACK) {
                    nodeNum = node.children[1]
                } else if (res == Plane.PLANESIDE_FRONT) {
                    nodeNum = node.children[0]
                } else {
                    nodeNum = BoundsReachableAreaNum_r(node.children[1], bounds, areaFlags, excludeTravelFlags)
                    if (nodeNum != 0) {
                        return nodeNum
                    }
                    nodeNum = node.children[0]
                }
            }
            return 0
        }

        private fun MaxTreeDepth_r(nodeNum: Int, depth: CInt?, maxDepth: CInt?) {
            var maxDepth = maxDepth
            val node: aasNode_s?
            if (nodeNum <= 0) {
                return
            }
            depth.setVal(depth.getVal() + 1)
            if (depth.getVal() > maxDepth.getVal()) {
                maxDepth = depth
            }
            node = nodes.oGet(nodeNum)
            MaxTreeDepth_r(node.children[0], depth, maxDepth)
            MaxTreeDepth_r(node.children[1], depth, maxDepth)
            depth.setVal(depth.getVal() - 1)
        }

        private fun MaxTreeDepth(): Int {
            val depth = CInt(0)
            val maxDepth = CInt(0)

//	depth = maxDepth = 0;
            MaxTreeDepth_r(1, depth, maxDepth)
            return maxDepth.getVal()
        }

        private fun AreaContentsTravelFlags(areaNum: Int): Int {
            return if (areas.oGet(areaNum).contents and AASFile.AREACONTENTS_WATER != 0) {
                AASFile.TFL_WATER
            } else AASFile.TFL_AIR
        }

        private fun AreaReachableGoal(areaNum: Int): idVec3? {
            var i: Int
            var faceNum: Int
            var numFaces: Int
            val area: aasArea_s?
            val center = idVec3()
            val start = idVec3()
            val end = idVec3()
            val trace = aasTrace_s()
            area = areas.oGet(areaNum)
            if (0 == area.flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) || area.flags and AASFile.AREA_LIQUID != 0) {
                return AreaCenter(areaNum)
            }
            center.oSet(Vector.getVec3_origin())
            numFaces = 0
            i = 0
            while (i < area.numFaces) {
                faceNum = faceIndex.oGet(area.firstFace + i)
                if (0 == faces.oGet(Math.abs(faceNum)).flags and AASFile.FACE_FLOOR) {
                    i++
                    continue
                }
                center.plusAssign(FaceCenter(Math.abs(faceNum)))
                numFaces++
                i++
            }
            if (numFaces > 0) {
                center.divAssign(numFaces.toFloat())
            }
            center.plusAssign(2, 1.0f)
            end.oSet(center)
            end.minusAssign(2, 1024f)
            Trace(trace, center, end)
            return trace.endpos
        }

        private fun NumReachabilities(): Int {
            var i: Int
            var num: Int
            var reach: idReachability?
            num = 0
            i = 0
            while (i < areas.Num()) {
                reach = areas.oGet(i).reach
                while (reach != null) {
                    num++
                    reach = reach.next
                }
                i++
            }
            return num
        }

        // friend class idAASBuild;
        // friend class idAASReach;
        // friend class idAASCluster;
        init {
            planeList.SetGranularity(AASFile_local.AAS_PLANE_GRANULARITY)
            vertices.SetGranularity(AASFile_local.AAS_VERTEX_GRANULARITY)
            edges.SetGranularity(AASFile_local.AAS_EDGE_GRANULARITY)
            edgeIndex.SetGranularity(AASFile_local.AAS_INDEX_GRANULARITY)
            faces.SetGranularity(AASFile_local.AAS_LIST_GRANULARITY)
            faceIndex.SetGranularity(AASFile_local.AAS_INDEX_GRANULARITY)
            areas.SetGranularity(AASFile_local.AAS_LIST_GRANULARITY)
            nodes.SetGranularity(AASFile_local.AAS_LIST_GRANULARITY)
            portals.SetGranularity(AASFile_local.AAS_LIST_GRANULARITY)
            portalIndex.SetGranularity(AASFile_local.AAS_INDEX_GRANULARITY)
            clusters.SetGranularity(AASFile_local.AAS_LIST_GRANULARITY)
        }
    }

    class aasTraceStack_s {
        val end: idVec3?
        var nodeNum = 0
        var planeNum = 0
        val start: idVec3?

        init {
            start = idVec3()
            end = idVec3()
        }
    }
}