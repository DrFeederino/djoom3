package neo.Game.Script

import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idScriptObject
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDef
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Program.varEval_s
import neo.Game.Script.Script_Thread.idThread
import neo.TempDump
import neo.framework.Common
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.getVec3_zero
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.min

/**
 *
 */
object Script_Interpreter {
    const val LOCALSTACK_SIZE = 6144
    const val MAX_STACK_DEPTH = 64

    class prstack_s {
        var f: function_t? = null
        var s = 0
        var stackbase = 0
    }

    class idInterpreter {
        private val callStack: ArrayList<prstack_s> = ArrayList(MAX_STACK_DEPTH)

        //
        private val localstack: ByteArray = ByteArray(LOCALSTACK_SIZE)
        var debug = false

        //
        var doneProcessing = false
        var terminateOnExit = true
        var threadDying = false
        private var callStackDepth = 0

        //
        private var currentFunction: function_t? = null
        private var eventEntity: idEntity? = null
        private var instructionPointer = 0
        private var localstackBase = 0
        private var localstackUsed = 0
        private var maxLocalstackUsed = 0
        private var maxStackDepth = 0
        private var multiFrameEvent: idEventDef? = null

        //
        //
        //
        private var popParms = 0

        //
        private lateinit var thread: idThread
        private fun PopParms(numParms: Int) {
            // pop our parms off the stack
            if (localstackUsed < numParms) {
                Error("locals stack underflow\n")
            }
            localstackUsed -= numParms
        }

        private fun PushString(string: String) {
//            System.out.println("+++ " + string);
            if (localstackUsed + Script_Program.MAX_STRING_LEN > LOCALSTACK_SIZE) {
                Error("PushString: locals stack overflow\n")
            }
            //            idStr.Copynz(localstack[localstackUsed], string, MAX_STRING_LEN);
            val str = string + '\u0000'
            val length = min(str.length, Script_Program.MAX_STRING_LEN)
            System.arraycopy(str.toByteArray(), 0, localstack, localstackUsed, length)
            localstackUsed += Script_Program.MAX_STRING_LEN
        }

        private fun Push(value: Int) {
            if (localstackUsed == 36) {
                val a = 0
            }
            if (localstackUsed + Integer.BYTES > LOCALSTACK_SIZE) {
                Error("Push: locals stack overflow\n")
            }
            localstack[localstackUsed + 0] = (value ushr 0).toByte()
            localstack[localstackUsed + 1] = (value ushr 8).toByte()
            localstack[localstackUsed + 2] = (value ushr 16).toByte()
            localstack[localstackUsed + 3] = (value ushr 24).toByte()
            localstackUsed += Integer.BYTES
        }

        private fun FloatToString(value: Float): String {
            if (value == value.toInt().toFloat()) {
                text = String.format("%d", value.toInt()).toCharArray()
            } else {
                text = String.format("%f", value).toCharArray()
            }
            return TempDump.ctos(text)
        }

        private fun AppendString(def: idVarDef, from: String) {
            if (def.initialized == initialized_t.stackVariable) {
//                idStr.Append(localstack[localstackBase + def.value.stackOffset], MAX_STRING_LEN, from);
                val str = from + '\u0000'
                val length = min(str.length, Script_Program.MAX_STRING_LEN)
                val offset = localstackBase + def.value.getStackOffset()
                val appendOffset = TempDump.strLen(localstack, offset)
                System.arraycopy(str.toByteArray(), 0, localstack, appendOffset, length)
            } else {
                def.value.stringPtr = idStr.Append(def.value.stringPtr!!, Script_Program.MAX_STRING_LEN, from)!!
            }
        }

        private fun SetString(def: idVarDef, from: String) {
            if (def.initialized == initialized_t.stackVariable) {
//                idStr.Copynz(localstack[localstackBase + def.value.stackOffset], from, MAX_STRING_LEN);
                val str = from + '\u0000'
                val length = min(str.length, Script_Program.MAX_STRING_LEN)
                System.arraycopy(str.toByteArray(), 0, localstack, localstackBase + def.value.getStackOffset(), length)
            } else {
                def.value.stringPtr = from //idStr.Copynz(def.value.stringPtr, from, MAX_STRING_LEN);
            }
        }

        private fun GetString(def: idVarDef): String? {
            return if (def.initialized == initialized_t.stackVariable) {
                TempDump.btos(localstack, localstackBase + def.value.getStackOffset())
            } else {
                def.value.stringPtr
            }
        }

        private fun GetVariable(def: idVarDef): varEval_s {
            return if (def.initialized == initialized_t.stackVariable) {
                val `val` = varEval_s()
                `val`.setIntPtr(
                    localstack,
                    localstackBase + def.value.getStackOffset()
                ) // = ( int * )&localstack[ localstackBase + def->value.stackOffset ];
                `val`
            } else {
                def.value
            }
        }

        private fun GetEvalVariable(def: idVarDef): varEval_s {
            val `var` = GetVariable(def)
            if (`var`.getEntityNumberPtr() != NULL_ENTITY) {
                val scriptObject = Game_local.gameLocal.entities[`var`.getEntityNumberPtr() - 1]!!.scriptObject
                val data = scriptObject.data
                if (data.capacity() > 1) {
                    `var`.evalPtr = varEval_s()
                    `var`.evalPtr!!.setBytePtr(data, scriptObject.offset)
                }
            }
            return `var`
        }

        private fun GetEntity(entnum: Int): idEntity? {
            assert(entnum <= Game_local.MAX_GENTITIES)
            return if (entnum > 0 && entnum <= Game_local.MAX_GENTITIES) {
                Game_local.gameLocal.entities[entnum - 1]
            } else null
        }

        private fun GetScriptObject(entnum: Int): idScriptObject? {
            val ent: idEntity?
            assert(entnum <= Game_local.MAX_GENTITIES)
            if (entnum > 0 && entnum <= Game_local.MAX_GENTITIES) {
                ent = Game_local.gameLocal.entities[entnum - 1]
                if (ent != null && ent.scriptObject.data != null) {
                    return ent.scriptObject
                }
            }
            return null
        }

        private fun NextInstruction(position: Int) {
            // Before we execute an instruction, we increment instructionPointer,
            // therefore we need to compensate for that here.
            instructionPointer = position - 1
        }

        private fun LeaveFunction(returnDef: idVarDef) {
            val stack: prstack_s?
            val ret: varEval_s?
            if (callStackDepth <= 0) {
                Error("prog stack underflow")
            }

            // return value
            if (returnDef != null) {
                when (returnDef.Type()) {
                    Script_Program.ev_string -> Game_local.gameLocal.program.ReturnString(GetString(returnDef)!!)
                    Script_Program.ev_vector -> {
                        ret = GetVariable(returnDef)
                        Game_local.gameLocal.program.ReturnVector(ret.getidVec3Ptr())
                    }
                    else -> {
                        ret = GetVariable(returnDef)
                        Game_local.gameLocal.program.ReturnInteger(ret.getIntPtr())
                    }
                }
            }

            // remove locals from the stack
            PopParms(currentFunction!!.locals)
            assert(localstackUsed == localstackBase)
            if (debug) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Game_local.gameLocal.Printf(
                    "%d: %s(%d): exit %s",
                    Game_local.gameLocal.time,
                    Game_local.gameLocal.program.GetFilename(line.file.toInt()),
                    line.linenumber,
                    currentFunction!!.Name()
                )
                if (callStackDepth > 1) {
                    Game_local.gameLocal.Printf(
                        " return to %s(line %d)\n",
                        callStack[callStackDepth - 1].f!!.Name(),
                        Game_local.gameLocal.program.GetStatement(callStack[callStackDepth - 1].s).linenumber
                    )
                } else {
                    Game_local.gameLocal.Printf(" done\n")
                }
            }

            // up stack
            callStackDepth--
            stack = callStack[callStackDepth]
            currentFunction = stack.f
            localstackBase = stack.stackbase
            NextInstruction(stack.s)
            if (0 == callStackDepth) {
                // all done
                doneProcessing = true
                threadDying = true
                currentFunction = null
            }
        }

        private fun CallEvent(func: function_t?, argsize: Int) {
            var i: Int
            var j: Int
            val `var` = varEval_s()
            var pos: Int
            val start: Int
            val data = ArrayList<idEventArg<*>>(Event.D_EVENT_MAXARGS)
            val evdef: idEventDef
            val format: CharArray
            if (null == func) {
                Error("NULL function")
                return
            }
            assert(func.eventdef != null)
            evdef = func.eventdef!!
            start = localstackUsed - argsize
            `var`.setIntPtr(localstack, start)
            eventEntity = GetEntity(`var`.getEntityNumberPtr())
            if (null == eventEntity || !eventEntity!!.RespondsTo(evdef)) {
                if (eventEntity != null && Common.com_developer.GetBool()) {
                    // give a warning in developer mode
                    Warning(
                        "Function '%s' not supported on entity '%s'",
                        evdef.GetName(),
                        eventEntity!!.name.toString()
                    )
                }
                when (evdef.GetReturnType()) {
                    Event.D_EVENT_INTEGER -> Game_local.gameLocal.program.ReturnInteger(0)
                    Event.D_EVENT_FLOAT -> Game_local.gameLocal.program.ReturnFloat(0f)
                    Event.D_EVENT_VECTOR -> Game_local.gameLocal.program.ReturnVector(getVec3_zero())
                    Event.D_EVENT_STRING -> Game_local.gameLocal.program.ReturnString("")
                    Event.D_EVENT_ENTITY, Event.D_EVENT_ENTITY_NULL -> Game_local.gameLocal.program.ReturnEntity(null)
                    Event.D_EVENT_TRACE -> {}
                    else -> {}
                }
                PopParms(argsize)
                eventEntity = null
                return
            }
            format = evdef.GetArgFormat()!!.toCharArray()
            j = 0
            i = 0
            pos = Script_Program.type_object.Size()
            while (pos < argsize || i < format.size && format[i].code != 0) {
                when (format[i]) {
                    Event.D_EVENT_INTEGER -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(`var`.getFloatPtr().toInt())
                    }
                    Event.D_EVENT_FLOAT -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(`var`.getFloatPtr())
                    }
                    Event.D_EVENT_VECTOR -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(`var`.getidVec3Ptr())
                    }
                    Event.D_EVENT_STRING -> data[i] = idEventArg.toArg(
                        TempDump.btos(
                            localstack,
                            start + pos
                        )
                    ) //( *( const char ** )&data[ i ] ) = ( char * )&localstack[ start + pos ];
                    Event.D_EVENT_ENTITY -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(GetEntity(`var`.getEntityNumberPtr()))
                        if (null == data[i]) {
                            Warning("Entity not found for event '%s'. Terminating thread.", evdef.GetName())
                            threadDying = true
                            PopParms(argsize)
                            return
                        }
                    }
                    Event.D_EVENT_ENTITY_NULL -> {
                        `var`.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(GetEntity(`var`.getEntityNumberPtr()))
                    }
                    Event.D_EVENT_TRACE -> Error(
                        "trace type not supported from script for '%s' event.",
                        evdef.GetName()
                    )
                    else -> Error("Invalid arg format string for '%s' event.", evdef.GetName())
                }
                pos += func.parmSize[j++]
                i++
            }
            popParms = argsize
            eventEntity!!.ProcessEventArgPtr(evdef, data)
            if (null == multiFrameEvent) {
                if (popParms != 0) {
                    PopParms(popParms)
                }
                eventEntity = null
            } else {
                doneProcessing = true
            }
            popParms = 0
        }

        private fun CallSysEvent(func: function_t?, argsize: Int) {
            var i: Int
            var j: Int
            val source = varEval_s()
            var pos: Int
            val start: Int
            val data = kotlin.collections.ArrayList<idEventArg<*>>(Event.D_EVENT_MAXARGS)
            val evdef: idEventDef
            val format: String?
            if (null == func) {
                Error("NULL function")
                return
            }
            assert(func.eventdef != null)
            evdef = func.eventdef!!
            start = localstackUsed - argsize
            format = evdef.GetArgFormat()!!
            j = 0
            i = 0
            pos = 0
            while (pos < argsize || i < format.length) {
                when (format[i]) {
                    Event.D_EVENT_INTEGER -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(source.getFloatPtr().toInt())
                    }
                    Event.D_EVENT_FLOAT -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(source.getFloatPtr())
                    }
                    Event.D_EVENT_VECTOR -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(source.getidVec3Ptr())
                    }
                    Event.D_EVENT_STRING -> data[i] = idEventArg.toArg(TempDump.btos(localstack, start + pos))
                    Event.D_EVENT_ENTITY -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(GetEntity(source.getEntityNumberPtr()))
                        if (null == data[i]) {
                            Warning("Entity not found for event '%s'. Terminating thread.", evdef.GetName())
                            threadDying = true
                            PopParms(argsize)
                            return
                        }
                    }
                    Event.D_EVENT_ENTITY_NULL -> {
                        source.setIntPtr(localstack, start + pos)
                        data[i] = idEventArg.toArg(GetEntity(source.getEntityNumberPtr()))
                    }
                    Event.D_EVENT_TRACE -> Error(
                        "trace type not supported from script for '%s' event.",
                        evdef.GetName()
                    )
                    else -> Error("Invalid arg format string for '%s' event.", evdef.GetName())
                }
                pos += func.parmSize[j++]
                i++
            }

//            throw new TODO_Exception();
            popParms = argsize
            thread.ProcessEventArgPtr(evdef, data)
            if (popParms != 0) {
                PopParms(popParms)
            }
            popParms = 0
        }

        // save games
        fun Save(savefile: idSaveGame) {                // archives object for save game file
            var i: Int
            savefile.WriteInt(callStackDepth)
            i = 0
            while (i < callStackDepth) {
                savefile.WriteInt(callStack[i].s)
                if (callStack[i].f != null) {
                    savefile.WriteInt(Game_local.gameLocal.program.GetFunctionIndex(callStack[i].f!!))
                } else {
                    savefile.WriteInt(-1)
                }
                savefile.WriteInt(callStack[i].stackbase)
                i++
            }
            savefile.WriteInt(maxStackDepth)
            savefile.WriteInt(localstackUsed)
            savefile.Write(ByteBuffer.wrap(localstack), localstackUsed)
            savefile.WriteInt(localstackBase)
            savefile.WriteInt(maxLocalstackUsed)
            if (currentFunction != null) {
                savefile.WriteInt(Game_local.gameLocal.program.GetFunctionIndex(currentFunction!!))
            } else {
                savefile.WriteInt(-1)
            }
            savefile.WriteInt(instructionPointer)
            savefile.WriteInt(popParms)
            if (multiFrameEvent != null) {
                savefile.WriteString(multiFrameEvent!!.GetName())
            } else {
                savefile.WriteString("")
            }
            savefile.WriteObject(eventEntity!!)
            savefile.WriteObject(thread)
            savefile.WriteBool(doneProcessing)
            savefile.WriteBool(threadDying)
            savefile.WriteBool(terminateOnExit)
            savefile.WriteBool(debug)
        }

        fun Restore(savefile: idRestoreGame) {                // unarchives object from save game file
            var i: Int
            val funcname = idStr()
            val func_index = CInt()
            callStackDepth = savefile.ReadInt()
            i = 0
            while (i < callStackDepth) {
                callStack[i].s = savefile.ReadInt()
                savefile.ReadInt(func_index)
                if (func_index._val >= 0) {
                    callStack[i].f = Game_local.gameLocal.program.GetFunction(func_index._val)
                } else {
                    callStack[i].f = null
                }
                callStack[i].stackbase = savefile.ReadInt()
                i++
            }
            maxStackDepth = savefile.ReadInt()
            localstackUsed = savefile.ReadInt()
            savefile.Read(ByteBuffer.wrap(localstack), localstackUsed)
            localstackBase = savefile.ReadInt()
            maxLocalstackUsed = savefile.ReadInt()
            savefile.ReadInt(func_index)
            currentFunction = if (func_index._val >= 0) {
                Game_local.gameLocal.program.GetFunction(func_index._val)
            } else {
                null
            }
            instructionPointer = savefile.ReadInt()
            popParms = savefile.ReadInt()
            savefile.ReadString(funcname)
            if (funcname.Length() != 0) {
                multiFrameEvent = idEventDef.FindEvent(funcname.toString())
            }
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/eventEntity)
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/thread)
            doneProcessing = savefile.ReadBool()
            threadDying = savefile.ReadBool()
            terminateOnExit = savefile.ReadBool()
            debug = savefile.ReadBool()
        }

        fun SetThread(pThread: idThread) {
            thread = pThread
        }

        fun StackTrace() {
            var f: function_t?
            var i: Int
            var top: Int
            if (callStackDepth == 0) {
                Game_local.gameLocal.Printf("<NO STACK>\n")
                return
            }
            top = callStackDepth
            if (top >= MAX_STACK_DEPTH) {
                top = MAX_STACK_DEPTH - 1
            }
            if (null == currentFunction) {
                Game_local.gameLocal.Printf("<NO FUNCTION>\n")
            } else {
                Game_local.gameLocal.Printf(
                    "%12s : %s\n",
                    Game_local.gameLocal.program.GetFilename(currentFunction!!.filenum),
                    currentFunction!!.Name()
                )
            }
            i = top
            while (i >= 0) {
                f = callStack[i].f
                if (null == f) {
                    Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                } else {
                    Game_local.gameLocal.Printf(
                        "%12s : %s\n",
                        Game_local.gameLocal.program.GetFilename(f.filenum),
                        f.Name()
                    )
                }
                i--
            }
        }

        fun CurrentLine(): Int {
            return if (instructionPointer < 0) {
                0
            } else Game_local.gameLocal.program.GetLineNumberForStatement(instructionPointer).toInt()
        }

        fun CurrentFile(): String? {
            return if (instructionPointer < 0) {
                ""
            } else Game_local.gameLocal.program.GetFilenameForStatement(instructionPointer)
        }

        /*
         ============
         idInterpreter::Error

         Aborts the currently executing function
         ============
         */
        fun Error(fmt: String, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            StackTrace()
            if (instructionPointer >= 0 && instructionPointer < Game_local.gameLocal.program.NumStatements()) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Common.common.Error(
                    "%s(%d): Thread '%s': %s\n",
                    Game_local.gameLocal.program.GetFilename(line.file.toInt()),
                    line.linenumber,
                    thread.GetThreadName(),
                    text
                )
            } else {
                Common.common.Error("Thread '%s': %s\n", thread.GetThreadName(), text)
            }
        }

        /*
         ============
         idInterpreter::Warning

         Prints file and line number information with warning.
         ============
         */
        fun Warning(fmt: String, vararg objects: Any?) { // id_attribute((format(printf,2,3)));
            val text = String.format(fmt, *objects)
            if (instructionPointer >= 0 && instructionPointer < Game_local.gameLocal.program.NumStatements()) {
                val line = Game_local.gameLocal.program.GetStatement(instructionPointer)
                Common.common.Warning(
                    "%s(%d): Thread '%s': %s",
                    Game_local.gameLocal.program.GetFilename(line.file.toInt()),
                    line.linenumber,
                    thread!!.GetThreadName(),
                    text
                )
            } else {
                Common.common.Warning("Thread '%s' : %s", thread.GetThreadName(), text)
            }
        }

        fun DisplayInfo() {
            var f: function_t?
            var i: Int
            Game_local.gameLocal.Printf(" Stack depth: %d bytes, %d max\n", localstackUsed, maxLocalstackUsed)
            Game_local.gameLocal.Printf("  Call depth: %d, %d max\n", callStackDepth, maxStackDepth)
            Game_local.gameLocal.Printf("  Call Stack: ")
            if (callStackDepth == 0) {
                Game_local.gameLocal.Printf("<NO STACK>\n")
            } else {
                if (TempDump.NOT(currentFunction)) {
                    Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                } else {
                    Game_local.gameLocal.Printf(
                        "%12s : %s\n",
                        Game_local.gameLocal.program.GetFilename(currentFunction!!.filenum),
                        currentFunction!!.Name()
                    )
                }
                i = callStackDepth
                while (i > 0) {
                    Game_local.gameLocal.Printf("              ")
                    f = callStack[i].f
                    if (null == f) {
                        Game_local.gameLocal.Printf("<NO FUNCTION>\n")
                    } else {
                        Game_local.gameLocal.Printf(
                            "%12s : %s\n",
                            Game_local.gameLocal.program.GetFilename(f.filenum),
                            f.Name()
                        )
                    }
                    i--
                }
            }
        }

        fun BeginMultiFrameEvent(ent: idEntity?, event: idEventDef): Boolean {
            if (eventEntity != ent) {
                Error("idInterpreter::BeginMultiFrameEvent called with wrong entity")
            }
            if (multiFrameEvent != null) {
                if (multiFrameEvent != event) {
                    Error("idInterpreter::BeginMultiFrameEvent called with wrong event")
                }
                return false
            }
            multiFrameEvent = event
            return true
        }

        fun EndMultiFrameEvent(ent: idEntity, event: idEventDef) {
            if (multiFrameEvent != event) {
                Error("idInterpreter::EndMultiFrameEvent called with wrong event")
            }
            multiFrameEvent = null
        }

        fun MultiFrameEventInProgress(): Boolean {
            return multiFrameEvent != null
        }

        /*
         ====================
         idInterpreter::ThreadCall

         Copys the args from the calling thread's stack
         ====================
         */
        fun ThreadCall(source: idInterpreter, func: function_t?, args: Int) {
            Reset()

//	memcpy( localstack, &source.localstack[ source.localstackUsed - args ], args );
            System.arraycopy(source.localstack, source.localstackUsed - args, localstack, 0, args)
            localstackUsed = args
            localstackBase = 0
            maxLocalstackUsed = localstackUsed
            EnterFunction(func, false)
            thread.SetThreadName(currentFunction!!.Name())
        }

        /*
         ====================
         idInterpreter::EnterFunction

         Returns the new program statement counter

         NOTE: If this is called from within a event called by this interpreter, the function arguments will be invalid after calling this function.
         ====================
         */
        fun EnterFunction(func: function_t?, clearStack: Boolean) {
            val c: Int
            val stack: prstack_s?
            if (clearStack) {
                Reset()
            }
            if (popParms != 0) {
                PopParms(popParms)
                popParms = 0
            }
            if (callStackDepth >= MAX_STACK_DEPTH) {
                Error("call stack overflow")
            }
            callStack[callStackDepth] = prstack_s()
            stack = callStack[callStackDepth]
            stack.s = instructionPointer + 1 // point to the next instruction to execute
            stack.f = currentFunction
            stack.stackbase = localstackBase
            callStackDepth++
            if (callStackDepth > maxStackDepth) {
                maxStackDepth = callStackDepth
            }
            if (null == func) {
                Error("NULL function")
                return
            }
            if (debug) {
                if (currentFunction != null) {
                    Game_local.gameLocal.Printf(
                        "%d: call '%s' from '%s'(line %d)%s\n",
                        Game_local.gameLocal.time,
                        func.Name(),
                        currentFunction!!.Name(),
                        Game_local.gameLocal.program.GetStatement(instructionPointer).linenumber,
                        if (clearStack) " clear stack" else ""
                    )
                } else {
                    Game_local.gameLocal.Printf(
                        "%d: call '%s'%s\n",
                        Game_local.gameLocal.time,
                        func.Name(),
                        if (clearStack) " clear stack" else ""
                    )
                }
            }
            currentFunction = func
            assert(TempDump.NOT(func.eventdef))
            NextInstruction(func.firstStatement)

            // allocate space on the stack for locals
            // parms are already on stack
            c = func.locals - func.parmTotal
            assert(c >= 0)
            if (localstackUsed + c > LOCALSTACK_SIZE) {
                Error("EnterFuncton: locals stack overflow\n")
            }

            // initialize local stack variables to zero
            //	memset( &localstack[ localstackUsed ], 0, c );
            Arrays.fill(localstack, localstackUsed, localstackUsed + c, 0.toByte())
            localstackUsed += c
            localstackBase = localstackUsed - func.locals
            if (localstackUsed > maxLocalstackUsed) {
                maxLocalstackUsed = localstackUsed
            }
        }

        /*
         ================
         idInterpreter::EnterObjectFunction

         Calls a function on a script object.

         NOTE: If this is called from within a event called by this interpreter, the function arguments will be invalid after calling this function.
         ================
         */
        fun EnterObjectFunction(self: idEntity, func: function_t, clearStack: Boolean) {
            if (clearStack) {
                Reset()
            }
            if (popParms != 0) {
                PopParms(popParms)
                popParms = 0
            }
            Push(self.entityNumber + 1)
            EnterFunction(func, false)
        }

        fun Execute(): Boolean {
            DBG_Execute++
            var var_a: varEval_s = varEval_s()
            var var_b: varEval_s
            var var_c: varEval_s
            var `var`: varEval_s = varEval_s()
            var st: statement_s
            var runaway: Int
            var newThread: idThread
            var floatVal: Float
            var obj: idScriptObject?
            var func: function_t?
            //            System.out.println(instructionPointer);
            if (threadDying || TempDump.NOT(currentFunction)) {
                return true
            }
            if (multiFrameEvent != null) {
                // move to previous instruction and call it again
                instructionPointer--
            }
            runaway = 5000000
            doneProcessing = false
            while (!doneProcessing && !threadDying) {
                instructionPointer++
                if (0 == --runaway) {
                    Error("runaway loop error")
                }

                // next statement
                st = Game_local.gameLocal.program.GetStatement(instructionPointer)
                val a = st.a!!
                val b = st.b!!
                val c = st.c!!
                when (Script_Compiler.op_codes.values()[st.op.toInt()]) {
                    Script_Compiler.op_codes.OP_RETURN -> LeaveFunction(a)
                    Script_Compiler.op_codes.OP_THREAD -> {
                        newThread = idThread(this, a.value.functionPtr, b.value.getArgSize())
                        newThread.Start()

                        // return the thread number to the script
                        Game_local.gameLocal.program.ReturnFloat(newThread.GetThreadNum().toFloat())
                        PopParms(b.value.getArgSize())
                    }
                    Script_Compiler.op_codes.OP_OBJTHREAD -> {
                        var_a = GetVariable(a)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            func = obj.GetTypeDef()!!.GetFunction(b.value.getVirtualFunction())!!
                            assert(c.value.getArgSize() == func.parmTotal)
                            newThread = idThread(this, GetEntity(var_a.getEntityNumberPtr())!!, func, func.parmTotal)
                            newThread.Start()

                            // return the thread number to the script
                            Game_local.gameLocal.program.ReturnFloat(newThread.GetThreadNum().toFloat())
                        } else {
                            // return a null thread to the script
                            Game_local.gameLocal.program.ReturnFloat(0.0f)
                        }
                        PopParms(c.value.getArgSize())
                    }
                    Script_Compiler.op_codes.OP_CALL -> EnterFunction(a.value.functionPtr!!, false)
                    Script_Compiler.op_codes.OP_EVENTCALL -> CallEvent(a.value.functionPtr, b.value.getArgSize())
                    Script_Compiler.op_codes.OP_OBJECTCALL -> {
                        var_a = GetVariable(a)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            func = obj.GetTypeDef()!!.GetFunction(b.value.getVirtualFunction())!!
                            EnterFunction(func, false)
                        } else {
                            // return a 'safe' value
                            Game_local.gameLocal.program.ReturnVector(getVec3_zero())
                            Game_local.gameLocal.program.ReturnString("")
                            PopParms(c.value.getArgSize())
                        }
                    }
                    Script_Compiler.op_codes.OP_SYSCALL -> CallSysEvent(a.value.functionPtr, b.value.getArgSize())
                    Script_Compiler.op_codes.OP_IFNOT -> {
                        var_a = GetVariable(a)
                        if (var_a.getIntPtr() == 0) {
                            NextInstruction(instructionPointer + b.value.getJumpOffset())
                        }
                    }
                    Script_Compiler.op_codes.OP_IF -> {
                        var_a = GetVariable(a)
                        if (var_a.getIntPtr() != 0) {
                            NextInstruction(instructionPointer + b.value.getJumpOffset())
                        }
                    }
                    Script_Compiler.op_codes.OP_GOTO -> NextInstruction(instructionPointer + a.value.getJumpOffset())
                    Script_Compiler.op_codes.OP_ADD_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getFloatPtr() + var_b.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_ADD_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setVectorPtr(var_a.getidVec3Ptr().plus(var_b.getidVec3Ptr()))
                    }
                    Script_Compiler.op_codes.OP_ADD_S -> {
                        SetString(c, GetString(a)!!)
                        AppendString(c, GetString(b)!!)
                    }
                    Script_Compiler.op_codes.OP_ADD_FS -> {
                        var_a = GetVariable(a)
                        SetString(c, FloatToString(var_a.getFloatPtr()))
                        AppendString(c, GetString(b)!!)
                    }
                    Script_Compiler.op_codes.OP_ADD_SF -> {
                        var_b = GetVariable(b)
                        SetString(c, GetString(a)!!)
                        AppendString(c, FloatToString(var_b.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_ADD_VS -> {
                        var_a = GetVariable(a)
                        SetString(c, var_a.getidVec3Ptr().ToString())
                        AppendString(c, GetString(b)!!)
                    }
                    Script_Compiler.op_codes.OP_ADD_SV -> {
                        var_b = GetVariable(b)
                        SetString(c, GetString(a)!!)
                        AppendString(c, var_b.getidVec3Ptr().ToString())
                    }
                    Script_Compiler.op_codes.OP_SUB_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getFloatPtr() - var_b.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_SUB_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setVectorPtr(var_a.getidVec3Ptr().minus(var_b.getidVec3Ptr()))
                    }
                    Script_Compiler.op_codes.OP_MUL_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getFloatPtr() * var_b.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_MUL_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getidVec3Ptr().times(var_b.getidVec3Ptr()))
                    }
                    Script_Compiler.op_codes.OP_MUL_FV -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setVectorPtr(var_b.getidVec3Ptr().times(var_a.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_MUL_VF -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.getidVec3Ptr().set(var_a.getidVec3Ptr().times(var_b.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_DIV_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        if (var_b.getFloatPtr() == 0.0f) {
                            Warning("Divide by zero")
                            var_c.setFloatPtr(idMath.INFINITY)
                        } else {
                            var_c.setFloatPtr(var_a.getFloatPtr() / var_b.getFloatPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_MOD_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        if (var_b.getFloatPtr() == 0.0f) {
                            Warning("Divide by zero")
                            var_c.setFloatPtr(var_a.getFloatPtr())
                        } else {
                            var_c.setFloatPtr(
                                (var_a.getFloatPtr().toInt() % var_b.getFloatPtr().toInt()).toFloat()
                            ) //TODO:casts!
                        }
                    }
                    Script_Compiler.op_codes.OP_BITAND -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr((var_a.getFloatPtr().toInt() and var_b.getFloatPtr().toInt()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_BITOR -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr((var_a.getFloatPtr().toInt() or var_b.getFloatPtr().toInt()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_GE -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() >= var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_LE -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() <= var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_GT -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() > var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_LT -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() < var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_AND -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getFloatPtr() != 0.0f && var_b.getFloatPtr() != 0.0f).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_AND_BOOLF -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getIntPtr() != 0 && var_b.getFloatPtr() != 0.0f).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_AND_FBOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getFloatPtr() != 0.0f && var_b.getIntPtr() != 0).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_AND_BOOLBOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getIntPtr() != 0 && var_b.getIntPtr() != 0).toFloat())
                    }
                    Script_Compiler.op_codes.OP_OR -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getFloatPtr() != 0.0f || var_b.getFloatPtr() != 0.0f).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_OR_BOOLF -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getIntPtr() != 0 || var_b.getFloatPtr() != 0.0f).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_OR_FBOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getFloatPtr() != 0.0f || var_b.getIntPtr() != 0).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_OR_BOOLBOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getIntPtr() != 0 || var_b.getIntPtr() != 0).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NOT_BOOL -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getIntPtr() == 0).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NOT_F -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() == 0.0f).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NOT_V -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getidVec3Ptr() == getVec3_zero()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NOT_S -> {
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(!TempDump.isNotNullOrEmpty(GetString(a))).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NOT_ENT -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(GetEntity(var_a.getEntityNumberPtr()) == null).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NEG_F -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(-var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_NEG_V -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setVectorPtr(var_a.getidVec3Ptr().unaryMinus())
                    }
                    Script_Compiler.op_codes.OP_INT_F -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_EQ_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() == var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_EQ_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getidVec3Ptr() == var_b.getidVec3Ptr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_EQ_S -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(idStr.Cmp(GetString(a)!!, GetString(b)!!) == 0).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_EQ_E, Script_Compiler.op_codes.OP_EQ_EO, Script_Compiler.op_codes.OP_EQ_OE, Script_Compiler.op_codes.OP_EQ_OO -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getEntityNumberPtr() == var_b.getEntityNumberPtr()).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_NE_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getFloatPtr() != var_b.getFloatPtr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NE_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(TempDump.btoi(var_a.getidVec3Ptr() != var_b.getidVec3Ptr()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_NE_S -> {
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(idStr.Cmp(GetString(a)!!, GetString(b)!!) != 0).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_NE_E, Script_Compiler.op_codes.OP_NE_EO, Script_Compiler.op_codes.OP_NE_OE, Script_Compiler.op_codes.OP_NE_OO -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(
                            TempDump.btoi(var_a.getEntityNumberPtr() != var_b.getEntityNumberPtr()).toFloat()
                        )
                    }
                    Script_Compiler.op_codes.OP_UADD_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr(var_b.getFloatPtr() + var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_UADD_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setVectorPtr(var_b.getidVec3Ptr().plus(var_a.getidVec3Ptr()))
                    }
                    Script_Compiler.op_codes.OP_USUB_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr(var_b.getFloatPtr() - var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_USUB_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setVectorPtr(var_b.getidVec3Ptr().minus(var_a.getidVec3Ptr()))
                    }
                    Script_Compiler.op_codes.OP_UMUL_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr(var_b.getFloatPtr() * var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_UMUL_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setVectorPtr(var_b.getidVec3Ptr().times(var_a.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_UDIV_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        if (var_a.getFloatPtr() == 0.0f) {
                            Warning("Divide by zero")
                            var_b.setFloatPtr(idMath.INFINITY)
                        } else {
                            var_b.setFloatPtr(var_b.getFloatPtr() / var_a.getFloatPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_UDIV_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        if (var_a.getFloatPtr() == 0.0f) {
                            Warning("Divide by zero")
                            var_b.setVectorPtr(floatArrayOf(idMath.INFINITY, idMath.INFINITY, idMath.INFINITY))
                        } else {
                            var_b.setVectorPtr(var_b.getidVec3Ptr().div(var_a.getFloatPtr()))
                        }
                    }
                    Script_Compiler.op_codes.OP_UMOD_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        if (var_a.getFloatPtr() == 0.0f) {
                            Warning("Divide by zero")
                            var_b.setFloatPtr(var_a.getFloatPtr())
                        } else {
                            var_b.setFloatPtr((var_b.getFloatPtr().toInt() % var_a.getFloatPtr().toInt()).toFloat())
                        }
                    }
                    Script_Compiler.op_codes.OP_UOR_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr((var_b.getFloatPtr().toInt() or var_a.getFloatPtr().toInt()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_UAND_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr((var_b.getFloatPtr().toInt() and var_a.getFloatPtr().toInt()).toFloat())
                    }
                    Script_Compiler.op_codes.OP_UINC_F -> {
                        var_a = GetVariable(a)
                        var_a.setFloatPtr(var_a.getFloatPtr() + 1)
                    }
                    Script_Compiler.op_codes.OP_UINCP_F -> {
                        var_a = GetVariable(a)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            `var`.setFloatPtr(`var`.getFloatPtr() + 1)
                        }
                    }
                    Script_Compiler.op_codes.OP_UDEC_F -> {
                        var_a = GetVariable(a)
                        var_a.setFloatPtr(var_a.getFloatPtr() - 1)
                    }
                    Script_Compiler.op_codes.OP_UDECP_F -> {
                        var_a = GetVariable(a)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            `var`.setFloatPtr(`var`.getFloatPtr() - 1)
                        }
                    }
                    Script_Compiler.op_codes.OP_COMP_F -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        var_c.setFloatPtr(var_a.getFloatPtr().toInt().inv().toFloat())
                    }
                    Script_Compiler.op_codes.OP_STORE_F -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr(var_a.getFloatPtr())
                    }
                    Script_Compiler.op_codes.OP_STORE_ENT -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setEntityNumberPtr(var_a.getEntityNumberPtr())
                    }
                    Script_Compiler.op_codes.OP_STORE_BOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setIntPtr(var_a.getIntPtr())
                    }
                    Script_Compiler.op_codes.OP_STORE_OBJENT -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (null == obj) {
                            var_b.setEntityNumberPtr(0)
                        } else if (!obj.GetTypeDef()!!.Inherits(b.TypeDef()!!)) {
                            //Warning( "object '%s' cannot be converted to '%s'", obj.GetTypeName(), st.b.TypeDef().Name() );
                            var_b.setEntityNumberPtr(0)
                        } else {
                            var_b.setEntityNumberPtr(var_a.getEntityNumberPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STORE_OBJ, Script_Compiler.op_codes.OP_STORE_ENTOBJ -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setEntityNumberPtr(var_a.getEntityNumberPtr())
                    }
                    Script_Compiler.op_codes.OP_STORE_S -> SetString(b, GetString(a)!!)
                    Script_Compiler.op_codes.OP_STORE_V -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setVectorPtr(var_a.getidVec3Ptr())
                    }
                    Script_Compiler.op_codes.OP_STORE_FTOS -> {
                        var_a = GetVariable(a)
                        SetString(b, FloatToString(var_a.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_STORE_BTOS -> {
                        var_a = GetVariable(a)
                        SetString(b, if (TempDump.itob(var_a.getIntPtr())) "true" else "false")
                    }
                    Script_Compiler.op_codes.OP_STORE_VTOS -> {
                        var_a = GetVariable(a)
                        SetString(b, var_a.getidVec3Ptr().ToString())
                    }
                    Script_Compiler.op_codes.OP_STORE_FTOBOOL -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        if (var_a.getFloatPtr() != 0.0f) {
                            var_b.setIntPtr(1)
                        } else {
                            var_b.setIntPtr(0)
                        }
                    }
                    Script_Compiler.op_codes.OP_STORE_BOOLTOF -> {
                        var_a = GetVariable(a)
                        var_b = GetVariable(b)
                        var_b.setFloatPtr(java.lang.Float.intBitsToFloat(var_a.getIntPtr()))
                    }
                    Script_Compiler.op_codes.OP_STOREP_F -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setFloatPtr(var_a.getFloatPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_ENT -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setEntityNumberPtr(var_a.getEntityNumberPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_FLD, Script_Compiler.op_codes.OP_STOREP_BOOL -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setIntPtr(var_a.getIntPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_S -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_b.evalPtr!!.setString(GetString(a)!!) //idStr.Copynz(var_b.evalPtr.stringPtr, GetString(st.a), MAX_STRING_LEN);
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_V -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setVectorPtr(var_a.getidVec3Ptr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_FTOS -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setString(FloatToString(var_a.getFloatPtr())) //idStr.Copynz(var_b.evalPtr.stringPtr, FloatToString(var_a.floatPtr.oGet()), MAX_STRING_LEN);
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_BTOS -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            if (var_a.getFloatPtr() != 0.0f) {
                                var_b.evalPtr!!.setString("true") //idStr.Copynz(var_b.evalPtr.stringPtr, "true", MAX_STRING_LEN);
                            } else {
                                var_b.evalPtr!!.setString("false") //idStr.Copynz(var_b.evalPtr.stringPtr, "false", MAX_STRING_LEN);
                            }
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_VTOS -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setString(
                                var_a.getidVec3Ptr().ToString()
                            ) //idStr.Copynz(var_b.evalPtr.stringPtr, var_a.vectorPtr[0].ToString(), MAX_STRING_LEN);
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_FTOBOOL -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            if (var_a.getFloatPtr() != 0.0f) {
                                var_b.evalPtr!!.setIntPtr(1)
                            } else {
                                var_b.evalPtr!!.setIntPtr(0)
                            }
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_BOOLTOF -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.setFloatPtr(java.lang.Float.intBitsToFloat(var_a.getIntPtr()))
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_OBJ -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            var_b.evalPtr!!.setEntityNumberPtr(var_a.getEntityNumberPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_STOREP_OBJENT -> {
                        var_b = GetEvalVariable(b)
                        if (var_b != null && var_b.evalPtr != null) {
                            var_a = GetVariable(a)
                            obj = GetScriptObject(var_a.getEntityNumberPtr())
                            if (null == obj) {
                                var_b.evalPtr!!.setEntityNumberPtr(0)

                                // st.b points to type_pointer, which is just a temporary that gets its type reassigned, so we store the real type in st.c
                                // so that we can do a type check during run time since we don't know what type the script object is at compile time because it
                                // comes from an entity
                            } else if (!obj.GetTypeDef()!!.Inherits(c.TypeDef()!!)) {
                                //Warning( "object '%s' cannot be converted to '%s'", obj.GetTypeName(), st.c.TypeDef().Name() );
                                var_b.evalPtr!!.setEntityNumberPtr(0)
                            } else {
                                var_b.evalPtr!!.setEntityNumberPtr(var_a.getEntityNumberPtr())
                            }
                        }
                    }
                    Script_Compiler.op_codes.OP_ADDRESS -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            obj.offset = b.value.getPtrOffset()
                            var_c.setEvalPtr(var_a.getEntityNumberPtr())
                        } else {
                            var_c.setEvalPtr(NULL_ENTITY)
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_F -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            var_c.setFloatPtr(`var`.getFloatPtr())
                        } else {
                            var_c.setFloatPtr(0.0f)
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_ENT -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            var_c.setEntityNumberPtr(`var`.getEntityNumberPtr())
                        } else {
                            var_c.setEntityNumberPtr(0)
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_BOOL -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            var_c.setIntPtr(`var`.getIntPtr())
                        } else {
                            var_c.setIntPtr(0)
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_S -> {
                        var_a = GetVariable(a)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setStringPtr(obj.data, b.value.getPtrOffset())
                            SetString(c, `var`.stringPtr!!)
                        } else {
                            SetString(c, "")
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_V -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (obj != null) {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            var_c.setVectorPtr(`var`.getidVec3Ptr())
                        } else {
                            var_c.setVectorPtr(getVec3_zero())
                        }
                    }
                    Script_Compiler.op_codes.OP_INDIRECT_OBJ -> {
                        var_a = GetVariable(a)
                        var_c = GetVariable(c)
                        obj = GetScriptObject(var_a.getEntityNumberPtr())
                        if (null == obj) {
                            var_c.setEntityNumberPtr(0)
                        } else {
                            `var`.setBytePtr(obj.data, b.value.getPtrOffset())
                            var_c.setEntityNumberPtr(`var`.getEntityNumberPtr())
                        }
                    }
                    Script_Compiler.op_codes.OP_PUSH_F -> {
                        var_a = GetVariable(a)
                        Push(var_a.getIntPtr())
                    }
                    Script_Compiler.op_codes.OP_PUSH_FTOS -> {
                        var_a = GetVariable(a)
                        PushString(FloatToString(var_a.getFloatPtr()))
                    }
                    Script_Compiler.op_codes.OP_PUSH_BTOF -> {
                        var_a = GetVariable(a)
                        floatVal = var_a.getIntPtr().toFloat()
                        Push(java.lang.Float.floatToIntBits(floatVal))
                    }
                    Script_Compiler.op_codes.OP_PUSH_FTOB -> {
                        var_a = GetVariable(a)
                        if (var_a.getFloatPtr() != 0.0f) {
                            Push(1)
                        } else {
                            Push(0)
                        }
                    }
                    Script_Compiler.op_codes.OP_PUSH_VTOS -> {
                        var_a = GetVariable(a)
                        PushString(var_a.getidVec3Ptr().ToString())
                    }
                    Script_Compiler.op_codes.OP_PUSH_BTOS -> {
                        var_a = GetVariable(a)
                        PushString(if (TempDump.itob(var_a.getIntPtr())) "true" else "false")
                    }
                    Script_Compiler.op_codes.OP_PUSH_ENT -> {
                        var_a = GetVariable(a)
                        Push(var_a.getEntityNumberPtr())
                    }
                    Script_Compiler.op_codes.OP_PUSH_S -> PushString(GetString(a)!!)
                    Script_Compiler.op_codes.OP_PUSH_V -> {
                        var_a = GetVariable(a)
                        Push(java.lang.Float.floatToIntBits(var_a.getidVec3Ptr().x))
                        Push(java.lang.Float.floatToIntBits(var_a.getidVec3Ptr().y))
                        Push(java.lang.Float.floatToIntBits(var_a.getidVec3Ptr().z))
                    }
                    Script_Compiler.op_codes.OP_PUSH_OBJ -> {
                        var_a = GetVariable(a)
                        Push(var_a.getEntityNumberPtr())
                    }
                    Script_Compiler.op_codes.OP_PUSH_OBJENT -> {
                        var_a = GetVariable(a)
                        Push(var_a.getEntityNumberPtr())
                    }
                    Script_Compiler.op_codes.OP_BREAK, Script_Compiler.op_codes.OP_CONTINUE -> Error(
                        "Bad opcode %d",
                        st.op
                    )
                    else -> Error("Bad opcode %d", st.op)
                }
            }
//            var_c = null
//            var_b = var_c
//            var_a = var_b
//            `var` = var_a
            return threadDying
        }

        fun Reset() {
            callStackDepth = 0
            localstackUsed = 0
            localstackBase = 0
            maxLocalstackUsed = 0
            maxStackDepth = 0
            popParms = 0
            multiFrameEvent = null
            eventEntity = null
            currentFunction = null
            NextInstruction(0)
            threadDying = false
            doneProcessing = true
        }

        /*
         ================
         idInterpreter::GetRegisterValue

         Returns a string representation of the value of the register.  This is 
         used primarily for the debugger and debugging

         //FIXME:  This is pretty much wrong.  won't access data in most situations.
         ================
         */
        fun GetRegisterValue(name: String, out: idStr, scopeDepth: Int): Boolean {
            var scopeDepth = scopeDepth
            val reg: varEval_s?
            var d: idVarDef?
            val funcObject = arrayOf<String>("") //new char[1024];
            val funcName: String?
            val scope: idVarDef?
            val field: idTypeDef?
            val obj: idScriptObject
            val func: function_t?
            val funcIndex: Int
            out.Empty()
            if (scopeDepth == -1) {
                scopeDepth = callStackDepth
            }
            func = if (scopeDepth == callStackDepth) {
                currentFunction
            } else {
                callStack[scopeDepth].f
            }
            if (null == func) {
                return false
            }
            idStr.Companion.Copynz(funcObject, func.Name(), 4)
            funcIndex = funcObject[0].indexOf("::")
            if (funcIndex != -1) {
//                funcName = "\0";
                scope = Game_local.gameLocal.program.GetDef(null, funcObject[0], Script_Program.def_namespace)
                funcName = funcObject[0].substring(funcIndex + 2) //TODO:check pointer location
            } else {
                funcName = funcObject[0]
                scope = Script_Program.def_namespace
            }

            // Get the function from the object
            d = Game_local.gameLocal.program.GetDef(null, funcName, scope!!)
            if (null == d) {
                return false
            }

            // Get the variable itself and check various namespaces
            d = Game_local.gameLocal.program.GetDef(null, name, d)
            if (null == d) {
                if (scope === Script_Program.def_namespace) {
                    return false
                }
                d = Game_local.gameLocal.program.GetDef(null, name, scope)
                if (null == d) {
                    d = Game_local.gameLocal.program.GetDef(null, name, Script_Program.def_namespace)
                    if (null == d) {
                        return false
                    }
                }
            }
            reg = GetVariable(d)
            return when (d.Type()) {
                Script_Program.ev_float -> {
                    if (reg.getFloatPtr() != 0.0f) {
                        out.set(Str.va("%g", reg.getFloatPtr()))
                    } else {
                        out.set("0")
                    }
                    true
                }
                Script_Program.ev_vector -> {
                    //                    if (reg.vectorPtr != null) {
                    val vectorPtr = idVec3(reg.getidVec3Ptr())
                    out.set(Str.va("%g,%g,%g", vectorPtr.x, vectorPtr.y, vectorPtr.z))
                    //                    } else {
//                        out.oSet("0,0,0");
//                    }
                    true
                }
                Script_Program.ev_boolean -> {
                    if (reg.getIntPtr() != 0) {
                        out.set(Str.va("%d", reg.getIntPtr()))
                    } else {
                        out.set("0")
                    }
                    true
                }
                Script_Program.ev_field -> {
                    if (scope === Script_Program.def_namespace) {
                        // should never happen, but handle it safely anyway
                        return false
                    }
                    field = scope.TypeDef()!!.GetParmType(reg.getPtrOffset()).FieldType()
                    obj = idScriptObject()
                    obj.Read(
                        ByteBuffer.wrap(
                            localstack.copyOf(callStack[callStackDepth].stackbase)
                        )
                    ) //TODO: check this range
                    if (null == field || null == obj) {
                        return false
                    }
                    when (field.Type()) {
                        Script_Program.ev_boolean -> {
                            out.set(Str.va("%d", obj.data.getInt(reg.getPtrOffset())))
                            true
                        }
                        Script_Program.ev_float -> {
                            out.set(Str.va("%g", obj.data.getFloat(reg.getPtrOffset())))
                            true
                        }
                        else -> false
                    }
                }
                Script_Program.ev_string -> {
                    if (reg.stringPtr.isNotEmpty()) {
                        out.set("\"")
                        out.plusAssign(reg.stringPtr!!)
                        out.plusAssign("\"")
                    } else {
                        out.set("\"\"")
                    }
                    true
                }
                else -> false
            }
            //            return false;
        }

        fun GetCallstackDepth(): Int {
            return callStackDepth
        }

        fun GetCallstack(): prstack_s {
            return callStack[0]
        }

        fun GetCurrentFunction(): function_t? {
            return currentFunction
        }

        fun GetThread(): idThread? {
            return thread
        }

        companion object {
            const val NULL_ENTITY = -1
            var text: CharArray = CharArray(32)
            private var DBG_Execute = 0
        }

        init {
            //            memset(localstack, 0, sizeof(localstack));
//            memset(callStack, 0, sizeof(callStack));
            Reset()
        }
    }
}