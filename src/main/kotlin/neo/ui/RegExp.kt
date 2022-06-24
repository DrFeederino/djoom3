package neo.ui

import neo.TempDump
import neo.framework.Common
import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.Rectangle.idRectangle
import neo.ui.RegExp.idRegister.REGTYPE
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinInt
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinVar
import neo.ui.Winvar.idWinVec2
import neo.ui.Winvar.idWinVec3
import neo.ui.Winvar.idWinVec4

/**
 *
 */
class RegExp {
    class idRegister {
        companion object {
            val REGCOUNT: IntArray = IntArray(TempDump.etoi(REGTYPE.NUMTYPES))
            private var DBG_GetFromRegs = 0

            init {
                val bv = intArrayOf(4, 1, 1, 1, 0, 2, 3, 4)
                System.arraycopy(bv, 0, REGCOUNT, 0, REGCOUNT.size)
            }
        }

        /*unsigned*/  val regs: IntArray = IntArray(4)
        var DBG_D3_KEY = false

        //
        var enabled = false
        val name: idStr = idStr()
        var regCount = 0
        var type: Short = 0
        var storedValue: idWinVar? = null

        //
        //
        constructor()
        constructor(p: String, t: Int) {
            name.set(p)
            type = t.toShort()
            assert(t >= 0 && t < REGTYPE.NUMTYPES.ordinal)
            regCount = REGCOUNT[t]
            enabled = type.toInt() != REGTYPE.STRING.ordinal
            storedValue = null
        }

        fun SetToRegs(registers: FloatArray) {
            var i: Int
            var v: idVec4 = idVec4()
            val v2: idVec2
            val v3 = idVec3()
            val rect = idRectangle()
            if (!enabled || storedValue == null || storedValue != null && (storedValue!!.GetDict() != null || !storedValue!!.GetEval())) {
                return
            }
            when (REGTYPE.values()[type.toInt()]) {
                REGTYPE.VEC4 -> {
                    v = (storedValue as idWinVec4).data
                }
                REGTYPE.RECTANGLE -> {
                    rect.set((storedValue as idWinRectangle).data)
                    v = rect.ToVec4()
                }
                REGTYPE.VEC2 -> {
                    v2 = (storedValue as idWinVec2).data
                    v[0] = v2[0]
                    v[1] = v2[1]
                }
                REGTYPE.VEC3 -> {
                    v3.set((storedValue as idWinVec3).data)
                    v[0] = v3[0]
                    v[1] = v3[1]
                    v[2] = v3[2]
                }
                REGTYPE.FLOAT -> {
                    v[0] = (storedValue as idWinFloat).data
                }
                REGTYPE.INT -> {
                    v[0] = (storedValue as idWinInt).data.toFloat()
                }
                REGTYPE.BOOL -> {
                    v[0] = TempDump.btoi((storedValue as idWinBool).data).toFloat()
                }
                else -> {
                    Common.common.FatalError("idRegister::SetToRegs: bad reg type")
                }
            }
            for (i in 0 until regCount) {
                registers[regs[i]] = v[i]
            }
        }

        fun GetFromRegs(registers: FloatArray) {
            DBG_GetFromRegs++
            val v = idVec4()
            val rect = idRectangle()
            if (!enabled || storedValue == null || storedValue != null && (storedValue!!.GetDict() != null || !storedValue!!.GetEval())) {
                return
            }
            for (i in 0 until regCount) {
                v[i] = registers.get(regs[i])
            }
            when (REGTYPE.values()[type.toInt()]) {
                REGTYPE.VEC4 -> {
                    (storedValue as idWinVec4).set(v)
                }
                REGTYPE.RECTANGLE -> {
                    rect.x = v.x
                    rect.y = v.y
                    rect.w = v.z
                    rect.h = v.w
                    (storedValue as idWinRectangle).set(rect)
                }
                REGTYPE.VEC2 -> {
                    (storedValue as idWinVec2).set(v.ToVec2())
                }
                REGTYPE.VEC3 -> {
                    (storedValue as idWinVec3).set(v.ToVec3())
                }
                REGTYPE.FLOAT -> {
                    (storedValue as idWinFloat).data = v[0]
                }
                REGTYPE.INT -> {
                    (storedValue as idWinInt).data = v[0].toInt()
                }
                REGTYPE.BOOL -> {
                    (storedValue as idWinBool).data = v[0] != 0.0f
                }
                else -> {
                    Common.common.FatalError("idRegister::GetFromRegs: bad reg type")
                }
            }
        }

        fun CopyRegs(src: idRegister) {
            regs[0] = src.regs[0]
            regs[1] = src.regs[1]
            regs[2] = src.regs[2]
            regs[3] = src.regs[3]
        }

        fun Enable(b: Boolean) {
            enabled = b
        }

        fun ReadFromDemoFile(f: idDemoFile) {
            enabled = f.ReadBool()
            type = f.ReadShort()
            regCount = f.ReadInt()
            for (i in 0..3) {
                regs[i] = f.ReadUnsignedShort()
            }
            name.set(f.ReadHashString())
        }

        fun WriteToDemoFile(f: idDemoFile) {
            f.WriteBool(enabled)
            f.WriteShort(type)
            f.WriteInt(regCount)
            for (i in 0..3) {
                f.WriteUnsignedShort(regs[i])
            }
            f.WriteHashString(name.toString())
        }

        fun WriteToSaveGame(savefile: idFile) {
            val len: Int
            savefile.WriteBool(enabled)
            savefile.WriteShort(type)
            savefile.WriteInt(regCount)
            savefile.WriteShort(regs[0].toShort())
            len = name.Length()
            savefile.WriteInt(len)
            savefile.WriteString(name)
            storedValue!!.WriteToSaveGame(savefile)
        }

        fun ReadFromSaveGame(savefile: idFile) {
            val len: Int
            enabled = savefile.ReadBool()
            type = savefile.ReadShort()
            regCount = savefile.ReadInt()
            regs[0] = savefile.ReadShort().toInt()
            len = savefile.ReadInt()
            name.Fill(' ', len)
            savefile.ReadString(name)
            storedValue!!.ReadFromSaveGame(savefile)
        }

        enum class REGTYPE {
            VEC4 /*= 0*/, FLOAT, BOOL, INT, STRING, VEC2, VEC3, RECTANGLE, NUMTYPES
        }
    }

    class idRegisterList {
        private val regHash: idHashIndex
        private val regs: idList<idRegister>

        // ~idRegisterList();
        fun AddReg(name: String, type: Int, src: idParser, win: idWindow, `var`: idWinVar) {
            var reg: idRegister?
            reg = FindReg(name)
            if (null == reg) {
                assert(type >= 0 && type < REGTYPE.NUMTYPES.ordinal)
                val numRegs = idRegister.REGCOUNT[type]
                reg = idRegister(name, type)
                reg.storedValue = `var`
                if (type == REGTYPE.STRING.ordinal) {
                    val tok = idToken()
                    if (src.ReadToken(tok)) {
                        if ("#str_07184" == tok.toString()) {
                            reg.DBG_D3_KEY = true
                        }
                        tok.set(Common.common.GetLanguageDict().GetString(tok.toString()))
                        `var`.Init(tok.toString(), win)
                    }
                } else {
                    for (i in 0 until numRegs) {
                        reg.regs[i] = win.ParseExpression(src, null)
                        if (i < numRegs - 1) {
                            src.ExpectTokenString(",")
                        }
                    }
                }
                val hash = regHash.GenerateKey(name, false)
                regHash.Add(hash, regs.Append(reg))
            } else {
                val numRegs = idRegister.REGCOUNT[type]
                reg.storedValue = `var`
                if (type == REGTYPE.STRING.ordinal) {
                    val tok = idToken()
                    if (src.ReadToken(tok)) {
                        `var`.Init(tok.toString(), win)
                    }
                } else {
                    for (i in 0 until numRegs) {
                        reg.regs[i] = win.ParseExpression(src, null)
                        if (i < numRegs - 1) {
                            src.ExpectTokenString(",")
                        }
                    }
                }
            }
        }

        fun AddReg(name: String, type: Int, data: idVec4, win: idWindow, `var`: idWinVar) {
            if (FindReg(name) == null) {
                assert(type >= 0 && type < REGTYPE.NUMTYPES.ordinal)
                val numRegs = idRegister.REGCOUNT[type]
                val reg = idRegister(name, type)
                reg.storedValue = `var`
                for (i in 0 until numRegs) {
                    reg.regs[i] = win.ExpressionConstant(data[i])
                }
                val hash = regHash.GenerateKey(name, false)
                regHash.Add(hash, regs.Append(reg))
            }
        }

        fun FindReg(name: String): idRegister? {
            val hash = regHash.GenerateKey(name, false)
            var i = regHash.First(hash)
            while (i != -1) {
                if (regs[i].name.Icmp(name) == 0) {
//                    System.out.println(regs.oGet(i));
                    return regs[i]
                }
                i = regHash.Next(i)
            }
            return null
        }

        fun SetToRegs(registers: FloatArray) {
            var i: Int
            i = 0
            while (i < regs.Num()) {
                regs[i].SetToRegs(registers)
                i++
            }
        }

        fun GetFromRegs(registers: FloatArray) {
            for (i in 0 until regs.Num()) {
                regs[i].GetFromRegs(registers)
            }
        }

        fun Reset() {
            regs.DeleteContents(true)
            regHash.Clear()
        }

        fun ReadFromDemoFile(f: idDemoFile) {
            val c = CInt()
            f.ReadInt(c)
            regs.DeleteContents(true)
            for (i in 0 until c._val) {
                val reg = idRegister()
                reg.ReadFromDemoFile(f)
                regs.Append(reg)
            }
        }

        fun WriteToDemoFile(f: idDemoFile) {
            val c = regs.Num()
            f.WriteInt(c)
            for (i in 0 until c) {
                regs[i].WriteToDemoFile(f)
            }
        }

        fun WriteToSaveGame(savefile: idFile) {
            var i: Int
            val num: Int
            num = regs.Num()
            savefile.WriteInt(num)
            i = 0
            while (i < num) {
                regs[i].WriteToSaveGame(savefile)
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile) {
            var i: Int
            val num: Int
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                regs[i].ReadFromSaveGame(savefile)
                i++
            }
        }

        //
        //
        init {
            regs = idList(4) //.SetGranularity(4);
            regHash = idHashIndex(32, 4) //.SetGranularity(4);
            //            regHash.Clear(32, 4);
        }
    }
}