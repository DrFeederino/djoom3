package neo.idlib.math

import neo.Game.Animation.Anim_Blend.idAnimBlend
import neo.Renderer.Model.dominantTri_s
import neo.Renderer.Model.shadowCache_s
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CmdSystem.cmdFunction_t
import neo.idlib.CmdArgs
import neo.idlib.Lib.idLib
import neo.idlib.containers.CFloat
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd_Generic.idSIMD_Generic
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.idlib.math.Vector.idVecX
import neo.sys.sys_public
import java.nio.FloatBuffer
import java.util.*

/**
 *
 */
object Simd {
    //
    //
    const val MIXBUFFER_SAMPLES = 4096
    var baseClocks: Long = 0
    var generic: idSIMDProcessor = idSIMD_Generic() // pointer to generic SIMD implementation
    var SIMDProcessor: idSIMDProcessor = generic

    //
    var processor: idSIMDProcessor? = null // pointer to SIMD processor

    enum class speakerLabel {
        SPEAKER_LEFT, SPEAKER_RIGHT, SPEAKER_CENTER, SPEAKER_LFE, SPEAKER_BACKLEFT, SPEAKER_BACKRIGHT
    }

    /*
     ===============================================================================

     Single Instruction Multiple Data (SIMD)

     For optimal use data should be aligned on a 16 byte boundary.
     All idSIMDProcessor routines are thread safe.

     ===============================================================================
     */
    object idSIMD {
        fun Init() {
            generic = idSIMD_Generic()
            generic.cpuid = sys_public.CPUID_GENERIC
            processor = null
            SIMDProcessor = generic
        }

        fun InitProcessor(module: String, forceGeneric: Boolean) {
            /*cpuid_t*/
            val cpuid: Int = idLib.sys!!.GetProcessorId()
            val newProcessor: idSIMDProcessor = generic //TODO:add useSSE to startup sequence.
            //            if (forceGeneric) {
//
//                newProcessor = generic;
//
//            } else {
//
//                if (processor != null) {
//                    if ((cpuid & CPUID_ALTIVEC) != 0) {
//                        processor = new idSIMD_AltiVec();
//                    } else if (((cpuid & CPUID_MMX) & (cpuid & CPUID_SSE) & (cpuid & CPUID_SSE2) & (cpuid & CPUID_SSE3))
//                            != 0) {
//                        processor = new idSIMD_SSE3();
//                    } else if (((cpuid & CPUID_MMX) & (cpuid & CPUID_SSE) & (cpuid & CPUID_SSE2)) != 0) {
//                        processor = new idSIMD_SSE2();
//                    } else if (((cpuid & CPUID_MMX) & (cpuid & CPUID_SSE)) != 0) {
//                        processor = new idSIMD_SSE();
//                    } else if (((cpuid & CPUID_MMX) & (cpuid & CPUID_3DNOW)) != 0) {
//                        processor = new idSIMD_3DNow();
//                    } else
//                    if ((cpuid & CPUID_MMX) != 0) {
//                        processor = new idSIMD_MMX();
//                    } else {
//                        processor = generic;
//                    }
//                    processor.cpuid = cpuid;
//                }
//                newProcessor = processor;
//            }
            if (newProcessor != SIMDProcessor) {
                SIMDProcessor = newProcessor
                idLib.common.Printf("%s using %s for SIMD processing\n", module, SIMDProcessor.GetName())
            }
            //            if ((cpuid & CPUID_FTZ) != 0) {
//                idLib.sys.FPU_SetFTZ(true);
//                idLib.common.Printf("enabled Flush-To-Zero mode\n");
//            }
//
//            if ((cpuid & CPUID_DAZ) != 0) {
//                idLib.sys.FPU_SetDAZ(true);
//                idLib.common.Printf("enabled Denormals-Are-Zero mode\n");
//            }
        }

        fun Shutdown() {
            if (processor !== generic) {
//		delete processor;
            }
            //	delete generic;
            //SIMDProcessor = null
            processor = SIMDProcessor
            generic = processor as idSIMDProcessor
        }

        @Deprecated(
            """we don't really have simd like this in java, so why
          pretend"""
        )
        class Test_f : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
//
//                if (_WIN32) {
////                    SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_TIME_CRITICAL);
//                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
//                } /* _WIN32 */
//
//                p_simd = processor;
//                p_generic = generic;
//
//                if (isNotNullOrEmpty(args.Argv(1))) {
//                    int cpuid_t = idLib.sys.GetProcessorId();
//                    idStr argString = new idStr(args.Args());
//
//                    argString.Replace(" ", "");
//
//                    if (idStr.Icmp(argString, "MMX") == 0) {
//                        if (0 == (cpuid_t & CPUID_MMX)) {
//                            common.Printf("CPU does not support MMX\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_MMX();
//                    } else if (idStr.Icmp(argString, "3DNow") == 0) {
//                        if (0 == (cpuid_t & CPUID_MMX) || 0 == (cpuid_t & CPUID_3DNOW)) {
//                            common.Printf("CPU does not support MMX & 3DNow\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_3DNow();
//                    } else if (idStr.Icmp(argString, "SSE") == 0) {
//                        if (0 == (cpuid_t & CPUID_MMX) || 0 == (cpuid_t & CPUID_SSE)) {
//                            common.Printf("CPU does not support MMX & SSE\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_SSE();
//                    } else if (idStr.Icmp(argString, "SSE2") == 0) {
//                        if (0 == (cpuid_t & CPUID_MMX) || 0 == (cpuid_t & CPUID_SSE) || 0 == (cpuid_t & CPUID_SSE2)) {
//                            common.Printf("CPU does not support MMX & SSE & SSE2\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_SSE2();
//                    } else if (idStr.Icmp(argString, "SSE3") == 0) {
//                        if (0 == (cpuid_t & CPUID_MMX) || 0 == (cpuid_t & CPUID_SSE) || 0 == (cpuid_t & CPUID_SSE2) || 0 == (cpuid_t & CPUID_SSE3)) {
//                            common.Printf("CPU does not support MMX & SSE & SSE2 & SSE3\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_SSE3();
//                    } else if (idStr.Icmp(argString, "AltiVec") == 0) {
//                        if (0 == (cpuid_t & CPUID_ALTIVEC)) {
//                            common.Printf("CPU does not support AltiVec\n");
//                            return;
//                        }
//                        p_simd = new idSIMD_AltiVec();
//                    } else {
//                        common.Printf("invalid argument, use: MMX, 3DNow, SSE, SSE2, SSE3, AltiVec\n");
//                        return;
//                    }
//                }
//
//                idLib.common.SetRefreshOnPrint(true);
//
//                idLib.common.Printf("using %s for SIMD processing\n", p_simd.GetName());
//
//                GetBaseClocks();
//
//                TestMath();
//                TestAdd();
//                TestSub();
//                TestMul();
//                TestDiv();
//                TestMulAdd();
//                TestMulSub();
//                TestDot();
//                TestCompare();
//                TestMinMax();
//                TestClamp();
//                TestMemcpy();
//                TestMemset();
//                TestNegate();
//
//                TestMatXMultiplyVecX();
//                TestMatXMultiplyAddVecX();
//                TestMatXTransposeMultiplyVecX();
//                TestMatXTransposeMultiplyAddVecX();
//                TestMatXMultiplyMatX();
//                TestMatXTransposeMultiplyMatX();
//                TestMatXLowerTriangularSolve();
//                TestMatXLowerTriangularSolveTranspose();
//                TestMatXLDLTFactor();
//
//                idLib.common.Printf("====================================\n");
//
//                TestBlendJoints();
//                TestConvertJointQuatsToJointMats();
//                TestConvertJointMatsToJointQuats();
//                TestTransformJoints();
//                TestUntransformJoints();
//                TestTransformVerts();
//                TestTracePointCull();
//                TestDecalPointCull();
//                TestOverlayPointCull();
//                TestDeriveTriPlanes();
//                TestDeriveTangents();
//                TestDeriveUnsmoothedTangents();
//                TestNormalizeTangents();
//                TestGetTextureSpaceLightVectors();
//                TestGetSpecularTextureCoords();
//                TestCreateShadowCache();
//
//                idLib.common.Printf("====================================\n");
//
//                TestSoundUpSampling();
//                TestSoundMixing();
//
//                idLib.common.SetRefreshOnPrint(false);
//
//                if (!p_simd.equals(processor)) {
////                    delete p_simd;
//                }
//                p_simd = null;
//                p_generic = null;
//
//                if (_WIN32) {
//                    SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_NORMAL);
//                }/* _WIN32 */
            }

            companion object {
                private val instance: cmdFunction_t = Test_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }
    }

    abstract class idSIMDProcessor {
        //
        /*cpuid_t*/  var cpuid: Int = sys_public.CPUID_NONE

        //
        abstract fun  /*char *VPCALL*/GetName(): String

        //
        abstract fun  /*VPCALL*/Add(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Add(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Sub(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Sub(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Mul(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Mul(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Div(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Div(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MulAdd(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MulAdd(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MulSub(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MulSub(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)

        //
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idVec3, src: Array<idVec3>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idVec3, src: Array<idPlane>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idVec3, src: Array<idDrawVert>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idPlane, src: Array<idVec3>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idPlane, src: Array<idPlane>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, constant: idPlane, src: Array<idDrawVert>, count: Int)
        abstract fun  /*VPCALL*/Dot(dst: FloatArray, src0: Array<idVec3>, src1: Array<idVec3>, count: Int)
        abstract fun  /*VPCALL*/Dot(dot: CFloat, src1: FloatArray, src2: FloatArray, count: Int)
        fun Dot(dot: CFloat, src1: FloatBuffer, src2: FloatArray, count: Int) {
            Dot(dot, TempDump.fbtofa(src1), src2, count)
        }

        abstract fun  /*VPCALL*/CmpGT( /*byte*/
            dst: BooleanArray, src0: FloatArray, constant: Float, count: Int
        )

        abstract fun  /*VPCALL*/CmpGT(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
        abstract fun  /*VPCALL*/CmpGE( /*byte*/
            dst: BooleanArray, src0: FloatArray, constant: Float, count: Int
        )

        abstract fun  /*VPCALL*/CmpGE(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
        abstract fun  /*VPCALL*/CmpLT( /*byte*/
            dst: BooleanArray, src0: FloatArray, constant: Float, count: Int
        )

        abstract fun  /*VPCALL*/CmpLT(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
        abstract fun  /*VPCALL*/CmpLE( /*byte*/
            dst: BooleanArray, src0: FloatArray, constant: Float, count: Int
        )

        abstract fun  /*VPCALL*/CmpLE(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)

        //
        abstract fun  /*VPCALL*/MinMax(min: CFloat, max: CFloat, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MinMax(min: idVec2, max: idVec2, src: Array<idVec2>, count: Int)
        abstract fun  /*VPCALL*/MinMax(min: idVec3, max: idVec3, src: Array<idVec3>, count: Int)
        abstract fun  /*VPCALL*/MinMax(min: idVec3, max: idVec3, src: Array<idDrawVert>, count: Int)
        abstract fun  /*VPCALL*/MinMax(
            min: idVec3,
            max: idVec3,
            src: Array<idDrawVert>,
            indexes: IntArray,
            count: Int
        )

        //
        abstract fun  /*VPCALL*/Clamp(dst: FloatArray, src: FloatArray, min: Float, max: Float, count: Int)
        abstract fun  /*VPCALL*/ClampMin(dst: FloatArray, src: FloatArray, min: Float, count: Int)
        abstract fun  /*VPCALL*/ClampMax(dst: FloatArray, src: FloatArray, max: Float, count: Int)

        @Deprecated("")
        abstract fun  /*VPCALL*/Memcpy(dst: Array<Any>, src: Array<Any>, count: Int)
        fun  /*VPCALL*/Memcpy(dst: Array<idAnimBlend>, src: Array<idAnimBlend>, count: Int) {
            //System.arraycopy(src, 0, dst, 0, count);
            for (i in 0 until count) {
                dst[i] = idAnimBlend(src[i])
            }
        }

        fun  /*VPCALL*/Memcpy(dst: Array<idDrawVert>, src: Array<idDrawVert>, count: Int) {
            System.arraycopy(src, 0, dst, 0, count)
            //            for (int i = 0; i < count; i++) {
//                dst[i].oSet(src[i]); // it's not overloaded
//            }
        }

        fun  /*VPCALL*/Memcpy(dst: Array<idJointQuat>, src: Array<idJointQuat>, count: Int) {
            System.arraycopy(src, 0, dst, 0, count)
        }

        fun  /*VPCALL*/Memcpy(dst: Array<shadowCache_s>, src: Array<idVec4>, count: Int) {
            for (i in 0 until count) {
                dst[i].xyz = src[i]
            }
        }

        fun  /*VPCALL*/Memcpy(dst: Array<shadowCache_s>, src: Array<shadowCache_s>, count: Int) {
            for (i in 0 until count) {
                dst[i].xyz = src[i].xyz
            }
        }

        fun  /*VPCALL*/Memcpy(dst: Any, src: Any, count: Int) {
            throw TODO_Exception()
        }

        @Deprecated("")
        abstract fun  /*VPCALL*/Memset(dst: Array<Any>, `val`: Int, count: Int)

        //
        //	// these assume 16 byte aligned and 16 byte padded memory
        abstract fun  /*VPCALL*/Zero16(dst: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Negate16(dst: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Copy16(dst: FloatArray, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Add16(dst: FloatArray, src1: FloatArray, src2: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Sub16(dst: FloatArray, src1: FloatArray, src2: FloatArray, count: Int)
        abstract fun  /*VPCALL*/Mul16(dst: FloatArray, src1: FloatArray, constant: Float, count: Int)
        abstract fun  /*VPCALL*/AddAssign16(dst: FloatArray, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/SubAssign16(dst: FloatArray, src: FloatArray, count: Int)
        abstract fun  /*VPCALL*/MulAssign16(dst: FloatArray, constant: Float, count: Int)

        //
        //	// idMatX operations
        abstract fun  /*VPCALL*/MatX_MultiplyVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_MultiplyAddVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_MultiplySubVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_TransposeMultiplyVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_TransposeMultiplyAddVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_TransposeMultiplySubVecX(dst: idVecX, mat: idMatX, vec: idVecX)
        abstract fun  /*VPCALL*/MatX_MultiplyMatX(dst: idMatX, m1: idMatX, m2: idMatX)
        abstract fun  /*VPCALL*/MatX_TransposeMultiplyMatX(dst: idMatX, m1: idMatX, m2: idMatX)
        abstract fun  /*VPCALL*/MatX_LowerTriangularSolve(
            L: idMatX,
            x: FloatArray,
            b: FloatArray,
            n: Int /*, int skip = 0*/
        )

        fun MatX_LowerTriangularSolve(L: idMatX, x: FloatArray, b: FloatBuffer, n: Int /*, int skip = 0*/) {
            MatX_LowerTriangularSolve(L, x, TempDump.fbtofa(b), n)
        }

        abstract fun  /*VPCALL*/MatX_LowerTriangularSolve(L: idMatX, x: FloatArray, b: FloatArray, n: Int, skip: Int)
        abstract fun  /*VPCALL*/MatX_LowerTriangularSolveTranspose(L: idMatX, x: FloatArray, b: FloatArray, n: Int)
        abstract fun  /*VPCALL*/MatX_LDLTFactor(mat: idMatX, invDiag: idVecX, n: Int): Boolean

        //
        //	// rendering
        abstract fun  /*VPCALL*/BlendJoints(
            joints: Array<idJointQuat>,
            blendJoints: Array<idJointQuat>,
            lerp: Float,
            index: IntArray,
            numJoints: Int
        )

        abstract fun  /*VPCALL*/BlendJoints(
            joints: Array<idJointQuat>,
            blendJoints: ArrayList<idJointQuat>,
            lerp: Float,
            index: IntArray,
            numJoints: Int
        )

        abstract fun  /*VPCALL*/ConvertJointQuatsToJointMats(
            jointMats: Array<idJointMat>,
            jointQuats: Array<idJointQuat>,
            numJoints: Int
        )

        abstract fun  /*VPCALL*/ConvertJointMatsToJointQuats(
            jointQuats: ArrayList<idJointQuat>,
            jointMats: Array<idJointMat>,
            numJoints: Int
        )

        abstract fun  /*VPCALL*/TransformJoints(
            jointMats: Array<idJointMat>,
            parents: IntArray,
            firstJoint: Int,
            lastJoint: Int
        )

        abstract fun  /*VPCALL*/UntransformJoints(
            jointMats: Array<idJointMat>,
            parents: IntArray,
            firstJoint: Int,
            lastJoint: Int
        )

        abstract fun  /*VPCALL*/TransformVerts(
            verts: Array<idDrawVert?>,
            numVerts: Int,
            joints: Array<idJointMat>,
            weights: Array<idVec4>,
            index: IntArray,
            numWeights: Int
        )

        abstract fun  /*VPCALL*/TracePointCull(
            cullBits: ByteArray,
            totalOr: ByteArray,
            radius: Float,
            planes: Array<idPlane>,
            verts: Array<idDrawVert>,
            numVerts: Int
        )

        abstract fun  /*VPCALL*/DecalPointCull(
            cullBits: ByteArray,
            planes: Array<idPlane>,
            verts: Array<idDrawVert>,
            numVerts: Int
        )

        abstract fun  /*VPCALL*/OverlayPointCull(
            cullBits: ByteArray,
            texCoords: Array<idVec2>,
            planes: Array<idPlane>,
            verts: Array<idDrawVert>,
            numVerts: Int
        )

        abstract fun  /*VPCALL*/DeriveTriPlanes(
            planes: Array<idPlane>,
            verts: Array<idDrawVert>,
            numVerts: Int,
            indexes: IntArray,
            numIndexes: Int
        )

        abstract fun  /*VPCALL*/DeriveTangents(
            planes: Array<idPlane>,
            verts: Array<idDrawVert>,
            numVerts: Int,
            indexes: IntArray,
            numIndexes: Int
        )

        abstract fun  /*VPCALL*/DeriveUnsmoothedTangents(
            verts: Array<idDrawVert>,
            dominantTris: Array<dominantTri_s>,
            numVerts: Int
        )

        abstract fun  /*VPCALL*/NormalizeTangents(verts: Array<idDrawVert>, numVerts: Int)
        abstract fun  /*VPCALL*/CreateTextureSpaceLightVectors(
            lightVectors: Array<idVec3>,
            lightOrigin: idVec3,
            verts: Array<idDrawVert>,
            numVerts: Int,
            indexes: IntArray,
            numIndexes: Int
        )

        fun CreateTextureSpaceLightVectors(
            localLightVector: idVec3,
            localLightOrigin: idVec3,
            verts: Array<idDrawVert>,
            numVerts: Int,
            indexes: IntArray,
            numIndexes: Int
        ) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        abstract fun  /*VPCALL*/CreateSpecularTextureCoords(
            texCoords: Array<idVec4>,
            lightOrigin: idVec3,
            viewOrigin: idVec3,
            verts: Array<idDrawVert>,
            numVerts: Int,
            indexes: IntArray,
            numIndexes: Int
        )

        abstract fun  /*VPCALL*/CreateShadowCache(
            vertexCache: Array<idVec4>,
            vertRemap: IntArray,
            lightOrigin: idVec3,
            verts: Array<idDrawVert>,
            numVerts: Int
        ): Int

        abstract fun  /*VPCALL*/CreateVertexProgramShadowCache(
            vertexCache: Array<idVec4>,
            verts: Array<idDrawVert>,
            numVerts: Int
        ): Int

        fun CreateVertexProgramShadowCache(vertexCache: idVec4, verts: Array<idDrawVert>, numVerts: Int) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        //
        //	// sound mixing
        abstract fun  /*VPCALL*/UpSamplePCMTo44kHz(
            dest: FloatArray,
            pcm: ShortArray,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        )

        fun  /*VPCALL*/UpSampleOGGTo44kHz(
            dest: FloatArray,
            ogg: Array<FloatArray>,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        ) {
            this.UpSampleOGGTo44kHz(dest, 0, ogg, numSamples, kHz, numChannels)
        }

        abstract fun  /*VPCALL*/UpSampleOGGTo44kHz(
            dest: FloatArray,
            offset: Int,
            ogg: Array<FloatArray>,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        )

        abstract fun  /*VPCALL*/UpSampleOGGTo44kHz(
            dest: FloatBuffer,
            offset: Int,
            ogg: Array<FloatArray>,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        )

        abstract fun  /*VPCALL*/MixSoundTwoSpeakerMono(
            mixBuffer: FloatArray,
            samples: FloatArray,
            numSamples: Int,
            lastV: FloatArray,
            currentV: FloatArray
        )

        abstract fun  /*VPCALL*/MixSoundTwoSpeakerStereo(
            mixBuffer: FloatArray,
            samples: FloatArray,
            numSamples: Int,
            lastV: FloatArray,
            currentV: FloatArray
        )

        abstract fun  /*VPCALL*/MixSoundSixSpeakerMono(
            mixBuffer: FloatArray,
            samples: FloatArray,
            numSamples: Int,
            lastV: FloatArray,
            currentV: FloatArray
        )

        abstract fun  /*VPCALL*/MixSoundSixSpeakerStereo(
            mixBuffer: FloatArray,
            samples: FloatArray,
            numSamples: Int,
            lastV: FloatArray,
            currentV: FloatArray
        )

        abstract fun  /*VPCALL*/MixedSoundToSamples(
            samples: IntArray,
            offset: Int,
            mixBuffer: FloatArray,
            numSamples: Int
        )

        fun  /*VPCALL*/MixedSoundToSamples(samples: IntArray, mixBuffer: FloatArray, numSamples: Int) {
            MixedSoundToSamples(samples, 0, mixBuffer, numSamples)
        }

        fun Memset(cullBits: ByteArray, i: Int, numVerts: Int) {
            Arrays.fill(cullBits, 0, numVerts, i.toByte())
        }

        fun Memset(cullBits: IntArray, i: Int, numVerts: Int) {
            Arrays.fill(cullBits, 0, numVerts, i)
        }

        /*
        ============
        idSIMD_Generic::CmpGE

          dst[i] = src0[i] >= constant;
        ============
        */
        fun CmpGE(facing: ByteArray, planeSide: FloatArray, f: Float, numFaces: Int) {
            val nm = numFaces and -0x4
            var i: Int = 0
            while (i < nm) {
                facing[i + 0] = TempDump.btoi(planeSide[i + 0] >= f).toByte()
                facing[i + 1] = TempDump.btoi(planeSide[i + 1] >= f).toByte()
                facing[i + 2] = TempDump.btoi(planeSide[i + 2] >= f).toByte()
                facing[i + 3] = TempDump.btoi(planeSide[i + 3] >= f).toByte()
                i += 4
            }
            while (i < numFaces) {
                facing[i + 0] = TempDump.btoi(planeSide[i + 0] >= f).toByte()
                i++
            }
        }

        fun Memcpy(dst: IntArray, src: IntArray, count: Int) {
            Memcpy(dst, 0, src, 0, count)
        }

        fun Memcpy(dst: IntArray, dstOffset: Int, src: IntArray, srcOffset: Int, count: Int) {
            System.arraycopy(src, srcOffset, dst, dstOffset, count)
        }

    } //TODO:add tests
}