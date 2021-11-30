package neo.Tools.Compilers.DMap

import neo.Renderer.Material
import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.bspface_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.framework.Common
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.sys.win_shared

/**
 *
 */
object facebsp {
    //
    const val BLOCK_SIZE = 1024

    //
    //
    var c_nodes = 0
    var c_faceLeafs = 0

    //void RemovePortalFromNode( uPortal_s *portal, node_s *l );
    fun NodeForPoint(node: node_s?, origin: idVec3?): node_s? {
        var node = node
        var d: Float
        while (node.planenum != dmap.PLANENUM_LEAF) {
            val plane = dmap.dmapGlobals.mapPlanes.get(node.planenum)
            d = plane.Distance(origin)
            node = if (d >= 0) {
                node.children[0]
            } else {
                node.children[1]
            }
        }
        return node
    }

    /*
     =============
     FreeTreePortals_r
     =============
     */
    fun FreeTreePortals_r(node: node_s?) {
        var p: uPortal_s?
        var nextp: uPortal_s?
        var s: Int

        // free children
        if (node.planenum != dmap.PLANENUM_LEAF) {
            facebsp.FreeTreePortals_r(node.children[0])
            facebsp.FreeTreePortals_r(node.children[1])
        }

        // free portals
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0
            nextp = p.next[s]
            portals.RemovePortalFromNode(p, p.nodes[1 xor s])
            portals.FreePortal(p)
            p = nextp
        }
        node.portals = null
    }

    /*
     =============
     FreeTree_r
     =============
     */
    fun FreeTree_r(node: node_s?) {
        // free children
        if (node.planenum != dmap.PLANENUM_LEAF) {
            facebsp.FreeTree_r(node.children[0])
            facebsp.FreeTree_r(node.children[1])
        }

        // free brushes
        ubrush.FreeBrushList(node.brushlist)

        // free the node
        facebsp.c_nodes--
        node.clear() //Mem_Free(node);
    }

    /*
     =============
     FreeTree
     =============
     */
    fun FreeTree(tree: tree_s?) {
        if (TempDump.NOT(tree)) {
            return
        }
        facebsp.FreeTreePortals_r(tree.headnode)
        facebsp.FreeTree_r(tree.headnode)
        tree.clear() //Mem_Free(tree);
    }

    //===============================================================
    fun PrintTree_r(node: node_s?, depth: Int) {
        var i: Int
        var bb: uBrush_t?
        i = 0
        while (i < depth) {
            Common.common.Printf("  ")
            i++
        }
        if (node.planenum == dmap.PLANENUM_LEAF) {
            if (TempDump.NOT(node.brushlist)) {
                Common.common.Printf("NULL\n")
            } else {
                bb = node.brushlist
                while (bb != null) {
                    Common.common.Printf("%d ", bb.original.brushnum)
                    bb = bb.next as uBrush_t
                }
                Common.common.Printf("\n")
            }
            return
        }
        val plane = dmap.dmapGlobals.mapPlanes.get(node.planenum)
        Common.common.Printf(
            "#%d (%5.2f %5.2f %5.2f %5.2f)\n", node.planenum,
            plane.get(0), plane.get(1), plane.get(2), plane.get(3)
        )
        facebsp.PrintTree_r(node.children[0], depth + 1)
        facebsp.PrintTree_r(node.children[1], depth + 1)
    }

    /*
     ================
     AllocBspFace
     ================
     */
    fun AllocBspFace(): bspface_s? {
        val f: bspface_s
        f = bspface_s() // Mem_Alloc(sizeof(f));
        //	memset( f, 0, sizeof(*f) );
        return f
    }

    /*
     ================
     FreeBspFace
     ================
     */
    fun FreeBspFace(f: bspface_s?) {
        if (f.w != null) {
            //		delete f.w;
            f.w = null
        }
        f.clear() //Mem_Free(f);
    }

    /*
     ================
     SelectSplitPlaneNum
     ================
     */
    fun SelectSplitPlaneNum(node: node_s?, list: bspface_s?): Int {
        var split: bspface_s?
        var check: bspface_s?
        var bestSplit: bspface_s?
        var splits: Int
        var facing: Int
        var front: Int
        var back: Int
        var side: Int
        var value: Int
        var bestValue: Int
        val plane = idPlane()
        val planenum: Int
        var havePortals: Boolean
        var dist: Float
        val halfSize = idVec3()

        // if it is crossing a 1k block boundary, force a split
        // this prevents epsilon problems from extending an
        // arbitrary distance across the map
        halfSize.set(node.bounds.get(1).minus(node.bounds.get(0)).oMultiply(0.5f))
        for (axis in 0..2) {
            dist = if (halfSize.get(axis) > facebsp.BLOCK_SIZE) {
                (facebsp.BLOCK_SIZE * (Math.floor(
                    ((node.bounds.get(
                        0,
                        axis
                    ) + halfSize.get(axis)) / facebsp.BLOCK_SIZE).toDouble()
                ) + 1.0f)).toFloat()
            } else {
                (facebsp.BLOCK_SIZE * (Math.floor(
                    (node.bounds.get(
                        0,
                        axis
                    ) / facebsp.BLOCK_SIZE).toDouble()
                ) + 1.0f)).toFloat()
            }
            if (dist > node.bounds.get(0, axis) + 1.0f && dist < node.bounds.get(1, axis) - 1.0f) {
                plane.set(0, plane.set(1, plane.set(2, 0.0f)))
                plane.set(axis, 1.0f)
                plane.set(3, -dist)
                planenum = FindFloatPlane(plane)
                return planenum
            }
        }

        // pick one of the face planes
        // if we have any portal faces at all, only
        // select from them, otherwise select from
        // all faces
        bestValue = -999999
        bestSplit = list
        havePortals = false
        split = list
        while (split != null) {
            split.checked = false
            if (split.portal) {
                havePortals = true
            }
            split = split.next
        }
        split = list
        while (split != null) {
            if (split.checked) {
                split = split.next
                continue
            }
            if (havePortals != split.portal) {
                split = split.next
                continue
            }
            val mapPlane = dmap.dmapGlobals.mapPlanes.get(split.planenum)
            splits = 0
            facing = 0
            front = 0
            back = 0
            check = list
            while (check != null) {
                if (check.planenum == split.planenum) {
                    facing++
                    check.checked = true // won't need to test this plane again
                    check = check.next
                    continue
                }
                side = check.w.PlaneSide(mapPlane)
                if (side == Plane.SIDE_CROSS) {
                    splits++
                } else if (side == Plane.SIDE_FRONT) {
                    front++
                } else if (side == Plane.SIDE_BACK) {
                    back++
                }
                check = check.next
            }
            value = 5 * facing - 5 * splits // - abs(front-back);
            if (mapPlane.Type() < Plane.PLANETYPE_TRUEAXIAL) {
                value += 5 // axial is better
            }
            if (value > bestValue) {
                bestValue = value
                bestSplit = split
            }
            split = split.next
        }
        return if (bestValue == -999999) {
            -1
        } else bestSplit.planenum
    }

    /*
     ================
     BuildFaceTree_r
     ================
     */
    fun BuildFaceTree_r(node: node_s?, list: bspface_s?) {
        var split: bspface_s?
        var next: bspface_s?
        var side: Int
        var newFace: bspface_s?
        val childLists = arrayOfNulls<bspface_s?>(2)
        val frontWinding = idWinding()
        val backWinding = idWinding()
        var i: Int
        val splitPlaneNum: Int
        splitPlaneNum = facebsp.SelectSplitPlaneNum(node, list)
        // if we don't have any more faces, this is a node
        if (splitPlaneNum == -1) {
            node.planenum = dmap.PLANENUM_LEAF
            facebsp.c_faceLeafs++
            return
        }

        // partition the list
        node.planenum = splitPlaneNum
        val plane = dmap.dmapGlobals.mapPlanes.get(splitPlaneNum)
        childLists[0] = null
        childLists[1] = null
        split = list
        while (split != null) {
            next = split.next
            if (split.planenum == node.planenum) {
                facebsp.FreeBspFace(split)
                split = next
                continue
            }
            side = split.w.PlaneSide(plane)
            if (side == Plane.SIDE_CROSS) {
                split.w.Split(plane, ubrush.CLIP_EPSILON * 2, frontWinding, backWinding)
                if (!frontWinding.isNULL) {
                    newFace = facebsp.AllocBspFace()
                    newFace.w = frontWinding
                    newFace.next = childLists[0]
                    newFace.planenum = split.planenum
                    childLists[0] = newFace
                }
                if (!backWinding.isNULL) {
                    newFace = facebsp.AllocBspFace()
                    newFace.w = backWinding
                    newFace.next = childLists[1]
                    newFace.planenum = split.planenum
                    childLists[1] = newFace
                }
                facebsp.FreeBspFace(split)
            } else if (side == Plane.SIDE_FRONT) {
                split.next = childLists[0]
                childLists[0] = split
            } else if (side == Plane.SIDE_BACK) {
                split.next = childLists[1]
                childLists[1] = split
            }
            split = next
        }

        // recursively process children
        i = 0
        while (i < 2) {
            node.children[i] = ubrush.AllocNode()
            node.children[i].parent = node
            node.children[i].bounds = node.bounds
            i++
        }

        // split the bounds if we have a nice axial plane
        i = 0
        while (i < 3) {
            if (Math.abs(plane.get(i) - 1.0f) < 0.001) {
                node.children[0].bounds.set(0, i, plane.Dist())
                node.children[1].bounds.set(1, i, plane.Dist())
                break
            }
            i++
        }
        i = 0
        while (i < 2) {
            facebsp.BuildFaceTree_r(node.children[i], childLists[i])
            i++
        }
    }

    /*
     ================
     FaceBSP

     List will be freed before returning
     ================
     */
    fun FaceBSP(list: bspface_s?): tree_s? {
        val tree: tree_s?
        var face: bspface_s?
        var i: Int
        var count: Int
        val start: Int
        val end: Int
        start = win_shared.Sys_Milliseconds()
        Common.common.Printf("--- FaceBSP ---\n")
        tree = ubrush.AllocTree()
        count = 0
        tree.bounds.Clear()
        face = list
        while (face != null) {
            count++
            i = 0
            while (i < face.w.GetNumPoints()) {
                tree.bounds.AddPoint(face.w.get(i).ToVec3())
                i++
            }
            face = face.next
        }
        Common.common.Printf("%5d faces\n", count)
        tree.headnode = ubrush.AllocNode()
        tree.headnode.bounds = tree.bounds
        facebsp.c_faceLeafs = 0
        facebsp.BuildFaceTree_r(tree.headnode, list)
        Common.common.Printf("%5d leafs\n", facebsp.c_faceLeafs)
        end = win_shared.Sys_Milliseconds()
        Common.common.Printf("%5.1f seconds faceBsp\n", (end - start) / 1000.0)
        return tree
    }

    //==========================================================================
    /*
     =================
     MakeStructuralBspFaceList
     =================
     */
    fun MakeStructuralBspFaceList(list: primitive_s?): bspface_s? {
        var list = list
        var b: uBrush_t
        var i: Int
        var s: side_s?
        var w: idWinding?
        var f: bspface_s?
        var flist: bspface_s?
        flist = null
        while (list != null) {
            b = list.brush as uBrush_t
            if (TempDump.NOT(b)) {
                list = list.next
                continue
            }
            if (!b.opaque && 0 == b.contents and Material.CONTENTS_AREAPORTAL) {
                list = list.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                s = b.sides[i]
                w = s.winding
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                if (b.contents and Material.CONTENTS_AREAPORTAL != 0 && 0 == s.material.GetContentFlags() and Material.CONTENTS_AREAPORTAL) {
                    i++
                    continue
                }
                f = facebsp.AllocBspFace()
                if (s.material.GetContentFlags() and Material.CONTENTS_AREAPORTAL != 0) {
                    f.portal = true
                }
                f.w = w.Copy()
                f.planenum = s.planenum and 1.inv()
                f.next = flist
                flist = f
                i++
            }
            list = list.next
        }
        return flist
    }

    /*
     =================
     MakeVisibleBspFaceList
     =================
     */
    fun MakeVisibleBspFaceList(list: primitive_s?): bspface_s? {
        var list = list
        var b: uBrush_t
        var i: Int
        var s: side_s?
        var w: idWinding?
        var f: bspface_s?
        var flist: bspface_s?
        flist = null
        while (list != null) {
            b = list.brush as uBrush_t
            if (TempDump.NOT(b)) {
                list = list.next
                continue
            }
            if (!b.opaque && 0 == b.contents and Material.CONTENTS_AREAPORTAL) {
                list = list.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                s = b.sides[i]
                w = s.visibleHull
                if (TempDump.NOT(w)) {
                    i++
                    continue
                }
                f = facebsp.AllocBspFace()
                if (s.material.GetContentFlags() and Material.CONTENTS_AREAPORTAL != 0) {
                    f.portal = true
                }
                f.w = w.Copy()
                f.planenum = s.planenum and 1.inv()
                f.next = flist
                flist = f
                i++
            }
            list = list.next
        }
        return flist
    }
}