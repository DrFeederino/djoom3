package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_local.viewDef_s
import neo.TempDump.SERiAL
import neo.framework.Common
import neo.framework.DeclManager
import neo.framework.FileSystem_h
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import java.util.*

/**
 *
 */
object Model_md3 {
    const val MAX_MD3PATH = 64 // from quake3

    /*
     ========================================================================

     .MD3 triangle model file format

     Private structures used by the MD3 loader.

     ========================================================================
     */
    const val MD3_IDENT = ('3'.code shl 24) + ('P'.code shl 16) + ('D'.code shl 8) + 'I'.code
    const val MD3_MAX_FRAMES = 1024 // per model

    //
    // limits
    const val MD3_MAX_LODS = 4
    const val MD3_MAX_SHADERS = 256 // per surface
    const val MD3_MAX_SURFACES = 32 // per model
    const val MD3_MAX_TAGS = 16 // per frame
    const val MD3_MAX_TRIANGLES = 8192 // per surface
    const val MD3_MAX_VERTS = 4096 // per surface
    const val MD3_VERSION = 15

    //
    // vertex scales
    const val MD3_XYZ_SCALE = 1.0 / 64

    //
    // surface geometry should not exceed these limits
    const val SHADER_MAX_VERTEXES = 1000
    const val SHADER_MAX_INDEXES = 6 * SHADER_MAX_VERTEXES
    fun LL(x: Int): Int {
        return Lib.LittleLong(x)
    }

    internal class md3Frame_s {
        val bounds: Array<idVec3> = idVec3.generateArray(2)
        val localOrigin: idVec3 = idVec3()

        //	char		name[16];
        var name: String? = null
        var radius = 0f
    }

    internal class md3Tag_s {
        //	char		name[MAX_MD3PATH];	// tag name
        val axis: Array<idVec3> = idVec3.generateArray(3)
        var name // tag name
                : String? = null
        val origin: idVec3 = idVec3()
    }

    /*
     ** md3Surface_t
     **
     ** CHUNK			SIZE
     ** header			sizeof( md3Surface_t )
     ** shaders			sizeof( md3Shader_t ) * numShaders
     ** triangles[0]		sizeof( md3Triangle_t ) * numTriangles
     ** st				sizeof( md3St_t ) * numVerts
     ** XyzNormals		sizeof( md3XyzNormal_t ) * numVerts * numFrames
     */
    internal class md3Surface_s {
        //
        var flags = 0
        var ident //
                = 0

        //
        //	char		name[MAX_MD3PATH];	// polyset name
        var name // polyset name
                : String = ""
        lateinit var normals: ArrayList<md3XyzNormal_t>
        var numFrames // all surfaces in a model should have the same
                = 0

        //
        var numShaders // all surfaces in a model should have the same
                = 0

        //
        var numTriangles = 0
        var numVerts = 0
        var ofsEnd // next surface follows
                = 0

        //
        var ofsShaders // offset from start of md3Surface_t
                = 0
        var ofsSt // texture coords are common for all frames
                = 0
        var ofsTriangles = 0
        var ofsXyzNormals // numVerts * numFrames
                = 0

        //
        lateinit var shaders: ArrayList<md3Shader_t>

        //
        //
        lateinit var triangles: ArrayList<md3Triangle_t>

        //
        lateinit var verts: ArrayList<md3St_t>
    }

    internal class md3Shader_t {
        //	char				name[MAX_MD3PATH];
        var name: String = ""
        var shader // for in-game use
                : idMaterial? = null
    }

    internal class md3Triangle_t {
        var indexes: IntArray = IntArray(3)
    }

    internal class md3St_t {
        var st: FloatArray = FloatArray(2)
    }

    class md3XyzNormal_t {
        var normal: Short = 0
        var xyz: ShortArray = ShortArray(3)
    }

    internal class md3Header_s : SERiAL {
        //
        var flags = 0

        //
        //
        lateinit var frames: ArrayList<md3Frame_s>
        var ident = 0

        //
        //	char		name[MAX_MD3PATH];	// model name
        var name // model name
                : String = ""

        //
        var numFrames = 0

        //
        var numSkins = 0
        var numSurfaces = 0
        var numTags = 0

        //
        var ofsEnd // end of file
                = 0

        //
        var ofsFrames // offset for first frame
                = 0
        var ofsSurfaces // first surface, others follow
                = 0
        var ofsTags // numFrames * numTags
                = 0
        lateinit var surfaces: ArrayList<md3Surface_s>
        lateinit var tags: ArrayList<md3Tag_s>
        var version = 0
        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    /*
     ===============================================================================

     MD3 animated model

     ===============================================================================
     */
    internal class idRenderModelMD3 : idRenderModelStatic() {
        private var dataSize // just for listing purposes
                = 0
        private val index // model = tr.models[model->index]
                = 0
        private var md3 // only if type == MOD_MESH
                : md3Header_s? = null
        private val numLods = 0

        //
        //
        override fun InitFromFile(fileName: String) {
            var i: Int
            var j: Int
            val pinmodel: md3Header_s
            var frame: md3Frame_s
            var surf: md3Surface_s
            var shader: md3Shader_t
            var tri: md3Triangle_t
            var st: md3St_t
            var xyz: md3XyzNormal_t
            var tag: md3Tag_s
            val buffer = arrayOfNulls<ByteBuffer>(1)
            val version: Int
            var size: Int
            name.set(fileName)
            size = FileSystem_h.fileSystem.ReadFile(fileName, buffer, null)
            if (0 == size || size < 0) {
                return
            }
            pinmodel = md3Header_s()
            pinmodel.Read(buffer[0]!!)
            version = Lib.LittleLong(pinmodel.version)
            if (version != MD3_VERSION) {
                FileSystem_h.fileSystem.FreeFile(buffer)
                Common.common.Warning(
                    "InitFromFile: %s has wrong version (%d should be %d)",
                    fileName, version, MD3_VERSION
                )
                return
            }
            size = Lib.LittleLong(pinmodel.ofsEnd)
            dataSize += size
            //            md3 = new md3Header_s[size];// Mem_Alloc(size);

//            memcpy(md3, buffer, LittleLong(pinmodel.ofsEnd));
//            for (int h = 0; h < size; h++) {
            md3 = md3Header_s()
            md3!!.Read(buffer[0]!!)
            md3!!.ident = LL(md3!!.ident)
            md3!!.version = LL(md3!!.version)
            md3!!.numFrames = LL(md3!!.numFrames)
            md3!!.numTags = LL(md3!!.numTags)
            md3!!.numSurfaces = LL(md3!!.numSurfaces)
            md3!!.ofsFrames = LL(md3!!.ofsFrames)
            md3!!.ofsTags = LL(md3!!.ofsTags)
            md3!!.ofsSurfaces = LL(md3!!.ofsSurfaces)
            md3!!.ofsEnd = LL(md3!!.ofsEnd)
            if (md3!!.numFrames < 1) {
                Common.common.Warning("InitFromFile: %s has no frames", fileName)
                FileSystem_h.fileSystem.FreeFile(buffer)
                return
            }

            // swap all the frames
//            frame = (md3Frame_s) ((byte[]) md3[md3!!.ofsFrames]);
            md3!!.frames = ArrayList<md3Frame_s>(md3!!.numFrames)
            i = 0
            while (i < md3!!.numFrames) {
                frame = md3Frame_s()
                frame.radius = Lib.LittleFloat(frame.radius)
                j = 0
                while (j < 3) {
                    frame.bounds[0][j] = Lib.LittleFloat(frame.bounds[0][j])
                    frame.bounds[1][j] = Lib.LittleFloat(frame.bounds[1][j])
                    frame.localOrigin[j] = Lib.LittleFloat(frame.localOrigin[j])
                    j++
                }
                md3!!.frames[i] = frame
                i++
            }

            // swap all the tags
//                tag = (md3Tag_s) ((byte[]) md3[md3!!.ofsTags]);
            md3!!.tags = ArrayList<md3Tag_s>(md3!!.numTags * md3!!.numFrames)
            i = 0
            while (i < md3!!.numTags * md3!!.numFrames) {
                tag = md3Tag_s()
                j = 0
                while (j < 3) {
                    tag.origin[j] = Lib.LittleFloat(tag.origin[j])
                    tag.axis[0][j] = Lib.LittleFloat(tag.axis[0][j])
                    tag.axis[1][j] = Lib.LittleFloat(tag.axis[1][j])
                    tag.axis[2][j] = Lib.LittleFloat(tag.axis[2][j])
                    j++
                }
                md3!!.tags[i] = tag
                i++
            }

            // swap all the surfaces
//                surf = (md3Surface_s) ((byte[]) md3[md3!!.ofsSurfaces]);
            md3!!.surfaces = ArrayList<md3Surface_s>(md3!!.numSurfaces)
            i = 0
            while (i < md3!!.numSurfaces) {
                surf = md3Surface_s()
                surf.ident = LL(surf.ident)
                surf.flags = LL(surf.flags)
                surf.numFrames = LL(surf.numFrames)
                surf.numShaders = LL(surf.numShaders)
                surf.numTriangles = LL(surf.numTriangles)
                surf.ofsTriangles = LL(surf.ofsTriangles)
                surf.numVerts = LL(surf.numVerts)
                surf.ofsShaders = LL(surf.ofsShaders)
                surf.ofsSt = LL(surf.ofsSt)
                surf.ofsXyzNormals = LL(surf.ofsXyzNormals)
                surf.ofsEnd = LL(surf.ofsEnd)
                if (surf.numVerts > SHADER_MAX_VERTEXES) {
                    Common.common.Error(
                        "InitFromFile: %s has more than %d verts on a surface (%d)",
                        fileName, SHADER_MAX_VERTEXES, surf.numVerts
                    )
                }
                if (surf.numTriangles * 3 > SHADER_MAX_INDEXES) {
                    Common.common.Error(
                        "InitFromFile: %s has more than %d triangles on a surface (%d)",
                        fileName, SHADER_MAX_INDEXES / 3, surf.numTriangles
                    )
                }

                // change to surface identifier
                surf.ident = 0 //SF_MD3;

                // lowercase the surface name so skin compares are faster
//		int slen = (int)strlen( surf.name );
//		for( j = 0; j < slen; j++ ) {
//			surf.name[j] = tolower( surf.name[j] );
//		}
                surf.name = surf.name.lowercase(Locale.getDefault())

                // strip off a trailing _1 or _2
                // this is a crutch for q3data being a mess
                j = surf.name.length
                if (j > 2 && surf.name[j - 2] == '_') {
                    surf.name = surf.name.substring(0, j - 2)
                }

                // register the shaders
//                    shader = (md3Shader_s) ((byte[]) surf[surf.ofsShaders]);
                surf.shaders = ArrayList<md3Shader_t>(surf.numShaders)
                j = 0
                while (j < surf.numShaders) {
                    shader = md3Shader_t()
                    var sh: idMaterial?
                    sh = DeclManager.declManager.FindMaterial(shader.name)
                    shader.shader = sh
                    surf.shaders[j] = shader
                    j++
                }

                // swap all the triangles
//                    tri = (md3Triangle_t) ((byte[]) surf[surf.ofsTriangles]);
                surf.triangles = ArrayList<md3Triangle_t>(surf.numTriangles)
                j = 0
                while (j < surf.numTriangles) {
                    tri = md3Triangle_t()
                    tri.indexes[0] = LL(tri.indexes[0])
                    tri.indexes[1] = LL(tri.indexes[1])
                    tri.indexes[2] = LL(tri.indexes[2])
                    surf.triangles[j] = tri
                    j++
                }

                // swap all the ST
//                    st = (md3St_t) ((byte[]) surf + surf.ofsSt);
                surf.verts = ArrayList<md3St_t>(surf.numVerts)
                j = 0
                while (j < surf.numVerts) {
                    st = md3St_t()
                    st.st[0] = Lib.LittleFloat(st.st[0])
                    st.st[1] = Lib.LittleFloat(st.st[1])
                    surf.verts[j] = st
                    j++
                }

                // swap all the XyzNormals
//                    xyz = (md3XyzNormal_t) ((byte[]) surf + surf.ofsXyzNormals);
                surf.normals = ArrayList<md3XyzNormal_t>(surf.numVerts * surf.numFrames)
                j = 0
                while (j < surf.numVerts * surf.numFrames) {
                    xyz = md3XyzNormal_t()
                    xyz.xyz[0] = Lib.LittleShort(xyz.xyz[0])
                    xyz.xyz[1] = Lib.LittleShort(xyz.xyz[1])
                    xyz.xyz[2] = Lib.LittleShort(xyz.xyz[2])
                    xyz.normal = Lib.LittleShort(xyz.normal)
                    surf.normals[j] = xyz
                    j++
                }

                // find the next surface
//                    surf = (md3Surface_t) ((byte[]) surf[surf.ofsEnd]);//TODO: make sure the offsets are mapped correctly with the serialization
//                    
                md3!!.surfaces[i] = surf
                i++
            }
            //            }
            FileSystem_h.fileSystem.FreeFile(buffer)
        }

        override fun IsDynamicModel(): dynamicModel_t {
            return dynamicModel_t.DM_CACHED
        }

        override fun InstantiateDynamicModel(
            ent: renderEntity_s,
            view: viewDef_s?,
            cachedModel: idRenderModel?
        ): idRenderModel? {
            var cachedModel = cachedModel
            var i: Int
            var j: Int
            val backlerp: Float
            //            md3Triangle_t triangle;
//            float[] texCoords;
            var indexes: Int
            var numVerts: Int
            var surface: md3Surface_s
            val frame: Int
            val oldframe: Int
            val staticModel: idRenderModelStatic
            if (cachedModel != null) {
//		delete cachedModel;
                cachedModel = null
            }
            staticModel = idRenderModelStatic()
            staticModel.bounds.Clear()

//            surface = (md3Surface_t) ((byte[]) md3[ md3!!.ofsSurfaces]);
            surface = md3!!.surfaces[0]

            // TODO: these need set by an entity
            frame =
                ent.shaderParms[RenderWorld.SHADERPARM_MD3_FRAME].toInt() // probably want to keep frames < 1000 or so
            oldframe = ent.shaderParms[RenderWorld.SHADERPARM_MD3_LASTFRAME].toInt()
            backlerp = ent.shaderParms[RenderWorld.SHADERPARM_MD3_BACKLERP]
            i = 0
            while (i < md3!!.numSurfaces /*i++*/) {
                val tri = tr_trisurf.R_AllocStaticTriSurf()
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, surface.numVerts)
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, surface.numTriangles * 3)
                tri.bounds.Clear()
                val surf = modelSurface_s()
                surf.geometry = tri

//                md3Shader_t shaders = (md3Shader_t) ((byte[]) surface[surface.ofsShaders]);
                val shaders = surface.shaders[0]
                surf.shader = shaders.shader
                LerpMeshVertexes(tri, surface, backlerp, frame, oldframe)
                indexes = surface.numTriangles * 3
                j = 0
                for (triangle in surface.triangles) {
//                triangles = (int[]) ((byte[]) surface + surface.ofsTriangles);
                    while ( /*j = 0*/j < indexes) {
                        tri.indexes[j] = triangle.indexes[j]
                        j++
                    }
                    tri.numIndexes += indexes
                }
                numVerts = surface.numVerts
                j = 0
                for (texCoords in surface.verts) {
//                texCoords = (float[]) ((byte[]) surface + surface.ofsSt);
                    while ( /*j = 0*/j < numVerts) {
                        val stri = tri.verts[j]
                        stri.st[0] = texCoords.st[j * 2 + 0]
                        stri.st[1] = texCoords.st[j * 2 + 1]
                        j++
                    }
                }
                tr_trisurf.R_BoundTriSurf(tri)
                staticModel.AddSurface(surf)
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[0])
                staticModel.bounds.AddPoint(surf.geometry!!.bounds[1])

                // find the next surface
                surface = md3!!.surfaces[++i]
            }
            return staticModel
        }

        override fun Bounds(ent: renderEntity_s?): idBounds {
            val ret = idBounds()
            ret.Clear()
            if (null == ent || null == md3) {
                // just give it the editor bounds
                ret.AddPoint(idVec3(-10, -10, -10))
                ret.AddPoint(idVec3(10, 10, 10))
                return ret
            }

//            md3Frame_s frame = (md3Frame_t) ((byte[]) md3 + md3!!.ofsFrames);
            val frame = md3!!.frames[0]
            ret.AddPoint(frame.bounds[0])
            ret.AddPoint(frame.bounds[1])
            return ret
        }

        private fun LerpMeshVertexes(
            tri: srfTriangles_s, surf: md3Surface_s, backlerp: Float,  /*final*/
            frame: Int,  /*final*/
            oldframe: Int
        ) {
            var frame = frame
            var oldframe = oldframe
            var oldXyz: md3XyzNormal_t
            var newXyz: md3XyzNormal_t
            val oldXyzScale: Float
            val newXyzScale: Float
            var vertNum: Int
            val numVerts: Int

//            newXyz = (short[]) ((byte[]) surf + surf.ofsXyzNormals) + (frame * surf.numVerts * 4);
            newXyz = surf.normals[frame]
            newXyzScale = (MD3_XYZ_SCALE * (1.0 - backlerp)).toFloat()
            numVerts = surf.numVerts
            if (backlerp == 0f) {
                //
                // just copy the vertexes
                //
                vertNum = 0
                while (vertNum < numVerts) {
                    val outvert = tri.verts[tri.numVerts]
                    outvert.xyz.x = newXyz.xyz[0] * newXyzScale
                    outvert.xyz.y = newXyz.xyz[1] * newXyzScale
                    outvert.xyz.z = newXyz.xyz[2] * newXyzScale
                    tri.numVerts++
                    newXyz = surf.normals[++frame]
                    vertNum++
                }
            } else {
                //
                // interpolate and copy the vertexes
                //
//                oldXyz = (short[]) ((byte[]) surf + surf.ofsXyzNormals) + (oldframe * surf.numVerts * 4);
                oldXyz = surf.normals[oldframe]
                oldXyzScale = (MD3_XYZ_SCALE * backlerp).toFloat()
                vertNum = 0
                while (vertNum < numVerts) {
                    val outvert = tri.verts[tri.numVerts]

                    // interpolate the xyz
                    outvert.xyz.x = oldXyz.xyz[0] * oldXyzScale + newXyz.xyz[0] * newXyzScale
                    outvert.xyz.y = oldXyz.xyz[1] * oldXyzScale + newXyz.xyz[1] * newXyzScale
                    outvert.xyz.z = oldXyz.xyz[2] * oldXyzScale + newXyz.xyz[2] * newXyzScale
                    tri.numVerts++
                    vertNum++
                    oldXyz = surf.normals[++oldframe]
                    newXyz = surf.normals[++frame]
                }
            }
        }
    }
}