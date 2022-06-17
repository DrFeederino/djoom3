package neo.Game.Script

import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.TempDump
import neo.TempDump.SERiAL
import neo.framework.File_h.idFile
import neo.idlib.Lib.idException
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec3
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 *
 */
object Script_Program {
    const val MAX_STRING_LEN = 128
    const val MAX_FUNCS = 3072
    const val MAX_GLOBALS = 196608 // in bytes
    const val MAX_STATEMENTS = 81920 // statement_s - 18 bytes last I checked
    const val MAX_STRINGS = 1024
    const val ev_argsize = 13
    const val ev_boolean = 14
    const val ev_entity = 6

    //    public enum etype_t {
    const val ev_error = -1
    const val ev_field = 7
    const val ev_float = 4
    const val ev_function = 8
    const val ev_jumpoffset = 12
    const val ev_namespace = 2
    const val ev_object = 11
    const val ev_pointer = 10
    const val ev_scriptevent = 1
    const val ev_string = 3
    const val ev_vector = 5
    const val ev_virtualfunction = 9
    const val ev_void = 0
    val type_argsize: idTypeDef =
        idTypeDef(ev_argsize, "<argsize>", 4, null) // only used for function call and thread opcodes
    val def_argsize: idVarDef = idVarDef(type_argsize)
    val type_boolean: idTypeDef = idTypeDef(ev_boolean, "boolean", 4, null)
    val def_boolean: idVarDef = idVarDef(type_boolean)
    val type_entity: idTypeDef =
        idTypeDef(ev_entity, "entity", 4, null) // stored as entity number pointer
    val def_entity: idVarDef = idVarDef(type_entity)
    val type_field: idTypeDef = idTypeDef(ev_field, "field", 4, null)
    val def_field: idVarDef = idVarDef(type_field)
    val type_float: idTypeDef = idTypeDef(ev_float, "float", 4, null)
    val def_float: idVarDef = idVarDef(type_float)
    val type_jumpoffset: idTypeDef =
        idTypeDef(ev_jumpoffset, "<jump>", 4, null) // only used for jump opcodes
    val def_jumpoffset: idVarDef = idVarDef(type_jumpoffset) // only used for jump opcodes
    val type_namespace: idTypeDef = idTypeDef(ev_namespace, "namespace", 4, null)
    val def_namespace: idVarDef = idVarDef(type_namespace)
    val type_object: idTypeDef =
        idTypeDef(ev_object, "object", 4, null) // stored as entity number pointer
    val def_object: idVarDef = idVarDef(type_object)
    val type_pointer: idTypeDef = idTypeDef(ev_pointer, "pointer", 4, null)
    val def_pointer: idVarDef = idVarDef(type_pointer)
    val type_scriptevent: idTypeDef = idTypeDef(ev_scriptevent, "scriptevent", 4, null)
    val def_scriptevent: idVarDef = idVarDef(type_scriptevent)
    val type_string: idTypeDef = idTypeDef(ev_string, "string", MAX_STRING_LEN, null)
    val def_string: idVarDef = idVarDef(type_string)
    val type_vector: idTypeDef = idTypeDef(ev_vector, "vector", 12, null)
    val def_vector: idVarDef = idVarDef(type_vector)
    val type_virtualfunction: idTypeDef = idTypeDef(ev_virtualfunction, "virtual function", 4, null)
    val def_virtualfunction: idVarDef = idVarDef(type_virtualfunction)

    //    };
    /* **********************************************************************

     Variable and type defintions

     ***********************************************************************/
    // simple types.  function types are dynamically allocated
    val type_void: idTypeDef = idTypeDef(ev_void, "void", 0, null)
    val type_function: idTypeDef = idTypeDef(ev_function, "function", 4, type_void)
    val def_function: idVarDef = idVarDef(type_function)

    //
    val def_void: idVarDef = idVarDef(type_void)

    /* **********************************************************************

     function_t

     ***********************************************************************/
    class function_t : SERiAL {
        var def: idVarDef? = null

        //
        var eventdef: idEventDef? = null
        var filenum // source file defined in
                = 0
        var firstStatement = 0
        var locals // total ints of parms + locals
                = 0
        var numStatements //TODO:booleans?
                = 0
        val parmSize: ArrayList<Int> = ArrayList()
        var parmTotal = 0
        var type: idTypeDef? = null
        private val name: idStr = idStr()
        fun  /*size_t*/Allocated(): Int {
            return name.Allocated() + (parmSize.size * Integer.BYTES)
        }

        fun SetName(name: String) {
            this.name.set(name)
        }

        fun Name(): String {
            return name.toString()
        }

        fun Clear() {
            eventdef = null
            def = null
            type = null
            firstStatement = 0
            numStatements = 0
            parmTotal = 0
            locals = 0
            filenum = 0
            name.Clear()
            parmSize.clear()
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            try {
                name.Read(buffer)
                buffer.getInt() //skip
                buffer.getInt() //skip
                buffer.getInt() //skip
                firstStatement = buffer.getInt()
                numStatements = buffer.getInt()
                parmTotal = buffer.getInt()
                locals = buffer.getInt()
                filenum = buffer.getInt()
            } catch (ignore: BufferUnderflowException) {
            }
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            val SIZE: Int = (idStr.Companion.SIZE
                    + TempDump.CPP_class.Pointer.SIZE //eventdef
                    + TempDump.CPP_class.Pointer.SIZE //def
                    + TempDump.CPP_class.Pointer.SIZE //type
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + idList.Companion.SIZE)
            val BYTES = SIZE / java.lang.Byte.SIZE
        }

        //
        //
        init {
            Clear()
        }
    }

    class  /*union*/ eval_s {
        //TODO:unionize?
        val _float: Float
        val _int: Int
        val entity: Int
        val function: Array<function_t>?
        val stringPtr: Array<String>?
        var vector: FloatArray = FloatArray(3)

        constructor(string: String) {
            stringPtr = arrayOf(string)
            _float = Float.NaN
            function = null
            entity = Int.MIN_VALUE
            _int = entity
        }

        constructor(_float: Float) {
            stringPtr = null
            this._float = _float
            function = null
            entity = Int.MIN_VALUE
            _int = entity
        }

        constructor(vector: FloatArray) {
            stringPtr = null
            _float = Float.NaN
            this.vector = vector
            function = null
            entity = Int.MIN_VALUE
            _int = entity
        }

        constructor(func: function_t) {
            stringPtr = null
            _float = Float.NaN
            function = arrayOf(func)
            entity = Int.MIN_VALUE
            _int = entity
        }

        constructor(`val`: Int) {
            stringPtr = null
            _float = Float.NaN
            function = null
            entity = `val`
            _int = entity
        }
    }

    /* **********************************************************************

     idTypeDef

     Contains type information for variables and functions.

     ***********************************************************************/
    class idTypeDef {
        //
        var def // a def that points to this type
                : idVarDef? = null

        //
        // function types are more complex
        private var auxType // return type
                : idTypeDef? = null
        private val functions: ArrayList<function_t> = ArrayList()
        private var name: idStr = idStr()
        private var parmNames: ArrayList<idStr> = ArrayList()
        private val parmTypes: ArrayList<idTypeDef> = ArrayList()
        var size = 0
        private var   /*etype_t*/type = 0

        //
        //
        constructor(other: idTypeDef) {
            oSet(other)
        }

        constructor(   /*etype_t*/etype: Int, edef: idVarDef?, ename: String, esize: Int, aux: idTypeDef?) {
            name = idStr(ename)
            type = etype
            def = edef
            size = esize
            auxType = aux
            parmTypes.ensureCapacity(1)
            parmNames.ensureCapacity(1)
            functions.ensureCapacity(1)
        }

        constructor(   /*etype_t*/etype: Int, ename: String, esize: Int, aux: idTypeDef?) {
            name = idStr(ename)
            type = etype
            def = idVarDef(this)
            size = esize
            auxType = aux
            parmTypes.ensureCapacity(1)
            parmNames.ensureCapacity(1)
            functions.ensureCapacity(1)
        }

        fun oSet(other: idTypeDef) {
            type = other.type
            def = other.def
            name = other.name
            size = other.size
            auxType = other.auxType
            parmTypes.addAll(other.parmTypes)
            parmNames = other.parmNames
            functions.addAll(other.functions)
        }

        fun  /*size_t*/Allocated(): Int {
            var   /*size_t*/memsize: Int
            var i: Int
            memsize =
                name.Allocated() + parmTypes.size * Integer.BYTES + parmNames.size + functions.size * Integer.BYTES
            i = 0
            while (i < parmTypes.size) {
                memsize += parmNames[i].Allocated()
                i++
            }
            return memsize
        }

        /*
         ================
         idTypeDef::Inherits

         Returns true if basetype is an ancestor of this type.
         ================
         */
        fun Inherits(basetype: idTypeDef): Boolean {
            var superType: idTypeDef?
            if (type != ev_object) {
                return false
            }
            if (this == basetype) {
                return true
            }
            superType = auxType
            while (superType != null) {
                if (superType == basetype) {
                    return true
                }
                superType = superType.auxType
            }
            return false
        }

        /*
         ================
         idTypeDef::MatchesType

         Returns true if both types' base types and parameters match
         ================
         */
        fun MatchesType(matchtype: idTypeDef): Boolean {
            var i: Int
            if (this == matchtype) {
                return true
            }
            if (type != matchtype.type || auxType != matchtype.auxType) {
                return false
            }
            if (parmTypes.size != matchtype.parmTypes.size) {
                return false
            }
            i = 0
            while (i < matchtype.parmTypes.size) {
                if (parmTypes[i] != matchtype.parmTypes[i]) {
                    return false
                }
                i++
            }
            return true
        }

        /*
         ================
         idTypeDef::MatchesVirtualFunction

         Returns true if both functions' base types and parameters match
         ================
         */
        fun MatchesVirtualFunction(matchfunc: idTypeDef): Boolean {
            var i: Int
            if (this == matchfunc) {
                return true
            }
            if (type != matchfunc.type || auxType != matchfunc.auxType) {
                return false
            }
            if (parmTypes.size != matchfunc.parmTypes.size) {
                return false
            }
            if (parmTypes.size > 0) {
                if (!parmTypes[0].Inherits(matchfunc.parmTypes[0])) {
                    return false
                }
            }
            i = 1
            while (i < matchfunc.parmTypes.size) {
                if (parmTypes[i] != matchfunc.parmTypes[i]) {
                    return false
                }
                i++
            }
            return true
        }

        /*
         ================
         idTypeDef::AddFunctionParm

         Adds a new parameter for a function type.
         ================
         */
        fun AddFunctionParm(parmtype: idTypeDef, name: String) {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::AddFunctionParm : tried to add parameter on non-function type")
            }
            parmTypes.add(parmtype)
            val parmName = parmNames.addEmptyStr()
            parmName.set(name)
        }

        /*
         ================
         idTypeDef::AddField

         Adds a new field to an object type.
         ================
         */
        fun AddField(fieldtype: idTypeDef, name: String) {
            if (type != ev_object) {
                throw idCompileError("idTypeDef::AddField : tried to add field to non-object type")
            }
            parmTypes.add(fieldtype)
            val parmName = parmNames.addEmptyStr()
            parmName.set(name)
            size += if (fieldtype.FieldType()!!.Inherits(type_object)) {
                type_object.Size()
            } else {
                fieldtype.FieldType()!!.Size()
            }
        }

        fun ArrayList<idStr>.addEmptyStr(): idStr {
            val idStr = idStr()
            add(idStr)
            return idStr
        }

        fun SetName(newname: String) {
            name.set(newname)
        }

        fun Name(): String {
            return name.toString()
        }

        fun  /*etype_t*/Type(): Int {
            return type
        }

        fun Size(): Int {
            return size
        }

        /*
         ================
         idTypeDef::SuperClass

         If type is an object, then returns the object's superclass
         ================
         */
        fun SuperClass(): idTypeDef? {
            if (type != ev_object) {
                throw idCompileError("idTypeDef::SuperClass : tried to get superclass of a non-object type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::ReturnType

         If type is a function, then returns the function's return type
         ================
         */
        fun ReturnType(): idTypeDef? {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::ReturnType: tried to get return type on non-function type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetReturnType

         If type is a function, then sets the function's return type
         ================
         */
        fun SetReturnType(returntype: idTypeDef?) {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::SetReturnType: tried to set return type on non-function type")
            }
            auxType = returntype
        }

        /*
         ================
         idTypeDef::FieldType

         If type is a field, then returns it's type
         ================
         */
        fun FieldType(): idTypeDef? {
            if (type != ev_field) {
                throw idCompileError("idTypeDef::FieldType: tried to get field type on non-field type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetFieldType

         If type is a field, then sets the function's return type
         ================
         */
        fun SetFieldType(fieldtype: idTypeDef?) {
            if (type != ev_field) {
                throw idCompileError("idTypeDef::SetFieldType: tried to set return type on non-function type")
            }
            auxType = fieldtype
        }

        /*
         ================
         idTypeDef::PointerType

         If type is a pointer, then returns the type it points to
         ================
         */
        fun PointerType(): idTypeDef? {
            if (type != ev_pointer) {
                throw idCompileError("idTypeDef::PointerType: tried to get pointer type on non-pointer")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetPointerType

         If type is a pointer, then sets the pointer's type
         ================
         */
        fun SetPointerType(pointertype: idTypeDef?) {
            if (type != ev_pointer) {
                throw idCompileError("idTypeDef::SetPointerType: tried to set type on non-pointer")
            }
            auxType = pointertype
        }

        fun NumParameters(): Int {
            return parmTypes.size
        }

        fun GetParmType(parmNumber: Int): idTypeDef {
            assert(parmNumber >= 0)
            assert(parmNumber < parmTypes.size)
            return parmTypes[parmNumber]
        }

        fun GetParmName(parmNumber: Int): String {
            assert(parmNumber >= 0)
            assert(parmNumber < parmTypes.size)
            return parmNames[parmNumber].toString()
        }

        fun NumFunctions(): Int {
            return functions.size
        }

        fun GetFunctionNumber(func: function_t?): Int {
            var i: Int
            i = 0
            while (i < functions.size) {
                if (functions[i] == func) {
                    return i
                }
                i++
            }
            return -1
        }

        fun GetFunction(funcNumber: Int): function_t? {
            assert(funcNumber >= 0)
            assert(funcNumber < functions.size)
            return functions[funcNumber]
        }

        fun AddFunction(func: function_t) {
            var i: Int
            i = 0
            while (i < functions.size) {
                if (functions[i].def!!.Name() == func.def!!.Name()) {
                    if (func.def!!.TypeDef()!!.MatchesVirtualFunction(functions[i].def!!.TypeDef()!!)) {
                        functions[i] = func
                        return
                    }
                }
                i++
            }
            functions.add(func)
        }

        companion object {
            const val BYTES = Integer.BYTES * 8 //TODO:<-
        }
    }

    /* **********************************************************************

     idScriptObject

     In-game representation of objects in scripts.  Use the idScriptVariable template
     (below) to access variables.

     ***********************************************************************/
    class idScriptObject : SERiAL {
        //
        var data: ByteBuffer = ByteBuffer.allocate(1)
        var offset = 0
        private var type: idTypeDef

        // ~idScriptObject();
        fun Save(savefile: idSaveGame) {            // archives object for save game file
            val   /*size_t*/size: Int
            if (type == type_object && data.capacity() > 1) {
                // Write empty string for uninitialized object
                savefile.WriteString("")
            } else {
                savefile.WriteString(type.Name())
                size = type.Size()
                savefile.WriteInt(size)
                savefile.Write(data, size)
            }
        }

        fun Restore(savefile: idRestoreGame) {            // unarchives object from save game file
            val typeName = idStr()
            val size = CInt()
            savefile.ReadString(typeName)

            // Empty string signals uninitialized object
            if (typeName.Length() == 0) {
                return
            }
            if (!SetType(typeName.toString())) {
                savefile.Error("idScriptObject::Restore: failed to restore object of type '%s'.", typeName.toString())
            }
            savefile.ReadInt(size)
            if (size._val != type.Size()) {
                savefile.Error(
                    "idScriptObject::Restore: size of object '%s' doesn't match size in save game.",
                    typeName
                )
            }
            savefile.Read(data, size._val)
        }

        fun Free() {
//            if (data != null) {
//                Mem_Free(data);
//            }
            data = ByteBuffer.allocate(1)
            type = type_object
        }

        /*
         ============
         idScriptObject::SetType

         Allocates an object and initializes memory.
         ============
         */
        fun SetType(typeName: String): Boolean {
            val   /*size_t*/size: Int
            val newType: idTypeDef?

            // lookup the type
            newType = Game_local.gameLocal.program.FindType(typeName)

            // only allocate memory if the object type changes
            if (newType != type) {
                Free()
                if (null == newType) {
                    Game_local.gameLocal.Warning("idScriptObject::SetType: Unknown type '%s'", typeName)
                    return false
                }
                if (!newType.Inherits(type_object)) {
                    Game_local.gameLocal.Warning(
                        "idScriptObject::SetType: Can't create object of type '%s'.  Must be an object type.",
                        newType.Name()
                    )
                    return false
                }

                // set the type
                type = newType

                // allocate the memory
                size = type.Size()
                data = ByteBuffer.allocate(size) // Mem_Alloc(size);
            }

            // init object memory
            ClearObject()
            return true
        }

        /*
         ============
         idScriptObject::ClearObject

         Resets the memory for the script object without changing its type.
         ============
         */
        fun ClearObject() {
            val   /*size_t*/size: Int
            if (type != type_object) {
                // init object memory
                size = type.Size()
                //		memset( data, 0, size );
                data.clear()
            }
        }

        fun HasObject(): Boolean {
            return type != type_object
        }

        fun GetTypeDef(): idTypeDef? {
            return type
        }

        fun GetTypeName(): String {
            return type.Name()
        }

        fun GetConstructor(): function_t? {
            val func: function_t?
            func = GetFunction("init")
            return func
        }

        fun GetDestructor(): function_t? {
            val func: function_t?
            func = GetFunction("destroy")
            return func
        }

        fun GetFunction(name: String): function_t? {
            val func: function_t?
            if (type == type_object) {
                return null
            }
            func = Game_local.gameLocal.program.FindFunction(name, type)
            return func
        }

        fun GetVariable(name: String,    /*etype_t*/etype: Int): ByteBuffer? {
            var i: Int
            var pos: Int
            var t: idTypeDef?
            var parm: idTypeDef?
            if (type == type_object) {
                return null
            }
            t = type
            do {
                pos = if (t!!.SuperClass() != type_object) {
                    t.SuperClass()!!.Size()
                } else {
                    0
                }
                i = 0
                while (i < t.NumParameters()) {
                    parm = t.GetParmType(i)
                    if (t.GetParmName(i) == name) {
                        return if (etype != parm.FieldType()!!.Type()) {
                            null
                        } else data.position(pos).slice()
                    }
                    pos += if (parm.FieldType()!!.Inherits(type_object)) {
                        type_object.Size()
                    } else {
                        parm.FieldType()!!.Size()
                    }
                    i++
                }
                t = t.SuperClass()
            } while (t != null && t != type_object)
            return null
        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        //
        //
        init {
            type = type_object
        }
    }

    /* **********************************************************************

     idScriptVariable

     Helper template that handles looking up script variables stored in objects.
     If the specified variable doesn't exist, or is the wrong data type, idScriptVariable
     will cause an error.

     ***********************************************************************/
    open class idScriptVariable<type, returnType>     //
    //
    //        public idScriptVariable() {
    //            etype = null;
    //            data = null;
    //        }
    //        
        (   /*etype_t*//*etype_t*/protected val etype: Int) {
        private var data: ByteBuffer? = null
        fun IsLinked(): Boolean {
            return data != null
        }

        fun Unlink() {
            data = null
        }

        fun LinkTo(obj: idScriptObject, name: String) {
            data = obj.GetVariable(name, etype) //TODO:convert bytes to type
            if (null == data) {
                idGameLocal.Companion.gameError("Missing '%s' field in script object '%s'", name, obj.GetTypeName())
            }
        }

        fun oSet(value: returnType): idScriptVariable<*, *> {
            // check if we attempt to access the object before it's been linked
            assert(data != null)

            // make sure we don't crash if we don't have a pointer
            if (data != null) {
                val pos = data!!.position()
                when (etype) {
                    ev_boolean -> data!!.put(TempDump.btoi(value as Boolean).toByte())
                    ev_float -> data!!.putFloat(value as Float)
                }
                data!!.position(pos)
            }
            return this
        }

        fun underscore(): returnType? {
            // check if we attempt to access the object before it's been linked
            assert(data != null)

            // make sure we don't crash if we don't have a pointer
            return if (data != null) {
                val pos = data!!.position()
                when (etype) {
                    ev_boolean -> TempDump.itob(data!!.get(pos).toInt()) as returnType
                    ev_float -> data!!.getFloat(pos) as returnType
                    else -> null
                }
            } else {
                // reasonably safe value
                null
            }
        }

        fun underscore(bla: returnType) {
            oSet(bla)
        }
    }

    /* **********************************************************************

     Script object variable access template instantiations

     These objects will automatically handle looking up of the current value
     of a variable in a script object.  They can be stored as part of a class
     for up-to-date values of the variable, or can be used in functions to
     sample the data for non-dynamic values.

     ***********************************************************************/
    class idScriptBool : idScriptVariable<Boolean, Boolean>(ev_boolean)
    class idScriptFloat : idScriptVariable<Float, Float>(ev_float)
    private class idScriptInt : idScriptVariable<Float, Int>(ev_float)
    private class idScriptVector : idScriptVariable<idVec3, idVec3>(ev_vector)
    private class idScriptString : idScriptVariable<idStr, String>(ev_string)

    /* **********************************************************************

     idCompileError

     Causes the compiler to exit out of compiling the current function and
     display an error message with line and file info.

     ***********************************************************************/
    class idCompileError(text: String) : idException(text)

    /* **********************************************************************

     idVarDef

     Define the name, type, and location of variables, functions, and objects
     defined in script.

     ***********************************************************************/
    class  /*union*/ varEval_s {
        //        final         int[]          intPtr;
        //        final         ByteBuffer     bytePtr;
        //        private int virtualFunction;
        //        private int jumpOffset;
        //        private int stackOffset;		// offset in stack for local variables
        //        private int argSize;
        var evalPtr: varEval_s? = null
        var functionPtr: function_t? = null
        var objectPtrPtr: idScriptObject? = null
        var stringPtr: String? = null

        //        final         float[]        floatPtr;
        val vectorPtr: idVec3 = idVec3()
        private var offset = 0

        //        private int ptrOffset;
        private var primitive = ByteBuffer.allocate(java.lang.Float.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN)
        fun getVirtualFunction(): Int {
            return getPrimitive()
        }

        fun setVirtualFunction(`val`: Int) {
            setPrimitive(`val`)
        }

        fun getJumpOffset(): Int {
            return getPrimitive()
        }

        fun setJumpOffset(`val`: Int) {
            setPrimitive(`val`)
        }

        fun getStackOffset(): Int {
            return getPrimitive()
        }

        fun setStackOffset(`val`: Int) {
            setPrimitive(`val`)
        }

        fun getArgSize(): Int {
            return getPrimitive()
        }

        fun setArgSize(`val`: Int) {
            setPrimitive(`val`)
        }

        fun getPtrOffset(): Int {
            return getPrimitive()
        }

        fun setPtrOffset(`val`: Int) {
            setPrimitive(`val`)
        }

        private fun getPrimitive(): Int {
            return primitive.getInt(0)
        }

        private fun setPrimitive(`val`: Int) {
            primitive.putInt(0, `val`)
        }

        fun setIntPtr(`val`: ByteArray, offset: Int) {
            setBytePtr(ByteBuffer.wrap(`val`), offset)
        }

        fun getidVec3Ptr(): idVec3 {
            vectorPtr[0] = primitive.getFloat(0)
            vectorPtr[1] = primitive.getFloat(4)
            vectorPtr[2] = primitive.getFloat(8)
            return vectorPtr
        }

        fun setVectorPtr(vector: idVec3) {
            setVectorPtr(vector.ToFloatPtr())
        }

        fun setVectorPtr(vector: FloatArray) {
            vectorPtr.set(idVec3(vector))
            primitive.putFloat(0, vector[0])
            primitive.putFloat(4, vector[1])
            primitive.putFloat(8, vector[2])
        }

        //        void bytePtr(ByteBuffer data, int ptrOffset) {
        //            throw new UnsupportedOperationException("Not supported yet.");
        //        }
        fun getIntPtr(): Int {
            return primitive.getInt(0)
        }

        fun setIntPtr(`val`: Int) {
            setPrimitive(`val`)
        }

        fun getFloatPtr(): Float {
            return primitive.getFloat(0)
        }

        fun setFloatPtr(`val`: Float) {
            primitive.putFloat(0, `val`)
        }

        fun getEntityNumberPtr(): Int {
            return getIntPtr()
        }

        fun setEntityNumberPtr(`val`: Int) {
            setPrimitive(`val`)
        }

        fun setBytePtr(bytes: ByteBuffer, offset: Int) {
            this.offset = offset
            primitive =
                bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset).slice().order(ByteOrder.LITTLE_ENDIAN)
        }

        fun setBytePtr(bytes: ByteArray, offset: Int) {
            setBytePtr(ByteBuffer.wrap(bytes), offset)
        }

        fun setStringPtr(data: ByteBuffer, offset: Int) {
            stringPtr = TempDump.btos(data.array(), offset)
        }

        fun setString(string: String) { //TODO:clean up all these weird string pointers
            primitive.put(string.toByteArray()).rewind()
        }

        fun setEvalPtr(entityNumberIndex: Int) {
            setEntityNumberPtr(entityNumberIndex)
        }

        companion object {
            const val BYTES = java.lang.Float.BYTES
        }
    }

    class idVarDef @JvmOverloads constructor(  //
        private var typeDef: idTypeDef? = null /*= NULL*/
    ) {
        //
        var initialized: initialized_t
        var num = 0
        var numUsers // number of users if this is a constant
                = 0
        var scope // function, namespace, or object the var was defined in
                : idVarDef? = null

        //
        lateinit var value: varEval_s
        var name // name of this var
                : idVarDefName = idVarDefName()
        var next // next var with the same name
                : idVarDef?

        // ~idVarDef();
        fun close() {
            name?.RemoveDef(this)
        }

        fun Name(): String {
            return name.Name()
        }

        fun GlobalName(): String {
            return if (scope != def_namespace) {
                Str.va("%s::%s", scope!!.GlobalName(), name.Name())
            } else {
                name.Name()
            }
        }

        fun SetTypeDef(_type: idTypeDef?) {
            typeDef = _type
        }

        fun TypeDef(): idTypeDef? {
            return typeDef
        }

        fun  /*etype_t*/Type(): Int {
            return if (typeDef != null) typeDef!!.Type() else ev_void
        }

        fun DepthOfScope(otherScope: idVarDef?): Int {
            var def: idVarDef?
            var depth: Int
            depth = 1
            def = otherScope
            while (def != null) {
                if (def == scope) {
                    return depth
                }
                depth++
                def = def.scope
            }
            return 0
        }

        fun SetFunction(func: function_t) {
            assert(typeDef != null)
            initialized = initialized_t.initializedConstant
            assert(typeDef!!.Type() == ev_function)
            value = varEval_s()
            value.functionPtr = func
        }

        fun SetObject(`object`: idScriptObject?) {
            assert(typeDef != null)
            initialized = initialized
            assert(typeDef!!.Inherits(type_object))
            value = varEval_s()
            value.objectPtrPtr = `object`
        }

        fun SetValue(_value: eval_s, constant: Boolean) {
            assert(typeDef != null)
            initialized = if (constant) {
                initialized_t.initializedConstant
            } else {
                initialized_t.initializedVariable
            }
            when (typeDef!!.Type()) {
                ev_pointer, ev_boolean, ev_field -> value.setIntPtr(_value._int)
                ev_jumpoffset -> value.setJumpOffset(_value._int)
                ev_argsize -> value.setArgSize(_value._int)
                ev_entity -> value.setEntityNumberPtr(_value.entity)
                ev_string -> value.stringPtr =
                    _value.stringPtr!![0] //idStr.Copynz(value.stringPtr, _value.stringPtr, MAX_STRING_LEN);
                ev_float -> value.setFloatPtr(_value._float)
                ev_vector -> value.setVectorPtr(_value.vector)
                ev_function -> value.functionPtr = _value.function!![0]
                ev_virtualfunction -> value.setVirtualFunction(_value._int)
                ev_object -> value.setEntityNumberPtr(_value.entity)
                else -> throw idCompileError(Str.va("weird type on '%s'", Name()))
            }
        }

        fun SetString(string: String, constant: Boolean) {
            initialized = if (constant) {
                initialized_t.initializedConstant
            } else {
                initialized_t.initializedVariable
            }
            assert(typeDef != null && typeDef!!.Type() == ev_string)
            value.stringPtr = string
        }

        fun Next(): idVarDef? {
            return next
        } // next var def with same name

        fun PrintInfo(file: idFile, instructionPointer: Int) {
            val jumpst: statement_s
            val jumpto: Int
            val   /*etype_t*/etype: Int
            if (initialized == initialized_t.initializedConstant) {
                file.Printf("const ")
            }
            etype = typeDef!!.Type()
            when (etype) {
                ev_jumpoffset -> {
                    jumpto = instructionPointer + value.getJumpOffset()
                    jumpst = Game_local.gameLocal.program.GetStatement(jumpto)
                    file.Printf(
                        "address %d [%s(%d)]",
                        jumpto,
                        Game_local.gameLocal.program.GetFilename(jumpst.file),
                        jumpst.linenumber
                    )
                }
                ev_function -> if (value.functionPtr!!.eventdef != null) {
                    file.Printf("event %s", GlobalName())
                } else {
                    file.Printf("function %s", GlobalName())
                }
                ev_field -> file.Printf("field %d", value.getPtrOffset())
                ev_argsize -> file.Printf("args %d", value.getArgSize())
                else -> {
                    file.Printf("%s ", typeDef!!.Name())
                    if (initialized == initialized_t.initializedConstant) {
                        when (etype) {
                            ev_string -> {
                                file.Printf("\"")
                                for (ch in value.stringPtr!!.toCharArray()) {
                                    if (idStr.Companion.CharIsPrintable(ch.code)) {
                                        file.Printf("%c", ch)
                                    } else if (ch == '\n') {
                                        file.Printf("\\n")
                                    } else {
                                        file.Printf("\\x%.2x", ch.code)
                                    }
                                }
                                file.Printf("\"")
                            }
                            ev_vector -> file.Printf("'%s'", value.getidVec3Ptr().ToString())
                            ev_float -> file.Printf("%f", value.getFloatPtr())
                            ev_virtualfunction -> file.Printf("vtable[ %d ]", value.getVirtualFunction())
                            else -> file.Printf("%d", value.getIntPtr())
                        }
                    } else if (initialized == initialized_t.stackVariable) {
                        file.Printf("stack[%d]", value.getStackOffset())
                    } else {
                        file.Printf("global[%d]", num)
                    }
                }
            }
        }

        enum class initialized_t {
            uninitialized, initializedVariable, initializedConstant, stackVariable
        }

        companion object {
            // friend class idVarDefName;
            const val BYTES = (Integer.BYTES
                    + varEval_s.BYTES
                    + Integer.BYTES
                    + Integer.BYTES)
        }

        //
        //
        init {
            initialized = initialized_t.uninitialized
            //	memset( &value, 0, sizeof( value ) );
            name = idVarDefName()
            next = null
        }
    }

    /* **********************************************************************

     idVarDefName

     ***********************************************************************/
    class idVarDefName {
        private var defs: idVarDef?
        private val name: idStr = idStr()

        //
        //
        constructor() {
            defs = null
        }

        constructor(n: String) {
            name.set(n)
            defs = null
        }

        fun Name(): String {
            return name.toString()
        }

        fun GetDefs(): idVarDef? {
            return defs
        }

        fun AddDef(def: idVarDef) {
            assert(def.next == null)
            def.name = this
            def.next = defs
            defs = def
        }

        fun RemoveDef(def: idVarDef) {
            if (defs == def) {
                defs = def.next
            } else {
                var d = defs!!
                while (d.next != null) {
                    if (d.next == def) {
                        d.next = def.next
                        break
                    }
                    d = d.next!!
                }
            }
            def.next = null
            def.name = idVarDefName()
        }
    }

    class statement_s {
        var a: idVarDef? = null
        var b: idVarDef? = null
        var c: idVarDef? = null
        var file = 0
        var linenumber = 0
        var   /*unsigned short*/op = 0
    }

    init {
        type_void.def = def_void
        type_scriptevent.def = def_scriptevent
        type_namespace.def = def_namespace
        type_string.def = def_string
        type_float.def = def_float
        type_vector.def = def_vector
        type_entity.def = def_entity
        type_field.def = def_field
        type_function.def = def_function
        type_virtualfunction.def = def_virtualfunction
        type_pointer.def = def_pointer
        type_object.def = def_object
        type_jumpoffset.def = def_jumpoffset
        type_argsize.def = def_argsize
        type_boolean.def = def_boolean
    }
}