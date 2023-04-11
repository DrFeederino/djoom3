package neo.Renderer

import neo.TempDump
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.Text.Str.idStr
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 *
 */
object Model_ase {
    lateinit var ase: ase_t

    /*
     =================
     ASE_Load
     =================
     */
    fun ASE_Load(fileName: String): aseModel_s? {
        val buf = arrayOfNulls<ByteBuffer>(1)
        val timeStamp = LongArray(1)
        val ase: aseModel_s?
        FileSystem_h.fileSystem.ReadFile(fileName, buf, timeStamp)
        if (buf[0] == null) {
            return null
        }
        ase = ASE_Parse(buf[0]!!, false)
        ase.timeStamp[0] = timeStamp[0]
        FileSystem_h.fileSystem.FreeFile(buf)
        return ase
    }

    /*
     =================
     ASE_Free
     =================
     */
    fun ASE_Free(ase: aseModel_s?) {
        var i: Int
        var j: Int
        var obj: aseObject_t?
        var mesh: aseMesh_t?
        var material: aseMaterial_t
        if (null == ase) {
            return
        }
        i = 0
        while (i < ase.objects.size) {
            obj = ase.objects[i]
            j = 0
            while (j < obj.frames.size) {
                mesh = obj.frames[j]
                if (mesh.vertexes.isNotEmpty()) {
//                    Mem_Free(mesh.vertexes);
                    mesh.vertexes.clear()
                }
                if (mesh.tvertexes.isNotEmpty()) {
//                    Mem_Free(mesh.tvertexes);
                    mesh.tvertexes.clear()
                }
                if (mesh.cvertexes.isNotEmpty()) {
//                    Mem_Free(mesh.cvertexes);
                    mesh.cvertexes.clear()
                }
                if (mesh.faces.isNotEmpty()) {
//                    Mem_Free(mesh.faces);
                    mesh.faces.clear()
                }
                //                Mem_Free(mesh);
                mesh = null
                j++
            }
            obj.frames.clear()

            // free the base nesh
            mesh = obj.mesh
            if (mesh.vertexes.isNotEmpty()) {
//                Mem_Free(mesh.vertexes);
                mesh.vertexes.clear()
            }
            if (mesh.tvertexes.isNotEmpty()) {
//                Mem_Free(mesh.tvertexes);
                mesh.tvertexes.clear()
            }
            if (mesh.cvertexes.isNotEmpty()) {
//                Mem_Free(mesh.cvertexes);
                mesh.cvertexes.clear()
            }
            if (mesh.faces.isNotEmpty()) {
//                Mem_Free(mesh.faces);
                mesh.faces.clear()
            }
            //            Mem_Free(obj);
            obj = null
            i++
        }
        ase.objects.clear()
        i = 0
        while (i < ase.materials.size) {

//            material = ase.materials.oGet(i);
//            Mem_Free(material);
            ase.materials.removeAt(i)
            i++
        }
        ase.materials.clear()

//	delete ase;
    }

    /*
     ======================================================================

     Parses 3D Studio Max ASCII export files.
     The goal is to parse the information into memory exactly as it is
     represented in the file.  Users of the data will then move it
     into a form that is more convenient for them.

     ======================================================================
     */
    fun VERBOSE(fmt: String, vararg x: Any?) {
        if (ase.verbose) {
            Common.common.Printf(fmt, x)
        }
    }

    fun ASE_GetCurrentMesh(): aseMesh_t {
        return ase.currentMesh
    }

    fun CharIsTokenDelimiter(ch: Int): Boolean {
        return ch <= 32
    }

    fun ASE_GetToken(restOfLine: Boolean): Boolean {
        var i = 0
        ase.token = ""
        if (ase.buffer == null) {
            return false
        }
        if (ase.curpos == ase.len) {
            return false
        }

        // skip over crap
        while (ase.curpos < ase.len && ase.buffer!![ase.curpos].code <= 32) {
            ase.curpos++
        }
        while (ase.curpos < ase.len) {
            ase.token += ase.buffer!![ase.curpos] //ase.token[i] = *ase.curpos;
            ase.curpos++
            i++
            val c: Char = ase.token[i - 1]
            if (CharIsTokenDelimiter(c.code) && !restOfLine || c == '\n' || c == '\r') {
                ase.token = ase.token.substring(0, i - 1)
                break
            }
        }

//        ase.token = replaceByIndex((char) 0, i, ase.token);
        return true
    }

    /**
     *
     */
    fun ASE_ParseBracedBlock(parser: ASE?) {
        var indent = 0
        while (ASE_GetToken(false)) {
            if ("{" == ase.token) {
                indent++
            } else if ("}" == ase.token) {
                --indent
                if (indent == 0) {
                    break
                } else if (indent < 0) {
                    Common.common.Error("Unexpected '}'")
                }
            } else {
                parser?.run(ase.token)
            }
        }
    }

    fun ASE_SkipEnclosingBraces() {
        var indent = 0
        while (ASE_GetToken(false)) {
            if ("{" == ase.token) {
                indent++
            } else if ("}" == ase.token) {
                indent--
                if (indent == 0) {
                    break
                } else if (indent < 0) {
                    Common.common.Error("Unexpected '}'")
                }
            }
        }
    }

    fun ASE_SkipRestOfLine() {
        ASE_GetToken(true)
    }

    fun ASE_ParseGeomObject() {
        val `object`: aseObject_t
        VERBOSE("GEOMOBJECT")

//        object = (aseObject_t *) Mem_Alloc(sizeof(aseObject_t));
//        memset(object, 0, sizeof(aseObject_t));
        `object` = aseObject_t()
        ase.model.objects.add(`object`)
        ase.currentObject = `object`
        `object`.frames.ensureCapacity(32)
        ASE_ParseBracedBlock(ASE_KeyGEOMOBJECT.getInstance())
    }

    /*
     =================
     ASE_Parse
     =================
     */
    fun ASE_Parse(buffer: ByteBuffer, verbose: Boolean): aseModel_s {
        ase = ase_t() //memset( &ase, 0, sizeof( ase ) );
        ase.verbose = verbose
        ase.buffer = TempDump.bbtocb(buffer) //.asCharBuffer();
        ase.len = ase.buffer!!.length //TODO:capacity?
        ase.curpos = 0 //ase.buffer;
        ase.currentObject = null

        // NOTE: using new operator because aseModel_t contains idList class objects
        ase.model = aseModel_s() //memset(ase.model, 0, sizeof(aseModel_t));
        ase.model.objects.ensureCapacity(32)
        ase.model.materials.ensureCapacity(32)
        while (ASE_GetToken(false)) {
            when (ase.token) {
                "*3DSMAX_ASCIIEXPORT", "*COMMENT" -> ASE_SkipRestOfLine()
                "*SCENE" -> ASE_SkipEnclosingBraces()
                "*GROUP" -> {
                    ASE_GetToken(false) // group name
                    ASE_ParseBracedBlock(ASE_KeyGROUP.getInstance())
                }
                "*SHAPEOBJECT" -> ASE_SkipEnclosingBraces()
                "*CAMERAOBJECT" -> ASE_SkipEnclosingBraces()
                "*MATERIAL_LIST" -> {
                    VERBOSE("MATERIAL_LIST\n")
                    ASE_ParseBracedBlock(ASE_KeyMATERIAL_LIST.getInstance())
                }
                "*GEOMOBJECT" -> ASE_ParseGeomObject()
                else -> if (TempDump.isNotNullOrEmpty(ase.token)) {
                    Common.common.Printf("Unknown token '%s'\n", ase.token)
                }
            }
        }
        return ase.model
    }

    fun atof(str: String): Float {
        return try {
            str.toFloat()
        } catch (exc: NumberFormatException) {
            0.0f
        }
    }

    /*
     ===============================================================================

     ASE loader. (3D Studio Max ASCII Export)

     ===============================================================================
     */
    class aseFace_t {
        var tVertexNum: IntArray = IntArray(3)
        var vertexColors: Array<ByteArray> = Array(3) { ByteArray(4) }
        val faceNormal: idVec3 = idVec3()
        var vertexNum: IntArray = IntArray(3)
        val vertexNormals: Array<idVec3> = idVec3.Companion.generateArray(3)
    }

    class aseMesh_t {
        //
        val transform: Array<idVec3> = idVec3.Companion.generateArray(4) // applied to normals
        private val DBG_count = DBG_counter++

        //
        var colorsParsed = false
        var cvertexes: ArrayList<idVec3> = ArrayList()
        var faces: ArrayList<aseFace_t> = ArrayList()
        var normalsParsed = false
        var numCVFaces = 0
        var numCVertexes = 0
        var numFaces = 0
        var numTVFaces = 0
        var numTVertexes = 0
        var numVertexes = 0
        var timeValue = 0
        var tvertexes: ArrayList<idVec2> = ArrayList()
        var vertexes: ArrayList<idVec3> = ArrayList()

        companion object {
            private var DBG_counter = 1
        }
    }

    class aseMaterial_t {
        val name: CharArray = CharArray(128)
        var angle // in clockwise radians
                = 0f

        //        String name;
        var uOffset = 0f
        var vOffset // max lets you offset by material without changing texCoords
                = 0f
        var uTiling = 0f
        var vTiling // multiply tex coords by this
                = 0f
    }

    class aseObject_t {
        val frames: ArrayList<aseMesh_t>
        var materialRef = 0

        //
        var mesh: aseMesh_t

        //	char					name[128];
        var name: CharArray

        init {
            name = CharArray(128)
            mesh = aseMesh_t()
            frames = ArrayList()
        }
    }

    class aseModel_s {
        //	ID_TIME_T					timeStamp;
        val timeStamp: LongArray = longArrayOf(1)
        val materials: ArrayList<aseMaterial_t>
        val objects: ArrayList<aseObject_t>

        init {
            materials = ArrayList()
            objects = ArrayList()
        }
    }

    // working variables used during parsing
    class ase_t {
        var buffer: CharBuffer? = null
        var curpos = 0
        var currentFace = 0
        lateinit var currentMaterial: aseMaterial_t
        lateinit var currentMesh: aseMesh_t
        var currentObject: aseObject_t? = null
        var currentVertex = 0
        var len = 0

        //
        lateinit var model: aseModel_s

        //        final char[] token = new char[1024];
        var token: String = ""

        //
        var verbose = false
    }

    abstract class ASE {
        abstract fun run(token: String)
    }

    class ASE_KeyMAP_DIFFUSE private constructor() : ASE() {
        override fun run(token: String) {
            val material: aseMaterial_t?
            when ("" + token) {
                "*BITMAP" -> {
                    val qpath: idStr
                    val matname: idStr
                    ASE_GetToken(false)
                    // remove the quotes
                    val s = ase.token.substring(1).indexOf('\"')
                    if (s > 0) {
                        ase.token = ase.token.substring(0, s + 1)
                    }
                    matname = idStr(ase.token.substring(1))
                    // convert the 3DSMax material pathname to a qpath
                    matname.BackSlashesToSlashes()
                    qpath = idStr(FileSystem_h.fileSystem.OSPathToRelativePath(matname.toString()))
                    idStr.Companion.Copynz(
                        ase.currentMaterial.name,
                        qpath.toString(),
                        ase.currentMaterial.name.size
                    )
                }
                "*UVW_U_OFFSET" -> {
                    material = ase.model.materials[ase.model.materials.size - 1]
                    ASE_GetToken(false)
                    material.uOffset = ase.token.toFloat()
                }
                "*UVW_V_OFFSET" -> {
                    material = ase.model.materials[ase.model.materials.size - 1]
                    ASE_GetToken(false)
                    material.vOffset = ase.token.toFloat()
                }
                "*UVW_U_TILING" -> {
                    material = ase.model.materials[ase.model.materials.size - 1]
                    ASE_GetToken(false)
                    material.uTiling = ase.token.toFloat()
                }
                "*UVW_V_TILING" -> {
                    material = ase.model.materials[ase.model.materials.size - 1]
                    ASE_GetToken(false)
                    material.vTiling = ase.token.toFloat()
                }
                "*UVW_ANGLE" -> {
                    material = ase.model.materials[ase.model.materials.size - 1]
                    ASE_GetToken(false)
                    material.angle = ase.token.toFloat()
                }
                else -> {}
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMAP_DIFFUSE()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMATERIAL private constructor() : ASE() {
        override fun run(token: String) {
            run {
                if ("*MAP_DIFFUSE" == token) {
                    ASE_ParseBracedBlock(ASE_KeyMAP_DIFFUSE.getInstance())
                } else {
                }
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMATERIAL()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMATERIAL_LIST private constructor() : ASE() {
        override fun run(token: String) {
            if ("*MATERIAL_COUNT" == token) {
                ASE_GetToken(false)
                VERBOSE("..num materials: %s\n", ase.token)
            } else if ("*MATERIAL" == token) {
                VERBOSE("..material %d\n", ase.model.materials.size)

//                ase.currentMaterial = (aseMaterial_t) Mem_Alloc(sizeof(aseMaterial_t));
//                memset(ase.currentMaterial, 0, sizeof(aseMaterial_t));
                ase.currentMaterial = aseMaterial_t()
                ase.currentMaterial.uTiling = 1f
                ase.currentMaterial.vTiling = 1f
                ase.model.materials.add(ase.currentMaterial)
                ASE_ParseBracedBlock(ASE_KeyMATERIAL.getInstance())
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMATERIAL_LIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyNODE_TM private constructor() : ASE() {
        override fun run(token: String) {
            var i: Int
            val j: Int
            j = when ("" + token) {
                "*TM_ROW0" -> 0
                "*TM_ROW1" -> 1
                "*TM_ROW2" -> 2
                "*TM_ROW3" -> 3
                else -> -1
            }
            i = 0
            while (i < 3 && j != -1) {
                ASE_GetToken(false)
                ase.currentObject!!.mesh.transform[j][i] = ase.token.toFloat()
                i++
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyNODE_TM()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_VERTEX_LIST private constructor() : ASE() {
        override fun run(token: String) {
            run {
                val pMesh: aseMesh_t = ASE_GetCurrentMesh()
                if ("*MESH_VERTEX" == token) {
                    ASE_GetToken(false) // skip number
                    //pMesh.vertexes[ase.currentVertex] = new idVec3();
                    ASE_GetToken(false)
                    pMesh.vertexes[ase.currentVertex].x = ase.token.toFloat()
                    ASE_GetToken(false)
                    pMesh.vertexes[ase.currentVertex].y = ase.token.toFloat()
                    ASE_GetToken(false)
                    pMesh.vertexes[ase.currentVertex].z = ase.token.toFloat()
                    ase.currentVertex++
                    if (ase.currentVertex > pMesh.numVertexes) {
                        Common.common.Error("ase.currentVertex >= pMesh.numVertexes")
                    }
                } else {
                    Common.common.Error("Unknown token '%s' while parsing MESH_VERTEX_LIST", token)
                }
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_VERTEX_LIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_FACE_LIST private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            if ("*MESH_FACE" == token) {
                ASE_GetToken(false) // skip face number
                pMesh.faces.add(ase.currentFace, aseFace_t())

                // we are flipping the order here to change the front/back facing
                // from 3DS to our standard (clockwise facing out)
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // first vertex
                pMesh.faces[ase.currentFace].vertexNum[0] = ase.token.toInt()
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // second vertex
                pMesh.faces[ase.currentFace].vertexNum[2] = ase.token.toInt()
                ASE_GetToken(false) // skip label
                ASE_GetToken(false) // third vertex
                pMesh.faces[ase.currentFace].vertexNum[1] = ase.token.toInt()
                ASE_GetToken(true)

                // we could parse material id and smoothing groups here
/*
                 if ( ( p = strstr( ase.token, "*MESH_MTLID" ) ) != 0 )
                 {
                 p += strlen( "*MESH_MTLID" ) + 1;
                 mtlID = Integer.parseInt( p );
                 }
                 else
                 {
                 common.Error( "No *MESH_MTLID found for face!" );
                 }
                 */ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_FACE_LIST", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_FACE_LIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyTFACE_LIST private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            if ("*MESH_TFACE" == token) {
                val a: Int
                val b: Int
                val c: Int
                ASE_GetToken(false)
                ASE_GetToken(false)
                a = ase.token.toInt()
                ASE_GetToken(false)
                c = ase.token.toInt()
                ASE_GetToken(false)
                b = ase.token.toInt()
                pMesh.faces[ase.currentFace].tVertexNum[0] = a
                pMesh.faces[ase.currentFace].tVertexNum[1] = b
                pMesh.faces[ase.currentFace].tVertexNum[2] = c
                ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_TFACE", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyTFACE_LIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyCFACE_LIST private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            if ("*MESH_CFACE" == token) {
                ASE_GetToken(false)
                for (i in 0..2) {
                    ASE_GetToken(false)
                    val a = ase.token.toInt()

                    // we flip the vertex order to change the face direction to our style
                    pMesh.faces[ase.currentFace].vertexColors[remap.get(i)][0] =
                        (pMesh.cvertexes[a][0] * 255).toInt().toByte()
                    pMesh.faces[ase.currentFace].vertexColors[remap.get(i)][1] =
                        (pMesh.cvertexes[a][1] * 255).toInt().toByte()
                    pMesh.faces[ase.currentFace].vertexColors[remap.get(i)][2] =
                        (pMesh.cvertexes[a][2] * 255).toInt().toByte()
                }
                ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_CFACE", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyCFACE_LIST()
            private val remap /*[3]*/: IntArray = intArrayOf(0, 2, 1)
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_TVERTLIST private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            if ("*MESH_TVERT" == token) {
//		char u[80], v[80], w[80];
                val u: String?
                val v: String?
                val w: String?
                ASE_GetToken(false)
                pMesh.tvertexes.add(ase.currentVertex, idVec2())
                ASE_GetToken(false)
                //		strcpy( u, ase.token );
                u = ase.token
                ASE_GetToken(false)
                //		strcpy( v, ase.token );
                v = ase.token
                ASE_GetToken(false)
                //		strcpy( w, ase.token );
                w = ase.token
                pMesh.tvertexes[ase.currentVertex].x = u.toFloat()
                // our OpenGL second texture axis is inverted from MAX's sense
                pMesh.tvertexes[ase.currentVertex].y = 1.0f - v.toFloat()
                ase.currentVertex++
                if (ase.currentVertex > pMesh.numTVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numTVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_TVERTLIST", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_TVERTLIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_CVERTLIST private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            pMesh.colorsParsed = true
            if ("*MESH_VERTCOL" == token) {
                ASE_GetToken(false)
                ASE_GetToken(false)
                // atof can return 0.0 if it can't convert. Not really the case if java land
                if (pMesh.cvertexes.isEmpty()) {
                    pMesh.cvertexes.addAll(idVec3.Companion.generateArray(pMesh.numCVertexes))
                }
                //pMesh.cvertexes[ase.currentVertex] = new idVec3();
                pMesh.cvertexes[ase.currentVertex][0] = atof(token)
                ASE_GetToken(false)
                pMesh.cvertexes[ase.currentVertex][1] = atof(token)
                ASE_GetToken(false)
                pMesh.cvertexes[ase.currentVertex][2] = atof(token)
                ase.currentVertex++
                if (ase.currentVertex > pMesh.numCVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numCVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_CVERTLIST", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_CVERTLIST()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_NORMALS private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            val f: aseFace_t?
            val n = idVec3()
            pMesh.normalsParsed = true
            if ("*MESH_FACENORMAL" == token) {
                val num: Int
                ASE_GetToken(false)
                num = ase.token.toInt()
                if (num >= pMesh.numFaces || num < 0) {
                    Common.common.Error("MESH_NORMALS face index out of range: %d", num)
                }
                f = pMesh.faces[ase.currentFace]
                if (num != ase.currentFace) {
                    Common.common.Error("MESH_NORMALS face index != currentFace")
                }
                ASE_GetToken(false)
                n[0] = ase.token.toFloat()
                ASE_GetToken(false)
                n[1] = ase.token.toFloat()
                ASE_GetToken(false)
                n[2] = ase.token.toFloat()
                f.faceNormal[0] =
                    n[0] * pMesh.transform[0][0] + n[1] * pMesh.transform[1][0] + n[2] * pMesh.transform[2][0]
                f.faceNormal[1] =
                    n[0] * pMesh.transform[0][1] + n[1] * pMesh.transform[1][1] + n[2] * pMesh.transform[2][1]
                f.faceNormal[2] =
                    n[0] * pMesh.transform[0][2] + n[1] * pMesh.transform[1][2] + n[2] * pMesh.transform[2][2]
                f.faceNormal.Normalize()
                ase.currentFace++
            } else if ("*MESH_VERTEXNORMAL" == token) {
                val num: Int
                var v: Int
                ASE_GetToken(false)
                num = ase.token.toInt()
                if (num >= pMesh.numVertexes || num < 0) {
                    Common.common.Error("MESH_NORMALS vertex index out of range: %d", num)
                }
                f = pMesh.faces[ase.currentFace - 1]
                v = 0
                while (v < 3) {
                    if (num == f.vertexNum[v]) {
                        break
                    }
                    v++
                }
                if (v == 3) {
                    Common.common.Error("MESH_NORMALS vertex index doesn't match face")
                }
                ASE_GetToken(false)
                n[0] = ase.token.toFloat()
                ASE_GetToken(false)
                n[1] = ase.token.toFloat()
                ASE_GetToken(false)
                n[2] = ase.token.toFloat()
                f.vertexNormals[v][0] =
                    n[0] * pMesh.transform[0][0] + n[1] * pMesh.transform[1][0] + n[2] * pMesh.transform[2][0]
                f.vertexNormals[v][0] =
                    n[0] * pMesh.transform[0][1] + n[1] * pMesh.transform[1][1] + n[2] * pMesh.transform[2][2]
                f.vertexNormals[v][0] =
                    n[0] * pMesh.transform[0][2] + n[1] * pMesh.transform[1][2] + n[2] * pMesh.transform[2][1]
                f.vertexNormals[v].Normalize()
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_NORMALS()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH private constructor() : ASE() {
        override fun run(token: String) {
            val pMesh: aseMesh_t = ASE_GetCurrentMesh()
            if (token.isNotEmpty()) {
                when (token) {
                    "*TIMEVALUE" -> {
                        ASE_GetToken(false)
                        pMesh.timeValue = ase.token.toInt()
                        VERBOSE(".....timevalue: %d\n", pMesh.timeValue)
                    }
                    "*MESH_NUMVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh.numVertexes = ase.token.toInt()
                        VERBOSE(".....num vertexes: %d\n", pMesh.numVertexes)
                    }
                    "*MESH_NUMTVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh.numTVertexes = ase.token.toInt()
                        VERBOSE(".....num tvertexes: %d\n", pMesh.numTVertexes)
                    }
                    "*MESH_NUMCVERTEX" -> {
                        ASE_GetToken(false)
                        pMesh.numCVertexes = ase.token.toInt()
                        VERBOSE(".....num cvertexes: %d\n", pMesh.numCVertexes)
                    }
                    "*MESH_NUMFACES" -> {
                        ASE_GetToken(false)
                        pMesh.numFaces = ase.token.toInt()
                        VERBOSE(".....num faces: %d\n", pMesh.numFaces)
                    }
                    "*MESH_NUMTVFACES" -> {
                        ASE_GetToken(false)
                        pMesh.numTVFaces = ase.token.toInt()
                        VERBOSE(".....num tvfaces: %d\n", pMesh.numTVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMTVFACES != MESH_NUMFACES")
                        }
                    }
                    "*MESH_NUMCVFACES" -> {
                        ASE_GetToken(false)
                        pMesh.numCVFaces = ase.token.toInt()
                        VERBOSE(".....num cvfaces: %d\n", pMesh.numCVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMCVFACES != MESH_NUMFACES")
                        }
                    }
                    "*MESH_VERTEX_LIST" -> {
                        pMesh.vertexes = ArrayList(
                            arrayListOf<idVec3>(
                                *
                                idVec3.Companion.generateArray(pMesh.numVertexes)
                            )
                        ) // Mem_Alloc(pMesh.numVertexes);
                        ase.currentVertex = 0
                        VERBOSE(".....parsing MESH_VERTEX_LIST\n")
                        ASE_ParseBracedBlock(ASE_KeyMESH_VERTEX_LIST.getInstance())
                    }
                    "*MESH_TVERTLIST" -> {
                        ase.currentVertex = 0
                        pMesh.tvertexes = ArrayList<idVec2>(pMesh.numTVertexes) // Mem_Alloc(pMesh.numTVertexes);
                        VERBOSE(".....parsing MESH_TVERTLIST\n")
                        ASE_ParseBracedBlock(ASE_KeyMESH_TVERTLIST.getInstance())
                    }
                    "*MESH_CVERTLIST" -> {
                        ase.currentVertex = 0
                        pMesh.cvertexes = ArrayList(
                            arrayListOf(
                                *
                                idVec3.Companion.generateArray(pMesh.numCVertexes)
                            )
                        ) // Mem_Alloc(pMesh.numCVertexes);
                        VERBOSE(".....parsing MESH_CVERTLIST\n")
                        ASE_ParseBracedBlock(ASE_KeyMESH_CVERTLIST.getInstance())
                    }
                    "*MESH_FACE_LIST" -> {
                        pMesh.faces = ArrayList<aseFace_t>(pMesh.numFaces) // Mem_Alloc(pMesh.numFaces);
                        ase.currentFace = 0
                        VERBOSE(".....parsing MESH_FACE_LIST\n")
                        ASE_ParseBracedBlock(ASE_KeyMESH_FACE_LIST.getInstance())
                    }
                    "*MESH_TFACELIST" -> {
                        if (pMesh.faces.isEmpty()) {
                            Common.common.Error("*MESH_TFACELIST before *MESH_FACE_LIST")
                        }
                        ase.currentFace = 0
                        VERBOSE(".....parsing MESH_TFACE_LIST\n")
                        ASE_ParseBracedBlock(ASE_KeyTFACE_LIST.getInstance())
                    }
                    "*MESH_CFACELIST" -> {
                        if (pMesh.faces.isEmpty()) { //TODO:check pointer position instead of entire array
                            Common.common.Error("*MESH_CFACELIST before *MESH_FACE_LIST")
                        }
                        ase.currentFace = 0
                        VERBOSE(".....parsing MESH_CFACE_LIST\n")
                        ASE_ParseBracedBlock(ASE_KeyCFACE_LIST.getInstance())
                    }
                    "*MESH_NORMALS" -> {
                        if (pMesh.faces.isEmpty()) {
                            Common.common.Warning("*MESH_NORMALS before *MESH_FACE_LIST")
                        }
                        ase.currentFace = 0
                        VERBOSE(".....parsing MESH_NORMALS\n")
                        ASE_ParseBracedBlock(ASE_KeyMESH_NORMALS.getInstance())
                    }
                }
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyMESH_ANIMATION private constructor() : ASE() {
        override fun run(token: String) {
            val mesh: aseMesh_t

            // loads a single animation frame
            if ("*MESH" == token) {
                VERBOSE("...found MESH\n")

//                mesh = (aseMesh_t) Mem_Alloc(sizeof(aseMesh_t));
//                memset(mesh, 0, sizeof(aseMesh_t));
                mesh = aseMesh_t()
                ase.currentMesh = mesh
                ase.currentObject!!.frames.add(mesh)
                ASE_ParseBracedBlock(ASE_KeyMESH.getInstance())
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_ANIMATION", token)
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyMESH_ANIMATION()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyGEOMOBJECT private constructor() : ASE() {
        override fun run(token: String) {
            val `object`: aseObject_t?
            `object` = ase.currentObject
            when ("" + token) {
                "*NODE_NAME" -> {
                    ASE_GetToken(true)
                    VERBOSE(" %s\n", ase.token)
                    idStr.Companion.Copynz(`object`!!.name, ase.token, `object`.name.size)
                }
                "*NODE_PARENT" -> ASE_SkipRestOfLine()
                "*NODE_TM", "*TM_ANIMATION" -> ASE_ParseBracedBlock(ASE_KeyNODE_TM.getInstance())
                "*MESH" -> {
                    val transform: Array<idVec3> =
                        idVec3.Companion.copyVec(ase.currentObject!!.mesh.transform) //copied from the bfg sources
                    run {
                        ase.currentObject!!.mesh = aseMesh_t()
                        ase.currentMesh = ase.currentObject!!.mesh
                    }
                    var i = 0
                    while (i < transform.size) {
                        ase.currentMesh.transform[i].set(transform[i])
                        i++
                    }
                    ASE_ParseBracedBlock(ASE_KeyMESH.getInstance())
                }
                "*MATERIAL_REF" -> {
                    ASE_GetToken(false)
                    `object`!!.materialRef = ase.token.toInt()
                }
                "*MESH_ANIMATION" -> {
                    VERBOSE("..found MESH_ANIMATION\n")
                    ASE_ParseBracedBlock(ASE_KeyMESH_ANIMATION.getInstance())
                }
                "*PROP_MOTIONBLUR", "*PROP_CASTSHADOW", "*PROP_RECVSHADOW" -> ASE_SkipRestOfLine()
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyGEOMOBJECT()
            fun getInstance(): ASE {
                return instance
            }
        }
    }

    class ASE_KeyGROUP private constructor() : ASE() {
        override fun run(token: String) {
            if ("*GEOMOBJECT" == token) {
                ASE_ParseGeomObject()
            }
        }

        companion object {
            private val instance: ASE = ASE_KeyGROUP()
            fun getInstance(): ASE {
                return instance
            }
        }
    }
}