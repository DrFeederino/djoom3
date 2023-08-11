package neo.idlib.geometry

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Lib.idLib
import neo.idlib.containers.CFloat
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.Square
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Pluecker.idPluecker
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec5
import kotlin.math.abs

/**
 *
 */
object Winding {
    /*
     ===============================================================================

     idFixedWinding is a fixed buffer size winding not using
     memory allocations.

     When an operation would overflow the fixed buffer a warning
     is printed and the operation is safely cancelled.

     ===============================================================================
     */
    const val MAX_POINTS_ON_WINDING = 64

    /*
     ===============================================================================

     A winding is an arbitrary convex polygon defined by an array of points.

     ===============================================================================
     */
    open class idWinding {
        protected var allocedSize = 0

        //
        //
        protected var numPoints // number of points
                : Int
        protected var p // pointer to point data
                : Array<idVec5> = emptyArray()
        private var NULL =
            true // used to identify whether any value was assigned. used in combination with idWinding.Split(....);

        constructor() {
            allocedSize = 0
            numPoints = allocedSize
            p = emptyArray()
        }

        // allocate for n points
        constructor(n: Int) {
            allocedSize = 0
            numPoints = allocedSize
            p = emptyArray()
            EnsureAlloced(n)
        }

        // winding from points
        constructor(verts: Array<idVec3>, n: Int) {
            var i: Int
            allocedSize = 0
            numPoints = allocedSize
            p = emptyArray()
            if (!EnsureAlloced(n)) {
                numPoints = 0
                return
            }
            i = 0
            while (i < n) {
                p[i].set(verts[i])
                p[i].t = 0.0f
                p[i].s = p[i].t
                i++
            }
            numPoints = n
        }

        // base winding for plane
        constructor(normal: idVec3, dist: Float) {
            allocedSize = 0
            numPoints = allocedSize
            BaseForPlane(normal, dist)
        }

        // base winding for plane
        constructor(plane: idPlane) {
            allocedSize = 0
            numPoints = allocedSize
            BaseForPlane(plane)
        }

        constructor(winding: idWinding) {
            var i: Int
            if (!EnsureAlloced(winding.GetNumPoints())) {
                numPoints = 0
                return
            }
            i = 0
            while (i < winding.GetNumPoints()) {
                p[i] = idVec5(winding[i])
                i++
            }
            numPoints = winding.GetNumPoints()
        }

        //public				~idWinding();
        //
        open fun set(winding: idWinding): idWinding {
            var i: Int
            NULL = false
            if (!EnsureAlloced(winding.numPoints)) {
                numPoints = 0
                return this
            }
            i = 0
            while (i < winding.numPoints) {
                p[i] = idVec5(winding.p[i])
                i++
            }
            numPoints = winding.numPoints
            return this
        }

        //public	final idVec5 	operator[]( final int index ) ;
        operator fun get(index: Int): idVec5 {
            return p[index]
        }

        operator fun get(index: Int, index2: Int): Float {
            return p[index][index2]
        }

        operator fun set(index: Int, value: idVec5): idVec5 {
            return value.also { p[index] = it }
        }

        operator fun set(index: Int, value: idVec3): idVec5 {
            if (p.size >= index) {
                p[index] = idVec5() //lazy init.
            }
            return p[index].set(value)
        }

        // add a point to the end of the winding point array
        fun plusAssign(v: idVec3): idWinding {
            AddPoint(v)
            return this
        }

        fun plusAssign(v: idVec5): idWinding {
            AddPoint(v)
            return this
        }

        fun AddPoint(v: idVec3) {
            if (!EnsureAlloced(numPoints + 1, true)) {
                return
            }
            p[numPoints].set(v)
            numPoints++
        }

        fun AddPoint(v: idVec5) {
            if (!EnsureAlloced(numPoints + 1, true)) {
                return
            }
            p[numPoints] = v
            numPoints++
        }

        // number of points on winding
        fun GetNumPoints(): Int {
            return numPoints
        }

        fun SetNumPoints(n: Int) {
            if (!EnsureAlloced(n, true)) {
                return
            }
            numPoints = n
        }

        open fun Clear() {
            numPoints = 0
            //	delete[] p;
            p = emptyArray()
        }

        // huge winding for plane, the points go counter clockwise when facing the front of the plane
        fun BaseForPlane(normal: idVec3, dist: Float) {
            val org = idVec3()
            val vright = idVec3()
            val vup = idVec3()
            org.set(normal * dist)
            normal.NormalVectors(vup, vright)
            vup.timesAssign(Lib.MAX_WORLD_SIZE.toFloat())
            vright.timesAssign(Lib.MAX_WORLD_SIZE.toFloat())
            EnsureAlloced(4)
            numPoints = 4
            p[0].set(idVec5(org - vright + vup))
            p[0].t = 0.0f
            p[0].s = p[0].t
            p[1].set(idVec5(org + vright + vup))
            p[1].t = 0.0f
            p[1].s = p[1].t
            p[2].set(idVec5(org + vright - vup))
            p[2].t = 0.0f
            p[2].s = p[2].t
            p[3].set(idVec5(org - vright - vup))
            p[3].t = 0.0f
            p[3].s = p[3].t
        }

        fun BaseForPlane(plane: idPlane) {
            BaseForPlane(plane.Normal(), plane.Dist())
        }

        // splits the winding into a front and back winding, the winding itself stays unchanged
        // returns a SIDE_
        fun Split(plane: idPlane, epsilon: Float, front: idWinding, back: idWinding): Int {
            val dists: FloatArray
            val sides: IntArray
            val counts = IntArray(3)
            var dot: Float
            var i: Int
            var j: Int
            val mid = idVec5()
            var f: idWinding
            var b: idWinding
            val maxpts: Int

//	assert( this );
            dists = FloatArray(numPoints + 4)
            sides = IntArray(numPoints + 4)
            counts[2] = 0
            counts[1] = counts[2]
            counts[0] = counts[1]

            // determine sides for each point
            i = 0
            while (i < numPoints) {
                dot = plane.Distance(p[i].ToVec3())
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            dists[i] = dists[0]

//            front[0] = back[0] = null;//TODO:check the double pointers!!!
            //
            // if coplanar, put on the front side if the normals match
            if (0 == counts[Plane.SIDE_FRONT] && 0 == counts[Plane.SIDE_BACK]) {
                val windingPlane = idPlane()
                GetPlane(windingPlane)
                return if (windingPlane.Normal() * plane.Normal() > 0.0f) {
                    front.set(Copy())
                    Plane.SIDE_FRONT
                } else {
                    back.set(Copy())
                    Plane.SIDE_BACK
                }
            }
            // if nothing at the front of the clipping plane
            if (0 == counts[Plane.SIDE_FRONT]) {
                back.set(Copy())
                return Plane.SIDE_BACK
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[Plane.SIDE_BACK]) {
                front.set(Copy())
                return Plane.SIDE_FRONT
            }
            maxpts = numPoints + 4 // cant use counts[0]+2 because of fp grouping errors
            front.set(idWinding(maxpts).also { f = it })
            back.set(idWinding(maxpts).also { b = it })
            i = 0
            while (i < numPoints) {
                val p1 = p[i]
                if (sides[i] == Plane.SIDE_ON) {
                    f.p[f.numPoints].set(p1)
                    f.numPoints++
                    b.p[b.numPoints].set(p1)
                    b.numPoints++
                    i++
                    continue
                }
                if (sides[i] == Plane.SIDE_FRONT) {
                    f.p[f.numPoints] = p1
                    f.numPoints++
                }
                if (sides[i] == Plane.SIDE_BACK) {
                    b.p[b.numPoints] = p1
                    b.numPoints++
                }
                if (sides[i + 1] == Plane.SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }

                // generate a split point
                val p2 = p[(i + 1) % numPoints]

                // always calculate the split going from the same side
                // or minor epsilon issues can happen
                if (sides[i] == Plane.SIDE_FRONT) {
                    dot = dists[i] / (dists[i] - dists[i + 1])
                    j = 0
                    while (j < 3) {

                        // avoid round off error when possible
                        if (plane.Normal()[j] == 1.0f) {
                            mid[j] = plane.Dist()
                        } else if (plane.Normal()[j] == -1.0f) {
                            mid[j] = -plane.Dist()
                        } else {
                            mid[j] = p1[j] + dot * (p2[j] - p1[j])
                        }
                        j++
                    }
                    mid.s = p1.s + dot * (p2.s - p1.s)
                    mid.t = p1.t + dot * (p2.t - p1.t)
                } else {
                    dot = dists[i + 1] / (dists[i + 1] - dists[i])
                    j = 0
                    while (j < 3) {

                        // avoid round off error when possible
                        if (plane.Normal()[j] == 1.0f) {
                            mid[j] = plane.Dist()
                        } else if (plane.Normal()[j] == -1.0f) {
                            mid[j] = -plane.Dist()
                        } else {
                            mid[j] = p2[j] + dot * (p1[j] - p2[j])
                        }
                        j++
                    }
                    mid.s = p2.s + dot * (p1.s - p2.s)
                    mid.t = p2.t + dot * (p1.t - p2.t)
                }
                f.p[f.numPoints] = mid
                f.numPoints++
                b.p[b.numPoints] = mid
                b.numPoints++
                i++
            }
            if (f.numPoints > maxpts || b.numPoints > maxpts) {
                idLib.common.FatalError("idWinding::Split: points exceeded estimate.")
            }
            return Plane.SIDE_CROSS
        }

        // returns the winding fragment at the front of the clipping plane,
        // if there is nothing at the front the winding itself is destroyed and NULL is returned

        fun Clip(plane: idPlane, epsilon: Float = Plane.ON_EPSILON, keepOn: Boolean = false): idWinding? {
            val dists: FloatArray
            val sides: IntArray
            val newPoints: Array<idVec5>
            var newNumPoints: Int
            val counts = IntArray(3)
            var dot: Float
            var i: Int
            var j: Int
            var p1: idVec5
            var p2: idVec5
            val mid = idVec5()
            val maxpts: Int

//	assert( this );
            dists = FloatArray(numPoints + 4)
            sides = IntArray(numPoints + 4)
            counts[Plane.SIDE_ON] = 0
            counts[Plane.SIDE_BACK] = counts[Plane.SIDE_ON]
            counts[Plane.SIDE_FRONT] = counts[Plane.SIDE_BACK]

            // determine sides for each point
            i = 0
            while (i < numPoints) {
                dot = plane.Distance(p[i].ToVec3())
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            dists[i] = dists[0]

            // if the winding is on the plane and we should keep it
            if (keepOn && 0 == counts[Plane.SIDE_FRONT] && 0 == counts[Plane.SIDE_BACK]) {
                return this
            }
            // if nothing at the front of the clipping plane
            if (0 == counts[Plane.SIDE_FRONT]) {
//		delete this;
                return null
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[Plane.SIDE_BACK]) {
                return this
            }
            maxpts = numPoints + 4 // cant use counts[0]+2 because of fp grouping errors
            newPoints = idVec5.generateArray(maxpts)
            newNumPoints = 0
            i = 0
            while (i < numPoints) {
                p1 = p[i]
                if (newNumPoints + 1 > maxpts) {
                    return this // can't split -- fall back to original
                }
                if (sides[i] == Plane.SIDE_ON) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                    i++
                    continue
                }
                if (sides[i] == Plane.SIDE_FRONT) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                }
                if (sides[i + 1] == Plane.SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }
                if (newNumPoints + 1 > maxpts) {
                    return this // can't split -- fall back to original
                }

                // generate a split point
                p2 = p[(i + 1) % numPoints]
                dot = dists[i] / (dists[i] - dists[i + 1])
                j = 0
                while (j < 3) {

                    // avoid round off error when possible
                    if (plane.Normal()[j] == 1.0f) {
                        mid[j] = plane.Dist()
                    } else if (plane.Normal()[j] == -1.0f) {
                        mid[j] = -plane.Dist()
                    } else {
                        mid[j] = p1[j] + dot * (p2[j] - p1[j])
                    }
                    j++
                }
                mid.s = p1.s + dot * (p2.s - p1.s)
                mid.t = p1.t + dot * (p2.t - p1.t)
                newPoints[newNumPoints] = mid
                newNumPoints++
                i++
            }
            if (!EnsureAlloced(newNumPoints, false)) {
                return this
            }
            numPoints = newNumPoints
            i = 0
            while (i < newNumPoints) {
                p[i] = newPoints[i]
                i++
            }
            return this
        }

        // cuts off the part at the back side of the plane, returns true if some part was at the front
        // if there is nothing at the front the number of points is set to zero

        fun ClipInPlace(plane: idPlane, epsilon: Float = Plane.ON_EPSILON, keepOn: Boolean = false): Boolean {
            val dists: FloatArray
            val sides: IntArray
            val newPoints: Array<idVec5>
            var newNumPoints: Int
            val counts = IntArray(3)
            var dot: Float
            var i: Int
            var j: Int
            var p1: idVec5
            var p2: idVec5
            val mid = idVec5()
            val maxpts: Int

//	assert( this );
            dists = FloatArray(numPoints + 4)
            sides = IntArray(numPoints + 4)
            counts[Plane.SIDE_ON] = 0
            counts[Plane.SIDE_BACK] = counts[Plane.SIDE_ON]
            counts[Plane.SIDE_FRONT] = counts[Plane.SIDE_BACK]

            // determine sides for each point
            i = 0
            while (i < numPoints) {
                dot = plane.Distance(p[i].ToVec3())
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            sides[i] = sides[0]
            dists[i] = dists[0]

            // if the winding is on the plane and we should keep it
            if (keepOn && 0 == counts[Plane.SIDE_FRONT] && 0 == counts[Plane.SIDE_BACK]) {
                return true
            }
            // if nothing at the front of the clipping plane
            if (0 == counts[Plane.SIDE_FRONT]) {
                numPoints = 0
                return false
            }
            // if nothing at the back of the clipping plane
            if (0 == counts[Plane.SIDE_BACK]) {
                return true
            }
            maxpts = numPoints + 4 // cant use counts[0]+2 because of fp grouping errors
            newPoints = idVec5.generateArray(maxpts)
            newNumPoints = 0
            i = 0
            while (i < numPoints) {
                p1 = p[i]
                if (newNumPoints + 1 > maxpts) {
                    return true // can't split -- fall back to original
                }
                if (sides[i] == Plane.SIDE_ON) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                    i++
                    continue
                }
                if (sides[i] == Plane.SIDE_FRONT) {
                    newPoints[newNumPoints].set(p1)
                    newNumPoints++
                }
                if (sides[i + 1] == Plane.SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }
                if (newNumPoints + 1 > maxpts) {
                    return true // can't split -- fall back to original
                }

                // generate a split point
                p2 = p[(i + 1) % numPoints]
                dot = dists[i] / (dists[i] - dists[i + 1])
                j = 0
                while (j < 3) {

                    // avoid round off error when possible
                    if (plane.Normal()[j] == 1.0f) {
                        mid[j] = plane.Dist()
                    } else if (plane.Normal()[j] == -1.0f) {
                        mid[j] = -plane.Dist()
                    } else {
                        mid[j] = p1[j] + dot * (p2[j] - p1[j])
                    }
                    j++
                }
                mid.s = p1.s + dot * (p2.s - p1.s)
                mid.t = p1.t + dot * (p2.t - p1.t)
                newPoints[newNumPoints].set(mid)
                newNumPoints++
                i++
            }
            if (!EnsureAlloced(newNumPoints, false)) {
                return true
            }
            numPoints = newNumPoints
            i = 0
            while (i < newNumPoints) {
                p[i] = idVec5(newPoints[i])
                i++
            }
            return true
        }

        //
        // returns a copy of the winding
        fun Copy(): idWinding {
            val w: idWinding
            w = idWinding(numPoints)
            w.numPoints = numPoints
            for (i in 0 until numPoints) {
                w.p[i] = idVec5(p[i])
            }
            return w
        }

        fun Reverse(): idWinding {
            val w: idWinding
            var i: Int
            w = idWinding(numPoints)
            w.numPoints = numPoints
            i = 0
            while (i < numPoints) {
                w.p[numPoints - i - 1] = p[i]
                i++
            }
            return w
        }

        fun ReverseSelf() {
            for (i in 0 until (numPoints shr 1)) {
                val v = idVec5(p[i])
                p[i] = p[numPoints - i - 1]
                p[numPoints - i - 1] = v
            }
        }


        fun RemoveEqualPoints(epsilon: Float = Plane.ON_EPSILON) {
            var i: Int
            var j: Int
            i = 0
            while (i < numPoints) {
                if ((p[i].ToVec3() - p[(i + numPoints - 1) % numPoints].ToVec3()).LengthSqr() >= Square(epsilon)
                ) {
                    i++
                    continue
                }
                numPoints--
                j = i
                while (j < numPoints) {
                    p[j] = p[j + 1]
                    j++
                }
                i--
                i++
            }
        }


        fun RemoveColinearPoints(normal: idVec3, epsilon: Float = Plane.ON_EPSILON) {
            var i: Int
            var j: Int
            val edgeNormal = idVec3()
            var dist: Float
            if (numPoints <= 3) {
                return
            }
            i = 0
            while (i < numPoints) {
                // create plane through edge orthogonal to winding plane
                edgeNormal.set((p[i].ToVec3() - p[(i + numPoints - 1) % numPoints].ToVec3()).Cross(normal))
                edgeNormal.Normalize()
                dist = edgeNormal * p[i].ToVec3()
                if (abs(edgeNormal * p[(i + 1) % numPoints].ToVec3() - dist) > epsilon) {
                    i++
                    continue
                }
                numPoints--
                j = i
                while (j < numPoints) {
                    p[j] = p[j + 1]
                    j++
                }
                i--
                i++
            }
        }

        fun RemovePoint(point: Int) {
            if (point < 0 || point >= numPoints) {
                idLib.common.FatalError("idWinding::removePoint: point out of range")
            }
            if (point < numPoints - 1) {
//		memmove(&p[point], &p[point+1], (numPoints - point - 1) * sizeof(p[0]) );
                p[point] = p[point + 1]
            }
            numPoints--
        }

        fun InsertPoint(point: idVec3, spot: Int) {
            var i: Int
            if (spot > numPoints) {
                idLib.common.FatalError("idWinding::insertPoint: spot > numPoints")
            }
            if (spot < 0) {
                idLib.common.FatalError("idWinding::insertPoint: spot < 0")
            }
            EnsureAlloced(numPoints + 1, true)
            i = numPoints
            while (i > spot) {
                p[i] = p[i - 1]
                i--
            }
            p[spot].set(point)
            numPoints++
        }


        fun InsertPointIfOnEdge(point: idVec3, plane: idPlane, epsilon: Float = Plane.ON_EPSILON): Boolean {
            var dist: Float
            var dot: Float
            val normal = idVec3()

            // point may not be too far from the winding plane
            if (abs(plane.Distance(point)) > epsilon) {
                return false
            }
            for (i in 0..numPoints) {
                // create plane through edge orthogonal to winding plane
                normal.set((p[(i + 1) % numPoints].ToVec3() - p[i].ToVec3()).Cross(plane.Normal()))
                normal.Normalize()
                dist = normal * p[i].ToVec3()
                if (abs(normal * point - dist) > epsilon) {
                    continue
                }
                normal.set(plane.Normal().Cross(normal))
                dot = normal * point
                dist = dot - normal * p[i].ToVec3()
                if (dist < epsilon) {
                    // if the winding already has the point
                    if (dist > -epsilon) {
                        return false
                    }
                    continue
                }
                dist = dot - normal * p[(i + 1) % numPoints].ToVec3()
                if (dist > -epsilon) {
                    // if the winding already has the point
                    if (dist < epsilon) {
                        return false
                    }
                    continue
                }
                InsertPoint(point, i + 1)
                return true
            }
            return false
        }

        /*
         =============
         idWinding::AddToConvexHull

         Adds the given winding to the convex hull.
         Assumes the current winding already is a convex hull with three or more points.
         =============
         */
        // add a winding to the convex hull

        fun AddToConvexHull(
            winding: idWinding?,
            normal: idVec3,
            epsilon: Float = Plane.ON_EPSILON
        ) { // add a winding to the convex hull
            var i: Int
            var j: Int
            var k: Int
            val dir = idVec3()
            var d: Float
            val maxPts: Int
            val hullSide: BooleanArray
            var outside: Boolean
            var numNewHullPoints: Int
            val newHullPoints: Array<idVec5>
            if (null == winding) {
                return
            }
            maxPts = numPoints + winding.numPoints
            if (!EnsureAlloced(maxPts, true)) {
                return
            }
            newHullPoints = idVec5.generateArray(maxPts)
            val hullDirs: Array<idVec3> = idVec3.generateArray(maxPts)
            hullSide = BooleanArray(maxPts)
            i = 0
            while (i < winding.numPoints) {
                val p1 = winding.p[i]

                // calculate hull edge vectors
                j = 0
                while (j < numPoints) {
                    dir.set(p[(j + 1) % numPoints].ToVec3() - p[j].ToVec3())
                    dir.Normalize()
                    hullDirs[j].set(normal.Cross(dir))
                    j++
                }

                // calculate side for each hull edge
                outside = false
                j = 0
                while (j < numPoints) {
                    dir.set(p1.ToVec3() - p[j].ToVec3())
                    d = dir * hullDirs[j]
                    if (d >= epsilon) {
                        outside = true
                    }
                    hullSide[j] = d >= -epsilon
                    j++
                }

                // if the point is effectively inside, do nothing
                if (!outside) {
                    i++
                    continue
                }

                // find the back side to front side transition
                j = 0
                while (j < numPoints) {
                    if (!hullSide[j] && hullSide[(j + 1) % numPoints]) {
                        break
                    }
                    j++
                }
                if (j >= numPoints) {
                    i++
                    continue
                }

                // insert the point here
                newHullPoints[0].set(p1)
                numNewHullPoints = 1

                // copy over all points that aren't double fronts
                j = (j + 1) % numPoints
                k = 0
                while (k < numPoints) {
                    if (hullSide[(j + k) % numPoints] && hullSide[(j + k + 1) % numPoints]) {
                        k++
                        continue
                    }
                    newHullPoints[numNewHullPoints].set(p[(j + k + 1) % numPoints])
                    numNewHullPoints++
                    k++
                }
                numPoints = numNewHullPoints
                i = 0
                while (i < numNewHullPoints) {
                    p[i].set(newHullPoints[i])
                    i++
                }
                i++
            }
        }

        /*
         =============
         idWinding::AddToConvexHull

         Add a point to the convex hull.
         The current winding must be convex but may be degenerate and can have less than three points.
         =============
         */
        // add a point to the convex hull

        fun AddToConvexHull(
            point: idVec3,
            normal: idVec3,
            epsilon: Float = Plane.ON_EPSILON
        ) { // add a point to the convex hull
            var j: Int
            var k: Int
            var numHullPoints: Int
            val dir = idVec3()
            var d: Float
            val hullSide: BooleanArray
            val hullPoints: Array<idVec5>
            var outside: Boolean
            when (numPoints) {
                0 -> {
                    p[0] = idVec5(point)
                    numPoints++
                    return
                }
                1 -> {

                    // don't add the same point second
                    if (p[0].ToVec3().Compare(point, epsilon)) {
                        return
                    }
                    p[1] = idVec5(point)
                    numPoints++
                    return
                }
                2 -> {

                    // don't add a point if it already exists
                    if (p[0].ToVec3().Compare(point, epsilon) || p[1].ToVec3().Compare(point, epsilon)) {
                        return
                    }
                    // if only two points make sure we have the right ordering according to the normal
                    dir.set(point - p[0].ToVec3())
                    dir.set(dir.Cross(p[1].ToVec3() - p[0].ToVec3()))
                    if (dir[0] == 0.0f && dir[1] == 0.0f && dir[2] == 0.0f) {
                        // points don't make a plane
                        return
                    }
                    if (dir * normal > 0.0f) {
                        p[2].set(point)
                    } else {
                        p[2].set(p[1])
                        p[1].set(point)
                    }
                    numPoints++
                    return
                }
            }
            val hullDirs: Array<idVec3> = idVec3.generateArray(numPoints)
            hullSide = BooleanArray(numPoints)

            // calculate hull edge vectors
            j = 0
            while (j < numPoints) {
                dir.set(p[(j + 1) % numPoints].ToVec3() - p[j].ToVec3())
                hullDirs[j].set(normal.Cross(dir))
                j++
            }

            // calculate side for each hull edge
            outside = false
            j = 0
            while (j < numPoints) {
                dir.set(point - p[j].ToVec3())
                d = dir * hullDirs[j]
                if (d >= epsilon) {
                    outside = true
                }
                hullSide[j] = d >= -epsilon
                j++
            }

            // if the point is effectively inside, do nothing
            if (!outside) {
                return
            }

            // find the back side to front side transition
            j = 0
            while (j < numPoints) {
                if (!hullSide[j] && hullSide[(j + 1) % numPoints]) {
                    break
                }
                j++
            }
            if (j >= numPoints) {
                return
            }
            hullPoints = idVec5.generateArray(numPoints + 1)

            // insert the point here
            hullPoints[0] = idVec5(point)
            numHullPoints = 1

            // copy over all points that aren't double fronts
            j = (j + 1) % numPoints
            k = 0
            while (k < numPoints) {
                if (hullSide[(j + k) % numPoints] && hullSide[(j + k + 1) % numPoints]) {
                    k++
                    continue
                }
                hullPoints[numHullPoints].set(p[(j + k + 1) % numPoints])
                numHullPoints++
                k++
            }
            if (!EnsureAlloced(numHullPoints, false)) {
                return
            }
            numPoints = numHullPoints
            //	memcpy( p, hullPoints, numHullPoints * sizeof(idVec5) );
            System.arraycopy(hullPoints, 0, p, 0, numHullPoints)
        }

        // tries to merge 'this' with the given winding, returns NULL if merge fails, both 'this' and 'w' stay intact
        // 'keep' tells if the contacting points should stay even if they create colinear edges

        fun TryMerge(w: idWinding, planenormal: idVec3, keep: Int = 0): idWinding? {
            val p1 = idVec3()
            val p2 = idVec3()
            val p3 = idVec3()
            val p4 = idVec3()
            val back = idVec3()
            val newf: idWinding
            val f1: idWinding
            val f2: idWinding
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val normal = idVec3()
            val delta = idVec3()
            var dot: Float
            val keep1: Boolean
            val keep2: Boolean
            f1 = this
            f2 = idWinding(w)
            //
            // find a idLib::common edge
            //
            j = 0
            i = 0
            while (i < f1.numPoints) {
                p1.set(f1.p[i].ToVec3())
                p2.set(f1.p[(i + 1) % f1.numPoints].ToVec3())
                j = 0
                while (j < f2.numPoints) {
                    p3.set(f2.p[j].ToVec3())
                    p4.set(f2.p[(j + 1) % f2.numPoints].ToVec3())
                    k = 0
                    while (k < 3) {
                        if (abs(p1[k] - p4[k]) > 0.1f) {
                            break
                        }
                        if (abs(p2[k] - p3[k]) > 0.1f) {
                            break
                        }
                        k++
                    }
                    if (k == 3) {
                        break
                    }
                    j++
                }
                if (j < f2.numPoints) {
                    break
                }
                i++
            }
            if (i == f1.numPoints) {
                return null // no matching edges
            }

            //
            // check slope of connected lines
            // if the slopes are colinear, the point can be removed
            //
            back.set(f1.p[(i + f1.numPoints - 1) % f1.numPoints].ToVec3())
            delta.set(p1 - back)
            normal.set(planenormal.Cross(delta))
            normal.Normalize()
            back.set(f2.p[(j + 2) % f2.numPoints].ToVec3())
            delta.set(back - p1)
            dot = delta * normal
            if (dot > CONTINUOUS_EPSILON) {
                return null // not a convex polygon
            }
            keep1 = dot < -CONTINUOUS_EPSILON
            back.set(f1.p[(i + 2) % f1.numPoints].ToVec3())
            delta.set(back - p2)
            normal.set(planenormal.Cross(delta))
            normal.Normalize()
            back.set(f2.p[(j + f2.numPoints - 1) % f2.numPoints].ToVec3())
            delta.set(back - p2)
            dot = delta * normal
            if (dot > CONTINUOUS_EPSILON) {
                return null // not a convex polygon
            }
            keep2 = dot < -CONTINUOUS_EPSILON

            //
            // build the new polygon
            //
            newf = idWinding(f1.numPoints + f2.numPoints)

            // copy first polygon
            k = (i + 1) % f1.numPoints
            while (k != i) {
                if (0 == keep && k == (i + 1) % f1.numPoints && !keep2) {
                    k = (k + 1) % f1.numPoints
                    continue
                }
                newf.p[newf.numPoints] = f1.p[k]
                newf.numPoints++
                k = (k + 1) % f1.numPoints
            }

            // copy second polygon
            l = (j + 1) % f2.numPoints
            while (l != j) {
                if (0 == keep && l == (j + 1) % f2.numPoints && !keep1) {
                    l = (l + 1) % f2.numPoints
                    continue
                }
                newf.p[newf.numPoints] = f2.p[l]
                newf.numPoints++
                l = (l + 1) % f2.numPoints
            }
            return newf
        }

        // check whether the winding is valid or not

        fun Check(print: Boolean = true): Boolean {
            var i: Int
            var j: Int
            var d: Float
            var edgedist: Float
            val dir = idVec3()
            val edgenormal = idVec3()
            val area: Float
            val plane = idPlane()
            if (numPoints < 3) {
                if (print) {
                    idLib.common.Printf("idWinding::Check: only %d points.", numPoints)
                }
                return false
            }
            area = GetArea()
            if (area < 1.0f) {
                if (print) {
                    idLib.common.Printf("idWinding::Check: tiny area: %f", area)
                }
                return false
            }
            GetPlane(plane)
            i = 0
            while (i < numPoints) {
                val p1 = p[i].ToVec3()

                // check if the winding is huge
                j = 0
                while (j < 3) {
                    if (p1[j] >= Lib.MAX_WORLD_COORD || p1[j] <= Lib.MIN_WORLD_COORD) {
                        if (print) {
                            idLib.common.Printf(
                                "idWinding::Check: point %d outside world %c-axis: %f",
                                i,
                                'X'.code + j,
                                p1[j]
                            )
                        }
                        return false
                    }
                    j++
                }
                j = if (i + 1 == numPoints) 0 else i + 1

                // check if the point is on the face plane
                d = p1 * plane.Normal() + plane[3]
                if (d < -Plane.ON_EPSILON || d > Plane.ON_EPSILON) {
                    if (print) {
                        idLib.common.Printf("idWinding::Check: point %d off plane.", i)
                    }
                    return false
                }

                // check if the edge isn't degenerate
                val p2 = p[j].ToVec3()
                dir.set(p2 - p1)
                if (dir.Length() < Plane.ON_EPSILON) {
                    if (print) {
                        idLib.common.Printf("idWinding::Check: edge %d is degenerate.", i)
                    }
                    return false
                }

                // check if the winding is convex
                edgenormal.set(plane.Normal().Cross(dir))
                edgenormal.Normalize()
                edgedist = p1 * edgenormal
                edgedist += Plane.ON_EPSILON

                // all other points must be on front side
                j = 0
                while (j < numPoints) {
                    if (j == i) {
                        j++
                        continue
                    }
                    d = p[j].ToVec3() * edgenormal
                    if (d > edgedist) {
                        if (print) {
                            idLib.common.Printf("idWinding::Check: non-convex.")
                        }
                        return false
                    }
                    j++
                }
                i++
            }
            return true
        }

        fun GetArea(): Float {
            var i: Int
            val d1 = idVec3()
            val d2 = idVec3()
            val cross = idVec3()
            var total: Float
            total = 0.0f
            i = 2
            while (i < numPoints) {
                d1.set(p[i - 1].ToVec3() - p[0].ToVec3())
                d2.set(p[i].ToVec3() - p[0].ToVec3())
                cross.set(d1.Cross(d2))
                total += cross.Length()
                i++
            }
            return total * 0.5f
        }

        fun GetCenter(): idVec3 {
            var i: Int
            val center = idVec3()
            center.Zero()
            i = 0
            while (i < numPoints) {
                center.plusAssign(p[i].ToVec3())
                i++
            }
            center.timesAssign(1.0f / numPoints)
            return center
        }

        fun GetRadius(center: idVec3): Float {
            var i: Int
            var radius: Float
            var r: Float
            val dir = idVec3()
            radius = 0.0f
            i = 0
            while (i < numPoints) {
                dir.set(p[i].ToVec3() - center)
                r = dir * dir
                if (r > radius) {
                    radius = r
                }
                i++
            }
            return idMath.Sqrt(radius)
        }

        fun GetPlane(normal: idVec3, dist: CFloat) {
            val v1 = idVec3()
            val v2 = idVec3()
            val center = idVec3()
            if (numPoints < 3) {
                normal.Zero()
                dist._val = (0.0f)
                return
            }
            center.set(GetCenter())
            v1.set(p[0].ToVec3() - center)
            v2.set(p[1].ToVec3() - center)
            normal.set(v2.Cross(v1))
            normal.Normalize()
            dist._val = p[0].ToVec3() * normal
        }

        fun GetPlane(plane: idPlane) {
            val v1 = idVec3()
            val v2 = idVec3()
            val center = idVec3()
            if (numPoints < 3) {
                plane.Zero()
                return
            }
            center.set(GetCenter())
            v1.set(p[0].ToVec3() - center)
            v2.set(p[1].ToVec3() - center)
            plane.SetNormal(v2.Cross(v1))
            plane.Normalize()
            plane.FitThroughPoint(p[0].ToVec3())
        }

        fun GetBounds(bounds: idBounds) {
            var i: Int
            if (0 == numPoints) {
                bounds.Clear()
                return
            }
            bounds[0] = bounds.set(1, p[0].ToVec3())
            i = 1
            while (i < numPoints) {
                if (p[i].x < bounds[0].x) {
                    bounds[0].x = p[i].x
                } else if (p[i].x > bounds[1].x) {
                    bounds[1].x = p[i].x
                }
                if (p[i].y < bounds[0].y) {
                    bounds[0].y = p[i].y
                } else if (p[i].y > bounds[1].y) {
                    bounds[1].y = p[i].y
                }
                if (p[i].z < bounds[0].z) {
                    bounds[0].z = p[i].z
                } else if (p[i].z > bounds[1].z) {
                    bounds[1].z = p[i].z
                }
                i++
            }
        }

        fun IsTiny(): Boolean {
            var i: Int
            var len: Float
            val delta = idVec3()
            var edges: Int
            edges = 0
            i = 0
            while (i < numPoints) {
                delta.set(p[(i + 1) % numPoints].ToVec3() - p[i].ToVec3())
                len = delta.Length()
                if (len > EDGE_LENGTH) {
                    if (++edges == 3) {
                        return false
                    }
                }
                i++
            }
            return true
        }

        fun IsHuge(): Boolean {    // base winding for a plane is typically huge
            var i: Int
            var j: Int
            i = 0
            while (i < numPoints) {
                j = 0
                while (j < 3) {
                    if (p[i][j] <= Lib.MIN_WORLD_COORD || p[i][j] >= Lib.MAX_WORLD_COORD
                    ) {
                        return true
                    }
                    j++
                }
                i++
            }
            return false
        }

        fun Print() {
            var i: Int
            i = 0
            while (i < numPoints) {
                idLib.common.Printf("(%5.1f, %5.1f, %5.1f)\n", p[i][0], p[i][1], p[i][2])
                i++
            }
        }

        fun PlaneDistance(plane: idPlane): Float {
            var i: Int
            var d: Float
            var min: Float
            var max: Float
            min = idMath.INFINITY
            max = -min
            i = 0
            while (i < numPoints) {
                d = plane.Distance(p[i].ToVec3())
                if (d < min) {
                    min = d
                    if (Math_h.FLOATSIGNBITSET(min) and Math_h.FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                if (d > max) {
                    max = d
                    if (Math_h.FLOATSIGNBITSET(min) and Math_h.FLOATSIGNBITNOTSET(max) != 0) {
                        return 0.0f
                    }
                }
                i++
            }
            if (Math_h.FLOATSIGNBITNOTSET(min) != 0) {
                return min
            }
            return if (Math_h.FLOATSIGNBITSET(max) != 0) {
                max
            } else 0.0f
        }


        fun PlaneSide(plane: idPlane, epsilon: Float = Plane.ON_EPSILON): Int {
            var front: Boolean
            var back: Boolean
            var i: Int
            var d: Float
            front = false
            back = false
            i = 0
            while (i < numPoints) {
                d = plane.Distance(p[i].ToVec3())
                if (d < -epsilon) {
                    if (front) {
                        return Plane.SIDE_CROSS
                    }
                    back = true
                    i++
                    continue
                } else if (d > epsilon) {
                    if (back) {
                        return Plane.SIDE_CROSS
                    }
                    front = true
                    i++
                    continue
                }
                i++
            }
            if (back) {
                return Plane.SIDE_BACK
            }
            return if (front) {
                Plane.SIDE_FRONT
            } else Plane.SIDE_ON
        }

        fun PlanesConcave(w2: idWinding, normal1: idVec3, normal2: idVec3, dist1: Float, dist2: Float): Boolean {
            var i: Int

            // check if one of the points of winding 1 is at the back of the plane of winding 2
            i = 0
            while (i < numPoints) {
                if (normal2 * p[i].ToVec3() - dist2 > WCONVEX_EPSILON) {
                    return true
                }
                i++
            }
            // check if one of the points of winding 2 is at the back of the plane of winding 1
            i = 0
            while (i < w2.numPoints) {
                if (normal1 * w2.p[i].ToVec3() - dist1 > WCONVEX_EPSILON) {
                    return true
                }
                i++
            }
            return false
        }

        fun PointInside(normal: idVec3, point: idVec3, epsilon: Float): Boolean {
            var i: Int
            val dir = idVec3()
            val n = idVec3()
            val pointvec = idVec3()
            i = 0
            while (i < numPoints) {
                dir.set(p[(i + 1) % numPoints].ToVec3() - p[i].ToVec3())
                pointvec.set(point - p[i].ToVec3())
                n.set(dir.Cross(normal))
                if (pointvec * n < -epsilon) {
                    return false
                }
                i++
            }
            return true
        }

        // returns true if the line or ray intersects the winding

        fun LineIntersection(
            windingPlane: idPlane,
            start: idVec3,
            end: idVec3,
            backFaceCull: Boolean = false
        ): Boolean {
            val front: Float
            val back: Float
            val frac: Float
            val mid = idVec3()
            front = windingPlane.Distance(start)
            back = windingPlane.Distance(end)

            // if both points at the same side of the plane
            if (front < 0.0f && back < 0.0f) {
                return false
            }
            if (front > 0.0f && back > 0.0f) {
                return false
            }

            // if back face culled
            if (backFaceCull && front < 0.0f) {
                return false
            }

            // get point of intersection with winding plane
            if (abs(front - back) < 0.0001f) {
                mid.set(end)
            } else {
                frac = front / (front - back)
                mid[0] = start[0] + (end[0] - start[0]) * frac
                mid[1] = start[1] + (end[1] - start[1]) * frac
                mid[2] = start[2] + (end[2] - start[2]) * frac
            }
            return PointInside(windingPlane.Normal(), mid, 0.0f)
        }

        // intersection point is start + dir * scale

        fun RayIntersection(
            windingPlane: idPlane,
            start: idVec3,
            dir: idVec3,
            scale: CFloat,
            backFaceCull: Boolean = false
        ): Boolean {
            var i: Int
            var side: Boolean
            var lastside = false
            val pl1 = idPluecker()
            val pl2 = idPluecker()
            scale._val = (0.0f)
            pl1.FromRay(start, dir)
            i = 0
            while (i < numPoints) {
                pl2.FromLine(p[i].ToVec3(), p[(i + 1) % numPoints].ToVec3())
                side = pl1.PermutedInnerProduct(pl2) > 0.0f
                if (i != 0 && side != lastside) {
                    return false
                }
                lastside = side
                i++
            }
            if (!backFaceCull || lastside) {
                windingPlane.RayIntersection(start, dir, scale)
                return true
            }
            return false
        }

        protected fun EnsureAlloced(n: Int, keep: Boolean = false): Boolean {
            return if (n > allocedSize) {
                ReAllocate(n, keep)
            } else true
        }

        protected open fun ReAllocate(n: Int): Boolean {
            return ReAllocate(n, false)
        }

        protected open fun ReAllocate(n: Int, keep: Boolean): Boolean {
            var n = n
            val oldP = p
            n = n + 3 and 3.inv() // align up to multiple of four
            p = idVec5.generateArray(n)
            if (oldP.isNotEmpty() && keep) {
//			memcpy( p, oldP, numPoints * sizeof(p[0]) );
                System.arraycopy(oldP, 0, p, 0, numPoints)
            }
            allocedSize = n
            return true
        }

        fun isNULL(): Boolean {
            return NULL
        }

        companion object {
            const val CONTINUOUS_EPSILON = 0.005f

            //
            private const val EDGE_LENGTH = 0.2f

            //
            private const val WCONVEX_EPSILON = 0.2f


            fun TriangleArea(a: idVec3, b: idVec3, c: idVec3): Float {
                val v1 = idVec3()
                val v2 = idVec3()
                val cross = idVec3()
                v1.set(b - a)
                v2.set(c - a)
                cross.set(v1.Cross(v2))
                return 0.5f * cross.Length()
            }
        }
    }

    class idFixedWinding : idWinding {
        protected val data: Array<idVec5> =
            idVec5.generateArray(MAX_POINTS_ON_WINDING) // point data

        constructor() {
            numPoints = 0
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
        }

        constructor(n: Int) {
            numPoints = 0
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
        }

        constructor(verts: Array<idVec3>, n: Int) {
            var i: Int
            numPoints = 0
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
            if (!EnsureAlloced(n)) {
                numPoints = 0
                return
            }
            i = 0
            while (i < n) {
                p[i].set(verts[i])
                p[i].t = 0f
                p[i].s = p[i].t
                i++
            }
            numPoints = n
        }

        constructor(normal: idVec3, dist: Float) {
            numPoints = 0
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
            BaseForPlane(normal, dist)
        }

        constructor(plane: idPlane) {
            numPoints = 0
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
            BaseForPlane(plane)
        }

        constructor(winding: idWinding) {
            var i: Int
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
            if (!EnsureAlloced(winding.GetNumPoints())) {
                numPoints = 0
                return
            }
            i = 0
            while (i < winding.GetNumPoints()) {
                p[i] = idVec5(winding[i])
                i++
            }
            numPoints = winding.GetNumPoints()
        }

        //public	virtual			~idFixedWinding( void );
        //
        constructor(winding: idFixedWinding) {
            var i: Int
            p = data
            allocedSize = MAX_POINTS_ON_WINDING
            if (!EnsureAlloced(winding.GetNumPoints())) {
                numPoints = 0
                return
            }
            i = 0
            while (i < winding.GetNumPoints()) {
                p[i] = idVec5(winding[i])
                i++
            }
            numPoints = winding.GetNumPoints()
        }

        override fun set(winding: idWinding): idFixedWinding {
            var i: Int
            if (!EnsureAlloced(winding.GetNumPoints())) {
                numPoints = 0
                return this
            }
            i = 0
            while (i < winding.GetNumPoints()) {
                p[i] = idVec5(winding[i])
                i++
            }
            numPoints = winding.GetNumPoints()
            return this
        }

        override fun Clear() {
            numPoints = 0
        }

        // splits the winding in a back and front part, 'this' becomes the front part
        // returns a SIDE_

        fun Split(back: idFixedWinding, plane: idPlane, epsilon: Float = Plane.ON_EPSILON): Int {
            val counts = IntArray(3)
            val dists = FloatArray(MAX_POINTS_ON_WINDING + 4)
            val sides = IntArray(MAX_POINTS_ON_WINDING + 4)
            var dot: Float
            var i: Int
            var j: Int
            var p2: idVec5
            val mid = idVec5()
            val out = idFixedWinding()
            counts[Plane.SIDE_ON] = 0
            counts[Plane.SIDE_BACK] = counts[Plane.SIDE_ON]
            counts[Plane.SIDE_FRONT] = counts[Plane.SIDE_BACK]

            // determine sides for each point
            i = 0
            while (i < numPoints) {
                dot = plane.Distance(p[i].ToVec3())
                dists[i] = dot
                if (dot > epsilon) {
                    sides[i] = Plane.SIDE_FRONT
                } else if (dot < -epsilon) {
                    sides[i] = Plane.SIDE_BACK
                } else {
                    sides[i] = Plane.SIDE_ON
                }
                counts[sides[i]]++
                i++
            }
            if (0 == counts[Plane.SIDE_BACK]) {
                return if (0 == counts[Plane.SIDE_FRONT]) {
                    Plane.SIDE_ON
                } else {
                    Plane.SIDE_FRONT
                }
            }
            if (0 == counts[Plane.SIDE_FRONT]) {
                return Plane.SIDE_BACK
            }
            sides[i] = sides[0]
            dists[i] = dists[0]
            out.numPoints = 0
            back.numPoints = 0
            i = 0
            while (i < numPoints) {
                val p1 = p[i]
                if (!out.EnsureAlloced(out.numPoints + 1, true)) {
                    return Plane.SIDE_FRONT // can't split -- fall back to original
                }
                if (!back.EnsureAlloced(back.numPoints + 1, true)) {
                    return Plane.SIDE_FRONT // can't split -- fall back to original
                }
                if (sides[i] == Plane.SIDE_ON) {
                    out.p[out.numPoints].set(p1)
                    out.numPoints++
                    back.p[back.numPoints].set(p1)
                    back.numPoints++
                    i++
                    continue
                }
                if (sides[i] == Plane.SIDE_FRONT) {
                    out.p[out.numPoints].set(p1)
                    out.numPoints++
                }
                if (sides[i] == Plane.SIDE_BACK) {
                    back.p[back.numPoints].set(p1)
                    back.numPoints++
                }
                if (sides[i + 1] == Plane.SIDE_ON || sides[i + 1] == sides[i]) {
                    i++
                    continue
                }
                if (!out.EnsureAlloced(out.numPoints + 1, true)) {
                    return Plane.SIDE_FRONT // can't split -- fall back to original
                }
                if (!back.EnsureAlloced(back.numPoints + 1, true)) {
                    return Plane.SIDE_FRONT // can't split -- fall back to original
                }

                // generate a split point
                j = i + 1
                p2 = if (j >= numPoints) {
                    p[0]
                } else {
                    p[j]
                }
                dot = dists[i] / (dists[i] - dists[i + 1])
                j = 0
                while (j < 3) {

                    // avoid round off error when possible
                    if (plane.Normal()[j] == 1.0f) {
                        mid[j] = plane.Dist()
                    } else if (plane.Normal()[j] == -1.0f) {
                        mid[j] = -plane.Dist()
                    } else {
                        mid[j] = p1[j] + dot * (p2[j] - p1[j])
                    }
                    j++
                }
                mid.s = p1.s + dot * (p2.s - p1.s)
                mid.t = p1.t + dot * (p2.t - p1.t)
                out.p[out.numPoints] = mid
                out.numPoints++
                back.p[back.numPoints] = mid
                back.numPoints++
                i++
            }
            i = 0
            while (i < out.numPoints) {
                p[i] = out.p[i]
                i++
            }
            numPoints = out.numPoints
            return Plane.SIDE_CROSS
        }

        override fun ReAllocate(n: Int): Boolean {
            return ReAllocate(n, false)
        }

        override fun ReAllocate(n: Int, keep: Boolean): Boolean {
            assert(n <= MAX_POINTS_ON_WINDING)
            if (n > MAX_POINTS_ON_WINDING) {
                idLib.common.Printf("WARNING: idFixedWinding -> MAX_POINTS_ON_WINDING overflowed\n")
                return false
            }
            return true
        }
    }
}