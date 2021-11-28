package neo.Game.AI

import neo.CM.CollisionModel
import neo.CM.CollisionModel.trace_s
import neo.Game.AI.AAS.idAAS
import neo.Game.AI.AI.obstaclePath_s
import neo.Game.AI.AI.predictedPath_s
import neo.Game.Actor.idActor
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics.idPhysics
import neo.TempDump
import neo.Tools.Compilers.AAS.AASFile
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.Lib
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.Queue.idQueueTemplate
import neo.idlib.geometry.Winding2D.idWinding2D
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.ui.DeviceContext.idDeviceContext
import java.util.stream.Stream

/**
 *
 */
object AI_pathing {
    const val CLIP_BOUNDS_EPSILON = 10.0f
    const val MAX_AAS_WALL_EDGES = 256
    const val MAX_FRAME_SLIDE = 5
    const val MAX_OBSTACLES = 256
    const val MAX_OBSTACLE_PATH = 64

    /*
     ===============================================================================

     Dynamic Obstacle Avoidance

     - assumes the AI lives inside a bounding box aligned with the gravity direction
     - obstacles in proximity of the AI are gathered
     - if obstacles are found the AAS walls are also considered as obstacles
     - every obstacle is represented by an oriented bounding box (OBB)
     - an OBB is projected onto a 2D plane orthogonal to AI's gravity direction
     - the 2D windings of the projections are expanded for the AI bbox
     - a path tree is build using clockwise and counter clockwise edge walks along the winding edges
     - the path tree is pruned and optimized
     - the shortest path is chosen for navigation

     ===============================================================================
     */
    const val MAX_OBSTACLE_RADIUS = 256.0f
    const val MAX_PATH_NODES = 256

    //
    const val OVERCLIP = 1.001f
    const val PUSH_OUTSIDE_OBSTACLES = 0.5f

    //
    //    static idBlockAlloc<pathNode_s> pathNodeAllocator = new Heap.idBlockAlloc<>(128);
    var pathNodeAllocator = 0

    //    
    /*
     ============
     LineIntersectsPath
     ============
     */
    fun LineIntersectsPath(start: idVec2?, end: idVec2?, node: pathNode_s?): Boolean {
        var d0: Float
        var d1: Float
        var d2: Float
        var d3: Float
        val plane1 = idVec3()
        val plane2 = idVec3()
        plane1.oSet(Plane2DFromPoints(start, end))
        d0 = plane1.x * node.pos.x + plane1.y * node.pos.y + plane1.z
        while (node.parent != null) {
            d1 = plane1.x * node.parent.pos.x + plane1.y * node.parent.pos.y + plane1.z
            if (Math_h.FLOATSIGNBITSET(d0) xor Math_h.FLOATSIGNBITSET(d1) != 0) {
                plane2.oSet(Plane2DFromPoints(node.pos, node.parent.pos))
                d2 = plane2.x * start.x + plane2.y * start.y + plane2.z
                d3 = plane2.x * end.x + plane2.y * end.y + plane2.z
                if (Math_h.FLOATSIGNBITSET(d2) xor Math_h.FLOATSIGNBITSET(d3) != 0) {
                    return true
                }
            }
            d0 = d1
            node.oSet(node.parent)
        }
        return false
    }

    /*
     ============
     PointInsideObstacle
     ============
     */
    fun PointInsideObstacle(obstacles: Array<obstacle_s?>?, numObstacles: Int, point: idVec2?): Int {
        var i: Int
        i = 0
        while (i < numObstacles) {
            val bounds = obstacles.get(i).bounds
            if (point.x < bounds.get(0).x || point.y < bounds.get(0).y || point.x > bounds.get(1).x || point.y > bounds.get(
                    1
                ).y
            ) {
                i++
                continue
            }
            if (!obstacles.get(i).winding.PointInside(point, 0.1f)) {
                i++
                continue
            }
            return i
            i++
        }
        return -1
    }

    /*
     ============
     GetPointOutsideObstacles
     ============
     */
    // all calls to this function are with edgeNum = null, what is the point of it?
    fun GetPointOutsideObstacles(
        obstacles: Array<obstacle_s?>?,
        numObstacles: Int,
        point: idVec2?,
        obstacle: CInt?,
        edgeNum: CInt?
    ) {
        var i: Int
        var j: Int
        var k: Int
        var n: Int
        var bestObstacle: Int
        var bestEdgeNum: Int
        var queueStart: Int
        val queueEnd: Int
        val edgeNums = IntArray(2)
        var d: Float
        var bestd: Float
        val scale = Stream.generate { CFloat() }
            .limit(2)
            .toArray<CFloat?> { _Dummy_.__Array__() }
        val plane = idVec3()
        val bestPlane = idVec3()
        var newPoint: idVec2?
        var dir: idVec2
        var bestPoint: idVec2? = idVec2()
        val queue: IntArray
        val obstacleVisited: BooleanArray
        var w1: idWinding2D?
        var w2: idWinding2D?
        obstacle?.setVal(-1)
        edgeNum?.setVal(-1)
        bestObstacle = AI_pathing.PointInsideObstacle(obstacles, numObstacles, point)
        if (bestObstacle == -1) {
            return
        }
        val w = obstacles.get(bestObstacle).winding
        bestd = idMath.INFINITY
        bestEdgeNum = 0
        i = 0
        while (i < w.GetNumPoints()) {
            plane.oSet(idWinding2D.Companion.Plane2DFromPoints(w.oGet((i + 1) % w.GetNumPoints()), w.oGet(i), true))
            d = plane.x * point.x + plane.y * point.y + plane.z
            if (d < bestd) {
                bestd = d
                bestPlane.oSet(plane)
                bestEdgeNum = i
            }
            // if this is a wall always try to pop out at the first edge
            if (obstacles.get(bestObstacle).entity == null) {
                break
            }
            i++
        }
        newPoint = point.oMinus(bestPlane.ToVec2().oMultiply(bestd + AI_pathing.PUSH_OUTSIDE_OBSTACLES))
        if (AI_pathing.PointInsideObstacle(obstacles, numObstacles, newPoint) == -1) {
            point.oSet(newPoint)
            obstacle?.setVal(bestObstacle)
            edgeNum?.setVal(bestEdgeNum)
            return
        }
        queue = IntArray(numObstacles)
        obstacleVisited = BooleanArray(numObstacles)
        queueStart = 0
        queueEnd = 1
        queue[0] = bestObstacle

//	memset( obstacleVisited, 0, numObstacles * sizeof( obstacleVisited[0] ) );
        obstacleVisited[bestObstacle] = true
        bestd = idMath.INFINITY
        i = queue[0]
        while (queueStart < queueEnd) {
            w1 = obstacles.get(i).winding
            w1.Expand(AI_pathing.PUSH_OUTSIDE_OBSTACLES)
            j = 0
            while (j < numObstacles) {

                // if the obstacle has been visited already
                if (obstacleVisited[j]) {
                    j++
                    continue
                }
                // if the bounds do not intersect
                if (obstacles.get(j).bounds.get(0).x > obstacles.get(i).bounds.get(1).x || obstacles.get(j).bounds.get(0).y > obstacles.get(
                        i
                    ).bounds.get(1).y || obstacles.get(j).bounds.get(1).x < obstacles.get(i).bounds.get(0).x || obstacles.get(
                        j
                    ).bounds.get(1).y < obstacles.get(i).bounds.get(0).y
                ) {
                    j++
                    continue
                }
                queue[queueEnd++] = j
                obstacleVisited[j] = true
                w2 = obstacles.get(j).winding
                w2.Expand(0.2f)
                k = 0
                while (k < w1.GetNumPoints()) {
                    dir = w1.oGet((k + 1) % w1.GetNumPoints()).oMinus(w1.oGet(k))
                    if (!w2.RayIntersection(w1.oGet(k), dir, scale[0], scale[1], edgeNums)) {
                        k++
                        continue
                    }
                    n = 0
                    while (n < 2) {
                        newPoint = w1.oGet(k).oPlus(dir.oMultiply(scale[n].getVal()))
                        if (AI_pathing.PointInsideObstacle(obstacles, numObstacles, newPoint) == -1) {
                            d = newPoint.oMinus(point).LengthSqr()
                            if (d < bestd) {
                                bestd = d
                                bestPoint = newPoint
                                bestEdgeNum = edgeNums[n]
                                bestObstacle = j
                            }
                        }
                        n++
                    }
                    k++
                }
                j++
            }
            if (bestd < idMath.INFINITY) {
                point.oSet(bestPoint)
                obstacle?.setVal(bestObstacle)
                edgeNum?.setVal(bestEdgeNum)
                return
            }
            i = queue[++queueStart]
        }
        Game_local.gameLocal.Warning("GetPointOutsideObstacles: no valid point found")
    }

    /*
     ============
     GetFirstBlockingObstacle
     ============
     */
    fun GetFirstBlockingObstacle(
        obstacles: Array<obstacle_s?>?,
        numObstacles: Int,
        skipObstacle: Int,
        startPos: idVec2?,
        delta: idVec2?,
        blockingScale: CFloat?,
        blockingObstacle: CInt?,
        blockingEdgeNum: CInt?
    ): Boolean {
        var blockingScale = blockingScale
        var i: Int
        val edgeNums = IntArray(2)
        val dist: Float
        val scale1 = CFloat()
        val scale2 = CFloat()
        val bounds: Array<idVec2?> = idVec2.Companion.generateArray(2)

        // get bounds for the current movement delta
        bounds[0] = startPos.oMinus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
        bounds[1] = startPos.oPlus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
        bounds[Math_h.FLOATSIGNBITNOTSET(delta.x)].x += delta.x
        bounds[Math_h.FLOATSIGNBITNOTSET(delta.y)].y += delta.y

        // test for obstacles blocking the path
        blockingScale.setVal(idMath.INFINITY)
        dist = delta.Length()
        i = 0
        while (i < numObstacles) {
            if (i == skipObstacle) {
                i++
                continue
            }
            if (bounds[0].x > obstacles.get(i).bounds.get(1).x || bounds[0].y > obstacles.get(i).bounds.get(1).y || bounds[1].x < obstacles.get(
                    i
                ).bounds.get(0).x || bounds[1].y < obstacles.get(i).bounds.get(0).y
            ) {
                i++
                continue
            }
            if (obstacles.get(i).winding.RayIntersection(startPos, delta, scale1, scale2, edgeNums)) {
                if (scale1.getVal() < blockingScale.getVal() && scale1.getVal() * dist > -0.01f && scale2.getVal() * dist > 0.01f) {
                    blockingScale = scale1
                    blockingObstacle.setVal(i)
                    blockingEdgeNum.setVal(edgeNums[0])
                }
            }
            i++
        }
        return blockingScale.getVal() < 1.0f
    }

    /*
     ============
     GetObstacles
     ============
     */
    fun GetObstacles(
        physics: idPhysics?,
        aas: idAAS?,
        ignore: idEntity?,
        areaNum: Int,
        startPos: idVec3?,
        seekPos: idVec3?,
        obstacles: Array<obstacle_s?>?,
        maxObstacles: Int,
        clipBounds: idBounds?
    ): Int {
        var i: Int
        var j: Int
        val numListedClipModels: Int
        var numObstacles: Int
        var numVerts: Int
        val clipMask: Int
        val blockingObstacle = CInt()
        val blockingEdgeNum = CInt()
        val wallEdges = IntArray(AI_pathing.MAX_AAS_WALL_EDGES)
        val verts = IntArray(2)
        val lastVerts = IntArray(2)
        val nextVerts = IntArray(2)
        val numWallEdges: Int
        val stepHeight = CFloat()
        val headHeight = CFloat()
        val blockingScale = CFloat()
        val min = CFloat()
        val max = CFloat()
        val seekDelta = idVec3()
        val start = idVec3()
        val end = idVec3()
        val nextStart = idVec3()
        val nextEnd = idVec3()
        val silVerts: Array<idVec3?> = idVec3.Companion.generateArray(32)
        var edgeDir: idVec2?
        val edgeNormal = idVec2()
        var nextEdgeDir: idVec2?
        val nextEdgeNormal = idVec2()
        var lastEdgeNormal = idVec2()
        val expBounds: Array<idVec2?> = idVec2.Companion.generateArray(2)
        var obDelta: idVec2
        var obPhys: idPhysics
        var box: idBox?
        var obEnt: idEntity
        var clipModel: idClipModel
        val clipModelList = Stream.generate { idClipModel() }.limit(Game_local.MAX_GENTITIES.toLong())
            .toArray<idClipModel?> { _Dummy_.__Array__() }
        numObstacles = 0
        seekDelta.oSet(seekPos.oMinus(startPos))
        expBounds[0] = physics.GetBounds().oGet(0).ToVec2()
            .oMinus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
        expBounds[1] = physics.GetBounds().oGet(1).ToVec2()
            .oPlus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
        physics.GetAbsBounds().AxisProjection(physics.GetGravityNormal().oNegative(), stepHeight, headHeight)
        stepHeight.setVal(stepHeight.getVal() + aas.GetSettings().maxStepHeight.getVal())

        // clip bounds for the obstacle search space
        clipBounds.oSet(0, clipBounds.oSet(1, startPos))
        clipBounds.AddPoint(seekPos)
        clipBounds.ExpandSelf(AI_pathing.MAX_OBSTACLE_RADIUS)
        clipMask = physics.GetClipMask()

        // find all obstacles touching the clip bounds
        numListedClipModels = Game_local.gameLocal.clip.ClipModelsTouchingBounds(
            clipBounds,
            clipMask,
            clipModelList,
            Game_local.MAX_GENTITIES
        )
        i = 0
        while (i < numListedClipModels && numObstacles < AI_pathing.MAX_OBSTACLES) {
            clipModel = clipModelList[i]
            obEnt = clipModel.GetEntity()
            if (!clipModel.IsTraceModel()) {
                i++
                continue
            }
            if (obEnt is idActor) {
                obPhys = obEnt.GetPhysics()
                // ignore myself, my enemy, and dead bodies
                if (obPhys === physics || obEnt === ignore || obEnt.health <= 0) {
                    i++
                    continue
                }
                // if the actor is moving
                val v1 = idVec3(obPhys.GetLinearVelocity())
                if (v1.LengthSqr() > Math_h.Square(10.0f)) {
                    val v2 = idVec3(physics.GetLinearVelocity())
                    if (v2.LengthSqr() > Math_h.Square(10.0f)) {
                        // if moving in about the same direction
                        if (v1.oMultiply(v2) > 0.0f) {
                            i++
                            continue
                        }
                    }
                }
            } else if (obEnt is idMoveable) {
                // moveables are considered obstacles
            } else {
                // ignore everything else
                i++
                continue
            }

            // check if we can step over the object
            clipModel.GetAbsBounds().AxisProjection(physics.GetGravityNormal().oNegative(), min, max)
            if (max.getVal() < stepHeight.getVal() || min.getVal() > headHeight.getVal()) {
                // can step over this one
                i++
                continue
            }

            // project a box containing the obstacle onto the floor plane
            box = idBox(clipModel.GetBounds(), clipModel.GetOrigin(), clipModel.GetAxis())
            numVerts = box.GetParallelProjectionSilhouetteVerts(physics.GetGravityNormal(), silVerts)

            // create a 2D winding for the obstacle;
            val obstacle = obstacles.get(numObstacles++)
            obstacle.winding.Clear()
            j = 0
            while (j < numVerts) {
                obstacle.winding.AddPoint(silVerts[j].ToVec2())
                j++
            }
            if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
                j = 0
                while (j < numVerts) {
                    silVerts[j].z = startPos.z
                    j++
                }
                j = 0
                while (j < numVerts) {
                    Game_local.gameRenderWorld.DebugArrow(
                        idDeviceContext.Companion.colorWhite,
                        silVerts[j],
                        silVerts[(j + 1) % numVerts],
                        4
                    )
                    j++
                }
            }

            // expand the 2D winding for collision with a 2D box
            obstacle.winding.ExpandForAxialBox(expBounds)
            obstacle.winding.GetBounds(obstacle.bounds)
            obstacle.entity = obEnt
            i++
        }

        // if there are no dynamic obstacles the path should be through valid AAS space
        if (numObstacles == 0) {
            return 0
        }

        // if the current path doesn't intersect any dynamic obstacles the path should be through valid AAS space
        if (AI_pathing.PointInsideObstacle(obstacles, numObstacles, startPos.ToVec2()) == -1) {
            if (!AI_pathing.GetFirstBlockingObstacle(
                    obstacles,
                    numObstacles,
                    -1,
                    startPos.ToVec2(),
                    seekDelta.ToVec2(),
                    blockingScale,
                    blockingObstacle,
                    blockingEdgeNum
                )
            ) {
                return 0
            }
        }

        // create obstacles for AAS walls
        if (aas != null) {
            val halfBoundsSize = (expBounds[1].x - expBounds[0].x) * 0.5f
            numWallEdges =
                aas.GetWallEdges(areaNum, clipBounds, AASFile.TFL_WALK, wallEdges, AI_pathing.MAX_AAS_WALL_EDGES)
            aas.SortWallEdges(wallEdges, numWallEdges)
            lastVerts[1] = 0
            lastVerts[0] = lastVerts[1]
            lastEdgeNormal.Zero()
            nextVerts[1] = 0
            nextVerts[0] = nextVerts[1]
            i = 0
            while (i < numWallEdges && numObstacles < AI_pathing.MAX_OBSTACLES) {
                aas.GetEdge(wallEdges[i], start, end)
                aas.GetEdgeVertexNumbers(wallEdges[i], verts)
                edgeDir = end.ToVec2().oMinus(start.ToVec2())
                edgeDir.Normalize()
                edgeNormal.x = edgeDir.y
                edgeNormal.y = -edgeDir.x
                if (i < numWallEdges - 1) {
                    aas.GetEdge(wallEdges[i + 1], nextStart, nextEnd)
                    aas.GetEdgeVertexNumbers(wallEdges[i + 1], nextVerts)
                    nextEdgeDir = nextEnd.ToVec2().oMinus(nextStart.ToVec2())
                    nextEdgeDir.Normalize()
                    nextEdgeNormal.x = nextEdgeDir.y
                    nextEdgeNormal.y = -nextEdgeDir.x
                }
                val obstacle = obstacles.get(numObstacles++)
                obstacle.winding.Clear()
                obstacle.winding.AddPoint(end.ToVec2())
                obstacle.winding.AddPoint(start.ToVec2())
                obstacle.winding.AddPoint(start.ToVec2().oMinus(edgeDir.oMinus(edgeNormal.oMultiply(halfBoundsSize))))
                obstacle.winding.AddPoint(end.ToVec2().oPlus(edgeDir.oMinus(edgeNormal.oMultiply(halfBoundsSize))))
                if (lastVerts[1] == verts[0]) {
                    obstacle.winding.oMinSet(2, lastEdgeNormal.oMultiply(halfBoundsSize))
                } else {
                    obstacle.winding.oMinSet(1, edgeDir)
                }
                if (verts[1] == nextVerts[0]) {
                    obstacle.winding.oMinSet(3, nextEdgeNormal.oMultiply(halfBoundsSize))
                } else {
                    obstacle.winding.oPluSet(0, edgeDir)
                }
                obstacle.winding.GetBounds(obstacle.bounds)
                obstacle.entity = null

//			memcpy( lastVerts, verts, sizeof( lastVerts ) );
                lastVerts[0] = verts[0]
                lastVerts[1] = verts[1]
                lastEdgeNormal = edgeNormal
                i++
            }
        }

        // show obstacles
        if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
            i = 0
            while (i < numObstacles) {
                val obstacle = obstacles.get(i)
                j = 0
                while (j < obstacle.winding.GetNumPoints()) {
                    silVerts[j].oSet(obstacle.winding.oGet(j))
                    silVerts[j].z = startPos.z
                    j++
                }
                j = 0
                while (j < obstacle.winding.GetNumPoints()) {
                    Game_local.gameRenderWorld.DebugArrow(
                        idDeviceContext.Companion.colorGreen,
                        silVerts[j],
                        silVerts[(j + 1) % obstacle.winding.GetNumPoints()],
                        4
                    )
                    j++
                }
                i++
            }
        }
        return numObstacles
    }

    /*
     ============
     FreePathTree_r
     ============
     */
    fun FreePathTree_r(node: pathNode_s?) {
        if (node.children.get(0) != null) {
            AI_pathing.FreePathTree_r(node.children.get(0))
        }
        if (node.children.get(1) != null) {
            AI_pathing.FreePathTree_r(node.children.get(1))
        }
        //        pathNodeAllocator.Free(node);
        AI_pathing.pathNodeAllocator--
    }

    /*
     ============
     DrawPathTree
     ============
     */
    fun DrawPathTree(root: pathNode_s?, height: Float) {
        var i: Int
        val start = idVec3()
        val end = idVec3()
        var node: pathNode_s?
        node = root
        while (node != null) {
            i = 0
            while (i < 2) {
                if (node.children.get(i) != null) {
                    start.oSet(node.pos)
                    start.z = height
                    end.oSet(node.children.get(i).pos)
                    end.z = height
                    Game_local.gameRenderWorld.DebugArrow(
                        if (node.edgeNum == -1) idDeviceContext.Companion.colorYellow else if (i != 0) idDeviceContext.Companion.colorBlue else idDeviceContext.Companion.colorRed,
                        start,
                        end,
                        1
                    )
                    break
                }
                i++
            }
            node = node.next
        }
    }

    /*
     ============
     GetPathNodeDelta
     ============
     */
    fun GetPathNodeDelta(
        node: pathNode_s?,
        obstacles: Array<obstacle_s?>?,
        seekPos: idVec2?,
        blocked: Boolean
    ): Boolean {
        val numPoints: Int
        var edgeNum: Int
        val facing: Boolean
        val seekDelta = idVec2()
        var n: pathNode_s?
        numPoints = obstacles.get(node.obstacle).winding.GetNumPoints()

        // get delta along the current edge
        while (true) {
            edgeNum = (node.edgeNum + node.dir) % numPoints
            node.delta = obstacles.get(node.obstacle).winding.oGet(edgeNum).oMinus(node.pos)
            if (node.delta.LengthSqr() > 0.01f) {
                break
            }
            node.edgeNum = (node.edgeNum + numPoints + (2 * node.dir - 1)) % numPoints
        }

        // if not blocked
        if (!blocked) {

            // test if the current edge faces the goal
            seekDelta.oSet(seekPos.oMinus(node.pos))
            facing = (2 * node.dir - 1) * (node.delta.x * seekDelta.y - node.delta.y * seekDelta.x) >= 0.0f

            // if the current edge faces goal and the line from the current
            // position to the goal does not intersect the current path
            if (facing && !AI_pathing.LineIntersectsPath(node.pos, seekPos, node.parent)) {
                node.delta = seekPos.oMinus(node.pos)
                node.edgeNum = -1
            }
        }

        // if the delta is along the obstacle edge
        if (node.edgeNum != -1) {
            // if the edge is found going from this node to the root node
            n = node.parent
            while (n != null) {
                if (node.obstacle != n.obstacle || node.edgeNum != n.edgeNum) {
                    n = n.parent
                    continue
                }

                // test whether or not the edge segments actually overlap
                if (n.pos.oMultiply(node.delta) > node.pos.oPlus(node.delta).oMultiply(node.delta)) {
                    n = n.parent
                    continue
                }
                if (node.pos.oMultiply(node.delta) > n.pos.oPlus(n.delta).oMultiply(node.delta)) {
                    n = n.parent
                    continue
                }
                break
                n = n.parent
            }
            return n == null
        }
        return true
    }

    /*
     ============
     BuildPathTree
     ============
     */
    fun BuildPathTree(
        obstacles: Array<obstacle_s?>?,
        numObstacles: Int,
        clipBounds: idBounds?,
        startPos: idVec2?,
        seekPos: idVec2?,
        path: obstaclePath_s?
    ): pathNode_s? {
        var obstaclePoints: Int
        var bestNumNodes = AI_pathing.MAX_OBSTACLE_PATH
        val blockingEdgeNum = CInt()
        val blockingObstacle = CInt()
        val blockingScale = CFloat()
        val root: pathNode_s
        var node: pathNode_s
        var child: pathNode_s
        // gcc 4.0
        val pathNodeQueue = idQueueTemplate<pathNode_s?>()
        val treeQueue = idQueueTemplate<pathNode_s?>()
        root = pathNode_s() //pathNodeAllocator.Alloc();
        root.Init()
        root.pos = startPos
        root.delta = seekPos.oMinus(root.pos)
        root.numNodes = 0
        pathNodeQueue.Add(root)
        node = pathNodeQueue.Get()
        while (node != null && AI_pathing.pathNodeAllocator < AI_pathing.MAX_PATH_NODES) {
            treeQueue.Add(node)

            // if this path has more than twice the number of nodes than the best path so far
            if (node.numNodes > bestNumNodes * 2) {
                node = pathNodeQueue.Get()
                continue
            }

            // don't move outside of the clip bounds
            val endPos = node.pos.oPlus(node.delta)
            if (endPos.x - AI_pathing.CLIP_BOUNDS_EPSILON < clipBounds.oGet(0).x || endPos.x + AI_pathing.CLIP_BOUNDS_EPSILON > clipBounds.oGet(
                    1
                ).x || endPos.y - AI_pathing.CLIP_BOUNDS_EPSILON < clipBounds.oGet(0).y || endPos.y + AI_pathing.CLIP_BOUNDS_EPSILON > clipBounds.oGet(
                    1
                ).y
            ) {
                node = pathNodeQueue.Get()
                continue
            }

            // if an obstacle is blocking the path
            if (AI_pathing.GetFirstBlockingObstacle(
                    obstacles,
                    numObstacles,
                    node.obstacle,
                    node.pos,
                    node.delta,
                    blockingScale,
                    blockingObstacle,
                    blockingEdgeNum
                )
            ) {
                if (path.firstObstacle == null) {
                    path.firstObstacle = obstacles.get(blockingObstacle.getVal()).entity
                }
                node.delta.oMulSet(blockingScale.getVal())
                if (node.edgeNum == -1) {
                    node.children.get(0) = pathNode_s() // pathNodeAllocator.Alloc();
                    node.children.get(0).Init()
                    node.children.get(1) = pathNode_s() //pathNodeAllocator.Alloc();
                    node.children.get(1).Init()
                    node.children.get(0).dir = 0
                    node.children.get(1).dir = 1
                    node.children.get(1).parent = node
                    node.children.get(0).parent = node.children.get(1).parent
                    node.children.get(1).pos = node.pos.oPlus(node.delta)
                    node.children.get(0).pos = node.children.get(1).pos
                    node.children.get(1).obstacle = blockingObstacle.getVal()
                    node.children.get(0).obstacle = node.children.get(1).obstacle
                    node.children.get(1).edgeNum = blockingEdgeNum.getVal()
                    node.children.get(0).edgeNum = node.children.get(1).edgeNum
                    node.children.get(1).numNodes = node.numNodes + 1
                    node.children.get(0).numNodes = node.children.get(1).numNodes
                    if (AI_pathing.GetPathNodeDelta(node.children.get(0), obstacles, seekPos, true)) {
                        pathNodeQueue.Add(node.children.get(0))
                    }
                    if (AI_pathing.GetPathNodeDelta(node.children.get(1), obstacles, seekPos, true)) {
                        pathNodeQueue.Add(node.children.get(1))
                    }
                } else {
                    child = pathNode_s()
                    node.children.get(node.dir) = child //pathNodeAllocator.Alloc();
                    child.Init()
                    child.dir = node.dir
                    child.parent = node
                    child.pos = node.pos.oPlus(node.delta)
                    child.obstacle = blockingObstacle.getVal()
                    child.edgeNum = blockingEdgeNum.getVal()
                    child.numNodes = node.numNodes + 1
                    if (AI_pathing.GetPathNodeDelta(child, obstacles, seekPos, true)) {
                        pathNodeQueue.Add(child)
                    }
                }
            } else {
                child = pathNode_s()
                node.children.get(node.dir) = child //pathNodeAllocator.Alloc();
                child.Init()
                child.dir = node.dir
                child.parent = node
                child.pos = node.pos.oPlus(node.delta)
                child.numNodes = node.numNodes + 1

                // there is a free path towards goal
                if (node.edgeNum == -1) {
                    if (node.numNodes < bestNumNodes) {
                        bestNumNodes = node.numNodes
                    }
                    node = pathNodeQueue.Get()
                    continue
                }
                child.obstacle = node.obstacle
                obstaclePoints = obstacles.get(node.obstacle).winding.GetNumPoints()
                child.edgeNum = (node.edgeNum + obstaclePoints + (2 * node.dir - 1)) % obstaclePoints
                if (AI_pathing.GetPathNodeDelta(child, obstacles, seekPos, false)) {
                    pathNodeQueue.Add(child)
                }
            }
            node = pathNodeQueue.Get()
        }
        return root
    }

    /*
     ============
     PrunePathTree
     ============
     */
    fun PrunePathTree(root: pathNode_s?, seekPos: idVec2?) {
        var i: Int
        var bestDist: Float
        var node: pathNode_s?
        var lastNode: pathNode_s?
        var n: pathNode_s?
        var bestNode: pathNode_s?
        node = root
        while (node != null) {
            node.dist = seekPos.oMinus(node.pos).LengthSqr()
            if (node.children.get(0) != null) {
                node = node.children.get(0)
            } else if (node.children.get(1) != null) {
                node = node.children.get(1)
            } else {

                // find the node closest to the goal along this path
                bestDist = idMath.INFINITY
                bestNode = node
                n = node
                while (n != null) {
                    if (n.children.get(0) != null && n.children.get(1) != null) {
                        break
                    }
                    if (n.dist < bestDist) {
                        bestDist = n.dist
                        bestNode = n
                    }
                    n = n.parent
                }

                // free tree down from the best node
                i = 0
                while (i < 2) {
                    if (bestNode.children.get(i) != null) {
                        AI_pathing.FreePathTree_r(bestNode.children.get(i))
                        bestNode.children.get(i) = null
                    }
                    i++
                }
                lastNode = bestNode
                node = bestNode.parent
                while (node != null) {
                    if (node.children.get(1) != null && node.children.get(1) !== lastNode) {
                        node = node.children.get(1)
                        break
                    }
                    lastNode = node
                    node = node.parent
                }
            }
        }
    }

    /*
     ============
     OptimizePath
     ============
     */
    fun OptimizePath(
        root: pathNode_s?,
        leafNode: pathNode_s?,
        obstacles: Array<obstacle_s?>?,
        numObstacles: Int,
        optimizedPath: Array<idVec2?>? /*[MAX_OBSTACLE_PATH]*/
    ): Int {
        var i: Int
        val numPathPoints: Int
        val edgeNums = IntArray(2)
        var curNode: pathNode_s?
        var nextNode: pathNode_s?
        var curPos: idVec2?
        var curDelta: idVec2?
        val bounds: Array<idVec2?> = idVec2.Companion.generateArray(2)
        var curLength: Float
        val scale1 = CFloat()
        val scale2 = CFloat()
        optimizedPath.get(0) = root.pos
        numPathPoints = 1
        nextNode = root.also { curNode = it }
        while (curNode !== leafNode) {
            nextNode = leafNode
            while (nextNode.parent !== curNode) {


                // can only take shortcuts when going from one object to another
                if (nextNode.obstacle == curNode.obstacle) {
                    nextNode = nextNode.parent
                    continue
                }
                curPos = curNode.pos
                curDelta = nextNode.pos.oMinus(curPos)
                curLength = curDelta.Length()

                // get bounds for the current movement delta
                bounds[0] = curPos.oMinus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
                bounds[1] = curPos.oPlus(idVec2(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON))
                bounds[Math_h.FLOATSIGNBITNOTSET(curDelta.x)].x += curDelta.x
                bounds[Math_h.FLOATSIGNBITNOTSET(curDelta.y)].y += curDelta.y

                // test if the shortcut intersects with any obstacles
                i = 0
                while (i < numObstacles) {
                    if (bounds[0].x > obstacles.get(i).bounds.get(1).x || bounds[0].y > obstacles.get(i).bounds.get(1).y || bounds[1].x < obstacles.get(
                            i
                        ).bounds.get(0).x || bounds[1].y < obstacles.get(i).bounds.get(0).y
                    ) {
                        i++
                        continue
                    }
                    if (obstacles.get(i).winding.RayIntersection(curPos, curDelta, scale1, scale2, edgeNums)) {
                        if (scale1.getVal() >= 0.0f && scale1.getVal() <= 1.0f && (i != nextNode.obstacle || scale1.getVal() * curLength < curLength - 0.5f)) {
                            break
                        }
                        if (scale2.getVal() >= 0.0f && scale2.getVal() <= 1.0f && (i != nextNode.obstacle || scale2.getVal() * curLength < curLength - 0.5f)) {
                            break
                        }
                    }
                    i++
                }
                if (i >= numObstacles) {
                    break
                }
                nextNode = nextNode.parent
            }

            // store the next position along the optimized path
            optimizedPath.get(numPathPoints++) = nextNode.pos
            curNode = nextNode
        }
        return numPathPoints
    }

    /*
     ============
     PathLength
     ============
     */
    fun PathLength(optimizedPath: Array<idVec2?>? /*[MAX_OBSTACLE_PATH]*/, numPathPoints: Int, curDir: idVec2?): Float {
        var i: Int
        var pathLength: Float

        // calculate the path length
        pathLength = 0.0f
        i = 0
        while (i < numPathPoints - 1) {
            pathLength += optimizedPath.get(i + 1).oMinus(optimizedPath.get(i)).LengthFast()
            i++
        }

        // add penalty if this path does not go in the current direction
        if (curDir.oMultiply(optimizedPath.get(1).oMinus(optimizedPath.get(0))) < 0.0f) {
            pathLength += 100.0f
        }
        return pathLength
    }

    /*
     ============
     FindOptimalPath

     Returns true if there is a path all the way to the goal.
     ============
     */
    fun FindOptimalPath(
        root: pathNode_s?,
        obstacles: Array<obstacle_s?>?,
        numObstacles: Int,
        height: Float,
        curDir: idVec3?,
        seekPos: idVec3?
    ): Boolean {
        var i: Int
        var numPathPoints: Int
        var bestNumPathPoints: Int
        var node: pathNode_s?
        var lastNode: pathNode_s?
        var bestNode: pathNode_s?
        val optimizedPath: Array<idVec2?> = idVec2.Companion.generateArray(AI_pathing.MAX_OBSTACLE_PATH)
        var pathLength: Float
        var bestPathLength: Float
        var pathToGoalExists: Boolean
        var optimizedPathCalculated: Boolean
        optimizedPath[1] = idVec2(-107374176, -107374176)
        seekPos.Zero()
        seekPos.z = height
        pathToGoalExists = false
        optimizedPathCalculated = false
        bestNode = root
        //        bestNumPathPoints = 0;
        bestPathLength = idMath.INFINITY
        node = root
        while (node != null) {
            pathToGoalExists = pathToGoalExists or (node.dist < 0.1f)
            if (node.dist <= bestNode.dist) {
                if (Math.abs(node.dist - bestNode.dist) < 0.1f) {
                    if (!optimizedPathCalculated) {
                        bestNumPathPoints =
                            AI_pathing.OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
                        bestPathLength = AI_pathing.PathLength(optimizedPath, bestNumPathPoints, curDir.ToVec2())
                        seekPos.oSet(optimizedPath[1])
                    }
                    numPathPoints = AI_pathing.OptimizePath(root, node, obstacles, numObstacles, optimizedPath)
                    pathLength = AI_pathing.PathLength(optimizedPath, numPathPoints, curDir.ToVec2())
                    if (pathLength < bestPathLength) {
                        bestNode = node
                        bestNumPathPoints = numPathPoints
                        bestPathLength = pathLength
                        seekPos.oSet(optimizedPath[1])
                    }
                    optimizedPathCalculated = true
                } else {
                    bestNode = node
                    optimizedPathCalculated = false
                }
            }
            if (node.children.get(0) != null) {
                node = node.children.get(0)
            } else if (node.children.get(1) != null) {
                node = node.children.get(1)
            } else {
                lastNode = node
                node = node.parent
                while (node != null) {
                    if (node.children.get(1) != null && node.children.get(1) != lastNode) {
                        node = node.children.get(1)
                        break
                    }
                    lastNode = node
                    node = node.parent
                }
            }
        }
        if (!pathToGoalExists) {
            seekPos.oSet(root.children.get(0).pos)
        } else if (!optimizedPathCalculated) {
            AI_pathing.OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
            seekPos.oSet(optimizedPath[1])
        }
        if (SysCvar.ai_showObstacleAvoidance.GetBool()) {
            val start = idVec3()
            val end = idVec3()
            end.z = height + 4.0f
            start.z = end.z
            numPathPoints = AI_pathing.OptimizePath(root, bestNode, obstacles, numObstacles, optimizedPath)
            i = 0
            while (i < numPathPoints - 1) {
                start.oSet(optimizedPath[i])
                end.oSet(optimizedPath[i + 1])
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorCyan, start, end, 1)
                i++
            }
        }
        return pathToGoalExists
    }

    /*
     ===============================================================================

     Path Prediction

     Uses the AAS to quickly and accurately predict a path for a certain
     period of time based on an initial position and velocity.

     ===============================================================================
     */
    /*
     ============
     PathTrace

     Returns true if a stop event was triggered.
     ============
     */
    fun PathTrace(
        ent: idEntity?,
        aas: idAAS?,
        start: idVec3?,
        end: idVec3?,
        stopEvent: Int,
        trace: pathTrace_s?,
        path: predictedPath_s?
    ): Boolean {
        val clipTrace = trace_s()
        val aasTrace = aasTrace_s()

//	memset( &trace, 0, sizeof( trace ) );TODO:
        if (TempDump.NOT(aas) || TempDump.NOT(aas.GetSettings())) {
            Game_local.gameLocal.clip.Translation(
                clipTrace, start, end, ent.GetPhysics().GetClipModel(),
                ent.GetPhysics().GetClipModel().GetAxis(), Game_local.MASK_MONSTERSOLID, ent
            )

            // NOTE: could do (expensive) ledge detection here for when there is no AAS file
            trace.fraction = clipTrace.fraction
            trace.endPos.oSet(clipTrace.endpos)
            trace.normal.oSet(clipTrace.c.normal)
            trace.blockingEntity = Game_local.gameLocal.entities[clipTrace.c.entityNum]
        } else {
            aasTrace.getOutOfSolid = 1 //true;
            if (stopEvent and AI.SE_ENTER_LEDGE_AREA != 0) {
                aasTrace.flags = aasTrace.flags or AASFile.AREA_LEDGE
            }
            if (stopEvent and AI.SE_ENTER_OBSTACLE != 0) {
                aasTrace.travelFlags = aasTrace.travelFlags or AASFile.TFL_INVALID
            }
            aas.Trace(aasTrace, start, end)
            Game_local.gameLocal.clip.TranslationEntities(
                clipTrace, start, aasTrace.endpos, ent.GetPhysics().GetClipModel(),
                ent.GetPhysics().GetClipModel().GetAxis(), Game_local.MASK_MONSTERSOLID, ent
            )
            if (clipTrace.fraction >= 1.0f) {
                trace.fraction = aasTrace.fraction
                trace.endPos.oSet(aasTrace.endpos)
                trace.normal.oSet(aas.GetPlane(aasTrace.planeNum).Normal())
                trace.blockingEntity = Game_local.gameLocal.world
                if (aasTrace.fraction < 1.0f) {
                    if (stopEvent and AI.SE_ENTER_LEDGE_AREA != 0) {
                        if (aas.AreaFlags(aasTrace.blockingAreaNum) and AASFile.AREA_LEDGE != 0) {
                            path.endPos.oSet(trace.endPos)
                            path.endNormal.oSet(trace.normal)
                            path.endEvent = AI.SE_ENTER_LEDGE_AREA
                            path.blockingEntity = trace.blockingEntity
                            if (SysCvar.ai_debugMove.GetBool()) {
                                Game_local.gameRenderWorld.DebugLine(
                                    idDeviceContext.Companion.colorRed,
                                    start,
                                    aasTrace.endpos
                                )
                            }
                            return true
                        }
                    }
                    if (stopEvent and AI.SE_ENTER_OBSTACLE != 0) {
                        if (aas.AreaTravelFlags(aasTrace.blockingAreaNum) and AASFile.TFL_INVALID != 0) {
                            path.endPos.oSet(trace.endPos)
                            path.endNormal.oSet(trace.normal)
                            path.endEvent = AI.SE_ENTER_OBSTACLE
                            path.blockingEntity = trace.blockingEntity
                            if (SysCvar.ai_debugMove.GetBool()) {
                                Game_local.gameRenderWorld.DebugLine(
                                    idDeviceContext.Companion.colorRed,
                                    start,
                                    aasTrace.endpos
                                )
                            }
                            return true
                        }
                    }
                }
            } else {
                trace.fraction = clipTrace.fraction
                trace.endPos.oSet(clipTrace.endpos)
                trace.normal.oSet(clipTrace.c.normal)
                trace.blockingEntity = Game_local.gameLocal.entities[clipTrace.c.entityNum]
            }
        }
        if (trace.fraction >= 1.0f) {
            trace.blockingEntity = null
        }
        return false
    }

    fun Ballistics(
        start: idVec3?,
        end: idVec3?,
        speed: Float,
        gravity: Float,
        bal: Array<ballistics_s?>? /*[2]*/
    ): Int {
        var n: Int
        var i: Int
        val x: Float
        val y: Float
        val a: Float
        val b: Float
        val c: Float
        var d: Float
        val sqrtd: Float
        val inva: Float
        val p = FloatArray(2)
        x = end.ToVec2().oMinus(start.ToVec2()).Length()
        y = end.oGet(2) - start.oGet(2)
        a = 4.0f * y * y + 4.0f * x * x
        b = -4.0f * speed * speed - 4.0f * y * gravity
        c = gravity * gravity
        d = b * b - 4.0f * a * c
        if (d <= 0.0f || a == 0.0f) {
            return 0
        }
        sqrtd = idMath.Sqrt(d)
        inva = 0.5f / a
        p[0] = (-b + sqrtd) * inva
        p[1] = (-b - sqrtd) * inva
        n = 0
        i = 0
        while (i < 2) {
            if (p[i] <= 0.0f) {
                i++
                continue
            }
            d = idMath.Sqrt(p[i])
            bal.get(n).angle =
                Math.atan2((0.5f * (2.0f * y * p[i] - gravity) / d).toDouble(), (d * x).toDouble()).toFloat()
            bal.get(n).time = (x / (Math.cos(bal.get(n).angle.toDouble()) * speed)).toFloat()
            bal.get(n).angle = idMath.AngleNormalize180(Vector.RAD2DEG(bal.get(n).angle))
            n++
            i++
        }
        return n
    }

    /*
     =====================
     HeightForTrajectory

     Returns the maximum hieght of a given trajectory
     =====================
     */
    fun HeightForTrajectory(start: idVec3?, zVel: Float, gravity: Float): Float {
        val maxHeight: Float
        val t: Float
        t = zVel / gravity
        // maximum height of projectile
        maxHeight = start.z - 0.5f * gravity * (t * t)
        return maxHeight
    }

    /*
     ===============================================================================

     Path Prediction

     Uses the AAS to quickly and accurately predict a path for a certain
     period of time based on an initial position and velocity.

     ===============================================================================
     */
    internal class pathTrace_s {
        var blockingEntity: idEntity? = null
        val endPos: idVec3? = idVec3()
        var fraction = 0f
        val normal: idVec3? = idVec3()
    }

    internal class obstacle_s {
        var bounds: Array<idVec2?>? = idVec2.Companion.generateArray(2)
        var entity: idEntity? = null
        var winding: idWinding2D? = idWinding2D()
    }

    /*
     ===============================================================================

     Trajectory Prediction

     Finds the best collision free trajectory for a clip model based on an
     initial position, target position and speed.

     ===============================================================================
     */
    internal class pathNode_s {
        var children: Array<pathNode_s?>? = arrayOfNulls<pathNode_s?>(2)
        var delta: idVec2?
        var dir = 0
        var dist = 0f
        var edgeNum = 0
        var next: pathNode_s? = null
        var numNodes = 0
        var obstacle = 0
        var parent: pathNode_s? = null
        var pos: idVec2?
        fun Init() {
            dir = 0
            pos.Zero()
            delta.Zero()
            obstacle = -1
            edgeNum = -1
            numNodes = 0
            next = null
            children.get(1) = next
            children.get(0) = children.get(1)
            parent = children.get(0)
        }

        private fun oSet(parent: pathNode_s?) { //TODO:how do we reference the non objects?
            dir = parent.dir
            pos = parent.pos
            delta = parent.delta
            dist = parent.dist
            obstacle = parent.obstacle
            edgeNum = parent.edgeNum
            numNodes = parent.numNodes
            this.parent = parent.parent
            children = parent.children
            next = parent.next
        }

        init {
            pos = idVec2()
            delta = idVec2()
            AI_pathing.pathNodeAllocator++
        }
    }

    internal class wallEdge_s {
        var edgeNum = 0
        var next: AI_pathing.wallEdge_s? = null
        var verts: IntArray? = IntArray(2)
    }

    /*
     =====================
     Ballistics

     get the ideal aim pitch angle in order to hit the target
     also get the time it takes for the projectile to arrive at the target
     =====================
     */
    internal class ballistics_s {
        var angle // angle in degrees in the range [-180, 180]
                = 0f
        var time // time it takes before the projectile arrives
                = 0f
    }
}