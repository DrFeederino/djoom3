package neo.Tools.Compilers.DMap

import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object ubrush {
    const val CLIP_EPSILON = 0.1f

    //
    // if a brush just barely pokes onto the other side,
    // let it slide by without chopping
    const val PLANESIDE_EPSILON = 0.001
    const val PSIDE_BACK = 2
    const val PSIDE_FACING = 4

    //
    const val PSIDE_FRONT = 1
    const val PSIDE_BOTH = ubrush.PSIDE_FRONT or ubrush.PSIDE_BACK

    //
    var c_active_brushes = 0

    //
    var c_nodes = 0

    //0.1
    /*
     ================
     CountBrushList
     ================
     */
    fun CountBrushList(brushes: uBrush_t?): Int {
        var brushes = brushes
        var c: Int
        c = 0
        while (brushes != null) {
            c++
            brushes = brushes.next as uBrush_t
        }
        return c
    }

    @Deprecated("")
    fun BrushSizeForSides(numsides: Int): Int {
        throw UnsupportedOperationException()
        //        int c;
//
//        // allocate a structure with a variable number of sides at the end
//        //	c = (int)&(((uBrush_t *)0).sides[numsides]);	// bounds checker complains about this
//        c = sizeof(uBrush_t) + sizeof(side_t) * (numsides - 6);
//
//        return c;
    }

    /*
     ================
     AllocBrush
     ================
     */
    @Deprecated("")
    fun AllocBrush(numsides: Int): Array<uBrush_t?>? {
        throw UnsupportedOperationException()
        //        uBrush_t[] bb;
//        int c;
//
//        c = BrushSizeForSides(numsides);
//
//        bb = new uBrush_t[c];// Mem_Alloc(c);
//        //	memset (bb, 0, c);
//        c_active_brushes++;
//        return bb;
    }

    /*
     ================
     FreeBrush
     ================
     */
    @Deprecated("")
    fun FreeBrush(brushes: uBrush_t?) {
        var i: Int
        i = 0
        while (i < brushes.numsides) {
            if (brushes.sides[i].winding != null) {
                //			delete brushes.sides[i].winding;
                brushes.sides[i].winding = null
            }
            if (brushes.sides[i].visibleHull != null) {
                //			delete brushes.sides[i].visibleHull;
                brushes.sides[i].visibleHull = null
            }
            i++
        }
        brushes.clear() //Mem_Free(brushes);
        ubrush.c_active_brushes--
    }

    /*
     ================
     FreeBrushList
     ================
     */
    fun FreeBrushList(brushes: uBrush_t?) {
        var brushes = brushes
        var next: uBrush_t
        while (brushes != null) {
            next = brushes.next as uBrush_t
            ubrush.FreeBrush(brushes)
            brushes = next
        }
    }

    /*
     ==================
     CopyBrush

     Duplicates the brush, the sides, and the windings
     ==================
     */
    fun CopyBrush(brush: uBrush_t?): uBrush_t? {
        val newBrush: uBrush_t
        var size: Int
        var i: Int
        //
//        size = BrushSizeForSides(brush.numsides);
//
//        newbrush = AllocBrush(brush.numsides);
//        memcpy(newbrush, brush, size);
        newBrush = uBrush_t(brush)
        ubrush.c_active_brushes++
        i = 0
        while (i < brush.numsides) {
            if (brush.sides[i].winding != null) {
                newBrush.sides[i].winding = brush.sides[i].winding.Copy()
            }
            i++
        }
        return newBrush
    }

    /*
     ================
     DrawBrushList
     ================
     */
    fun DrawBrushList(brush: uBrush_t?) {
        var brush = brush
        var i: Int
        var s: side_s?
        gldraw.GLS_BeginScene()
        while (brush != null) {
            i = 0
            while (i < brush.numsides) {
                s = brush.sides[i]
                if (TempDump.NOT(s.winding)) {
                    i++
                    continue
                }
                gldraw.GLS_Winding(s.winding, 0)
                i++
            }
            brush = brush.next as uBrush_t
        }
        gldraw.GLS_EndScene()
    }

    /*
     =============
     PrintBrush
     =============
     */
    fun PrintBrush(brush: uBrush_t?) {
        var i: Int
        Common.common.Printf("brush: %p\n", brush)
        i = 0
        while (i < brush.numsides) {
            brush.sides[i].winding.Print()
            Common.common.Printf("\n")
            i++
        }
    }

    /*
     ==================
     BoundBrush

     Sets the mins/maxs based on the windings
     returns false if the brush doesn't enclose a valid volume
     ==================
     */
    fun BoundBrush(brush: uBrush_t?): Boolean {
        var i: Int
        var j: Int
        var w: idWinding?
        brush.bounds.Clear()
        i = 0
        while (i < brush.numsides) {
            w = brush.sides[i].winding
            if (TempDump.NOT(w)) {
                i++
                continue
            }
            j = 0
            while (j < w.GetNumPoints()) {
                brush.bounds.AddPoint(w.get(j).ToVec3())
                j++
            }
            i++
        }
        i = 0
        while (i < 3) {
            if (brush.bounds.get(0, i) < Lib.Companion.MIN_WORLD_COORD || brush.bounds.get(
                    1,
                    i
                ) > Lib.Companion.MAX_WORLD_COORD || brush.bounds.get(0, i) >= brush.bounds.get(1, i)
            ) {
                return false
            }
            i++
        }
        return true
    }

    /*
     ==================
     CreateBrushWindings

     makes basewindigs for sides and mins / maxs for the brush
     returns false if the brush doesn't enclose a valid volume
     ==================
     */
    fun CreateBrushWindings(brush: uBrush_t?): Boolean {
        var i: Int
        var j: Int
        var w: idWinding?
        var plane: idPlane?
        var side: side_s?
        i = 0
        while (i < brush.numsides) {
            side = brush.sides[i]
            plane = dmap.dmapGlobals.mapPlanes.get(side.planenum)
            w = idWinding(plane)
            j = 0
            while (j < brush.numsides && w != null) {
                if (i == j) {
                    j++
                    continue
                }
                if (brush.sides[j].planenum == brush.sides[i].planenum xor 1) {
                    j++
                    continue  // back side clipaway
                }
                plane = dmap.dmapGlobals.mapPlanes.get(brush.sides[j].planenum xor 1)
                w = w.Clip(plane, 0f) //CLIP_EPSILON);
                j++
            }
            if (side.winding != null) {
                //			delete side.winding;
                side.winding = null
            }
            side.winding = w
            i++
        }
        return ubrush.BoundBrush(brush)
    }

    /*
     ==================
     BrushFromBounds

     Creates a new axial brush
     ==================
     */
    fun BrushFromBounds(bounds: idBounds?): uBrush_t? {
        val b: uBrush_t
        var i: Int
        val plane = idPlane()
        b = uBrush_t() //AllocBrush(6);
        ubrush.c_active_brushes++
        b.numsides = 6
        i = 0
        while (i < 3) {
            plane.set(0, plane.set(1, plane.set(2, 0f)))
            plane.set(i, 1f)
            plane.set(3, -bounds.get(1, i))
            b.sides[i].planenum = FindFloatPlane(plane)
            plane.set(i, -1f)
            plane.set(3, bounds.get(0, i))
            b.sides[3 + i].planenum = FindFloatPlane(plane)
            i++
        }
        ubrush.CreateBrushWindings(b)
        return b
    }

    /*
     ==================
     BrushVolume

     ==================
     */
    fun BrushVolume(brush: uBrush_t?): Float {
        var i: Int
        var w: idWinding?
        val corner = idVec3()
        var d: Float
        var area: Float
        var volume: Float
        if (TempDump.NOT(brush)) {
            return 0
        }

        // grab the first valid point as the corner
        w = null
        i = 0
        while (i < brush.numsides) {
            w = brush.sides[i].winding
            if (w != null) {
                break
            }
            i++
        }
        if (TempDump.NOT(w)) {
            return 0
        }
        Vector.VectorCopy(w.get(0), corner)

        // make tetrahedrons to all other faces
        volume = 0f
        while (i < brush.numsides) {
            w = brush.sides[i].winding
            if (TempDump.NOT(w)) {
                i++
                continue
            }
            val plane = dmap.dmapGlobals.mapPlanes.get(brush.sides[i].planenum)
            d = -plane.Distance(corner)
            area = w.GetArea()
            volume += d * area
            i++
        }
        volume /= 3f
        return volume
    }

    /*
     ==================
     WriteBspBrushMap

     FIXME: use new brush format
     ==================
     */
    fun WriteBspBrushMap(name: String?, list: uBrush_t?) {
        var list = list
        val f: idFile?
        var s: side_s?
        var i: Int
        var w: idWinding
        Common.common.Printf("writing %s\n", name)
        f = FileSystem_h.fileSystem.OpenFileWrite(name)
        if (TempDump.NOT(f)) {
            Common.common.Error("Can't write %s\b", name)
        }
        f.Printf("{\n\"classname\" \"worldspawn\"\n")
        while (list != null) {
            f.Printf("{\n")
            s = list.sides[0.also { i = it }]
            while (i < list.numsides) {
                w = idWinding(dmap.dmapGlobals.mapPlanes.get(s.planenum))
                f.Printf(
                    "( %d %d %d ) ",
                    w.get(0).get(0).toInt(),
                    w.get(0).get(1).toInt(),
                    w.get(0).get(2).toInt()
                )
                f.Printf(
                    "( %d %d %d ) ",
                    w.get(1).get(0).toInt(),
                    w.get(1).get(1).toInt(),
                    w.get(1).get(2).toInt()
                )
                f.Printf(
                    "( %d %d %d ) ",
                    w.get(2).get(0).toInt(),
                    w.get(2).get(1).toInt(),
                    w.get(2).get(2).toInt()
                )
                f.Printf("notexture 0 0 0 1 1\n")
                s = list.sides[++i]
            }
            f.Printf("}\n")
            list = list.next as uBrush_t
        }
        f.Printf("}\n")
        FileSystem_h.fileSystem.CloseFile(f)
    }

    //=====================================================================================
    /*
     ====================
     FilterBrushIntoTree_r

     ====================
     */
    fun FilterBrushIntoTree_r(b: uBrush_t?, node: node_s?): Int {
        val front = uBrush_t()
        val back = uBrush_t()
        var c: Int
        if (TempDump.NOT(b)) {
            return 0
        }

        // add it to the leaf list
        if (node.planenum == dmap.PLANENUM_LEAF) {
            b.next = node.brushlist
            node.brushlist = b

            // classify the leaf by the structural brush
            if (b.opaque) {
                node.opaque = true
            }
            return 1
        }

        // split it by the node plane
        ubrush.SplitBrush(b, node.planenum, front, back)
        ubrush.FreeBrush(b)
        c = 0
        c += ubrush.FilterBrushIntoTree_r(front, node.children[0])
        c += ubrush.FilterBrushIntoTree_r(back, node.children[1])
        return c
    }

    /*
     =====================
     FilterBrushesIntoTree

     Mark the leafs as opaque and areaportals and put brush
     fragments in each leaf so portal surfaces can be matched
     to materials
     =====================
     */
    fun FilterBrushesIntoTree(e: uEntity_t?) {
        var prim: primitive_s?
        var b: uBrush_t
        var newb: uBrush_t?
        var r: Int
        var c_unique: Int
        var c_clusters: Int
        Common.common.Printf("----- FilterBrushesIntoTree -----\n")
        c_unique = 0
        c_clusters = 0
        prim = e.primitives
        while (prim != null) {
            b = prim.brush as uBrush_t
            if (TempDump.NOT(b)) {
                prim = prim.next
                continue
            }
            c_unique++
            newb = ubrush.CopyBrush(b)
            r = ubrush.FilterBrushIntoTree_r(newb, e.tree.headnode)
            c_clusters += r
            prim = prim.next
        }
        Common.common.Printf("%5d total brushes\n", c_unique)
        Common.common.Printf("%5d cluster references\n", c_clusters)
    }

    /*
     ================
     AllocTree
     ================
     */
    fun AllocTree(): tree_s? {
        val tree: tree_s
        tree = tree_s() // Mem_Alloc(sizeof(tree));
        //	memset (tree, 0, sizeof(*tree));
        tree.bounds.Clear()
        return tree
    }

    /*
     ================
     AllocNode
     ================
     */
    fun AllocNode(): node_s? {
        val node: node_s
        node = node_s() // Mem_Alloc(sizeof(node));
        //	memset (node, 0, sizeof(*node));
        return node
    }

    //============================================================
    /*
     ==================
     BrushMostlyOnSide

     ==================
     */
    fun BrushMostlyOnSide(brush: uBrush_t?, plane: idPlane?): Int {
        var i: Int
        var j: Int
        var w: idWinding?
        var d: Float
        var max: Float
        var side: Int
        max = 0f
        side = ubrush.PSIDE_FRONT
        i = 0
        while (i < brush.numsides) {
            w = brush.sides[i].winding
            if (TempDump.NOT(w)) {
                i++
                continue
            }
            j = 0
            while (j < w.GetNumPoints()) {
                d = plane.Distance(w.get(j).ToVec3())
                if (d > max) {
                    max = d
                    side = ubrush.PSIDE_FRONT
                }
                if (-d > max) {
                    max = -d
                    side = ubrush.PSIDE_BACK
                }
                j++
            }
            i++
        }
        return side
    }

    /*
     ================
     SplitBrush

     Generates two new brushes, leaving the original
     unchanged
     ================
     */
    fun SplitBrush(brush: uBrush_t?, planenum: Int, front: uBrush_t?, back: uBrush_t?) {
        val b = arrayOfNulls<uBrush_t?>(2)
        var i: Int
        var j: Int
        var w: idWinding?
        val midwinding: idWinding?
        val cw = arrayOfNulls<idWinding?>(2)
        var s: side_s?
        var cs: side_s?
        var d: Float
        var d_front: Float
        var d_back: Float
        val plane = dmap.dmapGlobals.mapPlanes.get(planenum)

        // check all points
        d_back = 0f
        d_front = d_back
        i = 0
        while (i < brush.numsides) {
            w = brush.sides[i].winding
            if (TempDump.NOT(w)) {
                i++
                continue
            }
            j = 0
            while (j < w.GetNumPoints()) {
                d = plane.Distance(w.get(j).ToVec3())
                if (d > 0 && d > d_front) {
                    d_front = d
                }
                if (d < 0 && d < d_back) {
                    d_back = d
                }
                j++
            }
            i++
        }
        if (d_front < 0.1) // PLANESIDE_EPSILON)
        {    // only on back
            back.set(ubrush.CopyBrush(brush))
            return
        }
        if (d_back > -0.1) // PLANESIDE_EPSILON)
        {    // only on front
            front.set(ubrush.CopyBrush(brush))
            return
        }

        // create a new winding from the split plane
        w = idWinding(plane)
        i = 0
        while (i < brush.numsides && w != null) {
            val plane2 = dmap.dmapGlobals.mapPlanes.get(brush.sides[i].planenum xor 1)
            w = w.Clip(plane2, 0f) // PLANESIDE_EPSILON);
            i++
        }
        if (TempDump.NOT(w) || w.IsTiny()) {
            // the brush isn't really split
            val side: Int
            side = ubrush.BrushMostlyOnSide(brush, plane)
            if (side == ubrush.PSIDE_FRONT) {
                front.set(ubrush.CopyBrush(brush))
            }
            if (side == ubrush.PSIDE_BACK) {
                back.set(ubrush.CopyBrush(brush))
            }
            return
        }
        if (w.IsHuge()) {
            Common.common.Printf("WARNING: huge winding\n")
        }
        midwinding = w

        // split it for real
        i = 0
        while (i < 2) {
            b[i] = uBrush_t(brush) //AllocBrush(brush.numsides + 1);
            //            memcpy(b[i], brush, sizeof(uBrush_t) - sizeof(brush.sides));
            ubrush.c_active_brushes++
            b[i].numsides = 0
            b[i].next = null
            b[i].original = brush.original
            i++
        }

        // split all the current windings
        i = 0
        while (i < brush.numsides) {
            s = brush.sides[i]
            w = s.winding
            if (TempDump.NOT(w)) {
                i++
                continue
            }
            w.Split(plane, 0f, cw[0], cw[1])
            j = 0
            while (j < 2) {
                if (TempDump.NOT(cw[j])) {
                    j++
                    continue
                }
                /*
                 if ( cw[j].IsTiny() )
                 {
                 delete cw[j];
                 continue;
                 }
                 */b[j].sides[b[j].numsides] = s
                cs = b[j].sides[b[j].numsides]
                b[j].numsides++
                //                cs = s;
                cs.winding = cw[j]
                j++
            }
            i++
        }

        // see if we have valid polygons on both sides
        i = 0
        while (i < 2) {
            if (!ubrush.BoundBrush(b[i])) {
                break
            }
            if (b[i].numsides < 3) {
                ubrush.FreeBrush(b[i])
                b[i] = null
            }
            i++
        }
        if (!(b[0] != null && b[1] != null)) {
            if (TempDump.NOT(b[0]) && TempDump.NOT(b[1])) {
                Common.common.Printf("split removed brush\n")
            } else {
                Common.common.Printf("split not on both sides\n")
            }
            if (b[0] != null) {
                ubrush.FreeBrush(b[0])
                front.set(ubrush.CopyBrush(brush))
            }
            if (b[1] != null) {
                ubrush.FreeBrush(b[1])
                back.set(ubrush.CopyBrush(brush))
            }
            return
        }

        // add the midwinding to both sides
        i = 0
        while (i < 2) {
            cs = b[i].sides[b[i].numsides]
            b[i].numsides++
            cs.planenum = planenum xor i xor 1
            cs.material = null
            if (i == 0) {
                cs.winding = midwinding.Copy()
            } else {
                cs.winding = midwinding
            }
            i++
        }
        run {
            var v1: Float
            var i2: Int
            i2 = 0
            while (i2 < 2) {
                v1 = ubrush.BrushVolume(b[i2])
                if (v1 < 1.0) {
                    ubrush.FreeBrush(b[i2])
                    b[i2] = null
                    //			common.Printf ("tiny volume after clip\n");
                }
                i2++
            }
        }
        front.set(b[0])
        back.set(b[1])
    }
}