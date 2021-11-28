package neo.idlib

import neo.TempDump
import neo.TempDump.CPP_class.Char
import neo.framework.Common
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import java.nio.*

/**
 *
 */
class LangDict {
    /*
     ===============================================================================

     Simple dictionary specifically for the localized string tables.

     ===============================================================================
     */
    class idLangKeyValue {
        var key: idStr? = null
        var value: idStr? = null
    }

    class idLangDict {
        val args: idList<idLangKeyValue?>? = idList()

        //
        private var baseID: Int

        //
        //
        private val hash: idHashIndex? = idHashIndex()
        fun Clear() {
            args.Clear()
            hash.Clear()
        }

        @JvmOverloads
        @Throws(idException::class)
        fun Load(fileName: String?, clear: Boolean = true): Boolean {
            if (clear) {
                Clear()
            }
            val buffer = arrayOf<ByteBuffer?>(null)
            val src =
                idLexer(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
            val len = idLib.fileSystem.ReadFile(fileName, buffer)
            if (len <= 0) {
                // let whoever called us deal with the failure (so sys_lang can be reset)
                return false
            }
            src.LoadMemory(TempDump.bbtocb(buffer[0]), TempDump.bbtocb(buffer[0]).capacity(), fileName)
            if (!src.IsLoaded()) {
                return false
            }
            var tok: idToken?
            var tok2: idToken?
            src.ExpectTokenString("{")
            while (src.ReadToken(idToken().also { tok = it })) {
                if (tok == "}") {
                    break
                }
                if (src.ReadToken(idToken().also { tok2 = it })) {
                    if (tok2 == "}") {
                        break
                    }
                    val kv = idLangKeyValue()
                    kv.key = tok
                    kv.value = tok2
                    assert(kv.key.Cmpn(Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0)
                    //                    if (tok.equals("#str_07184")) {
//                        tok2.oSet("006");
//                    }
                    hash.Add(GetHashKey(kv.key), args.Append(kv))
                }
            }
            idLib.common.Printf("%d strings read from %s\n", args.Num(), fileName)
            idLib.fileSystem.FreeFile(buffer)
            return true
        }

        fun Save(fileName: String?) {
            val outFile = idLib.fileSystem.OpenFileWrite(fileName)
            outFile.WriteFloatString("// string table\n// english\n//\n\n{\n")
            for (j in 0 until args.Num()) {
                outFile.WriteFloatString("\t\"%s\"\t\"", args.oGet(j).key)
                val l = args.oGet(j).value.Length()
                val slash: Char = '\\'
                val tab: Char = 't'
                val nl: Char = 'n'
                for (k in 0 until l) {
                    val ch: Char = args.oGet(j).value.toString()[k]
                    if (ch == '\t') {
                        outFile.WriteChar(slash)
                        outFile.WriteChar(tab)
                    } else if (ch == '\n' || ch == '\r') {
                        outFile.WriteChar(slash)
                        outFile.WriteChar(nl)
                    } else {
                        outFile.WriteChar(ch)
                    }
                }
                outFile.WriteFloatString("\"\n")
            }
            outFile.WriteFloatString("\n}\n")
            idLib.fileSystem.CloseFile(outFile)
        }

        fun AddString(str: String?): String? {
            if (ExcludeString(str)) {
                return str
            }
            var c = args.Num()
            for (j in 0 until c) {
                if (idStr.Companion.Cmp(args.oGet(j).value.toString(), str) == 0) {
                    return args.oGet(j).key.toString()
                }
            }
            val id = GetNextId()
            val kv = idLangKeyValue()
            // _D3XP
            kv.key = idStr(Str.va("#str_%08i", id))
            // kv.key = va( "#str_%05i", id );
            kv.value = idStr(str)
            c = args.Append(kv)
            assert(kv.key.Cmpn(Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0)
            hash.Add(GetHashKey(kv.key), c)
            return args.oGet(c).key.toString()
        }

        @Throws(idException::class)
        fun GetString(str: String?): String? {
            if ("#str_07184" == str) {
//                System.out.printf("GetString#%d\n", DBG_GetString);
//                return (DBG_GetString++) + "bnlaaaaaaaaaaa";
            }
            if (str == null || str.isEmpty()) {
                return ""
            }
            if (idStr.Companion.Cmpn(str, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) != 0) {
                return str
            }
            val hashKey = GetHashKey(str)
            var i = hash.First(hashKey)
            while (i != -1) {
                if (args.oGet(i).key.Cmp(str) == 0) {
                    return args.oGet(i).value.toString()
                }
                i = hash.Next(i)
            }
            idLib.common.Warning("Unknown string id %s", str)
            return str
        }

        @Throws(idException::class)
        fun GetString(str: idStr?): String? {
            return GetString(str.toString())
        }

        // adds the value and key as passed (doesn't generate a "#str_xxxxx" key or ensure the key/value pair is unique)
        fun AddKeyVal(key: String?, `val`: String?) {
            val kv = idLangKeyValue()
            kv.key = idStr(key)
            kv.value = idStr(`val`)
            assert(kv.key.Cmpn(Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0)
            hash.Add(GetHashKey(kv.key), args.Append(kv))
        }

        fun GetNumKeyVals(): Int {
            return args.Num()
        }

        fun GetKeyVal(i: Int): idLangKeyValue? {
            return args.oGet(i)
        }

        fun SetBaseID(id: Int) {
            baseID = id
        }

        private fun ExcludeString(str: String?): Boolean {
            if (str == null) {
                return true
            }
            val c = str.length
            if (c <= 1) {
                return true
            }
            if (idStr.Companion.Cmpn(str, Common.STRTABLE_ID, Common.STRTABLE_ID_LENGTH) == 0) {
                return true
            }
            if (idStr.Companion.Icmpn(str, "gui::", "gui::".length) == 0) {
                return true
            }
            if (str[0] == '$') {
                return true
            }
            var i: Int
            i = 0
            while (i < c) {
                if (Character.isAlphabetic(str[i].code)) {
                    break
                }
                i++
            }
            return i == c
        }

        private fun GetNextId(): Int {
            val c = args.Num()

            //Let and external user supply the base id for this dictionary
            var id = baseID
            if (c == 0) {
                return id
            }
            var work: idStr?
            for (j in 0 until c) {
                work = args.oGet(j).key
                work.StripLeading(Common.STRTABLE_ID)
                val test = work.toString().toInt()
                if (test > id) {
                    id = test
                }
            }
            return id + 1
        }

        private fun GetHashKey(str: idStr?): Int {
            return GetHashKey(str.toString())
        }

        private fun GetHashKey(str: String?): Int {
            var hashKey = 0
            var i: Int
            var c: Char
            i = Common.STRTABLE_ID_LENGTH
            while (i < str.length) {
                c = str.get(i)
                assert(Character.isDigit(c))
                hashKey = hashKey * 10 + c.code - '0'.code
                i++
            }
            return hashKey
        }

        companion object {
            private const val DBG_GetString = 1
        }

        //public							~idLangDict( void );
        //
        init {
            args.SetGranularity(256)
            hash.SetGranularity(256)
            hash.Clear(4096, 8192)
            baseID = 0
        }
    }
}