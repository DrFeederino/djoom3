package neo.idlib.Text

import neo.TempDump.CPP_class.Char
import neo.idlib.Text.Str.idStr
import neo.idlib.math.Math_h.idMath

/**
 *
 */
object Token {
    const val TT_BINARY = 0x00010 // binary number
    const val TT_DECIMAL = 0x00002 // decimal number
    const val TT_DOUBLE_PRECISION = 0x00200 // double
    const val TT_EXTENDED_PRECISION = 0x00400 // long double
    const val TT_FLOAT = 0x00080 // floating point number
    const val TT_HEX = 0x00004 // hexadecimal number
    const val TT_INDEFINITE = 0x01000 // indefinite 1.#IND
    const val TT_INFINITE = 0x00800 // infinite 1.#INF

    //
    // number sub types
    const val TT_INTEGER = 0x00001 // integer
    const val TT_IPADDRESS = 0x04000 // ip address
    const val TT_IPPORT = 0x08000 // ip port
    const val TT_LITERAL = 2 // literal
    const val TT_LONG = 0x00020 // long int
    const val TT_NAME = 4 // name
    const val TT_NAN = 0x02000 // NaN
    const val TT_NUMBER = 3 // number
    const val TT_OCTAL = 0x00008 // octal number
    const val TT_PUNCTUATION = 5 // punctuation
    const val TT_SINGLE_PRECISION = 0x00100 // float

    /*
     ===============================================================================

     idToken is a token read from a file or memory with idLexer or idParser

     ===============================================================================
     */
    // token types
    const val TT_STRING = 1 // string
    const val TT_UNSIGNED = 0x00040 // unsigned int
    const val TT_VALUESVALID = 0x10000 // set if intvalue and floatvalue are valid

    // string sub type is the length of the string
    // literal sub type is the ASCII code
    // punctuation sub type is the punctuation id
    // name sub type is the length of the name
    class idToken : idStr {
        //	friend class idParser;
        //	friend class idLexer;
        var flags // token flags, used for recursive defines
                = 0
        var line // line in script the token was on
                = 0
        var linesCrossed // number of lines crossed in white space before token
                = 0
        var subtype // token sub type
                = 0
        var type // token type
                = 0
        var floatValue // floating point value
                = 0.0

        //
        var intValue // integer value
                : Long = 0
        var next // next token in chain, only used by idParser
                : idToken? = null
        var whiteSpaceEnd_p // end of white space before token, only used by idLexer
                = 0
        var whiteSpaceStart_p // start of white space before token, only used by idLexer
                = 0

        //
        //
        constructor()
        constructor(token: idToken?) {
            this.oSet(token)
        }

        // double value of TT_NUMBER
        fun GetDoubleValue(): Double {
            if (type != Token.TT_NUMBER) {
                return 0.0
            }
            if (0 == subtype and Token.TT_VALUESVALID) {
                NumberValue()
            }
            return floatValue
        }

        // float value of TT_NUMBER
        fun GetFloatValue(): Float {
            return GetDoubleValue().toFloat()
        }

        fun GetUnsignedLongValue(): Long {        // unsigned long value of TT_NUMBER
            if (type != Token.TT_NUMBER) {
                return 0
            }
            if (0 == subtype and Token.TT_VALUESVALID) {
                NumberValue()
            }
            return intValue
        }

        fun GetIntValue(): Int {                // int value of TT_NUMBER
            return GetUnsignedLongValue().toInt()
        }

        fun WhiteSpaceBeforeToken(): Boolean { // returns length of whitespace before token
            return whiteSpaceEnd_p > whiteSpaceStart_p
        }

        fun ClearTokenWhiteSpace() {        // forget whitespace before token
            whiteSpaceStart_p = 0
            whiteSpaceEnd_p = 0
            linesCrossed = 0
        }

        //
        fun NumberValue() {                // calculate values for a TT_NUMBER
            var i: Int
            var pow: Int
            var c: Int
            val div: Boolean
            val p: CharArray?
            var pIndex = 0
            var m: Double
            assert(type == Token.TT_NUMBER)
            p = c_str()
            floatValue = 0.0
            intValue = 0
            // floating point number
            if (subtype and Token.TT_FLOAT != 0) {
                if (subtype and (Token.TT_INFINITE or Token.TT_INDEFINITE or Token.TT_NAN) != 0) {
                    if (subtype and Token.TT_INFINITE != 0) {            // 1.#INF
                        val inf = 0x7f800000
                        floatValue = inf.toFloat().toDouble() //TODO:WHY THE DOUBLE CAST?
                    } else if (subtype and Token.TT_INDEFINITE != 0) {    // 1.#IND
                        val ind = -0x400000
                        floatValue = ind.toFloat().toDouble()
                    } else if (subtype and Token.TT_NAN != 0) {            // 1.#QNAN
                        val nan = 0x7fc00000
                        floatValue = nan.toFloat().toDouble()
                    }
                } else {
                    while ( /*p[pIndex]!=null &&*/p[pIndex] != '.' && p[pIndex] != 'e') {
                        floatValue = floatValue * 10.0 + (p[pIndex] - '0').toDouble()
                        pIndex++
                    }
                    if (p[pIndex] == '.') {
                        pIndex++
                        m = 0.1
                        while (pIndex < p.size && p[pIndex] != 'e') {
                            floatValue = floatValue + (p[pIndex] - '0').toDouble() * m
                            m *= 0.1
                            pIndex++
                        }
                    }
                    if (pIndex < p.size && p[pIndex] == 'e') {
                        pIndex++
                        if (p[pIndex] == '-') {
                            div = true
                            pIndex++
                        } else if (p[pIndex] == '+') {
                            div = false
                            pIndex++
                        } else {
                            div = false
                        }
                        pow = 0
                        while (pIndex < p.size) {
                            pow = pow * 10 + (p[pIndex] - '0')
                            pIndex++
                        }
                        m = 1.0
                        i = 0
                        while (i < pow) {
                            m *= 10.0
                            i++
                        }
                        if (div) {
                            floatValue /= m
                        } else {
                            floatValue *= m
                        }
                    }
                }
                intValue = idMath.Ftol(floatValue.toFloat())
            } else if (subtype and Token.TT_DECIMAL != 0) {
                while (pIndex < p.size) {
                    intValue = intValue * 10 + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toDouble()
            } else if (subtype and Token.TT_IPADDRESS != 0) {
                c = 0
                while ( /*p[pIndex] &&*/p[pIndex] != ':') {
                    if (p[pIndex] == '.') {
                        while (c != 3) {
                            intValue = intValue * 10
                            c++
                        }
                        c = 0
                    } else {
                        intValue = intValue * 10 + (p[pIndex] - '0')
                        c++
                    }
                    pIndex++
                }
                while (c != 3) {
                    intValue = intValue * 10
                    c++
                }
                floatValue = intValue.toDouble()
            } else if (subtype and Token.TT_OCTAL != 0) {
                // step over the first zero
                pIndex += 1
                while (pIndex < p.size) {
                    intValue = (intValue shl 3) + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toDouble()
            } else if (subtype and Token.TT_HEX != 0) {
                // step over the leading 0x or 0X
                pIndex += 2
                while (pIndex < p.size) {
                    intValue = intValue shl 4
                    intValue += if (p[pIndex] >= 'a' && p[pIndex] <= 'f') {
                        (p[pIndex] - 'a' + 10).toLong()
                    } else if (p[pIndex] >= 'A' && p[pIndex] <= 'F') {
                        (p[pIndex] - 'A' + 10).toLong()
                    } else {
                        (p[pIndex] - '0').toLong()
                    }
                    p[pIndex]++
                }
                floatValue = intValue.toDouble()
            } else if (subtype and Token.TT_BINARY != 0) {
                // step over the leading 0b or 0B
                pIndex += 2
                while (pIndex < p.size) {
                    intValue = (intValue shl 1) + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toDouble()
            }
            subtype = subtype or Token.TT_VALUESVALID
        }

        // append character without adding trailing zero
        fun AppendDirty(a: Char) {
            EnsureAlloced(len + 2, true)
            //	data[len++] = a;
            data += a
            len++
        }

        fun oSet(token: idToken?): idToken? {
            type = token.type
            subtype = token.subtype
            line = token.line
            linesCrossed = token.linesCrossed
            flags = token.flags
            intValue = token.intValue
            floatValue = token.floatValue
            whiteSpaceStart_p = token.whiteSpaceStart_p
            whiteSpaceEnd_p = token.whiteSpaceEnd_p
            next = token.next
            super.oSet(token)
            return this
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }

            //idStr
            if (javaClass != obj.javaClass) {
                return super.equals(obj)
            }
            val other = obj as idToken?
            return data.startsWith(other.data)
        }
    }
}