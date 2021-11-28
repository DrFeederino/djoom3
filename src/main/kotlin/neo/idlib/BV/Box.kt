package neo.idlib.BV

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.containers.CFloat
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVecX
import java.util.*

/**
 *
 */
object Box {
    //                4---{4}---5
    //     +         /|        /|
    //     Z      {7} {8}   {5} |
    //     -     /    |    /    {9}
    //          7--{6}----6     |
    //          |     |   |     |
    //        {11}    0---|-{0}-1
    //          |    /    |    /       -
    //          | {3}  {10} {1}       Y
    //          |/        |/         +
    //          3---{2}---2
    //
    //            - X +
    //
    //      plane bits:
    //      0 = min x
    //      1 = max x
    //      2 = min y
    //      3 = max y
    //      4 = min z
    //      5 = max z
    /*
     static int boxVertPlanes[8] = {
     ( (1<<0) | (1<<2) | (1<<4) ),
     ( (1<<1) | (1<<2) | (1<<4) ),
     ( (1<<1) | (1<<3) | (1<<4) ),
     ( (1<<0) | (1<<3) | (1<<4) ),
     ( (1<<0) | (1<<2) | (1<<5) ),
     ( (1<<1) | (1<<2) | (1<<5) ),
     ( (1<<1) | (1<<3) | (1<<5) ),
     ( (1<<0) | (1<<3) | (1<<5) )
     };

     static int boxVertEdges[8][3] = {
     // bottom
     { 3, 0, 8 },
     { 0, 1, 9 },
     { 1, 2, 10 },
     { 2, 3, 11 },
     // top
     { 7, 4, 8 },
     { 4, 5, 9 },
     { 5, 6, 10 },
     { 6, 7, 11 }
     };

     static int boxEdgePlanes[12][2] = {
     // bottom
     { 4, 2 },
     { 4, 1 },
     { 4, 3 },
     { 4, 0 },
     // top
     { 5, 2 },
     { 5, 1 },
     { 5, 3 },
     { 5, 0 },
     // sides
     { 0, 2 },
     { 2, 1 },
     { 1, 3 },
     { 3, 0 }
     };

     static int boxEdgeVerts[12][2] = {
     // bottom
     { 0, 1 },
     { 1, 2 },
     { 2, 3 },
     { 3, 0 },
     // top
     { 4, 5 },
     { 5, 6 },
     { 6, 7 },
     { 7, 4 },
     // sides
     { 0, 4 },
     { 1, 5 },
     { 2, 6 },
     { 3, 7 }
     };
     */
    val boxPlaneBitsSilVerts: Array<IntArray?>? = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 7, 4, 0, 3, 0, 0),
        intArrayOf(4, 5, 6, 2, 1, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 4, 5, 1, 0, 0, 0),
        intArrayOf(6, 3, 7, 4, 5, 1, 0),
        intArrayOf(6, 4, 5, 6, 2, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 6, 7, 3, 2, 0, 0),
        intArrayOf(6, 6, 7, 4, 0, 3, 2),
        intArrayOf(6, 5, 6, 7, 3, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 0, 1, 2, 3, 0, 0),
        intArrayOf(6, 0, 1, 2, 3, 7, 4),
        intArrayOf(6, 3, 2, 6, 5, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 1, 2, 3, 0, 4, 5),
        intArrayOf(6, 1, 2, 3, 7, 4, 5),
        intArrayOf(6, 2, 3, 0, 4, 5, 6),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 0, 1, 2, 6, 7, 3),
        intArrayOf(6, 0, 1, 2, 6, 7, 4),
        intArrayOf(6, 0, 1, 5, 6, 7, 3),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(4, 7, 6, 5, 4, 0, 0),
        intArrayOf(6, 7, 6, 5, 4, 0, 3),
        intArrayOf(6, 5, 4, 7, 6, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 4, 7, 6, 5, 1, 0),
        intArrayOf(6, 3, 7, 6, 5, 1, 0),
        intArrayOf(6, 4, 7, 6, 2, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(6, 6, 5, 4, 7, 3, 2),
        intArrayOf(6, 6, 5, 4, 0, 3, 2),
        intArrayOf(6, 5, 4, 7, 3, 2, 1),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0)
    )

    /*
     ============
     BoxPlaneClip
     ============
     */
    fun BoxPlaneClip(denom: Float, numer: Float, scale0: CFloat?, scale1: CFloat?): Boolean {
        return if (denom > 0.0f) {
            if (numer > denom * scale1.getVal()) {
                return false
            }
            if (numer > denom * scale0.getVal()) {
                scale0.setVal(numer / denom)
            }
            true
        } else if (denom < 0.0f) {
            if (numer > denom * scale0.getVal()) {
                return false
            }
            if (numer > denom * scale1.getVal()) {
                scale1.setVal(numer / denom)
            }
            true
        } else {
            numer <= 0.0f
        }
    }

    /*
     ===============================================================================

     Oriented Bounding Box

     ===============================================================================
     */
    class idBox {
        private var axis: idMat3? = idMat3()
        private val center: idVec3? = idVec3()
        private val extents: idVec3? = idVec3()

        //
        //
        constructor()
        constructor(center: idVec3?, extents: idVec3?, axis: idMat3?) {
            {
                this.center.oSet(center)
                this.extents.oSet(extents)
                this.axis = idMat3(axis)
            }
        }

        constructor(point: idVec3?) {
            center.oSet(point)
            extents.Zero()
            axis.Identity()
        }

        constructor(bounds: idBounds?) {
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))
            axis.Identity()
        }

        constructor(bounds: idBounds?, origin: idVec3?, axis: idMat3?) {
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))
            center.oSet(origin.oPlus(center.oMultiply(axis)))
            this.axis = idMat3(axis)
        }

        constructor(box: idBox?) {
            center.oSet(box.center)
            extents.oSet(box.extents)
            axis = idMat3(box.axis)
        }

        //
        fun oPlus(t: idVec3?): idBox? {                // returns translated box
            return idBox(center.oPlus(t), extents, axis)
        }

        fun oPluSet(t: idVec3?): idBox? {                    // translate the box
            center.oPluSet(t)
            return this
        }

        fun oMultiply(r: idMat3?): idBox? {                // returns rotated box
            return idBox(center.oMultiply(r), extents, axis.oMultiply(r))
        }

        fun oMulSet(r: idMat3?): idBox? {                    // rotate the box
            center.oMulSet(r)
            axis.oMulSet(r)
            return this
        }

        fun oPlus(a: idBox?): idBox? {
            val newBox: idBox
            newBox = idBox(this)
            newBox.AddBox(a)
            return newBox
        }

        fun oPluSet(a: idBox?): idBox? {
            AddBox(a)
            return this
        }

        fun oMinus(a: idBox?): idBox? {
            return idBox(center, extents.oMinus(a.extents), axis)
        }

        fun oMinSet(a: idBox?): idBox? {
            extents.oMinSet(a.extents)
            return this
        }

        //
        fun Compare(a: idBox?): Boolean {                        // exact compare, no epsilon
            return center.Compare(a.center) && extents.Compare(a.extents) && axis.Compare(a.axis)
        }

        fun Compare(a: idBox?, epsilon: Float): Boolean {    // compare with epsilon
            return center.Compare(a.center, epsilon) && extents.Compare(a.extents, epsilon) && axis.Compare(
                a.axis,
                epsilon
            )
        }

        //public	boolean			operator==(	final idBox &a ) ;						// exact compare, no epsilon
        //public	boolean			operator!=(	final idBox &a ) ;						// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 7
            hash = 31 * hash + Objects.hashCode(center)
            hash = 31 * hash + Objects.hashCode(extents)
            hash = 31 * hash + Objects.hashCode(axis)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idBox?
            if (center != other.center) {
                return false
            }
            return if (extents != other.extents) {
                false
            } else axis == other.axis
        }

        //
        fun Clear() {                                    // inside out box
            center.Zero()
            //            extents[0] = extents[1] = extents[2] = -idMath::INFINITY;
            extents.oSet(0, extents.oSet(1, extents.oSet(2, -idMath.INFINITY)))
            axis.Identity()
        }

        fun Zero() {                                    // single point at origin
            center.Zero()
            extents.Zero()
            axis.Identity()
        }

        // returns center of the box
        fun GetCenter(): idVec3? {
            return center
        }

        // returns extents of the box
        fun GetExtents(): idVec3? {
            return extents
        }

        // returns the axis of the box
        fun GetAxis(): idMat3? {
            return axis
        }

        fun GetVolume(): Float {                        // returns the volume of the box
            return extents.oMultiply(2.0f).LengthSqr()
        }

        fun IsCleared(): Boolean {                        // returns true if box are inside out
            return extents.oGet(0) < 0.0f
        }

        //
        fun AddPoint(v: idVec3?): Boolean {                    // add the point, returns true if the box expanded
            val axis2 = idMat3()
            val bounds1 = idBounds()
            val bounds2 = idBounds()
            if (extents.oGet(0) < 0.0f) {
                extents.Zero()
                center.oSet(v)
                axis.Identity()
                return true
            }
            bounds1.oSet(0, 0, bounds1.oSet(1, 0, center.oMultiply(axis.oGet(0))))
            bounds1.oSet(0, 1, bounds1.oSet(1, 1, center.oMultiply(axis.oGet(1))))
            bounds1.oSet(0, 2, bounds1.oSet(1, 2, center.oMultiply(axis.oGet(2))))
            bounds1.oGet(0).oMinSet(extents)
            bounds1.oGet(1).oPluSet(extents)
            if (!bounds1.AddPoint(
                    idVec3(
                        v.oMultiply(axis.oGet(0)),
                        v.oMultiply(axis.oGet(1)),
                        v.oMultiply(axis.oGet(2))
                    )
                )
            ) {
                // point is contained in the box
                return false
            }
            axis2.oSet(0, v.oMinus(center))
            axis2.oGet(0).Normalize()
            axis2.oSet(
                1,
                axis.oGet(
                    Math_h.Min3Index(
                        axis2.oGet(0).oMultiply(axis.oGet(0)),
                        axis2.oGet(0).oMultiply(axis.oGet(1)),
                        axis2.oGet(0).oMultiply(axis.oGet(2))
                    )
                )
            )
            axis2.oSet(1, axis2.oGet(1).oMinus(axis2.oGet(0).oMultiply(axis2.oGet(1).oMultiply(axis2.oGet(0)))))
            axis2.oGet(1).Normalize()
            axis2.oGet(2).Cross(axis2.oGet(0), axis2.oGet(1))
            AxisProjection(axis2, bounds2)
            bounds2.AddPoint(idVec3(v.oMultiply(axis2.oGet(0)), v.oMultiply(axis2.oGet(1)), v.oMultiply(axis2.oGet(2))))

            // create new box based on the smallest bounds
            if (bounds1.GetVolume() < bounds2.GetVolume()) {
                center.oSet(bounds1.oGet(0).oPlus(bounds1.oGet(1)).oMultiply(0.5f))
                extents.oSet(bounds1.oGet(1).oMinus(center))
                center.oMulSet(axis)
            } else {
                center.oSet(bounds2.oGet(0).oPlus(bounds2.oGet(1)).oMultiply(0.5f))
                extents.oSet(bounds2.oGet(1).oMinus(center))
                center.oMulSet(axis2)
                axis = axis2 //TODO: new axis 2?
            }
            return true
        }

        // add the box, returns true if the box expanded
        fun AddBox(a: idBox?): Boolean {
            var i: Int
            var besti: Int
            var v: Float
            var bestv: Float
            val dir = idVec3()
            val ax = arrayOfNulls<idMat3?>(4)
            val bounds = arrayOfNulls<idBounds?>(4)
            val b = idBounds()
            if (a.extents.oGet(0) < 0.0f) {
                return false
            }
            if (extents.oGet(0) < 0.0f) {
                center.oSet(a.center)
                extents.oSet(a.extents)
                axis = idMat3(a.axis)
                return true
            }

            // test axis of this box
            ax[0] = axis
            bounds[0].oSet(0, 0, bounds[0].oSet(1, 0, center.oMultiply(ax[0].oGet(0))))
            bounds[0].oSet(0, 1, bounds[0].oSet(1, 1, center.oMultiply(ax[0].oGet(1))))
            bounds[0].oSet(0, 2, bounds[0].oSet(1, 2, center.oMultiply(ax[0].oGet(2))))
            bounds[0].oGet(0).oMinSet(extents)
            bounds[0].oGet(1).oPluSet(extents)
            a.AxisProjection(ax[0], b)
            if (!bounds[0].AddBounds(b)) {
                // the other box is contained in this box
                return false
            }

            // test axis of other box
            ax[1] = a.axis
            bounds[0].oSet(0, 0, bounds[0].oSet(1, 0, a.center.oMultiply(ax[0].oGet(0))))
            bounds[0].oSet(0, 1, bounds[0].oSet(1, 1, a.center.oMultiply(ax[0].oGet(1))))
            bounds[0].oSet(0, 2, bounds[0].oSet(1, 2, a.center.oMultiply(ax[0].oGet(2))))
            bounds[0].oGet(0).oMinSet(a.extents)
            bounds[0].oGet(1).oPluSet(a.extents)
            AxisProjection(ax[1], b)
            if (!bounds[1].AddBounds(b)) {
                // this box is contained in the other box
                center.oSet(a.center)
                extents.oSet(a.extents)
                axis = idMat3(a.axis)
                return true
            }

            // test axes aligned with the vector between the box centers and one of the box axis
            dir.oSet(a.center.oMinus(center))
            dir.Normalize()
            i = 2
            while (i < 4) {
                ax[i].oSet(0, dir)
                ax[i].oSet(
                    1,
                    ax[i - 2].oGet(
                        Math_h.Min3Index(
                            dir.oMultiply(ax[i - 2].oGet(0)),
                            dir.oMultiply(ax[i - 2].oGet(1)),
                            dir.oMultiply(ax[i - 2].oGet(2))
                        )
                    )
                )
                ax[i].oSet(1, ax[i].oGet(1).oMinus(dir.oMultiply(ax[i].oGet(1).oMultiply(dir))))
                ax[i].oGet(1).Normalize()
                ax[i].oGet(2).Cross(dir, ax[i].oGet(1))
                AxisProjection(ax[i], bounds[i])
                a.AxisProjection(ax[i], b)
                bounds[i].AddBounds(b)
                i++
            }

            // get the bounds with the smallest volume
            bestv = idMath.INFINITY
            besti = 0
            i = 0
            while (i < 4) {
                v = bounds[i].GetVolume()
                if (v < bestv) {
                    bestv = v
                    besti = i
                }
                i++
            }

            // create a box from the smallest bounds axis pair
            center.oSet(bounds[besti].oGet(0).oPlus(bounds[besti].oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds[besti].oGet(1).oMinus(center))
            center.oMulSet(ax[besti])
            axis = ax[besti]
            return false
        }

        fun Expand(d: Float): idBox? {                    // return box expanded in all directions with the given value
            return idBox(center, extents.oPlus(idVec3(d, d, d)), axis)
        }

        fun ExpandSelf(d: Float): idBox? {                    // expand box in all directions with the given value
            extents.oPluSet(0, d)
            extents.oPluSet(1, d)
            extents.oPluSet(2, d)
            return this
        }

        fun Translate(translation: idVec3?): idBox? {    // return translated box
            return idBox(center.oPlus(translation), extents, axis)
        }

        fun TranslateSelf(translation: idVec3?): idBox? {        // translate this box
            center.oPluSet(translation)
            return this
        }

        fun Rotate(rotation: idMat3?): idBox? {            // return rotated box
            return idBox(center.oMultiply(rotation), extents, axis.oMultiply(rotation))
        }

        fun RotateSelf(rotation: idMat3?): idBox? {            // rotate this box
            center.oMulSet(rotation)
            axis.oMulSet(rotation)
            return this
        }

        //
        fun PlaneDistance(plane: idPlane?): Float {
            val d1: Float
            val d2: Float
            d1 = plane.Distance(center)
            d2 = (Math.abs(extents.oGet(0) * plane.Normal().oGet(0))
                    + Math.abs(extents.oGet(1) * plane.Normal().oGet(1))
                    + Math.abs(extents.oGet(2) * plane.Normal().oGet(2)))
            if (d1 - d2 > 0.0f) {
                return d1 - d2
            }
            return if (d1 + d2 < 0.0f) {
                d1 + d2
            } else 0.0f
        }

        @JvmOverloads
        fun PlaneSide(plane: idPlane?, epsilon: Float = Plane.ON_EPSILON): Int {
            val d1: Float
            val d2: Float
            d1 = plane.Distance(center)
            d2 = (Math.abs(extents.oGet(0) * plane.Normal().oGet(0))
                    + Math.abs(extents.oGet(1) * plane.Normal().oGet(1))
                    + Math.abs(extents.oGet(2) * plane.Normal().oGet(2)))
            if (d1 - d2 > epsilon) {
                return Plane.PLANESIDE_FRONT
            }
            return if (d1 + d2 < -epsilon) {
                Plane.PLANESIDE_BACK
            } else Plane.PLANESIDE_CROSS
        }

        //
        fun ContainsPoint(p: idVec3?): Boolean {            // includes touching
            val lp = idVec3(p.oMinus(center))
            return (Math.abs(lp.oMultiply(axis.oGet(0))) <= extents.oGet(0)
                    && Math.abs(lp.oMultiply(axis.oGet(1))) <= extents.oGet(1)
                    && Math.abs(lp.oMultiply(axis.oGet(2))) <= extents.oGet(2))
        }

        fun IntersectsBox(a: idBox?): Boolean {            // includes touching
            val dir = idVec3() // vector between centers
            val c = Array<FloatArray?>(3) { FloatArray(3) } // matrix c = axis.Transpose() * a.axis
            val ac = Array<FloatArray?>(3) { FloatArray(3) } // absolute values of c
            val axisdir = FloatArray(3) // axis[i] * dir
            var d: Float
            var e0: Float
            var e1: Float // distance between centers and projected extents
            dir.oSet(a.center.oMinus(center))

            // axis C0 + t * A0
            c[0].get(0) = axis.oGet(0).oMultiply(a.axis.oGet(0))
            c[0].get(1) = axis.oGet(0).oMultiply(a.axis.oGet(1))
            c[0].get(2) = axis.oGet(0).oMultiply(a.axis.oGet(2))
            axisdir[0] = axis.oGet(0).oMultiply(dir)
            ac[0].get(0) = Math.abs(c[0].get(0))
            ac[0].get(1) = Math.abs(c[0].get(1))
            ac[0].get(2) = Math.abs(c[0].get(2))
            d = Math.abs(axisdir[0])
            e0 = extents.oGet(0)
            e1 = a.extents.oGet(0) * ac[0].get(0) + a.extents.oGet(1) * ac[0].get(1) + a.extents.oGet(2) * ac[0].get(2)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1
            c[1].get(0) = axis.oGet(1).oMultiply(a.axis.oGet(0))
            c[1].get(1) = axis.oGet(1).oMultiply(a.axis.oGet(1))
            c[1].get(2) = axis.oGet(1).oMultiply(a.axis.oGet(2))
            axisdir[1] = axis.oGet(1).oMultiply(dir)
            ac[1].get(0) = Math.abs(c[1].get(0))
            ac[1].get(1) = Math.abs(c[1].get(1))
            ac[1].get(2) = Math.abs(c[1].get(2))
            d = Math.abs(axisdir[1])
            e0 = extents.oGet(1)
            e1 = a.extents.oGet(0) * ac[1].get(0) + a.extents.oGet(1) * ac[1].get(1) + a.extents.oGet(2) * ac[1].get(2)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2
            c[2].get(0) = axis.oGet(2).oMultiply(a.axis.oGet(0))
            c[2].get(1) = axis.oGet(2).oMultiply(a.axis.oGet(1))
            c[2].get(2) = axis.oGet(2).oMultiply(a.axis.oGet(2))
            axisdir[2] = axis.oGet(2).oMultiply(dir)
            ac[2].get(0) = Math.abs(c[2].get(0))
            ac[2].get(1) = Math.abs(c[2].get(1))
            ac[2].get(2) = Math.abs(c[2].get(2))
            d = Math.abs(axisdir[2])
            e0 = extents.oGet(2)
            e1 = a.extents.oGet(0) * ac[2].get(0) + a.extents.oGet(1) * ac[2].get(1) + a.extents.oGet(2) * ac[2].get(2)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B0
            d = Math.abs(a.axis.oGet(0).oMultiply(dir))
            e0 = extents.oGet(0) * ac[0].get(0) + extents.oGet(1) * ac[1].get(0) + extents.oGet(2) * ac[2].get(0)
            e1 = a.extents.oGet(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B1
            d = Math.abs(a.axis.oGet(1).oMultiply(dir))
            e0 = extents.oGet(0) * ac[0].get(1) + extents.oGet(1) * ac[1].get(1) + extents.oGet(2) * ac[2].get(1)
            e1 = a.extents.oGet(1)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * B2
            d = Math.abs(a.axis.oGet(2).oMultiply(dir))
            e0 = extents.oGet(0) * ac[0].get(2) + extents.oGet(1) * ac[1].get(2) + extents.oGet(2) * ac[2].get(2)
            e1 = a.extents.oGet(2)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB0
            d = Math.abs(axisdir[2] * c[1].get(0) - axisdir[1] * c[2].get(0))
            e0 = extents.oGet(1) * ac[2].get(0) + extents.oGet(2) * ac[1].get(0)
            e1 = a.extents.oGet(1) * ac[0].get(2) + a.extents.oGet(2) * ac[0].get(1)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB1
            d = Math.abs(axisdir[2] * c[1].get(1) - axisdir[1] * c[2].get(1))
            e0 = extents.oGet(1) * ac[2].get(1) + extents.oGet(2) * ac[1].get(1)
            e1 = a.extents.oGet(0) * ac[0].get(2) + a.extents.oGet(2) * ac[0].get(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A0xB2
            d = Math.abs(axisdir[2] * c[1].get(2) - axisdir[1] * c[2].get(2))
            e0 = extents.oGet(1) * ac[2].get(2) + extents.oGet(2) * ac[1].get(2)
            e1 = a.extents.oGet(0) * ac[0].get(1) + a.extents.oGet(1) * ac[0].get(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB0
            d = Math.abs(axisdir[0] * c[2].get(0) - axisdir[2] * c[0].get(0))
            e0 = extents.oGet(0) * ac[2].get(0) + extents.oGet(2) * ac[0].get(0)
            e1 = a.extents.oGet(1) * ac[1].get(2) + a.extents.oGet(2) * ac[1].get(1)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB1
            d = Math.abs(axisdir[0] * c[2].get(1) - axisdir[2] * c[0].get(1))
            e0 = extents.oGet(0) * ac[2].get(1) + extents.oGet(2) * ac[0].get(1)
            e1 = a.extents.oGet(0) * ac[1].get(2) + a.extents.oGet(2) * ac[1].get(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A1xB2
            d = Math.abs(axisdir[0] * c[2].get(2) - axisdir[2] * c[0].get(2))
            e0 = extents.oGet(0) * ac[2].get(2) + extents.oGet(2) * ac[0].get(2)
            e1 = a.extents.oGet(0) * ac[1].get(1) + a.extents.oGet(1) * ac[1].get(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB0
            d = Math.abs(axisdir[1] * c[0].get(0) - axisdir[0] * c[1].get(0))
            e0 = extents.oGet(0) * ac[1].get(0) + extents.oGet(1) * ac[0].get(0)
            e1 = a.extents.oGet(1) * ac[2].get(2) + a.extents.oGet(2) * ac[2].get(1)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB1
            d = Math.abs(axisdir[1] * c[0].get(1) - axisdir[0] * c[1].get(1))
            e0 = extents.oGet(0) * ac[1].get(1) + extents.oGet(1) * ac[0].get(1)
            e1 = a.extents.oGet(0) * ac[2].get(2) + a.extents.oGet(2) * ac[2].get(0)
            if (d > e0 + e1) {
                return false
            }

            // axis C0 + t * A2xB2
            d = Math.abs(axisdir[1] * c[0].get(2) - axisdir[0] * c[1].get(2))
            e0 = extents.oGet(0) * ac[1].get(2) + extents.oGet(1) * ac[0].get(2)
            e1 = a.extents.oGet(0) * ac[2].get(1) + a.extents.oGet(1) * ac[2].get(0)
            return d <= e0 + e1
        }

        /*
         ============
         idBox::LineIntersection

         Returns true if the line intersects the box between the start and end point.
         ============
         */
        fun LineIntersection(start: idVec3?, end: idVec3?): Boolean {
            val ld = FloatArray(3)
            val lineDir = idVec3(end.oMinus(start).oMultiply(0.5f))
            val lineCenter = idVec3(start.oPlus(lineDir))
            val dir = idVec3(lineCenter.oMinus(center))
            ld[0] = Math.abs(lineDir.oMultiply(axis.oGet(0)))
            if (Math.abs(dir.oMultiply(axis.oGet(0))) > extents.oGet(0) + ld[0]) {
                return false
            }
            ld[1] = Math.abs(lineDir.oMultiply(axis.oGet(1)))
            if (Math.abs(dir.oMultiply(axis.oGet(1))) > extents.oGet(1) + ld[1]) {
                return false
            }
            ld[2] = Math.abs(lineDir.oMultiply(axis.oGet(2)))
            if (Math.abs(dir.oMultiply(axis.oGet(2))) > extents.oGet(2) + ld[2]) {
                return false
            }
            val cross = idVec3(lineDir.Cross(dir))
            if (Math.abs(cross.oMultiply(axis.oGet(0))) > extents.oGet(1) * ld[2] + extents.oGet(2) * ld[1]) {
                return false
            }
            return if (Math.abs(cross.oMultiply(axis.oGet(1))) > extents.oGet(0) * ld[2] + extents.oGet(2) * ld[0]) {
                false
            } else Math.abs(cross.oMultiply(axis.oGet(2))) <= extents.oGet(0) * ld[1] + extents.oGet(1) * ld[0]
        }

        /*
         ============
         idBox::RayIntersection

         Returns true if the ray intersects the box.
         The ray can intersect the box in both directions from the start point.
         If start is inside the box then scale1 < 0 and scale2 > 0.
         ============
         */
        // intersection points are (start + dir * scale1) and (start + dir * scale2)
        fun RayIntersection(start: idVec3?, dir: idVec3?, scale1: CFloat?, scale2: CFloat?): Boolean {
            val localStart = idVec3()
            val localDir = idVec3()
            localStart.oSet(start.oMinus(center).oMultiply(axis.Transpose()))
            localDir.oSet(dir.oMultiply(axis.Transpose()))
            scale1.setVal(-idMath.INFINITY)
            scale2.setVal(idMath.INFINITY)
            return (Box.BoxPlaneClip(localDir.x, -localStart.x - extents.oGet(0), scale1, scale2)
                    && Box.BoxPlaneClip(-localDir.x, localStart.x - extents.oGet(0), scale1, scale2)
                    && Box.BoxPlaneClip(localDir.y, -localStart.y - extents.oGet(1), scale1, scale2)
                    && Box.BoxPlaneClip(-localDir.y, localStart.y - extents.oGet(1), scale1, scale2)
                    && Box.BoxPlaneClip(localDir.z, -localStart.z - extents.oGet(2), scale1, scale2)
                    && Box.BoxPlaneClip(-localDir.z, localStart.z - extents.oGet(2), scale1, scale2))
        }

        //
        /*
         ============
         idBox::FromPoints

         Tight box for a collection of points.
         ============
         */
        // tight box for a collection of points
        fun FromPoints(points: Array<idVec3?>?, numPoints: Int) {
            var i: Int
            val invNumPoints: Float
            var sumXX: Float
            var sumXY: Float
            var sumXZ: Float
            var sumYY: Float
            var sumYZ: Float
            var sumZZ: Float
            val dir = idVec3()
            val bounds = idBounds()
            val eigenVectors = idMatX()
            val eigenValues = idVecX()

            // compute mean of points
            center.oSet(points.get(0))
            i = 1
            while (i < numPoints) {
                center.oPluSet(points.get(i))
                i++
            }
            invNumPoints = 1.0f / numPoints
            center.oMulSet(invNumPoints)

            // compute covariances of points
            sumXX = 0.0f
            sumXY = 0.0f
            sumXZ = 0.0f
            sumYY = 0.0f
            sumYZ = 0.0f
            sumZZ = 0.0f
            i = 0
            while (i < numPoints) {
                dir.oSet(points.get(i).oMinus(center))
                sumXX += dir.x * dir.x
                sumXY += dir.x * dir.y
                sumXZ += dir.x * dir.z
                sumYY += dir.y * dir.y
                sumYZ += dir.y * dir.z
                sumZZ += dir.z * dir.z
                i++
            }
            sumXX *= invNumPoints
            sumXY *= invNumPoints
            sumXZ *= invNumPoints
            sumYY *= invNumPoints
            sumYZ *= invNumPoints
            sumZZ *= invNumPoints

            // compute eigenvectors for covariance matrix
            eigenValues.SetData(3, idVecX.Companion.VECX_ALLOCA(3))
            eigenVectors.SetData(3, 3, idMatX.Companion.MATX_ALLOCA(3 * 3))
            eigenVectors.oSet(0, 0, sumXX)
            eigenVectors.oSet(0, 1, sumXY)
            eigenVectors.oSet(0, 2, sumXZ)
            eigenVectors.oSet(1, 0, sumXY)
            eigenVectors.oSet(1, 1, sumYY)
            eigenVectors.oSet(1, 2, sumYZ)
            eigenVectors.oSet(2, 0, sumXZ)
            eigenVectors.oSet(2, 1, sumYZ)
            eigenVectors.oSet(2, 2, sumZZ)
            eigenVectors.Eigen_SolveSymmetric(eigenValues)
            eigenVectors.Eigen_SortIncreasing(eigenValues)
            axis.oSet(0, 0, eigenVectors.oGet(0)[0])
            axis.oSet(0, 1, eigenVectors.oGet(0)[1])
            axis.oSet(0, 2, eigenVectors.oGet(0)[2])
            axis.oSet(1, 0, eigenVectors.oGet(1)[0])
            axis.oSet(1, 1, eigenVectors.oGet(1)[1])
            axis.oSet(1, 2, eigenVectors.oGet(1)[2])
            axis.oSet(2, 0, eigenVectors.oGet(2)[0])
            axis.oSet(2, 1, eigenVectors.oGet(2)[1])
            axis.oSet(2, 2, eigenVectors.oGet(2)[2])
            extents.oSet(0, eigenValues.p[0])
            extents.oSet(1, eigenValues.p[0])
            extents.oSet(2, eigenValues.p[0])

            // refine by calculating the bounds of the points projected onto the axis and adjusting the center and extents
            bounds.Clear()
            i = 0
            while (i < numPoints) {
                bounds.AddPoint(
                    idVec3(
                        points.get(i).oMultiply(axis.oGet(0)),
                        points.get(i).oMultiply(axis.oGet(1)),
                        points.get(i).oMultiply(axis.oGet(2))
                    )
                )
                i++
            }
            center.oSet(bounds.oGet(0).oPlus(bounds.oGet(1)).oMultiply(0.5f))
            extents.oSet(bounds.oGet(1).oMinus(center))
            center.oMulSet(axis)
        }

        //					// most tight box for a translation
        //public	void			FromPointTranslation( final idVec3 &point, final idVec3 &translation );
        //public	void			FromBoxTranslation( final idBox &box, final idVec3 &translation );
        //					// most tight box for a rotation
        //public	void			FromPointRotation( final idVec3 &point, final idRotation &rotation );
        //public	void			FromBoxRotation( final idBox &box, final idRotation &rotation );
        //
        fun ToPoints(points: Array<idVec3?>?) {
            val ax = idMat3()
            val temp: Array<idVec3?> = idVec3.Companion.generateArray(4)
            ax.oSet(0, axis.oGet(0).oMultiply(extents.oGet(0)))
            ax.oSet(1, axis.oGet(1).oMultiply(extents.oGet(1)))
            ax.oSet(2, axis.oGet(2).oMultiply(extents.oGet(2)))
            temp[0].oSet(center.oMinus(ax.oGet(0)))
            temp[1].oSet(center.oPlus(ax.oGet(0)))
            temp[2].oSet(ax.oGet(1).oMinus(ax.oGet(2)))
            temp[3].oSet(ax.oGet(1).oPlus(ax.oGet(2)))
            points.get(0).oSet(temp[0].oMinus(temp[3]))
            points.get(1).oSet(temp[1].oMinus(temp[3]))
            points.get(2).oSet(temp[1].oPlus(temp[2]))
            points.get(3).oSet(temp[0].oPlus(temp[2]))
            points.get(4).oSet(temp[0].oMinus(temp[2]))
            points.get(5).oSet(temp[1].oMinus(temp[2]))
            points.get(6).oSet(temp[1].oPlus(temp[3]))
            points.get(7).oSet(temp[0].oPlus(temp[3]))
        }

        fun ToSphere(): idSphere? {
            return idSphere(center, extents.Length())
        }

        //
        //					// calculates the projection of this box onto the given axis
        fun AxisProjection(dir: idVec3?, min: CFloat?, max: CFloat?) {
            val d1 = dir.oMultiply(center)
            val d2 = (Math.abs(extents.oGet(0) * dir.oMultiply(axis.oGet(0)))
                    + Math.abs(extents.oGet(1) * dir.oMultiply(axis.oGet(1)))
                    + Math.abs(extents.oGet(2) * dir.oMultiply(axis.oGet(2))))
            min.setVal(d1 - d2)
            max.setVal(d1 + d2)
        }

        fun AxisProjection(ax: idMat3?, bounds: idBounds?) {
            for (i in 0..2) {
                val d1 = ax.oGet(i).oMultiply(center)
                val d2 = (Math.abs(extents.oGet(0) * ax.oGet(i).oMultiply(axis.oGet(0)))
                        + Math.abs(extents.oGet(1) * ax.oGet(i).oMultiply(axis.oGet(1)))
                        + Math.abs(extents.oGet(2) * ax.oGet(i).oMultiply(axis.oGet(2))))
                bounds.oSet(0, i, d1 - d2)
                bounds.oSet(1, i, d1 + d2)
            }
        }

        //
        // calculates the silhouette of the box
        fun GetProjectionSilhouetteVerts(projectionOrigin: idVec3?, silVerts: Array<idVec3?>?): Int {
            var f: Float
            var i: Int
            var planeBits: Int
            val index: IntArray?
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val dir1 = idVec3()
            val dir2 = idVec3()
            ToPoints(points)
            dir1.oSet(points[0].oMinus(projectionOrigin))
            dir2.oSet(points[6].oMinus(projectionOrigin))
            f = dir1.oMultiply(axis.oGet(0))
            planeBits = Math_h.FLOATSIGNBITNOTSET(f)
            f = dir2.oMultiply(axis.oGet(0))
            planeBits = planeBits or (Math_h.FLOATSIGNBITSET(f) shl 1)
            f = dir1.oMultiply(axis.oGet(1))
            planeBits = planeBits or (Math_h.FLOATSIGNBITNOTSET(f) shl 2)
            f = dir2.oMultiply(axis.oGet(1))
            planeBits = planeBits or (Math_h.FLOATSIGNBITSET(f) shl 3)
            f = dir1.oMultiply(axis.oGet(2))
            planeBits = planeBits or (Math_h.FLOATSIGNBITNOTSET(f) shl 4)
            f = dir2.oMultiply(axis.oGet(2))
            planeBits = planeBits or (Math_h.FLOATSIGNBITSET(f) shl 5)
            index = Box.boxPlaneBitsSilVerts[planeBits]
            i = 0
            while (i < index[0]) {
                silVerts.get(i).oSet(points[index[i + 1]])
                i++
            }
            return index[0]
        }

        fun GetParallelProjectionSilhouetteVerts(projectionDir: idVec3?, silVerts: Array<idVec3?>?): Int {
            var f: Float
            var i: Int
            var planeBits: Int
            val index: IntArray?
            val points: Array<idVec3?> = idVec3.Companion.generateArray(8)
            ToPoints(points)
            planeBits = 0
            f = projectionDir.oMultiply(axis.oGet(0))
            if (Math_h.FLOATNOTZERO(f)) {
                planeBits = 1 shl Math_h.FLOATSIGNBITSET(f)
            }
            f = projectionDir.oMultiply(axis.oGet(1))
            if (Math_h.FLOATNOTZERO(f)) {
                planeBits = planeBits or (4 shl Math_h.FLOATSIGNBITSET(f))
            }
            f = projectionDir.oMultiply(axis.oGet(2))
            if (Math_h.FLOATNOTZERO(f)) {
                planeBits = planeBits or (16 shl Math_h.FLOATSIGNBITSET(f))
            }
            index = Box.boxPlaneBitsSilVerts[planeBits]
            i = 0
            while (i < index[0]) {
                silVerts.get(i).oSet(points[index[i + 1]])
                i++
            }
            return index[0]
        }
    }
}