package neo.framework

import neo.TempDump
import neo.TempDump.void_callback
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.File_h.idFile
import neo.idlib.*
import neo.idlib.Lib.idException
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.sys.win_input

/**
 *
 */
object KeyInput {
    /*
     ===============================================================================

     Key Input

     ===============================================================================
     */
    const val K_ACUTE_ACCENT = 180 // accute accent

    //
    const val K_ALT = 140

    //
    const val K_AUX1 = 230
    const val K_AUX10 = 244
    const val K_AUX11 = 245
    const val K_AUX12 = 246
    const val K_AUX13 = 247
    const val K_AUX14 = 248
    const val K_AUX15 = 250
    const val K_AUX16 = 251
    const val K_AUX2 = 231
    const val K_AUX3 = 232
    const val K_AUX4 = 233
    const val K_AUX5 = 237
    const val K_AUX6 = 238
    const val K_AUX7 = 239
    const val K_AUX8 = 240
    const val K_AUX9 = 243

    //
    const val K_BACKSPACE = 127
    const val K_CAPSLOCK = 129
    const val K_CEDILLA_C = 231 // lowercase c with Cedilla

    //
    const val K_COMMAND = 128
    const val K_CTRL = 141
    const val K_DEL = 144
    const val K_DOWNARROW = 134
    const val K_END = 148
    const val K_ENTER = 13
    const val K_ESCAPE = 27

    //
    const val K_F1 = 149
    const val K_F10 = 158
    const val K_F11 = 159
    const val K_F12 = 160
    const val K_F13 = 162
    const val K_F14 = 163
    const val K_F15 = 164
    const val K_F2 = 150
    const val K_F3 = 151
    const val K_F4 = 152
    const val K_F5 = 153
    const val K_F6 = 154
    const val K_F7 = 155
    const val K_F8 = 156
    const val K_F9 = 157
    const val K_GRAVE_A = 224 // lowercase a with grave accent
    const val K_GRAVE_E = 232 // lowercase e with grave accent
    const val K_GRAVE_I = 236 // lowercase i with grave accent
    const val K_GRAVE_O = 242 // lowercase o with grave accent
    const val K_GRAVE_U = 249 // lowercase u with grave accent
    const val K_HOME = 147
    const val K_INS = 143
    const val K_INVERTED_EXCLAMATION = 161 // upside down !

    //
    const val K_JOY1 = 197
    const val K_JOY10 = 206
    const val K_JOY11 = 207
    const val K_JOY12 = 208
    const val K_JOY13 = 209
    const val K_JOY14 = 210
    const val K_JOY15 = 211
    const val K_JOY16 = 212
    const val K_JOY17 = 213
    const val K_JOY18 = 214
    const val K_JOY19 = 215
    const val K_JOY2 = 198
    const val K_JOY20 = 216
    const val K_JOY21 = 217
    const val K_JOY22 = 218
    const val K_JOY23 = 219
    const val K_JOY24 = 220
    const val K_JOY25 = 221
    const val K_JOY26 = 222
    const val K_JOY27 = 223
    const val K_JOY28 = 225
    const val K_JOY29 = 226
    const val K_JOY3 = 199
    const val K_JOY30 = 227
    const val K_JOY31 = 228
    const val K_JOY32 = 229
    const val K_JOY4 = 200
    const val K_JOY5 = 201
    const val K_JOY6 = 202
    const val K_JOY7 = 203
    const val K_JOY8 = 204
    const val K_JOY9 = 205
    const val K_KP_5 = 169
    const val K_KP_DEL = 176
    const val K_KP_DOWNARROW = 172
    const val K_KP_END = 171
    const val K_KP_ENTER = 174
    const val K_KP_EQUALS = 183

    //
    const val K_KP_HOME = 165
    const val K_KP_INS = 175
    const val K_KP_LEFTARROW = 168
    const val K_KP_MINUS = 179
    const val K_KP_NUMLOCK = 181
    const val K_KP_PGDN = 173
    const val K_KP_PGUP = 167
    const val K_KP_PLUS = 180
    const val K_KP_RIGHTARROW = 170
    const val K_KP_SLASH = 177
    const val K_KP_STAR = 182
    const val K_KP_UPARROW = 166
    const val K_LAST_KEY = 254 // this better be < 256!
    const val K_LEFTARROW = 135

    //
    //                          // The 3 windows keys
    const val K_LWIN = 137

    //
    const val K_MASCULINE_ORDINATOR = 186
    const val K_MENU = 139

    //                       // K_MOUSE enums must be contiguous (no char codes in the middle)
    const val K_MOUSE1 = 187
    const val K_MOUSE2 = 188
    const val K_MOUSE3 = 189
    const val K_MOUSE4 = 190
    const val K_MOUSE5 = 191
    const val K_MOUSE6 = 192
    const val K_MOUSE7 = 193
    const val K_MOUSE8 = 194

    //
    const val K_MWHEELDOWN = 195
    const val K_MWHEELUP = 196
    const val K_PAUSE = 132
    const val K_PGDN = 145
    const val K_PGUP = 146
    const val K_POWER = 131

    //
    const val K_PRINT_SCR = 252 // SysRq / PrintScr
    const val K_RIGHTARROW = 136
    const val K_RIGHT_ALT = 253 // used by some languages as "Alt-Gr"
    const val K_RWIN = 138
    const val K_SCROLL = 130
    const val K_SHIFT = 142
    const val K_SPACE = 32
    const val K_SUPERSCRIPT_TWO = 178 // superscript 2

    // these are the key numbers that are used by the key system
    // normal keys should be passed as lowercased ascii
    // Some high ascii (> 127) characters that are mapped directly to keys on
    // western european keyboards are inserted in this table so that those keys
    // are bindable (otherwise they get bound as one of the special keys in this table)
    //
    //
    const val K_TAB = 9
    const val K_TILDE_N = 241 // lowercase n with tilde

    //
    const val K_UPARROW = 133

    //    
    //    
    //
    //
    const val ID_DOOM_LEGACY = false

    //
    //
    const val MAX_KEYS = 256

    //
    val cheatCodes: Array<String?>? = arrayOf(
        "iddqd",  // Invincibility
        "idkfa",  // All weapons, keys, ammo, and 200% armor
        "idfa",  // Reset ammunition
        "idspispopd",  // Walk through walls
        "idclip",  // Walk through walls
        "idchoppers",  // Chainsaw
        /*
         "idbeholds",	// Berserker strength
         "idbeholdv",	// Temporary invincibility
         "idbeholdi",	// Temporary invisibility
         "idbeholda",	// Full automap
         "idbeholdr",	// Anti-radiation suit
         "idbeholdl",	// Light amplification visor
         "idclev",		// Level select
         "iddt",			// Toggle full map; full map and objects; normal map
         "idmypos",		// Display coordinates and heading
         "idmus",		// Change music to indicated level
         "fhhall",		// Kill all enemies in level
         "fhshh",		// Invisible to enemies until attack
         */
        null
    )

    //
    //    
    // #if MACOS_X
    // const char* OSX_GetLocalizedString( const char* );
    // #endif
    //    
    //    
    // names not in this list can either be lowercase ascii, or '0xnn' hex sequences
    val keynames: Array<keyname_t?>? = arrayOf(
        keyname_t("TAB", KeyInput.K_TAB, "#str_07018"),
        keyname_t("ENTER", KeyInput.K_ENTER, "#str_07019"),
        keyname_t("ESCAPE", KeyInput.K_ESCAPE, "#str_07020"),
        keyname_t("SPACE", KeyInput.K_SPACE, "#str_07021"),
        keyname_t("BACKSPACE", KeyInput.K_BACKSPACE, "#str_07022"),
        keyname_t("UPARROW", KeyInput.K_UPARROW, "#str_07023"),
        keyname_t("DOWNARROW", KeyInput.K_DOWNARROW, "#str_07024"),
        keyname_t("LEFTARROW", KeyInput.K_LEFTARROW, "#str_07025"),
        keyname_t("RIGHTARROW", KeyInput.K_RIGHTARROW, "#str_07026"),  //
        keyname_t("ALT", KeyInput.K_ALT, "#str_07027"),
        keyname_t("RIGHTALT", KeyInput.K_RIGHT_ALT, "#str_07027"),
        keyname_t("CTRL", KeyInput.K_CTRL, "#str_07028"),
        keyname_t("SHIFT", KeyInput.K_SHIFT, "#str_07029"),  //
        keyname_t("LWIN", KeyInput.K_LWIN, "#str_07030"),
        keyname_t("RWIN", KeyInput.K_RWIN, "#str_07031"),
        keyname_t("MENU", KeyInput.K_MENU, "#str_07032"),  //
        keyname_t("COMMAND", KeyInput.K_COMMAND, "#str_07033"),  //
        keyname_t("CAPSLOCK", KeyInput.K_CAPSLOCK, "#str_07034"),
        keyname_t("SCROLL", KeyInput.K_SCROLL, "#str_07035"),
        keyname_t("PRINTSCREEN", KeyInput.K_PRINT_SCR, "#str_07179"),  //
        keyname_t("F1", KeyInput.K_F1, "#str_07036"),
        keyname_t("F2", KeyInput.K_F2, "#str_07037"),
        keyname_t("F3", KeyInput.K_F3, "#str_07038"),
        keyname_t("F4", KeyInput.K_F4, "#str_07039"),
        keyname_t("F5", KeyInput.K_F5, "#str_07040"),
        keyname_t("F6", KeyInput.K_F6, "#str_07041"),
        keyname_t("F7", KeyInput.K_F7, "#str_07042"),
        keyname_t("F8", KeyInput.K_F8, "#str_07043"),
        keyname_t("F9", KeyInput.K_F9, "#str_07044"),
        keyname_t("F10", KeyInput.K_F10, "#str_07045"),
        keyname_t("F11", KeyInput.K_F11, "#str_07046"),
        keyname_t("F12", KeyInput.K_F12, "#str_07047"),  //
        keyname_t("INS", KeyInput.K_INS, "#str_07048"),
        keyname_t("DEL", KeyInput.K_DEL, "#str_07049"),
        keyname_t("PGDN", KeyInput.K_PGDN, "#str_07050"),
        keyname_t("PGUP", KeyInput.K_PGUP, "#str_07051"),
        keyname_t("HOME", KeyInput.K_HOME, "#str_07052"),
        keyname_t("END", KeyInput.K_END, "#str_07053"),  //
        keyname_t("MOUSE1", KeyInput.K_MOUSE1, "#str_07054"),
        keyname_t("MOUSE2", KeyInput.K_MOUSE2, "#str_07055"),
        keyname_t("MOUSE3", KeyInput.K_MOUSE3, "#str_07056"),
        keyname_t("MOUSE4", KeyInput.K_MOUSE4, "#str_07057"),
        keyname_t("MOUSE5", KeyInput.K_MOUSE5, "#str_07058"),
        keyname_t("MOUSE6", KeyInput.K_MOUSE6, "#str_07059"),
        keyname_t("MOUSE7", KeyInput.K_MOUSE7, "#str_07060"),
        keyname_t("MOUSE8", KeyInput.K_MOUSE8, "#str_07061"),  //
        keyname_t("MWHEELUP", KeyInput.K_MWHEELUP, "#str_07131"),
        keyname_t("MWHEELDOWN", KeyInput.K_MWHEELDOWN, "#str_07132"),  //
        keyname_t("JOY1", KeyInput.K_JOY1, "#str_07062"),
        keyname_t("JOY2", KeyInput.K_JOY2, "#str_07063"),
        keyname_t("JOY3", KeyInput.K_JOY3, "#str_07064"),
        keyname_t("JOY4", KeyInput.K_JOY4, "#str_07065"),
        keyname_t("JOY5", KeyInput.K_JOY5, "#str_07066"),
        keyname_t("JOY6", KeyInput.K_JOY6, "#str_07067"),
        keyname_t("JOY7", KeyInput.K_JOY7, "#str_07068"),
        keyname_t("JOY8", KeyInput.K_JOY8, "#str_07069"),
        keyname_t("JOY9", KeyInput.K_JOY9, "#str_07070"),
        keyname_t("JOY10", KeyInput.K_JOY10, "#str_07071"),
        keyname_t("JOY11", KeyInput.K_JOY11, "#str_07072"),
        keyname_t("JOY12", KeyInput.K_JOY12, "#str_07073"),
        keyname_t("JOY13", KeyInput.K_JOY13, "#str_07074"),
        keyname_t("JOY14", KeyInput.K_JOY14, "#str_07075"),
        keyname_t("JOY15", KeyInput.K_JOY15, "#str_07076"),
        keyname_t("JOY16", KeyInput.K_JOY16, "#str_07077"),
        keyname_t("JOY17", KeyInput.K_JOY17, "#str_07078"),
        keyname_t("JOY18", KeyInput.K_JOY18, "#str_07079"),
        keyname_t("JOY19", KeyInput.K_JOY19, "#str_07080"),
        keyname_t("JOY20", KeyInput.K_JOY20, "#str_07081"),
        keyname_t("JOY21", KeyInput.K_JOY21, "#str_07082"),
        keyname_t("JOY22", KeyInput.K_JOY22, "#str_07083"),
        keyname_t("JOY23", KeyInput.K_JOY23, "#str_07084"),
        keyname_t("JOY24", KeyInput.K_JOY24, "#str_07085"),
        keyname_t("JOY25", KeyInput.K_JOY25, "#str_07086"),
        keyname_t("JOY26", KeyInput.K_JOY26, "#str_07087"),
        keyname_t("JOY27", KeyInput.K_JOY27, "#str_07088"),
        keyname_t("JOY28", KeyInput.K_JOY28, "#str_07089"),
        keyname_t("JOY29", KeyInput.K_JOY29, "#str_07090"),
        keyname_t("JOY30", KeyInput.K_JOY30, "#str_07091"),
        keyname_t("JOY31", KeyInput.K_JOY31, "#str_07092"),
        keyname_t("JOY32", KeyInput.K_JOY32, "#str_07093"),  //
        keyname_t("AUX1", KeyInput.K_AUX1, "#str_07094"),
        keyname_t("AUX2", KeyInput.K_AUX2, "#str_07095"),
        keyname_t("AUX3", KeyInput.K_AUX3, "#str_07096"),
        keyname_t("AUX4", KeyInput.K_AUX4, "#str_07097"),
        keyname_t("AUX5", KeyInput.K_AUX5, "#str_07098"),
        keyname_t("AUX6", KeyInput.K_AUX6, "#str_07099"),
        keyname_t("AUX7", KeyInput.K_AUX7, "#str_07100"),
        keyname_t("AUX8", KeyInput.K_AUX8, "#str_07101"),
        keyname_t("AUX9", KeyInput.K_AUX9, "#str_07102"),
        keyname_t("AUX10", KeyInput.K_AUX10, "#str_07103"),
        keyname_t("AUX11", KeyInput.K_AUX11, "#str_07104"),
        keyname_t("AUX12", KeyInput.K_AUX12, "#str_07105"),
        keyname_t("AUX13", KeyInput.K_AUX13, "#str_07106"),
        keyname_t("AUX14", KeyInput.K_AUX14, "#str_07107"),
        keyname_t("AUX15", KeyInput.K_AUX15, "#str_07108"),
        keyname_t("AUX16", KeyInput.K_AUX16, "#str_07109"),  //
        keyname_t("KP_HOME", KeyInput.K_KP_HOME, "#str_07110"),
        keyname_t("KP_UPARROW", KeyInput.K_KP_UPARROW, "#str_07111"),
        keyname_t("KP_PGUP", KeyInput.K_KP_PGUP, "#str_07112"),
        keyname_t("KP_LEFTARROW", KeyInput.K_KP_LEFTARROW, "#str_07113"),
        keyname_t("KP_5", KeyInput.K_KP_5, "#str_07114"),
        keyname_t("KP_RIGHTARROW", KeyInput.K_KP_RIGHTARROW, "#str_07115"),
        keyname_t("KP_END", KeyInput.K_KP_END, "#str_07116"),
        keyname_t("KP_DOWNARROW", KeyInput.K_KP_DOWNARROW, "#str_07117"),
        keyname_t("KP_PGDN", KeyInput.K_KP_PGDN, "#str_07118"),
        keyname_t("KP_ENTER", KeyInput.K_KP_ENTER, "#str_07119"),
        keyname_t("KP_INS", KeyInput.K_KP_INS, "#str_07120"),
        keyname_t("KP_DEL", KeyInput.K_KP_DEL, "#str_07121"),
        keyname_t("KP_SLASH", KeyInput.K_KP_SLASH, "#str_07122"),
        keyname_t("KP_MINUS", KeyInput.K_KP_MINUS, "#str_07123"),
        keyname_t("KP_PLUS", KeyInput.K_KP_PLUS, "#str_07124"),
        keyname_t("KP_NUMLOCK", KeyInput.K_KP_NUMLOCK, "#str_07125"),
        keyname_t("KP_STAR", KeyInput.K_KP_STAR, "#str_07126"),
        keyname_t("KP_EQUALS", KeyInput.K_KP_EQUALS, "#str_07127"),  //
        keyname_t("PAUSE", KeyInput.K_PAUSE, "#str_07128"),  //
        keyname_t("SEMICOLON", ';', "#str_07129"),  // because a raw semicolon separates commands
        keyname_t("APOSTROPHE", '\'', "#str_07130"),  // because a raw apostrophe messes with parsing
        //
        keyname_t(null, 0, null)
    )

    //
    // keys that can be set without a special name
    val unnamedkeys: String? = "*,-=./[\\]1234567890abcdefghijklmnopqrstuvwxyz"

    //    
    //    
    //
    //
    var key_overstrikeMode = false
    var keys: Array<idKey?>? = null
    var lastKeyIndex = 0

    //
    var lastKeys: CharArray? = CharArray(32)

    object idKeyInput {
        /*
         ===================
         idKeyInput::KeyNumToString

         Returns a string (either a single ascii char, a K_* name, or a 0x11 hex string) for the
         given keynum.
         ===================
         */
        var tinystr: CharArray? = CharArray(5)

        /*
         ============
         idKeyInput::KeysFromBinding
         returns the localized name of the key for the binding
         ============
         */
        private val keyName: CharArray? = CharArray(Lib.Companion.MAX_STRING_CHARS)

        @Throws(idException::class)
        fun Init() {
            KeyInput.keys = arrayOfNulls<idKey?>(KeyInput.MAX_KEYS)
            for (k in KeyInput.keys.indices) {
                KeyInput.keys[k] = idKey()
            }

            // register our functions
            CmdSystem.cmdSystem.AddCommand(
                "bind",
                Key_Bind_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "binds a command to a key",
                ArgCompletion_KeyName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "bindunbindtwo",
                Key_BindUnBindTwo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "binds a key but unbinds it first if there are more than two binds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "unbind",
                Key_Unbind_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "unbinds any command from a key",
                ArgCompletion_KeyName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "unbindall",
                Key_Unbindall_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "unbinds any commands from all keys"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listBinds",
                Key_ListBinds_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists key bindings"
            )
        }

        fun Shutdown() {
//	delete [] keys;
            KeyInput.keys = null
        }

        /*
         ===================
         idKeyInput::PreliminaryKeyEvent

         Tracks global key up/down state
         Called by the system for both key up and key down events
         ===================
         */
        @Throws(idException::class)
        fun PreliminaryKeyEvent(keyNum: Int, down: Boolean) {
            KeyInput.keys[keyNum].down = down
            if (KeyInput.ID_DOOM_LEGACY) {
                if (down) {
                    KeyInput.lastKeys[0 + (KeyInput.lastKeyIndex and 15)] = keyNum.toChar()
                    KeyInput.lastKeys[16 + (KeyInput.lastKeyIndex and 15)] = keyNum.toChar()
                    KeyInput.lastKeyIndex = KeyInput.lastKeyIndex + 1 and 15
                    var i = 0
                    while (KeyInput.cheatCodes[i] != null) {
                        val l = KeyInput.cheatCodes[i].length
                        assert(l <= 16)
                        if (idStr.Companion.Icmpn(
                                TempDump.ctos(KeyInput.lastKeys).substring(16 + (KeyInput.lastKeyIndex and 15) - l),
                                KeyInput.cheatCodes[i],
                                l
                            ) == 0
                        ) {
                            Common.common.Printf("your memory serves you well!\n")
                            break
                        }
                        i++
                    }
                }
            }
        }

        fun IsDown(keyNum: Int): Boolean {
            return if (keyNum == -1) {
                false
            } else KeyInput.keys[keyNum].down
        }

        fun GetUsercmdAction(keyNum: Int): Int {
            return KeyInput.keys[keyNum].usercmdAction
        }

        fun GetOverstrikeMode(): Boolean {
            return KeyInput.key_overstrikeMode
        }

        fun SetOverstrikeMode(state: Boolean) {
            KeyInput.key_overstrikeMode = state
        }

        @Throws(idException::class)
        fun ClearStates() {
            var i: Int
            i = 0
            while (i < KeyInput.MAX_KEYS) {
                if (KeyInput.keys[i].down) {
                    PreliminaryKeyEvent(i, false)
                }
                KeyInput.keys[i].down = false
                i++
            }

            // clear the usercommand states
            UsercmdGen.usercmdGen.Clear()
        }

        /*
         ===================
         idKeyInput::StringToKeyNum

         Returns a key number to be used to index keys[] by looking at
         the given string.  Single ascii characters return themselves, while
         the K_* names are matched up.

         0x11 will be interpreted as raw hex, which will allow new controlers
         to be configured even if they don't have defined names.
         ===================
         */
        fun StringToKeyNum(str: String?): Int {
            var kn: Int
            if (null == str || str.isEmpty()) {
                return -1
            }
            if (1 == str.length) {
                return str[0]
            }

            // check for hex code
            if (str[0] == '0' && str[0] == 'x' && str.length == 4) {
                var n1: Int
                var n2: Int
                n1 = str[2].code
                if (n1 >= '0'.code && n1 <= '9'.code) {
                    n1 -= '0'.code
                } else if (n1 >= 'a'.code && n1 <= 'f'.code) {
                    n1 = n1 - 'a'.code + 10
                } else {
                    n1 = 0
                }
                n2 = str[3].code
                if (n2 >= '0'.code && n2 <= '9'.code) {
                    n2 -= '0'.code
                } else if (n2 >= 'a'.code && n2 <= 'f'.code) {
                    n2 = n2 - 'a'.code + 10
                } else {
                    n2 = 0
                }
                return n1 * 16 + n2
            }

            // scan for a text match
            kn = 0
            while (kn < KeyInput.keynames.size) {
                if (0 == idStr.Companion.Icmp(str, KeyInput.keynames[kn].name)) {
                    return KeyInput.keynames[kn].keynum
                }
                kn++
            }
            return -1
        }

        @Throws(idException::class)
        fun KeyNumToString(keyNum: Int, localized: Boolean): String? {
//	keyname_t	kn;
            val i: Int
            val j: Int
            if (keyNum == -1) {
                return "<KEY NOT FOUND>"
            }
            if (keyNum < 0 || keyNum > 255) {
                return "<OUT OF RANGE>"
            }

            // check for printable ascii (don't use quote)
            if (keyNum > 32 && keyNum < 127 && keyNum != '"'.code && keyNum != ';'.code && keyNum != '\''.code) {
                tinystr.get(0) = win_input.Sys_MapCharForKey(keyNum)
                tinystr.get(1) = 0
                return TempDump.ctos(tinystr)
            }

            // check for a key string
            for (kn in KeyInput.keynames) {
                if (keyNum == kn.keynum) {
                    return if (!localized || kn.strId[0] != '#') {
                        kn.name
                    } else {
                        if (BuildDefines.MACOS_X) {
                            when (kn.keynum) {
                                KeyInput.K_ENTER, KeyInput.K_BACKSPACE, KeyInput.K_ALT, KeyInput.K_INS, KeyInput.K_PRINT_SCR -> Common.common.GetLanguageDict()
                                    .GetString(kn.strId)
                                else -> Common.common.GetLanguageDict().GetString(kn.strId)
                            }
                        } else {
                            Common.common.GetLanguageDict().GetString(kn.strId)
                        }
                    }
                }
            }

            // check for European high-ASCII characters
            if (localized && keyNum >= 161 && keyNum <= 255) {
                tinystr.get(0) = keyNum.toChar()
                tinystr.get(1) = 0
                return TempDump.ctos(tinystr)
            }

            // make a hex string
            i = keyNum shr 4
            j = keyNum and 15
            tinystr.get(0) = '0'
            tinystr.get(1) = 'x'
            tinystr.get(2) = (if (i > 9) i - 10 + 'a'.code else i + '0'.code).toChar()
            tinystr.get(3) = (if (j > 9) j - 10 + 'a'.code else j + '0'.code).toChar()
            tinystr.get(4) = 0
            return TempDump.ctos(tinystr)
        }

        fun SetBinding(keyNum: Int, binding: String?) {
            if (keyNum == -1) {
                return
            }

            // Clear out all button states so we aren't stuck forever thinking this key is held down
            UsercmdGen.usercmdGen.Clear()

            // allocate memory for new binding
            KeyInput.keys[keyNum].binding = idStr(binding)

            // find the action for the async command generation
            KeyInput.keys[keyNum].usercmdAction = UsercmdGen.usercmdGen.CommandStringUsercmdData(binding)

            // consider this like modifying an archived cvar, so the
            // file write will be triggered at the next oportunity
            CVarSystem.cvarSystem.SetModifiedFlags(CVarSystem.CVAR_ARCHIVE)
        }

        fun GetBinding(keyNum: Int): String? {
            return if (keyNum == -1) {
                ""
            } else KeyInput.keys[keyNum].binding.toString()
        }

        fun UnbindBinding(binding: String?): Boolean {
            var unbound = false
            var i: Int
            if (binding != null) {
                i = 0
                while (i < KeyInput.MAX_KEYS) {
                    if (KeyInput.keys[i].binding.Icmp(binding) == 0) {
                        SetBinding(i, "")
                        unbound = true
                    }
                    i++
                }
            }
            return unbound
        }

        fun NumBinds(binding: String?): Int {
            var i: Int
            var count = 0
            if (binding != null) {
                i = 0
                while (i < KeyInput.MAX_KEYS) {
                    if (KeyInput.keys[i].binding.Icmp(binding) == 0) {
                        count++
                    }
                    i++
                }
            }
            return count
        }

        @Throws(idException::class)
        fun ExecKeyBinding(keyNum: Int): Boolean {
            // commands that are used by the async thread
            // don't add text
            if (KeyInput.keys[keyNum].usercmdAction != 0) {
                return false
            }

            // send the bound action
            if (KeyInput.keys[keyNum].binding.Length() != 0) {
                CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_APPEND,
                    KeyInput.keys[keyNum].binding.toString()
                )
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "\n")
            }
            return true
        }

        @Throws(idException::class)
        fun KeysFromBinding(bind: String?): String? {
            var i: Int
            keyName.get(0) = '\u0000'
            if (bind != null) {
                i = 0
                while (i < KeyInput.MAX_KEYS) {
                    if (KeyInput.keys[i].binding.Icmp(bind) == 0) {
                        if (keyName.get(0) != '\u0000') {
                            idStr.Companion.Append(
                                keyName,
                                Lib.Companion.MAX_STRING_CHARS,
                                Common.common.GetLanguageDict().GetString("#str_07183")
                            )
                        }
                        idStr.Companion.Append(keyName, keyName.size, KeyNumToString(i, true))
                    }
                    i++
                }
            }
            if (keyName.get(0) == '\u0000') {
                idStr.Companion.Copynz(keyName, Common.common.GetLanguageDict().GetString("#str_07133"), keyName.size)
            }
            idStr.Companion.ToLower(keyName)
            return TempDump.ctos(keyName)
        }

        /*
         ============
         idKeyInput::BindingFromKey
         returns the binding for the localized name of the key
         ============
         */
        fun BindingFromKey(key: String?): String? {
            val keyNum = StringToKeyNum(key)
            return if (keyNum < 0 || keyNum >= KeyInput.MAX_KEYS) {
                null
            } else KeyInput.keys[keyNum].binding.toString()
        }

        fun KeyIsBoundTo(keyNum: Int, binding: String?): Boolean {
            return if (keyNum >= 0 && keyNum < KeyInput.MAX_KEYS) {
                KeyInput.keys[keyNum].binding.Icmp(binding) == 0
            } else false
        }

        /*
         ============
         idKeyInput::WriteBindings

         Writes lines containing "bind key value"
         ============
         */
        @Throws(idException::class)
        fun WriteBindings(f: idFile?) {
            var i: Int
            f.Printf("unbindall\n")
            i = 0
            while (i < KeyInput.MAX_KEYS) {
                if (KeyInput.keys[i].binding.Length() != 0) {
                    val name = KeyNumToString(i, false)

                    // handle the escape character nicely
                    if ("\\" == name) {
                        f.Printf("bind \"\\\" \"%s\"\n", KeyInput.keys[i].binding)
                    } else {
                        f.Printf("bind \"%s\" \"%s\"\n", KeyNumToString(i, false), KeyInput.keys[i].binding)
                    }
                }
                i++
            }
        }

        class ArgCompletion_KeyName : CmdSystem.argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String?>?) {
                var kn: Int
                var i: Int
                i = 0
                while (i < KeyInput.unnamedkeys.length - 1) {
                    callback.run(Str.va("%s %c", args.Argv(0), KeyInput.unnamedkeys[i]))
                    i++
                }
                kn = 0
                while (kn < KeyInput.keynames.size) {
                    callback.run(Str.va("%s %s", args.Argv(0), KeyInput.keynames[kn].name))
                    kn++
                }
            }

            companion object {
                private val instance: CmdSystem.argCompletion_t? = ArgCompletion_KeyName()
                fun getInstance(): CmdSystem.argCompletion_t? {
                    return instance
                }
            }
        }
    }

    internal class keyname_t(
        var name: String?, var keynum: Int, // localized string id
        var strId: String?
    )

    private class idKey {
        var binding: idStr?
        var down = false
        var repeats // if > 1, it is autorepeating
                = 0
        var usercmdAction // for testing by the asyncronous usercmd generation
                : Int

        init {
            binding = idStr()
            usercmdAction = 0
        }
    }

    /////////////////////////////
    /*
     ===================
     Key_Unbind_f
     ===================
     */
    internal class Key_Unbind_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val b: Int
            if (args.Argc() != 2) {
                Common.common.Printf("unbind <key> : remove commands from a key\n")
                return
            }
            b = idKeyInput.StringToKeyNum(args.Argv(1))
            if (b == -1) {
                // If it wasn't a key, it could be a command
                if (!idKeyInput.UnbindBinding(args.Argv(1))) {
                    Common.common.Printf("\"%s\" isn't a valid key\n", args.Argv(1))
                }
            } else {
                idKeyInput.SetBinding(b, "")
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Key_Unbind_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Key_Unbindall_f
     ===================
     */
    internal class Key_Unbindall_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            i = 0
            while (i < KeyInput.MAX_KEYS) {
                idKeyInput.SetBinding(i, "")
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Key_Unbindall_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     Key_Bind_f
     ===================
     */
    internal class Key_Bind_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val c: Int
            val b: Int
            var cmd: String? //= new char[MAX_STRING_CHARS];
            c = args.Argc()
            if (c < 2) {
                Common.common.Printf("bind <key> [command] : attach a command to a key\n")
                return
            }
            b = idKeyInput.StringToKeyNum(args.Argv(1))
            if (b == -1) {
                Common.common.Printf("\"%s\" isn't a valid key\n", args.Argv(1))
                return
            }
            if (c == 2) {
                if (KeyInput.keys[b].binding.Length() != 0) {
                    Common.common.Printf("\"%s\" = \"%s\"\n", args.Argv(1), KeyInput.keys[b].binding.toString())
                } else {
                    Common.common.Printf("\"%s\" is not bound\n", args.Argv(1))
                }
                return
            }

            // copy the rest of the command line
            cmd = "" //[0] = 0;		// start out with a null string
            //            String cmd_str=new String (cmd);
            i = 2
            while (i < c) {
                cmd += args.Argv(i)
                if (i != c - 1) {
                    cmd += " "
                }
                i++
            }
            idKeyInput.SetBinding(b, cmd)
        }

        companion object {
            private val instance: cmdFunction_t? = Key_Bind_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ============
     Key_BindUnBindTwo_f

     binds keynum to bindcommand and unbinds if there are already two binds on the key
     ============
     */
    internal class Key_BindUnBindTwo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val c = args.Argc()
            if (c < 3) {
                Common.common.Printf("bindunbindtwo <keynum> [command]\n")
                return
            }
            val key = args.Argv(1).toInt()
            val bind = args.Argv(2)
            if (idKeyInput.NumBinds(bind) >= 2 && !idKeyInput.KeyIsBoundTo(key, bind)) {
                idKeyInput.UnbindBinding(bind)
            }
            idKeyInput.SetBinding(key, bind)
        }

        companion object {
            private val instance: cmdFunction_t? = Key_BindUnBindTwo_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ============
     Key_ListBinds_f
     ============
     */
    internal class Key_ListBinds_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            i = 0
            while (i < KeyInput.MAX_KEYS) {
                if (KeyInput.keys[i].binding.Length() != 0) {
                    Common.common.Printf(
                        "%s \"%s\"\n",
                        idKeyInput.KeyNumToString(i, false),
                        KeyInput.keys[i].binding.toString()
                    )
                }
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t? = Key_ListBinds_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}