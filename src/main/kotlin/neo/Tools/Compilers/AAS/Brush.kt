package neo.Tools.Compilers.AAS

import neo.TempDump
import neo.Tools.Compilers.AAS.AASBuild.Allowance
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.MapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.sys.win_shared
import java.util.*

/**
 *
 */
object Brush {
    //
    const val BFL_NO_VALID_SPLITTERS = 0x0001

    //
    const val BRUSH_BEVEL_EPSILON = 0.1f

    //
    const val BRUSH_EPSILON = 0.1f
    const val BRUSH_PLANESIDE_BACK = 2
    const val BRUSH_PLANESIDE_FACING = 4

    /*
     ===============================================================================

     Brushes

     ===============================================================================
     */
    const val BRUSH_PLANESIDE_FRONT = 1
    const val BRUSH_PLANESIDE_BOTH = Brush.BRUSH_PLANESIDE_FRONT or Brush.BRUSH_PLANESIDE_BACK
    const val BRUSH_PLANE_DIST_EPSILON = 0.01f
    const val BRUSH_PLANE_NORMAL_EPSILON = 0.00001f

    //
    const val OUTPUT_CHOP_STATS = false

    //
    const val OUTPUT_UPDATE_TIME = 500 // update every 500 msec
    const val SFL_BEVEL = 0x0002

    //
    const val SFL_SPLIT = 0x0001
    const val SFL_TESTED_SPLITTER = 0x0008
    const val SFL_USED_SPLITTER = 0x0004
    private const val lastUpdateTime = 0

    /*
     ============
     DisplayRealTimeString
     ============
     */
    fun DisplayRealTimeString(format: String?, vararg args: Any?) {
//        va_list argPtr;
        val buf: String //= new char[MAX_STRING_CHARS];
        val time: Int
        time = win_shared.Sys_Milliseconds()
        if (time > Brush.lastUpdateTime + Brush.OUTPUT_UPDATE_TIME) {
//            va_start(argPtr, string);
//            vsprintf(buf, string, argPtr);
//            va_end(argPtr);
            buf = String.format(format, Arrays.toString(args))
            Common.common.Printf(buf)
            Brush.lastUpdateTime = time
        }
    }

    //===============================================================
    //
    //	idBrushSide
    //
    //===============================================================
    internal class idBrushSide {
        private var flags: Int
        private val plane: idPlane? = idPlane()
        private var planeNum: Int
        private var winding: idWinding?

        // friend class idBrush;
        constructor() {
            flags = 0
            planeNum = -1
            winding = null
        }

        constructor(plane: idPlane?, planeNum: Int) {
            flags = 0
            this.plane.oSet(plane)
            this.planeNum = planeNum
            winding = null
        }

        // ~idBrushSide();
        fun GetFlags(): Int {
            return flags
        }

        fun SetFlag(flag: Int) {
            flags = flags or flag
        }

        fun RemoveFlag(flag: Int) {
            flags = flags and flag.inv()
        }

        fun GetPlane(): idPlane? {
            return plane
        }

        fun SetPlaneNum(num: Int) {
            planeNum = num
        }

        fun GetPlaneNum(): Int {
            return planeNum
        }

        fun GetWinding(): idWinding? {
            return winding
        }

        fun Copy(): idBrushSide? {
            val side: idBrushSide
            side = idBrushSide(plane, planeNum)
            side.flags = flags
            if (winding != null) {
                side.winding = winding.Copy()
            } else {
                side.winding = null
            }
            return side
        }

        fun Split(splitPlane: idPlane?, front: Array<idBrushSide?>?, back: Array<idBrushSide?>?): Int {
            val frontWinding = idWinding()
            val backWinding = idWinding()
            assert(winding != null)
            back.get(0) = null
            front.get(0) = back.get(0)
            winding.Split(splitPlane, 0.0f, frontWinding, backWinding)
            if (!frontWinding.isNULL) {
                front.get(0) = idBrushSide(plane, planeNum)
                front.get(0).winding = frontWinding
                front.get(0).flags = flags
            }
            if (!backWinding.isNULL) {
                back.get(0) = idBrushSide(plane, planeNum)
                back.get(0).winding = backWinding
                back.get(0).flags = flags
            }
            return if (!frontWinding.isNULL && !backWinding.isNULL) {
                Plane.PLANESIDE_CROSS
            } else if (!frontWinding.isNULL) {
                Plane.PLANESIDE_FRONT
            } else {
                Plane.PLANESIDE_BACK
            }
        }
    }

    //===============================================================
    //
    //	idBrush
    //
    //===============================================================
    internal class idBrush {
        private var NULL = true // used with the Split(....), see idWinding for more info.
        private var bounds // brush bounds
                : idBounds? = null
        private var contents // contents of brush
                : Int
        private var entityNum // entity number in editor
                = 0
        private var flags // brush flags
                = 0
        private val next // next brush in list
                : idBrush? = null
        private var planeSide // side of a plane this brush is on
                = 0
        private var primitiveNum // primitive number in editor
                = 0
        private var savedPlaneSide // saved plane side
                = 0
        private val sides: idList<idBrushSide?>? = idList() // list with sides
        private var windingsValid // set when side windings are valid
                : Boolean

        // ~idBrush();
        fun GetFlags(): Int {
            return flags
        }

        fun SetFlag(flag: Int) {
            flags = flags or flag
        }

        fun RemoveFlag(flag: Int) {
            flags = flags and flag.inv()
        }

        fun SetEntityNum(num: Int) {
            entityNum = num
        }

        fun SetPrimitiveNum(num: Int) {
            primitiveNum = num
        }

        fun SetContents(contents: Int) {
            this.contents = contents
        }

        fun GetContents(): Int {
            return contents
        }

        fun GetBounds(): idBounds? {
            return bounds
        }

        fun GetVolume(): Float {
            var i: Int
            var w: idWinding?
            val corner = idVec3()
            var d: Float
            var area: Float
            var volume: Float

            // grab the first valid point as a corner
            w = null
            i = 0
            while (i < sides.Num()) {
                w = sides.oGet(i).winding
                if (w != null) {
                    break
                }
                i++
            }
            if (TempDump.NOT(w)) {
                return 0.0f
            }
            corner.oSet(w.oGet(0).ToVec3())

            // create tetrahedrons to all other sides
            volume = 0.0f
            while (i < sides.Num()) {
                w = sides.oGet(i).winding
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                d = -(corner.times(sides.oGet(i).plane.Normal()) - sides.oGet(i).plane.Dist())
                area = w.GetArea()
                volume += d * area
                i++
            }
            return volume * (1.0f / 3.0f)
        }

        fun GetNumSides(): Int {
            return sides.Num()
        }

        fun GetSide(i: Int): idBrushSide? {
            return sides.oGet(i)
        }

        fun SetPlaneSide(s: Int) {
            planeSide = s
        }

        fun SavePlaneSide() {
            savedPlaneSide = planeSide
        }

        fun GetSavedPlaneSide(): Int {
            return savedPlaneSide
        }

        fun FromSides(sideList: idList<idBrushSide?>?): Boolean {
            var i: Int
            i = 0
            while (i < sideList.Num()) {
                sides.Append(sideList.oGet(i))
                i++
            }
            sideList.Clear()
            return CreateWindings()
        }

        fun FromWinding(w: idWinding?, windingPlane: idPlane?): Boolean {
            var i: Int
            var j: Int
            var bestAxis: Int
            val plane = idPlane()
            val normal = idVec3()
            val axialNormal = idVec3()
            sides.Append(idBrushSide(windingPlane, -1))
            sides.Append(idBrushSide(windingPlane.oNegative(), -1))
            bestAxis = 0
            i = 1
            while (i < 3) {
                if (Math.abs(windingPlane.Normal().oGet(i)) > Math.abs(windingPlane.Normal().oGet(bestAxis))) {
                    bestAxis = i
                }
                i++
            }
            axialNormal.oSet(Vector.getVec3_origin())
            if (windingPlane.Normal().oGet(bestAxis) > 0.0f) {
                axialNormal.oSet(bestAxis, 1.0f)
            } else {
                axialNormal.oSet(bestAxis, -1.0f)
            }
            i = 0
            while (i < w.GetNumPoints()) {
                j = (i + 1) % w.GetNumPoints()
                normal.oSet(w.oGet(j).ToVec3().oMinus(w.oGet(i).ToVec3()).Cross(axialNormal))
                if (normal.Normalize() < 0.5f) {
                    i++
                    continue
                }
                plane.SetNormal(normal)
                plane.FitThroughPoint(w.oGet(j).ToVec3())
                sides.Append(idBrushSide(plane, -1))
                i++
            }
            if (sides.Num() < 4) {
                i = 0
                while (i < sides.Num()) {

//			delete sides[i];
                    sides.oSet(i, null)
                    i++
                }
                sides.Clear()
                return false
            }
            sides.oGet(0).winding = w.Copy()
            windingsValid = true
            BoundBrush(null)
            return true
        }

        fun FromBounds(bounds: idBounds?): Boolean {
            var axis: Int
            var dir: Int
            val normal = idVec3()
            val plane = idPlane()
            axis = 0
            while (axis < 3) {
                dir = -1
                while (dir <= 1) {
                    normal.oSet(Vector.getVec3_origin())
                    normal.oSet(axis, dir.toFloat())
                    plane.SetNormal(normal)
                    plane.SetDist(dir * bounds.oGet(if (dir == 1) 1 else 0, axis))
                    sides.Append(idBrushSide(plane, -1))
                    dir += 2
                }
                axis++
            }
            return CreateWindings()
        }

        fun Transform(origin: idVec3?, axis: idMat3?) {
            var i: Int
            var transformed = false
            if (axis.IsRotated()) {
                i = 0
                while (i < sides.Num()) {
                    sides.oGet(i).plane.RotateSelf(Vector.getVec3_origin(), axis)
                    i++
                }
                transformed = true
            }
            if (origin != Vector.getVec3_origin()) {
                i = 0
                while (i < sides.Num()) {
                    sides.oGet(i).plane.TranslateSelf(origin)
                    i++
                }
                transformed = true
            }
            if (transformed) {
                CreateWindings()
            }
        }

        fun Copy(): idBrush? {
            var i: Int
            val b: idBrush
            b = idBrush()
            b.entityNum = entityNum
            b.primitiveNum = primitiveNum
            b.contents = contents
            b.windingsValid = windingsValid
            b.bounds = bounds
            i = 0
            while (i < sides.Num()) {
                b.sides.Append(sides.oGet(i).Copy())
                i++
            }
            return b
        }

        fun TryMerge(brush: idBrush?, planeList: idPlaneSet?): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            var m: Int
            var seperatingPlane: Int
            val brushes = arrayOfNulls<idBrush?>(2)
            var w: idWinding?
            var plane: idPlane?

            // brush bounds should overlap
            i = 0
            while (i < 3) {
                if (bounds.oGet(0, i) > brush.bounds.oGet(1, i) + 0.1f) {
                    return false
                }
                if (bounds.oGet(1, i) < brush.bounds.oGet(0, i) - 0.1f) {
                    return false
                }
                i++
            }

            // the brushes should share an opposite plane
            seperatingPlane = -1
            i = 0
            while (i < GetNumSides()) {
                j = 0
                while (j < brush.GetNumSides()) {
                    if (GetSide(i).GetPlaneNum() == brush.GetSide(j).GetPlaneNum() xor 1) {
                        // may only have one seperating plane
                        if (seperatingPlane != -1) {
                            return false
                        }
                        seperatingPlane = GetSide(i).GetPlaneNum()
                        break
                    }
                    j++
                }
                i++
            }
            if (seperatingPlane == -1) {
                return false
            }
            brushes[0] = this
            brushes[1] = brush
            i = 0
            while (i < 2) {
                j = TempDump.SNOT(i.toDouble())
                k = 0
                while (k < brushes[i].GetNumSides()) {


                    // if the brush side plane is the seprating plane
                    if (0 == brushes[i].GetSide(k).GetPlaneNum() xor seperatingPlane shr 1) {
                        k++
                        continue
                    }
                    plane = brushes[i].GetSide(k).GetPlane()

                    // all the non seperating brush sides of the other brush should be at the back or on the plane
                    l = 0
                    while (l < brushes[j].GetNumSides()) {
                        w = brushes[j].GetSide(l).GetWinding()
                        if (TempDump.NOT(w)) {
                            l++
                            continue
                        }
                        if (0 == brushes[j].GetSide(l).GetPlaneNum() xor seperatingPlane shr 1) {
                            l++
                            continue
                        }
                        m = 0
                        while (m < w.GetNumPoints()) {
                            if (plane.Distance(w.oGet(m).ToVec3()) > 0.1f) {
                                return false
                            }
                            m++
                        }
                        l++
                    }
                    k++
                }
                i++
            }

            // add any sides from the other brush to this brush
            i = 0
            while (i < brush.GetNumSides()) {
                j = 0
                while (j < GetNumSides()) {
                    if (0 == brush.GetSide(i).GetPlaneNum() xor GetSide(j).GetPlaneNum() shr 1) {
                        break
                    }
                    j++
                }
                if (j < GetNumSides()) {
                    sides.oGet(j).flags = sides.oGet(j).flags and brush.GetSide(i).GetFlags()
                    i++
                    continue
                }
                sides.Append(brush.GetSide(i).Copy())
                i++
            }

            // remove any side from this brush that is the opposite of a side of the other brush
            i = 0
            while (i < GetNumSides()) {
                j = 0
                while (j < brush.GetNumSides()) {
                    if (GetSide(i).GetPlaneNum() == brush.GetSide(j).GetPlaneNum() xor 1) {
                        break
                    }
                    j++
                }
                if (j < brush.GetNumSides()) {
//			delete sides[i];
                    sides.RemoveIndex(i)
                    i--
                    //                    continue;
                }
                i++
            }
            contents = contents or brush.contents
            CreateWindings()
            BoundBrush()
            return true
        }

        // returns true if the brushes did intersect
        fun Subtract(b: idBrush?, list: idBrushList?): Boolean {
            var i: Int
            val front = idBrush()
            val back = idBrush()
            var `in`: idBrush
            list.Clear()
            `in` = this
            i = 0
            while (i < b.sides.Num() && `in` != null) {
                `in`.Split(b.sides.oGet(i).plane, b.sides.oGet(i).planeNum, front, back)

//                if (!in.equals(this)) {
//			delete in;
//                    in = null;
//                }
                if (!front.isNULL()) {
                    list.AddToTail(front)
                }
                `in` = back
                i++
            }
            // if didn't really intersect
            if (!TempDump.NOT(`in`)) {
                list.Free()
                return false
            }

//	delete in;
            return true
        }

        // split the brush into a front and back brush
        fun Split(
            plane: idPlane?,
            planeNum: Int,
            front: idBrush?,
            back: idBrush?
        ): Int { //TODO:generic function pointer class.
            val res: Int
            var i: Int
            var j: Int
            var side: idBrushSide?
            val frontSide = arrayOf<idBrushSide?>(null)
            val backSide = arrayOf<idBrushSide?>(null)
            var dist: Float
            var maxBack: Float
            var maxFront: Float
            val maxBackWinding: FloatArray
            val maxFrontWinding: FloatArray
            var w: idWinding?
            var mid: idWinding?
            assert(windingsValid)

//            if (front != null) {
//                front[0] = null;
//            }
//            if (back != null) {
//                back[0] = null;
//            }
//
            res = bounds.PlaneSide(plane, -Brush.BRUSH_EPSILON)
            if (res == Plane.PLANESIDE_FRONT) {
                front?.oSet(Copy())
                return res
            }
            if (res == Plane.PLANESIDE_BACK) {
                back?.oSet(Copy())
                return res
            }
            maxBackWinding = FloatArray(sides.Num())
            maxFrontWinding = FloatArray(sides.Num())
            maxBack = 0.0f
            maxFront = maxBack
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)
                w = side.winding
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                maxBackWinding[i] = 10.0f
                maxFrontWinding[i] = -10.0f
                j = 0
                while (j < w.GetNumPoints()) {
                    dist = plane.Distance(w.oGet(j).ToVec3())
                    if (dist > maxFrontWinding[i]) {
                        maxFrontWinding[i] = dist
                    }
                    if (dist < maxBackWinding[i]) {
                        maxBackWinding[i] = dist
                    }
                    j++
                }
                if (maxFrontWinding[i] > maxFront) {
                    maxFront = maxFrontWinding[i]
                }
                if (maxBackWinding[i] < maxBack) {
                    maxBack = maxBackWinding[i]
                }
                i++
            }
            if (maxFront < Brush.BRUSH_EPSILON) {
                back?.oSet(Copy())
                return Plane.PLANESIDE_BACK
            }
            if (maxBack > -Brush.BRUSH_EPSILON) {
                front?.oSet(Copy())
                return Plane.PLANESIDE_FRONT
            }
            mid = idWinding(plane.Normal(), plane.Dist())
            i = 0
            while (i < sides.Num() && mid != null) {
                mid = mid.Clip(sides.oGet(i).plane.oNegative(), Brush.BRUSH_EPSILON, false)
                i++
            }
            if (mid != null) {
                if (mid.IsTiny()) {
//			delete mid;
                    mid = null
                } else if (mid.IsHuge()) {
                    // if the winding is huge then the brush is unbounded
                    Common.common.Warning(
                        "brush %d on entity %d is unbounded"
                                + "( %1.2f %1.2f %1.2f )-( %1.2f %1.2f %1.2f )-( %1.2f %1.2f %1.2f )",
                        primitiveNum,
                        entityNum,
                        bounds.oGet(0, 0),
                        bounds.oGet(0, 1),
                        bounds.oGet(0, 2),
                        bounds.oGet(1, 0),
                        bounds.oGet(1, 1),
                        bounds.oGet(1, 2),
                        bounds.oGet(1, 0) - bounds.oGet(0, 0),
                        bounds.oGet(1, 1) - bounds.oGet(0, 1),
                        bounds.oGet(1, 2) - bounds.oGet(0, 2)
                    )
                    //			delete mid;
                    mid = null
                }
            }
            if (TempDump.NOT(mid)) {
                return if (maxFront > -maxBack) {
                    front?.oSet(Copy())
                    Plane.PLANESIDE_FRONT
                } else {
                    back?.oSet(Copy())
                    Plane.PLANESIDE_BACK
                }
            }
            if (TempDump.NOT(front) && TempDump.NOT(back)) {
//		delete mid;
                return Plane.PLANESIDE_CROSS
            }
            front.oSet(idBrush())
            front.SetContents(contents)
            front.SetEntityNum(entityNum)
            front.SetPrimitiveNum(primitiveNum)
            back.oSet(idBrush())
            back.SetContents(contents)
            back.SetEntityNum(entityNum)
            back.SetPrimitiveNum(primitiveNum)
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)
                if (TempDump.NOT(side.winding)) {
                    i++
                    continue
                }

                // if completely at the front
                if (maxBackWinding[i] >= Brush.BRUSH_EPSILON) {
                    front.sides.Append(side.Copy())
                } // if completely at the back
                else if (maxFrontWinding[i] <= -Brush.BRUSH_EPSILON) {
                    back.sides.Append(side.Copy())
                } else {
                    // split the side
                    side.Split(plane, frontSide, backSide)
                    if (frontSide[0] != null) {
                        front.sides.Append(frontSide[0])
                    } else if (maxFrontWinding[i] > -Brush.BRUSH_EPSILON) {
                        // favor an overconstrained brush
                        side = side.Copy()
                        side.winding = side.winding.Clip(
                            idPlane(plane.Normal(), plane.Dist() - (Brush.BRUSH_EPSILON + 0.02f)),
                            0.01f,
                            true
                        )
                        assert(side.winding != null)
                        front.sides.Append(side)
                    }
                    if (backSide[0] != null) {
                        back.sides.Append(backSide[0])
                    } else if (maxBackWinding[i] < Brush.BRUSH_EPSILON) {
                        // favor an overconstrained brush
                        side = side.Copy()
                        side.winding = side.winding.Clip(
                            idPlane(
                                plane.Normal().oNegative(),
                                -(plane.Dist() + (Brush.BRUSH_EPSILON + 0.02f))
                            ), 0.01f, true
                        )
                        assert(side.winding != null)
                        back.sides.Append(side)
                    }
                }
                i++
            }
            side = idBrushSide(plane.oNegative(), planeNum xor 1)
            side.winding = mid.Reverse()
            side.flags = side.flags or Brush.SFL_SPLIT
            front.sides.Append(side)
            front.windingsValid = true
            front.BoundBrush(this)
            side = idBrushSide(plane, planeNum)
            side.winding = mid
            side.flags = side.flags or Brush.SFL_SPLIT
            back.sides.Append(side)
            back.windingsValid = true
            back.BoundBrush(this)
            return Plane.PLANESIDE_CROSS
        }

        // expand the brush for an axial bounding box
        fun ExpandForAxialBox(bounds: idBounds?) {
            var i: Int
            var j: Int
            var side: idBrushSide?
            val v = idVec3()
            AddBevelsForAxialBox()
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)
                j = 0
                while (j < 3) {
                    if (side.plane.Normal().oGet(j) > 0.0f) {
                        v.oSet(j, bounds.oGet(0, j))
                    } else {
                        v.oSet(j, bounds.oGet(1, j))
                    }
                    j++
                }
                side.plane.SetDist(side.plane.Dist() + v.times(side.plane.Normal().oNegative()))
                i++
            }
            if (!CreateWindings()) {
                Common.common.Error(
                    "idBrush::ExpandForAxialBox: brush %d on entity %d imploded",
                    primitiveNum,
                    entityNum
                )
            }

            /*
             // after expansion at least all non bevel sides should have a winding
             for ( i = 0; i < sides.Num(); i++ ) {
             side = sides[i];
             if ( !side->winding ) {
             if ( !( side->flags & SFL_BEVEL ) ) {
             int shit = 1;
             }
             }
             }
             */
        }

        // next brush in list
        fun Next(): idBrush? {
            return next
        }

        private fun CreateWindings(): Boolean {
            var i: Int
            var j: Int
            var side: idBrushSide?
            bounds.Clear()
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)

//		if ( side.winding!=null ) {
//			delete side.winding;
//		}
                side.winding = idWinding(side.plane.Normal(), side.plane.Dist())
                j = 0
                while (j < sides.Num() && side.winding != null) {
                    if (i == j) {
                        j++
                        continue
                    }
                    // keep the winding if on the clip plane
                    side.winding = side.winding.Clip(sides.oGet(j).plane.oNegative(), Brush.BRUSH_EPSILON, true)
                    j++
                }
                if (side.winding != null) {
                    j = 0
                    while (j < side.winding.GetNumPoints()) {
                        bounds.AddPoint(side.winding.oGet(j).ToVec3())
                        j++
                    }
                }
                i++
            }
            if (bounds.oGet(0, 0) > bounds.oGet(1, 0)) {
                return false
            }
            i = 0
            while (i < 3) {
                if (bounds.oGet(0, i) < Lib.Companion.MIN_WORLD_COORD || bounds.oGet(
                        1,
                        i
                    ) > Lib.Companion.MAX_WORLD_COORD
                ) {
                    return false
                }
                i++
            }
            windingsValid = true
            return true
        }

        private fun BoundBrush(original: idBrush? = null /*= NULL*/) {
            var i: Int
            var j: Int
            var side: idBrushSide?
            var w: idWinding?
            assert(windingsValid)
            bounds.Clear()
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)
                w = side.winding
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                j = 0
                while (j < w.GetNumPoints()) {
                    bounds.AddPoint(w.oGet(j).ToVec3())
                    j++
                }
                i++
            }
            if (bounds.oGet(0, 0) > bounds.oGet(1, 0)) {
                if (original != null) {
                    val bm = idBrushMap("error_brush", "_original")
                    bm.WriteBrush(original)
                    //			delete bm;
                }
                Common.common.Error(
                    "idBrush::BoundBrush: brush %d on entity %d without windings",
                    primitiveNum,
                    entityNum
                )
            }
            i = 0
            while (i < 3) {
                if (bounds.oGet(0, i) < Lib.Companion.MIN_WORLD_COORD || bounds.oGet(
                        1,
                        i
                    ) > Lib.Companion.MAX_WORLD_COORD
                ) {
                    if (original != null) {
                        val bm = idBrushMap("error_brush", "_original")
                        bm.WriteBrush(original)
                        //				delete bm;
                    }
                    Common.common.Error(
                        "idBrush::BoundBrush: brush %d on entity %d is unbounded",
                        primitiveNum,
                        entityNum
                    )
                }
                i++
            }
        }

        private fun AddBevelsForAxialBox() {
            var axis: Int
            var dir: Int
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            var order: Int
            var side: idBrushSide?
            var newSide: idBrushSide
            val plane = idPlane()
            val normal = idVec3()
            val vec = idVec3()
            var w: idWinding?
            var w2: idWinding?
            var d: Float
            var minBack: Float
            assert(windingsValid)

            // add the axial planes
            order = 0
            axis = 0
            while (axis < 3) {
                dir = -1
                while (dir <= 1) {


                    // see if the plane is already present
                    i = 0
                    while (i < sides.Num()) {
                        if (dir > 0) {
                            if (sides.oGet(i).plane.Normal().oGet(axis) >= 0.9999f) {
                                break
                            }
                        } else {
                            if (sides.oGet(i).plane.Normal().oGet(axis) <= -0.9999f) {
                                break
                            }
                        }
                        i++
                    }
                    if (i >= sides.Num()) {
                        normal.oSet(Vector.getVec3_origin())
                        normal.oSet(axis, dir.toFloat())
                        plane.SetNormal(normal)
                        plane.SetDist(dir * bounds.oGet(if (dir == 1) 1 else 0, axis))
                        newSide = idBrushSide(plane, -1)
                        newSide.SetFlag(Brush.SFL_BEVEL)
                        sides.Append(newSide)
                    }
                    dir += 2
                    order++
                }
                axis++
            }

            // if the brush is pure axial we're done
            if (sides.Num() == 6) {
                return
            }

            // test the non-axial plane edges
            i = 0
            while (i < sides.Num()) {
                side = sides.oGet(i)
                w = side.winding
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                j = 0
                while (j < w.GetNumPoints()) {
                    k = (j + 1) % w.GetNumPoints()
                    vec.oSet(w.oGet(j).ToVec3().oMinus(w.oGet(k).ToVec3()))
                    if (vec.Normalize() < 0.5f) {
                        j++
                        continue
                    }
                    k = 0
                    while (k < 3) {
                        if (vec.oGet(k) == 1.0f || vec.oGet(k) == -1.0f || vec.oGet(k) == 0.0f && vec.oGet((k + 1) % 3) == 0.0f) {
                            break // axial
                        }
                        k++
                    }
                    if (k < 3) {
                        j++
                        continue  // only test non-axial edges
                    }

                    // try the six possible slanted axials from this edge
                    axis = 0
                    while (axis < 3) {
                        dir = -1
                        while (dir <= 1) {


                            // construct a plane
                            normal.oSet(Vector.getVec3_origin())
                            normal.oSet(axis, dir.toFloat())
                            normal.oSet(vec.Cross(normal))
                            if (normal.Normalize() < 0.5f) {
                                dir += 2
                                continue
                            }
                            plane.SetNormal(normal)
                            plane.FitThroughPoint(w.oGet(j).ToVec3())

                            // if all the points on all the sides are
                            // behind this plane, it is a proper edge bevel
                            k = 0
                            while (k < sides.Num()) {


                                // if this plane has allready been used, skip it
                                if (plane.Compare(sides.oGet(k).plane, 0.001f, 0.1f)) {
                                    break
                                }
                                w2 = sides.oGet(k).winding
                                if (TempDump.NOT(w2)) {
                                    k++
                                    continue
                                }
                                minBack = 0.0f
                                l = 0
                                while (l < w2.GetNumPoints()) {
                                    d = plane.Distance(w2.oGet(l).ToVec3())
                                    if (d > Brush.BRUSH_BEVEL_EPSILON) {
                                        break // point at the front
                                    }
                                    if (d < minBack) {
                                        minBack = d
                                    }
                                    l++
                                }
                                // if some point was at the front
                                if (l < w2.GetNumPoints()) {
                                    break
                                }
                                // if no points at the back then the winding is on the bevel plane
                                if (minBack > -Brush.BRUSH_BEVEL_EPSILON) {
                                    break
                                }
                                k++
                            }
                            if (k < sides.Num()) {
                                dir += 2
                                continue  // wasn't part of the outer hull
                            }

                            // add this plane
                            newSide = idBrushSide(plane, -1)
                            newSide.SetFlag(Brush.SFL_BEVEL)
                            sides.Append(newSide)
                            dir += 2
                        }
                        axis++
                    }
                    j++
                }
                i++
            }
        }

        private fun RemoveSidesWithoutWinding(): Boolean {
            var i: Int
            i = 0
            while (i < sides.Num()) {
                if (sides.oGet(i).winding != null) {
                    i++
                    continue
                }
                sides.RemoveIndex(i)
                i--
                i++
            }
            return sides.Num() >= 4
        }

        fun isNULL(): Boolean {
            return NULL
        }

        private fun oSet(Copy: idBrush?) {
            NULL = false
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        //
        //
        // friend class idBrushList;
        init {
            contents = flags
            bounds.Clear()
            sides.Clear()
            windingsValid = false
        }
    }

    //===============================================================
    //
    //	idBrushList
    //
    //===============================================================
    internal class idBrushList {
        private var head: idBrush?
        private var numBrushSides = 0
        private var numBrushes: Int
        private var tail: idBrush?
        private fun oSet(keep: idBrushList?) {
            head = keep.head
            tail = keep.tail
            numBrushes = keep.numBrushes
            numBrushSides = keep.numBrushSides
        }

        // ~idBrushList();
        fun Num(): Int {
            return numBrushes
        }

        fun NumSides(): Int {
            return numBrushSides
        }

        fun Head(): idBrush? {
            return head
        }

        fun Tail(): idBrush? {
            return tail
        }

        fun Clear() {
            tail = null
            head = tail
            numBrushes = 0
        }

        fun IsEmpty(): Boolean {
            return numBrushes == 0
        }

        fun GetBounds(): idBounds? {
            val bounds = idBounds()
            var b: idBrush?
            bounds.Clear()
            b = Head()
            while (b != null) {
                bounds.timesAssign(b.GetBounds())
                b = b.Next()
            }
            return bounds
        }

        // add brush to the tail of the list
        fun AddToTail(brush: idBrush?) {
            brush.next = null
            if (tail != null) {
                tail.next = brush
            }
            tail = brush
            if (TempDump.NOT(head)) {
                head = brush
            }
            numBrushes++
            numBrushSides += brush.sides.Num()
        }

        // add list to the tail of the list
        fun AddToTail(list: idBrushList?) {
            var brush: idBrush?
            var next: idBrush?
            brush = list.head
            while (brush != null) {
                next = brush.next
                brush.next = null
                if (tail != null) {
                    tail.next = brush
                }
                tail = brush
                if (TempDump.NOT(head)) {
                    head = brush
                }
                numBrushes++
                numBrushSides += brush.sides.Num()
                brush = next
            }
            list.tail = null
            list.head = list.tail
            list.numBrushes = 0
        }

        // add brush to the front of the list
        fun AddToFront(brush: idBrush?) {
            brush.next = head
            head = brush
            if (TempDump.NOT(tail)) {
                tail = brush
            }
            numBrushes++
            numBrushSides += brush.sides.Num()
        }

        // add list to the front of the list
        fun AddToFront(list: idBrushList?) {
            var brush: idBrush?
            var next: idBrush?
            brush = list.head
            while (brush != null) {
                next = brush.next
                brush.next = head
                head = brush
                if (TempDump.NOT(tail)) {
                    tail = brush
                }
                numBrushes++
                numBrushSides += brush.sides.Num()
                brush = next
            }
            list.tail = null
            list.head = list.tail
            list.numBrushes = 0
        }

        // remove the brush from the list
        fun Remove(brush: idBrush?) {
            var b: idBrush?
            var last: idBrush?
            last = null
            b = head
            while (b != null) {
                if (b == brush) {
                    if (last != null) {
                        last.next = b.next
                    } else {
                        head = b.next
                    }
                    if (b == tail) {
                        tail = last
                    }
                    numBrushes--
                    numBrushSides -= brush.sides.Num()
                    return
                }
                last = b
                b = b.next
            }
        }

        // remove the brush from the list and delete the brush
        fun Delete(brush: idBrush?) {
            var b: idBrush?
            var last: idBrush?
            last = null
            b = head
            while (b != null) {
                if (b == brush) {
                    if (last != null) {
                        last.next = b.next
                    } else {
                        head = b.next
                    }
                    if (b == tail) {
                        tail = last
                    }
                    numBrushes--
                    numBrushSides -= b.sides.Num()
                    //			delete b;
                    return
                }
                last = b
                b = b.next
            }
        }

        // returns a copy of the brush list
        fun Copy(): idBrushList? {
            var brush: idBrush?
            val list: idBrushList
            list = idBrushList()
            brush = head
            while (brush != null) {
                list.AddToTail(brush.Copy())
                brush = brush.next
            }
            return list
        }

        // delete all brushes in the list
        fun Free() {
            var brush: idBrush?
            var next: idBrush?
            brush = head
            while (brush != null) {
                next = brush.next
                brush = next
            }
            tail = null
            head = tail
            numBrushSides = 0
            numBrushes = numBrushSides
        }

        // split the brushes in the list into two lists
        @JvmOverloads
        fun Split(
            plane: idPlane?,
            planeNum: Int,
            frontList: idBrushList?,
            backList: idBrushList?,
            useBrushSavedPlaneSide: Boolean = false /*= false*/
        ) {
            var b: idBrush?
            val front = idBrush()
            val back = idBrush()
            frontList.Clear()
            backList.Clear()
            if (!useBrushSavedPlaneSide) {
                b = head
                while (b != null) {
                    b.Split(plane, planeNum, front, back)
                    if (!front.isNULL()) {
                        frontList.AddToTail(front)
                    }
                    if (!back.isNULL()) {
                        backList.AddToTail(back)
                    }
                    b = b.next
                }
                return
            }
            b = head
            while (b != null) {
                if (b.savedPlaneSide and Brush.BRUSH_PLANESIDE_BOTH != 0) {
                    b.Split(plane, planeNum, front, back)
                    if (!front.isNULL()) {
                        frontList.AddToTail(front)
                    }
                    if (!back.isNULL()) {
                        backList.AddToTail(back)
                    }
                } else if (b.savedPlaneSide and Brush.BRUSH_PLANESIDE_FRONT != 0) {
                    frontList.AddToTail(b.Copy())
                } else {
                    backList.AddToTail(b.Copy())
                }
                b = b.next
            }
        }

        // chop away all brush overlap
        fun Chop(chopAllowed: Allowance?) {
            var b1: idBrush?
            var b2: idBrush?
            var next: idBrush?
            val sub1 = idBrushList()
            val sub2 = idBrushList()
            val keep = idBrushList()
            var i: Int
            var j: Int
            var c1: Int
            var c2: Int
            val planeList = idPlaneSet()
            if (Brush.OUTPUT_CHOP_STATS) {
                Common.common.Printf("[Brush CSG]\n")
                Common.common.Printf("%6d original brushes\n", Num())
            }
            CreatePlaneList(planeList)
            b1 = Head()
            while (b1 != null) {
                b2 = b1.next
                while (b2 != null) {
                    next = b2.next
                    i = 0
                    while (i < 3) {
                        if (b1.bounds.oGet(0, i) >= b2.bounds.oGet(1, i)) {
                            break
                        }
                        if (b1.bounds.oGet(1, i) <= b2.bounds.oGet(0, i)) {
                            break
                        }
                        i++
                    }
                    if (i < 3) {
                        b2 = next
                        continue
                    }
                    i = 0
                    while (i < b1.GetNumSides()) {
                        j = 0
                        while (j < b2.GetNumSides()) {
                            if (b1.GetSide(i).GetPlaneNum() == b2.GetSide(j).GetPlaneNum() xor 1) {
                                // opposite planes, so not touching
                                break
                            }
                            j++
                        }
                        if (j < b2.GetNumSides()) {
                            break
                        }
                        i++
                    }
                    if (i < b1.GetNumSides()) {
                        b2 = next
                        continue
                    }
                    sub1.Clear()
                    sub2.Clear()
                    c1 = 999999
                    c2 = 999999

                    // if b2 may chop up b1
                    if (TempDump.NOT(chopAllowed) || chopAllowed.run(b2, b1)) {
                        if (!b1.Subtract(b2, sub1)) {
                            // didn't really intersect
                            b2 = next
                            continue
                        }
                        if (sub1.IsEmpty()) {
                            // b1 is swallowed by b2
                            Delete(b1)
                            break
                        }
                        c1 = sub1.Num()
                    }

                    // if b1 may chop up b2
                    if (TempDump.NOT(chopAllowed) || chopAllowed.run(b1, b2)) {
                        if (!b2.Subtract(b1, sub2)) {
                            // didn't really intersect
                            b2 = next
                            continue
                        }
                        if (sub2.IsEmpty()) {
                            // b2 is swallowed by b1
                            sub1.Free()
                            Delete(b2)
                            b2 = next
                            continue
                        }
                        c2 = sub2.Num()
                    }
                    if (sub1.IsEmpty() && sub2.IsEmpty()) {
                        b2 = next
                        continue
                    }

                    // don't allow too much fragmentation
                    if (c1 > 2 && c2 > 2) {
                        sub1.Free()
                        sub2.Free()
                        b2 = next
                        continue
                    }
                    if (c1 < c2) {
                        sub2.Free()
                        this.AddToTail(sub1)
                        Delete(b1)
                        break
                    } else {
                        sub1.Free()
                        this.AddToTail(sub2)
                        Delete(b2)
                        b2 = next
                        continue
                    }
                    b2 = next
                }
                if (TempDump.NOT(2.0)) {
                    // b1 is no longer intersecting anything, so keep it
                    Remove(b1)
                    keep.AddToTail(b1)
                    if (Brush.OUTPUT_CHOP_STATS) {
                        Brush.DisplayRealTimeString("\r%6d", keep.numBrushes)
                    }
                }
                b1 = Head()
            }
            oSet(keep)
            if (Brush.OUTPUT_CHOP_STATS) {
                Common.common.Printf("\r%6d output brushes\n", Num())
            }
        }

        // merge brushes
        fun Merge(mergeAllowed: Allowance?) {
            val planeList = idPlaneSet()
            var b1: idBrush?
            var b2: idBrush?
            var nextb2: idBrush?
            var numMerges: Int
            Common.common.Printf("[Brush Merge]\n")
            Common.common.Printf("%6d original brushes\n", Num())
            CreatePlaneList(planeList)
            numMerges = 0
            b1 = Head()
            while (b1 != null) {
                b2 = Head()
                while (b2 != null) {
                    nextb2 = b2.Next()
                    if (b2 === b1) {
                        b2 = nextb2
                        continue
                    }
                    if (mergeAllowed != null && !mergeAllowed.run(b1, b2)) {
                        b2 = nextb2
                        continue
                    }
                    if (b1.TryMerge(b2, planeList)) {
                        Delete(b2)
                        Brush.DisplayRealTimeString("\r%6d" + ++numMerges)
                        nextb2 = Head()
                    }
                    b2 = nextb2
                }
                b1 = b1.next
            }
            Common.common.Printf("\r%6d brushes merged\n", numMerges)
        }

        // set the given flag on all brush sides facing the plane
        fun SetFlagOnFacingBrushSides(plane: idPlane?, flag: Int) {
            var i: Int
            var b: idBrush?
            var w: idWinding?
            b = head
            while (b != null) {
                if (Math.abs(b.GetBounds().PlaneDistance(plane)) > 0.1f) {
                    b = b.next
                    continue
                }
                i = 0
                while (i < b.GetNumSides()) {
                    w = b.GetSide(i).GetWinding()
                    if (TempDump.NOT(w)) {
                        if (b.GetSide(i).GetPlane()
                                .Compare(plane, Brush.BRUSH_PLANE_NORMAL_EPSILON, Brush.BRUSH_PLANE_DIST_EPSILON)
                        ) {
                            b.GetSide(i).SetFlag(flag)
                        }
                        i++
                        continue
                    }
                    if (w.PlaneSide(plane) == Plane.SIDE_ON) {
                        b.GetSide(i).SetFlag(flag)
                    }
                    i++
                }
                b = b.next
            }
        }

        // get a list with planes for all brushes in the list
        fun CreatePlaneList(planeList: idPlaneSet?) {
            var i: Int
            var b: idBrush?
            var side: idBrushSide?
            planeList.Resize(512, 128)
            b = Head()
            while (b != null) {
                i = 0
                while (i < b.GetNumSides()) {
                    side = b.GetSide(i)
                    side.SetPlaneNum(
                        planeList.FindPlane(
                            side.GetPlane(),
                            Brush.BRUSH_PLANE_NORMAL_EPSILON,
                            Brush.BRUSH_PLANE_DIST_EPSILON
                        )
                    )
                    i++
                }
                b = b.Next()
            }
        }

        // write a brush map with the brushes in the list
        fun WriteBrushMap(fileName: idStr?, ext: idStr?) {
            val map: idBrushMap
            map = idBrushMap(fileName, ext)
            map.WriteBrushList(this)
            //	delete map;
        }

        //
        //
        init {
            numBrushes = numBrushSides
            tail = null
            head = tail
        }
    }

    //===============================================================
    //
    //	idBrushMap
    //
    //===============================================================
    internal class idBrushMap(fileName: String?, ext: String?) {
        private var brushCount: Int
        private val fp: idFile?
        private var texture: idStr? = null

        //
        //
        constructor(fileName: idStr?, ext: idStr?) : this(fileName.toString(), ext.toString())

        // ~idBrushMap( void );
        fun SetTexture(textureName: idStr?) {
            texture = textureName
        }

        fun SetTexture(textureName: String?) {
            this.SetTexture(idStr(textureName))
        }

        fun WriteBrush(brush: idBrush?) {
            var i: Int
            var side: idBrushSide?
            if (TempDump.NOT(fp)) {
                return
            }
            fp.WriteFloatString("// primitive %d\n{\nbrushDef3\n{\n", brushCount++)
            i = 0
            while (i < brush.GetNumSides()) {
                side = brush.GetSide(i)
                fp.WriteFloatString(
                    " ( %f %f %f %f ) ",
                    side.GetPlane().oGet(0),
                    side.GetPlane().oGet(1),
                    side.GetPlane().oGet(2),
                    -side.GetPlane().Dist()
                )
                fp.WriteFloatString("( ( 0.031250 0 0 ) ( 0 0.031250 0 ) ) %s 0 0 0\n", texture)
                i++
            }
            fp.WriteFloatString("}\n}\n")
        }

        fun WriteBrushList(brushList: idBrushList?) {
            var b: idBrush?
            if (TempDump.NOT(fp)) {
                return
            }
            b = brushList.Head()
            while (b != null) {
                WriteBrush(b)
                b = b.Next()
            }
        }

        init {
            val qpath: idStr
            qpath = idStr(fileName)
            qpath.StripFileExtension()
            qpath.oPluSet(ext)
            qpath.SetFileExtension("map")
            Common.common.Printf("writing %s...\n", qpath)
            fp = FileSystem_h.fileSystem.OpenFileWrite(qpath.toString(), "fs_devpath")
            if (TempDump.NOT(fp)) {
                Common.common.Error("Couldn't open %s\n", qpath)
                return
            }
            texture.oSet("textures/washroom/btile01")
            fp.WriteFloatString("Version %1.2f\n", MapFile.CURRENT_MAP_VERSION.toFloat())
            fp.WriteFloatString("{\n")
            fp.WriteFloatString("\"classname\" \"worldspawn\"\n")
            brushCount = 0
        }
    }
}