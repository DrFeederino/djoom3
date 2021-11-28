package neo.Renderer

import neo.Renderer.*
import neo.Renderer.Image.GeneratorFunction
import neo.Renderer.Image.idImage
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Material.textureRepeat_t
import neo.TempDump
import neo.framework.CmdSystem.cmdFunction_t
import neo.idlib.*
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h.idMath
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.EXTTextureCompressionS3TC
import org.lwjgl.opengl.GL11
import java.nio.*
import java.util.*

/**
 *
 */
object Image_init {
    // the size determines how far away from the edge the blocks start fading
    const val BORDER_CLAMP_SIZE = 32
    const val DEEP_RANGE = -30f

    /*
     ================
     R_FogImage

     We calculate distance correctly in two planes, but the
     third will still be projection based
     ================
     */
    const val FOG_SIZE = 128
    val IC_Info: Array<imageClassificate_t?>? = arrayOf(
        imageClassificate_t("models/characters", "Characters", IMAGE_CLASSIFICATION.IC_NPC, 512, 512),
        imageClassificate_t("models/weapons", "Weapons", IMAGE_CLASSIFICATION.IC_WEAPON, 512, 512),
        imageClassificate_t("models/monsters", "Monsters", IMAGE_CLASSIFICATION.IC_MONSTER, 512, 512),
        imageClassificate_t("models/mapobjects", "Model Geometry", IMAGE_CLASSIFICATION.IC_MODELGEOMETRY, 512, 512),
        imageClassificate_t("models/items", "Items", IMAGE_CLASSIFICATION.IC_ITEMS, 512, 512),
        imageClassificate_t("models", "Other model textures", IMAGE_CLASSIFICATION.IC_MODELSOTHER, 512, 512),
        imageClassificate_t("guis/assets", "Guis", IMAGE_CLASSIFICATION.IC_GUIS, 256, 256),
        imageClassificate_t("textures", "World Geometry", IMAGE_CLASSIFICATION.IC_WORLDGEOMETRY, 256, 256),
        imageClassificate_t("", "Other", IMAGE_CLASSIFICATION.IC_OTHER, 256, 256)
    )
    const val NORMAL_MAP_SIZE = 32
    const val QUADRATIC_HEIGHT = 4

    /*
     ================
     R_QuadraticImage

     ================
     */
    const val QUADRATIC_WIDTH = 32

    /*
     ================
     FogFraction

     Height values below zero are inside the fog volume
     ================
     */
    const val RAMP_RANGE = 8f
    val imageFilter: Array<String?>? = arrayOf(
        "GL_LINEAR_MIPMAP_NEAREST",
        "GL_LINEAR_MIPMAP_LINEAR",
        "GL_NEAREST",
        "GL_LINEAR",
        "GL_NEAREST_MIPMAP_NEAREST",
        "GL_NEAREST_MIPMAP_LINEAR",
        null
    )

    fun ClassifyImage(name: String?): Int {
        val str: idStr
        str = idStr(name)
        for (i in 0 until IMAGE_CLASSIFICATION.IC_COUNT) {
            if (str.Find(Image_init.IC_Info[i].rootPath, false) == 0) {
                return Image_init.IC_Info[i].type
            }
        }
        return IMAGE_CLASSIFICATION.IC_OTHER
    }

    /**
     * * NORMALIZATION CUBE MAP CONSTRUCTION **
     */
    /* Given a cube map face index, cube map size, and integer 2D face position,
     * return the cooresponding normalized vector.
     */
    fun getCubeVector(i: Int, cubesize: Int, x: Int, y: Int, vector: FloatArray?) {
        val s: Float
        val t: Float
        val sc: Float
        val tc: Float
        val mag: Float
        s = (x.toFloat() + 0.5f) / cubesize.toFloat()
        t = (y.toFloat() + 0.5f) / cubesize.toFloat()
        sc = s * 2.0f - 1.0f
        tc = t * 2.0f - 1.0f
        when (i) {
            0 -> {
                vector.get(0) = 1.0f
                vector.get(1) = -tc
                vector.get(2) = -sc
            }
            1 -> {
                vector.get(0) = -1.0f
                vector.get(1) = -tc
                vector.get(2) = sc
            }
            2 -> {
                vector.get(0) = sc
                vector.get(1) = 1.0f
                vector.get(2) = tc
            }
            3 -> {
                vector.get(0) = sc
                vector.get(1) = -1.0f
                vector.get(2) = -tc
            }
            4 -> {
                vector.get(0) = sc
                vector.get(1) = -tc
                vector.get(2) = 1.0f
            }
            5 -> {
                vector.get(0) = -sc
                vector.get(1) = -tc
                vector.get(2) = -1.0f
            }
        }
        mag =
            idMath.InvSqrt(vector.get(0) * vector.get(0) + vector.get(1) * vector.get(1) + vector.get(2) * vector.get(2))
        vector.get(0) *= mag
        vector.get(1) *= mag
        vector.get(2) *= mag
    }

    fun FogFraction(viewHeight: Float, targetHeight: Float): Float {
        val total = Math.abs(targetHeight - viewHeight)

//	return targetHeight >= 0 ? 0 : 1.0;
        // only ranges that cross the ramp range are special
        if (targetHeight > 0 && viewHeight > 0) {
            return 0.0f
        }
        if (targetHeight < -Image_init.RAMP_RANGE && viewHeight < -Image_init.RAMP_RANGE) {
            return 1.0f
        }
        val above: Float
        above = if (targetHeight > 0) {
            targetHeight
        } else if (viewHeight > 0) {
            viewHeight
        } else {
            0f
        }
        var rampTop: Float
        var rampBottom: Float
        if (viewHeight > targetHeight) {
            rampTop = viewHeight
            rampBottom = targetHeight
        } else {
            rampTop = targetHeight
            rampBottom = viewHeight
        }
        if (rampTop > 0) {
            rampTop = 0f
        }
        if (rampBottom < -Image_init.RAMP_RANGE) {
            rampBottom = -Image_init.RAMP_RANGE
        }
        val rampSlope = 1.0f / Image_init.RAMP_RANGE
        if (0.0f == total) {
            return -viewHeight * rampSlope
        }
        val ramp = (1.0f - (rampTop * rampSlope + rampBottom * rampSlope) * -0.5f) * (rampTop - rampBottom)
        var frac = (total - above - ramp) / total

        // after it gets moderately deep, always use full value
        val deepest = if (viewHeight < targetHeight) viewHeight else targetHeight
        val deepFrac = deepest / Image_init.DEEP_RANGE
        if (deepFrac >= 1.0) {
            return 1.0f
        }
        frac = frac * (1.0f - deepFrac) + deepFrac
        return frac
    }

    internal object IMAGE_CLASSIFICATION {
        const val IC_COUNT = 9
        const val IC_GUIS = 6
        const val IC_ITEMS = 4
        const val IC_MODELGEOMETRY = 3
        const val IC_MODELSOTHER = 5
        const val IC_MONSTER = 2
        const val IC_NPC = 0
        const val IC_OTHER = 8
        const val IC_WEAPON = 1
        const val IC_WORLDGEOMETRY = 7
    }

    internal class imageClassificate_t(
        var rootPath: String?,
        var desc: String?,
        var type: Int,
        var maxWidth: Int,
        var maxHeight: Int
    )

    internal class intList : idList<Int?>()

    /*
     ===============
     R_ListImages_f
     ===============
     */
    internal class R_ListImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var j: Int
            var partialSize: Int
            var image: idImage?
            var totalSize: Int
            var count = 0
            var matchTag = 0
            var uncompressedOnly = false
            var unloaded = false
            var partial = false
            var cached = false
            var uncached = false
            var failed = false
            var touched = false
            var sorted = false
            var duplicated = false
            var byClassification = false
            var overSized = false
            if (args.Argc() == 1) {
            } else if (args.Argc() == 2) {
                if (idStr.Companion.Icmp(args.Argv(1), "uncompressed") == 0) {
                    uncompressedOnly = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "sorted") == 0) {
                    sorted = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "partial") == 0) {
                    partial = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "unloaded") == 0) {
                    unloaded = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "cached") == 0) {
                    cached = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "uncached") == 0) {
                    uncached = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "tagged") == 0) {
                    matchTag = 1
                } else if (idStr.Companion.Icmp(args.Argv(1), "duplicated") == 0) {
                    duplicated = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "touched") == 0) {
                    touched = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "classify") == 0) {
                    byClassification = true
                    sorted = true
                } else if (idStr.Companion.Icmp(args.Argv(1), "oversized") == 0) {
                    byClassification = true
                    sorted = true
                    overSized = true
                } else {
                    failed = true
                }
            } else {
                failed = true
            }
            if (failed) {
                idLib.common.Printf("usage: listImages [ sorted | partial | unloaded | cached | uncached | tagged | duplicated | touched | classify | showOverSized ]\n")
                return
            }
            val header = "       -w-- -h-- filt -fmt-- wrap  size --name-------\n"
            idLib.common.Printf("\n%s", header)
            totalSize = 0

//	sortedImage_t	[]sortedArray = (sortedImage_t *)alloca( sizeof( sortedImage_t ) * globalImages.images.Num() );
            val sortedArray = arrayOfNulls<sortedImage_t?>(Image.globalImages.images.Num())
            i = 0
            while (i < Image.globalImages.images.Num()) {
                image = Image.globalImages.images.oGet(i)
                if (uncompressedOnly) {
                    if (image.internalFormat >= EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT && image.internalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
                        || image.internalFormat == 0x80E5
                    ) {
                        i++
                        continue
                    }
                }
                if (matchTag != 0 && image.classification != matchTag) {
                    i++
                    continue
                }
                if (unloaded && image.texNum != idImage.Companion.TEXTURE_NOT_LOADED) {
                    i++
                    continue
                }
                if (partial && !image.isPartialImage) {
                    i++
                    continue
                }
                if (cached && (null == image.partialImage || image.texNum == idImage.Companion.TEXTURE_NOT_LOADED)) {
                    i++
                    continue
                }
                if (uncached && (null == image.partialImage || image.texNum != idImage.Companion.TEXTURE_NOT_LOADED)) {
                    i++
                    continue
                }

                // only print duplicates (from mismatched wrap / clamp, etc)
                if (duplicated) {
//			int j;
                    j = i + 1
                    while (j < Image.globalImages.images.Num()) {
                        if (idStr.Companion.Icmp(image.imgName, Image.globalImages.images.oGet(j).imgName) == 0) {
                            break
                        }
                        j++
                    }
                    if (j == Image.globalImages.images.Num()) {
                        i++
                        continue
                    }
                }

                // "listimages touched" will list only images bound since the last "listimages touched" call
                if (touched) {
                    if (image.bindCount == 0) {
                        i++
                        continue
                    }
                    image.bindCount = 0
                }
                if (sorted) {
                    sortedArray[count].image = image
                    sortedArray[count].size = image.StorageSize()
                } else {
                    idLib.common.Printf("%4d:", i)
                    image.Print()
                }
                totalSize += image.StorageSize()
                count++
                i++
            }
            if (sorted) {
                Arrays.sort(
                    sortedArray,
                    0,
                    count,
                    R_QsortImageSizes()
                ) //qsort(sortedArray, count, sizeof(sortedImage_t), R_QsortImageSizes);
                partialSize = 0
                i = 0
                while (i < count) {
                    idLib.common.Printf("%4d:", i)
                    sortedArray[i].image.Print()
                    partialSize += sortedArray[i].image.StorageSize()
                    if ((i + 1) % 10 == 0) {
                        idLib.common.Printf(
                            "-------- %5.1f of %5.1f megs --------\n",
                            partialSize / (1024 * 1024.0), totalSize / (1024 * 1024.0)
                        )
                    }
                    i++
                }
            }
            idLib.common.Printf("%s", header)
            idLib.common.Printf(" %d images (%d total)\n", count, Image.globalImages.images.Num())
            idLib.common.Printf(" %5.1f total megabytes of images\n\n\n", totalSize / (1024 * 1024.0))
            if (byClassification) {
                val classifications: Array<idList<Int?>?> = arrayOfNulls<idList<*>?>(IMAGE_CLASSIFICATION.IC_COUNT)
                i = 0
                while (i < count) {
                    val cl = Image_init.ClassifyImage(sortedArray[i].image.imgName.toString())
                    classifications[cl].Append(i)
                    i++
                }
                i = 0
                while (i < IMAGE_CLASSIFICATION.IC_COUNT) {
                    partialSize = 0
                    val overSizedList = idList<Int?>()
                    j = 0
                    while (j < classifications[i].Num()) {
                        partialSize += sortedArray.get(classifications[i].oGet(j)).image.StorageSize()
                        if (overSized) {
                            if (sortedArray.get(classifications[i].oGet(j)).image.uploadWidth.getVal() > Image_init.IC_Info[i].maxWidth && sortedArray.get(
                                    classifications[i].oGet(j)
                                ).image.uploadHeight.getVal() > Image_init.IC_Info[i].maxHeight
                            ) {
                                overSizedList.Append(classifications[i].oGet(j))
                            }
                        }
                        j++
                    }
                    idLib.common.Printf(
                        " Classification %s contains %d images using %5.1f megabytes\n",
                        Image_init.IC_Info[i].desc,
                        classifications[i].Num(),
                        partialSize / (1024 * 1024.0)
                    )
                    if (overSized && overSizedList.Num() != 0) {
                        idLib.common.Printf("  The following images may be oversized\n")
                        j = 0
                        while (j < overSizedList.Num()) {
                            idLib.common.Printf("    ")
                            sortedArray.get(overSizedList.oGet(j)).image.Print()
                            idLib.common.Printf("\n")
                            j++
                        }
                    }
                    i++
                }
            }
        }

        companion object {
            private val instance: cmdFunction_t? = R_ListImages_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===============
     R_CombineCubeImages_f

     Used to combine animations of six separate tga files into
     a serials of 6x taller tga files, for preparation to roq compress
     ===============
     */
    internal class R_CombineCubeImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            if (args.Argc() != 2) {
                idLib.common.Printf("usage: combineCubeImages <baseName>\n")
                idLib.common.Printf(" combines basename[1-6][0001-9999].tga to basenameCM[0001-9999].tga\n")
                idLib.common.Printf(" 1: forward 2:right 3:back 4:left 5:up 6:down\n")
                return
            }
            val baseName = idStr(args.Argv(1))
            idLib.common.SetRefreshOnPrint(true)
            for (frameNum in 1..9999) {
//		final char	[]filename=new char[MAX_IMAGE_NAME];
                var filename: String
                val pics = arrayOfNulls<ByteBuffer?>(6) //Good God!
                val width = intArrayOf(0)
                val height = intArrayOf(0)
                var side: Int
                val orderRemap = intArrayOf(1, 3, 4, 2, 5, 6)
                side = 0
                while (side < 6) {
                    filename = String.format("%s%d%04i.tga", baseName, orderRemap[side], frameNum)
                    idLib.common.Printf("reading %s\n", filename)
                    pics[side] = Image_files.R_LoadImage(filename, width, height, null, true)
                    if (TempDump.NOT(pics[side])) {
                        idLib.common.Printf("not found.\n")
                        break
                    }
                    when (side) {
                        0 -> Image_process.R_RotatePic(pics[side], width[0])
                        1 -> {
                            Image_process.R_RotatePic(pics[side], width[0])
                            Image_process.R_HorizontalFlip(pics[side], width[0], height[0])
                            Image_process.R_VerticalFlip(pics[side], width[0], height[0])
                        }
                        2 -> Image_process.R_VerticalFlip(pics[side], width[0], height[0])
                        3 -> Image_process.R_HorizontalFlip(pics[side], width[0], height[0])
                        4 -> Image_process.R_RotatePic(pics[side], width[0])
                        5 -> Image_process.R_RotatePic(pics[side], width[0])
                    }
                    side++
                }
                if (side != 6) {
                    val i = 0
                    while (i < side) {
                        pics[side] = null //Mem_Free(pics[side]);
                        side++
                    }
                    break
                }
                var combined =
                    ByteBuffer.allocate(width[0] * height[0] * 6 * 4) // Mem_Alloc(width[0] * height[0] * 6 * 4);
                val length = width[0] * height[0] * 4
                side = 0
                while (side < 6) {

//			memcpy( combined+width*height*4*side, pics[side], width*height*4 );
                    combined.position(length * side)
                    combined.put(pics[side].array(), 0, length)
                    pics[side] = null //Mem_Free(pics[side]);
                    side++
                }
                filename = String.format("%sCM%04i.tga", baseName, frameNum)
                idLib.common.Printf("writing %s\n", filename)
                R_WriteTGA(filename, combined, width[0], height[0] * 6)
                combined = null //Mem_Free(combined);
            }
            idLib.common.SetRefreshOnPrint(false)
        }

        companion object {
            private val instance: cmdFunction_t? = R_CombineCubeImages_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===============
     R_ReloadImages_f

     Regenerate all images that came directly from files that have changed, so
     any saved changes will show up in place.

     New r_texturesize/r_texturedepth variables will take effect on reload

     reloadImages <all>
     ===============
     */
    internal class R_ReloadImages_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var image: idImage?
            var all: Boolean
            var checkPrecompressed: Boolean

            // this probably isn't necessary...
            Image.globalImages.ChangeTextureFilter()
            all = false
            checkPrecompressed = false // if we are doing this as a vid_restart, look for precompressed like normal
            if (args.Argc() == 2) {
                if (0 == idStr.Companion.Icmp(args.Argv(1), "all")) {
                    all = true
                } else if (0 == idStr.Companion.Icmp(args.Argv(1), "reload")) {
                    all = true
                    checkPrecompressed = true
                } else {
                    idLib.common.Printf("USAGE: reloadImages <all>\n")
                    return
                }
            }
            i = 0
            while (i < Image.globalImages.images.Num()) {
                image = Image.globalImages.images.oGet(i)
                image.Reload(checkPrecompressed, all)
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t? = R_ReloadImages_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    internal class sortedImage_t {
        var image: idImage? = null
        var size = 0
    }

    /*

     ================
     R_RampImage

     Creates a 0-255 ramp image
     ================
     */
    internal class R_RampImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            val data = ByteBuffer.allocate(256 * 4)
            x = 0
            while (x < 256) {
                data.putInt(x * 4, x)
                x++
            }
            image.GenerateImage(
                data,
                256,
                1,
                textureFilter_t.TF_NEAREST,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_RampImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     ================
     R_SpecularTableImage

     Creates a ramp that matches our fudged specular calculation
     ================
     */
    internal class R_SpecularTableImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            val data = ByteBuffer.allocate(256 * 4)
            x = 0
            while (x < 256) {
                var f = x / 255f
                if (false) {
                    f = Math.pow(f.toDouble(), 16.0).toFloat()
                } else {
                    // this is the behavior of the hacked up fragment programs that
                    // can't really do a power function
                    f = (f - 0.75f) * 4
                    if (f < 0) {
                        f = 0f
                    }
                    f = f * f
                }
                val b = (f * 255).toInt()
                data.putInt(
                    x * 4,
                    b
                ) //TODO:check whether setting 4 bytes to an int is the same as what we're doing here!
                x++
            }
            image.GenerateImage(
                data,
                256,
                1,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_SpecularTableImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     ================
     R_Specular2DTableImage

     Create a 2D table that calculates ( reflection dot , specularity )
     ================
     */
    internal class R_Specular2DTableImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            var y: Int
            val data = ByteBuffer.allocate(256 * 256 * 4)

//	memset( data, 0, sizeof( data ) );
            x = 0
            while (x < 256) {
                val f = x / 255.0f
                y = 0
                while (y < 256) {
                    val b = (Math.pow(f.toDouble(), y.toDouble()) * 255.0f).toInt()
                    if (b == 0) {
                        // as soon as b equals zero all remaining values in this column are going to be zero
                        // we early out to avoid pow() underflows
                        break
                    }
                    data.putInt(y * 4 + x * 256, b)
                    y++
                }
                x++
            }
            image.GenerateImage(
                data,
                256,
                256,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_Specular2DTableImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     ================
     R_AlphaRampImage

     Creates a 0-255 ramp image
     ================
     */
    internal class R_AlphaRampImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            val data = ByteBuffer.allocate(256 * 4)
            x = 0
            while (x < 256) {
                data.putInt(x * 4, x)
                x++
            }
            image.GenerateImage(
                data,
                256,
                1,
                textureFilter_t.TF_NEAREST,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_AlphaRampImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_DefaultImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            image.MakeDefault()
        }

        companion object {
            private val instance: GeneratorFunction? = R_DefaultImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_WhiteImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = ByteBuffer.allocate(idImage.Companion.DEFAULT_SIZE * idImage.Companion.DEFAULT_SIZE * 4)

            // solid white texture
//	memset( data, 255, sizeof( data ) );
            Arrays.fill(data.array(), 255.toByte())
            image.GenerateImage(
                data,
                idImage.Companion.DEFAULT_SIZE,
                idImage.Companion.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_WhiteImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_BlackImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = ByteBuffer.allocate(idImage.Companion.DEFAULT_SIZE * idImage.Companion.DEFAULT_SIZE * 4)

            // solid black texture
//	memset( data, 0, sizeof( data ) );
            image.GenerateImage(
                data,
                idImage.Companion.DEFAULT_SIZE,
                idImage.Companion.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_DEFAULT
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_BlackImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_BorderClampImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = Array<Array<ByteArray?>?>(Image_init.BORDER_CLAMP_SIZE) {
                Array(Image_init.BORDER_CLAMP_SIZE) {
                    ByteArray(4)
                }
            }

            // solid white texture with a single pixel black border
//	memset( data, 255, sizeof( data ) );
            for (a in 0 until data[0].length) {
                for (b in 0 until data[0].get(0).length) {
                    data[a].get(b) = byteArrayOf(-1, -1, -1, -1)
                }
            }
            for (i in 0 until Image_init.BORDER_CLAMP_SIZE) {
                data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(3) = 0
                data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(2) =
                    data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(3)
                data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(1) =
                    data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(2)
                data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(0) =
                    data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(1)
                data[0].get(i).get(3) = data[Image_init.BORDER_CLAMP_SIZE - 1].get(i).get(0)
                data[0].get(i).get(2) = data[0].get(i).get(3)
                data[0].get(i).get(1) = data[0].get(i).get(2)
                data[0].get(i).get(0) = data[0].get(i).get(1)
                data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(3) = data[0].get(i).get(0)
                data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(2) =
                    data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(3)
                data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(1) =
                    data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(2)
                data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(0) =
                    data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(1)
                data[i].get(0).get(3) = data[i].get(Image_init.BORDER_CLAMP_SIZE - 1).get(0)
                data[i].get(0).get(2) = data[i].get(0).get(3)
                data[i].get(0).get(1) = data[i].get(0).get(2)
                data[i].get(0).get(0) = data[i].get(0).get(1)
            }
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                Image_init.BORDER_CLAMP_SIZE,
                Image_init.BORDER_CLAMP_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP_TO_BORDER,
                textureDepth_t.TD_DEFAULT
            )
            if (!tr_local.glConfig.isInitialized) {
                // can't call qglTexParameterfv yet
                return
            }
            // explicit zero border
            val color = BufferUtils.createFloatBuffer(4)
            //            color[0] = color[1] = color[2] = color[3] = 0.0f;
            qgl.qglTexParameterfv(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, color)
            //            qglTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, 0.0f);
        }

        companion object {
            private val instance: GeneratorFunction? = R_BorderClampImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_RGBA8Image private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = ByteBuffer.allocate(idImage.Companion.DEFAULT_SIZE * idImage.Companion.DEFAULT_SIZE * 4)

//	memset( data, 0, sizeof( data ) );
            data.put(0, 16.toByte())
            data.put(1, 32.toByte())
            data.put(2, 48.toByte())
            data.put(3, 96.toByte())
            image.GenerateImage(
                data,
                idImage.Companion.DEFAULT_SIZE,
                idImage.Companion.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_RGBA8Image()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_RGB8Image private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = ByteBuffer.allocate(idImage.Companion.DEFAULT_SIZE * idImage.Companion.DEFAULT_SIZE * 4)

//	memset( data, 0, sizeof( data ) );
            data.put(0, 16.toByte())
            data.put(1, 32.toByte())
            data.put(2, 48.toByte())
            data.put(3, 255.toByte())
            image.GenerateImage(
                data,
                idImage.Companion.DEFAULT_SIZE,
                idImage.Companion.DEFAULT_SIZE,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_RGB8Image()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_AlphaNotchImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = ByteBuffer.allocate(2 * 4)

            // this is used for alpha test clip planes
            data.put(0, 255.toByte())
            data.put(1, 255.toByte())
            data.put(2, 255.toByte())
            data.put(3, 0.toByte())
            data.put(4, 255.toByte())
            data.put(5, 255.toByte())
            data.put(6, 255.toByte())
            data.put(7, 255.toByte())
            image.GenerateImage(
                data,
                2,
                1,
                textureFilter_t.TF_NEAREST,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_AlphaNotchImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_FlatNormalImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val data = Array<Array<ByteArray?>?>(idImage.Companion.DEFAULT_SIZE) {
                Array(idImage.Companion.DEFAULT_SIZE) {
                    ByteArray(4)
                }
            }
            var i: Int
            val red = if (idImageManager.Companion.image_useNormalCompression.GetInteger() == 1) 0 else 3
            val alpha = if (red == 0) 3 else 0
            // flat normal map for default bunp mapping
            i = 0
            while (i < 4) {
                data[0].get(i).get(red) = 128.toByte()
                data[0].get(i).get(1) = 128.toByte()
                data[0].get(i).get(2) = 255.toByte()
                data[0].get(i).get(alpha) = 255.toByte()
                i++
            }
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                2,
                2,
                textureFilter_t.TF_DEFAULT,
                true,
                textureRepeat_t.TR_REPEAT,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_FlatNormalImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_AmbientNormalImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
//            final byte[][][] data = new byte[DEFAULT_SIZE][DEFAULT_SIZE][4];
            val data = ByteArray(idImage.Companion.DEFAULT_SIZE)
            var i: Int
            val red = if (idImageManager.Companion.image_useNormalCompression.GetInteger() == 1) 0 else 3
            val alpha = if (red == 0) 3 else 0
            // flat normal map for default bunp mapping
            i = 0
            while (i < idImage.Companion.DEFAULT_SIZE) {
                data[i + red] = (255 * tr_local.tr.ambientLightVector.oGet(0)).toByte()
                data[i + 1] = (255 * tr_local.tr.ambientLightVector.oGet(1)).toByte()
                data[i + 2] = (255 * tr_local.tr.ambientLightVector.oGet(2)).toByte()
                data[i + alpha] = 255.toByte()
                i += 4
            }
            val pics = arrayOfNulls<ByteBuffer?>(6)
            i = 0
            while (i < 6) {
                pics[i] = TempDump.wrapToNativeBuffer(data) //TODO: wtf does this data[0][0] do?
                i++
            }
            // this must be a cube map for fragment programs to simply substitute for the normalization cube map
            image.GenerateCubeImage(pics, 2, textureFilter_t.TF_DEFAULT, true, textureDepth_t.TD_HIGH_QUALITY)
        }

        companion object {
            private val instance: GeneratorFunction? = R_AmbientNormalImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /* Initialize a cube map texture object that generates RGB values
     * that when expanded to a [-1,1] range in the register combiners
     * form a normalized vector matching the per-pixel vector used to
     * access the cube map.
     */
    internal class makeNormalizeVectorCubeMap private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            val vector = FloatArray(3)
            var i: Int
            var x: Int
            var y: Int
            val pixels = arrayOfNulls<ByteBuffer?>(6) //[size*size*4*6];
            val size: Int
            size = Image_init.NORMAL_MAP_SIZE

//	pixels[0] = (GLubyte[]) Mem_Alloc(size*size*4*6);
            i = 0
            while (i < 6) {
                pixels[i] = BufferUtils.createByteBuffer(size * size * 4)
                y = 0
                while (y < size) {
                    x = 0
                    while (x < size) {
                        Image_init.getCubeVector(i, size, x, y, vector)
                        pixels[i].put(4 * (y * size + x) + 0, (128 + 127 * vector[0]).toInt().toByte())
                        pixels[i].put(4 * (y * size + x) + 1, (128 + 127 * vector[1]).toInt().toByte())
                        pixels[i].put(4 * (y * size + x) + 2, (128 + 127 * vector[2]).toInt().toByte())
                        pixels[i].put(4 * (y * size + x) + 3, 255.toByte())
                        x++
                    }
                    y++
                }
                i++
            }
            image.GenerateCubeImage(pixels, size, textureFilter_t.TF_LINEAR, false, textureDepth_t.TD_HIGH_QUALITY)

//            Mem_Free(pixels[0]);
        }

        companion object {
            private val instance: GeneratorFunction? = makeNormalizeVectorCubeMap()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     ================
     R_CreateNoFalloffImage

     This is a solid white texture that is zero clamped.
     ================
     */
    internal class R_CreateNoFalloffImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            var y: Int
            val data = Array<Array<ByteArray?>?>(16) { Array(tr_local.FALLOFF_TEXTURE_SIZE) { ByteArray(4) } }

//	memset( data, 0, sizeof( data ) );
            x = 1
            while (x < tr_local.FALLOFF_TEXTURE_SIZE - 1) {
                y = 1
                while (y < 15) {
                    data[y].get(x).get(0) = 255.toByte()
                    data[y].get(x).get(1) = 255.toByte()
                    data[y].get(x).get(2) = 255.toByte()
                    data[y].get(x).get(3) = 255.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                tr_local.FALLOFF_TEXTURE_SIZE,
                16,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_CLAMP_TO_ZERO,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_CreateNoFalloffImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_FogImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            var y: Int
            val data = Array<Array<ByteArray?>?>(Image_init.FOG_SIZE) { Array(Image_init.FOG_SIZE) { ByteArray(4) } }
            var b: Int
            val step = FloatArray(256)
            var i: Int
            var remaining = 1.0f
            i = 0
            while (i < 256) {
                step[i] = remaining
                remaining *= 0.982f
                i++
            }
            x = 0
            while (x < Image_init.FOG_SIZE) {
                y = 0
                while (y < Image_init.FOG_SIZE) {
                    var d: Float
                    d = idMath.Sqrt(
                        ((x - Image_init.FOG_SIZE / 2) * (x - Image_init.FOG_SIZE / 2)
                                + (y - Image_init.FOG_SIZE / 2) * (y - Image_init.FOG_SIZE / 2)).toFloat()
                    )
                    d /= (Image_init.FOG_SIZE / 2 - 1).toFloat()
                    b = (d * 255).toInt().toByte().toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    b = (255 * (1.0 - step[b])).toInt().toByte().toInt()
                    if (x == 0 || x == Image_init.FOG_SIZE - 1 || y == 0 || y == Image_init.FOG_SIZE - 1) {
                        b = 255 // avoid clamping issues
                    }
                    data[y].get(x).get(2) = 255.toByte()
                    data[y].get(x).get(1) = data[y].get(x).get(2)
                    data[y].get(x).get(0) = data[y].get(x).get(1)
                    data[y].get(x).get(3) = b.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                Image_init.FOG_SIZE,
                Image_init.FOG_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_FogImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     ================
     R_FogEnterImage

     Modulate the fog alpha density based on the distance of the
     start and end points to the terminator plane
     ================
     */
    internal class R_FogEnterImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            var y: Int
            val data =
                Array<Array<ByteArray?>?>(tr_local.FOG_ENTER_SIZE) { Array(tr_local.FOG_ENTER_SIZE) { ByteArray(4) } }
            var b: Int
            x = 0
            while (x < tr_local.FOG_ENTER_SIZE) {
                y = 0
                while (y < tr_local.FOG_ENTER_SIZE) {
                    var d: Float
                    d = Image_init.FogFraction(
                        (x - tr_local.FOG_ENTER_SIZE / 2).toFloat(),
                        (y - tr_local.FOG_ENTER_SIZE / 2).toFloat()
                    )
                    b = (d * 255).toInt().toByte().toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    data[y].get(x).get(2) = 255.toByte()
                    data[y].get(x).get(1) = data[y].get(x).get(2)
                    data[y].get(x).get(0) = data[y].get(x).get(1)
                    data[y].get(x).get(3) = b.toByte()
                    y++
                }
                x++
            }

            // if mipmapped, acutely viewed surfaces fade wrong
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                tr_local.FOG_ENTER_SIZE,
                tr_local.FOG_ENTER_SIZE,
                textureFilter_t.TF_LINEAR,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_FogEnterImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    internal class R_QuadraticImage private constructor() : GeneratorFunction() {
        override fun run(image: idImage?) {
            var x: Int
            var y: Int
            val data =
                Array<Array<ByteArray?>?>(Image_init.QUADRATIC_HEIGHT) { Array(Image_init.QUADRATIC_WIDTH) { ByteArray(4) } }
            var b: Int
            x = 0
            while (x < Image_init.QUADRATIC_WIDTH) {
                y = 0
                while (y < Image_init.QUADRATIC_HEIGHT) {
                    var d: Float
                    d = x - (Image_init.QUADRATIC_WIDTH / 2 - 0.5f)
                    d = Math.abs(d)
                    d -= 0.5f
                    d /= (Image_init.QUADRATIC_WIDTH / 2).toFloat()
                    d = 1.0f - d
                    d = d * d
                    b = (d * 255).toInt().toByte().toInt()
                    if (b <= 0) {
                        b = 0
                    } else if (b > 255) {
                        b = 255
                    }
                    data[y].get(x).get(2) = b.toByte()
                    data[y].get(x).get(1) = data[y].get(x).get(2)
                    data[y].get(x).get(0) = data[y].get(x).get(1)
                    data[y].get(x).get(3) = 255.toByte()
                    y++
                }
                x++
            }
            image.GenerateImage(
                ByteBuffer.wrap(TempDump.flatten(data)),
                Image_init.QUADRATIC_WIDTH,
                Image_init.QUADRATIC_HEIGHT,
                textureFilter_t.TF_DEFAULT,
                false,
                textureRepeat_t.TR_CLAMP,
                textureDepth_t.TD_HIGH_QUALITY
            )
        }

        companion object {
            private val instance: GeneratorFunction? = R_QuadraticImage()
            fun getInstance(): GeneratorFunction? {
                return instance
            }
        }
    }

    /*
     =======================
     R_QsortImageSizes

     =======================
     */
    internal class R_QsortImageSizes : cmp_t<sortedImage_t?> {
        override fun compare(ea: sortedImage_t?, eb: sortedImage_t?): Int {
            if (ea.size > eb.size) {
                return -1
            }
            return if (ea.size < eb.size) {
                1
            } else idStr.Companion.Icmp(ea.image.imgName, eb.image.imgName)
        }
    }
}