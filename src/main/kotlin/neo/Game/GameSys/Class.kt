package neo.Game.GameSys

import neo.CM.CollisionModel.trace_s
import neo.Game.*
import neo.Game.AFEntity.idAFEntity_ClawFourFingers
import neo.Game.AFEntity.idAFEntity_Generic
import neo.Game.AFEntity.idAFEntity_WithAttachedHead
import neo.Game.AI.AI.idAI
import neo.Game.AI.AI.idCombatNode
import neo.Game.AI.AI_Vagary.idAI_Vagary
import neo.Game.BrittleFracture.idBrittleFracture
import neo.Game.Camera.idCameraAnim
import neo.Game.Camera.idCameraView
import neo.Game.Entity.idAnimatedEntity
import neo.Game.Entity.idEntity
import neo.Game.FX.idEntityFx
import neo.Game.FX.idTeleporter
import neo.Game.GameSys.*
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Item.idItem
import neo.Game.Item.idItemPowerup
import neo.Game.Item.idItemRemover
import neo.Game.Item.idMoveableItem
import neo.Game.Item.idMoveablePDAItem
import neo.Game.Item.idObjective
import neo.Game.Item.idObjectiveComplete
import neo.Game.Item.idPDAItem
import neo.Game.Item.idVideoCDItem
import neo.Game.Light.idLight
import neo.Game.Misc.idActivator
import neo.Game.Misc.idAnimated
import neo.Game.Misc.idBeam
import neo.Game.Misc.idDamagable
import neo.Game.Misc.idEarthQuake
import neo.Game.Misc.idExplodable
import neo.Game.Misc.idForceField
import neo.Game.Misc.idFuncAASObstacle
import neo.Game.Misc.idFuncEmitter
import neo.Game.Misc.idFuncPortal
import neo.Game.Misc.idFuncRadioChatter
import neo.Game.Misc.idFuncSmoke
import neo.Game.Misc.idFuncSplat
import neo.Game.Misc.idLocationEntity
import neo.Game.Misc.idLocationSeparatorEntity
import neo.Game.Misc.idPathCorner
import neo.Game.Misc.idPhantomObjects
import neo.Game.Misc.idPlayerStart
import neo.Game.Misc.idShaking
import neo.Game.Misc.idSpawnableEntity
import neo.Game.Misc.idStaticEntity
import neo.Game.Misc.idVacuumEntity
import neo.Game.Misc.idVacuumSeparatorEntity
import neo.Game.Moveable.idBarrel
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Moveable.idMoveable
import neo.Game.Mover.idBobber
import neo.Game.Mover.idDoor
import neo.Game.Mover.idElevator
import neo.Game.Mover.idMover
import neo.Game.Mover.idPendulum
import neo.Game.Mover.idPlat
import neo.Game.Mover.idRotater
import neo.Game.Mover.idSplinePath
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idBFGProjectile
import neo.Game.Projectile.idDebris
import neo.Game.Projectile.idGuidedProjectile
import neo.Game.Projectile.idProjectile
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.Script.Script_Thread.idThread
import neo.Game.SecurityCamera.idSecurityCamera
import neo.Game.Sound.idSound
import neo.Game.Target.idTarget
import neo.Game.Target.idTarget_CallObjectFunction
import neo.Game.Target.idTarget_Damage
import neo.Game.Target.idTarget_EnableLevelWeapons
import neo.Game.Target.idTarget_EnableStamina
import neo.Game.Target.idTarget_EndLevel
import neo.Game.Target.idTarget_FadeEntity
import neo.Game.Target.idTarget_GiveEmail
import neo.Game.Target.idTarget_GiveSecurity
import neo.Game.Target.idTarget_LevelTrigger
import neo.Game.Target.idTarget_LightFadeIn
import neo.Game.Target.idTarget_LightFadeOut
import neo.Game.Target.idTarget_LockDoor
import neo.Game.Target.idTarget_Remove
import neo.Game.Target.idTarget_RemoveWeapons
import neo.Game.Target.idTarget_SetInfluence
import neo.Game.Target.idTarget_SetKeyVal
import neo.Game.Target.idTarget_SetPrimaryObjective
import neo.Game.Target.idTarget_SetShaderParm
import neo.Game.Target.idTarget_Show
import neo.Game.Target.idTarget_Tip
import neo.Game.Trigger.idTrigger_Count
import neo.Game.Trigger.idTrigger_EntityName
import neo.Game.Trigger.idTrigger_Fade
import neo.Game.Trigger.idTrigger_Hurt
import neo.Game.Trigger.idTrigger_Multi
import neo.Game.Trigger.idTrigger_Timer
import neo.Game.Trigger.idTrigger_Touch
import neo.Game.WorldSpawn.idWorldspawn
import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CmdSystem.cmdFunction_t
import neo.idlib.*
import neo.idlib.Lib.idException
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.Hierarchy.idHierarchy
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec3
import java.lang.Class

/**
 *
 */
object Class {
    val EV_Remove: idEventDef = idEventDef("<immediateremove>", null)
    val EV_SafeRemove: idEventDef = idEventDef("remove", null)
    var classHierarchy: idHierarchy<idTypeInfo?> = idHierarchy()
    var eventCallbackMemory = 0

    // this is the head of a singly linked list of all the idTypes
    var typelist: idTypeInfo? = null

    fun interface eventCallback_t<T : idClass?> {
        open fun accept(t: T?, vararg args: idEventArg<*>?)
    }

    @FunctionalInterface
    interface eventCallback_t0<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t)
        }

        open fun accept(e: T?)
    }

    @FunctionalInterface
    interface eventCallback_t1<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0])
        }

        open fun accept(t: T?, a: idEventArg<*>?)
    }

    @FunctionalInterface
    interface eventCallback_t2<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0], args[1])
        }

        open fun accept(t: T?, a: idEventArg<*>?, b: idEventArg<*>?)
    }

    @FunctionalInterface
    interface eventCallback_t3<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0], args[1], args[2])
        }

        open fun accept(t: T?, a: idEventArg<*>?, b: idEventArg<*>?, c: idEventArg<*>?)
    }

    @FunctionalInterface
    interface eventCallback_t4<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0], args[1], args[2], args[3])
        }

        open fun accept(t: T?, a: idEventArg<*>?, b: idEventArg<*>?, c: idEventArg<*>?, d: idEventArg<*>?)
    }

    @FunctionalInterface
    interface eventCallback_t5<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0], args[1], args[2], args[3], args[4])
        }

        open fun accept(
            t: T?,
            a: idEventArg<*>?,
            b: idEventArg<*>?,
            c: idEventArg<*>?,
            d: idEventArg<*>?,
            e: idEventArg<*>?
        )
    }

    @FunctionalInterface
    interface eventCallback_t6<T : idClass?> : eventCallback_t<T?> {
        override fun accept(t: T?, vararg args: idEventArg<*>?) {
            accept(t, args[0], args[1], args[2], args[3], args[4], args[5])
        }

        open fun accept(
            t: T?,
            a: idEventArg<*>?,
            b: idEventArg<*>?,
            c: idEventArg<*>?,
            d: idEventArg<*>?,
            e: idEventArg<*>?,
            f: idEventArg<*>?
        )
    }

    abstract class classSpawnFunc_t<type> {
        abstract fun run(): type?
    }

    abstract class idClass_Save {
        abstract fun run(savefile: idSaveGame?)
    }

    abstract class idClass_Restore {
        abstract fun run(savefile: idRestoreGame?)
    }

    class idEventFunc<type> {
        var event: idEventDef? = null
        var function: eventCallback_t<*>? = null
    }

    class idEventArg<T> {
        var type = 0
        var value: T?

        //
        //
        private constructor(data: T?) {
            type =
                if (data is Int) Event.D_EVENT_INTEGER.code else if (data is Enum<*>) Event.D_EVENT_INTEGER.code else if (data is Float) Event.D_EVENT_FLOAT.code else if (data is idVec3) Event.D_EVENT_VECTOR.code else if (data is idStr) Event.D_EVENT_STRING.code else if (data is String) Event.D_EVENT_STRING.code else if (data is idEntity) Event.D_EVENT_ENTITY.code else if (data is trace_s) Event.D_EVENT_TRACE.code else {
                    Event.D_EVENT_VOID.code
                    //throw new TempDump.TypeErasure_Expection();
                }
            value = data
        }

        constructor(type: Int, data: T?) {
            this.type = type
            value = data
        }

        companion object {
            fun <T> toArg(data: T?): idEventArg<T?>? {
                return idEventArg<Any?>(data)
            }

            fun toArg(data: Int): idEventArg<Int?>? {
                return idEventArg<Any?>(Event.D_EVENT_INTEGER, data)
            }

            fun toArg(data: Float): idEventArg<Float?>? {
                return idEventArg<Any?>(Event.D_EVENT_FLOAT, data)
            }

            fun toArg(data: idVec3?): idEventArg<idVec3?>? {
                return idEventArg<Any?>(Event.D_EVENT_VECTOR, data)
            }

            fun toArg(data: idStr?): idEventArg<idStr?>? {
                return idEventArg<Any?>(Event.D_EVENT_STRING, data)
            }

            fun toArg(data: String?): idEventArg<String?>? {
                return idEventArg<Any?>(Event.D_EVENT_STRING, data)
            }

            fun toArg(data: idEntity?): idEventArg<idEntity?>? {
                return idEventArg<Any?>(Event.D_EVENT_ENTITY, data)
            }

            fun toArg(data: trace_s?): idEventArg<trace_s?>? {
                return idEventArg<Any?>(Event.D_EVENT_TRACE, data)
            }
        }
    }

    class idAllocError(text: String? /*= ""*/) : idException(text)

    //    /*
    //================
    //ABSTRACT_PROTOTYPE
    //
    //This macro must be included in the definition of any abstract subclass of idClass.
    //It prototypes variables used in class instanciation and type checking.
    //Use this on single inheritance abstract classes only.
    //================
    //*/
    //#define ABSTRACT_PROTOTYPE( nameofclass )								\
    //public:																	\
    //	static	idTypeInfo						Type;						\
    //	static	idClass							*CreateInstance( void );	\
    //	virtual	idTypeInfo						*GetType( void ) const;		\
    //	static	idEventFunc<nameofclass>		eventCallbacks[]
    //
    ///*
    //================
    //ABSTRACT_DECLARATION
    //
    //This macro must be included in the code to properly initialize variables
    //used in type checking.  It also defines the list of events that the class
    //responds to.  Take special care to ensure that the proper superclass is
    //indicated or the run-time tyep information will be incorrect.  Use this
    //on abstract classes only.
    //================
    //*/
    //#define ABSTRACT_DECLARATION( nameofsuperclass, nameofclass )										\
    //	idTypeInfo nameofclass::Type( #nameofclass, #nameofsuperclass,									\
    //		( idEventFunc<idClass> * )nameofclass::eventCallbacks, nameofclass::CreateInstance, ( void ( idClass::* )( void ) )&nameofclass::Spawn,	\
    //		( void ( idClass::* )( idSaveGame * ) const )&nameofclass::Save, ( void ( idClass::* )( idRestoreGame * ) )&nameofclass::Restore );	\
    //	idClass *nameofclass::CreateInstance( void ) {													\
    //		gameLocal.Error( "Cannot instanciate abstract class %s.", #nameofclass );					\
    //		return NULL;																				\
    //	}																								\
    //	idTypeInfo *nameofclass::GetType( void ) const {												\
    //		return &( nameofclass::Type );																\
    //	}																								\
    //	idEventFunc<nameofclass> nameofclass::eventCallbacks[] = {
    //
    //typedef void ( idClass::*classSpawnFunc_t )( void );
    //
    //class idSaveGame;
    //class idRestoreGame;
    abstract class idClass /*<nameOfClass>*/ {
        companion object {
            //        public static final idTypeInfo Type = null;
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()

            //
            private var initialized = false
            private const val memused = 0
            private const val numobjects = 0
            private var typeNumBits = 0

            // typenum order
            private val typenums: idList<idTypeInfo?>? = idList()

            // alphabetical order
            private val types: idList<idTypeInfo?>? = idList()

            //
            //
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            fun INIT() {
                var c: idTypeInfo?
                var num: Int
                Game_local.gameLocal.Printf("Initializing class hierarchy\n")
                if (initialized) {
                    Game_local.gameLocal.Printf("...already initialized\n")
                    return
                }

                // init the event callback tables for all the classes
                c = neo.Game.GameSys.Class.typelist
                while (c != null) {
                    c.Init()
                    c = c.next
                }

                // number the types according to the class hierarchy so we can quickly determine if a class
                // is a subclass of another
                num = 0
                c = neo.Game.GameSys.Class.classHierarchy.GetNext()
                while (c != null) {
                    c.typeNum = num
                    c.lastChild += num
                    c = c.node.GetNext()
                    num++
                }

                // number of bits needed to send types over network
                typeNumBits = idMath.BitsForInteger(num)

                // create a list of the types so we can do quick lookups
                // one list in alphabetical order, one in typenum order
                types.SetGranularity(1)
                types.SetNum(num)
                typenums.SetGranularity(1)
                typenums.SetNum(num)
                num = 0
                c = neo.Game.GameSys.Class.typelist
                while (c != null) {
                    types.set(num, c)
                    typenums.set(c.typeNum, c)
                    c = c.next
                    num++
                }
                initialized = true
                Game_local.gameLocal.Printf(
                    "...%d classes, %d bytes for event callbacks\n",
                    types.Num(),
                    neo.Game.GameSys.Class.eventCallbackMemory
                )
            }

            fun Shutdown() {
                var c: idTypeInfo?
                c = neo.Game.GameSys.Class.typelist
                while (c != null) {
                    c.Shutdown()
                    c = c.next
                }
                types.Clear()
                typenums.Clear()
                initialized = false
            }

            /*
         ================
         idClass::GetClass

         Returns the idTypeInfo for the name of the class passed in.  This is a static function
         so it must be called as idClass::GetClass( classname )
         ================
         */
            fun GetClass(name: String?): idTypeInfo? {
                when (name) {
                    "idWorldspawn" -> {}
                }
                return null
            }

            fun GetEntity(name: String?): idEntity? {
                return if (name == null || name.isEmpty()) {
                    null
                } else when (name) {
                    "idWorldspawn" -> idWorldspawn()
                    "idStaticEntity" -> idStaticEntity()
                    "idPathCorner" -> idPathCorner()
                    "idTrigger_Multi" -> idTrigger_Multi()
                    "idTarget_Tip" -> idTarget_Tip()
                    "idTarget_Remove" -> idTarget_Remove()
                    "idMover" -> idMover()
                    "idMoveable" -> idMoveable()
                    "idLight" -> idLight()
                    "idCameraAnim" -> idCameraAnim()
                    "idAI" -> idAI()
                    "idFuncEmitter" -> idFuncEmitter()
                    "idAnimated" -> idAnimated()
                    "idBFGProjectile" -> idBFGProjectile()
                    "idTrigger_Hurt" -> idTrigger_Hurt()
                    "idMoveablePDAItem" -> idMoveablePDAItem()
                    "idLocationEntity" -> idLocationEntity()
                    "idPlayerStart" -> idPlayerStart()
                    "idSound" -> idSound()
                    "idTarget_GiveEmail" -> idTarget_GiveEmail()
                    "idTarget_SetPrimaryObjective" -> idTarget_SetPrimaryObjective()
                    "idObjectiveComplete" -> idObjectiveComplete()
                    "idTarget" -> idTarget()
                    "idCameraView" -> idCameraView()
                    "idObjective" -> idObjective()
                    "idTarget_SetShaderParm" -> idTarget_SetShaderParm()
                    "idTarget_FadeEntity" -> idTarget_FadeEntity()
                    "idEntityFx" -> idEntityFx()
                    "idItem" -> idItem()
                    "idSplinePath" -> idSplinePath()
                    "idAFEntity_Generic" -> idAFEntity_Generic()
                    "idDoor" -> idDoor()
                    "idProjectile" -> idProjectile()
                    "idTrigger_Count" -> idTrigger_Count()
                    "idTarget_EndLevel" -> idTarget_EndLevel()
                    "idTarget_CallObjectFunction" -> idTarget_CallObjectFunction()
                    "idTrigger_Fade" -> idTrigger_Fade()
                    "idPDAItem" -> idPDAItem()
                    "idVideoCDItem" -> idVideoCDItem()
                    "idLocationSeparatorEntity" -> idLocationSeparatorEntity()
                    "idPlayer" -> idPlayer()
                    "idDebris" -> idDebris()
                    "idSpawnableEntity" -> idSpawnableEntity()
                    "idTarget_LightFadeIn" -> idTarget_LightFadeIn()
                    "idTarget_LightFadeOut" -> idTarget_LightFadeOut()
                    "idItemPowerup" -> idItemPowerup()
                    "idForceField" -> idForceField()
                    "idTarget_LockDoor" -> idTarget_LockDoor()
                    "idTarget_SetInfluence" -> idTarget_SetInfluence()
                    "idExplodingBarrel" -> idExplodingBarrel()
                    "idTarget_EnableLevelWeapons" -> idTarget_EnableLevelWeapons()
                    "idAFEntity_WithAttachedHead" -> idAFEntity_WithAttachedHead()
                    "idCombatNode" -> idCombatNode()
                    "idFuncAASObstacle" -> idFuncAASObstacle()
                    "idVacuumEntity" -> idVacuumEntity()
                    "idRotater" -> idRotater()
                    "idElevator" -> idElevator()
                    "idShaking" -> idShaking()
                    "idFuncRadioChatter" -> idFuncRadioChatter()
                    "idFuncPortal" -> idFuncPortal()
                    "idMoveableItem" -> idMoveableItem()
                    "idFuncSmoke" -> idFuncSmoke()
                    "idPhantomObjects" -> idPhantomObjects()
                    "idBeam" -> idBeam()
                    "idExplodable" -> idExplodable()
                    "idEarthQuake" -> idEarthQuake()
                    "idGuidedProjectile" -> idGuidedProjectile()
                    "idTarget_Show" -> idTarget_Show()
                    "idBrittleFracture" -> idBrittleFracture()
                    "idTrigger_Timer" -> idTrigger_Timer()
                    "idPendulum" -> idPendulum()
                    "idItemRemover" -> idItemRemover()
                    "idTarget_GiveSecurity" -> idTarget_GiveSecurity()
                    "idTrigger_EntityName" -> idTrigger_EntityName()
                    "idBarrel" -> idBarrel()
                    "idActivator" -> idActivator()
                    "idFuncSplat" -> idFuncSplat()
                    "idTarget_Damage" -> idTarget_Damage()
                    "idTarget_SetKeyVal" -> idTarget_SetKeyVal()
                    "idTarget_EnableStamina" -> idTarget_EnableStamina()
                    "idVacuumSeparatorEntity" -> idVacuumSeparatorEntity()
                    "idDamagable" -> idDamagable()
                    "idSecurityCamera" -> idSecurityCamera()
                    "idTrigger_Touch" -> idTrigger_Touch()
                    "idAFEntity_ClawFourFingers" -> idAFEntity_ClawFourFingers()
                    "idAI_Vagary" -> idAI_Vagary()
                    "idBobber" -> idBobber()
                    "idTarget_LevelTrigger" -> idTarget_LevelTrigger()
                    "idTarget_RemoveWeapons" -> idTarget_RemoveWeapons()
                    "idTeleporter" -> idTeleporter()
                    "idPlat" -> idPlat()
                    "idSoulCubeMissile" -> idSoulCubeMissile()
                    "idAnimatedEntity" -> idAnimatedEntity()
                    else -> null
                }
            }

            // #ifdef ID_REDIRECT_NEWDELETE
            // #undef new
            // #endif
            //public	Object						operator new( size_t );
            //public	Object						operator new( size_t s, int, int, char *, int );
            //public	void						operator delete( void * );
            //public	void						operator delete( void *, int, int, char *, int );
            // #ifdef ID_REDIRECT_NEWDELETE
            // #define new ID_DEBUG_NEW
            // #endif
            fun CreateInstance(name: String?): idClass? {
//            idTypeInfo type;
//            idClass obj;
//
//            type = idClass.GetClass(name);
//            if (NOT(type)) {
//                return null;
//            }
//
//            return type.CreateInstance();
//            return obj;
                throw TODO_Exception()
            }

            fun GetNumTypes(): Int {
                return types.Num()
            }

            fun GetTypeNumBits(): Int {
                return typeNumBits
            }

            fun GetType(typeNum: Int): idTypeInfo? {
                var c: idTypeInfo?
                if (!initialized) {
                    c = neo.Game.GameSys.Class.typelist
                    while (c != null) {
                        if (c.typeNum == typeNum) {
                            return c
                        }
                        c = c.next
                    }
                } else if (typeNum >= 0 && typeNum < types.Num()) {
                    return typenums.get(typeNum)
                }
                return null
            }

            fun delete(clazz: idClass?) {
                clazz?._deconstructor()
            }

            init {
                eventCallbacks[neo.Game.GameSys.Class.EV_Remove] =
                    eventCallback_t0<idClass?> { obj: T? -> neo.Game.GameSys.obj.Event_Remove() } as eventCallback_t0<idClass?>
                eventCallbacks[neo.Game.GameSys.Class.EV_SafeRemove] =
                    eventCallback_t0<idClass?> { obj: T? -> neo.Game.GameSys.obj.Event_SafeRemove() } as eventCallback_t0<idClass?>
            }
        }

        abstract fun CreateInstance(): idClass?
        abstract fun  /*idTypeInfo*/GetType(): Class<out idClass?>?
        abstract fun getEventCallBack(event: idEventDef): eventCallback_t<*>

        // virtual						~idClass();
        protected open fun _deconstructor() {
            CancelEvents(this)
        }

        open fun Spawn() {}
        fun CallSpawn() {
            throw TODO_Exception()
            //            java.lang.Class/*idTypeInfo*/ type;
//
//            type = GetType();
//            CallSpawnFunc(type);
        }

        /*
         ================
         idClass::GetClassname

         Returns the text classname of the object.
         ================
         */
        fun GetClassname(): String? {
            return this.javaClass.simpleName
        }

        /*
         ================
         idClass::GetSuperclass

         Returns the text classname of the superclass.
         ================
         */
        fun GetSuperclass(): String? {
            throw TODO_Exception()
            //            java.lang.Class/*idTypeInfo*/ cls;
//
//            cls = GetType();
//            return cls.superclass;
        }

        fun FindUninitializedMemory() {
//#ifdef ID_DEBUG_UNINITIALIZED_MEMORY
//	unsigned long *ptr = ( ( unsigned long * )this ) - 1;
//	int size = *ptr;
//	assert( ( size & 3 ) == 0 );
//	size >>= 2;
//	for ( int i = 0; i < size; i++ ) {
//		if ( ptr[i] == 0xcdcdcdcd ) {
//			const char *varName = GetTypeVariableName( GetClassname(), i << 2 );
//			gameLocal.Warning( "type '%s' has uninitialized variable %s (offset %d)", GetClassname(), varName, i << 2 );
//		}
//	}
//#endif
        }

        open fun Save(savefile: idSaveGame?) {}
        open fun Restore(savefile: idRestoreGame?) {}
        fun RespondsTo(ev: idEventDef?): Boolean {
            return getEventCallBack(ev) != null //HACKME::7
            //            throw new TODO_Exception();
//            final idTypeInfo c;
//
//            assert (idEvent.initialized);
//            c = GetType();
//            return c.RespondsTo(ev);
        }

        fun PostEventMS(ev: idEventDef?, time: Int): Boolean {
            return PostEventArgs(ev, time, 0)
        }

        fun PostEventMS(ev: idEventDef?, time: Float, arg1: Any?): Boolean {
            return PostEventArgs(ev, time.toInt(), 1, idEventArg.toArg<Any?>(arg1))
        }

        fun PostEventMS(ev: idEventDef?, time: Int, arg1: Any?, arg2: Any?): Boolean {
            return PostEventArgs(ev, time, 2, idEventArg.toArg<Any?>(arg1), idEventArg.toArg<Any?>(arg2))
        }

        fun PostEventMS(ev: idEventDef?, time: Int, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return PostEventArgs(
                ev,
                time,
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun PostEventMS(ev: idEventDef?, time: Int, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return PostEventArgs(
                ev,
                time,
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun PostEventMS(
            ev: idEventDef?,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun PostEventMS(
            ev: idEventDef?,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun PostEventMS(
            ev: idEventDef?,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun PostEventMS(
            ev: idEventDef?,
            time: Int,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                time,
                8,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7),
                idEventArg.toArg<Any?>(arg8)
            )
        }

        fun PostEventSec(ev: idEventDef?, time: Float): Boolean {
            return PostEventArgs(ev, Math_h.SEC2MS(time).toInt(), 0)
        }

        fun PostEventSec(ev: idEventDef?, time: Float, arg1: idEventArg<*>?): Boolean {
            return PostEventArgs(ev, Math_h.SEC2MS(time).toInt(), 1, arg1)
        }

        fun PostEventSec(ev: idEventDef?, time: Float, arg1: Any?): Boolean {
            return PostEventArgs(ev, Math_h.SEC2MS(time).toInt(), 1, idEventArg.toArg<Any?>(arg1))
        }

        fun PostEventSec(ev: idEventDef?, time: Float, arg1: Any?, arg2: Any?): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                2,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2)
            )
        }

        fun PostEventSec(ev: idEventDef?, time: Float, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun PostEventSec(ev: idEventDef?, time: Float, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun PostEventSec(
            ev: idEventDef?,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun PostEventSec(
            ev: idEventDef?,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun PostEventSec(
            ev: idEventDef?,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun PostEventSec(
            ev: idEventDef?,
            time: Float,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return PostEventArgs(
                ev,
                Math_h.SEC2MS(time).toInt(),
                8,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7),
                idEventArg.toArg<Any?>(arg8)
            )
        }

        fun ProcessEvent(ev: idEventDef?): Boolean {
            return ProcessEventArgs(ev, 0)
        }

        fun ProcessEvent(ev: idEventDef?, arg1: Any?): Boolean {
            return ProcessEventArgs(ev, 1, idEventArg.toArg<Any?>(arg1))
        }

        fun ProcessEvent(ev: idEventDef?, arg1: idEntity?): Boolean {
            return ProcessEventArgs(ev, 1, idEventArg.toArg(arg1))
        }

        fun ProcessEvent(ev: idEventDef?, arg1: Any?, arg2: Any?): Boolean {
            return ProcessEventArgs(ev, 2, idEventArg.toArg<Any?>(arg1), idEventArg.toArg<Any?>(arg2))
        }

        fun ProcessEvent(ev: idEventDef?, arg1: Any?, arg2: Any?, arg3: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                3,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3)
            )
        }

        fun ProcessEvent(ev: idEventDef?, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                4,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4)
            )
        }

        fun ProcessEvent(ev: idEventDef?, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?, arg5: Any?): Boolean {
            return ProcessEventArgs(
                ev,
                5,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5)
            )
        }

        fun ProcessEvent(
            ev: idEventDef?,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                6,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6)
            )
        }

        fun ProcessEvent(
            ev: idEventDef?,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                7,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7)
            )
        }

        fun ProcessEvent(
            ev: idEventDef?,
            arg1: Any?,
            arg2: Any?,
            arg3: Any?,
            arg4: Any?,
            arg5: Any?,
            arg6: Any?,
            arg7: Any?,
            arg8: Any?
        ): Boolean {
            return ProcessEventArgs(
                ev,
                8,
                idEventArg.toArg<Any?>(arg1),
                idEventArg.toArg<Any?>(arg2),
                idEventArg.toArg<Any?>(arg3),
                idEventArg.toArg<Any?>(arg4),
                idEventArg.toArg<Any?>(arg5),
                idEventArg.toArg<Any?>(arg6),
                idEventArg.toArg<Any?>(arg7),
                idEventArg.toArg<Any?>(arg8)
            )
        }

        fun ProcessEventArgPtr(ev: idEventDef?, data: Array<idEventArg<*>?>?): Boolean {
            val num: Int
            val callback: eventCallback_t<*>?
            assert(ev != null)
            assert(idEvent.Companion.initialized)
            if (SysCvar.g_debugTriggers.GetBool() && ev === Entity.EV_Activate && this is idEntity) {
                val name: String?
                name =
                    if (data.get(0) != null && data.get(0).value as idClass? is idEntity) (data.get(0).value as idEntity?).GetName() else "NULL"
                Game_local.gameLocal.Printf(
                    "%d: '%s' activated by '%s'\n",
                    Game_local.gameLocal.framenum,
                    (this as idEntity).GetName(),
                    name
                )
            }
            num = ev.GetEventNum()
            callback = getEventCallBack(ev) //callback = c.eventMap[num];
            if (callback == null) {
                // we don't respond to this event, so ignore it
                return false
            }
            assert(Event.D_EVENT_MAXARGS == 8)
            when (ev.GetNumArgs()) {
                0, 1, 2, 3, 4, 5, 6, 7, 8 -> ////		typedef void ( idClass.*eventCallback_8_t )( const int, const int, const int, const int, const int, const int, const int, const int );
////		( this.*( eventCallback_8_t )callback )( data[ 0 ], data[ 1 ], data[ 2 ], data[ 3 ], data[ 4 ], data[ 5 ], data[ 6 ], data[ 7 ] );
//                    callback.run(data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
                    callback.accept(this, *data)
                else -> Game_local.gameLocal.Warning("Invalid formatspec on event '%s'", ev.GetName())
            }

// #endif
            return true
        }

        fun CancelEvents(ev: idEventDef?) {
            idEvent.Companion.CancelEvents(this, ev)
        }

        open fun Event_Remove() {
            //	delete this;//if only
            if (this is idBFGProjectile) delete(this) else if (this is idProjectile) delete(this) else if (this is idTrigger_Multi) delete(
                this
            ) else if (this is idTarget_Remove) delete(this) else if (this is idAI) delete(this) else if (this is idEntity) delete(
                this
            ) else if (this is idThread) idThread.Companion.delete(this as idThread) else throw TODO_Exception()
        }

        // Static functions
        /*
         ================
         idClass::Init

         Should be called after all idTypeInfos are initialized, so must be called
         manually upon game code initialization.  Tells all the idTypeInfos to initialize
         their event callback table for the associated class.  This should only be called
         once during the execution of the program or DLL.
         ================
         */
        open fun Init() {
            INIT()
        }

        abstract fun oSet(oGet: idClass?)
        private fun CallSpawnFunc(cls: idTypeInfo?): classSpawnFunc_t<*>? {
            val func: classSpawnFunc_t<*>?
            if (cls.zuper != null) { //TODO:rename super
                func = CallSpawnFunc(cls.zuper)
                if (func === cls.Spawn) {
                    // don't call the same function twice in a row.
                    // this can happen when subclasses don't have their own spawn function.
                    return func
                }
            }

//	( this.*cls.Spawn )();
            cls.Spawn.run()
            return cls.Spawn
        }

        private fun PostEventArgs(ev: idEventDef?, time: Int, numargs: Int, vararg args: idEventArg<*>?): Boolean {
            val c: Class<*>
            val event: idEvent
            assert(ev != null)
            if (!idEvent.Companion.initialized) {
                return false
            }

            //TODO:disabled for medicinal reasons
            c = this.javaClass
            if (TempDump.NOT(getEventCallBack(ev))) {
                // we don't respond to this event, so ignore it
                return false
            }

            // we service events on the client to avoid any bad code filling up the event pool
            // we don't want them processed usually, unless when the map is (re)loading.
            // we allow threads to run fine, though.
            if (Game_local.gameLocal.isClient && Game_local.gameLocal.GameState() != Game_local.gameState_t.GAMESTATE_STARTUP && this !is idThread) {
                return true
            }

//            va_start(args, numargs);
            event = idEvent.Companion.Alloc(ev, numargs, *args)
            //            va_end(args);

            //TODO:same as line #755
            event.Schedule(this, c, time)
            return true
        }

        private fun ProcessEventArgs(ev: idEventDef?, numargs: Int, vararg args: idEventArg<*>?): Boolean {
            var c: idTypeInfo
            var num: Int
            val data = arrayOfNulls<idEventArg<*>?>(Event.D_EVENT_MAXARGS)
            assert(ev != null)
            assert(idEvent.Companion.initialized)

            //TODO:same as PostEventArgs
//            c = GetType();
//            num = ev.GetEventNum();
//            if (NOT(c.eventMap[num])) {
//                // we don't respond to this event, so ignore it
//                return false;
//            }

//            va_start(args, numargs);
            idEvent.Companion.CopyArgs(ev, numargs, args, data)
            //            va_end(args);
            ProcessEventArgPtr(ev, data)
            return true
        }

        private fun Event_SafeRemove() {
            // Forces the remove to be done at a safe time
            PostEventMS(neo.Game.GameSys.Class.EV_Remove, 0)
        }

        /*
         ================
         idClass::DisplayInfo_f
         ================
         */
        class DisplayInfo_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                Game_local.gameLocal.Printf(
                    "Class memory status: %d bytes allocated in %d objects\n",
                    memused,
                    numobjects
                )
            }

            companion object {
                private val instance: cmdFunction_t? = DisplayInfo_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }

        /*
         ================
         idClass::ListClasses_f
         ================
         */
        class ListClasses_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                var type: idTypeInfo?
                Game_local.gameLocal.Printf("%-24s %-24s %-6s %-6s\n", "Classname", "Superclass", "Type", "Subclasses")
                Game_local.gameLocal.Printf("----------------------------------------------------------------------\n")
                i = 0
                while (i < types.Num()) {
                    type = types.get(i)
                    Game_local.gameLocal.Printf(
                        "%-24s %-24s %6d %6d\n",
                        type.classname,
                        type.superclass,
                        type.typeNum,
                        type.lastChild - type.typeNum
                    )
                    i++
                }
                Game_local.gameLocal.Printf("...%d classes", types.Num())
            }

            companion object {
                private val instance: cmdFunction_t? = ListClasses_f()
                fun getInstance(): cmdFunction_t? {
                    return instance
                }
            }
        }
    }

    /**
     * *********************************************************************
     *
     *
     * idTypeInfo
     *
     */
    @Deprecated(
        """use the native java classes instead.
      *********************************************************************"""
    )
    class idTypeInfo(
        classname: String?,
        superclass: String?,
        eventCallbacks: Array<idEventFunc<idClass?>?>?,
        CreateInstance: classSpawnFunc_t<*>?,
        Spawn: classSpawnFunc_t<*>?,
        Save: idClass_Save?,
        Restore: idClass_Restore?
    ) {
        //
        var CreateInstance: classSpawnFunc_t<*>?
        var Restore: idClass_Restore?
        var Save: idClass_Save?
        var Spawn: classSpawnFunc_t<*>?
        var classname: String?

        //
        var eventCallbacks: Array<idEventFunc<idClass?>?>?
        var eventMap: Array<eventCallback_t<*>?>?
        var freeEventMap: Boolean
        var lastChild: Int
        var next: idTypeInfo? = null

        //
        var node: idHierarchy<idTypeInfo?>? = null
        var superclass: String?
        var typeNum: Int
        var zuper: idTypeInfo?

        // ~idTypeInfo();
        /*
         ================
         idTypeInfo::Init

         Initializes the event callback table for the class.  Creates a 
         table for fast lookups of event functions.  Should only be called once.
         ================
         */
        fun Init() {
            var c: idTypeInfo?
            var def: Array<idEventFunc<idClass?>?>?
            var ev: Int
            var i: Int
            val set: BooleanArray
            val num: Int
            if (eventMap != null) {
                // we've already been initialized by a subclass
                return
            }

            // make sure our superclass is initialized first
            if (zuper != null && null == zuper.eventMap) {
                zuper.Init()
            }

            // add to our node hierarchy
            if (zuper != null) {
                node.ParentTo(zuper.node)
            } else {
                node.ParentTo(neo.Game.GameSys.Class.classHierarchy)
            }
            node.SetOwner(this)

            // keep track of the number of children below each class
            c = zuper
            while (c != null) {
                c.lastChild++
                c = c.zuper
            }

            // if we're not adding any new event callbacks, we can just use our superclass's table
            if ((null == eventCallbacks || TempDump.NOT(eventCallbacks.get(0).event)) && zuper != null) {
                eventMap = zuper.eventMap
                return
            }

            // set a flag so we know to delete the eventMap table
            freeEventMap = true

            // Allocate our new table.  It has to have as many entries as there
            // are events.  NOTE: could save some space by keeping track of the maximum
            // event that the class responds to and doing range checking.
            num = idEventDef.Companion.NumEventCommands()
            eventMap = arrayOfNulls<eventCallback_t<*>?>(num)
            //	memset( eventMap, 0, sizeof( eventCallback_t ) * num );
            neo.Game.GameSys.Class.eventCallbackMemory += num * 4

            // allocate temporary memory for flags so that the subclass's event callbacks
            // override the superclass's event callback
            set = BooleanArray(num)
            //	memset( set, 0, sizeof( bool ) * num );

            // go through the inheritence order and copies the event callback function into
            // a list indexed by the event number.  This allows fast lookups of
            // event functions.
            c = this
            while (c != null) {
                def = c.eventCallbacks
                if (null == def) {
                    c = c.zuper
                    continue
                }

                // go through each entry until we hit the NULL terminator
                i = 0
                while (def[i].event != null) {
                    ev = def[i].event.GetEventNum()
                    if (set[ev]) {
                        i++
                        continue
                    }
                    set[ev] = true
                    eventMap.get(ev) = def[i].function
                    i++
                }
                c = c.zuper
            }

//	delete[] set;
        }

        /*
         ================
         idTypeInfo::Shutdown

         Should only be called when DLL or EXE is being shutdown.
         Although it cleans up any allocated memory, it doesn't bother to remove itself 
         from the class list since the program is shutting down.
         ================
         */
        fun Shutdown() {
            // free up the memory used for event lookups
            if (eventMap != null) {
//		if ( freeEventMap ) {
//			delete[] eventMap;
//		}
                eventMap = null
            }
            typeNum = 0
            lastChild = 0
        }

        /*
         ================
         idTypeInfo::IsType

         Checks if the object's class is a subclass of the class defined by the 
         passed in idTypeInfo.
         ================
         */
        fun IsType(type: idTypeInfo?): Boolean {
            return typeNum >= type.typeNum && typeNum <= type.lastChild
        }

        fun IsType(type: Class<*>?): Boolean {
            throw TODO_Exception()
        }

        fun RespondsTo(ev: idEventDef?): Boolean {
            assert(idEvent.Companion.initialized)
            // we don't respond to this event
            return null != eventMap.get(ev.GetEventNum())
        }

        //
        //
        init {
            var type: idTypeInfo?
            var insert: idTypeInfo?
            this.classname = classname
            this.superclass = superclass
            this.eventCallbacks = eventCallbacks
            eventMap = null
            this.Spawn = Spawn
            this.Save = Save
            this.Restore = Restore
            this.CreateInstance = CreateInstance
            zuper = idClass.GetClass(superclass)
            freeEventMap = false
            typeNum = 0
            lastChild = 0

            // Check if any subclasses were initialized before their superclass
            type = neo.Game.GameSys.Class.typelist
            while (type != null) {
                if (type.zuper == null && TempDump.NOT(idStr.Companion.Cmp(type.superclass, this.classname).toDouble())
                    && idStr.Companion.Cmp(type.classname, "idClass") != 0
                ) {
                    type.zuper = this
                }
                type = type.next
            }

            // Insert sorted
            insert = neo.Game.GameSys.Class.typelist
            while (insert != null) {
                assert(idStr.Companion.Cmp(classname, insert.classname) != 0)
                if (idStr.Companion.Cmp(classname, insert.classname) < 0) {
                    next = insert
                    insert = this
                    break
                }
                insert = insert.next
            }
            if (null == insert) {
                insert = this
                next = null
            }
        }
    }
}