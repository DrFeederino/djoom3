package neo.idlib.hashing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static neo.idlib.Lib.idException;

public class MD4 {

    private static final boolean MD4 = true;

    /*
     ===============================================================================

     Calculates a checksum for a block of data
     using the MD4 message-digest algorithm.

     ===============================================================================
     */
    public static String MD4_BlockChecksum(final ByteBuffer data, int length) {

        return BlockChecksum(data, length, MD4);
    }

    public static String MD4_BlockChecksum(final int[] data, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 4);
        buffer.asIntBuffer().put(data);

        return BlockChecksum(buffer, length, MD4);
    }

    public static String MD4_BlockChecksum(final byte[] data, int length) {
        return BlockChecksum(ByteBuffer.wrap(data), length, MD4); //TODO: make sure checksums match the original c++ rsa version.
    }

    static String BlockChecksum(final ByteBuffer data, final int length, final boolean MD4) {
        int hash = 0;
        try {
            final int currentPosition = data.position();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");

            messageDigest.update(data);

            data.position(currentPosition);

            ByteBuffer digest = ByteBuffer.wrap(messageDigest.digest());
            digest.order(ByteOrder.LITTLE_ENDIAN);
            int digestInt = digest.getInt();
            hash = digestInt ^ digestInt ^ digestInt ^ digestInt;

        } catch (NoSuchAlgorithmException ex) {
            throw new idException(ex);
        }

        return Integer.toUnsignedString(hash);
    }
}
