package neo.Sound

import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_local.waveformatex_s
import neo.Sound.snd_system.idSoundSystemLocal
import neo.Sound.snd_wavefile.idWaveFile
import neo.TempDump
import neo.framework.BuildDefines
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.framework.DeclManager
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.Lib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Simd
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import java.nio.ByteBuffer

/**
 *
 */
object snd_cache {
    //    static final boolean USE_SOUND_CACHE_ALLOCATOR = true;
    //    static final idDynamicBlockAlloc<Byte> soundCacheAllocator;
    //
    //    static {
    //        if (USE_SOUND_CACHE_ALLOCATOR) {
    //            soundCacheAllocator = new idDynamicBlockAlloc<>(1 << 20, 1 << 10);
    ////        } else {
    ////            soundCacheAllocator = new idDynamicAlloc<>(1 << 20, 1 << 10);
    //        }
    //    }
    /*
     ===================================================================================

     This class holds the actual wavefile bitmap, size, and info.

     ===================================================================================
     */
    const val SCACHE_SIZE = Simd.MIXBUFFER_SAMPLES * 20 // 1/2 of a second (aroundabout)

    class idSoundSample {
        var amplitudeData // precomputed min,max amplitude pairs
                : ByteBuffer
        var defaultSound: Boolean
        var hardwareBuffer: Boolean
        var levelLoadReferenced // so we can tell which samples aren't needed any more
                : Boolean
        val name // name of the sample file
                : idStr = idStr()
        var nonCacheData // if it's not cached
                : ByteBuffer

        //
        var objectInfo // what are we caching
                : waveformatex_s = waveformatex_s()
        var objectMemSize // object size in memory
                : Int
        var objectSize // size of waveform in samples, excludes the header
                : Int
        var onDemand: Boolean
        var   /*ALuint*/openalBuffer // openal buffer
                : Int
        var purged: Boolean
        var   /*ID_TIME_T*/timestamp // the most recent of all images used in creation, for reloadImages command
                : Long = 0

        // ~idSoundSample();
        fun LengthIn44kHzSamples(): Int {
            // objectSize is samples
            return if (objectInfo.nSamplesPerSec == 11025) {
                objectSize shl 2
            } else if (objectInfo.nSamplesPerSec == 22050) {
                objectSize shl 1
            } else {
                objectSize shl 0
            }
        }

        fun  /*ID_TIME_T*/GetNewTimeStamp(): Long {
            val timestamp = longArrayOf(0)
            FileSystem_h.fileSystem.ReadFile(name.toString(), null, timestamp)
            if (timestamp[0] == FileSystem_h.FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                val oggName = idStr(name)
                oggName.SetFileExtension(".ogg")
                FileSystem_h.fileSystem.ReadFile(oggName.toString(), null, timestamp)
            }
            return timestamp[0]
        }

        // turns it into a beep	
        fun MakeDefault() {
            var i: Int
            var v: Float
            var sample: Short

//	memset( &objectInfo, 0, sizeof( objectInfo ) );
            objectInfo = waveformatex_s()
            objectInfo.nChannels = 1
            objectInfo.wBitsPerSample = 16
            objectInfo.nSamplesPerSec = 44100
            objectSize = Simd.MIXBUFFER_SAMPLES * 2
            objectMemSize = objectSize * 2 //* sizeof(short);
            nonCacheData = BufferUtils.createByteBuffer(objectMemSize) //soundCacheAllocator.Alloc(objectMemSize);
            val ncd = nonCacheData.asShortBuffer()
            i = 0
            while (i < Simd.MIXBUFFER_SAMPLES) {
                v = Math.sin((idMath.PI * 2 * i / 64).toDouble()).toFloat()
                sample = (v * 0x4000).toInt().toShort()
                ncd.put(i * 2 + 0, sample)
                ncd.put(i * 2 + 1, sample)
                i++
            }
            if (idSoundSystemLocal.useOpenAL) {
                AL10.alGetError()
                //                alGenBuffers(1, openalBuffer);
                openalBuffer = AL10.alGenBuffers()
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    Common.common.Error("idSoundCache: error generating OpenAL hardware buffer")
                }
                AL10.alGetError()
                //                alBufferData(openalBuffer, objectInfo.nChannels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, nonCacheData, objectMemSize, objectInfo.nSamplesPerSec);
                AL10.alBufferData(
                    openalBuffer /*  <<TODO>>   */,
                    if (objectInfo.nChannels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                    nonCacheData,
                    objectInfo.nSamplesPerSec
                )
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    Common.common.Error("idSoundCache: error loading data into OpenAL hardware buffer")
                } else {
                    hardwareBuffer = true
                }
            }
            defaultSound = true
        }

        /*
         ===================
         idSoundSample::Load

         Loads based on name, possibly doing a MakeDefault if necessary
         ===================
         */
        // loads the current sound based on name
        fun Load() {
            defaultSound = false
            purged = false
            hardwareBuffer = false
            timestamp = GetNewTimeStamp()
            if (timestamp == FileSystem_h.FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                Common.common.Warning("Couldn't load sound '%s' using default", name)
                MakeDefault()
                return
            }

            // load it
            val fh = idWaveFile()
            val info = Array(1) { waveformatex_s() }
            if (fh.Open(name.toString(), info) == -1) {
                Common.common.Warning("Couldn't load sound '%s' using default", name)
                MakeDefault()
                return
            }
            if (info[0].nChannels != 1 && info[0].nChannels != 2) {
                Common.common.Warning("idSoundSample: %s has %d channels, using default", name, info[0].nChannels)
                fh.Close()
                MakeDefault()
                return
            }
            if (info[0].wBitsPerSample != 16) {
                Common.common.Warning(
                    "idSoundSample: %s is %dbits, expected 16bits using default",
                    name,
                    info[0].wBitsPerSample
                )
                fh.Close()
                MakeDefault()
                return
            }
            if (info[0].nSamplesPerSec != 44100 && info[0].nSamplesPerSec != 22050 && info[0].nSamplesPerSec != 11025) {
                Common.common.Warning(
                    "idSoundCache: %s is %dHz, expected 11025, 22050 or 44100 Hz. Using default",
                    name,
                    info[0].nSamplesPerSec
                )
                fh.Close()
                MakeDefault()
                return
            }
            objectInfo = info[0]
            objectSize = fh.GetOutputSize()
            objectMemSize = fh.GetMemorySize()
            nonCacheData = BufferUtils.createByteBuffer(objectMemSize) //soundCacheAllocator.Alloc( objectMemSize );
            val temp = ByteBuffer.allocate(objectMemSize)
            fh.Read(temp, objectMemSize, null)
            nonCacheData.put(temp).rewind()

            // optionally convert it to 22kHz to save memory
            CheckForDownSample()

            // create hardware audio buffers 
            if (idSoundSystemLocal.useOpenAL) {
                // PCM loads directly;
                if (objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_PCM) {
                    AL10.alGetError()
                    //                    alGenBuffers(1, openalBuffer);
                    openalBuffer = AL10.alGenBuffers()
                    if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                        Common.common.Error("idSoundCache: error generating OpenAL hardware buffer")
                    }
                    //                    if (alIsBuffer(openalBuffer)) {
                    if (AL10.alIsBuffer(openalBuffer)) {
                        AL10.alGetError()
                        //                        alBufferData(openalBuffer, objectInfo.nChannels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, nonCacheData, objectMemSize, objectInfo.nSamplesPerSec);
                        AL10.alBufferData(
                            openalBuffer,
                            if (objectInfo.nChannels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                            nonCacheData,
                            objectInfo.nSamplesPerSec
                        )
                        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                            Common.common.Error("idSoundCache: error loading data into OpenAL hardware buffer")
                        } else {
                            // Compute amplitude block size
                            val blockSize = 512 * objectInfo.nSamplesPerSec / 44100

                            // Allocate amplitude data array
                            amplitudeData =
                                BufferUtils.createByteBuffer((objectSize / blockSize + 1) * 2 * java.lang.Short.BYTES) //soundCacheAllocator.Alloc( ( objectSize / blockSize + 1 ) * 2 * sizeof( short) );

                            // Creating array of min/max amplitude pairs per blockSize samples
                            val ncd = nonCacheData.asShortBuffer()
                            var i: Int
                            i = 0
                            while (i < objectSize) {
                                var min: Short = 32767
                                var max: Short = -32768
                                var j: Int
                                j = 0
                                while (j < Lib.Min(objectSize - i, blockSize)) {
                                    min = Math.min(ncd[i + j].toInt(), min.toInt()).toShort()
                                    max = Math.max(ncd[i + j].toInt(), max.toInt()).toShort()
                                    j++
                                }
                                amplitudeData.putShort(i / blockSize * 2, min)
                                amplitudeData.putShort(i / blockSize * 2 + 1, max)
                                i += blockSize
                            }
                            hardwareBuffer = true
                        }
                    }
                }

                // OGG decompressed at load time (when smaller than s_decompressionLimit seconds, 6 seconds by default)
                if (objectInfo.wFormatTag == snd_local.WAVE_FORMAT_TAG_OGG) {
                    if (BuildDefines.MACOS_X && objectSize < objectInfo.nSamplesPerSec * idSoundSystemLocal.s_decompressionLimit.GetInteger()
                        || AL10.alIsExtensionPresent("EAX-RAM") && objectSize < objectInfo.nSamplesPerSec * idSoundSystemLocal.s_decompressionLimit.GetInteger()
                    ) {
                        AL10.alGetError()
                        openalBuffer = AL10.alGenBuffers()
                        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                            Common.common.Error("idSoundCache: error generating OpenAL hardware buffer")
                        }
                        if (AL10.alIsBuffer(openalBuffer)) {
                            val decoder: idSampleDecoder = idSampleDecoder.Alloc()
                            var destData =
                                BufferUtils.createByteBuffer((LengthIn44kHzSamples() + 1) * java.lang.Float.BYTES) //soundCacheAllocator.Alloc( ( LengthIn44kHzSamples() + 1 ) * sizeof( float ) );

                            // Decoder *always* outputs 44 kHz data
                            decoder.Decode(this, 0, LengthIn44kHzSamples(), destData.asFloatBuffer())

                            // Downsample back to original frequency (save memory)
                            if (objectInfo.nSamplesPerSec == 11025) {
                                for (i in 0 until objectSize) {
                                    if (destData.getFloat(i * 4) < -32768.0f) {
                                        destData.putShort(i, Short.MIN_VALUE)
                                    } else if (destData.getFloat(i * 4) > 32767.0f) {
                                        destData.putShort(i, Short.MAX_VALUE)
                                    } else {
                                        destData.putShort(i, idMath.FtoiFast(destData.getFloat(i * 4)).toShort())
                                    }
                                }
                            } else if (objectInfo.nSamplesPerSec == 22050) {
                                for (i in 0 until objectSize) {
                                    if (destData.getFloat(i * 2) < -32768.0f) {
                                        destData.putShort(i, Short.MIN_VALUE)
                                    } else if (destData.getFloat(i * 2) > 32767.0f) {
                                        destData.putShort(i, Short.MAX_VALUE)
                                    } else {
                                        destData.putShort(i, idMath.FtoiFast(destData.getFloat(i * 2)).toShort())
                                    }
                                }
                            } else {
                                for (i in 0 until objectSize) {
                                    if (destData.getFloat(i) < -32768.0f) {
                                        destData.putShort(i, Short.MIN_VALUE)
                                    } else if (destData.getFloat(i) > 32767.0f) {
                                        destData.putShort(i, Short.MAX_VALUE)
                                    } else {
                                        destData.putShort(i, idMath.FtoiFast(destData.getFloat(i)).toShort())
                                    }
                                }
                            }
                            AL10.alGetError()
                            //                            alBufferData(openalBuffer, objectInfo.nChannels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, destData, objectSize * sizeof(short), objectInfo.nSamplesPerSec);
                            AL10.alBufferData(
                                openalBuffer,
                                if (objectInfo.nChannels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                                destData,
                                objectInfo.nSamplesPerSec
                            )
                            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                                Common.common.Error("idSoundCache: error loading data into OpenAL hardware buffer")
                            } else {
                                // Compute amplitude block size
                                val blockSize = 512 * objectInfo.nSamplesPerSec / 44100

                                // Allocate amplitude data array
                                amplitudeData =
                                    BufferUtils.createByteBuffer((objectSize / blockSize + 1) * 2 * java.lang.Short.BYTES) //soundCacheAllocator.Alloc( ( objectSize / blockSize + 1 ) * 2 * sizeof( short ) );

                                // Creating array of min/max amplitude pairs per blockSize samples
                                var i: Int
                                i = 0
                                while (i < objectSize) {
                                    var min: Short = 32767
                                    var max: Short = -32768
                                    var j: Int
                                    j = 0
                                    while (j < Lib.Min(objectSize - i, blockSize)) {
                                        min = if (destData.getShort(i + j) < min) destData.getShort(i + j) else min
                                        max = if (destData.getShort(i + j) > max) destData.getShort(i + j) else max
                                        j++
                                    }
                                    amplitudeData.putShort(i / blockSize * 2, min)
                                    amplitudeData.putShort(i / blockSize * 2 + 1, max)
                                    i += blockSize
                                }
                                hardwareBuffer = true
                            }

//					soundCacheAllocator.Free( (byte *)destData );
                            destData.clear()
                            idSampleDecoder.Free(decoder)
                        }
                    }
                }

                // Free memory if sample was loaded into hardware
                if (hardwareBuffer) {
//			soundCacheAllocator.Free( nonCacheData );
                    nonCacheData.clear()
                }
            }
            fh.Close()
        }

        // reloads if timestamp has changed, or always if force
        fun Reload(force: Boolean) {
            if (!force) {
                val newTimestamp: Long

                // check the timestamp
                newTimestamp = GetNewTimeStamp()
                if (newTimestamp == FileSystem_h.FILE_NOT_FOUND_TIMESTAMP.toLong()) {
                    if (!defaultSound) {
                        Common.common.Warning("Couldn't load sound '%s' using default", name)
                        MakeDefault()
                    }
                    return
                }
                if (newTimestamp == timestamp) {
                    return  // don't need to reload it
                }
            }
            Common.common.Printf("reloading %s\n", name)
            PurgeSoundSample()
            Load()
        }

        fun PurgeSoundSample() {            // frees all data
            purged = true
            if (hardwareBuffer && idSoundSystemLocal.useOpenAL) {
                val error = AL10.alGetError()
                //                alDeleteBuffers(1, openalBuffer);
                AL10.alDeleteBuffers(openalBuffer)
                if (error != AL10.AL_NO_ERROR) {
                    Common.common.Error("idSoundCache: error unloading data from OpenAL hardware buffer")
                } else {
                    openalBuffer = 0
                    hardwareBuffer = false
                }
            }
            if (amplitudeData != null) {
//                soundCacheAllocator.Free(amplitudeData);
                amplitudeData.clear()
            }
            if (nonCacheData != null) {
//                soundCacheAllocator.Free(nonCacheData);
                nonCacheData.clear()
            }
        }

        fun CheckForDownSample() {        // down sample if required
            if (!idSoundSystemLocal.s_force22kHz.GetBool()) {
                return
            }
            if (objectInfo.wFormatTag != snd_local.WAVE_FORMAT_TAG_PCM || objectInfo.nSamplesPerSec != 44100) {
                return
            }
            val shortSamples = objectSize shr 1
            val converted = BufferUtils.createByteBuffer(shortSamples * 2) // soundCacheAllocator.Alloc(shortSamples);
            if (objectInfo.nChannels == 1) {
                for (i in 0 until shortSamples) {
                    converted.putShort(i, nonCacheData.getShort(i * 2))
                }
            } else {
                var i = 0
                while (i < shortSamples) {
                    converted.putShort(i + 0, nonCacheData.getShort(i * 2 + 0))
                    converted.putShort(i + 1, nonCacheData.getShort(i * 2 + 1))
                    i += 2
                }
            }
            //            soundCacheAllocator.Free(nonCacheData);
            nonCacheData = converted
            objectSize = objectSize shr 1
            objectMemSize = objectMemSize shr 1
            objectInfo.nAvgBytesPerSec = objectInfo.nAvgBytesPerSec shr 1
            objectInfo.nSamplesPerSec = objectInfo.nSamplesPerSec shr 1
        }

        /*
         ===================
         idSoundSample::FetchFromCache

         Returns true on success.
         ===================
         */
        fun FetchFromCache(
            offset: Int,
            output: ByteBuffer?,
            position: IntArray?,
            size: IntArray?,
            allowIO: Boolean
        ): Boolean {
            var offset = offset
            offset = offset and -0x2
            if (objectSize == 0 || offset < 0 || offset > objectSize * 2 /*(int) sizeof(short)*/ || TempDump.NOT(
                    nonCacheData
                )
            ) {
                return false
            }
            if (output != null) {
                nonCacheData.mark()
                nonCacheData.position(offset)
                output.put(nonCacheData)
                nonCacheData.reset()
            }
            if (position != null) {
                position[0] = 0
            }
            if (size != null) {
                size[0] = objectSize * 2 /*sizeof(short)*/ - offset
                if (size[0] > SCACHE_SIZE) {
                    size[0] = SCACHE_SIZE
                }
            }
            return true
        }

        //
        //
        init {
//	memset( &objectInfo, 0, sizeof(waveformatex_t) );
            objectInfo = waveformatex_s()
            objectSize = 0
            objectMemSize = 0
            nonCacheData = ByteBuffer.allocate(0)
            amplitudeData = ByteBuffer.allocate(0)
            openalBuffer = 0
            hardwareBuffer = false
            defaultSound = false
            onDemand = false
            purged = false
            levelLoadReferenced = false
        }
    }

    /*
     ===================================================================================

     The actual sound cache.

     ===================================================================================
     */
    class idSoundCache {
        private var insideLevelLoad: Boolean
        private val listCache: idList<idSoundSample>

        // ~idSoundCache();
        /*
         ===================
         idSoundCache::FindSound

         Adds a sound object to the cache and returns a handle for it.
         ===================
         */
        fun FindSound(filename: idStr, loadOnDemandOnly: Boolean): idSoundSample {
            val fname: idStr
            fname = idStr(filename)
            fname.BackSlashesToSlashes()
            fname.ToLower()
            DeclManager.declManager.MediaPrint("%s\n", fname)

            // check to see if object is already in cache
            for (i in 0 until listCache.Num()) {
                val def = listCache[i]
                if (def != null && def.name == fname) {
                    def.levelLoadReferenced = true
                    if (def.purged && !loadOnDemandOnly) {
                        def.Load()
                    }
                    return def
                }
            }

            // create a new entry
            val def = idSoundSample()
            var shandle = listCache.FindNull()
            if (shandle != -1) {
                listCache[shandle] = def
            } else {
                shandle = listCache.Append(def)
            }
            def.name.set(fname)
            def.levelLoadReferenced = true
            def.onDemand = loadOnDemandOnly
            def.purged = true
            if (!loadOnDemandOnly) {
                // this may make it a default sound if it can't be loaded
                def.Load()
            }
            return def
        }

        fun GetNumObjects(): Int {
            return listCache.Num()
        }

        /*
         ===================
         idSoundCache::::GetObject

         returns a single cached object pointer
         ===================
         */
        fun GetObject(index: Int): idSoundSample? {
            return if (index < 0 || index > listCache.Num()) {
                null
            } else listCache[index]
        }

        /*
         ===================
         idSoundCache::ReloadSounds

         Completely nukes the current cache
         ===================
         */
        fun ReloadSounds(force: Boolean) {
            var i: Int
            i = 0
            while (i < listCache.Num()) {
                val def = listCache[i]
                def.Reload(force)
                i++
            }
        }

        /*
         ====================
         BeginLevelLoad

         Mark all file based images as currently unused,
         but don't free anything.  Calls to ImageFromFile() will
         either mark the image as used, or create a new image without
         loading the actual data.
         ====================
         */
        fun BeginLevelLoad() {
            insideLevelLoad = true
            for (i in 0 until listCache.Num()) {
                val sample = listCache[i]
                if (Common.com_purgeAll.GetBool()) {
                    sample.PurgeSoundSample()
                }
                sample.levelLoadReferenced = false
            }

//            soundCacheAllocator.FreeEmptyBaseBlocks();
        }

        /*
         ====================
         EndLevelLoad

         Free all samples marked as unused
         ====================
         */
        fun EndLevelLoad() {
            var useCount: Int
            var purgeCount: Int
            Common.common.Printf("----- idSoundCache::EndLevelLoad -----\n")
            insideLevelLoad = false

            // purge the ones we don't need
            useCount = 0
            purgeCount = 0
            for (i in 0 until listCache.Num()) {
                val sample = listCache[i]
                if (sample.purged) {
                    continue
                }
                if (!sample.levelLoadReferenced) {
//			common.Printf( "Purging %s\n", sample.name.c_str() );
                    purgeCount += sample.objectMemSize
                    sample.PurgeSoundSample()
                } else {
                    useCount += sample.objectMemSize
                }
            }

//            soundCacheAllocator.FreeEmptyBaseBlocks();
            Common.common.Printf("%5dk referenced\n", useCount / 1024)
            Common.common.Printf("%5dk purged\n", purgeCount / 1024)
            Common.common.Printf("----------------------------------------\n")
        }

        fun PrintMemInfo(mi: MemInfo_t) {
            var i: Int
            var j: Int
            var num = 0
            var total = 0
            val sortIndex: IntArray
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileWrite(mi.filebase.toString() + "_sounds.txt")
            if (null == f) {
                return
            }

            // count
            i = 0
            while (i < listCache.Num()) {
                if (null == listCache[i]) {
                    break
                }
                i++
                num++
            }

            // sort first
            sortIndex = IntArray(num)
            i = 0
            while (i < num) {
                sortIndex[i] = i
                i++
            }
            i = 0
            while (i < num - 1) {
                j = i + 1
                while (j < num) {
                    if (listCache[sortIndex[i]].objectMemSize < listCache[sortIndex[j]].objectMemSize) {
                        val temp = sortIndex[i]
                        sortIndex[i] = sortIndex[j]
                        sortIndex[j] = temp
                    }
                    j++
                }
                i++
            }

            // print next
            i = 0
            while (i < num) {
                val sample = listCache[sortIndex[i]]

                // this is strange
                if (null == sample) {
                    i++
                    continue
                }
                total += sample.objectMemSize
                f.Printf(
                    "%s %s\n",
                    idStr.FormatNumber(sample.objectMemSize).toString(),
                    sample.name.toString()
                )
                i++
            }
            mi.soundAssetsTotal = total
            f.Printf("\nTotal sound bytes allocated: %s\n", idStr.FormatNumber(total).toString())
            FileSystem_h.fileSystem.CloseFile(f)
        }

        //
        //
        init {
            listCache = idList()
            //            soundCacheAllocator.Init();
//            soundCacheAllocator.SetLockMemory(true);
            listCache.AssureSize(1024, idSoundSample())
            listCache.SetGranularity(256)
            insideLevelLoad = false
        }
    }
}