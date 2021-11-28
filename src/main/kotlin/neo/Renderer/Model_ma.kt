package neo.Renderer

import neo.TempDump
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.idlib.Lib.idException
import neo.idlib.Text.Lexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat4
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 *
 */
object Model_ma {
    /**
     *
     */
    var maGlobal: ma_t? = null

    /*
     ======================================================================

     Parses Maya ASCII files.

     ======================================================================
     */
    fun MA_VERBOSE(fmt: String?, vararg x: Any?) {
        if (Model_ma.maGlobal.verbose) {
            Common.common.Printf(fmt, *x)
        }
    }

    @Throws(idException::class)
    fun MA_ParseNodeHeader(parser: idParser?, header: maNodeHeader_t?) {

//	memset(header, 0, sizeof(maNodeHeader_t));//TODO:
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (0 == token.Icmp("-")) {
                parser.ReadToken(token)
                if (0 == token.Icmp("n")) {
                    parser.ReadToken(token)
                    header.name = token.toString()
                } else if (0 == token.Icmp("p")) {
                    parser.ReadToken(token)
                    header.parent = token.toString()
                }
            } else if (0 == token.Icmp(";")) {
                break
            }
        }
    }

    @Throws(idException::class)
    fun MA_ParseHeaderIndex(
        header: maAttribHeader_t?,
        minIndex: IntArray?,
        maxIndex: IntArray?,
        headerType: String?,
        skipString: String?
    ): Boolean {
        val miniParse = idParser()
        val token = idToken()
        miniParse.LoadMemory(header.name, header.name.length, headerType)
        if (skipString != null) {
            miniParse.SkipUntilString(skipString)
        }
        if (!miniParse.SkipUntilString("[")) {
            //This was just a header
            return false
        }
        minIndex.get(0) = miniParse.ParseInt()
        miniParse.ReadToken(token)
        if (0 == token.Icmp("]")) {
            maxIndex.get(0) = minIndex.get(0)
        } else {
            maxIndex.get(0) = miniParse.ParseInt()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseAttribHeader(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val token = idToken()

        // memset(header, 0, sizeof(maAttribHeader_t));
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            parser.ReadToken(token)
            if (0 == token.Icmp("s")) {
                header.size = parser.ParseInt()
                parser.ReadToken(token)
            }
        }
        header.name = token.toString()
        return true
    }

    @Throws(idException::class)
    fun MA_ReadVec3(parser: idParser?, vec: idVec3?): Boolean {
        // idToken token;
        if (!parser.SkipUntilString("double3")) {
            throw idException(Str.va("Maya Loader '%s': Invalid Vec3", parser.GetFileName()))
            //		return false;
        }

        //We need to flip y and z because of the maya coordinate system
        vec.x = parser.ParseFloat()
        vec.z = parser.ParseFloat()
        vec.y = parser.ParseFloat()
        return true
    }

    fun IsNodeComplete(token: idToken?): Boolean {
        return 0 == token.Icmp("createNode") || 0 == token.Icmp("connectAttr") || 0 == token.Icmp("select")
    }

    @Throws(idException::class)
    fun MA_ParseTransform(parser: idParser?): Boolean {
        val header = maNodeHeader_t()
        val transform: maTransform_s
        // memset(&header, 0, sizeof(header));

        //Allocate room for the transform
        transform = maTransform_s() // Mem_Alloc(sizeof(maTransform_s));
        // memset(transform, 0, sizeof(maTransform_t));
        transform.scale.z = 1f
        transform.scale.y = transform.scale.z
        transform.scale.x = transform.scale.y

        //Get the header info from the transform
        Model_ma.MA_ParseNodeHeader(parser, header)

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (Model_ma.IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                parser.ReadToken(token)
                if (0 == token.Icmp(".t")) {
                    if (!Model_ma.MA_ReadVec3(parser, transform.translate)) {
                        return false
                    }
                    transform.translate.y *= -1f
                } else if (0 == token.Icmp(".r")) {
                    if (!Model_ma.MA_ReadVec3(parser, transform.rotate)) {
                        return false
                    }
                } else if (0 == token.Icmp(".s")) {
                    if (!Model_ma.MA_ReadVec3(parser, transform.scale)) {
                        return false
                    }
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
        if (!header.parent.isEmpty()) {
            //Find the parent
            val parent: maTransform_s? = Model_ma.maGlobal.model.transforms[header.parent]
            if (parent != null) {
                transform.parent = parent
            }
        }

        //Add this transform to the list
        Model_ma.maGlobal.model.transforms[header.name] = transform
        return true
    }

    @Throws(idException::class)
    fun MA_ParseVertex(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        // idToken token;

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.vertexes) {
            pMesh.numVertexes = header.size
            pMesh.vertexes = idVec3.Companion.generateArray(pMesh.numVertexes) // Mem_Alloc(pMesh.numVertexes);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "VertexHeader", null)) {
            //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.vertexes.get(i).x = parser.ParseFloat()
            pMesh.vertexes.get(i).z = parser.ParseFloat()
            pMesh.vertexes.get(i).y = -parser.ParseFloat()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseVertexTransforms(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.vertTransforms) {
            if (header.size == 0) {
                header.size = 1
            }
            pMesh.numVertTransforms = header.size
            pMesh.vertTransforms = arrayOfNulls<idVec4?>(pMesh.numVertTransforms) // Mem_Alloc(pMesh.numVertTransforms);
            pMesh.nextVertTransformIndex = 0
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "VertexTransformHeader", null)) {
            //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float3")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.vertTransforms.get(pMesh.nextVertTransformIndex).x = parser.ParseFloat()
            pMesh.vertTransforms.get(pMesh.nextVertTransformIndex).z = parser.ParseFloat()
            pMesh.vertTransforms.get(pMesh.nextVertTransformIndex).y = -parser.ParseFloat()

            //w hold the vert index
            pMesh.vertTransforms.get(pMesh.nextVertTransformIndex).w = i.toFloat()
            pMesh.nextVertTransformIndex++
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseEdge(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        // idToken token;

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.edges) {
            pMesh.numEdges = header.size
            pMesh.edges = idVec3.Companion.generateArray(pMesh.numEdges) // Mem_Alloc(pMesh.numEdges);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "EdgeHeader", null)) {
            //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.edges.get(i).x = parser.ParseFloat()
            pMesh.edges.get(i).y = parser.ParseFloat()
            pMesh.edges.get(i).z = parser.ParseFloat()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseNormal(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.normals) {
            pMesh.numNormals = header.size
            pMesh.normals = idVec3.Companion.generateArray(pMesh.numNormals) // Mem_Alloc(pMesh.numNormals);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "NormalHeader", null)) {
            //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float3")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.normals.get(i).x = parser.ParseFloat()

            //Adjust the normals for the change in coordinate systems
            pMesh.normals.get(i).z = parser.ParseFloat()
            pMesh.normals.get(i).y = -parser.ParseFloat()
            pMesh.normals.get(i).Normalize()
        }
        pMesh.normalsParsed = true
        pMesh.nextNormal = 0
        return true
    }

    @Throws(idException::class)
    fun MA_ParseFace(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val token = idToken()

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.faces) {
            pMesh.numFaces = header.size
            pMesh.faces = arrayOfNulls<maFace_t?>(pMesh.numFaces) // Mem_Alloc(pMesh.numFaces);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "FaceHeader", null)) {
            //This was just a header
            return true
        }

        //Read the face data
        var currentFace = minIndex[0] - 1
        while (parser.ReadToken(token)) {
            if (Model_ma.IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("f")) {
                val count = parser.ParseInt()
                if (count != 3) {
                    throw idException(Str.va("Maya Loader '%s': Face is not a triangle.", parser.GetFileName()))
                    //                    return false;
                }
                //Increment the face number because a new face always starts with an "f" token
                currentFace++

                //We cannot reorder edges until later because the normal processing
                //assumes the edges are in the original order
                pMesh.faces.get(currentFace).edge.get(0) = parser.ParseInt()
                pMesh.faces.get(currentFace).edge.get(1) = parser.ParseInt()
                pMesh.faces.get(currentFace).edge.get(2) = parser.ParseInt()

                //Some more init stuff
                pMesh.faces.get(currentFace).vertexColors.get(2) = -1
                pMesh.faces.get(currentFace).vertexColors.get(1) = pMesh.faces.get(currentFace).vertexColors.get(2)
                pMesh.faces.get(currentFace).vertexColors.get(0) = pMesh.faces.get(currentFace).vertexColors.get(1)
            } else if (0 == token.Icmp("mu")) {
                val uvstIndex = parser.ParseInt()
                val count = parser.ParseInt()
                if (count != 3) {
                    throw idException(Str.va("Maya Loader '%s': Invalid texture coordinates.", parser.GetFileName()))
                    //                    return false;
                }
                pMesh.faces.get(currentFace).tVertexNum.get(0) = parser.ParseInt()
                pMesh.faces.get(currentFace).tVertexNum.get(1) = parser.ParseInt()
                pMesh.faces.get(currentFace).tVertexNum.get(2) = parser.ParseInt()
            } else if (0 == token.Icmp("mf")) {
                val count = parser.ParseInt()
                if (count != 3) {
                    throw idException(Str.va("Maya Loader '%s': Invalid texture coordinates.", parser.GetFileName()))
                    //                    return false;
                }
                pMesh.faces.get(currentFace).tVertexNum.get(0) = parser.ParseInt()
                pMesh.faces.get(currentFace).tVertexNum.get(1) = parser.ParseInt()
                pMesh.faces.get(currentFace).tVertexNum.get(2) = parser.ParseInt()
            } else if (0 == token.Icmp("fc")) {
                val count = parser.ParseInt()
                if (count != 3) {
                    throw idException(Str.va("Maya Loader '%s': Invalid vertex color.", parser.GetFileName()))
                    //                    return false;
                }
                pMesh.faces.get(currentFace).vertexColors.get(0) = parser.ParseInt()
                pMesh.faces.get(currentFace).vertexColors.get(1) = parser.ParseInt()
                pMesh.faces.get(currentFace).vertexColors.get(2) = parser.ParseInt()
            }
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseColor(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        // idToken token;

        //Allocate enough space for all the verts if this is the first attribute for verticies
        if (null == pMesh.colors) {
            pMesh.numColors = header.size
            pMesh.colors = ByteArray(pMesh.numColors * 4) // Mem_Alloc(pMesh.numColors * 4);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "ColorHeader", null)) {
            //This was just a header
            return true
        }

        //Read each vert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.colors.get(i * 4 + 0) = (parser.ParseFloat() * 255).toByte()
            pMesh.colors.get(i * 4 + 1) = (parser.ParseFloat() * 255).toByte()
            pMesh.colors.get(i * 4 + 2) = (parser.ParseFloat() * 255).toByte()
            pMesh.colors.get(i * 4 + 3) = (parser.ParseFloat() * 255).toByte()
        }
        return true
    }

    @Throws(idException::class)
    fun MA_ParseTVert(parser: idParser?, header: maAttribHeader_t?): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val token = idToken()

        //This is not the texture coordinates. It is just the name so ignore it
        if (header.name.contains("uvsn")) {
            return true
        }

        //Allocate enough space for all the data
        if (null == pMesh.tvertexes) {
            pMesh.numTVertexes = header.size
            pMesh.tvertexes = arrayOfNulls<idVec2?>(pMesh.numTVertexes) // Mem_Alloc(pMesh.numTVertexes);
        }

        //Get the start and end index for this attribute
        val minIndex = IntArray(1)
        val maxIndex = IntArray(1)
        if (!Model_ma.MA_ParseHeaderIndex(header, minIndex, maxIndex, "TextureCoordHeader", "uvsp")) {
            //This was just a header
            return true
        }
        parser.ReadToken(token)
        if (0 == token.Icmp("-")) {
            val tk2 = idToken()
            parser.ReadToken(tk2)
            if (0 == tk2.Icmp("type")) {
                parser.SkipUntilString("float2")
            } else {
                parser.UnreadToken(tk2)
                parser.UnreadToken(token)
            }
        } else {
            parser.UnreadToken(token)
        }

        //Read each tvert
        for (i in minIndex[0]..maxIndex[0]) {
            pMesh.tvertexes.get(i).x = parser.ParseFloat()
            pMesh.tvertexes.get(i).y = 1.0f - parser.ParseFloat()
        }
        return true
    }

    /*
     *	Quick check to see if the vert participates in a shared normal
     */
    fun MA_QuickIsVertShared(faceIndex: Int, vertIndex: Int): Boolean {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val vertNum = pMesh.faces.get(faceIndex).vertexNum.get(vertIndex)
        for (i in 0..2) {
            var edge = pMesh.faces.get(faceIndex).edge.get(i)
            if (edge < 0) {
                edge = (Math.abs(edge) - 1)
            }
            if (pMesh.edges.get(edge).z == 1f && (pMesh.edges.get(edge).x == vertNum.toFloat() || pMesh.edges.get(edge).y == vertNum.toFloat())) {
                return true
            }
        }
        return false
    }

    fun MA_GetSharedFace(faceIndex: Int, vertIndex: Int, sharedFace: IntArray?, sharedVert: IntArray?) {
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh
        val vertNum = pMesh.faces.get(faceIndex).vertexNum.get(vertIndex)
        sharedFace.get(0) = -1
        sharedVert.get(0) = -1

        //Find a shared edge on this face that contains the specified vert
        for (edgeIndex in 0..2) {
            var edge = pMesh.faces.get(faceIndex).edge.get(edgeIndex)
            if (edge < 0) {
                edge = (Math.abs(edge) - 1)
            }
            if (pMesh.edges.get(edge).z == 1f && (pMesh.edges.get(edge).x == vertNum.toFloat() || pMesh.edges.get(edge).y == vertNum.toFloat())) {
                for (i in 0 until faceIndex) {
                    for (j in 0..2) {
                        if (pMesh.faces.get(i).vertexNum.get(j) == vertNum) {
                            sharedFace.get(0) = i
                            sharedVert.get(0) = j
                            break
                        }
                    }
                }
            }
            if (sharedFace.get(0) != -1) {
                break
            }
        }
    }

    @Throws(idException::class)
    fun MA_ParseMesh(parser: idParser?) {
        val `object`: maObject_t
        `object` = maObject_t() // Mem_Alloc(sizeof(maObject_t));
        //	memset( object, 0, sizeof( maObject_t ) );
        Model_ma.maGlobal.model.objects.Append(`object`)
        Model_ma.maGlobal.currentObject = `object`
        `object`.materialRef = -1

        //Get the header info from the mesh
        val nodeHeader = maNodeHeader_t()
        Model_ma.MA_ParseNodeHeader(parser, nodeHeader)

        //Find my parent
        if (!nodeHeader.parent.isEmpty()) {
            //Find the parent
            val parent: maTransform_s? = Model_ma.maGlobal.model.transforms[nodeHeader.parent]
            if (parent != null) {
                Model_ma.maGlobal.currentObject.mesh.transform = parent
            }
        }
        `object`.name = nodeHeader.name

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (Model_ma.IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                val attribHeader = maAttribHeader_t()
                Model_ma.MA_ParseAttribHeader(parser, attribHeader)
                if (attribHeader.name.contains(".vt")) {
                    Model_ma.MA_ParseVertex(parser, attribHeader)
                } else if (attribHeader.name.contains(".ed")) {
                    Model_ma.MA_ParseEdge(parser, attribHeader)
                } else if (attribHeader.name.contains(".pt")) {
                    Model_ma.MA_ParseVertexTransforms(parser, attribHeader)
                } else if (attribHeader.name.contains(".n")) {
                    Model_ma.MA_ParseNormal(parser, attribHeader)
                } else if (attribHeader.name.contains(".fc")) {
                    Model_ma.MA_ParseFace(parser, attribHeader)
                } else if (attribHeader.name.contains(".clr")) {
                    Model_ma.MA_ParseColor(parser, attribHeader)
                } else if (attribHeader.name.contains(".uvst")) {
                    Model_ma.MA_ParseTVert(parser, attribHeader)
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
        val pMesh: maMesh_t? = Model_ma.maGlobal.currentObject.mesh

        //Get the verts from the edge
        for (i in 0 until pMesh.numFaces) {
            for (j in 0..2) {
                var edge = pMesh.faces.get(i).edge.get(j)
                if (edge < 0) {
                    edge = (Math.abs(edge) - 1)
                    pMesh.faces.get(i).vertexNum.get(j) = pMesh.edges.get(edge).y.toInt()
                } else {
                    pMesh.faces.get(i).vertexNum.get(j) = pMesh.edges.get(edge).x.toInt()
                }
            }
        }

        //Get the normals
        if (pMesh.normalsParsed) {
            for (i in 0 until pMesh.numFaces) {
                for (j in 0..2) {

                    //Is this vertex shared
                    val sharedFace = intArrayOf(-1)
                    val sharedVert = intArrayOf(-1)
                    if (Model_ma.MA_QuickIsVertShared(i, j)) {
                        Model_ma.MA_GetSharedFace(i, j, sharedFace, sharedVert)
                    }
                    if (sharedFace[0] != -1) {
                        //Get the normal from the share
                        pMesh.faces.get(i).vertexNormals.get(j)
                            .oSet(pMesh.faces.get(sharedFace[0]).vertexNormals.get(sharedVert[0]))
                    } else {
                        //The vertex is not shared so get the next normal
                        if (pMesh.nextNormal >= pMesh.numNormals) {
                            //We are using more normals than exist
                            throw idException(Str.va("Maya Loader '%s': Invalid Normals Index.", parser.GetFileName()))
                        }
                        pMesh.faces.get(i).vertexNormals.get(j).oSet(pMesh.normals.get(pMesh.nextNormal))
                        pMesh.nextNormal++
                    }
                }
            }
        }

        //Now that the normals are good...lets reorder the verts to make the tris face the right way
        for (i in 0 until pMesh.numFaces) {
            var tmp = pMesh.faces.get(i).vertexNum.get(1)
            pMesh.faces.get(i).vertexNum.get(1) = pMesh.faces.get(i).vertexNum.get(2)
            pMesh.faces.get(i).vertexNum.get(2) = tmp
            val tmpVec = idVec3(pMesh.faces.get(i).vertexNormals.get(1))
            pMesh.faces.get(i).vertexNormals.get(1).oSet(pMesh.faces.get(i).vertexNormals.get(2))
            pMesh.faces.get(i).vertexNormals.get(2).oSet(tmpVec)
            tmp = pMesh.faces.get(i).tVertexNum.get(1)
            pMesh.faces.get(i).tVertexNum.get(1) = pMesh.faces.get(i).tVertexNum.get(2)
            pMesh.faces.get(i).tVertexNum.get(2) = tmp
            tmp = pMesh.faces.get(i).vertexColors.get(1)
            pMesh.faces.get(i).vertexColors.get(1) = pMesh.faces.get(i).vertexColors.get(2)
            pMesh.faces.get(i).vertexColors.get(2) = tmp
        }

        //Now apply the pt transformations
        for (i in 0 until pMesh.numVertTransforms) {
            pMesh.vertexes.get(pMesh.vertTransforms.get(i).w.toInt()).oPluSet(pMesh.vertTransforms.get(i).ToVec3())
        }
        Model_ma.MA_VERBOSE(Str.va("MESH %s - parent %s\n", nodeHeader.name, nodeHeader.parent))
        Model_ma.MA_VERBOSE(Str.va("\tverts:%d\n", Model_ma.maGlobal.currentObject.mesh.numVertexes))
        Model_ma.MA_VERBOSE(Str.va("\tfaces:%d\n", Model_ma.maGlobal.currentObject.mesh.numFaces))
    }

    @Throws(idException::class)
    fun MA_ParseFileNode(parser: idParser?) {

        //Get the header info from the node
        val header = maNodeHeader_t()
        Model_ma.MA_ParseNodeHeader(parser, header)

        //Read the transform attributes
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (Model_ma.IsNodeComplete(token)) {
                parser.UnreadToken(token)
                break
            }
            if (0 == token.Icmp("setAttr")) {
                val attribHeader = maAttribHeader_t()
                Model_ma.MA_ParseAttribHeader(parser, attribHeader)
                if (attribHeader.name.contains(".ftn")) {
                    parser.SkipUntilString("string")
                    parser.ReadToken(token)
                    if (0 == token.Icmp("(")) {
                        parser.ReadToken(token)
                    }
                    var fileNode: maFileNode_t
                    fileNode = maFileNode_t() // Mem_Alloc(sizeof(maFileNode_t));
                    fileNode.name = header.name
                    fileNode.path = token.toString()
                    Model_ma.maGlobal.model.fileNodes[fileNode.name] = fileNode
                } else {
                    parser.SkipRestOfLine()
                }
            }
        }
    }

    fun MA_ParseMaterialNode(parser: idParser?) {

        //Get the header info from the node
        val header = maNodeHeader_t()
        Model_ma.MA_ParseNodeHeader(parser, header)
        val matNode = maMaterialNode_s()
        //        matNode = (maMaterialNode_s) Mem_Alloc(sizeof(maMaterialNode_t));
//	memset(matNode, 0, sizeof(maMaterialNode_t));
        matNode.name = header.name
        Model_ma.maGlobal.model.materialNodes[matNode.name] = matNode
    }

    @Throws(idException::class)
    fun MA_ParseCreateNode(parser: idParser?) {
        val token = idToken()
        parser.ReadToken(token)
        if (0 == token.Icmp("transform")) {
            Model_ma.MA_ParseTransform(parser)
        } else if (0 == token.Icmp("mesh")) {
            Model_ma.MA_ParseMesh(parser)
        } else if (0 == token.Icmp("file")) {
            Model_ma.MA_ParseFileNode(parser)
        } else if (0 == token.Icmp("shadingEngine") || 0 == token.Icmp("lambert") || 0 == token.Icmp("phong") || 0 == token.Icmp(
                "blinn"
            )
        ) {
            Model_ma.MA_ParseMaterialNode(parser)
        }
    }

    fun MA_AddMaterial(materialName: String?): Int {
        val destNode: maMaterialNode_s? = Model_ma.maGlobal.model.materialNodes[materialName]
        if (destNode != null) {
            var matNode = destNode

            //Iterate down the tree until we get a file
            while (matNode != null && null == matNode.file) {
                matNode = matNode.child
            }
            if (matNode != null && matNode.file != null) {

                //Got the file
                val material: maMaterial_t
                material = maMaterial_t() //Mem_Alloc(sizeof(maMaterial_t));
                //			memset( material, 0, sizeof( maMaterial_t ) );

                //Remove the OS stuff
                val qPath: String?
                qPath = FileSystem_h.fileSystem.OSPathToRelativePath(matNode.file.path)
                material.name = qPath
                Model_ma.maGlobal.model.materials.Append(material)
                return Model_ma.maGlobal.model.materials.Num() - 1
            }
        }
        return -1
    }

    @Throws(idException::class)
    fun MA_ParseConnectAttr(parser: idParser?): Boolean {
        var temp: idStr?
        val srcName: idStr?
        val srcType: idStr?
        val destName: idStr?
        val destType: idStr?
        val token = idToken()
        parser.ReadToken(token)
        temp = token
        var dot = temp.Find(".")
        if (dot == -1) {
            throw idException(Str.va("Maya Loader '%s': Invalid Connect Attribute.", parser.GetFileName()))
            //		return false;
        }
        srcName = temp.Left(dot)
        srcType = temp.Right(temp.Length() - dot - 1)
        parser.ReadToken(token)
        temp = token
        dot = temp.Find(".")
        if (dot == -1) {
            throw idException(Str.va("Maya Loader '%s': Invalid Connect Attribute.", parser.GetFileName()))
            //		return false;
        }
        destName = temp.Left(dot)
        destType = temp.Right(temp.Length() - dot - 1)
        if (srcType.Find("oc") != -1) {
            //Is this attribute a material node attribute
            val matNode: maMaterialNode_s? = Model_ma.maGlobal.model.materialNodes[srcName.toString()]
            if (matNode != null) {
                val destNode: maMaterialNode_s? = Model_ma.maGlobal.model.materialNodes[destName.toString()]
                if (destNode != null) {
                    destNode.child = matNode
                }
            }

            //Is this attribute a file node
            val fileNode: maFileNode_t? = Model_ma.maGlobal.model.fileNodes[srcName.toString()]
            if (fileNode != null) {
                val destNode: maMaterialNode_s? = Model_ma.maGlobal.model.materialNodes[destName.toString()]
                if (destNode != null) {
                    destNode.file = fileNode
                }
            }
        }
        if (srcType.Find("iog") != -1) {
            //Is this an attribute for one of our meshes
            for (i in 0 until Model_ma.maGlobal.model.objects.Num()) {
                if (Model_ma.maGlobal.model.objects.oGet(i).name == srcName) {
                    //maGlobal.model.objects.oGet(i).materialRef = MA_AddMaterial(destName);
                    Model_ma.maGlobal.model.objects.oGet(i).materialName = destName.toString()
                    break
                }
            }
        }
        return true
    }

    fun MA_BuildScale(mat: idMat4?, x: Float, y: Float, z: Float) {
        mat.Identity()
        mat.oGet(0).oSet(0, x)
        mat.oGet(1).oSet(1, y)
        mat.oGet(2).oSet(2, z)
    }

    fun MA_BuildAxisRotation(mat: idMat4?, ang: Float, axis: Int) {
        val sinAng = idMath.Sin(ang)
        val cosAng = idMath.Cos(ang)
        mat.Identity()
        when (axis) {
            0 -> {
                mat.oGet(1).oSet(1, cosAng)
                mat.oGet(1).oSet(2, sinAng)
                mat.oGet(2).oSet(1, -sinAng)
                mat.oGet(2).oSet(2, cosAng)
            }
            1 -> {
                mat.oGet(0).oSet(0, cosAng)
                mat.oGet(0).oSet(2, -sinAng)
                mat.oGet(2).oSet(0, sinAng)
                mat.oGet(2).oSet(2, cosAng)
            }
            2 -> {
                mat.oGet(0).oSet(0, cosAng)
                mat.oGet(0).oSet(1, sinAng)
                mat.oGet(1).oSet(0, -sinAng)
                mat.oGet(1).oSet(1, cosAng)
            }
        }
    }

    fun MA_ApplyTransformation(model: maModel_s?) {
        for (i in 0 until model.objects.Num()) {
            val mesh = model.objects.oGet(i).mesh
            var transform = mesh.transform
            while (transform != null) {
                val rotx = idMat4()
                val roty = idMat4()
                val rotz = idMat4()
                val scale = idMat4()
                rotx.Identity()
                roty.Identity()
                rotz.Identity()
                if (Math.abs(transform.rotate.x) > 0.0f) {
                    Model_ma.MA_BuildAxisRotation(rotx, Math_h.DEG2RAD(-transform.rotate.x), 0)
                }
                if (Math.abs(transform.rotate.y) > 0.0f) {
                    Model_ma.MA_BuildAxisRotation(roty, Math_h.DEG2RAD(transform.rotate.y), 1)
                }
                if (Math.abs(transform.rotate.z) > 0.0f) {
                    Model_ma.MA_BuildAxisRotation(rotz, Math_h.DEG2RAD(-transform.rotate.z), 2)
                }
                Model_ma.MA_BuildScale(scale, transform.scale.x, transform.scale.y, transform.scale.z)

                //Apply the transformation to each vert
                for (j in 0 until mesh.numVertexes) {
                    mesh.vertexes.get(j).oSet(scale.oMultiply(mesh.vertexes.get(j)))
                    mesh.vertexes.get(j).oSet(rotx.oMultiply(mesh.vertexes.get(j)))
                    mesh.vertexes.get(j).oSet(rotz.oMultiply(mesh.vertexes.get(j)))
                    mesh.vertexes.get(j).oSet(roty.oMultiply(mesh.vertexes.get(j)))
                    mesh.vertexes.get(j).oSet(mesh.vertexes.get(j).oPlus(transform.translate))
                }
                transform = transform.parent
            }
        }
    }

    /*
     =================
     MA_Parse
     =================
     */
    @Throws(idException::class)
    fun MA_Parse(buffer: CharBuffer?, filename: String?, verbose: Boolean): maModel_s? {
        // // memset( &maGlobal, 0, sizeof( maGlobal ) );
        Model_ma.maGlobal.verbose = verbose
        Model_ma.maGlobal.currentObject = null

        // NOTE: using new operator because aseModel_t contains idList class objects
        Model_ma.maGlobal.model = maModel_s()
        Model_ma.maGlobal.model.objects.Resize(32, 32)
        Model_ma.maGlobal.model.materials.Resize(32, 32)
        val parser = idParser()
        parser.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT)
        parser.LoadMemory(buffer, buffer.length, filename) //TODO:use capacity instead of length?
        val token = idToken()
        while (parser.ReadToken(token)) {
            if (0 == token.Icmp("createNode")) {
                Model_ma.MA_ParseCreateNode(parser)
            } else if (0 == token.Icmp("connectAttr")) {
                Model_ma.MA_ParseConnectAttr(parser)
            }
        }

        //Resolve The Materials
        for (i in 0 until Model_ma.maGlobal.model.objects.Num()) {
            Model_ma.maGlobal.model.objects.oGet(i).materialRef =
                Model_ma.MA_AddMaterial(Model_ma.maGlobal.model.objects.oGet(i).materialName)
        }

        //Apply Transformation
        Model_ma.MA_ApplyTransformation(Model_ma.maGlobal.model)
        return Model_ma.maGlobal.model
    }

    /*
     =================
     MA_Load
     =================
     */
    fun MA_Load(fileName: String?): maModel_s? {
        val buf = arrayOf<ByteBuffer?>(null)
        val timeStamp = LongArray(1)
        var ma: maModel_s?
        FileSystem_h.fileSystem.ReadFile(fileName, buf, timeStamp)
        if (null == buf[0]) {
            return null
        }
        try {
            ma = Model_ma.MA_Parse(TempDump.bbtocb(buf[0]), fileName, false)
            ma.timeStamp = timeStamp
        } catch (e: idException) {
            Common.common.Warning("%s", e.error)
            if (Model_ma.maGlobal.model != null) {
                Model_ma.MA_Free(Model_ma.maGlobal.model)
            }
            ma = null
        }

//        fileSystem.FreeFile(buf);
        return ma
    }

    /*
     =================
     MA_Free
     =================
     */
    fun MA_Free(ma: maModel_s?) {
        var i: Int
        var obj: maObject_t
        var mesh: maMesh_t
        var material: maMaterial_t
        if (TempDump.NOT(ma)) {
            return
        }
        //        for (i = 0; i < ma.objects.Num(); i++) {
//            obj = ma.objects.oGet(i);
//
//            // free the base nesh
//            mesh = obj.mesh;
//
//            if (mesh.vertexes != null) {
//                Mem_Free(mesh.vertexes);
//            }
//            if (mesh.vertTransforms != null) {
//                Mem_Free(mesh.vertTransforms);
//            }
//            if (mesh.normals != null) {
//                Mem_Free(mesh.normals);
//            }
//            if (mesh.tvertexes != null) {
//                Mem_Free(mesh.tvertexes);
//            }
//            if (mesh.edges != null) {
//                Mem_Free(mesh.edges);
//            }
//            if (mesh.colors != null) {
//                Mem_Free(mesh.colors);
//            }
//            if (mesh.faces != null) {
//                Mem_Free(mesh.faces);
//            }
//            Mem_Free(obj);
//        }
//        ma.objects.Clear();
//
//        for (i = 0; i < ma.materials.Num(); i++) {
//            material = ma.materials.oGet(i);
//            Mem_Free(material);
//        }
//        ma.materials.Clear();
//
//        maTransform_s trans;
//        for (i = 0; i < ma.transforms.Num(); i++) {
//            trans = ma.transforms.GetIndex(i);
//            Mem_Free(trans);
//        }
//        ma.transforms.Clear();
//
//        maFileNode_t fileNode;
//        for (i = 0; i < ma.fileNodes.Num(); i++) {
//            fileNode = ma.fileNodes.GetIndex(i);
//            Mem_Free(fileNode);
//        }
//        ma.fileNodes.Clear();
//
//        maMaterialNode_s matNode;
//        for (i = 0; i < ma.materialNodes.Num(); i++) {
//            matNode = ma.materialNodes.GetIndex(i);
//            Mem_Free(matNode);
//        }
        ma.materialNodes.clear()
        //	delete ma;
    }

    /*
     ===============================================================================

     MA loader. (Maya Ascii Format)

     ===============================================================================
     */
    internal class maNodeHeader_t {
        //	char					name[128];
        var name: String? = null

        //	char					parent[128];
        var parent: String? = null
    }

    internal class maAttribHeader_t {
        //	char					name[128];
        var name: String? = null
        var size = 0
    }

    internal class maTransform_s {
        var parent: maTransform_s? = null
        val rotate: idVec3? = idVec3()
        val scale: idVec3? = idVec3()
        val translate: idVec3? = idVec3()
    }

    internal class maFace_t {
        var edge: IntArray? = IntArray(3)
        var tVertexNum: IntArray? = IntArray(3)
        var vertexColors: IntArray? = IntArray(3)
        val vertexNormals: Array<idVec3?>? = idVec3.Companion.generateArray(3)
        var vertexNum: IntArray? = IntArray(3)
    }

    internal class maMesh_t {
        var colors: ByteArray?
        var edges: Array<idVec3?>?
        var faces: Array<maFace_t?>?
        var nextNormal = 0
        var nextVertTransformIndex = 0
        var normals: Array<idVec3?>?
        var normalsParsed = false

        //
        //Colors
        var numColors = 0

        //
        //Edges
        var numEdges = 0

        //
        //Faces
        var numFaces = 0

        //
        //Normals
        var numNormals = 0

        //
        //Texture Coordinates
        var numTVertexes = 0
        var numVertTransforms = 0

        //
        //Verts
        var numVertexes = 0

        //Transform to be applied
        var transform: maTransform_s? = null
        var tvertexes: Array<idVec2?>?
        var vertTransforms: Array<idVec4?>?
        var vertexes: Array<idVec3?>?
    }

    internal class maMaterial_t {
        var angle // in clockwise radians
                = 0f

        //	char					name[128];
        var name: String? = null
        var uOffset = 0f
        var vOffset // max lets you offset by material without changing texCoords
                = 0f
        var uTiling = 0f
        var vTiling // multiply tex coords by this
                = 0f
    }

    internal class maObject_t {
        //	char					materialName[128];
        var materialName: String? = null
        var materialRef = 0

        //
        var mesh: maMesh_t? = null

        //	char					name[128];
        var name: String? = null
    }

    internal class maFileNode_t {
        //	char					name[128];
        var name: String? = null

        //	char					path[1024];
        var path: String? = null
    }

    internal class maMaterialNode_s {
        //
        var child: maMaterialNode_s? = null
        var file: maFileNode_t? = null

        //	char					name[128];
        var name: String? = null
    }

    internal class maModel_s {
        //
        //Material Resolution
        var fileNodes: HashMap<String?, maFileNode_t?>? = null
        var materialNodes: HashMap<String?, maMaterialNode_s?>? = null
        val materials: idList<maMaterial_t?>? = idList()
        val objects: idList<maObject_t?>? = idList()
        var   /*ID_TIME_T*/timeStamp: LongArray? = LongArray(1)
        var transforms: HashMap<String?, maTransform_s?>? = null
    }

    // working variables used during parsing
    class ma_t {
        var currentObject: maObject_t? = null
        var model: maModel_s? = null
        var verbose = false
    }
}