package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.deformInfo_s
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_trisurf.R_DeriveTangents
import neo.TempDump
import neo.framework.DeclManager
import neo.framework.FileSystem_h
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib.idException
import neo.idlib.Text.Lexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import java.util.*

/**
 *
 */
object Model_liquid {
    const val LIQUID_MAX_SKIP_FRAMES = 5
    const val LIQUID_MAX_TYPES = 3

    //    
    /*
     ===============================================================================

     Liquid model

     ===============================================================================
     */
    class idRenderModelLiquid : idRenderModelStatic() {
        //
        var nextDropTime = 0
        private var deformInfo // used to create srfTriangles_s from base frames and new vertexes
                : deformInfo_s? = null

        //
        //
        private var density = 0.97f
        private var drop_delay = 1000f
        private var drop_height = 4f
        private var drop_radius = 4
        private var liquid_type = 0
        private lateinit var page1: Array<Float>
        private lateinit var page2: Array<Float>

        //
        private val pages: idList<Float> = idList()

        //
        private val random: idRandom = idRandom()
        private var scale_x = 256.0f
        private var scale_y = 256.0f
        private var seed: Int

        //
        private var shader: idMaterial?
        private var time: Int
        private var update_tics: Int

        //
        private val verts: idList<idDrawVert> = idList()
        private var verts_x = 32
        private var verts_y = 32

        @Throws(idException::class)
        override fun InitFromFile(fileName: String) {
            var i: Int
            var x: Int
            var y: Int
            val token = idToken()
            val parser = idParser(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS)
            val tris = idList<Int>()
            var size_x: Float
            var size_y: Float
            var rate: Float
            name = idStr(fileName)
            if (!parser.LoadFile(fileName)) {
                MakeDefaultModel()
                return
            }
            size_x = scale_x * verts_x
            size_y = scale_y * verts_y
            while (parser.ReadToken(token)) {
                if (0 == token.Icmp("seed")) {
                    seed = parser.ParseInt()
                } else if (0 == token.Icmp("size_x")) {
                    size_x = parser.ParseFloat()
                } else if (0 == token.Icmp("size_y")) {
                    size_y = parser.ParseFloat()
                } else if (0 == token.Icmp("verts_x")) {
                    verts_x = parser.ParseFloat().toInt()
                    if (verts_x < 2) {
                        parser.Warning("Invalid # of verts.  Using default model.")
                        MakeDefaultModel()
                        return
                    }
                } else if (0 == token.Icmp("verts_y")) {
                    verts_y = parser.ParseFloat().toInt()
                    if (verts_y < 2) {
                        parser.Warning("Invalid # of verts.  Using default model.")
                        MakeDefaultModel()
                        return
                    }
                } else if (0 == token.Icmp("liquid_type")) {
                    liquid_type = parser.ParseInt() - 1
                    if (liquid_type < 0 || liquid_type >= Model_liquid.LIQUID_MAX_TYPES) {
                        parser.Warning("Invalid liquid_type.  Using default model.")
                        MakeDefaultModel()
                        return
                    }
                } else if (0 == token.Icmp("density")) {
                    density = parser.ParseFloat()
                } else if (0 == token.Icmp("drop_height")) {
                    drop_height = parser.ParseFloat()
                } else if (0 == token.Icmp("drop_radius")) {
                    drop_radius = parser.ParseInt()
                } else if (0 == token.Icmp("drop_delay")) {
                    drop_delay = Math_h.SEC2MS(parser.ParseFloat())
                } else if (0 == token.Icmp("shader")) {
                    parser.ReadToken(token)
                    shader = DeclManager.declManager.FindMaterial(token)
                } else if (0 == token.Icmp("seed")) {
                    seed = parser.ParseInt()
                } else if (0 == token.Icmp("update_rate")) {
                    rate = parser.ParseFloat()
                    if (rate <= 0.0f || rate > 60.0f) {
                        parser.Warning("Invalid update_rate.  Must be between 0 and 60.  Using default model.")
                        MakeDefaultModel()
                        return
                    }
                    update_tics = (1000 / rate).toInt()
                } else {
                    parser.Warning("Unknown parameter '%s'.  Using default model.", token)
                    MakeDefaultModel()
                    return
                }
            }
            scale_x = size_x / (verts_x - 1)
            scale_y = size_y / (verts_y - 1)
            pages.SetNum(2 * verts_x * verts_y)
            page1 = pages.getList()
            page2 = Arrays.copyOfRange(page1, verts_x * verts_y, page1.size)

            verts.SetNum(verts_x * verts_y)
            i = 0
            y = 0
            while (y < verts_y) {
                x = 0
                while (x < verts_x) {
                    page1[i] = 0.0f
                    page2[i] = 0.0f
                    verts[i].Clear()
                    verts[i].xyz.set(x * scale_x, y * scale_y, 0.0f)
                    verts[i].st.set(x.toFloat() / (verts_x - 1).toFloat(), -y.toFloat() / (verts_y - 1).toFloat())
                    x++
                    i++
                }
                y++
            }
            tris.SetNum((verts_x - 1) * (verts_y - 1) * 6)
            i = 0
            y = 0
            while (y < verts_y - 1) {
                x = 1
                while (x < verts_x) {
                    tris[i + 0] = y * verts_x + x
                    tris[i + 1] = y * verts_x + x - 1
                    tris[i + 2] = (y + 1) * verts_x + x - 1
                    tris[i + 3] = (y + 1) * verts_x + x - 1
                    tris[i + 4] = (y + 1) * verts_x + x
                    tris[i + 0] = y * verts_x + x
                    x++
                    i += 6
                }
                y++
            }

            // build the information that will be common to all animations of this mesh:
            // sil edge connectivity and normal / tangent generation information
            deformInfo =
                tr_trisurf.R_BuildDeformInfo(verts.Num(), verts.getList() as Array<idDrawVert?>, tris.Num(), tris, true)
            bounds.Clear()
            bounds.AddPoint(idVec3(0.0f, 0.0f, drop_height * -10.0f))
            bounds.AddPoint(idVec3((verts_x - 1) * scale_x, (verts_y - 1) * scale_y, drop_height * 10.0f))

            // set the timestamp for reloadmodels
            FileSystem_h.fileSystem.ReadFile(name, null, timeStamp)
            Reset()
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CONTINUOUS
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel = cachedModel
            val staticModel: idRenderModelStatic
            var frames: Int
            val t: Int
            val lerp: Float
            if (cachedModel != null) {
//		delete cachedModel;
                cachedModel = null
            }
            if (TempDump.NOT(deformInfo)) {
                return null
            }
            t = if (null == view) {
                0
            } else {
                view.renderView.time
            }

            // update the liquid model
            frames = (t - time) / update_tics
            if (frames > Model_liquid.LIQUID_MAX_SKIP_FRAMES) {
                // don't let time accumalate when skipping frames
                time += update_tics * (frames - Model_liquid.LIQUID_MAX_SKIP_FRAMES)
                frames = Model_liquid.LIQUID_MAX_SKIP_FRAMES
            }
            while (frames > 0) {
                Update()
                frames--
            }

            // create the surface
            lerp = (t - time).toFloat() / update_tics.toFloat()
            val surf = GenerateSurface(lerp)
            staticModel = idRenderModelStatic()
            staticModel.AddSurface(surf)
            staticModel.bounds = idBounds(surf.geometry!!.bounds)
            return staticModel
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            // FIXME: need to do this better
            return bounds
        }

        override fun Reset() {
            var i: Int
            var x: Int
            var y: Int
            if (pages.Num() < 2 * verts_x * verts_y) {
                return
            }
            nextDropTime = 0
            time = 0
            random.SetSeed(seed)
            page1 = pages.getList()
            page2 = page1.copyOfRange(verts_x * verts_y, page1.size)
            i = 0
            y = 0
            while (y < verts_y) {
                x = 0
                while (x < verts_x) {
                    page1[i] = 0.0f
                    page2[i] = 0.0f
                    verts[i].xyz.z = 0.0f
                    x++
                    i++
                }
                y++
            }
        }

        fun IntersectBounds(bounds: idBounds, displacement: Float) {
            var cx: Int
            var cy: Int
            var left: Int
            var top: Int
            var right: Int
            var bottom: Int
            val up: Float
            val down: Float
            var pos: Float
            left = (bounds[0].x / scale_x).toInt()
            right = (bounds[1].x / scale_x).toInt()
            top = (bounds[0].y / scale_y).toInt()
            bottom = (bounds[1].y / scale_y).toInt()
            down = bounds[0].z
            up = bounds[1].z
            if (right < 1 || left >= verts_x || bottom < 1 || top >= verts_x) {
                return
            }

            // Perform edge clipping...
            if (left < 1) {
                left = 1
            }
            if (right >= verts_x) {
                right = verts_x - 1
            }
            if (top < 1) {
                top = 1
            }
            if (bottom >= verts_y) {
                bottom = verts_y - 1
            }
            cy = top
            while (cy < bottom) {
                cx = left
                while (cx < right) {
                    pos = page1[verts_x * cy + cx]
                    if (pos > down) { //&& ( *pos < up ) ) {
                        page1[verts_x * cy + cx] = down
                    }
                    cx++
                }
                cy++
            }
        }

        private fun GenerateSurface(lerp: Float): modelSurface_s {
            val tri: srfTriangles_s
            var i: Int
            val base: Int
            var vert: idDrawVert
            val surf = modelSurface_s()
            val inv_lerp: Float
            inv_lerp = 1.0f - lerp
            vert = verts[0]
            i = 0
            while (i < verts.Num()) {
                vert.xyz.z = page1[i] * lerp + page2[i] * inv_lerp
                vert = verts[++i]
            }
            val deformInfo = deformInfo!!
            tr_local.tr.pc.c_deformedSurfaces++
            tr_local.tr.pc.c_deformedVerts += deformInfo.numOutputVerts
            tr_local.tr.pc.c_deformedIndexes += deformInfo.numIndexes
            tri = tr_trisurf.R_AllocStaticTriSurf()

            // note that some of the data is references, and should not be freed
            tri.deformedSurface = true
            tri.numIndexes = deformInfo.numIndexes
            tri.indexes = deformInfo.indexes
            tri.silIndexes = deformInfo.silIndexes
            tri.numMirroredVerts = deformInfo.numMirroredVerts
            tri.mirroredVerts = deformInfo.mirroredVerts
            tri.numDupVerts = deformInfo.numDupVerts
            tri.dupVerts = deformInfo.dupVerts
            tri.numSilEdges = deformInfo.numSilEdges
            tri.silEdges = deformInfo.silEdges
            tri.dominantTris = deformInfo.dominantTris
            tri.numVerts = deformInfo.numOutputVerts
            tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
            Simd.SIMDProcessor.Memcpy(tri.verts!!, verts, deformInfo.numSourceVerts)

            // replicate the mirror seam vertexes
            base = deformInfo.numOutputVerts - deformInfo.numMirroredVerts
            i = 0
            while (i < deformInfo.numMirroredVerts) {
                tri.verts!![base + i] = tri.verts!![deformInfo.mirroredVerts!![i]]
                i++
            }
            tr_trisurf.R_BoundTriSurf(tri)

            // If a surface is going to be have a lighting interaction generated, it will also have to call
            // R_DeriveTangents() to get normals, tangents, and face planes.  If it only
            // needs shadows generated, it will only have to generate face planes.  If it only
            // has ambient drawing, or is culled, no additional work will be necessary
            if (!RenderSystem_init.r_useDeferredTangents.GetBool()) {
                // set face planes, vertex normals, tangents
                R_DeriveTangents(tri)
            }
            surf.geometry = tri
            surf.shader = shader
            return surf
        }

        private fun WaterDrop(x: Int, y: Int, page: Array<Float>) {
            var x = x
            var y = y
            var cx: Int
            var cy: Int
            var left: Int
            var top: Int
            var right: Int
            var bottom: Int
            var square: Int
            val radsquare = drop_radius * drop_radius
            val invlength = 1.0f / radsquare.toFloat()
            var dist: Float
            if (x < 0) {
                x = 1 + drop_radius + random.RandomInt((verts_x - 2 * drop_radius - 1).toDouble())
            }
            if (y < 0) {
                y = 1 + drop_radius + random.RandomInt((verts_y - 2 * drop_radius - 1).toDouble())
            }
            left = -drop_radius
            right = drop_radius
            top = -drop_radius
            bottom = drop_radius

            // Perform edge clipping...
            if (x - drop_radius < 1) {
                left -= x - drop_radius - 1
            }
            if (y - drop_radius < 1) {
                top -= y - drop_radius - 1
            }
            if (x + drop_radius > verts_x - 1) {
                right -= x + drop_radius - verts_x + 1
            }
            if (y + drop_radius > verts_y - 1) {
                bottom -= y + drop_radius - verts_y + 1
            }
            cy = top
            while (cy < bottom) {
                cx = left
                while (cx < right) {
                    square = cy * cy + cx * cx
                    if (square < radsquare) {
                        dist = idMath.Sqrt(square.toFloat() * invlength)
                        page[verts_x * (cy + y) + cx + x] += idMath.Cos16(dist * idMath.PI * 0.5f) * drop_height
                    }
                    cx++
                }
                cy++
            }
        }

        fun Array<Float>.swap(anotherList: Array<Float>) {
            val length = size
            val c = FloatArray(length)
            System.arraycopy(this, 0, c, 0, length)
            System.arraycopy(anotherList, 0, this, 0, length)
            System.arraycopy(c, 0, anotherList, 0, length)
        }

        private fun Update() {
            var x: Int
            var y: Int
            var p2: Int
            var p1: Int
            var value: Float
            time += update_tics
            page1.swap(page2)
            if (time > nextDropTime) {
                WaterDrop(-1, -1, page2)
                nextDropTime = (time + drop_delay).toInt()
            } else if (time < nextDropTime - drop_delay) {
                nextDropTime = (time + drop_delay).toInt()
            }

//            p1 = page1;
//            p2 = page2;
            p2 = 0
            p1 = p2
            when (liquid_type) {
                0 -> {
                    y = 1
                    while (y < verts_y - 1) {
                        p2 += verts_x
                        p1 += verts_x
                        x = 1
                        while (x < verts_x - 1) {
                            value = ((page2[p2 + x + verts_x]
                                    + page2[p2 + x - verts_x]
                                    + page2[p2 + x + 1]
                                    + page2[p2 + x - 1]
                                    + page2[p2 + x - verts_x - 1]
                                    + page2[p2 + x - verts_x + 1]
                                    + page2[p2 + x + verts_x - 1]
                                    + page2[p2 + x + verts_x + 1]
                                    + page2[p2 + x]) * (2.0f / 9.0f)
                                    - page1[p1 + x])
                            page1[p1 + x] = value * density
                            x++
                        }
                        y++
                    }
                }
                1 -> {
                    y = 1
                    while (y < verts_y - 1) {
                        p2 += verts_x
                        p1 += verts_x
                        x = 1
                        while (x < verts_x - 1) {
                            value = ((page2[p2 + x + verts_x]
                                    + page2[p2 + x - verts_x]
                                    + page2[p2 + x + 1]
                                    + page2[p2 + x - 1]
                                    + page2[p2 + x - verts_x - 1]
                                    + page2[p2 + x - verts_x + 1]
                                    + page2[p2 + x + verts_x - 1]
                                    + page2[p2 + x + verts_x + 1]) * 0.25f
                                    - page1[p1 + x])
                            page1[p1 + x] = value * density
                            x++
                        }
                        y++
                    }
                }
                2 -> {
                    y = 1
                    while (y < verts_y - 1) {
                        p2 += verts_x
                        p1 += verts_x
                        x = 1
                        while (x < verts_x - 1) {
                            value = (page2[p2 + x + verts_x]
                                    + page2[p2 + x - verts_x]
                                    + page2[p2 + x + 1]
                                    + page2[p2 + x - 1]
                                    + page2[p2 + x - verts_x - 1]
                                    + page2[p2 + x - verts_x + 1]
                                    + page2[p2 + x + verts_x - 1]
                                    + page2[p2 + x + verts_x + 1]
                                    + page2[p2 + x]) * (1.0f / 9.0f)
                            page1[p1 + x] = value * density
                            x++
                        }
                        y++
                    }
                }
            }
        }

        //
        //
        init {
            shader = DeclManager.declManager.FindMaterial("")
            update_tics = 33 // ~30 hz
            time = 0
            seed = 0
            random.SetSeed(0)
        }
    }
}