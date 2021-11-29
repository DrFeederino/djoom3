package neo.idlib.geometry

import neo.TempDump.SERiAL
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import org.lwjgl.BufferUtils
import java.nio.*

/**
 *
 */
object DrawVert {
    fun toByteBuffer(verts: Array<idDrawVert?>): ByteBuffer {
        val data = BufferUtils.createByteBuffer(idDrawVert.BYTES * verts.size)
        for (vert in verts) {
            if (vert != null) {
                data.put(vert.Write().rewind())
            }
        }
        //        System.out.printf("%d %d %d %d\n", data.get(0) & 0xff, data.get(1) & 0xff, data.get(2) & 0xff, data.get(3) & 0xff);
//        System.out.printf("%d %d %d %d\n", data.get(4) & 0xff, data.get(5) & 0xff, data.get(6) & 0xff, data.get(7) & 0xff);
//        System.out.printf("%f %f %f %f\n", data.getFloat(0), data.getFloat(4), data.getFloat(8), data.getFloat(12));
        return data.flip()
    }

    /*
     ===============================================================================

     Draw Vertex.

     ===============================================================================
     */
    class idDrawVert : SERiAL {
        private val DBG_count = DBG_counter++
        val color: ByteArray = ByteArray(4)
        val normal: idVec3
        val st: idVec2
        val tangents: Array<idVec3>
        val xyz: idVec3

        @Transient
        private var VBO_OFFSET = 0

        constructor() {
            xyz = idVec3()
            st = idVec2()
            normal = idVec3()
            tangents = idVec3.generateArray(2)
        }

        /**
         * copy constructor
         *
         * @param dv
         */
        constructor(dv: idDrawVert?) {
            if (null == dv) {
                xyz = idVec3()
                st = idVec2()
                normal = idVec3()
                tangents = idVec3.Companion.generateArray(2)
                return
            }
            xyz = idVec3(dv.xyz)
            st = idVec2(dv.st)
            normal = idVec3(dv.normal)
            tangents = arrayOf(idVec3(dv.tangents.get(0)), idVec3(dv.tangents.get(1)))
        }

        /**
         * cast constructor
         *
         * @param buffer
         */
        constructor(buffer: ByteBuffer?) : this() {
            Read(buffer)
        }

        fun oSet(dv: idDrawVert?) {
            xyz.oSet(dv.xyz)
            st.oSet(dv.st)
            normal.oSet(dv.normal)
            tangents.get(0).oSet(dv.tangents.get(0))
            tangents.get(1).oSet(dv.tangents.get(1))
        }

        fun oGet(index: Int): Float {
            when (index) {
                0, 1, 2 -> return xyz.oGet(index)
                3, 4 -> return st.oGet(index - 3)
                5, 6, 7 -> return normal.oGet(index - 5)
                8, 9, 10 -> return tangents.get(0).oGet(index - 8)
                11, 12, 13 -> return tangents.get(1).oGet(index - 11)
                14, 15, 16, 17 -> return color.get(index - 14)
            }
            return -1
        }

        fun Clear() {
            xyz.Zero()
            st.Zero()
            normal.Zero()
            tangents.get(0).Zero()
            tangents.get(1).Zero()
            color.get(3) = 0
            color.get(2) = color.get(3)
            color.get(1) = color.get(2)
            color.get(0) = color.get(1)
        }

        fun Lerp(a: idDrawVert?, b: idDrawVert?, f: Float) {
            xyz.oSet(a.xyz.oPlus(b.xyz.oMinus(a.xyz).oMultiply(f)))
            st.oSet(a.st.oPlus(b.st.oMinus(a.st).oMultiply(f)))
        }

        fun LerpAll(a: idDrawVert?, b: idDrawVert?, f: Float) {
            xyz.oSet(a.xyz.oPlus(b.xyz.oMinus(a.xyz).oMultiply(f)))
            st.oSet(a.st.oPlus(b.st.oMinus(a.st).oMultiply(f)))
            normal.oSet(a.normal.oPlus(b.normal.oMinus(a.normal).oMultiply(f)))
            tangents.get(0).oSet(a.tangents.get(0).oPlus(b.tangents.get(0).oMinus(a.tangents.get(0)).oMultiply(f)))
            tangents.get(1).oSet(a.tangents.get(1).oPlus(b.tangents.get(1).oMinus(a.tangents.get(1)).oMultiply(f)))
            color.get(0) = (a.color.get(0) + f * (b.color.get(0) - a.color.get(0))).toInt().toByte()
            color.get(1) = (a.color.get(1) + f * (b.color.get(1) - a.color.get(1))).toInt().toByte()
            color.get(2) = (a.color.get(2) + f * (b.color.get(2) - a.color.get(2))).toInt().toByte()
            color.get(3) = (a.color.get(3) + f * (b.color.get(3) - a.color.get(3))).toInt().toByte()
        }

        fun Normalize() {
            normal.Normalize()
            tangents.get(1).Cross(normal, tangents.get(0))
            tangents.get(1).Normalize()
            tangents.get(0).Cross(tangents.get(1), normal)
            tangents.get(0).Normalize()
        }

        fun SetColor(color: Long) {
//	*reinterpret_cast<dword *>(this->color) = color;
//            this.color = this.set_reinterpret_cast(color);
            val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
            buffer.putLong(color)
            this.color = buffer.array()
        }

        fun GetColor(): Long {
            return get_reinterpret_cast()
        }

        private fun get_reinterpret_cast(): Long {
            return color.get(0) and 0x000000FF or (color.get(1) and 0x0000FF00
                    ) or (color.get(2) and 0x00FF0000
                    ) or (color.get(3) and -0x1000000)
        }

        private fun set_reinterpret_cast(color: Long): ShortArray? {
            return shortArrayOf(
                (color and 0x000000FF).toShort(),
                (color and 0x0000FF00).toShort(),
                (color and 0x00FF0000).toShort(),
                (color and -0x1000000).toShort()
            )
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            if (null == buffer) {
                return
            }
            if (buffer.capacity() == Integer.SIZE / java.lang.Byte.SIZE) {
                VBO_OFFSET = buffer.getInt(0)
                return
            }
            xyz.oSet(0, buffer.float)
            xyz.oSet(1, buffer.float)
            xyz.oSet(2, buffer.float)
            st.oSet(0, buffer.float)
            st.oSet(1, buffer.float)
            normal.oSet(0, buffer.float)
            normal.oSet(1, buffer.float)
            normal.oSet(2, buffer.float)
            for (tan in tangents) {
                tan.oSet(0, buffer.float)
                tan.oSet(1, buffer.float)
                tan.oSet(2, buffer.float)
            }
            for (c in color.indices) {
                color.get(c) = buffer.get()
            }
        }

        override fun Write(): ByteBuffer {
            val data = ByteBuffer.allocate(BYTES)
            data.order(ByteOrder.LITTLE_ENDIAN) //very importante.
            data.putFloat(xyz.oGet(0))
            data.putFloat(xyz.oGet(1))
            data.putFloat(xyz.oGet(2))
            data.putFloat(st.oGet(0))
            data.putFloat(st.oGet(1))
            data.putFloat(normal.oGet(0))
            data.putFloat(normal.oGet(1))
            data.putFloat(normal.oGet(2))
            for (tan in tangents) {
                data.putFloat(tan.oGet(0))
                data.putFloat(tan.oGet(1))
                data.putFloat(tan.oGet(2))
            }
            for (colour in color) {
                data.put(Math.abs(colour.toByte().toInt()).toByte())
            }
            return data
        }

        fun xyzOffset(): Int {
            return VBO_OFFSET
        }

        fun stOffset(): Int {
            return xyzOffset() + idVec3.Companion.BYTES //+xyz
        }

        fun normalOffset(): Int {
            return stOffset() + idVec2.Companion.BYTES //+xyz+st
        }

        fun tangentsOffset_0(): Int {
            return normalOffset() + idVec3.Companion.BYTES //+xyz+st+normal
        }

        fun tangentsOffset_1(): Int {
            return tangentsOffset_0() + idVec3.Companion.BYTES //+xyz+st+normal
        }

        fun colorOffset(): Int {
            return tangentsOffset_1() + idVec3.Companion.BYTES //+xyz+st+normal+tangents
        }

        companion object {
            @Transient
            val SIZE: Int = (idVec3.Companion.SIZE
                    + idVec2.Companion.SIZE
                    + idVec3.Companion.SIZE
                    + 2 * idVec3.Companion.SIZE
                    + 4 * java.lang.Byte.SIZE) //color

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE

            ////#if 0 // was MACOS_X see comments concerning DRAWVERT_PADDED in Simd_Altivec.h
            ////	float			padding;
            ////#endif
            //public	float			operator[]( const int index ) const;
            //public	float &			operator[]( const int index );
            private var DBG_counter = 0
        }
    }
}