package neo.Renderer

import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewDef_s
import neo.framework.Common
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4

/**
 *
 */
object tr_shadowbounds {
    private val lut: Array<polyhedron?>? = arrayOfNulls<polyhedron?>(64)
    private val p: polyhedron? = null

    //int MyArrayInt::max_size = 0;
    fun four_ints(a: Int, b: Int, c: Int, d: Int): MyArrayInt? {
        val vi = MyArrayInt()
        vi.push_back(a)
        vi.push_back(b)
        vi.push_back(c)
        vi.push_back(d)
        return vi
    }

    //int MyArrayVec4::max_size = 0;
    fun homogeneous_difference(a: idVec4?, b: idVec4?): idVec3? {
        val v = idVec3()
        v.x = b.x * a.w - a.x * b.w
        v.y = b.y * a.w - a.y * b.w
        v.z = b.z * a.w - a.z * b.w
        return v
    }

    // handles positive w only
    fun compute_homogeneous_plane(a: idVec4?, b: idVec4?, c: idVec4?): idVec4? {
        var a = a
        var b = b
        var c = c
        val v = idVec4()
        var t: idVec4?
        if (a.oGet(3) == 0f) {
            t = a
            a = b
            b = c
            c = t
        }
        if (a.oGet(3) == 0f) {
            t = a
            a = b
            b = c
            c = t
        }

        // can't handle 3 infinite points
        if (a.oGet(3) == 0f) {
            return v
        }
        val vb = idVec3(tr_shadowbounds.homogeneous_difference(a, b))
        val vc = idVec3(tr_shadowbounds.homogeneous_difference(a, c))
        val n = idVec3(vb.Cross(vc))
        n.Normalize()
        v.x = n.x
        v.y = n.y
        v.z = n.z
        v.w = -n.oMultiply(idVec3(a.x, a.y, a.z)) / a.w
        return v
    }

    //int MyArrayPoly::max_size = 0;
    // make a unit cube
    fun PolyhedronFromBounds(b: idBounds?): polyhedron? {

//       3----------2
//       |\        /|
//       | \      / |
//       |   7--6   |
//       |   |  |   |
//       |   4--5   |
//       |  /    \  |
//       | /      \ |
//       0----------1
//
        if (tr_shadowbounds.p.e.size() == 0) {
            tr_shadowbounds.p.v.push_back(idVec4(-1, -1, 1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(1, -1, 1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(1, 1, 1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(-1, 1, 1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(-1, -1, -1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(1, -1, -1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(1, 1, -1, 1))
            tr_shadowbounds.p.v.push_back(idVec4(-1, 1, -1, 1))
            tr_shadowbounds.p.add_quad(0, 1, 2, 3)
            tr_shadowbounds.p.add_quad(7, 6, 5, 4)
            tr_shadowbounds.p.add_quad(1, 0, 4, 5)
            tr_shadowbounds.p.add_quad(2, 1, 5, 6)
            tr_shadowbounds.p.add_quad(3, 2, 6, 7)
            tr_shadowbounds.p.add_quad(0, 3, 7, 4)
            tr_shadowbounds.p.compute_neighbors()
            tr_shadowbounds.p.recompute_planes()
            tr_shadowbounds.p.v.empty() // no need to copy this data since it'll be replaced
        }
        val p2 = polyhedron(tr_shadowbounds.p)
        val min = idVec3(b.oGet(0))
        val max = idVec3(b.oGet(1))
        p2.v.empty()
        p2.v.push_back(idVec4(min.x, min.y, max.z, 1))
        p2.v.push_back(idVec4(max.x, min.y, max.z, 1))
        p2.v.push_back(idVec4(max.x, max.y, max.z, 1))
        p2.v.push_back(idVec4(min.x, max.y, max.z, 1))
        p2.v.push_back(idVec4(min.x, min.y, min.z, 1))
        p2.v.push_back(idVec4(max.x, min.y, min.z, 1))
        p2.v.push_back(idVec4(max.x, max.y, min.z, 1))
        p2.v.push_back(idVec4(min.x, max.y, min.z, 1))
        p2.recompute_planes()
        return p2
    }

    fun make_sv(oc: polyhedron?, light: idVec4?): polyhedron? {
        var index = 0
        for (i in 0..5) {
            if (oc.p.oGet(i).plane.oMultiply(light) > 0) {
                index = index or (1 shl i)
            }
        }
        if (tr_shadowbounds.lut[index].e.size() == 0) {
            tr_shadowbounds.lut[index] = oc
            val ph: polyhedron? = tr_shadowbounds.lut[index]
            val V = ph.v.size()
            for (j in 0 until V) {
                val proj = idVec3(tr_shadowbounds.homogeneous_difference(light, ph.v.oGet(j)))
                ph.v.push_back(idVec4(proj.x, proj.y, proj.z, 0))
            }
            ph.p.empty()
            for (i in 0 until oc.p.size()) {
                if (oc.p.oGet(i).plane.oMultiply(light) > 0) {
                    ph.p.push_back(oc.p.oGet(i))
                }
            }
            if (ph.p.size() == 0) {
                return polyhedron().also { tr_shadowbounds.lut[index] = it }
            }
            ph.compute_neighbors()
            val vpg = MyArrayPoly()
            val I = ph.p.size()
            for (i in 0 until I) {
                val vi = ph.p.oGet(i).vi
                val ni = ph.p.oGet(i).ni
                val S = vi.size()
                for (j in 0 until S) {
                    if (ni.oGet(j) == -1) {
                        val pg = poly()
                        val a: Int = vi.oGet((j + 1) % S)
                        val b: Int = vi.oGet(j)
                        pg.vi = tr_shadowbounds.four_ints(a, b, b + V, a + V)
                        pg.ni = tr_shadowbounds.four_ints(-1, -1, -1, -1)
                        vpg.push_back(pg)
                    }
                }
            }
            for (i in 0 until vpg.size()) {
                ph.p.push_back(vpg.oGet(i))
            }
            ph.compute_neighbors()
            ph.v.empty() // no need to copy this data since it'll be replaced
        }
        val ph2: polyhedron? = tr_shadowbounds.lut[index]

        // initalize vertices
        ph2.v = oc.v
        val V = ph2.v.size()
        for (j in 0 until V) {
            val proj = idVec3(tr_shadowbounds.homogeneous_difference(light, ph2.v.oGet(j)))
            ph2.v.push_back(idVec4(proj.x, proj.y, proj.z, 0))
        }

        // need to compute planes for the shadow volume (sv)
        ph2.recompute_planes()
        return ph2
    }

    //int MyArrayEdge::max_size = 0;
    fun polyhedron_edges(a: polyhedron?, e: MySegments?) {
        e.empty()
        if (a.e.size() == 0 && a.p.size() != 0) {
            a.compute_neighbors()
        }
        for (i in 0 until a.e.size()) {
            e.push_back(a.v.oGet(a.e.oGet(i).vi.get(0)))
            e.push_back(a.v.oGet(a.e.oGet(i).vi.get(1)))
        }
    }

    // clip the segments of e by the planes of polyhedron a.
    fun clip_segments(ph: polyhedron?, `is`: MySegments?, os: MySegments?) {
        val p = ph.p
        var i = 0
        while (i < `is`.size()) {
            var a = `is`.oGet(i)
            var b = `is`.oGet(i + 1)
            var c: idVec4
            var discard = false
            for (j in 0 until p.size()) {
                val da = a.oMultiply(p.oGet(j).plane)
                val db = b.oMultiply(p.oGet(j).plane)
                val rdw = 1 / (da - db)
                var code = 0
                if (da > 0) {
                    code = 2
                }
                if (db > 0) {
                    code = code or 1
                }
                when (code) {
                    3 -> discard = true
                    2 -> {
                        c = a.oMultiply(db * rdw).oPlus(b.oMultiply(da * rdw)).oNegative()
                        a = c
                    }
                    1 -> {
                        c = a.oMultiply(db * rdw).oPlus(b.oMultiply(da * rdw)).oNegative()
                        b = c
                    }
                    0 -> {}
                    else -> Common.common.Printf("bad clip code!\n")
                }
                if (discard) {
                    break
                }
            }
            if (!discard) {
                os.push_back(a)
                os.push_back(b)
            }
            i += 2
        }
    }

    fun make_idMat4(m: FloatArray?): idMat4? {
        return idMat4(
            m.get(0), m.get(4), m.get(8), m.get(12),
            m.get(1), m.get(5), m.get(9), m.get(13),
            m.get(2), m.get(6), m.get(10), m.get(14),
            m.get(3), m.get(7), m.get(11), m.get(15)
        )
    }

    fun v4to3(v: idVec4?): idVec3? {
        return idVec3(v.x / v.w, v.y / v.w, v.z / v.w)
    }

    fun draw_polyhedron(viewDef: viewDef_s?, p: polyhedron?, color: idVec4?) {
        for (i in 0 until p.e.size()) {
            viewDef.renderWorld.DebugLine(
                color,
                tr_shadowbounds.v4to3(p.v.oGet(p.e.oGet(i).vi.get(0))),
                tr_shadowbounds.v4to3(p.v.oGet(p.e.oGet(i).vi.get(1)))
            )
        }
    }

    fun draw_segments(viewDef: viewDef_s?, s: MySegments?, color: idVec4?) {
        var i = 0
        while (i < s.size()) {
            viewDef.renderWorld.DebugLine(color, tr_shadowbounds.v4to3(s.oGet(i)), tr_shadowbounds.v4to3(s.oGet(i + 1)))
            i += 2
        }
    }

    fun world_to_hclip(viewDef: viewDef_s?, global: idVec4?, clip: idVec4?) {
        var i: Int
        val view = idVec4()
        i = 0
        while (i < 4) {
            view.oSet(
                i,
                global.oGet(0) * viewDef.worldSpace.modelViewMatrix[i + 0 * 4] + global.oGet(1) * viewDef.worldSpace.modelViewMatrix[i + 1 * 4] + global.oGet(
                    2
                ) * viewDef.worldSpace.modelViewMatrix[i + 2 * 4] + global.oGet(3) * viewDef.worldSpace.modelViewMatrix[i + 3 * 4]
            )
            i++
        }
        i = 0
        while (i < 4) {
            clip.oSet(
                i,
                view.oGet(0) * viewDef.projectionMatrix[i + 0 * 4] + view.oGet(1) * viewDef.projectionMatrix[i + 1 * 4] + view.oGet(
                    2
                ) * viewDef.projectionMatrix[i + 2 * 4] + view.oGet(3) * viewDef.projectionMatrix[i + 3 * 4]
            )
            i++
        }
    }

    fun R_CalcIntersectionScissor(
        lightDef: idRenderLightLocal?,
        entityDef: idRenderEntityLocal?,
        viewDef: viewDef_s?
    ): idScreenRect? {
        val omodel = tr_shadowbounds.make_idMat4(entityDef.modelMatrix)
        val lmodel = tr_shadowbounds.make_idMat4(lightDef.modelMatrix)

        // compute light polyhedron
        val lvol: polyhedron? = tr_shadowbounds.PolyhedronFromBounds(lightDef.frustumTris.bounds)
        // transform it into world space
        //lvol.transform( lmodel );

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            tr_shadowbounds.draw_polyhedron(viewDef, lvol, Lib.Companion.colorRed)
        }

        // compute object polyhedron
        val vol: polyhedron? = tr_shadowbounds.PolyhedronFromBounds(entityDef.referenceBounds)

        //viewDef.renderWorld.DebugBounds( colorRed, lightDef.frustumTris.bounds );
        //viewDef.renderWorld.DebugBox( colorBlue, idBox( model.Bounds(), entityDef.parms.origin, entityDef.parms.axis ) );
        // transform it into world space
        vol.transform(omodel)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            tr_shadowbounds.draw_polyhedron(viewDef, vol, Lib.Companion.colorBlue)
        }

        // transform light position into world space
        val lightpos = idVec4(
            lightDef.globalLightOrigin.x,
            lightDef.globalLightOrigin.y,
            lightDef.globalLightOrigin.z,
            1.0f
        )

        // generate shadow volume "polyhedron"
        val sv: polyhedron? = tr_shadowbounds.make_sv(vol, lightpos)
        val in_segs = MySegments()
        val out_segs = MySegments()

        // get shadow volume edges
        tr_shadowbounds.polyhedron_edges(sv, in_segs)
        // clip them against light bounds planes
        tr_shadowbounds.clip_segments(lvol, in_segs, out_segs)

        // get light bounds edges
        tr_shadowbounds.polyhedron_edges(lvol, in_segs)
        // clip them by the shadow volume
        tr_shadowbounds.clip_segments(sv, in_segs, out_segs)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            tr_shadowbounds.draw_segments(viewDef, out_segs, Lib.Companion.colorGreen)
        }
        val outbounds = idBounds()
        outbounds.Clear()
        for (i in 0 until out_segs.size()) {
            val v = idVec4()
            tr_shadowbounds.world_to_hclip(viewDef, out_segs.oGet(i), v)
            if (v.w <= 0.0f) {
                return lightDef.viewLight.scissorRect
            }
            val rv = idVec3(v.x, v.y, v.z)
            rv.oDivSet(v.w)
            outbounds.AddPoint(rv)
        }

        // limit the bounds to avoid an inside out scissor rectangle due to floating point to short conversion
        if (outbounds.oGet(0).x < -1.0f) {
            outbounds.oGet(0).x = -1.0f
        }
        if (outbounds.oGet(1).x > 1.0f) {
            outbounds.oGet(1).x = 1.0f
        }
        if (outbounds.oGet(0).y < -1.0f) {
            outbounds.oGet(0).y = -1.0f
        }
        if (outbounds.oGet(1).y > 1.0f) {
            outbounds.oGet(1).y = 1.0f
        }
        val w2 = (viewDef.viewport.x2 - viewDef.viewport.x1 + 1) / 2.0f
        val x = viewDef.viewport.x1.toFloat()
        val h2 = (viewDef.viewport.y2 - viewDef.viewport.y1 + 1) / 2.0f
        val y = viewDef.viewport.y1.toFloat()
        val rect = idScreenRect()
        rect.x1 = (outbounds.oGet(0).x * w2 + w2 + x).toInt()
        rect.x2 = (outbounds.oGet(1).x * w2 + w2 + x).toInt()
        rect.y1 = (outbounds.oGet(0).y * h2 + h2 + y).toInt()
        rect.y2 = (outbounds.oGet(1).y * h2 + h2 + y).toInt()
        rect.Expand()
        rect.Intersect(lightDef.viewLight.scissorRect)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2 && !rect.IsEmpty()) {
            viewDef.renderWorld.DebugScreenRect(Lib.Companion.colorYellow, rect, viewDef)
        }
        return rect
    }

    // Compute conservative shadow bounds as the intersection
    // of the object's bounds' shadow volume and the light's bounds.
    //
    // --cass
    internal open class MyArray<T> {
        var s = 0
        var v // = (T[]) new Object[N];
                : Array<T?>?

        //
        private val N: Int

        private constructor() {
            N = -1
        }

        constructor(N: Int) //: s(0)
        {
            this.N = N
            v = arrayOfNulls<Any?>(N) as Array<T?>
        }

        constructor(N: Int, cpy: MyArray<T?>?) //: s(cpy.s)
        {
            this.N = N
            v = arrayOfNulls<Any?>(N) as Array<T?>
            for (i in 0 until s) {
                v.get(i) = cpy.v.get(i)
            }
        }

        fun push_back(i: T?) {
            v.get(s) = i
            s++
            //if(s > max_size)
            //	max_size = int(s);
        }

        fun oGet(index: Int): T? {
            return v.get(index)
        }

        fun oSet(index: Int, value: T?): T? {
            return value.also { v.get(index) = it }
        }

        //	const T & operator[](int i) const {
        //		return v[i];
        //	}
        fun size(): Int {
            return s
        }

        fun empty() {
            s = 0
        } //	static int max_size;
    }

    //int MySegments::max_size = 0;
    internal object MyArrayInt : MyArray<Int?>() {
        private const val N = 4
    }

    internal object MyArrayVec4 : MyArray<idVec4?>() {
        private const val N = 16
    }

    internal class poly {
        var ni: MyArrayInt? = null
        var plane: idVec4? = null
        var vi: MyArrayInt? = null
    }

    internal object MyArrayPoly : MyArray<poly?>() {
        private const val N = 9
    }

    internal class edge {
        var pi: IntArray? = IntArray(2)
        var vi: IntArray? = IntArray(2)
    }

    internal object MyArrayEdge : MyArray<edge?>() {
        private const val N = 15
    }

    internal class polyhedron {
        var e: MyArrayEdge? = null
        var p: MyArrayPoly? = null
        var v: MyArrayVec4? = null

        private constructor() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        private constructor(p: polyhedron?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun add_quad(va: Int, vb: Int, vc: Int, vd: Int) {
            val pg = poly()
            pg.vi = tr_shadowbounds.four_ints(va, vb, vc, vd)
            pg.ni = tr_shadowbounds.four_ints(-1, -1, -1, -1)
            pg.plane = tr_shadowbounds.compute_homogeneous_plane(v.oGet(va), v.oGet(vb), v.oGet(vc))
            p.push_back(pg)
        }

        fun discard_neighbor_info() {
            for (i in 0 until p.size()) {
                val ni = p.oGet(i).ni
                for (j in 0 until ni.size()) {
                    ni.oSet(j, -1)
                }
            }
        }

        fun compute_neighbors() {
            e.empty()
            discard_neighbor_info()
            var found: Boolean
            val P = p.size()
            // for each polygon
            for (i in 0 until P - 1) {
                val vi = p.oGet(i).vi
                val ni = p.oGet(i).ni
                val Si = vi.size()

                // for each edge of that polygon
                for (ii in 0 until Si) {
                    val ii1 = (ii + 1) % Si

                    // continue if we've already found this neighbor
                    if (ni.oGet(ii) != -1) {
                        continue
                    }
                    found = false
                    // check all remaining polygons
                    for (j in i + 1 until P) {
                        val vj = p.oGet(j).vi
                        val nj = p.oGet(j).ni
                        val Sj = vj.size()
                        for (jj in 0 until Sj) {
                            val jj1 = (jj + 1) % Sj
                            if (vi.oGet(ii) === vj.oGet(jj1) && vi.oGet(ii1) === vj.oGet(jj)) {
                                val ed = edge()
                                ed.vi.get(0) = vi.oGet(ii)
                                ed.vi.get(1) = vi.oGet(ii1)
                                ed.pi.get(0) = i
                                ed.pi.get(1) = j
                                e.push_back(ed)
                                ni.oSet(ii, j)
                                ni.oSet(jj, i)
                                found = true
                                break
                            } else if (vi.oGet(ii) === vj.oGet(jj) && vi.oGet(ii1) === vj.oGet(jj1)) {
                                System.err.printf("why am I here?\n")
                            }
                        }
                        if (found) {
                            break
                        }
                    }
                }
            }
        }

        fun recompute_planes() {
            // for each polygon
            for (i in 0 until p.size()) {
                p.oGet(i).plane = tr_shadowbounds.compute_homogeneous_plane(
                    v.oGet(p.oGet(i).vi.oGet(0)),
                    v.oGet(p.oGet(i).vi.oGet(1)),
                    v.oGet(p.oGet(i).vi.oGet(2))
                )
            }
        }

        fun transform(m: idMat4?) {
            for (i in 0 until v.size()) {
                v.oSet(i, m.oMultiply(v.oGet(i)))
            }
            recompute_planes()
        }
    }

    internal object MySegments : MyArray<idVec4?>() {
        private const val N = 36
    }
}