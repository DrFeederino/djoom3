package neo.Renderer

import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.frameData_t
import neo.Renderer.tr_local.frameMemoryBlock_s
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_local.viewEntity_s
import neo.framework.Common
import neo.framework.Session
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.Math_h.DEG2RAD
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Math_h.idMath.FtoiFast
import neo.idlib.math.Math_h.idMath.SinCos
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Vector.DotProduct
import neo.idlib.math.Vector.VectorSubtract
import neo.idlib.math.Vector.idVec
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.tan

/**
 *
 */
object tr_main {
    //====================================================================
    //=====================================================
    val MEMORY_BLOCK_SIZE: Int = 0x100000

    /*
     ======================
     R_ShowColoredScreenRect
     ======================
     */
    val colors /*[]*/: Array<idVec4> = arrayOf(
        Lib.colorRed,
        Lib.colorGreen,
        Lib.colorBlue,
        Lib.colorYellow,
        Lib.colorMagenta,
        Lib.colorCyan,
        Lib.colorWhite,
        Lib.colorPurple
    )

    /*
     =================
     R_SetViewMatrix

     Sets up the world to view matrix for a given viewParm
     =================
     */
    private val s_flipMatrix /*[16]*/: FloatArray = floatArrayOf( // convert from our coordinate system (looking down X)
        // to OpenGL's coordinate system (looking down -Z)
        -0f, 0f, -1f, 0f,
        -1f, 0f, -0f, 0f,
        -0f, 1f, -0f, 0f,
        -0f, 0f, -0f, 1f
    )

    /*
     ================
     R_RenderView

     A view may be either the actual camera view,
     a mirror / remote location, or a 3D view on a gui surface.

     Parms will typically be allocated with R_FrameAlloc
     ================
     */
    var DEBUG_R_RenderView: Int = 0

    /*
     =================
     R_CornerCullLocalBox

     Tests all corners against the frustum.
     Can still generate a few false positives when the box is outside a corner.
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    private var DBG_R_CornerCullLocalBox: Int = 0

    /*
     ===============
     R_SetupProjection

     This uses the "infinite far z" trick
     ===============
     */
    private val random: idRandom? = null

    /*
     ======================
     R_ScreenRectFromViewFrustumBounds
     ======================
     */
    fun R_ScreenRectFromViewFrustumBounds(bounds: idBounds): idScreenRect {
        val screenRect: idScreenRect = idScreenRect()
        screenRect.x1 =
            FtoiFast(0.5f * (1.0f - bounds[1].y) * (tr_local.tr.viewDef!!.viewport.x2 - tr_local.tr.viewDef!!.viewport.x1))
        screenRect.x2 =
            FtoiFast(0.5f * (1.0f - bounds[0].y) * (tr_local.tr.viewDef!!.viewport.x2 - tr_local.tr.viewDef!!.viewport.x1))
        screenRect.y1 =
            FtoiFast(0.5f * (1.0f + bounds[0].z) * (tr_local.tr.viewDef!!.viewport.y2 - tr_local.tr.viewDef!!.viewport.y1))
        screenRect.y2 =
            FtoiFast(0.5f * (1.0f + bounds[1].z) * (tr_local.tr.viewDef!!.viewport.y2 - tr_local.tr.viewDef!!.viewport.y1))
        if (RenderSystem_init.r_useDepthBoundsTest!!.GetInteger() != 0) {
            val zmin: CFloat = CFloat(screenRect.zmin)
            val zmax: CFloat = CFloat(screenRect.zmax)
            R_TransformEyeZToWin(-bounds[0].x, tr_local.tr.viewDef!!.projectionMatrix, zmin)
            R_TransformEyeZToWin(-bounds[1].x, tr_local.tr.viewDef!!.projectionMatrix, zmax)
            screenRect.zmin = zmin._val
            screenRect.zmax = zmax._val
        }
        return screenRect
    }

    fun R_ShowColoredScreenRect(rect: idScreenRect, colorIndex: Int) {
        if (!rect.IsEmpty()) {
            tr_local.tr.viewDef!!.renderWorld!!.DebugScreenRect(
                colors[colorIndex and 7],
                rect,
                tr_local.tr.viewDef!!
            )
        }
    }

    /*
     ====================
     R_ToggleSmpFrame
     ====================
     */
    fun R_ToggleSmpFrame() {
        if (RenderSystem_init.r_lockSurfaces!!.GetBool()) {
            return
        }
        tr_trisurf.R_FreeDeferredTriSurfs(tr_local.frameData)

        // clear frame-temporary data
        var block: frameMemoryBlock_s?

        // update the highwater mark
        R_CountFrameData()
        val frame: frameData_t = tr_local.frameData!!

        // reset the memory allocation to the first block
        frame.alloc = frame.memory

        // clear all the blocks
        block = frame.memory
        while (block != null) {
            block.used = 0
            block = block.next
        }
        RenderSystem.R_ClearCommandChain()
    }

    /*
     =====================
     R_ShutdownFrameData
     =====================
     */
    fun R_ShutdownFrameData() {
        var block: frameMemoryBlock_s?

        // free any current data
        var frame: frameData_t? = tr_local.frameData
        if (null == frame) {
            return
        }
        tr_trisurf.R_FreeDeferredTriSurfs(frame)
        var nextBlock: frameMemoryBlock_s?
        block = frame.memory
        while (block != null) {
            nextBlock = block.next
            block = null
            block = nextBlock
        }
        frame = null
        tr_local.frameData = null
    }

    /*
     =====================
     R_InitFrameData
     =====================
     */
    fun R_InitFrameData() {
        val block: frameMemoryBlock_s?
        R_ShutdownFrameData()
        tr_local.frameData = frameData_t() // Mem_ClearedAlloc(sizeof(frameData));
        val frame: frameData_t = tr_local.frameData!!
        val size: Int = MEMORY_BLOCK_SIZE
        block = frameMemoryBlock_s() // Mem_Alloc(size /*+ sizeof( *block )*/);
        if (null == block) {
            Common.common.FatalError("R_InitFrameData: Mem_Alloc() failed")
        }
        block.size = size
        block.used = 0
        block.next = null
        frame.memory = block
        frame.memoryHighwater = 0
        R_ToggleSmpFrame()
    }

    /*
     ================
     R_CountFrameData
     ================
     */
    @Deprecated("")
    fun R_CountFrameData(): Int {
        var block: frameMemoryBlock_s?
        var count: Int = 0
        val frame: frameData_t = tr_local.frameData!!
        block = frame.memory
        while (block != null) {
            count += block.used
            if (block === frame.alloc) {
                break
            }
            block = block.next
        }

        // note if this is a new highwater mark
        if (count > frame.memoryHighwater) {
            frame.memoryHighwater = count
        }
        return count
    }

    /*
     =================
     R_StaticAlloc
     =================
     */
    @Deprecated("")
    fun R_StaticAlloc(bytes: Int): Any {
        throw UnsupportedOperationException()
        //        Object buf;
//
//        tr.pc.c_alloc++;
//
//        tr.staticAllocCount += bytes;
//
//        buf = Mem_Alloc(bytes);
//
//        // don't exit on failure on zero length allocations since the old code didn't
//        if (null == buf && (bytes != 0)) {
//            common.FatalError("R_StaticAlloc failed on %d bytes", bytes);
//        }
//        return buf;
    }

    /*
     =================
     R_StaticFree
     =================
     */
    @Deprecated("")
    fun R_StaticFree(data: Any?) {
//        tr.pc.c_free++;
//        Mem_Free(data);
        throw UnsupportedOperationException()
    }

    /*
     ================
     R_FrameAlloc

     This data will be automatically freed when the
     current frame's back end completes.

     This should only be called by the front end.  The
     back end shouldn't need to allocate memory.

     If we passed smpFrame in, the back end could
     alloc memory, because it will always be a
     different frameData than the front end is using.

     All temporary data, like dynamic tesselations
     and local spaces are allocated here.

     The memory will not move, but it may not be
     contiguous with previous allocations even
     from this frame.

     The memory is NOT zero filled.
     Should part of this be inlined in a macro?
     ================
     */
    @Deprecated("")
    fun R_FrameAlloc(bytes: Int): Any {
//        frameData_t frame;
//        frameMemoryBlock_s block;
//        Object buf;
//
//        bytes = (bytes + 16) & ~15;
//        // see if it can be satisfied in the current block
//        frame = frameData;
//        block = frame.alloc;
//
//        if (block.size - block.used >= bytes) {
//            buf = block.base + block.used;
//            block.used += bytes;
//            return buf;
//        }
//
//        // advance to the next memory block if available
//        block = block.next;
//        // create a new block if we are at the end of
//        // the chain
//        if (null == block) {
//            int size;
//
//            size = MEMORY_BLOCK_SIZE;
//            block = (frameMemoryBlock_s) Mem_Alloc(size /*+ sizeof( *block )*/);
//            if (null == block) {
//                common.FatalError("R_FrameAlloc: Mem_Alloc() failed");
//            }
//            block.size = size;
//            block.used = 0;
//            block.next = null;
//            frame.alloc.next = block;
//        }
//
//        // we could fix this if we needed to...
//        if (bytes > block.size) {
//            common.FatalError("R_FrameAlloc of %d exceeded MEMORY_BLOCK_SIZE",
//                    bytes);
//        }
//
//        frame.alloc = block;
//
//        block.used = bytes;
//
//        return block.base;
        throw UnsupportedOperationException()
    }

    /*
     ==================
     R_ClearedFrameAlloc
     ==================
     */
    @Deprecated("")
    fun R_ClearedFrameAlloc(bytes: Int): Any {
//        Object r;
//
//        r = R_FrameAlloc(bytes);
//        SIMDProcessor.Memset(r, 0, bytes);
//        return r;
        throw UnsupportedOperationException()
    }

    /*
     ==================
     R_FrameFree

     This does nothing at all, as the frame data is reused every frame
     and can only be stack allocated.

     The only reason for it's existance is so functions that can
     use either static or frame memory can set function pointers
     to both alloc and free.
     ==================
     */
    fun R_FrameFree(data: Any?) {}

    //==========================================================================
    fun R_AxisToModelMatrix(axis: idMat3, origin: idVec3, modelMatrix: FloatArray /*[16]*/) {
        modelMatrix[0] = axis[0, 0]
        modelMatrix[4] = axis[1, 0]
        modelMatrix[8] = axis[2, 0]
        modelMatrix[12] = origin[0]
        modelMatrix[1] = axis[0, 1]
        modelMatrix[5] = axis[1, 1]
        modelMatrix[9] = axis[2, 1]
        modelMatrix[13] = origin[1]
        modelMatrix[2] = axis[0, 2]
        modelMatrix[6] = axis[1, 2]
        modelMatrix[10] = axis[2, 2]
        modelMatrix[14] = origin[2]
        modelMatrix[3] = 0f
        modelMatrix[7] = 0f
        modelMatrix[11] = 0f
        modelMatrix[15] = 1f
    }

    // FIXME: these assume no skewing or scaling transforms
    fun R_LocalPointToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idVec3): idVec3 {
        val out: idVec3 = idVec3()

// if (MACOS_X && __i386__){
        // __m128 m0, m1, m2, m3;
        // __m128 in0, in1, in2;
        // float i0,i1,i2;
        // i0 = in[0];
        // i1 = in[1];
        // i2 = in[2];
        // m0 = _mm_loadu_ps(&modelMatrix[0]);
        // m1 = _mm_loadu_ps(&modelMatrix[4]);
        // m2 = _mm_loadu_ps(&modelMatrix[8]);
        // m3 = _mm_loadu_ps(&modelMatrix[12]);
        // in0 = _mm_load1_ps(&i0);
        // in1 = _mm_load1_ps(&i1);
        // in2 = _mm_load1_ps(&i2);
        // m0 = _mm_mul_ps(m0, in0);
        // m1 = _mm_mul_ps(m1, in1);
        // m2 = _mm_mul_ps(m2, in2);
        // m0 = _mm_add_ps(m0, m1);
        // m0 = _mm_add_ps(m0, m2);
        // m0 = _mm_add_ps(m0, m3);
        // _mm_store_ss(&out[0], m0);
        // m1 = (__m128) _mm_shuffle_epi32((__m128i)m0, 0x55);
        // _mm_store_ss(&out[1], m1);
        // m2 = _mm_movehl_ps(m2, m0);
        // _mm_store_ss(&out[2], m2);
// }else
        run({
            out.set(
                idVec3(
                    ((`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8]) + modelMatrix[12]),
                    ((`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9]) + modelMatrix[13]),
                    ((`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10]) + modelMatrix[14])
                )
            )
        })
        return out
    }

    fun R_PointTimesMatrix(modelMatrix: FloatArray /*[16]*/, `in`: idVec4, out: idVec4) {
        out[0] = (`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8]) + modelMatrix[12]
        out[1] = (`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9]) + modelMatrix[13]
        out[2] = (`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10]) + modelMatrix[14]
        out[3] = (`in`[0] * modelMatrix[3]) + (`in`[1] * modelMatrix[7]) + (`in`[2] * modelMatrix[11]) + modelMatrix[15]
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: idVec<*>) {
        val temp: FloatArray = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out[0] = DotProduct(temp, (modelMatrix)!!)
        out[1] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: FloatArray) {
        val temp: FloatArray = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out[0] = DotProduct(temp, (modelMatrix)!!)
        out[1] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: FloatBuffer) {
        val temp: FloatArray = FloatArray(4)
        VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out.put(0, DotProduct(temp, (modelMatrix)!!))
        out.put(1, DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.put(2, DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12)))
    }

    fun R_LocalVectorToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idVec3, out: idVec3) {
        out[0] = (`in`[0] * modelMatrix[0]) + (`in`[1] * modelMatrix[4]) + (`in`[2] * modelMatrix[8])
        out[1] = (`in`[0] * modelMatrix[1]) + (`in`[1] * modelMatrix[5]) + (`in`[2] * modelMatrix[9])
        out[2] = (`in`[0] * modelMatrix[2]) + (`in`[1] * modelMatrix[6]) + (`in`[2] * modelMatrix[10])
    }

    fun R_GlobalVectorToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3, out: idVec3) {
        out[0] = DotProduct(`in`.ToFloatPtr(), (modelMatrix)!!)
        out[1] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPlaneToLocal(modelMatrix: FloatArray /*[16]*/, `in`: idPlane, out: idPlane) {
        out[0] = DotProduct(`in`.ToFloatPtr(), modelMatrix)
        out[1] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8))
        out[2] = DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12))
        out[3] = `in`[3] + (modelMatrix[12] * `in`[0]) + (modelMatrix[13] * `in`[1]) + (modelMatrix[14] * `in`[2])
    }

    fun R_LocalPlaneToGlobal(modelMatrix: FloatArray /*[16]*/, `in`: idPlane, out: idPlane) {
        R_LocalVectorToGlobal(modelMatrix, `in`.Normal(), out.Normal())
        val offset: Float = (modelMatrix[12] * out[0]) + (modelMatrix[13] * out[1]) + (modelMatrix[14] * out[2])
        out[3] = `in`[3] - offset
    }

    // transform Z in eye coordinates to window coordinates
    fun R_TransformEyeZToWin(src_z: Float, projectionMatrix: FloatArray, dst_z: CFloat) {

        // projection
        val clip_z: Float = src_z * projectionMatrix[2 + 2 * 4] + projectionMatrix[2 + 3 * 4]
        val clip_w: Float = src_z * projectionMatrix[3 + 2 * 4] + projectionMatrix[3 + 3 * 4]
        if (clip_w <= 0.0f) {
            dst_z._val = 0.0f // clamp to near plane
        } else {
            dst_z._val = clip_z / clip_w
            dst_z._val = dst_z._val * 0.5f + 0.5f // convert to window coords
        }
    }

    /*
     =================
     R_RadiusCullLocalBox

     A fast, conservative center-to-corner culling test
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    fun R_RadiusCullLocalBox(
        bounds: idBounds,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane>
    ): Boolean {
        var d: Float
        val worldOrigin: idVec3 = idVec3()
        val worldRadius: Float
        var frust: idPlane
        if (RenderSystem_init.r_useCulling!!.GetInteger() == 0) {
            return false
        }

        // transform the surface bounds into world space
        val localOrigin: idVec3 = idVec3((bounds[0].plus(bounds[1])).times(0.5f))
        worldOrigin.set(R_LocalPointToGlobal(modelMatrix!!, localOrigin))
        worldRadius = (bounds[0].minus(localOrigin)).Length() // FIXME: won't be correct for scaled objects
        var i: Int = 0
        while (i < numPlanes) {
            frust = planes[i]
            d = frust.Distance(worldOrigin)
            if (d > worldRadius) {
                return true // culled
            }
            i++
        }
        return false // not culled
    }

    fun R_CornerCullLocalBox(
        bounds: idBounds,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane>
    ): Boolean {
        var j: Int
        val transformed: Array<idVec3> = idVec3.generateArray(8)
        val dists: FloatArray = FloatArray(8)
        val v: idVec3 = idVec3()
        var frust: idPlane
        DBG_R_CornerCullLocalBox++

        // we can disable box culling for experimental timing purposes
        if (RenderSystem_init.r_useCulling!!.GetInteger() < 2) {
            return false
        }

        // transform into world space
        var i: Int = 0
        while (i < 8) {
            v[0] = bounds[(i shr 0) and 1, 0]
            v[1] = bounds[(i shr 1) and 1, 1]
            v[2] = bounds[(i shr 2) and 1, 2]
            transformed[i].set(R_LocalPointToGlobal(modelMatrix!!, v))
            i++
        }

        // check against frustum planes
        i = 0
        while (i < numPlanes) {
            frust = planes[i]
            j = 0
            while (j < 8) {
                dists[j] = frust.Distance(transformed[j])
                if (dists[j] < 0) {
                    break
                }
                j++
            }
            if (j == 8) {
//                System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
//                System.out.println(">>>>>>>>>>> " + Arrays.toString(transformed));
//                System.out.println(">>>>>>>>>>> " + Arrays.toString(dists));
//                System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
                // all points were behind one of the planes
                tr_local.tr.pc!!.c_box_cull_out++
                return true
            }
            i++
        }
        //        System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
//        System.out.println(">>>>>>>>>>> " + Arrays.toString(transformed));
//        System.out.println(">>>>>>>>>>> " + Arrays.toString(dists));
//        System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
        tr_local.tr.pc!!.c_box_cull_in++
        return false // not culled
    }

    /*
     =================
     R_CullLocalBox

     Performs quick test before expensive test
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    fun R_CullLocalBox(
        bounds: idBounds?,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane?>?
    ): Boolean {
        if (R_RadiusCullLocalBox(bounds!!, modelMatrix, numPlanes, planes as Array<idPlane>)) {
            return true
        }
        return R_CornerCullLocalBox(bounds, modelMatrix, numPlanes, planes as Array<idPlane>)
    }

    /*
     ==========================
     R_TransformModelToClip
     ==========================
     */
    fun R_TransformModelToClip(
        src: idVec3,
        modelMatrix: FloatArray,
        projectionMatrix: FloatArray,
        eye: idPlane,
        dst: idPlane
    ) {
        var i: Int = 0
        while (i < 4) {
            eye[i] = (src[0] * modelMatrix[i + 0 * 4]
                    ) + (src[1] * modelMatrix[i + 1 * 4]
                    ) + (src[2] * modelMatrix[i + 2 * 4]
                    ) + (1 * modelMatrix[i + 3 * 4])
            i++
        }
        i = 0
        while (i < 4) {
            dst[i] = (eye[0] * projectionMatrix[i + 0 * 4]
                    ) + (eye[1] * projectionMatrix[i + 1 * 4]
                    ) + (eye[2] * projectionMatrix[i + 2 * 4]
                    ) + (eye[3] * projectionMatrix[i + 3 * 4])
            i++
        }
    }

    /*
     ==========================
     R_GlobalToNormalizedDeviceCoordinates

     -1 to 1 range in x, y, and z
     ==========================
     */
    fun R_GlobalToNormalizedDeviceCoordinates(global: idVec3, ndc: idVec3) {
        var i: Int
        val view: idPlane = idPlane()
        val clip: idPlane = idPlane()

        // _D3XP added work on primaryView when no viewDef
        if (null == tr_local.tr.viewDef) {
            i = 0
            while (i < 4) {
                view[i] = (global[0] * tr_local.tr.primaryView!!.worldSpace.modelViewMatrix[i + 0 * 4]
                        ) + (global[1] * tr_local.tr.primaryView!!.worldSpace.modelViewMatrix[i + 1 * 4]
                        ) + (global[2] * tr_local.tr.primaryView!!.worldSpace.modelViewMatrix[i + 2 * 4]
                        ) + tr_local.tr.primaryView!!.worldSpace.modelViewMatrix[i + 3 * 4]
                i++
            }
            i = 0
            while (i < 4) {
                clip[i] = (view[0] * tr_local.tr.primaryView!!.projectionMatrix[i + 0 * 4]
                        ) + (view[1] * tr_local.tr.primaryView!!.projectionMatrix[i + 1 * 4]
                        ) + (view[2] * tr_local.tr.primaryView!!.projectionMatrix[i + 2 * 4]
                        ) + (view[3] * tr_local.tr.primaryView!!.projectionMatrix[i + 3 * 4])
                i++
            }
        } else {
            i = 0
            while (i < 4) {
                view[i] = (global[0] * tr_local.tr.viewDef!!.worldSpace.modelViewMatrix[i + 0 * 4]
                        ) + (global[1] * tr_local.tr.viewDef!!.worldSpace.modelViewMatrix[i + 1 * 4]
                        ) + (global[2] * tr_local.tr.viewDef!!.worldSpace.modelViewMatrix[i + 2 * 4]
                        ) + tr_local.tr.viewDef!!.worldSpace.modelViewMatrix[i + 3 * 4]
                i++
            }
            i = 0
            while (i < 4) {
                clip[i] = (view[0] * tr_local.tr.viewDef!!.projectionMatrix[i + 0 * 4]
                        ) + (view[1] * tr_local.tr.viewDef!!.projectionMatrix[i + 1 * 4]
                        ) + (view[2] * tr_local.tr.viewDef!!.projectionMatrix[i + 2 * 4]
                        ) + (view[3] * tr_local.tr.viewDef!!.projectionMatrix[i + 3 * 4])
                i++
            }
        }
        ndc[0] = clip[0] / clip[3]
        ndc[1] = clip[1] / clip[3]
        ndc[2] = (clip[2] + clip[3]) / (2 * clip[3])
    }

    /*
     ==========================
     R_TransformClipToDevice

     Clip to normalized device coordinates
     ==========================
     */
    fun R_TransformClipToDevice(clip: idPlane, view: viewDef_s?, normalized: idVec3) {
        normalized[0] = clip[0] / clip[3]
        normalized[1] = clip[1] / clip[3]
        normalized[2] = clip[2] / clip[3]
    }

    /*
     ==========================
     myGlMultMatrix
     ==========================
     */
    fun myGlMultMatrix(a: FloatArray /*[16]*/, b: FloatArray /*[16]*/, out: FloatArray /*[16]*/) {
        if (false) {
//            int i, j;
//
//            for (i = 0; i < 4; i++) {
//                for (j = 0; j < 4; j++) {
//                    out[ i * 4 + j] =
//                            a[ i * 4 + 0] * b[ 0 * 4 + j]
//                            + a[ i * 4 + 1] * b[ 1 * 4 + j]
//                            + a[ i * 4 + 2] * b[ 2 * 4 + j]
//                            + a[ i * 4 + 3] * b[ 3 * 4 + j];
//                }
//            }
        } else {
            out[0 * 4 + 0] =
                (a[0 * 4 + 0] * b[0 * 4 + 0]) + (a[0 * 4 + 1] * b[1 * 4 + 0]) + (a[0 * 4 + 2] * b[2 * 4 + 0]) + (a[0 * 4 + 3] * b[3 * 4 + 0])
            out[0 * 4 + 1] =
                (a[0 * 4 + 0] * b[0 * 4 + 1]) + (a[0 * 4 + 1] * b[1 * 4 + 1]) + (a[0 * 4 + 2] * b[2 * 4 + 1]) + (a[0 * 4 + 3] * b[3 * 4 + 1])
            out[0 * 4 + 2] =
                (a[0 * 4 + 0] * b[0 * 4 + 2]) + (a[0 * 4 + 1] * b[1 * 4 + 2]) + (a[0 * 4 + 2] * b[2 * 4 + 2]) + (a[0 * 4 + 3] * b[3 * 4 + 2])
            out[0 * 4 + 3] =
                (a[0 * 4 + 0] * b[0 * 4 + 3]) + (a[0 * 4 + 1] * b[1 * 4 + 3]) + (a[0 * 4 + 2] * b[2 * 4 + 3]) + (a[0 * 4 + 3] * b[3 * 4 + 3])
            out[1 * 4 + 0] =
                (a[1 * 4 + 0] * b[0 * 4 + 0]) + (a[1 * 4 + 1] * b[1 * 4 + 0]) + (a[1 * 4 + 2] * b[2 * 4 + 0]) + (a[1 * 4 + 3] * b[3 * 4 + 0])
            out[1 * 4 + 1] =
                (a[1 * 4 + 0] * b[0 * 4 + 1]) + (a[1 * 4 + 1] * b[1 * 4 + 1]) + (a[1 * 4 + 2] * b[2 * 4 + 1]) + (a[1 * 4 + 3] * b[3 * 4 + 1])
            out[1 * 4 + 2] =
                (a[1 * 4 + 0] * b[0 * 4 + 2]) + (a[1 * 4 + 1] * b[1 * 4 + 2]) + (a[1 * 4 + 2] * b[2 * 4 + 2]) + (a[1 * 4 + 3] * b[3 * 4 + 2])
            out[1 * 4 + 3] =
                (a[1 * 4 + 0] * b[0 * 4 + 3]) + (a[1 * 4 + 1] * b[1 * 4 + 3]) + (a[1 * 4 + 2] * b[2 * 4 + 3]) + (a[1 * 4 + 3] * b[3 * 4 + 3])
            out[2 * 4 + 0] =
                (a[2 * 4 + 0] * b[0 * 4 + 0]) + (a[2 * 4 + 1] * b[1 * 4 + 0]) + (a[2 * 4 + 2] * b[2 * 4 + 0]) + (a[2 * 4 + 3] * b[3 * 4 + 0])
            out[2 * 4 + 1] =
                (a[2 * 4 + 0] * b[0 * 4 + 1]) + (a[2 * 4 + 1] * b[1 * 4 + 1]) + (a[2 * 4 + 2] * b[2 * 4 + 1]) + (a[2 * 4 + 3] * b[3 * 4 + 1])
            out[2 * 4 + 2] =
                (a[2 * 4 + 0] * b[0 * 4 + 2]) + (a[2 * 4 + 1] * b[1 * 4 + 2]) + (a[2 * 4 + 2] * b[2 * 4 + 2]) + (a[2 * 4 + 3] * b[3 * 4 + 2])
            out[2 * 4 + 3] =
                (a[2 * 4 + 0] * b[0 * 4 + 3]) + (a[2 * 4 + 1] * b[1 * 4 + 3]) + (a[2 * 4 + 2] * b[2 * 4 + 3]) + (a[2 * 4 + 3] * b[3 * 4 + 3])
            out[3 * 4 + 0] =
                (a[3 * 4 + 0] * b[0 * 4 + 0]) + (a[3 * 4 + 1] * b[1 * 4 + 0]) + (a[3 * 4 + 2] * b[2 * 4 + 0]) + (a[3 * 4 + 3] * b[3 * 4 + 0])
            out[3 * 4 + 1] =
                (a[3 * 4 + 0] * b[0 * 4 + 1]) + (a[3 * 4 + 1] * b[1 * 4 + 1]) + (a[3 * 4 + 2] * b[2 * 4 + 1]) + (a[3 * 4 + 3] * b[3 * 4 + 1])
            out[3 * 4 + 2] =
                (a[3 * 4 + 0] * b[0 * 4 + 2]) + (a[3 * 4 + 1] * b[1 * 4 + 2]) + (a[3 * 4 + 2] * b[2 * 4 + 2]) + (a[3 * 4 + 3] * b[3 * 4 + 2])
            out[3 * 4 + 3] =
                (a[3 * 4 + 0] * b[0 * 4 + 3]) + (a[3 * 4 + 1] * b[1 * 4 + 3]) + (a[3 * 4 + 2] * b[2 * 4 + 3]) + (a[3 * 4 + 3] * b[3 * 4 + 3])
        }
    }

    /*
     ================
     R_TransposeGLMatrix
     ================
     */
    fun R_TransposeGLMatrix(`in`: FloatArray /*[16]*/, out: FloatArray /*[16]*/) {
        var j: Int
        var i: Int = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                out[i * 4 + j] = `in`[j * 4 + i]
                j++
            }
            i++
        }
    }

    fun R_SetViewMatrix(viewDef: viewDef_s) {
        val origin: idVec3 = idVec3()
        val viewerMatrix: FloatArray = FloatArray(16)
        viewDef!!.worldSpace = viewEntity_s()
        val world: viewEntity_s = viewDef!!.worldSpace //memset(world, 0, sizeof(world));

        // the model matrix is an identity
        world.modelMatrix[0 * 4 + 0] = 1f
        world.modelMatrix[1 * 4 + 1] = 1f
        world.modelMatrix[2 * 4 + 2] = 1f

        // transform by the camera placement
        origin.set(viewDef!!.renderView.vieworg)
        viewerMatrix[0] = viewDef!!.renderView.viewaxis[0, 0]
        viewerMatrix[4] = viewDef!!.renderView.viewaxis[0, 1]
        viewerMatrix[8] = viewDef!!.renderView.viewaxis[0, 2]
        viewerMatrix[12] =
            (-origin[0] * viewerMatrix[0]) + (-origin[1] * viewerMatrix[4]) + (-origin[2] * viewerMatrix[8])
        viewerMatrix[1] = viewDef!!.renderView.viewaxis[1, 0]
        viewerMatrix[5] = viewDef!!.renderView.viewaxis[1, 1]
        viewerMatrix[9] = viewDef!!.renderView.viewaxis[1, 2]
        viewerMatrix[13] =
            (-origin[0] * viewerMatrix[1]) + (-origin[1] * viewerMatrix[5]) + (-origin[2] * viewerMatrix[9])
        viewerMatrix[2] = viewDef!!.renderView.viewaxis[2, 0]
        viewerMatrix[6] = viewDef!!.renderView.viewaxis[2, 1]
        viewerMatrix[10] = viewDef!!.renderView.viewaxis[2, 2]
        viewerMatrix[14] =
            (-origin[0] * viewerMatrix[2]) + (-origin[1] * viewerMatrix[6]) + (-origin[2] * viewerMatrix[10])
        viewerMatrix[3] = 0f
        viewerMatrix[7] = 0f
        viewerMatrix[11] = 0f
        viewerMatrix[15] = 1f

        // convert from our coordinate system (looking down X)
        // to OpenGL's coordinate system (looking down -Z)
        myGlMultMatrix(viewerMatrix, s_flipMatrix, world.modelViewMatrix)
    }

    fun R_SetupProjection() {
        var xmin: Float
        var xmax: Float
        var ymin: Float
        var ymax: Float
        var jitterx: Float
        var jittery: Float

        // random jittering is usefull when multiple
        // frames are going to be blended together
        // for motion blurred anti-aliasing
        if (RenderSystem_init.r_jitter!!.GetBool()) {
            jitterx = random!!.RandomFloat()
            jittery = random!!.RandomFloat()
        } else {
            jittery = 0f
            jitterx = jittery
        }

        //
        // set up projection matrix
        //
        var zNear: Float = RenderSystem_init.r_znear!!.GetFloat()
        if (tr_local.tr.viewDef!!.renderView.cramZNear) {
            zNear *= 0.25.toFloat()
        }
        ymax = (zNear * tan((tr_local.tr.viewDef!!.renderView.fov_y * idMath.PI / 360.0f).toDouble())).toFloat()
        ymin = -ymax
        xmax = (zNear * tan((tr_local.tr.viewDef!!.renderView.fov_x * idMath.PI / 360.0f).toDouble())).toFloat()
        xmin = -xmax
        val width: Float = xmax - xmin
        val height: Float = ymax - ymin
        jitterx = jitterx * width / (tr_local.tr.viewDef!!.viewport.x2 - tr_local.tr.viewDef!!.viewport.x1 + 1)
        xmin += jitterx
        xmax += jitterx
        jittery = jittery * height / (tr_local.tr.viewDef!!.viewport.y2 - tr_local.tr.viewDef!!.viewport.y1 + 1)
        ymin += jittery
        ymax += jittery
        tr_local.tr.viewDef!!.projectionMatrix[0] = 2 * zNear / width
        tr_local.tr.viewDef!!.projectionMatrix[4] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[8] = (xmax + xmin) / width // normally 0
        tr_local.tr.viewDef!!.projectionMatrix[12] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[1] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[5] = 2 * zNear / height
        tr_local.tr.viewDef!!.projectionMatrix[9] = (ymax + ymin) / height // normally 0
        tr_local.tr.viewDef!!.projectionMatrix[13] = 0f

        // this is the far-plane-at-infinity formulation, and
        // crunches the Z range slightly so w=0 vertexes do not
        // rasterize right at the wraparound point
        tr_local.tr.viewDef!!.projectionMatrix[2] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[6] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[10] = -0.999f
        tr_local.tr.viewDef!!.projectionMatrix[14] = -2.0f * zNear
        tr_local.tr.viewDef!!.projectionMatrix[3] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[7] = 0f
        tr_local.tr.viewDef!!.projectionMatrix[11] = -1f
        tr_local.tr.viewDef!!.projectionMatrix[15] = 0f
    }

    /*
     =================
     R_SetupViewFrustum

     Setup that culling frustum planes for the current view
     FIXME: derive from modelview matrix times projection matrix
     =================
     */
    fun R_SetupViewFrustum() {
        val xs: CFloat = CFloat(0.0f)
        val xc: CFloat = CFloat(0.0f)
        var ang: Float = DEG2RAD(tr_local.tr.viewDef!!.renderView.fov_x) * 0.5f
        SinCos(ang, xs, xc)
        tr_local.tr.viewDef!!.frustum[0].set(
            tr_local.tr.viewDef!!.renderView.viewaxis[0].times(xs._val)
                .plus(tr_local.tr.viewDef!!.renderView.viewaxis[1].times(xc._val))
        )
        tr_local.tr.viewDef!!.frustum[1].set(
            tr_local.tr.viewDef!!.renderView.viewaxis[0].times(xs._val)
                .minus(tr_local.tr.viewDef!!.renderView.viewaxis[1].times(xc._val))
        )
        ang = DEG2RAD(tr_local.tr.viewDef!!.renderView.fov_y) * 0.5f
        SinCos(ang, xs, xc)
        tr_local.tr.viewDef!!.frustum[2].set(
            tr_local.tr.viewDef!!.renderView.viewaxis[0].times(xs._val)
                .plus(tr_local.tr.viewDef!!.renderView.viewaxis[2].times(xc._val))
        )
        tr_local.tr.viewDef!!.frustum[3].set(
            tr_local.tr.viewDef!!.renderView.viewaxis[0].times(xs._val)
                .minus(tr_local.tr.viewDef!!.renderView.viewaxis[2].times(xc._val))
        )

        // plane four is the front clipping plane
        tr_local.tr.viewDef!!.frustum[4].set( /* vec3_origin - */tr_local.tr.viewDef!!.renderView.viewaxis[0])
        var i: Int = 0
        while (i < 5) {

            // flip direction so positive side faces out (FIXME: globally unify this)
            tr_local.tr.viewDef!!.frustum[i].set(tr_local.tr.viewDef!!.frustum[i].Normal().unaryMinus())
            tr_local.tr.viewDef!!.frustum[i][3] =
                -(tr_local.tr.viewDef!!.renderView.vieworg.times(tr_local.tr.viewDef!!.frustum[i].Normal()))
            i++
        }

        // eventually, plane five will be the rear clipping plane for fog
        var dNear: Float = RenderSystem_init.r_znear!!.GetFloat()
        if (tr_local.tr.viewDef!!.renderView.cramZNear) {
            dNear *= 0.25f
        }
        val dFar: Float = Lib.MAX_WORLD_SIZE.toFloat()
        val dLeft: Float = (dFar * tan(DEG2RAD(tr_local.tr.viewDef!!.renderView.fov_x * 0.5f).toDouble())).toFloat()
        val dUp: Float = (dFar * tan(DEG2RAD(tr_local.tr.viewDef!!.renderView.fov_y * 0.5f).toDouble())).toFloat()
        tr_local.tr.viewDef!!.viewFrustum.SetOrigin(tr_local.tr.viewDef!!.renderView.vieworg)
        tr_local.tr.viewDef!!.viewFrustum.SetAxis(tr_local.tr.viewDef!!.renderView.viewaxis)
        tr_local.tr.viewDef!!.viewFrustum.SetSize(dNear, dFar, dLeft, dUp)
    }

    /*
     ===================
     R_ConstrainViewFrustum
     ===================
     */
    fun R_ConstrainViewFrustum() {
        val bounds: idBounds = idBounds()

        // constrain the view frustum to the total bounds of all visible lights and visible entities
        bounds.Clear()
        var vLight: tr_local.viewLight_s? = tr_local.tr.viewDef!!.viewLights
        while (vLight != null) {
            bounds.AddBounds(vLight.lightDef!!.frustumTris!!.bounds)
            vLight = vLight.next
        }
        var vEntity: viewEntity_s? = tr_local.tr.viewDef!!.viewEntitys
        while (vEntity != null) {
            bounds.AddBounds(vEntity.entityDef!!.referenceBounds)
            vEntity = vEntity.next
        }
        tr_local.tr.viewDef!!.viewFrustum.ConstrainToBounds(bounds)
        if (RenderSystem_init.r_useFrustumFarDistance!!.GetFloat() > 0.0f) {
            tr_local.tr.viewDef!!.viewFrustum.MoveFarDistance(RenderSystem_init.r_useFrustumFarDistance!!.GetFloat())
        }
    }

    /*
     =================
     R_SortDrawSurfs
     =================
     */
    fun R_SortDrawSurfs() {
        // sort the drawsurfs by sort type, then orientation, then shader
//        qsort(tr.viewDef!!.drawSurfs, tr.viewDef!!.numDrawSurfs, sizeof(tr.viewDef!!.drawSurfs[0]), R_QsortSurfaces);
        if (tr_local.tr.viewDef!!.drawSurfs != null) {
            Arrays.sort(tr_local.tr.viewDef!!.drawSurfs, 0, tr_local.tr.viewDef!!.numDrawSurfs, R_QsortSurfaces())
            //            int bla = 0;
//            for (int i = 0; i < tr.viewDef!!.numDrawSurfs; i++) {
//                Material.shaderStage_t[] stages = tr.viewDef!!.drawSurfs[i].material.stages;
//                if (stages != null && stages[0].texture.image[0] != null &&
//                        stages[0].texture.image[0].imgName.toString().contains("env/cloudy")) {
//                    tr.viewDef!!.drawSurfs[bla++] = tr.viewDef!!.drawSurfs[i];
//                    System.out.println(stages[0].texture.image[0].imgName);
//                }
//            }
//            tr.viewDef!!.numDrawSurfs = bla;
//
//            final int from = 61;
//            final int to = Math.min(tr.viewDef!!.numDrawSurfs - from, from + 1);
//            tr.viewDef!!.drawSurfs = Arrays.copyOfRange(tr.viewDef!!.drawSurfs, from, to);
//            tr.viewDef!!.numDrawSurfs = to - from;
//           tr.viewDef!!.drasawwwwwwwwwwwwwwwwwwwwwwwwwwwwaw a    wSurfs[0].geo.indexes = null;
        }
    }

    //========================================================================
    //    
    //    
    //==============================================================================
    fun R_RenderView(parms: viewDef_s) {
        val oldView: viewDef_s?
        DEBUG_R_RenderView++
        if (parms.renderView.width <= 0 || parms.renderView.height <= 0) {
            return
        }
        tr_local.tr.viewCount++
        //        System.out.println("tr.viewCount::R_RenderView");

        // save view in case we are a subview
        oldView = tr_local.tr.viewDef
        tr_local.tr.viewDef = parms
        tr_local.tr.sortOffset = 0f

        // set the matrix for world space to eye space
        R_SetViewMatrix(tr_local.tr.viewDef!!)

        // the four sides of the view frustum are needed
        // for culling and portal visibility
        R_SetupViewFrustum()

        // we need to set the projection matrix before doing
        // portal-to-screen scissor box calculations
        R_SetupProjection()

        // identify all the visible portalAreas, and the entityDefs and
        // lightDefs that are in them and pass culling.
//	static_cast<idRenderWorldLocal *>(parms.renderWorld).FindViewLightsAndEntities();
        parms.renderWorld!!.FindViewLightsAndEntities()

        // constrain the view frustum to the view lights and entities
        R_ConstrainViewFrustum()

        // make sure that interactions exist for all light / entity combinations
        // that are visible
        // add any pre-generated light shadows, and calculate the light shader values
        tr_light.R_AddLightSurfaces()

        // adds ambient surfaces and create any necessary interaction surfaces to add to the light
        // lists
        tr_light.R_AddModelSurfaces()

        // any viewLight that didn't have visible surfaces can have it's shadows removed
        tr_light.R_RemoveUnecessaryViewLights()

        // sort all the ambient surfaces for translucency ordering
        R_SortDrawSurfs()

        // generate any subviews (mirrors, cameras, etc) before adding this view
        if (tr_subview.R_GenerateSubViews()) {
            // if we are debugging subviews, allow the skipping of the
            // main view draw
            if (RenderSystem_init.r_subviewOnly!!.GetBool()) {
                return
            }
        }

        // write everything needed to the demo file
        if (Session.session.writeDemo != null) {
//		static_cast<idRenderWorldLocal *>(parms.renderWorld)->WriteVisibleDefs( tr.viewDef );
            parms.renderWorld!!.WriteVisibleDefs(tr_local.tr.viewDef!!)
        }

        // add the rendering commands for this viewDef
        RenderSystem.R_AddDrawViewCmd(parms)

        // restore view in case we are a subview
        tr_local.tr.viewDef = oldView
    }

    /*
     ==========================================================================================

     DRAWSURF SORTING

     ==========================================================================================
     */
    /*
     =======================
     R_QsortSurfaces

     =======================
     */
    class R_QsortSurfaces() : cmp_t<drawSurf_s?> {
        public override fun compare(a: drawSurf_s?, b: drawSurf_s?): Int {

            //this check assumes that the array contains nothing but nulls from this point.
            if (null == a && null == b) {
                return 0
            }
            if (null == b || (null != a && a.sort < b.sort)) {
                return -1
            }
            if (null == a || (null != b && a.sort > b.sort)) {
                return 1
            }
            return 0
        }
    }
}
