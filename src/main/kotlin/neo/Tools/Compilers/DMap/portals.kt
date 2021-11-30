package neo.Tools.Compilers.DMap

import neo.Renderer.Material
import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.MapFile.idMapEntity
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object portals {
    //
    const val BASE_WINDING_EPSILON = 0.001f
    const val MAX_INTER_AREA_PORTALS = 1024

    //
    val interAreaPortals: Array<interAreaPortal_t?>? = arrayOfNulls<interAreaPortal_t?>(portals.MAX_INTER_AREA_PORTALS)

    //
    const val SIDESPACE = 8
    const val SPLIT_WINDING_EPSILON = 0.001f
    var numInterAreaPortals = 0

    //
    var c_active_portals = 0
    var c_areaFloods = 0

    //
    var c_areas = 0

    //
    var c_floodedleafs = 0
    var c_inside = 0

    //
    var c_outside = 0
    var c_peak_portals = 0
    var c_solid = 0

    //
    var c_tinyportals = 0

    /*
     ===========
     AllocPortal
     ===========
     */
    fun AllocPortal(): uPortal_s? {
        val p: uPortal_s
        portals.c_active_portals++
        if (portals.c_active_portals > portals.c_peak_portals) {
            portals.c_peak_portals = portals.c_active_portals
        }
        p = uPortal_s() // Mem_Alloc(sizeof(uPortal_s));
        //	memset (p, 0, sizeof(uPortal_s ));
        return p
    }

    fun FreePortal(p: uPortal_s?) {
        if (p.winding != null) //		delete p.winding;
        {
            p.winding = null
        }
        portals.c_active_portals--
        p.clear() //Mem_Free(p);
    }

    /*
     =============
     Portal_Passable

     Returns true if the portal has non-opaque leafs on both sides
     =============
     */
    fun Portal_Passable(p: uPortal_s?): Boolean {
        if (TempDump.NOT(p.onnode)) {
            return false // to global outsideleaf
        }
        if (p.nodes[0].planenum != dmap.PLANENUM_LEAF
            || p.nodes[1].planenum != dmap.PLANENUM_LEAF
        ) {
            Common.common.Error("Portal_EntityFlood: not a leaf")
        }
        return !p.nodes[0].opaque && !p.nodes[1].opaque
    }

    //==============================================================
    //=============================================================================
    /*
     =============
     AddPortalToNodes
     =============
     */
    fun AddPortalToNodes(p: uPortal_s?, front: node_s?, back: node_s?) {
        if (p.nodes[0] != null || p.nodes[1] != null) {
            Common.common.Error("AddPortalToNode: allready included")
        }
        p.nodes[0] = front
        p.next[0] = front.portals
        front.portals = p
        p.nodes[1] = back
        p.next[1] = back.portals
        back.portals = p
    }

    /*
     =============
     RemovePortalFromNode
     =============
     */
    fun RemovePortalFromNode(portal: uPortal_s?, l: node_s?) {
        var pp: uPortal_s?
        var t: uPortal_s?

// remove reference to the current portal
        pp = l.portals
        while (true) {
            t = pp
            if (TempDump.NOT(t)) {
                Common.common.Error("RemovePortalFromNode: portal not in leaf")
            }
            if (t == portal) {
                break
            }
            if (t.nodes[0] == l) {
                pp = t.next[0]
                l.portals = pp //TODO:check this pointer to a pointer assignment.
            } else if (t.nodes[1] === l) {
                pp = t.next[1]
                l.portals = pp
            } else {
                Common.common.Error("RemovePortalFromNode: portal not bounding leaf")
            }
        }
        if (portal.nodes[0] === l) {
            pp = portal.next[0]
            l.portals = pp
            portal.nodes[0] = null
        } else if (portal.nodes[1] === l) {
            pp = portal.next[1]
            l.portals = pp
            portal.nodes[1] = null
        } else {
            Common.common.Error("RemovePortalFromNode: mislinked")
        }
    }

    //============================================================================
    fun PrintPortal(p: uPortal_s?) {
        var i: Int
        val w: idWinding?
        w = p.winding
        i = 0
        while (i < w.GetNumPoints()) {
            Common.common.Printf("(%5.0f,%5.0f,%5.0f)\n", w.oGet(i, 0), w.oGet(i, 1), w.oGet(i, 2))
            i++
        }
    }

    /*
     ================
     MakeHeadnodePortals

     The created portals will face the global outside_node
     ================
     */
    fun MakeHeadnodePortals(tree: tree_s?) {
        val bounds = idBounds()
        var i: Int
        var j: Int
        var n: Int
        var p: uPortal_s?
        val portals = arrayOfNulls<uPortal_s?>(6)
        val bplanes: Array<idPlane?> = idPlane.Companion.generateArray(6)
        val node: node_s?
        node = tree.headnode
        tree.outside_node.planenum = dmap.PLANENUM_LEAF
        tree.outside_node.brushlist = null
        tree.outside_node.portals = null
        tree.outside_node.opaque = false

        // if no nodes, don't go any farther
        if (node.planenum == dmap.PLANENUM_LEAF) {
            return
        }

        // pad with some space so there will never be null volume leafs
        i = 0
        while (i < 3) {
            bounds.set(0, i, tree.bounds.get(0, i) - portals.SIDESPACE)
            bounds.set(1, i, tree.bounds.get(1, i) - portals.SIDESPACE)
            if (bounds.get(0, i) >= bounds.get(1, i)) {
                Common.common.Error("Backwards tree volume")
            }
            i++
        }
        i = 0
        while (i < 3) {
            j = 0
            while (j < 2) {
                n = j * 3 + i
                p = portals.AllocPortal()
                portals[n] = p
                val pl = bplanes[n]
                //			memset (pl, 0, sizeof(*pl));
                if (j != 0) {
                    pl.set(i, -1f)
                    pl.set(3, bounds.get(j, i))
                } else {
                    pl.set(i, 1f)
                    pl.set(3, -bounds.get(j, i))
                }
                p.plane.set(pl)
                p.winding = idWinding(pl)
                portals.AddPortalToNodes(p, node, tree.outside_node)
                j++
            }
            i++
        }

        // clip the basewindings by all the other planes
        i = 0
        while (i < 6) {
            j = 0
            while (j < 6) {
                if (j == i) {
                    j++
                    continue
                }
                portals[i].winding = portals[i].winding.Clip(bplanes[j], Plane.ON_EPSILON)
                j++
            }
            i++
        }
    }

    //===================================================
    /*
     ================
     BaseWindingForNode
     ================
     */
    fun BaseWindingForNode(node: node_s?): idWinding? {
        var node = node
        var w: idWinding?
        var n: node_s?
        w = idWinding(dmap.dmapGlobals.mapPlanes.get(node.planenum))

        // clip by all the parents
        n = node.parent
        while (n != null && w != null) {
            val plane = dmap.dmapGlobals.mapPlanes.get(n.planenum)
            w = if (n.children[0] == node) {
                // take front
                w.Clip(plane, portals.BASE_WINDING_EPSILON)
            } else {
                // take back
                val back = idPlane(plane.unaryMinus())
                w.Clip(back, portals.BASE_WINDING_EPSILON)
            }
            node = n
            n = n.parent
        }
        return w
    }

    /*
     ==================
     MakeNodePortal

     create the new portal by taking the full plane winding for the cutting plane
     and clipping it by all of parents of this node
     ==================
     */
    fun MakeNodePortal(node: node_s?) {
        val new_portal: uPortal_s?
        var p: uPortal_s?
        var w: idWinding?
        var side: Int
        w = portals.BaseWindingForNode(node)

        // clip the portal by all the other portals in the node
        p = node.portals
        while (p != null && w != null) {
            val plane = idPlane()
            if (p.nodes[0] == node) {
                side = 0
                plane.set(p.plane)
            } else if (p.nodes[1] === node) {
                side = 1
                plane.set(p.plane.unaryMinus())
            } else {
                Common.common.Error("CutNodePortals_r: mislinked portal")
                side = 0 // quiet a compiler warning
            }
            w = w.Clip(plane, ubrush.CLIP_EPSILON)
            p = p.next[side]
        }
        if (TempDump.NOT(w)) {
            return
        }
        if (w.IsTiny()) {
            portals.c_tinyportals++
            //		delete w;
            return
        }
        new_portal = portals.AllocPortal()
        new_portal.plane.set(dmap.dmapGlobals.mapPlanes.get(node.planenum))
        new_portal.onnode = node
        new_portal.winding = w
        portals.AddPortalToNodes(new_portal, node.children[0], node.children[1])
    }

    //============================================================
    /*
     ==============
     SplitNodePortals

     Move or split the portals that bound node so that the node's
     children have portals instead of node.
     ==============
     */
    fun SplitNodePortals(node: node_s?) {
        var p: uPortal_s?
        var next_portal: uPortal_s?
        var new_portal: uPortal_s?
        val f: node_s?
        val b: node_s?
        var other_node: node_s?
        var side: Int
        var frontwinding: idWinding? = idWinding()
        var backwinding: idWinding? = idWinding()
        val plane = dmap.dmapGlobals.mapPlanes.get(node.planenum)
        f = node.children[0]
        b = node.children[1]
        p = node.portals
        while (p != null) {
            side = if (p.nodes[0] === node) {
                0
            } else if (p.nodes[1] === node) {
                1
            } else {
                Common.common.Error("SplitNodePortals: mislinked portal")
                0 // quiet a compiler warning
            }
            next_portal = p.next[side]
            other_node = p.nodes[1 xor side]
            portals.RemovePortalFromNode(p, p.nodes[0])
            portals.RemovePortalFromNode(p, p.nodes[1])

            //
            // cut the portal into two portals, one on each side of the cut plane
            //
            p.winding.Split(plane, portals.SPLIT_WINDING_EPSILON, frontwinding, backwinding)
            if (frontwinding != null && frontwinding.IsTiny()) {
//			delete frontwinding;
                frontwinding = null
                portals.c_tinyportals++
            }
            if (backwinding != null && backwinding.IsTiny()) {
//			delete backwinding;
                backwinding = null
                portals.c_tinyportals++
            }
            if (TempDump.NOT(frontwinding) && TempDump.NOT(backwinding)) {    // tiny windings on both sides
                p = next_portal
                continue
            }
            if (TempDump.NOT(frontwinding)) {
//			delete backwinding;
                if (side == 0) {
                    portals.AddPortalToNodes(p, b, other_node)
                } else {
                    portals.AddPortalToNodes(p, other_node, b)
                }
                p = next_portal
                continue
            }
            if (TempDump.NOT(backwinding)) {
//			delete frontwinding;
                if (side == 0) {
                    portals.AddPortalToNodes(p, f, other_node)
                } else {
                    portals.AddPortalToNodes(p, other_node, f)
                }
                p = next_portal
                continue
            }

            // the winding is split
            new_portal = portals.AllocPortal()
            new_portal = p
            new_portal.winding = backwinding
            //		delete p.winding;
            p.winding = frontwinding
            if (side == 0) {
                portals.AddPortalToNodes(p, f, other_node)
                portals.AddPortalToNodes(new_portal, b, other_node)
            } else {
                portals.AddPortalToNodes(p, other_node, f)
                portals.AddPortalToNodes(new_portal, other_node, b)
            }
            p = next_portal
        }
        node.portals = null
    }

    /*
     ================
     CalcNodeBounds
     ================
     */
    fun CalcNodeBounds(node: node_s?) {
        var p: uPortal_s?
        var s: Int
        var i: Int

        // calc mins/maxs for both leafs and nodes
        node.bounds.Clear()
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0
            i = 0
            while (i < p.winding.GetNumPoints()) {
                node.bounds.AddPoint(p.winding.oGet(i).ToVec3())
                i++
            }
            p = p.next[s]
        }
    }

    /*
     ==================
     MakeTreePortals_r
     ==================
     */
    fun MakeTreePortals_r(node: node_s?) {
        var i: Int
        portals.CalcNodeBounds(node)
        if (node.bounds.get(0, 0) >= node.bounds.get(1, 0)) {
            Common.common.Warning("node without a volume")
        }
        i = 0
        while (i < 3) {
            if (node.bounds.get(0, i) < Lib.Companion.MIN_WORLD_COORD || node.bounds.get(
                    1,
                    i
                ) > Lib.Companion.MAX_WORLD_COORD
            ) {
                Common.common.Warning("node with unbounded volume")
                break
            }
            i++
        }
        if (node.planenum == dmap.PLANENUM_LEAF) {
            return
        }
        portals.MakeNodePortal(node)
        portals.SplitNodePortals(node)
        portals.MakeTreePortals_r(node.children[0])
        portals.MakeTreePortals_r(node.children[1])
    }

    /*
     ==================
     MakeTreePortals
     ==================
     */
    fun MakeTreePortals(tree: tree_s?) {
        Common.common.Printf("----- MakeTreePortals -----\n")
        portals.MakeHeadnodePortals(tree)
        portals.MakeTreePortals_r(tree.headnode)
    }

    /*
     =========================================================

     FLOOD ENTITIES

     =========================================================
     */
    /*
     =============
     FloodPortals_r
     =============
     */
    fun FloodPortals_r(node: node_s?, dist: Int) {
        var p: uPortal_s?
        var s: Int
        if (node.occupied != 0) {
            return
        }
        if (node.opaque) {
            return
        }
        portals.c_floodedleafs++
        node.occupied = dist
        p = node.portals
        while (p != null) {
            s = if (p.nodes[1] == node) 1 else 0
            portals.FloodPortals_r(p.nodes[1 xor s], dist + 1)
            p = p.next[s]
        }
    }

    /*
     =============
     PlaceOccupant
     =============
     */
    fun PlaceOccupant(headnode: node_s?, origin: idVec3?, occupant: uEntity_t?): Boolean {
        var node: node_s?
        var d: Float


        // find the leaf to start in
        node = headnode
        while (node.planenum != dmap.PLANENUM_LEAF) {
            val plane = dmap.dmapGlobals.mapPlanes.get(node.planenum)
            d = plane.Distance(origin)
            node = if (d >= 0.0f) {
                node.children[0]
            } else {
                node.children[1]
            }
        }
        if (node.opaque) {
            return false
        }
        node.occupant = occupant
        portals.FloodPortals_r(node, 1)
        return true
    }

    /*
     =============
     FloodEntities

     Marks all nodes that can be reached by entites
     =============
     */
    fun FloodEntities(tree: tree_s?): Boolean {
        var i: Int
        val origin = idVec3()
        val cl = arrayOf<String?>(null)
        var inside: Boolean
        val headnode: node_s?
        headnode = tree.headnode
        Common.common.Printf("--- FloodEntities ---\n")
        inside = false
        tree.outside_node.occupied = 0
        portals.c_floodedleafs = 0
        var errorShown = false
        i = 1
        while (i < dmap.dmapGlobals.num_entities) {
            var mapEnt: idMapEntity?
            mapEnt = dmap.dmapGlobals.uEntities[i].mapEntity
            if (!mapEnt.epairs.GetVector("origin", "", origin)) {
                i++
                continue
            }

            // any entity can have "noFlood" set to skip it
            if (mapEnt.epairs.GetString("noFlood", "", cl)) {
                i++
                continue
            }
            mapEnt.epairs.GetString("classname", "", cl)
            if (cl[0] == "light") {
                val v = arrayOf<String?>(null)

                // don't place lights that have a light_start field, because they can still
                // be valid if their origin is outside the world
                mapEnt.epairs.GetString("light_start", "", v)
                if (TempDump.isNotNullOrEmpty(v[0])) {
                    i++
                    continue
                }

                // don't place fog lights, because they often
                // have origins outside the light
                mapEnt.epairs.GetString("texture", "", v)
                if (TempDump.isNotNullOrEmpty(v[0])) {
                    val mat: idMaterial? = DeclManager.declManager.FindMaterial(v[0])
                    if (mat.IsFogLight()) {
                        i++
                        continue
                    }
                }
            }
            if (portals.PlaceOccupant(headnode, origin, dmap.dmapGlobals.uEntities[i])) {
                inside = true
            }
            if (tree.outside_node.occupied != 0 && !errorShown) {
                errorShown = true
                Common.common.Printf("Leak on entity # %d\n", i)
                val p = arrayOf<String?>(null)
                mapEnt.epairs.GetString("classname", "", p)
                Common.common.Printf("Entity classname was: %s\n", p[0])
                mapEnt.epairs.GetString("name", "", p)
                Common.common.Printf("Entity name was: %s\n", p[0])
                val origin2 = idVec3()
                if (mapEnt.epairs.GetVector("origin", "", origin2)) {
                    Common.common.Printf("Entity origin is: %f %f %f\n\n\n", origin2.x, origin2.y, origin2.z)
                }
            }
            i++
        }
        Common.common.Printf("%5d flooded leafs\n", portals.c_floodedleafs)
        if (!inside) {
            Common.common.Printf("no entities in open -- no filling\n")
        } else if (tree.outside_node.occupied != 0) {
            Common.common.Printf("entity reached from outside -- no filling\n")
        }
        return inside && 0 == tree.outside_node.occupied
    }

    /*
     =========================================================

     FLOOD AREAS

     =========================================================
     */
    /*
     =================
     FindSideForPortal
     =================
     */
    fun FindSideForPortal(p: uPortal_s?): side_s? {
        var i: Int
        var j: Int
        var k: Int
        var node: node_s?
        var b: uBrush_t?
        var orig: uBrush_t
        var s: side_s?
        var s2: side_s?

        // scan both bordering nodes brush lists for a portal brush
        // that shares the plane
        i = 0
        while (i < 2) {
            node = p.nodes[i]
            b = node.brushlist
            while (b != null) {
                if (0 == b.contents and Material.CONTENTS_AREAPORTAL) {
                    b = b.next as uBrush_t
                    continue
                }
                orig = b.original as uBrush_t
                j = 0
                while (j < orig.numsides) {
                    s = orig.sides[j]
                    if (TempDump.NOT(s.visibleHull)) {
                        j++
                        continue
                    }
                    if (0 == s.material.GetContentFlags() and Material.CONTENTS_AREAPORTAL) {
                        j++
                        continue
                    }
                    if (s.planenum and 1.inv() != p.onnode.planenum and 1.inv()) {
                        j++
                        continue
                    }
                    // remove the visible hull from any other portal sides of this portal brush
                    k = 0
                    while (k < orig.numsides) {
                        if (k == j) {
                            k++
                            continue
                        }
                        s2 = orig.sides[k]
                        if (null == s2.visibleHull) {
                            k++
                            continue
                        }
                        if (0 == s2.material.GetContentFlags() and Material.CONTENTS_AREAPORTAL) {
                            k++
                            continue
                        }
                        Common.common.Warning(
                            "brush has multiple area portal sides at %s",
                            s2.visibleHull.GetCenter().ToString()
                        )
                        //					delete s2.visibleHull;
                        s2.visibleHull = null
                        k++
                    }
                    return s
                    j++
                }
                b = b.next as uBrush_t
            }
            i++
        }
        return null
    }

    /*
     =============
     FloodAreas_r
     =============
     */
    fun FloodAreas_r(node: node_s?) {
        var p: uPortal_s?
        var s: Int
        if (node.area != -1) {
            return  // allready got it
        }
        if (node.opaque) {
            return
        }
        portals.c_areaFloods++
        node.area = portals.c_areas
        p = node.portals
        while (p != null) {
            var other: node_s?
            s = if (p.nodes[1] == node) 1 else 0
            other = p.nodes[1 xor s]
            if (!portals.Portal_Passable(p)) {
                p = p.next[s]
                continue
            }

            // can't flood through an area portal
            if (portals.FindSideForPortal(p) != null) {
                p = p.next[s]
                continue
            }
            portals.FloodAreas_r(other)
            p = p.next[s]
        }
    }

    /*
     =============
     FindAreas_r

     Just decend the tree, and for each node that hasn't had an
     area set, flood fill out from there
     =============
     */
    fun FindAreas_r(node: node_s?) {
        if (node.planenum != dmap.PLANENUM_LEAF) {
            portals.FindAreas_r(node.children[0])
            portals.FindAreas_r(node.children[1])
            return
        }
        if (node.opaque) {
            return
        }
        if (node.area != -1) {
            return  // allready got it
        }
        portals.c_areaFloods = 0
        portals.FloodAreas_r(node)
        Common.common.Printf("area %d has %d leafs\n", portals.c_areas, portals.c_areaFloods)
        portals.c_areas++
    }

    /*
     ============
     CheckAreas_r
     ============
     */
    fun CheckAreas_r(node: node_s?) {
        if (node.planenum != dmap.PLANENUM_LEAF) {
            portals.CheckAreas_r(node.children[0])
            portals.CheckAreas_r(node.children[1])
            return
        }
        if (!node.opaque && node.area < 0) {
            Common.common.Error("CheckAreas_r: area = %d", node.area)
        }
    }

    /*
     ============
     ClearAreas_r

     Set all the areas to -1 before filling
     ============
     */
    fun ClearAreas_r(node: node_s?) {
        if (node.planenum != dmap.PLANENUM_LEAF) {
            portals.ClearAreas_r(node.children[0])
            portals.ClearAreas_r(node.children[1])
            return
        }
        node.area = -1
    }

    //=============================================================
    /*
     =================
     FindInterAreaPortals_r

     =================
     */
    fun FindInterAreaPortals_r(node: node_s?) {
        var p: uPortal_s?
        var s: Int
        var i: Int
        var w: idWinding
        var iap: interAreaPortal_t?
        var side: side_s?
        if (node.planenum != dmap.PLANENUM_LEAF) {
            portals.FindInterAreaPortals_r(node.children[0])
            portals.FindInterAreaPortals_r(node.children[1])
            return
        }
        if (node.opaque) {
            return
        }
        p = node.portals
        while (p != null) {
            var other: node_s?
            s = if (p.nodes[1] == node) 1 else 0
            other = p.nodes[1 xor s]
            if (other.opaque) {
                p = p.next[s]
                continue
            }

            // only report areas going from lower number to higher number
            // so we don't report the portal twice
            if (other.area <= node.area) {
                p = p.next[s]
                continue
            }
            side = portals.FindSideForPortal(p)
            //		w = p.winding;
            if (TempDump.NOT(side)) {
                Common.common.Warning("FindSideForPortal failed at %s", p.winding.GetCenter().ToString())
                p = p.next[s]
                continue
            }
            w = side.visibleHull
            if (TempDump.NOT(w)) {
                p = p.next[s]
                continue
            }

            // see if we have created this portal before
            i = 0
            while (i < portals.numInterAreaPortals) {
                iap = portals.interAreaPortals[i]
                if (side === iap.side
                    && (p.nodes[0].area == iap.area0 && p.nodes[1].area == iap.area1
                            || p.nodes[1].area == iap.area0 && p.nodes[0].area == iap.area1)
                ) {
                    break
                }
                i++
            }
            if (i != portals.numInterAreaPortals) {
                p = p.next[s]
                continue  // already emited
            }
            iap = portals.interAreaPortals[portals.numInterAreaPortals]
            portals.numInterAreaPortals++
            if (side.planenum == p.onnode.planenum) {
                iap.area0 = p.nodes[0].area
                iap.area1 = p.nodes[1].area
            } else {
                iap.area0 = p.nodes[1].area
                iap.area1 = p.nodes[0].area
            }
            iap.side = side
            p = p.next[s]
        }
    }

    /*
     =============
     FloodAreas

     Mark each leaf with an area, bounded by CONTENTS_AREAPORTAL
     Sets e.areas.numAreas
     =============
     */
    fun FloodAreas(e: uEntity_t?) {
        Common.common.Printf("--- FloodAreas ---\n")

        // set all areas to -1
        portals.ClearAreas_r(e.tree.headnode)

        // flood fill from non-opaque areas
        portals.c_areas = 0
        portals.FindAreas_r(e.tree.headnode)
        Common.common.Printf("%5d areas\n", portals.c_areas)
        e.numAreas = portals.c_areas

        // make sure we got all of them
        portals.CheckAreas_r(e.tree.headnode)

        // identify all portals between areas if this is the world
        if (e == dmap.dmapGlobals.uEntities[0]) {
            portals.numInterAreaPortals = 0
            portals.FindInterAreaPortals_r(e.tree.headnode)
        }
    }

    /*
     ======================================================

     FILL OUTSIDE

     ======================================================
     */
    fun FillOutside_r(node: node_s?) {
        if (node.planenum != dmap.PLANENUM_LEAF) {
            portals.FillOutside_r(node.children[0])
            portals.FillOutside_r(node.children[1])
            return
        }

        // anything not reachable by an entity
        // can be filled away
        if (TempDump.NOT(node.occupied.toDouble())) {
            if (!node.opaque) {
                portals.c_outside++
                node.opaque = true
            } else {
                portals.c_solid++
            }
        } else {
            portals.c_inside++
        }
    }

    /*
     =============
     FillOutside

     Fill (set node.opaque = true) all nodes that can't be reached by entities
     =============
     */
    fun FillOutside(e: uEntity_t?) {
        portals.c_outside = 0
        portals.c_inside = 0
        portals.c_solid = 0
        Common.common.Printf("--- FillOutside ---\n")
        portals.FillOutside_r(e.tree.headnode)
        Common.common.Printf("%5d solid leafs\n", portals.c_solid)
        Common.common.Printf("%5d leafs filled\n", portals.c_outside)
        Common.common.Printf("%5d inside leafs\n", portals.c_inside)
    }

    internal class interAreaPortal_t {
        var area0 = 0
        var area1 = 0
        var side: side_s? = null
    }
}