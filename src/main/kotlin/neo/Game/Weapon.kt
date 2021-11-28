package neo.Game

import neo.CM.CollisionModel.trace_s
import neo.CM.CollisionModel_local
import neo.Game.*
import neo.Game.AFEntity.idAFAttachment
import neo.Game.AI.AI.idAI
import neo.Game.Actor.idActor
import neo.Game.Animation.Anim
import neo.Game.Entity.idAnimatedEntity
import neo.Game.Entity.idEntity
import neo.Game.Entity.signalNum_t
import neo.Game.Game.refSound_t
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.eventCallback_t4
import neo.Game.GameSys.Class.eventCallback_t5
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idEntityPtr
import neo.Game.Game_local.idGameLocal
import neo.Game.Item.idMoveableItem
import neo.Game.MultiplayerGame.gameType_t
import neo.Game.Player.idPlayer
import neo.Game.Projectile.idDebris
import neo.Game.Projectile.idProjectile
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Thread.idThread
import neo.Game.Trigger.idTrigger
import neo.Renderer.*
import neo.Renderer.Material.surfTypes_t
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.framework.BuildDefines
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.framework.DeclManager.declType_t
import neo.framework.DeclParticle.idDeclParticle
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.BitMsg.idBitMsgDelta
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Lib
import neo.idlib.Lib.idLib
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.math.*
import neo.idlib.math.Angles.idAngles
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.ui.UserInterface
import java.nio.*

/**
 *
 */
object Weapon {
    //
    const val AMMO_NUMTYPES = 16
    val EV_Weapon_AddToClip: idEventDef? = idEventDef("addToClip", "d")
    val EV_Weapon_AllowDrop: idEventDef? = idEventDef("allowDrop", "d")
    val EV_Weapon_AmmoAvailable: idEventDef? = idEventDef("ammoAvailable", null, 'f')
    val EV_Weapon_AmmoInClip: idEventDef? = idEventDef("ammoInClip", null, 'f')
    val EV_Weapon_AutoReload: idEventDef? = idEventDef("autoReload", null, 'f')

    //
    //
    // event defs
    //
    val EV_Weapon_Clear: idEventDef? = idEventDef("<clear>")
    val EV_Weapon_ClipSize: idEventDef? = idEventDef("clipSize", null, 'f')
    val EV_Weapon_CreateProjectile: idEventDef? = idEventDef("createProjectile", null, 'e')
    val EV_Weapon_EjectBrass: idEventDef? = idEventDef("ejectBrass")
    val EV_Weapon_Flashlight: idEventDef? = idEventDef("flashlight", "d")
    val EV_Weapon_GetOwner: idEventDef? = idEventDef("getOwner", null, 'e')
    val EV_Weapon_GetWorldModel: idEventDef? = idEventDef("getWorldModel", null, 'e')
    val EV_Weapon_IsInvisible: idEventDef? = idEventDef("isInvisible", null, 'f')
    val EV_Weapon_LaunchProjectiles: idEventDef? = idEventDef("launchProjectiles", "dffff")
    val EV_Weapon_Melee: idEventDef? = idEventDef("melee", null, 'd')
    val EV_Weapon_NetEndReload: idEventDef? = idEventDef("netEndReload")
    val EV_Weapon_NetReload: idEventDef? = idEventDef("netReload")
    val EV_Weapon_Next: idEventDef? = idEventDef("nextWeapon")
    val EV_Weapon_State: idEventDef? = idEventDef("weaponState", "sd")
    val EV_Weapon_TotalAmmoCount: idEventDef? = idEventDef("totalAmmoCount", null, 'f')
    val EV_Weapon_UseAmmo: idEventDef? = idEventDef("useAmmo", "d")
    val EV_Weapon_WeaponHolstered: idEventDef? = idEventDef("weaponHolstered")
    val EV_Weapon_WeaponLowering: idEventDef? = idEventDef("weaponLowering")
    val EV_Weapon_WeaponOutOfAmmo: idEventDef? = idEventDef("weaponOutOfAmmo")
    val EV_Weapon_WeaponReady: idEventDef? = idEventDef("weaponReady")
    val EV_Weapon_WeaponReloading: idEventDef? = idEventDef("weaponReloading")
    val EV_Weapon_WeaponRising: idEventDef? = idEventDef("weaponRising")
    const val LIGHTID_VIEW_MUZZLE_FLASH = 100

    //
    const val LIGHTID_WORLD_MUZZLE_FLASH = 1

    /*
     ===============================================================================

     Player Weapon

     ===============================================================================
     */
    enum class weaponStatus_t {
        WP_READY, WP_OUTOFAMMO, WP_RELOAD, WP_HOLSTERED, WP_RISING, WP_LOWERING
    }

    /* **********************************************************************

     idWeapon  
	
     ***********************************************************************/
    class idWeapon : idAnimatedEntity() {
        companion object {
            // enum {
            val EVENT_RELOAD: Int = idEntity.Companion.EVENT_MAXEVENTS
            val EVENT_ENDRELOAD = EVENT_RELOAD + 1
            val EVENT_CHANGESKIN = EVENT_RELOAD + 2
            val EVENT_MAXEVENTS = EVENT_RELOAD + 3

            // CLASS_PROTOTYPE( idWeapon );
            private val eventCallbacks: MutableMap<idEventDef?, eventCallback_t<*>?>? = HashMap()
            fun CacheWeapon(weaponName: String?) {
                val weaponDef: idDeclEntityDef?
                val brassDefName: String?
                val clipModelName = idStr()
                val trm = idTraceModel()
                val guiName: String?
                weaponDef = Game_local.gameLocal.FindEntityDef(weaponName, false)
                if (null == weaponDef) {
                    return
                }

                // precache the brass collision model
                brassDefName = weaponDef.dict.GetString("def_ejectBrass")
                if (TempDump.isNotNullOrEmpty(brassDefName)) {
                    val brassDef = Game_local.gameLocal.FindEntityDef(brassDefName, false)
                    if (brassDef != null) {
                        brassDef.dict.GetString("clipmodel", "", clipModelName)
                        if (!TempDump.isNotNullOrEmpty(clipModelName)) {
                            clipModelName.oSet(brassDef.dict.GetString("model")) // use the visual model
                        }
                        // load the trace model
                        CollisionModel_local.collisionModelManager.TrmFromModel(clipModelName, trm)
                    }
                }
                guiName = weaponDef.dict.GetString("gui")
                if (TempDump.isNotNullOrEmpty(guiName)) {
                    UserInterface.uiManager.FindGui(guiName, true, false, true)
                }
            }

            /* **********************************************************************

         Ammo

         ***********************************************************************/
            fun  /*ammo_t*/GetAmmoNumForName(ammoname: String?): Int {
                val num = CInt()
                val ammoDict: idDict?
                assert(ammoname != null)
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_types", false)
                if (null == ammoDict) {
                    idGameLocal.Companion.Error("Could not find entity definition for 'ammo_types'\n")
                }
                if (!TempDump.isNotNullOrEmpty(ammoname)) {
                    return 0
                }
                if (!ammoDict.GetInt(ammoname, "-1", num)) {
                    idGameLocal.Companion.Error("Unknown ammo type '%s'", ammoname)
                }
                if (num.getVal() < 0 || num.getVal() >= Weapon.AMMO_NUMTYPES) {
                    idGameLocal.Companion.Error(
                        "Ammo type '%s' value out of range.  Maximum ammo types is %d.\n",
                        ammoname,
                        Weapon.AMMO_NUMTYPES
                    )
                }
                return num.getVal()
            }

            fun GetAmmoNameForNum(   /*ammo_t*/ammonum: Int): String? {
                var i: Int
                val num: Int
                val ammoDict: idDict?
                var kv: idKeyValue?
                //	char []text = new char[32 ];
                val text: String
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_types", false)
                if (null == ammoDict) {
                    idGameLocal.Companion.Error("Could not find entity definition for 'ammo_types'\n")
                }
                text = String.format("%d", ammonum)
                num = ammoDict.GetNumKeyVals()
                i = 0
                while (i < num) {
                    kv = ammoDict.GetKeyVal(i)
                    if (kv.GetValue() == text) {
                        return kv.GetKey().toString()
                    }
                    i++
                }
                return null
            }

            fun GetAmmoPickupNameForNum(   /*ammo_t*/ammonum: Int): String? {
                var i: Int
                val num: Int
                val ammoDict: idDict?
                var kv: idKeyValue?
                ammoDict = Game_local.gameLocal.FindEntityDefDict("ammo_names", false)
                if (null == ammoDict) {
                    idGameLocal.Companion.Error("Could not find entity definition for 'ammo_names'\n")
                }
                val name = GetAmmoNameForNum(ammonum)
                if (TempDump.isNotNullOrEmpty(name)) {
                    num = ammoDict.GetNumKeyVals()
                    i = 0
                    while (i < num) {
                        kv = ammoDict.GetKeyVal(i)
                        if (idStr.Companion.Icmp(kv.GetKey().toString(), name) == 0) {
                            return kv.GetValue().toString()
                        }
                        i++
                    }
                }
                return ""
            }

            fun getEventCallBacks(): MutableMap<idEventDef?, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAnimatedEntity.Companion.getEventCallBacks())
                eventCallbacks[Weapon.EV_Weapon_Clear] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_Clear() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_GetOwner] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_GetOwner() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_State] =
                    eventCallback_t2<idWeapon?> { obj: T?, _statename: idEventArg<*>? ->
                        neo.Game.obj.Event_WeaponState(neo.Game._statename)
                    } as eventCallback_t2<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponReady] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponReady() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponOutOfAmmo] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponOutOfAmmo() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponReloading] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponReloading() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponHolstered] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponHolstered() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponRising] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponRising() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_WeaponLowering] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_WeaponLowering() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_UseAmmo] =
                    eventCallback_t1<idWeapon?> { obj: T?, _amount: idEventArg<*>? -> neo.Game.obj.Event_UseAmmo(neo.Game._amount) } as eventCallback_t1<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_AddToClip] =
                    eventCallback_t1<idWeapon?> { obj: T?, amount: idEventArg<*>? -> neo.Game.obj.Event_AddToClip(neo.Game.amount) } as eventCallback_t1<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_AmmoInClip] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_AmmoInClip() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_AmmoAvailable] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_AmmoAvailable() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_TotalAmmoCount] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_TotalAmmoCount() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_ClipSize] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_ClipSize() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Actor.AI_PlayAnim] =
                    eventCallback_t2<idWeapon?> { obj: T?, _channel: idEventArg<*>? -> neo.Game.obj.Event_PlayAnim(neo.Game._channel) } as eventCallback_t2<idWeapon?>
                eventCallbacks[Actor.AI_PlayCycle] =
                    eventCallback_t2<idWeapon?> { obj: T?, _channel: idEventArg<*>? -> neo.Game.obj.Event_PlayCycle(neo.Game._channel) } as eventCallback_t2<idWeapon?>
                eventCallbacks[Actor.AI_SetBlendFrames] =
                    eventCallback_t2<idWeapon?> { obj: T?, channel: idEventArg<*>? ->
                        neo.Game.obj.Event_SetBlendFrames(neo.Game.channel)
                    } as eventCallback_t2<idWeapon?>
                eventCallbacks[Actor.AI_GetBlendFrames] =
                    eventCallback_t1<idWeapon?> { obj: T?, channel: idEventArg<*>? ->
                        neo.Game.obj.Event_GetBlendFrames(neo.Game.channel)
                    } as eventCallback_t1<idWeapon?>
                eventCallbacks[Actor.AI_AnimDone] =
                    eventCallback_t2<idWeapon?> { obj: T?, channel: idEventArg<*>? -> neo.Game.obj.Event_AnimDone(neo.Game.channel) } as eventCallback_t2<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_Next] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_Next() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Entity.EV_SetSkin] =
                    eventCallback_t1<idWeapon?> { obj: T?, _skinname: idEventArg<*>? -> neo.Game.obj.Event_SetSkin(neo.Game._skinname) } as eventCallback_t1<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_Flashlight] =
                    eventCallback_t1<idWeapon?> { obj: T?, enable: idEventArg<*>? -> neo.Game.obj.Event_Flashlight(neo.Game.enable) } as eventCallback_t1<idWeapon?>
                eventCallbacks[Light.EV_Light_GetLightParm] =
                    eventCallback_t1<idWeapon?> { obj: T?, _parmnum: idEventArg<*>? ->
                        neo.Game.obj.Event_GetLightParm(neo.Game._parmnum)
                    } as eventCallback_t1<idWeapon?>
                eventCallbacks[Light.EV_Light_SetLightParm] =
                    eventCallback_t2<idWeapon?> { obj: T?, _parmnum: idEventArg<*>? ->
                        neo.Game.obj.Event_SetLightParm(neo.Game._parmnum)
                    } as eventCallback_t2<idWeapon?>
                eventCallbacks[Light.EV_Light_SetLightParms] =
                    eventCallback_t4<idWeapon?> { obj: T?, parm0: idEventArg<*>? -> neo.Game.obj.Event_SetLightParms(neo.Game.parm0) } as eventCallback_t4<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_LaunchProjectiles] =
                    eventCallback_t5<idWeapon?> { obj: T?, _num_projectiles: idEventArg<*>? ->
                        neo.Game.obj.Event_LaunchProjectiles(neo.Game._num_projectiles)
                    } as eventCallback_t5<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_CreateProjectile] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_CreateProjectile() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_EjectBrass] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_EjectBrass() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_Melee] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_Melee() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_GetWorldModel] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_GetWorldModel() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_AllowDrop] =
                    eventCallback_t1<idWeapon?> { obj: T?, allow: idEventArg<*>? -> neo.Game.obj.Event_AllowDrop(neo.Game.allow) } as eventCallback_t1<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_AutoReload] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_AutoReload() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_NetReload] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_NetReload() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_IsInvisible] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_IsInvisible() } as eventCallback_t0<idWeapon?>
                eventCallbacks[Weapon.EV_Weapon_NetEndReload] =
                    eventCallback_t0<idWeapon?> { obj: T? -> neo.Game.obj.Event_NetEndReload() } as eventCallback_t0<idWeapon?>
            }
        }

        // script control
        private val WEAPON_ATTACK: idScriptBool? = idScriptBool()
        private val WEAPON_LOWERWEAPON: idScriptBool? = idScriptBool()
        private val WEAPON_NETENDRELOAD: idScriptBool? = idScriptBool()
        private val WEAPON_NETFIRING: idScriptBool? = idScriptBool()
        private val WEAPON_NETRELOAD: idScriptBool? = idScriptBool()
        private val WEAPON_RAISEWEAPON: idScriptBool? = idScriptBool()
        private val WEAPON_RELOAD: idScriptBool? = idScriptBool()
        private val brassDict: idDict?

        //
        private val flashColor: idVec3?
        private val icon: idStr?
        private val idealState: idStr?
        private val meleeDefName: idStr?
        private val muzzleAxis: idMat3?

        //
        // the muzzle bone's position, used for launching projectiles and trailing smoke
        private val muzzleOrigin: idVec3?
        private val muzzle_kick_angles: idAngles?
        private val muzzle_kick_offset: idVec3?

        //
        private val nozzleGlowColor // color of the nozzle glow
                : idVec3?
        private val playerViewAxis: idMat3?

        //
        // these are the player render view parms, which include bobbing
        private val playerViewOrigin: idVec3?
        private val projectileDict: idDict?

        //
        private val pushVelocity: idVec3?
        private val state: idStr?
        private val strikePos // position of last melee strike
                : idVec3?
        private val viewWeaponAxis: idMat3?

        //
        // the view weapon render entity parms
        private val viewWeaponOrigin: idVec3?

        //
        //
        private val worldModel: idEntityPtr<idAnimatedEntity?>?
        private var allowDrop: Boolean
        private var ammoClip = 0
        private var ammoRequired // amount of ammo to use each shot.  0 means weapon doesn't need ammo.
                = 0

        //
        // ammo management
        private var   /*ammo_t*/ammoType = 0
        private var animBlendFrames = 0
        private var animDoneTime = 0

        //
        // joints from models
        private var   /*jointHandle_t*/barrelJointView = 0
        private var   /*jointHandle_t*/barrelJointWorld = 0

        //
        // berserk
        private var berserk: Int
        private var brassDelay: Int
        private var clipSize // 0 means no reload
                = 0
        private var continuousSmoke // if smoke is continuous ( chainsaw )
                = false
        private var disabled = false
        private var   /*jointHandle_t*/ejectJointView = 0
        private var   /*jointHandle_t*/ejectJointWorld = 0
        private var   /*jointHandle_t*/flashJointView = 0

        //
        private var   /*jointHandle_t*/flashJointWorld = 0
        private var flashTime = 0

        //
        // view weapon gui light
        private var guiLight: renderLight_s?
        private var guiLightHandle: Int
        private var   /*jointHandle_t*/guiLightJointView = 0

        //
        // effects
        private var hasBloodSplat = false
        private var hide = false
        private var hideDistance = 0f
        private var hideEnd = 0f
        private var hideOffset = 0f
        private var hideStart = 0f
        private var hideStartTime = 0

        //
        // hiding (for GUIs and NPCs)
        private var hideTime = 0

        // a projectile is launched
        // mp client
        private var isFiring = false
        private var isLinked = false

        //
        // weapon kick
        private var kick_endtime = 0
        private var lastAttack // last time an attack occured
                = 0
        private var lightOn = false
        private var lowAmmo // if ammo in clip hits this threshold, snd_
                = 0
        private var meleeDef: idDeclEntityDef? = null
        private var meleeDistance = 0f

        //
        // muzzle flash
        private var muzzleFlash // positioned on view weapon bone
                : renderLight_s?
        private var muzzleFlashEnd: Int
        private var muzzleFlashHandle: Int
        private var muzzle_kick_maxtime = 0
        private var muzzle_kick_time = 0
        private var nextStrikeFx // used for sound and decal ( may use for strike smoke too )
                = 0

        //
        // nozzle effects
        private var nozzleFx // does this use nozzle effects ( parm5 at rest, parm6 firing )
                = false

        // this also assumes a nozzle light atm
        private var nozzleFxFade // time it takes to fade between the effects
                = 0
        private var nozzleGlow // nozzle light
                : renderLight_s?
        private var nozzleGlowHandle // handle for nozzle light
                : Int
        private var nozzleGlowRadius // radius of glow light
                = 0f
        private var nozzleGlowShader // shader for glow light
                : idMaterial? = null

        //
        private var owner: idPlayer? = null
        private var powerAmmo // true if the clip reduction is a factor of the power setting when
                = false

        //
        // precreated projectile
        private var projectileEnt: idEntity? = null
        private var silent_fire = false

        //
        // sound
        private var sndHum: idSoundShader? = null
        private var status: weaponStatus_t? = null
        private var strikeAxis // axis of last melee strike
                : idMat3? = null
        private var strikeSmoke // striking something in melee
                : idDeclParticle? = null
        private var strikeSmokeStartTime // timing
                = 0
        private var thread: idThread?
        private var   /*jointHandle_t*/ventLightJointView = 0

        //
        // weighting for viewmodel angles
        private var weaponAngleOffsetAverages = 0
        private var weaponAngleOffsetMax = 0f
        private var weaponAngleOffsetScale = 0f

        //
        // weapon definition
        // we maintain local copies of the projectile and brass dictionaries so they
        // do not have to be copied across the DLL boundary when entities are spawned
        private var weaponDef: idDeclEntityDef?
        private var weaponOffsetScale = 0f
        private var weaponOffsetTime = 0f

        //
        // new style muzzle smokes
        private var weaponSmoke // null if it doesn't smoke
                : idDeclParticle? = null
        private var weaponSmokeStartTime // set to gameLocal.time every weapon fire
                = 0

        // virtual					~idWeapon();
        //
        private var worldMuzzleFlash // positioned on world weapon bone
                : renderLight_s?
        private var worldMuzzleFlashHandle: Int

        //
        // zoom
        private var zoomFov // variable zoom fov per weapon
                = 0

        // Init
        override fun Spawn() {
            super.Spawn()
            if (!Game_local.gameLocal.isClient) {
                // setup the world model
                worldModel.oSet(
                    Game_local.gameLocal.SpawnEntityType(
                        idAnimatedEntity::class.java,
                        null
                    ) as idAnimatedEntity
                )
                worldModel.GetEntity().fl.networkSync = true
            }
            thread = idThread()
            thread.ManualDelete()
            thread.ManualControl()
        }

        /*
         ================
         idWeapon::SetOwner

         Only called at player spawn time, not each weapon switch
         ================
         */
        fun SetOwner(_owner: idPlayer?) {
            assert(null == owner)
            owner = _owner
            SetName(Str.va("%s_weapon", owner.name))
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().SetName(Str.va("%s_weapon_worldmodel", owner.name))
            }
        }

        fun GetOwner(): idPlayer? {
            return owner
        }

        /*
         ================
         idWeapon::ShouldConstructScriptObjectAtSpawn

         Called during idEntity::Spawn to see if it should construct the script object or not.
         Overridden by subclasses that need to spawn the script object themselves.
         ================
         */
        fun ShouldfinalructScriptObjectAtSpawn(): Boolean {
            return false
        }

        // save games
        override fun Save(savefile: idSaveGame?) {                    // archives object for save game file
            savefile.WriteInt(TempDump.etoi(status))
            savefile.WriteObject(thread)
            savefile.WriteString(state)
            savefile.WriteString(idealState)
            savefile.WriteInt(animBlendFrames)
            savefile.WriteInt(animDoneTime)
            savefile.WriteBool(isLinked)
            savefile.WriteObject(owner)
            worldModel.Save(savefile)
            savefile.WriteInt(hideTime)
            savefile.WriteFloat(hideDistance)
            savefile.WriteInt(hideStartTime)
            savefile.WriteFloat(hideStart)
            savefile.WriteFloat(hideEnd)
            savefile.WriteFloat(hideOffset)
            savefile.WriteBool(hide)
            savefile.WriteBool(disabled)
            savefile.WriteInt(berserk)
            savefile.WriteVec3(playerViewOrigin)
            savefile.WriteMat3(playerViewAxis)
            savefile.WriteVec3(viewWeaponOrigin)
            savefile.WriteMat3(viewWeaponAxis)
            savefile.WriteVec3(muzzleOrigin)
            savefile.WriteMat3(muzzleAxis)
            savefile.WriteVec3(pushVelocity)
            savefile.WriteString(weaponDef.GetName())
            savefile.WriteFloat(meleeDistance)
            savefile.WriteString(meleeDefName)
            savefile.WriteInt(brassDelay)
            savefile.WriteString(icon)
            savefile.WriteInt(guiLightHandle)
            savefile.WriteRenderLight(guiLight)
            savefile.WriteInt(muzzleFlashHandle)
            savefile.WriteRenderLight(muzzleFlash)
            savefile.WriteInt(worldMuzzleFlashHandle)
            savefile.WriteRenderLight(worldMuzzleFlash)
            savefile.WriteVec3(flashColor)
            savefile.WriteInt(muzzleFlashEnd)
            savefile.WriteInt(flashTime)
            savefile.WriteBool(lightOn)
            savefile.WriteBool(silent_fire)
            savefile.WriteInt(kick_endtime)
            savefile.WriteInt(muzzle_kick_time)
            savefile.WriteInt(muzzle_kick_maxtime)
            savefile.WriteAngles(muzzle_kick_angles)
            savefile.WriteVec3(muzzle_kick_offset)
            savefile.WriteInt(ammoType)
            savefile.WriteInt(ammoRequired)
            savefile.WriteInt(clipSize)
            savefile.WriteInt(ammoClip)
            savefile.WriteInt(lowAmmo)
            savefile.WriteBool(powerAmmo)

            // savegames <= 17
            savefile.WriteInt(0)
            savefile.WriteInt(zoomFov)
            savefile.WriteJoint(barrelJointView)
            savefile.WriteJoint(flashJointView)
            savefile.WriteJoint(ejectJointView)
            savefile.WriteJoint(guiLightJointView)
            savefile.WriteJoint(ventLightJointView)
            savefile.WriteJoint(flashJointWorld)
            savefile.WriteJoint(barrelJointWorld)
            savefile.WriteJoint(ejectJointWorld)
            savefile.WriteBool(hasBloodSplat)
            savefile.WriteSoundShader(sndHum)
            savefile.WriteParticle(weaponSmoke)
            savefile.WriteInt(weaponSmokeStartTime)
            savefile.WriteBool(continuousSmoke)
            savefile.WriteParticle(strikeSmoke)
            savefile.WriteInt(strikeSmokeStartTime)
            savefile.WriteVec3(strikePos)
            savefile.WriteMat3(strikeAxis)
            savefile.WriteInt(nextStrikeFx)
            savefile.WriteBool(nozzleFx)
            savefile.WriteInt(nozzleFxFade)
            savefile.WriteInt(lastAttack)
            savefile.WriteInt(nozzleGlowHandle)
            savefile.WriteRenderLight(nozzleGlow)
            savefile.WriteVec3(nozzleGlowColor)
            savefile.WriteMaterial(nozzleGlowShader)
            savefile.WriteFloat(nozzleGlowRadius)
            savefile.WriteInt(weaponAngleOffsetAverages)
            savefile.WriteFloat(weaponAngleOffsetScale)
            savefile.WriteFloat(weaponAngleOffsetMax)
            savefile.WriteFloat(weaponOffsetTime)
            savefile.WriteFloat(weaponOffsetScale)
            savefile.WriteBool(allowDrop)
            savefile.WriteObject(projectileEnt)
        }

        override fun Restore(savefile: idRestoreGame?) {                    // unarchives object from save game file
            status = Weapon.weaponStatus_t.values()[savefile.ReadInt()]
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/thread)
            savefile.ReadString(state)
            savefile.ReadString(idealState)
            animBlendFrames = savefile.ReadInt()
            animDoneTime = savefile.ReadInt()
            isLinked = savefile.ReadBool()

            // Re-link script fields
            WEAPON_ATTACK.LinkTo(scriptObject, "WEAPON_ATTACK")
            WEAPON_RELOAD.LinkTo(scriptObject, "WEAPON_RELOAD")
            WEAPON_NETRELOAD.LinkTo(scriptObject, "WEAPON_NETRELOAD")
            WEAPON_NETENDRELOAD.LinkTo(scriptObject, "WEAPON_NETENDRELOAD")
            WEAPON_NETFIRING.LinkTo(scriptObject, "WEAPON_NETFIRING")
            WEAPON_RAISEWEAPON.LinkTo(scriptObject, "WEAPON_RAISEWEAPON")
            WEAPON_LOWERWEAPON.LinkTo(scriptObject, "WEAPON_LOWERWEAPON")
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/owner)
            worldModel.Restore(savefile)
            hideTime = savefile.ReadInt()
            hideDistance = savefile.ReadFloat()
            hideStartTime = savefile.ReadInt()
            hideStart = savefile.ReadFloat()
            hideEnd = savefile.ReadFloat()
            hideOffset = savefile.ReadFloat()
            hide = savefile.ReadBool()
            disabled = savefile.ReadBool()
            berserk = savefile.ReadInt()
            savefile.ReadVec3(playerViewOrigin)
            savefile.ReadMat3(playerViewAxis)
            savefile.ReadVec3(viewWeaponOrigin)
            savefile.ReadMat3(viewWeaponAxis)
            savefile.ReadVec3(muzzleOrigin)
            savefile.ReadMat3(muzzleAxis)
            savefile.ReadVec3(pushVelocity)
            val objectname = idStr()
            savefile.ReadString(objectname)
            weaponDef = Game_local.gameLocal.FindEntityDef(objectname.toString())
            meleeDef = Game_local.gameLocal.FindEntityDef(weaponDef.dict.GetString("def_melee"), false)
            val projectileDef = Game_local.gameLocal.FindEntityDef(weaponDef.dict.GetString("def_projectile"), false)
            if (projectileDef != null) {
                projectileDict.oSet(projectileDef.dict)
            } else {
                projectileDict.Clear()
            }
            val brassDef = Game_local.gameLocal.FindEntityDef(weaponDef.dict.GetString("def_ejectBrass"), false)
            if (brassDef != null) {
                brassDict.oSet(brassDef.dict)
            } else {
                brassDict.Clear()
            }
            meleeDistance = savefile.ReadFloat()
            savefile.ReadString(meleeDefName)
            brassDelay = savefile.ReadInt()
            savefile.ReadString(icon)
            guiLightHandle = savefile.ReadInt()
            savefile.ReadRenderLight(guiLight)
            muzzleFlashHandle = savefile.ReadInt()
            savefile.ReadRenderLight(muzzleFlash)
            worldMuzzleFlashHandle = savefile.ReadInt()
            savefile.ReadRenderLight(worldMuzzleFlash)
            savefile.ReadVec3(flashColor)
            muzzleFlashEnd = savefile.ReadInt()
            flashTime = savefile.ReadInt()
            lightOn = savefile.ReadBool()
            silent_fire = savefile.ReadBool()
            kick_endtime = savefile.ReadInt()
            muzzle_kick_time = savefile.ReadInt()
            muzzle_kick_maxtime = savefile.ReadInt()
            savefile.ReadAngles(muzzle_kick_angles)
            savefile.ReadVec3(muzzle_kick_offset)
            ammoType = savefile.ReadInt()
            ammoRequired = savefile.ReadInt()
            clipSize = savefile.ReadInt()
            ammoClip = savefile.ReadInt()
            lowAmmo = savefile.ReadInt()
            powerAmmo = savefile.ReadBool()

            // savegame versions <= 17
            val foo: Int
            foo = savefile.ReadInt()
            zoomFov = savefile.ReadInt()
            barrelJointView = savefile.ReadJoint()
            flashJointView = savefile.ReadJoint()
            ejectJointView = savefile.ReadJoint()
            guiLightJointView = savefile.ReadJoint()
            ventLightJointView = savefile.ReadJoint()
            flashJointWorld = savefile.ReadJoint()
            barrelJointWorld = savefile.ReadJoint()
            ejectJointWorld = savefile.ReadJoint()
            hasBloodSplat = savefile.ReadBool()
            savefile.ReadSoundShader(sndHum)
            savefile.ReadParticle(weaponSmoke)
            weaponSmokeStartTime = savefile.ReadInt()
            continuousSmoke = savefile.ReadBool()
            savefile.ReadParticle(strikeSmoke)
            strikeSmokeStartTime = savefile.ReadInt()
            savefile.ReadVec3(strikePos)
            savefile.ReadMat3(strikeAxis)
            nextStrikeFx = savefile.ReadInt()
            nozzleFx = savefile.ReadBool()
            nozzleFxFade = savefile.ReadInt()
            lastAttack = savefile.ReadInt()
            nozzleGlowHandle = savefile.ReadInt()
            savefile.ReadRenderLight(nozzleGlow)
            savefile.ReadVec3(nozzleGlowColor)
            savefile.ReadMaterial(nozzleGlowShader)
            nozzleGlowRadius = savefile.ReadFloat()
            weaponAngleOffsetAverages = savefile.ReadInt()
            weaponAngleOffsetScale = savefile.ReadFloat()
            weaponAngleOffsetMax = savefile.ReadFloat()
            weaponOffsetTime = savefile.ReadFloat()
            weaponOffsetScale = savefile.ReadFloat()
            allowDrop = savefile.ReadBool()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/projectileEnt)
        }

        /* **********************************************************************

         Weapon definition management

         ***********************************************************************/
        fun Clear() {
            CancelEvents(Weapon.EV_Weapon_Clear)
            DeconstructScriptObject()
            scriptObject.Free()
            WEAPON_ATTACK.Unlink()
            WEAPON_RELOAD.Unlink()
            WEAPON_NETRELOAD.Unlink()
            WEAPON_NETENDRELOAD.Unlink()
            WEAPON_NETFIRING.Unlink()
            WEAPON_RAISEWEAPON.Unlink()
            WEAPON_LOWERWEAPON.Unlink()
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld.FreeLightDef(muzzleFlashHandle)
                muzzleFlashHandle = -1
            }
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld.FreeLightDef(muzzleFlashHandle)
                muzzleFlashHandle = -1
            }
            if (worldMuzzleFlashHandle != -1) {
                Game_local.gameRenderWorld.FreeLightDef(worldMuzzleFlashHandle)
                worldMuzzleFlashHandle = -1
            }
            if (guiLightHandle != -1) {
                Game_local.gameRenderWorld.FreeLightDef(guiLightHandle)
                guiLightHandle = -1
            }
            if (nozzleGlowHandle != -1) {
                Game_local.gameRenderWorld.FreeLightDef(nozzleGlowHandle)
                nozzleGlowHandle = -1
            }

//	memset( &renderEntity, 0, sizeof( renderEntity ) );
            renderEntity = renderEntity_s()
            renderEntity.entityNum = entityNumber
            renderEntity.noShadow = true
            renderEntity.noSelfShadow = true
            renderEntity.customSkin = null

            // set default shader parms
            renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] = 1.0f
            renderEntity.shaderParms[3] = 1.0f
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] = 0.0f
            renderEntity.shaderParms[5] = 0.0f
            renderEntity.shaderParms[6] = 0.0f
            renderEntity.shaderParms[7] = 0.0f
            if (refSound.referenceSound != null) {
                refSound.referenceSound.Free(true)
            }
            //	memset( &refSound, 0, sizeof( refSound_t ) );
            refSound = refSound_t()

            // setting diversity to 0 results in no random sound.  -1 indicates random.
            refSound.diversity = -1.0f
            if (owner != null) {
                // don't spatialize the weapon sounds
                refSound.listenerId = owner.GetListenerId()
            }

            // clear out the sounds from our spawnargs since we'll copy them from the weapon def
            var kv = spawnArgs.MatchPrefix("snd_")
            while (kv != null) {
                spawnArgs.Delete(kv.GetKey())
                kv = spawnArgs.MatchPrefix("snd_")
            }
            hideTime = 300
            hideDistance = -15.0f
            hideStartTime = Game_local.gameLocal.time - hideTime
            hideStart = 0.0f
            hideEnd = 0.0f
            hideOffset = 0.0f
            hide = false
            disabled = false
            weaponSmoke = null
            weaponSmokeStartTime = 0
            continuousSmoke = false
            strikeSmoke = null
            strikeSmokeStartTime = 0
            strikePos.Zero()
            strikeAxis = idMat3.Companion.getMat3_identity()
            nextStrikeFx = 0
            icon.oSet("")
            playerViewAxis.Identity()
            playerViewOrigin.Zero()
            viewWeaponAxis.Identity()
            viewWeaponOrigin.Zero()
            muzzleAxis.Identity()
            muzzleOrigin.Zero()
            pushVelocity.Zero()
            status = weaponStatus_t.WP_HOLSTERED
            state.oSet("")
            idealState.oSet("")
            animBlendFrames = 0
            animDoneTime = 0
            projectileDict.Clear()
            meleeDef = null
            meleeDefName.oSet("")
            meleeDistance = 0.0f
            brassDict.Clear()
            flashTime = 250
            lightOn = false
            silent_fire = false
            ammoType = 0
            ammoRequired = 0
            ammoClip = 0
            clipSize = 0
            lowAmmo = 0
            powerAmmo = false
            kick_endtime = 0
            muzzle_kick_time = 0
            muzzle_kick_maxtime = 0
            muzzle_kick_angles.Zero()
            muzzle_kick_offset.Zero()
            zoomFov = 90
            barrelJointView = Model.INVALID_JOINT
            flashJointView = Model.INVALID_JOINT
            ejectJointView = Model.INVALID_JOINT
            guiLightJointView = Model.INVALID_JOINT
            ventLightJointView = Model.INVALID_JOINT
            barrelJointWorld = Model.INVALID_JOINT
            flashJointWorld = Model.INVALID_JOINT
            ejectJointWorld = Model.INVALID_JOINT
            hasBloodSplat = false
            nozzleFx = false
            nozzleFxFade = 1500
            lastAttack = 0
            nozzleGlowHandle = -1
            nozzleGlowShader = null
            nozzleGlowRadius = 10f
            nozzleGlowColor.Zero()
            weaponAngleOffsetAverages = 0
            weaponAngleOffsetScale = 0.0f
            weaponAngleOffsetMax = 0.0f
            weaponOffsetTime = 0.0f
            weaponOffsetScale = 0.0f
            allowDrop = true
            animator.ClearAllAnims(Game_local.gameLocal.time, 0)
            FreeModelDef()
            sndHum = null
            isLinked = false
            projectileEnt = null
            isFiring = false
        }

        fun GetWeaponDef(objectName: String?, ammoinclip: Int) {
            val shader = arrayOf<String?>(null)
            val objectType = arrayOf<String?>(null)
            val vmodel: String?
            val guiName: String?
            val projectileName: String?
            val brassDefName: String?
            var smokeName: String?
            val ammoAvail: Int
            Clear()
            if (!TempDump.isNotNullOrEmpty(objectName)) { //|| !objectname[ 0 ] ) {
                return
            }
            assert(owner != null)
            weaponDef = Game_local.gameLocal.FindEntityDef(objectName)
            ammoType = GetAmmoNumForName(weaponDef.dict.GetString("ammoType"))
            ammoRequired = weaponDef.dict.GetInt("ammoRequired")
            clipSize = weaponDef.dict.GetInt("clipSize")
            lowAmmo = weaponDef.dict.GetInt("lowAmmo")
            icon.oSet(weaponDef.dict.GetString("icon"))
            silent_fire = weaponDef.dict.GetBool("silent_fire")
            powerAmmo = weaponDef.dict.GetBool("powerAmmo")
            muzzle_kick_time = Math_h.SEC2MS(weaponDef.dict.GetFloat("muzzle_kick_time")).toInt()
            muzzle_kick_maxtime = Math_h.SEC2MS(weaponDef.dict.GetFloat("muzzle_kick_maxtime")).toInt()
            muzzle_kick_angles.oSet(weaponDef.dict.GetAngles("muzzle_kick_angles"))
            muzzle_kick_offset.oSet(weaponDef.dict.GetVector("muzzle_kick_offset"))
            hideTime = Math_h.SEC2MS(weaponDef.dict.GetFloat("hide_time", "0.3")).toInt()
            hideDistance = weaponDef.dict.GetFloat("hide_distance", "-15")

            // muzzle smoke
            smokeName = weaponDef.dict.GetString("smoke_muzzle")
            weaponSmoke = if (TempDump.isNotNullOrEmpty(smokeName)) {
                DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            continuousSmoke = weaponDef.dict.GetBool("continuousSmoke")
            weaponSmokeStartTime = if (continuousSmoke) Game_local.gameLocal.time else 0
            smokeName = weaponDef.dict.GetString("smoke_strike")
            strikeSmoke = if (TempDump.isNotNullOrEmpty(smokeName)) {
                DeclManager.declManager.FindType(declType_t.DECL_PARTICLE, smokeName) as idDeclParticle
            } else {
                null
            }
            strikeSmokeStartTime = 0
            strikePos.Zero()
            strikeAxis = idMat3.Companion.getMat3_identity()
            nextStrikeFx = 0

            // setup gui light
            guiLight = renderLight_s() //	memset( &guiLight, 0, sizeof( guiLight ) );
            val guiLightShader = weaponDef.dict.GetString("mtr_guiLightShader")
            if (TempDump.isNotNullOrEmpty(guiLightShader)) {
                guiLight.shader = DeclManager.declManager.FindMaterial(guiLightShader, false)
                guiLight.lightRadius.oSet(0, guiLight.lightRadius.oSet(1, guiLight.lightRadius.oSet(2, 3f)))
                guiLight.pointLight = true
            }

            // setup the view model
            vmodel = weaponDef.dict.GetString("model_view")
            SetModel(vmodel)

            // setup the world model
            InitWorldModel(weaponDef)

            // copy the sounds from the weapon view model def into out spawnargs
            var kv = weaponDef.dict.MatchPrefix("snd_")
            while (kv != null) {
                spawnArgs.Set(kv.GetKey(), kv.GetValue())
                kv = weaponDef.dict.MatchPrefix("snd_", kv)
            }

            // find some joints in the model for locating effects
            barrelJointView = animator.GetJointHandle("barrel")
            flashJointView = animator.GetJointHandle("flash")
            ejectJointView = animator.GetJointHandle("eject")
            guiLightJointView = animator.GetJointHandle("guiLight")
            ventLightJointView = animator.GetJointHandle("ventLight")

            // get the projectile
            projectileDict.Clear()
            projectileName = weaponDef.dict.GetString("def_projectile")
            if (TempDump.isNotNullOrEmpty(projectileName)) {
                val projectileDef = Game_local.gameLocal.FindEntityDef(projectileName, false)
                if (null == projectileDef) {
                    Game_local.gameLocal.Warning("Unknown projectile '%s' in weapon '%s'", projectileName, objectName)
                } else {
                    val spawnclass = projectileDef.dict.GetString("spawnclass")
                    val spawnEntity: idEntity = idClass.Companion.GetEntity(spawnclass)
                    if (spawnEntity !is idProjectile) {
                        Game_local.gameLocal.Warning(
                            "Invalid spawnclass '%s' on projectile '%s' (used by weapon '%s')",
                            spawnclass,
                            projectileName,
                            objectName
                        )
                    } else {
                        projectileDict.oSet(projectileDef.dict)
                    }
                }
            }

            // set up muzzleflash render light
            val flashShader: idMaterial?
            val flashTarget = idVec3()
            val flashUp = idVec3()
            val flashRight = idVec3()
            val flashRadius: Float
            val flashPointLight: Boolean
            weaponDef.dict.GetString("mtr_flashShader", "", shader)
            flashShader = DeclManager.declManager.FindMaterial(shader[0], false)
            flashPointLight = weaponDef.dict.GetBool("flashPointLight", "1")
            weaponDef.dict.GetVector("flashColor", "0 0 0", flashColor)
            flashRadius = weaponDef.dict.GetInt("flashRadius").toFloat() // if 0, no light will spawn
            flashTime = Math_h.SEC2MS(weaponDef.dict.GetFloat("flashTime", "0.25")).toInt()
            flashTarget.oSet(weaponDef.dict.GetVector("flashTarget"))
            flashUp.oSet(weaponDef.dict.GetVector("flashUp"))
            flashRight.oSet(weaponDef.dict.GetVector("flashRight"))
            muzzleFlash = renderLight_s() //memset( & muzzleFlash, 0, sizeof(muzzleFlash));
            muzzleFlash.lightId = Weapon.LIGHTID_VIEW_MUZZLE_FLASH + owner.entityNumber
            muzzleFlash.allowLightInViewID = owner.entityNumber + 1

            // the weapon lights will only be in first person
            guiLight.allowLightInViewID = owner.entityNumber + 1
            nozzleGlow.allowLightInViewID = owner.entityNumber + 1
            muzzleFlash.pointLight = flashPointLight
            muzzleFlash.shader = flashShader
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = flashColor.oGet(0)
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = flashColor.oGet(1)
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = flashColor.oGet(2)
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
            muzzleFlash.lightRadius.oSet(0, flashRadius)
            muzzleFlash.lightRadius.oSet(1, flashRadius)
            muzzleFlash.lightRadius.oSet(2, flashRadius)
            if (!flashPointLight) {
                muzzleFlash.target.oSet(flashTarget)
                muzzleFlash.up.oSet(flashUp)
                muzzleFlash.right.oSet(flashRight)
                muzzleFlash.end.oSet(flashTarget)
            }

            // the world muzzle flash is the same, just positioned differently
            worldMuzzleFlash = renderLight_s(muzzleFlash)
            worldMuzzleFlash.suppressLightInViewID = owner.entityNumber + 1
            worldMuzzleFlash.allowLightInViewID = 0
            worldMuzzleFlash.lightId = Weapon.LIGHTID_WORLD_MUZZLE_FLASH + owner.entityNumber

            //-----------------------------------
            nozzleFx = weaponDef.dict.GetBool("nozzleFx")
            nozzleFxFade = weaponDef.dict.GetInt("nozzleFxFade", "1500")
            nozzleGlowColor.oSet(weaponDef.dict.GetVector("nozzleGlowColor", "1 1 1"))
            nozzleGlowRadius = weaponDef.dict.GetFloat("nozzleGlowRadius", "10")
            weaponDef.dict.GetString("mtr_nozzleGlowShader", "", shader)
            nozzleGlowShader = DeclManager.declManager.FindMaterial(shader[0], false)

            // get the melee damage def
            meleeDistance = weaponDef.dict.GetFloat("melee_distance")
            meleeDefName.oSet(weaponDef.dict.GetString("def_melee"))
            if (meleeDefName.Length() != 0) {
                meleeDef = Game_local.gameLocal.FindEntityDef(meleeDefName.toString(), false)
                if (null == meleeDef) {
                    idGameLocal.Companion.Error("Unknown melee '%s'", meleeDefName)
                }
            }

            // get the brass def
            brassDict.Clear()
            brassDelay = weaponDef.dict.GetInt("ejectBrassDelay", "0")
            brassDefName = weaponDef.dict.GetString("def_ejectBrass")
            if (TempDump.isNotNullOrEmpty(brassDefName)) {
                val brassDef = Game_local.gameLocal.FindEntityDef(brassDefName, false)
                if (null == brassDef) {
                    Game_local.gameLocal.Warning("Unknown brass '%s'", brassDefName)
                } else {
                    brassDict.oSet(brassDef.dict)
                }
            }
            if (ammoType < 0 || ammoType >= Weapon.AMMO_NUMTYPES) {
                Game_local.gameLocal.Warning("Unknown ammotype in object '%s'", objectName)
            }
            ammoClip = ammoinclip
            if (ammoClip < 0 || ammoClip > clipSize) {
                // first time using this weapon so have it fully loaded to start
                ammoClip = clipSize
                ammoAvail = owner.inventory.HasAmmo(ammoType, ammoRequired)
                if (ammoClip > ammoAvail) {
                    ammoClip = ammoAvail
                }
            }
            renderEntity.gui[0] = null
            guiName = weaponDef.dict.GetString("gui")
            if (TempDump.isNotNullOrEmpty(guiName)) {
                renderEntity.gui[0] = UserInterface.uiManager.FindGui(guiName, true, false, true)
            }
            zoomFov = weaponDef.dict.GetInt("zoomFov", "70")
            berserk = weaponDef.dict.GetInt("berserk", "2")
            weaponAngleOffsetAverages = weaponDef.dict.GetInt("weaponAngleOffsetAverages", "10")
            weaponAngleOffsetScale = weaponDef.dict.GetFloat("weaponAngleOffsetScale", "0.25")
            weaponAngleOffsetMax = weaponDef.dict.GetFloat("weaponAngleOffsetMax", "10")
            weaponOffsetTime = weaponDef.dict.GetFloat("weaponOffsetTime", "400")
            weaponOffsetScale = weaponDef.dict.GetFloat("weaponOffsetScale", "0.005")
            if (!weaponDef.dict.GetString("weapon_scriptobject", null, objectType)) {
                idGameLocal.Companion.Error("No 'weapon_scriptobject' set on '%s'.", objectName)
            }

            // setup script object
            if (!scriptObject.SetType(objectType[0])) {
                idGameLocal.Companion.Error("Script object '%s' not found on weapon '%s'.", objectType[0], objectName)
            }
            WEAPON_ATTACK.LinkTo(scriptObject, "WEAPON_ATTACK")
            WEAPON_RELOAD.LinkTo(scriptObject, "WEAPON_RELOAD")
            WEAPON_NETRELOAD.LinkTo(scriptObject, "WEAPON_NETRELOAD")
            WEAPON_NETENDRELOAD.LinkTo(scriptObject, "WEAPON_NETENDRELOAD")
            if (!BuildDefines.ID_DEMO_BUILD) WEAPON_NETFIRING.LinkTo(scriptObject, "WEAPON_NETFIRING")
            WEAPON_RAISEWEAPON.LinkTo(scriptObject, "WEAPON_RAISEWEAPON")
            WEAPON_LOWERWEAPON.LinkTo(scriptObject, "WEAPON_LOWERWEAPON")
            spawnArgs.oSet(weaponDef.dict)
            shader[0] = spawnArgs.GetString("snd_hum")
            if (TempDump.isNotNullOrEmpty(shader[0])) {
                sndHum = DeclManager.declManager.FindSound(shader[0])
                StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
            }
            isLinked = true

            // call script object's constructor
            ConstructScriptObject()

            // make sure we have the correct skin
            UpdateSkin()
        }

        fun IsLinked(): Boolean {
            return isLinked
        }

        fun IsWorldModelReady(): Boolean {
            return worldModel.GetEntity() != null
        }

        /* **********************************************************************

         GUIs

         ***********************************************************************/
        fun Icon(): String? {
            return icon.toString()
        }

        fun UpdateGUI() {
            if (null == renderEntity.gui[0]) {
                return
            }
            if (status == weaponStatus_t.WP_HOLSTERED) {
                return
            }
            if (owner.weaponGone) {
                // dropping weapons was implemented wierd, so we have to not update the gui when it happens or we'll get a negative ammo count
                return
            }
            if (Game_local.gameLocal.localClientNum != owner.entityNumber) {
                // if updating the hud for a followed client
                if (Game_local.gameLocal.localClientNum >= 0 && Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] != null && Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] is idPlayer) {
                    val p = Game_local.gameLocal.entities[Game_local.gameLocal.localClientNum] as idPlayer
                    if (!p.spectating || p.spectator != owner.entityNumber) {
                        return
                    }
                } else {
                    return
                }
            }
            val inclip = AmmoInClip()
            val ammoamount = AmmoAvailable()
            if (ammoamount < 0) {
                // show infinite ammo
                renderEntity.gui[0].SetStateString("player_ammo", "")
            } else {
                // show remaining ammo
                renderEntity.gui[0].SetStateString("player_totalammo", Str.va("%d", ammoamount - inclip))
                renderEntity.gui[0].SetStateString("player_ammo", if (ClipSize() != 0) Str.va("%d", inclip) else "--")
                renderEntity.gui[0].SetStateString(
                    "player_clips",
                    if (ClipSize() != 0) Str.va("%d", ammoamount / ClipSize()) else "--"
                )
                renderEntity.gui[0].SetStateString("player_allammo", Str.va("%d/%d", inclip, ammoamount - inclip))
            }
            renderEntity.gui[0].SetStateBool("player_ammo_empty", ammoamount == 0)
            renderEntity.gui[0].SetStateBool("player_clip_empty", inclip == 0)
            renderEntity.gui[0].SetStateBool("player_clip_low", inclip <= lowAmmo)
        }

        override fun SetModel(modelname: String?) {
            assert(modelname != null)
            if (modelDefHandle >= 0) {
                Game_local.gameRenderWorld.RemoveDecals(modelDefHandle)
            }
            renderEntity.hModel = animator.SetModel(modelname)
            if (renderEntity.hModel != null) {
                renderEntity.customSkin = animator.ModelDef().GetDefaultSkin()
                renderEntity.numJoints = animator.GetJoints(renderEntity)
            } else {
                renderEntity.customSkin = null
                renderEntity.callback = null
                renderEntity.numJoints = 0
                renderEntity.joints = null
            }

            // hide the model until an animation is played
            Hide()
        }

        /*
         ================
         idWeapon::GetGlobalJointTransform

         This returns the offset and axis of a weapon bone in world space, suitable for attaching models or lights
         ================
         */
        fun GetGlobalJointTransform(
            viewModel: Boolean,    /*jointHandle_t*/
            jointHandle: Int,
            offset: idVec3?,
            axis: idMat3?
        ): Boolean {
            if (viewModel) {
                // view model
                if (animator.GetJointTransform(jointHandle, Game_local.gameLocal.time, offset, axis)) {
                    offset.oSet(offset.oMultiply(viewWeaponAxis).oPlus(viewWeaponOrigin))
                    axis.oSet(axis.oMultiply(viewWeaponAxis))
                    return true
                }
            } else {
                // world model
                if (worldModel.GetEntity() != null && worldModel.GetEntity().GetAnimator()
                        .GetJointTransform(jointHandle, Game_local.gameLocal.time, offset, axis)
                ) {
                    offset.oSet(
                        worldModel.GetEntity().GetPhysics().GetOrigin()
                            .oPlus(offset.oMultiply(worldModel.GetEntity().GetPhysics().GetAxis()))
                    )
                    axis.oSet(axis.oMultiply(worldModel.GetEntity().GetPhysics().GetAxis()))
                    return true
                }
            }
            offset.oSet(viewWeaponOrigin)
            axis.oSet(viewWeaponAxis)
            return false
        }

        fun SetPushVelocity(pushVelocity: idVec3?) {
            this.pushVelocity.oSet(pushVelocity)
        }

        fun UpdateSkin(): Boolean {
            val func: function_t?
            if (!isLinked) {
                return false
            }
            func = scriptObject.GetFunction("UpdateSkin")
            if (null == func) {
                idLib.common.Warning("Can't find function 'UpdateSkin' in object '%s'", scriptObject.GetTypeName())
                return false
            }

            // use the frameCommandThread since it's safe to use outside of framecommands
            Game_local.gameLocal.frameCommandThread.CallFunction(this, func, true)
            Game_local.gameLocal.frameCommandThread.Execute()
            return true
        }

        /* **********************************************************************

         State control/player interface

         ***********************************************************************/
        override fun Think() {
            // do nothing because the present is called from the player through PresentWeapon
        }

        fun Raise() {
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(true)
            }
        }

        fun PutAway() {
            hasBloodSplat = false
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(true)
            }
        }

        /*
         ================
         idWeapon::Reload
         NOTE: this is only for impulse-triggered reload, auto reload is scripted
         ================
         */
        fun Reload() {
            if (isLinked) {
                WEAPON_RELOAD.underscore(true)
            }
        }

        fun LowerWeapon() {
            if (!hide) {
                hideStart = 0.0f
                hideEnd = hideDistance
                hideStartTime = if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                    Game_local.gameLocal.time - (hideTime - (Game_local.gameLocal.time - hideStartTime))
                } else {
                    Game_local.gameLocal.time
                }
                hide = true
            }
        }

        fun RaiseWeapon() {
            Show()
            if (hide) {
                hideStart = hideDistance
                hideEnd = 0.0f
                hideStartTime = if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                    Game_local.gameLocal.time - (hideTime - (Game_local.gameLocal.time - hideStartTime))
                } else {
                    Game_local.gameLocal.time
                }
                hide = false
            }
        }

        fun HideWeapon() {
            Hide()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().Hide()
            }
            muzzleFlashEnd = 0
        }

        fun ShowWeapon() {
            Show()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().Show()
            }
            if (lightOn) {
                MuzzleFlashLight()
            }
        }

        fun HideWorldModel() {
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().Hide()
            }
        }

        fun ShowWorldModel() {
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().Show()
            }
        }

        fun OwnerDied() {
            if (isLinked) {
                SetState("OwnerDied", 0)
                thread.Execute()
            }
            Hide()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().Hide()
            }

            // don't clear the weapon immediately since the owner might have killed himself by firing the weapon
            // within the current stack frame
            PostEventMS(Weapon.EV_Weapon_Clear, 0)
        }

        fun BeginAttack() {
            if (status != weaponStatus_t.WP_OUTOFAMMO) {
                lastAttack = Game_local.gameLocal.time
            }
            if (!isLinked) {
                return
            }
            if (!WEAPON_ATTACK.underscore()) {
                if (sndHum != null) {
                    StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
                }
            }
            WEAPON_ATTACK.underscore(true)
        }

        fun EndAttack() {
            if (!WEAPON_ATTACK.IsLinked()) {
                return
            }
            if (WEAPON_ATTACK.underscore()) {
                WEAPON_ATTACK.underscore(false)
                if (sndHum != null) {
                    StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
                }
            }
        }

        fun IsReady(): Boolean {
            return !hide && !IsHidden() && (status == weaponStatus_t.WP_RELOAD || status == weaponStatus_t.WP_READY || status == weaponStatus_t.WP_OUTOFAMMO)
        }

        fun IsReloading(): Boolean {
            return status == weaponStatus_t.WP_RELOAD
        }

        fun IsHolstered(): Boolean {
            return status == weaponStatus_t.WP_HOLSTERED
        }

        fun ShowCrosshair(): Boolean {
            return !(state == weaponStatus_t.WP_RISING || state == weaponStatus_t.WP_LOWERING || state == weaponStatus_t.WP_HOLSTERED)
        }

        fun DropItem(velocity: idVec3?, activateDelay: Int, removeDelay: Int, died: Boolean): idEntity? {
            if (null == weaponDef || null == worldModel.GetEntity()) {
                return null
            }
            if (!allowDrop) {
                return null
            }
            val classname = weaponDef.dict.GetString("def_dropItem")
            if (!TempDump.isNotNullOrEmpty(classname)) {
                return null
            }
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), true)
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY3), true)
            return idMoveableItem.Companion.DropItem(
                classname,
                worldModel.GetEntity().GetPhysics().GetOrigin(),
                worldModel.GetEntity().GetPhysics().GetAxis(),
                velocity,
                activateDelay,
                removeDelay
            )
        }

        fun CanDrop(): Boolean {
            if (null == weaponDef || null == worldModel.GetEntity()) {
                return false
            }
            val classname = weaponDef.dict.GetString("def_dropItem")
            return TempDump.isNotNullOrEmpty(classname)
        }

        fun WeaponStolen() {
            assert(!Game_local.gameLocal.isClient)
            if (projectileEnt != null) {
                if (isLinked) {
                    SetState("WeaponStolen", 0)
                    thread.Execute()
                }
                projectileEnt = null
            }

            // set to holstered so we can switch weapons right away
            status = weaponStatus_t.WP_HOLSTERED
            HideWeapon()
        }

        /* **********************************************************************

         Script state management

         ***********************************************************************/
        /*
         ================
         idWeapon::ConstructScriptObject

         Called during idEntity::Spawn.  Calls the constructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         ================
         */
        override fun ConstructScriptObject(): idThread? {
            val constructor: function_t?
            thread.EndThread()

            // call script object's constructor
            constructor = scriptObject.GetConstructor()
            if (null == constructor) {
                idGameLocal.Companion.Error("Missing constructor on '%s' for weapon", scriptObject.GetTypeName())
            }

            // init the script object's data
            scriptObject.ClearObject()
            thread.CallFunction(this, constructor, true)
            thread.Execute()
            return thread
        }

        /*
         ================
         idWeapon::DeconstructScriptObject

         Called during idEntity::~idEntity.  Calls the destructor on the script object.
         Can be overridden by subclasses when a thread doesn't need to be allocated.
         Not called during idGameLocal::MapShutdown.
         ================
         */
        fun DefinalructScriptObject() {
            val destructor: function_t?
            if (TempDump.NOT(thread)) {
                return
            }

            // don't bother calling the script object's destructor on map shutdown
            if (Game_local.gameLocal.GameState() == Game_local.gameState_t.GAMESTATE_SHUTDOWN) {
                return
            }
            thread.EndThread()

            // call script object's destructor
            destructor = scriptObject.GetDestructor()
            if (destructor != null) {
                // start a thread that will run immediately and end
                thread.CallFunction(this, destructor, true)
                thread.Execute()
                thread.EndThread()
            }

            // clear out the object's memory
            scriptObject.ClearObject()
        }

        fun SetState(statename: String?, blendFrames: Int) {
            val func: function_t?
            if (!isLinked) {
                return
            }
            func = scriptObject.GetFunction(statename)
            if (null == func) {
                assert(false)
                idGameLocal.Companion.Error(
                    "Can't find function '%s' in object '%s'",
                    statename,
                    scriptObject.GetTypeName()
                )
            }
            thread.CallFunction(this, func, true)
            state.oSet(statename)
            animBlendFrames = blendFrames
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameLocal.Printf("%d: weapon state : %s\n", Game_local.gameLocal.time, statename)
            }
            idealState.oSet("")
        }

        fun UpdateScript() {
            var count: Int
            if (!isLinked) {
                return
            }

            // only update the script on new frames
            if (!Game_local.gameLocal.isNewFrame) {
                return
            }
            if (idealState.Length() != 0) {
                SetState(idealState.toString(), animBlendFrames)
            }

            // update script state, which may call Event_LaunchProjectiles, among other things
            count = 10
            while ((thread.Execute() || idealState.Length() != 0) && count-- != 0) {
                // happens for weapons with no clip (like grenades)
                if (idealState.Length() != 0) {
                    SetState(idealState.toString(), animBlendFrames)
                }
            }
            WEAPON_RELOAD.underscore(false)
        }

        fun EnterCinematic() {
            StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), false)
            if (isLinked) {
                SetState("EnterCinematic", 0)
                thread.Execute()
                WEAPON_ATTACK.underscore(false)
                WEAPON_RELOAD.underscore(false)
                WEAPON_NETRELOAD.underscore(false)
                WEAPON_NETENDRELOAD.underscore(false)
                WEAPON_NETFIRING.underscore(false)
                WEAPON_RAISEWEAPON.underscore(false)
                WEAPON_LOWERWEAPON.underscore(false)
            }
            disabled = true
            LowerWeapon()
        }

        fun ExitCinematic() {
            disabled = false
            if (isLinked) {
                SetState("ExitCinematic", 0)
                thread.Execute()
            }
            RaiseWeapon()
        }

        fun NetCatchup() {
            if (isLinked) {
                SetState("NetCatchup", 0)
                thread.Execute()
            }
        }

        /* **********************************************************************

         Visual presentation

         ***********************************************************************/
        fun PresentWeapon(showViewModel: Boolean) {
            playerViewOrigin.oSet(owner.firstPersonViewOrigin)
            playerViewAxis.oSet(owner.firstPersonViewAxis)

            // calculate weapon position based on player movement bobbing
            owner.CalculateViewWeaponPos(viewWeaponOrigin, viewWeaponAxis)

            // hide offset is for dropping the gun when approaching a GUI or NPC
            // This is simpler to manage than doing the weapon put-away animation
            if (Game_local.gameLocal.time - hideStartTime < hideTime) {
                var frac = (Game_local.gameLocal.time - hideStartTime).toFloat() / hideTime.toFloat()
                if (hideStart < hideEnd) {
                    frac = 1.0f - frac
                    frac = 1.0f - frac * frac
                } else {
                    frac = frac * frac
                }
                hideOffset = hideStart + (hideEnd - hideStart) * frac
            } else {
                hideOffset = hideEnd
                if (hide && disabled) {
                    Hide()
                }
            }
            viewWeaponOrigin.oPluSet(viewWeaponAxis.oGet(2).oMultiply(hideOffset))

            // kick up based on repeat firing
            MuzzleRise(viewWeaponOrigin, viewWeaponAxis)

            // set the physics position and orientation
            GetPhysics().SetOrigin(viewWeaponOrigin)
            GetPhysics().SetAxis(viewWeaponAxis)
            UpdateVisuals()

            // update the weapon script
            UpdateScript()
            UpdateGUI()

            // update animation
            UpdateAnimation()

            // only show the surface in player view
            renderEntity.allowSurfaceInViewID = owner.entityNumber + 1

            // crunch the depth range so it never pokes into walls this breaks the machine gun gui
            renderEntity.weaponDepthHack = true

            // present the model
            if (showViewModel) {
                Present()
            } else {
                FreeModelDef()
            }
            if (worldModel.GetEntity() != null && worldModel.GetEntity().GetRenderEntity() != null) {
                // deal with the third-person visible world model
                // don't show shadows of the world model in first person
                if (Game_local.gameLocal.isMultiplayer || SysCvar.g_showPlayerShadow.GetBool() || SysCvar.pm_thirdPerson.GetBool()) {
                    worldModel.GetEntity().GetRenderEntity().suppressShadowInViewID = 0
                } else {
                    worldModel.GetEntity().GetRenderEntity().suppressShadowInViewID = owner.entityNumber + 1
                    worldModel.GetEntity().GetRenderEntity().suppressShadowInLightID =
                        Weapon.LIGHTID_VIEW_MUZZLE_FLASH + owner.entityNumber
                }
            }
            if (nozzleFx) {
                UpdateNozzleFx()
            }

            // muzzle smoke
            if (showViewModel && !disabled && weaponSmoke != null && weaponSmokeStartTime != 0) {
                // use the barrel joint if available
                if (barrelJointView != 0) {
                    GetGlobalJointTransform(true, barrelJointView, muzzleOrigin, muzzleAxis)
                } else {
                    // default to going straight out the view
                    muzzleOrigin.oSet(playerViewOrigin)
                    muzzleAxis.oSet(playerViewAxis)
                }
                // spit out a particle
                if (!Game_local.gameLocal.smokeParticles.EmitSmoke(
                        weaponSmoke,
                        weaponSmokeStartTime,
                        Game_local.gameLocal.random.RandomFloat(),
                        muzzleOrigin,
                        muzzleAxis
                    )
                ) {
                    weaponSmokeStartTime = if (continuousSmoke) Game_local.gameLocal.time else 0
                }
            }
            if (showViewModel && strikeSmoke != null && strikeSmokeStartTime != 0) {
                // spit out a particle
                if (!Game_local.gameLocal.smokeParticles.EmitSmoke(
                        strikeSmoke,
                        strikeSmokeStartTime,
                        Game_local.gameLocal.random.RandomFloat(),
                        strikePos,
                        strikeAxis
                    )
                ) {
                    strikeSmokeStartTime = 0
                }
            }

            // remove the muzzle flash light when it's done
            if (!lightOn && Game_local.gameLocal.time >= muzzleFlashEnd || IsHidden()) {
                if (muzzleFlashHandle != -1) {
                    Game_local.gameRenderWorld.FreeLightDef(muzzleFlashHandle)
                    muzzleFlashHandle = -1
                }
                if (worldMuzzleFlashHandle != -1) {
                    Game_local.gameRenderWorld.FreeLightDef(worldMuzzleFlashHandle)
                    worldMuzzleFlashHandle = -1
                }
            }

            // update the muzzle flash light, so it moves with the gun
            if (muzzleFlashHandle != -1) {
                UpdateFlashPosition()
                Game_local.gameRenderWorld.UpdateLightDef(muzzleFlashHandle, muzzleFlash)
                Game_local.gameRenderWorld.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)

                // wake up monsters with the flashlight
                if (!Game_local.gameLocal.isMultiplayer && lightOn && !owner.fl.notarget) {
                    AlertMonsters()
                }
            }

            // update the gui light
            if (guiLight.lightRadius.oGet(0) != 0f && guiLightJointView != Model.INVALID_JOINT) {
                GetGlobalJointTransform(true, guiLightJointView, guiLight.origin, guiLight.axis)
                if (guiLightHandle != -1) {
                    Game_local.gameRenderWorld.UpdateLightDef(guiLightHandle, guiLight)
                } else {
                    guiLightHandle = Game_local.gameRenderWorld.AddLightDef(guiLight)
                }
            }
            if (status != weaponStatus_t.WP_READY && sndHum != null) {
                StopSound(TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_BODY), false)
            }
            UpdateSound()
        }

        fun GetZoomFov(): Int {
            return zoomFov
        }

        fun GetWeaponAngleOffsets(average: CInt?, scale: CFloat?, max: CFloat?) {
            average.setVal(weaponAngleOffsetAverages)
            scale.setVal(weaponAngleOffsetScale)
            max.setVal(weaponAngleOffsetMax)
        }

        fun GetWeaponTimeOffsets(time: CFloat?, scale: CFloat?) {
            time.setVal(weaponOffsetTime)
            scale.setVal(weaponOffsetScale)
        }

        fun BloodSplat(size: Float): Boolean {
            val s = CFloat()
            val c = CFloat()
            val localAxis = idMat3()
            val axistemp = idMat3()
            val localOrigin = idVec3()
            val normal = idVec3()
            if (hasBloodSplat) {
                return true
            }
            hasBloodSplat = true
            if (modelDefHandle < 0) {
                return false
            }
            if (!GetGlobalJointTransform(true, ejectJointView, localOrigin, localAxis)) {
                return false
            }
            localOrigin.oPluSet(0, Game_local.gameLocal.random.RandomFloat() * -10.0f)
            localOrigin.oPluSet(1, Game_local.gameLocal.random.RandomFloat() * 1.0f)
            localOrigin.oPluSet(2, Game_local.gameLocal.random.RandomFloat() * -2.0f)
            normal.oSet(
                idVec3(
                    Game_local.gameLocal.random.CRandomFloat(),
                    -Game_local.gameLocal.random.RandomFloat(),
                    -1
                )
            )
            normal.Normalize()
            idMath.SinCos16(Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI, s, c)
            localAxis.oSet(2, normal.oNegative())
            localAxis.oGet(2).NormalVectors(axistemp.oGet(0), axistemp.oGet(1))
            localAxis.oSet(0, axistemp.oGet(0).oMultiply(c.getVal()).oPlus(axistemp.oGet(1).oMultiply(-s.getVal())))
            localAxis.oSet(1, axistemp.oGet(0).oMultiply(-s.getVal()).oPlus(axistemp.oGet(1).oMultiply(-c.getVal())))
            localAxis.oGet(0).oMulSet(1.0f / size)
            localAxis.oGet(1).oMulSet(1.0f / size)
            val localPlane: Array<idPlane?> = idPlane.Companion.generateArray(2)
            localPlane[0].oSet(localAxis.oGet(0))
            localPlane[0].oSet(3, -localOrigin.oMultiply(localAxis.oGet(0)) + 0.5f)
            localPlane[1].oSet(localAxis.oGet(1))
            localPlane[1].oSet(3, -localOrigin.oMultiply(localAxis.oGet(1)) + 0.5f)
            val mtr: idMaterial? = DeclManager.declManager.FindMaterial("textures/decals/duffysplatgun")
            Game_local.gameRenderWorld.ProjectOverlay(modelDefHandle, localPlane, mtr)
            return true
        }

        fun  /*ammo_t*/GetAmmoType(): Int {
            return ammoType
        }

        fun AmmoAvailable(): Int {
            return if (owner != null) {
                owner.inventory.HasAmmo(ammoType, ammoRequired)
            } else {
                0
            }
        }

        fun AmmoInClip(): Int {
            return ammoClip
        }

        fun ResetAmmoClip() {
            ammoClip = -1
        }

        fun ClipSize(): Int {
            return clipSize
        }

        fun LowAmmo(): Int {
            return lowAmmo
        }

        fun AmmoRequired(): Int {
            return ammoRequired
        }

        override fun WriteToSnapshot(msg: idBitMsgDelta?) {
            msg.WriteBits(ammoClip, Player.ASYNC_PLAYER_INV_CLIP_BITS)
            msg.WriteBits(worldModel.GetSpawnId(), 32)
            msg.WriteBits(TempDump.btoi(lightOn), 1)
            msg.WriteBits(if (isFiring) 1 else 0, 1)
        }

        // };
        override fun ReadFromSnapshot(msg: idBitMsgDelta?) {
            ammoClip = msg.ReadBits(Player.ASYNC_PLAYER_INV_CLIP_BITS)
            worldModel.SetSpawnId(msg.ReadBits(32))
            val snapLight = msg.ReadBits(1) != 0
            isFiring = msg.ReadBits(1) != 0

            // WEAPON_NETFIRING is only turned on for other clients we're predicting. not for local client
            if (owner != null && Game_local.gameLocal.localClientNum != owner.entityNumber && WEAPON_NETFIRING.IsLinked()) {

                // immediately go to the firing state so we don't skip fire animations
                if (!WEAPON_NETFIRING.underscore() && isFiring) {
                    idealState.oSet("Fire")
                }

                // immediately switch back to idle
                if (WEAPON_NETFIRING.underscore() && !isFiring) {
                    idealState.oSet("Idle")
                }
                WEAPON_NETFIRING.underscore(isFiring)
            }
            if (snapLight != lightOn) {
                Reload()
            }
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
            return when (event) {
                EVENT_RELOAD -> {
                    if (Game_local.gameLocal.time - time < 1000) {
                        if (WEAPON_NETRELOAD.IsLinked()) {
                            WEAPON_NETRELOAD.underscore(true)
                            WEAPON_NETENDRELOAD.underscore(false)
                        }
                    }
                    true
                }
                EVENT_ENDRELOAD -> {
                    if (WEAPON_NETENDRELOAD.IsLinked()) {
                        WEAPON_NETENDRELOAD.underscore(true)
                    }
                    true
                }
                EVENT_CHANGESKIN -> {
                    val index = Game_local.gameLocal.ClientRemapDecl(declType_t.DECL_SKIN, msg.ReadLong())
                    renderEntity.customSkin = if (index != -1) DeclManager.declManager.DeclByIndex(
                        declType_t.DECL_SKIN,
                        index
                    ) as idDeclSkin else null
                    UpdateVisuals()
                    if (worldModel.GetEntity() != null) {
                        worldModel.GetEntity().SetSkin(renderEntity.customSkin)
                    }
                    true
                }
                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        override fun ClientPredictionThink() {
            UpdateAnimation()
        }

        // flashlight
        private fun AlertMonsters() {
            val tr = trace_s()
            var ent: idEntity?
            val end = idVec3(muzzleFlash.origin.oPlus(muzzleFlash.axis.oMultiply(muzzleFlash.target)))
            Game_local.gameLocal.clip.TracePoint(
                tr,
                muzzleFlash.origin,
                end,
                Material.CONTENTS_OPAQUE or Game_local.MASK_SHOT_RENDERMODEL or Material.CONTENTS_FLASHLIGHT_TRIGGER,
                owner
            )
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameRenderWorld.DebugLine(Lib.Companion.colorYellow, muzzleFlash.origin, end, 0)
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorGreen, muzzleFlash.origin, tr.endpos, 2, 0)
            }
            if (tr.fraction < 1.0f) {
                ent = Game_local.gameLocal.GetTraceEntity(tr)
                if (ent is idAI) {
                    (ent as idAI?).TouchedByFlashlight(owner)
                } else if (ent is idTrigger) {
                    ent.Signal(signalNum_t.SIG_TOUCH)
                    ent.ProcessEvent(Entity.EV_Touch, owner, tr)
                }
            }

            // jitter the trace to try to catch cases where a trace down the center doesn't hit the monster
            end.oPluSet(muzzleFlash.axis.oMultiply(muzzleFlash.right.oMultiply(idMath.Sin16(Math_h.MS2SEC(Game_local.gameLocal.time.toFloat()) * 31.34f))))
            end.oPluSet(muzzleFlash.axis.oMultiply(muzzleFlash.up.oMultiply(idMath.Sin16(Math_h.MS2SEC(Game_local.gameLocal.time.toFloat()) * 12.17f))))
            Game_local.gameLocal.clip.TracePoint(
                tr,
                muzzleFlash.origin,
                end,
                Material.CONTENTS_OPAQUE or Game_local.MASK_SHOT_RENDERMODEL or Material.CONTENTS_FLASHLIGHT_TRIGGER,
                owner
            )
            if (SysCvar.g_debugWeapon.GetBool()) {
                Game_local.gameRenderWorld.DebugLine(Lib.Companion.colorYellow, muzzleFlash.origin, end, 0)
                Game_local.gameRenderWorld.DebugArrow(Lib.Companion.colorGreen, muzzleFlash.origin, tr.endpos, 2, 0)
            }
            if (tr.fraction < 1.0f) {
                ent = Game_local.gameLocal.GetTraceEntity(tr)
                if (ent is idAI) {
                    (ent as idAI?).TouchedByFlashlight(owner)
                } else if (ent is idTrigger) {
                    ent.Signal(signalNum_t.SIG_TOUCH)
                    ent.ProcessEvent(Entity.EV_Touch, owner, tr)
                }
            }
        }

        // Visual presentation
        private fun InitWorldModel(def: idDeclEntityDef?) {
            val ent: idEntity?
            ent = worldModel.GetEntity()
            assert(ent != null)
            assert(def != null)
            val model = def.dict.GetString("model_world")
            val attach = def.dict.GetString("joint_attach")
            ent.SetSkin(null)
            if (TempDump.isNotNullOrEmpty(model)) {
                ent.Show()
                ent.SetModel(model)
                if (ent.GetAnimator().ModelDef() != null) {
                    ent.SetSkin(ent.GetAnimator().ModelDef().GetDefaultSkin())
                }
                ent.GetPhysics().SetContents(0)
                ent.GetPhysics().SetClipModel(null, 1.0f)
                ent.BindToJoint(owner, attach, true)
                ent.GetPhysics().SetOrigin(Vector.getVec3_origin())
                ent.GetPhysics().SetAxis(idMat3.Companion.getMat3_identity())

                // supress model in player views, but allow it in mirrors and remote views
                val worldModelRenderEntity = ent.GetRenderEntity()
                if (worldModelRenderEntity != null) {
                    worldModelRenderEntity.suppressSurfaceInViewID = owner.entityNumber + 1
                    worldModelRenderEntity.suppressShadowInViewID = owner.entityNumber + 1
                    worldModelRenderEntity.suppressShadowInLightID =
                        Weapon.LIGHTID_VIEW_MUZZLE_FLASH + owner.entityNumber
                }
            } else {
                ent.SetModel("")
                ent.Hide()
            }
            flashJointWorld = ent.GetAnimator().GetJointHandle("flash")
            barrelJointWorld = ent.GetAnimator().GetJointHandle("muzzle")
            ejectJointWorld = ent.GetAnimator().GetJointHandle("eject")
        }

        private fun MuzzleFlashLight() {
            if (!lightOn && (!SysCvar.g_muzzleFlash.GetBool() || 0f == muzzleFlash.lightRadius.oGet(0))) {
                return
            }
            if (flashJointView == Model.INVALID_JOINT) {
                return
            }
            UpdateFlashPosition()

            // these will be different each fire
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] =
                renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]

            // the light will be removed at this time
            muzzleFlashEnd = Game_local.gameLocal.time + flashTime
            if (muzzleFlashHandle != -1) {
                Game_local.gameRenderWorld.UpdateLightDef(muzzleFlashHandle, muzzleFlash)
                Game_local.gameRenderWorld.UpdateLightDef(worldMuzzleFlashHandle, worldMuzzleFlash)
            } else {
                muzzleFlashHandle = Game_local.gameRenderWorld.AddLightDef(muzzleFlash)
                worldMuzzleFlashHandle = Game_local.gameRenderWorld.AddLightDef(worldMuzzleFlash)
            }
        }

        /*
         ================
         idWeapon::MuzzleRise

         The machinegun and chaingun will incrementally back up as they are being fired
         ================
         */
        private fun MuzzleRise(origin: idVec3?, axis: idMat3?) {
            var time: Int
            val amount: Float
            val ang: idAngles?
            val offset = idVec3()
            time = kick_endtime - Game_local.gameLocal.time
            if (time <= 0) {
                return
            }
            if (muzzle_kick_maxtime <= 0) {
                return
            }
            if (time > muzzle_kick_maxtime) {
                time = muzzle_kick_maxtime
            }
            amount = time.toFloat() / muzzle_kick_maxtime.toFloat()
            ang = muzzle_kick_angles.oMultiply(amount)
            offset.oSet(muzzle_kick_offset.oMultiply(amount))
            origin.oSet(origin.oMinus(axis.oMultiply(offset)))
            axis.oSet(ang.ToMat3().oMultiply(axis))
        }

        private fun UpdateNozzleFx() {
            if (!nozzleFx) {
                return
            }

            //
            // shader parms
            //
            val la = Game_local.gameLocal.time - lastAttack + 1
            var s = 1.0f
            var l = 0.0f
            if (la < nozzleFxFade) {
                s = la.toFloat() / nozzleFxFade
                l = 1.0f - s
            }
            renderEntity.shaderParms[5] = s
            renderEntity.shaderParms[6] = l
            if (ventLightJointView == Model.INVALID_JOINT) {
                return
            }

            //
            // vent light
            //
            if (nozzleGlowHandle == -1) {
//		memset(&nozzleGlow, 0, sizeof(nozzleGlow));
                nozzleGlow = renderLight_s()
                if (owner != null) {
                    nozzleGlow.allowLightInViewID = owner.entityNumber + 1
                }
                nozzleGlow.pointLight = true
                nozzleGlow.noShadows = true
                nozzleGlow.lightRadius.x = nozzleGlowRadius
                nozzleGlow.lightRadius.y = nozzleGlowRadius
                nozzleGlow.lightRadius.z = nozzleGlowRadius
                nozzleGlow.shader = nozzleGlowShader
                nozzleGlow.shaderParms[RenderWorld.SHADERPARM_TIMESCALE] = 1.0f
                nozzleGlow.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                    -Math_h.MS2SEC(Game_local.gameLocal.time.toFloat())
                GetGlobalJointTransform(true, ventLightJointView, nozzleGlow.origin, nozzleGlow.axis)
                nozzleGlowHandle = Game_local.gameRenderWorld.AddLightDef(nozzleGlow)
            }
            GetGlobalJointTransform(true, ventLightJointView, nozzleGlow.origin, nozzleGlow.axis)
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_RED] = nozzleGlowColor.x * s
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_GREEN] = nozzleGlowColor.y * s
            nozzleGlow.shaderParms[RenderWorld.SHADERPARM_BLUE] = nozzleGlowColor.z * s
            Game_local.gameRenderWorld.UpdateLightDef(nozzleGlowHandle, nozzleGlow)
        }

        private fun UpdateFlashPosition() {
            // the flash has an explicit joint for locating it
            GetGlobalJointTransform(true, flashJointView, muzzleFlash.origin, muzzleFlash.axis)

            // if the desired point is inside or very close to a wall, back it up until it is clear
            val start = idVec3(muzzleFlash.origin.oMinus(playerViewAxis.oGet(0).oMultiply(16f)))
            val end = idVec3(muzzleFlash.origin.oPlus(playerViewAxis.oGet(0).oMultiply(8f)))
            val tr = trace_s()
            Game_local.gameLocal.clip.TracePoint(tr, start, end, Game_local.MASK_SHOT_RENDERMODEL, owner)
            // be at least 8 units away from a solid
            muzzleFlash.origin.oSet(tr.endpos.oMinus(playerViewAxis.oGet(0).oMultiply(8f)))

            // put the world muzzle flash on the end of the joint, no matter what
            GetGlobalJointTransform(false, flashJointWorld, worldMuzzleFlash.origin, worldMuzzleFlash.axis)
        }

        /* **********************************************************************

         Script events

         ***********************************************************************/
        private fun Event_Clear() {
            Clear()
        }

        private fun Event_GetOwner() {
            idThread.Companion.ReturnEntity(owner)
        }

        //
        //        private void Event_SetWeaponStatus(float newStatus);
        //
        private fun Event_WeaponState(_statename: idEventArg<String?>?, blendFrames: idEventArg<Int?>?) {
            val statename = _statename.value
            val func: function_t?
            func = scriptObject.GetFunction(statename)
            if (null == func) {
                assert(false)
                idGameLocal.Companion.Error(
                    "Can't find function '%s' in object '%s'",
                    statename,
                    scriptObject.GetTypeName()
                )
            }
            idealState.oSet(statename)
            isFiring = 0 == idealState.Icmp("Fire")
            animBlendFrames = blendFrames.value
            thread.DoneProcessing()
        }

        private fun Event_WeaponReady() {
            status = weaponStatus_t.WP_READY
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
            if (sndHum != null) {
                StartSoundShader(sndHum, gameSoundChannel_t.SND_CHANNEL_BODY, 0, false, null)
            }
        }

        private fun Event_WeaponOutOfAmmo() {
            status = weaponStatus_t.WP_OUTOFAMMO
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
        }

        private fun Event_WeaponReloading() {
            status = weaponStatus_t.WP_RELOAD
        }

        private fun Event_WeaponHolstered() {
            status = weaponStatus_t.WP_HOLSTERED
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(false)
            }
        }

        private fun Event_WeaponRising() {
            status = weaponStatus_t.WP_RISING
            if (isLinked) {
                WEAPON_LOWERWEAPON.underscore(false)
            }
            owner.WeaponRisingCallback()
        }

        private fun Event_WeaponLowering() {
            status = weaponStatus_t.WP_LOWERING
            if (isLinked) {
                WEAPON_RAISEWEAPON.underscore(false)
            }
            owner.WeaponLoweringCallback()
        }

        private fun Event_UseAmmo(_amount: idEventArg<Int?>?) {
            val amount: Int = _amount.value
            if (Game_local.gameLocal.isClient) {
                return
            }
            owner.inventory.UseAmmo(ammoType, if (powerAmmo) amount else amount * ammoRequired)
            if (clipSize != 0 && ammoRequired != 0) {
                ammoClip -= if (powerAmmo) amount else amount * ammoRequired
                if (ammoClip < 0) {
                    ammoClip = 0
                }
            }
        }

        private fun Event_AddToClip(amount: idEventArg<Int?>?) {
            val ammoAvail: Int
            if (Game_local.gameLocal.isClient) {
                return
            }
            ammoClip += amount.value
            if (ammoClip > clipSize) {
                ammoClip = clipSize
            }
            ammoAvail = owner.inventory.HasAmmo(ammoType, ammoRequired)
            if (ammoClip > ammoAvail) {
                ammoClip = ammoAvail
            }
        }

        private fun Event_AmmoInClip() {
            val ammo = AmmoInClip()
            idThread.Companion.ReturnFloat(ammo.toFloat())
        }

        private fun Event_AmmoAvailable() {
            val ammoAvail = owner.inventory.HasAmmo(ammoType, ammoRequired)
            idThread.Companion.ReturnFloat(ammoAvail.toFloat())
        }

        private fun Event_TotalAmmoCount() {
            val ammoAvail = owner.inventory.HasAmmo(ammoType, 1)
            idThread.Companion.ReturnFloat(ammoAvail.toFloat())
        }

        private fun Event_ClipSize() {
            idThread.Companion.ReturnFloat(clipSize.toFloat())
        }

        private fun Event_PlayAnim(_channel: idEventArg<Int?>?, _animname: idEventArg<String?>?) {
            val channel: Int = _channel.value
            val animname = _animname.value
            var anim: Int
            anim = animator.GetAnim(animname)
            if (0 == anim) {
                Game_local.gameLocal.Warning("missing '%s' animation on '%s' (%s)", animname, name, GetEntityDefName())
                animator.Clear(channel, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = 0
            } else {
                if (!(owner != null && owner.GetInfluenceLevel() != 0)) {
                    Show()
                }
                animator.PlayAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = animator.CurrentAnim(channel).GetEndTime()
                if (worldModel.GetEntity() != null) {
                    anim = worldModel.GetEntity().GetAnimator().GetAnim(animname)
                    if (anim != 0) {
                        worldModel.GetEntity().GetAnimator()
                            .PlayAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                    }
                }
            }
            animBlendFrames = 0
            idThread.Companion.ReturnInt(0)
        }

        private fun Event_PlayCycle(_channel: idEventArg<Int?>?, _animname: idEventArg<String?>?) {
            val channel: Int = _channel.value
            val animname = _animname.value
            var anim: Int
            anim = animator.GetAnim(animname)
            if (0 == anim) {
                Game_local.gameLocal.Warning("missing '%s' animation on '%s' (%s)", animname, name, GetEntityDefName())
                animator.Clear(channel, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = 0
            } else {
                if (!(owner != null && owner.GetInfluenceLevel() != 0)) {
                    Show()
                }
                animator.CycleAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                animDoneTime = animator.CurrentAnim(channel).GetEndTime()
                if (worldModel.GetEntity() != null) {
                    anim = worldModel.GetEntity().GetAnimator().GetAnim(animname)
                    worldModel.GetEntity().GetAnimator()
                        .CycleAnim(channel, anim, Game_local.gameLocal.time, Anim.FRAME2MS(animBlendFrames))
                }
            }
            animBlendFrames = 0
            idThread.Companion.ReturnInt(0)
        }

        private fun Event_AnimDone(channel: idEventArg<Int?>?, blendFrames: idEventArg<Int?>?) {
            idThread.Companion.ReturnInt(animDoneTime - Anim.FRAME2MS(blendFrames.value) <= Game_local.gameLocal.time)
        }

        private fun Event_SetBlendFrames(channel: idEventArg<Int?>?, blendFrames: idEventArg<Int?>?) {
            animBlendFrames = blendFrames.value
        }

        private fun Event_GetBlendFrames(channel: idEventArg<Int?>?) {
            idThread.Companion.ReturnInt(animBlendFrames)
        }

        private fun Event_Next() {
            // change to another weapon if possible
            owner.NextBestWeapon()
        }

        private fun Event_SetSkin(_skinname: idEventArg<String?>?) {
            val skinname = _skinname.value
            val skinDecl: idDeclSkin?
            skinDecl = if (!TempDump.isNotNullOrEmpty(skinname)) {
                null
            } else {
                DeclManager.declManager.FindSkin(skinname)
            }
            renderEntity.customSkin = skinDecl
            UpdateVisuals()
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().SetSkin(skinDecl)
            }
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.WriteLong(
                    if (skinDecl != null) Game_local.gameLocal.ServerRemapDecl(
                        -1,
                        declType_t.DECL_SKIN,
                        skinDecl.Index()
                    ) else -1
                )
                ServerSendEvent(EVENT_CHANGESKIN, msg, false, -1)
            }
        }

        private fun Event_Flashlight(enable: idEventArg<Int?>?) {
            if (enable.value != 0) {
                lightOn = true
                MuzzleFlashLight()
            } else {
                lightOn = false
                muzzleFlashEnd = 0
            }
        }

        private fun Event_GetLightParm(_parmnum: idEventArg<Int?>?) {
            val parmnum: Int = _parmnum.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Companion.Error("shader parm index (%d) out of range", parmnum)
            }
            idThread.Companion.ReturnFloat(muzzleFlash.shaderParms[parmnum])
        }

        private fun Event_SetLightParm(_parmnum: idEventArg<Int?>?, _value: idEventArg<Float?>?) {
            val parmnum: Int = _parmnum.value
            val value: Float = _value.value
            if (parmnum < 0 || parmnum >= Material.MAX_ENTITY_SHADER_PARMS) {
                idGameLocal.Companion.Error("shader parm index (%d) out of range", parmnum)
            }
            muzzleFlash.shaderParms[parmnum] = value
            worldMuzzleFlash.shaderParms[parmnum] = value
            UpdateVisuals()
        }

        private fun Event_SetLightParms(
            parm0: idEventArg<Float?>?,
            parm1: idEventArg<Float?>?,
            parm2: idEventArg<Float?>?,
            parm3: idEventArg<Float?>?
        ) {
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = parm0.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2.value
            muzzleFlash.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_RED] = parm0.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_GREEN] = parm1.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_BLUE] = parm2.value
            worldMuzzleFlash.shaderParms[RenderWorld.SHADERPARM_ALPHA] = parm3.value
            UpdateVisuals()
        }

        private fun Event_LaunchProjectiles(
            _num_projectiles: idEventArg<Int?>?,
            _spread: idEventArg<Float?>?,
            fuseOffset: idEventArg<Float?>?,
            launchPower: idEventArg<Float?>?,
            _dmgPower: idEventArg<Float?>?
        ) {
            val num_projectiles: Int = _num_projectiles.value
            val spread: Float = _spread.value
            var dmgPower: Float = _dmgPower.value
            var proj: idProjectile?
            val ent = arrayOfNulls<idEntity?>(1)
            var i: Int
            val dir = idVec3()
            var ang: Float
            var spin: Float
            val distance = CFloat()
            val tr = trace_s()
            val start = idVec3()
            val muzzle_pos = idVec3()
            val ownerBounds: idBounds?
            var projBounds: idBounds?
            if (IsHidden()) {
                return
            }
            if (0 == projectileDict.GetNumKeyVals()) {
                val classname = weaponDef.dict.GetString("classname")
                Game_local.gameLocal.Warning("No projectile defined on '%s'", classname)
                return
            }

            // avoid all ammo considerations on an MP client
            if (!Game_local.gameLocal.isClient) {

                // check if we're out of ammo or the clip is empty
                val ammoAvail = owner.inventory.HasAmmo(ammoType, ammoRequired)
                if (0 == ammoAvail || clipSize != 0 && ammoClip <= 0) {
                    return
                }

                // if this is a power ammo weapon ( currently only the bfg ) then make sure
                // we only fire as much power as available in each clip
                if (powerAmmo) {
                    // power comes in as a float from zero to max
                    // if we use this on more than the bfg will need to define the max
                    // in the .def as opposed to just in the script so proper calcs
                    // can be done here.
                    dmgPower = (dmgPower.toInt() + 1).toFloat()
                    if (dmgPower > ammoClip) {
                        dmgPower = ammoClip.toFloat()
                    }
                }
                owner.inventory.UseAmmo(ammoType, (if (powerAmmo) dmgPower else ammoRequired).toInt())
                if (clipSize != 0 && ammoRequired != 0) {
                    ammoClip -= if (powerAmmo) dmgPower else 1
                }
            }
            if (!silent_fire) {
                // wake up nearby monsters
                Game_local.gameLocal.AlertAI(owner)
            }

            // set the shader parm to the time of last projectile firing,
            // which the gun material shaders can reference for single shot barrel glows, etc
            renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY] = Game_local.gameLocal.random.CRandomFloat()
            renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET] =
                -Math_h.MS2SEC(Game_local.gameLocal.realClientTime.toFloat())
            if (worldModel.GetEntity() != null) {
                worldModel.GetEntity().SetShaderParm(
                    RenderWorld.SHADERPARM_DIVERSITY,
                    renderEntity.shaderParms[RenderWorld.SHADERPARM_DIVERSITY]
                )
                worldModel.GetEntity().SetShaderParm(
                    RenderWorld.SHADERPARM_TIMEOFFSET,
                    renderEntity.shaderParms[RenderWorld.SHADERPARM_TIMEOFFSET]
                )
            }

            // calculate the muzzle position
            if (barrelJointView != Model.INVALID_JOINT && projectileDict.GetBool("launchFromBarrel")) {
                // there is an explicit joint for the muzzle
                GetGlobalJointTransform(true, barrelJointView, muzzleOrigin, muzzleAxis)
            } else {
                // go straight out of the view
                muzzleOrigin.oSet(playerViewOrigin)
                muzzleAxis.oSet(playerViewAxis)
            }

            // add some to the kick time, incrementally moving repeat firing weapons back
            if (kick_endtime < Game_local.gameLocal.realClientTime) {
                kick_endtime = Game_local.gameLocal.realClientTime
            }
            kick_endtime += muzzle_kick_time
            if (kick_endtime > Game_local.gameLocal.realClientTime + muzzle_kick_maxtime) {
                kick_endtime = Game_local.gameLocal.realClientTime + muzzle_kick_maxtime
            }
            if (Game_local.gameLocal.isClient) {

                // predict instant hit projectiles
                if (projectileDict.GetBool("net_instanthit")) {
                    val spreadRad = Math_h.DEG2RAD(spread)
                    muzzle_pos.oSet(muzzleOrigin.oPlus(playerViewAxis.oGet(0).oMultiply(2.0f)))
                    i = 0
                    while (i < num_projectiles) {
                        ang = idMath.Sin(spreadRad * Game_local.gameLocal.random.RandomFloat())
                        spin = Math_h.DEG2RAD(360.0f) * Game_local.gameLocal.random.RandomFloat()
                        dir.oSet(
                            playerViewAxis.oGet(0).oPlus(
                                playerViewAxis.oGet(2).oMultiply(ang * idMath.Sin(spin))
                                    .oMinus(playerViewAxis.oGet(1).oMultiply(ang * idMath.Cos(spin)))
                            )
                        )
                        dir.Normalize()
                        Game_local.gameLocal.clip.Translation(
                            tr,
                            muzzle_pos,
                            muzzle_pos.oPlus(dir.oMultiply(4096.0f)),
                            null,
                            idMat3.Companion.getMat3_identity(),
                            Game_local.MASK_SHOT_RENDERMODEL,
                            owner
                        )
                        if (tr.fraction < 1.0f) {
                            idProjectile.Companion.ClientPredictionCollide(
                                this,
                                projectileDict,
                                tr,
                                Vector.getVec3_origin(),
                                true
                            )
                        }
                        i++
                    }
                }
            } else {
                ownerBounds = owner.GetPhysics().GetAbsBounds()
                owner.AddProjectilesFired(num_projectiles)
                val spreadRad = Math_h.DEG2RAD(spread)
                i = 0
                while (i < num_projectiles) {
                    ang = idMath.Sin(spreadRad * Game_local.gameLocal.random.RandomFloat())
                    spin = Math_h.DEG2RAD(360.0f) * Game_local.gameLocal.random.RandomFloat()
                    dir.oSet(
                        playerViewAxis.oGet(0).oPlus(
                            playerViewAxis.oGet(2).oMultiply(ang * idMath.Sin(spin))
                                .oMinus(playerViewAxis.oGet(1).oMultiply(ang * idMath.Cos(spin)))
                        )
                    )
                    dir.Normalize()
                    if (projectileEnt != null) {
                        ent[0] = projectileEnt
                        ent[0].Show()
                        ent[0].Unbind()
                        projectileEnt = null
                    } else {
                        Game_local.gameLocal.SpawnEntityDef(projectileDict, ent, false)
                    }
                    if (null == ent || ent[0] !is idProjectile) {
                        val projectileName = weaponDef.dict.GetString("def_projectile")
                        idGameLocal.Companion.Error("'%s' is not an idProjectile", projectileName)
                    }
                    if (projectileDict.GetBool("net_instanthit")) {
                        // don't synchronize this on top of the already predicted effect
                        ent[0].fl.networkSync = false
                    }
                    proj = ent[0] as idProjectile?
                    proj.Create(owner, muzzleOrigin, dir)
                    projBounds = proj.GetPhysics().GetBounds().Rotate(proj.GetPhysics().GetAxis())

                    // make sure the projectile starts inside the bounding box of the owner
                    if (i == 0) {
                        muzzle_pos.oSet(muzzleOrigin.oPlus(playerViewAxis.oGet(0).oMultiply(2.0f)))
                        if (ownerBounds.oMinus(projBounds)
                                .RayIntersection(muzzle_pos, playerViewAxis.oGet(0), distance)
                        ) {
                            start.oSet(muzzle_pos.oPlus(playerViewAxis.oGet(0).oMultiply(distance.getVal())))
                        } else {
                            start.oSet(ownerBounds.GetCenter())
                        }
                        Game_local.gameLocal.clip.Translation(
                            tr,
                            start,
                            muzzle_pos,
                            proj.GetPhysics().GetClipModel(),
                            proj.GetPhysics().GetClipModel().GetAxis(),
                            Game_local.MASK_SHOT_RENDERMODEL,
                            owner
                        )
                        muzzle_pos.oSet(tr.endpos)
                    }
                    proj.Launch(muzzle_pos, dir, pushVelocity, fuseOffset.value, launchPower.value, dmgPower)
                    i++
                }

                // toss the brass
                PostEventMS(Weapon.EV_Weapon_EjectBrass, brassDelay)
            }

            // add the light for the muzzleflash
            if (!lightOn) {
                MuzzleFlashLight()
            }
            owner.WeaponFireFeedback(weaponDef.dict)

            // reset muzzle smoke
            weaponSmokeStartTime = Game_local.gameLocal.realClientTime
        }

        private fun Event_CreateProjectile() {
            if (!Game_local.gameLocal.isClient) {
                val projectileEnt2 = arrayOf<idEntity?>(null)
                Game_local.gameLocal.SpawnEntityDef(projectileDict, projectileEnt2, false)
                projectileEnt = projectileEnt2[0]
                if (projectileEnt != null) {
                    projectileEnt.SetOrigin(GetPhysics().GetOrigin())
                    projectileEnt.Bind(owner, false)
                    projectileEnt.Hide()
                }
                idThread.Companion.ReturnEntity(projectileEnt)
            } else {
                idThread.Companion.ReturnEntity(null)
            }
        }

        /*
         ================
         idWeapon::Event_EjectBrass

         Toss a shell model out from the breach if the bone is present
         ================
         */
        private fun Event_EjectBrass() {
            if (!SysCvar.g_showBrass.GetBool() || !owner.CanShowWeaponViewmodel()) {
                return
            }
            if (ejectJointView == Model.INVALID_JOINT || 0 == brassDict.GetNumKeyVals()) {
                return
            }
            if (Game_local.gameLocal.isClient) {
                return
            }
            val axis = idMat3()
            val origin = idVec3()
            val linear_velocity = idVec3()
            val angular_velocity = idVec3()
            val ent = arrayOf<idEntity?>(null)
            if (!GetGlobalJointTransform(true, ejectJointView, origin, axis)) {
                return
            }
            Game_local.gameLocal.SpawnEntityDef(brassDict, ent, false)
            if (TempDump.NOT(ent[0]) || ent[0] !is idDebris) {
                idGameLocal.Companion.Error(
                    "'%s' is not an idDebris",
                    if (weaponDef != null) weaponDef.dict.GetString("def_ejectBrass") else "def_ejectBrass"
                )
            }
            val debris = ent[0] as idDebris?
            debris.Create(owner, origin, axis)
            debris.Launch()
            linear_velocity.oSet(
                playerViewAxis.oGet(0).oPlus(playerViewAxis.oGet(1).oPlus(playerViewAxis.oGet(2))).oMultiply(40f)
            )
            angular_velocity.Set(
                10 * Game_local.gameLocal.random.CRandomFloat(),
                10 * Game_local.gameLocal.random.CRandomFloat(),
                10 * Game_local.gameLocal.random.CRandomFloat()
            )
            debris.GetPhysics().SetLinearVelocity(linear_velocity)
            debris.GetPhysics().SetAngularVelocity(angular_velocity)
        }

        private fun Event_Melee() {
            val ent: idEntity?
            val tr = trace_s()
            if (null == meleeDef) {
                idGameLocal.Companion.Error("No meleeDef on '%s'", weaponDef.dict.GetString("classname"))
            }
            if (!Game_local.gameLocal.isClient) {
                val start = idVec3(playerViewOrigin)
                val end = idVec3(
                    start.oPlus(
                        playerViewAxis.oGet(0).oMultiply(meleeDistance * owner.PowerUpModifier(Player.MELEE_DISTANCE))
                    )
                )
                Game_local.gameLocal.clip.TracePoint(tr, start, end, Game_local.MASK_SHOT_RENDERMODEL, owner)
                ent = if (tr.fraction < 1.0f) {
                    Game_local.gameLocal.GetTraceEntity(tr)
                } else {
                    null
                }
                if (SysCvar.g_debugWeapon.GetBool()) {
                    Game_local.gameRenderWorld.DebugLine(Lib.Companion.colorYellow, start, end, 100)
                    if (ent != null) {
                        Game_local.gameRenderWorld.DebugBounds(
                            Lib.Companion.colorRed,
                            ent.GetPhysics().GetBounds(),
                            ent.GetPhysics().GetOrigin(),
                            100
                        )
                    }
                }
                var hit = false
                var hitSound = meleeDef.dict.GetString("snd_miss")
                if (ent != null) {
                    val push = meleeDef.dict.GetFloat("push")
                    val impulse = idVec3(tr.c.normal.oMultiply(-push * owner.PowerUpModifier(Player.SPEED)))
                    if (Game_local.gameLocal.world.spawnArgs.GetBool("no_Weapons") && (ent is idActor || ent is idAFAttachment)) {
                        idThread.Companion.ReturnInt(0)
                        return
                    }
                    ent.ApplyImpulse(this, tr.c.id, tr.c.point, impulse)

                    // weapon stealing - do this before damaging so weapons are not dropped twice
                    if (Game_local.gameLocal.isMultiplayer
                        && weaponDef != null && weaponDef.dict.GetBool("stealing")
                        && ent is idPlayer
                        && !owner.PowerUpActive(Player.BERSERK)
                        && (Game_local.gameLocal.gameType != gameType_t.GAME_TDM || Game_local.gameLocal.serverInfo.GetBool(
                            "si_teamDamage"
                        ) || owner.team != (ent as idPlayer?).team)
                    ) {
                        owner.StealWeapon(ent as idPlayer?)
                    }
                    if (ent.fl.takedamage) {
                        val kickDir = idVec3()
                        val globalKickDir = idVec3()
                        meleeDef.dict.GetVector("kickDir", "0 0 0", kickDir)
                        globalKickDir.oSet(muzzleAxis.oMultiply(kickDir))
                        ent.Damage(
                            owner,
                            owner,
                            globalKickDir,
                            meleeDefName.toString(),
                            owner.PowerUpModifier(Player.MELEE_DAMAGE),
                            tr.c.id
                        )
                        hit = true
                    }
                    if (weaponDef.dict.GetBool("impact_damage_effect")) {
                        if (ent.spawnArgs.GetBool("bleed")) {
                            hitSound =
                                meleeDef.dict.GetString(if (owner.PowerUpActive(Player.BERSERK)) "snd_hit_berserk" else "snd_hit")
                            ent.AddDamageEffect(tr, impulse, meleeDef.dict.GetString("classname"))
                        } else {
                            var type = tr.c.material.GetSurfaceType()
                            if (type == surfTypes_t.SURFTYPE_NONE) {
                                type = surfTypes_t.values()[GetDefaultSurfaceType()]
                            }
                            val materialType = Game_local.gameLocal.sufaceTypeNames[type.ordinal]

                            // start impact sound based on material type
                            hitSound = meleeDef.dict.GetString(Str.va("snd_%s", materialType))
                            if (TempDump.isNotNullOrEmpty(hitSound)) {
                                hitSound = meleeDef.dict.GetString("snd_metal")
                            }
                            if (Game_local.gameLocal.time > nextStrikeFx) {
                                val decal: String?
                                // project decal
                                decal = weaponDef.dict.GetString("mtr_strike")
                                if (TempDump.isNotNullOrEmpty(decal)) {
                                    Game_local.gameLocal.ProjectDecal(
                                        tr.c.point,
                                        tr.c.normal.oNegative(),
                                        8.0f,
                                        true,
                                        6.0f,
                                        decal
                                    )
                                }
                                nextStrikeFx = Game_local.gameLocal.time + 200
                            } else {
                                hitSound = ""
                            }
                            strikeSmokeStartTime = Game_local.gameLocal.time
                            strikePos.oSet(tr.c.point)
                            strikeAxis.oSet(tr.endAxis.oNegative())
                        }
                    }
                }
                if (TempDump.isNotNullOrEmpty(hitSound)) {
                    val snd = DeclManager.declManager.FindSound(hitSound)
                    StartSoundShader(snd, gameSoundChannel_t.SND_CHANNEL_BODY2, 0, true, null)
                }
                idThread.Companion.ReturnInt(hit)
                owner.WeaponFireFeedback(weaponDef.dict)
                return
            }
            idThread.Companion.ReturnInt(0)
            owner.WeaponFireFeedback(weaponDef.dict)
        }

        private fun Event_GetWorldModel() {
            idThread.Companion.ReturnEntity(worldModel.GetEntity())
        }

        private fun Event_AllowDrop(allow: idEventArg<Int?>?) {
            allowDrop = allow.value != 0
        }

        private fun Event_AutoReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isClient) {
                idThread.Companion.ReturnFloat(0.0f)
                return
            }
            idThread.Companion.ReturnFloat(
                TempDump.btoi(Game_local.gameLocal.userInfo[owner.entityNumber].GetBool("ui_autoReload")).toFloat()
            )
        }

        private fun Event_NetReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isServer) {
                ServerSendEvent(EVENT_RELOAD, null, false, -1)
            }
        }

        private fun Event_IsInvisible() {
            if (null == owner) {
                idThread.Companion.ReturnFloat(0f)
                return
            }
            idThread.Companion.ReturnFloat(if (owner.PowerUpActive(Player.INVISIBILITY)) 1 else 0.toFloat())
        }

        private fun Event_NetEndReload() {
            assert(owner != null)
            if (Game_local.gameLocal.isServer) {
                ServerSendEvent(EVENT_ENDRELOAD, null, false, -1)
            }
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef?): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        /* **********************************************************************

         init

         ***********************************************************************/
        init {
            worldModel = idEntityPtr(null)
            weaponDef = null
            thread = null
            guiLight = renderLight_s() //memset( &guiLight, 0, sizeof( guiLight ) );
            muzzleFlash = renderLight_s() //memset( &muzzleFlash, 0, sizeof( muzzleFlash ) );
            worldMuzzleFlash = renderLight_s() //memset( &worldMuzzleFlash, 0, sizeof( worldMuzzleFlash ) );
            nozzleGlow = renderLight_s() //memset( &nozzleGlow, 0, sizeof( nozzleGlow ) );
            muzzleFlashEnd = 0
            flashColor = Vector.getVec3_origin()
            muzzleFlashHandle = -1
            worldMuzzleFlashHandle = -1
            guiLightHandle = -1
            nozzleGlowHandle = -1
            modelDefHandle = -1
            berserk = 2
            brassDelay = 0
            allowDrop = true
            state = idStr()
            idealState = idStr()
            playerViewOrigin = idVec3()
            playerViewAxis = idMat3()
            viewWeaponOrigin = idVec3()
            viewWeaponAxis = idMat3()
            muzzleOrigin = idVec3()
            muzzleAxis = idMat3()
            pushVelocity = idVec3()
            projectileDict = idDict()
            meleeDefName = idStr()
            brassDict = idDict()
            icon = idStr()
            muzzle_kick_angles = idAngles()
            muzzle_kick_offset = idVec3()
            strikePos = idVec3()
            nozzleGlowColor = idVec3()
            Clear()
            fl.networkSync = true
        }
    }
}