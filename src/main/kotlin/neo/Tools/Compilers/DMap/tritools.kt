package neo.Tools.Compilers.DMap

import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.Tools.Compilers.DMap.tritjunction.hashVert_s
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object tritools {
    /*

     All triangle list functions should behave reasonably with NULL lists.

     */
    /*
     ===============
     AllocTri
     ===============
     */
    fun AllocTri(): mapTri_s? {
        val tri: mapTri_s
        tri = mapTri_s() // Mem_Alloc(sizeof(tri));
        //	memset( tri, 0, sizeof( *tri ) );
        return tri
    }

    /*
     ===============
     FreeTri
     ===============
     */
    fun FreeTri(tri: mapTri_s?) {
        tri.clear() //Mem_Free(tri);
    }

    /*
     ===============
     MergeTriLists

     This does not copy any tris, it just relinks them
     ===============
     */
    fun MergeTriLists(a: mapTri_s?, b: mapTri_s?): mapTri_s? {
        var prev: mapTri_s?
        prev = a
        while (prev != null && prev.next != null) {
            prev = prev.next
        }
        prev.next = b
        return a
    }

    /*
     ===============
     FreeTriList
     ===============
     */
    fun FreeTriList(a: mapTri_s?) {
        var a = a
        var next: mapTri_s?
        while (a != null) {
            next = a.next
            a.clear() //Mem_Free(a);
            a = next
        }
    }

    /*
     ===============
     CopyTriList
     ===============
     */
    fun CopyTriList(a: mapTri_s?): mapTri_s? {
        var testList: mapTri_s?
        var tri: mapTri_s?
        testList = null
        tri = a
        while (tri != null) {
            var copy: mapTri_s?
            copy = tritools.CopyMapTri(tri)
            copy.next = testList
            testList = copy
            tri = tri.next
        }
        return testList
    }

    /*
     =============
     CountTriList
     =============
     */
    fun CountTriList( /*final*/
        tri: mapTri_s?
    ): Int {
        var tri = tri
        var c: Int
        c = 0
        while (tri != null) {
            c++
            tri = tri.next
        }
        return c
    }

    /*
     ===============
     CopyMapTri
     ===============
     */
    fun CopyMapTri(tri: mapTri_s?): mapTri_s? {
        val t: mapTri_s?

//        t = (mapTri_s) Mem_Alloc(sizeof(t));
        t = tri
        return t
    }

    /*
     ===============
     MapTriArea
     ===============
     */
    fun MapTriArea(tri: mapTri_s?): Float {
        return idWinding.Companion.TriangleArea(tri.v[0].xyz, tri.v[1].xyz, tri.v[2].xyz)
    }

    /*
     ===============
     RemoveBadTris

     Return a new list with any zero or negative area triangles removed
     ===============
     */
    fun RemoveBadTris(list: mapTri_s?): mapTri_s? {
        var newList: mapTri_s?
        var copy: mapTri_s?
        var tri: mapTri_s?
        newList = null
        tri = list
        while (tri != null) {
            if (tritools.MapTriArea(tri) > 0) {
                copy = tritools.CopyMapTri(tri)
                copy.next = newList
                newList = copy
            }
            tri = tri.next
        }
        return newList
    }

    /*
     ================
     BoundTriList
     ================
     */
    fun BoundTriList( /*final*/
        list: mapTri_s?, b: idBounds?
    ) {
        var list = list
        b.Clear()
        while (list != null) {
            b.AddPoint(list.v[0].xyz)
            b.AddPoint(list.v[1].xyz)
            b.AddPoint(list.v[2].xyz)
            list = list.next
        }
    }

    /*
     ================
     DrawTri
     ================
     */
    fun DrawTri(tri: mapTri_s?) {
        val w = idWinding()
        w.SetNumPoints(3)
        Vector.VectorCopy(tri.v[0].xyz, w.get(0))
        Vector.VectorCopy(tri.v[1].xyz, w.get(1))
        Vector.VectorCopy(tri.v[2].xyz, w.get(2))
        gldraw.DrawWinding(w)
    }

    /*
     ================
     FlipTriList

     Swaps the vertex order
     ================
     */
    fun FlipTriList(tris: mapTri_s?) {
        var tri: mapTri_s?
        tri = tris
        while (tri != null) {
            var v: idDrawVert?
            var hv: hashVert_s?
            var ov: optVertex_s?
            v = tri.v[0]
            tri.v[0] = tri.v[2]
            tri.v[2] = v
            hv = tri.hashVert[0]
            tri.hashVert[0] = tri.hashVert[2]
            tri.hashVert[2] = hv
            ov = tri.optVert[0]
            tri.optVert[0] = tri.optVert[2]
            tri.optVert[2] = ov
            tri = tri.next
        }
    }

    /*
     ================
     WindingForTri
     ================
     */
    fun WindingForTri(tri: mapTri_s?): idWinding? {
        val w: idWinding
        w = idWinding(3)
        w.SetNumPoints(3)
        Vector.VectorCopy(tri.v[0].xyz, w.get(0))
        Vector.VectorCopy(tri.v[1].xyz, w.get(1))
        Vector.VectorCopy(tri.v[2].xyz, w.get(2))
        return w
    }

    /*
     ================
     TriVertsFromOriginal

     Regenerate the texcoords and colors on a fragmented tri from the plane equations
     ================
     */
    fun TriVertsFromOriginal(tri: mapTri_s?, original: mapTri_s?) {
        var i: Int
        var j: Int
        val denom: Float
        denom = idWinding.Companion.TriangleArea(original.v[0].xyz, original.v[1].xyz, original.v[2].xyz)
        if (denom == 0f) {
            return  // original was degenerate, so it doesn't matter
        }
        i = 0
        while (i < 3) {
            var a: Float
            var b: Float
            var c: Float

            // find the barycentric coordinates
            a = idWinding.Companion.TriangleArea(tri.v[i].xyz, original.v[1].xyz, original.v[2].xyz) / denom
            b = idWinding.Companion.TriangleArea(tri.v[i].xyz, original.v[2].xyz, original.v[0].xyz) / denom
            c = idWinding.Companion.TriangleArea(tri.v[i].xyz, original.v[0].xyz, original.v[1].xyz) / denom

            // regenerate the interpolated values
            tri.v[i].st.set(
                0,
                a * original.v[0].st.get(0) + b * original.v[1].st.get(0) + c * original.v[2].st.get(0)
            )
            tri.v[i].st.set(
                1,
                a * original.v[0].st.get(1) + b * original.v[1].st.get(1) + c * original.v[2].st.get(1)
            )
            j = 0
            while (j < 3) {
                tri.v[i].normal.set(
                    j,
                    a * original.v[0].normal.get(j) + b * original.v[1].normal.get(j) + c * original.v[2].normal.get(
                        j
                    )
                )
                j++
            }
            tri.v[i].normal.Normalize()
            i++
        }
    }

    /*
     ================
     WindingToTriList

     Generates a new list of triangles with proper texcoords from a winding
     created by clipping the originalTri

     OriginalTri can be NULL if you don't care about texCoords
     ================
     */
    fun WindingToTriList(w: idWinding?, originalTri: mapTri_s?): mapTri_s? {
        var tri: mapTri_s?
        var triList: mapTri_s?
        var i: Int
        var j: Int
        val vec = idVec3()
        if (TempDump.NOT(w)) {
            return null
        }
        triList = null
        i = 2
        while (i < w.GetNumPoints()) {
            tri = tritools.AllocTri()
            tri = if (TempDump.NOT(originalTri)) {
//			memset( tri, 0, sizeof( *tri ) );
                mapTri_s()
            } else {
                originalTri
            }
            tri.next = triList //TODO:what happens here?
            triList = tri
            j = 0
            while (j < 3) {
                if (j == 0) {
                    vec.set(w.get(0).ToVec3())
                } else if (j == 1) {
                    vec.set(w.get(i - 1).ToVec3())
                } else {
                    vec.set(w.get(i).ToVec3())
                }
                Vector.VectorCopy(vec, tri.v[j].xyz)
                j++
            }
            if (originalTri != null) {
                tritools.TriVertsFromOriginal(tri, originalTri)
            }
            i++
        }
        return triList
    }

    /*
     ==================
     ClipTriList
     ==================
     */
    fun ClipTriList(
        list: mapTri_s?, plane: idPlane?, epsilon: Float,
        front: mapTri_s?, back: mapTri_s?
    ) {
        var front = front
        var back = back
        var tri: mapTri_s?
        var newList: mapTri_s?
        var w: idWinding?
        val frontW = idWinding()
        val backW = idWinding()

//        front[0] = null;
//        back[0] = null;
        tri = list
        while (tri != null) {
            w = tritools.WindingForTri(tri)
            w.Split(plane, epsilon, frontW, backW)
            newList = tritools.WindingToTriList(frontW, tri)
            front = tritools.MergeTriLists(front, newList)
            newList = tritools.WindingToTriList(backW, tri)
            back = tritools.MergeTriLists(back, newList)
            tri = tri.next
        }
    }

    /*
     ==================
     PlaneForTri
     ==================
     */
    fun PlaneForTri(tri: mapTri_s?, plane: idPlane?) {
        plane.FromPoints(tri.v[0].xyz, tri.v[1].xyz, tri.v[2].xyz)
    }
}