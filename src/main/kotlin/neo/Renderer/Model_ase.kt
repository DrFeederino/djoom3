package neo.Renderer

import neo.TempDump
import neo.TempDump.CPP_class.Char
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import java.nio.*

/**
 *
 */
object Model_ase {
    var ase: ase_t? = null

    /*
     =================
     ASE_Load
     =================
     */
    fun ASE_Load(fileName: String?): aseModel_s? {
        val buf = arrayOf<ByteBuffer?>(null)
        val timeStamp = LongArray(1)
        val ase: aseModel_s?
        FileSystem_h.fileSystem.ReadFile(fileName, buf, timeStamp)
        if (null == buf) {
            return null
        }
        ase = Model_ase.ASE_Parse(buf[0], false)
        ase.timeStamp.get(0) = timeStamp[0]
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
        while (i < ase.objects.Num()) {
            obj = ase.objects.get(i)
            j = 0
            while (j < obj.frames.Num()) {
                mesh = obj.frames.get(j)
                if (mesh.vertexes != null) {
//                    Mem_Free(mesh.vertexes);
                    mesh.vertexes = null
                }
                if (mesh.tvertexes != null) {
//                    Mem_Free(mesh.tvertexes);
                    mesh.tvertexes = null
                }
                if (mesh.cvertexes != null) {
//                    Mem_Free(mesh.cvertexes);
                    mesh.cvertexes = null
                }
                if (mesh.faces != null) {
//                    Mem_Free(mesh.faces);
                    mesh.faces = null
                }
                //                Mem_Free(mesh);
                mesh = null
                j++
            }
            obj.frames.Clear()

            // free the base nesh
            mesh = obj.mesh
            if (mesh.vertexes != null) {
//                Mem_Free(mesh.vertexes);
                mesh.vertexes = null
            }
            if (mesh.tvertexes != null) {
//                Mem_Free(mesh.tvertexes);
                mesh.tvertexes = null
            }
            if (mesh.cvertexes != null) {
//                Mem_Free(mesh.cvertexes);
                mesh.cvertexes = null
            }
            if (mesh.faces != null) {
//                Mem_Free(mesh.faces);
                mesh.faces = null
            }
            //            Mem_Free(obj);
            obj = null
            i++
        }
        ase.objects.Clear()
        i = 0
        while (i < ase.materials.Num()) {

//            material = ase.materials.oGet(i);
//            Mem_Free(material);
            ase.materials.set(i, null)
            i++
        }
        ase.materials.Clear()

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
    fun VERBOSE(fmt: String?, vararg x: Any?) {
        if (Model_ase.ase.verbose) {
            Common.common.Printf(fmt, *x)
        }
    }

    fun ASE_GetCurrentMesh(): aseMesh_t? {
        return Model_ase.ase.currentMesh
    }

    fun CharIsTokenDelimiter(ch: Int): Boolean {
        return ch <= 32
    }

    fun ASE_GetToken(restOfLine: Boolean): Boolean {
        var i = 0
        Model_ase.ase.token = ""
        if (Model_ase.ase.buffer == null) {
            return false
        }
        if (Model_ase.ase.curpos == Model_ase.ase.len) {
            return false
        }

        // skip over crap
        while (Model_ase.ase.curpos < Model_ase.ase.len && Model_ase.ase.buffer[Model_ase.ase.curpos].code <= 32) {
            Model_ase.ase.curpos++
        }
        while (Model_ase.ase.curpos < Model_ase.ase.len) {
            Model_ase.ase.token += Model_ase.ase.buffer[Model_ase.ase.curpos] //ase.token[i] = *ase.curpos;
            Model_ase.ase.curpos++
            i++
            val c: Char = Model_ase.ase.token[i - 1]
            if (Model_ase.CharIsTokenDelimiter(c.code) && !restOfLine || c == '\n' || c == '\r') {
                Model_ase.ase.token = Model_ase.ase.token.substring(0, i - 1)
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
        while (Model_ase.ASE_GetToken(false)) {
            if ("{" == Model_ase.ase.token) {
                indent++
            } else if ("}" == Model_ase.ase.token) {
                --indent
                if (indent == 0) {
                    break
                } else if (indent < 0) {
                    Common.common.Error("Unexpected '}'")
                }
            } else {
                parser?.run(Model_ase.ase.token)
            }
        }
    }

    fun ASE_SkipEnclosingBraces() {
        var indent = 0
        while (Model_ase.ASE_GetToken(false)) {
            if ("{" == Model_ase.ase.token) {
                indent++
            } else if ("}" == Model_ase.ase.token) {
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
        Model_ase.ASE_GetToken(true)
    }

    fun ASE_ParseGeomObject() {
        val `object`: aseObject_t
        Model_ase.VERBOSE("GEOMOBJECT")

//        object = (aseObject_t *) Mem_Alloc(sizeof(aseObject_t));
//        memset(object, 0, sizeof(aseObject_t));
        `object` = aseObject_t()
        Model_ase.ase.model.objects.Append(`object`)
        Model_ase.ase.currentObject = `object`
        `object`.frames.Resize(32, 32)
        Model_ase.ASE_ParseBracedBlock(ASE_KeyGEOMOBJECT.getInstance())
    }

    /*
     =================
     ASE_Parse
     =================
     */
    fun ASE_Parse(buffer: ByteBuffer?, verbose: Boolean): aseModel_s? {
        Model_ase.ase = ase_t() //memset( &ase, 0, sizeof( ase ) );
        Model_ase.ase.verbose = verbose
        Model_ase.ase.buffer = TempDump.bbtocb(buffer) //.asCharBuffer();
        Model_ase.ase.len = Model_ase.ase.buffer.length //TODO:capacity?
        Model_ase.ase.curpos = 0 //ase.buffer;
        Model_ase.ase.currentObject = null

        // NOTE: using new operator because aseModel_t contains idList class objects
        Model_ase.ase.model = aseModel_s() //memset(ase.model, 0, sizeof(aseModel_t));
        Model_ase.ase.model.objects.Resize(32, 32)
        Model_ase.ase.model.materials.Resize(32, 32)
        while (Model_ase.ASE_GetToken(false)) {
            when (Model_ase.ase.token) {
                "*3DSMAX_ASCIIEXPORT", "*COMMENT" -> Model_ase.ASE_SkipRestOfLine()
                "*SCENE" -> Model_ase.ASE_SkipEnclosingBraces()
                "*GROUP" -> {
                    Model_ase.ASE_GetToken(false) // group name
                    Model_ase.ASE_ParseBracedBlock(ASE_KeyGROUP.getInstance())
                }
                "*SHAPEOBJECT" -> Model_ase.ASE_SkipEnclosingBraces()
                "*CAMERAOBJECT" -> Model_ase.ASE_SkipEnclosingBraces()
                "*MATERIAL_LIST" -> {
                    Model_ase.VERBOSE("MATERIAL_LIST\n")
                    Model_ase.ASE_ParseBracedBlock(ASE_KeyMATERIAL_LIST.getInstance())
                }
                "*GEOMOBJECT" -> Model_ase.ASE_ParseGeomObject()
                else -> if (TempDump.isNotNullOrEmpty(Model_ase.ase.token)) {
                    Common.common.Printf("Unknown token '%s'\n", Model_ase.ase.token)
                }
            }
        }
        return Model_ase.ase.model
    }

    fun atof(str: String?): Float {
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
        var tVertexNum: IntArray? = IntArray(3)
        var vertexColors: Array<ByteArray?>? = Array(3) { ByteArray(4) }
        val faceNormal: idVec3 = idVec3()
        var vertexNum: IntArray? = IntArray(3)
        val vertexNormals: Array<idVec3>? = idVec3.Companion.generateArray(3)
    }

    class aseMesh_t {
        //
        val transform: Array<idVec3>? = idVec3.Companion.generateArray(4) // applied to normals
        private val DBG_count = DBG_counter++

        //
        var colorsParsed = false
        var cvertexes: Array<idVec3>?
        var faces: Array<aseFace_t?>?
        var normalsParsed = false
        var numCVFaces = 0
        var numCVertexes = 0
        var numFaces = 0
        var numTVFaces = 0
        var numTVertexes = 0
        var numVertexes = 0
        var timeValue = 0
        var tvertexes: Array<idVec2>?
        var vertexes: Array<idVec3>?

        companion object {
            private var DBG_counter = 1
        }
    }

    class aseMaterial_t {
        val name: CharArray? = CharArray(128)
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
        val frames: idList<aseMesh_t?>?
        var materialRef = 0

        //
        var mesh: aseMesh_t?

        //	char					name[128];
        var name: CharArray?

        init {
            name = CharArray(128)
            mesh = aseMesh_t()
            frames = idList()
        }
    }

    class aseModel_s {
        //	ID_TIME_T					timeStamp;
        val timeStamp: LongArray? = longArrayOf(1)
        val materials: idList<aseMaterial_t?>?
        val objects: idList<aseObject_t?>?

        init {
            materials = idList()
            objects = idList()
        }
    }

    // working variables used during parsing
    class ase_t {
        var buffer: CharBuffer? = null
        var curpos = 0
        var currentFace = 0
        var currentMaterial: aseMaterial_t? = null
        var currentMesh: aseMesh_t? = null
        var currentObject: aseObject_t? = null
        var currentVertex = 0
        var len = 0

        //
        var model: aseModel_s? = null

        //        final char[] token = new char[1024];
        var token: String? = null

        //
        var verbose = false
    }

    abstract class ASE {
        abstract fun run(token: String?)
    }

    class ASE_KeyMAP_DIFFUSE private constructor() : ASE() {
        override fun run(token: String?) {
            val material: aseMaterial_t?
            when ("" + token) {
                "*BITMAP" -> {
                    val qpath: idStr
                    val matname: idStr
                    Model_ase.ASE_GetToken(false)
                    // remove the quotes
                    val s = Model_ase.ase.token.substring(1).indexOf('\"')
                    if (s > 0) {
                        Model_ase.ase.token = Model_ase.ase.token.substring(0, s + 1)
                    }
                    matname = idStr(Model_ase.ase.token.substring(1))
                    // convert the 3DSMax material pathname to a qpath
                    matname.BackSlashesToSlashes()
                    qpath = idStr(FileSystem_h.fileSystem.OSPathToRelativePath(matname.toString()))
                    idStr.Companion.Copynz(
                        Model_ase.ase.currentMaterial.name,
                        qpath.toString(),
                        Model_ase.ase.currentMaterial.name.size
                    )
                }
                "*UVW_U_OFFSET" -> {
                    material = Model_ase.ase.model.materials.get(Model_ase.ase.model.materials.Num() - 1)
                    Model_ase.ASE_GetToken(false)
                    material.uOffset = Model_ase.ase.token.toFloat()
                }
                "*UVW_V_OFFSET" -> {
                    material = Model_ase.ase.model.materials.get(Model_ase.ase.model.materials.Num() - 1)
                    Model_ase.ASE_GetToken(false)
                    material.vOffset = Model_ase.ase.token.toFloat()
                }
                "*UVW_U_TILING" -> {
                    material = Model_ase.ase.model.materials.get(Model_ase.ase.model.materials.Num() - 1)
                    Model_ase.ASE_GetToken(false)
                    material.uTiling = Model_ase.ase.token.toFloat()
                }
                "*UVW_V_TILING" -> {
                    material = Model_ase.ase.model.materials.get(Model_ase.ase.model.materials.Num() - 1)
                    Model_ase.ASE_GetToken(false)
                    material.vTiling = Model_ase.ase.token.toFloat()
                }
                "*UVW_ANGLE" -> {
                    material = Model_ase.ase.model.materials.get(Model_ase.ase.model.materials.Num() - 1)
                    Model_ase.ASE_GetToken(false)
                    material.angle = Model_ase.ase.token.toFloat()
                }
                else -> {}
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMAP_DIFFUSE()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMATERIAL private constructor() : ASE() {
        override fun run(token: String?) {
            run {
                if ("*MAP_DIFFUSE" == token) {
                    Model_ase.ASE_ParseBracedBlock(ASE_KeyMAP_DIFFUSE.getInstance())
                } else {
                }
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMATERIAL()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMATERIAL_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            if ("*MATERIAL_COUNT" == token) {
                Model_ase.ASE_GetToken(false)
                Model_ase.VERBOSE("..num materials: %s\n", Model_ase.ase.token)
            } else if ("*MATERIAL" == token) {
                Model_ase.VERBOSE("..material %d\n", Model_ase.ase.model.materials.Num())

//                ase.currentMaterial = (aseMaterial_t) Mem_Alloc(sizeof(aseMaterial_t));
//                memset(ase.currentMaterial, 0, sizeof(aseMaterial_t));
                Model_ase.ase.currentMaterial = aseMaterial_t()
                Model_ase.ase.currentMaterial.uTiling = 1f
                Model_ase.ase.currentMaterial.vTiling = 1f
                Model_ase.ase.model.materials.Append(Model_ase.ase.currentMaterial)
                Model_ase.ASE_ParseBracedBlock(ASE_KeyMATERIAL.getInstance())
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMATERIAL_LIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyNODE_TM private constructor() : ASE() {
        override fun run(token: String?) {
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
                Model_ase.ASE_GetToken(false)
                Model_ase.ase.currentObject.mesh.transform[j].set(i, Model_ase.ase.token.toFloat())
                i++
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyNODE_TM()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_VERTEX_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            run {
                val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
                if ("*MESH_VERTEX" == token) {
                    Model_ase.ASE_GetToken(false) // skip number
                    //pMesh.vertexes[ase.currentVertex] = new idVec3();
                    Model_ase.ASE_GetToken(false)
                    pMesh.vertexes.get(Model_ase.ase.currentVertex).x = Model_ase.ase.token.toFloat()
                    Model_ase.ASE_GetToken(false)
                    pMesh.vertexes.get(Model_ase.ase.currentVertex).y = Model_ase.ase.token.toFloat()
                    Model_ase.ASE_GetToken(false)
                    pMesh.vertexes.get(Model_ase.ase.currentVertex).z = Model_ase.ase.token.toFloat()
                    Model_ase.ase.currentVertex++
                    if (Model_ase.ase.currentVertex > pMesh.numVertexes) {
                        Common.common.Error("ase.currentVertex >= pMesh.numVertexes")
                    }
                } else {
                    Common.common.Error("Unknown token '%s' while parsing MESH_VERTEX_LIST", token)
                }
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_VERTEX_LIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_FACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            if ("*MESH_FACE" == token) {
                Model_ase.ASE_GetToken(false) // skip face number
                pMesh.faces.get(Model_ase.ase.currentFace) = aseFace_t()

                // we are flipping the order here to change the front/back facing
                // from 3DS to our standard (clockwise facing out)
                Model_ase.ASE_GetToken(false) // skip label
                Model_ase.ASE_GetToken(false) // first vertex
                pMesh.faces.get(Model_ase.ase.currentFace).vertexNum.get(0) = Model_ase.ase.token.toInt()
                Model_ase.ASE_GetToken(false) // skip label
                Model_ase.ASE_GetToken(false) // second vertex
                pMesh.faces.get(Model_ase.ase.currentFace).vertexNum.get(2) = Model_ase.ase.token.toInt()
                Model_ase.ASE_GetToken(false) // skip label
                Model_ase.ASE_GetToken(false) // third vertex
                pMesh.faces.get(Model_ase.ase.currentFace).vertexNum.get(1) = Model_ase.ase.token.toInt()
                Model_ase.ASE_GetToken(true)

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
                 */Model_ase.ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_FACE_LIST", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_FACE_LIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyTFACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            if ("*MESH_TFACE" == token) {
                val a: Int
                val b: Int
                val c: Int
                Model_ase.ASE_GetToken(false)
                Model_ase.ASE_GetToken(false)
                a = Model_ase.ase.token.toInt()
                Model_ase.ASE_GetToken(false)
                c = Model_ase.ase.token.toInt()
                Model_ase.ASE_GetToken(false)
                b = Model_ase.ase.token.toInt()
                pMesh.faces.get(Model_ase.ase.currentFace).tVertexNum.get(0) = a
                pMesh.faces.get(Model_ase.ase.currentFace).tVertexNum.get(1) = b
                pMesh.faces.get(Model_ase.ase.currentFace).tVertexNum.get(2) = c
                Model_ase.ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_TFACE", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyTFACE_LIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyCFACE_LIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            if ("*MESH_CFACE" == token) {
                Model_ase.ASE_GetToken(false)
                for (i in 0..2) {
                    Model_ase.ASE_GetToken(false)
                    val a = Model_ase.ase.token.toInt()

                    // we flip the vertex order to change the face direction to our style
                    pMesh.faces.get(Model_ase.ase.currentFace).vertexColors.get(remap.get(i)).get(0) =
                        (pMesh.cvertexes.get(a).get(0) * 255).toInt().toByte()
                    pMesh.faces.get(Model_ase.ase.currentFace).vertexColors.get(remap.get(i)).get(1) =
                        (pMesh.cvertexes.get(a).get(1) * 255).toInt().toByte()
                    pMesh.faces.get(Model_ase.ase.currentFace).vertexColors.get(remap.get(i)).get(2) =
                        (pMesh.cvertexes.get(a).get(2) * 255).toInt().toByte()
                }
                Model_ase.ase.currentFace++
            } else {
                Common.common.Error("Unknown token '%s' in MESH_CFACE", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyCFACE_LIST()
            private val remap /*[3]*/: IntArray? = intArrayOf(0, 2, 1)
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_TVERTLIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            if ("*MESH_TVERT" == token) {
//		char u[80], v[80], w[80];
                val u: String?
                val v: String?
                val w: String?
                Model_ase.ASE_GetToken(false)
                pMesh.tvertexes.get(Model_ase.ase.currentVertex) = idVec2()
                Model_ase.ASE_GetToken(false)
                //		strcpy( u, ase.token );
                u = Model_ase.ase.token
                Model_ase.ASE_GetToken(false)
                //		strcpy( v, ase.token );
                v = Model_ase.ase.token
                Model_ase.ASE_GetToken(false)
                //		strcpy( w, ase.token );
                w = Model_ase.ase.token
                pMesh.tvertexes.get(Model_ase.ase.currentVertex).x = u.toFloat()
                // our OpenGL second texture axis is inverted from MAX's sense
                pMesh.tvertexes.get(Model_ase.ase.currentVertex).y = 1.0f - v.toFloat()
                Model_ase.ase.currentVertex++
                if (Model_ase.ase.currentVertex > pMesh.numTVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numTVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_TVERTLIST", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_TVERTLIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_CVERTLIST private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            pMesh.colorsParsed = true
            if ("*MESH_VERTCOL" == token) {
                Model_ase.ASE_GetToken(false)
                Model_ase.ASE_GetToken(false)
                // atof can return 0.0 if it can't convert. Not really the case if java land
                if (pMesh.cvertexes == null) {
                    pMesh.cvertexes = idVec3.Companion.generateArray(pMesh.numCVertexes)
                }
                //pMesh.cvertexes[ase.currentVertex] = new idVec3();
                pMesh.cvertexes.get(Model_ase.ase.currentVertex).set(0, Model_ase.atof(token))
                Model_ase.ASE_GetToken(false)
                pMesh.cvertexes.get(Model_ase.ase.currentVertex).set(1, Model_ase.atof(token))
                Model_ase.ASE_GetToken(false)
                pMesh.cvertexes.get(Model_ase.ase.currentVertex).set(2, Model_ase.atof(token))
                Model_ase.ase.currentVertex++
                if (Model_ase.ase.currentVertex > pMesh.numCVertexes) {
                    Common.common.Error("ase.currentVertex > pMesh.numCVertexes")
                }
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_CVERTLIST", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_CVERTLIST()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_NORMALS private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            val f: aseFace_t?
            val n = idVec3()
            pMesh.normalsParsed = true
            if ("*MESH_FACENORMAL" == token) {
                val num: Int
                Model_ase.ASE_GetToken(false)
                num = Model_ase.ase.token.toInt()
                if (num >= pMesh.numFaces || num < 0) {
                    Common.common.Error("MESH_NORMALS face index out of range: %d", num)
                }
                f = pMesh.faces.get(Model_ase.ase.currentFace)
                if (num != Model_ase.ase.currentFace) {
                    Common.common.Error("MESH_NORMALS face index != currentFace")
                }
                Model_ase.ASE_GetToken(false)
                n.set(0, Model_ase.ase.token.toFloat())
                Model_ase.ASE_GetToken(false)
                n.set(1, Model_ase.ase.token.toFloat())
                Model_ase.ASE_GetToken(false)
                n.set(2, Model_ase.ase.token.toFloat())
                f.faceNormal.set(
                    0,
                    n.get(0) * pMesh.transform.get(0).get(0) + n.get(1) * pMesh.transform.get(1)
                        .get(0) + n.get(2) * pMesh.transform.get(2).get(0)
                )
                f.faceNormal.set(
                    1,
                    n.get(0) * pMesh.transform.get(0).get(1) + n.get(1) * pMesh.transform.get(1)
                        .get(1) + n.get(2) * pMesh.transform.get(2).get(1)
                )
                f.faceNormal.set(
                    2,
                    n.get(0) * pMesh.transform.get(0).get(2) + n.get(1) * pMesh.transform.get(1)
                        .get(2) + n.get(2) * pMesh.transform.get(2).get(2)
                )
                f.faceNormal.Normalize()
                Model_ase.ase.currentFace++
            } else if ("*MESH_VERTEXNORMAL" == token) {
                val num: Int
                var v: Int
                Model_ase.ASE_GetToken(false)
                num = Model_ase.ase.token.toInt()
                if (num >= pMesh.numVertexes || num < 0) {
                    Common.common.Error("MESH_NORMALS vertex index out of range: %d", num)
                }
                f = pMesh.faces.get(Model_ase.ase.currentFace - 1)
                v = 0
                while (v < 3) {
                    if (num == f.vertexNum.get(v)) {
                        break
                    }
                    v++
                }
                if (v == 3) {
                    Common.common.Error("MESH_NORMALS vertex index doesn't match face")
                }
                Model_ase.ASE_GetToken(false)
                n.set(0, Model_ase.ase.token.toFloat())
                Model_ase.ASE_GetToken(false)
                n.set(1, Model_ase.ase.token.toFloat())
                Model_ase.ASE_GetToken(false)
                n.set(2, Model_ase.ase.token.toFloat())
                f.vertexNormals.get(v).set(
                    0,
                    n.get(0) * pMesh.transform.get(0).get(0) + n.get(1) * pMesh.transform.get(1)
                        .get(0) + n.get(2) * pMesh.transform.get(2).get(0)
                )
                f.vertexNormals.get(v).set(
                    0,
                    n.get(0) * pMesh.transform.get(0).get(1) + n.get(1) * pMesh.transform.get(1)
                        .get(1) + n.get(2) * pMesh.transform.get(2).get(2)
                )
                f.vertexNormals.get(v).set(
                    0,
                    n.get(0) * pMesh.transform.get(0).get(2) + n.get(1) * pMesh.transform.get(1)
                        .get(2) + n.get(2) * pMesh.transform.get(2).get(1)
                )
                f.vertexNormals.get(v).Normalize()
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_NORMALS()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH private constructor() : ASE() {
        override fun run(token: String?) {
            val pMesh: aseMesh_t? = Model_ase.ASE_GetCurrentMesh()
            if (null != token) {
                when (token) {
                    "*TIMEVALUE" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.timeValue = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....timevalue: %d\n", pMesh.timeValue)
                    }
                    "*MESH_NUMVERTEX" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numVertexes = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num vertexes: %d\n", pMesh.numVertexes)
                    }
                    "*MESH_NUMTVERTEX" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numTVertexes = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num tvertexes: %d\n", pMesh.numTVertexes)
                    }
                    "*MESH_NUMCVERTEX" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numCVertexes = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num cvertexes: %d\n", pMesh.numCVertexes)
                    }
                    "*MESH_NUMFACES" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numFaces = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num faces: %d\n", pMesh.numFaces)
                    }
                    "*MESH_NUMTVFACES" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numTVFaces = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num tvfaces: %d\n", pMesh.numTVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMTVFACES != MESH_NUMFACES")
                        }
                    }
                    "*MESH_NUMCVFACES" -> {
                        Model_ase.ASE_GetToken(false)
                        pMesh.numCVFaces = Model_ase.ase.token.toInt()
                        Model_ase.VERBOSE(".....num cvfaces: %d\n", pMesh.numCVFaces)
                        if (pMesh.numTVFaces != pMesh.numFaces) {
                            Common.common.Error("MESH_NUMCVFACES != MESH_NUMFACES")
                        }
                    }
                    "*MESH_VERTEX_LIST" -> {
                        pMesh.vertexes =
                            idVec3.Companion.generateArray(pMesh.numVertexes) // Mem_Alloc(pMesh.numVertexes);
                        Model_ase.ase.currentVertex = 0
                        Model_ase.VERBOSE(".....parsing MESH_VERTEX_LIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_VERTEX_LIST.getInstance())
                    }
                    "*MESH_TVERTLIST" -> {
                        Model_ase.ase.currentVertex = 0
                        pMesh.tvertexes = arrayOfNulls<idVec2>(pMesh.numTVertexes) // Mem_Alloc(pMesh.numTVertexes);
                        Model_ase.VERBOSE(".....parsing MESH_TVERTLIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_TVERTLIST.getInstance())
                    }
                    "*MESH_CVERTLIST" -> {
                        Model_ase.ase.currentVertex = 0
                        pMesh.cvertexes =
                            idVec3.Companion.generateArray(pMesh.numCVertexes) // Mem_Alloc(pMesh.numCVertexes);
                        Model_ase.VERBOSE(".....parsing MESH_CVERTLIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_CVERTLIST.getInstance())
                    }
                    "*MESH_FACE_LIST" -> {
                        pMesh.faces = arrayOfNulls<aseFace_t?>(pMesh.numFaces) // Mem_Alloc(pMesh.numFaces);
                        Model_ase.ase.currentFace = 0
                        Model_ase.VERBOSE(".....parsing MESH_FACE_LIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_FACE_LIST.getInstance())
                    }
                    "*MESH_TFACELIST" -> {
                        if (null == pMesh.faces) {
                            Common.common.Error("*MESH_TFACELIST before *MESH_FACE_LIST")
                        }
                        Model_ase.ase.currentFace = 0
                        Model_ase.VERBOSE(".....parsing MESH_TFACE_LIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyTFACE_LIST.getInstance())
                    }
                    "*MESH_CFACELIST" -> {
                        if (null == pMesh.faces) { //TODO:check pointer position instead of entire array
                            Common.common.Error("*MESH_CFACELIST before *MESH_FACE_LIST")
                        }
                        Model_ase.ase.currentFace = 0
                        Model_ase.VERBOSE(".....parsing MESH_CFACE_LIST\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyCFACE_LIST.getInstance())
                    }
                    "*MESH_NORMALS" -> {
                        if (null == pMesh.faces) {
                            Common.common.Warning("*MESH_NORMALS before *MESH_FACE_LIST")
                        }
                        Model_ase.ase.currentFace = 0
                        Model_ase.VERBOSE(".....parsing MESH_NORMALS\n")
                        Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_NORMALS.getInstance())
                    }
                }
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyMESH_ANIMATION private constructor() : ASE() {
        override fun run(token: String?) {
            val mesh: aseMesh_t

            // loads a single animation frame
            if ("*MESH" == token) {
                Model_ase.VERBOSE("...found MESH\n")

//                mesh = (aseMesh_t) Mem_Alloc(sizeof(aseMesh_t));
//                memset(mesh, 0, sizeof(aseMesh_t));
                mesh = aseMesh_t()
                Model_ase.ase.currentMesh = mesh
                Model_ase.ase.currentObject.frames.Append(mesh)
                Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH.getInstance())
            } else {
                Common.common.Error("Unknown token '%s' while parsing MESH_ANIMATION", token)
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyMESH_ANIMATION()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyGEOMOBJECT private constructor() : ASE() {
        override fun run(token: String?) {
            val `object`: aseObject_t?
            `object` = Model_ase.ase.currentObject
            when ("" + token) {
                "*NODE_NAME" -> {
                    Model_ase.ASE_GetToken(true)
                    Model_ase.VERBOSE(" %s\n", Model_ase.ase.token)
                    idStr.Companion.Copynz(`object`.name, Model_ase.ase.token, `object`.name.size)
                }
                "*NODE_PARENT" -> Model_ase.ASE_SkipRestOfLine()
                "*NODE_TM", "*TM_ANIMATION" -> Model_ase.ASE_ParseBracedBlock(ASE_KeyNODE_TM.getInstance())
                "*MESH" -> {
                    val transform: Array<idVec3> =
                        idVec3.Companion.copyVec(Model_ase.ase.currentObject.mesh.transform) //copied from the bfg sources
                    run {
                        Model_ase.ase.currentObject.mesh = aseMesh_t()
                        Model_ase.ase.currentMesh = Model_ase.ase.currentObject.mesh
                    }
                    var i = 0
                    while (i < transform.size) {
                        Model_ase.ase.currentMesh.transform[i].set(transform[i])
                        i++
                    }
                    Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH.getInstance())
                }
                "*MATERIAL_REF" -> {
                    Model_ase.ASE_GetToken(false)
                    `object`.materialRef = Model_ase.ase.token.toInt()
                }
                "*MESH_ANIMATION" -> {
                    Model_ase.VERBOSE("..found MESH_ANIMATION\n")
                    Model_ase.ASE_ParseBracedBlock(ASE_KeyMESH_ANIMATION.getInstance())
                }
                "*PROP_MOTIONBLUR", "*PROP_CASTSHADOW", "*PROP_RECVSHADOW" -> Model_ase.ASE_SkipRestOfLine()
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyGEOMOBJECT()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }

    class ASE_KeyGROUP private constructor() : ASE() {
        override fun run(token: String?) {
            if ("*GEOMOBJECT" == token) {
                Model_ase.ASE_ParseGeomObject()
            }
        }

        companion object {
            private val instance: ASE? = ASE_KeyGROUP()
            fun getInstance(): ASE? {
                return instance
            }
        }
    }
}