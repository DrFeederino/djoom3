package neo.idlib.hashing

import java.nio.ByteBuffer

/**
 *
 */
object MD5 {
    private const val MD5 = false

    /*
     ===============
     MD5_BlockChecksum
     ===============
     */
    fun MD5_BlockChecksum(data: ByteArray, length: Int): String {
        return MD4.BlockChecksum(ByteBuffer.wrap(data), length, MD5)
    }

    fun MD5_BlockChecksum(data: String, length: Int): String {
        return MD5_BlockChecksum(data.toByteArray(), length)
    }
}