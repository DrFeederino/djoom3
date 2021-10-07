package neo.Renderer;

import static org.lwjgl.opengl.EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
import static org.lwjgl.opengl.EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT;

/**
 *
 */
public class Image_load {

    /*
     PROBLEM: compressed textures may break the zero clamp rule!
     */
    static boolean FormatIsDXT(int internalFormat) {
        return internalFormat >= GL_COMPRESSED_RGB_S3TC_DXT1_EXT
                && internalFormat <= GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
    }

    static int MakePowerOfTwo(int num) {
        int pot;
        for (pot = 1; pot < num; pot <<= 1) {
        }
        return pot;
    }
}
