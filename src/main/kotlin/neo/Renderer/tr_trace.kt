package neo.Renderer

import neo.Renderer.*
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.localTrace_t
import neo.framework.Common
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Timer.idTimer
import neo.idlib.math.Math_h
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import org.lwjgl.opengl.GL11

/**
 *
 */
object tr_trace {
    private const val TEST_TRACE = false

    /*
     =================
     R_LocalTrace

     If we resort the vertexes so all silverts come first, we can save some work here.
     =================
     */
    fun R_LocalTrace(start: idVec3?, end: idVec3?, radius: Float, tri: srfTriangles_s?): localTrace_t? {
        var i: Int
        var j: Int
        val cullBits: ByteArray
        val planes: Array<idPlane?> = idPlane.Companion.generateArray(4)
        val hit = localTrace_t()
        var c_testEdges: Int
        var c_testPlanes: Int
        var c_intersect: Int
        val startDir = idVec3()
        val totalOr = ByteArray(1)
        val radiusSqr: Float
        var trace_timer: idTimer? = null
        if (tr_trace.TEST_TRACE) {
            trace_timer = idTimer()
            trace_timer.Start()
        }
        hit.fraction = 1.0f

        // create two planes orthogonal to each other that intersect along the trace
        startDir.set(end.minus(start))
        startDir.Normalize()
        startDir.NormalVectors(planes[0].Normal(), planes[1].Normal())
        planes[0].set(3, -start.times(planes[0].Normal()))
        planes[1].set(3, -start.times(planes[1].Normal()))

        // create front and end planes so the trace is on the positive sides of both
        planes[2].set(startDir)
        planes[2].set(3, -start.times(planes[2].Normal()))
        planes[3].set(startDir.oNegative())
        planes[3].set(3, -end.times(planes[3].Normal()))

        // catagorize each point against the four planes
        cullBits = ByteArray(tri.numVerts)
        Simd.SIMDProcessor.TracePointCull(cullBits, totalOr, radius, planes, tri.verts, tri.numVerts)

        // if we don't have points on both sides of both the ray planes, no intersection
        if (totalOr[0] xor (totalOr[0] shr 4) and 3 != 0) {
            //common.Printf( "nothing crossed the trace planes\n" );
            return hit
        }

        // if we don't have any points between front and end, no intersection
        if (totalOr[0] xor (totalOr[0] shr 1) and 4 != 0) {
            //common.Printf( "trace didn't reach any triangles\n" );
            return hit
        }

        // scan for triangles that cross both planes
        c_testPlanes = 0
        c_testEdges = 0
        c_intersect = 0
        radiusSqr = Math_h.Square(radius)
        startDir.set(end.minus(start))
        if (null == tri.facePlanes || !tri.facePlanesCalculated) {
            tr_trisurf.R_DeriveFacePlanes(tri)
        }
        i = 0
        j = 0
        while (i < tri.numIndexes) {
            var d1: Float
            var d2: Float
            var f: Float
            var d: Float
            var edgeLengthSqr: Float
            var plane: idPlane?
            val point = idVec3()
            val dir: Array<idVec3?> = idVec3.Companion.generateArray(3)
            val cross = idVec3()
            val edge = idVec3()
            var triOr: Byte

            // get sidedness info for the triangle
            triOr = cullBits[tri.indexes[i + 0]]
            triOr = triOr or cullBits[tri.indexes[i + 1]]
            triOr = triOr or cullBits[tri.indexes[i + 2]]

            // if we don't have points on both sides of both the ray planes, no intersection
            if (triOr xor (triOr shr 4) and 3 != 0) {
                i += 3
                j++
                continue
            }

            // if we don't have any points between front and end, no intersection
            if (triOr xor (triOr shr 1) and 4 != 0) {
                i += 3
                j++
                continue
            }
            c_testPlanes++
            plane = tri.facePlanes[j]
            d1 = plane.Distance(start)
            d2 = plane.Distance(end)
            if (d1 <= d2) {
                i += 3
                j++
                continue  // comning at it from behind or parallel
            }
            if (d1 < 0.0f) {
                i += 3
                j++
                continue  // starts past it
            }
            if (d2 > 0.0f) {
                i += 3
                j++
                continue  // finishes in front of it
            }
            f = d1 / (d1 - d2)
            if (f < 0.0f) {
                i += 3
                j++
                continue  // shouldn't happen
            }
            if (f >= hit.fraction) {
                i += 3
                j++
                continue  // have already hit something closer
            }
            c_testEdges++

            // find the exact point of impact with the plane
            point.set(start.oPlus(startDir.times(f)))

            // see if the point is within the three edges
            // if radius > 0 the triangle is expanded with a circle in the triangle plane
            dir[0].set(tri.verts[tri.indexes[i + 0]].xyz.minus(point))
            dir[1].set(tri.verts[tri.indexes[i + 1]].xyz.minus(point))
            cross.set(dir[0].Cross(dir[1]))
            d = plane.Normal().times(cross)
            if (d > 0.0f) {
                if (radiusSqr <= 0.0f) {
                    i += 3
                    j++
                    continue
                }
                edge.set(tri.verts[tri.indexes[i + 0]].xyz.minus(tri.verts[tri.indexes[i + 1]].xyz))
                edgeLengthSqr = edge.LengthSqr()
                if (cross.LengthSqr() > edgeLengthSqr * radiusSqr) {
                    i += 3
                    j++
                    continue
                }
                d = dir[0].times(edge)
                if (d < 0.0f) {
                    edge.set(tri.verts[tri.indexes[i + 0]].xyz.minus(tri.verts[tri.indexes[i + 2]].xyz))
                    d = dir[0].times(edge)
                    if (d < 0.0f) {
                        if (dir[0].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                } else if (d > edgeLengthSqr) {
                    edge.set(tri.verts[tri.indexes[i + 1]].xyz.minus(tri.verts[tri.indexes[i + 2]].xyz))
                    d = dir[1].times(edge)
                    if (d < 0.0f) {
                        if (dir[1].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                }
            }
            dir[2].set(tri.verts[tri.indexes[i + 2]].xyz.minus(point))
            cross.set(dir[1].Cross(dir[2]))
            d = plane.Normal().times(cross)
            if (d > 0.0f) {
                if (radiusSqr <= 0.0f) {
                    i += 3
                    j++
                    continue
                }
                edge.set(tri.verts[tri.indexes[i + 1]].xyz.minus(tri.verts[tri.indexes[i + 2]].xyz))
                edgeLengthSqr = edge.LengthSqr()
                if (cross.LengthSqr() > edgeLengthSqr * radiusSqr) {
                    i += 3
                    j++
                    continue
                }
                d = dir[1].times(edge)
                if (d < 0.0f) {
                    edge.set(tri.verts[tri.indexes[i + 1]].xyz.minus(tri.verts[tri.indexes[i + 0]].xyz))
                    d = dir[1].times(edge)
                    if (d < 0.0f) {
                        if (dir[1].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                } else if (d > edgeLengthSqr) {
                    edge.set(tri.verts[tri.indexes[i + 2]].xyz.minus(tri.verts[tri.indexes[i + 0]].xyz))
                    d = dir[2].times(edge)
                    if (d < 0.0f) {
                        if (dir[2].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                }
            }
            cross.set(dir[2].Cross(dir[0]))
            d = plane.Normal().times(cross)
            if (d > 0.0f) {
                if (radiusSqr <= 0.0f) {
                    i += 3
                    j++
                    continue
                }
                edge.set(tri.verts[tri.indexes[i + 2]].xyz.minus(tri.verts[tri.indexes[i + 0]].xyz))
                edgeLengthSqr = edge.LengthSqr()
                if (cross.LengthSqr() > edgeLengthSqr * radiusSqr) {
                    i += 3
                    j++
                    continue
                }
                d = dir[2].times(edge)
                if (d < 0.0f) {
                    edge.set(tri.verts[tri.indexes[i + 2]].xyz.minus(tri.verts[tri.indexes[i + 1]].xyz))
                    d = dir[2].times(edge)
                    if (d < 0.0f) {
                        if (dir[2].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                } else if (d > edgeLengthSqr) {
                    edge.set(tri.verts[tri.indexes[i + 0]].xyz.minus(tri.verts[tri.indexes[i + 1]].xyz))
                    d = dir[0].times(edge)
                    if (d < 0.0f) {
                        if (dir[0].LengthSqr() > radiusSqr) {
                            i += 3
                            j++
                            continue
                        }
                    }
                }
            }

            // we hit it
            c_intersect++
            hit.fraction = f
            hit.normal.set(plane.Normal())
            hit.point.set(point)
            hit.indexes[0] = tri.indexes[i]
            hit.indexes[1] = tri.indexes[i + 1]
            hit.indexes[2] = tri.indexes[i + 2]
            i += 3
            j++
        }
        if (tr_trace.TEST_TRACE) {
            trace_timer.Stop()
            Common.common.Printf(
                "testVerts:%d c_testPlanes:%d c_testEdges:%d c_intersect:%d msec:%1.4f\n",
                tri.numVerts, c_testPlanes, c_testEdges, c_intersect, trace_timer.Milliseconds()
            )
        }
        return hit
    }

    /*
     =================
     RB_DrawExpandedTriangles
     =================
     */
    fun RB_DrawExpandedTriangles(tri: srfTriangles_s?, radius: Float, vieworg: idVec3?) {
        var i: Int
        var j: Int
        var k: Int
        val dir: Array<idVec3?> = idVec3.Companion.generateArray(6)
        val normal = idVec3()
        val point = idVec3()
        i = 0
        while (i < tri.numIndexes) {
            val p /*[3]*/ = arrayOf(
                tri.verts[tri.indexes[i + 0]].xyz,
                tri.verts[tri.indexes[i + 1]].xyz,
                tri.verts[tri.indexes[i + 2]].xyz
            )
            dir[0].set(p[0].minus(p[1]))
            dir[1].set(p[1].minus(p[2]))
            dir[2].set(p[2].minus(p[0]))
            normal.set(dir[0].Cross(dir[1]))
            if (normal.times(p[0]) < normal.times(vieworg)) {
                i += 3
                continue
            }
            dir[0].set(normal.Cross(dir[0]))
            dir[1].set(normal.Cross(dir[1]))
            dir[2].set(normal.Cross(dir[2]))
            dir[0].Normalize()
            dir[1].Normalize()
            dir[2].Normalize()
            qgl.qglBegin(GL11.GL_LINE_LOOP)
            j = 0
            while (j < 3) {
                k = (j + 1) % 3
                dir[4].set(dir[j].oPlus(dir[k]).oMultiply(0.5f))
                dir[4].Normalize()
                dir[3].set(dir[j].oPlus(dir[4]).oMultiply(0.5f))
                dir[3].Normalize()
                dir[5].set(dir[4].oPlus(dir[k]).oMultiply(0.5f))
                dir[5].Normalize()
                point.set(p[k].oPlus(dir[j].times(radius)))
                qgl.qglVertex3f(point.get(0), point.get(1), point.get(2))
                point.set(p[k].oPlus(dir[3].times(radius)))
                qgl.qglVertex3f(point.get(0), point.get(1), point.get(2))
                point.set(p[k].oPlus(dir[4].times(radius)))
                qgl.qglVertex3f(point.get(0), point.get(1), point.get(2))
                point.set(p[k].oPlus(dir[5].times(radius)))
                qgl.qglVertex3f(point.get(0), point.get(1), point.get(2))
                point.set(p[k].oPlus(dir[k].times(radius)))
                qgl.qglVertex3f(point.get(0), point.get(1), point.get(2))
                j++
            }
            qgl.qglEnd()
            i += 3
        }
    }

    /*
     ================
     RB_ShowTrace

     Debug visualization
     ================
     */
    fun RB_ShowTrace(drawSurfs: Array<drawSurf_s?>?, numDrawSurfs: Int) {
        var i: Int
        var tri: srfTriangles_s
        var surf: drawSurf_s?
        val start = idVec3()
        val end = idVec3()
        val localStart = idVec3()
        val localEnd = idVec3()
        var hit: localTrace_t?
        val radius: Float
        if (RenderSystem_init.r_showTrace.GetInteger() == 0) {
            return
        }
        radius = if (RenderSystem_init.r_showTrace.GetInteger() == 2) {
            5.0f
        } else {
            0.0f
        }

        // determine the points of the trace
        start.set(tr_local.backEnd.viewDef.renderView.vieworg)
        end.set(start.oPlus(tr_local.backEnd.viewDef.renderView.viewaxis.get(0).times(4000f)))

        // check and draw the surfaces
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        tr_backend.GL_TexEnv(GL11.GL_MODULATE)
        Image.globalImages.whiteImage.Bind()

        // find how many are ambient
        i = 0
        while (i < numDrawSurfs) {
            surf = drawSurfs.get(i)
            tri = surf.geo
            if (i > 211) {
                i++
                continue
            }
            if (tri == null || tri.verts == null) {
                i++
                continue
            }

            // transform the points into local space
            tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, start, localStart)
            tr_main.R_GlobalPointToLocal(surf.space.modelMatrix, end, localEnd)

            // check the bounding box
            if (!tri.bounds.Expand(radius).LineIntersection(localStart, localEnd)) {
                i++
                continue
            }
            qgl.qglLoadMatrixf(surf.space.modelViewMatrix)

            // highlight the surface
            tr_backend.GL_State(tr_local.GLS_SRCBLEND_SRC_ALPHA or tr_local.GLS_DSTBLEND_ONE_MINUS_SRC_ALPHA)
            qgl.qglColor4f(1f, 0f, 0f, 0.25f)
            tr_render.RB_DrawElementsImmediate(tri)

            // draw the bounding box
            tr_backend.GL_State(tr_local.GLS_DEPTHFUNC_ALWAYS)
            qgl.qglColor4f(1f, 1f, 1f, 1f)
            tr_rendertools.RB_DrawBounds(tri.bounds)
            if (radius != 0.0f) {
                // draw the expanded triangles
                qgl.qglColor4f(0.5f, 0.5f, 1.0f, 1.0f)
                tr_trace.RB_DrawExpandedTriangles(tri, radius, localStart)
            }

            // check the exact surfaces
            hit = tr_trace.R_LocalTrace(localStart, localEnd, radius, tri)
            if (hit.fraction < 1.0) {
                qgl.qglColor4f(1f, 1f, 1f, 1f)
                tr_rendertools.RB_DrawBounds(idBounds(hit.point).Expand(1f))
            }
            i++
        }
    }
}