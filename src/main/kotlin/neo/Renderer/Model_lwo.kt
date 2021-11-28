package neo.Renderer

import neo.TempDump
import neo.TempDump.CPP_class.Char
import neo.TempDump.NiLLABLE
import neo.TempDump.TODO_Exception
import neo.framework.FileSystem_h
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.idlib.Lib
import neo.idlib.containers.List.cmp_t
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import java.nio.*
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object Model_lwo {
    /*
     ======================================================================

     LWO2 loader. (LightWave Object)

     Ernie Wright  17 Sep 00

     ======================================================================
     */
    const val BEH_CONSTANT = 1
    const val BEH_LINEAR = 5
    const val BEH_OFFSET = 4
    const val BEH_OSCILLATE = 3
    const val BEH_REPEAT = 2
    const val BEH_RESET = 0

    /*
     ======================================================================
     flen

     This accumulates a count of the number of bytes read.  Callers can set
     it at the beginning of a sequence of reads and then retrieve it to get
     the number of bytes actually read.  If one of the I/O functions fails,
     flen is set to an error code, after which the I/O functions ignore
     read requests until flen is reset.
     ====================================================================== */
    const val FLEN_ERROR = -9999

    //    public static final int ID_VMAP = ('V' << 24 | 'M' << 16 | 'A' << 8 | 'P');
    const val ID_AAST = 'A'.code shl 24 or ('A'.code shl 16) or ('S'.code shl 8) or 'T'.code
    const val ID_ADTR = 'A'.code shl 24 or ('D'.code shl 16) or ('T'.code shl 8) or 'R'.code
    const val ID_ALPH = 'A'.code shl 24 or ('L'.code shl 16) or ('P'.code shl 8) or 'H'.code
    const val ID_ANIM = 'A'.code shl 24 or ('N'.code shl 16) or ('I'.code shl 8) or 'M'.code
    const val ID_AVAL = 'A'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_AXIS = 'A'.code shl 24 or ('X'.code shl 16) or ('I'.code shl 8) or 'S'.code
    const val ID_BBOX = 'B'.code shl 24 or ('B'.code shl 16) or ('O'.code shl 8) or 'X'.code
    const val ID_BEZ2 = 'B'.code shl 24 or ('E'.code shl 16) or ('Z'.code shl 8) or '2'.code
    const val ID_BEZI = 'B'.code shl 24 or ('E'.code shl 16) or ('Z'.code shl 8) or 'I'.code
    const val ID_BLOK = 'B'.code shl 24 or ('L'.code shl 16) or ('O'.code shl 8) or 'K'.code
    const val ID_BONE = 'B'.code shl 24 or ('O'.code shl 16) or ('N'.code shl 8) or 'E'.code
    const val ID_BRIT = 'B'.code shl 24 or ('R'.code shl 16) or ('I'.code shl 8) or 'T'.code
    const val ID_BUMP = 'B'.code shl 24 or ('U'.code shl 16) or ('M'.code shl 8) or 'P'.code
    const val ID_CHAN = 'C'.code shl 24 or ('H'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_CLIP = 'C'.code shl 24 or ('L'.code shl 16) or ('I'.code shl 8) or 'P'.code
    const val ID_CLRF = 'C'.code shl 24 or ('L'.code shl 16) or ('R'.code shl 8) or 'F'.code
    const val ID_CLRH = 'C'.code shl 24 or ('L'.code shl 16) or ('R'.code shl 8) or 'H'.code
    const val ID_CNTR = 'C'.code shl 24 or ('N'.code shl 16) or ('T'.code shl 8) or 'R'.code

    //
    /* surfaces */
    const val ID_COLR = 'C'.code shl 24 or ('O'.code shl 16) or ('L'.code shl 8) or 'R'.code
    const val ID_CONT = 'C'.code shl 24 or ('O'.code shl 16) or ('N'.code shl 8) or 'T'.code
    const val ID_CSYS = 'C'.code shl 24 or ('S'.code shl 16) or ('Y'.code shl 8) or 'S'.code
    const val ID_CURV = 'C'.code shl 24 or ('U'.code shl 16) or ('R'.code shl 8) or 'V'.code
    const val ID_DATA = 'D'.code shl 24 or ('A'.code shl 16) or ('T'.code shl 8) or 'A'.code
    const val ID_DESC = 'D'.code shl 24 or ('E'.code shl 16) or ('S'.code shl 8) or 'C'.code
    const val ID_DIFF = 'D'.code shl 24 or ('I'.code shl 16) or ('F'.code shl 8) or 'F'.code
    const val ID_ENAB = 'E'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'B'.code
    const val ID_ENVL = 'E'.code shl 24 or ('N'.code shl 16) or ('V'.code shl 8) or 'L'.code
    const val ID_ETPS = 'E'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code

    //
    /* polygon types */
    const val ID_FACE = 'F'.code shl 24 or ('A'.code shl 16) or ('C'.code shl 8) or 'E'.code
    const val ID_FALL = 'F'.code shl 24 or ('A'.code shl 16) or ('L'.code shl 8) or 'L'.code
    const val ID_FKEY = 'F'.code shl 24 or ('K'.code shl 16) or ('E'.code shl 8) or 'Y'.code
    const val ID_FLAG = 'F'.code shl 24 or ('L'.code shl 16) or ('A'.code shl 8) or 'G'.code

    //
    const val ID_FORM = 'F'.code shl 24 or ('O'.code shl 16) or ('R'.code shl 8) or 'M'.code
    const val ID_FTPS = 'F'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code
    const val ID_FUNC = 'F'.code shl 24 or ('U'.code shl 16) or ('N'.code shl 8) or 'C'.code
    const val ID_GAMM = 'G'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'M'.code
    const val ID_GLOS = 'G'.code shl 24 or ('L'.code shl 16) or ('O'.code shl 8) or 'S'.code

    //
    /* gradient */
    const val ID_GRAD = 'G'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'D'.code
    const val ID_GREN = 'G'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'N'.code
    const val ID_GRPT = 'G'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'T'.code
    const val ID_GRST = 'G'.code shl 24 or ('R'.code shl 16) or ('S'.code shl 8) or 'T'.code
    const val ID_GVAL = 'G'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_HERM = 'H'.code shl 24 or ('E'.code shl 16) or ('R'.code shl 8) or 'M'.code
    const val ID_HUE = 'H'.code shl 24 or ('U'.code shl 16) or ('E'.code shl 8) or ' '.code
    const val ID_ICON = 'I'.code shl 24 or ('C'.code shl 16) or ('O'.code shl 8) or 'N'.code
    const val ID_IFLT = 'I'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_IKEY = 'I'.code shl 24 or ('K'.code shl 16) or ('E'.code shl 8) or 'Y'.code
    const val ID_IMAG = 'I'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'G'.code

    //
    /* image map */
    const val ID_IMAP = 'I'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_INAM = 'I'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'M'.code
    const val ID_ISEQ = 'I'.code shl 24 or ('S'.code shl 16) or ('E'.code shl 8) or 'Q'.code
    const val ID_ITPS = 'I'.code shl 24 or ('T'.code shl 16) or ('P'.code shl 8) or 'S'.code
    const val ID_KEY = 'K'.code shl 24 or ('E'.code shl 16) or ('Y'.code shl 8) or ' '.code

    //
    /* top-level chunks */
    const val ID_LAYR = 'L'.code shl 24 or ('A'.code shl 16) or ('Y'.code shl 8) or 'R'.code
    const val ID_LINE = 'L'.code shl 24 or ('I'.code shl 16) or ('N'.code shl 8) or 'E'.code

    //    public static final int ID_LINE = ('L' << 24 | 'I' << 16 | 'N' << 8 | 'E');
    const val ID_LSIZ = 'L'.code shl 24 or ('S'.code shl 16) or ('I'.code shl 8) or 'Z'.code
    const val ID_LUMI = 'L'.code shl 24 or ('U'.code shl 16) or ('M'.code shl 8) or 'I'.code
    const val ID_LWO2 = 'L'.code shl 24 or ('W'.code shl 16) or ('O'.code shl 8) or '2'.code
    const val ID_LWOB = 'L'.code shl 24 or ('W'.code shl 16) or ('O'.code shl 8) or 'B'.code
    const val ID_MBAL = 'M'.code shl 24 or ('B'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_NAME = 'N'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'E'.code
    const val ID_NEGA = 'N'.code shl 24 or ('E'.code shl 16) or ('G'.code shl 8) or 'A'.code
    const val ID_OPAC = 'O'.code shl 24 or ('P'.code shl 16) or ('A'.code shl 8) or 'C'.code
    const val ID_OREF = 'O'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code

    //
    /* polygon tags */ //    public static final int ID_SURF = ('S' << 24 | 'U' << 16 | 'R' << 8 | 'F');
    const val ID_PART = 'P'.code shl 24 or ('A'.code shl 16) or ('R'.code shl 8) or 'T'.code
    const val ID_PFLT = 'P'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_PIXB = 'P'.code shl 24 or ('I'.code shl 16) or ('X'.code shl 8) or 'B'.code
    const val ID_PNAM = 'P'.code shl 24 or ('N'.code shl 16) or ('A'.code shl 8) or 'M'.code
    const val ID_PNTS = 'P'.code shl 24 or ('N'.code shl 16) or ('T'.code shl 8) or 'S'.code
    const val ID_POLS = 'P'.code shl 24 or ('O'.code shl 16) or ('L'.code shl 8) or 'S'.code
    const val ID_POST = 'P'.code shl 24 or ('O'.code shl 16) or ('S'.code shl 8) or 'T'.code

    //
    /* envelopes */
    const val ID_PRE = 'P'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or ' '.code

    //
    /* procedural */
    const val ID_PROC = 'P'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'C'.code
    const val ID_PROJ = 'P'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'J'.code
    const val ID_PTAG = 'P'.code shl 24 or ('T'.code shl 16) or ('A'.code shl 8) or 'G'.code
    const val ID_PTCH = 'P'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'H'.code
    const val ID_REFL = 'R'.code shl 24 or ('E'.code shl 16) or ('F'.code shl 8) or 'L'.code
    const val ID_RFOP = 'R'.code shl 24 or ('F'.code shl 16) or ('O'.code shl 8) or 'P'.code
    const val ID_RIMG = 'R'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'G'.code
    const val ID_RIND = 'R'.code shl 24 or ('I'.code shl 16) or ('N'.code shl 8) or 'D'.code
    const val ID_ROTA = 'R'.code shl 24 or ('O'.code shl 16) or ('T'.code shl 8) or 'A'.code
    const val ID_RSAN = 'R'.code shl 24 or ('S'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SATR = 'S'.code shl 24 or ('A'.code shl 16) or ('T'.code shl 8) or 'R'.code

    //
    /* shader */
    const val ID_SHDR = 'S'.code shl 24 or ('H'.code shl 16) or ('D'.code shl 8) or 'R'.code
    const val ID_SHRP = 'S'.code shl 24 or ('H'.code shl 16) or ('R'.code shl 8) or 'P'.code
    const val ID_SIDE = 'S'.code shl 24 or ('I'.code shl 16) or ('D'.code shl 8) or 'E'.code
    const val ID_SIZE = 'S'.code shl 24 or ('I'.code shl 16) or ('Z'.code shl 8) or 'E'.code
    const val ID_SMAN = 'S'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SMGP = 'S'.code shl 24 or ('M'.code shl 16) or ('G'.code shl 8) or 'P'.code
    const val ID_SPAN = 'S'.code shl 24 or ('P'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_SPEC = 'S'.code shl 24 or ('P'.code shl 16) or ('E'.code shl 8) or 'C'.code
    const val ID_STCC = 'S'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'C'.code
    const val ID_STCK = 'S'.code shl 24 or ('T'.code shl 16) or ('C'.code shl 8) or 'K'.code
    const val ID_STEP = 'S'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'P'.code

    //
    /* clips */
    const val ID_STIL = 'S'.code shl 24 or ('T'.code shl 16) or ('I'.code shl 8) or 'L'.code
    const val ID_SURF = 'S'.code shl 24 or ('U'.code shl 16) or ('R'.code shl 8) or 'F'.code
    const val ID_TAGS = 'T'.code shl 24 or ('A'.code shl 16) or ('G'.code shl 8) or 'S'.code
    const val ID_TAMP = 'T'.code shl 24 or ('A'.code shl 16) or ('M'.code shl 8) or 'P'.code
    const val ID_TCB = 'T'.code shl 24 or ('C'.code shl 16) or ('B'.code shl 8) or ' '.code
    const val ID_TEXT = 'T'.code shl 24 or ('E'.code shl 16) or ('X'.code shl 8) or 'T'.code
    const val ID_TIME = 'T'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'E'.code
    const val ID_TIMG = 'T'.code shl 24 or ('I'.code shl 16) or ('M'.code shl 8) or 'G'.code

    //
    /* texture coordinates */
    const val ID_TMAP = 'T'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_TRAN = 'T'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'N'.code
    const val ID_TRNL = 'T'.code shl 24 or ('R'.code shl 16) or ('N'.code shl 8) or 'L'.code
    const val ID_TROP = 'T'.code shl 24 or ('R'.code shl 16) or ('O'.code shl 8) or 'P'.code

    //
    /* texture layer */
    const val ID_TYPE = 'T'.code shl 24 or ('Y'.code shl 16) or ('P'.code shl 8) or 'E'.code

    //    public static final int ID_COLR = ('C' << 24 | 'O' << 16 | 'L' << 8 | 'R');
    const val ID_VALU = 'V'.code shl 24 or ('A'.code shl 16) or ('L'.code shl 8) or 'U'.code
    const val ID_VMAD = 'V'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'D'.code
    const val ID_VMAP = 'V'.code shl 24 or ('M'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_WRAP = 'W'.code shl 24 or ('R'.code shl 16) or ('A'.code shl 8) or 'P'.code
    const val ID_WRPH = 'W'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'H'.code
    const val ID_WRPW = 'W'.code shl 24 or ('R'.code shl 16) or ('P'.code shl 8) or 'W'.code
    const val ID_XREF = 'X'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code
    const val PROJ_CUBIC = 3
    const val PROJ_CYLINDRICAL = 1
    const val PROJ_FRONT = 4
    const val PROJ_PLANAR = 0
    const val PROJ_SPHERICAL = 2
    const val WRAP_EDGE = 1
    const val WRAP_MIRROR = 3

    //
    const val WRAP_NONE = 0
    const val WRAP_REPEAT = 2
    const val ID_BTEX = 'B'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_CTEX = 'C'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_DTEX = 'D'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_LTEX = 'L'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_RFLT = 'R'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'T'.code
    const val ID_RTEX = 'R'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_SDAT = 'S'.code shl 24 or ('D'.code shl 16) or ('A'.code shl 8) or 'T'.code

    /* IDs specific to LWOB */
    const val ID_SRFS = 'S'.code shl 24 or ('R'.code shl 16) or ('F'.code shl 8) or 'S'.code
    const val ID_STEX = 'S'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code

    //    static final int ID_TAMP = ('T' << 24 | 'A' << 16 | 'M' << 8 | 'P');
    //    static final int ID_TIMG = ('T' << 24 | 'I' << 16 | 'M' << 8 | 'G');
    const val ID_TAAS = 'T'.code shl 24 or ('A'.code shl 16) or ('A'.code shl 8) or 'S'.code
    const val ID_TCLR = 'T'.code shl 24 or ('C'.code shl 16) or ('L'.code shl 8) or 'R'.code
    const val ID_TCTR = 'T'.code shl 24 or ('C'.code shl 16) or ('T'.code shl 8) or 'R'.code
    const val ID_TFAL = 'T'.code shl 24 or ('F'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_TFLG = 'T'.code shl 24 or ('F'.code shl 16) or ('L'.code shl 8) or 'G'.code
    const val ID_TFP0 = 'T'.code shl 24 or ('F'.code shl 16) or ('P'.code shl 8) or '0'.code
    const val ID_TFP1 = 'T'.code shl 24 or ('F'.code shl 16) or ('P'.code shl 8) or '1'.code
    const val ID_TOPC = 'T'.code shl 24 or ('O'.code shl 16) or ('P'.code shl 8) or 'C'.code
    const val ID_TREF = 'T'.code shl 24 or ('R'.code shl 16) or ('E'.code shl 8) or 'F'.code
    const val ID_TSIZ = 'T'.code shl 24 or ('S'.code shl 16) or ('I'.code shl 8) or 'Z'.code
    const val ID_TTEX = 'T'.code shl 24 or ('T'.code shl 16) or ('E'.code shl 8) or 'X'.code
    const val ID_TVAL = 'T'.code shl 24 or ('V'.code shl 16) or ('A'.code shl 8) or 'L'.code
    const val ID_TVEL = 'T'.code shl 24 or ('V'.code shl 16) or ('E'.code shl 8) or 'L'.code
    const val ID_VDIF = 'V'.code shl 24 or ('D'.code shl 16) or ('I'.code shl 8) or 'F'.code

    //    static final int ID_FLAG = ('F' << 24 | 'L' << 16 | 'A' << 8 | 'G');
    const val ID_VLUM = 'V'.code shl 24 or ('L'.code shl 16) or ('U'.code shl 8) or 'M'.code
    const val ID_VSPC = 'V'.code shl 24 or ('S'.code shl 16) or ('P'.code shl 8) or 'C'.code
    var flen = 0

    /* chunk and subchunk IDs */
    fun LWID_(a: Char, b: Char, c: Char, d: Char): Int {
        return a.code shl 24 or (b.code shl 16) or (c.code shl 8) or (d.code shl 0)
    }

    /*
     ======================================================================
     lwGetClip()

     Read image references from a CLIP chunk in an LWO2 file.
     ====================================================================== */
    fun lwGetClip(fp: idFile?, cksize: Int): lwClip? {
        val clip: lwClip
        var filt: lwPlugin
        var id: Int
        var sz: Int
        val pos: Int
        var rlen: Int


        /* allocate the Clip structure */Fail@ if (true) {
            clip = lwClip() // Mem_ClearedAlloc(sizeof(lwClip));
            //            if (NOT(clip)) {
//                break Fail;
//            }
            clip.contrast.`val` = 1.0f
            clip.brightness.`val` = 1.0f
            clip.saturation.`val` = 1.0f
            clip.gamma.`val` = 1.0f

            /* remember where we started */Model_lwo.set_flen(0)
            pos = fp.Tell()

            /* index */clip.index = Model_lwo.getI4(fp)

            /* first subchunk header */clip.type = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp).toInt()
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }
            sz += sz and 1
            Model_lwo.set_flen(0)
            when (clip.type) {
                Model_lwo.ID_STIL -> clip.source.still.name = Model_lwo.getS0(fp)
                Model_lwo.ID_ISEQ -> {
                    clip.source.seq.digits = Model_lwo.getU1(fp).code
                    clip.source.seq.flags = Model_lwo.getU1(fp).code
                    clip.source.seq.offset = Model_lwo.getI2(fp).toInt()
                    clip.source.seq.start = Model_lwo.getI2(fp).toInt()
                    clip.source.seq.end = Model_lwo.getI2(fp).toInt()
                    clip.source.seq.prefix = Model_lwo.getS0(fp)
                    clip.source.seq.suffix = Model_lwo.getS0(fp)
                }
                Model_lwo.ID_ANIM -> {
                    clip.source.anim.name = Model_lwo.getS0(fp)
                    clip.source.anim.server = Model_lwo.getS0(fp)
                    rlen = Model_lwo.get_flen()
                    clip.source.anim.data = Model_lwo.getbytes(fp, sz - rlen)
                }
                Model_lwo.ID_XREF -> {
                    clip.source.xref.index = Model_lwo.getI4(fp)
                    clip.source.xref.string = Model_lwo.getS0(fp)
                }
                Model_lwo.ID_STCC -> {
                    clip.source.cycle.lo = Model_lwo.getI2(fp).toInt()
                    clip.source.cycle.hi = Model_lwo.getI2(fp).toInt()
                    clip.source.cycle.name = Model_lwo.getS0(fp)
                }
                else -> {}
            }

            /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                break@Fail
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the CLIP chunk? */rlen = fp.Tell() - pos
            if (cksize < rlen) {
                break@Fail
            }
            if (cksize == rlen) {
                return clip
            }

            /* process subchunks as they're encountered */id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp).toInt()
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }
            while (true) {
                sz += sz and 1
                Model_lwo.set_flen(0)
                when (id) {
                    Model_lwo.ID_TIME -> {
                        clip.start_time = Model_lwo.getF4(fp)
                        clip.duration = Model_lwo.getF4(fp)
                        clip.frame_rate = Model_lwo.getF4(fp)
                    }
                    Model_lwo.ID_CONT -> {
                        clip.contrast.`val` = Model_lwo.getF4(fp)
                        clip.contrast.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_BRIT -> {
                        clip.brightness.`val` = Model_lwo.getF4(fp)
                        clip.brightness.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_SATR -> {
                        clip.saturation.`val` = Model_lwo.getF4(fp)
                        clip.saturation.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_HUE -> {
                        clip.hue.`val` = Model_lwo.getF4(fp)
                        clip.hue.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_GAMM -> {
                        clip.gamma.`val` = Model_lwo.getF4(fp)
                        clip.gamma.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_NEGA -> clip.negative = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_IFLT, Model_lwo.ID_PFLT -> {
                        filt = lwPlugin() // Mem_ClearedAlloc(sizeof(lwPlugin));
                        if (TempDump.NOT(filt)) {
                            break@Fail
                        }
                        filt.name = Model_lwo.getS0(fp)
                        filt.flags = Model_lwo.getU2(fp).toInt()
                        rlen = Model_lwo.get_flen()
                        filt.data = Model_lwo.getbytes(fp, sz - rlen)
                        if (id == Model_lwo.ID_IFLT) {
                            clip.ifilter = Model_lwo.lwListAdd(clip.ifilter, filt) //TODO:check this construction
                            clip.nifilters++
                        } else {
                            clip.ifilter = Model_lwo.lwListAdd(clip.ifilter, filt)
                            clip.npfilters++
                        }
                    }
                    else -> {}
                }

                /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
                if (rlen < 0 || rlen > sz) {
                    break@Fail
                }

                /* skip unread parts of the current subchunk */if (rlen < sz) {
                    fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the CLIP chunk? */rlen = fp.Tell() - pos
                if (cksize < rlen) {
                    break@Fail
                }
                if (cksize == rlen) {
                    break
                }

                /* get the next chunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                sz = Model_lwo.getU2(fp).toInt()
                if (6 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            return clip
        }
        //        Fail:
        lwFreeClip.getInstance().run(clip)
        return null
    }

    /*
     ======================================================================
     lwFindClip()

     Returns an lwClip pointer, given a clip index.
     ====================================================================== */
    fun lwFindClip(list: lwClip?, index: Int): lwClip? {
        var clip: lwClip?
        clip = list
        while (clip != null) {
            if (clip.index == index) {
                break
            }
            clip = clip.next
        }
        return clip
    }

    /*
     ======================================================================
     lwGetEnvelope()

     Read an ENVL chunk from an LWO2 file.
     ====================================================================== */
    fun lwGetEnvelope(fp: idFile?, cksize: Int): lwEnvelope? {
        val env: lwEnvelope
        var key: lwKey? = null
        var plug: lwPlugin
        var id: Int
        var sz: Short
        val f = FloatArray(4)
        var i: Int
        var nparams: Int
        val pos: Int
        var rlen: Int


        /* allocate the Envelope structure */Fail@ if (true) {
            env = lwEnvelope() // Mem_ClearedAlloc(sizeof(lwEnvelope));
            if (TempDump.NOT(env)) {
                break@Fail
            }

            /* remember where we started */Model_lwo.set_flen(0)
            pos = fp.Tell()

            /* index */env.index = Model_lwo.getVX(fp)

            /* first subchunk header */id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }

            /* process subchunks as they're encountered */while (true) {
                sz += (sz and 1).toShort()
                Model_lwo.set_flen(0)
                when (id) {
                    Model_lwo.ID_TYPE -> env.type = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_NAME -> env.name = Model_lwo.getS0(fp)
                    Model_lwo.ID_PRE -> env.behavior.get(0) = Model_lwo.getU2(fp)
                    Model_lwo.ID_POST -> env.behavior.get(1) = Model_lwo.getU2(fp)
                    Model_lwo.ID_KEY -> {
                        key = lwKey() // Mem_ClearedAlloc(sizeof(lwKey));
                        if (TempDump.NOT(key)) { //TODO:unnecessary?
                            break@Fail
                        }
                        key.time = Model_lwo.getF4(fp)
                        key.value = Model_lwo.getF4(fp)
                        Model_lwo.lwListInsert(env.key, key, compare_keys())
                        env.nkeys++
                    }
                    Model_lwo.ID_SPAN -> {
                        if (TempDump.NOT(key)) {
                            break@Fail
                        }
                        key.shape = Model_lwo.getU4(fp).toLong()
                        nparams = (sz - 4) / 4
                        if (nparams > 4) {
                            nparams = 4
                        }
                        i = 0
                        while (i < nparams) {
                            f[i] = Model_lwo.getF4(fp)
                            i++
                        }
                        when (key.shape.toInt()) {
                            Model_lwo.ID_TCB -> {
                                key.tension = f[0]
                                key.continuity = f[1]
                                key.bias = f[2]
                            }
                            Model_lwo.ID_BEZI, Model_lwo.ID_HERM, Model_lwo.ID_BEZ2 -> {
                                i = 0
                                while (i < nparams) {
                                    key.param.get(i) = f[i]
                                    i++
                                }
                            }
                        }
                    }
                    Model_lwo.ID_CHAN -> {
                        plug = lwPlugin() // Mem_ClearedAlloc(sizeof(lwPlugin));
                        if (TempDump.NOT(plug)) {
                            break@Fail
                        }
                        plug.name = Model_lwo.getS0(fp)
                        plug.flags = Model_lwo.getU2(fp).toInt()
                        plug.data = Model_lwo.getbytes(fp, sz - Model_lwo.get_flen())
                        env.cfilter = Model_lwo.lwListAdd(env.cfilter, plug)
                        env.ncfilters++
                    }
                    else -> {}
                }

                /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
                if (rlen < 0 || rlen > sz) {
                    break@Fail
                }

                /* skip unread parts of the current subchunk */if (rlen < sz) {
                    fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the ENVL chunk? */rlen = fp.Tell() - pos
                if (cksize < rlen) {
                    break@Fail
                }
                if (cksize == rlen) {
                    break
                }

                /* get the next subchunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                sz = Model_lwo.getU2(fp)
                if (6 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            return env
        }
        //        Fail:
        lwFreeEnvelope.getInstance().run(env)
        return null
    }

    /*
     ======================================================================
     lwFindEnvelope()

     Returns an lwEnvelope pointer, given an envelope index.
     ====================================================================== */
    fun lwFindEnvelope(list: lwEnvelope?, index: Int): lwEnvelope? {
        var env: lwEnvelope?
        env = list
        while (env != null) {
            if (env.index == index) {
                break
            }
            env = env.next
        }
        return env
    }

    /*
     ======================================================================
     range()

     Given the value v of a periodic function, returns the equivalent value
     v2 in the principal interval [lo, hi].  If i isn't null, it receives
     the number of wavelengths between v and v2.

     v2 = v - i * (hi - lo)

     For example, range( 3 pi, 0, 2 pi, i ) returns pi, with i = 1.
     ====================================================================== */
    fun range(v: Float, lo: Float, hi: Float, i: IntArray?): Float {
        val v2: Float
        val r = hi - lo
        if (r.toDouble() == 0.0) {
            if (i.get(0) != 0) {
                i.get(0) = 0
            }
            return lo
        }
        v2 = lo + v - r * Math.floor(v.toDouble() / r).toFloat()
        if (i.get(0) != 0) {
            i.get(0) = -((v2 - v) / r + if (v2 > v) 0.5 else -0.5).toInt()
        }
        return v2
    }

    /*
     ======================================================================
     hermite()

     Calculate the Hermite coefficients.
     ====================================================================== */
    fun hermite(t: Float, h1: FloatArray?, h2: FloatArray?, h3: FloatArray?, h4: FloatArray?) {
        val t2: Float
        val t3: Float
        t2 = t * t
        t3 = t * t2
        h2.get(0) = 3.0f * t2 - t3 - t3
        h1.get(0) = 1.0f - h2.get(0)
        h4.get(0) = t3 - t2
        h3.get(0) = h4.get(0) - t2 + t
    }

    /*
     ======================================================================
     bezier()

     Interpolate the value of a 1D Bezier curve.
     ====================================================================== */
    fun bezier(x0: Float, x1: Float, x2: Float, x3: Float, t: Float): Float {
        val a: Float
        val b: Float
        val c: Float
        val t2: Float
        val t3: Float
        t2 = t * t
        t3 = t2 * t
        c = 3.0f * (x1 - x0)
        b = 3.0f * (x2 - x1) - c
        a = x3 - x0 - c - b
        return a * t3 + b * t2 + c * t + x0
    }

    /*
     ======================================================================
     bez2_time()

     Find the t for which bezier() returns the input time.  The handle
     endpoints of a BEZ2 curve represent the control points, and these have
     (time, value) coordinates, so time is used as both a coordinate and a
     parameter for this curve type.
     ====================================================================== */
    fun bez2_time(
        x0: Float, x1: Float, x2: Float, x3: Float, time: Float,
        t0: FloatArray?, t1: FloatArray?
    ): Float {
        val v: Float
        val t: Float
        t = t0.get(0) + (t1.get(0) - t0.get(0)) * 0.5f
        v = Model_lwo.bezier(x0, x1, x2, x3, t)
        return if (Math.abs(time - v) > .0001f) {
            if (v > time) {
                t1.get(0) = t
            } else {
                t0.get(0) = t
            }
            Model_lwo.bez2_time(x0, x1, x2, x3, time, t0, t1)
        } else {
            t
        }
    }

    /*
     ======================================================================
     bez2()

     Interpolate the value of a BEZ2 curve.
     ====================================================================== */
    fun bez2(key0: lwKey?, key1: lwKey?, time: Float): Float {
        val x: Float
        val y: Float
        val t: Float
        val t0 = floatArrayOf(0.0f)
        val t1 = floatArrayOf(1.0f)
        x = if (key0.shape == Model_lwo.ID_BEZ2.toLong()) {
            key0.time + key0.param.get(2)
        } else {
            key0.time + (key1.time - key0.time) / 3.0f
        }
        t = Model_lwo.bez2_time(key0.time, x, key1.time + key1.param.get(0), key1.time, time, t0, t1)
        y = if (key0.shape == Model_lwo.ID_BEZ2.toLong()) {
            key0.value + key0.param.get(3)
        } else {
            key0.value + key0.param.get(1) / 3.0f
        }
        return Model_lwo.bezier(key0.value, y, key1.param.get(1) + key1.value, key1.value, t)
    }

    /*
     ======================================================================
     outgoing()

     Return the outgoing tangent to the curve at key0.  The value returned
     for the BEZ2 case is used when extrapolating a linear pre behavior and
     when interpolating a non-BEZ2 span.
     ====================================================================== */
    fun outgoing(key0: lwKey?, key1: lwKey?): Float {
        val a: Float
        val b: Float
        val d: Float
        val t: Float
        var out: Float
        when (key0.shape.toInt()) {
            Model_lwo.ID_TCB -> {
                a = ((1.0f - key0.tension)
                        * (1.0f + key0.continuity)
                        * (1.0f + key0.bias))
                b = ((1.0f - key0.tension)
                        * (1.0f - key0.continuity)
                        * (1.0f - key0.bias))
                d = key1.value - key0.value
                if (key0.prev != null) {
                    t = (key1.time - key0.time) / (key1.time - key0.prev.time)
                    out = t * (a * (key0.value - key0.prev.value) + b * d)
                } else {
                    out = b * d
                }
            }
            Model_lwo.ID_LINE -> {
                d = key1.value - key0.value
                if (key0.prev != null) {
                    t = (key1.time - key0.time) / (key1.time - key0.prev.time)
                    out = t * (key0.value - key0.prev.value + d)
                } else {
                    out = d
                }
            }
            Model_lwo.ID_BEZI, Model_lwo.ID_HERM -> {
                out = key0.param.get(1)
                if (key0.prev != null) {
                    out *= (key1.time - key0.time) / (key1.time - key0.prev.time)
                }
            }
            Model_lwo.ID_BEZ2 -> {
                out = key0.param.get(3) * (key1.time - key0.time)
                if (Math.abs(key0.param.get(2)) > 1e-5f) {
                    out /= key0.param.get(2)
                } else {
                    out *= 1e5f
                }
            }
            Model_lwo.ID_STEP -> out = 0.0f
            else -> out = 0.0f
        }
        return out
    }

    /*
     ======================================================================
     incoming()

     Return the incoming tangent to the curve at key1.  The value returned
     for the BEZ2 case is used when extrapolating a linear post behavior.
     ====================================================================== */
    fun incoming(key0: lwKey?, key1: lwKey?): Float {
        val a: Float
        val b: Float
        val d: Float
        val t: Float
        var `in`: Float
        when (key1.shape.toInt()) {
            Model_lwo.ID_LINE -> {
                d = key1.value - key0.value
                if (key1.next != null) {
                    t = (key1.time - key0.time) / (key1.next.time - key0.time)
                    `in` = t * (key1.next.value - key1.value + d)
                } else {
                    `in` = d
                }
            }
            Model_lwo.ID_TCB -> {
                a = ((1.0f - key1.tension)
                        * (1.0f - key1.continuity)
                        * (1.0f + key1.bias))
                b = ((1.0f - key1.tension)
                        * (1.0f + key1.continuity)
                        * (1.0f - key1.bias))
                d = key1.value - key0.value
                if (key1.next != null) {
                    t = (key1.time - key0.time) / (key1.next.time - key0.time)
                    `in` = t * (b * (key1.next.value - key1.value) + a * d)
                } else {
                    `in` = a * d
                }
            }
            Model_lwo.ID_BEZI, Model_lwo.ID_HERM -> {
                `in` = key1.param.get(0)
                if (key1.next != null) {
                    `in` *= (key1.time - key0.time) / (key1.next.time - key0.time)
                }
                //                break;
                return `in`
            }
            Model_lwo.ID_BEZ2 -> {
                `in` = key1.param.get(1) * (key1.time - key0.time)
                if (Math.abs(key1.param.get(0)) > 1e-5f) {
                    `in` /= key1.param.get(0)
                } else {
                    `in` *= 1e5f
                }
            }
            Model_lwo.ID_STEP -> `in` = 0.0f
            else -> `in` = 0.0f
        }
        return `in`
    }

    /*
     ======================================================================
     evalEnvelope()

     Given a list of keys and a time, returns the interpolated value of the
     envelope at that time.
     ====================================================================== */
    fun evalEnvelope(env: lwEnvelope?, time: Float): Float {
        var time = time
        var key0: lwKey?
        val key1: lwKey?
        val skey: lwKey?
        var ekey: lwKey?
        val t: Float
        val `in`: Float
        val out: Float
        var offset = 0.0f
        val h1 = FloatArray(1)
        val h2 = FloatArray(1)
        val h3 = FloatArray(1)
        val h4 = FloatArray(1)
        val noff = IntArray(1)


        /* if there's no key, the value is 0 */if (env.nkeys == 0) {
            return 0.0f
        }

        /* if there's only one key, the value is constant */if (env.nkeys == 1) {
            return env.key.value
        }

        /* find the first and last keys */ekey = env.key
        skey = ekey
        while (ekey.next != null) {
            ekey = ekey.next
        }

        /* use pre-behavior if time is before first key time */if (time < skey.time) {
            when (env.behavior.get(0)) {
                Model_lwo.BEH_RESET -> return 0.0f
                Model_lwo.BEH_CONSTANT -> return skey.value
                Model_lwo.BEH_REPEAT -> time = Model_lwo.range(time, skey.time, ekey.time, null)
                Model_lwo.BEH_OSCILLATE -> {
                    time = Model_lwo.range(time, skey.time, ekey.time, noff)
                    if (noff[0] % 2 != 0) {
                        time = ekey.time - skey.time - time
                    }
                }
                Model_lwo.BEH_OFFSET -> {
                    time = Model_lwo.range(time, skey.time, ekey.time, noff)
                    offset = noff[0] * (ekey.value - skey.value)
                }
                Model_lwo.BEH_LINEAR -> {
                    out = (Model_lwo.outgoing(skey, skey.next)
                            / (skey.next.time - skey.time))
                    return out * (time - skey.time) + skey.value
                }
            }
        } /* use post-behavior if time is after last key time */ else if (time > ekey.time) {
            when (env.behavior.get(1)) {
                Model_lwo.BEH_RESET -> return 0.0f
                Model_lwo.BEH_CONSTANT -> return ekey.value
                Model_lwo.BEH_REPEAT -> time = Model_lwo.range(time, skey.time, ekey.time, null)
                Model_lwo.BEH_OSCILLATE -> {
                    time = Model_lwo.range(time, skey.time, ekey.time, noff)
                    if (noff[0] % 2 != 0) {
                        time = ekey.time - skey.time - time
                    }
                }
                Model_lwo.BEH_OFFSET -> {
                    time = Model_lwo.range(time, skey.time, ekey.time, noff)
                    offset = noff[0] * (ekey.value - skey.value)
                }
                Model_lwo.BEH_LINEAR -> {
                    `in` = (Model_lwo.incoming(ekey.prev, ekey)
                            / (ekey.time - ekey.prev.time))
                    return `in` * (time - ekey.time) + ekey.value
                }
            }
        }

        /* get the endpoints of the interval being evaluated */key0 = env.key
        while (time > key0.next.time) {
            key0 = key0.next
        }
        key1 = key0.next

        /* check for singularities first */if (time == key0.time) {
            return key0.value + offset
        } else if (time == key1.time) {
            return key1.value + offset
        }

        /* get interval length, time in [0, 1] */t = (time - key0.time) / (key1.time - key0.time)
        return when (key1.shape.toInt()) {
            Model_lwo.ID_TCB, Model_lwo.ID_BEZI, Model_lwo.ID_HERM -> {
                out = Model_lwo.outgoing(key0, key1)
                `in` = Model_lwo.incoming(key0, key1)
                Model_lwo.hermite(t, h1, h2, h3, h4)
                h1[0] * key0.value + h2[0] * key1.value + h3[0] * out + h4[0] * `in` + offset
            }
            Model_lwo.ID_BEZ2 -> Model_lwo.bez2(key0, key1, time) + offset
            Model_lwo.ID_LINE -> key0.value + t * (key1.value - key0.value) + offset
            Model_lwo.ID_STEP -> key0.value + offset
            else -> offset
        }
    }

    /*
     ======================================================================
     lwListFree()

     Free the items in a list.
     ====================================================================== */
    @Deprecated("")
    fun lwListFree(list: Any?, freeNode: LW?) {
        var node: lwNode?
        var next: lwNode?
        node = list as lwNode?
        while (node != null) {
            next = node.getNext()
            freeNode.run(node)
            node = next
        }
    }

    /*
     ======================================================================
     lwListAdd()

     Append a node to a list.
     ====================================================================== */
    fun <T> lwListAdd(list: T?, node: lwNode?): T? {
        var head: lwNode?
        var tail: lwNode? = null
        head = list as lwNode?
        if (null == head) {
            return node as T?
        }
        while (head != null) {
            tail = head
            head = head.getNext()
        }
        tail.setNext(node)
        node.setPrev(tail)
        return list
    }

    //    @Deprecated
    //    public static Object lwListAdd(Object list, Object node) {
    //        lwNode head, tail = new lwNode();
    //
    //        head = (lwNode) list;
    //        if (null == head) {
    //            return node;
    //        }
    //        while (head != null) {
    //            tail = head;
    //            head = head.next;
    //        }
    //        tail.next = (lwNode) node;
    //        ((lwNode) node).prev = tail;
    //
    //        return list;
    //    }
    /*
     ======================================================================
     lwListInsert()

     Insert a node into a list in sorted order.
     ====================================================================== */
    fun lwListInsert(vList: lwNode?, vItem: lwNode?, compare: cmp_t<*>?) {
        val list: lwNode?
        val item: lwNode?
        var node: lwNode?
        var prev: lwNode?
        if (vList == null) {
            return  // maybe re-init?
        }
        if (vList.isNULL) {
            vList.oSet(vItem)
            return
        }
        list = vList
        item = vItem
        node = list
        prev = null
        while (node != null) {
            if (0 < compare.compare(node, item)) {
                break
            }
            prev = node
            node = node.getNext()
        }
        if (null == prev) {
            vList.oSet(item)
            node.setPrev(item)
            item.setNext(node)
        } else if (null == node) {
            prev.setNext(item)
            item.setPrev(prev)
        } else {
            item.setNext(node)
            item.setPrev(prev)
            prev.setNext(item)
            node.setPrev(item)
        }
    }

    fun get_flen(): Int {
        return Model_lwo.flen
    }

    fun set_flen(i: Int) {
        Model_lwo.flen = i
    }

    fun getbytes(fp: idFile?, size: Int): ByteArray? {
        var data: ByteBuffer?
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return null
        }
        if (size < 0) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return null
        }
        data = ByteBuffer.allocate(size) //Mem_ClearedAlloc(size);
        if (null == data) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return null
        }
        if (size != fp.Read(data, size)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            //            Mem_Free(data);
            data = null
            return null
        }
        Model_lwo.flen += size
        return data.array()
    }

    fun skipbytes(fp: idFile?, n: Int) {
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return
        }
        if (!fp.Seek(n.toLong(), fsOrigin_t.FS_SEEK_CUR)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
        } else {
            Model_lwo.flen += n
        }
    }

    fun getI1(fp: idFile?): Int {
        val i: Int
        val c = byteArrayOf(0)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        //        c[0] = 0;
        i = fp.Read(ByteBuffer.wrap(c))
        if (i < 0) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Model_lwo.flen += 1
        return if (c[0] > 127) {
            c[0] - 256
        } else c[0]
    }

    fun getI2(fp: idFile?): Short {
        val i = ByteBuffer.allocate(2)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        if (2 != fp.Read(i)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Lib.Companion.BigRevBytes(i,  /*2,*/1)
        Model_lwo.flen += 2
        return i.short
    }

    fun getI4(fp: idFile?): Int {
        val i = ByteBuffer.allocate(4)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        if (4 != fp.Read(i, 4)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Lib.Companion.BigRevBytes(i,  /*4,*/1)
        Model_lwo.flen += 4
        return i.int
    }

    fun getU1(fp: idFile?): Char {
        val i: Int
        val c = byteArrayOf(0)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        c[0] = 0
        i = fp.Read(ByteBuffer.wrap(c), 1)
        if (i < 0) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Model_lwo.flen += 1
        return c[0] as Char
    }

    fun getU2(fp: idFile?): Short {
        val i = ByteBuffer.allocate(2)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        if (2 != fp.Read(i)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Lib.Companion.BigRevBytes(i,  /*2*,*/1)
        Model_lwo.flen += 2
        return i.short
    }

    fun getU4(fp: idFile?): Int {
        val i = ByteBuffer.allocate(4)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        if (4 != fp.Read(i)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0
        }
        Lib.Companion.BigRevBytes(i,  /*4,*/1)
        Model_lwo.flen += 4
        return i.int
    }

    fun getVX(fp: idFile?): Int {
        val c = ByteBuffer.allocate(1)
        var i: Int
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        c.clear()
        if (fp.Read(c) == -1) {
            return 0
        }
        if (c[0].toInt() != 0xFF) {
            i = TempDump.btoi(c) shl 8
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or TempDump.btoi(c)
            Model_lwo.flen += 2
        } else {
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = TempDump.btoi(c) shl 16
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or (TempDump.btoi(c) shl 8)
            c.clear()
            if (fp.Read(c) == -1) {
                return 0
            }
            i = i or TempDump.btoi(c)
            Model_lwo.flen += 4
        }
        return i
    }

    fun getF4(fp: idFile?): Float {
        val f = ByteBuffer.allocate(4)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0.0f
        }
        if (4 != fp.Read(f)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return 0.0f
        }
        Lib.Companion.BigRevBytes(f,  /*4,*/1)
        Model_lwo.flen += 4
        return if (Math_h.FLOAT_IS_DENORMAL(f.getFloat(0))) {
            0
        } else f.getFloat(0)
    }

    fun getS0(fp: idFile?): String? {
        val s: ByteBuffer?
        var i: Int
        val len: Int
        val pos: Int
        val c = ByteBuffer.allocate(1)
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return null
        }
        pos = fp.Tell()
        i = 1
        while (true) {
            c.clear()
            if (fp.Read(c) == -1) {
                Model_lwo.flen = Model_lwo.FLEN_ERROR
                return null
            }
            if (c[0].toInt() == 0) {
                break
            }
            i++
        }
        if (i == 1) {
            if (!fp.Seek((pos + 2).toLong(), fsOrigin_t.FS_SEEK_SET)) {
                Model_lwo.flen = Model_lwo.FLEN_ERROR
            } else {
                Model_lwo.flen += 2
            }
            return null
        }
        len = i + (i and 1)
        s = ByteBuffer.allocate(len) // Mem_ClearedAlloc(len);
        if (TempDump.NOT(s)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return null
        }
        if (!fp.Seek(pos.toLong(), fsOrigin_t.FS_SEEK_SET)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return null
        }
        if (len != fp.Read(s, len)) {
            Model_lwo.flen = Model_lwo.FLEN_ERROR
            return null
        }
        Model_lwo.flen += len
        return TempDump.bbtocb(s).toString().trim { it <= ' ' } //TODO:check output(my tests return chinese characters).
    }

    @Deprecated("") //UNUSED
    fun sgetI1(bp: Array<String?>?): Int {
        var i: Int
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        i = bp.get(0).get(0).code
        if (i > 127) {
            i -= 256
        }
        Model_lwo.flen += 1
        bp.get(0) = bp.get(0).substring(1)
        return i
    }

    fun sgetI2(bp: ByteBuffer?): Short {
        val i: Short
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        //   memcpy( i, bp, 2 );
        Lib.Companion.BigRevBytes(bp,  /*bp.position(), 2,*/1)
        Model_lwo.flen += 2
        i = bp.getShort()
        bp.position(bp.position() + 2)
        return i
    }

    @Deprecated("") //UNUSED
    fun sgetI4(bp: Array<String?>?): Int {
        throw UnsupportedOperationException()
        //        int[] i = {0};
//
//        if (flen == FLEN_ERROR) {
//            return 0;
//        }
////   memcpy( &i, *bp, 4 );
//        i[0] |= bp[0].charAt(0) << 24;
//        i[0] |= bp[0].charAt(1) << 16;
//        i[0] |= bp[0].charAt(2) << 8;
//        i[0] |= bp[0].charAt(3) << 0;//TODO:check endianess
//        BigRevBytes(i, /*4,*/ 1);
//        flen += 4;
//        bp[0] = bp[0].substring(4);
//        return i[0];
    }

    @Deprecated("") //UNUSED
    fun sgetU1(bp: Array<String?>?): Char {
        val c: Char
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        c = bp.get(0).get(0)
        Model_lwo.flen += 1
        bp.get(0) = bp.get(0).substring(1)
        return c
    }

    fun sgetU2(bp: ByteBuffer?): Short {
        val i: Short
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        //        i = (short) ((bp.get() << 8) | bp.get());//TODO: &0xFF???
        i = bp.getShort()
        Model_lwo.flen += 2
        //        *bp += 2;
        return i
    }

    fun sgetU4(bp: ByteBuffer?): Int {
        val i: Int
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        //   memcpy( &i, *bp, 4 );
        Lib.Companion.BigRevBytes(bp,  /*bp.position(), 4,*/1)
        Model_lwo.flen += 4
        i = bp.getInt()
        //        bp.position(bp.position() + 4);
        return i
    }

    fun sgetVX(bp: ByteBuffer?): Int {
        val i: Int
        val pos = bp.position()
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0
        }
        if (bp.get(pos).toInt() != 0xFF) {
            i = TempDump.btoi(bp.get(pos)) shl 8 or TempDump.btoi(bp.get(pos + 1))
            Model_lwo.flen += 2
            bp.position(pos + 2)
        } else {
            i = TempDump.btoi(bp.get(pos + 1)) shl 16 or (TempDump.btoi(bp.get(pos + 2)) shl 8) or TempDump.btoi(
                bp.get(pos + 3)
            )
            Model_lwo.flen += 4
            bp.position(pos + 4)
        }
        return i
    }

    fun sgetF4(bp: ByteBuffer?): Float {
        var f: Float
        val i = 0
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return 0.0f
        }
        //   memcpy( &f, *bp, 4 );
        Lib.Companion.BigRevBytes(bp,  /*bp.position(), 4,*/1)
        Model_lwo.flen += 4
        f = bp.getFloat()
        //        bp.position(bp.position() + 4);
        if (Math_h.FLOAT_IS_DENORMAL(f)) {
            f = 0.0f
        }
        return f
    }

    fun sgetS0(bp: ByteBuffer?): String? {
        var s: String?
        //   unsigned char *buf = *bp;
        var len: Int
        val pos = bp.position()
        if (Model_lwo.flen == Model_lwo.FLEN_ERROR) {
            return null
        }

        //   len = strlen( (const char*)buf ) + 1;
        s = String(bp.array()).substring(pos)
        len = TempDump.strLen(s) + 1 //TODO:check
        if (1 == len) {
            Model_lwo.flen += 2
            bp.position(pos + 2)
            return null
        }
        len += len and 1
        //        s =  Mem_ClearedAlloc(len);
//        if (null == s) {
//            flen = FLEN_ERROR;
//            return null;
//        }
//
//   memcpy( s, buf, len );
        s = s.substring(0, len)
        Model_lwo.flen += s.length
        bp.position(pos + s.length)
        return s
    }

    /*
     ======================================================================
     lwFreeObject()

     Free memory used by an lwObject.
     ====================================================================== */
    @Deprecated("")
    fun lwFreeObject(`object`: lwObject?) {
        var `object` = `object`
        if (`object` != null) {
//            lwListFree(object.layer, lwFreeLayer.getInstance());
//            lwListFree(object.env, lwFreeEnvelope.getInstance());
//            lwListFree(object.clip, lwFreeClip.getInstance());
//            lwListFree(object.surf, lwFreeSurface.getInstance());
//            lwFreeTags(object.taglist);
            `object` = null
        }
    }

    /*
     ======================================================================
     lwGetObject()

     Returns the contents of a LightWave object, given its filename, or
     null if the file couldn't be loaded.  On failure, failID and failpos
     can be used to diagnose the cause.

     1.  If the file isn't an LWO2 or an LWOB, failpos will contain 12 and
     failID will be unchanged.

     2.  If an error occurs while reading, failID will contain the most
     recently read IFF chunk ID, and failpos will contain the value
     returned by fp.Tell() at the time of the failure.

     3.  If the file couldn't be opened, or an error occurs while reading
     the first 12 bytes, both failID and failpos will be unchanged.

     If you don't need this information, failID and failpos can be null.
     ====================================================================== */
    fun lwGetObject(filename: String?, failID: IntArray?, failpos: IntArray?): lwObject? {
        var fp: idFile? // = null;
        val `object`: lwObject
        var layer: lwLayer?
        var node: lwNode?
        var id: Int
        val formsize: Int
        val type: Int
        var cksize: Int
        var i: Int
        var rlen: Int
        fp = FileSystem_h.fileSystem.OpenFileRead(filename)
        if (null == fp) {
            return null
        }

        /* read the first 12 bytes */Model_lwo.set_flen(0)
        id = Model_lwo.getU4(fp)
        formsize = Model_lwo.getU4(fp)
        type = Model_lwo.getU4(fp)
        if (12 != Model_lwo.get_flen()) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return null
        }

        /* is this a LW object? */if (id != Model_lwo.ID_FORM) {
            FileSystem_h.fileSystem.CloseFile(fp)
            if (failpos != null) {
                failpos[0] = 12
            }
            return null
        }
        if (type != Model_lwo.ID_LWO2) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return if (type == Model_lwo.ID_LWOB) {
                Model_lwo.lwGetObject5(filename, failID, failpos)
            } else {
                if (failpos != null) {
                    failpos[0] = 12
                }
                null
            }
        }
        Fail@ /* allocate an object and a default layer */if (true) {
            `object` = lwObject() // Mem_ClearedAlloc(sizeof(lwObject));
            //            if (null == object) {
//                break Fail;
//            }
            layer = lwLayer() // Mem_ClearedAlloc(sizeof(lwLayer));
            //            if (null == layer) {
//                break Fail;
//            }
            `object`.layer = layer
            `object`.timeStamp.get(0) = fp.Timestamp()

            /* get the first chunk header */id = Model_lwo.getU4(fp)
            cksize = Model_lwo.getU4(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }

            /* process chunks as they're encountered */
            var j = 0
            while (true) {
                j++
                cksize += cksize and 1
                when (id) {
                    Model_lwo.ID_LAYR -> {
                        if (`object`.nlayers > 0) {
                            layer = lwLayer() // Mem_ClearedAlloc(sizeof(lwLayer));
                            //                            if (null == layer) {
//                                break Fail;
//                            }
                            `object`.layer = Model_lwo.lwListAdd(`object`.layer, layer)
                        }
                        `object`.nlayers++
                        Model_lwo.set_flen(0)
                        layer.index = Model_lwo.getU2(fp).toInt()
                        layer.flags = Model_lwo.getU2(fp).toInt()
                        layer.pivot.get(0) = Model_lwo.getF4(fp)
                        layer.pivot.get(1) = Model_lwo.getF4(fp)
                        layer.pivot.get(2) = Model_lwo.getF4(fp)
                        layer.name = Model_lwo.getS0(fp)
                        rlen = Model_lwo.get_flen()
                        if (rlen < 0 || rlen > cksize) {
                            break@Fail
                        }
                        if (rlen <= cksize - 2) {
                            layer.parent = Model_lwo.getU2(fp).toInt()
                        }
                        rlen = Model_lwo.get_flen()
                        if (rlen < cksize) {
                            fp.Seek((cksize - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                        }
                    }
                    Model_lwo.ID_PNTS -> if (!Model_lwo.lwGetPoints(fp, cksize, layer.point)) {
                        break@Fail
                    }
                    Model_lwo.ID_POLS -> if (!Model_lwo.lwGetPolygons(fp, cksize, layer.polygon, layer.point.offset)) {
                        break@Fail
                    }
                    Model_lwo.ID_VMAP, Model_lwo.ID_VMAD -> {
                        node = Model_lwo.lwGetVMap(
                            fp,
                            cksize,
                            layer.point.offset,
                            layer.polygon.offset,
                            if (id == Model_lwo.ID_VMAD) 1 else 0
                        )
                        if (null == node) {
                            break@Fail
                        }
                        layer.vmap = Model_lwo.lwListAdd(layer.vmap, node)
                        layer.nvmaps++
                    }
                    Model_lwo.ID_PTAG -> if (!Model_lwo.lwGetPolygonTags(fp, cksize, `object`.taglist, layer.polygon)) {
                        break@Fail
                    }
                    Model_lwo.ID_BBOX -> {
                        Model_lwo.set_flen(0)
                        i = 0
                        while (i < 6) {
                            layer.bbox.get(i) = Model_lwo.getF4(fp)
                            i++
                        }
                        rlen = Model_lwo.get_flen()
                        if (rlen < 0 || rlen > cksize) {
                            break@Fail
                        }
                        if (rlen < cksize) {
                            fp.Seek((cksize - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                        }
                    }
                    Model_lwo.ID_TAGS -> if (!Model_lwo.lwGetTags(fp, cksize, `object`.taglist)) {
                        break@Fail
                    }
                    Model_lwo.ID_ENVL -> {
                        node = Model_lwo.lwGetEnvelope(fp, cksize)
                        if (null == node) {
                            break@Fail
                        }
                        `object`.env = Model_lwo.lwListAdd(`object`.env, node)
                        `object`.nenvs++
                    }
                    Model_lwo.ID_CLIP -> {
                        node = Model_lwo.lwGetClip(fp, cksize)
                        if (null == node) {
                            break@Fail
                        }
                        `object`.clip = Model_lwo.lwListAdd(`object`.clip, node)
                        `object`.nclips++
                    }
                    Model_lwo.ID_SURF -> {
                        node = Model_lwo.lwGetSurface(fp, cksize)
                        if (null == node) {
                            break@Fail
                        }
                        `object`.surf = Model_lwo.lwListAdd(`object`.surf, node)
                        `object`.nsurfs++
                    }
                    Model_lwo.ID_DESC, Model_lwo.ID_TEXT, Model_lwo.ID_ICON -> fp.Seek(
                        cksize.toLong(),
                        fsOrigin_t.FS_SEEK_CUR
                    )
                    else -> fp.Seek(cksize.toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the file? */if (formsize <= fp.Tell() - 8) {
                    break
                }

                /* get the next chunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                cksize = Model_lwo.getU4(fp)
                if (8 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            FileSystem_h.fileSystem.CloseFile(fp)
            fp = null
            if (`object`.nlayers == 0) {
                `object`.nlayers = 1
            }
            layer = `object`.layer
            while (layer != null) {
                Model_lwo.lwGetBoundingBox(layer.point, layer.bbox)
                Model_lwo.lwGetPolyNormals(layer.point, layer.polygon)
                if (!Model_lwo.lwGetPointPolygons(layer.point, layer.polygon)) {
                    break@Fail
                }
                if (!Model_lwo.lwResolvePolySurfaces(layer.polygon, `object`)) {
                    break@Fail
                }
                Model_lwo.lwGetVertNormals(layer.point, layer.polygon)
                if (!Model_lwo.lwGetPointVMaps(layer.point, layer.vmap)) {
                    break@Fail
                }
                if (!Model_lwo.lwGetPolyVMaps(layer.polygon, layer.vmap)) {
                    break@Fail
                }
                layer = layer.next
            }
            return `object`
        }

//        Fail:
        if (failID != null) {
            failID[0] = id
        }
        if (fp != null) {
            if (failpos != null) {
                failpos[0] = fp.Tell()
            }
            FileSystem_h.fileSystem.CloseFile(fp)
        }
        //        lwFreeObject(object);
        return null
    }

    //    static {
    //        ID_SRFS = LWID_('S', 'R', 'F', 'S');
    ////ID_FLAG= LWID_('F','L','A','G');
    //        ID_VLUM = LWID_('V', 'L', 'U', 'M');
    //        ID_VDIF = LWID_('V', 'D', 'I', 'F');
    //        ID_VSPC = LWID_('V', 'S', 'P', 'C');
    //        ID_RFLT = LWID_('R', 'F', 'L', 'T');
    //        ID_BTEX = LWID_('B', 'T', 'E', 'X');
    //        ID_CTEX = LWID_('C', 'T', 'E', 'X');
    //        ID_DTEX = LWID_('D', 'T', 'E', 'X');
    //        ID_LTEX = LWID_('L', 'T', 'E', 'X');
    //        ID_RTEX = LWID_('R', 'T', 'E', 'X');
    //        ID_STEX = LWID_('S', 'T', 'E', 'X');
    //        ID_TTEX = LWID_('T', 'T', 'E', 'X');
    //        ID_TFLG = LWID_('T', 'F', 'L', 'G');
    //        ID_TSIZ = LWID_('T', 'S', 'I', 'Z');
    //        ID_TCTR = LWID_('T', 'C', 'T', 'R');
    //        ID_TFAL = LWID_('T', 'F', 'A', 'L');
    //        ID_TVEL = LWID_('T', 'V', 'E', 'L');
    //        ID_TCLR = LWID_('T', 'C', 'L', 'R');
    //        ID_TVAL = LWID_('T', 'V', 'A', 'L');
    ////ID_TAMP= LWID_('T','A','M','P');
    ////ID_TIMG= LWID_('T','I','M','G');
    //        ID_TAAS = LWID_('T', 'A', 'A', 'S');
    //        ID_TREF = LWID_('T', 'R', 'E', 'F');
    //        ID_TOPC = LWID_('T', 'O', 'P', 'C');
    //        ID_SDAT = LWID_('S', 'D', 'A', 'T');
    //        ID_TFP0 = LWID_('T', 'F', 'P', '0');
    //        ID_TFP1 = LWID_('T', 'F', 'P', '1');
    //    }
    /*
     ======================================================================
     add_clip()

     Add a clip to the clip list.  Used to store the contents of an RIMG or
     TIMG surface subchunk.
     ====================================================================== */
    fun add_clip(s: Array<String?>?, clist: lwClip?, nclips: IntArray?): Int {
        var clist = clist
        val clip: lwClip
        var p: Int
        clip = lwClip() // Mem_ClearedAlloc(sizeof(lwClip));
        if (null == clip) {
            return 0
        }
        clip.contrast.`val` = 1.0f
        clip.brightness.`val` = 1.0f
        clip.saturation.`val` = 1.0f
        clip.gamma.`val` = 1.0f
        if (s.get(0).indexOf("(sequence)").also { p = it } != 0) {
//      p[ -1 ] = 0;
            s.get(0) = TempDump.replaceByIndex('\u0000', p, s.get(0))
            clip.type = Model_lwo.ID_ISEQ
            clip.source.seq.prefix = s.get(0)
            clip.source.seq.digits = 3
        } else {
            clip.type = Model_lwo.ID_STIL
            clip.source.still.name = s.get(0)
        }
        nclips.get(0)++
        clip.index = nclips.get(0)
        clist = Model_lwo.lwListAdd(clist, clip)
        return clip.index
    }

    /*
     ======================================================================
     add_tvel()

     Add a triple of envelopes to simulate the old texture velocity
     parameters.
     ====================================================================== */
    fun add_tvel(pos: FloatArray?, vel: FloatArray?, elist: lwEnvelope?, nenvs: IntArray?): Int {
        var elist = elist
        var env: lwEnvelope? = null
        var key0: lwKey
        var key1: lwKey
        var i: Int
        i = 0
        while (i < 3) {
            env = lwEnvelope() // Mem_ClearedAlloc(sizeof(lwEnvelope));
            key0 = lwKey() // Mem_ClearedAlloc(sizeof(lwKey));
            key1 = lwKey() // Mem_ClearedAlloc(sizeof(lwKey));
            if (null == env || null == key0 || null == key1) {
                return 0
            }
            key0.next = key1
            key0.value = pos.get(i)
            key0.time = 0.0f
            key1.prev = key0
            key1.value = pos.get(i) + vel.get(i) * 30.0f
            key1.time = 1.0f
            key1.shape = Model_lwo.ID_LINE.toLong()
            key0.shape = key1.shape
            env.index = nenvs.get(0) + i + 1
            env.type = 0x0301 + i
            env.name = "" //(String) Mem_ClearedAlloc(11);
            if (env.name != null) {
                env.name = "Position." + ('X'.code + i)
                //                env.name = "Position.X";
//                env.name[9] += i;
            }
            env.key = key0
            env.nkeys = 2
            env.behavior.get(0) = Model_lwo.BEH_LINEAR
            env.behavior.get(1) = Model_lwo.BEH_LINEAR
            elist = Model_lwo.lwListAdd(elist, env)
            i++
        }
        nenvs.get(0) += 3
        return env.index - 2
    }

    /*
     ======================================================================
     get_texture()

     Create a new texture for BTEX, CTEX, etc. subchunks.
     ====================================================================== */
    fun get_texture(s: String?): lwTexture? {
        val tex: lwTexture
        tex = lwTexture() // Mem_ClearedAlloc(sizeof(lwTexture));
        if (null == tex) {
            return null
        }
        tex.tmap.size.`val`.get(2) = 1.0f
        tex.tmap.size.`val`.get(1) = tex.tmap.size.`val`.get(2)
        tex.tmap.size.`val`.get(0) = tex.tmap.size.`val`.get(1)
        tex.opacity.`val` = 1.0f
        tex.enabled = 1
        if (s.contains("Image Map")) {
            tex.type = Model_lwo.ID_IMAP.toLong()
            if (s.contains("Planar")) {
                tex.param.imap.projection = 0
            } else if (s.contains("Cylindrical")) {
                tex.param.imap.projection = 1
            } else if (s.contains("Spherical")) {
                tex.param.imap.projection = 2
            } else if (s.contains("Cubic")) {
                tex.param.imap.projection = 3
            } else if (s.contains("Front")) {
                tex.param.imap.projection = 4
            }
            tex.param.imap.aa_strength = 1.0f
            tex.param.imap.amplitude.`val` = 1.0f
            //            Mem_Free(s);
        } else {
            tex.type = Model_lwo.ID_PROC.toLong()
            tex.param.proc.name = s
        }
        return tex
    }

    /*
     ======================================================================
     lwGetSurface5()

     Read an lwSurface from an LWOB file.
     ====================================================================== */
    fun lwGetSurface5(fp: idFile?, cksize: Int, obj: lwObject?): lwSurface? {
        val surf: lwSurface
        var tex: lwTexture? = lwTexture()
        var shdr = lwPlugin()
        val s = arrayOf<String?>(null)
        val v = FloatArray(3)
        var id: Int
        var flags: Int
        var sz: Short
        val pos: Int
        var rlen: Int
        var i = 0


        /* allocate the Surface structure */surf = lwSurface() // Mem_ClearedAlloc(sizeof(lwSurface));
        Fail@ if (true) {
            if (TempDump.NOT(surf)) {
                break@Fail
            }

            /* non-zero defaults */surf.color.rgb.get(0) = 0.78431f
            surf.color.rgb.get(1) = 0.78431f
            surf.color.rgb.get(2) = 0.78431f
            surf.diffuse.`val` = 1.0f
            surf.glossiness.`val` = 0.4f
            surf.bump.`val` = 1.0f
            surf.eta.`val` = 1.0f
            surf.sideflags = 1

            /* remember where we started */Model_lwo.set_flen(0)
            pos = fp.Tell()

            /* name */surf.name = Model_lwo.getS0(fp)

            /* first subchunk header */id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }

            /* process subchunks as they're encountered */while (true) {
                sz += (sz and 1).toShort()
                Model_lwo.set_flen(0)
                when (id) {
                    Model_lwo.ID_COLR -> {
                        surf.color.rgb.get(0) = Model_lwo.getU1(fp).code.toFloat() / 255.0f
                        surf.color.rgb.get(1) = Model_lwo.getU1(fp).code.toFloat() / 255.0f
                        surf.color.rgb.get(2) = Model_lwo.getU1(fp).code.toFloat() / 255.0f
                    }
                    Model_lwo.ID_FLAG -> {
                        flags = Model_lwo.getU2(fp).toInt()
                        if (flags and 4 == 4) {
                            surf.smooth = 1.56207f
                        }
                        if (flags and 8 == 8) {
                            surf.color_hilite.`val` = 1.0f
                        }
                        if (flags and 16 == 16) {
                            surf.color_filter.`val` = 1.0f
                        }
                        if (flags and 128 == 128) {
                            surf.dif_sharp.`val` = 0.5f
                        }
                        if (flags and 256 == 256) {
                            surf.sideflags = 3
                        }
                        if (flags and 512 == 512) {
                            surf.add_trans.`val` = 1.0f
                        }
                    }
                    Model_lwo.ID_LUMI -> surf.luminosity.`val` = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_VLUM -> surf.luminosity.`val` = Model_lwo.getF4(fp)
                    Model_lwo.ID_DIFF -> surf.diffuse.`val` = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_VDIF -> surf.diffuse.`val` = Model_lwo.getF4(fp)
                    Model_lwo.ID_SPEC -> surf.specularity.`val` = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_VSPC -> surf.specularity.`val` = Model_lwo.getF4(fp)
                    Model_lwo.ID_GLOS -> surf.glossiness.`val` =
                        Math.log(Model_lwo.getU2(fp).toDouble()).toFloat() / 20.7944f
                    Model_lwo.ID_SMAN -> surf.smooth = Model_lwo.getF4(fp)
                    Model_lwo.ID_REFL -> surf.reflection.`val`.`val` = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_RFLT -> surf.reflection.options = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_RIMG -> {
                        s[0] = Model_lwo.getS0(fp)
                        run {
                            val nclips = intArrayOf(obj.nclips)
                            surf.reflection.cindex = Model_lwo.add_clip(s, obj.clip, nclips)
                            obj.nclips = nclips[0]
                        }
                        surf.reflection.options = 3
                    }
                    Model_lwo.ID_RSAN -> surf.reflection.seam_angle = Model_lwo.getF4(fp)
                    Model_lwo.ID_TRAN -> surf.transparency.`val`.`val` = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_RIND -> surf.eta.`val` = Model_lwo.getF4(fp)
                    Model_lwo.ID_BTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.bump.tex = Model_lwo.lwListAdd(surf.bump.tex, tex)
                    }
                    Model_lwo.ID_CTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.color.tex = Model_lwo.lwListAdd(surf.color.tex, tex)
                    }
                    Model_lwo.ID_DTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.diffuse.tex = Model_lwo.lwListAdd(surf.diffuse.tex, tex)
                    }
                    Model_lwo.ID_LTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.luminosity.tex = Model_lwo.lwListAdd(surf.luminosity.tex, tex)
                    }
                    Model_lwo.ID_RTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.reflection.`val`.tex = Model_lwo.lwListAdd(surf.reflection.`val`.tex, tex)
                    }
                    Model_lwo.ID_STEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.specularity.tex = Model_lwo.lwListAdd(surf.specularity.tex, tex)
                    }
                    Model_lwo.ID_TTEX -> {
                        s[0] = String(Model_lwo.getbytes(fp, sz.toInt()))
                        tex = Model_lwo.get_texture(s[0])
                        surf.transparency.`val`.tex = Model_lwo.lwListAdd(surf.transparency.`val`.tex, tex)
                    }
                    Model_lwo.ID_TFLG -> {
                        flags = Model_lwo.getU2(fp).toInt()
                        if (flags and 1 == 1) {
                            i = 0
                        }
                        if (flags and 2 == 2) {
                            i = 1
                        }
                        if (flags and 4 == 4) {
                            i = 2
                        }
                        tex.axis = i.toShort()
                        if (tex.type == Model_lwo.ID_IMAP.toLong()) {
                            tex.param.imap.axis = i
                        } else {
                            tex.param.proc.axis = i
                        }
                        if (flags and 8 == 8) {
                            tex.tmap.coord_sys = 1
                        }
                        if (flags and 16 == 16) {
                            tex.negative = 1
                        }
                        if (flags and 32 == 32) {
                            tex.param.imap.pblend = 1
                        }
                        if (flags and 64 == 64) {
                            tex.param.imap.aa_strength = 1.0f
                            tex.param.imap.aas_flags = 1
                        }
                    }
                    Model_lwo.ID_TSIZ -> {
                        i = 0
                        while (i < 3) {
                            tex.tmap.size.`val`.get(i) = Model_lwo.getF4(fp)
                            i++
                        }
                    }
                    Model_lwo.ID_TCTR -> {
                        i = 0
                        while (i < 3) {
                            tex.tmap.center.`val`.get(i) = Model_lwo.getF4(fp)
                            i++
                        }
                    }
                    Model_lwo.ID_TFAL -> {
                        i = 0
                        while (i < 3) {
                            tex.tmap.falloff.`val`.get(i) = Model_lwo.getF4(fp)
                            i++
                        }
                    }
                    Model_lwo.ID_TVEL -> {
                        i = 0
                        while (i < 3) {
                            v[i] = Model_lwo.getF4(fp)
                            i++
                        }
                        run {
                            val nenvs = intArrayOf(obj.nenvs)
                            tex.tmap.center.eindex = Model_lwo.add_tvel(tex.tmap.center.`val`, v, obj.env, nenvs)
                            obj.nenvs = nenvs[0]
                        }
                    }
                    Model_lwo.ID_TCLR -> if (tex.type == Model_lwo.ID_PROC.toLong()) {
                        i = 0
                        while (i < 3) {
                            tex.param.proc.value[i] = Model_lwo.getU1(fp).code.toFloat() / 255.0f
                            i++
                        }
                    }
                    Model_lwo.ID_TVAL -> tex.param.proc.value[0] = Model_lwo.getI2(fp) / 256.0f
                    Model_lwo.ID_TAMP -> if (tex.type == Model_lwo.ID_IMAP.toLong()) {
                        tex.param.imap.amplitude.`val` = Model_lwo.getF4(fp)
                    }
                    Model_lwo.ID_TIMG -> {
                        s[0] = Model_lwo.getS0(fp)
                        run {
                            val nClips = intArrayOf(obj.nclips)
                            tex.param.imap.cindex = Model_lwo.add_clip(s, obj.clip, nClips)
                            obj.nclips = nClips[0]
                        }
                    }
                    Model_lwo.ID_TAAS -> {
                        tex.param.imap.aa_strength = Model_lwo.getF4(fp)
                        tex.param.imap.aas_flags = 1
                    }
                    Model_lwo.ID_TREF -> tex.tmap.ref_object = String(Model_lwo.getbytes(fp, sz.toInt()))
                    Model_lwo.ID_TOPC -> tex.opacity.`val` = Model_lwo.getF4(fp)
                    Model_lwo.ID_TFP0 -> if (tex.type == Model_lwo.ID_IMAP.toLong()) {
                        tex.param.imap.wrapw.`val` = Model_lwo.getF4(fp)
                    }
                    Model_lwo.ID_TFP1 -> if (tex.type == Model_lwo.ID_IMAP.toLong()) {
                        tex.param.imap.wraph.`val` = Model_lwo.getF4(fp)
                    }
                    Model_lwo.ID_SHDR -> {
                        shdr = lwPlugin() // Mem_ClearedAlloc(sizeof(lwPlugin));
                        if (null == shdr) {
                            break@Fail
                        }
                        shdr.name = String(Model_lwo.getbytes(fp, sz.toInt()))
                        surf.shader = Model_lwo.lwListAdd(surf.shader, shdr)
                        surf.nshaders++
                    }
                    Model_lwo.ID_SDAT -> shdr.data = Model_lwo.getbytes(fp, sz.toInt())
                    else -> {}
                }

                /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
                if (rlen < 0 || rlen > sz) {
                    break@Fail
                }

                /* skip unread parts of the current subchunk */if (rlen < sz) {
                    fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the SURF chunk? */if (cksize <= fp.Tell() - pos) {
                    break
                }

                /* get the next subchunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                sz = Model_lwo.getU2(fp)
                if (6 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            return surf
        }

//        Fail:
        if (surf != null) {
            lwFreeSurface.getInstance().run(surf)
        }
        return null
    }

    /*
     ======================================================================
     lwGetPolygons5()

     Read polygon records from a POLS chunk in an LWOB file.  The polygons
     are added to the array in the lwPolygonList.
     ====================================================================== */
    fun lwGetPolygons5(fp: idFile?, cksize: Int, plist: lwPolygonList?, ptoffset: Int): Boolean {
        var pp: lwPolygon?
        //        lwPolVert pv;
        val buf: ByteBuffer?
        var i: Int
        var j: Int
        var nv: Int
        var nverts: Int
        var npols: Int
        var p: Int
        var v: Int
        if (cksize == 0) {
            return true
        }

        /* read the whole chunk */Model_lwo.set_flen(0)
        buf = ByteBuffer.wrap(Model_lwo.getbytes(fp, cksize))
        Fail@ if (true) {
            if (null == buf) {
                break@Fail
            }

            /* count the polygons and vertices */nverts = 0
            npols = 0
            //            buf = buf;
            while (buf.position() < cksize) {
                nv = Model_lwo.sgetU2(buf).toInt()
                nverts += nv
                npols++
                buf.position(buf.position() + 2 * nv)
                i = Model_lwo.sgetI2(buf).toInt()
                if (i < 0) {
                    buf.position(buf.position() + 2) // detail polygons
                }
            }
            if (!Model_lwo.lwAllocPolygons(plist, npols, nverts)) {
                break@Fail
            }

            /* fill in the new polygons */
//            buf = buf;
            pp = plist.pol.get(plist.offset.also { p = it })
            //            pv = plist.pol[0].v[v = plist.voffset];
            v = plist.voffset
            i = 0
            while (i < npols) {
                nv = Model_lwo.sgetU2(buf).toInt()
                pp.nverts = nv
                pp.type = Model_lwo.ID_FACE.toLong()
                if (null == pp.v) {
                    pp.setV(plist.pol.get(0).v, v)
                }
                j = 0
                while (j < nv) {
                    plist.pol.get(0).getV(v + j).index = Model_lwo.sgetU2(buf) + ptoffset
                    j++
                }
                j = Model_lwo.sgetI2(buf).toInt()
                if (j < 0) {
                    j = -j
                    buf.position(buf.position() + 2)
                }
                j -= 1
                pp.surf = lwNode.getPosition(pp.surf, j) as lwSurface?
                pp = plist.pol.get(p++)
                //                pv = plist.pol[0].v[v += nv];
                v += nv
                i++
            }

//            buf=null
            return true
        }

//        Fail:
//        if (buf != null) {
//            buf=null
//        }
        Model_lwo.lwFreePolygons(plist)
        return false
    }

    /*
     ======================================================================
     getLWObject5()

     Returns the contents of an LWOB, given its filename, or null if the
     file couldn't be loaded.  On failure, failID and failpos can be used
     to diagnose the cause.

     1.  If the file isn't an LWOB, failpos will contain 12 and failID will
     be unchanged.

     2.  If an error occurs while reading an LWOB, failID will contain the
     most recently read IFF chunk ID, and failpos will contain the
     value returned by fp.Tell() at the time of the failure.

     3.  If the file couldn't be opened, or an error occurs while reading
     the first 12 bytes, both failID and failpos will be unchanged.

     If you don't need this information, failID and failpos can be null.
     ====================================================================== */
    fun lwGetObject5(filename: String?, failID: IntArray?, failpos: IntArray?): lwObject? {
        var fp: idFile?
        val `object`: lwObject
        val layer: lwLayer
        var node: lwNode?
        var id: Int
        val formsize: Int
        val type: Int
        var cksize: Int


        /* open the file */
        //fp = fopen( filename, "rb" );
        //if ( !fp ) return null;

        /* read the first 12 bytes */fp = FileSystem_h.fileSystem.OpenFileRead(filename)
        if (null == fp) {
            return null
        }
        Model_lwo.set_flen(0)
        id = Model_lwo.getU4(fp)
        formsize = Model_lwo.getU4(fp)
        type = Model_lwo.getU4(fp)
        if (12 != Model_lwo.get_flen()) {
            FileSystem_h.fileSystem.CloseFile(fp)
            return null
        }

        /* LWOB? */if (id != Model_lwo.ID_FORM || type != Model_lwo.ID_LWOB) {
            FileSystem_h.fileSystem.CloseFile(fp)
            if (failpos != null) {
                failpos[0] = 12
            }
            return null
        }
        Fail@ /* allocate an object and a default layer */if (true) {
            `object` = lwObject() // Mem_ClearedAlloc(sizeof(lwObject));
            if (null == `object`) {
                break@Fail
            }
            layer = lwLayer() // Mem_ClearedAlloc(sizeof(lwLayer));
            if (null == layer) {
                break@Fail
            }
            `object`.layer = layer
            `object`.nlayers = 1

            /* get the first chunk header */id = Model_lwo.getU4(fp)
            cksize = Model_lwo.getU4(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }

            /* process chunks as they're encountered */while (true) {
                cksize += cksize and 1
                when (id) {
                    Model_lwo.ID_PNTS -> if (!Model_lwo.lwGetPoints(fp, cksize, layer.point)) {
                        break@Fail
                    }
                    Model_lwo.ID_POLS -> if (!Model_lwo.lwGetPolygons5(
                            fp, cksize, layer.polygon,
                            layer.point.offset
                        )
                    ) {
                        break@Fail
                    }
                    Model_lwo.ID_SRFS -> if (!Model_lwo.lwGetTags(fp, cksize, `object`.taglist)) {
                        break@Fail
                    }
                    Model_lwo.ID_SURF -> {
                        node = Model_lwo.lwGetSurface5(fp, cksize, `object`)
                        if (null == node) {
                            break@Fail
                        }
                        `object`.surf = Model_lwo.lwListAdd(`object`.surf, node)
                        `object`.nsurfs++
                    }
                    else -> fp.Seek(cksize.toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the file? */if (formsize <= fp.Tell() - 8) {
                    break
                }

                /* get the next chunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                cksize = Model_lwo.getU4(fp)
                if (8 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            FileSystem_h.fileSystem.CloseFile(fp)
            fp = null
            Model_lwo.lwGetBoundingBox(layer.point, layer.bbox)
            Model_lwo.lwGetPolyNormals(layer.point, layer.polygon)
            if (!Model_lwo.lwGetPointPolygons(layer.point, layer.polygon)) {
                break@Fail
            }
            if (!Model_lwo.lwResolvePolySurfaces(layer.polygon, `object`)) {
                break@Fail
            }
            Model_lwo.lwGetVertNormals(layer.point, layer.polygon)
            return `object`
        }

//        Fail2:
        if (failID != null) {
            failID[0] = id
        }
        if (fp != null) {
            if (failpos != null) {
                failpos[0] = fp.Tell()
            }
            FileSystem_h.fileSystem.CloseFile(fp)
        }
        //        lwFreeObject(object);
        return null
    }

    /*
     ======================================================================
     lwFreePoints()

     Free the memory used by an lwPointList.
     ====================================================================== */
    fun lwFreePoints(point: lwPointList?) {
        var i: Int
        if (point != null) {
            if (point.pt != null) {
//                for (i = 0; i < point.count; i++) {
//                    if (point.pt[ i].pol != null) {
//                        Mem_Free(point.pt[ i].pol);
//                    }
//                    if (point.pt[ i].vm != null) {
//                        Mem_Free(point.pt[ i].vm);
//                    }
//                }
//                Mem_Free(point.pt);
                point.pt = null
            }
            //            memset(point, 0, sizeof(lwPointList));
        }
    }

    /*
     ======================================================================
     lwFreePolygons()

     Free the memory used by an lwPolygonList.
     ====================================================================== */
    fun lwFreePolygons(plist: lwPolygonList?) {
        var i: Int
        var j: Int
        if (plist != null) {
            if (plist.pol != null) {
//                for (i = 0; i < plist.count; i++) {
//                    if (plist.pol[ i].v != null) {
//                        for (j = 0; j < plist.pol[ i].nverts; j++) {
//                            if (plist.pol[ i].v[ j].vm != null) {
//                                Mem_Free(plist.pol[ i].v[ j].vm);
//                            }
//                        }
//                    }
//                }
//                if (plist.pol[ 0].v != null) {
//                    Mem_Free(plist.pol[ 0].v);
//                }
//                Mem_Free(plist.pol);
                plist.pol = null
            }
            //            memset(plist, 0, sizeof(lwPolygonList));
        }
    }

    /*
     ======================================================================
     lwGetPoints()

     Read point records from a PNTS chunk in an LWO2 file.  The points are
     added to the array in the lwPointList.
     ====================================================================== */
    fun lwGetPoints(fp: idFile?, cksize: Int, point: lwPointList?): Boolean {
        val f: ByteBuffer?
        val np: Int
        var i: Int
        var j: Int
        if (cksize == 1) {
            return true
        }

        /* extend the point array to hold the new points */np = cksize / 12
        point.offset = point.count
        point.count += np
        var oldpt = point.pt
        point.pt = arrayOfNulls<lwPoint?>(point.count) // Mem_Alloc(point.count);
        if (null == point.pt) {
            return false
        }
        if (oldpt != null) {
//            memcpy(point.pt, oldpt, point.offset * sizeof(lwPoint));
            i = 0
            while (i < point.offset) {
                point.pt.get(i) = lwPoint(oldpt[i])
                i++
            }
            //            Mem_Free(oldpt);
            oldpt = null
        }
        //	memset( &point.pt[ point.offset ], 0, np * sizeof( lwPoint ) );
        for (n in point.offset until np) {
            point.pt.get(n) = lwPoint()
        }

        /* read the whole chunk */f = ByteBuffer.wrap(Model_lwo.getbytes(fp, cksize))
        if (null == f) {
            return false
        }
        Lib.Companion.BigRevBytes(f,  /*4,*/np * 3)

        /* assign position values */i = 0
        j = 0
        while (i < np) {
            point.pt.get(i).pos.get(0) = f.float //f[ j ];
            point.pt.get(i).pos.get(1) = f.float //f[ j + 1 ];
            point.pt.get(i).pos.get(2) = f.float //f[ j + 2 ];
            i++
            j += 3
        }

//        Mem_Free(f);
        return true
    }

    /*
     ======================================================================
     lwGetBoundingBox()

     Calculate the bounding box for a point list, but only if the bounding
     box hasn't already been initialized.
     ====================================================================== */
    fun lwGetBoundingBox(point: lwPointList?, bbox: FloatArray?) {
        var i: Int
        var j: Int
        if (point.count == 0) {
            return
        }
        i = 0
        while (i < 6) {
            if (bbox.get(i) != 0.0f) {
                return
            }
            i++
        }
        bbox.get(2) = 1e20f
        bbox.get(1) = bbox.get(2)
        bbox.get(0) = bbox.get(1)
        bbox.get(5) = -1e20f
        bbox.get(4) = bbox.get(5)
        bbox.get(3) = bbox.get(4)
        i = 0
        while (i < point.count) {
            j = 0
            while (j < 3) {
                if (bbox.get(j) > point.pt.get(i).pos.get(j)) {
                    bbox.get(j) = point.pt.get(i).pos.get(j)
                }
                if (bbox.get(j + 3) < point.pt.get(i).pos.get(j)) {
                    bbox.get(j + 3) = point.pt.get(i).pos.get(j)
                }
                j++
            }
            i++
        }
    }

    /*
     ======================================================================
     lwAllocPolygons()

     Allocate or extend the polygon arrays to hold new records.
     ====================================================================== */
    fun lwAllocPolygons(plist: lwPolygonList?, npols: Int, nverts: Int): Boolean {
        var i: Int
        plist.offset = plist.count
        plist.count += npols
        var oldpol = plist.pol
        plist.pol = arrayOfNulls<lwPolygon?>(plist.count) // Mem_Alloc(plist.count);
        //        if (null == plist.pol) {
//            return false;
//        }
        if (oldpol != null) {
//            memcpy(plist.pol, oldpol, plist.offset);
            i = 0
            while (i < plist.offset) {
                plist.pol.get(i) = lwPolygon(oldpol[i])
                i++
            }
            //            Mem_Free(oldpol);
            oldpol = null
        }
        //        memset(plist.pol + plist.offset, 0, npols);
        i = 0
        while (i < npols) {
            plist.pol.get(plist.offset + i) = lwPolygon()
            i++
        }
        plist.voffset = plist.vcount
        plist.vcount += nverts
        var oldpolv = plist.pol.get(0).v
        plist.pol.get(0).v = arrayOfNulls<lwPolVert?>(plist.vcount) // Mem_Alloc(plist.vcount);
        if (null == plist.pol.get(0).v) {
            return false
        }
        if (oldpolv != null) {
//            memcpy(plist.pol[0].v, oldpolv, plist.voffset);
            System.arraycopy(oldpolv, 0, plist.pol.get(0).v, 0, plist.offset)
            oldpolv = null //Mem_Free(oldpolv);
        }
        //        memset(plist.pol[ 0].v + plist.voffset, 0, nverts);
        i = 0
        while (i < nverts) {
            plist.pol.get(0).v.get(plist.voffset + i) = lwPolVert()
            i++
        }

        /* fix up the old vertex pointers */i = 1
        while (i < plist.offset) {
            for (j in plist.pol.get(i).v.indices) {
//            plist.pol[i].v = plist.pol[i - 1].v + plist.pol[i - 1].nverts;
                plist.pol.get(i).v.get(j) = lwPolVert() //TODO:simplify.
            }
            i++
        }
        return true
    }

    /*
     ======================================================================
     lwGetPolygons()

     Read polygon records from a POLS chunk in an LWO2 file.  The polygons
     are added to the array in the lwPolygonList.
     ====================================================================== */
    fun lwGetPolygons(fp: idFile?, cksize: Int, plist: lwPolygonList?, ptoffset: Int): Boolean {
        var pp: lwPolygon?
        //        lwPolVert pv;
        var buf: ByteBuffer?
        var i: Int
        var j: Int
        var flags: Int
        var nv: Int
        var nverts: Int
        var npols: Int
        var p: Int
        var v: Int
        val type: Int
        if (cksize == 0) {
            return true
        }

        /* read the whole chunk */Model_lwo.set_flen(0)
        type = Model_lwo.getU4(fp)
        buf = ByteBuffer.wrap(Model_lwo.getbytes(fp, cksize - 4))
        Fail@ if (true) {
            if (cksize != Model_lwo.get_flen()) {
                break@Fail
            }

            /* count the polygons and vertices */nverts = 0
            npols = 0
            //            buf = buf;
            while (buf.hasRemaining()) { //( bp < buf + cksize - 4 ) {
                nv = Model_lwo.sgetU2(buf).toInt()
                nv = nv and 0x03FF
                nverts += nv
                npols++
                i = 0
                while (i < nv) {
                    j = Model_lwo.sgetVX(buf)
                    i++
                }
            }
            if (!Model_lwo.lwAllocPolygons(plist, npols, nverts)) {
                break@Fail
            }

            /* fill in the new polygons */buf.rewind() //bp = buf;
            p = plist.offset
            //            pv = plist.pol[0].v[v = plist.voffset];
            v = plist.voffset
            i = 0
            while (i < npols) {
                nv = Model_lwo.sgetU2(buf).toInt()
                flags = nv and 0xFC00
                nv = nv and 0x03FF
                pp = plist.pol.get(p++)
                pp.nverts = nv
                pp.flags = flags
                pp.type = type.toLong()
                if (null == pp.v) {
                    pp.setV(plist.pol.get(0).v, v)
                }
                j = 0
                while (j < nv) {
                    pp.getV(j).index = Model_lwo.sgetVX(buf) + ptoffset
                    j++
                }

//                pv = plist.pol[0].v[v += nv];
                v += nv
                i++
            }
            buf = null
            return true
        }
        Fail@ if (buf != null) {
            buf = null
        }
        Model_lwo.lwFreePolygons(plist)
        return false
    }

    /*
     ======================================================================
     lwGetPolyNormals()

     Calculate the polygon normals.  By convention, LW's polygon normals
     are found as the cross product of the first and last edges.  It's
     undefined for one- and two-point polygons.
     ====================================================================== */
    fun lwGetPolyNormals(point: lwPointList?, polygon: lwPolygonList?) {
        var i: Int
        var j: Int
        val p1 = FloatArray(3)
        val p2 = FloatArray(3)
        val pn = FloatArray(3)
        val v1 = FloatArray(3)
        val v2 = FloatArray(3)
        i = 0
        while (i < polygon.count) {
            if (polygon.pol.get(i).nverts < 3) {
                i++
                continue
            }
            j = 0
            while (j < 3) {


                // FIXME: track down why indexes are way out of range
                p1[j] = point.pt.get(polygon.pol.get(i).getV(0).index).pos.get(j)
                p2[j] = point.pt.get(polygon.pol.get(i).getV(1).index).pos.get(j)
                pn[j] = point.pt.get(polygon.pol.get(i).getV(polygon.pol.get(i).nverts - 1).index).pos.get(j)
                j++
            }
            j = 0
            while (j < 3) {
                v1[j] = p2[j] - p1[j]
                v2[j] = pn[j] - p1[j]
                j++
            }
            Model_lwo.cross(v1, v2, polygon.pol.get(i).norm)
            Model_lwo.normalize(polygon.pol.get(i).norm)
            i++
        }
    }

    /*
     ======================================================================
     lwGetPointPolygons()

     For each point, fill in the indexes of the polygons that share the
     point.  Returns 0 if any of the memory allocations fail, otherwise
     returns 1.
     ====================================================================== */
    fun lwGetPointPolygons(point: lwPointList?, polygon: lwPolygonList?): Boolean {
        var i: Int
        var j: Int
        var k: Int

        /* count the number of polygons per point */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol.get(i).nverts) {
                ++point.pt.get(polygon.pol.get(i).getV(j).index).npols
                j++
            }
            i++
        }

        /* alloc per-point polygon arrays */i = 0
        while (i < point.count) {
            if (point.pt.get(i).npols == 0) {
                i++
                continue
            }
            point.pt.get(i).pol = IntArray(point.pt.get(i).npols) // Mem_ClearedAlloc(point.pt[ i].npols);
            if (null == point.pt.get(i).pol) {
                return false
            }
            point.pt.get(i).npols = 0
            i++
        }

        /* fill in polygon array for each point */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol.get(i).nverts) {
                k = polygon.pol.get(i).getV(j).index
                point.pt.get(k).pol.get(point.pt.get(k).npols) = i
                ++point.pt.get(k).npols
                j++
            }
            i++
        }
        return true
    }

    /*
     ======================================================================
     lwResolvePolySurfaces()

     Convert tag indexes into actual lwSurface pointers.  If any polygons
     point to tags for which no corresponding surface can be found, a
     default surface is created.
     ====================================================================== */
    fun lwResolvePolySurfaces(polygon: lwPolygonList?, `object`: lwObject?): Boolean {
        var s: Array<lwSurface?>?
        var st: lwSurface?
        var i: Int
        var index: Int
        val tlist = `object`.taglist
        var surf = `object`.surf
        if (tlist.count == 0) {
            return true
        }
        s = arrayOfNulls<lwSurface?>(tlist.count) // Mem_ClearedAlloc(tlist.count);
        //        if (null == s) {
//            return 0;
//        }
        i = 0
        while (i < tlist.count) {
            st = surf
            while (st != null) {
                if (st.name != null && st.name == tlist.tag.get(i)) {
                    s[i] = st
                    break
                }
                st = st.next
            }
            i++
        }
        i = 0
        while (i < polygon.count) {
            index = polygon.pol.get(i).part
            if (index < 0 || index > tlist.count) {
                return false
            }
            if (null == s[index]) {
                s[index] = Model_lwo.lwDefaultSurface()
                if (null == s[index]) {
                    return false
                }
                s[index].name = "" //(String) Mem_ClearedAlloc(tlist.tag[ index].length() + 1);
                if (null == s[index].name) {
                    return false
                }
                s[index].name = tlist.tag.get(index)
                surf = Model_lwo.lwListAdd(surf, s[index])
                `object`.nsurfs++
            }
            //            polygon.pol[ i].surf.oSet(s[ index]);
            polygon.pol.get(i).surf = s[index] //TODO:should this be an oSet() to preserve the refs?
            i++
        }
        s = null
        return true
    }

    /*
     ======================================================================
     lwGetVertNormals()

     Calculate the vertex normals.  For each polygon vertex, sum the
     normals of the polygons that share the point.  If the normals of the
     current and adjacent polygons form an angle greater than the max
     smoothing angle for the current polygon's surface, the normal of the
     adjacent polygon is excluded from the sum.  It's also excluded if the
     polygons aren't in the same smoothing group.

     Assumes that lwGetPointPolygons(), lwGetPolyNormals() and
     lwResolvePolySurfaces() have already been called.
     ====================================================================== */
    fun lwGetVertNormals(point: lwPointList?, polygon: lwPolygonList?) {
        var j: Int
        var k: Int
        var n: Int
        var g: Int
        var h: Int
        var p: Int
        var a: Float
        j = 0
        while (j < polygon.count) {
            n = 0
            while (n < polygon.pol.get(j).nverts) {
                k = 0
                while (k < 3) {
                    polygon.pol.get(j).getV(n).norm.get(k) = polygon.pol.get(j).norm.get(k)
                    k++
                }
                if (polygon.pol.get(j).surf.smooth <= 0) {
                    n++
                    continue
                }
                p = polygon.pol.get(j).getV(n).index
                g = 0
                while (g < point.pt.get(p).npols) {
                    h = point.pt.get(p).pol.get(g)
                    if (h == j) {
                        g++
                        continue
                    }
                    if (polygon.pol.get(j).smoothgrp != polygon.pol.get(h).smoothgrp) {
                        g++
                        continue
                    }
                    a = idMath.ACos(Model_lwo.dot(polygon.pol.get(j).norm, polygon.pol.get(h).norm))
                    if (a > polygon.pol.get(j).surf.smooth) {
                        g++
                        continue
                    }
                    k = 0
                    while (k < 3) {
                        polygon.pol.get(j).getV(n).norm.get(k) += polygon.pol.get(h).norm.get(k)
                        k++
                    }
                    g++
                }
                Model_lwo.normalize(polygon.pol.get(j).getV(n).norm)
                n++
            }
            j++
        }
    }

    /*
     ======================================================================
     lwFreeTags()

     Free memory used by an lwTagList.
     ====================================================================== */
    fun lwFreeTags(tlist: lwTagList?) {
        var i: Int
        if (tlist != null) {
            if (tlist.tag != null) {
//                for (i = 0; i < tlist.count; i++) {
//                    if (tlist.tag[ i] != null) {
//                        Mem_Free(tlist.tag[ i]);
//                    }
//                }
                tlist.tag = null
            }
            //            memset(tlist, 0, sizeof(lwTagList));
        }
    }

    /*
     ======================================================================
     lwGetTags()

     Read tag strings from a TAGS chunk in an LWO2 file.  The tags are
     added to the lwTagList array.
     ====================================================================== */
    fun lwGetTags(fp: idFile?, ckSize: Int, tList: lwTagList?): Boolean {
        val buf: ByteBuffer?
        val nTags: Int
        val bp: String
        val tags: Array<String?>
        if (ckSize == 0) {
            return true
        }

        /* read the whole chunk */Model_lwo.set_flen(0)
        buf = ByteBuffer.wrap(Model_lwo.getbytes(fp, ckSize))
        if (null == buf) {
            return false
        }

        /* count the strings */bp = String(buf.array())
        tags = bp.split("\u0000+").toTypedArray() //TODO:make sure we don't need the \0?
        nTags = tags.size

        /* expand the string array to hold the new tags */tList.offset = tList.count
        tList.count += nTags
        val oldtag = tList.tag
        tList.tag = arrayOfNulls<String?>(tList.count)
        if (tList.count == 0) {
            return false
        }
        if (oldtag != null) {
            System.arraycopy(oldtag, 0, tList.tag, 0, tList.offset)
        }

        /* copy the new tags to the tag array */System.arraycopy(tags, 0, tList.tag, tList.offset, nTags)
        return true
    }

    /*
     ======================================================================
     lwGetPolygonTags()

     Read polygon tags from a PTAG chunk in an LWO2 file.
     ====================================================================== */
    fun lwGetPolygonTags(fp: idFile?, cksize: Int, tlist: lwTagList?, plist: lwPolygonList?): Boolean {
        val type: Int
        var rlen = 0
        var i: Int
        var j: Int
        val nodeMap: MutableMap<Int?, lwNode?> = HashMap(2)
        Model_lwo.set_flen(0)
        type = Model_lwo.getU4(fp)
        rlen = Model_lwo.get_flen()
        if (rlen < 0) {
            return false
        }
        if (type != Model_lwo.ID_SURF && type != Model_lwo.ID_PART && type != Model_lwo.ID_SMGP) {
            fp.Seek((cksize - 4).toLong(), fsOrigin_t.FS_SEEK_CUR)
            return true
        }
        while (rlen < cksize) {
            i = Model_lwo.getVX(fp) + plist.offset
            j = Model_lwo.getVX(fp) + tlist.offset
            rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > cksize) {
                return false
            }

            //add static reference if it doesthnt exist.
            if (!nodeMap.containsKey(j)) {
                nodeMap[j] = lwSurface()
            }
            when (type) {
                Model_lwo.ID_SURF -> {
                    plist.pol.get(i).surf = nodeMap[j] as lwSurface?
                    plist.pol.get(i).part = j
                }
                Model_lwo.ID_PART -> plist.pol.get(i).part = j
                Model_lwo.ID_SMGP -> plist.pol.get(i).smoothgrp = j
            }
        }
        return true
    }

    /*
     ======================================================================
     lwGetTHeader()

     Read a texture map header from a SURF.BLOK in an LWO2 file.  This is
     the first subchunk in a BLOK, and its contents are common to all three
     texture types.
     ====================================================================== */
    fun lwGetTHeader(fp: idFile?, hsz: Int, tex: lwTexture?): Int {
        var id: Int
        var sz: Short
        val pos: Int
        var rlen: Int


        /* remember where we started */Model_lwo.set_flen(0)
        pos = fp.Tell()

        /* ordinal string */tex.ord = Model_lwo.getS0(fp)

        /* first subchunk header */id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        if (0 > Model_lwo.get_flen()) {
            return 0
        }

        /* process subchunks as they're encountered */while (true) {
            sz += (sz and 1).toShort()
            Model_lwo.set_flen(0)
            when (id) {
                Model_lwo.ID_CHAN -> tex.chan = Model_lwo.getU4(fp).toLong()
                Model_lwo.ID_OPAC -> {
                    tex.opac_type = Model_lwo.getU2(fp)
                    tex.opacity.`val` = Model_lwo.getF4(fp)
                    tex.opacity.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_ENAB -> tex.enabled = Model_lwo.getU2(fp)
                Model_lwo.ID_NEGA -> tex.negative = Model_lwo.getU2(fp)
                Model_lwo.ID_AXIS -> tex.axis = Model_lwo.getU2(fp)
                else -> {}
            }

            /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the texture header subchunk? */if (hsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */Model_lwo.set_flen(0)
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (6 != Model_lwo.get_flen()) {
                return 0
            }
        }
        Model_lwo.set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetTMap()

     Read a texture map from a SURF.BLOK in an LWO2 file.  The TMAP
     defines the mapping from texture to world or object coordinates.
     ====================================================================== */
    fun lwGetTMap(fp: idFile?, tmapsz: Int, tmap: lwTMap?): Int {
        var id: Int
        var sz: Short
        var rlen: Int
        val pos: Int
        var i: Int
        pos = fp.Tell()
        id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        if (0 > Model_lwo.get_flen()) {
            return 0
        }
        while (true) {
            sz += (sz and 1).toShort()
            Model_lwo.set_flen(0)
            when (id) {
                Model_lwo.ID_SIZE -> {
                    i = 0
                    while (i < 3) {
                        tmap.size.`val`.get(i) = Model_lwo.getF4(fp)
                        i++
                    }
                    tmap.size.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_CNTR -> {
                    i = 0
                    while (i < 3) {
                        tmap.center.`val`.get(i) = Model_lwo.getF4(fp)
                        i++
                    }
                    tmap.center.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_ROTA -> {
                    i = 0
                    while (i < 3) {
                        tmap.rotate.`val`.get(i) = Model_lwo.getF4(fp)
                        i++
                    }
                    tmap.rotate.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_FALL -> {
                    tmap.fall_type = Model_lwo.getU2(fp).toInt()
                    i = 0
                    while (i < 3) {
                        tmap.falloff.`val`.get(i) = Model_lwo.getF4(fp)
                        i++
                    }
                    tmap.falloff.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_OREF -> tmap.ref_object = Model_lwo.getS0(fp)
                Model_lwo.ID_CSYS -> tmap.coord_sys = Model_lwo.getU2(fp).toInt()
                else -> {}
            }

            /* error while reading the current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the TMAP subchunk? */if (tmapsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */Model_lwo.set_flen(0)
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (6 != Model_lwo.get_flen()) {
                return 0
            }
        }
        Model_lwo.set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetImageMap()

     Read an lwImageMap from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetImageMap(fp: idFile?, rsz: Int, tex: lwTexture?): Int {
        var id: Int
        var sz: Short
        var rlen: Int
        val pos: Int
        pos = fp.Tell()
        id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        if (0 > Model_lwo.get_flen()) {
            return 0
        }
        while (true) {
            sz += (sz and 1).toShort()
            Model_lwo.set_flen(0)
            when (id) {
                Model_lwo.ID_TMAP -> if (0 == Model_lwo.lwGetTMap(fp, sz.toInt(), tex.tmap)) {
                    return 0
                }
                Model_lwo.ID_PROJ -> tex.param.imap.projection = Model_lwo.getU2(fp).toInt()
                Model_lwo.ID_VMAP -> tex.param.imap.vmap_name = Model_lwo.getS0(fp)
                Model_lwo.ID_AXIS -> tex.param.imap.axis = Model_lwo.getU2(fp).toInt()
                Model_lwo.ID_IMAG -> tex.param.imap.cindex = Model_lwo.getVX(fp)
                Model_lwo.ID_WRAP -> {
                    tex.param.imap.wrapw_type = Model_lwo.getU2(fp).toInt()
                    tex.param.imap.wraph_type = Model_lwo.getU2(fp).toInt()
                }
                Model_lwo.ID_WRPW -> {
                    tex.param.imap.wrapw.`val` = Model_lwo.getF4(fp)
                    tex.param.imap.wrapw.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_WRPH -> {
                    tex.param.imap.wraph.`val` = Model_lwo.getF4(fp)
                    tex.param.imap.wraph.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_AAST -> {
                    tex.param.imap.aas_flags = Model_lwo.getU2(fp).toInt()
                    tex.param.imap.aa_strength = Model_lwo.getF4(fp)
                }
                Model_lwo.ID_PIXB -> tex.param.imap.pblend = Model_lwo.getU2(fp).toInt()
                Model_lwo.ID_STCK -> {
                    tex.param.imap.stck.`val` = Model_lwo.getF4(fp)
                    tex.param.imap.stck.eindex = Model_lwo.getVX(fp)
                }
                Model_lwo.ID_TAMP -> {
                    tex.param.imap.amplitude.`val` = Model_lwo.getF4(fp)
                    tex.param.imap.amplitude.eindex = Model_lwo.getVX(fp)
                }
                else -> {}
            }

            /* error while reading the current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the image map? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */Model_lwo.set_flen(0)
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (6 != Model_lwo.get_flen()) {
                return 0
            }
        }
        Model_lwo.set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetProcedural()

     Read an lwProcedural from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetProcedural(fp: idFile?, rsz: Int, tex: lwTexture?): Int {
        var id: Int
        var sz: Short
        var rlen: Int
        val pos: Int
        pos = fp.Tell()
        id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        if (0 > Model_lwo.get_flen()) {
            return 0
        }
        while (true) {
            sz += (sz and 1).toShort()
            Model_lwo.set_flen(0)
            when (id) {
                Model_lwo.ID_TMAP -> if (0 == Model_lwo.lwGetTMap(fp, sz.toInt(), tex.tmap)) {
                    return 0
                }
                Model_lwo.ID_AXIS -> tex.param.proc.axis = Model_lwo.getU2(fp).toInt()
                Model_lwo.ID_VALU -> {
                    tex.param.proc.value[0] = Model_lwo.getF4(fp)
                    if (sz >= 8) {
                        tex.param.proc.value[1] = Model_lwo.getF4(fp)
                    }
                    if (sz >= 12) {
                        tex.param.proc.value[2] = Model_lwo.getF4(fp)
                    }
                }
                Model_lwo.ID_FUNC -> {
                    tex.param.proc.name = Model_lwo.getS0(fp)
                    rlen = Model_lwo.get_flen()
                    tex.param.proc.data = Model_lwo.getbytes(fp, sz - rlen)
                }
                else -> {}
            }

            /* error while reading the current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the procedural block? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */Model_lwo.set_flen(0)
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (6 != Model_lwo.get_flen()) {
                return 0
            }
        }
        Model_lwo.set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetGradient()

     Read an lwGradient from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetGradient(fp: idFile?, rsz: Int, tex: lwTexture?): Int {
        var id: Int
        var sz: Short
        var rlen: Int
        val pos: Int
        var i: Int
        var j: Int
        var nkeys: Int
        pos = fp.Tell()
        id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        if (0 > Model_lwo.get_flen()) {
            return 0
        }
        while (true) {
            sz += (sz and 1).toShort()
            Model_lwo.set_flen(0)
            when (id) {
                Model_lwo.ID_TMAP -> if (0 == Model_lwo.lwGetTMap(fp, sz.toInt(), tex.tmap)) {
                    return 0
                }
                Model_lwo.ID_PNAM -> tex.param.grad.paramname = Model_lwo.getS0(fp)
                Model_lwo.ID_INAM -> tex.param.grad.itemname = Model_lwo.getS0(fp)
                Model_lwo.ID_GRST -> tex.param.grad.start = Model_lwo.getF4(fp)
                Model_lwo.ID_GREN -> tex.param.grad.end = Model_lwo.getF4(fp)
                Model_lwo.ID_GRPT -> tex.param.grad.repeat = Model_lwo.getU2(fp).toInt()
                Model_lwo.ID_FKEY -> {
                    nkeys = sz.toInt() // sizeof(lwGradKey);
                    tex.param.grad.key = arrayOfNulls<lwGradKey?>(nkeys) // Mem_ClearedAlloc(nkeys);
                    if (null == tex.param.grad.key) {
                        return 0
                    }
                    i = 0
                    while (i < nkeys) {
                        tex.param.grad.key[i].value = Model_lwo.getF4(fp)
                        j = 0
                        while (j < 4) {
                            tex.param.grad.key[i].rgba[j] = Model_lwo.getF4(fp)
                            j++
                        }
                        i++
                    }
                }
                Model_lwo.ID_IKEY -> {
                    nkeys = sz / 2
                    tex.param.grad.ikey = ShortArray(nkeys) // Mem_ClearedAlloc(nkeys);
                    if (null == tex.param.grad.ikey) {
                        return 0
                    }
                    i = 0
                    while (i < nkeys) {
                        tex.param.grad.ikey[i] = Model_lwo.getU2(fp)
                        i++
                    }
                }
                else -> {}
            }

            /* error while reading the current subchunk? */rlen = Model_lwo.get_flen()
            if (rlen < 0 || rlen > sz) {
                return 0
            }

            /* skip unread parts of the current subchunk */if (rlen < sz) {
                fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
            }

            /* end of the gradient? */if (rsz <= fp.Tell() - pos) {
                break
            }

            /* get the next subchunk header */Model_lwo.set_flen(0)
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (6 != Model_lwo.get_flen()) {
                return 0
            }
        }
        Model_lwo.set_flen(fp.Tell() - pos)
        return 1
    }

    /*
     ======================================================================
     lwGetTexture()

     Read an lwTexture from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetTexture(fp: idFile?, bloksz: Int, type: Int): lwTexture? {
        var tex: lwTexture?
        var sz: Short
        val ok: Int
        tex = lwTexture() // Mem_ClearedAlloc(sizeof(lwTexture));
        if (null == tex) {
            return null
        }
        tex.type = type.toLong()
        tex.tmap.size.`val`.get(2) = 1.0f
        tex.tmap.size.`val`.get(1) = tex.tmap.size.`val`.get(2)
        tex.tmap.size.`val`.get(0) = tex.tmap.size.`val`.get(1)
        tex.opacity.`val` = 1.0f
        tex.enabled = 1
        sz = Model_lwo.getU2(fp)
        if (0 == Model_lwo.lwGetTHeader(fp, sz.toInt(), tex)) {
            tex = null
            return null
        }
        sz = (bloksz - sz - 6).toShort()
        ok = when (type) {
            Model_lwo.ID_IMAP -> Model_lwo.lwGetImageMap(fp, sz.toInt(), tex)
            Model_lwo.ID_PROC -> Model_lwo.lwGetProcedural(fp, sz.toInt(), tex)
            Model_lwo.ID_GRAD -> Model_lwo.lwGetGradient(fp, sz.toInt(), tex)
            else -> TempDump.btoi(!fp.Seek(sz.toLong(), fsOrigin_t.FS_SEEK_CUR))
        }
        if (0 == ok) {
            lwFreeTexture.getInstance().run(tex)
            return null
        }
        Model_lwo.set_flen(bloksz)
        return tex
    }

    /*
     ======================================================================
     lwGetShader()

     Read a shader record from a SURF.BLOK in an LWO2 file.
     ====================================================================== */
    fun lwGetShader(fp: idFile?, bloksz: Int): lwPlugin? {
        val shdr: lwPlugin
        var id: Int
        var sz: Short
        var hsz: Int
        var rlen: Int
        val pos: Int
        shdr = lwPlugin() //Mem_ClearedAlloc(sizeof(lwPlugin));
        if (null == shdr) {
            return null
        }
        pos = fp.Tell()
        Model_lwo.set_flen(0)
        hsz = Model_lwo.getU2(fp).toInt()
        shdr.ord = Model_lwo.getS0(fp)
        id = Model_lwo.getU4(fp)
        sz = Model_lwo.getU2(fp)
        Fail@ if (true) {
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }
            while (hsz > 0) {
                sz += (sz and 1).toShort()
                hsz -= sz.toInt()
                if (id == Model_lwo.ID_ENAB) {
                    shdr.flags = Model_lwo.getU2(fp).toInt()
                    break
                } else {
                    fp.Seek(sz.toLong(), fsOrigin_t.FS_SEEK_CUR)
                    id = Model_lwo.getU4(fp)
                    sz = Model_lwo.getU2(fp)
                }
            }
            id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }
            while (true) {
                sz += (sz and 1).toShort()
                Model_lwo.set_flen(0)
                when (id) {
                    Model_lwo.ID_FUNC -> {
                        shdr.name = Model_lwo.getS0(fp)
                        rlen = Model_lwo.get_flen()
                        shdr.data = Model_lwo.getbytes(fp, sz - rlen)
                    }
                    else -> {}
                }

                /* error while reading the current subchunk? */rlen = Model_lwo.get_flen()
                if (rlen < 0 || rlen > sz) {
                    break@Fail
                }

                /* skip unread parts of the current subchunk */if (rlen < sz) {
                    fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the shader block? */if (bloksz <= fp.Tell() - pos) {
                    break
                }

                /* get the next subchunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                sz = Model_lwo.getU2(fp)
                if (6 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            Model_lwo.set_flen(fp.Tell() - pos)
            return shdr
        }

//        Fail:
        lwFreePlugin.getInstance().run(shdr)
        return null
    }

    /*
     ======================================================================
     add_texture()

     Finds the surface channel (lwTParam or lwCParam) to which a texture is
     applied, then calls lwListInsert().
     ====================================================================== */
    fun add_texture(surf: lwSurface?, tex: lwTexture?): Int {
        val list: lwTexture?
        list = when (tex.chan.toInt()) {
            Model_lwo.ID_COLR -> surf.color.tex
            Model_lwo.ID_LUMI -> surf.luminosity.tex
            Model_lwo.ID_DIFF -> surf.diffuse.tex
            Model_lwo.ID_SPEC -> surf.specularity.tex
            Model_lwo.ID_GLOS -> surf.glossiness.tex
            Model_lwo.ID_REFL -> surf.reflection.`val`.tex
            Model_lwo.ID_TRAN -> surf.transparency.`val`.tex
            Model_lwo.ID_RIND -> surf.eta.tex
            Model_lwo.ID_TRNL -> surf.translucency.tex
            Model_lwo.ID_BUMP -> surf.bump.tex
            else -> return 0
        }
        Model_lwo.lwListInsert(list, tex, compare_textures())
        return 1
    }

    /*
     ======================================================================
     lwDefaultSurface()

     Allocate and initialize a surface.
     ====================================================================== */
    fun lwDefaultSurface(): lwSurface? {
        val surf: lwSurface
        surf = lwSurface() // Mem_ClearedAlloc(sizeof(lwSurface));
        if (null == surf) {
            return null
        }
        surf.color.rgb.get(0) = 0.78431f
        surf.color.rgb.get(1) = 0.78431f
        surf.color.rgb.get(2) = 0.78431f
        surf.diffuse.`val` = 1.0f
        surf.glossiness.`val` = 0.4f
        surf.bump.`val` = 1.0f
        surf.eta.`val` = 1.0f
        surf.sideflags = 1
        return surf
    }

    /*
     ======================================================================
     lwGetSurface()

     Read an lwSurface from an LWO2 file.
     ====================================================================== */
    fun lwGetSurface(fp: idFile?, cksize: Int): lwSurface? {
        val surf: lwSurface
        var tex: lwTexture?
        var shdr: lwPlugin?
        var id: Int
        var type: Int
        var sz: Short
        val pos: Int
        var rlen: Int


        /* allocate the Surface structure */surf = lwSurface() // Mem_ClearedAlloc(sizeof(lwSurface));
        Fail@ if (true) {
//            if (null == surf) {
//                break Fail;
//            }

            /* non-zero defaults */
            surf.color.rgb.get(0) = 0.78431f
            surf.color.rgb.get(1) = 0.78431f
            surf.color.rgb.get(2) = 0.78431f
            surf.diffuse.`val` = 1.0f
            surf.glossiness.`val` = 0.4f
            surf.bump.`val` = 1.0f
            surf.eta.`val` = 1.0f
            surf.sideflags = 1

            /* remember where we started */Model_lwo.set_flen(0)
            pos = fp.Tell()

            /* names */surf.name = Model_lwo.getS0(fp)
            surf.srcname = Model_lwo.getS0(fp)

            /* first subchunk header */id = Model_lwo.getU4(fp)
            sz = Model_lwo.getU2(fp)
            if (0 > Model_lwo.get_flen()) {
                break@Fail
            }

            /* process subchunks as they're encountered */while (true) {
                sz += (sz and 1).toShort()
                Model_lwo.set_flen(0)
                when (id) {
                    Model_lwo.ID_COLR -> {
                        surf.color.rgb.get(0) = Model_lwo.getF4(fp)
                        surf.color.rgb.get(1) = Model_lwo.getF4(fp)
                        surf.color.rgb.get(2) = Model_lwo.getF4(fp)
                        surf.color.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_LUMI -> {
                        surf.luminosity.`val` = Model_lwo.getF4(fp)
                        surf.luminosity.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_DIFF -> {
                        surf.diffuse.`val` = Model_lwo.getF4(fp)
                        surf.diffuse.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_SPEC -> {
                        surf.specularity.`val` = Model_lwo.getF4(fp)
                        surf.specularity.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_GLOS -> {
                        surf.glossiness.`val` = Model_lwo.getF4(fp)
                        surf.glossiness.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_REFL -> {
                        surf.reflection.`val`.`val` = Model_lwo.getF4(fp)
                        surf.reflection.`val`.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_RFOP -> surf.reflection.options = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_RIMG -> surf.reflection.cindex = Model_lwo.getVX(fp)
                    Model_lwo.ID_RSAN -> surf.reflection.seam_angle = Model_lwo.getF4(fp)
                    Model_lwo.ID_TRAN -> {
                        surf.transparency.`val`.`val` = Model_lwo.getF4(fp)
                        surf.transparency.`val`.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_TROP -> surf.transparency.options = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_TIMG -> surf.transparency.cindex = Model_lwo.getVX(fp)
                    Model_lwo.ID_RIND -> {
                        surf.eta.`val` = Model_lwo.getF4(fp)
                        surf.eta.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_TRNL -> {
                        surf.translucency.`val` = Model_lwo.getF4(fp)
                        surf.translucency.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_BUMP -> {
                        surf.bump.`val` = Model_lwo.getF4(fp)
                        surf.bump.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_SMAN -> surf.smooth = Model_lwo.getF4(fp)
                    Model_lwo.ID_SIDE -> surf.sideflags = Model_lwo.getU2(fp).toInt()
                    Model_lwo.ID_CLRH -> {
                        surf.color_hilite.`val` = Model_lwo.getF4(fp)
                        surf.color_hilite.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_CLRF -> {
                        surf.color_filter.`val` = Model_lwo.getF4(fp)
                        surf.color_filter.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_ADTR -> {
                        surf.add_trans.`val` = Model_lwo.getF4(fp)
                        surf.add_trans.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_SHRP -> {
                        surf.dif_sharp.`val` = Model_lwo.getF4(fp)
                        surf.dif_sharp.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_GVAL -> {
                        surf.glow.`val` = Model_lwo.getF4(fp)
                        surf.glow.eindex = Model_lwo.getVX(fp)
                    }
                    Model_lwo.ID_LINE -> {
                        surf.line.enabled = 1
                        if (sz >= 2) {
                            surf.line.flags = Model_lwo.getU2(fp).toInt()
                        }
                        if (sz >= 6) {
                            surf.line.size.`val` = Model_lwo.getF4(fp)
                        }
                        if (sz >= 8) {
                            surf.line.size.eindex = Model_lwo.getVX(fp)
                        }
                    }
                    Model_lwo.ID_ALPH -> {
                        surf.alpha_mode = Model_lwo.getU2(fp).toInt()
                        surf.alpha = Model_lwo.getF4(fp)
                    }
                    Model_lwo.ID_AVAL -> surf.alpha = Model_lwo.getF4(fp)
                    Model_lwo.ID_BLOK -> {
                        type = Model_lwo.getU4(fp)
                        when (type) {
                            Model_lwo.ID_IMAP, Model_lwo.ID_PROC, Model_lwo.ID_GRAD -> {
                                tex = Model_lwo.lwGetTexture(fp, sz - 4, type)
                                if (null == tex) {
                                    break@Fail
                                }
                                if (0 == Model_lwo.add_texture(surf, tex)) {
                                    lwFreeTexture.getInstance().run(tex)
                                }
                                Model_lwo.set_flen(4 + Model_lwo.get_flen())
                            }
                            Model_lwo.ID_SHDR -> {
                                shdr = Model_lwo.lwGetShader(fp, sz - 4)
                                if (null == shdr) {
                                    break@Fail
                                }
                                Model_lwo.lwListInsert(surf.shader, shdr, compare_shaders())
                                ++surf.nshaders
                                Model_lwo.set_flen(4 + Model_lwo.get_flen())
                            }
                        }
                    }
                    else -> {}
                }

                /* error while reading current subchunk? */rlen = Model_lwo.get_flen()
                if (rlen < 0 || rlen > sz) {
                    break@Fail
                }

                /* skip unread parts of the current subchunk */if (rlen < sz) {
                    fp.Seek((sz - rlen).toLong(), fsOrigin_t.FS_SEEK_CUR)
                }

                /* end of the SURF chunk? */if (cksize <= fp.Tell() - pos) {
                    break
                }

                /* get the next subchunk header */Model_lwo.set_flen(0)
                id = Model_lwo.getU4(fp)
                sz = Model_lwo.getU2(fp)
                if (6 != Model_lwo.get_flen()) {
                    break@Fail
                }
            }
            return surf
        }

//        Fail:
        if (surf != null) {
            lwFreeSurface.getInstance().run(surf)
        }
        return null
    }

    fun dot(a: FloatArray?, b: FloatArray?): Float {
        return a.get(0) * b.get(0) + a.get(1) * b.get(1) + a.get(2) * b.get(2)
    }

    fun cross(a: FloatArray?, b: FloatArray?, c: FloatArray?) {
        c.get(0) = a.get(1) * b.get(2) - a.get(2) * b.get(1)
        c.get(1) = a.get(2) * b.get(0) - a.get(0) * b.get(2)
        c.get(2) = a.get(0) * b.get(1) - a.get(1) * b.get(0)
    }

    fun normalize(v: FloatArray?) {
        val r: Float
        r = idMath.Sqrt(Model_lwo.dot(v, v))
        if (r > 0) {
            v.get(0) /= r
            v.get(1) /= r
            v.get(2) /= r
        }
    }

    /*
     ======================================================================
     lwGetVMap()

     Read an lwVMap from a VMAP or VMAD chunk in an LWO2.
     ====================================================================== */
    fun lwGetVMap(fp: idFile?, cksize: Int, ptoffset: Int, poloffset: Int, perpoly: Int): lwVMap? {
        var buf: ByteBuffer?
        //        String b[];
        val vmap: lwVMap
        var f: FloatArray
        var i: Int
        var j: Int
        var npts: Int
        val rlen: Int


        /* read the whole chunk */Model_lwo.set_flen(0)
        buf = ByteBuffer.wrap(Model_lwo.getbytes(fp, cksize))
        if (null == buf) {
            return null
        }
        vmap = lwVMap() // Mem_ClearedAlloc(sizeof(lwVMap));
        //        if (null == vmap) {
//            buf = null
//            return null;
//        }

        /* initialize the vmap */vmap.perpoly = perpoly

//        buf = buf;
        Model_lwo.set_flen(0)
        vmap.type = Model_lwo.sgetU4(buf).toLong()
        vmap.dim = Model_lwo.sgetU2(buf).toInt()
        vmap.name = Model_lwo.sgetS0(buf)
        rlen = Model_lwo.get_flen()

        /* count the vmap records */npts = 0
        while (buf.hasRemaining()) { //( bp < buf + cksize ) {
            i = Model_lwo.sgetVX(buf)
            if (perpoly != 0) {
                i = Model_lwo.sgetVX(buf)
            }
            buf.position(buf.position() + vmap.dim * (java.lang.Float.SIZE / java.lang.Byte.SIZE))
            ++npts
        }

        /* allocate the vmap */vmap.nverts = npts
        vmap.vindex = IntArray(npts) // Mem_ClearedAlloc(npts);
        Fail@ if (true) {
//            if (null == vmap.vindex) {
//                break Fail;
//            }
            if (perpoly != 0) {
                vmap.pindex = IntArray(npts) // Mem_ClearedAlloc(npts);
                //                if (null == vmap.pindex) {
//                    break Fail;
//                }
            }
            if (vmap.dim > 0) {
                vmap.`val` = arrayOfNulls<FloatArray?>(npts) // Mem_ClearedAlloc(npts);
                //                if (null == vmap.val) {
//                    break Fail;
//                }
//                f = new float[npts];// Mem_ClearedAlloc(npts);
//                if (null == f) {
//                    break Fail;
//                }
                i = 0
                while (i < npts) {

//                    vmap.val[i] = f[i] * vmap.dim;
                    vmap.`val`.get(i) = FloatArray(vmap.dim)
                    i++
                }
            }

            /* fill in the vmap values */buf.position(rlen)
            i = 0
            while (i < npts) {
                vmap.vindex.get(i) = Model_lwo.sgetVX(buf)
                if (perpoly != 0) {
                    vmap.pindex.get(i) = Model_lwo.sgetVX(buf)
                }
                j = 0
                while (j < vmap.dim) {
                    vmap.`val`.get(i).get(j) = Model_lwo.sgetF4(buf)
                    j++
                }
                i++
            }
            buf = null
            return vmap
        }

//        Fail:
        if (buf != null) {
            buf = null
        }
        lwFreeVMap.getInstance().run(vmap)
        return null
    }

    /*
     ======================================================================
     lwGetPointVMaps()

     Fill in the lwVMapPt structure for each point.
     ====================================================================== */
    fun lwGetPointVMaps(point: lwPointList?, vmap: lwVMap?): Boolean {
        var vm: lwVMap?
        var i: Int
        var j: Int
        var n: Int

        /* count the number of vmap values for each point */vm = vmap
        while (vm != null) {
            if (0 == vm.perpoly) {
                i = 0
                while (i < vm.nverts) {
                    ++point.pt.get(vm.vindex.get(i)).nvmaps
                    i++
                }
            }
            vm = vm.next
        }

        /* allocate vmap references for each mapped point */i = 0
        while (i < point.count) {
            if (point.pt.get(i).nvmaps != 0) {
                point.pt.get(i).vm = Stream.generate { lwVMapPt() }.limit(point.pt.get(i).nvmaps.toLong())
                    .toArray { _Dummy_.__Array__() } // Mem_ClearedAlloc(point.pt[ i].nvmaps);
                if (null == point.pt.get(i).vm) {
                    return false
                }
                point.pt.get(i).nvmaps = 0
            }
            i++
        }

        /* fill in vmap references for each mapped point */vm = vmap
        while (vm != null) {
            if (0 == vm.perpoly) {
                i = 0
                while (i < vm.nverts) {
                    j = vm.vindex.get(i)
                    n = point.pt.get(j).nvmaps
                    point.pt.get(j).vm.get(n).vmap = vm
                    point.pt.get(j).vm.get(n).index = i
                    ++point.pt.get(j).nvmaps
                    i++
                }
            }
            vm = vm.next
        }
        return true
    }

    /*
     ======================================================================
     lwGetPolyVMaps()

     Fill in the lwVMapPt structure for each polygon vertex.
     ====================================================================== */
    fun lwGetPolyVMaps(polygon: lwPolygonList?, vmap: lwVMap?): Boolean {
        var vm: lwVMap?
        var pv: lwPolVert?
        var i: Int
        var j: Int

        /* count the number of vmap values for each polygon vertex */vm = vmap
        while (vm != null) {
            if (vm.perpoly != 0) {
                i = 0
                while (i < vm.nverts) {
                    j = 0
                    while (j < polygon.pol.get(vm.pindex.get(i)).nverts) {
                        pv = polygon.pol.get(vm.pindex.get(i)).getV(j)
                        if (vm.vindex.get(i) == pv.index) {
                            ++pv.nvmaps
                            break
                        }
                        j++
                    }
                    i++
                }
            }
            vm = vm.next
        }

        /* allocate vmap references for each mapped vertex */i = 0
        while (i < polygon.count) {
            j = 0
            while (j < polygon.pol.get(i).nverts) {
                pv = polygon.pol.get(i).getV(j)
                if (pv.nvmaps != 0) {
                    pv.vm = lwVMapPt.generateArray(pv.nvmaps)
                    if (null == pv.vm) {
                        return false
                    }
                    pv.nvmaps = 0
                }
                j++
            }
            i++
        }

        /* fill in vmap references for each mapped point */vm = vmap
        while (vm != null) {
            if (vm.perpoly != 0) {
                i = 0
                while (i < vm.nverts) {
                    j = 0
                    while (j < polygon.pol.get(vm.pindex.get(i)).nverts) {
                        pv = polygon.pol.get(vm.pindex.get(i)).getV(j)
                        if (vm.vindex.get(i) == pv.index) {
                            pv.vm.get(pv.nvmaps).vmap = vm
                            pv.vm.get(pv.nvmaps).index = i
                            ++pv.nvmaps
                            break
                        }
                        j++
                    }
                    i++
                }
            }
            vm = vm.next
        }
        return true
    }

    /* generic linked list */
    internal abstract class lwNode : NiLLABLE<lwNode?> {
        var NULL = false

        //        lwNode next, prev;
        var data: Any? = null
        override fun oSet(node: lwNode?): lwNode? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun isNULL(): Boolean {
            return NULL
        }

        abstract fun getNext(): lwNode?
        abstract fun setNext(next: lwNode?)
        abstract fun getPrev(): lwNode?
        abstract fun setPrev(prev: lwNode?)

        companion object {
            fun getPosition(n: lwNode?): Int {
                var n = n
                var position = 0
                while (n != null) {
                    position++
                    n = n.getPrev()
                }
                return position
            }

            fun getPosition(n: lwNode?, pos: Int): lwNode? {
                var n = n
                var position = getPosition(n)
                if (position > pos) {
                    while (position != pos) {
                        if (null == n) { //TODO:make sure the returning null isn't recast into an int somewhere.
                            break
                        }
                        position--
                        n = n.getPrev()
                    }
                } else {
                    while (position != pos) {
                        if (null == n) {
                            break
                        }
                        position--
                        n = n.getNext()
                    }
                }
                return n
            }
        }
    }

    /* plug-in reference */
    internal class lwPlugin : lwNode() {
        var flags = 0
        var name: String? = null
        var next: lwPlugin? = null
        var prev: lwPlugin? = null
        var ord: String? = null

        //        Object data;
        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwPlugin?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwPlugin?
        }
    }

    /* envelopes */
    internal class lwKey : lwNode() {
        var bias = 0f
        var continuity = 0f
        var next: lwKey? = null
        var prev: lwKey? = null
        var param: FloatArray? = FloatArray(4)
        var shape // ID_TCB, ID_BEZ2, etc.
                : Long = 0
        var tension = 0f
        var time = 0f
        var value = 0f
        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwKey?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwKey?
        }
    }

    internal class lwEnvelope : lwNode() {
        var behavior: IntArray? = IntArray(2) // pre and post (extrapolation)
        var cfilter // linked list of channel filters
                : lwPlugin? = null
        var index = 0
        var key // linked list of keys
                : lwKey? = null
        var name: String? = null
        var ncfilters = 0
        var next: lwEnvelope? = null
        var prev: lwEnvelope? = null
        var nkeys = 0
        var type = 0
        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwEnvelope?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwEnvelope?
        }
    }

    /* values that can be enveloped */
    internal class lwEParam {
        var eindex = 0
        var `val` = 0f
    }

    internal class lwVParam {
        var eindex = 0
        var `val`: FloatArray? = FloatArray(3)
    }

    /* clips */
    internal class lwClipStill {
        var name: String? = null
    }

    internal class lwClipSeq {
        var digits = 0
        var end = 0
        var flags = 0
        var offset = 0
        var prefix // filename before sequence digits
                : String? = null
        var start = 0
        var suffix // after digits, e.g. extensions
                : String? = null
    }

    internal class lwClipAnim {
        var data: Any? = null
        var name: String? = null
        var server // anim loader plug-in
                : String? = null
    }

    internal class lwClipXRef {
        var clip: lwClip? = null
        var index = 0
        var string: String? = null
    }

    internal class lwClipCycle {
        var hi = 0
        var lo = 0
        var name: String? = null
    }

    internal class lwClip : lwNode() {
        var brightness: lwEParam? = lwEParam()
        var contrast: lwEParam? = lwEParam()
        var duration = 0f
        var frame_rate = 0f
        var gamma: lwEParam? = lwEParam()
        var hue: lwEParam? = lwEParam()
        var ifilter // linked list of image filters
                : lwPlugin? = null
        var index = 0
        var negative = 0
        var next: lwClip? = null
        var prev: lwClip? = null
        var nifilters = 0
        var npfilters = 0
        var pfilter // linked list of pixel filters
                : lwPlugin? = null
        var saturation: lwEParam? = lwEParam()
        var source: Model_lwo.lwClip.Source? = Model_lwo.lwClip.Source()
        var start_time = 0f
        var type // ID_STIL, ID_ISEQ, etc.
                = 0

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwClip?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwClip?
        }

        internal class Source {
            var anim: lwClipAnim? = lwClipAnim()
            var cycle: lwClipCycle? = lwClipCycle()
            var seq: lwClipSeq? = lwClipSeq()
            var still: lwClipStill? = lwClipStill()
            var xref: lwClipXRef? = lwClipXRef()
        }
    }

    /* textures */
    internal class lwTMap {
        var center: lwVParam? = lwVParam()
        var coord_sys = 0
        var fall_type = 0
        var falloff: lwVParam? = lwVParam()
        var ref_object: String? = null
        var rotate: lwVParam? = lwVParam()
        var size: lwVParam? = lwVParam()
    }

    internal class lwImageMap {
        var aa_strength = 0f
        var aas_flags = 0
        var amplitude: lwEParam? = lwEParam()
        var axis = 0
        var cindex = 0
        var pblend = 0
        var projection = 0
        var stck: lwEParam? = lwEParam()
        var vmap_name: String? = null
        var wraph: lwEParam? = lwEParam()
        var wraph_type = 0
        var wrapw: lwEParam? = lwEParam()
        var wrapw_type = 0
    }

    internal class lwProcedural {
        var axis = 0
        var data: Any? = null
        var name: String? = null
        var value: FloatArray? = FloatArray(3)
    }

    internal class lwGradKey {
        var next: lwGradKey? = null
        var prev: lwGradKey? = null
        var rgba: FloatArray? = FloatArray(4)
        var value = 0f
    }

    internal class lwGradient {
        var end = 0f
        var ikey // array of interpolation codes
                : ShortArray?
        var itemname: String? = null
        var key // array of gradient keys
                : Array<lwGradKey?>?
        var paramname: String? = null
        var repeat = 0
        var start = 0f
    }

    internal class lwTexture : lwNode() {
        var axis: Short = 0
        var chan: Long = 0
        var enabled: Short = 0
        var negative: Short = 0
        var next: lwTexture? = null
        var prev: lwTexture? = null
        var opac_type: Short = 0
        var opacity: lwEParam? = lwEParam()
        var ord: String? = null
        var param: Model_lwo.lwTexture.Param? = Model_lwo.lwTexture.Param()
        var tmap: lwTMap? = lwTMap()
        var type: Long = 0
        override fun oSet(node: lwNode?): lwNode? {
            val tempNode = node as lwTexture?
            NULL = false
            next = tempNode.next
            prev = tempNode.prev
            ord = tempNode.ord
            type = tempNode.type
            chan = tempNode.chan
            opac_type = tempNode.opac_type
            enabled = tempNode.enabled
            negative = tempNode.negative
            axis = tempNode.axis
            return this
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwTexture?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwTexture?
        }

        internal class Param {
            var grad: lwGradient? = lwGradient()
            var imap: lwImageMap? = lwImageMap()
            var proc: lwProcedural? = lwProcedural()
        }

        init {
            NULL = true
        }
    }

    /* values that can be textured */
    internal class lwTParam {
        var eindex = 0
        var tex: lwTexture? = lwTexture() // linked list of texture layers
        var `val` = 0f
    }

    internal class lwCParam {
        var eindex = 0
        var rgb: FloatArray? = FloatArray(3)
        var tex: lwTexture? = lwTexture() // linked list of texture layers
    }

    /* surfaces */
    internal class lwGlow {
        var enabled: Short = 0
        var intensity: lwEParam? = null
        var size: lwEParam? = null
        var type: Short = 0
    }

    internal class lwRMap {
        var cindex = 0
        var options = 0
        var seam_angle = 0f
        var `val`: lwTParam? = lwTParam()
    }

    internal class lwLine {
        var enabled: Short = 0
        var flags = 0
        var size: lwEParam? = lwEParam()
    }

    internal class lwSurface : lwNode() {
        var add_trans: lwEParam? = lwEParam()
        var alpha = 0f
        var alpha_mode = 0
        var bump: lwTParam? = lwTParam()
        var color: lwCParam? = lwCParam()
        var color_filter: lwEParam? = lwEParam()
        var color_hilite: lwEParam? = lwEParam()
        var dif_sharp: lwEParam? = lwEParam()
        var diffuse: lwTParam? = lwTParam()
        var eta: lwTParam? = lwTParam()
        var glossiness: lwTParam? = lwTParam()
        var glow: lwEParam? = lwEParam()
        var line: lwLine? = lwLine()
        var luminosity: lwTParam? = lwTParam()
        var name: String? = null
        var next: lwSurface? = null
        var prev: lwSurface? = null
        var nshaders = 0
        var reflection: lwRMap? = lwRMap()
        var shader: lwPlugin? = lwPlugin() // linked list of shaders
        var sideflags = 0
        var smooth = 0f
        var specularity: lwTParam? = lwTParam()
        var srcname: String? = null
        var translucency: lwTParam? = lwTParam()
        var transparency: lwRMap? = lwRMap()
        override fun oSet(node: lwNode?): lwNode? {
            val surface = node as lwSurface?
            next = surface.next
            prev = surface.prev
            name = surface.name
            srcname = surface.srcname
            color = surface.color
            luminosity = surface.luminosity
            diffuse = surface.diffuse
            specularity = surface.specularity
            glossiness = surface.glossiness
            reflection = surface.reflection
            transparency = surface.transparency
            eta = surface.eta
            translucency = surface.translucency
            bump = surface.bump
            smooth = surface.smooth
            sideflags = surface.sideflags
            alpha = surface.alpha
            alpha_mode = surface.alpha_mode
            color_hilite = surface.color_hilite
            color_filter = surface.color_filter
            add_trans = surface.add_trans
            dif_sharp = surface.dif_sharp
            glow = surface.glow
            line = surface.line
            shader = surface.shader
            nshaders = surface.nshaders
            return this
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwSurface?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwSurface?
        }

        override fun hashCode(): Int {
            var hash = 5
            hash = 41 * hash + Objects.hashCode(name)
            return hash
        }

        //TODO:make sure the name is enough for equality.
        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as lwSurface?
            return name == other.name
        }
    }

    /* vertex maps */
    internal class lwVMap : lwNode() {
        var dim = 0
        var name: String? = null
        var next: lwVMap? = null
        var prev: lwVMap? = null
        var nverts = 0

        // added by duffy
        var offset = 0
        var perpoly = 0
        var pindex // array of polygon indexes
                : IntArray?
        var type: Long = 0
        var `val`: Array<FloatArray?>?
        var vindex // array of point indexes
                : IntArray?

        private fun clear() {
            next = null
            prev = null
            name = null
            type = 0
            dim = 0
            nverts = 0
            perpoly = 0
            vindex = null
            pindex = null
            `val` = null
            offset = 0
        }

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwVMap?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwVMap?
        }
    }

    internal class lwVMapPt {
        var index // vindex or pindex element
                = 0
        var vmap: lwVMap? = null

        companion object {
            fun generateArray(length: Int): Array<lwVMapPt?>? {
                return Stream.generate { lwVMapPt() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }
    }

    /* points and polygons */
    internal class lwPoint {
        var npols // number of polygons sharing the point
                = 0
        var nvmaps = 0
        var pol // array of polygon indexes
                : IntArray?
        var pos: FloatArray? = FloatArray(3)
        var vm // array of vmap references
                : Array<lwVMapPt?>?

        constructor()
        constructor(`val`: lwPoint?) {
            npols = `val`.npols
            nvmaps = `val`.nvmaps
            pol = `val`.pol
            pos = `val`.pos
            vm = `val`.vm
        }
    }

    internal class lwPolVert {
        var index // index into the point array
                = 0
        var norm: FloatArray? = FloatArray(3)
        var nvmaps = 0
        var vm // array of vmap references
                : Array<lwVMapPt?>?
    }

    internal class lwPolygon {
        var flags = 0
        var norm: FloatArray? = FloatArray(3)
        var nverts = 0
        var part // part index
                = 0
        var smoothgrp // smoothing group
                = 0
        var surf: lwSurface? = null
        var type: Long = 0
        private var v // array of vertex records
                : Array<lwPolVert?>?
        private var vOffset // the offset from the start of v to point towards.
                = 0

        fun getV(index: Int): lwPolVert? {
            return v.get(vOffset + index)
        }

        fun setV(v: Array<lwPolVert?>?, vOffset: Int) {
            this.v = v
            this.vOffset = vOffset
        }

        constructor()
        constructor(`val`: lwPolygon?) {
            flags = `val`.flags
            norm = `val`.norm
            nverts = `val`.nverts
            part = `val`.part
            smoothgrp = `val`.smoothgrp
            surf = `val`.surf
            type = `val`.type
            v = `val`.v
            vOffset = `val`.vOffset
        }
    }

    internal class lwPointList {
        var count = 0
        var offset // only used during reading
                = 0
        var pt // array of points
                : Array<lwPoint?>?
    }

    internal class lwPolygonList {
        var count = 0
        var offset // only used during reading
                = 0
        var pol // array of polygons
                : Array<lwPolygon?>?
        var vcount // total number of vertices
                = 0
        var voffset // only used during reading
                = 0
    }

    /* geometry layers */
    internal class lwLayer : lwNode() {
        var bbox: FloatArray? = FloatArray(6)
        var flags = 0
        var index = 0
        var name: String? = null
        var next: lwLayer? = null
        var prev: lwLayer? = null
        var nvmaps = 0
        var parent = 0
        var pivot: FloatArray? = FloatArray(3)
        var point: lwPointList? = lwPointList()
        var polygon: lwPolygonList? = lwPolygonList()
        var vmap // linked list of vmaps
                : lwVMap? = null

        override fun getNext(): lwNode? {
            return next
        }

        override fun setNext(next: lwNode?) {
            this.next = next as lwLayer?
        }

        override fun getPrev(): lwNode? {
            return prev
        }

        override fun setPrev(prev: lwNode?) {
            this.prev = prev as lwLayer?
        }
    }

    /* tag strings */
    internal class lwTagList {
        var count = 0
        var offset // only used during reading
                = 0
        var tag // array of strings
                : Array<String?>?
    }

    /* an object */
    internal class lwObject {
        val taglist: lwTagList? = lwTagList()
        var clip // linked list of clips
                : lwClip? = null
        var env // linked list of envelopes
                : lwEnvelope? = null
        var layer // linked list of layers
                : lwLayer? = null
        var nclips = 0
        var nenvs = 0
        var nlayers = 0
        var nsurfs = 0
        var surf // linked list of surfaces
                : lwSurface? = null
        var timeStamp: LongArray? = longArrayOf(0)
    }

    /*
     ======================================================================

     Converted from lwobject sample prog from LW 6.5 SDK.

     ======================================================================
     */
    internal abstract class LW {
        abstract fun run(p: Any?)
    }

    /*
     ======================================================================
     lwFreeClip()

     Free memory used by an lwClip.
     ====================================================================== */
    @Deprecated("")
    class lwFreeClip private constructor() : LW() {
        @Deprecated("")
        override fun run(p: Any?) {
            var clip = p as lwClip?
            if (clip != null) {
//                lwListFree(clip.ifilter, lwFreePlugin.getInstance());
//                lwListFree(clip.pfilter, lwFreePlugin.getInstance());
                when (clip.type) {
                    Model_lwo.ID_STIL -> {
                        if (clip.source.still.name != null) {
                            clip.source.still.name = null
                        }
                    }
                    Model_lwo.ID_ISEQ -> {
                        if (clip.source.seq.suffix != null) {
                            clip.source.seq.suffix = null
                        }
                        if (clip.source.seq.prefix != null) {
                            clip.source.seq.prefix = null
                        }
                    }
                    Model_lwo.ID_ANIM -> {
                        if (clip.source.anim.server != null) {
                            clip.source.anim.server = null
                        }
                        if (clip.source.anim.name != null) {
                            clip.source.anim.name = null
                        }
                    }
                    Model_lwo.ID_XREF -> {
                        if (clip.source.xref.string != null) {
                            clip.source.xref.string = null
                        }
                    }
                    Model_lwo.ID_STCC -> {
                        if (clip.source.cycle.name != null) {
                            clip.source.cycle.name = null
                        }
                    }
                }
                clip = null
            }
        }

        companion object {
            private val instance: LW? = lwFreeClip()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    @Deprecated("")
    class lwFree private constructor() : LW() {
        override fun run(p: Any?) {
//        Mem_Free(ptr);
        }

        companion object {
            private val instance: LW? = lwFree()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeEnvelope()

     Free the memory used by an lwEnvelope.
     ====================================================================== */
    @Deprecated("")
    class lwFreeEnvelope private constructor() : LW() {
        override fun run(p: Any?) {
            var env = p as lwEnvelope?
            if (env != null) {
                if (env.name != null) {
                    env.name = null
                }
                //                lwListFree(env.key, lwFree.getInstance());
//                lwListFree(env.cfilter, lwFreePlugin.getInstance());
                env = null
            }
        }

        companion object {
            private val instance: LW? = lwFreeEnvelope()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    class compare_keys : cmp_t<lwKey?> {
        override fun compare(k1: lwKey?, k2: lwKey?): Int {
            return if (k1.time > k2.time) 1 else if (k1.time < k2.time) -1 else 0
        }
    }

    /*
     ======================================================================
     lwFreeLayer()

     Free memory used by an lwLayer.
     ====================================================================== */
    class lwFreeLayer private constructor() : LW() {
        override fun run(p: Any?) {
            var layer = p as lwLayer?
            if (layer != null) {
                if (layer.name != null) {
                    layer.name = null
                }
                Model_lwo.lwFreePoints(layer.point)
                Model_lwo.lwFreePolygons(layer.polygon)
                //                lwListFree(layer.vmap, lwFreeVMap.getInstance());
                layer = null
            }
        }

        companion object {
            private val instance: LW? = lwFreeLayer()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreePlugin()

     Free the memory used by an lwPlugin.
     ====================================================================== */
    class lwFreePlugin private constructor() : LW() {
        override fun run(o: Any?) {
            throw TODO_Exception()
            //            lwPlugin p = (lwPlugin) o;
//            if (p != null) {
//                if (p.ord != null) {
//                    Mem_Free(p.ord);
//                }
//                if (p.name != null) {
//                    Mem_Free(p.name);
//                }
//                if (p.data != null) {
//                    Mem_Free(p.data);
//                }
//                Mem_Free(p);
//            }
        }

        companion object {
            private val instance: LW? = lwFreePlugin()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeTexture()

     Free the memory used by an lwTexture.
     ====================================================================== */
    class lwFreeTexture private constructor() : LW() {
        override fun run(p: Any?) {
            var t = p as lwTexture?
            if (t != null) {
                if (t.ord != null) {
//                    Mem_Free(t.ord);
                    t.ord = null
                }
                when (t.type.toInt()) {
                    Model_lwo.ID_IMAP -> if (t.param.imap.vmap_name != null) {
//                            Mem_Free(t.param.imap.vmap_name);
                        t.param.imap.vmap_name = null
                    }
                    Model_lwo.ID_PROC -> {
                        if (t.param.proc.name != null) {
//                            Mem_Free(t.param.proc.name);
                            t.param.proc.name = null
                        }
                        if (t.param.proc.data != null) {
//                            Mem_Free(t.param.proc.data);
                            t.param.proc.data = null
                        }
                    }
                    Model_lwo.ID_GRAD -> {
                        if (t.param.grad.key != null) {
//                            Mem_Free(t.param.grad.key);
                            t.param.grad.key = null
                        }
                        if (t.param.grad.ikey != null) {
//                            Mem_Free(t.param.grad.ikey);
                            t.param.grad.ikey = null
                        }
                    }
                }
                if (t.tmap.ref_object != null) {
//                    Mem_Free(t.tmap.ref_object);
                    t.tmap.ref_object = null
                }
                t = null
            }
        }

        companion object {
            private val instance: LW? = lwFreeTexture()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    /*
     ======================================================================
     lwFreeSurface()

     Free the memory used by an lwSurface.
     ====================================================================== */
    class lwFreeSurface private constructor() : LW() {
        override fun run(p: Any?) {
            var surf = p as lwSurface?
            if (surf != null) {
                if (surf.name != null) {
                    surf.name = null
                }
                if (surf.srcname != null) {
                    surf.srcname = null
                }
                //
//                lwListFree(surf.shader, lwFreePlugin.getInstance());
//
//                lwListFree(surf.color.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.luminosity.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.diffuse.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.specularity.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.glossiness.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.reflection.val.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.transparency.val.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.eta.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.translucency.tex, lwFreeTexture.getInstance());
//                lwListFree(surf.bump.tex, lwFreeTexture.getInstance());
//
                surf = null
            }
        }

        companion object {
            private val instance: LW? = lwFreeSurface()
            fun getInstance(): LW? {
                return instance
            }
        }
    }

    /*
     ======================================================================
     compare_textures()
     compare_shaders()

     Callbacks for the lwListInsert() function, which is called to add
     textures to surface channels and shaders to surfaces.
     ====================================================================== */
    class compare_textures : cmp_t<lwTexture?> {
        override fun compare(a: lwTexture?, b: lwTexture?): Int {
            return a.ord.compareTo(b.ord)
        }
    }

    class compare_shaders : cmp_t<lwPlugin?> {
        override fun compare(a: lwPlugin?, b: lwPlugin?): Int {
            return a.ord.compareTo(b.ord)
        }
    }

    /*
     ======================================================================
     lwFreeVMap()

     Free memory used by an lwVMap.
     ====================================================================== */
    class lwFreeVMap private constructor() : LW() {
        override fun run(p: Any?) {
            val vmap = p as lwVMap?
            if (vmap != null) {
                if (vmap.name != null) {
                    vmap.name = null
                }
                if (vmap.vindex != null) {
                    vmap.vindex = null
                }
                if (vmap.pindex != null) {
                    vmap.pindex = null
                }
                if (vmap.`val` != null) {
//                    if (vmap.val[0] != 0f) {
//                        Mem_Free(vmap.val[0]);
//                    }
                    vmap.`val` = null
                }
                vmap.clear()
            }
        }

        companion object {
            private val instance: LW? = lwFreeVMap()
            fun getInstance(): LW? {
                return instance
            }
        }
    }
}