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
            val REGCOUNT: IntArray? = IntArray(TempDump.etoi(REGTYPE.NUMTYPES))
            private var DBG_GetFromRegs = 0

            init {
                val bv = intArrayOf(4, 1, 1, 1, 0, 2, 3, 4)
                System.arraycopy(neo.ui.bv, 0, REGCOUNT, 0, REGCOUNT.size)
            }
        }

        /*unsigned*/  val regs: ShortArray? = ShortArray(4)
        var DBG_D3_KEY = false

        //
        var enabled = false
        var name: idStr? = null
        var regCount = 0
        var type: Short = 0
        var `var`: idWinVar? = null

        //
        //
        constructor()
        constructor(p: String?, t: Int) {
            name = idStr(p)
            type = t.toShort()
            assert(t >= 0 && t < REGTYPE.NUMTYPES.ordinal)
            regCount = REGCOUNT.get(t)
            enabled = type.toInt() != REGTYPE.STRING.ordinal
            `var` = null
        }

        fun SetToRegs(registers: FloatArray?) {
            var i: Int
            var v: idVec4? = idVec4()
            val v2: idVec2?
            val v3 = idVec3()
            val rect = idRectangle()
            if (!enabled || `var` == null || `var` != null && (`var`.GetDict() != null || !`var`.GetEval())) {
                return
            }
            when (REGTYPE.values()[type.toInt()]) {
                REGTYPE.VEC4 -> {
                    v = (`var` as idWinVec4?).data
                }
                REGTYPE.RECTANGLE -> {
                    rect.oSet((`var` as idWinRectangle?).data)
                    v = rect.ToVec4()
                }
                REGTYPE.VEC2 -> {
                    v2 = (`var` as idWinVec2?).data
                    v.oSet(0, v2.oGet(0))
                    v.oSet(1, v2.oGet(1))
                }
                REGTYPE.VEC3 -> {
                    v3.oSet((`var` as idWinVec3?).data)
                    v.oSet(0, v3.oGet(0))
                    v.oSet(1, v3.oGet(1))
                    v.oSet(2, v3.oGet(2))
                }
                REGTYPE.FLOAT -> {
                    v.oSet(0, (`var` as idWinFloat?).data)
                }
                REGTYPE.INT -> {
                    v.oSet(0, (`var` as idWinInt?).data.toFloat())
                }
                REGTYPE.BOOL -> {
                    v.oSet(0, TempDump.btoi((`var` as idWinBool?).data).toFloat())
                }
                else -> {
                    Common.common.FatalError("idRegister::SetToRegs: bad reg type")
                }
            }
            i = 0
            while (i < regCount) {
                if (java.lang.Float.isInfinite(v.oGet(i).also { registers.get(regs.get(i)) = it })) {
                    val bla = 111
                }
                i++
            }
        }

        fun GetFromRegs(registers: FloatArray?) {
            DBG_GetFromRegs++
            val v = idVec4()
            val rect = idRectangle()
            if (!enabled || `var` == null || `var` != null && (`var`.GetDict() != null || !`var`.GetEval())) {
                return
            }
            for (i in 0 until regCount) {
                v.oSet(i, registers.get(regs.get(i)))
            }
            when (REGTYPE.values()[type.toInt()]) {
                REGTYPE.VEC4 -> {
                    (`var` as idWinVec4?).oSet(v)
                }
                REGTYPE.RECTANGLE -> {
                    rect.x = v.x
                    rect.y = v.y
                    rect.w = v.z
                    rect.h = v.w
                    (`var` as idWinRectangle?).oSet(rect)
                }
                REGTYPE.VEC2 -> {
                    (`var` as idWinVec2?).oSet(v.ToVec2())
                }
                REGTYPE.VEC3 -> {
                    (`var` as idWinVec3?).oSet(v.ToVec3())
                }
                REGTYPE.FLOAT -> {
                    (`var` as idWinFloat?).data = v.oGet(0)
                }
                REGTYPE.INT -> {
                    (`var` as idWinInt?).data = v.oGet(0).toInt()
                }
                REGTYPE.BOOL -> {
                    (`var` as idWinBool?).data = v.oGet(0) != 0.0f
                }
                else -> {
                    Common.common.FatalError("idRegister::GetFromRegs: bad reg type")
                }
            }
        }

        fun CopyRegs(src: idRegister?) {
            regs.get(0) = src.regs.get(0)
            regs.get(1) = src.regs.get(1)
            regs.get(2) = src.regs.get(2)
            regs.get(3) = src.regs.get(3)
        }

        fun Enable(b: Boolean) {
            enabled = b
        }

        fun ReadFromDemoFile(f: idDemoFile?) {
            enabled = f.ReadBool()
            type = f.ReadShort()
            regCount = f.ReadInt()
            for (i in 0..3) {
                regs.get(i) = f.ReadUnsignedShort().toShort()
            }
            name.oSet(f.ReadHashString())
        }

        fun WriteToDemoFile(f: idDemoFile?) {
            f.WriteBool(enabled)
            f.WriteShort(type)
            f.WriteInt(regCount)
            for (i in 0..3) {
                f.WriteUnsignedShort(regs.get(i))
            }
            f.WriteHashString(name.toString())
        }

        fun WriteToSaveGame(savefile: idFile?) {
            val len: Int
            savefile.WriteBool(enabled)
            savefile.WriteShort(type)
            savefile.WriteInt(regCount)
            savefile.WriteShort(regs.get(0))
            len = name.Length()
            savefile.WriteInt(len)
            savefile.WriteString(name)
            `var`.WriteToSaveGame(savefile)
        }

        fun ReadFromSaveGame(savefile: idFile?) {
            val len: Int
            enabled = savefile.ReadBool()
            type = savefile.ReadShort()
            regCount = savefile.ReadInt()
            regs.get(0) = savefile.ReadShort()
            len = savefile.ReadInt()
            name.Fill(' ', len)
            savefile.ReadString(name)
            `var`.ReadFromSaveGame(savefile)
        }

        enum class REGTYPE {
            VEC4 /*= 0*/, FLOAT, BOOL, INT, STRING, VEC2, VEC3, RECTANGLE, NUMTYPES
        }
    }

    internal class idRegisterList {
        private val regHash: idHashIndex?
        private val regs: idList<idRegister?>?

        // ~idRegisterList();
        fun AddReg(name: String?, type: Int, src: idParser?, win: idWindow?, `var`: idWinVar?) {
            var reg: idRegister?
            reg = FindReg(name)
            if (null == reg) {
                assert(type >= 0 && type < REGTYPE.NUMTYPES.ordinal)
                val numRegs = idRegister.REGCOUNT.get(type)
                reg = idRegister(name, type)
                reg.`var` = `var`
                if (type == REGTYPE.STRING.ordinal) {
                    val tok = idToken()
                    if (src.ReadToken(tok)) {
                        if ("#str_07184" == tok.toString()) {
                            reg.DBG_D3_KEY = true
                        }
                        tok.oSet(Common.common.GetLanguageDict().GetString(tok.toString()))
                        `var`.Init(tok.toString(), win)
                    }
                } else {
                    for (i in 0 until numRegs) {
                        reg.regs.get(i) = win.ParseExpression(src, null).toShort()
                        if (i < numRegs - 1) {
                            src.ExpectTokenString(",")
                        }
                    }
                }
                val hash = regHash.GenerateKey(name, false)
                regHash.Add(hash, regs.Append(reg))
            } else {
                val numRegs = idRegister.REGCOUNT.get(type)
                reg.`var` = `var`
                if (type == REGTYPE.STRING.ordinal) {
                    val tok = idToken()
                    if (src.ReadToken(tok)) {
                        `var`.Init(tok.toString(), win)
                    }
                } else {
                    for (i in 0 until numRegs) {
                        reg.regs.get(i) = win.ParseExpression(src, null).toShort()
                        if (i < numRegs - 1) {
                            src.ExpectTokenString(",")
                        }
                    }
                }
            }
        }

        fun AddReg(name: String?, type: Int, data: idVec4?, win: idWindow?, `var`: idWinVar?) {
            if (FindReg(name) == null) {
                assert(type >= 0 && type < REGTYPE.NUMTYPES.ordinal)
                val numRegs = idRegister.REGCOUNT.get(type)
                val reg = idRegister(name, type)
                reg.`var` = `var`
                for (i in 0 until numRegs) {
                    reg.regs.get(i) = win.ExpressionConstant(data.oGet(i)).toShort()
                }
                val hash = regHash.GenerateKey(name, false)
                regHash.Add(hash, regs.Append(reg))
            }
        }

        fun FindReg(name: String?): idRegister? {
            val hash = regHash.GenerateKey(name, false)
            var i = regHash.First(hash)
            while (i != -1) {
                if (regs.oGet(i).name.Icmp(name) == 0) {
//                    System.out.println(regs.oGet(i));
                    return regs.oGet(i)
                }
                i = regHash.Next(i)
            }
            return null
        }

        fun SetToRegs(registers: FloatArray?) {
            var i: Int
            i = 0
            while (i < regs.Num()) {
                regs.oGet(i).SetToRegs(registers)
                i++
            }
        }

        fun GetFromRegs(registers: FloatArray?) {
            for (i in 0 until regs.Num()) {
                regs.oGet(i).GetFromRegs(registers)
            }
        }

        fun Reset() {
            regs.DeleteContents(true)
            regHash.Clear()
        }

        fun ReadFromDemoFile(f: idDemoFile?) {
            val c = CInt()
            f.ReadInt(c)
            regs.DeleteContents(true)
            for (i in 0 until c.getVal()) {
                val reg = idRegister()
                reg.ReadFromDemoFile(f)
                regs.Append(reg)
            }
        }

        fun WriteToDemoFile(f: idDemoFile?) {
            val c = regs.Num()
            f.WriteInt(c)
            for (i in 0 until c) {
                regs.oGet(i).WriteToDemoFile(f)
            }
        }

        fun WriteToSaveGame(savefile: idFile?) {
            var i: Int
            val num: Int
            num = regs.Num()
            savefile.WriteInt(num)
            i = 0
            while (i < num) {
                regs.oGet(i).WriteToSaveGame(savefile)
                i++
            }
        }

        fun ReadFromSaveGame(savefile: idFile?) {
            var i: Int
            val num: Int
            num = savefile.ReadInt()
            i = 0
            while (i < num) {
                regs.oGet(i).ReadFromSaveGame(savefile)
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