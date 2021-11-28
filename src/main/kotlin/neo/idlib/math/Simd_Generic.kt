package neo.idlib.math

import neo.Renderer.Model.dominantTri_s
import neo.TempDump.Deprecation_Exception
import neo.idlib.containers.CFloat
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd.idSIMDProcessor
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.idlib.math.Vector.idVecX
import java.nio.*
import java.util.*

/**
 *
 */
object Simd_Generic {
    //     UNROLL1(Y) { int _IX; for (_IX=0;_IX<count;_IX++) {Y_IX;} }
    //#define UNROLL2(Y) { int _IX, _NM = count&0xfffffffe; for (_IX=0;_IX<_NM;_IX+=2){Y(_IX+0);Y(_IX+1);} if (_IX < count) {Y_IX;}}
    const val DERIVE_UNSMOOTHED_BITANGENT = true

    //    static void UNROLL4(float[] dst, float constant, float[] src, int count) {
    //        int _IX, _NM = count & 0xfffffffc;
    //        for (_IX = 0; _IX < _NM; _IX += 4) {
    //            dst[_IX + 0] = src[_IX + 0] + constant;
    //            dst[_IX + 1] = src[_IX + 1] + constant;
    //            dst[_IX + 2] = src[_IX + 2] + constant;
    //            dst[_IX + 3] = src[_IX + 3] + constant;
    //        }
    //        for (; _IX < count; _IX++) {
    //            dst[_IX] = src[_IX] + constant;
    //        }
    //    }
    //#define UNROLL8(Y) { int _IX, _NM = count&0xfffffff8; for (_IX=0;_IX<_NM;_IX+=8){Y(_IX+0);Y(_IX+1);Y(_IX+2);Y(_IX+3);Y(_IX+4);Y(_IX+5);Y(_IX+6);Y(_IX+7);} _NM = count&0xfffffffe; for(;_IX<_NM;_IX+=2){Y_IX; Y(_IX+1);} if (_IX < count) {Y_IX;} }
    const val MIXBUFFER_SAMPLES = 4096

    /*
     ===============================================================================

     Generic implementation of idSIMDProcessor

     ===============================================================================
     */
    internal class idSIMD_Generic : idSIMDProcessor() {
        private val NSKIP1_0 = 1 shl 3 or (0 and 7)
        private val NSKIP2_0 = 2 shl 3 or (0 and 7)
        private val NSKIP2_1 = 2 shl 3 or (1 and 7)
        private val NSKIP3_0 = 3 shl 3 or (0 and 7)
        private val NSKIP3_1 = 3 shl 3 or (1 and 7)
        private val NSKIP3_2 = 3 shl 3 or (2 and 7)
        private val NSKIP4_0 = 4 shl 3 or (0 and 7)
        private val NSKIP4_1 = 4 shl 3 or (1 and 7)
        private val NSKIP4_2 = 4 shl 3 or (2 and 7)
        private val NSKIP4_3 = 4 shl 3 or (3 and 7)
        private val NSKIP5_0 = 5 shl 3 or (0 and 7)
        private val NSKIP5_1 = 5 shl 3 or (1 and 7)
        private val NSKIP5_2 = 5 shl 3 or (2 and 7)
        private val NSKIP5_3 = 5 shl 3 or (3 and 7)
        private val NSKIP5_4 = 5 shl 3 or (4 and 7)
        private val NSKIP6_0 = 6 shl 3 or (0 and 7)
        private val NSKIP6_1 = 6 shl 3 or (1 and 7)
        private val NSKIP6_2 = 6 shl 3 or (2 and 7)
        private val NSKIP6_3 = 6 shl 3 or (3 and 7)
        private val NSKIP6_4 = 6 shl 3 or (4 and 7)
        private val NSKIP6_5 = 6 shl 3 or (5 and 7)
        private val NSKIP7_0 = 7 shl 3 or (0 and 7)
        private val NSKIP7_1 = 7 shl 3 or (1 and 7)
        private val NSKIP7_2 = 7 shl 3 or (2 and 7)
        private val NSKIP7_3 = 7 shl 3 or (3 and 7)
        private val NSKIP7_4 = 7 shl 3 or (4 and 7)
        private val NSKIP7_5 = 7 shl 3 or (5 and 7)
        private val NSKIP7_6 = 7 shl 3 or (6 and 7)
        override fun GetName(): String? {
            return "generic code"
        }

        /*
         ============
         idSIMD_Generic::Add

         dst[i] = constant + src[i];
         ============
         */
        override fun Add(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
//#define OPER(X) dst[(X)] = src[(X)] + constant;
//	UNROLL4(OPER)
//#undef OPER
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src.get(_IX + 0) + constant
                dst.get(_IX + 1) = src.get(_IX + 1) + constant
                dst.get(_IX + 2) = src.get(_IX + 2) + constant
                dst.get(_IX + 3) = src.get(_IX + 3) + constant
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src.get(_IX) + constant
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Add

         dst[i] = src0[i] + src1[i];
         ============
         */
        override fun Add(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) + src1.get(_IX + 0)
                dst.get(_IX + 1) = src0.get(_IX + 1) + src1.get(_IX + 1)
                dst.get(_IX + 2) = src0.get(_IX + 2) + src1.get(_IX + 2)
                dst.get(_IX + 3) = src0.get(_IX + 3) + src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) + src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Sub

         dst[i] = constant - src[i];
         ============
         */
        override fun Sub(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
            val c = constant.toDouble()
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = (c - src.get(_IX + 0)).toFloat()
                dst.get(_IX + 1) = (c - src.get(_IX + 1)).toFloat()
                dst.get(_IX + 2) = (c - src.get(_IX + 2)).toFloat()
                dst.get(_IX + 3) = (c - src.get(_IX + 3)).toFloat()
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = (c - src.get(_IX)).toFloat()
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Sub

         dst[i] = src0[i] - src1[i];
         ============
         */
        override fun Sub(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) - src1.get(_IX + 0)
                dst.get(_IX + 1) = src0.get(_IX + 1) - src1.get(_IX + 1)
                dst.get(_IX + 2) = src0.get(_IX + 2) - src1.get(_IX + 2)
                dst.get(_IX + 3) = src0.get(_IX + 3) - src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) - src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Mul

         dst[i] = constant * src[i];
         ============
         */
        override fun Mul(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
            val c = constant.toDouble()
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = (c * src.get(_IX + 0)).toFloat()
                dst.get(_IX + 1) = (c * src.get(_IX + 1)).toFloat()
                dst.get(_IX + 2) = (c * src.get(_IX + 2)).toFloat()
                dst.get(_IX + 3) = (c * src.get(_IX + 3)).toFloat()
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = (c * src.get(_IX)).toFloat()
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Mul

         dst[i] = src0[i] * src1[i];
         ============
         */
        override fun Mul(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) * src1.get(_IX + 0)
                dst.get(_IX + 1) = src0.get(_IX + 1) * src1.get(_IX + 1)
                dst.get(_IX + 2) = src0.get(_IX + 2) * src1.get(_IX + 2)
                dst.get(_IX + 3) = src0.get(_IX + 3) * src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) * src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Div

         dst[i] = constant / divisor[i];
         ============
         */
        override fun Div(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
            val c = constant.toDouble()
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = (c / src.get(_IX + 0)).toFloat()
                dst.get(_IX + 1) = (c / src.get(_IX + 1)).toFloat()
                dst.get(_IX + 2) = (c / src.get(_IX + 2)).toFloat()
                dst.get(_IX + 3) = (c / src.get(_IX + 3)).toFloat()
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = (c / src.get(_IX)).toFloat()
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Div

         dst[i] = src0[i] / src1[i];
         ============
         */
        override fun Div(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) / src1.get(_IX + 0)
                dst.get(_IX + 1) = src0.get(_IX + 1) / src1.get(_IX + 1)
                dst.get(_IX + 2) = src0.get(_IX + 2) / src1.get(_IX + 2)
                dst.get(_IX + 3) = src0.get(_IX + 3) / src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) / src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::MulAdd

         dst[i] += constant * src[i];
         ============
         */
        override fun MulAdd(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
            val c = constant.toDouble()
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) += c * src.get(_IX + 0)
                dst.get(_IX + 1) += c * src.get(_IX + 1)
                dst.get(_IX + 2) += c * src.get(_IX + 2)
                dst.get(_IX + 3) += c * src.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) += c * src.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::MulAdd

         dst[i] += src0[i] * src1[i];
         ============
         */
        override fun MulAdd(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) += src0.get(_IX + 0) * src1.get(_IX + 0)
                dst.get(_IX + 1) += src0.get(_IX + 1) * src1.get(_IX + 1)
                dst.get(_IX + 2) += src0.get(_IX + 2) * src1.get(_IX + 2)
                dst.get(_IX + 3) += src0.get(_IX + 3) * src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) += src0.get(_IX) * src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::MulSub

         dst[i] -= constant * src[i];
         ============
         */
        override fun MulSub(dst: FloatArray?, constant: Float, src: FloatArray?, count: Int) {
            val c = constant.toDouble()
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) -= c * src.get(_IX + 0)
                dst.get(_IX + 1) -= c * src.get(_IX + 1)
                dst.get(_IX + 2) -= c * src.get(_IX + 2)
                dst.get(_IX + 3) -= c * src.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) -= c * src.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::MulSub

         dst[i] -= src0[i] * src1[i];
         ============
         */
        override fun MulSub(dst: FloatArray?, src0: FloatArray?, src1: FloatArray?, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) -= src0.get(_IX + 0) * src1.get(_IX + 0)
                dst.get(_IX + 1) -= src0.get(_IX + 1) * src1.get(_IX + 1)
                dst.get(_IX + 2) -= src0.get(_IX + 2) * src1.get(_IX + 2)
                dst.get(_IX + 3) -= src0.get(_IX + 3) * src1.get(_IX + 3)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) -= src0.get(_IX) * src1.get(_IX)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant * src[i];
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idVec3?, src: Array<idVec3?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) = src.get(_IX).oMultiply(constant)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant * src[i].Normal() + src[i][3];
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idVec3?, src: Array<idPlane?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) = constant.oMultiply(src.get(_IX).Normal()) + src.get(_IX)
                    .oGet(3) //NB I'm not saying operator overloading would have prevented this bug, but....!@#$%$@#^&#$^%^#%^&#$*^&
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant * src[i].xyz;
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idVec3?, src: Array<idDrawVert?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX + 0) = src.get(_IX).xyz.oMultiply(constant)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant.Normal() * src[i] + constant[3];
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idPlane?, src: Array<idVec3?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) = constant.Normal().oMultiply(src.get(_IX)) + constant.oGet(3)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant.Normal() * src[i].Normal() + constant[3] * src[i][3];
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idPlane?, src: Array<idPlane?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) =
                    constant.Normal().oMultiply(src.get(_IX).Normal()) + src.get(_IX).oGet(3) * constant.oGet(3)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = constant.Normal() * src[i].xyz + constant[3];
         ============
         */
        override fun Dot(dst: FloatArray?, constant: idPlane?, src: Array<idDrawVert?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) = constant.Normal().oMultiply(src.get(_IX).xyz) + constant.oGet(3)
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dst[i] = src0[i] * src1[i];
         ============
         */
        override fun Dot(dst: FloatArray?, src0: Array<idVec3?>?, src1: Array<idVec3?>?, count: Int) {
            var _IX: Int
            _IX = 0
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX).oMultiply(src1.get(_IX))
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::Dot

         dot = src1[0] * src2[0] + src1[1] * src2[1] + src1[2] * src2[2] + ...
         ============
         */
        override fun Dot(dot: CFloat?, src1: FloatArray?, src2: FloatArray?, count: Int) {
            when (count) {
                0 -> {
                    dot.setVal(0.0f)
                    return
                }
                1 -> {
                    dot.setVal(src1.get(0) * src2.get(0))
                    return
                }
                2 -> {
                    dot.setVal(src1.get(0) * src2.get(0) + src1.get(1) * src2.get(1))
                    return
                }
                3 -> {
                    dot.setVal(src1.get(0) * src2.get(0) + src1.get(1) * src2.get(1) + src1.get(2) * src2.get(2))
                    return
                }
                else -> {
                    var i: Int
                    var s0: Double
                    var s1: Double
                    var s2: Double
                    var s3: Double
                    s0 = (src1.get(0) * src2.get(0)).toDouble()
                    s1 = (src1.get(1) * src2.get(1)).toDouble()
                    s2 = (src1.get(2) * src2.get(2)).toDouble()
                    s3 = (src1.get(3) * src2.get(3)).toDouble()
                    i = 4
                    while (i < count - 7) {
                        s0 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                        s2 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                        s3 += (src1.get(i + 3) * src2.get(i + 3)).toDouble()
                        s0 += (src1.get(i + 4) * src2.get(i + 4)).toDouble()
                        s1 += (src1.get(i + 5) * src2.get(i + 5)).toDouble()
                        s2 += (src1.get(i + 6) * src2.get(i + 6)).toDouble()
                        s3 += (src1.get(i + 7) * src2.get(i + 7)).toDouble()
                        i += 8
                    }
                    when (count - i) {
                        7 -> {
                            s0 += (src1.get(i + 6) * src2.get(i + 6)).toDouble()
                            s1 += (src1.get(i + 5) * src2.get(i + 5)).toDouble()
                            s2 += (src1.get(i + 4) * src2.get(i + 4)).toDouble()
                            s3 += (src1.get(i + 3) * src2.get(i + 3)).toDouble()
                            s0 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        6 -> {
                            s1 += (src1.get(i + 5) * src2.get(i + 5)).toDouble()
                            s2 += (src1.get(i + 4) * src2.get(i + 4)).toDouble()
                            s3 += (src1.get(i + 3) * src2.get(i + 3)).toDouble()
                            s0 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        5 -> {
                            s2 += (src1.get(i + 4) * src2.get(i + 4)).toDouble()
                            s3 += (src1.get(i + 3) * src2.get(i + 3)).toDouble()
                            s0 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        4 -> {
                            s3 += (src1.get(i + 3) * src2.get(i + 3)).toDouble()
                            s0 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        3 -> {
                            s0 += (src1.get(i + 2) * src2.get(i + 2)).toDouble()
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        2 -> {
                            s1 += (src1.get(i + 1) * src2.get(i + 1)).toDouble()
                            s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        }
                        1 -> s2 += (src1.get(i + 0) * src2.get(i + 0)).toDouble()
                        0 -> {}
                    }
                    var sum: Double
                    sum = s3
                    sum += s2
                    sum += s1
                    sum += s0
                    dot.setVal(sum.toFloat())
                }
            }
        }

        /*
         ============
         idSIMD_Generic::CmpGT

         dst[i] = src0[i] > constant;
         ============
         */
        override fun CmpGT(dst: BooleanArray?, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) > constant
                dst.get(_IX + 1) = src0.get(_IX + 1) > constant
                dst.get(_IX + 2) = src0.get(_IX + 2) > constant
                dst.get(_IX + 3) = src0.get(_IX + 3) > constant
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) > constant
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpGT

         dst[i] |= ( src0[i] > constant ) << bitNum;
         ============
         */
        override fun CmpGT(dst: ByteArray?, bitNum: Byte, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            val _bitNum = (1 shl bitNum).toByte() //TODO:check byte signage
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = dst.get(_IX + 0) or if (src0.get(_IX + 0) > constant) _bitNum else 0
                dst.get(_IX + 1) = dst.get(_IX + 1) or if (src0.get(_IX + 1) > constant) _bitNum else 0
                dst.get(_IX + 2) = dst.get(_IX + 2) or if (src0.get(_IX + 2) > constant) _bitNum else 0
                dst.get(_IX + 3) = dst.get(_IX + 3) or if (src0.get(_IX + 3) > constant) _bitNum else 0
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = dst.get(_IX) or if (src0.get(_IX) > constant) _bitNum else 0
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpGE

         dst[i] = src0[i] >= constant;
         ============
         */
        override fun CmpGE(dst: BooleanArray?, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) >= constant
                dst.get(_IX + 1) = src0.get(_IX + 1) >= constant
                dst.get(_IX + 2) = src0.get(_IX + 2) >= constant
                dst.get(_IX + 3) = src0.get(_IX + 3) >= constant
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) >= constant
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpGE

         dst[i] |= ( src0[i] >= constant ) << bitNum;
         ============
         */
        override fun CmpGE(dst: ByteArray?, bitNum: Byte, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            val _bitNum = (1 shl bitNum).toByte() //TODO:check byte signage
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = dst.get(_IX + 0) or if (src0.get(_IX + 0) >= constant) _bitNum else 0
                dst.get(_IX + 1) = dst.get(_IX + 1) or if (src0.get(_IX + 1) >= constant) _bitNum else 0
                dst.get(_IX + 2) = dst.get(_IX + 2) or if (src0.get(_IX + 2) >= constant) _bitNum else 0
                dst.get(_IX + 3) = dst.get(_IX + 3) or if (src0.get(_IX + 3) >= constant) _bitNum else 0
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = dst.get(_IX) or if (src0.get(_IX) >= constant) _bitNum else 0
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpLT

         dst[i] = src0[i] < constant;
         ============
         */
        override fun CmpLT(dst: BooleanArray?, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) < constant
                dst.get(_IX + 1) = src0.get(_IX + 1) < constant
                dst.get(_IX + 2) = src0.get(_IX + 2) < constant
                dst.get(_IX + 3) = src0.get(_IX + 3) < constant
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) < constant
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpLT

         dst[i] |= ( src0[i] < constant ) << bitNum;
         ============
         */
        override fun CmpLT(dst: ByteArray?, bitNum: Byte, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            val _bitNum = (1 shl bitNum).toByte() //TODO:check byte signage
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = dst.get(_IX + 0) or if (src0.get(_IX + 0) < constant) _bitNum else 0
                dst.get(_IX + 1) = dst.get(_IX + 1) or if (src0.get(_IX + 1) < constant) _bitNum else 0
                dst.get(_IX + 2) = dst.get(_IX + 2) or if (src0.get(_IX + 2) < constant) _bitNum else 0
                dst.get(_IX + 3) = dst.get(_IX + 3) or if (src0.get(_IX + 3) < constant) _bitNum else 0
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = dst.get(_IX) or if (src0.get(_IX) < constant) _bitNum else 0
                _IX++
            }
        }

        /*
         ============
         idSIMD_Generic::CmpLE

         dst[i] = src0[i] <= constant;
         ============
         */
        override fun CmpLE(dst: BooleanArray?, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = src0.get(_IX + 0) <= constant
                dst.get(_IX + 1) = src0.get(_IX + 1) <= constant
                dst.get(_IX + 2) = src0.get(_IX + 2) <= constant
                dst.get(_IX + 3) = src0.get(_IX + 3) <= constant
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = src0.get(_IX) <= constant
                _IX++
            }
        }

        override fun CmpLE(dst: ByteArray?, bitNum: Byte, src0: FloatArray?, constant: Float, count: Int) {
            var _IX: Int
            val _NM = count and -0x4
            val _bitNum = (1 shl bitNum).toByte() //TODO:check byte signage
            _IX = 0
            while (_IX < _NM) {
                dst.get(_IX + 0) = (if (src0.get(_IX + 0) <= constant) _bitNum else 0)
                dst.get(_IX + 1) = (if (src0.get(_IX + 1) <= constant) _bitNum else 0)
                dst.get(_IX + 2) = (if (src0.get(_IX + 2) <= constant) _bitNum else 0)
                dst.get(_IX + 3) = (if (src0.get(_IX + 3) <= constant) _bitNum else 0)
                _IX += 4
            }
            while (_IX < count) {
                dst.get(_IX) = (if (src0.get(_IX) <= constant) _bitNum else 0)
                _IX++
            }
        }

        override fun MinMax(min: CFloat?, max: CFloat?, src: FloatArray?, count: Int) {
            min.setVal(idMath.INFINITY)
            max.setVal(-idMath.INFINITY)
            for (_IX in 0 until count) {
                if (src.get(_IX) < min.getVal()) min.setVal(src.get(_IX))
                if (src.get(_IX) > max.getVal()) max.setVal(src.get(_IX))
            }
        }

        override fun MinMax(min: idVec2?, max: idVec2?, src: Array<idVec2?>?, count: Int) {
            min.y = idMath.INFINITY
            min.x = min.y
            max.y = -idMath.INFINITY
            max.x = max.y
            for (_IX in 0 until count) {
                val v = src.get(_IX)
                if (v.x < min.x) min.x = v.x
                if (v.x > max.x) max.x = v.x
                if (v.y < min.y) min.y = v.y
                if (v.y > max.y) max.y = v.y
            }
        }

        override fun MinMax(min: idVec3?, max: idVec3?, src: Array<idVec3?>?, count: Int) {
            min.z = idMath.INFINITY
            min.y = min.z
            min.x = min.y
            max.z = -idMath.INFINITY
            max.y = max.z
            max.x = max.y
            for (_IX in 0 until count) {
                val v = src.get(_IX)
                if (v.x < min.x) min.x = v.x
                if (v.x > max.x) max.x = v.x
                if (v.y < min.y) min.y = v.y
                if (v.y > max.y) max.y = v.y
                if (v.z < min.z) min.z = v.z
                if (v.z > max.z) max.z = v.z
            }
        }

        override fun MinMax(min: idVec3?, max: idVec3?, src: Array<idDrawVert?>?, count: Int) {
            min.z = idMath.INFINITY
            min.y = min.z
            min.x = min.y
            max.z = -idMath.INFINITY
            max.y = max.z
            max.x = max.y
            for (_IX in 0 until count) {
                val v = src.get(_IX).xyz
                if (v.oGet(0) < min.x) min.x = v.oGet(0)
                if (v.oGet(0) > max.x) max.x = v.oGet(0)
                if (v.oGet(1) < min.y) min.y = v.oGet(1)
                if (v.oGet(1) > max.y) max.y = v.oGet(1)
                if (v.oGet(2) < min.z) min.z = v.oGet(2)
                if (v.oGet(2) > max.z) max.z = v.oGet(2)
            }
        }

        override fun MinMax(min: idVec3?, max: idVec3?, src: Array<idDrawVert?>?, indexes: IntArray?, count: Int) {
            min.z = idMath.INFINITY
            min.y = min.z
            min.x = min.y
            max.z = -idMath.INFINITY
            max.y = max.z
            max.x = max.y
            for (_IX in 0 until count) {
                val v = src.get(indexes.get(_IX)).xyz
                if (v.oGet(0) < min.x) min.x = v.oGet(0)
                if (v.oGet(0) > max.x) max.x = v.oGet(0)
                if (v.oGet(1) < min.y) min.y = v.oGet(1)
                if (v.oGet(1) > max.y) max.y = v.oGet(1)
                if (v.oGet(2) < min.z) min.z = v.oGet(2)
                if (v.oGet(2) > max.z) max.z = v.oGet(2)
            }
        }

        override fun Clamp(dst: FloatArray?, src: FloatArray?, min: Float, max: Float, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = if (src.get(_IX) < min) min else if (src.get(_IX) > max) max else src.get(_IX)
            }
        }

        override fun ClampMin(dst: FloatArray?, src: FloatArray?, min: Float, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = if (src.get(_IX) < min) min else src.get(_IX)
            }
        }

        override fun ClampMax(dst: FloatArray?, src: FloatArray?, max: Float, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = if (src.get(_IX) > max) max else src.get(_IX)
            }
        }

        override fun Memcpy(dst: Array<Any?>?, src: Array<Any?>?, count: Int) {
//            memcpy( dst, src, count );
//            System.arraycopy(src, 0, dst, 0, count);
            throw Deprecation_Exception()
        }

        override fun Memset(dst: Array<Any?>?, `val`: Int, count: Int) {
//            memset( dst, val, count );
            Arrays.fill(dst, 0, count, `val`)
        }

        override fun Zero16(dst: FloatArray?, count: Int) {
//            memset( dst, 0, count * sizeof( float ) );
            Arrays.fill(dst, 0, count, 0f)
        }

        override fun Negate16(dst: FloatArray?, count: Int) {
//            unsigned int *ptr = reinterpret_cast<unsigned int *>(dst);
            for (_IX in 0 until count) {
                var _dst = java.lang.Float.floatToIntBits(dst.get(_IX))
                _dst = _dst xor (1 shl 31) // IEEE 32 bits float sign bit
                dst.get(_IX) = java.lang.Float.intBitsToFloat(_dst)
            }
        }

        override fun Copy16(dst: FloatArray?, src: FloatArray?, count: Int) {
//            for (int _IX = 0; _IX < count; _IX++) {
//                dst[_IX] = src[_IX];
//            }
            System.arraycopy(src, 0, dst, 0, count)
        }

        override fun Add16(dst: FloatArray?, src1: FloatArray?, src2: FloatArray?, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = src1.get(_IX) + src2.get(_IX)
            }
        }

        override fun Sub16(dst: FloatArray?, src1: FloatArray?, src2: FloatArray?, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = src1.get(_IX) - src2.get(_IX)
            }
        }

        override fun Mul16(dst: FloatArray?, src1: FloatArray?, constant: Float, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) = src1.get(_IX) * constant
            }
        }

        override fun AddAssign16(dst: FloatArray?, src: FloatArray?, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) += src.get(_IX)
            }
        }

        override fun SubAssign16(dst: FloatArray?, src: FloatArray?, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) -= src.get(_IX)
            }
        }

        override fun MulAssign16(dst: FloatArray?, constant: Float, count: Int) {
            for (_IX in 0 until count) {
                dst.get(_IX) *= constant
            }
        }

        override fun MatX_MultiplyVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            var j: Int
            val numRows: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumColumns())
            assert(dst.GetSize() >= mat.GetNumRows())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numRows = mat.GetNumRows()
            when (mat.GetNumColumns()) {
                1 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] = mPtr[mIndex + 0] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] = mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1]
                        mIndex += 2
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] = mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2]
                        mIndex += 3
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] =
                            mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3]
                        mIndex += 4
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] =
                            mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4]
                        mIndex += 5
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] =
                            mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4] + mPtr[mIndex + 5] * vPtr[5]
                        mIndex += 6
                        i++
                    }
                }
                else -> {
                    val numColumns = mat.GetNumColumns()
                    i = 0
                    while (i < numRows) {
                        var sum = mPtr[mIndex + 0] * vPtr[0]
                        j = 1
                        while (j < numColumns) {
                            sum += mPtr[mIndex + j] * vPtr[j]
                            j++
                        }
                        dstPtr[i] = sum
                        mIndex += numColumns
                        i++
                    }
                }
            }
        }

        override fun MatX_MultiplyAddVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            var j: Int
            val numRows: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumColumns())
            assert(dst.GetSize() >= mat.GetNumRows())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numRows = mat.GetNumRows()
            when (mat.GetNumColumns()) {
                1 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1]
                        mIndex += 2
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2]
                        mIndex += 3
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3]
                        mIndex += 4
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4]
                        mIndex += 5
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] += mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4] + mPtr[mIndex + 5] * vPtr[5]
                        mIndex += 6
                        i++
                    }
                }
                else -> {
                    val numColumns = mat.GetNumColumns()
                    i = 0
                    while (i < numRows) {
                        var sum = mPtr[mIndex + 0] * vPtr[0]
                        j = 1
                        while (j < numColumns) {
                            sum += mPtr[mIndex + j] * vPtr[j]
                            j++
                        }
                        dstPtr[i] += sum
                        mIndex += numColumns
                        i++
                    }
                }
            }
        }

        override fun MatX_MultiplySubVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            var j: Int
            val numRows: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumColumns())
            assert(dst.GetSize() >= mat.GetNumRows())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numRows = mat.GetNumRows()
            when (mat.GetNumColumns()) {
                1 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1]
                        mIndex += 2
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2]
                        mIndex += 3
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3]
                        mIndex += 4
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4]
                        mIndex += 5
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numRows) {
                        dstPtr[i] -= mPtr[mIndex + 0] * vPtr[0] + mPtr[mIndex + 1] * vPtr[1] + mPtr[mIndex + 2] * vPtr[2] + mPtr[mIndex + 3] * vPtr[3] + mPtr[mIndex + 4] * vPtr[4] + mPtr[mIndex + 5] * vPtr[5]
                        mIndex += 6
                        i++
                    }
                }
                else -> {
                    val numColumns = mat.GetNumColumns()
                    i = 0
                    while (i < numRows) {
                        var sum = mPtr[mIndex + 0] * vPtr[0]
                        j = 1
                        while (j < numColumns) {
                            sum += mPtr[mIndex + j] * vPtr[j]
                            j++
                        }
                        dstPtr[i] -= sum
                        mIndex += numColumns
                        i++
                    }
                }
            }
        }

        override fun MatX_TransposeMultiplyVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            var j: Int
            val numColumns: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumRows())
            assert(dst.GetSize() >= mat.GetNumColumns())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numColumns = mat.GetNumColumns()
            when (mat.GetNumRows()) {
                1 -> {
                    i = 0
                    while (i < numColumns) {
                        //TODO:check pointer to array conversion
                        dstPtr[i] = mPtr[mIndex] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] = mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1]
                        mIndex++
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] =
                            mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2]
                        mIndex++
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] =
                            mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3]
                        mIndex++
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] =
                            mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4]
                        mIndex++
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] =
                            mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4] + mPtr[mIndex + 5 * numColumns] * vPtr[5]
                        mIndex++
                        i++
                    }
                }
                else -> {
                    val numRows = mat.GetNumRows()
                    i = 0
                    while (i < numColumns) {
                        mIndex = i
                        var sum = mPtr[0] * vPtr[0]
                        j = 1
                        while (j < numRows) {
                            mIndex += numColumns
                            sum += mPtr[0] * vPtr[j]
                            j++
                        }
                        dstPtr[i] = sum
                        i++
                    }
                }
            }
        }

        override fun MatX_TransposeMultiplyAddVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            var j: Int
            val numColumns: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumRows())
            assert(dst.GetSize() >= mat.GetNumColumns())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numColumns = mat.GetNumColumns()
            when (mat.GetNumRows()) {
                1 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1]
                        mIndex++
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2]
                        mIndex++
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3]
                        mIndex++
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4]
                        mIndex++
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] += mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4] + mPtr[mIndex + 5 * numColumns] * vPtr[5]
                        mIndex++
                        i++
                    }
                }
                else -> {
                    val numRows = mat.GetNumRows()
                    i = 0
                    while (i < numColumns) {
                        mIndex = i
                        var sum = mPtr[0] * vPtr[0]
                        j = 1
                        while (j < numRows) {
                            mIndex += numColumns
                            sum += mPtr[0] * vPtr[j]
                            j++
                        }
                        dstPtr[i] += sum
                        i++
                    }
                }
            }
        }

        override fun MatX_TransposeMultiplySubVecX(dst: idVecX?, mat: idMatX?, vec: idVecX?) {
            var i: Int
            val numColumns: Int
            var mIndex = 0
            val mPtr: FloatArray?
            val vPtr: FloatArray?
            val dstPtr: FloatArray?
            assert(vec.GetSize() >= mat.GetNumRows())
            assert(dst.GetSize() >= mat.GetNumColumns())
            mPtr = mat.ToFloatPtr()
            vPtr = vec.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            numColumns = mat.GetNumColumns()
            when (mat.GetNumRows()) {
                1 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0]
                        mIndex++
                        i++
                    }
                }
                2 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1]
                        mIndex++
                        i++
                    }
                }
                3 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2]
                        mIndex++
                        i++
                    }
                }
                4 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3]
                        mIndex++
                        i++
                    }
                }
                5 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4]
                        mIndex++
                        i++
                    }
                }
                6 -> {
                    i = 0
                    while (i < numColumns) {
                        dstPtr[i] -= mPtr[mIndex] * vPtr[0] + mPtr[mIndex + numColumns] * vPtr[1] + mPtr[mIndex + 2 * numColumns] * vPtr[2] + mPtr[mIndex + 3 * numColumns] * vPtr[3] + mPtr[mIndex + 4 * numColumns] * vPtr[4] + mPtr[mIndex + 5 * numColumns] * vPtr[5]
                        mIndex++
                        i++
                    }
                }
                else -> {
                    val numRows = mat.GetNumRows()
                    i = 0
                    while (i < numColumns) {
                        mIndex = i
                        var sum = mPtr[mIndex] * vPtr[0]
                        var j = 1
                        while (j < numRows) {
                            mIndex += numColumns
                            sum += mPtr[mIndex] * vPtr[j]
                            j++
                        }
                        dstPtr[i] -= sum
                        i++
                    }
                }
            }
        }

        /*
         ============
         idSIMD_Generic::MatX_MultiplyMatX

         optimizes the following matrix multiplications:

         NxN * Nx6
         6xN * Nx6
         Nx6 * 6xN
         6x6 * 6xN

         with N in the range [1-6].
         ============
         */
        override fun MatX_MultiplyMatX(dst: idMatX?, m1: idMatX?, m2: idMatX?) {
            var i: Int
            var j: Int
            val k: Int
            val l: Int
            var n: Int
            var m1Index = 0
            var m2Index = 0
            var dIndex = 0
            val dstPtr: FloatArray?
            val m1Ptr: FloatArray?
            val m2Ptr: FloatArray?
            var sum: Double
            assert(m1.GetNumColumns() == m2.GetNumRows())
            dstPtr = dst.ToFloatPtr() //TODO:check floatptr back reference
            m1Ptr = m1.ToFloatPtr()
            m2Ptr = m2.ToFloatPtr()
            k = m1.GetNumRows()
            l = m2.GetNumColumns()
            when (m1.GetNumColumns()) {
                1 -> {
                    if (l == 6) {
                        i = 0
                        while (i < k) {
                            // Nx1 * 1x6
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 0]
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 1]
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 2]
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 3]
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 4]
                            dstPtr[dIndex++] = m1Ptr[m1Index + i] * m2Ptr[m2Index + 5]
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] = m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                2 -> {
                    if (l == 6) {
                        i = 0
                        while (i < k) {
                            // Nx2 * 2x6
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 6]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 7]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 2] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 8]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 3] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 9]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 4] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 10]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 5] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 11]
                            m1Index += 2
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + l]
                            m2Index++
                            j++
                        }
                        m1Index += 2
                        i++
                    }
                }
                3 -> {
                    if (l == 6) {
                        i = 0
                        while (i < k) {
                            // Nx3 * 3x6
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 6] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 12]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 7] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 13]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 2] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 8] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 14]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 3] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 9] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 15]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 4] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 10] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 16]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 5] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 11] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 17]
                            m1Index += 3
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * l]
                            m2Index++
                            j++
                        }
                        m1Index += 3
                        i++
                    }
                }
                4 -> {
                    if (l == 6) {
                        i = 0
                        while (i < k) {
                            // Nx4 * 4x6
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 6] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 12] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 18]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 7] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 13] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 19]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 2] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 8] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 14] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 20]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 3] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 9] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 15] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 21]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 4] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 10] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 16] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 22]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 5] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 11] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 17] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 23]
                            m1Index += 4
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * l]
                            m2Index++
                            j++
                        }
                        m1Index += 4
                        i++
                    }
                }
                5 -> {
                    if (l == 6) {
                        i = 0
                        while (i < k) {
                            // Nx5 * 5x6
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 6] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 12] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 18] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 24]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 7] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 13] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 19] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 25]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 2] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 8] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 14] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 20] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 26]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 3] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 9] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 15] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 21] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 27]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 4] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 10] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 16] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 22] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 28]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 5] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 11] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 17] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 23] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 29]
                            m1Index += 5
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * l] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * l]
                            m2Index++
                            j++
                        }
                        m1Index += 5
                        i++
                    }
                }
                6 -> {
                    when (k) {
                        1 -> {
                            if (l == 1) {        // 1x6 * 6x1
                                dstPtr[0] =
                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5]
                                return
                            }
                        }
                        2 -> {
                            if (l == 2) {        // 2x6 * 6x2
                                i = 0
                                while (i < 2) {
                                    j = 0
                                    while (j < 2) {
                                        dstPtr[dIndex] =
                                            m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 2 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 2 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 2 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 2 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 2 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 2 + j]
                                        dIndex++
                                        j++
                                    }
                                    m1Index += 6
                                    i++
                                }
                                return
                            }
                        }
                        3 -> {
                            if (l == 3) {        // 3x6 * 6x3
                                i = 0
                                while (i < 3) {
                                    j = 0
                                    while (j < 3) {
                                        dstPtr[dIndex] =
                                            m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 3 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 3 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 3 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 3 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 3 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 3 + j]
                                        dIndex++
                                        j++
                                    }
                                    m1Index += 6
                                    i++
                                }
                                return
                            }
                        }
                        4 -> {
                            run {
                                if (l == 4) {        // 4x6 * 6x4
                                    i = 0
                                    while (i < 4) {
                                        j = 0
                                        while (j < 4) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 4 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 4 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 4 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 4 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 4 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 4 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                            }
                            run {
                                if (l == 5) {        // 5x6 * 6x5
                                    i = 0
                                    while (i < 5) {
                                        j = 0
                                        while (j < 5) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 5 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 5 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 5 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 5 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 5 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 5 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                            }
                            run {
                                when (l) {
                                    1 -> {
                                        // 6x6 * 6x1
                                        i = 0
                                        while (i < 6) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 1] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 1] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 1] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 1] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 1]
                                            dIndex++
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    2 -> {
                                        // 6x6 * 6x2
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 2) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 2 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 2 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 2 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 2 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 2 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 2 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    3 -> {
                                        // 6x6 * 6x3
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 3) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 3 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 3 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 3 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 3 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 3 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 3 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    4 -> {
                                        // 6x6 * 6x4
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 4) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 4 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 4 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 4 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 4 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 4 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 4 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    5 -> {
                                        // 6x6 * 6x5
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 5) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 5 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 5 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 5 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 5 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 5 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 5 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    6 -> {
                                        // 6x6 * 6x6
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 6) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 6 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 6 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 6 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 6 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 6 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 6 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                }
                            }
                        }
                        5 -> {
                            run {
                                if (l == 5) {
                                    i = 0
                                    while (i < 5) {
                                        j = 0
                                        while (j < 5) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 5 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 5 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 5 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 5 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 5 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 5 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                            }
                            run {
                                when (l) {
                                    1 -> {
                                        i = 0
                                        while (i < 6) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 1] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 1] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 1] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 1] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 1]
                                            dIndex++
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    2 -> {
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 2) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 2 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 2 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 2 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 2 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 2 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 2 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    3 -> {
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 3) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 3 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 3 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 3 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 3 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 3 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 3 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    4 -> {
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 4) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 4 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 4 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 4 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 4 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 4 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 4 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    5 -> {
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 5) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 5 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 5 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 5 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 5 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 5 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 5 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                    6 -> {
                                        i = 0
                                        while (i < 6) {
                                            j = 0
                                            while (j < 6) {
                                                dstPtr[dIndex] =
                                                    m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 6 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 6 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 6 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 6 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 6 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 6 + j]
                                                dIndex++
                                                j++
                                            }
                                            m1Index += 6
                                            i++
                                        }
                                        return
                                    }
                                }
                            }
                        }
                        6 -> {
                            when (l) {
                                1 -> {
                                    i = 0
                                    while (i < 6) {
                                        dstPtr[dIndex] =
                                            m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 1] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 1] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 1] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 1] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 1] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 1]
                                        dIndex++
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                                2 -> {
                                    i = 0
                                    while (i < 6) {
                                        j = 0
                                        while (j < 2) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 2 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 2 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 2 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 2 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 2 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 2 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                                3 -> {
                                    i = 0
                                    while (i < 6) {
                                        j = 0
                                        while (j < 3) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 3 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 3 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 3 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 3 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 3 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 3 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                                4 -> {
                                    i = 0
                                    while (i < 6) {
                                        j = 0
                                        while (j < 4) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 4 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 4 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 4 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 4 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 4 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 4 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                                5 -> {
                                    i = 0
                                    while (i < 6) {
                                        j = 0
                                        while (j < 5) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 5 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 5 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 5 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 5 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 5 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 5 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                                6 -> {
                                    i = 0
                                    while (i < 6) {
                                        j = 0
                                        while (j < 6) {
                                            dstPtr[dIndex] =
                                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0 * 6 + j] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + 1 * 6 + j] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * 6 + j] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * 6 + j] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * 6 + j] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * 6 + j]
                                            dIndex++
                                            j++
                                        }
                                        m1Index += 6
                                        i++
                                    }
                                    return
                                }
                            }
                        }
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + 1] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3] * m2Ptr[m2Index + 3 * l] + m1Ptr[m1Index + 4] * m2Ptr[m2Index + 4 * l] + m1Ptr[m1Index + 5] * m2Ptr[m2Index + 5 * l]
                            m2Index++
                            j++
                        }
                        m1Index += 6
                        i++
                    }
                }
                else -> {
                    i = 0
                    while (i < k) {
                        j = 0
                        while (j < l) {
                            m2Index = j
                            sum = (m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]).toDouble()
                            n = 1
                            while (n < m1.GetNumColumns()) {
                                m2Index += l
                                sum += (m1Ptr[m1Index + n] * m2Ptr[m2Index + 0]).toDouble()
                                n++
                            }
                            dstPtr[dIndex++] = sum.toFloat()
                            j++
                        }
                        m1Index += m1.GetNumColumns()
                        i++
                    }
                }
            }
        }

        /*
         ============
         idSIMD_Generic::MatX_TransposeMultiplyMatX

         optimizes the following tranpose matrix multiplications:

         Nx6 * NxN
         6xN * 6x6

         with N in the range [1-6].
         ============
         */
        override fun MatX_TransposeMultiplyMatX(dst: idMatX?, m1: idMatX?, m2: idMatX?) {
            var i: Int
            var j: Int
            val k: Int
            val l: Int
            var n: Int
            var m1Index = 0
            var m2Index = 0
            var dIndex = 0
            val dstPtr: FloatArray?
            val m1Ptr: FloatArray?
            val m2Ptr: FloatArray?
            var sum: Double
            assert(m1.GetNumRows() == m2.GetNumRows())
            m1Ptr = m1.ToFloatPtr()
            m2Ptr = m2.ToFloatPtr()
            dstPtr = dst.ToFloatPtr()
            k = m1.GetNumColumns()
            l = m2.GetNumColumns()
            when (m1.GetNumRows()) {
                1 -> {
                    if (k == 6 && l == 1) {            // 1x6 * 1x1
                        i = 0
                        while (i < 6) {
                            dstPtr[dIndex++] = m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]
                            m1Index++
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] = m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                2 -> {
                    if (k == 6 && l == 2) {            // 2x6 * 2x2
                        i = 0
                        while (i < 6) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 2 + 0] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 2 + 0]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 2 + 1] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 2 + 1]
                            m1Index++
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + k] * m2Ptr[m2Index + l]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                3 -> {
                    if (k == 6 && l == 3) {            // 3x6 * 3x3
                        i = 0
                        while (i < 6) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 3 + 0] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 3 + 0] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 3 + 0]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 3 + 1] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 3 + 1] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 3 + 1]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 3 + 2] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 3 + 2] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 3 + 2]
                            m1Index++
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + k] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2 * k] * m2Ptr[m2Index + 2 * l]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                4 -> {
                    if (k == 6 && l == 4) {            // 4x6 * 4x4
                        i = 0
                        while (i < 6) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 4 + 0] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 4 + 0] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 4 + 0] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 4 + 0]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 4 + 1] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 4 + 1] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 4 + 1] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 4 + 1]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 4 + 2] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 4 + 2] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 4 + 2] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 4 + 2]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 4 + 3] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 4 + 3] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 4 + 3] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 4 + 3]
                            m1Index++
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + k] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2 * k] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3 * k] * m2Ptr[m2Index + 3 * l]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                5 -> {
                    if (k == 6 && l == 5) {            // 5x6 * 5x5
                        i = 0
                        while (i < 6) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 5 + 0] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 5 + 0] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 5 + 0] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 5 + 0] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 5 + 0]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 5 + 1] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 5 + 1] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 5 + 1] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 5 + 1] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 5 + 1]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 5 + 2] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 5 + 2] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 5 + 2] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 5 + 2] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 5 + 2]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 5 + 3] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 5 + 3] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 5 + 3] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 5 + 3] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 5 + 3]
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 5 + 4] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 5 + 4] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 5 + 4] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 5 + 4] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 5 + 4]
                            m1Index++
                            i++
                        }
                        return
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + k] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2 * k] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3 * k] * m2Ptr[m2Index + 3 * l] + m1Ptr[m1Index + 4 * k] * m2Ptr[m2Index + 4 * l]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                6 -> {
                    if (l == 6) {
                        when (k) {
                            1 -> {
                                m2Index = 0
                                j = 0
                                while (j < 6) {
                                    dstPtr[dIndex++] =
                                        m1Ptr[m1Index + 0 * 1] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 1] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 1] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 1] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 1] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 1] * m2Ptr[m2Index + 5 * 6]
                                    m2Index++
                                    j++
                                }
                                return
                            }
                            2 -> {
                                i = 0
                                while (i < 2) {
                                    m2Index = 0
                                    j = 0
                                    while (j < 6) {
                                        dstPtr[dIndex++] =
                                            m1Ptr[m1Index + 0 * 2] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 2] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 2] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 2] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 2] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 2] * m2Ptr[m2Index + 5 * 6]
                                        m2Index++
                                        j++
                                    }
                                    m1Index++
                                    i++
                                }
                                return
                            }
                            3 -> {
                                i = 0
                                while (i < 3) {
                                    m2Index = 0
                                    j = 0
                                    while (j < 6) {
                                        dstPtr[dIndex++] =
                                            m1Ptr[m1Index + 0 * 3] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 3] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 3] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 3] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 3] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 3] * m2Ptr[m2Index + 5 * 6]
                                        m2Index++
                                        j++
                                    }
                                    m1Index++
                                    i++
                                }
                                return
                            }
                            4 -> {
                                i = 0
                                while (i < 4) {
                                    m2Index = 0
                                    j = 0
                                    while (j < 6) {
                                        dstPtr[dIndex++] =
                                            m1Ptr[m1Index + 0 * 4] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 4] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 4] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 4] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 4] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 4] * m2Ptr[m2Index + 5 * 6]
                                        m2Index++
                                        j++
                                    }
                                    m1Index++
                                    i++
                                }
                                return
                            }
                            5 -> {
                                i = 0
                                while (i < 5) {
                                    m2Index = 0
                                    j = 0
                                    while (j < 6) {
                                        dstPtr[dIndex++] =
                                            m1Ptr[m1Index + 0 * 5] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 5] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 5] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 5] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 5] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 5] * m2Ptr[m2Index + 5 * 6]
                                        m2Index++
                                        j++
                                    }
                                    m1Index++
                                    i++
                                }
                                return
                            }
                            6 -> {
                                i = 0
                                while (i < 6) {
                                    m2Index = 0
                                    j = 0
                                    while (j < 6) {
                                        dstPtr[dIndex++] =
                                            m1Ptr[m1Index + 0 * 6] * m2Ptr[m2Index + 0 * 6] + m1Ptr[m1Index + 1 * 6] * m2Ptr[m2Index + 1 * 6] + m1Ptr[m1Index + 2 * 6] * m2Ptr[m2Index + 2 * 6] + m1Ptr[m1Index + 3 * 6] * m2Ptr[m2Index + 3 * 6] + m1Ptr[m1Index + 4 * 6] * m2Ptr[m2Index + 4 * 6] + m1Ptr[m1Index + 5 * 6] * m2Ptr[m2Index + 5 * 6]
                                        m2Index++
                                        j++
                                    }
                                    m1Index++
                                    i++
                                }
                                return
                            }
                        }
                    }
                    i = 0
                    while (i < k) {
                        m2Index = 0
                        j = 0
                        while (j < l) {
                            dstPtr[dIndex++] =
                                m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0] + m1Ptr[m1Index + k] * m2Ptr[m2Index + l] + m1Ptr[m1Index + 2 * k] * m2Ptr[m2Index + 2 * l] + m1Ptr[m1Index + 3 * k] * m2Ptr[m2Index + 3 * l] + m1Ptr[m1Index + 4 * k] * m2Ptr[m2Index + 4 * l] + m1Ptr[m1Index + 5 * k] * m2Ptr[m2Index + 5 * l]
                            m2Index++
                            j++
                        }
                        m1Index++
                        i++
                    }
                }
                else -> {
                    i = 0
                    while (i < k) {
                        j = 0
                        while (j < l) {
                            m1Index = i
                            m2Index = j
                            sum = (m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]).toDouble()
                            n = 1
                            while (n < m1.GetNumRows()) {
                                m1Index += k
                                m2Index += l
                                sum += (m1Ptr[m1Index + 0] * m2Ptr[m2Index + 0]).toDouble()
                                n++
                            }
                            dstPtr[dIndex++] = sum.toFloat()
                            j++
                        }
                        i++
                    }
                }
            }
        }

        private fun NSKIP(n: Int, s: Int): Int {
            return n shl 3 or (s and 7)
        }

        override fun MatX_LowerTriangularSolve(L: idMatX?, x: FloatArray?, b: FloatArray?, n: Int) {
            MatX_LowerTriangularSolve(L, x, b, n, 0)
        }

        /*
         ============
         idSIMD_Generic::MatX_LowerTriangularSolve

         solves x in Lx = b for the n * n sub-matrix of L
         if skip > 0 the first skip elements of x are assumed to be valid already
         L has to be a lower triangular matrix with (implicit) ones on the diagonal
         x == b is allowed
         ============
         */
        override fun MatX_LowerTriangularSolve(L: idMatX?, x: FloatArray?, b: FloatArray?, n: Int, skip: Int) {
//#if 1
            var skip = skip
            val nc: Int
            var lptr: FloatArray?
            var lIndex = 0
            if (skip >= n) {
                return
            }
            lptr = L.ToFloatPtr()
            nc = L.GetNumColumns()

            // unrolled cases for n < 8
            if (n < 8) {
//		#define NSKIP( n, s )	((n<<3)|(s&7))
                when (NSKIP(n, skip)) {
                    NSKIP1_0 -> {
                        x.get(0) = b.get(0)
                        return
                    }
                    NSKIP2_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        return
                    }
                    NSKIP2_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        return
                    }
                    NSKIP3_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        return
                    }
                    NSKIP3_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        return
                    }
                    NSKIP3_2 -> {
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        return
                    }
                    NSKIP4_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        return
                    }
                    NSKIP4_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        return
                    }
                    NSKIP4_2 -> {
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        return
                    }
                    NSKIP4_3 -> {
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        return
                    }
                    NSKIP5_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        return
                    }
                    NSKIP5_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        return
                    }
                    NSKIP5_2 -> {
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        return
                    }
                    NSKIP5_3 -> {
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        return
                    }
                    NSKIP5_4 -> {
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        return
                    }
                    NSKIP6_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP6_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP6_2 -> {
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP6_3 -> {
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP6_4 -> {
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP6_5 -> {
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        return
                    }
                    NSKIP7_0 -> {
                        x.get(0) = b.get(0)
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_1 -> {
                        x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_2 -> {
                        x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_3 -> {
                        x.get(3) =
                            b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                                2
                            )
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_4 -> {
                        x.get(4) =
                            b.get(4) - lptr[4 * nc + 0] * x.get(0) - lptr[4 * nc + 1] * x.get(1) - lptr[4 * nc + 2] * x.get(
                                2
                            ) - lptr[4 * nc + 3] * x.get(3)
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_5 -> {
                        x.get(5) =
                            b.get(5) - lptr[5 * nc + 0] * x.get(0) - lptr[5 * nc + 1] * x.get(1) - lptr[5 * nc + 2] * x.get(
                                2
                            ) - lptr[5 * nc + 3] * x.get(3) - lptr[5 * nc + 4] * x.get(4)
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                    NSKIP7_6 -> {
                        x.get(6) =
                            b.get(6) - lptr[6 * nc + 0] * x.get(0) - lptr[6 * nc + 1] * x.get(1) - lptr[6 * nc + 2] * x.get(
                                2
                            ) - lptr[6 * nc + 3] * x.get(3) - lptr[6 * nc + 4] * x.get(4) - lptr[6 * nc + 5] * x.get(5)
                        return
                    }
                }
                return
            }
            when (skip) {
                0 -> {
                    x.get(0) = b.get(0)
                    x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                    x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                    x.get(3) =
                        b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                            2
                        )
                    skip = 4
                }
                1 -> {
                    x.get(1) = b.get(1) - lptr[1 * nc + 0] * x.get(0)
                    x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                    x.get(3) =
                        b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                            2
                        )
                    skip = 4
                }
                2 -> {
                    x.get(2) = b.get(2) - lptr[2 * nc + 0] * x.get(0) - lptr[2 * nc + 1] * x.get(1)
                    x.get(3) =
                        b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                            2
                        )
                    skip = 4
                }
                3 -> {
                    x.get(3) =
                        b.get(3) - lptr[3 * nc + 0] * x.get(0) - lptr[3 * nc + 1] * x.get(1) - lptr[3 * nc + 2] * x.get(
                            2
                        )
                    skip = 4
                }
            }

//	lptr = L[skip];
            lptr = L.oGet(skip)
            var i: Int
            var j: Int
            var s0: Double
            var s1: Double
            var s2: Double
            var s3: Double
            i = skip
            while (i < n) {
                s0 = (lptr[lIndex + 0] * x.get(0)).toDouble()
                s1 = (lptr[lIndex + 1] * x.get(1)).toDouble()
                s2 = (lptr[lIndex + 2] * x.get(2)).toDouble()
                s3 = (lptr[lIndex + 3] * x.get(3)).toDouble()
                j = 4
                while (j < i - 7) {
                    s0 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                    s2 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                    s3 += (lptr[lIndex + j + 3] * x.get(j + 3)).toDouble()
                    s0 += (lptr[lIndex + j + 4] * x.get(j + 4)).toDouble()
                    s1 += (lptr[lIndex + j + 5] * x.get(j + 5)).toDouble()
                    s2 += (lptr[lIndex + j + 6] * x.get(j + 6)).toDouble()
                    s3 += (lptr[lIndex + j + 7] * x.get(j + 7)).toDouble()
                    j += 8
                }
                when (i - j) {
                    7 -> {
                        s0 += (lptr[lIndex + j + 6] * x.get(j + 6)).toDouble()
                        s1 += (lptr[lIndex + j + 5] * x.get(j + 5)).toDouble()
                        s2 += (lptr[lIndex + j + 4] * x.get(j + 4)).toDouble()
                        s3 += (lptr[lIndex + j + 3] * x.get(j + 3)).toDouble()
                        s0 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    6 -> {
                        s1 += (lptr[lIndex + j + 5] * x.get(j + 5)).toDouble()
                        s2 += (lptr[lIndex + j + 4] * x.get(j + 4)).toDouble()
                        s3 += (lptr[lIndex + j + 3] * x.get(j + 3)).toDouble()
                        s0 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    5 -> {
                        s2 += (lptr[lIndex + j + 4] * x.get(j + 4)).toDouble()
                        s3 += (lptr[lIndex + j + 3] * x.get(j + 3)).toDouble()
                        s0 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    4 -> {
                        s3 += (lptr[lIndex + j + 3] * x.get(j + 3)).toDouble()
                        s0 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    3 -> {
                        s0 += (lptr[lIndex + j + 2] * x.get(j + 2)).toDouble()
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    2 -> {
                        s1 += (lptr[lIndex + j + 1] * x.get(j + 1)).toDouble()
                        s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    }
                    1 -> s2 += (lptr[lIndex + j + 0] * x.get(j + 0)).toDouble()
                    0 -> {}
                }
                var sum: Double
                sum = s3
                sum += s2
                sum += s1
                sum += s0
                sum -= b.get(i)
                x.get(i) = -sum.toFloat()
                lIndex += nc
                i++
            }

//#else
//
//	int i, j;
//	const float *lptr;
//	double sum;
//
//	for ( i = skip; i < n; i++ ) {
//		sum = b[i];
//		lptr = L[i];
//		for ( j = 0; j < i; j++ ) {
//			sum -= lptr[j] * x[j];
//		}
//		x[i] = sum;
//	}
//
//#endif
        }

        /*
         ============
         idSIMD_Generic::MatX_LowerTriangularSolveTranspose

         solves x in L'x = b for the n * n sub-matrix of L
         L has to be a lower triangular matrix with (implicit) ones on the diagonal
         x == b is allowed
         ============
         */
        override fun MatX_LowerTriangularSolveTranspose(L: idMatX?, x: FloatArray?, b: FloatArray?, n: Int) {
//#if 1
            val nc: Int
            var lptr: FloatArray?
            lptr = L.ToFloatPtr()
            nc = L.GetNumColumns()

            // unrolled cases for n < 8
            if (n < 8) {
                when (n) {
                    0 -> return
                    1 -> {
                        x.get(0) = b.get(0)
                        return
                    }
                    2 -> {
                        x.get(1) = b.get(1)
                        x.get(0) = b.get(0) - lptr[1 * nc + 0] * x.get(1)
                        return
                    }
                    3 -> {
                        x.get(2) = b.get(2)
                        x.get(1) = b.get(1) - lptr[2 * nc + 1] * x.get(2)
                        x.get(0) = b.get(0) - lptr[2 * nc + 0] * x.get(2) - lptr[1 * nc + 0] * x.get(1)
                        return
                    }
                    4 -> {
                        x.get(3) = b.get(3)
                        x.get(2) = b.get(2) - lptr[3 * nc + 2] * x.get(3)
                        x.get(1) = b.get(1) - lptr[3 * nc + 1] * x.get(3) - lptr[2 * nc + 1] * x.get(2)
                        x.get(0) =
                            b.get(0) - lptr[3 * nc + 0] * x.get(3) - lptr[2 * nc + 0] * x.get(2) - lptr[1 * nc + 0] * x.get(
                                1
                            )
                        return
                    }
                    5 -> {
                        x.get(4) = b.get(4)
                        x.get(3) = b.get(3) - lptr[4 * nc + 3] * x.get(4)
                        x.get(2) = b.get(2) - lptr[4 * nc + 2] * x.get(4) - lptr[3 * nc + 2] * x.get(3)
                        x.get(1) =
                            b.get(1) - lptr[4 * nc + 1] * x.get(4) - lptr[3 * nc + 1] * x.get(3) - lptr[2 * nc + 1] * x.get(
                                2
                            )
                        x.get(0) =
                            b.get(0) - lptr[4 * nc + 0] * x.get(4) - lptr[3 * nc + 0] * x.get(3) - lptr[2 * nc + 0] * x.get(
                                2
                            ) - lptr[1 * nc + 0] * x.get(1)
                        return
                    }
                    6 -> {
                        x.get(5) = b.get(5)
                        x.get(4) = b.get(4) - lptr[5 * nc + 4] * x.get(5)
                        x.get(3) = b.get(3) - lptr[5 * nc + 3] * x.get(5) - lptr[4 * nc + 3] * x.get(4)
                        x.get(2) =
                            b.get(2) - lptr[5 * nc + 2] * x.get(5) - lptr[4 * nc + 2] * x.get(4) - lptr[3 * nc + 2] * x.get(
                                3
                            )
                        x.get(1) =
                            b.get(1) - lptr[5 * nc + 1] * x.get(5) - lptr[4 * nc + 1] * x.get(4) - lptr[3 * nc + 1] * x.get(
                                3
                            ) - lptr[2 * nc + 1] * x.get(2)
                        x.get(0) =
                            b.get(0) - lptr[5 * nc + 0] * x.get(5) - lptr[4 * nc + 0] * x.get(4) - lptr[3 * nc + 0] * x.get(
                                3
                            ) - lptr[2 * nc + 0] * x.get(2) - lptr[1 * nc + 0] * x.get(1)
                        return
                    }
                    7 -> {
                        x.get(6) = b.get(6)
                        x.get(5) = b.get(5) - lptr[6 * nc + 5] * x.get(6)
                        x.get(4) = b.get(4) - lptr[6 * nc + 4] * x.get(6) - lptr[5 * nc + 4] * x.get(5)
                        x.get(3) =
                            b.get(3) - lptr[6 * nc + 3] * x.get(6) - lptr[5 * nc + 3] * x.get(5) - lptr[4 * nc + 3] * x.get(
                                4
                            )
                        x.get(2) =
                            b.get(2) - lptr[6 * nc + 2] * x.get(6) - lptr[5 * nc + 2] * x.get(5) - lptr[4 * nc + 2] * x.get(
                                4
                            ) - lptr[3 * nc + 2] * x.get(3)
                        x.get(1) =
                            b.get(1) - lptr[6 * nc + 1] * x.get(6) - lptr[5 * nc + 1] * x.get(5) - lptr[4 * nc + 1] * x.get(
                                4
                            ) - lptr[3 * nc + 1] * x.get(3) - lptr[2 * nc + 1] * x.get(2)
                        x.get(0) =
                            b.get(0) - lptr[6 * nc + 0] * x.get(6) - lptr[5 * nc + 0] * x.get(5) - lptr[4 * nc + 0] * x.get(
                                4
                            ) - lptr[3 * nc + 0] * x.get(3) - lptr[2 * nc + 0] * x.get(2) - lptr[1 * nc + 0] * x.get(1)
                        return
                    }
                }
                return
            }
            var i: Int
            var j: Int
            var s0: Double
            var s1: Double
            var s2: Double
            var s3: Double
            val xptr: FloatArray?
            var lIndex: Int
            var xIndex: Int
            lptr = L.ToFloatPtr()
            lIndex = n * nc + n - 4
            xptr = x
            xIndex = n

            // process 4 rows at a time
            i = n
            while (i >= 4) {
                s0 = b.get(i - 4)
                s1 = b.get(i - 3)
                s2 = b.get(i - 2)
                s3 = b.get(i - 1)
                // process 4x4 blocks
                j = 0
                while (j < n - i) {
                    s0 -= (lptr[lIndex + (j + 0) * nc + 0] * xptr.get(xIndex + j + 0)).toDouble()
                    s1 -= (lptr[lIndex + (j + 0) * nc + 1] * xptr.get(xIndex + j + 0)).toDouble()
                    s2 -= (lptr[lIndex + (j + 0) * nc + 2] * xptr.get(xIndex + j + 0)).toDouble()
                    s3 -= (lptr[lIndex + (j + 0) * nc + 3] * xptr.get(xIndex + j + 0)).toDouble()
                    s0 -= (lptr[lIndex + (j + 1) * nc + 0] * xptr.get(xIndex + j + 1)).toDouble()
                    s1 -= (lptr[lIndex + (j + 1) * nc + 1] * xptr.get(xIndex + j + 1)).toDouble()
                    s2 -= (lptr[lIndex + (j + 1) * nc + 2] * xptr.get(xIndex + j + 1)).toDouble()
                    s3 -= (lptr[lIndex + (j + 1) * nc + 3] * xptr.get(xIndex + j + 1)).toDouble()
                    s0 -= (lptr[lIndex + (j + 2) * nc + 0] * xptr.get(xIndex + j + 2)).toDouble()
                    s1 -= (lptr[lIndex + (j + 2) * nc + 1] * xptr.get(xIndex + j + 2)).toDouble()
                    s2 -= (lptr[lIndex + (j + 2) * nc + 2] * xptr.get(xIndex + j + 2)).toDouble()
                    s3 -= (lptr[lIndex + (j + 2) * nc + 3] * xptr.get(xIndex + j + 2)).toDouble()
                    s0 -= (lptr[lIndex + (j + 3) * nc + 0] * xptr.get(xIndex + j + 3)).toDouble()
                    s1 -= (lptr[lIndex + (j + 3) * nc + 1] * xptr.get(xIndex + j + 3)).toDouble()
                    s2 -= (lptr[lIndex + (j + 3) * nc + 2] * xptr.get(xIndex + j + 3)).toDouble()
                    s3 -= (lptr[lIndex + (j + 3) * nc + 3] * xptr.get(xIndex + j + 3)).toDouble()
                    j += 4
                }
                // process left over of the 4 rows
                s0 -= lptr[lIndex + 0 - 1 * nc] * s3
                s1 -= lptr[lIndex + 1 - 1 * nc] * s3
                s2 -= lptr[lIndex + 2 - 1 * nc] * s3
                s0 -= lptr[lIndex + 0 - 2 * nc] * s2
                s1 -= lptr[lIndex + 1 - 2 * nc] * s2
                s0 -= lptr[lIndex + 0 - 3 * nc] * s1
                // store result
                xptr.get(xIndex - 4) = s0.toFloat()
                xptr.get(xIndex - 3) = s1.toFloat()
                xptr.get(xIndex - 2) = s2.toFloat()
                xptr.get(xIndex - 1) = s3.toFloat()
                // update pointers for next four rows
                lIndex -= 4 + 4 * nc
                xIndex -= 4
                i -= 4
            }
            // process left over rows
            i--
            while (i >= 0) {
                s0 = b.get(i)
                lptr = L.oGet(0)
                lIndex = i
                j = i + 1
                while (j < n) {
                    s0 -= (lptr[lIndex + j * nc] * x.get(j)).toDouble()
                    j++
                }
                x.get(i) = s0.toFloat()
                i--
            }

//#else
//
//	int i, j, nc;
//	const float *ptr;
//	double sum;
//
//	nc = L.GetNumColumns();
//	for ( i = n - 1; i >= 0; i-- ) {
//		sum = b[i];
//		ptr = L[0] + i;
//		for ( j = i + 1; j < n; j++ ) {
//			sum -= ptr[j*nc] * x[j];
//		}
//		x[i] = sum;
//	}
//
//#endif
        }

        /*
         ============
         idSIMD_Generic::MatX_LDLTFactor

         in-place factorization LDL' of the n * n sub-matrix of mat
         the reciprocal of the diagonal elements are stored in invDiag
         ============
         */
        override fun MatX_LDLTFactor(mat: idMatX?, invDiag: idVecX?, n: Int): Boolean {
//#if 1
            var i: Int
            var j: Int
            var k: Int
            val nc: Int
            val v: FloatArray
            val diag: FloatArray
            var mptr: FloatArray?
            var s0: Float
            var s1: Float
            var s2: Float
            var s3: Float
            var sum: Float
            var d: Float
            var mIndex = 0
            v = FloatArray(n)
            diag = FloatArray(n)
            nc = mat.GetNumColumns()
            if (n <= 0) {
                return true
            }
            mptr = mat.oGet(0)
            sum = mptr[0]
            if (sum == 0.0f) {
                return false
            }
            diag[0] = sum
            invDiag.p[0] = 1.0f / sum.also { d = it }
            if (n <= 1) {
                return true
            }
            mptr = mat.oGet(0)
            j = 1
            while (j < n) {
                mptr[j * nc + 0] = mptr[j * nc + 0] * d
                j++
            }
            mptr = mat.oGet(1)
            v[0] = diag[0] * mptr[0]
            s0 = v[0] * mptr[0]
            sum = mptr[1] - s0
            if (sum == 0.0f) {
                return false
            }
            mat.oSet(1, 1, sum)
            diag[1] = sum
            d = 1.0f / sum
            invDiag.p[1] = d
            if (n <= 2) {
                return true
            }
            mptr = mat.oGet(0)
            j = 2
            while (j < n) {
                mptr[j * nc + 1] = (mptr[j * nc + 1] - v[0] * mptr[j * nc + 0]) * d
                j++
            }
            mptr = mat.oGet(2)
            v[0] = diag[0] * mptr[0]
            s0 = v[0] * mptr[0]
            v[1] = diag[1] * mptr[1]
            s1 = v[1] * mptr[1]
            sum = mptr[2] - s0 - s1
            if (sum == 0.0f) {
                return false
            }
            mat.oSet(2, 2, sum)
            diag[2] = sum
            d = 1.0f / sum
            invDiag.p[2] = d
            if (n <= 3) {
                return true
            }
            mptr = mat.oGet(0)
            j = 3
            while (j < n) {
                mptr[j * nc + 2] = (mptr[j * nc + 2] - v[0] * mptr[j * nc + 0] - v[1] * mptr[j * nc + 1]) * d
                j++
            }
            mptr = mat.oGet(3)
            v[0] = diag[0] * mptr[0]
            s0 = v[0] * mptr[0]
            v[1] = diag[1] * mptr[1]
            s1 = v[1] * mptr[1]
            v[2] = diag[2] * mptr[2]
            s2 = v[2] * mptr[2]
            sum = mptr[3] - s0 - s1 - s2
            if (sum == 0.0f) {
                return false
            }
            mat.oSet(3, 3, sum)
            diag[3] = sum
            d = 1.0f / sum
            invDiag.p[3] = d
            if (n <= 4) {
                return true
            }
            mptr = mat.oGet(0)
            j = 4
            while (j < n) {
                mptr[j * nc + 3] =
                    (mptr[j * nc + 3] - v[0] * mptr[j * nc + 0] - v[1] * mptr[j * nc + 1] - v[2] * mptr[j * nc + 2]) * d
                j++
            }
            i = 4
            while (i < n) {
                mptr = mat.oGet(i)
                v[0] = diag[0] * mptr[0]
                s0 = v[0] * mptr[0]
                v[1] = diag[1] * mptr[1]
                s1 = v[1] * mptr[1]
                v[2] = diag[2] * mptr[2]
                s2 = v[2] * mptr[2]
                v[3] = diag[3] * mptr[3]
                s3 = v[3] * mptr[3]
                k = 4
                while (k < i - 3) {
                    v[k + 0] = diag[k + 0] * mptr[k + 0]
                    s0 += v[k + 0] * mptr[k + 0]
                    v[k + 1] = diag[k + 1] * mptr[k + 1]
                    s1 += v[k + 1] * mptr[k + 1]
                    v[k + 2] = diag[k + 2] * mptr[k + 2]
                    s2 += v[k + 2] * mptr[k + 2]
                    v[k + 3] = diag[k + 3] * mptr[k + 3]
                    s3 += v[k + 3] * mptr[k + 3]
                    k += 4
                }
                when (i - k) {
                    3 -> {
                        v[k + 2] = diag[k + 2] * mptr[k + 2]
                        s0 += v[k + 2] * mptr[k + 2]
                        v[k + 1] = diag[k + 1] * mptr[k + 1]
                        s1 += v[k + 1] * mptr[k + 1]
                        v[k + 0] = diag[k + 0] * mptr[k + 0]
                        s2 += v[k + 0] * mptr[k + 0]
                    }
                    2 -> {
                        v[k + 1] = diag[k + 1] * mptr[k + 1]
                        s1 += v[k + 1] * mptr[k + 1]
                        v[k + 0] = diag[k + 0] * mptr[k + 0]
                        s2 += v[k + 0] * mptr[k + 0]
                    }
                    1 -> {
                        v[k + 0] = diag[k + 0] * mptr[k + 0]
                        s2 += v[k + 0] * mptr[k + 0]
                    }
                    0 -> {}
                }
                sum = s3
                sum += s2
                sum += s1
                sum += s0
                sum = mptr[i] - sum
                if (sum == 0.0f) {
                    return false
                }
                mat.oSet(i, i, sum)
                diag[i] = sum
                d = 1.0f / sum
                invDiag.p[i] = d
                if (i + 1 >= n) {
                    return true
                }
                mptr = mat.oGet(i + 1)
                j = i + 1
                while (j < n) {
                    s0 = mptr[mIndex + 0] * v[0]
                    s1 = mptr[mIndex + 1] * v[1]
                    s2 = mptr[mIndex + 2] * v[2]
                    s3 = mptr[mIndex + 3] * v[3]
                    k = 4
                    while (k < i - 7) {
                        s0 += mptr[mIndex + k + 0] * v[k + 0]
                        s1 += mptr[mIndex + k + 1] * v[k + 1]
                        s2 += mptr[mIndex + k + 2] * v[k + 2]
                        s3 += mptr[mIndex + k + 3] * v[k + 3]
                        s0 += mptr[mIndex + k + 4] * v[k + 4]
                        s1 += mptr[mIndex + k + 5] * v[k + 5]
                        s2 += mptr[mIndex + k + 6] * v[k + 6]
                        s3 += mptr[mIndex + k + 7] * v[k + 7]
                        k += 8
                    }
                    when (i - k) {
                        7 -> {
                            s0 += mptr[mIndex + k + 6] * v[k + 6]
                            s1 += mptr[mIndex + k + 5] * v[k + 5]
                            s2 += mptr[mIndex + k + 4] * v[k + 4]
                            s3 += mptr[mIndex + k + 3] * v[k + 3]
                            s0 += mptr[mIndex + k + 2] * v[k + 2]
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        6 -> {
                            s1 += mptr[mIndex + k + 5] * v[k + 5]
                            s2 += mptr[mIndex + k + 4] * v[k + 4]
                            s3 += mptr[mIndex + k + 3] * v[k + 3]
                            s0 += mptr[mIndex + k + 2] * v[k + 2]
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        5 -> {
                            s2 += mptr[mIndex + k + 4] * v[k + 4]
                            s3 += mptr[mIndex + k + 3] * v[k + 3]
                            s0 += mptr[mIndex + k + 2] * v[k + 2]
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        4 -> {
                            s3 += mptr[mIndex + k + 3] * v[k + 3]
                            s0 += mptr[mIndex + k + 2] * v[k + 2]
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        3 -> {
                            s0 += mptr[mIndex + k + 2] * v[k + 2]
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        2 -> {
                            s1 += mptr[mIndex + k + 1] * v[k + 1]
                            s2 += mptr[mIndex + k + 0] * v[k + 0]
                        }
                        1 -> s2 += mptr[mIndex + k + 0] * v[k + 0]
                        0 -> {}
                    }
                    sum = s3
                    sum += s2
                    sum += s1
                    sum += s0
                    mptr[mIndex + i] = (mptr[mIndex + i] - sum) * d
                    mIndex += nc
                    j++
                }
                i++
            }
            return true

//#else
//
//	int i, j, k, nc;
//	float *v, *ptr, *diagPtr;
//	double d, sum;
//
//	v = (float *) _alloca16( n * sizeof( float ) );
//	nc = mat.GetNumColumns();
//
//	for ( i = 0; i < n; i++ ) {
//
//		ptr = mat[i];
//		diagPtr = mat[0];
//		sum = ptr[i];
//		for ( j = 0; j < i; j++ ) {
//			d = ptr[j];
//		    v[j] = diagPtr[0] * d;
//		    sum -= v[j] * d;
//			diagPtr += nc + 1;
//		}
//
//		if ( sum == 0.0f ) {
//			return false;
//		}
//
//		diagPtr[0] = sum;
//		invDiag[i] = d = 1.0f / sum;
//
//		if ( i + 1 >= n ) {
//			continue;
//		}
//
//		ptr = mat[i+1];
//		for ( j = i + 1; j < n; j++ ) {
//			sum = ptr[i];
//			for ( k = 0; k < i; k++ ) {
//				sum -= ptr[k] * v[k];
//			}
//			ptr[i] = sum * d;
//			ptr += nc;
//		}
//	}
//
//	return true;
//
//#endif
        }

        override fun BlendJoints(
            joints: Array<idJointQuat?>?,
            blendJoints: Array<idJointQuat?>?,
            lerp: Float,
            index: IntArray?,
            numJoints: Int
        ) {
            var i: Int
            i = 0
            while (i < numJoints) {
                val j = index.get(i)
                joints.get(j).q.Slerp(joints.get(j).q, blendJoints.get(j).q, lerp)
                joints.get(j).t.Lerp(joints.get(j).t, blendJoints.get(j).t, lerp)
                i++
            }
        }

        override fun BlendJoints(
            joints: Array<idJointQuat?>?,
            blendJoints: ArrayList<idJointQuat?>?,
            lerp: Float,
            index: IntArray?,
            numJoints: Int
        ) {
            var i: Int
            i = 0
            while (i < numJoints) {
                val j = index.get(i)
                joints.get(j).q.Slerp(joints.get(j).q, blendJoints.get(j).q, lerp)
                joints.get(j).t.Lerp(joints.get(j).t, blendJoints.get(j).t, lerp)
                i++
            }
        }

        override fun ConvertJointQuatsToJointMats(
            jointMats: Array<idJointMat?>?,
            jointQuats: Array<idJointQuat?>?,
            numJoints: Int
        ) {
            var i: Int
            i = 0
            while (i < numJoints) {
                jointMats.get(i).SetRotation(jointQuats.get(i).q.ToMat3())
                jointMats.get(i).SetTranslation(jointQuats.get(i).t)
                i++
            }
        }

        override fun ConvertJointMatsToJointQuats(
            jointQuats: ArrayList<idJointQuat?>?,
            jointMats: Array<idJointMat?>?,
            numJoints: Int
        ) {
            var i: Int
            i = 0
            while (i < numJoints) {
                if (i >= jointQuats.size) {
                    jointQuats.add(i, jointMats.get(i).ToJointQuat())
                } else {
                    jointQuats.set(i, jointMats.get(i).ToJointQuat())
                }
                i++
            }
        }

        override fun TransformJoints(
            jointMats: Array<idJointMat?>?,
            parents: IntArray?,
            firstJoint: Int,
            lastJoint: Int
        ) {
            var i: Int
            i = firstJoint
            while (i <= lastJoint) {
                assert(parents.get(i) < i)
                jointMats.get(i).oMulSet(jointMats.get(parents.get(i)))
                i++
            }
        }

        override fun UntransformJoints(
            jointMats: Array<idJointMat?>?,
            parents: IntArray?,
            firstJoint: Int,
            lastJoint: Int
        ) {
            var i: Int
            i = lastJoint
            while (i >= firstJoint) {
                assert(parents.get(i) < i)
                jointMats.get(i).oDivSet(jointMats.get(parents.get(i)))
                i--
            }
        }

        override fun TransformVerts(
            verts: Array<idDrawVert?>?,
            numVerts: Int,
            joints: Array<idJointMat?>?,
            weights: Array<idVec4?>?,
            index: IntArray?,
            numWeights: Int
        ) {
            var i: Int
            var j: Int
            val jointsPtr = jmtobb(joints)
            j = 0.also { i = it }
            while (i < numVerts) {
                val v = idVec3()
                v.oSet(toIdJointMat(jointsPtr, index.get(j * 2 + 0)).oMultiply(weights.get(j)))
                while (index.get(j * 2 + 1) == 0) {
                    j++
                    v.oPluSet(toIdJointMat(jointsPtr, index.get(j * 2 + 0)).oMultiply(weights.get(j)))
                }
                j++
                verts.get(i) = if (verts.get(i) == null) idDrawVert() else verts.get(i)
                verts.get(i).xyz.oSet(v)
                i++
            }
        }

        override fun TracePointCull(
            cullBits: ByteArray?,
            totalOr: ByteArray?,
            radius: Float,
            planes: Array<idPlane?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int
        ) {
            var i: Int
            var tOr: Byte
            tOr = 0
            i = 0
            while (i < numVerts) {
                var bits: Int
                var d0: Float
                var d1: Float
                var d2: Float
                var d3: Float
                var t: Float
                val v = verts.get(i).xyz
                d0 = planes.get(0).Distance(v)
                d1 = planes.get(1).Distance(v)
                d2 = planes.get(2).Distance(v)
                d3 = planes.get(3).Distance(v)
                t = d0 + radius
                bits = Math_h.FLOATSIGNBITSET(t) shl 0
                t = d1 + radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 1)
                t = d2 + radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 2)
                t = d3 + radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 3)
                t = d0 - radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 4)
                t = d1 - radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 5)
                t = d2 - radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 6)
                t = d3 - radius
                bits = bits or (Math_h.FLOATSIGNBITSET(t) shl 7)
                bits = bits xor 0x0F // flip lower four bits
                tOr = tOr or bits
                cullBits.get(i) = bits.toByte()
                i++
            }
            totalOr.get(0) = tOr
        }

        override fun DecalPointCull(
            cullBits: ByteArray?,
            planes: Array<idPlane?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int
        ) {
            var i: Int
            i = 0
            while (i < numVerts) {
                var bits: Int
                var d0: Float
                var d1: Float
                var d2: Float
                var d3: Float
                var d4: Float
                var d5: Float
                val v = verts.get(i).xyz
                d0 = planes.get(0).Distance(v)
                d1 = planes.get(1).Distance(v)
                d2 = planes.get(2).Distance(v)
                d3 = planes.get(3).Distance(v)
                d4 = planes.get(4).Distance(v)
                d5 = planes.get(5).Distance(v)
                bits = Math_h.FLOATSIGNBITSET(d0) shl 0
                bits = bits or (Math_h.FLOATSIGNBITSET(d1) shl 1)
                bits = bits or (Math_h.FLOATSIGNBITSET(d2) shl 2)
                bits = bits or (Math_h.FLOATSIGNBITSET(d3) shl 3)
                bits = bits or (Math_h.FLOATSIGNBITSET(d4) shl 4)
                bits = bits or (Math_h.FLOATSIGNBITSET(d5) shl 5)
                cullBits.get(i) = (bits xor 0x3F).toByte() // flip lower 6 bits
                i++
            }
        }

        override fun OverlayPointCull(
            cullBits: ByteArray?,
            texCoords: Array<idVec2?>?,
            planes: Array<idPlane?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int
        ) {
            var i: Int
            i = 0
            while (i < numVerts) {
                var bits: Int
                var d0: Float
                var d1: Float
                val v = verts.get(i).xyz
                texCoords.get(i).oSet(0, planes.get(0).Distance(v).also { d0 = it })
                texCoords.get(i).oSet(1, planes.get(1).Distance(v).also { d1 = it })
                bits = Math_h.FLOATSIGNBITSET(d0) shl 0
                d0 = 1.0f - d0
                bits = bits or (Math_h.FLOATSIGNBITSET(d1) shl 1)
                d1 = 1.0f - d1
                bits = bits or (Math_h.FLOATSIGNBITSET(d0) shl 2)
                bits = bits or (Math_h.FLOATSIGNBITSET(d1) shl 3)
                cullBits.get(i) = bits.toByte()
                i++
            }
        }

        /*
         ============
         idSIMD_Generic::DeriveTriPlanes

         Derives a plane equation for each triangle.
         ============
         */
        override fun DeriveTriPlanes(
            planes: Array<idPlane?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int,
            indexes: IntArray?,
            numIndexes: Int
        ) {
            var i: Int
            var planePtr: Int
            i = 0.also { planePtr = it }
            while (i < numIndexes) {
                val a: idDrawVert?
                val b: idDrawVert?
                val c: idDrawVert?
                val d0 = FloatArray(3)
                val d1 = FloatArray(3)
                var f: Float
                val n = idVec3()
                a = verts.get(indexes.get(i + 0))
                b = verts.get(indexes.get(i + 1))
                c = verts.get(indexes.get(i + 2))
                d0[0] = b.xyz.oGet(0) - a.xyz.oGet(0)
                d0[1] = b.xyz.oGet(1) - a.xyz.oGet(1)
                d0[2] = b.xyz.oGet(2) - a.xyz.oGet(2)
                d1[0] = c.xyz.oGet(0) - a.xyz.oGet(0)
                d1[1] = c.xyz.oGet(1) - a.xyz.oGet(1)
                d1[2] = c.xyz.oGet(2) - a.xyz.oGet(2)
                n.oSet(
                    idVec3(
                        d1[1] * d0[2] - d1[2] * d0[1],
                        d1[2] * d0[0] - d1[0] * d0[2],
                        d1[0] * d0[1] - d1[1] * d0[0]
                    )
                )
                f = idMath.RSqrt(n.x * n.x + n.y * n.y + n.z * n.z)
                n.x *= f
                n.y *= f
                n.z *= f
                planes.get(planePtr).SetNormal(n)
                planes.get(planePtr).FitThroughPoint(a.xyz)
                planePtr++
                i += 3
            }
        }

        /*
         ============
         idSIMD_Generic::DeriveTangents

         Derives the normal and orthogonal tangent vectors for the triangle vertices.
         For each vertex the normal and tangent vectors are derived from all triangles
         using the vertex which results in smooth tangents across the mesh.
         In the process the triangle planes are calculated as well.
         ============
         */
        override fun DeriveTangents(
            planes: Array<idPlane?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int,
            indexes: IntArray?,
            numIndexes: Int
        ) {
            var i: Int
            var planesPtr: Int
            val used = BooleanArray(numVerts)
            //	memset( used, 0, numVerts * sizeof( used[0] ) );
            i = 0.also { planesPtr = it }
            while (i < numIndexes) {
                var a: idDrawVert?
                var b: idDrawVert?
                var c: idDrawVert?
                var signBit: Int
                val d0 = FloatArray(5)
                val d1 = FloatArray(5)
                var f: Float
                var area: Float
                val n = idVec3()
                val t0 = idVec3()
                val t1 = idVec3()
                val v0 = indexes.get(i + 0)
                val v1 = indexes.get(i + 1)
                val v2 = indexes.get(i + 2)
                a = verts.get(v0)
                b = verts.get(v1)
                c = verts.get(v2)
                d0[0] = b.xyz.oGet(0) - a.xyz.oGet(0)
                d0[1] = b.xyz.oGet(1) - a.xyz.oGet(1)
                d0[2] = b.xyz.oGet(2) - a.xyz.oGet(2)
                d0[3] = b.st.oGet(0) - a.st.oGet(0)
                d0[4] = b.st.oGet(1) - a.st.oGet(1)
                d1[0] = c.xyz.oGet(0) - a.xyz.oGet(0)
                d1[1] = c.xyz.oGet(1) - a.xyz.oGet(1)
                d1[2] = c.xyz.oGet(2) - a.xyz.oGet(2)
                d1[3] = c.st.oGet(0) - a.st.oGet(0)
                d1[4] = c.st.oGet(1) - a.st.oGet(1)

                // normal
                n.oSet(
                    idVec3(
                        d1[1] * d0[2] - d1[2] * d0[1],
                        d1[2] * d0[0] - d1[0] * d0[2],
                        d1[0] * d0[1] - d1[1] * d0[0]
                    )
                )
                f = idMath.RSqrt(n.x * n.x + n.y * n.y + n.z * n.z)
                n.x *= f
                n.y *= f
                n.z *= f
                planes.get(planesPtr).SetNormal(n)
                planes.get(planesPtr).FitThroughPoint(a.xyz)
                planesPtr++

                // area sign bit
                area = d0[3] * d1[4] - d0[4] * d1[3]
                signBit = java.lang.Float.floatToIntBits(area) and (1 shl 31)

                // first tangent
                t0.oSet(0, d0[0] * d1[4] - d0[4] * d1[0])
                t0.oSet(1, d0[1] * d1[4] - d0[4] * d1[1])
                t0.oSet(2, d0[2] * d1[4] - d0[4] * d1[2])
                f = idMath.RSqrt(t0.x * t0.x + t0.y * t0.y + t0.z * t0.z)
                f = java.lang.Float.intBitsToFloat(java.lang.Float.floatToIntBits(f) xor signBit)
                t0.x *= f
                t0.y *= f
                t0.z *= f

                // second tangent
                t1.oSet(0, d0[3] * d1[0] - d0[0] * d1[3])
                t1.oSet(1, d0[3] * d1[1] - d0[1] * d1[3])
                t1.oSet(2, d0[3] * d1[2] - d0[2] * d1[3])
                f = idMath.RSqrt(t1.x * t1.x + t1.y * t1.y + t1.z * t1.z)
                f = java.lang.Float.intBitsToFloat(java.lang.Float.floatToIntBits(f) xor signBit)
                t1.x *= f
                t1.y *= f
                t1.z *= f
                if (used[v0]) {
                    a.normal.oPluSet(n)
                    a.tangents[0].oPluSet(t0)
                    a.tangents[1].oPluSet(t1)
                } else {
                    a.normal.oSet(n)
                    a.tangents[0] = t0
                    a.tangents[1] = t1
                    used[v0] = true
                }
                if (used[v1]) {
                    b.normal.oPluSet(n)
                    b.tangents[0].oPluSet(t0)
                    b.tangents[1].oPluSet(t1)
                } else {
                    b.normal.oSet(n)
                    b.tangents[0] = t0
                    b.tangents[1] = t1
                    used[v1] = true
                }
                if (used[v2]) {
                    c.normal.oPluSet(n)
                    c.tangents[0].oPluSet(t0)
                    c.tangents[1].oPluSet(t1)
                } else {
                    c.normal.oSet(n)
                    c.tangents[0] = t0
                    c.tangents[1] = t1
                    used[v2] = true
                }
                i += 3
            }
        }

        /*
         ============
         idSIMD_Generic::DeriveUnsmoothedTangents

         Derives the normal and orthogonal tangent vectors for the triangle vertices.
         For each vertex the normal and tangent vectors are derived from a single dominant triangle.
         ============
         */
        override fun DeriveUnsmoothedTangents(
            verts: Array<idDrawVert?>?,
            dominantTris: Array<dominantTri_s?>?,
            numVerts: Int
        ) {
            for (i in 0 until numVerts) {
                val a: idDrawVert?
                val b: idDrawVert?
                val c: idDrawVert?
                val d0: Float
                val d1: Float
                val d2: Float
                val d3: Float
                val d4: Float
                val d5: Float
                val d6: Float
                val d7: Float
                val d8: Float
                val d9: Float
                val s0: Float
                val s1: Float
                val s2: Float
                val n0: Float
                val n1: Float
                val n2: Float
                val t0: Float
                val t1: Float
                val t2: Float
                val t3: Float
                val t4: Float
                val t5: Float
                val dt = dominantTris.get(i)
                a = verts.get(i)
                b = verts.get(dt.v2)
                c = verts.get(dt.v3)
                d0 = b.xyz.oGet(0) - a.xyz.oGet(0)
                d1 = b.xyz.oGet(1) - a.xyz.oGet(1)
                d2 = b.xyz.oGet(2) - a.xyz.oGet(2)
                d3 = b.st.oGet(0) - a.st.oGet(0)
                d4 = b.st.oGet(1) - a.st.oGet(1)
                d5 = c.xyz.oGet(0) - a.xyz.oGet(0)
                d6 = c.xyz.oGet(1) - a.xyz.oGet(1)
                d7 = c.xyz.oGet(2) - a.xyz.oGet(2)
                d8 = c.st.oGet(0) - a.st.oGet(0)
                d9 = c.st.oGet(1) - a.st.oGet(1)
                s0 = dt.normalizationScale[0]
                s1 = dt.normalizationScale[1]
                s2 = dt.normalizationScale[2]
                n0 = s2 * (d6 * d2 - d7 * d1)
                n1 = s2 * (d7 * d0 - d5 * d2)
                n2 = s2 * (d5 * d1 - d6 * d0)
                t0 = s0 * (d0 * d9 - d4 * d5)
                t1 = s0 * (d1 * d9 - d4 * d6)
                t2 = s0 * (d2 * d9 - d4 * d7)
                if (Simd_Generic.DERIVE_UNSMOOTHED_BITANGENT) {
                    t3 = s1 * (n2 * t1 - n1 * t2)
                    t4 = s1 * (n0 * t2 - n2 * t0)
                    t5 = s1 * (n1 * t0 - n0 * t1)
                } else {
                    t3 = s1 * (d3 * d5 - d0 * d8)
                    t4 = s1 * (d3 * d6 - d1 * d8)
                    t5 = s1 * (d3 * d7 - d2 * d8)
                }
                a.normal.oSet(0, n0)
                a.normal.oSet(1, n1)
                a.normal.oSet(2, n2)
                a.tangents[0].oSet(0, t0)
                a.tangents[0].oSet(1, t1)
                a.tangents[0].oSet(2, t2)
                a.tangents[1].oSet(0, t3)
                a.tangents[1].oSet(1, t4)
                a.tangents[1].oSet(2, t5)
            }
        }

        /*
         ============
         idSIMD_Generic::NormalizeTangents

         Normalizes each vertex normal and projects and normalizes the
         tangent vectors onto the plane orthogonal to the vertex normal.
         ============
         */
        override fun NormalizeTangents(verts: Array<idDrawVert?>?, numVerts: Int) {
            for (i in 0 until numVerts) {
                val v = verts.get(i).normal
                var f: Float
                f = idMath.RSqrt(v.x * v.x + v.y * v.y + v.z * v.z)
                v.x *= f
                v.y *= f
                v.z *= f
                for (j in 0..1) {
                    val t = verts.get(i).tangents[j]
                    t.oMinSet(v.oMultiply(t.oMultiply(v)))
                    f = idMath.RSqrt(t.x * t.x + t.y * t.y + t.z * t.z)
                    t.x *= f
                    t.y *= f
                    t.z *= f
                }
            }
        }

        /*
         ============
         idSIMD_Generic::CreateTextureSpaceLightVectors

         Calculates light vectors in texture space for the given triangle vertices.
         For each vertex the direction towards the light origin is projected onto texture space.
         The light vectors are only calculated for the vertices referenced by the indexes.
         ============
         */
        override fun CreateTextureSpaceLightVectors(
            lightVectors: Array<idVec3?>?,
            lightOrigin: idVec3?,
            verts: Array<idDrawVert?>?,
            numVerts: Int,
            indexes: IntArray?,
            numIndexes: Int
        ) {
            val used = BooleanArray(numVerts)
            //	memset( used, 0, numVerts * sizeof( used[0] ) );
            for (i in numIndexes - 1 downTo 0) {
                used[indexes.get(i)] = true
            }
            for (i in 0 until numVerts) {
                if (!used[i]) {
                    continue
                }
                val v = verts.get(i)
                val lightDir = idVec3(lightOrigin.oMinus(v.xyz))
                lightVectors.get(i).oSet(0, lightDir.oMultiply(v.tangents[0]))
                lightVectors.get(i).oSet(1, lightDir.oMultiply(v.tangents[1]))
                lightVectors.get(i).oSet(2, lightDir.oMultiply(v.normal))
            }
        }

        /*
         ============
         idSIMD_Generic::CreateSpecularTextureCoords

         Calculates specular texture coordinates for the given triangle vertices.
         For each vertex the normalized direction towards the light origin is added to the
         normalized direction towards the view origin and the result is projected onto texture space.
         The texture coordinates are only calculated for the vertices referenced by the indexes.
         ============
         */
        override fun CreateSpecularTextureCoords(
            texCoords: Array<idVec4?>?,
            lightOrigin: idVec3?,
            viewOrigin: idVec3?,
            verts: Array<idDrawVert?>?,
            numVerts: Int,
            indexes: IntArray?,
            numIndexes: Int
        ) {
            val used = BooleanArray(numVerts)
            //	memset( used, 0, numVerts * sizeof( used[0] ) );
            for (i in numIndexes - 1 downTo 0) {
                used[indexes.get(i)] = true
            }
            for (i in 0 until numVerts) {
                if (!used[i]) {
                    continue
                }
                val v = verts.get(i)
                val lightDir = idVec3(lightOrigin.oMinus(v.xyz))
                val viewDir = idVec3(viewOrigin.oMinus(v.xyz))
                var ilength: Float
                ilength = idMath.RSqrt(lightDir.oMultiply(lightDir))
                lightDir.oMulSet(ilength)
                ilength = idMath.RSqrt(viewDir.oMultiply(viewDir))
                viewDir.oMulSet(ilength)
                lightDir.oPluSet(viewDir)
                texCoords.get(i).oSet(0, lightDir.oMultiply(v.tangents[0]))
                texCoords.get(i).oSet(1, lightDir.oMultiply(v.tangents[1]))
                texCoords.get(i).oSet(2, lightDir.oMultiply(v.normal))
                texCoords.get(i).oSet(3, 1.0f)
            }
        }

        override fun CreateShadowCache(
            vertexCache: Array<idVec4?>?,
            vertRemap: IntArray?,
            lightOrigin: idVec3?,
            verts: Array<idDrawVert?>?,
            numVerts: Int
        ): Int {
            var outVerts = 0
            for (i in 0 until numVerts) {
                if (vertRemap.get(i) != 0) {
                    continue
                }
                val v = verts.get(i).xyz.ToFloatPtr()
                vertexCache.get(outVerts + 0).oSet(0, v[0])
                vertexCache.get(outVerts + 0).oSet(1, v[1])
                vertexCache.get(outVerts + 0).oSet(2, v[2])
                vertexCache.get(outVerts + 0).oSet(3, 1.0f)

                // R_SetupProjection() builds the projection matrix with a slight crunch
                // for depth, which keeps this w=0 division from rasterizing right at the
                // wrap around point and causing depth fighting with the rear caps
                vertexCache.get(outVerts + 1).oSet(0, v[0] - lightOrigin.oGet(0))
                vertexCache.get(outVerts + 1).oSet(1, v[1] - lightOrigin.oGet(1))
                vertexCache.get(outVerts + 1).oSet(2, v[2] - lightOrigin.oGet(2))
                vertexCache.get(outVerts + 1).oSet(3, 0.0f)
                vertRemap.get(i) = outVerts
                outVerts += 2
            }
            return outVerts
        }

        override fun CreateVertexProgramShadowCache(
            vertexCache: Array<idVec4?>?,
            verts: Array<idDrawVert?>?,
            numVerts: Int
        ): Int {
            for (i in 0 until numVerts) {
                val v = verts.get(i).xyz.ToFloatPtr()
                vertexCache.get(i * 2 + 0).oSet(0, v[0])
                vertexCache.get(i * 2 + 1).oSet(0, v[0])
                vertexCache.get(i * 2 + 0).oSet(1, v[1])
                vertexCache.get(i * 2 + 1).oSet(1, v[1])
                vertexCache.get(i * 2 + 0).oSet(2, v[2])
                vertexCache.get(i * 2 + 1).oSet(2, v[2])
                vertexCache.get(i * 2 + 0).oSet(3, 1.0f)
                vertexCache.get(i * 2 + 1).oSet(3, 0.0f)
            }
            return numVerts * 2
        }

        /*
         ============
         idSIMD_Generic::UpSamplePCMTo44kHz

         Duplicate samples for 44kHz output.
         ============
         */
        override fun UpSamplePCMTo44kHz(
            dest: FloatArray?,
            pcm: ShortArray?,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        ) {
            if (kHz == 11025) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.get(i * 4 + 3) = pcm.get(i + 0)
                        dest.get(i * 4 + 2) = dest.get(i * 4 + 3)
                        dest.get(i * 4 + 1) = dest.get(i * 4 + 2)
                        dest.get(i * 4 + 0) = dest.get(i * 4 + 1)
                    }
                } else {
                    var i = 0
                    while (i < numSamples) {
                        dest.get(i * 4 + 6) = pcm.get(i + 0)
                        dest.get(i * 4 + 4) = dest.get(i * 4 + 6)
                        dest.get(i * 4 + 2) = dest.get(i * 4 + 4)
                        dest.get(i * 4 + 0) = dest.get(i * 4 + 2)
                        dest.get(i * 4 + 7) = pcm.get(i + 1)
                        dest.get(i * 4 + 5) = dest.get(i * 4 + 7)
                        dest.get(i * 4 + 3) = dest.get(i * 4 + 5)
                        dest.get(i * 4 + 1) = dest.get(i * 4 + 3)
                        i += 2
                    }
                }
            } else if (kHz == 22050) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.get(i * 2 + 1) = pcm.get(i + 0)
                        dest.get(i * 2 + 0) = dest.get(i * 2 + 1)
                    }
                } else {
                    var i = 0
                    while (i < numSamples) {
                        dest.get(i * 2 + 2) = pcm.get(i + 0)
                        dest.get(i * 2 + 0) = dest.get(i * 2 + 2)
                        dest.get(i * 2 + 3) = pcm.get(i + 1)
                        dest.get(i * 2 + 1) = dest.get(i * 2 + 3)
                        i += 2
                    }
                }
            } else if (kHz == 44100) {
                for (i in 0 until numSamples) {
                    dest.get(i) = pcm.get(i)
                }
            } else {
//		assert( 0 );
                assert(false)
            }
        }

        /*
         ============
         idSIMD_Generic::UpSampleOGGTo44kHz

         Duplicate samples for 44kHz output.
         ============
         */
        override fun UpSampleOGGTo44kHz(
            dest: FloatArray?,
            offset: Int,
            ogg: Array<FloatArray?>?,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        ) {
            if (kHz == 11025) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.get(offset + (i * 4 + 3)) = ogg.get(0).get(i) * 32768.0f
                        dest.get(offset + (i * 4 + 2)) = dest.get(offset + (i * 4 + 3))
                        dest.get(offset + (i * 4 + 1)) = dest.get(offset + (i * 4 + 2))
                        dest.get(offset + (i * 4 + 0)) = dest.get(offset + (i * 4 + 1))
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.get(offset + (i * 8 + 6)) = ogg.get(0).get(i) * 32768.0f
                        dest.get(offset + (i * 8 + 4)) = dest.get(offset + (i * 8 + 6))
                        dest.get(offset + (i * 8 + 2)) = dest.get(offset + (i * 8 + 4))
                        dest.get(offset + (i * 8 + 0)) = dest.get(offset + (i * 8 + 2))
                        dest.get(offset + (i * 8 + 7)) = ogg.get(1).get(i) * 32768.0f
                        dest.get(offset + (i * 8 + 5)) = dest.get(offset + (i * 8 + 7))
                        dest.get(offset + (i * 8 + 3)) = dest.get(offset + (i * 8 + 5))
                        dest.get(offset + (i * 8 + 1)) = dest.get(offset + (i * 8 + 3))
                    }
                }
            } else if (kHz == 22050) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.get(offset + (i * 2 + 1)) = ogg.get(0).get(i) * 32768.0f
                        dest.get(offset + (i * 2 + 0)) = dest.get(offset + (i * 2 + 1))
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.get(offset + (i * 4 + 2)) = ogg.get(0).get(i) * 32768.0f
                        dest.get(offset + (i * 4 + 0)) = dest.get(offset + (i * 4 + 2))
                        dest.get(offset + (i * 4 + 3)) = ogg.get(1).get(i) * 32768.0f
                        dest.get(offset + (i * 4 + 1)) = dest.get(offset + (i * 4 + 3))
                    }
                }
            } else if (kHz == 44100) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.get(offset + (i * 1 + 0)) = ogg.get(0).get(i) * 32768.0f
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.get(offset + (i * 2 + 0)) = ogg.get(0).get(i) * 32768.0f
                        dest.get(offset + (i * 2 + 1)) = ogg.get(1).get(i) * 32768.0f
                    }
                }
            } else {
                assert(false)
            }
        }

        override fun UpSampleOGGTo44kHz(
            dest: FloatBuffer?,
            offset: Int,
            ogg: Array<FloatArray?>?,
            numSamples: Int,
            kHz: Int,
            numChannels: Int
        ) {
            var offset = offset
            offset += dest.position()
            if (kHz == 11025) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.put(offset + (i * 4 + 0), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 4 + 1), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 4 + 2), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 4 + 3), ogg.get(0).get(i) * 32768.0f)
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.put(offset + (i * 8 + 0), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 2), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 4), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 6), ogg.get(0).get(i) * 32768.0f)
                        dest.put(offset + (i * 8 + 1), ogg.get(1).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 3), ogg.get(1).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 5), ogg.get(1).get(i) * 32768.0f)
                            .put(offset + (i * 8 + 7), ogg.get(1).get(i) * 32768.0f)
                    }
                }
            } else if (kHz == 22050) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.put(offset + (i * 2 + 0), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 2 + 1), ogg.get(0).get(i) * 32768.0f)
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.put(offset + (i * 4 + 0), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 4 + 2), ogg.get(0).get(i) * 32768.0f)
                        dest.put(offset + (i * 4 + 1), ogg.get(1).get(i) * 32768.0f)
                            .put(offset + (i * 4 + 3), ogg.get(1).get(i) * 32768.0f)
                    }
                }
            } else if (kHz == 44100) {
                if (numChannels == 1) {
                    for (i in 0 until numSamples) {
                        dest.put(offset + (i * 1 + 0), ogg.get(0).get(i) * 32768.0f)
                    }
                } else {
                    for (i in 0 until numSamples shr 1) {
                        dest.put(offset + (i * 2 + 0), ogg.get(0).get(i) * 32768.0f)
                            .put(offset + (i * 2 + 1), ogg.get(1).get(i) * 32768.0f)
                    }
                }
            } else {
                assert(false)
            }
        }

        override fun MixSoundTwoSpeakerMono(
            mixBuffer: FloatArray?,
            samples: FloatArray?,
            numSamples: Int,
            lastV: FloatArray?,
            currentV: FloatArray?
        ) {
            var sL = lastV.get(0)
            var sR = lastV.get(1)
            val incL = (currentV.get(0) - lastV.get(0)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incR = (currentV.get(1) - lastV.get(1)) / Simd_Generic.MIXBUFFER_SAMPLES
            assert(numSamples == Simd_Generic.MIXBUFFER_SAMPLES)
            for (j in 0 until Simd_Generic.MIXBUFFER_SAMPLES) {
                mixBuffer.get(j * 2 + 0) += samples.get(j) * sL
                mixBuffer.get(j * 2 + 1) += samples.get(j) * sR
                sL += incL
                sR += incR
            }
        }

        override fun MixSoundTwoSpeakerStereo(
            mixBuffer: FloatArray?,
            samples: FloatArray?,
            numSamples: Int,
            lastV: FloatArray?,
            currentV: FloatArray?
        ) {
            var sL = lastV.get(0)
            var sR = lastV.get(1)
            val incL = (currentV.get(0) - lastV.get(0)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incR = (currentV.get(1) - lastV.get(1)) / Simd_Generic.MIXBUFFER_SAMPLES
            assert(numSamples == Simd_Generic.MIXBUFFER_SAMPLES)
            for (j in 0 until Simd_Generic.MIXBUFFER_SAMPLES) {
                mixBuffer.get(j * 2 + 0) += samples.get(j * 2 + 0) * sL
                mixBuffer.get(j * 2 + 1) += samples.get(j * 2 + 1) * sR
                sL += incL
                sR += incR
            }
        }

        override fun MixSoundSixSpeakerMono(
            mixBuffer: FloatArray?,
            samples: FloatArray?,
            numSamples: Int,
            lastV: FloatArray?,
            currentV: FloatArray?
        ) {
            var sL0 = lastV.get(0)
            var sL1 = lastV.get(1)
            var sL2 = lastV.get(2)
            var sL3 = lastV.get(3)
            var sL4 = lastV.get(4)
            var sL5 = lastV.get(5)
            val incL0 = (currentV.get(0) - lastV.get(0)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL1 = (currentV.get(1) - lastV.get(1)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL2 = (currentV.get(2) - lastV.get(2)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL3 = (currentV.get(3) - lastV.get(3)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL4 = (currentV.get(4) - lastV.get(4)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL5 = (currentV.get(5) - lastV.get(5)) / Simd_Generic.MIXBUFFER_SAMPLES
            assert(numSamples == Simd_Generic.MIXBUFFER_SAMPLES)
            for (i in 0 until Simd_Generic.MIXBUFFER_SAMPLES) {
                mixBuffer.get(i * 6 + 0) += samples.get(i) * sL0
                mixBuffer.get(i * 6 + 1) += samples.get(i) * sL1
                mixBuffer.get(i * 6 + 2) += samples.get(i) * sL2
                mixBuffer.get(i * 6 + 3) += samples.get(i) * sL3
                mixBuffer.get(i * 6 + 4) += samples.get(i) * sL4
                mixBuffer.get(i * 6 + 5) += samples.get(i) * sL5
                sL0 += incL0
                sL1 += incL1
                sL2 += incL2
                sL3 += incL3
                sL4 += incL4
                sL5 += incL5
            }
        }

        override fun MixSoundSixSpeakerStereo(
            mixBuffer: FloatArray?,
            samples: FloatArray?,
            numSamples: Int,
            lastV: FloatArray?,
            currentV: FloatArray?
        ) {
            var sL0 = lastV.get(0)
            var sL1 = lastV.get(1)
            var sL2 = lastV.get(2)
            var sL3 = lastV.get(3)
            var sL4 = lastV.get(4)
            var sL5 = lastV.get(5)
            val incL0 = (currentV.get(0) - lastV.get(0)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL1 = (currentV.get(1) - lastV.get(1)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL2 = (currentV.get(2) - lastV.get(2)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL3 = (currentV.get(3) - lastV.get(3)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL4 = (currentV.get(4) - lastV.get(4)) / Simd_Generic.MIXBUFFER_SAMPLES
            val incL5 = (currentV.get(5) - lastV.get(5)) / Simd_Generic.MIXBUFFER_SAMPLES
            assert(numSamples == Simd_Generic.MIXBUFFER_SAMPLES)
            for (i in 0 until Simd_Generic.MIXBUFFER_SAMPLES) {
                mixBuffer.get(i * 6 + 0) += samples.get(i * 2 + 0) * sL0
                mixBuffer.get(i * 6 + 1) += samples.get(i * 2 + 1) * sL1
                mixBuffer.get(i * 6 + 2) += samples.get(i * 2 + 0) * sL2
                mixBuffer.get(i * 6 + 3) += samples.get(i * 2 + 0) * sL3
                mixBuffer.get(i * 6 + 4) += samples.get(i * 2 + 0) * sL4
                mixBuffer.get(i * 6 + 5) += samples.get(i * 2 + 1) * sL5
                sL0 += incL0
                sL1 += incL1
                sL2 += incL2
                sL3 += incL3
                sL4 += incL4
                sL5 += incL5
            }
        }

        override fun MixedSoundToSamples(samples: ShortArray?, offset: Int, mixBuffer: FloatArray?, numSamples: Int) {
            for (i in 0 until numSamples) {
                if (mixBuffer.get(i) <= -32768.0f) {
                    samples.get(offset + i) = -32768
                } else if (mixBuffer.get(i) >= 32767.0f) {
                    samples.get(offset + i) = 32767
                } else {
                    samples.get(offset + i) = mixBuffer.get(i) as Short
                }
            }
        }

        companion object {
            //TODO: move to TempDump
            private fun jmtobb(joints: Array<idJointMat?>?): ByteBuffer? {
                val byteBuffer =
                    ByteBuffer.allocate(idJointMat.Companion.SIZE * joints.size).order(ByteOrder.LITTLE_ENDIAN)
                for (i in joints.indices) {
                    byteBuffer.position(i * idJointMat.Companion.SIZE)
                    byteBuffer.asFloatBuffer().put(joints.get(i).ToFloatPtr())
                }
                return byteBuffer
            }

            private fun toIdJointMat(jointsPtr: ByteBuffer?, position: Int): idJointMat? {
                val buffer = jointsPtr.duplicate().position(position).order(ByteOrder.LITTLE_ENDIAN)
                val temp = FloatArray(12)
                for (i in 0..11) {
                    temp[i] = buffer.float
                }
                return idJointMat(temp)
            }
        }
    }
}