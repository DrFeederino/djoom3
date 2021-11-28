package neo.idlib.Text

import neo.idlib.Lib
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.math.Lcp
import java.util.*

/**
 *
 */
class CmdArgs {
    /*
     ===============================================================================

     Command arguments.

     ===============================================================================
     */
    internal inner class idCmdArgs {
        //
        private var argc // number of arguments
                = 0
        private val argv: CharArray? = CharArray(CmdArgs.idCmdArgs.Companion.MAX_COMMAND_ARGS) // points into tokenized
        private val tokenized: CharArray? =
            CharArray(CmdArgs.idCmdArgs.Companion.MAX_COMMAND_STRING) // will have 0 bytes inserted

        //
        //
        constructor() {
            argc = 0
        }

        constructor(text: String?, keepAsStrings: Boolean) {
            TokenizeString(text, keepAsStrings)
        }

        //
        fun oSet(args: CmdArgs.idCmdArgs?) {
            var i: Int
            argc = args.argc
            //	memcpy( tokenized, args.tokenized, MAX_COMMAND_STRING );
            System.arraycopy(args.tokenized, 0, tokenized, 0, CmdArgs.idCmdArgs.Companion.MAX_COMMAND_STRING)
            //            for (i = 0; i < argc; i++) {
//		argv[ i ] = tokenized + ( args.argv[ i ] - args.tokenized );
//            }
            System.arraycopy(args.argv, 0, argv, 0, argc)
        }

        //
        // The functions that execute commands get their parameters with these functions.
        fun Argc(): Int {
            return argc
        }

        // Argv() will return an empty string, not NULL if arg >= argc.
        fun Argv(arg: Int): String? {
            return (if (arg >= 0 && arg < argc) argv.get(arg) else "") as String
        }

        // Returns a single string containing argv(start) to argv(end)
        // escapeArgs is a fugly way to put the string back into a state ready to tokenize again
        //public	String			Args( int start = 1, int end = -1, bool escapeArgs = false ) const;
        fun Args(start: Int, end: Int, escapeArgs: Boolean): String? {
//	static char cmd_args[MAX_COMMAND_STRING];
            var end = end
            var cmd_args = ""
            var i: Int
            if (end < 0) {
                end = argc - 1
            } else if (end >= argc) {
                end = argc - 1
            }
            cmd_args += '\u0000'
            if (escapeArgs) {
//		strcat( cmd_args, "\"" );
                cmd_args += "\""
            }
            i = start
            while (i <= end) {
                if (i > start) {
                    cmd_args += if (escapeArgs) {
                        "\" \""
                    } else {
                        " "
                    }
                }
                if (escapeArgs && Arrays.binarySearch(argv, i, argv.size, '\\') != 0) {
                    var p = i
                    while (argv.get(p) != '\u0000') {
                        if (argv.get(p) == '\\') {
                            cmd_args += "\\\\"
                        } else {
                            val l = cmd_args.length
                            cmd_args += argv.get(p)
                            cmd_args += '\u0000'
                        }
                        p++
                    }
                } else {
                    cmd_args += argv.get(i)
                }
                i++
            }
            if (escapeArgs) {
                cmd_args += "\""
            }
            return cmd_args
        }

        //
        /*
         ============
         idCmdArgs::TokenizeString

         Parses the given string into command line tokens.
         The text is copied to a separate buffer and 0 characters
         are inserted in the appropriate place. The argv array
         will point into this temporary buffer.
         ============
         */
        // Takes a null terminated string and breaks the string up into arg tokens.
        // Does not need to be /n terminated.
        // Set keepAsStrings to true to only seperate tokens from whitespace and comments, ignoring punctuation
        @Throws(idException::class)
        fun TokenizeString(text: String?, keepAsStrings: Boolean) {
            val lex = idLexer()
            val token = idToken()
            val number = idToken()
            var len: Int
            var totalLen: Int

            // clear previous args
            argc = 0
            if (null == text) {
                return
            }
            lex.LoadMemory(text, text.length, "idCmdSystemLocal::TokenizeString")
            lex.SetFlags(
                Lexer.LEXFL_NOERRORS
                        or Lexer.LEXFL_NOWARNINGS
                        or Lexer.LEXFL_NOSTRINGCONCAT
                        or Lexer.LEXFL_ALLOWPATHNAMES
                        or Lexer.LEXFL_NOSTRINGESCAPECHARS
                        or Lexer.LEXFL_ALLOWIPADDRESSES or if (keepAsStrings) Lexer.LEXFL_ONLYSTRINGS else 0
            )
            totalLen = 0
            while (true) {
                if (argc == CmdArgs.idCmdArgs.Companion.MAX_COMMAND_ARGS) {
                    return  // this is usually something malicious
                }
                if (!lex.ReadToken(token)) {
                    return
                }

                // check for negative numbers
                if (!keepAsStrings && token == "-") {
                    if (lex.CheckTokenType(Token.TT_NUMBER, 0, number) != 0) {
                        token.oSet("-$number")
                    }
                }

                // check for cvar expansion
                if (token == "$") {
                    if (!lex.ReadToken(token)) {
                        return
                    }
                    if (idLib.cvarSystem != null) {
                        token.oSet(idLib.cvarSystem.GetCVarString(token.toString()))
                    } else {
                        token.oSet("<unknown>")
                    }
                }
                len = token.Length()
                if (totalLen + len + 1 > tokenized.size) {
                    return  // this is usually something malicious
                }

                // regular token
                argv.get(argc) = tokenized.get(totalLen)
                argc++
                val tokenizedClam = Lcp.clam(tokenized, totalLen)
                idStr.Companion.Copynz(tokenizedClam, token.toString(), tokenized.size - totalLen)
                Lcp.unClam(tokenized, tokenizedClam)
                totalLen += len + 1
            }
        }

        //
        fun AppendArg(text: String?) {
            if (0 == argc) {
                argc = 1
                argv.get(0) = tokenized.get(0)
                idStr.Companion.Copynz(tokenized, text, tokenized.size)
            } else {
                argv.get(argc) = argv.get(argc - 1 + (argv.size - argc - 1) + 1)
                val argvClam = Lcp.clam(argv, argc)
                idStr.Companion.Copynz(argvClam, text, tokenized.size - (argv.size - argc - tokenized.get(0)))
                Lcp.unClam(argv, argvClam)
                argc++
            }
        }

        fun Clear() {
            argc = 0
        }

        fun GetArgs(_argc: IntArray?): CharArray? {
            _argc.get(0) = argc
            return argv
        }

        companion object {
            private const val MAX_COMMAND_ARGS = 64
            private val MAX_COMMAND_STRING: Int = 2 * Lib.Companion.MAX_STRING_CHARS
        }
    }
}