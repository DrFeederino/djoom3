package neo.Tools.Compilers.RoqVQ

/**
 *
 */
object GDefs {
    const val BIEMULT = 0.50000f
    const val BMULT = 0.1140f
    const val BQEMULT = -0.08131f
    const val GIEMULT = -0.33126f
    const val GMULT = 0.5870f
    const val GQEMULT = -0.41869f

    //
    const val RIEMULT = -0.16874f

    //
    //
    const val RMULT = 0.2990f // use these for televisions
    const val RQEMULT = 0.50000f

    /*==================*
     * TYPE DEFINITIONS *
     *==================*/
    // typedef unsigned char byte;
    // typedef unsigned short word;
    //#define	dabs(a) (((a)<0) ? -(a) : (a))
    //#define CLAMP(v,l,h) ((v)<(l) ? (l) : (v)>(h) ? (h) : v)
    //#define	xswap(a,b) { a^=b; b^=a; a^=b; }
    //#define lum(a) ( 0.2990*(a>>16) + 0.5870*((a>>8)&0xff) + 0.1140*(a&0xff) )
    //#define gsign(a)  	((a) < 0 ? -1 : 1)
    //#define mnint(a)	((a) < 0 ? (int)(a - 0.5) : (int)(a + 0.5))
    //#define mmax(a, b)  	((a) > (b) ? (a) : (b))
    //#define mmin(a, b)  	((a) < (b) ? (a) : (b))
    //#define RGBDIST( src0, src1 ) ( ((src0[0]-src1[0])*(src0[0]-src1[0])) + \
    //								((src0[1]-src1[1])*(src0[1]-src1[1])) + \
    //								((src0[2]-src1[2])*(src0[2]-src1[2])) )
    fun RGBDIST(src0: IntArray, src1: IntArray, i0: Int, i1: Int): Int {
        return (src0[i0 + 0] - src1[i1 + 0]) * (src0[i0 + 0] - src1[i1 + 0]) + (src0[i0 + 1] - src1[i1 + 1]) * (src0[i0 + 1] - src1[i1 + 1]) + (src0[i0 + 2] - src1[i1 + 2]) * (src0[i0 + 2] - src1[i1 + 2])
    }

    //
    //#define RGBADIST( src0, src1 ) ( ((src0[0]-src1[0])*(src0[0]-src1[0])) + \
    //								 ((src0[1]-src1[1])*(src0[1]-src1[1])) + \
    //								 ((src0[2]-src1[2])*(src0[2]-src1[2])) + \
    //								 ((src0[3]-src1[3])*(src0[3]-src1[3])) )
    fun RGBADIST(src0: ByteArray, src1: ByteArray, i0: Int, i1: Int): Int {
        return (src0[i0 + 0] - src1[i1 + 0]) * (src0[i0 + 0] - src1[i1 + 0]) + (src0[i0 + 1] - src1[i1 + 1]) * (src0[i0 + 1] - src1[i1 + 1]) + (src0[i0 + 2] - src1[i1 + 2]) * (src0[i0 + 2] - src1[i1 + 2]) + (src0[i0 + 3] - src1[i1 + 3]) * (src0[i0 + 3] - src1[i1 + 3])
    }
}