package neo.Game.GameSys

import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.trace_s
import neo.Game.AFEntity
import neo.Game.AI.AI
import neo.Game.AI.AI_Events
import neo.Game.AI.AI_Vagary
import neo.Game.Actor
import neo.Game.Camera
import neo.Game.FX
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Game.Light
import neo.Game.Misc
import neo.Game.Moveable
import neo.Game.Mover
import neo.Game.Player
import neo.Game.Projectile
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Thread
import neo.Game.SecurityCamera
import neo.Game.Sound
import neo.Game.Weapon
import neo.TempDump
import neo.TempDump.SERiAL
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CInt
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.math.Vector.idVec3
import java.nio.*

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
    var EventPool: Array<idEvent?>? = arrayOfNulls<idEvent?>(Event.MAX_EVENTS)
    var EventQueue: idLinkList<idEvent?>? = idLinkList()

    //
    var FreeEvents: idLinkList<idEvent?>? = idLinkList()

    //
    var eventError = false
    var eventErrorMsg: String? = null

    /* **********************************************************************

     idEventDef

     ***********************************************************************/
    class idEventDef(command: String?, formatSpec: String? /*= NULL*/, returnType: Char /*= 0*/) {
        private val argOffset: IntArray? = IntArray(Event.D_EVENT_MAXARGS)
        private val   /*size_t*/argsize: Int
        private val eventnum: Int
        private val formatspec: String?
        private val   /*unsigned int*/formatspecIndex: Long
        private val name: String?
        private val next: idEventDef? = null
        private val numargs: Int
        private val returnType: Int

        //
        //
        @JvmOverloads
        constructor(command: String?, formatspec: String? = null /*= NULL*/) : this(command, formatspec, 0.toChar())

        fun GetName(): String? {
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
            assert(arg >= 0 && arg < Event.D_EVENT_MAXARGS)
            return argOffset.get(arg)
        }

        override fun hashCode(): Int {
            return eventnum
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val that = o as idEventDef?
            return eventnum == that.eventnum
        }

        companion object {
            //
            private val eventDefList: Array<idEventDef?>? = arrayOfNulls<idEventDef?>(Event.MAX_EVENTS)
            private var numEventDefs = 0
            fun NumEventCommands(): Int {
                return numEventDefs
            }

            fun GetEventCommand(eventnum: Int): idEventDef? {
                return eventDefList.get(eventnum)
            }

            fun FindEvent(name: String?): idEventDef? {
                var ev: idEventDef?
                val num: Int
                var i: Int
                assert(name != null)
                num = numEventDefs
                i = 0
                while (i < num) {
                    ev = eventDefList.get(i)
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
            var ev: idEventDef?
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
            assert(numargs <= Event.D_EVENT_MAXARGS)
            if (numargs > Event.D_EVENT_MAXARGS) {
                Event.eventError = true
                Event.eventErrorMsg = String.format("idEventDef::idEventDef : Too many args for '%s' event.", name)
                return
            }

            // make sure the format for the args is valid, calculate the formatspecindex, and the offsets for each arg
            bits = 0
            argsize = 0
            argOffset = IntArray(Event.D_EVENT_MAXARGS) //memset( argOffset, 0, sizeof( argOffset ) );
            i = 0
            while (i < numargs) {
                argOffset[i] = argsize
                when (formatSpec[i]) {
                    Event.D_EVENT_FLOAT -> {
                        bits = bits or (1 shl i)
                        argsize += java.lang.Float.SIZE / java.lang.Byte.SIZE
                    }
                    Event.D_EVENT_INTEGER -> argsize += Integer.SIZE / java.lang.Byte.SIZE
                    Event.D_EVENT_VECTOR -> argsize += idVec3.Companion.BYTES
                    Event.D_EVENT_STRING -> argsize += Script_Program.MAX_STRING_LEN
                    Event.D_EVENT_ENTITY, Event.D_EVENT_ENTITY_NULL -> argsize += TempDump.CPP_class.Pointer.SIZE / java.lang.Byte.SIZE
                    Event.D_EVENT_TRACE -> {}
                    else -> {
                        Event.eventError = true
                        Event.eventErrorMsg = String.format(
                            "idEventDef::idEventDef : Invalid arg format '%s' string for '%s' event.",
                            formatSpec,
                            name
                        )
                        return
                    }
                }
                i++
            }

            // calculate the formatspecindex
            formatspecIndex = 1 shl numargs + Event.D_EVENT_MAXARGS or bits

            // go through the list of defined events and check for duplicates
            // and mismatched format strings
            eventnum = numEventDefs
            i = 0
            while (i < eventnum) {
                ev = eventDefList.get(i)
                if (command == ev.name) {
                    if (formatSpec != ev.formatspec) {
                        Event.eventError = true
                        Event.eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing format strings ('%s'!='%s').",
                            command, formatSpec, ev.formatspec
                        )
                        return
                    }
                    if (ev.returnType != returnType.code) {
                        Event.eventError = true
                        Event.eventErrorMsg = String.format(
                            "idEvent '%s' defined twice with same name but differing return types ('%c'!='%c').",
                            command, returnType, ev.returnType
                        )
                        return
                    }
                    // Don't bother putting the duplicate event in list.
                    eventnum = ev.eventnum
                    return
                }
                i++
            }
            ev = this
            if (numEventDefs >= Event.MAX_EVENTS) {
                Event.eventError = true
                Event.eventErrorMsg = String.format("numEventDefs >= MAX_EVENTS")
                return
            }
            eventDefList.get(numEventDefs) = ev
            numEventDefs++
        }
    }

    /* **********************************************************************

     idEvent

     ***********************************************************************/
    class idEvent {
        private var data: Array<idEventArg<*>?>?

        //
        private val eventNode: idLinkList<idEvent?>? = idLinkList()
        private var eventdef: idEventDef? = null
        private var `object`: idClass? = null
        private var time = 0
        private var typeinfo: Class<*>? = null
        fun Free() {
//            if (data != null) {
//                eventDataAllocator.Free(data);
            data = null
            //            }
            eventdef = null
            time = 0
            `object` = null
            typeinfo = null
            eventNode.SetOwner(this)
            eventNode.AddToEnd(Event.FreeEvents)
        }

        fun Schedule(obj: idClass?, type: Class<*>?, time: Int) {
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
            event = Event.EventQueue.Next()
            while (event != null && this.time >= event.time) {
                event = event.eventNode.Next()
            }
            if (event != null) {
                eventNode.InsertBefore(event.eventNode)
            } else {
                eventNode.AddToEnd(Event.EventQueue)
            }
        }

        fun GetData(): Array<Any?>? {
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
            fun Alloc(evdef: idEventDef?, numargs: Int, vararg args: idEventArg<*>?): idEvent? {
                val ev: idEvent?
                val   /*size_t*/size: Int
                val format: String?
                //            idEventArg arg;
                var i: Int
                var materialName: String
                if (Event.FreeEvents.IsListEmpty()) {
                    idGameLocal.Companion.Error("idEvent::Alloc : No more free events")
                }
                ev = Event.FreeEvents.Next()
                ev.eventNode.Remove()
                ev.eventdef = evdef
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Companion.Error(
                        "idEvent::Alloc : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }
                size = evdef.GetArgSize()
                if (size != 0) {
//		ev.data = eventDataAllocator.Alloc( size );
//		memset( ev.data, 0, size );
                    ev.data = args.clone()
                } else {
                    ev.data = null
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
                evdef: idEventDef?,
                numargs: Int,
                args: Array<idEventArg<*>?>?,
                data: Array<idEventArg<*>?>? /*[ D_EVENT_MAXARGS ]*/
            ) {
                var i: Int
                val format: CharArray
                format = evdef.GetArgFormat().toCharArray()
                if (numargs != evdef.GetNumArgs()) {
                    idGameLocal.Companion.Error(
                        "idEvent::CopyArgs : Wrong number of args for '%s' event.",
                        evdef.GetName()
                    )
                }
                i = 0
                while (i < numargs) {
                    val arg = args.get(i)
                    if (format[i] != arg.type) {
                        arg.type = Event.D_EVENT_STRING.code // try to force the string type
                        // when NULL is passed in for an entity, it gets cast as an integer 0, so don't give an error when it happens
//                    if (!(((format[i] == D_EVENT_TRACE) || (format[i] == D_EVENT_ENTITY)) && (arg.type == 'd') && (arg.value == Integer.valueOf(0)))) {
//                        Game_local.idGameLocal.Error("idEvent::CopyArgs : Wrong type passed in for arg # %d on '%s' event.", i, evdef.GetName());
//                    }
                    }
                    data.get(i) = arg
                    i++
                }
            }

            @JvmOverloads
            fun CancelEvents(obj: idClass?, evdef: idEventDef? = null /*= NULL*/) {
                var event: idEvent?
                var next: idEvent?
                if (!initialized) {
                    return
                }
                event = Event.EventQueue.Next()
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
                Event.FreeEvents.Clear()
                Event.EventQueue.Clear()

                //
                // add the events to the free list
                //
                i = 0
                while (i < Event.MAX_EVENTS) {
                    Event.EventPool[i].Free()
                    i++
                }
            }

            fun ServiceEvents() {
                var event: idEvent?
                var num: Int
                val args = arrayOfNulls<idEventArg<*>?>(Event.D_EVENT_MAXARGS)
                var offset: Int
                var i: Int
                var numargs: Int
                var formatspec: String
                var tracePtr: Array<trace_s?>
                var ev: idEventDef?
                var data: Array<Any?>
                var materialName: String
                num = 0
                while (!Event.EventQueue.IsListEmpty()) {
                    event = Event.EventQueue.Next()
                    assert(event != null)
                    if (event.time > Game_local.gameLocal.time) {
                        break
                    }

                    // copy the data into the local args array and set up pointers
                    ev = event.eventdef
                    formatspec = ev.GetArgFormat()
                    numargs = ev.GetNumArgs()
                    i = 0
                    while (i < numargs) {
                        when (formatspec[i]) {
                            Event.D_EVENT_INTEGER, Event.D_EVENT_FLOAT, Event.D_EVENT_VECTOR, Event.D_EVENT_STRING, Event.D_EVENT_ENTITY, Event.D_EVENT_ENTITY_NULL, Event.D_EVENT_TRACE -> args[i] =
                                event.data.get(i)
                            else -> idGameLocal.Companion.Error(
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
                    if (num > Event.MAX_EVENTSPERFRAME) {
                        idGameLocal.Companion.Error("Event overflow.  Possible infinite loop in script.")
                    }
                }
            }

            fun Init() {
                Game_local.gameLocal.Printf("Initializing event system\n")
                if (Event.eventError) {
                    idGameLocal.Companion.Error("%s", Event.eventErrorMsg)
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
            fun Save(savefile: idSaveGame?) {                    // archives object for save game file
                var str: String
                var i: Int
                var size: Int
                var event: idEvent?
                var dataPtr: ByteArray
                var validTrace: Boolean
                var format: String?
                savefile.WriteInt(Event.EventQueue.Num())
                event = Event.EventQueue.Next()
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

            fun Restore(savefile: idRestoreGame?) {                // unarchives object from save game file
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
                while (i < num.getVal()) {
                    if (Event.FreeEvents.IsListEmpty()) {
                        idGameLocal.Companion.Error("idEvent::Restore : No more free events")
                    }
                    event = Event.FreeEvents.Next()
                    event.eventNode.Remove()
                    event.eventNode.AddToEnd(Event.EventQueue)
                    event.time = savefile.ReadInt()

                    // read the event name
                    savefile.ReadString(name)
                    event.eventdef = idEventDef.FindEvent(name.toString())
                    if (null == event.eventdef) {
                        savefile.Error("idEvent::Restore: unknown event '%s'", name.toString())
                    }

                    // read the classtype
                    savefile.ReadString(name)
                    try {
                        event.typeinfo = Class.forName(name.toString())
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
                    if (argsize.getVal() != event.eventdef.GetArgSize()) {
                        savefile.Error(
                            "idEvent::Restore: arg size (%d) doesn't match saved arg size(%d) on event '%s'",
                            event.eventdef.GetArgSize(),
                            argsize.getVal(),
                            event.eventdef.GetName()
                        )
                    }
                    if (argsize.getVal() != 0) {
                        event.data =
                            arrayOfNulls<idEventArg<*>?>(argsize.getVal()) //eventDataAllocator.Alloc(argsize[0]);
                        format = event.eventdef.GetArgFormat()
                        assert(format != null)
                        j = 0
                        size = 0
                        while (j < event.eventdef.GetNumArgs()) {
                            when (format.get(j)) {
                                Event.D_EVENT_FLOAT -> {
                                    event.data.get(j) = idEventArg<Any?>(Event.D_EVENT_FLOAT, savefile.ReadFloat())
                                    size += java.lang.Float.BYTES
                                }
                                Event.D_EVENT_INTEGER -> event.data.get(j) =
                                    idEventArg<Any?>(Event.D_EVENT_INTEGER, savefile.ReadInt())
                                Event.D_EVENT_ENTITY -> event.data.get(j) =
                                    idEventArg<Any?>(Event.D_EVENT_ENTITY, savefile.ReadInt())
                                Event.D_EVENT_ENTITY_NULL -> {
                                    event.data.get(j) = idEventArg<Any?>(Event.D_EVENT_ENTITY_NULL, savefile.ReadInt())
                                    size += Integer.BYTES
                                }
                                Event.D_EVENT_VECTOR -> {
                                    val buffer = idVec3()
                                    savefile.ReadVec3(buffer)
                                    buffer.Write()
                                    event.data.get(j) = idEventArg<Any?>(Event.D_EVENT_VECTOR, buffer)
                                    size += idVec3.Companion.BYTES
                                }
                                Event.D_EVENT_TRACE -> {
                                    val readBool = savefile.ReadBool()
                                    event.data.get(j) = idEventArg<Any?>(Event.D_EVENT_TRACE, if (readBool) 1 else 0)
                                    size++
                                    //						if ( *reinterpret_cast<bool *>( dataPtr ) ) {
                                    if (readBool) {
                                        size += SERiAL.Companion.BYTES
                                        val t = trace_s()
                                        RestoreTrace(savefile, t)
                                        event.data.get(j) = idEventArg<Any?>(Event.D_EVENT_TRACE, t)
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
                        event.data = null
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
            fun SaveTrace(savefile: idSaveGame?, trace: trace_s?) {
                savefile.WriteFloat(trace.fraction)
                savefile.WriteVec3(trace.endpos)
                savefile.WriteMat3(trace.endAxis)
                savefile.WriteInt(TempDump.etoi(trace.c.type))
                savefile.WriteVec3(trace.c.point)
                savefile.WriteVec3(trace.c.normal)
                savefile.WriteFloat(trace.c.dist)
                savefile.WriteInt(trace.c.contents)
                //            savefile.WriteInt( /*(int&)*/trace.c.material);
                savefile.Write( /*(int&)*/trace.c.material)
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
            fun RestoreTrace(savefile: idRestoreGame?, trace: trace_s?) {
                trace.fraction = savefile.ReadFloat()
                savefile.ReadVec3(trace.endpos)
                savefile.ReadMat3(trace.endAxis)
                trace.c.type = contactType_t.values()[savefile.ReadInt()]
                savefile.ReadVec3(trace.c.point)
                savefile.ReadVec3(trace.c.normal)
                trace.c.dist = savefile.ReadFloat()
                trace.c.contents = savefile.ReadInt()
                //            savefile.ReadInt( /*(int&)*/trace.c.material);
                savefile.Read( /*(int&)*/trace.c.material)
                trace.c.contents = savefile.ReadInt()
                trace.c.modelFeature = savefile.ReadInt()
                trace.c.trmFeature = savefile.ReadInt()
                trace.c.id = savefile.ReadInt()
            }
        }
    }

    //
    init {
        for (e in Event.EventPool.indices) {
            Event.EventPool[e] = idEvent()
        }
        {   //preload AI_Events' idEventDefs(473).
            val actor = Actor()
            val entity = AFEntity()
            val ai = AI()
            val events = AI_Events()
            val vagary = AI_Vagary()
            val camera = Camera()
            val entity1 = Entity()
            val fx = FX()
            val item = Item()
            val light = Light()
            val misc = Misc()
            val moveable = Moveable()
            val mover = Mover()
            val player = Player()
            val projectile = Projectile()
            val thread = Script_Thread()
            val securityCamera = SecurityCamera()
            val sound = Sound()
            val target = Target()
            val trigger = Trigger()
            val weapon = Weapon()
        }
    }
}