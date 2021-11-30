package neo.framework

import neo.Sound.snd_shader.idSoundShader
import neo.Sound.snd_system
import neo.TempDump
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.*
import neo.framework.DeclAF.idDeclAF
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclFX.idDeclFX
import neo.framework.DeclPDA.*
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.DeclTable.idDeclTable
import neo.framework.FileSystem_h.idFileList
import neo.framework.File_h.idFile
import neo.idlib.*
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Lib.idException
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.hashing.MD5
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.math.BigInteger
import java.nio.*
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Stream

/**
 *
 */
object DeclManager {
    //
    val DECL_LEXER_FLAGS =
        Lexer.LEXFL_NOSTRINGCONCAT or  // multiple strings seperated by whitespaces are not concatenated
                Lexer.LEXFL_NOSTRINGESCAPECHARS or  // no escape characters inside strings
                Lexer.LEXFL_ALLOWPATHNAMES or  // allow path seperators in names
                Lexer.LEXFL_ALLOWMULTICHARLITERALS or  // allow multi character literals
                Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT or  // allow multiple strings seperated by '\' to be concatenated
                Lexer.LEXFL_NOFATALERRORS // just set a flag instead of fatal erroring
    val listDeclStrings: Array<String?>? = arrayOf("current", "all", "ever", null)
    const val GET_HUFFMAN_FREQUENCIES = false

    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    //
    /*
     ====================================================================================

     decl text huffman compression

     ====================================================================================
     */
    const val MAX_HUFFMAN_SYMBOLS = 256
    const val USE_COMPRESSED_DECLS = true

    //
    // compression ratio = 64%
    val huffmanFrequencies: IntArray? = intArrayOf(
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00078fb6, 0x000352a7, 0x00000002, 0x00000001, 0x0002795e, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00049600, 0x000000dd, 0x00018732, 0x0000005a, 0x00000007, 0x00000092, 0x0000000a, 0x00000919,
        0x00002dcf, 0x00002dda, 0x00004dfc, 0x0000039a, 0x000058be, 0x00002d13, 0x00014d8c, 0x00023c60,
        0x0002ddb0, 0x0000d1fc, 0x000078c4, 0x00003ec7, 0x00003113, 0x00006b59, 0x00002499, 0x0000184a,
        0x0000250b, 0x00004e38, 0x000001ca, 0x00000011, 0x00000020, 0x000023da, 0x00000012, 0x00000091,
        0x0000000b, 0x00000b14, 0x0000035d, 0x0000137e, 0x000020c9, 0x00000e11, 0x000004b4, 0x00000737,
        0x000006b8, 0x00001110, 0x000006b3, 0x000000fe, 0x00000f02, 0x00000d73, 0x000005f6, 0x00000be4,
        0x00000d86, 0x0000014d, 0x00000d89, 0x0000129b, 0x00000db3, 0x0000015a, 0x00000167, 0x00000375,
        0x00000028, 0x00000112, 0x00000018, 0x00000678, 0x0000081a, 0x00000677, 0x00000003, 0x00018112,
        0x00000001, 0x000441ee, 0x000124b0, 0x0001fa3f, 0x00026125, 0x0005a411, 0x0000e50f, 0x00011820,
        0x00010f13, 0x0002e723, 0x00003518, 0x00005738, 0x0002cc26, 0x0002a9b7, 0x0002db81, 0x0003b5fa,
        0x000185d2, 0x00001299, 0x00030773, 0x0003920d, 0x000411cd, 0x00018751, 0x00005fbd, 0x000099b0,
        0x00009242, 0x00007cf2, 0x00002809, 0x00005a1d, 0x00000001, 0x00005a1d, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001,
        0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001
    )
    var huffmanCodes = Stream.generate { huffmanCode_s() }.limit(DeclManager.MAX_HUFFMAN_SYMBOLS.toLong())
        .toArray<huffmanCode_s?> { _Dummy_.__Array__() } //new huffmanCode_s[MAX_HUFFMAN_SYMBOLS];
    var huffmanTree: huffmanNode_s? = null
    var maxHuffmanBits = 0
    var totalCompressedLength = 0
    var totalUncompressedLength = 0
    private val declManagerLocal: idDeclManagerLocal = idDeclManagerLocal()
    var declManager: idDeclManager? = DeclManager.declManagerLocal
    fun idDeclAllocator(   /*<idDecl>*/theMobRules: Class<*>?): Constructor<idDecl?>? {
        //TODO:use reflection. EDIT:cross fingers.
        try {
            return theMobRules.getConstructor()
        } catch (ex: NoSuchMethodException) {
            Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
        } catch (ex: SecurityException) {
            Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
        }
        return null
    }

    /*
     ================
     ClearHuffmanFrequencies
     ================
     */
    fun ClearHuffmanFrequencies() {
        var i: Int
        i = 0
        while (i < DeclManager.MAX_HUFFMAN_SYMBOLS) {
            DeclManager.huffmanFrequencies[i] = 1
            i++
        }
    }

    /*
     ================
     InsertHuffmanNode
     ================
     */
    fun InsertHuffmanNode(firstNode: huffmanNode_s?, node: huffmanNode_s?): huffmanNode_s? {
        var firstNode = firstNode
        var n: huffmanNode_s?
        var lastNode: huffmanNode_s?
        lastNode = null
        n = firstNode
        while (n != null) {
            if (node.frequency <= n.frequency) {
                break
            }
            lastNode = n
            n = n.next
        }
        if (lastNode != null) {
            node.next = lastNode.next
            lastNode.next = node
        } else {
            node.next = firstNode
            firstNode = node
        }
        return firstNode
    }

    /*
     ================
     BuildHuffmanCode_r
     ================
     */
    fun BuildHuffmanCode_r(
        node: huffmanNode_s?,
        code: huffmanCode_s?,
        codes: Array<huffmanCode_s?>? /*[MAX_HUFFMAN_SYMBOLS]*/
    ) {
        if (node.symbol == -1) {
            val newCode = huffmanCode_s(code)
            assert(code.numBits < codes.get(0).bits.size * 8)
            newCode.numBits++
            if (code.numBits > DeclManager.maxHuffmanBits) {
                DeclManager.maxHuffmanBits = newCode.numBits
            }
            DeclManager.BuildHuffmanCode_r(node.children.get(0), newCode, codes)
            newCode.bits.get(code.numBits shr 5) = newCode.bits.get(code.numBits shr 5) or (1 shl (code.numBits and 31))
            DeclManager.BuildHuffmanCode_r(node.children.get(1), newCode, codes)
        } else {
            assert(code.numBits <= codes.get(0).bits.size * 8)
            codes.get(node.symbol) = huffmanCode_s(code)
        }
    }

    /*
     ================
     FreeHuffmanTree_r
     ================
     */
    fun FreeHuffmanTree_r(node: huffmanNode_s?) {
        if (node.symbol == -1) {
            DeclManager.FreeHuffmanTree_r(node.children.get(0))
            DeclManager.FreeHuffmanTree_r(node.children.get(1))
        }
        //	delete node;
    }

    /*
     ================
     HuffmanHeight_r
     ================
     */
    fun HuffmanHeight_r(node: huffmanNode_s?): Int {
        if (node == null) {
            return -1
        }
        val left = DeclManager.HuffmanHeight_r(node.children.get(0))
        val right = DeclManager.HuffmanHeight_r(node.children.get(1))
        return if (left > right) {
            left + 1
        } else right + 1
    }

    //
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    //    
    /*
     ================
     SetupHuffman
     ================
     */
    fun SetupHuffman() {
        var i: Int
        val height: Int
        var firstNode: huffmanNode_s?
        var node: huffmanNode_s
        val code: huffmanCode_s
        firstNode = null
        i = 0
        while (i < DeclManager.MAX_HUFFMAN_SYMBOLS) {
            node = huffmanNode_s()
            node.symbol = i
            node.frequency = DeclManager.huffmanFrequencies[i]
            node.next = null
            node.children.get(0) = null
            node.children.get(1) = null
            firstNode = DeclManager.InsertHuffmanNode(firstNode, node)
            i++
        }
        i = 1
        while (i < DeclManager.MAX_HUFFMAN_SYMBOLS) {
            node = huffmanNode_s()
            node.symbol = -1
            node.frequency = firstNode.frequency + firstNode.next.frequency
            node.next = null
            node.children.get(0) = firstNode
            node.children.get(1) = firstNode.next
            firstNode = DeclManager.InsertHuffmanNode(firstNode.next.next, node)
            i++
        }
        DeclManager.maxHuffmanBits = 0
        code = huffmanCode_s() //memset( &code, 0, sizeof( code ) );
        DeclManager.BuildHuffmanCode_r(firstNode, code, DeclManager.huffmanCodes)
        DeclManager.huffmanTree = firstNode
        height = DeclManager.HuffmanHeight_r(firstNode)
        assert(DeclManager.maxHuffmanBits == height)
    }

    /*
     ================
     ShutdownHuffman
     ================
     */
    fun ShutdownHuffman() {
        if (DeclManager.huffmanTree != null) {
            DeclManager.FreeHuffmanTree_r(DeclManager.huffmanTree)
        }
    }

    /*
     ================
     HuffmanCompressText
     ================
     */
    private fun HuffmanCompressText(
        text: String?,
        textLength: Int,
        compressed: ByteBuffer?,
        maxCompressedSize: Int
    ): Int {
        var i: Int
        var j: Int
        val msg = idBitMsg()
        DeclManager.totalUncompressedLength += textLength
        msg.Init(compressed, maxCompressedSize)
        msg.BeginWriting()
        i = 0
        while (i < textLength) {
            val code: huffmanCode_s? = DeclManager.huffmanCodes[text.get(i).code]
            j = 0
            while (j < code.numBits shr 5) {
                msg.WriteBits(code.bits.get(j) as Int, 32)
                j++
            }
            if (code.numBits and 31 != 0) {
                msg.WriteBits(code.bits.get(j) as Int, code.numBits and 31)
            }
            i++
        }
        DeclManager.totalCompressedLength += msg.GetSize()
        return msg.GetSize()
    }

    /*
     ================
     HuffmanDecompressText
     ================
     */
    fun HuffmanDecompressText(
        text: Array<String?>?,
        textLength: Int,
        compressed: ByteBuffer?,
        compressedSize: Int
    ): Int {
        var i: Int
        var bit: Int
        val msg = idBitMsg()
        var node: huffmanNode_s?
        msg.Init(compressed, compressedSize)
        msg.SetSize(compressedSize)
        msg.BeginReading()
        text.get(0) = ""
        i = 0
        while (i < textLength) {
            node = DeclManager.huffmanTree
            do {
                bit = msg.ReadBits(1)
                node = node.children.get(bit)
                //                System.out.println(bit + ":" + node.symbol);
            } while (node.symbol == -1)
            text.get(0) += node.symbol.toChar()
            i++
        }
        //        text[0] += '\0';
        return msg.GetReadCount()
    }

    fun setDeclManager(declManager: idDeclManager?) {
        DeclManager.declManagerLocal = declManager as idDeclManagerLocal?
        DeclManager.declManager = DeclManager.declManagerLocal
    }

    enum class declState_t {
        DS_UNPARSED, DS_DEFAULTED,  // set if a parse failed due to an error, or the lack of any source
        DS_PARSED
    }

    /*
     ===============================================================================

     Declaration Manager

     All "small text" data types, like materials, sound shaders, fx files,
     entity defs, etc. are managed uniformly, allowing reloading, purging,
     listing, printing, etc. All "large text" data types that never have more
     than one declaration in a given file, like maps, models, AAS files, etc.
     are not handled here.

     A decl will never, ever go away once it is created. The manager is
     guaranteed to always return the same decl pointer for a decl type/name
     combination. The index of a decl in the per type list also stays the
     same throughout the lifetime of the engine. Although the pointer to
     a decl always stays the same, one should never maintain pointers to
     data inside decls. The data stored in a decl is not garranteed to stay
     the same for more than one engine frame.

     The decl indexes of explicitely defined decls are garrenteed to be
     consistent based on the parsed decl files. However, the indexes of
     implicit decls may be different based on the order in which levels
     are loaded.

     The decl namespaces are separate for each type. Comments for decls go
     above the text definition to keep them associated with the proper decl.

     During decl parsing, errors should never be issued, only warnings
     followed by a call to MakeDefault().

     ===============================================================================
     */
    enum class declType_t {
        DECL_TABLE,  //0
        DECL_MATERIAL, DECL_SKIN, DECL_SOUND, DECL_ENTITYDEF, DECL_MODELDEF, DECL_FX, DECL_PARTICLE, DECL_AF, DECL_PDA, DECL_VIDEO, DECL_AUDIO, DECL_EMAIL, DECL_MODELEXPORT, DECL_MAPDEF,  //14

        // new decl types can be added here
        _15_, _16_, _17_, _18_, _19_, _20_, _21_, _22_, _23_, _24_, _25_, _26_, _27_, _28_, _29_, _30_, _31_, DECL_MAX_TYPES //32
    }

    abstract class idDeclBase {
        // public	abstract 				~idDeclBase() {};
        abstract fun GetName(): String?
        abstract fun GetType(): declType_t?
        abstract fun GetState(): declState_t?
        abstract fun IsImplicit(): Boolean
        abstract fun IsValid(): Boolean
        abstract fun Invalidate()

        @Throws(idException::class)
        abstract fun Reload()

        @Throws(idException::class)
        abstract fun EnsureNotPurged()
        abstract fun Index(): Int
        abstract fun GetLineNum(): Int
        abstract fun GetFileName(): String?
        abstract fun GetText(text: Array<String?>?)
        abstract fun GetTextLength(): Int
        abstract fun SetText(text: String?)

        @Throws(idException::class)
        abstract fun ReplaceSourceFileText(): Boolean
        abstract fun SourceFileChanged(): Boolean

        @Throws(idException::class)
        abstract fun MakeDefault()
        abstract fun EverReferenced(): Boolean
        abstract fun SetDefaultText(): Boolean
        abstract fun DefaultDefinition(): String?

        @Throws(idException::class)
        abstract fun Parse(text: String?, textLength: Int): Boolean
        abstract fun FreeData()
        abstract /*size_t*/  fun Size(): Long

        @Throws(idException::class)
        abstract fun List()
        abstract fun Print()
    }

    open class idDecl     //
    //
    // The constructor should initialize variables such that
    // an immediate call to FreeData() does no harm.
    {
        var base: idDeclBase? = null

        // public /*abstract*/ 				~idDecl() {};
        // Returns the name of the decl.
        fun GetName(): String? {
            return base.GetName()
        }

        // Returns the decl type.
        fun GetType(): declType_t? {
            return base.GetType()
        }

        // Returns the decl state which is usefull for finding out if a decl defaulted.
        fun GetState(): declState_t? {
            return base.GetState()
        }

        // Returns true if the decl was defaulted or the text was created with a call to SetDefaultText.
        fun IsImplicit(): Boolean {
            return base.IsImplicit()
        }

        // The only way non-manager code can have an invalid decl is if the *ByIndex()
        // call was used with forceParse = false to walk the lists to look at names
        // without touching the media.
        fun IsValid(): Boolean {
            return base.IsValid()
        }

        // Sets state back to unparsed.
        // Used by decl editors to undo any changes to the decl.
        fun Invalidate() {
            base.Invalidate()
        }

        // if a pointer might possible be stale from a previous level,
        // call this to have it re-parsed
        @Throws(idException::class)
        fun EnsureNotPurged() {
            base.EnsureNotPurged()
        }

        // Returns the index in the per-type list.
        fun Index(): Int {
            return base.Index()
        }

        // Returns the line number the decl starts.
        fun GetLineNum(): Int {
            return base.GetLineNum()
        }

        // Returns the name of the file in which the decl is defined.
        fun GetFileName(): String? {
            return base.GetFileName()
        }

        // Returns the decl text.
        fun GetText(text: Array<String?>?) {
            base.GetText(text)
        }

        // Returns the length of the decl text.
        fun GetTextLength(): Int {
            return base.GetTextLength()
        }

        // Sets new decl text.
        fun SetText(text: String?) {
            base.SetText(text)
        }

        // Saves out new text for the decl.
        // Used by decl editors to replace the decl text in the source file.
        @Throws(idException::class)
        fun ReplaceSourceFileText(): Boolean {
            return base.ReplaceSourceFileText()
        }

        // Returns true if the source file changed since it was loaded and parsed.
        fun SourceFileChanged(): Boolean {
            return base.SourceFileChanged()
        }

        // Frees data and makes the decl a default.
        @Throws(idException::class)
        fun MakeDefault() {
            base.MakeDefault()
        }

        // Returns true if the decl was ever referenced.
        fun EverReferenced(): Boolean {
            return base.EverReferenced()
        }

        // Sets textSource to a default text if necessary.
        // This may be overridden to provide a default definition based on the
        // decl name. For instance materials may default to an implicit definition
        // using a texture with the same name as the decl.
        @Throws(idException::class)
        /*abstract*/ open fun SetDefaultText(): Boolean {
            return base.SetDefaultText()
        }

        // Each declaration type must have a default string that it is guaranteed
        // to parse acceptably. When a decl is not explicitly found, is purged, or
        // has an error while parsing, MakeDefault() will do a FreeData(), then a
        // Parse() with DefaultDefinition(). The defaultDefintion should start with
        // an open brace and end with a close brace.
        /*abstract*/ open fun DefaultDefinition(): String? {
            return base.DefaultDefinition()
        }

        // The manager will have already parsed past the type, name and opening brace.
        // All necessary media will be touched before return.
        // The manager will have called FreeData() before issuing a Parse().
        // The subclass can call MakeDefault() internally at any point if
        // there are parse errors.
        @Throws(idException::class)
        /*abstract*/ open fun Parse(text: String?, textLength: Int): Boolean {
            return base.Parse(text, textLength)
        }

        // Frees any pointers held by the subclass. This may be called before
        // any Parse(), so the constructor must have set sane values. The decl will be
        // invalid after issuing this call, but it will always be immediately followed
        // by a Parse()
        /*abstract*/ open fun FreeData() {
            base.FreeData()
        }

        // If this isn't overridden, it will just print the decl name.
        // The manager will have printed 7 characters on the line already,
        // containing the reference state and index number.
        @Throws(idException::class)
        /*abstract*/ open fun List() {
            base.List()
        }

        // The print function will already have dumped the text source
        // and common data, subclasses can override this to dump more
        // explicit data.
        @Throws(idException::class)
        /*abstract*/ open fun Print() {
            base.Print()
        }

        companion object {
            @Transient
            val SIZE = TempDump.CPP_class.Pointer.SIZE //base is an abstract class.
        }
    }

    abstract class idDeclManager {
        // virtual 					~idDeclManager() {}
        //
        @Throws(idException::class)
        abstract fun Init()
        abstract fun Shutdown()

        @Throws(idException::class)
        abstract fun Reload(force: Boolean)

        @Throws(idException::class)
        abstract fun BeginLevelLoad()
        abstract fun EndLevelLoad()

        // Registers a new decl type.
        @Throws(idException::class)
        abstract fun RegisterDeclType(
            typeName: String?,
            type: declType_t?,
            allocator: Constructor<idDecl?>? /* *(*allocator)()*/
        )

        // Registers a new folder with decl files.
        @Throws(idException::class)
        abstract fun RegisterDeclFolder(folder: String?, extension: String?, defaultType: declType_t?)

        // Returns a checksum for all loaded decl text.
        abstract fun GetChecksum(): BigInteger?

        // Returns the number of decl types.
        abstract fun GetNumDeclTypes(): Int

        // Returns the type name for a decl type.
        @Throws(idException::class)
        abstract fun GetDeclNameFromType(type: declType_t?): String?

        // Returns the decl type for a type name.
        abstract fun GetDeclTypeFromName(typeName: String?): declType_t?

        // If makeDefault is true, a default decl of appropriate type will be created
        // if an explicit one isn't found. If makeDefault is false, NULL will be returned
        // if the decl wasn't explcitly defined.
        @Throws(idException::class)
        abstract fun FindType(type: declType_t?, name: String?, makeDefault: Boolean /*= true*/): idDecl?

        @JvmOverloads
        fun FindType(type: declType_t?, name: idStr?, makeDefault: Boolean = true): idDecl? {
            return FindType(type, name.toString(), makeDefault)
        }

        fun FindType(type: declType_t?, name: String?): idDecl? {
            return FindType(type, idStr(name))
        }

        @Throws(idException::class)
        abstract fun FindDeclWithoutParsing(type: declType_t?, name: String?, makeDefault: Boolean /*= true*/): idDecl?

        @Throws(idException::class)
        open fun FindDeclWithoutParsing(type: declType_t?, name: String?): idDecl? {
            return FindDeclWithoutParsing(type, name, true)
        }

        @Throws(idException::class)
        abstract fun ReloadFile(filename: String?, force: Boolean)

        // Returns the number of decls of the given type.
        @Throws(idException::class)
        abstract fun GetNumDecls(type: Int): Int

        @Throws(idException::class)
        fun GetNumDecls(type: declType_t?): Int {
            return GetNumDecls(TempDump.etoi(type))
        }

        // The complete lists of decls can be walked to populate editor browsers.
        // If forceParse is set false, you can get the decl to check name / filename / etc.
        // without causing it to parse the source and load media.
        @Throws(idException::class)
        abstract fun DeclByIndex(type: declType_t?, index: Int, forceParse: Boolean /*= true*/): idDecl?

        @Throws(idException::class)
        abstract fun DeclByIndex(type: declType_t?, index: Int): idDecl?

        // List and print decls.
        @Throws(idException::class)
        abstract fun ListType(args: CmdArgs.idCmdArgs?, type: declType_t?)

        @Throws(idException::class)
        abstract fun PrintType(args: CmdArgs.idCmdArgs?, type: declType_t?)

        // Creates a new default decl of the given type with the given name in
        // the given file used by editors to create a new decls.
        @Throws(idException::class)
        abstract fun CreateNewDecl(type: declType_t?, name: String?, fileName: String?): idDecl?

        // BSM - Added for the material editors rename capabilities
        abstract fun RenameDecl(type: declType_t?, oldName: String?, newName: String?): Boolean

        // When media files are loaded, a reference line can be printed at a
        // proper indentation if decl_show is set
        @Throws(idException::class)
        abstract fun MediaPrint(fmt: String?, vararg arg: Any?)
        abstract fun WritePrecacheCommands(f: idFile?)

        // Convenience functions for specific types.
        @Throws(idException::class)
        abstract fun FindMaterial(name: idStr?, makeDefault: Boolean /*= true*/): idMaterial?

        @Throws(idException::class)
        fun FindMaterial(name: idStr?): idMaterial? {
            return FindMaterial(name, true)
        }

        @Deprecated("") //name could have a back reference.
        fun FindMaterial(name: String?, makeDefault: Boolean): idMaterial? {
            return FindMaterial(idStr(name), makeDefault)
        }

        @Deprecated("") //name could have a back reference.
        fun FindMaterial(name: String?): idMaterial? {
            return FindMaterial(idStr(name))
        }

        @Throws(idException::class)
        abstract fun FindSkin(name: idStr?, makeDefault: Boolean /* = true*/): idDeclSkin?

        @Throws(idException::class)
        fun FindSkin(name: idStr?): idDeclSkin? {
            return FindSkin(name, true)
        }

        @Deprecated("") //name could have a back reference.
        fun FindSkin(name: String?, makeDefault: Boolean): idDeclSkin? {
            return FindSkin(idStr(name), makeDefault)
        }

        @Deprecated("") //name could have a back reference.
        fun FindSkin(name: String?): idDeclSkin? {
            return FindSkin(idStr(name))
        }

        @Throws(idException::class)
        abstract fun FindSound(name: idStr?, makeDefault: Boolean /* = true*/): idSoundShader?

        @Throws(idException::class)
        fun FindSound(name: idStr?): idSoundShader? {
            return FindSound(name, true)
        }

        @Deprecated("") //name could have a back reference.
        fun FindSound(name: String?, makeDefault: Boolean): idSoundShader? {
            return FindSound(idStr(name), makeDefault)
        }

        @Deprecated("") //name could have a back reference.
        fun FindSound(name: String?): idSoundShader? {
            return FindSound(idStr(name))
        }

        @Throws(idException::class)
        abstract fun MaterialByIndex(index: Int, forceParse: Boolean /*= true*/): idMaterial?

        @Throws(idException::class)
        open fun MaterialByIndex(index: Int): idMaterial? {
            return MaterialByIndex(index, true)
        }

        @Throws(idException::class)
        abstract fun SkinByIndex(index: Int, forceParse: Boolean /*= true */): idDeclSkin?

        @Throws(idException::class)
        open fun SkinByIndex(index: Int): idDeclSkin? {
            return SkinByIndex(index, true)
        }

        @Throws(idException::class)
        abstract fun SoundByIndex(index: Int, forceParse: Boolean /*= true*/): idSoundShader?

        @Throws(idException::class)
        open fun SoundByIndex(index: Int): idSoundShader? {
            return SoundByIndex(index, true)
        }
    }

    class idListDecls_f(private val type: declType_t?) : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            DeclManager.declManager.ListType(args, type)
        }
    }

    class idPrintDecls_f(private val type: declType_t?) : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            DeclManager.declManager.PrintType(args, type)
        }
    }

    internal class idDeclType {
        var allocator //(*allocator)( void );
                : Constructor<idDecl?>? = null
        var type: declType_t? = null
        var typeName: idStr? = null
    }

    internal class idDeclFolder {
        var defaultType: declType_t? = null
        var extension: idStr? = null
        var folder: idStr? = null
    }

    internal class idDeclLocal : idDeclBase() {
        private var checksum // checksum of the decl text
                : BigInteger?
        private var compressedLength // compressed length
                : Int
        private var declState // decl state
                : declState_t?
        private val everReferenced // set to true if the decl was ever used
                : Boolean
        private val index // index in the per-type list
                : Int

        //
        private val name // name of the decl
                : idStr?

        //
        private val nextInFile // next decl in the decl file
                : idDeclLocal?

        //
        private val parsedOutsideLevelLoad // these decls will never be purged
                : Boolean
        private val redefinedInReload // used during file reloading to make sure a decl that has its source removed will be defaulted
                : Boolean
        private var referencedThisLevel // set to true when the decl is used for the current level
                : Boolean
        private var self: idDecl? = null
        private val sourceFile // source file in which the decl was defined
                : idDeclFile?
        private val sourceLine // this is where the actual declaration token starts
                : Int
        private var sourceTextLength // length of decl text in source file
                : Int
        private var sourceTextOffset // offset in source file to decl text
                : Int
        private var textLength // length of textSource
                : Int

        //
        //
        private var textSource // decl text definition
                : ByteBuffer?
        private val type // decl type
                : declType_t?

        override fun GetName(): String? {
            return name.toString()
        }

        override fun GetType(): declType_t? {
            return type
        }

        override fun GetState(): declState_t? {
            return declState
        }

        override fun IsImplicit(): Boolean {
            return sourceFile === DeclManager.declManagerLocal.GetImplicitDeclFile()
        }

        override fun IsValid(): Boolean {
            return declState != declState_t.DS_UNPARSED
        }

        override fun Invalidate() {
            declState = declState_t.DS_UNPARSED
        }

        @Throws(idException::class)
        override fun Reload() {
            sourceFile.Reload(false)
        }

        @Throws(idException::class)
        override fun EnsureNotPurged() {
            if (declState == declState_t.DS_UNPARSED) {
                ParseLocal()
            }
        }

        override fun Index(): Int {
            return index
        }

        override fun GetLineNum(): Int {
            return sourceLine
        }

        override fun GetFileName(): String? {
            return sourceFile?.fileName?.toString() ?: "*invalid*"
        }

        override fun GetText(text: Array<String?>?) {
            if (DeclManager.USE_COMPRESSED_DECLS) {
                DeclManager.HuffmanDecompressText(text, textLength, textSource, compressedLength)
            } else {
                // memcpy( text, textSource, textLength+1 );
            }
        }

        override fun GetTextLength(): Int {
            return textLength
        }

        override fun SetText(text: String?) {
            SetTextLocal(text, text.length)
        }

        @Throws(idException::class)
        override fun ReplaceSourceFileText(): Boolean {
            val oldFileLength: Int
            val newFileLength: Int
            val buffer: ByteArray
            var file: idFile?
            Common.common.Printf("Writing '%s' to '%s'...\n", GetName(), GetFileName())
            if (sourceFile === DeclManager.declManagerLocal.implicitDecls) {
                Common.common.Warning("Can't save implicit declaration %s.", GetName())
                return false
            }

            // get length and allocate buffer to hold the file
            oldFileLength = sourceFile.fileSize
            newFileLength = oldFileLength - sourceTextLength + textLength
            //            buffer = (char[]) Mem_Alloc(Max(newFileLength, oldFileLength));
            buffer = ByteArray(Lib.Companion.Max(newFileLength, oldFileLength))

            // read original file
            if (sourceFile.fileSize != 0) {
                file = FileSystem_h.fileSystem.OpenFileRead(GetFileName())
                if (null == file) {
//                    Mem_Free(buffer);
                    Common.common.Warning("Couldn't open %s for reading.", GetFileName())
                    return false
                }
                if (file.Length() != sourceFile.fileSize || file.Timestamp() != sourceFile.timestamp.get(0)) {
//                    Mem_Free(buffer);
                    Common.common.Warning("The file %s has been modified outside of the engine.", GetFileName())
                    return false
                }
                file.Read(ByteBuffer.wrap(buffer), oldFileLength)
                FileSystem_h.fileSystem.CloseFile(file)
                if (MD5.MD5_BlockChecksum(buffer, oldFileLength) != sourceFile.checksum) {
//                    Mem_Free(buffer);
                    Common.common.Warning("The file %s has been modified outside of the engine.", GetFileName())
                    return false
                }
            }

            // insert new text
            val declText: CharArray //= new char[textLength + 1];
            val declString = arrayOfNulls<String?>(1)
            GetText(declString)
            declText = declString[0].toCharArray()
            //	memmove( buffer + sourceTextOffset + textLength, buffer + sourceTextOffset + sourceTextLength, oldFileLength - sourceTextOffset - sourceTextLength );
            System.arraycopy(
                buffer,
                sourceTextOffset + sourceTextLength,
                buffer,
                sourceTextOffset + textLength,
                oldFileLength - sourceTextOffset - sourceTextLength
            )
            //	memcpy( buffer + sourceTextOffset, declText, textLength );
            System.arraycopy(declText, 0, buffer, sourceTextOffset, textLength)

            // write out new file
            file = FileSystem_h.fileSystem.OpenFileWrite(GetFileName(), "fs_devpath")
            if (null == file) {
//                Mem_Free(buffer);
                Common.common.Warning("Couldn't open %s for writing.", GetFileName())
                return false
            }
            file.Write(ByteBuffer.wrap(buffer), newFileLength)
            FileSystem_h.fileSystem.CloseFile(file)

            // set new file size, checksum and timestamp
            sourceFile.fileSize = newFileLength
            sourceFile.checksum = BigInteger(MD5.MD5_BlockChecksum(buffer, newFileLength))
            FileSystem_h.fileSystem.ReadFile(GetFileName(), null, sourceFile.timestamp)

            // free buffer
//            Mem_Free(buffer);
            // move all decls in the same file
            var decl = sourceFile.decls
            while (decl != null) {
                if (decl.sourceTextOffset > sourceTextOffset) {
                    decl.sourceTextOffset += textLength - sourceTextLength
                }
                decl = decl.nextInFile
            }

            // set new size of text in source file
            sourceTextLength = textLength
            return true
        }

        override fun SourceFileChanged(): Boolean {
            val newLength: Int
            /*ID_TIME_T*/
            val newTimestamp = LongArray(1)
            if (sourceFile.fileSize <= 0) {
                return false
            }
            newLength = FileSystem_h.fileSystem.ReadFile(GetFileName(), null, newTimestamp)
            return newLength != sourceFile.fileSize || newTimestamp != sourceFile.timestamp
        }

        @Throws(idException::class)
        override fun MakeDefault() {
            val defaultText: String?
            DeclManager.declManagerLocal.MediaPrint("DEFAULTED\n")
            declState = declState_t.DS_DEFAULTED
            AllocateSelf()
            defaultText = self.DefaultDefinition()

            // a parse error inside a DefaultDefinition() string could
            // cause an infinite loop, but normal default definitions could
            // still reference other default definitions, so we can't
            // just dump out on the first recursion
            if (++recursionLevel > 100) {
                Common.common.FatalError("idDecl::MakeDefault: bad DefaultDefinition(): %s", defaultText)
            }

            // always free data before parsing
            self.FreeData()

            // parse
            self.Parse(defaultText, defaultText.length)

            // we could still eventually hit the recursion if we have enough Error() calls inside Parse...
            --recursionLevel
        }

        override fun EverReferenced(): Boolean {
            return everReferenced
        }

        override fun Size(): Long {
            return  /*sizeof(idDecl) +*/name.Allocated()
        }

        protected override fun SetDefaultText(): Boolean {
            return false
        }

        protected override fun DefaultDefinition(): String? {
            return "{ }"
        }

        @Throws(idException::class)
        protected override fun Parse(text: String?, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")
            src.SkipBracedSection(false)
            return true
        }

        protected override fun FreeData() {}

        @Throws(idException::class)
        protected override fun List() {
            Common.common.Printf("%s\n", GetName())
        }

        protected override fun Print() {}
        fun AllocateSelf() {
            if (null == self) {
                try {
                    DBG_AllocateSelf++
                    self = DeclManager.declManagerLocal.GetDeclType(TempDump.etoi(type)).allocator.newInstance()
                    self.base = this
                } catch (ex: InstantiationException) {
                    Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
                } catch (ex: IllegalAccessException) {
                    Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
                } catch (ex: IllegalArgumentException) {
                    Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
                } catch (ex: InvocationTargetException) {
                    Logger.getLogger(DeclManager::class.java.name).log(Level.SEVERE, null, ex)
                }
            }
        }

        // Parses the decl definition.
        // After calling parse, a decl will be guaranteed usable.
        @Throws(idException::class)
        fun ParseLocal() {
            var generatedDefaultText = false
            AllocateSelf()

            // always free data before parsing
            self.FreeData()
            DeclManager.declManagerLocal.MediaPrint(
                "parsing %s %s\n",
                DeclManager.declManagerLocal.declTypes.get(type.ordinal).typeName,
                name
            )

            // if no text source try to generate default text
            if (textSource == null) {
                generatedDefaultText = self.SetDefaultText()
            }

            // indent for DEFAULTED or media file references
            DeclManager.declManagerLocal.indent++

            // no text immediately causes a MakeDefault()
            if (textSource == null) {
                MakeDefault()
                DeclManager.declManagerLocal.indent--
                return
            }
            declState = declState_t.DS_PARSED

            // parse
            val declText = arrayOf<String?>(null) /*(char *) _alloca( ( GetTextLength() + 1 ) * sizeof( char ) )*/
            GetText(declText)
            self.Parse(declText[0], GetTextLength())

            // free generated text
            if (generatedDefaultText) {
//                Mem_Free(textSource);
                textSource = null
                textLength = 0
            }
            DeclManager.declManagerLocal.indent--
        }

        // Does a MakeDefualt, but flags the decl so that it
        // will Parse() the next time the decl is found.
        @Throws(idException::class)
        fun Purge() {
            // never purge things that were referenced outside level load,
            // like the console and menu graphics
            if (parsedOutsideLevelLoad) {
                return
            }
            referencedThisLevel = false
            MakeDefault()

            // the next Find() for this will re-parse the real data
            declState = declState_t.DS_UNPARSED
        }

        // Set textSource possible with compression.
        fun SetTextLocal(text: String?, length: Int) {

//            Mem_Free(textSource);
            textSource = null
            checksum = BigInteger(MD5.MD5_BlockChecksum(text, length))
            if (DeclManager.GET_HUFFMAN_FREQUENCIES) {
                for (i in 0 until length) {
//		huffmanFrequencies[((const unsigned char *)text)[i]]++;
                    DeclManager.huffmanFrequencies[text.get(i).code and 0xff]++
                }
            }
            if (DeclManager.USE_COMPRESSED_DECLS) {
                val maxBytesPerCode = DeclManager.maxHuffmanBits + 7 shr 3
                val compressed = ByteBuffer.allocate(length * maxBytesPerCode)
                compressedLength = DeclManager.HuffmanCompressText(text, length, compressed, length * maxBytesPerCode)
                compressed.rewind()
                textSource = compressed //(char *)Mem_Alloc( compressedLength );
                //	memcpy( textSource, compressed, compressedLength );
            } else {
                compressedLength = length
                textSource = TempDump.atobb(text) //(char *) Mem_Alloc( length + 1 );
                //	memcpy( textSource, text, length );
//	textSource[length] = '\0';
            }
            textLength = length
        }

        companion object {
            private var DBG_AllocateSelf = 0
            private var recursionLevel = 0
        }

        init {
            name = idStr("unnamed")
            textSource = null
            textLength = 0
            compressedLength = 0
            sourceFile = null
            sourceTextOffset = 0
            sourceTextLength = 0
            sourceLine = 0
            checksum = BigInteger.ZERO
            type = declType_t.DECL_ENTITYDEF
            index = 0
            declState = declState_t.DS_UNPARSED
            parsedOutsideLevelLoad = false
            referencedThisLevel = false
            everReferenced = false
            redefinedInReload = false
            nextInFile = null
        }
    }

    internal class idDeclFile {
        var checksum: BigInteger?

        //
        var decls: idDeclLocal?
        var defaultType: declType_t?
        var fileName: idStr?
        var fileSize: Int
        var numLines: Int

        //
        /*ID_TIME_T*/  var timestamp: LongArray? = LongArray(1)

        //
        //
        /*
         ================
         idDeclFile::LoadAndParse

         This is used during both the initial load, and any reloads
         ================
         */
        var c_savedMemory = 0

        constructor() {
            fileName = idStr("<implicit file>")
            defaultType = declType_t.DECL_MAX_TYPES
            timestamp.get(0) = 0
            checksum = BigInteger.ZERO
            fileSize = 0
            numLines = 0
            decls = null
        }

        //
        constructor(fileName: String?, defaultType: declType_t?) {
            this.fileName = idStr(fileName)
            this.defaultType = defaultType
            timestamp.get(0) = 0
            checksum = BigInteger.ZERO
            fileSize = 0
            numLines = 0
            decls = null
        }

        /*
         ================
         idDeclFile::Reload

         ForceReload will cause it to reload even if the timestamp hasn't changed
         ================
         */
        @Throws(idException::class)
        fun Reload(force: Boolean) {
            // check for an unchanged timestamp
            if (!force && timestamp.get(0) != 0) {
                /*ID_TIME_T*/
                val testTimeStamp = LongArray(1)
                FileSystem_h.fileSystem.ReadFile(fileName.toString(), null, testTimeStamp)
                if (testTimeStamp == timestamp) {
                    return
                }
            }

            // parse the text
            LoadAndParse()
        }

        @Throws(idException::class)
        fun LoadAndParse(): BigInteger? {
            var i: Int
            var numTypes: Int
            val src = idLexer()
            val token = idToken()
            var startMarker: Int
            val buffer = arrayOf<ByteBuffer?>(null)
            val length: Int
            var size: Int
            var sourceLine: Int
            var name: String
            var newDecl: idDeclLocal?
            var reparse: Boolean

            // load the text
            Common.common.DPrintf("...loading '%s'\n", fileName.toString())
            length = FileSystem_h.fileSystem.ReadFile(fileName.toString(), buffer, timestamp)
            if (length == -1) {
                Common.common.FatalError("couldn't load %s", fileName.toString())
                return BigInteger.ZERO
            }
            if (!src.LoadMemory(TempDump.bbtocb(buffer[0]), length, fileName.toString())) {
                Common.common.Error("Couldn't parse %s", fileName.toString())
                //                Mem_Free(buffer);
                return BigInteger.ZERO
            }

            // mark all the defs that were from the last reload of this file
            run {
                var decl = decls
                while (decl != null) {
                    decl.redefinedInReload = false
                    decl = decl.nextInFile
                }
            }
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            checksum = BigInteger(MD5.MD5_BlockChecksum(buffer[0].array(), length))
            fileSize = length

            // scan through, identifying each individual declaration
            while (true) {
                startMarker = src.GetFileOffset()
                sourceLine = src.GetLineNum()

                // parse the decl type name
                if (!src.ReadToken(token)) {
                    break
                }
                var identifiedType: declType_t? = declType_t.DECL_MAX_TYPES

                // get the decl type from the type name
                numTypes = DeclManager.declManagerLocal.GetNumDeclTypes()
                i = 0
                while (i < numTypes) {
                    val typeInfo: idDeclType? = DeclManager.declManagerLocal.GetDeclType(i)
                    if (typeInfo != null && typeInfo.typeName.Icmp(token.toString()) == 0) {
                        identifiedType = typeInfo.type
                        break
                    }
                    i++
                }
                if (i >= numTypes) {
                    identifiedType = if (token.Icmp("{") == 0) {

                        // if we ever see an open brace, we somehow missed the [type] <name> prefix
                        src.Warning("Missing decl name")
                        src.SkipBracedSection(false)
                        continue
                    } else {
                        if (defaultType == declType_t.DECL_MAX_TYPES) {
                            src.Warning("No type")
                            continue
                        }
                        src.UnreadToken(token)
                        // use the default type
                        defaultType
                    }
                }

                // now parse the name
                if (!src.ReadToken(token)) {
                    src.Warning("Type without definition at end of file")
                    break
                }
                if (0 == token.Icmp("{")) {
                    // if we ever see an open brace, we somehow missed the [type] <name> prefix
                    src.Warning("Missing decl name")
                    src.SkipBracedSection(false)
                    continue
                }

                // FIXME: export decls are only used by the model exporter, they are skipped here for now
                if (identifiedType == declType_t.DECL_MODELEXPORT) {
                    src.SkipBracedSection()
                    continue
                }
                name = token.toString()

                // make sure there's a '{'
                if (!src.ReadToken(token)) {
                    src.Warning("Type without definition at end of file")
                    break
                }
                if (token != "{") {
                    src.Warning("Expecting '{' but found '%s'", token)
                    continue
                }
                src.UnreadToken(token)

                // now take everything until a matched closing brace
                src.SkipBracedSection()
                size = src.GetFileOffset() - startMarker

                // look it up, possibly getting a newly created default decl
                reparse = false
                newDecl = DeclManager.declManagerLocal.FindTypeWithoutParsing(identifiedType, name, false)
                if (newDecl != null) {
                    // update the existing copy
                    if (newDecl.sourceFile !== this || newDecl.redefinedInReload) {
                        src.Warning(
                            "%s '%s' previously defined at %s:%d",
                            DeclManager.declManagerLocal.GetDeclNameFromType(identifiedType),
                            name,
                            newDecl.sourceFile.fileName.toString(),
                            newDecl.sourceLine
                        )
                        continue
                    }
                    if (newDecl.declState != declState_t.DS_UNPARSED) {
                        reparse = true
                    }
                } else {
                    // allow it to be created as a default, then add it to the per-file list
                    newDecl = DeclManager.declManagerLocal.FindTypeWithoutParsing(identifiedType, name, true)
                    newDecl.nextInFile = decls
                    decls = newDecl
                }
                newDecl.redefinedInReload = true
                if (newDecl.textSource != null) {
//                    Mem_Free(newDecl.textSource);
                    newDecl.textSource = null
                }
                newDecl.SetTextLocal(String(buffer[0].array()).substring(startMarker), size)
                newDecl.sourceFile = this
                newDecl.sourceTextOffset = startMarker
                newDecl.sourceTextLength = size
                newDecl.sourceLine = sourceLine
                newDecl.declState = declState_t.DS_UNPARSED

                // if it is currently in use, reparse it immedaitely
                if (reparse) {
                    newDecl.ParseLocal()
                }
            }
            numLines = src.GetLineNum()

//            Mem_Free(buffer);
            // any defs that weren't redefinedInReload should now be defaulted
            var decl = decls
            while (decl != null) {
                if (decl.redefinedInReload == false) {
                    decl.MakeDefault()
                    decl.sourceTextOffset = decl.sourceFile.fileSize
                    decl.sourceTextLength = 0
                    decl.sourceLine = decl.sourceFile.numLines
                }
                decl = decl.nextInFile
            }
            return checksum
        }
    }

    internal class idDeclManagerLocal : idDeclManager() {
        private val hashTables: Array<idHashIndex?>?
        private val linearLists: Array<idList<idDeclLocal?>?>?

        //                               // text definitions were not found. Decls that became default
        //                               // because of a parse error are not in this list.
        private var checksum // checksum of all loaded decl text
                : BigInteger? = null
        private val declFolders: idList<idDeclFolder?>?
        private val declTypes: idList<idDeclType?>?
        private val implicitDecls // this holds all the decls that were created because explicit
                : idDeclFile? = null
        private val indent // for MediaPrint
                = 0
        private var insideLevelLoad = false

        //
        //
        //
        private val loadedFiles: idList<idDeclFile?>?

        @Throws(idException::class)
        override fun Init() {
            Common.common.Printf("----- Initializing Decls -----\n")
            checksum = BigInteger.ZERO
            if (DeclManager.USE_COMPRESSED_DECLS) {
                DeclManager.SetupHuffman()
            }
            if (DeclManager.GET_HUFFMAN_FREQUENCIES) {
                DeclManager.ClearHuffmanFrequencies()
            }

            // decls used throughout the engine
            RegisterDeclType("table", declType_t.DECL_TABLE, DeclManager.idDeclAllocator(idDeclTable::class.java))
            RegisterDeclType("material", declType_t.DECL_MATERIAL, DeclManager.idDeclAllocator(idMaterial::class.java))
            RegisterDeclType("skin", declType_t.DECL_SKIN, DeclManager.idDeclAllocator(idDeclSkin::class.java))
            RegisterDeclType("sound", declType_t.DECL_SOUND, DeclManager.idDeclAllocator(idSoundShader::class.java))
            RegisterDeclType(
                "entityDef",
                declType_t.DECL_ENTITYDEF,
                DeclManager.idDeclAllocator(idDeclEntityDef::class.java)
            )
            RegisterDeclType("mapDef", declType_t.DECL_MAPDEF, DeclManager.idDeclAllocator(idDeclEntityDef::class.java))
            RegisterDeclType("fx", declType_t.DECL_FX, DeclManager.idDeclAllocator(idDeclFX::class.java))
            RegisterDeclType(
                "particle",
                declType_t.DECL_PARTICLE,
                DeclManager.idDeclAllocator(idDeclParticle::class.java)
            )
            RegisterDeclType("articulatedFigure", declType_t.DECL_AF, DeclManager.idDeclAllocator(idDeclAF::class.java))
            RegisterDeclType("pda", declType_t.DECL_PDA, DeclManager.idDeclAllocator(idDeclPDA::class.java))
            RegisterDeclType("email", declType_t.DECL_EMAIL, DeclManager.idDeclAllocator(idDeclEmail::class.java))
            RegisterDeclType("video", declType_t.DECL_VIDEO, DeclManager.idDeclAllocator(idDeclVideo::class.java))
            RegisterDeclType("audio", declType_t.DECL_AUDIO, DeclManager.idDeclAllocator(idDeclAudio::class.java))
            RegisterDeclFolder("materials", ".mtr", declType_t.DECL_MATERIAL)
            RegisterDeclFolder("skins", ".skin", declType_t.DECL_SKIN)
            RegisterDeclFolder("sound", ".sndshd", declType_t.DECL_SOUND)

            // add console commands
            CmdSystem.cmdSystem.AddCommand(
                "listDecls",
                ListDecls_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists all decls"
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadDecls",
                ReloadDecls_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "reloads decls"
            )
            CmdSystem.cmdSystem.AddCommand(
                "touch",
                TouchDecl_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "touches a decl"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listTables",
                idListDecls_f(declType_t.DECL_TABLE),
                CmdSystem.CMD_FL_SYSTEM,
                "lists tables",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listMaterials",
                idListDecls_f(declType_t.DECL_MATERIAL),
                CmdSystem.CMD_FL_SYSTEM,
                "lists materials",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listSkins",
                idListDecls_f(declType_t.DECL_SKIN),
                CmdSystem.CMD_FL_SYSTEM,
                "lists skins",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listSoundShaders",
                idListDecls_f(declType_t.DECL_SOUND),
                CmdSystem.CMD_FL_SYSTEM,
                "lists sound shaders",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listEntityDefs",
                idListDecls_f(declType_t.DECL_ENTITYDEF),
                CmdSystem.CMD_FL_SYSTEM,
                "lists entity defs",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listFX",
                idListDecls_f(declType_t.DECL_FX),
                CmdSystem.CMD_FL_SYSTEM,
                "lists FX systems",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listParticles",
                idListDecls_f(declType_t.DECL_PARTICLE),
                CmdSystem.CMD_FL_SYSTEM,
                "lists particle systems",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listAF",
                idListDecls_f(declType_t.DECL_AF),
                CmdSystem.CMD_FL_SYSTEM,
                "lists articulated figures",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listPDAs",
                idListDecls_f(declType_t.DECL_PDA),
                CmdSystem.CMD_FL_SYSTEM,
                "lists PDAs",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listEmails",
                idListDecls_f(declType_t.DECL_EMAIL),
                CmdSystem.CMD_FL_SYSTEM,
                "lists Emails",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listVideos",
                idListDecls_f(declType_t.DECL_VIDEO),
                CmdSystem.CMD_FL_SYSTEM,
                "lists Videos",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listAudios",
                idListDecls_f(declType_t.DECL_AUDIO),
                CmdSystem.CMD_FL_SYSTEM,
                "lists Audios",
                ArgCompletion_String(DeclManager.listDeclStrings)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printTable",
                idPrintDecls_f(declType_t.DECL_TABLE),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a table",
                ArgCompletion_Decl(declType_t.DECL_TABLE)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printMaterial",
                idPrintDecls_f(declType_t.DECL_MATERIAL),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a material",
                ArgCompletion_Decl(declType_t.DECL_MATERIAL)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printSkin",
                idPrintDecls_f(declType_t.DECL_SKIN),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a skin",
                ArgCompletion_Decl(declType_t.DECL_SKIN)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printSoundShader",
                idPrintDecls_f(declType_t.DECL_SOUND),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a sound shader",
                ArgCompletion_Decl(declType_t.DECL_SOUND)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printEntityDef",
                idPrintDecls_f(declType_t.DECL_ENTITYDEF),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an entity def",
                ArgCompletion_Decl(declType_t.DECL_ENTITYDEF)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printFX",
                idPrintDecls_f(declType_t.DECL_FX),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an FX system",
                ArgCompletion_Decl(declType_t.DECL_FX)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printParticle",
                idPrintDecls_f(declType_t.DECL_PARTICLE),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a particle system",
                ArgCompletion_Decl(declType_t.DECL_PARTICLE)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printAF",
                idPrintDecls_f(declType_t.DECL_AF),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an articulated figure",
                ArgCompletion_Decl(declType_t.DECL_AF)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printPDA",
                idPrintDecls_f(declType_t.DECL_PDA),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an PDA",
                ArgCompletion_Decl(declType_t.DECL_PDA)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printEmail",
                idPrintDecls_f(declType_t.DECL_EMAIL),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an Email",
                ArgCompletion_Decl(declType_t.DECL_EMAIL)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printVideo",
                idPrintDecls_f(declType_t.DECL_VIDEO),
                CmdSystem.CMD_FL_SYSTEM,
                "prints a Audio",
                ArgCompletion_Decl(declType_t.DECL_VIDEO)
            )
            CmdSystem.cmdSystem.AddCommand(
                "printAudio",
                idPrintDecls_f(declType_t.DECL_AUDIO),
                CmdSystem.CMD_FL_SYSTEM,
                "prints an Video",
                ArgCompletion_Decl(declType_t.DECL_AUDIO)
            )
            CmdSystem.cmdSystem.AddCommand(
                "listHuffmanFrequencies",
                ListHuffmanFrequencies_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists decl text character frequencies"
            )
            Common.common.Printf("------------------------------\n")
        }

        override fun Shutdown() {
            var i: Int
            var j: Int
            var decl: idDeclLocal?

            // free decls
            i = 0
            while (i < declType_t.DECL_MAX_TYPES.ordinal) {
                j = 0
                while (j < linearLists.get(i).Num()) {
                    decl = linearLists.get(i).get(j)
                    if (decl.self != null) {
                        decl.self.FreeData()
                        //				delete decl.self;
                    }
                    if (decl.textSource != null) {
//                        Mem_Free(decl.textSource);
                        decl.textSource = null
                    }
                    j++
                }
                linearLists.get(i).Clear()
                hashTables.get(i).Free()
                i++
            }

            // free decl files
            loadedFiles.DeleteContents(true)

            // free the decl types and folders
            declTypes.DeleteContents(true)
            declFolders.DeleteContents(true)
            if (DeclManager.USE_COMPRESSED_DECLS) {
                DeclManager.ShutdownHuffman()
            }
        }

        @Throws(idException::class)
        override fun Reload(force: Boolean) {
            for (i in 0 until loadedFiles.Num()) {
                loadedFiles.get(i).Reload(force)
            }
        }

        @Throws(idException::class)
        override fun BeginLevelLoad() {
            insideLevelLoad = true

            // clear all the referencedThisLevel flags and purge all the data
            // so the next reference will cause a reparse
            for (i in 0 until declType_t.DECL_MAX_TYPES.ordinal) {
                val num = linearLists.get(i).Num()
                for (j in 0 until num) {
                    val decl = linearLists.get(i).get(j)
                    decl.Purge()
                }
            }
        }

        override fun EndLevelLoad() {
            insideLevelLoad = false

            // we don't need to do anything here, but the image manager, model manager,
            // and sound sample manager will need to free media that was not referenced
        }

        @Throws(idException::class)
        override fun RegisterDeclType(typeName: String?, type: declType_t?, allocator: Constructor<idDecl?>?) {
            val declType: idDeclType
            if (type.ordinal < declTypes.Num() && declTypes.get(type.ordinal) != null) {
                Common.common.Warning("idDeclManager::RegisterDeclType: type '%s' already exists", typeName)
                return
            }
            declType = idDeclType()
            declType.typeName = idStr(typeName)
            declType.type = type
            declType.allocator = allocator
            if (type.ordinal + 1 > declTypes.Num()) {
                declTypes.AssureSize(type.ordinal + 1, null)
            }
            declTypes.set(type.ordinal, declType)
        }

        @Throws(idException::class)
        override fun RegisterDeclFolder(folder: String?, extension: String?, defaultType: declType_t?) {
            var i: Int
            var j: Int
            var fileName: idStr?
            val declFolder: idDeclFolder?
            val fileList: idFileList?
            var df: idDeclFile?

            // check whether this folder / extension combination already exists
            i = 0
            while (i < declFolders.Num()) {
                if (declFolders.get(i).folder.Icmp(folder) == 0 && declFolders.get(i).extension.Icmp(extension) == 0) {
                    break
                }
                i++
            }
            if (i < declFolders.Num()) {
                declFolder = declFolders.get(i)
            } else {
                declFolder = idDeclFolder()
                declFolder.folder = idStr(folder)
                declFolder.extension = idStr(extension)
                declFolder.defaultType = defaultType
                declFolders.Append(declFolder)
            }

            // scan for decl files
            fileList =
                FileSystem_h.fileSystem.ListFiles(declFolder.folder.toString(), declFolder.extension.toString(), true)

            // load and parse decl files
            i = 0
            while (i < fileList.GetNumFiles()) {
                fileName = idStr(declFolder.folder.toString() + "/" + fileList.GetFile(i))

                // check whether this file has already been loaded
                j = 0
                while (j < loadedFiles.Num()) {
                    if (fileName.Icmp(loadedFiles.get(j).fileName.toString()) == 0) {
                        break
                    }
                    j++
                }
                if (j < loadedFiles.Num()) {
                    df = loadedFiles.get(j)
                } else {
                    df = idDeclFile(fileName.toString(), defaultType)
                    loadedFiles.Append(df)
                }
                df.LoadAndParse()
                i++
            }
            FileSystem_h.fileSystem.FreeFileList(fileList)
        }

        override fun GetChecksum(): BigInteger? {
            throw UnsupportedOperationException()
            //            int i, j, total, num;
//            BigInteger[] checksumData;
//
//            // get the total number of decls
//            total = 0;
//            for (i = 0; i < DECL_MAX_TYPES.ordinal(); i++) {
//                total += linearLists[i].Num();
//            }
//
//            checksumData = new BigInteger[total * 2];
//
//            total = 0;
//            for (i = 0; i < DECL_MAX_TYPES.ordinal(); i++) {
//                declType_t type = declType_t.values()[i];
//
//                // FIXME: not particularly pretty but PDAs and associated decls are localized and should not be checksummed
//                if (type == DECL_PDA || type == DECL_VIDEO || type == DECL_AUDIO || type == DECL_EMAIL) {
//                    continue;
//                }
//
//                num = linearLists[i].Num();
//                for (j = 0; j < num; j++) {
//                    idDeclLocal decl = linearLists[i].oGet(j);
//
//                    if (decl.sourceFile == implicitDecls) {
//                        continue;
//                    }
//
//                    checksumData[total * 2 + 0] = total;
//                    checksumData[total * 2 + 1] = decl.checksum;
//                    total++;
//                }
//            }
//
//            Lib.LittleRevBytes(checksumData, total * 2);
//            return MD5_BlockChecksum(checksumData, total * 2 /* sizeof(int)*/);
        }

        override fun GetNumDeclTypes(): Int {
            return declTypes.Num()
        }

        @Throws(idException::class)
        override fun GetDeclNameFromType(type: declType_t?): String? {
            val typeIndex = type.ordinal
            if (typeIndex < 0 || typeIndex >= declTypes.Num() || declTypes.get(typeIndex) == null) {
                Common.common.FatalError("idDeclManager::GetDeclNameFromType: bad type: %d", typeIndex)
            }
            return declTypes.get(typeIndex).typeName.toString()
        }

        override fun GetDeclTypeFromName(typeName: String?): declType_t? {
            var i: Int
            i = 0
            while (i < declTypes.Num()) {
                if (declTypes.get(i) != null && declTypes.get(i).typeName.Icmp(typeName) == 0) {
                    return declTypes.get(i).type
                }
                i++
            }
            return declType_t.DECL_MAX_TYPES
        }

        @Throws(idException::class)
        override fun FindType(type: declType_t?, name: String?, makeDefault: Boolean): idDecl? {
            var name = name
            val decl: idDeclLocal?

//            TempDump.printCallStack("--------------"+ DEBUG_FindType);
            DEBUG_FindType++
            if (name == null || name.isEmpty()) {
                name = "_emptyName"
                //common.Warning( "idDeclManager::FindType: empty %s name", GetDeclType( (int)type ).typeName.c_str() );
            }
            decl = FindTypeWithoutParsing(type, name, makeDefault)
            if (null == decl) {
                return null
            }
            decl.AllocateSelf()

            // if it hasn't been parsed yet, parse it now
            if (decl.declState == declState_t.DS_UNPARSED) {
                decl.ParseLocal()
            }

            // mark it as referenced
            decl.referencedThisLevel = true
            decl.everReferenced = true
            if (insideLevelLoad) {
                decl.parsedOutsideLevelLoad = false
            }
            return decl.self
        }

        @Throws(idException::class)
        override fun FindDeclWithoutParsing(type: declType_t?, name: String?, makeDefault: Boolean): idDecl? {
            val decl: idDeclLocal?
            decl = FindTypeWithoutParsing(type, name, makeDefault)
            return decl?.self
        }

        @Throws(idException::class)
        override fun FindDeclWithoutParsing(type: declType_t?, name: String?): idDecl? {
            return FindDeclWithoutParsing(type, name, true)
        }

        @Throws(idException::class)
        override fun ReloadFile(filename: String?, force: Boolean) {
            for (i in 0 until loadedFiles.Num()) {
                if (0 == loadedFiles.get(i).fileName.Icmp(filename)) {
                    checksum = checksum.xor(loadedFiles.get(i).checksum)
                    loadedFiles.get(i).Reload(force)
                    checksum = checksum.xor(loadedFiles.get(i).checksum)
                }
            }
        }

        @Throws(idException::class)
        override fun GetNumDecls(typeIndex: Int): Int {
//            int typeIndex = typeIndex;
            if (typeIndex < 0 || typeIndex >= declTypes.Num() || declTypes.get(typeIndex) == null) {
                Common.common.FatalError("idDeclManager::GetNumDecls: bad type: %d", typeIndex)
            }
            return linearLists.get(typeIndex).Num()
        }

        @Throws(idException::class)
        override fun DeclByIndex(type: declType_t?, index: Int, forceParse: Boolean): idDecl? {
            val typeIndex = type.ordinal
            if (typeIndex < 0 || typeIndex >= declTypes.Num() || declTypes.get(typeIndex) == null) {
                Common.common.FatalError("idDeclManager::DeclByIndex: bad type: %d", typeIndex)
            }
            if (index < 0 || index >= linearLists.get(typeIndex).Num()) {
                Common.common.Error("idDeclManager::DeclByIndex: out of range")
            }
            val decl = linearLists.get(typeIndex).get(index)
            decl.AllocateSelf()
            if (forceParse && decl.declState == declState_t.DS_UNPARSED) {
                decl.ParseLocal()
            }
            return decl.self
        }

        @Throws(idException::class)
        override fun DeclByIndex(type: declType_t?, index: Int): idDecl? {
            return DeclByIndex(type, index, true)
        }

        /*
         ===================
         idDeclManagerLocal::ListType

         list*
         Lists decls currently referenced

         list* ever
         Lists decls that have been referenced at least once since app launched

         list* all
         Lists every decl declared, even if it hasn't been referenced or parsed

         FIXME: alphabetized, wildcards?
         ===================
         */
        @Throws(idException::class)
        override fun ListType(args: CmdArgs.idCmdArgs?, type: declType_t?) {
            val all: Boolean
            val ever: Boolean
            all = 0 == idStr.Companion.Icmp(args.Argv(1), "all")
            ever = 0 == idStr.Companion.Icmp(args.Argv(1), "ever")
            Common.common.Printf("--------------------\n")
            var printed = 0
            val count = linearLists.get(type.ordinal).Num()
            for (i in 0 until count) {
                val decl = linearLists.get(type.ordinal).get(i)
                if (!all && decl.declState == declState_t.DS_UNPARSED) {
                    continue
                }
                if (!all && !ever && !decl.referencedThisLevel) {
                    continue
                }
                if (decl.referencedThisLevel) {
                    Common.common.Printf("*")
                } else if (decl.everReferenced) {
                    Common.common.Printf(".")
                } else {
                    Common.common.Printf(" ")
                }
                if (decl.declState == declState_t.DS_DEFAULTED) {
                    Common.common.Printf("D")
                } else {
                    Common.common.Printf(" ")
                }
                Common.common.Printf("%4d: ", decl.index)
                printed++
                if (decl.declState == declState_t.DS_UNPARSED) {
                    // doesn't have any type specific data yet
                    Common.common.Printf("%s\n", decl.GetName())
                } else {
                    decl.self.List()
                }
            }
            Common.common.Printf("--------------------\n")
            Common.common.Printf("%d of %d %s\n", printed, count, declTypes.get(type.ordinal).typeName.toString())
        }

        @Throws(idException::class)
        override fun PrintType(args: CmdArgs.idCmdArgs?, type: declType_t?) {
            // individual decl types may use additional command parameters
            if (args.Argc() < 2) {
                Common.common.Printf("USAGE: Print<decl type> <decl name> [type specific parms]\n")
                return
            }

            // look it up, skipping the public path so it won't parse or reference
            val decl = FindTypeWithoutParsing(type, args.Argv(1), false)
            if (null == decl) {
                Common.common.Printf(
                    "%s '%s' not found.\n",
                    declTypes.get(type.ordinal).typeName.toString(),
                    args.Argv(1)
                )
                return
            }

            // print information common to all decls
            Common.common.Printf("%s %s:\n", declTypes.get(type.ordinal).typeName.toString(), decl.name.toString())
            Common.common.Printf("source: %s:%d\n", decl.sourceFile.fileName.toString(), decl.sourceLine)
            Common.common.Printf("----------\n")
            if (decl.textSource != null) {
                val declText = arrayOfNulls<String?>(1) //[decl.textLength + 1 ];
                decl.GetText(declText)
                Common.common.Printf("%s\n", declText[0])
            } else {
                Common.common.Printf("NO SOURCE\n")
            }
            Common.common.Printf("----------\n")
            when (decl.declState) {
                declState_t.DS_UNPARSED -> Common.common.Printf("Unparsed.\n")
                declState_t.DS_DEFAULTED -> Common.common.Printf("<DEFAULTED>\n")
                declState_t.DS_PARSED -> Common.common.Printf("Parsed.\n")
            }
            if (decl.referencedThisLevel) {
                Common.common.Printf("Currently referenced this level.\n")
            } else if (decl.everReferenced) {
                Common.common.Printf("Referenced in a previous level.\n")
            } else {
                Common.common.Printf("Never referenced.\n")
            }

            // allow type-specific data to be printed
            if (decl.self != null) {
                decl.self.Print()
            }
        }

        @Throws(idException::class)
        override fun CreateNewDecl(type: declType_t?, name: String?, _fileName: String?): idDecl? {
            val typeIndex = type.ordinal
            var i: Int
            val hash: Int
            if (typeIndex < 0 || typeIndex >= declTypes.Num() || declTypes.get(typeIndex) == null) {
                Common.common.FatalError("idDeclManager::CreateNewDecl: bad type: %d", typeIndex)
            }
            val canonicalName = CharArray(Lib.Companion.MAX_STRING_CHARS)
            MakeNameCanonical(name, canonicalName, Lib.Companion.MAX_STRING_CHARS)
            val fileName = idStr(_fileName)
            fileName.BackSlashesToSlashes()

            // see if it already exists
            hash = hashTables.get(typeIndex).GenerateKey(canonicalName, false)
            i = hashTables.get(typeIndex).First(hash)
            while (i >= 0) {
                if (linearLists.get(typeIndex).get(i).name.Icmp(TempDump.ctos(canonicalName)) == 0) {
                    linearLists.get(typeIndex).get(i).AllocateSelf()
                    return linearLists.get(typeIndex).get(i).self
                }
                i = hashTables.get(typeIndex).Next(i)
            }
            val sourceFile: idDeclFile?

            // find existing source file or create a new one
            i = 0
            while (i < loadedFiles.Num()) {
                if (loadedFiles.get(i).fileName.Icmp(fileName.toString()) == 0) {
                    break
                }
                i++
            }
            if (i < loadedFiles.Num()) {
                sourceFile = loadedFiles.get(i)
            } else {
                sourceFile = idDeclFile(fileName.toString(), type)
                loadedFiles.Append(sourceFile)
            }
            val decl = idDeclLocal()
            decl.name = idStr(TempDump.ctos(canonicalName))
            decl.type = type
            decl.declState = declState_t.DS_UNPARSED
            decl.AllocateSelf()
            val header = declTypes.get(typeIndex).typeName
            val defaultText = idStr(decl.self.DefaultDefinition())
            val size: Int = header.Length() + 1 + idStr.Companion.Length(canonicalName) + 1 + defaultText.Length()
            val declText = CharArray(size + 1)

//	memcpy( declText, header, header.Length() );
            System.arraycopy(header.c_str(), 0, declText, 0, header.Length())
            declText[header.Length()] = ' '
            //	memcpy( declText + header.Length() + 1, canonicalName, idStr::Length( canonicalName ) );
            System.arraycopy(canonicalName, 0, declText, header.Length() + 1, idStr.Companion.Length(canonicalName))
            declText[header.Length() + 1 + idStr.Companion.Length(canonicalName)] = ' '
            //	memcpy( declText + header.Length() + 1 + idStr::Length( canonicalName ) + 1, defaultText, defaultText.Length() + 1 );
            System.arraycopy(
                defaultText.c_str(),
                0,
                declText,
                header.Length() + 1 + idStr.Companion.Length(canonicalName) + 1,
                defaultText.Length() + 1
            )
            val declString = TempDump.ctos(declText)
            decl.SetTextLocal(declString, declString.length)
            decl.sourceFile = sourceFile
            decl.sourceTextOffset = sourceFile.fileSize
            decl.sourceTextLength = 0
            decl.sourceLine = sourceFile.numLines
            decl.ParseLocal()

            // add this decl to the source file list
            decl.nextInFile = sourceFile.decls
            sourceFile.decls = decl

            // add it to the hash table and linear list
            decl.index = linearLists.get(typeIndex).Num()
            hashTables.get(typeIndex).Add(hash, linearLists.get(typeIndex).Append(decl))
            return decl.self
        }

        //BSM Added for the material editors rename capabilities
        override fun RenameDecl(type: declType_t?, oldName: String?, newName: String?): Boolean {
            val canonicalOldName = CharArray(Lib.Companion.MAX_STRING_CHARS)
            MakeNameCanonical(oldName, canonicalOldName, Lib.Companion.MAX_STRING_CHARS)
            val canonicalNewName = CharArray(Lib.Companion.MAX_STRING_CHARS)
            MakeNameCanonical(newName, canonicalNewName, Lib.Companion.MAX_STRING_CHARS)
            var decl: idDeclLocal? = null

            // make sure it already exists
            val typeIndex = type.ordinal
            var i: Int
            val hash: Int
            hash = hashTables.get(typeIndex).GenerateKey(canonicalOldName, false)
            i = hashTables.get(typeIndex).First(hash)
            while (i >= 0) {
                if (linearLists.get(typeIndex).get(i).name.Icmp(TempDump.ctos(canonicalOldName)) == 0) {
                    decl = linearLists.get(typeIndex).get(i)
                    break
                }
                i = hashTables.get(typeIndex).Next(i)
            }
            if (null == decl) {
                return false
            }

            //if ( !hashTables[(int)type].Get( canonicalOldName, &declPtr ) )
            //	return false;
            //decl = *declPtr;
            //Change the name
            decl.name = idStr(TempDump.ctos(canonicalNewName))

            // add it to the hash table
            //hashTables[(int)decl.type].Set( decl.name, decl );
            val newhash = hashTables.get(typeIndex).GenerateKey(TempDump.ctos(canonicalNewName), false)
            hashTables.get(typeIndex).Add(newhash, decl.index)

            //Remove the old hash item
            hashTables.get(typeIndex).Remove(hash, decl.index)
            return true
        }

        /*
         ===================
         idDeclManagerLocal::MediaPrint

         This is just used to nicely indent media caching prints
         ===================
         */
        @Throws(idException::class)
        override fun MediaPrint(fmt: String?, vararg arg: Any?) {
            if (0 == decl_show.GetInteger()) {
                return
            }
            for (i in 0 until indent) {
                Common.common.Printf("    ")
            }
            //	va_list		argptr;
            val buffer = arrayOf<String?>(null) //new char[1024];
            //	va_start (argptr,fmt);
            idStr.Companion.vsnPrintf(buffer, 1024, fmt, *arg)
            //	va_end (argptr);
//            buffer[1024 - 1] = '\0';
            Common.common.Printf("%s", buffer[0])
        }

        override fun WritePrecacheCommands(f: idFile?) {
            for (i in 0 until declTypes.Num()) {
                var num: Int
                if (declTypes.get(i) == null) {
                    continue
                }
                num = linearLists.get(i).Num()
                for (j in 0 until num) {
                    val decl = linearLists.get(i).get(j)
                    if (!decl.referencedThisLevel) {
                        continue
                    }
                    var str: String //[1024];
                    str = String.format("touch %s %s\n", declTypes.get(i).typeName.toString(), decl.GetName())
                    Common.common.Printf("%s", str)
                    f.Printf("%s", str)
                }
            }
        }

        /* *******************************************************************/
        @Throws(idException::class)
        override fun FindMaterial(name: idStr?, makeDefault: Boolean): idMaterial? {
            return FindType(declType_t.DECL_MATERIAL, name, makeDefault) as idMaterial?
        }

        @Throws(idException::class)
        override fun MaterialByIndex(index: Int, forceParse: Boolean): idMaterial? {
            return DeclByIndex(declType_t.DECL_MATERIAL, index, forceParse) as idMaterial?
        }

        @Throws(idException::class)
        override fun MaterialByIndex(index: Int): idMaterial? {
            return MaterialByIndex(index, true)
        }

        /* *******************************************************************/
        @Throws(idException::class)
        override fun FindSkin(name: idStr?, makeDefault: Boolean): idDeclSkin? {
            return FindType(declType_t.DECL_SKIN, name, makeDefault) as idDeclSkin?
        }

        @Throws(idException::class)
        override fun SkinByIndex(index: Int, forceParse: Boolean): idDeclSkin? {
            return DeclByIndex(declType_t.DECL_SKIN, index, forceParse) as idDeclSkin?
        }

        @Throws(idException::class)
        override fun SkinByIndex(index: Int): idDeclSkin? {
            return SkinByIndex(index, true)
        }

        /* *******************************************************************/
        @Throws(idException::class)
        override fun FindSound(name: idStr?, makeDefault: Boolean): idSoundShader? {
            return FindType(declType_t.DECL_SOUND, name, makeDefault) as idSoundShader?
        }

        @Throws(idException::class)
        override fun SoundByIndex(index: Int, forceParse: Boolean): idSoundShader? {
            return DeclByIndex(declType_t.DECL_SOUND, index, forceParse) as idSoundShader?
        }

        /* *******************************************************************/ //
        @Throws(idException::class)
        override fun SoundByIndex(index: Int): idSoundShader? {
            return SoundByIndex(index, true)
        }

        /*
         ===================
         idDeclManagerLocal::FindTypeWithoutParsing

         This finds or creats the decl, but does not cause a parse.  This is only used internally.
         ===================
         */
        @Throws(idException::class)
        fun FindTypeWithoutParsing(type: declType_t?, name: String?, makeDefault: Boolean /*= true*/): idDeclLocal? {
            val typeIndex = type.ordinal
            var i: Int
            val hash: Int
            if (typeIndex < 0 || typeIndex >= declTypes.Num() || declTypes.get(typeIndex) == null) {
                Common.common.FatalError("idDeclManager.FindTypeWithoutParsing: bad type: %d", typeIndex)
            }
            val canonicalName = CharArray(Lib.Companion.MAX_STRING_CHARS)
            MakeNameCanonical(name, canonicalName, Lib.Companion.MAX_STRING_CHARS)

            // see if it already exists
            hash = hashTables.get(typeIndex).GenerateKey(canonicalName, false)
            i = hashTables.get(typeIndex).First(hash)
            while (i >= 0) {
                if (linearLists.get(typeIndex).get(i).name.Icmp(TempDump.ctos(canonicalName)) == 0) {
                    // only print these when decl_show is set to 2, because it can be a lot of clutter
                    if (decl_show.GetInteger() > 1) {
                        MediaPrint("referencing %s %s\n", declTypes.get(type.ordinal).typeName.toString(), name)
                    }
                    return linearLists.get(typeIndex).get(i)
                }
                i = hashTables.get(typeIndex).Next(i)
            }
            if (!makeDefault) {
                return null
            }
            val decl = idDeclLocal()
            decl.self = null
            decl.name = idStr(TempDump.ctos(canonicalName))
            decl.type = type
            decl.declState = declState_t.DS_UNPARSED
            decl.textSource = null
            decl.textLength = 0
            decl.sourceFile = implicitDecls
            decl.referencedThisLevel = false
            decl.everReferenced = false
            decl.parsedOutsideLevelLoad = !insideLevelLoad

            // add it to the linear list and hash table
            decl.index = linearLists.get(typeIndex).Num()
            hashTables.get(typeIndex).Add(hash, linearLists.get(typeIndex).Append(decl))
            return decl
        }

        fun GetDeclType(type: Int): idDeclType? {
            return declTypes.get(type)
        }

        fun GetImplicitDeclFile(): idDeclFile? {
            return implicitDecls
        }

        /*
         ================
         idDeclManagerLocal.ListDecls_f
         ================
         */
        internal class ListDecls_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                var j: Int
                var totalDecls = 0
                var totalText = 0
                var totalStructs = 0
                i = 0
                while (i < DeclManager.declManagerLocal.declTypes.Num()) {
                    var size: Int
                    var num: Int
                    if (DeclManager.declManagerLocal.declTypes.get(i) == null) {
                        i++
                        continue
                    }
                    num = DeclManager.declManagerLocal.linearLists[i].Num()
                    totalDecls += num
                    size = 0
                    j = 0
                    while (j < num) {
                        size += DeclManager.declManagerLocal.linearLists[i].get(j).Size().toInt()
                        if (DeclManager.declManagerLocal.linearLists[i].get(j).self != null) {
                            size += 4
                        }
                        j++
                    }
                    totalStructs += size
                    Common.common.Printf(
                        "%4dk %4d %s\n",
                        size shr 10,
                        num,
                        DeclManager.declManagerLocal.declTypes.get(i).typeName.toString()
                    )
                    i++
                }
                i = 0
                while (i < DeclManager.declManagerLocal.loadedFiles.Num()) {
                    val df: idDeclFile? = DeclManager.declManagerLocal.loadedFiles.get(i)
                    totalText += df.fileSize
                    i++
                }
                Common.common.Printf(
                    "%d total decls is %d decl files\n",
                    totalDecls,
                    DeclManager.declManagerLocal.loadedFiles.Num()
                )
                Common.common.Printf("%dKB in text, %dKB in structures\n", totalText shr 10, totalStructs shr 10)
            }

            companion object {
                private val instance: cmdFunction_t? = ListDecls_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }

        /*
         ===================
         idDeclManagerLocal.ReloadDecls_f

         Reload will not find any new files created in the directories, it
         will only reload existing files.

         A reload will never cause anything to be purged.
         ===================
         */
        internal class ReloadDecls_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val force: Boolean
                if (0 == idStr.Companion.Icmp(args.Argv(1), "all")) {
                    force = true
                    Common.common.Printf("reloading all decl files:\n")
                } else {
                    force = false
                    Common.common.Printf("reloading changed decl files:\n")
                }
                snd_system.soundSystem.SetMute(true)
                DeclManager.declManagerLocal.Reload(force)
                snd_system.soundSystem.SetMute(false)
            }

            companion object {
                private val instance: cmdFunction_t? = ReloadDecls_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }

        /*
         ===================
         idDeclManagerLocal.TouchDecl_f
         ===================
         */
        internal class TouchDecl_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                if (args.Argc() != 3) {
                    Common.common.Printf("usage: touch <type> <name>\n")
                    Common.common.Printf("valid types: ")
                    i = 0
                    while (i < DeclManager.declManagerLocal.declTypes.Num()) {
                        if (DeclManager.declManagerLocal.declTypes.get(i) != null) {
                            Common.common.Printf(
                                "%s ",
                                DeclManager.declManagerLocal.declTypes.get(i).typeName.toString()
                            )
                        }
                        i++
                    }
                    Common.common.Printf("\n")
                    return
                }
                i = 0
                while (i < DeclManager.declManagerLocal.declTypes.Num()) {
                    if (DeclManager.declManagerLocal.declTypes.get(i) != null && DeclManager.declManagerLocal.declTypes.get(
                            i
                        ).typeName.Icmp(args.Argv(1)) == 0
                    ) {
                        break
                    }
                    i++
                }
                if (i >= DeclManager.declManagerLocal.declTypes.Num()) {
                    Common.common.Printf("unknown decl type '%s'\n", args.Argv(1))
                    return
                }
                val values: Array<declType_t?> = DeclManager.declType_t.values()
                if (i < values.size) {
                    val decl: idDecl = DeclManager.declManagerLocal.FindType(values[i], idStr(args.Argv(2)), false)
                    if (null == decl) {
                        Common.common.Printf(
                            "%s '%s' not found\n",
                            DeclManager.declManagerLocal.declTypes.get(i).typeName.toString(),
                            args.Argv(2)
                        )
                    }
                }
            }

            companion object {
                private val instance: cmdFunction_t? = TouchDecl_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }

        companion object {
            //
            private val decl_show: idCVar? = idCVar(
                "decl_show",
                "0",
                CVarSystem.CVAR_SYSTEM,
                "set to 1 to print parses, 2 to also print references",
                0,
                2,
                ArgCompletion_Integer(0, 2)
            )

            /*
         =================
         idDeclManagerLocal::FindType

         External users will always cause the decl to be parsed before returning
         =================
         */
            var DEBUG_FindType = 0
            fun MakeNameCanonical(name: String?, result: CharArray?, maxLength: Int) { //TODO:maxlength???
                var i: Int
                var lastDot: Int
                lastDot = -1
                i = 0
                while (i < maxLength && i < name.length) {
                    val c = name.get(i).code
                    if (c == '\\'.code) {
                        result.get(i) = '/'
                    } else if (c == '.'.code) {
                        lastDot = i
                        result.get(i) = c.toChar()
                    } else {
                        result.get(i) = idStr.Companion.ToLower(c.toChar())
                    }
                    i++
                }
                if (lastDot != -1) {
                    result.get(lastDot) = '\u0000'
                } else {
                    result.get(i) = '\u0000'
                }
            }
        }

        init {
            declTypes = idList()
            declFolders = idList()
            loadedFiles = idList()
            hashTables = arrayOfNulls<idHashIndex?>(TempDump.etoi(declType_t.DECL_MAX_TYPES))
            linearLists = arrayOfNulls<idList<*>?>(TempDump.etoi(declType_t.DECL_MAX_TYPES))
            for (d in 0 until TempDump.etoi(declType_t.DECL_MAX_TYPES)) {
                hashTables[d] = idHashIndex()
                linearLists[d] = idList<idDeclLocal?>()
            }
        }
    }

    internal class huffmanNode_s {
        var children: Array<huffmanNode_s?>? = arrayOfNulls<huffmanNode_s?>(2)
        var frequency = 0
        var next: huffmanNode_s? = null
        var symbol = 0
    }

    internal class huffmanCode_s {
        var bits: LongArray? = LongArray(8)
        var numBits = 0

        private constructor()
        constructor(code: huffmanCode_s?) {
            numBits = code.numBits
            System.arraycopy(code.bits, 0, bits, 0, bits.size)
        }
    }

    /*
     ================
     ListHuffmanFrequencies_f
     ================
     */
    internal class ListHuffmanFrequencies_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val compression: Float
            compression =
                (if (0 == DeclManager.totalUncompressedLength) 100 else 100 * DeclManager.totalCompressedLength / DeclManager.totalUncompressedLength).toFloat()
            Common.common.Printf("// compression ratio = %d%%\n", compression.toInt())
            Common.common.Printf("static int huffmanFrequencies[] = {\n")
            i = 0
            while (i < DeclManager.MAX_HUFFMAN_SYMBOLS) {
                Common.common.Printf(
                    "\t0x%08x, 0x%08x, 0x%08x, 0x%08x, 0x%08x, 0x%08x, 0x%08x, 0x%08x,\n",
                    DeclManager.huffmanFrequencies[i + 0], DeclManager.huffmanFrequencies[i + 1],
                    DeclManager.huffmanFrequencies[i + 2], DeclManager.huffmanFrequencies[i + 3],
                    DeclManager.huffmanFrequencies[i + 4], DeclManager.huffmanFrequencies[i + 5],
                    DeclManager.huffmanFrequencies[i + 6], DeclManager.huffmanFrequencies[i + 7]
                )
                i += 8
            }
            Common.common.Printf("}\n")
        }

        companion object {
            private val instance: cmdFunction_t? = ListHuffmanFrequencies_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}