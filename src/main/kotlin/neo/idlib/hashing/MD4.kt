package neo.idlib.hashing

import neo.idlib.Lib.idException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object MD4 {
    private const val MD4 = true

    /*
     ===============================================================================

     Calculates a checksum for a block of data
     using the MD4 message-digest algorithm.

     ===============================================================================
     */
    fun MD4_BlockChecksum(data: ByteBuffer?, length: Int): String? {
        return neo.idlib.hashing.MD4.BlockChecksum(data, length, neo.idlib.hashing.MD4.MD4)
    }

    fun MD4_BlockChecksum(data: IntArray?, length: Int): String? {
        val buffer = ByteBuffer.allocate(data.size * 4)
        buffer.asIntBuffer().put(data)
        return neo.idlib.hashing.MD4.BlockChecksum(buffer, length, neo.idlib.hashing.MD4.MD4)
    }

    fun MD4_BlockChecksum(data: ByteArray?, length: Int): String? {
        return neo.idlib.hashing.MD4.BlockChecksum(
            ByteBuffer.wrap(data),
            length,
            neo.idlib.hashing.MD4.MD4
        ) //TODO: make sure checksums match the original c++ rsa version.
    }

    fun BlockChecksum(data: ByteBuffer?, length: Int, MD4: Boolean): String? {
        var hash = 0
        hash = try {
            val currentPosition = data.position()
            val messageDigest = if (MD4) sun.security.provider.MD4.getInstance() else MessageDigest.getInstance("MD5")
            messageDigest.update(data)
            data.position(currentPosition)
            val digest = ByteBuffer.wrap(messageDigest.digest())
            digest.order(ByteOrder.LITTLE_ENDIAN)
            val digestInt = digest.int
            digestInt xor digestInt xor digestInt xor digestInt
        } catch (ex: NoSuchAlgorithmException) {
            throw idException(ex)
        }
        return Integer.toUnsignedString(hash)
    }
}