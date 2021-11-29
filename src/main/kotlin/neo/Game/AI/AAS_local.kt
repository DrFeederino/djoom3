package neo.Game.AI

import neo.Game.AI.AAS.aasGoal_s
import neo.Game.AI.AAS.aasObstacle_s
import neo.Game.AI.AAS.aasPath_s
import neo.Game.AI.AAS.idAAS
import neo.Game.AI.AAS.idAASCallback
import neo.Game.AI.AAS_routing.idRoutingCache
import neo.Game.AI.AAS_routing.idRoutingObstacle
import neo.Game.AI.AAS_routing.idRoutingUpdate
import neo.Game.AI.AI.idAASFindCover
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Player.idPlayer
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasCluster_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasNode_s
import neo.Tools.Compilers.AAS.AASFile.aasPortal_s
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Tools.Compilers.AAS.AASFile.idAASFile
import neo.Tools.Compilers.AAS.AASFile.idAASSettings
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Tools.Compilers.AAS.AASFile.idReachability_Walk
import neo.Tools.Compilers.AAS.AASFileManager
import neo.framework.Common
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.*
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.IntBuffer
import java.util.stream.Stream

/**
 *
 */
class AAS_local {
    internal class idAASLocal : idAAS() {
        private val obstacleList // list with obstacles
                : idList<idRoutingObstacle?>?

        //
        // routing data
        private var areaCacheIndex // for each area in each cluster the travel times to all other areas in the cluster
                : Array<Array<idRoutingCache?>?>?
        private var areaCacheIndexSize // number of area cache entries
                = 0
        private var areaTravelTimes // travel times through the areas
                : IntArray?
        private var areaUpdate // memory used to update the area routing cache
                : Array<idRoutingUpdate?>?
        private var cacheListEnd // end of list with cache sorted from oldest to newest
                : idRoutingCache? = null
        private var cacheListStart // start of list with cache sorted from oldest to newest
                : idRoutingCache? = null
        private var file: idAASFile? = null
        private var goalAreaTravelTimes // travel times to goal areas
                : IntArray?
        private val name: idStr? = null
        private var numAreaTravelTimes // number of area travel times
                = 0
        private var portalCacheIndex // for each area in the world the travel times from each portal
                : Array<idRoutingCache?>?
        private var portalCacheIndexSize // number of portal cache entries
                = 0

        //
        //
        private var portalUpdate // memory used to update the portal routing cache
                : Array<idRoutingUpdate?>?

        // virtual						~idAASLocal();
        private var totalCacheMemory // total cache memory used
                = 0

        override fun Init(
            mapName: idStr?,  /*unsigned int*/
            mapFileCRC: Long
        ): Boolean {
            if (file != null && mapName.Icmp(file.GetName()) == 0 && mapFileCRC == file.GetCRC()) {
                Common.common.Printf("Keeping %s\n", file.GetName())
                RemoveAllObstacles()
            } else {
                Shutdown()
                file = AASFileManager.AASFileManager.LoadAAS(mapName.toString(), mapFileCRC)
                if (TempDump.NOT(file)) {
                    Common.common.DWarning("Couldn't load AAS file: '%s'", mapName.toString())
                    return false
                }
                SetupRouting()
            }
            return true
        }

        fun Shutdown() {
            if (file != null) {
                ShutdownRouting()
                RemoveAllObstacles()
                AASFileManager.AASFileManager.FreeAAS(file)
                file = null
            }
        }

        override fun Stats() {
            if (TempDump.NOT(file)) {
                return
            }
            Common.common.Printf("[%s]\n", file.GetName())
            file.PrintInfo()
            RoutingStats()
        }

        override fun Test(origin: idVec3?) {
            if (TempDump.NOT(file)) {
                return
            }
            if (SysCvar.aas_randomPullPlayer.GetBool()) {
                RandomPullPlayer(origin)
            }
            if (SysCvar.aas_pullPlayer.GetInteger() > 0 && SysCvar.aas_pullPlayer.GetInteger() < file.GetNumAreas()) {
                ShowWalkPath(
                    origin,
                    SysCvar.aas_pullPlayer.GetInteger(),
                    AreaCenter(SysCvar.aas_pullPlayer.GetInteger())
                )
                PullPlayer(origin, SysCvar.aas_pullPlayer.GetInteger())
            }
            if (SysCvar.aas_showPath.GetInteger() > 0 && SysCvar.aas_showPath.GetInteger() < file.GetNumAreas()) {
                ShowWalkPath(origin, SysCvar.aas_showPath.GetInteger(), AreaCenter(SysCvar.aas_showPath.GetInteger()))
            }
            if (SysCvar.aas_showFlyPath.GetInteger() > 0 && SysCvar.aas_showFlyPath.GetInteger() < file.GetNumAreas()) {
                ShowFlyPath(
                    origin,
                    SysCvar.aas_showFlyPath.GetInteger(),
                    AreaCenter(SysCvar.aas_showFlyPath.GetInteger())
                )
            }
            if (SysCvar.aas_showHideArea.GetInteger() > 0 && SysCvar.aas_showHideArea.GetInteger() < file.GetNumAreas()) {
                ShowHideArea(origin, SysCvar.aas_showHideArea.GetInteger())
            }
            if (SysCvar.aas_showAreas.GetBool()) {
                ShowArea(origin)
            }
            if (SysCvar.aas_showWallEdges.GetBool()) {
                ShowWallEdges(origin)
            }
            if (SysCvar.aas_showPushIntoArea.GetBool()) {
                ShowPushIntoArea(origin)
            }
        }

        override fun GetSettings(): idAASSettings? {
            return if (TempDump.NOT(file)) {
                null
            } else file.GetSettings()
        }

        override fun PointAreaNum(origin: idVec3?): Int {
            return if (TempDump.NOT(file)) {
                0
            } else file.PointAreaNum(origin)
        }

        override fun PointReachableAreaNum(origin: idVec3?, searchBounds: idBounds?, areaFlags: Int): Int {
            return if (TempDump.NOT(file)) {
                0
            } else file.PointReachableAreaNum(origin, searchBounds, areaFlags, AASFile.TFL_INVALID)
        }

        override fun BoundsReachableAreaNum(bounds: idBounds?, areaFlags: Int): Int {
            return if (TempDump.NOT(file)) {
                0
            } else file.BoundsReachableAreaNum(bounds, areaFlags, AASFile.TFL_INVALID)
        }

        override fun PushPointIntoAreaNum(areaNum: Int, origin: idVec3?) {
            if (TempDump.NOT(file)) {
                return
            }
            file.PushPointIntoAreaNum(areaNum, origin)
        }

        override fun AreaCenter(areaNum: Int): idVec3? {
            return if (TempDump.NOT(file)) {
                Vector.getVec3_origin()
            } else file.GetArea(areaNum).center
        }

        override fun AreaFlags(areaNum: Int): Int {
            return if (TempDump.NOT(file)) {
                0
            } else file.GetArea(areaNum).flags
        }

        override fun AreaTravelFlags(areaNum: Int): Int {
            return if (TempDump.NOT(file)) {
                0
            } else file.GetArea(areaNum).travelFlags
        }

        override fun Trace(trace: aasTrace_s?, start: idVec3?, end: idVec3?): Boolean {
            if (TempDump.NOT(file)) {
                trace.numAreas = 0
                trace.lastAreaNum = trace.numAreas
                trace.fraction = trace.lastAreaNum.toFloat()
                return true
            }
            return file.Trace(trace, start, end)
        }

        override fun GetPlane(planeNum: Int): idPlane? {
            return if (TempDump.NOT(file)) {
                dummy
            } else file.GetPlane(planeNum)
        }

        override fun GetWallEdges(
            areaNum: Int,
            bounds: idBounds?,
            travelFlags: Int,
            edges: IntArray?,
            maxEdges: Int
        ): Int {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            var face1Num: Int
            var face2Num: Int
            var edge1Num: Int
            var edge2Num: Int
            val numEdges: Int
            var absEdge1Num: Int
            var curArea: Int
            var queueStart: Int
            val queueEnd: Int
            val areaQueue: IntArray
            val areasVisited: ByteArray
            var area: aasArea_s?
            var face1: aasFace_s?
            var face2: aasFace_s?
            var reach: idReachability?
            if (TempDump.NOT(file)) {
                return 0
            }
            numEdges = 0
            areasVisited =
                ByteArray(file.GetNumAreas()) //	memset( areasVisited, 0, file.GetNumAreas() * sizeof( byte ) );
            areaQueue = IntArray(file.GetNumAreas())
            queueStart = -1
            queueEnd = 0
            areaQueue[0] = areaNum
            areasVisited[areaNum] = 1 //true;
            curArea = areaNum
            while (queueStart < queueEnd) {
                area = file.GetArea(curArea)
                i = 0
                while (i < area.numFaces) {
                    face1Num = file.GetFaceIndex(area.firstFace + i)
                    face1 = file.GetFace(Math.abs(face1Num))
                    if (0 == face1.flags and AASFile.FACE_FLOOR) {
                        i++
                        continue
                    }
                    j = 0
                    while (j < face1.numEdges) {
                        edge1Num = file.GetEdgeIndex(face1.firstEdge + j)
                        absEdge1Num = Math.abs(edge1Num)

                        // test if the edge is shared by another floor face of this area
                        k = 0
                        while (k < area.numFaces) {
                            if (k == i) {
                                k++
                                continue
                            }
                            face2Num = file.GetFaceIndex(area.firstFace + k)
                            face2 = file.GetFace(Math.abs(face2Num))
                            if (0 == face2.flags and AASFile.FACE_FLOOR) {
                                k++
                                continue
                            }
                            l = 0
                            while (l < face2.numEdges) {
                                edge2Num = Math.abs(file.GetEdgeIndex(face2.firstEdge + l))
                                if (edge2Num == absEdge1Num) {
                                    break
                                }
                                l++
                            }
                            if (l < face2.numEdges) {
                                break
                            }
                            k++
                        }
                        if (k < area.numFaces) {
                            j++
                            continue
                        }

                        // test if the edge is used by a reachability
                        reach = area.reach
                        while (reach != null) {
                            if (reach.travelType and travelFlags != 0) {
                                if (reach.edgeNum == absEdge1Num) {
                                    break
                                }
                            }
                            reach = reach.next
                        }
                        if (reach != null) {
                            j++
                            continue
                        }

                        // test if the edge is already in the list
                        k = 0
                        while (k < numEdges) {
                            if (edge1Num == edges.get(k)) {
                                break
                            }
                            k++
                        }
                        if (k < numEdges) {
                            j++
                            continue
                        }

                        // add the edge to the list
                        edges.get(numEdges++) = edge1Num
                        if (numEdges >= maxEdges) {
                            return numEdges
                        }
                        j++
                    }
                    i++
                }

                // add new areas to the queue
                reach = area.reach
                while (reach != null) {
                    if (reach.travelType and travelFlags != 0) {
                        // if the area the reachability leads to hasn't been visited yet and the area bounds touch the search bounds
                        if (0 == areasVisited[reach.toAreaNum.toInt()] && bounds.IntersectsBounds(file.GetArea(reach.toAreaNum.toInt()).bounds)) {
                            areaQueue[queueEnd++] = reach.toAreaNum
                            areasVisited[reach.toAreaNum.toInt()] = 1 //true;
                        }
                    }
                    reach = reach.next
                }
                curArea = areaQueue[++queueStart]
            }
            return numEdges
        }

        override fun SortWallEdges(edges: IntArray?, numEdges: Int) {
            var i: Int
            var j: Int
            var k: Int
            var numSequences: Int
            val sequenceFirst: Array<AI_pathing.wallEdge_s?>
            val sequenceLast: Array<AI_pathing.wallEdge_s?>
            val wallEdges: Array<AI_pathing.wallEdge_s?>
            var wallEdge: AI_pathing.wallEdge_s?
            wallEdges = arrayOfNulls<AI_pathing.wallEdge_s?>(numEdges)
            sequenceFirst = arrayOfNulls<AI_pathing.wallEdge_s?>(numEdges)
            sequenceLast = arrayOfNulls<AI_pathing.wallEdge_s?>(numEdges)
            i = 0
            while (i < numEdges) {
                wallEdges[i] = AI_pathing.wallEdge_s()
                wallEdges[i].edgeNum = edges.get(i)
                GetEdgeVertexNumbers(edges.get(i), wallEdges[i].verts)
                wallEdges[i].next = null
                sequenceFirst[i] = wallEdges[i]
                sequenceLast[i] = wallEdges[i]
                i++
            }
            numSequences = numEdges
            i = 0
            while (i < numSequences) {
                j = i + 1
                while (j < numSequences) {
                    if (sequenceFirst[i].verts[0] == sequenceLast[j].verts[1]) {
                        sequenceLast[j].next = sequenceFirst[i]
                        sequenceFirst[i] = sequenceFirst[j]
                        break
                    }
                    if (sequenceLast[i].verts[1] == sequenceFirst[j].verts[0]) {
                        sequenceLast[i].next = sequenceFirst[j]
                        break
                    }
                    j++
                }
                if (j < numSequences) {
                    numSequences--
                    k = j
                    while (k < numSequences) {
                        sequenceFirst[k] = sequenceFirst[k + 1]
                        sequenceLast[k] = sequenceLast[k + 1]
                        k++
                    }
                    i = -1
                }
                i++
            }
            k = 0
            i = 0
            while (i < numSequences) {
                wallEdge = sequenceFirst[i]
                while (wallEdge != null) {
                    edges.get(k++) = wallEdge.edgeNum
                    wallEdge = wallEdge.next
                }
                i++
            }
        }

        override fun GetEdgeVertexNumbers(edgeNum: Int, verts: IntArray? /*[2]*/) {
            if (TempDump.NOT(file)) {
                verts.get(1) = 0
                verts.get(0) = verts.get(1)
                return
            }
            val v = file.GetEdge(Math.abs(edgeNum)).vertexNum
            verts.get(0) = v[Math_h.INTSIGNBITSET(edgeNum)]
            verts.get(1) = v[Math_h.INTSIGNBITNOTSET(edgeNum)]
        }

        override fun GetEdge(edgeNum: Int, start: idVec3?, end: idVec3?) {
            if (TempDump.NOT(file)) {
                start.Zero()
                end.Zero()
                return
            }
            val v = file.GetEdge(Math.abs(edgeNum)).vertexNum
            start.oSet(file.GetVertex(v[Math_h.INTSIGNBITSET(edgeNum)]))
            end.oSet(file.GetVertex(v[Math_h.INTSIGNBITNOTSET(edgeNum)]))
        }

        override fun SetAreaState(bounds: idBounds?, areaContents: Int, disabled: Boolean): Boolean {
            val expBounds = idBounds()
            if (TempDump.NOT(file)) {
                return false
            }
            expBounds.oSet(0, bounds.oGet(0).oMinus(file.GetSettings().boundingBoxes[0].oGet(1)))
            expBounds.oSet(1, bounds.oGet(1).oMinus(file.GetSettings().boundingBoxes[0].oGet(0)))

            // find all areas within or touching the bounds with the given contents and disable/enable them for routing
            return SetAreaState_r(1, expBounds, areaContents, disabled)
        }

        override fun  /*aasHandle_t*/AddObstacle(bounds: idBounds?): Int {
            val obstacle: idRoutingObstacle
            if (TempDump.NOT(file)) {
                return -1
            }
            obstacle = idRoutingObstacle()
            obstacle.bounds.oSet(0, bounds.oGet(0).oMinus(file.GetSettings().boundingBoxes[0].oGet(1)))
            obstacle.bounds.oSet(1, bounds.oGet(1).oMinus(file.GetSettings().boundingBoxes[0].oGet(0)))
            GetBoundsAreas_r(1, obstacle.bounds, obstacle.areas)
            SetObstacleState(obstacle, true)
            obstacleList.Append(obstacle)
            return obstacleList.Num() - 1
        }

        override fun RemoveObstacle(   /*aasHandle_t*/handle: Int) {
            if (TempDump.NOT(file)) {
                return
            }
            if (handle >= 0 && handle < obstacleList.Num()) {
                SetObstacleState(obstacleList.oGet(handle), false)

//		delete obstacleList[handle];
                obstacleList.RemoveIndex(handle)
            }
        }

        override fun RemoveAllObstacles() {
            var i: Int
            if (TempDump.NOT(file)) {
                return
            }
            i = 0
            while (i < obstacleList.Num()) {
                SetObstacleState(obstacleList.oGet(i), false)
                i++
            }
            obstacleList.Clear()
        }

        override fun TravelTimeToGoalArea(areaNum: Int, origin: idVec3?, goalAreaNum: Int, travelFlags: Int): Int {
            val travelTime = CInt()
            val reach = arrayOf<idReachability?>(null)
            if (TempDump.NOT(file)) {
                return 0
            }
            return if (!RouteToGoalArea(areaNum, origin, goalAreaNum, travelFlags, travelTime, reach)) {
                0
            } else travelTime.getVal()
        }

        override fun RouteToGoalArea(
            areaNum: Int,
            origin: idVec3?,
            goalAreaNum: Int,
            travelFlags: Int,
            travelTime: CInt?,
            reach: Array<idReachability?>?
        ): Boolean {
            var clusterNum: Int
            var goalClusterNum: Int
            var portalNum: Int
            var i: Int
            var clusterAreaNum: Int
            /*unsigned short*/
            var t: Int
            var bestTime: Int
            var portal: aasPortal_s?
            val cluster: aasCluster_s?
            var areaCache: idRoutingCache?
            val portalCache: idRoutingCache?
            var clusterCache: idRoutingCache?
            var bestReach: idReachability?
            var r: idReachability?
            var nextr: idReachability?
            travelTime.setVal(0)
            reach.get(0) = null
            if (TempDump.NOT(file)) {
                return false
            }
            if (areaNum == goalAreaNum) {
                return true
            }
            if (areaNum <= 0 || areaNum >= file.GetNumAreas()) {
                Game_local.gameLocal.Printf("RouteToGoalArea: areaNum %d out of range\n", areaNum)
                return false
            }
            if (goalAreaNum <= 0 || goalAreaNum >= file.GetNumAreas()) {
                Game_local.gameLocal.Printf("RouteToGoalArea: goalAreaNum %d out of range\n", goalAreaNum)
                return false
            }
            while (totalCacheMemory > AAS_routing.MAX_ROUTING_CACHE_MEMORY) {
                DeleteOldestCache()
            }
            clusterNum = file.GetArea(areaNum).cluster.toInt()
            goalClusterNum = file.GetArea(goalAreaNum).cluster.toInt()

            // if the source area is a cluster portal, read directly from the portal cache
            if (clusterNum < 0) {
                // if the goal area is a portal
                if (goalClusterNum < 0) {
                    // just assume the goal area is part of the front cluster
                    portal = file.GetPortal(-goalClusterNum)
                    goalClusterNum = portal.clusters[0]
                }
                // get the portal routing cache
                portalCache = GetPortalRoutingCache(goalClusterNum, goalAreaNum, travelFlags)
                reach.get(0) = GetAreaReachability(areaNum, portalCache.reachabilities[-clusterNum])
                travelTime.setVal(
                    portalCache.travelTimes[-clusterNum] + AreaTravelTime(
                        areaNum,
                        origin,
                        reach.get(0).start
                    )
                )
                return true
            }
            bestTime = 0
            bestReach = null

            // check if the goal area is a portal of the source area cluster
            if (goalClusterNum < 0) {
                portal = file.GetPortal(-goalClusterNum)
                if (portal.clusters[0] == clusterNum || portal.clusters[1] == clusterNum) {
                    goalClusterNum = clusterNum
                }
            }

            // if both areas are in the same cluster
            if (clusterNum > 0 && goalClusterNum > 0 && clusterNum == goalClusterNum) {
                clusterCache = GetAreaRoutingCache(clusterNum, goalAreaNum, travelFlags)
                clusterAreaNum = ClusterAreaNum(clusterNum, areaNum)
                if (clusterCache.travelTimes[clusterAreaNum] != 0) {
                    bestReach = GetAreaReachability(areaNum, clusterCache.reachabilities[clusterAreaNum])
                    bestTime =
                        clusterCache.travelTimes[clusterAreaNum] + AreaTravelTime(areaNum, origin, bestReach.start)
                } else {
                    clusterCache = null
                }
            } else {
                clusterCache = null
            }
            clusterNum = file.GetArea(areaNum).cluster.toInt()
            goalClusterNum = file.GetArea(goalAreaNum).cluster.toInt()

            // if the goal area is a portal
            if (goalClusterNum < 0) {
                // just assume the goal area is part of the front cluster
                portal = file.GetPortal(-goalClusterNum)
                goalClusterNum = portal.clusters[0]
            }
            // get the portal routing cache
            portalCache = GetPortalRoutingCache(goalClusterNum, goalAreaNum, travelFlags)

            // the cluster the area is in
            cluster = file.GetCluster(clusterNum)
            // current area inside the current cluster
            clusterAreaNum = ClusterAreaNum(clusterNum, areaNum)
            // if the area is not a reachable area
            if (clusterAreaNum >= cluster.numReachableAreas) {
                return false
            }

            // find the portal of the source area cluster leading towards the goal area
            i = 0
            while (i < cluster.numPortals) {
                portalNum = file.GetPortalIndex(cluster.firstPortal + i)

                // if the goal area isn't reachable from the portal
                if (0 == portalCache.travelTimes[portalNum]) {
                    i++
                    continue
                }
                portal = file.GetPortal(portalNum)
                // get the cache of the portal area
                areaCache = GetAreaRoutingCache(clusterNum, portal.areaNum.toInt(), travelFlags)
                // if the portal is not reachable from this area
                if (0 == areaCache.travelTimes[clusterAreaNum]) {
                    i++
                    continue
                }
                r = GetAreaReachability(areaNum, areaCache.reachabilities[clusterAreaNum])
                if (clusterCache != null) {
                    // if the next reachability from the portal leads back into the cluster
                    nextr = GetAreaReachability(portal.areaNum.toInt(), portalCache.reachabilities[portalNum])
                    if (file.GetArea(nextr.toAreaNum.toInt()).cluster < 0 || file.GetArea(nextr.toAreaNum.toInt()).cluster.toInt() == clusterNum) {
                        i++
                        continue
                    }
                }

                // the total travel time is the travel time from the portal area to the goal area
                // plus the travel time from the source area towards the portal area
                t = portalCache.travelTimes[portalNum] + areaCache.travelTimes[clusterAreaNum]
                // NOTE:	Should add the exact travel time through the portal area.
                //			However we add the largest travel time through the portal area.
                //			We cannot directly calculate the exact travel time through the portal area
                //			because the reachability used to travel into the portal area is not known.
                t += portal.maxAreaTravelTime

                // if the time is better than the one already found
                if (0 == bestTime || t < bestTime) {
                    bestReach = r
                    bestTime = t
                }
                i++
            }
            if (TempDump.NOT(bestReach)) {
                return false
            }
            reach.get(0) = bestReach
            travelTime.setVal(bestTime)
            return true
        }

        /*
         ============
         idAASLocal::WalkPathToGoal

         FIXME: don't stop optimizing on first failure ?
         ============
         */
        override fun WalkPathToGoal(
            path: aasPath_s?,
            areaNum: Int,
            origin: idVec3?,
            goalAreaNum: Int,
            goalOrigin: idVec3?,
            travelFlags: Int
        ): Boolean {
            var i: Int
            var curAreaNum: Int
            var lastAreaIndex: Int
            val travelTime = CInt()
            val endAreaNum = CInt()
            val moveAreaNum = CInt()
            val lastAreas = IntArray(4)
            val reach = arrayOf<idReachability?>(null)
            val endPos = idVec3()
            path.type = AAS.PATHTYPE_WALK
            path.moveGoal.oSet(origin)
            path.moveAreaNum = areaNum
            path.secondaryGoal.oSet(origin)
            path.reachability = null
            if (file == null || areaNum == goalAreaNum) {
                path.moveGoal.oSet(goalOrigin)
                return true
            }
            lastAreas[3] = areaNum
            lastAreas[2] = lastAreas[3]
            lastAreas[1] = lastAreas[2]
            lastAreas[0] = lastAreas[1]
            lastAreaIndex = 0
            curAreaNum = areaNum
            i = 0
            while (i < AAS_pathing.maxWalkPathIterations) {
                if (!RouteToGoalArea(curAreaNum, path.moveGoal, goalAreaNum, travelFlags, travelTime, reach)) {
                    break
                }
                if (TempDump.NOT(reach[0])) {
                    return false
                }

                // no need to check through the first area
                if (areaNum != curAreaNum) {
                    // only optimize a limited distance ahead
                    if (reach[0].start.oMinus(origin).LengthSqr() > Math_h.Square(AAS_pathing.maxWalkPathDistance)) {
                        if (AAS_pathing.SUBSAMPLE_WALK_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleWalkPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    reach[0].start,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                    if (!WalkPathValid(areaNum, origin, 0, reach[0].start, travelFlags, endPos, endAreaNum)) {
                        if (AAS_pathing.SUBSAMPLE_WALK_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleWalkPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    reach[0].start,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                }
                path.moveGoal.oSet(reach[0].start)
                path.moveAreaNum = curAreaNum
                if (reach[0].travelType != AASFile.TFL_WALK) {
                    break
                }
                if (!WalkPathValid(areaNum, origin, 0, reach[0].end, travelFlags, endPos, endAreaNum)) {
                    return true
                }
                path.moveGoal.oSet(reach[0].end)
                path.moveAreaNum = reach[0].toAreaNum.toInt()
                if (reach[0].toAreaNum.toInt() == goalAreaNum) {
                    if (!WalkPathValid(areaNum, origin, 0, goalOrigin, travelFlags, endPos, endAreaNum)) {
                        if (AAS_pathing.SUBSAMPLE_WALK_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleWalkPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    goalOrigin,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                    path.moveGoal.oSet(goalOrigin)
                    path.moveAreaNum = goalAreaNum
                    return true
                }
                lastAreas[lastAreaIndex] = curAreaNum
                lastAreaIndex = lastAreaIndex + 1 and 3
                curAreaNum = reach[0].toAreaNum.toInt()
                if (curAreaNum == lastAreas[0] || curAreaNum == lastAreas[1] || curAreaNum == lastAreas[2] || curAreaNum == lastAreas[3]) {
                    Common.common.Warning(
                        "idAASLocal::WalkPathToGoal: local routing minimum going from area %d to area %d",
                        areaNum,
                        goalAreaNum
                    )
                    break
                }
                i++
            }
            if (TempDump.NOT(reach[0])) {
                return false
            }
            when (reach[0].travelType) {
                AASFile.TFL_WALKOFFLEDGE -> {
                    path.type = AAS.PATHTYPE_WALKOFFLEDGE
                    path.secondaryGoal.oSet(reach[0].end)
                    path.reachability = reach[0]
                }
                AASFile.TFL_BARRIERJUMP -> {
                    path.type = path.type or AAS.PATHTYPE_BARRIERJUMP
                    path.secondaryGoal.oSet(reach[0].end)
                    path.reachability = reach[0]
                }
                AASFile.TFL_JUMP -> {
                    path.type = path.type or AAS.PATHTYPE_JUMP
                    path.secondaryGoal.oSet(reach[0].end)
                    path.reachability = reach[0]
                }
                else -> {}
            }
            return true
        }

        /*
         ============
         idAASLocal::WalkPathValid

         returns true if one can walk in a straight line between origin and goalOrigin
         ============
         */
        override fun WalkPathValid(
            areaNum: Int,
            origin: idVec3?,
            goalAreaNum: Int,
            goalOrigin: idVec3?,
            travelFlags: Int,
            endPos: idVec3?,
            endAreaNum: CInt?
        ): Boolean {
            var curAreaNum: Int
            val lastAreaNum: Int
            var lastAreaIndex: Int
            val lastAreas = IntArray(4)
            val pathPlane = idPlane()
            val frontPlane = idPlane()
            val farPlane = idPlane()
            var reach: idReachability?
            var area: aasArea_s?
            val p = idVec3()
            val dir = idVec3()
            if (file == null) {
                endPos.oSet(goalOrigin)
                endAreaNum.setVal(0)
                return true
            }
            lastAreas[3] = areaNum
            lastAreas[2] = lastAreas[3]
            lastAreas[1] = lastAreas[2]
            lastAreas[0] = lastAreas[1]
            lastAreaIndex = 0
            pathPlane.SetNormal(goalOrigin.oMinus(origin).Cross(file.GetSettings().gravityDir))
            pathPlane.Normalize()
            pathPlane.FitThroughPoint(origin)
            frontPlane.SetNormal(goalOrigin.oMinus(origin))
            frontPlane.Normalize()
            frontPlane.FitThroughPoint(origin)
            farPlane.SetNormal(frontPlane.Normal())
            farPlane.FitThroughPoint(goalOrigin)
            curAreaNum = areaNum
            lastAreaNum = curAreaNum
            while (true) {

                // find the furthest floor face split point on the path
                if (!FloorEdgeSplitPoint(endPos, curAreaNum, pathPlane, frontPlane, false)) {
                    endPos.oSet(origin)
                }

                // if we found a point near or further than the goal we're done
                if (farPlane.Distance(endPos) > -0.5f) {
                    break
                }

                // if we reached the goal area we're done
                if (curAreaNum == goalAreaNum) {
                    break
                }
                frontPlane.SetDist(frontPlane.Normal().times(endPos))
                area = file.GetArea(curAreaNum)
                reach = area.reach
                while (reach != null) {
                    if (reach.travelType != AASFile.TFL_WALK) {
                        reach = reach.next
                        continue
                    }

                    // if the reachability goes back to a previous area
                    if (reach.toAreaNum == lastAreas[0] || reach.toAreaNum == lastAreas[1] || reach.toAreaNum == lastAreas[2] || reach.toAreaNum == lastAreas[3]) {
                        reach = reach.next
                        continue
                    }

                    // if undesired travel flags are required to travel through the area
                    if (file.GetArea(reach.toAreaNum.toInt()).travelFlags and travelFlags.inv() != 0) {
                        reach = reach.next
                        continue
                    }

                    // don't optimize through an area near a ledge
                    if (file.GetArea(reach.toAreaNum.toInt()).flags and AASFile.AREA_LEDGE != 0) {
                        reach = reach.next
                        continue
                    }

                    // find the closest floor face split point on the path
                    if (!FloorEdgeSplitPoint(p, reach.toAreaNum.toInt(), pathPlane, frontPlane, true)) {
                        reach = reach.next
                        continue
                    }

                    // direction parallel to gravity
                    dir.oSet(
                        file.GetSettings().gravityDir.times(endPos.times(file.GetSettings().gravityDir))
                            .oMinus(file.GetSettings().gravityDir.times(p.times(file.GetSettings().gravityDir)))
                    )
                    if (dir.LengthSqr() > Math_h.Square(file.GetSettings().maxStepHeight.getVal())) {
                        reach = reach.next
                        continue
                    }

                    // direction orthogonal to gravity
                    dir.oSet(endPos.oMinus(p.oMinus(dir)))
                    if (dir.LengthSqr() > Math_h.Square(0.2f)) {
                        reach = reach.next
                        continue
                    }
                    break
                    reach = reach.next
                }
                if (TempDump.NOT(reach)) {
                    return false
                }
                lastAreas[lastAreaIndex] = curAreaNum
                lastAreaIndex = lastAreaIndex + 1 and 3
                curAreaNum = reach.toAreaNum.toInt()
            }
            endAreaNum.setVal(curAreaNum)
            return true
        }

        /*
         ============
         idAASLocal::FlyPathToGoal

         FIXME: don't stop optimizing on first failure ?
         ============
         */
        override fun FlyPathToGoal(
            path: aasPath_s?,
            areaNum: Int,
            origin: idVec3?,
            goalAreaNum: Int,
            goalOrigin: idVec3?,
            travelFlags: Int
        ): Boolean {
            var i: Int
            var curAreaNum: Int
            var lastAreaIndex: Int
            val travelTime = CInt()
            val endAreaNum = CInt()
            val moveAreaNum = CInt()
            val lastAreas = IntArray(4)
            val reach = arrayOf<idReachability?>(null)
            val endPos = idVec3()
            path.type = AAS.PATHTYPE_WALK
            path.moveGoal.oSet(origin)
            path.moveAreaNum = areaNum
            path.secondaryGoal.oSet(origin)
            path.reachability = null
            if (file == null || areaNum == goalAreaNum) {
                path.moveGoal.oSet(goalOrigin)
                return true
            }
            lastAreas[3] = areaNum
            lastAreas[2] = lastAreas[3]
            lastAreas[1] = lastAreas[2]
            lastAreas[0] = lastAreas[1]
            lastAreaIndex = 0
            curAreaNum = areaNum
            i = 0
            while (i < AAS_pathing.maxFlyPathIterations) {
                if (!RouteToGoalArea(curAreaNum, path.moveGoal, goalAreaNum, travelFlags, travelTime, reach)) {
                    break
                }
                if (null == reach[0]) {
                    return false
                }

                // no need to check through the first area
                if (areaNum != curAreaNum) {
                    if (reach[0].start.oMinus(origin).LengthSqr() > Math_h.Square(AAS_pathing.maxFlyPathDistance)) {
                        if (AAS_pathing.SUBSAMPLE_FLY_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleFlyPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    reach[0].start,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                    if (!FlyPathValid(areaNum, origin, 0, reach[0].start, travelFlags, endPos, endAreaNum)) {
                        if (AAS_pathing.SUBSAMPLE_FLY_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleFlyPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    reach[0].start,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                }
                path.moveGoal.oSet(reach[0].start)
                path.moveAreaNum = curAreaNum
                if (!FlyPathValid(areaNum, origin, 0, reach[0].end, travelFlags, endPos, endAreaNum)) {
                    return true
                }
                path.moveGoal.oSet(reach[0].end)
                path.moveAreaNum = reach[0].toAreaNum.toInt()
                if (reach[0].toAreaNum.toInt() == goalAreaNum) {
                    if (!FlyPathValid(areaNum, origin, 0, goalOrigin, travelFlags, endPos, endAreaNum)) {
                        if (AAS_pathing.SUBSAMPLE_FLY_PATH != 0) {
                            path.moveGoal.oSet(
                                SubSampleFlyPath(
                                    areaNum,
                                    origin,
                                    path.moveGoal,
                                    goalOrigin,
                                    travelFlags,
                                    moveAreaNum
                                )
                            )
                            path.moveAreaNum = moveAreaNum.getVal()
                        }
                        return true
                    }
                    path.moveGoal.oSet(goalOrigin)
                    path.moveAreaNum = goalAreaNum
                    return true
                }
                lastAreas[lastAreaIndex] = curAreaNum
                lastAreaIndex = lastAreaIndex + 1 and 3
                curAreaNum = reach[0].toAreaNum.toInt()
                if (curAreaNum == lastAreas[0] || curAreaNum == lastAreas[1] || curAreaNum == lastAreas[2] || curAreaNum == lastAreas[3]) {
                    Common.common.Warning(
                        "idAASLocal::FlyPathToGoal: local routing minimum going from area %d to area %d",
                        areaNum,
                        goalAreaNum
                    )
                    break
                }
                i++
            }
            return null != reach[0]
        }

        /*
         ============
         idAASLocal::FlyPathValid

         returns true if one can fly in a straight line between origin and goalOrigin
         ============
         */
        override fun FlyPathValid(
            areaNum: Int,
            origin: idVec3?,
            goalAreaNum: Int,
            goalOrigin: idVec3?,
            travelFlags: Int,
            endPos: idVec3?,
            endAreaNum: CInt?
        ): Boolean {
            val trace = aasTrace_s()
            if (file == null) {
                endPos.oSet(goalOrigin)
                endAreaNum.setVal(0)
                return true
            }
            file.Trace(trace, origin, goalOrigin)
            endPos.oSet(trace.endpos)
            endAreaNum.setVal(trace.lastAreaNum)
            return trace.fraction >= 1.0f
        }

        override fun ShowWalkPath(origin: idVec3?, goalAreaNum: Int, goalOrigin: idVec3?) {
            var i: Int
            val areaNum: Int
            var curAreaNum: Int
            val travelTime = CInt()
            val reach = arrayOf<idReachability?>(null)
            val org = idVec3()
            val areaCenter = idVec3()
            val path = aasPath_s()
            if (TempDump.NOT(file)) {
                return
            }
            org.oSet(origin)
            areaNum = PointReachableAreaNum(org, DefaultSearchBounds(), AASFile.AREA_REACHABLE_WALK)
            PushPointIntoAreaNum(areaNum, org)
            curAreaNum = areaNum
            i = 0
            while (i < 100) {
                if (!RouteToGoalArea(
                        curAreaNum,
                        org,
                        goalAreaNum,
                        AASFile.TFL_WALK or AASFile.TFL_AIR,
                        travelTime,
                        reach
                    )
                ) {
                    break
                }
                if (TempDump.NOT(reach[0])) {
                    break
                }
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorGreen, org, reach[0].start, 2)
                DrawReachability(reach[0])
                if (reach[0].toAreaNum.toInt() == goalAreaNum) {
                    break
                }
                curAreaNum = reach[0].toAreaNum.toInt()
                org.oSet(reach[0].end)
                i++
            }
            if (WalkPathToGoal(path, areaNum, origin, goalAreaNum, goalOrigin, AASFile.TFL_WALK or AASFile.TFL_AIR)) {
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorBlue, origin, path.moveGoal, 2)
            }
        }

        override fun ShowFlyPath(origin: idVec3?, goalAreaNum: Int, goalOrigin: idVec3?) {
            var i: Int
            val areaNum: Int
            var curAreaNum: Int
            val travelTime = CInt()
            val reach = arrayOf<idReachability?>(null)
            val org = idVec3()
            val areaCenter = idVec3()
            val path = aasPath_s()
            if (TempDump.NOT(file)) {
                return
            }
            org.oSet(origin)
            areaNum = PointReachableAreaNum(org, DefaultSearchBounds(), AASFile.AREA_REACHABLE_FLY)
            PushPointIntoAreaNum(areaNum, org)
            curAreaNum = areaNum
            i = 0
            while (i < 100) {
                if (!RouteToGoalArea(
                        curAreaNum,
                        org,
                        goalAreaNum,
                        AASFile.TFL_WALK or AASFile.TFL_FLY or AASFile.TFL_AIR,
                        travelTime,
                        reach
                    )
                ) {
                    break
                }
                if (TempDump.NOT(reach[0])) {
                    break
                }
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorPurple, org, reach[0].start, 2)
                DrawReachability(reach[0])
                if (reach[0].toAreaNum.toInt() == goalAreaNum) {
                    break
                }
                curAreaNum = reach[0].toAreaNum.toInt()
                org.oSet(reach[0].end)
                i++
            }
            if (FlyPathToGoal(
                    path,
                    areaNum,
                    origin,
                    goalAreaNum,
                    goalOrigin,
                    AASFile.TFL_WALK or AASFile.TFL_FLY or AASFile.TFL_AIR
                )
            ) {
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorBlue, origin, path.moveGoal, 2)
            }
        }

        override fun FindNearestGoal(
            goal: aasGoal_s?,
            areaNum: Int,
            origin: idVec3?,
            target: idVec3?,
            travelFlags: Int,
            obstacles: Array<aasObstacle_s?>?,
            numObstacles: Int,
            callback: idAASCallback?
        ): Boolean {
            var i: Int
            var j: Int
            var k: Int
            val badTravelFlags: Int
            var nextAreaNum: Int
            var bestAreaNum: Int
            /*unsigned short*/
            var t: Int
            var bestTravelTime: Int
            var updateListStart: idRoutingUpdate?
            var updateListEnd: idRoutingUpdate?
            var curUpdate: idRoutingUpdate?
            var nextUpdate: idRoutingUpdate?
            var reach: idReachability?
            var nextArea: aasArea_s?
            val v1 = idVec3()
            val v2 = idVec3()
            val p = idVec3()
            val targetDist: Float
            var dist: Float
            if (file == null || areaNum <= 0) {
                goal.areaNum = areaNum
                goal.origin.oSet(origin)
                return false
            }

            // if the first area is valid goal, just return the origin
            if (callback.TestArea(this, areaNum)) {
                goal.areaNum = areaNum
                goal.origin.oSet(origin)
                return true
            }

            // setup obstacles
            k = 0
            while (k < numObstacles) {
                obstacles.get(k).expAbsBounds.oSet(
                    0,
                    obstacles.get(k).absBounds.oGet(0).oMinus(file.GetSettings().boundingBoxes[0].oGet(1))
                )
                obstacles.get(k).expAbsBounds.oSet(
                    1,
                    obstacles.get(k).absBounds.oGet(1).oMinus(file.GetSettings().boundingBoxes[0].oGet(0))
                )
                k++
            }
            badTravelFlags = travelFlags.inv()
            Simd.SIMDProcessor.Memset(goalAreaTravelTimes, 0, file.GetNumAreas() /*sizeof(unsigned short )*/)
            targetDist = target.oMinus(origin).Length()

            // initialize first update
            curUpdate = areaUpdate.get(areaNum)
            curUpdate.areaNum = areaNum
            curUpdate.tmpTravelTime = 0
            curUpdate.start.oSet(origin)
            curUpdate.next = null
            curUpdate.prev = null
            updateListStart = curUpdate
            updateListEnd = curUpdate
            bestTravelTime = 0
            bestAreaNum = 0

            // while there are updates in the list
            while (updateListStart != null) {
                curUpdate = updateListStart
                if (curUpdate.next != null) {
                    curUpdate.next.prev = null
                } else {
                    updateListEnd = null
                }
                updateListStart = curUpdate.next
                curUpdate.isInList = false

                // if we already found a closer location
                if (bestTravelTime != 0 && curUpdate.tmpTravelTime >= bestTravelTime) {
                    continue
                }
                i = 0
                reach = file.GetArea(curUpdate.areaNum).reach
                while (reach != null) {


                    // if the reachability uses an undesired travel type
                    if (reach.travelType and badTravelFlags != 0) {
                        reach = reach.next
                        i++
                        continue
                    }

                    // next area the reversed reachability leads to
                    nextAreaNum = reach.toAreaNum.toInt()
                    nextArea = file.GetArea(nextAreaNum)

                    // if traveling through the next area requires an undesired travel flag
                    if (nextArea.travelFlags and badTravelFlags != 0) {
                        reach = reach.next
                        i++
                        continue
                    }
                    t = (curUpdate.tmpTravelTime
                            + AreaTravelTime(curUpdate.areaNum, curUpdate.start, reach.start)
                            + reach.travelTime)

                    // project target origin onto movement vector through the area
                    v1.oSet(reach.end.oMinus(curUpdate.start))
                    v1.Normalize()
                    v2.oSet(target.oMinus(curUpdate.start))
                    p.oSet(curUpdate.start.oPlus(v1.times(v2.times(v1))))

                    // get the point on the path closest to the target
                    j = 0
                    while (j < 3) {
                        if (p.oGet(j) > curUpdate.start.oGet(j) + 0.1f && p.oGet(j) > reach.end.oGet(j) + 0.1f
                            || p.oGet(j) < curUpdate.start.oGet(j) - 0.1f && p.oGet(j) < reach.end.oGet(j) - 0.1f
                        ) {
                            break
                        }
                        j++
                    }
                    dist = if (j >= 3) {
                        target.oMinus(p).Length()
                    } else {
                        target.oMinus(reach.end).Length()
                    }

                    // avoid moving closer to the target
                    if (dist < targetDist) {
                        t += ((targetDist - dist) * 10).toInt()
                    }

                    // if we already found a closer location
                    if (bestTravelTime != 0 && t >= bestTravelTime) {
                        reach = reach.next
                        i++
                        continue
                    }

                    // if this is not the best path towards the next area
                    if (goalAreaTravelTimes.get(nextAreaNum) != 0 && t >= goalAreaTravelTimes.get(nextAreaNum)) {
                        reach = reach.next
                        i++
                        continue
                    }

                    // path may not go through any obstacles
                    k = 0
                    while (k < numObstacles) {

                        // if the movement vector intersects the expanded obstacle bounds
                        if (obstacles.get(k).expAbsBounds.LineIntersection(curUpdate.start, reach.end)) {
                            break
                        }
                        k++
                    }
                    if (k < numObstacles) {
                        reach = reach.next
                        i++
                        continue
                    }
                    goalAreaTravelTimes.get(nextAreaNum) = t
                    nextUpdate = areaUpdate.get(nextAreaNum)
                    nextUpdate.areaNum = nextAreaNum
                    nextUpdate.tmpTravelTime = t
                    nextUpdate.start.oSet(reach.end)

                    // if we are not allowed to fly
                    if (badTravelFlags and AASFile.TFL_FLY != 0) {
                        // avoid areas near ledges
                        if (file.GetArea(nextAreaNum).flags and AASFile.AREA_LEDGE != 0) {
                            nextUpdate.tmpTravelTime += AAS_routing.LEDGE_TRAVELTIME_PANALTY
                        }
                    }
                    if (!nextUpdate.isInList) {
                        nextUpdate.next = null
                        nextUpdate.prev = updateListEnd
                        if (updateListEnd != null) {
                            updateListEnd.next = nextUpdate
                        } else {
                            updateListStart = nextUpdate
                        }
                        updateListEnd = nextUpdate
                        nextUpdate.isInList = true
                    }

                    // don't put goal near a ledge
                    if (0 == nextArea.flags and AASFile.AREA_LEDGE) {

                        // add travel time through the area
                        t += AreaTravelTime(reach.toAreaNum.toInt(), reach.end, nextArea.center)
                        if (0 == bestTravelTime || t < bestTravelTime) {
                            // if the area is not visible to the target
                            if (callback.TestArea(this, reach.toAreaNum.toInt())) {
                                bestTravelTime = t
                                bestAreaNum = reach.toAreaNum.toInt()
                            }
                        }
                    }
                    reach = reach.next
                    i++
                }
            }
            if (bestAreaNum != 0) {
                goal.areaNum = bestAreaNum
                goal.origin.oSet(AreaCenter(bestAreaNum))
                return true
            }
            return false
        }

        // routing
        private fun SetupRouting(): Boolean {
            CalculateAreaTravelTimes()
            SetupRoutingCache()
            return true
        }

        private fun ShutdownRouting() {
            DeleteAreaTravelTimes()
            ShutdownRoutingCache()
        }

        private /*unsigned short*/   fun AreaTravelTime(areaNum: Int, start: idVec3?, end: idVec3?): Int {
            var dist: Float
            dist = end.oMinus(start).Length()
            dist *= if (file.GetArea(areaNum).travelFlags and AASFile.TFL_CROUCH != 0) {
                100.0f / 100.0f
            } else if (file.GetArea(areaNum).travelFlags and AASFile.TFL_WATER != 0) {
                100.0f / 150.0f
            } else {
                100.0f / 300.0f
            }
            return if (dist < 1.0f) {
                1
            } else idMath.FtoiFast(dist)
        }

        private fun CalculateAreaTravelTimes() {
            var n: Int
            var i: Int
            var j: Int
            var numReach: Int
            var numRevReach: Int
            var t: Int
            var maxt: Int
            var bytePtr: Int
            var reach: idReachability?
            var rev_reach: idReachability?

            // get total memory for all area travel times
            numAreaTravelTimes = 0
            n = 0
            while (n < file.GetNumAreas()) {
                if (TempDump.NOT((file.GetArea(n).flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)).toDouble())) {
                    n++
                    continue
                }
                numReach = 0
                reach = file.GetArea(n).reach
                while (reach != null) {
                    numReach++
                    reach = reach.next
                }
                numRevReach = 0
                rev_reach = file.GetArea(n).rev_reach
                while (rev_reach != null) {
                    numRevReach++
                    rev_reach = rev_reach.rev_next
                }
                numAreaTravelTimes += numReach * numRevReach
                n++
            }
            areaTravelTimes =
                IntArray(numAreaTravelTimes) // Mem_Alloc(numAreaTravelTimes /* sizeof(unsigned short )*/);
            bytePtr = 0 //(byte *) areaTravelTimes;
            n = 0
            while (n < file.GetNumAreas()) {
                if (TempDump.NOT((file.GetArea(n).flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY)).toDouble())) {
                    n++
                    continue
                }

                // for each reachability that starts in this area calculate the travel time
                // towards all the reachabilities that lead towards this area
                maxt = 0.also { i = it }
                reach = file.GetArea(n).reach
                while (reach != null) {
                    assert(i < AASFile.MAX_REACH_PER_AREA)
                    if (i >= AASFile.MAX_REACH_PER_AREA) {
                        idGameLocal.Companion.Error("i >= MAX_REACH_PER_AREA")
                    }
                    reach.number = i.toByte()
                    reach.disableCount = 0
                    reach.areaTravelTimes = IntBuffer.wrap(areaTravelTimes).position(bytePtr).slice()
                    j = 0
                    rev_reach = file.GetArea(n).rev_reach
                    while (rev_reach != null) {
                        t = AreaTravelTime(n, reach.start, rev_reach.end)
                        reach.areaTravelTimes.put(j, t)
                        if (t > maxt) {
                            maxt = t
                        }
                        rev_reach = rev_reach.rev_next
                        j++
                    }
                    bytePtr += j // * sizeof( unsigned short );//TODO:double check the increment size.
                    reach = reach.next
                    i++
                }

                // if this area is a portal
                if (file.GetArea(n).cluster < 0) {
                    // set the maximum travel time through this portal
                    file.SetPortalMaxTravelTime(-file.GetArea(n).cluster.toInt(), maxt)
                }
                n++
            }

//	assert( ( (unsigned int) bytePtr - (unsigned int) areaTravelTimes ) <= numAreaTravelTimes * sizeof( unsigned short ) );
        }

        private fun DeleteAreaTravelTimes() {
//            Mem_Free(areaTravelTimes);
            areaTravelTimes = null
            numAreaTravelTimes = 0
        }

        private fun SetupRoutingCache() {
            var i: Int
            var bytePtr: Int
            areaCacheIndexSize = 0
            i = 0
            while (i < file.GetNumClusters()) {
                areaCacheIndexSize += file.GetCluster(i).numReachableAreas
                i++
            }
            areaCacheIndex =
                Array(file.GetNumClusters()) { arrayOfNulls<idRoutingCache?>(areaCacheIndexSize) } // Mem_ClearedAlloc(file.GetNumClusters() /* sizeof( idRoutingCache ** )*/ + areaCacheIndexSize /* sizeof( idRoutingCache *)*/);
            //	bytePtr = ((byte *)areaCacheIndex) + file.GetNumClusters() * sizeof( idRoutingCache ** );
//            bytePtr = file.GetNumClusters();
//            for (i = 0; i < file.GetNumClusters(); i++) {
//                areaCacheIndex[i] = new idRoutingCache[bytePtr];
//                bytePtr += file.GetCluster(i).numReachableAreas /* sizeof( idRoutingCache * )*/;
//            }
            portalCacheIndexSize = file.GetNumAreas()
            portalCacheIndex =
                arrayOfNulls<idRoutingCache?>(portalCacheIndexSize) // Mem_ClearedAlloc(portalCacheIndexSize /* sizeof( idRoutingCache * )*/);
            areaUpdate = Stream
                .generate { idRoutingUpdate() }
                .limit(file.GetNumAreas().toLong())
                .toArray { _Dummy_.__Array__() } // Mem_ClearedAlloc(file.GetNumAreas() /* sizeof( idRoutingUpdate )*/);
            portalUpdate =
                arrayOfNulls<idRoutingUpdate?>(file.GetNumPortals() + 1) // Mem_ClearedAlloc((file.GetNumPortals() + 1) /* sizeof( idRoutingUpdate )*/);
            goalAreaTravelTimes =
                IntArray(file.GetNumAreas()) // Mem_ClearedAlloc(file.GetNumAreas() /* sizeof( unsigned short )*/);
            cacheListEnd = null
            cacheListStart = cacheListEnd
            totalCacheMemory = 0
        }

        private fun DeleteClusterCache(clusterNum: Int) {
            var i: Int
            var cache: idRoutingCache?
            i = 0
            while (i < file.GetCluster(clusterNum).numReachableAreas) {
                cache = areaCacheIndex.get(clusterNum).get(i)
                while (cache != null) {
                    areaCacheIndex.get(clusterNum).get(i) = cache.next
                    UnlinkCache(cache)
                    cache = areaCacheIndex.get(clusterNum).get(i)
                }
                i++
            }
        }

        private fun DeletePortalCache() {
            var i: Int
            var cache: idRoutingCache?
            i = 0
            while (i < file.GetNumAreas()) {
                cache = portalCacheIndex.get(i)
                while (cache != null) {
                    portalCacheIndex.get(i) = cache.next
                    UnlinkCache(cache)
                    cache = portalCacheIndex.get(i)
                }
                i++
            }
        }

        private fun ShutdownRoutingCache() {
            var i: Int
            i = 0
            while (i < file.GetNumClusters()) {
                DeleteClusterCache(i)
                i++
            }
            DeletePortalCache()

//            Mem_Free(areaCacheIndex);
            areaCacheIndex = null
            areaCacheIndexSize = 0
            //            Mem_Free(portalCacheIndex);
            portalCacheIndex = null
            portalCacheIndexSize = 0
            //            Mem_Free(areaUpdate);
            areaUpdate = null
            //            Mem_Free(portalUpdate);
            portalUpdate = null
            //            Mem_Free(goalAreaTravelTimes);
            goalAreaTravelTimes = null
            cacheListEnd = null
            cacheListStart = cacheListEnd
            totalCacheMemory = 0
        }

        private fun RoutingStats() {
            var cache: idRoutingCache?
            var numAreaCache: Int
            var numPortalCache: Int
            var totalAreaCacheMemory: Int
            var totalPortalCacheMemory: Int
            numPortalCache = 0
            numAreaCache = numPortalCache
            totalPortalCacheMemory = 0
            totalAreaCacheMemory = totalPortalCacheMemory
            cache = cacheListStart
            while (cache != null) {
                if (cache.type == AAS_routing.CACHETYPE_AREA) {
                    numAreaCache++
                    //			totalAreaCacheMemory += sizeof( idRoutingCache ) + cache.size * (sizeof( unsigned short ) + sizeof( byte ));
                    totalAreaCacheMemory += cache.size
                } else {
                    numPortalCache++
                    //			totalPortalCacheMemory += sizeof( idRoutingCache ) + cache.size * (sizeof( unsigned short ) + sizeof( byte ));
                    totalPortalCacheMemory += cache.size
                }
                cache = cache.time_next
            }
            Game_local.gameLocal.Printf("%6d area cache (%d KB)\n", numAreaCache, totalAreaCacheMemory shr 10)
            Game_local.gameLocal.Printf("%6d portal cache (%d KB)\n", numPortalCache, totalPortalCacheMemory shr 10)
            Game_local.gameLocal.Printf(
                "%6d total cache (%d KB)\n",
                numAreaCache + numPortalCache,
                totalCacheMemory shr 10
            )
            Game_local.gameLocal.Printf(
                "%6d area travel times (%d KB)\n",
                numAreaTravelTimes,
                numAreaTravelTimes /* sizeof( unsigned short )*/ shr 10
            )
            Game_local.gameLocal.Printf(
                "%6d area cache entries (%d KB)\n",
                areaCacheIndexSize,
                areaCacheIndexSize /* sizeof( idRoutingCache * )*/ shr 10
            )
            Game_local.gameLocal.Printf(
                "%6d portal cache entries (%d KB)\n",
                portalCacheIndexSize,
                portalCacheIndexSize /* sizeof( idRoutingCache * )*/ shr 10
            )
        }

        /*
         ============
         idAASLocal::LinkCache

         link the cache in the cache list sorted from oldest to newest cache
         ============
         */
        private fun LinkCache(cache: idRoutingCache?) {

            // if the cache is already linked
            if (cache.time_next != null || cache.time_prev != null || cacheListStart === cache) {
                UnlinkCache(cache)
            }
            totalCacheMemory += cache.Size()

            // add cache to the end of the list
            cache.time_next = null
            cache.time_prev = cacheListEnd
            if (cacheListEnd != null) {
                cacheListEnd.time_next = cache
            }
            cacheListEnd = cache
            if (null == cacheListStart) {
                cacheListStart = cache
            }
        }

        private fun UnlinkCache(cache: idRoutingCache?) {
            totalCacheMemory -= cache.Size()

            // unlink the cache
            if (cache.time_next != null) {
                cache.time_next.time_prev = cache.time_prev
            } else {
                cacheListEnd = cache.time_prev
            }
            if (cache.time_prev != null) {
                cache.time_prev.time_next = cache.time_next
            } else {
                cacheListStart = cache.time_next
            }
            cache.time_prev = null
            cache.time_next = cache.time_prev
        }

        private fun DeleteOldestCache() {
            val cache: idRoutingCache?
            assert(cacheListStart != null)

            // unlink the oldest cache
            cache = cacheListStart
            UnlinkCache(cache)

            // unlink the oldest cache from the area or portal cache index
            if (cache.next != null) {
                cache.next.prev = cache.prev
            }
            if (cache.prev != null) {
                cache.prev.next = cache.next
            } else if (cache.type == AAS_routing.CACHETYPE_AREA) {
                areaCacheIndex.get(cache.cluster).get(ClusterAreaNum(cache.cluster, cache.areaNum)) = cache.next
            } else if (cache.type == AAS_routing.CACHETYPE_PORTAL) {
                portalCacheIndex.get(cache.areaNum) = cache.next
            }

//	delete cache;
        }

        private fun GetAreaReachability(areaNum: Int, reachabilityNum: Int): idReachability? {
            var reachabilityNum = reachabilityNum
            var reach: idReachability?
            reach = file.GetArea(areaNum).reach
            while (reach != null) {
                if (--reachabilityNum < 0) {
                    return reach
                }
                reach = reach.next
            }
            return null
        }

        private fun ClusterAreaNum(clusterNum: Int, areaNum: Int): Int {
            val side: Int
            val areaCluster: Int
            areaCluster = file.GetArea(areaNum).cluster.toInt()
            return if (areaCluster > 0) {
                file.GetArea(areaNum).clusterAreaNum
            } else {
                side = if (file.GetPortal(-areaCluster).clusters[0] != clusterNum) 1 else 0
                file.GetPortal(-areaCluster).clusterAreaNum[side]
            }
        }

        private fun UpdateAreaRoutingCache(areaCache: idRoutingCache?) {
            var i: Int
            var nextAreaNum: Int
            var cluster: Int
            val badTravelFlags: Int
            var clusterAreaNum: Int
            val numReachableAreas: Int
            var t: Int
            val startAreaTravelTimes = IntArray(AASFile.MAX_REACH_PER_AREA)
            var updateListStart: idRoutingUpdate?
            var updateListEnd: idRoutingUpdate?
            var curUpdate: idRoutingUpdate?
            var nextUpdate: idRoutingUpdate?
            var reach: idReachability?
            var nextArea: aasArea_s?

            // number of reachability areas within this cluster
            numReachableAreas = file.GetCluster(areaCache.cluster).numReachableAreas

            // number of the start area within the cluster
            clusterAreaNum = ClusterAreaNum(areaCache.cluster, areaCache.areaNum)
            if (clusterAreaNum >= numReachableAreas) {
                return
            }
            areaCache.travelTimes[clusterAreaNum] = areaCache.startTravelTime
            badTravelFlags = areaCache.travelFlags.inv()

            // initialize first update
            areaUpdate.get(clusterAreaNum) = idRoutingUpdate()
            curUpdate = areaUpdate.get(clusterAreaNum)
            curUpdate.areaNum = areaCache.areaNum
            curUpdate.areaTravelTimes = IntBuffer.wrap(startAreaTravelTimes)
            curUpdate.tmpTravelTime = areaCache.startTravelTime
            curUpdate.next = null
            curUpdate.prev = null
            updateListStart = curUpdate
            updateListEnd = curUpdate

            // while there are updates in the list
            while (updateListStart != null) {
                curUpdate = updateListStart
                if (curUpdate.next != null) {
                    curUpdate.next.prev = null
                } else {
                    updateListEnd = null
                }
                updateListStart = curUpdate.next
                curUpdate.isInList = false
                i = 0
                reach = file.GetArea(curUpdate.areaNum).rev_reach
                while (reach != null) {


                    // if the reachability uses an undesired travel type
                    if (reach.travelType and badTravelFlags != 0) {
                        reach = reach.rev_next
                        i++
                        continue
                    }

                    // next area the reversed reachability leads to
                    nextAreaNum = reach.fromAreaNum.toInt()
                    nextArea = file.GetArea(nextAreaNum)

                    // if traveling through the next area requires an undesired travel flag
                    if (nextArea.travelFlags and badTravelFlags != 0) {
                        reach = reach.rev_next
                        i++
                        continue
                    }

                    // get the cluster number of the area
                    cluster = nextArea.cluster.toInt()
                    // don't leave the cluster, however do flood into cluster portals
                    if (cluster > 0 && cluster != areaCache.cluster) {
                        reach = reach.rev_next
                        i++
                        continue
                    }

                    // get the number of the area in the cluster
                    clusterAreaNum = ClusterAreaNum(areaCache.cluster, nextAreaNum)
                    if (clusterAreaNum >= numReachableAreas) {
                        reach = reach.rev_next
                        i++
                        continue  // should never happen
                    }
                    assert(clusterAreaNum < areaCache.size)

                    // time already travelled plus the traveltime through the current area
                    // plus the travel time of the reachability towards the next area
                    t = curUpdate.tmpTravelTime + curUpdate.areaTravelTimes[i] + reach.travelTime
                    if (0 == areaCache.travelTimes[clusterAreaNum] || t < areaCache.travelTimes[clusterAreaNum]) {
                        areaCache.travelTimes[clusterAreaNum] = t
                        areaCache.reachabilities[clusterAreaNum] =
                            reach.number // reversed reachability used to get into this area
                        areaUpdate.get(clusterAreaNum) =
                            if (areaUpdate.get(clusterAreaNum) == null) idRoutingUpdate() else areaUpdate.get(
                                clusterAreaNum
                            )
                        nextUpdate = areaUpdate.get(clusterAreaNum)
                        nextUpdate.areaNum = nextAreaNum
                        nextUpdate.tmpTravelTime = t
                        nextUpdate.areaTravelTimes = reach.areaTravelTimes

                        // if we are not allowed to fly
                        if (badTravelFlags and AASFile.TFL_FLY != 0) {
                            // avoid areas near ledges
                            if (file.GetArea(nextAreaNum).flags and AASFile.AREA_LEDGE != 0) {
                                nextUpdate.tmpTravelTime += AAS_routing.LEDGE_TRAVELTIME_PANALTY
                            }
                        }
                        if (!nextUpdate.isInList) {
                            nextUpdate.next = null
                            nextUpdate.prev = updateListEnd
                            if (updateListEnd != null) {
                                updateListEnd.next = nextUpdate
                            } else {
                                updateListStart = nextUpdate
                            }
                            updateListEnd = nextUpdate
                            nextUpdate.isInList = true
                        }
                    }
                    reach = reach.rev_next
                    i++
                }
            }
        }

        private fun GetAreaRoutingCache(clusterNum: Int, areaNum: Int, travelFlags: Int): idRoutingCache? {
            val clusterAreaNum: Int
            var cache: idRoutingCache?
            val clusterCache: idRoutingCache?

            // number of the area in the cluster
            clusterAreaNum = ClusterAreaNum(clusterNum, areaNum)
            // pointer to the cache for the area in the cluster
            clusterCache = areaCacheIndex.get(clusterNum).get(clusterAreaNum)
            // check if cache without undesired travel flags already exists
            cache = clusterCache
            while (cache != null) {
                if (cache.travelFlags == travelFlags) {
                    break
                }
                cache = cache.next
            }
            // if no cache found
            if (null == cache) {
                cache = idRoutingCache(file.GetCluster(clusterNum).numReachableAreas)
                cache.type = AAS_routing.CACHETYPE_AREA
                cache.cluster = clusterNum
                cache.areaNum = areaNum
                cache.startTravelTime = 1
                cache.travelFlags = travelFlags
                cache.prev = null
                cache.next = clusterCache
                if (clusterCache != null) {
                    clusterCache.prev = cache
                }
                areaCacheIndex.get(clusterNum).get(clusterAreaNum) = cache
                UpdateAreaRoutingCache(cache)
            }
            LinkCache(cache)
            return cache
        }

        private fun UpdatePortalRoutingCache(portalCache: idRoutingCache?) {
            var i: Int
            var portalNum: Int
            var clusterAreaNum: Int
            var t: Int
            var portal: aasPortal_s?
            var cluster: aasCluster_s?
            var cache: idRoutingCache?
            var updateListStart: idRoutingUpdate?
            var updateListEnd: idRoutingUpdate?
            var curUpdate: idRoutingUpdate?
            var nextUpdate: idRoutingUpdate?
            portalUpdate.get(file.GetNumPortals()) = idRoutingUpdate()
            curUpdate = portalUpdate.get(file.GetNumPortals())
            curUpdate.cluster = portalCache.cluster
            curUpdate.areaNum = portalCache.areaNum
            curUpdate.tmpTravelTime = portalCache.startTravelTime

            //put the area to start with in the current read list
            curUpdate.next = null
            curUpdate.prev = null
            updateListStart = curUpdate
            updateListEnd = curUpdate

            // while there are updates in the current list
            while (updateListStart != null) {
                curUpdate = updateListStart
                // remove the current update from the list
                if (curUpdate.next != null) {
                    curUpdate.next.prev = null
                } else {
                    updateListEnd = null
                }
                updateListStart = curUpdate.next
                // current update is removed from the list
                curUpdate.isInList = false
                cluster = file.GetCluster(curUpdate.cluster)
                cache = GetAreaRoutingCache(curUpdate.cluster, curUpdate.areaNum, portalCache.travelFlags)

                // take all portals of the cluster
                i = 0
                while (i < cluster.numPortals) {
                    portalNum = file.GetPortalIndex(cluster.firstPortal + i)
                    assert(portalNum < portalCache.size)
                    portal = file.GetPortal(portalNum)
                    clusterAreaNum = ClusterAreaNum(curUpdate.cluster, portal.areaNum.toInt())
                    if (clusterAreaNum >= cluster.numReachableAreas) {
                        i++
                        continue
                    }
                    t = cache.travelTimes[clusterAreaNum]
                    if (t == 0) {
                        i++
                        continue
                    }
                    t += curUpdate.tmpTravelTime
                    if (0 == portalCache.travelTimes[portalNum] || t < portalCache.travelTimes[portalNum]) {
                        portalCache.travelTimes[portalNum] = t
                        portalCache.reachabilities[portalNum] = cache.reachabilities[clusterAreaNum]
                        portalUpdate.get(portalNum) =
                            if (portalUpdate.get(portalNum) == null) idRoutingUpdate() else portalUpdate.get(portalNum)
                        nextUpdate = portalUpdate.get(portalNum)
                        if (portal.clusters[0] == curUpdate.cluster) {
                            nextUpdate.cluster = portal.clusters[1]
                        } else {
                            nextUpdate.cluster = portal.clusters[0]
                        }
                        nextUpdate.areaNum = portal.areaNum.toInt()
                        // add travel time through the actual portal area for the next update
                        nextUpdate.tmpTravelTime = t + portal.maxAreaTravelTime
                        if (!nextUpdate.isInList) {
                            nextUpdate.next = null
                            nextUpdate.prev = updateListEnd
                            if (updateListEnd != null) {
                                updateListEnd.next = nextUpdate
                            } else {
                                updateListStart = nextUpdate
                            }
                            updateListEnd = nextUpdate
                            nextUpdate.isInList = true
                        }
                    }
                    i++
                }
            }
        }

        private fun GetPortalRoutingCache(clusterNum: Int, areaNum: Int, travelFlags: Int): idRoutingCache? {
            var cache: idRoutingCache?

            // check if cache without undesired travel flags already exists
            cache = portalCacheIndex.get(areaNum)
            while (cache != null) {
                if (cache.travelFlags == travelFlags) {
                    break
                }
                cache = cache.next
            }
            // if no cache found
            if (null == cache) {
                cache = idRoutingCache(file.GetNumPortals())
                cache.type = AAS_routing.CACHETYPE_PORTAL
                cache.cluster = clusterNum
                cache.areaNum = areaNum
                cache.startTravelTime = 1
                cache.travelFlags = travelFlags
                cache.prev = null
                cache.next = portalCacheIndex.get(areaNum)
                if (portalCacheIndex.get(areaNum) != null) {
                    portalCacheIndex.get(areaNum).prev = cache
                }
                portalCacheIndex.get(areaNum) = cache
                UpdatePortalRoutingCache(cache)
            }
            LinkCache(cache)
            return cache
        }

        private fun RemoveRoutingCacheUsingArea(areaNum: Int) {
            val clusterNum: Int
            clusterNum = file.GetArea(areaNum).cluster.toInt()
            if (clusterNum > 0) {
                // remove all the cache in the cluster the area is in
                DeleteClusterCache(clusterNum)
            } else {
                // if this is a portal remove all cache in both the front and back cluster
                DeleteClusterCache(file.GetPortal(-clusterNum).clusters[0])
                DeleteClusterCache(file.GetPortal(-clusterNum).clusters[1])
            }
            DeletePortalCache()
        }

        private fun DisableArea(areaNum: Int) {
            assert(areaNum > 0 && areaNum < file.GetNumAreas())
            if (file.GetArea(areaNum).travelFlags and AASFile.TFL_INVALID != 0) {
                return
            }
            file.SetAreaTravelFlag(areaNum, AASFile.TFL_INVALID)
            RemoveRoutingCacheUsingArea(areaNum)
        }

        private fun EnableArea(areaNum: Int) {
            assert(areaNum > 0 && areaNum < file.GetNumAreas())
            if (0 == file.GetArea(areaNum).travelFlags and AASFile.TFL_INVALID) {
                return
            }
            file.RemoveAreaTravelFlag(areaNum, AASFile.TFL_INVALID)
            RemoveRoutingCacheUsingArea(areaNum)
        }

        private fun SetAreaState_r(nodeNum: Int, bounds: idBounds?, areaContents: Int, disabled: Boolean): Boolean {
            var nodeNum = nodeNum
            var res: Int
            var node: aasNode_s?
            var foundClusterPortal = false
            while (nodeNum != 0) {
                if (nodeNum < 0) {
                    // if this area is a cluster portal
                    if (file.GetArea(-nodeNum).contents and areaContents != 0) {
                        if (disabled) {
                            DisableArea(-nodeNum)
                        } else {
                            EnableArea(-nodeNum)
                        }
                        foundClusterPortal = foundClusterPortal or true
                    }
                    break
                }
                node = file.GetNode(nodeNum)
                res = bounds.PlaneSide(file.GetPlane(node.planeNum))
                if (res == Plane.PLANESIDE_BACK) {
                    nodeNum = node.children[1]
                } else if (res == Plane.PLANESIDE_FRONT) {
                    nodeNum = node.children[0]
                } else {
                    foundClusterPortal =
                        foundClusterPortal or SetAreaState_r(node.children[1], bounds, areaContents, disabled)
                    nodeNum = node.children[0]
                }
            }
            return foundClusterPortal
        }

        private fun GetBoundsAreas_r(nodeNum: Int, bounds: idBounds?, areas: idList<Int?>?) {
            var nodeNum = nodeNum
            var res: Int
            var node: aasNode_s?
            while (nodeNum != 0) {
                if (nodeNum < 0) {
                    areas.Append(-nodeNum)
                    break
                }
                node = file.GetNode(nodeNum)
                res = bounds.PlaneSide(file.GetPlane(node.planeNum))
                nodeNum = if (res == Plane.PLANESIDE_BACK) {
                    node.children[1]
                } else if (res == Plane.PLANESIDE_FRONT) {
                    node.children[0]
                } else {
                    GetBoundsAreas_r(node.children[1], bounds, areas)
                    node.children[0]
                }
            }
        }

        private fun SetObstacleState(obstacle: idRoutingObstacle?, enable: Boolean) {
            var i: Int
            var area: aasArea_s?
            var reach: idReachability?
            var rev_reach: idReachability?
            var inside: Boolean
            i = 0
            while (i < obstacle.areas.Num()) {
                RemoveRoutingCacheUsingArea(obstacle.areas.oGet(i))
                area = file.GetArea(obstacle.areas.oGet(i))
                rev_reach = area.rev_reach
                while (rev_reach != null) {
                    if (rev_reach.travelType and AASFile.TFL_INVALID != 0) {
                        rev_reach = rev_reach.rev_next
                        continue
                    }
                    inside = false
                    if (obstacle.bounds.ContainsPoint(rev_reach.end)) {
                        inside = true
                    } else {
                        reach = area.reach
                        while (reach != null) {
                            if (obstacle.bounds.LineIntersection(rev_reach.end, reach.start)) {
                                inside = true
                                break
                            }
                            reach = reach.next
                        }
                    }
                    if (inside) {
                        if (enable) {
                            rev_reach.disableCount--
                            if (rev_reach.disableCount <= 0) {
                                rev_reach.travelType = rev_reach.travelType and AASFile.TFL_INVALID.inv()
                                rev_reach.disableCount = 0
                            }
                        } else {
                            rev_reach.travelType = rev_reach.travelType or AASFile.TFL_INVALID
                            rev_reach.disableCount++
                        }
                    }
                    rev_reach = rev_reach.rev_next
                }
                i++
            }
        }

        // pathing
        /*
         ============
         idAASLocal::EdgeSplitPoint

         calculates split point of the edge with the plane
         returns true if the split point is between the edge vertices
         ============
         */
        private fun EdgeSplitPoint(split: idVec3?, edgeNum: Int, plane: idPlane?): Boolean {
            val edge: aasEdge_s?
            val v1 = idVec3()
            val v2 = idVec3()
            val d1: Float
            val d2: Float
            edge = file.GetEdge(edgeNum)
            v1.oSet(file.GetVertex(edge.vertexNum[0]))
            v2.oSet(file.GetVertex(edge.vertexNum[1]))
            d1 = v1.times(plane.Normal()) - plane.Dist()
            d2 = v2.times(plane.Normal()) - plane.Dist()

            //if ( (d1 < CM_CLIP_EPSILON && d2 < CM_CLIP_EPSILON) || (d1 > -CM_CLIP_EPSILON && d2 > -CM_CLIP_EPSILON) ) {
            if (Math_h.FLOATSIGNBITSET(d1) == Math_h.FLOATSIGNBITSET(d2)) {
                return false
            }
            split.oSet(v1.oPlus(v2.oMinus(v1).oMultiply(d1 / (d1 - d2))))
            return true
        }

        /*
         ============
         idAASLocal::FloorEdgeSplitPoint

         calculates either the closest or furthest point on the floor of the area which also lies on the pathPlane
         the point has to be on the front side of the frontPlane to be valid
         ============
         */
        private fun FloorEdgeSplitPoint(
            bestSplit: idVec3?,
            areaNum: Int,
            pathPlane: idPlane?,
            frontPlane: idPlane?,
            closest: Boolean
        ): Boolean {
            var i: Int
            var j: Int
            var faceNum: Int
            var edgeNum: Int
            val area: aasArea_s?
            var face: aasFace_s?
            val split = idVec3()
            var dist: Float
            var bestDist: Float
            bestDist = if (closest) {
                AAS_pathing.maxWalkPathDistance
            } else {
                -0.1f
            }
            area = file.GetArea(areaNum)
            i = 0
            while (i < area.numFaces) {
                faceNum = file.GetFaceIndex(area.firstFace + i)
                face = file.GetFace(Math.abs(faceNum))
                if (0 == face.flags and AASFile.FACE_FLOOR) {
                    i++
                    continue
                }
                j = 0
                while (j < face.numEdges) {
                    edgeNum = file.GetEdgeIndex(face.firstEdge + j)
                    if (!EdgeSplitPoint(split, Math.abs(edgeNum), pathPlane)) {
                        j++
                        continue
                    }
                    dist = frontPlane.Distance(split)
                    if (closest) {
                        if (dist >= -0.1f && dist < bestDist) {
                            bestDist = dist
                            bestSplit.oSet(split)
                        }
                    } else {
                        if (dist > bestDist) {
                            bestDist = dist
                            bestSplit.oSet(split)
                        }
                    }
                    j++
                }
                i++
            }
            return if (closest) {
                bestDist < AAS_pathing.maxWalkPathDistance
            } else {
                bestDist > -0.1f
            }
        }

        private fun SubSampleWalkPath(
            areaNum: Int,
            origin: idVec3?,
            start: idVec3?,
            end: idVec3?,
            travelFlags: Int,
            endAreaNum: CInt?
        ): idVec3? {
            var i: Int
            val numSamples: Int
            val curAreaNum = CInt()
            val dir = idVec3()
            val point = idVec3()
            val nextPoint = idVec3()
            val endPos = idVec3()
            dir.oSet(end.oMinus(start))
            numSamples = (dir.Length() / AAS_pathing.walkPathSampleDistance).toInt() + 1
            point.oSet(start)
            i = 1
            while (i < numSamples) {
                nextPoint.oSet(start.oPlus(dir.times(i.toFloat() / numSamples)))
                if (point.oMinus(nextPoint).LengthSqr() > Math_h.Square(AAS_pathing.maxWalkPathDistance)) {
                    return point
                }
                if (!WalkPathValid(areaNum, origin, 0, nextPoint, travelFlags, endPos, curAreaNum)) {
                    return point
                }
                point.oSet(nextPoint)
                endAreaNum.setVal(curAreaNum.getVal())
                i++
            }
            return point
        }

        private fun SubSampleFlyPath(
            areaNum: Int,
            origin: idVec3?,
            start: idVec3?,
            end: idVec3?,
            travelFlags: Int,
            endAreaNum: CInt?
        ): idVec3? {
            var i: Int
            val numSamples: Int
            val curAreaNum = CInt()
            val dir = idVec3()
            val point = idVec3()
            val nextPoint = idVec3()
            val endPos = idVec3()
            dir.oSet(end.oMinus(start))
            numSamples = (dir.Length() / AAS_pathing.flyPathSampleDistance).toInt() + 1
            point.oSet(start)
            i = 1
            while (i < numSamples) {
                nextPoint.oSet(start.oPlus(dir.times(i.toFloat() / numSamples)))
                if (point.oMinus(nextPoint).LengthSqr() > Math_h.Square(AAS_pathing.maxFlyPathDistance)) {
                    return point
                }
                if (!FlyPathValid(areaNum, origin, 0, nextPoint, travelFlags, endPos, curAreaNum)) {
                    return point
                }
                point.oSet(nextPoint)
                endAreaNum.setVal(curAreaNum.getVal())
                i++
            }
            return point
        }

        // debug
        private fun DefaultSearchBounds(): idBounds? {
            return file.GetSettings().boundingBoxes[0]
        }

        private fun DrawCone(origin: idVec3?, dir: idVec3?, radius: Float, color: idVec4?) {
            var i: Int
            val axis = idMat3()
            val center = idVec3()
            val top = idVec3()
            val p = idVec3()
            val lastp = idVec3()
            axis.oSet(2, dir)
            axis.oGet(2).NormalVectors(axis.oGet(0), axis.oGet(1))
            axis.oSet(1, axis.oGet(1).oNegative())
            center.oSet(origin.oPlus(dir))
            top.oSet(center.oPlus(dir.times(3.0f * radius)))
            lastp.oSet(center.oPlus(axis.oGet(1).times(radius)))
            i = 20
            while (i <= 360) {
                p.oSet(
                    center.oPlus(
                        axis.oGet(0).times((Math.sin(Math_h.DEG2RAD(i.toFloat()).toDouble()) * radius).toFloat())
                            .oPlus(
                                axis.oGet(1)
                                    .times((Math.cos(Math_h.DEG2RAD(i.toFloat()).toDouble()) * radius).toFloat())
                            )
                    )
                )
                Game_local.gameRenderWorld.DebugLine(color, lastp, p, 0)
                Game_local.gameRenderWorld.DebugLine(color, p, top, 0)
                lastp.oSet(p)
                i += 20
            }
        }

        private fun DrawArea(areaNum: Int) {
            var i: Int
            val numFaces: Int
            val firstFace: Int
            val area: aasArea_s?
            var reach: idReachability?
            if (TempDump.NOT(file)) {
                return
            }
            area = file.GetArea(areaNum)
            numFaces = area.numFaces
            firstFace = area.firstFace
            i = 0
            while (i < numFaces) {
                DrawFace(Math.abs(file.GetFaceIndex(firstFace + i)), file.GetFaceIndex(firstFace + i) < 0)
                i++
            }
            reach = area.reach
            while (reach != null) {
                DrawReachability(reach)
                reach = reach.next
            }
        }

        private fun DrawFace(faceNum: Int, side: Boolean) {
            var i: Int
            var j: Int
            val numEdges: Int
            val firstEdge: Int
            val face: aasFace_s?
            val mid = idVec3()
            val end = idVec3()
            if (TempDump.NOT(file)) {
                return
            }
            face = file.GetFace(faceNum)
            numEdges = face.numEdges
            firstEdge = face.firstEdge
            mid.oSet(Vector.getVec3_origin())
            i = 0
            while (i < numEdges) {
                DrawEdge(Math.abs(file.GetEdgeIndex(firstEdge + i)), face.flags and AASFile.FACE_FLOOR != 0)
                j = file.GetEdgeIndex(firstEdge + i)
                mid.plusAssign(file.GetVertex(file.GetEdge(Math.abs(j)).vertexNum[if (j < 0) 1 else 0]))
                i++
            }
            mid.divAssign(numEdges.toFloat())
            if (side) {
                end.oSet(mid.oMinus(file.GetPlane(file.GetFace(faceNum).planeNum).Normal().times(5.0f)))
            } else {
                end.oSet(mid.oPlus(file.GetPlane(file.GetFace(faceNum).planeNum).Normal().times(5.0f)))
            }
            Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorGreen, mid, end, 1)
        }

        private fun DrawEdge(edgeNum: Int, arrow: Boolean) {
            val edge: aasEdge_s?
            val color: idVec4
            if (TempDump.NOT(file)) {
                return
            }
            edge = file.GetEdge(edgeNum)
            color = Lib.Companion.colorRed
            if (arrow) {
                Game_local.gameRenderWorld.DebugArrow(
                    color,
                    file.GetVertex(edge.vertexNum[0]),
                    file.GetVertex(edge.vertexNum[1]),
                    1
                )
            } else {
                Game_local.gameRenderWorld.DebugLine(
                    color,
                    file.GetVertex(edge.vertexNum[0]),
                    file.GetVertex(edge.vertexNum[1])
                )
            }
            if (Game_local.gameLocal.GetLocalPlayer() != null) {
                Game_local.gameRenderWorld.DrawText(
                    Str.va("%d", edgeNum),
                    file.GetVertex(edge.vertexNum[0]).oPlus(file.GetVertex(edge.vertexNum[1])).oMultiply(0.5f)
                        .oPlus(idVec3(0, 0, 4)),
                    0.1f,
                    Lib.Companion.colorRed,
                    Game_local.gameLocal.GetLocalPlayer().viewAxis
                )
            }
        }

        private fun DrawReachability(reach: idReachability?) {
            Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorCyan, reach.start, reach.end, 2)
            if (Game_local.gameLocal.GetLocalPlayer() != null) {
                Game_local.gameRenderWorld.DrawText(
                    Str.va("%d", reach.edgeNum),
                    reach.start.oPlus(reach.end).oMultiply(0.5f),
                    0.1f,
                    Lib.Companion.colorWhite,
                    Game_local.gameLocal.GetLocalPlayer().viewAxis
                )
            }
            if (reach.travelType == AASFile.TFL_WALK) {
                val walk = reach as idReachability_Walk?
            }
        }

        private fun ShowArea(origin: idVec3?) {
            val areaNum: Int
            val area: aasArea_s?
            val org = idVec3()
            areaNum = PointReachableAreaNum(
                origin,
                DefaultSearchBounds(),
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            org.oSet(origin)
            PushPointIntoAreaNum(areaNum, org)
            if (SysCvar.aas_goalArea.GetInteger() != 0) {
                val travelTime = CInt()
                val reach = arrayOf<idReachability?>(null)
                RouteToGoalArea(
                    areaNum,
                    org,
                    SysCvar.aas_goalArea.GetInteger(),
                    AASFile.TFL_WALK or AASFile.TFL_AIR,
                    travelTime,
                    reach
                )
                Game_local.gameLocal.Printf("\rtt = %4d", travelTime.getVal())
                if (reach[0] != null) {
                    Game_local.gameLocal.Printf(" to area %4d", reach[0].toAreaNum)
                    DrawArea(reach[0].toAreaNum.toInt())
                }
            }
            if (areaNum != lastAreaNum) {
                area = file.GetArea(areaNum)
                Game_local.gameLocal.Printf("area %d: ", areaNum)
                if (area.flags and AASFile.AREA_LEDGE != 0) {
                    Game_local.gameLocal.Printf("AREA_LEDGE ")
                }
                if (area.flags and AASFile.AREA_REACHABLE_WALK != 0) {
                    Game_local.gameLocal.Printf("AREA_REACHABLE_WALK ")
                }
                if (area.flags and AASFile.AREA_REACHABLE_FLY != 0) {
                    Game_local.gameLocal.Printf("AREA_REACHABLE_FLY ")
                }
                if (area.contents and AASFile.AREACONTENTS_CLUSTERPORTAL != 0) {
                    Game_local.gameLocal.Printf("AREACONTENTS_CLUSTERPORTAL ")
                }
                if (area.contents and AASFile.AREACONTENTS_OBSTACLE != 0) {
                    Game_local.gameLocal.Printf("AREACONTENTS_OBSTACLE ")
                }
                Game_local.gameLocal.Printf("\n")
                lastAreaNum = areaNum
            }
            if (org != origin) {
                val bnds = file.GetSettings().boundingBoxes[0]
                bnds.oGet(1).z = bnds.oGet(0).z
                Game_local.gameRenderWorld.DebugBounds(Lib.Companion.colorYellow, bnds, org)
            }
            DrawArea(areaNum)
        }

        private fun ShowWallEdges(origin: idVec3?) {
            var i: Int
            val areaNum: Int
            val numEdges: Int
            val edges = IntArray(1024)
            val start = idVec3()
            val end = idVec3()
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (null == player) {
                return
            }
            areaNum = PointReachableAreaNum(
                origin,
                DefaultSearchBounds(),
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            numEdges = GetWallEdges(areaNum, idBounds(origin).Expand(256.0f), AASFile.TFL_WALK, edges, 1024)
            i = 0
            while (i < numEdges) {
                GetEdge(edges[i], start, end)
                Game_local.gameRenderWorld.DebugLine(Lib.Companion.colorRed, start, end)
                Game_local.gameRenderWorld.DrawText(
                    Str.va("%d", edges[i]),
                    start.oPlus(end).oMultiply(0.5f),
                    0.1f,
                    Lib.Companion.colorWhite,
                    player.viewAxis
                )
                i++
            }
        }

        private fun ShowHideArea(origin: idVec3?, targetAreaNum: Int) {
            val areaNum: Int
            val numObstacles: Int
            val target = idVec3()
            val goal = aasGoal_s()
            val obstacles =
                Stream.generate { aasObstacle_s() }.limit(10).toArray<aasObstacle_s?> { _Dummy_.__Array__() }
            areaNum = PointReachableAreaNum(
                origin,
                DefaultSearchBounds(),
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            target.oSet(AreaCenter(targetAreaNum))

            // consider the target an obstacle
            obstacles[0].absBounds.oSet(idBounds(target).Expand(16f))
            numObstacles = 1
            DrawCone(target, idVec3(0, 0, 1), 16.0f, Lib.Companion.colorYellow)
            val findCover = idAASFindCover(target)
            if (FindNearestGoal(
                    goal,
                    areaNum,
                    origin,
                    target,
                    AASFile.TFL_WALK or AASFile.TFL_AIR,
                    obstacles,
                    numObstacles,
                    findCover
                )
            ) {
                DrawArea(goal.areaNum)
                ShowWalkPath(origin, goal.areaNum, goal.origin)
                DrawCone(goal.origin, idVec3(0, 0, 1), 16.0f, Lib.Companion.colorWhite)
            }
        }

        private fun PullPlayer(origin: idVec3?, toAreaNum: Int): Boolean {
            val areaNum: Int
            val areaCenter = idVec3()
            val dir = idVec3()
            val vel = idVec3()
            val delta: idAngles?
            val path = aasPath_s()
            val player: idPlayer?
            player = Game_local.gameLocal.GetLocalPlayer()
            if (null == player) {
                return true
            }
            val physics = player.GetPhysics() ?: return true
            if (0 == toAreaNum) {
                return false
            }
            areaNum = PointReachableAreaNum(
                origin,
                DefaultSearchBounds(),
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            areaCenter.oSet(AreaCenter(toAreaNum))
            if (player.GetPhysics().GetAbsBounds().Expand(8f).ContainsPoint(areaCenter)) {
                return false
            }
            return if (WalkPathToGoal(
                    path,
                    areaNum,
                    origin,
                    toAreaNum,
                    areaCenter,
                    AASFile.TFL_WALK or AASFile.TFL_AIR
                )
            ) {
                dir.oSet(path.moveGoal.oMinus(origin))
                dir.timesAssign(2, 0.5f)
                dir.Normalize()
                delta = dir.ToAngles().minus(player.cmdAngles.minus(player.GetDeltaViewAngles()))
                delta.Normalize180()
                player.SetDeltaViewAngles(player.GetDeltaViewAngles().plus(delta.times(0.1f)))
                dir.oSet(2, 0.0f)
                dir.Normalize()
                dir.timesAssign(100.0f)
                vel.oSet(physics.GetLinearVelocity())
                dir.oSet(2, vel.oGet(2))
                physics.SetLinearVelocity(dir)
                true
            } else {
                false
            }
        }

        private fun RandomPullPlayer(origin: idVec3?) {
            val rnd: Int
            var i: Int
            var n: Int
            if (!PullPlayer(origin, SysCvar.aas_pullPlayer.GetInteger())) {
                rnd = (Game_local.gameLocal.random.RandomFloat() * file.GetNumAreas()).toInt()
                i = 0
                while (i < file.GetNumAreas()) {
                    n = (rnd + i) % file.GetNumAreas()
                    if (file.GetArea(n).flags and (AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY) != 0) {
                        SysCvar.aas_pullPlayer.SetInteger(n)
                    }
                    i++
                }
            } else {
                ShowWalkPath(
                    origin,
                    SysCvar.aas_pullPlayer.GetInteger(),
                    AreaCenter(SysCvar.aas_pullPlayer.GetInteger())
                )
            }
        }

        private fun ShowPushIntoArea(origin: idVec3?) {
            val areaNum: Int
            val target = idVec3()
            target.oSet(origin)
            areaNum = PointReachableAreaNum(
                target,
                DefaultSearchBounds(),
                AASFile.AREA_REACHABLE_WALK or AASFile.AREA_REACHABLE_FLY
            )
            PushPointIntoAreaNum(areaNum, target)
            Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorGreen, origin, target, 1)
        }

        companion object {
            private val dummy: idPlane? = idPlane()
            private var lastAreaNum = 0
        }

        init {
            obstacleList = idList()
        }
    }
}