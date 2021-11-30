package neo.Sound

import neo.Renderer.RenderWorld.idRenderWorld
import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_local.soundDemoCommand_t
import neo.Sound.snd_shader.idSoundShader
import neo.Sound.snd_system.idSoundSystemLocal
import neo.Sound.snd_world.idSoundWorldLocal
import neo.Sound.sound.idSoundEmitter
import neo.TempDump
import neo.framework.*
import neo.framework.DemoFile.demoSystem_t
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.sys.win_shared
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import java.nio.*

/**
 *
 */
object snd_emitter {
    //    typedef enum {
    const val PLAYBACK_ADVANCING = 1

    //enum {
    const val PLAYBACK_RESET = 0
    const val REMOVE_STATUS_ALIVE = 0
    const val REMOVE_STATUS_INVALID = -1

    //} removeStatus_t;
    const val REMOVE_STATUS_SAMPLEFINISHED = 2
    const val REMOVE_STATUS_WAITSAMPLEFINISHED = 1

    /*
     ===============================================================================

     SOUND EMITTER

     ===============================================================================
     */
    // sound channels
    const val SCHANNEL_ANY = 0 // used in queries and commands to effect every channel at once, in

    // startSound to have it not override any other channel
    const val SCHANNEL_ONE = 1 // any following integer can be used as a channel number

    internal class idSoundFade {
        var fadeEnd44kHz = 0
        var fadeEndVolume // in dB
                = 0f
        var fadeStart44kHz = 0
        var fadeStartVolume // in dB
                = 0f

        //
        //
        fun Clear() {
            fadeStart44kHz = 0
            fadeEnd44kHz = 0
            fadeStartVolume = 0f
            fadeEndVolume = 0f
        }

        fun FadeDbAt44kHz(current44kHz: Int): Float {
            val fadeDb: Float
            fadeDb = if (current44kHz >= fadeEnd44kHz) {
                fadeEndVolume
            } else if (current44kHz > fadeStart44kHz) {
                val fraction = (fadeEnd44kHz - fadeStart44kHz).toFloat()
                val over = (current44kHz - fadeStart44kHz).toFloat()
                fadeStartVolume + (fadeEndVolume - fadeStartVolume) * over / fraction
            } else {
                fadeStartVolume
            }
            return fadeDb
        }
    }

    internal open class SoundFX//            memset(continuitySamples, 0, sizeof(float) * 4);     //
    //
    {
        //
        protected var buffer: FloatArray? = null

        //
        protected var channel = 0
        protected var continuitySamples: FloatArray? = FloatArray(4)
        protected var initialized = false
        protected var maxlen = 0

        //
        protected var param = 0f

        //	virtual				~SoundFX()										{ if ( buffer ) delete buffer; };
        open fun Initialize() {}
        open fun ProcessSample(`in`: FloatArray?, in_offset: Int, out: FloatArray?, out_offset: Int) {}
        fun SetChannel(chan: Int) {
            channel = chan
        }

        fun GetChannel(): Int {
            return channel
        }

        fun SetContinuitySamples(in1: Float, in2: Float, out1: Float, out2: Float) {
            continuitySamples.get(0) = in1
            continuitySamples.get(1) = in2
            continuitySamples.get(2) = out1
            continuitySamples.get(3) = out2
        }

        // FIXME?
        fun GetContinuitySamples(in1: FloatArray?, in2: FloatArray?, out1: FloatArray?, out2: FloatArray?) {
            in1.get(0) = continuitySamples.get(0)
            in2.get(0) = continuitySamples.get(1)
            out1.get(0) = continuitySamples.get(2)
            out2.get(0) = continuitySamples.get(3)
        }

        fun SetParameter(`val`: Float) {
            param = `val`
        }
    }

    internal class SoundFX_Lowpass : SoundFX() {
        override fun ProcessSample(`in`: FloatArray?, in_offset: Int, out: FloatArray?, out_offset: Int) {
            val c: Float
            val a1: Float
            val a2: Float
            val a3: Float
            val b1: Float
            val b2: Float
            val resonance: Float = idSoundSystemLocal.Companion.s_enviroSuitCutoffQ.GetFloat()
            val cutoffFrequency: Float = idSoundSystemLocal.Companion.s_enviroSuitCutoffFreq.GetFloat()
            Initialize()
            c = 1.0f / idMath.Tan16(idMath.PI * cutoffFrequency / 44100)

            // compute coefs
            a1 = 1.0f / (1.0f + resonance * c + c * c)
            a2 = 2 * a1
            a3 = a1
            b1 = 2.0f * (1.0f - c * c) * a1
            b2 = (1.0f - resonance * c + c * c) * a1

            // compute output value
            out.get(out_offset + 0) =
                a1 * `in`.get(in_offset + 0) + a2 * `in`.get(in_offset + -1) + a3 * `in`.get(in_offset + -2) - b1 * out.get(
                    out_offset + -1
                ) - b2 * out.get(out_offset + -2)
        }
    }

    internal class SoundFX_LowpassFast : SoundFX() {
        var a1 = 0f
        var a2 = 0f
        var a3 = 0f
        var b1 = 0f
        var b2 = 0f
        var freq = 0f
        var res = 0f

        //
        //
        override fun ProcessSample(`in`: FloatArray?, inOffset: Int, out: FloatArray?, outOffset: Int) {
            // compute output value
            out.get(outOffset + 0) =
                a1 * `in`.get(inOffset + 0) + a2 * `in`.get(inOffset - 1) + a3 * `in`.get(inOffset - 2) - b1 * out.get(
                    outOffset - 1
                ) - b2 * out.get(outOffset - 2)
        }

        @JvmOverloads
        fun SetParms(p1: Float = 0f /*= 0*/, p2: Float = 0f /*= 0*/, p3: Float = 0f /*= 0*/) {
            val c: Float

            // set the vars
            freq = p1
            res = p2

            // precompute the coefs
            c = 1.0f / idMath.Tan(idMath.PI * freq / 44100)

            // compute coefs
            a1 = 1.0f / (1.0f + res * c + c * c)
            a2 = 2 * a1
            a3 = a1
            b1 = 2.0f * (1.0f - c * c) * a1
            b2 = (1.0f - res * c + c * c) * a1
        }
    }

    //};
    internal class SoundFX_Comb : SoundFX() {
        var currentTime = 0

        //
        //
        override fun Initialize() {
            if (initialized) {
                return
            }
            initialized = true
            maxlen = 50000
            buffer = FloatArray(maxlen)
            currentTime = 0
        }

        override fun ProcessSample(`in`: FloatArray?, in_offset: Int, out: FloatArray?, out_offset: Int) {
            val gain: Float = idSoundSystemLocal.Companion.s_reverbFeedback.GetFloat()
            val len: Int = (idSoundSystemLocal.Companion.s_reverbTime.GetFloat() + param).toInt()
            Initialize()

            // sum up and output
            out.get(out_offset + 0) = buffer.get(currentTime)
            buffer.get(currentTime) = buffer.get(currentTime) * gain + `in`.get(in_offset + 0)

            // increment current time
            currentTime++
            if (currentTime >= len) {
                currentTime -= len
            }
        }
    }

    internal class FracTime {
        var frac = 0f
        var time = 0

        //
        //
        fun Set(`val`: Int) {
            time = `val`
            frac = 0f
        }

        fun Increment(`val`: Float) {
            frac += `val`
            while (frac >= 1f) {
                time++
                frac--
            }
        }
    }

    internal class idSlowChannel {
        var active = false
        var chan: idSoundChannel? = null

        //
        var curPosition: FracTime? = FracTime()
        var curSampleOffset = 0

        //
        var lowpass: SoundFX_LowpassFast? = null

        //
        var newPosition: FracTime? = FracTime()
        var newSampleOffset = 0

        //
        var playbackState = 0
        var triggerOffset = 0

        //
        //
        // functions
        fun GenerateSlowChannel(playPos: FracTime?, sampleCount44k: Int, finalBuffer: FloatArray?) {
            val sw = snd_system.soundSystemLocal.GetPlayingSoundWorld() as idSoundWorldLocal
            val `in` = FloatArray(Simd.MIXBUFFER_SAMPLES + 3)
            val out = FloatArray(Simd.MIXBUFFER_SAMPLES + 3)
            val src = FloatArray(Simd.MIXBUFFER_SAMPLES + 3 - 2)
            val spline = FloatArray(Simd.MIXBUFFER_SAMPLES + 3 - 2)
            //            int src, spline;
            val slowmoSpeed: Float
            var i: Int
            val neededSamples: Int
            val orgTime: Int
            val zeroedPos: Int
            var count = 0

//            src = in + 2;
//            spline = out + 2;
            slowmoSpeed = sw?.slowmoSpeed ?: 1f
            neededSamples = (sampleCount44k * slowmoSpeed + 4).toInt()
            orgTime = playPos.time

            // get the channel's samples
            chan.GatherChannelSamples(playPos.time * 2, neededSamples, FloatBuffer.wrap(src))
            i = 0
            while (i < neededSamples shr 1) {
                spline[i] = src[i * 2]
                i++
            }

            // interpolate channel
            zeroedPos = playPos.time
            playPos.time = 0
            i = 0
            while (i < sampleCount44k shr 1) {
                var `val`: Float
                `val` = spline[playPos.time]
                src[i] = `val`
                playPos.Increment(slowmoSpeed)
                i++
                count += 2
            }

            // lowpass filter
//            float *in_p = in + 2, *out_p = out + 2;
            val in_p1 = floatArrayOf(0f)
            val in_p2 = floatArrayOf(0f)
            val out_p1 = floatArrayOf(0f)
            val out_p2 = floatArrayOf(0f)
            val numSamples = sampleCount44k shr 1
            lowpass.GetContinuitySamples(in_p1, in_p2, out_p1, out_p2)
            lowpass.SetParms(slowmoSpeed * 15000, 1.2f, 9f)
            System.arraycopy(src, 0, `in`, 2, Simd.MIXBUFFER_SAMPLES + 3 - 2)
            System.arraycopy(spline, 0, out, 2, Simd.MIXBUFFER_SAMPLES + 3 - 2)
            `in`[0] = in_p1[0] //FIXME:ugly block.
            `in`[1] = in_p2[0]
            out[0] = out_p1[0]
            out[1] = out_p2[0]
            i = 0
            count = 0
            while (i < numSamples) {
                lowpass.ProcessSample(`in`, 2 + i, out, 2 + i)
                finalBuffer.get(count + 1) = out[i]
                finalBuffer.get(count) = finalBuffer.get(count + 1)
                i++
                count += 2
            }
            lowpass.SetContinuitySamples(
                `in`[2 + numSamples - 2],
                `in`[2 + numSamples - 3],
                out[2 + numSamples - 2],
                out[2 + numSamples - 3]
            ) //2 = pointer offset
            playPos.time += zeroedPos
        }

        fun GetSlowmoSpeed(): Float {
            val sw = snd_system.soundSystemLocal.GetPlayingSoundWorld() as idSoundWorldLocal
            return sw?.slowmoSpeed ?: 0
        }

        fun AttachSoundChannel(chan: idSoundChannel?) {
            this.chan = chan
        }

        fun Reset() {
//	memset( this, 0, sizeof( *this ) );//TODO:

//	this.chan = chan;
            curPosition.Set(0)
            newPosition.Set(0)
            curSampleOffset = -10000
            newSampleOffset = -10000
            triggerOffset = 0
        }

        fun GatherChannelSamples(sampleOffset44k: Int, sampleCount44k: Int, dest: FloatArray?) {
            var state = 0

            // setup chan
            active = true
            newSampleOffset = sampleOffset44k shr 1

            // set state
            if (newSampleOffset < curSampleOffset) {
                state = snd_emitter.PLAYBACK_RESET
            } else if (newSampleOffset > curSampleOffset) {
                state = snd_emitter.PLAYBACK_ADVANCING
            }
            if (state == snd_emitter.PLAYBACK_RESET) {
                curPosition.Set(newSampleOffset)
            }

            // set current vars
            curSampleOffset = newSampleOffset
            newPosition = curPosition

            // do the slow processing
            GenerateSlowChannel(newPosition, sampleCount44k, dest)

            // finish off
            if (state == snd_emitter.PLAYBACK_ADVANCING) {
                curPosition = newPosition
            }
        }

        fun IsActive(): Boolean {
            return active
        }

        fun GetCurrentPosition(): FracTime? {
            return curPosition
        }
    }

    internal class idSoundChannel {
        var channelFade: idSoundFade?
        var decoder: idSampleDecoder? = null

        //
        var disallowSlow = false
        var diversity = 0f
        var lastV: FloatArray? = FloatArray(6) // last calculated volume for each speaker, so we can smoothly fade
        var lastVolume // last calculated volume based on distance
                = 0f
        var   /*ALuint*/lastopenalStreamingBuffer: IntBuffer?
        var leadinSample // if not looped, this is the only sample
                : idSoundSample? = null
        var   /*ALuint*/openalSource = 0
        var   /*ALuint*/openalStreamingBuffer: IntBuffer?
        var   /*ALuint*/openalStreamingOffset = 0
        var parms // combines the shader parms and the per-channel overrides
                : snd_shader.soundShaderParms_t? = null
        var soundShader: idSoundShader? = null
        var trigger44kHzTime // hardware time sample the channel started
                = 0
        var   /*s_channelType*/triggerChannel = 0
        var triggerGame44kHzTime // game time sample time the channel started
                = 0
        var triggerState = false
        var triggered = false

        //						~idSoundChannel( void );
        fun Clear() {
            var j: Int
            Stop()
            soundShader = null
            lastVolume = 0.0f
            triggerChannel = snd_emitter.SCHANNEL_ANY
            channelFade.Clear()
            diversity = 0.0f
            leadinSample = null
            trigger44kHzTime = 0
            j = 0
            while (j < 6) {
                lastV.get(j) = 0.0f
                j++
            }
            //	memset( &parms, 0, sizeof(parms) );
            parms = snd_shader.soundShaderParms_t()
            triggered = false
            openalSource = 0 //null;
            openalStreamingOffset = 0
            //            openalStreamingBuffer[0] = openalStreamingBuffer[1] = openalStreamingBuffer[2] = 0;
//            lastopenalStreamingBuffer[0] = lastopenalStreamingBuffer[1] = lastopenalStreamingBuffer[2] = 0;
            openalStreamingBuffer.clear()
            lastopenalStreamingBuffer.clear()
        }

        fun Start() {
            triggerState = true
            if (decoder == null) {
                decoder = idSampleDecoder.Companion.Alloc()
            }
        }

        fun Stop() {
            triggerState = false
            if (decoder != null) {
                idSampleDecoder.Companion.Free(decoder)
                decoder = null
            }
        }

        /*
         ===================
         idSoundChannel::GatherChannelSamples

         Will always return 44kHz samples for the given range, even if it deeply looped or
         out of the range of the unlooped samples.  Handles looping between multiple different
         samples and leadins
         ===================
         */
        fun GatherChannelSamples(sampleOffset44k: Int, sampleCount44k: Int, dest: FloatBuffer?) {
            var sampleOffset44k = sampleOffset44k
            var sampleCount44k = sampleCount44k
            var dest_p = 0
            var len: Int

//Sys_DebugPrintf( "msec:%i sample:%i : %i : %i\n", Sys_Milliseconds(), soundSystemLocal.GetCurrent44kHzTime(), sampleOffset44k, sampleCount44k );	//!@#
            // negative offset times will just zero fill
            if (sampleOffset44k < 0) {
                len = -sampleOffset44k
                if (len > sampleCount44k) {
                    len = sampleCount44k
                }
                //		memset( dest_p, 0, len * sizeof( dest_p[0] ) );
//                dest.clear();
                dest_p += len
                sampleCount44k -= len
                sampleOffset44k += len
            }

            // grab part of the leadin sample
            val leadin = leadinSample
            if (TempDump.NOT(leadin) || sampleOffset44k < 0 || sampleCount44k <= 0) {
//		memset( dest_p, 0, sampleCount44k * sizeof( dest_p[0] ) );
//                dest.clear();
                return
            }
            if (sampleOffset44k < leadin.LengthIn44kHzSamples()) {
                len = leadin.LengthIn44kHzSamples() - sampleOffset44k
                if (len > sampleCount44k) {
                    len = sampleCount44k
                }

                // decode the sample
                decoder.Decode(leadin, sampleOffset44k, len, dest)
                dest.position(len.let { dest_p += it; dest_p })
                sampleCount44k -= len
                sampleOffset44k += len
            }

            // if not looping, zero fill any remaining spots
            if (null == soundShader || 0 == parms.soundShaderFlags and snd_shader.SSF_LOOPING) {
//		memset( dest_p, 0, sampleCount44k * sizeof( dest_p[0] ) );
//                dest.clear();
                return
            }

            // fill the remainder with looped samples
            val loop = soundShader.entries[0]
                ?: //		memset( dest_p, 0, sampleCount44k * sizeof( dest_p[0] ) );
//                dest.clear();
                return
            sampleOffset44k -= leadin.LengthIn44kHzSamples()
            while (sampleCount44k > 0) {
                val totalLen = loop.LengthIn44kHzSamples()
                sampleOffset44k %= totalLen
                len = totalLen - sampleOffset44k
                if (len > sampleCount44k) {
                    len = sampleCount44k
                }

                // decode the sample
                decoder.Decode(loop, sampleOffset44k, len, dest) //TODO:
                dest.position(len.let { dest_p += it; dest_p })
                sampleCount44k -= len
                sampleOffset44k += len
            }
        }

        fun ALStop() {            // free OpenAL resources if any
            if (idSoundSystemLocal.Companion.useOpenAL) {
                if (AL10.alIsSource(openalSource)) {
                    AL10.alSourceStop(openalSource)
                    AL10.alSourcei(openalSource, AL10.AL_BUFFER, 0)
                    snd_system.soundSystemLocal.FreeOpenALSource(openalSource)
                }
                if (openalStreamingBuffer.get(0) != 0 && openalStreamingBuffer.get(1) != 0 && openalStreamingBuffer.get(
                        2
                    ) != 0
                ) {
                    AL10.alGetError()
                    //                    alDeleteBuffers(3, openalStreamingBuffer[0]);
                    AL10.alDeleteBuffers(openalStreamingBuffer)
                    if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                        openalStreamingBuffer.clear()
                    }
                }
                if (lastopenalStreamingBuffer.get(0) != 0 && lastopenalStreamingBuffer.get(1) != 0 && lastopenalStreamingBuffer.get(
                        2
                    ) != 0
                ) {
                    AL10.alGetError()
                    //                    alDeleteBuffers(3, lastopenalStreamingBuffer[0]);
                    AL10.alDeleteBuffers(lastopenalStreamingBuffer)
                    if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                        lastopenalStreamingBuffer.clear()
                    }
                }
            }
        }

        //
        //
        init {
            channelFade = idSoundFade()
            openalStreamingBuffer = BufferUtils.createIntBuffer(3)
            lastopenalStreamingBuffer = BufferUtils.createIntBuffer(3)
            Clear()
        }
    }

    // typedef int s_channelType;	  // the game uses its own series of enums, and we don't want to require casts
    internal class idSoundEmitterLocal : idSoundEmitter() {
        //
        // this is just used for feedback to the game or rendering system:
        // flashing lights and screen shakes.  Because the material expression
        // evaluation doesn't do common subexpression removal, we cache the
        // last generated value
        var ampTime = 0
        var amplitude = 0f
        var channels: Array<idSoundChannel?>?
        var distance // in meters, this may be the straight-line distance, or
                = 0f
        var hasShakes = false

        //
        var index // in world emitter list
                = 0
        var lastValidPortalArea // so an emitter that slides out of the world continues playing
                = 0
        var listenerId = 0

        //
        //
        // the following are calculated in UpdateEmitter, and don't need to be archived
        var maxDistance // greatest of all playing channel distances
                = 0f

        //
        val origin: idVec3?
        var parms // default overrides for all channels
                : snd_shader.soundShaderParms_t? = null
        var playing // if false, no channel is active
                = false

        //						    // or a point through a portal chain
        var realDistance // in meters
                = 0f
        var   /*removeStatus_t*/removeStatus = 0
        var slowChannels: Array<idSlowChannel?>?
        var soundWorld // the world that holds this emitter
                : idSoundWorldLocal? = null
        val spatializedOrigin // the virtual sound origin, either the real sound origin,
                : idVec3?

        //----------------------------------------------
        // the "time" parameters should be game time in msec, which is used to make queries
        // return deterministic values regardless of async buffer scheduling
        /*
         =====================
         idSoundEmitterLocal::Free

         They are never truly freed, just marked so they can be reused by the soundWorld
         // a non-immediate free will let all currently playing sounds complete
         =====================
         */
        override fun Free(immediate: Boolean) {
            if (removeStatus != snd_emitter.REMOVE_STATUS_ALIVE) {
                return
            }
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("FreeSound (%d,%d)\n", index, immediate)
            }
            if (soundWorld != null && soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_FREE)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteInt(TempDump.btoi(immediate))
            }
            if (!immediate) {
                removeStatus = snd_emitter.REMOVE_STATUS_WAITSAMPLEFINISHED
            } else {
                Clear()
            }
        }

        // the parms specified will be the default overrides for all sounds started on this emitter.
        // NULL is acceptable for parms
        override fun UpdateEmitter(origin: idVec3?, listenerId: Int, parms: snd_shader.soundShaderParms_t?) {
            if (null == parms) {
                Common.common.Error("idSoundEmitterLocal::UpdateEmitter: NULL parms")
            }
            if (soundWorld != null && soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_UPDATE)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteVec3(origin)
                soundWorld.writeDemo.WriteInt(listenerId)
                soundWorld.writeDemo.WriteFloat(parms.minDistance)
                soundWorld.writeDemo.WriteFloat(parms.maxDistance)
                soundWorld.writeDemo.WriteFloat(parms.volume)
                soundWorld.writeDemo.WriteFloat(parms.shakes)
                soundWorld.writeDemo.WriteInt(parms.soundShaderFlags)
                soundWorld.writeDemo.WriteInt(parms.soundClass)
            }
            this.origin.set(origin)
            this.listenerId = listenerId
            this.parms = parms

            // FIXME: change values on all channels?
        }

        /*
         =====================
         idSoundEmitterLocal::StartSound

         returns the length of the started sound in msec
         =====================
         */
        override fun StartSound(
            shader: idSoundShader?,
            channel: Int,
            diversity: Float /*= 0*/,
            soundShaderFlags: Int /*= 0*/,
            allowSlow: Boolean /*= true*/
        ): Int {
            var i: Int
            if (null == shader) {
                return 0
            }
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf(
                    "StartSound %dms (%d,%d,%s) = ",
                    soundWorld.gameMsec,
                    index,
                    channel,
                    shader.GetName()
                )
            }
            if (soundWorld != null && soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_START)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteHashString(shader.GetName())
                soundWorld.writeDemo.WriteInt(channel)
                soundWorld.writeDemo.WriteFloat(diversity)
                soundWorld.writeDemo.WriteInt(soundShaderFlags)
            }

            // build the channel parameters by taking the shader parms and optionally overriding
            val chanParms = arrayOf<snd_shader.soundShaderParms_t?>(null)
            chanParms[0] = shader.parms
            OverrideParms(chanParms[0], parms, chanParms)
            chanParms[0].soundShaderFlags = chanParms[0].soundShaderFlags or soundShaderFlags
            if (chanParms[0].shakes > 0.0f) {
                shader.CheckShakesAndOgg()
            }

            // this is the sample time it will be first mixed
            var start44kHz: Int
            start44kHz = if (soundWorld.fpa[0] != null) {
                // if we are recording an AVI demo, don't use hardware time
                soundWorld.lastAVI44kHz + Simd.MIXBUFFER_SAMPLES
            } else {
                snd_system.soundSystemLocal.GetCurrent44kHzTime() + Simd.MIXBUFFER_SAMPLES
            }

            //
            // pick which sound to play from the shader
            //
            if (0 == shader.numEntries) {
                if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                    Common.common.Printf("no samples in sound shader\n")
                }
                return 0 // no sounds
            }
            var choice: Int

            // pick a sound from the list based on the passed diversity
            choice = (diversity * shader.numEntries).toInt()
            if (choice < 0 || choice >= shader.numEntries) {
                choice = 0
            }

            // bump the choice if the exact sound was just played and we are NO_DUPS
            if (chanParms[0].soundShaderFlags and snd_shader.SSF_NO_DUPS != 0) {
                val sample: idSoundSample?
                sample = if (shader.leadins[choice] != null) {
                    shader.leadins[choice]
                } else {
                    shader.entries[choice]
                }
                i = 0
                while (i < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = channels.get(i)
                    if (chan.leadinSample === sample) {
                        choice = (choice + 1) % shader.numEntries
                        break
                    }
                    i++
                }
            }

            // PLAY_ONCE sounds will never be restarted while they are running
            if (chanParms[0].soundShaderFlags and snd_shader.SSF_PLAY_ONCE != 0) {
                i = 0
                while (i < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = channels.get(i)
                    if (chan.triggerState && chan.soundShader === shader) {
                        if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                            Common.common.Printf("PLAY_ONCE not restarting\n")
                        }
                        return 0
                    }
                    i++
                }
            }

            // never play the same sound twice with the same starting time, even
            // if they are on different channels
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                val chan = channels.get(i)
                if (chan.triggerState && chan.soundShader === shader && chan.trigger44kHzTime == start44kHz) {
                    if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                        Common.common.Printf("already started this frame\n")
                    }
                    return 0
                }
                i++
            }
            Sys_EnterCriticalSection()

            // kill any sound that is currently playing on this channel
            if (channel != snd_emitter.SCHANNEL_ANY) {
                i = 0
                while (i < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = channels.get(i)
                    if (chan.triggerState && chan.soundShader != null && chan.triggerChannel == channel) {
                        if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                            Common.common.Printf("(override %s)", chan.soundShader.base.GetName())
                        }
                        chan.Stop()

                        // if this was an onDemand sound, purge the sample now
                        if (chan.leadinSample.onDemand) {
                            chan.ALStop()
                            chan.leadinSample.PurgeSoundSample()
                        }
                        break
                    }
                    i++
                }
            }

            // find a free channel to play the sound on
            var chan: idSoundChannel?
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                chan = channels.get(i)
                if (!chan.triggerState) {
                    break
                }
                i++
            }
            if (i == snd_local.SOUND_MAX_CHANNELS) {
                // we couldn't find a channel for it
                Sys_LeaveCriticalSection()
                if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                    Common.common.Printf("no channels available\n")
                }
                return 0
            }
            chan = channels.get(i)
            if (shader.leadins[choice] != null) {
                chan.leadinSample = shader.leadins[choice]
            } else {
                chan.leadinSample = shader.entries[choice]
            }

            // if the sample is onDemand (voice mails, etc), load it now
            if (chan.leadinSample.purged) {
                val start = win_shared.Sys_Milliseconds()
                chan.leadinSample.Load()
                val end = win_shared.Sys_Milliseconds()
                Session.Companion.session.TimeHitch(end - start)
                // recalculate start44kHz, because loading may have taken a fair amount of time
                if (TempDump.NOT(soundWorld.fpa[0])) {
                    start44kHz = snd_system.soundSystemLocal.GetCurrent44kHzTime() + Simd.MIXBUFFER_SAMPLES
                }
            }
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("'%s'\n", chan.leadinSample.name)
            }
            if (idSoundSystemLocal.Companion.s_skipHelltimeFX.GetBool()) {
                chan.disallowSlow = true
            } else {
                chan.disallowSlow = !allowSlow
            }
            ResetSlowChannel(chan)

            // the sound will start mixing in the next async mix block
            chan.triggered = true
            chan.openalStreamingOffset = 0
            chan.trigger44kHzTime = start44kHz
            chan.parms = chanParms[0]
            chan.triggerGame44kHzTime = soundWorld.game44kHz
            chan.soundShader = shader
            chan.triggerChannel = channel
            chan.Start()

            // we need to start updating the def and mixing it in
            playing = true

            // spatialize it immediately, so it will start the next mix block
            // even if that happens before the next PlaceOrigin()
            Spatialize(soundWorld.listenerPos, soundWorld.listenerArea, soundWorld.rw)

            // return length of sound in milliseconds
            var length = chan.leadinSample.LengthIn44kHzSamples()
            if (chan.leadinSample.objectInfo.nChannels == 2) {
                length /= 2 // stereo samples
            }

            // adjust the start time based on diversity for looping sounds, so they don't all start
            // at the same point
            if (chan.parms.soundShaderFlags and snd_shader.SSF_LOOPING != 0 && TempDump.NOT(
                    chan.leadinSample.LengthIn44kHzSamples().toDouble()
                )
            ) {
                chan.trigger44kHzTime -= (diversity * length).toInt()
                chan.trigger44kHzTime =
                    chan.trigger44kHzTime and 7.inv() // so we don't have to worry about the 22kHz and 11kHz expansions
                // starting in fractional samples
                chan.triggerGame44kHzTime -= (diversity * length).toInt()
                chan.triggerGame44kHzTime = chan.triggerGame44kHzTime and 7.inv()
            }
            length *= (1000 / snd_local.PRIMARYFREQ.toFloat()).toInt()
            Sys_LeaveCriticalSection()
            return length
        }

        // pass SCHANNEL_ANY to effect all channels
        override fun ModifySound(channel: Int, parms: snd_shader.soundShaderParms_t?) {
            if (null == parms) {
                Common.common.Error("idSoundEmitterLocal::ModifySound: NULL parms")
            }
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("ModifySound(%d,%d)\n", index, channel)
            }
            if (soundWorld != null && soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_MODIFY)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteInt(channel)
                soundWorld.writeDemo.WriteFloat(parms.minDistance)
                soundWorld.writeDemo.WriteFloat(parms.maxDistance)
                soundWorld.writeDemo.WriteFloat(parms.volume)
                soundWorld.writeDemo.WriteFloat(parms.shakes)
                soundWorld.writeDemo.WriteInt(parms.soundShaderFlags)
                soundWorld.writeDemo.WriteInt(parms.soundClass)
            }
            for (i in 0 until snd_local.SOUND_MAX_CHANNELS) {
                val chan = channels.get(i)
                if (!chan.triggerState) {
                    continue
                }
                if (channel != snd_emitter.SCHANNEL_ANY && chan.triggerChannel != channel) {
                    continue
                }
                val chanParms = arrayOf(chan.parms)
                OverrideParms(chan.parms, parms, chanParms)
                chan.parms = chanParms[0]
                if (chan.parms.shakes > 0.0f && chan.soundShader != null) {
                    chan.soundShader.CheckShakesAndOgg()
                }
            }
        }

        /*
         ===================
         idSoundEmitterLocal::StopSound

         can pass SCHANNEL_ANY
         ===================
         */
        override fun StopSound(channel: Int) {
            var i: Int
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("StopSound(%d,%d)\n", index, channel)
            }
            if (soundWorld != null && soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_STOP)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteInt(channel)
            }
            Sys_EnterCriticalSection()
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                val chan = channels.get(i)
                if (!chan.triggerState) {
                    i++
                    continue
                }
                if (channel != snd_emitter.SCHANNEL_ANY && chan.triggerChannel != channel) {
                    i++
                    continue
                }

                // stop it
                chan.Stop()

                // free hardware resources
                chan.ALStop()

                // if this was an onDemand sound, purge the sample now
                if (chan.leadinSample.onDemand) {
                    chan.leadinSample.PurgeSoundSample()
                }
                chan.leadinSample = null
                chan.soundShader = null
                i++
            }
            Sys_LeaveCriticalSection()
        }

        /*
         ===================
         idSoundEmitterLocal::FadeSound

         to is in Db (sigh), over is in seconds
         ===================
         */
        override fun FadeSound(channel: Int, to: Float, over: Float) {
            if (idSoundSystemLocal.Companion.s_showStartSound.GetInteger() != 0) {
                Common.common.Printf("FadeSound(%d,%d,%f,%f )\n", index, channel, to, over)
            }
            if (TempDump.NOT(soundWorld)) {
                return
            }
            if (soundWorld.writeDemo != null) {
                soundWorld.writeDemo.WriteInt(demoSystem_t.DS_SOUND)
                soundWorld.writeDemo.WriteInt(soundDemoCommand_t.SCMD_FADE)
                soundWorld.writeDemo.WriteInt(index)
                soundWorld.writeDemo.WriteInt(channel)
                soundWorld.writeDemo.WriteFloat(to)
                soundWorld.writeDemo.WriteFloat(over)
            }
            val start44kHz: Int
            start44kHz = if (soundWorld.fpa[0] != null) {
                // if we are recording an AVI demo, don't use hardware time
                soundWorld.lastAVI44kHz + Simd.MIXBUFFER_SAMPLES
            } else {
                snd_system.soundSystemLocal.GetCurrent44kHzTime() + Simd.MIXBUFFER_SAMPLES
            }
            val length44kHz = snd_system.soundSystemLocal.MillisecondsToSamples((over * 1000).toInt())
            for (i in 0 until snd_local.SOUND_MAX_CHANNELS) {
                val chan = channels.get(i)
                if (!chan.triggerState) {
                    continue
                }
                if (channel != snd_emitter.SCHANNEL_ANY && chan.triggerChannel != channel) {
                    continue
                }

                // if it is already fading to this volume at this rate, don't change it
                if (chan.channelFade.fadeEndVolume == to
                    && chan.channelFade.fadeEnd44kHz - chan.channelFade.fadeStart44kHz == length44kHz
                ) {
                    continue
                }

                // fade it
                chan.channelFade.fadeStartVolume = chan.channelFade.FadeDbAt44kHz(start44kHz)
                chan.channelFade.fadeStart44kHz = start44kHz
                chan.channelFade.fadeEnd44kHz = start44kHz + length44kHz
                chan.channelFade.fadeEndVolume = to
            }
        }

        override fun CurrentlyPlaying(): Boolean {
            return playing
        }

        /*
         ===================
         idSoundEmitterLocal::CurrentAmplitude

         this is called from the main thread by the material shader system
         to allow lights and surface flares to vary with the sound amplitude
         ===================
         */
        override fun CurrentAmplitude(): Float {
            if (idSoundSystemLocal.Companion.s_constantAmplitude.GetFloat() >= 0.0f) {
                return idSoundSystemLocal.Companion.s_constantAmplitude.GetFloat()
            }
            if (removeStatus > snd_emitter.REMOVE_STATUS_WAITSAMPLEFINISHED) {
                return 0.0f
            }
            val localTime = snd_system.soundSystemLocal.GetCurrent44kHzTime()

            // see if we can use our cached value
            if (ampTime == localTime) {
                return amplitude
            }

            // calculate a new value
            ampTime = localTime
            amplitude = soundWorld.FindAmplitude(this, localTime, null, snd_emitter.SCHANNEL_ANY, false)
            return amplitude
        }

        // for save games.  Index will always be > 0
        override fun Index(): Int {
            return index
        }

        //----------------------------------------------
        fun Clear() {
            var i: Int
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                channels.get(i).ALStop()
                channels.get(i).Clear()
                i++
            }
            removeStatus = snd_emitter.REMOVE_STATUS_SAMPLEFINISHED
            distance = 0.0f
            lastValidPortalArea = -1
            playing = false
            hasShakes = false
            ampTime = 0 // last time someone queried
            amplitude = 0f
            maxDistance = 10.0f // meters
            spatializedOrigin.Zero()

//	memset( &parms, 0, sizeof( parms ) );
            parms = snd_shader.soundShaderParms_t()
        }

        fun OverrideParms(
            base: snd_shader.soundShaderParms_t?,
            over: snd_shader.soundShaderParms_t?,
            out: Array<snd_shader.soundShaderParms_t?>?
        ) {
            if (null == over) {
                out.get(0) = base
                return
            }
            if (over.minDistance != 0f) {
                out.get(0).minDistance = over.minDistance
            } else {
                out.get(0).minDistance = base.minDistance
            }
            if (over.maxDistance != 0f) {
                out.get(0).maxDistance = over.maxDistance
            } else {
                out.get(0).maxDistance = base.maxDistance
            }
            if (over.shakes != 0f) {
                out.get(0).shakes = over.shakes
            } else {
                out.get(0).shakes = base.shakes
            }
            if (over.volume != 0f) {
                out.get(0).volume = over.volume
            } else {
                out.get(0).volume = base.volume
            }
            if (over.soundClass != 0) {
                out.get(0).soundClass = over.soundClass
            } else {
                out.get(0).soundClass = base.soundClass
            }
            out.get(0).soundShaderFlags = base.soundShaderFlags or over.soundShaderFlags
        }

        /*
         ==================
         idSoundEmitterLocal::CheckForCompletion

         Checks to see if all the channels have completed, clearing the playing flag if necessary.
         Sets the playing and shakes bools.
         ==================
         */
        fun CheckForCompletion(current44kHzTime: Int) {
            var hasActive: Boolean
            var i: Int
            hasActive = false
            hasShakes = false
            if (playing) {
                i = 0
                while (i < snd_local.SOUND_MAX_CHANNELS) {
                    val chan = channels.get(i)
                    if (!chan.triggerState) {
                        i++
                        continue
                    }
                    val shader = chan.soundShader
                    if (null == shader) {
                        i++
                        continue
                    }

                    // see if this channel has completed
                    if (0 == chan.parms.soundShaderFlags and snd_shader.SSF_LOOPING) {
                        var   /*ALint*/state = AL10.AL_PLAYING
                        if (idSoundSystemLocal.Companion.useOpenAL && AL10.alIsSource(chan.openalSource)) {
//                            alGetSourcei(chan.openalSource, AL_SOURCE_STATE, state);
                            state = AL10.alGetSourcei(chan.openalSource, AL10.AL_SOURCE_STATE)
                        }
                        val slow = GetSlowChannel(chan)
                        if (soundWorld.slowmoActive && slow.IsActive()) {
                            if (slow.GetCurrentPosition().time >= chan.leadinSample.LengthIn44kHzSamples() / 2) {
                                chan.Stop()
                                // if this was an onDemand sound, purge the sample now
                                if (chan.leadinSample.onDemand) {
                                    chan.leadinSample.PurgeSoundSample()
                                }
                                i++
                                continue
                            }
                        } else if (chan.trigger44kHzTime + chan.leadinSample.LengthIn44kHzSamples() < current44kHzTime || state == AL10.AL_STOPPED) {
                            chan.Stop()

                            // free hardware resources
                            chan.ALStop()

                            // if this was an onDemand sound, purge the sample now
                            if (chan.leadinSample.onDemand) {
                                chan.leadinSample.PurgeSoundSample()
                            }
                            i++
                            continue
                        }
                    }

                    // free decoder memory if no sound was decoded for a while
                    if (chan.decoder != null && chan.decoder.GetLastDecodeTime() < current44kHzTime - snd_local.SOUND_DECODER_FREE_DELAY) {
                        chan.decoder.ClearDecoder()
                    }
                    hasActive = true
                    if (chan.parms.shakes > 0.0f) {
                        hasShakes = true
                    }
                    i++
                }
            }

            // mark the entire sound emitter as non-playing if there aren't any active channels
            if (!hasActive) {
                playing = false
                if (removeStatus == snd_emitter.REMOVE_STATUS_WAITSAMPLEFINISHED) {
                    // this can now be reused by the next request for a new soundEmitter
                    removeStatus = snd_emitter.REMOVE_STATUS_SAMPLEFINISHED
                }
            }
        }

        /*
         ===================
         idSoundEmitterLocal::Spatialize

         Called once each sound frame by the main thread from idSoundWorldLocal::PlaceOrigin
         ===================
         */
        fun Spatialize(listenerPos: idVec3?, listenerArea: Int, rw: idRenderWorld?) {
            var i: Int
            val hasActive = false

            //
            // work out the maximum distance of all the playing channels
            //
            maxDistance = 0f
            i = 0
            while (i < snd_local.SOUND_MAX_CHANNELS) {
                val chan = channels.get(i)
                if (!chan.triggerState) {
                    i++
                    continue
                }
                if (chan.parms.maxDistance > maxDistance) {
                    maxDistance = chan.parms.maxDistance
                }
                i++
            }

            //
            // work out where the sound comes from
            //
            val realOrigin = idVec3(origin.times(snd_shader.DOOM_TO_METERS))
            val len = idVec3(listenerPos.minus(realOrigin))
            realDistance = len.LengthFast()
            if (realDistance >= maxDistance) {
                // no way to possibly hear it
                distance = realDistance
                return
            }

            //
            // work out virtual origin and distance, which may be from a portal instead of the actual origin
            //
            distance = maxDistance * snd_shader.METERS_TO_DOOM
            if (listenerArea == -1) {        // listener is outside the world
                return
            }
            if (rw != null) {
                // we have a valid renderWorld
                var soundInArea = rw.PointInArea(origin)
                if (soundInArea == -1) {
                    if (lastValidPortalArea == -1) {        // sound is outside the world
                        distance = realDistance
                        spatializedOrigin.set(origin) // sound is in our area
                        return
                    }
                    soundInArea = lastValidPortalArea
                }
                lastValidPortalArea = soundInArea
                if (soundInArea == listenerArea) {
                    distance = realDistance
                    spatializedOrigin.set(origin) // sound is in our area
                    return
                }
                soundWorld.ResolveOrigin(0, null, soundInArea, 0.0f, origin, this)
                distance /= snd_shader.METERS_TO_DOOM
            } else {
                // no portals available
                distance = realDistance
                spatializedOrigin.set(origin) // sound is in our area
            }
        }

        fun GetSlowChannel(chan: idSoundChannel?): idSlowChannel? {
            return slowChannels.get(TempDump.indexOf(chan, channels)) //TODO: pointer subtraction
        }

        fun SetSlowChannel(chan: idSoundChannel?, slow: idSlowChannel?) {
            slowChannels.get(TempDump.indexOf(chan, channels)) = slow
        }

        fun ResetSlowChannel(chan: idSoundChannel?) {
            val index = TempDump.indexOf(chan, channels)
            slowChannels.get(index).Reset()
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        //
        //
        init {
            origin = idVec3()
            spatializedOrigin = idVec3()
            channels = arrayOfNulls<idSoundChannel?>(snd_local.SOUND_MAX_CHANNELS)
            for (c in channels.indices) {
                channels.get(c) = idSoundChannel()
            }
            slowChannels = arrayOfNulls<idSlowChannel?>(snd_local.SOUND_MAX_CHANNELS)
            for (s in slowChannels.indices) {
                slowChannels.get(s) = idSlowChannel()
            }
            Clear()
        }
    }
}