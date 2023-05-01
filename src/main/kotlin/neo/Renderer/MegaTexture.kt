package neo.Renderer

import neo.Renderer.Image.*
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.Model.srfTriangles_s
import neo.TempDump
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Vector.idVec3
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.GL11
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.*

/**
 *
 */
object MegaTexture {
    const val MAX_LEVELS = 12
    const val MAX_LEVEL_WIDTH = 512
    const val MAX_MEGA_CHANNELS = 3 // normal, diffuse, specular
    const val TILE_PER_LEVEL = 4
    const val TILE_SIZE = MAX_LEVEL_WIDTH / TILE_PER_LEVEL
    val colors /*[8][4]*/: Array<ShortArray> = arrayOf(
        shortArrayOf(0, 0, 0, 55),
        shortArrayOf(255, 0, 0, 255),
        shortArrayOf(0, 255, 0, 255),
        shortArrayOf(255, 255, 0, 255),
        shortArrayOf(0, 0, 255, 255),
        shortArrayOf(255, 0, 255, 255),
        shortArrayOf(0, 255, 255, 255),
        shortArrayOf(255, 255, 255, 255)
    )
    var fillColor: fillColors = fillColors()

    /*

     allow sparse population of the upper detail tiles

     */
    fun RoundDownToPowerOfTwo(num: Int): Int {
        var pot: Int
        pot = 1
        while (pot * 2 <= num) {
            pot = pot shl 1
        }
        return pot
    }

    fun ReadByte(f: idFile): Byte {
        val b = ByteBuffer.allocate(1)
        f.Read(b, 1)
        return b.get()
    }

    fun ReadShort(f: idFile): Short {
        val b = ByteBuffer.allocate(2)
        f.Read(b, 2)

//        return (short) (b[0] + (b[1] << 8));
        return b.short
    }

    internal class idTextureTile {
        var x = 0
        var y = 0

        companion object {
            @Transient
            val SIZE = (Integer.SIZE
                    + Integer.SIZE)
        }
    }

    internal class idTextureLevel {
        //
        var image: idImage = idImage()
        var mega: idMegaTexture = idMegaTexture()
        var tileMap: Array<ArrayList<idTextureTile>> =
            Array(TILE_PER_LEVEL) { ArrayList<idTextureTile>(TILE_PER_LEVEL) }

        //
        var tileOffset = 0
        var tilesHigh = 0
        var tilesWide = 0

        //
        val parms = BufferUtils.createFloatBuffer(4)

        //
        /*
         ====================
         UpdateForCenter

         Center is in the 0.0 to 1.0 range
         ====================
         */
        fun UpdateForCenter(center: FloatArray /*[2]*/) {
            val globalTileCorner = IntArray(2)
            val localTileOffset = IntArray(2)
            if (tilesWide <= TILE_PER_LEVEL && tilesHigh <= TILE_PER_LEVEL) {
                globalTileCorner[0] = 0
                globalTileCorner[1] = 0
                localTileOffset[0] = 0
                localTileOffset[1] = 0
                // orient the mask so that it doesn't mask anything at all
                parms.put(0, 0.25f)
                parms.put(1, 0.25f)
                parms.put(3, 0.25f)
            } else {
                for (i in 0..1) {
                    val global = FloatArray(2)

                    // this value will be outside the 0.0 to 1.0 range unless
                    // we are in the corner of the megaTexture
                    global[i] = (center[i] * parms[3] - 0.5f) * TILE_PER_LEVEL
                    globalTileCorner[i] = (global[i] + 0.5).toInt()
                    localTileOffset[i] = globalTileCorner[i] and TILE_PER_LEVEL - 1

                    // scaling for the mask texture to only allow the proper window
                    // of tiles to show through
                    parms.put(i, -globalTileCorner[i] / TILE_PER_LEVEL.toFloat())
                }
            }
            image!!.Bind()
            for (x in 0 until TILE_PER_LEVEL) {
                for (y in 0 until TILE_PER_LEVEL) {
                    val globalTile = IntArray(2)
                    globalTile[0] = globalTileCorner[0] + (x - localTileOffset[0] and TILE_PER_LEVEL - 1)
                    globalTile[1] = globalTileCorner[1] + (y - localTileOffset[1] and TILE_PER_LEVEL - 1)
                    UpdateTile(x, y, globalTile[0], globalTile[1])
                }
            }
        }

        /*
         ====================
         UpdateTile

         A local tile will only be mapped to globalTile[ localTile + X * TILE_PER_LEVEL ] for some x
         ====================
         */
        fun UpdateTile(localX: Int, localY: Int, globalX: Int, globalY: Int) {
            val tile = tileMap[localX][localY]
            if (tile.x == globalX && tile.y == globalY) {
                return
            }
            if (globalX and TILE_PER_LEVEL - 1 != localX || globalY and TILE_PER_LEVEL - 1 != localY) {
                Common.common.Error("idTextureLevel::UpdateTile: bad coordinate mod")
            }
            tile.x = globalX
            tile.y = globalY
            val data = ByteBuffer.allocate(TILE_SIZE * TILE_SIZE * 4)
            if (globalX >= tilesWide || globalX < 0 || globalY >= tilesHigh || globalY < 0) {
                // off the map
//		memset( data, 0, sizeof( data ) );
            } else {
                // extract the data from the full image (FIXME: background load from disk)
                val tileNum = tileOffset + tile.y * tilesWide + tile.x
                val tileSize = TILE_SIZE * TILE_SIZE * 4
                mega.fileHandle!!.Seek((tileNum * tileSize).toLong(), fsOrigin_t.FS_SEEK_SET)
                //		memset( data, 128, sizeof( data ) );
                Arrays.fill(data.array(), 128.toByte())
                mega.fileHandle!!.Read(data, tileSize)
            }
            if (idMegaTexture.r_showMegaTextureLabels.GetBool()) {
                // put a color marker in it
                val color /*[4]*/ = byteArrayOf(
                    (255 * localX / TILE_PER_LEVEL).toByte(),
                    (255 * localY / TILE_PER_LEVEL).toByte(),
                    0,
                    0
                )
                for (x in 0..7) {
                    for (y in 0..7) {
//				*(int *)&data[ ( ( y + TILE_SIZE/2 - 4 ) * TILE_SIZE + x + TILE_SIZE/2 - 4 ) * 4 ] = *(int *)color;
                        System.arraycopy(
                            color,
                            0,
                            data.array(),
                            ((y + TILE_SIZE / 2 - 4) * TILE_SIZE + x + TILE_SIZE / 2 - 4) * 4,
                            4
                        )
                    }
                }
            }

            // upload all the mip-map levels
            var level = 0
            var size = TILE_SIZE
            while (true) {
                qgl.qglTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    level,
                    localX * size,
                    localY * size,
                    size,
                    size,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    data
                )
                size = size shr 1
                level++
                if (size == 0) {
                    break
                }
                val byteSize = size * 4
                // mip-map in place
                for (y in 0 until size) {
                    val `in` = ByteArray(data.capacity() - y * size * 16)
                    val in2 = ByteArray(`in`.size - size * 8)
                    val out = ByteArray(data.capacity() - y * size * 4)
                    //			in = data + y * size * 16;
//			in2 = in + size * 8;
//			out = data + y * size * 4;
                    System.arraycopy(data.array(), y * size * 16, `in`, 0, `in`.size)
                    System.arraycopy(`in`, size * 8, in2, 0, in2.size)
                    System.arraycopy(data.array(), y * size * 4, out, 0, out.size)
                    for (x in 0 until size) {
                        out[x * 4 + 0] =
                            (`in`[x * 8 + 0] + `in`[x * 8 + 4 + 0] + in2[x * 8 + 0] + in2[x * 8 + 4 + 0] shr 2).toByte()
                        out[x * 4 + 1] =
                            (`in`[x * 8 + 1] + `in`[x * 8 + 4 + 1] + in2[x * 8 + 1] + in2[x * 8 + 4 + 1] shr 2).toByte()
                        out[x * 4 + 2] =
                            (`in`[x * 8 + 2] + `in`[x * 8 + 4 + 2] + in2[x * 8 + 2] + in2[x * 8 + 4 + 2] shr 2).toByte()
                        out[x * 4 + 3] =
                            (`in`[x * 8 + 3] + `in`[x * 8 + 4 + 3] + in2[x * 8 + 3] + in2[x * 8 + 4 + 3] shr 2).toByte()
                    }
                }
            }
        }

        /*
         =====================
         Invalidate

         Forces all tiles to be regenerated
         =====================
         */
        fun Invalidate() {
            for (x in 0 until TILE_PER_LEVEL) {
                for (y in 0 until TILE_PER_LEVEL) {
                    tileMap[x][y].y = -99999
                    tileMap[x][y].x = tileMap[x][y].y
                }
            }
        }

        companion object {
            @Transient
            val SIZE = (TempDump.CPP_class.Pointer.SIZE //idMegaTexture * mega
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + TempDump.CPP_class.Pointer.SIZE //idImage * image
                    + idTextureTile.SIZE * TILE_PER_LEVEL * TILE_PER_LEVEL)
        }
    }

    internal class megaTextureHeader_t : Serializable {
        var tileSize = 0
        var tilesHigh = 0
        var tilesWide = 0

        companion object {
            @Transient
            val BYTES = Integer.BYTES * 3
            fun ReadDdsFileHeader_t(): ByteBuffer {
                return ByteBuffer.allocate(BYTES)
            }

            fun ReadDdsFileHeader_t(buffer: ByteBuffer): megaTextureHeader_t {
                val t = megaTextureHeader_t()
                t.tileSize = buffer.int
                t.tilesWide = buffer.int
                t.tilesHigh = buffer.int
                return t
            }

            fun WriteDdsFileHeader_t(ev: megaTextureHeader_t): ByteBuffer {
                val buffer = ReadDdsFileHeader_t()
                buffer.putInt(ev.tileSize).putInt(ev.tilesWide).putInt(ev.tilesHigh).flip()
                return buffer
            }
        }
    }

    class idMegaTexture {
        companion object {
            @Transient
            val SIZE: Int = (TempDump.CPP_class.Pointer.SIZE //idFile fileHandle
                    + TempDump.CPP_class.Pointer.SIZE //srfTriangles_s currentTriMapping
                    + idVec3.Companion.SIZE
                    + java.lang.Float.SIZE * 2 * 4
                    + Integer.SIZE
                    + idTextureLevel.SIZE * MAX_LEVELS
                    + megaTextureHeader_t.BYTES)
            private val parms /*[4]*/ = BufferUtils.createFloatBuffer(4) // no contribution

            //
            private val r_megaTextureLevel: idCVar = idCVar(
                "r_megaTextureLevel",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER,
                "draw only a specific level"
            )
            private val r_showMegaTexture: idCVar = idCVar(
                "r_showMegaTexture",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_BOOL,
                "display all the level images"
            )
            val r_showMegaTextureLabels: idCVar = idCVar(
                "r_showMegaTextureLabels",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_BOOL,
                "draw colored blocks in each tile"
            )
            private val r_skipMegaTexture: idCVar = idCVar(
                "r_skipMegaTexture",
                "0",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER,
                "only use the lowest level image"
            )
            private val r_terrainScale: idCVar = idCVar(
                "r_terrainScale",
                "3",
                CVarSystem.CVAR_RENDERER or CVarSystem.CVAR_INTEGER,
                "vertically scale USGS data"
            )

            private fun GenerateMegaMipMaps(header: megaTextureHeader_t, outFile: idFile) {
                outFile.Flush()

                // out fileSystem doesn't allow read / write access...
                val inFile = FileSystem_h.fileSystem.OpenFileRead(outFile.GetName())!!
                var tileOffset = 1
                var width = header.tilesWide
                var height = header.tilesHigh
                val tileSize = header.tileSize * header.tileSize * 4
                val oldBlock = ByteArray(tileSize)
                val newBlock = ByteArray(tileSize)
                while (width > 1 || height > 1) {
                    var newHeight = height + 1 shr 1
                    if (newHeight < 1) {
                        newHeight = 1
                    }
                    val newWidth = width + 1 shr 1
                    if (width < 1) {
                        width = 1
                    }
                    Common.common.Printf("generating %d x %d block mip level\n", newWidth, newHeight)
                    var tileNum: Int
                    for (y in 0 until newHeight) {
                        Common.common.Printf("row %d\n", y)
                        Session.Companion.session.UpdateScreen()
                        for (x in 0 until newWidth) {
                            // mip map four original blocks down into a single new block
                            for (yy in 0..1) {
                                for (xx in 0..1) {
                                    val tx = x * 2 + xx
                                    val ty = y * 2 + yy
                                    if (tx > width || ty > height) {
                                        // off edge, zero fill
//							memset( newBlock, 0, sizeof( newBlock ) );
                                    } else {
                                        tileNum = tileOffset + ty * width + tx
                                        inFile.Seek((tileNum * tileSize).toLong(), fsOrigin_t.FS_SEEK_SET)
                                        inFile.Read(ByteBuffer.wrap(oldBlock), tileSize)
                                    }
                                    // mip map the new pixels
                                    for (yyy in 0 until TILE_SIZE / 2) {
                                        for (xxx in 0 until TILE_SIZE / 2) {
                                            val `in` = (yyy * 2 * TILE_SIZE + xxx * 2) * 4
                                            val out =
                                                ((TILE_SIZE / 2 * yy + yyy) * TILE_SIZE + TILE_SIZE / 2 * xx + xxx) * 4
                                            newBlock[out + 0] =
                                                (oldBlock[`in` + 0] + oldBlock[`in` + 4] + oldBlock[`in` + 0 + TILE_SIZE * 4] + oldBlock[`in` + 4 + TILE_SIZE * 4] shr 2).toByte()
                                            newBlock[out + 1] =
                                                (oldBlock[`in` + 1] + oldBlock[`in` + 5] + oldBlock[`in` + 1 + TILE_SIZE * 4] + oldBlock[`in` + 5 + TILE_SIZE * 4] shr 2).toByte()
                                            newBlock[out + 2] =
                                                (oldBlock[`in` + 2] + oldBlock[`in` + 6] + oldBlock[`in` + 2 + TILE_SIZE * 4] + oldBlock[`in` + 6 + TILE_SIZE * 4] shr 2).toByte()
                                            newBlock[out + 3] =
                                                (oldBlock[`in` + 3] + oldBlock[`in` + 7] + oldBlock[`in` + 3 + TILE_SIZE * 4] + oldBlock[`in` + 7 + TILE_SIZE * 4] shr 2).toByte()
                                        }
                                    }

                                    // write the block out
                                    tileNum = tileOffset + width * height + y * newWidth + x
                                    outFile.Seek((tileNum * tileSize).toLong(), fsOrigin_t.FS_SEEK_SET)
                                    outFile.Write(ByteBuffer.wrap(newBlock), tileSize)
                                }
                            }
                        }
                    }
                    tileOffset += width * height
                    width = newWidth
                    height = newHeight
                }

//	delete inFile;
            }

            /*
         ====================
         GenerateMegaPreview

         Make a 2k x 2k preview image for a mega texture that can be used in modeling programs
         ====================
         */
            private fun GenerateMegaPreview(fileName: String) {
                val fileHandle = FileSystem_h.fileSystem.OpenFileRead(fileName)
                if (null == fileHandle) {
                    Common.common.Printf("idMegaTexture: failed to open %s\n", fileName)
                    return
                }
                val outName = idStr(fileName)
                outName.StripFileExtension()
                outName.plusAssign("_preview.tga")
                Common.common.Printf("Creating %s.\n", outName.toString())
                val header: megaTextureHeader_t?
                val headerBuffer = megaTextureHeader_t.ReadDdsFileHeader_t()
                fileHandle.Read(headerBuffer)
                header = megaTextureHeader_t.ReadDdsFileHeader_t(headerBuffer)
                if (header.tileSize < 64 || header.tilesWide < 1 || header.tilesHigh < 1) {
                    Common.common.Printf("idMegaTexture: bad header on %s\n", fileName)
                    return
                }
                val tileSize = header.tileSize
                var width = header.tilesWide
                var height = header.tilesHigh
                var tileOffset = 1
                val tileBytes = tileSize * tileSize * 4
                // find the level that fits
                while (width * tileSize > 2048 || height * tileSize > 2048) {
                    tileOffset += width * height
                    width = width shr 1
                    if (width < 1) {
                        width = 1
                    }
                    height = height shr 1
                    if (height < 1) {
                        height = 1
                    }
                }
                val pic = ByteBuffer.allocate(width * height * tileBytes) // R_StaticAlloc(width * height * tileBytes);
                val oldBlock = ByteBuffer.allocate(tileBytes)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val tileNum = tileOffset + y * width + x
                        fileHandle.Seek((tileNum * tileBytes).toLong(), fsOrigin_t.FS_SEEK_SET)
                        fileHandle.Read(oldBlock, tileBytes)
                        for (yy in 0 until tileSize) {
//				memcpy( pic + ( ( y * tileSize + yy ) * width * tileSize + x * tileSize  ) * 4,
//					oldBlock + yy * tileSize * 4, tileSize * 4 );
                            pic.position(((y * tileSize + yy) * width * tileSize + x * tileSize) * 4)
                            pic.put(oldBlock.array(), yy * tileSize * 4, tileSize * 4)
                        }
                    }
                }
                Image_files.R_WriteTGA(outName.toString(), pic, width * tileSize, height * tileSize, false)

//            R_StaticFree(pic);
//	delete fileHandle;
            }

            init {
                parms.put(floatArrayOf(-2f, -2f, 0f, 1f)).rewind()
            }
        }

        private val levels: ArrayList<idTextureLevel> =
            ArrayList<idTextureLevel>(MAX_LEVELS) // 0 is the highest resolution

        //
        private val localViewToTextureCenter: Array<FloatArray> = Array(2) { FloatArray(4) }

        //
        private var currentTriMapping: srfTriangles_s? = null

        //
        private val currentViewOrigin: idVec3 = idVec3()
        var fileHandle: idFile? = null

        //
        //
        private lateinit var header: megaTextureHeader_t

        //
        private var numLevels = 0
        fun InitFromMegaFile(fileBase: String): Boolean {
            val name = idStr("megaTextures/$fileBase")
            name.StripFileExtension()
            name.Append(".mega")
            var width: Int
            var height: Int
            fileHandle = FileSystem_h.fileSystem.OpenFileRead(name.toString())
            if (null == fileHandle) {
                Common.common.Printf("idMegaTexture: failed to open %s\n", name)
                return false
            }
            val headerBuffer = megaTextureHeader_t.ReadDdsFileHeader_t()
            fileHandle!!.Read(headerBuffer)
            header = megaTextureHeader_t.ReadDdsFileHeader_t(headerBuffer)
            if (header.tileSize < 64 || header.tilesWide < 1 || header.tilesHigh < 1) {
                Common.common.Printf("idMegaTexture: bad header on %s\n", name)
                return false
            }
            currentTriMapping = null
            numLevels = 0
            width = header.tilesWide
            height = header.tilesHigh
            var tileOffset = 1 // just past the header

//	memset( levels, 0, sizeof( levels ) );
            while (true) {
                val level = levels[numLevels]
                level.mega = this
                level.tileOffset = tileOffset
                level.tilesWide = width
                level.tilesHigh = height
                level.parms.put(0, -1f) // initially mask everything
                level.parms.put(1, 0f)
                level.parms.put(2, 0f)
                level.parms.put(3, width.toFloat() / TILE_PER_LEVEL)
                level.Invalidate()
                tileOffset += level.tilesWide * level.tilesHigh
                val str = String.format("MEGA_%s_%d", fileBase, numLevels)

                // give each level a default fill color
                for (i in 0..3) {
                    fillColor.setColor(i, colors[numLevels + 1][i])
                }
                levels[numLevels].image = Image.globalImages.ImageFromFunction(str, R_EmptyLevelImage.getInstance())
                numLevels++
                if (width <= TILE_PER_LEVEL && height <= TILE_PER_LEVEL) {
                    break
                }
                width = width + 1 shr 1
                height = height + 1 shr 1
            }

            // force first bind to load everything
            currentViewOrigin[0] = -99999999.0f
            currentViewOrigin[1] = -99999999.0f
            currentViewOrigin[2] = -99999999.0f
            return true
        }

        /*
         ====================
         SetMappingForSurface

         analyzes xyz and st to create a mapping
         This is not very robust, but works for rectangular grids
         ====================
         */
        fun SetMappingForSurface(tri: srfTriangles_s) {    // analyzes xyz and st to create a mapping
            if (tri == currentTriMapping) {
                return
            }
            currentTriMapping = tri
            if (null == tri.verts) {
                return
            }
            var origin: idDrawVert = idDrawVert()
            val axis = Array<idDrawVert>(2) { idDrawVert() }
            origin.st[0] = 1.0f
            origin.st[1] = 1.0f
            axis[0].st[0] = 0f
            axis[0].st[1] = 1f
            axis[1].st[0] = 1f
            axis[1].st[1] = 0f
            for (i in 0 until tri.numVerts) {
                val v = tri.verts!![i]!!
                if (v.st[0] <= origin.st[0] && v.st[1] <= origin.st[1]) {
                    origin = v
                }
                if (v.st[0] >= axis[0].st[0] && v.st[1] <= axis[0].st[1]) {
                    axis[0] = v
                }
                if (v.st[0] <= axis[1].st[0] && v.st[1] >= axis[1].st[1]) {
                    axis[1] = v
                }
            }
            for (i in 0..1) {
                val dir = idVec3(axis[i].xyz.minus(origin.xyz))
                val texLen = axis[i].st[i] - origin.st[i]
                val spaceLen = axis[i].xyz.minus(origin.xyz).Length()
                val scale = texLen / (spaceLen * spaceLen)
                dir.timesAssign(scale)
                val c = origin.xyz.times(dir) - origin.st[i]
                localViewToTextureCenter[i][0] = dir[0]
                localViewToTextureCenter[i][1] = dir[1]
                localViewToTextureCenter[i][2] = dir[2]
                localViewToTextureCenter[i][3] = -c
            }
        }

        fun BindForViewOrigin(viewOrigin: idVec3) {    // binds images and sets program parameters
            SetViewOrigin(viewOrigin)

            // borderClamp image goes in texture 0
            tr_backend.GL_SelectTexture(0)
            Image.globalImages.borderClampImage.Bind()

            // level images in higher textures, blurriest first
            for (i in 0..6) {
                tr_backend.GL_SelectTexture(1 + i)
                if (i >= numLevels) {
                    Image.globalImages.whiteImage!!.Bind()
                    qgl.qglProgramLocalParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, i, parms)
                } else {
                    val level = levels[numLevels - 1 - i]
                    if (r_showMegaTexture.GetBool()) {
                        if (i and 1 == 1) {
                            Image.globalImages.blackImage.Bind()
                        } else {
                            Image.globalImages.whiteImage!!.Bind()
                        }
                    } else {
                        level.image.Bind()
                    }
                    qgl.qglProgramLocalParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, i, level.parms)
                }
            }
            val parms = BufferUtils.createFloatBuffer(4)
            parms.put(0, 0f)
            parms.put(1, 0f)
            parms.put(2, 0f)
            parms.put(3, 1f)
            qgl.qglProgramLocalParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 7, parms)
            parms.put(0, 1f)
            parms.put(1, 1f)
            parms.put(2, r_terrainScale.GetFloat())
            parms.put(3, 1f)
            qgl.qglProgramLocalParameter4fvARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, 8, parms)
        }

        //// private:
        //// friend class idTextureLevel;
        /*
         ====================
         Unbind

         This can go away once everything uses fragment programs so the enable states don't
         need tracking
         ====================
         */
        fun Unbind() {                                // removes texture bindings
            for (i in 0 until numLevels) {
                tr_backend.GL_SelectTexture(1 + i)
                Image.globalImages.BindNull()
            }
        }

        private fun SetViewOrigin(viewOrigin: idVec3) {
            if (r_showMegaTextureLabels.IsModified()) {
                r_showMegaTextureLabels.ClearModified()
                currentViewOrigin[0] = viewOrigin[0] + 0.1f // force a change
                for (i in 0 until numLevels) {
                    levels[i].Invalidate()
                }
            }
            if (viewOrigin === currentViewOrigin) {
                return
            }
            if (r_skipMegaTexture.GetBool()) {
                return
            }
            currentViewOrigin.set(viewOrigin)
            val texCenter = FloatArray(2)

            // convert the viewOrigin to a texture center, which will
            // be a different conversion for each megaTexture
            for (i in 0..1) {
                texCenter[i] =
                    viewOrigin[0] * localViewToTextureCenter[i][0] + viewOrigin[1] * localViewToTextureCenter[i][1] + viewOrigin[2] * localViewToTextureCenter[i][2] + localViewToTextureCenter[i][3]
            }
            for (i in 0 until numLevels) {
                levels[i].UpdateForCenter(texCenter)
            }
        }

        /*
         ====================
         MakeMegaTexture_f

         Incrementally load a giant tga file and process into the mega texture block format
         ====================
         */
        class MakeMegaTexture_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                val columns: Int
                val rows: Int
                val fileSize: Int
                val numBytes: Int
                var pixbuf: Int
                var row: Int
                var column: Int
                val targa_header = _TargaHeader()
                if (args.Argc() != 2) {
                    Common.common.Printf("USAGE: makeMegaTexture <filebase>\n")
                    return
                }
                val name_s = idStr("megaTextures/" + args.Argv(1))
                name_s.StripFileExtension()
                name_s.Append(".tga")
                val name = name_s.toString()

                //
                // open the file
                //
                Common.common.Printf("Opening %s.\n", name)
                fileSize = FileSystem_h.fileSystem.ReadFile(name, null, null)
                val file = FileSystem_h.fileSystem.OpenFileRead(name)
                if (null == file) {
                    Common.common.Printf("Couldn't open %s\n", name)
                    return
                }
                targa_header.id_length = Char(ReadByte(file).toUShort())
                targa_header.colormap_type = Char(ReadByte(file).toUShort())
                targa_header.image_type = Char(ReadByte(file).toUShort())
                targa_header.colormap_index = ReadShort(file)
                targa_header.colormap_length = ReadShort(file)
                targa_header.colormap_size = Char(ReadByte(file).toUShort())
                targa_header.x_origin = ReadShort(file)
                targa_header.y_origin = ReadShort(file)
                targa_header.width = ReadShort(file)
                targa_header.height = ReadShort(file)
                targa_header.pixel_size = Char(ReadByte(file).toUShort())
                targa_header.attributes = Char(ReadByte(file).toUShort())
                if (targa_header.image_type.code != 2 && targa_header.image_type.code != 10 && targa_header.image_type.code != 3) {
                    Common.common.Error(
                        "LoadTGA( %s ): Only type 2 (RGB), 3 (gray), and 10 (RGB) TGA images supported\n",
                        name
                    )
                }
                if (targa_header.colormap_type.code != 0) {
                    Common.common.Error("LoadTGA( %s ): colormaps not supported\n", name)
                }
                if (targa_header.pixel_size.code != 32 && targa_header.pixel_size.code != 24 && targa_header.image_type.code != 3) {
                    Common.common.Error("LoadTGA( %s ): Only 32 or 24 bit images supported (no colormaps)\n", name)
                }
                if (targa_header.image_type.code == 2 || targa_header.image_type.code == 3) {
                    numBytes = targa_header.width * targa_header.height * (targa_header.pixel_size.code shr 3)
                    if (numBytes > fileSize - 18 - targa_header.id_length.code) {
                        Common.common.Error("LoadTGA( %s ): incomplete file\n", name)
                    }
                }
                columns = targa_header.width.toInt()
                rows = targa_header.height.toInt()

                // skip TARGA image comment
                if (targa_header.id_length.code != 0) {
                    file.Seek(targa_header.id_length.code.toLong(), fsOrigin_t.FS_SEEK_CUR)
                }
                val mtHeader = megaTextureHeader_t()
                mtHeader.tileSize = TILE_SIZE
                mtHeader.tilesWide =
                    RoundDownToPowerOfTwo(targa_header.width.toInt()) / TILE_SIZE
                mtHeader.tilesHigh =
                    RoundDownToPowerOfTwo(targa_header.height.toInt()) / TILE_SIZE
                val outName = idStr(name)
                outName.StripFileExtension()
                outName.Append(".mega")
                Common.common.Printf(
                    "Writing %d x %d size %d tiles to %s.\n",
                    mtHeader.tilesWide, mtHeader.tilesHigh, mtHeader.tileSize, outName
                )

                // open the output megatexture file
                val out = FileSystem_h.fileSystem.OpenFileWrite(outName.toString())!!
                out.Write(megaTextureHeader_t.WriteDdsFileHeader_t(mtHeader))
                out.Seek((TILE_SIZE * TILE_SIZE * 4).toLong(), fsOrigin_t.FS_SEEK_SET)

                // we will process this one row of tiles at a time, since the entire thing
                // won't fit in memory
                val targa_rgba =
                    ByteArray(TILE_SIZE * targa_header.width * 4) // R_StaticAlloc(TILE_SIZE * targa_header.width * 4);
                var blockRowsRemaining = mtHeader.tilesHigh
                while (blockRowsRemaining-- != 0) {
                    Common.common.Printf("%d blockRowsRemaining\n", blockRowsRemaining)
                    Session.Companion.session.UpdateScreen()
                    if (targa_header.image_type.code == 2 || targa_header.image_type.code == 3) {
                        // Uncompressed RGB or gray scale image
                        row = 0
                        while (row < TILE_SIZE) {
                            pixbuf = row * columns * 4
                            column = 0
                            while (column < columns) {
                                var red: Byte
                                var green: Byte
                                var blue: Byte
                                var alphabyte: Byte
                                when (targa_header.pixel_size.code) {
                                    8 -> {
                                        blue = ReadByte(file)
                                        green = blue
                                        red = blue
                                        targa_rgba[pixbuf++] = red
                                        targa_rgba[pixbuf++] = green
                                        targa_rgba[pixbuf++] = blue
                                        targa_rgba[pixbuf++] = 255.toByte()
                                    }
                                    24 -> {
                                        blue = ReadByte(file)
                                        green = ReadByte(file)
                                        red = ReadByte(file)
                                        targa_rgba[pixbuf++] = red
                                        targa_rgba[pixbuf++] = green
                                        targa_rgba[pixbuf++] = blue
                                        targa_rgba[pixbuf++] = 255.toByte()
                                    }
                                    32 -> {
                                        blue = ReadByte(file)
                                        green = ReadByte(file)
                                        red = ReadByte(file)
                                        alphabyte = ReadByte(file)
                                        targa_rgba[pixbuf++] = red
                                        targa_rgba[pixbuf++] = green
                                        targa_rgba[pixbuf++] = blue
                                        targa_rgba[pixbuf++] = alphabyte
                                    }
                                    else -> Common.common.Error(
                                        "LoadTGA( %s ): illegal pixel_size '%d'\n",
                                        name,
                                        targa_header.pixel_size
                                    )
                                }
                                column++
                            }
                            row++
                        }
                    } else if (targa_header.image_type.code == 10) {   // Runlength encoded RGB images
                        var red: Byte
                        var green: Byte
                        var blue: Byte
                        var alphabyte: Byte
                        var packetHeader: Int
                        var packetSize: Byte
                        var j: Byte
                        red = 0
                        green = 0
                        blue = 0
                        alphabyte = 0xff.toByte()
                        row = 0
                        while (row < TILE_SIZE) {
                            pixbuf = row * columns * 4
                            column = 0
                            breakOut@ while (column < columns) {
                                packetHeader = ReadByte(file).toInt()
                                packetSize = (1 + (packetHeader and 0x7f)).toByte()
                                if (packetHeader and 0x80 == 0x80) {        // run-length packet
                                    when (targa_header.pixel_size.code) {
                                        24 -> {
                                            blue = ReadByte(file)
                                            green = ReadByte(file)
                                            red = ReadByte(file)
                                            alphabyte = 255.toByte()
                                        }
                                        32 -> {
                                            blue = ReadByte(file)
                                            green = ReadByte(file)
                                            red = ReadByte(file)
                                            alphabyte = ReadByte(file)
                                        }
                                        else -> Common.common.Error(
                                            "LoadTGA( %s ): illegal pixel_size '%d'\n",
                                            name,
                                            targa_header.pixel_size
                                        )
                                    }
                                    j = 0
                                    while (j < packetSize) {
                                        targa_rgba[pixbuf++] = red
                                        targa_rgba[pixbuf++] = green
                                        targa_rgba[pixbuf++] = blue
                                        targa_rgba[pixbuf++] = alphabyte
                                        column++
                                        if (column == columns) { // run spans across rows
                                            Common.common.Error("TGA had RLE across columns, probably breaks block")
                                            column = 0
                                            if (row > 0) {
                                                row--
                                            } else {
                                                break@breakOut
                                            }
                                            pixbuf = row * columns * 4
                                        }
                                        j++
                                    }
                                } else {                            // non run-length packet
                                    j = 0
                                    while (j < packetSize) {
                                        when (targa_header.pixel_size.code) {
                                            24 -> {
                                                blue = ReadByte(file)
                                                green = ReadByte(file)
                                                red = ReadByte(file)
                                                targa_rgba[pixbuf++] = red
                                                targa_rgba[pixbuf++] = green
                                                targa_rgba[pixbuf++] = blue
                                                targa_rgba[pixbuf++] = 255.toByte()
                                            }
                                            32 -> {
                                                blue = ReadByte(file)
                                                green = ReadByte(file)
                                                red = ReadByte(file)
                                                alphabyte = ReadByte(file)
                                                targa_rgba[pixbuf++] = red
                                                targa_rgba[pixbuf++] = green
                                                targa_rgba[pixbuf++] = blue
                                                targa_rgba[pixbuf++] = alphabyte
                                            }
                                            else -> Common.common.Error(
                                                "LoadTGA( %s ): illegal pixel_size '%d'\n",
                                                name,
                                                targa_header.pixel_size
                                            )
                                        }
                                        column++
                                        if (column == columns) { // pixel packet run spans across rows
                                            column = 0
                                            if (row > 0) {
                                                row--
                                            } else {
                                                break@breakOut
                                            }
                                            pixbuf = row * columns * 4
                                        }
                                        j++
                                    }
                                }
                            }
                            row++
                        }
                    }

                    //
                    // write out individual blocks from the full row block buffer
                    //
                    for (rowBlock in 0 until mtHeader.tilesWide) {
                        for (y in 0 until TILE_SIZE) {
                            out.Write(
                                ByteBuffer.wrap(
                                    Arrays.copyOfRange(
                                        targa_rgba,
                                        (y * targa_header.width + rowBlock * TILE_SIZE) * 4,
                                        targa_rgba.size
                                    )
                                ), TILE_SIZE * 4
                            )
                        }
                    }
                }
                //
//                R_StaticFree(targa_rgba);
                GenerateMegaMipMaps(mtHeader, out)

//	delete out;
//	delete file;
                GenerateMegaPreview(outName.toString())
                //if (false){
//	if ( (targa_header.attributes & (1<<5)) ) {			// image flp bit
//		R_VerticalFlip( *pic, *width, *height );
//	}
//}
            }

            companion object {
                private val instance: cmdFunction_t = MakeMegaTexture_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }
    }

    class fillColors {
        var intVal = 0
        fun getColor(index: Int): Byte {
            return (intVal shr index and 0xFF).toByte()
        }

        fun setColor(index: Int, color: Short) {
            val down = 0xFF shl index
            intVal = intVal and down.inv()
            intVal = intVal or (color.toInt() and 0xFF shl index)
        }
    }

    internal class R_EmptyLevelImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage) {
            val c = MAX_LEVEL_WIDTH * MAX_LEVEL_WIDTH
            val data = ByteBuffer.allocate(c * 4)
            for (i in 0 until c) {
                data.putInt(i, fillColor.intVal)
            }

            // FIXME: this won't live past vid mode changes
            image.GenerateImage(
                data,
                MAX_LEVEL_WIDTH,
                MAX_LEVEL_WIDTH,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_EmptyLevelImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    //===================================================================================================
    internal class _TargaHeader {
        var colormap_index: Short = 0
        var colormap_length: Short = 0
        var colormap_size: Char = 0.toChar()
        var id_length: Char = 0.toChar()
        var colormap_type: Char = 0.toChar()
        var image_type: Char = 0.toChar()
        var pixel_size: Char = 0.toChar()
        var attributes: Char = 0.toChar()
        var x_origin: Short = 0
        var y_origin: Short = 0
        var width: Short = 0
        var height: Short = 0
    }
}