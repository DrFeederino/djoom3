package neo.Tools.Compilers.RoqVQ;

/**
 *
 */
public class QuadDefs {

    static final int CCC = 2;
    //
    static final int CCCBITMAP = 0;
    //
    static final int COLA = 0;
    static final int COLB = 1;
    static final int COLC = 2;
    static final int COLPATA = 4;
    static final int COLPATB = 5;
    static final int COLPATS = 6;
    static final int COLS = 3;
    static final int DEAD = 6;
    static final int DEP = 0;
    static final int FCC = 1;
    static final int FCCDOMAIN = 1;
    static final int GENERATION = 7;
    //
    static final int MAXSIZE = 16;
    static final int MINSIZE = 4;
    static final int MOT = 5;
    static final int PAT = 4;
    static final int PATNUMBE2 = 3;
    static final int PATNUMBE3 = 4;
    static final int PATNUMBE4 = 5;
    static final int PATNUMBE5 = 6;
    static final int PATNUMBER = 2;
    //
    static final int RoQ_ID = 0x1084;
    static final int RoQ_PUZZLE_QUAD = 0x1003;
    static final int RoQ_QUAD = 0x1000;
    static final int RoQ_QUAD_CODEBOOK = 0x1002;
    static final int RoQ_QUAD_HANG = 0x1013;
    static final int RoQ_QUAD_INFO = 0x1001;
    static final int RoQ_QUAD_JPEG = 0x1012;
    static final int RoQ_QUAD_SMALL = 0x1010;
    static final int RoQ_QUAD_VQ = 0x1011;
    static final int SLD = 3;

    static class shortQuadCel {

        byte size;                                      //  32, 16, 8, or 4
        int/*word*/ xat;                // where is it at on the screen
        int/*word*/ yat;                //
    }

    static class quadcel {

        long/*unsigned int*/ bitmap;            // ccc bitmap
        //
        float cccsnr;                                   // ccc bitmap snr to actual image
        //
        long/*unsigned int*/ cola;            // color a for ccc
        long/*unsigned int*/ colb;            // color b for ccc
        long/*unsigned int*/ colc;            // color b for ccc
        long/*unsigned int*/ colpata;
        long/*unsigned int*/ colpatb;
        long/*unsigned int*/ colpats;
        float dctsnr;
        //
        int/*word*/          domain;                // where to copy from for fcc
        float fccsnr;                                   // fcc bitmap snr to actual image
        boolean mark;
        float motsnr;                                   // delta snr to previous image
        float patsnr;
        int/*word*/[] patten = new int[5];        // which pattern
        float rsnr;                                     // what's the current snr
        byte size;                                      //  32, 16, 8, or 4
        long/*unsigned int*/ sldcol;            // sold color
        float sldsnr;                                   // solid color snr
        float[] snr = new float[DEAD + 1];        // snrssss
        //
        int status;
        int/*word*/ xat;                // where is it at on the screen
        int/*word*/ yat;                //
    }

    static class dataQuadCel {

        long/*unsigned int*/[] bitmaps = new long[7];    // ccc bitmap
        long/*unsigned int*/[] cols = new long[8];
        float[] snr = new float[DEAD + 1];        // snrssss
    }

    static class norm {

        /*unsigned short*/ int index;
        float normal;
    }

    static class dtlCel {
        int[] a = new int[4];
        int[] b = new int[4];
        /*unsigned*/ char[] dtlMap = new char[256];
        int[] g = new int[4];
        int[] r = new int[4];
        float ymean;
    }

    static class pPixel {

        byte r, g, b, a;
    }

}
