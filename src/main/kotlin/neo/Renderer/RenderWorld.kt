package neo.Renderer

import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.tr_local.areaReference_s
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Sound.sound.idSoundEmitter
import neo.TempDump.Atomics.*
import neo.TempDump.SERiAL
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common.Companion.common
import neo.framework.DeclManager
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.DemoFile.idDemoFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.CmdArgs
import neo.idlib.Lib.idException
import neo.idlib.containers.CInt
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.getVec3_origin
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.UserInterface.idUserInterface
import java.nio.*
import java.util.*

/**
 *
 */
object RenderWorld {
    //
    // shader parms
    val MAX_GLOBAL_SHADER_PARMS: Int = 12

    //
    // guis
    val MAX_RENDERENTITY_GUI: Int = 3

    /*
     ===============================================================================

     Render World

     ===============================================================================
     */
    val PROC_FILE_EXT: String = "proc"
    val PROC_FILE_ID: String = "mapProcFile003"
    val SHADERPARM_ALPHA: Int = 3

    //
    val SHADERPARM_BEAM_END_X: Int = 8 // for _beam models
    val SHADERPARM_BEAM_END_Y: Int = 9
    val SHADERPARM_BEAM_END_Z: Int = 10
    val SHADERPARM_BEAM_WIDTH: Int = 11
    val SHADERPARM_BLUE: Int = 2
    val SHADERPARM_DIVERSITY: Int = 5 // random between 0.0 and 1.0 for some effects (muzzle flashes, etc)
    val SHADERPARM_GREEN: Int = 1
    val SHADERPARM_MD3_BACKLERP: Int = 10

    //
    val SHADERPARM_MD3_FRAME: Int = 8
    val SHADERPARM_MD3_LASTFRAME: Int = 9

    //
    // model parms
    val SHADERPARM_MD5_SKINSCALE: Int = 8 // for scaling vertex offsets on md5 models (jack skellington effect)
    val SHADERPARM_MODE: Int = 7 // for selecting which shader passes to enable

    //
    val SHADERPARM_PARTICLE_STOPTIME: Int = 8 // don't spawn any more particles after this time

    //
    val SHADERPARM_RED: Int = 0
    val SHADERPARM_SPRITE_HEIGHT: Int = 9

    //
    val SHADERPARM_SPRITE_WIDTH: Int = 8
    val SHADERPARM_TIMEOFFSET: Int = 4
    val SHADERPARM_TIMESCALE: Int = 3
    val SHADERPARM_TIME_OF_DEATH: Int = 7 // for the monster skin-burn-away effect enable and time offset

    //    
    val NUM_PORTAL_ATTRIBUTES: Int = 3 //PS_BLOCK_ALL needs to be changed manually if this value is changed.

    /*
     ===============
     R_GlobalShaderOverride
     ===============
     */
    @Throws(idException::class)
    fun R_GlobalShaderOverride(shader: Array<idMaterial?>): Boolean {
        if (!shader[0]!!.IsDrawn()) {
            return false
        }
        if (tr_local.tr.primaryRenderView!!.globalMaterial != null) {
            shader[0] = tr_local.tr.primaryRenderView!!.globalMaterial
            return true
        }
        if (RenderSystem_init.r_materialOverride!!.GetString() != null && !RenderSystem_init.r_materialOverride!!.GetString()!!
                .isEmpty()
        ) {
            shader[0] = DeclManager.declManager.FindMaterial(RenderSystem_init.r_materialOverride!!.GetString()!!)
            return true
        }
        return false
    }

    /*
     ===============
     R_RemapShaderBySkin
     ===============
     */
    fun R_RemapShaderBySkin(shader: idMaterial?, skin: idDeclSkin?, customShader: idMaterial?): idMaterial? {
        if (null == shader) {
            return null
        }

        // never remap surfaces that were originally nodraw, like collision hulls
        if (!shader.IsDrawn()) {
            return shader
        }
        if (customShader != null) {
            // this is sort of a hack, but cause deformed surfaces to map to empty surfaces,
            // so the item highlight overlay doesn't highlight the autosprite surface
            if (shader.Deform() != null) {
                return null
            }
            return customShader
        }
        if (null == skin /*|| null == shader*/) {
            return shader
        }
        return skin.RemapShaderBySkin(shader)
    }

    enum class portalConnection_t {
        PS_BLOCK_NONE,

        // = 0,
        //
        PS_BLOCK_VIEW,

        // = 1,
        PS_BLOCK_LOCATION,
        // = 2,  // game map location strings often stop in hallways
        /**
         * padding
         */
        __3,
        PS_BLOCK_AIR,
        // = 4,       // windows between pressurized and unpresurized areas
        //
        /**
         * padding
         */
        __5,

        /**
         * padding
         */
        __6,
        PS_BLOCK_ALL //= (1 << NUM_PORTAL_ATTRIBUTES) - 1
    }

    abstract class deferredEntityCallback_t() : SERiAL {
        abstract fun run(e: renderEntity_s?, v: renderView_s?): Boolean
    }

    class renderEntity_s {
        val shaderParms: FloatArray =
            FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader or model generation
        private val DBG_count: Int = DBG_counter++

        //
        // if non-zero, the surface and shadow (if it casts one)
        // will only show up in the specific view, ie: player weapons
        var allowSurfaceInViewID: Int = 0
        var axis: idMat3
        var bodyId: Int = 0

        //
        // Entities that are expensive to generate, like skeletal models, can be
        // deferred until their bounds are found to be in view, in the frustum
        // of a shadowing light that is in view, or contacted by a trace / overlay test.
        // This is also used to do visual cueing on items in the view
        // The renderView may be NULL if the callback is being issued for a non-view related
        // source.
        // The callback function should clear renderEntity->callback if it doesn't
        // want to be called again next time the entity is referenced (ie, if the
        // callback has now made the entity valid until the next updateEntity)
        var bounds // only needs to be set for deferred models and md5s
                : idBounds
        var callback: deferredEntityCallback_t? = null

        //
        var callbackData: ByteBuffer? = null // used for whatever the callback wants

        //
        // texturing
        var customShader: idMaterial? = null // if non-0, all surfaces will use this
        var customSkin: idDeclSkin? = null // 0 for no remappings

        //
        var entityNum: Int = 0

        // this automatically implies noShadow
        var forceUpdate: Int = 0 // force an update (NOTE: not a bool to keep this struct a multiple of 4 bytes)//TODO:

        // networking: see WriteGUIToSnapshot / ReadGUIFromSnapshot
        var gui: Array<idUserInterface?> = arrayOfNulls(RenderWorld.MAX_RENDERENTITY_GUI)
        var hModel: idRenderModel? = null // this can only be null if callback is set
        var joints // array of joints that will modify vertices.
                : Array<idJointMat?>? = null

        // NULL if non-deformable model.  NOT freed by renderer
        //
        var modelDepthHack: Float = 0f // squash depth range so particle effects don't clip into walls

        //
        var noDynamicInteractions: Boolean = false // don't create any light / shadow interactions after

        //
        // options to override surface shader flags (replace with material parameters?)
        var noSelfShadow: Boolean = false // cast shadows onto other objects,but not self
        var noShadow: Boolean = false // no shadow at all

        //
        var numJoints: Int = 0

        //
        // positioning
        // axis rotation vectors must be unit length for many
        // R_LocalToGlobal functions to work, so don't scale models!
        // axis vectors are [0] = forward, [1] = left, [2] = up
        val origin: idVec3
        var referenceShader: idMaterial? = null // used so flares can reference the proper light shader
        var referenceSound: idSoundEmitter? = null // for shader sound tables, allowing effects to vary with sounds

        //
        var remoteRenderView: renderView_s? = null // any remote camera surfaces will use this

        //
        // world models for the player and weapons will not cast shadows from view weapon
        // muzzle flashes
        var suppressShadowInLightID: Int = 0
        var suppressShadowInViewID: Int = 0

        //
        // player bodies and possibly player shadows should be suppressed in views from
        // that player's eyes, but will show up in mirrors and other subviews
        // security cameras could suppress their model in their subviews if we add a way
        // of specifying a view number for a remoteRenderMap view
        var suppressSurfaceInViewID: Int = 0
        var timeGroup: Int = 0

        // the level load is completed.  This is a performance hack
        // for the gigantic outdoor meshes in the monorail map, so
        // all the lights in the moving monorail don't touch the meshes
        //
        var weaponDepthHack: Boolean = false // squash depth range so view weapons don't poke into walls
        var xrayIndex: Int = 0

        constructor() {
            origin = idVec3()
            axis = idMat3()
            bounds = idBounds()
        }

        constructor(newEntity: renderEntity_s) {
            hModel = newEntity.hModel
            entityNum = newEntity.entityNum
            bodyId = newEntity.bodyId
            bounds = idBounds(newEntity.bounds)
            callback = newEntity.callback
            callbackData = newEntity.callbackData
            suppressSurfaceInViewID = newEntity.suppressSurfaceInViewID
            suppressShadowInViewID = newEntity.suppressShadowInViewID
            suppressShadowInLightID = newEntity.suppressShadowInLightID
            allowSurfaceInViewID = newEntity.allowSurfaceInViewID
            origin = idVec3(newEntity.origin)
            axis = idMat3(newEntity.axis)
            customShader = newEntity.customShader
            referenceShader = newEntity.referenceShader
            customSkin = newEntity.customSkin
            referenceSound = newEntity.referenceSound
            System.arraycopy(newEntity.shaderParms, 0, shaderParms, 0, shaderParms.size)
            for (i in gui.indices) {
                gui[i] = newEntity.gui[i]
            }
            remoteRenderView = newEntity.remoteRenderView
            numJoints = newEntity.numJoints
            joints = newEntity.joints
            modelDepthHack = newEntity.modelDepthHack
            noSelfShadow = newEntity.noSelfShadow
            noShadow = newEntity.noShadow
            noDynamicInteractions = newEntity.noDynamicInteractions
            weaponDepthHack = newEntity.weaponDepthHack
            forceUpdate = newEntity.forceUpdate
            timeGroup = newEntity.timeGroup
            xrayIndex = newEntity.xrayIndex
        }

        fun atomicSet(shadow: renderEntityShadow) {
            hModel = shadow.hModel
            entityNum = shadow.entityNum._val
            bodyId = shadow.bodyId._val
            bounds = shadow.bounds
            callback = shadow.callback
            callbackData = shadow.callbackData
            suppressSurfaceInViewID = shadow.suppressSurfaceInViewID._val
            suppressShadowInViewID = shadow.suppressShadowInViewID._val
            suppressShadowInLightID = shadow.suppressShadowInLightID._val
            allowSurfaceInViewID = shadow.allowSurfaceInViewID._val
            origin.set(shadow.origin)
            axis = shadow.axis
            customShader = shadow.customShader
            referenceShader = shadow.referenceShader
            customSkin = shadow.customSkin
            referenceSound = shadow.referenceSound
            remoteRenderView = shadow.remoteRenderView
            numJoints = shadow.numJoints._val
            joints = shadow.joints as Array<idJointMat?>
            modelDepthHack = shadow.modelDepthHack._val
            noSelfShadow = shadow.noSelfShadow._val
            noShadow = shadow.noShadow._val
            noDynamicInteractions = shadow.noDynamicInteractions._val
            weaponDepthHack = shadow.weaponDepthHack._val
            forceUpdate = shadow.forceUpdate._val
            timeGroup = shadow.timeGroup._val
            xrayIndex = shadow.xrayIndex._val
        }

        fun clear() {
            val newEntity: renderEntity_s = renderEntity_s()
            hModel = newEntity.hModel
            entityNum = newEntity.entityNum
            bodyId = newEntity.bodyId
            bounds = newEntity.bounds
            callback = newEntity.callback
            callbackData = newEntity.callbackData
            suppressSurfaceInViewID = newEntity.suppressSurfaceInViewID
            suppressShadowInViewID = newEntity.suppressShadowInViewID
            suppressShadowInLightID = newEntity.suppressShadowInLightID
            allowSurfaceInViewID = newEntity.allowSurfaceInViewID
            origin.set(newEntity.origin)
            axis = newEntity.axis
            customShader = newEntity.customShader
            referenceShader = newEntity.referenceShader
            customSkin = newEntity.customSkin
            referenceSound = newEntity.referenceSound
            remoteRenderView = newEntity.remoteRenderView
            numJoints = newEntity.numJoints
            joints = newEntity.joints
            modelDepthHack = newEntity.modelDepthHack
            noSelfShadow = newEntity.noSelfShadow
            noShadow = newEntity.noShadow
            noDynamicInteractions = newEntity.noDynamicInteractions
            weaponDepthHack = newEntity.weaponDepthHack
            forceUpdate = newEntity.forceUpdate
            timeGroup = newEntity.timeGroup
            xrayIndex = newEntity.xrayIndex
        }

        public override fun hashCode(): Int {
            var hash: Int = 7
            hash = 71 * hash + Objects.hashCode(hModel)
            hash = 71 * hash + entityNum
            hash = 71 * hash + bodyId
            hash = 71 * hash + Objects.hashCode(bounds)
            hash = 71 * hash + Objects.hashCode(callback)
            hash = 71 * hash + Objects.hashCode(callbackData)
            hash = 71 * hash + suppressSurfaceInViewID
            hash = 71 * hash + suppressShadowInViewID
            hash = 71 * hash + suppressShadowInLightID
            hash = 71 * hash + allowSurfaceInViewID
            hash = 71 * hash + Objects.hashCode(origin)
            hash = 71 * hash + Objects.hashCode(axis)
            hash = 71 * hash + Objects.hashCode(customShader)
            hash = 71 * hash + Objects.hashCode(referenceShader)
            hash = 71 * hash + Objects.hashCode(customSkin)
            hash = 71 * hash + Objects.hashCode(referenceSound)
            hash = 71 * hash + shaderParms.contentHashCode()
            hash = 71 * hash + gui.contentDeepHashCode()
            hash = 71 * hash + Objects.hashCode(remoteRenderView)
            hash = 71 * hash + numJoints
            hash = 71 * hash + joints.contentDeepHashCode()
            hash = 71 * hash + java.lang.Float.floatToIntBits(modelDepthHack)
            hash = 71 * hash + (if (noSelfShadow) 1 else 0)
            hash = 71 * hash + (if (noShadow) 1 else 0)
            hash = 71 * hash + (if (noDynamicInteractions) 1 else 0)
            hash = 71 * hash + (if (weaponDepthHack) 1 else 0)
            hash = 71 * hash + forceUpdate
            hash = 71 * hash + timeGroup
            hash = 71 * hash + xrayIndex
            return hash
        }

        public override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other: renderEntity_s = obj as renderEntity_s
            if (!Objects.equals(hModel, other.hModel)) {
                return false
            }
            if (entityNum != other.entityNum) {
                return false
            }
            if (bodyId != other.bodyId) {
                return false
            }
            if (!Objects.equals(bounds, other.bounds)) {
                return false
            }
            if (!Objects.equals(callback, other.callback)) {
                return false
            }
            if (!Objects.equals(callbackData, other.callbackData)) {
                return false
            }
            if (suppressSurfaceInViewID != other.suppressSurfaceInViewID) {
                return false
            }
            if (suppressShadowInViewID != other.suppressShadowInViewID) {
                return false
            }
            if (suppressShadowInLightID != other.suppressShadowInLightID) {
                return false
            }
            if (allowSurfaceInViewID != other.allowSurfaceInViewID) {
                return false
            }
            if (!Objects.equals(origin, other.origin)) {
                return false
            }
            if (!Objects.equals(axis, other.axis)) {
                return false
            }
            if (!Objects.equals(customShader, other.customShader)) {
                return false
            }
            if (!Objects.equals(referenceShader, other.referenceShader)) {
                return false
            }
            if (!Objects.equals(customSkin, other.customSkin)) {
                return false
            }
            if (!Objects.equals(referenceSound, other.referenceSound)) {
                return false
            }
            if (!shaderParms.contentEquals(other.shaderParms)) {
                return false
            }
            if (!gui.contentDeepEquals(other.gui)) {
                return false
            }
            if (!Objects.equals(remoteRenderView, other.remoteRenderView)) {
                return false
            }
            if (numJoints != other.numJoints) {
                return false
            }
            if (!joints.contentDeepEquals(other.joints)) {
                return false
            }
            if (java.lang.Float.floatToIntBits(modelDepthHack) != java.lang.Float.floatToIntBits(other.modelDepthHack)) {
                return false
            }
            if (noSelfShadow != other.noSelfShadow) {
                return false
            }
            if (noShadow != other.noShadow) {
                return false
            }
            if (noDynamicInteractions != other.noDynamicInteractions) {
                return false
            }
            if (weaponDepthHack != other.weaponDepthHack) {
                return false
            }
            if (forceUpdate != other.forceUpdate) {
                return false
            }
            if (timeGroup != other.timeGroup) {
                return false
            }
            return xrayIndex == other.xrayIndex
        }

        companion object {
            private var DBG_counter: Int = 0
        }
    }

    class renderLight_s {
        val end: idVec3 = idVec3()
        val lightCenter: idVec3 = idVec3() // offset the lighting direction for shading and
        val lightRadius: idVec3 = idVec3() // xyz radius for point lights
        val origin: idVec3 = idVec3()
        val right: idVec3 = idVec3()
        val shaderParms: FloatArray = FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader
        val start: idVec3 = idVec3()

        // shadows, relative to origin
        //
        // frustum definition for projected lights, all reletive to origin
        // FIXME: we should probably have real plane equations here, and offer
        // a helper function for conversion from this format
        val target: idVec3 = idVec3()
        val up: idVec3 = idVec3()

        //
        // if non-zero, the light will only show up in the specific view
        // which can allow player gun gui lights and such to not effect everyone
        var allowLightInViewID: Int = 0
        var axis: idMat3 = idMat3() // rotation vectors, must be unit length

        //
        // muzzle flash lights will not cast shadows from player and weapon world models
        var lightId: Int = 0

        //
        // I am sticking the four bools together so there are no unused gaps in
        // the padded structure, which could confuse the memcmp that checks for redundant
        // updates
        var noShadows: Boolean = false // (should we replace this with material parameters on the shader?)
        var noSpecular: Boolean = false // (should we replace this with material parameters on the shader?)
        var parallel: Boolean = false // lightCenter gives the direction to the light at infinity

        //
        var pointLight: Boolean =
            false // otherwise a projection light (should probably invert the sense of this, because points are way more common)

        //
        // Dmap will generate an optimized shadow volume named _prelight_<lightName>
        // for the light against all the _area* models in the map.  The renderer will
        // ignore this value if the light has been moved after initial creation
        var prelightModel: idRenderModel? = null
        var referenceSound: idSoundEmitter? = null // for shader sound tables, allowing effects to vary with sounds

        //
        //
        var shader: idMaterial? = null // NULL = either lights/defaultPointLight or lights/defaultProjectedLight

        //
        // if non-zero, the light will not show up in the specific view,
        // which may be used if we want to have slightly different muzzle
        // flash lights for the player and other views
        var suppressLightInViewID: Int = 0

        constructor()

        //copy constructor
        constructor(other: renderLight_s) {
            axis = idMat3(other.axis)
            origin.set(other.origin)
            suppressLightInViewID = other.suppressLightInViewID
            allowLightInViewID = other.allowLightInViewID
            noShadows = other.noShadows
            noSpecular = other.noSpecular
            pointLight = other.pointLight
            parallel = other.parallel
            lightRadius.set(other.lightRadius)
            lightCenter.set(other.lightCenter)
            target.set(other.target)
            right.set(other.right)
            up.set(other.up)
            start.set(other.start)
            end.set(other.end)
            prelightModel = other.prelightModel
            lightId = other.lightId
            shader = other.shader
            System.arraycopy(other.shaderParms, 0, shaderParms, 0, other.shaderParms.size)
            referenceSound = other.referenceSound
        }

        fun clear() { //TODO:hardcoded values
            val temp: renderLight_s = renderLight_s()
            axis = temp.axis
            origin.set(temp.origin)
            suppressLightInViewID = temp.suppressLightInViewID
            allowLightInViewID = temp.allowLightInViewID
            noShadows = temp.noShadows
            noSpecular = temp.noSpecular
            pointLight = temp.pointLight
            parallel = temp.parallel
            lightRadius.set(temp.lightRadius)
            lightCenter.set(temp.lightCenter)
            target.set(temp.target)
            right.set(temp.right)
            up.set(temp.up)
            start.set(temp.start)
            end.set(temp.end)
            prelightModel = temp.prelightModel
            lightId = temp.lightId
            shader = temp.shader
            referenceSound = temp.referenceSound
        }

        fun atomicSet(shadow: renderLightShadow) {
            axis = shadow.axis
            origin.set(shadow.origin)
            suppressLightInViewID = shadow.suppressLightInViewID._val
            allowLightInViewID = shadow.allowLightInViewID._val
            noShadows = shadow.noShadows._val
            noSpecular = shadow.noSpecular._val
            pointLight = shadow.pointLight._val
            parallel = shadow.parallel._val
            lightRadius.set(shadow.lightRadius)
            lightCenter.set(shadow.lightCenter)
            target.set(shadow.target)
            right.set(shadow.right)
            up.set(shadow.up)
            start.set(shadow.start)
            end.set(shadow.end)
            prelightModel = shadow.prelightModel
            lightId = shadow.lightId._val
            shader = shadow.shader
            referenceSound = shadow.referenceSound
        }
    }

    class renderView_s : SERiAL {
        val vieworg: idVec3 = idVec3()
        private val DBG_count: Int = DBG_counter++

        //
        var cramZNear: Boolean = false // for cinematics, we want to set ZNear much lower
        var forceUpdate: Boolean = false // for an update

        //
        var fov_x: Float = 0f
        var fov_y: Float = 0f
        var globalMaterial: idMaterial? = null // used to override everything draw
        var shaderParms: FloatArray =
            FloatArray(RenderWorld.MAX_GLOBAL_SHADER_PARMS) // can be used in any way by shader

        //
        // time in milliseconds for shader effects and other time dependent rendering issues
        var time: Int = 0
        var viewID: Int = 0
        var viewaxis: idMat3 = idMat3() // transformation matrix, view looks down the positive X axis

        //
        // sized from 0 to SCREEN_WIDTH / SCREEN_HEIGHT (640/480), not actual resolution
        var x: Int = 0
        var y: Int = 0
        var width: Int = 0
        var height: Int = 0

        constructor()
        constructor(renderView: renderView_s) {
            viewID = renderView.viewID
            x = renderView.x
            y = renderView.y
            width = renderView.width
            height = renderView.height
            fov_x = renderView.fov_x
            fov_y = renderView.fov_y
            vieworg.set((renderView.vieworg))
            viewaxis = idMat3(renderView.viewaxis)
            cramZNear = renderView.cramZNear
            forceUpdate = renderView.forceUpdate
            time = renderView.time
            globalMaterial = renderView.globalMaterial
        }

        fun atomicSet(shadow: renderViewShadow) {
            viewID = shadow.viewID._val
            x = shadow.x._val
            y = shadow.y._val
            width = shadow.width._val
            height = shadow.height._val
            fov_x = shadow.fov_x._val
            fov_y = shadow.fov_y._val
            vieworg.set((shadow.vieworg))
            viewaxis = idMat3(shadow.viewaxis)
            cramZNear = shadow.cramZNear._val
            forceUpdate = shadow.forceUpdate._val
            time = shadow.time._val
            for (a in 0 until RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                shaderParms[a] = shadow.shaderParms[a]._val
            }
            globalMaterial = shadow.globalMaterial
        }

        public override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        public override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        public override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            // player views will set this to a non-zero integer for model suppress / allow
            // subviews (mirrors, cameras, etc) will always clear it to zero
            private var DBG_counter: Int = 0
        }
    }

    // exitPortal_t is returned by idRenderWorld::GetPortal()
    class exitPortal_t() {
        var areas: IntArray = IntArray(2) // areas connected by this portal
        var blockingBits: Int = 0 // PS_BLOCK_VIEW, PS_BLOCK_AIR, etc
        var  /*qhandle_t */portalHandle: Int = 0
        var w: idWinding? = null // winding points have counter clockwise ordering seen from areas[0]
    }

    // guiPoint_t is returned by idRenderWorld::GuiTrace()
    class guiPoint_t() {
        var guiId: Int = 0 // id of gui ( 0, 1, or 2 ) that the trace happened against
        var x: Float = 0f
        var y: Float = 0f // 0.0 to 1.0 range if trace hit a gui, otherwise -1
    }

    // modelTrace_t is for tracing vs. visual geometry
    class modelTrace_s() {
        var entity: renderEntity_s? = null // render entity that was hit
        var fraction: Float = 0f // fraction of trace completed
        var jointNumber: Int = 0 // md5 joint nearest to the hit triangle
        var material: idMaterial? = null // material of hit surface
        val normal: idVec3 = idVec3() // hit triangle normal vector in global space
        val point: idVec3 = idVec3() // end point of trace in global space
        fun clear() {
            point.Zero()
            normal.Zero()
            material = idMaterial()
            entity = renderEntity_s()
            jointNumber = 0
            fraction = jointNumber.toFloat()
        }
    }

    abstract class idRenderWorld() {
        //	virtual					~idRenderWorld() {};
        // The same render world can be reinitialized as often as desired
        // a NULL or empty mapName will create an empty, single area world
        @Throws(idException::class)
        abstract fun InitFromMap(mapName: String?): Boolean

        //-------------- Entity and Light Defs -----------------
        // entityDefs and lightDefs are added to a given world to determine
        // what will be drawn for a rendered scene.  Most update work is defered
        // until it is determined that it is actually needed for a given view.
        abstract fun AddEntityDef(re: renderEntity_s): Int
        abstract fun UpdateEntityDef(entityHandle: Int, re: renderEntity_s)
        abstract fun FreeEntityDef(entityHandle: Int)
        abstract fun GetRenderEntity(entityHandle: Int): renderEntity_s?
        abstract fun AddLightDef(rlight: renderLight_s): Int
        abstract fun UpdateLightDef(lightHandle: Int, rlight: renderLight_s)
        abstract fun FreeLightDef(lightHandle: Int)
        abstract fun GetRenderLight(lightHandle: Int): renderLight_s?

        // Force the generation of all light / surface interactions at the start of a level
        // If this isn't called, they will all be dynamically generated
        abstract fun GenerateAllInteractions()

        // returns true if this area model needs portal sky to draw
        abstract fun CheckAreaForPortalSky(areaNum: Int): Boolean

        //-------------- Decals and Overlays  -----------------
        // Creates decals on all world surfaces that the winding projects onto.
        // The projection origin should be infront of the winding plane.
        // The decals are projected onto world geometry between the winding plane and the projection origin.
        // The decals are depth faded from the winding plane to a certain distance infront of the
        // winding plane and the same distance from the projection origin towards the winding.
        abstract fun ProjectDecalOntoWorld(
            winding: idFixedWinding,
            projectionOrigin: idVec3?,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        )

        // Creates decals on static models.
        abstract fun ProjectDecal(
            entityHandle: Int,
            winding: idFixedWinding,
            projectionOrigin: idVec3?,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        )

        // Creates overlays on dynamic models.
        abstract fun ProjectOverlay(
            entityHandle: Int,
            localTextureAxis: Array<idPlane?>? /*[2]*/,
            material: idMaterial?
        )

        // Removes all decals and overlays from the given entity def.
        abstract fun RemoveDecals(entityHandle: Int)

        //-------------- Scene Rendering -----------------
        // some calls to material functions use the current renderview time when servicing cinematics.  this function
        // ensures that any parms accessed (such as time) are properly set.
        abstract fun SetRenderView(renderView: renderView_s?)

        // rendering a scene may actually render multiple subviews for mirrors and portals, and
        // may render composite textures for gui console screens and light projections
        // It would also be acceptable to render a scene multiple times, for "rear view mirrors", etc
        abstract fun RenderScene(renderView: renderView_s)

        //-------------- Portal Area Information -----------------
        // returns the number of portals
        abstract fun NumPortals(): Int

        // returns 0 if no portal contacts the bounds
        // This is used by the game to identify portals that are contained
        // inside doors, so the connection between areas can be topologically
        // terminated when the door shuts.
        abstract fun FindPortal(b: idBounds?): Int

        // doors explicitly close off portals when shut
        // multiple bits can be set to block multiple things, ie: ( PS_VIEW | PS_LOCATION | PS_AIR )
        abstract fun SetPortalState(portal: Int, blockingBits: Int)
        abstract fun GetPortalState(portal: Int): Int

        // returns true only if a chain of portals without the given connection bits set
        // exists between the two areas (a door doesn't separate them, etc)
        abstract fun AreasAreConnected(areaNum1: Int, areaNum2: Int, connection: portalConnection_t): Boolean

        // returns the number of portal areas in a map, so game code can build information
        // tables for the different areas
        abstract fun NumAreas(): Int

        // Will return -1 if the point is not in an area, otherwise
        // it will return 0 <= value < NumAreas()
        abstract fun PointInArea(point: idVec3?): Int

        // fills the *areas array with the numbers of the areas the bounds cover
        // returns the total number of areas the bounds cover
        abstract fun BoundsInAreas(bounds: idBounds?, areas: IntArray?, maxAreas: Int): Int

        // Used by the sound system to do area flowing
        abstract fun NumPortalsInArea(areaNum: Int): Int

        // returns one portal from an area
        abstract fun GetPortal(areaNum: Int, portalNum: Int): exitPortal_t

        //-------------- Tracing  -----------------
        // Checks a ray trace against any gui surfaces in an entity, returning the
        // fraction location of the trace on the gui surface, or -1,-1 if no hit.
        // This doesn't do any occlusion testing, simply ignoring non-gui surfaces.
        // start / end are in global world coordinates.
        abstract fun GuiTrace(entityHandle: Int, start: idVec3?, end: idVec3?): guiPoint_t

        // Traces vs the render model, possibly instantiating a dynamic version, and returns true if something was hit
        abstract fun ModelTrace(
            trace: modelTrace_s,
            entityHandle: Int,
            start: idVec3?,
            end: idVec3?,
            radius: Float
        ): Boolean

        // Traces vs the whole rendered world. FIXME: we need some kind of material flags.
        abstract fun Trace(
            trace: modelTrace_s,
            start: idVec3,
            end: idVec3,
            radius: Float,
            skipDynamic: Boolean /*= true*/,
            skipPlayer: Boolean /* = false*/
        ): Boolean

        @JvmOverloads
        fun Trace(
            trace: modelTrace_s,
            start: idVec3,
            end: idVec3,
            radius: Float,
            skipDynamic: Boolean = true
        ): Boolean {
            return Trace(trace, start, end, radius, skipDynamic, false)
        }

        // Traces vs the world model bsp tree.
        abstract fun FastWorldTrace(trace: modelTrace_s, start: idVec3, end: idVec3): Boolean

        //-------------- Demo Control  -----------------
        // Writes a loadmap command to the demo, and clears archive counters.
        abstract fun StartWritingDemo(demo: idDemoFile?)
        abstract fun StopWritingDemo()

        // Returns true when demoRenderView has been filled in.
        // adds/updates/frees entityDefs and lightDefs based on the current demo file
        // and returns the renderView to be used to render this frame.
        // a demo file may need to be advanced multiple times if the framerate
        // is less than 30hz
        // demoTimeOffset will be set if a new map load command was processed before
        // the next renderScene
        abstract fun ProcessDemoCommand(
            readDemo: idDemoFile?,
            demoRenderView: renderView_s,
            demoTimeOffset: CInt
        ): Boolean

        // this is used to regenerate all interactions ( which is currently only done during influences ), there may be a less
        // expensive way to do it
        abstract fun RegenerateWorld()

        //-------------- Debug Visualization  -----------------
        // Line drawing for debug visualization
        abstract fun DebugClearLines(time: Int) // a time of 0 will clear all lines and text
        abstract fun DebugLine(
            color: idVec4?,
            start: idVec3?,
            end: idVec3?,
            lifetime: Int /*= 0*/,
            depthTest: Boolean /* = false*/
        )

        @JvmOverloads
        fun DebugLine(color: idVec4?, start: idVec3?, end: idVec3?, lifetime: Int = 0 /*= 0*/) {
            DebugLine(color, start, end, lifetime, false)
        }

        abstract fun DebugArrow(color: idVec4?, start: idVec3?, end: idVec3, size: Int, lifetime: Int /*= 0*/)
        fun DebugArrow(color: idVec4?, start: idVec3?, end: idVec3, size: Int) {
            DebugArrow(color, start, end, size, 0)
        }

        abstract fun DebugWinding(
            color: idVec4?,
            w: idWinding,
            origin: idVec3,
            axis: idMat3?,
            lifetime: Int /*= 0*/,
            depthTest: Boolean /*= false*/
        )

        @JvmOverloads
        fun DebugWinding(color: idVec4?, w: idWinding, origin: idVec3, axis: idMat3?, lifetime: Int = 0 /*= 0*/) {
            DebugWinding(color, w, origin, axis, lifetime, false)
        }

        abstract fun DebugCircle(
            color: idVec4?,
            origin: idVec3,
            dir: idVec3,
            radius: Float,
            numSteps: Int,
            lifetime: Int /* = 0*/,
            depthTest: Boolean /*= false */
        )

        @JvmOverloads
        fun DebugCircle(
            color: idVec4?,
            origin: idVec3,
            dir: idVec3,
            radius: Float,
            numSteps: Int,
            lifetime: Int = 0 /* = 0*/
        ) {
            DebugCircle(color, origin, dir, radius, numSteps, lifetime, false)
        }

        abstract fun DebugSphere(
            color: idVec4?,
            sphere: idSphere,
            lifetime: Int /* = 0*/,
            depthTest: Boolean /* = false */
        )

        @JvmOverloads
        fun DebugSphere(color: idVec4?, sphere: idSphere, lifetime: Int = 0 /* = 0*/) {
            DebugSphere(color, sphere, lifetime, false)
        }

        abstract fun DebugBounds(
            color: idVec4?,
            bounds: idBounds,
            org: idVec3 /* = vec3_origin*/,
            lifetime: Int /* = 0*/
        )

        @JvmOverloads
        fun DebugBounds(color: idVec4?, bounds: idBounds, org: idVec3 = getVec3_origin() /* = vec3_origin*/) {
            DebugBounds(color, bounds, org, 0)
        }

        abstract fun DebugBox(color: idVec4?, box: idBox, lifetime: Int /* = 0*/)
        fun DebugBox(color: idVec4?, box: idBox) {
            DebugBox(color, box, 0)
        }

        abstract fun DebugFrustum(
            color: idVec4?,
            frustum: idFrustum,
            showFromOrigin: Boolean /* = false*/,
            lifetime: Int /*= 0*/
        )

        @JvmOverloads
        fun DebugFrustum(color: idVec4?, frustum: idFrustum, showFromOrigin: Boolean = false /* = false*/) {
            DebugFrustum(color, frustum, showFromOrigin, 0)
        }

        abstract fun DebugCone(
            color: idVec4?,
            apex: idVec3,
            dir: idVec3?,
            radius1: Float,
            radius2: Float,
            lifetime: Int /*= 0*/
        )

        fun DebugCone(color: idVec4?, apex: idVec3, dir: idVec3?, radius1: Float, radius2: Float) {
            DebugCone(color, apex, dir, radius1, radius2, 0)
        }

        abstract fun DebugAxis(origin: idVec3?, axis: idMat3)

        // Polygon drawing for debug visualization.
        abstract fun DebugClearPolygons(time: Int) // a time of 0 will clear all polygons
        abstract fun DebugPolygon(
            color: idVec4?,
            winding: idWinding?,
            lifeTime: Int /* = 0*/,
            depthTest: Boolean /*= false*/
        )

        @JvmOverloads
        fun DebugPolygon(color: idVec4?, winding: idWinding?, lifeTime: Int = 0 /* = 0*/) {
            DebugPolygon(color, winding, lifeTime, false)
        }

        // Text drawing for debug visualization.
        abstract fun DrawText(
            text: String?,
            origin: idVec3?,
            scale: Float,
            color: idVec4?,
            viewAxis: idMat3?,
            align: Int /*= 1*/,
            lifetime: Int /*= 0*/,
            depthTest: Boolean /* = false*/
        )

        @JvmOverloads
        fun DrawText(
            text: String?,
            origin: idVec3?,
            scale: Float,
            color: idVec4?,
            viewAxis: idMat3?,
            align: Int = 1 /*= 1*/,
            lifetime: Int = 0 /*= 0*/
        ) {
            DrawText(text, origin, scale, color, viewAxis, align, lifetime, false)
        }
    }

    /*
     ===================
     R_ListRenderLightDefs_f
     ===================
     */
    class R_ListRenderLightDefs_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var ldef: idRenderLightLocal?
            if (null == tr_local.tr.primaryWorld) {
                return
            }
            var active: Int = 0
            var totalRef: Int = 0
            var totalIntr: Int = 0
            i = 0
            while (i < tr_local.tr.primaryWorld!!.lightDefs.Num()) {
                ldef = tr_local.tr.primaryWorld!!.lightDefs[i]
                if (null == ldef) {
                    common.Printf("%4d: FREED\n", i)
                    i++
                    continue
                }

                // count up the interactions
                var iCount: Int = 0
                var inter: idInteraction? = ldef.firstInteraction
                while (inter != null) {
                    iCount++
                    inter = inter.lightNext
                }
                totalIntr += iCount

                // count up the references
                var rCount: Int = 0
                var ref: areaReference_s? = ldef.references
                while (ref != null) {
                    rCount++
                    ref = ref.ownerNext
                }
                totalRef += rCount
                common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, ldef.lightShader!!.GetName())
                active++
                i++
            }
            common.Printf("%d lightDefs, %d interactions, %d areaRefs\n", active, totalIntr, totalRef)
        }

        companion object {
            val instance: cmdFunction_t = R_ListRenderLightDefs_f()
        }
    }

    /*
     ===================
     R_ListRenderEntityDefs_f
     ===================
     */
    class R_ListRenderEntityDefs_f private constructor() : cmdFunction_t() {
        public override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var mdef: idRenderEntityLocal?
            if (null == tr_local.tr.primaryWorld) {
                return
            }
            var active: Int = 0
            var totalRef: Int = 0
            var totalIntr: Int = 0
            i = 0
            while (i < tr_local.tr.primaryWorld!!.entityDefs.Num()) {
                mdef = tr_local.tr.primaryWorld!!.entityDefs[i]
                if (null == mdef) {
                    common.Printf("%4d: FREED\n", i)
                    i++
                    continue
                }

                // count up the interactions
                var iCount: Int = 0
                var inter: idInteraction? = mdef.firstInteraction
                while (inter != null) {
                    iCount++
                    inter = inter.entityNext
                }
                totalIntr += iCount

                // count up the references
                var rCount: Int = 0
                var ref: areaReference_s? = mdef.entityRefs
                while (ref != null) {
                    rCount++
                    ref = ref.ownerNext
                }
                totalRef += rCount
                common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, mdef.parms.hModel!!.Name())
                active++
                i++
            }
            common.Printf("total active: %d\n", active)
        }

        companion object {
            val instance: cmdFunction_t = R_ListRenderEntityDefs_f()
        }
    }
}
