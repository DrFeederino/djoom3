package neo.Renderer

import neo.Sound.snd_system
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.Lib
import neo.idlib.Text.Str.idStr
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.experimental.and

/**
 *
 */
object Cinematic {
    const val CIN_hold = 4
    const val CIN_loop = 2
    const val CIN_shader = 16
    const val CIN_silent = 8

    //
    const val CIN_system = 1
    const val DEFAULT_CIN_HEIGHT = 512

    //
    const val DEFAULT_CIN_WIDTH = 512
    const val INPUT_BUF_SIZE = 32768 /* choose an efficiently fread'able size */
    const val MAXSIZE = 8
    const val MINSIZE = 4
    const val ROQ_CODEBOOK = 0x1002

    //
    const val ROQ_FILE = 0x1084
    const val ROQ_PACKET = 0x1030
    const val ROQ_QUAD = 0x1000
    const val ROQ_QUAD_HANG = 0x1013
    const val ROQ_QUAD_INFO = 0x1001
    const val ROQ_QUAD_JPEG = 0x1012
    const val ROQ_QUAD_VQ = 0x1011
    val ROQ_UB_tab: LongArray = LongArray(256)
    val ROQ_UG_tab: LongArray = LongArray(256)
    val ROQ_VG_tab: LongArray = LongArray(256)
    val ROQ_VR_tab: LongArray = LongArray(256)

    //
    // temporary buffers used by all cinematics
    val ROQ_YY_tab: LongArray = LongArray(256)
    const val ZA_SOUND_MONO = 0x1020
    const val ZA_SOUND_STEREO = 0x1021
    var file: IntArray = IntArray(0)
    var vq2: ByteBuffer = ByteBuffer.allocate(1)
    var vq4: ByteBuffer = ByteBuffer.allocate(1)
    var vq8: ByteBuffer = ByteBuffer.allocate(1)
    private fun VQ2TO4(a: ByteBuffer, b: ByteBuffer, c: ByteBuffer, d: ByteBuffer) {
        val aPos = a.position()
        val bPos = b.position()
        c.putShort(a.getShort(aPos + 0))
        c.putShort(a.getShort(aPos + 2))
        c.putShort(b.getShort(bPos + 0))
        c.putShort(b.getShort(bPos + 2))
        d.putShort(a.getShort(aPos + 0))
        d.putShort(a.getShort(aPos + 0))
        d.putShort(a.getShort(aPos + 2))
        d.putShort(a.getShort(aPos + 2))
        d.putShort(b.getShort(bPos + 0))
        d.putShort(b.getShort(bPos + 0))
        d.putShort(b.getShort(bPos + 2))
        d.putShort(b.getShort(bPos + 2))
        d.putShort(a.getShort(aPos + 0))
        d.putShort(a.getShort(aPos + 0))
        d.putShort(a.getShort(aPos + 2))
        d.putShort(a.getShort(aPos + 2))
        d.putShort(b.getShort(bPos + 0))
        d.putShort(b.getShort(bPos + 0))
        d.putShort(b.getShort(bPos + 2))
        d.putShort(b.getShort(bPos + 2))
        a.position(aPos + 4) // += 2;
        b.position(bPos + 4) // += 2;
    }

    private fun VQ2TO4(a: IntBuffer, b: IntBuffer, c: IntBuffer, d: IntBuffer) {
        val aPos = a.position()
        val bPos = b.position()
        c.put(a.get(aPos + 0))
        c.put(a.get(aPos + 1))
        c.put(b.get(bPos + 0))
        c.put(b.get(bPos + 1))
        d.put(a.get(aPos + 0))
        d.put(a.get(aPos + 0))
        d.put(a.get(aPos + 1))
        d.put(a.get(aPos + 1))
        d.put(b.get(bPos + 0))
        d.put(b.get(bPos + 0))
        d.put(b.get(bPos + 1))
        d.put(b.get(bPos + 1))
        d.put(a.get(aPos + 0))
        d.put(a.get(aPos + 0))
        d.put(a.get(aPos + 1))
        d.put(a.get(aPos + 1))
        d.put(b.get(bPos + 0))
        d.put(b.get(bPos + 0))
        d.put(b.get(bPos + 1))
        d.put(b.get(bPos + 1))
        a.position(aPos + 2) // += 2;
        b.position(bPos + 2) // += 2;
    }

    //
    private fun VQ2TO2(a: ByteBuffer, b: ByteBuffer, c: ByteBuffer, d: ByteBuffer) {
        val aPos = a.position()
        val bPos = b.position()
        c.putShort(a.getShort(aPos)) //TODO:use shortBuffers instead?
        c.putShort(b.getShort(bPos))
        d.putShort(a.getShort(aPos))
        d.putShort(a.getShort(aPos))
        d.putShort(b.getShort(bPos))
        d.putShort(b.getShort(bPos))
        d.putShort(a.getShort(aPos))
        d.putShort(a.getShort(aPos))
        d.putShort(b.getShort(bPos))
        d.putShort(b.getShort(bPos))
        a.position(aPos + 2) //++;
        b.position(bPos + 2) //++;
    }

    private fun VQ2TO2(a: IntBuffer, b: IntBuffer, c: IntBuffer, d: IntBuffer) {
        val aPos = a.position()
        val bPos = b.position()
        c.put(a.get(aPos))
        c.put(b.get(bPos))
        d.put(a.get(aPos))
        d.put(a.get(aPos))
        d.put(b.get(bPos))
        d.put(b.get(bPos))
        d.put(a.get(aPos))
        d.put(a.get(aPos))
        d.put(b.get(bPos))
        d.put(b.get(bPos))
        a.get() //++;
        b.get() //++;
    }

    private fun JPEGBlit(wStatus: ByteBuffer, data: IntArray, offset: Int, datasize: Int): Int {
        throw TODO_Exception()
    }

    /**
     * The original file[] was a byte array.
     */
    private fun expandBuffer(tempFile: ByteBuffer): IntArray {
        for (f in file!!.indices) {
            file!![f] = (tempFile.get(f) and 0xFF.toByte()).toInt()
        }
        return file!!
    }

    /**
     * @return A ByteBuffer duplicate of the `src` buffer with
     * `offset` as start position.
     */
    private fun point(src: ByteBuffer, offset: Long): ByteBuffer {
        val pos = src.position()
        return try {
            src.duplicate().position((pos + offset).toInt()).order(src.order())
        } catch (e: Exception) {
            System.err.printf("point---> %d, %d, %d\n", src.capacity(), src.remaining(), offset)
            throw e
        }
    }

    /**
     * `offset` is doubled to account for the short->byte conversion.
     *
     * @see point
     */
    private fun vqPoint(src: ByteBuffer, offset: Long): ByteBuffer {
        var offset = offset
        offset = offset * 2 //because we use bytebuffers isntead of short arrays.
        return point(src, offset)
    }

    /*
     ===============================================================================

     RoQ cinematic

     Multiple idCinematics can run simultaniously.
     A single idCinematic can be reused for multiple files if desired.

     ===============================================================================
     */
    // cinematic states
    enum class cinStatus_t {
        FMV_IDLE, FMV_PLAY,  // play
        FMV_EOF,  // all other conditions, i.e. stop/EOF/abort
        FMV_ID_BLT, FMV_ID_IDLE, FMV_LOOPED, FMV_ID_WAIT
    }

    // a cinematic stream generates an image buffer, which the caller will upload to a texture
    class cinData_t {
        var image // RGBA format, alpha will be 255
                : ByteBuffer? = null
        var imageWidth = 0
        var imageHeight // will be a power of 2
                = 0
        var status: cinStatus_t? = null
    }

    abstract class idCinematic : Cloneable {
        //	// frees all allocated memory
        // public	abstract				~idCinematic();
        // returns false if it failed to load
        open fun InitFromFile(qpath: String, looping: Boolean): Boolean {
            return false
        }

        // returns the length of the animation in milliseconds
        open fun AnimationLength(): Int {
            return 0
        }

        // the pointers in cinData_t will remain valid until the next UpdateForTime() call
        open fun ImageForTime(milliseconds: Int): cinData_t {
            //	memset( &c, 0, sizeof( c ) );
            return cinData_t()
        }

        // closes the file and frees all allocated memory
        open fun Close() {}

        // closes the file and frees all allocated memory
        open fun ResetTime(time: Int) {}

        @Deprecated("") //remove if not used.
        @Throws(CloneNotSupportedException::class)
        abstract override fun clone(): idCinematic

        companion object {
            // initialize cinematic play back data
            fun InitCinematic() {
                val t_ub: Float
                val t_vr: Float
                val t_ug: Float
                val t_vg: Float
                var i: Int

                // generate YUV tables
                t_ub = 1.77200f / 2.0f * (1 shl 6).toFloat() + 0.5f
                t_vr = 1.40200f / 2.0f * (1 shl 6).toFloat() + 0.5f
                t_ug = 0.34414f / 2.0f * (1 shl 6).toFloat() + 0.5f
                t_vg = 0.71414f / 2.0f * (1 shl 6).toFloat() + 0.5f
                i = 0
                while (i < 256) {
                    val x = (2 * i - 255).toFloat()
                    ROQ_UB_tab[i] = (t_ub * x + (1 shl 5)).toLong()
                    ROQ_VR_tab[i] = (t_vr * x + (1 shl 5)).toLong()
                    ROQ_UG_tab[i] = (-t_ug * x).toLong()
                    ROQ_VG_tab[i] = (-t_vg * x + (1 shl 5)).toLong()
                    ROQ_YY_tab[i] = (i shl 6 or (i shr 2)).toLong()
                    i++
                }
                file = IntArray(65536) // Mem_Alloc(65536);
                vq2 = ByteBuffer.allocate(256 * 16 * 4 * 2)
                    .order(ByteOrder.LITTLE_ENDIAN) // Mem_Alloc(256*16*4 * sizeof( word ));
                vq4 = ByteBuffer.allocate(256 * 64 * 4 * 2)
                    .order(ByteOrder.LITTLE_ENDIAN) // Mem_Alloc(256*64*4 * sizeof( word ));
                vq8 = ByteBuffer.allocate(256 * 256 * 4 * 2)
                    .order(ByteOrder.LITTLE_ENDIAN) // Mem_Alloc(256*256*4 * sizeof( word ));

//            //TODO:for debug purposes only.
//            short[] bla2 = new short[256 * 16 * 4];
//            short[] bla4 = new short[256 * 64 * 4];
//            short[] bla8 = new short[256 * 256 * 4];
//            Arrays.fill(bla2, (short) 0xCD);
//            Arrays.fill(bla4, (short) 0xCD);
//            Arrays.fill(bla8, (short) 0xCD);
//            Arrays.fill(file, 0xCD);
//            vq2.asShortBuffer().put(bla2);
//            vq4.asShortBuffer().put(bla4);
//            vq8.asShortBuffer().put(bla8);
            }

            // shutdown cinematic play back data
            fun ShutdownCinematic() {
                file = IntArray(0)
                vq2 = ByteBuffer.allocate(1)
                vq4 = ByteBuffer.allocate(1)
                vq8 = ByteBuffer.allocate(1)
            }

            // allocates and returns a private subclass that implements the methods
            // This should be used instead of new
            fun Alloc(): idCinematic {
                return idCinematicLocal()
            }
        }
    }

    /*
     ===============================================

     Sound meter.

     ===============================================
     */
    class idSndWindow : idCinematic {
        private var showWaveform: Boolean

        //
        //
        constructor() {
            showWaveform = false
        }

        //						~idSndWindow() {}
        private constructor(window: idSndWindow) {
            showWaveform = window.showWaveform
        }

        override fun InitFromFile(qpath: String, looping: Boolean): Boolean {
            val fname = idStr(qpath)
            fname.ToLower()
            showWaveform = 0 == fname.Icmp("waveform")
            return true
        }

        override fun ImageForTime(milliseconds: Int): cinData_t {
            return snd_system.soundSystem.ImageForTime(milliseconds, showWaveform)
        }

        override fun AnimationLength(): Int {
            return -1
        }

        @Throws(CloneNotSupportedException::class)
        override fun clone(): idCinematic {
            return idSndWindow(this)
        }
    }

    internal class idCinematicLocal() : idCinematic() {
        private val mComp: LongArray = LongArray(256)
        private val t: LongArray = LongArray(2)
        private var CIN_WIDTH = 0
        private var CIN_HEIGHT = 0
        private var ROQSize: Long = 0
        private var RoQFrameSize = 0
        private var RoQPlayed: Long = 0

        //
        private var animationLength = 0
        private var buf: ByteBuffer?
        private var dirty = false
        private var drawX: Long = 0
        private var drawY: Long = 0
        private val fileName: idStr = idStr()
        private var frameRate = 0f
        private var half = false
        private var iFile: idFile?

        //
        private var image: ByteBuffer? = null
        private var inMemory = false

        //
        private var looping = false
        private var normalBuffer0: Long = 0
        private var numQuads: Long = 0
        private var onQuad: Long = 0

        //        private byte[][][] qStatus = new byte[2][][];
        private var qStatus: Array<ArrayList<ByteBuffer>> = Array<ArrayList<ByteBuffer>>(2) { ArrayList() }
        private var roqF0: Long = 0
        private var roqF1: Long = 0
        private var roqFPS: Long = 0
        private var roq_flags: Long = 0
        private var roq_id = 0
        private var samplesPerLine: Long = 0
        private var samplesPerPixel: Long = 2 // defaults to 2
        private var screenDelta = 0
        private var smoothedDouble = false
        private var startTime = 0

        //
        //
        private var status: cinStatus_t
        private var tfps: Long = 0
        private var xSize = 0
        private var ySize = 0
        private var maxSize = 0
        private var minSize = 0

        private constructor(local: idCinematicLocal) : this() {
            System.arraycopy(local.mComp, 0, mComp, 0, mComp.size)
            qStatus = local.qStatus //pointer
            fileName.set(local.fileName)
            CIN_WIDTH = local.CIN_WIDTH
            CIN_HEIGHT = local.CIN_HEIGHT
            iFile = local.iFile //pointer
            status = local.status
            tfps = local.tfps
            RoQPlayed = local.RoQPlayed
            ROQSize = local.ROQSize
            RoQFrameSize = local.RoQFrameSize
            onQuad = local.onQuad
            numQuads = local.numQuads
            samplesPerLine = local.samplesPerLine
            roq_id = local.roq_id
            screenDelta = local.screenDelta
            buf = local.buf //pointer
            samplesPerPixel = local.samplesPerPixel
            xSize = local.xSize
            ySize = local.ySize
            maxSize = local.maxSize
            minSize = local.minSize
            normalBuffer0 = local.normalBuffer0
            roq_flags = local.roq_flags
            roqF0 = local.roqF0
            roqF1 = local.roqF1
            System.arraycopy(local.t, 0, t, 0, t.size)
            roqFPS = local.roqFPS
            drawX = local.drawX
            drawY = local.drawY
            animationLength = local.animationLength
            startTime = local.startTime
            frameRate = local.frameRate
            image = local.image //pointer
            looping = local.looping
            dirty = local.dirty
            half = local.half
            smoothedDouble = local.smoothedDouble
            inMemory = local.inMemory
        }

        override fun InitFromFile(qpath: String, amilooping: Boolean): Boolean {
            val RoQID: Int
            val tempFile: ByteBuffer?
            debugInitFromFile++
            Close()
            inMemory = false
            animationLength = 100000
            fileName.set(
                if (!qpath.contains("/") && !qpath.contains("\\")) {
                    idStr(String.format("video/%s", qpath))
                } else {
                    idStr(String.format("%s", qpath))
                }
            )
            iFile = FileSystem_h.fileSystem.OpenFileRead(fileName.toString())
            if (null == iFile) {
                return false
            }
            ROQSize = iFile!!.Length().toLong()
            looping = amilooping
            CIN_HEIGHT = DEFAULT_CIN_HEIGHT
            CIN_WIDTH = DEFAULT_CIN_WIDTH
            samplesPerPixel = 4
            startTime = 0 //Sys_Milliseconds();
            buf = null
            tempFile = ByteBuffer.allocate(file!!.size)
            iFile!!.Read(tempFile, 16)
            file = expandBuffer(tempFile)
            RoQID = file!![0] + (file!![1] shl 8)
            frameRate = file!![6].toFloat()
            if (frameRate == 32.0f) {
                frameRate = 1000.0f / 32.0f
            }
            if (RoQID == ROQ_FILE) {
                RoQ_init()
                status = cinStatus_t.FMV_PLAY
                ImageForTime(0)
                status = if (looping) cinStatus_t.FMV_PLAY else cinStatus_t.FMV_IDLE
                return true
            }
            RoQShutdown()
            return false
        }

        override fun ImageForTime(thisTime: Int): cinData_t {
            var thisTime = thisTime
            debugImageForTime++
            val cinData: cinData_t
            if (thisTime < 0) {
                thisTime = 0
            }
            cinData = cinData_t() //memset( &cinData, 0, sizeof(cinData) );
            if (RenderSystem_init.r_skipROQ.GetBool()) {
                return cinData
            }
            if (status == cinStatus_t.FMV_EOF || status == cinStatus_t.FMV_IDLE) {
                return cinData
            }
            if (null == buf || startTime == -1) {
                if (startTime == -1) {
                    RoQReset()
                }
                startTime = thisTime
            }
            tfps = ((thisTime - startTime) * frameRate / 1000).toLong()
            if (tfps < 0) {
                tfps = 0
            }
            if (tfps < numQuads) {
                RoQReset()
                buf = null
                status = cinStatus_t.FMV_PLAY
            }
            if (null == buf) {
                while (null == buf) {
                    RoQInterrupt()
                }
            } else {
                while (tfps != numQuads && status == cinStatus_t.FMV_PLAY) {
                    RoQInterrupt()
                }
            }
            if (status == cinStatus_t.FMV_LOOPED) {
                status = cinStatus_t.FMV_PLAY
                while (null == buf && status == cinStatus_t.FMV_PLAY) {
                    RoQInterrupt()
                }
                startTime = thisTime
            }
            if (status == cinStatus_t.FMV_EOF) {
                if (looping) {
                    RoQReset()
                    buf = null
                    if (status == cinStatus_t.FMV_LOOPED) {
                        status = cinStatus_t.FMV_PLAY
                    }
                    while (null == buf && status == cinStatus_t.FMV_PLAY) {
                        RoQInterrupt()
                    }
                    startTime = thisTime
                } else {
                    status = cinStatus_t.FMV_IDLE
                    RoQShutdown()
                }
            }
            cinData.imageWidth = CIN_WIDTH
            cinData.imageHeight = CIN_HEIGHT
            cinData.status = status
            cinData.image = TempDump.wrapToNativeBuffer(buf!!.slice().array())
            //            if (tr_render.variable >= 189) {
//            flushBufferToDisk(buf);
//            }
            return cinData
        }

        override fun AnimationLength(): Int {
            return animationLength
        }

        override fun Close() {
            if (image != null) {
//                Mem_Free(image);
                image = null
                buf = null
                status = cinStatus_t.FMV_EOF
            }
            RoQShutdown()
        }

        override fun ResetTime(time: Int) {
            startTime =
                (if (tr_local.backEnd.viewDef != null) 1000 * tr_local.backEnd.viewDef!!.floatTime else -1).toInt()
            status = cinStatus_t.FMV_PLAY
        }

        private fun RoQ_init() {
            RoQPlayed = 24

            /*	get frame rate */roqFPS = (file[6] + file[7] * 256).toLong()
            if (0L == roqFPS) {
                roqFPS = 30
            }
            numQuads = -1
            roq_id = file[8] + file[9] * 256
            RoQFrameSize = file[10] + file[11] * 256 + file[12] * 65536
            roq_flags = (file[14] + file[15] * 256).toLong()
        }

        private fun blitVQQuad32fs(status: ArrayList<ByteBuffer>, data: IntArray, offset: Int = 0) {
            var newd: Short
            var celdata: Int
            var code: Int
            var index: Int
            var i: Int
            var d_index: Int
            newd = 0
            celdata = 0
            index = 0
            d_index = 0
            do {
                if (0 == newd.toInt()) {
                    newd = 7
                    celdata = (data[offset + d_index + 0]
                            + (data[offset + d_index + 1] shl 8))
                    d_index += 2
                } else {
                    newd--
                }
                code = celdata and 0xc000
                celdata = celdata shl 2
                when (code) {
                    0x8000 -> {
                        blit8_32(
                            vqPoint(vq8!!, (data[offset + d_index] * 128).toLong()),
                            status[index],
                            samplesPerLine
                        )
                        d_index++
                        index += 5
                    }
                    0xc000 -> {
                        index++ // skip 8x8
                        i = 0
                        while (i < 4) {
                            if (0 == newd.toInt()) {
                                newd = 7
                                celdata = data[offset + d_index + 0] + data[offset + d_index + 1] * 256
                                d_index += 2
                            } else {
                                newd--
                            }
                            code = celdata and 0xc000
                            celdata = celdata shl 2
                            when (code) {
                                0x8000 -> {
                                    blit4_32(
                                        vqPoint(
                                            vq4!!,
                                            (data[offset + d_index] * 32).toLong()
                                        ), status[index], samplesPerLine
                                    )
                                    d_index++
                                }
                                0xc000 -> {
                                    blit2_32(
                                        vqPoint(
                                            vq2!!,
                                            (data[offset + d_index] * 8).toLong()
                                        ), status[index], samplesPerLine
                                    )
                                    d_index++
                                    blit2_32(
                                        vqPoint(
                                            vq2!!,
                                            (data[offset + d_index] * 8).toLong()
                                        ), point(status[index], 8), samplesPerLine
                                    )
                                    d_index++
                                    blit2_32(
                                        vqPoint(
                                            vq2!!,
                                            (data[offset + d_index] * 8).toLong()
                                        ), point(status[index], samplesPerLine * 2), samplesPerLine
                                    )
                                    d_index++
                                    blit2_32(
                                        vqPoint(
                                            vq2!!,
                                            (data[offset + d_index] * 8).toLong()
                                        ), point(status[index], samplesPerLine * 2 + 8), samplesPerLine
                                    )
                                    d_index++
                                }
                                0x4000 -> {
                                    move4_32(
                                        point(status[index], mComp[data[offset + d_index]]),
                                        status[index],
                                        samplesPerLine
                                    )
                                    d_index++
                                }
                            }
                            index++
                            i++
                        }
                    }
                    0x4000 -> {
                        move8_32(
                            point(status[index], mComp[data[offset + d_index]]),
                            status[index],
                            samplesPerLine
                        )
                        d_index++
                        index += 5
                    }
                    0x0000 -> index += 5
                }
            } while (status[index] != null)
        }

        private fun RoQShutdown() {
            if (status == cinStatus_t.FMV_IDLE) {
                return
            }
            status = cinStatus_t.FMV_IDLE
            if (iFile != null) {
                FileSystem_h.fileSystem.CloseFile(iFile!!)
                iFile = null
            }
            fileName.set(idStr(""))
        }

        private fun RoQInterrupt() {
            var framedata: Int
            val tempFile: ByteBuffer?
            var redump: Boolean
            tempFile = ByteBuffer.allocate(file.size)
            iFile!!.Read(tempFile, RoQFrameSize + 8)
            file = expandBuffer(tempFile)
            if (RoQPlayed >= ROQSize) {
                if (looping) {
                    RoQReset()
                } else {
                    status = cinStatus_t.FMV_EOF
                }
                return
            }
            framedata = 0 //file;
            //
// new frame is ready
//
            do {
                redump = false
                when (roq_id) {
                    ROQ_QUAD_VQ -> {
                        if (numQuads and 1 == 1L) {
                            normalBuffer0 = t[1]
                            RoQPrepMcomp(roqF0, roqF1)
                            blitVQQuad32fs(qStatus[1], file, framedata)
                            buf = point(image!!, screenDelta.toLong())
                        } else {
                            normalBuffer0 = t[0]
                            RoQPrepMcomp(roqF0, roqF1)
                            blitVQQuad32fs(qStatus[0], file, framedata)
                            buf = image
                        }
                        if (numQuads == 0L) {        // first frame
//				memcpy(image+screenDelta, image, samplesPerLine*ysize);
                            System.arraycopy(
                                image!!.array(),
                                0,
                                image!!.array(),
                                screenDelta,
                                samplesPerLine.toInt() * ySize
                            )
                        }
                        numQuads++
                        dirty = true
                    }
                    ROQ_CODEBOOK -> {
                        debugRoQInterrupt++
                        decodeCodeBook(file, framedata, roq_flags)
                    }
                    ZA_SOUND_MONO -> {}
                    ZA_SOUND_STEREO -> {}
                    ROQ_QUAD_INFO -> {
                        if (numQuads == -1L) {
                            readQuadInfo(file, framedata)
                            setupQuad(0, 0)
                        }
                        if (numQuads != 1L) {
                            numQuads = 0
                        }
                    }
                    ROQ_PACKET -> {
                        inMemory = roq_flags != 0L
                        RoQFrameSize = 0 // for header
                    }
                    ROQ_QUAD_HANG -> RoQFrameSize = 0
                    ROQ_QUAD_JPEG -> if (0L == numQuads) {
                        normalBuffer0 = t[0]
                        JPEGBlit(image!!, file, framedata, RoQFrameSize)
                        //				memcpy(image+screenDelta, image, samplesPerLine*ysize);
                        System.arraycopy(image, 0, image, screenDelta, samplesPerLine.toInt() * ySize)
                        numQuads++
                    }
                    else -> status = cinStatus_t.FMV_EOF
                }
                //
// read in next frame data
//
                if (RoQPlayed >= ROQSize) {
                    if (looping) {
                        RoQReset()
                    } else {
                        status = cinStatus_t.FMV_EOF
                    }
                    return
                }
                framedata += RoQFrameSize
                roq_id = file[framedata + 0] + file[framedata + 1] * 256
                RoQFrameSize =
                    file[framedata + 2] + file[framedata + 3] * 256 + file[framedata + 4] * 65536
                roq_flags = (file[framedata + 6] + file[framedata + 7] * 256).toLong()
                roqF0 = file[framedata + 7] as Long
                roqF1 = file[framedata + 6] as Long
                //                System.out.printf("roq_id=%d, roqF0=%d, roqF1=%d\n", roq_id, roqF0, roqF1);
                if (RoQFrameSize > 65536 || roq_id == 0x1084) {
                    Common.common.DPrintf("roq_size>65536||roq_id==0x1084\n")
                    status = cinStatus_t.FMV_EOF
                    if (looping) {
                        RoQReset()
                    }
                    return
                }
                if (inMemory && status != cinStatus_t.FMV_EOF) {
                    inMemory = false
                    framedata += 8
                    redump = true //goto redump;
                }
            } while (redump) //{

//
// one more frame hits the dust
//
//	assert(RoQFrameSize <= 65536);
//	r = Sys_StreamedRead( file, RoQFrameSize+8, 1, iFile );
            RoQPlayed += (RoQFrameSize + 8).toLong()
        }

        private fun move8_32(src: ByteBuffer, dst: ByteBuffer, spl: Long) {
//            if (true) {
            val dsrc: IntBuffer
            val ddst: IntBuffer
            val dspl: Int
            dsrc = src.asIntBuffer()
            ddst = dst.asIntBuffer()
            dspl = spl.toInt() shr 2
            ddst.put(0 * dspl + 0, dsrc[0 * dspl + 0])
            ddst.put(0 * dspl + 1, dsrc[0 * dspl + 1])
            ddst.put(0 * dspl + 2, dsrc[0 * dspl + 2])
            ddst.put(0 * dspl + 3, dsrc[0 * dspl + 3])
            ddst.put(0 * dspl + 4, dsrc[0 * dspl + 4])
            ddst.put(0 * dspl + 5, dsrc[0 * dspl + 5])
            ddst.put(0 * dspl + 6, dsrc[0 * dspl + 6])
            ddst.put(0 * dspl + 7, dsrc[0 * dspl + 7])
            ddst.put(1 * dspl + 0, dsrc[1 * dspl + 0])
            ddst.put(1 * dspl + 1, dsrc[1 * dspl + 1])
            ddst.put(1 * dspl + 2, dsrc[1 * dspl + 2])
            ddst.put(1 * dspl + 3, dsrc[1 * dspl + 3])
            ddst.put(1 * dspl + 4, dsrc[1 * dspl + 4])
            ddst.put(1 * dspl + 5, dsrc[1 * dspl + 5])
            ddst.put(1 * dspl + 6, dsrc[1 * dspl + 6])
            ddst.put(1 * dspl + 7, dsrc[1 * dspl + 7])
            ddst.put(2 * dspl + 0, dsrc[2 * dspl + 0])
            ddst.put(2 * dspl + 1, dsrc[2 * dspl + 1])
            ddst.put(2 * dspl + 2, dsrc[2 * dspl + 2])
            ddst.put(2 * dspl + 3, dsrc[2 * dspl + 3])
            ddst.put(2 * dspl + 4, dsrc[2 * dspl + 4])
            ddst.put(2 * dspl + 5, dsrc[2 * dspl + 5])
            ddst.put(2 * dspl + 6, dsrc[2 * dspl + 6])
            ddst.put(2 * dspl + 7, dsrc[2 * dspl + 7])
            ddst.put(3 * dspl + 0, dsrc[3 * dspl + 0])
            ddst.put(3 * dspl + 1, dsrc[3 * dspl + 1])
            ddst.put(3 * dspl + 2, dsrc[3 * dspl + 2])
            ddst.put(3 * dspl + 3, dsrc[3 * dspl + 3])
            ddst.put(3 * dspl + 4, dsrc[3 * dspl + 4])
            ddst.put(3 * dspl + 5, dsrc[3 * dspl + 5])
            ddst.put(3 * dspl + 6, dsrc[3 * dspl + 6])
            ddst.put(3 * dspl + 7, dsrc[3 * dspl + 7])
            ddst.put(4 * dspl + 0, dsrc[4 * dspl + 0])
            ddst.put(4 * dspl + 1, dsrc[4 * dspl + 1])
            ddst.put(4 * dspl + 2, dsrc[4 * dspl + 2])
            ddst.put(4 * dspl + 3, dsrc[4 * dspl + 3])
            ddst.put(4 * dspl + 4, dsrc[4 * dspl + 4])
            ddst.put(4 * dspl + 5, dsrc[4 * dspl + 5])
            ddst.put(4 * dspl + 6, dsrc[4 * dspl + 6])
            ddst.put(4 * dspl + 7, dsrc[4 * dspl + 7])
            ddst.put(5 * dspl + 0, dsrc[5 * dspl + 0])
            ddst.put(5 * dspl + 1, dsrc[5 * dspl + 1])
            ddst.put(5 * dspl + 2, dsrc[5 * dspl + 2])
            ddst.put(5 * dspl + 3, dsrc[5 * dspl + 3])
            ddst.put(5 * dspl + 4, dsrc[5 * dspl + 4])
            ddst.put(5 * dspl + 5, dsrc[5 * dspl + 5])
            ddst.put(5 * dspl + 6, dsrc[5 * dspl + 6])
            ddst.put(5 * dspl + 7, dsrc[5 * dspl + 7])
            ddst.put(6 * dspl + 0, dsrc[6 * dspl + 0])
            ddst.put(6 * dspl + 1, dsrc[6 * dspl + 1])
            ddst.put(6 * dspl + 2, dsrc[6 * dspl + 2])
            ddst.put(6 * dspl + 3, dsrc[6 * dspl + 3])
            ddst.put(6 * dspl + 4, dsrc[6 * dspl + 4])
            ddst.put(6 * dspl + 5, dsrc[6 * dspl + 5])
            ddst.put(6 * dspl + 6, dsrc[6 * dspl + 6])
            ddst.put(6 * dspl + 7, dsrc[6 * dspl + 7])
            ddst.put(7 * dspl + 0, dsrc[7 * dspl + 0])
            ddst.put(7 * dspl + 1, dsrc[7 * dspl + 1])
            ddst.put(7 * dspl + 2, dsrc[7 * dspl + 2])
            ddst.put(7 * dspl + 3, dsrc[7 * dspl + 3])
            ddst.put(7 * dspl + 4, dsrc[7 * dspl + 4])
            ddst.put(7 * dspl + 5, dsrc[7 * dspl + 5])
            ddst.put(7 * dspl + 6, dsrc[7 * dspl + 6])
            ddst.put(7 * dspl + 7, dsrc[7 * dspl + 7])
            //}else{
//	// double *dsrc, *ddst;
//	int dspl;
//
//	// dsrc = (double *)src;
//	// ddst = (double *)dst;
//	dspl = spl>>3;
//
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2]; dst[3] = src[3];
//            }
        }

        private fun move4_32(src: ByteBuffer, dst: ByteBuffer, spl: Long) {
//            if (true) {
            val dsrc: IntBuffer
            val ddst: IntBuffer
            val dspl: Int
            dsrc = src.asIntBuffer()
            ddst = dst.asIntBuffer()
            dspl = spl.toInt() shr 2
            ddst.put(0 * dspl + 0, dsrc[0 * dspl + 0])
            ddst.put(0 * dspl + 1, dsrc[0 * dspl + 1])
            ddst.put(0 * dspl + 2, dsrc[0 * dspl + 2])
            ddst.put(0 * dspl + 3, dsrc[0 * dspl + 3])
            ddst.put(1 * dspl + 0, dsrc[1 * dspl + 0])
            ddst.put(1 * dspl + 1, dsrc[1 * dspl + 1])
            ddst.put(1 * dspl + 2, dsrc[1 * dspl + 2])
            ddst.put(1 * dspl + 3, dsrc[1 * dspl + 3])
            ddst.put(2 * dspl + 0, dsrc[2 * dspl + 0])
            ddst.put(2 * dspl + 1, dsrc[2 * dspl + 1])
            ddst.put(2 * dspl + 2, dsrc[2 * dspl + 2])
            ddst.put(2 * dspl + 3, dsrc[2 * dspl + 3])
            ddst.put(3 * dspl + 0, dsrc[3 * dspl + 0])
            ddst.put(3 * dspl + 1, dsrc[3 * dspl + 1])
            ddst.put(3 * dspl + 2, dsrc[3 * dspl + 2])
            ddst.put(3 * dspl + 3, dsrc[3 * dspl + 3])
            //}else{
//	// double *dsrc, *ddst;
//	int dspl;
//
//	// dsrc = (double *)src;
//	// ddst = (double *)dst;
//	dspl = spl>>3;
//
//	dst[0] = src[0]; dst[1] = src[1];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//	src += dspl; dst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//            }
        }

        private fun blit8_32(src: ByteBuffer, dst: ByteBuffer, spl: Long) {
//            if (true) {
            val dsrc: IntBuffer
            val ddst: IntBuffer
            val dspl: Int
            dsrc = src.asIntBuffer()
            ddst = dst.asIntBuffer()
            dspl = spl.toInt() shr 2
            ddst.put(0 * dspl + 0, dsrc.get())
            ddst.put(0 * dspl + 1, dsrc.get())
            ddst.put(0 * dspl + 2, dsrc.get())
            ddst.put(0 * dspl + 3, dsrc.get())
            ddst.put(0 * dspl + 4, dsrc.get())
            ddst.put(0 * dspl + 5, dsrc.get())
            ddst.put(0 * dspl + 6, dsrc.get())
            ddst.put(0 * dspl + 7, dsrc.get())
            ddst.put(1 * dspl + 0, dsrc.get())
            ddst.put(1 * dspl + 1, dsrc.get())
            ddst.put(1 * dspl + 2, dsrc.get())
            ddst.put(1 * dspl + 3, dsrc.get())
            ddst.put(1 * dspl + 4, dsrc.get())
            ddst.put(1 * dspl + 5, dsrc.get())
            ddst.put(1 * dspl + 6, dsrc.get())
            ddst.put(1 * dspl + 7, dsrc.get())
            ddst.put(2 * dspl + 0, dsrc.get())
            ddst.put(2 * dspl + 1, dsrc.get())
            ddst.put(2 * dspl + 2, dsrc.get())
            ddst.put(2 * dspl + 3, dsrc.get())
            ddst.put(2 * dspl + 4, dsrc.get())
            ddst.put(2 * dspl + 5, dsrc.get())
            ddst.put(2 * dspl + 6, dsrc.get())
            ddst.put(2 * dspl + 7, dsrc.get())
            ddst.put(3 * dspl + 0, dsrc.get())
            ddst.put(3 * dspl + 1, dsrc.get())
            ddst.put(3 * dspl + 2, dsrc.get())
            ddst.put(3 * dspl + 3, dsrc.get())
            ddst.put(3 * dspl + 4, dsrc.get())
            ddst.put(3 * dspl + 5, dsrc.get())
            ddst.put(3 * dspl + 6, dsrc.get())
            ddst.put(3 * dspl + 7, dsrc.get())
            ddst.put(4 * dspl + 0, dsrc.get())
            ddst.put(4 * dspl + 1, dsrc.get())
            ddst.put(4 * dspl + 2, dsrc.get())
            ddst.put(4 * dspl + 3, dsrc.get())
            ddst.put(4 * dspl + 4, dsrc.get())
            ddst.put(4 * dspl + 5, dsrc.get())
            ddst.put(4 * dspl + 6, dsrc.get())
            ddst.put(4 * dspl + 7, dsrc.get())
            ddst.put(5 * dspl + 0, dsrc.get())
            ddst.put(5 * dspl + 1, dsrc.get())
            ddst.put(5 * dspl + 2, dsrc.get())
            ddst.put(5 * dspl + 3, dsrc.get())
            ddst.put(5 * dspl + 4, dsrc.get())
            ddst.put(5 * dspl + 5, dsrc.get())
            ddst.put(5 * dspl + 6, dsrc.get())
            ddst.put(5 * dspl + 7, dsrc.get())
            ddst.put(6 * dspl + 0, dsrc.get())
            ddst.put(6 * dspl + 1, dsrc.get())
            ddst.put(6 * dspl + 2, dsrc.get())
            ddst.put(6 * dspl + 3, dsrc.get())
            ddst.put(6 * dspl + 4, dsrc.get())
            ddst.put(6 * dspl + 5, dsrc.get())
            ddst.put(6 * dspl + 6, dsrc.get())
            ddst.put(6 * dspl + 7, dsrc.get())
            ddst.put(7 * dspl + 0, dsrc.get())
            ddst.put(7 * dspl + 1, dsrc.get())
            ddst.put(7 * dspl + 2, dsrc.get())
            ddst.put(7 * dspl + 3, dsrc.get())
            ddst.put(7 * dspl + 4, dsrc.get())
            ddst.put(7 * dspl + 5, dsrc.get())
            ddst.put(7 * dspl + 6, dsrc.get())
            ddst.put(7 * dspl + 7, dsrc.get())
            //}else{
//	// double *dsrc, *ddst;
//	int dspl;
//
//	// dsrc = (double *)src;
//	// ddst = (double *)dst;
//	dspl = spl>>3;
//
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//	dsrc += 4; ddst += dspl;
//	dst[0] = dsrc[0]; dst[1] = dsrc[1]; dst[2] = dsrc[2]; dst[3] = dsrc[3];
//            }
        }

        private fun blit4_32(src: ByteBuffer, dst: ByteBuffer, spl: Long) {
//            if (true) {
            val dsrc: IntBuffer
            val ddst: IntBuffer
            val dspl: Int
            dsrc = src.asIntBuffer()
            ddst = dst.asIntBuffer()
            dspl = spl.toInt() shr 2
            ddst.put(0 * dspl + 0, dsrc.get())
            ddst.put(0 * dspl + 1, dsrc.get())
            ddst.put(0 * dspl + 2, dsrc.get())
            ddst.put(0 * dspl + 3, dsrc.get())
            ddst.put(1 * dspl + 0, dsrc.get())
            ddst.put(1 * dspl + 1, dsrc.get())
            ddst.put(1 * dspl + 2, dsrc.get())
            ddst.put(1 * dspl + 3, dsrc.get())
            ddst.put(2 * dspl + 0, dsrc.get())
            ddst.put(2 * dspl + 1, dsrc.get())
            ddst.put(2 * dspl + 2, dsrc.get())
            ddst.put(2 * dspl + 3, dsrc.get())
            ddst.put(3 * dspl + 0, dsrc.get())
            ddst.put(3 * dspl + 1, dsrc.get())
            ddst.put(3 * dspl + 2, dsrc.get())
            ddst.put(3 * dspl + 3, dsrc.get())
            //}else{
//	// double *dsrc, *ddst;
//	int dspl;
//
//	// dsrc = (double *)src;
//	// ddst = (double *)dst;
//	dspl = spl>>3;
//
//	dst[0] = src[0]; dst[1] = src[1];
//	dsrc += 2; ddst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//	dsrc += 2; ddst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//	dsrc += 2; ddst += dspl;
//	dst[0] = src[0]; dst[1] = src[1];
//            }
        }

        private fun blit2_32(src: ByteBuffer, dst: ByteBuffer, spl: Long) {
//            if (true) {
            val dsrc: IntBuffer
            val ddst: IntBuffer
            val dspl: Int
            dsrc = src.asIntBuffer()
            ddst = dst.asIntBuffer()
            dspl = spl.toInt() shr 2
            ddst.put(0 * dspl + 0, dsrc.get())
            ddst.put(0 * dspl + 1, dsrc.get())
            ddst.put(1 * dspl + 0, dsrc.get())
            ddst.put(1 * dspl + 1, dsrc.get())
            //}else{
//	// double *dsrc, *ddst;
//	int dspl;
//
//	// dsrc = (double *)src;
//	// ddst = (double *)dst;
//	dspl = spl>>3;
//
//	dst[0] = src[0];
//	dst[dspl] = src[1];
//            }
        }

        //
        private fun yuv_to_rgb(y: Long, u: Long, v: Long): Short {
            var r: Long
            var g: Long
            var b: Long
            val YY = ROQ_YY_tab[y.toInt()]
            r = YY + ROQ_VR_tab[v.toInt()] shr 9
            g = YY + ROQ_UG_tab[u.toInt()] + ROQ_VG_tab[v.toInt()] shr 8
            b = YY + ROQ_UB_tab[u.toInt()] shr 9
            if (r < 0) {
                r = 0
            }
            if (g < 0) {
                g = 0
            }
            if (b < 0) {
                b = 0
            }
            if (r > 31) {
                r = 31
            }
            if (g > 63) {
                g = 63
            }
            if (b > 31) {
                b = 31
            }
            return ((r shl 11) + (g shl 5) + b).toShort()
        }

        private fun yuv_to_rgb24(y: Long, u: Long, v: Long): Int {
            var r: Long
            var g: Long
            var b: Long
            val YY = ROQ_YY_tab[y.toInt()]
            r = YY + ROQ_VR_tab[v.toInt()] shr 6
            g = YY + ROQ_UG_tab[u.toInt()] + ROQ_VG_tab[v.toInt()] shr 6
            b = YY + ROQ_UB_tab[u.toInt()] shr 6
            if (r < 0) {
                r = 0
            }
            if (g < 0) {
                g = 0
            }
            if (b < 0) {
                b = 0
            }
            if (r > 255) {
                r = 255
            }
            if (g > 255) {
                g = 255
            }
            if (b > 255) {
                b = 255
            }

//            System.out.printf("----- %d, %d, %d, %d, %d, %d, %d, %d\n", LittleLong((r) + (g << 8) + (b << 16)), y, u, v, YY, r, g, b);
            return Lib.Companion.LittleLong(r + (g shl 8) + (b shl 16))
        }

        private fun decodeCodeBook(input: IntArray, offset: Int, roq_flags: Long) {
            var i: Long
            var j: Long
            var two: Long
            var four: Long
            var aptr: ByteBuffer?
            var bptr: ByteBuffer?
            val cptr: ByteBuffer?
            val dptr: ByteBuffer?
            var y0: Long
            var y1: Long
            var y2: Long
            var y3: Long
            var cr: Long
            var cb: Long
            var iaptr: IntBuffer?
            var ibptr: IntBuffer?
            val icptr: IntBuffer?
            val idptr: IntBuffer?
            var i_ptr: Int
            if (0L == roq_flags) {
                four = 256
                two = four
            } else {
                two = roq_flags shr 8
                if (0L == two) {
                    two = 256
                }
                four = roq_flags and 0xff
            }
            four *= 2
            bptr = vq2.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            i_ptr = offset
            if (!half) {
                if (!smoothedDouble) {
////////////////////////////////////////////////////////////////////////////////
// normal height
////////////////////////////////////////////////////////////////////////////////
                    if (samplesPerPixel == 2L) {
                        i = 0
                        while (i < two) {
                            y0 = input[i_ptr++].toLong()
                            y1 = input[i_ptr++].toLong()
                            y2 = input[i_ptr++].toLong()
                            y3 = input[i_ptr++].toLong()
                            cr = input[i_ptr++].toLong()
                            cb = input[i_ptr++].toLong()
                            bptr.putShort(yuv_to_rgb(y0, cr, cb))
                            bptr.putShort(yuv_to_rgb(y1, cr, cb))
                            bptr.putShort(yuv_to_rgb(y2, cr, cb))
                            bptr.putShort(yuv_to_rgb(y3, cr, cb))
                            i++
                        }
                        cptr = vq4.duplicate()
                        dptr = vq8.duplicate()
                        i = 0
                        while (i < four) {
                            aptr = vqPoint(vq2, (input[i_ptr++] * 4).toLong())
                            bptr = vqPoint(vq2, (input[i_ptr++] * 4).toLong())
                            j = 0
                            while (j < 2) {
                                VQ2TO4(aptr, bptr, cptr, dptr)
                                j++
                            }
                            i++
                        }
                    } else if (samplesPerPixel == 4L) {
                        ibptr = bptr.asIntBuffer()
                        var x0: Int
                        var x1: Int
                        var x2: Int
                        var x3: Int
                        i = 0
                        while (i < two) {
                            y0 = input[i_ptr++].toLong()
                            y1 = input[i_ptr++].toLong()
                            y2 = input[i_ptr++].toLong()
                            y3 = input[i_ptr++].toLong() //TODO:beware the signed vs unsigned shit.
                            cr = input[i_ptr++].toLong()
                            cb = input[i_ptr++].toLong()
                            ibptr.put(yuv_to_rgb24(y0, cr, cb).also { x0 = it })
                            ibptr.put(yuv_to_rgb24(y1, cr, cb).also { x1 = it })
                            ibptr.put(yuv_to_rgb24(y2, cr, cb).also { x2 = it })
                            ibptr.put(yuv_to_rgb24(y3, cr, cb).also { x3 = it })
                            i++
                        }
                        icptr = vq4.asIntBuffer()
                        idptr = vq8.asIntBuffer()
                        i = 0
                        while (i < four) {
                            iaptr = vq2.asIntBuffer().position(input[i_ptr++] * 4)
                            ibptr = vq2.asIntBuffer().position(input[i_ptr++] * 4)
                            j = 0
                            while (j < 2) {
                                VQ2TO4(iaptr, ibptr, icptr, idptr)
                                j++
                            }
                            i++
                        }
                    }
                } else {
////////////////////////////////////////////////////////////////////////////////
// double height, smoothed
////////////////////////////////////////////////////////////////////////////////
                    if (samplesPerPixel == 2L) {
                        i = 0
                        while (i < two) {
                            y0 = input[i_ptr++].toLong()
                            y1 = input[i_ptr++].toLong()
                            y2 = input[i_ptr++].toLong()
                            y3 = input[i_ptr++].toLong()
                            cr = input[i_ptr++].toLong()
                            cb = input[i_ptr++].toLong()
                            bptr.putShort(yuv_to_rgb(y0, cr, cb))
                            bptr.putShort(yuv_to_rgb(y1, cr, cb))
                            bptr.putShort(yuv_to_rgb((y0 * 3 + y2) / 4, cr, cb))
                            bptr.putShort(yuv_to_rgb((y1 * 3 + y3) / 4, cr, cb))
                            bptr.putShort(yuv_to_rgb((y0 + y2 * 3) / 4, cr, cb))
                            bptr.putShort(yuv_to_rgb((y1 + y3 * 3) / 4, cr, cb))
                            bptr.putShort(yuv_to_rgb(y2, cr, cb))
                            bptr.putShort(yuv_to_rgb(y3, cr, cb))
                            i++
                        }
                        cptr = vq4.duplicate()
                        dptr = vq8.duplicate()
                        i = 0
                        while (i < four) {
                            aptr = vqPoint(vq2, (input[i_ptr++] * 8).toLong())
                            bptr = vqPoint(vq2, (input[i_ptr++] * 8).toLong())
                            j = 0
                            while (j < 2) {
                                VQ2TO4(aptr, bptr, cptr, dptr)
                                VQ2TO4(aptr, bptr, cptr, dptr)
                                j++
                            }
                            i++
                        }
                    } else if (samplesPerPixel == 4L) {
                        ibptr = bptr.asIntBuffer()
                        i = 0
                        while (i < two) {
                            y0 = input[i_ptr++].toLong()
                            y1 = input[i_ptr++].toLong()
                            y2 = input[i_ptr++].toLong()
                            y3 = input[i_ptr++].toLong()
                            cr = input[i_ptr++].toLong()
                            cb = input[i_ptr++].toLong()
                            ibptr.put(yuv_to_rgb24(y0, cr, cb))
                            ibptr.put(yuv_to_rgb24(y1, cr, cb))
                            ibptr.put(yuv_to_rgb24((y0 * 3 + y2) / 4, cr, cb))
                            ibptr.put(yuv_to_rgb24((y1 * 3 + y3) / 4, cr, cb))
                            ibptr.put(yuv_to_rgb24((y0 + y2 * 3) / 4, cr, cb))
                            ibptr.put(yuv_to_rgb24((y1 + y3 * 3) / 4, cr, cb))
                            ibptr.put(yuv_to_rgb24(y2, cr, cb))
                            ibptr.put(yuv_to_rgb24(y3, cr, cb))
                            i++
                        }
                        icptr = vq4.asIntBuffer()
                        idptr = vq8.asIntBuffer()
                        i = 0
                        while (i < four) {
                            iaptr = vq2.asIntBuffer().position(input[i_ptr++] * 8)
                            ibptr = vq2.asIntBuffer().position(input[i_ptr++] * 8)
                            j = 0
                            while (j < 2) {
                                VQ2TO4(iaptr, ibptr, icptr, idptr)
                                VQ2TO4(iaptr, ibptr, icptr, idptr)
                                j++
                            }
                            i++
                        }
                    }
                }
            } else {
////////////////////////////////////////////////////////////////////////////////
// 1/4 screen
////////////////////////////////////////////////////////////////////////////////
                if (samplesPerPixel == 2L) {
                    i = 0
                    while (i < two) {
                        y0 = input[i_ptr].toLong()
                        i_ptr += 2
                        y2 = input[i_ptr].toLong()
                        i_ptr += 2
                        cr = input[i_ptr++].toLong()
                        cb = input[i_ptr++].toLong()
                        bptr.putShort(yuv_to_rgb(y0, cr, cb))
                        bptr.putShort(yuv_to_rgb(y2, cr, cb))
                        i++
                    }
                    cptr = vq4.duplicate()
                    dptr = vq8.duplicate()
                    i = 0
                    while (i < four) {
                        aptr = vqPoint(vq2, (input[i_ptr++] * 2).toLong())
                        bptr = vqPoint(vq2, (input[i_ptr++] * 2).toLong())
                        j = 0
                        while (j < 2) {
                            VQ2TO2(aptr, bptr, cptr, dptr)
                            j++
                        }
                        i++
                    }
                } else if (samplesPerPixel == 4L) {
                    ibptr = bptr.asIntBuffer()
                    i = 0
                    while (i < two) {
                        y0 = input[i_ptr].toLong()
                        i_ptr += 2
                        y2 = input[i_ptr].toLong()
                        i_ptr += 2
                        cr = input[i_ptr++].toLong()
                        cb = input[i_ptr++].toLong()
                        ibptr.put(yuv_to_rgb24(y0, cr, cb))
                        ibptr.put(yuv_to_rgb24(y2, cr, cb))
                        i++
                    }
                    icptr = vq4.asIntBuffer()
                    idptr = vq8.asIntBuffer()
                    i = 0
                    while (i < four) {
                        iaptr = vq2.asIntBuffer().position(input[i_ptr++] * 2)
                        ibptr = vq2.asIntBuffer().position(input[i_ptr++] * 2)
                        j = 0
                        while (j < 2) {
                            VQ2TO2(iaptr, ibptr, icptr, idptr)
                            j++
                        }
                        i++
                    }
                }
            }
        }

        private fun recurseQuad(startX: Long, startY: Long, quadSize: Long, xOff: Long, yOff: Long) {
            var quadSize = quadSize
            val scrOff: ByteBuffer?
            var bigX: Long
            var bigY: Long
            val lowX: Long
            val lowY: Long
            val useY: Long
            val offset: Int
            offset = screenDelta
            lowY = 0
            lowX = lowY
            bigX = xSize.toLong()
            bigY = ySize.toLong()
            if (bigX > CIN_WIDTH) {
                bigX = CIN_WIDTH.toLong()
            }
            if (bigY > CIN_HEIGHT) {
                bigY = CIN_HEIGHT.toLong()
            }
            if (startX >= lowX && startX + quadSize <= bigX && startY + quadSize <= bigY && startY >= lowY && quadSize <= MAXSIZE) {
                useY = startY
                val offering =
                    ((useY + (CIN_HEIGHT - bigY shr 1) + yOff) * samplesPerLine + (startX + xOff) * samplesPerPixel).toInt()
                scrOff = point(image!!, offering.toLong())
                qStatus[0][onQuad.toInt()] = scrOff
                qStatus[1][onQuad++.toInt()] = point(scrOff, offset.toLong())
            }
            if (quadSize != MINSIZE.toLong()) {
                quadSize = quadSize shr 1
                recurseQuad(startX, startY, quadSize, xOff, yOff)
                recurseQuad(startX + quadSize, startY, quadSize, xOff, yOff)
                recurseQuad(startX, startY + quadSize, quadSize, xOff, yOff)
                recurseQuad(startX + quadSize, startY + quadSize, quadSize, xOff, yOff)
            }
        }

        private fun setupQuad(xOff: Long, yOff: Long) {
            var numQuadCels: Long
            var x: Long
            var y: Long
            var i: Int
            val temp: ByteBuffer?
            numQuadCels = (CIN_WIDTH * CIN_HEIGHT / 16).toLong()
            numQuadCels += numQuadCels / 4 + numQuadCels / 16
            numQuadCels += 64 // for overflow
            numQuadCels = (xSize * ySize / 16).toLong()
            numQuadCels += numQuadCels / 4
            numQuadCels += 64 // for overflow
            onQuad = 0
            y = 0
            while (y < ySize.toLong()) {
                x = 0
                while (x < xSize.toLong()) {
                    recurseQuad(x, y, 16, xOff, yOff)
                    x += 16
                }
                y += 16
            }
            temp = null
            i = (numQuadCels - 64).toInt()
            while (i < numQuadCels) {
                //temp;			// eoq
                qStatus[1][i] = ByteBuffer.allocate(1)
                qStatus[0][i] = qStatus[1][i] // eoq
                i++
            }
        }

        private fun readQuadInfo(qData: IntArray, offset: Int) {
            xSize = qData[offset + 0] + qData[offset + 1] * 256
            ySize = qData[offset + 2] + qData[offset + 3] * 256
            maxSize = qData[offset + 4] + qData[offset + 5] * 256
            minSize = qData[offset + 6] + qData[offset + 7] * 256
            CIN_HEIGHT = ySize
            CIN_WIDTH = xSize
            samplesPerLine = CIN_WIDTH * samplesPerPixel
            screenDelta = (CIN_HEIGHT * samplesPerLine).toInt()
            if (TempDump.NOT(image)) {
                image = ByteBuffer.allocate((CIN_WIDTH * CIN_HEIGHT * samplesPerPixel * 2).toInt())
                    .order(ByteOrder.LITTLE_ENDIAN) //Mem_Alloc((int) (CIN_WIDTH * CIN_HEIGHT * samplesPerPixel * 2));
            }
            half = false
            smoothedDouble = false
            t[0] = screenDelta.toLong() //t[0] = (0 - (unsigned int)image)+(unsigned int)image+screenDelta;
            t[1] = (-screenDelta).toLong() //t[1] = (0 - ((unsigned int)image + screenDelta))+(unsigned int)image;
            drawX = CIN_WIDTH.toLong()
            drawY = CIN_HEIGHT.toLong()
        }

        private fun RoQPrepMcomp(xOff: Long, yOff: Long) {
            var x: Int
            var y: Int
            var i: Long
            var j: Long
            var temp: Long
            var temp2: Long
            i = samplesPerLine
            j = samplesPerPixel
            if (xSize == ySize * 4 && !half) {
                j = j + j
                i = i + i
            }
            y = 0
            while (y < 16) {
                temp2 = (y + yOff - 8) * i
                x = 0
                while (x < 16) {
                    temp = (x + xOff - 8) * j
                    mComp[x * 16 + y] = normalBuffer0 - (temp2 + temp) and 0xFFFFFFFFL
                    x++
                }
                y++
            }
        }

        private fun RoQReset() {
            val tempFile: ByteBuffer?
            tempFile = ByteBuffer.allocate(file!!.size)
            iFile!!.Seek(0, fsOrigin_t.FS_SEEK_SET)
            iFile!!.Read(tempFile, 16)
            file = expandBuffer(tempFile)
            RoQ_init()
            status = cinStatus_t.FMV_LOOPED
        }

        @Throws(CloneNotSupportedException::class)
        override fun clone(): idCinematic {
            return idCinematicLocal(this)
        }

        companion object {
            var debugInitFromFile = 0
            var debugRoQInterrupt = 0
            private var debugImageForTime = 0
        }

        init {
            status = cinStatus_t.FMV_EOF
            buf = null
            iFile = null
            qStatus[0] = ArrayList<ByteBuffer>(32768) // Mem_Alloc(32768);
            qStatus[1] = ArrayList<ByteBuffer>(32768) // Mem_Alloc(32768);
        }
    } //    private static void flushBufferToDisk(final ByteBuffer buffer) {
    //        try {
    //            File file = new File("/temp/j" + fileNumber);
    //            file.createNewFile();
    //            try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
    //                fileChannel.write(buffer.slice());
    //            }
    ////            File fileVQ2 = new File("/temp/jvq2" + fileNumber);
    ////            fileVQ2.createNewFile();
    ////            try (FileChannel fileChannel = FileChannel.open(fileVQ2.toPath(), StandardOpenOption.WRITE)) {
    ////                fileChannel.write(vq2.duplicate());
    ////            }
    ////            File fileVQ4 = new File("/temp/jvq4" + fileNumber);
    ////            fileVQ4.createNewFile();
    ////            try (FileChannel fileChannel = FileChannel.open(fileVQ4.toPath(), StandardOpenOption.WRITE)) {
    ////                fileChannel.write(vq4.duplicate());
    ////            }
    ////            File fileVQ8 = new File("/temp/jvq8" + (fileNumber));
    ////            fileVQ8.createNewFile();
    ////            try (FileChannel fileChannel = FileChannel.open(fileVQ8.toPath(), StandardOpenOption.WRITE)) {
    ////                fileChannel.write(vq8.duplicate());
    ////            }
    //            fileNumber++;
    //        } catch (IOException ex) {
    //            Logger.getLogger(Cinematic.class.getName()).log(Level.SEVERE, null, ex);
    //        }
    //    }
    //    static int fileNumber = 0;
}