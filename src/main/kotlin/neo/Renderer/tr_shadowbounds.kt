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
    private val lut: Array<polyhedron> = Array<polyhedron>(64) { polyhedron() }
    private val p: polyhedron = polyhedron()

    //int MyArrayInt::max_size = 0;
    fun four_ints(a: Int, b: Int, c: Int, d: Int): IntArray {
        val vi = ArrayList<Int>()
        vi.add(a)
        vi.add(b)
        vi.add(c)
        vi.add(d)
        return vi.toIntArray()
    }

    //int MyArrayVec4::max_size = 0;
    fun homogeneous_difference(a: idVec4, b: idVec4): idVec3 {
        val v = idVec3()
        v.x = b.x * a.w - a.x * b.w
        v.y = b.y * a.w - a.y * b.w
        v.z = b.z * a.w - a.z * b.w
        return v
    }

    // handles positive w only
    fun compute_homogeneous_plane(a: idVec4, b: idVec4, c: idVec4): idVec4 {
        val v = idVec4()
        var t: idVec4
        if (a[3] == 0f) {
            t = a
            a.set(b)
            b.set(c)
            c.set(t)
        }
        if (a[3] == 0f) {
            t = a
            a.set(b)
            b.set(c)
            c.set(t)
        }

        // can't handle 3 infinite points
        if (a[3] == 0f) {
            return v
        }
        val vb = idVec3(homogeneous_difference(a, b))
        val vc = idVec3(homogeneous_difference(a, c))
        val n = idVec3(vb.Cross(vc))
        n.Normalize()
        v.x = n.x
        v.y = n.y
        v.z = n.z
        v.w = -n.times(idVec3(a.x, a.y, a.z)) / a.w
        return v
    }

    //int MyArrayPoly::max_size = 0;
    // make a unit cube
    fun PolyhedronFromBounds(b: idBounds): polyhedron {

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
        if (p.e.size == 0) {
            p.v.add(idVec4(-1, -1, 1, 1))
            p.v.add(idVec4(1, -1, 1, 1))
            p.v.add(idVec4(1, 1, 1, 1))
            p.v.add(idVec4(-1, 1, 1, 1))
            p.v.add(idVec4(-1, -1, -1, 1))
            p.v.add(idVec4(1, -1, -1, 1))
            p.v.add(idVec4(1, 1, -1, 1))
            p.v.add(idVec4(-1, 1, -1, 1))
            p.add_quad(0, 1, 2, 3)
            p.add_quad(7, 6, 5, 4)
            p.add_quad(1, 0, 4, 5)
            p.add_quad(2, 1, 5, 6)
            p.add_quad(3, 2, 6, 7)
            p.add_quad(0, 3, 7, 4)
            p.compute_neighbors()
            p.recompute_planes()
            p.v.clear() // no need to copy this data since it'll be replaced
        }
        val p2 = polyhedron(p)
        val min = idVec3(b[0])
        val max = idVec3(b[1])
        p2.v.clear()
        p2.v.add(idVec4(min.x, min.y, max.z, 1f))
        p2.v.add(idVec4(max.x, min.y, max.z, 1f))
        p2.v.add(idVec4(max.x, max.y, max.z, 1f))
        p2.v.add(idVec4(min.x, max.y, max.z, 1f))
        p2.v.add(idVec4(min.x, min.y, min.z, 1f))
        p2.v.add(idVec4(max.x, min.y, min.z, 1f))
        p2.v.add(idVec4(max.x, max.y, min.z, 1f))
        p2.v.add(idVec4(min.x, max.y, min.z, 1f))
        p2.recompute_planes()
        return p2
    }

    fun make_sv(oc: polyhedron, light: idVec4): polyhedron {
        var index = 0
        for (i in 0..5) {
            if (oc.p[i].plane.times(light) > 0) {
                index = index or (1 shl i)
            }
        }
        if (lut[index].e.size == 0) {
            lut[index] = oc
            val ph: polyhedron = lut[index]
            val V = ph.v.size
            for (j in 0 until V) {
                val proj = idVec3(homogeneous_difference(light, ph.v[j]))
                ph.v.add(idVec4(proj.x, proj.y, proj.z, 0f))
            }
            ph.p.clear()
            for (i in 0 until oc.p.size) {
                if (oc.p[i].plane.times(light) > 0) {
                    ph.p.add(oc.p[i])
                }
            }
            if (ph.p.size == 0) {
                return polyhedron().also { lut[index] = it }
            }
            ph.compute_neighbors()
            val vpg = ArrayList<poly>()
            val I = ph.p.size
            for (i in 0 until I) {
                val vi = ph.p[i].vi
                val ni = ph.p[i].ni
                val S = vi.size
                for (j in 0 until S) {
                    if (ni[j] == -1) {
                        val pg = poly()
                        val a: Int = vi[(j + 1) % S]
                        val b: Int = vi[j]
                        pg.vi = four_ints(a, b, b + V, a + V)
                        pg.ni = four_ints(-1, -1, -1, -1)
                        vpg.add(pg)
                    }
                }
            }
            for (i in 0 until vpg.size) {
                ph.p.add(vpg.get(i))
            }
            ph.compute_neighbors()
            ph.v.clear() // no need to copy this data since it'll be replaced
        }
        val ph2: polyhedron = lut[index]

        // initalize vertices
        ph2.v = oc.v
        val V = ph2.v.size
        for (j in 0 until V) {
            val proj = idVec3(homogeneous_difference(light, ph2.v[j]))
            ph2.v.add(idVec4(proj.x, proj.y, proj.z, 0f))
        }

        // need to compute planes for the shadow volume (sv)
        ph2.recompute_planes()
        return ph2
    }

    //int MyArrayEdge::max_size = 0;
    fun polyhedron_edges(a: polyhedron, e: ArrayList<idVec4>) {
        e.clear()
        if (a.e.size == 0 && a.p.size != 0) {
            a.compute_neighbors()
        }
        for (i in 0 until a.e.size) {
            e.add(a.v[a.e[i].vi[0]])
            e.add(a.v[a.e[i].vi[1]])
        }
    }

    // clip the segments of e by the planes of polyhedron a.
    fun clip_segments(ph: polyhedron, `is`: ArrayList<idVec4>, os: ArrayList<idVec4>) {
        val p = ph.p
        var i = 0
        while (i < `is`.size) {
            var a = `is`[i]
            var b = `is`[i + 1]
            var c: idVec4
            var discard = false
            for (j in 0 until p.size) {
                val da = a.times(p[j].plane)
                val db = b.times(p[j].plane)
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
                        c = a.times(db * rdw).plus(b.times(da * rdw)).unaryMinus()
                        a = c
                    }
                    1 -> {
                        c = a.times(db * rdw).plus(b.times(da * rdw)).unaryMinus()
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
                os.add(a)
                os.add(b)
            }
            i += 2
        }
    }

    fun make_idMat4(m: FloatArray): idMat4 {
        return idMat4(
            m[0], m[4], m[8], m[12],
            m[1], m[5], m[9], m[13],
            m[2], m[6], m[10], m[14],
            m[3], m[7], m[11], m[15]
        )
    }

    fun v4to3(v: idVec4): idVec3 {
        return idVec3(v.x / v.w, v.y / v.w, v.z / v.w)
    }

    fun draw_polyhedron(viewDef: viewDef_s, p: polyhedron, color: idVec4) {
        for (i in 0 until p.e.size) {
            viewDef.renderWorld!!.DebugLine(
                color,
                v4to3(p.v[p.e[i].vi[0]]),
                v4to3(p.v[p.e[i].vi[1]])
            )
        }
    }

    fun draw_segments(viewDef: viewDef_s, s: ArrayList<idVec4>, color: idVec4) {
        var i = 0
        while (i < s.size) {
            viewDef.renderWorld!!.DebugLine(color, v4to3(s.get(i)), v4to3(s.get(i + 1)))
            i += 2
        }
    }

    fun world_to_hclip(viewDef: viewDef_s, global: idVec4, clip: idVec4) {
        var i: Int
        val view = idVec4()
        i = 0
        while (i < 4) {
            view[i] =
                global[0] * viewDef.worldSpace.modelViewMatrix[i + 0 * 4] + global[1] * viewDef.worldSpace.modelViewMatrix[i + 1 * 4] + global[2] * viewDef.worldSpace.modelViewMatrix[i + 2 * 4] + global[3] * viewDef.worldSpace.modelViewMatrix[i + 3 * 4]
            i++
        }
        i = 0
        while (i < 4) {
            clip[i] =
                view[0] * viewDef.projectionMatrix[i + 0 * 4] + view[1] * viewDef.projectionMatrix[i + 1 * 4] + view[2] * viewDef.projectionMatrix[i + 2 * 4] + view[3] * viewDef.projectionMatrix[i + 3 * 4]
            i++
        }
    }

    fun R_CalcIntersectionScissor(
        lightDef: idRenderLightLocal,
        entityDef: idRenderEntityLocal,
        viewDef: viewDef_s
    ): idScreenRect {
        val omodel = make_idMat4(entityDef.modelMatrix)
        val lmodel = make_idMat4(lightDef.modelMatrix)

        // compute light polyhedron
        val lvol: polyhedron = PolyhedronFromBounds(lightDef.frustumTris!!.bounds)
        // transform it into world space
        //lvol.transform( lmodel );

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            draw_polyhedron(viewDef, lvol, Lib.colorRed)
        }

        // compute object polyhedron
        val vol: polyhedron = PolyhedronFromBounds(entityDef.referenceBounds)

        //viewDef.renderWorld.DebugBounds( colorRed, lightDef.frustumTris.bounds );
        //viewDef.renderWorld.DebugBox( colorBlue, idBox( model.Bounds(), entityDef.parms.origin, entityDef.parms.axis ) );
        // transform it into world space
        vol.transform(omodel)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            draw_polyhedron(viewDef, vol, Lib.colorBlue)
        }

        // transform light position into world space
        val lightpos = idVec4(
            lightDef.globalLightOrigin.x,
            lightDef.globalLightOrigin.y,
            lightDef.globalLightOrigin.z,
            1.0f
        )

        // generate shadow volume "polyhedron"
        val sv: polyhedron = make_sv(vol, lightpos)
        val in_segs = ArrayList<idVec4>(36)
        val out_segs = ArrayList<idVec4>(36)

        // get shadow volume edges
        polyhedron_edges(sv, in_segs)
        // clip them against light bounds planes
        clip_segments(lvol, in_segs, out_segs)

        // get light bounds edges
        polyhedron_edges(lvol, in_segs)
        // clip them by the shadow volume
        clip_segments(sv, in_segs, out_segs)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2) {
            draw_segments(viewDef, out_segs, Lib.colorGreen)
        }
        val outbounds = idBounds()
        outbounds.Clear()
        for (i in 0 until out_segs.size) {
            val v = idVec4()
            world_to_hclip(viewDef, out_segs[i], v)
            if (v.w <= 0.0f) {
                return lightDef.viewLight!!.scissorRect
            }
            val rv = idVec3(v.x, v.y, v.z)
            rv.divAssign(v.w)
            outbounds.AddPoint(rv)
        }

        // limit the bounds to avoid an inside out scissor rectangle due to floating point to short conversion
        if (outbounds[0].x < -1.0f) {
            outbounds[0].x = -1.0f
        }
        if (outbounds[1].x > 1.0f) {
            outbounds[1].x = 1.0f
        }
        if (outbounds[0].y < -1.0f) {
            outbounds[0].y = -1.0f
        }
        if (outbounds[1].y > 1.0f) {
            outbounds[1].y = 1.0f
        }
        val w2 = (viewDef.viewport.x2 - viewDef.viewport.x1 + 1) / 2.0f
        val x = viewDef.viewport.x1.toFloat()
        val h2 = (viewDef.viewport.y2 - viewDef.viewport.y1 + 1) / 2.0f
        val y = viewDef.viewport.y1.toFloat()
        val rect = idScreenRect()
        rect.x1 = (outbounds[0].x * w2 + w2 + x).toInt()
        rect.x2 = (outbounds[1].x * w2 + w2 + x).toInt()
        rect.y1 = (outbounds[0].y * h2 + h2 + y).toInt()
        rect.y2 = (outbounds[1].y * h2 + h2 + y).toInt()
        rect.Expand()
        rect.Intersect(lightDef.viewLight!!.scissorRect)

        // debug //
        if (RenderSystem_init.r_useInteractionScissors.GetInteger() == -2 && !rect.IsEmpty()) {
            viewDef.renderWorld!!.DebugScreenRect(Lib.colorYellow, rect, viewDef)
        }
        return rect
    }

    class poly {
        var ni: IntArray = IntArray(4)
        var plane: idVec4 = idVec4()
        var vi: IntArray = IntArray(4)
    }

    class edge {
        var pi: IntArray = IntArray(2)
        var vi: IntArray = IntArray(2)
    }

    class polyhedron {
        var e: ArrayList<edge> = ArrayList(15)
        var p: ArrayList<poly> = ArrayList(9)
        var v: ArrayList<idVec4> = ArrayList(16)

        constructor() {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        constructor(p: polyhedron) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        fun add_quad(va: Int, vb: Int, vc: Int, vd: Int) {
            val pg = poly()
            pg.vi = four_ints(va, vb, vc, vd)
            pg.ni = four_ints(-1, -1, -1, -1)
            pg.plane = compute_homogeneous_plane(v[va], v[vb], v[vc])
            p.add(pg)
        }

        fun discard_neighbor_info() {
            for (i in 0 until p.size) {
                val ni = p[i].ni
                for (j in 0 until ni.size) {
                    ni.set(j, -1)
                }
            }
        }

        fun compute_neighbors() {
            e.clear()
            discard_neighbor_info()
            var found: Boolean
            val P = p.size
            // for each polygon
            for (i in 0 until P - 1) {
                val vi = p[i].vi
                val ni = p[i].ni
                val Si = vi.size

                // for each edge of that polygon
                for (ii in 0 until Si) {
                    val ii1 = (ii + 1) % Si

                    // continue if we've already found this neighbor
                    if (ni[ii] != -1) {
                        continue
                    }
                    found = false
                    // check all remaining polygons
                    for (j in i + 1 until P) {
                        val vj = p[j].vi
                        val nj = p[j].ni
                        val Sj = vj.size
                        for (jj in 0 until Sj) {
                            val jj1 = (jj + 1) % Sj
                            if (vi[ii] === vj[jj1] && vi[ii1] === vj[jj]) {
                                val ed = edge()
                                ed.vi[0] = vi[ii]
                                ed.vi[1] = vi[ii1]
                                ed.pi[0] = i
                                ed.pi[1] = j
                                e.add(ed)
                                ni.set(ii, j)
                                ni.set(jj, i)
                                found = true
                                break
                            } else if (vi[ii] === vj[jj] && vi[ii1] === vj[jj1]) {
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
            for (i in 0 until p.size) {
                p[i].plane = compute_homogeneous_plane(
                    v[p[i].vi[0]],
                    v[p[i].vi[1]],
                    v[p[i].vi[2]]
                )
            }
        }

        fun transform(m: idMat4) {
            for (i in 0 until v.size) {
                v.set(i, m.times(v[i]))
            }
            recompute_planes()
        }
    }
}