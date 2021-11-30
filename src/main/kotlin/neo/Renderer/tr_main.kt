package neo.Renderer

import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_local.frameData_t
import neo.Renderer.tr_local.frameMemoryBlock_s
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_local.viewEntity_s
import neo.framework.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Random.idRandom
import neo.idlib.math.Vector.idVec
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.FloatBuffer
import java.util.*

/**
 *
 */
object tr_main {
    //====================================================================
    //=====================================================
    const val MEMORY_BLOCK_SIZE = 0x100000

    /*
     ======================
     R_ShowColoredScreenRect
     ======================
     */
    val colors /*[]*/: Array<idVec4?>? = arrayOf(
        Lib.Companion.colorRed,
        Lib.Companion.colorGreen,
        Lib.Companion.colorBlue,
        Lib.Companion.colorYellow,
        Lib.Companion.colorMagenta,
        Lib.Companion.colorCyan,
        Lib.Companion.colorWhite,
        Lib.Companion.colorPurple
    )

    /*
     =================
     R_SetViewMatrix

     Sets up the world to view matrix for a given viewParm
     =================
     */
    private val s_flipMatrix /*[16]*/: FloatArray? =
        floatArrayOf(-0f, 0f, -1f, 0f, -1f, 0f, -0f, 0f, -0f, 1f, -0f, 0f, -0f, 0f, -0f, 1f)

    /*
     ================
     R_RenderView

     A view may be either the actual camera view,
     a mirror / remote location, or a 3D view on a gui surface.

     Parms will typically be allocated with R_FrameAlloc
     ================
     */
    var DEBUG_R_RenderView = 0

    /*
     =================
     R_CornerCullLocalBox

     Tests all corners against the frustum.
     Can still generate a few false positives when the box is outside a corner.
     Returns true if the box is outside the given global frustum, (positive sides are out)
     =================
     */
    private const val DBG_R_CornerCullLocalBox = 0

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
    fun R_ScreenRectFromViewFrustumBounds(bounds: idBounds?): idScreenRect? {
        val screenRect = idScreenRect()
        screenRect.x1 =
            idMath.FtoiFast(0.5f * (1.0f - bounds.get(1).y) * (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1))
        screenRect.x2 =
            idMath.FtoiFast(0.5f * (1.0f - bounds.get(0).y) * (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1))
        screenRect.y1 =
            idMath.FtoiFast(0.5f * (1.0f + bounds.get(0).z) * (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1))
        screenRect.y2 =
            idMath.FtoiFast(0.5f * (1.0f + bounds.get(1).z) * (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1))
        if (RenderSystem_init.r_useDepthBoundsTest.GetInteger() != 0) {
            val zmin = CFloat(screenRect.zmin)
            val zmax = CFloat(screenRect.zmax)
            tr_main.R_TransformEyeZToWin(-bounds.get(0).x, tr_local.tr.viewDef.projectionMatrix, zmin)
            tr_main.R_TransformEyeZToWin(-bounds.get(1).x, tr_local.tr.viewDef.projectionMatrix, zmax)
            screenRect.zmin = zmin.getVal()
            screenRect.zmax = zmax.getVal()
        }
        return screenRect
    }

    fun R_ShowColoredScreenRect(rect: idScreenRect?, colorIndex: Int) {
        if (!rect.IsEmpty()) {
            tr_local.tr.viewDef.renderWorld.DebugScreenRect(tr_main.colors[colorIndex and 7], rect, tr_local.tr.viewDef)
        }
    }

    /*
     ====================
     R_ToggleSmpFrame
     ====================
     */
    fun R_ToggleSmpFrame() {
        if (RenderSystem_init.r_lockSurfaces.GetBool()) {
            return
        }
        tr_trisurf.R_FreeDeferredTriSurfs(tr_local.frameData)

        // clear frame-temporary data
        val frame: frameData_t?
        var block: frameMemoryBlock_s?

        // update the highwater mark
        tr_main.R_CountFrameData()
        frame = tr_local.frameData

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
        var frame: frameData_t?
        var block: frameMemoryBlock_s?

        // free any current data
        frame = tr_local.frameData
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
        val size: Int
        val frame: frameData_t?
        val block: frameMemoryBlock_s
        tr_main.R_ShutdownFrameData()
        tr_local.frameData = frameData_t() // Mem_ClearedAlloc(sizeof(frameData));
        frame = tr_local.frameData
        size = tr_main.MEMORY_BLOCK_SIZE
        block = frameMemoryBlock_s() // Mem_Alloc(size /*+ sizeof( *block )*/);
        if (null == block) {
            Common.common.FatalError("R_InitFrameData: Mem_Alloc() failed")
        }
        block.size = size
        block.used = 0
        block.next = null
        frame.memory = block
        frame.memoryHighwater = 0
        tr_main.R_ToggleSmpFrame()
    }

    /*
     ================
     R_CountFrameData
     ================
     */
    @Deprecated("")
    fun R_CountFrameData(): Int {
        val frame: frameData_t?
        var block: frameMemoryBlock_s?
        var count: Int
        count = 0
        frame = tr_local.frameData
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
    fun R_StaticAlloc(bytes: Int): Any? {
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
    fun R_FrameAlloc(bytes: Int): Any? {
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
    fun R_ClearedFrameAlloc(bytes: Int): Any? {
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
    fun R_AxisToModelMatrix(axis: idMat3?, origin: idVec3?, modelMatrix: FloatArray? /*[16]*/) {
        modelMatrix.get(0) = axis.get(0, 0)
        modelMatrix.get(4) = axis.get(1, 0)
        modelMatrix.get(8) = axis.get(2, 0)
        modelMatrix.get(12) = origin.get(0)
        modelMatrix.get(1) = axis.get(0, 1)
        modelMatrix.get(5) = axis.get(1, 1)
        modelMatrix.get(9) = axis.get(2, 1)
        modelMatrix.get(13) = origin.get(1)
        modelMatrix.get(2) = axis.get(0, 2)
        modelMatrix.get(6) = axis.get(1, 2)
        modelMatrix.get(10) = axis.get(2, 2)
        modelMatrix.get(14) = origin.get(2)
        modelMatrix.get(3) = 0
        modelMatrix.get(7) = 0
        modelMatrix.get(11) = 0
        modelMatrix.get(15) = 1
    }

    // FIXME: these assume no skewing or scaling transforms
    fun R_LocalPointToGlobal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?): idVec3? {
        val out = idVec3()

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
        run {
            out.set(
                idVec3(
                    `in`.get(0) * modelMatrix.get(0) + `in`.get(1) * modelMatrix.get(4) + `in`.get(2) * modelMatrix.get(
                        8
                    ) + modelMatrix.get(12),
                    `in`.get(0) * modelMatrix.get(1) + `in`.get(1) * modelMatrix.get(5) + `in`.get(2) * modelMatrix.get(
                        9
                    ) + modelMatrix.get(13),
                    `in`.get(0) * modelMatrix.get(2) + `in`.get(1) * modelMatrix.get(6) + `in`.get(2) * modelMatrix.get(
                        10
                    ) + modelMatrix.get(14)
                )
            )
        }
        return out
    }

    fun R_PointTimesMatrix(modelMatrix: FloatArray? /*[16]*/, `in`: idVec4?, out: idVec4?) {
        out.set(
            0,
            `in`.get(0) * modelMatrix.get(0) + `in`.get(1) * modelMatrix.get(4) + `in`.get(2) * modelMatrix.get(8) + modelMatrix.get(
                12
            )
        )
        out.set(
            1,
            `in`.get(0) * modelMatrix.get(1) + `in`.get(1) * modelMatrix.get(5) + `in`.get(2) * modelMatrix.get(9) + modelMatrix.get(
                13
            )
        )
        out.set(
            2,
            `in`.get(0) * modelMatrix.get(2) + `in`.get(1) * modelMatrix.get(6) + `in`.get(2) * modelMatrix.get(10) + modelMatrix.get(
                14
            )
        )
        out.set(
            3,
            `in`.get(0) * modelMatrix.get(3) + `in`.get(1) * modelMatrix.get(7) + `in`.get(2) * modelMatrix.get(11) + modelMatrix.get(
                15
            )
        )
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?, out: idVec<*>?) {
        val temp = FloatArray(4)
        Vector.VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out.set(0, Vector.DotProduct(temp, modelMatrix))
        out.set(1, Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.set(2, Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12)))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?, out: FloatArray?) {
        val temp = FloatArray(4)
        Vector.VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out.get(0) = Vector.DotProduct(temp, modelMatrix)
        out.get(1) = Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8))
        out.get(2) = Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12))
    }

    fun R_GlobalPointToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?, out: FloatBuffer?) {
        val temp = FloatArray(4)
        Vector.VectorSubtract(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 12, 16), temp)
        out.put(0, Vector.DotProduct(temp, modelMatrix))
        out.put(1, Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.put(2, Vector.DotProduct(temp, Arrays.copyOfRange(modelMatrix, 8, 12)))
    }

    fun R_LocalVectorToGlobal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?, out: idVec3?) {
        out.set(
            0,
            `in`.get(0) * modelMatrix.get(0) + `in`.get(1) * modelMatrix.get(4) + `in`.get(2) * modelMatrix.get(8)
        )
        out.set(
            1,
            `in`.get(0) * modelMatrix.get(1) + `in`.get(1) * modelMatrix.get(5) + `in`.get(2) * modelMatrix.get(9)
        )
        out.set(
            2,
            `in`.get(0) * modelMatrix.get(2) + `in`.get(1) * modelMatrix.get(6) + `in`.get(2) * modelMatrix.get(10)
        )
    }

    fun R_GlobalVectorToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idVec3?, out: idVec3?) {
        out.set(0, Vector.DotProduct(`in`.ToFloatPtr(), modelMatrix))
        out.set(1, Vector.DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.set(2, Vector.DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12)))
    }

    fun R_GlobalPlaneToLocal(modelMatrix: FloatArray? /*[16]*/, `in`: idPlane?, out: idPlane?) {
        out.set(0, Vector.DotProduct(`in`.ToFloatPtr(), modelMatrix))
        out.set(1, Vector.DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 4, 8)))
        out.set(2, Vector.DotProduct(`in`.ToFloatPtr(), Arrays.copyOfRange(modelMatrix, 8, 12)))
        out.set(
            3,
            `in`.get(3) + modelMatrix.get(12) * `in`.get(0) + modelMatrix.get(13) * `in`.get(1) + modelMatrix.get(14) * `in`.get(
                2
            )
        )
    }

    fun R_LocalPlaneToGlobal(modelMatrix: FloatArray? /*[16]*/, `in`: idPlane?, out: idPlane?) {
        val offset: Float
        tr_main.R_LocalVectorToGlobal(modelMatrix, `in`.Normal(), out.Normal())
        offset =
            modelMatrix.get(12) * out.get(0) + modelMatrix.get(13) * out.get(1) + modelMatrix.get(14) * out.get(2)
        out.set(3, `in`.get(3) - offset)
    }

    // transform Z in eye coordinates to window coordinates
    fun R_TransformEyeZToWin(src_z: Float, projectionMatrix: FloatArray?, dst_z: CFloat?) {
        val clip_z: Float
        val clip_w: Float

        // projection
        clip_z = src_z * projectionMatrix.get(2 + 2 * 4) + projectionMatrix.get(2 + 3 * 4)
        clip_w = src_z * projectionMatrix.get(3 + 2 * 4) + projectionMatrix.get(3 + 3 * 4)
        if (clip_w <= 0.0f) {
            dst_z.setVal(0.0f) // clamp to near plane
        } else {
            dst_z.setVal(clip_z / clip_w)
            dst_z.setVal(dst_z.getVal() * 0.5f + 0.5f) // convert to window coords
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
        bounds: idBounds?,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane?>?
    ): Boolean {
        var i: Int
        var d: Float
        val worldOrigin = idVec3()
        val worldRadius: Float
        var frust: idPlane?
        if (RenderSystem_init.r_useCulling.GetInteger() == 0) {
            return false
        }

        // transform the surface bounds into world space
        val localOrigin = idVec3(bounds.get(0).oPlus(bounds.get(1)).oMultiply(0.5f))
        worldOrigin.set(tr_main.R_LocalPointToGlobal(modelMatrix, localOrigin))
        worldRadius = bounds.get(0).minus(localOrigin).Length() // FIXME: won't be correct for scaled objects
        i = 0
        while (i < numPlanes) {
            frust = planes.get(i)
            d = frust.Distance(worldOrigin)
            if (d > worldRadius) {
                return true // culled
            }
            i++
        }
        return false // not culled
    }

    fun R_CornerCullLocalBox(
        bounds: idBounds?,
        modelMatrix: FloatArray? /*[16]*/,
        numPlanes: Int,
        planes: Array<idPlane?>?
    ): Boolean {
        var i: Int
        var j: Int
        val transformed: Array<idVec3?> = idVec3.Companion.generateArray(8)
        val dists = FloatArray(8)
        val v = idVec3()
        var frust: idPlane?
        tr_main.DBG_R_CornerCullLocalBox++

        // we can disable box culling for experimental timing purposes
        if (RenderSystem_init.r_useCulling.GetInteger() < 2) {
            return false
        }

        // transform into world space
        i = 0
        while (i < 8) {
            v.set(0, bounds.get(i shr 0 and 1, 0))
            v.set(1, bounds.get(i shr 1 and 1, 1))
            v.set(2, bounds.get(i shr 2 and 1, 2))
            transformed[i].set(tr_main.R_LocalPointToGlobal(modelMatrix, v))
            i++
        }

        // check against frustum planes
        i = 0
        while (i < numPlanes) {
            frust = planes.get(i)
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
                tr_local.tr.pc.c_box_cull_out++
                return true
            }
            i++
        }
        //        System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
//        System.out.println(">>>>>>>>>>> " + Arrays.toString(transformed));
//        System.out.println(">>>>>>>>>>> " + Arrays.toString(dists));
//        System.out.println("<<<<<<<<<<< " + DBG_R_CornerCullLocalBox);
        tr_local.tr.pc.c_box_cull_in++
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
        return if (tr_main.R_RadiusCullLocalBox(bounds, modelMatrix, numPlanes, planes)) {
            true
        } else tr_main.R_CornerCullLocalBox(bounds, modelMatrix, numPlanes, planes)
    }

    /*
     ==========================
     R_TransformModelToClip
     ==========================
     */
    fun R_TransformModelToClip(
        src: idVec3?,
        modelMatrix: FloatArray?,
        projectionMatrix: FloatArray?,
        eye: idPlane?,
        dst: idPlane?
    ) {
        var i: Int
        i = 0
        while (i < 4) {
            eye.set(
                i,
                src.get(0) * modelMatrix.get(i + 0 * 4) + src.get(1) * modelMatrix.get(i + 1 * 4) + src.get(2) * modelMatrix.get(
                    i + 2 * 4
                ) + 1 * modelMatrix.get(i + 3 * 4)
            )
            i++
        }
        i = 0
        while (i < 4) {
            dst.set(
                i,
                eye.get(0) * projectionMatrix.get(i + 0 * 4) + eye.get(1) * projectionMatrix.get(i + 1 * 4) + eye.get(
                    2
                ) * projectionMatrix.get(i + 2 * 4) + eye.get(3) * projectionMatrix.get(i + 3 * 4)
            )
            i++
        }
    }

    /*
     ==========================
     R_GlobalToNormalizedDeviceCoordinates

     -1 to 1 range in x, y, and z
     ==========================
     */
    fun R_GlobalToNormalizedDeviceCoordinates(global: idVec3?, ndc: idVec3?) {
        var i: Int
        val view = idPlane()
        val clip = idPlane()

        // _D3XP added work on primaryView when no viewDef
        if (null == tr_local.tr.viewDef) {
            i = 0
            while (i < 4) {
                view.set(
                    i,
                    global.get(0) * tr_local.tr.primaryView.worldSpace.modelViewMatrix[i + 0 * 4] + global.get(1) * tr_local.tr.primaryView.worldSpace.modelViewMatrix[i + 1 * 4] + global.get(
                        2
                    ) * tr_local.tr.primaryView.worldSpace.modelViewMatrix[i + 2 * 4] + tr_local.tr.primaryView.worldSpace.modelViewMatrix[i + 3 * 4]
                )
                i++
            }
            i = 0
            while (i < 4) {
                clip.set(
                    i,
                    view.get(0) * tr_local.tr.primaryView.projectionMatrix[i + 0 * 4] + view.get(1) * tr_local.tr.primaryView.projectionMatrix[i + 1 * 4] + view.get(
                        2
                    ) * tr_local.tr.primaryView.projectionMatrix[i + 2 * 4] + view.get(3) * tr_local.tr.primaryView.projectionMatrix[i + 3 * 4]
                )
                i++
            }
        } else {
            i = 0
            while (i < 4) {
                view.set(
                    i,
                    global.get(0) * tr_local.tr.viewDef.worldSpace.modelViewMatrix[i + 0 * 4] + global.get(1) * tr_local.tr.viewDef.worldSpace.modelViewMatrix[i + 1 * 4] + global.get(
                        2
                    ) * tr_local.tr.viewDef.worldSpace.modelViewMatrix[i + 2 * 4] + tr_local.tr.viewDef.worldSpace.modelViewMatrix[i + 3 * 4]
                )
                i++
            }
            i = 0
            while (i < 4) {
                clip.set(
                    i,
                    view.get(0) * tr_local.tr.viewDef.projectionMatrix[i + 0 * 4] + view.get(1) * tr_local.tr.viewDef.projectionMatrix[i + 1 * 4] + view.get(
                        2
                    ) * tr_local.tr.viewDef.projectionMatrix[i + 2 * 4] + view.get(3) * tr_local.tr.viewDef.projectionMatrix[i + 3 * 4]
                )
                i++
            }
        }
        ndc.set(0, clip.get(0) / clip.get(3))
        ndc.set(1, clip.get(1) / clip.get(3))
        ndc.set(2, (clip.get(2) + clip.get(3)) / (2 * clip.get(3)))
    }

    /*
     ==========================
     R_TransformClipToDevice

     Clip to normalized device coordinates
     ==========================
     */
    fun R_TransformClipToDevice(clip: idPlane?, view: viewDef_s?, normalized: idVec3?) {
        normalized.set(0, clip.get(0) / clip.get(3))
        normalized.set(1, clip.get(1) / clip.get(3))
        normalized.set(2, clip.get(2) / clip.get(3))
    }

    /*
     ==========================
     myGlMultMatrix
     ==========================
     */
    fun myGlMultMatrix(a: FloatArray? /*[16]*/, b: FloatArray? /*[16]*/, out: FloatArray? /*[16]*/) {
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
            out.get(0 * 4 + 0) =
                a.get(0 * 4 + 0) * b.get(0 * 4 + 0) + a.get(0 * 4 + 1) * b.get(1 * 4 + 0) + a.get(0 * 4 + 2) * b.get(2 * 4 + 0) + a.get(
                    0 * 4 + 3
                ) * b.get(3 * 4 + 0)
            out.get(0 * 4 + 1) =
                a.get(0 * 4 + 0) * b.get(0 * 4 + 1) + a.get(0 * 4 + 1) * b.get(1 * 4 + 1) + a.get(0 * 4 + 2) * b.get(2 * 4 + 1) + a.get(
                    0 * 4 + 3
                ) * b.get(3 * 4 + 1)
            out.get(0 * 4 + 2) =
                a.get(0 * 4 + 0) * b.get(0 * 4 + 2) + a.get(0 * 4 + 1) * b.get(1 * 4 + 2) + a.get(0 * 4 + 2) * b.get(2 * 4 + 2) + a.get(
                    0 * 4 + 3
                ) * b.get(3 * 4 + 2)
            out.get(0 * 4 + 3) =
                a.get(0 * 4 + 0) * b.get(0 * 4 + 3) + a.get(0 * 4 + 1) * b.get(1 * 4 + 3) + a.get(0 * 4 + 2) * b.get(2 * 4 + 3) + a.get(
                    0 * 4 + 3
                ) * b.get(3 * 4 + 3)
            out.get(1 * 4 + 0) =
                a.get(1 * 4 + 0) * b.get(0 * 4 + 0) + a.get(1 * 4 + 1) * b.get(1 * 4 + 0) + a.get(1 * 4 + 2) * b.get(2 * 4 + 0) + a.get(
                    1 * 4 + 3
                ) * b.get(3 * 4 + 0)
            out.get(1 * 4 + 1) =
                a.get(1 * 4 + 0) * b.get(0 * 4 + 1) + a.get(1 * 4 + 1) * b.get(1 * 4 + 1) + a.get(1 * 4 + 2) * b.get(2 * 4 + 1) + a.get(
                    1 * 4 + 3
                ) * b.get(3 * 4 + 1)
            out.get(1 * 4 + 2) =
                a.get(1 * 4 + 0) * b.get(0 * 4 + 2) + a.get(1 * 4 + 1) * b.get(1 * 4 + 2) + a.get(1 * 4 + 2) * b.get(2 * 4 + 2) + a.get(
                    1 * 4 + 3
                ) * b.get(3 * 4 + 2)
            out.get(1 * 4 + 3) =
                a.get(1 * 4 + 0) * b.get(0 * 4 + 3) + a.get(1 * 4 + 1) * b.get(1 * 4 + 3) + a.get(1 * 4 + 2) * b.get(2 * 4 + 3) + a.get(
                    1 * 4 + 3
                ) * b.get(3 * 4 + 3)
            out.get(2 * 4 + 0) =
                a.get(2 * 4 + 0) * b.get(0 * 4 + 0) + a.get(2 * 4 + 1) * b.get(1 * 4 + 0) + a.get(2 * 4 + 2) * b.get(2 * 4 + 0) + a.get(
                    2 * 4 + 3
                ) * b.get(3 * 4 + 0)
            out.get(2 * 4 + 1) =
                a.get(2 * 4 + 0) * b.get(0 * 4 + 1) + a.get(2 * 4 + 1) * b.get(1 * 4 + 1) + a.get(2 * 4 + 2) * b.get(2 * 4 + 1) + a.get(
                    2 * 4 + 3
                ) * b.get(3 * 4 + 1)
            out.get(2 * 4 + 2) =
                a.get(2 * 4 + 0) * b.get(0 * 4 + 2) + a.get(2 * 4 + 1) * b.get(1 * 4 + 2) + a.get(2 * 4 + 2) * b.get(2 * 4 + 2) + a.get(
                    2 * 4 + 3
                ) * b.get(3 * 4 + 2)
            out.get(2 * 4 + 3) =
                a.get(2 * 4 + 0) * b.get(0 * 4 + 3) + a.get(2 * 4 + 1) * b.get(1 * 4 + 3) + a.get(2 * 4 + 2) * b.get(2 * 4 + 3) + a.get(
                    2 * 4 + 3
                ) * b.get(3 * 4 + 3)
            out.get(3 * 4 + 0) =
                a.get(3 * 4 + 0) * b.get(0 * 4 + 0) + a.get(3 * 4 + 1) * b.get(1 * 4 + 0) + a.get(3 * 4 + 2) * b.get(2 * 4 + 0) + a.get(
                    3 * 4 + 3
                ) * b.get(3 * 4 + 0)
            out.get(3 * 4 + 1) =
                a.get(3 * 4 + 0) * b.get(0 * 4 + 1) + a.get(3 * 4 + 1) * b.get(1 * 4 + 1) + a.get(3 * 4 + 2) * b.get(2 * 4 + 1) + a.get(
                    3 * 4 + 3
                ) * b.get(3 * 4 + 1)
            out.get(3 * 4 + 2) =
                a.get(3 * 4 + 0) * b.get(0 * 4 + 2) + a.get(3 * 4 + 1) * b.get(1 * 4 + 2) + a.get(3 * 4 + 2) * b.get(2 * 4 + 2) + a.get(
                    3 * 4 + 3
                ) * b.get(3 * 4 + 2)
            out.get(3 * 4 + 3) =
                a.get(3 * 4 + 0) * b.get(0 * 4 + 3) + a.get(3 * 4 + 1) * b.get(1 * 4 + 3) + a.get(3 * 4 + 2) * b.get(2 * 4 + 3) + a.get(
                    3 * 4 + 3
                ) * b.get(3 * 4 + 3)
        }
    }

    /*
     ================
     R_TransposeGLMatrix
     ================
     */
    fun R_TransposeGLMatrix(`in`: FloatArray? /*[16]*/, out: FloatArray? /*[16]*/) {
        var i: Int
        var j: Int
        i = 0
        while (i < 4) {
            j = 0
            while (j < 4) {
                out.get(i * 4 + j) = `in`.get(j * 4 + i)
                j++
            }
            i++
        }
    }

    fun R_SetViewMatrix(viewDef: viewDef_s?) {
        val origin = idVec3()
        val world: viewEntity_s?
        val viewerMatrix = FloatArray(16)
        viewDef.worldSpace = viewEntity_s()
        world = viewDef.worldSpace //memset(world, 0, sizeof(world));

        // the model matrix is an identity
        world.modelMatrix[0 * 4 + 0] = 1
        world.modelMatrix[1 * 4 + 1] = 1
        world.modelMatrix[2 * 4 + 2] = 1

        // transform by the camera placement
        origin.set(viewDef.renderView.vieworg)
        viewerMatrix[0] = viewDef.renderView.viewaxis.get(0, 0)
        viewerMatrix[4] = viewDef.renderView.viewaxis.get(0, 1)
        viewerMatrix[8] = viewDef.renderView.viewaxis.get(0, 2)
        viewerMatrix[12] =
            -origin.get(0) * viewerMatrix[0] + -origin.get(1) * viewerMatrix[4] + -origin.get(2) * viewerMatrix[8]
        viewerMatrix[1] = viewDef.renderView.viewaxis.get(1, 0)
        viewerMatrix[5] = viewDef.renderView.viewaxis.get(1, 1)
        viewerMatrix[9] = viewDef.renderView.viewaxis.get(1, 2)
        viewerMatrix[13] =
            -origin.get(0) * viewerMatrix[1] + -origin.get(1) * viewerMatrix[5] + -origin.get(2) * viewerMatrix[9]
        viewerMatrix[2] = viewDef.renderView.viewaxis.get(2, 0)
        viewerMatrix[6] = viewDef.renderView.viewaxis.get(2, 1)
        viewerMatrix[10] = viewDef.renderView.viewaxis.get(2, 2)
        viewerMatrix[14] =
            -origin.get(0) * viewerMatrix[2] + -origin.get(1) * viewerMatrix[6] + -origin.get(2) * viewerMatrix[10]
        viewerMatrix[3] = 0
        viewerMatrix[7] = 0
        viewerMatrix[11] = 0
        viewerMatrix[15] = 1

        // convert from our coordinate system (looking down X)
        // to OpenGL's coordinate system (looking down -Z)
        tr_main.myGlMultMatrix(viewerMatrix, tr_main.s_flipMatrix, world.modelViewMatrix)
    }

    fun R_SetupProjection() {
        var xmin: Float
        var xmax: Float
        var ymin: Float
        var ymax: Float
        val width: Float
        val height: Float
        var zNear: Float
        var jitterx: Float
        var jittery: Float

        // random jittering is usefull when multiple
        // frames are going to be blended together
        // for motion blurred anti-aliasing
        if (RenderSystem_init.r_jitter.GetBool()) {
            jitterx = tr_main.random.RandomFloat()
            jittery = tr_main.random.RandomFloat()
        } else {
            jittery = 0f
            jitterx = jittery
        }

        //
        // set up projection matrix
        //
        zNear = RenderSystem_init.r_znear.GetFloat()
        if (tr_local.tr.viewDef.renderView.cramZNear) {
            zNear *= 0.25.toFloat()
        }
        ymax = (zNear * Math.tan((tr_local.tr.viewDef.renderView.fov_y * idMath.PI / 360.0f).toDouble())).toFloat()
        ymin = -ymax
        xmax = (zNear * Math.tan((tr_local.tr.viewDef.renderView.fov_x * idMath.PI / 360.0f).toDouble())).toFloat()
        xmin = -xmax
        width = xmax - xmin
        height = ymax - ymin
        jitterx = jitterx * width / (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1 + 1)
        xmin += jitterx
        xmax += jitterx
        jittery = jittery * height / (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1 + 1)
        ymin += jittery
        ymax += jittery
        tr_local.tr.viewDef.projectionMatrix[0] = 2 * zNear / width
        tr_local.tr.viewDef.projectionMatrix[4] = 0
        tr_local.tr.viewDef.projectionMatrix[8] = (xmax + xmin) / width // normally 0
        tr_local.tr.viewDef.projectionMatrix[12] = 0
        tr_local.tr.viewDef.projectionMatrix[1] = 0
        tr_local.tr.viewDef.projectionMatrix[5] = 2 * zNear / height
        tr_local.tr.viewDef.projectionMatrix[9] = (ymax + ymin) / height // normally 0
        tr_local.tr.viewDef.projectionMatrix[13] = 0

        // this is the far-plane-at-infinity formulation, and
        // crunches the Z range slightly so w=0 vertexes do not
        // rasterize right at the wraparound point
        tr_local.tr.viewDef.projectionMatrix[2] = 0
        tr_local.tr.viewDef.projectionMatrix[6] = 0
        tr_local.tr.viewDef.projectionMatrix[10] = -0.999f
        tr_local.tr.viewDef.projectionMatrix[14] = -2.0f * zNear
        tr_local.tr.viewDef.projectionMatrix[3] = 0
        tr_local.tr.viewDef.projectionMatrix[7] = 0
        tr_local.tr.viewDef.projectionMatrix[11] = -1
        tr_local.tr.viewDef.projectionMatrix[15] = 0
    }

    /*
     =================
     R_SetupViewFrustum

     Setup that culling frustum planes for the current view
     FIXME: derive from modelview matrix times projection matrix
     =================
     */
    fun R_SetupViewFrustum() {
        var i: Int
        val xs = CFloat(0.0f)
        val xc = CFloat(0.0f)
        var ang: Float
        ang = Math_h.DEG2RAD(tr_local.tr.viewDef.renderView.fov_x) * 0.5f
        idMath.SinCos(ang, xs, xc)
        tr_local.tr.viewDef.frustum[0].set(
            tr_local.tr.viewDef.renderView.viewaxis.get(0).times(xs.getVal())
                .oPlus(tr_local.tr.viewDef.renderView.viewaxis.get(1).times(xc.getVal()))
        )
        tr_local.tr.viewDef.frustum[1].set(
            tr_local.tr.viewDef.renderView.viewaxis.get(0).times(xs.getVal())
                .oMinus(tr_local.tr.viewDef.renderView.viewaxis.get(1).times(xc.getVal()))
        )
        ang = Math_h.DEG2RAD(tr_local.tr.viewDef.renderView.fov_y) * 0.5f
        idMath.SinCos(ang, xs, xc)
        tr_local.tr.viewDef.frustum[2].set(
            tr_local.tr.viewDef.renderView.viewaxis.get(0).times(xs.getVal())
                .oPlus(tr_local.tr.viewDef.renderView.viewaxis.get(2).times(xc.getVal()))
        )
        tr_local.tr.viewDef.frustum[3].set(
            tr_local.tr.viewDef.renderView.viewaxis.get(0).times(xs.getVal())
                .oMinus(tr_local.tr.viewDef.renderView.viewaxis.get(2).times(xc.getVal()))
        )

        // plane four is the front clipping plane
        tr_local.tr.viewDef.frustum[4].set( /* vec3_origin - */tr_local.tr.viewDef.renderView.viewaxis.get(0))
        i = 0
        while (i < 5) {

            // flip direction so positive side faces out (FIXME: globally unify this)
            tr_local.tr.viewDef.frustum[i].set(tr_local.tr.viewDef.frustum[i].Normal().oNegative())
            tr_local.tr.viewDef.frustum[i].set(
                3,
                -tr_local.tr.viewDef.renderView.vieworg.times(tr_local.tr.viewDef.frustum[i].Normal())
            )
            i++
        }

        // eventually, plane five will be the rear clipping plane for fog
        var dNear: Float
        val dFar: Float
        val dLeft: Float
        val dUp: Float
        dNear = RenderSystem_init.r_znear.GetFloat()
        if (tr_local.tr.viewDef.renderView.cramZNear) {
            dNear *= 0.25f
        }
        dFar = Lib.Companion.MAX_WORLD_SIZE.toFloat()
        dLeft = (dFar * Math.tan(Math_h.DEG2RAD(tr_local.tr.viewDef.renderView.fov_x * 0.5f).toDouble())).toFloat()
        dUp = (dFar * Math.tan(Math_h.DEG2RAD(tr_local.tr.viewDef.renderView.fov_y * 0.5f).toDouble())).toFloat()
        tr_local.tr.viewDef.viewFrustum.SetOrigin(tr_local.tr.viewDef.renderView.vieworg)
        tr_local.tr.viewDef.viewFrustum.SetAxis(tr_local.tr.viewDef.renderView.viewaxis)
        tr_local.tr.viewDef.viewFrustum.SetSize(dNear, dFar, dLeft, dUp)
    }

    /*
     ===================
     R_ConstrainViewFrustum
     ===================
     */
    fun R_ConstrainViewFrustum() {
        val bounds = idBounds()

        // constrain the view frustum to the total bounds of all visible lights and visible entities
        bounds.Clear()
        var vLight = tr_local.tr.viewDef.viewLights
        while (vLight != null) {
            bounds.AddBounds(vLight.lightDef.frustumTris.bounds)
            vLight = vLight.next
        }
        var vEntity = tr_local.tr.viewDef.viewEntitys
        while (vEntity != null) {
            bounds.AddBounds(vEntity.entityDef.referenceBounds)
            vEntity = vEntity.next
        }
        tr_local.tr.viewDef.viewFrustum.ConstrainToBounds(bounds)
        if (RenderSystem_init.r_useFrustumFarDistance.GetFloat() > 0.0f) {
            tr_local.tr.viewDef.viewFrustum.MoveFarDistance(RenderSystem_init.r_useFrustumFarDistance.GetFloat())
        }
    }

    /*
     =================
     R_SortDrawSurfs
     =================
     */
    fun R_SortDrawSurfs() {
        // sort the drawsurfs by sort type, then orientation, then shader
//        qsort(tr.viewDef.drawSurfs, tr.viewDef.numDrawSurfs, sizeof(tr.viewDef.drawSurfs[0]), R_QsortSurfaces);
        if (tr_local.tr.viewDef.drawSurfs != null) {
            Arrays.sort(tr_local.tr.viewDef.drawSurfs, 0, tr_local.tr.viewDef.numDrawSurfs, R_QsortSurfaces())
            //            int bla = 0;
//            for (int i = 0; i < tr.viewDef.numDrawSurfs; i++) {
//                Material.shaderStage_t[] stages = tr.viewDef.drawSurfs[i].material.stages;
//                if (stages != null && stages[0].texture.image[0] != null &&
//                        stages[0].texture.image[0].imgName.toString().contains("env/cloudy")) {
//                    tr.viewDef.drawSurfs[bla++] = tr.viewDef.drawSurfs[i];
//                    System.out.println(stages[0].texture.image[0].imgName);
//                }
//            }
//            tr.viewDef.numDrawSurfs = bla;
//
//            final int from = 61;
//            final int to = Math.min(tr.viewDef.numDrawSurfs - from, from + 1);
//            tr.viewDef.drawSurfs = Arrays.copyOfRange(tr.viewDef.drawSurfs, from, to);
//            tr.viewDef.numDrawSurfs = to - from;
//           tr.viewDef.drasawwwwwwwwwwwwwwwwwwwwwwwwwwwwaw a    wSurfs[0].geo.indexes = null;
        }
    }

    //========================================================================
    //    
    //    
    //==============================================================================
    fun R_RenderView(parms: viewDef_s?) {
        val oldView: viewDef_s?
        tr_main.DEBUG_R_RenderView++
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
        tr_main.R_SetViewMatrix(tr_local.tr.viewDef)

        // the four sides of the view frustum are needed
        // for culling and portal visibility
        tr_main.R_SetupViewFrustum()

        // we need to set the projection matrix before doing
        // portal-to-screen scissor box calculations
        tr_main.R_SetupProjection()

        // identify all the visible portalAreas, and the entityDefs and
        // lightDefs that are in them and pass culling.
//	static_cast<idRenderWorldLocal *>(parms.renderWorld).FindViewLightsAndEntities();
        parms.renderWorld.FindViewLightsAndEntities()

        // constrain the view frustum to the view lights and entities
        tr_main.R_ConstrainViewFrustum()

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
        tr_main.R_SortDrawSurfs()

        // generate any subviews (mirrors, cameras, etc) before adding this view
        if (tr_subview.R_GenerateSubViews()) {
            // if we are debugging subviews, allow the skipping of the
            // main view draw
            if (RenderSystem_init.r_subviewOnly.GetBool()) {
                return
            }
        }

        // write everything needed to the demo file
        if (Session.Companion.session.writeDemo != null) {
//		static_cast<idRenderWorldLocal *>(parms.renderWorld)->WriteVisibleDefs( tr.viewDef );
            parms.renderWorld.WriteVisibleDefs(tr_local.tr.viewDef)
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
    class R_QsortSurfaces : cmp_t<drawSurf_s?> {
        override fun compare(a: drawSurf_s?, b: drawSurf_s?): Int {

            //this check assumes that the array contains nothing but nulls from this point.
            if (null == a && null == b) {
                return 0
            }
            if (null == b || null != a && a.sort < b.sort) {
                return -1
            }
            return if (null == a || null != b && a.sort > b.sort) {
                1
            } else 0
        }
    }
}