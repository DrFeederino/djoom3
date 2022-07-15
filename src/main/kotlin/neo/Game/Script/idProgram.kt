/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neo.Game.Script

import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.Script_Compiler.idCompiler
import neo.Game.Script.Script_Compiler.opcode_s
import neo.Game.Script.Script_Program.MAX_FUNCS
import neo.Game.Script.Script_Program.MAX_STATEMENTS
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idCompileError
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDef
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Program.idVarDefName
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Program.varEval_s
import neo.Game.Script.Script_Thread.idThread
import neo.TempDump
import neo.framework.FileSystem_h.fsMode_t
import neo.framework.File_h.idFile
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer
import java.util.*

/* **********************************************************************

 idProgram

 Handles compiling and storage of script data.  Multiple idProgram objects
 would represent seperate programs with no knowledge of each other.  Scripts
 meant to access shared data and functions should all be compiled by a
 single idProgram.

 ***********************************************************************/
class idProgram {

    private val fileList = ArrayList<String>()
    private val filename: idStr = idStr()
    private var filenum = 0

    private var numVariables = 0
    private var variables: ByteArray = ByteArray(Script_Program.MAX_GLOBALS)
    private val variableDefaults: ArrayList<Byte> = ArrayList(Script_Program.MAX_GLOBALS)
    private val functions: ArrayList<function_t> = ArrayList(MAX_FUNCS)
    private val statements: ArrayList<statement_s> = ArrayList(MAX_STATEMENTS)
    private val types: ArrayList<idTypeDef> = ArrayList()
    private val varDefNames: ArrayList<idVarDefName> = ArrayList()
    private val varDefNameHash: idHashIndex = idHashIndex()
    private val varDefs: ArrayList<idVarDef> = ArrayList()

    private var sysDef: idVarDef? = null

    private var top_functions = 0
    private var top_statements = 0
    private var top_types = 0
    private var top_defs = 0
    private var top_files = 0

    public var returnDef: idVarDef? = null
    public var returnStringDef: idVarDef? = null

    // save games
    // Used to insure program code has not
    //    changed between savegames

    fun ArrayList<*>.MemoryUsed(): Int {
        return size * Integer.BYTES
    }

    fun ArrayList<String>.sizeStrings(): Int {
        return idStr.SIZE * size
    }


    /*
     ==============
     idProgram::CompileStats

     called after all files are compiled to report memory usage.
     ==============
     */
    private fun CompileStats() {
        var memused: Int
        val memallocated: Int
        val numdefs: Int
        var stringspace: Int
        var funcMem: Int
        var i: Int
        Game_local.gameLocal.Printf("---------- Compile stats ----------\n")
        Game_local.gameLocal.DPrintf("Files loaded:\n")
        stringspace = 0
        i = 0
        while (i < fileList.size) {
            Game_local.gameLocal.DPrintf("   %s\n", fileList[i])
            stringspace += fileList[i].length
            i++
        }
        stringspace += fileList.sizeStrings()
        numdefs = varDefs.size
        memused = varDefs.size * idVarDef.BYTES
        memused += types.size * idTypeDef.BYTES
        memused += stringspace
        i = 0
        while (i < types.size) {
            memused += types[i].Allocated()
            i++
        }
        funcMem = functions.MemoryUsed()
        i = 0
        while (i < functions.size) {
            funcMem += functions[i].Allocated()
            i++
        }
        memallocated = funcMem + memused + BYTES
        memused += statements.MemoryUsed()
        memused += functions.MemoryUsed() // name and filename of functions are shared, so no need to include them
        memused += variables.size
        Game_local.gameLocal.Printf("\nMemory usage:\n")
        Game_local.gameLocal.Printf("     Strings: %d, %d bytes\n", fileList.size, stringspace)
        Game_local.gameLocal.Printf("  Statements: %d, %d bytes\n", statements.size, statements.MemoryUsed())
        Game_local.gameLocal.Printf("   Functions: %d, %d bytes\n", functions.size, funcMem)
        Game_local.gameLocal.Printf("   Variables: %d bytes\n", numVariables)
        Game_local.gameLocal.Printf("    Mem used: %d bytes\n", memused)
        Game_local.gameLocal.Printf(" Static data: %d bytes\n", BYTES)
        Game_local.gameLocal.Printf("   Allocated: %d bytes\n", memallocated)
        Game_local.gameLocal.Printf(" Thread size: %d bytes\n\n", idThread.BYTES)
    }

    // ~idProgram();
    // save games
    fun Save(savefile: idSaveGame) {
        var i: Int
        var currentFileNum = top_files
        savefile.WriteInt(fileList.size - currentFileNum)
        while (currentFileNum < fileList.size) {
            savefile.WriteString(fileList[currentFileNum])
            currentFileNum++
        }
        i = 0
        while (i < variableDefaults.size) {
            if (variables[i] != variableDefaults[i]) {
                savefile.WriteInt(i)
                savefile.WriteByte(variables[i])
            }
            i++
        }
        // Mark the end of the diff with default variables with -1
        savefile.WriteInt(-1)
        savefile.WriteInt(numVariables)
        i = variableDefaults.size
        while (i < numVariables) {
            savefile.WriteByte(variables[i])
            i++
        }
        val checksum = CalculateChecksum()
        savefile.WriteInt(checksum)
    }

    fun Restore(savefile: idRestoreGame): Boolean {
        var i: Int
        val num = CInt()
        val index = CInt()
        var result = true
        val scriptname = idStr()
        savefile.ReadInt(num)
        i = 0
        while (i < num._val) {
            savefile.ReadString(scriptname)
            CompileFile(scriptname.toString())
            i++
        }
        savefile.ReadInt(index)
        while (index._val >= 0) {
            variables[index._val] = savefile.ReadByte()
            savefile.ReadInt(index)
        }
        savefile.ReadInt(num)
        i = variableDefaults.size
        while (i < num._val) {
            variables[i] = savefile.ReadByte()
            i++
        }
        val saved_checksum = CInt()
        val checksum: Int
        savefile.ReadInt(saved_checksum)
        checksum = CalculateChecksum()
        if (saved_checksum._val != checksum) {
            result = false
        }
        return result
    }

    // Used to insure program code has not
    fun CalculateChecksum(): Int {
        var i: Int
        val result: Int

        class statementBlock_t {
            var a = 0
            var b = 0
            var c = 0
            var file: UShort = 0u
            var lineNumber: UShort = 0u
            var   /*unsigned short*/op: UShort = 0u
            fun toArray(): IntArray {
                return intArrayOf(op.toInt(), a, b, c, lineNumber.toInt(), file.toInt())
            }
        }

        val statementList = Array(statements.size) { statementBlock_t() }
        val statementIntArray = IntArray(statements.size * 6)

//	memset( statementList, 0, ( sizeof(statementBlock_t) * statements.size ) );
        // Copy info into new list, using the variable numbers instead of a pointer to the variable
        i = 0
        while (i < statements.size) {
            //statementList[i] = statementBlock_t()
            statementList[i].op = statements[i].op
            if (statements[i].a != null) {
                statementList[i].a = statements[i].a!!.num
            } else {
                statementList[i].a = -1
            }
            if (statements[i].b != null) {
                statementList[i].b = statements[i].b!!.num
            } else {
                statementList[i].b = -1
            }
            if (statements[i].c != null) {
                statementList[i].c = statements[i].c!!.num
            } else {
                statementList[i].c = -1
            }
            statementList[i].lineNumber = statements[i].linenumber
            statementList[i].file = statements[i].file
            System.arraycopy(statementList[i].toArray(), 0, statementIntArray, i * 6, 6)
            i++
        }
        result =
            0 // new BigInteger(MD4_BlockChecksum(statementIntArray, /*sizeof(statementBlock_t)*/ statements.size)).intValue();

//	delete [] statementList;
        return result
    }

    //    changed between savegames
    fun Startup(defaultScript: String) {
        Game_local.gameLocal.Printf("Initializing scripts\n")
        // make sure all data is freed up
        idThread.Restart()
        // get ready for loading scripts
        BeginCompilation()

        // load the default script
        if (!defaultScript.isNullOrEmpty()) {
            CompileFile(defaultScript)
        }

        FinishCompilation()
    }

    /*
     ==============
     idProgram::Restart

     Restores all variables to their initial value
     ==============
     */
    fun Restart() {
        var i: Int
        idThread.Restart()

        //
        // since there may have been a script loaded by the map or the user may
        // have typed "script" from the console, free up any types and vardefs that
        // have been allocated after the initial startup
        //
//	for( i = top_types; i < types.size; i++ ) {
//		delete types[ i ];
//	}
        types.ensureCapacity(top_types)

//	for( i = top_defs; i < varDefs.size; i++ ) {
//		delete varDefs[ i ];
//	}
        varDefs.ensureCapacity(top_defs)
        i = top_functions
        while (i < functions.size) {
            functions[i].Clear()
            i++
        }
        functions.ensureCapacity(top_functions)
        statements.ensureCapacity(top_statements)
        fileList.ensureCapacity(top_files)
        filename.Clear()

        // reset the variables to their default values
        numVariables = variableDefaults.size
        i = 0
        while (i < numVariables) {
            variables[i] = variableDefaults[i]
            i++
        }
    }

    fun CompileText(source: String, text: String, console: Boolean): Boolean {
        val compiler = idCompiler()
        var i: Int
        var def: idVarDef
        val ospath: String?

        // use a full os path for GetFilenum since it calls OSPathToRelativePath to convert filenames from the parser
        ospath = idLib.fileSystem.RelativePathToOSPath(source)
        filenum = GetFilenum(ospath)
        try {
            compiler.CompileFile(text, filename.toString(), console)

            // check to make sure all functions prototyped have code
            i = 0
            while (i < varDefs.size) {
                def = varDefs[i]
                if (def.Type() == Script_Program.ev_function && (def.scope!!.Type() == Script_Program.ev_namespace || def.scope!!.TypeDef()!!
                        .Inherits(Script_Program.type_object))
                ) {
                    if (null == def.value.functionPtr!!.eventdef && 0 == def.value.functionPtr!!.firstStatement) {
                        throw idCompileError(Str.va("function %s was not defined\n", def.GlobalName()))
                    }
                }
                i++
            }
        } catch (err: idCompileError) {
            if (console) {
                Game_local.gameLocal.Printf("%s\n", err.error)
                return false
            } else {
                idGameLocal.Error("%s\n", err.error)
            }
        }
        if (!console) {
            CompileStats()
        }
        return true
    }

    fun CompileFunction(functionName: String, text: String): function_t? {
        val result: Boolean
        result = CompileText(functionName, text, false)
        if (SysCvar.g_disasm.GetBool()) {
            Disassemble()
        }
        if (!result) {
            idGameLocal.Error("Compile failed.")
        }
        return FindFunction(functionName)
    }

    fun CompileFile(filename: String) {
        val src = arrayOf<ByteBuffer?>(null)
        val result: Boolean
        if (idLib.fileSystem.ReadFile(filename, src, null) < 0) {
            idGameLocal.Error("Couldn't load %s\n", filename)
        }
        result = CompileText(filename, String(src[0]!!.array()), false)
        idLib.fileSystem.FreeFile(src)
        if (SysCvar.g_disasm.GetBool()) {
            Disassemble()
        }
        if (!result) {
            idGameLocal.Error("Compile failed in file %s.", filename)
        }
    }

    /*
     ==============
     idProgram::BeginCompilation

     called before compiling a batch of files, clears the pr struct
     ==============
     */
    fun BeginCompilation() {
        val statement: statement_s
        FreeData()
        try {
            // make the first statement a return for a "NULL" function
            statement = AllocStatement()
            statement.linenumber = 0u
            statement.file = 0u
            statement.op = Script_Compiler.op_codes.OP_RETURN.ordinal.toUShort()
            statement.a = null
            statement.b = null
            statement.c = null

            // define NULL
            //AllocDef( &type_void, "<NULL>", &def_namespace, true );
            // define the return def
            returnDef = AllocDef(Script_Program.type_vector, "<RETURN>", Script_Program.def_namespace, false)

            // define the return def for strings
            returnStringDef = AllocDef(Script_Program.type_string, "<RETURN>", Script_Program.def_namespace, false)

            // define the sys object
            sysDef = AllocDef(Script_Program.type_void, "sys", Script_Program.def_namespace, true)
        } catch (err: idCompileError) {
            idGameLocal.Error("%s", err.error)
        }
    }

    /*
     ==============
     idProgram::FinishCompilation

     Called after all files are compiled to check for errors
     ==============
     */
    fun FinishCompilation() {
        var i: Int
        top_functions = functions.size
        top_statements = statements.size
        top_types = types.size
        top_defs = varDefs.size
        top_files = fileList.size
        variableDefaults.clear()
        variableDefaults.ensureCapacity(numVariables)
        i = 0
        while (i < numVariables) {
            variableDefaults[i] = variables[i]
            i++
        }
    }

    fun DisassembleStatement(file: idFile, instructionPointer: Int) {
        val op: opcode_s
        val statement: statement_s?
        statement = statements[instructionPointer]
        op = idCompiler.opcodes.get(statement.op.toInt())
        file.Printf(
            "%20s(%d):\t%6d: %15s\t",
            fileList[statement.file.toInt()],
            statement.linenumber,
            instructionPointer,
            op.opname
        )
        if (statement.a != null) {
            file.Printf("\ta: ")
            statement.a!!.PrintInfo(file, instructionPointer)
        }
        if (statement.b != null) {
            file.Printf("\tb: ")
            statement.b!!.PrintInfo(file, instructionPointer)
        }
        if (statement.c != null) {
            file.Printf("\tc: ")
            statement.c!!.PrintInfo(file, instructionPointer)
        }
        file.Printf("\n")
    }

    fun Disassemble() {
        var i: Int
        var instructionPointer: Int
        var func: function_t?
        val file: idFile
        file = idLib.fileSystem.OpenFileByMode("script/disasm.txt", fsMode_t.FS_WRITE)!!
        i = 0
        while (i < functions.size) {
            func = functions[i]
            if (func.eventdef != null) {
                // skip eventdefs
                i++
                continue
            }
            file.Printf(
                "\nfunction %s() %d stack used, %d parms, %d locals {\n",
                func.Name(),
                func.locals,
                func.parmTotal,
                func.locals - func.parmTotal
            )
            instructionPointer = 0
            while (instructionPointer < func.numStatements) {
                DisassembleStatement(file, func.firstStatement + instructionPointer)
                instructionPointer++
            }
            file.Printf("}\n")
            i++
        }
        idLib.fileSystem.CloseFile(file)
    }

    fun FreeData() {
        var i: Int

        // free the defs
        varDefs.clear()
        varDefNames.clear()
        varDefNameHash.Free()
        returnDef = null
        returnStringDef = null
        sysDef = null

        // free any special types we've created
        types.clear()
        filenum = 0
        numVariables = 0
        //	memset( variables, 0, sizeof( variables ) );
        variables = ByteArray(variables.size)

        // clear all the strings in the functions so that it doesn't look like we're leaking memory.
        i = 0
        while (i < functions.size) {
            functions[i].Clear()
            i++
        }
        filename.Clear()
        fileList.clear()
        statements.clear()
        functions.clear()
        top_functions = 0
        top_statements = 0
        top_types = 0
        top_defs = 0
        top_files = 0
        filename.set("")
    }

    fun GetFilename(num: Int): String {
        return fileList[num]
    }

    fun kotlin.collections.ArrayList<String>.addUnique(s: String): Int {
        var index = indexOf(s)
        if (index == -1) {
            add(s)
            index = indexOf(s)
        }
        return index
    }

    fun GetFilenum(name: String): Int {
        if (filename.toString() == name) {
            return filenum
        }
        val strippedName: String?
        strippedName = idLib.fileSystem.OSPathToRelativePath(name)
        filenum = if (TempDump.isNotNullOrEmpty(strippedName)) {
            // not off the base path so just use the full path
            fileList.addUnique(name)
        } else {
            fileList.addUnique(strippedName)
        }

        // save the unstripped name so that we don't have to strip the incoming name every time we call GetFilenum
        filename.set(name)
        return filenum
    }

    fun GetLineNumberForStatement(index: Int): UShort {
        return statements[index].linenumber
    }

    fun GetFilenameForStatement(index: Int): String {
        return GetFilename(statements[index].file.toInt())
    }

    fun AllocType(type: idTypeDef): idTypeDef {
        val newtype: idTypeDef
        newtype = idTypeDef(type)
        types.add(newtype)
        return newtype
    }

    fun AllocType(   /*etype_t*/etype: Int, edef: idVarDef?, ename: String, esize: Int, aux: idTypeDef?): idTypeDef {
        val newtype: idTypeDef
        newtype = idTypeDef(etype, edef, ename, esize, aux)
        types.add(newtype)
        return newtype
    }

    /*
     ============
     idProgram::GetType

     Returns a preexisting complex type that matches the parm, or allocates
     a new one and copies it out.
     ============
     */
    fun GetType(type: idTypeDef, allocate: Boolean): idTypeDef? {
        var i: Int

        //FIXME: linear search == slow
        i = types.size - 1
        while (i >= 0) {
            if (types[i].MatchesType(type) && types[i].Name() == type.Name()) {
                return types[i]
            }
            i--
        }
        return if (!allocate) {
            null
        } else AllocType(type)

        // allocate a new one
    }

    /*
     ============
     idProgram::FindType

     Returns a preexisting complex type that matches the name, or returns NULL if not found
     ============
     */
    fun FindType(name: String): idTypeDef? {
        var check: idTypeDef?
        var i: Int
        i = types.size - 1
        while (i >= 0) {
            check = types[i]
            if (check.Name() == name) {
                return check
            }
            i--
        }
        return null
    }

    fun AllocDef(type: idTypeDef, name: String, scope: idVarDef?, constant: Boolean): idVarDef {
        val def: idVarDef
        var element: String
        val def_x: idVarDef?
        val def_y: idVarDef?
        val def_z: idVarDef?

        // allocate a new def
        def = idVarDef(type)
        def.scope = scope!!
        def.numUsers = 1
        varDefs.add(def)
        def.num = varDefs.indexOf(def)
        def.value = varEval_s()

        // add the def to the list with defs with this name and set the name pointer
        AddDefToNameList(def, name)
        if (type.Type() == Script_Program.ev_vector || type.Type() == Script_Program.ev_field && type.FieldType()!!
                .Type() == Script_Program.ev_vector
        ) {
            //
            // vector
            //
            if (Script_Compiler.RESULT_STRING == name) {
                // <RESULT> vector defs don't need the _x, _y and _z components
                assert(scope.Type() == Script_Program.ev_function)
                def.value.setStackOffset(scope.value.functionPtr!!.locals)
                def.initialized = initialized_t.stackVariable
                scope.value.functionPtr!!.locals += type.size
            } else if (scope.TypeDef()!!.Inherits(Script_Program.type_object)) {
                val newtype = idTypeDef(Script_Program.ev_field, null, "float field", 0, Script_Program.type_float)
                val type2 = GetType(newtype, true)

                // set the value to the variable's position in the object
                def.value.setPtrOffset(scope.TypeDef()!!.size)

                // make automatic defs for the vectors elements
                // origin can be accessed as origin_x, origin_y, and origin_z
                element = String.format("%s_x", def.Name())
                def_x = AllocDef(type2!!, element, scope, constant)
                element = String.format("%s_y", def.Name())
                def_y = AllocDef(type2, element, scope, constant)
                def_y.value.setPtrOffset(def_x.value.getPtrOffset() + Script_Program.type_float.size)
                element = String.format("%s_z", def.Name())
                def_z = AllocDef(type2, element, scope, constant)
                def_z.value.setPtrOffset(def_y.value.getPtrOffset() + Script_Program.type_float.size)
            } else {
                // make automatic defs for the vectors elements
                // origin can be accessed as origin_x, origin_y, and origin_z
                element = String.format("%s_x", def.Name())
                def_x = AllocDef(Script_Program.type_float, element, scope, constant)
                element = String.format("%s_y", def.Name())
                def_y = AllocDef(Script_Program.type_float, element, scope, constant)
                element = String.format("%s_z", def.Name())
                def_z = AllocDef(Script_Program.type_float, element, scope, constant)

                // point the vector def to the x coordinate
                def.value = def_x.value
                def.initialized = def_x.initialized
            }
        } else if (scope.TypeDef()!!.Inherits(Script_Program.type_object)) {
            //
            // object variable
            //
            // set the value to the variable's position in the object
            def.value.setPtrOffset(scope.TypeDef()!!.size)
        } else if (scope.Type() == Script_Program.ev_function) {
            //
            // stack variable
            //
            // since we don't know how many local variables there are,
            // we have to have them go backwards on the stack
            def.value.setStackOffset(scope.value.functionPtr!!.locals)
            def.initialized = initialized_t.stackVariable
            if (type.Inherits(Script_Program.type_object)) {
                // objects only have their entity number on the stack, not the entire object
                scope.value.functionPtr!!.locals += Script_Program.type_object.size
            } else {
                scope.value.functionPtr!!.locals += type.size
            }
        } else {
            //
            // global variable
            //
            def.value.setBytePtr(variables, numVariables)
            numVariables += def.TypeDef()!!.size
            //            System.out.println(def.TypeDef().Name());
            if (numVariables > variables.size) {
                throw idCompileError(Str.va("Exceeded global memory size (%d bytes)", variables.size))
            }
            Arrays.fill(variables, numVariables, variables.size, 0.toByte())
            //                memset(def.value.bytePtr, 0, def.TypeDef().size);
        }
        return def
    }

    /*
     ============
     idProgram::GetDef

     If type is NULL, it will match any type
     ============
     */
    fun GetDef(type: idTypeDef?, name: String, scope: idVarDef?): idVarDef? {
        var def: idVarDef?
        var bestDef: idVarDef?
        var bestDepth: Int
        var depth: Int
        bestDepth = 0
        bestDef = null
        def = GetDefList(name)
        while (def != null) {
            if (def.scope!!.Type() == Script_Program.ev_namespace) {
                depth = def.DepthOfScope(scope)
                if (0 == depth) {
                    // not in the same namespace
                    def = def.Next()
                    continue
                }
            } else if (def.scope !== scope) {
                // in a different function
                def = def.Next()
                continue
            } else {
                depth = 1
            }
            if (null == bestDef || depth < bestDepth) {
                bestDepth = depth
                bestDef = def
            }
            def = def.Next()
        }

        // see if the name is already in use for another type
        if (bestDef != null && type != null && bestDef.TypeDef() != type) {
            throw idCompileError(Str.va("Type mismatch on redeclaration of %s", name))
        }
        return bestDef
    }

    fun FreeDef(def: idVarDef, scope: idVarDef?) {
        var e: idVarDef?
        var i: Int
        if (def.Type() == Script_Program.ev_vector) {
            var name: String
            name = String.format("%s_x", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
            name = String.format("%s_y", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
            name = String.format("%s_z", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
        }
        varDefs.removeAt(def.num)
        i = def.num
        while (i < varDefs.size) {
            varDefs[i].num = i
            i++
        }
        def.close()
    }

    fun FindFreeResultDef(type: idTypeDef, name: String, scope: idVarDef?, a: idVarDef?, b: idVarDef?): idVarDef? {
        var def: idVarDef?
        def = GetDefList(name)
        while (def != null) {
            if (def == a || def == b) {
                def = def.Next()
                continue
            }
            if (def.TypeDef() != type) {
                def = def.Next()
                continue
            }
            if (def.scope != scope) {
                def = def.Next()
                continue
            }
            if (def.numUsers <= 1) {
                def = def.Next()
                continue
            }
            def = def.Next()
            return def
        }
        return AllocDef(type, name, scope, false)
    }

    fun GetDefList(name: String): idVarDef? {
        var i: Int
        val hash: Int
        hash = varDefNameHash.GenerateKey(name, true)
        i = varDefNameHash.First(hash)
        while (i != -1) {
            if (idStr.Cmp(varDefNames[i].Name(), name) == 0) {
                return varDefNames[i].GetDefs()
            }
            i = varDefNameHash.Next(i)
        }
        return null
    }

    fun AddDefToNameList(def: idVarDef, name: String) {
        var i: Int
        val hash: Int
        hash = varDefNameHash.GenerateKey(name, true)
        i = varDefNameHash.First(hash)
        while (i != -1) {
            if (idStr.Cmp(varDefNames[i].Name(), name) == 0) {
                break
            }
            i = varDefNameHash.Next(i)
        }
        if (i == -1) {
            val newDefName = idVarDefName(name)
            varDefNames.add(newDefName)
            i = varDefNames.indexOf(newDefName)
            varDefNameHash.Add(hash, i)
        }
        varDefNames[i].AddDef(def)
    }

    /*
     ================
     idProgram::FindFunction

     Searches for the specified function in the currently loaded script.  A full namespace should be
     specified if not in the global namespace.

     Returns 0 if function not found.
     Returns >0 if function found.
     ================
     */
    fun FindFunction(name: String): function_t? {                // returns NULL if function not found
        var start: Int
        var pos: Int
        var namespaceDef: idVarDef
        var def: idVarDef?
        assert(name != null)
        val fullname = idStr(name)
        start = 0
        namespaceDef = Script_Program.def_namespace
        do {
            pos = fullname.Find("::", true, start)
            if (pos < 0) {
                break
            }
            val namespaceName = fullname.Mid(start, pos - start).toString()
            def = GetDef(null, namespaceName, namespaceDef)
            if (null == def) {
                // couldn't find namespace
                return null
            }
            namespaceDef = def

            // skip past the ::
            start = pos + 2
        } while (def!!.Type() == Script_Program.ev_namespace)
        val funcName = fullname.Right(fullname.Length() - start).toString()
        def = GetDef(null, funcName, namespaceDef)
        if (null == def) {
            // couldn't find function
            return null
        }
        return if (def.Type() == Script_Program.ev_function && def.value.functionPtr!!.eventdef == null) {
            def.value.functionPtr
        } else null

        // is not a function, or is an eventdef
    }

    fun FindFunction(name: idStr): function_t? {
        return FindFunction(name.toString())
    }

    /*
     ================
     idProgram::FindFunction

     Searches for the specified object function in the currently loaded script.

     Returns 0 if function not found.
     Returns >0 if function found.
     ================
     */
    fun FindFunction(name: String, type: idTypeDef): function_t? {    // returns NULL if function not found
        var tdef: idVarDef
        var def: idVarDef?

        // look for the function
//            def = null;
        tdef = type.def!!
        while (tdef !== Script_Program.def_object) {
            def = GetDef(null, name, tdef)
            if (def != null) {
                return def.value.functionPtr
            }
            tdef = tdef.TypeDef()!!.SuperClass()!!.def!!
        }
        return null
    }

    fun AllocFunction(def: idVarDef): function_t {
        if (functions.size >= MAX_FUNCS) {
            throw idCompileError(Str.va("Exceeded maximum allowed number of functions (%d)", MAX_FUNCS))
        }

        // fill in the dfunction
        val func = function_t()
        func.eventdef = null
        func.def = def
        func.type = def.TypeDef()
        func.firstStatement = 0
        func.numStatements = 0
        func.parmTotal = 0
        func.locals = 0
        func.filenum = filenum
        func.parmSize.ensureCapacity(1)
        func.SetName(def.GlobalName())
        def.SetFunction(func)
        functions.add(func)
        return func
    }

    fun GetFunction(index: Int): function_t {
        return functions[index]
    }

    fun GetFunctionIndex(func: function_t): Int {
        return functions.indexOf(func)
    }

    fun SetEntity(name: String, ent: idEntity?) {
        val def: idVarDef?
        var defName = "$"
        defName += name
        def = GetDef(Script_Program.type_entity, defName, Script_Program.def_namespace)
        if (def != null && def.initialized != initialized_t.stackVariable) {
            // 0 is reserved for NULL entity
            if (null == ent) {
                def.value.setEntityNumberPtr(0)
            } else {
                def.value.setEntityNumberPtr(ent.entityNumber + 1)
            }
        }
    }

    fun AllocStatement(): statement_s {
        if (statements.size >= MAX_STATEMENTS) {
            throw idCompileError(Str.va("Exceeded maximum allowed number of statements (%d)", MAX_STATEMENTS))
        }
        val addedElement = statement_s()
        statements.add(addedElement)
        return addedElement
    }

    /*
    ================
    idProgram::GetStatement
    ================
    */
    fun GetStatement(index: Int): statement_s {
        return statements[index]
    }

    fun NumStatements(): Int {
        return statements.size
    }

    fun GetReturnedInteger(): Int {
        return returnDef!!.value.getIntPtr()
    }

    fun ReturnFloat(value: Float) {
        returnDef!!.value.setFloatPtr(value)
    }

    fun ReturnInteger(value: Int) {
        returnDef!!.value.setIntPtr(value)
    }

    fun ReturnVector(vec: idVec3) {
        returnDef!!.value.setVectorPtr(vec)
    }

    fun ReturnString(string: String) {
        returnStringDef!!.value.stringPtr =
            string //idStr.Copynz(returnStringDef.value.stringPtr, string, MAX_STRING_LEN);
    }

    fun ReturnEntity(ent: idEntity?) {
        if (ent != null) {
            returnDef!!.value.setEntityNumberPtr(ent.entityNumber + 1)
        } else {
            returnDef!!.value.setEntityNumberPtr(0)
        }
    }

    fun NumFilenames(): Int {
        return fileList.size
    }

    companion object {
        const val BYTES = Integer.BYTES * 20 //TODO:
    }

    //
    //
    init {
        FreeData()
    }
}