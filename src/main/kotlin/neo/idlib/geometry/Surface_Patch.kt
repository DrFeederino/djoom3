package neo.idlib.geometry

import neo.idlib.Dict_h.idDict
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.math.Math_h
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Surface_Patch {
    /*
     ===============================================================================

     Bezier patch surface.

     ===============================================================================
     */
    class idSurface_Patch : idSurface {
        //
        //
        //
        //
        //
        //
        //
        //
        var epairs: idDict? = null
        protected var expanded // true if vertices are spaced out
                = false
        protected var height // height of patch
                = 0
        protected var maxHeight // maximum height allocated for
                = 0

        //
        //
        protected var maxWidth // maximum width allocated for
                = 0
        protected var type = 0
        protected var width // width of patch
                = 0

        constructor() {
            maxWidth = 0
            maxHeight = maxWidth
            width = maxHeight
            height = width
            expanded = false
        }

        //public						~idSurface_Patch( void );
        //
        constructor(maxPatchWidth: Int, maxPatchHeight: Int) {
            height = 0
            width = height
            maxWidth = maxPatchWidth
            maxHeight = maxPatchHeight
            verts.SetNum(maxWidth * maxHeight)
            expanded = false
        }

        constructor(patch: idSurface_Patch?) {
            this.oSet(patch)
        }

        constructor(patch: idMapPrimitive?) {
            this.oSet(patch)
        }

        @Throws(Exception::class)
        fun SetSize(patchWidth: Int, patchHeight: Int) {
            if (patchWidth < 1 || patchWidth > maxWidth) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchWidth")
            }
            if (patchHeight < 1 || patchHeight > maxHeight) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchHeight")
            }
            width = patchWidth
            height = patchHeight
            verts.SetNum(width * height, false)
        }

        fun GetWidth(): Int {
            return width
        }

        fun GetHeight(): Int {
            return height
        }

        // subdivide the patch mesh based on error
        @JvmOverloads
        @Throws(idException::class)
        fun Subdivide(
            maxHorizontalError: Float,
            maxVerticalError: Float,
            maxLength: Float,
            genNormals: Boolean = false
        ) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val prev = idDrawVert()
            val next = idDrawVert()
            val mid = idDrawVert()
            val prevxyz = idVec3()
            val nextxyz = idVec3()
            val midxyz = idVec3()
            val delta = idVec3()
            val maxHorizontalErrorSqr: Float
            val maxVerticalErrorSqr: Float
            val maxLengthSqr: Float

            // generate normals for the control mesh
            if (genNormals) {
                GenerateNormals()
            }
            maxHorizontalErrorSqr = Math_h.Square(maxHorizontalError)
            maxVerticalErrorSqr = Math_h.Square(maxVerticalError)
            maxLengthSqr = Math_h.Square(maxLength)
            Expand()

            // horizontal subdivisions
            j = 0
            while (j + 2 < width) {

                // check subdivided midpoints against control points
                i = 0
                while (i < height) {
                    l = 0
                    while (l < 3) {
                        prevxyz.oSet(
                            1,
                            verts.oGet(i * maxWidth + j + 1).xyz.oGet(l) - verts.oGet(i * maxWidth + j).xyz.oGet(l)
                        )
                        nextxyz.oSet(
                            1,
                            verts.oGet(i * maxWidth + j + 2).xyz.oGet(l) - verts.oGet(i * maxWidth + j + 1).xyz.oGet(l)
                        )
                        midxyz.oSet(
                            1,
                            (verts.oGet(i * maxWidth + j).xyz.oGet(l) + verts.oGet(i * maxWidth + j + 1).xyz.oGet(l) * 2.0f + verts.oGet(
                                i * maxWidth + j + 2
                            ).xyz.oGet(l)) * 0.25f
                        )
                        l++
                    }
                    if (maxLength > 0.0f) {
                        // if the span length is too long, force a subdivision
                        if (prevxyz.LengthSqr() > maxLengthSqr || nextxyz.LengthSqr() > maxLengthSqr) {
                            break
                        }
                    }
                    // see if this midpoint is off far enough to subdivide
                    delta.plusAssign(verts.oGet(i * maxWidth + j + 1).xyz.oMinus(midxyz))
                    if (delta.LengthSqr() > maxHorizontalErrorSqr) {
                        break
                    }
                    i++
                }
                if (i == height) {
                    j += 2
                    continue  // didn't need subdivision
                }
                if (width + 2 >= maxWidth) {
                    ResizeExpanded(maxHeight, maxWidth + 4)
                }

                // insert two columns and replace the peak
                width += 2
                i = 0
                while (i < height) {
                    LerpVert(verts.oGet(i * maxWidth + j), verts.oGet(i * maxWidth + j + 1), prev)
                    LerpVert(verts.oGet(i * maxWidth + j + 1), verts.oGet(i * maxWidth + j + 2), next)
                    LerpVert(prev, next, mid)
                    k = width - 1
                    while (k > j + 3) {
                        verts.oSet(i * maxWidth + k, verts.oGet(i * maxWidth + k - 2))
                        k--
                    }
                    verts.oSet(i * maxWidth + j + 1, prev)
                    verts.oSet(i * maxWidth + j + 2, mid)
                    verts.oSet(i * maxWidth + j + 3, next)
                    i++
                }

                // back up and recheck this set again, it may need more subdivision
                j -= 2
                j += 2
            }

            // vertical subdivisions
            j = 0
            while (j + 2 < height) {

                // check subdivided midpoints against control points
                i = 0
                while (i < width) {
                    l = 0
                    while (l < 3) {
                        prevxyz.oSet(
                            1,
                            verts.oGet((j + 1) * maxWidth + i).xyz.oGet(l) - verts.oGet(j * maxWidth + i).xyz.oGet(l)
                        )
                        nextxyz.oSet(
                            1,
                            verts.oGet((j + 2) * maxWidth + i).xyz.oGet(l) - verts.oGet((j + 1) * maxWidth + i).xyz.oGet(
                                l
                            )
                        )
                        midxyz.oSet(
                            1,
                            (verts.oGet(j * maxWidth + i).xyz.oGet(l) + verts.oGet((j + 1) * maxWidth + i).xyz.oGet(l) * 2.0f + verts.oGet(
                                (j + 2) * maxWidth + i
                            ).xyz.oGet(l)) * 0.25f
                        )
                        l++
                    }
                    if (maxLength > 0.0f) {
                        // if the span length is too long, force a subdivision
                        if (prevxyz.LengthSqr() > maxLengthSqr || nextxyz.LengthSqr() > maxLengthSqr) {
                            break
                        }
                    }
                    // see if this midpoint is off far enough to subdivide
                    delta.oSet(verts.oGet((j + 1) * maxWidth + i).xyz.oMinus(midxyz))
                    if (delta.LengthSqr() > maxVerticalErrorSqr) {
                        break
                    }
                    i++
                }
                if (i == width) {
                    j += 2
                    continue  // didn't need subdivision
                }
                if (height + 2 >= maxHeight) {
                    ResizeExpanded(maxHeight + 4, maxWidth)
                }

                // insert two columns and replace the peak
                height += 2
                i = 0
                while (i < width) {
                    LerpVert(verts.oGet(j * maxWidth + i), verts.oGet((j + 1) * maxWidth + i), prev)
                    LerpVert(verts.oGet((j + 1) * maxWidth + i), verts.oGet((j + 2) * maxWidth + i), next)
                    LerpVert(prev, next, mid)
                    k = height - 1
                    while (k > j + 3) {
                        verts.oSet(k * maxWidth + i, verts.oGet((k - 2) * maxWidth + i))
                        k--
                    }
                    verts.oSet((j + 1) * maxWidth + i, prev)
                    verts.oSet((j + 2) * maxWidth + i, mid)
                    verts.oSet((j + 3) * maxWidth + i, next)
                    i++
                }

                // back up and recheck this set again, it may need more subdivision
                j -= 2
                j += 2
            }
            PutOnCurve()
            RemoveLinearColumnsRows()
            Collapse()

            // normalize all the lerped normals
            if (genNormals) {
                i = 0
                while (i < width * height) {
                    verts.oGet(i).normal.Normalize()
                    i++
                }
            }
            GenerateIndexes()
        }

        // subdivide the patch up to an explicit number of horizontal and vertical subdivisions
        //						
        //		
        @JvmOverloads
        @Throws(idException::class)
        fun SubdivideExplicit(
            horzSubdivisions: Int,
            vertSubdivisions: Int,
            genNormals: Boolean,
            removeLinear: Boolean = false
        ) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val sample = Array<Array<idDrawVert?>?>(3) { arrayOfNulls<idDrawVert?>(3) }
            val outWidth = (width - 1) / 2 * horzSubdivisions + 1
            val outHeight = (height - 1) / 2 * vertSubdivisions + 1
            val dv = arrayOfNulls<idDrawVert?>(outWidth * outHeight)

            // generate normals for the control mesh
            if (genNormals) {
                GenerateNormals()
            }
            var baseCol = 0
            i = 0
            while (i + 2 < width) {
                var baseRow = 0
                j = 0
                while (j + 2 < height) {
                    k = 0
                    while (k < 3) {
                        l = 0
                        while (l < 3) {
                            sample[k].get(l) = verts.oGet((j + l) * width + i + k)
                            l++
                        }
                        k++
                    }
                    SampleSinglePatch(sample, baseCol, baseRow, outWidth, horzSubdivisions, vertSubdivisions, dv)
                    baseRow += vertSubdivisions
                    j += 2
                }
                baseCol += horzSubdivisions
                i += 2
            }
            verts.SetNum(outWidth * outHeight)
            i = 0
            while (i < outWidth * outHeight) {
                verts.oSet(i, dv[i])
                i++
            }

//	delete[] dv;
            maxWidth = outWidth
            width = maxWidth
            maxHeight = outHeight
            height = maxHeight
            expanded = false
            if (removeLinear) {
                Expand()
                RemoveLinearColumnsRows()
                Collapse()
            }

            // normalize all the lerped normals
            if (genNormals) {
                i = 0
                while (i < width * height) {
                    verts.oGet(i).normal.Normalize()
                    i++
                }
            }
            GenerateIndexes()
        }

        /*
         =================
         idSurface_Patch::PutOnCurve

         Expects an expanded patch.
         =================
         */
        private fun PutOnCurve() { // put the approximation points on the curve
            var i: Int
            var j: Int
            val prev = idDrawVert()
            val next = idDrawVert()
            assert(expanded == true)
            // put all the approximating points on the curve
            i = 0
            while (i < width) {
                j = 1
                while (j < height) {
                    LerpVert(verts.oGet(j * maxWidth + i), verts.oGet((j + 1) * maxWidth + i), prev)
                    LerpVert(verts.oGet(j * maxWidth + i), verts.oGet((j - 1) * maxWidth + i), next)
                    LerpVert(prev, next, verts.oGet(j * maxWidth + i))
                    j += 2
                }
                i++
            }
            j = 0
            while (j < height) {
                i = 1
                while (i < width) {
                    LerpVert(verts.oGet(j * maxWidth + i), verts.oGet(j * maxWidth + i + 1), prev)
                    LerpVert(verts.oGet(j * maxWidth + i), verts.oGet(j * maxWidth + i - 1), next)
                    LerpVert(prev, next, verts.oGet(j * maxWidth + i))
                    i += 2
                }
                j++
            }
        }

        /*
         ================
         idSurface_Patch::RemoveLinearColumnsRows

         Expects an expanded patch.
         ================
         */
        private fun RemoveLinearColumnsRows() { // remove columns and rows with all points on one line{
            var i: Int
            var j: Int
            var k: Int
            var len: Float
            var maxLength: Float
            val proj = idVec3()
            val dir = idVec3()
            assert(expanded == true)
            j = 1
            while (j < width - 1) {
                maxLength = 0f
                i = 0
                while (i < height) {
                    ProjectPointOntoVector(
                        verts.oGet(i * maxWidth + j).xyz,
                        verts.oGet(i * maxWidth + j - 1).xyz, verts.oGet(i * maxWidth + j + 1).xyz, proj
                    )
                    dir.oSet(verts.oGet(i * maxWidth + j).xyz.oMinus(proj))
                    len = dir.LengthSqr()
                    if (len > maxLength) {
                        maxLength = len
                    }
                    i++
                }
                if (maxLength < Math_h.Square(0.2f)) {
                    width--
                    i = 0
                    while (i < height) {
                        k = j
                        while (k < width) {
                            verts.oSet(i * maxWidth + k, verts.oGet(i * maxWidth + k + 1))
                            k++
                        }
                        i++
                    }
                    j--
                }
                j++
            }
            j = 1
            while (j < height - 1) {
                maxLength = 0f
                i = 0
                while (i < width) {
                    ProjectPointOntoVector(
                        verts.oGet(j * maxWidth + i).xyz,
                        verts.oGet((j - 1) * maxWidth + i).xyz, verts.oGet((j + 1) * maxWidth + i).xyz, proj
                    )
                    dir.oSet(verts.oGet(j * maxWidth + i).xyz.oMinus(proj))
                    len = dir.LengthSqr()
                    if (len > maxLength) {
                        maxLength = len
                    }
                    i++
                }
                if (maxLength < Math_h.Square(0.2f)) {
                    height--
                    i = 0
                    while (i < width) {
                        k = j
                        while (k < height) {
                            verts.oSet(k * maxWidth + i, verts.oGet((k + 1) * maxWidth + i))
                            k++
                        }
                        i++
                    }
                    j--
                }
                j++
            }
        }

        // resize verts buffer
        private fun ResizeExpanded(newHeight: Int, newWidth: Int) {
            var i: Int
            var j: Int
            assert(expanded == true)
            if (newHeight <= maxHeight && newWidth <= maxWidth) {
                return
            }
            if (newHeight * newWidth > maxHeight * maxWidth) {
                verts.SetNum(newHeight * newWidth)
            }
            // space out verts for new height and width
            j = maxHeight - 1
            while (j >= 0) {
                i = maxWidth - 1
                while (i >= 0) {
                    verts.oSet(j * newWidth + i, verts.oGet(j * maxWidth + i))
                    i--
                }
                j--
            }
            maxHeight = newHeight
            maxWidth = newWidth
        }

        // space points out over maxWidth * maxHeight buffer
        @Throws(idException::class)
        private fun Expand() {
            var i: Int
            var j: Int
            if (expanded) {
                idLib.common.FatalError("idSurface_Patch::Expand: patch alread expanded")
            }
            expanded = true
            verts.SetNum(maxWidth * maxHeight, false)
            if (width != maxWidth) {
                j = height - 1
                while (j >= 0) {
                    i = width - 1
                    while (i >= 0) {
                        verts.oSet(j * maxWidth + i, verts.oGet(j * width + i))
                        i--
                    }
                    j--
                }
            }
        }

        // move all points to the start of the verts buffer
        @Throws(idException::class)
        private fun Collapse() {
            var i: Int
            var j: Int
            if (!expanded) {
                idLib.common.FatalError("idSurface_Patch::Collapse: patch not expanded")
            }
            expanded = false
            if (width != maxWidth) {
                j = 0
                while (j < height) {
                    i = 0
                    while (i < width) {
                        verts.oSet(j * width + i, verts.oGet(j * maxWidth + i))
                        i++
                    }
                    j++
                }
            }
            verts.SetNum(width * height, false)
        }

        // project a point onto a vector to calculate maximum curve error
        private fun ProjectPointOntoVector(point: idVec3?, vStart: idVec3?, vEnd: idVec3?, vProj: idVec3?) {
            val pVec = idVec3()
            val vec = idVec3()
            pVec.oSet(point.oMinus(vStart))
            vec.oSet(vEnd.oMinus(vStart))
            vec.Normalize()
            // project onto the directional vector for this segment
            vProj.oSet(vStart.oPlus(vec.times(pVec.times(vec))))
        }

        /*
         =================
         idSurface_Patch::GenerateNormals

         Handles all the complicated wrapping and degenerate cases
         Expects a Not expanded patch.
         =================
         */
        private fun GenerateNormals() { // generate normals
            var i: Int
            var j: Int
            var k: Int
            var dist: Int
            val norm = idVec3()
            val sum = idVec3()
            var count: Int
            val base = idVec3()
            val delta = idVec3()
            var x: Int
            var y: Int
            val around: Array<idVec3?> = idVec3.Companion.generateArray(8)
            val temp = idVec3()
            val good = BooleanArray(8)
            var wrapWidth: Boolean
            var wrapHeight: Boolean
            val neighbors = arrayOf<IntArray?>(
                intArrayOf(0, 1),
                intArrayOf(1, 1),
                intArrayOf(1, 0),
                intArrayOf(1, -1),
                intArrayOf(0, -1),
                intArrayOf(-1, -1),
                intArrayOf(-1, 0),
                intArrayOf(-1, 1)
            )
            assert(expanded == false)

            //
            // if all points are coplanar, set all normals to that plane
            //
            val extent: Array<idVec3?> = idVec3.Companion.generateArray(3)
            val offset: Float
            extent[0].oSet(verts.oGet(width - 1).xyz.oMinus(verts.oGet(0).xyz))
            extent[1].oSet(verts.oGet((height - 1) * width + width - 1).xyz.oMinus(verts.oGet(0).xyz))
            extent[2].oSet(verts.oGet((height - 1) * width).xyz.oMinus(verts.oGet(0).xyz))
            norm.oSet(extent[0].Cross(extent[1]))
            if (norm.LengthSqr() == 0.0f) {
                norm.oSet(extent[0].Cross(extent[2]))
                if (norm.LengthSqr() == 0.0f) {
                    norm.oSet(extent[1].Cross(extent[2]))
                }
            }

            // wrapped patched may not get a valid normal here
            if (norm.Normalize() != 0.0f) {
                offset = verts.oGet(0).xyz.times(norm)
                i = 1
                while (i < width * height) {
                    val d = verts.oGet(i).xyz.times(norm)
                    if (Math.abs(d - offset) > COPLANAR_EPSILON) {
                        break
                    }
                    i++
                }
                if (i == width * height) {
                    // all are coplanar
                    i = 0
                    while (i < width * height) {
                        verts.oGet(i).normal.oSet(norm)
                        i++
                    }
                    return
                }
            }

            // check for wrapped edge cases, which should smooth across themselves
            wrapWidth = false
            i = 0
            while (i < height) {
                delta.oSet(verts.oGet(i * width).xyz.oMinus(verts.oGet(i * width + width - 1).xyz))
                if (delta.LengthSqr() > Math_h.Square(1.0f)) {
                    break
                }
                i++
            }
            if (i == height) {
                wrapWidth = true
            }
            wrapHeight = false
            i = 0
            while (i < width) {
                delta.oSet(verts.oGet(i).xyz.oMinus(verts.oGet((height - 1) * width + i).xyz))
                if (delta.LengthSqr() > Math_h.Square(1.0f)) {
                    break
                }
                i++
            }
            if (i == width) {
                wrapHeight = true
            }
            i = 0
            while (i < width) {
                j = 0
                while (j < height) {
                    count = 0
                    base.oSet(verts.oGet(j * width + i).xyz)
                    k = 0
                    while (k < 8) {
                        around[k].oSet(Vector.getVec3_origin())
                        good[k] = false
                        dist = 1
                        while (dist <= 3) {
                            x = i + neighbors[k].get(0) * dist
                            y = j + neighbors[k].get(1) * dist
                            if (wrapWidth) {
                                if (x < 0) {
                                    x = width - 1 + x
                                } else if (x >= width) {
                                    x = 1 + x - width
                                }
                            }
                            if (wrapHeight) {
                                if (y < 0) {
                                    y = height - 1 + y
                                } else if (y >= height) {
                                    y = 1 + y - height
                                }
                            }
                            if (x < 0 || x >= width || y < 0 || y >= height) {
                                break // edge of patch
                            }
                            temp.oSet(verts.oGet(y * width + x).xyz.oMinus(base))
                            if (temp.Normalize() == 0.0f) {
                                dist++
                                continue  // degenerate edge, get more dist
                            } else {
                                good[k] = true
                                around[k].oSet(temp)
                                break // good edge
                            }
                            dist++
                        }
                        k++
                    }
                    sum.oSet(Vector.getVec3_origin())
                    k = 0
                    while (k < 8) {
                        if (!good[k] || !good[k + 1 and 7]) {
                            k++
                            continue  // didn't get two points
                        }
                        norm.oSet(around[k + 1 and 7].Cross(around[k]))
                        if (norm.Normalize() == 0.0f) {
                            k++
                            continue
                        }
                        sum.plusAssign(norm)
                        count++
                        k++
                    }
                    if (count == 0) {
                        //idLib::common->Printf("bad normal\n");
                        count = 1
                    }
                    verts.oGet(j * width + i).normal.oSet(sum)
                    verts.oGet(j * width + i).normal.Normalize()
                    j++
                }
                i++
            }
        }

        // generate triangle indexes
        private fun GenerateIndexes() {
            var i: Int
            var j: Int
            var v1: Int
            var v2: Int
            var v3: Int
            var v4: Int
            var index: Int
            indexes.SetNum((width - 1) * (height - 1) * 2 * 3, false)
            index = 0
            i = 0
            while (i < width - 1) {
                j = 0
                while (j < height - 1) {
                    v1 = j * width + i
                    v2 = v1 + 1
                    v3 = v1 + width + 1
                    v4 = v1 + width
                    indexes.oSet(index++, v1)
                    indexes.oSet(index++, v3)
                    indexes.oSet(index++, v2)
                    indexes.oSet(index++, v1)
                    indexes.oSet(index++, v4)
                    indexes.oSet(index++, v3)
                    j++
                }
                i++
            }
            GenerateEdgeIndexes()
        }

        // lerp point from two patch point
        private fun LerpVert(a: idDrawVert?, b: idDrawVert?, out: idDrawVert?) {
            out.xyz.oSet(0, 0.5f * (a.xyz.oGet(0) + b.xyz.oGet(0)))
            out.xyz.oSet(1, 0.5f * (a.xyz.oGet(1) + b.xyz.oGet(1)))
            out.xyz.oSet(2, 0.5f * (a.xyz.oGet(2) + b.xyz.oGet(2)))
            out.normal.oSet(0, 0.5f * (a.normal.oGet(0) + b.normal.oGet(0)))
            out.normal.oSet(1, 0.5f * (a.normal.oGet(1) + b.normal.oGet(1)))
            out.normal.oSet(2, 0.5f * (a.normal.oGet(2) + b.normal.oGet(2)))
            out.st.oSet(0, 0.5f * (a.st.oGet(0) + b.st.oGet(0)))
            out.st.oSet(1, 0.5f * (a.st.oGet(1) + b.st.oGet(1)))
        }

        // sample a single 3x3 patch
        private fun SampleSinglePatchPoint(ctrl: Array<Array<idDrawVert?>?>?, u: Float, v: Float, out: idDrawVert?) {
            val vCtrl = Array<FloatArray?>(3) { FloatArray(8) }
            var vPoint: Int
            var axis: Int

            // find the control points for the v coordinate
            vPoint = 0
            while (vPoint < 3) {
                axis = 0
                while (axis < 8) {
                    var a: Float
                    var b: Float
                    var c: Float
                    var qA: Float
                    var qB: Float
                    var qC: Float
                    if (axis < 3) {
                        a = ctrl.get(0).get(vPoint).xyz.oGet(axis)
                        b = ctrl.get(1).get(vPoint).xyz.oGet(axis)
                        c = ctrl.get(2).get(vPoint).xyz.oGet(axis)
                    } else if (axis < 6) {
                        a = ctrl.get(0).get(vPoint).normal.oGet(axis - 3)
                        b = ctrl.get(1).get(vPoint).normal.oGet(axis - 3)
                        c = ctrl.get(2).get(vPoint).normal.oGet(axis - 3)
                    } else {
                        a = ctrl.get(0).get(vPoint).st.oGet(axis - 6)
                        b = ctrl.get(1).get(vPoint).st.oGet(axis - 6)
                        c = ctrl.get(2).get(vPoint).st.oGet(axis - 6)
                    }
                    qA = a - 2.0f * b + c
                    qB = 2.0f * b - 2.0f * a
                    qC = a
                    vCtrl[vPoint].get(axis) = qA * u * u + qB * u + qC
                    axis++
                }
                vPoint++
            }

            // interpolate the v value
            axis = 0
            while (axis < 8) {
                var a: Float
                var b: Float
                var c: Float
                var qA: Float
                var qB: Float
                var qC: Float
                a = vCtrl[0].get(axis)
                b = vCtrl[1].get(axis)
                c = vCtrl[2].get(axis)
                qA = a - 2.0f * b + c
                qB = 2.0f * b - 2.0f * a
                qC = a
                if (axis < 3) {
                    out.xyz.oSet(axis, qA * v * v + qB * v + qC)
                } else if (axis < 6) {
                    out.normal.oSet(axis - 3, qA * v * v + qB * v + qC)
                } else {
                    out.st.oSet(axis - 6, qA * v * v + qB * v + qC)
                }
                axis++
            }
        }

        private fun SampleSinglePatch(
            ctrl: Array<Array<idDrawVert?>?>?,
            baseCol: Int,
            baseRow: Int,
            width: Int,
            horzSub: Int,
            vertSub: Int,
            outVerts: Array<idDrawVert?>?
        ) {
            var horzSub = horzSub
            var vertSub = vertSub
            var i: Int
            var j: Int
            var u: Float
            var v: Float
            horzSub++
            vertSub++
            i = 0
            while (i < horzSub) {
                j = 0
                while (j < vertSub) {
                    u = i.toFloat() / (horzSub - 1)
                    v = j.toFloat() / (vertSub - 1)
                    SampleSinglePatchPoint(ctrl, u, v, outVerts.get((baseRow + j) * width + i + baseCol))
                    j++
                }
                i++
            }
        }

        private fun oSet(patch: idSurface_Patch?) {
            width = patch.width
            height = patch.height
            maxWidth = patch.maxWidth
            maxHeight = patch.maxHeight
            verts.SetNum(maxWidth * maxHeight)
            expanded = patch.expanded
        }

        private fun oSet(patch: idMapPrimitive?) {
            type = patch.GetType()
            epairs = patch.epairs
        }

        companion object {
            //
            const val COPLANAR_EPSILON = 0.1f
        }
    }
}