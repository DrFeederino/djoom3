package neo.Renderer

import neo.Renderer.Model.lightingCache_s
import neo.Renderer.Model.shadowCache_s
import neo.TempDump.Deprecation_Exception
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.geometry.DrawVert
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBVertexBufferObject
import java.nio.ByteBuffer

/**
 *
 */
object VertexCache {
    val vertexCache: idVertexCache = idVertexCache()
    const val EXPAND_HEADERS = 1024

    //
    const val FRAME_MEMORY_BYTES = 0x200000

    // vertex cache calls should only be made by the front end
    const val NUM_VERTEX_FRAMES = 2

    internal enum class vertBlockTag_t {
        TAG_FREE, TAG_USED, TAG_FIXED,  // for the temp buffers
        TAG_TEMP // in frame temp area, not static area
    }

    class vertCache_s : Iterable<vertCache_s?> {
        //TODO:use iterators for all our makeshift linked lists.
        private val frameUsed // it can't be purged if near the current frame
                = 0
        private val indexBuffer // holds indexes instead of vertexes
                = false
        private val next: vertCache_s? = null
        private val prev // may be on the static list or one of the frame lists
                : vertCache_s? = null
        private val offset = 0
        private val size // may be larger than the amount asked for, due
                = 0

        //                                 // to round up and minimum fragment sizes
        private val tag // a tag of 0 is a free block
                : vertBlockTag_t? = null
        private val user // will be set to zero when purged
                : vertCache_s? = null
        private val   /*GLuint*/vao = 0
        private val   /*GLuint*/vbo = 0
        private val virtMem // only one of vbo / virtMem will be set
                : ByteBuffer? = null

        override fun iterator(): MutableIterator<vertCache_s?>? {
            return object : MutableIterator<Any?> {
                override fun hasNext(): Boolean {
                    return next != null
                }

                override fun next(): Any? {
                    return next
                }

                override fun remove() {
                    throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
                }
            }
        }

        companion object {
            /**
             * Creates an array starting at the current object, till it reaches
             * NULL.
             */
            fun toArray(cache_s: vertCache_s?): Array<vertCache_s?>? {
                val array: MutableList<vertCache_s?> = ArrayList(10)
                val iterator: MutableIterator<vertCache_s?>?
                if (cache_s != null) {
                    iterator = cache_s.iterator()
                    array.add(cache_s)
                    while (iterator.hasNext()) {
                        array.add(iterator.next())
                    }
                }
                return array.toTypedArray()
            }
        }
    }

    //
    /*
     ==============
     R_ListVertexCache_f
     ==============
     */
    internal class R_ListVertexCache_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            VertexCache.vertexCache.List()
        }

        companion object {
            private val instance: cmdFunction_t? = R_ListVertexCache_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    //================================================================================
    class idVertexCache {
        //
        private val tempBuffers // allocated at startup
                : Array<vertCache_s?>?

        //
        private var allocatingTempBuffer // force GL_STREAM_DRAW_ARB
                = false

        //
        private var currentFrame // for purgable block tracking
                = 0
        private val deferredFreeList // head of doubly linked list
                : vertCache_s?
        private var dynamicAllocThisFrame = 0
        private var dynamicCountThisFrame = 0
        private val dynamicHeaders // head of doubly linked list
                : vertCache_s?

        // staticHeaders.next is most recently used
        //
        private var frameBytes // for each of NUM_VERTEX_FRAMES frames
                = 0
        private val freeDynamicHeaders // head of doubly linked list
                : vertCache_s?

        //
        //        private final idBlockAlloc<vertCache_s> headerAllocator = new idBlockAlloc<>(1024);
        //
        private val freeStaticHeaders // head of doubly linked list
                : vertCache_s?
        private var listNum // currentFrame % NUM_VERTEX_FRAMES, determines which tempBuffers to use
                = 0

        //
        private var staticAllocThisFrame // debug counter
                = 0
        private var staticAllocTotal // for end of frame purging
                = 0
        private var staticCountThisFrame = 0

        //
        private var staticCountTotal = 0
        private val staticHeaders // head of doubly linked list in MRU order,
                : vertCache_s?
        private var tempOverflow // had to alloc a temp in static memory
                = false

        //
        private var virtualMemory // not fast stuff
                = false

        fun Init() {
            CmdSystem.cmdSystem.AddCommand(
                "listVertexCache",
                R_ListVertexCache_f.getInstance(),
                CmdSystem.CMD_FL_RENDERER,
                "lists vertex cache"
            )
            if (r_vertexBufferMegs.GetInteger() < 8) {
                r_vertexBufferMegs.SetInteger(8)
            }
            virtualMemory = false

            // use ARB_vertex_buffer_object unless explicitly disabled
            if (RenderSystem_init.r_useVertexBuffers.GetInteger() != 0 && tr_local.glConfig.ARBVertexBufferObjectAvailable) {
                Common.common.Printf("using ARB_vertex_buffer_object memory\n")
            } else {
                virtualMemory = true
                RenderSystem_init.r_useIndexBuffers.SetBool(false)
                Common.common.Printf("WARNING: vertex array range in virtual memory (SLOW)\n")
            }

            // initialize the cache memory blocks
            freeStaticHeaders.prev = freeStaticHeaders
            freeStaticHeaders.next = freeStaticHeaders.prev
            staticHeaders.prev = staticHeaders
            staticHeaders.next = staticHeaders.prev
            freeDynamicHeaders.prev = freeDynamicHeaders
            freeDynamicHeaders.next = freeDynamicHeaders.prev
            dynamicHeaders.prev = dynamicHeaders
            dynamicHeaders.next = dynamicHeaders.prev
            deferredFreeList.prev = deferredFreeList
            deferredFreeList.next = deferredFreeList.prev

            // set up the dynamic frame memory
            frameBytes = VertexCache.FRAME_MEMORY_BYTES
            staticAllocTotal = 0
            var junk = BufferUtils.createByteBuffer(frameBytes) // Mem_Alloc(frameBytes);
            for (i in 0 until VertexCache.NUM_VERTEX_FRAMES) {
                allocatingTempBuffer = true // force the alloc to use GL_STREAM_DRAW_ARB
                tempBuffers.get(i) = Alloc(junk, frameBytes)
                allocatingTempBuffer = false
                tempBuffers.get(i).tag = vertBlockTag_t.TAG_FIXED
                // unlink these from the static list, so they won't ever get purged
                tempBuffers.get(i).next.prev = tempBuffers.get(i).prev
                tempBuffers.get(i).prev.next = tempBuffers.get(i).next
            }
            //            Mem_Free(junk);
            junk = null
            EndFrame()
        }

        fun Shutdown() {
//	PurgeAll();	// !@#: also purge the temp buffers

//            headerAllocator.Shutdown();
        }

        /*
         =============
         idVertexCache::IsFast

         just for gfxinfo printing
         =============
         */
        fun IsFast(): Boolean {
            return !virtualMemory
        }

        /*
         ===========
         idVertexCache::PurgeAll

         Used when toggling vertex programs on or off, because
         the cached data isn't valid
         ===========
         */
        // called when vertex programs are enabled or disabled, because
        // the cached data is no longer valid
        fun PurgeAll() {
            while (staticHeaders.next !== staticHeaders) {
                ActuallyFree(staticHeaders.next)
            }
        }

        // Tries to allocate space for the given data in fast vertex
        // memory, and copies it over.
        // Alloc does NOT do a touch, which allows purging of things
        // created at level load time even if a frame hasn't passed yet.
        // These allocations can be purged, which will zero the pointer.
        @JvmOverloads
        fun Alloc(
            data: ByteBuffer?,
            size: Int,
            @Deprecated("") buffer: vertCache_s? = null,
            indexBuffer: Boolean = false /*= false*/
        ): vertCache_s? {
            var buffer = buffer
            var block: vertCache_s?
            if (size <= 0) {
                Common.common.Error("idVertexCache::Alloc: size = %d\n", size)
            }

            // if we can't find anything, it will be NULL
            buffer = null

            // if we don't have any remaining unused headers, allocate some more
            if (freeStaticHeaders.next === freeStaticHeaders) {
                for (i in 0 until VertexCache.EXPAND_HEADERS) {
                    block = vertCache_s() //headerAllocator.Alloc();
                    block.next = freeStaticHeaders.next
                    block.prev = freeStaticHeaders
                    block.next.prev = block
                    block.prev.next = block
                    if (!virtualMemory) {
                        block.vbo = qgl.qglGenBuffersARB()
                        //                        block.vao = GL30.glGenVertexArrays();
                    }
                }
            }

            // move it from the freeStaticHeaders list to the staticHeaders list
            block = freeStaticHeaders.next
            block.next.prev = block.prev
            block.prev.next = block.next
            block.next = staticHeaders.next
            block.prev = staticHeaders
            block.next.prev = block
            block.prev.next = block
            block.size = size
            block.offset = 0
            block.tag = vertBlockTag_t.TAG_USED

            // save data for debugging
            staticAllocThisFrame += block.size
            staticCountThisFrame++
            staticCountTotal++
            staticAllocTotal += block.size

            // this will be set to zero when it is purged
            block.user = buffer //TODO:wtf?
            buffer = block

            // allocation doesn't imply used-for-drawing, because at level
            // load time lots of things may be created, but they aren't
            // referenced by the GPU yet, and can be purged if needed.
            block.frameUsed = currentFrame - VertexCache.NUM_VERTEX_FRAMES
            block.indexBuffer = indexBuffer

            // copy the data
            if (block.vbo != 0) {
                if (indexBuffer) {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, block.vbo) //TODO:get?
                    qgl.qglBufferDataARB(
                        ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB,  /*(GLsizeiptrARB)*/
                        size,
                        data,
                        ARBVertexBufferObject.GL_STATIC_DRAW_ARB
                    )
                } else {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, block.vbo) //TODO:get?
                    if (allocatingTempBuffer) {
                        qgl.qglBufferDataARB(
                            ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,  /*(GLsizeiptrARB)*/
                            size,
                            data,
                            ARBVertexBufferObject.GL_STREAM_DRAW_ARB
                        )
                    } else {
                        qgl.qglBufferDataARB(
                            ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,  /*(GLsizeiptrARB)*/
                            size,
                            data,
                            ARBVertexBufferObject.GL_STATIC_DRAW_ARB
                        )
                    }
                }
            } else {
                block.virtMem = ByteBuffer.allocate(size)
                //                SIMDProcessor.Memcpy(block.virtMem, data, size);
                block.virtMem = data.duplicate()
            }
            return buffer
        }

        @Deprecated("")
        fun Alloc(data: IntArray?, size: Int, buffer: vertCache_s?, indexBuffer: Boolean /*= false*/) {
            val byteData = ByteBuffer.allocate(data.size * 4)
            byteData.asIntBuffer().put(data)
            throw Deprecation_Exception()
        }

        fun Alloc(data: IntArray?, size: Int, indexBuffer: Boolean): vertCache_s? {
            val byteData = BufferUtils.createByteBuffer(size)
            byteData.asIntBuffer().put(data)
            return Alloc(byteData, size, indexBuffer)
        }

        fun Alloc(data: ByteBuffer?, size: Int, indexBuffer: Boolean): vertCache_s? {
            return Alloc(data, size, null, indexBuffer)
        }

        fun Alloc(data: Array<idDrawVert?>?, size: Int, buffer: vertCache_s?): vertCache_s? {
            return Alloc(DrawVert.toByteBuffer(data), size, buffer, false)
        }

        fun Alloc(data: Array<idDrawVert?>?, size: Int): vertCache_s? {
            return Alloc(DrawVert.toByteBuffer(data), size, null)
        }

        fun Alloc(data: Array<lightingCache_s?>?, size: Int): vertCache_s? {
            return Alloc(lightingCache_s.Companion.toByteBuffer(data), size, null)
        }

        fun Alloc(data: Array<shadowCache_s?>?, size: Int): vertCache_s? {
            return Alloc(shadowCache_s.Companion.toByteBuffer(data), size, null)
        }

        /*
         ==============
         idVertexCache::Position

         this will be a real pointer with virtual memory,
         but it will be an int offset cast to a pointer with
         ARB_vertex_buffer_object

         The ARB_vertex_buffer_object will be bound
         ==============
         */
        // This will be a real pointer with virtual memory,
        // but it will be an int offset cast to a pointer of ARB_vertex_buffer_object
        fun Position(buffer: vertCache_s?): ByteBuffer? {
            if (null == buffer || buffer.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache::Position: bad vertCache_t")
            }

            // the ARB vertex object just uses an offset
            if (buffer.vbo != 0) {
                if (r_showVertexCache.GetInteger() == 2) {
                    if (buffer.tag == vertBlockTag_t.TAG_TEMP) {
                        Common.common.Printf(
                            "GL_ARRAY_BUFFER_ARB = %d + %d (%d bytes)\n",
                            buffer.vbo,
                            buffer.offset,
                            buffer.size
                        )
                    } else {
                        Common.common.Printf("GL_ARRAY_BUFFER_ARB = %d (%d bytes)\n", buffer.vbo, buffer.size)
                    }
                }
                if (buffer.indexBuffer) {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, buffer.vbo)
                } else {
                    qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, buffer.vbo)
                }
                return ByteBuffer.allocate(Integer.BYTES).putInt(buffer.offset).flip()
            }

            // virtual memory is a real pointer
            return buffer.virtMem.position(buffer.offset).flip()
        }

        // if r_useIndexBuffers is enabled, but you need to draw something without
        // an indexCache, this must be called to reset GL_ELEMENT_ARRAY_BUFFER_ARB
        fun UnbindIndex() {
            qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, 0)
        }

        /*
         ===========
         idVertexCache::AllocFrameTemp

         A frame temp allocation must never be allowed to fail due to overflow.
         We can't simply sync with the GPU and overwrite what we have, because
         there may still be future references to dynamically created surfaces.
         ===========
         */
        // automatically freed at the end of the next frame
        // used for specular texture coordinates and gui drawing, which
        // will change every frame.
        // will return NULL if the vertex cache is completely full
        // As with Position(), this may not actually be a pointer you can access.
        fun AllocFrameTemp(data: ByteBuffer?, size: Int): vertCache_s? {
            var block: vertCache_s? = null
            if (size <= 0) {
                Common.common.Error("idVertexCache::AllocFrameTemp: size = %d\n", size)
            }
            if (dynamicAllocThisFrame + size > frameBytes) {
                // if we don't have enough room in the temp block, allocate a static block,
                // but immediately free it so it will get freed at the next frame
                tempOverflow = true
                block = Alloc(data, size)
                Free(block)
                return block
            }

            // this data is just going on the shared dynamic list
            // if we don't have any remaining unused headers, allocate some more
            if (freeDynamicHeaders.next === freeDynamicHeaders) {
                for (i in 0 until VertexCache.EXPAND_HEADERS) {
                    block = vertCache_s() // headerAllocator.Alloc();
                    block.next = freeDynamicHeaders.next
                    block.prev = freeDynamicHeaders
                    block.next.prev = block
                    block.prev.next = block
                }
            }

            // move it from the freeDynamicHeaders list to the dynamicHeaders list
            block = freeDynamicHeaders.next
            block.next.prev = block.prev
            block.prev.next = block.next
            block.next = dynamicHeaders.next
            block.prev = dynamicHeaders
            block.next.prev = block
            block.prev.next = block
            block.size = size
            block.tag = vertBlockTag_t.TAG_TEMP
            block.indexBuffer = false
            block.offset = dynamicAllocThisFrame
            dynamicAllocThisFrame += block.size
            dynamicCountThisFrame++
            block.user = null
            block.frameUsed = 0

            // copy the data
            block.virtMem = tempBuffers.get(listNum).virtMem
            block.vbo = tempBuffers.get(listNum).vbo
            if (block.vbo != 0) {
//                GL30.glBindVertexArray(block.vao);
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, block.vbo)
                qgl.qglBufferSubDataARB(
                    ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,
                    block.offset.toLong(),  /*(GLsizeiptrARB)*/
                    size.toLong(),
                    data
                )
                //                GL15.glBufferData(GL_ARRAY_BUFFER, DrawVert.toByteBuffer(data), GL15.GL_STATIC_DRAW);
//                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
            } else {
                Simd.SIMDProcessor.Memcpy(block.virtMem.position(block.offset), data, size)
            }
            return block
        }

        fun AllocFrameTemp(data: Array<idDrawVert?>?, size: Int): vertCache_s? {
            return AllocFrameTemp(DrawVert.toByteBuffer(data), size)
        }

        fun AllocFrameTemp(data: Array<idVec3>?, size: Int): vertCache_s? {
            return AllocFrameTemp(idVec3.Companion.toByteBuffer(data), size)
        }

        fun AllocFrameTemp(data: Array<idVec4>?, size: Int): vertCache_s? {
            return AllocFrameTemp(idVec4.Companion.toByteBuffer(data), size)
        }

        // notes that a buffer is used this frame, so it can't be purged
        // out from under the GPU
        fun Touch(block: vertCache_s?) {
            if (null == block) {
                Common.common.Error("idVertexCache Touch: NULL pointer")
            }
            if (block.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache Touch: freed pointer")
            }
            if (block.tag == vertBlockTag_t.TAG_TEMP) {
                Common.common.FatalError("idVertexCache Touch: temporary pointer")
            }
            block.frameUsed = currentFrame

            // move to the head of the LRU list
            block.next.prev = block.prev
            block.prev.next = block.next
            block.next = staticHeaders.next
            block.prev = staticHeaders
            staticHeaders.next.prev = block
            staticHeaders.next = block
        }

        // this block won't have to zero a buffer pointer when it is purged,
        // but it must still wait for the frames to pass, in case the GPU
        // is still referencing it
        fun Free(block: vertCache_s?) {
            if (null == block) {
                return
            }
            if (block.tag == vertBlockTag_t.TAG_FREE) {
                Common.common.FatalError("idVertexCache Free: freed pointer")
            }
            if (block.tag == vertBlockTag_t.TAG_TEMP) {
                Common.common.FatalError("idVertexCache Free: temporary pointer")
            }

            // this block still can't be purged until the frame count has expired,
            // but it won't need to clear a user pointer when it is
            block.user = null
            block.next.prev = block.prev
            block.prev.next = block.next
            block.next = deferredFreeList.next
            block.prev = deferredFreeList
            deferredFreeList.next.prev = block
            deferredFreeList.next = block
        }

        // updates the counter for determining which temp space to use
        // and which blocks can be purged
        // Also prints debugging info when enabled
        fun EndFrame() {
            // display debug information
            if (r_showVertexCache.GetBool()) {
                var staticUseCount = 0
                var staticUseSize = 0
                var block = staticHeaders.next
                while (block !== staticHeaders) {
                    if (block.frameUsed == currentFrame) {
                        staticUseCount++
                        staticUseSize += block.size
                    }
                    block = block.next
                }
                val frameOverflow = if (tempOverflow) "(OVERFLOW)" else ""
                Common.common.Printf(
                    "vertex dynamic:%d=%dk%s, static alloc:%d=%dk used:%d=%dk total:%d=%dk\n",
                    dynamicCountThisFrame, dynamicAllocThisFrame / 1024, frameOverflow,
                    staticCountThisFrame, staticAllocThisFrame / 1024,
                    staticUseCount, staticUseSize / 1024,
                    staticCountTotal, staticAllocTotal / 1024
                )
            }

//if (false){
//	// if our total static count is above our working memory limit, start purging things
//	while ( staticAllocTotal > r_vertexBufferMegs.GetInteger() * 1024 * 1024 ) {
//		// free the least recently used
//
//	}
//}
            if (!virtualMemory) {
                // unbind vertex buffers so normal virtual memory will be used in case
                // r_useVertexBuffers / r_useIndexBuffers
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB, 0)
                qgl.qglBindBufferARB(ARBVertexBufferObject.GL_ELEMENT_ARRAY_BUFFER_ARB, 0)
            }
            currentFrame = tr_local.tr.frameCount
            listNum = currentFrame % VertexCache.NUM_VERTEX_FRAMES
            staticAllocThisFrame = 0
            staticCountThisFrame = 0
            dynamicAllocThisFrame = 0
            dynamicCountThisFrame = 0
            tempOverflow = false

            // free all the deferred free headers
            while (deferredFreeList.next !== deferredFreeList) {
                ActuallyFree(deferredFreeList.next)
            }

            // free all the frame temp headers
            val block = dynamicHeaders.next
            if (block !== dynamicHeaders) {
                block.prev = freeDynamicHeaders
                dynamicHeaders.prev.next = freeDynamicHeaders.next
                freeDynamicHeaders.next.prev = dynamicHeaders.prev
                freeDynamicHeaders.next = block
                dynamicHeaders.prev = dynamicHeaders
                dynamicHeaders.next = dynamicHeaders.prev
            }
        }

        // listVertexCache calls this
        fun List() {
            var numActive = 0
            //            int numDeferred = 0;
            var frameStatic = 0
            var totalStatic = 0
            //            int deferredSpace = 0;
            var block: vertCache_s?
            block = staticHeaders.next
            while (block !== staticHeaders) {
                numActive++
                totalStatic += block.size
                if (block.frameUsed == currentFrame) {
                    frameStatic += block.size
                }
                block = block.next
            }
            var numFreeStaticHeaders = 0
            block = freeStaticHeaders.next
            while (block !== freeStaticHeaders) {
                numFreeStaticHeaders++
                block = block.next
            }
            var numFreeDynamicHeaders = 0
            block = freeDynamicHeaders.next
            while (block !== freeDynamicHeaders) {
                numFreeDynamicHeaders++
                block = block.next
            }
            Common.common.Printf("%d megs working set\n", r_vertexBufferMegs.GetInteger())
            Common.common.Printf("%d dynamic temp buffers of %dk\n", VertexCache.NUM_VERTEX_FRAMES, frameBytes / 1024)
            Common.common.Printf("%5d active static headers\n", numActive)
            Common.common.Printf("%5d free static headers\n", numFreeStaticHeaders)
            Common.common.Printf("%5d free dynamic headers\n", numFreeDynamicHeaders)
            if (!virtualMemory) {
                Common.common.Printf("Vertex cache is in ARB_vertex_buffer_object memory (FAST).\n")
            } else {
                Common.common.Printf("Vertex cache is in virtual memory (SLOW)\n")
            }
            if (RenderSystem_init.r_useIndexBuffers.GetBool()) {
                Common.common.Printf("Index buffers are accelerated.\n")
            } else {
                Common.common.Printf("Index buffers are not used.\n")
            }
        }

        //        private void InitMemoryBlocks(int size);
        private fun ActuallyFree(block: vertCache_s?) {
            if (null == block) {
                Common.common.Error("idVertexCache Free: NULL pointer")
            }
            if (block.user != null) {
                // let the owner know we have purged it
                block.user = null
            }

            // temp blocks are in a shared space that won't be freed
            if (block.tag != vertBlockTag_t.TAG_TEMP) {
                staticAllocTotal -= block.size
                staticCountTotal--
                if (block.vbo != 0) {
//                    if (false) {// this isn't really necessary, it will be reused soon enough
//                        // filling with zero length data is the equivalent of freeing
//                        qglBindBufferARB(GL_ARRAY_BUFFER_ARB, block.vbo);
//                        qglBufferDataARB(GL_ARRAY_BUFFER_ARB, 0, 0, GL_DYNAMIC_DRAW_ARB);
//                    }
                } else if (block.virtMem != null) {
//                    Mem_Free(block.virtMem);
                    block.virtMem = null
                }
            }
            block.tag = vertBlockTag_t.TAG_FREE // mark as free

            // unlink stick it back on the free list
            block.next.prev = block.prev
            block.prev.next = block.next
            if (true) {
                // stick it on the front of the free list so it will be reused immediately
                block.next = freeStaticHeaders.next
                block.prev = freeStaticHeaders
                //            } else {
//                // stick it on the back of the free list so it won't be reused soon (just for debugging)
//                block.next = freeStaticHeaders;
//                block.prev = freeStaticHeaders.prev;
            }
            block.next.prev = block
            block.prev.next = block
        }

        companion object {
            private val r_showVertexCache: idCVar? =
                idCVar("r_showVertexCache", "0", CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_RENDERER, "")
            private val r_vertexBufferMegs: idCVar? =
                idCVar("r_vertexBufferMegs", "32", CVarSystem.CVAR_INTEGER or CVarSystem.CVAR_RENDERER, "")
        }

        //
        //
        init {
            freeStaticHeaders = vertCache_s()
            freeDynamicHeaders = vertCache_s()
            dynamicHeaders = vertCache_s()
            deferredFreeList = vertCache_s()
            staticHeaders = vertCache_s()
            tempBuffers = arrayOfNulls<vertCache_s?>(VertexCache.NUM_VERTEX_FRAMES)
        }
    }
}