package neo.Game.GameSys

import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.trace_s
import neo.Game.*
import neo.Game.AI.AI
import neo.Game.AI.AI_Events
import neo.Game.AI.AI_Vagary
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Thread
import neo.Game.Target
import neo.TempDump
import neo.TempDump.SERiAL
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.math.Vector.idVec3
import java.nio.ByteBuffer

/**
 *
 */
object Event {
    val D_EVENT_ENTITY: Char = 'e'
    val D_EVENT_ENTITY_NULL: Char = 'E' // event can handle NULL entity pointers
    val D_EVENT_FLOAT: Char = 'f'
    val D_EVENT_INTEGER: Char = 'd'
    const val D_EVENT_MAXARGS =
        8 // if changed, enable the CREATE_EVENT_CODE define in Event.cpp to generate switch statement for idClass::ProcessEventArgPtr.
    val D_EVENT_STRING: Char = 's'
    val D_EVENT_TRACE: Char = 't'
    val D_EVENT_VECTOR: Char = 'v'

    //                                                          // running the game will then generate c:\doom\base\events.txt, the contents of which should be copied into the switch statement.
    //
    val D_EVENT_VOID: Char = 0.toChar()

    //
    const val MAX_EVENTS = 4096

    //
    const val MAX_EVENTSPERFRAME = 4096
    var EventPool: Array<idEvent> = Array(MAX_EVENTS) { idEvent() }
    var EventQueue: idLinkList<idEvent> = idLinkList()

    //
    var FreeEvents: idLinkList<idEvent> = idLinkList()

    //
    var eventError = false
    var eventErrorMsg: String? = null

    /* **********************************************************************

     idEventDef

     ***********************************************************************/
    class idEventDef(command: String, formatSpec: String? = null /*= NULL*/, returnType: Char /*= 0*/) {
        private var argOffset: IntArray = IntArray(D_EVENT_MAXARGS)
        private var   /*size_t*/argsize: Int
        private var eventnum: Int
        private val formatspec: String?
        private val   /*unsigned int*/formatspecIndex: Long
        private val name: String

        //private val next: idEventDef = null
        private val numargs: Int
        private val returnType: Int

        //
        //
        @JvmOverloads
        constructor(command: String, formatspec: String? = null /*= NULL*/) : this(command, formatspec, 0.toChar())

        fun GetName(): String {
            return name
        }

        fun GetArgFormat(): String? {
            return formatspec
        }

        fun  /*unsigned int*/GetFormatspecIndex(): Long {
            return formatspecIndex
        }

        fun GetReturnType(): Char {
            return returnType.toChar()
        }

        fun GetEventNum(): Int {
            return eventnum
        }

        fun GetNumArgs(): Int {
            return numargs
        }

        fun  /*size_t*/GetArgSize(): Int {
            return argsize
        }

        fun GetArgOffset(arg: Int): Int {
            assert(arg >= 0 && arg < D_EVENT_MAXARGS)
            return argOffset[arg]
        }

        override fun hashCode(): Int {
            return eventnum
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val that = o as idEventDef
            return eventnum == that.eventnum
        }

        companion object {
            //
            private val eventDefList: ArrayList<idEventDef> = ArrayList(MAX_EVENTS)
            private var numEventDefs = 0
            fun NumEventCommands(): Int {
                return numEventDefs
            }

            fun GetEventCommand(eventnum: Int): idEventDef {
                return eventDefList[eventnum]
            }

            fun FindEvent(name: String): idEventDef? {
                var ev: idEventDef
                val num: Int
                var i: Int
                assert(name != null)
                num = numEventDefs
                i = 0
                while (i < num) {
                    ev = eventDefList[i]
                    if (name == ev.name) {
                        return ev
                    }
                    i++
                }
                return null
            }
        }

        init {
            var formatSpec = formatSpec
            var ev: idEventDef
            var i: Int
            var   /*unsigned int*/bits: Long
            assert(command != null)
            assert(!idEvent.initialized)

            // Allow NULL to indicate no args, but always store it as ""
            // so we don't have to check for it.
            if (null == formatSpec) {
                formatSpec = ""
            }
            name = command
            formatspec = formatSpec
            this.returnType = returnType.code
            numargs = formatSpec.length
            assert(numargs <= D_EVENT_MAXARGS)
            if (numargs > D_EVENT_MAXARGS) {
                eventError = true
                eventErrorMsg = String.format("idEventDef::idEventDef : Too many args for '%s' event.", name)
            }

            // make sure the format for the args is valid, calculate the formatspecindex, and the offsets for each arg
            bits = 0
            argsize = 0
            argOffset = IntArray(D_EVENT_MAXARGS) //memset( argOffset, 0, sizeof( argOffset ) );
            i = 0
            while (i < numargs) {
                argOffset[i] = argsize
                when (formatSpec[i]) {
                    D_EVENT_FLOAT -> {
                        bits = bits or ((1 shl i).toLong())
                        argsize += java.lang.Float.SIZE / java.lang.Byte.SIZE
                    }
                    D_EVENT_INTEGER -> argsize += Integer.SIZE / java.lang.Byte.SIZE
                    D_EVENT_VECTOR -> argsize += idVec3.BYTES
                    D_EVENT_STRING -> argsize += Script_Program.MAX_STRING_LEN
                    D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> argsize += TempDump.CPP_class.Pointer.SIZE / java.lang.Byte.SIZE
                    D_EVENT_TRACE -> {}
                    else -> {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEventDef::idEventDef : Invalid arg format '%s' string for '%s' event.",
                            formatSpec,
                            name
                        )
                    }
                }
                i++
            }

            // calculate the formatspecindex
            formatspecIndex = (1 shl numargs + D_EVENT_MAXARGS or bits.toInt()).toLong()

            // go through the list of defined events and check for duplicates
            // and mismatched format strings
            eventnum = numEventDefs
            i = 0
            while (i < eventnum) {
                ev = eventDefList[i]
                if (command == ev.name) {
                    if (formatSpec != ev.formatspec) {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing format strings ('%s'!='%s').",
                            command, formatSpec, ev.formatspec
                        )
                    }
                    if (ev.returnType != returnType.code) {
                        eventError = true
                        eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing return types ('%c'!='%c').",
                            command, returnType, ev.returnType
                        )
                    }
                    // Don't bother putting the duplicate event in list.
                    eventnum = ev.eventnum
                }
                i++
            }
            ev = this
            if (numEventDefs >= MAX_EVENTS) {
                eventError = true
                eventErrorMsg = String.format("numEventDefs >= MAX_EVENTS")
            }
            eventDefList.add(numEventDefs, ev)
            numEventDefs++
        }
    }

    /* **********************************************************************

     idEvent

     ***********************************************************************/
    class idEvent {
        private var data: ArrayList<idEventArg<*>> = ArrayList()

        //
        private val eventNode: idLinkList<idEvent> = idLinkList()
        private lateinit var eventdef: idEventDef
        private lateinit var `object`: idClass
        private var time = 0
        private lateinit var typeinfo: java.lang.Class<*>
        fun Free() {
//            if (data != null) {
//                eventDataAllocator.Free(data);
            data.clear()
            //            }
            //eventdef
            time = 0
            //`object` = null
            //typeinfo = null
            eventNode.SetOwner(this)
            eventNode.AddToEnd(FreeEvents)
        }

        fun Schedule(obj: idClass, type: java.lang.Class<*>, time: Int) {
            var event: idEvent?
            assert(initialized)
            if (!initialized) {
                return
            }
            `object` = obj
            typeinfo = type

            // wraps after 24 days...like I care. ;)
            this.time = Game_local.gameLocal.time + time
            eventNode.Remove()
            event = EventQueue.Next()
            while (event != null && this.time >= event.time) {
                event = event.eventNode.Next()
            }
            if (event != null) {
                eventNode.InsertBefore(event.eventNode)
            } else {
                eventNode.AddToEnd(EventQueue)
            }
        }

        fun GetData(): ArrayList<*> {
            return data
        }

        companion object {
            //
            //        private static idDynamicBlockAlloc<Byte> eventDataAllocator = new idDynamicBlockAlloc(16 * 1024, 256);
            //
            var initialized = false

            //
            //
            // ~idEvent();
            fun Alloc(evdef: idEventDef, numargs: Int, vararg args: idEventArg<*>?): idEvent {
                val ev: idEvent
                val   /*size_t*/size: Int
                val format: String?
                //            idEventArg arg;
                var i: Int
                var materialName: String
                if (FreeEvents.IsListEmpty()) {
                    idGameLocal.Error("idEvent::Alloc : No more free events")
                }
                ev = FreeEvents.Next()!!
                ev.eventNode.Remove()
                ev.eventdef = evdef
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Error(
                        "idEvent::Alloc : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }
                size = evdef.GetArgSize()
                if (size != 0) {
//		ev.data = eventDataAllocator.Alloc( size );
//		memset( ev.data, 0, size );
                    ev.data.clear()
                    args.forEach { arg -> ev.data.add(arg!!) }
                } else {
                    ev.data.clear()
                }
                format = evdef.GetArgFormat()
                //            for (i = 0; i < numargs; i++) {
//                for (idEventArg arg : args) {
////                arg = va_arg(args, idEventArg);
//                    if (format.charAt(i) != arg.type) {
//                        // when NULL is passed in for an entity, it gets cast as an integer 0, so don't give an error when it happens
//                        if (!(((format.charAt(i) == D_EVENT_TRACE) || (format.charAt(i) == D_EVENT_ENTITY)) && (arg.type == 'd') && (arg.value == Integer.valueOf(0)))) {
//                            gameLocal.Error("idEvent::Alloc : Wrong type passed in for arg # %d on '%s' event.", i, evdef.GetName());
//                        }
//                    }
//
//                    switch (format.charAt(i)) {//TODO:S
//                        case D_EVENT_FLOAT:
//                        case D_EVENT_INTEGER:
//                            ev.data[i] = arg.value;
//                            break;
//                        case D_EVENT_VECTOR:
//                            if (arg.value != null) {
//                                ev.data[i] = arg.value;
//                            }
//                            break;
//                        case D_EVENT_STRING:
//                            if (arg.value != null) {
//                                ev.data[i] = (String) arg.value;
//                            }
//                            break;
//                        case D_EVENT_ENTITY:
//                        case D_EVENT_ENTITY_NULL:
//                            ev.data[i] = new idEntityPtr<idEntity>((idEntity) arg.value);
//                            break;
//                        case D_EVENT_TRACE:
//			if ( arg.value!=null ) {
//				*reinterpret_cast<bool *>( ev.data[i] ) = true;
//				*reinterpret_cast<trace_t *>( ev.data[i] + sizeof( bool ) ) = *reinterpret_cast<const trace_t *>( arg.value );
//                        final idMaterial material = ((trace_s ) arg.value ).c.material;
//
//				// save off the material as a string since the pointer won't be valid in save games.
//				// since we save off the entire trace_t structure, if the material is NULL here,
//				// it will be NULL when we process it, so we don't need to save off anything in that case.
//				if ( material !=null) {
//					materialName = material.GetName();
//					idStr.Copynz( reinterpret_cast<char *>( ev.data[i] + sizeof( bool ) + sizeof( trace_t ) ), materialName, MAX_STRING_LEN );
//				}
//			} else {
//				*reinterpret_cast<bool *>( ev.data[i] ) = false;
//			}
//                            break;
//                        default:
//                            gameLocal.Error("idEvent::Alloc : Invalid arg format '%s' string for '%s' event.", format, evdef.GetName());
//                            break;
//                    }
//                }
//            }
                return ev
            }

            fun CopyArgs(
                evdef: idEventDef,
                numargs: Int,
                args: Array<out idEventArg<*>>,
                //data: Array<idEventArg<*>> /*[ D_EVENT_MAXARGS ]*/
            ): Array<idEventArg<*>> {
                var i: Int
                val format: CharArray
                format = evdef.GetArgFormat()!!.toCharArray()
                val data: ArrayList<idEventArg<*>> = ArrayList()
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Error(
                        "idEvent::CopyArgs : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }
                i = 0
                while (i < numargs) {
                    val arg = args[i]
                    if (format[i].code != arg.type) {
                        arg.type = D_EVENT_STRING.code // try to force the string type
                        // when NULL is passed in for an entity, it gets cast as an integer 0, so don't give an error when it happens
//                    if (!(((format[i] == D_EVENT_TRACE) || (format[i] == D_EVENT_ENTITY)) && (arg.type == 'd') && (arg.value == Integer.valueOf(0)))) {
//                        Game_local.idGameLocal.Error("idEvent::CopyArgs : Wrong type passed in for arg # %d on '%s' event.", i, evdef.GetName());
//                    }
                    }
                    data.add(i, arg)
                    i++
                }

                return data.toTypedArray()
            }

            @JvmOverloads
            fun CancelEvents(obj: idClass, evdef: idEventDef? = null /*= NULL*/) {
                var event: idEvent?
                var next: idEvent?
                if (!initialized) {
                    return
                }
                event = EventQueue.Next()
                while (event != null) {
                    next = event.eventNode.Next()
                    if (event.`object` === obj) {
                        if (null == evdef || evdef == event.eventdef) {
                            event.Free()
                        }
                    }
                    event = next
                }
            }

            fun ClearEventList() {
                var i: Int

                //
                // initialize lists
                //
                FreeEvents.Clear()
                EventQueue.Clear()

                //
                // add the events to the free list
                //
                i = 0
                while (i < MAX_EVENTS) {
                    EventPool[i].Free()
                    i++
                }
            }

            fun ServiceEvents() {
                var event: idEvent?
                var num: Int
                val args = ArrayList<idEventArg<*>>(D_EVENT_MAXARGS)
                var offset: Int
                var i: Int
                var numargs: Int
                var formatspec: String
                var tracePtr: Array<trace_s?>
                var ev: idEventDef
                var data: Array<Any?>
                var materialName: String
                num = 0
                while (!EventQueue.IsListEmpty()) {
                    event = EventQueue.Next()!!
                    assert(event != null)
                    if (event.time > Game_local.gameLocal.time) {
                        break
                    }

                    // copy the data into the local args array and set up pointers
                    ev = event.eventdef
                    formatspec = ev.GetArgFormat()!!
                    numargs = ev.GetNumArgs()
                    i = 0
                    while (i < numargs) {
                        when (formatspec[i]) {
                            D_EVENT_INTEGER, D_EVENT_FLOAT, D_EVENT_VECTOR, D_EVENT_STRING, D_EVENT_ENTITY, D_EVENT_ENTITY_NULL, D_EVENT_TRACE -> args[i] =
                                event.data[i]
                            else -> idGameLocal.Error(
                                "idEvent::ServiceEvents : Invalid arg format '%s' string for '%s' event.",
                                formatspec,
                                ev.GetName()
                            )
                        }
                        i++
                    }


                    // the event is removed from its list so that if then object
                    // is deleted, the event won't be freed twice
                    event.eventNode.Remove()
                    assert(event.`object` != null)
                    event.`object`.ProcessEventArgPtr(ev, args)

// #if 0
                    // // event functions may never leave return values on the FPU stack
                    // // enable this code to check if any event call left values on the FPU stack
                    // if ( !sys.FPU_StackIsEmpty() ) {
                    // gameLocal.Error( "idEvent::ServiceEvents %d: %s left a value on the FPU stack\n", num, ev.GetName() );
                    // }
// #endif
                    // return the event to the free list
                    event.Free()

                    // Don't allow ourselves to stay in here too long.  An abnormally high number
                    // of events being processed is evidence of an infinite loop of events.
                    num++
                    if (num > MAX_EVENTSPERFRAME) {
                        idGameLocal.Error("Event overflow.  Possible infinite loop in script.")
                    }
                }
            }

            fun Init() {
                Game_local.gameLocal.Printf("Initializing event system\n")
                if (eventError) {
                    idGameLocal.Error("%s", eventErrorMsg)
                }

// #ifdef CREATE_EVENT_CODE
                // void CreateEventCallbackHandler();
                // CreateEventCallbackHandler();
                // gameLocal.Error( "Wrote event callback handler" );
// #endif
                if (initialized) {
                    Game_local.gameLocal.Printf("...already initialized\n")
                    ClearEventList()
                    return
                }
                ClearEventList()
                //
//            eventDataAllocator.Init();
//
                Game_local.gameLocal.Printf("...%d event definitions\n", idEventDef.NumEventCommands())

                // the event system has started
                initialized = true
            }

            fun Shutdown() {
                Game_local.gameLocal.Printf("Shutdown event system\n")
                if (!initialized) {
                    Game_local.gameLocal.Printf("...not started\n")
                    return
                }
                ClearEventList()
                // say it is now shutdown
                initialized = false
            }

            // save games
            fun Save(savefile: idSaveGame) {                    // archives object for save game file
                var str: String
                var i: Int
                var size: Int
                var event: idEvent?
                var dataPtr: ByteArray
                var validTrace: Boolean
                var format: String?
                savefile.WriteInt(EventQueue.Num())
                event = EventQueue.Next()
                while (event != null) {
                    savefile.WriteInt(event.time)
                    savefile.WriteString(event.eventdef.GetName())
                    savefile.WriteString(event.typeinfo.getSimpleName())
                    savefile.WriteObject(event.`object`)
                    savefile.WriteInt(event.eventdef.GetArgSize())
                    format = event.eventdef.GetArgFormat()
                    i = 0
                    size = 0
                    while (i < event.eventdef.GetNumArgs()) {
                        ++i
                    }
                    assert(size == event.eventdef.GetArgSize())
                    event = event.eventNode.Next()
                }
            }

            fun Restore(savefile: idRestoreGame) {                // unarchives object from save game file
                val str = ByteBuffer.allocate(Script_Program.MAX_STRING_LEN)
                val num = CInt()
                val argsize = CInt()
                var i: Int
                var j: Int
                var size: Int
                val name = idStr()
                var event: idEvent?
                var format: String?
                savefile.ReadInt(num)
                i = 0
                while (i < num._val) {
                    if (FreeEvents.IsListEmpty()) {
                        idGameLocal.Error("idEvent::Restore : No more free events")
                    }
                    event = FreeEvents.Next()!!
                    event.eventNode.Remove()
                    event.eventNode.AddToEnd(EventQueue)
                    event.time = savefile.ReadInt()

                    // read the event name
                    savefile.ReadString(name)
                    event.eventdef = idEventDef.FindEvent(name.toString())!!
                    if (null == event.eventdef) {
                        savefile.Error("idEvent::Restore: unknown event '%s'", name.toString())
                    }

                    // read the classtype
                    savefile.ReadString(name)
                    try {
                        event.typeinfo = java.lang.Class.forName(name.toString())
                    } catch (e: ClassNotFoundException) {
                        savefile.Error(
                            "idEvent::Restore: unknown class '%s' on event '%s'",
                            name.toString(),
                            event.eventdef.GetName()
                        )
                    }
                    savefile.ReadObject(event.`object`)

                    // read the args
                    savefile.ReadInt(argsize)
                    if (argsize._val != event.eventdef.GetArgSize()) {
                        savefile.Error(
                            "idEvent::Restore: arg size (%d) doesn't match saved arg size(%d) on event '%s'",
                            event.eventdef.GetArgSize(),
                            argsize._val,
                            event.eventdef.GetName()
                        )
                    }
                    if (argsize._val != 0) {
                        event.data = ArrayList(argsize._val) //eventDataAllocator.Alloc(argsize[0]);
                        format = event.eventdef.GetArgFormat()!!
                        assert(format != null)
                        j = 0
                        size = 0
                        while (j < event.eventdef.GetNumArgs()) {
                            when (format[j]) {
                                D_EVENT_FLOAT -> {
                                    event.data[j] = idEventArg<Any?>(D_EVENT_FLOAT.code, savefile.ReadFloat())
                                    size += java.lang.Float.BYTES
                                }
                                D_EVENT_INTEGER -> event.data[j] =
                                    idEventArg<Any?>(D_EVENT_INTEGER.code, savefile.ReadInt())
                                D_EVENT_ENTITY -> event.data[j] =
                                    idEventArg<Any?>(D_EVENT_ENTITY.code, savefile.ReadInt())
                                D_EVENT_ENTITY_NULL -> {
                                    event.data[j] = idEventArg<Any?>(D_EVENT_ENTITY_NULL.code, savefile.ReadInt())
                                    size += Integer.BYTES
                                }
                                D_EVENT_VECTOR -> {
                                    val buffer = idVec3()
                                    savefile.ReadVec3(buffer)
                                    buffer.Write()
                                    event.data[j] = idEventArg<Any?>(D_EVENT_VECTOR.code, buffer)
                                    size += idVec3.BYTES
                                }
                                D_EVENT_TRACE -> {
                                    val readBool = savefile.ReadBool()
                                    event.data[j] = idEventArg<Any?>(D_EVENT_TRACE.code, if (readBool) 1 else 0)
                                    size++
                                    //						if ( *reinterpret_cast<bool *>( dataPtr ) ) {
                                    if (readBool) {
                                        size += SERiAL.BYTES
                                        val t = trace_s()
                                        RestoreTrace(savefile, t)
                                        event.data[j] = idEventArg<Any?>(D_EVENT_TRACE.code, t)
                                        if (t.c.material != null) {
                                            size += Script_Program.MAX_STRING_LEN
                                            savefile.Read(str, Script_Program.MAX_STRING_LEN)
                                        }
                                    }
                                }
                                else -> {}
                            }
                            ++j
                        }
                        assert(size == event.eventdef.GetArgSize())
                    } else {
                        event.data.clear()
                    }
                    i++
                }
            }

            /*
         ================
         idEvent::WriteTrace

         idSaveGame has a WriteTrace procedure, but unfortunately idEvent wants the material
         string name at the of the data structure rather than in the middle
         ================
         */
            fun SaveTrace(savefile: idSaveGame, trace: trace_s) {
                savefile.WriteFloat(trace.fraction)
                savefile.WriteVec3(trace.endpos)
                savefile.WriteMat3(trace.endAxis)
                savefile.WriteInt(TempDump.etoi(trace.c.type))
                savefile.WriteVec3(trace.c.point)
                savefile.WriteVec3(trace.c.normal)
                savefile.WriteFloat(trace.c.dist)
                savefile.WriteInt(trace.c.contents)
                //            savefile.WriteInt( /*(int&)*/trace.c.material);
                savefile.Write( /*(int&)*/trace.c.material!!)
                savefile.WriteInt(trace.c.contents)
                savefile.WriteInt(trace.c.modelFeature)
                savefile.WriteInt(trace.c.trmFeature)
                savefile.WriteInt(trace.c.id)
            }

            /*
             ================
             idEvent::ReadTrace

             idRestoreGame has a ReadTrace procedure, but unfortunately idEvent wants the material
             string name at the of the data structure rather than in the middle
             ================
             */
            fun RestoreTrace(savefile: idRestoreGame, trace: trace_s) {
                trace.fraction = savefile.ReadFloat()
                savefile.ReadVec3(trace.endpos)
                savefile.ReadMat3(trace.endAxis)
                trace.c.type = contactType_t.values()[savefile.ReadInt()]
                savefile.ReadVec3(trace.c.point)
                savefile.ReadVec3(trace.c.normal)
                trace.c.dist = savefile.ReadFloat()
                trace.c.contents = savefile.ReadInt()
                //            savefile.ReadInt( /*(int&)*/trace.c.material);
                savefile.Read( /*(int&)*/trace.c.material!!)
                trace.c.contents = savefile.ReadInt()
                trace.c.modelFeature = savefile.ReadInt()
                trace.c.trmFeature = savefile.ReadInt()
                trace.c.id = savefile.ReadInt()
            }
        }
    }

    //
    init {
//preload AI_Events' idEventDefs(473).
        val actor = Actor
        val entity = AFEntity
        val ai = AI
        val events = AI_Events
        val vagary = AI_Vagary
        val camera = Camera
        val entity1 = Entity
        val fx = FX
        val item = Item
        val light = Light
        val misc = Misc
        val moveable = Moveable
        val mover = Mover
        val player = Player
        val projectile = Projectile
        val thread = Script_Thread
        val securityCamera = SecurityCamera
        val sound = Sound
        val target = Target
        val trigger = Trigger
        val weapon = Weapon
    }
}