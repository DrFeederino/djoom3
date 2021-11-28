package neo.Renderer

import neo.Renderer.Image.textureDepth_t
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token.idToken
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer

/**
 *
 */
object Image_program {
    // we build a canonical token form of the image program here
    val parseBuffer: StringBuffer? = StringBuffer(Image.MAX_IMAGE_NAME)
    private val factors: Array<FloatArray?>? =
        arrayOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f))

    /*
     ===================
     R_LoadImageProgram
     ===================
     */
    @JvmOverloads
    fun R_LoadImageProgram(
        name: String?, width: IntArray?, height: IntArray?,  /*ID_TIME_T */
        timestamps: LongArray?, depth: Array<textureDepth_t?>? = null
    ): ByteBuffer? {
        val src = idLexer()
        val pic = arrayOf<ByteBuffer?>(null)
        src.LoadMemory(name, name.length, name)
        src.SetFlags(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_ALLOWPATHNAMES)
        Image_program.parseBuffer.delete(0, Image_program.parseBuffer.capacity())
        if (timestamps != null) {
            timestamps[0] = 0
        }
        Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)
        src.FreeSource()
        return pic[-+-0]
    }

    /*
     ===================
     R_ParseImageProgram_r

     If pic is NULL, the timestamps will be filled in, but no image will be generated
     If both pic and timestamps are NULL, it will just advance past it, which can be
     used to parse an image program from a text stream.
     ===================
     */
    fun R_ParseImageProgram_r(
        src: idLexer?,
        pic: Array<ByteBuffer?>?,
        width: IntArray?,
        height: IntArray?,
        timestamps: LongArray?,
        depth: Array<textureDepth_t?>?
    ): Boolean {
        val token = idToken()
        val scale: Float
        val timestamp = longArrayOf(0)
        src.ReadToken(token)
        Image_program.AppendToken(token)
        if (0 == token.Icmp("heightmap")) {
            Image_program.MatchAndAppendToken(src, "(")
            if (!Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)) {
                return false
            }
            Image_program.MatchAndAppendToken(src, ",")
            src.ReadToken(token)
            Image_program.AppendToken(token)
            scale = token.GetFloatValue()

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_HeightmapToNormalMap(pic[0], width.get(0), height.get(0), scale)
                if (depth != null) {
                    depth[0] = textureDepth_t.TD_BUMP
                }
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("addnormals")) {
            var pic2: Array<ByteBuffer?>? =
                arrayOf(if (pic != null && pic[0] != null) ByteBuffer.allocate(pic[0].capacity()) else null)
            val width2 = intArrayOf(0)
            val height2 = intArrayOf(0)
            Image_program.MatchAndAppendToken(src, "(")
            if (!Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)) {
                return false
            }
            Image_program.MatchAndAppendToken(src, ",")
            if (!Image_program.R_ParseImageProgram_r(src, pic2, width2, height2, timestamps, depth)) {
                if (pic != null && pic[0] != null) {
                    pic[0].clear() //R_StaticFree(pic);
                }
                return false
            }

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_AddNormalMaps(pic[0], width.get(0), height.get(0), pic2.get(0), width2[0], height2[0])
                //                R_StaticFree(pic2);
                pic2 = null
                if (depth != null) {
                    depth[0] = textureDepth_t.TD_BUMP
                }
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("smoothnormals")) {
            Image_program.MatchAndAppendToken(src, "(")
            if (!Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)) {
                return false
            }
            if (pic != null && pic[0] != null) {
                Image_program.R_SmoothNormalMap(pic[0], width.get(0), height.get(0))
                if (depth != null) {
                    depth[0] = textureDepth_t.TD_BUMP
                }
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("add")) {
            val pic2 = arrayOf(if (pic != null) ByteBuffer.allocate(pic[0].capacity()) else null)
            val width2 = intArrayOf(0)
            val height2 = intArrayOf(0)
            Image_program.MatchAndAppendToken(src, "(")
            if (!Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)) {
                return false
            }
            Image_program.MatchAndAppendToken(src, ",")
            if (!Image_program.R_ParseImageProgram_r(src, pic2, width2, height2, timestamps, depth)) {
                if (pic != null && pic[0] != null) {
                    pic[0].clear() //R_StaticFree(pic[0]);
                }
                return false
            }

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_ImageAdd(pic[0], width.get(0), height.get(0), pic2[0], width2[0], height2[0])
                //                R_StaticFree(pic2);
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("scale")) {
            val scale2 = FloatArray(4)
            var i: Int
            Image_program.MatchAndAppendToken(src, "(")
            Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)
            i = 0
            while (i < 4) {
                Image_program.MatchAndAppendToken(src, ",")
                src.ReadToken(token)
                Image_program.AppendToken(token)
                scale2[i] = token.GetFloatValue()
                i++
            }

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_ImageScale(pic[0], width.get(0), height.get(0), scale2)
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("invertAlpha")) {
            Image_program.MatchAndAppendToken(src, "(")
            Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_InvertAlpha(pic[0], width.get(0), height.get(0))
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("invertColor")) {
            Image_program.MatchAndAppendToken(src, "(")
            Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)

            // process it
            if (pic != null && pic[0] != null) {
                Image_program.R_InvertColor(pic[0], width.get(0), height.get(0))
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("makeIntensity")) {
            var i: Int
            Image_program.MatchAndAppendToken(src, "(")
            Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)

            // copy red to green, blue, and alpha
            if (pic != null && pic[0] != null) {
                val c: Int
                c = width.get(0) * height.get(0) * 4
                pic[0].position(0)
                i = 0
                while (i < c) {
                    val r = pic[0].get(i)
                    val rgba = byteArrayOf(r, r, r, r)
                    pic[0].put(rgba)
                    i += 4
                }
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }
        if (0 == token.Icmp("makeAlpha")) {
            var i: Int
            Image_program.MatchAndAppendToken(src, "(")
            Image_program.R_ParseImageProgram_r(src, pic, width, height, timestamps, depth)

            // average RGB into alpha, then set RGB to white
            if (pic != null && pic[0] != null) {
                val c: Int
                pic[0].position(0)
                c = width.get(0) * height.get(0) * 4
                i = 0
                while (i < c) {
                    val rgb = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte())
                    pic[0].put(i + 3, ((pic[0].get(i + 0) + pic[0].get(i + 1) + pic[0].get(i + 2)) / 3).toByte())
                    pic[0].put(rgb)
                    i += 4
                }
            }
            Image_program.MatchAndAppendToken(src, ")")
            return true
        }

        // if we are just parsing instead of loading or checking,
        // don't do the R_LoadImage
        if (null == timestamps && null == pic) {
            return true
        }

        // load it as an image
        pic.get(0) = Image_files.R_LoadImage(token.toString(), width, height, timestamp, true)
        if (timestamp[0] == -1) {
            return false
        }

        // add this to the timestamp
        if (timestamps != null) {
            if (timestamp[0] > timestamps[0]) {
                timestamps[0] = timestamp[0]
            }
        }
        return true
    }

    /*
     ===================
     AppendToken
     ===================
     */
    fun AppendToken(token: idToken?) {
        // add a leading space if not at the beginning
        if (Image_program.parseBuffer.length > 0) {
//            idStr.Append(parseBuffer, MAX_IMAGE_NAME, " ");
            Image_program.parseBuffer.append(" ")
        }
        //        idStr.Append(parseBuffer, MAX_IMAGE_NAME, token.toString());
        Image_program.parseBuffer.append(token.toString())
    }

    /*
     ===================
     MatchAndAppendToken
     ===================
     */
    fun MatchAndAppendToken(src: idLexer?, match: String?) {
        if (!src.ExpectTokenString(match)) {
            return
        }
        // a matched token won't need a leading space
//        idStr.Append(parseBuffer, MAX_IMAGE_NAME, match);
        Image_program.parseBuffer.append(match)
    }

    /*
     =================
     R_HeightmapToNormalMap

     it is not possible to convert a heightmap into a normal map
     properly without knowing the texture coordinate stretching.
     We can assume constant and equal ST vectors for walls, but not for characters.
     =================
     */
    fun R_HeightmapToNormalMap(data: ByteBuffer?, width: Int, height: Int, scale: Float) {
        var scale = scale
        var i: Int
        var j: Int
        val depth: ByteArray
        scale = scale / 256

        // copy and convert to grey scale
        j = width * height
        depth = ByteArray(j) //R_StaticAlloc(j);
        i = 0
        while (i < j) {
            depth[i] = ((data.get(i * 4) + data.get(i * 4 + 1) + data.get(i * 4 + 2)) / 3).toByte()
            i++
        }
        val dir = idVec3()
        val dir2 = idVec3()
        i = 0
        while (i < height) {
            j = 0
            while (j < width) {
                var d1: Int
                var d2: Int
                var d3: Int
                var d4: Int
                var a1: Int
                var a2: Int
                var a3: Int
                var a4: Int

                // FIXME: look at five points?
                // look at three points to estimate the gradient
                d1 = depth[i * width + j]
                a1 = d1
                d2 = depth[i * width + (j + 1 and width - 1)]
                a2 = d2
                d3 = depth[(i + 1 and height - 1) * width + j]
                a3 = d3
                d4 = depth[(i + 1 and height - 1) * width + (j + 1 and width - 1)]
                a4 = d4
                d2 -= d1
                d3 -= d1
                dir.oSet(0, -d2 * scale)
                dir.oSet(1, -d3 * scale)
                dir.oSet(2, 1f)
                dir.NormalizeFast()
                a1 -= a3
                a4 -= a3
                dir2.oSet(0, -a4 * scale)
                dir2.oSet(1, a1 * scale)
                dir2.oSet(2, 1f)
                dir2.NormalizeFast()
                dir.oPluSet(dir2)
                dir.NormalizeFast()
                a1 = (i * width + j) * 4
                data.put(a1 + 0, (dir.oGet(0) * 127 + 128).toByte())
                data.put(a1 + 1, (dir.oGet(1) * 127 + 128).toByte())
                data.put(a1 + 2, (dir.oGet(2) * 127 + 128).toByte())
                data.put(a1 + 3, 255.toByte())
                j++
            }
            i++
        }

//        R_StaticFree(depth);
    }

    /*
     ================
     R_SmoothNormalMap
     ================
     */
    fun R_SmoothNormalMap(data: ByteBuffer?, width: Int, height: Int) {
        val orig: ByteArray
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        val normal = idVec3()
        var out: Int
        orig = ByteArray(width * height * 4) // R_StaticAlloc(width * height * 4);
        //	memcpy( orig, data, width * height * 4 );
        System.arraycopy(data.array(), 0, orig, 0, width * height * 4)
        i = 0
        while (i < width) {
            j = 0
            while (j < height) {
                normal.oSet(Vector.getVec3_origin())
                k = -1
                while (k < 2) {
                    l = -1
                    while (l < 2) {
                        var `in`: Int
                        `in` =  /*orig +*/((j + l and height - 1) * width + (i + k and width - 1)) * 4

                        // ignore 000 and -1 -1 -1
                        if (orig[`in` + 0] == 0 && orig[`in` + 1] == 0 && orig[`in` + 2] == 0) {
                            l++
                            continue
                        }
                        if (orig[`in` + 0] == 128 && orig[`in` + 1] == 128 && orig[`in` + 2] == 128) {
                            l++
                            continue
                        }
                        normal.oPluSet(0, Image_program.factors[k + 1][l + 1] * (orig[`in` + 0] - 128))
                        normal.oPluSet(1, Image_program.factors[k + 1][l + 1] * (orig[`in` + 1] - 128))
                        normal.oPluSet(2, Image_program.factors[k + 1][l + 1] * (orig[`in` + 2] - 128))
                        l++
                    }
                    k++
                }
                normal.Normalize()
                out =  /*data +*/(j * width + i) * 4
                data.put(out + 0, (128 + 127 * normal.oGet(0)).toByte())
                data.put(out + 1, (128 + 127 * normal.oGet(1)).toByte())
                data.put(out + 2, (128 + 127 * normal.oGet(2)).toByte())
                j++
            }
            i++
        }

//        R_StaticFree(orig);
    }

    /*
     ===================
     R_ImageAdd

     ===================
     */
    fun R_ImageAdd(data1: ByteBuffer?, width1: Int, height1: Int, data2: ByteBuffer?, width2: Int, height2: Int) {
        var i: Int
        var j: Int
        val c: Int
        val newMap: ByteArray?

        // resample pic2 to the same size as pic1
        if (width2 != width1 || height2 != height1) {
            newMap = Image_process.R_Dropsample(data2, width2, height2, width1, height1)
            data2.put(newMap) //TODO:not overwrite reference. EDIT:is this enough?
        } else {
            newMap = null
        }
        c = width1 * height1 * 4
        i = 0
        while (i < c) {
            j = data1.get(i) + data2.get(i)
            if (j > 255) {
                j = 255
            }
            data1.put(i, j.toByte())
            i++
        }

//        if (newMap != null) {
//            R_StaticFree(newMap);
//        }
    }

    /*
     =================
     R_ImageScale
     =================
     */
    fun R_ImageScale(data: ByteBuffer?, width: Int, height: Int, scale: FloatArray? /*[4]*/) {
        var i: Int
        var j: Int
        val c: Int
        c = width * height * 4
        i = 0
        while (i < c) {
            j = (data.get(i) * scale.get(i and 3)).toInt().toByte().toInt()
            if (j < 0) {
                j = 0
            } else if (j > 255) {
                j = 255
            }
            data.put(i, j.toByte())
            i++
        }
    }

    /*
     =================
     R_InvertAlpha
     =================
     */
    fun R_InvertAlpha(data: ByteBuffer?, width: Int, height: Int) {
        var i: Int
        val c: Int
        c = width * height * 4
        i = 0
        while (i < c) {
            data.put(i + 3, (255 - data.get(i + 3)).toByte())
            i += 4
        }
    }

    /*
     =================
     R_InvertColor
     =================
     */
    fun R_InvertColor(data: ByteBuffer?, width: Int, height: Int) {
        var i: Int
        val c: Int
        c = width * height * 4
        i = 0
        while (i < c) {
            data.put(i + 0, (255 - data.get(i + 0)).toByte())
            data.put(i + 1, (255 - data.get(i + 1)).toByte())
            data.put(i + 2, (255 - data.get(i + 2)).toByte())
            i += 4
        }
    }

    /*
     ===================
     R_AddNormalMaps

     ===================
     */
    fun R_AddNormalMaps(data1: ByteBuffer?, width1: Int, height1: Int, data2: ByteBuffer?, width2: Int, height2: Int) {
        var data2 = data2
        var i: Int
        var j: Int
        val newMap: ByteArray?

        // resample pic2 to the same size as pic1
        if (width2 != width1 || height2 != height1) {
            newMap = Image_process.R_Dropsample(data2, width2, height2, width1, height1)
            data2 = BufferUtils.createByteBuffer(newMap.size).put(newMap).flip()
        } else {
            newMap = null
        }

        // add the normal change from the second and renormalize
        i = 0
        while (i < height1) {
            j = 0
            while (j < width1) {
                var d1: Int
                var d2: Int
                val n = idVec3()
                var len: Float
                d1 =  /* data1 + */(i * width1 + j) * 4
                d2 =  /*data2 + */(i * width1 + j) * 4
                n.oSet(0, (data1.get(d1 + 0) - 128) / 127.0f)
                n.oSet(1, (data1.get(d1 + 1) - 128) / 127.0f)
                n.oSet(2, (data1.get(d1 + 2) - 128) / 127.0f)

                // There are some normal maps that blend to 0,0,0 at the edges
                // this screws up compression, so we try to correct that here by instead fading it to 0,0,1
                len = n.LengthFast()
                if (len < 1.0f) {
                    n.oSet(2, idMath.Sqrt(1.0f - n.oGet(0) * n.oGet(0) - n.oGet(1) * n.oGet(1)))
                }
                n.oPluSet(0, (data2.get(d2 + 0) - 128) / 127.0f)
                n.oPluSet(1, (data2.get(d2 + 1) - 128) / 127.0f)
                n.Normalize()
                data1.put(d1 + 0, (n.oGet(0) * 127 + 128).toByte())
                data1.put(d1 + 1, (n.oGet(1) * 127 + 128).toByte())
                data1.put(d1 + 2, (n.oGet(2) * 127 + 128).toByte())
                data1.put(d1 + 3, 255.toByte())
                j++
            }
            i++
        }

//        if (newMap != null) {
//            R_StaticFree(newMap);
//        }
    }

    /*
     ===================
     R_ParsePastImageProgram
     ===================
     */
    fun R_ParsePastImageProgram(src: idLexer?): String? {
        Image_program.parseBuffer.delete(0, Image_program.parseBuffer.capacity())
        Image_program.R_ParseImageProgram_r(src, null, null, null, null, null)
        return String(Image_program.parseBuffer)
    }
}